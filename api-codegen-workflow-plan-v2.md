# AI-Powered API Code Generation Workflow — Plan v2

> Revised stack: Spring Boot 3.2 + Java 21 monolith, Thymeleaf + HTMX frontend, CodeMirror 6 preview

---

## 1. Executive Summary

A single deployable Spring Boot JAR where users paste an OpenAPI/Swagger spec (YAML or JSON),
choose a target language (Java or Python at launch), and optionally provide extra hints.
A multi-agent AI pipeline (Spec Analyst → Architect → Code Generator → Test Writer → Reviewer)
produces a ~90% implemented codebase delivered as both an in-browser file-tree preview and a
downloadable ZIP — all served from one backend with zero separate frontend build tooling.

---

## 2. Technology Stack

| Layer              | Choice                                   | Rationale                                                                                     |
|--------------------|------------------------------------------|-----------------------------------------------------------------------------------------------|
| Runtime            | Java 17 (LTS)                            | Strong typing; locally installed; minimum required for Spring Boot 3.x                      |
| Framework          | Spring Boot 3.2+                         | Web, SSE, validation, scheduling all in one; familiar ecosystem                              |
| Templating         | Thymeleaf 3.x                            | Server-rendered HTML; zero JS build pipeline for MVP                                         |
| Interactivity      | HTMX 2.x                                 | SSE progress stream + partial HTML swaps without custom JS                                   |
| Code preview       | CodeMirror 6 (CDN)                       | Lightweight, works in plain `<script>` tag, supports Java + Python syntax highlighting       |
| Styling            | Tailwind CSS (CDN play build)            | Utility-first; no PostCSS/Node needed at MVP stage                                           |
| Spec parsing       | `snakeyaml` + `jackson-databind`         | Both already on Spring classpath; handles YAML and JSON                                      |
| AI calls           | Spring `RestClient` → Anthropic API      | Anthropic Java SDK still maturing; raw HTTP is stable and fully controllable                 |
| ZIP generation     | `java.util.zip` (stdlib)                 | No extra dependencies                                                                         |
| SSE streaming      | Spring `SseEmitter`                      | Native Spring support; pairs perfectly with HTMX `hx-ext="sse"`                              |
| Session storage    | In-memory `ConcurrentHashMap` (MVP)      | Simple; upgrade to Redis when scaling beyond one instance                                    |
| Build tool         | Maven                                    | Matches your Java background; Spring Initializr default                                      |
| Deployment         | Single fat JAR / Docker                  | `./mvnw package && java -jar` — deploy anywhere                                              |

---

## 3. System Architecture

```
Browser
  │  GET /          → Thymeleaf renders input page
  │  POST /generate → starts pipeline, returns session ID
  │  GET /progress/{id} (SSE) ← SseEmitter pushes agent progress as HTML fragments
  │  GET /result/{id}  → Thymeleaf renders file-tree + CodeMirror preview
  │  GET /download/{id} → streams ZIP
  │
Spring Boot Monolith (single JAR)
  ├── Web Layer         (Spring MVC controllers + Thymeleaf views)
  ├── Pipeline Service  (orchestrates agents via Virtual Thread executor)
  ├── Agent Services    (one Spring @Service per agent)
  ├── Spec Parser       (snakeyaml + jackson)
  ├── ZIP Builder       (java.util.zip)
  └── Session Store     (ConcurrentHashMap<String, GenerationSession>)
        │
        │ HTTP via RestClient
        ▼
  Anthropic Claude API  (claude-haiku-4-5-20251001)
```

---

## 4. Spring Boot Project Structure

