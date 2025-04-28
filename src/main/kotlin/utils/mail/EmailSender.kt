package com.example.utils.mail

import com.example.utils.FunctionResult
import io.github.cdimascio.dotenv.dotenv
import org.simplejavamail.api.email.Email
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder

class EmailSender {
    companion object {
        fun sendResetEmail(
            userEmail: String,
            username: String,
            resetCode: String
        ): FunctionResult<Unit> {
            val dotenv = dotenv()

            return try {

                val email: Email = EmailBuilder.startingBlank()
                    .from("NIO", dotenv["EMAIL_ADDRESS"]) // Отправитель
                    .to(username, userEmail)                    // Получатель
                    .withSubject("Сброс пароля")
                    .withPlainText("""
                    Здравствуйте, $username!
                    
                    Ваш код для сброса пароля: $resetCode
                    
                    Код действителен в течение 10 минут.
                    Если вы не запрашивали сброс пароля, просто проигнорируйте это письмо.
                    
                    С уважением, команда NIO
                """.trimIndent())
                    .buildEmail()
                MailerBuilder
                    .withSMTPServer(
                        "smtp.gmail.com",
                        587,
                        dotenv["EMAIL_ADDRESS"],
                        dotenv["APP_PASSWORD"]
                    )
                    .withTransportStrategy(TransportStrategy.SMTP_TLS)
                    .buildMailer()
                    .sendMail(email)

                FunctionResult.Success(Unit)
            } catch (ex: Exception) {
                ex.printStackTrace()
                FunctionResult.Error("Can't sent message: ${ex.localizedMessage}")
            }
        }
    }
}