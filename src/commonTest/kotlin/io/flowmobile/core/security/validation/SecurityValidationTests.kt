package io.flowmobile.core.security.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Testes para o módulo de validação de segurança.
 */
class ExpressionSanitizerTest {

    @Test
    fun testSanitizeValidExpression() {
        val result = ExpressionSanitizer.sanitize("user.name")
        when (result) {
            is SanitizeResult.Valid -> assertEquals("user.name", result.expression)
            else -> fail("Expected Valid result")
        }
    }

    @Test
    fun testSanitizeBlocksDropCommand() {
        val result = ExpressionSanitizer.sanitize("DROP TABLE users")
        assertTrue(result is SanitizeResult.Invalid)
    }

    @Test
    fun testSanitizeBlocksDeleteCommand() {
        val result = ExpressionSanitizer.sanitize("DELETE FROM users")
        assertTrue(result is SanitizeResult.Invalid)
    }

    @Test
    fun testSanitizeBlocksInsertCommand() {
        val result = ExpressionSanitizer.sanitize("INSERT INTO users VALUES")
        assertTrue(result is SanitizeResult.Invalid)
    }

    @Test
    fun testSanitizeBlocksUpdateCommand() {
        val result = ExpressionSanitizer.sanitize("UPDATE users SET")
        assertTrue(result is SanitizeResult.Invalid)
    }

    @Test
    fun testSanitizeAllowsVariableReferences() {
        val result = ExpressionSanitizer.sanitize("user.name")
        assertTrue(result is SanitizeResult.Valid)
    }

    @Test
    fun testSanitizeAllowsPropertyAccess() {
        val result = ExpressionSanitizer.sanitize("data.items")
        assertTrue(result is SanitizeResult.Valid)
    }

    @Test
    fun testSanitizeBlocksCommandExecution() {
        val result = ExpressionSanitizer.sanitize("exec command")
        assertTrue(result is SanitizeResult.Invalid)
    }

    @Test
    fun testSanitizeBlocksEval() {
        val result = ExpressionSanitizer.sanitize("eval code")
        assertTrue(result is SanitizeResult.Invalid)
    }

    @Test
    fun testSanitizeBlocksSystemAccess() {
        val result = ExpressionSanitizer.sanitize("System.exit")
        assertTrue(result is SanitizeResult.Invalid)
    }

    @Test
    fun testSanitizeBlocksRuntimeAccess() {
        val result = ExpressionSanitizer.sanitize("Runtime.getRuntime")
        assertTrue(result is SanitizeResult.Invalid)
    }

    @Test
    fun testSanitizeRejectsEmptyExpression() {
        val result = ExpressionSanitizer.sanitize("")
        assertTrue(result is SanitizeResult.Invalid)
    }
}

class FlowSchemaValidatorTest {

    private val propertyValidator = ComponentPropertyValidator()
    private val validator = FlowSchemaValidator(propertyValidator)

    @Test
    fun testValidateMissingId() {
        val flowJson = mapOf(
            "name" to "Test Flow",
            "version" to "1.0.0",
            "components" to emptyList<Any>(),
            "connections" to emptyList<Any>()
        )
        val result = validator.validate(flowJson)
        assertTrue(result is FlowValidationResult.Invalid)
    }

    @Test
    fun testValidateMissingComponents() {
        val flowJson = mapOf(
            "id" to "flow-1",
            "name" to "Test Flow",
            "version" to "1.0.0",
            "connections" to emptyList<Any>()
        )
        val result = validator.validate(flowJson)
        assertTrue(result is FlowValidationResult.Invalid)
    }

    @Test
    fun testValidateMissingConnections() {
        val flowJson = mapOf(
            "id" to "flow-1",
            "name" to "Test Flow",
            "version" to "1.0.0",
            "components" to emptyList<Any>()
        )
        val result = validator.validate(flowJson)
        assertTrue(result is FlowValidationResult.Invalid)
    }

    @Test
    fun testValidateValidMinimalFlow() {
        val flowJson = mapOf(
            "id" to "flow-1",
            "name" to "Test Flow",
            "version" to "1.0.0",
            "components" to listOf(
                mapOf(
                    "id" to "start",
                    "type" to "StartComponent",
                    "properties" to emptyMap<String, Any>()
                )
            ),
            "connections" to emptyList<Any>()
        )
        val result = validator.validate(flowJson)
        assertTrue(result is FlowValidationResult.Valid)
    }
}
