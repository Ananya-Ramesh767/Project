/**
 * Centralized Academic Context synchronization utility.
 * Manages Department and Semester state across all administrative modules.
 */

export const SEMESTER_OPTIONS = [
  { value: '1', label: 'Sem 1 — Odd Semester', isOdd: true },
  { value: '2', label: 'Sem 2 — Even Semester', isOdd: false },
  { value: '3', label: 'Sem 3 — Odd Semester', isOdd: true },
  { value: '4', label: 'Sem 4 — Even Semester', isOdd: false },
  { value: '5', label: 'Sem 5 — Odd Semester', isOdd: true },
  { value: '6', label: 'Sem 6 — Even Semester', isOdd: false },
  { value: '7', label: 'Sem 7 — Odd Semester', isOdd: true },
  { value: '8', label: 'Sem 8 — Even Semester', isOdd: false }
];

export function getStoredAcademicContext() {
  let dept = localStorage.getItem('academicContext_dept') || '';
  let sem = localStorage.getItem('academicContext_semester') || '';
  if (dept === '[object Object]') {
    localStorage.removeItem('academicContext_dept');
    dept = '';
  }
  if (sem === '[object Object]') {
    localStorage.removeItem('academicContext_semester');
    sem = '';
  }
  return { department: dept, semester: sem };
}

export function setStoredAcademicContext(deptOrObj, maybeSem) {
  let dept = undefined;
  let sem = undefined;
  if (deptOrObj && typeof deptOrObj === 'object') {
    dept = deptOrObj.department;
    sem = deptOrObj.semester;
  } else {
    dept = deptOrObj;
    sem = maybeSem;
  }

  if (dept !== undefined) {
    if (dept && String(dept) !== '[object Object]') {
      localStorage.setItem('academicContext_dept', String(dept));
    } else {
      localStorage.removeItem('academicContext_dept');
    }
  }

  if (sem !== undefined) {
    if (sem && String(sem) !== '[object Object]') {
      localStorage.setItem('academicContext_semester', String(sem));
    } else {
      localStorage.removeItem('academicContext_semester');
    }
  }
}

/**
 * Determines current calendar season:
 * July to December (months 6..11): Odd Semesters (Sem 1, 3, 5, 7)
 * January to May (months 0..4): Even Semesters (Sem 2, 4, 6, 8)
 * June (month 5): Transition / configure according to schedule
 */
export function getCurrentAcademicSeason() {
  const month = new Date().getMonth(); // 0 = Jan, 11 = Dec
  if (month >= 6) {
    return { isOdd: true, label: 'Odd Semester Cycle (July–December)', applicableSems: ['1', '3', '5', '7'] };
  } else if (month <= 4) {
    return { isOdd: false, label: 'Even Semester Cycle (January–May)', applicableSems: ['2', '4', '6', '8'] };
  } else {
    // June: Inter-semester transition
    return { isOdd: true, label: 'Academic Term Transition (June)', applicableSems: ['1', '2', '3', '4', '5', '6', '7', '8'] };
  }
}
