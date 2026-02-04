package io.flowmobile.core.services.mock

import io.flowmobile.core.services.*
import kotlinx.coroutines.delay

/**
 * Implementação mock do AnalyticsService para testes e sandbox.
 * Captura todos os eventos para posterior verificação.
 */
class MockAnalyticsService(
    /** Se o analytics está habilitado */
    private var enabled: Boolean = true,
    
    /** Latência artificial em milissegundos */
    var latencyMs: Long = 0
) : AnalyticsService {
    
    private val _events = mutableListOf<TrackedEvent>()
    private val _screenViews = mutableListOf<ScreenView>()
    private val _userProperties = mutableMapOf<String, String?>()
    private var _userId: String? = null
    private var _currentSessionId: String? = null
    
    /** Lista de eventos rastreados */
    val events: List<TrackedEvent> get() = _events.toList()
    
    /** Lista de visualizações de tela */
    val screenViews: List<ScreenView> get() = _screenViews.toList()
    
    /** Propriedades do usuário */
    val userProperties: Map<String, String?> get() = _userProperties.toMap()
    
    /** ID do usuário atual */
    val userId: String? get() = _userId
    
    /** ID da sessão atual */
    val currentSessionId: String? get() = _currentSessionId
    
    private suspend fun simulateLatency() {
        if (latencyMs > 0) {
            delay(latencyMs)
        }
    }
    
    override suspend fun trackEvent(name: String, parameters: Map<String, Any>) {
        if (!enabled) return
        simulateLatency()
        
        _events.add(TrackedEvent(
            name = name,
            parameters = parameters.mapValues { it.value.toString() },
            timestamp = currentTimeMillis(),
            sessionId = _currentSessionId,
            userId = _userId
        ))
    }
    
    override suspend fun trackScreenView(
        screenName: String,
        screenClass: String?,
        parameters: Map<String, Any>
    ) {
        if (!enabled) return
        simulateLatency()
        
        _screenViews.add(ScreenView(
            screenName = screenName,
            screenClass = screenClass,
            parameters = parameters.mapValues { it.value.toString() },
            timestamp = currentTimeMillis()
        ))
    }
    
    override suspend fun setUserProperty(name: String, value: String?) {
        if (!enabled) return
        simulateLatency()
        
        _userProperties[name] = value
    }
    
    override suspend fun setUserId(userId: String?) {
        simulateLatency()
        _userId = userId
    }
    
    override suspend fun resetUser() {
        simulateLatency()
        
        _userId = null
        _userProperties.clear()
    }
    
    override suspend fun startSession(sessionId: String?, parameters: Map<String, Any>) {
        if (!enabled) return
        simulateLatency()
        
        _currentSessionId = sessionId ?: generateSessionId()
        trackEvent(AnalyticsEvent.SESSION_STARTED, parameters + mapOf("session_id" to (_currentSessionId ?: "")))
    }
    
    override suspend fun endSession(parameters: Map<String, Any>) {
        if (!enabled) return
        simulateLatency()
        
        trackEvent(AnalyticsEvent.SESSION_ENDED, parameters + mapOf("session_id" to (_currentSessionId ?: "")))
        _currentSessionId = null
    }
    
    override suspend fun trackConversion(
        conversionName: String,
        value: Double?,
        currency: String?,
        parameters: Map<String, Any>
    ) {
        if (!enabled) return
        simulateLatency()
        
        val conversionParams = mutableMapOf<String, Any>(
            "conversion_name" to conversionName
        )
        value?.let { conversionParams["value"] = it }
        currency?.let { conversionParams["currency"] = it }
        
        trackEvent("conversion", conversionParams + parameters)
    }
    
    override suspend fun trackError(
        error: String,
        throwable: Throwable?,
        fatal: Boolean,
        parameters: Map<String, Any>
    ) {
        if (!enabled) return
        simulateLatency()
        
        val errorParams = mutableMapOf<String, Any>(
            "error_message" to error,
            "fatal" to fatal
        )
        throwable?.let { errorParams["stack_trace"] = it.stackTraceToString() }
        
        trackEvent(AnalyticsEvent.ERROR_OCCURRED, errorParams + parameters)
    }
    
    override suspend fun trackTiming(
        category: String,
        name: String,
        durationMs: Long,
        parameters: Map<String, Any>
    ) {
        if (!enabled) return
        simulateLatency()
        
        trackEvent("timing", mapOf(
            "category" to category,
            "name" to name,
            "duration_ms" to durationMs
        ) + parameters)
    }
    
    override suspend fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
    
    override suspend fun isEnabled(): Boolean = enabled
    
    // Métodos auxiliares para testes
    
    /**
     * Limpa todos os dados rastreados.
     */
    fun clearTrackedData() {
        _events.clear()
        _screenViews.clear()
    }
    
    /**
     * Verifica se um evento foi rastreado.
     */
    fun hasEvent(name: String): Boolean {
        return _events.any { it.name == name }
    }
    
    /**
     * Obtém eventos por nome.
     */
    fun getEventsByName(name: String): List<TrackedEvent> {
        return _events.filter { it.name == name }
    }
    
    /**
     * Verifica se uma tela foi visualizada.
     */
    fun hasScreenView(screenName: String): Boolean {
        return _screenViews.any { it.screenName == screenName }
    }
    
    /**
     * Conta eventos rastreados.
     */
    fun countEvents(): Int = _events.size
    
    /**
     * Reseta o mock.
     */
    fun reset() {
        _events.clear()
        _screenViews.clear()
        _userProperties.clear()
        _userId = null
        _currentSessionId = null
        enabled = true
    }
    
    private fun generateSessionId(): String {
        return "session_${currentTimeMillis()}"
    }
}

/**
 * Representa um evento rastreado.
 */
data class TrackedEvent(
    val name: String,
    val parameters: Map<String, String>,
    val timestamp: Long,
    val sessionId: String?,
    val userId: String?
)

/**
 * Representa uma visualização de tela.
 */
data class ScreenView(
    val screenName: String,
    val screenClass: String?,
    val parameters: Map<String, String>,
    val timestamp: Long
)
