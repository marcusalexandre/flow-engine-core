package io.flowmobile.core.services.mock

import io.flowmobile.core.services.*
import kotlinx.coroutines.delay

/**
 * Implementação mock do DeviceService para testes e sandbox.
 */
class MockDeviceService(
    /** Informações do dispositivo simuladas */
    var deviceInfo: DeviceInfo = createDefaultDeviceInfo(),
    
    /** Informações da aplicação simuladas */
    var appInfo: AppInfo = createDefaultAppInfo(),
    
    /** Informações da tela simuladas */
    var screenInfo: ScreenInfo = createDefaultScreenInfo(),
    
    /** Informações da bateria simuladas */
    var batteryInfo: BatteryInfo = createDefaultBatteryInfo(),
    
    /** Informações de memória simuladas */
    var memoryInfo: MemoryInfo = createDefaultMemoryInfo(),
    
    /** Locale simulado */
    var locale: String = "pt-BR",
    
    /** Timezone simulado */
    var timeZone: String = "America/Sao_Paulo",
    
    /** Se está em modo escuro */
    var isDarkModeEnabled: Boolean = false,
    
    /** Se é tablet */
    var isTabletDevice: Boolean = false,
    
    /** Se está em foreground */
    var isAppInForeground: Boolean = true,
    
    /** Latência artificial em milissegundos */
    var latencyMs: Long = 0
) : DeviceService {
    
    private val _clipboard = mutableListOf<String>()
    private val _openedUrls = mutableListOf<String>()
    private val _hapticFeedbacks = mutableListOf<HapticType>()
    
    private val _recordedOperations = mutableListOf<DeviceOperation>()
    
    /** URLs que foram abertas */
    val openedUrls: List<String> get() = _openedUrls.toList()
    
    /** Feedbacks hápticos disparados */
    val hapticFeedbacks: List<HapticType> get() = _hapticFeedbacks.toList()
    
    /** Operações gravadas */
    val recordedOperations: List<DeviceOperation> get() = _recordedOperations.toList()
    
    private suspend fun simulateLatency() {
        if (latencyMs > 0) {
            delay(latencyMs)
        }
    }
    
    override suspend fun getDeviceInfo(): DeviceInfo {
        simulateLatency()
        _recordedOperations.add(DeviceOperation.GetDeviceInfo)
        return deviceInfo
    }
    
    override suspend fun getAppInfo(): AppInfo {
        simulateLatency()
        _recordedOperations.add(DeviceOperation.GetAppInfo)
        return appInfo
    }
    
    override suspend fun getScreenInfo(): ScreenInfo {
        simulateLatency()
        _recordedOperations.add(DeviceOperation.GetScreenInfo)
        return screenInfo
    }
    
    override suspend fun getBatteryInfo(): BatteryInfo {
        simulateLatency()
        _recordedOperations.add(DeviceOperation.GetBatteryInfo)
        return batteryInfo
    }
    
    override suspend fun getMemoryInfo(): MemoryInfo {
        simulateLatency()
        _recordedOperations.add(DeviceOperation.GetMemoryInfo)
        return memoryInfo
    }
    
    override suspend fun getLocale(): String {
        simulateLatency()
        _recordedOperations.add(DeviceOperation.GetLocale(locale))
        return locale
    }
    
    override suspend fun getTimeZone(): String {
        simulateLatency()
        _recordedOperations.add(DeviceOperation.GetTimeZone(timeZone))
        return timeZone
    }
    
    override suspend fun isDarkMode(): Boolean {
        simulateLatency()
        _recordedOperations.add(DeviceOperation.IsDarkMode(isDarkModeEnabled))
        return isDarkModeEnabled
    }
    
    override suspend fun isTablet(): Boolean {
        simulateLatency()
        _recordedOperations.add(DeviceOperation.IsTablet(isTabletDevice))
        return isTabletDevice
    }
    
    override suspend fun isInForeground(): Boolean {
        simulateLatency()
        _recordedOperations.add(DeviceOperation.IsInForeground(isAppInForeground))
        return isAppInForeground
    }
    
    override suspend fun openUrl(url: String): Boolean {
        simulateLatency()
        _openedUrls.add(url)
        _recordedOperations.add(DeviceOperation.OpenUrl(url, true))
        return true
    }
    
    override suspend fun copyToClipboard(text: String) {
        simulateLatency()
        _clipboard.clear()
        _clipboard.add(text)
        _recordedOperations.add(DeviceOperation.CopyToClipboard(text))
    }
    
    override suspend fun readFromClipboard(): String? {
        simulateLatency()
        val text = _clipboard.lastOrNull()
        _recordedOperations.add(DeviceOperation.ReadFromClipboard(text))
        return text
    }
    
    override suspend fun hapticFeedback(type: HapticType) {
        simulateLatency()
        _hapticFeedbacks.add(type)
        _recordedOperations.add(DeviceOperation.HapticFeedback(type))
    }
    
    // Métodos auxiliares para testes
    
    /**
     * Limpa operações gravadas.
     */
    fun clearRecordedOperations() {
        _recordedOperations.clear()
    }
    
    /**
     * Limpa a área de transferência.
     */
    fun clearClipboard() {
        _clipboard.clear()
    }
    
    /**
     * Reseta o mock para valores padrão.
     */
    fun reset() {
        deviceInfo = createDefaultDeviceInfo()
        appInfo = createDefaultAppInfo()
        screenInfo = createDefaultScreenInfo()
        batteryInfo = createDefaultBatteryInfo()
        memoryInfo = createDefaultMemoryInfo()
        locale = "pt-BR"
        timeZone = "America/Sao_Paulo"
        isDarkModeEnabled = false
        isTabletDevice = false
        isAppInForeground = true
        _clipboard.clear()
        _openedUrls.clear()
        _hapticFeedbacks.clear()
        _recordedOperations.clear()
    }
    
    companion object {
        fun createDefaultDeviceInfo() = DeviceInfo(
            platform = Platform.ANDROID,
            osVersion = "14",
            manufacturer = "Mock",
            model = "Mock Device",
            deviceName = "Mock Device Name",
            deviceId = "mock_device_id_12345",
            isEmulator = true,
            architecture = "arm64-v8a"
        )
        
        fun createDefaultAppInfo() = AppInfo(
            appName = "FlowMobile Mock",
            packageName = "io.flowmobile.mock",
            version = "1.0.0",
            versionCode = 1,
            isDebug = true,
            installTime = currentTimeMillis() - 86400000,
            lastUpdateTime = currentTimeMillis()
        )
        
        fun createDefaultScreenInfo() = ScreenInfo(
            widthPx = 1080,
            heightPx = 2400,
            density = 2.75f,
            widthDp = 392,
            heightDp = 873,
            orientation = ScreenOrientation.PORTRAIT,
            refreshRate = 60f
        )
        
        fun createDefaultBatteryInfo() = BatteryInfo(
            level = 85,
            isCharging = false,
            chargingType = ChargingType.NONE,
            isPowerSaveMode = false
        )
        
        fun createDefaultMemoryInfo() = MemoryInfo(
            totalBytes = 8_000_000_000,
            availableBytes = 4_000_000_000,
            usedByAppBytes = 150_000_000,
            isLowMemory = false
        )
    }
}

