package io.flowmobile.core.runtime

import io.flowmobile.core.domain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Testes de integração para execução completa de fluxos.
 */
class FlowExecutorIntegrationTest {
    
    /**
     * Cria um HostServiceRegistry com serviço MathService para testes.
     */
    private fun createMockRegistry(): HostServiceRegistry {
        val registry = HostServiceRegistry()
        registry.register("MathService", object : HostService {
            override val name = "MathService"
            
            override suspend fun execute(
                method: String,
                parameters: Map<String, VariableValue>
            ): ServiceResult {
                return when (method) {
                    "add" -> {
                        val a = (parameters["a"] as? VariableValue.NumberValue)?.value ?: 0.0
                        val b = (parameters["b"] as? VariableValue.NumberValue)?.value ?: 0.0
                        ServiceResult.success(VariableValue.NumberValue(a + b))
                    }
                    else -> ServiceResult.failure("Método desconhecido: $method")
                }
            }
        })
        return registry
    }
    
    @Test
    fun testSimpleFlowExecution() = runTest {
        // Criar um fluxo simples: Start -> End
        val start = StartComponent(
            id = "start",
            name = "Start",
            metadata = ComponentMetadata()
        )
        
        val end = EndComponent(
            id = "end",
            name = "End",
            metadata = ComponentMetadata()
        )
        
        val connection = Connection(
            id = "conn-1",
            sourceComponentId = "start",
            sourcePortId = StartComponent.PORT_OUTPUT,
            targetComponentId = "end",
            targetPortId = EndComponent.PORT_INPUT,
            metadata = ConnectionMetadata()
        )
        
        val flow = Flow(
            id = "test-flow",
            name = "Test Flow",
            version = "1.0.0",
            components = listOf(start, end),
            connections = listOf(connection),
            metadata = FlowMetadata()
        )
        
        // Executar o fluxo
        val executor = FlowExecutor(createMockRegistry())
        val result = executor.execute(flow)
        
        // Verificar resultado
        assertTrue(result.isSuccess(), "Fluxo deveria executar com sucesso")
        assertEquals(ResultStatus.SUCCESS, result.status)
    }
    
    @Test
    fun testFlowWithAction() = runTest {
        // Criar fluxo: Start -> Action -> End
        val start = StartComponent(
            id = "start",
            name = "Start",
            metadata = ComponentMetadata()
        )
        
        val action = ActionComponent(
            id = "action",
            name = "Add Numbers",
            metadata = ComponentMetadata(),
            properties = mapOf(
                ActionComponent.PROPERTY_SERVICE to ComponentProperty.StringValue("MathService"),
                ActionComponent.PROPERTY_METHOD to ComponentProperty.StringValue("add")
            )
        )
        
        val end = EndComponent(
            id = "end",
            name = "End",
            metadata = ComponentMetadata()
        )
        
        val connections = listOf(
            Connection(
                id = "conn-1",
                sourceComponentId = "start",
                sourcePortId = StartComponent.PORT_OUTPUT,
                targetComponentId = "action",
                targetPortId = "in",
                metadata = ConnectionMetadata()
            ),
            Connection(
                id = "conn-2",
                sourceComponentId = "action",
                sourcePortId = "success",
                targetComponentId = "end",
                targetPortId = EndComponent.PORT_INPUT,
                metadata = ConnectionMetadata()
            )
        )
        
        val flow = Flow(
            id = "test-flow-action",
            name = "Test Flow with Action",
            version = "1.0.0",
            components = listOf(start, action, end),
            connections = connections,
            metadata = FlowMetadata()
        )
        
        // Criar contexto inicial com variáveis
        val initialContext = ExecutionContext.create("test-flow-action", "start")
            .withVariable("a", VariableValue.NumberValue(5.0))
            .withVariable("b", VariableValue.NumberValue(3.0))
        
        // Executar o fluxo
        val executor = FlowExecutor(createMockRegistry())
        val result = executor.execute(flow, initialContext)
        
        // Verificar resultado
        assertTrue(result.isSuccess(), "Fluxo com action deveria executar com sucesso")
    }
    
