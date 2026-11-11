package com.kex.vikrsaathi.data.repository

import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import com.kex.vikrsaathi.data.dao.BillDao
import com.kex.vikrsaathi.data.dao.BillItemDao
import com.kex.vikrsaathi.data.dao.BillPaymentDao
import com.kex.vikrsaathi.data.dao.CustomerLedgerDao
import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.Bill
import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.entity.BillPayment
import com.kex.vikrsaathi.data.entity.BillVersion
import com.kex.vikrsaathi.data.entity.BillVersionItem
import com.kex.vikrsaathi.data.entity.BillVersionPayment
import com.kex.vikrsaathi.data.entity.CustomerLedgerEntry
import com.kex.vikrsaathi.domain.finance.BillLedgerPlanner
import com.kex.vikrsaathi.domain.finance.BillPaymentRequest
import com.kex.vikrsaathi.domain.finance.BillPaymentStatus
import com.kex.vikrsaathi.domain.finance.CustomerLedgerType
import com.kex.vikrsaathi.domain.finance.FinanceError
import com.kex.vikrsaathi.domain.finance.FinanceException
import com.kex.vikrsaathi.domain.finance.FinanceReference
import com.kex.vikrsaathi.domain.finance.LedgerRow
import com.kex.vikrsaathi.domain.finance.PaymentPortion
import com.kex.vikrsaathi.domain.finance.PaymentSplitValidator
import com.kex.vikrsaathi.domain.finance.ReversalReason
import com.kex.vikrsaathi.domain.money.Money
import com.kex.vikrsaathi.data.model.BillLineItem
import com.kex.vikrsaathi.data.model.BillVersionDetails
import com.kex.vikrsaathi.data.model.BillWithDetails
import com.kex.vikrsaathi.data.model.DashboardPeriodStats
import com.kex.vikrsaathi.data.model.DashboardTopItem
import com.kex.vikrsaathi.domain.bill.BillVersionDiffer
import com.kex.vikrsaathi.domain.bill.OperatorLabels
import com.kex.vikrsaathi.domain.dashboard.DashboardPeriod
import com.kex.vikrsaathi.domain.dashboard.DashboardPeriods
import com.kex.vikrsaathi.domain.dashboard.GrossProfitCalculator
import com.kex.vikrsaathi.domain.dashboard.PeriodComparison
import com.kex.vikrsaathi.data.entity.CostSourceType
import com.kex.vikrsaathi.domain.inventory.StockDeltaCalculator
import com.kex.vikrsaathi.domain.inventory.StockPolicy
import com.kex.vikrsaathi.util.InventoryMode
import com.kex.vikrsaathi.util.InvoiceNumberFormatter

