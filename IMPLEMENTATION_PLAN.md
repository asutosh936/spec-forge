# SpecForge — Implementation Plan
> Lean MVP: Spring Boot 3.2 + Java 21 · Thymeleaf + HTMX · Single Claude API call per generation

---

## 1. Project Goal

A standalone Spring Boot web application where a user:
1. Pastes an OpenAPI spec (YAML or JSON)
2. Chooses a target language (Java or Python)
3. Optionally adds free-text hints (e.g. "use PostgreSQL", "add JWT auth")
4. Clicks **Generate** and receives a fully-structured, ~90% implemented codebase
   as both an in-browser file-tree preview and a downloadable ZIP

**Core constraints:**
- Minimum Claude API calls — exactly **one call per generation run**
- All non-generative work (parsing, validation, blueprint, ZIP) done in pure Java
- Full test suite must run **offline** with zero API calls (stub client via Spring profiles)
- Single deployable fat JAR — no separate frontend build pipeline

---

## 2. Technology Stack

| Layer | Choice |
|---|---|
| Java | 21 (LTS) — virtual threads enabled |
| Framework | Spring Boot 3.2+ |
| Templating | Thymeleaf 3.x + HTMX 2.x |
| Code preview | CodeMirror 6 via CDN (esm.sh) |
| Styling | Tailwind CSS via CDN |
| Spec parsing | `swagger-parser` 2.1.22 (OpenAPI 3.x + Swagger 2.x) |
| AI HTTP client | Spring `RestClient` → Anthropic API |
| ZIP | `java.util.zip` (stdlib) |
| Build | Maven |
| Testing | JUnit 5 + Mockito + MockMvc |
| Deployment | Single fat JAR / Docker |

---

## 3. Maven `pom.xml` — Full Dependencies

```xml
<properties>
    <java.version>21</java.version>
</properties>

<dependencies>
    <!-- Spring Boot -->
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

    <!-- Thymeleaf layout dialect -->
    <dependency>
        <groupId>nz.net.ultraq.thymeleaf</groupId>
        <artifactId>thymeleaf-layout-dialect</artifactId>
    </dependency>

    <!-- OpenAPI spec parsing + validation (handles YAML/JSON, $ref resolution) -->
    <dependency>
        <groupId>io.swagger.parser.v3</groupId>
        <artifactId>swagger-parser</artifactId>
        <version>2.1.22</version>
    </dependency>

    <!-- Lombok — reduces boilerplate -->
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

> `java.util.zip`, `java.net.http`, and virtual threads are all stdlib in Java 21.
> Anthropic is called via Spring's built-in `RestClient` — no extra HTTP dependency needed.

---

## 4. Project Structure

```
specforge/
├── src/
│   ├── main/
│   │   ├── java/com/specforge/
│   │   │   │
│   │   │   ├── SpecForgeApplication.java
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── AnthropicConfig.java
│   │   │   │   └── WebConfig.java
│   │   │   │
│   │   │   ├── web/
│   │   │   │   ├── GeneratorController.java
│   │   │   │   └── DownloadController.java
│   │   │   │
│   │   │   ├── model/
│   │   │   │   ├── GenerationRequest.java
│   │   │   │   ├── GenerationResult.java
│   │   │   │   ├── GeneratedFile.java
│   │   │   │   ├── ApiManifest.java
│   │   │   │   ├── ProjectBlueprint.java
│   │   │   │   └── Language.java
│   │   │   │
│   │   │   ├── parser/
│   │   │   │   ├── SpecParser.java
│   │   │   │   └── SpecValidationException.java
│   │   │   │
│   │   │   ├── blueprint/
│   │   │   │   └── ProjectBlueprintBuilder.java
│   │   │   │
│   │   │   ├── prompt/
│   │   │   │   └── PromptBuilder.java
│   │   │   │
│   │   │   ├── client/
│   │   │   │   ├── AnthropicClient.java          ← interface (the seam)
│   │   │   │   ├── AnthropicRestClient.java      ← real impl, @Profile("!test")
│   │   │   │   └── RateLimitException.java
│   │   │   │
│   │   │   ├── agent/
│   │   │   │   └── CodeGenerationAgent.java      ← single Claude call
│   │   │   │
│   │   │   ├── response/
│   │   │   │   └── GenerationResponseParser.java
│   │   │   │
│   │   │   ├── zip/
│   │   │   │   └── ZipBuilder.java
│   │   │   │
│   │   │   ├── store/
│   │   │   │   └── SessionStore.java
│   │   │   │
│   │   │   └── exception/
│   │   │       └── GlobalExceptionHandler.java
│   │   │
│   │   └── resources/
│   │       ├── templates/
│   │       │   ├── layout.html
│   │       │   ├── index.html
│   │       │   ├── result.html
│   │       │   ├── fragments/
│   │       │   │   └── error.html
│   │       │   └── error/
│   │       │       └── 500.html
│   │       │
│   │       ├── static/
│   │       │   ├── css/app.css
│   │       │   └── js/preview.js
│   │       │
│   │       ├── prompts/
│   │       │   └── codegen-system.txt            ← externalised system prompt
│   │       │
│   │       ├── application.yml
│   │       └── application-local.yml             ← gitignored local overrides
│   │
│   └── test/
│       ├── java/com/specforge/
│       │   ├── parser/SpecParserTest.java
│       │   ├── blueprint/ProjectBlueprintBuilderTest.java
│       │   ├── prompt/PromptBuilderTest.java
│       │   ├── agent/CodeGenerationAgentTest.java
│       │   ├── response/GenerationResponseParserTest.java
│       │   ├── zip/ZipBuilderTest.java
│       │   └── web/GeneratorControllerTest.java
│       │
│       └── resources/
│           ├── fixtures/
│           │   ├── sample-openapi.yaml            ← test spec
│           │   └── sample-claude-response.json    ← canned Claude response
│           └── application-test.yml
│
├── pom.xml
├── Dockerfile
├── .env.example
├── .gitignore
└── README.md
```

---

## 5. Class-by-Class Implementation Guide

### 5.1 `SpecForgeApplication.java`

```java
@SpringBootApplication
public class SpecForgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpecForgeApplication.class, args);
    }
}
```

Enable virtual threads in `application.yml` (not in code):
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

---

### 5.2 `config/AnthropicConfig.java`

Declare a `RestClient` bean pointed at the Anthropic base URL.
Inject `anthropic.api-key` from properties.
Set default headers: `Content-Type: application/json`, `x-api-key`, `anthropic-version: 2023-06-01`.

```java
@Configuration
public class AnthropicConfig {

