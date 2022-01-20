package mega.privacy.android.app.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.NumberPicker.OnValueChangeListener
import android.widget.NumberPicker.OnScrollListener
import mega.privacy.android.app.MegaApplication
import mega.privacy.android.app.R
import mega.privacy.android.app.constants.BroadcastConstants
import mega.privacy.android.app.constants.BroadcastConstants.ACTION_UPDATE_RETENTION_TIME
import mega.privacy.android.app.databinding.ActivityManageChatHistoryBinding
import mega.privacy.android.app.listeners.SetRetentionTimeListener
import mega.privacy.android.app.utils.ChatUtil
import mega.privacy.android.app.utils.ChatUtil.createHistoryRetentionAlertDialog
import mega.privacy.android.app.utils.Constants.*
import mega.privacy.android.app.utils.LogUtil.logDebug
import mega.privacy.android.app.utils.LogUtil.logError
import mega.privacy.android.app.utils.StringResourcesUtils
import mega.privacy.android.app.utils.TextUtil
import nz.mega.sdk.MegaApiJava.INVALID_HANDLE
import nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE
import nz.mega.sdk.MegaChatRoom
import java.util.*

class ManageChatHistoryActivity : PasscodeActivity(), View.OnClickListener {
    companion object {
        private const val OPTION_HOURS = 0
        private const val OPTION_DAYS = 1
        private const val OPTION_MONTHS = 3
        private const val OPTION_WEEKS = 2
        private const val OPTION_YEARS = 4

        private const val MINIMUM_VALUE_NUMBER_PICKER = 1
        private const val DAYS_IN_A_MONTH_VALUE = 30
        private const val MAXIMUM_VALUE_NUMBER_PICKER_HOURS = 24
        private const val MAXIMUM_VALUE_NUMBER_PICKER_DAYS = 31
        private const val MAXIMUM_VALUE_NUMBER_PICKER_WEEKS = 4
        private const val MAXIMUM_VALUE_NUMBER_PICKER_MONTHS = 12
        private const val MINIMUM_VALUE_TEXT_PICKER = 0
        private const val MAXIMUM_VALUE_TEXT_PICKER = 4
    }

    private var chat: MegaChatRoom? = null
    private var chatId = MEGACHAT_INVALID_HANDLE
    private var contactHandle = INVALID_HANDLE
    private var isFromContacts = false
    private lateinit var binding: ActivityManageChatHistoryBinding

