import React, { useState } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import ProtectedRoute from './components/ProtectedRoute';

import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import AdminDashboard from './pages/AdminDashboard';
import SubjectManagementPage from './pages/SubjectManagementPage';
import NepRuleManagementPage from './pages/NepRuleManagementPage';
import FacultyManagementPage from './pages/FacultyManagementPage';
import RoomManagementPage from './pages/RoomManagementPage';
import BatchManagementPage from './pages/BatchManagementPage';
import OfferingManagementPage from './pages/OfferingManagementPage';
import CalendarManagementPage from './pages/CalendarManagementPage';
import TimeslotManagementPage from './pages/TimeslotManagementPage';
import GenerateTimetablePage from './pages/GenerateTimetablePage';
import SectionTimetablePage from './pages/SectionTimetablePage';
import SyllabusManagementPage from './pages/SyllabusManagementPage';
import GenerateLessonPlanPage from './pages/GenerateLessonPlanPage';
import MyLessonPlansPage from './pages/MyLessonPlansPage';
import DataReadinessPage from './pages/DataReadinessPage';
import FacultyPortalPage from './pages/FacultyPortalPage';

/** Wraps each routed page with an animated slide-up + correct padding. */
function Page({ children }) {
  const location = useLocation();
  return (
    <div key={location.pathname} className="route-transition">
      {children}
    </div>
  );
}

export default function App() {
  const [user, setUser] = useState(() => {
    try {
      // Migrate legacy key once
      const legacy = localStorage.getItem('odysseus_user');
      if (legacy) { localStorage.setItem('eec_user', legacy); localStorage.removeItem('odysseus_user'); }
      const saved = localStorage.getItem('eec_user');
      return saved ? JSON.parse(saved) : null;
    } catch { return null; }
  });

  const handleLogin  = (data) => { setUser(data); localStorage.setItem('eec_user', JSON.stringify(data)); };
  const handleLogout = ()     => { setUser(null);  localStorage.removeItem('eec_user'); localStorage.removeItem('odysseus_user'); };

  const adminHome = '/dashboard';
  const facultyHome = '/faculty-portal';

  return (
    <Router>
      <div className="app-container">

        {/* ── Sidebar (authenticated only) ─────────────────────────────── */}
        {user && <Sidebar user={user} onLogout={handleLogout} />}

        {/* ── Main content ──────────────────────────────────────────────── */}
        <main className={user ? 'main-content' : ''} style={!user ? { width: '100%' } : {}}>
          <Routes>

            {/* Public */}
            <Route path="/"      element={user ? <Navigate to={user.role === 'ROLE_ADMIN' ? adminHome : facultyHome} replace /> : <LandingPage />} />
            <Route path="/login" element={<LoginPage onLogin={handleLogin} />} />
            <Route path="/signup" element={<SignupPage onLogin={handleLogin} />} />

            {/* Admin ──────────────────────────────────────────────────── */}
            <Route path="/dashboard" element={
              <ProtectedRoute user={user} requiredRole="ROLE_ADMIN">
                <Page><AdminDashboard user={user} /></Page>
              </ProtectedRoute>
            } />

            <Route path="/subjects" element={
              <ProtectedRoute user={user} requiredRole="ROLE_ADMIN">
                <Page><SubjectManagementPage user={user} /></Page>
              </ProtectedRoute>
            } />

            <Route path="/nep-rules" element={
              <ProtectedRoute user={user} requiredRole="ROLE_ADMIN">
                <Page><NepRuleManagementPage user={user} /></Page>
              </ProtectedRoute>
            } />

            <Route path="/faculty" element={
              <ProtectedRoute user={user} requiredRole="ROLE_ADMIN">
                <Page><FacultyManagementPage user={user} /></Page>
              </ProtectedRoute>
            } />

            <Route path="/rooms" element={
              <ProtectedRoute user={user} requiredRole="ROLE_ADMIN">
                <Page><RoomManagementPage user={user} /></Page>
              </ProtectedRoute>
            } />

            <Route path="/batches" element={
              <ProtectedRoute user={user} requiredRole="ROLE_ADMIN">
                <Page><BatchManagementPage user={user} /></Page>
              </ProtectedRoute>
            } />

            <Route path="/offerings" element={
              <ProtectedRoute user={user} requiredRole="ROLE_ADMIN">
                <Page><OfferingManagementPage user={user} /></Page>
              </ProtectedRoute>
            } />

            <Route path="/timeslots" element={
              <ProtectedRoute user={user} requiredRole="ROLE_ADMIN">
                <Page><TimeslotManagementPage user={user} /></Page>
              </ProtectedRoute>
            } />

            <Route path="/generate" element={
              <ProtectedRoute user={user} requiredRole="ROLE_ADMIN">
                <Page><GenerateTimetablePage user={user} /></Page>
              </ProtectedRoute>
            } />

            <Route path="/readiness" element={
              <ProtectedRoute user={user} requiredRole="ROLE_ADMIN">
                <Page><DataReadinessPage user={user} /></Page>
              </ProtectedRoute>
            } />

            {/* Faculty Pages ────────────────────────────────────────── */}
            <Route path="/faculty-portal" element={
              <ProtectedRoute user={user} requiredRole="ROLE_FACULTY">
                <Page><FacultyPortalPage user={user} /></Page>
              </ProtectedRoute>
            } />

            <Route path="/syllabus" element={
              <ProtectedRoute user={user} requiredRole={['ROLE_ADMIN', 'ROLE_FACULTY']}>
                <Page><SyllabusManagementPage user={user} /></Page>
              </ProtectedRoute>
            } />

            <Route path="/lesson-plan" element={
              <ProtectedRoute user={user} requiredRole={['ROLE_ADMIN', 'ROLE_FACULTY']}>
                <Page><GenerateLessonPlanPage user={user} /></Page>
              </ProtectedRoute>
            } />

            <Route path="/my-lesson-plans" element={
              <ProtectedRoute user={user} requiredRole={['ROLE_ADMIN', 'ROLE_FACULTY']}>
                <Page><GenerateLessonPlanPage user={user} /></Page>
              </ProtectedRoute>
            } />

            {/* Shared Admin + Faculty Pages ────────────────────────────── */}
            <Route path="/calendar" element={
              <ProtectedRoute user={user} requiredRole={['ROLE_ADMIN', 'ROLE_FACULTY']}>
                <Page><CalendarManagementPage user={user} /></Page>
              </ProtectedRoute>
            } />

            <Route path="/timetable" element={
              <ProtectedRoute user={user} requiredRole={['ROLE_ADMIN', 'ROLE_FACULTY']}>
                <Page><SectionTimetablePage user={user} /></Page>
              </ProtectedRoute>
            } />

            {/* Catch-all */}
            <Route path="*" element={<Navigate to="/" replace />} />

          </Routes>
        </main>
      </div>
    </Router>
  );
}
