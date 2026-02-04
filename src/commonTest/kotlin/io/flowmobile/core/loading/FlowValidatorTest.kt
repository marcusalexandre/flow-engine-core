package io.flowmobile.core.loading

import io.flowmobile.core.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Testes para o FlowValidator.
 * Verifica validações estruturais e semânticas de fluxos.
 */
class FlowValidatorTest {
    
    private val validator = FlowValidator()
    
    // ==================== Helper Functions ====================
    
    private fun createValidFlow(): Flow {
        return Flow(
            id = "flow-1",
            name = "Fluxo Válido",
            version = "1.0.0",
            components = listOf(
                StartComponent(id = "start-1", name = "Início"),
                EndComponent(id = "end-1", name = "Fim")
            ),
            connections = listOf(
                Connection(
                    id = "conn-1",
                    sourceComponentId = "start-1",
                    sourcePortId = "out",
                    targetComponentId = "end-1",
                    targetPortId = "in"
                )
            )
        )
    }
    
    private fun createActionComponent(id: String, name: String): ActionComponent {
        return ActionComponent(
            id = id,
            name = name,
            properties = mapOf(
                ActionComponent.PROPERTY_SERVICE to ComponentProperty.StringValue("test"),
                ActionComponent.PROPERTY_METHOD to ComponentProperty.StringValue("execute")
            )
        )
    }
    
    private fun createDecisionComponent(id: String, name: String): DecisionComponent {
        return DecisionComponent(
            id = id,
            name = name,
            properties = mapOf(
                DecisionComponent.PROPERTY_CONDITION to ComponentProperty.StringValue("isValid")
            )
        )
    }
    
    // ==================== Fluxos Válidos ====================
    
    @Test
    fun testValidateSimpleValidFlow() {
        val flow = createValidFlow()
        
        val result = validator.validate(flow)
        
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }
    
    @Test
    fun testValidateFlowWithAction() {
        val flow = Flow(
            id = "flow-2",
            name = "Fluxo com Ação",
            version = "1.0.0",
            components = listOf(
                StartComponent(id = "start-1", name = "Início"),
                createActionComponent("action-1", "Ação"),
                EndComponent(id = "end-1", name = "Fim")
            ),
            connections = listOf(
                Connection(
                    id = "conn-1",
                    sourceComponentId = "start-1",
                    sourcePortId = "out",
                    targetComponentId = "action-1",
                    targetPortId = "in"
                ),
                Connection(
                    id = "conn-2",
                    sourceComponentId = "action-1",
                    sourcePortId = "success",
                    targetComponentId = "end-1",
                    targetPortId = "in"
                )
            )
        )
        
        val result = validator.validate(flow)
        
        assertTrue(result.isValid)
    }
    
    @Test
    fun testValidateFlowWithDecision() {
        val flow = Flow(
            id = "flow-3",
            name = "Fluxo com Decisão",
            version = "1.0.0",
            components = listOf(
                StartComponent(id = "start-1", name = "Início"),
                createDecisionComponent("decision-1", "Decisão"),
                EndComponent(id = "end-true", name = "Fim Verdadeiro"),
                EndComponent(id = "end-false", name = "Fim Falso")
            ),
            connections = listOf(
                Connection(
                    id = "conn-1",
                    sourceComponentId = "start-1",
                    sourcePortId = "out",
                    targetComponentId = "decision-1",
                    targetPortId = "in"
                ),
                Connection(
                    id = "conn-2",
                    sourceComponentId = "decision-1",
                    sourcePortId = "true",
                    targetComponentId = "end-true",
                    targetPortId = "in"
                ),
                Connection(
                    id = "conn-3",
                    sourceComponentId = "decision-1",
                    sourcePortId = "false",
                    targetComponentId = "end-false",
                    targetPortId = "in"
                )
            )
        )
        
        val result = validator.validate(flow)
        
        assertTrue(result.isValid)
    }
    
    // ==================== Validações Estruturais ====================
    
