# PowerShell script to query duplicate timetable assignments using H2 shell
$jar = "C:\Users\anany\.m2\repository\com\h2database\h2\2.2.224\h2-2.2.224.jar"
$dbPath = "C:\Users\anany\Downloads\TIMETABLE_GENERATION (2)\TIMETABLE_GENERATION (1)\TIMETABLE_GENERATION\java project\OOPS-JAVA---Odysseus\backend\data\odysseus_db"
# SQL to fetch duplicate assignments with details
$sql = @"
SELECT ta.id, ta.batch_id, ta.subject_id, ta.academic_year, ta.semester, ta.faculty_id, ta.is_lab_block, ta.activity_type
FROM timetable_assignments ta
WHERE (ta.batch_id, ta.subject_id, ta.academic_year, ta.semester) IN (
    SELECT batch_id, subject_id, academic_year, semester
    FROM timetable_assignments
    GROUP BY batch_id, subject_id, academic_year, semester
    HAVING COUNT(*) > 1
)
ORDER BY ta.batch_id, ta.subject_id, ta.academic_year, ta.semester;
"@
# Run H2 Shell
java -cp $jar org.h2.tools.Shell -url "jdbc:h2:file:$dbPath;IFEXISTS=TRUE" -user sa -sql $sql

