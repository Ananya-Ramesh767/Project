$body = '{"username":"admin","password":"admin123"}'
$login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -Body $body -ContentType 'application/json'
$headers = @{ Authorization = "Bearer " + $login.token }

Write-Host "=== FACULTY ==="
$faculty = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/faculty' -Headers $headers
Write-Host "Total faculty: $($faculty.Count)"
$faculty | Select-Object -First 20 id, employeeId, name, department | Format-Table

Write-Host "=== BATCHES ==="
$batches = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/batches' -Headers $headers
Write-Host "Total batches: $($batches.Count)"
$batches | Format-Table id, batchName, department, semester, section

Write-Host "=== OFFERINGS ==="
$offerings = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/offerings' -Headers $headers
Write-Host "Total offerings: $($offerings.Count)"
$offerings | Select-Object -First 10 id, @{N='Batch';E={$_.batch.batchName}}, @{N='Subject';E={$_.subject.subjectCode}}, @{N='Faculty';E={$_.assignedFaculty.name}} | Format-Table