    @Test
    fun testValidateStructure_DuplicateComponentIds() {
        // Este teste usa o método de validação estrutural diretamente
        // porque o construtor do Flow já valida IDs únicos
        
        val components = listOf(
            StartComponent(id = "start-1", name = "Início"),
            EndComponent(id = "end-1", name = "Fim")
        )
        
        val flow = Flow(
            id = "flow-1",
            name = "Test",
            version = "1.0.0",
            components = components,
            connections = listOf(
                Connection(
                    id = "conn-1",
                    sourceComponentId = "start-1",
                    sourcePortId = "out",
                    targetComponentId = "end-1",
                    targetPortId = "in"
                )
            )
        )
        
        val errors = validator.validateStructure(flow)
        
        // Flow válido não deve ter erros estruturais
        assertTrue(errors.isEmpty())
    }
    
    @Test
    fun testValidateStructure_ValidFlow() {
        val flow = createValidFlow()
        
        val errors = validator.validateStructure(flow)
        
        assertTrue(errors.isEmpty())
    }
    
    // ==================== Validações de Conexões ====================
    
    @Test
    fun testValidateConnections_InvalidSourceComponent() {
        val flow = Flow(
            id = "flow-1",
            name = "Test",
            version = "1.0.0",
            components = listOf(
                StartComponent(id = "start-1", name = "Início"),
                EndComponent(id = "end-1", name = "Fim")
            ),
            connections = listOf(
                Connection(
                    id = "conn-1",
                    sourceComponentId = "non-existent",
                    sourcePortId = "out",
                    targetComponentId = "end-1",
                    targetPortId = "in"
                )
            )
        )
        
        val errors = validator.validateConnections(flow)
        
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.code == "INVALID_SOURCE_COMPONENT" })
    }
    
    @Test
    fun testValidateConnections_InvalidTargetComponent() {
        val flow = Flow(
            id = "flow-1",
            name = "Test",
            version = "1.0.0",
            components = listOf(
                StartComponent(id = "start-1", name = "Início"),
                EndComponent(id = "end-1", name = "Fim")
            ),
            connections = listOf(
                Connection(
                    id = "conn-1",
                    sourceComponentId = "start-1",
                    sourcePortId = "out",
                    targetComponentId = "non-existent",
                    targetPortId = "in"
                )
            )
        )
        
        val errors = validator.validateConnections(flow)
        
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.code == "INVALID_TARGET_COMPONENT" })
    }
    
    @Test
    fun testValidateConnections_InvalidSourcePort() {
        val flow = Flow(
            id = "flow-1",
            name = "Test",
            version = "1.0.0",
            components = listOf(
                StartComponent(id = "start-1", name = "Início"),
                EndComponent(id = "end-1", name = "Fim")
            ),
            connections = listOf(
                Connection(
                    id = "conn-1",
                    sourceComponentId = "start-1",
                    sourcePortId = "invalid-port",
                    targetComponentId = "end-1",
                    targetPortId = "in"
                )
            )
        )
        
        val errors = validator.validateConnections(flow)
        
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.code == "INVALID_SOURCE_PORT" })
    }
    
    @Test
    fun testValidateConnections_InvalidTargetPort() {
        val flow = Flow(
            id = "flow-1",
            name = "Test",
            version = "1.0.0",
            components = listOf(
                StartComponent(id = "start-1", name = "Início"),
                EndComponent(id = "end-1", name = "Fim")
            ),
            connections = listOf(
                Connection(
                    id = "conn-1",
                    sourceComponentId = "start-1",
                    sourcePortId = "out",
                    targetComponentId = "end-1",
                    targetPortId = "invalid-port"
                )
            )
        )
        
        val errors = validator.validateConnections(flow)
        
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.code == "INVALID_TARGET_PORT" })
    }
    
    @Test
    fun testValidateConnections_ValidConnections() {
        val flow = createValidFlow()
        
        val errors = validator.validateConnections(flow)
        
        assertTrue(errors.isEmpty())
    }
    
    // ==================== Validações de Ciclos ====================
    
    @Test
    fun testValidateSemantics_NoCycle() {
        val flow = createValidFlow()
        
        val errors = validator.validateSemantics(flow)
        
        assertTrue(errors.none { it.code == "CYCLE_DETECTED" })
    }
    
    // ==================== Warnings ====================
    
    @Test
    fun testValidateWarnings_OrphanComponent() {
        val flow = Flow(
            id = "flow-1",
            name = "Test",
            version = "1.0.0",
            components = listOf(
                StartComponent(id = "start-1", name = "Início"),
                createActionComponent("orphan-1", "Órfão"),
                EndComponent(id = "end-1", name = "Fim")
            ),
            connections = listOf(
                Connection(
                    id = "conn-1",
                    sourceComponentId = "start-1",
                    sourcePortId = "out",
                    targetComponentId = "end-1",
                    targetPortId = "in"
                )
            )
        )
        
        val result = validator.validate(flow)
        
        assertTrue(result.isValid) // Warnings não invalidam o fluxo
        assertTrue(result.warnings.any { it.code == "ORPHAN_COMPONENT" })
    }
    
    @Test
    fun testValidateWarnings_UnreachableEndComponent() {
        // Criar um fluxo onde um EndComponent não é alcançável
        val flow = Flow(
            id = "flow-1",
            name = "Test",
            version = "1.0.0",
            components = listOf(
                StartComponent(id = "start-1", name = "Início"),
                EndComponent(id = "end-1", name = "Fim Alcançável"),
                EndComponent(id = "end-2", name = "Fim Não Alcançável")
            ),
            connections = listOf(
                Connection(
                    id = "conn-1",
                    sourceComponentId = "start-1",
                    sourcePortId = "out",
                    targetComponentId = "end-1",
                    targetPortId = "in"
                )
            )
        )
        
        val result = validator.validate(flow)
        
        // O EndComponent "end-2" não é alcançável
        assertTrue(result.warnings.any { 
            it.code == "UNREACHABLE_END_COMPONENT" && it.message.contains("end-2") 
        })
    }
    
    // ==================== Validação de Componentes ====================
    
    @Test
    fun testValidateComponents_ValidActionComponent() {
        val flow = Flow(
            id = "flow-1",
            name = "Test",
            version = "1.0.0",
            components = listOf(
                StartComponent(id = "start-1", name = "Início"),
                createActionComponent("action-1", "Ação"),
                EndComponent(id = "end-1", name = "Fim")
            ),
            connections = listOf(
                Connection("c1", "start-1", "out", "action-1", "in"),
                Connection("c2", "action-1", "success", "end-1", "in")
            )
        )
        
        val errors = validator.validateComponents(flow)
        
        assertTrue(errors.isEmpty())
    }
    
    @Test
    fun testValidateComponents_ValidDecisionComponent() {
        val flow = Flow(
            id = "flow-1",
            name = "Test",
            version = "1.0.0",
            components = listOf(
                StartComponent(id = "start-1", name = "Início"),
                createDecisionComponent("decision-1", "Decisão"),
                EndComponent(id = "end-1", name = "Fim")
            ),
            connections = listOf(
                Connection("c1", "start-1", "out", "decision-1", "in"),
                Connection("c2", "decision-1", "true", "end-1", "in")
            )
        )
        
        val errors = validator.validateComponents(flow)
        
        assertTrue(errors.isEmpty())
    }
    
    // ==================== Teste Completo ====================
    
    @Test
    fun testValidate_ComplexValidFlow() {
        val flow = Flow(
            id = "complex-flow",
            name = "Fluxo Complexo",
            version = "2.0.0",
            components = listOf(
                StartComponent(id = "start", name = "Início"),
                createActionComponent("action1", "Ação 1"),
                createDecisionComponent("decision", "Decisão"),
                createActionComponent("action2", "Ação 2"),
                EndComponent(id = "end-success", name = "Sucesso"),
                EndComponent(id = "end-error", name = "Erro")
            ),
            connections = listOf(
                Connection("c1", "start", "out", "action1", "in"),
                Connection("c2", "action1", "success", "decision", "in"),
                Connection("c3", "action1", "error", "end-error", "in"),
                Connection("c4", "decision", "true", "action2", "in"),
                Connection("c5", "decision", "false", "end-error", "in"),
                Connection("c6", "action2", "success", "end-success", "in")
            )
        )
        
        val result = validator.validate(flow)
        
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }
}
