Add-Type -AssemblyName System.IO.Compression
$urls = @(
    "https://avkb.short.gy/epg.xml.gz",
    "https://avkb.short.gy/jioepg.xml.gz",
    "https://avkb.short.gy/tsepg.xml.gz"
)

foreach ($url in $urls) {
    Write-Host "--- Source: $url ---"
    try {
        $tmp = [System.IO.Path]::GetTempFileName()
        # Use System.Net.WebClient for more direct control or Invoke-WebRequest
        Invoke-WebRequest -Uri $url -OutFile $tmp -UserAgent "Mozilla/5.0"
        
        $fs = [System.IO.File]::OpenRead($tmp)
        $gs = New-Object System.IO.Compression.GZipStream($fs, [System.IO.Compression.CompressionMode]::Decompress)
        $reader = New-Object System.IO.StreamReader($gs)
        
        $c = 0
        while ($c -lt 50 -and ($line = $reader.ReadLine()) -ne $null) {
            if ($line -like "*<display-name*" -or $line -like "*<channel id=*") {
                Write-Host $line.Trim()
                $c++
            }
        }
        $reader.Close()
        $fs.Close()
        Remove-Item $tmp
    }
    catch {
        Write-Host "Error: $_"
    }
}
