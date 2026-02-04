package io.flowmobile.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConnectionTest {
    
    @Test
    fun createsValidConnection() {
        val connection = Connection(
            id = "conn-1",
            sourceComponentId = "comp-1",
            sourcePortId = "out",
            targetComponentId = "comp-2",
            targetPortId = "in"
        )
        
        assertEquals("conn-1", connection.id)
        assertEquals("comp-1", connection.sourceComponentId)
        assertEquals("out", connection.sourcePortId)
        assertEquals("comp-2", connection.targetComponentId)
        assertEquals("in", connection.targetPortId)
    }
    
    @Test
    fun failsWithBlankId() {
        assertFailsWith<IllegalArgumentException> {
            Connection(
                id = "",
                sourceComponentId = "comp-1",
                sourcePortId = "out",
                targetComponentId = "comp-2",
                targetPortId = "in"
            )
        }
    }
    
    @Test
    fun failsWithBlankSourceComponentId() {
        assertFailsWith<IllegalArgumentException> {
            Connection(
                id = "conn-1",
                sourceComponentId = "",
                sourcePortId = "out",
                targetComponentId = "comp-2",
                targetPortId = "in"
            )
        }
    }
    
    @Test
    fun failsWithBlankTargetComponentId() {
        assertFailsWith<IllegalArgumentException> {
            Connection(
                id = "conn-1",
                sourceComponentId = "comp-1",
                sourcePortId = "out",
                targetComponentId = "",
                targetPortId = "in"
            )
        }
    }
    
    @Test
    fun failsWhenConnectingComponentToItself() {
        assertFailsWith<IllegalArgumentException> {
            Connection(
                id = "conn-1",
                sourceComponentId = "comp-1",
                sourcePortId = "out",
                targetComponentId = "comp-1",
                targetPortId = "in"
            )
        }
    }
}
