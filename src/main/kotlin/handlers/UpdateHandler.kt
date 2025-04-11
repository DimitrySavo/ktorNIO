package com.example.handlers

import com.example.utils.FunctionResult
import com.example.UpdateObject
import com.example.daos.StorageItemsIds
import com.example.daos.UserItemsTable
import java.util.UUID

fun updateHandler(instance: UpdateObject, uid: UUID, type: StorageItemsIds): FunctionResult<Unit> {
    return UserItemsTable.updateItem(
        uid = uid,
        type = type,
        fileContent = instance.data.fileContent,
        name = instance.data.name,
        parentId = instance.data.parent_id
    )
}