    @Value("${anthropic.api-key}")
    private String apiKey;

    @Value("${anthropic.base-url}")
    private String baseUrl;

    @Bean
    public RestClient anthropicRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
```

---

### 5.3 `model/Language.java`

```java
public enum Language {
    JAVA,
    PYTHON;

    public static Language fromString(String value) {
        return valueOf(value.toUpperCase());
    }
}
```

---

### 5.4 `model/GenerationRequest.java`

Form-backing bean. Validated with Bean Validation annotations.

Fields:
- `String spec` — `@NotBlank`
- `Language language` — `@NotNull`
- `String context` — optional, nullable

---

### 5.5 `model/ApiManifest.java`

Immutable record produced by `SpecParser`. Captures everything downstream needs.

```java
public record ApiManifest(
    String title,
    String version,
    List<EndpointInfo> endpoints,
    List<SchemaInfo> schemas,
    AuthInfo auth,           // null if no securitySchemes
    Language language,
    String userContext       // forwarded from GenerationRequest
) {}
```

Supporting records:

```java
public record EndpointInfo(
    String path,
    String method,           // GET, POST, PUT, DELETE, PATCH
    String operationId,
    String summary,
    String requestBodySchema,
    String responseSchema,
    List<String> pathParams,
    List<String> queryParams,
    boolean requiresAuth
) {}

public record SchemaInfo(
    String name,
    Map<String, String> fields   // fieldName → type
) {}

public record AuthInfo(
    String type,             // "bearer", "apiKey", "oauth2"
    String location          // "header", "query" — for apiKey
) {}
```

---

### 5.6 `model/ProjectBlueprint.java`

Immutable record produced by `ProjectBlueprintBuilder`. Describes every file to be generated before any AI call is made.

```java
public record ProjectBlueprint(
    String basePackage,
    String projectName,
    Language language,
    List<FileSpec> filesToGenerate
) {}

public record FileSpec(
    String relativePath,     // e.g. "src/main/java/com/example/controller/UserController.java"
    String fileType,         // "controller", "service", "model", "dto", "test", "config", "gitignore"
    String description       // short hint for the AI: "REST controller for /users endpoints"
) {}
```

---

### 5.7 `model/GeneratedFile.java`

```java
public record GeneratedFile(
    String relativePath,
    String content
) {}
```

---

### 5.8 `model/GenerationResult.java`

```java
public record GenerationResult(
    String sessionId,
    List<GeneratedFile> files,
    Language language,
    Instant generatedAt
) {
    // Convenience: find file by path for UI rendering
    public Optional<GeneratedFile> findFile(String path) {
        return files.stream().filter(f -> f.relativePath().equals(path)).findFirst();
    }
}
```

---

### 5.9 `parser/SpecParser.java`

**Pure Java — zero AI calls.**

Uses `io.swagger.parser.OpenAPIParser` (from `swagger-parser` dependency).

Responsibilities:
- Accept raw spec string (YAML or JSON — `swagger-parser` detects automatically)
- Parse and validate via `new OpenAPIParser().readContents(...)`
- Collect `parseResult.getMessages()` — if any messages are errors, throw `SpecValidationException` with the message list included
- Walk `openAPI.getPaths()` to build `List<EndpointInfo>`
- Walk `openAPI.getComponents().getSchemas()` to build `List<SchemaInfo>`
- Walk `openAPI.getComponents().getSecuritySchemes()` to build `AuthInfo`
- Return a fully populated `ApiManifest`

**Important implementation notes:**
- `swagger-parser` resolves `$ref` automatically — treat all schemas as already resolved
- For `requestBody` and `response` schemas, serialize back to a compact JSON string to pass into the manifest (the AI needs the shape, not the parsed object)
- Cap: if `endpoints.size() > 25`, throw `SpecValidationException("Spec exceeds 25-endpoint MVP limit")` — return a user-friendly error, do not proceed to AI call

```java
@Component
public class SpecParser {

