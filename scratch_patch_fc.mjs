import fs from 'fs';

const path = 'backend/src/main/java/com/odysseus/backend/controller/FacultyController.java';
let content = fs.readFileSync(path, 'utf8');

// 1. Deduplicate batches in proposeAssignment:
const target1 = `        List<Batch> batches = batchRepository.findAll().stream()
                .filter(Batch::isActive)
                .filter(b -> deptFilter.isEmpty() || com.odysseus.backend.util.DepartmentMatcher.matches(b.getDepartment(), deptFilter))
                .filter(b -> {
                    if (finalTargetSemester != null) {
                        return Objects.equals(b.getSemester(), finalTargetSemester);
                    }
                    if (finalIsOdd != null && b.getSemester() != null) {
                        return finalIsOdd ? (b.getSemester() % 2 == 1) : (b.getSemester() % 2 == 0);
                    }
                    return true;
                })
                .sorted(Comparator.comparing(Batch::getBatchName))
                .collect(Collectors.toList());

        List<Map<String, Object>> batchList = batches.stream().map(b -> {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("id", b.getId());
            bm.put("batchName", b.getBatchName());
            bm.put("section", b.getSection() != null ? b.getSection() : "");
            return bm;
        }).collect(Collectors.toList());`;

const replacement1 = `        List<Batch> batches = batchRepository.findAll().stream()
                .filter(Batch::isActive)
                .filter(b -> deptFilter.isEmpty() || com.odysseus.backend.util.DepartmentMatcher.matches(b.getDepartment(), deptFilter))
                .filter(b -> {
                    if (finalTargetSemester != null) {
                        return Objects.equals(b.getSemester(), finalTargetSemester);
                    }
                    if (finalIsOdd != null && b.getSemester() != null) {
                        return finalIsOdd ? (b.getSemester() % 2 == 1) : (b.getSemester() % 2 == 0);
                    }
                    return true;
                })
                .sorted(Comparator.comparing(Batch::getBatchName))
                .collect(Collectors.toList());

        // Deduplicate batches by semester and section: exactly ONE batch per section per semester
        Map<String, Batch> uniqueBatches = new LinkedHashMap<>();
        for (Batch b : batches) {
            String bKey = (b.getSemester() != null ? b.getSemester() : 0) + "-" 
                    + (b.getSection() != null ? b.getSection().trim().toUpperCase() : b.getBatchName().trim());
            uniqueBatches.putIfAbsent(bKey, b);
        }
        batches = new ArrayList<>(uniqueBatches.values());

        List<Map<String, Object>> batchList = batches.stream().map(b -> {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("id", b.getId());
            bm.put("batchName", b.getBatchName());
            bm.put("section", b.getSection() != null ? b.getSection() : "");
            bm.put("semester", b.getSemester());
            bm.put("department", b.getDepartment());
            return bm;
        }).collect(Collectors.toList());`;

// Normalize line endings for replacement
const norm = s => s.replace(/\r\n/g, '\n');
content = norm(content);

if (!content.includes(norm(target1))) {
  console.error("Target 1 not found in FacultyController.java!");
  process.exit(1);
}
content = content.replace(norm(target1), norm(replacement1));

// 2. relevantSubjectBatches and proposal.put("relevantBatches"):
const target2 = `            List<Batch> relevantSubjectBatches = batches.stream()
                    .filter(b -> subject.getSemester() == null || Objects.equals(b.getSemester(), subject.getSemester()))
                    .collect(Collectors.toList());
            if (relevantSubjectBatches.isEmpty()) relevantSubjectBatches = batches;`;

const replacement2 = `            List<Batch> relevantSubjectBatches = batches.stream()
                    .filter(b -> subject.getSemester() == null || Objects.equals(b.getSemester(), subject.getSemester()))
                    .collect(Collectors.toList());`;

if (!content.includes(norm(target2))) {
  console.error("Target 2 not found in FacultyController.java!");
  process.exit(1);
}
content = content.replace(norm(target2), norm(replacement2));

