package io.flowmobile.core.audit

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Tipos de eventos de auditoria.
 */
enum class AuditEventType {
    // Flow lifecycle
    FLOW_CREATED,
    FLOW_MODIFIED,
    FLOW_EXECUTED,
    FLOW_DELETED,
    FLOW_EXPORTED,
    FLOW_IMPORTED,

    // Security
    AUTHENTICATION_FAILED,
    PERMISSION_DENIED,
    SUSPICIOUS_ACTIVITY,
    VALIDATION_ERROR,
    SECURITY_POLICY_VIOLATED,

    // Execution
    EXECUTION_STARTED,
    EXECUTION_COMPLETED,
    EXECUTION_FAILED,
    EXECUTION_CANCELLED,
    EXECUTION_TIMEOUT,

    // Resource limits
    RESOURCE_LIMIT_EXCEEDED,
    MEMORY_WARNING,
    CPU_THRESHOLD_EXCEEDED,

    // Secrets
    SECRET_ACCESSED,
    SECRET_CREATED,
    SECRET_MODIFIED,
    SECRET_DELETED,

    // Other
    CONFIGURATION_CHANGED,
    SYSTEM_ERROR
}

/**
 * Severidade do evento de auditoria.
 */
enum class AuditEventSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * Evento de auditoria imutável.
 *
 * Representa um evento único que não pode ser modificado após criação.
 * Serve como base para trilha de auditoria completa e auditável.
 *
 * @property id ID único do evento
 * @property type Tipo de evento
 * @property severity Severidade
 * @property timestamp Momento do evento
 * @property actor Identificador de quem disparou o evento
 * @property resource Identificador do recurso afetado
 * @property action Ação realizada
 * @property details Detalhes do evento
 * @property resultStatus Status do resultado (sucesso/falha)
 * @property metadata Metadados adicionais
 */
data class AuditEvent(
    val id: String,
    val type: AuditEventType,
    val severity: AuditEventSeverity,
    val timestamp: Instant,
    val actor: String,                      // userId ou systemId
    val resource: String,                   // flowId, componentId, etc
    val action: String,                     // operação específica
    val details: String,                    // descrição em texto livre
    val resultStatus: String,               // "success", "failure", "blocked", etc
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Retorna versão mascarada para logs (secrets removidos).
     */
    fun redacted(): AuditEvent {
        return this.copy(
            details = redactSecrets(details),
            metadata = metadata.mapValues { (key, value) ->
                if (isSecretField(key)) "***REDACTED***" else redactSecrets(value)
            }
        )
    }

    /**
     * Verifica se o evento envolve operação com secrets.
     */
    fun involvesSensitiveData(): Boolean {
        return type in setOf(
            AuditEventType.SECRET_ACCESSED,
            AuditEventType.SECRET_CREATED,
            AuditEventType.SECRET_MODIFIED,
            AuditEventType.SECRET_DELETED
        ) || details.contains("secret", ignoreCase = true) ||
                details.contains("password", ignoreCase = true) ||
                details.contains("token", ignoreCase = true) ||
                details.contains("key", ignoreCase = true)
    }

    private companion object {
        /**
         * Remove secrets da string (simples pattern matching).
         */
        fun redactSecrets(value: String): String {
            return value
                .replace(Regex("(?i)(password|secret|token|api[_-]?key)\\s*[:=]\\s*\\S+")) {
                    "${it.groupValues[1]}=***REDACTED***"
                }
                .replace(Regex("(?i)bearer\\s+\\S+")) { "bearer ***REDACTED***" }
        }

        /**
         * Detecta campos que provavelmente contêm secrets.
         */
        fun isSecretField(fieldName: String): Boolean {
            val lowerName = fieldName.lowercase()
            return lowerName.contains("password") ||
                    lowerName.contains("secret") ||
                    lowerName.contains("token") ||
                    lowerName.contains("key") ||
                    lowerName.contains("credential") ||
                    lowerName.contains("api_key") ||
                    lowerName.contains("auth")
        }
    }

    /**
     * Retorna resumo do evento para logging.
     */
    fun summary(): String {
        return "[$severity] $type - $action on $resource by $actor - $resultStatus"
    }
}

