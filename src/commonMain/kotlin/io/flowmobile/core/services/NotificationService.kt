package io.flowmobile.core.services

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Serviço de notificações (push e local).
 * 
 * Implementações específicas por plataforma:
 * - Android: NotificationManager, WorkManager, Firebase Cloud Messaging
 * - iOS: UNUserNotificationCenter, APNs
 * - Web: Notification API, Service Workers
 */
interface NotificationService {
    
    /**
     * Exibe uma notificação local.
     *
     * @param notification Configuração da notificação
     * @return ID da notificação exibida
     */
    suspend fun show(notification: NotificationConfig): String
    
    /**
     * Cancela uma notificação pelo ID.
     *
     * @param notificationId ID da notificação a cancelar
     */
    suspend fun cancel(notificationId: String)
    
    /**
     * Cancela todas as notificações.
     */
    suspend fun cancelAll()
    
    /**
     * Agenda uma notificação para um momento futuro.
     *
     * @param notification Configuração da notificação
     * @param triggerTime Timestamp em milissegundos para quando exibir
     * @return ID da notificação agendada
     */
    suspend fun schedule(notification: NotificationConfig, triggerTime: Long): String
    
    /**
     * Cancela uma notificação agendada.
     *
     * @param notificationId ID da notificação agendada
     */
    suspend fun cancelScheduled(notificationId: String)
    
    /**
     * Lista notificações agendadas.
     *
     * @return Lista de IDs de notificações agendadas
     */
    suspend fun listScheduled(): List<String>
    
    /**
     * Verifica se as notificações estão habilitadas.
     *
     * @return true se as notificações estão permitidas
     */
    suspend fun areNotificationsEnabled(): Boolean
    
    /**
     * Solicita permissão para exibir notificações.
     *
     * @return true se a permissão foi concedida
     */
    suspend fun requestPermission(): Boolean
    
    /**
     * Observa cliques em notificações.
     *
     * @return Flow que emite quando uma notificação é clicada
     */
    fun observeNotificationClicks(): Flow<NotificationClick>
    
    /**
     * Obtém o token de push notification (FCM, APNs, etc.).
     *
     * @return Token ou null se não disponível
     */
    suspend fun getPushToken(): String?
    
    /**
     * Observa mudanças no token de push.
     *
     * @return Flow que emite novos tokens
     */
    fun observePushTokenChanges(): Flow<String>
    
    /**
     * Atualiza o badge da aplicação.
     *
     * @param count Número a exibir no badge (0 para remover)
     */
    suspend fun setBadgeCount(count: Int)
}

/**
 * Configuração de uma notificação.
 */
@Serializable
data class NotificationConfig(
    /** ID único da notificação (opcional, será gerado se não fornecido) */
    val id: String? = null,
    
    /** Título da notificação */
    val title: String,
    
    /** Corpo/mensagem da notificação */
    val body: String,
    
    /** Subtítulo (iOS) ou texto expandido (Android) */
    val subtitle: String? = null,
    
    /** Dados extras para passar com a notificação */
    val data: Map<String, String> = emptyMap(),
    
    /** ID do canal de notificação (Android) */
    val channelId: String? = null,
    
    /** Prioridade da notificação */
    val priority: NotificationPriority = NotificationPriority.DEFAULT,
    
    /** URL da imagem grande (opcional) */
    val imageUrl: String? = null,
    
    /** Nome do ícone pequeno (Android) */
    val smallIcon: String? = null,
    
    /** Se deve fazer som */
    val sound: Boolean = true,
    
    /** Se deve vibrar */
    val vibrate: Boolean = true,
    
    /** Ações da notificação */
    val actions: List<NotificationAction> = emptyList(),
    
    /** Grupo da notificação (para agrupamento) */
    val group: String? = null,
    
    /** Se é o resumo do grupo */
    val isGroupSummary: Boolean = false
)

/**
 * Ação de uma notificação.
 */
@Serializable
data class NotificationAction(
    /** ID único da ação */
    val id: String,
    
    /** Texto do botão */
    val title: String,
    
    /** Ícone da ação (opcional) */
    val icon: String? = null,
    
    /** Se requer desbloquear o dispositivo */
    val requiresUnlock: Boolean = false,
    
    /** Se permite entrada de texto (Android) */
    val allowsTextInput: Boolean = false
)

/**
 * Prioridade da notificação.
 */
@Serializable
enum class NotificationPriority {
    /** Prioridade mínima (silenciosa) */
    MIN,
    
    /** Prioridade baixa */
    LOW,
    
    /** Prioridade padrão */
    DEFAULT,
    
    /** Prioridade alta */
    HIGH,
    
    /** Prioridade máxima (urgente) */
    MAX
}

/**
 * Representa um clique em uma notificação.
 */
@Serializable
data class NotificationClick(
    /** ID da notificação clicada */
    val notificationId: String,
    
    /** ID da ação clicada (null se clicou na notificação principal) */
    val actionId: String? = null,
    
    /** Dados da notificação */
    val data: Map<String, String>,
    
    /** Texto digitado pelo usuário (se ação com input) */
    val inputText: String? = null
)
