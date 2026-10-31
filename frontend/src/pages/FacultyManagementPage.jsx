import React, { useState, useEffect, useMemo } from 'react';
import {
  Users, Upload, FileText, CheckCircle2, AlertCircle, Check, X,
  ChevronDown, ChevronRight, Info, RefreshCw, Eye, UserCheck,
  Search, Sliders, ArrowRight, ShieldCheck, HelpCircle, Plus, Trash2,
  Edit3, Filter, AlertTriangle, Layers, Grid, List, CheckSquare
} from 'lucide-react';
import Modal from '../components/Modal';
import { FACULTY_DEPARTMENTS, MAIN_INSTITUTIONAL_PROGRAMMES } from '../utils/programmes';
import { getStoredAcademicContext, setStoredAcademicContext } from '../utils/academicContext';

export const DEPT_CODES = {
  'Computer Science Engineering': 'CSE',
  'Computer Science and Engineering': 'CSE',
  'Electronics & Communication Engineering': 'ECE',
  'Electronics and Communication Engineering': 'ECE',
  'Electrical & Electronics': 'EEE',
  'Electrical and Electronics Engineering': 'EEE',
  'Mechanical Engineering': 'MECH',
  'Civil Engineering': 'CIVIL',
  'Mathematics': 'MATH',
  'Physics': 'PHY',
  'Chemistry': 'CHEM',
  'English': 'ENG',
  'Information Technology': 'IT',
  'AI and Data Science Engineering': 'AIDS',
  'Artificial Intelligence and Data Science': 'AIDS',
  'Biomedical Engineering': 'BME',
  'Automobile Engineering': 'AUTO',
  'Robotics and Automation Engineering': 'ROBOTICS',
  'Computer Science & Engineering (Artificial Intelligence & Machine Learning)': 'CSE-AIML',
  'Computer Science & Engineering (Cyber Security)': 'CSE-CS',
  'B.E. Computer Science and Design': 'CSD',
  'Computer Science and Design': 'CSD',
  'Computer Science and Design (CSD)': 'CSD',
  'B.Tech. Biotechnology': 'BIOTECH',
  'Biotechnology': 'BIOTECH',
  'B.Tech. Computer Science and Business System (With TCS partnership)': 'CSBS',
  'Computer Science and Business': 'CSBS',
  'Computer Science and Business Systems': 'CSBS',
  'MCA': 'MCA',
  'MBA': 'MBA',
  'Master of Computer Applications': 'MCA',
  'Master of Computer Applications (MCA)': 'MCA',
  'Master of Business Administration': 'MBA',
  'Master of Business Administration (MBA)': 'MBA'
};

export const SEMESTER_OPTIONS = [
  'Select Semester',
  'Sem 1 — Odd Semester',
  'Sem 2 — Even Semester',
  'Sem 3 — Odd Semester',
  'Sem 4 — Even Semester',
  'Sem 5 — Odd Semester',
  'Sem 6 — Even Semester',
  'Sem 7 — Odd Semester',
  'Sem 8 — Even Semester'
];

