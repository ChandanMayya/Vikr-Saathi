package com.loctell.vikrsaathi.data.repository

import androidx.lifecycle.LiveData
import com.loctell.vikrsaathi.data.dao.CustomerDao
import com.loctell.vikrsaathi.data.entity.Customer

class CustomerRepository(private val customerDao: CustomerDao) {

    val allCustomers: LiveData<List<Customer>> = customerDao.getAllCustomers()

    suspend fun insert(customer: Customer): Long = customerDao.insert(customer)

    suspend fun update(customer: Customer) = customerDao.update(customer)

    suspend fun delete(customer: Customer) = customerDao.delete(customer)

    suspend fun getById(id: Long): Customer? = customerDao.getCustomerById(id)

    suspend fun search(query: String): List<Customer> {
        if (query.isBlank()) return emptyList()
        return customerDao.searchCustomers(query)
    }
}
