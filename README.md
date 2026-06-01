# SpecForge

AI-powered API code generation from OpenAPI/Swagger specs.  
Paste a spec → pick a language → get a runnable codebase in one click.

---

## How It Works

```
POST /generate
    │
    ├─ SpecParser      → validates YAML/JSON, enforces ≤10 endpoint cap  (pure Java)
    ├─ SpecAnalyzer    → extracts endpoints, schemas, base package         (pure Java)
    ├─ ProjectBlueprintBuilder → decides every file to generate           (pure Java)
    │
    └─ CodeGenerationAgent → ONE Claude call with full blueprint
           │
           └─ GenerationResponseParser → parses {"files": [...]}
                    │
                    └─ SessionStore → stores result → redirect to /result/{id}
```

**Single Claude call per run.** All spec analysis and project planning is done in pure Java — the AI only writes code.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Anthropic API key | — | [console.anthropic.com](https://console.anthropic.com) |
| Python | 3.11+ | Only needed to *run* generated Python output |
| pip / venv | — | Only needed to *run* generated Python output |

---

## Quick Start

```bash
# 1. Start with a real API key (calls Claude)
ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run

# 2. Start in offline/stub mode (no API key needed, returns fixture response)
mvn spring-boot:run -Dspring.profiles.active=test

# 3. Open the app
open http://localhost:8080
```

On startup, look for one of these log banners to confirm which mode is active:

```
# Real mode:
[REAL] LLM mode: LIVE — API calls will be made
[REAL] provider=anthropic, model=claude-haiku-4-5-20251001

# Stub mode:
[STUB] LLM mode: OFFLINE — using fixture response
[STUB] No real API calls will be made. Zero cost.
```

---

## Switching LLM Providers

Edit `src/main/resources/application.yml` — no code changes needed:

```yaml
# Anthropic (default)
llm:
  provider: anthropic
  model: claude-haiku-4-5-20251001
  base-url: https://api.anthropic.com/v1
  api-key: ${ANTHROPIC_API_KEY}

# Ollama (local, free, no rate limits)
llm:
  provider: ollama
  model: llama3.1:8b          # requires: brew install ollama && ollama pull llama3.1:8b
  base-url: http://localhost:11434/v1
  api-key: ollama

# Groq (free tier, high limits)
llm:
  provider: groq
  model: llama-3.1-8b-instant
  base-url: https://api.groq.com/openai/v1
  api-key: ${GROQ_API_KEY}
```

---

## Sample OpenAPI Spec (for testing)

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

---

## Validation Guide

### Running the Test Suite

```bash
mvn clean test
# Expected: 21 tests, 0 failures
# Note: tests run offline — no LLM calls, no API key needed
```

---

### End-to-End Validation — Java (Spring Boot)

**Step 1 — Generate the project**

1. Start the app: `ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run`
2. Open [http://localhost:8080](http://localhost:8080)
3. Paste the Pet Store spec above
4. Select **Java (Spring Boot)**
5. Click **Generate Project** — button disables, spinner appears
6. Wait 30–90 seconds for the single Claude call to complete
7. You land on `/result/{id}` with a file tree and CodeMirror preview

**Step 2 — Verify the result page**

| Check | Expected |
|-------|----------|
| File tree (left panel) | Lists generated files: `pom.xml`, `*.java` files |
| CodeMirror preview (right panel) | Shows file content with syntax highlighting |
| File count | Roughly 10–14 files for the Pet Store spec |
| No error banner on the page | Generation succeeded |

**Step 3 — Download and inspect the ZIP**

Click **Download ZIP** → save `generated-project.zip`, then:

```bash
cd /tmp
unzip generated-project.zip -d pet-store-generated
ls -R pet-store-generated/
```

**Expected file structure:**

```
pet-store-generated/
├── pom.xml
├── .gitignore
├── README.md
├── src/
│   └── main/
│       ├── java/com/example/pet/store/
│       │   ├── PetStoreApplication.java
│       │   ├── controller/
│       │   │   └── PetsController.java
│       │   ├── service/
│       │   │   └── PetsService.java
│       │   ├── repository/
│       │   │   └── PetsRepository.java
│       │   ├── model/
│       │   │   ├── Pet.java
│       │   │   └── NewPet.java
│       │   ├── dto/
│       │   │   └── PetDto.java
│       │   └── exception/
│       │       └── GlobalExceptionHandler.java
│       └── resources/
│           └── application.yml
```

**Step 4 — Compile the generated project**

```bash
cd /tmp/pet-store-generated

# Compile only (fastest check)
mvn clean compile

# Or build fully
mvn clean package -DskipTests
```

**Expected outcome:**
```
[INFO] BUILD SUCCESS
```

If there are compilation errors, they are typically:
- Missing imports (Claude forgot one) — add manually
- Wrong package name — check `pom.xml` `<groupId>` matches the Java package
- These are expected ~10–20% of the time with Haiku; use a higher-tier model for better quality

**Step 5 — Run the generated project**

```bash
cd /tmp/pet-store-generated

# If compile succeeded:
mvn spring-boot:run
# OR
java -jar target/pet-store-*.jar
```

Verify the API is up:

```bash
# List pets (GET /pets)
curl http://localhost:8080/pets
# Expected: 200 OK with JSON array (may be empty: [])

# Create a pet (POST /pets)
curl -X POST http://localhost:8080/pets \
  -H "Content-Type: application/json" \
  -d '{"name": "Buddy"}'
# Expected: 201 Created with pet JSON

# Get a pet (GET /pets/1)
curl http://localhost:8080/pets/1
# Expected: 200 OK with pet JSON
```

**Java validation success criteria:**

- [ ] `mvn clean compile` exits with `BUILD SUCCESS`
- [ ] `pom.xml` has correct `groupId`, Spring Boot parent, H2 and JPA dependencies
- [ ] Controller class exists with `@RestController` and mapped endpoints
- [ ] Service class exists and is injected into controller
- [ ] Repository interface extends `JpaRepository`
- [ ] `GET /pets` returns 200, `POST /pets` returns 201

---

### End-to-End Validation — Python (FastAPI)

**Step 1 — Generate the project**

1. Start the app: `ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run`
2. Open [http://localhost:8080](http://localhost:8080)
3. Paste the Pet Store spec above
4. Select **Python (FastAPI)**
5. Click **Generate Project** — button disables, spinner appears
6. Wait 30–90 seconds
7. You land on `/result/{id}` with a file tree and CodeMirror preview

**Step 2 — Verify the result page**

| Check | Expected |
|-------|----------|
| File tree | Lists `main.py`, `requirements.txt`, `routers/`, `models/`, `schemas/` |
| CodeMirror preview | Shows Python code with syntax highlighting |
| File count | Roughly 8–12 files for the Pet Store spec |

**Step 3 — Download and inspect the ZIP**

Click **Download ZIP** → save `generated-project.zip`, then:

```bash
cd /tmp
unzip generated-project.zip -d pet-store-python
ls -R pet-store-python/
```

**Expected file structure:**

```
pet-store-python/
├── main.py
├── requirements.txt
├── database.py
├── exceptions.py
├── .gitignore
├── README.md
├── models/
│   ├── pet.py
│   └── newpet.py
├── schemas/
│   ├── pet.py
│   └── newpet.py
├── routers/
│   └── pets.py
└── services/
    └── pets_service.py
```

**Step 4 — Install dependencies and run**

```bash
cd /tmp/pet-store-python

# Create a virtual environment
python3 -m venv venv
source venv/bin/activate          # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Start the server
uvicorn main:app --reload
# Expected: "Uvicorn running on http://127.0.0.1:8000"
```

**Step 5 — Test the API**

```bash
# List pets (GET /pets)
curl http://localhost:8000/pets
# Expected: 200 OK with JSON array

# Create a pet (POST /pets)
curl -X POST http://localhost:8000/pets \
  -H "Content-Type: application/json" \
  -d '{"name": "Whiskers"}'
# Expected: 201 Created with pet JSON

# Get a pet (GET /pets/1)
curl http://localhost:8000/pets/1
# Expected: 200 OK with pet JSON

# View auto-generated API docs
open http://localhost:8000/docs
# FastAPI generates Swagger UI automatically
```

**Python validation success criteria:**

- [ ] `pip install -r requirements.txt` completes without errors
- [ ] `uvicorn main:app` starts without `ImportError` or `SyntaxError`
- [ ] `http://localhost:8000/docs` shows the Swagger UI with all endpoints
- [ ] `GET /pets` returns 200, `POST /pets` returns 201
- [ ] All routers are registered in `main.py`

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Spinner shows but page never loads | Claude is slow (30–90s is normal) | Wait; check logs for `[REAL CLAUDE]` |
| `Generation response was truncated` | Spec has too many files for a single call | Reduce spec to ≤5 endpoints |
| `Rate limit exceeded after 4 retries` | Anthropic free-tier limit hit | Wait 1–2 minutes or switch to Ollama/Groq |
| `AgentException: LLM response is not valid JSON` | Claude didn't follow response format | Retry — Haiku occasionally misbehaves |
| `ANTHROPIC_API_KEY=changeme` error | API key not set | `export ANTHROPIC_API_KEY=sk-ant-...` |
| Java generated project: `BUILD FAILURE` | Missing import or wrong package | Add import manually; or retry with Sonnet |
| Python generated project: `ImportError` | Claude used wrong module path | Fix the import path manually; or retry |
| Result page shows empty file tree | Generation succeeded but files array empty | Check logs for parse errors |

---

## Logging

### LLM Mode (visible at startup)

```
[REAL] LLM mode: LIVE — API calls will be made
[STUB] LLM mode: OFFLINE — using fixture response
```

### Per-request

```
[REAL CLAUDE] Calling Anthropic API — model=claude-haiku-4-5-20251001
[STUB]        Returning canned fixture response (no API call)
[REAL LLM]    Calling OpenAI-compatible API — model=llama3.1:8b
```

### Enable DEBUG logging

Add to `application.yml`:

```yaml
logging:
  level:
    com.example.apicodegen: DEBUG
```

---

## Project Structure

```
spec-forge/
├── src/main/java/com/example/apicodegen/
│   ├── blueprint/      ProjectBlueprintBuilder  — pure Java, derives file list
│   ├── parser/         SpecParser + SpecAnalyzer — pure Java, no AI
│   ├── prompt/         PromptBuilder             — serialises manifest to JSON
│   ├── agent/          CodeGenerationAgent       — THE single Claude call
│   ├── response/       GenerationResponseParser  — parses {"files": [...]}
│   ├── llm/            LlmClient interface + Anthropic/OpenAI/Stub impls
│   ├── model/          Records: ApiManifest, ProjectBlueprint, GenerationResult
│   ├── store/          SessionStore              — in-memory, TTL cleanup
│   ├── zip/            ZipBuilder
│   ├── web/            GeneratorController       — GET /, POST /generate, result, download
│   ├── config/         LlmClientConfig, LlmProperties
│   └── exception/      GlobalExceptionHandler, AgentException
├── src/main/resources/
│   ├── prompts/codegen-system.txt   — the only system prompt
│   ├── templates/                   — Thymeleaf (index.html, result.html)
│   └── application.yml
├── src/test/
│   ├── java/            SpecParserTest, SpecAnalyzerTest (21 tests, offline)
│   └── resources/fixtures/sample-claude-response.json
└── pom.xml
```

---

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `ANTHROPIC_API_KEY` | Yes (real mode) | `changeme` | Anthropic API key |
| `GROQ_API_KEY` | Only for Groq | — | Groq API key |
| `SERVER_PORT` | No | `8080` | HTTP port |
