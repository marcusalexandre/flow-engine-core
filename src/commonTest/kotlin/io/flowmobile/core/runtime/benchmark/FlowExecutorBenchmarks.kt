package io.flowmobile.core.runtime.benchmark

import io.flowmobile.core.domain.*
import io.flowmobile.core.observability.NoOpExecutionObserver
import io.flowmobile.core.runtime.FlowExecutor
import io.flowmobile.core.runtime.HostServiceRegistry
import io.flowmobile.core.runtime.HostService
import io.flowmobile.core.runtime.ServiceResult
import io.flowmobile.core.runtime.async.AsyncFlowExecutor
import kotlinx.datetime.Clock

/**
 * Suite de benchmarks para medir performance do engine.
 *
 * Implementa benchmarks para validar otimizações de performance:
 * - Simple Flow (5 components): < 10ms
 * - Medium Flow (50 components): < 100ms
 * - Large Flow (500 components): < 1s
 * - Parallel Branches (10 branches): > 100 exec/s
 * - Context Size (1MB): < 2MB overhead
 */
class FlowExecutorBenchmarks(
    private val hostServiceRegistry: HostServiceRegistry = createTestRegistry()
) {
    
    private val executor = FlowExecutor(
        hostServiceRegistry,
        NoOpExecutionObserver()
    )
    
    private val asyncExecutor = AsyncFlowExecutor(
        hostServiceRegistry,
        NoOpExecutionObserver()
    )
    
    /**
     * Resultado de um benchmark.
     */
    data class BenchmarkResult(
        val name: String,
        val durationMs: Long,
        val targetMs: Long,
        val passed: Boolean,
        val throughputOpsPerSecond: Double? = null
    ) {
        fun prettyPrint(): String {
            val target = if (throughputOpsPerSecond != null) {
                ">${throughputOpsPerSecond.toInt()} ops/s"
            } else {
                "<${targetMs}ms"
            }
            
            val status = if (passed) "✓ PASS" else "✗ FAIL"
            val actualMetric = if (throughputOpsPerSecond != null) {
                "${throughputOpsPerSecond.toInt()} ops/s"
            } else {
                "${durationMs}ms"
            }
            
            return "$status $name: $actualMetric (target: $target)"
        }
    }
    
    /**
     * Benchmark: Simple flow com 5 componentes.
     * Target: < 10ms
     */
    fun benchmarkSimpleFlow(): BenchmarkResult = runBlockingTest {
        val flow = createSimpleFlow(5)
        val context = ExecutionContext.create(flow.id, flow.getStartComponent().id)
        
        val startTime = Clock.System.now()
        val result = executor.execute(flow, context)
        val endTime = Clock.System.now()
        
        val durationMs = (endTime - startTime).inWholeMilliseconds
        val passed = durationMs < 10 && result.isSuccess()
        
        BenchmarkResult(
            name = "Simple Flow (5 components)",
            durationMs = durationMs,
            targetMs = 10,
            passed = passed
        )
    }
    
    /**
     * Benchmark: Medium flow com 50 componentes.
     * Target: < 100ms
     */
    fun benchmarkMediumFlow(): BenchmarkResult = runBlockingTest {
        val flow = createSimpleFlow(50)
        val context = ExecutionContext.create(flow.id, flow.getStartComponent().id)
        
        val startTime = Clock.System.now()
        val result = executor.execute(flow, context)
        val endTime = Clock.System.now()
        
        val durationMs = (endTime - startTime).inWholeMilliseconds
        val passed = durationMs < 100 && result.isSuccess()
        
        BenchmarkResult(
            name = "Medium Flow (50 components)",
            durationMs = durationMs,
            targetMs = 100,
            passed = passed
        )
    }
    
    /**
     * Benchmark: Large flow com 500 componentes.
     * Target: < 1s (1000ms)
     */
    fun benchmarkLargeFlow(): BenchmarkResult = runBlockingTest {
        val flow = createSimpleFlow(500)
        val context = ExecutionContext.create(flow.id, flow.getStartComponent().id)
        
        val startTime = Clock.System.now()
        val result = executor.execute(flow, context)
        val endTime = Clock.System.now()
        
        val durationMs = (endTime - startTime).inWholeMilliseconds
        val passed = durationMs < 1000 && result.isSuccess()
        
        BenchmarkResult(
            name = "Large Flow (500 components)",
            durationMs = durationMs,
            targetMs = 1000,
            passed = passed
        )
    }
    
    /**
     * Benchmark: Execução paralela de 10 branches.
     * Target: > 100 execuções/segundo
     */
    fun benchmarkParallelBranches(): BenchmarkResult = runBlockingTest {
        val numBranches = 10
        val flows = (0 until numBranches).map { createSimpleFlow(5) }
        
        val startTime = Clock.System.now()
        val results = asyncExecutor.executeParallelAsync(
            flows.map { it to null }
        )
        val endTime = Clock.System.now()
        
        val durationMs = (endTime - startTime).inWholeMilliseconds
        val successCount = results.count { it.isSuccess() }
        val throughput = if (durationMs > 0) (numBranches * 1000.0) / durationMs else 0.0
        val passed = successCount == numBranches && throughput > 100
        
        BenchmarkResult(
            name = "Parallel Branches (10 branches, 5 components each)",
            durationMs = durationMs,
            targetMs = 0,
            passed = passed,
            throughputOpsPerSecond = throughput
        )
    }
    
    /**
     * Executa a suite completa de benchmarks.
     *
     * @return Lista de resultados dos benchmarks
     */
    fun runAll(): List<BenchmarkResult> {
        println("╔════════════════════════════════════════════════════════════════╗")
        println("║         Flow Engine Performance Benchmarks - Phase 10           ║")
        println("╚════════════════════════════════════════════════════════════════╝")
        println()
        
        val results = listOf(
            benchmarkSimpleFlow(),
            benchmarkMediumFlow(),
            benchmarkLargeFlow(),
            benchmarkParallelBranches()
        )
        
        results.forEach { result ->
            println(result.prettyPrint())
        }
        
        println()
        val passCount = results.count { it.passed }
        val totalCount = results.size
        println("Results: $passCount/$totalCount passed")
        println()
        
        return results
    }
}

