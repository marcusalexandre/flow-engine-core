package io.flowmobile.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ComponentTest {
    
    @Test
    fun startComponentHasNoInputPortsAndOneOutputPort() {
        val start = StartComponent(id = "start-1", name = "Start")
        
        assertTrue(start.getInputPorts().isEmpty())
        assertEquals(1, start.getOutputPorts().size)
        assertEquals("out", start.getOutputPorts()[0].id)
        assertEquals(PortType.CONTROL, start.getOutputPorts()[0].type)
    }
    
    @Test
    fun endComponentHasOneInputPortAndNoOutputPorts() {
        val end = EndComponent(id = "end-1", name = "End")
        
        assertEquals(1, end.getInputPorts().size)
        assertEquals("in", end.getInputPorts()[0].id)
        assertTrue(end.getOutputPorts().isEmpty())
    }
    
    @Test
    fun decisionComponentHasCorrectPorts() {
        val decision = DecisionComponent(
            id = "decision-1",
            name = "Decision",
            properties = mapOf(
                DecisionComponent.PROPERTY_CONDITION to ComponentProperty.Expression("userId != null")
            )
        )
        
        assertEquals(1, decision.getInputPorts().size)
        assertEquals(2, decision.getOutputPorts().size)
        
        val outputPorts = decision.getOutputPorts()
        assertTrue(outputPorts.any { it.id == "true" })
        assertTrue(outputPorts.any { it.id == "false" })
    }
    
    @Test
    fun decisionComponentRequiresConditionProperty() {
        assertFailsWith<IllegalArgumentException> {
            DecisionComponent(
                id = "decision-1",
                name = "Decision",
                properties = emptyMap()
            )
        }
    }
    
    @Test
    fun actionComponentRequiresServiceAndMethodProperties() {
        assertFailsWith<IllegalArgumentException> {
            ActionComponent(
                id = "action-1",
                name = "Action",
                properties = emptyMap()
            )
        }
    }
    
    @Test
    fun actionComponentWithValidPropertiesSucceeds() {
        val action = ActionComponent(
            id = "action-1",
            name = "Action",
            properties = mapOf(
                ActionComponent.PROPERTY_SERVICE to ComponentProperty.StringValue("storage"),
                ActionComponent.PROPERTY_METHOD to ComponentProperty.StringValue("get")
            )
        )
        
        assertEquals("action-1", action.id)
        assertEquals(ComponentType.ACTION, action.type)
        assertEquals(1, action.getInputPorts().size)
        assertEquals(2, action.getOutputPorts().size)
    }
    
    @Test
    fun actionComponentHasSuccessAndErrorOutputPorts() {
        val action = ActionComponent(
            id = "action-1",
            name = "Action",
            properties = mapOf(
                ActionComponent.PROPERTY_SERVICE to ComponentProperty.StringValue("storage"),
                ActionComponent.PROPERTY_METHOD to ComponentProperty.StringValue("get")
            )
        )
        
        val outputPorts = action.getOutputPorts()
        assertTrue(outputPorts.any { it.id == "success" })
        assertTrue(outputPorts.any { it.id == "error" })
    }
    
    @Test
    fun componentCannotHaveBlankId() {
        assertFailsWith<IllegalArgumentException> {
            StartComponent(id = "", name = "Start")
        }
    }
    
    @Test
    fun componentCannotHaveBlankName() {
        assertFailsWith<IllegalArgumentException> {
            StartComponent(id = "start-1", name = "")
        }
    }
}
