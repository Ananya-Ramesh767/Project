import React, { useMemo, useState } from 'react';
import { CalendarDays, Landmark, Building2, Sparkles, Info, BookOpen, GraduationCap, CheckCircle } from 'lucide-react';
import Modal from './Modal';

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];
const WEEKDAY_LABELS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

const typeMeta = {
  GOVERNMENT: { color: '#ef4444', label: 'Government Holiday', icon: Landmark, dotColor: '#ef4444', bg: 'rgba(239,68,68,0.1)' },
  State: { color: '#ef4444', label: 'Government Holiday', icon: Landmark, dotColor: '#ef4444', bg: 'rgba(239,68,68,0.1)' },
  COLLEGE: { color: '#38bdf8', label: 'College / Institutional', icon: Building2, dotColor: '#38bdf8', bg: 'rgba(56,189,248,0.1)' },
  OTHER: { color: '#60a5fa', label: 'Restricted / Optional', icon: Info, dotColor: '#60a5fa', bg: 'rgba(96,165,250,0.1)' },
  UNEXPECTED: { color: '#a855f7', label: 'Unexpected Holiday', icon: Sparkles, dotColor: '#a855f7', bg: 'rgba(168,85,247,0.1)' },
  ASSESSMENT: { color: '#f59e0b', label: 'Unit Test / Examination', icon: BookOpen, dotColor: '#f59e0b', bg: 'rgba(245,158,11,0.12)' },
  UNIT_TEST: { color: '#f59e0b', label: 'Unit Test', icon: BookOpen, dotColor: '#f59e0b', bg: 'rgba(245,158,11,0.12)' },
  CAT: { color: '#f59e0b', label: 'CAT Examination', icon: BookOpen, dotColor: '#f59e0b', bg: 'rgba(245,158,11,0.12)' },
  EXAM: { color: '#f59e0b', label: 'Semester Exam', icon: GraduationCap, dotColor: '#f59e0b', bg: 'rgba(245,158,11,0.12)' },
  VACATION: { color: '#10b981', label: 'Vacation / Recess', icon: CheckCircle, dotColor: '#10b981', bg: 'rgba(16,185,129,0.1)' }
};

export function metaFor(event) {
  if (!event) return typeMeta.OTHER;
  if (event.unexpected) return typeMeta.UNEXPECTED;
  const t = (event.type || '').toUpperCase();
  const name = (event.name || '').toLowerCase();
  if (t === 'ASSESSMENT' || name.includes('unit test') || name.includes('cat') || name.includes('exam') || name.includes('internal') || name.includes('assessment')) {
    return typeMeta.ASSESSMENT;
  }
  if (t === 'VACATION' || name.includes('vacation') || name.includes('recess')) {
    return typeMeta.VACATION;
  }
  return typeMeta[t] || typeMeta[event.type] || typeMeta.OTHER;
}

