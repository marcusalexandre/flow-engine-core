package io.flowmobile.core.observability

import io.flowmobile.core.domain.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Representa um span no contexto de distributed tracing.
 *
 * Um span é a unidade básica de tracing, representando uma operação ou período de tempo.
 */
interface Span {
    /**
     * ID único do span.
     */
    val spanId: String

    /**
     * ID do trace ao qual este span pertence.
     */
    val traceId: String

    /**
     * ID do span pai, ou null se este for o root span.
     */
    val parentSpanId: String?

    /**
     * Nome/descrição do span.
     */
    val name: String

    /**
     * Timestamp de início do span.
     */
    val startTime: Instant

    /**
     * Timestamp de fim do span, ou null se ainda ativo.
     */
    val endTime: Instant?

    /**
     * Adiciona um atributo ao span.
     */
    fun setAttribute(key: String, value: String)

    /**
     * Adiciona um atributo numérico ao span.
     */
    fun setNumericAttribute(key: String, value: Number)

    /**
     * Adiciona um evento ao span.
     */
    fun addEvent(name: String, timestamp: Instant = Clock.System.now())

    /**
     * Marca o span como finalizado.
     */
    fun end()

    /**
     * Indica se o span está ativo (não finalizou ainda).
     */
    val isActive: Boolean
}

/**
 * Implementação básica de Span para testes.
 */
class SimpleSpan(
    override val spanId: String,
    override val traceId: String,
    override val parentSpanId: String? = null,
    override val name: String,
    override val startTime: Instant
) : Span {
    private val attributes = mutableMapOf<String, String>()
    private val events = mutableListOf<Pair<String, Instant>>()
    override var endTime: Instant? = null
    override var isActive: Boolean = true

    override fun setAttribute(key: String, value: String) {
        if (isActive) {
            attributes[key] = value
        }
    }

    override fun setNumericAttribute(key: String, value: Number) {
        if (isActive) {
            attributes[key] = value.toString()
        }
    }

    override fun addEvent(name: String, timestamp: Instant) {
        if (isActive) {
            events.add(name to timestamp)
        }
    }

    override fun end() {
        if (isActive) {
            endTime = Clock.System.now()
            isActive = false
        }
    }

    fun getAttributes(): Map<String, String> = attributes.toMap()
    fun getEvents(): List<Pair<String, Instant>> = events.toList()
}

/**
 * Interface para criar e gerenciar spans em tracing distribuído.
 */
interface Tracer {
    /**
     * Cria um novo span com o nome fornecido.
     *
     * @param name Nome do span
     * @param parentSpan Span pai opcional
     * @return Novo span criado
     */
    fun createSpan(name: String, parentSpan: Span? = null): Span

    /**
     * Obtém o contexto de tracing atual.
     */
    fun getCurrentContext(): String

    /**
     * Define um novo contexto de tracing.
     */
    fun setCurrentContext(context: String)
}

/**
 * Implementação básica de Tracer para testes.
 */
class SimpleTracer : Tracer {
    private var currentTraceId = generateId()
    private var currentContext: String? = null
    private var spanCounter = 0

    override fun createSpan(name: String, parentSpan: Span?): Span {
        val spanId = generateId()
        val span = SimpleSpan(
            spanId = spanId,
            traceId = currentTraceId,
            parentSpanId = parentSpan?.spanId,
            name = name,
            startTime = Clock.System.now()
        )
        return span
    }

    override fun getCurrentContext(): String {
        return currentContext ?: "$currentTraceId-context"
    }

    override fun setCurrentContext(context: String) {
        currentContext = context
    }

    private fun generateId(): String {
        return "${Clock.System.now().toEpochMilliseconds()}-${spanCounter++}"
    }
}

/**
 * Observer que implementa distributed tracing compatível com OpenTelemetry.
 *
 * Cria spans para eventos principais de execução e propaga o contexto de tracing
 * entre serviços, facilitando o acompanhamento de uma execução através de
 * múltiplos componentes e serviços.
 *
 * A estrutura de spans segue o padrão:
 * ```
 * flow.execute
 *   ├── flow.component.start
 *   ├── component.action
 *   │   └── component.action.host_service_call
 *   ├── flow.component.end
 *   └── component.decision
 * ```
 *
 * @property tracer Implementação de tracer
 */
