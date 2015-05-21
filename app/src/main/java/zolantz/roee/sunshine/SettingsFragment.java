package zolantz.roee.sunshine;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

public class SettingsFragment extends PreferenceFragment {

    private SharedPreferences.OnSharedPreferenceChangeListener mListener =
        new SharedPreferences.OnSharedPreferenceChangeListener() {

            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals(getString(R.string.pref_location_key))) {
                    Preference locationPref = findPreference(key);
                    locationPref.setSummary(sharedPreferences.getString(key, getString(R.string.pref_location_default)));
                }

                if (key.equals(getString(R.string.pref_units_key))) {
                    Preference tempUnitsPref = findPreference(key);
                    tempUnitsPref.setSummary(sharedPreferences.getString(key, getString(R.string.pref_units_metric)));
                }
            }
        };

    public SettingsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        try {
            addPreferencesFromResource(R.xml.pref_general);
        } catch (Exception ex) {
            Toast.makeText(getActivity(), "lala", Toast.LENGTH_SHORT).show();
        }
        // Update the location preferences
        // Update can also be done with a listener
        findPreference(getString(R.string.pref_location_key))
            .setSummary(PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default)));

        // Update the temp units preference
        findPreference(getString(R.string.pref_units_key))
            .setSummary(PreferenceManager.getDefaultSharedPreferences(getActivity())
                    .getString(getString(R.string.pref_units_key), getString(R.string.pref_units_metric)));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_settings, container, false);
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
            .registerOnSharedPreferenceChangeListener(mListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
            .unregisterOnSharedPreferenceChangeListener(mListener);
    }
}