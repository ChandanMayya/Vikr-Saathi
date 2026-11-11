package com.kex.vikrsaathi.data.repository

import com.kex.vikrsaathi.data.database.AppDatabase
import com.kex.vikrsaathi.data.entity.OperatorProfile
import com.kex.vikrsaathi.domain.bill.OperatorLabels

class OperatorRepository(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository
) {
    suspend fun all(): List<OperatorProfile> = database.operatorDao().getAll()

    suspend fun actorLabel(): String {
        val id = settingsRepository.selectedOperatorId
        if (id <= 0L) return OperatorLabels.THIS_DEVICE
        val name = database.operatorDao().getById(id)?.name?.trim().orEmpty()
        return name.ifEmpty { OperatorLabels.THIS_DEVICE }
    }

    suspend fun saveAndSelect(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            settingsRepository.selectedOperatorId = 0L
            return OperatorLabels.THIS_DEVICE
        }
        val existing = database.operatorDao().findByName(trimmed)
        val id = existing?.id ?: database.operatorDao().insert(
            OperatorProfile(name = trimmed, createdAt = System.currentTimeMillis())
        )
        settingsRepository.selectedOperatorId = id
        return trimmed
    }

    suspend fun select(id: Long) {
        if (id <= 0L || database.operatorDao().getById(id) == null) {
            settingsRepository.selectedOperatorId = 0L
        } else {
            settingsRepository.selectedOperatorId = id
        }
    }
}
