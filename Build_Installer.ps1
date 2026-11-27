# STORM 3DS - Windows Release Packaging & StormInstaller Sign Pipeline
$ErrorActionPreference = "Stop"
[System.Diagnostics.Process]::GetCurrentProcess().PriorityClass = [System.Diagnostics.ProcessPriorityClass]::BelowNormal

$baseDir = $PSScriptRoot
if (-not $baseDir) { $baseDir = "F:\STORM 3DS" }

$appConfigPath = Join-Path $baseDir "buildSrc\src\main\kotlin\AppConfig.kt"
$appVersion = "1.11.1"
if (Test-Path $appConfigPath) {
    $cfgText = Get-Content $appConfigPath -Raw
    if ($cfgText -match 'versionName\s*=\s*"([^"]+)"') {
        $appVersion = $Matches[1].TrimStart('v', 'V')
    }
}

$releasesDir = Join-Path $baseDir "releases"
$installerDir = Join-Path $baseDir "installer"
$installerProjDir = Join-Path $installerDir "StormInstaller"
$outputDir = Join-Path $installerDir "Output"
$winReleaseDir = Join-Path $baseDir "core\build_win\bin\Release"

if (-not (Test-Path $releasesDir)) { New-Item -ItemType Directory -Path $releasesDir | Out-Null }
if (-not (Test-Path $outputDir)) { New-Item -ItemType Directory -Path $outputDir | Out-Null }

$exePath = Join-Path $winReleaseDir "STORM_3DS.exe"
if (-not (Test-Path $exePath)) {
    throw "ERROR: STORM_3DS.exe not found at $exePath. Build citra_meta first!"
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "   STORM 3DS $appVersion - Windows Setup & Sign Pipeline" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# Step 1: Locate STORM TEAM Master Certificate (UAC Publisher: STORM TEAM)
$cert = $null
foreach ($loc in @('CurrentUser', 'LocalMachine')) {
    try {
        $store = [System.Security.Cryptography.X509Certificates.X509Store]::new('My', $loc)
        $store.Open('ReadOnly')
        $cert = $store.Certificates | Where-Object { $_.HasPrivateKey -and $_.Subject -like "*CN=STORM TEAM*" } | Select-Object -First 1
        $store.Close()
        if ($cert) { break }
    } catch { }
}
if (-not $cert) {
    throw "CRITICAL ERROR: STORM TEAM Master Certificate (CN=STORM TEAM) not found in certificate store!"
}
$certThumb = $cert.Thumbprint
Write-Host "[1/4] Certificate: $($cert.Subject) [$certThumb]" -ForegroundColor Green

$cerInstaller = Join-Path $installerDir "STORM_Certificate.cer"
[System.IO.File]::WriteAllBytes($cerInstaller, $cert.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Cert))

$signtool = (Get-ChildItem "C:\Program Files (x86)\Windows Kits\10\bin\*\x64\signtool.exe" -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName)
if (-not $signtool -or -not (Test-Path $signtool)) {
    throw "ERROR: signtool.exe not found under Windows Kits 10!"
}

function Invoke-StormSign([string]$FilePath) {
    $tsUrls = @(
        "http://timestamp.digicert.com",
        "http://timestamp.sectigo.com",
        "http://tsa.starfieldtech.com",
        "http://timestamp.entrust.net/TSS/RFC3161sha2TS"
    )
    $signed = $false
    foreach ($ts in $tsUrls) {
        for ($attempt = 1; $attempt -le 3; $attempt++) {
            & $signtool sign /fd SHA256 /tr $ts /td SHA256 /d "STORM 3DS" /du "https://github.com/ReiKatari/STORM_3DS" /sha1 $certThumb $FilePath | Out-Null
            if ($LASTEXITCODE -eq 0) {
                $signed = $true
                break
            }
            Start-Sleep -Milliseconds 800
        }
        if ($signed) { break }
    }
    if (-not $signed) {
        Write-Warning "Timestamp sign failed for $FilePath, falling back to non-timestamped signature..."
        & $signtool sign /fd SHA256 /d "STORM 3DS" /du "https://github.com/ReiKatari/STORM_3DS" /sha1 $certThumb $FilePath
        if ($LASTEXITCODE -ne 0) {
            throw "signtool failed for $FilePath"
        }
    }
}

# Step 2: Sign STORM_3DS.exe and repackage portable ZIP
Write-Host "[2/4] Signing STORM_3DS.exe and updating portable ZIP..." -ForegroundColor Yellow
Unblock-File -Path $exePath -ErrorAction SilentlyContinue
Invoke-StormSign $exePath

$winZipPath = Join-Path $releasesDir "STORM_3DS_${appVersion}_Windows_x64.zip"
if (-not (Test-Path $winZipPath)) {
    $latestZip = Get-ChildItem $releasesDir -Filter "STORM_3DS_*_Windows_x64.zip" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($latestZip) {
        Copy-Item $latestZip.FullName $winZipPath -Force
    }
}

