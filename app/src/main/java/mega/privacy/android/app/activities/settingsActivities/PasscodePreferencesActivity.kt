package mega.privacy.android.app.activities.settingsActivities

import android.os.Bundle
import mega.privacy.android.app.R
import mega.privacy.android.app.fragments.settingsFragments.SettingsPasscodeLockFragment

class PasscodePreferencesActivity : PreferencesBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.settings_passcode_lock)

        if (savedInstanceState == null) {
            replaceFragment(SettingsPasscodeLockFragment())
        }
    }
}