function buildMonthMatrix(year, monthIndex) {
  const firstDay = new Date(year, monthIndex, 1);
  const startWeekday = firstDay.getDay();
  const daysInMonth = new Date(year, monthIndex + 1, 0).getDate();
  const cells = [];
  for (let i = 0; i < startWeekday; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
}

export default function YearCalendar({ year, holidays = [], canManage = false, onAddUnexpected, onRemoveUnexpected }) {
  const [activeDay, setActiveDay] = useState(null);
  const [hoveredDay, setHoveredDay] = useState(null); // { key, events, x, y }

  // Group events by date (supporting multiple events on the same date)
  const eventMap = useMemo(() => {
    const map = {};
    holidays.forEach(h => {
      const d = h.holidayDate || h.date;
      if (!d) return;
      if (!map[d]) map[d] = [];
      map[d].push(h);
    });
    return map;
  }, [holidays]);

  const dateKey = (y, m, d) => `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;

  const govtCount = holidays.filter(h => !h.unexpected && (h.type === 'GOVERNMENT' || h.type === 'State')).length;
  const examCount = holidays.filter(h => {
    const t = (h.type || '').toUpperCase();
    const n = (h.name || '').toLowerCase();
    return t === 'ASSESSMENT' || n.includes('test') || n.includes('exam') || n.includes('cat');
  }).length;
  const unexpectedCount = holidays.filter(h => h.unexpected).length;

  return (
    <div style={{ position: 'relative' }}>
      {/* Header row: title + legend */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '0.75rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
          <CalendarDays size={20} style={{ color: 'var(--accent-primary)' }} />
          <div>
            <div style={{ fontWeight: 800, fontSize: '1.05rem' }}>Yearly Calendar — {year}</div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-dim)' }}>
              {govtCount} Govt Holidays &middot; {examCount} Academic Exams/Tests &middot; {unexpectedCount} Unexpected Days
            </div>
          </div>
        </div>

        {/* Legend */}
        <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', fontSize: '0.75rem', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#ef4444', display: 'inline-block', boxShadow: '0 0 4px rgba(239,68,68,0.7)' }} />
            <span style={{ color: '#f87171', fontWeight: 700 }}>Govt Holiday</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#f59e0b', display: 'inline-block', boxShadow: '0 0 4px rgba(245,158,11,0.7)' }} />
            <span style={{ color: '#fbbf24', fontWeight: 700 }}>Exam / Unit Test</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#38bdf8', display: 'inline-block' }} />
            <span style={{ color: '#38bdf8' }}>College Event</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#a855f7', display: 'inline-block' }} />
            <span style={{ color: '#c084fc' }}>Unexpected</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#10b981', display: 'inline-block' }} />
            <span style={{ color: '#34d399' }}>Vacation</span>
          </div>
        </div>
      </div>

      {/* Floating Hover Tooltip / Popover */}
      {hoveredDay && hoveredDay.events && hoveredDay.events.length > 0 && (
        <div
          style={{
            position: 'fixed',
            left: Math.min(hoveredDay.x + 12, window.innerWidth - 280),
            top: Math.max(hoveredDay.y - 40, 10),
            zIndex: 9999,
            background: 'rgba(15, 23, 42, 0.96)',
            border: '1px solid rgba(99, 102, 241, 0.4)',
            backdropFilter: 'blur(8px)',
            borderRadius: '8px',
            padding: '0.65rem 0.85rem',
            boxShadow: '0 8px 24px rgba(0,0,0,0.5)',
            pointerEvents: 'none',
            maxWidth: '280px',
            fontSize: '0.78rem'
          }}
        >
          <div style={{ fontWeight: 800, color: '#e2e8f0', marginBottom: '0.4rem', borderBottom: '1px solid rgba(255,255,255,0.1)', paddingBottom: '0.25rem' }}>
            📅 {hoveredDay.key}
          </div>
          {hoveredDay.events.map((ev, i) => {
            const m = metaFor(ev);
            return (
              <div key={i} style={{ marginBottom: i < hoveredDay.events.length - 1 ? '0.4rem' : 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', color: m.color, fontWeight: 700 }}>
                  <span style={{ width: 6, height: 6, borderRadius: '50%', background: m.color, display: 'inline-block' }} />
                  <span>{ev.name}</span>
                </div>
                <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginLeft: '0.75rem' }}>
                  {m.label} {ev.semester ? `(Sem ${ev.semester})` : ''}
                </div>
                {ev.description && ev.description !== ev.name && (
                  <div style={{ fontSize: '0.68rem', color: '#94a3b8', marginLeft: '0.75rem', marginTop: '1px' }}>
                    {ev.description}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* 12-month grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '1rem' }}>
        {MONTH_NAMES.map((name, monthIndex) => {
          const cells = buildMonthMatrix(year, monthIndex);
          return (
            <div key={name} className="glass-panel" style={{ padding: '1rem' }}>
              <div style={{ fontWeight: 700, fontSize: '0.9rem', marginBottom: '0.65rem' }}>{name}</div>
              {/* Weekday headers */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: '2px', marginBottom: '0.3rem' }}>
                {WEEKDAY_LABELS.map((w, i) => (
                  <div key={i} style={{ textAlign: 'center', fontSize: '0.65rem', color: 'var(--text-dim)', fontWeight: 700 }}>{w}</div>
                ))}
              </div>
              {/* Date cells */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: '2px' }}>
                {cells.map((d, idx) => {
                  if (d === null) return <div key={idx} />;
                  const key = dateKey(year, monthIndex, d);
                  const dayEvents = eventMap[key] || [];
                  const hasEvents = dayEvents.length > 0;
                  const isSunday = idx % 7 === 0;

                  // Find primary event for styling
                  const primaryEvent = dayEvents[0];
                  const primaryMeta = primaryEvent ? metaFor(primaryEvent) : null;
                  const hasGovt = dayEvents.some(e => e.type === 'GOVERNMENT' || e.type === 'State');
                  const hasExam = dayEvents.some(e => {
                    const t = (e.type || '').toUpperCase();
                    const n = (e.name || '').toLowerCase();
                    return t === 'ASSESSMENT' || n.includes('test') || n.includes('exam') || n.includes('cat');
                  });

                  return (
                    <div
                      key={idx}
                      onClick={() => hasEvents && setActiveDay({ key, events: dayEvents })}
                      onMouseEnter={(e) => {
                        if (hasEvents) {
                          const rect = e.currentTarget.getBoundingClientRect();
                          setHoveredDay({ key, events: dayEvents, x: rect.right, y: rect.top });
                        }
                      }}
                      onMouseLeave={() => setHoveredDay(null)}
                      style={{
                        aspectRatio: '1',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        borderRadius: '6px',
                        fontSize: '0.68rem',
                        cursor: hasEvents ? 'pointer' : 'default',
                        background: hasGovt ? 'rgba(239,68,68,0.08)' : hasExam ? 'rgba(245,158,11,0.08)' : hasEvents ? primaryMeta.bg : 'transparent',
                        border: hasGovt ? '1px solid rgba(239,68,68,0.25)' : hasExam ? '1px solid rgba(245,158,11,0.25)' : hasEvents ? `1px solid ${primaryMeta.color}40` : '1px solid transparent',
                        color: hasGovt ? '#f87171' : hasExam ? '#fbbf24' : isSunday ? 'var(--text-dim)' : 'var(--text-muted)',
                        fontWeight: hasEvents ? 800 : 500,
                        gap: '2px',
                        transition: 'background 0.15s, border 0.15s'
                      }}
                    >
                      {/* Date number */}
                      <span>{d}</span>
                      
                      {/* Indicator dots for events */}
                      {hasEvents && (
                        <div style={{ display: 'flex', gap: '2px', alignItems: 'center' }}>
                          {dayEvents.slice(0, 3).map((ev, ei) => {
                            const em = metaFor(ev);
                            return (
                              <span
                                key={ei}
                                style={{
                                  width: 4,
                                  height: 4,
                                  borderRadius: '50%',
                                  background: em.dotColor,
                                  display: 'inline-block',
                                  boxShadow: `0 0 3px ${em.dotColor}aa`
                                }}
                              />
                            );
                          })}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>

      {/* Holiday / Event detail modal */}
      <Modal
        isOpen={!!activeDay}
        onClose={() => setActiveDay(null)}
        title={
          activeDay ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <CalendarDays size={20} style={{ color: 'var(--accent-primary)' }} />
              <span>Events on {activeDay.key}</span>
            </div>
          ) : ''
        }
      >
        {activeDay && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {activeDay.events.map((ev, i) => {
              const m = metaFor(ev);
              return (
                <div
                  key={i}
                  style={{
                    padding: '0.85rem',
                    background: 'rgba(255,255,255,0.03)',
                    borderRadius: '8px',
                    borderLeft: `4px solid ${m.color}`
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      {React.createElement(m.icon, { size: 18, color: m.color })}
                      <strong style={{ fontSize: '0.95rem' }}>{ev.name}</strong>
                    </div>
                    <span
                      className="badge"
                      style={{
                        background: `${m.color}22`,
                        color: m.color,
                        border: `1px solid ${m.color}55`,
                        fontSize: '0.72rem'
                      }}
                    >
                      {m.label}
                    </span>
                  </div>

                  {ev.semester && (
                    <div style={{ fontSize: '0.78rem', color: '#38bdf8', marginBottom: '0.25rem' }}>
                      Applicable Semester: Semester {ev.semester}
                    </div>
                  )}

                  {ev.description && (
                    <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)', margin: '0.25rem 0' }}>
                      {ev.description}
                    </p>
                  )}

                  {!ev.unexpected && (
                    <p style={{ fontSize: '0.72rem', color: 'var(--text-dim)', margin: '0.25rem 0 0 0' }}>
                      Recorded in Academic Calendar. Teaching sessions will observe this constraint.
                    </p>
                  )}

                  {canManage && ev.unexpected && (
                    <div style={{ marginTop: '0.75rem' }}>
                      <button
                        className="btn btn-danger"
                        style={{ padding: '0.3rem 0.6rem', fontSize: '0.75rem' }}
                        onClick={() => {
                          onRemoveUnexpected && onRemoveUnexpected(ev);
                          setActiveDay(null);
                        }}
                      >
                        Remove Unexpected Holiday
                      </button>
                    </div>
                  )}
                </div>
              );
            })}

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
              <button className="btn btn-secondary" onClick={() => setActiveDay(null)}>Close</button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
