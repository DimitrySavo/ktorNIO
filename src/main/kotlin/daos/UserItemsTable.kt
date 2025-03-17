package com.example.daos

import com.example.FunctionResult
import com.example.data.MinIOFactory
import com.example.data.createFileInMinio
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

object UserItemsTable : Table("useritems") {
    val uid = uuid("uid")
    val parent_id = uuid("parent_uid").nullable()
    val name = varchar("name", 255)
    val type = integer("type").references(StorageItemsTypesTable.typeId)
    val version = text("version")
    val owner_id = integer("owner_id").references(Users.userId)
    val created_at = timestamp("created_at")
    val updated_at = timestamp("updated_at")
    val deleted_at = timestamp("deleted_at")

    fun createItem(
        uid: UUID,
        parent_id: UUID? = null,
        user_id: Int,
        name: String,
        type: StorageItemsIds
    ) : FunctionResult<String> {
        return try {
            transaction {
                insert {
                    it[this.uid] = uid
                    it[this.parent_id] = parent_id
                    it[this.owner_id] = user_id
                    it[this.name] = name
                    it[this.type] = type.id
                    it[this.version] = "null"
                }

                if (type != StorageItemsIds.folder) {
                    val result = createFileInMinio(uid, type)
                    if(result is FunctionResult.Error) {
                        throw Exception("MinIO file creation failed: ${result.message}")
                    }
                }
            }
            println("Created db item successfully")
            FunctionResult.Success("Created ok with uid: $uid")
        } catch (ex: SQLException) {
            println("Get an sql exception: $ex")
            FunctionResult.Error("Sql exception")
        } catch (ex: Exception) {
            println("Get exception: $ex")
            FunctionResult.Error("Get exception")
        }
    }

    fun softItemDeletion(uid: UUID) : FunctionResult<Unit> {
        return try {
            transaction {
                update({ UserItemsTable.uid eq uid }) {
                    it[deleted_at] = Instant.now()
                }
            }
            println("Soft deleted db item successfully")
            FunctionResult.Success(Unit)
        } catch (ex: SQLException) {
            println("Get sql exception: ${ex.message}")
            FunctionResult.Error(ex.toString())
        } catch (ex: Exception) {
            println("Get exception: ${ex.message}")
            FunctionResult.Error(ex.toString())
        }
    }
}


