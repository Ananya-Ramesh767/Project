import React, { useState, useEffect } from 'react';
import { DoorOpen, Plus, Edit2, Trash2, Layers, AlertCircle, Check, Building, Grid, RefreshCw, CheckCircle2, ShieldAlert } from 'lucide-react';
import { INSTITUTIONAL_PROGRAMMES } from '../utils/programmes';

export default function RoomManagementPage({ user }) {
  const [rooms, setRooms] = useState([]);
  const [showModal, setShowModal] = useState(false);
  const [showHierarchicalModal, setShowHierarchicalModal] = useState(false);
  const [editId, setEditId] = useState(null);

  // Tab: 'infrastructure' | 'allocation'
  const [activeTab, setActiveTab] = useState('infrastructure');
  const [allocationDept, setAllocationDept] = useState(INSTITUTIONAL_PROGRAMMES[4]);
  const [proposals, setProposals] = useState(null);
  const [validationReport, setValidationReport] = useState(null);
  const [allocationLoading, setAllocationLoading] = useState(false);
  const [reconcileResult, setReconcileResult] = useState('');
  const [autoAllocMessage, setAutoAllocMessage] = useState('');

  // Filters
  const [filterBlock, setFilterBlock] = useState('ALL');
  const [filterType, setFilterType] = useState('ALL');

  const [form, setForm] = useState({
    roomCode: '',
    roomName: '',
    roomType: 'LECTURE_HALL',
    capacity: 60,
    department: INSTITUTIONAL_PROGRAMMES[4], // Computer Science Engineering
    blockName: 'A Block',
    floorNumber: 1,
    yearOfStudy: '',
    assignedSection: ''
  });

  // Hierarchical Setup Form
  const [numBlocks, setNumBlocks] = useState(2);
  const [hierarchicalBlocks, setHierarchicalBlocks] = useState([
    {
      blockName: 'Block A',
      department: INSTITUTIONAL_PROGRAMMES[4],
      numFloors: 2,
      floors: [
        {
          floorNumber: 1,
          numRooms: 2,
          rooms: [
            { roomName: 'Room 101', roomCode: 'A-101', roomType: 'LECTURE_HALL', capacity: 60 },
            { roomName: 'Room 102', roomCode: 'A-102', roomType: 'LECTURE_HALL', capacity: 60 }
          ]
        },
        {
          floorNumber: 2,
          numRooms: 2,
          rooms: [
            { roomName: 'Room 201', roomCode: 'A-201', roomType: 'LECTURE_HALL', capacity: 60 },
            { roomName: 'CS Lab 1', roomCode: 'A-LAB-1', roomType: 'LAB', capacity: 60 }
          ]
        }
      ]
    },
    {
      blockName: 'Block B',
      department: INSTITUTIONAL_PROGRAMMES[4],
      numFloors: 1,
      floors: [
        {
          floorNumber: 1,
          numRooms: 2,
          rooms: [
            { roomName: 'Room 101', roomCode: 'B-101', roomType: 'LECTURE_HALL', capacity: 60 },
            { roomName: 'Room 102', roomCode: 'B-102', roomType: 'LECTURE_HALL', capacity: 60 }
          ]
        }
      ]
    }
  ]);

  const [error, setError] = useState('');
  const [hierarchicalError, setHierarchicalError] = useState('');
  const [hierarchicalSuccess, setHierarchicalSuccess] = useState('');
  const [hierarchicalLoading, setHierarchicalLoading] = useState(false);

  useEffect(() => {
    fetchRooms();
    fetchValidation();
  }, []);

  const handleNumBlocksChange = (val) => {
    const count = Math.max(1, Math.min(10, Number(val) || 1));
    setNumBlocks(count);
    const updated = [];
    for (let b = 0; b < count; b++) {
      if (hierarchicalBlocks[b]) {
        updated.push(hierarchicalBlocks[b]);
      } else {
        const letter = String.fromCharCode(65 + b);
        updated.push({
          blockName: `Block ${letter}`,
          department: INSTITUTIONAL_PROGRAMMES[4],
          numFloors: 1,
          floors: [
            {
              floorNumber: 1,
              numRooms: 2,
              rooms: [
                { roomName: 'Room 101', roomCode: `${letter}-101`, roomType: 'LECTURE_HALL', capacity: 60 },
                { roomName: 'Room 102', roomCode: `${letter}-102`, roomType: 'LECTURE_HALL', capacity: 60 }
              ]
            }
          ]
        });
      }
    }
    setHierarchicalBlocks(updated);
  };

  const handleBlockFieldChange = (bIdx, field, val) => {
    const copy = [...hierarchicalBlocks];
    copy[bIdx] = { ...copy[bIdx], [field]: val };
    setHierarchicalBlocks(copy);
  };

  const handleNumFloorsChange = (bIdx, val) => {
    const floorCount = Math.max(1, Math.min(15, Number(val) || 1));
    const copy = [...hierarchicalBlocks];
    const targetBlock = { ...copy[bIdx], numFloors: floorCount };
    const prefix = targetBlock.blockName.replace(/[^A-Za-z0-9]/g, '').slice(0, 3).toUpperCase() || 'BLK';
    const updatedFloors = [];
    for (let f = 1; f <= floorCount; f++) {
      const existing = targetBlock.floors?.find(fl => fl.floorNumber === f);
      if (existing) {
        updatedFloors.push(existing);
      } else {
        updatedFloors.push({
          floorNumber: f,
          numRooms: 2,
          rooms: [
            { roomName: `Room ${f}01`, roomCode: `${prefix}-${f}01`, roomType: 'LECTURE_HALL', capacity: 60 },
            { roomName: `Room ${f}02`, roomCode: `${prefix}-${f}02`, roomType: 'LECTURE_HALL', capacity: 60 }
          ]
        });
      }
    }
    targetBlock.floors = updatedFloors;
    copy[bIdx] = targetBlock;
    setHierarchicalBlocks(copy);
  };

  const handleNumRoomsChange = (bIdx, fIdx, val) => {
    const roomCount = Math.max(1, Math.min(30, Number(val) || 1));
    const copy = [...hierarchicalBlocks];
    const block = { ...copy[bIdx] };
    const prefix = block.blockName.replace(/[^A-Za-z0-9]/g, '').slice(0, 3).toUpperCase() || 'BLK';
    const floors = [...block.floors];
    const floor = { ...floors[fIdx], numRooms: roomCount };
    const currentRooms = floor.rooms || [];
    const updatedRooms = [];
    for (let r = 0; r < roomCount; r++) {
      if (currentRooms[r]) {
        updatedRooms.push(currentRooms[r]);
      } else {
        const roomNum = floor.floorNumber * 100 + (r + 1);
        updatedRooms.push({
          roomName: `Room ${roomNum}`,
          roomCode: `${prefix}-${roomNum}`,
          roomType: 'LECTURE_HALL',
          capacity: 60
        });
      }
    }
    floor.rooms = updatedRooms;
    floors[fIdx] = floor;
    block.floors = floors;
    copy[bIdx] = block;
    setHierarchicalBlocks(copy);
  };

  const handleRoomFieldChange = (bIdx, fIdx, rIdx, field, val) => {
    const copy = [...hierarchicalBlocks];
    const block = { ...copy[bIdx] };
    const floors = [...block.floors];
    const floor = { ...floors[fIdx] };
    const rooms = [...floor.rooms];
    rooms[rIdx] = { ...rooms[rIdx], [field]: val };
    floor.rooms = rooms;
    floors[fIdx] = floor;
    block.floors = floors;
    copy[bIdx] = block;
    setHierarchicalBlocks(copy);
  };

  const handleHierarchicalSubmit = async (e) => {
    e.preventDefault();
    setHierarchicalError('');
    setHierarchicalSuccess('');
    setHierarchicalLoading(true);

    try {
      const res = await fetch('/api/admin/rooms/hierarchical-setup', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${user.token}`
        },
        body: JSON.stringify({ blocks: hierarchicalBlocks })
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.error || errData.message || 'Failed to set up hierarchical blocks');
      }

      const result = await res.json();
      setHierarchicalSuccess(`Successfully provisioned ${result.totalCreated} rooms across ${hierarchicalBlocks.length} blocks!`);
      setTimeout(() => {
        setShowHierarchicalModal(false);
        setHierarchicalSuccess('');
        fetchRooms();
      }, 1200);
    } catch (err) {
      setHierarchicalError(err.message);
    } finally {
      setHierarchicalLoading(false);
    }
  };

  const fetchRooms = async () => {
    try {
      const res = await fetch('/api/admin/rooms', { headers: { 'Authorization': `Bearer ${user.token}` } });
      if (res.ok) setRooms(await res.json());
    } catch (e) { console.error(e); }
  };

  const fetchValidation = async () => {
    try {
      const res = await fetch('/api/admin/rooms/validate', { headers: { 'Authorization': `Bearer ${user.token}` } });
      if (res.ok) setValidationReport(await res.json());
    } catch (e) { console.error(e); }
  };

  const fetchProposals = async (dept) => {
    try {
      const q = dept && dept !== 'ALL' ? `?department=${encodeURIComponent(dept)}` : '';
      const res = await fetch(`/api/admin/rooms/allocation-proposals${q}`, {
        headers: { 'Authorization': `Bearer ${user.token}` }
      });
      if (res.ok) setProposals(await res.json());
    } catch (e) { console.error(e); }
  };

  const handleAutoAllocate = async () => {
    setAllocationLoading(true);
    setAutoAllocMessage('');
    try {
      const res = await fetch('/api/admin/rooms/auto-allocate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${user.token}` },
        body: JSON.stringify({ department: allocationDept })
      });
      if (res.ok) {
        setAutoAllocMessage('Auto-allocation completed and saved successfully.');
        await fetchRooms();
        await fetchProposals(allocationDept);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setAllocationLoading(false);
    }
  };

  const handleReconcile = async () => {
    setAllocationLoading(true);
    setReconcileResult('');
    try {
      const res = await fetch('/api/admin/rooms/reconcile', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${user.token}` }
      });
      if (res.ok) {
        const data = await res.json();
        setReconcileResult(`Successfully reconciled ${data.reconciledCount} misassigned section(s).`);
        await fetchRooms();
        await fetchProposals(allocationDept);
        await fetchValidation();
      }
    } catch (e) {
      console.error(e);
    } finally {
      setAllocationLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    const url = editId ? `/api/admin/rooms/${editId}` : '/api/admin/rooms';
    const method = editId ? 'PUT' : 'POST';

    try {
      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${user.token}` },
        body: JSON.stringify(form)
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || 'Failed to save room');
      }
      setShowModal(false);
      fetchRooms();
    } catch (err) { setError(err.message); }
  };


  const handleEdit = (r) => {
    setEditId(r.id);
    setForm({
      roomCode: r.roomCode,
      roomName: r.roomName,
      roomType: r.roomType,
      capacity: r.capacity,
      department: r.department || 'CSE',
      blockName: r.blockName || 'Tech Park Block A',
      floorNumber: r.floorNumber ?? 1,
      yearOfStudy: r.yearOfStudy || '',
      assignedSection: r.assignedSection || ''
    });
    setShowModal(true);
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Deactivate this room?')) return;
    try {
      await fetch(`/api/admin/rooms/${id}`, { method: 'DELETE', headers: { 'Authorization': `Bearer ${user.token}` } });
      fetchRooms();
    } catch (e) { console.error(e); }
  };

  const uniqueBlocks = [...new Set(rooms.map(r => r.blockName).filter(Boolean))];

  const filteredRooms = rooms.filter(r => {
    if (filterBlock !== 'ALL' && r.blockName !== filterBlock) return false;
    if (filterType !== 'ALL' && r.roomType !== filterType) return false;
    return true;
  });

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Room Master</h1>
          <p className="page-subtitle">Hierarchical block architecture, lecture halls, and specialized laboratory configuration.</p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button className="btn btn-primary" onClick={() => {
              setShowHierarchicalModal(true);
              setHierarchicalError('');
              setHierarchicalSuccess('');
            }}>
            <Building size={18} /> Hierarchical Block Setup
          </button>
          <button className="btn btn-secondary" onClick={() => {
              setEditId(null);
              setForm({ roomCode: '', roomName: '', roomType: 'LECTURE_HALL', capacity: 60, department: INSTITUTIONAL_PROGRAMMES[4], blockName: 'A Block', floorNumber: 1 });
              setShowModal(true);
            }}>
            <Plus size={18} /> Add Single Room
          </button>
        </div>
      </div>

      {/* Tab Switcher: Physical Infrastructure vs Automatic Section Allocation */}
      <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.5rem', borderBottom: '1px solid rgba(255,255,255,0.08)', paddingBottom: '0.75rem' }}>
        <button
          type="button"
          className={`btn ${activeTab === 'infrastructure' ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => setActiveTab('infrastructure')}
        >
          <Building size={16} /> Physical Room Infrastructure ({rooms.length})
        </button>
        <button
          type="button"
          className={`btn ${activeTab === 'allocation' ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => {
            setActiveTab('allocation');
            fetchProposals(allocationDept);
          }}
        >
          <Grid size={16} /> Automatic Section Allocation
        </button>
      </div>

      {/* Room Data Quality Warning Banner */}
      {validationReport && (validationReport.warnings?.length > 0 || validationReport.blockingIssues?.length > 0) && (
        <div className="glass-panel" style={{
          padding: '1rem 1.25rem',
          marginBottom: '1.5rem',
          borderLeft: '4px solid #f59e0b',
          background: 'rgba(245, 158, 11, 0.08)'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '0.75rem' }}>
            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <ShieldAlert size={20} style={{ color: '#f59e0b', flexShrink: 0, marginTop: '2px' }} />
              <div>
                <strong style={{ fontSize: '0.9rem', color: '#fcd34d' }}>Room Master Data Inconsistencies Detected</strong>
                <ul style={{ margin: '0.4rem 0 0 0', paddingLeft: '1.2rem', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                  {validationReport.warnings?.slice(0, 3).map((w, idx) => (
                    <li key={idx}>{w.issue} ({w.roomCode})</li>
                  ))}
                  {validationReport.blockingIssues?.map((b, idx) => (
                    <li key={`b-${idx}`} style={{ color: '#f87171' }}>{b.issue} ({b.roomCode})</li>
                  ))}
                </ul>
              </div>
            </div>
            <button
              className="btn btn-secondary"
              style={{ fontSize: '0.8rem', padding: '0.35rem 0.75rem' }}
              disabled={allocationLoading}
              onClick={handleReconcile}
            >
              <RefreshCw size={14} className={allocationLoading ? 'spin' : ''} /> Run Reconciliation
            </button>
          </div>
          {reconcileResult && (
            <div style={{ marginTop: '0.5rem', fontSize: '0.82rem', color: '#34d399', fontWeight: 600 }}>
              ✓ {reconcileResult}
            </div>
          )}
        </div>
      )}

      {/* TAB 1: PHYSICAL ROOM INFRASTRUCTURE */}
      {activeTab === 'infrastructure' && (
        <>
          {/* Filter Bar */}
          <div className="glass-panel" style={{ marginBottom: '1.5rem', padding: '1rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem', flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span style={{ fontSize: '0.85rem', fontWeight: 700 }}>Academic Block:</span>
                <select
                  className="form-select"
                  style={{ padding: '0.3rem 0.75rem', fontSize: '0.8rem', width: 'auto' }}
                  value={filterBlock}
                  onChange={e => setFilterBlock(e.target.value)}
                >
                  <option value="ALL">All Blocks ({uniqueBlocks.length})</option>
                  {uniqueBlocks.map(b => (
                    <option key={b} value={b}>{b}</option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span style={{ fontSize: '0.85rem', fontWeight: 700 }}>Room Type:</span>
                <div style={{ display: 'flex', gap: '0.3rem' }}>
                  {['ALL', 'LECTURE_HALL', 'LAB'].map(t => (
                    <button
                      key={t}
                      type="button"
                      className={`btn ${filterType === t ? 'btn-primary' : 'btn-secondary'}`}
                      style={{ padding: '0.25rem 0.65rem', fontSize: '0.75rem' }}
                      onClick={() => setFilterType(t)}
                    >
                      {t === 'ALL' ? 'All Types' : t === 'LAB' ? 'Laboratories' : 'Classrooms'}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            <div style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>
              Total Provisioned: <strong>{filteredRooms.length} rooms</strong>
            </div>
          </div>

          <div className="glass-panel">
            <div className="data-table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Room Code</th>
                    <th>Room Name</th>
                    <th>Academic Block</th>
                    <th>Type</th>
                    <th>Capacity</th>
                    <th>Department</th>
                    <th>Assigned Default Section</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredRooms.map(r => (
                    <tr key={r.id}>
                      <td><strong>{r.roomCode}</strong></td>
                      <td>{r.roomName}</td>
                      <td>
                        <span className="badge badge-secondary" style={{ fontSize: '0.75rem' }}>
                          {r.blockName || 'Unassigned Block'} · Flr {r.floorNumber ?? 1}
                        </span>
                      </td>
                      <td>
                        <span className={`badge ${r.roomType === 'LAB' ? 'badge-warning' : 'badge-success'}`}>
                          {r.roomType === 'LAB' ? 'LABORATORY' : 'CLASSROOM'}
                        </span>
                      </td>
                      <td><strong>{r.capacity} seats</strong></td>
                      <td style={{ maxWidth: '180px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={r.department}>
                        {r.department}
                      </td>
                      <td>
                        {r.assignedSection ? (
                          <span className="badge badge-primary">
                            {r.assignedSection} {r.yearOfStudy ? `(Yr ${r.yearOfStudy})` : ''}
                          </span>
                        ) : (
                          <span className="badge badge-secondary" style={{ opacity: 0.6 }}>Unassigned</span>
                        )}
                      </td>
                      <td><span className={`badge ${r.active ? 'badge-success' : 'badge-danger'}`}>{r.active ? 'Active' : 'Inactive'}</span></td>
                      <td>
                        <div style={{ display: 'flex', gap: '0.5rem' }}>
                          <button className="btn btn-secondary" style={{ padding: '0.4rem 0.6rem' }} onClick={() => handleEdit(r)}><Edit2 size={14} /></button>
                          <button className="btn btn-danger" style={{ padding: '0.4rem 0.6rem' }} onClick={() => handleDelete(r.id)}><Trash2 size={14} /></button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {filteredRooms.length === 0 && (
                    <tr>
                      <td colSpan="9" style={{ textAlign: 'center', padding: '3.5rem 1rem' }}>
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.75rem' }}>
                          <Building size={44} style={{ color: 'var(--text-muted)', opacity: 0.5 }} />
                          <h4 style={{ fontSize: '1.05rem', fontWeight: 700, margin: 0 }}>No rooms configured.</h4>
                          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', margin: 0, maxWidth: '450px' }}>
                            Set up academic blocks, classrooms, and laboratories to enable automatic room and lab allocation.
                          </p>
                          <button
                            className="btn btn-primary"
                            style={{ marginTop: '0.5rem', fontSize: '0.85rem' }}
                            onClick={() => { setShowHierarchicalModal(true); setHierarchicalError(''); setHierarchicalSuccess(''); }}
                          >
                            <Building size={16} /> Configure Room Master
                          </button>
                        </div>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      {/* TAB 2: AUTOMATIC SECTION ALLOCATION */}
      {activeTab === 'allocation' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          {/* Department Selection & Action Bar */}
          <div className="glass-panel" style={{ padding: '1.25rem 1.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
              <div>
                <label className="form-label" style={{ marginBottom: '0.25rem', fontSize: '0.78rem', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                  Target Department
                </label>
                <select
                  className="form-select"
                  style={{ width: 'auto', minWidth: '260px' }}
                  value={allocationDept}
                  onChange={e => {
                    setAllocationDept(e.target.value);
                    fetchProposals(e.target.value);
                  }}
                >
                  {INSTITUTIONAL_PROGRAMMES.map(p => (
                    <option key={p} value={p}>{p}</option>
                  ))}
                </select>
              </div>

              {proposals && proposals.departmentProposals?.length > 0 && (
                <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
                  <div style={{ background: 'rgba(255,255,255,0.04)', padding: '0.5rem 1rem', borderRadius: '6px' }}>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Preferred Floor</div>
                    <div style={{ fontWeight: 800, fontSize: '1.05rem', color: '#38bdf8' }}>
                      Floor {proposals.departmentProposals[0].preferredFloor ?? 'N/A'}
                    </div>
                  </div>
                  <div style={{ background: 'rgba(255,255,255,0.04)', padding: '0.5rem 1rem', borderRadius: '6px' }}>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Classrooms on Floor</div>
                    <div style={{ fontWeight: 800, fontSize: '1.05rem', color: '#34d399' }}>
                      {proposals.departmentProposals[0].roomsAvailableOnPreferredFloor} available
                    </div>
                  </div>
                  <div style={{ background: 'rgba(255,255,255,0.04)', padding: '0.5rem 1rem', borderRadius: '6px' }}>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Department Sections</div>
                    <div style={{ fontWeight: 800, fontSize: '1.05rem' }}>
                      {proposals.departmentProposals[0].totalSections} sections
                    </div>
                  </div>
                </div>
              )}
            </div>

            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <button
                className="btn btn-primary"
                disabled={allocationLoading}
                onClick={handleAutoAllocate}
              >
                <RefreshCw size={16} className={allocationLoading ? 'spin' : ''} />
                {allocationLoading ? 'Calculating…' : 'Run Auto-Allocation'}
              </button>
            </div>
          </div>

          {autoAllocMessage && (
            <div className="alert alert-success" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <CheckCircle2 size={16} /> <span>{autoAllocMessage}</span>
            </div>
          )}

          {/* Allocation Proposals Table */}
          {proposals && proposals.departmentProposals?.map((dp, dpIdx) => (
            <div key={dpIdx} className="glass-panel" style={{ padding: '1.5rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                <div>
                  <h3 style={{ fontSize: '1.15rem', fontWeight: 800, margin: 0 }}>
                    {dp.department} — Default Section Classrooms
                  </h3>
                  <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)', margin: '0.25rem 0 0 0' }}>
                    Institutional grouping: Sections grouped on Floor {dp.preferredFloor} ({dp.preferredBlock}). Lab rooms are reserved separately for practical sessions.
                  </p>
                </div>
                <span className={`badge ${dp.allAllocated ? 'badge-success' : 'badge-warning'}`}>
                  {dp.allocatedCount} / {dp.totalSections} Allocated
                </span>
              </div>

              {dp.warnings && dp.warnings.length > 0 && (
                <div style={{ marginBottom: '1rem', padding: '0.75rem 1rem', background: 'rgba(245, 158, 11, 0.1)', borderRadius: '6px', fontSize: '0.82rem', color: '#fcd34d' }}>
                  {dp.warnings.map((w, wIdx) => <div key={wIdx}>⚠ {w}</div>)}
                </div>
              )}

              <div className="data-table-container">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Section</th>
                      <th>Semester</th>
                      <th>Student Strength</th>
                      <th>Allocated Classroom</th>
                      <th>Room Capacity</th>
                      <th>Location</th>
                      <th>Allocation Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dp.allocations?.map((a, aIdx) => (
                      <tr key={aIdx}>
                        <td><strong>{a.batchName}</strong></td>
                        <td>Sem {a.semester}</td>
                        <td>{a.strength} students</td>
                        <td>
                          {a.roomCode ? (
                            <strong style={{ color: '#38bdf8' }}>{a.roomCode} ({a.roomName})</strong>
                          ) : (
                            <span style={{ color: '#f87171' }}>Unallocated</span>
                          )}
                        </td>
                        <td>
                          {a.roomCapacity ? (
                            <span style={{ color: a.capacityValid ? '#34d399' : '#f87171' }}>
                              {a.roomCapacity} seats {a.capacityValid ? '✓' : '✗ (Insufficient)'}
                            </span>
                          ) : '—'}
                        </td>
                        <td>
                          {a.blockName ? `${a.blockName} · Floor ${a.floorNumber}` : '—'}
                        </td>
                        <td>
                          {a.roomCode ? (
                            a.isPreferredFloor ? (
                              <span className="badge badge-success">✓ Preferred Floor ({a.floorNumber})</span>
                            ) : (
                              <span className="badge badge-warning">⚠ Overflow Floor ({a.floorNumber})</span>
                            )
                          ) : (
                            <span className="badge badge-danger">✗ No Room Available</span>
                          )}
                        </td>
                      </tr>
                    ))}
                    {(!dp.allocations || dp.allocations.length === 0) && (
                      <tr>
                        <td colSpan="7" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                          No active student sections found for this department. Create sections in Batches & Sections.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>

              {/* Lab Isolation Info Box */}
              <div style={{ marginTop: '1.25rem', padding: '0.85rem 1rem', background: 'rgba(56, 189, 248, 0.06)', borderRadius: '6px', fontSize: '0.82rem', border: '1px solid rgba(56, 189, 248, 0.15)', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <DoorOpen size={18} style={{ color: '#38bdf8', flexShrink: 0 }} />
                <span>
                  <strong>Laboratory Resource Isolation:</strong> Specialized labs (Computer Lab, Electronics Lab, etc.) are kept unassigned from section home rooms. The Timetable Solver assigns laboratories exclusively for LAB subjects during practical periods.
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Single Add/Edit Modal */}
      {showModal && (
        <div className="modal-backdrop-centered">
          <div className="modal-card-centered glass-panel" style={{ width: '100%', maxWidth: '600px', maxHeight: '90vh', overflowY: 'auto', padding: '2rem' }}>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 800, marginBottom: '1.5rem' }}>{editId ? 'Edit Room' : 'Add New Room'}</h2>
            {error && <div className="alert alert-danger">{error}</div>}
            <form onSubmit={handleSubmit}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '1rem' }}>
                <div className="form-group">
                  <label className="form-label">Room Code *</label>
                  <input type="text" className="form-input" value={form.roomCode} onChange={e => setForm({ ...form, roomCode: e.target.value })} required />
                </div>
                <div className="form-group">
                  <label className="form-label">Room Name *</label>
                  <input type="text" className="form-input" value={form.roomName} onChange={e => setForm({ ...form, roomName: e.target.value })} required />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '1rem' }}>
                <div className="form-group">
                  <label className="form-label">Block Name</label>
                  <input type="text" className="form-input" value={form.blockName} onChange={e => setForm({ ...form, blockName: e.target.value })} />
                </div>
                <div className="form-group">
                  <label className="form-label">Floor Number</label>
                  <input type="number" className="form-input" value={form.floorNumber} onChange={e => setForm({ ...form, floorNumber: Number(e.target.value) })} min="0" />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                <div className="form-group">
                  <label className="form-label">Type *</label>
                  <select className="form-select" value={form.roomType} onChange={e => setForm({ ...form, roomType: e.target.value })}>
                    <option value="LECTURE_HALL">CLASSROOM (Lecture Hall)</option>
                    <option value="LAB">LABORATORY</option>
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Capacity (Seats) *</label>
                  <input type="number" className="form-input" value={form.capacity} onChange={e => setForm({ ...form, capacity: Number(e.target.value) })} min="1" required />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Programme / Department</label>
                <select className="form-select" value={form.department} onChange={e => setForm({ ...form, department: e.target.value })}>
                  {INSTITUTIONAL_PROGRAMMES.map(prog => (
                    <option key={prog} value={prog}>{prog}</option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                <div className="form-group">
                  <label className="form-label">Assigned Section (e.g. A, B, CSE-A)</label>
                  <input
                    type="text"
                    className="form-input"
                    value={form.assignedSection}
                    placeholder="e.g. A"
                    onChange={e => setForm({ ...form, assignedSection: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Year of Study (1 - 4)</label>
                  <input
                    type="number"
                    className="form-input"
                    value={form.yearOfStudy}
                    placeholder="e.g. 2"
                    onChange={e => setForm({ ...form, yearOfStudy: e.target.value ? Number(e.target.value) : '' })}
                    min="1"
                    max="4"
                  />
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1.5rem' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Save Room</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Hierarchical Block Setup Modal */}
      {showHierarchicalModal && (
        <div className="modal-backdrop-centered">
          <div className="modal-card-centered glass-panel" style={{ width: '100%', maxWidth: '850px', maxHeight: '90vh', overflowY: 'auto', padding: '2rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
              <div>
                <h2 style={{ fontSize: '1.3rem', fontWeight: 800 }}>Hierarchical Block & Facility Provisioning</h2>
                <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
                  Define blocks, number of classrooms, and specialized laboratories per block.
                </p>
              </div>
              <button className="btn btn-secondary" onClick={() => setShowHierarchicalModal(false)}>✕</button>
            </div>

            {hierarchicalError && (
              <div className="alert alert-danger" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                <AlertCircle size={16} /> <span>{hierarchicalError}</span>
              </div>
            )}
            {hierarchicalSuccess && (
              <div className="alert alert-success" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                <Check size={16} /> <span>{hierarchicalSuccess}</span>
              </div>
            )}

            <form onSubmit={handleHierarchicalSubmit}>
              <div style={{ background: 'rgba(0,0,0,0.2)', padding: '1rem', borderRadius: 'var(--radius-md)', marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '1rem' }}>
                <div style={{ fontWeight: 700, fontSize: '0.9rem' }}>Number of Academic Blocks:</div>
                <input
                  type="number"
                  className="form-input"
                  style={{ width: '100px' }}
                  min="1"
                  max="10"
                  value={numBlocks}
                  onChange={e => handleNumBlocksChange(e.target.value)}
                  required
                />
                <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  (Dynamic block configurations will generate automatically below)
                </span>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', marginBottom: '1.5rem' }}>
                {hierarchicalBlocks.map((blk, bIdx) => (
                  <div key={bIdx} className="glass-panel" style={{ padding: '1.25rem', background: 'rgba(255,255,255,0.02)', borderColor: 'rgba(255,255,255,0.1)' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', borderBottom: '1px solid rgba(255,255,255,0.08)', paddingBottom: '0.5rem' }}>
                      <h4 style={{ fontSize: '1rem', fontWeight: 800, color: 'var(--accent-primary)', margin: 0 }}>
                        Block {bIdx + 1} Infrastructure
                      </h4>
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1.5fr 1fr', gap: '1rem', marginBottom: '1.25rem' }}>
                      <div className="form-group" style={{ marginBottom: 0 }}>
                        <label className="form-label">Block Name *</label>
                        <input
                          type="text"
                          className="form-input"
                          value={blk.blockName}
                          onChange={e => handleBlockFieldChange(bIdx, 'blockName', e.target.value)}
                          placeholder="e.g. Block A, Tech Park"
                          required
                        />
                      </div>
                      <div className="form-group" style={{ marginBottom: 0 }}>
                        <label className="form-label">Associated Programme / Department</label>
                        <select
                          className="form-select"
                          value={blk.department}
                          onChange={e => handleBlockFieldChange(bIdx, 'department', e.target.value)}
                        >
                          {INSTITUTIONAL_PROGRAMMES.map(prog => (
                            <option key={prog} value={prog}>{prog}</option>
                          ))}
                        </select>
                      </div>
                      <div className="form-group" style={{ marginBottom: 0 }}>
                        <label className="form-label">How many floors does this block have? *</label>
                        <input
                          type="number"
                          className="form-input"
                          min="1"
                          max="15"
                          value={blk.numFloors || blk.floors?.length || 1}
                          onChange={e => handleNumFloorsChange(bIdx, e.target.value)}
                          required
                        />
                      </div>
                    </div>

                    {/* Floors inside this block */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '0.75rem' }}>
                      {blk.floors?.map((flr, fIdx) => (
                        <div key={fIdx} style={{ background: 'rgba(0,0,0,0.25)', padding: '1rem', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.06)' }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                            <span style={{ fontWeight: 700, fontSize: '0.88rem', color: '#38bdf8' }}>
                              Floor {flr.floorNumber}
                            </span>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                              <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                                How many classrooms on this floor?
                              </span>
                              <input
                                type="number"
                                className="form-input"
                                style={{ width: '80px', padding: '0.25rem 0.5rem', fontSize: '0.8rem' }}
                                min="1"
                                max="30"
                                value={flr.numRooms || flr.rooms?.length || 1}
                                onChange={e => handleNumRoomsChange(bIdx, fIdx, e.target.value)}
                                required
                              />
                            </div>
                          </div>

                          {/* Classrooms for this floor */}
                          <div style={{ overflowX: 'auto' }}>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem' }}>
                              <thead>
                                <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.08)', color: 'var(--text-dim)', textAlign: 'left' }}>
                                  <th style={{ padding: '0.4rem 0.5rem' }}>Room #</th>
                                  <th style={{ padding: '0.4rem 0.5rem' }}>Name of Classroom *</th>
                                  <th style={{ padding: '0.4rem 0.5rem' }}>Room Code *</th>
                                  <th style={{ padding: '0.4rem 0.5rem' }}>Room Type</th>
                                  <th style={{ padding: '0.4rem 0.5rem' }}>Capacity</th>
                                </tr>
                              </thead>
                              <tbody>
                                {flr.rooms?.map((rm, rIdx) => (
                                  <tr key={rIdx} style={{ borderBottom: '1px solid rgba(255,255,255,0.03)' }}>
                                    <td style={{ padding: '0.4rem 0.5rem', color: 'var(--text-muted)', width: '60px' }}>
                                      #{rIdx + 1}
                                    </td>
                                    <td style={{ padding: '0.4rem 0.5rem' }}>
                                      <input
                                        type="text"
                                        className="form-input"
                                        style={{ padding: '0.3rem 0.6rem', fontSize: '0.82rem' }}
                                        value={rm.roomName}
                                        placeholder="e.g. Room 101, CS Lab"
                                        onChange={e => handleRoomFieldChange(bIdx, fIdx, rIdx, 'roomName', e.target.value)}
                                        required
                                      />
                                    </td>
                                    <td style={{ padding: '0.4rem 0.5rem', width: '130px' }}>
                                      <input
                                        type="text"
                                        className="form-input"
                                        style={{ padding: '0.3rem 0.6rem', fontSize: '0.82rem' }}
                                        value={rm.roomCode}
                                        placeholder="e.g. A-101"
                                        onChange={e => handleRoomFieldChange(bIdx, fIdx, rIdx, 'roomCode', e.target.value)}
                                        required
                                      />
                                    </td>
                                    <td style={{ padding: '0.4rem 0.5rem', width: '150px' }}>
                                      <select
                                        className="form-select"
                                        style={{ padding: '0.3rem 0.6rem', fontSize: '0.82rem' }}
                                        value={rm.roomType}
                                        onChange={e => handleRoomFieldChange(bIdx, fIdx, rIdx, 'roomType', e.target.value)}
                                      >
                                        <option value="LECTURE_HALL">Classroom</option>
                                        <option value="LAB">Laboratory</option>
                                      </select>
                                    </td>
                                    <td style={{ padding: '0.4rem 0.5rem', width: '90px' }}>
                                      <input
                                        type="number"
                                        className="form-input"
                                        style={{ padding: '0.3rem 0.6rem', fontSize: '0.82rem' }}
                                        min="1"
                                        value={rm.capacity}
                                        onChange={e => handleRoomFieldChange(bIdx, fIdx, rIdx, 'capacity', Number(e.target.value))}
                                        required
                                      />
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowHierarchicalModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary" disabled={hierarchicalLoading}>
                  {hierarchicalLoading ? 'Provisioning Rooms…' : `Provision All ${hierarchicalBlocks.length} Blocks & Classrooms`}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
