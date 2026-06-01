package com.example.apicodegen.zip;

import com.example.apicodegen.model.GenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class ZipBuilder {

    private static final Logger log = LoggerFactory.getLogger(ZipBuilder.class);

    public byte[] build(GenerationResult result) {
        log.info("Building ZIP archive with {} files", result.files().size());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (var file : result.files()) {
                log.debug("Adding file to ZIP: {}", file.filename());
                ZipEntry entry = new ZipEntry(file.filename());
                zos.putNextEntry(entry);
                zos.write(file.content().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        } catch (IOException e) {
            log.error("Failed to build ZIP archive", e);
            throw new RuntimeException("Failed to build ZIP archive", e);
        }
        byte[] zipBytes = baos.toByteArray();
        log.info("ZIP archive built successfully: {} bytes", zipBytes.length);
        return zipBytes;
    }
}
