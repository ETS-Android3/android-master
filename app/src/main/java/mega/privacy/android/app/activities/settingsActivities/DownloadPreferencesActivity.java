package mega.privacy.android.app.activities.settingsActivities;

import android.os.Bundle;

import mega.privacy.android.app.R;
import mega.privacy.android.app.fragments.settingsFragments.DownloadSettingsFragment;

public class DownloadPreferencesActivity extends PreferencesBaseActivity {

    private DownloadSettingsFragment sttDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.download_location);

        sttDownload = new DownloadSettingsFragment();
        replaceFragment(sttDownload);
    }
}
