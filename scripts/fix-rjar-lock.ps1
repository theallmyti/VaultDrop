param(
    [string]$GradleTask = ":app:compileDebugKotlin",
    [switch]$RerunTasks
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$rJarPath = "app\build\intermediates\compile_and_runtime_not_namespaced_r_class_jar\debug\processDebugResources\R.jar"
$rJarDir = Split-Path -Parent $rJarPath

function Remove-WithRetry {
    param(
        [string]$Path,
        [switch]$Directory
    )

    for ($attempt = 1; $attempt -le 8; $attempt++) {
        try {
            if (Test-Path $Path) {
                if ($Directory.IsPresent) {
                    Remove-Item $Path -Recurse -Force -ErrorAction Stop
                } else {
                    Remove-Item $Path -Force -ErrorAction Stop
                }
            }

            if (-not (Test-Path $Path)) {
                return $true
            }
        } catch {
            # Retry because file handles can clear between attempts.
        }
    }

    return (-not (Test-Path $Path))
}

Write-Host "Stopping Gradle daemons..."
& .\gradlew --stop | Out-Host

Write-Host "Stopping Gradle-related Java processes..."
$gradleJava = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object {
        $_.CommandLine -and (
            $_.CommandLine -match "GradleDaemon" -or
            $_.CommandLine -match "org\\.gradle\\.launcher\\.daemon" -or
            $_.CommandLine -match "kotlin-daemon"
        )
    }

foreach ($proc in $gradleJava) {
    Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
}

if (Test-Path $rJarPath) {
    Write-Host "Removing locked artifact: $rJarPath"
    $removed = Remove-WithRetry -Path $rJarPath
    if (-not $removed) {
        Write-Host "Lock persists. Forcing all Java processes to stop..."
        & taskkill /F /IM java.exe /T | Out-Null
        $removed = Remove-WithRetry -Path $rJarPath
    }
    if (-not $removed) {
        throw "Unable to delete locked file: $rJarPath"
    }
}

if (Test-Path $rJarDir) {
    Write-Host "Cleaning resources directory: $rJarDir"
    [void](Remove-WithRetry -Path $rJarDir -Directory)
}

$args = @($GradleTask)
if ($RerunTasks.IsPresent) {
    $args += "--rerun-tasks"
}
$args += "--no-daemon"

Write-Host "Running Gradle task: $GradleTask"
& .\gradlew @args
$exitCode = $LASTEXITCODE

if ($exitCode -ne 0) {
    Write-Error "Gradle task failed with exit code $exitCode"
    exit $exitCode
}

Write-Host "R.jar recovery + build completed successfully."