    public ApiManifest parse(GenerationRequest request) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);

        SwaggerParseResult result = new OpenAPIParser()
                .readContents(request.spec(), null, options);

        if (result.getOpenAPI() == null) {
            throw new SpecValidationException("Invalid spec: " + result.getMessages());
        }

        OpenAPI api = result.getOpenAPI();
        // ... build and return ApiManifest
    }
}
```

---

### 5.10 `blueprint/ProjectBlueprintBuilder.java`

**Pure Java — zero AI calls.**

Takes an `ApiManifest` and deterministically decides every file that needs to be generated.

**For Java (Spring Boot):**

Always generate:
- `pom.xml`
- `{ProjectName}Application.java`
- `application.yml`
- `application-test.yml` (H2 in-memory config)
- `exception/GlobalExceptionHandler.java`
- `exception/ResourceNotFoundException.java`
- `.gitignore`
- `README.md`

Per schema in `manifest.schemas()`:
- `model/{Name}.java` — JPA entity
- `dto/{Name}RequestDto.java`
- `dto/{Name}ResponseDto.java`

Per resource tag (group endpoints by first path segment, e.g. `/users/*` → `users`):
- `controller/{Resource}Controller.java`
- `service/{Resource}Service.java`
- `repository/{Resource}Repository.java`

If `manifest.auth() != null`:
- `config/SecurityConfig.java`
- `security/JwtFilter.java` (if bearer)

Per resource tag (tests):
- `test/controller/{Resource}ControllerTest.java`
- `test/service/{Resource}ServiceTest.java`

**For Python (FastAPI):**

Always generate:
- `requirements.txt`
- `main.py`
- `core/config.py`
- `core/database.py`
- `dependencies.py`
- `exceptions.py`
- `.gitignore`
- `README.md`
- `tests/conftest.py`

Per schema:
- `models/{name}.py`
- `schemas/{name}.py`

Per resource tag:
- `routers/{resource}.py`
- `services/{resource}_service.py`

Per resource tag (tests):
- `tests/test_{resource}.py`

**Base package naming:**
- Java: derive from project title — sanitise to lowercase alphanumeric, e.g. `"Pet Store API"` → `com.petstore`
- Python: lowercase, underscored project name as top-level directory

---

### 5.11 `prompt/PromptBuilder.java`

**Pure Java — zero AI calls.**

Serialises `ApiManifest` + `ProjectBlueprint` into the user message sent to Claude.

Responsibilities:
- Load system prompt from `classpath:prompts/codegen-system.txt` once at startup (`@PostConstruct`)
- Build a compact JSON user message containing:
  - The manifest (endpoints, schemas, auth)
  - The blueprint (list of files with their descriptions)
  - The target language
  - User's optional context hint
- Keep the serialisation compact — omit nulls, use short field names where readable

```java
@Component
public class PromptBuilder {

    @Value("classpath:prompts/codegen-system.txt")
    private Resource systemPromptResource;

    private String systemPrompt;

    @PostConstruct
    void init() throws IOException {
        this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    public String getSystemPrompt() { return systemPrompt; }

    public String buildUserMessage(ApiManifest manifest, ProjectBlueprint blueprint) {
        // Serialise to compact JSON via Jackson ObjectMapper
        // Structure:
        // {
        //   "language": "JAVA",
        //   "context": "use PostgreSQL",
        //   "manifest": { ... },
        //   "filesToGenerate": [ { "path": "...", "type": "controller", "hint": "..." } ]
        // }
    }
}
```

---

### 5.12 `resources/prompts/codegen-system.txt`

This is the most important tuning knob in the entire system. Keep it externalised.

Content guidance (write the actual file with this content):

```
You are an expert software engineer. You will receive a JSON object describing an API spec
and a list of files to generate. Generate ALL files in one response.

RULES:
1. Respond ONLY with a valid JSON object. No markdown, no prose, no code fences.
2. Response format:
   {
     "files": [
       { "path": "relative/path/to/File.java", "content": "full file content here" },
       ...
     ]
   }
3. Every file in the input "filesToGenerate" array MUST appear in your output.
4. Implementation completeness:
   - All method signatures must be real and fully implemented.
   - All request/response mapping must be complete.
   - CRUD service methods must contain real logic using the repository/database layer.
   - Use // TODO only for complex business rules that are genuinely unknowable from the spec.
5. For Java: use Spring Boot 3.2, Lombok, JPA, Bean Validation. Package: as provided.
6. For Python: use FastAPI, Pydantic v2, SQLAlchemy 2.x async. Follow the layout provided.
7. Tests must cover: one happy-path test and one error-path test per endpoint.
8. Never hardcode secrets. Use @Value / pydantic Settings for config.
9. .gitignore must be complete for the target language and framework.
10. README.md must include: project description, prerequisites, how to run, environment variables.
```

---

### 5.13 `client/AnthropicClient.java` — Interface (the testability seam)

```java
public interface AnthropicClient {
    /**
     * Send a system prompt + user message to Claude.
     * Returns the raw text content of the first response block.
     * Implementations handle retry, backoff, and error mapping.
     */
    String complete(String systemPrompt, String userMessage);
}
```

---

### 5.14 `client/AnthropicRestClient.java` — Production Implementation

Active on all profiles except `test`.

Responsibilities:
- Build Anthropic `/v1/messages` request body:
  ```json
  {
    "model": "${anthropic.model}",
    "max_tokens": 8192,
    "system": "<systemPrompt>",
    "messages": [{ "role": "user", "content": "<userMessage>" }]
  }
  ```
- POST to `/v1/messages` via the `RestClient` bean
- Parse response: extract `content[0].text`
- **Retry logic for 429:**
  - Max retries: 4
  - Delays: 2s, 4s, 8s, 16s (exponential backoff, `Thread.sleep` — virtual thread parks cheaply)
  - On HTTP 429, read `Retry-After` header if present; use it instead of calculated delay
  - On HTTP 5xx, retry with same backoff
  - On HTTP 4xx (except 429), throw immediately — no retry (bad request, auth failure, etc.)
- Log each retry attempt at WARN level with attempt number and wait duration
- Throw `RateLimitException` if all retries exhausted

```java
@Component
@Profile("!test")
public class AnthropicRestClient implements AnthropicClient {

