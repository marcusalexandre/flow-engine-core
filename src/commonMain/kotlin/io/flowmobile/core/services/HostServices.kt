package io.flowmobile.core.services

import io.flowmobile.core.domain.VariableValue
import kotlinx.coroutines.flow.Flow

/**
 * Interface principal que agrega todos os serviços do host.
 * 
 * HostServices é o ponto de entrada para todos os serviços disponíveis
 * na aplicação host. Cada plataforma (Android, iOS, Web) implementa
 * esta interface com suas implementações específicas.
 * 
 * Princípios de Design:
 * - Interface-first: Todas as capacidades definidas como interfaces
 * - Null-safety: Serviços opcionais retornam null se não disponíveis
 * - Async-ready: Todas as operações são suspend functions
 * - Mockable: Interfaces simples facilitam mocking
 */
interface HostServices {
    /**
     * Serviço de armazenamento local (SharedPreferences, UserDefaults, LocalStorage).
     * Pode ser null se o armazenamento não estiver disponível.
     */
    val storage: StorageService?
    
    /**
     * Serviço de rede para requisições HTTP e monitoramento de conectividade.
     * Pode ser null se o serviço de rede não estiver disponível.
     */
    val network: NetworkService?
    
    /**
     * Serviço de logging estruturado.
     * Pode ser null se o logging não estiver configurado.
     */
    val logging: LoggingService?
    
    /**
     * Serviço de analytics para rastreamento de eventos.
     * Pode ser null se o analytics não estiver configurado.
     */
    val analytics: AnalyticsService?
    
    /**
     * Serviço de navegação entre telas/rotas.
     * Pode ser null se a navegação não estiver disponível.
     */
    val navigation: NavigationService?
    
    /**
     * Serviço de notificações (push, local).
     * Pode ser null se as notificações não estiverem configuradas.
     */
    val notification: NotificationService?
    
    /**
     * Serviço de informações do dispositivo.
     * Pode ser null se as informações do dispositivo não estiverem disponíveis.
     */
    val device: DeviceService?
    
    /**
     * Mapa de serviços customizados registrados pela aplicação host.
     * Permite extensibilidade para serviços específicos do domínio.
     */
    val custom: Map<String, CustomService>
    
    /**
     * Obtém um serviço customizado pelo nome.
     *
     * @param name Nome do serviço customizado
     * @return O serviço customizado ou null se não existir
     */
    fun getCustomService(name: String): CustomService? = custom[name]
    
    companion object {
        /**
         * Cria um builder para construir uma instância de HostServices.
         */
        fun builder(): HostServicesBuilder = HostServicesBuilder()
    }
}

/**
 * Builder para construir instâncias de HostServices.
 */
class HostServicesBuilder {
    private var storage: StorageService? = null
    private var network: NetworkService? = null
    private var logging: LoggingService? = null
    private var analytics: AnalyticsService? = null
    private var navigation: NavigationService? = null
    private var notification: NotificationService? = null
    private var device: DeviceService? = null
    private var custom: MutableMap<String, CustomService> = mutableMapOf()
    
    fun storage(service: StorageService?): HostServicesBuilder {
        this.storage = service
        return this
    }
    
    fun network(service: NetworkService?): HostServicesBuilder {
        this.network = service
        return this
    }
    
    fun logging(service: LoggingService?): HostServicesBuilder {
        this.logging = service
        return this
    }
    
    fun analytics(service: AnalyticsService?): HostServicesBuilder {
        this.analytics = service
        return this
    }
    
    fun navigation(service: NavigationService?): HostServicesBuilder {
        this.navigation = service
        return this
    }
    
    fun notification(service: NotificationService?): HostServicesBuilder {
        this.notification = service
        return this
    }
    
    fun device(service: DeviceService?): HostServicesBuilder {
        this.device = service
        return this
    }
    
    fun customService(name: String, service: CustomService): HostServicesBuilder {
        this.custom[name] = service
        return this
    }
    
    fun build(): HostServices = DefaultHostServices(
        storage = storage,
        network = network,
        logging = logging,
        analytics = analytics,
        navigation = navigation,
        notification = notification,
        device = device,
        custom = custom.toMap()
    )
}

/**
 * Implementação padrão de HostServices.
 */
internal class DefaultHostServices(
    override val storage: StorageService?,
    override val network: NetworkService?,
    override val logging: LoggingService?,
    override val analytics: AnalyticsService?,
    override val navigation: NavigationService?,
    override val notification: NotificationService?,
    override val device: DeviceService?,
    override val custom: Map<String, CustomService>
) : HostServices
