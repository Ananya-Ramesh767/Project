import React, { useState, useEffect } from 'react';
import {
  Users, Plus, Edit2, Trash2, Calendar, Clock, Check, X, Star,
  AlertCircle, Upload, FileText, Sparkles, ChevronDown, ChevronUp,
  UserCheck, AlertTriangle, BookOpen, UserPlus, ShieldAlert
} from 'lucide-react';
import { FACULTY_DEPARTMENTS, MAIN_INSTITUTIONAL_PROGRAMMES } from '../utils/programmes';
// Faculty Master uses FACULTY_DEPARTMENTS (all 21 departments including Math, Physics, Chemistry, English)
// Other modules use MAIN_INSTITUTIONAL_PROGRAMMES (only the 17 main departments)
const INSTITUTIONAL_PROGRAMMES = FACULTY_DEPARTMENTS;
import { SEMESTER_OPTIONS, getStoredAcademicContext, setStoredAcademicContext } from '../utils/academicContext';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
const PERIODS = [1, 2, 3, 4, 5, 6, 7, 8];

export default function FacultyManagementPage({ user }) {
  // Navigation tabs
  const [activeTab, setActiveTab] = useState('assignment'); // 'assignment' | 'roster'

  const [faculty, setFaculty] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [workloads, setWorkloads] = useState([]);
  const [showModal, setShowModal] = useState(false);
  const [editId, setEditId] = useState(null);

  // Filter / Department synced with academicContext
  const initialContext = getStoredAcademicContext();
  const [filterDept, setFilterDept] = useState(() => initialContext.department || INSTITUTIONAL_PROGRAMMES[4]);
  const [filterSemester, setFilterSemester] = useState(() => initialContext.semester || 'ALL');

  // Auto-Assign State
  const [autoAssigning, setAutoAssigning] = useState(false);
  const [autoAssignMsg, setAutoAssignMsg] = useState(null);

  // Subject-Wise Assignment Proposal state
  const [proposals, setProposals] = useState([]);
  const [allInstitutionFaculty, setAllInstitutionFaculty] = useState([]);
  const [deptBatches, setDeptBatches] = useState([]);
  const [proposalsLoading, setProposalsLoading] = useState(false);
  const [proposalError, setProposalError] = useState('');

  // Local assignment mapping state: subjectId -> { facultyIds: [], sectionMap: { batchId: facultyId }, requiredCount: N }
  const [assignmentsState, setAssignmentsState] = useState({});
  // Expand/collapse sections per subject: subjectId -> boolean
  const [expandedSections, setExpandedSections] = useState({});
  // Manual faculty entry per subject: subjectId -> { open: boolean, name: '', employeeId: '', email: '' }
  const [manualEntryState, setManualEntryState] = useState({});

  const [savingAssignments, setSavingAssignments] = useState(false);
  const [assignmentSuccessMsg, setAssignmentSuccessMsg] = useState('');

  // PDF Upload state
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [uploadFile, setUploadFile] = useState(null);
  const [uploadDept, setUploadDept] = useState(() => initialContext.department || INSTITUTIONAL_PROGRAMMES[4]);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [parsedFaculty, setParsedFaculty] = useState([]);
  const [uploadError, setUploadError] = useState('');
  const [uploadSuccess, setUploadSuccess] = useState('');

  // Availability modal
  const [showAvailModal, setShowAvailModal] = useState(false);
  const [activeFacultyForAvail, setActiveFacultyForAvail] = useState(null);
  const [availMatrix, setAvailMatrix] = useState({});
  const [availSaving, setAvailSaving] = useState(false);
  const [availSuccess, setAvailSuccess] = useState('');

  const [form, setForm] = useState({
    employeeId: '',
    name: '',
    email: '',
    department: initialContext.department || INSTITUTIONAL_PROGRAMMES[4],
    designation: 'Assistant Professor',
    specialization: '',
    maxWeeklyLoad: 18,
    hod: false,
    subjectIds: []
  });

  const [error, setError] = useState('');

  useEffect(() => {
    fetchFaculty();
    fetchSubjects();
    fetchWorkloads();
  }, []);

  useEffect(() => {
    if (activeTab === 'assignment') {
      fetchProposals(filterDept);
    }
  }, [activeTab, filterDept, filterSemester]);

  const fetchFaculty = async () => {
    try {
      const res = await fetch('/api/admin/faculty', { headers: { 'Authorization': `Bearer ${user.token}` } });
      if (res.ok) setFaculty(await res.json());
    } catch (e) { console.error(e); }
  };

  const fetchSubjects = async () => {
    try {
      const res = await fetch('/api/admin/subjects', { headers: { 'Authorization': `Bearer ${user.token}` } });
      if (res.ok) setSubjects(await res.json());
    } catch (e) { console.error(e); }
  };

  const fetchWorkloads = async () => {
    try {
      const res = await fetch('/api/admin/faculty/workload', { headers: { 'Authorization': `Bearer ${user.token}` } });
      if (res.ok) setWorkloads(await res.json());
    } catch (e) { console.error(e); }
  };

  // Fetch subject-wise proposals for current department
  const fetchProposals = async (dept) => {
    setProposalsLoading(true);
    setProposalError('');
    try {
      const semParam = filterSemester !== 'ALL' ? `&semester=${filterSemester}` : '';
      const deptParam = dept !== 'ALL' ? `?department=${encodeURIComponent(dept)}${semParam}` : (semParam ? `?${semParam.substring(1)}` : '');
      const res = await fetch(`/api/admin/faculty/propose-assignment${deptParam}`, {
        headers: { 'Authorization': `Bearer ${user.token}` }
      });
      if (!res.ok) throw new Error('Failed to load subject proposals for department');
      const data = await res.json();
      setProposals(data.proposals || []);
      setAllInstitutionFaculty(data.allFaculty || []);
      setDeptBatches(data.batches || []);

      // Initialize assignment state from proposals
      const initialMap = {};
      (data.proposals || []).forEach(p => {
        // Pre-select suggested faculty or existing assigned faculty
        let selectedFids = [];
        if (p.existingSectionFaculty && Object.keys(p.existingSectionFaculty).length > 0) {
          selectedFids = [...new Set(Object.values(p.existingSectionFaculty))];
        }
        // If existing has fewer faculty than recommended and suggestions exist, merge suggestions
        if (selectedFids.length < (p.requiredFacultyCount || 1) && p.suggestedFacultyIds && p.suggestedFacultyIds.length > 0) {
          p.suggestedFacultyIds.slice(0, p.requiredFacultyCount || 1).forEach(fid => {
            if (!selectedFids.includes(fid)) selectedFids.push(fid);
          });
        }
        if (selectedFids.length === 0 && p.suggestedFacultyIds && p.suggestedFacultyIds.length > 0) {
          selectedFids = p.suggestedFacultyIds.slice(0, p.requiredFacultyCount || 1);
        }

        const sectionMap = {};
        const relevantBatches = (data.batches || []).filter(b => p.semester == null || b.semester == null || b.semester === p.semester);
        const targetBatches = relevantBatches.length > 0 ? relevantBatches : (data.batches || []);

        targetBatches.forEach((b, idx) => {
          if (p.existingSectionFaculty && p.existingSectionFaculty[b.id]) {
            sectionMap[b.id] = p.existingSectionFaculty[b.id];
          } else if (p.suggestedSectionFaculty && p.suggestedSectionFaculty[b.id]) {
            sectionMap[b.id] = p.suggestedSectionFaculty[b.id];
          } else if (selectedFids.length > 0) {
            sectionMap[b.id] = selectedFids[idx % selectedFids.length];
          }
        });

        initialMap[p.subjectId] = {
          facultyIds: selectedFids,
          requiredCount: p.requiredFacultyCount || 1,
          sectionMap: sectionMap
        };
      });
      setAssignmentsState(initialMap);
    } catch (err) {
      setProposalError(err.message);
    } finally {
      setProposalsLoading(false);
    }
  };

  // Auto-distribute sections for a subject across its selected faculty
  const handleAutoDistributeSubject = (subjectId) => {
    const p = proposals.find(pr => pr.subjectId === subjectId);
    if (!p) return;
    const current = assignmentsState[subjectId] || { facultyIds: [], requiredCount: 1, sectionMap: {} };
    let fids = current.facultyIds;
    if (fids.length === 0 && p.suggestedFacultyIds && p.suggestedFacultyIds.length > 0) {
      fids = p.suggestedFacultyIds.slice(0, p.requiredFacultyCount || 1);
    }
    if (fids.length === 0) return;

    const relevantBatches = deptBatches.filter(b => p.semester == null || b.semester == null || b.semester === p.semester);
    const targetBatches = relevantBatches.length > 0 ? relevantBatches : deptBatches;

    const newSectionMap = {};
    targetBatches.forEach((b, idx) => {
      newSectionMap[b.id] = fids[idx % fids.length];
    });

    setAssignmentsState(prev => ({
      ...prev,
      [subjectId]: {
        ...current,
        facultyIds: fids,
        sectionMap: newSectionMap
      }
    }));
  };

  // Auto-distribute sections across all subjects
  const handleAutoDistributeAllSubjects = () => {
    proposals.forEach(p => {
      handleAutoDistributeSubject(p.subjectId);
    });
  };

  // Toggle faculty selection for a subject
  const handleToggleFacultyForSubject = (subjectId, facultyId) => {
    setAssignmentsState(prev => {
      const current = prev[subjectId] || { facultyIds: [], requiredCount: 1, sectionMap: {} };
      const exists = current.facultyIds.includes(facultyId);
      const newFids = exists
        ? current.facultyIds.filter(id => id !== facultyId)
        : [...current.facultyIds, facultyId];

      const p = proposals.find(pr => pr.subjectId === subjectId);
      const relevantBatches = deptBatches.filter(b => !p || p.semester == null || b.semester == null || b.semester === p.semester);
      const targetBatches = relevantBatches.length > 0 ? relevantBatches : deptBatches;

      // Re-distribute across sections evenly among all selected faculty
      const updatedSectionMap = { ...current.sectionMap };
      if (newFids.length > 0) {
        targetBatches.forEach((b, idx) => {
          updatedSectionMap[b.id] = newFids[idx % newFids.length];
        });
      } else {
        targetBatches.forEach(b => {
          delete updatedSectionMap[b.id];
        });
      }

      return {
        ...prev,
        [subjectId]: {
          ...current,
          facultyIds: newFids,
          sectionMap: updatedSectionMap
        }
      };
    });
  };

  // Adjust required faculty count for a subject
  const handleSetRequiredCount = (subjectId, count) => {
    const safeCount = Math.max(1, count);
    setAssignmentsState(prev => ({
      ...prev,
      [subjectId]: {
        ...(prev[subjectId] || { facultyIds: [], sectionMap: {} }),
        requiredCount: safeCount
      }
    }));
  };

  // Change faculty for an individual section
  const handleSectionFacultyChange = (subjectId, batchId, facultyId) => {
    setAssignmentsState(prev => {
      const current = prev[subjectId] || { facultyIds: [], requiredCount: 1, sectionMap: {} };
      return {
        ...prev,
        [subjectId]: {
          ...current,
          sectionMap: {
            ...current.sectionMap,
            [batchId]: Number(facultyId)
          }
        }
      };
    });
  };

  // Manual faculty quick add for a specific subject
  const handleAddManualFaculty = async (subjectId) => {
    const input = manualEntryState[subjectId];
    if (!input || !input.name || !input.name.trim()) return;

    try {
      const payload = {
        name: input.name.trim(),
        employeeId: input.employeeId ? input.employeeId.trim() : `FAC-${Math.floor(1000 + Math.random() * 9000)}`,
        email: input.email ? input.email.trim() : `${input.name.trim().toLowerCase().replace(/[^a-z0-9]/g, '.')}@odysseus.edu`,
        department: filterDept !== 'ALL' ? filterDept : INSTITUTIONAL_PROGRAMMES[4]
      };

      const res = await fetch('/api/admin/faculty/quick-create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${user.token}` },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to create faculty');
      }

      const newFaculty = await res.json();
      // Add to faculty list
      setAllInstitutionFaculty(prev => [...prev, newFaculty]);
      fetchFaculty();

      // Automatically select this faculty for this subject
      handleToggleFacultyForSubject(subjectId, newFaculty.id);

      // Close manual input
      setManualEntryState(prev => ({
        ...prev,
        [subjectId]: { open: false, name: '', employeeId: '', email: '' }
      }));
    } catch (e) {
      alert(e.message);
    }
  };

  // Confirm and Save All Subject-Faculty Mappings
  const handleConfirmAllAssignments = async () => {
    setSavingAssignments(true);
    setAssignmentSuccessMsg('');
    try {
      const assignmentsPayload = proposals.map(p => {
        const state = assignmentsState[p.subjectId] || {};
        const sectionAssignments = [];
        if (state.sectionMap) {
          Object.entries(state.sectionMap).forEach(([bId, fId]) => {
            if (fId) sectionAssignments.push({ batchId: Number(bId), facultyId: Number(fId) });
          });
        }
        return {
          subjectId: p.subjectId,
          requiredFacultyCount: state.requiredCount || 1,
          facultyIds: state.facultyIds || [],
          sectionAssignments: sectionAssignments
        };
      }).filter(item => item.facultyIds.length > 0 || (item.sectionAssignments && item.sectionAssignments.length > 0));

      if (assignmentsPayload.length === 0) {
        throw new Error('Please select at least one faculty member before confirming assignments.');
      }

      const res = await fetch('/api/admin/faculty/confirm-assignment', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${user.token}` },
        body: JSON.stringify({
          department: filterDept !== 'ALL' ? filterDept : null,
          academicYear: '2026-2027',
          assignments: assignmentsPayload
        })
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to confirm assignments');
      }

      const data = await res.json();
      setAssignmentSuccessMsg(`✅ ${data.message} (${data.offeringsCreated + data.offeringsUpdated} section offerings updated across ${data.totalBatches} batches)`);
      fetchFaculty();
      fetchWorkloads();
      fetchProposals(filterDept);
    } catch (err) {
      alert(err.message);
    } finally {
      setSavingAssignments(false);
    }
  };

  const handleOpenAvailability = async (f) => {
    setActiveFacultyForAvail(f);
    setAvailSuccess('');
    const matrix = {};
    DAYS.forEach(d => {
      PERIODS.forEach(p => {
        matrix[`${d}-${p}`] = { available: true, preferred: false };
      });
    });

    try {
      const res = await fetch(`/api/admin/faculty/${f.id}/availability`, {
        headers: { 'Authorization': `Bearer ${user.token}` }
      });
      if (res.ok) {
        const slots = await res.json();
        slots.forEach(s => {
          matrix[`${s.dayOfWeek}-${s.periodNumber}`] = {
            available: s.available ?? true,
            preferred: s.preferred ?? false
          };
        });
      }
    } catch (e) { console.error(e); }

    setAvailMatrix(matrix);
    setShowAvailModal(true);
  };

  const toggleSlot = (day, period) => {
    const key = `${day}-${period}`;
    const current = availMatrix[key] || { available: true, preferred: false };
    let next;
    if (current.available && !current.preferred) {
      next = { available: true, preferred: true };
    } else if (current.available && current.preferred) {
      next = { available: false, preferred: false };
    } else {
      next = { available: true, preferred: false };
    }
    setAvailMatrix(prev => ({ ...prev, [key]: next }));
  };

  const handleSaveAvailability = async () => {
    if (!activeFacultyForAvail) return;
    setAvailSaving(true);
    setAvailSuccess('');

    const payload = [];
    DAYS.forEach(day => {
      PERIODS.forEach(period => {
        const slot = availMatrix[`${day}-${period}`];
        if (slot) {
          payload.push({
            dayOfWeek: day,
            periodNumber: period,
            available: slot.available,
            preferred: slot.preferred
          });
        }
      });
    });

    try {
      const res = await fetch(`/api/admin/faculty/${activeFacultyForAvail.id}/availability`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${user.token}` },
        body: JSON.stringify(payload)
      });
      if (res.ok) {
        setAvailSuccess('Availability constraints saved successfully!');
        setTimeout(() => {
          setShowAvailModal(false);
          setAvailSuccess('');
        }, 1200);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setAvailSaving(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    const url = editId ? `/api/admin/faculty/${editId}` : '/api/admin/faculty';
    const method = editId ? 'PUT' : 'POST';

    try {
      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${user.token}` },
        body: JSON.stringify(form)
      });
      if (!res.ok) {
        const errData = await res.json();
        throw new Error(errData.error || 'Failed to save faculty');
      }
      setShowModal(false);
      fetchFaculty();
    } catch (err) {
      setError(err.message);
    }
  };

  const handleSubjectToggle = (sId) => {
    setForm(prev => {
      const exists = prev.subjectIds.includes(sId);
      return {
        ...prev,
        subjectIds: exists ? prev.subjectIds.filter(id => id !== sId) : [...prev.subjectIds, sId]
      };
    });
  };

  const handleEdit = (f) => {
    setEditId(f.id);
    setForm({
      employeeId: f.employeeId,
      name: f.name,
      email: f.email,
      department: f.department,
      designation: f.designation || 'Assistant Professor',
      specialization: f.specialization || '',
      maxWeeklyLoad: f.maxWeeklyLoad || 18,
      hod: Boolean(f.hod || (f.designation && f.designation.toLowerCase().includes('hod'))),
      subjectIds: f.eligibleSubjects ? f.eligibleSubjects.map(s => s.id) : []
    });
    setShowModal(true);
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Deactivate this faculty member?')) return;
    try {
      await fetch(`/api/admin/faculty/${id}`, { method: 'DELETE', headers: { 'Authorization': `Bearer ${user.token}` } });
      fetchFaculty();
    } catch (e) { console.error(e); }
  };

  const handleUploadParse = async (e) => {
    e.preventDefault();
    if (!uploadFile) {
      setUploadError('Please select a PDF file to upload.');
      return;
    }
    setUploadError('');
    setUploadSuccess('');
    setUploadLoading(true);

    try {
      const formData = new FormData();
      formData.append('file', uploadFile);
      formData.append('department', uploadDept);

      const res = await fetch('/api/admin/faculty/parse-document', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${user.token}` },
        body: formData
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.error || 'Failed to extract faculty list from PDF');
      }

      const data = await res.json();
      if (!data.preview || data.preview.length === 0) {
        setUploadError('Unable to automatically extract faculty records from this document. You can add them manually.');
        setParsedFaculty([]);
      } else {
        const enriched = (data.preview || []).map(f => ({
          ...f,
          hod: Boolean(f.hod || (f.designation && f.designation.toLowerCase().includes('hod')))
        }));
        setParsedFaculty(enriched);
        setUploadSuccess(`Extracted ${enriched.length} faculty members! Review and edit details below before saving.`);
      }
    } catch (err) {
      setUploadError(err.message);
    } finally {
      setUploadLoading(false);
    }
  };

  const handleBatchSaveFaculty = async () => {
    if (parsedFaculty.length === 0) return;
    setUploadLoading(true);
    setUploadError('');
    setUploadSuccess('');

    try {
      const payload = parsedFaculty.map(f => ({
        ...f,
        hod: Boolean(f.hod || (f.designation && f.designation.toLowerCase().includes('hod')))
      }));

      const res = await fetch('/api/admin/faculty/batch-save', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${user.token}`
        },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.error || 'Failed to save faculty records');
      }

      const result = await res.json();
      setShowUploadModal(false);
      setParsedFaculty([]);
      setUploadFile(null);
      setAssignmentSuccessMsg(`✅ Successfully imported ${result.savedCount} faculty members into Faculty Master!`);
      setFilterDept(uploadDept);
      setStoredAcademicContext({ department: uploadDept });

      fetchFaculty();
      fetchWorkloads();
      fetchProposals(uploadDept);
    } catch (err) {
      setUploadError(err.message);
    } finally {
      setUploadLoading(false);
    }
  };

  const filteredFaculty = faculty.filter(f => {
    if (filterDept === 'ALL') return true;
    return comMatches(f.department, filterDept);
  });

  function comMatches(d1, d2) {
    if (!d1 || !d2) return false;
    const a = d1.toLowerCase().trim();
    const b = d2.toLowerCase().trim();
    if (a === b) return true;
    if ((a.includes('computer') && b.includes('computer')) ||
        (a.includes('cse') && b.includes('computer')) ||
        (b.includes('cse') && a.includes('computer'))) return true;
    return false;
  }

  const totalAllocated = workloads.reduce((sum, w) => sum + (w.allocatedHours || 0), 0);
  const totalMax = faculty.reduce((sum, f) => sum + (f.maxWeeklyLoad || 0), 0);
  const avgLoad = faculty.length > 0 ? (totalAllocated / faculty.length).toFixed(1) : 0;

  return (
    <div>
      {/* Page Header */}
      <div className="page-header" style={{ marginBottom: '1.25rem' }}>
        <div>
          <h1 className="page-title">Faculty Master & Subject Assignment</h1>
          <p className="page-subtitle">
            Upload department faculty rosters, determine subject-specific eligibility, configure required staff counts, and confirm section assignments.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button
            className="btn btn-secondary"
            onClick={() => { setShowUploadModal(true); setUploadError(''); setUploadSuccess(''); }}
          >
            <Upload size={18} /> Upload Faculty PDF
          </button>
          <button
            className="btn btn-primary"
            onClick={() => {
              setEditId(null);
              setForm({
                employeeId: `FAC-${Math.floor(1000 + Math.random() * 9000)}`,
                name: '',
                email: '',
                department: filterDept !== 'ALL' ? filterDept : INSTITUTIONAL_PROGRAMMES[4],
                designation: 'Assistant Professor',
                specialization: '',
                maxWeeklyLoad: 18,
                subjectIds: []
              });
              setShowModal(true);
            }}
          >
            <Plus size={18} /> Add Faculty Member
          </button>
        </div>
      </div>

      {/* Global Success / Notice Banner */}
      {assignmentSuccessMsg && (
        <div style={{
          padding: '0.85rem 1.25rem',
          borderRadius: '8px',
          marginBottom: '1.25rem',
          background: 'rgba(16, 185, 129, 0.15)',
          border: '1px solid #10b981',
          color: '#6ee7b7',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          fontSize: '0.9rem'
        }}>
          <span>{assignmentSuccessMsg}</span>
          <button onClick={() => setAssignmentSuccessMsg('')} style={{ background: 'transparent', border: 'none', color: 'inherit', cursor: 'pointer', fontSize: '1.1rem' }}>&times;</button>
        </div>
      )}

      {/* Top Department & View Controls */}
      <div className="glass-panel" style={{ marginBottom: '1.5rem', padding: '1rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <span style={{ fontSize: '0.85rem', fontWeight: 700 }}>Department:</span>
            <select
              className="form-select"
              style={{ padding: '0.35rem 0.75rem', fontSize: '0.85rem', minWidth: '240px' }}
              value={filterDept}
              onChange={e => {
                setFilterDept(e.target.value);
                setUploadDept(e.target.value);
              }}
            >
              {INSTITUTIONAL_PROGRAMMES.map(prog => (
                <option key={prog} value={prog}>{prog}</option>
              ))}
            </select>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <span style={{ fontSize: '0.85rem', fontWeight: 700 }}>Semester:</span>
            <select
              className="form-select"
              style={{ padding: '0.35rem 0.75rem', fontSize: '0.85rem', width: 'auto' }}
              value={filterSemester}
              onChange={e => setFilterSemester(e.target.value)}
            >
              <option value="ALL">All Semesters</option>
              {[1, 2, 3, 4, 5, 6, 7, 8].map(s => (
                <option key={s} value={s}>Semester {s}</option>
              ))}
            </select>
          </div>
        </div>

        {/* View Switcher Tabs */}
        <div style={{ display: 'flex', background: 'rgba(0,0,0,0.3)', borderRadius: '8px', padding: '0.25rem', gap: '0.25rem' }}>
          <button
            onClick={() => setActiveTab('assignment')}
            style={{
              padding: '0.45rem 1rem',
              borderRadius: '6px',
              fontSize: '0.85rem',
              fontWeight: 700,
              border: 'none',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '0.4rem',
              background: activeTab === 'assignment' ? 'var(--accent-primary, #6366f1)' : 'transparent',
              color: activeTab === 'assignment' ? '#ffffff' : 'var(--text-muted)'
            }}
          >
            <BookOpen size={16} /> Subject-Wise Assignment & Confirmation
          </button>
          <button
            onClick={() => setActiveTab('roster')}
            style={{
              padding: '0.45rem 1rem',
              borderRadius: '6px',
              fontSize: '0.85rem',
              fontWeight: 700,
              border: 'none',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '0.4rem',
              background: activeTab === 'roster' ? 'var(--accent-primary, #6366f1)' : 'transparent',
              color: activeTab === 'roster' ? '#ffffff' : 'var(--text-muted)'
            }}
          >
            <Users size={16} /> Faculty Roster & Workload ({faculty.length})
          </button>
        </div>
      </div>

      {/* ========================================================================= */}
      {/* TAB 1: SUBJECT-WISE FACULTY ASSIGNMENT & ADMIN CONFIRMATION               */}
      {/* ========================================================================= */}
      {activeTab === 'assignment' && (
        <div>
          {/* Header Action Bar for Confirmation */}
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '1.25rem',
            padding: '1rem 1.25rem',
            background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.15), rgba(168, 85, 247, 0.1))',
            borderRadius: '12px',
            border: '1px solid rgba(99, 102, 241, 0.3)'
          }}>
            <div>
              <h3 style={{ fontSize: '1.05rem', fontWeight: 800, margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Sparkles size={18} style={{ color: 'var(--accent-primary, #818cf8)' }} />
                Subject Eligibility & Allocation Workflow
              </h3>
              <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)', margin: '0.25rem 0 0 0' }}>
                Confirm eligible faculty per subject based on specialization or manual selection. Once confirmed, assignments flow to all active sections.
              </p>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <button
                type="button"
                className="btn btn-secondary"
                disabled={savingAssignments || proposals.length === 0}
                onClick={handleAutoDistributeAllSubjects}
                style={{ padding: '0.65rem 1.1rem', fontSize: '0.85rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.4rem' }}
                title="Equitably distribute sections among confirmed faculty for each subject to balance workload"
              >
                <Sparkles size={16} /> Auto-Distribute All Sections
              </button>
              <button
                className="btn btn-primary"
                disabled={savingAssignments || proposals.length === 0}
                onClick={handleConfirmAllAssignments}
                style={{ padding: '0.65rem 1.5rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '0.5rem', boxShadow: '0 4px 14px rgba(99, 102, 241, 0.4)' }}
              >
                <Check size={18} /> {savingAssignments ? 'Saving & Syncing…' : 'Confirm & Save All Assignments'}
              </button>
            </div>
          </div>

          {proposalsLoading ? (
            <div className="glass-panel" style={{ padding: '3rem', textAlign: 'center', color: 'var(--text-muted)' }}>
              Loading subject-faculty proposals for {filterDept}…
            </div>
          ) : proposals.length === 0 ? (
            <div className="glass-panel" style={{ padding: '3.5rem 1rem', textAlign: 'center' }}>
              <BookOpen size={48} style={{ opacity: 0.4, margin: '0 auto 1rem auto' }} />
              <h3 style={{ fontSize: '1.1rem', fontWeight: 700, margin: 0 }}>No subjects found for this department.</h3>
              <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '0.4rem' }}>
                Configure subjects in <strong>Subjects & Credits</strong> first, or choose another department above.
              </p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              {proposals.map(p => {
                const subjectState = assignmentsState[p.subjectId] || { facultyIds: [], requiredCount: p.requiredFacultyCount || 1, sectionMap: {} };
                const selectedCount = subjectState.facultyIds.length;
                const requiredCount = subjectState.requiredCount;
                const hasMatching = p.eligibleFaculty && p.eligibleFaculty.length > 0;
                const isSectionExpanded = !!expandedSections[p.subjectId];
                const manualState = manualEntryState[p.subjectId] || { open: false, name: '', employeeId: '', email: '' };

                const isCountSatisfied = selectedCount >= requiredCount;

                return (
                  <div
                    key={p.subjectId}
                    className="glass-panel"
                    style={{
                      padding: '1.25rem',
                      borderLeft: isCountSatisfied
                        ? '4px solid #10b981'
                        : (!hasMatching && selectedCount === 0)
                          ? '4px solid #f59e0b'
                          : '4px solid #6366f1'
                    }}
                  >
                    {/* Top Row: Subject Details & Required Count */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                          <span style={{ fontSize: '1.05rem', fontWeight: 800, color: 'var(--accent-primary, #818cf8)' }}>
                            {p.subjectCode}
                          </span>
                          <span style={{ fontSize: '1.05rem', fontWeight: 800 }}>
                            {p.subjectName}
                          </span>
                          <span className="badge badge-secondary" style={{ fontSize: '0.72rem' }}>
                            {p.subjectType}
                          </span>
                          {p.semester && (
                            <span className="badge badge-warning" style={{ fontSize: '0.72rem' }}>
                              Sem {p.semester}
                            </span>
                          )}
                          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                            ({p.weeklyHours ? p.weeklyHours.toFixed(1) : '3.0'} hrs/wk)
                          </span>
                        </div>
                        <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                          Department: <strong>{p.department}</strong>
                        </div>
                      </div>

                      {/* Required Faculty Counter */}
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', background: 'rgba(0,0,0,0.3)', padding: '0.35rem 0.75rem', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.08)' }}>
                        <span style={{ fontSize: '0.82rem', fontWeight: 700, color: 'var(--text-muted)' }}>
                          Required Faculty:
                        </span>
                        <button
                          type="button"
                          className="btn btn-secondary"
                          style={{ padding: '0.15rem 0.45rem', fontSize: '0.8rem', minWidth: '24px' }}
                          onClick={() => handleSetRequiredCount(p.subjectId, requiredCount - 1)}
                          disabled={requiredCount <= 1}
                        >
                          -
                        </button>
                        <span style={{ fontSize: '0.95rem', fontWeight: 800, minWidth: '16px', textAlign: 'center' }}>
                          {requiredCount}
                        </span>
                        <button
                          type="button"
                          className="btn btn-secondary"
                          style={{ padding: '0.15rem 0.45rem', fontSize: '0.8rem', minWidth: '24px' }}
                          onClick={() => handleSetRequiredCount(p.subjectId, requiredCount + 1)}
                        >
                          +
                        </button>

                        <span
                          className={`badge ${isCountSatisfied ? 'badge-success' : 'badge-danger'}`}
                          style={{ marginLeft: '0.3rem', fontSize: '0.75rem' }}
                        >
                          {selectedCount} / {requiredCount} Selected
                        </span>
                      </div>
                    </div>

                    {/* Middle: Faculty Matching & Selection Area */}
                    <div style={{ marginTop: '1rem', paddingTop: '1rem', borderTop: '1px solid rgba(255,255,255,0.06)' }}>
                      {hasMatching ? (
                        <div>
                          <div style={{ fontSize: '0.82rem', fontWeight: 700, marginBottom: '0.5rem', color: 'var(--text-muted)' }}>
                            Eligible Faculty from Uploaded Department Roster:
                          </div>
                          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '0.65rem' }}>
                            {p.eligibleFaculty.map(f => {
                              const isSelected = subjectState.facultyIds.includes(f.id);
                              return (
                                <div
                                  key={f.id}
                                  onClick={() => handleToggleFacultyForSubject(p.subjectId, f.id)}
                                  style={{
                                    padding: '0.65rem 0.85rem',
                                    borderRadius: '8px',
                                    cursor: 'pointer',
                                    userSelect: 'none',
                                    transition: 'all 0.15s ease',
                                    background: isSelected ? 'rgba(99, 102, 241, 0.2)' : 'rgba(0,0,0,0.2)',
                                    border: isSelected ? '1px solid #818cf8' : '1px solid rgba(255,255,255,0.08)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                    gap: '0.5rem'
                                  }}
                                >
                                  <div>
                                    <div style={{ fontSize: '0.85rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                                      {isSelected && <Check size={14} style={{ color: '#818cf8' }} />}
                                      <span>{f.name}</span>
                                    </div>
                                    <div style={{ fontSize: '0.74rem', color: 'var(--text-muted)', marginTop: '0.15rem' }}>
                                      {f.employeeId} {f.designation ? `• ${f.designation}` : ''}
                                    </div>
                                    {f.specialization && (
                                      <div style={{ fontSize: '0.7rem', color: '#c084fc', marginTop: '0.1rem' }}>
                                        Spec: {f.specialization}
                                      </div>
                                    )}
                                  </div>
                                  <div style={{ textAlign: 'right', fontSize: '0.72rem', color: f.currentLoad > f.maxLoad ? '#ef4444' : 'var(--text-muted)' }}>
                                    Load: {f.currentLoad} / {f.maxLoad} hrs
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        </div>
                      ) : (
                        /* Case 6 Requirement: Subject with NO matching faculty in uploaded PDF */
                        <div style={{
                          background: 'rgba(245, 158, 11, 0.12)',
                          border: '1px solid rgba(245, 158, 11, 0.4)',
                          borderRadius: '8px',
                          padding: '0.85rem 1rem'
                        }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#fbbf24', fontSize: '0.88rem', fontWeight: 700 }}>
                            <AlertTriangle size={18} />
                            <span>No suitable faculty found for this subject in the uploaded department roster.</span>
                          </div>
                          <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', margin: '0.35rem 0 0.75rem 0' }}>
                            The system does not guess or randomly assign faculty. Please manually select an existing faculty member or enter the instructor details below.
                          </p>

                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
                            {/* Option 1: Dropdown from all faculty */}
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', flex: 1, minWidth: '240px' }}>
                              <span style={{ fontSize: '0.78rem', fontWeight: 700 }}>Select Existing:</span>
                              <select
                                className="form-select"
                                style={{ padding: '0.35rem 0.6rem', fontSize: '0.8rem', flex: 1 }}
                                value=""
                                onChange={e => {
                                  if (e.target.value) {
                                    handleToggleFacultyForSubject(p.subjectId, Number(e.target.value));
                                  }
                                }}
                              >
                                <option value="">-- Choose faculty from institution --</option>
                                {allInstitutionFaculty.map(af => (
                                  <option key={af.id} value={af.id}>
                                    {af.name} ({af.employeeId}) — {af.department}
                                  </option>
                                ))}
                              </select>
                            </div>

                            {/* Option 2: Enter manually */}
                            <button
                              type="button"
                              className="btn btn-secondary"
                              style={{ fontSize: '0.8rem', padding: '0.35rem 0.75rem', display: 'flex', alignItems: 'center', gap: '0.35rem' }}
                              onClick={() => setManualEntryState(prev => ({
                                ...prev,
                                [p.subjectId]: {
                                  open: !manualState.open,
                                  name: '',
                                  employeeId: `FAC-${Math.floor(1000 + Math.random() * 9000)}`,
                                  email: ''
                                }
                              }))}
                            >
                              <UserPlus size={14} /> + Enter Faculty Manually
                            </button>
                          </div>

                          {/* Quick Manual Entry Form */}
                          {manualState.open && (
                            <div style={{ marginTop: '0.75rem', padding: '0.75rem', background: 'rgba(0,0,0,0.3)', borderRadius: '6px' }}>
                              <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 2fr auto', gap: '0.5rem', alignItems: 'end' }}>
                                <div>
                                  <label style={{ fontSize: '0.72rem', color: 'var(--text-muted)', display: 'block', marginBottom: '0.2rem' }}>
                                    Faculty Full Name *
                                  </label>
                                  <input
                                    type="text"
                                    className="form-input"
                                    placeholder="e.g. Dr. K. Ramanathan"
                                    value={manualState.name || ''}
                                    onChange={e => setManualEntryState(prev => ({
                                      ...prev,
                                      [p.subjectId]: { ...prev[p.subjectId], name: e.target.value }
                                    }))}
                                    style={{ padding: '0.3rem 0.5rem', fontSize: '0.8rem' }}
                                  />
                                </div>
                                <div>
                                  <label style={{ fontSize: '0.72rem', color: 'var(--text-muted)', display: 'block', marginBottom: '0.2rem' }}>
                                    Emp ID
                                  </label>
                                  <input
                                    type="text"
                                    className="form-input"
                                    value={manualState.employeeId || ''}
                                    onChange={e => setManualEntryState(prev => ({
                                      ...prev,
                                      [p.subjectId]: { ...prev[p.subjectId], employeeId: e.target.value }
                                    }))}
                                    style={{ padding: '0.3rem 0.5rem', fontSize: '0.8rem' }}
                                  />
                                </div>
                                <div>
                                  <label style={{ fontSize: '0.72rem', color: 'var(--text-muted)', display: 'block', marginBottom: '0.2rem' }}>
                                    Email
                                  </label>
                                  <input
                                    type="email"
                                    className="form-input"
                                    placeholder="faculty@odysseus.edu"
                                    value={manualState.email || ''}
                                    onChange={e => setManualEntryState(prev => ({
                                      ...prev,
                                      [p.subjectId]: { ...prev[p.subjectId], email: e.target.value }
                                    }))}
                                    style={{ padding: '0.3rem 0.5rem', fontSize: '0.8rem' }}
                                  />
                                </div>
                                <button
                                  type="button"
                                  className="btn btn-primary"
                                  style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem' }}
                                  onClick={() => handleAddManualFaculty(p.subjectId)}
                                >
                                  Add & Assign
                                </button>
                              </div>
                            </div>
                          )}
                        </div>
                      )}

                      {/* Display Selected Faculty Pills */}
                      {selectedCount > 0 && (
                        <div style={{ marginTop: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                          <span style={{ fontSize: '0.78rem', fontWeight: 700, color: 'var(--text-muted)' }}>
                            Assigned to this Subject:
                          </span>
                          {subjectState.facultyIds.map(fid => {
                            const facObj = allInstitutionFaculty.find(f => f.id === fid)
                              || (p.eligibleFaculty && p.eligibleFaculty.find(f => f.id === fid));
                            return (
                              <span
                                key={fid}
                                style={{
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  gap: '0.35rem',
                                  background: 'rgba(99, 102, 241, 0.25)',
                                  border: '1px solid #818cf8',
                                  color: '#c7d2fe',
                                  padding: '0.2rem 0.55rem',
                                  borderRadius: '6px',
                                  fontSize: '0.78rem',
                                  fontWeight: 700
                                }}
                              >
                                {facObj ? facObj.name : `Faculty #${fid}`}
                                <button
                                  type="button"
                                  onClick={() => handleToggleFacultyForSubject(p.subjectId, fid)}
                                  style={{ background: 'transparent', border: 'none', color: 'inherit', cursor: 'pointer', padding: 0, marginLeft: '0.2rem' }}
                                >
                                  &times;
                                </button>
                              </span>
                            );
                          })}
                        </div>
                      )}
                    </div>

                    {/* Section-Wise Preview & Customization (Requirement 11 & Case 5) */}
                    {deptBatches.length > 0 && (
                      <div style={{ marginTop: '0.85rem', paddingTop: '0.65rem', borderTop: '1px solid rgba(255,255,255,0.06)' }}>
                        <div
                          onClick={() => setExpandedSections(prev => ({ ...prev, [p.subjectId]: !prev[p.subjectId] }))}
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            cursor: 'pointer',
                            fontSize: '0.8rem',
                            fontWeight: 700,
                            color: 'var(--text-muted)'
                          }}
                        >
                          <span style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                            Section-Wise Faculty Assignment ({deptBatches.length} Sections)
                            {selectedCount > 1 && (
                              <span style={{ fontSize: '0.72rem', color: '#34d399' }}>
                                (Multi-faculty distributed)
                              </span>
                            )}
                          </span>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                            <button
                              type="button"
                              className="btn btn-secondary"
                              style={{ fontSize: '0.72rem', padding: '0.2rem 0.55rem', fontWeight: 600 }}
                              onClick={(e) => {
                                e.stopPropagation();
                                handleAutoDistributeSubject(p.subjectId);
                              }}
                              title="Evenly distribute sections among selected faculty"
                            >
                              ⚡ Auto-Distribute Sections
                            </button>
                            <span>{isSectionExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}</span>
                          </div>
                        </div>

                        {isSectionExpanded && (
                          <div style={{ marginTop: '0.75rem', background: 'rgba(0,0,0,0.2)', padding: '0.75rem', borderRadius: '6px' }}>
                            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '0.5rem' }}>
                              Different sections can have different faculty. Select which confirmed instructor teaches each section:
                            </div>
                            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '0.5rem' }}>
                              {deptBatches.map(b => {
                                const currentAssignedId = subjectState.sectionMap ? subjectState.sectionMap[b.id] : null;
                                return (
                                  <div
                                    key={b.id}
                                    style={{
                                      display: 'flex',
                                      alignItems: 'center',
                                      justifyContent: 'space-between',
                                      background: 'rgba(0,0,0,0.25)',
                                      padding: '0.35rem 0.6rem',
                                      borderRadius: '4px',
                                      fontSize: '0.8rem'
                                    }}
                                  >
                                    <span style={{ fontWeight: 700 }}>{b.batchName}</span>
                                    <select
                                      className="form-select"
                                      style={{ padding: '0.2rem 0.4rem', fontSize: '0.75rem', width: 'auto', maxWidth: '160px' }}
                                      value={currentAssignedId || ''}
                                      onChange={e => handleSectionFacultyChange(p.subjectId, b.id, e.target.value)}
                                    >
                                      <option value="">-- Unassigned --</option>
                                      {/* Offer confirmed faculty first */}
                                      {subjectState.facultyIds.map(fid => {
                                        const fObj = allInstitutionFaculty.find(f => f.id === fid)
                                          || (p.eligibleFaculty && p.eligibleFaculty.find(f => f.id === fid));
                                        return (
                                          <option key={fid} value={fid}>
                                            {fObj ? fObj.name : `Faculty #${fid}`}
                                          </option>
                                        );
                                      })}
                                      {/* Also allow selecting from all institution faculty */}
                                      <optgroup label="Other Faculty">
                                        {allInstitutionFaculty.filter(f => !subjectState.facultyIds.includes(f.id)).map(f => (
                                          <option key={f.id} value={f.id}>{f.name}</option>
                                        ))}
                                      </optgroup>
                                    </select>
                                  </div>
                                );
                              })}
                            </div>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {/* Bottom Confirmation Bar */}
          {proposals.length > 0 && (
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginTop: '1.5rem',
              padding: '1rem',
              background: 'rgba(0,0,0,0.3)',
              borderRadius: '8px'
            }}>
              <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                Configured assignments will be synced across all student sections for <strong>{filterDept}</strong>.
              </span>
              <button
                className="btn btn-primary"
                disabled={savingAssignments}
                onClick={handleConfirmAllAssignments}
                style={{ padding: '0.65rem 1.5rem', fontWeight: 700 }}
              >
                <Check size={18} /> {savingAssignments ? 'Saving & Syncing…' : 'Confirm & Save All Assignments'}
              </button>
            </div>
          )}
        </div>
      )}

      {/* ========================================================================= */}
      {/* TAB 2: FACULTY ROSTER & WORKLOAD (Original Master View)                   */}
      {/* ========================================================================= */}
      {activeTab === 'roster' && (
        <div>
          {/* Workload Summary Metric Cards */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
            <div className="glass-panel" style={{ padding: '1rem' }}>
              <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Faculty Staff</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, marginTop: '0.25rem', color: 'var(--accent-primary)' }}>
                {faculty.length} Professors
              </div>
            </div>
            <div className="glass-panel" style={{ padding: '1rem' }}>
              <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Allocated Weekly Load</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, marginTop: '0.25rem', color: '#38bdf8' }}>
                {totalAllocated} hrs / {totalMax} max
              </div>
            </div>
            <div className="glass-panel" style={{ padding: '1rem' }}>
              <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Average Weekly Load</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, marginTop: '0.25rem', color: '#a78bfa' }}>
                {avgLoad} hrs / faculty
              </div>
            </div>
            <div className="glass-panel" style={{ padding: '1rem' }}>
              <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Workload Balance</div>
              <div style={{ fontSize: '1rem', fontWeight: 700, marginTop: '0.45rem', color: '#34d399', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                <Check size={18} /> Load Distributed
              </div>
            </div>
          </div>

          <div className="glass-panel">
            <div className="data-table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Emp ID</th>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Dept</th>
                    <th>Designation / Specialization</th>
                    <th>Allocated / Max</th>
                    <th>Eligible Teaching Subjects</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredFaculty.map(f => {
                    const wl = workloads.find(w => w.facultyId === f.id);
                    const currentLoad = wl ? wl.allocatedHours : 0;
                    return (
                      <tr key={f.id}>
                        <td><strong>{f.employeeId}</strong></td>
                        <td>{f.name}</td>
                        <td>{f.email}</td>
                        <td>{f.department}</td>
                        <td>
                          <div style={{ fontSize: '0.8rem' }}>{f.designation || 'Faculty'}</div>
                          {f.specialization && (
                            <div style={{ fontSize: '0.72rem', color: '#c084fc' }}>{f.specialization}</div>
                          )}
                        </td>
                        <td>
                          <span style={{ fontWeight: 700, color: currentLoad > f.maxWeeklyLoad ? 'var(--accent-danger)' : 'var(--accent-secondary)' }}>
                            {currentLoad} / {f.maxWeeklyLoad} hrs
                          </span>
                        </td>
                        <td>
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.25rem' }}>
                            {f.eligibleSubjects && f.eligibleSubjects.map(s => (
                              <span key={s.id} className="badge badge-warning" style={{ fontSize: '0.7rem' }}>
                                {s.subjectCode}
                              </span>
                            ))}
                            {(!f.eligibleSubjects || f.eligibleSubjects.length === 0) && (
                              <span style={{ fontSize: '0.75rem', color: 'var(--accent-danger)' }}>No subjects assigned</span>
                            )}
                          </div>
                        </td>
                        <td>
                          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
                            <span className={`badge ${f.active ? 'badge-success' : 'badge-danger'}`}>
                              {f.active ? 'Active' : 'Inactive'}
                            </span>
                            {Boolean(f.hod || (f.designation && f.designation.toLowerCase().includes('hod'))) && (
                              <span className="badge badge-warning" style={{ fontSize: '0.65rem' }}>
                                HOD (Excluded)
                              </span>
                            )}
                          </div>
                        </td>
                        <td>
                          <div style={{ display: 'flex', gap: '0.4rem' }}>
                            <button
                              className="btn btn-secondary"
                              style={{ padding: '0.4rem 0.6rem', border: '1px solid rgba(168,85,247,0.4)', background: 'rgba(168,85,247,0.15)' }}
                              title="Configure Availability Matrix"
                              onClick={() => handleOpenAvailability(f)}
                            >
                              <Calendar size={14} style={{ color: '#c084fc' }} />
                            </button>
                            <button className="btn btn-secondary" style={{ padding: '0.4rem 0.6rem' }} onClick={() => handleEdit(f)}>
                              <Edit2 size={14} />
                            </button>
                            <button className="btn btn-danger" style={{ padding: '0.4rem 0.6rem' }} onClick={() => handleDelete(f.id)}>
                              <Trash2 size={14} />
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                  {filteredFaculty.length === 0 && (
                    <tr>
                      <td colSpan="9" style={{ textAlign: 'center', padding: '3.5rem 1rem' }}>
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.75rem' }}>
                          <Users size={44} style={{ color: 'var(--text-muted)', opacity: 0.5 }} />
                          <h4 style={{ fontSize: '1.05rem', fontWeight: 700, margin: 0 }}>No faculty records found.</h4>
                          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', margin: 0, maxWidth: '450px' }}>
                            Upload a faculty PDF roster or add faculty members manually.
                          </p>
                          <button
                            className="btn btn-primary"
                            style={{ marginTop: '0.5rem', fontSize: '0.85rem' }}
                            onClick={() => { setShowUploadModal(true); setUploadError(''); setUploadSuccess(''); }}
                          >
                            <Upload size={16} /> Upload Faculty PDF
                          </button>
                        </div>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* MODAL: ADD / EDIT FACULTY MEMBER                                          */}
      {/* ========================================================================= */}
      {showModal && (
        <div className="modal-backdrop-centered">
          <div className="modal-card-centered glass-panel" style={{ width: '100%', maxWidth: '650px', maxHeight: '90vh', overflowY: 'auto', padding: '2rem' }}>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 800, marginBottom: '1.5rem' }}>{editId ? 'Edit Faculty' : 'Add Faculty Member'}</h2>
            {error && <div className="alert alert-danger">{error}</div>}
            <form onSubmit={handleSubmit}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '1rem' }}>
                <div className="form-group">
                  <label className="form-label">Employee ID *</label>
                  <input type="text" className="form-input" value={form.employeeId} onChange={e => setForm({ ...form, employeeId: e.target.value })} required />
                </div>
                <div className="form-group">
                  <label className="form-label">Full Name *</label>
                  <input type="text" className="form-input" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} required />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr', gap: '1rem' }}>
                <div className="form-group">
                  <label className="form-label">Email *</label>
                  <input type="email" className="form-input" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} required />
                </div>
                <div className="form-group">
                  <label className="form-label">Department *</label>
                  <select className="form-select" value={form.department} onChange={e => setForm({ ...form, department: e.target.value })}>
                    {INSTITUTIONAL_PROGRAMMES.map(prog => (
                      <option key={prog} value={prog}>{prog}</option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Max Load (hrs)</label>
                  <input type="number" className="form-input" value={form.maxWeeklyLoad} onChange={e => setForm({ ...form, maxWeeklyLoad: Number(e.target.value) })} min="1" max="40" required />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                <div className="form-group">
                  <label className="form-label">Designation</label>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="e.g. Associate Professor"
                    value={form.designation}
                    onChange={e => setForm({ ...form, designation: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Specialization / Expertise</label>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="e.g. Data Structures, AI/ML, Networks"
                    value={form.specialization}
                    onChange={e => setForm({ ...form, specialization: e.target.value })}
                  />
                </div>
              </div>

              <div className="form-group" style={{ marginTop: '0.75rem', background: 'rgba(239, 68, 68, 0.08)', padding: '0.65rem 0.85rem', borderRadius: '8px', border: '1px solid rgba(239, 68, 68, 0.25)', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <input
                  type="checkbox"
                  id="formHodCheckbox"
                  checked={form.hod || false}
                  onChange={e => setForm({ ...form, hod: e.target.checked })}
                  style={{ width: '18px', height: '18px', cursor: 'pointer' }}
                />
                <label htmlFor="formHodCheckbox" style={{ margin: 0, fontSize: '0.85rem', cursor: 'pointer', fontWeight: 600, color: '#fca5a5' }}>
                  Head of Department (HOD) — Exclude from regular class teaching assignments
                </label>
              </div>

              <div className="form-group" style={{ marginTop: '1rem' }}>
                <label className="form-label">Subject Teaching Eligibility</label>
                <div className="glass-panel" style={{ maxHeight: '180px', overflowY: 'auto', padding: '0.75rem', background: 'rgba(0,0,0,0.3)' }}>
                  {subjects.map(s => (
                    <label key={s.id} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem', cursor: 'pointer', fontSize: '0.85rem' }}>
                      <input type="checkbox" checked={form.subjectIds.includes(s.id)} onChange={() => handleSubjectToggle(s.id)} />
                      <span><strong>{s.subjectCode}</strong> — {s.subjectName} ({s.subjectType})</span>
                    </label>
                  ))}
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1.5rem' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Save Faculty</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* MODAL: UPLOAD FACULTY PDF DOCUMENT                                        */}
      {/* ========================================================================= */}
      {showUploadModal && (
        <div className="modal-backdrop-centered">
          <div className="modal-card-centered glass-panel" style={{ width: '100%', maxWidth: '900px', maxHeight: '90vh', overflowY: 'auto', padding: '2rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
              <div>
                <h2 style={{ fontSize: '1.3rem', fontWeight: 800 }}>Upload Faculty List Document</h2>
                <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                  Extract faculty profiles from department PDF. Then proceed directly to subject-wise assignment and confirmation.
                </p>
              </div>
              <button className="btn btn-secondary" onClick={() => { setShowUploadModal(false); setParsedFaculty([]); }}>✕</button>
            </div>

            {uploadError && (
              <div className="alert alert-danger" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                <AlertCircle size={16} /> <span>{uploadError}</span>
              </div>
            )}
            {uploadSuccess && (
              <div className="alert alert-success" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                <Check size={16} /> <span>{uploadSuccess}</span>
              </div>
            )}

            <form onSubmit={handleUploadParse} style={{ marginBottom: '1.5rem', background: 'rgba(0,0,0,0.25)', padding: '1.25rem', borderRadius: 'var(--radius-md)' }}>
              <div style={{ display: 'grid', gridTemplateColumns: '2fr 1.5fr auto', gap: '1rem', alignItems: 'end' }}>
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label className="form-label">Faculty Roster File (PDF / CSV / TXT) *</label>
                  <input
                    type="file"
                    className="form-input"
                    accept=".pdf,.csv,.txt,.doc,.docx"
                    onChange={e => setUploadFile(e.target.files[0])}
                    required
                  />
                </div>
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label className="form-label">Department / Programme</label>
                  <select className="form-select" value={uploadDept} onChange={e => setUploadDept(e.target.value)}>
                    {INSTITUTIONAL_PROGRAMMES.map(prog => (
                      <option key={prog} value={prog}>{prog}</option>
                    ))}
                  </select>
                </div>
                <button type="submit" className="btn btn-primary" disabled={uploadLoading} style={{ padding: '0.65rem 1.25rem' }}>
                  {uploadLoading ? 'Extracting…' : 'Extract PDF'}
                </button>
              </div>
            </form>

            {/* Extracted Preview Table */}
            {parsedFaculty.length > 0 && (
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                  <div>
                    <h4 style={{ fontSize: '0.95rem', fontWeight: 700 }}>
                      Extracted Faculty Preview ({parsedFaculty.length})
                    </h4>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      Verify names, designations, and specializations before importing into Faculty Master.
                    </span>
                  </div>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    style={{ fontSize: '0.75rem', padding: '0.3rem 0.6rem' }}
                    onClick={() => {
                      setParsedFaculty([
                        ...parsedFaculty,
                        {
                          employeeId: `FAC-${1000 + parsedFaculty.length + 1}`,
                          name: '',
                          email: '',
                          department: uploadDept,
                          designation: 'Assistant Professor',
                          specialization: '',
                          maxWeeklyLoad: 18
                        }
                      ]);
                    }}
                  >
                    + Add Row
                  </button>
                </div>

                <div className="data-table-container" style={{ maxHeight: '320px', overflowY: 'auto', marginBottom: '1.25rem' }}>
                  <table className="data-table" style={{ minWidth: '980px' }}>
                    <thead>
                      <tr>
                        <th style={{ minWidth: '110px' }}>Emp ID</th>
                        <th style={{ minWidth: '260px' }}>Full Name</th>
                        <th style={{ minWidth: '200px' }}>Designation / Specialization</th>
                        <th style={{ minWidth: '200px' }}>Department</th>
                        <th style={{ minWidth: '140px', textAlign: 'center' }}>HOD (Non-Teaching)</th>
                        <th style={{ minWidth: '90px', textAlign: 'center' }}>Max Load</th>
                        <th style={{ minWidth: '80px', textAlign: 'center' }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {parsedFaculty.map((f, idx) => (
                        <tr key={idx}>
                          <td>
                            <input
                              type="text"
                              className="form-input"
                              style={{ padding: '0.2rem 0.4rem', fontSize: '0.8rem', width: '100px' }}
                              value={f.employeeId || ''}
                              onChange={e => {
                                const copy = [...parsedFaculty];
                                copy[idx].employeeId = e.target.value;
                                setParsedFaculty(copy);
                              }}
                            />
                          </td>
                          <td>
                            <input
                              type="text"
                              className="form-input"
                              style={{ padding: '0.2rem 0.4rem', fontSize: '0.8rem', minWidth: '250px', width: '100%' }}
                              value={f.name || ''}
                              placeholder="Full faculty name (e.g. Dr. K. Ramanathan)"
                              onChange={e => {
                                const copy = [...parsedFaculty];
                                copy[idx].name = e.target.value;
                                setParsedFaculty(copy);
                              }}
                            />
                          </td>
                          <td>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
                              <input
                                type="text"
                                className="form-input"
                                placeholder="Designation"
                                style={{ padding: '0.15rem 0.35rem', fontSize: '0.75rem' }}
                                value={f.designation || ''}
                                onChange={e => {
                                  const copy = [...parsedFaculty];
                                  copy[idx].designation = e.target.value;
                                  setParsedFaculty(copy);
                                }}
                              />
                              <input
                                type="text"
                                className="form-input"
                                placeholder="Specialization"
                                style={{ padding: '0.15rem 0.35rem', fontSize: '0.75rem' }}
                                value={f.specialization || ''}
                                onChange={e => {
                                  const copy = [...parsedFaculty];
                                  copy[idx].specialization = e.target.value;
                                  setParsedFaculty(copy);
                                }}
                              />
                            </div>
                          </td>
                          <td>
                            <select
                              className="form-select"
                              style={{ padding: '0.2rem 0.4rem', fontSize: '0.75rem', width: 'auto', maxWidth: '180px' }}
                              value={f.department || uploadDept}
                              onChange={e => {
                                const copy = [...parsedFaculty];
                                copy[idx].department = e.target.value;
                                setParsedFaculty(copy);
                              }}
                            >
                              {INSTITUTIONAL_PROGRAMMES.map(prog => (
                                <option key={prog} value={prog}>{prog}</option>
                              ))}
                            </select>
                          </td>
                          <td style={{ textAlign: 'center' }}>
                            <input
                              type="checkbox"
                              style={{ width: '16px', height: '16px', cursor: 'pointer' }}
                              title="Head of Department (HOD) — Excludes from regular teaching class allocations"
                              checked={Boolean(f.hod || (f.designation && f.designation.toLowerCase().includes('hod')))}
                              onChange={e => {
                                const copy = [...parsedFaculty];
                                copy[idx].hod = e.target.checked;
                                setParsedFaculty(copy);
                              }}
                            />
                          </td>
                          <td>
                            <input
                              type="number"
                              className="form-input"
                              style={{ padding: '0.2rem 0.4rem', fontSize: '0.8rem', width: '60px' }}
                              value={f.maxWeeklyLoad || 18}
                              onChange={e => {
                                const copy = [...parsedFaculty];
                                copy[idx].maxWeeklyLoad = Number(e.target.value);
                                setParsedFaculty(copy);
                              }}
                            />
                          </td>
                          <td>
                            <button
                              type="button"
                              className="btn btn-danger"
                              style={{ padding: '0.25rem 0.5rem', fontSize: '0.7rem' }}
                              onClick={() => {
                                setParsedFaculty(parsedFaculty.filter((_, i) => i !== idx));
                              }}
                            >
                              Remove
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem' }}>
                  <button type="button" className="btn btn-secondary" onClick={() => setParsedFaculty([])}>
                    Clear Preview
                  </button>
                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={uploadLoading || parsedFaculty.length === 0}
                    onClick={handleBatchSaveFaculty}
                  >
                    {uploadLoading ? 'Saving...' : `Save ${parsedFaculty.length} Faculty & Proceed to Subject Assignment`}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* MODAL: AVAILABILITY MATRIX                                                */}
      {/* ========================================================================= */}
      {showAvailModal && activeFacultyForAvail && (
        <div className="modal-backdrop-centered">
          <div className="modal-card-centered glass-panel" style={{ width: '100%', maxWidth: '900px', maxHeight: '90vh', overflowY: 'auto', padding: '2rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
              <div>
                <h2 style={{ fontSize: '1.3rem', fontWeight: 800 }}>
                  Availability Matrix — {activeFacultyForAvail.name} ({activeFacultyForAvail.employeeId})
                </h2>
                <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
                  Click slots to cycle: <strong>Available (Default)</strong> ➔ <strong>Preferred (⭐ Purple)</strong> ➔ <strong>Unavailable (✕ Blocked)</strong>.
                </p>
              </div>
              <button className="btn btn-secondary" onClick={() => setShowAvailModal(false)}>✕</button>
            </div>

            {availSuccess && (
              <div className="alert alert-success" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                <Check size={16} /> <span>{availSuccess}</span>
              </div>
            )}

            {/* Legend */}
            <div style={{ display: 'flex', gap: '1.5rem', marginBottom: '1rem', padding: '0.75rem 1rem', background: 'rgba(0,0,0,0.3)', borderRadius: 'var(--radius-sm)', fontSize: '0.8rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <span style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: '22px', height: '22px', borderRadius: '4px', background: 'rgba(34,197,94,0.2)', border: '1px solid #22c55e', color: '#86efac' }}>✓</span>
                <span>Available</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <span style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: '22px', height: '22px', borderRadius: '4px', background: 'rgba(168,85,247,0.3)', border: '1px solid #a855f7', color: '#c084fc' }}>⭐</span>
                <span>Preferred Slot</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <span style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: '22px', height: '22px', borderRadius: '4px', background: 'rgba(239,68,68,0.25)', border: '1px solid #ef4444', color: '#fca5a5' }}>✕</span>
                <span>Unavailable / Leave</span>
              </div>
            </div>

            {/* Matrix Table */}
            <div className="data-table-container" style={{ marginBottom: '1.5rem' }}>
              <table className="data-table" style={{ textAlign: 'center' }}>
                <thead>
                  <tr>
                    <th style={{ textAlign: 'left' }}>Day / Period</th>
                    {PERIODS.map(p => (
                      <th key={p} style={{ textAlign: 'center', minWidth: '75px' }}>
                        P{p}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {DAYS.map(day => (
                    <tr key={day}>
                      <td style={{ textAlign: 'left', fontWeight: 700, fontSize: '0.85rem' }}>
                        {day}
                      </td>
                      {PERIODS.map(period => {
                        const slot = availMatrix[`${day}-${period}`] || { available: true, preferred: false };
                        let bg = 'rgba(34,197,94,0.12)';
                        let border = '1px solid rgba(34,197,94,0.3)';
                        let icon = '✓';
                        let textColor = '#86efac';

                        if (!slot.available) {
                          bg = 'rgba(239,68,68,0.2)';
                          border = '1px solid rgba(239,68,68,0.45)';
                          icon = '✕ Blocked';
                          textColor = '#fca5a5';
                        } else if (slot.preferred) {
                          bg = 'rgba(168,85,247,0.25)';
                          border = '1px solid rgba(168,85,247,0.5)';
                          icon = '⭐ Pref';
                          textColor = '#d8b4fe';
                        }

                        return (
                          <td key={period} style={{ padding: '0.4rem' }}>
                            <div
                              onClick={() => toggleSlot(day, period)}
                              style={{
                                background: bg,
                                border: border,
                                borderRadius: '6px',
                                padding: '0.45rem 0.25rem',
                                cursor: 'pointer',
                                fontSize: '0.72rem',
                                fontWeight: 700,
                                color: textColor,
                                userSelect: 'none',
                                transition: 'all 0.15s ease'
                              }}
                              title="Click to toggle status"
                            >
                              {icon}
                            </div>
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem' }}>
              <button type="button" className="btn btn-secondary" onClick={() => setShowAvailModal(false)}>Cancel</button>
              <button
                type="button"
                className="btn btn-primary"
                disabled={availSaving}
                onClick={handleSaveAvailability}
              >
                {availSaving ? 'Saving...' : 'Save Availability Constraints'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