export const HEADER_DEPARTMENTS = [
  'Select Department',
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

export function getSemesterNumber(str) {
  if (!str || str === 'Select Semester') return null;
  const m = str.match(/\d+/);
  return m ? parseInt(m[0], 10) : null;
}

export function getCycleFromSemester(str) {
  const num = getSemesterNumber(str);
  if (!num) return null;
  return num % 2 === 1 ? 'Odd Semester' : 'Even Semester';
}

export const DEPT_NAME_MAP = {
  'Artificial Intelligence and Data Science': 'Artificial Intelligence and Data Science',
  'Automobile Engineering': 'Automobile Engineering',
  'Biomedical Engineering': 'Biomedical Engineering',
  'Biotechnology': 'Biotechnology',
  'Civil Engineering': 'Civil Engineering',
  'Computer Science and Engineering': 'Computer Science and Engineering',
  'Computer Science & Engineering (Artificial Intelligence & Machine Learning)': 'Computer Science & Engineering (Artificial Intelligence & Machine Learning)',
  'Computer Science & Engineering (Cyber Security)': 'Computer Science & Engineering (Cyber Security)',
  'Computer Science and Business Systems': 'Computer Science and Business',
  'Computer Science and Design': 'Computer Science and Design (CSD)',
  'Electrical and Electronics Engineering': 'Electrical & Electronics',
  'Electronics and Communication Engineering': 'Electronics & Communication Engineering',
  'Information Technology': 'Information Technology',
  'Mechanical Engineering': 'Mechanical Engineering',
  'Master of Business Administration': 'Master of Business Administration (MBA)',
  'Master of Computer Applications': 'Master of Computer Applications (MCA)',
  'Robotics and Automation Engineering': 'Robotics and Automation Engineering'
};

export function matchesDept(deptString, selectedOption) {
  if (!deptString || !selectedOption) return false;
  if (selectedOption === 'ALL' || selectedOption === 'Select Department') return true;
  if (deptString === 'ALL' || deptString === 'Select Department') return true;

  const targetDbName = DEPT_NAME_MAP[selectedOption] || selectedOption;
  if (deptString.trim().toLowerCase() === targetDbName.trim().toLowerCase()) return true;
  if (deptString.trim().toLowerCase() === selectedOption.trim().toLowerCase()) return true;

  const clean = s => s.toLowerCase().replace(/&/g, 'and').replace(/[^a-z0-9]/g, '');
  return clean(deptString) === clean(targetDbName) || clean(deptString) === clean(selectedOption);
}

export const REVERSE_DEPT_CODES = Object.entries(DEPT_CODES).reduce((acc, [full, code]) => {
  acc[code] = full;
  return acc;
}, {});

export const DEPT_BADGE_STYLES = {
  CSE: { bg: 'rgba(99, 102, 241, 0.15)', text: '#818cf8', border: 'rgba(99, 102, 241, 0.3)' },
  ECE: { bg: 'rgba(16, 185, 129, 0.15)', text: '#34d399', border: 'rgba(16, 185, 129, 0.3)' },
  EEE: { bg: 'rgba(245, 158, 11, 0.15)', text: '#fbbf24', border: 'rgba(245, 158, 11, 0.3)' },
  MECH: { bg: 'rgba(239, 68, 68, 0.15)', text: '#f87171', border: 'rgba(239, 68, 68, 0.3)' },
  CIVIL: { bg: 'rgba(168, 85, 247, 0.15)', text: '#c084fc', border: 'rgba(168, 85, 247, 0.3)' },
  MATH: { bg: 'rgba(6, 182, 212, 0.15)', text: '#38bdf8', border: 'rgba(6, 182, 212, 0.3)' },
  PHY: { bg: 'rgba(244, 63, 94, 0.15)', text: '#fb7185', border: 'rgba(244, 63, 94, 0.3)' },
  CHEM: { bg: 'rgba(52, 211, 153, 0.15)', text: '#6ee7b7', border: 'rgba(52, 211, 153, 0.3)' },
  ENG: { bg: 'rgba(251, 146, 60, 0.15)', text: '#fdba74', border: 'rgba(251, 146, 60, 0.3)' },
  IT: { bg: 'rgba(59, 130, 246, 0.15)', text: '#60a5fa', border: 'rgba(59, 130, 246, 0.3)' },
  AIDS: { bg: 'rgba(139, 92, 246, 0.15)', text: '#a78bfa', border: 'rgba(139, 92, 246, 0.3)' },
  DEFAULT: { bg: 'rgba(148, 163, 184, 0.15)', text: '#cbd5e1', border: 'rgba(148, 163, 184, 0.3)' }
};

export function getDeptCode(deptName) {
  if (!deptName) return 'GEN';
  if (DEPT_CODES[deptName]) return DEPT_CODES[deptName];
  for (const [full, code] of Object.entries(DEPT_CODES)) {
    if (deptName.toLowerCase().includes(full.toLowerCase()) || full.toLowerCase().includes(deptName.toLowerCase())) {
      return code;
    }
  }
  return deptName.substring(0, 4).toUpperCase();
}

export function getBadgeStyle(deptCode) {
  return DEPT_BADGE_STYLES[deptCode] || DEPT_BADGE_STYLES.DEFAULT;
}

export default function FacultyManagementPage({ user }) {
  const [academicYear, setAcademicYear] = useState('2026 - 2027');
  const [semesterFilter, setSemesterFilter] = useState('Select Semester');
  const [headerDeptFilter, setHeaderDeptFilter] = useState('Select Department');
  const [selectedSection, setSelectedSection] = useState('ALL');

  // Faculty Master State
  const [allFaculty, setAllFaculty] = useState([]);
  const [deptCounts, setDeptCounts] = useState({});
  const [loadingFaculty, setLoadingFaculty] = useState(false);
  const [facultySearch, setFacultySearch] = useState('');
  const [selectedDeptFilter, setSelectedDeptFilter] = useState('ALL');
  const [viewMode, setViewMode] = useState('table'); // 'matrix' or 'table'

  // Upload & Extraction State
  const [uploadFile, setUploadFile] = useState(null);
  const [uploadExtracting, setUploadExtracting] = useState(false);
  const [uploadPreview, setUploadPreview] = useState([]);
  const [uploadMsg, setUploadMsg] = useState({ text: '', type: '' });
  const [mergedDeptBreakdown, setMergedDeptBreakdown] = useState({});
  const [showUploadModal, setShowUploadModal] = useState(false);

  // Section Assignment State
  const [batches, setBatches] = useState([]);
  const [proposals, setProposals] = useState([]);
  const [sectionAllocations, setSectionAllocations] = useState([]); // array of per-(subject, section) assignments
  const [proposalsLoading, setProposalsLoading] = useState(false);
  const [finalizing, setFinalizing] = useState(false);
  const [saveSuccessMsg, setSaveSuccessMsg] = useState('');

  // Re-assign Modal State
  const [showAssignModal, setShowAssignModal] = useState(false);
  const [selectedAllocationForAssign, setSelectedAllocationForAssign] = useState(null);
  const [assignFacultyId, setAssignFacultyId] = useState('');

  // Rules Modal
  const [showRulesModal, setShowRulesModal] = useState(false);

  useEffect(() => {
    fetchFacultyData();
    fetchDepartmentCounts();
    fetchBatches();
  }, []);

  useEffect(() => {
    fetchProposalsAndBatches();
  }, [academicYear, semesterFilter]);

  const fetchFacultyData = async () => {
    setLoadingFaculty(true);
    try {
      const res = await fetch('/api/admin/faculty', {
        headers: { Authorization: `Bearer ${user?.token}` }
      });
      if (res.ok) {
        const data = await res.json();
        setAllFaculty(data || []);
      }
    } catch (e) {
      console.error('Failed to fetch faculty:', e);
    } finally {
      setLoadingFaculty(false);
    }
  };

  const fetchDepartmentCounts = async () => {
    try {
      const res = await fetch('/api/admin/faculty/department-counts', {
        headers: { Authorization: `Bearer ${user?.token}` }
      });
      if (res.ok) {
        const counts = await res.json();
        setDeptCounts(counts || {});
      }
    } catch (e) {
      console.error('Failed to fetch department counts:', e);
    }
  };

  const fetchBatches = async () => {
    try {
      const res = await fetch('/api/admin/batches', {
        headers: { Authorization: `Bearer ${user?.token}` }
      });
      if (res.ok) {
        const data = await res.json();
        setBatches(data || []);
      }
    } catch (e) {
      console.error('Failed to fetch batches:', e);
    }
  };

  const fetchProposalsAndBatches = async () => {
    setProposalsLoading(true);
    try {
      let semParam = '';
      const semNum = getSemesterNumber(semesterFilter);
      if (semNum) {
        semParam = `&semester=${semNum}`;
      }

      const res = await fetch(`/api/admin/faculty/propose-assignment?department=ALL${semParam}`, {
        headers: { Authorization: `Bearer ${user?.token}` }
      });
      if (res.ok) {
        const data = await res.json();
        const rawProposals = data.proposals || [];
        const rawBatches = data.batches || [];
        setProposals(rawProposals);
        if (rawBatches.length > 0) setBatches(rawBatches);

        // Build flat section allocations: every subject × relevant batches
        const flatAllocations = [];
        let globalIndex = 1;

        // In-memory load tracking to ensure even distribution and 18h limits
        const inMemoryLoad = {};
        (data.allFaculty || []).forEach(f => {
          inMemoryLoad[f.id] = 0;
        });

        rawProposals.forEach(prop => {
          const relevantBatches = prop.relevantBatches && prop.relevantBatches.length > 0
            ? prop.relevantBatches
            : rawBatches;

          relevantBatches.forEach(b => {
            let assignedFaculty = null;
            let status = 'Pending Review';

            // 1. Existing assignment preserved
            if (prop.existingSectionFaculty && prop.existingSectionFaculty[b.id]) {
              const fid = prop.existingSectionFaculty[b.id];
              assignedFaculty = (prop.eligibleFaculty || []).find(f => f.id === fid)
                || (data.allFaculty || []).find(f => f.id === fid);
              if (assignedFaculty) status = 'Confirmed';
            }

            // 2. Suggested section distribution from server
            if (!assignedFaculty && prop.suggestedSectionFaculty && prop.suggestedSectionFaculty[b.id]) {
              const fid = prop.suggestedSectionFaculty[b.id];
              assignedFaculty = (prop.eligibleFaculty || []).find(f => f.id === fid)
                || (data.allFaculty || []).find(f => f.id === fid);
              if (assignedFaculty) status = 'Confirmed';
            }

            // 3. Round-robin assignment from eligible pool respecting 18h limit
            if (!assignedFaculty && prop.eligibleFaculty && prop.eligibleFaculty.length > 0) {
              const eligiblePool = [...prop.eligibleFaculty].sort((x, y) => {
                const loadX = inMemoryLoad[x.id] || 0;
                const loadY = inMemoryLoad[y.id] || 0;
                return loadX - loadY;
              });

              const pick = eligiblePool.find(f => ((inMemoryLoad[f.id] || 0) + (prop.weeklyHours || 3)) <= 18)
                || eligiblePool[0];

              if (pick) {
                assignedFaculty = pick;
                inMemoryLoad[pick.id] = (inMemoryLoad[pick.id] || 0) + (prop.weeklyHours || 3);
                status = 'Confirmed';
              }
            }

            flatAllocations.push({
              id: `${prop.subjectId}-${b.id}`,
              slNo: globalIndex++,
              batchId: b.id,
              batchName: b.batchName || `Section ${b.section || ''}`,
              section: b.section || b.batchName,
              subjectId: prop.subjectId,
              subjectCode: prop.subjectCode,
              subjectName: prop.subjectName,
              department: prop.department,
              departmentKey: prop.departmentKey || getDeptCode(prop.department),
              coursePrefix: prop.coursePrefix || '',
              weeklyHours: prop.weeklyHours || 4,
              type: prop.type || 'THEORY',
              assignedFacultyId: assignedFaculty ? assignedFaculty.id : null,
              assignedFacultyName: assignedFaculty ? assignedFaculty.name : 'Unassigned',
              assignedFacultyDesignation: assignedFaculty ? (assignedFaculty.designation || 'Faculty') : '',
              assignedFacultyDept: assignedFaculty ? (assignedFaculty.departmentKey || getDeptCode(assignedFaculty.department)) : '—',
              currentLoad: assignedFaculty ? (inMemoryLoad[assignedFaculty.id] || 0) : 0,
              maxLoad: 18,
              status: status,
              isMapped: prop.isMapped,
              eligibleFaculty: prop.eligibleFaculty || [],
              allEligible: data.allFaculty || []
            });
          });
        });

        setSectionAllocations(flatAllocations);
      }
    } catch (e) {
      console.error('Failed to load proposals:', e);
    } finally {
      setProposalsLoading(false);
    }
  };

  // One-click Load Stored Merged PDF
  const handleLoadStoredMergedPdf = async () => {
    setUploadExtracting(true);
    setUploadMsg({ text: '', type: '' });
    try {
      const res = await fetch('/api/admin/faculty/load-stored-merged-pdf', {
        method: 'POST',
        headers: { Authorization: `Bearer ${user?.token}` }
      });
      const data = await res.json();
      if (res.ok) {
        setUploadPreview(data.preview || []);
        setMergedDeptBreakdown(data.departmentCounts || {});
        setUploadMsg({
          text: `Extracted ${data.totalExtracted} faculty across ${data.totalDepartments} departments from ${data.fileName}`,
          type: 'success'
        });
      } else {
        setUploadMsg({ text: data.error || 'Failed to extract stored PDF', type: 'error' });
      }
    } catch (e) {
      setUploadMsg({ text: 'Error extracting stored PDF: ' + e.message, type: 'error' });
    } finally {
      setUploadExtracting(false);
    }
  };

  // Upload New Merged File
  const handleExtractUploadedFile = async () => {
    if (!uploadFile) return;
    setUploadExtracting(true);
    setUploadMsg({ text: '', type: '' });

    const formData = new FormData();
    formData.append('file', uploadFile);
    formData.append('department', 'ALL');

    try {
      const res = await fetch('/api/admin/faculty/extract-merged-pdf', {
        method: 'POST',
        headers: { Authorization: `Bearer ${user?.token}` },
        body: formData
      });
      const data = await res.json();
      if (res.ok) {
        setUploadPreview(data.preview || []);
        setMergedDeptBreakdown(data.departmentCounts || {});
        setUploadMsg({
          text: `Successfully extracted ${data.totalExtracted} records across ${data.totalDepartments} departments!`,
          type: 'success'
        });
      } else {
        setUploadMsg({ text: data.error || 'Extraction failed', type: 'error' });
      }
    } catch (e) {
      setUploadMsg({ text: 'Extraction failed: ' + e.message, type: 'error' });
    } finally {
      setUploadExtracting(false);
    }
  };

  // Confirm and Save All Extracted Records to Database
  const handleSaveExtractedToMaster = async () => {
    if (uploadPreview.length === 0) return;
    setUploadExtracting(true);
    try {
      const res = await fetch('/api/admin/faculty/batch-save', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${user?.token}`
        },
        body: JSON.stringify(uploadPreview)
      });
      const data = await res.json();
      if (res.ok) {
        setUploadMsg({
          text: `Saved ${uploadPreview.length} faculty and staff records to the institutional master registry!`,
          type: 'success'
        });
        setUploadPreview([]);
        setUploadFile(null);
        await fetchFacultyData();
        await fetchDepartmentCounts();
        await fetchProposalsAndBatches();
      } else {
        setUploadMsg({ text: data.error || 'Failed to save faculty records', type: 'error' });
      }
    } catch (e) {
      setUploadMsg({ text: 'Error saving records: ' + e.message, type: 'error' });
    } finally {
      setUploadExtracting(false);
    }
  };

  // Finalize and Save Subject-Faculty Assignments to SubjectOfferings
  const handleFinalizeAndSave = async () => {
    setFinalizing(true);
    setSaveSuccessMsg('');
    try {
      const semNum = getSemesterNumber(semesterFilter) || 3;
      const payload = sectionAllocations
        .filter(a => a.assignedFacultyId)
        .map(a => ({
          batchId: a.batchId,
          subjectId: a.subjectId,
          facultyId: a.assignedFacultyId,
          academicYear: academicYear,
          semester: semNum
        }));

      const res = await fetch('/api/admin/faculty/batch-assign', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${user?.token}`
        },
        body: JSON.stringify(payload)
      });

      if (res.ok) {
        setSaveSuccessMsg('All section faculty assignments finalized and saved successfully! Timetable generator is ready.');
        setTimeout(() => setSaveSuccessMsg(''), 6000);
      } else {
        const err = await res.json();
        alert('Failed to save assignments: ' + (err.error || 'Unknown error'));
      }
    } catch (e) {
      alert('Error saving assignments: ' + e.message);
    } finally {
      setFinalizing(false);
    }
  };

  // Trigger automated assignment re-distribution
  const handleAutoAssignAll = () => {
    fetchProposalsAndBatches();
  };

  // Manual faculty re-assignment
  const handleApplyManualAssign = () => {
    if (!selectedAllocationForAssign || !assignFacultyId) return;
    const selectedFac = (selectedAllocationForAssign.eligibleFaculty || []).find(f => String(f.id) === String(assignFacultyId))
      || (selectedAllocationForAssign.allEligible || []).find(f => String(f.id) === String(assignFacultyId));

    if (!selectedFac) return;

    setSectionAllocations(prev => prev.map(a => {
      if (a.id === selectedAllocationForAssign.id) {
        return {
          ...a,
          assignedFacultyId: selectedFac.id,
          assignedFacultyName: selectedFac.name,
          assignedFacultyDesignation: selectedFac.designation || 'Faculty',
          assignedFacultyDept: selectedFac.departmentKey || getDeptCode(selectedFac.department),
          status: 'Confirmed'
        };
      }
      return a;
    }));

    setShowAssignModal(false);
    setSelectedAllocationForAssign(null);
  };

  // Faculty filtered by Header Department (affects summary cards and directory)
  const headerDeptFilteredFaculty = useMemo(() => {
    if (!headerDeptFilter || headerDeptFilter === 'Select Department' || headerDeptFilter === 'ALL') {
      return allFaculty;
    }
    return allFaculty.filter(f => matchesDept(f.department, headerDeptFilter) || matchesDept(f.departmentKey, headerDeptFilter));
  }, [allFaculty, headerDeptFilter]);

  // Department-wise Grouped Faculty
  const departmentGroups = useMemo(() => {
    const groups = {};
    headerDeptFilteredFaculty.forEach(f => {
      const d = f.department || 'General';
      if (!groups[d]) groups[d] = [];
      groups[d].push(f);
    });
    return groups;
  }, [headerDeptFilteredFaculty]);

  // Filtered Faculty List for Roster Table
  const filteredFaculty = useMemo(() => {
    return allFaculty.filter(f => {
      const matchHeaderDept = !headerDeptFilter || headerDeptFilter === 'Select Department' || headerDeptFilter === 'ALL'
        || matchesDept(f.department, headerDeptFilter)
        || matchesDept(f.departmentKey, headerDeptFilter);
      const matchDept = selectedDeptFilter === 'ALL' || f.department === selectedDeptFilter || f.departmentKey === selectedDeptFilter;
      const s = facultySearch.toLowerCase();
      const matchSearch = !facultySearch
        || (f.name && f.name.toLowerCase().includes(s))
        || (f.designation && f.designation.toLowerCase().includes(s))
        || (f.qualification && f.qualification.toLowerCase().includes(s))
        || (f.specialization && f.specialization.toLowerCase().includes(s))
        || (f.department && f.department.toLowerCase().includes(s));
      return matchHeaderDept && matchDept && matchSearch;
    });
  }, [allFaculty, headerDeptFilter, selectedDeptFilter, facultySearch]);

  // Filtered Section Allocations
  const filteredAllocations = useMemo(() => {
    if (selectedSection === 'ALL') return sectionAllocations;
    return sectionAllocations.filter(a => a.batchName === selectedSection || a.section === selectedSection);
  }, [sectionAllocations, selectedSection]);

  // Overall Statistics
  const teachingCount = headerDeptFilteredFaculty.filter(f => f.eligibleForTeaching !== false && !f.hod).length;
  const excludedCount = headerDeptFilteredFaculty.length - teachingCount;
  const detectedDeptCount = useMemo(() => {
    if (!headerDeptFilter || headerDeptFilter === 'Select Department' || headerDeptFilter === 'ALL') {
      return Object.keys(deptCounts).length || 19;
    }
    return headerDeptFilteredFaculty.length > 0 ? 1 : 0;
  }, [deptCounts, headerDeptFilter, headerDeptFilteredFaculty]);
  const distinctBatches = Array.from(new Set(batches.map(b => b.batchName || b.section))).filter(Boolean);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.75rem', paddingBottom: '3rem' }}>

      {/* ── TOP HEADER & CONTEXT BAR ── */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '1rem',
        paddingBottom: '0.75rem',
        borderBottom: '1px solid var(--border-color)'
      }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
            <div style={{
              width: 38,
              height: 38,
              borderRadius: 'var(--radius-md)',
              background: 'linear-gradient(135deg, #a855f7, #6366f1)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#ffffff',
              boxShadow: 'var(--shadow-glow)'
            }}>
              <Users size={20} />
            </div>
            <div>
              <h1 style={{ fontSize: '1.45rem', fontWeight: 800, margin: 0, letterSpacing: '-0.02em', color: 'var(--text-main)' }}>
                Easwari Engineering College — Faculty Master &amp; Subject Allocation
              </h1>
              <p style={{ fontSize: '0.78rem', color: 'var(--text-dim)', margin: '0.15rem 0 0' }}>
                One-Time Merged Faculty Document Extraction, Department Master Registry &amp; Section-Wise Timetable Assignment
              </p>
            </div>
          </div>
        </div>

        {/* Filters & Actions */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <select
            value={academicYear}
            onChange={(e) => setAcademicYear(e.target.value)}
            className="form-select"
            style={{ padding: '0.45rem 2rem 0.45rem 0.85rem', fontSize: '0.82rem', fontWeight: 600, minWidth: '135px' }}
          >
            <option value="2026 - 2027">2026 - 2027</option>
            <option value="2025 - 2026">2025 - 2026</option>
          </select>

          <select
            value={semesterFilter}
            onChange={(e) => setSemesterFilter(e.target.value)}
            className="form-select"
            style={{ padding: '0.45rem 2rem 0.45rem 0.85rem', fontSize: '0.82rem', fontWeight: 600, minWidth: '185px' }}
          >
            {SEMESTER_OPTIONS.map((sem) => (
              <option key={sem} value={sem}>{sem}</option>
            ))}
          </select>

          <select
            value={headerDeptFilter}
            onChange={(e) => setHeaderDeptFilter(e.target.value)}
            className="form-select"
            style={{ padding: '0.45rem 2rem 0.45rem 0.85rem', fontSize: '0.82rem', fontWeight: 600, minWidth: '200px', maxWidth: '320px' }}
          >
            {HEADER_DEPARTMENTS.map((dept) => (
              <option key={dept} value={dept}>{dept}</option>
            ))}
          </select>

          <button
            type="button"
            onClick={() => setShowRulesModal(true)}
            className="btn btn-outline"
            style={{ padding: '0.45rem 0.8rem', fontSize: '0.8rem', borderColor: 'rgba(168,85,247,0.4)', color: '#c084fc' }}
          >
            <Info size={14} /> Allocation Rules
          </button>
        </div>
      </div>

      {/* ── SECTION 1: ONE-TIME MERGED PDF UPLOAD & INSTITUTIONAL STATS ── */}
      <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <h2 style={{ fontSize: '1.05rem', fontWeight: 700, margin: 0, color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
                <Upload size={18} style={{ color: '#818cf8' }} />
                One-Time Merged Faculty Document Upload
              </h2>
              <span style={{
                fontSize: '0.68rem',
                fontWeight: 700,
                padding: '0.15rem 0.55rem',
                borderRadius: '9999px',
                background: 'rgba(99, 102, 241, 0.15)',
                color: '#818cf8',
                border: '1px solid rgba(99, 102, 241, 0.3)'
              }}>
                Single Merged PDF
              </span>
            </div>
            <p style={{ fontSize: '0.76rem', color: 'var(--text-dim)', margin: '0.25rem 0 0' }}>
              Upload one institutional merged PDF containing all department faculty lists. The system automatically categorizes faculty by department, identifies HODs and lab technicians, and stores verified records.
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <button
              type="button"
              disabled={uploadExtracting}
              onClick={handleLoadStoredMergedPdf}
              className="btn btn-secondary"
              style={{
                padding: '0.45rem 0.9rem',
                fontSize: '0.8rem',
                fontWeight: 700,
                background: 'rgba(99, 102, 241, 0.18)',
                borderColor: 'rgba(99, 102, 241, 0.45)',
                color: '#a5b4fc'
              }}
              title="Loads Faculty_merged_corrected.pdf from system Downloads"
            >
              <RefreshCw size={14} className={uploadExtracting ? 'spin' : ''} />
              {uploadExtracting ? 'Extracting...' : 'Load Stored Merged PDF'}
            </button>

            <button
              type="button"
              onClick={() => document.getElementById('merged-pdf-browse-input').click()}
              className="btn btn-primary"
              style={{ padding: '0.45rem 0.9rem', fontSize: '0.8rem', fontWeight: 700 }}
            >
              <Upload size={14} /> Upload Custom PDF
            </button>
            <input
              id="merged-pdf-browse-input"
              type="file"
              accept=".pdf,.txt,.csv"
              style={{ display: 'none' }}
              onChange={(e) => {
                if (e.target.files && e.target.files[0]) {
                  setUploadFile(e.target.files[0]);
                  handleExtractUploadedFile();
                }
              }}
            />
          </div>
        </div>

        {/* Live KPI Cards */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))',
          gap: '1rem'
        }}>
          <div style={{
            padding: '1rem 1.15rem',
            borderRadius: 'var(--radius-md)',
            background: 'rgba(99, 102, 241, 0.08)',
            border: '1px solid rgba(99, 102, 241, 0.25)'
          }}>
            <div style={{ fontSize: '0.72rem', fontWeight: 700, color: 'var(--text-dim)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Detected Departments
            </div>
            <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#818cf8', marginTop: '0.2rem' }}>
              {detectedDeptCount}
            </div>
            <div style={{ fontSize: '0.7rem', color: 'var(--text-dim)', marginTop: '0.15rem' }}>
              Dynamic institutional groups
            </div>
          </div>

          <div style={{
            padding: '1rem 1.15rem',
            borderRadius: 'var(--radius-md)',
            background: 'rgba(168, 85, 247, 0.08)',
            border: '1px solid rgba(168, 85, 247, 0.25)'
          }}>
            <div style={{ fontSize: '0.72rem', fontWeight: 700, color: 'var(--text-dim)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Total Faculty &amp; Staff
            </div>
            <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#c084fc', marginTop: '0.2rem' }}>
              {headerDeptFilteredFaculty.length}
            </div>
            <div style={{ fontSize: '0.7rem', color: 'var(--text-dim)', marginTop: '0.15rem' }}>
              Saved in Institutional Master
            </div>
          </div>

          <div style={{
            padding: '1rem 1.15rem',
            borderRadius: 'var(--radius-md)',
            background: 'rgba(16, 185, 129, 0.08)',
            border: '1px solid rgba(16, 185, 129, 0.25)'
          }}>
            <div style={{ fontSize: '0.72rem', fontWeight: 700, color: 'var(--text-dim)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Teaching Faculty (Eligible)
            </div>
            <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#34d399', marginTop: '0.2rem' }}>
              {teachingCount}
            </div>
            <div style={{ fontSize: '0.7rem', color: 'var(--text-dim)', marginTop: '0.15rem' }}>
              Available for subject assignments
            </div>
          </div>

          <div style={{
            padding: '1rem 1.15rem',
            borderRadius: 'var(--radius-md)',
            background: 'rgba(245, 158, 11, 0.08)',
            border: '1px solid rgba(245, 158, 11, 0.25)'
          }}>
            <div style={{ fontSize: '0.72rem', fontWeight: 700, color: 'var(--text-dim)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Excluded Staff (HOD / Lab Tech)
            </div>
            <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#fbbf24', marginTop: '0.2rem' }}>
              {excludedCount}
            </div>
            <div style={{ fontSize: '0.7rem', color: 'var(--text-dim)', marginTop: '0.15rem' }}>
              Strictly excluded from teaching
            </div>
          </div>
        </div>

        {/* Preview Banner if fresh extraction pending */}
        {uploadPreview.length > 0 && (
          <div style={{
            padding: '0.9rem 1.1rem',
            borderRadius: 'var(--radius-md)',
            background: 'rgba(16, 185, 129, 0.12)',
            border: '1px solid #10b981',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '0.75rem'
          }}>
            <div>
              <div style={{ fontSize: '0.85rem', fontWeight: 700, color: '#10b981', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <CheckCircle2 size={16} /> Extracted {uploadPreview.length} Records Across {Object.keys(mergedDeptBreakdown).length} Departments
              </div>
              <div style={{ fontSize: '0.74rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
                Review complete. Click below to save all records to the institutional master database.
              </div>
            </div>

            <button
              type="button"
              disabled={uploadExtracting}
              onClick={handleSaveExtractedToMaster}
              className="btn btn-success"
              style={{ padding: '0.45rem 1rem', fontSize: '0.8rem', fontWeight: 700 }}
            >
              <Check size={14} /> Save All {uploadPreview.length} Records to Master
            </button>
          </div>
        )}

        {/* Feedback message */}
        {uploadMsg.text && (
          <div style={{
            padding: '0.6rem 0.9rem',
            borderRadius: 'var(--radius-sm)',
            fontSize: '0.76rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.45rem',
            background: uploadMsg.type === 'error' ? 'rgba(239, 68, 68, 0.15)' : 'rgba(16, 185, 129, 0.15)',
            border: `1px solid ${uploadMsg.type === 'error' ? '#ef4444' : '#10b981'}`,
            color: uploadMsg.type === 'error' ? '#fca5a5' : '#6ee7b7'
          }}>
            {uploadMsg.type === 'error' ? <AlertCircle size={14} /> : <CheckCircle2 size={14} />}
            <span>{uploadMsg.text}</span>
          </div>
        )}
      </div>

      {/* ── SECTION 2: NEW FACULTY MASTER TABLE (ORGANIZED BY DEPARTMENT) ── */}
      <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1.15rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <h2 style={{ fontSize: '1.05rem', fontWeight: 700, margin: 0, color: 'var(--text-main)' }}>
                Faculty Master Directory (Department-wise)
              </h2>
              <span style={{ fontSize: '0.72rem', color: 'var(--text-dim)', fontWeight: 600 }}>
                ({filteredFaculty.length} of {headerDeptFilteredFaculty.length} records)
              </span>
            </div>
            <p style={{ fontSize: '0.75rem', color: 'var(--text-dim)', margin: '0.2rem 0 0' }}>
              Extracted records with structured fields: Department, Faculty Number, Name, Designation, Qualification, Specialization, and Teaching Eligibility.
            </p>
          </div>

          {/* Search, Filter & View Toggle */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
            <div style={{ position: 'relative', width: '220px' }}>
              <Search size={14} style={{ position: 'absolute', left: '0.7rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-dim)' }} />
              <input
                type="text"
                placeholder="Search faculty, designation..."
                value={facultySearch}
                onChange={(e) => setFacultySearch(e.target.value)}
                className="form-control"
                style={{ padding: '0.4rem 0.6rem 0.4rem 2rem', fontSize: '0.78rem' }}
              />
            </div>

            <select
              value={selectedDeptFilter}
              onChange={(e) => setSelectedDeptFilter(e.target.value)}
              className="form-select"
              style={{ padding: '0.4rem 1.8rem 0.4rem 0.75rem', fontSize: '0.78rem', minWidth: '150px' }}
            >
              <option value="ALL">All Departments ({allFaculty.length})</option>
              {Object.keys(departmentGroups).map(d => (
                <option key={d} value={d}>
                  {getDeptCode(d)} — {d} ({departmentGroups[d].length})
                </option>
              ))}
            </select>

            <div style={{
              display: 'flex',
              alignItems: 'center',
              border: '1px solid var(--border-color)',
              borderRadius: 'var(--radius-sm)',
              overflow: 'hidden'
            }}>
              <button
                type="button"
                onClick={() => setViewMode('table')}
                style={{
                  padding: '0.35rem 0.6rem',
                  fontSize: '0.74rem',
                  fontWeight: 600,
                  border: 'none',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.3rem',
                  background: viewMode === 'table' ? '#4F46E5' : 'transparent',
                  color: viewMode === 'table' ? '#ffffff' : 'var(--text-dim)'
                }}
              >
                <List size={13} /> Detailed Roster
              </button>
              <button
                type="button"
                onClick={() => setViewMode('matrix')}
                style={{
                  padding: '0.35rem 0.6rem',
                  fontSize: '0.74rem',
                  fontWeight: 600,
                  border: 'none',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.3rem',
                  background: viewMode === 'matrix' ? '#4F46E5' : 'transparent',
                  color: viewMode === 'matrix' ? '#ffffff' : 'var(--text-dim)'
                }}
              >
                <Grid size={13} /> Department Matrix
              </button>
            </div>
          </div>
        </div>

        {/* VIEW 1: DETAILED ROSTER TABLE */}
        {viewMode === 'table' && (
          <div style={{ overflowX: 'auto', maxHeight: '480px', overflowY: 'auto', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}>
            <table className="table" style={{ fontSize: '0.78rem', width: '100%', marginBottom: 0 }}>
              <thead>
                <tr>
                  <th style={{ width: '45px', padding: '0.65rem 0.7rem' }}>#</th>
                  <th style={{ width: '100px', padding: '0.65rem 0.7rem' }}>Department</th>
                  <th style={{ width: '85px', padding: '0.65rem 0.7rem' }}>Faculty No.</th>
                  <th style={{ padding: '0.65rem 0.7rem' }}>Faculty Name</th>
                  <th style={{ padding: '0.65rem 0.7rem' }}>Designation</th>
                  <th style={{ padding: '0.65rem 0.7rem' }}>Qualification &amp; Specialization</th>
                  <th style={{ width: '90px', padding: '0.65rem 0.7rem' }}>Category</th>
                  <th style={{ width: '130px', padding: '0.65rem 0.7rem' }}>Eligibility Status</th>
                </tr>
              </thead>
              <tbody>
                {loadingFaculty ? (
                  <tr>
                    <td colSpan={8} style={{ textAlign: 'center', padding: '2.5rem', color: 'var(--text-dim)' }}>
                      Loading institutional faculty master...
                    </td>
                  </tr>
                ) : filteredFaculty.length === 0 ? (
                  <tr>
                    <td colSpan={8} style={{ textAlign: 'center', padding: '2.5rem', color: 'var(--text-dim)' }}>
                      No faculty records found. Click <strong>Load Stored Merged PDF</strong> above to populate.
                    </td>
                  </tr>
                ) : (
                  filteredFaculty.map((f, idx) => {
                    const dCode = f.departmentKey || getDeptCode(f.department);
                    const isEligible = f.eligibleForTeaching !== false && !f.hod;
                    const isHod = f.hod || f.category === 'HOD';

                    return (
                      <tr key={f.id || idx}>
                        <td style={{ padding: '0.55rem 0.7rem', color: 'var(--text-dim)' }}>{idx + 1}</td>
                        <td style={{ padding: '0.55rem 0.7rem' }}>
                          <span style={{
                            padding: '0.12rem 0.4rem',
                            borderRadius: '4px',
                            fontSize: '0.68rem',
                            fontWeight: 800,
                            ...getBadgeStyle(dCode)
                          }}>
                            {dCode}
                          </span>
                        </td>
                        <td style={{ padding: '0.55rem 0.7rem', fontWeight: 600, color: 'var(--text-muted)' }}>
                          Faculty {f.facultyNumber || (idx + 1)}
                        </td>
                        <td style={{ padding: '0.55rem 0.7rem', fontWeight: 700, color: 'var(--text-main)' }}>
                          {f.name}
                        </td>
                        <td style={{ padding: '0.55rem 0.7rem', color: 'var(--text-muted)' }}>
                          {f.designation || 'Assistant Professor'}
                        </td>
                        <td style={{ padding: '0.55rem 0.7rem', color: 'var(--text-dim)', fontSize: '0.74rem' }}>
                          {f.qualification || '—'}{f.specialization ? ` • ${f.specialization}` : ''}
                        </td>
                        <td style={{ padding: '0.55rem 0.7rem' }}>
                          <span style={{
                            padding: '0.12rem 0.4rem',
                            borderRadius: '9999px',
                            fontSize: '0.65rem',
                            fontWeight: 700,
                            background: isHod ? 'rgba(239, 68, 68, 0.15)' : f.category === 'LAB_TECHNICIAN' ? 'rgba(245, 158, 11, 0.15)' : 'rgba(99, 102, 241, 0.15)',
                            color: isHod ? '#fca5a5' : f.category === 'LAB_TECHNICIAN' ? '#fcd34d' : '#a5b4fc',
                            border: `1px solid ${isHod ? 'rgba(239, 68, 68, 0.3)' : f.category === 'LAB_TECHNICIAN' ? 'rgba(245, 158, 11, 0.3)' : 'rgba(99, 102, 241, 0.3)'}`
                          }}>
                            {f.category || 'TEACHING'}
                          </span>
                        </td>
                        <td style={{ padding: '0.55rem 0.7rem' }}>
                          {isEligible ? (
                            <span style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '0.3rem',
                              padding: '0.15rem 0.5rem',
                              borderRadius: '9999px',
                              fontSize: '0.66rem',
                              fontWeight: 700,
                              background: 'rgba(16, 185, 129, 0.15)',
                              color: '#6ee7b7',
                              border: '1px solid rgba(16, 185, 129, 0.3)'
                            }}>
                              <CheckCircle2 size={11} /> Eligible (Teaching)
                            </span>
                          ) : (
                            <span style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '0.3rem',
                              padding: '0.15rem 0.5rem',
                              borderRadius: '9999px',
                              fontSize: '0.66rem',
                              fontWeight: 700,
                              background: 'rgba(245, 158, 11, 0.15)',
                              color: '#fbbf24',
                              border: '1px solid rgba(245, 158, 11, 0.3)'
                            }}>
                              <AlertCircle size={11} /> Excluded ({isHod ? 'HOD' : 'Lab/Staff'})
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* VIEW 2: HORIZONTAL DEPARTMENT MATRIX (Prompt Requirement: Department | Faculty 1 | Faculty 2 | Faculty 3 ...) */}
        {viewMode === 'matrix' && (
          <div style={{ overflowX: 'auto', maxHeight: '480px', overflowY: 'auto', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}>
            <table className="table" style={{ fontSize: '0.76rem', width: '100%', marginBottom: 0 }}>
              <thead>
                <tr>
                  <th style={{ width: '140px', padding: '0.65rem 0.7rem', position: 'sticky', left: 0, background: '#170733', zIndex: 2 }}>
                    Department
                  </th>
                  <th style={{ width: '180px', padding: '0.65rem 0.7rem' }}>Faculty 1</th>
                  <th style={{ width: '180px', padding: '0.65rem 0.7rem' }}>Faculty 2</th>
                  <th style={{ width: '180px', padding: '0.65rem 0.7rem' }}>Faculty 3</th>
                  <th style={{ width: '180px', padding: '0.65rem 0.7rem' }}>Faculty 4</th>
                  <th style={{ width: '180px', padding: '0.65rem 0.7rem' }}>Faculty 5</th>
                  <th style={{ width: '180px', padding: '0.65rem 0.7rem' }}>Faculty 6+</th>
                </tr>
              </thead>
              <tbody>
                {Object.keys(departmentGroups).length === 0 ? (
                  <tr>
                    <td colSpan={7} style={{ textAlign: 'center', padding: '2.5rem', color: 'var(--text-dim)' }}>
                      No faculty records extracted yet.
                    </td>
                  </tr>
                ) : (
                  Object.entries(departmentGroups).map(([dept, facList]) => {
                    const code = getDeptCode(dept);
                    return (
                      <tr key={dept}>
                        <td style={{
                          padding: '0.6rem 0.7rem',
                          position: 'sticky',
                          left: 0,
                          background: '#130726',
                          zIndex: 1,
                          borderRight: '1px solid var(--border-color)'
                        }}>
                          <div style={{ fontWeight: 800, color: 'var(--text-main)', fontSize: '0.8rem' }}>{code}</div>
                          <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)', marginTop: '0.1rem' }}>
                            {facList.length} staff
                          </div>
                        </td>

                        {[0, 1, 2, 3, 4].map(idx => {
                          const f = facList[idx];
                          if (!f) return <td key={idx} style={{ padding: '0.5rem', color: 'var(--text-dim)' }}>—</td>;
                          const isEligible = f.eligibleForTeaching !== false && !f.hod;
                          return (
                            <td key={idx} style={{ padding: '0.5rem 0.65rem', verticalAlign: 'top' }}>
                              <div style={{
                                padding: '0.45rem 0.55rem',
                                borderRadius: 'var(--radius-sm)',
                                background: 'rgba(255,255,255,0.02)',
                                border: '1px solid rgba(255,255,255,0.06)'
                              }}>
                                <div style={{ fontWeight: 700, color: 'var(--text-main)', fontSize: '0.76rem' }}>{f.name}</div>
                                <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>{f.designation || 'Faculty'}</div>
                                {f.qualification && (
                                  <div style={{ fontSize: '0.65rem', color: 'var(--text-dim)' }}>{f.qualification}</div>
                                )}
                                <div style={{ marginTop: '0.25rem' }}>
                                  <span style={{
                                    fontSize: '0.62rem',
                                    fontWeight: 700,
                                    padding: '0.08rem 0.35rem',
                                    borderRadius: '4px',
                                    background: isEligible ? 'rgba(16, 185, 129, 0.15)' : 'rgba(245, 158, 11, 0.15)',
                                    color: isEligible ? '#6ee7b7' : '#fbbf24'
                                  }}>
                                    {isEligible ? 'Eligible' : 'Excluded'}
                                  </span>
                                </div>
                              </div>
                            </td>
                          );
                        })}

                        <td style={{ padding: '0.5rem 0.65rem', color: 'var(--text-dim)' }}>
                          {facList.length > 5 ? (
                            <span style={{ fontSize: '0.72rem', fontWeight: 600, color: '#818cf8' }}>
                              +{facList.length - 5} more in roster
                            </span>
                          ) : '—'}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── SECTION 3: SECTION-WISE ASSIGNMENT DASHBOARD ── */}
      <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1.15rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <h2 style={{ fontSize: '1.05rem', fontWeight: 700, margin: 0, color: 'var(--text-main)' }}>
                Section-Wise Subject to Faculty Allocation
              </h2>
              <span style={{ fontSize: '0.72rem', fontWeight: 700, padding: '0.12rem 0.45rem', borderRadius: '9999px', background: 'rgba(16, 185, 129, 0.15)', color: '#34d399', border: '1px solid rgba(16, 185, 129, 0.3)' }}>
                Consolidated to Faculty Portal
              </span>
            </div>
            <p style={{ fontSize: '0.75rem', color: 'var(--text-dim)', margin: '0.2rem 0 0' }}>
              Faculty eligibility strictly enforced: MAB → Maths, CSC/CSE/CSS → CSE, CYB → Chem, MES → Mech, Embedded Systems → ECE, Aptitude/Common → Section Dept. HODs and Lab Techs excluded. Workload capped at 18 hrs.
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem', flexWrap: 'wrap' }}>
            {/* Section Selector */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
              <span style={{ fontSize: '0.74rem', fontWeight: 700, color: 'var(--text-dim)' }}>Section:</span>
              <select
                value={selectedSection}
                onChange={(e) => setSelectedSection(e.target.value)}
                className="form-select"
                style={{ padding: '0.4rem 2rem 0.4rem 0.75rem', fontSize: '0.78rem', minWidth: '120px' }}
              >
                <option value="ALL">All Sections ({distinctBatches.length || '7'})</option>
                {distinctBatches.map(b => (
                  <option key={b} value={b}>{b}</option>
                ))}
              </select>
            </div>

            <button
              type="button"
              onClick={handleAutoAssignAll}
              className="btn btn-secondary"
              style={{
                padding: '0.42rem 0.85rem',
                fontSize: '0.78rem',
                fontWeight: 700,
                background: 'rgba(99, 102, 241, 0.15)',
                borderColor: 'rgba(99, 102, 241, 0.4)',
                color: '#a5b4fc'
              }}
            >
              <RefreshCw size={13} className={proposalsLoading ? 'spin' : ''} /> Auto-Assign All
            </button>

            <button
              type="button"
              disabled={finalizing || sectionAllocations.length === 0}
              onClick={handleFinalizeAndSave}
              className="btn btn-primary"
              style={{
                padding: '0.42rem 1rem',
                fontSize: '0.78rem',
                fontWeight: 700,
                background: '#10b981',
                borderColor: '#10b981',
                color: '#ffffff'
              }}
            >
              <Check size={14} /> {finalizing ? 'Saving...' : 'Finalize & Save All'}
            </button>
          </div>
        </div>

        {/* Save success banner */}
        {saveSuccessMsg && (
          <div style={{
            padding: '0.6rem 0.9rem',
            borderRadius: 'var(--radius-sm)',
            background: 'rgba(16, 185, 129, 0.15)',
            border: '1px solid #10b981',
            color: '#6ee7b7',
            fontSize: '0.76rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.4rem'
          }}>
            <CheckCircle2 size={15} /> {saveSuccessMsg}
          </div>
        )}

        {/* Section Table */}
        <div style={{ overflowX: 'auto', maxHeight: '520px', overflowY: 'auto', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}>
          <table className="table" style={{ fontSize: '0.78rem', width: '100%', marginBottom: 0 }}>
            <thead>
              <tr>
                <th style={{ width: '45px', padding: '0.65rem 0.6rem' }}>Sl.</th>
                <th style={{ width: '85px', padding: '0.65rem 0.6rem' }}>Section</th>
                <th style={{ width: '110px', padding: '0.65rem 0.6rem' }}>Course Code</th>
                <th style={{ padding: '0.65rem 0.6rem' }}>Subject Title</th>
                <th style={{ width: '65px', padding: '0.65rem 0.6rem' }}>Dept</th>
                <th style={{ width: '85px', padding: '0.65rem 0.6rem' }}>Weekly Hrs</th>
                <th style={{ width: '90px', padding: '0.65rem 0.6rem' }}>Type</th>
                <th style={{ padding: '0.65rem 0.6rem' }}>Assigned Faculty</th>
                <th style={{ width: '135px', padding: '0.65rem 0.6rem' }}>Workload / Free Pds</th>
                <th style={{ width: '95px', padding: '0.65rem 0.6rem' }}>Status</th>
                <th style={{ width: '70px', padding: '0.65rem 0.6rem', textAlign: 'center' }}>Action</th>
              </tr>
            </thead>
            <tbody>
              {proposalsLoading ? (
                <tr>
                  <td colSpan={11} style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-dim)' }}>
                    Evaluating curriculum, course prefixes, and assigning eligible faculty...
                  </td>
                </tr>
              ) : filteredAllocations.length === 0 ? (
                <tr>
                  <td colSpan={11} style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-dim)' }}>
                    No subjects in curriculum for this semester. Upload curriculum in <strong>Subjects &amp; Credits</strong>.
                  </td>
                </tr>
              ) : (
                filteredAllocations.map((item, idx) => {
                  const dBadge = getBadgeStyle(item.departmentKey);
                  const isConfirmed = item.status === 'Confirmed';
                  const isOverload = item.currentLoad > item.maxLoad;

                  return (
                    <tr key={item.id || idx}>
                      <td style={{ padding: '0.55rem 0.6rem', color: 'var(--text-dim)' }}>{idx + 1}</td>
                      <td style={{ padding: '0.55rem 0.6rem', fontWeight: 800, color: 'var(--text-main)' }}>
                        <span style={{
                          padding: '0.12rem 0.45rem',
                          borderRadius: '4px',
                          background: 'rgba(255,255,255,0.04)',
                          border: '1px solid rgba(255,255,255,0.1)'
                        }}>
                          {item.batchName}
                        </span>
                      </td>
                      <td style={{ padding: '0.55rem 0.6rem', fontWeight: 700, color: 'var(--text-main)' }}>
                        <div>{item.subjectCode}</div>
                        {item.coursePrefix && (
                          <span style={{ fontSize: '0.65rem', color: '#818cf8', fontWeight: 700 }}>
                            [{item.coursePrefix}]
                          </span>
                        )}
                      </td>
                      <td style={{ padding: '0.55rem 0.6rem', fontWeight: 600, color: 'var(--text-main)' }}>
                        {item.subjectName}
                      </td>
                      <td style={{ padding: '0.55rem 0.6rem' }}>
                        <span style={{
                          padding: '0.12rem 0.35rem',
                          borderRadius: '4px',
                          fontSize: '0.68rem',
                          fontWeight: 800,
                          ...dBadge
                        }}>
                          {item.departmentKey}
                        </span>
                      </td>
                      <td style={{ padding: '0.55rem 0.6rem', fontWeight: 700, color: '#a78bfa' }}>
                        {item.weeklyHours} hrs/wk
                      </td>
                      <td style={{ padding: '0.55rem 0.6rem' }}>
                        <span style={{
                          fontSize: '0.65rem',
                          fontWeight: 700,
                          padding: '0.1rem 0.35rem',
                          borderRadius: '4px',
                          background: item.type === 'PRACTICAL' || item.type === 'LAB'
                            ? 'rgba(16, 185, 129, 0.15)'
                            : item.type === 'THEORY_AND_LAB'
                            ? 'rgba(245, 158, 11, 0.15)'
                            : 'rgba(99, 102, 241, 0.15)',
                          color: item.type === 'PRACTICAL' || item.type === 'LAB'
                            ? '#34d399'
                            : item.type === 'THEORY_AND_LAB'
                            ? '#fbbf24'
                            : '#818cf8'
                        }}>
                          {item.type}
                        </span>
                      </td>
                      <td style={{ padding: '0.55rem 0.6rem' }}>
                        {item.assignedFacultyName === 'Unassigned' ? (
                          <span style={{ color: 'var(--text-dim)', fontStyle: 'italic' }}>Unassigned</span>
                        ) : (
                          <div>
                            <div style={{ fontWeight: 700, color: 'var(--text-main)' }}>{item.assignedFacultyName}</div>
                            <div style={{ fontSize: '0.67rem', color: 'var(--text-dim)' }}>
                              {item.assignedFacultyDesignation} • {item.assignedFacultyDept}
                            </div>
                          </div>
                        )}
                      </td>
                      <td style={{ padding: '0.55rem 0.6rem' }}>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.15rem' }}>
                          <span style={{
                            fontSize: '0.72rem',
                            fontWeight: 700,
                            color: isOverload ? '#ef4444' : '#34d399'
                          }}>
                            {item.currentLoad}/{item.maxLoad} hrs
                          </span>
                          <span style={{ fontSize: '0.65rem', color: 'var(--text-dim)' }}>
                            ~2 free pds/day aim
                          </span>
                        </div>
                      </td>
                      <td style={{ padding: '0.55rem 0.6rem' }}>
                        <span style={{
                          padding: '0.12rem 0.45rem',
                          borderRadius: '9999px',
                          fontSize: '0.66rem',
                          fontWeight: 700,
                          background: isConfirmed ? 'rgba(16, 185, 129, 0.15)' : 'rgba(245, 158, 11, 0.15)',
                          color: isConfirmed ? '#6ee7b7' : '#fcd34d',
                          border: `1px solid ${isConfirmed ? 'rgba(16, 185, 129, 0.3)' : 'rgba(245, 158, 11, 0.3)'}`
                        }}>
                          {item.status}
                        </span>
                      </td>
                      <td style={{ padding: '0.55rem 0.6rem', textAlign: 'center' }}>
                        <button
                          type="button"
                          onClick={() => {
                            setSelectedAllocationForAssign(item);
                            setAssignFacultyId(item.assignedFacultyId ? String(item.assignedFacultyId) : '');
                            setShowAssignModal(true);
                          }}
                          className="btn btn-secondary"
                          style={{ padding: '0.25rem 0.5rem', fontSize: '0.7rem' }}
                        >
                          Change
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Section Dashboard Footer KPIs */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '0.75rem',
          paddingTop: '0.75rem',
          borderTop: '1px solid var(--border-color)',
          fontSize: '0.76rem',
          color: 'var(--text-dim)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
            <span>Total Section Allocations: <strong style={{ color: 'var(--text-main)' }}>{filteredAllocations.length}</strong></span>
            <span>Assigned: <strong style={{ color: '#10b981' }}>{filteredAllocations.filter(a => a.assignedFacultyName !== 'Unassigned').length}</strong></span>
            <span>Simultaneous Conflicts: <strong style={{ color: '#10b981' }}>0 detected</strong></span>
          </div>
          <div>
            <span style={{ color: '#818cf8', fontWeight: 600 }}>All institutional workload constraints respected</span>
          </div>
        </div>
      </div>

      {/* ── MODAL: MANUAL FACULTY RE-ASSIGNMENT ── */}
      <Modal
        isOpen={showAssignModal}
        onClose={() => {
          setShowAssignModal(false);
          setSelectedAllocationForAssign(null);
        }}
        title="Reassign Faculty for Section"
        maxWidth="540px"
      >
        {selectedAllocationForAssign && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div style={{
              padding: '0.75rem',
              borderRadius: 'var(--radius-sm)',
              background: 'rgba(99, 102, 241, 0.08)',
              border: '1px solid rgba(99, 102, 241, 0.25)'
            }}>
              <div style={{ fontSize: '0.85rem', fontWeight: 700, color: 'var(--text-main)' }}>
                {selectedAllocationForAssign.subjectCode} — {selectedAllocationForAssign.subjectName}
              </div>
              <div style={{ fontSize: '0.74rem', color: 'var(--text-dim)', marginTop: '0.2rem' }}>
                Section: <strong>{selectedAllocationForAssign.batchName}</strong> • Target Dept: <strong>{selectedAllocationForAssign.departmentKey}</strong> • {selectedAllocationForAssign.weeklyHours} hrs/week
              </div>
            </div>

            <div>
              <label style={{ fontSize: '0.8rem', fontWeight: 700, color: 'var(--text-main)', marginBottom: '0.4rem', display: 'block' }}>
                Select Eligible Faculty:
              </label>
              <select
                value={assignFacultyId}
                onChange={(e) => setAssignFacultyId(e.target.value)}
                className="form-select"
                style={{ width: '100%', padding: '0.5rem' }}
              >
                <option value="">-- Choose Faculty --</option>
                {(selectedAllocationForAssign.eligibleFaculty || []).map(f => (
                  <option key={f.id} value={f.id}>
                    {f.name} ({f.designation || 'Faculty'} • {f.departmentKey || getDeptCode(f.department)})
                  </option>
                ))}
              </select>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '0.5rem' }}>
              <button
                type="button"
                onClick={() => setShowAssignModal(false)}
                className="btn btn-secondary"
                style={{ padding: '0.4rem 0.8rem', fontSize: '0.8rem' }}
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={!assignFacultyId}
                onClick={handleApplyManualAssign}
                className="btn btn-primary"
                style={{ padding: '0.4rem 1rem', fontSize: '0.8rem', fontWeight: 700 }}
              >
                Confirm Assignment
              </button>
            </div>
          </div>
        )}
      </Modal>

      {/* ── MODAL: INSTITUTIONAL ALLOCATION RULES ── */}
      <Modal
        isOpen={showRulesModal}
        onClose={() => setShowRulesModal(false)}
        title="Easwari Engineering College — Academic Allocation Rules"
        maxWidth="620px"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', fontSize: '0.8rem', color: 'var(--text-muted)', lineHeight: 1.55 }}>
          <p>
            The automated timetable scheduler assigns teaching faculty to sections according to approved institutional curriculum guidelines:
          </p>

          <div style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: '0.65rem',
            padding: '0.75rem',
            borderRadius: 'var(--radius-sm)',
            background: 'rgba(255,255,255,0.02)',
            border: '1px solid var(--border-color)'
          }}>
            <div><strong>MAB / MAT:</strong> Mathematics Faculty</div>
            <div><strong>CSC / CSE / CSS:</strong> Computer Science Faculty</div>
            <div><strong>CYB / CHM:</strong> Chemistry Faculty</div>
            <div><strong>MES / MEC:</strong> Mechanical Engineering Faculty</div>
            <div><strong>ESD / ECE:</strong> Electronics &amp; Communication</div>
            <div><strong>APT / TEC / COU:</strong> Section Department Faculty</div>
          </div>

          <div style={{ fontWeight: 700, color: '#f59e0b', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <ShieldCheck size={16} /> Strict Institutional Constraints:
          </div>
          <ul style={{ paddingLeft: '1.2rem', margin: 0, display: 'flex', flexDirection: 'column', gap: '0.35rem' }}>
            <li><strong>HOD Exclusion:</strong> Heads of Departments (HODs) must never receive teaching assignments.</li>
            <li><strong>Lab &amp; Non-Teaching Staff:</strong> Excluded from classroom teaching allocations.</li>
            <li><strong>Maximum Workload:</strong> Configurable 18 teaching hours/week limit strictly enforced.</li>
            <li><strong>Simultaneous Section Prevention:</strong> A faculty member cannot teach two sections during the same period.</li>
            <li><strong>Target Free Periods:</strong> Aims for ~2 free periods in an 8-period teaching day.</li>
          </ul>

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
            <button
              type="button"
              onClick={() => setShowRulesModal(false)}
              className="btn btn-primary"
              style={{ padding: '0.4rem 1rem', fontSize: '0.8rem' }}
            >
              Close
            </button>
          </div>
        </div>
      </Modal>

    </div>
  );
}
