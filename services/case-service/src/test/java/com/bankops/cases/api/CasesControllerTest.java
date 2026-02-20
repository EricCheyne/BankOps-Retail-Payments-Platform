package com.bankops.cases.api;

import com.bankops.cases.repository.CaseDecisionRepository;
import com.bankops.cases.repository.OpsCaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CasesControllerTest {

    private final OpsCaseRepository opsCaseRepository = mock(OpsCaseRepository.class);
    private final CaseDecisionRepository caseDecisionRepository = mock(CaseDecisionRepository.class);
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CasesController controller = new CasesController(
            opsCaseRepository, caseDecisionRepository, kafkaTemplate, objectMapper
    );

    @Test
    void decideCase_ShouldFail_WhenActorIsCreator() {
        UUID caseId = UUID.randomUUID();
        String creator = "risk-service";
        OpsCaseRepository.OpsCase mockCase = new OpsCaseRepository.OpsCase(
                caseId, Instant.now(), UUID.randomUUID(), "OPEN", creator, "user123", objectMapper.createArrayNode()
        );

        when(opsCaseRepository.findById(caseId)).thenReturn(Optional.of(mockCase));

        CasesController.DecisionRequest request = new CasesController.DecisionRequest("APPROVE", "Looks good");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            controller.decideCase(caseId, creator, request);
        });

        assertEquals(400, exception.getStatusCode().value());
        assertEquals("SoD Violation: Actor cannot be the case creator", exception.getReason());
    }

    @Test
    void decideCase_ShouldFail_WhenActorIsRequester() {
        UUID caseId = UUID.randomUUID();
        String requester = "user123";
        OpsCaseRepository.OpsCase mockCase = new OpsCaseRepository.OpsCase(
                caseId, Instant.now(), UUID.randomUUID(), "OPEN", "risk-service", requester, objectMapper.createArrayNode()
        );

        when(opsCaseRepository.findById(caseId)).thenReturn(Optional.of(mockCase));

        CasesController.DecisionRequest request = new CasesController.DecisionRequest("APPROVE", "Looks good");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            controller.decideCase(caseId, requester, request);
        });

        assertEquals(400, exception.getStatusCode().value());
        assertEquals("SoD Violation: Actor cannot be the transfer requester", exception.getReason());
    }

    @Test
    void decideCase_ShouldFail_WhenCaseNotOpen() {
        UUID caseId = UUID.randomUUID();
        OpsCaseRepository.OpsCase mockCase = new OpsCaseRepository.OpsCase(
                caseId, Instant.now(), UUID.randomUUID(), "APPROVED", "risk-service", "user123", objectMapper.createArrayNode()
        );

        when(opsCaseRepository.findById(caseId)).thenReturn(Optional.of(mockCase));

        CasesController.DecisionRequest request = new CasesController.DecisionRequest("APPROVE", "Already approved");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            controller.decideCase(caseId, "ops-user", request);
        });

        assertEquals(409, exception.getStatusCode().value());
        assertEquals("Case is already closed", exception.getReason());
    }
}
