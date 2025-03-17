package com.example.handlers

import com.example.FunctionResult
import com.example.daos.UserItemsTable
import java.util.UUID

fun handleItemDelete(itemUid: UUID) : FunctionResult<Unit> {
    return UserItemsTable.softItemDeletion(uid = itemUid)
}