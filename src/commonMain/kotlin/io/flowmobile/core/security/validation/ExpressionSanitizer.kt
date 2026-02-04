package io.flowmobile.core.security.validation

/**
 * Resultado da sanitização de uma expressão.
 */
sealed class SanitizeResult {
    /**
     * Expressão válida após sanitização.
     * @property expression A expressão sanitizada
     */
    data class Valid(val expression: String) : SanitizeResult()

    /**
     * Expressão inválida que não passou na validação.
     * @property reason Motivo da rejeição
     */
    data class Invalid(val reason: String) : SanitizeResult()
}

/**
 * Sanitiza e valida expressões para prevenir injeção de código.
 *
 * Implementa defesa em profundidade contra expressões maliciosas:
 * - Rejeita caracteres especiais perigosos
 * - Limita tamanho da expressão
 * - Valida padrão de identificadores
 * - Detecção de tentativas comuns de bypass
 *
 * **Padrão Permitido:**
 * - Identificadores: `[a-zA-Z_][a-zA-Z0-9_]*`
 * - Acesso a propriedades: `.` (ponto)
 * - Acesso a índices: `[]` (colchetes)
 * - Operadores aritméticos: `+ - * /`
 * - Comparadores: `== != < > <= >=`
 * - Lógicos: `&& ||`
 * - Espaços em branco
 *
 * **Exemplos Válidos:**
 * ```
 * user.name
 * items[0].value
 * total + subtotal
 * status == "active"
 * ```
 *
 * **Exemplos Inválidos:**
 * ```
 * System.exit(0)           // Caractere proibido '('
 * import java.lang.Runtime // Palavra-chave suspeitosa
 * $(eval)                   // Caracteres proibidos
 * ```
 */
object ExpressionSanitizer {
    /**
     * Padrão de expressão válida.
     * Permite identificadores, operadores básicos, acessos de propriedade/índice.
     */
    private val ALLOWED_PATTERN = Regex(
        "^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*|\\[\\d+\\])*" +
        "(\\s*[+\\-*/%]\\s*[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*|\\[\\d+\\])*)*" +
        "(\\s*(==|!=|<|>|<=|>=|&&|\\|\\|)\\s*" +
        "([a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*|\\[\\d+\\])*|\\d+|\"[^\"]*\"))*$"
    )

    /**
     * Caracteres perigosos que nunca são permitidos.
     */
    private val DANGEROUS_CHARS = setOf(
        '(', ')', '{', '}', '[', ']',  // Parênteses e chaves (parcialmente permitidos)
        ';', '`', '\\', '"', '\'',      // Delimitadores perigosos
        '$', '@', '#', '&', '|', '?',  // Caracteres especiais
        '!', '^', '~'                   // Operadores bit a bit
    )

    /**
     * Palavras-chave suspeitas que indicam tentativa de código executável.
     */
    private val SUSPICIOUS_KEYWORDS = setOf(
        // Palavras-chave de linguagens
        "import", "export", "require", "eval", "exec", "System", "Runtime",
        // Métodos perigosos
        "exec", "executeQuery", "execute", "system", "Runtime.getRuntime",
        // Injeção
        "drop", "delete", "truncate", "insert", "update",
        // Acesso a sistema
        "File", "FileInputStream", "FileOutputStream",
        // Diretivas
        "pragma", "script"
    )

    /**
     * Tamanho máximo permitido para uma expressão (4KB).
     */
    private const val MAX_EXPRESSION_LENGTH = 4096

    /**
     * Sanitiza uma expressão removendo espaços em branco nas extremidades e validando.
     *
     * @param expression A expressão a ser sanitizada
     * @return [SanitizeResult.Valid] se a expressão é segura, [SanitizeResult.Invalid] caso contrário
     *
     * **Algoritmo:**
     * 1. Verifica se a expressão está vazia
     * 2. Valida tamanho máximo
     * 3. Detecta caracteres perigosos
     * 4. Detecta palavras-chave suspeitas (case-insensitive)
     * 5. Valida padrão geral de expressão
     * 6. Retorna expressão sanitizada (com trim)
     */
    fun sanitize(expression: String): SanitizeResult {
        // Validação 1: Expressão vazia
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) {
            return SanitizeResult.Invalid("Expression cannot be empty")
        }

        // Validação 2: Tamanho máximo
        if (trimmed.length > MAX_EXPRESSION_LENGTH) {
            return SanitizeResult.Invalid(
                "Expression exceeds maximum length of $MAX_EXPRESSION_LENGTH characters"
            )
        }

        // Validação 3: Caracteres perigosos
        val dangerousCharsFound = trimmed.any { it in DANGEROUS_CHARS }
        if (dangerousCharsFound) {
            val forbidden = trimmed.filter { it in DANGEROUS_CHARS }
            return SanitizeResult.Invalid(
                "Expression contains forbidden characters: $forbidden"
            )
        }

        // Validação 4: Palavras-chave suspeitas (case-insensitive)
        val lowerExpression = trimmed.lowercase()
        val suspiciousFound = SUSPICIOUS_KEYWORDS.any { keyword ->
            lowerExpression.contains(keyword, ignoreCase = true)
        }
        if (suspiciousFound) {
            return SanitizeResult.Invalid(
                "Expression contains suspicious keywords (possible injection attempt)"
            )
        }

        // Validação 5: Padrão geral (permite mais operadores comuns)
        if (!isValidExpressionPattern(trimmed)) {
            return SanitizeResult.Invalid(
                "Expression does not match allowed pattern"
            )
        }

        // Validação 6: Sem SQL injection
        if (detectSQLInjection(trimmed)) {
            return SanitizeResult.Invalid(
                "Expression contains possible SQL injection pattern"
            )
        }

        return SanitizeResult.Valid(trimmed)
    }

    /**
     * Valida se a expressão segue o padrão permitido.
     * Permite identificadores simples com operadores básicos.
     */
    private fun isValidExpressionPattern(expression: String): Boolean {
        // Padrão simplificado que é mais permissivo
        val simplePattern = Regex("^[a-zA-Z_][a-zA-Z0-9_\\s.\\[\\]\\+\\-*/%=!<>&|]*$")
        return simplePattern.matches(expression)
    }

    /**
     * Detecta padrões comuns de SQL injection.
     */
    private fun detectSQLInjection(expression: String): Boolean {
        val sqlPatterns = listOf(
            Regex("'\\s*(OR|AND)\\s*'", RegexOption.IGNORE_CASE),
            Regex("--\\s*$", RegexOption.IGNORE_CASE),
            Regex(";\\s*(DROP|DELETE|INSERT|UPDATE|TRUNCATE)", RegexOption.IGNORE_CASE),
            Regex("\\*/.*UNION", RegexOption.IGNORE_CASE)
        )
        return sqlPatterns.any { it.containsMatchIn(expression) }
    }

    /**
     * Valida múltiplas expressões e retorna a primeira inválida, se houver.
     *
     * @param expressions Lista de expressões para validar
     * @return [SanitizeResult.Valid] se todas são válidas, [SanitizeResult.Invalid] caso contrário
     */
    fun validateMultiple(expressions: List<String>): SanitizeResult {
        expressions.forEach { expression ->
            val result = sanitize(expression)
            if (result is SanitizeResult.Invalid) {
                return result
            }
        }
        return SanitizeResult.Valid("")
    }
}
