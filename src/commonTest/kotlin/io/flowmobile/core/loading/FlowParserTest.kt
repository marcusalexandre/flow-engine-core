package io.flowmobile.core.loading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes para o FlowParser.
 * Verifica o parsing de JSON para estruturas intermediárias.
 */
class FlowParserTest {
    
    private val parser = FlowParser()
    
    // ==================== JSON Válido ====================
    
    @Test
    fun testParseValidSimpleFlow() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-1",
                "name": "Fluxo Simples",
                "version": "1.0.0",
                "components": [
                    {
                        "id": "start-1",
                        "type": "START",
                        "name": "Início"
                    },
                    {
                        "id": "end-1",
                        "type": "END",
                        "name": "Fim"
                    }
                ],
                "connections": [
                    {
                        "id": "conn-1",
                        "source": {
                            "componentId": "start-1",
                            "portId": "out"
                        },
                        "target": {
                            "componentId": "end-1",
                            "portId": "in"
                        }
                    }
                ]
            }
        }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        assertTrue(result.isSuccess())
        val document = result.getOrNull()
        assertNotNull(document)
        assertEquals("1.0.0", document.schemaVersion)
        assertEquals("flow-1", document.flow.id)
        assertEquals("Fluxo Simples", document.flow.name)
        assertEquals(2, document.flow.components.size)
        assertEquals(1, document.flow.connections.size)
    }
    
    @Test
    fun testParseFlowWithProperties() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-2",
                "name": "Fluxo com Propriedades",
                "version": "1.0.0",
                "components": [
                    {
                        "id": "start-1",
                        "type": "START",
                        "name": "Início"
                    },
                    {
                        "id": "action-1",
                        "type": "ACTION",
                        "name": "Ação",
                        "properties": {
                            "service": "storage",
                            "method": "get",
                            "key": "user_data",
                            "timeout": 5000,
                            "retry": true
                        }
                    },
                    {
                        "id": "end-1",
                        "type": "END",
                        "name": "Fim"
                    }
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
        
        val result = parser.parse(json)
        
        assertTrue(result.isSuccess())
        val document = result.getOrThrow()
        
        val actionComponent = document.flow.components.find { it.id == "action-1" }
        assertNotNull(actionComponent)
        assertEquals("ACTION", actionComponent.type)
        
        val props = actionComponent.properties
        assertTrue(props["service"] is PropertyDefinition.StringValue)
        assertEquals("storage", (props["service"] as PropertyDefinition.StringValue).value)
        assertTrue(props["timeout"] is PropertyDefinition.NumberValue)
        assertEquals(5000.0, (props["timeout"] as PropertyDefinition.NumberValue).value)
        assertTrue(props["retry"] is PropertyDefinition.BooleanValue)
        assertEquals(true, (props["retry"] as PropertyDefinition.BooleanValue).value)
    }
    
    @Test
    fun testParseFlowWithExpressions() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-3",
                "name": "Fluxo com Expressões",
                "version": "1.0.0",
                "components": [
                    {
                        "id": "start-1",
                        "type": "START",
                        "name": "Início"
                    },
                    {
                        "id": "decision-1",
                        "type": "DECISION",
                        "name": "Decisão",
                        "properties": {
                            "condition": "${'$'}{user.isActive}"
                        }
                    },
                    {
                        "id": "end-1",
                        "type": "END",
                        "name": "Fim"
                    }
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
                        "target": { "componentId": "end-1", "portId": "in" }
                    }
                ]
            }
        }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        assertTrue(result.isSuccess())
        val document = result.getOrThrow()
        
        val decisionComponent = document.flow.components.find { it.id == "decision-1" }
        assertNotNull(decisionComponent)
        
        val conditionProp = decisionComponent.properties["condition"] as? PropertyDefinition.Expression
        assertNotNull(conditionProp)
        assertEquals("\${user.isActive}", conditionProp.expression)
    }
    
    @Test
    fun testParseFlowWithMetadata() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-4",
                "name": "Fluxo com Metadados",
                "version": "1.0.0",
                "description": "Um fluxo de teste",
                "components": [
                    {
                        "id": "start-1",
                        "type": "START",
                        "name": "Início",
                        "position": { "x": 100, "y": 50 },
                        "metadata": {
                            "description": "Componente inicial",
                            "tags": ["entrada", "principal"]
                        }
                    },
                    {
                        "id": "end-1",
                        "type": "END",
                        "name": "Fim"
                    }
                ],
                "connections": [
                    {
                        "id": "conn-1",
                        "source": { "componentId": "start-1", "portId": "out" },
                        "target": { "componentId": "end-1", "portId": "in" },
                        "metadata": { "label": "Conexão principal" }
                    }
                ],
                "metadata": {
                    "author": "Test Author",
                    "tags": ["teste", "exemplo"],
                    "customData": { "env": "development" }
                }
            }
        }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        assertTrue(result.isSuccess())
        val document = result.getOrThrow()
        
        assertEquals("Um fluxo de teste", document.flow.description)
        assertEquals("Test Author", document.flow.metadata.author)
        assertEquals(listOf("teste", "exemplo"), document.flow.metadata.tags)
        
        val startComponent = document.flow.components.find { it.id == "start-1" }
        assertNotNull(startComponent)
        assertNotNull(startComponent.position)
        assertEquals(100.0, startComponent.position?.x)
        assertEquals(50.0, startComponent.position?.y)
        assertEquals("Componente inicial", startComponent.metadata.description)
        
        val connection = document.flow.connections.first()
        assertEquals("Conexão principal", connection.metadata.label)
    }
    
    // ==================== JSON Inválido ====================
    
    @Test
    fun testParseInvalidJson() {
        val json = "{ invalid json }"
        
        val result = parser.parse(json)
        
        assertTrue(result.isFailure())
        val errors = result.errorsOrEmpty()
        assertEquals(1, errors.size)
        assertEquals("INVALID_JSON", errors.first().code)
    }
    
    @Test
    fun testParseMissingSchemaVersion() {
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
        
        val result = parser.parse(json)
        
        assertTrue(result.isFailure())
        val errors = result.errorsOrEmpty()
        assertTrue(errors.any { it.code == "MISSING_SCHEMA_VERSION" })
    }
    
    @Test
    fun testParseMissingFlow() {
        val json = """
        {
            "schemaVersion": "1.0.0"
        }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        assertTrue(result.isFailure())
        val errors = result.errorsOrEmpty()
        assertTrue(errors.any { it.code == "MISSING_FLOW" })
    }
    
    @Test
    fun testParseMissingFlowFields() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "name": "Test"
            }
        }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        assertTrue(result.isFailure())
        val errors = result.errorsOrEmpty()
        assertTrue(errors.any { it.code == "MISSING_FIELD" && it.path?.contains("id") == true })
        assertTrue(errors.any { it.code == "MISSING_FIELD" && it.path?.contains("version") == true })
        assertTrue(errors.any { it.code == "MISSING_FIELD" && it.path?.contains("components") == true })
        assertTrue(errors.any { it.code == "MISSING_FIELD" && it.path?.contains("connections") == true })
    }
    
    @Test
    fun testParseMissingComponentFields() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-1",
                "name": "Test",
                "version": "1.0.0",
                "components": [
                    {
                        "id": "comp-1"
                    }
                ],
                "connections": []
            }
        }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        assertTrue(result.isFailure())
        val errors = result.errorsOrEmpty()
        assertTrue(errors.any { it.code == "MISSING_FIELD" && it.path?.contains("type") == true })
        assertTrue(errors.any { it.code == "MISSING_FIELD" && it.path?.contains("name") == true })
    }
    
    @Test
    fun testParseMissingConnectionFields() {
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
                        "source": { "componentId": "start-1" }
                    }
                ]
            }
        }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        assertTrue(result.isFailure())
        val errors = result.errorsOrEmpty()
        assertTrue(errors.any { it.code == "MISSING_FIELD" && it.path?.contains("portId") == true })
        assertTrue(errors.any { it.code == "MISSING_FIELD" && it.path?.contains("target") == true })
    }
    
    // ==================== Tipos de Propriedades ====================
    
    @Test
    fun testParseAllPropertyTypes() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-5",
                "name": "Teste de Tipos",
                "version": "1.0.0",
                "components": [
                    {
                        "id": "start-1",
                        "type": "START",
                        "name": "Início",
                        "properties": {
                            "stringProp": "hello",
                            "numberProp": 42.5,
                            "boolProp": true,
                            "nullProp": null,
                            "arrayProp": [1, 2, "three"],
                            "objectProp": {
                                "nested": "value"
                            }
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
                ]
            }
        }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        assertTrue(result.isSuccess())
        val document = result.getOrThrow()
        
        val startComponent = document.flow.components.find { it.id == "start-1" }
        assertNotNull(startComponent)
        
        val props = startComponent.properties
        
        assertTrue(props["stringProp"] is PropertyDefinition.StringValue)
        assertEquals("hello", (props["stringProp"] as PropertyDefinition.StringValue).value)
        
        assertTrue(props["numberProp"] is PropertyDefinition.NumberValue)
        assertEquals(42.5, (props["numberProp"] as PropertyDefinition.NumberValue).value)
        
        assertTrue(props["boolProp"] is PropertyDefinition.BooleanValue)
        assertEquals(true, (props["boolProp"] as PropertyDefinition.BooleanValue).value)
        
        assertTrue(props["nullProp"] is PropertyDefinition.NullValue)
        
        assertTrue(props["arrayProp"] is PropertyDefinition.ArrayValue)
        val arrayProp = props["arrayProp"] as PropertyDefinition.ArrayValue
        assertEquals(3, arrayProp.value.size)
        
        assertTrue(props["objectProp"] is PropertyDefinition.ObjectValue)
        val objectProp = props["objectProp"] as PropertyDefinition.ObjectValue
        assertTrue(objectProp.value.containsKey("nested"))
    }
    
    // ==================== Versões Suportadas ====================
    
    @Test
    fun testGetSupportedVersions() {
        val versions = parser.getSupportedVersions()
        
        assertTrue(versions.isNotEmpty())
        assertTrue(versions.contains("1.0.0"))
    }
}
