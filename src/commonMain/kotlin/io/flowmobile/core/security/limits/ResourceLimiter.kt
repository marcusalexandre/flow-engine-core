package io.flowmobile.core.security.limits

import kotlinx.datetime.Clock

/**
 * Configuração de limites de recursos para execução de fluxos.
 *
 * @property maxExecutionTime Tempo máximo de execução em milissegundos
 * @property maxSteps Número máximo de passos de execução
 * @property maxContextSize Tamanho máximo do contexto em bytes
 * @property maxAuditTrailEntries Número máximo de entradas no audit trail
 * @property maxRecursionDepth Profundidade máxima de recursão
 * @property maxParallelBranches Número máximo de branches paralelos
 * @property maxVariablesPerContext Número máximo de variáveis no contexto
 * @property maxVariableSize Tamanho máximo de uma variável em bytes
 */
data class LimitConfig(
    val maxExecutionTime: Long = 30_000L,           // 30 segundos
    val maxSteps: Int = 10_000,                     // 10k passos
    val maxContextSize: Long = 10 * 1024 * 1024L,   // 10MB
    val maxAuditTrailEntries: Int = 10_000,         // 10k entradas
    val maxRecursionDepth: Int = 100,               // 100 níveis
    val maxParallelBranches: Int = 10,              // 10 branches
    val maxVariablesPerContext: Int = 1000,         // 1000 variáveis
    val maxVariableSize: Long = 1024 * 1024L        // 1MB por variável
) {
    /**
     * Valida se a configuração é consistente.
     * @return Lista de erros, vazio se válido
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (maxExecutionTime <= 0) {
            errors.add("maxExecutionTime must be positive, got $maxExecutionTime")
        }
        if (maxSteps <= 0) {
            errors.add("maxSteps must be positive, got $maxSteps")
        }
        if (maxContextSize <= 0) {
            errors.add("maxContextSize must be positive, got $maxContextSize")
        }
        if (maxAuditTrailEntries <= 0) {
            errors.add("maxAuditTrailEntries must be positive, got $maxAuditTrailEntries")
        }
        if (maxRecursionDepth <= 0) {
            errors.add("maxRecursionDepth must be positive, got $maxRecursionDepth")
        }
        if (maxParallelBranches <= 0) {
            errors.add("maxParallelBranches must be positive, got $maxParallelBranches")
        }
        if (maxVariablesPerContext <= 0) {
            errors.add("maxVariablesPerContext must be positive, got $maxVariablesPerContext")
        }
        if (maxVariableSize <= 0) {
            errors.add("maxVariableSize must be positive, got $maxVariableSize")
        }

        return errors
    }

    /**
     * Retorna versão mais restritiva (para ambientes sensíveis).
     */
    companion object {
        /**
         * Configuração padrão (balanceada).
         */
        fun default() = LimitConfig()

        /**
         * Configuração permissiva (para desenvolvimento).
         */
        fun permissive() = LimitConfig(
            maxExecutionTime = 5 * 60_000L,         // 5 minutos
            maxSteps = 100_000,                     // 100k passos
            maxContextSize = 100 * 1024 * 1024L,    // 100MB
            maxAuditTrailEntries = 100_000,         // 100k entradas
            maxRecursionDepth = 1000,               // 1000 níveis
            maxParallelBranches = 100,              // 100 branches
            maxVariablesPerContext = 10_000,        // 10k variáveis
            maxVariableSize = 10 * 1024 * 1024L     // 10MB por variável
        )

        /**
         * Configuração restritiva (para produção com dados sensíveis).
         */
        fun restrictive() = LimitConfig(
            maxExecutionTime = 10_000L,             // 10 segundos
            maxSteps = 1_000,                       // 1k passos
            maxContextSize = 1024 * 1024L,          // 1MB
            maxAuditTrailEntries = 1_000,           // 1k entradas
            maxRecursionDepth = 10,                 // 10 níveis
            maxParallelBranches = 5,                // 5 branches
            maxVariablesPerContext = 100,           // 100 variáveis
            maxVariableSize = 100 * 1024L           // 100KB por variável
        )
    }
}

/**
 * Exceções de limite de recursos.
 */
sealed class ResourceLimitExceededException(message: String) : Exception(message)

class ExecutionTimeLimitExceededException(maxTime: Long) :
    ResourceLimitExceededException("Execution time exceeded limit of ${maxTime}ms")

class StepLimitExceededException(maxSteps: Int) :
    ResourceLimitExceededException("Execution exceeded maximum steps of $maxSteps")

class ContextSizeLimitExceededException(currentSize: Long, maxSize: Long) :
    ResourceLimitExceededException(
        "Context size ($currentSize bytes) exceeded limit of $maxSize bytes"
    )

