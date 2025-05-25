package com.example.daos

import com.example.utils.logging.LogWriter
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object StorageItemsTypesTable: Table("storageitemtypes") {
    val typeId = integer("typeid").autoIncrement()
    val typeName = varchar("typename", 50)
    val mimeType = varchar("mimetype", 50)
    override val primaryKey = PrimaryKey(typeId)

    data class StorageType(
        val id: Int,
        val typeName: String,
        val mimeType: String
    )

    fun getStorageTypes() : List<StorageType>? {
        return try {
            transaction {
                StorageItemsTypesTable
                    .selectAll()
                    .map { row ->
                        StorageType(row[typeId], row[typeName], row[mimeType])
                    }
            }
        } catch (ex: Exception) {
            LogWriter.log("Get an exception in getStorageTypes : $ex")
            null
        }
    }
}

enum class StorageItemsIds(val id: Int, val mimeType: String) {
    md(1, "text/markdown"),
    folder(2, ""),
    png(3, "")
}