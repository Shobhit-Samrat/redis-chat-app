# Redis Chat Application (Spring Boot)

A simple chat backend where **Redis is the primary data store**: chat rooms, participants, message history and
real-time delivery (Pub/Sub). Pure REST - no UI - fully testable from Postman.

* Java 17, Spring Boot 3.3, Spring Data Redis (Lettuce)
* JUnit 5 + Mockito + MockMvc, Testcontainers for end-to-end tests against real Redis

## Run it

**Prerequisites:** JDK 17+, Maven 3.8+, Docker (for Redis).

```bash
# 1. start Redis (AOF + RDB persistence enabled, data kept in a Docker volume)
docker compose up -d

# 2. run the app on http://localhost:8080
mvn spring-boot:run

# 3. run all tests (the Testcontainers integration test is skipped automatically if Docker is not running)
mvn test
```

Configuration (`src/main/resources/application.yml`): `REDIS_HOST` / `REDIS_PORT` env vars, plus `chat.*` settings
(`require-membership`, `max-messages-per-room`, `default-history-limit`, `max-history-limit`, `sse-timeout`).

## API

Base path: `/api/chatapp/chatrooms`. All bodies are JSON. A ready-to-import **Postman collection** is in
`postman/Redis-Chat-App.postman_collection.json`.

| # | Method & path | Body / query | Success |
|---|---------------|--------------|---------|
| 1 | `POST /api/chatapp/chatrooms` | `{"roomName":"general"}` | `201` |
| 2 | `POST /api/chatapp/chatrooms/{roomId}/join` | `{"participant":"guest_user"}` | `200` |
| 3 | `POST /api/chatapp/chatrooms/{roomId}/messages` | `{"participant":"guest_user","message":"Hello, everyone!"}` | `201` |
| 4 | `GET /api/chatapp/chatrooms/{roomId}/messages?limit=10` | `limit` optional (default 10, max 100) | `200` |
| 5 | `GET /api/chatapp/chatrooms/{roomId}/stream?participant=guest_user` | Server-Sent Events (real-time feed) | `200` (stream) |
| 6 | `DELETE /api/chatapp/chatrooms/{roomId}` | - | `200` |

### Example responses

```jsonc
// 1. POST /api/chatapp/chatrooms
{ "message": "Chat room 'general' created successfully.", "roomId": "general", "status": "success" }

// 2. POST /api/chatapp/chatrooms/general/join
{ "message": "User 'guest_user' joined chat room 'general'.", "status": "success" }

// 3. POST /api/chatapp/chatrooms/general/messages
{ "message": "Message sent successfully.", "status": "success" }

// 4. GET /api/chatapp/chatrooms/general/messages?limit=10   (oldest -> newest)
{ "messages": [
    { "participant": "guest_user",   "message": "Hello, everyone!", "timestamp": "2024-01-01T10:00:00Z" },
    { "participant": "another_user", "message": "Hi, guest_user!",  "timestamp": "2024-01-01T10:01:00Z" } ] }

// 6. DELETE /api/chatapp/chatrooms/general
{ "message": "Chat room 'general' deleted successfully.", "roomId": "general", "status": "success" }
```

### Errors

Every error uses the same envelope: `{"message": "...", "status": "error"}`.

| Situation | HTTP |
|-----------|------|
| Duplicate room name | `409 Conflict` |
| Room does not exist (join / send / history / stream / delete) | `404 Not Found` |
| Sender / listener has not joined the room (`chat.require-membership=true`) | `403 Forbidden` |
| Invalid room name, blank participant/message, malformed JSON, bad or out-of-range `limit` | `400 Bad Request` |
| Wrong HTTP method | `405 Method Not Allowed` |
| Redis unreachable | `503 Service Unavailable` |

Room names: 1-50 characters, letters / digits / `_` / `-`.

## Real-time messaging (Redis Pub/Sub)

1. `POST .../messages` stores the message in a Redis List **and** publishes the same JSON to the channel `chat:channel:{roomId}`.
2. The app holds one pattern subscription (`chat:channel:*`) - it covers all current and future rooms, and every
   running instance receives every message (scale out by simply starting more instances).
