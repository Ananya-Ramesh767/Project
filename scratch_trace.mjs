const tokenRes = await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' })
});
const tokenData = await tokenRes.json();
const headers = { Authorization: `Bearer ${tokenData.token}` };

// 1. Offerings
const offRes = await fetch('http://localhost:8080/api/admin/offerings?department=CSE&semester=5', { headers });
const offs = await offRes.json();
console.log('--- SUBJECT OFFERINGS IN DB ---');
console.log('Total Offerings:', offs.length);
const cseA = offs.filter(o => o.batch && o.batch.batchName && o.batch.batchName.includes('A'));
console.log('CSE-A Offerings count:', cseA.length);
cseA.forEach(o => {
  console.log(`[${o.id}] Batch: ${o.batch.batchName} | Subject: ${o.subject?.subjectCode} (${o.subject?.subjectName}) | Assigned Faculty: ${o.assignedFaculty?.name || 'NULL'}`);
});

// Check if there are duplicates for same batch and subject in DB:
const counts = {};
offs.forEach(o => {
  const k = `${o.batch?.id}-${o.subject?.id}`;
  counts[k] = (counts[k] || 0) + 1;
});
const dupes = Object.entries(counts).filter(([k, v]) => v > 1);
console.log('Duplicate (batch, subject) pairs in subject_offerings DB:', dupes);

// 2. Propose Assignment
const propRes = await fetch('http://localhost:8080/api/admin/faculty/propose-assignment?department=CSE&semester=5', { headers });
const propData = await propRes.json();
console.log('\n--- PROPOSE ASSIGNMENT API RESPONSE ---');
console.log('Batches returned:', propData.batches?.length);
console.log('Proposals returned:', propData.proposals?.length);
if (propData.proposals) {
  propData.proposals.slice(0, 3).forEach(p => {
    console.log(`Subject: ${p.subjectCode} - ${p.subjectName}`);
    console.log(`  requiredFacultyCount: ${p.requiredFacultyCount}`);
    console.log(`  suggestedFacultyIds:`, p.suggestedFacultyIds);
    console.log(`  suggestedSectionFaculty:`, p.suggestedSectionFaculty);
    console.log(`  existingSectionFaculty:`, p.existingSectionFaculty);
    console.log(`  eligibleFaculty count:`, p.eligibleFaculty?.length);
  });
}

