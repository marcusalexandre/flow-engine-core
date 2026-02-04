@file:OptIn(ExperimentalJsExport::class)

package io.flowmobile.core.js

import io.flowmobile.core.domain.ExecutionError
import io.flowmobile.core.domain.ExecutionMetrics
import io.flowmobile.core.domain.ExecutionResult
import io.flowmobile.core.domain.VariableValue
import io.flowmobile.core.loading.FlowLoader
import io.flowmobile.core.runtime.ExecutionMode
import io.flowmobile.core.runtime.FlowExecutor
import io.flowmobile.core.runtime.HostService
import io.flowmobile.core.runtime.HostServiceRegistry
import io.flowmobile.core.runtime.ServiceResult
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExport
enum class JsExecutionMode {
    RUN_TO_COMPLETION,
    STEP_BY_STEP,
    RUN_TO_BREAKPOINT
}

@JsExport
data class JsValidationResult(
    val isValid: Boolean,
    val errors: Array<String>,
    val warnings: Array<String>
)

@JsExport
fun interface JsHostServiceHandler {
    fun handle(method: String, parametersJson: String): String?
}

@JsExport
class JsHostServiceRegistry {
    private val registry = HostServiceRegistry()

    fun register(name: String, handler: JsHostServiceHandler) {
        registry.register(name, JsHostServiceAdapter(name, handler))
    }

    fun unregister(name: String) {
        registry.unregister(name)
    }

    fun isAvailable(name: String): Boolean = registry.isAvailable(name)

    fun listAvailable(): Array<String> = registry.listAvailable().toTypedArray()

    internal fun asRegistry(): HostServiceRegistry = registry
}

@JsExport
class FlowEngineJs {
    private val loader = FlowLoader()

    fun validate(flowJson: String): JsValidationResult {
        val result = loader.validate(flowJson)
        val errors = result.errors.map { it.toString() }.toTypedArray()
        val warnings = result.warnings.map { it.toString() }.toTypedArray()
        return JsValidationResult(result.isValid, errors, warnings)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun execute(
        flowJson: String,
        services: JsHostServiceRegistry,
        mode: JsExecutionMode = JsExecutionMode.RUN_TO_COMPLETION
    ) = GlobalScope.promise {
        val loadResult = loader.load(flowJson)
        val flow = loadResult.getOrThrow()
        val executor = FlowExecutor(services.asRegistry())
        val executionResult = executor.execute(
            flow = flow,
            initialContext = null,
            mode = mode.toExecutionMode()
        )
        json.encodeToString(JsonObject.serializer(), executionResultToJson(executionResult))
    }
}

private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private fun JsExecutionMode.toExecutionMode(): ExecutionMode = when (this) {
    JsExecutionMode.RUN_TO_COMPLETION -> ExecutionMode.RUN_TO_COMPLETION
    JsExecutionMode.STEP_BY_STEP -> ExecutionMode.STEP_BY_STEP
    JsExecutionMode.RUN_TO_BREAKPOINT -> ExecutionMode.RUN_TO_BREAKPOINT
}

private fun encodeVariableMap(parameters: Map<String, VariableValue>): String {
    val jsonObject = buildJsonObject {
        for ((key, value) in parameters) {
            put(key, variableValueToJson(value))
        }
    }
    return json.encodeToString(JsonObject.serializer(), jsonObject)
}

private fun executionResultToJson(result: ExecutionResult): JsonObject = buildJsonObject {
    put("status", result.status.name)
    put("outputVariables", variableMapToJson(result.outputVariables))
    if (result.error != null) {
        put("error", executionErrorToJson(result.error))
    }
    put("metrics", executionMetricsToJson(result.metrics))
}

private fun executionErrorToJson(error: ExecutionError): JsonObject = buildJsonObject {
    put("code", error.code)
    put("message", error.message)
    if (error.componentId != null) {
        put("componentId", error.componentId)
    }
    put("details", buildJsonObject {
        for ((key, value) in error.details) {
            put(key, value)
        }
    })
    put("stackTrace", buildJsonArray {
        error.stackTrace.forEach { add(JsonPrimitive(it)) }
    })
}

private fun executionMetricsToJson(metrics: ExecutionMetrics): JsonObject = buildJsonObject {
    if (metrics.startTime != null) {
        put("startTime", metrics.startTime.toString())
    } else {
        put("startTime", JsonNull)
    }
    if (metrics.endTime != null) {
        put("endTime", metrics.endTime.toString())
    } else {
        put("endTime", JsonNull)
    }
    put("duration", metrics.duration)
    put("componentsExecuted", metrics.componentsExecuted)
    put("variablesCreated", metrics.variablesCreated)
    put("customMetrics", buildJsonObject {
        for ((key, value) in metrics.customMetrics) {
            put(key, value)
        }
    })
}

private fun variableMapToJson(map: Map<String, VariableValue>): JsonObject = buildJsonObject {
    for ((key, value) in map) {
        put(key, variableValueToJson(value))
    }
}

private fun variableValueToJson(value: VariableValue): JsonElement = when (value) {
    is VariableValue.StringValue -> JsonPrimitive(value.value)
    is VariableValue.NumberValue -> JsonPrimitive(value.value)
    is VariableValue.BooleanValue -> JsonPrimitive(value.value)
    is VariableValue.ObjectValue -> buildJsonObject {
        for ((key, nested) in value.value) {
            put(key, variableValueToJson(nested))
        }
    }
    is VariableValue.ArrayValue -> buildJsonArray {
        value.value.forEach { add(variableValueToJson(it)) }
    }
    is VariableValue.NullValue -> JsonNull
}

private fun jsonElementToVariableValue(element: JsonElement): VariableValue = when (element) {
    is JsonNull -> VariableValue.NullValue
    is JsonObject -> VariableValue.ObjectValue(
        element.mapValues { jsonElementToVariableValue(it.value) }
    )
    is JsonArray -> VariableValue.ArrayValue(
        element.map { jsonElementToVariableValue(it) }
    )
    is JsonPrimitive -> {
        when {
            element.isString -> VariableValue.StringValue(element.content)
            element.content == "true" || element.content == "false" ->
                VariableValue.BooleanValue(element.content == "true")
            element.content.toDoubleOrNull() != null ->
                VariableValue.NumberValue(element.content.toDouble())
            else -> VariableValue.StringValue(element.content)
        }
    }
}

private class JsHostServiceAdapter(
    override val name: String,
    private val handler: JsHostServiceHandler
) : HostService {
    override suspend fun execute(
        method: String,
        parameters: Map<String, VariableValue>
    ): ServiceResult {
        return try {
            val paramsJson = encodeVariableMap(parameters)
            val resultJson = handler.handle(method, paramsJson)
            val resultValue = resultJson?.let { jsonElementToVariableValue(json.parseToJsonElement(it)) }
            ServiceResult.success(resultValue)
        } catch (e: Exception) {
            ServiceResult.failure(e.message ?: "Erro ao executar serviço JS")
        }
    }
}
