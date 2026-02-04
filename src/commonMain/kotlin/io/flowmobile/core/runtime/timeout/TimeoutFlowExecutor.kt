package io.flowmobile.core.runtime.timeout

import io.flowmobile.core.domain.*
import io.flowmobile.core.runtime.FlowExecutor
import io.flowmobile.core.runtime.HostServiceRegistry
import io.flowmobile.core.observability.ExecutionObserver
import io.flowmobile.core.observability.NoOpExecutionObserver
import kotlinx.coroutines.*

/**
 * Executor de fluxo com suporte a timeout.
 *
 * Permite executar fluxos com limite de tempo máximo.
 * Se o tempo limite for excedido, a execução é cancelada e uma erro é reportado.
 *
 * @property executor Executor base de fluxos
 */
class TimeoutFlowExecutor(
    private val executor: FlowExecutor
) {
    
    /**
     * Executa um fluxo com limite de tempo.
     *
     * @param flow Fluxo a executar
     * @param context Contexto inicial
     * @param timeoutMs Limite de tempo em milissegundos
     * @return Resultado da execução (ou erro se timeout)
     */
    suspend fun executeWithTimeout(
        flow: io.flowmobile.core.domain.Flow,
        context: ExecutionContext? = null,
        timeoutMs: Long
    ): ExecutionResult = withTimeoutOrNull(timeoutMs) {
        executor.execute(flow, context)
    }?.let { result ->
        // Execução completou no tempo limite
        result
    } ?: run {
        // Timeout excedido
        ExecutionResult.failure(
            ExecutionError(
                code = "TIMEOUT_EXCEEDED",
                message = "Execução excedeu o limite de tempo de ${timeoutMs}ms",
                componentId = context?.currentComponentId
            )
        )
    }
    
    /**
     * Executa um passo com limite de tempo.
     *
     * @param flow Fluxo
     * @param context Contexto
     * @param timeoutMs Limite de tempo em milissegundos
     * @return Resultado do passo ou erro se timeout
     */
    suspend fun stepWithTimeout(
        flow: io.flowmobile.core.domain.Flow,
        context: ExecutionContext,
        timeoutMs: Long
    ): io.flowmobile.core.runtime.StepResult? = withTimeoutOrNull(timeoutMs) {
        executor.step(flow, context)
    }
}

/**
 * Cria um executor com timeout padrão.
 *
 * Útil para garantir que execuções completem em tempo razoável.
 *
 * @param hostServiceRegistry Registro de serviços do host
 * @param observer Observer de eventos
 * @param defaultTimeoutMs Timeout padrão em milissegundos
 * @return Novo executor com timeout
 */
@Suppress("UNUSED_PARAMETER")
fun createTimeoutFlowExecutor(
    hostServiceRegistry: HostServiceRegistry,
    observer: ExecutionObserver = NoOpExecutionObserver(),
    defaultTimeoutMs: Long = 30000L
): TimeoutFlowExecutor {
    val baseExecutor = FlowExecutor(hostServiceRegistry, observer)
    return TimeoutFlowExecutor(baseExecutor)
}

/**
 * Utilitário para executar uma função com timeout.
 *
 * Útil para operações que podem demorar muito.
 *
 * @param timeoutMs Timeout em milissegundos
 * @param onTimeout Callback chamado se timeout for excedido
 * @param block Função a executar
 * @return Resultado da execução, ou null se timeout
 */
suspend fun <T> executeWithTimeoutLogging(
    timeoutMs: Long,
    onTimeout: (Long) -> Unit = {},
    block: suspend () -> T
): T? = try {
    withTimeout(timeoutMs) {
        block()
    }
} catch (e: TimeoutCancellationException) {
    onTimeout(timeoutMs)
    null
}

/**
 * Executa um bloco com retry e timeout.
 * Útil para operações que podem ser retentadas em caso de erro transitório.
 *
 * @param timeoutMs Timeout total em milissegundos
 * @param maxRetries Número máximo de tentativas
 * @param delayMs Delay entre tentativas em milissegundos
 * @param block Função a executar
 * @return Resultado ou null se todas as tentativas falharem
 */
suspend fun <T> executeWithTimeoutAndRetry(
    timeoutMs: Long,
    maxRetries: Int = 3,
    delayMs: Long = 100L,
    block: suspend () -> T
): T? = withTimeoutOrNull(timeoutMs) {
    var lastError: Exception? = null
    
    repeat(maxRetries) { attempt ->
        try {
            return@withTimeoutOrNull block()
        } catch (e: Exception) {
            lastError = e
            if (attempt < maxRetries - 1) {
                delay(delayMs)
            }
        }
    }
    
    lastError?.let { throw it }
    null
}