```
spec-forge/                                              ← project root
├── src/
│   ├── main/
│   │   ├── java/com/example/apicodegen/
│   │   │   │
│   │   │   ├── ApiCodegenApplication.java          ← @SpringBootApplication, enables virtual threads
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── AnthropicConfig.java             ← RestClient bean, API key from properties
│   │   │   │   ├── ExecutorConfig.java              ← Virtual thread executor bean
│   │   │   │   └── WebConfig.java                   ← MVC config, static resources
│   │   │   │
│   │   │   ├── web/
│   │   │   │   ├── GeneratorController.java         ← GET /, POST /generate
│   │   │   │   ├── ProgressController.java          ← GET /progress/{id}  (SseEmitter)
│   │   │   │   ├── ResultController.java            ← GET /result/{id}
│   │   │   │   └── DownloadController.java          ← GET /download/{id}
│   │   │   │
│   │   │   ├── pipeline/
│   │   │   │   ├── PipelineOrchestrator.java        ← drives agents in sequence, fires SSE events
│   │   │   │   └── PipelineSession.java             ← immutable record: sessionId, spec, language, context
│   │   │   │
│   │   │   ├── agent/
│   │   │   │   ├── AgentService.java                ← shared base: buildRequest(), callClaude(), parseJson()
│   │   │   │   ├── SpecAnalystAgent.java            ← Agent 1: spec → ApiManifest
│   │   │   │   ├── ArchitectAgent.java              ← Agent 2: manifest → ProjectBlueprint
│   │   │   │   ├── CodeGeneratorAgent.java          ← Agent 3: blueprint → Map<filename, code>
│   │   │   │   ├── TestWriterAgent.java             ← Agent 4: source files → test files
│   │   │   │   └── ReviewerAgent.java               ← Agent 5: all files → ReviewReport
│   │   │   │
│   │   │   ├── model/
│   │   │   │   ├── GenerationRequest.java           ← form-backing bean (spec, language, context)
│   │   │   │   ├── ApiManifest.java                 ← Agent 1 output (record)
│   │   │   │   ├── ProjectBlueprint.java            ← Agent 2 output (record)
│   │   │   │   ├── GeneratedFile.java               ← filename + content + fileType
│   │   │   │   ├── GenerationResult.java            ← full output: files + review notes
│   │   │   │   └── Language.java                    ← enum: JAVA, PYTHON
│   │   │   │
│   │   │   ├── parser/
│   │   │   │   ├── SpecParser.java                  ← detects YAML/JSON, validates, normalises
│   │   │   │   └── SpecValidationException.java
│   │   │   │
│   │   │   ├── store/
│   │   │   │   └── SessionStore.java                ← ConcurrentHashMap + scheduled cleanup (@Scheduled)
│   │   │   │
│   │   │   ├── zip/
│   │   │   │   └── ZipBuilder.java                  ← GenerationResult → byte[]
│   │   │   │
│   │   │   └── exception/
│   │   │       ├── GlobalExceptionHandler.java      ← @ControllerAdvice, returns error Thymeleaf fragment
│   │   │       └── AgentException.java
│   │   │
│   │   └── resources/
│   │       ├── templates/
│   │       │   ├── layout.html                      ← Thymeleaf layout dialect base template
│   │       │   ├── index.html                       ← Input form (spec paste, language picker, context)
│   │       │   ├── progress.html                    ← SSE progress page (HTMX sse-connect)
│   │       │   ├── result.html                      ← File tree + CodeMirror preview + download button
│   │       │   ├── fragments/
│   │       │   │   ├── progress-event.html          ← SSE fragment: one agent status line
│   │       │   │   ├── file-tree.html               ← Reusable file tree component
│   │       │   │   └── error.html                   ← Inline error fragment
│   │       │   └── error/
│   │       │       └── 500.html
│   │       │
│   │       ├── static/
│   │       │   ├── css/
│   │       │   │   └── app.css                      ← Minimal custom styles on top of Tailwind CDN
│   │       │   └── js/
│   │       │       └── preview.js                   ← CodeMirror 6 initialisation (file-tree click → load file)
│   │       │
│   │       ├── application.yml                      ← Server port, Anthropic key (env var), session TTL
│   │       ├── application-local.yml                ← Local overrides (gitignored)
│   │       └── prompts/                             ← Externalised prompt templates (easier to tune)
│   │           ├── analyst-system.txt
│   │           ├── architect-system.txt
│   │           ├── codegen-system.txt
│   │           ├── testwriter-system.txt
│   │           └── reviewer-system.txt
│   │
│   └── test/
│       └── java/com/example/apicodegen/
│           ├── parser/SpecParserTest.java
│           ├── agent/SpecAnalystAgentTest.java       ← uses Mockito to mock RestClient
│           ├── agent/CodeGeneratorAgentTest.java
│           ├── pipeline/PipelineOrchestratorTest.java
│           ├── zip/ZipBuilderTest.java
│           └── web/GeneratorControllerTest.java      ← MockMvc integration test
│
├── pom.xml
├── Dockerfile
├── .env.example                                     ← ANTHROPIC_API_KEY=
├── .gitignore
└── README.md
```

---

## 5. Maven `pom.xml` — Key Dependencies

