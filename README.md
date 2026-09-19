# compiler-data-service

Spring Boot microservice that compiles/runs untrusted code on demand, called
from your own backend as an internal API (not meant to be public-facing).

## How it works

1. Caller sends `POST /api/v1/compile` with an `Authorization: <api-key>` header.
2. `ApiKeyAuthFilter` checks the key; `RateLimitFilter` throttles per key.
3. The job is submitted to a bounded thread pool. If the pool and its wait
   queue are both full, the caller gets an immediate `429` instead of blocking.
4. A worker thread runs the code inside a locked-down, single-use Docker
   container for the target language: `--network none`, capped memory/CPU,
   `--pids-limit`, dropped capabilities, read-only root filesystem, and a
   host-side watchdog that force-kills the container if it overruns its
   timeout.
5. stdout/stderr/exit code come back in the response.

Supported languages: `c`, `cpp`, `java`, `python`, `javascript`.

## Requirements

- Java 17+, Maven
- Docker installed and running on the host (the service shells out to the
  `docker` CLI — no daemon socket access needed beyond that)
- Pull the base images once up front so first requests aren't slow:

```
docker pull gcc:13
docker pull eclipse-temurin:21-jdk
docker pull python:3.11-slim
docker pull node:20-slim
```

## Configuration

All under `app.*` in `application.yml`, overridable via env vars, e.g.:

```
API_KEYS=your-real-secret,another-caller-key
```

See `application.yml` for queue size, worker pool size, per-execution
timeout/memory/CPU limits, and rate-limit settings.

## Run

```
mvn spring-boot:run
```

## Example request

```
curl -X POST http://localhost:8080/api/v1/compile \
  -H "Authorization: dev-secret-key-change-me" \
  -H "Content-Type: application/json" \
  -d '{
        "language": "python",
        "code": "print(input())",
        "stdin": "hello"
      }'
```

Response:

```json
{
  "status": "SUCCESS",
  "stdout": "hello\n",
  "stderr": "",
  "exitCode": 0,
  "executionTimeMs": 412
}
```

`status` is one of `SUCCESS`, `ERROR` (non-zero exit — compile error or
runtime error, see `stderr`), or `TIMEOUT`.

## Notes / next steps

- This is single-instance rate limiting and queueing (in-memory). If you ever
  run more than one replica behind a load balancer, move the token bucket to
  Redis so limits are shared across instances.
- Language/version pinning is done via the Docker image tags in
  `Language.java` — bump those centrally when you need a newer compiler.
- Java submissions must define `public class Main` since the source file is
  written out as `Main.java`.



mvn clean package -DskipTests
java -jar target/compiler-data-service-0.1.0.jar
