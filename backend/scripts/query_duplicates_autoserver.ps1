# PowerShell script to query duplicate timetable assignments using H2 Shell with AUTO_SERVER mode
$jar = "C:\Users\anany\.m2\repository\com\h2database\h2\2.2.224\h2-2.2.224.jar"
# Use same DB file path as in application.yml (without .mv.db extension)
$dbPath = "$PSScriptRoot\..\data\odysseus_db"
# Connection URL matching application.yml (includes AUTO_SERVER to allow concurrent access)
$url = "jdbc:h2:file:$dbPath;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1"
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
java -cp $jar org.h2.tools.Shell -url $url -user sa -sql $sql