```xml
<dependencies>
    <!-- Spring Boot starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Thymeleaf extras -->
    <dependency>
        <groupId>nz.net.ultraq.thymeleaf</groupId>
        <artifactId>thymeleaf-layout-dialect</artifactId>
    </dependency>

    <!-- YAML + JSON parsing -->
    <dependency>
        <groupId>org.yaml</groupId>
        <artifactId>snakeyaml</artifactId>    <!-- already pulled in by spring-boot-starter -->
    </dependency>

    <!-- Utilities -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

> `java.util.zip` and `java.net.http` are stdlib — no extra entries needed.
> Anthropic is called via Spring's `RestClient` (built into `spring-boot-starter-web`).

---

## 6. Parallel Agent Batch Calls

Agent 3 (Code Generator) fans out file generation across multiple parallel Claude calls.
With Java 17 a `CachedThreadPool` handles the I/O-bound HTTP calls cleanly:

```java
// ExecutorConfig.java
@Bean
public ExecutorService agentExecutor() {
    return Executors.newCachedThreadPool();
}

// CodeGeneratorAgent.java  (sketch)
List<Future<GeneratedFile>> futures = batch.stream()
    .map(fileSpec -> agentExecutor.submit(() -> generateFile(fileSpec, blueprint, manifest)))
    .toList();

List<GeneratedFile> results = futures.stream()
    .map(f -> { try { return f.get(); } catch (Exception e) { throw new AgentException(e); } })
    .toList();
```

Each `generateFile()` call blocks on a `RestClient` HTTP call to Anthropic — the cached
thread pool creates threads on demand and reuses idle ones, which is efficient for the
short-lived parallel bursts this pipeline produces.

---

## 7. SSE Progress Streaming with HTMX

### Server side — `SseEmitter`

```java
// ProgressController.java
@GetMapping(value = "/progress/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter progress(@PathVariable String id) {
    SseEmitter emitter = new SseEmitter(180_000L); // 3-min timeout
    sessionStore.registerEmitter(id, emitter);
    return emitter;
}

// PipelineOrchestrator.java  — called after each agent completes
void pushProgress(String sessionId, String agentName, String status, String detail) {
    String html = templateEngine.process("fragments/progress-event",
        new Context(Locale.getDefault(), Map.of(
            "agentName", agentName,
            "status", status,        // "running" | "done" | "error"
            "detail", detail
        )));
    sessionStore.getEmitter(sessionId)
        .ifPresent(e -> e.send(SseEmitter.event().data(html)));
}
```

### Client side — `progress.html`

```html
<!-- HTMX listens on SSE and appends each fragment into #progress-list -->
<div hx-ext="sse"
     sse-connect="/progress/[(${sessionId})]"
     sse-swap="message"
     hx-target="#progress-list"
     hx-swap="beforeend">
</div>
<ul id="progress-list" class="space-y-2 font-mono text-sm">
  <!-- agent status lines appear here in real time -->
</ul>
```

### Progress event fragment — `fragments/progress-event.html`

```html
<li th:fragment="event" class="flex items-center gap-3">
  <span th:classappend="${status == 'done'} ? 'text-green-500' :
                         (${status == 'error'} ? 'text-red-500' : 'text-yellow-400')">
    <span th:text="${status == 'done'} ? '✓' : (${status == 'error'} ? '✗' : '⟳')"></span>
  </span>
  <span th:text="${agentName}" class="font-semibold"></span>
  <span th:text="${detail}" class="text-gray-400"></span>
