package com.odysseus.backend.util;

import com.odysseus.backend.service.DepartmentService;

/**
 * Generic Department Matcher.
 *
 * Normalizes variations and checks equivalence using generic canonical keys
 * and alias definitions.
 */
public class DepartmentMatcher {

    /**
     * Checks whether two department strings refer to the same department identity.
     */
    public static boolean matches(String dept1, String dept2) {
        if (dept1 == null || dept2 == null) {
            return false;
        }

        String a = dept1.trim();
        String b = dept2.trim();

        if (a.equalsIgnoreCase(b)) {
            return true;
        }

        if (a.equalsIgnoreCase("ALL") || b.equalsIgnoreCase("ALL")) {
            return true;
        }

        // Compare normalized alphanumeric strings
        String normA = DepartmentService.normalizeForComparison(DepartmentService.cleanDepartmentString(a));
        String normB = DepartmentService.normalizeForComparison(DepartmentService.cleanDepartmentString(b));
        if (!normA.isEmpty() && normA.equals(normB)) {
            return true;
        }

        // Compare derived acronym / keys
        String keyA = DepartmentService.deriveKeyFromName(a);
        String keyB = DepartmentService.deriveKeyFromName(b);
        if (!keyA.isEmpty() && !keyA.equals("DEPT") && keyA.equalsIgnoreCase(keyB)) {
            return true;
        }

        // Common abbreviation checks
        if (isCse(a) && isCse(b)) return true;
        if (isIt(a) && isIt(b)) return true;
        if (isMech(a) && isMech(b)) return true;
        if (isCivil(a) && isCivil(b)) return true;
        if (isEee(a) && isEee(b)) return true;
        if (isEce(a) && isEce(b)) return true;
        if (isAids(a) && isAids(b)) return true;
        if (isMath(a) && isMath(b)) return true;
        if (isPhy(a) && isPhy(b)) return true;
        if (isChem(a) && isChem(b)) return true;
        if (isEng(a) && isEng(b)) return true;

        return false;
    }

    private static boolean isCse(String s) {
        String lower = s.trim().toLowerCase();
        return lower.startsWith("cse") || lower.contains("computer science") || lower.equals("cs");
    }

    private static boolean isIt(String s) {
        String lower = s.trim().toLowerCase();
        return lower.equals("it") || lower.contains("information technology");
    }

    private static boolean isMech(String s) {
        String lower = s.trim().toLowerCase();
        return lower.startsWith("mech") || lower.contains("mechanical") || lower.equals("me");
    }

    private static boolean isCivil(String s) {
        String lower = s.trim().toLowerCase();
        return lower.equals("civil") || lower.equals("ce");
    }

    private static boolean isEee(String s) {
        String lower = s.trim().toLowerCase();
        return lower.startsWith("eee") || lower.equals("ee") || lower.contains("electrical");
    }

    private static boolean isEce(String s) {
        String lower = s.trim().toLowerCase();
        return lower.startsWith("ece") || lower.equals("ec") || lower.contains("electronics and communication") || lower.contains("electronics & communication");
    }

    private static boolean isAids(String s) {
        String lower = s.trim().toLowerCase();
        return lower.startsWith("aids") || lower.contains("data science") || lower.equals("ai&ds");
    }

    private static boolean isMath(String s) {
        String lower = s.trim().toLowerCase();
        return lower.startsWith("math") || lower.contains("mathematics") || lower.startsWith("mathem");
    }

    private static boolean isPhy(String s) {
        String lower = s.trim().toLowerCase();
        return lower.startsWith("phy") || lower.contains("physics") || lower.startsWith("physic");
    }

    private static boolean isChem(String s) {
        String lower = s.trim().toLowerCase();
        return lower.startsWith("chem") || lower.contains("chemistry") || lower.startsWith("chemis");
    }

    private static boolean isEng(String s) {
        String lower = s.trim().toLowerCase();
        return lower.startsWith("eng") || lower.contains("english") || lower.startsWith("englis");
    }
}
