import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { PlayCircle, AlertCircle, CheckCircle2, Loader, FileCode, Clock, XCircle, ArrowRight, Filter, ShieldCheck, ShieldAlert, Users, BookOpen, Building2 } from 'lucide-react';
import { INSTITUTIONAL_PROGRAMMES } from '../utils/programmes';
import { SEMESTER_OPTIONS, getStoredAcademicContext, setStoredAcademicContext } from '../utils/academicContext';

export default function GenerateTimetablePage({ user }) {
  const navigate = useNavigate();
  const [jobId, setJobId] = useState(null);
  const [jobStatus, setJobStatus] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showContractModal, setShowContractModal] = useState(false);
  const [contractPayload, setContractPayload] = useState(null);
  const [readiness, setReadiness] = useState(null);
  const [readinessLoading, setReadinessLoading] = useState(false);
  const pollingRef = useRef(null);

  // Scope selectors — synced with global academic context
  const initialContext = getStoredAcademicContext();
  const [department, setDepartment] = useState(() => initialContext.department || '');
  const [semester, setSemester] = useState(() => initialContext.semester || '');

  useEffect(() => {
    const handleContextChange = (e) => {
      const ctx = e.detail || getStoredAcademicContext();
      setDepartment(ctx.department || '');
      setSemester(ctx.semester || '');
    };
    window.addEventListener('academicContextChanged', handleContextChange);
    window.addEventListener('storage', handleContextChange);
    return () => {
      window.removeEventListener('academicContextChanged', handleContextChange);
      window.removeEventListener('storage', handleContextChange);
    };
  }, []);

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

  const startPolling = useCallback((jid, dept, sem) => {
    if (pollingRef.current) clearInterval(pollingRef.current);

    pollingRef.current = setInterval(async () => {
      try {
        const res = await fetch(`/api/scheduler/jobs/${jid}`, {
          headers: user?.token ? { 'Authorization': `Bearer ${user.token}` } : {}
        });
        if (!res.ok) return;
        const data = await res.json();
        setJobStatus(data);
        localStorage.setItem(`schedulerJob_${dept || ''}_${sem || ''}`, JSON.stringify(data));

        if (['COMPLETED', 'FAILED', 'INFEASIBLE'].includes(data.status)) {
          clearInterval(pollingRef.current);
          pollingRef.current = null;
          setLoading(false);
        }
      } catch (e) {
        console.error('Polling error:', e);
      }
    }, 1500);
  }, [user]);

  // Fetch the latest job status from backend on mount and scope change; fallback to localStorage
  const fetchLatestJob = useCallback(async (dept, sem) => {
    try {
      const params = new URLSearchParams();
      if (dept) params.append('dept', dept);
      if (sem) params.append('sem', sem);
      const res = await fetch(`/api/scheduler/jobs/latest?${params.toString()}`, {
        headers: user?.token ? { 'Authorization': `Bearer ${user.token}` } : {}
      });
      if (res.ok) {
        const data = await res.json();
        setJobId(data.jobId);
        setJobStatus(data);
        localStorage.setItem(`schedulerJob_${dept || ''}_${sem || ''}`, JSON.stringify(data));
        if (data.status === 'QUEUED' || data.status === 'SOLVING') {
          setLoading(true);
          startPolling(data.jobId, dept, sem);
        } else {
          setLoading(false);
        }
        return;
      }
    } catch (e) {
      console.warn('Backend fetch latest job failed, falling back to localStorage:', e);
    }

    // LocalStorage fallback
    try {
      const cached = localStorage.getItem(`schedulerJob_${dept || ''}_${sem || ''}`);
      if (cached) {
        const data = JSON.parse(cached);
        setJobId(data.jobId);
        setJobStatus(data);
        if (data.status === 'QUEUED' || data.status === 'SOLVING') {
          setLoading(true);
          startPolling(data.jobId, dept, sem);
        } else {
          setLoading(false);
        }
      } else {
        setJobId(null);
        setJobStatus(null);
        setLoading(false);
      }
    } catch (e) {
      setJobId(null);
      setJobStatus(null);
      setLoading(false);
    }
  }, [user, startPolling]);

  useEffect(() => {
    fetchReadiness(department, semester);
    fetchLatestJob(department, semester);
  }, [department, semester, fetchReadiness, fetchLatestJob]);

  // Cleanup polling on unmount
  useEffect(() => {
    return () => { if (pollingRef.current) clearInterval(pollingRef.current); };
  }, []);

  const handleGenerateClick = async () => {
    setLoading(true);
    setError('');
    setJobId(null);
    setJobStatus(null);

    try {
      const params = new URLSearchParams();
      if (department) params.append('department', department);
      if (semester)   params.append('semester', semester);
      const res = await fetch(`/api/scheduler/generate?${params.toString()}`, {
        method: 'POST',
        headers: user?.token ? { 'Authorization': `Bearer ${user.token}` } : {}
      });
      const data = await res.json();

      if (!res.ok) {
        if (data.status === 'VALIDATION_FAILED') {
          setJobStatus({ status: 'VALIDATION_FAILED', message: data.message, readiness: data.readiness });
        } else {
          setError(data.message || data.error || 'Failed to start generation');
        }
        setLoading(false);
        fetchReadiness(department, semester);
        return;
      }

      setJobId(data.jobId);
      setJobStatus({ status: data.status, message: data.message });
      localStorage.setItem(`schedulerJob_${department || ''}_${semester || ''}`, JSON.stringify(data));
      startPolling(data.jobId, department, semester);
    } catch (e) {
      setError('Network connection error: ' + e.message);
      setLoading(false);
    }
  };

  const handleInspectContract = async () => {
    try {
      const res = await fetch('/api/scheduler/input', {
        headers: user?.token ? { 'Authorization': `Bearer ${user.token}` } : {}
      });
      if (res.ok) {
        setContractPayload(await res.json());
        setShowContractModal(true);
      }
    } catch (e) { console.error(e); }
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
              setStoredAcademicContext(e.target.value, semester);
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
              setStoredAcademicContext(department, e.target.value);
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
                {jobStatus.status === 'QUEUED' && 'Queued'}
                {jobStatus.status === 'SOLVING' && 'Solving...'}
                {jobStatus.status === 'COMPLETED' && 'Generated successfully'}
                {jobStatus.status === 'FAILED' && 'Failed'}
                {jobStatus.status === 'INFEASIBLE' && 'Infeasible'}
                {jobStatus.status === 'VALIDATION_FAILED' && 'Validation Failed'}
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
                    View Timetables <ArrowRight size={16} />
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
