package io.flowmobile.core.runtime

import io.flowmobile.core.domain.*
import io.flowmobile.core.observability.*
import kotlinx.datetime.Clock

/**
 * Motor de execução de fluxos que orquestra a execução passo-a-passo de componentes.
 *
 * O FlowExecutor é responsável por:
 * - Executar fluxos completos ou passo-a-passo
 * - Gerenciar o contexto de execução
 * - Registrar todas as operações no audit trail
 * - Coordenar com o GraphInterpreter para resolução de próximos componentes
 * - Invocar serviços do host através do HostServiceRegistry
 * - Notificar observers sobre eventos de execução
 *
 * @property hostServiceRegistry Registro de serviços do host para ActionComponents
 * @property observer Observer para eventos de execução (padrão: NoOp)
 */
class FlowExecutor(
    private val hostServiceRegistry: HostServiceRegistry,
    private val observer: ExecutionObserver = NoOpExecutionObserver()
) {
    
    private val interpreter = GraphInterpreter()
    
    /**
     * Executa um fluxo completo até um EndComponent ou erro.
     *
     * @param flow O fluxo a executar
     * @param initialContext Contexto inicial (opcional, usa contexto padrão se não fornecido)
     * @param mode Modo de execução
     * @return Resultado da execução
     */
    suspend fun execute(
        flow: Flow,
        initialContext: ExecutionContext? = null,
        mode: ExecutionMode = ExecutionMode.RUN_TO_COMPLETION
    ): ExecutionResult {
        // Validar grafo antes de executar
        val validation = interpreter.validateDAG(flow)
        if (!validation.isValid) {
            return ExecutionResult.failure(
                ExecutionError(
                    code = "INVALID_GRAPH",
                    message = "Grafo inválido: ${validation.errors.joinToString(", ")}",
                    componentId = null
                )
            )
        }
        
        // Inicializar contexto
        val startComponent = flow.getStartComponent()
        var context = initialContext ?: ExecutionContext.create(
            flowId = flow.id,
            initialComponentId = startComponent.id
        )
        
        // Adicionar entrada de audit para início da execução
        context = context.appendAuditEntry(
            componentId = startComponent.id,
            action = AuditAction.COMPONENT_STARTED,
            message = "Iniciando execução do fluxo ${flow.name}"
        )
        
        val startTime = Clock.System.now()
        
        // Notificar observer: execução iniciada
        observer.onExecutionStarted(flow, context, startTime)
        
        try {
            when (mode) {
                ExecutionMode.RUN_TO_COMPLETION -> {
                    context = executeToCompletion(flow, context)
                }
                ExecutionMode.STEP_BY_STEP -> {
                    // Para step-by-step, executar apenas um passo
                    val stepResult = step(flow, context)
                    context = stepResult.context
                    if (!stepResult.isComplete && stepResult.error == null) {
                        return ExecutionResult.partial(context)
                    }
                }
                ExecutionMode.RUN_TO_BREAKPOINT -> {
                    // Implementação futura: executar até encontrar breakpoint
                    context = executeToCompletion(flow, context)
                }
            }
            
            val endTime = Clock.System.now()
            val duration = (endTime - startTime).inWholeMilliseconds
            
            // Verificar se terminou em um EndComponent
            val currentComponentId = context.currentComponentId
            val currentComponent = currentComponentId?.let { flow.getComponentById(it) }
            if (currentComponent is EndComponent) {
                val result = ExecutionResult.success(
                    outputVariables = context.variables,
                    metrics = ExecutionMetrics(
                        startTime = startTime,
                        endTime = endTime,
                        duration = duration,
                        componentsExecuted = context.auditTrail.count { 
                            it.action == AuditAction.COMPONENT_COMPLETED 
                        }
                    )
                )
                
                // Notificar observer: execução completada com sucesso
                observer.onExecutionCompleted(flow, result, context, endTime, duration)
                
                return result
            } else {
                val error = ExecutionError(
                    code = "UNEXPECTED_TERMINATION",
                    message = "Execução terminou sem alcançar EndComponent",
                    componentId = context.currentComponentId
                )
                
                // Notificar observer: execução falhou
                observer.onExecutionFailed(flow, error, context, endTime, duration)
                
                return ExecutionResult.failure(error)
            }
            
        } catch (e: Exception) {
            val endTime = Clock.System.now()
            val duration = (endTime - startTime).inWholeMilliseconds
            
            val error = ExecutionError(
                code = "EXECUTION_ERROR",
                message = e.message ?: "Erro desconhecido durante execução",
                componentId = context.currentComponentId
            )
            
            // Notificar observer: execução falhou
            observer.onExecutionFailed(flow, error, context, endTime, duration)
            
            return ExecutionResult.failure(error)
        }
    }
    
    /**
     * Executa um único passo do fluxo (um componente).
     *
     * @param flow O fluxo sendo executado
     * @param context Contexto atual
     * @return Resultado do passo executado
     */
    suspend fun step(flow: Flow, context: ExecutionContext): StepResult {
        val currentComponentId = context.currentComponentId ?: return StepResult(
            context = context,
            isComplete = true,
            error = "Componente atual não definido"
        )
        val currentComponent = flow.getComponentById(currentComponentId)
            ?: return StepResult(
                context = context,
                isComplete = true,
                error = "Componente não encontrado: $currentComponentId"
            )
        
        val stepStartTime = Clock.System.now()
        
        // Notificar observer: entrando em componente
        observer.onComponentEnter(currentComponent, context, stepStartTime)
        
        // Executar o componente atual
        var newContext = context.appendAuditEntry(
            componentId = currentComponent.id,
            action = AuditAction.COMPONENT_STARTED,
            message = "Executando ${currentComponent.type}"
        )
        
        try {
            newContext = when (currentComponent) {
                is StartComponent -> executeStart(flow, currentComponent, newContext)
                is EndComponent -> {
                    newContext.appendAuditEntry(
                        componentId = currentComponent.id,
                        action = AuditAction.COMPONENT_COMPLETED,
                        message = "Fluxo finalizado"
                    )
                }
                is ActionComponent -> executeAction(flow, currentComponent, newContext)
                is DecisionComponent -> executeDecision(flow, currentComponent, newContext)
                is ForkComponent -> executeFork(flow, currentComponent, newContext)
                is JoinComponent -> executeJoin(flow, currentComponent, newContext)
            }
            
            // Marcar componente como completado
            newContext = newContext.appendAuditEntry(
                componentId = currentComponent.id,
                action = AuditAction.COMPONENT_COMPLETED,
                message = "Componente executado com sucesso"
            )
            
            val stepEndTime = Clock.System.now()
            val stepDuration = (stepEndTime - stepStartTime).inWholeMilliseconds
            
            // Notificar observer: saído de componente
            val result = ExecutionResult.success(outputVariables = emptyMap())
            observer.onComponentExit(currentComponent, result, newContext, stepEndTime, stepDuration)
            
            // Notificar observer: contexto alterado
            observer.onContextChanged(context, newContext, "component_executed", stepEndTime)
            
            // Para DecisionComponent, notificar sobre a avaliação
            if (currentComponent is DecisionComponent) {
                val condition = (currentComponent.properties[DecisionComponent.PROPERTY_CONDITION] as? ComponentProperty.StringValue)?.value ?: "unknown"
                val nextComponent = interpreter.resolveNext(flow, newContext)
                // Verificar se o próximo componente está no branch "true"
                observer.onDecisionEvaluated(currentComponent, condition, nextComponent != null, newContext, stepEndTime)
            }
            
            // Resolver próximo componente
            val nextComponent = interpreter.resolveNext(flow, newContext)
            
            if (nextComponent != null) {
                // Mover para o próximo componente
                newContext = newContext.withCurrentComponent(nextComponent.id)
                return StepResult(
                    context = newContext,
                    isComplete = false,
                    error = null
                )
            } else {
                // Execução completa
                return StepResult(
                    context = newContext,
                    isComplete = true,
                    error = null
                )
            }
            
        } catch (e: Exception) {
            val stepEndTime = Clock.System.now()
            val stepDuration = (stepEndTime - stepStartTime).inWholeMilliseconds
            
            newContext = newContext.appendAuditEntry(
                componentId = currentComponent.id,
                action = AuditAction.COMPONENT_FAILED,
                message = "Erro: ${e.message}"
            )
            
            // Notificar observer: componente falhou
            val error = ExecutionResult.failure(
                ExecutionError(
                    code = "COMPONENT_EXECUTION_ERROR",
                    message = e.message ?: "Erro desconhecido",
                    componentId = currentComponent.id
                )
            )
            observer.onComponentExit(currentComponent, error, newContext, stepEndTime, stepDuration)
            
            return StepResult(
                context = newContext,
                isComplete = true,
                error = e.message ?: "Erro desconhecido"
            )
        }
    }
    
    /**
     * Executa até completar ou encontrar erro.
     */
    private suspend fun executeToCompletion(flow: Flow, initialContext: ExecutionContext): ExecutionContext {
        var context = initialContext
        var maxIterations = 10000 // Proteção contra loops infinitos
        
        while (maxIterations > 0) {
            val stepResult = step(flow, context)
            context = stepResult.context
            
            if (stepResult.isComplete) {
                break
            }
            
            if (stepResult.error != null) {
                throw RuntimeException(stepResult.error)
            }
            
            maxIterations--
        }
        
        if (maxIterations == 0) {
            throw RuntimeException("Execução excedeu número máximo de iterações (possível loop infinito)")
        }
        
        return context
    }
    
    /**
     * Executa um StartComponent.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun executeStart(
        flow: Flow,
        component: StartComponent,
        context: ExecutionContext
    ): ExecutionContext {
        // StartComponent apenas inicializa variáveis do contexto
        // As variáveis já foram definidas no contexto inicial
        return context
    }
    
    /**
     * Executa um ActionComponent invocando o serviço do host.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun executeAction(
        flow: Flow,
        component: ActionComponent,
        context: ExecutionContext
    ): ExecutionContext {
        val serviceName = (component.properties[ActionComponent.PROPERTY_SERVICE] as? ComponentProperty.StringValue)?.value
            ?: throw IllegalStateException("ActionComponent sem propriedade 'service'")
        
        val methodName = (component.properties[ActionComponent.PROPERTY_METHOD] as? ComponentProperty.StringValue)?.value
            ?: throw IllegalStateException("ActionComponent sem propriedade 'method'")
        
        // Extrair parâmetros das portas de entrada
        val parameters = mutableMapOf<String, VariableValue>()
        for (port in component.getInputPorts()) {
            val value = context.variables[port.name]
            if (value != null) {
                parameters[port.name] = value
            }
        }
        
        // Invocar serviço do host
        val result = hostServiceRegistry.executeService(serviceName, methodName, parameters)
        
        if (!result.success) {
            throw RuntimeException("Erro ao executar serviço $serviceName.$methodName: ${result.error}")
        }
        
        // Armazenar resultado na variável de saída
        var newContext = context
        if (result.result != null) {
            val outputPort = component.getOutputPorts().firstOrNull()
            if (outputPort != null) {
                newContext = newContext.withVariable(outputPort.name, result.result)
            }
        }
        
        return newContext
    }
    
    /**
     * Executa um DecisionComponent avaliando a condição.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun executeDecision(
        flow: Flow,
        component: DecisionComponent,
        context: ExecutionContext
    ): ExecutionContext {
        // A lógica de avaliação já está no GraphInterpreter.resolveNext
        // Aqui apenas registramos a avaliação
        val condition = (component.properties[DecisionComponent.PROPERTY_CONDITION] as? ComponentProperty.StringValue)?.value
        
        return context.appendAuditEntry(
            componentId = component.id,
            action = AuditAction.VARIABLE_UPDATED,
            message = "Avaliando condição: $condition"
        )
    }
    
    /**
     * Executa um ForkComponent dividindo a execução em branches paralelos.
     * Nota: A implementação atual é um placeholder. Execução paralela é implementada em AsyncFlowExecutor.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun executeFork(
        flow: Flow,
        component: ForkComponent,
        context: ExecutionContext
    ): ExecutionContext {
        return context.appendAuditEntry(
            componentId = component.id,
            action = AuditAction.COMPONENT_STARTED,
            message = "Fork: dividindo em ${component.getBranchCount()} branches (execução em sequência neste modo)"
        )
    }
    
    /**
     * Executa um JoinComponent aguardando convergência de branches.
     * Nota: A implementação atual é um placeholder. Sincronização é implementada em AsyncFlowExecutor.
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun executeJoin(
        flow: Flow,
        component: JoinComponent,
        context: ExecutionContext
    ): ExecutionContext {
        return context.appendAuditEntry(
            componentId = component.id,
            action = AuditAction.COMPONENT_COMPLETED,
            message = "Join: mode ${component.getJoinMode()}"
        )
    }
    
    /**
     * Aborta a execução atual.
     *
     * @param context Contexto da execução
     * @param flow O fluxo sendo executado
     * @return Resultado de abort
     */
    fun abort(context: ExecutionContext, flow: Flow): AbortResult {
        val endTime = Clock.System.now()
        val duration = 0L // Duration not easily calculated here
        
        val newContext = context.appendAuditEntry(
            componentId = context.currentComponentId ?: context.flowId,
            action = AuditAction.COMPONENT_FAILED,
            message = "Execução abortada pelo usuário"
        )
        
        // Notificar observer: execução abortada
        observer.onExecutionAborted(flow, newContext, "Abortado pelo usuário", endTime, duration)
        
        return AbortResult(
            context = newContext,
            reason = "Abortado pelo usuário"
        )
    }
}

/**
 * Modo de execução do fluxo.
 */
enum class ExecutionMode {
    /** Executa até alcançar um EndComponent ou erro */
    RUN_TO_COMPLETION,
    
    /** Executa um componente por vez */
    STEP_BY_STEP,
    
    /** Executa até encontrar um breakpoint definido */
    RUN_TO_BREAKPOINT
}

/**
 * Resultado de um passo de execução.
 *
 * @property context Contexto atualizado após o passo
 * @property isComplete true se a execução foi completada
 * @property error Mensagem de erro se houver falha
 */
data class StepResult(
    val context: ExecutionContext,
    val isComplete: Boolean,
    val error: String?
)

/**
 * Resultado de abort de execução.
 *
 * @property context Contexto no momento do abort
 * @property reason Razão do abort
 */
data class AbortResult(
    val context: ExecutionContext,
    val reason: String
)