class BillRepository(
    private val database: AppDatabase,
    private val billDao: BillDao,
    private val billItemDao: BillItemDao,
    private val billPaymentDao: BillPaymentDao,
    private val customerLedgerDao: CustomerLedgerDao,
    private val settingsRepository: SettingsRepository,
    private val inventoryRepository: InventoryRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val itemCostRepository: ItemCostRepository = ItemCostRepository(database),
    private val operatorRepository: OperatorRepository = OperatorRepository(database, settingsRepository)
) {

    val allBills: LiveData<List<Bill>> = billDao.getAllBills()
    val allBillsWithDetails: LiveData<List<BillWithDetails>> = billDao.getAllBillsWithDetails()

    fun searchBills(query: String): LiveData<List<Bill>> = billDao.searchBills(query)

    suspend fun getBillWithDetails(billId: Long): BillWithDetails? =
        billDao.getBillWithDetails(billId)

    suspend fun getPayments(billId: Long): List<BillPayment> = billPaymentDao.getForBill(billId)

    suspend fun deleteBill(bill: Bill) {
        database.withTransaction {
            if (database.billVersionDao().countForBill(bill.id) > 0) {
                throw BillHistoryRetainedException(bill.id)
            }
            val items = billItemDao.getItemsForBill(bill.id)
            val quantities = StockDeltaCalculator.aggregateQuantities(
                items.map { it.itemId to it.quantity }
            )
            val existingLedger = customerLedgerDao.getForBill(bill.id).map { it.toLedgerRow() }
            val plan = BillLedgerPlanner.planCreditSale(
                existing = existingLedger,
                newCustomerId = null,
                newCreditPaise = 0L,
                billId = bill.id,
                reason = ReversalReason.BILL_DELETED
            )
            insertPlan(plan, bill.date)
            customerLedgerDao.deleteAllocationsForBill(bill.id)
            restoreKnownCosts(items, CostSourceType.SALE_REVERSAL, bill.id)
            billItemDao.deleteItemsForBill(bill.id)
            billDao.deleteBill(bill)
            inventoryRepository.reverseBillStock(bill.id, quantities)
        }
    }

    suspend fun saveBill(
        customerId: Long?,
        lineItems: List<BillLineItem>,
        billRoundOff: Double = 0.0,
        existingBillId: Long? = null,
        payment: BillPaymentRequest = BillPaymentRequest.LeaveUnchanged,
        shortageAcknowledged: Boolean = false
    ): Long {
        val subtotal = BillTotalsCalculator.lineItemsSubtotal(lineItems)
        val total = subtotal + billRoundOff
        return database.withTransaction {
            val billId: Long
            val oldQuantities: Map<Long, Int>
            val previousCustomerId: Long?
            val ledgerAt: Long

            if (existingBillId != null) {
                val existing = billDao.getBillById(existingBillId)
                    ?: return@withTransaction saveBill(
                        customerId,
                        lineItems,
                        billRoundOff,
                        null,
                        payment,
                        shortageAcknowledged
                    )
                val priorItems = billItemDao.getItemsForBill(existingBillId)
                val priorPayments = billPaymentDao.getForBill(existingBillId)
                oldQuantities = StockDeltaCalculator.aggregateQuantities(
                    priorItems.map { it.itemId to it.quantity }
                )
                enforceStock(lineItems, existingBillId, oldQuantities, shortageAcknowledged)
                val priorCount = database.billVersionDao().countForBill(existingBillId)
                val previousState = if (priorCount == 0) {
                    stateOf(existing, priorItems, priorPayments)
                } else {
                    val latest = database.billVersionDao().getVersionsForBill(existingBillId).first()
                    stateOfVersion(latest)
                }
                if (priorCount == 0) {
                    insertVersionSnapshot(
                        bill = existing,
                        items = priorItems,
                        payments = priorPayments,
                        versionNumber = 1,
                        createdAt = existing.date,
                        changeSummary = BillVersionDiffer.ORIGINAL_SALE,
                        actorLabel = OperatorLabels.THIS_DEVICE
                    )
                }
                restoreKnownCosts(priorItems, CostSourceType.SALE_REVERSAL, existingBillId)
                previousCustomerId = existing.customerId
                ledgerAt = clock()
                billId = existingBillId
                billDao.updateBill(
                    existing.copy(
                        customerId = customerId,
                        total = total,
                        roundOff = billRoundOff
                    )
                )
                billItemDao.deleteItemsForBill(existingBillId)
                billItemDao.insertAll(lineItems.toPricedBillItems(billId))
                inventoryRepository.applyBillStockChanges(
                    billId = billId,
                    oldQuantities = oldQuantities,
                    newLineItems = lineItems
                )
                applyPayment(
                    billId = billId,
                    customerId = customerId,
                    previousCustomerId = previousCustomerId,
                    total = total,
                    billDate = ledgerAt,
                    payment = payment
                )
                val stored = billDao.getBillById(billId) ?: existing
                val newItems = billItemDao.getItemsForBill(billId)
                val newPayments = billPaymentDao.getForBill(billId)
                val summary = BillVersionDiffer.summarize(
                    before = previousState,
                    after = stateOf(stored, newItems, newPayments),
                    currencySymbol = settingsRepository.currencySymbol
                )
                insertVersionSnapshot(
                    bill = stored,
                    items = newItems,
                    payments = newPayments,
                    versionNumber = database.billVersionDao().maxVersionNumber(billId) + 1,
                    createdAt = ledgerAt,
                    changeSummary = summary,
                    actorLabel = operatorRepository.actorLabel()
                )
            } else {
                oldQuantities = emptyMap()
                previousCustomerId = null
                enforceStock(lineItems, null, oldQuantities, shortageAcknowledged)
                ledgerAt = clock()
                val (billNumber, counter) = generateNextInvoiceNumber()
                val bill = Bill(
                    billNumber = billNumber,
                    invoiceCounter = counter,
                    customerId = customerId,
                    total = total,
                    roundOff = billRoundOff,
                    date = ledgerAt
                )
                billId = billDao.insertBill(bill)
                billItemDao.insertAll(lineItems.toPricedBillItems(billId))
                inventoryRepository.applyBillStockChanges(
                    billId = billId,
                    oldQuantities = oldQuantities,
                    newLineItems = lineItems
                )
                applyPayment(
                    billId = billId,
                    customerId = customerId,
                    previousCustomerId = previousCustomerId,
                    total = total,
                    billDate = ledgerAt,
                    payment = payment
                )
            }
            billId
        }
    }

    /**
     * Stores the untouched bill as v1 before later edits mutate the customer or lines.
     * A bill that already has snapshots is left unchanged.
     */
    suspend fun preserveLegacyVersionIfNeeded(billId: Long) {
        database.withTransaction {
            if (database.billVersionDao().countForBill(billId) > 0) return@withTransaction
            val existing = billDao.getBillById(billId) ?: return@withTransaction
            insertVersionSnapshot(
                bill = existing,
                items = billItemDao.getItemsForBill(billId),
                payments = billPaymentDao.getForBill(billId),
                versionNumber = 1,
                createdAt = existing.date,
                changeSummary = BillVersionDiffer.ORIGINAL_SALE,
                actorLabel = OperatorLabels.THIS_DEVICE
            )
        }
    }

    suspend fun displayedVersionCount(billId: Long): Int {
        val stored = database.billVersionDao().countForBill(billId)
        if (stored > 0) return stored
        return if (billDao.getBillById(billId) != null) 1 else 0
    }

    /**
     * Newest version first. A bill that has never been edited is shown as one current
     * version taken from the live bill. That row is not written until the first edit.
     */
    suspend fun getVersionHistory(billId: Long): List<BillVersionDetails> {
        val stored = database.billVersionDao().getVersionsForBill(billId)
        if (stored.isNotEmpty()) {
            val currentNumber = stored.maxOf { it.versionNumber }
            return stored.map { version ->
                BillVersionDetails(
                    version = version,
                    items = database.billVersionDao().getItems(version.id),
                    payments = database.billVersionDao().getPayments(version.id),
                    isCurrent = version.versionNumber == currentNumber
                )
            }
        }
        val live = billDao.getBillWithDetails(billId) ?: return emptyList()
        val customer = live.customer
        val synthetic = BillVersion(
            billId = billId,
            billNumber = live.bill.billNumber,
            versionNumber = 1,
            createdAt = live.bill.date,
            saleDate = live.bill.date,
            customerId = live.bill.customerId,
            customerName = customer?.name?.trim()?.takeIf { it.isNotEmpty() } ?: BillVersionDiffer.WALK_IN,
            customerPhone = customer?.phone?.trim().orEmpty(),
            customerAddress = customer?.formattedAddress().orEmpty(),
            total = live.bill.total,
            roundOff = live.bill.roundOff,
            paymentStatus = live.bill.paymentStatus,
            paymentLabel = live.bill.paymentLabel,
            changeSummary = BillVersionDiffer.ORIGINAL_SALE,
            actorLabel = OperatorLabels.THIS_DEVICE
        )
        return listOf(
            BillVersionDetails(
                version = synthetic,
                items = live.items.map { item ->
                    BillVersionItem(
                        versionId = 0,
                        itemId = item.itemId,
                        itemName = item.itemName,
                        quantity = item.quantity,
                        mrp = item.mrp,
                        discount = item.discount,
                        finalPrice = item.finalPrice,
                        roundOff = item.roundOff,
                        unitCostPaise = item.unitCostPaise
                    )
                },
                payments = live.payments.map { payment ->
                    BillVersionPayment(
                        versionId = 0,
                        method = payment.method,
                        amountPaise = payment.amountPaise
                    )
                },
                isCurrent = true
            )
        )
    }

    suspend fun duplicateBill(billId: Long, shortageAcknowledged: Boolean = false): Long? {
        val source = billDao.getBillWithDetails(billId) ?: return null
        val duplicatedLines = source.items.map { item ->
            BillLineItem(
                itemId = item.itemId,
                name = item.itemName,
                mrp = item.mrp,
                discount = item.discount,
                quantity = item.quantity,
                roundOff = item.roundOff
            )
        }
        val portions = billPaymentDao.getForBill(billId).map {
            PaymentPortion(it.method, it.amountPaise)
        }
        val request = if (portions.isEmpty()) {
            BillPaymentRequest.KeepLegacy
        } else {
            BillPaymentRequest.Record(portions)
        }
        return saveBill(
            customerId = source.bill.customerId,
            lineItems = duplicatedLines,
            billRoundOff = source.bill.roundOff,
            payment = request,
            shortageAcknowledged = shortageAcknowledged
        )
    }

    suspend fun billNumberExists(billNumber: String): Boolean =
        billDao.countByBillNumber(billNumber) > 0

    suspend fun getDashboardPeriodStats(
        period: DashboardPeriod,
        nowMillis: Long = clock()
    ): DashboardPeriodStats {
        val zone = java.util.TimeZone.getDefault()
        val window = DashboardPeriods.current(period, nowMillis, zone)
        val previous = DashboardPeriods.previous(window, zone)
        val total = billDao.getTotalSalesBetween(window.startInclusive, window.endExclusive)
        val billCount = billDao.getBillCountBetween(window.startInclusive, window.endExclusive)
        val previousTotal = billDao.getTotalSalesBetween(previous.startInclusive, previous.endExclusive)
        val previousCount = billDao.getBillCountBetween(previous.startInclusive, previous.endExclusive)
        val topItems = billDao.getTopSoldItemsBetween(window.startInclusive, window.endExclusive).map { row ->
            DashboardTopItem(name = row.itemName, quantity = row.totalQuantity, itemId = row.itemId)
        }
        val bills = billDao.getAllBillsWithDetailsSync().filter { details ->
            details.bill.date >= window.startInclusive && details.bill.date < window.endExclusive
        }
        val returnLines = database.customerLedgerDao().getAllReturnLines().groupBy { it.returnId }
        val returns = database.customerLedgerDao().getAllReturns()
            .filter { it.occurredAt >= window.startInclusive && it.occurredAt < window.endExclusive }
            .map { entry ->
                GrossProfitCalculator.ReturnSlice(
                    refundPaise = entry.moneyRefundedPaise + entry.creditAdjustmentPaise,
                    lines = returnLines[entry.id].orEmpty().map { line ->
                        GrossProfitCalculator.SaleSlice(
                            revenuePaise = 0L,
                            quantity = line.quantity,
                            unitCostPaise = line.unitCostPaise
                        )
                    }
                )
            }
        val sales = bills.flatMap { details ->
            details.items.map { item ->
                GrossProfitCalculator.SaleSlice(
                    revenuePaise = Money.rupeesToPaise(item.finalPrice * item.quantity + item.roundOff),
                    quantity = item.quantity,
                    unitCostPaise = item.unitCostPaise
                )
            }
        }
        val roundOff = bills.sumOf { Money.rupeesToPaise(it.bill.roundOff) }
        return DashboardPeriodStats(
            period = period,
            window = window,
            previousWindow = previous,
            totalSales = total,
            billCount = billCount,
            comparison = PeriodComparison.of(total, previousTotal, billCount, previousCount),
            topItems = topItems,
            profit = GrossProfitCalculator.calculate(sales, roundOff, returns)
        )
    }

    suspend fun importBillFromBackup(
        billNumber: String,
        invoiceCounter: Int,
        date: Long,
        customerId: Long?,
        lineItems: List<BillLineItem>,
        billRoundOff: Double = 0.0,
        paymentStatus: String = BillPaymentStatus.LEGACY,
        paymentLabel: String = BillPaymentStatus.LABEL_LEGACY,
        payments: List<PaymentPortion> = emptyList()
    ): Long? {
        if (billNumber.isBlank() || lineItems.isEmpty()) return null
        if (billDao.countByBillNumber(billNumber) > 0) return null

        val subtotal = BillTotalsCalculator.lineItemsSubtotal(lineItems)
        val total = subtotal + billRoundOff
        val bill = Bill(
            billNumber = billNumber,
            invoiceCounter = invoiceCounter.coerceAtLeast(0),
            customerId = customerId,
            total = total,
            roundOff = billRoundOff,
            date = date,
            paymentStatus = paymentStatus,
            paymentLabel = paymentLabel
        )
        val billId = billDao.insertBill(bill)
        billItemDao.insertAll(lineItems.map { line ->
            BillItem(
                billId = billId,
                itemId = line.itemId,
                itemName = line.name,
                quantity = line.quantity,
                mrp = line.mrp,
                discount = line.discount,
                finalPrice = line.unitPriceAfterDiscount,
                roundOff = line.roundOff,
                unitCostPaise = line.unitCostPaise
            )
        })
        payments.forEach { portion ->
            billPaymentDao.insert(
                BillPayment(billId = billId, method = portion.method, amountPaise = portion.amountPaise)
            )
        }

        if (invoiceCounter > 0) {
            val dbMax = billDao.getMaxInvoiceCounter() ?: 0
            settingsRepository.invoiceCounter = maxOf(settingsRepository.invoiceCounter, dbMax + 1)
        }
        return billId
    }

    suspend fun importVersionHistory(billId: Long, versions: List<BillVersionDetails>) {
        if (versions.isEmpty()) return
        database.withTransaction {
            versions.sortedBy { it.version.versionNumber }.forEach { details ->
                val versionId = database.billVersionDao().insertVersion(
                    details.version.copy(id = 0, billId = billId)
                )
                if (details.items.isNotEmpty()) {
                    database.billVersionDao().insertItems(
                        details.items.map { it.copy(id = 0, versionId = versionId) }
                    )
                }
                if (details.payments.isNotEmpty()) {
                    database.billVersionDao().insertPayments(
                        details.payments.map { it.copy(id = 0, versionId = versionId) }
                    )
                }
            }
        }
    }

    private suspend fun applyPayment(
        billId: Long,
        customerId: Long?,
        previousCustomerId: Long?,
        total: Double,
        billDate: Long,
        payment: BillPaymentRequest
    ) {
        val existingPayments = billPaymentDao.getForBill(billId)
        val totalPaise = Money.rupeesToPaise(total)
        when (payment) {
            BillPaymentRequest.LeaveUnchanged -> {
                if (existingPayments.isEmpty()) return
                val sum = existingPayments.sumOf { it.amountPaise }
                if (sum != totalPaise) throw FinanceException(FinanceError.PAYMENT_CHANGED_WHILE_DISABLED)
            }
            BillPaymentRequest.KeepLegacy -> {
                if (existingPayments.isNotEmpty()) {
                    throw FinanceException(FinanceError.LEGACY_BILL_HAS_PAYMENTS)
                }
                billDao.updatePaymentStatus(billId, BillPaymentStatus.LEGACY, BillPaymentStatus.LABEL_LEGACY)
            }
            is BillPaymentRequest.Record -> {
                val split = PaymentSplitValidator.validate(
                    totalPaise = totalPaise,
                    portions = payment.portions,
                    hasRegisteredCustomer = customerId != null
                )
                billPaymentDao.deleteForBill(billId)
                split.portions.forEach { portion ->
                    billPaymentDao.insert(
                        BillPayment(billId = billId, method = portion.method, amountPaise = portion.amountPaise)
                    )
                }
                billDao.updatePaymentStatus(billId, BillPaymentStatus.RECORDED, split.label)
                val existingLedger = customerLedgerDao.getForBill(billId).map { it.toLedgerRow() }
                val plan = BillLedgerPlanner.planCreditSale(
                    existing = existingLedger,
                    newCustomerId = customerId ?: previousCustomerId,
                    newCreditPaise = split.creditPaise,
                    billId = billId,
                    reason = ReversalReason.BILL_EDITED
                )
                if (split.creditPaise > 0L && customerId == null) {
                    throw FinanceException(FinanceError.CREDIT_REQUIRES_CUSTOMER)
                }
                val corrected = if (split.creditPaise > 0L) {
                    plan.copy(
                        posting = plan.posting?.copy(partyId = customerId!!)
                    )
                } else {
                    plan
                }
                insertPlan(corrected, billDate)
            }
        }
    }

    private suspend fun insertPlan(
        plan: com.kex.vikrsaathi.domain.finance.LedgerPlan,
        occurredAt: Long
    ) {
        val now = System.currentTimeMillis()
        plan.reversals.forEach { reversal ->
            customerLedgerDao.insert(
                CustomerLedgerEntry(
                    customerId = reversal.partyId,
                    entryType = CustomerLedgerType.REVERSAL,
                    amountPaise = reversal.amountPaise,
                    occurredAt = occurredAt,
                    createdAt = now,
                    reason = reversal.reason,
                    referenceType = FinanceReference.BILL,
                    referenceId = reversal.billId,
                    reversesEntryId = reversal.reversesEntryId,
                    billId = reversal.billId
                )
            )
        }
        plan.posting?.let { posting ->
            customerLedgerDao.insert(
                CustomerLedgerEntry(
                    customerId = posting.partyId,
                    entryType = posting.entryType,
                    amountPaise = posting.amountPaise,
                    occurredAt = occurredAt,
                    createdAt = now,
                    referenceType = posting.referenceType,
                    referenceId = posting.referenceId,
                    billId = posting.billId
                )
            )
        }
    }

    private suspend fun enforceStock(
        lineItems: List<BillLineItem>,
        existingBillId: Long?,
        oldQuantities: Map<Long, Int>,
        shortageAcknowledged: Boolean
    ) {
        if (settingsRepository.inventoryMode == InventoryMode.OFF) return
        val shortfalls = inventoryRepository.findShortfalls(
            lineItems = lineItems,
            existingBillId = existingBillId,
            oldBillQuantities = oldQuantities
        )
        if (StockPolicy.blocksSave(
                settingsRepository.inventoryMode,
                shortfalls.isNotEmpty(),
                shortageAcknowledged
            )
        ) {
            throw InsufficientStockException(shortfalls)
        }
    }

    private suspend fun insertVersionSnapshot(
        bill: Bill,
        items: List<BillItem>,
        payments: List<BillPayment>,
        versionNumber: Int,
        createdAt: Long,
        changeSummary: String,
        actorLabel: String
    ) {
        val customer = bill.customerId?.let { database.customerDao().getCustomerById(it) }
        val versionId = database.billVersionDao().insertVersion(
            BillVersion(
                billId = bill.id,
                billNumber = bill.billNumber,
                versionNumber = versionNumber,
                createdAt = createdAt,
                saleDate = bill.date,
                customerId = bill.customerId,
                customerName = customer?.name?.trim()?.takeIf { it.isNotEmpty() }
                    ?: BillVersionDiffer.WALK_IN,
                customerPhone = customer?.phone?.trim().orEmpty(),
                customerAddress = customer?.formattedAddress().orEmpty(),
                total = bill.total,
                roundOff = bill.roundOff,
                paymentStatus = bill.paymentStatus,
                paymentLabel = bill.paymentLabel,
                changeSummary = changeSummary,
                actorLabel = actorLabel
            )
        )
        if (items.isNotEmpty()) {
            database.billVersionDao().insertItems(
                items.map { item ->
                    BillVersionItem(
                        versionId = versionId,
                        itemId = item.itemId,
                        itemName = item.itemName,
                        quantity = item.quantity,
                        mrp = item.mrp,
                        discount = item.discount,
                        finalPrice = item.finalPrice,
                        roundOff = item.roundOff,
                        unitCostPaise = item.unitCostPaise
                    )
                }
            )
        }
        if (payments.isNotEmpty()) {
            database.billVersionDao().insertPayments(
                payments.map { payment ->
                    BillVersionPayment(
                        versionId = versionId,
                        method = payment.method,
                        amountPaise = payment.amountPaise
                    )
                }
            )
        }
    }

    private suspend fun stateOf(
        bill: Bill,
        items: List<BillItem>,
        payments: List<BillPayment>
    ): BillVersionDiffer.State {
        val customer = bill.customerId?.let { database.customerDao().getCustomerById(it) }
        return BillVersionDiffer.State(
            customerName = customer?.name?.trim()?.takeIf { it.isNotEmpty() } ?: BillVersionDiffer.WALK_IN,
            customerPhone = customer?.phone?.trim().orEmpty(),
            customerAddress = customer?.formattedAddress().orEmpty(),
            roundOff = bill.roundOff,
            lines = items.map { it.toLineSnap() },
            payments = payments.map { BillVersionDiffer.PaymentSnap(it.method, it.amountPaise) }
        )
    }

    private suspend fun stateOfVersion(version: BillVersion): BillVersionDiffer.State {
        val items = database.billVersionDao().getItems(version.id)
        val payments = database.billVersionDao().getPayments(version.id)
        return BillVersionDiffer.State(
            customerName = version.customerName,
            customerPhone = version.customerPhone,
            customerAddress = version.customerAddress,
            roundOff = version.roundOff,
            lines = items.map { item ->
                BillVersionDiffer.LineSnap(
                    itemId = item.itemId,
                    name = item.itemName,
                    quantity = item.quantity,
                    mrp = item.mrp,
                    discount = item.discount,
                    finalPrice = item.finalPrice,
                    roundOff = item.roundOff
                )
            },
            payments = payments.map { BillVersionDiffer.PaymentSnap(it.method, it.amountPaise) }
        )
    }

    private fun List<BillLineItem>.toBillItems(billId: Long): List<BillItem> = map { line ->
        BillItem(
            billId = billId,
            itemId = line.itemId,
            itemName = line.name,
            quantity = line.quantity,
            mrp = line.mrp,
            discount = line.discount,
            finalPrice = line.unitPriceAfterDiscount,
            roundOff = line.roundOff,
            unitCostPaise = line.unitCostPaise
        )
    }

    private suspend fun List<BillLineItem>.toPricedBillItems(billId: Long): List<BillItem> {
        return map { line ->
            val cost = line.itemId?.let { itemId ->
                itemCostRepository.consume(itemId, line.quantity)
            }
            line.copy(unitCostPaise = cost).let { priced ->
                listOf(priced).toBillItems(billId).single()
            }
        }
    }

    private suspend fun restoreKnownCosts(items: List<BillItem>, sourceType: String, sourceId: Long) {
        val at = clock()
        items.forEach { item ->
            val itemId = item.itemId ?: return@forEach
            val cost = item.unitCostPaise ?: return@forEach
            itemCostRepository.restore(itemId, item.quantity, cost, sourceType, sourceId, at)
        }
    }

    private fun BillItem.toLineSnap() = BillVersionDiffer.LineSnap(
        itemId = itemId,
        name = itemName,
        quantity = quantity,
        mrp = mrp,
        discount = discount,
        finalPrice = finalPrice,
        roundOff = roundOff
    )

    private suspend fun generateNextInvoiceNumber(): Pair<String, Int> {
        val dbMax = billDao.getMaxInvoiceCounter() ?: 0
        val counter = maxOf(settingsRepository.invoiceCounter, dbMax + 1)
        val billNumber = InvoiceNumberFormatter.format(
            prefix = settingsRepository.invoicePrefix,
            counter = counter,
            suffix = settingsRepository.invoiceSuffix,
            separator = settingsRepository.invoiceSeparator,
            counterMinDigits = settingsRepository.invoiceCounterMinDigits
        )
        settingsRepository.invoiceCounter = counter + 1
        return billNumber to counter
    }
}

internal fun CustomerLedgerEntry.toLedgerRow(): LedgerRow = LedgerRow(
    id = id,
    partyId = customerId,
    entryType = entryType,
    amountPaise = amountPaise,
    occurredAt = occurredAt,
    reversesEntryId = reversesEntryId,
    billId = billId,
    referenceType = referenceType,
    referenceId = referenceId,
    method = method,
    note = note,
    reason = reason
)
