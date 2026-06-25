package com.kex.vikrsaathi.data.model

import androidx.room.Embedded
import androidx.room.Relation
import com.kex.vikrsaathi.data.entity.Bill
import com.kex.vikrsaathi.data.entity.BillItem
import com.kex.vikrsaathi.data.entity.Customer

data class BillWithDetails(
    @Embedded val bill: Bill,
    @Relation(
        parentColumn = "customerId",
        entityColumn = "id"
    )
    val customer: Customer?,
    @Relation(
        parentColumn = "id",
        entityColumn = "billId"
    )
    val items: List<BillItem>
)