    private static final int MAX_RETRIES = 4;
    private static final long BASE_DELAY_MS = 2000L;

    private final RestClient restClient;

    @Value("${anthropic.model}")
    private String model;

    // constructor injection of RestClient bean

    @Override
    public String complete(String systemPrompt, String userMessage) {
        int attempt = 0;
        while (true) {
            try {
                return callApi(systemPrompt, userMessage);
            } catch (RateLimitException e) {
                if (++attempt > MAX_RETRIES) throw e;
                long wait = BASE_DELAY_MS * (1L << attempt);
                log.warn("Claude 429 — attempt {}/{}, waiting {}ms", attempt, MAX_RETRIES, wait);
                Thread.sleep(wait);  // virtual thread: parks, does not block carrier thread
            }
        }
    }

    private String callApi(String systemPrompt, String userMessage) {
        // build request body map, POST, parse response content[0].text
        // throw RateLimitException on 429, RuntimeException on other errors
    }
}
```

---

### 5.15 `client/StubAnthropicClient.java` — Test Implementation

Active only on `test` profile. Reads a canned response from the test fixtures directory.
Zero network calls. Zero cost.

```java
@Component
@Profile("test")
public class StubAnthropicClient implements AnthropicClient {

    @Value("classpath:fixtures/sample-claude-response.json")
    private Resource fixture;

