package com.kex.vikrsaathi.data.reset

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class ResetHistoryStore(private val context: Context) {

    private val historyDir = File(context.filesDir, "reset_history").apply { mkdirs() }
    private val snapshotsDir = File(historyDir, "snapshots").apply { mkdirs() }
    private val indexFile = File(historyDir, "index.json")

    fun snapshotFile(entryId: String): File = File(snapshotsDir, "$entryId.json")

    fun loadEntries(): List<ResetHistoryEntry> {
        purgeExpired()
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(indexFile.readText())
            val array = root.getJSONArray("entries")
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ResetHistoryEntry(
                            id = obj.getString("id"),
                            performedAt = obj.getLong("performedAt"),
                            resetCategories = obj.getJSONArray("resetCategories").toStringList(),
                            snapshotFileName = obj.getString("snapshotFileName")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
            .filter { snapshotFile(it.id).exists() }
            .sortedByDescending { it.performedAt }
    }

    fun addEntry(entry: ResetHistoryEntry, snapshotJson: String) {
        purgeExpired()
        snapshotFile(entry.id).writeText(snapshotJson, Charsets.UTF_8)
        val entries = loadEntries().toMutableList()
        entries.add(0, entry)
        saveIndex(entries)
    }

    fun getEntry(id: String): ResetHistoryEntry? =
        loadEntries().firstOrNull { it.id == id }

    fun readSnapshotJson(entryId: String): String? {
        val file = snapshotFile(entryId)
        if (!file.exists()) return null
        return file.readText()
    }

    fun purgeExpired() {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        val entries = if (!indexFile.exists()) {
            emptyList()
        } else {
            runCatching {
                val root = JSONObject(indexFile.readText())
                val array = root.getJSONArray("entries")
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        add(
                            ResetHistoryEntry(
                                id = obj.getString("id"),
                                performedAt = obj.getLong("performedAt"),
                                resetCategories = obj.getJSONArray("resetCategories").toStringList(),
                                snapshotFileName = obj.getString("snapshotFileName")
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        val kept = entries.filter { it.performedAt >= cutoff }
        val removed = entries.filter { it.performedAt < cutoff }
        removed.forEach { snapshotFile(it.id).delete() }
        if (removed.isNotEmpty() || entries.size != kept.size) {
            saveIndex(kept)
        }
    }

    fun createEntryId(): String = UUID.randomUUID().toString()

    private fun saveIndex(entries: List<ResetHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("performedAt", entry.performedAt)
                    put("resetCategories", JSONArray(entry.resetCategories))
                    put("snapshotFileName", entry.snapshotFileName)
                }
            )
        }
        indexFile.writeText(JSONObject().put("entries", array).toString(), Charsets.UTF_8)
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) add(getString(i))
    }

    companion object {
        private val RETENTION_MS = TimeUnit.DAYS.toMillis(30)
    }
}
