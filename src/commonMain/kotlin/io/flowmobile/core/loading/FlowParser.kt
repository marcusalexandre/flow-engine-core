package io.flowmobile.core.loading

import io.flowmobile.core.domain.*
import kotlinx.serialization.json.*

/**
 * Parser para converter JSON em definições de fluxo.
 *
 * O FlowParser é responsável por:
 * - Fazer parsing estrutural do JSON
 * - Converter para estruturas intermediárias (FlowDefinition)
 * - Retornar erros detalhados com linha/coluna quando possível
 *
 * O parser suporta múltiplas versões de schema, selecionando o parser adequado.
 */
class FlowParser {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    /**
     * Faz parsing de uma string JSON para FlowDocument.
     *
     * @param jsonString O JSON a ser parseado
     * @return LoadResult com FlowDocument ou erros
     */
    fun parse(jsonString: String): LoadResult<FlowDocument> {
        return try {
            // Primeiro, fazer parsing para JsonElement para inspeção
            val jsonElement = try {
                json.parseToJsonElement(jsonString)
            } catch (e: Exception) {
                return LoadResult.failure(
                    LoadError(
                        code = "INVALID_JSON",
                        message = "JSON inválido: ${e.message}"
                    )
                )
            }
            
            // Verificar se é um objeto
            if (jsonElement !is JsonObject) {
                return LoadResult.failure(
                    LoadError(
                        code = "INVALID_ROOT",
                        message = "Documento deve ser um objeto JSON"
                    )
                )
            }
            
            // Verificar campos obrigatórios
            val schemaVersion = jsonElement["schemaVersion"]?.jsonPrimitive?.contentOrNull
            if (schemaVersion == null) {
                return LoadResult.failure(
                    LoadError(
                        code = "MISSING_SCHEMA_VERSION",
                        message = "Campo 'schemaVersion' é obrigatório",
                        path = "schemaVersion"
                    )
                )
            }
            
            val flowObject = jsonElement["flow"]
            if (flowObject == null) {
                return LoadResult.failure(
                    LoadError(
                        code = "MISSING_FLOW",
                        message = "Campo 'flow' é obrigatório",
                        path = "flow"
                    )
                )
            }
            
            if (flowObject !is JsonObject) {
                return LoadResult.failure(
                    LoadError(
                        code = "INVALID_FLOW",
                        message = "Campo 'flow' deve ser um objeto",
                        path = "flow"
                    )
                )
            }
            
            // Parsear o fluxo
            val flowResult = parseFlowDefinition(flowObject)
            
            flowResult.map { flowDef ->
                FlowDocument(
                    schemaVersion = schemaVersion,
                    flow = flowDef
                )
            }
            
        } catch (e: Exception) {
            LoadResult.failure(
                LoadError(
                    code = "PARSE_ERROR",
                    message = "Erro ao parsear JSON: ${e.message}"
                )
            )
        }
    }
    