private fun <T> runBlockingTest(block: suspend () -> T): T {
    var result: T? = null
    var exception: Throwable? = null

    kotlinx.coroutines.test.runTest {
        try {
            result = block()
        } catch (e: Throwable) {
            exception = e
        }
    }

    exception?.let { throw it }

    @Suppress("UNCHECKED_CAST")
    return result as T
}

/**
 * Cria um HostServiceRegistry com um serviço de teste para benchmarks.
 */
private fun createTestRegistry(): HostServiceRegistry {
    val registry = HostServiceRegistry()
    registry.register("TestService", object : HostService {
        override val name: String = "TestService"
        
        override suspend fun execute(
            method: String,
            parameters: Map<String, VariableValue>
        ): ServiceResult {
            return ServiceResult.success(VariableValue.StringValue("mock_result"))
        }
    })
    return registry
}

/**
 * Cria um fluxo simples com N componentes Action conectados em série.
 */
private fun createSimpleFlow(componentCount: Int): Flow {
    require(componentCount >= 2) { "Flow must have at least START and END" }
    
    val components = mutableListOf<Component>()
    val connections = mutableListOf<Connection>()
    
    // START component
    val startComponent = StartComponent(
        id = "start",
        name = "Start",
        properties = emptyMap()
    )
    components.add(startComponent)
    
    // ACTION components
    for (i in 1..componentCount - 2) {
        val component = ActionComponent(
            id = "action_$i",
            name = "Action $i",
            properties = mapOf(
                ActionComponent.PROPERTY_SERVICE to ComponentProperty.StringValue("TestService"),
                ActionComponent.PROPERTY_METHOD to ComponentProperty.StringValue("execute")
            )
        )
        components.add(component)
        
        // Connect previous to this
        val fromId = if (i == 1) "start" else "action_${i - 1}"
        val fromPort = if (i == 1) "out" else "result"
        
        connections.add(
            Connection(
                id = "conn_$i",
                sourceComponentId = fromId,
                sourcePortId = fromPort,
                targetComponentId = "action_$i",
                targetPortId = "in"
            )
        )
    }
    
    // END component
    val endComponent = EndComponent(
        id = "end",
        name = "End",
        properties = emptyMap()
    )
    components.add(endComponent)
    
    // Connect last ACTION to END
    val lastActionId = "action_${componentCount - 2}"
    connections.add(
        Connection(
            id = "conn_end",
            sourceComponentId = lastActionId,
            sourcePortId = "result",
            targetComponentId = "end",
            targetPortId = "in"
        )
    )
    
    return Flow(
        id = "benchmark_flow",
        name = "Benchmark Flow",
        version = "1.0.0",
        components = components,
        connections = connections
    )
}
