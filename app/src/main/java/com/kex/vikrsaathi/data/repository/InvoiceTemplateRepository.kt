package com.kex.vikrsaathi.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.kex.vikrsaathi.data.dao.InvoiceTemplateDao
import com.kex.vikrsaathi.data.dao.InvoiceTemplateVersionDao
import com.kex.vikrsaathi.data.entity.InvoiceTemplateEntity
import com.kex.vikrsaathi.data.entity.InvoiceTemplateVersionEntity
import com.kex.vikrsaathi.data.model.template.DefaultInvoiceTemplate
import com.kex.vikrsaathi.data.model.template.InvoiceTemplate
import com.kex.vikrsaathi.data.model.template.InvoiceTemplateVersion
import com.kex.vikrsaathi.data.model.template.TemplateJsonCodec

class InvoiceTemplateRepository(
    private val dao: InvoiceTemplateDao,
    private val versionDao: InvoiceTemplateVersionDao
) {

    companion object {
        private const val MAX_VERSIONS_PER_TEMPLATE = 20
    }

    val allTemplates: LiveData<List<InvoiceTemplate>> =
        dao.getAllTemplates().map { list -> list.map { it.toDomain() } }

    suspend fun ensureDefaultTemplateExists() {
        if (dao.count() > 0) return
        val template = DefaultInvoiceTemplate.create()
        insert(template)
    }

    suspend fun getDefaultTemplate(): InvoiceTemplate {
        val entity = dao.getDefaultTemplate()
        if (entity != null) return entity.toDomain()
        ensureDefaultTemplateExists()
        return dao.getDefaultTemplate()?.toDomain() ?: DefaultInvoiceTemplate.create()
    }

    suspend fun setAsDefault(templateId: Long) {
        dao.setAsDefault(templateId)
    }

    suspend fun insert(template: InvoiceTemplate): Long {
        return dao.insert(template.toEntity())
    }

    suspend fun getById(id: Long): InvoiceTemplate? {
        return dao.getById(id)?.toDomain()
    }

    suspend fun update(template: InvoiceTemplate) {
        val updated = template.copy(
            version = template.version + 1,
            updatedAt = System.currentTimeMillis()
        )
        dao.insert(updated.toEntity())
        saveVersionSnapshot(updated)
    }

    suspend fun duplicateTemplate(sourceId: Long, newName: String): Long {
        val source = getById(sourceId) ?: return 0L
        val duplicated = source.copy(
            id = 0,
            name = newName,
            isDefault = false,
            elements = source.elements.map { element ->
                element.copy(id = java.util.UUID.randomUUID().toString())
            },
            updatedAt = System.currentTimeMillis()
        )
        return insert(duplicated)
    }

    suspend fun createBlankFromDefault(newName: String): Long {
        val default = getDefaultTemplate()
        return duplicateTemplate(default.id, newName)
    }

    suspend fun getVersions(templateId: Long): List<InvoiceTemplateVersion> {
        return versionDao.getVersionsForTemplate(templateId).map { entity ->
            val parsed = TemplateJsonCodec.fromJson(entity.snapshotJson)
            InvoiceTemplateVersion(
                id = entity.id,
                templateId = entity.templateId,
                versionNumber = entity.versionNumber,
                savedAt = entity.savedAt,
                elementCount = parsed.elements.size
            )
        }
    }

    suspend fun restoreFromVersion(versionId: Long): InvoiceTemplate? {
        val versionEntity = versionDao.getById(versionId) ?: return null
        val current = getById(versionEntity.templateId) ?: return null
        val snapshot = TemplateJsonCodec.fromJson(versionEntity.snapshotJson)
        return snapshot.copy(
            id = current.id,
            name = current.name,
            isDefault = current.isDefault,
            version = current.version
        )
    }

    private suspend fun saveVersionSnapshot(template: InvoiceTemplate) {
        if (template.id <= 0L) return
        val nextNumber = (versionDao.getMaxVersionNumber(template.id) ?: 0) + 1
        versionDao.insert(
            InvoiceTemplateVersionEntity(
                templateId = template.id,
                versionNumber = nextNumber,
                snapshotJson = TemplateJsonCodec.toJson(template),
                savedAt = System.currentTimeMillis()
            )
        )
        pruneOldVersions(template.id)
    }

    private suspend fun pruneOldVersions(templateId: Long) {
        val versions = versionDao.getVersionsForTemplate(templateId)
        if (versions.size <= MAX_VERSIONS_PER_TEMPLATE) return
        versions.drop(MAX_VERSIONS_PER_TEMPLATE).forEach { versionDao.deleteById(it.id) }
    }

    private fun InvoiceTemplateEntity.toDomain(): InvoiceTemplate {
        val parsed = TemplateJsonCodec.fromJson(elementsJson, id = id, isDefault = isDefault, name = name)
        return parsed.copy(
            id = id,
            name = name,
            isDefault = isDefault,
            pageWidthPt = pageWidthPt,
            pageHeightPt = pageHeightPt,
            marginLeft = marginLeft,
            marginTop = marginTop,
            marginRight = marginRight,
            marginBottom = marginBottom,
            version = version,
            updatedAt = updatedAt
        )
    }

    private fun InvoiceTemplate.toEntity(): InvoiceTemplateEntity {
        return InvoiceTemplateEntity(
            id = id,
            name = name,
            isDefault = isDefault,
            pageWidthPt = pageWidthPt,
            pageHeightPt = pageHeightPt,
            marginLeft = marginLeft,
            marginTop = marginTop,
            marginRight = marginRight,
            marginBottom = marginBottom,
            elementsJson = TemplateJsonCodec.toJson(this),
            version = version,
            updatedAt = updatedAt
        )
    }
}
