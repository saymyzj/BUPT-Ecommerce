param(
    [switch]$ResetMiddleware,
    [switch]$RestartServices
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$logRoot = Join-Path $root "logs\local-start"
$pidRoot = Join-Path $logRoot "pids"
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
New-Item -ItemType Directory -Path $pidRoot -Force | Out-Null
& (Join-Path $PSScriptRoot "load-env.ps1") -Root $root

function Test-PortOpen {
    param(
        [Parameter(Mandatory = $true)][int]$Port
    )

    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if ($async.AsyncWaitHandle.WaitOne(500)) {
            $client.EndConnect($async)
            $client.Close()
            return $true
        }
        $client.Close()
    } catch {
    }
    return $false
}

function Start-ServiceProcess {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [Parameter(Mandatory = $true)][string]$MavenModule,
        [Parameter(Mandatory = $true)][int]$Port
    )

    if (Test-PortOpen -Port $Port) {
        Write-Host "$ServiceName is already listening on port $Port, skip starting."
        return
    }

    $logFile = Join-Path $logRoot "$ServiceName.log"
    $pidFile = Join-Path $pidRoot "$ServiceName.pid"
    $command = "mvn -pl $MavenModule -DskipTests spring-boot:run > `"$logFile`" 2>&1"
    $process = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $command -WindowStyle Hidden -WorkingDirectory $root -PassThru
    Set-Content -Path $pidFile -Value $process.Id
}

function Stop-ProcessTree {
    param(
        [Parameter(Mandatory = $true)][int]$ProcessId
    )

    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue
    foreach ($child in $children) {
        Stop-ProcessTree -ProcessId $child.ProcessId
    }

    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($process -ne $null) {
        Stop-Process -Id $ProcessId -Force
    }
}

function Stop-ProcessOnPort {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [Parameter(Mandatory = $true)][int]$Port
    )

    if (-not $RestartServices) {
        return
    }

    $pidFile = Join-Path $pidRoot "$ServiceName.pid"
    if (-not (Test-Path $pidFile)) {
        if (Test-PortOpen -Port $Port) {
            Write-Host "$ServiceName port $Port is occupied, but no PID file from this script was found. Leaving that process running."
        }
        return
    }

    $processIdText = (Get-Content -Path $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    $processId = 0
    if (-not [int]::TryParse($processIdText, [ref]$processId)) {
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
        return
    }

    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($process -ne $null) {
        Write-Host "Stopping script-started $ServiceName process $($process.Id) on port $Port..."
        Stop-ProcessTree -ProcessId $process.Id
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue

    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-PortOpen -Port $Port)) {
            return
        }
        Start-Sleep -Seconds 1
    }

    throw "Timed out waiting for $ServiceName port $Port to close"
}

function Wait-ForPort {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortOpen -Port $Port) {
            return
        }
        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for port $Port"
}

function Wait-ForMySqlReady {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerName,
        [int]$TimeoutSeconds = 180
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        docker exec -e MYSQL_PWD=password $ContainerName mysqladmin ping -uroot --silent *> $null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 3
    }

    throw "Timed out waiting for MySQL container $ContainerName to become ready"
}

function Get-ContainerStatus {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerName
    )

    $status = docker ps -a --filter "name=^/$ContainerName$" --format "{{.Status}}"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to query Docker container $ContainerName"
    }
    return $status
}

function Reset-ContainerIfRequested {
    param(
        [Parameter(Mandatory = $true)][string]$ContainerName
    )

    if (-not $ResetMiddleware) {
        return
    }

    $status = Get-ContainerStatus -ContainerName $ContainerName
    if (-not [string]::IsNullOrWhiteSpace($status)) {
        Write-Host "Removing existing middleware container $ContainerName because -ResetMiddleware was specified..."
        docker rm -f $ContainerName | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to remove Docker container $ContainerName"
        }
    }
}

function Ensure-MiddlewareContainer {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [Parameter(Mandatory = $true)][string]$ContainerName,
        [Parameter(Mandatory = $true)][int]$Port
    )

    Reset-ContainerIfRequested -ContainerName $ContainerName

    $status = Get-ContainerStatus -ContainerName $ContainerName
    if ([string]::IsNullOrWhiteSpace($status)) {
        Write-Host "Creating $ContainerName..."
        docker compose up -d $ServiceName
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to create Docker service $ServiceName"
        }
    } elseif ($status -notlike "Up*") {
        Write-Host "Starting existing $ContainerName..."
        docker start $ContainerName | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to start $ContainerName. If it was created with old settings, rerun: .\scripts\start-local.ps1 -ResetMiddleware"
        }
    } else {
        Write-Host "$ContainerName is already running."
    }

    Wait-ForPort -Port $Port
}

function Test-HttpHealth {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [Parameter(Mandatory = $true)][int]$Port
    )

    try {
        $response = Invoke-RestMethod -Uri "http://localhost:$Port/actuator/health" -TimeoutSec 5
        Write-Host "$ServiceName health: $($response.status)"
    } catch {
        Write-Host "$ServiceName health check failed on port ${Port}: $($_.Exception.Message)"
    }
}

Write-Host "Starting middleware containers..."
Ensure-MiddlewareContainer -ServiceName "mysql" -ContainerName "bupt-ecommerce-mysql" -Port 3306
Wait-ForMySqlReady -ContainerName "bupt-ecommerce-mysql"
Ensure-MiddlewareContainer -ServiceName "redis" -ContainerName "bupt-ecommerce-redis" -Port 6379
Ensure-MiddlewareContainer -ServiceName "rabbitmq" -ContainerName "bupt-ecommerce-rabbitmq" -Port 5672

Write-Host "Starting services..."
Stop-ProcessOnPort -ServiceName "gateway-service" -Port 8080
Start-ServiceProcess -ServiceName "gateway-service" -MavenModule "gateway-service" -Port 8080
Wait-ForPort -Port 8080
Stop-ProcessOnPort -ServiceName "user-service" -Port 8081
Start-ServiceProcess -ServiceName "user-service" -MavenModule "user-service" -Port 8081
Wait-ForPort -Port 8081
Stop-ProcessOnPort -ServiceName "product-seckill-service" -Port 8082
Start-ServiceProcess -ServiceName "product-seckill-service" -MavenModule "product-seckill-service" -Port 8082
Wait-ForPort -Port 8082
Stop-ProcessOnPort -ServiceName "order-service" -Port 8083
Start-ServiceProcess -ServiceName "order-service" -MavenModule "order-service" -Port 8083
Wait-ForPort -Port 8083
Stop-ProcessOnPort -ServiceName "ai-service" -Port 8084
Start-ServiceProcess -ServiceName "ai-service" -MavenModule "ai-service" -Port 8084
Wait-ForPort -Port 8084
Stop-ProcessOnPort -ServiceName "push-service" -Port 8085
Start-ServiceProcess -ServiceName "push-service" -MavenModule "push-service" -Port 8085
Wait-ForPort -Port 8085

Write-Host "Checking service health..."
Test-HttpHealth -ServiceName "gateway-service" -Port 8080
Test-HttpHealth -ServiceName "user-service" -Port 8081
Test-HttpHealth -ServiceName "product-seckill-service" -Port 8082
Test-HttpHealth -ServiceName "order-service" -Port 8083
Test-HttpHealth -ServiceName "ai-service" -Port 8084
Test-HttpHealth -ServiceName "push-service" -Port 8085

Write-Host "Done. Check logs under $logRoot"
Write-Host "API Gateway: http://localhost:8080"
Write-Host "Swagger UI:  http://localhost:8080/swagger-ui.html"
Write-Host "Test page:   $root\simple-test-page\index.html"
