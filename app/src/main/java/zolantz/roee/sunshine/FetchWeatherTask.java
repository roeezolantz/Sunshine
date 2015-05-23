package zolantz.roee.sunshine;

import android.accounts.NetworkErrorException;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.format.Time;
import android.util.Log;
import android.widget.Toast;
import org.apache.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.security.auth.callback.Callback;

/**
 * Created by Roee on 03/05/2015.
 */
public class FetchWeatherTask extends AsyncTask<String, String[], String[]> {

    private final String LOG_TAG = this.getClass().getSimpleName();

    //region Data Membres

    private asyncTaskUIMethods Callback;
    private Context context;

    //endregion

    //region Static code section

    /**
     * This code section prevents other classes to create new FetchWeatherTask every time they want to execute it..
     */
    public static FetchWeatherTask weatherTask;

    public static void runTaskInBackground(asyncTaskUIMethods callback, Context context) {
        weatherTask = new FetchWeatherTask(callback, context);
        weatherTask.getUpdatedWeather(false);
    }

    public static String[] runTaskAndWait(asyncTaskUIMethods callback, Context context) {
        weatherTask = new FetchWeatherTask(callback, context);
        return (weatherTask.getUpdatedWeather(true));
    }

    //endregion

    //region Interfaces

    /**
     * This interface is to help the asynctask call UI functions when its done or being cancelled
     */
    public interface asyncTaskUIMethods {
        void updateUI(String[] results);

        void onFetchingCancelled(String errorText, Throwable cause);
    }

    //endregion

    //region Ctor

    /**
     * Inits the async task with all the needed things
     * @param callback - The callback of the calling class
     * @param context - The context of the calling class
     */
    public FetchWeatherTask(asyncTaskUIMethods callback, Context context) {
        this.context = context;
        this.Callback = callback;
    }

    //endregion

    //region Access Methods (Getters and Setters) of FetchWeatherTask

    /**
     * @returns the calling callback of the asynctask
     */
    public asyncTaskUIMethods getCallback() {
        return this.Callback;
    }

    /**
     * Sets a new callback to the asynctask
     * @param callback - The new callback
     */
    public void setCallback(asyncTaskUIMethods callback) {
        this.Callback = callback;
    }

    /**
     *
     * @return
     */
    public Context getContext() {
        return context;
    }

    /**
     * Sets a new context to the asynctask
     * @param context - The new context
     */
    public void setContext(Context context) {
        this.context = context;
    }

    //endregion

    //region Internet Validation Methods

    /**
     * Checks if the device has an internet connection
     * Does not refers to wifi/3g
     * @returns a boolean
     */
    private Boolean isNetworkAvailable() {

        ConnectivityManager cm = (ConnectivityManager) this.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activityNetwork = cm.getActiveNetworkInfo();

        //boolean isWifi = activityNetwork.getType() == ConnectivityManager.TYPE_WIFI;

        return ((activityNetwork != null) &&
                (activityNetwork.isConnected()));
    }

    /**
     * Checks if the device has access to the internt, after the internet-is-on checking
     * @return
     */
    private String hasInternetAccess() {

        if (this.isNetworkAvailable()) {
            try {
                HttpURLConnection urlc =
                        (HttpURLConnection)(new URL("http://clients3.google.com/generate_204")
                                .openConnection());
                urlc.setRequestProperty("User-Agent", "Android");
                urlc.setRequestProperty("Connection", "close");
                urlc.setConnectTimeout(1500);
                urlc.connect();

                if ((urlc.getResponseCode() != 204) && (urlc.getContentLength() != 0))
                    return ("You are connected to internet connection but has no access.");
            } catch (IOException e) {
                Log.e("Internet_Checking_Tag", "Error checking internet connection", e);
            }
        } else {
            return ("No network available!");
            //Log.d("Internet_Checking_Tag", "No network available!");
        }

        return "";
    }

