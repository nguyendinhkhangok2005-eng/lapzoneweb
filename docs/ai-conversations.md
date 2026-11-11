# Persistent AI conversations

The existing Spring Boot MVC / Spring Data JPA architecture, configured MySQL schema,
`users` table and Spring Security `CustomUserDetails` principal are reused. No new
dependencies or frontend changes are required. AI consultation, verified product
recommendations, question validation and IP/global rate limits remain in place.

## Endpoints

| Method | URL | Request / behavior |
| --- | --- | --- |
| POST | `/api/ai/conversations` | Optional JSON `{"title":"Gaming laptop"}`; 201 with metadata and Location header. Missing title uses a default. |
| GET | `/api/ai/conversations?page=0&size=20` | Metadata array for the current owner, newest updated first; size 1–100. |
| GET | `/api/ai/conversations/{id}` | `{"conversation":{"id","title","createdAt","updatedAt"},"messages":[{"role","text","conversationId","createdAt"}]}`; messages oldest first. |
| PATCH | `/api/ai/conversations/{id}` | `{"title":"New title"}`; returns updated metadata. Titles must contain 1–120 characters without controls. |
| DELETE | `/api/ai/conversations/{id}` | Deletes this conversation and its messages; 204. |
| POST | `/api/ai/chat` | `{"message":"Laptop gaming dưới 20 triệu","conversationId":"<id>"}`; returns `{"reply":"...","conversationId":"<id>"}`. |
| POST | `/api/chat/reset?conversationId={id}` | Clears messages only in the specified conversation; returns the existing `reply` field. |
| POST | `/api/chat/reset` | Detaches the legacy session default so the next ID-less chat starts a new conversation; previous persistent history remains available. |

Use the existing login/session cookie. Authenticated conversations belong exclusively
to the current user and can be resumed after login in another session. Guests can use
all endpoints, but only for conversations created with their own session's server-side
random guest key. Guest history is persisted but becomes inaccessible after session
expiry/logout. Login does not automatically claim guest conversations. User IDs and
guest keys cannot be supplied through these APIs. Unknown or inaccessible IDs return
404, including chat, rename, reset and delete. Invalid titles/pagination return 400.

## Compatibility and context

Existing clients may continue sending only `message` and reading `reply`. A default
conversation is created lazily per session and authenticated user (or guest). Explicit
conversation requests do not switch that legacy default. The session stores only IDs
and a guest access key, never the message history. Existing transient pre-deployment
session messages are not imported because their original owner cannot be established.
Blank-question greetings remain stateless and return only `reply` as before.

Each conversation has a UUID string ID. All messages are retained; only its latest 12
messages are supplied to the AI, in chronological order. Replies retain the existing
`model` role and rendered product information. A database transaction and row lock
serialize sends, renames, resets and deletes for one conversation across instances.
The lock is held during the bounded AI call; other conversation IDs use separate locks.
The user/model message pair commits atomically; an unexpected exception rolls it back.
Instances sharing the database still need the existing sticky/shared HTTP sessions for
guest access and legacy defaults. Authenticated explicit-ID access uses user ownership.

## Database migration and verification

Apply [the additive SQL migration](../src/main/resources/db/manual/20261111_ai_conversations.sql)
to the existing database. It creates `ai_conversations` (optional FK to `users`) and
`ai_messages` (FK to the conversation), with owner, guest and message ordering indexes.
No migration library is added. The current `spring.jpa.hibernate.ddl-auto=update` also
creates these tables on startup; environments using `validate`/`none` must apply the SQL
first. Deleting a conversation removes its messages transactionally in the service.

Run `mvn test` with the application's configured MySQL available. The integration
tests use the current database and roll back their test records; Hibernate's configured
schema update still creates the new tables. They exercise durable history, the context
window, owner/guest isolation and scoped rename/reset/delete. Controller tests cover
legacy clients, authenticated scope selection, routing, validation and rate limits.

Verified locally with JDK 25 and MySQL 8.0.46: all 29 tests in
`AiConsultantControllerTest`, `AiConsultantServiceTest`, `AiConversationIntegrationTest`,
`SecurityConfigTest`, and `LapzonewebApplicationTests` passed. The current Hibernate
schema update created both new tables, indexes and foreign keys in `lapzone_db`.
The Gemini transport is mocked in these tests; no live provider request is required.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot'
mvn test '-Dtest=AiConsultantControllerTest,AiConsultantServiceTest,AiConversationIntegrationTest,SecurityConfigTest,LapzonewebApplicationTests' '-Dspring.jpa.show-sql=false'
```

The full-suite run also exposed four pre-existing failures in the unchanged
`OrderServiceTest`: three cart tests pass a null user despite the existing login
requirement, and one buy-now test omits the existing product lookup mock. These
order fixtures and the order implementation were left unchanged.

## Changed files

- [AiConsultantController.java](../src/main/java/com/lapzone/lapzoneweb/controller/AiConsultantController.java): conversation endpoints, principal/session scope and legacy defaults.
- [ChatRequestDTO.java](../src/main/java/com/lapzone/lapzoneweb/model/dto/ChatRequestDTO.java), [ChatMessageDTO.java](../src/main/java/com/lapzone/lapzoneweb/model/dto/ChatMessageDTO.java), [ConversationDTO.java](../src/main/java/com/lapzone/lapzoneweb/model/dto/ConversationDTO.java): conversation IDs and response contracts.
- [AiConversation.java](../src/main/java/com/lapzone/lapzoneweb/model/entity/AiConversation.java), [AiMessage.java](../src/main/java/com/lapzone/lapzoneweb/model/entity/AiMessage.java): persistent entities.
- [AiConversationRepository.java](../src/main/java/com/lapzone/lapzoneweb/model/repository/AiConversationRepository.java), [AiMessageRepository.java](../src/main/java/com/lapzone/lapzoneweb/model/repository/AiMessageRepository.java): scoped queries, locking and message history.
- [AiConversationService.java](../src/main/java/com/lapzone/lapzoneweb/model/service/AiConversationService.java): transactional lifecycle and isolated AI context.
- [AiConsultantControllerTest.java](../src/test/java/com/lapzone/lapzoneweb/AiConsultantControllerTest.java), [AiConversationIntegrationTest.java](../src/test/java/com/lapzone/lapzoneweb/AiConversationIntegrationTest.java): compatibility, rate limits, persistence and security coverage.
- [20261111_ai_conversations.sql](../src/main/resources/db/manual/20261111_ai_conversations.sql): optional manual schema migration.
- This report.

No frontend files were changed by this implementation; pre-existing chatbot template
edits were preserved.