/**
 * Representa uma operação de device gravada.
 */
sealed class DeviceOperation {
    abstract val timestamp: Long
    
    data object GetDeviceInfo : DeviceOperation() {
        override val timestamp: Long = currentTimeMillis()
    }
    
    data object GetAppInfo : DeviceOperation() {
        override val timestamp: Long = currentTimeMillis()
    }
    
    data object GetScreenInfo : DeviceOperation() {
        override val timestamp: Long = currentTimeMillis()
    }
    
    data object GetBatteryInfo : DeviceOperation() {
        override val timestamp: Long = currentTimeMillis()
    }
    
    data object GetMemoryInfo : DeviceOperation() {
        override val timestamp: Long = currentTimeMillis()
    }
    
    data class GetLocale(
        val locale: String,
        override val timestamp: Long = currentTimeMillis()
    ) : DeviceOperation()
    
    data class GetTimeZone(
        val timeZone: String,
        override val timestamp: Long = currentTimeMillis()
    ) : DeviceOperation()
    
    data class IsDarkMode(
        val result: Boolean,
        override val timestamp: Long = currentTimeMillis()
    ) : DeviceOperation()
    
    data class IsTablet(
        val result: Boolean,
        override val timestamp: Long = currentTimeMillis()
    ) : DeviceOperation()
    
    data class IsInForeground(
        val result: Boolean,
        override val timestamp: Long = currentTimeMillis()
    ) : DeviceOperation()
    
    data class OpenUrl(
        val url: String,
        val success: Boolean,
        override val timestamp: Long = currentTimeMillis()
    ) : DeviceOperation()
    
    data class CopyToClipboard(
        val text: String,
        override val timestamp: Long = currentTimeMillis()
    ) : DeviceOperation()
    
    data class ReadFromClipboard(
        val text: String?,
        override val timestamp: Long = currentTimeMillis()
    ) : DeviceOperation()
    
    data class HapticFeedback(
        val type: HapticType,
        override val timestamp: Long = currentTimeMillis()
    ) : DeviceOperation()
}
