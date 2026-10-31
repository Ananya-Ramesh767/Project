const res = await fetch("http://localhost:8080/api/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "admin", password: "admin123" })
});
const data = await res.json();
const headers = { Authorization: "Bearer " + data.token };

const oRes = await fetch("http://localhost:8080/api/admin/offerings", { headers });
const offerings = await oRes.json();

console.log("Offerings on batches 65..71 that have faculty assigned:");
offerings.filter(o => o.batch?.id >= 65 && o.batch?.id <= 71 && o.assignedFaculty != null).forEach(o => {
  console.log(`Offering ${o.id} | Batch ${o.batch.id} (${o.batch.batchName}) | Subject: ${o.subject?.subjectCode} (${o.subject?.subjectName}) | Faculty: ${o.assignedFaculty.name}`);
});

console.log("\nOfferings on batches 97..103 that have faculty assigned:");
offerings.filter(o => o.batch?.id >= 97 && o.batch?.id <= 103 && o.assignedFaculty != null).forEach(o => {
  console.log(`Offering ${o.id} | Batch ${o.batch.id} (${o.batch.batchName}) | Subject: ${o.subject?.subjectCode} (${o.subject?.subjectName}) | Faculty: ${o.assignedFaculty.name}`);
});

