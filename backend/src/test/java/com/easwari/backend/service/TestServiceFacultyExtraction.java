package com.easwari.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestServiceFacultyExtraction {

    @Test
    public void testAll21DepartmentsDetection() throws Exception {
        File file = new File("C:/Users/anany/Downloads/Faculty_merged_corrected.pdf");
        byte[] bytes = Files.readAllBytes(file.toPath());

        Map<String, Integer> deptCounts = new LinkedHashMap<>();

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            String currentDept = "Artificial Intelligence and Data Science";
            Pattern rowPattern = Pattern.compile("^(\\d{1,3})\\s+((?:Dr\\.?|Prof\\.?|Mr\\.?|Mrs\\.?|Ms\\.?|Mx\\.?|Sri\\.?|Smt\\.?)?\\s*[A-Za-z][A-Za-z .'-]{1,50}?)(?:\\s+(?:[A-Za-z.,& ]*(?:B\\.Sc|M\\.Sc|B\\.E|M\\.E|MCA|Ph\\.D|B\\.Tech|M\\.Tech|D\\.E|ITI)[^a-zA-Z0-9]*(?:\\s*\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})?))?\\s+(Professor(?:\\s*(?:and|&)?\\s*(?:HOD|Head(?:\\s+of\\s+the\\s+Department)?)?)?|Associate\\s*Prof(?:essor|\\.|\\b)|Assistant\\s*Prof(?:essor|\\.|\\b)|Asst\\.?\\s*Prof(?:essor|\\.|\\b)|Assoc\\.?\\s*Prof(?:essor|\\.|\\b)|Prof(?:essor|\\.|\\b)|Lab\\s*Assistant|LAB\\s*PROGRAMMER|Lab\\s*Programmer|Programmer|Technician)(.*)$", Pattern.CASE_INSENSITIVE);

            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                if (text == null || text.isBlank()) continue;

                String[] lines = text.split("\\r?\\n");
                for (int i = 0; i < lines.length; i++) {
                    String trimmed = lines[i].trim();
                    if (trimmed.isEmpty()) continue;

                    Matcher mDept = Pattern.compile("(?i)Department of ([A-Za-z0-9 &(),/-]+)").matcher(trimmed);
                    if (mDept.find()) {
                        String cand = mDept.group(1).trim();
                        // Lookahead for multi-line department names
                        for (int k = i + 1; k < Math.min(i + 3, lines.length); k++) {
                            String next = lines[k].trim();
                            if (next.isEmpty()) continue;
                            if (next.startsWith("(") || next.matches("(?i)^(Engineering|Systems|Technology|Science|Design).*")
                                    || next.matches("(?i)^\\(.*\\)$")) {
                                cand = cand + " " + next;
                            }
                            break;
                        }

                        if (!cand.toLowerCase().contains("srm") && !cand.toLowerCase().contains("easwari")
                                && !cand.toLowerCase().contains("faculty list") && !cand.toLowerCase().contains("faculty directory")) {
                            currentDept = cand;
                        }
                    }

                    Matcher rm = rowPattern.matcher(trimmed);
                    if (rm.matches()) {
                        deptCounts.merge(currentDept, 1, Integer::sum);
                    }
                }
            }
        }

        System.out.println("=== EXTRACTED DEPARTMENTS (" + deptCounts.size() + ") ===");
        deptCounts.forEach((k, v) -> System.out.println("  [" + k + "] -> " + v));
    }
}
