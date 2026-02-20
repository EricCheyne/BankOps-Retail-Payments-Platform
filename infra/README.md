# Infrastructure

This directory contains local infrastructure for the bankops platform.

## Commands

### Start infrastructure
```bash
docker compose up -d
```

### Create databases
After starting the infrastructure, run this script to create the necessary databases:
```bash
./create-dbs.sh
```

### Stop infrastructure (and remove volumes)
```bash
docker compose down -v
```
