import React, { useState, useEffect, useMemo } from 'react';
import {
  Users, BookOpen, Calendar, Clock, CheckCircle2, AlertCircle,
  Layers, ShieldCheck, ChevronRight, BookMarked, UserCheck, ArrowRight,
  RefreshCw, BarChart2, Award, Filter, Check, Search
} from 'lucide-react';
import { INSTITUTIONAL_PROGRAMMES } from '../utils/programmes';
import { getDeptCode, getBadgeStyle } from './FacultyManagementPage';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
const PERIODS = [1, 2, 3, 4, 5, 6, 7, 8];

export default function FacultyPortalPage({ user }) {
  // Department & Semester Filter State (Academic Context)
  const [selectedDept, setSelectedDept] = useState('Computer Science and Engineering');
  const [selectedSemester, setSelectedSemester] = useState('3');

  // Faculty State
  const [facultyList, setFacultyList] = useState([]);
  const [selectedFacultyId, setSelectedFacultyId] = useState('');
  const [profile, setProfile] = useState(null);
  const [subjects, setSubjects] = useState([]);
  const [timetable, setTimetable] = useState([]);
  const [workload, setWorkload] = useState(null);
  const [loading, setLoading] = useState(true);

  // Active Tab
  const [activeTab, setActiveTab] = useState('subjects'); // 'subjects' | 'timetable' | 'workload' | 'allocations'

  // Section Allocations State
  const [sectionAllocations, setSectionAllocations] = useState([]);
  const [allocationsLoading, setAllocationsLoading] = useState(false);
  const [selectedSectionFilter, setSelectedSectionFilter] = useState('ALL');
  const [allocationSearch, setAllocationSearch] = useState('');

  const headers = { Authorization: `Bearer ${user?.token}` };

  // Load initial faculty list
  useEffect(() => {
    fetchFacultyList();
  }, []);

  const fetchFacultyList = async () => {
    try {
      const res = await fetch('/api/faculty/list', { headers });
      if (res.ok) {
        const data = await res.json();
        setFacultyList(data || []);
        if (data && data.length > 0) {
          const matched = data.find(f => f.name && user?.fullName && f.name.toLowerCase().includes(user.fullName.toLowerCase()))
            || data[0];
          setSelectedFacultyId(matched ? String(matched.id) : String(data[0].id));
        }
      }
    } catch (e) {
      console.error('Failed to load faculty list:', e);
    }
  };

  // Filter faculty list by selected department
  const filteredFacultyList = useMemo(() => {
    if (!selectedDept || selectedDept === 'ALL') return facultyList;
    return facultyList.filter(f => {
      const fDept = f.department || '';
      return fDept.toLowerCase().includes(selectedDept.toLowerCase()) ||
        selectedDept.toLowerCase().includes(fDept.toLowerCase()) ||
        (f.departmentKey && selectedDept.toLowerCase().includes(f.departmentKey.toLowerCase()));
    });
  }, [facultyList, selectedDept]);

  // When filtered faculty list changes, update selectedFacultyId if not present
  useEffect(() => {
    if (filteredFacultyList.length > 0) {
      const exists = filteredFacultyList.some(f => String(f.id) === String(selectedFacultyId));
      if (!exists) {
        setSelectedFacultyId(String(filteredFacultyList[0].id));
      }
    }
  }, [filteredFacultyList]);

  // Load individual faculty details when selectedFacultyId changes
  useEffect(() => {
    if (!selectedFacultyId) return;
    loadFacultyDetails(selectedFacultyId);
  }, [selectedFacultyId]);

  const loadFacultyDetails = async (fid) => {
    setLoading(true);
    try {
      const [profRes, subRes, ttRes, wlRes] = await Promise.all([
        fetch(`/api/faculty/profile?facultyId=${fid}`, { headers }),
        fetch(`/api/faculty/my-subjects?facultyId=${fid}`, { headers }),
        fetch(`/api/faculty/my-timetable?facultyId=${fid}`, { headers }),
        fetch(`/api/faculty/my-workload?facultyId=${fid}`, { headers })
      ]);

      if (profRes.ok) setProfile(await profRes.json());
      if (subRes.ok) setSubjects(await subRes.json());
      if (ttRes.ok) setTimetable(await ttRes.json());
      if (wlRes.ok) setWorkload(await wlRes.json());
    } catch (e) {
      console.error('Error loading faculty portal data:', e);
    } finally {
      setLoading(false);
    }
  };

  // Fetch Section-Wise Allocations for Department & Semester
  useEffect(() => {
    fetchDepartmentAllocations();
  }, [selectedDept, selectedSemester]);

  const fetchDepartmentAllocations = async () => {
    setAllocationsLoading(true);
    try {
      let deptQuery = selectedDept && selectedDept !== 'ALL' ? encodeURIComponent(selectedDept) : 'ALL';
      let semQuery = selectedSemester && selectedSemester !== 'ALL' ? `&semester=${selectedSemester}` : '';
      const res = await fetch(`/api/admin/faculty/propose-assignment?department=${deptQuery}${semQuery}`, { headers });
      if (res.ok) {
        const data = await res.json();
        const rawProposals = data.proposals || [];
        const rawBatches = data.batches || [];

        const flat = [];
        let idx = 1;
        rawProposals.forEach(prop => {
          const relevantBatches = prop.relevantBatches && prop.relevantBatches.length > 0
            ? prop.relevantBatches
            : rawBatches;

          relevantBatches.forEach(b => {
            let assignedFaculty = null;
            let status = 'Pending';

            if (prop.existingSectionFaculty && prop.existingSectionFaculty[b.id]) {
              const fid = prop.existingSectionFaculty[b.id];
              assignedFaculty = (prop.eligibleFaculty || []).find(f => f.id === fid)
                || (data.allFaculty || []).find(f => f.id === fid);
              if (assignedFaculty) status = 'Confirmed';
            } else if (prop.suggestedSectionFaculty && prop.suggestedSectionFaculty[b.id]) {
              const fid = prop.suggestedSectionFaculty[b.id];
              assignedFaculty = (prop.eligibleFaculty || []).find(f => f.id === fid)
                || (data.allFaculty || []).find(f => f.id === fid);
              if (assignedFaculty) status = 'Confirmed';
            }

            flat.push({
              id: `${prop.subjectId}-${b.id}`,
              sl: idx++,
              batchId: b.id,
              batchName: b.batchName,
              subjectId: prop.subjectId,
              subjectCode: prop.subjectCode,
              subjectName: prop.subjectName,
              coursePrefix: prop.coursePrefix,
              departmentKey: prop.departmentKey || getDeptCode(prop.department),
              weeklyHours: prop.weeklyHours || 3,
              type: prop.subjectType || 'THEORY',
              assignedFacultyName: assignedFaculty ? assignedFaculty.name : 'Unassigned',
              assignedFacultyDesignation: assignedFaculty?.designation || 'Faculty',
              assignedFacultyDept: assignedFaculty?.departmentKey || (assignedFaculty ? getDeptCode(assignedFaculty.department) : ''),
              status: status
            });
          });
        });
        setSectionAllocations(flat);
      }
    } catch (e) {
      console.error('Failed to load department allocations:', e);
    } finally {
      setAllocationsLoading(false);
    }
  };

  // Filtered allocations for the table
  const displayedAllocations = useMemo(() => {
    return sectionAllocations.filter(item => {
      const matchSection = selectedSectionFilter === 'ALL' || item.batchName === selectedSectionFilter;
      const matchSearch = !allocationSearch.trim() ||
        item.subjectCode.toLowerCase().includes(allocationSearch.toLowerCase()) ||
        item.subjectName.toLowerCase().includes(allocationSearch.toLowerCase()) ||
        item.assignedFacultyName.toLowerCase().includes(allocationSearch.toLowerCase()) ||
        item.batchName.toLowerCase().includes(allocationSearch.toLowerCase());
      return matchSection && matchSearch;
    });
  }, [sectionAllocations, selectedSectionFilter, allocationSearch]);

  const distinctSections = useMemo(() => {
    const set = new Set();
    sectionAllocations.forEach(a => { if (a.batchName) set.add(a.batchName); });
    return Array.from(set).sort();
  }, [sectionAllocations]);

  // Timetable grid
  const timetableGrid = {};
  timetable.forEach(a => {
    if (a.dayOfWeek && a.periodNumber) {
      const key = `${a.dayOfWeek.toUpperCase()}-${a.periodNumber}`;
      timetableGrid[key] = a;
    }
  });

  const deptKey = profile?.departmentKey || getDeptCode(profile?.department);
  const badgeStyle = getBadgeStyle(deptKey);
  const loadPercentage = workload ? Math.min(100, Math.round((workload.totalAssignedHours / workload.maxHours) * 100)) : 0;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', paddingBottom: '3rem' }}>

      {/* ── HEADER ── */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '1rem',
        paddingBottom: '0.75rem',
        borderBottom: '1px solid var(--border-color)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <div style={{
            width: 42,
            height: 42,
            borderRadius: 'var(--radius-md)',
            background: 'linear-gradient(135deg, #6366f1, #a855f7)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            boxShadow: 'var(--shadow-glow)'
          }}>
            <UserCheck size={22} />
          </div>
          <div>
            <h1 style={{ fontSize: '1.4rem', fontWeight: 800, margin: 0, color: 'var(--text-main)', letterSpacing: '-0.02em' }}>
              Easwari Engineering College — Faculty Portal
            </h1>
            <p style={{ fontSize: '0.78rem', color: 'var(--text-dim)', margin: '0.15rem 0 0' }}>
              Personal Academic Dashboard • Section Allotments, Workload Limits &amp; Timetable Schedule
            </p>
          </div>
        </div>
      </div>

      {/* ── ACADEMIC CONTEXT SELECTOR (PURPLE GLASSMORPHISM) ── */}
      <div className="glass-panel" style={{
        padding: '1.15rem 1.4rem',
        borderRadius: 'var(--radius-md)',
        border: '1px solid rgba(168,85,247,0.3)',
        background: 'rgba(23, 7, 51, 0.7)',
        boxShadow: '0 8px 32px 0 rgba(112, 0, 255, 0.1)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem', flexWrap: 'wrap' }}>
            {/* Department Dropdown */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <Filter size={16} style={{ color: '#c084fc' }} />
              <label htmlFor="portal-dept-select" style={{ fontSize: '0.84rem', fontWeight: 700, color: 'var(--text-main)', margin: 0 }}>
                Department:
              </label>
              <select
                id="portal-dept-select"
                className="form-select"
                style={{
                  padding: '0.42rem 0.85rem',
                  fontSize: '0.84rem',
                  minWidth: '290px',
                  maxWidth: '380px',
                  background: 'rgba(15, 6, 32, 0.85)',
                  border: '1px solid rgba(168, 85, 247, 0.4)',
                  color: 'var(--text-main)'
                }}
                value={selectedDept}
                onChange={e => setSelectedDept(e.target.value)}
              >
                <option value="ALL">All 21 Departments</option>
                {INSTITUTIONAL_PROGRAMMES.map(prog => (
                  <option key={prog} value={prog}>{prog}</option>
                ))}
              </select>
            </div>

            {/* Semester Dropdown */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <label htmlFor="portal-sem-select" style={{ fontSize: '0.84rem', fontWeight: 700, color: 'var(--text-main)', margin: 0 }}>
                Semester:
              </label>
              <select
                id="portal-sem-select"
                className="form-select"
                style={{
                  padding: '0.42rem 0.85rem',
                  fontSize: '0.84rem',
                  minWidth: '200px',
                  background: 'rgba(15, 6, 32, 0.85)',
                  border: '1px solid rgba(168, 85, 247, 0.4)',
                  color: 'var(--text-main)'
                }}
                value={selectedSemester}
                onChange={e => setSelectedSemester(e.target.value)}
              >
                <option value="ALL">All Semesters (1–8)</option>
                <option value="1">Sem 1 — Odd Semester</option>
                <option value="2">Sem 2 — Even Semester</option>
                <option value="3">Sem 3 — Odd Semester</option>
                <option value="4">Sem 4 — Even Semester</option>
                <option value="5">Sem 5 — Odd Semester</option>
                <option value="6">Sem 6 — Even Semester</option>
                <option value="7">Sem 7 — Odd Semester</option>
                <option value="8">Sem 8 — Even Semester</option>
              </select>
            </div>
          </div>

          {/* Department Faculty Member Selector */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
            <span style={{ fontSize: '0.78rem', fontWeight: 700, color: 'var(--text-dim)' }}>Faculty:</span>
            <select
              value={selectedFacultyId}
              onChange={(e) => setSelectedFacultyId(e.target.value)}
              className="form-select"
              style={{
                padding: '0.42rem 2rem 0.42rem 0.85rem',
                fontSize: '0.82rem',
                fontWeight: 700,
                minWidth: '240px',
                background: 'rgba(15, 6, 32, 0.85)',
                border: '1px solid rgba(99, 102, 241, 0.4)',
                color: 'var(--text-main)'
              }}
            >
              {filteredFacultyList.map(f => (
                <option key={f.id} value={f.id}>
                  {f.name} ({f.departmentKey || getDeptCode(f.department)})
                </option>
              ))}
            </select>

            <button
              type="button"
              onClick={() => {
                loadFacultyDetails(selectedFacultyId);
                fetchDepartmentAllocations();
              }}
              className="btn btn-secondary"
              style={{ padding: '0.42rem 0.75rem', fontSize: '0.8rem' }}
              title="Refresh portal"
            >
              <RefreshCw size={14} className={loading || allocationsLoading ? 'spin' : ''} />
            </button>
          </div>
        </div>
      </div>

      {/* ── PROFILE & WORKLOAD BANNER ── */}
      {profile && (
        <div className="glass-panel" style={{ padding: '1.4rem', display: 'flex', flexDirection: 'column', gap: '1.2rem' }}>
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
            <div style={{ display: 'flex', gap: '1rem' }}>
              <div style={{
                width: 56,
                height: 56,
                borderRadius: '50%',
                background: 'rgba(99, 102, 241, 0.2)',
                border: '2px solid rgba(99, 102, 241, 0.5)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '1.3rem',
                fontWeight: 800,
                color: '#818cf8'
              }}>
                {profile.name ? profile.name.charAt(0) : 'F'}
              </div>

              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                  <h2 style={{ fontSize: '1.25rem', fontWeight: 800, margin: 0, color: 'var(--text-main)' }}>
                    {profile.name}
                  </h2>
                  <span style={{
                    padding: '0.15rem 0.5rem',
                    borderRadius: '4px',
                    fontSize: '0.72rem',
                    fontWeight: 800,
                    ...badgeStyle
                  }}>
                    {deptKey}
                  </span>
                  <span style={{
                    padding: '0.15rem 0.5rem',
                    borderRadius: '9999px',
                    fontSize: '0.68rem',
                    fontWeight: 700,
                    background: 'rgba(16, 185, 129, 0.15)',
                    color: '#6ee7b7',
                    border: '1px solid rgba(16, 185, 129, 0.3)'
                  }}>
                    Active Teaching Faculty
                  </span>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginTop: '0.35rem', fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                  <span><strong>Designation:</strong> {profile.designation || 'Faculty'}</span>
                  <span>•</span>
                  <span><strong>Emp ID:</strong> {profile.employeeId || 'N/A'}</span>
                  <span>•</span>
                  <span><strong>Department:</strong> {profile.department}</span>
                  {profile.qualification && (
                    <>
                      <span>•</span>
                      <span><strong>Qualification:</strong> {profile.qualification}</span>
                    </>
                  )}
                </div>
              </div>
            </div>

            {/* Quick Navigation Tabs */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              border: '1px solid var(--border-color)',
              borderRadius: 'var(--radius-sm)',
              overflow: 'hidden',
              flexWrap: 'wrap'
            }}>
              <button
                type="button"
                onClick={() => setActiveTab('subjects')}
                style={{
                  padding: '0.45rem 0.85rem',
                  fontSize: '0.78rem',
                  fontWeight: 700,
                  border: 'none',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.35rem',
                  background: activeTab === 'subjects' ? '#4F46E5' : 'transparent',
                  color: activeTab === 'subjects' ? '#fff' : 'var(--text-dim)'
                }}
              >
                <BookOpen size={14} /> My Subjects ({subjects.length})
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('timetable')}
                style={{
                  padding: '0.45rem 0.85rem',
                  fontSize: '0.78rem',
                  fontWeight: 700,
                  border: 'none',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.35rem',
                  background: activeTab === 'timetable' ? '#4F46E5' : 'transparent',
                  color: activeTab === 'timetable' ? '#fff' : 'var(--text-dim)'
                }}
              >
                <Calendar size={14} /> Weekly Schedule ({timetable.length} pds)
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('workload')}
                style={{
                  padding: '0.45rem 0.85rem',
                  fontSize: '0.78rem',
                  fontWeight: 700,
                  border: 'none',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.35rem',
                  background: activeTab === 'workload' ? '#4F46E5' : 'transparent',
                  color: activeTab === 'workload' ? '#fff' : 'var(--text-dim)'
                }}
              >
                <BarChart2 size={14} /> Workload Analysis
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('allocations')}
                style={{
                  padding: '0.45rem 0.85rem',
                  fontSize: '0.78rem',
                  fontWeight: 700,
                  border: 'none',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.35rem',
                  background: activeTab === 'allocations' ? '#9333ea' : 'transparent',
                  color: activeTab === 'allocations' ? '#fff' : '#c084fc'
                }}
              >
                <Layers size={14} /> Section Allocations &amp; Faculty ({sectionAllocations.length})
              </button>
            </div>
          </div>

          {/* KPI CARDS */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '0.9rem',
            paddingTop: '0.5rem',
            borderTop: '1px solid var(--border-color)'
          }}>
            <div style={{ padding: '0.85rem', borderRadius: 'var(--radius-sm)', background: 'rgba(99, 102, 241, 0.08)', border: '1px solid rgba(99, 102, 241, 0.25)' }}>
              <div style={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--text-dim)', textTransform: 'uppercase' }}>My Assigned Sections</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#818cf8', marginTop: '0.2rem' }}>
                {subjects.length}
              </div>
              <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)' }}>Confirmed curriculum allotments</div>
            </div>

            <div style={{ padding: '0.85rem', borderRadius: 'var(--radius-sm)', background: 'rgba(16, 185, 129, 0.08)', border: '1px solid rgba(16, 185, 129, 0.25)' }}>
              <div style={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--text-dim)', textTransform: 'uppercase' }}>Weekly Teaching Hours</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#34d399', marginTop: '0.2rem' }}>
                {workload ? `${workload.totalAssignedHours} / ${workload.maxHours}` : '0 / 18'} hrs
              </div>
              <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)' }}>Institutional limit: 18 hrs max</div>
            </div>

            <div style={{ padding: '0.85rem', borderRadius: 'var(--radius-sm)', background: 'rgba(168, 85, 247, 0.08)', border: '1px solid rgba(168, 85, 247, 0.25)' }}>
              <div style={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--text-dim)', textTransform: 'uppercase' }}>Theory vs Practical</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#c084fc', marginTop: '0.2rem' }}>
                {workload?.theoryHours || 0}T / {workload?.labHours || 0}L
              </div>
              <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)' }}>Continuous lab blocks supported</div>
            </div>

            <div style={{ padding: '0.85rem', borderRadius: 'var(--radius-sm)', background: 'rgba(245, 158, 11, 0.08)', border: '1px solid rgba(245, 158, 11, 0.25)' }}>
              <div style={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--text-dim)', textTransform: 'uppercase' }}>Department Total Allocations</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#fbbf24', marginTop: '0.2rem' }}>
                {sectionAllocations.length}
              </div>
              <div style={{ fontSize: '0.68rem', color: 'var(--text-dim)' }}>{selectedDept} (Sem {selectedSemester})</div>
            </div>
          </div>
        </div>
      )}

      {/* ── TAB 1: ASSIGNED SUBJECTS TABLE ── */}
      {activeTab === 'subjects' && (
        <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.5rem' }}>
            <div>
              <h2 style={{ fontSize: '1.05rem', fontWeight: 700, margin: 0, color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
                <BookOpen size={18} style={{ color: '#818cf8' }} />
                My Subject Allotments ({profile?.name || 'Faculty'})
              </h2>
              <p style={{ fontSize: '0.75rem', color: 'var(--text-dim)', margin: '0.2rem 0 0' }}>
                Courses and sections assigned to you for teaching this academic cycle according to institutional curriculum rules.
              </p>
            </div>
          </div>

          <div style={{ overflowX: 'auto', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}>
            <table className="table" style={{ fontSize: '0.78rem', width: '100%', marginBottom: 0 }}>
              <thead>
                <tr>
                  <th style={{ width: '45px', padding: '0.65rem 0.7rem' }}>#</th>
                  <th style={{ width: '90px', padding: '0.65rem 0.7rem' }}>Section</th>
                  <th style={{ width: '110px', padding: '0.65rem 0.7rem' }}>Course Code</th>
                  <th style={{ padding: '0.65rem 0.7rem' }}>Subject Name</th>
                  <th style={{ width: '90px', padding: '0.65rem 0.7rem' }}>Semester</th>
                  <th style={{ width: '90px', padding: '0.65rem 0.7rem' }}>Course Type</th>
                  <th style={{ width: '100px', padding: '0.65rem 0.7rem' }}>Weekly Hours</th>
                  <th style={{ width: '110px', padding: '0.65rem 0.7rem' }}>Status</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan={8} style={{ textAlign: 'center', padding: '2.5rem', color: 'var(--text-dim)' }}>
                      Loading your subject allotments...
                    </td>
                  </tr>
                ) : subjects.length === 0 ? (
                  <tr>
                    <td colSpan={8} style={{ textAlign: 'center', padding: '2.5rem', color: 'var(--text-dim)' }}>
                      No subjects are currently assigned to this faculty member. Select another faculty or check <strong>Section Allocations</strong>.
                    </td>
                  </tr>
                ) : (
                  subjects.map((sub, idx) => (
                    <tr key={sub.offeringId || idx}>
                      <td style={{ padding: '0.55rem 0.7rem', color: 'var(--text-dim)' }}>{idx + 1}</td>
                      <td style={{ padding: '0.55rem 0.7rem', fontWeight: 800, color: 'var(--text-main)' }}>
                        <span style={{
                          padding: '0.12rem 0.45rem',
                          borderRadius: '4px',
                          background: 'rgba(255,255,255,0.04)',
                          border: '1px solid rgba(255,255,255,0.1)'
                        }}>
                          {sub.batchName}
                        </span>
                      </td>
                      <td style={{ padding: '0.55rem 0.7rem', fontWeight: 700, color: 'var(--text-main)' }}>
                        {sub.subjectCode}
                      </td>
                      <td style={{ padding: '0.55rem 0.7rem', fontWeight: 600, color: 'var(--text-main)' }}>
                        {sub.subjectName}
                      </td>
                      <td style={{ padding: '0.55rem 0.7rem', color: 'var(--text-muted)' }}>
                        Semester {sub.semester}
                      </td>
                      <td style={{ padding: '0.55rem 0.7rem' }}>
                        <span style={{
                          padding: '0.12rem 0.45rem',
                          borderRadius: '4px',
                          fontSize: '0.66rem',
                          fontWeight: 700,
                          background: sub.type?.includes('LAB') ? 'rgba(16, 185, 129, 0.15)' : 'rgba(99, 102, 241, 0.15)',
                          color: sub.type?.includes('LAB') ? '#6ee7b7' : '#a5b4fc',
                          border: `1px solid ${sub.type?.includes('LAB') ? 'rgba(16, 185, 129, 0.3)' : 'rgba(99, 102, 241, 0.3)'}`
                        }}>
                          {sub.type}
                        </span>
                      </td>
                      <td style={{ padding: '0.55rem 0.7rem', fontWeight: 700, color: '#34d399' }}>
                        {sub.weeklyLecturePeriods + sub.weeklyPracticalPeriods} hrs/wk
                      </td>
                      <td style={{ padding: '0.55rem 0.7rem' }}>
                        <span style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '0.3rem',
                          padding: '0.12rem 0.45rem',
                          borderRadius: '9999px',
                          fontSize: '0.66rem',
                          fontWeight: 700,
                          background: 'rgba(16, 185, 129, 0.15)',
                          color: '#6ee7b7',
                          border: '1px solid rgba(16, 185, 129, 0.3)'
                        }}>
                          <CheckCircle2 size={11} /> Allotted
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ── TAB 2: WEEKLY TIMETABLE GRID ── */}
      {activeTab === 'timetable' && (
        <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div>
            <h2 style={{ fontSize: '1.05rem', fontWeight: 700, margin: 0, color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
              <Calendar size={18} style={{ color: '#818cf8' }} />
              My Weekly Timetable Schedule ({profile?.name || 'Faculty'})
            </h2>
            <p style={{ fontSize: '0.75rem', color: 'var(--text-dim)', margin: '0.2rem 0 0' }}>
              Weekly class timetable across periods 1–8. Continuous laboratory blocks and room venues are indicated.
            </p>
          </div>

          <div style={{ overflowX: 'auto', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}>
            <table className="table" style={{ fontSize: '0.76rem', width: '100%', marginBottom: 0, borderCollapse: 'collapse' }}>
              <thead>
                <tr>
                  <th style={{ width: '110px', padding: '0.7rem', textAlign: 'center', background: '#170733' }}>Day / Period</th>
                  {PERIODS.map(p => (
                    <th key={p} style={{ width: '12.5%', padding: '0.7rem', textAlign: 'center', background: '#170733' }}>
                      Period {p}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {DAYS.map(day => (
                  <tr key={day} style={{ borderBottom: '1px solid var(--border-color)' }}>
                    <td style={{
                      padding: '0.7rem',
                      fontWeight: 800,
                      color: 'var(--text-main)',
                      textAlign: 'center',
                      background: 'rgba(255,255,255,0.02)',
                      borderRight: '1px solid var(--border-color)'
                    }}>
                      {day}
                    </td>

                    {PERIODS.map(period => {
                      const assign = timetableGrid[`${day}-${period}`];
                      if (!assign) {
                        return (
                          <td key={period} style={{
                            padding: '0.6rem',
                            textAlign: 'center',
                            color: 'var(--text-dim)',
                            borderRight: '1px solid var(--border-color)'
                          }}>
                            <span style={{ fontSize: '0.7rem', opacity: 0.4 }}>Free</span>
                          </td>
                        );
                      }

                      const isLab = assign.subjectType?.includes('LAB') || assign.isLabBlock;

                      return (
                        <td key={period} style={{
                          padding: '0.45rem',
                          verticalAlign: 'top',
                          borderRight: '1px solid var(--border-color)',
                          background: isLab ? 'rgba(16, 185, 129, 0.08)' : 'rgba(99, 102, 241, 0.08)'
                        }}>
                          <div style={{
                            padding: '0.4rem 0.5rem',
                            borderRadius: 'var(--radius-sm)',
                            border: `1px solid ${isLab ? 'rgba(16, 185, 129, 0.3)' : 'rgba(99, 102, 241, 0.3)'}`
                          }}>
                            <div style={{ fontWeight: 800, fontSize: '0.74rem', color: isLab ? '#6ee7b7' : '#a5b4fc' }}>
                              {assign.subjectCode}
                            </div>
                            <div style={{ fontSize: '0.68rem', color: 'var(--text-main)', fontWeight: 600, marginTop: '0.1rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                              {assign.subjectName}
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '0.25rem', fontSize: '0.64rem', color: 'var(--text-dim)' }}>
                              <span style={{ fontWeight: 700, color: 'var(--text-muted)' }}>{assign.batchName}</span>
                              <span style={{ fontWeight: 700, color: '#fbbf24' }}>{assign.roomNumber}</span>
                            </div>
                          </div>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ── TAB 3: WORKLOAD ANALYSIS ── */}
      {activeTab === 'workload' && (
        <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          <div>
            <h2 style={{ fontSize: '1.05rem', fontWeight: 700, margin: 0, color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
              <BarChart2 size={18} style={{ color: '#818cf8' }} />
              Institutional Workload Breakdown
            </h2>
            <p style={{ fontSize: '0.75rem', color: 'var(--text-dim)', margin: '0.2rem 0 0' }}>
              Workload limits strictly cap each faculty member at 18 hours per week across theory, tutorial, and continuous lab blocks.
            </p>
          </div>

          <div style={{
            padding: '1.2rem',
            borderRadius: 'var(--radius-md)',
            background: 'rgba(255,255,255,0.02)',
            border: '1px solid var(--border-color)',
            display: 'flex',
            flexDirection: 'column',
            gap: '0.85rem'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '0.82rem', fontWeight: 700, color: 'var(--text-main)' }}>
                Weekly Capacity Usage: {workload?.totalAssignedHours || 0} / {workload?.maxHours || 18} Hours
              </span>
              <span style={{ fontSize: '0.82rem', fontWeight: 800, color: loadPercentage > 90 ? '#ef4444' : '#10b981' }}>
                {loadPercentage}% Utilized
              </span>
            </div>

            <div style={{ width: '100%', height: '10px', background: 'rgba(255,255,255,0.06)', borderRadius: '9999px', overflow: 'hidden' }}>
              <div style={{
                height: '100%',
                width: `${loadPercentage}%`,
                background: loadPercentage > 90 ? 'linear-gradient(90deg, #ef4444, #f97316)' : 'linear-gradient(90deg, #10b981, #6366f1)',
                borderRadius: '9999px',
                transition: 'width 0.4s ease'
              }} />
            </div>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '0.74rem', color: 'var(--text-dim)' }}>
              <span>Remaining Capacity: <strong style={{ color: '#34d399' }}>{workload?.remainingCapacity || 0} hours</strong></span>
              <span>Workload Compliance: <strong style={{ color: '#10b981' }}>100% Compliant (within 18h limit)</strong></span>
            </div>
          </div>
        </div>
      )}

      {/* ── TAB 4: DEPARTMENT SECTION ALLOCATIONS & ASSIGNED FACULTY ── */}
      {activeTab === 'allocations' && (
        <div className="glass-panel" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem' }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <h2 style={{ fontSize: '1.15rem', fontWeight: 800, margin: 0, color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Layers size={20} style={{ color: '#c084fc' }} />
                  Department Section Allocations &amp; Live Assigned Faculty
                </h2>
                <span style={{
                  fontSize: '0.72rem',
                  fontWeight: 800,
                  padding: '0.15rem 0.55rem',
                  borderRadius: '9999px',
                  background: 'rgba(168, 85, 247, 0.15)',
                  color: '#c084fc',
                  border: '1px solid rgba(168, 85, 247, 0.3)'
                }}>
                  {selectedDept} • Sem {selectedSemester}
                </span>
              </div>
              <p style={{ fontSize: '0.76rem', color: 'var(--text-dim)', margin: '0.25rem 0 0' }}>
                Authoritative view of all section offerings and assigned teaching faculty. Faculty eligibility and institutional 18-hour workload limits are strictly enforced.
              </p>
            </div>

            {/* Filter controls */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
              {/* Section Filter */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <span style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-dim)' }}>Section:</span>
                <select
                  value={selectedSectionFilter}
                  onChange={(e) => setSelectedSectionFilter(e.target.value)}
                  className="form-select"
                  style={{ padding: '0.4rem 1.8rem 0.4rem 0.75rem', fontSize: '0.78rem', minWidth: '120px' }}
                >
                  <option value="ALL">All Sections ({distinctSections.length})</option>
                  {distinctSections.map(s => (
                    <option key={s} value={s}>{s}</option>
                  ))}
                </select>
              </div>

              {/* Search input */}
              <div style={{ position: 'relative' }}>
                <Search size={14} style={{ position: 'absolute', left: '0.65rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-dim)' }} />
                <input
                  type="text"
                  placeholder="Search code, faculty, subject..."
                  value={allocationSearch}
                  onChange={e => setAllocationSearch(e.target.value)}
                  className="form-control"
                  style={{ padding: '0.4rem 0.75rem 0.4rem 2rem', fontSize: '0.78rem', width: '220px' }}
                />
              </div>
            </div>
          </div>

          {/* Allocation Statistics Bar */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '1.5rem',
            padding: '0.75rem 1rem',
            borderRadius: 'var(--radius-sm)',
            background: 'rgba(255,255,255,0.02)',
            border: '1px solid var(--border-color)',
            fontSize: '0.78rem',
            flexWrap: 'wrap'
          }}>
            <span>Total Offerings: <strong style={{ color: 'var(--text-main)' }}>{displayedAllocations.length}</strong> (of {sectionAllocations.length} total)</span>
            <span>•</span>
            <span>Assigned Faculty: <strong style={{ color: '#10b981' }}>{displayedAllocations.filter(a => a.assignedFacultyName !== 'Unassigned').length}</strong></span>
            <span>•</span>
            <span>Unassigned: <strong style={{ color: displayedAllocations.some(a => a.assignedFacultyName === 'Unassigned') ? '#f59e0b' : '#10b981' }}>
              {displayedAllocations.filter(a => a.assignedFacultyName === 'Unassigned').length}
            </strong></span>
            <span>•</span>
            <span>Faculty Conflict Status: <strong style={{ color: '#10b981' }}>Zero Cross-Section Collisions</strong></span>
          </div>

          {/* Allocations Table */}
          <div style={{ overflowX: 'auto', maxHeight: '550px', overflowY: 'auto', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}>
            <table className="table" style={{ fontSize: '0.78rem', width: '100%', marginBottom: 0 }}>
              <thead>
                <tr>
                  <th style={{ width: '45px', padding: '0.65rem 0.6rem' }}>Sl.</th>
                  <th style={{ width: '85px', padding: '0.65rem 0.6rem' }}>Section</th>
                  <th style={{ width: '110px', padding: '0.65rem 0.6rem' }}>Course Code</th>
                  <th style={{ padding: '0.65rem 0.6rem' }}>Subject Title</th>
                  <th style={{ width: '65px', padding: '0.65rem 0.6rem' }}>Dept</th>
                  <th style={{ width: '90px', padding: '0.65rem 0.6rem' }}>Weekly Hrs</th>
                  <th style={{ width: '90px', padding: '0.65rem 0.6rem' }}>Type</th>
                  <th style={{ padding: '0.65rem 0.6rem' }}>Assigned Faculty</th>
                  <th style={{ width: '100px', padding: '0.65rem 0.6rem' }}>Status</th>
                </tr>
              </thead>
              <tbody>
                {allocationsLoading ? (
                  <tr>
                    <td colSpan={9} style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-dim)' }}>
                      Evaluating curriculum allocations and faculty assignments for {selectedDept}...
                    </td>
                  </tr>
                ) : displayedAllocations.length === 0 ? (
                  <tr>
                    <td colSpan={9} style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-dim)' }}>
                      No subject offerings found for {selectedDept} in Semester {selectedSemester}.
                    </td>
                  </tr>
                ) : (
                  displayedAllocations.map((item, idx) => {
                    const isConfirmed = item.status === 'Confirmed';
                    const isLab = item.type?.includes('LAB') || item.type === 'PRACTICAL';
                    const dBadge = getBadgeStyle(item.departmentKey);

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
                            background: isLab
                              ? 'rgba(16, 185, 129, 0.15)'
                              : item.type === 'THEORY_AND_LAB'
                              ? 'rgba(245, 158, 11, 0.15)'
                              : 'rgba(99, 102, 241, 0.15)',
                            color: isLab
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
                          <span style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '0.25rem',
                            padding: '0.12rem 0.45rem',
                            borderRadius: '9999px',
                            fontSize: '0.66rem',
                            fontWeight: 700,
                            background: isConfirmed ? 'rgba(16, 185, 129, 0.15)' : 'rgba(245, 158, 11, 0.15)',
                            color: isConfirmed ? '#6ee7b7' : '#fcd34d',
                            border: `1px solid ${isConfirmed ? 'rgba(16, 185, 129, 0.3)' : 'rgba(245, 158, 11, 0.3)'}`
                          }}>
                            {isConfirmed ? <CheckCircle2 size={11} /> : null}
                            {item.status}
                          </span>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

    </div>
  );
}
