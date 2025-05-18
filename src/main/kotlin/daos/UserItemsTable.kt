package com.example.daos

import com.example.MetadataUpdateRequest
import com.example.utils.FunctionResult
import com.example.StorageItemResponse
import com.example.TextUpdateRequest
import com.example.data.createFileInMinio
import com.example.data.deleteFileInMinio
import com.example.data.readFromFile
import com.example.data.replaceFileMinio
import com.example.utils.Locker
import com.example.utils.theewaysmerge.ThreeWayMerge
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.sql.SQLException
import java.time.Instant
import java.util.*


object UserItemsTable : Table("useritems") {
    val uid = uuid("uid")
    val parent_id = uuid("parent_uid").references(
        UserItemsTable.uid,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE
    ).nullable()
    val name = varchar("name", 255)
    val type = integer("type").references(StorageItemsTypesTable.typeId)
    val version = text("version").nullable()
    val owner_id = uuid("owner_id").references(Users.userId)
    val created_at = timestamp("created_at")
    val updated_at = timestamp("updated_at")
    val deleted_at = timestamp("deleted_at").nullable()

    fun createItem(
        uid: UUID,
        parent_id: UUID? = null,
        user_id: UUID,
        name: String,
        type: StorageItemsTypesTable.StorageType
    ): FunctionResult<String> {
        return try {
            transaction {
                insert {
                    it[this.uid] = uid
                    it[this.parent_id] = parent_id
                    it[this.owner_id] = user_id
                    it[this.name] = name
                    it[this.type] = type.id
                    it[this.version] = null
                }

                if (type.id == 1) {
                    val result = createFileInMinio(uid, type)
                    if (result is FunctionResult.Error) {
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

    fun checkIsThereAnItem(uid: UUID): FunctionResult<Unit> {
        return try {
            val result = transaction {
                UserItemsTable.select(UserItemsTable.uid)
                    .where { UserItemsTable.uid eq uid }
                    .firstOrNull() != null
            }

            if (result) {
                FunctionResult.Success(Unit)
            } else {
                FunctionResult.Error("Can't find item with such uid")
            }
        } catch (ex: SQLException) {
            println("Get an sql exception: $ex")
            FunctionResult.Error("Sql exception")
        } catch (ex: Exception) {
            println("Get exception: $ex")
            FunctionResult.Error("Get exception")
        }
    }

    fun getItemVersion(uid: UUID): FunctionResult<String?> {
        return try {
            val row = transaction {
                UserItemsTable.selectAll()
                    .where { UserItemsTable.uid eq uid }
                    .singleOrNull()
            }

            if (row == null) {
                return FunctionResult.Error("Can't get item with such uid: $uid")
            }

            return FunctionResult.Success(row[version])
        } catch (ex: Exception) {
            FunctionResult.Error("Get an error ${ex.message}")
        }
    }

    fun softItemDeletion(uid: UUID): FunctionResult<Unit> {
        return try {
            Locker.withAdvisoryLock(uid) {
                val childItems = select(UserItemsTable.uid)
                    .where { parent_id eq uid }
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
        } catch (ex: Locker.ResourceLockException) {
            println("softItemDeletion function resource lock exception: $ex")
            return FunctionResult.Error("Can't lock resource right now")
        } catch (ex: SQLException) {
            println("Get sql exception: ${ex.message}")
            FunctionResult.Error(ex.toString())
        } catch (ex: Exception) {
            println("Get exception: ${ex.message}")
            FunctionResult.Error(ex.toString())
        }
    }

    fun updateMetadata(instance: MetadataUpdateRequest, userUid: UUID, itemUUID: UUID): FunctionResult<Unit> {
        return try {
            val updateCount = Locker.withAdvisoryLock(itemUUID) {
                UserItemsTable.update({
                    (UserItemsTable.uid eq itemUUID) and (UserItemsTable.owner_id eq userUid)
                }) {
                    if (instance.name != null) {
                        it[UserItemsTable.name] = instance.name
                    }
                    it[UserItemsTable.parent_id] = instance.parentUid
                }
            }
            println("updateMetadata successfully updated $updateCount rows")
            FunctionResult.Success(Unit)
        } catch (ex: Locker.ResourceLockException) {
            println("updateMetadata function resource lock exception: $ex")
            return FunctionResult.Error("Can't lock resource right now")
        } catch (ex: Exception) {
            println("updateMetadata function exception: $ex")
            FunctionResult.Error("Can't find item or internal server error")
        }
    }

    fun updateTextFile(instance: TextUpdateRequest, userUid: UUID, itemUUID: UUID): FunctionResult<String> {
        return try {
            Locker.withAdvisoryLock(itemUUID) {
                val row = UserItemsTable.selectAll()
                    .where { uid eq itemUUID }
                    .singleOrNull()

                if (row == null) {
                    return@withAdvisoryLock FunctionResult.Error("There is no file with such uid")
                }
                val serverVersion = row[version]

                if (serverVersion == instance.version || serverVersion == null) {
                    //replace item

                    return@withAdvisoryLock storeAndUpdateVersion(
                        itemUUID = itemUUID,
                        content = instance.content,
                        userUid = userUid
                    )
                } else {
                    //3way merge item
                    when (val serverText = readFromFile(itemUUID.toString())) {
                        is FunctionResult.Error -> {
                            println("Can't read file from minio with uid = $itemUUID")
                            return@withAdvisoryLock serverText
                        }

                        is FunctionResult.Success -> {
                            val mergeResult =
                                ThreeWayMerge().merge(
                                    base = instance.baseline,
                                    server = serverText.data,
                                    user = instance.content
                                )

                            println("Content for user $userUid is merged: $mergeResult\n")

                            return@withAdvisoryLock storeAndUpdateVersion(
                                itemUUID = itemUUID,
                                content = mergeResult,
                                userUid = userUid
                            )
                        }
                    }
                }
            }
        } catch (ex: Locker.ResourceLockException) {
            println("updateTextFile function resource lock exception: $ex")
            return FunctionResult.Error("Can't lock resource right now")
        } catch (ex: Exception) {
            println("updateTextFile function exception: $ex")
            FunctionResult.Error("Can't find item or internal server error")
        }
    }

    private fun storeAndUpdateVersion(
        itemUUID: UUID,
        content: String,
        userUid: UUID
    ): FunctionResult<String> {
        return when (val replacementResult = replaceFileMinio(
            uid = itemUUID,
            type = StorageItemsIds.md,
            content = content
        )) {
            is FunctionResult.Error -> replacementResult

            is FunctionResult.Success -> {
                val newVersion = computeHashVersion(content)
                UserItemsTable.update({ uid eq itemUUID }) {
                    it[version] = newVersion
                }
                FunctionResult.Success("File $itemUUID for user $userUid updated successfully")
            }
        }
    }

    private fun ResultRow.toStorageItemResponse(): StorageItemResponse {
        println("Row is: $this")
        return StorageItemResponse(
            uid = this[uid],
            parent_id = this[parent_id],
            name = this[name],
            type = this[StorageItemsTypesTable.typeName],
            version = this[version],
            created_at = this[created_at].epochSecond,
            updated_at = this[updated_at].epochSecond,
            deleted_at = this[deleted_at]?.epochSecond
        )
    }

    fun getUserItems(userId: UUID): FunctionResult<List<StorageItemResponse>> {
        return try {
            val rawRows = transaction {
                (UserItemsTable innerJoin StorageItemsTypesTable)
                    .selectAll()
                    .where { (owner_id eq userId) and (deleted_at.isNull()) }
                    .toList()
            }

            println("Raw rows: $rawRows")

            val items = transaction {
                (UserItemsTable innerJoin StorageItemsTypesTable)
                    .selectAll()
                    .where { (owner_id eq userId) and (deleted_at.isNull()) }
                    .map { row -> row.toStorageItemResponse() }
            }
            println(items.toString())

            FunctionResult.Success(items)
        } catch (ex: Exception) {
            println("Get an exception ${ex.message}")
            FunctionResult.Error(ex.toString())
        }
    }

    fun getDeletedUserItems(userId: UUID): FunctionResult<List<StorageItemResponse>> {
        return try {
            val items = transaction {
                (UserItemsTable innerJoin StorageItemsTypesTable)
                    .selectAll()
                    .where { (owner_id eq userId) and (deleted_at.isNotNull()) }
                    .map { row -> row.toStorageItemResponse() }
            }
            println(items.toString())

            FunctionResult.Success(items)
        } catch (ex: Exception) {
            println("Get an exception ${ex.message}")
            FunctionResult.Error(ex.toString())
        }
    }

    fun permanentDeleteItem(userUid: UUID, itemUid: UUID): FunctionResult<Unit> {
        return try {
            Locker.withAdvisoryLock(itemUid) {
                when (val storageResult = deleteFileInMinio(itemUid)) {
                    is FunctionResult.Error -> {
                        return@withAdvisoryLock storageResult
                    }

                    is FunctionResult.Success -> {
                        UserItemsTable.deleteWhere {
                            uid eq itemUid
                        }
                        return@withAdvisoryLock FunctionResult.Success(Unit)
                    }
                }
            }
        } catch (ex: Locker.ResourceLockException) {
            println("permanentDeleteItem function resource lock exception: $ex")
            FunctionResult.Error("Can't lock resource right now")
        } catch (ex: Exception) {
            println("permanentDeleteItem function exception: $ex")
            FunctionResult.Error("Can't delete item for some reason")
        }
    }

    fun restoreDeletedItem(userUid: UUID, itemUid: UUID): FunctionResult<Unit> {
        return try {
            Locker.withAdvisoryLock(itemUid) {
                val deletedFile = UserItemsTable.selectAll()
                    .where { uid eq itemUid and deleted_at.isNotNull() }
                    .singleOrNull()

                if (deletedFile == null) {
                    return@withAdvisoryLock FunctionResult.Error("Can't get deleted item with such uid")
                }

                val deletedName = deletedFile[name]

                val isExistWithName = UserItemsTable.selectAll()
                    .where {
                        (name eq deletedName) and
                                (parent_id.isNull()) and
                                (deleted_at.isNull())
                    }.singleOrNull()

                if (isExistWithName == null) {
                    UserItemsTable.update({ uid eq itemUid }) {
                        it[parent_id] = null
                        it[deleted_at] = null
                    }

                    return@withAdvisoryLock FunctionResult.Success(Unit)
                } else {
                    val newName = generateNewName(deletedName)
                    UserItemsTable.update({ uid eq itemUid }) {
                        it[name] = newName
                        it[parent_id] = null
                        it[deleted_at] = null
                    }

                    return@withAdvisoryLock FunctionResult.Success(Unit)
                }
            }
        } catch (ex: Locker.ResourceLockException) {
            println("restoreDeletedItem function resource lock exception: $ex")
            FunctionResult.Error("Can't lock resource right now")
        } catch (ex: Exception) {
            println("restoreDeletedItem function exception: $ex")
            FunctionResult.Error("Can't delete item for some reason")
        }
    }

    fun generateNewName(rawName: String): String {
        val oldName = normalizeName(rawName)
        val suffixRegex = Regex("""^${Regex.escape(oldName)}_(\d+)$""")

        val existingNames = transaction {
            UserItemsTable
                .select(UserItemsTable.name)
                .where {
                    UserItemsTable.name.like("${oldName}_%")
                        .and(UserItemsTable.parent_id.isNull())
                        .and(UserItemsTable.deleted_at.isNull())
                }.map { it[UserItemsTable.name] }
        }

        val maxIndex = existingNames
            .mapNotNull { name ->
                suffixRegex.find(name)?.groupValues?.get(1)?.toInt()
            }.maxOrNull() ?: 0

        return "${oldName}_${maxIndex + 1}"
    }

    fun normalizeName(name: String): String {
        val pattern = Regex("^(.*)_(\\d+)$")
        val match = pattern.matchEntire(name)
        return match?.groupValues?.get(1) ?: name
    }


    fun computeHashVersion(content: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(content.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun isItemOwnedByUser(userId: UUID, itemUUID: UUID): FunctionResult<Boolean> {
        return try {
            transaction {
                FunctionResult.Success(
                    UserItemsTable.selectAll()
                        .where {
                            (uid eq itemUUID) and
                                    (owner_id eq userId)
                        }.limit(1).any()
                )
            }
        } catch (ex: Exception) {
            println("Get an exception: $ex")
            return FunctionResult.Error("Get an exception: $ex")
        }
    }
}


