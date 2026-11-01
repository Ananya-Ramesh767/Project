import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard, BookOpen, Users, DoorOpen, Layers,
  BookMarked, Calendar, Clock, Sliders, PlayCircle, LogOut,
  ListChecks, CalendarClock, Table, ShieldCheck, UserCheck
} from 'lucide-react';

const NavItem = ({ to, icon: Icon, children }) => (
  <NavLink to={to} className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
    <Icon size={17} />
    <span>{children}</span>
  </NavLink>
);

export default function Sidebar({ user, onLogout }) {
  const navigate = useNavigate();
  const isAdmin = user?.role === 'ROLE_ADMIN';

  const handleLogout = () => { onLogout(); navigate('/login'); };

  // Strip ROLE_ prefix for display
  const roleLabel = (user?.role || '').replace('ROLE_', '');

  return (
    <aside className="sidebar">

      {/* Brand */}
      <div className="brand-header">
        <div className="brand-logo" style={{ fontSize: '0.9rem', fontWeight: 800 }}>EEC</div>
        <div style={{ minWidth: 0 }}>
          <h1 className="brand-title" style={{ fontSize: '1.05rem', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>Easwari Eng. College</h1>
          <span style={{ fontSize: '0.62rem', color: 'var(--accent-secondary)', letterSpacing: '0.07em', fontWeight: 700, textTransform: 'uppercase' }}>
            {isAdmin ? 'Admin Suite' : 'Faculty Portal'}
          </span>
        </div>
      </div>

      {/* Navigation */}
      <nav className="nav-group">
        {isAdmin && (
          <>
            <div className="nav-section-title">Academic Setup</div>
            <NavItem to="/dashboard"  icon={LayoutDashboard}>Dashboard</NavItem>
            <NavItem to="/nep-rules"  icon={Sliders}>NEP Credit Rules</NavItem>
            <NavItem to="/subjects"   icon={BookOpen}>Subjects &amp; Credits</NavItem>
            <NavItem to="/faculty"    icon={Users}>Faculty Master</NavItem>
            <NavItem to="/rooms"      icon={DoorOpen}>Room Master</NavItem>
            <NavItem to="/batches"    icon={Layers}>Batches &amp; Sections</NavItem>
            <NavItem to="/offerings"  icon={BookMarked}>Subject Offerings</NavItem>
            <NavItem to="/calendar"   icon={Calendar}>Year Calendar</NavItem>
            <NavItem to="/timeslots"  icon={Clock}>Timeslots</NavItem>

            <div className="nav-section-title">Scheduling</div>
            <NavItem to="/readiness"  icon={ShieldCheck}>Data Readiness</NavItem>
            <NavItem to="/generate"   icon={PlayCircle}>Generate Timetable</NavItem>
            <NavItem to="/timetable"  icon={Table}>View Timetables</NavItem>

            <div className="nav-section-title">Academic Delivery</div>
            <NavItem to="/syllabus"    icon={ListChecks}>Syllabus Management</NavItem>
            <NavItem to="/lesson-plan" icon={CalendarClock}>Lesson Plans</NavItem>
          </>
        )}

        {!isAdmin && (
          <>
            <div className="nav-section-title">Faculty Workspace</div>
            <NavItem to="/faculty-portal"  icon={UserCheck}>Faculty Dashboard</NavItem>
            <NavItem to="/timetable"       icon={Table}>My Timetable</NavItem>
            <NavItem to="/syllabus"        icon={ListChecks}>Syllabus Management</NavItem>
            <NavItem to="/lesson-plan"     icon={CalendarClock}>Lesson Plans</NavItem>
            <NavItem to="/calendar"        icon={Calendar}>Year Calendar</NavItem>
          </>
        )}

      </nav>

      {/* User footer */}
      <div style={{ paddingTop: '1rem', borderTop: '1px solid var(--border-color)', flexShrink: 0 }}>
        <div style={{ padding: '0 0.5rem 0.75rem' }}>
          <div style={{ fontSize: '0.87rem', fontWeight: 700, color: 'var(--text-main)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {user?.fullName || user?.username}
          </div>
          <div style={{ fontSize: '0.7rem', color: 'var(--accent-secondary)', fontWeight: 700, letterSpacing: '0.04em', marginTop: '0.15rem' }}>
            {roleLabel}
          </div>
          {user?.department && (
            <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '0.2rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={user.department}>
              🏛️ {user.department}
            </div>
          )}
        </div>
        <button onClick={handleLogout} className="btn btn-secondary" style={{ width: '100%', justifyContent: 'center' }}>
          <LogOut size={15} /> Sign Out
        </button>
      </div>
    </aside>
  );
}