    @Override
    public String complete(String systemPrompt, String userMessage) {
        return fixture.getContentAsString(StandardCharsets.UTF_8);
    }
}
```

The fixture file `src/test/resources/fixtures/sample-claude-response.json` must contain a realistic
Claude response matching the expected output format (a JSON object with a `"files"` array).
Create this fixture once by running the app manually against a real spec, copying the real Claude
response, and saving it. All subsequent test runs use this fixture.

---

### 5.16 `agent/CodeGenerationAgent.java`

The **only class in the app that calls `AnthropicClient`**.

```java
@Service
public class CodeGenerationAgent {

    private final AnthropicClient anthropicClient;
    private final PromptBuilder promptBuilder;
    private final GenerationResponseParser responseParser;

    // constructor injection

    public List<GeneratedFile> generate(ApiManifest manifest, ProjectBlueprint blueprint) {
        String systemPrompt = promptBuilder.getSystemPrompt();
        String userMessage  = promptBuilder.buildUserMessage(manifest, blueprint);
        String rawResponse  = anthropicClient.complete(systemPrompt, userMessage);
        return responseParser.parse(rawResponse);
    }
}
```

---

### 5.17 `response/GenerationResponseParser.java`

**Pure Java — zero AI calls.**

Parses Claude's JSON response into `List<GeneratedFile>`.

Responsibilities:
- Use Jackson `ObjectMapper` to parse the raw JSON string
- Extract the `"files"` array
- Map each element to a `GeneratedFile` record
- Guard against malformed responses:
  - If JSON parse fails → throw `IllegalStateException` with the raw response truncated to 500 chars for debugging
  - If `"files"` array is missing or empty → throw `IllegalStateException`
  - Strip any accidental markdown fences (` ```java `) from `content` fields — the prompt forbids them but add a defensive trim

---

### 5.18 `zip/ZipBuilder.java`

**Pure Java — `java.util.zip`.**

Takes a `List<GeneratedFile>` and returns a `byte[]`.

```java
@Component
public class ZipBuilder {

    public byte[] build(List<GeneratedFile> files, String projectName) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (GeneratedFile file : files) {
                ZipEntry entry = new ZipEntry(projectName + "/" + file.relativePath());
                zos.putNextEntry(entry);
                zos.write(file.content().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return baos.toByteArray();
        }
    }
}
```

---

### 5.19 `store/SessionStore.java`

In-memory store. Holds `GenerationResult` objects keyed by session ID.
Auto-purges sessions older than the configured TTL.

```java
@Component
public class SessionStore {

    private final Map<String, GenerationResult> store = new ConcurrentHashMap<>();

    @Value("${specforge.session.ttl-minutes:30}")
    private int ttlMinutes;

    public void save(GenerationResult result) {
        store.put(result.sessionId(), result);
    }

    public Optional<GenerationResult> find(String sessionId) {
        return Optional.ofNullable(store.get(sessionId));
    }

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void evictExpired() {
        Instant cutoff = Instant.now().minus(ttlMinutes, ChronoUnit.MINUTES);
        store.values().removeIf(r -> r.generatedAt().isBefore(cutoff));
    }
}
```

