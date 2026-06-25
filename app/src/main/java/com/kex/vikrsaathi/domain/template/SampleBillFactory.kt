package com.kex.vikrsaathi.domain.template

import com.kex.vikrsaathi.data.entity.Bill
import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.data.model.BillWithDetails

object SampleBillFactory {

    fun createSample(): BillWithDetails {
        val customer = Customer(
            id = 1,
            name = "Sample Customer",
            address1 = "123 Market Road, Bengaluru",
            phone = "9876543210"
        )
        val bill = Bill(
            id = 1,
            billNumber = "1001",
            customerId = 1,
            total = 1250.0,
            date = System.currentTimeMillis()
        )
        val items = listOf(
            BillItem(
                billId = 1,
                itemId = 1,
                itemName = "Sample Item A",
                quantity = 2,
                mrp = 500.0,
                discount = 10.0,
                finalPrice = 450.0
            ),
            BillItem(
                billId = 1,
                itemId = 2,
                itemName = "Sample Item B",
                quantity = 1,
                mrp = 400.0,
                discount = 5.0,
                finalPrice = 380.0
            )
        )
        return BillWithDetails(bill = bill, customer = customer, items = items)
    }
}
