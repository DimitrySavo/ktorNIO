package com.example.handlers

import com.example.CreateObject
import com.example.utils.FunctionResult
import com.example.daos.StorageItemsIds
import com.example.daos.StorageItemsTypesTable
import com.example.daos.User
import com.example.daos.UserItemsTable
import java.util.UUID

fun userItemCreationHandler(instance: CreateObject, ownerId: UUID) : FunctionResult<String> {
    val types = StorageItemsTypesTable.getStorageTypes()

    if (types.isNullOrEmpty()) {
        return FunctionResult.Error("Types is empty somehow")
    }

    val type = types.find { it.mimeType == instance.type }
    if (type == null) {
        return FunctionResult.Error("Unknown type")
    }

    return UserItemsTable.createItem(
        uid = instance.data.uid,
        parent_id = instance.data.parent_id,
        user_id = ownerId,
        name = instance.data.name,
        type = type
    )
}