const tokenRes = await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' })
});
const tokenData = await tokenRes.json();
const headers = { Authorization: `Bearer ${tokenData.token}` };

const offRes = await fetch('http://localhost:8080/api/admin/offerings', { headers });
const offs = await offRes.json();

console.log('Offerings for Parallel Processing (ID 196):');
const ppOffs = offs.filter(o => o.subject && o.subject.id === 196);
console.log('Total offerings for 196:', ppOffs.length);
ppOffs.forEach(o => {
  console.log(`Offering ID: ${o.id} | Batch ID: ${o.batch?.id} | Batch Name: ${o.batch?.batchName} | Section: ${o.batch?.section} | Faculty: ${o.assignedFaculty?.name || 'NULL'} | Active: ${o.active}`);
});

