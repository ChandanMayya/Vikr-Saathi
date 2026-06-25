package com.kex.vikrsaathi.ui.customer

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kex.vikrsaathi.data.entity.Customer
import com.kex.vikrsaathi.data.repository.CustomerRepository
import kotlinx.coroutines.launch

class CustomerViewModel(private val repository: CustomerRepository) : ViewModel() {

    val customers: LiveData<List<Customer>> = repository.allCustomers

    fun saveCustomer(customer: Customer, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = if (customer.id == 0L) {
                repository.insert(customer)
            } else {
                repository.update(customer)
                customer.id
            }
            onComplete(id)
        }
    }

    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch { repository.delete(customer) }
    }

    fun searchCustomers(query: String, callback: (List<Customer>) -> Unit) {
        viewModelScope.launch {
            callback(repository.search(query))
        }
    }
}
