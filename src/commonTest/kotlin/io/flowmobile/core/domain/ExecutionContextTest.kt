package io.flowmobile.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExecutionContextTest {
    
    @Test
    fun createsExecutionContextWithRequiredFields() {
        val context = ExecutionContext(
            flowId = "flow-1",
            executionId = "exec-1"
        )
        
        assertEquals("flow-1", context.flowId)
        assertEquals("exec-1", context.executionId)
        assertNull(context.currentComponentId)
        assertTrue(context.variables.isEmpty())
        assertEquals(ExecutionStatus.NOT_STARTED, context.status)
    }
    
    @Test
    fun withVariableCreatesNewContextWithVariable() {
        val context = ExecutionContext(
            flowId = "flow-1",
            executionId = "exec-1"
        )
        
        val newContext = context.withVariable("userId", VariableValue.StringValue("123"))
        
        // Original context unchanged
        assertTrue(context.variables.isEmpty())
        
        // New context has variable
        assertEquals(1, newContext.variables.size)
        val userIdValue = newContext.getVariable("userId") as? VariableValue.StringValue
        assertNotNull(userIdValue)
        assertEquals("123", userIdValue.value)
    }
    
    @Test
    fun withVariablesAddsMultipleVariables() {
        val context = ExecutionContext(
            flowId = "flow-1",
            executionId = "exec-1"
        )
        
        val newContext = context.withVariables(
            mapOf(
                "name" to VariableValue.StringValue("John"),
                "age" to VariableValue.NumberValue(30.0),
                "active" to VariableValue.BooleanValue(true)
            )
        )
        
        assertEquals(3, newContext.variables.size)
        assertNotNull(newContext.getVariable("name"))
        assertNotNull(newContext.getVariable("age"))
        assertNotNull(newContext.getVariable("active"))
    }
    
    @Test
    fun withCurrentComponentUpdatesCurrentComponent() {
        val context = ExecutionContext(
            flowId = "flow-1",
            executionId = "exec-1"
        )
        
        val newContext = context.withCurrentComponent("comp-1")
        
        assertNull(context.currentComponentId)
        assertEquals("comp-1", newContext.currentComponentId)
    }
    
    @Test
    fun pushStackFrameAddsFrameToStack() {
        val context = ExecutionContext(
            flowId = "flow-1",
            executionId = "exec-1"
        )
        
        val frame = StackFrame(
            componentId = "comp-1",
            componentType = ComponentType.ACTION,
            enteredAt = 1000L
        )
        
        val newContext = context.pushStackFrame(frame)
        
        assertEquals(0, context.executionStack.size)
        assertEquals(1, newContext.executionStack.size)
        assertEquals("comp-1", newContext.executionStack[0].componentId)
    }
    
    @Test
    fun popStackFrameRemovesFrameFromStack() {
        val frame = StackFrame(
            componentId = "comp-1",
            componentType = ComponentType.ACTION,
            enteredAt = 1000L
        )
        
        val context = ExecutionContext(
            flowId = "flow-1",
            executionId = "exec-1",
            executionStack = listOf(frame)
        )
        
        val newContext = context.popStackFrame()
        
        assertEquals(1, context.executionStack.size)
        assertEquals(0, newContext.executionStack.size)
    }
    
    @Test
    fun appendAuditEntryAddsEntryToAuditTrail() {
        val context = ExecutionContext(
            flowId = "flow-1",
            executionId = "exec-1"
        )
        
        val entry = AuditEntry(
            timestamp = 1000L,
            componentId = "comp-1",
            action = AuditAction.COMPONENT_ENTER,
            contextSnapshot = emptyMap()
        )
        
        val newContext = context.appendAuditEntry(entry)
        
        assertEquals(0, context.auditTrail.size)
        assertEquals(1, newContext.auditTrail.size)
        assertEquals(AuditAction.COMPONENT_ENTER, newContext.auditTrail[0].action)
    }
    
    @Test
    fun withStatusUpdatesExecutionStatus() {
        val context = ExecutionContext(
            flowId = "flow-1",
            executionId = "exec-1"
        )
        
        val newContext = context.withStatus(ExecutionStatus.RUNNING)
        
        assertEquals(ExecutionStatus.NOT_STARTED, context.status)
        assertEquals(ExecutionStatus.RUNNING, newContext.status)
    }
    
    @Test
    fun immutabilityOriginalContextNeverChanges() {
        val context = ExecutionContext(
            flowId = "flow-1",
            executionId = "exec-1"
        )
        
        // Perform multiple operations
        context
            .withVariable("var1", VariableValue.StringValue("value1"))
            .withCurrentComponent("comp-1")
            .withStatus(ExecutionStatus.RUNNING)
        
        // Original context should be unchanged
        assertTrue(context.variables.isEmpty())
        assertNull(context.currentComponentId)
        assertEquals(ExecutionStatus.NOT_STARTED, context.status)
    }
}
