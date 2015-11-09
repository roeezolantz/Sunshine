package zolantz.roee.sunshine;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.concurrent.Callable;
import javax.security.auth.callback.Callback;

import cn.pedant.SweetAlert.SweetAlertDialog;
import zolantz.roee.sunshine.data.WeatherContract;

import static android.view.View.OnClickListener;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment implements FetchWeatherTask.asyncTaskUIMethods {

    private static final String LOG_TAG = ForecastFragment.class.getSimpleName();

    private ForecastAdapter mForecastAdapter;

    private ListView mListView;
    private int mPosition = ListView.INVALID_POSITION;
    private boolean mUseTodayLayout;

    private static final String SELECTED_KEY = "selected_position";

    private static final int FORECAST_LOADER = 0;
    // For the forecast view we're showing only a small subset of the stored data.
    // Specify the columns we need.
    private static final String[] FORECAST_COLUMNS = {
            // In this case the id needs to be fully qualified with a table name, since
            // the content provider joins the location & weather tables in the background
            // (both have an _id column)
            // On the one hand, that's annoying.  On the other, you can search the weather table
            // using the location set by the user, which is only in the Location table.
            // So the convenience is worth it.
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.LocationEntry.COLUMN_COORD_LAT,
            WeatherContract.LocationEntry.COLUMN_COORD_LONG
    };

    // These indices are tied to FORECAST_COLUMNS.  If FORECAST_COLUMNS changes, these
    // must change.
    static final int COL_WEATHER_ID = 0;
    static final int COL_WEATHER_DATE = 1;
    static final int COL_WEATHER_DESC = 2;
    static final int COL_WEATHER_MAX_TEMP = 3;
    static final int COL_WEATHER_MIN_TEMP = 4;
    static final int COL_LOCATION_SETTING = 5;
    static final int COL_WEATHER_CONDITION_ID = 6;
    static final int COL_COORD_LAT = 7;
    static final int COL_COORD_LONG = 8;

    static SwipeRefreshLayout swipeLayout;
    private ProgressBar loadingSpinner;
    private ProgressDialog prgLoading;

    long startingTime;
    long endTime;

    //private static WeatherManager weatherManager;
    private static FetchWeatherTask weatherMachine;

    public ForecastFragment() {
    }

    @Override
    public void onStart() {
        super.onStart();

        swipeLayout = (SwipeRefreshLayout)getActivity().findViewById(R.id.swipe_container);
        swipeLayout.setColorSchemeResources(
                R.color.refresh_progress_1,
                R.color.refresh_progress_2,
                R.color.refresh_progress_3);
        swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {

            @Override
            public void onRefresh() {
                updateWeather(true, false);
            }
        });

        //this.prgLoading = new ProgressDialog(this.getActivity(), 0);

        // Gets the new weather info from the server asynchronic
        updateWeather(true, true);
    }

    /**
     * This method invokes the async task that updates the weather info by creating a new one every time
     */
    public void updateWeather(Boolean asyncOrNor, Boolean turnLoadingDialog) {

        SunshineSyncAdapter.syncImmediately(getActivity());

        // Creates a new async task because every async task can be invoked once.
        /*weatherMachine = new FetchWeatherTask(this, getActivity());
        weatherMachine.getUpdatedWeather();
        */
/*
        if (turnLoadingDialog) {
            prgLoading = new ProgressDialog(this.getActivity(), 0);
            prgLoading.setTitle("Loading");
            prgLoading.setMessage("Retriving weather forecast information from the server...");
            prgLoading.setCancelable(true);
            prgLoading.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    if (FetchWeatherTask.weatherTask.getStatus() != AsyncTask.Status.FINISHED) {
                        FetchWeatherTask.weatherTask.cancel(true);
                        Toast.makeText(getActivity(), "Cancelled", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            prgLoading.setProgressPercentFormat(NumberFormat.getPercentInstance());
            prgLoading.setCanceledOnTouchOutside(false);

            prgLoading.show();

            *//*SweetAlertDialog pDialog = new SweetAlertDialog(this.getActivity(), SweetAlertDialog.PROGRESS_TYPE);
            pDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
            pDialog.setTitleText("Loading");
            pDialog.setConfirmText("Tralala");
            pDialog.setCancelable(true);
            pDialog.show();*//*
        }

        getActivity().findViewById(R.id.txtReloadLabel).setVisibility(View.GONE);
        startingTime = SystemClock.currentThreadTimeMillis();

        if (asyncOrNor)
            FetchWeatherTask.runTaskInBackground(this, this.getActivity());
        else
            FetchWeatherTask.runTaskAndWait(this, this.getActivity());*/
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mForecastAdapter = new ForecastAdapter(getActivity(), null, 0);

        /*mForecastAdapter =
                new ArrayAdapter<String>(
                        // The current context (this fragment's parent activity
                        getActivity(),
                        // ID of the list item layout
                        R.layout.list_item_forecast,
                        // ID of the textview to populate
                        R.id.list_item_forecast_textview,
                        // The data
                        new ArrayList<String>());*/

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // Inits the loading spinner at the first time after the activity creates
        loadingSpinner = (ProgressBar)rootView.findViewById(R.id.progressBar1);

        // Restarts the loading spinner
        loadingSpinner.setVisibility(View.VISIBLE);

        mListView = (ListView) rootView.findViewById(R.id.listview_forecast);
        mListView.setAdapter(mForecastAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                Cursor cursor = (Cursor) adapterView.getItemAtPosition(position);

                if (cursor != null) {
                    String locationSetting = Utility.getPreferredLocation(getActivity());
                    Intent intent = new Intent(getActivity(), DetailActivity.class)
                            .setData(WeatherContract.WeatherEntry.buildWeatherLocationWithDate(
                                    locationSetting, cursor.getLong(COL_WEATHER_DATE)
                            ));
                    startActivity(intent);
                }
            }
        });

        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_refresh) {
            this.updateWeather(true, true);
            return true;
        } else if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.action_map) {
            openPreferredLocationInMap();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();

        if ((prgLoading != null) && prgLoading.isShowing())
            prgLoading.dismiss();
        prgLoading = null;
    }

    /**
     * Updates the UI with the asynctask information
     * @param forecast
     */
    public void updateUI(String[] forecast) {

        // Checks if there were any screen oriantation changes
        if (prgLoading != null) {

            // Clears the forecast listview
            mForecastAdapter.clear();
            mForecastAdapter.addAll(Arrays.asList(forecast));

            endTime = SystemClock.currentThreadTimeMillis();
            //Toast.makeText(getActivity(), "Forecast updated! " + (endTime - startingTime) + " millis", Toast.LENGTH_SHORT).show();

            // Diasbles the loading spinner because the loading has finished
            swipeLayout.setRefreshing(false);
            loadingSpinner.setVisibility(View.GONE);
            prgLoading.dismiss();
        }
    }

    private enum loadingWays {
        swipeLayout,
        classicLoadingWindow,
        sweetAlertsWindow
    }

    public void doSomeLoading(loadingWays loadingWay, Boolean loadingOrNot) {
        switch (loadingWay) {
            case swipeLayout :
                if (loadingOrNot)
                    swipeLayout.setRefreshing(true);
                else
                    swipeLayout.setRefreshing(false);
            case classicLoadingWindow :
                if (loadingOrNot)
                    prgLoading.show();
                else
                    prgLoading.cancel();
            default:
                /*
            case(loadingWays.classicLoadingWindow) {

            }
            case(loadingWays.sweetAlertsWindow) {

            }*/
        }

    }

    @Override
    public void onFetchingCancelled(String errorText, Throwable cause) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());

        doSomeLoading(loadingWays.swipeLayout, false);
        //swipeLayout.setRefreshing(false);

        if (loadingSpinner.getVisibility() == View.VISIBLE) {

            loadingSpinner.setVisibility(View.GONE);
            getActivity().findViewById(R.id.txtReloadLabel).setVisibility(View.VISIBLE);

            final FetchWeatherTask.asyncTaskUIMethods callback = this;

            // Turns the loading spinner into a button to reload
            this.getActivity().findViewById(R.id.txtReloadLabel).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadingSpinner.setVisibility(View.VISIBLE);
                    updateWeather(true, false);
                }
            });
        }


        // Checks if the cancel was because the user or nor
        if (errorText != "") {
            builder.setTitle("התרחשה שגיאה")
                    .setMessage(errorText)
                    .setPositiveButton("נסה שנית", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            updateWeather(true, false);
                        }
                    })
                    .setCancelable(true)
                    .show();
        }
    }

    @Override
    public void startingToCheck(String checkName) {

        // Checks if there were any screen oriantation changes
        if (prgLoading != null)
            this.prgLoading.setMessage(checkName);
    }
}