class AuditTrailLimitExceededException(maxEntries: Int) :
    ResourceLimitExceededException("Audit trail exceeded maximum entries of $maxEntries")

class RecursionDepthLimitExceededException(maxDepth: Int) :
    ResourceLimitExceededException("Recursion depth exceeded limit of $maxDepth")

class ParallelBranchLimitExceededException(maxBranches: Int) :
    ResourceLimitExceededException("Parallel branches exceeded limit of $maxBranches")

class VariableLimitExceededException(maxVariables: Int) :
    ResourceLimitExceededException("Number of variables exceeded limit of $maxVariables")

class VariableSizeLimitExceededException(size: Long, maxSize: Long) :
    ResourceLimitExceededException(
        "Variable size ($size bytes) exceeded limit of $maxSize bytes"
    )

/**
 * Estado de uso de recursos durante execução.
 */
data class ResourceUsage(
    val executionTimeMs: Long = 0L,
    val stepCount: Int = 0,
    val contextSize: Long = 0L,
    val auditTrailEntries: Int = 0,
    val recursionDepth: Int = 0,
    val parallelBranches: Int = 0,
    val variableCount: Int = 0,
    val peakContextSize: Long = 0L,
    val peakVariableCount: Int = 0
) {
    /**
     * Verifica se o uso está dentro dos limites.
     * @return Exceção se limite excedido, null caso contrário
     */
    fun checkAgainstLimits(config: LimitConfig): ResourceLimitExceededException? {
        return when {
            executionTimeMs > config.maxExecutionTime ->
                ExecutionTimeLimitExceededException(config.maxExecutionTime)
            stepCount > config.maxSteps ->
                StepLimitExceededException(config.maxSteps)
            contextSize > config.maxContextSize ->
                ContextSizeLimitExceededException(contextSize, config.maxContextSize)
            auditTrailEntries > config.maxAuditTrailEntries ->
                AuditTrailLimitExceededException(config.maxAuditTrailEntries)
            recursionDepth > config.maxRecursionDepth ->
                RecursionDepthLimitExceededException(config.maxRecursionDepth)
            parallelBranches > config.maxParallelBranches ->
                ParallelBranchLimitExceededException(config.maxParallelBranches)
            variableCount > config.maxVariablesPerContext ->
                VariableLimitExceededException(config.maxVariablesPerContext)
            else -> null
        }
    }
}

/**
 * Limita e monitora uso de recursos durante execução de fluxos.
 *
 * Implementa enforcement de limites em tempo real:
 * - Monitoramento de tempo de execução
 * - Contagem de passos
 * - Estimativa de tamanho de contexto
 * - Monitoramento de profundidade de recursão
 * - Validação de número de branches paralelos
 * - Limitação de variáveis por contexto
 *
 * **Uso:**
 * ```kotlin
 * val limiter = ResourceLimiter(LimitConfig.default())
 * 
 * limiter.withLimits {
 *     limiter.recordStep()
 *     limiter.recordContextSize(1024)
 *     limiter.checkLimits()
 * }
 * ```
 */
