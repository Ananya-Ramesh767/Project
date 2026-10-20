import React, { useState, useEffect } from 'react';
import { 
  Clock, Calendar, Layers, ShieldCheck, AlertCircle, CheckCircle2, 
  Plus, Trash2, RefreshCw, Eye, BookOpen, Users, DoorOpen, Sparkles, 
  BarChart2, ArrowRightLeft, History, RotateCcw, Download
} from 'lucide-react';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import Modal from '../components/Modal';

export default function SectionTimetablePage({ user }) {
  const [batches, setBatches] = useState([]);
  const [selectedBatchId, setSelectedBatchId] = useState(null);
  const [timetable, setTimetable] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [faculty, setFaculty] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState(null);
  const [activeTab, setActiveTab] = useState('grid'); // 'grid' | 'monitoring' | 'room-history'

  // Conflict Checker Modal State
  const [showModal, setShowModal] = useState(false);
  const [validationResult, setValidationResult] = useState(null);
  const [validating, setValidating] = useState(false);
  const [formData, setFormData] = useState({
    subjectId: '',
    facultyId: '',
    roomId: '',
    dayOfWeek: 'MONDAY',
    periodNumber: 1,
    durationPeriods: 1
  });

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

  useEffect(() => {
    if (selectedBatchId) {
      fetchTimetable(selectedBatchId);
      fetchRoomHistory(selectedBatchId);
    }
  }, [selectedBatchId]);

  const fetchInitialData = async () => {
    try {
      const headers = { 'Authorization': `Bearer ${user?.token}` };
      const [bRes, sRes, fRes, rRes] = await Promise.all([
        fetch('/api/admin/batches', { headers }),
        fetch('/api/admin/subjects', { headers }),
        fetch('/api/admin/faculty', { headers }),
        fetch('/api/admin/rooms', { headers })
      ]);

      const [bData, sData, fData, rData] = await Promise.all([
        bRes.ok ? bRes.json() : [],
        sRes.ok ? sRes.json() : [],
        fRes.ok ? fRes.json() : [],
        rRes.ok ? rRes.json() : []
      ]);

      setBatches(bData);
      setSubjects(sData);
      setFaculty(fData);
      setRooms(rData);

      if (bData.length > 0) {
        setSelectedBatchId(bData[0].id);
      }
    } catch (err) {
      console.error('Error loading master data:', err);
    }
  };

  const fetchTimetable = async (batchId) => {
    setLoading(true);
    try {
      const res = await fetch(`/api/timetable/batch/${batchId}`, {
        headers: { 'Authorization': `Bearer ${user?.token}` }
      });
      if (res.ok) {
        const data = await res.json();
        setTimetable(data);
      }
    } catch (err) {
      console.error('Error loading timetable:', err);
    } finally {
      setLoading(false);
    }
  };

  const fetchRoomHistory = async (batchId) => {
    try {
      const res = await fetch(`/api/timetable/room-history/${batchId}`, {
        headers: { 'Authorization': `Bearer ${user?.token}` }
      });
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
      const res = await fetch('/api/lesson-plan/admin/overview', {
        headers: { 'Authorization': `Bearer ${user?.token}` }
      });
      if (res.ok) {
        setLessonPlanOverview(await res.json());
      }
    } catch (err) {
      console.error('Error loading overview:', err);
    } finally {
      setLoadingOverview(false);
    }
  };

  const handleValidate = async () => {
    setValidating(true);
    setValidationResult(null);
    try {
      const payload = {
        batch: { id: selectedBatchId },
        subject: { id: parseInt(formData.subjectId) },
        faculty: { id: parseInt(formData.facultyId) },
        room: { id: parseInt(formData.roomId) },
        dayOfWeek: formData.dayOfWeek,
        periodNumber: parseInt(formData.periodNumber),
        durationPeriods: parseInt(formData.durationPeriods)
      };

      const res = await fetch('/api/timetable/validate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${user?.token}`
        },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      setValidationResult(data);
    } catch (err) {
      setValidationResult({ valid: false, message: 'Validation request failed: ' + err.message });
    } finally {
      setValidating(false);
    }
  };

  const handleSaveAssignment = async () => {
    try {
      const payload = {
        batch: { id: selectedBatchId },
        subject: { id: parseInt(formData.subjectId) },
        faculty: { id: parseInt(formData.facultyId) },
        room: { id: parseInt(formData.roomId) },
        dayOfWeek: formData.dayOfWeek,
        periodNumber: parseInt(formData.periodNumber),
        durationPeriods: parseInt(formData.durationPeriods)
      };

      const res = await fetch('/api/timetable/assignment', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${user?.token}`
        },
        body: JSON.stringify(payload)
      });

      if (res.ok) {
        setMessage({ type: 'success', text: 'Class successfully added to schedule.' });
        setShowModal(false);
        fetchTimetable(selectedBatchId);
      } else {
        const err = await res.json();
        setValidationResult(err);
      }
    } catch (err) {
      setMessage({ type: 'error', text: 'Save failed: ' + err.message });
    }
  };

  const handleDeleteAssignment = async (id) => {
    if (!window.confirm('Remove this assignment from the timetable?')) return;
    try {
      const res = await fetch(`/api/timetable/assignment/${id}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${user?.token}` }
      });
      if (res.ok) {
        setMessage({ type: 'success', text: 'Assignment removed.' });
        fetchTimetable(selectedBatchId);
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
          'Authorization': `Bearer ${user?.token}`
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
        fetchTimetable(selectedBatchId);
        fetchRoomHistory(selectedBatchId);
      } else {
        setMessage({ type: 'error', text: data.error || 'Failed to change room' });
      }
    } catch (err) {
      setMessage({ type: 'error', text: 'Room change request failed.' });
    }
  };

  const handleRestoreRoom = async (historyId) => {
    try {
      const res = await fetch('/api/timetable/restore-room', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${user?.token}`
        },
        body: JSON.stringify({ historyId })
      });
      const data = await res.json();
      if (res.ok) {
        setMessage({ type: 'success', text: data.message });
        fetchTimetable(selectedBatchId);
        fetchRoomHistory(selectedBatchId);
      }
    } catch (err) {
      setMessage({ type: 'error', text: 'Restore failed.' });
    }
  };

  // Find slot for day and period
  const getAssignmentAt = (day, periodNum) => {
    return timetable.find(a => {
      if (a.dayOfWeek !== day) return false;
      const start = a.periodNumber;
      const duration = a.durationPeriods || 1;
      return periodNum >= start && periodNum < start + duration;
    });
  };

  // Activity badge styling
  const getActivityMeta = (assignment) => {
    const act = assignment.activityType || (assignment.subject?.requiresLab ? 'LAB' : 'ACADEMIC');
    switch (act.toUpperCase()) {
      case 'LAB':
        return { bg: 'rgba(16, 185, 129, 0.2)', border: '#10b981', color: '#6ee7b7', label: 'LAB' };
      case 'CAREER_SKILL_DEVELOPMENT':
        return { bg: 'rgba(168, 85, 247, 0.25)', border: '#c084fc', color: '#e9d5ff', label: 'CAREER SKILLS (2H)' };
      case 'LEETCODE':
        return { bg: 'rgba(59, 130, 246, 0.2)', border: '#3b82f6', color: '#93c5fd', label: 'LEETCODE' };
      case 'APTITUDE':
        return { bg: 'rgba(245, 158, 11, 0.2)', border: '#f59e0b', color: '#fcd34d', label: 'APTITUDE' };
      case 'TECHNICAL_TEST':
        return { bg: 'rgba(239, 68, 68, 0.2)', border: '#ef4444', color: '#fca5a5', label: 'TECH TEST' };
      case 'COUNSELLING':
        return { bg: 'rgba(236, 72, 153, 0.2)', border: '#ec4899', color: '#fbcfe8', label: 'COUNSELLING' };
      case 'NPTEL':
        return { bg: 'rgba(6, 182, 212, 0.2)', border: '#06b6d4', color: '#a5f3fc', label: 'NPTEL' };
      case 'FREE':
        return { bg: 'rgba(107, 114, 128, 0.2)', border: '#6b7280', color: '#d1d5db', label: 'FREE' };
      default:
        return { bg: 'rgba(139, 92, 246, 0.2)', border: '#8b5cf6', color: '#ddd6fe', label: 'ACADEMIC' };
    }
  };

  // Hours calculation
  const totalWeeklyPeriods = 40;
  const academicPeriods = timetable.filter(a => !a.activityType || a.activityType === 'ACADEMIC' || a.activityType === 'LAB')
    .reduce((sum, a) => sum + (a.durationPeriods || 1), 0);
  const specialPeriods = timetable.filter(a => a.activityType && a.activityType !== 'ACADEMIC' && a.activityType !== 'LAB' && a.activityType !== 'FREE')
    .reduce((sum, a) => sum + (a.durationPeriods || 1), 0);
  const freePeriods = Math.max(0, totalWeeklyPeriods - academicPeriods - specialPeriods);

  // Faculty-specific logic: identify the logged-in faculty's assignments
  const isFaculty = user?.role === 'ROLE_FACULTY';
  const myFacultyName = isFaculty ? (user.fullName || user.username) : null;
  const isMyAssignment = (assignment) => {
    if (!isFaculty || !assignment) return false;
    const fName = assignment.faculty?.name || '';
    return myFacultyName && fName.toLowerCase().trim() === myFacultyName.toLowerCase().trim();
  };
  const myPeriods = isFaculty ? timetable.filter(a => isMyAssignment(a)) : [];

  const formatCellForPdf = (assignment) => {
    if (!assignment) return '—';
    const subCode = assignment.subject?.subjectCode || '';
    const subName = assignment.subject?.subjectName || assignment.activityType || '';
    const facName = assignment.faculty?.name || '';
    const roomCode = assignment.room?.roomCode || '';
    const lines = [subCode];
    if (subName) lines.push(subName.length > 22 ? subName.substring(0, 20) + '…' : subName);
    if (facName) lines.push(`Staff: ${facName.length > 18 ? facName.substring(0, 16) + '…' : facName}`);
    if (roomCode) lines.push(`[${roomCode}]`);
    return lines.join('\n');
  };

  const downloadSectionPdf = () => {
    const currentBatch = batches.find(b => b.id === selectedBatchId);
    if (!currentBatch) return;

    const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' });

    // Header Banner
    doc.setFillColor(30, 41, 59);
    doc.rect(0, 0, 297, 24, 'F');
    doc.setTextColor(255, 255, 255);
    doc.setFontSize(15);
    doc.setFont('helvetica', 'bold');
    doc.text('ODYSSEUS ACADEMIC TIMETABLE SYSTEM', 14, 11);
    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    doc.text(`Official Academic Timetable — Section ${currentBatch.section} (${currentBatch.batchName})`, 14, 18);

    // Meta Row
    doc.setTextColor(30, 41, 59);
    doc.setFontSize(9);
    doc.text(`Department: ${currentBatch.department || 'Engineering'}`, 14, 30);
    doc.text(`Semester: Sem ${currentBatch.semester || '3'}`, 95, 30);
    doc.text(`Academic Year: ${currentBatch.academicYear || '2026-2027'}`, 160, 30);
    doc.text(`Classroom: ${currentBatch.assignedRoomCode || 'TBD'} (${currentBatch.assignedBlockName || 'Academic Block'} Flr ${currentBatch.assignedFloorNumber || 1})`, 220, 30);

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

    const body = days.map(day => [
      day,
      formatCellForPdf(getAssignmentAt(day, 1)),
      formatCellForPdf(getAssignmentAt(day, 2)),
      'MORNING\nBREAK',
      formatCellForPdf(getAssignmentAt(day, 3)),
      formatCellForPdf(getAssignmentAt(day, 4)),
      formatCellForPdf(getAssignmentAt(day, 5)),
      'LUNCH\nBREAK',
      formatCellForPdf(getAssignmentAt(day, 6)),
      formatCellForPdf(getAssignmentAt(day, 7)),
      formatCellForPdf(getAssignmentAt(day, 8))
    ]);

    autoTable(doc, {
      startY: 34,
      head: head,
      body: body,
      theme: 'grid',
      styles: { fontSize: 7, halign: 'center', valign: 'middle', cellPadding: 2, minCellHeight: 18 },
      headStyles: { fillColor: [79, 70, 229], textColor: [255, 255, 255], fontStyle: 'bold', fontSize: 7.5 },
      alternateRowStyles: { fillColor: [248, 250, 252] },
      columnStyles: {
        0: { fontStyle: 'bold', halign: 'left', cellWidth: 22 },
        3: { fillColor: [241, 245, 249], fontStyle: 'italic', cellWidth: 15 },
        7: { fillColor: [241, 245, 249], fontStyle: 'italic', cellWidth: 15 }
      }
    });

    const pageCount = doc.internal.getNumberOfPages();
    for (let i = 1; i <= pageCount; i++) {
      doc.setPage(i);
      doc.setFontSize(8);
      doc.setTextColor(148, 163, 184);
      doc.text(`Generated by Odysseus Academic Timetable System — Approved Institutional Schedule`, 14, 202);
      doc.text(`Page ${i} of ${pageCount}`, 265, 202);
    }

    const cleanDept = (currentBatch.department || 'CSE').replace(/[^a-zA-Z0-9]/g, '_');
    doc.save(`Timetable_${cleanDept}_Sem${currentBatch.semester}_Sec${currentBatch.section}.pdf`);
  };

  const downloadDepartmentPdf = async () => {
    const currentBatch = batches.find(b => b.id === selectedBatchId);
    if (!currentBatch) return;

    const deptBatches = batches.filter(b => b.department === currentBatch.department && b.semester === currentBatch.semester);
    if (deptBatches.length === 0) return;

    setMessage({ type: 'success', text: `Generating complete timetable PDF for ${deptBatches.length} sections in ${currentBatch.department} Sem ${currentBatch.semester}…` });

    const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' });

    for (let bIdx = 0; bIdx < deptBatches.length; bIdx++) {
      const b = deptBatches[bIdx];
      if (bIdx > 0) doc.addPage();

      let bTimetable = [];
      try {
        const res = await fetch(`/api/timetable/batch/${b.id}`, {
          headers: { 'Authorization': `Bearer ${user?.token}` }
        });
        if (res.ok) bTimetable = await res.json();
      } catch (err) {
        console.warn('Could not fetch timetable for batch', b.id, err);
      }

      const getBatchSlot = (day, periodNum) => {
        return bTimetable.find(a => {
          if (a.dayOfWeek !== day) return false;
          const start = a.periodNumber;
          const duration = a.durationPeriods || 1;
          return periodNum >= start && periodNum < start + duration;
        });
      };

      // Header Banner
      doc.setFillColor(30, 41, 59);
      doc.rect(0, 0, 297, 24, 'F');
      doc.setTextColor(255, 255, 255);
      doc.setFontSize(15);
      doc.setFont('helvetica', 'bold');
      doc.text('ODYSSEUS ACADEMIC TIMETABLE SYSTEM', 14, 11);
      doc.setFontSize(10);
      doc.setFont('helvetica', 'normal');
      doc.text(`Official Academic Timetable — Section ${b.section} (${b.batchName})`, 14, 18);

      // Meta Row
      doc.setTextColor(30, 41, 59);
      doc.setFontSize(9);
      doc.text(`Department: ${b.department || 'Engineering'}`, 14, 30);
      doc.text(`Semester: Sem ${b.semester || '3'}`, 95, 30);
      doc.text(`Academic Year: ${b.academicYear || '2026-2027'}`, 160, 30);
      doc.text(`Classroom: ${b.assignedRoomCode || 'TBD'} (${b.assignedBlockName || 'Academic Block'} Flr ${b.assignedFloorNumber || 1})`, 220, 30);

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

      const body = days.map(day => [
        day,
        formatCellForPdf(getBatchSlot(day, 1)),
        formatCellForPdf(getBatchSlot(day, 2)),
        'MORNING\nBREAK',
        formatCellForPdf(getBatchSlot(day, 3)),
        formatCellForPdf(getBatchSlot(day, 4)),
        formatCellForPdf(getBatchSlot(day, 5)),
        'LUNCH\nBREAK',
        formatCellForPdf(getBatchSlot(day, 6)),
        formatCellForPdf(getBatchSlot(day, 7)),
        formatCellForPdf(getBatchSlot(day, 8))
      ]);

      autoTable(doc, {
        startY: 34,
        head: head,
        body: body,
        theme: 'grid',
        styles: { fontSize: 7, halign: 'center', valign: 'middle', cellPadding: 2, minCellHeight: 18 },
        headStyles: { fillColor: [79, 70, 229], textColor: [255, 255, 255], fontStyle: 'bold', fontSize: 7.5 },
        alternateRowStyles: { fillColor: [248, 250, 252] },
        columnStyles: {
          0: { fontStyle: 'bold', halign: 'left', cellWidth: 22 },
          3: { fillColor: [241, 245, 249], fontStyle: 'italic', cellWidth: 15 },
          7: { fillColor: [241, 245, 249], fontStyle: 'italic', cellWidth: 15 }
        }
      });
    }

    const pageCount = doc.internal.getNumberOfPages();
    for (let i = 1; i <= pageCount; i++) {
      doc.setPage(i);
      doc.setFontSize(8);
      doc.setTextColor(148, 163, 184);
      doc.text(`Generated by Odysseus Academic Timetable System — Approved Institutional Schedule`, 14, 202);
      doc.text(`Section ${i} of ${pageCount}`, 265, 202);
    }

    const cleanDept = (currentBatch.department || 'CSE').replace(/[^a-zA-Z0-9]/g, '_');
    doc.save(`Timetable_${cleanDept}_Sem${currentBatch.semester}_All_Sections.pdf`);
    setMessage({ type: 'success', text: `Downloaded complete PDF for all ${deptBatches.length} sections.` });
  };

  return (
    <div>
      {/* Top Header & Section Selector */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 800, background: 'linear-gradient(to right, #ffffff, #c084fc)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            Section Timetables & Monitoring
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: '0.25rem' }}>
            NEP-2020 Multi-Period Constraint Timetables with Break-Aware Lab Blocks & Live Relocation
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Layers size={18} style={{ color: 'var(--accent-primary)' }} />
            <select
              value={selectedBatchId || ''}
              onChange={(e) => setSelectedBatchId(Number(e.target.value))}
              style={{
                background: 'var(--bg-secondary)',
                color: 'var(--text-main)',
                border: '1px solid var(--border-accent)',
                borderRadius: '8px',
                padding: '0.5rem 1rem',
                fontSize: '0.95rem',
                fontWeight: 700,
                outline: 'none',
                cursor: 'pointer'
              }}
            >
              {batches.map(b => (
                <option key={b.id} value={b.id}>
                  {b.batchName} (Sem {b.semester} &bull; {b.department})
                </option>
              ))}
            </select>
          </div>

          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <button
              onClick={downloadSectionPdf}
              className="btn btn-secondary"
              style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '0.4rem', border: '1px solid rgba(99,102,241,0.4)', background: 'rgba(99,102,241,0.15)', color: '#a5b4fc' }}
              title="Download official printable PDF for this section"
            >
              <Download size={15} /> Section PDF
            </button>
            <button
              onClick={downloadDepartmentPdf}
              className="btn btn-secondary"
              style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '0.4rem' }}
              title="Download all sections of this department and semester as a multi-page PDF"
            >
              <Download size={15} /> Dept &amp; Sem PDF
            </button>
            <button
              onClick={() => { setActiveTab('grid'); }}
              className={`btn ${activeTab === 'grid' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem' }}
            >
              <Clock size={16} /> Timetable Grid
            </button>
            <button
              onClick={() => { setActiveTab('room-history'); fetchRoomHistory(selectedBatchId); }}
              className={`btn ${activeTab === 'room-history' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem' }}
            >
              <History size={16} /> Room History ({roomHistory.length})
            </button>
            <button
              onClick={() => { setActiveTab('monitoring'); fetchLessonPlanOverview(); }}
              className={`btn ${activeTab === 'monitoring' ? 'btn-primary' : 'btn-secondary'}`}
              style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem' }}
            >
              <BarChart2 size={16} /> Lesson Plan Tracking
            </button>
            {user?.role === 'ROLE_ADMIN' && (
              <button
                onClick={() => { setShowModal(true); setValidationResult(null); }}
                className="btn btn-secondary"
                style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem' }}
              >
                <Plus size={16} /> Test / Add Slot
              </button>
            )}
          </div>
        </div>
      </div>

      {message && (
        <div style={{
          padding: '0.75rem 1rem',
          borderRadius: '8px',
          marginBottom: '1rem',
          background: message.type === 'success' ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
          border: `1px solid ${message.type === 'success' ? '#10b981' : '#ef4444'}`,
          color: message.type === 'success' ? '#6ee7b7' : '#fca5a5',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between'
        }}>
          <span>{message.text}</span>
          <button onClick={() => setMessage(null)} style={{ background: 'transparent', border: 'none', color: 'inherit', cursor: 'pointer' }}>&times;</button>
        </div>
      )}

      {/* Summary Metrics Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
        <div className="glass-panel" style={{ padding: '1rem' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', fontWeight: 600 }}>TOTAL WEEKLY HOURS</div>
          <div style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--text-main)', marginTop: '0.25rem' }}>{totalWeeklyPeriods} hrs</div>
          <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>40 Periods / Week (8 x 5)</div>
        </div>

        <div className="glass-panel" style={{ padding: '1rem' }}>
          <div style={{ fontSize: '0.75rem', color: '#c084fc', fontWeight: 600 }}>ACADEMIC HOURS</div>
          <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#c084fc', marginTop: '0.25rem' }}>{academicPeriods} hrs</div>
          <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Required: 33 Academic Hours</div>
        </div>

        <div className="glass-panel" style={{ padding: '1rem' }}>
          <div style={{ fontSize: '0.75rem', color: '#38bdf8', fontWeight: 600 }}>SPECIAL ACTIVITIES</div>
          <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#38bdf8', marginTop: '0.25rem' }}>{specialPeriods} hrs</div>
          <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Career Skills, LeetCode, Aptitude</div>
        </div>

        <div className="glass-panel" style={{ padding: '1rem' }}>
          <div style={{ fontSize: '0.75rem', color: '#94a3b8', fontWeight: 600 }}>FREE / LIBRARY</div>
          <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#94a3b8', marginTop: '0.25rem' }}>{freePeriods} hrs</div>
          <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Unallocated / Self-Study</div>
        </div>

        <div className="glass-panel" style={{ padding: '1rem' }}>
          <div style={{ fontSize: '0.75rem', color: '#10b981', fontWeight: 600 }}>ALLOCATED CLASSROOM</div>
          <div style={{ fontSize: '1.3rem', fontWeight: 800, color: '#10b981', marginTop: '0.25rem', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {timetable.find(t => t.room && t.sessionType === 'THEORY')?.room?.roomCode || timetable.find(t => t.room)?.room?.roomCode || 'Pending Solve'}
          </div>
          <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
            {(() => {
              const r = timetable.find(t => t.room && t.sessionType === 'THEORY')?.room || timetable.find(t => t.room)?.room;
              return r ? `${r.blockName || 'Block'} • Floor ${r.floorNumber || 1}` : 'No allocation yet';
            })()}
          </div>
        </div>
      </div>

      {/* Main Tab Views */}
      {activeTab === 'grid' && (
        <div className="glass-panel" style={{ padding: '1.5rem', overflowX: 'auto' }}>
          {loading ? (
            <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>Loading section timetable...</div>
          ) : timetable.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '3rem' }}>
              <Clock size={40} style={{ color: 'var(--text-dim)', marginBottom: '0.75rem' }} />
              <div style={{ fontWeight: 700, fontSize: '1.1rem' }}>No Timetable Published Yet</div>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginTop: '0.25rem' }}>
                Use the Timetable Generator to create an authoritative schedule for this section.
              </p>
            </div>
          ) : (
            <>
              {/* Faculty-mode banner: show only when ROLE_FACULTY is logged in */}
              {isFaculty && (
                <div style={{
                  display: 'flex', alignItems: 'center', gap: '0.75rem',
                  background: 'rgba(251,191,36,0.1)', border: '1px solid rgba(251,191,36,0.35)',
                  borderRadius: '8px', padding: '0.75rem 1rem', marginBottom: '1rem', flexWrap: 'wrap'
                }}>
                  <Eye size={16} style={{ color: '#fbbf24', flexShrink: 0 }} />
                  <span style={{ fontSize: '0.85rem', color: '#fcd34d', fontWeight: 700 }}>Faculty View</span>
                  <span style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                    Showing timetable for <strong style={{ color: 'var(--text-main)' }}>{batches.find(b => b.id === selectedBatchId)?.batchName}</strong>.
                    Your assigned classes ({myPeriods.length} period{myPeriods.length !== 1 ? 's' : ''} this week) are highlighted in gold.
                    All data is live from the same source as the admin view.
                  </span>
                </div>
              )}
            <table style={{ width: '100%', borderCollapse: 'separate', borderSpacing: '6px', minWidth: '900px' }}>
              <thead>
                <tr>
                  <th style={{ padding: '0.75rem', background: 'var(--bg-secondary)', borderRadius: '6px', color: 'var(--text-muted)', fontSize: '0.8rem', width: '110px' }}>
                    DAY / PERIOD
                  </th>
                  {periods.map((p, i) => (
                    <th key={i} style={{
                      padding: '0.6rem',
                      background: p.isBreak || p.isLunch ? 'rgba(255, 255, 255, 0.03)' : 'var(--bg-secondary)',
                      borderRadius: '6px',
                      textAlign: 'center',
                      border: p.isBreak || p.isLunch ? '1px dashed rgba(255, 255, 255, 0.1)' : 'none'
                    }}>
                      <div style={{ fontSize: '0.85rem', fontWeight: 800, color: p.isBreak || p.isLunch ? 'var(--accent-warning)' : 'var(--text-main)' }}>
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
                            background: 'rgba(255, 255, 255, 0.02)',
                            borderRadius: '6px',
                            color: 'var(--text-dim)',
                            fontSize: '0.7rem',
                            letterSpacing: '0.08em',
                            fontWeight: 700,
                            verticalAlign: 'middle'
                          }}>
                            {period.label}
                          </td>
                        );
                      }

                      const assignment = getAssignmentAt(day, period.num);

                      if (!assignment) {
                        return (
                          <td key={pIdx} style={{
                            background: 'rgba(255, 255, 255, 0.015)',
                            borderRadius: '6px',
                            textAlign: 'center',
                            color: 'var(--text-dim)',
                            fontSize: '0.75rem',
                            padding: '0.5rem'
                          }}>
                            &mdash;
                          </td>
                        );
                      }

                      // If continuous block, only render cell at start of block
                      if (assignment.periodNumber !== period.num) {
                        return null; // Spanned by previous cell
                      }

                       const meta = getActivityMeta(assignment);
                      const colSpan = assignment.durationPeriods > 1 ? assignment.durationPeriods : 1;
                      const isMe = isMyAssignment(assignment);

                      return (
                        <td
                          key={pIdx}
                          colSpan={colSpan}
                          style={{
                            padding: '0.65rem',
                            borderRadius: '8px',
                            background: isMe ? 'rgba(251,191,36,0.18)' : meta.bg,
                            border: isMe ? '2px solid #fbbf24' : `1px solid ${meta.border}`,
                            verticalAlign: 'top',
                            position: 'relative'
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.35rem' }}>
                            <span style={{
                              fontSize: '0.65rem',
                              fontWeight: 800,
                              padding: '2px 6px',
                              borderRadius: '4px',
                              background: isMe ? '#fbbf24' : meta.border,
                              color: '#000'
                            }}>
                              {isMe ? '★ ' : ''}{meta.label}
                            </span>
                            {user?.role === 'ROLE_ADMIN' && (
                              <div style={{ display: 'flex', gap: '4px' }}>
                                <button
                                  onClick={() => {
                                    setSelectedAssignment(assignment);
                                    setNewRoomId(assignment.room?.id?.toString() || '');
                                    setShowRoomModal(true);
                                  }}
                                  title="Change classroom"
                                  style={{ background: 'transparent', border: 'none', color: meta.color, cursor: 'pointer', padding: 0 }}
                                >
                                  <ArrowRightLeft size={13} />
                                </button>
                                <button
                                  onClick={() => handleDeleteAssignment(assignment.id)}
                                  title="Delete slot"
                                  style={{ background: 'transparent', border: 'none', color: '#f87171', cursor: 'pointer', padding: 0 }}
                                >
                                  <Trash2 size={13} />
                                </button>
                              </div>
                            )}
                          </div>

                          <div style={{ fontWeight: 800, fontSize: '0.85rem', color: 'var(--text-main)', lineHeight: 1.2 }}>
                            {assignment.subject?.subjectCode || 'SPECIAL'}
                          </div>
                          <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', margin: '2px 0 4px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            {assignment.subject?.subjectName}
                          </div>

                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.68rem', color: isMe ? '#fcd34d' : meta.color, marginTop: '4px' }}>
                            <span style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
                              <Users size={11} /> {assignment.faculty?.name || 'Assigned'}
                            </span>
                            <span
                              style={{ display: 'flex', alignItems: 'center', gap: '3px', fontWeight: 700 }}
                              title={assignment.room ? `${assignment.room.roomName || ''} (${assignment.room.blockName || 'Block'} • Floor ${assignment.room.floorNumber || 1})` : ''}
                            >
                              <DoorOpen size={11} /> {assignment.room?.roomCode}
                              {assignment.room?.blockName ? <span style={{ opacity: 0.75, fontSize: '0.62rem' }}>({assignment.room.blockName.replace('Block', '').trim()}-F{assignment.room.floorNumber || 1})</span> : null}
                            </span>
                          </div>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
            </>
          )}
        </div>
      )}

      {/* Classroom Change History Tab */}
      {activeTab === 'room-history' && (
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 800 }}>Classroom Change & Relocation Log</h3>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Maintains full audit history of relocations with instant restore capability
            </span>
          </div>

          {roomHistory.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '2.5rem', color: 'var(--text-dim)' }}>
              No classroom change history recorded for this section.
            </div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Subject</th>
                  <th>Original Room</th>
                  <th>Relocated Room</th>
                  <th>Reason</th>
                  <th>Changed By</th>
                  <th>Timestamp</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {roomHistory.map(h => (
                  <tr key={h.id}>
                    <td style={{ fontWeight: 700 }}>{h.subject?.subjectCode} - {h.subject?.subjectName}</td>
                    <td><span className="badge" style={{ background: 'rgba(255, 255, 255, 0.08)' }}>{h.originalRoom?.roomCode}</span></td>
                    <td><span className="badge" style={{ background: 'rgba(168, 85, 247, 0.2)', color: '#c084fc' }}>{h.newRoom?.roomCode}</span></td>
                    <td style={{ color: 'var(--text-muted)' }}>{h.reason}</td>
                    <td>{h.changedBy || 'Admin'}</td>
                    <td style={{ fontSize: '0.8rem', color: 'var(--text-dim)' }}>{h.timestamp ? new Date(h.timestamp).toLocaleString() : 'N/A'}</td>
                    <td>
                      {h.active ? (
                        <button
                          onClick={() => handleRestoreRoom(h.id)}
                          className="btn btn-secondary"
                          style={{ padding: '0.3rem 0.65rem', fontSize: '0.75rem', gap: '4px' }}
                        >
                          <RotateCcw size={13} /> Restore Usual
                        </button>
                      ) : (
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>Restored</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* Lesson Plan Monitoring Tab */}
      {activeTab === 'monitoring' && (
        <div className="glass-panel" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 800 }}>Department Lesson Plan Delivery & Progress</h3>
            <button onClick={fetchLessonPlanOverview} className="btn btn-secondary" style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem' }}>
              <RefreshCw size={14} /> Refresh Metrics
            </button>
          </div>

          {loadingOverview ? (
            <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>Computing syllabus coverage...</div>
          ) : lessonPlanOverview.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-dim)' }}>
              No active lesson plans generated yet. Use the Lesson Plan module to upload syllabi and generate schedules.
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1rem' }}>
              {lessonPlanOverview.map((item, idx) => (
                <div key={idx} className="glass-panel" style={{ padding: '1.25rem', background: 'rgba(255, 255, 255, 0.02)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <div>
                      <span className="badge" style={{ background: 'var(--border-accent)', color: 'var(--text-main)', marginBottom: '0.4rem' }}>
                        {item.batchName}
                      </span>
                      <h4 style={{ fontWeight: 800, fontSize: '0.95rem' }}>{item.subjectCode}</h4>
                      <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{item.subjectName}</p>
                    </div>
                    <span style={{ fontSize: '1.2rem', fontWeight: 800, color: item.completionPercentage >= 75 ? '#34d399' : '#fbbf24' }}>
                      {item.completionPercentage}%
                    </span>
                  </div>

                  {/* Progress Bar */}
                  <div style={{ height: '6px', background: 'rgba(255, 255, 255, 0.08)', borderRadius: '3px', margin: '0.85rem 0', overflow: 'hidden' }}>
                    <div style={{ width: `${item.completionPercentage}%`, height: '100%', background: 'linear-gradient(90deg, #8b5cf6, #38bdf8)', borderRadius: '3px' }} />
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                    <span>Faculty: {item.facultyName}</span>
                    <span>Completed: {item.completedSessions} / {item.totalSessions} sessions</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Relocate Classroom Modal */}
      <Modal
        isOpen={showRoomModal && !!selectedAssignment}
        onClose={() => setShowRoomModal(false)}
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <ArrowRightLeft size={20} style={{ color: 'var(--accent-primary)' }} />
            <span>Relocate Classroom</span>
          </div>
        }
      >
        {selectedAssignment && (
          <div>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '1rem' }}>
              Relocating <strong>{selectedAssignment.subject?.subjectCode}</strong> on {selectedAssignment.dayOfWeek} P{selectedAssignment.periodNumber}
            </p>

            <div className="form-group">
              <label className="form-label">Current Room</label>
              <input type="text" className="form-control" value={selectedAssignment.room?.roomCode} disabled />
            </div>

            <div className="form-group">
              <label className="form-label">Select Target Room</label>
              <select className="form-control" value={newRoomId} onChange={e => setNewRoomId(e.target.value)}>
                <option value="">-- Choose New Room --</option>
                {rooms.map(r => (
                  <option key={r.id} value={r.id}>
                    {r.roomCode} ({r.roomType} &bull; Cap: {r.capacity})
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Reason for Relocation</label>
              <input
                type="text"
                className="form-control"
                placeholder="e.g. Projector maintenance, Air conditioning repair"
                value={roomChangeReason}
                onChange={e => setRoomChangeReason(e.target.value)}
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
              <button className="btn btn-secondary" onClick={() => setShowRoomModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleChangeRoom}>Confirm Relocation</button>
            </div>
          </div>
        )}
      </Modal>

      {/* Conflict-Checker / Add Slot Modal */}
      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <ShieldCheck size={22} style={{ color: 'var(--accent-primary)' }} />
            <span>Schedule Assignment Validator</span>
          </div>
        }
      >
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <div className="form-group">
            <label className="form-label">Subject</label>
            <select className="form-control" value={formData.subjectId} onChange={e => setFormData({ ...formData, subjectId: e.target.value })}>
              <option value="">-- Choose Subject --</option>
              {subjects.map(s => <option key={s.id} value={s.id}>{s.subjectCode} - {s.subjectName}</option>)}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Faculty</label>
            <select className="form-control" value={formData.facultyId} onChange={e => setFormData({ ...formData, facultyId: e.target.value })}>
              <option value="">-- Choose Faculty --</option>
              {faculty.map(f => <option key={f.id} value={f.id}>{f.name} ({f.department})</option>)}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Room</label>
            <select className="form-control" value={formData.roomId} onChange={e => setFormData({ ...formData, roomId: e.target.value })}>
              <option value="">-- Choose Room --</option>
              {rooms.map(r => <option key={r.id} value={r.id}>{r.roomCode} ({r.roomType})</option>)}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Day of Week</label>
            <select className="form-control" value={formData.dayOfWeek} onChange={e => setFormData({ ...formData, dayOfWeek: e.target.value })}>
              {days.map(d => <option key={d} value={d}>{d}</option>)}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Start Period</label>
            <select className="form-control" value={formData.periodNumber} onChange={e => setFormData({ ...formData, periodNumber: e.target.value })}>
              {[1, 2, 3, 4, 5, 6, 7, 8].map(p => <option key={p} value={p}>Period {p}</option>)}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Duration (Periods)</label>
            <select className="form-control" value={formData.durationPeriods} onChange={e => setFormData({ ...formData, durationPeriods: e.target.value })}>
              <option value="1">1 Period (Theory / Tutorial)</option>
              <option value="2">2 Periods (Lab Block)</option>
              <option value="3">3 Periods (Extended Lab Block)</option>
            </select>
          </div>
        </div>

        {/* Validation Feedback Area */}
        {validationResult && (
          <div style={{
            marginTop: '1rem',
            padding: '0.85rem',
            borderRadius: '8px',
            background: validationResult.valid ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
            border: `1px solid ${validationResult.valid ? '#10b981' : '#ef4444'}`
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 700, color: validationResult.valid ? '#34d399' : '#f87171' }}>
              {validationResult.valid ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
              <span>{validationResult.valid ? 'Constraint Satisfaction Confirmed' : `Conflict Detected: ${validationResult.conflictType}`}</span>
            </div>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '0.35rem' }}>
              {validationResult.message}
            </p>
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
          <button className="btn btn-secondary" onClick={() => setShowModal(false)}>Close</button>
          <button className="btn btn-secondary" onClick={handleValidate} disabled={validating}>
            {validating ? 'Validating...' : 'Check Conflict'}
          </button>
          {validationResult?.valid && (
            <button className="btn btn-primary" onClick={handleSaveAssignment}>
              Commit Assignment
            </button>
          )}
        </div>
      </Modal>
    </div>
  );
}
