package com.kex.vikrsaathi.data.repository

import androidx.lifecycle.LiveData
import com.kex.vikrsaathi.data.dao.ItemDao
import com.kex.vikrsaathi.data.entity.Item

class ItemRepository(private val itemDao: ItemDao) {

    val allItems: LiveData<List<Item>> = itemDao.getAllItems()

    suspend fun insert(item: Item): Long = itemDao.insert(item)

    suspend fun update(item: Item) = itemDao.update(item)

    suspend fun delete(item: Item) = itemDao.delete(item)

    suspend fun getById(id: Long): Item? = itemDao.getItemById(id)

    suspend fun getByBarcode(barcode: String): Item? = itemDao.getItemByBarcode(barcode)

    suspend fun searchByName(query: String): List<Item> {
        if (query.isBlank()) return emptyList()
        return itemDao.searchByName(query)
    }

    suspend fun isBarcodeUnique(barcode: String, excludeId: Long = 0): Boolean {
        if (barcode.isBlank()) return true
        return itemDao.countByBarcode(barcode, excludeId) == 0
    }
}