    @Test
    fun testFlowWithDecision() = runTest {
        // Criar fluxo: Start -> Decision -> End (true) ou End (false)
        val start = StartComponent(
            id = "start",
            name = "Start",
            metadata = ComponentMetadata()
        )
        
        val decision = DecisionComponent(
            id = "decision",
            name = "Check Condition",
            metadata = ComponentMetadata(),
            properties = mapOf(
                DecisionComponent.PROPERTY_CONDITION to ComponentProperty.StringValue("isActive")
            )
        )
        
        val endTrue = EndComponent(
            id = "end-true",
            name = "End True",
            metadata = ComponentMetadata()
        )
        
        val endFalse = EndComponent(
            id = "end-false",
            name = "End False",
            metadata = ComponentMetadata()
        )
        
        val connections = listOf(
            Connection(
                id = "conn-1",
                sourceComponentId = "start",
                sourcePortId = StartComponent.PORT_OUTPUT,
                targetComponentId = "decision",
                targetPortId = DecisionComponent.PORT_INPUT,
                metadata = ConnectionMetadata()
            ),
            Connection(
                id = "conn-2",
                sourceComponentId = "decision",
                sourcePortId = DecisionComponent.PORT_TRUE,
                targetComponentId = "end-true",
                targetPortId = EndComponent.PORT_INPUT,
                metadata = ConnectionMetadata()
            ),
            Connection(
                id = "conn-3",
                sourceComponentId = "decision",
                sourcePortId = DecisionComponent.PORT_FALSE,
                targetComponentId = "end-false",
                targetPortId = EndComponent.PORT_INPUT,
                metadata = ConnectionMetadata()
            )
        )
        
        val flow = Flow(
            id = "test-flow-decision",
            name = "Test Flow with Decision",
            version = "1.0.0",
            components = listOf(start, decision, endTrue, endFalse),
            connections = connections,
            metadata = FlowMetadata()
        )
        
        // Teste 1: Condição verdadeira
        val contextTrue = ExecutionContext.create("test-flow-decision", "start")
            .withVariable("isActive", VariableValue.BooleanValue(true))
        
        val executor = FlowExecutor(createMockRegistry())
        val resultTrue = executor.execute(flow, contextTrue)
        
        assertTrue(resultTrue.isSuccess(), "Fluxo com decisão (true) deveria executar com sucesso")
        
        // Teste 2: Condição falsa
        val contextFalse = ExecutionContext.create("test-flow-decision", "start")
            .withVariable("isActive", VariableValue.BooleanValue(false))
        
        val resultFalse = executor.execute(flow, contextFalse)
        
        assertTrue(resultFalse.isSuccess(), "Fluxo com decisão (false) deveria executar com sucesso")
    }
    
    @Test
    fun testStepByStepExecution() = runTest {
        // Criar fluxo simples para testar step-by-step
        val start = StartComponent(
            id = "start",
            name = "Start",
            metadata = ComponentMetadata()
        )
        
        val end = EndComponent(
            id = "end",
            name = "End",
            metadata = ComponentMetadata()
        )
        
        val connection = Connection(
            id = "conn-1",
            sourceComponentId = "start",
            sourcePortId = StartComponent.PORT_OUTPUT,
            targetComponentId = "end",
            targetPortId = EndComponent.PORT_INPUT,
            metadata = ConnectionMetadata()
        )
        
        val flow = Flow(
            id = "test-flow-step",
            name = "Test Flow Step",
            version = "1.0.0",
            components = listOf(start, end),
            connections = listOf(connection),
            metadata = FlowMetadata()
        )
        
        // Executar step-by-step
        val executor = FlowExecutor(createMockRegistry())
        var context = ExecutionContext.create("test-flow-step", "start")
        
        // Passo 1: Executar start
        var stepResult = executor.step(flow, context)
        context = stepResult.context
        assertEquals("end", context.currentComponentId, "Deveria estar no componente end")
        assertEquals(false, stepResult.isComplete, "Não deveria estar completo após primeiro passo")
        
        // Passo 2: Executar end
        stepResult = executor.step(flow, context)
        context = stepResult.context
        assertEquals(true, stepResult.isComplete, "Deveria estar completo após segundo passo")
        
        // Verificar audit trail
        val completedComponents = context.auditTrail.count { 
            it.action == AuditAction.COMPONENT_COMPLETED 
        }
        assertTrue(completedComponents >= 2, "Deveria ter pelo menos 2 componentes completados no audit trail")
    }
    
