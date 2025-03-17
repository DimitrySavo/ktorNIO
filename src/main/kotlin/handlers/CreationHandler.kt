package com.example.handlers

import com.example.CreateObject
import com.example.daos.StorageItemsIds
import com.example.daos.UserItemsTable

fun userItemCreationHandler(instance: CreateObject, ownerId: Int) {
    when (instance.type) {
        StorageItemsIds.md.name -> {
            UserItemsTable.createItem(
                uid = instance.data.uid,
                parent_id = instance.data.parent_id,
                user_id = ownerId,
                name = instance.data.name,
                type = StorageItemsIds.md
            )
        }

        StorageItemsIds.folder.name -> {
            UserItemsTable.createItem(
                uid = instance.data.uid,
                parent_id = instance.data.parent_id,
                user_id = ownerId,
                name = instance.data.name,
                type = StorageItemsIds.folder
            )
        }
    }
}