import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  BookOpen, Users, DoorOpen, Layers, BookMarked,
  Calendar, CheckCircle2, PlayCircle, Sliders, Clock,
  ArrowRight, ShieldCheck, Filter
} from 'lucide-react';
import { INSTITUTIONAL_PROGRAMMES } from '../utils/programmes';
import { getStoredAcademicContext, setStoredAcademicContext } from '../utils/academicContext';

const ACCENT = [
  'var(--accent-primary)',
  'var(--accent-secondary)',
  'var(--accent-success)',
  'var(--accent-warning)',
  'var(--session-tutorial-text)',
];

const STEPS = [
  { n: 1,  title: 'NEP Credit Rules',     icon: Sliders,       path: '/nep-rules',  desc: 'Set credit-to-hours conversion multipliers' },
  { n: 2,  title: 'Subjects & Credits',   icon: BookOpen,      path: '/subjects',   desc: 'Enter theory/lab/tutorial credits' },
  { n: 3,  title: 'Faculty Master',       icon: Users,         path: '/faculty',    desc: 'Manage faculty and subject eligibility' },
  { n: 4,  title: 'Room Master',          icon: DoorOpen,      path: '/rooms',      desc: 'Configure lecture halls and labs' },
  { n: 5,  title: 'Batches & Sections',   icon: Layers,        path: '/batches',    desc: 'Define student sections and strength' },
  { n: 6,  title: 'Subject Offerings',    icon: BookMarked,    path: '/offerings',  desc: 'Map subjects to sections with faculty' },
  { n: 7,  title: 'Year Calendar',        icon: Calendar,      path: '/calendar',   desc: 'Configure term dates and holidays' },
  { n: 8,  title: 'Timeslots',            icon: Clock,         path: '/timeslots',  desc: 'View college period schedule' },
  { n: 9,  title: 'Data Readiness',       icon: ShieldCheck,   path: '/readiness',  desc: 'Validate all data before scheduling' },
  { n: 10, title: 'Generate Timetable',   icon: PlayCircle,    path: '/generate',   desc: 'Trigger the constraint scheduler' },
];

