package io.flowmobile.core.observability

import io.flowmobile.core.domain.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject

/**
 * Níveis de log estruturado.
 */
enum class LogLevel {
    TRACE,   // Detalhes de debugging
    DEBUG,   // Informações de desenvolvimento
    INFO,    // Eventos de negócio (padrão)
    WARN,    // Situações anormais
    ERROR,   // Erros recuperáveis
    FATAL    // Erros irrecuperáveis
}

/**
 * Interface para emissão de logs.
 */
interface LogEmitter {
    fun emit(logLevel: LogLevel, message: String, metadata: Map<String, String>)
}

/**
 * Implementação padrão que emite para stdout/stderr.
 */
class ConsoleLogEmitter : LogEmitter {
    override fun emit(logLevel: LogLevel, message: String, metadata: Map<String, String>) {
        val output = buildJsonObject {
            put("timestamp", Clock.System.now().toString())
            put("level", logLevel.name)
            put("message", message)
            put("context", buildJsonObject {
                metadata.forEach { (k, v) ->
                    put(k, v)
                }
            })
        }
        
        val json = Json.encodeToString(output)
        println(json)
    }
}

/**
 * Observer que registra todos os eventos de execução em formato JSON estruturado.
 *
 * Produz logs estruturados com contexto completo para cada evento, facilitando
 * agregação e análise em sistemas de logging centralizados (ELK, Splunk, etc.).
 *
 * @property emitter Implementação que emite os logs
 * @property minLogLevel Nível mínimo de log a ser emitido (filtragem)
 */
