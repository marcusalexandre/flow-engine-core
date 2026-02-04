package io.flowmobile.core.services

import kotlinx.serialization.Serializable

/**
 * Serviço de analytics para rastreamento de eventos e métricas.
 * 
 * Implementações específicas por plataforma:
 * - Android: Firebase Analytics, Amplitude, Mixpanel
 * - iOS: Firebase Analytics, Amplitude, Mixpanel
 * - Web: Google Analytics, Segment, Mixpanel
 */
interface AnalyticsService {
    
    /**
     * Rastreia um evento.
     *
     * @param name Nome do evento
     * @param parameters Parâmetros do evento
     */
    suspend fun trackEvent(name: String, parameters: Map<String, Any> = emptyMap())
    
    /**
     * Rastreia uma visualização de tela.
     *
     * @param screenName Nome da tela
     * @param screenClass Classe da tela (opcional)
     * @param parameters Parâmetros adicionais
     */
    suspend fun trackScreenView(
        screenName: String,
        screenClass: String? = null,
        parameters: Map<String, Any> = emptyMap()
    )
    
    /**
     * Define uma propriedade do usuário.
     *
     * @param name Nome da propriedade
     * @param value Valor da propriedade
     */
    suspend fun setUserProperty(name: String, value: String?)
    
    /**
     * Define o ID do usuário.
     *
     * @param userId ID do usuário ou null para remover
     */
    suspend fun setUserId(userId: String?)
    
    /**
     * Reseta todas as propriedades do usuário.
     */
    suspend fun resetUser()
    
    /**
     * Rastreia o início de uma sessão.
     *
     * @param sessionId ID da sessão
     * @param parameters Parâmetros adicionais
     */
    suspend fun startSession(sessionId: String? = null, parameters: Map<String, Any> = emptyMap())
    
    /**
     * Rastreia o fim de uma sessão.
     *
     * @param parameters Parâmetros adicionais
     */
    suspend fun endSession(parameters: Map<String, Any> = emptyMap())
    
    /**
     * Rastreia uma conversão ou meta atingida.
     *
     * @param conversionName Nome da conversão
     * @param value Valor monetário (se aplicável)
     * @param currency Moeda do valor
     * @param parameters Parâmetros adicionais
     */
    suspend fun trackConversion(
        conversionName: String,
        value: Double? = null,
        currency: String? = null,
        parameters: Map<String, Any> = emptyMap()
    )
    
    /**
     * Rastreia um erro ou exceção.
     *
     * @param error Descrição do erro
     * @param throwable Exceção (se disponível)
     * @param fatal Se o erro é fatal
     * @param parameters Parâmetros adicionais
     */
    suspend fun trackError(
        error: String,
        throwable: Throwable? = null,
        fatal: Boolean = false,
        parameters: Map<String, Any> = emptyMap()
    )
    
    /**
     * Rastreia uma métrica de timing/performance.
     *
     * @param category Categoria da métrica
     * @param name Nome da métrica
     * @param durationMs Duração em milissegundos
     * @param parameters Parâmetros adicionais
     */
    suspend fun trackTiming(
        category: String,
        name: String,
        durationMs: Long,
        parameters: Map<String, Any> = emptyMap()
    )
    
    /**
     * Habilita ou desabilita a coleta de analytics.
     *
     * @param enabled true para habilitar
     */
    suspend fun setEnabled(enabled: Boolean)
    
    /**
     * Verifica se a coleta de analytics está habilitada.
     *
     * @return true se está habilitada
     */
    suspend fun isEnabled(): Boolean
}

/**
 * Evento de analytics pré-definido.
 */
@Serializable
data class AnalyticsEvent(
    /** Nome do evento */
    val name: String,
    
    /** Parâmetros do evento */
    val parameters: Map<String, String> = emptyMap(),
    
    /** Timestamp do evento */
    val timestamp: Long,
    
    /** ID de sessão */
    val sessionId: String? = null,
    
    /** ID do usuário */
    val userId: String? = null
) {
    companion object {
        // Nomes de eventos padrão
        const val FLOW_STARTED = "flow_started"
        const val FLOW_COMPLETED = "flow_completed"
        const val FLOW_FAILED = "flow_failed"
        const val COMPONENT_EXECUTED = "component_executed"
        const val DECISION_TAKEN = "decision_taken"
        const val ACTION_INVOKED = "action_invoked"
        const val ROLLBACK_PERFORMED = "rollback_performed"
        const val SESSION_STARTED = "session_started"
        const val SESSION_ENDED = "session_ended"
        const val ERROR_OCCURRED = "error_occurred"
    }
}