Add `@EnableScheduling` to `SpecForgeApplication`.

---

### 5.20 `web/GeneratorController.java`

Handles the main form flow.

```java
@Controller
public class GeneratorController {

    private final SpecParser specParser;
    private final ProjectBlueprintBuilder blueprintBuilder;
    private final CodeGenerationAgent codeGenerationAgent;
    private final ZipBuilder zipBuilder;
    private final SessionStore sessionStore;

    // constructor injection

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("request", new GenerationRequest());
        model.addAttribute("languages", Language.values());
        return "index";
    }

    @PostMapping("/generate")
    public String generate(@Valid @ModelAttribute GenerationRequest request,
                           BindingResult bindingResult,
                           Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("languages", Language.values());
            return "index";
        }

        ApiManifest manifest       = specParser.parse(request);
        ProjectBlueprint blueprint = blueprintBuilder.build(manifest);
        List<GeneratedFile> files  = codeGenerationAgent.generate(manifest, blueprint);
        String sessionId           = UUID.randomUUID().toString();

        sessionStore.save(new GenerationResult(sessionId, files, request.language(), Instant.now()));

        return "redirect:/result/" + sessionId;
    }

    @GetMapping("/result/{sessionId}")
    public String result(@PathVariable String sessionId, Model model) {
        GenerationResult result = sessionStore.find(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session expired"));

        model.addAttribute("result", result);
        model.addAttribute("firstFile", result.files().get(0));
        return "result";
    }
}
```

---

### 5.21 `web/DownloadController.java`

```java
@Controller
public class DownloadController {

    private final SessionStore sessionStore;
    private final ZipBuilder zipBuilder;

    @GetMapping("/download/{sessionId}")
    public ResponseEntity<byte[]> download(@PathVariable String sessionId) throws IOException {

        GenerationResult result = sessionStore.find(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        byte[] zip = zipBuilder.build(result.files(), "specforge-output");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"specforge-output.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }
}
```

---

### 5.22 `exception/GlobalExceptionHandler.java`

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SpecValidationException.class)
    public String handleSpecValidation(SpecValidationException ex, Model model) {
        model.addAttribute("error", ex.getMessage());
        model.addAttribute("request", new GenerationRequest());
        model.addAttribute("languages", Language.values());
        return "index";   // re-render the form with inline error
    }

    @ExceptionHandler(RateLimitException.class)
    public String handleRateLimit(Model model) {
        model.addAttribute("error", "Claude API is busy. Please try again in a minute.");
        model.addAttribute("request", new GenerationRequest());
        model.addAttribute("languages", Language.values());
        return "index";
    }
}
```

---

## 6. Thymeleaf Templates

### 6.1 `layout.html`

Base layout using Thymeleaf Layout Dialect.

Includes in `<head>`:
- Tailwind CSS: `<script src="https://cdn.tailwindcss.com"></script>`
- HTMX: `<script src="https://unpkg.com/htmx.org@2.0.0"></script>`

Defines two fragments:
- `layout:fragment="content"` — main page content slot
- A simple nav bar with the SpecForge logo and a "New generation" link to `/`

### 6.2 `index.html`

The input form. Key elements:

```html
<!-- Spec input — large textarea, monospaced font -->
<textarea name="spec" rows="20"
          placeholder="Paste your OpenAPI spec here (YAML or JSON)..."></textarea>

<!-- Language picker — radio buttons, not a dropdown -->
<label><input type="radio" name="language" value="JAVA" checked> Java (Spring Boot)</label>
<label><input type="radio" name="language" value="PYTHON"> Python (FastAPI)</label>

<!-- Optional context -->
<textarea name="context" rows="3"
          placeholder="Optional: e.g. use PostgreSQL, add JWT auth, use Lombok"></textarea>

<!-- Submit — disable on click to prevent double-submit -->
<button type="submit" onclick="this.disabled=true; this.form.submit()">
  Generate
</button>

<!-- Inline error display -->
<div th:if="${error}" th:text="${error}"></div>

