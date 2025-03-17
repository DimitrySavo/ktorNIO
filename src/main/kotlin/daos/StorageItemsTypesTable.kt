package com.example.daos

import org.jetbrains.exposed.sql.Table

object StorageItemsTypesTable: Table("storageitemtypes") {
    val typeId = integer("typeid").autoIncrement()
    val typeName = varchar("typename", 50)
    override val primaryKey = PrimaryKey(typeId)
}

object StorageItemsNames {
    const val MD = "md"
    const val FOLDER = "folder"
}