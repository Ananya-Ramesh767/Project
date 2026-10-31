import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';

export default function LandingPage() {
  const navigate = useNavigate();

  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', padding: '1.5rem' }}>
      <div className="fade-in-up" style={{ textAlign: 'center', maxWidth: '620px' }}>
        <div className="brand-wordmark" style={{ fontSize: 'clamp(2.5rem, 6vw, 4.5rem)', lineHeight: 1.1 }}>
          Easwari Engineering College
        </div>
        <p style={{ color: 'var(--text-muted)', fontSize: '1.05rem', marginTop: '1.25rem', maxWidth: '480px', marginLeft: 'auto', marginRight: 'auto', lineHeight: 1.6 }}>
          Academic Management &amp; Smart Timetable Scheduler
        </p>

        <div style={{ marginTop: '2.75rem', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.6rem' }}>
          <button
            className="btn btn-hero"
            onClick={() => navigate('/login')}
            style={{ minWidth: '280px', justifyContent: 'center', padding: '1rem 1.75rem', fontSize: '0.95rem' }}
          >
            Enter Portal <ArrowRight size={17} />
          </button>
          <span style={{ fontSize: '0.78rem', color: 'var(--text-dim)', letterSpacing: '0.04em' }}>Admin &amp; Faculty Portal</span>
        </div>
      </div>
    </div>
  );
}
