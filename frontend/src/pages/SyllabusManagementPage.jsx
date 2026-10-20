import React, { useState, useEffect } from 'react';
import { BookOpen, Plus, Edit2, Trash2, ListChecks, Target, FlaskConical, Rocket, GraduationCap, X, Upload, Check, AlertCircle, FileText, Layers, FileCheck } from 'lucide-react';
import Modal from '../components/Modal';

const TRACK_ORDER = ['THEORY', 'LAB', 'PROJECT', 'TUTORIAL'];

const TRACK_META = {
  THEORY: { label: 'Theory Units', icon: BookOpen, color: 'var(--session-theory-text)' },
  LAB: { label: 'Lab Experiments', icon: FlaskConical, color: 'var(--session-lab-text)' },
  PROJECT: { label: 'Project Track', icon: Rocket, color: 'var(--session-project-text)' },
  TUTORIAL: { label: 'Tutorial Units', icon: GraduationCap, color: 'var(--session-tutorial-text)' }
};

const emptyUnitForm = {
  sessionType: 'THEORY',
  unitNumber: 1,
  title: '',
  topicsText: '',
  periodsAllotted: 6,
  periodsAllottedIsDerived: false,
  derivationNote: ''
};

export default function SyllabusManagementPage({ user }) {
  const [subjects, setSubjects] = useState([]);
  const [selectedSubjectId, setSelectedSubjectId] = useState('');

  const [units, setUnits] = useState([]);
  const [outcomes, setOutcomes] = useState([]);
  const [objectives, setObjectives] = useState([]);
  const [error, setError] = useState('');

  // Syllabus PDF upload & preview
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [uploadFile, setUploadFile] = useState(null);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [savingLoading, setSavingLoading] = useState(false);
  const [uploadSuccess, setUploadSuccess] = useState('');
  const [uploadError, setUploadError] = useState('');
  const [extractedPreview, setExtractedPreview] = useState(null);

  const [showUnitModal, setShowUnitModal] = useState(false);
  const [unitForm, setUnitForm] = useState(emptyUnitForm);
  const [editUnitId, setEditUnitId] = useState(null);

  const [showOutcomeModal, setShowOutcomeModal] = useState(false);
  const [outcomeForm, setOutcomeForm] = useState({ coNumber: 1, description: '' });
  const [editOutcomeId, setEditOutcomeId] = useState(null);

  const [showObjectiveModal, setShowObjectiveModal] = useState(false);
  const [objectiveForm, setObjectiveForm] = useState({ sequenceNumber: 1, description: '' });
  const [editObjectiveId, setEditObjectiveId] = useState(null);

  const authHeaders = { 'Authorization': `Bearer ${user.token}` };


  useEffect(() => { fetchSubjects(); }, []);
  useEffect(() => {
    if (selectedSubjectId) fetchSyllabusData(selectedSubjectId);
    else { setUnits([]); setOutcomes([]); setObjectives([]); }
  }, [selectedSubjectId]);

  const fetchSubjects = async () => {
    try {
      const res = await fetch('/api/admin/subjects', { headers: authHeaders });
      if (res.ok) {
        const data = await res.json();
        setSubjects(data);
        if (data.length > 0 && !selectedSubjectId) setSelectedSubjectId(String(data[0].id));
      }
    } catch (e) { console.error(e); }
  };

  const fetchSyllabusData = async (subjectId) => {
    setError('');
    try {
      const [uRes, oRes, jRes] = await Promise.all([
        fetch(`/api/lesson-plan/subjects/${subjectId}/units`, { headers: authHeaders }),
        fetch(`/api/lesson-plan/subjects/${subjectId}/outcomes`, { headers: authHeaders }),
        fetch(`/api/lesson-plan/subjects/${subjectId}/objectives`, { headers: authHeaders })
      ]);
      setUnits(uRes.ok ? await uRes.json() : []);
      setOutcomes(oRes.ok ? await oRes.json() : []);
      setObjectives(jRes.ok ? await jRes.json() : []);
    } catch (e) { console.error(e); }
  };

  const selectedSubject = subjects.find(s => String(s.id) === String(selectedSubjectId));

  // ---------------------------------------------------------------- units

  const unitsByTrack = TRACK_ORDER.reduce((acc, track) => {
    acc[track] = units.filter(u => u.sessionType === track).sort((a, b) => a.unitNumber - b.unitNumber);
    return acc;
  }, {});
  const activeTracks = TRACK_ORDER.filter(t => unitsByTrack[t].length > 0);
  const tracksToShow = activeTracks.length > 0 ? activeTracks : ['THEORY'];

  const openAddUnit = (sessionType) => {
    setEditUnitId(null);
    const existingForTrack = unitsByTrack[sessionType] || [];
    const nextNumber = existingForTrack.length > 0 ? Math.max(...existingForTrack.map(u => u.unitNumber)) + 1 : 1;
    setUnitForm({ ...emptyUnitForm, sessionType, unitNumber: nextNumber });
    setShowUnitModal(true);
  };

  const openEditUnit = (unit) => {
    setEditUnitId(unit.id);
    setUnitForm({
      sessionType: unit.sessionType,
      unitNumber: unit.unitNumber,
      title: unit.title,
      topicsText: (unit.topics || []).join('\n'),
      periodsAllotted: unit.periodsAllotted,
      periodsAllottedIsDerived: !!unit.periodsAllottedIsDerived,
      derivationNote: unit.derivationNote || ''
    });
    setShowUnitModal(true);
  };

  const handleUnitSubmit = async (e) => {
    e.preventDefault();
    setError('');
    const body = {
      sessionType: unitForm.sessionType,
      unitNumber: Number(unitForm.unitNumber),
      title: unitForm.title,
      topics: unitForm.topicsText.split('\n').map(t => t.trim()).filter(Boolean),
      periodsAllotted: Number(unitForm.periodsAllotted),
      periodsAllottedIsDerived: unitForm.periodsAllottedIsDerived,
      derivationNote: unitForm.periodsAllottedIsDerived ? unitForm.derivationNote : null
    };
    const url = editUnitId
      ? `/api/lesson-plan/subjects/${selectedSubjectId}/units/${editUnitId}`
      : `/api/lesson-plan/subjects/${selectedSubjectId}/units`;
    try {
      const res = await fetch(url, {
        method: editUnitId ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders },
        body: JSON.stringify(body)
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || 'Failed to save unit');
      }
      setShowUnitModal(false);
      fetchSyllabusData(selectedSubjectId);
    } catch (err) { setError(err.message); }
  };

  const handleDeleteUnit = async (unitId) => {
    if (!window.confirm('Deactivate this syllabus unit?')) return;
    try {
      await fetch(`/api/lesson-plan/subjects/${selectedSubjectId}/units/${unitId}`, { method: 'DELETE', headers: authHeaders });
      fetchSyllabusData(selectedSubjectId);
    } catch (e) { console.error(e); }
  };

  // -------------------------------------------------------------- outcomes

  const openAddOutcome = () => {
    setEditOutcomeId(null);
    const nextNumber = outcomes.length > 0 ? Math.max(...outcomes.map(o => o.coNumber)) + 1 : 1;
    setOutcomeForm({ coNumber: nextNumber, description: '' });
    setShowOutcomeModal(true);
  };

  const openEditOutcome = (o) => {
    setEditOutcomeId(o.id);
    setOutcomeForm({ coNumber: o.coNumber, description: o.description });
    setShowOutcomeModal(true);
  };

  const handleOutcomeSubmit = async (e) => {
    e.preventDefault();
    setError('');
    const body = { coNumber: Number(outcomeForm.coNumber), description: outcomeForm.description };
    const url = editOutcomeId
      ? `/api/lesson-plan/subjects/${selectedSubjectId}/outcomes/${editOutcomeId}`
      : `/api/lesson-plan/subjects/${selectedSubjectId}/outcomes`;
    try {
      const res = await fetch(url, {
        method: editOutcomeId ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders },
        body: JSON.stringify(body)
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || 'Failed to save course outcome');
      }
      setShowOutcomeModal(false);
      fetchSyllabusData(selectedSubjectId);
    } catch (err) { setError(err.message); }
  };

  const handleDeleteOutcome = async (id) => {
    if (!window.confirm('Deactivate this course outcome?')) return;
    try {
      await fetch(`/api/lesson-plan/subjects/${selectedSubjectId}/outcomes/${id}`, { method: 'DELETE', headers: authHeaders });
      fetchSyllabusData(selectedSubjectId);
    } catch (e) { console.error(e); }
  };

  // ------------------------------------------------------------ objectives

  const openAddObjective = () => {
    setEditObjectiveId(null);
    const nextNumber = objectives.length > 0 ? Math.max(...objectives.map(o => o.sequenceNumber)) + 1 : 1;
    setObjectiveForm({ sequenceNumber: nextNumber, description: '' });
    setShowObjectiveModal(true);
  };

  const openEditObjective = (o) => {
    setEditObjectiveId(o.id);
    setObjectiveForm({ sequenceNumber: o.sequenceNumber, description: o.description });
    setShowObjectiveModal(true);
  };

  const handleObjectiveSubmit = async (e) => {
    e.preventDefault();
    setError('');
    const body = { sequenceNumber: Number(objectiveForm.sequenceNumber), description: objectiveForm.description };
    const url = editObjectiveId
      ? `/api/lesson-plan/subjects/${selectedSubjectId}/objectives/${editObjectiveId}`
      : `/api/lesson-plan/subjects/${selectedSubjectId}/objectives`;
    try {
      const res = await fetch(url, {
        method: editObjectiveId ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders },
        body: JSON.stringify(body)
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || 'Failed to save course objective');
      }
      setShowObjectiveModal(false);
      fetchSyllabusData(selectedSubjectId);
    } catch (err) { setError(err.message); }
  };

  const handleDeleteObjective = async (id) => {
    if (!window.confirm('Deactivate this course objective?')) return;
    try {
      await fetch(`/api/lesson-plan/subjects/${selectedSubjectId}/objectives/${id}`, { method: 'DELETE', headers: authHeaders });
      fetchSyllabusData(selectedSubjectId);
    } catch (e) { console.error(e); }
  };

  const handleAnalyseSyllabus = async (e) => {
    e.preventDefault();
    if (!uploadFile || !selectedSubjectId) {
      setUploadError('Please select a syllabus PDF and ensure a subject is selected.');
      return;
    }
    setUploadLoading(true);
    setUploadError('');
    setUploadSuccess('');
    try {
      const formData = new FormData();
      formData.append('file', uploadFile);
      const res = await fetch(`/api/lesson-plan/subjects/${selectedSubjectId}/preview-syllabus-document`, {
        method: 'POST',
        headers: authHeaders,
        body: formData
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to extract syllabus document');
      if (!data.units || data.units.length === 0) {
        throw new Error('No syllabus units or modules could be detected in this document.');
      }
      setExtractedPreview(data);
    } catch (err) {
      setUploadError(err.message);
    } finally {
      setUploadLoading(false);
    }
  };

  const handleConfirmAndSaveUnits = async () => {
    if (!extractedPreview || !extractedPreview.units || !selectedSubjectId) return;
    setSavingLoading(true);
    setUploadError('');
    setUploadSuccess('');
    try {
      const res = await fetch(`/api/lesson-plan/subjects/${selectedSubjectId}/save-syllabus-units`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders },
        body: JSON.stringify({
          units: extractedPreview.units || [],
          courseObjectives: extractedPreview.courseObjectives || [],
          courseOutcomes: extractedPreview.courseOutcomes || []
        })
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to save syllabus units');
      setUploadSuccess(data.message || `Successfully saved ${data.savedCount || extractedPreview.units.length} units!`);
      fetchSyllabusData(selectedSubjectId);
      setTimeout(() => {
        setShowUploadModal(false);
        setExtractedPreview(null);
        setUploadFile(null);
        setUploadSuccess('');
      }, 900);
    } catch (err) {
      setUploadError(err.message);
    } finally {
      setSavingLoading(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Syllabus Management</h1>
          <p className="page-subtitle">Define ordered units, topics, course outcomes and objectives per subject for lesson plan generation.</p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem' }}>
          <button
            className="btn btn-primary"
            disabled={!selectedSubjectId}
            onClick={() => { setShowUploadModal(true); setUploadError(''); setUploadSuccess(''); }}
          >
            <Upload size={16} /> Upload Syllabus PDF
          </button>
        </div>
      </div>


      <div className="glass-panel" style={{ marginBottom: '2rem' }}>
        <label className="form-label">Subject</label>
        <select className="form-select" value={selectedSubjectId} onChange={e => setSelectedSubjectId(e.target.value)}>
          <option value="">-- Select a Subject --</option>
          {subjects.map(s => (
            <option key={s.id} value={s.id}>{s.subjectCode} — {s.subjectName} ({s.department} Sem {s.semester})</option>
          ))}
        </select>
        {selectedSubject && (
          <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '0.75rem' }}>
            Periods/Week — L: {selectedSubject.weeklyLecturePeriods ?? '—'} · T: {selectedSubject.weeklyTutorialPeriods ?? '—'} ·
            {' '}P: {selectedSubject.weeklyPracticalPeriods ?? '—'} · R: {selectedSubject.weeklyProjectPeriods ?? '—'}
            {selectedSubject.curriculumCredits != null && <> · C: {selectedSubject.curriculumCredits}</>}
          </p>
        )}
        {subjects.length === 0 && (
          <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '0.75rem' }}>
            No subjects found. Add one on the Subjects &amp; Credits page first.
          </p>
        )}
      </div>

      {error && <div className="alert alert-danger">{error}</div>}

      {selectedSubjectId && (
        <>
          {/* Units grouped by track */}
          <div style={{ display: 'grid', gridTemplateColumns: `repeat(${tracksToShow.length}, 1fr)`, gap: '1.5rem', marginBottom: '2rem' }}>
            {tracksToShow.map(track => {
              const meta = TRACK_META[track];
              const Icon = meta.icon;
              return (
                <div className="glass-panel" key={track}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                    <h3 style={{ fontSize: '1rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '0.5rem', color: meta.color }}>
                      <Icon size={18} /> {meta.label}
                    </h3>
                    <button className="btn btn-secondary" style={{ padding: '0.4rem 0.75rem' }} onClick={() => openAddUnit(track)}>
                      <Plus size={14} /> Add Unit
                    </button>
                  </div>

                  {unitsByTrack[track].length === 0 && (
                    <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>No units entered for this track yet.</p>
                  )}

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                    {unitsByTrack[track].map(unit => (
                      <div key={unit.id} style={{ background: 'rgba(0,0,0,0.3)', borderRadius: 'var(--radius-md)', padding: '1rem' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                          <div>
                            <div style={{ fontWeight: 700, fontSize: '0.9rem' }}>
                              Unit {unit.unitNumber} — {unit.title}
                            </div>
                            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '0.35rem' }}>
                              {unit.periodsAllotted} periods
                              {unit.periodsAllottedIsDerived && (
                                <span className="badge badge-warning" style={{ marginLeft: '0.5rem' }}>Derived</span>
                              )}
                            </div>
                            {unit.topics && unit.topics.length > 0 && (
                              <ul style={{ marginTop: '0.5rem', marginLeft: '1.1rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                                {unit.topics.map((t, i) => <li key={i}>{t}</li>)}
                              </ul>
                            )}
                            {unit.periodsAllottedIsDerived && unit.derivationNote && (
                              <p style={{ fontSize: '0.75rem', color: 'var(--text-dim)', marginTop: '0.4rem', fontStyle: 'italic' }}>
                                {unit.derivationNote}
                              </p>
                            )}
                          </div>
                          <div style={{ display: 'flex', gap: '0.4rem', flexShrink: 0 }}>
                            <button className="btn btn-secondary" style={{ padding: '0.35rem 0.5rem' }} onClick={() => openEditUnit(unit)}>
                              <Edit2 size={13} />
                            </button>
                            <button className="btn btn-danger" style={{ padding: '0.35rem 0.5rem' }} onClick={() => handleDeleteUnit(unit.id)}>
                              <Trash2 size={13} />
                            </button>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>

          {/* Course Outcomes + Objectives */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
            <div className="glass-panel">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                <h3 style={{ fontSize: '1rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Target size={18} style={{ color: 'var(--accent-success)' }} /> Course Outcomes
                </h3>
                <button className="btn btn-secondary" style={{ padding: '0.4rem 0.75rem' }} onClick={openAddOutcome}>
                  <Plus size={14} /> Add CO
                </button>
              </div>
              {outcomes.length === 0 && <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>No course outcomes entered yet.</p>}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                {outcomes.sort((a, b) => a.coNumber - b.coNumber).map(o => (
                  <div key={o.id} style={{ display: 'flex', justifyContent: 'space-between', gap: '0.75rem', background: 'rgba(0,0,0,0.3)', borderRadius: 'var(--radius-md)', padding: '0.75rem 1rem' }}>
                    <div style={{ fontSize: '0.85rem' }}><strong>CO{o.coNumber}</strong> — {o.description}</div>
                    <div style={{ display: 'flex', gap: '0.35rem', flexShrink: 0 }}>
                      <button className="btn btn-secondary" style={{ padding: '0.3rem 0.45rem' }} onClick={() => openEditOutcome(o)}><Edit2 size={12} /></button>
                      <button className="btn btn-danger" style={{ padding: '0.3rem 0.45rem' }} onClick={() => handleDeleteOutcome(o.id)}><Trash2 size={12} /></button>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="glass-panel">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                <h3 style={{ fontSize: '1rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <ListChecks size={18} style={{ color: 'var(--accent-secondary)' }} /> Course Objectives
                </h3>
                <button className="btn btn-secondary" style={{ padding: '0.4rem 0.75rem' }} onClick={openAddObjective}>
                  <Plus size={14} /> Add Objective
                </button>
              </div>
              {objectives.length === 0 && <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>No course objectives entered yet.</p>}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                {objectives.sort((a, b) => a.sequenceNumber - b.sequenceNumber).map(o => (
                  <div key={o.id} style={{ display: 'flex', justifyContent: 'space-between', gap: '0.75rem', background: 'rgba(0,0,0,0.3)', borderRadius: 'var(--radius-md)', padding: '0.75rem 1rem' }}>
                    <div style={{ fontSize: '0.85rem' }}>{o.sequenceNumber}. {o.description}</div>
                    <div style={{ display: 'flex', gap: '0.35rem', flexShrink: 0 }}>
                      <button className="btn btn-secondary" style={{ padding: '0.3rem 0.45rem' }} onClick={() => openEditObjective(o)}><Edit2 size={12} /></button>
                      <button className="btn btn-danger" style={{ padding: '0.3rem 0.45rem' }} onClick={() => handleDeleteObjective(o.id)}><Trash2 size={12} /></button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </>
      )}

      {/* Unit Modal */}
      <Modal
        isOpen={showUnitModal}
        onClose={() => setShowUnitModal(false)}
        title={editUnitId ? 'Edit Syllabus Unit' : 'Add Syllabus Unit'}
      >
        <form onSubmit={handleUnitSubmit}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label">Session Type *</label>
              <select className="form-select" value={unitForm.sessionType} onChange={e => setUnitForm({ ...unitForm, sessionType: e.target.value })}>
                <option value="THEORY">THEORY</option>
                <option value="LAB">LAB</option>
                <option value="PROJECT">PROJECT</option>
                <option value="TUTORIAL">TUTORIAL</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Unit Number *</label>
              <input type="number" className="form-input" min="1" value={unitForm.unitNumber}
                onChange={e => setUnitForm({ ...unitForm, unitNumber: e.target.value })} required />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Title *</label>
            <input type="text" className="form-input" value={unitForm.title}
              onChange={e => setUnitForm({ ...unitForm, title: e.target.value })} required />
          </div>

          <div className="form-group">
            <label className="form-label">Topics (one per line)</label>
            <textarea className="form-input" rows={4} value={unitForm.topicsText}
              onChange={e => setUnitForm({ ...unitForm, topicsText: e.target.value })}
              placeholder={'e.g.\nAgents & problem solving\nSearch strategies\nConstraint satisfaction problems'} />
          </div>

          <div className="form-group">
            <label className="form-label">Periods Allotted *</label>
            <input type="number" className="form-input" min="1" value={unitForm.periodsAllotted}
              onChange={e => setUnitForm({ ...unitForm, periodsAllotted: e.target.value })} required />
          </div>

          <div className="form-group" style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <input type="checkbox" id="derivedFlag" checked={unitForm.periodsAllottedIsDerived}
              onChange={e => setUnitForm({ ...unitForm, periodsAllottedIsDerived: e.target.checked })} />
            <label htmlFor="derivedFlag" style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              Period count was computed (not printed directly in the syllabus document)
            </label>
          </div>

          {unitForm.periodsAllottedIsDerived && (
            <div className="form-group">
              <label className="form-label">Derivation Note</label>
              <input type="text" className="form-input" value={unitForm.derivationNote}
                onChange={e => setUnitForm({ ...unitForm, derivationNote: e.target.value })}
                placeholder="e.g. Source gives only a 30-period track total for 6 experiments; split evenly." />
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setShowUnitModal(false)}>Cancel</button>
            <button type="submit" className="btn btn-primary">{editUnitId ? 'Save Changes' : 'Add Unit'}</button>
          </div>
        </form>
      </Modal>

      {/* Outcome Modal */}
      <Modal
        isOpen={showOutcomeModal}
        onClose={() => setShowOutcomeModal(false)}
        title={editOutcomeId ? 'Edit Course Outcome' : 'Add Course Outcome'}
      >
        <form onSubmit={handleOutcomeSubmit}>
          <div className="form-group">
            <label className="form-label">CO Number *</label>
            <input type="number" className="form-input" min="1" value={outcomeForm.coNumber}
              onChange={e => setOutcomeForm({ ...outcomeForm, coNumber: e.target.value })} required />
          </div>
          <div className="form-group">
            <label className="form-label">Description *</label>
            <textarea className="form-input" rows={3} value={outcomeForm.description}
              onChange={e => setOutcomeForm({ ...outcomeForm, description: e.target.value })} required />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setShowOutcomeModal(false)}>Cancel</button>
            <button type="submit" className="btn btn-primary">{editOutcomeId ? 'Save Changes' : 'Add Outcome'}</button>
          </div>
        </form>
      </Modal>

      {/* Objective Modal */}
      <Modal
        isOpen={showObjectiveModal}
        onClose={() => setShowObjectiveModal(false)}
        title={editObjectiveId ? 'Edit Course Objective' : 'Add Course Objective'}
      >
        <form onSubmit={handleObjectiveSubmit}>
          <div className="form-group">
            <label className="form-label">Sequence Number *</label>
            <input type="number" className="form-input" min="1" value={objectiveForm.sequenceNumber}
              onChange={e => setObjectiveForm({ ...objectiveForm, sequenceNumber: e.target.value })} required />
          </div>
          <div className="form-group">
            <label className="form-label">Description *</label>
            <textarea className="form-input" rows={3} value={objectiveForm.description}
              onChange={e => setObjectiveForm({ ...objectiveForm, description: e.target.value })} required />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setShowObjectiveModal(false)}>Cancel</button>
            <button type="submit" className="btn btn-primary">{editObjectiveId ? 'Save Changes' : 'Add Objective'}</button>
          </div>
        </form>
      </Modal>

      {/* Upload Syllabus PDF Modal */}
      <Modal
        isOpen={showUploadModal}
        onClose={() => { setShowUploadModal(false); setExtractedPreview(null); }}
        title={extractedPreview ? "Review Extracted Syllabus Before Confirmation" : "Upload Syllabus PDF (Units & Topics)"}
        maxWidth={extractedPreview ? "900px" : "580px"}
      >
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

        {!extractedPreview ? (
          <div>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '1.25rem' }}>
              Target Subject: <strong>{selectedSubject?.subjectCode} — {selectedSubject?.subjectName}</strong>
              <br />
              Upload the complete syllabus PDF. The system will analyse all pages, identify subject details, unit structures, and extract complete topics for review before confirmation.
            </p>

            <form onSubmit={handleAnalyseSyllabus}>
              <div className="form-group">
                <label className="form-label">Syllabus PDF Document *</label>
                <input
                  type="file"
                  className="form-input"
                  accept=".pdf,.txt,.doc,.docx"
                  onChange={e => setUploadFile(e.target.files[0])}
                  required
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1.5rem' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowUploadModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={uploadLoading}>
                  {uploadLoading ? 'Analysing Entire Document…' : 'Analyse Syllabus Document'}
                </button>
              </div>
            </form>
          </div>
        ) : (
          <div>
            {/* Validation Metrics Grid */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: '0.75rem', marginBottom: '1.25rem' }}>
              <div style={{ background: 'rgba(255, 255, 255, 0.05)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '0.65rem', textAlign: 'center' }}>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginBottom: '0.2rem' }}>Pages</div>
                <div style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--accent-primary)' }}>{extractedPreview.totalPagesProcessed || 1}</div>
              </div>
              <div style={{ background: 'rgba(255, 255, 255, 0.05)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '0.65rem', textAlign: 'center' }}>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginBottom: '0.2rem' }}>Units</div>
                <div style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--accent-secondary)' }}>{extractedPreview.unitsIdentified || extractedPreview.units?.length || 0}</div>
              </div>
              <div style={{ background: 'rgba(255, 255, 255, 0.05)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '0.65rem', textAlign: 'center' }}>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginBottom: '0.2rem' }}>Topics</div>
                <div style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--accent-success)' }}>{extractedPreview.topicsExtracted || 0}</div>
              </div>
              <div style={{ background: 'rgba(255, 255, 255, 0.05)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '0.65rem', textAlign: 'center' }}>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginBottom: '0.2rem' }}>Objectives</div>
                <div style={{ fontSize: '1.15rem', fontWeight: 700, color: '#38bdf8' }}>{extractedPreview.courseObjectives?.length || extractedPreview.objectivesIdentified || 0}</div>
              </div>
              <div style={{ background: 'rgba(255, 255, 255, 0.05)', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '0.65rem', textAlign: 'center' }}>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginBottom: '0.2rem' }}>Course Outcomes</div>
                <div style={{ fontSize: '1.15rem', fontWeight: 700, color: '#f59e0b' }}>{extractedPreview.courseOutcomes?.length || extractedPreview.outcomesIdentified || 0}</div>
              </div>
            </div>

            {/* Review Warnings / Advisories */}
            {extractedPreview.reviewWarnings && extractedPreview.reviewWarnings.length > 0 && (
              <div className="alert alert-warning" style={{ marginBottom: '1rem', padding: '0.65rem 0.9rem', fontSize: '0.85rem' }}>
                <div style={{ fontWeight: 600, marginBottom: '0.25rem', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                  <AlertCircle size={15} /> <span>Extraction Notices for Review:</span>
                </div>
                <ul style={{ margin: 0, paddingLeft: '1.25rem' }}>
                  {extractedPreview.reviewWarnings.map((w, idx) => <li key={idx}>{w}</li>)}
                </ul>
              </div>
            )}

            {/* Units & Topics Preview */}
            <div style={{ maxHeight: '250px', overflowY: 'auto', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '0.75rem', marginBottom: '1rem', background: 'rgba(0,0,0,0.2)' }}>
              <div style={{ fontSize: '0.85rem', fontWeight: 700, marginBottom: '0.6rem', color: 'var(--accent-primary)' }}>
                Syllabus Units &amp; Topics ({extractedPreview.units?.length || 0}):
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.65rem' }}>
                {extractedPreview.units?.map((u, i) => (
                  <div key={i} style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)', borderRadius: '6px', padding: '0.65rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.35rem' }}>
                      <span style={{ fontWeight: 700, fontSize: '0.88rem', color: 'var(--accent-primary)' }}>
                        Unit {u.unitNumber}: {u.title}
                      </span>
                      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                        <span className="badge badge-info" style={{ fontSize: '0.72rem' }}>{u.sessionType || 'THEORY'}</span>
                        <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{u.periodsAllotted || 9} Periods</span>
                      </div>
                    </div>
                    {u.topics && u.topics.length > 0 ? (
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.3rem', marginTop: '0.35rem' }}>
                        {u.topics.map((t, tidx) => (
                          <span key={tidx} style={{ background: 'rgba(255,255,255,0.06)', borderRadius: '4px', padding: '0.15rem 0.45rem', fontSize: '0.75rem', color: 'var(--text-bright)' }}>
                            {t}
                          </span>
                        ))}
                      </div>
                    ) : (
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', fontStyle: 'italic' }}>No detailed sub-topics identified</div>
                    )}
                  </div>
                ))}
              </div>
            </div>

            {/* Course Objectives Preview */}
            {extractedPreview.courseObjectives && extractedPreview.courseObjectives.length > 0 && (
              <div style={{ maxHeight: '180px', overflowY: 'auto', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '0.75rem', marginBottom: '1rem', background: 'rgba(0,0,0,0.2)' }}>
                <div style={{ fontSize: '0.85rem', fontWeight: 700, marginBottom: '0.5rem', color: '#38bdf8' }}>
                  Extracted Course Objectives ({extractedPreview.courseObjectives.length}):
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                  {extractedPreview.courseObjectives.map((obj, i) => (
                    <div key={i} style={{ display: 'flex', gap: '0.5rem', fontSize: '0.82rem', background: 'rgba(255,255,255,0.02)', padding: '0.4rem 0.6rem', borderRadius: '4px' }}>
                      <span className="badge badge-info" style={{ height: 'fit-content', fontSize: '0.7rem' }}>Obj {obj.sequenceNumber || (i + 1)}</span>
                      <span style={{ color: 'var(--text-primary)' }}>{obj.description}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Course Outcomes Preview */}
            {extractedPreview.courseOutcomes && extractedPreview.courseOutcomes.length > 0 && (
              <div style={{ maxHeight: '180px', overflowY: 'auto', border: '1px solid var(--border-color)', borderRadius: '8px', padding: '0.75rem', marginBottom: '1.25rem', background: 'rgba(0,0,0,0.2)' }}>
                <div style={{ fontSize: '0.85rem', fontWeight: 700, marginBottom: '0.5rem', color: '#f59e0b' }}>
                  Extracted Course Outcomes ({extractedPreview.courseOutcomes.length}):
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                  {extractedPreview.courseOutcomes.map((co, i) => (
                    <div key={i} style={{ display: 'flex', gap: '0.5rem', fontSize: '0.82rem', background: 'rgba(255,255,255,0.02)', padding: '0.4rem 0.6rem', borderRadius: '4px' }}>
                      <span className="badge badge-warning" style={{ height: 'fit-content', fontSize: '0.7rem' }}>CO{co.coNumber || (i + 1)}</span>
                      <span style={{ color: 'var(--text-primary)' }}>{co.description}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '1.25rem' }}>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setExtractedPreview(null)}
                disabled={savingLoading}
              >
                Re-upload / Back
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={handleConfirmAndSaveUnits}
                disabled={savingLoading}
                style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}
              >
                <Check size={16} />
                {savingLoading ? 'Saving Syllabus...' : 'Confirm and Save'}
              </button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}

