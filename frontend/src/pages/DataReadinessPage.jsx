import React, { useState, useEffect } from 'react';
import {
  CheckCircle2, AlertTriangle, RefreshCw, ArrowRight,
  XCircle, Info, ShieldCheck, Filter
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { INSTITUTIONAL_PROGRAMMES } from '../utils/programmes';
import { getStoredAcademicContext, setStoredAcademicContext } from '../utils/academicContext';

export default function DataReadinessPage({ user }) {
  const [readiness, setReadiness] = useState(null);
  const [loading, setLoading] = useState(false);

  // Scope selectors — persisted to localStorage so they stay consistent across pages
  const [department, setDepartment] = useState(
    () => getStoredAcademicContext().department || ''
  );
  const [semester, setSemester] = useState(
    () => getStoredAcademicContext().semester || ''
  );
  const [academicYear] = useState('2026-2027');

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

  // Auto-run on mount or scope change
  useEffect(() => { runCheck(); }, [department, semester]);

  const runCheck = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (department) params.append('department', department);
      if (semester)   params.append('semester', semester);
      if (academicYear) params.append('academicYear', academicYear);

      const res = await fetch(`/api/admin/readiness?${params.toString()}`, {
        headers: { Authorization: `Bearer ${user.token}` }
      });
      if (res.ok) setReadiness(await res.json());
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const handleDeptChange = (val) => {
    setDepartment(val);
    setStoredAcademicContext(val, semester);
  };
  const handleSemChange = (val) => {
    setSemester(val);
    setStoredAcademicContext(department, val);
  };

  const blockingCount = readiness?.blockingIssues?.length ?? 0;
  const warningCount  = readiness?.warnings?.length ?? 0;

  return (
    <div>
      {/* Header */}
      <div className="page-header">
        <div>
          <h1 className="page-title">Data Readiness</h1>
          <p className="page-subtitle">
            Validate master data for a specific scheduling scope before generating timetables.
          </p>
        </div>
        <button className="btn btn-primary" onClick={runCheck} disabled={loading}>
          <RefreshCw size={16} className={loading ? 'spin' : ''} />
          {loading ? 'Checking…' : 'Run Check'}
        </button>
      </div>

      {/* Scope Selector */}
      <div className="glass-panel" style={{ marginBottom: '1.5rem', padding: '1.1rem 1.3rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Filter size={15} style={{ color: 'var(--accent-primary)' }} />
            <span style={{ fontSize: '0.83rem', fontWeight: 700 }}>Scope:</span>
          </div>

          <select
            className="form-select"
            style={{ padding: '0.38rem 0.85rem', fontSize: '0.83rem', minWidth: '260px' }}
            value={department}
            onChange={e => handleDeptChange(e.target.value)}
          >
            <option value="">All Departments (Global Check)</option>
            {INSTITUTIONAL_PROGRAMMES.map(p => (
              <option key={p} value={p}>{p}</option>
            ))}
          </select>

          <select
            className="form-select"
            style={{ padding: '0.38rem 0.85rem', fontSize: '0.83rem', minWidth: '150px' }}
            value={semester}
            onChange={e => handleSemChange(e.target.value)}
          >
            <option value="">All Semesters</option>
            {[1,2,3,4,5,6,7,8].map(s => <option key={s} value={s}>Semester {s}</option>)}
          </select>

          {(department || semester) && (
            <span className="badge badge-success" style={{ fontSize: '0.78rem' }}>
              {[department ? department.split(' ').slice(-1)[0] : null, semester ? 'Sem ' + semester : null]
                .filter(Boolean).join(' · ')}
            </span>
          )}

          <button
            className="btn btn-primary"
            style={{ marginLeft: 'auto', padding: '0.38rem 1rem', fontSize: '0.83rem' }}
            onClick={runCheck}
            disabled={loading}
          >
            {loading ? 'Checking…' : 'Apply & Run'}
          </button>
        </div>
      </div>

      {readiness && (
        <div>
          {/* Summary Banner */}
          <div
            className={`alert ${readiness.ready ? 'alert-success' : 'alert-danger'}`}
            style={{ padding: '1.1rem 1.4rem', marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '1rem' }}
          >
            {readiness.ready
              ? <CheckCircle2 size={28} />
              : <XCircle size={28} />
            }
            <div>
              <div style={{ fontWeight: 800, fontSize: '1rem' }}>
                {readiness.ready ? 'READY FOR TIMETABLE GENERATION' : 'BLOCKING ISSUES FOUND'}
              </div>
              <div style={{ fontSize: '0.88rem', marginTop: '0.25rem', opacity: 0.9 }}>
                {readiness.summaryMessage}
              </div>
            </div>
          </div>

          {/* Stats Row */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: '0.75rem', marginBottom: '1.5rem' }}>
            <StatCard label="Subjects Checked" value={readiness.subjectsChecked ?? '—'} color="var(--accent-primary)" />
            <StatCard label="Sections Checked" value={readiness.batchesChecked ?? '—'} color="var(--accent-secondary)" />
            <StatCard label="Offerings Checked" value={readiness.offeringsChecked ?? '—'} color="var(--accent-success)" />
            <StatCard
              label="Blocking Issues"
              value={blockingCount}
              color={blockingCount > 0 ? 'var(--accent-danger)' : 'var(--accent-success)'}
            />
            <StatCard
              label="Warnings"
              value={warningCount}
              color={warningCount > 0 ? 'var(--accent-warning)' : 'var(--accent-success)'}
            />
          </div>

          {/* Blocking Issues */}
          {blockingCount > 0 && (
            <div className="glass-panel" style={{ marginBottom: '1.25rem', borderLeft: '4px solid var(--accent-danger)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '1rem' }}>
                <XCircle size={18} style={{ color: 'var(--accent-danger)' }} />
                <h3 style={{ fontSize: '0.95rem', fontWeight: 700, color: 'var(--accent-danger)', margin: 0 }}>
                  Blocking Issues ({blockingCount})
                </h3>
                <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                  — must be resolved before generation
                </span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                {readiness.blockingIssues.map((issue, idx) => (
                  <div
                    key={idx}
                    style={{
                      background: 'rgba(239,68,68,0.07)',
                      border: '1px solid rgba(239,68,68,0.2)',
                      borderRadius: '6px',
                      padding: '0.65rem 0.9rem',
                      display: 'flex',
                      gap: '0.6rem',
                      alignItems: 'flex-start',
                      fontSize: '0.875rem'
                    }}
                  >
                    <span style={{ color: 'var(--accent-danger)', fontWeight: 700, minWidth: '1.2rem' }}>
                      {idx + 1}.
                    </span>
                    <span>{issue}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Warnings */}
          {warningCount > 0 && (
            <div className="glass-panel" style={{ marginBottom: '1.25rem', borderLeft: '4px solid var(--accent-warning)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '1rem' }}>
                <AlertTriangle size={18} style={{ color: 'var(--accent-warning)' }} />
                <h3 style={{ fontSize: '0.95rem', fontWeight: 700, color: 'var(--accent-warning)', margin: 0 }}>
                  Warnings ({warningCount})
                </h3>
                <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                  — advisory; will not block generation
                </span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                {readiness.warnings.map((w, idx) => (
                  <div
                    key={idx}
                    style={{
                      background: 'rgba(245,158,11,0.07)',
                      border: '1px solid rgba(245,158,11,0.2)',
                      borderRadius: '6px',
                      padding: '0.65rem 0.9rem',
                      display: 'flex',
                      gap: '0.6rem',
                      alignItems: 'flex-start',
                      fontSize: '0.875rem'
                    }}
                  >
                    <span style={{ color: 'var(--accent-warning)', fontWeight: 700, minWidth: '1.2rem' }}>
                      {idx + 1}.
                    </span>
                    <span>{w}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* All clear */}
          {readiness.ready && (
            <div className="glass-panel" style={{ textAlign: 'center', padding: '2rem' }}>
              <ShieldCheck size={40} style={{ color: 'var(--accent-success)', marginBottom: '0.75rem' }} />
              <h3 style={{ fontSize: '1.1rem', fontWeight: 700 }}>All Checks Passed</h3>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.88rem', margin: '0.5rem 0 1.5rem 0' }}>
                Subjects, offerings, faculty assignments, rooms, timeslots, and calendar are all valid.
                {warningCount > 0 && ` (${warningCount} advisory warning(s) noted above)`}
              </p>
              <Link to="/generate" className="btn btn-primary" style={{ display: 'inline-flex' }}>
                Proceed to Generate Timetable <ArrowRight size={16} />
              </Link>
            </div>
          )}

          {/* Info note about faculty roster */}
          <div
            style={{
              marginTop: '1.25rem',
              padding: '0.75rem 1rem',
              borderRadius: '6px',
              background: 'rgba(99,102,241,0.07)',
              border: '1px solid rgba(99,102,241,0.2)',
              display: 'flex',
              gap: '0.6rem',
              fontSize: '0.83rem',
              color: 'var(--text-muted)'
            }}
          >
            <Info size={15} style={{ color: 'var(--accent-primary)', flexShrink: 0, marginTop: '1px' }} />
            <span>
              Faculty Master contains the complete institutional faculty roster. Only faculty assigned
              to subject offerings within the selected scope are evaluated here. Unassigned faculty
              in the master roster are not reported as errors.
            </span>
          </div>
        </div>
      )}
    </div>
  );
}

function StatCard({ label, value, color }) {
  return (
    <div className="stat-card" style={{ textAlign: 'center', padding: '0.85rem', gap: '0.2rem' }}>
      <div style={{ fontSize: '1.6rem', fontWeight: 800, color }}>{value}</div>
      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', lineHeight: 1.3 }}>{label}</div>
    </div>
  );
}
