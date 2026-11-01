import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Lock, User, AlertCircle, ArrowRight, Eye, EyeOff, Shield, GraduationCap, Loader2 } from 'lucide-react';

export default function LoginPage({ onLogin }) {
  const [selectedRole, setSelectedRole] = useState('ROLE_ADMIN');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  // Load remembered username on mount
  useEffect(() => {
    const saved = localStorage.getItem('eec_remembered_user');
    if (saved) {
      setUsername(saved);
      setRememberMe(true);
    }
  }, []);

  const validateFields = () => {
    const errors = {};
    if (!username.trim()) errors.username = 'Username is required';
    if (!password) errors.password = 'Password is required';
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!validateFields()) return;
    setLoading(true);

    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: username.trim(), password, role: selectedRole })
      });

      if (!res.ok) {
        if (res.status === 401) {
          const errData = await res.json().catch(() => ({}));
          throw new Error(errData.message || 'Invalid username or password. Please try again.');
        } else if (res.status === 400) {
          const errData = await res.json().catch(() => ({}));
          throw new Error(errData.message || 'Invalid login data submitted.');
        } else if (res.status >= 500) {
          throw new Error('The server encountered an error. Please try again later.');
        } else {
          const errData = await res.json().catch(() => ({}));
          throw new Error(errData.message || 'An unexpected error occurred.');
        }
      }

      const data = await res.json();

      // Remember username if checkbox is checked
      if (rememberMe) {
        localStorage.setItem('eec_remembered_user', username.trim());
      } else {
        localStorage.removeItem('eec_remembered_user');
      }

      onLogin(data);

      if (data.role === 'ROLE_ADMIN') {
        // Reset academic context on fresh admin login so all numbers start as 0
        localStorage.removeItem('academicContext_dept');
        localStorage.removeItem('academicContext_semester');
        navigate('/dashboard');
      } else {
        navigate('/faculty-portal');
      }
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

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', padding: '1.5rem', background: 'var(--bg-gradient)', backgroundAttachment: 'fixed' }}>
      <div className="fade-in-up" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', marginBottom: '2.25rem', width: '100%' }}>
        <img
          src="/easwari-logo.png"
          alt="Easwari Engineering College"
          style={{ height: '80px', maxWidth: '340px', width: 'auto', marginBottom: '0.85rem', objectFit: 'contain', display: 'block', margin: '0 auto 0.85rem auto' }}
        />
        <div className="brand-wordmark" style={{ fontSize: '2.2rem', lineHeight: 1.1, textAlign: 'center', width: '100%' }}>
          Easwari Engineering College
        </div>
        <p style={{ color: 'var(--text-dim)', fontSize: '0.85rem', marginTop: '0.5rem', textAlign: 'center' }}>
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

        {/* Role Selector Toggle */}
        <div style={{ display: 'flex', gap: '0', marginBottom: '1.2rem', borderRadius: '8px', overflow: 'hidden', border: '1px solid var(--border-color)' }}>
          <button
            type="button"
            onClick={() => setSelectedRole('ROLE_ADMIN')}
            style={{
              flex: 1, padding: '0.65rem 0.5rem', border: 'none', cursor: 'pointer',
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.4rem',
              fontWeight: 700, fontSize: '0.82rem', transition: 'all 0.2s',
              background: selectedRole === 'ROLE_ADMIN' ? 'var(--accent-primary, #dc2626)' : 'transparent',
              color: selectedRole === 'ROLE_ADMIN' ? '#fff' : 'var(--text-muted)',
            }}
          >
            <Shield size={15} /> Admin
          </button>
          <button
            type="button"
            onClick={() => setSelectedRole('ROLE_FACULTY')}
            style={{
              flex: 1, padding: '0.65rem 0.5rem', border: 'none', cursor: 'pointer',
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.4rem',
              fontWeight: 700, fontSize: '0.82rem', transition: 'all 0.2s',
              borderLeft: '1px solid var(--border-color)',
              background: selectedRole === 'ROLE_FACULTY' ? 'var(--accent-primary, #dc2626)' : 'transparent',
              color: selectedRole === 'ROLE_FACULTY' ? '#fff' : 'var(--text-muted)',
            }}
          >
            <GraduationCap size={15} /> Faculty
          </button>
        </div>

        {error && (
          <div className="alert alert-danger" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.2rem' }}>
            <AlertCircle size={18} />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleLoginSubmit}>
          <div className="form-group">
            <label className="form-label">Username / Email / Faculty ID:</label>
            <div style={{ position: 'relative' }}>
              <User size={18} style={{ position: 'absolute', left: '1rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-dim)' }} />
              <input
                type="text"
                className="form-input"
                style={{ paddingLeft: '2.75rem' }}
                placeholder="Enter your username, email or faculty ID"
                value={username}
                onChange={(e) => { setUsername(e.target.value); setFieldErrors(f => ({ ...f, username: '' })); }}
                autoComplete="username"
              />
            </div>
            {fieldErrors.username && <span style={{ color: 'var(--accent-danger, #ef4444)', fontSize: '0.75rem', marginTop: '0.25rem', display: 'block' }}>{fieldErrors.username}</span>}
          </div>

          <div className="form-group" style={{ marginBottom: '0.8rem' }}>
            <label className="form-label">Password:</label>
            <div style={{ position: 'relative' }}>
              <Lock size={18} style={{ position: 'absolute', left: '1rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-dim)' }} />
              <input
                type={showPassword ? 'text' : 'password'}
                className="form-input"
                style={{ paddingLeft: '2.75rem', paddingRight: '2.75rem' }}
                placeholder="Enter your password"
                value={password}
                onChange={(e) => { setPassword(e.target.value); setFieldErrors(f => ({ ...f, password: '' })); }}
                autoComplete="current-password"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                style={{ position: 'absolute', right: '0.75rem', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-dim)', padding: '0.2rem', display: 'flex' }}
                tabIndex={-1}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {fieldErrors.password && <span style={{ color: 'var(--accent-danger, #ef4444)', fontSize: '0.75rem', marginTop: '0.25rem', display: 'block' }}>{fieldErrors.password}</span>}
          </div>

          {/* Remember Me + Forgot Password row */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.4rem', fontSize: '0.8rem' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer', color: 'var(--text-muted)' }}>
              <input
                type="checkbox"
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
                style={{ accentColor: 'var(--accent-primary, #dc2626)' }}
              />
              Remember me
            </label>
            <span
              style={{ color: 'var(--text-dim)', cursor: 'pointer', textDecoration: 'underline' }}
              title="Contact your system administrator to reset your password"
              onClick={() => alert('Please contact your system administrator to reset your password.\n\nEmail: admin@easwari.edu.in')}
            >
              Forgot password?
            </span>
          </div>

          <button type="submit" className="btn btn-primary" style={{ width: '100%', justifyContent: 'center', padding: '0.9rem', fontSize: '0.95rem' }} disabled={loading}>
            {loading ? <><Loader2 size={16} style={{ animation: 'spin 1s linear infinite' }} /> Authenticating…</> : <>Sign In <ArrowRight size={16} /></>}
          </button>
        </form>

        <div style={{ marginTop: '1.25rem', paddingTop: '1.1rem', borderTop: '1px solid var(--border-color)', textAlign: 'center', fontSize: '0.8rem', color: 'var(--text-dim)' }}>
          <p style={{ marginBottom: '0.6rem', fontWeight: 600, color: 'var(--text-muted)' }}>Quick Fill Demo Accounts</p>
          <div style={{ display: 'flex', gap: '0.6rem', justifyContent: 'center' }}>
            <button
              type="button"
              className="btn btn-secondary"
              style={{ fontSize: '0.75rem', padding: '0.4rem 0.8rem', border: '1px solid rgba(168, 85, 247, 0.4)', background: 'rgba(168, 85, 247, 0.1)' }}
              onClick={() => { setUsername('admin'); setPassword('admin123'); setSelectedRole('ROLE_ADMIN'); }}
            >
              👑 Admin
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              style={{ fontSize: '0.75rem', padding: '0.4rem 0.8rem', border: '1px solid rgba(192, 132, 252, 0.4)', background: 'rgba(192, 132, 252, 0.1)' }}
              onClick={() => { setUsername('faculty'); setPassword('faculty123'); setSelectedRole('ROLE_FACULTY'); }}
            >
              🎓 Faculty
            </button>
          </div>
        </div>

        <div style={{ marginTop: '1rem', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
          <Link to="/signup" style={{ color: 'var(--accent-primary, #dc2626)', fontSize: '0.82rem', textDecoration: 'none', fontWeight: 600 }}>
            Don't have an account? Create an account →
          </Link>
          <Link to="/" style={{ color: 'var(--text-dim)', fontSize: '0.82rem', textDecoration: 'none' }}>
            ← Back to Landing Page
          </Link>
        </div>
      </div>

      {/* Spinner keyframe for the loading icon */}
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}
