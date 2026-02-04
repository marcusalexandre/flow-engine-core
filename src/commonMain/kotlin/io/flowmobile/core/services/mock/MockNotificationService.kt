package io.flowmobile.core.services.mock

import io.flowmobile.core.services.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Implementação mock do NotificationService para testes e sandbox.
 */
class MockNotificationService(
    /** Se as notificações estão habilitadas */
    var notificationsEnabled: Boolean = true,
    
    /** Token de push simulado */
    var pushToken: String? = "mock_push_token_12345",
    
    /** Se deve simular falhas */
    var shouldFail: Boolean = false,
    
    /** Latência artificial em milissegundos */
    var latencyMs: Long = 0
) : NotificationService {
    
    private val _displayedNotifications = mutableListOf<DisplayedNotification>()
    private val _scheduledNotifications = mutableMapOf<String, ScheduledNotification>()
    private val _clickFlow = MutableSharedFlow<NotificationClick>()
    private val _tokenFlow = MutableStateFlow(pushToken)
    private var _badgeCount = 0
    private var _notificationIdCounter = 0
    
    private val _recordedOperations = mutableListOf<NotificationOperation>()
    
    /** Lista de notificações exibidas */
    val displayedNotifications: List<DisplayedNotification> get() = _displayedNotifications.toList()
    
    /** Lista de notificações agendadas */
    val scheduledNotifications: Map<String, ScheduledNotification> get() = _scheduledNotifications.toMap()
    
    /** Badge count atual */
    val badgeCount: Int get() = _badgeCount
    
    /** Lista de operações gravadas */
    val recordedOperations: List<NotificationOperation> get() = _recordedOperations.toList()
    
    private suspend fun simulateLatency() {
        if (latencyMs > 0) {
            delay(latencyMs)
        }
    }
    
    private fun checkFailure() {
        if (shouldFail) {
            throw MockServiceException("Falha simulada no NotificationService")
        }
    }
    
    private fun generateNotificationId(): String {
        return "notification_${++_notificationIdCounter}"
    }
    
    override suspend fun show(notification: NotificationConfig): String {
        simulateLatency()
        checkFailure()
        
        val id = notification.id ?: generateNotificationId()
        
        _displayedNotifications.add(DisplayedNotification(
            id = id,
            config = notification,
            displayedAt = currentTimeMillis()
        ))
        
        _recordedOperations.add(NotificationOperation.Show(id, notification))
        
        return id
    }
    
    override suspend fun cancel(notificationId: String) {
        simulateLatency()
        checkFailure()
        
        _displayedNotifications.removeAll { it.id == notificationId }
        _recordedOperations.add(NotificationOperation.Cancel(notificationId))
    }
    
    override suspend fun cancelAll() {
        simulateLatency()
        checkFailure()
        
        val count = _displayedNotifications.size
        _displayedNotifications.clear()
        _recordedOperations.add(NotificationOperation.CancelAll(count))
    }
    
    override suspend fun schedule(notification: NotificationConfig, triggerTime: Long): String {
        simulateLatency()
        checkFailure()
        
        val id = notification.id ?: generateNotificationId()
        
        _scheduledNotifications[id] = ScheduledNotification(
            id = id,
            config = notification,
            triggerTime = triggerTime,
            scheduledAt = currentTimeMillis()
        )
        
        _recordedOperations.add(NotificationOperation.Schedule(id, notification, triggerTime))
        
        return id
    }
    
    override suspend fun cancelScheduled(notificationId: String) {
        simulateLatency()
        checkFailure()
        
        _scheduledNotifications.remove(notificationId)
        _recordedOperations.add(NotificationOperation.CancelScheduled(notificationId))
    }
    
    override suspend fun listScheduled(): List<String> {
        simulateLatency()
        checkFailure()
        
        return _scheduledNotifications.keys.toList()
    }
    
    override suspend fun areNotificationsEnabled(): Boolean {
        simulateLatency()
        return notificationsEnabled
    }
    
    override suspend fun requestPermission(): Boolean {
        simulateLatency()
        
        _recordedOperations.add(NotificationOperation.RequestPermission(notificationsEnabled))
        
        return notificationsEnabled
    }
    
    override fun observeNotificationClicks(): Flow<NotificationClick> {
        return _clickFlow.asSharedFlow()
    }
    
    override suspend fun getPushToken(): String? {
        simulateLatency()
        return pushToken
    }
    
    override fun observePushTokenChanges(): Flow<String> {
        @Suppress("UNCHECKED_CAST")
        return _tokenFlow as Flow<String>
    }
    
    override suspend fun setBadgeCount(count: Int) {
        simulateLatency()
        checkFailure()
        
        _badgeCount = count
        _recordedOperations.add(NotificationOperation.SetBadge(count))
    }
    
    // Métodos auxiliares para testes
    
    /**
     * Simula um clique em uma notificação.
     */
    suspend fun simulateClick(notificationId: String, actionId: String? = null, inputText: String? = null) {
        val notification = _displayedNotifications.find { it.id == notificationId }
            ?: _scheduledNotifications[notificationId]?.let { 
                DisplayedNotification(it.id, it.config, currentTimeMillis()) 
            }
        
        notification?.let {
            _clickFlow.emit(NotificationClick(
                notificationId = notificationId,
                actionId = actionId,
                data = it.config.data,
                inputText = inputText
            ))
        }
    }
    
    /**
     * Atualiza o token de push.
     */
    fun updatePushToken(token: String) {
        pushToken = token
        _tokenFlow.value = token
    }
    
    /**
     * Verifica se uma notificação foi exibida.
     */
    fun hasNotification(id: String): Boolean {
        return _displayedNotifications.any { it.id == id }
    }
    
    /**
     * Limpa operações gravadas.
     */
    fun clearRecordedOperations() {
        _recordedOperations.clear()
    }
    
    /**
     * Reseta o mock.
     */
    fun reset() {
        _displayedNotifications.clear()
        _scheduledNotifications.clear()
        _recordedOperations.clear()
        _badgeCount = 0
        _notificationIdCounter = 0
        notificationsEnabled = true
        shouldFail = false
        pushToken = "mock_push_token_12345"
    }
}

