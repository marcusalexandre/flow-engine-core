package io.flowmobile.core.security.ratelimit

import kotlinx.datetime.Clock

/**
 * Configuração de rate limit para um endpoint.
 *
 * @property maxRequests Número máximo de requisições permitidas
 * @property windowSizeMs Tamanho da janela de tempo em milissegundos
 * @property burstAllowed Se burst é permitido (exceder por pouco tempo)
 */
data class RateLimitConfig(
    val maxRequests: Int,
    val windowSizeMs: Long,
    val burstAllowed: Boolean = false
) {
    /**
     * Retorna configurações padrão para cenários comuns.
     */
    companion object {
        // Flow Execution: 100 req/min
        fun flowExecution() = RateLimitConfig(
            maxRequests = 100,
            windowSizeMs = 60_000L
        )

        // Flow Save: 10 req/min
        fun flowSave() = RateLimitConfig(
            maxRequests = 10,
            windowSizeMs = 60_000L
        )

        // Sandbox Execution: 50 req/min
        fun sandboxExecution() = RateLimitConfig(
            maxRequests = 50,
            windowSizeMs = 60_000L
        )

        // API Geral: 1000 req/min
        fun apiGeneral() = RateLimitConfig(
            maxRequests = 1000,
            windowSizeMs = 60_000L
        )

        // Authentication: 5 req/min (anti-brute-force)
        fun authentication() = RateLimitConfig(
            maxRequests = 5,
            windowSizeMs = 60_000L,
            burstAllowed = false
        )

        // Search: 100 req/min
        fun search() = RateLimitConfig(
            maxRequests = 100,
            windowSizeMs = 60_000L,
            burstAllowed = true
        )
    }
}

/**
 * Resultado de verificação de rate limit.
 */
sealed class RateLimitCheckResult {
    /**
     * Requisição é permitida.
     * @property remainingRequests Requisições restantes nesta janela
     * @property resetTimeMs Quando a janela é resetada (em ms desde epoch)
     */
    data class Allowed(
        val remainingRequests: Int,
        val resetTimeMs: Long
    ) : RateLimitCheckResult()

    /**
     * Requisição excede o limite.
     * @property resetTimeMs Quando poderá fazer nova requisição
     */
    data class RateLimited(
        val resetTimeMs: Long,
        val retryAfterMs: Long
    ) : RateLimitCheckResult()
}

/**
 * Rate limiter com algoritmo Token Bucket.
 *
 * Implementa controle de taxa de requisições usando token bucket:
 * - Cada cliente começa com N tokens
 * - Cada requisição consome 1 token
 * - Tokens regeneram a uma taxa constante
 * - Burst é permitido se há tokens disponíveis
 *
 * **Características:**
 * - Justo: Todos os clientes têm taxa garantida
 * - Flexível: Permite burst sem ser injusto
 * - Eficiente: O(1) por requisição
 * - Distribuído: Pode usar Redis para múltiplas instâncias
 *
 * **Exemplo:**
 * ```kotlin
 * val limiter = TokenBucketRateLimiter(RateLimitConfig.flowExecution())
 * when (val result = limiter.checkAndConsume("user-123")) {
 *     is RateLimitCheckResult.Allowed -> {
 *         // Processar requisição
 *     }
 *     is RateLimitCheckResult.RateLimited -> {
 *         // Retornar 429 com retry-after
 *     }
 * }
 * ```
 */
