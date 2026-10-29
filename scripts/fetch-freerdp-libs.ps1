# Windows version of fetch-freerdp-libs.sh: download aFreeRDP 3.32.1 (arm64-v8a) from
# F-Droid and extract its native libraries into app\src\main\jniLibs\arm64-v8a.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-Location (Join-Path $PSScriptRoot '..')
$url = 'https://f-droid.org/repo/com.freerdp.afreerdp_3320103.apk'
$sha = '520403BF41DEC2E4599DDD22AB2BD7597F64867EC422C5E2FA46DA259CAC668E'
New-Item -ItemType Directory -Force dl | Out-Null
$apk = 'dl\afreerdp_3320103.apk'
if (-not (Test-Path $apk)) { curl.exe -fL --retry 5 -o $apk $url }
if ((Get-FileHash $apk -Algorithm SHA256).Hash -ne $sha) { throw "SHA-256 mismatch: $apk" }
$dst = 'app\src\main\jniLibs\arm64-v8a'
if (Test-Path $dst) { Remove-Item -Recurse -Force $dst }
New-Item -ItemType Directory -Force $dst | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $apk))
try {
  $z.Entries | Where-Object { $_.FullName -like 'lib/arm64-v8a/*' } | ForEach-Object {
    [IO.Compression.ZipFileExtensions]::ExtractToFile($_, (Join-Path $dst $_.Name), $true)
  }
} finally { $z.Dispose() }
Get-ChildItem $dst | ForEach-Object Name
