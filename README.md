# SpecForge

AI-powered API code generation from OpenAPI/Swagger specs. A learning project exploring multi-agent AI workflows.

**Current Status:** Phase 1 complete ✓ → Phase 2 ready to start

---

## Overview

SpecForge reads an OpenAPI/Swagger specification, runs a multi-agent Claude pipeline, and generates production-ready API code in your target language (Java or Python). All served as a single Spring Boot JAR — no separate frontend, no build tooling.

### Tech Stack

- **Runtime**: Java 17 (LTS)
- **Framework**: Spring Boot 3.2 + Thymeleaf + HTMX
- **AI**: Anthropic Claude API (Haiku tier for cost efficiency)
- **Spec Parsing**: YAML/JSON via SnakeYAML + Jackson
- **Code Preview**: CodeMirror 6 (CDN)
- **Styling**: Tailwind CSS (CDN)
- **Concurrency**: Cached thread pool for parallel agent calls

---

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Anthropic API key (get one at https://console.anthropic.com)

### Run Locally

```bash
# Clone and enter repo
cd spec-forge

# Set your API key (pick one):
export ANTHROPIC_API_KEY=sk-ant-...
# OR create .env and let Spring load it

# Build and run tests
mvn clean test

# Start the app
ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run

# Open http://localhost:8080
```

The app will log startup info to console. Press Ctrl+C to stop.

---

## Phase Validation Guide

Each phase is independently buildable and testable. Follow these steps to validate your current phase.

### Phase 1 — Walking Skeleton ✓ COMPLETE

**What Phase 1 validates:**
- Spec parsing (YAML/JSON, 10-endpoint cap)
- Agent base class and Claude API integration
- SpecAnalystAgent end-to-end (spec → ApiManifest JSON)
- HTTP form submission and JSON response

**How to validate Phase 1:**

**Step 1: Build and test**
```bash
mvn clean test
# Expected: 15/15 tests pass
```

**Step 2: Start the app**
```bash
ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run
# Wait for: "Tomcat started on port 8080"
```

**Step 3: Open the form**
Navigate to http://localhost:8080 in your browser. You should see:
- A text area for pasting a spec
- Radio buttons for Java/Python language selection
- A text area for additional context
- A "Generate Project" button

**Step 4: Paste a sample spec**
Copy this minimal OpenAPI spec into the textarea:

```yaml
openapi: "3.0.0"
info:
  title: Pet Store
  version: "1.0.0"
  description: A simple pet store API
paths:
  /pets:
    get:
      operationId: listPets
      summary: List all pets
      parameters:
        - name: limit
          in: query
          schema:
            type: integer
      responses:
        "200":
          description: A list of pets
    post:
      operationId: createPet
      summary: Create a pet
      requestBody:
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/NewPet"
      responses:
        "201":
          description: Created
  /pets/{petId}:
    get:
      operationId: getPet
      summary: Get a pet by ID
      parameters:
        - name: petId
          in: path
          required: true
          schema:
            type: integer
      responses:
        "200":
          description: A pet
components:
  schemas:
    Pet:
      properties:
        id:
          type: integer
        name:
          type: string
      required: [id, name]
    NewPet:
      properties:
        name:
          type: string
      required: [name]
```

**Step 5: Submit and verify response**
1. Select "Java (Spring Boot)"
2. Leave "Additional Context" empty (optional)
3. Click "Generate Project"
4. The page should return **formatted JSON** with structure like:

```json
{
  "title": "Pet Store",
  "version": "1.0.0",
  "description": "A simple pet store API",
  "basePackage": "com.example.petstore",
  "endpoints": [
    {
      "method": "GET",
      "path": "/pets",
      "operationId": "listPets",
      "summary": "List all pets",
      ...
    },
    ...
  ],
  "schemas": [...],
  "endpointCount": 3
}
```

**What to look for:**
- ✓ JSON is valid (not wrapped in markdown, not prose)
- ✓ `endpointCount` is `3` (GET /pets, POST /pets, GET /pets/{petId})
- ✓ All 3 endpoints appear in `endpoints` array
- ✓ `schemas` includes Pet and NewPet
- ✓ `basePackage` is a valid Java package name

**If something fails:**

| Symptom | Cause | Fix |
|---------|-------|-----|
| `ANTHROPIC_API_KEY=changeme` error | API key not set | `export ANTHROPIC_API_KEY=sk-ant-...` |
| Validation error: "max 10 endpoints" | Spec has >10 endpoints | Use a smaller spec (Phase 1 MVP is ≤10) |
| Validation error: "at least one endpoint" | Spec has no operations | Ensure `paths` has HTTP verbs (get, post, etc.) |
| `AgentException: Failed to parse agent response` | Claude returned non-JSON or wrapped response | Check logs, may be prompt issue |
| Browser shows plain text error fragment | Validation failed in SpecParser | Check error message for spec issues |

**Phase 1 Success Criteria:**
- ✓ Tests pass (15/15)
- ✓ App starts without errors
- ✓ Form submits and returns valid `ApiManifest` JSON
- ✓ No ERROR logs (DEBUG/INFO/WARN are fine)
- ✓ Spec is validated (10-endpoint cap enforced)

---

### Phase 2 — Full Pipeline, Java Output (IN PROGRESS)

**What Phase 2 adds:**
- All 5 agents (Architect, Code Generator, Test Writer, Reviewer)
- Generates complete Spring Boot project structure as files
- SessionStore with TTL cleanup (evicts sessions >30min old)
- Download endpoint (returns ZIP with generated project)
- All agent prompts fully implemented
- Async pipeline orchestration with SSE progress streaming
- Real-time progress UI with agent status
- File tree + CodeMirror preview on result page

**How to validate Phase 2:**

**Step 1: Build and test**
```bash
mvn clean test
# Expected: 15/15 tests pass (Phase 1 tests still valid)
```

**Step 2: Start the app**
```bash
ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run
# Wait for: "Tomcat started on port 8080"
# App may take 10-20s to initialize on first run
```

**Step 3: Open the form**
Navigate to http://localhost:8080 in your browser. You should see the same form as Phase 1.

**Step 4: Paste the same sample spec from Phase 1**
(Use the Pet Store example from Phase 1 validation above)

**Step 5: Submit and observe progress stream**
1. Click "Generate Project"
2. Browser should redirect to `/progress/{sessionId}` page
3. Real-time progress should appear with agent names and status:
   - ✓ Spec Analyst: running → done (extracts endpoints)
   - ▶ Architect: running → done (designs project structure)
   - ▶ Code Generator: running → done (generates source files)
   - ▶ Test Writer: running → done (generates test files)
   - ▶ Reviewer: running → done (reviews code quality)
4. When complete, browser auto-redirects to `/result/{sessionId}`

**Step 6: View result page**
You should see:
- **Left panel:** File tree with all generated files (pom.xml, Java source files, test files)
- **Right panel:** CodeMirror preview showing file content
- **Top:** Language, file count, review score
- **Download button:** Returns ZIP with complete project

**Step 7: Verify the generated project**
```bash
# Click Download button and save generated-project.zip
cd /tmp
unzip generated-project.zip
ls -la generated-project/
# Should contain:
#   pom.xml
#   src/main/java/com/example/petstore/PetstoreApplication.java
#   src/main/java/com/example/petstore/controller/
#   src/main/java/com/example/petstore/service/
#   src/main/java/com/example/petstore/model/
#   src/test/java/com/example/petstore/

# Try building the generated project:
cd generated-project
mvn clean compile
# Expected: BUILD SUCCESS (or reasonable compilation errors from generated code placeholders)
```

**Phase 2 Success Criteria:**
- ✓ Tests pass (15/15)
- ✓ App starts without errors
- ✓ Form submits and redirects to `/progress/{id}` page
- ✓ SSE stream shows all 5 agents completing in order
- ✓ Auto-redirect to `/result/{id}` after ~30-60 seconds (Claude API calls)
- ✓ File tree visible on result page with generated files
- ✓ CodeMirror preview updates on file selection
- ✓ Download button returns valid ZIP file
- ✓ ZIP contains Spring Boot project structure (pom.xml, src/, etc.)
- ✓ No ERROR logs (DEBUG/INFO/WARN are expected)

---

### Phase 3 — UI + Streaming (FUTURE)

Enhancements:
- Real-time progress page (currently skeleton)
- Thymeleaf layout + Tailwind styling
- Error handling + GlobalExceptionHandler integration
- CodeMirror file browser with syntax highlighting

**Validation:** Same as Phase 2 + UI is polished

---

### Phase 4 — Python Support + Hardening (FUTURE)

Enhancements:
- Python (FastAPI) output alongside Java
- Concurrency cap enforcement
- Docker build verified
- Rate limit handling

**Validation:** Phase 2 validation + can generate Python projects + Docker build works

---

### Phase 5 — Future Enhancements (FUTURE)

- Auth scaffolding (if spec has `securitySchemes`)
- Node.js / Go support
- Redis session store (multi-instance)
- GitHub push integration

---

## Logging

SLF4J + Logback (Spring Boot default) logs to:
- **stdout** (console)
- **logs/spring.log** (file, created at startup)

### Log Levels

```yaml
# In application.yml to see DEBUG logs:
logging:
  level:
    com.example.apicodegen: DEBUG
    org.springframework.web: INFO
```

**Key loggers:**
- `SpecParser`: Parsing, validation, endpoint counts
- `AgentService`: Claude API calls, request/response details
- `GeneratorController`: HTTP requests and validation
- `GlobalExceptionHandler`: All exceptions
- `SessionStore`: Session lifecycle

Example debug output:
```
21:20:10.123 [nio-8080-exec-1] DEBUG c.e.a.p.SpecParser -- Parsing spec of size 2 KB
21:20:10.124 [nio-8080-exec-1] DEBUG c.e.a.p.SpecParser -- Detected format: yaml
21:20:10.125 [nio-8080-exec-1] DEBUG c.e.a.p.SpecParser -- Spec parsed and validated successfully
21:20:10.126 [nio-8080-exec-1] INFO c.e.a.a.SpecAnalystAgent -- SpecAnalystAgent: starting analysis of 2048 character spec
21:20:10.200 [nio-8080-exec-1] INFO c.e.a.a.AgentService -- Calling Claude API at https://api.anthropic.com/v1/messages
21:20:11.500 [nio-8080-exec-1] INFO c.e.a.a.AgentService -- Claude response received: 1234 characters of content
```

---

## Architecture

### System Flow (Phase 1)

```
Browser
  ↓ GET /
Spring → index.html (form)
  ↓ POST /generate (spec YAML)
GeneratorController
  ↓ parse + validate
SpecParser (10-endpoint cap)
  ↓ analyze
SpecAnalystAgent
  ↓ REST call
Anthropic Claude API (Haiku)
  ↓ JSON response
AgentService.parseJson()
  ↓ ApiManifest record
Browser ← JSON response
```

### Session Store (Phase 2+)

```
SessionStore (in-memory ConcurrentHashMap)
├── sessionId → SessionEntry
│   ├── result: GenerationResult (or null while processing)
│   ├── emitter: SseEmitter (or null after pipeline done)
│   ├── createdAt: Instant
│   └── status: PENDING | RUNNING | DONE | FAILED
└── @Scheduled cleanup every 60s (evict >30min old sessions)
```

---

## Project Structure

```
spec-forge/
├── src/main/java/com/example/apicodegen/
│   ├── ApiCodegenApplication.java
│   ├── config/
│   │   ├── AnthropicConfig.java (RestClient bean)
│   │   └── ExecutorConfig.java (thread pool)
│   ├── web/
│   │   ├── GeneratorController.java
│   │   ├── ProgressController.java (Phase 2)
│   │   ├── ResultController.java (Phase 2)
│   │   └── DownloadController.java (Phase 2)
│   ├── agent/
│   │   ├── AgentService.java (base class)
│   │   ├── SpecAnalystAgent.java (implemented)
│   │   ├── ArchitectAgent.java (stub)
│   │   ├── CodeGeneratorAgent.java (stub)
│   │   ├── TestWriterAgent.java (stub)
│   │   └── ReviewerAgent.java (stub)
│   ├── model/ (all records)
│   ├── parser/ (SpecParser)
│   ├── pipeline/ (Phase 2)
│   ├── store/ (SessionStore)
│   ├── zip/ (Phase 2)
│   └── exception/
├── src/main/resources/
│   ├── templates/ (Thymeleaf)
│   ├── static/ (CSS, JS)
│   ├── prompts/ (system prompts)
│   └── application.yml
├── src/test/java/ (15 tests)
├── pom.xml
├── Dockerfile
└── .env.example
```

---

## Development Notes

### Adding a New Agent (Phase 2+)

1. Create `src/main/java/.../agent/NewAgent.java` extending `AgentService`
2. Write system prompt in `src/main/resources/prompts/newagent-system.txt`
3. Implement `public OutputType process(InputType input)`
4. Add test in `src/test/java/.../agent/NewAgentTest.java`
5. Wire into `PipelineOrchestrator.startAsync()`
6. Add to Phase 2 task tracking

### Testing Philosophy

- Unit tests for parsing, model serialization, agent output parsing
- Test doubles (override methods) instead of mocking the RestClient chain
- 15/15 Phase 1 tests pass with no external services needed

### Environment Variables

```bash
# Required
ANTHROPIC_API_KEY=sk-ant-...

# Optional (see application.yml for defaults)
SERVER_PORT=8080
LOGGING_LEVEL_COM_EXAMPLE_APICODEGEN=DEBUG
```

---

## Next Steps (Phase 2 Implementation)

1. Implement `ArchitectAgent` (Blueprint design)
2. Implement `CodeGeneratorAgent` (parallel file generation)
3. Implement `TestWriterAgent` (test file generation)
4. Implement `ReviewerAgent` (code review)
5. Build `PipelineOrchestrator` (agent sequencing, SSE events)
6. Build controllers: `ProgressController`, `ResultController`, `DownloadController`
7. Wire up `SessionStore` (session lifecycle)
8. Implement `ZipBuilder` (package files)
9. Update all agent prompts
10. Add Phase 2 tests

**Estimated scope:** 15–20 files, ~2000 LOC

---

## Questions?

- **How do I debug a failing spec?** Check `logs/spring.log` for SpecParser validation errors. Set `logging.level.com.example.apicodegen=DEBUG` for detailed trace.
- **Can I use a different Claude model?** Yes, change `anthropic.model` in `application.yml` (note: Opus/Sonnet cost more, Haiku is cheapest).
- **What happens if Claude fails?** Pipeline returns `AgentException`, GlobalExceptionHandler logs it and returns error fragment.
- **Can I run multiple instances?** Not yet (Phase 5): SessionStore is in-memory. Upgrade to Redis for multi-instance.

---

## License

Learning project. Use as reference.
