package com.kex.vikrsaathi.data.database

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

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bill_drafts (
                id INTEGER PRIMARY KEY NOT NULL,
                customerId INTEGER,
                customerName TEXT NOT NULL,
                buyerAddress TEXT NOT NULL,
                buyerPhone TEXT NOT NULL,
                lineItemsJson TEXT NOT NULL,
                grandTotal REAL NOT NULL,
                itemCount INTEGER NOT NULL,
                heldAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bill_drafts_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                customerId INTEGER,
                customerName TEXT NOT NULL,
                buyerAddress TEXT NOT NULL,
                buyerPhone TEXT NOT NULL,
                lineItemsJson TEXT NOT NULL,
                grandTotal REAL NOT NULL,
                itemCount INTEGER NOT NULL,
                heldAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO bill_drafts_new (
                customerId, customerName, buyerAddress, buyerPhone,
                lineItemsJson, grandTotal, itemCount, heldAt
            )
            SELECT customerId, customerName, buyerAddress, buyerPhone,
                lineItemsJson, grandTotal, itemCount, heldAt
            FROM bill_drafts
            """.trimIndent()
        )
        db.execSQL("DROP TABLE bill_drafts")
        db.execSQL("ALTER TABLE bill_drafts_new RENAME TO bill_drafts")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE items ADD COLUMN stockQty INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stock_movements (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                itemId INTEGER NOT NULL,
                delta INTEGER NOT NULL,
                quantityAfter INTEGER NOT NULL,
                type TEXT NOT NULL,
                referenceType TEXT,
                referenceId INTEGER,
                note TEXT,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(itemId) REFERENCES items(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_stock_movements_itemId_createdAt ON stock_movements(itemId, createdAt)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_stock_movements_referenceType_referenceId ON stock_movements(referenceType, referenceId)"
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE invoice_templates ADD COLUMN sheetType TEXT NOT NULL DEFAULT 'A4'"
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE bills ADD COLUMN roundOff REAL NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE bill_items ADD COLUMN roundOff REAL NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE bill_drafts ADD COLUMN roundOff REAL NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bills ADD COLUMN paymentStatus TEXT NOT NULL DEFAULT 'LEGACY'")
        db.execSQL("ALTER TABLE bills ADD COLUMN paymentLabel TEXT NOT NULL DEFAULT 'LEGACY'")
        db.execSQL("ALTER TABLE items ADD COLUMN preferredVendorId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_items_preferredVendorId ON items(preferredVendorId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bill_payments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                billId INTEGER NOT NULL,
                method TEXT NOT NULL,
                amountPaise INTEGER NOT NULL,
                FOREIGN KEY(billId) REFERENCES bills(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_bill_payments_billId_method ON bill_payments(billId, method)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS customer_ledger_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                customerId INTEGER NOT NULL,
                entryType TEXT NOT NULL,
                amountPaise INTEGER NOT NULL,
                occurredAt INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                method TEXT,
                note TEXT,
                reason TEXT,
                referenceType TEXT,
                referenceId INTEGER,
                reversesEntryId INTEGER,
                billId INTEGER,
                FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_ledger_entries_customerId_occurredAt ON customer_ledger_entries(customerId, occurredAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_ledger_entries_billId ON customer_ledger_entries(billId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_ledger_entries_referenceType_referenceId ON customer_ledger_entries(referenceType, referenceId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS customer_receipts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                customerId INTEGER NOT NULL,
                amountPaise INTEGER NOT NULL,
                occurredAt INTEGER NOT NULL,
                method TEXT NOT NULL,
                referenceNote TEXT,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_receipts_customerId ON customer_receipts(customerId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS customer_receipt_allocations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                receiptId INTEGER NOT NULL,
                billId INTEGER NOT NULL,
                amountPaise INTEGER NOT NULL,
                FOREIGN KEY(receiptId) REFERENCES customer_receipts(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_receipt_allocations_receiptId ON customer_receipt_allocations(receiptId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_receipt_allocations_billId ON customer_receipt_allocations(billId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sales_returns (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                customerId INTEGER NOT NULL,
                billId INTEGER,
                reason TEXT NOT NULL,
                moneyRefundedPaise INTEGER NOT NULL,
                creditAdjustmentPaise INTEGER NOT NULL,
                occurredAt INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_returns_customerId ON sales_returns(customerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_returns_billId ON sales_returns(billId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sales_return_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                returnId INTEGER NOT NULL,
                itemId INTEGER,
                itemName TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                FOREIGN KEY(returnId) REFERENCES sales_returns(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_return_lines_returnId ON sales_return_lines(returnId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_return_lines_itemId ON sales_return_lines(itemId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vendors (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                phone TEXT NOT NULL,
                address TEXT NOT NULL,
                email TEXT NOT NULL,
                notes TEXT NOT NULL,
                archived INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendors_name ON vendors(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendors_archived ON vendors(archived)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vendor_ledger_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                vendorId INTEGER NOT NULL,
                entryType TEXT NOT NULL,
                amountPaise INTEGER NOT NULL,
                occurredAt INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                method TEXT,
                note TEXT,
                reason TEXT,
                referenceType TEXT,
                referenceId INTEGER,
                reversesEntryId INTEGER,
                invoiceId INTEGER,
                FOREIGN KEY(vendorId) REFERENCES vendors(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_ledger_entries_vendorId_occurredAt ON vendor_ledger_entries(vendorId, occurredAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_ledger_entries_invoiceId ON vendor_ledger_entries(invoiceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_ledger_entries_referenceType_referenceId ON vendor_ledger_entries(referenceType, referenceId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vendor_payments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                vendorId INTEGER NOT NULL,
                amountPaise INTEGER NOT NULL,
                occurredAt INTEGER NOT NULL,
                method TEXT NOT NULL,
                referenceNote TEXT,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(vendorId) REFERENCES vendors(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_payments_vendorId ON vendor_payments(vendorId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vendor_payment_allocations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                paymentId INTEGER NOT NULL,
                invoiceId INTEGER NOT NULL,
                amountPaise INTEGER NOT NULL,
                FOREIGN KEY(paymentId) REFERENCES vendor_payments(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_payment_allocations_paymentId ON vendor_payment_allocations(paymentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_payment_allocations_invoiceId ON vendor_payment_allocations(invoiceId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                vendorId INTEGER NOT NULL,
                orderNumber TEXT NOT NULL,
                quoteId INTEGER,
                expectedDelivery INTEGER,
                notes TEXT NOT NULL,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(vendorId) REFERENCES vendors(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_vendorId ON purchase_orders(vendorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_quoteId ON purchase_orders(quoteId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_order_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                orderId INTEGER NOT NULL,
                itemId INTEGER,
                itemName TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                ratePaise INTEGER NOT NULL,
                FOREIGN KEY(orderId) REFERENCES purchase_orders(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_order_lines_orderId ON purchase_order_lines(orderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_order_lines_itemId ON purchase_order_lines(itemId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS goods_receipts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                vendorId INTEGER NOT NULL,
                orderId INTEGER,
                receiptNumber TEXT NOT NULL,
                receivedAt INTEGER NOT NULL,
                notes TEXT NOT NULL,
                directReceipt INTEGER NOT NULL,
                FOREIGN KEY(vendorId) REFERENCES vendors(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(orderId) REFERENCES purchase_orders(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goods_receipts_vendorId ON goods_receipts(vendorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goods_receipts_orderId ON goods_receipts(orderId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS goods_receipt_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                receiptId INTEGER NOT NULL,
                orderLineId INTEGER,
                itemId INTEGER,
                itemName TEXT NOT NULL,
                orderedQty INTEGER NOT NULL,
                receivedQty INTEGER NOT NULL,
                shortQty INTEGER NOT NULL,
                damagedQty INTEGER NOT NULL,
                rejectedQty INTEGER NOT NULL,
                acceptedQty INTEGER NOT NULL,
                FOREIGN KEY(receiptId) REFERENCES goods_receipts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goods_receipt_lines_receiptId ON goods_receipt_lines(receiptId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goods_receipt_lines_itemId ON goods_receipt_lines(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goods_receipt_lines_orderLineId ON goods_receipt_lines(orderLineId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_invoices (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                vendorId INTEGER NOT NULL,
                invoiceNumber TEXT NOT NULL,
                invoiceDate INTEGER NOT NULL,
                orderId INTEGER,
                receiptId INTEGER,
                deliveryChargesPaise INTEGER NOT NULL,
                otherChargesPaise INTEGER NOT NULL,
                discountPaise INTEGER NOT NULL,
                totalPaise INTEGER NOT NULL,
                notes TEXT NOT NULL,
                FOREIGN KEY(vendorId) REFERENCES vendors(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_invoices_vendorId ON purchase_invoices(vendorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_invoices_orderId ON purchase_invoices(orderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_invoices_receiptId ON purchase_invoices(receiptId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_invoice_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                invoiceId INTEGER NOT NULL,
                itemId INTEGER,
                itemName TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                ratePaise INTEGER NOT NULL,
                lineTotalPaise INTEGER NOT NULL,
                FOREIGN KEY(invoiceId) REFERENCES purchase_invoices(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_invoice_lines_invoiceId ON purchase_invoice_lines(invoiceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_invoice_lines_itemId ON purchase_invoice_lines(itemId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vendor_quotes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                vendorId INTEGER NOT NULL,
                quoteDate INTEGER NOT NULL,
                validUntil INTEGER,
                deliveryChargesPaise INTEGER NOT NULL,
                discountPaise INTEGER NOT NULL,
                expectedDelivery INTEGER,
                notes TEXT NOT NULL,
                FOREIGN KEY(vendorId) REFERENCES vendors(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_quotes_vendorId ON vendor_quotes(vendorId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vendor_quote_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                quoteId INTEGER NOT NULL,
                itemId INTEGER,
                itemName TEXT NOT NULL,
                ratePaise INTEGER NOT NULL,
                availableQty INTEGER NOT NULL,
                notes TEXT NOT NULL,
                FOREIGN KEY(quoteId) REFERENCES vendor_quotes(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_quote_lines_quoteId ON vendor_quote_lines(quoteId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_quote_lines_itemId ON vendor_quote_lines(itemId)")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                requestNumber TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                requiredBy INTEGER,
                status TEXT NOT NULL,
                notes TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS purchase_request_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                requestId INTEGER NOT NULL,
                itemId INTEGER,
                itemName TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                notes TEXT NOT NULL,
                FOREIGN KEY(requestId) REFERENCES purchase_requests(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_request_lines_requestId ON purchase_request_lines(requestId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_request_lines_itemId ON purchase_request_lines(itemId)")

        // Recreate quote and order tables. minSdk 24 cannot ADD COLUMN with a foreign key.
        db.execSQL(
            """
            CREATE TABLE vendor_quotes_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                vendorId INTEGER NOT NULL,
                quoteDate INTEGER NOT NULL,
                validUntil INTEGER,
                deliveryChargesPaise INTEGER NOT NULL,
                discountPaise INTEGER NOT NULL,
                expectedDelivery INTEGER,
                notes TEXT NOT NULL,
                requestId INTEGER,
                FOREIGN KEY(vendorId) REFERENCES vendors(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(requestId) REFERENCES purchase_requests(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO vendor_quotes_new (
                id, vendorId, quoteDate, validUntil, deliveryChargesPaise, discountPaise, expectedDelivery, notes, requestId
            )
            SELECT id, vendorId, quoteDate, validUntil, deliveryChargesPaise, discountPaise, expectedDelivery, notes, NULL
            FROM vendor_quotes
            """.trimIndent()
        )
        db.execSQL("DROP TABLE vendor_quotes")
        db.execSQL("ALTER TABLE vendor_quotes_new RENAME TO vendor_quotes")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_quotes_vendorId ON vendor_quotes(vendorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_quotes_requestId ON vendor_quotes(requestId)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_vendor_quotes_vendorId_requestId ON vendor_quotes(vendorId, requestId)"
        )
        db.execSQL(
            "INSERT OR REPLACE INTO sqlite_sequence(name, seq) SELECT 'vendor_quotes', IFNULL(MAX(id), 0) FROM vendor_quotes"
        )

        db.execSQL(
            """
            CREATE TABLE vendor_quote_lines_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                quoteId INTEGER NOT NULL,
                itemId INTEGER,
                itemName TEXT NOT NULL,
                ratePaise INTEGER NOT NULL,
                availableQty INTEGER NOT NULL,
                notes TEXT NOT NULL,
                requestLineId INTEGER,
                availability TEXT NOT NULL,
                FOREIGN KEY(quoteId) REFERENCES vendor_quotes(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(requestLineId) REFERENCES purchase_request_lines(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO vendor_quote_lines_new (
                id, quoteId, itemId, itemName, ratePaise, availableQty, notes, requestLineId, availability
            )
            SELECT id, quoteId, itemId, itemName, ratePaise, availableQty, notes, NULL, 'QUOTED'
            FROM vendor_quote_lines
            """.trimIndent()
        )
        db.execSQL("DROP TABLE vendor_quote_lines")
        db.execSQL("ALTER TABLE vendor_quote_lines_new RENAME TO vendor_quote_lines")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_quote_lines_quoteId ON vendor_quote_lines(quoteId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_quote_lines_itemId ON vendor_quote_lines(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vendor_quote_lines_requestLineId ON vendor_quote_lines(requestLineId)")
        db.execSQL(
            "INSERT OR REPLACE INTO sqlite_sequence(name, seq) SELECT 'vendor_quote_lines', IFNULL(MAX(id), 0) FROM vendor_quote_lines"
        )

        db.execSQL(
            """
            CREATE TABLE purchase_orders_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                vendorId INTEGER NOT NULL,
                orderNumber TEXT NOT NULL,
                quoteId INTEGER,
                expectedDelivery INTEGER,
                notes TEXT NOT NULL,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                requestId INTEGER,
                FOREIGN KEY(vendorId) REFERENCES vendors(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(requestId) REFERENCES purchase_requests(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO purchase_orders_new (
                id, vendorId, orderNumber, quoteId, expectedDelivery, notes, status, createdAt, requestId
            )
            SELECT id, vendorId, orderNumber, quoteId, expectedDelivery, notes, status, createdAt, NULL
            FROM purchase_orders
            """.trimIndent()
        )
        db.execSQL("DROP TABLE purchase_orders")
        db.execSQL("ALTER TABLE purchase_orders_new RENAME TO purchase_orders")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_vendorId ON purchase_orders(vendorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_quoteId ON purchase_orders(quoteId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_requestId ON purchase_orders(requestId)")
        db.execSQL(
            "INSERT OR REPLACE INTO sqlite_sequence(name, seq) SELECT 'purchase_orders', IFNULL(MAX(id), 0) FROM purchase_orders"
        )

        db.execSQL(
            """
            CREATE TABLE purchase_order_lines_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                orderId INTEGER NOT NULL,
                itemId INTEGER,
                itemName TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                ratePaise INTEGER NOT NULL,
                requestLineId INTEGER,
                FOREIGN KEY(orderId) REFERENCES purchase_orders(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(requestLineId) REFERENCES purchase_request_lines(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO purchase_order_lines_new (
                id, orderId, itemId, itemName, quantity, ratePaise, requestLineId
            )
            SELECT id, orderId, itemId, itemName, quantity, ratePaise, NULL
            FROM purchase_order_lines
            """.trimIndent()
        )
        db.execSQL("DROP TABLE purchase_order_lines")
        db.execSQL("ALTER TABLE purchase_order_lines_new RENAME TO purchase_order_lines")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_order_lines_orderId ON purchase_order_lines(orderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_order_lines_itemId ON purchase_order_lines(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_order_lines_requestLineId ON purchase_order_lines(requestLineId)")
        db.execSQL(
            "INSERT OR REPLACE INTO sqlite_sequence(name, seq) SELECT 'purchase_order_lines', IFNULL(MAX(id), 0) FROM purchase_order_lines"
        )
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN baseUnit TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE items ADD COLUMN purchaseUnit TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE items ADD COLUMN unitsPerPurchase INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE purchase_request_lines ADD COLUMN baseUnit TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE vendor_quote_lines ADD COLUMN unitName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE vendor_quote_lines ADD COLUMN unitsPerBundle INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE purchase_order_lines ADD COLUMN unitName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE purchase_order_lines ADD COLUMN unitsPerBundle INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE goods_receipt_lines ADD COLUMN unitName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE goods_receipt_lines ADD COLUMN unitsPerBundle INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE purchase_invoice_lines ADD COLUMN unitName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE purchase_invoice_lines ADD COLUMN unitsPerBundle INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE purchase_request_lines ADD COLUMN enteredQty INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE purchase_request_lines ADD COLUMN orderUnit TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE purchase_request_lines ADD COLUMN unitsPerBundle INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vendor_quotes ADD COLUMN quoteNumber TEXT NOT NULL DEFAULT ''")
    }
}
