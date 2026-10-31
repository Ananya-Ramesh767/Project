const res = await fetch("http://localhost:8080/api/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "admin", password: "admin123" })
});
const data = await res.json();
const headers = { Authorization: "Bearer " + data.token };

const [bRes, oRes, tRes] = await Promise.all([
  fetch("http://localhost:8080/api/admin/batches", { headers }),
  fetch("http://localhost:8080/api/admin/offerings", { headers }),
  fetch("http://localhost:8080/api/admin/timetable/assignments", { headers }).catch(() => ({ ok: false }))
]);

const batches = await bRes.json();
const offerings = await oRes.json();
let timetable = [];
if (tRes.ok) {
  timetable = await tRes.json();
}

console.log("Total Batches:", batches.length);
console.log("Total Offerings:", offerings.length);
console.log("Total Timetable Assignments:", timetable.length);

// Group batches by (canonicalDept, semester, section)
function getCanonicalDept(dept) {
  if (!dept) return "UNKNOWN";
  const d = dept.trim().toLowerCase();
  if (d.includes("computer science and engineering") || d.includes("computer science engineering") || d === "cse") return "CSE";
  if (d.includes("artificial intelligence & machine learning") || d.includes("ai & ml") || d.includes("cse-aiml")) return "CSE-AIML";
  if (d.includes("cyber security") || d.includes("cse-cs")) return "CSE-CS";
  if (d.includes("information technology") || d === "it") return "IT";
  if (d.includes("mechanical") || d === "mech") return "MECH";
  if (d.includes("civil")) return "CIVIL";
  if (d.includes("electrical") || d.includes("eee")) return "EEE";
  if (d.includes("electronics") || d.includes("ece")) return "ECE";
  if (d.includes("biomedical") || d.includes("bme")) return "BME";
  if (d.includes("automobile") || d.includes("auto")) return "AUTO";
  if (d.includes("robotics")) return "ROBOTICS";
  if (d.includes("data science") || d.includes("aids")) return "AIDS";
  if (d.includes("biotech")) return "BIOTECH";
  if (d.includes("business system") || d.includes("csbs")) return "CSBS";
  if (d.includes("design") || d.includes("csd")) return "CSD";
  if (d.includes("business administration") || d.includes("mba")) return "MBA";
  if (d.includes("computer applications") || d.includes("mca")) return "MCA";
  return dept.trim();
}

const batchGroups = {};
batches.forEach(b => {
  const cDept = getCanonicalDept(b.department);
  const sec = (b.section || "").toUpperCase();
  const sem = b.semester;
  const key = `${cDept} | Sem ${sem} | Sec ${sec}`;
  if (!batchGroups[key]) batchGroups[key] = [];
  batchGroups[key].push(b);
});

console.log("\n--- DUPLICATE BATCH GROUPS DETECTED ---");
const duplicates = [];
for (const [key, list] of Object.entries(batchGroups)) {
  if (list.length > 1) {
    console.log(`\nGroup: ${key} (${list.length} duplicate records)`);
    list.forEach(b => {
      const bOfferings = offerings.filter(o => o.batch?.id === b.id);
      const assignedCount = bOfferings.filter(o => o.assignedFaculty != null).length;
      const bTimetable = timetable.filter(t => t.batch?.id === b.id);
      console.log(`  -> ID: ${b.id} | BatchName: "${b.batchName}" | Dept: "${b.department}" | Offerings: ${bOfferings.length} (${assignedCount} with faculty) | TT assignments: ${bTimetable.length}`);
    });
    duplicates.push({ key, list });
  }
}

console.log(`\nTotal duplicate groups: ${duplicates.length}`);

