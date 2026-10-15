package com.odysseus.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestServiceFacultyExtraction {

    @Test
    public void testExtractFacultyFromFile() throws Exception {
        File file = new File("C:/Users/anany/Downloads/Faculties CSE.pdf");
        if (!file.exists()) {
            System.out.println("SKIPPING TEST: File not found: " + file.getAbsolutePath());
            return;
        }

        byte[] bytes = Files.readAllBytes(file.toPath());
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "Faculties CSE.pdf",
                "application/pdf",
                bytes
        );

        PdfExtractionService service = new PdfExtractionService();
        List<Map<String, Object>> list = service.extractFacultyFromFile(mockFile, "Computer Science Engineering");

        System.out.println("=== EXTRACTED FACULTY COUNT: " + list.size() + " ===");
        assertFalse(list.isEmpty(), "Extracted faculty list should not be empty!");
        assertTrue(list.size() >= 35, "Should extract at least 35 faculty members from 3 pages!");

        for (int i = 0; i < Math.min(list.size(), 10); i++) {
            Map<String, Object> f = list.get(i);
            System.out.println(f.get("employeeId") + " | " + f.get("name") + " | " + f.get("designation") + " | " + f.get("department") + " | " + f.get("email"));
        }
    }
}