3. `RedisMessageSubscriber` hands each message to the `MessageBroadcaster`, whose SSE implementation pushes it to every client
   connected to `GET .../stream`.

**Try it in Postman:** open `GET .../chatrooms/general/stream?participant=guest_user` (Postman shows SSE events live), then send a
message from another tab - the event `message` appears instantly. With curl:

```bash
curl -N "http://localhost:8080/api/chatapp/chatrooms/general/stream?participant=guest_user"
```

You can also watch the raw channel: `docker exec -it chat-redis redis-cli PSUBSCRIBE 'chat:channel:*'`.

## Redis data model

| Key | Type | Purpose |
|-----|------|---------|
| `chat:rooms` | Set | Index of room ids; `SADD` makes "create if absent" atomic (no duplicate-name race) |
| `chat:room:{id}` | Hash | Room metadata (`roomId`, `createdAt`) |
| `chat:room:{id}:participants` | Set | Members; O(1) join and membership check, re-join is idempotent |
| `chat:room:{id}:messages` | List | History as JSON strings. `RPUSH` to append, `LRANGE -N -1` for the last N, `LTRIM` caps the list (default 1000) |
| `chat:channel:{id}` | Pub/Sub | Real-time delivery channel |

Deleting a room removes the index entry and `DEL`s the hash, set and list in one command, then closes any live streams.

## Persistence

`docker-compose.yml` starts Redis with `--appendonly yes --appendfsync everysec` (AOF) plus an RDB snapshot rule, and mounts a volume,
so rooms and history survive restarts: `docker compose restart redis` and the data is still there.

## Architecture

```
controller/   ChatRoomController                 HTTP only: validate, delegate, shape response
service/      ChatRoomService (+Default impl)    business rules, orchestration
              RoomStreamService                  authorised live streams
              RoomDeletedEvent                   domain event (decouples deletion from open streams)
repository/   ChatRoomRepository (+Redis impl)   all Redis data-structure access (Repository pattern)
messaging/    MessagePublisher / RedisMessagePublisher
              RedisMessageSubscriber             Redis Pub/Sub listener
              MessageBroadcaster / SseEmitterRegistry   Observer-style fan-out to connected clients
codec/        MessageCodec                       single JSON wire format
config/       RedisConfig, ChatProperties, AppConfig
constants/    RedisKeys                          the only place key names are built
exception/    domain exceptions + GlobalExceptionHandler (@RestControllerAdvice)
dto/ model/   request/response records, ChatMessage
```

Design notes: constructor injection throughout; interfaces at layer boundaries so each layer is unit-testable with mocks;
`Clock` injected for deterministic timestamps; records for immutable DTOs.

## Tests

| Test | Scope |
|------|-------|
| `DefaultChatRoomServiceTest` | business logic with mocked repository / publisher (duplicates, missing rooms, membership, limits, deletion) |
| `ChatRoomControllerTest` | HTTP contract, status codes, validation and error envelope (`@WebMvcTest`) |
| `RedisMessageSubscriberTest`, `SseEmitterRegistryTest`, `MessageCodecTest`, `RedisKeysTest` | messaging / utility units |
| `ChatFlowIntegrationTest` | Test Cases 1-5 against real Redis via Testcontainers (create/join, ordering, Pub/Sub delivery, errors, deletion) |

## Assumptions

* A participant must have joined a room before sending messages or opening its stream. To relax this, set `chat.require-membership: false`.
* `POST` create operations return `201 Created`; the body matches the specification.

## Swagger / OpenAPI

Run the app and open **http://localhost:8080/swagger-ui.html** (raw spec: `/v3/api-docs`).

## Deployment

The `Dockerfile` builds a runnable image. Set the env var `REDIS_URL` (e.g. `rediss://default:<password>@<host>:<port>`) to point at a hosted Redis;
the app also honours `PORT`. Live URL: _add here after deploying_ - Swagger at `<live-url>/swagger-ui.html`.