    private val retentionTimeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == null || intent.action != ACTION_UPDATE_RETENTION_TIME) {
                return
            }

            val seconds =
                intent.getLongExtra(BroadcastConstants.RETENTION_TIME, DISABLED_RETENTION_TIME)
            updateRetentionTimeUI(seconds)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent == null || intent.extras == null) {
            logError("Cannot init view, Intent is null")
            finish()
        }

        chatId = intent.extras!!.getLong(CHAT_ID)
        isFromContacts = intent.extras!!.getBoolean(IS_FROM_CONTACTS)

        if (chatId != MEGACHAT_INVALID_HANDLE) {
            chat = megaChatApi.getChatRoom(chatId)
        } else {
            val email = intent.extras!!.getString(EMAIL)

            if (TextUtil.isTextEmpty(email)) {
                logError("Cannot init view, contact' email is empty")
                finish()
            }

            val contact = megaApi.getContact(email)
            if (contact == null) {
                logError("Cannot init view, contact is null")
                finish()
            }

            contactHandle = contact?.handle!!

            chat = megaChatApi.getChatRoomByUser(contactHandle)
            if (chat != null)
                chatId = chat?.chatId!!
        }

        registerReceiver(
            retentionTimeReceiver,
            IntentFilter(ACTION_UPDATE_RETENTION_TIME)
        )

        binding = ActivityManageChatHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.manageChatToolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setHomeButtonEnabled(true)
        actionBar?.title = getString(R.string.title_properties_manage_chat).toUpperCase(Locale.getDefault())

        binding.historyRetentionSwitch.isClickable = false
        binding.historyRetentionSwitch.isChecked = false
        binding.pickerLayout.visibility = View.GONE
        binding.separator.visibility = View.GONE

        if (chat == null) {
            logDebug("The chat does not exist")
            binding.historyRetentionSwitchLayout.setOnClickListener(null)
            binding.clearChatHistoryLayout.setOnClickListener(null)
            binding.retentionTimeTextLayout.setOnClickListener(null)
            binding.retentionTimeSubtitle.text =
                getString(R.string.subtitle_properties_history_retention)
            binding.retentionTime.visibility = View.GONE
        } else {
            logDebug("The chat exists")
            binding.historyRetentionSwitchLayout.setOnClickListener(this)
            binding.clearChatHistoryLayout.setOnClickListener(this)

            val seconds = chat!!.retentionTime
            updateRetentionTimeUI(seconds)

            binding.numberPicker.setOnScrollListener(onScrollListenerPickerNumber)
            binding.numberPicker.setOnValueChangedListener(onValueChangeListenerPickerNumber)
            binding.textPicker.setOnScrollListener(onScrollListenerPickerText)
            binding.textPicker.setOnValueChangedListener(onValueChangeListenerPickerText)
            binding.pickerButton.setOnClickListener(this)
        }
    }

    private var onValueChangeListenerPickerNumber =
        OnValueChangeListener { _, oldValue, newValue ->
            updateTextPicker(oldValue, newValue)
        }

    private var onValueChangeListenerPickerText =
        OnValueChangeListener { textPicker, _, _ ->
            updateNumberPicker(textPicker.value)
        }

    private var onScrollListenerPickerNumber =
        OnScrollListener { _, scrollState ->
            if (scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
                updateOptionsAccordingly()
            }
        }

    private var onScrollListenerPickerText =
        OnScrollListener { _, scrollState ->
            if (scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
                updateOptionsAccordingly()
            }
        }

    /**
     * Method that controls and shows the initial UI of the picket elements.
     *
     * @param seconds The time the retention time is enabled.
     */
    fun showPickers(seconds: Long) {
        logDebug("Show the pickers")
        binding.pickerLayout.visibility = View.VISIBLE
        binding.separator.visibility = View.VISIBLE

        binding.numberPicker.wrapSelectorWheel = true
        binding.textPicker.wrapSelectorWheel = true

        binding.textPicker.minimumWidth = MAXIMUM_VALUE_TEXT_PICKER
        binding.numberPicker.minValue = MINIMUM_VALUE_NUMBER_PICKER
        binding.textPicker.minValue = MINIMUM_VALUE_TEXT_PICKER
        binding.textPicker.maxValue = MAXIMUM_VALUE_TEXT_PICKER

        if (seconds == DISABLED_RETENTION_TIME) {
            updatePickersValues(
                MINIMUM_VALUE_TEXT_PICKER,
                MAXIMUM_VALUE_NUMBER_PICKER_HOURS,
                MINIMUM_VALUE_NUMBER_PICKER
            )
        } else {
            checkPickersValues(seconds)
        }
    }

    /**
     * Method for filling the text picker array from the value of the picker number value.
     *
     * @param value The current value of number picker.
     */
    private fun fillPickerText(value: Int) {
        binding.textPicker.displayedValues = null
        val arrayString: Array<String> = arrayOf(
            StringResourcesUtils.getQuantityString(
                R.plurals.retention_time_picker_hours,
                value
            ).toLowerCase(Locale.getDefault()),
            StringResourcesUtils.getQuantityString(
                R.plurals.retention_time_picker_days,
                value
            ).toLowerCase(Locale.getDefault()),
            StringResourcesUtils.getQuantityString(
                R.plurals.retention_time_picker_weeks,
                value
            ).toLowerCase(Locale.getDefault()),
            StringResourcesUtils.getQuantityString(
                R.plurals.retention_time_picker_months,
                value
            ).toLowerCase(Locale.getDefault()),
            StringResourcesUtils.getString(R.string.retention_time_picker_year).toLowerCase(Locale.getDefault())
        )
        binding.textPicker.displayedValues = arrayString
    }

    /**
     * Updates the initial values of the pickers.
     *
     * @param textValue The current value of text picker
     * @param maximumValue The maximum value of numbers picker
     * @param numberValue The current value of number picker
     */
    private fun updatePickersValues(textValue: Int, maximumValue: Int, numberValue: Int) {
        if (maximumValue < numberValue) {
            binding.textPicker.value = OPTION_HOURS
            binding.numberPicker.maxValue = MAXIMUM_VALUE_NUMBER_PICKER_HOURS
            binding.numberPicker.value = MINIMUM_VALUE_NUMBER_PICKER

        } else {
            binding.textPicker.value = textValue
            binding.numberPicker.maxValue = maximumValue
            binding.numberPicker.value = numberValue
        }

        fillPickerText(binding.numberPicker.value)
    }

    /**
     * Controls the initial values of the pickers.
     *
     * @param seconds The retention time in seconds.
     */
    private fun checkPickersValues(seconds: Long) {
        val numberYears = seconds / SECONDS_IN_YEAR
        val years = seconds - numberYears * SECONDS_IN_YEAR

        if (years == 0L) {
            updatePickersValues(OPTION_YEARS, MINIMUM_VALUE_NUMBER_PICKER, numberYears.toInt())
            return
        }

        val numberMonths = seconds / SECONDS_IN_MONTH_30
        val months = seconds - numberMonths * SECONDS_IN_MONTH_30

        if (months == 0L) {
            updatePickersValues(
                OPTION_MONTHS,
                MAXIMUM_VALUE_NUMBER_PICKER_MONTHS,
                numberMonths.toInt()
            )
            return
        }

        val numberWeeks = seconds / SECONDS_IN_WEEK
        val weeks = seconds - numberWeeks * SECONDS_IN_WEEK

        if (weeks == 0L) {
            updatePickersValues(
                OPTION_WEEKS,
                MAXIMUM_VALUE_NUMBER_PICKER_WEEKS,
                numberWeeks.toInt()
            )
            return
        }

        val numberDays = seconds / SECONDS_IN_DAY
        val days = seconds - numberDays * SECONDS_IN_DAY

        if (days == 0L) {
            updatePickersValues(OPTION_DAYS, MAXIMUM_VALUE_NUMBER_PICKER_DAYS, numberDays.toInt())
            return
        }

        val numberHours = seconds / SECONDS_IN_HOUR
        val hours = seconds - numberHours * SECONDS_IN_HOUR

        if (hours == 0L) {
            updatePickersValues(
                OPTION_HOURS,
                MAXIMUM_VALUE_NUMBER_PICKER_HOURS,
                numberHours.toInt()
            )
        }
    }

    /**
     * Updates the values of the text picker according to the current value of the number picker.
     *
     * @param oldValue the previous value of the number picker
     * @param newValue the current value of the number picker
     */
    private fun updateTextPicker(oldValue: Int, newValue: Int) {
        if ((oldValue == 1 && newValue == 1) || (oldValue > 1 && newValue > 1))
            return

        if ((oldValue == 1 && newValue > 1) || (newValue == 1 && oldValue > 1)) {
            fillPickerText(newValue)
            binding.textPicker.minimumWidth = MAXIMUM_VALUE_TEXT_PICKER
        }
    }

    /**
     * Method that transforms the chosen option into the most correct form:
     * - If the option selected is 24 hours, it becomes 1 day.
     * - If the selected option is 31 days, it becomes 1 month.
     * - If the selected option is 4 weeks, it becomes 1 month.
     * - If the selected option is 12 months, it becomes 1 year.
     */
    private fun updateOptionsAccordingly(){
        if (binding.textPicker.value == OPTION_HOURS &&
            binding.numberPicker.value == MAXIMUM_VALUE_NUMBER_PICKER_HOURS
        ) {
            updatePickersValues(
                OPTION_DAYS,
                getMaximumValueOfNumberPicker(OPTION_DAYS),
                MINIMUM_VALUE_NUMBER_PICKER
            )
            return
        }

        if (binding.textPicker.value == OPTION_DAYS &&
            binding.numberPicker.value == DAYS_IN_A_MONTH_VALUE
        ) {
            updatePickersValues(
                OPTION_MONTHS,
                getMaximumValueOfNumberPicker(OPTION_MONTHS),
                MINIMUM_VALUE_NUMBER_PICKER
            )
            return
        }

        if (binding.textPicker.value == OPTION_WEEKS &&
            binding.numberPicker.value == MAXIMUM_VALUE_NUMBER_PICKER_WEEKS
        ) {
            updatePickersValues(
                OPTION_MONTHS,
                getMaximumValueOfNumberPicker(OPTION_MONTHS),
                MINIMUM_VALUE_NUMBER_PICKER
            )
            return
        }

        if (binding.textPicker.value == OPTION_MONTHS &&
            binding.numberPicker.value == MAXIMUM_VALUE_NUMBER_PICKER_MONTHS
        ) {
            updatePickersValues(
                OPTION_YEARS,
                getMaximumValueOfNumberPicker(OPTION_YEARS),
                MINIMUM_VALUE_NUMBER_PICKER
            )
            return
        }
    }

    /**
     *
     * Method for getting the maximum value of the picker number from a value.
     * @param value the value
     */
    private fun getMaximumValueOfNumberPicker(value: Int): Int {
        when (value) {
            OPTION_HOURS -> {
                return MAXIMUM_VALUE_NUMBER_PICKER_HOURS
            }
            OPTION_DAYS -> {
                return MAXIMUM_VALUE_NUMBER_PICKER_DAYS
            }
            OPTION_WEEKS -> {
                return MAXIMUM_VALUE_NUMBER_PICKER_WEEKS
            }
            OPTION_MONTHS -> {
                return MAXIMUM_VALUE_NUMBER_PICKER_MONTHS
            }
            OPTION_YEARS -> {
                return MINIMUM_VALUE_NUMBER_PICKER
            }
            else -> {
                return 0
            }
        }
    }

    /**
     * Method that updates the values of the number picker according to the current value of the text picker.
     *
     * @param value the current value of the text picker
     */
    private fun updateNumberPicker(value: Int) {
        val maximumValue = getMaximumValueOfNumberPicker(value)

        if (binding.numberPicker.value > maximumValue) {
            updateTextPicker(binding.numberPicker.value, MINIMUM_VALUE_NUMBER_PICKER)
            binding.numberPicker.value = MINIMUM_VALUE_NUMBER_PICKER
        }

        binding.numberPicker.maxValue = maximumValue
    }

    /**
     * Method for updating the UI when the retention time is updated.
     *
     * @param seconds The retention time in seconds
     */
    private fun updateRetentionTimeUI(seconds: Long) {
        val timeFormatted = ChatUtil.transformSecondsInString(seconds)
        if (TextUtil.isTextEmpty(timeFormatted)) {
            binding.retentionTimeTextLayout.setOnClickListener(null)
            binding.historyRetentionSwitch.isChecked = false
            binding.retentionTimeSubtitle.text =
                getString(R.string.subtitle_properties_history_retention)
            binding.retentionTime.visibility = View.GONE
        } else {
            binding.retentionTimeTextLayout.setOnClickListener(this)
            binding.historyRetentionSwitch.isChecked = true
            binding.retentionTimeSubtitle.text = getString(R.string.subtitle_properties_manage_chat)
            binding.retentionTime.text = timeFormatted
            binding.retentionTime.visibility = View.VISIBLE
        }

        binding.pickerLayout.visibility = View.GONE
        binding.separator.visibility = View.GONE
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.clear_chat_history_layout -> {
                ChatUtil.showConfirmationClearChat(this, chat)
            }

            R.id.history_retention_switch_layout -> {
                if (binding.historyRetentionSwitch.isChecked) {
                    MegaApplication.getInstance().megaChatApi.setChatRetentionTime(
                        chat!!.chatId,
                        DISABLED_RETENTION_TIME,
                        SetRetentionTimeListener(this)
                    )
                } else {
                    createHistoryRetentionAlertDialog(this, chatId, true)
                }
            }

            R.id.retention_time_text_layout -> {
                createHistoryRetentionAlertDialog(this, chatId, false)
            }

            R.id.picker_button -> {
                binding.pickerLayout.visibility = View.GONE
                binding.separator.visibility = View.GONE
                var secondInOption = 0

                when (binding.textPicker.value) {
                    OPTION_HOURS -> {
                        secondInOption = SECONDS_IN_HOUR
                    }
                    OPTION_DAYS -> {
                        secondInOption = SECONDS_IN_DAY
                    }
                    OPTION_WEEKS -> {
                        secondInOption = SECONDS_IN_WEEK
                    }
                    OPTION_MONTHS -> {
                        secondInOption = SECONDS_IN_MONTH_30
                    }
                    OPTION_YEARS -> {
                        secondInOption = SECONDS_IN_YEAR
                    }
                }

                val totalSeconds = binding.numberPicker.value * secondInOption
                megaChatApi.setChatRetentionTime(
                    chatId,
                    totalSeconds.toLong(),
                    SetRetentionTimeListener(this)
                )
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home)
            onBackPressed()

        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        unregisterReceiver(retentionTimeReceiver)
        super.onDestroy()
    }
}