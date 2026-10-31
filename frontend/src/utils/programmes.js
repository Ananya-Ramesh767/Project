/**
 * Central Institutional Department Constants
 * 
 * Rules:
 * 1. MAIN_INSTITUTIONAL_PROGRAMMES contains the 17 main engineering/main departments.
 *    Used across: Admin Dashboard, Subjects and Credits, Batches and Sections,
 *    Subject Offerings, Timetable views, Room Master, and Lesson Plans.
 * 
 * 2. SUPPORTING_FACULTY_DEPARTMENTS contains the 4 supporting departments:
 *    Mathematics, Physics, Chemistry, English.
 * 
 * 3. FACULTY_DEPARTMENTS contains all 21 departments (17 main + 4 supporting).
 *    Used strictly in Faculty Master for faculty profiles and rosters.
 */

export const MAIN_INSTITUTIONAL_PROGRAMMES = [
  'Artificial Intelligence and Data Science',
  'Automobile Engineering',
  'Biomedical Engineering',
  'Biotechnology',
  'Civil Engineering',
  'Computer Science and Engineering',
  'Computer Science & Engineering (Artificial Intelligence & Machine Learning)',
  'Computer Science & Engineering (Cyber Security)',
  'Computer Science and Business Systems',
  'Computer Science and Design',
  'Electrical and Electronics Engineering',
  'Electronics and Communication Engineering',
  'Information Technology',
  'Mechanical Engineering',
  'Master of Business Administration',
  'Master of Computer Applications',
  'Robotics and Automation Engineering'
];

export const SUPPORTING_FACULTY_DEPARTMENTS = [
  'Mathematics',
  'Physics',
  'Chemistry',
  'English'
];

export const FACULTY_DEPARTMENTS = [
  ...MAIN_INSTITUTIONAL_PROGRAMMES,
  ...SUPPORTING_FACULTY_DEPARTMENTS
];

// For backward compatibility across general modules, default to the 17 main departments
export const INSTITUTIONAL_PROGRAMMES = MAIN_INSTITUTIONAL_PROGRAMMES;
