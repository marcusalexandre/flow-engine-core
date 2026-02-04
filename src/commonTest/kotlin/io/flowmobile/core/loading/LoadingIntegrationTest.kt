package io.flowmobile.core.loading

import io.flowmobile.core.domain.*
import io.flowmobile.core.runtime.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Testes End-to-End para o sistema de Loading.
 * Verifica o fluxo completo: JSON → Load → Validate → Execute → Result
 */
class LoadingIntegrationTest {
    
    private val loader = FlowLoader()
    
    // ==================== E2E: Fluxo Simples ====================
    
    @Test
    fun testE2E_SimpleFlow_LoadAndExecute() {
        // 1. JSON de entrada
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "e2e-simple-flow",
                "name": "Fluxo E2E Simples",
                "version": "1.0.0",
                "components": [
                    { "id": "start", "type": "START", "name": "Início" },
                    { "id": "end", "type": "END", "name": "Fim" }
                ],
                "connections": [
                    {
                        "id": "conn-1",
                        "source": { "componentId": "start", "portId": "out" },
                        "target": { "componentId": "end", "portId": "in" }
                    }
                ]
            }
        }
        """.trimIndent()
        
        // 2. Carregar fluxo
        val loadResult = loader.load(json)
        assertTrue(loadResult.isSuccess(), "Carregamento deve ser bem-sucedido")
        
        val flow = loadResult.getOrThrow()
        assertEquals("e2e-simple-flow", flow.id)
        assertEquals(2, flow.components.size)
        
        // 3. Validar estrutura
        val validator = FlowValidator()
        val validationResult = validator.validate(flow)
        assertTrue(validationResult.isValid, "Fluxo deve ser válido")
        
        // 4. Executar fluxo
        val executor = FlowExecutor(HostServiceRegistry())
        
        // Usar runBlocking para testes (em Kotlin Multiplatform, usar runTest)
        val executionResult = runBlockingTest {
            executor.execute(flow)
        }
        
        // 5. Verificar resultado
        assertTrue(executionResult.isSuccess(), "Execução deve ser bem-sucedida")
    }
    
    @Test
    fun testE2E_FlowWithAction_LoadAndExecute() {
        // 1. JSON com ActionComponent
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "e2e-action-flow",
                "name": "Fluxo E2E com Ação",
                "version": "1.0.0",
                "components": [
                    { "id": "start", "type": "START", "name": "Início" },
                    {
                        "id": "action",
                        "type": "ACTION",
                        "name": "Executar Teste",
                        "properties": {
                            "service": "test",
                            "method": "echo"
                        }
                    },
                    { "id": "end", "type": "END", "name": "Fim" }
                ],
                "connections": [
                    {
                        "id": "conn-1",
                        "source": { "componentId": "start", "portId": "out" },
                        "target": { "componentId": "action", "portId": "in" }
                    },
                    {
                        "id": "conn-2",
                        "source": { "componentId": "action", "portId": "success" },
                        "target": { "componentId": "end", "portId": "in" }
                    }
                ]
            }
        }
        """.trimIndent()
        
        // 2. Carregar fluxo
        val loadResult = loader.load(json)
        assertTrue(loadResult.isSuccess())
        
        val flow = loadResult.getOrThrow()
        
        // 3. Configurar serviço mock
        val registry = HostServiceRegistry()
        registry.register("test", object : HostService {
            override val name = "test"
            
            override suspend fun execute(
                method: String,
                parameters: Map<String, VariableValue>
            ): ServiceResult {
                return ServiceResult(
                    success = true,
                    result = VariableValue.StringValue("echo result")
                )
            }
        })
        
        // 4. Executar
        val executor = FlowExecutor(registry)
        val executionResult = runBlockingTest {
            executor.execute(flow)
        }
        
        // 5. Verificar
        assertTrue(executionResult.isSuccess())
    }
    
    @Test
    fun testE2E_FlowWithDecision_TrueBranch() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "e2e-decision-flow",
                "name": "Fluxo E2E com Decisão",
                "version": "1.0.0",
                "components": [
                    { "id": "start", "type": "START", "name": "Início" },
                    {
                        "id": "decision",
                        "type": "DECISION",
                        "name": "Verificar Flag",
                        "properties": {
                            "condition": "isActive"
                        }
                    },
                    { "id": "end-true", "type": "END", "name": "Fim Verdadeiro" },
                    { "id": "end-false", "type": "END", "name": "Fim Falso" }
                ],
                "connections": [
                    {
                        "id": "conn-1",
                        "source": { "componentId": "start", "portId": "out" },
                        "target": { "componentId": "decision", "portId": "in" }
                    },
                    {
                        "id": "conn-2",
                        "source": { "componentId": "decision", "portId": "true" },
                        "target": { "componentId": "end-true", "portId": "in" }
                    },
                    {
                        "id": "conn-3",
                        "source": { "componentId": "decision", "portId": "false" },
                        "target": { "componentId": "end-false", "portId": "in" }
                    }
                ]
            }
        }
        """.trimIndent()
        
        // Carregar
        val loadResult = loader.load(json)
        assertTrue(loadResult.isSuccess())
        
        val flow = loadResult.getOrThrow()
        
        // Executar com isActive = true
        val executor = FlowExecutor(HostServiceRegistry())
        val context = ExecutionContext.create(
            flowId = flow.id,
            initialComponentId = flow.getStartComponent().id
        ).withVariable("isActive", VariableValue.BooleanValue(true))
        
        val executionResult = runBlockingTest {
            executor.execute(flow, context)
        }
        
        assertTrue(executionResult.isSuccess())
        
        // Verificar que terminou no end-true
        val outputVars = executionResult.outputVariables
        assertTrue(outputVars["isActive"] is VariableValue.BooleanValue)
    }
    
    @Test
    fun testE2E_FlowWithDecision_FalseBranch() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "e2e-decision-flow-2",
                "name": "Fluxo E2E com Decisão (False)",
                "version": "1.0.0",
                "components": [
                    { "id": "start", "type": "START", "name": "Início" },
                    {
                        "id": "decision",
                        "type": "DECISION",
                        "name": "Verificar Flag",
                        "properties": {
                            "condition": "isActive"
                        }
                    },
                    { "id": "end-true", "type": "END", "name": "Fim Verdadeiro" },
                    { "id": "end-false", "type": "END", "name": "Fim Falso" }
                ],
                "connections": [
                    {
                        "id": "conn-1",
                        "source": { "componentId": "start", "portId": "out" },
                        "target": { "componentId": "decision", "portId": "in" }
                    },
                    {
                        "id": "conn-2",
                        "source": { "componentId": "decision", "portId": "true" },
                        "target": { "componentId": "end-true", "portId": "in" }
                    },
                    {
                        "id": "conn-3",
                        "source": { "componentId": "decision", "portId": "false" },
                        "target": { "componentId": "end-false", "portId": "in" }
                    }
                ]
            }
        }
        """.trimIndent()
        
        val loadResult = loader.load(json)
        assertTrue(loadResult.isSuccess())
        
        val flow = loadResult.getOrThrow()
        
        // Executar com isActive = false
        val executor = FlowExecutor(HostServiceRegistry())
        val context = ExecutionContext.create(
            flowId = flow.id,
            initialComponentId = flow.getStartComponent().id
        ).withVariable("isActive", VariableValue.BooleanValue(false))
        
        val executionResult = runBlockingTest {
            executor.execute(flow, context)
        }
        
        assertTrue(executionResult.isSuccess())
    }
    
    // ==================== E2E: Validação de Erros ====================
    
    @Test
    fun testE2E_InvalidFlow_ValidationErrors() {
        // JSON com conexão para componente inexistente
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "invalid-flow",
                "name": "Fluxo Inválido",
                "version": "1.0.0",
                "components": [
                    { "id": "start", "type": "START", "name": "Início" },
                    { "id": "end", "type": "END", "name": "Fim" }
                ],
                "connections": [
                    {
                        "id": "conn-1",
                        "source": { "componentId": "start", "portId": "out" },
                        "target": { "componentId": "non-existent", "portId": "in" }
                    }
                ]
            }
        }
        """.trimIndent()
        
        val loadResult = loader.load(json)
        
        assertTrue(loadResult.isFailure())
        
        val errors = loadResult.errorsOrEmpty()
        assertTrue(errors.any { it.code == "INVALID_TARGET_COMPONENT" })
    }
    
    @Test
    fun testE2E_MissingSchemaVersion() {
        val json = """
        {
            "flow": {
                "id": "flow-1",
                "name": "Test",
                "version": "1.0.0",
                "components": [],
                "connections": []
            }
        }
        """.trimIndent()
        
        val loadResult = loader.load(json)
        
        assertTrue(loadResult.isFailure())
        assertTrue(loadResult.errorsOrEmpty().any { it.code == "MISSING_SCHEMA_VERSION" })
    }
    
    // ==================== E2E: Fluxo Complexo ====================
    
    @Test
    fun testE2E_ComplexFlow_LoadValidateExecute() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "complex-e2e-flow",
                "name": "Fluxo E2E Complexo",
                "version": "2.0.0",
                "description": "Um fluxo complexo para testes E2E",
                "components": [
                    { "id": "start", "type": "START", "name": "Início" },
                    {
                        "id": "action-1",
                        "type": "ACTION",
                        "name": "Buscar Dados",
                        "properties": {
                            "service": "data",
                            "method": "fetch"
                        }
                    },
                    {
                        "id": "decision",
                        "type": "DECISION",
                        "name": "Dados Válidos?",
                        "properties": {
                            "condition": "dataValid"
                        }
                    },
                    {
                        "id": "action-2",
                        "type": "ACTION",
                        "name": "Processar",
                        "properties": {
                            "service": "processor",
                            "method": "process"
                        }
                    },
                    { "id": "end-success", "type": "END", "name": "Sucesso" },
                    { "id": "end-error", "type": "END", "name": "Erro" }
                ],
                "connections": [
                    {
                        "id": "c1",
                        "source": { "componentId": "start", "portId": "out" },
                        "target": { "componentId": "action-1", "portId": "in" }
                    },
                    {
                        "id": "c2",
                        "source": { "componentId": "action-1", "portId": "success" },
                        "target": { "componentId": "decision", "portId": "in" }
                    },
                    {
                        "id": "c3",
                        "source": { "componentId": "action-1", "portId": "error" },
                        "target": { "componentId": "end-error", "portId": "in" }
                    },
                    {
                        "id": "c4",
                        "source": { "componentId": "decision", "portId": "true" },
                        "target": { "componentId": "action-2", "portId": "in" }
                    },
                    {
                        "id": "c5",
                        "source": { "componentId": "decision", "portId": "false" },
                        "target": { "componentId": "end-error", "portId": "in" }
                    },
                    {
                        "id": "c6",
                        "source": { "componentId": "action-2", "portId": "success" },
                        "target": { "componentId": "end-success", "portId": "in" }
                    }
                ],
                "metadata": {
                    "author": "Test Suite",
                    "tags": ["e2e", "complex", "test"]
                }
            }
        }
        """.trimIndent()
        
        // 1. Carregar
        val loadResult = loader.load(json)
        assertTrue(loadResult.isSuccess(), "Carregamento deve ser bem-sucedido: ${loadResult.errorsOrEmpty()}")
        
        val flow = loadResult.getOrThrow()
        
        // 2. Verificar estrutura
        assertEquals("complex-e2e-flow", flow.id)
        assertEquals(6, flow.components.size)
        assertEquals(6, flow.connections.size)
        assertEquals("Test Suite", flow.metadata.author)
        
        // 3. Validar
        val validator = FlowValidator()
        val validationResult = validator.validate(flow)
        assertTrue(validationResult.isValid, "Fluxo deve ser válido: ${validationResult.errors}")
        
        // 4. Configurar serviços mock
        val registry = HostServiceRegistry()
        registry.register("data", createMockService("data"))
        registry.register("processor", createMockService("processor"))
        
        // 5. Executar
        val executor = FlowExecutor(registry)
        val context = ExecutionContext.create(
            flowId = flow.id,
            initialComponentId = flow.getStartComponent().id
        ).withVariable("dataValid", VariableValue.BooleanValue(true))
        
        val executionResult = runBlockingTest {
            executor.execute(flow, context)
        }
        
        // 6. Verificar resultado
        assertTrue(executionResult.isSuccess())
    }
    
    // ==================== Helpers ====================
    
    private fun createMockService(name: String): HostService {
        return object : HostService {
            override val name = name
            
            override suspend fun execute(
                method: String,
                parameters: Map<String, VariableValue>
            ): ServiceResult {
                return ServiceResult(
                    success = true,
                    result = VariableValue.StringValue("$name.$method executed")
                )
            }
        }
    }
    
    // Helper para executar suspend functions em testes
    private fun <T> runBlockingTest(block: suspend () -> T): T {
        var result: T? = null
        var exception: Throwable? = null
        
        kotlinx.coroutines.test.runTest {
            try {
                result = block()
            } catch (e: Throwable) {
                exception = e
            }
        }
        
        exception?.let { throw it }
        
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
