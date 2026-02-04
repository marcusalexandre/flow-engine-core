package io.flowmobile.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowTest {
    
    @Test
    fun createsValidFlowWithStartAndEndComponents() {
        val start = StartComponent(id = "start-1", name = "Start")
        val end = EndComponent(id = "end-1", name = "End")
        val connection = Connection(
            id = "conn-1",
            sourceComponentId = "start-1",
            sourcePortId = "out",
            targetComponentId = "end-1",
            targetPortId = "in"
        )
        
        val flow = Flow(
            id = "flow-1",
            name = "Test Flow",
            version = "1.0.0",
            components = listOf(start, end),
            connections = listOf(connection)
        )
        
        assertEquals("flow-1", flow.id)
        assertEquals("Test Flow", flow.name)
        assertEquals(2, flow.components.size)
        assertEquals(1, flow.connections.size)
    }
    
    @Test
    fun failsToCreateFlowWithoutStartComponent() {
        val end = EndComponent(id = "end-1", name = "End")
        
        assertFailsWith<IllegalArgumentException> {
            Flow(
                id = "flow-1",
                name = "Test Flow",
                version = "1.0.0",
                components = listOf(end),
                connections = emptyList()
            )
        }
    }
    
    @Test
    fun failsToCreateFlowWithoutEndComponent() {
        val start = StartComponent(id = "start-1", name = "Start")
        
        assertFailsWith<IllegalArgumentException> {
            Flow(
                id = "flow-1",
                name = "Test Flow",
                version = "1.0.0",
                components = listOf(start),
                connections = emptyList()
            )
        }
    }
    
    @Test
    fun failsToCreateFlowWithMultipleStartComponents() {
        val start1 = StartComponent(id = "start-1", name = "Start 1")
        val start2 = StartComponent(id = "start-2", name = "Start 2")
        val end = EndComponent(id = "end-1", name = "End")
        
        assertFailsWith<IllegalArgumentException> {
            Flow(
                id = "flow-1",
                name = "Test Flow",
                version = "1.0.0",
                components = listOf(start1, start2, end),
                connections = emptyList()
            )
        }
    }
    
    @Test
    fun allowsFlowWithMultipleEndComponents() {
        val start = StartComponent(id = "start-1", name = "Start")
        val end1 = EndComponent(id = "end-1", name = "Success")
        val end2 = EndComponent(id = "end-2", name = "Error")
        
        val flow = Flow(
            id = "flow-1",
            name = "Test Flow",
            version = "1.0.0",
            components = listOf(start, end1, end2),
            connections = emptyList()
        )
        
        val endComponents = flow.getEndComponents()
        assertEquals(2, endComponents.size)
    }
    
    @Test
    fun failsToCreateFlowWithDuplicateComponentIds() {
        val start = StartComponent(id = "comp-1", name = "Start")
        val end = EndComponent(id = "comp-1", name = "End") // Same ID
        
        assertFailsWith<IllegalArgumentException> {
            Flow(
                id = "flow-1",
                name = "Test Flow",
                version = "1.0.0",
                components = listOf(start, end),
                connections = emptyList()
            )
        }
    }
    
    @Test
    fun getStartComponentReturnsTheStartComponent() {
        val start = StartComponent(id = "start-1", name = "Start")
        val end = EndComponent(id = "end-1", name = "End")
        
        val flow = Flow(
            id = "flow-1",
            name = "Test Flow",
            version = "1.0.0",
            components = listOf(start, end),
            connections = emptyList()
        )
        
        val startComponent = flow.getStartComponent()
        assertNotNull(startComponent)
        assertEquals("start-1", startComponent.id)
    }
    
    @Test
    fun getComponentByIdFindsComponent() {
        val start = StartComponent(id = "start-1", name = "Start")
        val action = ActionComponent(
            id = "action-1",
            name = "Action",
            properties = mapOf(
                ActionComponent.PROPERTY_SERVICE to ComponentProperty.StringValue("storage"),
                ActionComponent.PROPERTY_METHOD to ComponentProperty.StringValue("get")
            )
        )
        val end = EndComponent(id = "end-1", name = "End")
        
        val flow = Flow(
            id = "flow-1",
            name = "Test Flow",
            version = "1.0.0",
            components = listOf(start, action, end),
            connections = emptyList()
        )
        
        val foundAction = flow.getComponentById("action-1")
        assertNotNull(foundAction)
        assertTrue(foundAction is ActionComponent)
        assertEquals("action-1", foundAction.id)
    }
    
    @Test
    fun getOutgoingConnectionsReturnsCorrectConnections() {
        val start = StartComponent(id = "start-1", name = "Start")
        val action = ActionComponent(
            id = "action-1",
            name = "Action",
            properties = mapOf(
                ActionComponent.PROPERTY_SERVICE to ComponentProperty.StringValue("storage"),
                ActionComponent.PROPERTY_METHOD to ComponentProperty.StringValue("get")
            )
        )
        val end = EndComponent(id = "end-1", name = "End")
        
        val conn1 = Connection(
            id = "conn-1",
            sourceComponentId = "start-1",
            sourcePortId = "out",
            targetComponentId = "action-1",
            targetPortId = "in"
        )
        val conn2 = Connection(
            id = "conn-2",
            sourceComponentId = "action-1",
            sourcePortId = "success",
            targetComponentId = "end-1",
            targetPortId = "in"
        )
        
        val flow = Flow(
            id = "flow-1",
            name = "Test Flow",
            version = "1.0.0",
            components = listOf(start, action, end),
            connections = listOf(conn1, conn2)
        )
        
        val outgoing = flow.getOutgoingConnections("start-1", "out")
        assertEquals(1, outgoing.size)
        assertEquals("action-1", outgoing[0].targetComponentId)
    }
}
