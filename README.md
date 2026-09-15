# market-kurly-clone

## Local Infrastructure

This project uses RabbitMQ as a shared message broker across all services.

1. Copy the env template: `cp .env.example .env`
2. Start RabbitMQ: `docker compose up -d`
3. Check it's healthy: `docker compose ps` (should show `healthy`)
4. Management UI: http://localhost:15672 (default `guest` / `guest`)
5. Stop: `docker compose down` (add `-v` to also wipe the persisted queue data volume)
