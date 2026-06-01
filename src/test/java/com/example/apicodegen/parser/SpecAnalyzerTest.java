package com.example.apicodegen.parser;

import com.example.apicodegen.model.ApiManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecAnalyzerTest {

    private SpecParser parser;
    private SpecAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        parser = new SpecParser();
        analyzer = new SpecAnalyzer();
    }

    @Test
    void extractsTitleVersionAndDescription() {
        String yaml = """
                openapi: "3.0.0"
                info:
                  title: Pet Store
                  version: "1.0.0"
                  description: A simple pet store API
                paths:
                  /pets:
                    get:
                      summary: List pets
                      responses:
                        "200":
                          description: ok
                """;
        ApiManifest manifest = analyze(yaml);
        assertThat(manifest.title()).isEqualTo("Pet Store");
        assertThat(manifest.version()).isEqualTo("1.0.0");
        assertThat(manifest.description()).isEqualTo("A simple pet store API");
    }

    @Test
    void derivesBasePackageFromTitle() {
        ApiManifest manifest = analyze(singleEndpointYaml("Pet Store API"));
        assertThat(manifest.basePackage()).isEqualTo("com.example.pet.store");
    }

    @Test
    void extractsEndpointMethodAndPath() {
        String yaml = """
                openapi: "3.0.0"
                info:
                  title: Test
                  version: "1.0"
                paths:
                  /pets:
                    get:
                      operationId: listPets
                      summary: List all pets
                      responses:
                        "200":
                          description: ok
                    post:
                      operationId: createPet
                      summary: Create a pet
                      responses:
                        "201":
                          description: created
                """;
        ApiManifest manifest = analyze(yaml);
        assertThat(manifest.endpointCount()).isEqualTo(2);
        assertThat(manifest.endpoints()).extracting(ApiManifest.EndpointInfo::method)
                .containsExactlyInAnyOrder("GET", "POST");
        assertThat(manifest.endpoints()).extracting(ApiManifest.EndpointInfo::path)
                .containsOnly("/pets");
    }

    @Test
    void usesOperationIdWhenPresent() {
        ApiManifest manifest = analyze("""
                openapi: "3.0.0"
                info:
                  title: Test
                  version: "1.0"
                paths:
                  /pets:
                    get:
                      operationId: listPets
                      responses:
                        "200":
                          description: ok
                """);
        assertThat(manifest.endpoints().get(0).operationId()).isEqualTo("listPets");
    }

    @Test
    void derivesOperationIdWhenMissing() {
        ApiManifest manifest = analyze("""
                openapi: "3.0.0"
                info:
                  title: Test
                  version: "1.0"
                paths:
                  /pets/{petId}:
                    get:
                      responses:
                        "200":
                          description: ok
                """);
        assertThat(manifest.endpoints().get(0).operationId()).isEqualTo("getPetsByPetId");
    }

    @Test
    void extractsPathAndQueryParams() {
        ApiManifest manifest = analyze("""
                openapi: "3.0.0"
                info:
                  title: Test
                  version: "1.0"
                paths:
                  /pets/{petId}:
                    get:
                      parameters:
                        - name: petId
                          in: path
                        - name: format
                          in: query
                      responses:
                        "200":
                          description: ok
                """);
        ApiManifest.EndpointInfo ep = manifest.endpoints().get(0);
        assertThat(ep.pathParams()).containsExactly("petId");
        assertThat(ep.queryParams()).containsExactly("format");
    }

    @Test
    void extractsSchemasFromComponents() {
        ApiManifest manifest = analyze("""
                openapi: "3.0.0"
                info:
                  title: Test
                  version: "1.0"
                paths:
                  /pets:
                    get:
                      responses:
                        "200":
                          description: ok
                components:
                  schemas:
                    Pet:
                      properties:
                        id:
                          type: integer
                        name:
                          type: string
                      required: [id, name]
                """);
        assertThat(manifest.schemas()).hasSize(1);
        ApiManifest.SchemaInfo pet = manifest.schemas().get(0);
        assertThat(pet.name()).isEqualTo("Pet");
        assertThat(pet.fields()).extracting(ApiManifest.FieldInfo::name)
                .containsExactlyInAnyOrder("id", "name");
        assertThat(pet.fields()).filteredOn(f -> f.name().equals("id"))
                .first().extracting(ApiManifest.FieldInfo::required).isEqualTo(true);
    }

    @Test
    void extractsRequestBodySchemaRef() {
        ApiManifest manifest = analyze("""
                openapi: "3.0.0"
                info:
                  title: Test
                  version: "1.0"
                paths:
                  /pets:
                    post:
                      requestBody:
                        content:
                          application/json:
                            schema:
                              $ref: "#/components/schemas/NewPet"
                      responses:
                        "201":
                          description: created
                """);
        assertThat(manifest.endpoints().get(0).requestBody()).isEqualTo("NewPet");
    }

    @Test
    void extractsResponseSchemaRef() {
        ApiManifest manifest = analyze("""
                openapi: "3.0.0"
                info:
                  title: Test
                  version: "1.0"
                paths:
                  /pets:
                    get:
                      responses:
                        "200":
                          content:
                            application/json:
                              schema:
                                $ref: "#/components/schemas/Pet"
                          description: ok
                """);
        assertThat(manifest.endpoints().get(0).responseSchema()).isEqualTo("Pet");
    }

    @Test
    void endpointCountMatchesEndpointsList() {
        String yaml = """
                openapi: "3.0.0"
                info:
                  title: Test
                  version: "1.0"
                paths:
                  /a: { get: { responses: { "200": { description: ok } } } }
                  /b: { post: { responses: { "201": { description: ok } } } }
                  /c: { put: { responses: { "200": { description: ok } } } }
                """;
        ApiManifest manifest = analyze(yaml);
        assertThat(manifest.endpointCount()).isEqualTo(manifest.endpoints().size()).isEqualTo(3);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ApiManifest analyze(String yaml) {
        Map<String, Object> parsed = parser.parse(yaml);
        return analyzer.analyze(parsed);
    }

    private String singleEndpointYaml(String title) {
        return """
                openapi: "3.0.0"
                info:
                  title: %s
                  version: "1.0"
                paths:
                  /ping:
                    get:
                      responses:
                        "200":
                          description: ok
                """.formatted(title);
    }
}
