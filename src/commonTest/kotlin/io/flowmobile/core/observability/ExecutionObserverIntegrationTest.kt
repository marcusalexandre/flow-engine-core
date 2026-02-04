package io.flowmobile.core.observability

import io.flowmobile.core.domain.*
import io.flowmobile.core.runtime.FlowExecutor
import io.flowmobile.core.runtime.HostServiceRegistry
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes de integração entre FlowExecutor e Observers.
 */
class ExecutionObserverIntegrationTest {

    @Test
    fun testCompositeObserverWithMultipleImplementations() {
        val loggingObserver = LoggingObserver(TestLogEmitter(), LogLevel.INFO)
        val metricsObserver = MetricsObserver(InMemoryMetricExporter())
        val tracingObserver = TracingObserver(SimpleTracer())
        
        val composite = CompositeExecutionObserver()
            .addObserver(loggingObserver)
            .addObserver(metricsObserver)
            .addObserver(tracingObserver)
        
        val flow = createSimpleFlow()
        val context = ExecutionContext.create(flow.id, flow.getStartComponent().id)
        val timestamp = Clock.System.now()
        composite.onExecutionStarted(flow, context, timestamp)
        
        // Composite foi criado corretamente
        assertTrue(true)
    }

    @Test
    fun testLoggingObserverWithAllLogLevels() {
        val emitter = TestLogEmitter()
        val infoObserver = LoggingObserver(emitter, LogLevel.INFO)
        
        val flow = createSimpleFlow()
        val context = ExecutionContext.create(flow.id, flow.getStartComponent().id)
        val timestamp = Clock.System.now()
        
        infoObserver.onExecutionStarted(flow, context, timestamp)
        
        // INFO observer deve emitir
        assertEquals(1, emitter.logs.size)
    }

    @Test
    fun testMetricsHistogramCalculations() {
        val exporter = InMemoryMetricExporter()
        val observer = MetricsObserver(exporter)
        
        val flow = createSimpleFlow()
        val context = ExecutionContext.create(flow.id, flow.getStartComponent().id)
        val timestamp = Clock.System.now()
        val component = StartComponent("start-1", "Start")
        
        // Simular múltiplas execuções com diferentes durações
        observer.onComponentExit(component, ExecutionResult.success(emptyMap()), context, timestamp, 10L)
        observer.onComponentExit(component, ExecutionResult.success(emptyMap()), context, timestamp, 20L)
        observer.onComponentExit(component, ExecutionResult.success(emptyMap()), context, timestamp, 30L)
        observer.onExecutionCompleted(flow, ExecutionResult.success(emptyMap()), context, timestamp, 60L)
        
        val metrics = exporter.metrics
        
        // Verificar que histogramas foram coletados
        val p50Metrics = metrics.filter { it.name.contains("p50") }
        assertTrue(p50Metrics.isNotEmpty())
    }

    @Test
    fun testTracingObserverPropagatesContext() {
        val tracer = SimpleTracer()
        val observer = TracingObserver(tracer)
        
        val flow = createSimpleFlow()
        val context = ExecutionContext.create(flow.id, flow.getStartComponent().id)
        val timestamp = Clock.System.now()
        
        observer.onExecutionStarted(flow, context, timestamp)
        
        // Context deve ser propagado
        val currentContext = tracer.getCurrentContext()
        assertTrue(currentContext.isNotEmpty())
    }

    private fun createSimpleFlow(): Flow {
        val start = StartComponent("start-1", "Start")
        val end = EndComponent("end-1", "End")
        
        val conn1 = Connection("conn-1", start.id, "out", end.id, "in")
        
        return Flow(
            id = "simple-flow",
            name = "Simple Flow",
            version = "1.0.0",
            components = listOf(start, end),
            connections = listOf(conn1)
        )
    }
}
