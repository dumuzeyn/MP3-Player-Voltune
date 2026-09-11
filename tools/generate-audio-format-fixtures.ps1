param([string]$Ffmpeg = "ffmpeg")
$ErrorActionPreference = "Stop"
$directory = Join-Path $PSScriptRoot "../app/src/androidTest/assets/audio-formats"
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$formats = [ordered]@{
    wav = "pcm_s16le"
    mp3 = "libmp3lame"
    m4a = "aac"
    aac = "aac"
    ogg = "libvorbis"
    oga = "libvorbis"
    opus = "libopus"
    flac = "flac"
}
foreach ($entry in $formats.GetEnumerator()) {
    & $Ffmpeg -hide_banner -loglevel error -y -f lavfi -i "sine=frequency=440:sample_rate=48000:duration=2" -ac 2 -c:a $entry.Value (Join-Path $directory "tone.$($entry.Key)")
    if ($LASTEXITCODE -ne 0) { throw "Fixture encoding failed: $($entry.Key)" }
}
