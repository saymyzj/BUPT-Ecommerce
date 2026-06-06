$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$logRoot = Join-Path $root "logs\local-start"
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
& (Join-Path $PSScriptRoot "load-env.ps1") -Root $root

function Start-ServiceProcess {
    param(
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [Parameter(Mandatory = $true)][string]$MavenModule
    )

    $logFile = Join-Path $logRoot "$ServiceName.log"
    $command = "mvn -pl $MavenModule -am spring-boot:run -DskipTests > `"$logFile`" 2>&1"
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $command -WindowStyle Hidden -WorkingDirectory $root | Out-Null
}

function Wait-ForPort {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $client = New-Object System.Net.Sockets.TcpClient
            $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
            if ($async.AsyncWaitHandle.WaitOne(500)) {
                $client.EndConnect($async)
                $client.Close()
                return
            }
            $client.Close()
        } catch {
        }
        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for port $Port"
}

Write-Host "Starting middleware containers..."
docker compose up -d mysql redis rabbitmq

Write-Host "Starting services..."
Start-ServiceProcess -ServiceName "gateway-service" -MavenModule "gateway-service"
Wait-ForPort -Port 8080
Start-ServiceProcess -ServiceName "user-service" -MavenModule "user-service"
Wait-ForPort -Port 8081
Start-ServiceProcess -ServiceName "product-seckill-service" -MavenModule "product-seckill-service"
Wait-ForPort -Port 8082
Start-ServiceProcess -ServiceName "order-service" -MavenModule "order-service"
Wait-ForPort -Port 8083
Start-ServiceProcess -ServiceName "ai-service" -MavenModule "ai-service"
Wait-ForPort -Port 8084
Start-ServiceProcess -ServiceName "push-service" -MavenModule "push-service"
Wait-ForPort -Port 8085

Write-Host "Done. Check logs under $logRoot"
