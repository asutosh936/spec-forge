package com.example.apicodegen.parser;

import com.example.apicodegen.model.ApiManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic OpenAPI spec analyser — no AI involved.
 * Handles both OpenAPI 3.x and Swagger 2.0 formats.
 * Input: the parsed spec Map from SpecParser.
 * Output: an ApiManifest record ready for the Architect agent.
 */
@Component
public class SpecAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(SpecAnalyzer.class);

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    public ApiManifest analyze(Map<String, Object> parsedSpec) {
        Map<String, Object> info = getMap(parsedSpec, "info");
        String title       = getString(info, "title", "API");
        String version     = String.valueOf(info.getOrDefault("version", "1.0"));
        String description = getString(info, "description", "");
        String basePackage = deriveBasePackage(title);

        Map<String, Object> paths = getMap(parsedSpec, "paths");
        List<ApiManifest.EndpointInfo> endpoints = extractEndpoints(paths);
        List<ApiManifest.SchemaInfo>   schemas   = extractSchemas(parsedSpec);

        log.info("SpecAnalyzer: extracted {} endpoints, {} schemas from '{}'",
                endpoints.size(), schemas.size(), title);

        return new ApiManifest(title, version, description, basePackage,
                endpoints, schemas, endpoints.size());
    }

    // ── Endpoints ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<ApiManifest.EndpointInfo> extractEndpoints(Map<String, Object> paths) {
        List<ApiManifest.EndpointInfo> endpoints = new ArrayList<>();
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            if (!(pathEntry.getValue() instanceof Map)) continue;
            Map<String, Object> pathItem = (Map<String, Object>) pathEntry.getValue();
            for (String method : HTTP_METHODS) {
                if (pathItem.containsKey(method)) {
                    endpoints.add(extractEndpoint(method, path,
                            (Map<String, Object>) pathItem.get(method)));
                }
            }
        }
        return endpoints;
    }

    @SuppressWarnings("unchecked")
    private ApiManifest.EndpointInfo extractEndpoint(String method, String path,
                                                      Map<String, Object> operation) {
        String operationId = getString(operation, "operationId", deriveOperationId(method, path));
        String summary     = getString(operation, "summary", "");
        List<Object> rawParams = (List<Object>) operation.getOrDefault("parameters", List.of());

        List<String> pathParams  = new ArrayList<>();
        List<String> queryParams = new ArrayList<>();
        for (Object p : rawParams) {
            if (!(p instanceof Map)) continue;
            Map<String, Object> param = (Map<String, Object>) p;
            String name = getString(param, "name", "");
            String in   = getString(param, "in", "");
            if ("path".equals(in))  pathParams.add(name);
            if ("query".equals(in)) queryParams.add(name);
        }

        List<String> tags = new ArrayList<>();
        if (operation.containsKey("tags")) {
            for (Object t : (List<Object>) operation.get("tags")) {
                tags.add(String.valueOf(t));
            }
        }

        String requestBodySchema = extractRequestBodySchema(operation);
        String responseSchema    = extractResponseSchema(operation);

        return new ApiManifest.EndpointInfo(
                method.toUpperCase(), path, operationId, summary,
                requestBodySchema, responseSchema, pathParams, queryParams, tags);
    }

    // ── Schemas ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<ApiManifest.SchemaInfo> extractSchemas(Map<String, Object> spec) {
        Map<String, Object> schemaMap = null;

        // OAS 3.x: components.schemas
        if (spec.containsKey("components")) {
            Map<String, Object> components = getMap(spec, "components");
            schemaMap = (Map<String, Object>) components.get("schemas");
        }
        // Swagger 2.0: definitions
        if (schemaMap == null && spec.containsKey("definitions")) {
            schemaMap = (Map<String, Object>) spec.get("definitions");
        }

        if (schemaMap == null) return List.of();

        List<ApiManifest.SchemaInfo> schemas = new ArrayList<>();
        for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {
            if (entry.getValue() instanceof Map) {
                schemas.add(extractSchema(entry.getKey(),
                        (Map<String, Object>) entry.getValue()));
            }
        }
        return schemas;
    }

    @SuppressWarnings("unchecked")
    private ApiManifest.SchemaInfo extractSchema(String name, Map<String, Object> schemaDef) {
        List<ApiManifest.FieldInfo> fields = new ArrayList<>();
        Map<String, Object> properties = (Map<String, Object>) schemaDef.getOrDefault("properties", Map.of());
        List<String> required = (List<String>) schemaDef.getOrDefault("required", List.of());

        for (Map.Entry<String, Object> prop : properties.entrySet()) {
            String fieldName = prop.getKey();
            String fieldType = "object";
            if (prop.getValue() instanceof Map) {
                Map<String, Object> propDef = (Map<String, Object>) prop.getValue();
                fieldType = resolveFieldType(propDef);
            }
            fields.add(new ApiManifest.FieldInfo(fieldName, fieldType, required.contains(fieldName)));
        }
        return new ApiManifest.SchemaInfo(name, fields);
    }

    // ── Request / Response helpers ────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String extractRequestBodySchema(Map<String, Object> operation) {
        // OAS 3.x
        if (operation.containsKey("requestBody")) {
            Map<String, Object> rb = getMap(operation, "requestBody");
            Map<String, Object> content = (Map<String, Object>) rb.get("content");
            if (content != null) {
                Map<String, Object> mediaType = (Map<String, Object>) content.get("application/json");
                if (mediaType == null && !content.isEmpty()) {
                    mediaType = (Map<String, Object>) content.values().iterator().next();
                }
                if (mediaType != null && mediaType.get("schema") instanceof Map) {
                    return schemaRef((Map<String, Object>) mediaType.get("schema"));
                }
            }
        }
        // Swagger 2.0: body parameter
        List<Object> params = (List<Object>) operation.getOrDefault("parameters", List.of());
        for (Object p : params) {
            if (!(p instanceof Map)) continue;
            Map<String, Object> param = (Map<String, Object>) p;
            if ("body".equals(param.get("in")) && param.get("schema") instanceof Map) {
                return schemaRef((Map<String, Object>) param.get("schema"));
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractResponseSchema(Map<String, Object> operation) {
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        if (responses == null) return "void";
        // Prefer 200, then 201, then first 2xx
        for (String code : List.of("200", "201")) {
            if (responses.containsKey(code) && responses.get(code) instanceof Map) {
                String s = extractSchemaFromResponse((Map<String, Object>) responses.get(code));
                if (s != null) return s;
            }
        }
        return "void";
    }

    @SuppressWarnings("unchecked")
    private String extractSchemaFromResponse(Map<String, Object> response) {
        // OAS 3.x: content.application/json.schema
        if (response.containsKey("content")) {
            Map<String, Object> content = (Map<String, Object>) response.get("content");
            Map<String, Object> mediaType = (Map<String, Object>) content.get("application/json");
            if (mediaType == null && !content.isEmpty()) {
                mediaType = (Map<String, Object>) content.values().iterator().next();
            }
            if (mediaType != null && mediaType.get("schema") instanceof Map) {
                return schemaRef((Map<String, Object>) mediaType.get("schema"));
            }
        }
        // Swagger 2.0: schema directly on response
        if (response.get("schema") instanceof Map) {
            return schemaRef((Map<String, Object>) response.get("schema"));
        }
        return null;
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    private String schemaRef(Map<String, Object> schema) {
        String ref = (String) schema.get("$ref");
        if (ref != null) {
            return ref.substring(ref.lastIndexOf('/') + 1);
        }
        String type = (String) schema.get("type");
        if ("array".equals(type) && schema.get("items") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> items = (Map<String, Object>) schema.get("items");
            String itemRef = (String) items.get("$ref");
            if (itemRef != null) return itemRef.substring(itemRef.lastIndexOf('/') + 1) + "[]";
            return items.getOrDefault("type", "object") + "[]";
        }
        return type != null ? type : "object";
    }

    private String resolveFieldType(Map<String, Object> propDef) {
        String ref = (String) propDef.get("$ref");
        if (ref != null) return ref.substring(ref.lastIndexOf('/') + 1);
        String type = (String) propDef.getOrDefault("type", "object");
        if ("array".equals(type) && propDef.get("items") instanceof Map) {
            return schemaRef((Map<String, Object>) propDef.get("items")) + "[]";
        }
        String format = (String) propDef.get("format");
        if ("integer".equals(type) && "int64".equals(format)) return "long";
        if ("number".equals(type) && "float".equals(format))  return "float";
        return type;
    }

    private String deriveOperationId(String method, String path) {
        // GET /pets/{petId} → getPetByPetId
        StringBuilder sb = new StringBuilder(method.toLowerCase());
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) continue;
            if (segment.startsWith("{") && segment.endsWith("}")) {
                String param = segment.substring(1, segment.length() - 1);
                sb.append("By")
                  .append(Character.toUpperCase(param.charAt(0)))
                  .append(param.substring(1));
            } else {
                sb.append(Character.toUpperCase(segment.charAt(0))).append(segment.substring(1));
            }
        }
        return sb.toString();
    }

    private String deriveBasePackage(String title) {
        // "Pet Store API" → "com.example.petstore"
        String slug = title.toLowerCase()
                .replaceAll("\\b(api|service|app|system)\\b", "")
                .trim()
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("\\.{2,}", ".")
                .replaceAll("^\\.|\\.$", "");
        return "com.example." + slug;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> parent, String key) {
        Object val = parent.get(key);
        return (val instanceof Map) ? (Map<String, Object>) val : Map.of();
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return (val instanceof String s && !s.isBlank()) ? s : defaultValue;
    }
}
