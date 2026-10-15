import React, { useState, useEffect } from 'react';
import { Sliders, Plus, Check, AlertCircle } from 'lucide-react';

export default function NepRuleManagementPage({ user }) {
  const [rules, setRules] = useState([]);
  const [showModal, setShowModal] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [form, setForm] = useState({
    ruleName: '',
    academicYear: '2026-2027',
    program: 'B.Tech / Undergraduate',
    theoryHoursPerCredit: 1.0,
    labHoursPerCredit: 2.0,
    tutorialHoursPerCredit: 1.0,
    active: true
  });

  const getAuthToken = () => {
    return user?.token || JSON.parse(localStorage.getItem('odysseus_user') || '{}')?.token || '';
  };

  useEffect(() => {
    fetchRules();
  }, []);

  const fetchRules = async () => {
    try {
      const token = getAuthToken();
      const res = await fetch('/api/admin/nep-rules', {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json();
        setRules(data);
      } else {
        console.error('Failed to fetch rules, status:', res.status);
      }
    } catch (e) {
      console.error('Fetch rules error:', e);
    }
  };

  const handleOpenModal = () => {
    setError('');
    setForm({
      ruleName: '',
      academicYear: '2026-2027',
      program: 'B.Tech / Undergraduate',
      theoryHoursPerCredit: 1.0,
      labHoursPerCredit: 2.0,
      tutorialHoursPerCredit: 1.0,
      active: true
    });
    setShowModal(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setSaving(true);

    try {
      const token = getAuthToken();
      const payload = {
        ruleName: form.ruleName.trim(),
        academicYear: form.academicYear || '2026-2027',
        program: form.program || 'B.Tech / Undergraduate',
        theoryHoursPerCredit: parseFloat(form.theoryHoursPerCredit) || 1.0,
        labHoursPerCredit: parseFloat(form.labHoursPerCredit) || 2.0,
        tutorialHoursPerCredit: parseFloat(form.tutorialHoursPerCredit) || 1.0,
        active: true
      };

      const res = await fetch('/api/admin/nep-rules', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify(payload)
      });

      if (res.ok) {
        setShowModal(false);
        setSuccess('Credit conversion rule saved and activated successfully!');
        fetchRules();
      } else {
        const errData = await res.json().catch(() => ({}));
        if (res.status === 401 || res.status === 403) {
          setError('Session expired or unauthorized. Please re-login.');
        } else {
          setError(errData.error || errData.message || `Failed to save rule (HTTP ${res.status})`);
        }
      }
    } catch (e) {
      console.error(e);
      setError('Network connection error while connecting to server.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">NEP Credit-to-Hours Rules</h1>
          <p className="page-subtitle">Configure institutional multipliers for calculating weekly teaching hours.</p>
        </div>
        <button className="btn btn-primary" onClick={handleOpenModal}>
          <Plus size={18} /> Add New Rule
        </button>
      </div>

      {success && (
        <div className="alert alert-success" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Check size={18} />
            <span>{success}</span>
          </div>
          <button onClick={() => setSuccess('')} style={{ background: 'transparent', border: 'none', color: 'inherit', cursor: 'pointer', fontSize: '1.2rem' }}>&times;</button>
        </div>
      )}

      <div className="glass-panel alert alert-info" style={{ marginBottom: '1.5rem' }}>
        <Sliders size={20} />
        <div>
          <strong>Configurable Multipliers:</strong> These credit rules dictate how course credits map to required weekly timetable hours. Changing the active rule automatically updates all subject weekly hour calculations across the system.
        </div>
      </div>

      <div className="glass-panel">
        <div className="data-table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Rule Name</th>
                <th>Academic Year</th>
                <th>Program</th>
                <th>Theory Multiplier</th>
                <th>Lab Multiplier</th>
                <th>Tutorial Multiplier</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {rules.length === 0 ? (
                <tr>
                  <td colSpan="7" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                    No credit rules loaded. Click &quot;Add New Rule&quot; to configure one.
                  </td>
                </tr>
              ) : (
                rules.map(rule => (
                  <tr key={rule.id}>
                    <td><strong>{rule.ruleName}</strong></td>
                    <td>{rule.academicYear}</td>
                    <td>{rule.program}</td>
                    <td>{rule.theoryHoursPerCredit} hrs / credit</td>
                    <td><strong style={{ color: 'var(--accent-secondary)' }}>{rule.labHoursPerCredit} hrs / credit</strong></td>
                    <td>{rule.tutorialHoursPerCredit} hrs / credit</td>
                    <td>
                      <span className={`badge ${rule.active ? 'badge-success' : 'badge-danger'}`}>
                        {rule.active ? 'Active Rule' : 'Inactive'}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {showModal && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div className="glass-panel" style={{ width: '100%', maxWidth: '500px', padding: '2rem' }}>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 800, marginBottom: '1.5rem' }}>Add Credit Conversion Rule</h2>
            
            {error && (
              <div className="alert alert-danger" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem', padding: '0.75rem 1rem' }}>
                <AlertCircle size={18} />
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label className="form-label">Rule Name *</label>
                <input
                  type="text"
                  className="form-input"
                  value={form.ruleName}
                  placeholder="e.g. Standard rule"
                  onChange={e => setForm({ ...form, ruleName: e.target.value })}
                  required
                />
              </div>
              <div className="form-group">
                <label className="form-label">Academic Year</label>
                <input
                  type="text"
                  className="form-input"
                  value={form.academicYear}
                  placeholder="2026-2027"
                  onChange={e => setForm({ ...form, academicYear: e.target.value })}
                />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '1rem' }}>
                <div className="form-group">
                  <label className="form-label">Theory Mult</label>
                  <input
                    type="number"
                    step="0.5"
                    className="form-input"
                    value={form.theoryHoursPerCredit}
                    onChange={e => setForm({ ...form, theoryHoursPerCredit: e.target.value })}
                    required
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Lab Mult</label>
                  <input
                    type="number"
                    step="0.5"
                    className="form-input"
                    value={form.labHoursPerCredit}
                    onChange={e => setForm({ ...form, labHoursPerCredit: e.target.value })}
                    required
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Tutorial Mult</label>
                  <input
                    type="number"
                    step="0.5"
                    className="form-input"
                    value={form.tutorialHoursPerCredit}
                    onChange={e => setForm({ ...form, tutorialHoursPerCredit: e.target.value })}
                    required
                  />
                </div>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1.5rem' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)} disabled={saving}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={saving}>
                  {saving ? 'Saving...' : 'Save & Activate'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