class LoggingObserver(
    private val emitter: LogEmitter = ConsoleLogEmitter(),
    private val minLogLevel: LogLevel = LogLevel.INFO
) : ExecutionObserver {

    private fun shouldLog(level: LogLevel): Boolean {
        return level.ordinal >= minLogLevel.ordinal
    }

    override fun onExecutionStarted(
        flow: Flow,
        context: ExecutionContext,
        timestamp: Instant
    ) {
        if (shouldLog(LogLevel.INFO)) {
            emitter.emit(
                LogLevel.INFO,
                "Execução de fluxo iniciada",
                mapOf(
                    "flow.id" to flow.id,
                    "flow.name" to flow.name,
                    "flow.version" to flow.version,
                    "execution.id" to context.executionId,
                    "timestamp" to timestamp.toString(),
                    "component.count" to flow.components.size.toString(),
                    "connection.count" to flow.connections.size.toString()
                )
            )
        }
    }

    override fun onComponentEnter(
        component: Component,
        context: ExecutionContext,
        timestamp: Instant
    ) {
        if (shouldLog(LogLevel.DEBUG)) {
            emitter.emit(
                LogLevel.DEBUG,
                "Entrando em componente: ${component.id}",
                mapOf(
                    "flow.id" to context.flowId,
                    "execution.id" to context.executionId,
                    "component.id" to component.id,
                    "component.type" to component::class.simpleName.orEmpty(),
                    "timestamp" to timestamp.toString(),
                    "context.stack.depth" to context.executionStack.size.toString()
                )
            )
        }
    }

    override fun onComponentExit(
        component: Component,
        result: ExecutionResult,
        context: ExecutionContext,
        timestamp: Instant,
        durationMs: Long
    ) {
        val logLevel = if (result.isSuccess()) LogLevel.DEBUG else LogLevel.WARN
        if (shouldLog(logLevel)) {
            val metadata = mutableMapOf(
                "flow.id" to context.flowId,
                "execution.id" to context.executionId,
                "component.id" to component.id,
                "component.type" to component::class.simpleName.orEmpty(),
                "timestamp" to timestamp.toString(),
                "duration.ms" to durationMs.toString(),
                "result.status" to (if (result.isSuccess()) "SUCCESS" else "FAILURE")
            )
            
            if (!result.isSuccess() && result.error != null) {
                metadata["error.code"] = result.error.code
                metadata["error.message"] = result.error.message
            }

            emitter.emit(
                logLevel,
                "Saído de componente: ${component.id}",
                metadata
            )
        }
    }

    override fun onContextChanged(
        oldContext: ExecutionContext,
        newContext: ExecutionContext,
        reason: String,
        timestamp: Instant
    ) {
        if (shouldLog(LogLevel.TRACE)) {
            val variablesDiff = findVariableDifferences(oldContext.variables, newContext.variables)
            emitter.emit(
                LogLevel.TRACE,
                "Contexto alterado: $reason",
                mapOf(
                    "flow.id" to newContext.flowId,
                    "execution.id" to newContext.executionId,
                    "reason" to reason,
                    "timestamp" to timestamp.toString(),
                    "variables.changed" to variablesDiff.size.toString(),
                    "variables.total" to newContext.variables.size.toString()
                )
            )
        }
    }

    override fun onDecisionEvaluated(
        decision: DecisionComponent,
        evaluatedCondition: String,
        result: Boolean,
        context: ExecutionContext,
        timestamp: Instant
    ) {
        if (shouldLog(LogLevel.DEBUG)) {
            emitter.emit(
                LogLevel.DEBUG,
                "Decisão avaliada: ${decision.id}",
                mapOf(
                    "flow.id" to context.flowId,
                    "execution.id" to context.executionId,
                    "decision.id" to decision.id,
                    "condition" to evaluatedCondition,
                    "result" to result.toString(),
                    "timestamp" to timestamp.toString(),
                    "branch.selected" to (if (result) "true" else "false")
                )
            )
        }
    }

    override fun onExecutionCompleted(
        flow: Flow,
        result: ExecutionResult,
        context: ExecutionContext,
        timestamp: Instant,
        durationMs: Long
    ) {
        if (shouldLog(LogLevel.INFO)) {
            val metadata = mutableMapOf(
                "flow.id" to flow.id,
                "flow.name" to flow.name,
                "execution.id" to context.executionId,
                "timestamp" to timestamp.toString(),
                "duration.ms" to durationMs.toString(),
                "status" to "COMPLETED",
                "audit.entries" to context.auditTrail.size.toString(),
                "final.variables" to context.variables.size.toString()
            )

            emitter.emit(
                LogLevel.INFO,
                "Execução de fluxo completada com sucesso",
                metadata
            )
        }
    }

    override fun onExecutionFailed(
        flow: Flow,
        error: ExecutionError,
        context: ExecutionContext,
        timestamp: Instant,
        durationMs: Long
    ) {
        if (shouldLog(LogLevel.ERROR)) {
            emitter.emit(
                LogLevel.ERROR,
                "Execução de fluxo falhou: ${error.message}",
                mapOf(
                    "flow.id" to flow.id,
                    "flow.name" to flow.name,
                    "execution.id" to context.executionId,
                    "error.code" to error.code,
                    "error.message" to error.message,
                    "error.component.id" to (error.componentId.orEmpty()),
                    "timestamp" to timestamp.toString(),
                    "duration.ms" to durationMs.toString(),
                    "status" to "FAILED",
                    "audit.entries" to context.auditTrail.size.toString()
                )
            )
        }
    }

    override fun onExecutionAborted(
        flow: Flow,
        context: ExecutionContext,
        reason: String,
        timestamp: Instant,
        durationMs: Long
    ) {
        if (shouldLog(LogLevel.WARN)) {
            emitter.emit(
                LogLevel.WARN,
                "Execução de fluxo abortada: $reason",
                mapOf(
                    "flow.id" to flow.id,
                    "flow.name" to flow.name,
                    "execution.id" to context.executionId,
                    "abort.reason" to reason,
                    "timestamp" to timestamp.toString(),
                    "duration.ms" to durationMs.toString(),
                    "status" to "ABORTED",
                    "audit.entries" to context.auditTrail.size.toString()
                )
            )
        }
    }

    /**
     * Encontra as diferenças entre dois mapas de variáveis.
     */
    private fun findVariableDifferences(
        old: Map<String, VariableValue>,
        new: Map<String, VariableValue>
    ): Set<String> {
        val changed = mutableSetOf<String>()
        
        // Variáveis adicionadas ou modificadas
        new.forEach { (key, newValue) ->
            if (key !in old || old[key] != newValue) {
                changed.add(key)
            }
        }
        
        // Variáveis removidas
        old.forEach { (key, _) ->
            if (key !in new) {
                changed.add(key)
            }
        }
        
        return changed
    }
}
