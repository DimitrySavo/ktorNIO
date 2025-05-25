package com.example.daos

import com.example.Feedback
import com.example.utils.FunctionResult
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

object UsersFeedback : Table("usersfeedback") {
    val feedbackId = integer("feedbackid").autoIncrement()
    val userUid = uuid("useruid").references(Users.userId)
    val username = varchar("username", 100)
    val title = varchar("title", 60).nullable()
    val body = text("body").nullable()
    val rating = integer("rating")

    override val primaryKey = PrimaryKey(feedbackId)

    fun addFeedback(feedback: Feedback) : FunctionResult<Unit> {
        return try {
            transaction {
                UsersFeedback.insert {
                    it[this.userUid] = feedback.userUid
                    it[this.username] = feedback.username
                    it[this.title] = feedback.title
                    it[this.body] = feedback.body
                    it[this.rating] = feedback.rating
                }
            }
            FunctionResult.Success(Unit)
        } catch (e: Exception) {
            FunctionResult.Error(e.message ?: "Unknown error occurred while adding feedback")
        }
    }

}