/*ProgressDialog prg = null;

        , Boolean hasToShowLoadingMenu

        // Checks if a loading menu is needed
        if (hasToShowLoadingMenu) {
            prg = new ProgressDialog(this.getActivity(), 0);
            prg.setTitle("Loading");
            prg.setMessage("Retriving weather forecast information from the server...");
            prg.show();
        }

        // Checks if has to cancel the loading dialog
        if (hasToShowLoadingMenu)
            prg.cancel();
*/


    @Override
    public void onSaveInstanceState(Bundle outState) {
        // When tablets rotate, the currently selected list item needs to be saved.
        // When no item is selected, mPosition will be set to Listview.INVALID_POSITION,
        // so check for that before storing.
        if (mPosition != ListView.INVALID_POSITION) {
            outState.putInt(SELECTED_KEY, mPosition);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        // This is called when a new Loader needs to be created.  This
        // fragment only uses one loader, so we don't care about checking the id.

        // To only show current and future dates, filter the query to return weather only for
        // dates after or including today.

        // Sort order:  Ascending, by date.
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";

        String locationSetting = Utility.getPreferredLocation(getActivity());
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                locationSetting, System.currentTimeMillis());

        return new CursorLoader(getActivity(),
                weatherForLocationUri,
                FORECAST_COLUMNS,
                null,
                null,
                sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mForecastAdapter.swapCursor(data);
        if (mPosition != ListView.INVALID_POSITION) {
            // If we don't need to restart the loader, and there's a desired position to restore
            // to, do so now.
            mListView.smoothScrollToPosition(mPosition);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mForecastAdapter.swapCursor(null);
    }

    public void setUseTodayLayout(boolean useTodayLayout) {
        mUseTodayLayout = useTodayLayout;
        if (mForecastAdapter != null) {
            mForecastAdapter.setUseTodayLayout(mUseTodayLayout);
        }
    }

    private void openPreferredLocationInMap() {
        // Using the URI scheme for showing a location found on a map.  This super-handy
        // intent can is detailed in the "Common Intents" page of Android's developer site:
        // http://developer.android.com/guide/components/intents-common.html#Maps
        if ( null != mForecastAdapter ) {
            Cursor c = mForecastAdapter.getCursor();
            if ( null != c ) {
                c.moveToPosition(0);
                String posLat = c.getString(COL_COORD_LAT);
                String posLong = c.getString(COL_COORD_LONG);
                Uri geoLocation = Uri.parse("geo:" + posLat + "," + posLong);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(geoLocation);

                if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Log.d(LOG_TAG, "Couldn't call " + geoLocation.toString() + ", no receiving apps installed!");
                }
            }

        }
    }