</li>
```

When the pipeline finishes, the final SSE event includes an `hx-redirect` header to send
the browser to `/result/{sessionId}` automatically.

---

## 8. Multi-Agent Pipeline — unchanged responsibilities, Java implementation notes

| Agent | Spring component | Key implementation note |
|---|---|---|
| Spec Analyst | `SpecAnalystAgent.java` | Sends raw spec + system prompt; response parsed to `ApiManifest` record via Jackson |
| Architect | `ArchitectAgent.java` | Receives `ApiManifest` JSON; returns `ProjectBlueprint` JSON; strict output schema in prompt |
| Code Generator | `CodeGeneratorAgent.java` | One `RestClient` call per file; batched via virtual thread executor; results merged into `Map<String, GeneratedFile>` |
| Test Writer | `TestWriterAgent.java` | Receives source file content + manifest; generates parallel test files same way as Agent 3 |
| Reviewer | `ReviewerAgent.java` | Receives all file contents concatenated (or summarised if large); returns `ReviewReport` record |

System prompts are loaded from `src/main/resources/prompts/*.txt` at startup via
`@Value("classpath:prompts/analyst-system.txt")` — keeping them out of Java source makes
iterating on prompt quality fast without recompiling.

### Pipeline Failure Behaviour

**Fail-fast, no retries.** If any agent throws, the pipeline marks the session as `FAILED`,
sends a final SSE error fragment naming the failing agent and reason, then completes the emitter.
No retry loops — retries would silently double cost with no guarantee of success.
The user sees a clear error message and a "Start over" link.

---

## 9. Thymeleaf Page Flow

```
GET /
└── index.html
    Spec textarea (YAML/JSON)
    Language radio: Java | Python
    Additional context textarea
    [Generate] button
          │
          │ POST /generate (form submit)
          ▼
    PipelineOrchestrator.startAsync(session)
          │ returns sessionId immediately
          ▼
GET /progress/{sessionId}          ← redirect after POST
└── progress.html
    HTMX SSE stream → appends agent status lines in real time
    Auto-redirect to /result/{sessionId} when pipeline completes
          │
          ▼
GET /result/{sessionId}
└── result.html
    Left: file tree (Thymeleaf th:each over GeneratedFile list)
    Right: CodeMirror 6 read-only editor
    [Download ZIP] → GET /download/{sessionId}
    [Start over]   → GET /
```

---

## 10. CodeMirror 6 Integration in Thymeleaf

```html
<!-- result.html — CDN imports, no npm/Node needed -->
<script type="module">
import { EditorView, basicSetup } from "https://esm.sh/@codemirror/basic-setup@0.20";
import { java }   from "https://esm.sh/@codemirror/lang-java@6";
import { python } from "https://esm.sh/@codemirror/lang-python@6";
import { oneDark } from "https://esm.sh/@codemirror/theme-one-dark@6";

const lang = /*[[${language}]]*/ 'java';
const langExtension = lang === 'python' ? python() : java();

let editor = new EditorView({
    doc: document.getElementById('initial-content').textContent,
    extensions: [basicSetup, langExtension, oneDark, EditorView.editable.of(false)],
    parent: document.getElementById('editor-mount')
});

// File tree click → swap editor content
document.querySelectorAll('.file-tree-item').forEach(item => {
    item.addEventListener('click', () => {
        const content = item.dataset.content;
        editor.dispatch({
            changes: { from: 0, to: editor.state.doc.length, insert: content }
        });
        document.querySelectorAll('.file-tree-item').forEach(i => i.classList.remove('active'));
        item.classList.add('active');
    });
});
</script>
```

File contents are embedded in the Thymeleaf template as `data-content` attributes on each
tree node — no extra AJAX call needed to load a file on click.

---

## 11. Generated Project Output

### Java (Spring Boot)
- `pom.xml`, `ApiApplication.java`, `application.yml`
- `controller/`, `service/`, `repository/`, `model/`, `dto/`, `exception/`
- JUnit 5 + MockMvc tests, `application-test.yml` with H2 in-memory database
- `.gitignore` (`.idea/`, `target/`, `*.class`, `.env`, `.DS_Store`)

### Python (FastAPI)
- `requirements.txt`, `pyproject.toml`, `main.py`, `alembic.ini`
- `routers/`, `schemas/`, `services/`, `models/`, `core/`
- `pytest` tests, `conftest.py`, in-memory SQLite fixture
- `.gitignore` (`__pycache__/`, `.env`, `venv/`, `.pytest_cache/`)

Both outputs include an auto-generated `README.md` with setup instructions.

---

## 12. `application.yml`

```yaml
server:
  port: 8080

spring:
  threads:
    virtual:
      enabled: true          # Java 21 virtual threads for all request handling

anthropic:
  api-key: ${ANTHROPIC_API_KEY}   # injected from environment — never hardcoded
  model: claude-haiku-4-5-20251001  # cheapest tier; upgrade to Sonnet/Opus when quality warrants
  max-tokens: 4096
  base-url: https://api.anthropic.com/v1

codegen:
  session:
    ttl-minutes: 30          # sessions purged after 30 minutes
    max-concurrent: 10       # reject new requests above this threshold
  spec:
    max-endpoints: 10        # MVP limit; enterprise specs out of scope at launch
    max-size-kb: 128         # reasonable upper bound for ≤10-endpoint specs
