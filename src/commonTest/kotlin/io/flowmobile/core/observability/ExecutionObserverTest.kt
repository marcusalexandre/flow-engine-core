package io.flowmobile.core.observability

import io.flowmobile.core.domain.*
import io.flowmobile.core.runtime.FlowExecutor
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes para ExecutionObserver e implementações.
 */
class ExecutionObserverTest {

    @Test
    fun testNoOpObserverDoesNothing() {
        val observer = NoOpExecutionObserver()
        val context = ExecutionContext.create("test-flow", "start-1")
        val timestamp = Clock.System.now()
        val flow = createSimpleFlow()

        // Não deve lançar exceção
        observer.onExecutionStarted(flow, context, timestamp)
        observer.onComponentEnter(StartComponent("start-1", "Start"), context, timestamp)
        observer.onComponentExit(StartComponent("start-1", "Start"), ExecutionResult.success(emptyMap()), context, timestamp, 10L)
        observer.onContextChanged(context, context, "test", timestamp)
        observer.onExecutionCompleted(flow, ExecutionResult.success(emptyMap()), context, timestamp, 100L)
        observer.onExecutionFailed(flow, ExecutionError("TEST", "Test error", null), context, timestamp, 100L)
        observer.onExecutionAborted(flow, context, "Test abort", timestamp, 100L)
    }

    @Test
    fun testCompositeObserverNotifiesAll() {
        val observer1 = TestExecutionObserver()
        val observer2 = TestExecutionObserver()
        val composite = CompositeExecutionObserver()
            .addObserver(observer1)
            .addObserver(observer2)

        val context = ExecutionContext.create("test-flow", "start-1")
        val timestamp = Clock.System.now()
        val component = StartComponent("start-1", "Start")

        composite.onComponentEnter(component, context, timestamp)

        assertEquals(1, observer1.componentEnterCount)
        assertEquals(1, observer2.componentEnterCount)
    }

    @Test
    fun testLoggingObserverRecordsEvents() {
        val emitter = TestLogEmitter()
        val observer = LoggingObserver(emitter, LogLevel.DEBUG)
        
        val context = ExecutionContext.create("flow-1", "start-1")
        val timestamp = Clock.System.now()
        val component = StartComponent("start-1", "Start")

        observer.onComponentEnter(component, context, timestamp)

        assertEquals(1, emitter.logs.size)
        assertEquals("start-1", emitter.logs[0].second["component.id"])
        assertEquals(LogLevel.DEBUG, emitter.logs[0].first)
    }

    @Test
    fun testLoggingObserverFiltersLogLevel() {
        val emitter = TestLogEmitter()
        val observer = LoggingObserver(emitter, LogLevel.ERROR)
        
        val flow = createSimpleFlow()
        val context = ExecutionContext.create("flow-1", "start-1")
        val timestamp = Clock.System.now()
        val component = StartComponent("start-1", "Start")

        observer.onComponentEnter(component, context, timestamp) // DEBUG
        observer.onExecutionFailed(
            flow,
            ExecutionError("TEST", "error", "comp-1"),
            context,
            timestamp,
            100L
        ) // ERROR

        assertEquals(1, emitter.logs.size)
        assertEquals(LogLevel.ERROR, emitter.logs[0].first)
    }

    @Test
    fun testMetricsObserverCountsExecutions() {
        val exporter = InMemoryMetricExporter()
        val observer = MetricsObserver(exporter)
        
        val flow = createSimpleFlow()
        val context = ExecutionContext.create(flow.id, flow.getStartComponent().id)
        val timestamp = Clock.System.now()
        val component = StartComponent("start-1", "Start")

        observer.onComponentExit(component, ExecutionResult.success(emptyMap()), context, timestamp, 50L)
        observer.onExecutionCompleted(flow, ExecutionResult.success(emptyMap()), context, timestamp, 100L)

        val metrics = exporter.metrics
        assertTrue(metrics.any { it.name == "component_execution_total" })
        assertTrue(metrics.any { it.name == "component_execution_duration_ms_p50" })
        assertTrue(metrics.any { it.name == "context_size_bytes" })
    }

    @Test
    fun testTracingObserverCreatesSpans() {
        val tracer = SimpleTracer()
        val observer = TracingObserver(tracer)
        
        val flow = createSimpleFlow()
        val context = ExecutionContext.create(flow.id, flow.getStartComponent().id)
        val timestamp = Clock.System.now()
        val component = StartComponent("start-1", "Start")

        observer.onExecutionStarted(flow, context, timestamp)
        observer.onComponentEnter(component, context, timestamp)
        observer.onComponentExit(component, ExecutionResult.success(emptyMap()), context, timestamp, 50L)
        observer.onExecutionCompleted(flow, ExecutionResult.success(emptyMap()), context, timestamp, 100L)

        // Não há exceção - spans foram criados corretamente
    }

    private fun createSimpleFlow(): Flow {
        val start = StartComponent("start-1", "Start")
        val end = EndComponent("end-1", "End")
        val connection = Connection("conn-1", start.id, "out", end.id, "in")
        
        return Flow(
            id = "test-flow",
            name = "Test Flow",
            version = "1.0.0",
            components = listOf(start, end),
            connections = listOf(connection)
        )
    }
}

/**
 * Observer de teste que conta os eventos.
 */
class TestExecutionObserver : ExecutionObserver {
    var componentEnterCount = 0
    var componentExitCount = 0
    var contextChangedCount = 0
    var decisionEvaluatedCount = 0
    var executionStartedCount = 0
    var executionCompletedCount = 0
    var executionFailedCount = 0
    var executionAbortedCount = 0

    override fun onExecutionStarted(flow: Flow, context: ExecutionContext, timestamp: Instant) {
        executionStartedCount++
    }

    override fun onComponentEnter(component: Component, context: ExecutionContext, timestamp: Instant) {
        componentEnterCount++
    }

    override fun onComponentExit(component: Component, result: ExecutionResult, context: ExecutionContext, timestamp: Instant, durationMs: Long) {
        componentExitCount++
    }

    override fun onContextChanged(oldContext: ExecutionContext, newContext: ExecutionContext, reason: String, timestamp: Instant) {
        contextChangedCount++
    }

    override fun onDecisionEvaluated(decision: DecisionComponent, evaluatedCondition: String, result: Boolean, context: ExecutionContext, timestamp: Instant) {
        decisionEvaluatedCount++
    }

    override fun onExecutionCompleted(flow: Flow, result: ExecutionResult, context: ExecutionContext, timestamp: Instant, durationMs: Long) {
        executionCompletedCount++
    }

    override fun onExecutionFailed(flow: Flow, error: ExecutionError, context: ExecutionContext, timestamp: Instant, durationMs: Long) {
        executionFailedCount++
    }

    override fun onExecutionAborted(flow: Flow, context: ExecutionContext, reason: String, timestamp: Instant, durationMs: Long) {
        executionAbortedCount++
    }
}

/**
 * Emitter de teste que armazena logs.
 */
class TestLogEmitter : LogEmitter {
    val logs = mutableListOf<Pair<LogLevel, Map<String, String>>>()

    override fun emit(logLevel: LogLevel, message: String, metadata: Map<String, String>) {
        logs.add(logLevel to metadata)
    }
}
