import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { User, Mail, Phone, Lock, AlertCircle, ArrowRight, Eye, EyeOff, Building2, Loader2, Shield, GraduationCap } from 'lucide-react';
import { FACULTY_DEPARTMENTS } from '../utils/programmes';

export default function SignupPage({ onLogin }) {
  const navigate = useNavigate();
  const [selectedRole, setSelectedRole] = useState('ROLE_ADMIN');
  const [form, setForm] = useState({
    fullName: '', email: '', phone: '', username: '',
    password: '', confirmPassword: '', department: ''
  });
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [loading, setLoading] = useState(false);

  const set = (field) => (e) => {
    setForm(f => ({ ...f, [field]: e.target.value }));
    setFieldErrors(f => ({ ...f, [field]: '' }));
  };

  const validate = () => {
    const e = {};
    if (!form.fullName.trim()) e.fullName = 'Full name is required';
    if (!form.email || !/^[\w.-]+@[\w.-]+\.[a-zA-Z]{2,}$/.test(form.email)) e.email = 'Valid email is required';
    if (!form.phone || !/^\d{10}$/.test(form.phone)) e.phone = 'Phone must be exactly 10 digits';
    if (!form.username || form.username.trim().length < 3) e.username = 'Username must be at least 3 characters';
    if (!form.password || form.password.length < 8) e.password = 'Password must be at least 8 characters';
    if (form.password !== form.confirmPassword) e.confirmPassword = 'Passwords do not match';
    if (selectedRole === 'ROLE_FACULTY' && !form.department) e.department = 'Department is required for faculty accounts';
    setFieldErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!validate()) return;
    setLoading(true);

    try {
      const res = await fetch('/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...form, role: selectedRole })
      });

      const data = await res.json().catch(() => ({}));

      if (!res.ok) {
        throw new Error(data.message || 'Signup failed. Please try again.');
      }

      // Auto-login after signup
      onLogin(data);
      if (data.role === 'ROLE_ADMIN') {
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

  const fieldStyle = { paddingLeft: '2.75rem' };
  const iconStyle = { position: 'absolute', left: '1rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-dim)' };
  const errStyle = { color: 'var(--accent-danger, #ef4444)', fontSize: '0.75rem', marginTop: '0.25rem', display: 'block' };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', padding: '1.5rem', background: 'var(--bg-gradient)', backgroundAttachment: 'fixed' }}>
      <div className="fade-in-up" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', marginBottom: '2.25rem', width: '100%' }}>
        <img src="/easwari-logo.png" alt="Easwari Engineering College" style={{ height: '70px', maxWidth: '320px', width: 'auto', marginBottom: '0.75rem', objectFit: 'contain', display: 'block', margin: '0 auto 0.75rem auto' }} />
        <div className="brand-wordmark" style={{ fontSize: '1.8rem', lineHeight: 1.1, textAlign: 'center', width: '100%' }}>
          Create an Account
        </div>
        <p style={{ color: 'var(--text-dim)', fontSize: '0.85rem', marginTop: '0.5rem', textAlign: 'center' }}>
          Easwari Engineering College — Academic Management
        </p>
      </div>

      <div className="glass-panel fade-in-up" style={{ width: '100%', maxWidth: '480px', padding: '28px 34px', boxShadow: '0 12px 40px rgba(0,0,0,0.85)', animationDelay: '0.08s' }}>
        <div style={{ marginBottom: '1rem' }}>
          <h2 style={{ fontSize: '1.35rem', fontWeight: 800 }}>Account Registration</h2>
          <p style={{ color: 'var(--text-dim)', fontSize: '0.83rem', marginTop: '0.2rem' }}>
            Choose your account role and enter your details to create an account.
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
          <div className="alert alert-danger" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
            <AlertCircle size={18} /><span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          {/* Full Name */}
          <div className="form-group">
            <label className="form-label">Full Name *</label>
            <div style={{ position: 'relative' }}>
              <User size={18} style={iconStyle} />
              <input type="text" className="form-input" style={fieldStyle} placeholder={selectedRole === 'ROLE_ADMIN' ? 'e.g. Administrator' : 'e.g. Dr. Anjali Sharma'} value={form.fullName} onChange={set('fullName')} />
            </div>
            {fieldErrors.fullName && <span style={errStyle}>{fieldErrors.fullName}</span>}
          </div>

          {/* Email */}
          <div className="form-group">
            <label className="form-label">Email *</label>
            <div style={{ position: 'relative' }}>
              <Mail size={18} style={iconStyle} />
              <input type="email" className="form-input" style={fieldStyle} placeholder="e.g. user@easwari.edu.in" value={form.email} onChange={set('email')} />
            </div>
            {fieldErrors.email && <span style={errStyle}>{fieldErrors.email}</span>}
          </div>

          {/* Phone */}
          <div className="form-group">
            <label className="form-label">Phone Number *</label>
            <div style={{ position: 'relative' }}>
              <Phone size={18} style={iconStyle} />
              <input type="tel" className="form-input" style={fieldStyle} placeholder="10-digit number" value={form.phone} onChange={set('phone')} maxLength={10} />
            </div>
            {fieldErrors.phone && <span style={errStyle}>{fieldErrors.phone}</span>}
          </div>

          {/* Department (Required for Faculty, optional/hidden for Admin) */}
          {selectedRole === 'ROLE_FACULTY' && (
            <div className="form-group">
              <label className="form-label">Department *</label>
              <div style={{ position: 'relative' }}>
                <Building2 size={18} style={iconStyle} />
                <select className="form-select" style={{ paddingLeft: '2.75rem' }} value={form.department} onChange={set('department')}>
                  <option value="">-- Select Department --</option>
                  {FACULTY_DEPARTMENTS.map(d => (
                    <option key={d} value={d}>{d}</option>
                  ))}
                </select>
              </div>
              {fieldErrors.department && <span style={errStyle}>{fieldErrors.department}</span>}
            </div>
          )}

          {/* Username */}
          <div className="form-group">
            <label className="form-label">Username *</label>
            <div style={{ position: 'relative' }}>
              <User size={18} style={iconStyle} />
              <input type="text" className="form-input" style={fieldStyle} placeholder="Choose a username (min 3 chars)" value={form.username} onChange={set('username')} />
            </div>
            {fieldErrors.username && <span style={errStyle}>{fieldErrors.username}</span>}
          </div>

          {/* Password */}
          <div className="form-group">
            <label className="form-label">Password *</label>
            <div style={{ position: 'relative' }}>
              <Lock size={18} style={iconStyle} />
              <input type={showPassword ? 'text' : 'password'} className="form-input" style={{ ...fieldStyle, paddingRight: '2.75rem' }} placeholder="Min 8 characters" value={form.password} onChange={set('password')} />
              <button type="button" onClick={() => setShowPassword(!showPassword)} style={{ position: 'absolute', right: '0.75rem', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-dim)', padding: '0.2rem', display: 'flex' }} tabIndex={-1}>
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {fieldErrors.password && <span style={errStyle}>{fieldErrors.password}</span>}
          </div>

          {/* Confirm Password */}
          <div className="form-group" style={{ marginBottom: '1.4rem' }}>
            <label className="form-label">Confirm Password *</label>
            <div style={{ position: 'relative' }}>
              <Lock size={18} style={iconStyle} />
              <input type={showConfirm ? 'text' : 'password'} className="form-input" style={{ ...fieldStyle, paddingRight: '2.75rem' }} placeholder="Re-enter password" value={form.confirmPassword} onChange={set('confirmPassword')} />
              <button type="button" onClick={() => setShowConfirm(!showConfirm)} style={{ position: 'absolute', right: '0.75rem', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-dim)', padding: '0.2rem', display: 'flex' }} tabIndex={-1}>
                {showConfirm ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {fieldErrors.confirmPassword && <span style={errStyle}>{fieldErrors.confirmPassword}</span>}
          </div>

          <button type="submit" className="btn btn-primary" style={{ width: '100%', justifyContent: 'center', padding: '0.9rem', fontSize: '0.95rem' }} disabled={loading}>
            {loading ? <><Loader2 size={16} style={{ animation: 'spin 1s linear infinite' }} /> Creating Account…</> : <>Create {selectedRole === 'ROLE_ADMIN' ? 'Admin' : 'Faculty'} Account <ArrowRight size={16} /></>}
          </button>
        </form>

        <div style={{ marginTop: '1.25rem', paddingTop: '1.1rem', borderTop: '1px solid var(--border-color)', textAlign: 'center' }}>
          <Link to="/login" style={{ color: 'var(--text-dim)', fontSize: '0.82rem', textDecoration: 'none' }}>
            Already have an account? ← Sign In
          </Link>
        </div>
      </div>

      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}
