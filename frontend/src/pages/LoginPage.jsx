import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Lock, User, AlertCircle, ArrowRight, Phone, Mail, Check, Building2 } from 'lucide-react';
import { INSTITUTIONAL_PROGRAMMES } from '../utils/programmes';

export default function LoginPage({ onLogin }) {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('admin123');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  // Personal Details step after role selection / auth
  const [showPersonalDetails, setShowPersonalDetails] = useState(false);
  const [pendingAuthData, setPendingAuthData] = useState(null);
  const [personalForm, setPersonalForm] = useState({
    fullName: '',
    email: '',
    phone: '',
    department: ''
  });
  const [detailsError, setDetailsError] = useState('');

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });

      if (!res.ok) {
        if (res.status === 401) {
          throw new Error('Invalid username or password. Please try again.');
        } else if (res.status === 400) {
          throw new Error('Invalid login data submitted.');
        } else if (res.status >= 500) {
          throw new Error('The server encountered an error. Please try again later.');
        } else {
          const errData = await res.json().catch(() => ({}));
          throw new Error(errData.message || 'An unexpected error occurred.');
        }
      }

      const data = await res.json();
      setPendingAuthData(data);

      // Pre-fill only the fullName from the auth response; leave everything else blank
      setPersonalForm({
        fullName: data.fullName || '',
        email: '',
        phone: '',
        department: ''
      });
      setShowPersonalDetails(true);
    } catch (err) {
      if (err.name === 'TypeError') {
        setError('The backend server is unavailable. Please check your connection.');
      } else {
        setError(err.message);
      }
    } finally {
      setLoading(false);
    }
  };

  const handlePersonalDetailsSubmit = (e) => {
    e.preventDefault();
    setDetailsError('');

    if (!personalForm.fullName.trim()) {
      setDetailsError('Please enter your full name.');
      return;
    }
    if (!personalForm.email.includes('@')) {
      setDetailsError('Please enter a valid institutional email address.');
      return;
    }
    if (!personalForm.phone || personalForm.phone.trim().length < 8) {
      setDetailsError('Please enter a valid phone number.');
      return;
    }

    const mergedUser = {
      ...pendingAuthData,
      fullName: personalForm.fullName,
      email: personalForm.email,
      phone: personalForm.phone,
      department: pendingAuthData.role === 'ROLE_FACULTY' ? personalForm.department : undefined
    };

    localStorage.setItem('odysseus_profile', JSON.stringify(personalForm));
    onLogin(mergedUser);

    if (mergedUser.role === 'ROLE_ADMIN') {
      navigate('/dashboard');
    } else {
      navigate('/my-lesson-plans');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', padding: '1.5rem', background: 'var(--bg-gradient)', backgroundAttachment: 'fixed' }}>
      <div className="fade-in-up" style={{ textAlign: 'center', marginBottom: '2.25rem' }}>
        <div className="brand-wordmark" style={{ fontSize: '2.2rem', lineHeight: 1.1 }}>
          Easwari Engineering College
        </div>
        <p style={{ color: 'var(--text-dim)', fontSize: '0.85rem', marginTop: '0.5rem' }}>
          Academic Management &amp; Smart Timetable Scheduler
        </p>
      </div>

      <div className="glass-panel fade-in-up" style={{ width: '100%', maxWidth: '460px', padding: '28px 34px', boxShadow: '0 12px 40px rgba(0,0,0,0.85)', animationDelay: '0.08s' }}>
        <div style={{ marginBottom: '1.2rem' }}>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 800 }}>Sign In</h2>
          <p style={{ color: 'var(--text-dim)', fontSize: '0.85rem', marginTop: '0.2rem' }}>
            Enter your institutional credentials to continue.
          </p>
        </div>

        {error && (
          <div className="alert alert-danger" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.2rem' }}>
            <AlertCircle size={18} />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleLoginSubmit}>
          <div className="form-group">
            <label className="form-label">Username:</label>
            <div style={{ position: 'relative' }}>
              <User size={18} style={{ position: 'absolute', left: '1rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-dim)' }} />
              <input
                type="text"
                className="form-input"
                style={{ paddingLeft: '2.75rem' }}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="form-group" style={{ marginBottom: '1.4rem' }}>
            <label className="form-label">Password:</label>
            <div style={{ position: 'relative' }}>
              <Lock size={18} style={{ position: 'absolute', left: '1rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-dim)' }} />
              <input
                type="password"
                className="form-input"
                style={{ paddingLeft: '2.75rem' }}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
          </div>

          <button type="submit" className="btn btn-primary" style={{ width: '100%', justifyContent: 'center', padding: '0.9rem', fontSize: '0.95rem' }} disabled={loading}>
            {loading ? 'Authenticating…' : <>Sign In <ArrowRight size={16} /></>}
          </button>
        </form>

        <div style={{ marginTop: '1.25rem', paddingTop: '1.1rem', borderTop: '1px solid var(--border-color)', textAlign: 'center', fontSize: '0.8rem', color: 'var(--text-dim)' }}>
          <p style={{ marginBottom: '0.6rem', fontWeight: 600, color: 'var(--text-muted)' }}>Quick Fill Demo Accounts</p>
          <div style={{ display: 'flex', gap: '0.6rem', justifyContent: 'center' }}>
            <button
              type="button"
              className="btn btn-secondary"
              style={{ fontSize: '0.75rem', padding: '0.4rem 0.8rem', border: '1px solid rgba(168, 85, 247, 0.4)', background: 'rgba(168, 85, 247, 0.1)' }}
              onClick={() => { setUsername('admin'); setPassword('admin123'); }}
            >
              👑 Admin (admin / admin123)
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              style={{ fontSize: '0.75rem', padding: '0.4rem 0.8rem', border: '1px solid rgba(192, 132, 252, 0.4)', background: 'rgba(192, 132, 252, 0.1)' }}
              onClick={() => { setUsername('faculty'); setPassword('faculty123'); }}
            >
              🎓 Faculty (faculty / faculty123)
            </button>
          </div>
        </div>

        <div style={{ marginTop: '1rem', textAlign: 'center' }}>
          <Link to="/" style={{ color: 'var(--text-dim)', fontSize: '0.82rem', textDecoration: 'none' }}>
            ← Back to Landing Page
          </Link>
        </div>
      </div>

      {/* Personal Details Confirmation Modal */}
      {showPersonalDetails && (
        <div className="modal-overlay">
          <div className="modal-card">
            <div className="modal-header">
              <div>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem', padding: '0.2rem 0.6rem', borderRadius: '4px', background: 'rgba(168,85,247,0.2)', color: '#c084fc', fontSize: '0.73rem', fontWeight: 700, marginBottom: '0.45rem' }}>
                  {pendingAuthData?.role === 'ROLE_ADMIN' ? '👑 Administrator' : '🎓 Faculty'}
                </span>
                <h2>Complete Your Profile</h2>
                <p style={{ color: 'var(--text-dim)', fontSize: '0.83rem', marginTop: '0.2rem', fontWeight: 400 }}>Enter your contact details to continue.</p>
              </div>
            </div>

            <form onSubmit={handlePersonalDetailsSubmit}>
              <div className="modal-body">
                {detailsError && (
                  <div className="alert alert-danger">
                    <AlertCircle size={16} /><span>{detailsError}</span>
                  </div>
                )}

                <div className="form-group">
                  <label className="form-label">Full Name *</label>
                  <div style={{ position: 'relative' }}>
                    <User size={16} style={{ position: 'absolute', left: '0.85rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-dim)' }} />
                    <input type="text" className="form-input" style={{ paddingLeft: '2.5rem' }} placeholder="e.g. Dr. Anjali Sharma" value={personalForm.fullName} onChange={e => setPersonalForm({ ...personalForm, fullName: e.target.value })} required />
                  </div>
                </div>

                <div className="form-group">
                  <label className="form-label">Institutional Email *</label>
                  <div style={{ position: 'relative' }}>
                    <Mail size={16} style={{ position: 'absolute', left: '0.85rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-dim)' }} />
                    <input type="email" className="form-input" style={{ paddingLeft: '2.5rem' }} placeholder="e.g. anjali@college.edu" value={personalForm.email} onChange={e => setPersonalForm({ ...personalForm, email: e.target.value })} required />
                  </div>
                </div>

                <div className="form-group">
                  <label className="form-label">Phone Number *</label>
                  <div style={{ position: 'relative' }}>
                    <Phone size={16} style={{ position: 'absolute', left: '0.85rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-dim)' }} />
                    <input type="tel" className="form-input" style={{ paddingLeft: '2.5rem' }} placeholder="e.g. +91 98765 43210" value={personalForm.phone} onChange={e => setPersonalForm({ ...personalForm, phone: e.target.value })} required />
                  </div>
                </div>

                {pendingAuthData?.role === 'ROLE_FACULTY' && (
                  <div className="form-group">
                    <label className="form-label">Department *</label>
                    <select className="form-select" value={personalForm.department} onChange={e => setPersonalForm({ ...personalForm, department: e.target.value })} required>
                      <option value="">-- Select Department --</option>
                      {INSTITUTIONAL_PROGRAMMES.map(prog => (
                        <option key={prog} value={prog}>{prog}</option>
                      ))}
                    </select>
                  </div>
                )}
              </div>

              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowPersonalDetails(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary"><Check size={15} /> Confirm &amp; Enter Portal</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
