import React, { useState, useEffect } from 'react';
import { Layers, Plus, Edit2, Trash2, DoorOpen, Filter, AlertTriangle } from 'lucide-react';
import Modal from '../components/Modal';
import { INSTITUTIONAL_PROGRAMMES } from '../utils/programmes';
import { SEMESTER_OPTIONS, getStoredAcademicContext, setStoredAcademicContext } from '../utils/academicContext';

export default function BatchManagementPage({ user }) {
  const [batches, setBatches] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [showModal, setShowModal] = useState(false);
  const [editId, setEditId] = useState(null);

  const initialContext = getStoredAcademicContext();
  const [filterDept, setFilterDept] = useState(() => initialContext.department || '');
  const [filterSemester, setFilterSemester] = useState(() => initialContext.semester || '');
  const [errorMsg, setErrorMsg] = useState('');

  const [form, setForm] = useState({
    batchName: '',
    section: 'A',
    department: initialContext.department || INSTITUTIONAL_PROGRAMMES[4],
    semester: initialContext.semester ? Number(initialContext.semester) : 3,
    strength: 60,
    academicYear: '2026-2027',
    assignedRoomCode: '',
    assignedBlockName: '',
    assignedFloorNumber: 1
  });

  useEffect(() => {
    fetchBatches();
    fetchRooms();
  }, []);

  const fetchBatches = async () => {
    try {
      const res = await fetch('/api/admin/batches', { headers: { 'Authorization': `Bearer ${user.token}` } });
      if (res.ok) setBatches(await res.json());
    } catch (e) { console.error(e); }
  };

  const fetchRooms = async () => {
    try {
      const res = await fetch('/api/admin/rooms', { headers: { 'Authorization': `Bearer ${user.token}` } });
      if (res.ok) setRooms(await res.json());
    } catch (e) { console.error(e); }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMsg('');

    if (form.strength > 70) {
      setErrorMsg('Maximum student strength allowed per section is 70 students. Please split this section or adjust the count.');
      return;
    }

    const url = editId ? `/api/admin/batches/${editId}` : '/api/admin/batches';
    const method = editId ? 'PUT' : 'POST';

    try {
      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${user.token}` },
        body: JSON.stringify(form)
      });
      if (res.ok) {
        setShowModal(false);
        fetchBatches();
      } else {
        const err = await res.json().catch(() => ({}));
        setErrorMsg(err.error || 'Failed to save batch section');
      }
    } catch (err) {
      setErrorMsg(err.message);
    }
  };

  const handleEdit = (b) => {
    setEditId(b.id);
    setErrorMsg('');
    setForm({
      batchName: b.batchName,
      section: b.section,
      department: b.department || INSTITUTIONAL_PROGRAMMES[4],
      semester: b.semester,
      strength: b.strength,
      academicYear: b.academicYear,
      assignedRoomCode: b.assignedRoomCode || '',
      assignedBlockName: b.assignedBlockName || '',
      assignedFloorNumber: b.assignedFloorNumber || 1
    });
    setShowModal(true);
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Permanently delete this section? Associated offerings will also be removed.')) return;
    try {
      await fetch(`/api/admin/batches/${id}`, { method: 'DELETE', headers: { 'Authorization': `Bearer ${user.token}` } });
      fetchBatches();
    } catch (e) { console.error(e); }
  };

  const handleDeptFilter = (dept) => {
    setFilterDept(dept);
    if (dept && dept !== 'ALL') setStoredAcademicContext({ department: dept });
  };

  const handleSemesterFilter = (sem) => {
    setFilterSemester(sem);
    if (sem && sem !== 'ALL') setStoredAcademicContext({ semester: sem });
  };

  const filteredBatches = batches.filter(b => {
    if (!filterSemester || filterSemester === '') return false;
    if (filterSemester !== 'ALL' && b.semester !== Number(filterSemester)) return false;
    if (!filterDept || filterDept === '') return false;
    if (filterDept !== 'ALL' && b.department !== filterDept) return false;
    return true;
  }).sort((a, b) => {
    const extractSection = (batch) => {
      if (batch.section && batch.section.trim()) return batch.section.trim().toUpperCase();
      const name = batch.batchName || '';
      const m = name.match(/(?:Section\s*|-)([A-Za-z0-9]+)$/i) || name.match(/([A-Za-z])$/);
      return m ? m[1].toUpperCase() : name.toUpperCase();
    };
    // Primary sort: section alphabetically (A → B → C → D → E → F → G)
    const secA = extractSection(a);
    const secB = extractSection(b);
    const secCmp = secA.localeCompare(secB, undefined, { numeric: true, sensitivity: 'base' });
    if (secCmp !== 0) return secCmp;
    // Secondary: semester asc
    if (a.semester !== b.semester) return (a.semester || 0) - (b.semester || 0);
    return (a.batchName || '').localeCompare(b.batchName || '');
  });

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Batches &amp; Sections</h1>
          <p className="page-subtitle">Manage student cohorts, section allocations, and maximum 70-student classroom capacity constraints.</p>
        </div>
        <button
          className="btn btn-primary"
          onClick={() => {
            setEditId(null);
            setErrorMsg('');
            setForm({
              batchName: '',
              section: 'A',
              department: filterDept && filterDept !== 'ALL' ? filterDept : INSTITUTIONAL_PROGRAMMES[4],
              semester: filterSemester && filterSemester !== 'ALL' ? Number(filterSemester) : 3,
              strength: 60,
              academicYear: '2026-2027'
            });
            setShowModal(true);
          }}
        >
          <Plus size={16} /> Add Batch / Section
        </button>
      </div>

      {/* Filter Bar */}
      <div className="glass-panel" style={{ marginBottom: '1.5rem', padding: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <span style={{ fontSize: '0.85rem', fontWeight: 700 }}>Department:</span>
            <select
              className="form-select"
              style={{ padding: '0.35rem 0.75rem', fontSize: '0.85rem', minWidth: '220px' }}
              value={filterDept}
              onChange={e => handleDeptFilter(e.target.value)}
            >
              <option value="">-- Select Department --</option>
              <option value="ALL">All Departments</option>
              {INSTITUTIONAL_PROGRAMMES.map(prog => (
                <option key={prog} value={prog}>{prog}</option>
              ))}
            </select>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Filter size={16} style={{ color: 'var(--accent-primary)' }} />
            <span style={{ fontSize: '0.85rem', fontWeight: 700 }}>Semester:</span>
            <select
              className="form-select"
              style={{ padding: '0.35rem 0.75rem', fontSize: '0.85rem', minWidth: '220px' }}
              value={filterSemester}
              onChange={e => handleSemesterFilter(e.target.value)}
            >
              <option value="">-- Select Semester --</option>
              <option value="ALL">All Semesters</option>
              {SEMESTER_OPTIONS.map(opt => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </div>

          <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginLeft: 'auto' }}>
            Displaying <strong>{filteredBatches.length}</strong> active sections
          </div>
        </div>
      </div>

      {filteredBatches.length === 0 ? (
        <div className="glass-panel" style={{ textAlign: 'center', padding: '3.5rem 1rem' }}>
          <Layers size={44} style={{ color: 'var(--accent-primary)', opacity: 0.6, margin: '0 auto 1rem auto' }} />
          <h3 style={{ fontSize: '1.1rem', fontWeight: 700, margin: '0 0 0.5rem 0' }}>
            {!filterDept || !filterSemester
              ? 'Please select Department and Semester above to view sections.'
              : 'No sections configured for this department and semester.'}
          </h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', maxWidth: '480px', margin: '0 auto 1.5rem auto' }}>
            Sections are automatically created when you upload curriculum documents in Subjects &amp; Credits, or you can create one directly.
          </p>
        </div>
      ) : (
        <div className="glass-panel">
          <div className="data-table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Batch Name</th>
                  <th>Section</th>
                  <th>Department</th>
                  <th>Semester</th>
                  <th>Student Strength</th>
                  <th>Allocated Room (Block / Floor)</th>
                  <th>Academic Year</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredBatches.map(b => (
                  <tr key={b.id}>
                    <td><strong>{b.batchName}</strong></td>
                    <td><span className="badge badge-warning">Section {b.section}</span></td>
                    <td>{b.department}</td>
                    <td>Sem {b.semester}</td>
                    <td>
                      {b.strength > 70 ? (
                        <span className="badge badge-danger" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                          <AlertTriangle size={12} /> {b.strength} (Exceeds 70 Max Limit)
                        </span>
                      ) : (
                        <strong>{b.strength} Students</strong>
                      )}
                    </td>
                    <td>
                      {b.assignedRoomCode ? (
                        <span className="badge badge-primary" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '0.78rem' }}>
                          <DoorOpen size={12} />
                          <strong>{b.assignedRoomCode}</strong> &bull; {b.assignedBlockName || 'Block'} (Flr {b.assignedFloorNumber ?? 1})
                        </span>
                      ) : (
                        <span className="badge badge-secondary" style={{ opacity: 0.7, fontSize: '0.75rem' }}>Auto-Allocated on Solve</span>
                      )}
                    </td>
                    <td>{b.academicYear}</td>
                    <td><span className={`badge ${b.active ? 'badge-success' : 'badge-danger'}`}>{b.active ? 'Active' : 'Inactive'}</span></td>
                    <td>
                      <div style={{ display: 'flex', gap: '0.5rem' }}>
                        <button className="btn btn-secondary" style={{ padding: '0.4rem 0.6rem' }} onClick={() => handleEdit(b)}><Edit2 size={14} /></button>
                        <button className="btn btn-danger" style={{ padding: '0.4rem 0.6rem' }} onClick={() => handleDelete(b.id)}><Trash2 size={14} /></button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title={editId ? 'Edit Batch / Section' : 'Add New Batch / Section'}>
        {errorMsg && (
          <div className="alert alert-danger" style={{ marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <AlertTriangle size={16} />
            <span>{errorMsg}</span>
          </div>
        )}
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Batch Name *</label>
            <input
              type="text"
              className="form-input"
              value={form.batchName}
              placeholder="e.g. CSE 2026 Batch A"
              onChange={e => setForm({ ...form, batchName: e.target.value })}
              required
            />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label">Section</label>
              <input
                type="text"
                className="form-input"
                value={form.section}
                placeholder="A, B, C..."
                onChange={e => setForm({ ...form, section: e.target.value })}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label">Semester</label>
              <input
                type="number"
                className="form-input"
                value={form.semester}
                onChange={e => setForm({ ...form, semester: Number(e.target.value) })}
                min="1"
                max="8"
                required
              />
            </div>
          </div>
          <div className="form-group">
            <label className="form-label">Department / Programme</label>
            <select
              className="form-select"
              value={form.department}
              onChange={e => setForm({ ...form, department: e.target.value })}
            >
              {INSTITUTIONAL_PROGRAMMES.map(p => (
                <option key={p} value={p}>{p}</option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Dedicated Classroom (Block / Floor / Room)</label>
            <select
              className="form-select"
              value={form.assignedRoomCode}
              onChange={e => {
                const code = e.target.value;
                const r = rooms.find(rm => rm.roomCode === code);
                setForm({
                  ...form,
                  assignedRoomCode: code,
                  assignedBlockName: r ? (r.blockName || '') : '',
                  assignedFloorNumber: r ? (r.floorNumber || 1) : 1
                });
              }}
            >
              <option value="">-- Auto-Allocate Dedicated Room by Timetable Solver --</option>
              {rooms.filter(r => r.roomType === 'LECTURE_HALL').map(r => (
                <option key={r.id} value={r.roomCode}>
                  {r.roomCode} - {r.roomName} ({r.blockName || 'Block'} · Floor {r.floorNumber ?? 1} · Cap: {r.capacity})
                </option>
              ))}
            </select>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label">Student Strength (Max: 70) *</label>
              <input
                type="number"
                className="form-input"
                value={form.strength}
                onChange={e => setForm({ ...form, strength: Number(e.target.value) })}
                min="1"
                max="70"
                required
              />
              <p style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                Strict institutional limit: 70 students per section.
              </p>
            </div>
            <div className="form-group">
              <label className="form-label">Academic Year</label>
              <input
                type="text"
                className="form-input"
                value={form.academicYear}
                onChange={e => setForm({ ...form, academicYear: e.target.value })}
                required
              />
            </div>
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
            <button type="submit" className="btn btn-primary">{editId ? 'Save Changes' : 'Create Batch'}</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
