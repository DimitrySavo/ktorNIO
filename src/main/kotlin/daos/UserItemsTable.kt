package com.example.daos

import com.example.utils.FunctionResult
import com.example.StorageItemResponse
import com.example.data.createFileInMinio
import com.example.data.readFromFile
import com.example.data.replaceFileMinio
import com.github.difflib.DiffUtils
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.sql.SQLException
import java.time.Instant
import java.util.*

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
                select(version)
                    .where { UserItemsTable.uid eq uid  }
                    .singleOrNull()
                    ?.get(version)
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
    ) : FunctionResult<Unit> {
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
                    row[updated_at] = Instant.now()
                    if (name != null) {
                        row[UserItemsTable.name] = name
                    }
                    if (parentId != null) {
                        row[parent_id] = parentId
                    }
                    if (hashSum != null) {
                        row[version] = hashSum
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
        uid: UUID,
        name: String? = null,
        parentId: UUID? = null,
        version: String,
        baseline: String,
        modifiedText: String,
        type: StorageItemsIds
    ): FunctionResult<Unit> {
        println("Get into updateItemWithVersion")
        val dmp = DiffMatchPatch()
        println("Create dmp")

        val lockAcquired = try {
            println("Get into lock try")
            transaction {
                println("Get into lock transaction")
                exec("select pg_advisory_lock(hashtext(\'$uid\'))")
            }
            println("Lock is good")
            true
        } catch (e: Exception) {
            println("Get an exception: $e")
            false
        }

        println("Lock is: $lockAcquired")

        if (!lockAcquired) {
            println("Can't lock resource in updateItemWithVersion")
            return FunctionResult.Error("Can't lock resource")
        }

        val serverVersion = transaction {
            select(UserItemsTable.version)
                .where { UserItemsTable.uid eq uid }
                .singleOrNull()?.get(UserItemsTable.version)
        }

        println("Server version is: $serverVersion")

        if (serverVersion == version || serverVersion == null) {
            //Just replacing server text with new one from client. Cuz if we don't have difference between baseline and server version so we either don't need to calculate diffs
            replaceFileMinio(
                uid = uid,
                type = type,
                content = modifiedText,
            )

            val hashSum = computeHashVersion(modifiedText)

            transaction {
                UserItemsTable.update({ UserItemsTable.uid eq uid }) { row ->
                    row[updated_at] = Instant.now()
                    row[UserItemsTable.version] = hashSum
                    if (name != null) row[UserItemsTable.name] = name
                    if (parentId != null) row[parent_id] = parentId
                }
            }

            transaction {
                exec("select pg_advisory_unlock(hashtext($uid))")
            }
            println("Item successfully updated cuz don't have any changes on server")
            return FunctionResult.Success(Unit)
        } else {
            //считаем patch для версии пользователя и для серверной версии. Дальше смотрим а наличие конфликтов и получаем еще один if
            println("Start calculating patches")
            when (val serverText = readFromFile(uid.toString())) {
                is FunctionResult.Error -> {
                    println("Can't read from file")
                    return FunctionResult.Error("Can't read from file")
                }
                is FunctionResult.Success -> {
                    println("Baseline: $baseline")
                    println("Server Text: ${serverText.data}")
                    println("Modified Text: $modifiedText")



                    println("Finish merge process")
                    return FunctionResult.Success(Unit)

                   /* val serverDiff = dmp.diffMain(baseline, serverText.data)
                    val userDiff = dmp.diffMain(baseline, modifiedText)

                    val userPatches = dmp.patchMake(baseline, userDiff)
                    val serverPatches = dmp.patchMake(baseline, serverDiff)

                    val (patchedBaseline, serverResult) = dmp.patchApply(serverPatches, baseline)

                    println("Server patches result: ${(serverResult as BooleanArray).joinToString()}}")
                    println("Sever new text: $patchedBaseline")

                    val (finalText, userResult) = dmp.patchApply(userPatches, patchedBaseline as String)

                    // work but has some issues with complicated merges and give false as result on some changes. Should add handler to false value in result array
                    println("Final patches result: ${(userResult as BooleanArray).joinToString()}")
                    println("Final new text: $finalText")

                    transaction {
                        exec("select pg_advisory_unlock(hashtext(\'$uid\'))")
                    }
                    println("Poka hz che-to proizoshlo")
                    return FunctionResult.Success(Unit)*/
                }
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
            created_at = this[created_at].epochSecond,
            updated_at = this[updated_at].epochSecond,
            deleted_at = this[deleted_at]?.epochSecond
        )
    }

    fun getUserItems(userId: Int): FunctionResult<List<StorageItemResponse>> {
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

    fun getDeletedUserItems(userId: Int): FunctionResult<List<StorageItemResponse>> {
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

    fun computeHashVersion(content: String) : String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(content.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}


