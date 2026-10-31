const tokenRes = await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' })
});
const tokenData = await tokenRes.json();
const headers = { Authorization: `Bearer ${tokenData.token}` };

const batchRes = await fetch('http://localhost:8080/api/admin/batches', { headers });
const batches = await batchRes.json();

console.log('All Batches in DB:');
batches.forEach(b => {
  if (b.department && b.department.includes('CSE') || b.batchName.includes('CSE')) {
    console.log(`ID: ${b.id} | Name: ${b.batchName} | Section: ${b.section} | Sem: ${b.semester} | Dept: ${b.department} | Year: ${b.academicYear} | Active: ${b.active}`);
  }
});

