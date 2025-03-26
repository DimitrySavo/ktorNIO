package com.example.daos

import com.example.FunctionResult
import com.example.StorageItemResponse
import com.example.data.createFileInMinio
import com.example.data.replaceFileMinio
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
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
    val deleted_at = timestamp("deleted_at").nullable()

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

    fun getItemVersion(uid: UUID): FunctionResult<String> {
        return try {
            val version = transaction {
                UserItemsTable
                    .select(UserItemsTable.version)
                    .where { UserItemsTable.uid eq uid  }
                    .singleOrNull()
                    ?.get(UserItemsTable.version)
            }

            if(version == null) {
                FunctionResult.Error("Version is null")
            } else {
                FunctionResult.Success(version.toString())
            }
        } catch (ex: Exception) {
            FunctionResult.Error("Get an error ${ex.message}")
        }
    }

    fun softItemDeletion(uid: UUID) : FunctionResult<Unit> {
        return try {
            transaction {
                val childItems = UserItemsTable
                    .select(UserItemsTable.uid)
                    .where { UserItemsTable.parent_id eq uid }
                    .map { it[UserItemsTable.uid] }

                childItems.forEach {
                    softItemDeletion(it)
                }

                UserItemsTable.update({ UserItemsTable.uid eq uid }) {
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

    fun updateItem(
        uid: UUID,
        fileContent: String? = null,
        name: String? = null,
        parentId: UUID? = null,
        type: StorageItemsIds
    ) :FunctionResult<Unit> {
        return try {
            // Add check for existing file, for is file delete and other stuff
            if (type != StorageItemsIds.folder) {
                if(fileContent != null) {
                    replaceFileMinio(
                        uid,
                        type,
                        fileContent
                    )
                }
            }


            val hashSum = if(type == StorageItemsIds.md && fileContent != null) computeHashVersion(fileContent) else null

            transaction {
                UserItemsTable.update({ UserItemsTable.uid eq uid }) { row ->
                    row[UserItemsTable.updated_at] = Instant.now()
                    if (name != null) {
                        row[UserItemsTable.name] = name
                    }
                    if (parentId != null) {
                        row[UserItemsTable.parent_id] = parentId
                    }
                    if (hashSum != null) {
                        row[UserItemsTable.version] = hashSum
                    }
                }
            }
            println("Updated item in db successfully")
            FunctionResult.Success(Unit)
        } catch (ex: Exception) {
            println("Exception in updateItem: ${ex.message}")
            FunctionResult.Error("Exception: ${ex.message}")
        }
    }

    fun updateItemWithVersion(

    ): FunctionResult<Unit> {
        return FunctionResult.Success(Unit)
    }

    private fun ResultRow.toStorageItemResponse(): StorageItemResponse {
        println("Row is: $this")
        return StorageItemResponse(
            uid = this[UserItemsTable.uid],
            parent_id = this[UserItemsTable.parent_id],
            name = this[UserItemsTable.name],
            type = this[StorageItemsTypesTable.typeName],
            created_at = this[UserItemsTable.created_at].epochSecond,
            updated_at = this[UserItemsTable.updated_at].epochSecond,
            deleted_at = this[UserItemsTable.deleted_at]?.epochSecond
        )
    }

    fun getUserItems(userId: Int): FunctionResult<List<StorageItemResponse>> {
        return try {
            val rawRows = transaction {
                (UserItemsTable innerJoin StorageItemsTypesTable)
                    .selectAll()
                    .where { (UserItemsTable.owner_id eq userId) and (UserItemsTable.deleted_at.isNull()) }
                    .toList()
            }

            println("Raw rows: $rawRows")

            val items = transaction {
                (UserItemsTable innerJoin StorageItemsTypesTable)
                    .selectAll()
                    .where { (UserItemsTable.owner_id eq userId) and (UserItemsTable.deleted_at.isNull()) }
                    .map { row -> row.toStorageItemResponse() }
            }
            println(items.toString())

            FunctionResult.Success(items)
        } catch (ex: Exception) {
            println("Get an exception ${ex.message}")
            FunctionResult.Error(ex.toString())
        }
    }

    fun getDeletedUserItems(userId: Int): FunctionResult<List<StorageItemResponse>> {
        return try {
            val items = transaction {
                (UserItemsTable innerJoin StorageItemsTypesTable)
                    .selectAll()
                    .where { (UserItemsTable.owner_id eq userId) and (UserItemsTable.deleted_at.isNotNull()) }
                    .map { row -> row.toStorageItemResponse() }
            }
            println(items.toString())

            FunctionResult.Success(items)
        } catch (ex: Exception) {
            println("Get an exception ${ex.message}")
            FunctionResult.Error(ex.toString())
        }
    }

    fun computeHashVersion(content: String) : String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(content.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}


