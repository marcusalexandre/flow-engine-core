package io.flowmobile.core.loading

import io.flowmobile.core.domain.*

/**
 * Carregador de fluxos que combina parsing, validação e conversão para modelo de domínio.
 *
 * O FlowLoader é o ponto de entrada principal para carregar fluxos de JSON.
 * Ele coordena:
 * - Parsing do JSON para estruturas intermediárias
 * - Validação estrutural e semântica
 * - Conversão para o modelo de domínio (Flow)
 *
 * Suporta múltiplas fontes de entrada através de FlowSource.
 */
class FlowLoader {
    
    private val parser = FlowParser()
    private val validator = FlowValidator()
    private val schemaVersionManager = SchemaVersionManager()
    
    /**
     * Carrega um fluxo a partir de uma string JSON.
     *
     * @param json String JSON contendo a definição do fluxo
     * @return LoadResult com o Flow ou erros detalhados
     */
    fun load(json: String): LoadResult<Flow> {
        // 1. Parsear JSON
        val parseResult = parser.parse(json)
        if (parseResult.isFailure()) {
            return LoadResult.failure(parseResult.errorsOrEmpty())
        }
        
        val document = parseResult.getOrThrow()
        
        // 2. Verificar versão do schema
        if (!schemaVersionManager.isSupported(document.schemaVersion)) {
            return LoadResult.failure(
                LoadError(
                    code = "UNSUPPORTED_SCHEMA_VERSION",
                    message = "Versão de schema '${document.schemaVersion}' não é suportada. " +
                            "Versões suportadas: ${schemaVersionManager.getSupportedVersions().joinToString(", ")}",
                    path = "schemaVersion"
                )
            )
        }
        
        // 3. Converter para modelo de domínio
        val conversionResult = convertToFlow(document.flow)
        if (conversionResult.isFailure()) {
            return conversionResult
        }
        
        val flow = conversionResult.getOrThrow()
        
        // 4. Validar fluxo
        val validationResult = validator.validate(flow)
        if (!validationResult.isValid) {
            val errors = validationResult.errors.map { error ->
                LoadError(
                    code = error.code,
                    message = error.message,
                    path = error.path
                )
            }
            return LoadResult.failure(errors)
        }
        
        // 5. Retornar com warnings de deprecação se houver
        val warnings = mutableListOf<LoadWarning>()
        warnings.addAll(schemaVersionManager.getDeprecationWarnings(document.schemaVersion))
        warnings.addAll(validationResult.warnings.map { warning ->
            LoadWarning(
                code = warning.code,
                message = warning.message,
                path = warning.path
            )
        })
        
        return LoadResult.success(flow, warnings)
    }
    
    /**
     * Valida um JSON sem converter para modelo de domínio.
     * Útil para validação rápida no editor.
     *
     * @param json String JSON a validar
     * @return ValidationResult com erros e avisos
     */
    fun validate(json: String): ValidationResult {
        // Parsear
        val parseResult = parser.parse(json)
        if (parseResult.isFailure()) {
            return ValidationResult(
                isValid = false,
                errors = parseResult.errorsOrEmpty().map { error ->
                    ValidationError(
                        code = error.code,
                        message = error.message,
                        path = error.path
                    )
                }
            )
        }
        
        val document = parseResult.getOrThrow()
        
        // Verificar versão
        if (!schemaVersionManager.isSupported(document.schemaVersion)) {
            return ValidationResult(
                isValid = false,
                errors = listOf(
                    ValidationError(
                        code = "UNSUPPORTED_SCHEMA_VERSION",
                        message = "Versão de schema não suportada: ${document.schemaVersion}",
                        path = "schemaVersion"
                    )
                )
            )
        }
        
        // Converter e validar
        val conversionResult = convertToFlow(document.flow)
        if (conversionResult.isFailure()) {
            return ValidationResult(
                isValid = false,
                errors = conversionResult.errorsOrEmpty().map { error ->
                    ValidationError(
                        code = error.code,
                        message = error.message,
                        path = error.path
                    )
                }
            )
        }
        
        return validator.validate(conversionResult.getOrThrow())
    }
    
    /**
     * Retorna a versão do schema de um JSON.
     *
     * @param json String JSON
     * @return Versão do schema ou null se não encontrada
     */
    fun getSchemaVersion(json: String): String? {
        val parseResult = parser.parse(json)
        return parseResult.getOrNull()?.schemaVersion
    }
    
