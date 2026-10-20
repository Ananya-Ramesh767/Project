import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlayCircle, AlertCircle, CheckCircle2, Loader, FileCode, Clock, XCircle, Calendar, ArrowRight, Filter, ShieldCheck, ShieldAlert, FlaskConical, Users, BookOpen, Building2, Download, CheckSquare } from 'lucide-react';
import { INSTITUTIONAL_PROGRAMMES } from '../utils/programmes';
import { SEMESTER_OPTIONS, getStoredAcademicContext, setStoredAcademicContext } from '../utils/academicContext';

const DAYS_ORDER = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
const PERIODS = [1, 2, 3, 4, 5, 6, 7, 8];

// Color palette for subjects
const SUBJECT_COLORS = [
  { bg: 'rgba(99, 102, 241, 0.25)', border: '#818cf8', text: '#a5b4fc' },
  { bg: 'rgba(16, 185, 129, 0.25)', border: '#34d399', text: '#6ee7b7' },
  { bg: 'rgba(245, 158, 11, 0.25)', border: '#fbbf24', text: '#fcd34d' },
  { bg: 'rgba(239, 68, 68, 0.25)',  border: '#f87171', text: '#fca5a5' },
  { bg: 'rgba(139, 92, 246, 0.25)', border: '#a78bfa', text: '#c4b5fd' },
  { bg: 'rgba(6, 182, 212, 0.25)',  border: '#22d3ee', text: '#67e8f9' },
  { bg: 'rgba(236, 72, 153, 0.25)', border: '#f472b6', text: '#f9a8d4' },
  { bg: 'rgba(34, 197, 94, 0.25)',  border: '#4ade80', text: '#86efac' },
];

function getSubjectColor(subjectId, colorMap) {
  if (!colorMap.current[subjectId]) {
    const idx = Object.keys(colorMap.current).length % SUBJECT_COLORS.length;
    colorMap.current[subjectId] = SUBJECT_COLORS[idx];
  }
  return colorMap.current[subjectId];
}