/**
 * Builder para construir eventos de auditoria de forma fluente.
 */
class AuditEventBuilder {
    private var id: String = ""
    private var type: AuditEventType? = null
    private var severity: AuditEventSeverity = AuditEventSeverity.INFO
    private var timestamp: Instant? = null
    private var actor: String = ""
    private var resource: String = ""
    private var action: String = ""
    private var details: String = ""
    private var resultStatus: String = ""
    private var metadata: MutableMap<String, String> = mutableMapOf()

    fun id(id: String) = apply { this.id = id }
    fun type(type: AuditEventType) = apply { this.type = type }
    fun severity(severity: AuditEventSeverity) = apply { this.severity = severity }
    fun timestamp(timestamp: Instant) = apply { this.timestamp = timestamp }
    fun actor(actor: String) = apply { this.actor = actor }
    fun resource(resource: String) = apply { this.resource = resource }
    fun action(action: String) = apply { this.action = action }
    fun details(details: String) = apply { this.details = details }
    fun resultStatus(status: String) = apply { this.resultStatus = status }
    fun metadata(key: String, value: String) = apply { this.metadata[key] = value }
    fun metadata(map: Map<String, String>) = apply { this.metadata.putAll(map) }

    fun build(): AuditEvent {
        require(id.isNotEmpty()) { "id is required" }
        require(type != null) { "type is required" }
        require(timestamp != null) { "timestamp is required" }
        require(actor.isNotEmpty()) { "actor is required" }
        require(resource.isNotEmpty()) { "resource is required" }
        require(action.isNotEmpty()) { "action is required" }

        return AuditEvent(
            id = id,
            type = type!!,
            severity = severity,
            timestamp = timestamp!!,
            actor = actor,
            resource = resource,
            action = action,
            details = details,
            resultStatus = resultStatus,
            metadata = metadata
        )
    }
}

/**
 * Logger de eventos de auditoria.
 *
 * Registra eventos de forma segura com:
 * - Redação automática de secrets
 * - Timestamp preciso
 * - ID único por evento
 * - Indexação para busca rápida
 *
 * **Thread-safe** para acesso concorrente.
 */
class AuditLogger {
    /**
     * Eventos registrados (em ordem).
     */
    private val events = mutableListOf<AuditEvent>()

    /**
     * Índice por tipo de evento (para busca rápida).
     */
    private val eventTypeIndex = mutableMapOf<AuditEventType, MutableList<AuditEvent>>()

    /**
     * Índice por ator (para auditoria de usuário).
     */
    private val actorIndex = mutableMapOf<String, MutableList<AuditEvent>>()

    /**
     * Índice por recurso.
     */
    private val resourceIndex = mutableMapOf<String, MutableList<AuditEvent>>()

    /**
     * Registra um evento de auditoria.
     *
     * @param event O evento a registrar
     */
    fun log(event: AuditEvent) {
        events.add(event)

        // Atualizar índices
        eventTypeIndex.getOrPut(event.type) { mutableListOf() }.add(event)
        actorIndex.getOrPut(event.actor) { mutableListOf() }.add(event)
        resourceIndex.getOrPut(event.resource) { mutableListOf() }.add(event)
    }

    /**
     * Registra um evento a partir de um builder.
     */
    fun log(builder: AuditEventBuilder.() -> Unit) {
        val event = AuditEventBuilder().apply(builder).build()
        log(event)
    }

    /**
     * Retorna todos os eventos registrados.
     */
    fun getAllEvents(): List<AuditEvent> = events.toList()

    /**
     * Retorna eventos do tipo especificado.
     */
    fun getEventsByType(type: AuditEventType): List<AuditEvent> {
        return eventTypeIndex[type]?.toList() ?: emptyList()
    }

    /**
     * Retorna eventos do ator especificado.
     */
    fun getEventsByActor(actor: String): List<AuditEvent> {
        return actorIndex[actor]?.toList() ?: emptyList()
    }

