$body = '{"username":"admin","password":"admin123"}'
$login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -Body $body -ContentType 'application/json'
$headers = @{ Authorization = "Bearer " + $login.token }

$subjects = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/subjects' -Headers $headers
$subjects | Format-Table id, subjectCode, subjectName, department, semester, calculatedWeeklyHours
