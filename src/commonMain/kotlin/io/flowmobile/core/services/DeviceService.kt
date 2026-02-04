package io.flowmobile.core.services

import kotlinx.serialization.Serializable

/**
 * Serviço de informações do dispositivo.
 * 
 * Implementações específicas por plataforma:
 * - Android: Build, PackageManager, Context
 * - iOS: UIDevice, ProcessInfo, Bundle
 * - Web: Navigator API, window.screen
 */
interface DeviceService {
    
    /**
     * Retorna informações gerais do dispositivo.
     *
     * @return Informações do dispositivo
     */
    suspend fun getDeviceInfo(): DeviceInfo
    
    /**
     * Retorna informações da aplicação.
     *
     * @return Informações da aplicação
     */
    suspend fun getAppInfo(): AppInfo
    
    /**
     * Retorna informações da tela/display.
     *
     * @return Informações da tela
     */
    suspend fun getScreenInfo(): ScreenInfo
    
    /**
     * Retorna informações da bateria.
     *
     * @return Informações da bateria
     */
    suspend fun getBatteryInfo(): BatteryInfo
    
    /**
     * Retorna informações de memória.
     *
     * @return Informações de memória
     */
    suspend fun getMemoryInfo(): MemoryInfo
    
    /**
     * Retorna o idioma atual do dispositivo.
     *
     * @return Código do idioma (ex: "pt-BR")
     */
    suspend fun getLocale(): String
    
    /**
     * Retorna o fuso horário atual.
     *
     * @return ID do fuso horário (ex: "America/Sao_Paulo")
     */
    suspend fun getTimeZone(): String
    
    /**
     * Verifica se o dispositivo está em modo escuro.
     *
     * @return true se o modo escuro está ativo
     */
    suspend fun isDarkMode(): Boolean
    
    /**
     * Verifica se o dispositivo é um tablet.
     *
     * @return true se é tablet
     */
    suspend fun isTablet(): Boolean
    
    /**
     * Verifica se a aplicação está em foreground.
     *
     * @return true se está em foreground
     */
    suspend fun isInForeground(): Boolean
    
    /**
     * Abre uma URL no navegador padrão.
     *
     * @param url URL a abrir
     * @return true se conseguiu abrir
     */
    suspend fun openUrl(url: String): Boolean
    
    /**
     * Copia texto para a área de transferência.
     *
     * @param text Texto a copiar
     */
    suspend fun copyToClipboard(text: String)
    
    /**
     * Lê texto da área de transferência.
     *
     * @return Texto da área de transferência ou null
     */
    suspend fun readFromClipboard(): String?
    
    /**
     * Dispara vibração háptica.
     *
     * @param type Tipo de feedback háptico
     */
    suspend fun hapticFeedback(type: HapticType)
}

/**
 * Informações do dispositivo.
 */
@Serializable
data class DeviceInfo(
    /** Plataforma (Android, iOS, Web) */
    val platform: Platform,
    
    /** Versão do sistema operacional */
    val osVersion: String,
    
    /** Fabricante do dispositivo */
    val manufacturer: String,
    
    /** Modelo do dispositivo */
    val model: String,
    
    /** Nome do dispositivo (se disponível) */
    val deviceName: String? = null,
    
    /** ID único do dispositivo (anonimizado) */
    val deviceId: String,
    
    /** Se é um emulador/simulador */
    val isEmulator: Boolean,
    
    /** Arquitetura do processador */
    val architecture: String? = null
)

/**
 * Plataforma de execução.
 */
@Serializable
enum class Platform {
    ANDROID,
    IOS,
    WEB,
    DESKTOP,
    UNKNOWN
}

/**
 * Informações da aplicação.
 */
@Serializable
data class AppInfo(
    /** Nome da aplicação */
    val appName: String,
    
    /** Package name / Bundle ID */
    val packageName: String,
    
    /** Versão da aplicação (ex: "1.2.3") */
    val version: String,
    
    /** Código da versão (ex: 123) */
    val versionCode: Long,
    
    /** Se é build de debug */
    val isDebug: Boolean,
    
    /** Data de instalação (timestamp) */
    val installTime: Long? = null,
    
    /** Data da última atualização (timestamp) */
    val lastUpdateTime: Long? = null
)

/**
 * Informações da tela.
 */
@Serializable
data class ScreenInfo(
    /** Largura em pixels */
    val widthPx: Int,
    
    /** Altura em pixels */
    val heightPx: Int,
    
    /** Densidade de pixels (DPI / 160) */
    val density: Float,
    
    /** Largura em DP (Android) ou pontos (iOS) */
    val widthDp: Int,
    
    /** Altura em DP (Android) ou pontos (iOS) */
    val heightDp: Int,
    
    /** Orientação atual */
    val orientation: ScreenOrientation,
    
    /** Taxa de atualização em Hz */
    val refreshRate: Float? = null
)

/**
 * Orientação da tela.
 */
@Serializable
enum class ScreenOrientation {
    PORTRAIT,
    LANDSCAPE,
    UNKNOWN
}

/**
 * Informações da bateria.
 */
@Serializable
data class BatteryInfo(
    /** Nível da bateria (0-100) */
    val level: Int,
    
    /** Se está carregando */
    val isCharging: Boolean,
    
    /** Tipo de carregamento */
    val chargingType: ChargingType,
    
    /** Se o modo de economia de bateria está ativo */
    val isPowerSaveMode: Boolean
)

/**
 * Tipo de carregamento.
 */
@Serializable
enum class ChargingType {
    /** Não está carregando */
    NONE,
    
    /** Carregador AC */
    AC,
    
    /** Carregador USB */
    USB,
    
    /** Carregamento sem fio */
    WIRELESS,
    
    /** Tipo desconhecido */
    UNKNOWN
}

/**
 * Informações de memória.
 */
@Serializable
data class MemoryInfo(
    /** Memória total em bytes */
    val totalBytes: Long,
    
    /** Memória disponível em bytes */
    val availableBytes: Long,
    
    /** Memória usada pela aplicação em bytes */
    val usedByAppBytes: Long? = null,
    
    /** Se a memória está baixa */
    val isLowMemory: Boolean
)

/**
 * Tipo de feedback háptico.
 */
@Serializable
enum class HapticType {
    /** Impacto leve */
    LIGHT,
    
    /** Impacto médio */
    MEDIUM,
    
    /** Impacto forte */
    HEAVY,
    
    /** Seleção */
    SELECTION,
    
    /** Sucesso */
    SUCCESS,
    
    /** Aviso */
    WARNING,
    
    /** Erro */
    ERROR
}
