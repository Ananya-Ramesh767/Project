import React, { useState, useEffect, useMemo } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import {
  PlayCircle, RefreshCw, AlertCircle, CheckCircle2, RotateCcw,
  Calendar, BookOpen, FlaskConical, Rocket, GraduationCap, X,
  Upload, FileText, Check, CalendarDays, Sparkles, Layers
} from 'lucide-react';
import Modal from '../components/Modal';

const TYPE_META = {
  THEORY: { label: 'Theory', gradientClass: 'jewel-theory', icon: BookOpen },
  LAB: { label: 'Lab', gradientClass: 'jewel-lab', icon: FlaskConical },
  PROJECT: { label: 'Project', gradientClass: 'jewel-project', icon: Rocket },
  TUTORIAL: { label: 'Tutorial', gradientClass: 'jewel-tutorial', icon: GraduationCap }
};

const STATUS_BADGE = {
  PLANNED: 'badge-warning',
  COMPLETED: 'badge-success',
  RESCHEDULED: 'badge-danger'
};

const TYPE_SORT = { THEORY: 0, LAB: 1, PROJECT: 1, TUTORIAL: 2 };

function formatDate(iso) {
  const d = new Date(iso + 'T00:00:00');
  return d.toLocaleDateString('en-IN', { weekday: 'long', day: '2-digit', month: 'short', year: 'numeric' });
}

