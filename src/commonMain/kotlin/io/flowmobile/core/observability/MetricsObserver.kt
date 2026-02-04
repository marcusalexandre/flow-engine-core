package io.flowmobile.core.observability

import io.flowmobile.core.domain.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.max
import kotlin.math.min

/**
 * Tipos de métricas coletadas pelo observador de métricas.
 */
sealed class Metric {
    abstract val name: String
    abstract val timestamp: Instant
    abstract val tags: Map<String, String>

    data class Counter(
        override val name: String,
        val value: Long = 1,
        override val timestamp: Instant,
        override val tags: Map<String, String> = emptyMap()
    ) : Metric()

    data class Histogram(
        override val name: String,
        val value: Double,
        override val timestamp: Instant,
        override val tags: Map<String, String> = emptyMap()
    ) : Metric()

    data class Gauge(
        override val name: String,
        val value: Double,
        override val timestamp: Instant,
        override val tags: Map<String, String> = emptyMap()
    ) : Metric()
}

/**
 * Interface para exportação de métricas.
 */
interface MetricExporter {
    fun export(metrics: List<Metric>)
}

/**
 * Implementação em memória de exportador de métricas (para testes).
 */
class InMemoryMetricExporter : MetricExporter {
    private val _metrics = mutableListOf<Metric>()
    val metrics: List<Metric> get() = _metrics.toList()

    override fun export(metrics: List<Metric>) {
        _metrics.addAll(metrics)
    }

    fun clear() {
        _metrics.clear()
    }
}

/**
 * Agregador de histogramas para calcular percentis.
 */