    /**
     *
     * @returns the error string, "" if all ok
     */
    private String performInternetChecksInAnotherThread() {
        String internetConnectionState = "";

        AsyncTask<Void, String, String> internetConnectionChecker = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] params) {
                String internetConnectionState = hasInternetAccess();

                return (internetConnectionState);
            }
        };

        try {
            Toast.makeText(this.getContext(), "Internet checking started", Toast.LENGTH_SHORT).show();

            internetConnectionState = internetConnectionChecker.execute().get();

            Toast.makeText(this.getContext(), "Internet checking done", Toast.LENGTH_SHORT).show();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return (internetConnectionState);
    }

    //endregion

    /**
     * Returns the weather array
     * @returns an array with all the weekly weather info
     */
    public String[] getUpdatedWeather(Boolean waitForReturnValue) {

        String internetState = "";//performInternetChecksInAnotherThread();

        String[] returnValue = null;

        // Checks if the device has internet
        if (!internetState.equals(""))
            this.getCallback().onFetchingCancelled(internetState, null);
        else {

            // Loads the preferences of the weather info before executing the task
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());

            final String location = prefs.getString(
                    this.getContext().getResources().getString(R.string.pref_location_key, ""),
                    this.getContext().getResources().getString(R.string.pref_location_default, ""));

            // Checks if the user wants to wait for the return value (async/nor)
            if (waitForReturnValue) {
                try {
                    returnValue = this.execute(location).get();
                } catch (Exception ex) {
                    this.getCallback().onFetchingCancelled("Error while fetching the weather task", ex);
                }
            } else {
                this.execute(location);
            }
        }

        return (returnValue);
        //performUpdateInAnotherTask();
    }

    //region Weather Forecast Methods

    private String getWeatherForecastDataFromServer(String postalCode) throws NetworkErrorException {

        // These two need to be declared outside the try/catch
        // so that they can be closed in the finally block.
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        // Will contain the raw JSON response as a string.
        String forecastJsonStr = null;

        String format = "json";
        String units = "metric";
        int numDays = 7;

        try {
            // Construct the URL for the OpenWeatherMap query
            // Possible parameters are available at OWM's forecast API page, at
            // http://openweathermap.org/API#forecast

            final String FORECAST_BASE_URL =
                    "http://api.openweathermap.org/data/2.5/forecast/daily?";
            final String QUERY_PARAM = "q";
            final String FORMAT_PARAM = "mode";
            final String UNITS_PARAM = "units";
            final String DAYS_PARAM = "cnt";

            Uri buildUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                    .appendQueryParameter(QUERY_PARAM, postalCode)
                    .appendQueryParameter(FORMAT_PARAM, format)
                    .appendQueryParameter(UNITS_PARAM, units)
                    .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays)).build();

            URL url = new URL(buildUri.toString());

            // Create the request to OpenWeatherMap, and open the connection
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");

            Integer timeout = urlConnection.getConnectTimeout();
            Log.d(LOG_TAG, "Original URL Timeout: " + timeout.toString());
            urlConnection.setConnectTimeout(10000);
            timeout = urlConnection.getConnectTimeout();
            Log.d(LOG_TAG, "Updated URL Timeout: " + timeout.toString());

            urlConnection.connect();

            int status = urlConnection.getResponseCode();
            if (status >= HttpStatus.SC_BAD_REQUEST) {
                Log.d(LOG_TAG, "HTTP Request didn't return OK. Status "+ Integer.toString(status));
                return null;
            }

            // Read the input stream into a String
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                // Nothing to do.
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line + "\n");
            }

            // Stream was empty.  No point in parsing.
            if (buffer.length() == 0) {
                forecastJsonStr = null;
            }

            forecastJsonStr = buffer.toString();

            // Log.v(LOG_TAG, "Forecast JSON String: " + forecastJsonStr);
        } catch (IOException e) {
            Log.e("PlaceholderFragment", "Error ", e);
            // If the code didn't successfully get the weather data, there's no point in attempting
            // to parse it.
            forecastJsonStr = null;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e("PlaceholderFragment", "Error closing stream", e);
                }
            }
        }

        return (forecastJsonStr);
    }

    /* The date/time conversion code is going to be moved outside the asynctask later,
     * so for convenience we're breaking it out into its own method now.
     */
    private String getReadableDateString(long time){
        // Because the API returns a unix timestamp (measured in seconds),
        // it must be converted to milliseconds in order to be converted to valid date.
        Date date = new Date(time * 1000);
        SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
        return shortenedDateFormat.format(time);
    }

    /**
     * Prepare the weather high/lows for presentation.
     */
    private String formatHighLows(double high, double low, String unitType) {
        // For presentation, assume the user doesn't care about tenths of a degree.

        if (unitType.equals(this.getContext().getResources().getString(R.string.pref_units_imperial))) {
            high = (high * 1.8) + 32;
            low = (low * 1.8) + 32;
        } else if (!unitType.equals(this.getContext().getResources().getString(R.string.pref_units_metric))) {
            Log.d(LOG_TAG, "Unit type not found : " + unitType);
        }

        long roundedHigh = Math.round(high);
        long roundedLow = Math.round(low);

        String highLowStr = roundedHigh + "/" + roundedLow;
        return highLowStr;
    }

    /**
     * Take the String representing the complete forecast in JSON Format and
     * pull out the data we need to construct the Strings needed for the wireframes.
     *
     * Fortunately parsing is easy:  constructor takes the JSON string and converts it
     * into an Object hierarchy for us.
     */
    private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
            throws JSONException {

        if (forecastJsonStr == null)
            return null;

        // These are the names of the JSON objects that need to be extracted.
        final String OWM_LIST = "list";
        final String OWM_WEATHER = "weather";
        final String OWM_TEMPERATURE = "temp";
        final String OWM_MAX = "max";
        final String OWM_MIN = "min";
        final String OWN_DATETIME = "dt";
        final String OWM_DESCRIPTION = "main";

        JSONObject forecastJson = new JSONObject(forecastJsonStr);
        JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

        // OWM returns daily forecasts based upon the local time of the city that is being
        // asked for, which means that we need to know the GMT offset to translate this data
        // properly.

        // Since this data is also sent in-order and the first day is always the
        // current day, we're going to take advantage of that to get a nice
        // normalized UTC date for all of our weather.

        Time dayTime = new Time();
        dayTime.setToNow();

        // we start at the day returned by local time. Otherwise this is a mess.
        int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

        // now we work exclusively in UTC
        dayTime = new Time();

        String[] resultStrs = new String[numDays];

        SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(this.getContext());
        String unitType = "metric";

        //String unitType = sharedPrefs.getString(
        //        getString(R.string.pref_units_key, ""),
        //        getString(R.string.pref_units_default, ""));

        for(int i = 0; i < weatherArray.length(); i++) {
            // For now, using the format "Day, description, hi/low"
            String day;
            String description;
            String highAndLow;

            // Get the JSON object representing the day
            JSONObject dayForecast = weatherArray.getJSONObject(i);

            // The date/time is returned as a long.  We need to convert that
            // into something human-readable, since most people won't read "1400356800" as
            // "this saturday".
            long dateTime;
            // Cheating to convert this to UTC time, which is what we want anyhow
            dateTime = dayTime.setJulianDay(julianStartDay+i);
            day = getReadableDateString(dateTime);

            // description is in a child array called "weather", which is 1 element long.
            JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
            description = weatherObject.getString(OWM_DESCRIPTION);

            // Temperatures are in a child object called "temp".  Try not to name variables
            // "temp" when working with temperature.  It confuses everybody.
            JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
            double high = temperatureObject.getDouble(OWM_MAX);
            double low = temperatureObject.getDouble(OWM_MIN);

            highAndLow = formatHighLows(high, low, unitType);
            resultStrs[i] = day + " - " + description + " - " + highAndLow;
        }

        return resultStrs;
    }

    //endregion

    /**
     * Function that does the update, I took this out to this func to do a nice try catch system on all of this
     */
    private void performUpdateInAnotherTask() {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());

        final String location = prefs.getString(
                this.getContext().getResources().getString(R.string.pref_location_key, ""),
                this.getContext().getResources().getString(R.string.pref_location_default, ""));

        this.execute(location);
    }

    @Override
    protected String[] doInBackground(String... params) {

        String[] weatherForecastArray = null;
        String weatherForecast = null;

        try {
            // Should throw NetworkErrorException if there is no internet access

            Thread.sleep(1000);
            if (!isCancelled())
                weatherForecast = getWeatherForecastDataFromServer(params[0]);
            Thread.sleep(1000);

            if (!isCancelled())
                weatherForecastArray = getWeatherDataFromJson(weatherForecast, 7);
        } catch (NetworkErrorException e) {
            e.printStackTrace();
            // TODO : has to throw this exception to the fragment so he will know that there is no internet
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return (weatherForecastArray);
    }

    @Override
    protected void onPostExecute(String[] result) {

        if (result != null) {
            this.getCallback().updateUI(result);
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        this.getCallback().onFetchingCancelled("", null);
    }
}