$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
& (Join-Path $PSScriptRoot "load-env.ps1") -Root $root

$mavenFromTemp = Join-Path $env:TEMP "codex-apache-maven-3.9.9\bin\mvn.cmd"
if (Test-Path $mavenFromTemp) {
    $maven = $mavenFromTemp
} else {
    $maven = "mvn"
}

& $maven -pl ai-service spring-boot:run -DskipTests
