import java.io.*
import java.net.URL
import java.util.zip.GZIPInputStream
import javax.xml.parsers.DocumentBuilderFactory

fun main(args: Array<String>) {
    val urls = listOf(
        "https://avkb.short.gy/epg.xml.gz",
        "https://avkb.short.gy/jioepg.xml.gz",
        "https://avkb.short.gy/tsepg.xml.gz"
    )

    for (urlStr in urls) {
        println("\n--- Source: $urlStr ---")
        try {
            val url = URL(urlStr)
            val connection = url.openConnection()
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            val bis = BufferedInputStream(GZIPInputStream(connection.getInputStream()))
            
            val reader = BufferedReader(InputStreamReader(bis, "UTF-8"))
            var count = 0
            var line: String? = reader.readLine()
            while (line != null && count < 100) {
                if (line.contains("<display-name") || line.contains("<channel id=")) {
                    println(line.trim())
                    count++
                }
                line = reader.readLine()
            }
            bis.close()
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }
}
