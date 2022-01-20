package mega.privacy.android.app.utils

import android.content.Context
import android.text.Spanned
import android.util.Base64
import androidx.annotation.ColorRes
import androidx.core.text.HtmlCompat
import mega.privacy.android.app.R
import nz.mega.sdk.MegaApiJava
import nz.mega.sdk.MegaStringMap
import java.lang.Exception

object StringUtils {

    @JvmStatic
    fun String.toSpannedHtmlText(): Spanned =
        HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY)

    @JvmStatic
    fun String.isTextEmpty() = TextUtil.isTextEmpty(this)

    @JvmStatic
    fun String.toThrowable(): Throwable = Throwable(this)

    /**
     * Format String with HTML color tags
     *
     * @param context   Context required to get resources
     * @param tag       Tag to be replaced with font color
     * @param color     Color to be colored with
     * @return          New String with HTML
     */
    @JvmStatic
    fun String.formatColorTag(context: Context, tag: Char, @ColorRes color: Int): String =
        replace("[$tag]", "<font color='${ColorUtils.getColorHexString(context, color)}'>")
            .replace("[/$tag]", "</font>")

    /**
     * Decode the Base64-encoded data into a new formatted String
     */
    fun String.decodeBase64(): String =
        Base64.decode(this, Base64.DEFAULT).toString(Charsets.UTF_8)

    /**
     * Decode each alias within MegaStringMap into a Map<Long, String>
     */
    fun MegaStringMap.getDecodedAliases(): Map<Long, String> {
        val aliases = mutableMapOf<Long, String>()

        for (i in 0 until keys.size()) {
            val base64Handle = keys[i]
            val handle = MegaApiJava.base64ToUserHandle(base64Handle)
            val alias = get(base64Handle).decodeBase64()
            aliases[handle] = alias
        }

        return aliases
    }

    /**
     * Format String pair to get date title.
     *
     * @param date String pair, which contains the whole date string.
     * @return Formatted Spanned can be set to TextView.
     */
    fun  Pair<String?, String?>.formatDateTitle() : Spanned {
        var dateText =
            if (TextUtil.isTextEmpty(this.second)) "[B]" + this.first + "[/B]" else StringResourcesUtils.getString(
                R.string.cu_month_year_date,
                this.first,
                this.second
            )
        try {
            dateText = dateText.replace("[B]", "<font face=\"sans-serif-medium\">")
                .replace("[/B]", "</font>")
        } catch (e: Exception) {
            LogUtil.logWarning("Exception formatting text.", e)
        }

        return dateText.toSpannedHtmlText()
    }
}
