package com.example.apicodegen.zip;

import com.example.apicodegen.model.GenerationResult;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class ZipBuilder {

    public byte[] build(GenerationResult result) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (var file : result.files()) {
                ZipEntry entry = new ZipEntry(file.filename());
                zos.putNextEntry(entry);
                zos.write(file.content().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to build ZIP archive", e);
        }
        return baos.toByteArray();
    }
}