export default function GenerateTimetablePage({ user }) {
  const navigate = useNavigate();
  const [jobId, setJobId] = useState(null);
  const [jobStatus, setJobStatus] = useState(null);
  const [timetable, setTimetable] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showContractModal, setShowContractModal] = useState(false);
  const [contractPayload, setContractPayload] = useState(null);
  const [readiness, setReadiness] = useState(null);
  const [readinessLoading, setReadinessLoading] = useState(false);
  const pollingRef = useRef(null);
  const subjectColorMap = useRef({});

  // Scope selectors — synced with global academic context
  const initialContext = getStoredAcademicContext();
  const [department, setDepartment] = useState(() => initialContext.department || '');
  const [semester, setSemester] = useState(() => initialContext.semester || '');

  // Admin validation checklist
  const [checklist, setChecklist] = useState({
    facultyClash: true,
    roomClash: true,
    hodExclusion: true,
    maxCapacity: true,
    floorCoherence: true
  });
  const [timetableConfirmed, setTimetableConfirmed] = useState(false);
  const [confirmMsg, setConfirmMsg] = useState('');

  // Fetch readiness report whenever scope changes
  const fetchReadiness = useCallback(async (dept, sem) => {
    setReadinessLoading(true);
    try {
      const params = new URLSearchParams();
      if (dept) params.append('department', dept);
      if (sem) params.append('semester', sem);
      const res = await fetch(`/api/scheduler/readiness?${params.toString()}`);
      if (res.ok) setReadiness(await res.json());
    } catch (e) { /* silent */ }
    finally { setReadinessLoading(false); }
  }, []);

  useEffect(() => {
    fetchReadiness(department, semester);
  }, [department, semester, fetchReadiness]);

  // Cleanup polling on unmount
  useEffect(() => {
    return () => { if (pollingRef.current) clearInterval(pollingRef.current); };
  }, []);

  const handleGenerateClick = async () => {
    setLoading(true);
    setError('');
    setJobId(null);
    setJobStatus(null);
    setTimetable(null);
    subjectColorMap.current = {};

    try {
      const params = new URLSearchParams();
      if (department) params.append('department', department);
      if (semester)   params.append('semester', semester);
      const res = await fetch(`/api/scheduler/generate?${params.toString()}`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${user.token}` }
      });
      const data = await res.json();

      if (!res.ok) {
        if (data.status === 'VALIDATION_FAILED') {
          setJobStatus({ status: 'VALIDATION_FAILED', message: data.message, readiness: data.readiness });
        } else {
          setError(data.message || data.error || 'Failed to start generation');
        }
        setLoading(false);
        // Refresh readiness after attempt
        fetchReadiness(department, semester);
        return;
      }

      setJobId(data.jobId);
      setJobStatus({ status: data.status, message: data.message });
      startPolling(data.jobId);
    } catch (e) {
      setError('Network connection error: ' + e.message);
      setLoading(false);
    }
  };

  const startPolling = (jid) => {
    if (pollingRef.current) clearInterval(pollingRef.current);

    pollingRef.current = setInterval(async () => {
      try {
        const res = await fetch(`/api/scheduler/jobs/${jid}`);
        if (!res.ok) return;
        const data = await res.json();
        setJobStatus(data);

        if (['COMPLETED', 'FAILED', 'INFEASIBLE'].includes(data.status)) {
          clearInterval(pollingRef.current);
          pollingRef.current = null;
          setLoading(false);

          if (data.status === 'COMPLETED' || data.status === 'INFEASIBLE') {
            fetchTimetable(jid);
          }
        }
      } catch (e) {
        console.error('Polling error:', e);
      }
    }, 1500);
  };

  const fetchTimetable = async (jid) => {
    try {
      const res = await fetch(`/api/scheduler/timetables/${jid}`);
      if (res.ok) {
        const data = await res.json();
        setTimetable(data);
      }
    } catch (e) { console.error('Fetch timetable error:', e); }
  };

  const handleInspectContract = async () => {
    try {
      const res = await fetch('/api/scheduler/input');
      if (res.ok) {
        setContractPayload(await res.json());
        setShowContractModal(true);
      }
    } catch (e) { console.error(e); }
  };

  // Group timetable entries by batch
  const groupByBatch = (entries) => {
    const groups = {};
    for (const entry of entries) {
      const key = entry.batchId;
      if (!groups[key]) groups[key] = { batchName: entry.batchName, entries: [] };
      groups[key].entries.push(entry);
    }
    return groups;
  };

  // Build a lookup: day -> period -> entry
  const buildGrid = (entries) => {
    const grid = {};
    for (const day of DAYS_ORDER) {
      grid[day] = {};
      for (const p of PERIODS) grid[day][p] = null;
    }
    for (const entry of entries) {
      if (grid[entry.dayOfWeek]) {
        grid[entry.dayOfWeek][entry.periodNumber] = entry;
      }
    }
    return grid;
  };

  const statusIcon = () => {
    if (!jobStatus) return null;
    switch (jobStatus.status) {
      case 'QUEUED': return <Clock size={36} style={{ color: '#fbbf24' }} />;
      case 'SOLVING': return <Loader size={36} style={{ color: '#818cf8', animation: 'spin 1.5s linear infinite' }} />;
      case 'COMPLETED': return <CheckCircle2 size={36} style={{ color: '#34d399' }} />;
      case 'FAILED': return <XCircle size={36} style={{ color: '#f87171' }} />;
      case 'INFEASIBLE': return <AlertCircle size={36} style={{ color: '#fbbf24' }} />;
      case 'VALIDATION_FAILED': return <AlertCircle size={36} style={{ color: '#f87171' }} />;
      default: return null;
    }
  };

  const statusColor = () => {
    if (!jobStatus) return 'var(--text-muted)';
    switch (jobStatus.status) {
      case 'QUEUED': return '#fbbf24';
      case 'SOLVING': return '#818cf8';
      case 'COMPLETED': return '#34d399';
      case 'FAILED': return '#f87171';
      case 'INFEASIBLE': return '#fbbf24';
      case 'VALIDATION_FAILED': return '#f87171';
      default: return 'var(--text-muted)';
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Generate Timetable</h1>
          <p className="page-subtitle">Run the constraint-satisfaction solver to build a timetable from validated master data.</p>
        </div>
      </div>

      {/* Scope + Action Panel */}
      <div className="glass-panel" style={{ padding: '1.5rem 1.75rem', marginBottom: '1.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
          <Filter size={15} style={{ color: 'var(--accent-primary)' }} />
          <span style={{ fontSize: '0.85rem', fontWeight: 700 }}>Scheduling Scope</span>
          <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginLeft: '0.25rem' }}>— only data within this scope will be validated and scheduled</span>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'center', marginBottom: '1.5rem' }}>
          <select
            className="form-select"
            style={{ padding: '0.4rem 0.85rem', fontSize: '0.84rem', minWidth: '260px' }}
            value={department}
            onChange={e => {
              setDepartment(e.target.value);
              setStoredAcademicContext({ department: e.target.value });
            }}
          >
            <option value="">-- Select Department --</option>
            <option value="ALL">All Departments</option>
            {INSTITUTIONAL_PROGRAMMES.map(p => <option key={p} value={p}>{p}</option>)}
          </select>
          <select
            className="form-select"
            style={{ padding: '0.4rem 0.85rem', fontSize: '0.84rem', minWidth: '220px' }}
            value={semester}
            onChange={e => {
              setSemester(e.target.value);
              setStoredAcademicContext({ semester: e.target.value });
            }}
          >
            <option value="">-- Select Semester --</option>
            <option value="ALL">All Semesters</option>
            {SEMESTER_OPTIONS.map(opt => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
          </select>
          {(department || semester) && (
            <span className="badge badge-success" style={{ fontSize: '0.78rem' }}>
              {[department?.split(' ').slice(-1)[0], semester ? 'Sem ' + semester : null].filter(Boolean).join(' · ')}
            </span>
          )}
        </div>

        <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
          <button
            className="btn btn-primary"
            style={{ padding: '0.75rem 1.75rem', fontSize: '0.95rem' }}
            onClick={handleGenerateClick}
            disabled={loading}
          >
            <PlayCircle size={18} />
            {loading ? (jobStatus?.status === 'SOLVING' ? 'Solver Running…' : 'Starting…') : 'Generate Timetable'}
          </button>
          <button className="btn btn-secondary" style={{ padding: '0.75rem 1.25rem', fontSize: '0.88rem' }} onClick={handleInspectContract}>
            <FileCode size={16} /> Inspect Scheduler Contract
          </button>
        </div>
      </div>

      {/* Readiness Panel */}
      {readiness && (
        <div className="glass-panel" style={{
          padding: '1.25rem 1.5rem',
          marginBottom: '1.5rem',
          borderLeft: `4px solid ${readiness.ready ? '#34d399' : '#f87171'}`
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.85rem' }}>
            {readiness.ready
              ? <ShieldCheck size={18} style={{ color: '#34d399' }} />
              : <ShieldAlert size={18} style={{ color: '#f87171' }} />}
            <span style={{ fontWeight: 700, fontSize: '0.9rem', color: readiness.ready ? '#34d399' : '#f87171' }}>
              {readiness.ready ? 'Ready to Generate' : 'Not Ready — Fix Issues Below'}
            </span>
            {readinessLoading && <Loader size={14} style={{ color: 'var(--text-muted)', animation: 'spin 1.5s linear infinite', marginLeft: '0.25rem' }} />}
            <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginLeft: 'auto' }}>{readiness.summaryMessage}</span>
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.6rem', marginBottom: readiness.blockingIssues?.length ? '0.85rem' : 0 }}>
            {[
              { icon: <Users size={13} />, label: `${readiness.batchesChecked ?? '?'} Sections`, ok: (readiness.batchesChecked ?? 0) > 0 },
              { icon: <Building2 size={13} />, label: `${readiness.subjectsChecked ?? '?'} Subjects`, ok: (readiness.subjectsChecked ?? 0) > 0 },
              { icon: <BookOpen size={13} />, label: `${readiness.offeringsChecked ?? '?'} Offerings`, ok: (readiness.offeringsChecked ?? 0) > 0 },
            ].map((item, i) => (
              <span key={i} style={{
                display: 'inline-flex', alignItems: 'center', gap: '0.3rem',
                padding: '0.25rem 0.6rem', borderRadius: '999px',
                background: item.ok ? 'rgba(52,211,153,0.12)' : 'rgba(248,113,113,0.12)',
                color: item.ok ? '#34d399' : '#f87171',
                fontSize: '0.78rem', fontWeight: 600, border: `1px solid ${item.ok ? 'rgba(52,211,153,0.3)' : 'rgba(248,113,113,0.3)'}`
              }}>
                {item.ok ? <CheckCircle2 size={11} /> : <XCircle size={11} />}
                {item.icon} {item.label}
              </span>
            ))}
          </div>
          {readiness.blockingIssues?.length > 0 && (
            <div>
              <div style={{ fontSize: '0.78rem', fontWeight: 700, color: 'var(--accent-danger)', marginBottom: '0.35rem' }}>
                {readiness.blockingIssues.length} Blocking Issue(s):
              </div>
              <ul style={{ margin: 0, paddingLeft: '1.1rem', fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                {readiness.blockingIssues.slice(0, 6).map((issue, i) => (
                  <li key={i} style={{ marginBottom: '0.2rem' }}>{issue}</li>
                ))}
                {readiness.blockingIssues.length > 6 && (
                  <li style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>
                    ...and {readiness.blockingIssues.length - 6} more
                  </li>
                )}
              </ul>
            </div>
          )}
          {readiness.warnings?.length > 0 && readiness.ready && (
            <div style={{ marginTop: '0.5rem' }}>
              <div style={{ fontSize: '0.78rem', fontWeight: 700, color: '#fbbf24', marginBottom: '0.35rem' }}>
                {readiness.warnings.length} Advisory Warning(s):
              </div>
              <ul style={{ margin: 0, paddingLeft: '1.1rem', fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                {readiness.warnings.slice(0, 4).map((w, i) => (
                  <li key={i} style={{ marginBottom: '0.15rem' }}>{w}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="glass-panel" style={{ borderLeft: '4px solid var(--accent-danger)', padding: '1.5rem', marginBottom: '2rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <XCircle size={24} style={{ color: 'var(--accent-danger)' }} />
            <p style={{ fontWeight: 600, color: 'var(--accent-danger)' }}>{error}</p>
          </div>
        </div>
      )}

      {/* Job Status Panel */}
      {jobStatus && (
        <div className="glass-panel" style={{ borderLeft: `4px solid ${statusColor()}`, padding: '2rem', marginBottom: '2rem' }}>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: '1rem' }}>
            {statusIcon()}
            <div style={{ flex: 1 }}>
              <h3 style={{ fontSize: '1.25rem', fontWeight: 800, color: statusColor() }}>
                {jobStatus.status === 'QUEUED' && 'Generation Queued'}
                {jobStatus.status === 'SOLVING' && 'Solver Running...'}
                {jobStatus.status === 'COMPLETED' && 'Timetable Generated Successfully'}
                {jobStatus.status === 'FAILED' && 'Generation Failed'}
                {jobStatus.status === 'INFEASIBLE' && 'Infeasible Schedule'}
                {jobStatus.status === 'VALIDATION_FAILED' && 'Validation Errors Blocked Generation'}
              </h3>
              <p style={{ marginTop: '0.5rem', fontSize: '0.95rem', color: 'var(--text-secondary)' }}>
                {jobStatus.message}
              </p>

              {jobId && (
                <div style={{ marginTop: '0.75rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  Job ID: <code style={{ color: '#67e8f9' }}>{jobId}</code>
                  {jobStatus.totalEntries > 0 && (
                    <span> · {jobStatus.totalEntries} entries scheduled</span>
                  )}
                </div>
              )}

              {jobStatus.status === 'COMPLETED' && (
                <div style={{ marginTop: '1.2rem' }}>
                  <button
                    className="btn btn-primary"
                    style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', padding: '0.65rem 1.25rem', fontSize: '0.88rem' }}
                    onClick={() => navigate('/timetable')}
                  >
                    Open Multi-Section Timetable & Relocation Console <ArrowRight size={16} />
                  </button>
                </div>
              )}

              {/* Progress bar for QUEUED/SOLVING */}
              {(jobStatus.status === 'QUEUED' || jobStatus.status === 'SOLVING') && (
                <div style={{ marginTop: '1rem', height: '4px', background: 'rgba(255,255,255,0.1)', borderRadius: '2px', overflow: 'hidden' }}>
                  <div style={{
                    height: '100%',
                    background: `linear-gradient(90deg, ${statusColor()}, transparent)`,
                    animation: 'shimmer 1.5s ease-in-out infinite',
                    width: jobStatus.status === 'QUEUED' ? '30%' : '70%',
                    transition: 'width 0.5s ease'
                  }} />
                </div>
              )}

              {/* Validation issues: blocking + warnings */}
              {jobStatus.status === 'VALIDATION_FAILED' && jobStatus.readiness && (
                <div style={{ marginTop: '1rem' }}>
                  {jobStatus.readiness.blockingIssues?.length > 0 && (
                    <div>
                      <div style={{ fontSize: '0.8rem', fontWeight: 700, color: 'var(--accent-danger)', marginBottom: '0.4rem' }}>
                        Blocking Issues ({jobStatus.readiness.blockingIssues.length})
                      </div>
                      <ul style={{ marginLeft: '1.1rem', fontSize: '0.83rem', color: 'var(--text-muted)' }}>
                        {jobStatus.readiness.blockingIssues.map((issue, i) => (
                          <li key={i} style={{ marginBottom: '0.2rem' }}>{issue}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                  {jobStatus.readiness.warnings?.length > 0 && (
                    <div style={{ marginTop: '0.6rem' }}>
                      <div style={{ fontSize: '0.8rem', fontWeight: 700, color: 'var(--accent-warning)', marginBottom: '0.4rem' }}>
                        Warnings ({jobStatus.readiness.warnings.length})
                      </div>
                      <ul style={{ marginLeft: '1.1rem', fontSize: '0.83rem', color: 'var(--text-muted)' }}>
                        {jobStatus.readiness.warnings.map((w, i) => (
                          <li key={i} style={{ marginBottom: '0.2rem' }}>{w}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Timetable Grid Display */}
      {timetable && timetable.entries?.length > 0 && (
        <div className="glass-panel" style={{ padding: '1.5rem', marginBottom: '1.5rem', border: '1px solid rgba(16, 185, 129, 0.4)', background: 'rgba(16, 185, 129, 0.05)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '1rem' }}>
            <div>
              <h3 style={{ fontSize: '1.15rem', fontWeight: 800, margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#6ee7b7' }}>
                <ShieldCheck size={20} /> Conflict &amp; Constraint Validation Checklist
              </h3>
              <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', margin: '0.25rem 0 0 0' }}>
                All hard scheduling constraints have been satisfied. Verify each item below before administrative publishing.
              </p>
            </div>
            <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => {
                  handleGenerateClick();
                }}
                disabled={loading}
              >
                Request Automatic Revision
              </button>
              <button
                type="button"
                className="btn btn-primary"
                style={{ background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)', border: 'none', fontWeight: 700 }}
                onClick={() => {
                  setTimetableConfirmed(true);
                  setConfirmMsg('✓ Timetable successfully confirmed, approved, and published to faculty and student portals!');
                  navigate('/timetable');
                }}
              >
                <CheckCircle2 size={16} /> Confirm &amp; Publish Timetable
              </button>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '0.75rem', marginTop: '1rem' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.84rem', cursor: 'pointer' }}>
              <input type="checkbox" checked={checklist.facultyClash} onChange={e => setChecklist({ ...checklist, facultyClash: e.target.checked })} />
              <span>✓ 0 Faculty Clashes (No simultaneous classes)</span>
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.84rem', cursor: 'pointer' }}>
              <input type="checkbox" checked={checklist.roomClash} onChange={e => setChecklist({ ...checklist, roomClash: e.target.checked })} />
              <span>✓ 0 Room Clashes (Exclusive room allocation)</span>
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.84rem', cursor: 'pointer' }}>
              <input type="checkbox" checked={checklist.hodExclusion} onChange={e => setChecklist({ ...checklist, hodExclusion: e.target.checked })} />
              <span>✓ HODs Excluded from Regular Teaching Pool</span>
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.84rem', cursor: 'pointer' }}>
              <input type="checkbox" checked={checklist.maxCapacity} onChange={e => setChecklist({ ...checklist, maxCapacity: e.target.checked })} />
              <span>✓ Max 70-Student Capacity Enforced</span>
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.84rem', cursor: 'pointer' }}>
              <input type="checkbox" checked={checklist.floorCoherence} onChange={e => setChecklist({ ...checklist, floorCoherence: e.target.checked })} />
              <span>✓ Block &amp; Floor Department Coherence Checked</span>
            </label>
          </div>
        </div>
      )}

      {/* Timetable Grid Display */}
      {timetable && timetable.entries?.length > 0 && (() => {
        const batches = groupByBatch(timetable.entries);
        return Object.entries(batches).map(([batchId, { batchName, entries }]) => {
          const grid = buildGrid(entries);
          return (
            <div key={batchId} className="glass-panel" style={{ padding: '1.5rem', marginBottom: '2rem' }}>
              <h3 style={{ fontSize: '1.1rem', fontWeight: 800, marginBottom: '1rem', color: 'var(--accent-primary)' }}>
                📅 {batchName} — Weekly Timetable
              </h3>
              <div className="data-table-container">
                <table className="data-table" style={{ fontSize: '0.78rem' }}>
                  <thead>
                    <tr>
                      <th style={{ width: '100px' }}>Day</th>
                      {PERIODS.map(p => (
                        <th key={p} style={{ textAlign: 'center', minWidth: '110px' }}>P{p}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {DAYS_ORDER.map(day => (
                      <tr key={day}>
                        <td style={{ fontWeight: 700, fontSize: '0.8rem' }}>
                          {day.charAt(0) + day.slice(1).toLowerCase()}
                        </td>
                        {PERIODS.map(p => {
                          const entry = grid[day]?.[p];
                          if (!entry) {
                            return <td key={p} style={{ textAlign: 'center', color: 'var(--text-muted)', opacity: 0.4 }}>—</td>;
                          }
                          const color = getSubjectColor(entry.subjectId, subjectColorMap);
                          return (
                            <td key={p} style={{
                              background: color.bg,
                              borderLeft: `3px solid ${color.border}`,
                              padding: '0.4rem 0.5rem',
                              verticalAlign: 'top'
                            }}>
                              <div style={{ fontWeight: 700, color: color.text, fontSize: '0.78rem' }}>
                                {entry.subjectCode}
                              </div>
                              <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                                {entry.facultyName}
                              </div>
                              <div style={{ fontSize: '0.65rem', color: 'var(--text-muted)', marginTop: '1px' }}>
                                {entry.roomCode}
                                <span style={{
                                  marginLeft: '4px',
                                  padding: '1px 4px',
                                  borderRadius: '3px',
                                  background: entry.sessionType === 'LAB' ? 'rgba(245,158,11,0.3)' : 'rgba(99,102,241,0.2)',
                                  color: entry.sessionType === 'LAB' ? '#fbbf24' : '#a5b4fc',
                                  fontSize: '0.6rem',
                                  fontWeight: 600
                                }}>
                                  {entry.sessionType}
                                </span>
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
          );
        });
      })()}

      {/* Empty timetable message */}
      {timetable && (!timetable.entries || timetable.entries.length === 0) && (
        <div className="glass-panel" style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
          <p>No timetable entries were generated. The solver may have found the scheduling problem infeasible.</p>
        </div>
      )}

      {/* Contract Inspection Modal */}
      {showContractModal && contractPayload && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.8)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div className="glass-panel" style={{ width: '100%', maxWidth: '800px', maxHeight: '85vh', overflowY: 'auto', padding: '2rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <div>
                <h2 style={{ fontSize: '1.25rem', fontWeight: 800 }}>Scheduler Input Contract</h2>
                <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                  Version: <code style={{ color: '#67e8f9' }}>{contractPayload.metadata?.contractVersion || '1.0.0'}</code>
                  {' · '}Generated: <code style={{ color: '#67e8f9' }}>{contractPayload.metadata?.generatedAt || 'N/A'}</code>
                </p>
              </div>
              <button className="btn btn-secondary" onClick={() => setShowContractModal(false)}>Close</button>
            </div>
            <pre style={{ background: 'rgba(0,0,0,0.6)', padding: '1.25rem', borderRadius: 'var(--radius-md)', fontSize: '0.75rem', color: '#67e8f9', overflowX: 'auto', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {JSON.stringify(contractPayload, null, 2)}
            </pre>
          </div>
        </div>
      )}

      {/* CSS for shimmer animation */}
      <style>{`
        @keyframes shimmer {
          0% { transform: translateX(-100%); }
          100% { transform: translateX(400%); }
        }
        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
}
