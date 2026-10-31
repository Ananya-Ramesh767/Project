import React, { useState, useEffect } from 'react';
import { Calendar as CalendarIcon, Plus, AlertTriangle, Check, Trash2, CalendarDays, ListFilter } from 'lucide-react';
import YearCalendar from '../components/YearCalendar';
import Modal from '../components/Modal';
import { getStoredAcademicContext, setStoredAcademicContext } from '../utils/academicContext';

export default function CalendarManagementPage({ user }) {
  const isAdmin = user?.role === 'ROLE_ADMIN';
  const currentSystemYear = new Date().getFullYear();
  const [selectedYear, setSelectedYear] = useState(currentSystemYear);
  const [calendars, setCalendars] = useState([]);
  const [yearHolidays, setYearHolidays] = useState([]);
  const [activeTab, setActiveTab] = useState('year-grid'); // 'year-grid' | 'holiday-list' | 'semester-schedule'

  // Academic Context & Semester Schedule
  const initialContext = getStoredAcademicContext();
  const [selectedSemester, setSelectedSemester] = useState(() => initialContext.semester ? Number(initialContext.semester) : 3);
  
  // Modals
  const [showHolidayModal, setShowHolidayModal] = useState(false);
  const [showUnexpectedModal, setShowUnexpectedModal] = useState(false);
  const [holidayForm, setHolidayForm] = useState({ holidayDate: '', name: '', type: 'GOVERNMENT', description: '' });
  const [unexpectedForm, setUnexpectedForm] = useState({ holidayDate: '', name: '', description: '' });
  const [activeCalId, setActiveCalId] = useState(null);

  useEffect(() => {
    fetchCalendars();
    fetchYearHolidays(selectedYear);
  }, [selectedYear]);

  const fetchCalendars = async () => {
    try {
      const res = await fetch('/api/admin/calendar', { headers: { 'Authorization': `Bearer ${user.token}` } });
      if (res.ok) {
        const data = await res.json();
        setCalendars(data);
        if (data.length > 0) setActiveCalId(data[0].id);
      }
    } catch (e) { console.error(e); }
  };

  const fetchYearHolidays = async (yr) => {
    try {
      const res = await fetch(`/api/admin/calendar/holidays/year/${yr}`, {
        headers: { 'Authorization': `Bearer ${user.token}` }
      });
      if (res.ok) {
        setYearHolidays(await res.json());
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleAddHoliday = async (e) => {
    e.preventDefault();
    if (!activeCalId) return;
    try {
      const res = await fetch(`/api/admin/calendar/${activeCalId}/holidays`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${user.token}` },
        body: JSON.stringify(holidayForm)
      });
      if (res.ok) {
        setShowHolidayModal(false);
        fetchCalendars();
        fetchYearHolidays(selectedYear);
      }
    } catch (e) { console.error(e); }
  };

  const handleAddUnexpected = async (e) => {
    e.preventDefault();
    try {
      const res = await fetch('/api/admin/calendar/holidays/unexpected', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${user.token}` },
        body: JSON.stringify({
          calendarId: activeCalId,
          holidayDate: unexpectedForm.holidayDate,
          name: unexpectedForm.name,
          description: unexpectedForm.description || 'Unexpected institution closure'
        })
      });
      if (res.ok) {
        setShowUnexpectedModal(false);
        setUnexpectedForm({ holidayDate: '', name: '', description: '' });
        fetchCalendars();
        fetchYearHolidays(selectedYear);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleDeleteUnexpected = async (holidayId) => {
    if (!window.confirm('Remove this unexpected holiday? The date will become a regular working day.')) return;
    try {
      const res = await fetch(`/api/admin/calendar/holidays/unexpected/${holidayId}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${user.token}` }
      });
      if (res.ok) {
        fetchCalendars();
        fetchYearHolidays(selectedYear);
      }
    } catch (e) {
      console.error(e);
    }
  };



  const unexpectedHolidays = yearHolidays.filter(h => h.unexpected);
  const regularHolidays = yearHolidays.filter(h => !h.unexpected);

  // Filter calendar and events for selected semester
  const semesterCalendar = calendars.find(c => c.semester === selectedSemester) || calendars[0];
  const semesterHolidays = (semesterCalendar?.holidays || []).filter(h => h.holidayDate);

  // Calculate approximate teaching/working days
  let semesterTeachingDaysCount = 0;
  if (semesterCalendar?.startDate && semesterCalendar?.endDate) {
    const start = new Date(semesterCalendar.startDate);
    const end = new Date(semesterCalendar.endDate);
    const holidayDates = new Set((semesterCalendar.holidays || []).map(h => h.holidayDate));
    let cur = new Date(start);
    while (cur <= end) {
      const day = cur.getDay(); // 0 is Sunday, 6 is Saturday
      const iso = cur.toISOString().split('T')[0];
      if (day !== 0 && day !== 6 && !holidayDates.has(iso)) {
        semesterTeachingDaysCount++;
      }
      cur.setDate(cur.getDate() + 1);
    }
  }

  // Merge yearHolidays and all calendar academic schedule events for selected year
  const allCalendarEvents = React.useMemo(() => {
    const list = [...yearHolidays];
    calendars.forEach(cal => {
      (cal.holidays || []).forEach(h => {
        if (!h.holidayDate) return;
        const hYear = new Date(h.holidayDate).getFullYear();
        if (hYear === selectedYear) {
          const exists = list.find(item => (item.id && item.id === h.id) || (item.holidayDate === h.holidayDate && item.name === h.name));
          if (!exists) {
            list.push({ ...h, semester: cal.semester, academicYear: cal.academicYear });
          } else {
            if (!exists.semester && cal.semester) exists.semester = cal.semester;
            if (!exists.academicYear && cal.academicYear) exists.academicYear = cal.academicYear;
          }
        }
      });
    });
    return list;
  }, [yearHolidays, calendars, selectedYear]);

  /* Tab 1: 12-Month Interactive Calendar Grid */
  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Year Calendar &amp; Academic Schedule</h1>
          <p className="page-subtitle">12-month calendar view with marked Central &amp; State Government Holidays, term schedules, examinations, and unexpected holiday declarations.</p>
        </div>
        {isAdmin && (
          <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
            <button
              className="btn btn-secondary"
              style={{ border: '1px solid rgba(239, 68, 68, 0.4)', background: 'rgba(239, 68, 68, 0.1)', color: '#fca5a5' }}
              onClick={() => setShowUnexpectedModal(true)}
            >
              <AlertTriangle size={18} /> Declare Unexpected Holiday
            </button>
            <button className="btn btn-secondary" onClick={() => setShowHolidayModal(true)}>
              <Plus size={18} /> Add Term Holiday
            </button>
          </div>
        )}
      </div>

      {/* Dynamic Year Picker & Quick Stats */}
      <div className="glass-panel" style={{ marginBottom: '1.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <CalendarIcon size={20} style={{ color: 'var(--accent-primary)' }} />
          <span style={{ fontWeight: 700, fontSize: '0.95rem' }}>Calendar Year Reference:</span>
          <div style={{ display: 'flex', gap: '0.4rem' }}>
            {[currentSystemYear, currentSystemYear + 1].map(yr => (
              <button
                key={yr}
                type="button"
                className={`btn ${selectedYear === yr ? 'btn-primary' : 'btn-secondary'}`}
                style={{ padding: '0.3rem 0.75rem', fontSize: '0.85rem' }}
                onClick={() => setSelectedYear(yr)}
              >
                {yr} {yr === currentSystemYear ? '(Current Year)' : '(Next Year)'}
              </button>
            ))}
          </div>
        </div>

        <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center' }}>
          <button
            className={`btn ${activeTab === 'year-grid' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ padding: '0.4rem 0.8rem', fontSize: '0.8rem' }}
            onClick={() => setActiveTab('year-grid')}
          >
            <CalendarDays size={16} /> 12-Month Grid
          </button>
          <button
            className={`btn ${activeTab === 'holiday-list' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ padding: '0.4rem 0.8rem', fontSize: '0.8rem' }}
            onClick={() => setActiveTab('holiday-list')}
          >
            <ListFilter size={16} /> Holiday List ({allCalendarEvents.length})
          </button>
        </div>
      </div>

      {/* Stats Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
        <div className="glass-panel" style={{ padding: '1rem' }}>
          <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Total Year Holidays &amp; Events</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 800, marginTop: '0.25rem', color: 'var(--accent-primary)' }}>
            {allCalendarEvents.length} Days
          </div>
        </div>
        <div className="glass-panel" style={{ padding: '1rem' }}>
          <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Government / Institutional</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 800, marginTop: '0.25rem', color: '#38bdf8' }}>
            {regularHolidays.length} Days
          </div>
        </div>
        <div className="glass-panel" style={{ padding: '1rem' }}>
          <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Unexpected / Ad-hoc Days</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 800, marginTop: '0.25rem', color: unexpectedHolidays.length > 0 ? '#f87171' : 'var(--accent-success)' }}>
            {unexpectedHolidays.length} Days
          </div>
        </div>
      </div>

      {/* Tab 1: 12-Month Interactive Calendar Grid */}
      {activeTab === 'year-grid' && (
        <div style={{ marginBottom: '2rem' }}>
          <YearCalendar year={selectedYear} holidays={allCalendarEvents} canManage={isAdmin} onAddUnexpected={() => setShowUnexpectedModal(true)} onRemoveUnexpected={(h) => handleDeleteUnexpected(h.id)} user={user} />
        </div>
      )}

      {/* Tab 2: Holiday List View & Term Calendar */}
      {activeTab === 'holiday-list' && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '1.5rem' }}>
          <div className="glass-panel">
            <h3 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '1rem' }}>Active Term Calendars</h3>
            {calendars.map(cal => (
              <div key={cal.id} style={{ background: 'rgba(0,0,0,0.3)', padding: '1rem', borderRadius: 'var(--radius-md)', marginBottom: '1rem' }}>
                <div style={{ fontWeight: 700, fontSize: '1.1rem' }}>AY {cal.academicYear} (Sem {cal.semester})</div>
                <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '0.4rem' }}>
                  Dates: {cal.startDate} to {cal.endDate}
                </div>
                <div style={{ marginTop: '0.75rem', display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                  <span className="badge badge-warning">Saturday: {cal.saturdayRule}</span>
                  <span className="badge badge-warning">Sunday: {cal.sundayRule}</span>
                </div>
              </div>
            ))}
          </div>

          <div className="glass-panel">
            <h3 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '1rem' }}>All Holidays for {selectedYear}</h3>
            <div className="data-table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Holiday Name</th>
                    <th>Type</th>
                    <th>Description</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {yearHolidays.map(h => (
                    <tr key={h.id}>
                      <td><strong>{h.holidayDate}</strong></td>
                      <td>{h.name}</td>
                      <td>
                        <span className={`badge ${h.unexpected ? 'badge-danger' : h.type === 'GOVERNMENT' ? 'badge-warning' : 'badge-success'}`}>
                          {h.unexpected ? 'UNEXPECTED' : h.type}
                        </span>
                      </td>
                      <td>{h.description || 'N/A'}</td>
                      <td>
                        {h.unexpected ? (
                          <button
                            className="btn btn-danger"
                            style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                            onClick={() => handleDeleteUnexpected(h.id)}
                            title="Remove unexpected holiday"
                          >
                            <Trash2 size={13} />
                          </button>
                        ) : (
                          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Term Record</span>
                        )}
                      </td>
                    </tr>
                  ))}
                  {yearHolidays.length === 0 && (
                    <tr>
                      <td colSpan="5" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '2rem' }}>
                        No holidays registered for {selectedYear}.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* Add Holiday Modal */}
      <Modal
        isOpen={showHolidayModal}
        onClose={() => setShowHolidayModal(false)}
        title="Add Term Holiday"
      >
        <form onSubmit={handleAddHoliday}>
          <div className="form-group">
            <label className="form-label">Holiday Date *</label>
            <input type="date" className="form-input" value={holidayForm.holidayDate} onChange={e => setHolidayForm({ ...holidayForm, holidayDate: e.target.value })} required />
          </div>
          <div className="form-group">
            <label className="form-label">Holiday Name *</label>
            <input type="text" className="form-input" value={holidayForm.name} onChange={e => setHolidayForm({ ...holidayForm, name: e.target.value })} required />
          </div>
          <div className="form-group">
            <label className="form-label">Type *</label>
            <select className="form-select" value={holidayForm.type} onChange={e => setHolidayForm({ ...holidayForm, type: e.target.value })}>
              <option value="GOVERNMENT">GOVERNMENT</option>
              <option value="COLLEGE">COLLEGE</option>
              <option value="OTHER">OTHER</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Description</label>
            <input type="text" className="form-input" value={holidayForm.description} onChange={e => setHolidayForm({ ...holidayForm, description: e.target.value })} />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setShowHolidayModal(false)}>Cancel</button>
            <button type="submit" className="btn btn-primary">Save Holiday</button>
          </div>
        </form>
      </Modal>

      {/* Declare Unexpected Holiday Modal */}
      <Modal
        isOpen={showUnexpectedModal}
        onClose={() => setShowUnexpectedModal(false)}
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <AlertTriangle size={22} style={{ color: '#ef4444' }} />
            <span>Declare Unexpected Holiday</span>
          </div>
        }
      >
        <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '1.25rem' }}>
          Emergency institution closure (e.g. cyclone, strike, government advisory order).
        </p>
        <form onSubmit={handleAddUnexpected}>
          <div className="form-group">
            <label className="form-label">Closure Date *</label>
            <input
              type="date"
              className="form-input"
              value={unexpectedForm.holidayDate}
              onChange={e => setUnexpectedForm({ ...unexpectedForm, holidayDate: e.target.value })}
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Reason / Announcement *</label>
            <input
              type="text"
              className="form-input"
              placeholder="e.g. Cyclone Alert - Institutional Holiday"
              value={unexpectedForm.name}
              onChange={e => setUnexpectedForm({ ...unexpectedForm, name: e.target.value })}
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">Details / Circular Reference</label>
            <textarea
              className="form-input"
              rows={2}
              placeholder="e.g. State govt advisory per circular #241/2026"
              value={unexpectedForm.description}
              onChange={e => setUnexpectedForm({ ...unexpectedForm, description: e.target.value })}
            />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '1rem', marginTop: '1.5rem' }}>
            <button type="button" className="btn btn-secondary" onClick={() => setShowUnexpectedModal(false)}>Cancel</button>
            <button type="submit" className="btn btn-danger">Declare Holiday</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
