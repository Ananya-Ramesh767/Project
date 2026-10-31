$loginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
$headers = @{ "Authorization" = "Bearer " + $loginRes.token }

Write-Host "Triggering timetable generation for CSE Sem 3..."
$startTime = [System.Diagnostics.Stopwatch]::StartNew()

try {
    $genRes = Invoke-RestMethod -Uri "http://localhost:8080/api/scheduler/generate?department=Computer%20Science%20Engineering&semester=3" -Method Post -Headers $headers
    $jobId = $genRes.jobId
    Write-Host "Job ID: $jobId"

    $status = "PENDING"
    $polls = 0
    while ($status -ne "COMPLETED" -and $status -ne "FAILED" -and $status -ne "INFEASIBLE" -and $polls -lt 60) {
        Start-Sleep -Milliseconds 250
        $job = Invoke-RestMethod -Uri "http://localhost:8080/api/scheduler/jobs/$jobId" -Headers $headers
        $status = $job.status
        $polls++
    }
    $startTime.Stop()

    Write-Host "Final Job Status: $status"
    Write-Host "Total Execution Time: $($startTime.ElapsedMilliseconds) ms ($([math]::Round($startTime.Elapsed.TotalSeconds, 2)) seconds)"
    Write-Host "Message: $($job.message)"
    Write-Host "Total entries generated: $($job.totalEntries)"
} catch {
    $startTime.Stop()
    Write-Host "Error during generation: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host "Response Body: $($reader.ReadToEnd())"
    }
}
