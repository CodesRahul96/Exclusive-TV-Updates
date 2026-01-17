Add-Type -AssemblyName System.IO.Compression
$urls = @(
    "https://avkb.short.gy/epg.xml.gz",
    "https://avkb.short.gy/jioepg.xml.gz",
    "https://avkb.short.gy/tsepg.xml.gz"
)
$targets = @("Star Plus", "Star Bharat", "Sony Max", "Sony Wah", "Colors")

foreach ($url in $urls) {
    Write-Host "`n=== Checking Source: $url ==="
    try {
        $tmp = [System.IO.Path]::GetTempFileName()
        Invoke-WebRequest -Uri $url -OutFile $tmp -UserAgent "Mozilla/5.0"
        
        $fs = [System.IO.File]::OpenRead($tmp)
        $gs = New-Object System.IO.Compression.GZipStream($fs, [System.IO.Compression.CompressionMode]::Decompress)
        $reader = New-Object System.IO.StreamReader($gs)
        
        $foundChannels = 0
        while (($line = $reader.ReadLine()) -ne $null) {
            if ($line -like "*<display-name*") {
                foreach ($target in $targets) {
                    if ($line -like "*$target*") {
                        Write-Host "FOUND: $($line.Trim())"
                        $foundChannels++
                        break
                    }
                }
            }
            # Limit search to first 2MB of XML content to avoid long waits
            if ($reader.BaseStream.Position -gt 2MB) { break }
        }
        $reader.Close()
        $fs.Close()
        Remove-Item $tmp
        Write-Host "Total targets found in sample: $foundChannels"
    }
    catch {
        Write-Host "Error: $_"
    }
}
