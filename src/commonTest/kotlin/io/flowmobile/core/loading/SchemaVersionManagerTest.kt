package io.flowmobile.core.loading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes para o SchemaVersionManager.
 * Verifica gerenciamento de versões, migração e deprecação.
 */
class SchemaVersionManagerTest {
    
    private val manager = SchemaVersionManager()
    
    // ==================== Versão Atual ====================
    
    @Test
    fun testGetCurrentVersion() {
        val version = manager.getCurrentVersion()
        
        assertEquals("1.0.0", version)
    }
    
    // ==================== Suporte a Versões ====================
    
    @Test
    fun testIsSupported_CurrentVersion() {
        assertTrue(manager.isSupported("1.0.0"))
    }
    
    @Test
    fun testIsSupported_MinorVersions() {
        assertTrue(manager.isSupported("1.0.1"))
        assertTrue(manager.isSupported("1.1.0"))
    }
    
    @Test
    fun testIsSupported_LegacyVersions() {
        assertTrue(manager.isSupported("0.9.0"))
        assertTrue(manager.isSupported("0.9.1"))
    }
    
    @Test
    fun testIsSupported_UnsupportedVersion() {
        assertFalse(manager.isSupported("99.0.0"))
        assertFalse(manager.isSupported("0.1.0"))
        assertFalse(manager.isSupported("invalid"))
    }
    
    // ==================== Deprecação ====================
    
    @Test
    fun testIsDeprecated_LegacyVersions() {
        assertTrue(manager.isDeprecated("0.9.0"))
        assertTrue(manager.isDeprecated("0.9.1"))
    }
    
    @Test
    fun testIsDeprecated_CurrentVersions() {
        assertFalse(manager.isDeprecated("1.0.0"))
        assertFalse(manager.isDeprecated("1.0.1"))
        assertFalse(manager.isDeprecated("1.1.0"))
    }
    
    // ==================== Read-Only ====================
    
    @Test
    fun testIsReadOnly_LegacyVersions() {
        assertTrue(manager.isReadOnly("0.9.0"))
        assertTrue(manager.isReadOnly("0.9.1"))
    }
    
    @Test
    fun testIsReadOnly_CurrentVersions() {
        assertFalse(manager.isReadOnly("1.0.0"))
        assertFalse(manager.isReadOnly("1.0.1"))
    }
    
    // ==================== Lista de Versões ====================
    
    @Test
    fun testGetSupportedVersions() {
        val versions = manager.getSupportedVersions()
        
        assertTrue(versions.isNotEmpty())
        assertTrue(versions.contains("1.0.0"))
        assertTrue(versions.contains("1.0.1"))
        assertTrue(versions.contains("1.1.0"))
        assertTrue(versions.contains("0.9.0"))
        assertTrue(versions.contains("0.9.1"))
    }
    
    // ==================== Migrador ====================
    
    @Test
    fun testGetMigrator_ValidMigration() {
        val migrator = manager.getMigrator("0.9.0", "1.0.0")
        
        assertNotNull(migrator)
        assertEquals("0.9.0", migrator.fromVersion)
        assertEquals("1.0.0", migrator.toVersion)
    }
    
    @Test
    fun testGetMigrator_SameVersion() {
        val migrator = manager.getMigrator("1.0.0", "1.0.0")
        
        assertNull(migrator) // Não pode migrar para mesma versão
    }
    
    @Test
    fun testGetMigrator_DowngradeDenied() {
        val migrator = manager.getMigrator("1.0.0", "0.9.0")
        
        assertNull(migrator) // Não pode fazer downgrade
    }
    
    @Test
    fun testGetMigrator_UnsupportedVersion() {
        val migrator = manager.getMigrator("0.9.0", "99.0.0")
        
        assertNull(migrator)
    }
    
    // ==================== Migração ====================
    