class TokenBucketRateLimiter(
    private val config: RateLimitConfig
) {
    /**
     * Estado de um bucket: (tokens, last_refill_time)
     */
    private data class BucketState(
        var tokens: Double,
        var lastRefillTimeMs: Long
    )

    /**
     * Buckets por identificador (cliente, endpoint, etc).
     */
    private val buckets = mutableMapOf<String, BucketState>()

    /**
     * Taxa de regeneração de tokens por ms.
     */
    private val tokensPerMs = config.maxRequests.toDouble() / config.windowSizeMs

    /**
     * Verifica e consome tokens de um cliente.
     *
     * @param clientId Identificador único do cliente
     * @return Resultado permitido ou rate-limited
     */
    fun checkAndConsume(clientId: String): RateLimitCheckResult {
        val bucket = buckets.getOrPut(clientId) { 
            BucketState(config.maxRequests.toDouble(), Clock.System.now().toEpochMilliseconds())
        }
        val now = Clock.System.now().toEpochMilliseconds()

        // Refill de tokens baseado no tempo decorrido
        val timeSinceLastRefill = now - bucket.lastRefillTimeMs
        val tokensToAdd = timeSinceLastRefill * tokensPerMs
        bucket.tokens = minOf(
            bucket.tokens + tokensToAdd,
            config.maxRequests.toDouble()
        )
        bucket.lastRefillTimeMs = now

        // Verificar se há tokens disponíveis
        return if (bucket.tokens >= 1.0) {
            bucket.tokens -= 1.0
            val remainingTokens = bucket.tokens.toInt()
            val resetTime = now + config.windowSizeMs

            RateLimitCheckResult.Allowed(remainingTokens, resetTime)
        } else {
            // Calcular quando teremos 1 token novamente
            val tokensNeeded = 1.0 - bucket.tokens
            val msNeeded = (tokensNeeded / tokensPerMs).toLong()
            val resetTime = now + msNeeded

            RateLimitCheckResult.RateLimited(resetTime, msNeeded)
        }
    }

    /**
     * Retorna informações do bucket de um cliente (para debugging).
     */
    fun getBucketInfo(clientId: String): Map<String, Any>? {
        val bucket = buckets[clientId] ?: return null
        return mapOf(
            "tokens" to bucket.tokens,
            "maxTokens" to config.maxRequests,
            "lastRefillTime" to bucket.lastRefillTimeMs
        )
    }

    /**
     * Reseta o bucket de um cliente.
     */
    fun resetClient(clientId: String) {
        buckets.remove(clientId)
    }

    /**
     * Reseta todos os buckets.
     */
    fun resetAll() {
        buckets.clear()
    }

    /**
     * Retorna número de clientes rastreados.
     */
    fun getTrackedClients(): Int = buckets.size

    /**
     * Limpa clientes inativos (sem requisições por um tempo).
     *
     * @param inactiveForMs Tempo de inatividade para remover
     * @return Número de clientes removidos
     */
    fun pruneInactiveClients(inactiveForMs: Long): Int {
        val now = Clock.System.now().toEpochMilliseconds()
        val removed = buckets.entries.removeAll { (_, state) ->
            now - state.lastRefillTimeMs > inactiveForMs
        }
        return if (removed) buckets.size else 0
    }
}

/**
 * Rate limiter baseado em contador simples.
 *
 * Versão mais simples que apenas conta requisições em uma janela.
 * Menos justo que token bucket mas mais simples.
 *
 * **Desvantagem:** Não suporta burst bem.
 */
class CounterBasedRateLimiter(
    private val config: RateLimitConfig
) {
    private data class Window(
        var count: Int = 0,
        var startTimeMs: Long = Clock.System.now().toEpochMilliseconds()
    )

    private val windows = mutableMapOf<String, Window>()

    fun checkAndConsume(clientId: String): RateLimitCheckResult {
        val now = Clock.System.now().toEpochMilliseconds()
        val window = windows.getOrPut(clientId) { Window(startTimeMs = now) }

        // Verificar se a janela expirou
        if (now - window.startTimeMs >= config.windowSizeMs) {
            // Iniciar nova janela
            window.count = 1
            window.startTimeMs = now
            val resetTime = now + config.windowSizeMs
            return RateLimitCheckResult.Allowed(config.maxRequests - 1, resetTime)
        }

        // Incrementar contador
        window.count++

        return if (window.count <= config.maxRequests) {
            val remaining = config.maxRequests - window.count
            val resetTime = window.startTimeMs + config.windowSizeMs
            RateLimitCheckResult.Allowed(remaining, resetTime)
        } else {
            val resetTime = window.startTimeMs + config.windowSizeMs
            val retryAfter = resetTime - now
            RateLimitCheckResult.RateLimited(resetTime, retryAfter)
        }
    }

    fun resetClient(clientId: String) {
        windows.remove(clientId)
    }

    fun resetAll() {
        windows.clear()
    }
}

