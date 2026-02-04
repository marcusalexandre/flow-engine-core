package io.flowmobile.core.loading

import io.flowmobile.core.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes para o FlowLoader.
 * Verifica o carregamento completo de JSON para modelo de domínio.
 */
class FlowLoaderTest {
    
    private val loader = FlowLoader()
    
    // ==================== Carregamento Bem-sucedido ====================
    
    @Test
    fun testLoadValidSimpleFlow() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-1",
                "name": "Fluxo Simples",
                "version": "1.0.0",
                "components": [
                    { "id": "start-1", "type": "START", "name": "Início" },
                    { "id": "end-1", "type": "END", "name": "Fim" }
                ],
                "connections": [
                    {
                        "id": "conn-1",
                        "source": { "componentId": "start-1", "portId": "out" },
                        "target": { "componentId": "end-1", "portId": "in" }
                    }
                ]
            }
        }
        """.trimIndent()
        
        val result = loader.load(json)
        
        assertTrue(result.isSuccess())
        
        val flow = result.getOrThrow()
        assertEquals("flow-1", flow.id)
        assertEquals("Fluxo Simples", flow.name)
        assertEquals(2, flow.components.size)
        assertEquals(1, flow.connections.size)
        
        // Verificar tipos de componentes
        assertTrue(flow.components[0] is StartComponent)
        assertTrue(flow.components[1] is EndComponent)
    }
    
    @Test
    fun testLoadFlowWithAction() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-2",
                "name": "Fluxo com Ação",
                "version": "1.0.0",
                "components": [
                    { "id": "start-1", "type": "START", "name": "Início" },
                    {
                        "id": "action-1",
                        "type": "ACTION",
                        "name": "Executar Serviço",
                        "properties": {
                            "service": "storage",
                            "method": "get"
                        }
                    },
                    { "id": "end-1", "type": "END", "name": "Fim" }
                ],
                "connections": [
                    {
                        "id": "conn-1",
                        "source": { "componentId": "start-1", "portId": "out" },
                        "target": { "componentId": "action-1", "portId": "in" }
                    },
                    {
                        "id": "conn-2",
                        "source": { "componentId": "action-1", "portId": "success" },
                        "target": { "componentId": "end-1", "portId": "in" }
                    }
                ]
            }
        }
        """.trimIndent()
        
        val result = loader.load(json)
        
        assertTrue(result.isSuccess())
        
        val flow = result.getOrThrow()
        val actionComponent = flow.components.find { it is ActionComponent }
        assertNotNull(actionComponent)
        
        val action = actionComponent as ActionComponent
        assertEquals("storage", (action.properties["service"] as ComponentProperty.StringValue).value)
        assertEquals("get", (action.properties["method"] as ComponentProperty.StringValue).value)
    }
    
    @Test
    fun testLoadFlowWithDecision() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-3",
                "name": "Fluxo com Decisão",
                "version": "1.0.0",
                "components": [
                    { "id": "start-1", "type": "START", "name": "Início" },
                    {
                        "id": "decision-1",
                        "type": "DECISION",
                        "name": "Verificar Condição",
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
                        "source": { "componentId": "start-1", "portId": "out" },
                        "target": { "componentId": "decision-1", "portId": "in" }
                    },
                    {
                        "id": "conn-2",
                        "source": { "componentId": "decision-1", "portId": "true" },
                        "target": { "componentId": "end-true", "portId": "in" }
                    },
                    {
                        "id": "conn-3",
                        "source": { "componentId": "decision-1", "portId": "false" },
                        "target": { "componentId": "end-false", "portId": "in" }
                    }
                ]
            }
        }
        """.trimIndent()
        
        val result = loader.load(json)
        
        assertTrue(result.isSuccess())
        
        val flow = result.getOrThrow()
        val decisionComponent = flow.components.find { it is DecisionComponent }
        assertNotNull(decisionComponent)
        
        val decision = decisionComponent as DecisionComponent
        assertEquals("isActive", (decision.properties["condition"] as ComponentProperty.StringValue).value)
    }
    
    // ==================== Erros de Carregamento ====================
    
    @Test
    fun testLoadInvalidJson() {
        val json = "{ invalid json }"
        
        val result = loader.load(json)
        
        assertTrue(result.isFailure())
        val errors = result.errorsOrEmpty()
        assertTrue(errors.any { it.code == "INVALID_JSON" })
    }
    
    @Test
    fun testLoadUnsupportedSchemaVersion() {
        val json = """
        {
            "schemaVersion": "99.0.0",
            "flow": {
                "id": "flow-1",
                "name": "Test",
                "version": "1.0.0",
                "components": [
                    { "id": "start-1", "type": "START", "name": "Início" },
                    { "id": "end-1", "type": "END", "name": "Fim" }
                ],
                "connections": []
            }
        }
        """.trimIndent()
        
        val result = loader.load(json)
        
        assertTrue(result.isFailure())
        val errors = result.errorsOrEmpty()
        assertTrue(errors.any { it.code == "UNSUPPORTED_SCHEMA_VERSION" })
    }
    
    @Test
    fun testLoadUnknownComponentType() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-1",
                "name": "Test",
                "version": "1.0.0",
                "components": [
                    { "id": "start-1", "type": "START", "name": "Início" },
                    { "id": "unknown-1", "type": "UNKNOWN_TYPE", "name": "Desconhecido" },
                    { "id": "end-1", "type": "END", "name": "Fim" }
                ],
                "connections": []
            }
        }
        """.trimIndent()
        
        val result = loader.load(json)
        
        assertTrue(result.isFailure())
        val errors = result.errorsOrEmpty()
        assertTrue(errors.any { it.code == "UNKNOWN_COMPONENT_TYPE" })
    }
    
    @Test
    fun testLoadMissingRequiredProperties() {
        // ActionComponent sem service/method
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-1",
                "name": "Test",
                "version": "1.0.0",
                "components": [
                    { "id": "start-1", "type": "START", "name": "Início" },
                    { "id": "action-1", "type": "ACTION", "name": "Ação" },
                    { "id": "end-1", "type": "END", "name": "Fim" }
                ],
                "connections": []
            }
        }
        """.trimIndent()
        
        val result = loader.load(json)
        
        assertTrue(result.isFailure())
        val errors = result.errorsOrEmpty()
        assertTrue(errors.any { it.message.contains("service") })
    }
    
    // ==================== Validação ====================
    
    @Test
    fun testValidateValidJson() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-1",
                "name": "Test",
                "version": "1.0.0",
                "components": [
                    { "id": "start-1", "type": "START", "name": "Início" },
                    { "id": "end-1", "type": "END", "name": "Fim" }
                ],
                "connections": [
                    {
                        "id": "conn-1",
                        "source": { "componentId": "start-1", "portId": "out" },
                        "target": { "componentId": "end-1", "portId": "in" }
                    }
                ]
            }
        }
        """.trimIndent()
        
        val result = loader.validate(json)
        
        assertTrue(result.isValid)
    }
    
    @Test
    fun testValidateInvalidJson() {
        val json = "invalid"
        
        val result = loader.validate(json)
        
        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }
    
    // ==================== Schema Version ====================
    
    @Test
    fun testGetSchemaVersion() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-1",
                "name": "Test",
                "version": "1.0.0",
                "components": [],
                "connections": []
            }
        }
        """.trimIndent()
        
        val version = loader.getSchemaVersion(json)
        
        assertEquals("1.0.0", version)
    }
    
    @Test
    fun testGetSchemaVersionInvalidJson() {
        val json = "invalid"
        
        val version = loader.getSchemaVersion(json)
        
        assertNull(version)
    }
    
    // ==================== Metadados ====================
    
    @Test
    fun testLoadFlowWithMetadata() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-1",
                "name": "Fluxo com Metadados",
                "version": "1.0.0",
                "components": [
                    {
                        "id": "start-1",
                        "type": "START",
                        "name": "Início",
                        "position": { "x": 100, "y": 50 },
                        "metadata": {
                            "description": "Componente inicial"
                        }
                    },
                    { "id": "end-1", "type": "END", "name": "Fim" }
                ],
                "connections": [
                    {
                        "id": "conn-1",
                        "source": { "componentId": "start-1", "portId": "out" },
                        "target": { "componentId": "end-1", "portId": "in" }
                    }
                ],
                "metadata": {
                    "author": "Test Author",
                    "description": "Descrição do fluxo",
                    "tags": ["teste", "exemplo"]
                }
            }
        }
        """.trimIndent()
        
        val result = loader.load(json)
        
        assertTrue(result.isSuccess())
        
        val flow = result.getOrThrow()
        assertEquals("Test Author", flow.metadata.author)
        assertEquals("Descrição do fluxo", flow.metadata.description)
        assertEquals(listOf("teste", "exemplo"), flow.metadata.tags)
        
        val startComponent = flow.components[0]
        assertNotNull(startComponent.metadata.position)
        assertEquals(100.0, startComponent.metadata.position?.x)
        assertEquals(50.0, startComponent.metadata.position?.y)
        assertEquals("Componente inicial", startComponent.metadata.description)
    }
    
    // ==================== Warnings ====================
    
    @Test
    fun testLoadWithWarnings() {
        // Fluxo válido mas com componente órfão (gera warning)
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-1",
                "name": "Test",
                "version": "1.0.0",
                "components": [
                    { "id": "start-1", "type": "START", "name": "Início" },
                    {
                        "id": "orphan-1",
                        "type": "ACTION",
                        "name": "Órfão",
                        "properties": { "service": "test", "method": "test" }
                    },
                    { "id": "end-1", "type": "END", "name": "Fim" }
                ],
                "connections": [
                    {
                        "id": "conn-1",
                        "source": { "componentId": "start-1", "portId": "out" },
                        "target": { "componentId": "end-1", "portId": "in" }
                    }
                ]
            }
        }
        """.trimIndent()
        
        val result = loader.load(json)
        
        assertTrue(result.isSuccess())
        
        val success = result as LoadResult.Success
        assertTrue(success.warnings.isNotEmpty())
        assertTrue(success.warnings.any { it.code == "ORPHAN_COMPONENT" })
    }
}
