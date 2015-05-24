package zolantz.roee.sunshine;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;

import javax.security.auth.callback.Callback;

import static android.view.View.OnClickListener;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment implements FetchWeatherTask.asyncTaskUIMethods {

    private static final String LOG_TAG = ForecastFragment.class.getSimpleName();

    private ArrayAdapter<String> mForecastAdapter;
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

        mForecastAdapter =
                new ArrayAdapter<String>(
                        // The current context (this fragment's parent activity
                        getActivity(),
                        // ID of the list item layout
                        R.layout.list_item_forecast,
                        // ID of the textview to populate
                        R.id.list_item_forecast_textview,
                        // The data
                        new ArrayList<String>());

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // Inits the loading spinner at the first time after the activity creates
        loadingSpinner = (ProgressBar)rootView.findViewById(R.id.progressBar1);

        // Restarts the loading spinner
        loadingSpinner.setVisibility(View.VISIBLE);

        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String forecast = mForecastAdapter.getItem(position);
                Intent intent = new Intent(getActivity(), DetailsActivity.class)
                        .putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(intent);
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
        }

        return super.onOptionsItemSelected(item);
    }

    public void updateUI(String[] forecast) {

        // Diasbles the loading spinner because the loading has finished
        loadingSpinner.setVisibility(View.GONE);
        prgLoading.cancel();

        // Clears the forecast listview
        mForecastAdapter.clear();
        mForecastAdapter.addAll(Arrays.asList(forecast));

        endTime = SystemClock.currentThreadTimeMillis();
        Toast.makeText(getActivity(), "Forecast updated! " + (endTime - startingTime) + " millis", Toast.LENGTH_SHORT).show();

        swipeLayout.setRefreshing(false);
    }

    @Override
    public void onFetchingCancelled(String errorText, Throwable cause) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this.getActivity());

        swipeLayout.setRefreshing(false);

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