class TracingObserver(
    private val tracer: Tracer = SimpleTracer()
) : ExecutionObserver {

    // Mapear componentes para seus spans
    private val spanMap = mutableMapOf<String, Span>()
    private var executionSpan: Span? = null

    override fun onExecutionStarted(
        flow: Flow,
        context: ExecutionContext,
        timestamp: Instant
    ) {
        executionSpan = tracer.createSpan("flow.execute").apply {
            setAttribute("flow.id", flow.id)
            setAttribute("flow.name", flow.name)
            setAttribute("execution.id", context.executionId)
            setAttribute("component.count", flow.components.size.toString())
        }
    }

    override fun onComponentEnter(
        component: Component,
        context: ExecutionContext,
        timestamp: Instant
    ) {
        val spanName = when (component) {
            is StartComponent -> "component.start"
            is EndComponent -> "component.end"
            is DecisionComponent -> "component.decision"
            is ActionComponent -> "component.action"
            else -> "component.unknown"
        }

        val span = tracer.createSpan(spanName, executionSpan).apply {
            setAttribute("component.id", component.id)
            setAttribute("component.type", component::class.simpleName.orEmpty())
            setAttribute("flow.id", context.flowId)
            setAttribute("execution.id", context.executionId)

            // Adicionar atributos específicos por tipo
            when (component) {
                is ActionComponent -> {
                    val service = (component.properties[ActionComponent.PROPERTY_SERVICE] as? ComponentProperty.StringValue)?.value ?: "unknown"
                    val method = (component.properties[ActionComponent.PROPERTY_METHOD] as? ComponentProperty.StringValue)?.value ?: "unknown"
                    setAttribute("action.service", service)
                    setAttribute("action.method", method)
                }
                is DecisionComponent -> {
                    val condition = (component.properties[DecisionComponent.PROPERTY_CONDITION] as? ComponentProperty.StringValue)?.value ?: "unknown"
                    setAttribute("decision.condition", condition)
                }
                else -> {}
            }
        }

        spanMap[component.id] = span
        addEvent("component_enter", timestamp)
    }

    override fun onComponentExit(
        component: Component,
        result: ExecutionResult,
        context: ExecutionContext,
        timestamp: Instant,
        durationMs: Long
    ) {
        val span = spanMap[component.id]
        span?.apply {
            setAttribute("result.status", if (result.isSuccess()) "SUCCESS" else "FAILURE")
            setNumericAttribute("duration.ms", durationMs)

            if (!result.isSuccess() && result.error != null) {
                setAttribute("error.code", result.error.code)
                setAttribute("error.message", result.error.message)
                addEvent("component_error", timestamp)
            } else {
                addEvent("component_exit", timestamp)
            }

            end()
        }

        spanMap.remove(component.id)
    }

    override fun onContextChanged(
        oldContext: ExecutionContext,
        newContext: ExecutionContext,
        reason: String,
        timestamp: Instant
    ) {
        executionSpan?.apply {
            addEvent("context_changed", timestamp)
            setAttribute("context.change.reason", reason)
            setNumericAttribute("context.variables.count", newContext.variables.size)
        }
    }

    override fun onDecisionEvaluated(
        decision: DecisionComponent,
        evaluatedCondition: String,
        result: Boolean,
        context: ExecutionContext,
        timestamp: Instant
    ) {
        val span = spanMap[decision.id]
        span?.apply {
            setAttribute("decision.condition", evaluatedCondition)
            setAttribute("decision.result", result.toString())
            setAttribute("decision.branch", if (result) "true" else "false")
            addEvent("decision_evaluated", timestamp)
        }
    }

    override fun onExecutionCompleted(
        flow: Flow,
        result: ExecutionResult,
        context: ExecutionContext,
        timestamp: Instant,
        durationMs: Long
    ) {
        executionSpan?.apply {
            setAttribute("execution.status", "COMPLETED")
            setNumericAttribute("execution.duration.ms", durationMs)
            setNumericAttribute("execution.components.executed", context.auditTrail.count { 
                it.action == AuditAction.COMPONENT_COMPLETED
            })
            addEvent("execution_completed", timestamp)
            end()
        }

        executionSpan = null
        spanMap.clear()
    }

    override fun onExecutionFailed(
        flow: Flow,
        error: ExecutionError,
        context: ExecutionContext,
        timestamp: Instant,
        durationMs: Long
    ) {
        executionSpan?.apply {
            setAttribute("execution.status", "FAILED")
            setAttribute("error.code", error.code)
            setAttribute("error.message", error.message)
            setNumericAttribute("execution.duration.ms", durationMs)
            addEvent("execution_failed", timestamp)
            end()
        }

        executionSpan = null
        spanMap.clear()
    }

    override fun onExecutionAborted(
        flow: Flow,
        context: ExecutionContext,
        reason: String,
        timestamp: Instant,
        durationMs: Long
    ) {
        executionSpan?.apply {
            setAttribute("execution.status", "ABORTED")
            setAttribute("abort.reason", reason)
            setNumericAttribute("execution.duration.ms", durationMs)
            addEvent("execution_aborted", timestamp)
            end()
        }

        executionSpan = null
        spanMap.clear()
    }

    /**
     * Adiciona um evento ao span de execução atual.
     */
    private fun addEvent(name: String, timestamp: Instant) {
        executionSpan?.addEvent(name, timestamp)
    }
}
