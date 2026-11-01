import React, { useState, useEffect, useMemo } from 'react';
import { 
  Clock, Calendar, Layers, ShieldCheck, AlertCircle, CheckCircle2, 
  Trash2, RefreshCw, Eye, BookOpen, Users, DoorOpen, Sparkles, 
  BarChart2, ArrowRightLeft, History, Download, Filter
} from 'lucide-react';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import Modal from '../components/Modal';
import { INSTITUTIONAL_PROGRAMMES } from '../utils/programmes';
import { SEMESTER_OPTIONS, getStoredAcademicContext, setStoredAcademicContext } from '../utils/academicContext';

export default function SectionTimetablePage({ user }) {
  const [batches, setBatches] = useState([]);
  const [allAssignments, setAllAssignments] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [faculty, setFaculty] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState(null);
  const [activeTab, setActiveTab] = useState('grid'); // 'grid' | 'monitoring' | 'room-history'

  // Scope filter state
  const initialContext = getStoredAcademicContext();
  const [departmentFilter, setDepartmentFilter] = useState(() => initialContext.department || 'ALL');
  const [semesterFilter, setSemesterFilter] = useState(() => initialContext.semester || 'ALL');

  useEffect(() => {
    const handleContextChange = (e) => {
      const ctx = e.detail || getStoredAcademicContext();
      setDepartmentFilter(ctx.department || 'ALL');
      setSemesterFilter(ctx.semester || 'ALL');
    };
    window.addEventListener('academicContextChanged', handleContextChange);
    window.addEventListener('storage', handleContextChange);
    return () => {
      window.removeEventListener('academicContextChanged', handleContextChange);
      window.removeEventListener('storage', handleContextChange);
    };
  }, []);

  // Room Relocation Modal State
  const [showRoomModal, setShowRoomModal] = useState(false);
  const [selectedAssignment, setSelectedAssignment] = useState(null);
  const [newRoomId, setNewRoomId] = useState('');
  const [roomChangeReason, setRoomChangeReason] = useState('');
  const [roomHistory, setRoomHistory] = useState([]);

  // Admin Monitoring Data
  const [lessonPlanOverview, setLessonPlanOverview] = useState([]);
  const [loadingOverview, setLoadingOverview] = useState(false);

  const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
  const periods = [
    { num: 1, label: 'P1', time: '08:15 - 09:05' },
    { num: 2, label: 'P2', time: '09:05 - 09:55' },
    { isBreak: true, label: 'BREAK', time: '09:55 - 10:10' },
    { num: 3, label: 'P3', time: '10:10 - 11:00' },
    { num: 4, label: 'P4', time: '11:00 - 11:50' },
    { num: 5, label: 'P5', time: '11:50 - 12:40' },
    { isLunch: true, label: 'LUNCH', time: '12:40 - 13:30' },
    { num: 6, label: 'P6', time: '13:30 - 14:15' },
    { num: 7, label: 'P7', time: '14:15 - 15:00' },
    { num: 8, label: 'P8', time: '15:00 - 15:45' }
  ];

  useEffect(() => {
    fetchInitialData();
  }, []);

  const fetchInitialData = async () => {
    setLoading(true);
    try {
      const headers = user?.token ? { 'Authorization': `Bearer ${user.token}` } : {};
      const isFacultyUser = user?.role === 'ROLE_FACULTY';

      if (isFacultyUser) {
        // Faculty: fetch only own timetable assignments from token-based endpoint
        const [ttRes, mySubRes] = await Promise.all([
          fetch('/api/faculty/my-timetable', { headers }),
          fetch('/api/faculty/my-subjects', { headers })
        ]);
        const assignments = ttRes.ok ? await ttRes.json() : [];
        const mySubjects = mySubRes.ok ? await mySubRes.json() : [];

        // Build synthetic batch list from assignments (unique batches this faculty teaches)
        const batchMap = {};
        assignments.forEach(a => {
          if (a.batch && !batchMap[a.batch.id]) batchMap[a.batch.id] = a.batch;
        });
        setBatches(Object.values(batchMap));
        setAllAssignments(assignments);
        setSubjects(mySubjects.map(o => o.subject).filter(Boolean));
        setFaculty([]);
        setRooms([]);
      } else {
        // Admin: full data access
        const [bRes, aRes, sRes, fRes, rRes] = await Promise.all([
          fetch('/api/admin/batches', { headers }),
          fetch('/api/timetable', { headers }),
          fetch('/api/admin/subjects', { headers }),
          fetch('/api/admin/faculty', { headers }),
          fetch('/api/admin/rooms', { headers })
        ]);

        const [bData, aData, sData, fData, rData] = await Promise.all([
          bRes.ok ? bRes.json() : [],
          aRes.ok ? aRes.json() : [],
          sRes.ok ? sRes.json() : [],
          fRes.ok ? fRes.json() : [],
          rRes.ok ? rRes.json() : []
        ]);

        setBatches(bData);
        setAllAssignments(aData);
        setSubjects(sData);
        setFaculty(fData);
        setRooms(rData);
      }
    } catch (err) {
      console.error('Error loading master data:', err);
    } finally {
      setLoading(false);
    }
  };

  const refreshTimetables = async () => {
    setLoading(true);
    try {
      const headers = user?.token ? { 'Authorization': `Bearer ${user.token}` } : {};
      const res = await fetch('/api/timetable', { headers });
      if (res.ok) {
        setAllAssignments(await res.json());
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const fetchRoomHistory = async (batchId) => {
    try {
      const headers = user?.token ? { 'Authorization': `Bearer ${user.token}` } : {};
      const res = await fetch(`/api/timetable/room-history/${batchId || 1}`, { headers });
      if (res.ok) {
        setRoomHistory(await res.json());
      }
    } catch (err) {
      console.error('Error loading room history:', err);
    }
  };

  const fetchLessonPlanOverview = async () => {
    setLoadingOverview(true);
    try {
      const headers = user?.token ? { 'Authorization': `Bearer ${user.token}` } : {};
      const res = await fetch('/api/lesson-plan/admin/overview', { headers });
      if (res.ok) {
        setLessonPlanOverview(await res.json());
      }
    } catch (err) {
      console.error('Error loading overview:', err);
    } finally {
      setLoadingOverview(false);
    }
  };

  const handleDeleteAssignment = async (id) => {
    if (!window.confirm('Remove this assignment from the timetable?')) return;
    try {
      const res = await fetch(`/api/timetable/assignment/${id}`, {
        method: 'DELETE',
        headers: user?.token ? { 'Authorization': `Bearer ${user.token}` } : {}
      });
      if (res.ok) {
        setMessage({ type: 'success', text: 'Assignment removed.' });
        refreshTimetables();
      }
    } catch (err) {
      setMessage({ type: 'error', text: 'Delete failed.' });
    }
  };

  const handleChangeRoom = async () => {
    if (!selectedAssignment || !newRoomId) return;
    try {
      const res = await fetch('/api/timetable/change-room', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(user?.token ? { 'Authorization': `Bearer ${user.token}` } : {})
        },
        body: JSON.stringify({
          assignmentId: selectedAssignment.id,
          newRoomId: parseInt(newRoomId),
          reason: roomChangeReason || 'Temporary reallocation',
          changedBy: user?.username || 'Admin'
        })
      });
      const data = await res.json();
      if (res.ok) {
        setMessage({ type: 'success', text: data.message });
        setShowRoomModal(false);
        setSelectedAssignment(null);
        setNewRoomId('');
        setRoomChangeReason('');
        refreshTimetables();
      } else {
        setMessage({ type: 'error', text: data.error || 'Failed to change room' });
      }
    } catch (err) {
      setMessage({ type: 'error', text: 'Room change request failed.' });
    }
  };

  // Bluish-Green (Teal) Theme Tokens derived at render time (ignoring any colors stored in DB)
  const TIMETABLE_TOKENS = {
    THEORY: {
      type: 'THEORY',
      label: 'THEORY',
      fill: 'var(--tt-theory-fill, rgba(20, 184, 166, 0.15))',
      border: 'var(--tt-theory-border, #14b8a6)',
      text: 'var(--tt-theory-text, #99f6e4)',
      chipBg: 'var(--tt-theory-chip-bg, rgba(20, 184, 166, 0.25))',
      chipText: '#99f6e4',
      pdfFill: [230, 250, 248],
      pdfText: [15, 118, 110]
    },
    LAB: {
      type: 'LAB',
      label: 'LAB',
      fill: 'var(--tt-lab-fill, rgba(6, 182, 212, 0.18))',
      border: 'var(--tt-lab-border, #06b6d4)',
      text: 'var(--tt-lab-text, #a5f3fc)',
      chipBg: 'var(--tt-lab-chip-bg, rgba(6, 182, 212, 0.25))',
      chipText: '#a5f3fc',
      pdfFill: [225, 248, 252],
      pdfText: [14, 116, 144]
    },
    SPECIAL: {
      type: 'SPECIAL',
      label: 'SPECIAL',
      fill: 'var(--tt-special-fill, rgba(45, 212, 191, 0.12))',
      border: 'var(--tt-special-border, #2dd4bf)',
      text: 'var(--tt-special-text, #ccfbf1)',
      chipBg: 'var(--tt-special-chip-bg, rgba(45, 212, 191, 0.25))',
      chipText: '#ccfbf1',
      pdfFill: [235, 252, 250],
      pdfText: [17, 94, 89]
    },
    LIBRARY: {
      type: 'LIBRARY',
      label: 'LIBRARY',
      fill: 'var(--tt-library-fill, rgba(16, 185, 129, 0.15))',
      border: 'var(--tt-library-border, #10b981)',
      text: 'var(--tt-library-text, #a7f3d0)',
      chipBg: 'var(--tt-library-chip-bg, rgba(16, 185, 129, 0.25))',
      chipText: '#a7f3d0',
      pdfFill: [236, 253, 245],
      pdfText: [6, 95, 70]
    },
    FREE: {
      type: 'FREE',
      label: 'FREE',
      fill: 'var(--tt-free-fill, rgba(20, 184, 166, 0.03))',
      border: 'var(--tt-free-border, rgba(20, 184, 166, 0.35))',
      text: 'var(--tt-free-text, #5eead4)',
      pdfFill: [255, 255, 255],
      pdfText: [148, 163, 184]
    },
    BREAK: {
      type: 'BREAK',
      label: 'BREAK',
      fill: 'var(--tt-break-fill, rgba(51, 65, 85, 0.35))',
      border: 'var(--tt-break-border, rgba(100, 116, 139, 0.3))',
      text: 'var(--tt-break-text, #94a3b8)',
      pdfFill: [241, 245, 249],
      pdfText: [100, 116, 139]
    }
  };

  const getPeriodTheme = (assignment) => {
    if (!assignment) return TIMETABLE_TOKENS.FREE;
    const act = (assignment.activityType || '').toUpperCase().trim();
    const sess = (assignment.sessionType || '').toUpperCase().trim();
    const subType = (assignment.subject?.subjectType || '').toUpperCase().trim();
    const code = (assignment.subject?.subjectCode || '').toUpperCase().trim();
    const name = (assignment.subject?.subjectName || '').toUpperCase().trim();

    // 1. Library or common non-teaching period
    if (act === 'COMMON' || act === 'LIBRARY' || sess === 'COMMON' || sess === 'LIBRARY'
        || subType === 'COMMON' || subType === 'NON_TEACHING' || subType === 'LIBRARY'
        || code.includes('LIB') || name.includes('LIBRARY') || name.includes('MENTORING') || name.includes('SPORTS')) {
      return TIMETABLE_TOKENS.LIBRARY;
    }

    // 2. Lab or practical session
    if (assignment.isLabBlock || sess === 'LAB' || act === 'LAB' || subType === 'LAB' || subType === 'PRACTICAL') {
      return TIMETABLE_TOKENS.LAB;
    }

    // 3. Special institutional activities (AT, TT, LC, COUN, NPTEL, CSD)
    if (sess === 'SPECIAL' || ['CAREER_SKILL_DEVELOPMENT', 'LEETCODE', 'APTITUDE', 'TECHNICAL_TEST', 'COUNSELLING', 'NPTEL'].includes(act)
        || subType === 'SPECIAL' || code.includes('APT') || code.includes('TEC') || code.includes('LEE') || code.includes('COU') || code.includes('NPT') || code.includes('SDA')) {
      return TIMETABLE_TOKENS.SPECIAL;
    }

    // 4. Regular theory
    return TIMETABLE_TOKENS.THEORY;
  };

  const getSectionStats = (batchAssignments) => {
    let scheduledPeriods = 0;
    (batchAssignments || []).forEach(a => {
      scheduledPeriods += (a.durationPeriods || 1);
    });
    const totalAvailable = 40; // 5 days * 8 teaching slots
    const freePeriods = Math.max(0, totalAvailable - scheduledPeriods);
    return { scheduledPeriods, freePeriods, assignmentCount: (batchAssignments || []).length };
  };

  const getSubjectDisplayName = (assignment) => {
    if (!assignment) return 'Free';
    const code = (assignment.subject?.subjectCode || assignment.activityType || '').toUpperCase().trim();
    const subName = (assignment.subject?.subjectName || '').toUpperCase().trim();
    const isLab = assignment.sessionType === 'LAB' || assignment.isLabBlock || assignment.activityType === 'LAB' || (assignment.subject?.subjectType === 'LAB');

    // Canonical short codes matching Easwari Reference Timetable format
    if (code.includes('LIB') || subName.includes('LIBRARY')) return 'Library';
    if (code.includes('CSS301') || code === 'ESD' || subName.includes('EMBEDDED')) return isLab ? 'ESD LAB' : 'ESD';
    if (code.includes('CSC301') || code === 'CN' || subName.includes('NETWORKS')) return isLab ? 'CN LAB' : 'CN';
    if (code.includes('MAB301') || code === 'DM' || subName.includes('DISCRETE')) {
      return (assignment.sessionType === 'TUTORIAL' || assignment.activityType === 'TUTORIAL') ? 'DM(T)' : 'DM';
    }
    if (code.includes('CSC303') || code === 'FAIML' || subName.includes('FAIML')) return isLab ? 'FAIML LAB' : 'FAIML';
    if (code.includes('CSC302') || code === 'ADSA' || subName.includes('DATA STRUCTURES')) return isLab ? 'ADSA LAB' : 'ADSA';
    if (code.includes('CSC304') || code === 'OOPJ' || subName.includes('JAVA')) return isLab ? 'OOPJ LAB' : 'OOPJ';
    if (code.includes('APT301') || code === 'APTITUDE' || code === 'AT' || subName.includes('APTITUDE')) return 'AT';
    if (code.includes('TEC301') || code === 'TECHNICAL_TEST' || code === 'TT' || subName.includes('TECHNICAL')) return 'TT';
    if (code.includes('LEE301') || code === 'LEETCODE' || code === 'LC' || subName.includes('LEET')) return 'LC';
    if (code.includes('COU301') || code === 'COUNSELLING' || code === 'COUN' || subName.includes('COUNSEL')) return 'COUN';
    if (code.includes('SDA301') || code === 'CAREER_SKILL_DEVELOPMENT' || code === 'CSD' || subName.includes('CAREER')) return 'CSD';
    if (code.includes('NPT301') || code === 'NPTEL' || subName.includes('NPTEL')) return 'NPTEL';

    if (assignment.subject?.subjectName) {
      let name = assignment.subject.subjectName.trim();
      return isLab && !name.toLowerCase().includes('lab') ? `${name} Lab` : name;
    }
    return assignment.activityType || 'Free';
  };

  const getAssignmentAt = (batchAssignments, day, periodNum) => {
    return batchAssignments.find(a => {
      if (a.dayOfWeek !== day) return false;
      const start = a.periodNumber;
      const duration = a.durationPeriods || 1;
      return periodNum >= start && periodNum < start + duration;
    });
  };

  // Faculty-specific logic: identify the logged-in faculty's assignments
  const isFaculty = user?.role === 'ROLE_FACULTY';
  const myFacultyName = isFaculty ? (user.fullName || user.username) : null;
  const isMyAssignment = (assignment) => {
    if (!isFaculty || !assignment) return true; // Admins view all assignments
    const fName = assignment.faculty?.name || '';
    const fEmail = assignment.faculty?.email || '';
    const userEmail = user?.email || '';
    if (fEmail && userEmail && fEmail.toLowerCase() === userEmail.toLowerCase()) return true;
    return myFacultyName && fName.toLowerCase().trim() === myFacultyName.toLowerCase().trim();
  };

  // Filter batches by selected department & semester
  const filteredBatches = useMemo(() => {
    return batches.filter(b => {
      if (departmentFilter && departmentFilter !== 'ALL') {
        const bDept = (b.department || '').toLowerCase();
        const sDept = departmentFilter.toLowerCase();
        if (!bDept.includes(sDept) && !sDept.includes(bDept)) return false;
      }
      if (semesterFilter && semesterFilter !== 'ALL') {
        if (String(b.semester) !== String(semesterFilter)) return false;
      }
      return true;
    });
  }, [batches, departmentFilter, semesterFilter]);

  // Group filtered batches by Department and Semester
  const groupedBatches = useMemo(() => {
    const groups = {};
    filteredBatches.forEach(b => {
      const key = `${b.department || 'Department'} — Semester ${b.semester || 'N/A'}`;
      if (!groups[key]) groups[key] = [];
      groups[key].push(b);
    });
    // Sort sections within each group: Section A, Section B, Section C...
    Object.values(groups).forEach(list => {
      list.sort((a, b) => (a.section || a.batchName || '').localeCompare(b.section || b.batchName || ''));
    });
    return groups;
  }, [filteredBatches]);

  const downloadAllPdf = () => {
    if (filteredBatches.length === 0) return;
    const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' });

    filteredBatches.forEach((b, idx) => {
      if (idx > 0) doc.addPage();
      const batchAssignments = allAssignments.filter(a => a.batch && a.batch.id === b.id);
      const stats = getSectionStats(batchAssignments);

      // Header Banner — Teal bluish-green family
      doc.setFillColor(15, 118, 110); // #0f766e
      doc.rect(0, 0, 297, 24, 'F');
      doc.setTextColor(255, 255, 255);
      doc.setFontSize(14);
      doc.setFont('helvetica', 'bold');
      doc.text('EASWARI ENGINEERING COLLEGE — ACADEMIC TIMETABLE SYSTEM', 14, 11);
      doc.setFontSize(10);
      doc.setFont('helvetica', 'normal');
      doc.text(`Official Academic Timetable — Section ${b.section || b.batchName} (${b.batchName})`, 14, 18);

      // Section metadata banner
      doc.setTextColor(30, 41, 59);
      doc.setFontSize(9);
      doc.text(`Department: ${b.department || 'Engineering'}`, 14, 29);
      doc.text(`Semester: Sem ${b.semester || '3'}`, 85, 29);
      doc.text(`Classroom: ${b.assignedRoomCode || 'TBD'} (${b.assignedBlockName || 'Academic Block'} Flr ${b.assignedFloorNumber || 1})`, 135, 29);
      doc.text(`Status: ${stats.scheduledPeriods} Scheduled Periods • ${stats.freePeriods} Free Periods`, 215, 29);

      const head = [[
        'Day',
        'P1\n08:15-09:05',
        'P2\n09:05-09:55',
        'Break\n09:55-10:10',
        'P3\n10:10-11:00',
        'P4\n11:00-11:50',
        'P5\n11:50-12:40',
        'Lunch\n12:40-13:30',
        'P6\n13:30-14:15',
        'P7\n14:15-15:00',
        'P8\n15:00-15:45'
      ]];

      const getPdfCell = (day, pNum) => {
        const assignment = getAssignmentAt(batchAssignments, day, pNum);
        if (!assignment) return 'Free';
        const name = getSubjectDisplayName(assignment);
        const code = assignment.subject?.subjectCode ? `(${assignment.subject.subjectCode})` : '';
        const room = assignment.room?.roomCode ? `[${assignment.room.roomCode}]` : '';
        const fac = assignment.faculty?.name || '';
        const meta = [room, fac].filter(Boolean).join(' • ');
        return [name, code, meta].filter(Boolean).join('\n');
      };

      const body = days.map(day => [
        day,
        getPdfCell(day, 1),
        getPdfCell(day, 2),
        'MORNING\nBREAK',
        getPdfCell(day, 3),
        getPdfCell(day, 4),
        getPdfCell(day, 5),
        'LUNCH\nBREAK',
        getPdfCell(day, 6),
        getPdfCell(day, 7),
        getPdfCell(day, 8)
      ]);

      const colToPeriod = { 1: 1, 2: 2, 4: 3, 5: 4, 6: 5, 8: 6, 9: 7, 10: 8 };

      autoTable(doc, {
        startY: 33,
        head: head,
        body: body,
        theme: 'grid',
        styles: {
          fontSize: 6.5,
          halign: 'center',
          valign: 'middle',
          cellPadding: 1.5,
          minCellHeight: 18,
          lineColor: [20, 184, 166],
          lineWidth: 0.15
        },
        headStyles: {
          fillColor: [15, 118, 110], // Teal header
          textColor: [255, 255, 255],
          fontStyle: 'bold',
          fontSize: 7.5,
          lineColor: [13, 148, 136],
          lineWidth: 0.2
        },
        columnStyles: {
          0: { fontStyle: 'bold', halign: 'left', cellWidth: 20, fillColor: [241, 245, 249], textColor: [15, 23, 42] },
          3: { fillColor: [241, 245, 249], textColor: [100, 116, 139], fontStyle: 'italic', cellWidth: 15 },
          7: { fillColor: [241, 245, 249], textColor: [100, 116, 139], fontStyle: 'italic', cellWidth: 15 }
        },
        didParseCell: function(data) {
          if (data.section === 'body') {
            const col = data.column.index;
            if (col in colToPeriod) {
              const pNum = colToPeriod[col];
              const day = days[data.row.index];
              const assignment = getAssignmentAt(batchAssignments, day, pNum);
              const theme = getPeriodTheme(assignment);
              data.cell.styles.fillColor = theme.pdfFill;
              data.cell.styles.textColor = theme.pdfText;
              if (!assignment) {
                data.cell.styles.fontStyle = 'italic';
              } else {
                data.cell.styles.fontStyle = 'bold';
              }
            }
          }
        }
      });
    });

    const pageCount = doc.internal.getNumberOfPages();
    for (let i = 1; i <= pageCount; i++) {
      doc.setPage(i);
      doc.setFontSize(8);
      doc.setTextColor(100, 116, 139);
      doc.text(`Generated by Easwari Engineering College — Academic Management & Smart Timetable Scheduler`, 14, 202);
      doc.text(`Section ${i} of ${pageCount}`, 265, 202);
    }

    doc.save(`Timetables_All_Sections.pdf`);
    setMessage({ type: 'success', text: `Downloaded PDF for all ${filteredBatches.length} sections.` });
  };

  return (
    <div>
      {/* Top Header & Scope Controls */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 800, background: 'linear-gradient(to right, #ffffff, #c084fc)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            {isFaculty ? 'My Timetable' : 'Multi-Section Timetables'}
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: '0.25rem' }}>
            {isFaculty ? 'Your assigned teaching slots across all sections — read-only.' : 'All sections stacked by department and semester with continuous lab blocks & synchronized special periods'}
          </p>
        </div>

        {/* Global Scope Selector (Replaces single-section selector) */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', background: 'var(--bg-secondary)', padding: '0.35rem 0.75rem', borderRadius: '8px', border: '1px solid var(--border-accent)' }}>
            <Filter size={15} style={{ color: 'var(--accent-primary)' }} />
            <select
              value={departmentFilter}
              onChange={(e) => {
                setDepartmentFilter(e.target.value);
                setStoredAcademicContext({ department: e.target.value });
              }}
              style={{ background: 'transparent', color: 'var(--text-main)', border: 'none', fontSize: '0.86rem', fontWeight: 600, outline: 'none', cursor: 'pointer' }}
            >
              <option value="ALL">All Departments</option>
              {INSTITUTIONAL_PROGRAMMES.map(p => <option key={p} value={p}>{p}</option>)}
            </select>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', background: 'var(--bg-secondary)', padding: '0.35rem 0.75rem', borderRadius: '8px', border: '1px solid var(--border-accent)' }}>
            <select
              value={semesterFilter}
              onChange={(e) => {
                setSemesterFilter(e.target.value);
                setStoredAcademicContext({ semester: e.target.value });
              }}
              style={{ background: 'transparent', color: 'var(--text-main)', border: 'none', fontSize: '0.86rem', fontWeight: 600, outline: 'none', cursor: 'pointer' }}
            >
              <option value="ALL">All Semesters</option>
              {SEMESTER_OPTIONS.map(opt => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
            </select>
          </div>

          <button
            onClick={downloadAllPdf}
            className="btn btn-secondary"
            style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '0.4rem', border: '1px solid rgba(99,102,241,0.4)', background: 'rgba(99,102,241,0.15)', color: '#a5b4fc' }}
            title="Download multi-section timetable PDF"
          >
            <Download size={15} /> Export PDF
          </button>

          <button
            onClick={refreshTimetables}
            className="btn btn-secondary"
            style={{ padding: '0.45rem 0.75rem', fontSize: '0.85rem' }}
            title="Refresh from server"
          >
            <RefreshCw size={15} />
          </button>
        </div>
      </div>

      {message && (
        <div style={{
          padding: '0.75rem 1rem',
          borderRadius: '8px',
          marginBottom: '1.5rem',
          background: message.type === 'success' ? 'rgba(52, 211, 153, 0.15)' : 'rgba(248, 113, 113, 0.15)',
          border: `1px solid ${message.type === 'success' ? '#34d399' : '#f87171'}`,
          color: message.type === 'success' ? '#6ee7b7' : '#fca5a5',
          fontSize: '0.88rem'
        }}>
          {message.text}
        </div>
      )}

      {/* Stacked Timetable Grids */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: '4rem', color: 'var(--text-muted)' }}>
          <Clock size={36} style={{ animation: 'spin 1.5s linear infinite', margin: '0 auto 1rem', display: 'block', color: 'var(--accent-primary)' }} />
          Loading timetables for all sections...
        </div>
      ) : Object.keys(groupedBatches).length === 0 ? (
        <div className="glass-panel" style={{ padding: '3rem', textAlign: 'center' }}>
          <Clock size={40} style={{ color: 'var(--text-dim)', marginBottom: '0.75rem' }} />
          <div style={{ fontWeight: 700, fontSize: '1.1rem' }}>No Sections Found for Selected Scope</div>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginTop: '0.25rem' }}>
            Adjust the department or semester filter, or generate a timetable in Generate Timetable.
          </p>
        </div>
      ) : (
        Object.entries(groupedBatches).map(([groupTitle, sectionBatches]) => (
          <div key={groupTitle} style={{ marginBottom: '2.5rem' }}>
            {/* Department & Semester Group Heading */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.75rem',
              padding: '0.75rem 1.25rem',
              background: 'linear-gradient(90deg, rgba(99, 102, 241, 0.18), rgba(168, 85, 247, 0.08))',
              borderLeft: '4px solid var(--accent-primary)',
              borderRadius: '8px',
              marginBottom: '1.5rem'
            }}>
              <Calendar size={20} style={{ color: 'var(--accent-primary)' }} />
              <h2 style={{ fontSize: '1.25rem', fontWeight: 800, margin: 0, color: '#ffffff' }}>
                {groupTitle}
              </h2>
              <span className="badge badge-success" style={{ marginLeft: 'auto', fontSize: '0.78rem' }}>
                {sectionBatches.length} Section{sectionBatches.length > 1 ? 's' : ''} Stacked
              </span>
            </div>

            {/* Stacked Grids: One per Section */}
            {sectionBatches.map(batch => {
              const batchAssignments = allAssignments.filter(a => a.batch && a.batch.id === batch.id);
              const stats = getSectionStats(batchAssignments);

              return (
                <div key={batch.id} className="glass-panel" style={{ padding: '1.5rem', marginBottom: '2rem', overflowX: 'auto' }}>
                  {/* Section Title Bar */}
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                      <span style={{
                        padding: '0.3rem 0.75rem',
                        background: 'rgba(20, 184, 166, 0.15)',
                        border: '1px solid rgba(20, 184, 166, 0.4)',
                        borderRadius: '6px',
                        fontWeight: 800,
                        fontSize: '0.95rem',
                        color: '#99f6e4'
                      }}>
                        Section {batch.section || batch.batchName}
                      </span>
                      <span style={{ fontWeight: 700, fontSize: '1.05rem', color: 'var(--text-main)' }}>
                        {batch.batchName}
                      </span>
                      <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                        ({batch.strength || 60} Students)
                      </span>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', fontSize: '0.82rem', color: 'var(--text-muted)', flexWrap: 'wrap' }}>
                      <span>
                        <strong style={{ color: '#10b981' }}>Dedicated Classroom:</strong> {batch.assignedRoomCode || 'TBD'}
                        {batch.assignedBlockName && ` (${batch.assignedBlockName} Flr ${batch.assignedFloorNumber || 1})`}
                      </span>
                      <span className="badge" style={{
                        background: 'rgba(20, 184, 166, 0.15)',
                        border: '1px solid rgba(20, 184, 166, 0.4)',
                        color: '#99f6e4',
                        fontWeight: 700
                      }}>
                        {batchAssignments.length > 0 ? `${batchAssignments.length} Assignments (${stats.scheduledPeriods} Periods)` : 'No Assignments'}
                      </span>
                      {stats.freePeriods > 0 && (
                        <span className="badge" style={{
                          background: 'rgba(20, 184, 166, 0.06)',
                          border: '1px dashed rgba(20, 184, 166, 0.4)',
                          color: '#5eead4',
                          fontWeight: 600
                        }}>
                          {stats.freePeriods} free periods this week
                        </span>
                      )}
                    </div>
                  </div>

                  {batchAssignments.length === 0 ? (
                    <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                      No timetable generated for this section yet.
                    </div>
                  ) : (
                    <table style={{ width: '100%', borderCollapse: 'separate', borderSpacing: '6px', minWidth: '950px' }}>
                      <thead>
                        <tr>
                          <th style={{ padding: '0.75rem', background: 'var(--bg-secondary)', borderRadius: '6px', color: 'var(--text-muted)', fontSize: '0.8rem', width: '110px' }}>
                            DAY / PERIOD
                          </th>
                          {periods.map((p, i) => (
                            <th key={i} style={{
                              padding: '0.6rem',
                              background: p.isBreak || p.isLunch ? 'rgba(51, 65, 85, 0.25)' : 'var(--bg-secondary)',
                              borderRadius: '6px',
                              textAlign: 'center',
                              border: p.isBreak || p.isLunch ? '1px dashed rgba(100, 116, 139, 0.25)' : 'none'
                            }}>
                              <div style={{ fontSize: '0.85rem', fontWeight: 800, color: p.isBreak || p.isLunch ? '#94a3b8' : 'var(--text-main)' }}>
                                {p.label}
                              </div>
                              <div style={{ fontSize: '0.65rem', color: 'var(--text-dim)', marginTop: '2px' }}>{p.time}</div>
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {days.map(day => (
                          <tr key={day}>
                            <td style={{
                              padding: '0.8rem',
                              background: 'var(--bg-secondary)',
                              borderRadius: '6px',
                              fontWeight: 800,
                              fontSize: '0.85rem',
                              color: 'var(--text-muted)'
                            }}>
                              {day}
                            </td>

                            {periods.map((period, pIdx) => {
                              if (period.isBreak || period.isLunch) {
                                return (
                                  <td key={pIdx} style={{
                                    textAlign: 'center',
                                    background: 'rgba(51, 65, 85, 0.25)',
                                    border: '1px dashed rgba(100, 116, 139, 0.25)',
                                    borderRadius: '6px',
                                    color: '#94a3b8',
                                    fontSize: '0.7rem',
                                    letterSpacing: '0.08em',
                                    fontWeight: 700,
                                    verticalAlign: 'middle'
                                  }}>
                                    {period.label}
                                  </td>
                                );
                              }

                              const assignment = getAssignmentAt(batchAssignments, day, period.num);

                              if (!assignment) {
                                return (
                                  <td key={pIdx} style={{
                                    background: 'var(--tt-free-fill, rgba(20, 184, 166, 0.03))',
                                    border: '1px dashed var(--tt-free-border, rgba(20, 184, 166, 0.35))',
                                    borderRadius: '8px',
                                    textAlign: 'center',
                                    color: 'var(--tt-free-text, #5eead4)',
                                    fontSize: '0.78rem',
                                    fontWeight: 600,
                                    letterSpacing: '0.04em',
                                    padding: '0.65rem 0.5rem',
                                    verticalAlign: 'middle'
                                  }}>
                                    Free
                                  </td>
                                );
                              }

                              // Spanning check: if this slot is covered by a multi-period block starting earlier, skip
                              if (assignment.periodNumber !== period.num) {
                                return null;
                              }

                              const colSpan = assignment.durationPeriods > 1 ? assignment.durationPeriods : 1;
                              const token = getPeriodTheme(assignment);
                              const displayName = getSubjectDisplayName(assignment);
                              const subCode = assignment.subject?.subjectCode || '';
                              const facultyName = assignment.faculty?.name || 'Unassigned';
                              const roomCode = assignment.room?.roomCode || 'TBD';

                              return (
                                <td
                                  key={pIdx}
                                  colSpan={colSpan}
                                  style={{
                                    padding: '0.65rem 0.5rem',
                                    borderRadius: '8px',
                                    background: token.fill,
                                    border: `1px solid ${token.border}`,
                                    verticalAlign: 'middle',
                                    position: 'relative',
                                    textAlign: 'center',
                                    minHeight: '60px',
                                    cursor: 'default',
                                    boxShadow: '0 2px 4px rgba(0, 0, 0, 0.15)'
                                  }}
                                >
                                  {user?.role === 'ROLE_ADMIN' && (
                                    <div style={{ position: 'absolute', top: '4px', right: '4px', display: 'flex', gap: '3px', opacity: 0.7 }}>
                                      <button
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          setSelectedAssignment(assignment);
                                          setNewRoomId(assignment.room?.id?.toString() || '');
                                          setShowRoomModal(true);
                                        }}
                                        title="Change classroom"
                                        style={{ background: 'transparent', border: 'none', color: token.border, cursor: 'pointer', padding: 0 }}
                                      >
                                        <ArrowRightLeft size={11} />
                                      </button>
                                      <button
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          handleDeleteAssignment(assignment.id);
                                        }}
                                        title="Delete slot"
                                        style={{ background: 'transparent', border: 'none', color: '#f87171', cursor: 'pointer', padding: 0 }}
                                      >
                                        <Trash2 size={11} />
                                      </button>
                                    </div>
                                  )}

                                  {/* Subject Title (Bold) & Code */}
                                  <div style={{
                                    fontWeight: 800,
                                    fontSize: '0.86rem',
                                    color: token.text,
                                    lineHeight: 1.3
                                  }}>
                                    {displayName}
                                    {subCode && subCode !== displayName && (
                                      <span style={{ fontSize: '0.68rem', color: token.text, opacity: 0.75, marginLeft: '4px', fontWeight: 500 }}>
                                        ({subCode})
                                      </span>
                                    )}
                                  </div>

                                  {/* Faculty Name (Smaller, readable) */}
                                  <div style={{
                                    fontSize: '0.7rem',
                                    color: '#cbd5e1',
                                    marginTop: '2px',
                                    whiteSpace: 'nowrap',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis'
                                  }}>
                                    {facultyName}
                                  </div>

                                  {/* Room Code & Category Chip */}
                                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '5px', marginTop: '4px', flexWrap: 'wrap' }}>
                                    <span style={{ fontSize: '0.68rem', color: '#94a3b8', fontWeight: 600 }}>
                                      {roomCode}
                                    </span>
                                    <span style={{
                                      fontSize: '0.6rem',
                                      fontWeight: 800,
                                      letterSpacing: '0.04em',
                                      padding: '1px 5px',
                                      borderRadius: '4px',
                                      background: token.chipBg,
                                      border: `1px solid ${token.border}`,
                                      color: token.chipText
                                    }}>
                                      {token.label}
                                    </span>
                                  </div>
                                </td>
                              );
                            })}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              );
            })}
          </div>
        ))
      )}

      {/* Classroom Relocation Modal */}
      {showRoomModal && selectedAssignment && (
        <Modal title="Relocate Classroom" onClose={() => setShowRoomModal(false)}>
          <div style={{ padding: '0.5rem 0' }}>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '1rem' }}>
              Relocate class for <strong>{selectedAssignment.subject?.subjectCode} - {selectedAssignment.subject?.subjectName}</strong> ({selectedAssignment.dayOfWeek} P{selectedAssignment.periodNumber})
            </p>
            <div className="form-group" style={{ marginBottom: '1rem' }}>
              <label className="form-label">Current Classroom</label>
              <input
                type="text"
                className="form-control"
                value={`${selectedAssignment.room?.roomCode} (${selectedAssignment.room?.blockName || 'Block'} - Flr ${selectedAssignment.room?.floorNumber || 1})`}
                disabled
              />
            </div>
            <div className="form-group" style={{ marginBottom: '1rem' }}>
              <label className="form-label">New Classroom</label>
              <select
                className="form-control"
                value={newRoomId}
                onChange={e => setNewRoomId(e.target.value)}
              >
                <option value="">-- Select Room --</option>
                {rooms.map(r => (
                  <option key={r.id} value={r.id}>
                    {r.roomCode} ({r.roomType}) - Cap: {r.capacity} ({r.blockName || 'Block'} Flr {r.floorNumber || 1})
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group" style={{ marginBottom: '1.5rem' }}>
              <label className="form-label">Reason for Relocation</label>
              <input
                type="text"
                className="form-control"
                placeholder="e.g. Projector maintenance, Air conditioning issue"
                value={roomChangeReason}
                onChange={e => setRoomChangeReason(e.target.value)}
              />
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
              <button className="btn btn-secondary" onClick={() => setShowRoomModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleChangeRoom}>Save Relocation</button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
