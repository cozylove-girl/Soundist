param([string]$MoodistUpstream)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $root "../..")).Path
$ambientRoot = Join-Path $root "app/src/main/assets/sounds"
$stagingSourceRoot = Join-Path $repositoryRoot "public/sounds"
$vscoRoot = Join-Path $root "app/src/main/assets/instruments/vsco"

if ([string]::IsNullOrWhiteSpace($MoodistUpstream)) {
    $MoodistUpstream = Join-Path $root "../../../moodist-upstream"
}
$MoodistUpstream = (Resolve-Path -LiteralPath $MoodistUpstream).Path
$upstreamSounds = Join-Path $MoodistUpstream "public/sounds"
$upstreamCommit = (& git -C $MoodistUpstream rev-parse HEAD).Trim()
$upstreamRemote = (& git -C $MoodistUpstream remote get-url origin).Trim()

function Sha256([string]$Path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

$ambientRows = foreach ($file in Get-ChildItem -LiteralPath $ambientRoot -Recurse -File | Where-Object Name -ne "README.txt" | Sort-Object FullName) {
    $relativePath = $file.FullName.Substring((Resolve-Path -LiteralPath $ambientRoot).Path.Length + 1).Replace("\", "/")
    $upstreamPath = Join-Path $upstreamSounds $relativePath
    $stagingSourcePath = Join-Path $stagingSourceRoot $relativePath
    if (-not (Test-Path -LiteralPath $upstreamPath)) { throw "Moodist upstream asset is missing: $relativePath" }
    if (-not (Test-Path -LiteralPath $stagingSourcePath)) { throw "Soundist staging source is missing: $relativePath" }
    $assetHash = Sha256 $file.FullName
    $upstreamHash = Sha256 $upstreamPath
    $stagingSourceHash = Sha256 $stagingSourcePath
    if ($assetHash -ne $upstreamHash) { throw "Ambient asset differs from Moodist upstream: $relativePath" }
    if ($assetHash -ne $stagingSourceHash) { throw "Packaged ambient asset differs from public/sounds source: $relativePath" }
    [pscustomobject]@{
        asset_path = "app/src/main/assets/sounds/$relativePath"
        bytes = $file.Length
        sha256 = $assetHash
        staging_source_path = "public/sounds/$relativePath"
        staging_source_sha256 = $stagingSourceHash
        upstream_path = "public/sounds/$relativePath"
        upstream_sha256 = $upstreamHash
        exact_match = "true"
        upstream_repository = $upstreamRemote
        upstream_commit = $upstreamCommit
        upstream_declared_license_families = "Pixabay Content License OR CC0 1.0"
        per_file_license_status = "not-published-by-upstream"
    }
}
if (@($ambientRows).Count -ne 84) { throw "Expected 84 ambient assets, found $(@($ambientRows).Count)" }
$ambientRows | Export-Csv -Delimiter "`t" -NoTypeInformation -Encoding utf8 -LiteralPath (Join-Path $root "AMBIENT_AUDIO_MANIFEST.tsv")

$vscoRows = foreach ($file in Get-ChildItem -LiteralPath $vscoRoot -File -Filter "*.wav" | Sort-Object Name) {
    [pscustomobject]@{
        asset_path = "app/src/main/assets/instruments/vsco/$($file.Name)"
        bytes = $file.Length
        sha256 = Sha256 $file.FullName
        source_repository = "https://github.com/sgossner/VSCO-2-CE"
        license = "CC0 1.0"
        license_file = "app/src/main/assets/instruments/vsco/VSCO-2-CE-LICENSE.txt"
    }
}
if (@($vscoRows).Count -ne 15) { throw "Expected 15 VSCO samples, found $(@($vscoRows).Count)" }
$vscoRows | Export-Csv -Delimiter "`t" -NoTypeInformation -Encoding utf8 -LiteralPath (Join-Path $root "VSCO_SAMPLE_LICENSE_MANIFEST.tsv")

Write-Host "Ambient assets verified: 84/84 exact Moodist copies at $upstreamCommit"
Write-Host "VSCO samples inventoried: 15/15 under CC0 1.0"