<!-- Loading indicator — shown while POST is in-flight -->
<div id="loading" style="display:none">
  Generating your codebase... this takes 15–30 seconds.
</div>
```

Add a small JS snippet to show `#loading` on form submit:
```html
<script>
document.querySelector('form').addEventListener('submit', () => {
  document.getElementById('loading').style.display = 'block';
});
</script>
```

### 6.3 `result.html`

Two-panel layout:

**Left panel — file tree:**
```html
<ul>
  <li th:each="file : ${result.files}"
      class="file-tree-item cursor-pointer"
      th:attr="data-path=${file.relativePath}, data-content=${file.content}"
      th:text="${file.relativePath}">
  </li>
</ul>
```

**Right panel — CodeMirror editor:**
```html
<div id="editor-mount"></div>

<!-- Embed first file's content for initial render -->
<pre id="initial-content" style="display:none"
     th:text="${firstFile.content}"></pre>
```

**Download button:**
```html
<a th:href="@{/download/{id}(id=${result.sessionId})}"
   class="download-btn">
  Download ZIP
</a>
```

**CodeMirror 6 initialisation** (`static/js/preview.js`):
```javascript
import { EditorView, basicSetup } from "https://esm.sh/@codemirror/basic-setup@0.20";
import { java }   from "https://esm.sh/@codemirror/lang-java@6";
import { python } from "https://esm.sh/@codemirror/lang-python@6";
import { oneDark } from "https://esm.sh/@codemirror/theme-one-dark@6";

// language is injected via a data attribute on <body>
const lang = document.body.dataset.language === 'PYTHON' ? python() : java();

let editor = new EditorView({
    doc: document.getElementById('initial-content').textContent,
    extensions: [basicSetup, lang, oneDark, EditorView.editable.of(false)],
    parent: document.getElementById('editor-mount')
});

document.querySelectorAll('.file-tree-item').forEach(item => {
    item.addEventListener('click', () => {
        editor.dispatch({
            changes: { from: 0, to: editor.state.doc.length, insert: item.dataset.content }
        });
        document.querySelectorAll('.file-tree-item').forEach(i => i.classList.remove('active'));
        item.classList.add('active');
    });
});
```

Include in `result.html` as:
```html
<script type="module" src="/js/preview.js"></script>
```

---

## 7. Configuration Files

### 7.1 `application.yml`

```yaml
server:
  port: 8080

spring:
  threads:
    virtual:
      enabled: true
  thymeleaf:
    cache: false   # set to true in production

anthropic:
  api-key: ${ANTHROPIC_API_KEY}
  base-url: https://api.anthropic.com
  model: claude-sonnet-4-20250514

specforge:
  session:
    ttl-minutes: 30
  spec:
    max-endpoints: 25
```

### 7.2 `application-local.yml` (gitignored)

```yaml
anthropic:
  api-key: sk-ant-your-key-here

spring:
  thymeleaf:
    cache: false
```

### 7.3 `src/test/resources/application-test.yml`

```yaml
spring:
  profiles:
    active: test

anthropic:
  api-key: stub
  model: stub
```

---

## 8. Testing Strategy

### Principle
The `test` Spring profile activates `StubAnthropicClient` automatically.
**No test ever makes a real API call.**

### Test classes

**`SpecParserTest.java`**
- Load `fixtures/sample-openapi.yaml`
- Assert correct endpoint count, schema names, auth type
- Test invalid YAML throws `SpecValidationException`
- Test spec with 26 endpoints throws `SpecValidationException`

**`ProjectBlueprintBuilderTest.java`**
- Given a known `ApiManifest` (2 resources, 3 schemas), assert correct file list for Java
- Assert correct file list for Python
- Assert base package derived correctly from API title

**`PromptBuilderTest.java`**
- Assert system prompt loads without error
- Assert user message is valid JSON (parse with Jackson)
- Assert all manifest endpoints appear in the user message

**`CodeGenerationAgentTest.java`**
- Uses `StubAnthropicClient` via `@SpringBootTest` with `test` profile
- Assert returns correct number of `GeneratedFile` objects
- Assert `relativePath` values match blueprint file list