    /**
     * Parseia um objeto JSON para FlowDefinition.
     */
    private fun parseFlowDefinition(jsonObject: JsonObject): LoadResult<FlowDefinition> {
        val errors = mutableListOf<LoadError>()
        
        // Campos obrigatórios
        val id = jsonObject["id"]?.jsonPrimitive?.contentOrNull
        if (id == null) {
            errors.add(LoadError(
                code = "MISSING_FIELD",
                message = "Campo 'id' é obrigatório no fluxo",
                path = "flow.id"
            ))
        }
        
        val name = jsonObject["name"]?.jsonPrimitive?.contentOrNull
        if (name == null) {
            errors.add(LoadError(
                code = "MISSING_FIELD",
                message = "Campo 'name' é obrigatório no fluxo",
                path = "flow.name"
            ))
        }
        
        val version = jsonObject["version"]?.jsonPrimitive?.contentOrNull
        if (version == null) {
            errors.add(LoadError(
                code = "MISSING_FIELD",
                message = "Campo 'version' é obrigatório no fluxo",
                path = "flow.version"
            ))
        }
        
        val componentsArray = jsonObject["components"]
        if (componentsArray == null) {
            errors.add(LoadError(
                code = "MISSING_FIELD",
                message = "Campo 'components' é obrigatório no fluxo",
                path = "flow.components"
            ))
        } else if (componentsArray !is JsonArray) {
            errors.add(LoadError(
                code = "INVALID_TYPE",
                message = "Campo 'components' deve ser um array",
                path = "flow.components"
            ))
        }
        
        val connectionsArray = jsonObject["connections"]
        if (connectionsArray == null) {
            errors.add(LoadError(
                code = "MISSING_FIELD",
                message = "Campo 'connections' é obrigatório no fluxo",
                path = "flow.connections"
            ))
        } else if (connectionsArray !is JsonArray) {
            errors.add(LoadError(
                code = "INVALID_TYPE",
                message = "Campo 'connections' deve ser um array",
                path = "flow.connections"
            ))
        }
        
        if (errors.isNotEmpty()) {
            return LoadResult.failure(errors)
        }
        
        // Parsear componentes
        val components = mutableListOf<ComponentDefinition>()
        val componentsJsonArray = componentsArray as JsonArray
        for ((index, componentElement) in componentsJsonArray.withIndex()) {
            if (componentElement !is JsonObject) {
                errors.add(LoadError(
                    code = "INVALID_COMPONENT",
                    message = "Componente deve ser um objeto",
                    path = "flow.components[$index]"
                ))
                continue
            }
            
            val componentResult = parseComponentDefinition(componentElement, index)
            when (componentResult) {
                is LoadResult.Success -> components.add(componentResult.value)
                is LoadResult.Failure -> errors.addAll(componentResult.errors)
            }
        }
        
        // Parsear conexões
        val connections = mutableListOf<ConnectionDefinition>()
        val connectionsJsonArray = connectionsArray as JsonArray
        for ((index, connectionElement) in connectionsJsonArray.withIndex()) {
            if (connectionElement !is JsonObject) {
                errors.add(LoadError(
                    code = "INVALID_CONNECTION",
                    message = "Conexão deve ser um objeto",
                    path = "flow.connections[$index]"
                ))
                continue
            }
            
            val connectionResult = parseConnectionDefinition(connectionElement, index)
            when (connectionResult) {
                is LoadResult.Success -> connections.add(connectionResult.value)
                is LoadResult.Failure -> errors.addAll(connectionResult.errors)
            }
        }
        
        if (errors.isNotEmpty()) {
            return LoadResult.failure(errors)
        }
        
        // Parsear metadados opcionais
        val description = jsonObject["description"]?.jsonPrimitive?.contentOrNull ?: ""
        val metadata = parseFlowMetadata(jsonObject["metadata"] as? JsonObject)
        
        return LoadResult.success(
            FlowDefinition(
                id = id!!,
                name = name!!,
                version = version!!,
                description = description,
                components = components,
                connections = connections,
                metadata = metadata
            )
        )
    }
    
