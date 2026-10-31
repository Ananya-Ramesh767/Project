$loginBody = @{ username = 'admin'; password = 'admin123' } | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -Body $loginBody -ContentType 'application/json'
$headers = @{ Authorization = 'Bearer ' + $login.token }

Write-Host '1. Triggering Database Backup...'
$backupRes = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/system/backup' -Method Post -Headers $headers
Write-Host 'Backup result:' ($backupRes | ConvertTo-Json -Compress)

Write-Host '2. Triggering Clean Reset (preserving Room Master 111 rooms)...'
$clearRes = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/system/clear-data?clearBatches=true' -Method Post -Headers $headers
Write-Host 'Clear result:' ($clearRes | ConvertTo-Json -Compress)

Write-Host '3. Verifying database state...'
$rooms = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/rooms' -Method Get -Headers $headers
$subjects = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/subjects' -Method Get -Headers $headers
$faculty = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/faculty' -Method Get -Headers $headers
$offerings = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/offerings' -Method Get -Headers $headers
$batches = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/batches' -Method Get -Headers $headers

Write-Host "Rooms count: $($rooms.Count) (Must be 111)"
Write-Host "Subjects count: $($subjects.Count) (Must be 0)"
Write-Host "Faculty count: $($faculty.Count) (Must be 0)"
Write-Host "Offerings count: $($offerings.Count) (Must be 0)"
Write-Host "Batches count: $($batches.Count) (Must be 0)"
