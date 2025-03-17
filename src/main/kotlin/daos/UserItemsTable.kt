package com.example.daos

import jdk.jfr.internal.event.EventConfiguration.timestamp
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object UserItemsTable : Table("useritems") {
    val uid = uuid("uid")
    val parent_id = uuid("parent_uid")
    val name = varchar("name", 255)
    val type = integer("type").references(StorageItemsTypesTable.typeId)
    val version = text("version")
    val created_at = timestamp("created_at")
    val updated_at = timestamp("updated_at")
}