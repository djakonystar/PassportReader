package site.texnopos.djakonystar.passportreader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import jj2000.j2k.decoder.Decoder
import jj2000.j2k.util.ParameterList
import org.jmrtd.lds.AbstractImageInfo
import java.io.*

object ImageUtil {
    fun getImage(context: Context, imageInfo: AbstractImageInfo): Image {
        val image = Image()
        val imageLength = imageInfo.imageLength
        val dataInputStream = DataInputStream(imageInfo.imageInputStream)
        val buffer = ByteArray(imageLength)
        try {
            dataInputStream.readFully(buffer, 0, imageLength)
            val inputStream: InputStream = ByteArrayInputStream(buffer, 0, imageLength)
            val bitmapImage = decodeImage(context, imageInfo.mimeType, inputStream)
            image.bitmapImage = bitmapImage
            val base64Image = Base64.encodeToString(buffer, Base64.DEFAULT)
            image.base64Image = base64Image
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return image
    }

    fun scaleImage(bitmap: Bitmap?): Bitmap? {
        var bitmapImage: Bitmap? = null
        if (bitmap != null) {
            val ratio = 400.0 / bitmap.height
            val targetHeight = (bitmap.height * ratio).toInt()
            val targetWidth = (bitmap.width * ratio).toInt()
            bitmapImage = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false)
        }
        return bitmapImage
    }

    @Throws(IOException::class)
    fun decodeImage(context: Context, mimeType: String, inputStream: InputStream): Bitmap? {
        return if (mimeType.equals(
                "image/jp2",
                ignoreCase = true
            ) || mimeType.equals("image/jpeg2000", ignoreCase = true)
        ) {

            // Save jp2 file
            val output: OutputStream = FileOutputStream(File(context.cacheDir, "temp.jp2"))
            val buffer = ByteArray(1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            output.close()

            // Decode jp2 file
            val pinfo = Decoder.getAllParameters()
            val parameters: ParameterList
            val defaults = ParameterList()
            for (i in pinfo.indices.reversed()) {
                if (pinfo[i][3] != null) {
                    defaults[pinfo[i][0]] = pinfo[i][3]
                }
            }
            parameters = ParameterList(defaults)
            parameters.setProperty("rate", "3")
            parameters.setProperty("o", context.cacheDir.toString() + "/temp.ppm")
            parameters.setProperty("debug", "on")
            parameters.setProperty("i", context.cacheDir.toString() + "/temp.jp2")
            val decoder = Decoder(parameters)
            decoder.run()

            // Read ppm file
            val reader = BufferedInputStream(
                FileInputStream(File(context.cacheDir.toString() + "/temp.ppm"))
            )
            if (reader.read() != 'P'.code || reader.read() != '6'.code) return null
            reader.read()
            var widths: String? = ""
            var heights: String? = ""
            var temp: Char
            while (reader.read().toChar().also { temp = it } != ' ') widths += temp
            while (reader.read().toChar().also { temp = it } >= '0' && temp <= '9') heights += temp
            if (reader.read() != '2'.code || reader.read() != '5'.code || reader.read() != '5'.code) return null
            reader.read()
            val width = Integer.valueOf(widths)
            val height = Integer.valueOf(heights)
            val colors = IntArray(width * height)
            val pixel = ByteArray(3)
            var len: Int
            var cnt = 0
            var total = 0
            val rgb = IntArray(3)
            while (reader.read(pixel).also { len = it } > 0) {
                for (i in 0 until len) {
                    rgb[cnt] = if (pixel[i] >= 0) pixel[i].toInt() else pixel[i] + 255
                    if (++cnt == 3) {
                        cnt = 0
                        colors[total++] = Color.rgb(rgb[0], rgb[1], rgb[2])
                    }
                }
            }
            Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
        }  else {
            BitmapFactory.decodeStream(inputStream)
        }
    }
}