    @Test
    fun testRollback() = runTest {
        // Criar fluxo com múltiplos componentes
        val start = StartComponent(
            id = "start",
            name = "Start",
            metadata = ComponentMetadata()
        )
        
        val action1 = ActionComponent(
            id = "action1",
            name = "Action 1",
            metadata = ComponentMetadata(),
            properties = mapOf(
                ActionComponent.PROPERTY_SERVICE to ComponentProperty.StringValue("MathService"),
                ActionComponent.PROPERTY_METHOD to ComponentProperty.StringValue("add")
            )
        )
        
        val action2 = ActionComponent(
            id = "action2",
            name = "Action 2",
            metadata = ComponentMetadata(),
            properties = mapOf(
                ActionComponent.PROPERTY_SERVICE to ComponentProperty.StringValue("MathService"),
                ActionComponent.PROPERTY_METHOD to ComponentProperty.StringValue("add")
            )
        )
        
        val end = EndComponent(
            id = "end",
            name = "End",
            metadata = ComponentMetadata()
        )
        
        val connections = listOf(
            Connection("conn-1", "start", StartComponent.PORT_OUTPUT, "action1", "in", ConnectionMetadata()),
            Connection("conn-2", "action1", "success", "action2", "in", ConnectionMetadata()),
            Connection("conn-3", "action2", "success", "end", EndComponent.PORT_INPUT, ConnectionMetadata())
        )
        
        val flow = Flow(
            id = "test-flow-rollback",
            name = "Test Rollback",
            version = "1.0.0",
            components = listOf(start, action1, action2, end),
            connections = connections,
            metadata = FlowMetadata()
        )
        
        // Executar até o fim
        val initialContext = ExecutionContext.create("test-flow-rollback", "start")
            .withVariable("a", VariableValue.NumberValue(10.0))
            .withVariable("b", VariableValue.NumberValue(5.0))
        
        val executor = FlowExecutor(createMockRegistry())
        val result = executor.execute(flow, initialContext)
        
        assertTrue(result.isSuccess(), "Fluxo deveria executar com sucesso")
        
        // Extrair contexto final do audit trail
        val completedSteps = result.metrics.componentsExecuted
        assertTrue(completedSteps >= 2, "Deveria ter executado pelo menos 2 componentes")
    }
    
    @Test
    fun testAuditTrailCompleteness() = runTest {
        // Verificar que o audit trail registra todas as operações
        val start = StartComponent(
            id = "start",
            name = "Start",
            metadata = ComponentMetadata()
        )
        
        val end = EndComponent(
            id = "end",
            name = "End",
            metadata = ComponentMetadata()
        )
        
        val connection = Connection(
            id = "conn-1",
            sourceComponentId = "start",
            sourcePortId = StartComponent.PORT_OUTPUT,
            targetComponentId = "end",
            targetPortId = EndComponent.PORT_INPUT,
            metadata = ConnectionMetadata()
        )
        
        val flow = Flow(
            id = "test-audit",
            name = "Test Audit",
            version = "1.0.0",
            components = listOf(start, end),
            connections = listOf(connection),
            metadata = FlowMetadata()
        )
        
        val executor = FlowExecutor(createMockRegistry())
        var context = ExecutionContext.create("test-audit", "start")
        
        // Executar passo a passo para capturar contexto
        var stepResult = executor.step(flow, context)
        context = stepResult.context
        
        stepResult = executor.step(flow, context)
        context = stepResult.context
        
        // Verificar audit trail
        assertTrue(context.auditTrail.isNotEmpty(), "Audit trail não deveria estar vazio")
        
        val startedActions = context.auditTrail.count { 
            it.action == AuditAction.COMPONENT_STARTED 
        }
        assertTrue(startedActions >= 2, "Deveria ter pelo menos 2 COMPONENT_STARTED")
        
        val completedActions = context.auditTrail.count { 
            it.action == AuditAction.COMPONENT_COMPLETED 
        }
        assertTrue(completedActions >= 2, "Deveria ter pelo menos 2 COMPONENT_COMPLETED")
    }
}
