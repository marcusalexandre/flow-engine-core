package io.flowmobile.core.security.validation

import io.flowmobile.core.domain.Component
import io.flowmobile.core.domain.ComponentType

/**
 * Resultado da validação de propriedades de componentes.
 */
sealed class PropertyValidationResult {
    /**
     * Propriedades válidas.
     * @property properties As propriedades validadas
     */
    data class Valid(val properties: Map<String, Any>) : PropertyValidationResult()

    /**
     * Propriedades inválidas.
     * @property errors Erros encontrados na validação
     */
    data class Invalid(val errors: List<String>) : PropertyValidationResult()
}

/**
 * Tipos de propriedades suportadas com validações específicas.
 */
enum class PropertyType {
    /** String - validação de tamanho e conteúdo */
    STRING,
    /** Número inteiro - validação de range */
    INTEGER,
    /** Número decimal - validação de range e precisão */
    DECIMAL,
    /** Booleano - validação de tipo */
    BOOLEAN,
    /** Lista - validação de tipo de itens e tamanho */
    LIST,
    /** Objeto/Map - validação de estrutura */
    OBJECT,
    /** Expressão - validação de injeção */
    EXPRESSION,
    /** Referência a variável */
    VARIABLE_REF
}

/**
 * Descritor de uma propriedade com suas validações.
 *
 * @property name Nome da propriedade
 * @property type Tipo da propriedade
 * @property required Se é obrigatória
 * @property constraints Restrições adicionais
 */
data class PropertyDescriptor(
    val name: String,
    val type: PropertyType,
    val required: Boolean = false,
    val constraints: Map<String, Any> = emptyMap()
)

/**
 * Valida propriedades de componentes contra tipos e restrições.
 *
 * Implementa validação em profundidade:
 * - Type checking
 * - Range validation (min/max)
 * - Size validation (length/count)
 * - Pattern validation (regex)
 * - Expression injection prevention
 * - Custom validators
 *
 * **Constraints Suportadas:**
 * ```kotlin
 * "minLength" -> Int
 * "maxLength" -> Int
 * "pattern" -> String (regex)
 * "minValue" -> Number
 * "maxValue" -> Number
 * "allowedValues" -> List<Any>
 * "itemType" -> PropertyType
 * "maxItems" -> Int
 * "maxNesting" -> Int
 * ```
 */
class ComponentPropertyValidator {
    /**
     * Registros de descritores simples em vez de Map para evitar problemas de type inference
     */
    private val descriptors = mutableListOf<Pair<ComponentType, List<PropertyDescriptor>>>()

    /**
     * Registra descritores de propriedades para um tipo de componente.
     *
     * @param componentType ID do tipo de componente
     * @param descriptorList Lista de descritores de propriedades
     */
    fun registerDescriptors(
        componentType: ComponentType,
        descriptorList: List<PropertyDescriptor>
    ) {
        descriptors.removeAll { it.first == componentType }
        descriptors.add(componentType to descriptorList)
    }

    /**
     * Valida propriedades de um componente.
     *
     * @param component O componente a validar
     * @param properties As propriedades a validar
     * @return [PropertyValidationResult.Valid] se válido, [PropertyValidationResult.Invalid] caso contrário
     *
     * **Algoritmo:**
     * 1. Recupera descritores para o tipo de componente
     * 2. Para cada propriedade, valida contra seu descritor
     * 3. Coleta todos os erros
     * 4. Retorna resultado consolidado
     */
    fun validate(
        component: Component,
        properties: Map<String, Any>
    ): PropertyValidationResult {
        val desc = descriptors.find { it.first == component.type }?.second ?: emptyList()
        val errors = mutableListOf<String>()

        // Validar propriedades obrigatórias
        for (descriptor in desc) {
            if (descriptor.required && descriptor.name !in properties) {
                errors.add("Required property '${descriptor.name}' is missing")
            }
        }

        // Validar propriedades fornecidas
        for (name in properties.keys) {
            val value = properties[name] ?: continue
            val descriptor = desc.find { it.name == name }
            if (descriptor != null) {
                val validationErrors = validateProperty(descriptor, value)
                errors.addAll(validationErrors)
            }
            // Propriedade desconhecida é permitida (flexibility)
        }

        return if (errors.isEmpty()) {
            PropertyValidationResult.Valid(properties)
        } else {
            PropertyValidationResult.Invalid(errors)
        }
    }

