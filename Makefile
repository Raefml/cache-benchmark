.PHONY: help build clean test run docker-build docker-up docker-down docker-logs benchmark init-data status prometheus grafana

# Variables
DOCKER_COMPOSE = docker compose -f docker/docker-compose.yml
APP_JAR = target/cache-benchmark-0.0.1-SNAPSHOT.jar
MAVEN = ./mvnw

# Colors for output
BLUE = \033[0;34m
GREEN = \033[0;32m
YELLOW = \033[1;33m
RED = \033[0;31m
NC = \033[0m # No Color

help: ## Show this help message
	@echo "$(BLUE)Cache Performance Benchmark - Available Commands$(NC)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "$(GREEN)%-20s$(NC) %s\n", $$1, $$2}'

# Build Commands
build: ## Build the application
	@echo "$(BLUE)Building application...$(NC)"
	$(MAVEN) clean package -DskipTests

build-with-tests: ## Build with tests
	@echo "$(BLUE)Building application with tests...$(NC)"
	$(MAVEN) clean package

compile: ## Compile the application
	@echo "$(BLUE)Compiling...$(NC)"
	$(MAVEN) compile

clean: ## Clean build artifacts
	@echo "$(BLUE)Cleaning build artifacts...$(NC)"
	$(MAVEN) clean
	rm -rf benchmark-results/

# Test Commands
test: ## Run tests
	@echo "$(BLUE)Running tests...$(NC)"
	$(MAVEN) test

test-coverage: ## Run tests with coverage
	@echo "$(BLUE)Running tests with coverage...$(NC)"
	$(MAVEN) test jacoco:report

# Run Commands
run: ## Run the application locally
	@echo "$(BLUE)Running application...$(NC)"
	$(MAVEN) spring-boot:run

run-jar: build ## Build and run JAR
	@echo "$(BLUE)Running JAR...$(NC)"
	java -jar $(APP_JAR)

# Docker Commands
docker-build: ## Build Docker image
	@echo "$(BLUE)Building Docker image...$(NC)"
	docker build -t cache-benchmark:latest .

docker-up: ## Start all Docker services
	@echo "$(BLUE)Starting Docker services...$(NC)"
	$(DOCKER_COMPOSE) up -d
	@echo "$(GREEN)Services started successfully!$(NC)"
	@echo "$(YELLOW)Waiting for services to be ready...$(NC)"
	@sleep 10
	@make docker-ps

docker-down: ## Stop all Docker services
	@echo "$(BLUE)Stopping Docker services...$(NC)"
	$(DOCKER_COMPOSE) down

docker-restart: docker-down docker-up ## Restart all Docker services

docker-ps: ## Show running containers
	@echo "$(BLUE)Running containers:$(NC)"
	$(DOCKER_COMPOSE) ps

docker-logs: ## Show logs from all services
	$(DOCKER_COMPOSE) logs -f

docker-logs-app: ## Show application logs
	$(DOCKER_COMPOSE) logs -f app

docker-clean: docker-down ## Clean Docker resources
	@echo "$(BLUE)Cleaning Docker resources...$(NC)"
	$(DOCKER_COMPOSE) down -v
	docker system prune -f

# Benchmark Commands
benchmark: ## Run performance benchmarks
	@echo "$(BLUE)Running performance benchmarks...$(NC)"
	@bash scripts/run-benchmarks.sh

benchmark-sequential: ## Run sequential benchmark only
	@echo "$(BLUE)Running sequential benchmark...$(NC)"
	@curl -X POST http://localhost:8080/api/benchmark/sequential | jq '.'

benchmark-concurrent: ## Run concurrent benchmark only
	@echo "$(BLUE)Running concurrent benchmark...$(NC)"
	@curl -X POST http://localhost:8080/api/benchmark/concurrent | jq '.'

# Data Commands
init-data: ## Initialize test data (1000 products)
	@echo "$(BLUE)Initializing test data...$(NC)"
	@curl -X POST http://localhost:8080/api/products/init/1000
	@echo "$(GREEN)Test data initialized!$(NC)"

init-data-10k: ## Initialize 10k test products
	@echo "$(BLUE)Initializing 10k test products...$(NC)"
	@curl -X POST http://localhost:8080/api/products/init/10000
	@echo "$(GREEN)10k test products initialized!$(NC)"

# Status Commands
status: ## Check provider status
	@echo "$(BLUE)Checking cache provider status...$(NC)"
	@curl -s http://localhost:8080/api/benchmark/status | jq '.'

health: ## Check application health
	@echo "$(BLUE)Checking application health...$(NC)"
	@curl -s http://localhost:8080/actuator/health | jq '.'

metrics: ## Show application metrics
	@echo "$(BLUE)Fetching metrics...$(NC)"
	@curl -s http://localhost:8080/actuator/metrics | jq '.'

# Monitoring Commands
prometheus: ## Open Prometheus UI
	@echo "$(BLUE)Opening Prometheus...$(NC)"
	@echo "$(YELLOW)Prometheus UI: http://localhost:9090$(NC)"
	@xdg-open http://localhost:9090 2>/dev/null || open http://localhost:9090 2>/dev/null || echo "Please open http://localhost:9090 in your browser"

grafana: ## Open Grafana UI
	@echo "$(BLUE)Opening Grafana...$(NC)"
	@echo "$(YELLOW)Grafana UI: http://localhost:3000$(NC)"
	@echo "$(YELLOW)Username: admin | Password: admin$(NC)"
	@xdg-open http://localhost:3000 2>/dev/null || open http://localhost:3000 2>/dev/null || echo "Please open http://localhost:3000 in your browser"

# Development Commands
dev: docker-up run ## Start Docker services and run application

dev-setup: docker-up init-data ## Setup development environment
	@echo "$(GREEN)Development environment ready!$(NC)"

fmt: ## Format code
	@echo "$(BLUE)Formatting code...$(NC)"
	$(MAVEN) spotless:apply

lint: ## Lint code
	@echo "$(BLUE)Linting code...$(NC)"
	$(MAVEN) checkstyle:check

# Database Commands
db-console: ## Open database console
	@echo "$(BLUE)Connecting to PostgreSQL...$(NC)"
	docker exec -it cache-benchmark-postgres psql -U postgres -d cachedb

db-reset: ## Reset database
	@echo "$(BLUE)Resetting database...$(NC)"
	docker exec -it cache-benchmark-postgres psql -U postgres -d cachedb -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

# Cache Commands
cache-flush-all: ## Flush all caches
	@echo "$(BLUE)Flushing all caches...$(NC)"
	@docker exec -it cache-benchmark-redis redis-cli FLUSHALL
	@docker exec -it cache-benchmark-valkey valkey-cli FLUSHALL
	@docker exec -it cache-benchmark-dragonflydb redis-cli FLUSHALL
	@docker exec -it cache-benchmark-keydb keydb-cli FLUSHALL
	@echo "$(GREEN)All caches flushed!$(NC)"

# Full Workflow Commands
setup: build docker-up init-data ## Complete setup (build, start services, init data)
	@echo "$(GREEN)Setup complete! Ready to run benchmarks.$(NC)"
	@echo "$(YELLOW)Run 'make benchmark' to start performance tests$(NC)"

all: clean build test docker-build docker-up init-data benchmark ## Run complete workflow

quick-start: docker-up ## Quick start (just Docker services)
	@echo "$(GREEN)Docker services started!$(NC)"
	@echo "$(YELLOW)Run 'make run' to start the application$(NC)"

# Information
info: ## Show project information
	@echo "$(BLUE)Cache Performance Benchmark$(NC)"
	@echo ""
	@echo "$(YELLOW)Services:$(NC)"
	@echo "  Application:    http://localhost:8080"
	@echo "  Prometheus:     http://localhost:9090"
	@echo "  Grafana:        http://localhost:3000 (admin/admin)"
	@echo "  PostgreSQL:     localhost:5432"
	@echo "  Redis:          localhost:6379"
	@echo "  Valkey:         localhost:6380"
	@echo "  DragonflyDB:    localhost:6381"
	@echo "  KeyDB:          localhost:6382"
	@echo "  Memcached:      localhost:11211"
	@echo "  Hazelcast:      localhost:5701"
	@echo "  Ignite:         localhost:10800"
	@echo ""
	@echo "$(YELLOW)API Endpoints:$(NC)"
	@echo "  GET    /api/products"
	@echo "  GET    /api/products/{id}"
	@echo "  POST   /api/products"
	@echo "  POST   /api/benchmark/sequential"
	@echo "  POST   /api/benchmark/concurrent"
	@echo "  GET    /api/benchmark/status"
	@echo ""
	@echo "$(YELLOW)For more commands, run:$(NC) make help"

# Default target
.DEFAULT_GOAL := help