    /**
     * Parseia um objeto JSON para ComponentDefinition.
     */
    private fun parseComponentDefinition(
        jsonObject: JsonObject,
        index: Int
    ): LoadResult<ComponentDefinition> {
        val basePath = "flow.components[$index]"
        val errors = mutableListOf<LoadError>()
        
        val id = jsonObject["id"]?.jsonPrimitive?.contentOrNull
        if (id == null) {
            errors.add(LoadError(
                code = "MISSING_FIELD",
                message = "Campo 'id' é obrigatório no componente",
                path = "$basePath.id"
            ))
        }
        
        val type = jsonObject["type"]?.jsonPrimitive?.contentOrNull
        if (type == null) {
            errors.add(LoadError(
                code = "MISSING_FIELD",
                message = "Campo 'type' é obrigatório no componente",
                path = "$basePath.type"
            ))
        }
        
        val name = jsonObject["name"]?.jsonPrimitive?.contentOrNull
        if (name == null) {
            errors.add(LoadError(
                code = "MISSING_FIELD",
                message = "Campo 'name' é obrigatório no componente",
                path = "$basePath.name"
            ))
        }
        
        if (errors.isNotEmpty()) {
            return LoadResult.failure(errors)
        }
        
        // Parsear propriedades
        val properties = mutableMapOf<String, PropertyDefinition>()
        val propsObject = jsonObject["properties"] as? JsonObject
        if (propsObject != null) {
            for ((key, value) in propsObject) {
                properties[key] = parsePropertyDefinition(value)
            }
        }
        
        // Parsear posição
        val position = (jsonObject["position"] as? JsonObject)?.let {
            PositionDefinition(
                x = it["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                y = it["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            )
        }
        
        // Parsear metadados
        val metadata = parseComponentMetadata(jsonObject["metadata"] as? JsonObject)
        
        return LoadResult.success(
            ComponentDefinition(
                id = id!!,
                type = type!!,
                name = name!!,
                properties = properties,
                position = position,
                metadata = metadata
            )
        )
    }
    
    /**
     * Parseia um objeto JSON para ConnectionDefinition.
     */
    private fun parseConnectionDefinition(
        jsonObject: JsonObject,
        index: Int
    ): LoadResult<ConnectionDefinition> {
        val basePath = "flow.connections[$index]"
        val errors = mutableListOf<LoadError>()
        
        val id = jsonObject["id"]?.jsonPrimitive?.contentOrNull
        if (id == null) {
            errors.add(LoadError(
                code = "MISSING_FIELD",
                message = "Campo 'id' é obrigatório na conexão",
                path = "$basePath.id"
            ))
        }
        
        // Parsear source
        val sourceObj = jsonObject["source"] as? JsonObject
        if (sourceObj == null) {
            errors.add(LoadError(
                code = "MISSING_FIELD",
                message = "Campo 'source' é obrigatório na conexão",
                path = "$basePath.source"
            ))
        }
        
        val sourceComponentId = sourceObj?.get("componentId")?.jsonPrimitive?.contentOrNull
        val sourcePortId = sourceObj?.get("portId")?.jsonPrimitive?.contentOrNull
        
        if (sourceObj != null && (sourceComponentId == null || sourcePortId == null)) {
            if (sourceComponentId == null) {
                errors.add(LoadError(
                    code = "MISSING_FIELD",
                    message = "Campo 'componentId' é obrigatório em source",
                    path = "$basePath.source.componentId"
                ))
            }
            if (sourcePortId == null) {
                errors.add(LoadError(
                    code = "MISSING_FIELD",
                    message = "Campo 'portId' é obrigatório em source",
                    path = "$basePath.source.portId"
                ))
            }
        }
        
        // Parsear target
        val targetObj = jsonObject["target"] as? JsonObject
        if (targetObj == null) {
            errors.add(LoadError(
                code = "MISSING_FIELD",
                message = "Campo 'target' é obrigatório na conexão",
                path = "$basePath.target"
            ))
        }
        
        val targetComponentId = targetObj?.get("componentId")?.jsonPrimitive?.contentOrNull
        val targetPortId = targetObj?.get("portId")?.jsonPrimitive?.contentOrNull
        
        if (targetObj != null && (targetComponentId == null || targetPortId == null)) {
            if (targetComponentId == null) {
                errors.add(LoadError(
                    code = "MISSING_FIELD",
                    message = "Campo 'componentId' é obrigatório em target",
                    path = "$basePath.target.componentId"
                ))
            }
            if (targetPortId == null) {
                errors.add(LoadError(
                    code = "MISSING_FIELD",
                    message = "Campo 'portId' é obrigatório em target",
                    path = "$basePath.target.portId"
                ))
            }
        }
        
        if (errors.isNotEmpty()) {
            return LoadResult.failure(errors)
        }
        
        val metadata = parseConnectionMetadata(jsonObject["metadata"] as? JsonObject)
        
        return LoadResult.success(
            ConnectionDefinition(
                id = id!!,
                source = PortReference(
                    componentId = sourceComponentId!!,
                    portId = sourcePortId!!
                ),
                target = PortReference(
                    componentId = targetComponentId!!,
                    portId = targetPortId!!
                ),
                metadata = metadata
            )
        )
    }
    
    /**
     * Parseia um valor JSON para PropertyDefinition.
     */
    private fun parsePropertyDefinition(element: JsonElement): PropertyDefinition {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> {
                        val content = element.content
                        // Verifica se é uma expressão (começa com ${ ou {{)
                        if (content.startsWith("\${") || content.startsWith("{{")) {
                            PropertyDefinition.Expression(content)
                        } else {
                            PropertyDefinition.StringValue(content)
                        }
                    }
                    element.booleanOrNull != null -> PropertyDefinition.BooleanValue(element.boolean)
                    element.doubleOrNull != null -> PropertyDefinition.NumberValue(element.double)
                    else -> PropertyDefinition.NullValue
                }
            }
            is JsonNull -> PropertyDefinition.NullValue
            is JsonObject -> PropertyDefinition.ObjectValue(
                element.mapValues { parsePropertyDefinition(it.value) }
            )
            is JsonArray -> PropertyDefinition.ArrayValue(
                element.map { parsePropertyDefinition(it) }
            )
        }
    }
    
    /**
     * Parseia metadados de fluxo.
     */
    private fun parseFlowMetadata(jsonObject: JsonObject?): FlowMetadataDefinition {
        if (jsonObject == null) return FlowMetadataDefinition()
        
        return FlowMetadataDefinition(
            description = jsonObject["description"]?.jsonPrimitive?.contentOrNull ?: "",
            author = jsonObject["author"]?.jsonPrimitive?.contentOrNull ?: "",
            createdAt = jsonObject["createdAt"]?.jsonPrimitive?.contentOrNull,
            updatedAt = jsonObject["updatedAt"]?.jsonPrimitive?.contentOrNull,
            tags = (jsonObject["tags"] as? JsonArray)?.mapNotNull { 
                it.jsonPrimitive.contentOrNull 
            } ?: emptyList(),
            customData = (jsonObject["customData"] as? JsonObject)?.mapValues { 
                it.value.jsonPrimitive.contentOrNull ?: "" 
            } ?: emptyMap()
        )
    }
    
    /**
     * Parseia metadados de componente.
     */
    private fun parseComponentMetadata(jsonObject: JsonObject?): ComponentMetadataDefinition {
        if (jsonObject == null) return ComponentMetadataDefinition()
        
        return ComponentMetadataDefinition(
            description = jsonObject["description"]?.jsonPrimitive?.contentOrNull ?: "",
            tags = (jsonObject["tags"] as? JsonArray)?.mapNotNull { 
                it.jsonPrimitive.contentOrNull 
            } ?: emptyList(),
            customData = (jsonObject["customData"] as? JsonObject)?.mapValues { 
                it.value.jsonPrimitive.contentOrNull ?: "" 
            } ?: emptyMap()
        )
    }
    
    /**
     * Parseia metadados de conexão.
     */
    private fun parseConnectionMetadata(jsonObject: JsonObject?): ConnectionMetadataDefinition {
        if (jsonObject == null) return ConnectionMetadataDefinition()
        
        return ConnectionMetadataDefinition(
            label = jsonObject["label"]?.jsonPrimitive?.contentOrNull ?: "",
            customData = (jsonObject["customData"] as? JsonObject)?.mapValues { 
                it.value.jsonPrimitive.contentOrNull ?: "" 
            } ?: emptyMap()
        )
    }
    
    /**
     * Retorna as versões de schema suportadas pelo parser.
     */
    fun getSupportedVersions(): List<String> {
        return listOf("1.0.0", "1.0.1", "1.1.0")
    }
}
