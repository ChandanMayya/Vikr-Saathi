package com.kex.vikrsaathi.data.model

import com.kex.vikrsaathi.data.entity.BillVersion
import com.kex.vikrsaathi.data.entity.BillVersionItem
import com.kex.vikrsaathi.data.entity.BillVersionPayment

data class BillVersionDetails(
    val version: BillVersion,
    val items: List<BillVersionItem>,
    val payments: List<BillVersionPayment>,
    val isCurrent: Boolean
)
