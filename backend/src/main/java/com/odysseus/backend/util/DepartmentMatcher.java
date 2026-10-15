package com.odysseus.backend.util;

public class DepartmentMatcher {

    /**
     * Checks whether two department strings match, supporting case-insensitivity,
     * abbreviations (e.g. CSE vs Computer Science Engineering), and common variants.
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

        // Computer Science Engineering variants
        boolean aCse = isCse(a);
        boolean bCse = isCse(b);
        if (aCse && bCse) return true;

        // Information Technology variants
        boolean aIt = isIt(a);
        boolean bIt = isIt(b);
        if (aIt && bIt) return true;

        // Mechanical variants
        boolean aMech = a.equalsIgnoreCase("MECH") || a.equalsIgnoreCase("Mechanical Engineering");
        boolean bMech = b.equalsIgnoreCase("MECH") || b.equalsIgnoreCase("Mechanical Engineering");
        if (aMech && bMech) return true;

        // Civil variants
        boolean aCivil = a.equalsIgnoreCase("CIVIL") || a.equalsIgnoreCase("Civil Engineering");
        boolean bCivil = b.equalsIgnoreCase("CIVIL") || b.equalsIgnoreCase("Civil Engineering");
        if (aCivil && bCivil) return true;

        // EEE variants
        boolean aEee = a.equalsIgnoreCase("EEE") || a.equalsIgnoreCase("Electrical & Electronics") || a.equalsIgnoreCase("Electrical and Electronics Engineering");
        boolean bEee = b.equalsIgnoreCase("EEE") || b.equalsIgnoreCase("Electrical & Electronics") || b.equalsIgnoreCase("Electrical and Electronics Engineering");
        if (aEee && bEee) return true;

        // ECE variants
        boolean aEce = a.equalsIgnoreCase("ECE") || a.equalsIgnoreCase("Electronics & Communication Engineering") || a.equalsIgnoreCase("Electronics and Communication Engineering");
        boolean bEce = b.equalsIgnoreCase("ECE") || b.equalsIgnoreCase("Electronics & Communication Engineering") || b.equalsIgnoreCase("Electronics and Communication Engineering");
        if (aEce && bEce) return true;

        // AI & DS variants
        boolean aAids = a.equalsIgnoreCase("AIDS") || a.equalsIgnoreCase("AI and Data Science Engineering") || a.equalsIgnoreCase("Artificial Intelligence and Data Science");
        boolean bAids = b.equalsIgnoreCase("AIDS") || b.equalsIgnoreCase("AI and Data Science Engineering") || b.equalsIgnoreCase("Artificial Intelligence and Data Science");
        if (aAids && bAids) return true;

        // AI & ML variants
        boolean aAiml = a.toLowerCase().contains("artificial intelligence & machine learning") || a.toLowerCase().contains("ai & ml") || a.equalsIgnoreCase("CSE-AIML");
        boolean bAiml = b.toLowerCase().contains("artificial intelligence & machine learning") || b.toLowerCase().contains("ai & ml") || b.equalsIgnoreCase("CSE-AIML");
        if (aAiml && bAiml) return true;

        return false;
    }

    private static boolean isCse(String s) {
        if (s == null) return false;
        String lower = s.trim().toLowerCase();
        return lower.equals("cse")
                || lower.equals("computer science engineering")
                || lower.equals("computer science & engineering")
                || lower.equals("computer science and engineering");
    }

    private static boolean isIt(String s) {
        if (s == null) return false;
        String lower = s.trim().toLowerCase();
        return lower.equals("it")
                || lower.equals("information technology");
    }
}