    @Test
    fun testMigrate_SameVersion() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-1",
                "name": "Test",
                "version": "1.0.0",
                "components": [],
                "connections": []
            }
        }
        """.trimIndent()
        
        val result = manager.migrate(json, "1.0.0")
        
        assertTrue(result.success)
        assertNotNull(result.migratedJson)
        assertTrue(result.warnings.isNotEmpty())
    }
    
    @Test
    fun testMigrate_InvalidJson() {
        val result = manager.migrate("invalid json", "1.0.0")
        
        assertFalse(result.success)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun testMigrate_NoMigrationPath() {
        val json = """
        {
            "schemaVersion": "1.0.0",
            "flow": {
                "id": "flow-1",
                "name": "Test",
                "version": "1.0.0",
                "components": [],
                "connections": []
            }
        }
        """.trimIndent()
        
        val result = manager.migrate(json, "0.9.0") // Downgrade não permitido
        
        assertFalse(result.success)
    }
    
    // ==================== Warnings de Deprecação ====================
    
    @Test
    fun testGetDeprecationWarnings_LegacyVersion() {
        val warnings = manager.getDeprecationWarnings("0.9.0")
        
        assertTrue(warnings.isNotEmpty())
        assertTrue(warnings.any { it.code == "DEPRECATED_SCHEMA_VERSION" })
        assertTrue(warnings.any { it.code == "READ_ONLY_SCHEMA_VERSION" })
        assertTrue(warnings.any { it.code == "LEGACY_SCHEMA" })
    }
    
    @Test
    fun testGetDeprecationWarnings_CurrentVersion() {
        val warnings = manager.getDeprecationWarnings("1.0.0")
        
        assertTrue(warnings.isEmpty())
    }
    
    // ==================== Migração de Schema 0.9.x -> 1.0.0 ====================
    
    @Test
    fun testMigration_0_9_to_1_0_ReplacesNodes() {
        val migration = Migration_0_9_to_1_0()
        
        val input = """{"schemaVersion": "0.9.0", "nodes": []}"""
        val output = migration.transform(input)
        
        assertTrue(output.contains("\"components\""))
        assertFalse(output.contains("\"nodes\""))
    }
    
    @Test
    fun testMigration_0_9_to_1_0_ReplacesEdges() {
        val migration = Migration_0_9_to_1_0()
        
        val input = """{"schemaVersion": "0.9.0", "edges": []}"""
        val output = migration.transform(input)
        
        assertTrue(output.contains("\"connections\""))
        assertFalse(output.contains("\"edges\""))
    }
    
    @Test
    fun testMigration_0_9_to_1_0_ReplacesFromTo() {
        val migration = Migration_0_9_to_1_0()
        
        val input = """{"schemaVersion": "0.9.0", "from": "a", "to": "b"}"""
        val output = migration.transform(input)
        
        assertTrue(output.contains("\"source\""))
        assertTrue(output.contains("\"target\""))
    }
    
    @Test
    fun testMigration_0_9_to_1_0_UpdatesVersion() {
        val migration = Migration_0_9_to_1_0()
        
        val input = """{"schemaVersion": "0.9.1"}"""
        val output = migration.transform(input)
        
        assertTrue(output.contains("\"1.0.0\""))
    }
    
    // ==================== NoOpMigration ====================
    
    @Test
    fun testNoOpMigration_UpdatesVersion() {
        val migration = NoOpMigration("1.0.0", "1.0.1")
        
        val input = """{"schemaVersion": "1.0.0"}"""
        val output = migration.transform(input)
        
        assertTrue(output.contains("\"1.0.1\""))
        assertFalse(output.contains("\"1.0.0\""))
    }
    
    @Test
    fun testNoOpMigration_WithoutSpaces() {
        val migration = NoOpMigration("1.0.0", "1.0.1")
        
        val input = """{"schemaVersion":"1.0.0"}"""
        val output = migration.transform(input)
        
        assertTrue(output.contains("\"1.0.1\""))
    }
}