**`GenerationResponseParserTest.java`**
- Test valid JSON response parses correctly
- Test missing `"files"` key throws `IllegalStateException`
- Test malformed JSON throws `IllegalStateException`
- Test markdown fence stripping works

**`ZipBuilderTest.java`**
- Build a ZIP from 3 test files
- Open with `ZipInputStream`, assert all 3 entries present with correct content

**`GeneratorControllerTest.java`** (MockMvc)
- `GET /` returns 200, model contains `request` and `languages`
- `POST /generate` with valid form data redirects to `/result/{id}`
- `POST /generate` with blank spec returns to `index` with errors
- `GET /result/{id}` with valid session returns 200
- `GET /result/unknown` returns 404
- `GET /download/{id}` returns `application/octet-stream`

---

## 9. Dockerfile

```dockerfile
# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Cache dependencies separately from source
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

COPY src/ src/
RUN ./mvnw -q package -DskipTests

# ── Stage 2: Extract Spring Boot layers ───────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS layers
WORKDIR /app
COPY --from=build /app/target/specforge-*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ── Stage 3: Minimal runtime ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
WORKDIR /app

# Copy layers — least-changed first for optimal cache hits on redeploy
COPY --from=layers /app/dependencies/          ./
COPY --from=layers /app/spring-boot-loader/    ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/           ./

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
```

### Build and run

```bash
# Build image
docker build -t specforge .

# Run locally
docker run -p 8080:8080 -e ANTHROPIC_API_KEY=sk-ant-... specforge

# Run with memory limit (activates MaxRAMPercentage)
docker run -p 8080:8080 -m 512m -e ANTHROPIC_API_KEY=sk-ant-... specforge
```

---

## 10. `.gitignore`

```gitignore
# Maven
target/
*.class

# IDE
.idea/
*.iml
.vscode/

# Environment
.env
.env.local
application-local.yml

# OS
.DS_Store
Thumbs.db

# Logs
*.log
logs/
```

---

## 11. `.env.example`

```bash
ANTHROPIC_API_KEY=sk-ant-your-key-here
```

---

## 12. Phased Delivery

### Phase 1 — MVP (current scope)
- [ ] All classes above implemented
- [ ] Java (Spring Boot) output only
- [ ] 25-endpoint cap enforced
- [ ] Full offline test suite passing
- [ ] Docker build working
- [ ] Deployed and accessible via browser

### Phase 2 — Python support
- [ ] Python (FastAPI) branch in `ProjectBlueprintBuilder`
- [ ] Python section added to `codegen-system.txt` prompt
- [ ] Python fixture added to test resources
- [ ] Python test cases in `SpecParserTest` and `GeneratorControllerTest`

### Phase 3 — Quality improvements
- [ ] Optional reviewer call (user opt-in checkbox on the form)
- [ ] Second Claude call only fires if user checks "Deep review" — clearly labelled
- [ ] `REVIEW_NOTES.md` added to ZIP when reviewer runs
- [ ] Upgrade `SessionStore` to Redis for multi-instance support

### Phase 4 — Additional languages
- [ ] Node.js (Express) support
- [ ] Go (Gin) support
- [ ] Language added to `Language` enum; blueprint + prompt updated per language

---

## 13. Key Design Decisions Summary

| Decision | Choice | Reason |
|---|---|---|
| Number of Claude calls per run | **1** | Minimum cost; one well-prompted call is sufficient for MVP |
| Spec parsing | **swagger-parser (Java)** | Deterministic, free, handles `$ref`, no AI needed |
| Blueprint generation | **Java (`ProjectBlueprintBuilder`)** | File structure is rule-based, not creative |
| Testability | **`AnthropicClient` interface + `@Profile("test")` stub** | Full test suite runs offline, zero API cost |
| 429 handling | **`AnthropicRestClient` with exponential backoff** | Encapsulated in one place; no other class sees it |
| Progress feedback | **Simple loading spinner** | One call = no incremental progress to report; spinner is honest |
| Frontend | **Thymeleaf + HTMX** | Zero build tooling; single JAR; your Java background |
| Code preview | **CodeMirror 6 via CDN** | Works in plain `<script type="module">`; no npm |
| Session storage | **`ConcurrentHashMap` + `@Scheduled` eviction** | Simple; sufficient for MVP; upgrade to Redis in Phase 3 |
