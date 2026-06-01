package com.example.apicodegen.blueprint;

import com.example.apicodegen.model.ApiManifest;
import com.example.apicodegen.model.Language;
import com.example.apicodegen.model.ProjectBlueprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic file-plan builder — no AI call.
 * Derives every file that needs to be generated from the manifest + language.
 */
@Component
public class ProjectBlueprintBuilder {

    private static final Logger log = LoggerFactory.getLogger(ProjectBlueprintBuilder.class);

    public ProjectBlueprint build(ApiManifest manifest, Language language) {
        String projectName = toKebabCase(manifest.title());
        String packageRoot  = manifest.basePackage();
        String packagePath  = packageRoot.replace('.', '/');
        String appClassName = toCamelCase(manifest.title()) + "Application";

        List<ProjectBlueprint.FilePlan> files = language == Language.JAVA
                ? buildJavaFiles(manifest, packageRoot, packagePath, appClassName, projectName)
                : buildPythonFiles(manifest, projectName);

        log.info("Blueprint built for '{}' ({}): {} files", manifest.title(), language, files.size());
        return new ProjectBlueprint(projectName, language, packageRoot, files);
    }

    // ── Java (Spring Boot) ────────────────────────────────────────────────────

    private List<ProjectBlueprint.FilePlan> buildJavaFiles(ApiManifest manifest, String pkg,
                                                            String pkgPath, String appClass,
                                                            String projectName) {
        List<ProjectBlueprint.FilePlan> files = new ArrayList<>();

        // Build files
        files.add(file("pom.xml", "BUILD",
                "Maven build file for Spring Boot 3.2, Java 17, H2, JPA, Lombok, Validation. groupId=" + pkg));
        files.add(file("src/main/java/" + pkgPath + "/" + appClass + ".java", "APPLICATION_MAIN",
                "@SpringBootApplication main class, package=" + pkg));
        files.add(file("src/main/resources/application.yml", "CONFIG",
                "port=8080, H2 in-memory datasource, spring.jpa.hibernate.ddl-auto=create-drop"));
        files.add(file("src/main/java/" + pkgPath + "/exception/GlobalExceptionHandler.java", "EXCEPTION",
                "@ControllerAdvice: ResourceNotFoundException→404, MethodArgumentNotValidException→400"));
        files.add(file(".gitignore", "OTHER",
                "Gitignore for Java/Maven/IntelliJ/macOS"));
        files.add(file("README.md", "README",
                "Project description, prerequisites (Java 17, Maven), how to run, env vars"));

        // One model + one combined DTO per schema (Request+Response in one file to save tokens)
        for (ApiManifest.SchemaInfo schema : manifest.schemas()) {
            String name = schema.name();
            files.add(file("src/main/java/" + pkgPath + "/model/" + name + ".java", "MODEL",
                    "JPA @Entity for " + name + ", fields: " + fieldSummary(schema)));
            files.add(file("src/main/java/" + pkgPath + "/dto/" + name + "Dto.java", "DTO",
                    "Lombok @Data DTOs for " + name + ": inner classes CreateDto and ResponseDto"));
        }

        // One controller + service + repo per resource
        for (String resource : extractResources(manifest)) {
            String cap = capitalize(resource);
            String endpoints = endpointSummary(manifest, resource);
            files.add(file("src/main/java/" + pkgPath + "/controller/" + cap + "Controller.java", "CONTROLLER",
                    "@RestController for: " + endpoints));
            files.add(file("src/main/java/" + pkgPath + "/service/" + cap + "Service.java", "SERVICE",
                    "Service using " + cap + "Repository for " + resource + " CRUD"));
            files.add(file("src/main/java/" + pkgPath + "/repository/" + cap + "Repository.java", "REPOSITORY",
                    "JpaRepository<" + cap + ", Long> for " + resource));
        }

        return files;
    }

    // ── Python (FastAPI) ──────────────────────────────────────────────────────

    private List<ProjectBlueprint.FilePlan> buildPythonFiles(ApiManifest manifest, String projectName) {
        List<ProjectBlueprint.FilePlan> files = new ArrayList<>();
        String pkg = projectName.replace('-', '_');

        files.add(file("requirements.txt", "BUILD",
                "fastapi, sqlalchemy, pydantic, uvicorn[standard], python-dotenv"));
        files.add(file("main.py", "APPLICATION_MAIN",
                "FastAPI app instance, includes all routers"));
        files.add(file("database.py", "CONFIG",
                "SQLAlchemy engine (SQLite :memory:), SessionLocal, get_db dependency"));
        files.add(file("exceptions.py", "EXCEPTION",
                "HTTP 404 exception handler"));
        files.add(file(".gitignore", "OTHER",
                "Gitignore for Python/venv/__pycache__/.env"));
        files.add(file("README.md", "README",
                "Description, prerequisites (Python 3.11+), run with uvicorn main:app"));

        for (ApiManifest.SchemaInfo schema : manifest.schemas()) {
            String name = schema.name().toLowerCase();
            files.add(file("models/" + name + ".py", "MODEL",
                    "SQLAlchemy ORM model for " + schema.name() + " with fields: " + fieldSummary(schema)));
            files.add(file("schemas/" + name + ".py", "DTO",
                    "Pydantic v2 schemas (Create, Update, Response) for " + schema.name()));
        }

        for (String resource : extractResources(manifest)) {
            String endpoints = endpointSummary(manifest, resource);
            files.add(file("routers/" + resource + ".py", "CONTROLLER",
                    "FastAPI APIRouter for: " + endpoints));
            files.add(file("services/" + resource + "_service.py", "SERVICE",
                    "SQLAlchemy CRUD functions for " + resource));
        }

        return files;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ProjectBlueprint.FilePlan file(String path, String type, String description) {
        return new ProjectBlueprint.FilePlan(path, type, description);
    }

    /** Extract unique resource names from the first non-empty path segment. */
    private Set<String> extractResources(ApiManifest manifest) {
        Set<String> resources = new LinkedHashSet<>();
        for (ApiManifest.EndpointInfo ep : manifest.endpoints()) {
            String[] parts = ep.path().split("/");
            for (String part : parts) {
                if (!part.isBlank() && !part.startsWith("{")) {
                    resources.add(part);
                    break;
                }
            }
        }
        return resources;
    }

    private String endpointSummary(ApiManifest manifest, String resource) {
        List<String> parts = new ArrayList<>();
        for (ApiManifest.EndpointInfo ep : manifest.endpoints()) {
            if (ep.path().split("/").length > 1 && ep.path().split("/")[1].equals(resource)) {
                parts.add(ep.method() + " " + ep.path());
            }
        }
        return String.join(", ", parts);
    }

    private String fieldSummary(ApiManifest.SchemaInfo schema) {
        List<String> parts = new ArrayList<>();
        for (ApiManifest.FieldInfo f : schema.fields()) {
            parts.add(f.name() + ":" + f.type());
        }
        return String.join(", ", parts);
    }

    private String toKebabCase(String title) {
        return title.toLowerCase()
                .replaceAll("\\b(api|service|app)\\b", "")
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    private String toCamelCase(String title) {
        StringBuilder sb = new StringBuilder();
        for (String word : title.split("[^a-zA-Z0-9]+")) {
            if (!word.isBlank()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1).toLowerCase());
            }
        }
        // Remove trailing "Api", "App", "Service" to keep class name clean
        String result = sb.toString().replaceAll("(Api|App|Service)$", "");
        return result.isEmpty() ? "Generated" : result;
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
