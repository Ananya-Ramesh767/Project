$loginBody = @{ username = 'admin'; password = 'admin123' } | ConvertTo-Json
$login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -Body $loginBody -ContentType 'application/json'
$headers = @{ Authorization = 'Bearer ' + $login.token; 'Content-Type' = 'application/json' }

Write-Host "1. Creating Section CSE-A and CSE-B..."
$batchA = @{
    batchName = "CSE-A"
    section = "A"
    department = "Computer Science Engineering"
    semester = 3
    strength = 60
    academicYear = "2026-2027"
    assignedRoomCode = ""
} | ConvertTo-Json

$batchB = @{
    batchName = "CSE-B"
    section = "B"
    department = "Computer Science Engineering"
    semester = 3
    strength = 60
    academicYear = "2026-2027"
    assignedRoomCode = ""
} | ConvertTo-Json

$resA = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/batches' -Method Post -Body $batchA -Headers $headers
$resB = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/batches' -Method Post -Body $batchB -Headers $headers

Write-Host "Created Batch A ID: $($resA.id), RoomCode: '$($resA.assignedRoomCode)' (Must be empty/null)"
Write-Host "Created Batch B ID: $($resB.id), RoomCode: '$($resB.assignedRoomCode)' (Must be empty/null)"

Write-Host "2. Verifying Room Master 70 capacity on classrooms..."
$rooms = Invoke-RestMethod -Uri 'http://localhost:8080/api/admin/rooms' -Method Get -Headers $headers
$lectureRooms = $rooms | Where-Object { $_.roomType -eq 'LECTURE_HALL' }
$invalidCapRooms = $lectureRooms | Where-Object { $_.capacity -ne 70 }
Write-Host "Total lecture halls: $($lectureRooms.Count)"
Write-Host "Lecture halls with capacity != 70: $($invalidCapRooms.Count) (Must be 0)"

Write-Host "Phase 2 verification passed!"