export default function AdminDashboard({ user }) {
  const [selectedDept, setSelectedDept] = useState(() => getStoredAcademicContext().department || '');
  const [selectedSemester, setSelectedSemester] = useState(() => getStoredAcademicContext().semester || '');

  const [stats, setStats] = useState({ subjects: 0, faculty: 0, rooms: 0, batches: 0 });
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const handleContextChange = (e) => {
      const ctx = e.detail || getStoredAcademicContext();
      setSelectedDept(ctx.department || '');
      setSelectedSemester(ctx.semester || '');
    };
    window.addEventListener('academicContextChanged', handleContextChange);
    window.addEventListener('storage', handleContextChange);
    return () => {
      window.removeEventListener('academicContextChanged', handleContextChange);
      window.removeEventListener('storage', handleContextChange);
    };
  }, []);

  useEffect(() => {
    loadStats();
  }, [selectedDept, selectedSemester]);

  const handleDeptChange = (dept) => {
    setSelectedDept(dept);
    setStoredAcademicContext(dept, selectedSemester);
  };

  const handleSemesterChange = (sem) => {
    setSelectedSemester(sem);
    setStoredAcademicContext(selectedDept, sem);
  };

  const loadStats = async () => {
    // If context is unselected, show 0 rather than global totals
    if (!selectedDept || !selectedSemester) {
      setStats({ subjects: 0, faculty: 0, rooms: 0, batches: 0 });
      return;
    }

    setLoading(true);
    try {
      const h = { Authorization: `Bearer ${user?.token}` };
      const deptParam = encodeURIComponent(selectedDept);
      const semParam = encodeURIComponent(selectedSemester);

      const [s, f, r, b] = await Promise.all([
        fetch(`/api/admin/subjects?department=${deptParam}&semester=${semParam}`, { headers: h }).then(r => r.ok ? r.json() : []),
        fetch(`/api/admin/faculty?department=${deptParam}`, { headers: h }).then(r => r.ok ? r.json() : []),
        fetch('/api/admin/rooms', { headers: h }).then(r => r.ok ? r.json() : []),
        fetch(`/api/admin/batches?department=${deptParam}&semester=${semParam}`, { headers: h }).then(r => r.ok ? r.json() : [])
      ]);

      // Rooms and labs are institution-wide shared resources (Part 4 requirement 10)
      setStats({
        subjects: s.length,
        faculty: f.length,
        rooms: r.length,
        batches: b.length
      });
    } catch (e) {
      console.error('Failed to load dashboard stats:', e);
      setStats({ subjects: 0, faculty: 0, rooms: 0, batches: 0 });
    } finally {
      setLoading(false);
    }
  };

  const METRICS = [
    { label: 'Subjects',         value: stats.subjects,  icon: BookOpen,   color: ACCENT[0] },
    { label: 'Faculty Members',  value: stats.faculty,   icon: Users,      color: ACCENT[1] },
    { label: 'Rooms & Labs',     value: stats.rooms,     icon: DoorOpen,   color: ACCENT[2] },
    { label: 'Batches',          value: stats.batches,   icon: Layers,     color: ACCENT[3] },
    { label: 'Required Staff',   value: stats.batches,   icon: ShieldCheck, color: ACCENT[4] },
  ];

  return (
    <div>
      {/* Header */}
      <div className="page-header">
        <div>
          <h1 className="page-title">Admin Dashboard</h1>
          <p className="page-subtitle">
            Welcome, {user?.fullName || user?.username}. Select an academic context to view statistics and manage timetables.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
          <Link to="/readiness" className="btn btn-primary">
            <CheckCircle2 size={17} /> Readiness Check
          </Link>
        </div>
      </div>

      {/* Academic Context Selector */}
      <div className="glass-panel" style={{ marginBottom: '1.75rem', padding: '1.25rem', border: '1px solid rgba(168,85,247,0.25)' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem', flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <Filter size={16} style={{ color: 'var(--accent-primary)' }} />
              <label htmlFor="dept-select" style={{ fontSize: '0.85rem', fontWeight: 700, color: 'var(--text-main)', margin: 0 }}>
                Department / Programme:
              </label>
              <select
                id="dept-select"
                className="form-select"
                style={{ padding: '0.4rem 0.85rem', fontSize: '0.85rem', minWidth: '280px', maxWidth: '380px' }}
                value={selectedDept}
                onChange={e => handleDeptChange(e.target.value)}
              >
                <option value="">Select Department</option>
                {INSTITUTIONAL_PROGRAMMES.map(prog => (
                  <option key={prog} value={prog}>{prog}</option>
                ))}
              </select>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <label htmlFor="sem-select" style={{ fontSize: '0.85rem', fontWeight: 700, color: 'var(--text-main)', margin: 0 }}>
                Semester:
              </label>
              <select
                id="sem-select"
                className="form-select"
                style={{ padding: '0.4rem 0.85rem', fontSize: '0.85rem', minWidth: '220px' }}
                value={selectedSemester}
                onChange={e => handleSemesterChange(e.target.value)}
              >
                <option value="">Select Semester</option>
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

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
            <span className="badge badge-secondary" style={{ padding: '0.35rem 0.75rem', fontSize: '0.78rem' }}>
              Calendar Cycle: {new Date().getMonth() >= 6 ? 'Odd Semester (Jul–Dec)' : (new Date().getMonth() <= 4 ? 'Even Semester (Jan–May)' : 'Transition (Jun)')}
            </span>
            {selectedDept && selectedSemester ? (
              <span className="badge badge-success" style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem' }}>
                Active Context: {selectedDept} — Sem {selectedSemester}
              </span>
            ) : (
              <span style={{ fontSize: '0.82rem', color: 'var(--text-muted)', fontStyle: 'italic' }}>
                Select Department and Semester above to view metrics
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Metrics */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: '1rem', marginBottom: '2.5rem' }}>
        {METRICS.map(m => {
          const Icon = m.icon;
          return (
            <div key={m.label} className="stat-card" style={{ textAlign: 'center', alignItems: 'center', opacity: loading ? 0.6 : 1, transition: 'opacity 0.2s ease' }}>
              <div style={{ width: 44, height: 44, borderRadius: 'var(--radius-md)', background: `${m.color}22`, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '0.35rem' }}>
                <Icon size={22} style={{ color: m.color }} />
              </div>
              <div className="stat-value">{m.value}</div>
              <div className="stat-label">{m.label}</div>
            </div>
          );
        })}
      </div>

      {/* Workflow */}
      <h2 style={{ fontSize: '1.15rem', fontWeight: 800, marginBottom: '1.25rem', color: 'var(--text-main)' }}>
        Recommended Setup Workflow
      </h2>

      <div className="grid-2">
        {STEPS.map(step => {
          const Icon = step.icon;
          return (
            <Link key={step.n} to={step.path} style={{ textDecoration: 'none', color: 'inherit' }}>
              <div className="glass-panel" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '1rem', padding: '1.1rem 1.3rem', cursor: 'pointer' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.9rem' }}>
                  <div style={{ width: 40, height: 40, borderRadius: 'var(--radius-md)', background: 'rgba(168,85,247,0.14)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--accent-primary)', flexShrink: 0 }}>
                    <Icon size={20} />
                  </div>
                  <div>
                    <div style={{ fontSize: '0.92rem', fontWeight: 700 }}>{step.n}. {step.title}</div>
                    <div style={{ fontSize: '0.80rem', color: 'var(--text-dim)', marginTop: '0.18rem' }}>{step.desc}</div>
                  </div>
                </div>
                <ArrowRight size={16} style={{ color: 'var(--text-dim)', flexShrink: 0 }} />
              </div>
            </Link>
          );
        })}
      </div>
    </div>
  );
}
