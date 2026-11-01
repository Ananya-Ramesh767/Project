import React, { useState, useEffect } from 'react';
import {
  Users, BookOpen, Calendar, Clock, CheckCircle2, AlertCircle,
  Layers, ShieldCheck, ChevronRight, BookMarked, UserCheck, ArrowRight,
  RefreshCw, BarChart2, Award, Check
} from 'lucide-react';
import { Link } from 'react-router-dom';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
const PERIODS = [1, 2, 3, 4, 5, 6, 7, 8];

export default function FacultyPortalPage({ user }) {
  const [profile, setProfile] = useState(null);
  const [subjects, setSubjects] = useState([]);
  const [timetable, setTimetable] = useState([]);
  const [workload, setWorkload] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const headers = { Authorization: `Bearer ${user?.token}` };

  useEffect(() => {
    loadMyData();
  }, []);

  const loadMyData = async () => {
    setLoading(true);
    setError('');
    try {
      const [profRes, subRes, ttRes, wlRes] = await Promise.all([
        fetch('/api/faculty/me', { headers }),
        fetch('/api/faculty/my-subjects', { headers }),
        fetch('/api/faculty/my-timetable', { headers }),
        fetch('/api/faculty/my-workload', { headers })
      ]);

      if (profRes.status === 404) {
        const errJson = await profRes.json().catch(() => ({}));
        setError(errJson.error || `No faculty record linked to email (${user?.email || user?.username}). Please contact the administrator.`);
        return;
      }

      if (!profRes.ok) {
        throw new Error('Failed to load faculty profile.');
      }

      setProfile(await profRes.json());
      if (subRes.ok) setSubjects(await subRes.json());
      if (ttRes.ok) setTimetable(await ttRes.json());
      if (wlRes.ok) setWorkload(await wlRes.json());
    } catch (e) {
      console.error(e);
      setError(e.message || 'Unable to load faculty dashboard.');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center' }}>
        <RefreshCw size={24} style={{ animation: 'spin 1s linear infinite', color: 'var(--accent-primary)' }} />
        <p style={{ marginTop: '0.75rem', color: 'var(--text-muted)' }}>Loading your faculty workspace…</p>
        <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: '2rem' }}>
        <div className="glass-panel" style={{ textAlign: 'center', padding: '3.5rem 2rem', maxWidth: '640px', margin: '0 auto' }}>
          <AlertCircle size={48} style={{ color: 'var(--accent-warning)', margin: '0 auto 1rem auto' }} />
          <h2 style={{ fontSize: '1.4rem', fontWeight: 800 }}>Faculty Profile Unlinked</h2>
          <p style={{ color: 'var(--text-muted)', marginTop: '0.75rem', lineHeight: 1.6, fontSize: '0.9rem' }}>
            {error}
          </p>
          <div style={{ marginTop: '1.5rem' }}>
            <button className="btn btn-primary" onClick={loadMyData}>
              <RefreshCw size={15} /> Retry
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Group timetable slots by day
  const timetableByDay = {};
  DAYS.forEach(d => { timetableByDay[d] = {}; });
  timetable.forEach(slot => {
    if (timetableByDay[slot.dayOfWeek]) {
      timetableByDay[slot.dayOfWeek][slot.periodNumber] = slot;
    }
  });

  return (
    <div>
      {/* Header */}
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 className="page-title">{profile?.name || user?.fullName}</h1>
          <p className="page-subtitle">
            {profile?.designation || 'Faculty Member'} &middot; {profile?.department || user?.department}
            {profile?.employeeId && <span> &middot; ID: {profile.employeeId}</span>}
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <Link to="/timetable" className="btn btn-secondary">
            <Clock size={16} /> My Timetable Grid
          </Link>
          <Link to="/lesson-plan" className="btn btn-primary">
            <BookMarked size={16} /> Lesson Plans
          </Link>
        </div>
      </div>

      {/* KPI Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1.25rem', marginBottom: '1.75rem' }}>
        {/* Sections */}
        <div className="glass-panel" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>My Sections</span>
            <Layers size={18} style={{ color: 'var(--accent-primary)' }} />
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 800, marginTop: '0.4rem', color: 'var(--text-main)' }}>
            {new Set(subjects.map(s => s.batchName)).size}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', marginTop: '0.2rem' }}>
            Across {subjects.length} subject offering{subjects.length === 1 ? '' : 's'}
          </div>
        </div>

        {/* Subjects */}
        <div className="glass-panel" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>My Subjects</span>
            <BookOpen size={18} style={{ color: 'var(--accent-secondary, #38bdf8)' }} />
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 800, marginTop: '0.4rem', color: 'var(--text-main)' }}>
            {new Set(subjects.map(s => s.subjectCode)).size}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', marginTop: '0.2rem' }}>
            Unique course codes
          </div>
        </div>

        {/* Weekly Hours vs Limit */}
        <div className="glass-panel" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>Weekly Hours vs Limit</span>
            <Clock size={18} style={{ color: workload?.isOverload ? 'var(--accent-danger, #ef4444)' : 'var(--accent-success, #22c55e)' }} />
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 800, marginTop: '0.4rem', color: workload?.isOverload ? 'var(--accent-danger)' : 'var(--text-main)' }}>
            {workload?.totalAssignedHours || profile?.currentLoad || 0} <span style={{ fontSize: '1rem', fontWeight: 500, color: 'var(--text-muted)' }}>/ {profile?.maxWeeklyLoad || 18} hrs</span>
          </div>
          <div style={{ fontSize: '0.75rem', color: workload?.isOverload ? 'var(--accent-danger)' : 'var(--text-dim)', marginTop: '0.2rem' }}>
            {workload?.isOverload ? '⚠️ Exceeds max load' : `${workload?.remainingCapacity || 0} hrs capacity left`}
          </div>
        </div>

        {/* Theory / Practical Split */}
        <div className="glass-panel" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>Theory / Practical Split</span>
            <BarChart2 size={18} style={{ color: '#a855f7' }} />
          </div>
          <div style={{ fontSize: '1.5rem', fontWeight: 800, marginTop: '0.4rem', color: 'var(--text-main)' }}>
            {workload?.theoryHours || profile?.theoryHours || 0}T &middot; {workload?.labHours || profile?.labHours || 0}L
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', marginTop: '0.2rem' }}>
            Theory vs Lab hours
          </div>
        </div>

        {/* Lesson Progress */}
        <div className="glass-panel" style={{ padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>Lesson Progress</span>
            <CheckCircle2 size={18} style={{ color: 'var(--accent-success)' }} />
          </div>
          <div style={{ fontSize: '1.8rem', fontWeight: 800, marginTop: '0.4rem', color: 'var(--accent-success)' }}>
            {profile?.lessonProgressPercentage || 0}%
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', marginTop: '0.2rem' }}>
            {profile?.completedSessions || 0} of {profile?.totalSessions || 0} sessions completed
          </div>
        </div>
      </div>

      {/* Progress Bar */}
      {profile?.totalSessions > 0 && (
        <div className="glass-panel" style={{ padding: '1.25rem', marginBottom: '1.75rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem', fontSize: '0.85rem' }}>
            <span style={{ fontWeight: 700 }}>Curriculum Delivery Progress</span>
            <span style={{ color: 'var(--accent-success)', fontWeight: 700 }}>{profile.lessonProgressPercentage}% Completed</span>
          </div>
          <div style={{ width: '100%', height: '10px', background: 'rgba(255,255,255,0.08)', borderRadius: '999px', overflow: 'hidden' }}>
            <div style={{ width: `${profile.lessonProgressPercentage}%`, height: '100%', background: 'linear-gradient(90deg, #22c55e, #10b981)', transition: 'width 0.4s ease' }} />
          </div>
        </div>
      )}

      {/* Two Column Layout: My Offerings & Weekly Schedule */}
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(320px, 1fr) minmax(360px, 1.4fr)', gap: '1.5rem' }}>
        {/* Left: My Assigned Subject Offerings */}
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 800, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <BookOpen size={18} style={{ color: 'var(--accent-primary)' }} />
              My Assigned Sections &amp; Subjects
            </h3>
            <span className="badge badge-warning">{subjects.length} Course Offerings</span>
          </div>

          {subjects.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '2.5rem 1rem', color: 'var(--text-muted)' }}>
              No subjects currently mapped to your faculty profile.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {subjects.map((sub, idx) => (
                <div
                  key={sub.offeringId || idx}
                  style={{
                    padding: '1rem',
                    borderRadius: '8px',
                    border: '1px solid var(--border-color)',
                    background: 'rgba(255,255,255,0.03)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '0.35rem'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontWeight: 800, fontSize: '0.95rem', color: 'var(--text-main)' }}>
                      {sub.subjectCode} &middot; {sub.subjectName}
                    </span>
                    <span className={`badge ${sub.type === 'LAB' ? 'badge-danger' : 'badge-warning'}`} style={{ fontSize: '0.7rem' }}>
                      {sub.type}
                    </span>
                  </div>
                  <div style={{ fontSize: '0.82rem', color: 'var(--text-muted)', display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
                    <span><strong>Section:</strong> {sub.batchName}</span>
                    <span><strong>Semester:</strong> Sem {sub.semester}</span>
                    <span><strong>Weekly Periods:</strong> {sub.weeklyLecturePeriods}T + {sub.weeklyPracticalPeriods}L</span>
                  </div>
                  <div style={{ marginTop: '0.4rem' }}>
                    <Link
                      to={`/lesson-plan?offeringId=${sub.offeringId}`}
                      style={{ fontSize: '0.8rem', color: 'var(--accent-primary)', textDecoration: 'none', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '0.25rem' }}
                    >
                      View Teaching Plan &rarr;
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Right: Weekly Schedule Preview */}
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 800, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Calendar size={18} style={{ color: 'var(--accent-secondary, #38bdf8)' }} />
              My Weekly Schedule
            </h3>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{timetable.length} teaching blocks</span>
          </div>

          {timetable.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '2.5rem 1rem', color: 'var(--text-muted)' }}>
              No timetable assignments published for your account yet.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.65rem' }}>
              {DAYS.map(day => {
                const daySlots = timetable.filter(t => t.dayOfWeek === day).sort((a, b) => a.periodNumber - b.periodNumber);
                if (daySlots.length === 0) return null;
                return (
                  <div key={day} style={{ padding: '0.75rem', borderRadius: '8px', background: 'rgba(255,255,255,0.02)', border: '1px solid var(--border-color)' }}>
                    <div style={{ fontWeight: 700, fontSize: '0.82rem', color: 'var(--accent-secondary, #38bdf8)', marginBottom: '0.5rem', textTransform: 'capitalize' }}>
                      {day.toLowerCase()}
                    </div>
                    <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                      {daySlots.map(s => (
                        <div
                          key={s.id}
                          style={{
                            padding: '0.4rem 0.65rem',
                            borderRadius: '6px',
                            background: s.isLabBlock ? 'rgba(239,68,68,0.15)' : 'rgba(168,85,247,0.15)',
                            border: `1px solid ${s.isLabBlock ? 'rgba(239,68,68,0.3)' : 'rgba(168,85,247,0.3)'}`,
                            fontSize: '0.75rem'
                          }}
                        >
                          <strong>P{s.periodNumber}{s.durationPeriods > 1 ? `-P${s.periodNumber + s.durationPeriods - 1}` : ''}</strong>: {s.subjectCode} ({s.batchName}) &middot; <span style={{ color: 'var(--text-muted)' }}>{s.roomNumber}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