// 3. Add relevantBatches into proposal and loop relevantSubjectBatches in existingSectionFaculty
const target3 = `            // Existing offerings / assigned faculty per section for this subject
            Map<Long, Long> existingSectionFaculty = new HashMap<>();
            for (Batch b : batches) {
                offeringRepository.findAll().stream()
                        .filter(o -> o.isActive() && o.getBatch() != null && Objects.equals(o.getBatch().getId(), b.getId())
                                && o.getSubject() != null && Objects.equals(o.getSubject().getId(), subject.getId())
                                && o.getAssignedFaculty() != null)
                        .findFirst()
                        .ifPresent(o -> existingSectionFaculty.put(b.getId(), o.getAssignedFaculty().getId()));
            }

            Map<String, Object> proposal = new LinkedHashMap<>();
            proposal.put("subjectId", subject.getId());
            proposal.put("subjectCode", subject.getSubjectCode());
            proposal.put("subjectName", subject.getSubjectName());
            proposal.put("subjectType", subject.getSubjectType());
            proposal.put("department", subject.getDepartment());
            proposal.put("coursePrefix", coursePrefix != null ? coursePrefix : "");
            proposal.put("departmentKey", targetDeptKey != null ? targetDeptKey : "UNMAPPED");
            proposal.put("isMapped", isMapped);
            proposal.put("mappingStatus", isMapped ? "MAPPED" : "UNMAPPED");
            proposal.put("unmappedWarning", unmappedWarning);
            proposal.put("semester", subject.getSemester());
            proposal.put("weeklyHours", subject.getCalculatedWeeklyHours());
            proposal.put("requiredFacultyCount", requiredCount);
            proposal.put("eligibleFaculty", eligibleList);
            proposal.put("suggestedFacultyIds", suggestedFacultyIds);
            proposal.put("suggestedSectionFaculty", suggestedSectionFaculty);
            proposal.put("requiresAdminInput", eligible.isEmpty() || !isMapped);
            proposal.put("existingSectionFaculty", existingSectionFaculty);
            proposals.add(proposal);`;

const replacement3 = `            // Existing offerings / assigned faculty per section for this subject
            Map<Long, Long> existingSectionFaculty = new HashMap<>();
            for (Batch b : relevantSubjectBatches) {
                offeringRepository.findAll().stream()
                        .filter(o -> o.isActive() && o.getBatch() != null && Objects.equals(o.getBatch().getId(), b.getId())
                                && o.getSubject() != null && Objects.equals(o.getSubject().getId(), subject.getId())
                                && o.getAssignedFaculty() != null)
                        .findFirst()
                        .ifPresent(o -> existingSectionFaculty.put(b.getId(), o.getAssignedFaculty().getId()));
            }

            List<Map<String, Object>> relevantBatchList = relevantSubjectBatches.stream().map(b -> {
                Map<String, Object> bm = new LinkedHashMap<>();
                bm.put("id", b.getId());
                bm.put("batchName", b.getBatchName());
                bm.put("section", b.getSection() != null ? b.getSection() : "");
                bm.put("semester", b.getSemester());
                bm.put("department", b.getDepartment());
                return bm;
            }).collect(Collectors.toList());

            Map<String, Object> proposal = new LinkedHashMap<>();
            proposal.put("subjectId", subject.getId());
            proposal.put("subjectCode", subject.getSubjectCode());
            proposal.put("subjectName", subject.getSubjectName());
            proposal.put("subjectType", subject.getSubjectType());
            proposal.put("department", subject.getDepartment());
            proposal.put("coursePrefix", coursePrefix != null ? coursePrefix : "");
            proposal.put("departmentKey", targetDeptKey != null ? targetDeptKey : "UNMAPPED");
            proposal.put("isMapped", isMapped);
            proposal.put("mappingStatus", isMapped ? "MAPPED" : "UNMAPPED");
            proposal.put("unmappedWarning", unmappedWarning);
            proposal.put("semester", subject.getSemester());
            proposal.put("weeklyHours", subject.getCalculatedWeeklyHours());
            proposal.put("requiredFacultyCount", requiredCount);
            proposal.put("relevantBatches", relevantBatchList);
            proposal.put("eligibleFaculty", eligibleList);
            proposal.put("suggestedFacultyIds", suggestedFacultyIds);
            proposal.put("suggestedSectionFaculty", suggestedSectionFaculty);
            proposal.put("requiresAdminInput", eligible.isEmpty() || !isMapped);
            proposal.put("existingSectionFaculty", existingSectionFaculty);
            proposals.add(proposal);`;

if (!content.includes(norm(target3))) {
  console.error("Target 3 not found in FacultyController.java!");
  process.exit(1);
}
content = content.replace(norm(target3), norm(replacement3));

fs.writeFileSync(path, content, 'utf8');
console.log("Successfully updated FacultyController.java!");

