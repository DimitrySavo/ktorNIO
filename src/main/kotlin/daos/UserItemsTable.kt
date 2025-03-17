package com.example.daos

import com.example.FunctionResult
import com.example.StorageItemResponse
import com.example.data.createFileInMinio
import com.example.data.readFromFile
import com.example.data.replaceFileMinio
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
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

    fun getUserItems(userId: Int): FunctionResult<List<StorageItemResponse>> {
        return try {
            val items = transaction {
                (UserItemsTable innerJoin StorageItemsTypesTable)
                    .selectAll()
                    .where { UserItemsTable.owner_id eq userId }
                    .map { row ->
                        val uid = row[UserItemsTable.uid]
                        val parentId = row[UserItemsTable.parent_id]
                        val name = row[UserItemsTable.name]
                        val typeName = row[StorageItemsTypesTable.typeName]

                        val content = if (row[UserItemsTable.type] == StorageItemsIds.md.id) {
                            try {
                                readFromFile(uid.toString())
                            } catch (ex: Exception) {
                                println("Error reading file")
                                ""
                            }
                        } else {
                            ""
                        }

                        StorageItemResponse(
                            uid = uid,
                            parent_id = parentId,
                            name = name,
                            type = typeName,
                            content = content
                        )
                    }
            }

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