data class HistogramStats(
    val count: Long = 0,
    val sum: Double = 0.0,
    val min: Double = Double.POSITIVE_INFINITY,
    val max: Double = Double.NEGATIVE_INFINITY,
    val values: List<Double> = emptyList()
) {
    val mean: Double get() = if (count > 0) sum / count else 0.0
    
    val p50: Double get() = percentile(0.5)
    val p95: Double get() = percentile(0.95)
    val p99: Double get() = percentile(0.99)

    fun percentile(p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = ((p * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    fun add(value: Double): HistogramStats {
        return HistogramStats(
            count = count + 1,
            sum = sum + value,
            min = min(min, value),
            max = max(max, value),
            values = values + value
        )
    }
}

/**
 * Observer que coleta métricas de execução para monitoramento e análise de performance.
 *
 * Coleta métricas como:
 * - Total e duração de execuções de fluxo
 * - Total e duração de execuções de componentes
 * - Branches tomados em decisões
 * - Erros por tipo
 * - Tamanho do contexto
 *
 * @property exporter Implementação que exporta as métricas coletadas
 */
class MetricsObserver(
    private val exporter: MetricExporter = InMemoryMetricExporter()
) : ExecutionObserver {

    // Contadores
    private val flowExecutionCounter = mutableMapOf<String, Long>()
    private val componentExecutionCounter = mutableMapOf<String, Long>()
    private val decisionBranchCounter = mutableMapOf<String, Long>()
    private val errorCounter = mutableMapOf<String, Long>()

    // Histogramas
    private val flowExecutionDuration = mutableMapOf<String, HistogramStats>()
    private val componentExecutionDuration = mutableMapOf<String, HistogramStats>()

    // Gauges
    private val contextSize = mutableMapOf<String, Double>()

    override fun onExecutionStarted(
        flow: Flow,
        context: ExecutionContext,
        timestamp: Instant
    ) {
        // Nada a fazer no início
    }

    override fun onComponentEnter(
        component: Component,
        context: ExecutionContext,
        timestamp: Instant
    ) {
        // Nada a fazer na entrada (duração será medida na saída)
    }

    override fun onComponentExit(
        component: Component,
        result: ExecutionResult,
        context: ExecutionContext,
        timestamp: Instant,
        durationMs: Long
    ) {
        val componentType = component::class.simpleName.orEmpty()
        val key = "${context.flowId}:${componentType}"

        // Incrementar contador de execução
        componentExecutionCounter[key] = (componentExecutionCounter[key] ?: 0) + 1

        // Registrar duração
        val stats = componentExecutionDuration[key] ?: HistogramStats()
        componentExecutionDuration[key] = stats.add(durationMs.toDouble())

        // Registrar tamanho do contexto
        val contextBytes = estimateContextSize(context)
        contextSize[key] = contextBytes.toDouble()
    }

    override fun onContextChanged(
        oldContext: ExecutionContext,
        newContext: ExecutionContext,
        reason: String,
        timestamp: Instant
    ) {
        // Nada a fazer (tamanho será registrado no exit de componente)
    }

    override fun onDecisionEvaluated(
        decision: DecisionComponent,
        evaluatedCondition: String,
        result: Boolean,
        context: ExecutionContext,
        timestamp: Instant
    ) {
        val branch = if (result) "true" else "false"
        val key = "${context.flowId}:${decision.id}:${branch}"
        decisionBranchCounter[key] = (decisionBranchCounter[key] ?: 0) + 1
    }

    override fun onExecutionCompleted(
        flow: Flow,
        result: ExecutionResult,
        context: ExecutionContext,
        timestamp: Instant,
        durationMs: Long
    ) {
        val key = flow.id

        // Incrementar contador de execução bem-sucedida
        flowExecutionCounter[key] = (flowExecutionCounter[key] ?: 0) + 1

        // Registrar duração
        val stats = flowExecutionDuration[key] ?: HistogramStats()
        flowExecutionDuration[key] = stats.add(durationMs.toDouble())

        // Exportar métricas
        exportMetrics()
    }

    override fun onExecutionFailed(
        flow: Flow,
        error: ExecutionError,
        context: ExecutionContext,
        timestamp: Instant,
        durationMs: Long
    ) {
        val key = error.code
        errorCounter[key] = (errorCounter[key] ?: 0) + 1

        // Exportar métricas
        exportMetrics()
    }

    override fun onExecutionAborted(
        flow: Flow,
        context: ExecutionContext,
        reason: String,
        timestamp: Instant,
        durationMs: Long
    ) {
        val key = "aborted:${reason}"
        errorCounter[key] = (errorCounter[key] ?: 0) + 1

        // Exportar métricas
        exportMetrics()
    }

    /**
     * Estima o tamanho do contexto em bytes.
     */
    private fun estimateContextSize(context: ExecutionContext): Long {
        var size = 0L

        // Tamanho estimado de cada variável
        context.variables.forEach { (key, value) ->
            size += key.length * 2 // UTF-16
            size += estimateVariableSize(value)
        }

        // Tamanho do audit trail
        size += context.auditTrail.size * 256 // Estimativa por entrada

        return size
    }

    /**
     * Estima o tamanho de uma variável em bytes.
     */
    private fun estimateVariableSize(value: VariableValue): Long {
        return when (value) {
            is VariableValue.StringValue -> value.value.length.toLong() * 2
            is VariableValue.NumberValue -> 16L
            is VariableValue.BooleanValue -> 1L
            is VariableValue.NullValue -> 1L
            is VariableValue.ObjectValue -> value.value.values.sumOf { estimateVariableSize(it) } + 64
            is VariableValue.ArrayValue -> value.value.sumOf { estimateVariableSize(it) } + 64
        }
    }

    /**
     * Exporta as métricas coletadas.
     */
    private fun exportMetrics() {
        val metrics = mutableListOf<Metric>()
        val timestamp = Clock.System.now()

        // Exportar contadores
        flowExecutionCounter.forEach { (key, value) ->
            metrics.add(
                Metric.Counter(
                    name = "flow_execution_total",
                    value = value,
                    timestamp = timestamp,
                    tags = mapOf("flow" to key)
                )
            )
        }

        componentExecutionCounter.forEach { (key, value) ->
            metrics.add(
                Metric.Counter(
                    name = "component_execution_total",
                    value = value,
                    timestamp = timestamp,
                    tags = mapOf("component" to key)
                )
            )
        }

        decisionBranchCounter.forEach { (key, value) ->
            metrics.add(
                Metric.Counter(
                    name = "decision_branch_total",
                    value = value,
                    timestamp = timestamp,
                    tags = mapOf("decision" to key)
                )
            )
        }

        errorCounter.forEach { (key, value) ->
            metrics.add(
                Metric.Counter(
                    name = "flow_error_total",
                    value = value,
                    timestamp = timestamp,
                    tags = mapOf("error_type" to key)
                )
            )
        }

        // Exportar histogramas (apenas P50, P95, P99)
        flowExecutionDuration.forEach { (key, stats) ->
            metrics.add(
                Metric.Histogram(
                    name = "flow_execution_duration_ms_p50",
                    value = stats.p50,
                    timestamp = timestamp,
                    tags = mapOf("flow" to key)
                )
            )
            metrics.add(
                Metric.Histogram(
                    name = "flow_execution_duration_ms_p95",
                    value = stats.p95,
                    timestamp = timestamp,
                    tags = mapOf("flow" to key)
                )
            )
            metrics.add(
                Metric.Histogram(
                    name = "flow_execution_duration_ms_p99",
                    value = stats.p99,
                    timestamp = timestamp,
                    tags = mapOf("flow" to key)
                )
            )
        }

        componentExecutionDuration.forEach { (key, stats) ->
            metrics.add(
                Metric.Histogram(
                    name = "component_execution_duration_ms_p50",
                    value = stats.p50,
                    timestamp = timestamp,
                    tags = mapOf("component" to key)
                )
            )
            metrics.add(
                Metric.Histogram(
                    name = "component_execution_duration_ms_p95",
                    value = stats.p95,
                    timestamp = timestamp,
                    tags = mapOf("component" to key)
                )
            )
        }

        // Exportar gauges
        contextSize.forEach { (key, size) ->
            metrics.add(
                Metric.Gauge(
                    name = "context_size_bytes",
                    value = size,
                    timestamp = timestamp,
                    tags = mapOf("flow" to key)
                )
            )
        }

        if (metrics.isNotEmpty()) {
            exporter.export(metrics)
        }
    }
}