```

---

## 13. Dockerfile

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./mvnw -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/api-codegen-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run:
```bash
docker build -t api-codegen .
docker run -p 8080:8080 -e ANTHROPIC_API_KEY=sk-ant-... api-codegen
```

---

## 14. Decisions Log

These questions were resolved before implementation began.

| # | Topic | Decision | Rationale |
|---|-------|----------|-----------|
| 1 | Database default | H2 in-memory | Simplest for MVP; user can swap to PostgreSQL/MySQL later via `application.yml` |
| 2 | Auth scaffolding | Skip for MVP | Adds complexity with no immediate value; deferred to Phase 5 |
| 3 | Spec size limit | Max 10 endpoints | Learning project scope; enterprise specs deferred |
| 4 | Claude model tier | `claude-haiku-4-5-20251001` | Lowest cost; upgrade path to Sonnet/Opus when quality becomes the bottleneck |
| 5 | Multi-tenancy | Personal tool | No app-level auth needed; no rate-limit strategy beyond `max-concurrent` |
| 6 | Pipeline failure | Fail-fast, no retries | Cheapest approach; clear error surfaced to user with agent name and reason |
| 7 | Agent output schemas | Designed in Phase 1 | `ApiManifest`, `ProjectBlueprint`, `ReviewReport` records defined during scaffold |
| 8 | Prompt templates | Built from scratch | No pre-existing prompts; authored as part of each agent's phase |

---

## 15. Phase & Task Tracking

Each phase must build and be independently testable before the next begins.

### Phase 1 — Walking Skeleton

| Task | Details | Status |
|------|---------|--------|
| Maven project scaffold | `pom.xml`, `ApiCodegenApplication.java`, all packages created with stub classes | Not Started |
| Model records designed | `ApiManifest`, `ProjectBlueprint`, `GeneratedFile`, `GenerationResult`, `ReviewReport`, `Language` enum | Not Started |
| `SpecParser` implemented | Detects YAML vs JSON, validates structure, counts endpoints, enforces 10-endpoint cap | Not Started |
| `SpecValidationException` | Typed exception with message for invalid/oversized specs | Not Started |
| `AnthropicConfig` | `RestClient` bean wired to Anthropic base URL + API key from env | Not Started |
| `AgentService` base class | `buildRequest()`, `callClaude()`, `parseJson()` shared helpers | Not Started |
| `analyst-system.txt` prompt | System prompt instructing Haiku to return strict `ApiManifest` JSON | Not Started |
| `SpecAnalystAgent` end-to-end | Sends spec → receives `ApiManifest` JSON → deserialises to record | Not Started |
| `GeneratorController` stub | `GET /` returns index page; `POST /generate` returns plain-text session ID | Not Started |
| Minimal `index.html` | Spec textarea, language radio, context textarea, submit button | Not Started |
| `SpecParserTest` | Unit tests: valid YAML, valid JSON, >10 endpoints, missing `paths`, malformed input | Not Started |
| `SpecAnalystAgentTest` | Mocked `RestClient`; verifies request shape and JSON-to-record deserialisation | Not Started |

### Phase 2 — Full Pipeline, Java Output

| Task | Details | Status |
|------|---------|--------|
| `ArchitectAgent` + prompt | Receives `ApiManifest`; returns `ProjectBlueprint` with file list and package structure | Not Started |
| `CodeGeneratorAgent` + prompt | Parallel virtual-thread fan-out; one call per file; merges into `Map<String, GeneratedFile>` | Not Started |
| `TestWriterAgent` + prompt | Generates JUnit 5 test per source file | Not Started |
| `ReviewerAgent` + prompt | Summarises all files; returns `ReviewReport` with issues list | Not Started |
| `PipelineOrchestrator` | Sequences all 5 agents; stores result in `SessionStore` | Not Started |
| `PipelineSession` record | Immutable: sessionId, spec, language, hints | Not Started |
| `SessionStore` | `ConcurrentHashMap` + `@Scheduled` TTL cleanup | Not Started |
| `ZipBuilder` | `GenerationResult → byte[]` ZIP archive | Not Started |
| `/download/{id}` endpoint | Streams ZIP as `application/zip` response | Not Started |
| `ZipBuilderTest` | Verifies ZIP contains expected file paths and non-empty content | Not Started |
| `PipelineOrchestratorTest` | Mocked agents; verifies sequencing and session state transitions | Not Started |

### Phase 3 — UI + Streaming

| Task | Details | Status |
|------|---------|--------|
| `layout.html` | Thymeleaf Layout Dialect base template with Tailwind CDN | Not Started |
| `progress.html` | HTMX SSE listener; appends agent status fragments in real time | Not Started |
| `fragments/progress-event.html` | Single agent status line fragment (running/done/error styling) | Not Started |
| `ProgressController` + `SseEmitter` | Registers emitter per session; pipeline pushes HTML fragments | Not Started |
| `result.html` | File tree left panel + CodeMirror 6 right panel + download button | Not Started |
| `fragments/file-tree.html` | Reusable tree component with `data-content` attributes | Not Started |
| `preview.js` | CodeMirror 6 init; file-tree click swaps editor content | Not Started |
| `GlobalExceptionHandler` | `@ControllerAdvice`; returns error fragment for HTMX + full page for direct hits | Not Started |
| `fragments/error.html` + `500.html` | Inline error fragment and full-page 500 template | Not Started |
| `app.css` | Minimal custom styles on top of Tailwind CDN | Not Started |
| `GeneratorControllerTest` | MockMvc: GET /, POST /generate redirect, invalid spec returns error fragment | Not Started |

### Phase 4 — Python Support + Hardening

| Task | Details | Status |
|------|---------|--------|
| Python output in `CodeGeneratorAgent` | FastAPI project structure: `routers/`, `schemas/`, `services/`, `models/` | Not Started |
| `TestWriterAgent` Python mode | `pytest` tests + `conftest.py` with SQLite fixture | Not Started |
| `codegen-system.txt` Python branch | Separate prompt section for FastAPI/Python conventions | Not Started |
| Concurrency cap enforcement | Reject `POST /generate` when active sessions ≥ `max-concurrent` | Not Started |
| Docker build verified | Multi-stage Dockerfile builds clean; app starts with env var | Not Started |
| README.md | Setup, env var docs, example spec, screenshot | Not Started |
| `CodeGeneratorAgentTest` Python | Verifies Python file structure from a sample spec | Not Started |

### Phase 5 — Future Enhancements

| Task | Details | Status |
|------|---------|--------|
| Auth scaffolding | If spec defines `securitySchemes` (JWT/API key/OAuth2), generate stubs with TODO comments | Not Started |
| Node.js output | Express / NestJS project structure | Not Started |
| Go output | Gin project structure | Not Started |
| Redis session store | Replace `ConcurrentHashMap` for multi-instance scaling | Not Started |
| GitHub push integration | Push generated project directly to a new GitHub repo | Not Started |
| Custom framework picker | Quarkus vs Spring Boot for Java; Flask vs FastAPI for Python | Not Started |
| React frontend | Extract Thymeleaf UI when complexity warrants a proper SPA | Not Started |
| Multi-tenancy / app auth | Add login layer when opening to external users | Not Started |

---

## 16. Agent Output Schemas

Designed in Phase 1. All records use Jackson for deserialisation; Claude is instructed to return
**only** a JSON object matching the schema — no markdown fences, no prose.

### `ApiManifest` (Agent 1 output)

```json
{
  "title": "Pet Store API",
  "version": "1.0.0",
  "description": "Brief summary of what the API does",
  "basePackage": "com.example.petstore",
  "endpoints": [
    {
      "method": "GET",
      "path": "/pets",
      "operationId": "listPets",
      "summary": "List all pets",
      "requestBody": null,
      "responseSchema": "Pet[]",
      "pathParams": [],
      "queryParams": ["limit", "offset"],
      "tags": ["pets"]
    }
  ],
  "schemas": [
    {
      "name": "Pet",
      "fields": [
        { "name": "id", "type": "integer", "required": true },
        { "name": "name", "type": "string", "required": true }
      ]
    }
  ],
  "endpointCount": 1
}
```

### `ProjectBlueprint` (Agent 2 output)

```json
{
  "projectName": "pet-store-api",
  "language": "JAVA",
  "framework": "spring-boot",
  "database": "h2",
  "packageRoot": "com.example.petstore",
  "filePlan": [
    {
      "filename": "src/main/java/com/example/petstore/controller/PetController.java",
      "fileType": "CONTROLLER",
      "purpose": "REST controller for /pets endpoints",
      "dependsOn": ["PetService", "PetDto"]
    }
  ]
}
```

### `ReviewReport` (Agent 5 output)

```json
{
  "overallScore": "good",
  "issues": [
    {
      "severity": "warning",
      "file": "src/main/java/.../PetController.java",
      "line": null,
      "message": "Missing @Valid annotation on request body parameter"
    }
  ],
  "suggestions": ["Consider adding OpenAPI annotations for auto-generated docs"],
  "summary": "Code is well-structured. One missing validation annotation."
}
```
