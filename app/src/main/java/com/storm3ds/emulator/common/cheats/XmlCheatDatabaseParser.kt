package com.storm3ds.emulator.common.cheats

import com.storm3ds.emulator.domain.model.CheatDatabase
import com.storm3ds.emulator.domain.model.Game
import com.storm3ds.emulator.impl.XmlCheatDatabaseSAXHandler
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

class XmlCheatDatabaseParser : CheatDatabaseParser {
    override fun parseCheatDatabase(databaseStream: ProgressTrackerInputStream, parseListener: CheatDatabaseParserListener) {
        val saxFactory = SAXParserFactory.newInstance().apply {
            isValidating = false
            isXIncludeAware = false
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        val parser = saxFactory.newSAXParser()
        runCatching {
            parser.xmlReader.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
        val handler = XmlCheatDatabaseSAXHandler(object : XmlCheatDatabaseSAXHandler.HandlerListener {
            override fun onCheatDatabaseParseStart(databaseName: String): CheatDatabase {
                return parseListener.onDatabaseParseStart(databaseName)
            }

            override fun onGameParseStart(gameName: String) {
                parseListener.onGameParseStart(gameName)
            }

            override fun onGameParsed(game: Game) {
                parseListener.onGameParsed(game)
            }

            override fun onParseComplete() {
                parseListener.onParseComplete()
            }
        })
        parser.parse(databaseStream, handler)
    }
}