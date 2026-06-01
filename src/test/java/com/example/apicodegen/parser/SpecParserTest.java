package com.example.apicodegen.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class SpecParserTest {

    private SpecParser parser;

    @BeforeEach
    void setUp() {
        parser = new SpecParser();
    }

    @Test
    void parsesValidYamlSpec() {
        String yaml = """
                openapi: "3.0.0"
                info:
                  title: Pet Store
                  version: "1.0"
                paths:
                  /pets:
                    get:
                      summary: List pets
                      responses:
                        "200":
                          description: ok
                """;

        Map<String, Object> result = parser.parse(yaml);

        assertThat(result).containsKey("paths");
        assertThat(result).containsKey("info");
    }

    @Test
    void parsesValidJsonSpec() {
        String json = """
                {
                  "openapi": "3.0.0",
                  "info": { "title": "Pet Store", "version": "1.0" },
                  "paths": {
                    "/pets": {
                      "get": { "summary": "List pets", "responses": { "200": { "description": "ok" } } }
                    }
                  }
                }
                """;

        Map<String, Object> result = parser.parse(json);

        assertThat(result).containsKey("paths");
    }

    @Test
    void rejectsEmptyInput() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(SpecValidationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(SpecValidationException.class);
    }

    @Test
    void rejectsSpecWithNoPaths() {
        String yaml = """
                openapi: "3.0.0"
                info:
                  title: Empty API
                  version: "1.0"
                """;

        assertThatThrownBy(() -> parser.parse(yaml))
                .isInstanceOf(SpecValidationException.class)
                .hasMessageContaining("paths");
    }

    @Test
    void rejectsSpecWithNoEndpoints() {
        String yaml = """
                openapi: "3.0.0"
                info:
                  title: Empty Paths API
                  version: "1.0"
                paths: {}
                """;

        assertThatThrownBy(() -> parser.parse(yaml))
                .isInstanceOf(SpecValidationException.class)
                .hasMessageContaining("at least one endpoint");
    }

    @Test
    void rejectsSpecWithMoreThanTenEndpoints() {
        // 11 endpoints across paths
        String yaml = """
                openapi: "3.0.0"
                info:
                  title: Big API
                  version: "1.0"
                paths:
                  /a: { get: { responses: { "200": { description: ok } } } }
                  /b: { get: { responses: { "200": { description: ok } } } }
                  /c: { get: { responses: { "200": { description: ok } } } }
                  /d: { get: { responses: { "200": { description: ok } } } }
                  /e: { get: { responses: { "200": { description: ok } } } }
                  /f: { get: { responses: { "200": { description: ok } } } }
                  /g: { get: { responses: { "200": { description: ok } } } }
                  /h: { get: { responses: { "200": { description: ok } } } }
                  /i: { get: { responses: { "200": { description: ok } } } }
                  /j: { get: { responses: { "200": { description: ok } } } }
                  /k: { get: { responses: { "200": { description: ok } } } }
                """;

        assertThatThrownBy(() -> parser.parse(yaml))
                .isInstanceOf(SpecValidationException.class)
                .hasMessageContaining("maximum supported is 10");
    }

    @Test
    void acceptsSpecWithExactlyTenEndpoints() {
        String yaml = """
                openapi: "3.0.0"
                info:
                  title: Boundary API
                  version: "1.0"
                paths:
                  /a: { get: { responses: { "200": { description: ok } } } }
                  /b: { get: { responses: { "200": { description: ok } } } }
                  /c: { get: { responses: { "200": { description: ok } } } }
                  /d: { get: { responses: { "200": { description: ok } } } }
                  /e: { get: { responses: { "200": { description: ok } } } }
                  /f: { get: { responses: { "200": { description: ok } } } }
                  /g: { get: { responses: { "200": { description: ok } } } }
                  /h: { get: { responses: { "200": { description: ok } } } }
                  /i: { get: { responses: { "200": { description: ok } } } }
                  /j: { get: { responses: { "200": { description: ok } } } }
                """;

        assertThatNoException().isThrownBy(() -> parser.parse(yaml));
    }

    @Test
    void detectsYamlFormat() {
        assertThat(parser.detectFormat("openapi: 3.0.0")).isEqualTo("yaml");
    }

    @Test
    void detectsJsonFormat() {
        assertThat(parser.detectFormat("{\"openapi\": \"3.0.0\"}")).isEqualTo("json");
    }

    @Test
    void rejectsMalformedYaml() {
        String badYaml = "paths:\n  bad: [unclosed";

        assertThatThrownBy(() -> parser.parse(badYaml))
                .isInstanceOf(SpecValidationException.class);
    }
}
