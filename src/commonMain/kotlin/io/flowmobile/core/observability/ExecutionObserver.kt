package io.flowmobile.core.observability

import io.flowmobile.core.domain.*
import kotlinx.datetime.Instant

/**
 * Interface para observação de eventos durante a execução de fluxos.
 *
 * Implementadores podem observar todos os eventos de ciclo de vida de uma execução
 * para propósitos de logging, métricas, tracing distribuído, auditoria, etc.
 *
 * Todas as implementações devem ser:
 * - Thread-safe (múltiplos executores podem chamar em paralelo)
 * - Não-bloqueantes ou com timeout adequado (não devem impedir execução)
 * - Idempotentes (pode ser chamada múltiplas vezes com mesmos argumentos)
 */
interface ExecutionObserver {

    /**
     * Chamado quando uma execução de fluxo é iniciada.
     *
     * @param flow O fluxo sendo executado
     * @param context O contexto inicial da execução
     * @param timestamp Instant de início
     */
    fun onExecutionStarted(
        flow: Flow,
        context: ExecutionContext,
        timestamp: Instant
    )

    /**
     * Chamado quando um componente é entrado (inicia sua execução).
     *
     * @param component O componente sendo entrado
     * @param context O contexto atual da execução
     * @param timestamp Instant de entrada
     */
    fun onComponentEnter(
        component: Component,
        context: ExecutionContext,
        timestamp: Instant
    )

    /**
     * Chamado quando um componente é saído (completa sua execução).
     *
     * @param component O componente que foi saído
     * @param result Resultado da execução do componente
     * @param context O contexto atualizado após execução
     * @param timestamp Instant de saída
     * @param durationMs Duração da execução do componente em ms
     */
    fun onComponentExit(
        component: Component,
        result: ExecutionResult,
        context: ExecutionContext,
        timestamp: Instant,
        durationMs: Long
    )

    /**
     * Chamado quando o contexto é modificado.
     *
     * Útil para rastreamento de mudanças de variáveis durante a execução.
     *
     * @param oldContext Contexto anterior
     * @param newContext Contexto novo
     * @param reason Motivo da mudança (ex: "variable_assigned", "component_completed")
     * @param timestamp Instant da mudança
     */
    fun onContextChanged(
        oldContext: ExecutionContext,
        newContext: ExecutionContext,
        reason: String,
        timestamp: Instant
    )

    /**
     * Chamado quando uma decisão (DecisionComponent) é avaliada.
     *
     * @param decision O componente de decisão
     * @param evaluatedCondition A condição avaliada
     * @param result O resultado booleano da avaliação
     * @param context O contexto de execução
     * @param timestamp Instant da avaliação
     */
    fun onDecisionEvaluated(
        decision: DecisionComponent,
        evaluatedCondition: String,
        result: Boolean,
        context: ExecutionContext,
        timestamp: Instant
    )

    /**
     * Chamado quando uma execução é completada com sucesso.
     *
     * @param flow O fluxo executado
     * @param result O resultado final da execução
     * @param context O contexto final
     * @param timestamp Instant de conclusão
     * @param durationMs Duração total da execução em ms
     */
    fun onExecutionCompleted(
        flow: Flow,
        result: ExecutionResult,
        context: ExecutionContext,
        timestamp: Instant,
        durationMs: Long
    )

    /**
     * Chamado quando uma execução falha com erro.
     *
     * @param flow O fluxo que estava sendo executado
     * @param error O erro que ocorreu
     * @param context O contexto no momento do erro
     * @param timestamp Instant do erro
     * @param durationMs Duração até o erro em ms
     */
    fun onExecutionFailed(
        flow: Flow,
        error: ExecutionError,
        context: ExecutionContext,
        timestamp: Instant,
        durationMs: Long
    )

    /**
     * Chamado quando uma execução é interrompida/cancelada.
     *
     * @param flow O fluxo que estava sendo executado
     * @param context O contexto no momento da interrupção
     * @param reason Motivo da interrupção
     * @param timestamp Instant da interrupção
     * @param durationMs Duração até a interrupção em ms
     */
    fun onExecutionAborted(
        flow: Flow,
        context: ExecutionContext,
        reason: String,
        timestamp: Instant,
        durationMs: Long
    )
}

/**
 * Observer nulo para evitar chamadas null-safe em locais que não precisam de observabilidade.
 */
class NoOpExecutionObserver : ExecutionObserver {
    override fun onExecutionStarted(flow: Flow, context: ExecutionContext, timestamp: Instant) {}
    override fun onComponentEnter(component: Component, context: ExecutionContext, timestamp: Instant) {}
    override fun onComponentExit(component: Component, result: ExecutionResult, context: ExecutionContext, timestamp: Instant, durationMs: Long) {}
    override fun onContextChanged(oldContext: ExecutionContext, newContext: ExecutionContext, reason: String, timestamp: Instant) {}
    override fun onDecisionEvaluated(decision: DecisionComponent, evaluatedCondition: String, result: Boolean, context: ExecutionContext, timestamp: Instant) {}
    override fun onExecutionCompleted(flow: Flow, result: ExecutionResult, context: ExecutionContext, timestamp: Instant, durationMs: Long) {}
    override fun onExecutionFailed(flow: Flow, error: ExecutionError, context: ExecutionContext, timestamp: Instant, durationMs: Long) {}
    override fun onExecutionAborted(flow: Flow, context: ExecutionContext, reason: String, timestamp: Instant, durationMs: Long) {}
}

/**
 * Compositor que permite registrar múltiplos observers e notifica todos.
 */
class CompositeExecutionObserver(
    private val observers: List<ExecutionObserver> = emptyList()
) : ExecutionObserver {

    fun addObserver(observer: ExecutionObserver): CompositeExecutionObserver {
        return CompositeExecutionObserver(observers + observer)
    }

    override fun onExecutionStarted(flow: Flow, context: ExecutionContext, timestamp: Instant) {
        observers.forEach { it.onExecutionStarted(flow, context, timestamp) }
    }

    override fun onComponentEnter(component: Component, context: ExecutionContext, timestamp: Instant) {
        observers.forEach { it.onComponentEnter(component, context, timestamp) }
    }

    override fun onComponentExit(component: Component, result: ExecutionResult, context: ExecutionContext, timestamp: Instant, durationMs: Long) {
        observers.forEach { it.onComponentExit(component, result, context, timestamp, durationMs) }
    }

    override fun onContextChanged(oldContext: ExecutionContext, newContext: ExecutionContext, reason: String, timestamp: Instant) {
        observers.forEach { it.onContextChanged(oldContext, newContext, reason, timestamp) }
    }

    override fun onDecisionEvaluated(decision: DecisionComponent, evaluatedCondition: String, result: Boolean, context: ExecutionContext, timestamp: Instant) {
        observers.forEach { it.onDecisionEvaluated(decision, evaluatedCondition, result, context, timestamp) }
    }

    override fun onExecutionCompleted(flow: Flow, result: ExecutionResult, context: ExecutionContext, timestamp: Instant, durationMs: Long) {
        observers.forEach { it.onExecutionCompleted(flow, result, context, timestamp, durationMs) }
    }

    override fun onExecutionFailed(flow: Flow, error: ExecutionError, context: ExecutionContext, timestamp: Instant, durationMs: Long) {
        observers.forEach { it.onExecutionFailed(flow, error, context, timestamp, durationMs) }
    }

    override fun onExecutionAborted(flow: Flow, context: ExecutionContext, reason: String, timestamp: Instant, durationMs: Long) {
        observers.forEach { it.onExecutionAborted(flow, context, reason, timestamp, durationMs) }
    }
}