class ResourceLimiter(
    private val config: LimitConfig
) {
    private val usage = mutableMapOf<String, ResourceUsage>()
    private val timers = mutableMapOf<String, Long>()

    init {
        val errors = config.validate()
        if (errors.isNotEmpty()) {
            throw IllegalArgumentException("Invalid LimitConfig: ${errors.joinToString(", ")}")
        }
    }

    /**
     * Inicia execução com ID único (por fluxo/sessão).
     */
    fun startExecution(executionId: String) {
        timers[executionId] = Clock.System.now().toEpochMilliseconds()
        usage[executionId] = ResourceUsage()
    }

    /**
     * Termina execução e retorna uso de recursos.
     */
    fun endExecution(executionId: String): ResourceUsage? {
        val startTime = timers.remove(executionId) ?: return null
        val executionTime = Clock.System.now().toEpochMilliseconds() - startTime

        return usage[executionId]?.copy(executionTimeMs = executionTime).also {
            usage.remove(executionId)
        }
    }

    /**
     * Registra um passo de execução.
     * @throws StepLimitExceededException se o limite é excedido
     */
    fun recordStep(executionId: String) {
        val current = usage[executionId] ?: return
        val updated = current.copy(stepCount = current.stepCount + 1)
        usage[executionId] = updated

        if (updated.stepCount > config.maxSteps) {
            throw StepLimitExceededException(config.maxSteps)
        }
    }

    /**
     * Registra tamanho do contexto.
     * @throws ContextSizeLimitExceededException se o limite é excedido
     */
    fun recordContextSize(executionId: String, size: Long) {
        val current = usage[executionId] ?: return
        val peak = maxOf(current.peakContextSize, size)
        val updated = current.copy(contextSize = size, peakContextSize = peak)
        usage[executionId] = updated

        if (size > config.maxContextSize) {
            throw ContextSizeLimitExceededException(size, config.maxContextSize)
        }
    }

    /**
     * Registra entrada no audit trail.
     * @throws AuditTrailLimitExceededException se o limite é excedido
     */
    fun recordAuditEntry(executionId: String) {
        val current = usage[executionId] ?: return
        val updated = current.copy(auditTrailEntries = current.auditTrailEntries + 1)
        usage[executionId] = updated

        if (updated.auditTrailEntries > config.maxAuditTrailEntries) {
            throw AuditTrailLimitExceededException(config.maxAuditTrailEntries)
        }
    }

    /**
     * Registra profundidade de recursão.
     * @throws RecursionDepthLimitExceededException se o limite é excedido
     */
    fun recordRecursionDepth(executionId: String, depth: Int) {
        val current = usage[executionId] ?: return
        val updated = current.copy(recursionDepth = depth)
        usage[executionId] = updated

        if (depth > config.maxRecursionDepth) {
            throw RecursionDepthLimitExceededException(config.maxRecursionDepth)
        }
    }

    /**
     * Registra número de branches paralelos.
     * @throws ParallelBranchLimitExceededException se o limite é excedido
     */
    fun recordParallelBranches(executionId: String, branches: Int) {
        val current = usage[executionId] ?: return
        val updated = current.copy(parallelBranches = branches)
        usage[executionId] = updated

        if (branches > config.maxParallelBranches) {
            throw ParallelBranchLimitExceededException(config.maxParallelBranches)
        }
    }

    /**
     * Registra número de variáveis.
     * @throws VariableLimitExceededException se o limite é excedido
     */
    fun recordVariableCount(executionId: String, count: Int) {
        val current = usage[executionId] ?: return
        val peak = maxOf(current.peakVariableCount, count)
        val updated = current.copy(variableCount = count, peakVariableCount = peak)
        usage[executionId] = updated

        if (count > config.maxVariablesPerContext) {
            throw VariableLimitExceededException(config.maxVariablesPerContext)
        }
    }

    /**
     * Valida tamanho de uma variável individual.
     * @throws VariableSizeLimitExceededException se o limite é excedido
     */
    fun validateVariableSize(size: Long) {
        if (size > config.maxVariableSize) {
            throw VariableSizeLimitExceededException(size, config.maxVariableSize)
        }
    }

    /**
     * Valida tempo total de execução.
     * @throws ExecutionTimeLimitExceededException se o limite é excedido
     */
    fun checkExecutionTime(executionId: String) {
        val startTime = timers[executionId] ?: return
        val elapsed = Clock.System.now().toEpochMilliseconds() - startTime

        if (elapsed > config.maxExecutionTime) {
            throw ExecutionTimeLimitExceededException(config.maxExecutionTime)
        }
    }

    /**
     * Verifica todos os limites contra o uso atual.
     * @throws ResourceLimitExceededException se algum limite é excedido
     */
    fun checkAllLimits(executionId: String) {
        val current = usage[executionId] ?: return
        val limitError = current.checkAgainstLimits(config)
        if (limitError != null) {
            throw limitError
        }

        checkExecutionTime(executionId)
    }

    /**
     * Retorna uso atual de recursos.
     */
    fun getUsage(executionId: String): ResourceUsage? = usage[executionId]

    /**
     * Retorna porcentagem de cada limite utilizado.
     */
    fun getUsagePercentages(executionId: String): Map<String, Double> {
        val current = usage[executionId] ?: return emptyMap()
        val startTime = timers[executionId] ?: Clock.System.now().toEpochMilliseconds()
        val elapsed = Clock.System.now().toEpochMilliseconds() - startTime

        return mapOf(
            "executionTime" to (elapsed.toDouble() / config.maxExecutionTime) * 100,
            "steps" to (current.stepCount.toDouble() / config.maxSteps) * 100,
            "contextSize" to (current.contextSize.toDouble() / config.maxContextSize) * 100,
            "auditTrailEntries" to (current.auditTrailEntries.toDouble() / config.maxAuditTrailEntries) * 100,
            "recursionDepth" to (current.recursionDepth.toDouble() / config.maxRecursionDepth) * 100,
            "parallelBranches" to (current.parallelBranches.toDouble() / config.maxParallelBranches) * 100,
            "variables" to (current.variableCount.toDouble() / config.maxVariablesPerContext) * 100
        )
    }

    /**
     * Limpa cache de execução antiga para evitar memory leak.
     */
    fun cleanup(executionId: String) {
        usage.remove(executionId)
        timers.remove(executionId)
    }
}
