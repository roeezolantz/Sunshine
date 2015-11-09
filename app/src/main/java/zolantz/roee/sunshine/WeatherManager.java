package zolantz.roee.sunshine;

import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;

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
import java.util.Date;
import java.util.concurrent.Callable;

/**
 * Created by Roee on 03/05/2015.
 */
public class WeatherManager {

    private static Context context;
    private final String LOG_TAG = this.getClass().getSimpleName();

    public WeatherManager(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * Checks if the device has an internet connection
     * Does not refers to wifi/3g
     * @returns a boolean
     */
    public static boolean isNetworkAvailable() {

        ConnectivityManager cm = (ConnectivityManager) WeatherManager.context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activityNetwork = cm.getActiveNetworkInfo();

        //boolean isWifi = activityNetwork.getType() == ConnectivityManager.TYPE_WIFI;

        return ((activityNetwork != null) &&
                (activityNetwork.isConnected()));
    }

    /**
     * Checks if the device has access to the internt, after the internet-is-on checking
     * @return
     */
    public static boolean hasInternetAccess() {

        if (isNetworkAvailable()) {
            try {
                HttpURLConnection urlc =
                        (HttpURLConnection)(new URL("http://clients3.google.com/generate_204")
                                .openConnection());
                urlc.setRequestProperty("User-Agent", "Android");
                urlc.setRequestProperty("Connection", "close");
                urlc.setConnectTimeout(1500);
                urlc.connect();

                return ((urlc.getResponseCode() == 204) && (urlc.getContentLength() == 0));
            } catch (IOException e) {
                Log.e("Internet_Checking_Tak", "Error checking internet connection", e);
            }
        } else {
            Log.d("Internet_Checking_Tak", "No network available!");
        }

        return false;
    }

    /**
     * Returns the weather array
     * @return
     */
    public String[] getUpdatedWeather(classWithUIupdates inter) {

        String[] retVal = null;

        // Checks if the phone has internet
        /*if (!WeatherManager.checkInternetConnection()) {

            AlertDialog.Builder builder = new AlertDialog.Builder(WeatherManager.context);

            builder.setTitle("שגיאה בקבלת הנתונים מהשרת")
                    .setMessage("לא זוהתה רשת. אנא התחבר לרשת ורענן את האפליקציה")
                    .setPositiveButton("נסה שנית", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    })
                    .setCancelable(true)
                    .show();
        } else {
        */    retVal = performUpdateInAnotherTask(inter);
        //}

        return (retVal);
    }

    /**
     * Function that does the update, I took this out to this func to do a nice try catch system on all of this
     */
    private String[] performUpdateInAnotherTask(classWithUIupdates inter) {

        final FetchWeatherTask weatherTask = new FetchWeatherTask(inter);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());

        final String location = prefs.getString(
                this.getContext().getResources().getString(R.string.pref_location_key, ""),
                this.getContext().getResources().getString(R.string.pref_location_default, ""));

        String[] retValue = null;

        weatherTask.execute(location);
/*

        try {
            retValue = weatherTask.execute(location).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
*/

        return retValue;
    }

    private String getWeatherForecastDataFromServer(String postalCode) throws NetworkErrorException {

        // Checks if the device has internet
        if (!WeatherManager.hasInternetAccess()) {
            throw new NetworkErrorException("No Internet access or connection!");
        }

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

    public class FetchWeatherTask extends AsyncTask<String, String[], String[]> {
        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        // This is a callable functino to perform updates in the ui after the asynctask dones. like an event in the fragment
        private Callable<Void> funcFromUItoFetch;

        private classWithUIupdates myRefreshInter;

        public FetchWeatherTask(classWithUIupdates inter) {
            myRefreshInter = inter;
        }
        public FetchWeatherTask(Callable<Void> func) {
            funcFromUItoFetch = func;
        }

        @Override
        protected String[] doInBackground(String... params) {

            String[] weatherForecastArray = null;

            if (params.length == 0) {
            }

            String weatherForecast = null;

            try {
                // Should throw NetworkErrorException if there is no internet access
                weatherForecast = getWeatherForecastDataFromServer(params[0]);

                weatherForecastArray = getWeatherDataFromJson(weatherForecast, 7);
            } catch (NetworkErrorException e) {
                e.printStackTrace();
                // TODO : has to throw this exception to the fragment so he will know that there is no internet
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return (weatherForecastArray);
        }

        @Override
        protected void onPostExecute(String[] result) {
            if (result != null) {

                myRefreshInter.updateUI(result);

                /*if (funcFromUItoFetch != null) {
                    try {
                        funcFromUItoFetch.call();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }*/
                //Toast.makeText(WeatherManager.context, "Forecast updated!", Toast.LENGTH_SHORT).show();

                //swipeLayout = (SwipeRefreshLayout) ((Activity)WeatherManager.context).findViewById(R.id.swipe_container);
                //swipeLayout.setRefreshing(false);

                //for (String dayForecastStr : result) {
                //    mForecastAdapter.add(dayForecastStr);
                //}
            }
        }
    }
}