/**
 * Representa uma notificação exibida.
 */
data class DisplayedNotification(
    val id: String,
    val config: NotificationConfig,
    val displayedAt: Long
)

/**
 * Representa uma notificação agendada.
 */
data class ScheduledNotification(
    val id: String,
    val config: NotificationConfig,
    val triggerTime: Long,
    val scheduledAt: Long
)

/**
 * Representa uma operação de notificação gravada.
 */
sealed class NotificationOperation {
    abstract val timestamp: Long
    
    data class Show(
        val id: String,
        val config: NotificationConfig,
        override val timestamp: Long = currentTimeMillis()
    ) : NotificationOperation()
    
    data class Cancel(
        val id: String,
        override val timestamp: Long = currentTimeMillis()
    ) : NotificationOperation()
    
    data class CancelAll(
        val count: Int,
        override val timestamp: Long = currentTimeMillis()
    ) : NotificationOperation()
    
    data class Schedule(
        val id: String,
        val config: NotificationConfig,
        val triggerTime: Long,
        override val timestamp: Long = currentTimeMillis()
    ) : NotificationOperation()
    
    data class CancelScheduled(
        val id: String,
        override val timestamp: Long = currentTimeMillis()
    ) : NotificationOperation()
    
    data class RequestPermission(
        val granted: Boolean,
        override val timestamp: Long = currentTimeMillis()
    ) : NotificationOperation()
    
    data class SetBadge(
        val count: Int,
        override val timestamp: Long = currentTimeMillis()
    ) : NotificationOperation()
}
