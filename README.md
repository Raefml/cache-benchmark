# Cache Benchmark

Comparaison des performances de 6 solutions de cache avec Spring Boot.

## Caches testés

- **Redis** - Le classique, single-threaded avec I/O multiplexing
- **Valkey** - Fork open-source de Redis
- **Memcached** - Simple et ultra-rapide
- **Hazelcast** - Distribué et multi-threaded
- **KeyDB** - Redis-compatible avec multi-threading
- **DragonflyDB** - Nouvelle génération, compatible Redis

## Stack

- Java 17 + Spring Boot
- Prometheus + Grafana
- Docker Compose

## Lancer le projet

```bash
# Démarrer les caches
docker-compose up -d

# Lancer l'application
./mvnw spring-boot:run

# Accéder au dashboard
# Grafana: http://localhost:3000
# Prometheus: http://localhost:9090
```

## Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/benchmark/run` | Lancer le benchmark complet |
| `GET /api/benchmark/concurrent` | Test sous charge (50 users) |

## Résultats

Les benchmarks mesurent :
- **Latence moyenne** (ms)
- **Throughput** (ops/sec)
- **Scalabilité** sous charge concurrente

## Auteur

Projet créé pour comparer les architectures single-threaded vs multi-threaded dans les solutions de cache.