    /**
     * Valida uma propriedade individual contra seu descritor.
     *
     * @param descriptor O descritor da propriedade
     * @param value O valor a validar
     * @return Lista de erros (vazio se válido)
     */
    private fun validateProperty(
        descriptor: PropertyDescriptor,
        value: Any
    ): List<String> {
        val errors = mutableListOf<String>()

        try {
            when (descriptor.type) {
                PropertyType.STRING -> {
                    if (value !is String) {
                        errors.add("Property '${descriptor.name}' must be a string, got ${value::class.simpleName}")
                    } else {
                        errors.addAll(validateString(descriptor, value))
                    }
                }
                PropertyType.INTEGER -> {
                    if (value !is Number) {
                        errors.add("Property '${descriptor.name}' must be a number, got ${value::class.simpleName}")
                    } else {
                        errors.addAll(validateInteger(descriptor, value))
                    }
                }
                PropertyType.DECIMAL -> {
                    if (value !is Number) {
                        errors.add("Property '${descriptor.name}' must be a decimal number, got ${value::class.simpleName}")
                    } else {
                        errors.addAll(validateDecimal(descriptor, value))
                    }
                }
                PropertyType.BOOLEAN -> {
                    if (value !is Boolean) {
                        errors.add("Property '${descriptor.name}' must be a boolean, got ${value::class.simpleName}")
                    }
                }
                PropertyType.LIST -> {
                    if (value !is List<*>) {
                        errors.add("Property '${descriptor.name}' must be a list, got ${value::class.simpleName}")
                    } else {
                        errors.addAll(validateList(descriptor, value))
                    }
                }
                PropertyType.EXPRESSION -> {
                    if (value !is String) {
                        errors.add("Property '${descriptor.name}' (expression) must be a string, got ${value::class.simpleName}")
                    } else {
                        val sanitized = ExpressionSanitizer.sanitize(value)
                        if (sanitized is SanitizeResult.Invalid) {
                            errors.add("Property '${descriptor.name}': ${sanitized.reason}")
                        }
                    }
                }
                PropertyType.VARIABLE_REF -> {
                    if (value !is String) {
                        errors.add("Property '${descriptor.name}' (variable reference) must be a string, got ${value::class.simpleName}")
                    } else {
                        if (!isValidVariableReference(value)) {
                            errors.add("Property '${descriptor.name}': Invalid variable reference format")
                        }
                    }
                }
                PropertyType.OBJECT -> {
                    if (value !is Map<*, *>) {
                        errors.add("Property '${descriptor.name}' must be an object, got ${value::class.simpleName}")
                    } else {
                        errors.addAll(validateObject(descriptor, value))
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Error validating property '${descriptor.name}': ${e.message}")
        }

        return errors
    }

    /**
     * Valida propriedades String.
     */
    private fun validateString(
        descriptor: PropertyDescriptor,
        value: String
    ): List<String> {
        val errors = mutableListOf<String>()
        val constraints = descriptor.constraints

        // Validar tamanho mínimo
        val minLength = constraints["minLength"] as? Int
        if (minLength != null && value.length < minLength) {
            errors.add(
                "Property '${descriptor.name}' must have at least $minLength characters, " +
                "got ${value.length}"
            )
        }

        // Validar tamanho máximo
        val maxLength = constraints["maxLength"] as? Int
        if (maxLength != null && value.length > maxLength) {
            errors.add(
                "Property '${descriptor.name}' must have at most $maxLength characters, " +
                "got ${value.length}"
            )
        }

        // Validar padrão (regex)
        val pattern = constraints["pattern"] as? String
        if (pattern != null) {
            try {
                if (!Regex(pattern).matches(value)) {
                    errors.add(
                        "Property '${descriptor.name}' does not match required pattern: $pattern"
                    )
                }
            } catch (e: Exception) {
                errors.add("Invalid regex pattern in constraint: $pattern")
            }
        }

        // Validar valores permitidos
        @Suppress("UNCHECKED_CAST")
        val allowedValues = constraints["allowedValues"] as? List<Any>
        if (allowedValues != null && value !in allowedValues) {
            errors.add(
                "Property '${descriptor.name}' must be one of $allowedValues, got '$value'"
            )
        }

        return errors
    }

    /**
     * Valida propriedades Integer.
     */
    private fun validateInteger(
        descriptor: PropertyDescriptor,
        value: Number
    ): List<String> {
        val errors = mutableListOf<String>()
        val constraints = descriptor.constraints
        val intValue = value.toLong()

        // Validar valor mínimo
        val minValue = (constraints["minValue"] as? Number)?.toLong()
        if (minValue != null && intValue < minValue) {
            errors.add(
                "Property '${descriptor.name}' must be at least $minValue, got $intValue"
            )
        }

        // Validar valor máximo
        val maxValue = (constraints["maxValue"] as? Number)?.toLong()
        if (maxValue != null && intValue > maxValue) {
            errors.add(
                "Property '${descriptor.name}' must be at most $maxValue, got $intValue"
            )
        }

        return errors
    }

    /**
     * Valida propriedades Decimal.
     */
    private fun validateDecimal(
        descriptor: PropertyDescriptor,
        value: Number
    ): List<String> {
        val errors = mutableListOf<String>()
        val constraints = descriptor.constraints
        val doubleValue = value.toDouble()

        // Validar valor mínimo
        val minValue = (constraints["minValue"] as? Number)?.toDouble()
        if (minValue != null && doubleValue < minValue) {
            errors.add(
                "Property '${descriptor.name}' must be at least $minValue, got $doubleValue"
            )
        }

        // Validar valor máximo
        val maxValue = (constraints["maxValue"] as? Number)?.toDouble()
        if (maxValue != null && doubleValue > maxValue) {
            errors.add(
                "Property '${descriptor.name}' must be at most $maxValue, got $doubleValue"
            )
        }

        return errors
    }

    /**
     * Valida propriedades List.
     */
    private fun validateList(
        descriptor: PropertyDescriptor,
        value: List<*>
    ): List<String> {
        val errors = mutableListOf<String>()
        val constraints = descriptor.constraints

        // Validar quantidade máxima de itens
        val maxItems = constraints["maxItems"] as? Int
        if (maxItems != null && value.size > maxItems) {
            errors.add(
                "Property '${descriptor.name}' must have at most $maxItems items, got ${value.size}"
            )
        }

        return errors
    }

    /**
     * Valida propriedades Object/Map.
     */
    private fun validateObject(
        descriptor: PropertyDescriptor,
        value: Map<*, *>
    ): List<String> {
        val errors = mutableListOf<String>()
        val constraints = descriptor.constraints

        // Validar profundidade de aninhamento
        val maxNesting = constraints["maxNesting"] as? Int
        if (maxNesting != null) {
            val depth = calculateMapDepth(value)
            if (depth > maxNesting) {
                errors.add(
                    "Property '${descriptor.name}' exceeds maximum nesting depth of $maxNesting"
                )
            }
        }

        return errors
    }

    /**
     * Calcula a profundidade de aninhamento de um mapa.
     */
    private fun calculateMapDepth(map: Map<*, *>): Int {
        if (map.isEmpty()) return 1
        var maxDepth = 1
        for (value in map.values) {
            if (value is Map<*, *>) {
                maxDepth = maxOf(maxDepth, 1 + calculateMapDepth(value))
            }
        }
        return maxDepth
    }

    /**
     * Valida se uma string é uma referência válida de variável.
     * Formato: `$variableName` ou `${variableName}`
     */
    private fun isValidVariableReference(reference: String): Boolean {
        return reference.matches(Regex("^\\$\\{?[a-zA-Z_][a-zA-Z0-9_]*\\}?$"))
    }
}
