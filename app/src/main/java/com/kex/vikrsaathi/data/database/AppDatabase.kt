package com.kex.vikrsaathi.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kex.vikrsaathi.data.dao.BillDao
import com.kex.vikrsaathi.data.dao.BillDraftDao
import com.kex.vikrsaathi.data.dao.BillItemDao
import com.kex.vikrsaathi.data.dao.BillPaymentDao
import com.kex.vikrsaathi.data.dao.CustomerDao
import com.kex.vikrsaathi.data.dao.CustomerLedgerDao
import com.kex.vikrsaathi.data.dao.InvoiceTemplateDao
import com.kex.vikrsaathi.data.dao.InvoiceTemplateVersionDao
import com.kex.vikrsaathi.data.dao.ItemDao
import com.kex.vikrsaathi.data.dao.PurchaseDao
import com.kex.vikrsaathi.data.dao.StockMovementDao
import com.kex.vikrsaathi.data.dao.VendorDao
import com.kex.vikrsaathi.data.entity.Bill
import com.kex.vikrsaathi.data.entity.BillDraftEntity
import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.entity.BillPayment
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.data.entity.CustomerLedgerEntry
import com.kex.vikrsaathi.data.entity.CustomerReceipt
import com.kex.vikrsaathi.data.entity.CustomerReceiptAllocation
import com.kex.vikrsaathi.data.entity.GoodsReceipt
import com.kex.vikrsaathi.data.entity.GoodsReceiptLine
import com.kex.vikrsaathi.data.entity.InvoiceTemplateEntity
import com.kex.vikrsaathi.data.entity.InvoiceTemplateVersionEntity
import com.kex.vikrsaathi.data.entity.Item
import com.kex.vikrsaathi.data.entity.PurchaseInvoice
import com.kex.vikrsaathi.data.entity.PurchaseInvoiceLine
import com.kex.vikrsaathi.data.entity.PurchaseOrder
import com.kex.vikrsaathi.data.entity.PurchaseOrderLine
import com.kex.vikrsaathi.data.entity.PurchaseRequest
import com.kex.vikrsaathi.data.entity.PurchaseRequestLine
import com.kex.vikrsaathi.data.entity.SalesReturn
import com.kex.vikrsaathi.data.entity.SalesReturnLine
import com.kex.vikrsaathi.data.entity.StockMovement
import com.kex.vikrsaathi.data.entity.Vendor
import com.kex.vikrsaathi.data.entity.VendorLedgerEntry
import com.kex.vikrsaathi.data.entity.VendorPayment
import com.kex.vikrsaathi.data.entity.VendorPaymentAllocation
import com.kex.vikrsaathi.data.entity.VendorQuote
import com.kex.vikrsaathi.data.entity.VendorQuoteLine

@Database(
    entities = [
        Customer::class,
        Item::class,
        Bill::class,
        BillItem::class,
        InvoiceTemplateEntity::class,
        InvoiceTemplateVersionEntity::class,
        BillDraftEntity::class,
        StockMovement::class,
        BillPayment::class,
        CustomerLedgerEntry::class,
        CustomerReceipt::class,
        CustomerReceiptAllocation::class,
        SalesReturn::class,
        SalesReturnLine::class,
        Vendor::class,
        VendorLedgerEntry::class,
        VendorPayment::class,
        VendorPaymentAllocation::class,
        PurchaseOrder::class,
        PurchaseOrderLine::class,
        GoodsReceipt::class,
        GoodsReceiptLine::class,
        PurchaseInvoice::class,
        PurchaseInvoiceLine::class,
        VendorQuote::class,
        VendorQuoteLine::class,
        PurchaseRequest::class,
        PurchaseRequestLine::class
    ],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun customerDao(): CustomerDao
    abstract fun itemDao(): ItemDao
    abstract fun billDao(): BillDao
    abstract fun billDraftDao(): BillDraftDao
    abstract fun billItemDao(): BillItemDao
    abstract fun invoiceTemplateDao(): InvoiceTemplateDao
    abstract fun invoiceTemplateVersionDao(): InvoiceTemplateVersionDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun billPaymentDao(): BillPaymentDao
    abstract fun customerLedgerDao(): CustomerLedgerDao
    abstract fun vendorDao(): VendorDao
    abstract fun purchaseDao(): PurchaseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vikr_saathi.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
