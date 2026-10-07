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

## Interactive execution (WebSocket)

For code that reads stdin while it runs — e.g. a loop that prompts for `t`
inputs one at a time — use the WebSocket endpoint instead of `/api/v1/compile`,
which only supports stdin supplied all at once up front.

Connect to:

```
ws(s)://<host>/ws/execute?apiKey=<api-key>
```

(a plain `Authorization: <api-key>` header also works for non-browser
clients, but browsers can't set custom headers on a WebSocket handshake, so
the query param exists for them).

One connection runs at most one submission at a time; send another `start`
to run again on the same connection once the previous one has exited.

Client → server (JSON text frames):

```
{"type":"start","language":"cpp","code":"..."}   // begin a run
{"type":"stdin","data":"5\n"}                     // send input to the running program
{"type":"eof"}                                    // close stdin (for programs reading until EOF)
{"type":"stop"}                                   // kill the running program early
```

Server → client:

```
{"type":"stdout","data":"..."}                    // a chunk of output, as soon as it's produced
{"type":"stderr","data":"..."}
{"type":"exit","status":"SUCCESS","exitCode":0,"executionTimeMs":123}
{"type":"error","message":"..."}                  // bad request / already running / server busy
```

`status` on exit is one of `SUCCESS`, `ERROR`, `TIME_LIMIT_EXCEEDED`,
`MEMORY_LIMIT_EXCEEDED`, `OUTPUT_LIMIT_EXCEEDED`, or `KILLED` (client sent
`stop`, or the connection dropped mid-run).

Unlike the batch endpoint, output here is streamed as it's produced rather
than buffered until the process exits, so a prompt printed without a
trailing newline (e.g. `printf("Enter a number: ")`) reaches the client
immediately instead of only after the whole run finishes. Session wall-clock
budget and concurrency caps are under `app.interactive.*` in
`application.yml`.

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
