package org.xoops.support

import com.intellij.codeInspection.LocalInspectionTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/** Every registered inspection instantiates, matches its shortName and ships a description page. */
class PluginDescriptorTest {

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
