package com.odysseus.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.odysseus.backend.domain.Subject;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PdfExtractionService {

    private static final String OCR_POWERSHELL_SCRIPT =
        "param ([string]$ImagePath)\n" +
        "Add-Type -AssemblyName System.Runtime.WindowsRuntime\n" +
        "$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]\n" +
        "function Await($WinRtTask, $ResultType) {\n" +
        "    $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)\n" +
        "    $netTask = $asTask.Invoke($null, @($WinRtTask))\n" +
        "    $netTask.Wait(-1) | Out-Null\n" +
        "    $netTask.Result\n" +
        "}\n" +
        "[Windows.Media.Ocr.OcrEngine, Windows.Foundation, ContentType = WindowsRuntime] | Out-Null\n" +
        "[Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType = WindowsRuntime] | Out-Null\n" +
        "[Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime] | Out-Null\n" +
        "$resolved = (Resolve-Path $ImagePath).Path\n" +
        "$file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($resolved)) ([Windows.Storage.StorageFile])\n" +
        "$stream = Await ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])\n" +
        "$decoder = Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])\n" +
        "$bitmap = Await ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])\n" +
        "$engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()\n" +
        "if ($null -eq $engine) { $engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage([Windows.Globalization.Language]::new('en-US')) }\n" +
        "$ocrResult = Await ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult])\n" +
        "$linesWithBounds = @()\n" +
        "foreach ($line in $ocrResult.Lines) {\n" +
        "    $minX = 999999; $minY = 999999; $maxX = 0; $maxY = 0;\n" +
        "    foreach ($w in $line.Words) {\n" +
        "        $r = $w.BoundingRect\n" +
        "        if ($r.X -lt $minX) { $minX = $r.X }\n" +
        "        if ($r.Y -lt $minY) { $minY = $r.Y }\n" +
        "        if (($r.X + $r.Width) -gt $maxX) { $maxX = $r.X + $r.Width }\n" +
        "        if (($r.Y + $r.Height) -gt $maxY) { $maxY = $r.Y + $r.Height }\n" +
        "    }\n" +
        "    $linesWithBounds += [PSCustomObject]@{\n" +
        "        Text = $line.Text\n" +
        "        X = $minX\n" +
        "        Y = $minY\n" +
        "        Width = ($maxX - $minX)\n" +
        "        Height = ($maxY - $minY)\n" +
        "    }\n" +
        "}\n" +
        "$linesWithBounds | ConvertTo-Json -Compress\n";

    private boolean isDesignationLine(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("professor") || lower.contains("prof.") || lower.contains("lecturer")
                || lower.contains("hod") || lower.contains("head of") || lower.contains("dean")
                || lower.contains("director") || lower.contains("instructor") || lower.contains("assistant prof")
                || lower.contains("assoc") || lower.contains("teaching assistant") || lower.contains("faculty")
                || lower.contains("guest lecturer") || lower.contains("adjunct");
    }

    private boolean isNameCandidate(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase().trim();
        return lower.startsWith("dr.") || lower.startsWith("prof.") || lower.startsWith("mr.")
                || lower.startsWith("mrs.") || lower.startsWith("ms.") || lower.startsWith("dr ")
                || lower.startsWith("prof ") || lower.startsWith("mr ") || lower.startsWith("mrs ")
                || lower.startsWith("ms ") || lower.startsWith("sri ") || lower.startsWith("smt ");
    }

    /**
     * Extracts text content safely from an uploaded PDF, CSV, or Text file.
     */
    public String extractTextFromFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty or missing.");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";

        if (fileName.endsWith(".pdf") || contentType.contains("pdf")) {
            try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                if (document.isEncrypted()) {
                    throw new IllegalArgumentException("The uploaded PDF is password protected and cannot be extracted.");
                }
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                String extracted = stripper.getText(document);
                if (extracted == null || extracted.trim().isEmpty()) {
                    log.warn("Extracted PDF text is empty. File may be a scanned image.");
                }
                return extracted != null ? extracted : "";
            } catch (Exception e) {
                log.error("Failed to parse PDF file {}: {}", fileName, e.getMessage());
                throw new IOException("Unable to extract text from PDF: " + e.getMessage(), e);
            }
        } else {
            // Default plain text / CSV decoding
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Extracts structured curriculum subjects from raw text or PDF content.
     */
    public List<Map<String, Object>> parseCurriculumSubjects(String rawText, Integer defaultSemester, String defaultDepartment) {
        List<Map<String, Object>> subjects = new ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) {
            return subjects;
        }

        String[] lines = rawText.split("\\r?\\n");
        Set<String> seenCodes = new HashSet<>();

        // Match course code at start of line: e.g. 2321MAB301T, 2321CSC302J, CS301, 21CS401, etc.
        Pattern codePattern = Pattern.compile("^(?:[0-9]{1,3}[.)]\\s+|[0-9]{1,3}\\s*[-–]\\s+|[a-zA-Z][.)]\\s+)?([0-9]{2,4}[A-Za-z]{2,5}[0-9]{2,4}[A-Za-z]?|[A-Za-z]{2,6}[-_]?[0-9]{2,5}[A-Za-z]?|[A-Za-z0-9]{4,15})\\b\\s*(.*)$");

        // Co-curricular non-credit course names (when course code column is blank or symbol)
        Map<String, String> coCurricularMap = new LinkedHashMap<>();
        coCurricularMap.put("aptitude test", "2321APT301");
        coCurricularMap.put("technical test", "2321TEC301");
        coCurricularMap.put("nptel", "2321NPT301");
        coCurricularMap.put("leet code", "2321LEE301");
        coCurricularMap.put("leetcode", "2321LEE301");
        coCurricularMap.put("counselling", "2321COU301");
        coCurricularMap.put("counseling", "2321COU301");
        coCurricularMap.put("library", "2321LIB301");
        coCurricularMap.put("soft skills", "2321SFT301");
        coCurricularMap.put("mentoring", "2321MEN301");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.toLowerCase().startsWith("subject code")
                    || trimmed.toLowerCase().startsWith("sl.") || trimmed.toLowerCase().startsWith("s.no")
                    || trimmed.toLowerCase().startsWith("course code") || trimmed.toLowerCase().startsWith("curriculum")
                    || trimmed.toLowerCase().startsWith("semester") || trimmed.toLowerCase().startsWith("course title")) {
                continue;
            }

            // Check delimiter split first (CSV, Tab, Pipe)
            String delimiter = trimmed.contains(",") ? "," : trimmed.contains("\t") ? "\t" : trimmed.contains("|") ? "\\|" : null;
            if (delimiter != null) {
                String[] tokens = trimmed.split(delimiter);
                if (tokens.length >= 2) {
                    String codeCandidate = tokens[0].trim().replaceAll("^[0-9]+[.)\\s-]*", "").replace("\"", "");
                    if (codeCandidate.length() >= 3 && codeCandidate.matches(".*[0-9].*") && codeCandidate.matches(".*[A-Za-z].*")) {
                        String name = cleanCourseTitle(tokens[1].trim().replace("\"", ""));
                        double theoryCredits = tokens.length > 2 ? parseDoubleSafe(tokens[2].trim(), 3.0) : 3.0;
                        double labCredits = tokens.length > 3 ? parseDoubleSafe(tokens[3].trim(), 0.0) : 0.0;
                        double tutorialCredits = tokens.length > 4 ? parseDoubleSafe(tokens[4].trim(), 0.0) : 0.0;
                        if (!seenCodes.contains(codeCandidate.toUpperCase())) {
                            seenCodes.add(codeCandidate.toUpperCase());
                            subjects.add(buildSubjectMap(codeCandidate, name, theoryCredits, labCredits, tutorialCredits, defaultSemester, defaultDepartment));
                        }
                        continue;
                    }
                }
            }

            // Check if line matches standard course code pattern
            Matcher cm = codePattern.matcher(trimmed);
            if (cm.matches()) {
                String codeCandidate = cm.group(1).trim();
                String rest = cm.group(2).trim();

                // Validate that codeCandidate contains both digits and letters (or is standard course code)
                if (codeCandidate.matches(".*[0-9].*") && codeCandidate.matches(".*[A-Za-z].*") && codeCandidate.length() >= 4) {
                    if (!seenCodes.contains(codeCandidate.toUpperCase())) {
                        Map<String, Object> sub = parseCourseLine(codeCandidate, rest, defaultSemester, defaultDepartment);
                        if (sub != null) {
                            seenCodes.add(codeCandidate.toUpperCase());
                            subjects.add(sub);
                            continue;
                        }
                    }
                }
            }

            // Check if line matches co-curricular / non-credit subject without explicit course code
            String lowerLine = trimmed.toLowerCase();
            for (Map.Entry<String, String> entry : coCurricularMap.entrySet()) {
                if (lowerLine.contains(entry.getKey())) {
                    String generatedCode = entry.getValue();
                    if (!seenCodes.contains(generatedCode)) {
                        seenCodes.add(generatedCode);
                        String title = capitalizeWords(entry.getKey());
                        boolean isLab = title.toLowerCase().contains("leet") || title.toLowerCase().contains("lab");
                        subjects.add(buildSubjectMap(generatedCode, title, isLab ? 0.0 : 1.0, isLab ? 1.0 : 0.0, 0.0, defaultSemester, defaultDepartment));
                        break;
                    }
                }
            }
        }

        return subjects;
    }

    private String cleanCourseTitle(String rawTitle) {
        if (rawTitle == null) return "";
        // Remove trailing mnemonics (like "DM", "CN", "FAIML", "OOPJ") or faculty annotations
        String cleaned = rawTitle.replaceAll("\\b(?:DM|CN|ADS|FAIML|ESD|OOPJ|CSD|AT|TT|NPTEL|LC|COUN)\\b.*$", "").trim();
        if (cleaned.length() >= 3) return cleaned;
        return rawTitle.trim();
    }

    private String capitalizeWords(String str) {
        String[] words = str.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private Map<String, Object> parseCourseLine(String code, String restOfLine, Integer defaultSemester, String defaultDepartment) {
        if (restOfLine.isEmpty()) return null;

        // Check if line ends with a period number (e.g. "... 4" or "... 5" or "... -")
        double periods = 3.0;
        String lineWithoutPeriods = restOfLine;
        Matcher periodMatcher = Pattern.compile("^(.*?)(?:\\s+([0-9]+(?:\\.[0-9]+)?)|\\s+[-–])$").matcher(restOfLine);
        if (periodMatcher.matches()) {
            lineWithoutPeriods = periodMatcher.group(1).trim();
            if (periodMatcher.group(2) != null) {
                periods = parseDoubleSafe(periodMatcher.group(2), 3.0);
            }
        }

        // Clean the title by stripping trailing mnemonic / faculty / department
        // e.g. "Discrete Mathematics DM Dr. Ramanathan MATH" -> "Discrete Mathematics"
        String title = lineWithoutPeriods;
        Matcher titleMatcher = Pattern.compile("^(.*?)\\s+\\b([A-Z]{2,6})\\b(?:\\s+(?:Dr\\.|Prof\\.|Mr\\.|Ms\\.|[A-Z][a-z]+).*|$)").matcher(lineWithoutPeriods);
        if (titleMatcher.find()) {
            String candidateTitle = titleMatcher.group(1).trim();
            if (candidateTitle.length() >= 3) {
                title = candidateTitle;
            }
        }

        // Determine theory vs lab split based on code suffix and title
        char lastChar = Character.toUpperCase(code.charAt(code.length() - 1));
        boolean hasLabInTitle = title.toLowerCase().contains("lab") || title.toLowerCase().contains("practical") || title.toLowerCase().contains("development");
        double theory = 0.0;
        double lab = 0.0;
        double tut = 0.0;

        if (lastChar == 'T') {
            // Pure Theory (e.g. 2321MAB301T, 2321CSC301T)
            theory = periods;
            lab = 0.0;
        } else if (lastChar == 'L' || hasLabInTitle) {
            // Pure Lab / Practical (e.g. 2321SDA301L)
            theory = 0.0;
            lab = Math.max(1.0, periods / 2.0); // each lab credit is 2 periods
        } else if (lastChar == 'J' || lastChar == 'R') {
            // Integrated Theory + Lab (e.g. 2321CSC302J, 2321CSC303J, 2321CSS301J, 2321CSC304R)
            if (periods >= 5.0) {
                theory = periods - 2.0; // e.g. 5 periods -> 3 theory + 2 lab (1 credit)
                lab = 1.0;
            } else if (periods >= 4.0) {
                theory = periods - 2.0; // e.g. 4 periods -> 2 theory + 2 lab (1 credit)
                lab = 1.0;
            } else {
                theory = periods;
                lab = 0.0;
            }
        } else {
            theory = periods;
            lab = 0.0;
        }

        return buildSubjectMap(code, title, theory, lab, tut, defaultSemester, defaultDepartment);
    }

    /**
     * High-level entry point for extracting faculty records from an uploaded file.
     * Tries fast text-based extraction first. If the document has no text layer or returns 0 records,
     * automatically invokes the Windows OCR image-extraction pipeline for scanned PDFs.
     */
    public List<Map<String, Object>> extractFacultyFromFile(MultipartFile file, String defaultDepartment) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty or missing.");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        String text = "";
        try {
            text = extractTextFromFile(file);
        } catch (Exception e) {
            log.warn("Could not extract text layer from file {}: {}", fileName, e.getMessage());
        }

        List<Map<String, Object>> results = new ArrayList<>();
        if (text != null && text.trim().length() >= 30) {
            results = parseFacultyList(text, defaultDepartment);
        }

        // If results found from text layer, return them
        if (results != null && !results.isEmpty()) {
            log.info("Extracted {} faculty members via text parser for department [{}]", results.size(), defaultDepartment);
            return results;
        }

        // If file is a PDF and text parsing yielded nothing, run OCR fallback
        if (fileName.endsWith(".pdf") || (file.getContentType() != null && file.getContentType().toLowerCase().contains("pdf"))) {
            log.info("PDF has no embedded text layer or returned 0 records. Initiating OCR extraction pipeline...");
            List<Map<String, Object>> ocrResults = extractFacultyViaOcr(file.getBytes(), defaultDepartment);
            if (ocrResults != null && !ocrResults.isEmpty()) {
                log.info("Extracted {} faculty members via OCR pipeline for department [{}]", ocrResults.size(), defaultDepartment);
                return ocrResults;
            }
        }

        return results != null ? results : Collections.emptyList();
    }

    /**
     * Extracts structured faculty members from raw text or PDF content.
     *
     * Multi-strategy pipeline:
     *  A) Detect delimiter-separated table (|, \t, ;, ,) with flexible header mapping
     *  B) Space-column detection from header alignment
     *  C) Name-prefix regex scan (Dr./Prof./Mr./Ms.) for free-form PDFs
     *  D) Deduplication + validation (name must be non-empty)
     */
    public List<Map<String, Object>> parseFacultyList(String rawText, String defaultDepartment) {
        List<Map<String, Object>> facultyList = new ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) {
            return facultyList;
        }

        String[] lines = rawText.split("\\r?\\n");

        // ── Strategy A: Delimiter-separated table (pipe, tab, semicolon, comma) ──────
        // Find header row first, then map data rows using detected column positions.
        int headerLineIdx = -1;
        String detectedDelimiter = null;
        Map<String, Integer> colMap = new LinkedHashMap<>(); // field -> column index

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // Identify delimiter in this line
            String delim = null;
            if (line.contains("|"))       delim = "\\|";
            else if (line.contains("\t")) delim = "\t";
            else if (line.contains(";"))  delim = ";";
            else if (line.contains(","))  delim = ",";

            if (delim == null) continue;

            String[] tokens = line.split(delim);
            if (tokens.length < 2) continue;

            // Check if this looks like a header row
            Map<String, Integer> attempt = detectColumnHeaders(tokens);
            if (!attempt.isEmpty()) {
                headerLineIdx = i;
                detectedDelimiter = delim;
                colMap = attempt;
                break;
            }
        }

        if (headerLineIdx >= 0 && detectedDelimiter != null && !colMap.isEmpty()) {
            Set<String> seenNames = new HashSet<>();
            int empCounter = 1;

            for (int i = headerLineIdx + 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                String[] tokens = line.split(detectedDelimiter, -1);
                if (tokens.length < 2) continue;

                // Skip repeated header rows across pages
                if (isHeaderRow(tokens)) continue;

                String name        = getTokenAt(tokens, colMap.get("name"), "").trim();
                String empId       = getTokenAt(tokens, colMap.get("id"), "").trim();
                String designation = getTokenAt(tokens, colMap.get("designation"), "").trim();
                String dept        = getTokenAt(tokens, colMap.get("department"), defaultDepartment != null ? defaultDepartment : "").trim();
                String email       = getTokenAt(tokens, colMap.get("email"), "").trim();
                String phone       = getTokenAt(tokens, colMap.get("phone"), "").trim();
                String spec        = getTokenAt(tokens, colMap.get("specialization"), "").trim();
                String subjects    = getTokenAt(tokens, colMap.get("subjects"), "").trim();

                // Clean up name
                name = cleanFacultyName(name);
                if (name.isEmpty()) continue;

                // Skip clearly invalid / non-name rows
                if (isGarbageLine(name)) continue;

                // Deduplicate by normalized name
                String normName = name.toLowerCase().replaceAll("\\s+", " ");
                if (seenNames.contains(normName)) continue;
                seenNames.add(normName);

                // Auto-generate empId if missing
                if (empId.isEmpty() || isGarbageLine(empId)) {
                    empId = String.format("FAC-%04d", empCounter++);
                }

                // Auto-generate email if missing
                if (email.isEmpty() || !email.contains("@")) {
                    String namePart = name.toLowerCase().replaceAll("[^a-z]", ".");
                    email = namePart + "@odysseus.edu";
                }

                // Fallback department
                if (dept.isEmpty()) {
                    dept = defaultDepartment != null ? defaultDepartment : "CSE";
                }

                Map<String, Object> map = new LinkedHashMap<>();
                map.put("employeeId",    empId);
                map.put("name",          name);
                map.put("designation",   designation);
                map.put("email",         email);
                map.put("phone",         phone);
                map.put("department",    dept);
                map.put("specialization", spec);
                map.put("subjects",      subjects);
                map.put("maxWeeklyLoad", 18);
                facultyList.add(map);
            }

            if (!facultyList.isEmpty()) {
                return facultyList;
            }
        }

        // ── Strategy B: Space-column detection (PDF text extractor output) ──────────
        // Look for a header line using common faculty column keywords, then use character
        // offsets to split subsequent lines into the same columns.
        List<Map<String, Object>> spaceResult = trySpaceColumnExtraction(lines, defaultDepartment);
        if (!spaceResult.isEmpty()) {
            return spaceResult;
        }

        // ── Strategy C: Name-prefix regex scan (Dr./Prof./Mr./Ms./Mx.) ──────────────
        // Works for free-form text, one faculty per line.
        Pattern nameLinePattern = Pattern.compile(
            "(?i)(?:^|\\b)(Dr\\.?|Prof\\.?|Mr\\.?|Mrs\\.?|Ms\\.?|Mx\\.?|Sri\\.?|Smt\\.?)\\s+([A-Za-z][A-Za-z .'-]{1,60})"
        );
        Pattern emailPattern = Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
        Pattern idPattern    = Pattern.compile("\\b(EMP|FAC|STF|STA)?[-]?[0-9]{3,8}\\b");

        Set<String> seenNames = new HashSet<>();
        int empCounter = 1;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || isGarbageLine(trimmed)) continue;

            Matcher nm = nameLinePattern.matcher(trimmed);
            while (nm.find()) {
                String prefix = nm.group(1).trim();
                String rawName = nm.group(2).trim();
                if (rawName.contains(" - ")) rawName = rawName.substring(0, rawName.indexOf(" - ")).trim();
                else if (rawName.contains(" – ")) rawName = rawName.substring(0, rawName.indexOf(" – ")).trim();
                else if (rawName.contains(" | ")) rawName = rawName.substring(0, rawName.indexOf(" | ")).trim();
                else if (rawName.contains(",")) rawName = rawName.substring(0, rawName.indexOf(",")).trim();

                String fullName = cleanFacultyName(prefix + " " + rawName);
                if (fullName.isEmpty()) continue;

                String norm = fullName.toLowerCase().replaceAll("\\s+", " ");
                if (seenNames.contains(norm)) continue;
                seenNames.add(norm);

                // Try to extract email and ID from same line
                Matcher em = emailPattern.matcher(trimmed);
                String email = em.find() ? em.group() : null;

                Matcher im = idPattern.matcher(trimmed);
                String empId = im.find() ? im.group().trim() : String.format("FAC-%04d", empCounter++);

                if (email == null || email.isEmpty()) {
                    email = fullName.toLowerCase().replaceAll("[^a-z]", ".") + "@odysseus.edu";
                }

                Map<String, Object> map = new LinkedHashMap<>();
                map.put("employeeId",    empId);
                map.put("name",          fullName);
                map.put("designation",   "");
                map.put("email",         email);
                map.put("phone",         "");
                map.put("department",    defaultDepartment != null ? defaultDepartment : "CSE");
                map.put("specialization", "");
                map.put("subjects",      "");
                map.put("maxWeeklyLoad", 18);
                facultyList.add(map);
            }
        }

        // ── Strategy D: Comma / Tab structured fallback (original logic) ─────────────
        // Also check for numbered list format common in Indian college PDFs:
        // e.g. "1. Dr. Ramanathan - Professor"
        //      "2. Mr. Senthil Kumar - Assistant Professor"
        if (facultyList.isEmpty()) {
            Pattern numberedNamePattern = Pattern.compile(
                "(?i)^[0-9]{1,3}[.)\\s]+((?:Dr\\.?|Prof\\.?|Mr\\.?|Ms\\.?|Mrs\\.?|Mx\\.?|Sri\\.?|Smt\\.?)?\\s*[A-Za-z][a-zA-Z .'-]{2,60})(?:\\s*[-–|,]\\s*(.*))?$"
            );
            Set<String> seenNum = new HashSet<>();
            int numEmpCounter = 1;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || isGarbageLine(trimmed)) continue;
                Matcher nm = numberedNamePattern.matcher(trimmed);
                if (nm.matches()) {
                    String rawName = nm.group(1).trim();
                    String fullName = cleanFacultyName(rawName);
                    if (fullName.isEmpty() || fullName.length() < 3) continue;
                    String norm = fullName.toLowerCase().replaceAll("\\s+", " ");
                    if (seenNum.contains(norm)) continue;
                    seenNum.add(norm);
                    String designation = nm.group(2) != null ? nm.group(2).trim() : "";
                    String empId = String.format("FAC-%04d", numEmpCounter++);
                    String email = fullName.toLowerCase().replaceAll("[^a-z]", ".") + "@odysseus.edu";
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("employeeId", empId);
                    map.put("name", fullName);
                    map.put("designation", designation);
                    map.put("email", email);
                    map.put("phone", "");
                    map.put("department", defaultDepartment != null ? defaultDepartment : "CSE");
                    map.put("specialization", "");
                    map.put("subjects", "");
                    map.put("maxWeeklyLoad", 18);
                    facultyList.add(map);
                }
            }
        }

        if (facultyList.isEmpty()) {
            Pattern facultyPattern = Pattern.compile(
                "(?i)(EMP[0-9]+|FAC[0-9]+|[0-9]{4,6})\\s*[,|;\\t]?\\s+(Dr\\.|Prof\\.|Mr\\.|Ms\\.)?\\s*([A-Za-z\\s.]+?)\\s*[,|;\\t]?\\s+([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})?"
            );
            Set<String> seen = new HashSet<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || isGarbageLine(trimmed)) continue;

                String delim = trimmed.contains(",") ? "," : trimmed.contains("\t") ? "\t" :
                               trimmed.contains("|") ? "\\|" : null;
                if (delim != null) {
                    String[] parts = trimmed.split(delim);
                    if (parts.length >= 2) {
                        String empId = parts[0].trim().replace("\"", "");
                        String name  = cleanFacultyName(parts[1].trim().replace("\"", ""));
                        if (name.isEmpty() || isGarbageLine(name)) continue;
                        String norm = name.toLowerCase().replaceAll("\\s+", " ");
                        if (seen.contains(norm)) continue;
                        seen.add(norm);

                        String email = parts.length > 2 ? parts[2].trim().replace("\"", "") :
                                       (empId.toLowerCase().replaceAll("[^a-z0-9]", "") + "@odysseus.edu");
                        String dept  = parts.length > 3 ? parts[3].trim().replace("\"", "") :
                                       (defaultDepartment != null ? defaultDepartment : "CSE");
                        int maxLoad  = parts.length > 4 ? (int) parseDoubleSafe(parts[4].trim(), 18) : 18;

                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("employeeId",    empId);
                        map.put("name",          name);
                        map.put("designation",   "");
                        map.put("email",         email);
                        map.put("phone",         "");
                        map.put("department",    dept);
                        map.put("specialization", "");
                        map.put("subjects",      "");
                        map.put("maxWeeklyLoad", maxLoad);
                        facultyList.add(map);
                        continue;
                    }
                }

                Matcher m = facultyPattern.matcher(trimmed);
                if (m.find()) {
                    String empId = m.group(1).trim();
                    String pref  = m.group(2) != null ? m.group(2) : "";
                    String name  = cleanFacultyName((pref + " " + m.group(3)).trim());
                    if (name.isEmpty()) continue;
                    String norm = name.toLowerCase().replaceAll("\\s+", " ");
                    if (seen.contains(norm)) continue;
                    seen.add(norm);

                    String email = m.group(4) != null ? m.group(4).trim() :
                                   (empId.toLowerCase() + "@odysseus.edu");

                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("employeeId",    empId);
                    map.put("name",          name);
                    map.put("designation",   "");
                    map.put("email",         email);
                    map.put("phone",         "");
                    map.put("department",    defaultDepartment != null ? defaultDepartment : "CSE");
                    map.put("specialization", "");
                    map.put("subjects",      "");
                    map.put("maxWeeklyLoad", 18);
                    facultyList.add(map);
                }
            }
        }

        // ── Strategy E: Last-resort — extract any "Title. Name" or "Proper Name" pattern ─
        // Handles plain text faculty lists with no structure at all, e.g.:
        //   "Dr. A. Ramanathan"  or  "Senthil Kumar R"
        if (facultyList.isEmpty()) {
            Pattern titleNamePat = Pattern.compile(
                "(?i)(?:^|\\s)((?:Dr|Prof|Mr|Mrs|Ms|Sri|Smt)\\.?\\s+[A-Za-z][a-zA-Z .'-]{2,50})"
            );
            Pattern properNamePat = Pattern.compile(
                "^([A-Za-z][A-Za-z.'-]{0,25}(?:\\s+[A-Za-z][A-Za-z.'-]{0,25}){1,5})$"
            );
            Set<String> seenE = new HashSet<>();
            int eCounter = 1;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || isGarbageLine(trimmed) || trimmed.length() > 100) continue;
                // Skip lines that are headers/labels
                String lower = trimmed.toLowerCase();
                if (lower.contains("name") && lower.contains("id")) continue;
                if (lower.contains("sl no") || lower.contains("s.no")) continue;

                Matcher tm = titleNamePat.matcher(trimmed);
                if (tm.find()) {
                    String rawName = tm.group(1).trim();
                    String fullName = cleanFacultyName(rawName);
                    if (fullName.isEmpty() || fullName.length() < 4) continue;
                    String norm = fullName.toLowerCase().replaceAll("\\s+", " ");
                    if (seenE.contains(norm)) continue;
                    seenE.add(norm);
                    String empId = String.format("FAC-%04d", eCounter++);
                    String email = fullName.toLowerCase().replaceAll("[^a-z]", ".").replaceAll("\\.+", ".") + "@odysseus.edu";
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("employeeId", empId);
                    map.put("name", fullName);
                    map.put("designation", "");
                    map.put("email", email);
                    map.put("phone", "");
                    map.put("department", defaultDepartment != null ? defaultDepartment : "CSE");
                    map.put("specialization", "");
                    map.put("subjects", "");
                    map.put("maxWeeklyLoad", 18);
                    facultyList.add(map);
                    continue;
                }
                // Check for plain proper-name lines (e.g. "Senthil Kumar R")
                Matcher pm = properNamePat.matcher(trimmed);
                if (pm.matches()) {
                    String fullName = cleanFacultyName(trimmed);
                    if (fullName.isEmpty() || fullName.length() < 4) continue;
                    String norm = fullName.toLowerCase().replaceAll("\\s+", " ");
                    if (seenE.contains(norm)) continue;
                    seenE.add(norm);
                    String empId = String.format("FAC-%04d", eCounter++);
                    String email = fullName.toLowerCase().replaceAll("[^a-z]", ".").replaceAll("\\.+", ".") + "@odysseus.edu";
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("employeeId", empId);
                    map.put("name", fullName);
                    map.put("designation", "");
                    map.put("email", email);
                    map.put("phone", "");
                    map.put("department", defaultDepartment != null ? defaultDepartment : "CSE");
                    map.put("specialization", "");
                    map.put("subjects", "");
                    map.put("maxWeeklyLoad", 18);
                    facultyList.add(map);
                }
            }
        }

        return facultyList;
    }

    /**
     * Extracts faculty records from image-based or scanned PDFs using native Windows OCR.
     */
    public List<Map<String, Object>> extractFacultyViaOcr(byte[] pdfBytes, String defaultDepartment) {
        List<Map<String, Object>> allFaculty = new ArrayList<>();
        File tempScript = null;
        File tempDir = null;

        try {
            // Prepare the OCR PowerShell script directly from embedded string to ensure 100% reliability
            tempScript = File.createTempFile("ocr_image_", ".ps1");
            tempScript.deleteOnExit();
            Files.writeString(tempScript.toPath(), OCR_POWERSHELL_SCRIPT, StandardCharsets.UTF_8);

            tempDir = Files.createTempDirectory("odysseus_ocr_").toFile();
            tempDir.deleteOnExit();

            ObjectMapper mapper = new ObjectMapper();
            int empCounter = 1;
            Set<String> seenNames = new HashSet<>();

            String deptPrefix = "FAC";
            if (defaultDepartment != null) {
                if (defaultDepartment.toLowerCase().contains("computer")) deptPrefix = "CSE";
                else if (defaultDepartment.toLowerCase().contains("information")) deptPrefix = "IT";
                else if (defaultDepartment.toLowerCase().contains("mechanical")) deptPrefix = "MECH";
                else if (defaultDepartment.toLowerCase().contains("civil")) deptPrefix = "CIVIL";
                else if (defaultDepartment.toLowerCase().contains("electrical")) deptPrefix = "EEE";
                else if (defaultDepartment.toLowerCase().contains("electronics")) deptPrefix = "ECE";
                else deptPrefix = defaultDepartment.replaceAll("[^A-Z]", "");
                if (deptPrefix.isEmpty()) deptPrefix = "FAC";
            }

            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                PDFRenderer renderer = new PDFRenderer(doc);

                for (int i = 0; i < doc.getNumberOfPages(); i++) {
                    BufferedImage image = null;
                    PDPage page = doc.getPage(i);
                    PDResources res = page.getResources();
                    if (res != null) {
                        for (org.apache.pdfbox.cos.COSName name : res.getXObjectNames()) {
                            PDXObject xo = res.getXObject(name);
                            if (xo instanceof PDImageXObject) {
                                image = ((PDImageXObject) xo).getImage();
                                break;
                            }
                        }
                    }
                    if (image == null) {
                        image = renderer.renderImageWithDPI(i, 200);
                    }

                    File tempImg = new File(tempDir, "page_" + i + ".png");
                    ImageIO.write(image, "png", tempImg);

                    ProcessBuilder pb = new ProcessBuilder(
                            "powershell", "-ExecutionPolicy", "Bypass",
                            "-File", tempScript.getAbsolutePath(),
                            "-ImagePath", tempImg.getAbsolutePath()
                    );
                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                    }
                    process.waitFor();
                    tempImg.delete();

                    String json = sb.toString().trim();
                    if (!json.startsWith("[")) {
                        log.warn("OCR output for page {} was not valid JSON: {}", i, json);
                        continue;
                    }

                    List<Map<String, Object>> ocrLines = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});

                    List<Map<String, Object>> nameLines = new ArrayList<>();
                    List<Map<String, Object>> desigLines = new ArrayList<>();

                    for (Map<String, Object> l : ocrLines) {
                        int x = ((Number) l.get("X")).intValue();
                        String text = ((String) l.get("Text")).trim();
                        if (text.isEmpty() || isGarbageLine(text)) continue;

                        if (isDesignationLine(text)) {
                            desigLines.add(l);
                        } else if (isNameCandidate(text)) {
                            nameLines.add(l);
                        } else if (x < 550) {
                            nameLines.add(l);
                        } else {
                            desigLines.add(l);
                        }
                    }

                    nameLines.sort(Comparator.comparingInt(m -> ((Number) m.get("Y")).intValue()));
                    desigLines.sort(Comparator.comparingInt(m -> ((Number) m.get("Y")).intValue()));

                    for (Map<String, Object> nl : nameLines) {
                        String rawName = (String) nl.get("Text");
                        int ny = ((Number) nl.get("Y")).intValue();

                        String cleanedName = cleanOcrFacultyName(rawName);
                        if (cleanedName.isEmpty() || isGarbageLine(cleanedName)) continue;

                        String norm = cleanedName.toLowerCase().replaceAll("\\s+", " ");
                        if (seenNames.contains(norm)) continue;
                        seenNames.add(norm);

                        // Find closest designation
                        String matchedDesig = "";
                        for (Map<String, Object> dl : desigLines) {
                            int dy = ((Number) dl.get("Y")).intValue();
                            if (Math.abs(dy - ny) <= 25) {
                                matchedDesig = (String) dl.get("Text");
                                break;
                            }
                        }
                        if (matchedDesig.isEmpty() && !desigLines.isEmpty()) {
                            Map<String, Object> closest = desigLines.stream()
                                    .min(Comparator.comparingInt(dl -> Math.abs(((Number) dl.get("Y")).intValue() - ny)))
                                    .orElse(null);
                            if (closest != null && Math.abs(((Number) closest.get("Y")).intValue() - ny) < 45) {
                                matchedDesig = (String) closest.get("Text");
                            }
                        }

                        String empId = String.format("%s-FAC-%03d", deptPrefix, empCounter++);
                        String email = generateEmailFromName(cleanedName);

                        Map<String, Object> faculty = new LinkedHashMap<>();
                        faculty.put("employeeId", empId);
                        faculty.put("name", cleanedName);
                        faculty.put("designation", matchedDesig);
                        faculty.put("email", email);
                        faculty.put("phone", "");
                        faculty.put("department", defaultDepartment != null ? defaultDepartment : "Computer Science Engineering");
                        faculty.put("specialization", "");
                        faculty.put("subjects", "");
                        faculty.put("maxWeeklyLoad", 18);
                        faculty.put("active", true);

                        allFaculty.add(faculty);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to run OCR extraction on PDF: {}", e.getMessage(), e);
        } finally {
            if (tempScript != null && tempScript.exists()) tempScript.delete();
            if (tempDir != null && tempDir.exists()) {
                File[] files = tempDir.listFiles();
                if (files != null) for (File f : files) f.delete();
                tempDir.delete();
            }
        }

        return allFaculty;
    }

    private String cleanOcrFacultyName(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("^[^a-zA-Z]+", "").trim();
        // Fix OCR artifacts like "_Mr>EM. Archana" -> "Mrs. M. Archana"
        s = s.replaceAll("(?i)^Mr>E?M?\\.?", "Mrs. ");
        s = s.replaceAll("(?i)^Mrs\\b\\.?", "Mrs. ");
        s = s.replaceAll("(?i)^Mr\\b\\.?", "Mr. ");
        s = s.replaceAll("(?i)^Dr\\b\\.?", "Dr. ");
        s = s.replaceAll("(?i)^Ms\\b\\.?", "Ms. ");
        s = s.replaceAll("(?i)^Prof\\b\\.?", "Prof. ");
        s = s.replaceAll("\\.{2,}", ".");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private String generateEmailFromName(String name) {
        String clean = name.toLowerCase()
                .replaceAll("^(dr\\.|prof\\.|mr\\.|mrs\\.|ms\\.)\\s*", "")
                .replaceAll("[^a-z0-9]", ".")
                .replaceAll("\\.+", ".");
        if (clean.startsWith(".")) clean = clean.substring(1);
        if (clean.endsWith(".")) clean = clean.substring(0, clean.length() - 1);
        return clean + "@odysseus.edu";
    }

    // ── Helper: detect column header mappings from a token array ─────────────────────
    private Map<String, Integer> detectColumnHeaders(String[] tokens) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < tokens.length; i++) {
            String h = tokens[i].trim().toLowerCase()
                       .replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
            if (h.matches(".*(\\bname\\b|faculty name|staff name|employee name).*")) {
                map.put("name", i);
            } else if (h.matches(".*(\\bid\\b|emp id|staff id|employee id|s\\.?no|sl\\.?no|serial).*")) {
                map.put("id", i);
            } else if (h.matches(".*(designation|post|position|rank).*")) {
                map.put("designation", i);
            } else if (h.matches(".*(department|dept|branch|division).*")) {
                map.put("department", i);
            } else if (h.matches(".*(e.?mail|mail id|email id).*")) {
                map.put("email", i);
            } else if (h.matches(".*(phone|mobile|contact|ph\\.?).*")) {
                map.put("phone", i);
            } else if (h.matches(".*(speciali[sz]ation|expertise|area of interest|research area).*")) {
                map.put("specialization", i);
            } else if (h.matches(".*(subjects?|subjects? handled|subjects? taught|courses? handled).*")) {
                map.put("subjects", i);
            } else if (h.matches(".*(experience|exp\\b).*")) {
                map.put("experience", i);
            }
        }
        return map;
    }

    // ── Helper: space-column extraction for PDFBox text output ──────────────────────
    private List<Map<String, Object>> trySpaceColumnExtraction(String[] lines, String defaultDepartment) {
        List<Map<String, Object>> result = new ArrayList<>();

        // Find a header line
        int headerIdx = -1;
        int[] colStarts = null;
        String[] colNames = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) continue;

            // Check if this line contains at least 2 known header keywords (space-separated)
            String lower = line.toLowerCase();
            int keywordCount = 0;
            if (lower.contains("name"))        keywordCount++;
            if (lower.contains("designation") || lower.contains("post")) keywordCount++;
            if (lower.contains("department") || lower.contains("dept")) keywordCount++;
            if (lower.contains("id") || lower.contains("s.no") || lower.contains("sl")) keywordCount++;
            if (lower.contains("email") || lower.contains("mail")) keywordCount++;

            if (keywordCount >= 2) {
                // Detect column start positions by finding where each "word" begins in this header
                List<Integer> starts  = new ArrayList<>();
                List<String>  headers = new ArrayList<>();
                int idx = 0;
                while (idx < line.length()) {
                    if (line.charAt(idx) == ' ') { idx++; continue; }
                    // Start of a token
                    int start = idx;
                    while (idx < line.length() && line.charAt(idx) != ' ') idx++;
                    // Allow adjacent words as one column if gap < 3 spaces
                    while (idx < line.length() && idx < start + 40) {
                        int spaces = 0;
                        int tmp = idx;
                        while (tmp < line.length() && line.charAt(tmp) == ' ') { tmp++; spaces++; }
                        if (spaces >= 3 || tmp >= line.length()) break;
                        // Continue token
                        while (tmp < line.length() && line.charAt(tmp) != ' ') tmp++;
                        idx = tmp;
                    }
                    headers.add(line.substring(start, idx).trim());
                    starts.add(start);
                }
                if (headers.size() >= 2) {
                    headerIdx = i;
                    colStarts = starts.stream().mapToInt(Integer::intValue).toArray();
                    colNames = headers.toArray(new String[0]);
                    break;
                }
            }
        }

        if (headerIdx < 0 || colStarts == null || colNames == null) return result;

        // Map column names to field keys
        Map<Integer, String> colFieldMap = new LinkedHashMap<>();
        for (int i = 0; i < colNames.length; i++) {
            String h = colNames[i].toLowerCase().replaceAll("[^a-z0-9 ]", "").trim();
            if (h.contains("name"))          colFieldMap.put(i, "name");
            else if (h.contains("id") || h.contains("s.no") || h.contains("sl"))
                                             colFieldMap.put(i, "id");
            else if (h.contains("designation") || h.contains("post"))
                                             colFieldMap.put(i, "designation");
            else if (h.contains("dept") || h.contains("department"))
                                             colFieldMap.put(i, "department");
            else if (h.contains("email") || h.contains("mail"))
                                             colFieldMap.put(i, "email");
            else if (h.contains("phone") || h.contains("mobile"))
                                             colFieldMap.put(i, "phone");
            else if (h.contains("speciali") || h.contains("expertise"))
                                             colFieldMap.put(i, "specialization");
        }

        if (!colFieldMap.containsValue("name")) return result;

        Set<String> seen = new HashSet<>();
        int empCounter = 1;

        for (int i = headerIdx + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) continue;

            // Extract tokens by column start positions
            String[] tokens = new String[colStarts.length];
            for (int c = 0; c < colStarts.length; c++) {
                int from = colStarts[c];
                int to   = (c + 1 < colStarts.length) ? colStarts[c + 1] : line.length();
                from = Math.min(from, line.length());
                to   = Math.min(to,   line.length());
                tokens[c] = from < to ? line.substring(from, to).trim() : "";
            }

            // Build field map
            Map<String, String> fields = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> e : colFieldMap.entrySet()) {
                int ci = e.getKey();
                if (ci < tokens.length) {
                    fields.put(e.getValue(), tokens[ci].trim());
                }
            }

            String name = cleanFacultyName(fields.getOrDefault("name", ""));
            if (name.isEmpty() || isGarbageLine(name)) continue;

            String norm = name.toLowerCase().replaceAll("\\s+", " ");
            if (seen.contains(norm)) continue;
            seen.add(norm);

            String empId = fields.getOrDefault("id", "").trim();
            if (empId.isEmpty()) empId = String.format("FAC-%04d", empCounter++);

            String email = fields.getOrDefault("email", "").trim();
            if (email.isEmpty() || !email.contains("@")) {
                email = name.toLowerCase().replaceAll("[^a-z]", ".") + "@odysseus.edu";
            }

            String dept = fields.getOrDefault("department", "").trim();
            if (dept.isEmpty()) dept = defaultDepartment != null ? defaultDepartment : "CSE";

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("employeeId",    empId);
            map.put("name",          name);
            map.put("designation",   fields.getOrDefault("designation", ""));
            map.put("email",         email);
            map.put("phone",         fields.getOrDefault("phone", ""));
            map.put("department",    dept);
            map.put("specialization", fields.getOrDefault("specialization", ""));
            map.put("subjects",      "");
            map.put("maxWeeklyLoad", 18);
            result.add(map);
        }
        return result;
    }

    // ── Helper: check if a token array looks like a repeated header row ──────────────
    private boolean isHeaderRow(String[] tokens) {
        int kwCount = 0;
        for (String t : tokens) {
            String lower = t.toLowerCase().replaceAll("[^a-z ]", "").trim();
            if (lower.equals("name") || lower.contains("faculty name") || lower.contains("staff name")
                || lower.equals("designation") || lower.equals("department") || lower.equals("dept")
                || lower.equals("email") || lower.contains("emp id") || lower.equals("sl no")
                || lower.equals("s no") || lower.contains("employee id"))
                kwCount++;
        }
        return kwCount >= 2;
    }

    // ── Helper: get a token at a given (possibly null) index ─────────────────────────
    private String getTokenAt(String[] tokens, Integer idx, String defaultVal) {
        if (idx == null || idx < 0 || idx >= tokens.length) return defaultVal;
        return tokens[idx].trim();
    }

    // ── Helper: clean up a faculty name string ───────────────────────────────────────
    private String cleanFacultyName(String raw) {
        if (raw == null) return "";
        // Remove leading/trailing punctuation, numbers, serial indicators
        String s = raw.replaceAll("^[0-9]+[.)\\s-]*", "")  // strip leading serial numbers
                      .replaceAll("\\s+", " ")
                      .replaceAll("[\"']", "")
                      .trim();
        // If it looks like a page number, date, or purely numeric — discard
        if (s.matches("[0-9]+") || s.matches("[0-9]{1,2}/[0-9]{1,2}/[0-9]{2,4}")) return "";
        // Must contain at least one letter
        if (!s.matches(".*[A-Za-z].*")) return "";
        // Must be between 2 and 80 chars
        if (s.length() < 2 || s.length() > 80) return "";
        return s;
    }

    // ── Helper: detect garbage / non-faculty lines ───────────────────────────────────
    private boolean isGarbageLine(String line) {
        if (line == null || line.trim().isEmpty()) return true;
        String lower = line.trim().toLowerCase();

        // If line contains common faculty titles or email, it's definitely NOT garbage!
        if (lower.contains("dr.") || lower.contains("dr ") || lower.contains("prof.") || lower.contains("prof ")
                || lower.contains("mr.") || lower.contains("mr ") || lower.contains("mrs.") || lower.contains("mrs ")
                || lower.contains("ms.") || lower.contains("ms ") || lower.contains("@")) {
            return false;
        }

        // Skip standalone document headers only if short and matching exact titles
        if (lower.length() < 70 && (
            lower.equals("faculty list") || lower.equals("list of faculty") || lower.equals("faculty roster")
            || lower.startsWith("department of") || lower.contains("all rights reserved")
            || lower.startsWith("academic year") || lower.startsWith("semester")
        )) {
            return true;
        }

        // Skip page numbers / pagination
        if (lower.matches("page\\s*[0-9]+.*") || lower.matches("[0-9]+\\s*of\\s*[0-9]+"))
            return true;

        // Skip address / contact footer lines
        if (lower.length() < 100 && (lower.startsWith("address:") || lower.startsWith("phone:") || lower.startsWith("fax:") || lower.startsWith("website:") || lower.startsWith("www.")))
            return true;

        return false;
    }

    public String classifyFacultyDepartment(String subjectCode, String subjectName, Integer semester, String mainDepartment) {
        if (subjectName == null) subjectName = "";
        if (subjectCode == null) subjectCode = "";
        String sName = subjectName.toLowerCase();
        String sCode = subjectCode.toUpperCase();

        // Check if this is a Special Test for Semesters 3-8 (Aptitude Test, Technical Test)
        // Rule 3: Semesters 3-8 tests must be assigned only to the main department!
        boolean isSem3To8 = semester != null && semester >= 3 && semester <= 8;
        if (isSem3To8 && (sName.contains("aptitude test") || sName.contains("technical test")
                || sName.contains("aptitude & reasoning") || sName.contains("technical assessment")
                || sCode.contains("APT") || sCode.contains("TEC")
                || sCode.equals("APTITUDE") || sCode.equals("TECHNICAL_TEST"))) {
            return (mainDepartment != null && !mainDepartment.isBlank()) ? mainDepartment : "Computer Science Engineering";
        }

        // 1. Mathematics
        if (sName.contains("mathematics") || sName.contains("discrete math") || sName.contains("calculus")
                || sName.contains("linear algebra") || sName.contains("probability") || sName.contains("statistics")
                || sName.contains("numerical methods") || sName.contains("transform techniques")
                || sName.contains("discrete structures") || sCode.contains("MAB") || sCode.contains("MAT")
                || sCode.contains("MA3") || sCode.contains("MA2") || sCode.contains("MA1")) {
            return "Mathematics";
        }

        // 2. Physics
        if (sName.contains("physics") || sName.contains("semiconductor") || sName.contains("quantum")
                || sName.contains("optics") || sName.contains("electromagnet")
                || sCode.contains("PHY") || sCode.contains("PH3") || sCode.contains("PH2") || sCode.contains("PH1")) {
            return "Physics";
        }

        // 3. Chemistry
        if (sName.contains("chemistry") || sName.contains("environmental") || sName.contains("polymer")
                || sName.contains("organic") || sName.contains("inorganic")
                || sCode.contains("CHY") || sCode.contains("CHE") || sCode.contains("CH3") || sCode.contains("CH1")) {
            return "Chemistry";
        }

        // 4. English / Humanities
        if (sName.contains("english") || sName.contains("communication") || sName.contains("technical english")
                || sName.contains("professional communication") || sName.contains("soft skill")
                || sCode.contains("ENG") || sCode.contains("EN3") || sCode.contains("EN1")) {
            return "English";
        }

        // 5. Default to the main department selected (e.g. Computer Science Engineering, AI and Data Science, etc.)
        return (mainDepartment != null && !mainDepartment.isBlank()) ? mainDepartment : "Computer Science Engineering";
    }

    private Map<String, Object> buildSubjectMap(String code, String name, double theory, double lab, double tut, Integer semester, String department) {
        double weeklyHours = theory * 1.0 + lab * 2.0 + tut * 1.0;
        boolean isLab = lab > 0.0;
        String type = isLab && theory > 0 ? "THEORY_AND_LAB" : (isLab ? "LAB" : (tut > 0 ? "TUTORIAL" : "THEORY"));

        String proposedFacultyDept = classifyFacultyDepartment(code, name, semester, department);

        Map<String, Object> item = new HashMap<>();
        item.put("subjectCode", code.toUpperCase());
        item.put("subjectName", name);
        item.put("subjectType", type);
        item.put("theoryCredits", theory);
        item.put("labCredits", lab);
        item.put("tutorialCredits", tut);
        item.put("theoryHours", theory * 1.0);
        item.put("labHours", lab * 2.0);
        item.put("tutorialHours", tut * 1.0);
        item.put("calculatedWeeklyHours", weeklyHours);
        item.put("requiresLab", isLab);
        item.put("labDuration", isLab ? 2 : null);
        item.put("semester", semester != null ? semester : 5);
        item.put("department", department != null ? department : "Computer Science Engineering");
        item.put("facultyDepartment", proposedFacultyDept);
        item.put("proposedFacultyDepartment", proposedFacultyDept);
        return item;
    }

    /**
     * Parses an Academic Schedule document / text to extract semester dates, year, and term.
     */
    public Map<String, Object> parseAcademicSchedule(String rawText, String fallbackYear, Integer fallbackSem) {
        Map<String, Object> result = new HashMap<>();
        String academicYear = fallbackYear != null ? fallbackYear : "2026-2027";
        int semester = fallbackSem != null ? fallbackSem : 3;

        LocalDate startDate = null;
        LocalDate endDate = null;

        if (rawText != null) {
            // Find academic year pattern (e.g. 2026-2027 or 2026-27)
            Matcher yearMatcher = Pattern.compile("(202[0-9])\\s*[-/]\\s*(202[0-9]|[0-9]{2})").matcher(rawText);
            if (yearMatcher.find()) {
                String y1 = yearMatcher.group(1);
                String y2 = yearMatcher.group(2);
                if (y2.length() == 2) y2 = "20" + y2;
                academicYear = y1 + "-" + y2;
            }

            // Find semester pattern
            Matcher semMatcher = Pattern.compile("(?i)(?:semester|sem)\\s*[-:]?\\s*([1-8]|I{1,3}|IV|V|VI|VII|VIII)").matcher(rawText);
            if (semMatcher.find()) {
                String semStr = semMatcher.group(1).toUpperCase();
                switch (semStr) {
                    case "1": case "I": semester = 1; break;
                    case "2": case "II": semester = 2; break;
                    case "3": case "III": semester = 3; break;
                    case "4": case "IV": semester = 4; break;
                    case "5": case "V": semester = 5; break;
                    case "6": case "VI": semester = 6; break;
                    case "7": case "VII": semester = 7; break;
                    case "8": case "VIII": semester = 8; break;
                }
            }

            // Look for date patterns like dd.MM.yyyy, dd-MM-yyyy, yyyy-MM-dd
            List<LocalDate> detectedDates = new ArrayList<>();
            Pattern datePattern = Pattern.compile("(\\b[0-3]?[0-9][./-][0-1]?[0-9][./-]202[0-9]\\b)");
            Matcher dateMatcher = datePattern.matcher(rawText);
            while (dateMatcher.find()) {
                LocalDate d = parseDateSafe(dateMatcher.group(1));
                if (d != null) detectedDates.add(d);
            }

            if (detectedDates.size() >= 2) {
                detectedDates.sort(Comparator.naturalOrder());
                startDate = detectedDates.get(0);
                endDate = detectedDates.get(detectedDates.size() - 1);
            }

            // Extract academic events, assessments, holidays, and vacations line-by-line
            List<Map<String, Object>> events = new ArrayList<>();
            Pattern eventLinePattern = Pattern.compile("(?i)(\\b[0-3]?[0-9][./-][0-1]?[0-9][./-]202[0-9]\\b)(?:\\s*[-–to]{1,4}\\s*(\\b[0-3]?[0-9][./-][0-1]?[0-9][./-]202[0-9]\\b))?\\s*[-–:]?\\s*(.+)");
            for (String line : rawText.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                Matcher em = eventLinePattern.matcher(trimmed);
                if (em.find()) {
                    LocalDate d1 = parseDateSafe(em.group(1));
                    String eventDesc = em.group(3) != null ? em.group(3).trim() : "";
                    if (d1 != null && !eventDesc.isBlank() && eventDesc.length() >= 3) {
                        String lowerDesc = eventDesc.toLowerCase();
                        String type = "COLLEGE";
                        if (lowerDesc.contains("internal") || lowerDesc.contains("unit test") || lowerDesc.contains("assessment") || lowerDesc.contains("exam")) {
                            type = "ASSESSMENT";
                        } else if (lowerDesc.contains("holiday") || lowerDesc.contains("jayanthi") || lowerDesc.contains("diwali") || lowerDesc.contains("pongal") || lowerDesc.contains("independence") || lowerDesc.contains("republic")) {
                            type = "GOVERNMENT";
                        } else if (lowerDesc.contains("vacation") || lowerDesc.contains("recess")) {
                            type = "VACATION";
                        }
                        Map<String, Object> ev = new HashMap<>();
                        ev.put("date", d1.toString());
                        ev.put("name", eventDesc);
                        ev.put("type", type);
                        ev.put("description", eventDesc);
                        events.add(ev);
                    }
                }
            }
            result.put("holidays", events);
            result.put("events", events);
        }

        // Fallback default semester dates if not extracted
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            int baseYear = Integer.parseInt(academicYear.split("-")[0]);
            if (semester % 2 != 0) { // Odd semester: July - Nov
                startDate = LocalDate.of(baseYear, 7, 6);
                endDate = LocalDate.of(baseYear, 10, 30);
            } else { // Even semester: Jan - April
                startDate = LocalDate.of(baseYear + 1, 1, 4);
                endDate = LocalDate.of(baseYear + 1, 4, 30);
            }
        }

        result.put("academicYear", academicYear);
        result.put("semester", semester);
        result.put("startDate", startDate.toString());
        result.put("endDate", endDate.toString());
        result.put("calendarName", "Academic Schedule " + academicYear + " Sem-" + semester);
        return result;
    }

    /**
     * Parses Syllabus Units & Topics from text or PDF across all pages.
     */
    public List<Map<String, Object>> parseSyllabusUnits(String rawText) {
        List<Map<String, Object>> units = new ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) {
            return units;
        }

        // Match unit boundaries like "UNIT I", "UNIT 1", "UNIT-1", "MODULE 1", "CHAPTER 1", "EXPERIMENT 1"
        Pattern unitHeaderPattern = Pattern.compile("(?i)\\b(?:UNIT|MODULE|CHAPTER|PART|EXPERIMENT)\\s*[-–:]?\\s*([IVXLCDM]+|[0-9]+)\\s*[-–:]?\\s*([^\n\r]+)?");
        Matcher matcher = unitHeaderPattern.matcher(rawText);

        List<Integer> matchStarts = new ArrayList<>();
        List<Integer> unitNums = new ArrayList<>();
        List<String> unitTitles = new ArrayList<>();
        List<String> sessionTypes = new ArrayList<>();

        while (matcher.find()) {
            matchStarts.add(matcher.start());
            String matchedPrefix = matcher.group(0).toUpperCase();
            String numStr = matcher.group(1).trim().toUpperCase();
            int uNum;
            try {
                uNum = Integer.parseInt(numStr);
            } catch (NumberFormatException nfe) {
                uNum = romanToInt(numStr);
            }
            unitNums.add(uNum > 0 ? uNum : unitNums.size() + 1);
            String title = matcher.group(2) != null ? matcher.group(2).trim().replaceAll("^[-–:\\s]+", "") : ("Unit " + (unitNums.size() + 1));
            unitTitles.add(title);
            sessionTypes.add(matchedPrefix.contains("EXPERIMENT") ? "LAB" : "THEORY");
        }

        for (int i = 0; i < matchStarts.size(); i++) {
            int start = matchStarts.get(i);
            int end = (i + 1 < matchStarts.size()) ? matchStarts.get(i + 1) : rawText.length();
            String sectionText = rawText.substring(start, end).trim();

            // Extract hours/periods if mentioned like "9 Hours" or "9 Periods"
            int periods = 9;
            Matcher periodMatcher = Pattern.compile("(?i)([0-9]{1,2})\\s*(?:hours|periods|hrs)").matcher(sectionText);
            if (periodMatcher.find()) {
                periods = Integer.parseInt(periodMatcher.group(1));
            }

            // Extract topics
            String[] rawLines = sectionText.split("\\r?\\n");
            List<String> topics = new ArrayList<>();
            for (int l = 1; l < rawLines.length; l++) {
                String line = rawLines[l].trim();
                if (line.isEmpty() || line.toLowerCase().startsWith("unit") || line.toLowerCase().startsWith("module") || line.length() < 3) {
                    continue;
                }
                // Filter out repeated headers and page numbers
                if (line.matches("(?i)^(?:page\\s+\\d+|anna\\s+university|regulation\\s+\\d+|department\\s+of).*")) {
                    continue;
                }
                // Split subtopics by comma, semicolon, bullet, or period
                String[] subtopics = line.split("[,;•–]|\\s{2,}");
                for (String st : subtopics) {
                    String clean = st.trim().replaceAll("^[0-9]+[.)]\\s*", "");
                    if (clean.length() >= 3 && !clean.toLowerCase().startsWith("total") && !clean.toLowerCase().startsWith("hours")
                            && !clean.toLowerCase().startsWith("periods") && !clean.toLowerCase().startsWith("credits")) {
                        topics.add(clean);
                    }
                }
            }

            if (topics.isEmpty()) {
                topics.add(unitTitles.get(i).isEmpty() ? ("Core concepts for Unit " + unitNums.get(i)) : unitTitles.get(i));
            }

            String finalTitle = unitTitles.get(i);
            if ((finalTitle == null || finalTitle.trim().isEmpty() || finalTitle.trim().equalsIgnoreCase("Unit " + unitNums.get(i))) && !topics.isEmpty()) {
                // If title was on next line or not caught by header pattern, infer from first line of section
                String[] sLines = sectionText.split("\\r?\\n");
                for (int l = 1; l < Math.min(sLines.length, 3); l++) {
                    String cand = sLines[l].trim();
                    if (!cand.isEmpty() && !cand.toLowerCase().startsWith("hours") && !cand.toLowerCase().startsWith("periods") && cand.length() >= 3) {
                        finalTitle = cand.replaceAll("^[-–:\\s]+", "").replaceAll("(?i)\\s*[0-9]+\\s*(?:hours|periods|hrs).*$", "").trim();
                        break;
                    }
                }
            }

            Map<String, Object> uMap = new HashMap<>();
            uMap.put("sessionType", sessionTypes.get(i));
            uMap.put("unitNumber", unitNums.get(i));
            uMap.put("title", (finalTitle != null && !finalTitle.trim().isEmpty()) ? finalTitle.trim() : ("Unit " + unitNums.get(i)));
            uMap.put("periodsAllotted", periods);
            uMap.put("topics", topics);
            units.add(uMap);
        }

        return units;
    }

    /**
     * Reads the syllabus document line-by-line to extract Course Objectives.
     */
    public List<Map<String, Object>> parseCourseObjectives(String rawText) {
        List<Map<String, Object>> objectives = new ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) return objectives;

        String[] lines = rawText.split("\\r?\\n");
        boolean inObjectives = false;
        StringBuilder currentDesc = new StringBuilder();
        int seqNum = 1;

        Pattern objHeader = Pattern.compile("(?i)^\\s*(?:COURSE\\s+OBJECTIVES?|OBJECTIVES?|PROGRAM\\s+EDUCATIONAL\\s+OBJECTIVES?)\\s*[:–-]?\\s*$");
        Pattern nextSection = Pattern.compile("(?i)^\\s*(?:UNIT\\s*[-–:]?\\s*[IVX0-9]+|MODULE\\s*[-–:]?\\s*[0-9]+|COURSE\\s+OUTCOMES?|OUTCOMES?|SYLLABUS|COURSE\\s+CONTENT|TEXT\\s*BOOKS?|REFERENCES?)\\b.*");
        Pattern itemStart = Pattern.compile("^(?:(?:[0-9]{1,2}[.)]|\\[[0-9]{1,2}\\]|\\([0-9]{1,2}\\)|[a-zA-Z][.)]|•|–|-)\\s*)?(To\\s+.*|Understand\\s+.*|Introduce\\s+.*|Learn\\s+.*|Develop\\s+.*|Provide\\s+.*|Equip\\s+.*|Familiarize\\s+.*|Enable\\s+.*|Impart\\s+.*|Gain\\s+.*|Study\\s+.*)", Pattern.CASE_INSENSITIVE);

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            if (objHeader.matcher(line).find() || line.equalsIgnoreCase("COURSE OBJECTIVES:") || line.equalsIgnoreCase("OBJECTIVES:")
                    || line.toLowerCase().contains("course objectives")) {
                inObjectives = true;
                continue;
            }

            if (inObjectives) {
                if (nextSection.matcher(line).find() && !line.toLowerCase().contains("objective")) {
                    inObjectives = false;
                    if (currentDesc.length() > 0) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("sequenceNumber", seqNum++);
                        map.put("description", currentDesc.toString().trim());
                        objectives.add(map);
                        currentDesc.setLength(0);
                    }
                    break;
                }

                Matcher m = itemStart.matcher(line);
                boolean isNumbered = line.matches("^[0-9]{1,2}[.)]\\s+.*") || line.matches("^[•–-]\\s+.*");

                if (m.matches() || isNumbered) {
                    if (currentDesc.length() > 0) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("sequenceNumber", seqNum++);
                        map.put("description", currentDesc.toString().trim());
                        objectives.add(map);
                        currentDesc.setLength(0);
                    }
                    String clean = line.replaceAll("^(?:[0-9]{1,2}[.)]|\\[[0-9]{1,2}\\]|\\([0-9]{1,2}\\)|[•–-]\\s*)", "").trim();
                    currentDesc.append(clean);
                } else if (currentDesc.length() > 0 && !line.toLowerCase().startsWith("page ") && line.length() > 3) {
                    currentDesc.append(" ").append(line);
                }
            }
        }

        if (currentDesc.length() > 0) {
            Map<String, Object> map = new HashMap<>();
            map.put("sequenceNumber", seqNum++);
            map.put("description", currentDesc.toString().trim());
            objectives.add(map);
        }

        return objectives;
    }

    /**
     * Reads the syllabus document line-by-line to extract Course Outcomes (CO1..COn).
     */
    public List<Map<String, Object>> parseCourseOutcomes(String rawText) {
        List<Map<String, Object>> outcomes = new ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) return outcomes;

        String[] lines = rawText.split("\\r?\\n");
        boolean inOutcomes = false;
        StringBuilder currentDesc = new StringBuilder();
        int currentCoNum = 1;

        Pattern outcomeHeader = Pattern.compile("(?i)^\\s*(?:COURSE\\s+OUTCOMES?|OUTCOMES?|COURSE\\s+OUTCOMES?\\s*\\(COS?\\)|UPON\\s+COMPLETION.*STUDENTS.*ABLE\\s+TO|AT\\s+THE\\s+END.*STUDENTS.*ABLE\\s+TO)\\s*[:–-]?\\s*$");
        Pattern nextSection = Pattern.compile("(?i)^\\s*(?:TEXT\\s*BOOKS?|REFERENCES?|REFERENCE\\s+BOOKS?|CO[-–\\s]*PO\\s+MAPPING|PO\\s+MAPPING|ARTICULATION\\s+MATRIX|WEB\\s+REFERENCES?)\\b.*");
        Pattern coLinePattern = Pattern.compile("(?i)^\\s*(?:CO\\s*[-–:]?\\s*([0-9]{1,2})|[0-9]{1,2}[.)]|•|–|-)\\s*[:–-]?\\s*(.*)$");

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // Direct check for CO1, CO2, etc. even if heading wasn't strictly matched
            Matcher directCo = Pattern.compile("(?i)^\\s*CO\\s*[-–:]?\\s*([1-9])\\s*[:–-]\\s*(.*)$").matcher(line);
            if (directCo.matches()) {
                if (currentDesc.length() > 0) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("coNumber", currentCoNum);
                    map.put("description", currentDesc.toString().trim());
                    outcomes.add(map);
                    currentDesc.setLength(0);
                }
                currentCoNum = Integer.parseInt(directCo.group(1));
                currentDesc.append(directCo.group(2).trim());
                inOutcomes = true;
                continue;
            }

            if (outcomeHeader.matcher(line).find() || line.toLowerCase().contains("course outcomes")
                    || line.toLowerCase().contains("students will be able to")) {
                inOutcomes = true;
                continue;
            }

            if (inOutcomes) {
                if (nextSection.matcher(line).find() && !line.toLowerCase().contains("outcome")) {
                    inOutcomes = false;
                    if (currentDesc.length() > 0) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("coNumber", currentCoNum);
                        map.put("description", currentDesc.toString().trim());
                        outcomes.add(map);
                        currentDesc.setLength(0);
                    }
                    break;
                }

                Matcher coMatcher = coLinePattern.matcher(line);
                if (coMatcher.matches()) {
                    if (currentDesc.length() > 0) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("coNumber", currentCoNum);
                        map.put("description", currentDesc.toString().trim());
                        outcomes.add(map);
                        currentDesc.setLength(0);
                    }

                    if (coMatcher.group(1) != null) {
                        currentCoNum = Integer.parseInt(coMatcher.group(1));
                    } else {
                        currentCoNum = outcomes.size() + 1;
                    }
                    currentDesc.append(coMatcher.group(2).trim());
                } else if (currentDesc.length() > 0 && !line.toLowerCase().startsWith("page ") && line.length() > 3) {
                    currentDesc.append(" ").append(line);
                }
            }
        }

        if (currentDesc.length() > 0) {
            Map<String, Object> map = new HashMap<>();
            map.put("coNumber", currentCoNum);
            map.put("description", currentDesc.toString().trim());
            outcomes.add(map);
        }

        return outcomes;
    }

    /**
     * Analyses an entire syllabus document, extracts text across all pages,
     * identifies subjects and unit structures, course objectives and outcomes,
     * and provides validation metrics.
     */
    public Map<String, Object> analyzeSyllabusDocument(MultipartFile file, Subject targetSubject) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty or missing.");
        }

        int totalPages = 1;
        String rawText = "";
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";

        if (fileName.toLowerCase().endsWith(".pdf") || (file.getContentType() != null && file.getContentType().toLowerCase().contains("pdf"))) {
            try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                if (document.isEncrypted()) {
                    throw new IllegalArgumentException("The uploaded PDF is password protected.");
                }
                totalPages = document.getNumberOfPages();
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                rawText = stripper.getText(document);
            }
        } else {
            rawText = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        List<Map<String, Object>> parsedUnits = parseSyllabusUnits(rawText);
        List<Map<String, Object>> parsedObjectives = parseCourseObjectives(rawText);
        List<Map<String, Object>> parsedOutcomes = parseCourseOutcomes(rawText);

        // Subject identification
        Set<String> identifiedSubjects = new LinkedHashSet<>();
        if (targetSubject != null) {
            identifiedSubjects.add(targetSubject.getSubjectCode() + " — " + targetSubject.getSubjectName());
        }

        Matcher codeMatcher = Pattern.compile("\\b([A-Z]{2,4}\\s*[-_]?\\s*[0-9]{3,5})\\b").matcher(rawText != null ? rawText : "");
        while (codeMatcher.find()) {
            String foundCode = codeMatcher.group(1).replaceAll("\\s+", "");
            if (targetSubject != null && targetSubject.getSubjectCode() != null
                    && targetSubject.getSubjectCode().replaceAll("\\s+", "").equalsIgnoreCase(foundCode)) {
                // target already matched
            } else if (foundCode.length() >= 5) {
                identifiedSubjects.add(foundCode);
            }
        }

        // Count total topics and compile validation warnings
        int totalTopicsCount = 0;
        List<String> reviewWarnings = new ArrayList<>();

        if (rawText == null || rawText.trim().length() < 50) {
            reviewWarnings.add("Document contains very little selectable text. File may be scanned or image-based.");
        }

        if (parsedUnits.isEmpty()) {
            reviewWarnings.add("No distinct Units or Modules could be identified. Check document structure.");
        } else {
            Set<Integer> unitNumbersSeen = new HashSet<>();
            for (Map<String, Object> u : parsedUnits) {
                Integer uNum = (Integer) u.get("unitNumber");
                if (uNum != null) unitNumbersSeen.add(uNum);
                @SuppressWarnings("unchecked")
                List<String> topics = (List<String>) u.getOrDefault("topics", Collections.emptyList());
                totalTopicsCount += topics.size();
                if (topics.isEmpty()) {
                    reviewWarnings.add("Unit " + uNum + " has no sub-topics identified.");
                }
            }
            if (!unitNumbersSeen.isEmpty()) {
                int maxUnit = Collections.max(unitNumbersSeen);
                for (int u = 1; u <= maxUnit; u++) {
                    if (!unitNumbersSeen.contains(u)) {
                        reviewWarnings.add("Unit " + u + " was not found in sequence (found " + unitNumbersSeen + ").");
                    }
                }
            }
        }

        if (totalPages > 2 && parsedUnits.size() < 3) {
            reviewWarnings.add("Document has " + totalPages + " pages, but only " + parsedUnits.size() + " unit(s) were identified. Check if later pages have additional content.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalPagesProcessed", totalPages);
        result.put("subjectsIdentified", new ArrayList<>(identifiedSubjects));
        result.put("unitsIdentified", parsedUnits.size());
        result.put("topicsExtracted", totalTopicsCount);
        result.put("objectivesIdentified", parsedObjectives.size());
        result.put("outcomesIdentified", parsedOutcomes.size());
        result.put("reviewWarnings", reviewWarnings);
        result.put("units", parsedUnits);
        result.put("courseObjectives", parsedObjectives);
        result.put("courseOutcomes", parsedOutcomes);
        result.put("fileName", fileName);
        return result;
    }

    private LocalDate parseDateSafe(String val) {
        if (val == null) return null;
        try {
            String clean = val.replaceAll("[^0-9./-]", "");
            String[] parts = clean.split("[./-]");
            if (parts.length == 3) {
                int p1 = Integer.parseInt(parts[0]);
                int p2 = Integer.parseInt(parts[1]);
                int p3 = Integer.parseInt(parts[2]);
                if (p3 > 1900) { // dd-MM-yyyy
                    return LocalDate.of(p3, p2, p1);
                } else if (p1 > 1900) { // yyyy-MM-dd
                    return LocalDate.of(p1, p2, p3);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private int romanToInt(String s) {
        Map<Character, Integer> map = Map.of('I', 1, 'V', 5, 'X', 10, 'L', 5, 'C', 100);
        int total = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int cur = map.getOrDefault(s.charAt(i), 0);
            if (cur < prev) total -= cur;
            else total += cur;
            prev = cur;
        }
        return total > 0 ? total : 1;
    }

    private double parseDoubleSafe(String val, double defaultVal) {
        if (val == null) return defaultVal;
        try {
            String cleaned = val.replaceAll("[^0-9.]", "");
            if (cleaned.isEmpty()) return defaultVal;
            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            return defaultVal;
        }
    }
}

