package com.kex.vikrsaathi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kex.vikrsaathi.data.entity.InvoiceTemplateVersionEntity

@Dao
interface InvoiceTemplateVersionDao {

    @Query(
        "SELECT * FROM invoice_template_versions WHERE templateId = :templateId ORDER BY versionNumber DESC"
    )
    suspend fun getVersionsForTemplate(templateId: Long): List<InvoiceTemplateVersionEntity>

    @Query("SELECT * FROM invoice_template_versions ORDER BY templateId ASC, versionNumber DESC")
    suspend fun getAllVersions(): List<InvoiceTemplateVersionEntity>

    @Query("SELECT * FROM invoice_template_versions WHERE id = :id")
    suspend fun getById(id: Long): InvoiceTemplateVersionEntity?

    @Query("SELECT MAX(versionNumber) FROM invoice_template_versions WHERE templateId = :templateId")
    suspend fun getMaxVersionNumber(templateId: Long): Int?

    @Insert
    suspend fun insert(entity: InvoiceTemplateVersionEntity): Long

    @Query("DELETE FROM invoice_template_versions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