export default function GenerateLessonPlanPage({ user }) {
  const [searchParams] = useSearchParams();
  const queryOfferingId = searchParams.get('offeringId') || '';
  const queryCalendarId = searchParams.get('academicCalendarId') || '';

  const pickerMode = !queryOfferingId;

  const [offerings, setOfferings] = useState([]);
  const [calendars, setCalendars] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [pickerBlocked, setPickerBlocked] = useState(false);

  const [offeringId, setOfferingId] = useState(queryOfferingId);
  const [calendarId, setCalendarId] = useState(queryCalendarId);

  // Upload Schedule PDF Modal State
  const [showScheduleModal, setShowScheduleModal] = useState(false);
  const [scheduleFile, setScheduleFile] = useState(null);
  const [scheduleYear, setScheduleYear] = useState('2026-2027');
  const [scheduleSem, setScheduleSem] = useState(3);
  const [scheduleLoading, setScheduleLoading] = useState(false);
  const [scheduleError, setScheduleError] = useState('');
  const [scheduleSuccess, setScheduleSuccess] = useState('');

  // Upload Syllabus PDF Modal State
  const [showSyllabusModal, setShowSyllabusModal] = useState(false);
  const [syllabusSubjectId, setSyllabusSubjectId] = useState('');
  const [syllabusFile, setSyllabusFile] = useState(null);
  const [syllabusLoading, setSyllabusLoading] = useState(false);
  const [syllabusError, setSyllabusError] = useState('');
  const [syllabusSuccess, setSyllabusSuccess] = useState('');

  const [plan, setPlan] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [genConflict, setGenConflict] = useState(false);
  const [hasLoadedOnce, setHasLoadedOnce] = useState(false);

  const [rescheduling, setRescheduling] = useState(null);
  const [rescheduleDate, setRescheduleDate] = useState('');
  const [actionError, setActionError] = useState('');

  const authHeaders = { 'Authorization': `Bearer ${user.token}` };


  useEffect(() => {
    if (pickerMode) fetchPickerOptions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (offeringId && calendarId) loadPlan(offeringId, calendarId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [offeringId, calendarId]);

  useEffect(() => {
    const sel = offerings.find(o => String(o.id) === String(offeringId));
    if (sel?.subject?.id) setSyllabusSubjectId(String(sel.subject.id));
  }, [offeringId, offerings]);

  const fetchPickerOptions = async () => {
    try {
      const [oRes, cRes, sRes] = await Promise.all([
        fetch('/api/admin/offerings', { headers: authHeaders }),
        fetch('/api/admin/calendar', { headers: authHeaders }),
        fetch('/api/admin/subjects', { headers: authHeaders })
      ]);
      if (oRes.status === 403 || cRes.status === 403) {
        setPickerBlocked(true);
        return;
      }
      if (oRes.ok) setOfferings(await oRes.json());
      if (sRes.ok) setSubjects(await sRes.json());
      if (cRes.ok) {
        const cals = await cRes.json();
        setCalendars(cals);
        if (cals.length > 0) setCalendarId(String(cals[0].id));
      }
    } catch (e) { console.error(e); }
  };

  const loadPlan = async (offId, calId) => {
    setLoading(true);
    setLoadError('');
    setGenConflict(false);
    try {
      const res = await fetch(`/api/lesson-plan/${offId}?academicCalendarId=${calId}`, { headers: authHeaders });
      if (res.status === 404) {
        setPlan(null);
        setSessions([]);
        setLoadError('No lesson plan has been generated yet for this offering and calendar.');
        return;
      }
      if (!res.ok) throw new Error('Failed to load the lesson plan.');
      const data = await res.json();
      setPlan(data);
      setSessions(data.sessions || []);
    } catch (e) {
      setLoadError(e.message);
    } finally {
      setLoading(false);
      setHasLoadedOnce(true);
    }
  };

  const handleGenerate = async (force = false) => {
    if (!offeringId || !calendarId) return;
    if (force && !window.confirm('This will deactivate the current plan and every PLANNED session in it, then generate a fresh one. Continue?')) return;
    setLoading(true);
    setLoadError('');
    setGenConflict(false);
    try {
      const res = await fetch(
        `/api/lesson-plan/generate?subjectOfferingId=${offeringId}&academicCalendarId=${calendarId}&force=${force}`,
        { method: 'POST', headers: authHeaders }
      );
      const data = await res.json().catch(() => ({}));
      if (res.status === 409) { setGenConflict(true); return; }
      if (res.status === 400) { setLoadError(data.error || 'Cannot generate a lesson plan for this offering.'); return; }
      if (!res.ok) throw new Error(data.error || 'Failed to generate the lesson plan.');
      setPlan(data);
      setSessions(data.sessions || []);
    } catch (e) {
      setLoadError(e.message);
    } finally {
      setLoading(false);
      setHasLoadedOnce(true);
    }
  };

  const applyUpdate = (original, replacement) => {
    setSessions(prev => {
      const next = prev.map(s => (s.id === original.id ? original : s));
      if (replacement) next.push(replacement);
      return next;
    });
  };

  const handleComplete = async (session) => {
    setActionError('');
    try {
      const res = await fetch(`/api/lesson-plan/session/${session.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', ...authHeaders },
        body: JSON.stringify({ status: 'COMPLETED' })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || 'Failed to mark this session complete.');
      applyUpdate(data.original, data.replacement);
    } catch (e) { setActionError(e.message); }
  };

  const openReschedule = (sessionId) => {
    setRescheduling(sessionId);
    setRescheduleDate('');
    setActionError('');
  };

  const submitReschedule = async (session) => {
    if (!rescheduleDate) { setActionError('Pick a new date before confirming the reschedule.'); return; }
    setActionError('');
    try {
      const res = await fetch(`/api/lesson-plan/session/${session.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', ...authHeaders },
        body: JSON.stringify({ status: 'RESCHEDULED', rescheduledDate: rescheduleDate })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || 'Failed to reschedule this session.');
      applyUpdate(data.original, data.replacement);
      setRescheduling(null);
    } catch (e) { setActionError(e.message); }
  };

  const handleUploadSchedule = async (e) => {
    e.preventDefault();
    if (!scheduleFile) return;
    setScheduleLoading(true);
    setScheduleError('');
    setScheduleSuccess('');
    try {
      const formData = new FormData();
      formData.append('file', scheduleFile);
      formData.append('academicYear', scheduleYear || '2026-2027');
      formData.append('semester', scheduleSem || 3);
      const parseRes = await fetch('/api/admin/calendar/parse-schedule-document', {
        method: 'POST',
        headers: authHeaders,
        body: formData
      });
      if (!parseRes.ok) throw new Error('Failed to parse schedule document');
      const parsed = await parseRes.json();

      // Save as academic calendar
      const calRes = await fetch('/api/admin/calendar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders },
        body: JSON.stringify({
          academicYear: parsed.academicYear,
          semester: parsed.semester,
          startDate: parsed.startDate,
          endDate: parsed.endDate,
          holidays: []
        })
      });
      if (!calRes.ok) throw new Error('Failed to save academic calendar');
      const savedCal = await calRes.json();
      setScheduleSuccess(`Extracted and created calendar for Sem ${savedCal.semester} (${savedCal.startDate} to ${savedCal.endDate})!`);
      await fetchPickerOptions();
      setCalendarId(String(savedCal.id));
      setTimeout(() => {
        setShowScheduleModal(false);
        setScheduleSuccess('');
      }, 1200);
    } catch (err) {
      setScheduleError(err.message);
    } finally {
      setScheduleLoading(false);
    }
  };

  const handleUploadSyllabus = async (e) => {
    e.preventDefault();
    const selectedOffering = offerings.find(o => String(o.id) === String(offeringId));
    const targetSubjectId = syllabusSubjectId || selectedOffering?.subject?.id;
    if (!syllabusFile || !targetSubjectId) {
      setSyllabusError('Please choose a target subject and select a syllabus PDF file.');
      return;
    }
    setSyllabusLoading(true);
    setSyllabusError('');
    setSyllabusSuccess('');
    try {
      const formData = new FormData();
      formData.append('file', syllabusFile);
      const res = await fetch(`/api/lesson-plan/subjects/${targetSubjectId}/upload-syllabus-document`, {
        method: 'POST',
        headers: authHeaders,
        body: formData
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to extract syllabus document');
      setSyllabusSuccess(data.message || `Successfully parsed and saved ${data.totalUnits} syllabus units!`);
      setTimeout(() => {
        setShowSyllabusModal(false);
        setSyllabusSuccess('');
      }, 1200);
    } catch (err) {
      setSyllabusError(err.message);
    } finally {
      setSyllabusLoading(false);
    }
  };

  const groupedByDate = useMemo(() => {
    const map = {};
    sessions.forEach(s => {
      if (!map[s.sessionDate]) map[s.sessionDate] = [];
      map[s.sessionDate].push(s);
    });
    return Object.keys(map).sort().map(date => ({
      date,
      sessions: map[date].sort((a, b) => (TYPE_SORT[a.periodType] ?? 9) - (TYPE_SORT[b.periodType] ?? 9))
    }));
  }, [sessions]);

  const selectedOfferingObj = offerings.find(o => String(o.id) === String(offeringId));
  const offeringLabel = (o) =>
    `${o.batch?.batchName || 'Batch ?'} • ${o.subject?.subjectCode || ''} ${o.subject?.subjectName || ''} • ${o.assignedFaculty?.name || 'Unassigned faculty'}`;

  const calendarLabel = (c) => `${c.academicYear} — Sem ${c.semester} (${c.startDate} to ${c.endDate})`;

  return (
    <div>
      <div className="page-header" style={{ marginBottom: '1.5rem' }}>
        <div>
          <h1 className="page-title">Lesson Plan</h1>
          <p className="page-subtitle">Generate and manage the day-by-day teaching plan for a subject offering.</p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button className="btn btn-secondary" onClick={() => { setShowScheduleModal(true); setScheduleError(''); setScheduleSuccess(''); }}>
            <Calendar size={16} /> Upload Academic Schedule PDF
          </button>
          <button
            className="btn btn-secondary"
            onClick={() => {
              if (selectedOfferingObj?.subject?.id) setSyllabusSubjectId(String(selectedOfferingObj.subject.id));
              setShowSyllabusModal(true);
              setSyllabusError('');
              setSyllabusSuccess('');
            }}
          >
            <BookOpen size={16} /> Upload Syllabus PDF
          </button>
        </div>
      </div>

      {/* Centered Configuration Card */}
      {pickerMode && (
        <div className="glass-panel" style={{ maxWidth: '840px', margin: '0 auto 2.5rem auto', padding: '2rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '0.75rem' }}>
            <div>
              <h3 style={{ fontSize: '1.15rem', fontWeight: 800, margin: 0 }}>Configure Lesson Plan Parameters</h3>
              <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)', margin: '0.25rem 0 0 0' }}>
                Select the target batch course offering and semester calendar window to generate sequence.
              </p>
            </div>
            {selectedOfferingObj && (
              <span className="badge badge-success">
                {selectedOfferingObj.subject?.subjectCode} ({selectedOfferingObj.batch?.batchName})
              </span>
            )}
          </div>

          {pickerBlocked ? (
            <div className="alert alert-info" style={{ marginBottom: 0 }}>
              <AlertCircle size={18} />
              <span>
                Your account can't browse the full offerings list. Head to{' '}
                <Link to="/my-lesson-plans" style={{ color: 'inherit', fontWeight: 700 }}>My Lesson Plans</Link>{' '}
                to open a plan for one of your own subject offerings.
              </span>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
                  <label className="form-label" style={{ marginBottom: 0 }}>Subject Offering *</label>
                  {offeringId && (
                    <button
                      type="button"
                      className="btn btn-secondary"
                      style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem' }}
                      onClick={() => setShowSyllabusModal(true)}
                    >
                      <Upload size={12} /> Upload Syllabus PDF for this course
                    </button>
                  )}
                </div>
                <select className="form-select" value={offeringId} onChange={e => setOfferingId(e.target.value)}>
                  <option value="">-- Select Batch / Subject / Faculty --</option>
                  {offerings.map(o => <option key={o.id} value={o.id}>{offeringLabel(o)}</option>)}
                </select>
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
                  <label className="form-label" style={{ marginBottom: 0 }}>Year Calendar / Term Window *</label>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem' }}
                    onClick={() => setShowScheduleModal(true)}
                  >
                    <Upload size={12} /> Upload Academic Schedule PDF
                  </button>
                </div>
                <select className="form-select" value={calendarId} onChange={e => setCalendarId(e.target.value)}>
                  <option value="">-- Select Calendar --</option>
                  {calendars.map(c => <option key={c.id} value={c.id}>{calendarLabel(c)}</option>)}
                </select>
              </div>

              <div style={{ display: 'flex', justifyContent: 'center', marginTop: '0.5rem' }}>
                <button
                  className="btn btn-primary"
                  disabled={!offeringId || !calendarId || loading}
                  onClick={() => handleGenerate(false)}
                  style={{ minWidth: '220px', padding: '0.75rem 1.5rem', fontSize: '0.95rem' }}
                >
                  <PlayCircle size={18} /> {plan ? 'Regenerate Lesson Plan' : 'Generate Lesson Plan'}
                </button>
              </div>
            </div>
          )}
        </div>
      )}


      {loading && <p style={{ color: 'var(--text-muted)' }}>Loading…</p>}

      {genConflict && (
        <div className="alert alert-danger" style={{ alignItems: 'center' }}>
          <AlertCircle size={18} />
          <span style={{ flex: 1 }}>An active lesson plan already exists for this offering and calendar.</span>
          <button className="btn btn-secondary" style={{ padding: '0.4rem 0.9rem' }} onClick={() => handleGenerate(true)}>
            <RefreshCw size={14} /> Regenerate Anyway
          </button>
        </div>
      )}

      {loadError && !genConflict && (
        <div className="alert alert-danger">
          <AlertCircle size={18} />
          <span>
            {loadError}
            {loadError.toLowerCase().includes('syllabus') && (
              <> {' '}<Link to="/syllabus" style={{ color: 'inherit', fontWeight: 700 }}>Go to Syllabus Management →</Link></>
            )}
          </span>
        </div>
      )}

      {plan && (
        <div className="glass-panel" style={{ marginBottom: '2rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
            <div>
              <h3 style={{ fontSize: '1.1rem', fontWeight: 800 }}>
                {plan.subjectOffering?.subject?.subjectCode} — {plan.subjectOffering?.subject?.subjectName}
              </h3>
              <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '0.3rem' }}>
                {plan.subjectOffering?.batch?.batchName} · Faculty: {plan.subjectOffering?.assignedFaculty?.name || 'Unassigned'} ·
                {' '}Calendar {plan.academicCalendar?.academicYear} ({plan.academicCalendar?.startDate} to {plan.academicCalendar?.endDate})
              </p>
              {plan.generationNotes && (
                <p style={{ fontSize: '0.8rem', color: 'var(--text-dim)', marginTop: '0.5rem', fontStyle: 'italic' }}>{plan.generationNotes}</p>
              )}
            </div>
            {!pickerMode && null}
          </div>
        </div>
      )}

      {actionError && <div className="alert alert-danger">{actionError}</div>}

      {sessions.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          {groupedByDate.map(group => (
            <div key={group.date} className="glass-panel lesson-plan-day">
              <div className="lesson-plan-day-header">
                <Calendar size={16} />
                <span>{formatDate(group.date)}</span>
              </div>
              <div className="lesson-plan-day-sessions">
                {group.sessions.map(session => {
                  const meta = TYPE_META[session.periodType] || TYPE_META.THEORY;
                  const Icon = meta.icon;
                  const isPlanned = session.status === 'PLANNED';
                  return (
                    <div key={session.id} className={`session-card ${meta.gradientClass}`}>
                      <div className="session-card-top">
                        <span className="badge" style={{ background: 'rgba(255,255,255,0.14)', color: '#ffffff', border: '1px solid rgba(255,255,255,0.22)', backdropFilter: 'blur(4px)' }}>
                          <Icon size={13} /> {meta.label}
                        </span>
                        <span className={`badge ${STATUS_BADGE[session.status] || 'badge-warning'}`}>{session.status}</span>
                      </div>
                      <div className="session-card-body">
                        <strong>Unit {session.syllabusUnit?.unitNumber}</strong> — {session.syllabusUnit?.title}
                        <div style={{ fontSize: '0.78rem', color: 'rgba(255,255,255,0.75)', marginTop: '0.3rem' }}>
                          {session.periodsCount} period{session.periodsCount > 1 ? 's' : ''} · #{session.overallSequence}
                          {session.startTimeslot?.periodLabel && <> · {session.startTimeslot.periodLabel}</>}
                        </div>
                        {session.remarks && (
                          <div style={{ fontSize: '0.78rem', color: 'rgba(255,255,255,0.6)', marginTop: '0.3rem', fontStyle: 'italic' }}>
                            {session.remarks}
                          </div>
                        )}
                        {session.status === 'RESCHEDULED' && session.rescheduledTo && (
                          <div style={{ fontSize: '0.78rem', color: '#fca5a5', marginTop: '0.3rem', fontWeight: 600 }}>
                            Moved to {session.rescheduledTo.sessionDate}
                          </div>
                        )}
                      </div>

                      {isPlanned && rescheduling !== session.id && (
                        <div className="session-card-actions">
                          <button className="btn btn-secondary" style={{ padding: '0.35rem 0.65rem', fontSize: '0.78rem' }} onClick={() => handleComplete(session)}>
                            <CheckCircle2 size={13} /> Mark Completed
                          </button>
                          <button className="btn btn-secondary" style={{ padding: '0.35rem 0.65rem', fontSize: '0.78rem' }} onClick={() => openReschedule(session.id)}>
                            <RotateCcw size={13} /> Reschedule
                          </button>
                        </div>
                      )}

                      {rescheduling === session.id && (
                        <div className="session-card-actions" style={{ flexDirection: 'column', alignItems: 'stretch', gap: '0.5rem' }}>
                          <input type="date" className="form-input" style={{ padding: '0.5rem 0.75rem', fontSize: '0.82rem' }}
                            value={rescheduleDate} onChange={e => setRescheduleDate(e.target.value)} />
                          <div style={{ display: 'flex', gap: '0.5rem' }}>
                            <button className="btn btn-primary" style={{ padding: '0.35rem 0.65rem', fontSize: '0.78rem', flex: 1 }} onClick={() => submitReschedule(session)}>
                              Confirm
                            </button>
                            <button className="btn btn-secondary" style={{ padding: '0.35rem 0.5rem' }} onClick={() => setRescheduling(null)}>
                              <X size={13} />
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      )}

      {sessions.length === 0 && !loading && !loadError && (
        <div className="page-center-card" style={{ maxWidth: '780px', margin: '1rem auto 2.5rem auto', textAlign: 'center' }}>
          <Layers size={44} style={{ color: 'var(--accent-primary)', opacity: 0.6, margin: '0 auto 1rem auto' }} />
          <h3 style={{ fontSize: '1.2rem', fontWeight: 800, marginBottom: '0.5rem' }}>Ready to Generate Teaching Sequence</h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', maxWidth: '540px', margin: '0 auto 1.5rem auto' }}>
            Upload the current semester&apos;s academic schedule and the subject syllabus (with all units and topics), then click <strong>Generate Lesson Plan</strong> above to map out dates and timetable slots.
          </p>
          <div style={{ display: 'flex', justifyContent: 'center', gap: '1rem', flexWrap: 'wrap' }}>
            <button className="btn btn-secondary" onClick={() => { setShowScheduleModal(true); setScheduleError(''); setScheduleSuccess(''); }}>
              <Calendar size={15} /> Upload Academic Schedule PDF
            </button>
            <button
              className="btn btn-secondary"
              onClick={() => {
                if (selectedOfferingObj?.subject?.id) setSyllabusSubjectId(String(selectedOfferingObj.subject.id));
                setShowSyllabusModal(true);
                setSyllabusError('');
                setSyllabusSuccess('');
              }}
            >
              <BookOpen size={15} /> Upload Syllabus PDF
            </button>
          </div>
        </div>
      )}

      {/* Upload Academic Schedule PDF Modal */}
      <Modal isOpen={showScheduleModal} onClose={() => setShowScheduleModal(false)} title="Upload Academic Schedule PDF">
        {scheduleError && (
          <div className="alert alert-danger" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
            <AlertCircle size={16} /> <span>{scheduleError}</span>
          </div>
        )}
        {scheduleSuccess && (
          <div className="alert alert-success" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
            <Check size={16} /> <span>{scheduleSuccess}</span>
          </div>
        )}

        <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '1.25rem' }}>
          Upload the institutional term calendar or academic schedule document. Dates for commencement, end-of-classes, and semester holidays are extracted automatically.
        </p>

        <form onSubmit={handleUploadSchedule}>
          <div className="form-group">
            <label className="form-label">Schedule Document (PDF / TXT) *</label>
            <input
              type="file"
              className="form-input"
              accept=".pdf,.txt,.doc,.docx"
              onChange={e => setScheduleFile(e.target.files[0])}
              required
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label">Academic Year</label>
              <input
                type="text"
                className="form-input"
                value={scheduleYear}
                onChange={e => setScheduleYear(e.target.value)}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label">Semester</label>
              <input
                type="number"
                min="1"
                max="8"
                className="form-input"
                value={scheduleSem}
                onChange={e => setScheduleSem(Number(e.target.value))}
                required
              />
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setShowScheduleModal(false)}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={scheduleLoading}>
              {scheduleLoading ? 'Extracting Schedule…' : 'Extract & Set Calendar'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Upload Subject Syllabus PDF Modal */}
      <Modal isOpen={showSyllabusModal} onClose={() => setShowSyllabusModal(false)} title="Upload Subject Syllabus PDF (Units & Topics)">
        {syllabusError && (
          <div className="alert alert-danger" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
            <AlertCircle size={16} /> <span>{syllabusError}</span>
          </div>
        )}
        {syllabusSuccess && (
          <div className="alert alert-success" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
            <Check size={16} /> <span>{syllabusSuccess}</span>
          </div>
        )}

        <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '1.25rem' }}>
          Upload the complete syllabus PDF containing all Unit names, Modules, and topics. The topics will be sequenced automatically into daily lesson sessions.
        </p>

        <form onSubmit={handleUploadSyllabus}>
          <div className="form-group">
            <label className="form-label">Target Course / Subject *</label>
            <select
              className="form-select"
              value={syllabusSubjectId}
              onChange={e => setSyllabusSubjectId(e.target.value)}
              required
            >
              <option value="">-- Select Target Subject --</option>
              {subjects.map(s => (
                <option key={s.id} value={s.id}>
                  {s.subjectCode} — {s.subjectName} (Sem {s.semester} {s.department})
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Syllabus PDF Document *</label>
            <input
              type="file"
              className="form-input"
              accept=".pdf,.txt,.doc,.docx"
              onChange={e => setSyllabusFile(e.target.files[0])}
              required
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setShowSyllabusModal(false)}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={syllabusLoading}>
              {syllabusLoading ? 'Extracting Units & Topics…' : 'Extract & Save Units'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

