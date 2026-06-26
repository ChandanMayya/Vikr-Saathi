package com.kex.vikrsaathi.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kex.vikrsaathi.data.entity.InvoiceTemplateEntity

@Dao
interface InvoiceTemplateDao {

    @Query("SELECT * FROM invoice_templates ORDER BY isDefault DESC, name ASC")
    fun getAllTemplates(): LiveData<List<InvoiceTemplateEntity>>

    @Query("SELECT * FROM invoice_templates ORDER BY isDefault DESC, name ASC")
    suspend fun getAllTemplatesSync(): List<InvoiceTemplateEntity>

    @Query("SELECT * FROM invoice_templates WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultTemplate(): InvoiceTemplateEntity?

    @Query("SELECT * FROM invoice_templates WHERE id = :id")
    suspend fun getById(id: Long): InvoiceTemplateEntity?

    @Query("SELECT COUNT(*) FROM invoice_templates")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InvoiceTemplateEntity): Long

    @Query("UPDATE invoice_templates SET isDefault = 0")
    suspend fun clearDefaultFlags()

    @Query("UPDATE invoice_templates SET isDefault = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markAsDefault(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Transaction
    suspend fun setAsDefault(id: Long) {
        clearDefaultFlags()
        markAsDefault(id)
    }

    @Query("DELETE FROM invoice_templates")
    suspend fun deleteAll()
}
