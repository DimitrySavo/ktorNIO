package com.example.handlers

import com.example.FunctionResult
import com.example.daos.UserItemsTable
import com.example.data.readFromFile
import java.util.UUID

fun getUserItemContent(uid: UUID): FunctionResult<Map<String, String>> {
    val resultMap = mutableMapOf<String, String>()

    val fileVersion = UserItemsTable.getItemVersion(uid)
    when (fileVersion) {
        is FunctionResult.Error -> return FunctionResult.Error("Error while getting file version")
        is FunctionResult.Success -> resultMap["version"] = fileVersion.data
    }

    val fileContent = readFromFile(uid.toString())
    when (fileContent) {
        is FunctionResult.Error -> return FunctionResult.Error("Error while getting file content")
        is FunctionResult.Success -> resultMap["content"] = fileContent.data
    }

    return FunctionResult.Success(resultMap)
}