/**
 * Agregador de múltiplos rate limiters para diferentes endpoints.
 *
 * Gerencia vários rate limiters, um por endpoint/recurso.
 *
 * **Exemplo:**
 * ```kotlin
 * val limiter = CompositeRateLimiter()
 * limiter.register("flow-execution", RateLimitConfig.flowExecution())
 * limiter.register("flow-save", RateLimitConfig.flowSave())
 *
 * val result = limiter.checkRequest("user-123", "flow-execution")
 * ```
 */
class CompositeRateLimiter {
    private val limiters = mutableMapOf<String, TokenBucketRateLimiter>()

    /**
     * Registra um rate limiter para um endpoint.
     */
    fun register(endpoint: String, config: RateLimitConfig) {
        limiters[endpoint] = TokenBucketRateLimiter(config)
    }

    /**
     * Verifica requisição contra limite do endpoint.
     */
    fun checkRequest(clientId: String, endpoint: String): RateLimitCheckResult {
        val limiter = limiters[endpoint]
            ?: throw IllegalArgumentException("Endpoint not registered: $endpoint")

        return limiter.checkAndConsume(clientId)
    }

    /**
     * Retorna status de um cliente em todos os endpoints.
     */
    fun getClientStatus(clientId: String): Map<String, Map<String, Any>?> {
        return limiters.mapValues { (_, limiter) ->
            limiter.getBucketInfo(clientId)
        }
    }

    /**
     * Limpa clientes inativos em todos os endpoints.
     */
    fun pruneInactiveClients(inactiveForMs: Long): Map<String, Int> {
        return limiters.mapValues { (_, limiter) ->
            limiter.pruneInactiveClients(inactiveForMs)
        }
    }
}

/**
 * Utilitários para headers de rate limiting HTTP.
 *
 * Implementa padrão RateLimit-* headers:
 * - RateLimit-Limit: Máximo de requisições
 * - RateLimit-Remaining: Requisições restantes
 * - RateLimit-Reset: Quando o limite reseta
 * - Retry-After: Quanto aguardar antes de retentar (em caso de limite)
 */
object RateLimitHeaders {
    /**
     * Extrai headers de um resultado Allowed.
     */
    fun extractHeaders(result: RateLimitCheckResult.Allowed): Map<String, String> {
        return mapOf(
            "RateLimit-Limit" to result.remainingRequests.toString(),
            "RateLimit-Remaining" to result.remainingRequests.toString(),
            "RateLimit-Reset" to (result.resetTimeMs / 1000).toString()
        )
    }

    /**
     * Extrai headers de um resultado RateLimited.
     */
    fun extractHeaders(result: RateLimitCheckResult.RateLimited): Map<String, String> {
        return mapOf(
            "Retry-After" to (result.retryAfterMs / 1000).toString(),
            "RateLimit-Reset" to (result.resetTimeMs / 1000).toString()
        )
    }

    /**
     * Retorna corpo de erro padrão para 429 Too Many Requests.
     */
    fun getTooManyRequestsBody(
        result: RateLimitCheckResult.RateLimited
    ): Map<String, Any> {
        return mapOf(
            "error" to "Too Many Requests",
            "status" to 429,
            "retryAfterSeconds" to (result.retryAfterMs / 1000),
            "resetTimeMs" to result.resetTimeMs
        )
    }
}
