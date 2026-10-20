import React, { useState, useEffect, useCallback } from 'react';
import { BookMarked, Plus, Trash2, Edit2, Check, X, ChevronDown, Filter, Sparkles, AlertTriangle } from 'lucide-react';
import Modal from '../components/Modal';
import { INSTITUTIONAL_PROGRAMMES } from '../utils/programmes';
import { SEMESTER_OPTIONS, getStoredAcademicContext, setStoredAcademicContext } from '../utils/academicContext';

export default function OfferingManagementPage({ user }) {
  const [offerings, setOfferings] = useState([]);
  const [batches, setBatches] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [allFaculty, setAllFaculty] = useState([]);

  // Scope filters
  const initialContext = getStoredAcademicContext();
  const [filterDept, setFilterDept] = useState(() => initialContext.department || '');
  const [filterSem, setFilterSem] = useState(() => initialContext.semester || '');

  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({ batchId: '', subjectId: '', assignedFacultyId: '', academicYear: '2026-2027' });
  const [formError, setFormError] = useState('');

  // Inline faculty edit state: offeringId -> facultyId being edited
  const [editingFaculty, setEditingFaculty] = useState(null); // { offeringId, currentFacultyId }
  const [editFacultyValue, setEditFacultyValue] = useState('');
  const [eligibleFacultyCache, setEligibleFacultyCache] = useState({}); // subjectId -> faculty[]

  const [autoAssigning, setAutoAssigning] = useState(false);
  const [msg, setMsg] = useState(null);

  const headers = { Authorization: `Bearer ${user.token}` };

  const fetchData = useCallback(async () => {
    try {
      const params = new URLSearchParams();
      if (filterDept) params.append('department', filterDept);
      if (filterSem)  params.append('semester', filterSem);

      const [oRes, bRes, sRes, fRes] = await Promise.all([
        fetch(`/api/admin/offerings?${params.toString()}`, { headers }),
        fetch(`/api/admin/batches?${params.toString()}`, { headers }),
        fetch(`/api/admin/subjects?${params.toString()}`, { headers }),
        fetch(`/api/admin/faculty${filterDept ? `?department=${encodeURIComponent(filterDept)}` : ''}`, { headers })
      ]);

      if (oRes.ok) setOfferings(await oRes.json());
      if (bRes.ok) setBatches(await bRes.json());
      if (sRes.ok) setSubjects(await sRes.json());
      if (fRes.ok) setAllFaculty(await fRes.json());
    } catch (e) { console.error(e); }
  }, [filterDept, filterSem]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const handleDeptChange = (val) => {
    setFilterDept(val);
    localStorage.setItem('academicContext_dept', val);
  };
  const handleSemChange = (val) => {
    setFilterSem(val);
    localStorage.setItem('academicContext_semester', val);
  };

  // Load eligible faculty for a subject (cached)
  const loadEligibleFaculty = async (subjectId) => {
    if (eligibleFacultyCache[subjectId]) return eligibleFacultyCache[subjectId];
    try {
      const params = new URLSearchParams();
      if (filterDept) params.append('department', filterDept);
      const res = await fetch(`/api/admin/offerings/eligible-faculty/${subjectId}?${params.toString()}`, { headers });
      if (res.ok) {
        const data = await res.json();
        setEligibleFacultyCache(prev => ({ ...prev, [subjectId]: data }));
        return data;
      }
    } catch (e) { console.error(e); }
    return allFaculty.map(f => ({ ...f, eligible: false }));
  };

  const startEditFaculty = async (offering) => {
    const fac = await loadEligibleFaculty(offering.subject?.id);
    setEligibleFacultyCache(prev => ({ ...prev, [offering.subject?.id]: fac }));
    setEditingFaculty({ offeringId: offering.id, subjectId: offering.subject?.id });
    setEditFacultyValue(offering.assignedFaculty?.id?.toString() || '');
  };

  const saveFacultyEdit = async () => {
    if (!editingFaculty) return;
    try {
      const res = await fetch(`/api/admin/offerings/${editingFaculty.offeringId}`, {
        method: 'PUT',
        headers: { ...headers, 'Content-Type': 'application/json' },
        body: JSON.stringify({ assignedFacultyId: editFacultyValue || null })
      });
      if (res.ok) {
        setEditingFaculty(null);
        setEditFacultyValue('');
        fetchData();
      }
    } catch (e) { console.error(e); }
  };

  const cancelEdit = () => {
    setEditingFaculty(null);
    setEditFacultyValue('');
  };

  const handleAutoAssign = async () => {
    setAutoAssigning(true);
    setMsg(null);
    try {
      const params = new URLSearchParams({ academicYear: '2026-2027' });
      if (filterDept) params.append('department', filterDept);
      if (filterSem)  params.append('semester', filterSem);

      const res = await fetch(`/api/admin/offerings/auto-assign?${params.toString()}`, {
        method: 'POST', headers
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Failed to auto-assign');
      setMsg({
        type: 'success',
        text: `Auto-assign complete: ${data.offeringsCreated} offerings created, ${data.offeringsUpdated} updated across ${data.totalBatches} sections.`
      });
      fetchData();
    } catch (err) {
      setMsg({ type: 'danger', text: err.message });
    } finally {
      setAutoAssigning(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError('');
    try {
      const res = await fetch('/api/admin/offerings', {
        method: 'POST',
        headers: { ...headers, 'Content-Type': 'application/json' },
        body: JSON.stringify(form)
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to create offering');
      }
      setShowModal(false);
      setForm({ batchId: '', subjectId: '', assignedFacultyId: '', academicYear: '2026-2027' });
      fetchData();
    } catch (err) { setFormError(err.message); }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Remove this subject offering?')) return;
    try {
      await fetch(`/api/admin/offerings/${id}`, { method: 'DELETE', headers });
      fetchData();
    } catch (e) { console.error(e); }
  };


  const extractSectionLetter = (batch) => {
    if (!batch) return '';
    if (batch.section && batch.section.trim()) return batch.section.trim().toUpperCase();
    const name = batch.batchName || '';
    const m = name.match(/(?:Section\s*|-)([A-Za-z0-9]+)$/i) || name.match(/([A-Za-z])$/);
    if (m) return m[1].toUpperCase();
    return name.toUpperCase();
  };

  // Sort offerings section-wise: Section A first, then B, then C, etc. Then by subject code within each section.
  const sortedOfferings = [...offerings].sort((a, b) => {
    const secA = extractSectionLetter(a.batch);
    const secB = extractSectionLetter(b.batch);
    const secCmp = secA.localeCompare(secB, undefined, { numeric: true, sensitivity: 'base' });
    if (secCmp !== 0) return secCmp;
    // Secondary: subject code
    const codeA = a.subject?.subjectCode || '';
    const codeB = b.subject?.subjectCode || '';
    return codeA.localeCompare(codeB);
  });

  // Also keep groupedBySubject for subject-level display (sorted by subject code)
  const groupedBySubject = offerings.reduce((acc, o) => {
    const key = o.subject?.id;
    if (!acc[key]) acc[key] = { subject: o.subject, offerings: [] };
    acc[key].offerings.push(o);
    return acc;
  }, {});


  const unassignedCount = offerings.filter(o => !o.assignedFaculty).length;

  return (
    <div>
      {/* Header */}
      <div className="page-header">
        <div>
          <h1 className="page-title">Subject Offerings</h1>
          <p className="page-subtitle">
            Assign subjects to sections and set the responsible faculty for each section independently.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.65rem', flexWrap: 'wrap' }}>
          <button
            className="btn btn-secondary"
            onClick={handleAutoAssign}
            disabled={autoAssigning}
            title="Auto-assign eligible faculty to offerings that currently have no faculty set"
            style={{ border: '1px solid rgba(16,185,129,0.4)', background: 'rgba(16,185,129,0.1)', color: '#34d399' }}
          >
            <Sparkles size={15} />
            {autoAssigning ? 'Assigning…' : 'Auto-Assign Unassigned'}
          </button>
          <button className="btn btn-primary" onClick={() => setShowModal(true)}>
            <Plus size={15} /> New Offering
          </button>
        </div>
      </div>

      {/* Scope Filter */}
      <div className="glass-panel" style={{ padding: '0.9rem 1.1rem', marginBottom: '1.25rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <Filter size={14} style={{ color: 'var(--accent-primary)' }} />
          <select
            className="form-select"
            style={{ padding: '0.35rem 0.75rem', fontSize: '0.82rem', minWidth: '230px' }}
            value={filterDept}
            onChange={e => {
              handleDeptChange(e.target.value);
              if (e.target.value) setStoredAcademicContext({ department: e.target.value });
            }}
          >
            <option value="">-- Select Department --</option>
            <option value="ALL">All Departments</option>
            {INSTITUTIONAL_PROGRAMMES.map(p => <option key={p} value={p}>{p}</option>)}
          </select>
          <select
            className="form-select"
            style={{ padding: '0.35rem 0.75rem', fontSize: '0.82rem', minWidth: '220px' }}
            value={filterSem}
            onChange={e => {
              handleSemChange(e.target.value);
              if (e.target.value) setStoredAcademicContext({ semester: e.target.value });
            }}
          >
            <option value="">-- Select Semester --</option>
            <option value="ALL">All Semesters</option>
            {SEMESTER_OPTIONS.map(opt => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
          </select>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            {offerings.length} offerings
            {unassignedCount > 0 && (
              <span style={{ color: 'var(--accent-warning)', marginLeft: '0.5rem' }}>
                · {unassignedCount} unassigned
              </span>
            )}
          </span>
        </div>
      </div>

      {/* Message */}
      {msg && (
        <div style={{
          padding: '0.7rem 1rem', borderRadius: '7px', marginBottom: '1rem',
          background: msg.type === 'success' ? 'rgba(16,185,129,0.12)' : 'rgba(239,68,68,0.12)',
          border: `1px solid ${msg.type === 'success' ? '#10b981' : '#ef4444'}`,
          color: msg.type === 'success' ? '#6ee7b7' : '#fca5a5',
          display: 'flex', justifyContent: 'space-between', fontSize: '0.88rem'
        }}>
          <span>{msg.text}</span>
          <button onClick={() => setMsg(null)} style={{ background: 'transparent', border: 'none', color: 'inherit', cursor: 'pointer' }}>✕</button>
        </div>
      )}

      {/* Unassigned warning */}
      {unassignedCount > 0 && !msg && (
        <div style={{
          padding: '0.65rem 0.9rem', borderRadius: '7px', marginBottom: '1rem',
          background: 'rgba(245,158,11,0.09)', border: '1px solid rgba(245,158,11,0.25)',
          display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.85rem', color: 'var(--accent-warning)'
        }}>
          <AlertTriangle size={15} />
          {unassignedCount} offering(s) have no faculty assigned. Click the edit icon to assign faculty per section.
        </div>
      )}

      {/* Offerings Table */}
      {offerings.length === 0 ? (
        <div className="glass-panel" style={{ textAlign: 'center', padding: '3rem 2rem' }}>
          <BookMarked size={40} style={{ color: 'var(--accent-primary)', opacity: 0.5, marginBottom: '1rem' }} />
          <h3 style={{ fontWeight: 700, marginBottom: '0.5rem' }}>No Subject Offerings</h3>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.88rem', maxWidth: '420px', margin: '0 auto 1.5rem auto' }}>
            Map subjects to student sections and assign qualified faculty before timetable generation.
          </p>
          <button className="btn btn-primary" onClick={() => setShowModal(true)}>
            <Plus size={15} /> Create First Offering
          </button>
        </div>
      ) : (
        <div className="glass-panel" style={{ padding: 0 }}>
          <div className="data-table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th style={{ minWidth: '130px' }}>Section</th>
                  <th>Subject</th>
                  <th>Type</th>
                  <th style={{ textAlign: 'center' }}>Hrs/Wk</th>
                  <th style={{ minWidth: '200px' }}>Assigned Faculty</th>
                  <th>Semester</th>
                  <th>Status</th>
                  <th style={{ textAlign: 'center' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {sortedOfferings.map(o => {
                  const isEditing = editingFaculty?.offeringId === o.id;
                  const eligFac = eligibleFacultyCache[o.subject?.id] || [];

                  return (
                    <tr key={o.id} style={isEditing ? { background: 'rgba(99,102,241,0.05)' } : {}}>
                      {/* Section */}
                      <td>
                        <span style={{ fontWeight: 700 }}>{o.batch?.batchName}</span>
                      </td>

                      {/* Subject */}
                      <td>
                        <div style={{ fontWeight: 600, fontSize: '0.88rem' }}>{o.subject?.subjectCode}</div>
                        <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{o.subject?.subjectName}</div>
                      </td>

                      {/* Type */}
                      <td>
                        <span className={`badge ${o.subject?.subjectType === 'LAB' ? 'badge-warning' : 'badge-info'}`}
                              style={{ fontSize: '0.72rem' }}>
                          {o.subject?.subjectType || 'THEORY'}
                        </span>
                      </td>

                      {/* Hrs/Wk */}
                      <td style={{ textAlign: 'center' }}>
                        <span style={{ fontWeight: 700, color: 'var(--accent-primary)' }}>
                          {o.subject?.calculatedWeeklyHours > 0
                            ? o.subject.calculatedWeeklyHours
                            : (o.subject?.weeklyLecturePeriods ?? '—')}
                        </span>
                      </td>

                      {/* Faculty — inline edit */}
                      <td>
                        {isEditing ? (
                          <div style={{ display: 'flex', gap: '0.4rem', alignItems: 'center' }}>
                            <select
                              className="form-select"
                              style={{ padding: '0.3rem 0.55rem', fontSize: '0.8rem', minWidth: '160px' }}
                              value={editFacultyValue}
                              onChange={e => setEditFacultyValue(e.target.value)}
                            >
                              <option value="">— Unassigned —</option>
                              {eligFac.length > 0 ? (
                                <>
                                  <optgroup label="Eligible Faculty">
                                    {eligFac.filter(f => f.eligible).map(f => (
                                      <option key={f.id} value={f.id}>{f.name}</option>
                                    ))}
                                  </optgroup>
                                  {eligFac.some(f => !f.eligible) && (
                                    <optgroup label="Other Faculty (not eligible)">
                                      {eligFac.filter(f => !f.eligible).map(f => (
                                        <option key={f.id} value={f.id}>{f.name}</option>
                                      ))}
                                    </optgroup>
                                  )}
                                </>
                              ) : (
                                allFaculty.map(f => <option key={f.id} value={f.id}>{f.name}</option>)
                              )}
                            </select>
                            <button
                              className="btn btn-primary"
                              style={{ padding: '0.3rem 0.55rem' }}
                              onClick={saveFacultyEdit}
                              title="Save"
                            >
                              <Check size={13} />
                            </button>
                            <button
                              className="btn btn-secondary"
                              style={{ padding: '0.3rem 0.55rem' }}
                              onClick={cancelEdit}
                              title="Cancel"
                            >
                              <X size={13} />
                            </button>
                          </div>
                        ) : (
                          <div
                            style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer' }}
                            onClick={() => startEditFaculty(o)}
                            title="Click to change faculty"
                          >
                            {o.assignedFaculty ? (
                              <span style={{ fontSize: '0.85rem' }}>
                                {o.assignedFaculty.name}
                                <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginLeft: '0.3rem' }}>
                                  ({o.assignedFaculty.employeeId})
                                </span>
                              </span>
                            ) : (
                              <span className="badge badge-warning" style={{ fontSize: '0.72rem' }}>
                                Unassigned
                              </span>
                            )}
                            <Edit2 size={12} style={{ color: 'var(--text-muted)', opacity: 0.6 }} />
                          </div>
                        )}
                      </td>

                      {/* Semester */}
                      <td style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                        {o.semester ?? o.batch?.semester ?? '—'}
                      </td>

                      {/* Status */}
                      <td>
                        <span className={`badge ${o.active ? 'badge-success' : 'badge-danger'}`} style={{ fontSize: '0.72rem' }}>
                          {o.active ? 'Active' : 'Inactive'}
                        </span>
                      </td>

                      {/* Actions */}
                      <td style={{ textAlign: 'center' }}>
                        <button
                          className="btn btn-danger"
                          style={{ padding: '0.3rem 0.5rem' }}
                          onClick={() => handleDelete(o.id)}
                          title="Remove offering"
                        >
                          <Trash2 size={13} />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Create Offering Modal */}
      <Modal isOpen={showModal} onClose={() => { setShowModal(false); setFormError(''); }} title="Create Subject Offering">
        {formError && (
          <div className="alert alert-danger" style={{ marginBottom: '1rem', fontSize: '0.88rem' }}>
            {formError}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Section / Batch *</label>
            <select
              className="form-select"
              value={form.batchId}
              onChange={e => setForm({ ...form, batchId: e.target.value })}
              required
            >
              <option value="">— Select Section —</option>
              {batches.map(b => (
                <option key={b.id} value={b.id}>
                  {b.batchName} · {b.department} · Sem {b.semester}
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Subject *</label>
            <select
              className="form-select"
              value={form.subjectId}
              onChange={e => {
                setForm({ ...form, subjectId: e.target.value, assignedFacultyId: '' });
                if (e.target.value) loadEligibleFaculty(e.target.value);
              }}
              required
            >
              <option value="">— Select Subject —</option>
              {subjects.map(s => (
                <option key={s.id} value={s.id}>
                  {s.subjectCode} – {s.subjectName} ({s.calculatedWeeklyHours || '?'} hrs/wk)
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Assigned Faculty</label>
            <select
              className="form-select"
              value={form.assignedFacultyId}
              onChange={e => setForm({ ...form, assignedFacultyId: e.target.value })}
            >
              <option value="">— Select Faculty —</option>
              {form.subjectId && eligibleFacultyCache[form.subjectId] ? (
                <>
                  <optgroup label="Eligible Faculty">
                    {(eligibleFacultyCache[form.subjectId] || []).filter(f => f.eligible).map(f => (
                      <option key={f.id} value={f.id}>{f.name} ({f.employeeId})</option>
                    ))}
                  </optgroup>
                  {(eligibleFacultyCache[form.subjectId] || []).some(f => !f.eligible) && (
                    <optgroup label="Other Faculty">
                      {(eligibleFacultyCache[form.subjectId] || []).filter(f => !f.eligible).map(f => (
                        <option key={f.id} value={f.id}>{f.name} ({f.employeeId})</option>
                      ))}
                    </optgroup>
                  )}
                </>
              ) : (
                allFaculty.map(f => <option key={f.id} value={f.id}>{f.name} ({f.employeeId})</option>)
              )}
            </select>
            <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '0.3rem' }}>
              Eligible faculty are shown first. Each section must have its own faculty assignment.
            </p>
          </div>

          <div className="form-group">
            <label className="form-label">Academic Year</label>
            <input
              className="form-input"
              value={form.academicYear}
              onChange={e => setForm({ ...form, academicYear: e.target.value })}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
            <button type="submit" className="btn btn-primary">Create Offering</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
