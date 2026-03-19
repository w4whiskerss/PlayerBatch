$ErrorActionPreference = "Stop"

$workspace = Split-Path -Parent $PSScriptRoot
$javaHome = (Resolve-Path "$workspace\.tools\jdk21\jdk-21.0.10+7").Path
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

$targets = @(
    @{ minecraft = "1.20"; fabric = "0.83.0+1.20"; carpet = "1.20-1.4.112+v230608"; java = "17" },
    @{ minecraft = "1.20.1"; fabric = "0.92.7+1.20.1"; carpet = "1.20-1.4.112+v230608"; java = "17" },
    @{ minecraft = "1.20.2"; fabric = "0.91.6+1.20.2"; carpet = "1.20.2-1.4.119+v230928"; java = "17" },
    @{ minecraft = "1.20.3"; fabric = "0.91.1+1.20.3"; carpet = "1.20.3-1.4.128+v231205"; java = "17" },
    @{ minecraft = "1.20.4"; fabric = "0.97.3+1.20.4"; carpet = "1.20.3-1.4.128+v231205"; java = "17" },
    @{ minecraft = "1.20.5"; fabric = "0.97.8+1.20.5"; carpet = "1.20.5-1.4.140+v240423"; java = "21" },
    @{ minecraft = "1.20.6"; fabric = "0.100.8+1.20.6"; carpet = "1.20.6-1.4.141+v240429"; java = "21" },
    @{ minecraft = "1.21"; fabric = "0.102.0+1.21"; carpet = "1.21-1.4.147+v240613"; java = "21" },
    @{ minecraft = "1.21.1"; fabric = "0.116.9+1.21.1"; carpet = "1.21-1.4.147+v240613"; java = "21" },
    @{ minecraft = "1.21.2"; fabric = "0.106.1+1.21.2"; carpet = "1.21.2-1.4.158+v241022"; java = "21" },
    @{ minecraft = "1.21.3"; fabric = "0.114.1+1.21.3"; carpet = "1.21.2-1.4.158+v241022"; java = "21" },
    @{ minecraft = "1.21.4"; fabric = "0.119.4+1.21.4"; carpet = "1.21.4-1.4.161+v241203"; java = "21" },
    @{ minecraft = "1.21.5"; fabric = "0.128.2+1.21.5"; carpet = "1.21.5-1.4.169+v250325"; java = "21" },
    @{ minecraft = "1.21.6"; fabric = "0.128.2+1.21.6"; carpet = "1.21.6-1.4.176+v250617"; java = "21" },
    @{ minecraft = "1.21.7"; fabric = "0.129.0+1.21.7"; carpet = "1.21.7-1.4.177+v250630"; java = "21" },
    @{ minecraft = "1.21.8"; fabric = "0.136.1+1.21.8"; carpet = "1.21.7-1.4.177+v250630"; java = "21" },
    @{ minecraft = "1.21.9"; fabric = "0.134.1+1.21.9"; carpet = "1.21.9-1.4.185+v250930"; java = "21" },
    @{ minecraft = "1.21.10"; fabric = "0.138.4+1.21.10"; carpet = "1.21.10-1.4.188+v251016"; java = "21" },
    @{ minecraft = "1.21.11"; fabric = "0.141.3+1.21.11"; carpet = "1.21.11-1.4.194+v260107"; java = "21" }
)

$distDir = Join-Path $workspace "dist"
New-Item -ItemType Directory -Force $distDir | Out-Null

foreach ($target in $targets) {
    $mc = $target.minecraft
    $carpetBase = ($target.carpet.Split("-")[-1]).Split("+")[0]
    $javaDep = if ($target.java -eq "17") { ">=17" } else { ">=21" }
    $jarName = "playerbatch-1.0.0-mc$mc.jar"

    if (Test-Path "$distDir\$jarName") {
        Write-Host "Skipping $mc (already built)" -ForegroundColor Yellow
        continue
    }

    Write-Host "Building $mc" -ForegroundColor Cyan
    & "$workspace\gradlew.bat" clean build `
        "-Pminecraft_version=$mc" `
        "-Pfabric_version=$($target.fabric)" `
        "-Pcarpet_version=$($target.carpet)" `
        "-Pjava_version=$($target.java)" `
        "-Pminecraft_dependency=~$mc" `
        "-Pcarpet_dependency=>=$carpetBase" `
        "-Pjava_dependency=$javaDep"

    if ($LASTEXITCODE -ne 0) {
        throw "Build failed for $mc"
    }

    Copy-Item "$workspace\build\libs\$jarName" "$distDir\$jarName" -Force
}

Write-Host "Done. Jars are in $distDir" -ForegroundColor Green

