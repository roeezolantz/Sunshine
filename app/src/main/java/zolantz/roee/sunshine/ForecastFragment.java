package zolantz.roee.sunshine;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.text.NumberFormat;

import zolantz.roee.sunshine.data.WeatherContract;

import static android.view.View.OnClickListener;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment implements FetchWeatherTask.asyncTaskUIMethods, LoaderManager.LoaderCallbacks<Cursor> {

    private static final String LOG_TAG = ForecastFragment.class.getSimpleName();

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

    private ForecastAdapter mForecastAdapter;
    //private ArrayAdapter<String> mForecastAdapter;
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

        // Creates a new async task because every async task can be invoked once.
        /*weatherMachine = new FetchWeatherTask(this, getActivity());
        weatherMachine.getUpdatedWeather();
        */

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

            /*SweetAlertDialog pDialog = new SweetAlertDialog(this.getActivity(), SweetAlertDialog.PROGRESS_TYPE);
            pDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
            pDialog.setTitleText("Loading");
            pDialog.setConfirmText("Tralala");
            pDialog.setCancelable(true);
            pDialog.show();*/
        }

        getActivity().findViewById(R.id.txtReloadLabel).setVisibility(View.GONE);
        startingTime = SystemClock.currentThreadTimeMillis();

        if (asyncOrNor)
            FetchWeatherTask.runTaskInBackground(this, this.getActivity());
        else
            FetchWeatherTask.runTaskAndWait(this, this.getActivity());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
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

        // The CursorAdapter will take data from our cursor and populate the ListView.
        mForecastAdapter = new ForecastAdapter(getActivity(), null, 0);

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // Inits the loading spinner at the first time after the activity creates
        loadingSpinner = (ProgressBar)rootView.findViewById(R.id.progressBar1);

        // Restarts the loading spinner
        loadingSpinner.setVisibility(View.VISIBLE);

        final ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);

        // We'll call our MainActivity
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                // CursorAdapter returns a cursor at the correct position for getItem(), or null
                // if it cannot seek to that position.
                Cursor cursor = (Cursor) adapterView.getItemAtPosition(position);
                if (cursor != null) {
                    String locationSetting = Utility.getPreferredLocation(getActivity());
                    Intent intent = new Intent(getActivity(), DetailsActivity.class)
                            .setData(WeatherContract.WeatherEntry.buildWeatherLocationWithDate(
                                    locationSetting, cursor.getLong(COL_WEATHER_DATE)
                            ));
                    startActivity(intent);
                }
            }
        });
        /*listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String forecast = mForecastAdapter.getItem(position);
                Intent intent = new Intent(getActivity(), DetailsActivity.class)
                        .putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(intent);
            }
        });
*/
        //TODO : Buggy!@#!@#@#$!%@#$%@$#%

       /* listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                int topRowVerticalPosition = (listView == null || listView.getChildCount() == 0) ?
                        0 : listView.getChildAt(0).getTop();
                swipeLayout.setEnabled((topRowVerticalPosition >= 0));
            }
        });*/

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

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        getLoaderManager().initLoader(FORECAST_LOADER, null, this);
        super.onActivityCreated(savedInstanceState);
    }

    // since we read the location when we create the loader, all we need to do is restart things
    void onLocationChanged( ) {
        // TODO : needed?
        updateWeather(true, true);
        getLoaderManager().restartLoader(FORECAST_LOADER, null, this);
    }

    /**
     * Updates the UI with the asynctask information
     * @param forecast
     */
    public void updateUI(String[] forecast) {

        // Checks if there were any screen oriantation changes
        if (prgLoading != null) {

            // Clears the forecast listview
/*
            mForecastAdapter.clear();
            mForecastAdapter.addAll(Arrays.asList(forecast));
*/

            endTime = SystemClock.currentThreadTimeMillis();
            //Toast.makeText(getActivity(), "Forecast updated! " + (endTime - startingTime) + " millis", Toast.LENGTH_SHORT).show();

            // Diasbles the loading spinner because the loading has finished
            swipeLayout.setRefreshing(false);
            loadingSpinner.setVisibility(View.GONE);
            prgLoading.dismiss();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String locationSetting = Utility.getPreferredLocation(getActivity());

        // Sort order:  Ascending, by date.
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";
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
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        mForecastAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mForecastAdapter.swapCursor(null);
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