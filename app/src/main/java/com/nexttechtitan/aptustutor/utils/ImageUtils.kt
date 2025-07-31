package com.nexttechtitan.aptustutor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.DecimalFormat

/**
 * Utility object for image manipulation tasks.
 */
object ImageUtils {

    private const val MAX_FILE_SIZE_MB = 20

    /**
     * Represents the result of an image compression operation.
     */
    sealed class ImageCompressionResult {
        /**
         * Indicates successful compression.
         * @property byteArray The compressed image data.
         */
        data class Success(val byteArray: ByteArray) : ImageCompressionResult() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as Success
                return byteArray.contentEquals(other.byteArray)
            }

            override fun hashCode(): Int {
                return byteArray.contentHashCode()
            }
        }

        /**
         * Indicates a failure during compression.
         * @property message A descriptive error message.
         * @property exception The exception that caused the failure, if any.
         */
        data class Error(val message: String, val exception: Throwable? = null) : ImageCompressionResult()
    }

    /**
     * Compresses an image from a given URI.
     * This function decodes the image, optionally downsamples it to fit within the requested
     * dimensions while maintaining aspect ratio, and then compresses it to the specified format and quality.
     * It performs operations on the [Dispatchers.IO] thread.
     *
     * @param context The context to use for accessing the content resolver.
     * @param uri The URI of the image to compress.
     * @param quality The quality of the compressed image (0-100). Only applies to formats like JPEG and WEBP_LOSSY.
     * @param reqWidth The maximum width the decoded image should have.
     * @param reqHeight The maximum height the decoded image should have.
     * @param format The desired [Bitmap.CompressFormat] for the output.
     * @return [ImageCompressionResult.Success] containing the compressed byte array,
     *         or [ImageCompressionResult.Error] if compression fails.
     */
    @JvmStatic
    suspend fun compressImage(
        context: Context,
        uri: Uri,
        quality: Int = 90,
        reqWidth: Int = 1024,
        reqHeight: Int = 1024,
        targetSizeKb: Int = 500,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ): ImageCompressionResult {
        return withContext(Dispatchers.IO) {
            var inputStream: InputStream? = null
            var secondInputStream: InputStream? = null
            var bitmap: Bitmap? = null

            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val fileSizeInMb = pfd.statSize / (1024 * 1024)
                    if (fileSizeInMb > MAX_FILE_SIZE_MB) {
                        return@withContext ImageCompressionResult.Error(
                            "Image is too large (${DecimalFormat("#.##").format(fileSizeInMb)} MB). Please select a file smaller than $MAX_FILE_SIZE_MB MB."
                        )
                    }
                }
                inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext ImageCompressionResult.Error("Unable to open InputStream for URI: $uri")

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()
                inputStream = null

                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    return@withContext ImageCompressionResult.Error(
                        "Failed to decode image bounds. URI might be invalid or image corrupted: $uri"
                    )
                }

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inJustDecodeBounds = false

                secondInputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext ImageCompressionResult.Error(
                        "Unable to open InputStream for URI (second pass): $uri"
                    )

                bitmap = BitmapFactory.decodeStream(secondInputStream, null, options)
                    ?: return@withContext ImageCompressionResult.Error("Failed to decode bitmap from URI: $uri")

                val outputStream = ByteArrayOutputStream()

                var currentQuality = quality
                bitmap.compress(format, currentQuality, outputStream)

                while (outputStream.size() / 1024 > targetSizeKb && currentQuality > 50) {
                    currentQuality -= 10
                    outputStream.reset()
                    bitmap.compress(format, currentQuality, outputStream)
                }
                val compressedBytes = outputStream.toByteArray()
                ImageCompressionResult.Success(compressedBytes)

            } catch (e: SecurityException) {
                ImageCompressionResult.Error("Permission denied for URI: $uri", e)
            } catch (e: IOException) {
                ImageCompressionResult.Error("IO error compressing image: ${e.message}", e)
            } catch (e: OutOfMemoryError) {
                ImageCompressionResult.Error("Out of memory while compressing image. Try a smaller image or reduce quality/size.", e)
            } catch (e: Exception) {
                ImageCompressionResult.Error("An unexpected error occurred: ${e.message}", e)
            } finally {
                try {
                    inputStream?.close()
                    secondInputStream?.close()
                } catch (e: IOException) {
                    //
                }
                bitmap?.recycle()
            }
        }
    }

    /**
     * Calculates the [BitmapFactory.Options.inSampleSize] value to decode a scaled-down
     * version of the image to save memory. The sample size is calculated to ensure
     * that the final image dimensions are close to the requested width and height,
     * while maintaining the original aspect ratio.
     *
     * The returned value will always be a power of 2, as this is generally
     * more efficient for [BitmapFactory].
     *
     * @param options The [BitmapFactory.Options] object containing the original image dimensions (`outWidth`, `outHeight`).
     * @param reqWidth The desired maximum width of the decoded image.
     * @param reqHeight The desired maximum height of the decoded image.
     * @return The calculated `inSampleSize`.
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight || (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}