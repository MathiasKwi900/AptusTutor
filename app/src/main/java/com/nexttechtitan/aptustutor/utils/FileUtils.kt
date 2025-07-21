package com.nexttechtitan.aptustutor.utils

import android.content.Context
import java.io.File
import java.io.IOException

object FileUtils {

    /**
     * Creates a permanent, unique file path for a student's answer image and saves the
     * given byte array to it.
     *
     * @param context The application context.
     * @param byteArray The compressed image data to save.
     * @param submissionId A unique ID for the submission this answer belongs to.
     * @param questionId A unique ID for the question this answer is for.
     * @return The absolute path to the newly created permanent file, or null on failure.
     */
    fun saveAnswerImage(
        context: Context,
        byteArray: ByteArray,
        submissionId: String,
        questionId: String
    ): String? {
        return try {
            // Create a structured directory: files/submissions/[submissionId]/
            val submissionDir = File(context.filesDir, "submissions/$submissionId")
            submissionDir.mkdirs() // Create directories if they don't exist

            // Create the final file and write the data
            val imageFile = File(submissionDir, "$questionId.jpg")
            imageFile.writeBytes(byteArray)

            // Return the permanent, reliable path
            imageFile.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}