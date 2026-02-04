$filePath = "app/src/main/java/com/codesrahul/exclusivetv/models/TVList.kt"
$lines = Get-Content $filePath
$newBlock = @(
'        if (peekContent.isNotEmpty() && (peekContent[0].toInt() >= 0x4D00 && peekContent[0].toInt() <= 0x4DFF)) {',
'            try {',
'                val content = file.readText()',
'                val secretKey = SecretManager.getAppKey()',
'                val decrypted = SecurityUtil.decryptChannelData(content, secretKey)',
'                val g = Gua()',
'                val decoded = if (g.verify(decrypted)) g.decode(decrypted) else decrypted',
'                val result = parseUniversal(decoded)',
'                if (result.isNotEmpty()) return@withContext expandNestedPlaylists(result)',
'            } catch (e: Exception) {',
'            }',
'        }'
)
# Lines 655..668 (1-indexed) are indices 654..667 (0-indexed)
$startIdx = 654
$endIdx = 667
$finalLines = $lines[0..($startIdx-1)] + $newBlock + $lines[($endIdx+1)..($lines.Count-1)]
$finalLines | Set-Content $filePath -Encoding UTF8