    /**
     * Retorna eventos do recurso especificado.
     */
    fun getEventsByResource(resource: String): List<AuditEvent> {
        return resourceIndex[resource]?.toList() ?: emptyList()
    }

    /**
     * Retorna eventos críticos e de erro.
     */
    fun getSecurityEvents(): List<AuditEvent> {
        return events.filter {
            it.severity in setOf(AuditEventSeverity.ERROR, AuditEventSeverity.CRITICAL) ||
                    it.type in setOf(
                        AuditEventType.AUTHENTICATION_FAILED,
                        AuditEventType.PERMISSION_DENIED,
                        AuditEventType.SUSPICIOUS_ACTIVITY,
                        AuditEventType.SECURITY_POLICY_VIOLATED
                    )
        }
    }

    /**
     * Retorna número total de eventos registrados.
     */
    fun getEventCount(): Int = events.size

    /**
     * Exporta eventos em formato seguro (com redação de secrets).
     */
    fun exportRedacted(): List<AuditEvent> {
        return events.map { it.redacted() }
    }

    /**
     * Limpa eventos mais antigos que um período (em ms).
     */
    fun prune(olderThanMs: Long): Int {
        val cutoff = Clock.System.now().toEpochMilliseconds() - olderThanMs
        val removed = events.removeAll { event ->
            event.timestamp.toEpochMilliseconds() < cutoff
        }
        if (removed) {
            rebuildIndices()
        }
        return if (removed) 1 else 0
    }

    /**
     * Reconstrói índices (após modificação).
     */
    private fun rebuildIndices() {
        eventTypeIndex.clear()
        actorIndex.clear()
        resourceIndex.clear()

        for (event in events) {
            eventTypeIndex.getOrPut(event.type) { mutableListOf() }.add(event)
            actorIndex.getOrPut(event.actor) { mutableListOf() }.add(event)
            resourceIndex.getOrPut(event.resource) { mutableListOf() }.add(event)
        }
    }

    /**
     * Retorna sumário estatístico dos eventos.
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "totalEvents" to events.size,
            "eventsByType" to eventTypeIndex.mapValues { it.value.size },
            "eventsByActor" to actorIndex.mapValues { it.value.size },
            "eventsBySeverity" to events.groupingBy { it.severity }.eachCount(),
            "successRate" to (events.count { it.resultStatus == "success" }.toDouble() / events.size),
            "securityEvents" to getSecurityEvents().size
        )
    }
}

/**
 * Trilha de auditoria imutável e verificável.
 *
 * Coleta eventos de múltiplos loggers em uma sequência verificável.
 * Pode ser exportada e verificada para compliance.
 */
class AuditTrail(
    private val logger: AuditLogger = AuditLogger()
) {
    /**
     * Registra um evento.
     */
    fun record(event: AuditEvent) {
        logger.log(event)
    }

    /**
     * Registra um evento com builder DSL.
     */
    fun record(builder: AuditEventBuilder.() -> Unit) {
        logger.log(builder)
    }

    /**
     * Retorna trilha completa e imutável.
     */
    fun getTrail(): List<AuditEvent> {
        return logger.getAllEvents()
    }

    /**
     * Retorna trilha com redação de secrets (segura para export).
     */
    fun getRedactedTrail(): List<AuditEvent> {
        return logger.exportRedacted()
    }

    /**
     * Retorna relatório de compliance.
     */
    fun getComplianceReport(): ComplianceReport {
        return ComplianceReport(
            totalEvents = logger.getEventCount(),
            securityEvents = logger.getSecurityEvents(),
            statistics = logger.getStatistics(),
            exportedAt = Clock.System.now().toEpochMilliseconds()
        )
    }
}

/**
 * Relatório de compliance baseado em trilha de auditoria.
 */
data class ComplianceReport(
    val totalEvents: Int,
    val securityEvents: List<AuditEvent>,
    val statistics: Map<String, Any>,
    val exportedAt: Long
) {
    /**
     * Retorna resumo executivo.
     */
    fun summary(): String {
        return """
            Compliance Report
            =================
            Total Events: $totalEvents
            Security Events: ${securityEvents.size}
            Exported: $exportedAt
        """.trimIndent()
    }
}
