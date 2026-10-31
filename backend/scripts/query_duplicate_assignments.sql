SELECT ta.id, b.name AS batch_name, s.code AS subject_code, f.name AS faculty_name, COUNT(*) AS cnt
FROM timetable_assignments ta
JOIN batches b ON b.id = ta.batch_id
JOIN subjects s ON s.id = ta.subject_id
JOIN faculty f ON f.id = ta.faculty_id
GROUP BY ta.id, batch_name, subject_code, faculty_name
HAVING cnt > 1;

