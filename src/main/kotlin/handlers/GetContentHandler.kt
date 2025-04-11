package com.example.handlers

import com.example.utils.FunctionResult
import com.example.daos.UserItemsTable
import com.example.data.readFromFile
import java.util.UUID

fun getUserItemContent(uid: UUID, userId: Int): FunctionResult<Map<String, String>> {
    val resultMap = mutableMapOf<String, String>()

    when (val isFileOwned = UserItemsTable.isItemOwnedByUser(userId, uid)) {
        is FunctionResult.Error -> return FunctionResult.Error("Can't get because of exception: ${isFileOwned.message}")
        is FunctionResult.Success -> {
            if (isFileOwned.data) {
                when (val fileVersion = UserItemsTable.getItemVersion(uid)) {
                    is FunctionResult.Error -> return FunctionResult.Error("Error while getting file version")
                    is FunctionResult.Success -> resultMap["version"] = fileVersion.data
                }

                when (val fileContent = readFromFile(uid.toString())) {
                    is FunctionResult.Error -> return FunctionResult.Error("Error while getting file content")
                    is FunctionResult.Success -> resultMap["content"] = fileContent.data
                }

                return FunctionResult.Success(resultMap)
            } else {
                return FunctionResult.Error("File is not owned by user: $userId")
            }
        }
    }
}
