package mega.privacy.android.app.fragments.homepage.photos

import android.content.Context
import mega.privacy.android.app.fragments.managerFragments.cu.CUCard
import mega.privacy.android.app.utils.FileUtil
import mega.privacy.android.app.utils.PreviewUtils
import mega.privacy.android.app.utils.Util
import nz.mega.sdk.MegaNode
import java.io.File
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.ArrayList

/**
 * Tool class used to organize MegaNode list by years, months and days.
 * MegaNode will be converted CUCard object.
 * Also will filter out the node which doesn't have local preview.
 */
class DateCardsProvider {

    /**
     * Days list.
     */
    private val days = ArrayList<CUCard>()

    /**
     * Months list.
     */
    private val months = ArrayList<CUCard>()

    /**
     * Years list.
     */
    private val years = ArrayList<CUCard>()

    /**
     * MegaNodes which doesn't have local preview.
     * Key: MegaNode
     * Value: Local path of the preview file that will be downloaded.
     */
    private val nodesWithoutPreview = mutableMapOf<MegaNode, String>()

    /**
     * Organize MegaNode list by years, months and days.
     * Convert MegaNode to CUCard and fill corresponding list.
     *
     * @param context Context object for get preview files' local path.
     * @param nodes MegaNode list, from which date cards will be extracted.
     */
    fun extractCardsFromNodeList(context: Context, nodes: List<MegaNode>) {
        var lastDayDate: LocalDate? = null
        var lastMonthDate: LocalDate? = null
        var lastYearDate: LocalDate? = null

        nodes.forEach foreach@{ node ->
            var shouldGetPreview = false
            val preview = File(
                PreviewUtils.getPreviewFolder(context),
                node.base64Handle + FileUtil.JPG_EXTENSION
            )

            val modifyDate = Util.fromEpoch(node.modificationTime)
            val day = DateTimeFormatter.ofPattern("dd").format(modifyDate)
            val month = DateTimeFormatter.ofPattern("MMMM").format(modifyDate)
            val year = DateTimeFormatter.ofPattern("uuuu").format(modifyDate)
            val sameYear = Year.from(LocalDate.now()) == Year.from(modifyDate)

            if (lastDayDate == null || lastDayDate!!.dayOfYear != modifyDate.dayOfYear) {
                shouldGetPreview = true
                lastDayDate = modifyDate
                val date =
                    DateTimeFormatter.ofPattern(if (sameYear) "dd MMMM" else "dd MMMM uuuu").format(lastDayDate)
                days.add(
                    CUCard(
                        node, if (preview.exists()) preview else null, day, month,
                        if (sameYear) null else year, date, modifyDate, 0
                    )
                )
            } else if (days.isNotEmpty()) {
                days[days.size - 1].incrementNumItems()
            }

            if (lastMonthDate == null || YearMonth.from(lastMonthDate) != YearMonth.from(modifyDate)
            ) {
                shouldGetPreview = true
                lastMonthDate = modifyDate
                val date = if (sameYear) month else DateTimeFormatter.ofPattern("MMMM yyyy").format(modifyDate)
                months.add(
                    CUCard(
                        node, if (preview.exists()) preview else null, null, month,
                        if (sameYear) null else year, date, modifyDate, 0
                    )
                )
            }

            if (lastYearDate == null || Year.from(lastYearDate) != Year.from(modifyDate)) {
                shouldGetPreview = true
                lastYearDate = modifyDate
                years.add(
                    CUCard(
                        node, if (preview.exists()) preview else null, null, null,
                        year, year, modifyDate, 0
                    )
                )
            }

            if (shouldGetPreview && !preview.exists()) {
                nodesWithoutPreview[node] = preview.absolutePath
            }
        }
    }

    // Public getter functions
    fun getDays() = days
    fun getMonths() = months
    fun getYears() = years
    fun getNodesWithoutPreview() = nodesWithoutPreview
}