    /**
     * Converte uma FlowDefinition para o modelo de domínio Flow.
     */
    private fun convertToFlow(definition: FlowDefinition): LoadResult<Flow> {
        val errors = mutableListOf<LoadError>()
        
        // Converter componentes
        val components = mutableListOf<Component>()
        for ((index, compDef) in definition.components.withIndex()) {
            val componentResult = convertComponent(compDef, index)
            when (componentResult) {
                is LoadResult.Success -> components.add(componentResult.value)
                is LoadResult.Failure -> errors.addAll(componentResult.errors)
            }
        }
        
        // Converter conexões
        val connections = mutableListOf<Connection>()
        for ((index, connDef) in definition.connections.withIndex()) {
            val connectionResult = convertConnection(connDef, index)
            when (connectionResult) {
                is LoadResult.Success -> connections.add(connectionResult.value)
                is LoadResult.Failure -> errors.addAll(connectionResult.errors)
            }
        }
        
        if (errors.isNotEmpty()) {
            return LoadResult.failure(errors)
        }
        
        // Criar Flow (isso vai validar as invariantes básicas)
        return try {
            val flow = Flow(
                id = definition.id,
                name = definition.name,
                version = definition.version,
                components = components,
                connections = connections,
                metadata = FlowMetadata(
                    description = definition.metadata.description,
                    author = definition.metadata.author,
                    createdAt = definition.metadata.createdAt?.toLongOrNull(),
                    updatedAt = definition.metadata.updatedAt?.toLongOrNull(),
                    tags = definition.metadata.tags,
                    customData = definition.metadata.customData
                )
            )
            LoadResult.success(flow)
        } catch (e: IllegalArgumentException) {
            LoadResult.failure(
                LoadError(
                    code = "INVALID_FLOW",
                    message = e.message ?: "Erro ao criar fluxo",
                    path = "flow"
                )
            )
        }
    }
    
    /**
     * Converte uma ComponentDefinition para Component do domínio.
     */
    private fun convertComponent(
        definition: ComponentDefinition,
        index: Int
    ): LoadResult<Component> {
        val basePath = "flow.components[$index]"
        
        // Converter propriedades
        val properties = definition.properties.mapValues { (_, propDef) ->
            propDef.toComponentProperty()
        }
        
        // Criar metadados
        val metadata = ComponentMetadata(
            position = definition.position?.let { Position(it.x, it.y) },
            description = definition.metadata.description,
            tags = definition.metadata.tags,
            customData = definition.metadata.customData
        )
        
        return try {
            val component = when (definition.type.uppercase()) {
                "START" -> StartComponent(
                    id = definition.id,
                    name = definition.name,
                    properties = properties,
                    metadata = metadata
                )
                "END" -> EndComponent(
                    id = definition.id,
                    name = definition.name,
                    properties = properties,
                    metadata = metadata
                )
                "ACTION" -> ActionComponent(
                    id = definition.id,
                    name = definition.name,
                    properties = properties,
                    metadata = metadata
                )
                "DECISION" -> DecisionComponent(
                    id = definition.id,
                    name = definition.name,
                    properties = properties,
                    metadata = metadata
                )
                else -> return LoadResult.failure(
                    LoadError(
                        code = "UNKNOWN_COMPONENT_TYPE",
                        message = "Tipo de componente desconhecido: ${definition.type}",
                        path = "$basePath.type"
                    )
                )
            }
            LoadResult.success(component)
        } catch (e: IllegalArgumentException) {
            LoadResult.failure(
                LoadError(
                    code = "INVALID_COMPONENT",
                    message = e.message ?: "Erro ao criar componente",
                    path = basePath
                )
            )
        }
    }
    
    /**
     * Converte uma ConnectionDefinition para Connection do domínio.
     */
    private fun convertConnection(
        definition: ConnectionDefinition,
        index: Int
    ): LoadResult<Connection> {
        val basePath = "flow.connections[$index]"
        
        return try {
            val connection = Connection(
                id = definition.id,
                sourceComponentId = definition.source.componentId,
                sourcePortId = definition.source.portId,
                targetComponentId = definition.target.componentId,
                targetPortId = definition.target.portId,
                metadata = ConnectionMetadata(
                    label = definition.metadata.label,
                    customData = definition.metadata.customData
                )
            )
            LoadResult.success(connection)
        } catch (e: IllegalArgumentException) {
            LoadResult.failure(
                LoadError(
                    code = "INVALID_CONNECTION",
                    message = e.message ?: "Erro ao criar conexão",
                    path = basePath
                )
            )
        }
    }
}

/**
 * Fonte de dados para carregamento de fluxos.
 */
sealed class FlowSource {
    /**
     * Fonte de string JSON direta.
     */
    data class StringSource(val json: String) : FlowSource()
    
    /**
     * Fonte de URL remota.
     */
    data class UrlSource(val url: String) : FlowSource()
    
    /**
     * Fonte de asset da aplicação.
     */
    data class AssetSource(val assetPath: String) : FlowSource()
}