$env:STORM_WIN_ZIP = $winZipPath
$env:STORM_EXE = $exePath
$env:STORM_CER = $cerInstaller
$env:STORM_REL_DIR = $winReleaseDir
$env:PYTHONUTF8 = "1"
@'
import os, zipfile, tempfile, shutil

zip_path = os.environ["STORM_WIN_ZIP"]
exe_path = os.environ["STORM_EXE"]
cer_path = os.environ["STORM_CER"]
rel_dir = os.environ["STORM_REL_DIR"]

# Sync SDL2.dll into core/build_win/bin/Release if missing (so Release/STORM_3DS.exe runs standalone)
sdl_rel = os.path.join(rel_dir, "SDL2.dll")
if not os.path.exists(sdl_rel) and os.path.exists(zip_path):
    with zipfile.ZipFile(zip_path, "r") as zf:
        for name in zf.namelist():
            if name.lower().endswith("sdl2.dll"):
                with open(sdl_rel, "wb") as f:
                    f.write(zf.read(name))
                break

dead_ffmpeg = {"avcodec-61.dll", "avformat-61.dll", "avutil-59.dll", "swresample-5.dll", "swscale-8.dll"}

assert os.path.exists(zip_path), f"Base zip missing: {zip_path}"
fd, tmp_zip = tempfile.mkstemp(suffix=".zip", dir=os.path.dirname(zip_path))
os.close(fd)
try:
    with zipfile.ZipFile(zip_path, "r") as zin, zipfile.ZipFile(tmp_zip, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as zout:
        for item in zin.infolist():
            fn = item.filename.lstrip("./\\")
            if fn.lower() in ("storm_3ds.exe", "storm_certificate.cer") or os.path.basename(fn).lower() in dead_ffmpeg:
                continue
            zout.writestr(item, zin.read(item.filename))
        zout.write(exe_path, arcname="STORM_3DS.exe")
        zout.write(cer_path, arcname="STORM_Certificate.cer")
    shutil.move(tmp_zip, zip_path)
finally:
    if os.path.exists(tmp_zip):
        os.remove(tmp_zip)
'@ | python -
if ($LASTEXITCODE -ne 0) { throw "Failed to repackage $winZipPath" }

# Step 3: Build single-file StormInstaller (.NET 8 win-x64, low CPU)
Write-Host "[3/4] Building StormInstaller ($appVersion)..." -ForegroundColor Yellow
if (Test-Path "$installerProjDir\bin") { Remove-Item "$installerProjDir\bin" -Recurse -Force -ErrorAction SilentlyContinue }
if (Test-Path "$installerProjDir\obj") { Remove-Item "$installerProjDir\obj" -Recurse -Force -ErrorAction SilentlyContinue }

$env:STORM_CERT_PATH = $cerInstaller
dotnet publish "$installerProjDir\StormInstaller.csproj" `
    -c Release -r win-x64 --self-contained true `
    -p:Version=$appVersion -p:AssemblyVersion="$appVersion.0" -p:FileVersion="$appVersion.0" `
    -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true `
    -p:EnableCompressionInSingleFile=true -m:1 /nr:false
if ($LASTEXITCODE -ne 0) { throw "dotnet publish failed for StormInstaller!" }

$publishedInstaller = "$installerProjDir\bin\Release\net8.0-windows\win-x64\publish\StormInstaller.exe"
if (-not (Test-Path $publishedInstaller)) {
    throw "StormInstaller.exe was not produced at $publishedInstaller"
}

$setupReleasePath = Join-Path $releasesDir "STORM_3DS_${appVersion}_Setup.exe"
$setupOutputPath = Join-Path $outputDir "STORM_3DS_${appVersion}_Setup.exe"
Copy-Item $publishedInstaller $setupReleasePath -Force
Copy-Item $publishedInstaller $setupOutputPath -Force

# Step 4: Sign installers (no version in /d) & Unblock
Write-Host "[4/4] Signing $setupReleasePath..." -ForegroundColor Yellow
Invoke-StormSign $setupReleasePath
Invoke-StormSign $setupOutputPath
Unblock-File -Path $setupReleasePath -ErrorAction SilentlyContinue
Unblock-File -Path $setupOutputPath -ErrorAction SilentlyContinue
Unblock-File -Path $winZipPath -ErrorAction SilentlyContinue

try {
    Import-Certificate -FilePath $cerInstaller -CertStoreLocation "Cert:\CurrentUser\TrustedPublisher" -ErrorAction SilentlyContinue | Out-Null
} catch { }

$hSetup = (Get-FileHash $setupReleasePath -Algorithm SHA256).Hash.ToLowerInvariant()
$sSetup = (Get-Item $setupReleasePath).Length
$hZip = (Get-FileHash $winZipPath -Algorithm SHA256).Hash.ToLowerInvariant()
$sZip = (Get-Item $winZipPath).Length

Write-Host "============================================================" -ForegroundColor Green
Write-Host "DONE! STORM 3DS $appVersion Windows packages ready:" -ForegroundColor Green
Write-Host "  Setup EXE: $setupReleasePath ($sSetup bytes) sha256=$hSetup" -ForegroundColor Green
Write-Host "  Win ZIP:   $winZipPath ($sZip bytes) sha256=$hZip" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
