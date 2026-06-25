package com.loctell.vikrsaathi.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS invoice_templates (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                isDefault INTEGER NOT NULL,
                pageWidthPt INTEGER NOT NULL,
                pageHeightPt INTEGER NOT NULL,
                marginLeft REAL NOT NULL,
                marginTop REAL NOT NULL,
                marginRight REAL NOT NULL,
                marginBottom REAL NOT NULL,
                elementsJson TEXT NOT NULL,
                version INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS invoice_template_versions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                templateId INTEGER NOT NULL,
                versionNumber INTEGER NOT NULL,
                snapshotJson TEXT NOT NULL,
                savedAt INTEGER NOT NULL,
                FOREIGN KEY(templateId) REFERENCES invoice_templates(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_invoice_template_versions_templateId ON invoice_template_versions(templateId)"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE bills ADD COLUMN invoiceCounter INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            """
            UPDATE bills SET invoiceCounter = CAST(billNumber AS INTEGER)
            WHERE billNumber GLOB '[0-9]*'
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_bills_invoiceCounter ON bills(invoiceCounter)"
        )
    }
}
