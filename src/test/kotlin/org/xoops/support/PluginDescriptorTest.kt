package org.xoops.support

import com.intellij.codeInspection.LocalInspectionTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.util.jar.JarFile
import javax.xml.parsers.DocumentBuilderFactory

/** Every registered inspection instantiates, matches its shortName and ships a description page. */
class PluginDescriptorTest {

    @Test
    fun registeredLiveTemplatesLoadFromPluginJar() {
        JarFile(System.getProperty("xoops.plugin.jar")).use { jar ->
            val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val descriptor = jar.getInputStream(jar.getJarEntry("META-INF/plugin.xml")).use { parser.parse(it) }
            val registrations = descriptor.getElementsByTagName("defaultLiveTemplates")
            assertEquals(1, registrations.length)
            val path = (registrations.item(0) as Element).getAttribute("file")
            val entry = jar.getJarEntry(path)
            assertNotNull("missing registered live templates: $path", entry)
            val templates = jar.getInputStream(entry).use { parser.parse(it) }
            assertEquals("XOOPS", templates.documentElement.getAttribute("group"))
            val entries = templates.getElementsByTagName("template")
            val names = (0 until entries.length).map { index ->
                (entries.item(index) as Element).getAttribute("name")
            }.toSet()
            assertEquals(
                setOf("xoguard", "xofetch", "xolang", "xocriteria", "xorequest", "xofetchdb", "xohead", "xoexec"),
                names,
            )
        }
    }

    @Test
    fun everyLocalInspectionIsConsistent() {
        val descriptor = javaClass.getResourceAsStream("/META-INF/plugin.xml")
        assertNotNull("plugin.xml must be on the test classpath", descriptor)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(descriptor)
        val inspections = document.getElementsByTagName("localInspection")
        assertTrue("expected the XOOPS inspection pack", inspections.length >= 20)
        for (i in 0 until inspections.length) {
            val inspection = inspections.item(i) as Element
            val shortName = inspection.getAttribute("shortName")
            assertTrue("shortName", shortName.isNotBlank())
            val tool = Class.forName(inspection.getAttribute("implementationClass"))
                .getDeclaredConstructor().newInstance() as LocalInspectionTool
            assertEquals(shortName, tool.shortName)
            assertNotNull(
                "missing inspectionDescriptions/$shortName.html",
                javaClass.getResource("/inspectionDescriptions/$shortName.html"),
            )
        }
    }
}
