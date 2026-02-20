package com.bankops.cases.api;

import com.bankops.cases.repository.CaseDecisionRepository;
import com.bankops.cases.repository.OpsCaseRepository;
import com.bankops.common.events.BankEventEnvelope;
import com.bankops.common.events.EventTypes;
import com.bankops.common.events.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cases")
public class CasesController {

    private static final Logger log = LoggerFactory.getLogger(CasesController.class);

    private final OpsCaseRepository opsCaseRepository;
    private final CaseDecisionRepository caseDecisionRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public CasesController(OpsCaseRepository opsCaseRepository,
                           CaseDecisionRepository caseDecisionRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper) {
        this.opsCaseRepository = opsCaseRepository;
        this.caseDecisionRepository = caseDecisionRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<OpsCaseRepository.OpsCase> listCases(@RequestParam(defaultValue = "OPEN") String status,
                                                     @RequestParam(defaultValue = "50") int limit) {
        return opsCaseRepository.listByStatus(status, limit);
    }

    @PostMapping("/{id}/decision")
    @Transactional
    public void decideCase(@PathVariable UUID id,
                           @RequestHeader("X-Actor") String actor,
                           @Valid @RequestBody DecisionRequest request) throws JsonProcessingException {
        
        OpsCaseRepository.OpsCase opsCase = opsCaseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));

        // SoD enforcement
        if (actor.equals(opsCase.createdBy())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SoD Violation: Actor cannot be the case creator");
        }
        if (actor.equals(opsCase.transferRequestedBy())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SoD Violation: Actor cannot be the transfer requester");
        }
        
        if (!"OPEN".equals(opsCase.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Case is already closed");
        }

        String newStatus = "APPROVE".equals(request.decision()) ? "APPROVED" : "REJECTED";
        
        caseDecisionRepository.insertDecision(
                UUID.randomUUID(),
                id,
                request.decision(),
                actor,
                request.justification()
        );

        opsCaseRepository.updateStatus(id, newStatus);
        
        log.info("Case id={} decided as {} by actor={}", id, newStatus, actor);

        publishDecisionEvent(opsCase, request, actor);
    }

    private void publishDecisionEvent(OpsCaseRepository.OpsCase opsCase, DecisionRequest request, String actor) throws JsonProcessingException {
        String eventType = "APPROVE".equals(request.decision()) ? EventTypes.CaseApproved : EventTypes.CaseRejected;
        
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("caseId", opsCase.id().toString());
        payload.put("transferId", opsCase.transferId().toString());
        payload.put("decision", request.decision());
        payload.put("decidedBy", actor);
        payload.put("justification", request.justification());
        payload.put("decidedAt", Instant.now().toString());

        BankEventEnvelope envelope = new BankEventEnvelope(
                eventType,
                UUID.randomUUID().toString(),
                Instant.now(),
                opsCase.transferId().toString(),
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);

        Message<String> message = MessageBuilder
                .withPayload(json)
                .setHeader(KafkaHeaders.TOPIC, Topics.BANKOPS_CASE_EVENTS)
                .setHeader(KafkaHeaders.KEY, opsCase.transferId().toString())
                .setHeader("event_type", eventType)
                .setHeader("correlation_id", opsCase.transferId().toString())
                .setHeader("event_id", envelope.eventId())
                .build();

        kafkaTemplate.send(message);
        log.info("Published {} event for caseId={}, transferId={}", eventType, opsCase.id(), opsCase.transferId());
    }

    public record DecisionRequest(
            @NotBlank @Pattern(regexp = "APPROVE|REJECT") String decision,
            @NotBlank @Size(max = 500) String justification
    ) {}
}
