#!/bin/bash
set -e

export PGPASSWORD=postgres
psql -h localhost -U postgres -c "CREATE DATABASE ledger;" || true
psql -h localhost -U postgres -c "CREATE DATABASE payments;" || true
psql -h localhost -U postgres -c "CREATE DATABASE cases;" || true
