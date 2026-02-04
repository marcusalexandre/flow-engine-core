package io.flowmobile.core.services.mock

import io.flowmobile.core.services.*

/**
 * Exceção lançada por serviços mock quando configurados para falhar.
 */
class MockServiceException(message: String) : RuntimeException(message)

/**
 * Factory para criar HostServices com implementações mock.
 * 
 * Útil para testes e sandbox onde você quer controlar completamente
 * o comportamento dos serviços.
 */
object MockHostServicesFactory {
    
    /**
     * Cria uma instância de HostServices com todos os serviços mock.
     *
     * @param configure Lambda para configurar os mocks antes de criar
     * @return HostServices com implementações mock
     */
    fun create(configure: MockHostServicesConfig.() -> Unit = {}): HostServices {
        val config = MockHostServicesConfig().apply(configure)
        return config.build()
    }
    
    /**
     * Cria uma instância mínima apenas com storage e logging.
     */
    fun createMinimal(): HostServices {
        return create {
            withStorage()
            withLogging()
        }
    }
    
    /**
     * Cria uma instância completa com todos os serviços.
     */
    fun createComplete(): HostServices {
        return create {
            withStorage()
            withNetwork()
            withNavigation()
            withLogging()
            withAnalytics()
            withNotification()
            withDevice()
        }
    }
}

/**
 * Configuração para criação de HostServices mock.
 */
class MockHostServicesConfig {
    
    private var storage: MockStorageService? = null
    private var network: MockNetworkService? = null
    private var navigation: MockNavigationService? = null
    private var logging: MockLoggingService? = null
    private var analytics: MockAnalyticsService? = null
    private var notification: MockNotificationService? = null
    private var device: MockDeviceService? = null
    private val customServices = mutableMapOf<String, CustomService>()
    
    // Getters para acesso aos mocks após criação
    val storageService: MockStorageService? get() = storage
    val networkService: MockNetworkService? get() = network
    val navigationService: MockNavigationService? get() = navigation
    val loggingService: MockLoggingService? get() = logging
    val analyticsService: MockAnalyticsService? get() = analytics
    val notificationService: MockNotificationService? get() = notification
    val deviceService: MockDeviceService? get() = device
    
    /**
     * Adiciona MockStorageService.
     */
    fun withStorage(
        predefinedValues: Map<String, String> = emptyMap(),
        configure: MockStorageService.() -> Unit = {}
    ): MockHostServicesConfig {
        storage = MockStorageService(predefinedValues).apply(configure)
        return this
    }
    
    /**
     * Adiciona MockNetworkService.
     */
    fun withNetwork(
        predefinedResponses: Map<String, NetworkResponse> = emptyMap(),
        configure: MockNetworkService.() -> Unit = {}
    ): MockHostServicesConfig {
        network = MockNetworkService(predefinedResponses).apply(configure)
        return this
    }
    
    /**
     * Adiciona MockNavigationService.
     */
    fun withNavigation(
        initialDestination: Destination? = null,
        configure: MockNavigationService.() -> Unit = {}
    ): MockHostServicesConfig {
        navigation = MockNavigationService(initialDestination).apply(configure)
        return this
    }
    
    /**
     * Adiciona MockLoggingService.
     */
    fun withLogging(
        minLevel: LogLevel = LogLevel.VERBOSE,
        configure: MockLoggingService.() -> Unit = {}
    ): MockHostServicesConfig {
        logging = MockLoggingService(minLevel).apply(configure)
        return this
    }
    
    /**
     * Adiciona MockAnalyticsService.
     */
    fun withAnalytics(
        enabled: Boolean = true,
        configure: MockAnalyticsService.() -> Unit = {}
    ): MockHostServicesConfig {
        analytics = MockAnalyticsService(enabled).apply(configure)
        return this
    }
    
    /**
     * Adiciona MockNotificationService.
     */
    fun withNotification(
        notificationsEnabled: Boolean = true,
        configure: MockNotificationService.() -> Unit = {}
    ): MockHostServicesConfig {
        notification = MockNotificationService(notificationsEnabled).apply(configure)
        return this
    }
    
    /**
     * Adiciona MockDeviceService.
     */
    fun withDevice(
        configure: MockDeviceService.() -> Unit = {}
    ): MockHostServicesConfig {
        device = MockDeviceService().apply(configure)
        return this
    }
    
    /**
     * Adiciona um serviço customizado.
     */
    fun withCustomService(name: String, service: CustomService): MockHostServicesConfig {
        customServices[name] = service
        return this
    }
    
    /**
     * Configura todos os mocks para simular falhas.
     */
    fun withAllFailing(): MockHostServicesConfig {
        storage?.shouldFail = true
        network?.shouldFail = true
        navigation?.shouldFail = true
        notification?.shouldFail = true
        return this
    }
    
    /**
     * Configura latência em todos os mocks.
     */
    fun withLatency(latencyMs: Long): MockHostServicesConfig {
        storage?.latencyMs = latencyMs
        network?.latencyMs = latencyMs
        navigation?.latencyMs = latencyMs
        analytics?.latencyMs = latencyMs
        notification?.latencyMs = latencyMs
        device?.latencyMs = latencyMs
        return this
    }
    
    /**
     * Constrói a instância de HostServices.
     */
    fun build(): HostServices {
        return HostServices.builder()
            .storage(storage)
            .network(network)
            .navigation(navigation)
            .logging(logging)
            .analytics(analytics)
            .notification(notification)
            .device(device)
            .apply {
                customServices.forEach { (name, service) ->
                    customService(name, service)
                }
            }
            .build()
    }
}

/**
 * Container que mantém referências aos mocks para facilitar testes.
 */
class MockHostServices(
    val storage: MockStorageService? = null,
    val network: MockNetworkService? = null,
    val navigation: MockNavigationService? = null,
    val logging: MockLoggingService? = null,
    val analytics: MockAnalyticsService? = null,
    val notification: MockNotificationService? = null,
    val device: MockDeviceService? = null
) {
    /**
     * Converte para HostServices.
     */
    fun toHostServices(): HostServices {
        return HostServices.builder()
            .storage(storage)
            .network(network)
            .navigation(navigation)
            .logging(logging)
            .analytics(analytics)
            .notification(notification)
            .device(device)
            .build()
    }
    
    /**
     * Reseta todos os mocks.
     */
    fun resetAll() {
        storage?.reset()
        network?.reset()
        navigation?.reset()
        logging?.reset()
        analytics?.reset()
        notification?.reset()
        device?.reset()
    }
    
    /**
     * Configura todos para falhar.
     */
    fun setAllFailing(shouldFail: Boolean) {
        storage?.shouldFail = shouldFail
        network?.shouldFail = shouldFail
        navigation?.shouldFail = shouldFail
        notification?.shouldFail = shouldFail
    }
    
    companion object {
        /**
         * Cria instância com todos os mocks.
         */
        fun createAll(): MockHostServices {
            return MockHostServices(
                storage = MockStorageService(),
                network = MockNetworkService(),
                navigation = MockNavigationService(),
                logging = MockLoggingService(),
                analytics = MockAnalyticsService(),
                notification = MockNotificationService(),
                device = MockDeviceService()
            )
        }
    }
}
