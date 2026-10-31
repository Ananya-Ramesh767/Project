const tokenRes = await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' })
});
const tokenData = await tokenRes.json();
const headers = { Authorization: `Bearer ${tokenData.token}` };

const subRes = await fetch('http://localhost:8080/api/admin/subjects', { headers });
const subjects = await subRes.json();

console.log('Subjects for Parallel Processing or Theory of Computation:');
subjects.forEach(s => {
  if (s.subjectCode.includes('501') || s.subjectCode.includes('502') || s.subjectCode.includes('CE0901')) {
    console.log(`ID: ${s.id} | Code: ${s.subjectCode} | Name: ${s.subjectName} | Dept: ${s.department} | Sem: ${s.semester} | Active: ${s.active}`);
  }
});

