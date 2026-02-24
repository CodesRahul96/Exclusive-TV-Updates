package com.codesrahul.exclusivetv.models

import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

fun parse() {
    val xml = """
    <tv>
      <channel id="COLORS.HD.in">
        <display-name lang="en">Colors HD</display-name>
        <icon src="http://jiotv.catchup.cdn.jio.com/..." />
        <url>http://www.jio.com</url>
      </channel>
    </tv>
    """
    
    val factory = XmlPullParserFactory.newInstance()
    val parser = factory.newPullParser()
    parser.setInput(StringReader(xml))
    
    val channelIdToNames = mutableMapOf<String, MutableSet<String>>()
    var eventType = parser.eventType
    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
        if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "channel") {
            val internedId = parser.getAttributeValue(null, "id")
            channelIdToNames.getOrPut(internedId) { mutableSetOf() }.add(internedId)
            var depth = 1
            while (depth > 0) {
                val nextType = try { parser.next() } catch (e: Exception) { break }
                if (nextType == org.xmlpull.v1.XmlPullParser.END_DOCUMENT) break
                
                if (nextType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    depth++
                    if (parser.name == "display-name") {
                        val dn = try { parser.nextText() } catch (e: Exception) { "" }
                        if (dn.isNotEmpty()) channelIdToNames[internedId]?.add(dn)
                        depth-- 
                    }
                } else if (nextType == org.xmlpull.v1.XmlPullParser.END_TAG) {
                    depth--
                }
            }
        }
        eventType = try { parser.next() } catch (e: Exception) { org.xmlpull.v1.XmlPullParser.END_DOCUMENT }
    }
    
    println("Results: " + channelIdToNames)
}

fun main() {
    parse()
}
