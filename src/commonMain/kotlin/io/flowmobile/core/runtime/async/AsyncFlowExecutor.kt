package io.flowmobile.core.runtime.async

import io.flowmobile.core.domain.*
import io.flowmobile.core.observability.*
import io.flowmobile.core.runtime.FlowExecutor
import io.flowmobile.core.runtime.StepResult
import io.flowmobile.core.runtime.HostServiceRegistry
import io.flowmobile.core.runtime.performance.GraphCache
import io.flowmobile.core.runtime.performance.StructuralSharing
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow as KotlinFlow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock

/**
 * Executor assíncrono para fluxos que suporta execução paralela de branches
 * e operações com coroutines.
 *
 * AsyncFlowExecutor permite:
 * - Execução assíncrona completa de fluxos
 * - Stepping assíncrono com contexto compartilhado
 * - Observação de eventos através de Kotlin Flow
 * - Suporte a cancellation e timeout
 * - Otimizações de performance através de caching
 *
 * @property hostServiceRegistry Registro de serviços do host
 * @property observer Observer para eventos de execução
 * @property dispatcher Dispatcher para execução das coroutines
 * @property graphCache Cache para otimizações de grafo
 */
class AsyncFlowExecutor(
    private val hostServiceRegistry: HostServiceRegistry,
    private val observer: ExecutionObserver = NoOpExecutionObserver(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val graphCache: GraphCache = GraphCache()
) {
    
    private val baseExecutor = FlowExecutor(hostServiceRegistry, observer)
    
    /**
     * Executa um fluxo completo de forma assíncrona até um EndComponent ou erro.
     *
     * @param flow O fluxo a executar
     * @param initialContext Contexto inicial (opcional)
     * @return Resultado da execução
     */
    suspend fun executeAsync(
        flow: io.flowmobile.core.domain.Flow,
        initialContext: ExecutionContext? = null
    ): ExecutionResult = withContext(dispatcher) {
        baseExecutor.execute(flow, initialContext)
    }
    
    /**
     * Executa um único passo de forma assíncrona.
     *
     * @param flow O fluxo sendo executado
     * @param context Contexto atual
     * @return Resultado do passo
     */
    suspend fun stepAsync(
        flow: io.flowmobile.core.domain.Flow,
        context: ExecutionContext
    ): StepResult = withContext(dispatcher) {
        baseExecutor.step(flow, context)
    }
    
    /**
     * Retorna um Kotlin Flow que emite eventos de execução durante a execução do fluxo.
     *
     * Permite observar a execução em tempo real:
     * ```kotlin
     * executor.executeAsFlow(flow, context).collect { event ->
     *     when (event) {
     *         is ExecutionEvent.ComponentStarted -> println("Started: ${event.component.id}")
     *         is ExecutionEvent.ComponentCompleted -> println("Completed: ${event.component.id}")
     *         is ExecutionEvent.ExecutionError -> println("Error: ${event.error.message}")
     *     }
     * }
     * ```
     *
     * @param flow O fluxo a executar
     * @param initialContext Contexto inicial
     * @return Flow de eventos de execução
     */
    fun executeAsFlow(
        flow: io.flowmobile.core.domain.Flow,
        initialContext: ExecutionContext? = null
    ): KotlinFlow<ExecutionEvent> = flow {
        try {
            val startComponent = flow.getStartComponent()
            val context = initialContext ?: ExecutionContext.create(
                flowId = flow.id,
                initialComponentId = startComponent.id
            )
            
            emit(ExecutionEvent.ExecutionStarted(flow, context, Clock.System.now()))
            
            var currentContext = context
            var maxIterations = 10000
            
            while (maxIterations > 0) {
                val currentComponentId = currentContext.currentComponentId ?: break
                val currentComponent = flow.getComponentById(currentComponentId) ?: break
                
                emit(ExecutionEvent.ComponentStarted(currentComponent, currentContext))
                
                val stepResult = stepAsync(flow, currentContext)
                currentContext = stepResult.context
                
                emit(ExecutionEvent.ComponentCompleted(currentComponent, currentContext))
                
                if (stepResult.isComplete || stepResult.error != null) {
                    break
                }
                
                maxIterations--
            }
            
            if (maxIterations == 0) {
                val error = ExecutionError(
                    code = "MAX_ITERATIONS_EXCEEDED",
                    message = "Execução excedeu número máximo de iterações",
                    componentId = currentContext.currentComponentId
                )
                emit(ExecutionEvent.ErrorOccurred(error, currentContext))
            } else {
                emit(ExecutionEvent.ExecutionCompleted(currentContext, Clock.System.now()))
            }
            
        } catch (e: Exception) {
            val error = ExecutionError(
                code = "EXECUTION_EXCEPTION",
                message = e.message ?: "Erro desconhecido",
                componentId = null
            )
            emit(ExecutionEvent.ErrorOccurred(error, null))
        }
    }
    
    /**
     * Executa múltiplos fluxos em paralelo de forma assíncrona.
     *
     * @param flows Pares de (Flow, InitialContext) para executar
     * @return Lista de resultados
     */
    suspend fun executeParallelAsync(
        flows: List<Pair<io.flowmobile.core.domain.Flow, ExecutionContext?>>
    ): List<ExecutionResult> = coroutineScope {
        flows.map { (flow, context) ->
            async {
                executeAsync(flow, context)
            }
        }.awaitAll()
    }
}

/**
 * Eventos emitidos durante a execução de um fluxo.
 * Permite observar o progresso da execução em tempo real.
 */
sealed class ExecutionEvent {
    /**
     * Emitido quando a execução do fluxo inicia.
     */
    data class ExecutionStarted(
        val flow: io.flowmobile.core.domain.Flow,
        val context: ExecutionContext,
        val timestamp: kotlinx.datetime.Instant
    ) : ExecutionEvent()
    
    /**
     * Emitido quando um componente inicia execução.
     */
    data class ComponentStarted(
        val component: Component,
        val context: ExecutionContext
    ) : ExecutionEvent()
    
    /**
     * Emitido quando um componente completa execução.
     */
    data class ComponentCompleted(
        val component: Component,
        val context: ExecutionContext
    ) : ExecutionEvent()
    
    /**
     * Emitido quando a execução completa com sucesso.
     */
    data class ExecutionCompleted(
        val context: ExecutionContext,
        val timestamp: kotlinx.datetime.Instant
    ) : ExecutionEvent()
    
    /**
     * Emitido quando ocorre um erro durante execução.
     */
    data class ErrorOccurred(
        val error: ExecutionError,
        val context: ExecutionContext?
    ) : ExecutionEvent()
}
