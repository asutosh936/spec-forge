package com.example.apicodegen.web;

import com.example.apicodegen.store.SessionStore;
import com.example.apicodegen.zip.ZipBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class DownloadController {

    private final SessionStore sessionStore;
    private final ZipBuilder zipBuilder;

    public DownloadController(SessionStore sessionStore, ZipBuilder zipBuilder) {
        this.sessionStore = sessionStore;
        this.zipBuilder = zipBuilder;
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        return sessionStore.getResult(id)
                .map(result -> {
                    byte[] zip = zipBuilder.build(result);
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"generated-project.zip\"")
                            .body(zip);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
