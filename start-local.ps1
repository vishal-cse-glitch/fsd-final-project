$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot
$backendRoot = Join-Path $projectRoot 'backend'
$targetRoot = Join-Path $backendRoot 'target'
New-Item -ItemType Directory -Force -Path $targetRoot | Out-Null

$installedJdk21 = Join-Path $env:LOCALAPPDATA 'Programs\Temurin21\jdk-21.0.13+9'
if (Test-Path (Join-Path $installedJdk21 'bin\java.exe')) {
    $javaHome = $installedJdk21
} else {
    $javaHome = $env:JAVA_HOME
}
if (-not (Test-Path (Join-Path $javaHome 'bin\java.exe'))) {
    throw 'Java 21 was not found. Install JDK 21, or set JAVA_HOME to its installation folder.'
}
$env:JAVA_HOME = $javaHome
$env:Path = "$(Join-Path $javaHome 'bin');$env:Path"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw 'Maven was not found on PATH. Install Maven 3.9 or later and reopen PowerShell.'
}
$mavenCommand = (Get-Command mvn).Source
$javaCommand = Join-Path $javaHome 'bin\java.exe'
$nodeCommand = (Get-Command node.exe -ErrorAction SilentlyContinue).Source
if (-not $nodeCommand) {
    throw 'Node.js was not found on PATH. Install Node.js 20 or later and reopen PowerShell.'
}
if (-not (Test-Path (Join-Path $projectRoot 'node_modules\vite'))) {
    throw 'Frontend packages are missing. Run npm install from the project folder.'
}

$backendLog = Join-Path $targetRoot 'studymate-backend.stdout.log'
$backendErrorLog = Join-Path $targetRoot 'studymate-backend.stderr.log'
$frontendLog = Join-Path $targetRoot 'studymate-vite.stdout.log'
$frontendErrorLog = Join-Path $targetRoot 'studymate-vite.stderr.log'
$aiDirectory = Join-Path $targetRoot 'local-ai'
$aiServer = Join-Path $aiDirectory 'llamafile.exe'
$aiModel = Join-Path $aiDirectory 'qwen2.5-0.5b-instruct-q4_k_m.gguf'
$aiLog = Join-Path $targetRoot 'studymate-ai.stdout.log'
$aiErrorLog = Join-Path $targetRoot 'studymate-ai.stderr.log'

function Test-ApiReady {
    try {
        Invoke-WebRequest 'http://127.0.0.1:8080/api/auth/me' -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop | Out-Null
        return $true
    } catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) {
            return $true
        }
        return $false
    }
}

function Test-AiReady {
    try {
        Invoke-WebRequest 'http://127.0.0.1:11434/v1/models' -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop | Out-Null
        return $true
    } catch {
        return $false
    }
}

if (-not (Test-Path $aiServer) -or -not (Test-Path $aiModel)) {
    throw "Free local AI files are missing. Run: powershell -ExecutionPolicy Bypass -File `"$projectRoot\setup-local-ai.ps1`""
}
if ((Get-Item $aiServer).Length -ne 368094430 -or (Get-Item $aiModel).Length -ne 491400032) {
    throw "The local AI downloads are incomplete. Run: powershell -ExecutionPolicy Bypass -File `"$projectRoot\setup-local-ai.ps1`""
}

if (-not (Test-AiReady)) {
    Start-Process -FilePath $aiServer `
        -ArgumentList @('-m', "`"$aiModel`"", '--server', '--host', '127.0.0.1', '--port', '11434', '--ctx-size', '4096') `
        -WorkingDirectory $aiDirectory `
        -RedirectStandardOutput $aiLog `
        -RedirectStandardError $aiErrorLog | Out-Null

    $aiDeadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $aiDeadline -and -not (Test-AiReady)) {
        Start-Sleep -Seconds 2
    }
    if (-not (Test-AiReady)) {
        $details = if (Test-Path $aiLog) { (Get-Content $aiLog -Tail 30) -join "`n" } else { 'No local AI output was produced.' }
        throw "The local AI server did not start on port 11434. AI log: $aiLog`n$details"
    }
}

if (-not (Test-ApiReady)) {
    Push-Location $backendRoot
    try {
        & $mavenCommand '-q' '-DskipTests' 'package'
        $buildExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($buildExitCode -ne 0) {
        throw "Backend packaging failed with exit code $buildExitCode."
    }

    $backendJar = Join-Path $targetRoot 'studymate-api-1.0.0.jar'
    if (-not (Test-Path $backendJar)) {
        throw "Backend JAR was not created: $backendJar"
    }
    Start-Process -FilePath $javaCommand `
        -ArgumentList @('-jar', "`"$backendJar`"", '--spring.profiles.active=local') `
        -WorkingDirectory $backendRoot `
        -RedirectStandardOutput $backendLog `
        -RedirectStandardError $backendErrorLog | Out-Null

    $apiDeadline = (Get-Date).AddMinutes(3)
    while ((Get-Date) -lt $apiDeadline -and -not (Test-ApiReady)) {
        Start-Sleep -Seconds 2
    }
    if (-not (Test-ApiReady)) {
        $details = if (Test-Path $backendLog) { (Get-Content $backendLog -Tail 30) -join "`n" } else { 'No backend output was produced.' }
        throw "The API did not start on port 8080. Backend log: $backendLog`n$details"
    }
}

$frontendUrl = 'http://127.0.0.1:5174'
$frontendReady = $false
try {
    $response = Invoke-WebRequest "$frontendUrl/" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
    $frontendReady = [int]$response.StatusCode -eq 200
} catch {
    $frontendReady = $false
}

if (-not $frontendReady) {
    Start-Process -FilePath $nodeCommand `
        -ArgumentList @((Join-Path $projectRoot 'node_modules\vite\bin\vite.js'), '--host', '127.0.0.1', '--port', '5174', '--strictPort') `
        -WorkingDirectory $projectRoot `
        -RedirectStandardOutput $frontendLog `
        -RedirectStandardError $frontendErrorLog | Out-Null

    $frontendDeadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $frontendDeadline -and -not $frontendReady) {
        Start-Sleep -Seconds 1
        try {
            $response = Invoke-WebRequest "$frontendUrl/" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
            $frontendReady = [int]$response.StatusCode -eq 200
        } catch {
            $frontendReady = $false
        }
    }
    if (-not $frontendReady) {
        $details = if (Test-Path $frontendLog) { (Get-Content $frontendLog -Tail 20) -join "`n" } else { 'No frontend output was produced.' }
        throw "The frontend did not start on port 5174. Frontend log: $frontendLog`n$details"
    }
}

Write-Output "StudyMate is running at $frontendUrl"
Write-Output 'API is ready at http://127.0.0.1:8080 (local H2 database)'
Write-Output 'Free local AI is ready at http://127.0.0.1:11434'
Write-Output "Backend logs: $backendLog"
