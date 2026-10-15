import React, { useMemo, useState } from 'react';
import { CalendarDays, Landmark, Building2, Sparkles, Info } from 'lucide-react';
import Modal from './Modal';

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];
const WEEKDAY_LABELS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

const typeMeta = {
  GOVERNMENT: { color: '#f87171', label: 'Government Holiday', icon: Landmark },
  State: { color: '#f87171', label: 'Government Holiday', icon: Landmark },
  COLLEGE: { color: '#fbbf24', label: 'College Holiday', icon: Building2 },
  OTHER: { color: '#60a5fa', label: 'Restricted / Optional', icon: Info },
  UNEXPECTED: { color: '#a855f7', label: 'Unexpected Holiday', icon: Sparkles }
};

function metaFor(holiday) {
  if (holiday.unexpected) return typeMeta.UNEXPECTED;
  return typeMeta[holiday.type] || typeMeta.OTHER;
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

  const holidayMap = useMemo(() => {
    const map = {};
    holidays.forEach(h => {
      if (!h.holidayDate) return;
      map[h.holidayDate] = h;
    });
    return map;
  }, [holidays]);

  const dateKey = (y, m, d) => `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;

  const govtCount = holidays.filter(h => !h.unexpected).length;
  const unexpectedCount = holidays.filter(h => h.unexpected).length;

  return (
    <div>
      {/* Header row: title + legend */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '0.75rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
          <CalendarDays size={20} style={{ color: 'var(--accent-primary)' }} />
          <div>
            <div style={{ fontWeight: 800, fontSize: '1.05rem' }}>Yearly Calendar — {year}</div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-dim)' }}>
              {govtCount} fixed holidays &middot; {unexpectedCount} unexpected / ad-hoc
            </div>
          </div>
        </div>

        {/* Legend */}
        <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', fontSize: '0.75rem', alignItems: 'center' }}>
          {/* Red dot — main holiday legend entry */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <span style={{
              width: 8, height: 8, borderRadius: '50%',
              background: '#ef4444', display: 'inline-block',
              boxShadow: '0 0 4px rgba(239,68,68,0.7)'
            }} />
            <span style={{ color: '#f87171', fontWeight: 700 }}>Holiday</span>
          </div>
          {Object.values(typeMeta).map((m, i) => (
            <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
              <span style={{ width: 10, height: 10, borderRadius: '50%', background: m.color, display: 'inline-block' }} />
              <span style={{ color: 'var(--text-muted)' }}>{m.label}</span>
            </div>
          ))}
        </div>
      </div>

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
                  const holiday = holidayMap[key];
                  const isSunday = idx % 7 === 0;
                  const meta = holiday ? metaFor(holiday) : null;

                  return (
                    <div
                      key={idx}
                      onClick={() => holiday && setActiveDay({ key, holiday })}
                      title={holiday ? `${holiday.name}${meta ? ' — ' + meta.label : ''}` : undefined}
                      style={{
                        aspectRatio: '1',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        borderRadius: '6px',
                        fontSize: '0.68rem',
                        cursor: holiday ? 'pointer' : 'default',
                        /* Subtle red tint background for holidays — no solid fill */
                        background: holiday ? 'rgba(239,68,68,0.08)' : 'transparent',
                        border: holiday ? '1px solid rgba(239,68,68,0.22)' : '1px solid transparent',
                        /* Date number color: red for holiday, dim for Sunday, muted for normal */
                        color: holiday ? '#f87171' : isSunday ? 'var(--text-dim)' : 'var(--text-muted)',
                        fontWeight: holiday ? 800 : 500,
                        gap: '1px',
                        transition: 'background 0.15s, border 0.15s'
                      }}
                    >
                      {/* Date number */}
                      <span>{d}</span>
                      {/* RED DOT — shown only for holidays */}
                      {holiday && (
                        <span style={{
                          width: 5,
                          height: 5,
                          borderRadius: '50%',
                          background: '#ef4444',
                          display: 'inline-block',
                          boxShadow: '0 0 3px rgba(239,68,68,0.9)',
                          flexShrink: 0
                        }} />
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>

      {/* Holiday detail modal */}
      <Modal
        isOpen={!!activeDay}
        onClose={() => setActiveDay(null)}
        title={
          activeDay ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              {React.createElement(metaFor(activeDay.holiday).icon, { size: 20, color: metaFor(activeDay.holiday).color })}
              <span>{activeDay.holiday.name}</span>
            </div>
          ) : ''
        }
      >
        {activeDay && (
          <div>
            <p style={{ fontSize: '0.9rem', color: 'var(--text-muted)', marginBottom: '0.5rem', fontWeight: 600 }}>{activeDay.key}</p>
            <span
              className="badge"
              style={{
                background: `${metaFor(activeDay.holiday).color}22`,
                color: metaFor(activeDay.holiday).color,
                border: `1px solid ${metaFor(activeDay.holiday).color}55`,
                display: 'inline-block',
                marginBottom: '0.75rem'
              }}
            >
              {metaFor(activeDay.holiday).label}
            </span>
            {activeDay.holiday.description && (
              <p style={{ fontSize: '0.85rem', marginTop: '0.5rem', color: 'var(--text-muted)' }}>{activeDay.holiday.description}</p>
            )}
            {!activeDay.holiday.unexpected && (
              <p style={{ fontSize: '0.75rem', marginTop: '0.75rem', color: 'var(--text-dim)' }}>
                This is a registered calendar holiday. Classes will not be scheduled on this date.
              </p>
            )}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
              {canManage && activeDay.holiday.unexpected && (
                <button
                  className="btn btn-danger"
                  onClick={() => { onRemoveUnexpected && onRemoveUnexpected(activeDay.holiday); setActiveDay(null); }}
                >
                  Remove Holiday
                </button>
              )}
              <button className="btn btn-secondary" onClick={() => setActiveDay(null)}>Close</button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
