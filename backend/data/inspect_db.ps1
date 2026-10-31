$login = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
$headers = @{ Authorization = "Bearer " + $login.token }
$batches = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/batches" -Headers $headers
$batches | Select-Object id, batchName, department, semester | Format-Table

$offerings = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/offerings" -Headers $headers
Write-Host "Total offerings in DB: $($offerings.Length)"
if ($offerings.Length -gt 0) {
try {
    $r = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/readiness?department=Computer%20Science%20Engineering&semester=3" -Headers $headers
    Write-Host "Readiness response:"
    $r | ConvertTo-Json -Depth 4
} catch {
    Write-Host "Readiness failed: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host "Body: $($reader.ReadToEnd())"
    }
}
}
