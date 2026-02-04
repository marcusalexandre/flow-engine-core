package io.flowmobile.core.runtime.cancellation

import kotlinx.coroutines.*

/**
 * Interface para gerenciar execução cancelável de fluxos.
 *
 * Permite monitorar o status de execução e solicitar cancelamento de uma execução em progresso.
 * O cancelamento é cooperativo - o executor deve checar frequentemente se foi solicitado
 * e parar a execução de forma limpa.
 */
interface CancellableExecution {
    
    /**
     * Indica se a execução ainda está ativa (não foi cancelada).
     */
    val isActive: Boolean
    
    /**
     * Indica se foi solicitado cancelamento.
     */
    val isCancellationRequested: Boolean
    
    /**
     * Obtém a razão do cancelamento, se houver.
     */
    val cancellationReason: String?
    
    /**
     * Solicita cancelamento da execução com uma razão.
     *
     * @param reason Descrição do motivo do cancelamento
     */
    fun cancel(reason: String)
    
    /**
     * Aguarda até que o cancelamento seja solicitado ou completa voluntariamente.
     * Lança CancellationException se o cancelamento for solicitado.
     *
     * @throws CancellationException se o cancelamento foi solicitado
     */
    suspend fun awaitCancellation()
    
    /**
     * Executa um bloco de código e verifica se cancelamento foi solicitado.
     * Se cancelamento foi solicitado, lança CancellationException.
     *
     * @param block Bloco de código a executar
     * @throws CancellationException se cancelamento foi solicitado
     */
    suspend fun <T> runIfActive(block: suspend () -> T): T {
        if (isCancellationRequested) {
            throw CancellationException(cancellationReason ?: "Execução cancelada")
        }
        return block()
    }
}

/**
 * Implementação padrão de CancellableExecution usando Kotlin Job.
 *
 * Vincula o cancelamento com o Job da coroutine, permitindo que o escopo
 * externo cancele a execução através de cancel() do Job.
 */
class JobBasedCancellableExecution(
    private val job: Job
) : CancellableExecution {
    
    private var _cancellationReason: String? = null
    
    override val isActive: Boolean
        get() = job.isActive
    
    override val isCancellationRequested: Boolean
        get() = !job.isActive || _cancellationReason != null
    
    override val cancellationReason: String?
        get() = _cancellationReason
    
    override fun cancel(reason: String) {
        _cancellationReason = reason
        job.cancel(reason)
    }
    
    override suspend fun awaitCancellation() {
        try {
            job.join()
        } catch (e: CancellationException) {
            throw e
        }
    }
}

/**
 * Contexto para gerenciar execução cancelável com suporte a polling.
 *
 * Útil para executores que precisam verificar frequentemente se cancelamento foi solicitado.
 */
class CancellationContext : CancellableExecution {
    
    private var _isCancellationRequested = false
    private var _cancellationReason: String? = null
    private val job = Job()
    
    override val isActive: Boolean
        get() = !_isCancellationRequested && job.isActive
    
    override val isCancellationRequested: Boolean
        get() = _isCancellationRequested
    
    override val cancellationReason: String?
        get() = _cancellationReason
    
    override fun cancel(reason: String) {
        _isCancellationRequested = true
        _cancellationReason = reason
        job.cancel(reason)
    }
    
    override suspend fun awaitCancellation() {
        if (_isCancellationRequested) {
            throw CancellationException(_cancellationReason ?: "Execução cancelada")
        }
        try {
            job.join()
        } catch (e: CancellationException) {
            throw e
        }
    }
}

/**
 * Executor assíncrono com suporte a cancelamento.
 *
 * Permite executar operações longas com possibilidade de cancelamento.
 */
class CancellableExecutor {
    
    /**
     * Executa um bloco suspendido com suporte a cancelamento.
     *
     * @param block Bloco a executar
     * @return CancellableExecution para gerenciar a execução
     */
    fun <T> executeWithCancellation(
        scope: CoroutineScope,
        block: suspend (CancellableExecution) -> T
    ) {
        val context = CancellationContext()
        scope.launch {
            try {
                block(context)
            } catch (e: CancellationException) {
                // Cancelamento solicitado, propagar
                throw e
            }
        }
    }
}

/**
 * Cria um scope coroutine com suporte a cancelamento.
 *
 * @param block Bloco a executar no scope
 * @return CancellableExecution para gerenciar a execução
 */
suspend fun <T> withCancellableScope(
    block: suspend (CancellableExecution) -> T
): T {
    val context = CancellationContext()
    return try {
        block(context)
    } finally {
        context.cancel("Scope finalizado")
    }
}
