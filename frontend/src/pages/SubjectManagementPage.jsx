import React, { useState, useEffect } from 'react';
import LiveCreditCalculator from '../components/LiveCreditCalculator';
import { Plus, Edit2, Trash2, BookOpen, Upload, Filter, Check, AlertCircle, FileText } from 'lucide-react';
import { INSTITUTIONAL_PROGRAMMES, FACULTY_DEPARTMENTS } from '../utils/programmes';
import { SEMESTER_OPTIONS, getStoredAcademicContext, setStoredAcademicContext } from '../utils/academicContext';
import Modal from '../components/Modal';

export default function SubjectManagementPage({ user }) {
  const [subjects, setSubjects] = useState([]);
  const [activeRule, setActiveRule] = useState(null);
  const [showModal, setShowModal] = useState(false);
  const [editId, setEditId] = useState(null);

  // Filters (synchronized with active academic context)
  const initialContext = getStoredAcademicContext();
  const [filterSemester, setFilterSemester] = useState(() => initialContext.semester || '');
  const [filterDept, setFilterDept] = useState(() => initialContext.department || '');

  // Document Upload & Preview
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [uploadFile, setUploadFile] = useState(null);
  const [uploadDept, setUploadDept] = useState(() => initialContext.department || INSTITUTIONAL_PROGRAMMES[4]);
  const [uploadSemester, setUploadSemester] = useState(() => initialContext.semester ? Number(initialContext.semester) : 3);
  const [uploadSectionCount, setUploadSectionCount] = useState(7);
  const [uploadLoading, setUploadLoading] = useState(false);
  const [parsedSubjects, setParsedSubjects] = useState([]);
  const [uploadError, setUploadError] = useState('');
  const [uploadSuccess, setUploadSuccess] = useState('');
  const [uploadTab, setUploadTab] = useState('file'); // 'file' | 'paste'
  const [pasteText, setPasteText] = useState('');
  const [pageSuccess, setPageSuccess] = useState('');

  const getShortDeptName = (dept) => {
    if (!dept) return '';
    if (dept.toLowerCase().includes('computer science')) return 'CSE';
    if (dept.toLowerCase().includes('information')) return 'IT';
    if (dept.toLowerCase().includes('mathematics')) return 'Math';
    if (dept.toLowerCase().includes('physics')) return 'Physics';
    if (dept.toLowerCase().includes('chemistry')) return 'Chemistry';
    if (dept.toLowerCase().includes('english')) return 'English';
    if (dept.toLowerCase().includes('biomedical')) return 'BME';
    if (dept.toLowerCase().includes('mechanical')) return 'MECH';
    if (dept.toLowerCase().includes('civil')) return 'CIVIL';
    return dept.split(' ')[0];
  };

  const [form, setForm] = useState({
    subjectCode: '',
    subjectName: '',
    department: INSTITUTIONAL_PROGRAMMES[4],
    semester: 3,
    subjectType: 'THEORY',
    theoryCredits: 3,
    labCredits: 0,
    tutorialCredits: 0,
    requiresLab: false,
    labDuration: 2,
    requiredFacultyCount: 1
  });

  const [error, setError] = useState('');

  useEffect(() => {
    fetchSubjects();
    fetchActiveRule();
  }, []);

  const fetchSubjects = async () => {
    try {
      const res = await fetch('/api/admin/subjects', {
        headers: { 'Authorization': `Bearer ${user.token}` }
      });
      if (res.ok) setSubjects(await res.json());
    } catch (e) {
      console.error(e);
    }
  };

  const fetchActiveRule = async () => {
    try {
      const res = await fetch('/api/admin/nep-rules', {
        headers: { 'Authorization': `Bearer ${user.token}` }
      });
      if (res.ok) {
        const rules = await res.json();
        const active = rules.find(r => r.active);
        setActiveRule(active || null);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    const url = editId ? `/api/admin/subjects/${editId}` : '/api/admin/subjects';
    const method = editId ? 'PUT' : 'POST';

    try {
      const res = await fetch(url, {
        method,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${user.token}`
        },
        body: JSON.stringify(form)
      });

      if (!res.ok) {
        const errData = await res.json();
        throw new Error(errData.error || 'Failed to save subject');
      }

      setShowModal(false);
      resetForm();
      fetchSubjects();
    } catch (err) {
      setError(err.message);
    }
  };

  const handleEdit = (sub) => {
    setEditId(sub.id);
    setForm({
      subjectCode: sub.subjectCode,
      subjectName: sub.subjectName,
      department: sub.department,
      semester: sub.semester || 5,
      subjectType: sub.subjectType,
      theoryCredits: sub.theoryCredits,
      labCredits: sub.labCredits,
      tutorialCredits: sub.tutorialCredits,
      requiresLab: sub.requiresLab,
      labDuration: sub.labDuration || 2,
      requiredFacultyCount: sub.requiredFacultyCount || 1
    });
    setShowModal(true);
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Are you sure you want to deactivate this subject?')) return;
    try {
      await fetch(`/api/admin/subjects/${id}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${user.token}` }
      });
      fetchSubjects();
    } catch (e) {
      console.error(e);
    }
  };

  const resetForm = () => {
    setEditId(null);
    setForm({
      subjectCode: '',
      subjectName: '',
      department: 'CSE',
      semester: 5,
      subjectType: 'THEORY',
      theoryCredits: 3,
      labCredits: 0,
      tutorialCredits: 0,
      requiresLab: false,
      labDuration: 2,
      requiredFacultyCount: 1
    });
  };



  const handlePasteParse = async (e) => {
    e.preventDefault();
    if (!pasteText.trim()) {
      setUploadError('Please paste syllabus text or tabular lines.');
      return;
    }
    setUploadError('');
    setUploadSuccess('');
    setUploadLoading(true);

    try {
      const res = await fetch('/api/admin/subjects/parse-text', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${user.token}`
        },
        body: JSON.stringify({
          rawText: pasteText,
          defaultSemester: uploadSemester,
          defaultDepartment: uploadDept
        })
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.error || 'Failed to parse text content');
      }

      const data = await res.json();
      const extractedList = Array.isArray(data) ? data : (data.preview || []);
      if (!extractedList || extractedList.length === 0) {
        setUploadError('Could not recognize courses from the pasted text. Try loading the Semester 3 preset.');
      } else {
        setParsedSubjects(extractedList);
        setUploadSuccess(`Extracted ${extractedList.length} subjects successfully! Review and edit below before committing.`);
      }
    } catch (err) {
      setUploadError(err.message);
    } finally {
      setUploadLoading(false);
    }
  };

  const handleUploadParse = async (e) => {
    e.preventDefault();
    if (!uploadFile) {
      setUploadError('Please choose a file to upload.');
      return;
    }
    setUploadError('');
    setUploadSuccess('');
    setUploadLoading(true);

    try {
      const formData = new FormData();
      formData.append('file', uploadFile);
      formData.append('department', uploadDept);
      formData.append('semester', uploadSemester);

      const res = await fetch('/api/admin/subjects/parse-document', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${user.token}` },
        body: formData
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.error || errData.message || 'Failed to parse syllabus document');
      }

      const data = await res.json();
      const extractedList = Array.isArray(data) ? data : (data.preview || []);
      if (!extractedList || extractedList.length === 0) {
        if (data.isScannedImage) {
          setUploadError('SCANNED_IMAGE');
        } else {
          setUploadError('No valid subjects could be parsed from the document text. Please verify the format or use the fast loader below.');
        }
      } else {
        setParsedSubjects(extractedList);
        setUploadSuccess(`Extracted ${extractedList.length} subjects successfully! Review and edit below before committing.`);
      }
    } catch (err) {
      setUploadError(err.message);
    } finally {
      setUploadLoading(false);
    }
  };

  const handleBatchSave = async () => {
    if (parsedSubjects.length === 0) return;
    setUploadLoading(true);
    setUploadError('');

    try {
      const res = await fetch('/api/admin/subjects/batch-save', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${user.token}`
        },
        body: JSON.stringify(parsedSubjects)
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.error || 'Failed to save parsed subjects');
      }

      const saved = await res.json();
      const count = saved.savedCount || (Array.isArray(saved) ? saved.length : parsedSubjects.length);

      // Automatically generate/update the requested number of sections for this programme & semester
      if (uploadSectionCount && uploadSectionCount > 0) {
        await fetch('/api/admin/batches/generate-sections', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${user.token}`
          },
          body: JSON.stringify({
            department: uploadDept,
            semester: uploadSemester,
            sectionCount: Number(uploadSectionCount),
            strength: 60,
            academicYear: '2026-2027'
          })
        }).catch(err => console.warn('Could not auto-generate sections:', err));
      }

      // Close modal, clear preview, retain active context and refresh main table
      setShowUploadModal(false);
      setParsedSubjects([]);
      setUploadFile(null);
      setFilterSemester(String(uploadSemester));
      setFilterDept(uploadDept);
      localStorage.setItem('academicContext_dept', uploadDept);
      localStorage.setItem('academicContext_semester', String(uploadSemester));
      setPageSuccess(`✓ Successfully saved ${count} subjects and configured ${uploadSectionCount} section(s) for ${uploadDept} Sem ${uploadSemester}. The subjects are now listed below.`);
      fetchSubjects();
    } catch (err) {
      setUploadError(err.message);
    } finally {
      setUploadLoading(false);
    }
  };

  const filteredSubjects = subjects.filter(sub => {
    if (!filterSemester || filterSemester === '') return false;
    if (filterSemester !== 'ALL' && sub.semester !== Number(filterSemester)) return false;
    if (!filterDept || filterDept === '') return false;
    if (filterDept !== 'ALL' && sub.department !== filterDept) return false;
    return true;
  });

  const handleDeptFilter = (dept) => {
    setFilterDept(dept);
    if (dept && dept !== 'ALL') setStoredAcademicContext({ department: dept });
  };

  const handleSemesterFilter = (sem) => {
    setFilterSemester(sem);
    if (sem && sem !== 'ALL') setStoredAcademicContext({ semester: sem });
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Subjects & Credit Management</h1>
          <p className="page-subtitle">Manage courses, credits, live weekly-hour requirements, and syllabus imports.</p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button className="btn btn-secondary" onClick={() => { setShowUploadModal(true); setUploadError(''); setUploadSuccess(''); }}>
            <Upload size={18} /> Upload Syllabus / Curriculum PDF
          </button>
          <button className="btn btn-primary" onClick={() => { resetForm(); setShowModal(true); }}>
            <Plus size={18} /> Add New Subject
          </button>
        </div>
      </div>

      <div className="glass-panel" style={{ marginBottom: '1.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h3 style={{ fontSize: '0.95rem', fontWeight: 700, marginBottom: '0.35rem' }}>Active NEP Credit Conversion Rule</h3>
          <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>
            Institutional Multipliers: <strong>Theory: {activeRule?.theoryHoursPerCredit || 1}x</strong> | <strong>Lab: {activeRule?.labHoursPerCredit || 2}x</strong> | <strong>Tutorial: {activeRule?.tutorialHoursPerCredit || 1}x</strong>
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>Total Courses:</span>
          <span className="badge badge-success" style={{ fontSize: '0.85rem' }}>{filteredSubjects.length} displayed</span>
        </div>
      </div>

      {/* Page-level success banner after batch save */}
      {pageSuccess && (
        <div className="alert alert-success" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '0.5rem', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Check size={16} />
            <span>{pageSuccess}</span>
          </div>
          <button onClick={() => setPageSuccess('')} style={{ background: 'transparent', border: 'none', color: 'inherit', cursor: 'pointer', fontSize: '1rem' }}>×</button>
        </div>
      )}

      {/* Inline extracted subjects preview — shown on main page when parsedSubjects exist */}
      {parsedSubjects.length > 0 && !showUploadModal && (
        <div className="glass-panel" style={{ marginBottom: '1.5rem', border: '1px solid rgba(139,92,246,0.35)', padding: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem', flexWrap: 'wrap', gap: '0.5rem' }}>
            <div>
              <h4 style={{ fontSize: '0.95rem', fontWeight: 700, margin: 0 }}>Extracted Subjects Preview ({parsedSubjects.length})</h4>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Review and edit before committing to the database.</span>
            </div>
            <div style={{ display: 'flex', gap: '0.6rem' }}>
              <button type="button" className="btn btn-secondary" style={{ fontSize: '0.8rem', padding: '0.35rem 0.7rem' }} onClick={() => setParsedSubjects([])}>
                Discard
              </button>
              <button
                type="button"
                className="btn btn-primary"
                style={{ fontSize: '0.8rem', padding: '0.35rem 0.9rem' }}
                disabled={uploadLoading}
                onClick={handleBatchSave}
              >
                {uploadLoading ? 'Saving...' : `Commit ${parsedSubjects.length} Subjects`}
              </button>
            </div>
          </div>
          <div className="data-table-container" style={{ maxHeight: '280px', overflowY: 'auto' }}>
            <table className="data-table" style={{ minWidth: '1050px' }}>
              <thead>
                <tr>
                  <th style={{ minWidth: '110px' }}>Code</th>
                  <th style={{ minWidth: '220px' }}>Subject Name</th>
                  <th style={{ minWidth: '110px' }}>Type</th>
                  <th style={{ minWidth: '180px' }}>Faculty Department</th>

                  <th style={{ minWidth: '60px', textAlign: 'center' }}>Th</th>
                  <th style={{ minWidth: '60px', textAlign: 'center' }}>Lab</th>
                  <th style={{ minWidth: '60px', textAlign: 'center' }}>Tut</th>
                  <th style={{ minWidth: '90px', textAlign: 'center' }}>Weekly Hrs</th>
                  <th style={{ minWidth: '60px', textAlign: 'center' }}>Remove</th>
                </tr>
              </thead>
              <tbody>
                {parsedSubjects.map((sub, idx) => {
                  const assignedDept = sub.facultyDepartment || sub.proposedFacultyDepartment || sub.department || uploadDept;
                  return (
                    <tr key={idx}>
                      <td>
                        <input type="text" className="form-input" style={{ padding: '0.2rem 0.4rem', fontSize: '0.8rem', width: '110px' }}
                          value={sub.subjectCode}
                          onChange={e => { const c = [...parsedSubjects]; c[idx].subjectCode = e.target.value; setParsedSubjects(c); }}
                        />
                      </td>
                      <td>
                        <input type="text" className="form-input" style={{ padding: '0.2rem 0.4rem', fontSize: '0.8rem', minWidth: '200px', width: '100%' }}
                          value={sub.subjectName}
                          onChange={e => { const c = [...parsedSubjects]; c[idx].subjectName = e.target.value; setParsedSubjects(c); }}
                        />
                      </td>
                      <td><span className="badge badge-warning" style={{ fontSize: '0.7rem' }}>{sub.subjectType || 'THEORY'}</span></td>
                      <td>
                        <select
                          className="form-select"
                          style={{ padding: '0.2rem 0.4rem', fontSize: '0.75rem', width: '100%', minWidth: '160px' }}
                          value={assignedDept}
                          onChange={e => {
                            const c = [...parsedSubjects];
                            c[idx].facultyDepartment = e.target.value;
                            c[idx].proposedFacultyDepartment = e.target.value;
                            setParsedSubjects(c);
                          }}
                        >
                          {FACULTY_DEPARTMENTS.map(prog => (
                            <option key={prog} value={prog}>{prog}</option>
                          ))}
                        </select>
                      </td>

                      <td style={{ textAlign: 'center' }}>{sub.theoryCredits ?? 0}</td>
                      <td style={{ textAlign: 'center' }}>{sub.labCredits ?? 0}</td>
                      <td style={{ textAlign: 'center' }}>{sub.tutorialCredits ?? 0}</td>
                      <td style={{ textAlign: 'center' }}><strong style={{ color: 'var(--accent-primary)' }}>{sub.calculatedWeeklyHours ?? (sub.theoryCredits + (sub.labCredits * 2) + sub.tutorialCredits)} hrs/wk</strong></td>
                      <td style={{ textAlign: 'center' }}>
                        <button type="button" className="btn btn-danger" style={{ padding: '0.2rem 0.45rem', fontSize: '0.7rem' }}
                          onClick={() => setParsedSubjects(parsedSubjects.filter((_, i) => i !== idx))}>✕</button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

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
        </div>
      </div>

      {/* Subjects Table */}
      <div className="glass-panel">
        <div className="data-table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Subject Name</th>
                <th>Dept / Sem</th>
                <th>Type</th>
                <th>Credits (T/L/Tut)</th>
                <th>Weekly Hours</th>
                <th>Req. Staff</th>
                <th>Lab Session</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredSubjects.map(sub => (
                <tr key={sub.id}>
                  <td><strong>{sub.subjectCode}</strong></td>
                  <td>{sub.subjectName}</td>
                  <td>{sub.department} - Sem {sub.semester}</td>
                  <td><span className="badge badge-warning">{sub.subjectType}</span></td>
                  <td>{sub.theoryCredits} / {sub.labCredits} / {sub.tutorialCredits}</td>
                  <td><strong style={{ color: 'var(--accent-primary)' }}>{sub.calculatedWeeklyHours} hrs/wk</strong></td>
                  <td>
                    <span className="badge badge-secondary" style={{ fontSize: '0.75rem' }}>
                      {sub.requiredFacultyCount || 1} Faculty
                    </span>
                  </td>
                  <td>{sub.requiresLab ? `${sub.labDuration} Periods` : 'N/A'}</td>
                  <td>
                    <span className={`badge ${sub.active ? 'badge-success' : 'badge-danger'}`}>
                      {sub.active ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button className="btn btn-secondary" style={{ padding: '0.4rem 0.6rem' }} onClick={() => handleEdit(sub)}>
                        <Edit2 size={14} />
                      </button>
                      <button className="btn btn-danger" style={{ padding: '0.4rem 0.6rem' }} onClick={() => handleDelete(sub.id)}>
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {filteredSubjects.length === 0 && (
                <tr>
                  <td colSpan="10" style={{ textAlign: 'center', padding: '3.5rem 1rem' }}>
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.75rem' }}>
                      <BookOpen size={44} style={{ color: 'var(--text-muted)', opacity: 0.5 }} />
                      <h4 style={{ fontSize: '1.05rem', fontWeight: 700, margin: 0 }}>
                        {!filterDept || !filterSemester
                          ? 'Please select Department and Semester above to view subjects.'
                          : 'No subjects found for this selection.'}
                      </h4>
                      <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', margin: 0, maxWidth: '450px' }}>
                        Upload the curriculum/syllabus PDF for this department and semester to automatically extract course codes, titles, credits, and weekly hours.
                      </p>
                      <button
                        className="btn btn-primary"
                        style={{ marginTop: '0.5rem', fontSize: '0.85rem' }}
                        onClick={() => {
                          if (filterDept && filterDept !== 'ALL') setUploadDept(filterDept);
                          if (filterSemester && filterSemester !== 'ALL') setUploadSemester(Number(filterSemester));
                          setShowUploadModal(true);
                          setUploadError('');
                          setUploadSuccess('');
                        }}
                      >
                        <Upload size={16} /> Upload Curriculum / Syllabus PDF
                      </button>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal - Add/Edit Subject */}
      <Modal isOpen={showModal} onClose={() => setShowModal(false)} title={editId ? 'Edit Subject' : 'Add New Subject'} maxWidth="650px">
        {error && <div className="alert alert-danger" style={{ marginBottom: '1rem' }}>{error}</div>}

        <form onSubmit={handleSubmit}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label">Subject Code *</label>
              <input type="text" className="form-input" value={form.subjectCode} onChange={e => setForm({ ...form, subjectCode: e.target.value })} required />
            </div>
            <div className="form-group">
              <label className="form-label">Subject Name *</label>
              <input type="text" className="form-input" value={form.subjectName} onChange={e => setForm({ ...form, subjectName: e.target.value })} required />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr 1fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label">Programme / Department *</label>
              <select className="form-select" value={form.department} onChange={e => setForm({ ...form, department: e.target.value })}>
                {INSTITUTIONAL_PROGRAMMES.map(prog => (
                  <option key={prog} value={prog}>{prog}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Semester *</label>
              <input type="number" className="form-input" value={form.semester} onChange={e => setForm({ ...form, semester: Number(e.target.value) })} min="1" max="8" required />
            </div>
            <div className="form-group">
              <label className="form-label">Subject Type *</label>
              <select className="form-select" value={form.subjectType} onChange={e => setForm({ ...form, subjectType: e.target.value })}>
                <option value="THEORY">THEORY</option>
                <option value="LAB">LAB</option>
                <option value="TUTORIAL">TUTORIAL</option>
                <option value="THEORY_AND_LAB">THEORY_AND_LAB</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Req. Staff *</label>
              <input
                type="number"
                className="form-input"
                value={form.requiredFacultyCount || 1}
                onChange={e => setForm({ ...form, requiredFacultyCount: Math.max(1, parseInt(e.target.value) || 1) })}
                min="1"
                max="10"
                required
              />
            </div>
          </div>

          {/* Credits Entry */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label">Theory Credits</label>
              <input type="number" step="0.5" className="form-input" value={form.theoryCredits} onChange={e => setForm({ ...form, theoryCredits: parseFloat(e.target.value) || 0 })} min="0" />
            </div>
            <div className="form-group">
              <label className="form-label">Lab Credits</label>
              <input type="number" step="0.5" className="form-input" value={form.labCredits} onChange={e => {
                const lCred = parseFloat(e.target.value) || 0;
                setForm({ ...form, labCredits: lCred, requiresLab: lCred > 0 });
              }} min="0" />
            </div>
            <div className="form-group">
              <label className="form-label">Tutorial Credits</label>
              <input type="number" step="0.5" className="form-input" value={form.tutorialCredits} onChange={e => setForm({ ...form, tutorialCredits: parseFloat(e.target.value) || 0 })} min="0" />
            </div>
          </div>

          {/* Live Preview */}
          <div style={{ marginBottom: '1.5rem' }}>
            <LiveCreditCalculator
              theoryCredits={form.theoryCredits}
              labCredits={form.labCredits}
              tutorialCredits={form.tutorialCredits}
              activeRule={activeRule}
            />
          </div>

          {form.requiresLab && (
            <div className="form-group glass-panel" style={{ background: 'rgba(245, 158, 11, 0.05)', borderColor: 'rgba(245, 158, 11, 0.3)' }}>
              <label className="form-label">Required Lab Continuous Periods</label>
              <select className="form-select" value={form.labDuration} onChange={e => setForm({ ...form, labDuration: Number(e.target.value) })}>
                <option value={2}>2 Continuous Periods (e.g. P1+P2, P3+P4)</option>
                <option value={3}>3 Continuous Periods (e.g. P3+P4+P5)</option>
              </select>
              <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '0.4rem' }}>
                Note: Lab blocks cannot cross Morning Break (09:55–10:10) or Lunch (12:40–13:30).
              </p>
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
            <button type="submit" className="btn btn-primary">{editId ? 'Save Changes' : 'Create Subject'}</button>
          </div>
        </form>
      </Modal>

      {/* Upload Syllabus / Curriculum Modal */}
      <Modal
        isOpen={showUploadModal}
        onClose={() => { setShowUploadModal(false); }}
        title="Upload Curriculum / Syllabus Document"
        maxWidth="950px"
      >
        {/* Mode Selector & Quick-Load Toolbar */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button
              type="button"
              className={`btn ${uploadTab === 'file' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ fontSize: '0.85rem', padding: '0.4rem 0.8rem' }}
              onClick={() => { setUploadTab('file'); setUploadError(''); }}
            >
              <Upload size={15} /> Upload PDF Document
            </button>
            <button
              type="button"
              className={`btn ${uploadTab === 'paste' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ fontSize: '0.85rem', padding: '0.4rem 0.8rem' }}
              onClick={() => { setUploadTab('paste'); setUploadError(''); }}
            >
              <FileText size={15} /> Paste Text / Table
            </button>
          </div>
        </div>

        {uploadError && (
          <div style={{
            background: 'rgba(239, 68, 68, 0.12)',
            border: '1px solid rgba(239, 68, 68, 0.4)',
            borderRadius: '8px',
            padding: '1rem',
            marginBottom: '1.25rem'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', color: '#fca5a5', fontWeight: 700, marginBottom: '0.4rem' }}>
              <AlertCircle size={18} />
              <span>
                {uploadError === 'SCANNED_IMAGE' ? 'Scanned Document / Photo Detected (No Digital Text)' : uploadError}
              </span>
            </div>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '0.85rem', lineHeight: 1.5 }}>
              {uploadError === 'SCANNED_IMAGE'
                ? 'Your uploaded PDF appears to be a scanned image without a selectable text layer. Please upload a digital PDF document or switch to Paste Text / Table tab to paste curriculum lines directly.'
                : 'Could not extract curriculum lines automatically from the document format. Please verify the document format or switch to Paste Text / Table to paste rows directly.'}
            </p>
            <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn btn-secondary"
                style={{ fontSize: '0.85rem', padding: '0.45rem 0.8rem' }}
                onClick={() => { setUploadTab('paste'); setUploadError(''); }}
              >
                Switch to Paste Text / Table
              </button>
            </div>
          </div>
        )}

        {uploadSuccess && (
          <div className="alert alert-success" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
            <Check size={16} /> <span>{uploadSuccess}</span>
          </div>
        )}

        {uploadTab === 'file' ? (
          <form onSubmit={handleUploadParse} style={{ marginBottom: '1.5rem', background: 'rgba(0,0,0,0.25)', padding: '1.25rem', borderRadius: 'var(--radius-md)' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1.8fr 1.2fr 0.8fr 0.8fr auto', gap: '0.75rem', alignItems: 'end' }}>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Syllabus File (PDF / CSV / TXT) *</label>
                <input
                  type="file"
                  className="form-input"
                  accept=".pdf,.csv,.txt,.doc,.docx"
                  onChange={e => setUploadFile(e.target.files[0])}
                  required
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Programme</label>
                <select className="form-select" value={uploadDept} onChange={e => setUploadDept(e.target.value)}>
                  {INSTITUTIONAL_PROGRAMMES.map(prog => (
                    <option key={prog} value={prog}>{prog}</option>
                  ))}
                </select>
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Semester</label>
                <input
                  type="number"
                  className="form-input"
                  min="1"
                  max="8"
                  value={uploadSemester}
                  onChange={e => setUploadSemester(Number(e.target.value))}
                  required
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Sections</label>
                <input
                  type="number"
                  className="form-input"
                  min="1"
                  max="20"
                  value={uploadSectionCount}
                  onChange={e => setUploadSectionCount(Number(e.target.value))}
                  required
                />
              </div>
              <button type="submit" className="btn btn-primary" disabled={uploadLoading} style={{ padding: '0.65rem 1.1rem' }}>
                {uploadLoading ? 'Extracting…' : 'Extract PDF'}
              </button>
            </div>
          </form>
        ) : (
          <form onSubmit={handlePasteParse} style={{ marginBottom: '1.5rem', background: 'rgba(0,0,0,0.25)', padding: '1.25rem', borderRadius: 'var(--radius-md)' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr 0.8fr', gap: '0.75rem', marginBottom: '0.75rem' }}>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Programme</label>
                <select className="form-select" value={uploadDept} onChange={e => setUploadDept(e.target.value)}>
                  {INSTITUTIONAL_PROGRAMMES.map(prog => (
                    <option key={prog} value={prog}>{prog}</option>
                  ))}
                </select>
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Semester</label>
                <input
                  type="number"
                  className="form-input"
                  min="1"
                  max="8"
                  value={uploadSemester}
                  onChange={e => setUploadSemester(Number(e.target.value))}
                  required
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">Sections</label>
                <input
                  type="number"
                  className="form-input"
                  min="1"
                  max="20"
                  value={uploadSectionCount}
                  onChange={e => setUploadSectionCount(Number(e.target.value))}
                  required
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Paste Course Rows (Code, Title, Periods) *</label>
              <textarea
                className="form-input"
                rows={5}
                placeholder={`2321MAB301T Discrete Mathematics DM 4\n2321CSC301T Computer Networks CN 3\n2321CSC302J Advanced Data Structures and Algorithms ADS 5\n2321CSC303J Fundamentals of AI and ML FAIML 4\n2321CSS301J Embedded System Design ESD 5\n2321CSC304R Object Oriented Programming using Java OOPJ 6\n2321SDA301L Career Skill Development III CSD 2\nAptitude Test AT -\nTechnical Test TT -\nNPTEL NPTEL -\nLeet Code LC -\nCounselling COUN -`}
                value={pasteText}
                onChange={e => setPasteText(e.target.value)}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setPasteText(`2321MAB301T Discrete Mathematics DM 4\n2321CSC301T Computer Networks CN 3\n2321CSC302J Advanced Data Structures and Algorithms ADS 5\n2321CSC303J Fundamentals of Artificial Intelligence and Machine Learning FAIML 4\n2321CSS301J Embedded System Design ESD 5\n2321CSC304R Object Oriented Programming using Java OOPJ 6\n2321SDA301L Career Skill Development III CSD 2\nAptitude Test AT -\nTechnical Test TT -\nNPTEL NPTEL -\nLeet Code LC -\nCounselling COUN -`)}
              >
                Insert Sample Text
              </button>
              <button type="submit" className="btn btn-primary" disabled={uploadLoading}>
                {uploadLoading ? 'Parsing…' : 'Parse Pasted Lines'}
              </button>
            </div>
          </form>
        )}

            {/* Extracted Preview Table */}
            {parsedSubjects.length > 0 && (
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                  <div>
                    <h4 style={{ fontSize: '0.95rem', fontWeight: 700 }}>
                      Extracted Courses Preview ({parsedSubjects.length})
                    </h4>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      Edit codes, names, or credits if needed before committing to the institutional catalog.
                    </span>
                  </div>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    style={{ fontSize: '0.75rem', padding: '0.3rem 0.6rem' }}
                    onClick={() => {
                      setParsedSubjects([
                        ...parsedSubjects,
                        {
                          subjectCode: '',
                          subjectName: '',
                          department: uploadDept,
                          semester: uploadSemester,
                          subjectType: 'THEORY',
                          theoryCredits: 3,
                          labCredits: 0,
                          tutorialCredits: 0,
                          requiresLab: false,
                          labDuration: 2
                        }
                      ]);
                    }}
                  >
                    + Add Missing Course
                  </button>
                </div>

                <div className="data-table-container" style={{ maxHeight: '320px', overflowY: 'auto', marginBottom: '1.25rem' }}>
                  <table className="data-table" style={{ minWidth: '1050px' }}>
                    <thead>
                      <tr>
                        <th style={{ minWidth: '100px' }}>Code</th>
                        <th style={{ minWidth: '220px' }}>Name</th>
                        <th style={{ minWidth: '110px' }}>Type</th>
                        <th style={{ minWidth: '180px' }}>Faculty Department</th>

                        <th style={{ minWidth: '55px', textAlign: 'center' }}>Th</th>
                        <th style={{ minWidth: '55px', textAlign: 'center' }}>Lab</th>
                        <th style={{ minWidth: '55px', textAlign: 'center' }}>Tut</th>
                        <th style={{ minWidth: '70px', textAlign: 'center' }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {parsedSubjects.map((sub, idx) => {
                        const assignedDept = sub.facultyDepartment || sub.proposedFacultyDepartment || sub.department || uploadDept;
                        return (
                          <tr key={idx}>
                            <td>
                              <input
                                type="text"
                                className="form-input"
                                style={{ padding: '0.2rem 0.4rem', fontSize: '0.8rem', width: '100px' }}
                                value={sub.subjectCode}
                                onChange={e => {
                                  const copy = [...parsedSubjects];
                                  copy[idx].subjectCode = e.target.value;
                                  setParsedSubjects(copy);
                                }}
                              />
                            </td>
                            <td>
                              <input
                                type="text"
                                className="form-input"
                                style={{ padding: '0.2rem 0.4rem', fontSize: '0.8rem', minWidth: '200px', width: '100%' }}
                                value={sub.subjectName}
                                onChange={e => {
                                  const copy = [...parsedSubjects];
                                  copy[idx].subjectName = e.target.value;
                                  setParsedSubjects(copy);
                                }}
                              />
                            </td>
                            <td>
                              <select
                                className="form-select"
                                style={{ padding: '0.2rem 0.4rem', fontSize: '0.75rem', width: 'auto' }}
                                value={sub.subjectType}
                                onChange={e => {
                                  const copy = [...parsedSubjects];
                                  copy[idx].subjectType = e.target.value;
                                  setParsedSubjects(copy);
                                }}
                              >
                                <option value="THEORY">THEORY</option>
                                <option value="LAB">LAB</option>
                                <option value="TUTORIAL">TUTORIAL</option>
                                <option value="THEORY_AND_LAB">THEORY_AND_LAB</option>
                              </select>
                            </td>
                            <td>
                              <select
                                className="form-select"
                                style={{ padding: '0.2rem 0.4rem', fontSize: '0.75rem', width: '100%', minWidth: '160px' }}
                                value={assignedDept}
                                onChange={e => {
                                  const copy = [...parsedSubjects];
                                  copy[idx].facultyDepartment = e.target.value;
                                  copy[idx].proposedFacultyDepartment = e.target.value;
                                  setParsedSubjects(copy);
                                }}
                              >
                                {FACULTY_DEPARTMENTS.map(prog => (
                                  <option key={prog} value={prog}>{prog}</option>
                                ))}
                              </select>
                            </td>

                            <td style={{ textAlign: 'center' }}>
                              <input
                                type="number"
                                className="form-input"
                                style={{ padding: '0.2rem 0.4rem', fontSize: '0.8rem', width: '55px' }}
                                value={sub.theoryCredits}
                                onChange={e => {
                                  const copy = [...parsedSubjects];
                                  copy[idx].theoryCredits = parseFloat(e.target.value) || 0;
                                  setParsedSubjects(copy);
                                }}
                              />
                            </td>
                            <td style={{ textAlign: 'center' }}>
                              <input
                                type="number"
                                className="form-input"
                                style={{ padding: '0.2rem 0.4rem', fontSize: '0.8rem', width: '55px' }}
                                value={sub.labCredits}
                                onChange={e => {
                                  const copy = [...parsedSubjects];
                                  copy[idx].labCredits = parseFloat(e.target.value) || 0;
                                  copy[idx].requiresLab = (parseFloat(e.target.value) || 0) > 0;
                                  setParsedSubjects(copy);
                                }}
                              />
                            </td>
                            <td style={{ textAlign: 'center' }}>
                              <input
                                type="number"
                                className="form-input"
                                style={{ padding: '0.2rem 0.4rem', fontSize: '0.8rem', width: '55px' }}
                                value={sub.tutorialCredits}
                                onChange={e => {
                                  const copy = [...parsedSubjects];
                                  copy[idx].tutorialCredits = parseFloat(e.target.value) || 0;
                                  setParsedSubjects(copy);
                                }}
                              />
                            </td>
                            <td style={{ textAlign: 'center' }}>
                              <button
                                type="button"
                                className="btn btn-danger"
                                style={{ padding: '0.25rem 0.5rem', fontSize: '0.7rem' }}
                                onClick={() => {
                                  setParsedSubjects(parsedSubjects.filter((_, i) => i !== idx));
                                }}
                              >
                                Remove
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem' }}>
                  <button type="button" className="btn btn-secondary" onClick={() => setParsedSubjects([])}>
                    Clear Preview
                  </button>
                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={uploadLoading || parsedSubjects.length === 0}
                    onClick={handleBatchSave}
                  >
                    {uploadLoading ? 'Saving...' : `Confirm & Save ${parsedSubjects.length} Courses + ${uploadSectionCount} Sections`}
                  </button>
                </div>
              </div>
            )}
      </Modal>
    </div>
  );
}
