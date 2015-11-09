package zolantz.roee.sunshine;

import android.accounts.NetworkErrorException;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
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
import java.util.Date;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import zolantz.roee.sunshine.data.WeatherContract;
import zolantz.roee.sunshine.data.WeatherContract.*;

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

        void startingToCheck(String checkName);
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

                publishProgress(new String[] {"Checking internet connection"});
                String internetConnectionState = hasInternetAccess();

                return (internetConnectionState);
            }

            @Override
            protected void onProgressUpdate(Object... values) {
                getCallback().startingToCheck(values[0].toString());
            }
        };

        try {
            internetConnectionState = internetConnectionChecker.execute().get();
            //Toast.makeText(this.getContext(), "Internet checking done", Toast.LENGTH_SHORT).show();

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

        //this.getCallback().startingToCheck("Internet Connection varification");

        String internetState = "";
        //performInternetChecksInAnotherThread();

        String[] returnValue = null;

        // Checks if the device has internet
        if (!internetState.equals(""))
            this.getCallback().onFetchingCancelled(internetState, null);
        else {

            // Loads the preferences of the weather info before executing the task
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());

            Boolean byList = prefs.getBoolean("countingWay", true);

            final String location = prefs.getString(
                    this.getContext().getResources().getString(R.string.pref_location_key, ""),
                    this.getContext().getResources().getString(R.string.pref_location_default, ""));

            String key = this.getContext().getResources().getString(R.string.pref_count_key, "");
            String count = this.getContext().getResources().getString(R.string.pref_count_default, "7");

            if (!byList)
                key = "count";

            final String numOfDays = prefs.getString(key, count);

            // Checks if the user wants to wait for the return value (async/nor)
            if (waitForReturnValue) {
                try {
                    returnValue = this.execute(location, numOfDays).get();
                } catch (Exception ex) {
                    this.getCallback().onFetchingCancelled("Error while fetching the weather task", ex);
                }
            } else {
                this.execute(location, numOfDays);
            }
        }

        return (returnValue);
        //performUpdateInAnotherTask();
    }

    //region Weather Forecast Methods

    private String getWeatherForecastDataFromServer(String postalCode, int numOfDays) throws NetworkErrorException {

        // These two need to be declared outside the try/catch
        // so that they can be closed in the finally block.
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        // Will contain the raw JSON response as a string.
        String forecastJsonStr = null;

        String format = "json";
        String units = "metric";
        //int numDays = numOfDays;//14;

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
            final String APPID_PARAM = "APPID";

            Uri buildUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                    .appendQueryParameter(QUERY_PARAM, postalCode)
                    .appendQueryParameter(FORMAT_PARAM, format)
                    .appendQueryParameter(UNITS_PARAM, units)
                    .appendQueryParameter(DAYS_PARAM, Integer.toString(numOfDays))
                    .appendQueryParameter(APPID_PARAM, BuildConfig.OPEN_WEATHER_MAP_API_KEY)
                    .build();

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
        /*Date date = new Date(time * 1000);
        SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
        return shortenedDateFormat.format(time);*/

        Date date = new Date(time);
        SimpleDateFormat format = new SimpleDateFormat("E, MMM d");
        return format.format(date).toString();
    }

    /**
     * Prepare the weather high/lows for presentation.
     */
    private String formatHighLows(double high, double low) {
        // For presentation, assume the user doesn't care about tenths of a degree.

        SharedPreferences sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(this.getContext());

        String unitType = sharedPrefs.getString(
                this.getContext().getString(R.string.pref_units_key),
                this.getContext().getString(R.string.pref_units_metric));

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

    //region NEW DB METHODS

    /**
     * Helper method to handle insertion of a new location in the weather database.
     *
     * @param locationSetting The location string used to request updates from the server.
     * @param cityName A human-readable city name, e.g "Mountain View"
     * @param lat the latitude of the city
     * @param lon the longitude of the city
     * @return the row ID of the added location.
     */
    long addLocation(String locationSetting, String cityName, double lat, double lon) {
        long locationId;

        // First, check if the location with this city name exists in the db
        Cursor locationCursor = this.getContext().getContentResolver().query(
                WeatherContract.LocationEntry.CONTENT_URI,
                new String[]{WeatherContract.LocationEntry._ID},
                WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING + " = ?",
                new String[]{locationSetting},
                null);

        if (locationCursor.moveToFirst()) {
            int locationIdIndex = locationCursor.getColumnIndex(WeatherContract.LocationEntry._ID);
            locationId = locationCursor.getLong(locationIdIndex);
        } else {
            // Now that the content provider is set up, inserting rows of data is pretty simple.
            // First create a ContentValues object to hold the data you want to insert.
            ContentValues locationValues = new ContentValues();

            // Then add the data, along with the corresponding name of the data type,
            // so the content provider knows what kind of value is being inserted.
            locationValues.put(WeatherContract.LocationEntry.COLUMN_CITY_NAME, cityName);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING, locationSetting);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LAT, lat);
            locationValues.put(WeatherContract.LocationEntry.COLUMN_COORD_LONG, lon);

            // Finally, insert location data into the database.
            Uri insertedUri = this.getContext().getContentResolver().insert(
                    LocationEntry.CONTENT_URI,
                    locationValues
            );

            // The resulting URI contains the ID for the row.  Extract the locationId from the Uri.
            locationId = ContentUris.parseId(insertedUri);
        }

        locationCursor.close();
        // Wait, that worked?  Yes!
        return locationId;
    }

    /*
        Students: This code will allow the FetchWeatherTask to continue to return the strings that
        the UX expects so that we can continue to test the application even once we begin using
        the database.
     */
    String[] convertContentValuesToUXFormat(Vector<ContentValues> cvv) {
        // return strings to keep UI functional for now
        String[] resultStrs = new String[cvv.size()];

        for ( int i = 0; i < cvv.size(); i++ ) {

            ContentValues weatherValues = cvv.elementAt(i);
            String highAndLow = formatHighLows(
                    weatherValues.getAsDouble(WeatherEntry.COLUMN_MAX_TEMP),
                    weatherValues.getAsDouble(WeatherEntry.COLUMN_MIN_TEMP));

            resultStrs[i] = getReadableDateString(
                    weatherValues.getAsLong(WeatherEntry.COLUMN_DATE)) +
                    " - " + weatherValues.getAsString(WeatherEntry.COLUMN_SHORT_DESC) +
                    " - " + highAndLow;
        }

        return resultStrs;
    }

    //endregion NEW DB METHODS

    /**
     * Take the String representing the complete forecast in JSON Format and
     * pull out the data we need to construct the Strings needed for the wireframes.
     *
     * Fortunately parsing is easy:  constructor takes the JSON string and converts it
     * into an Object hierarchy for us.
     */
    private String[] getWeatherDataFromJson(String forecastJsonStr, String locationSetting)//int numDays)
            throws JSONException {

        if (forecastJsonStr == null)
            return null;

        // These are the names of the JSON objects that need to be extracted.

        // Location information
        final String OWM_CITY = "city";
        final String OWM_CITY_NAME = "name";
        final String OWM_COORD = "coord";

        // Location coordinate
        final String OWM_LATITUDE = "lat";
        final String OWM_LONGITUDE = "lon";

        // Weather information.  Each day's forecast info is an element of the "list" array.
        final String OWM_LIST = "list";

        final String OWM_PRESSURE = "pressure";
        final String OWM_HUMIDITY = "humidity";
        final String OWM_WINDSPEED = "speed";
        final String OWM_WIND_DIRECTION = "deg";

        // All temperatures are children of the "temp" object.
        final String OWM_TEMPERATURE = "temp";
        final String OWM_MAX = "max";
        final String OWM_MIN = "min";

        final String OWN_DATETIME = "dt";
        final String OWM_WEATHER = "weather";
        final String OWM_DESCRIPTION = "main";
        final String OWM_WEATHER_ID = "id";


        /*JSONObject forecastJson = new JSONObject(forecastJsonStr);
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

            highAndLow = formatHighLows(high, low);
            resultStrs[i] = day + " - " + description + " - " + highAndLow;
        }

        return resultStrs;*/

        try {
            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            JSONObject cityJson = forecastJson.getJSONObject(OWM_CITY);
            String cityName = cityJson.getString(OWM_CITY_NAME);

            JSONObject cityCoord = cityJson.getJSONObject(OWM_COORD);
            double cityLatitude = cityCoord.getDouble(OWM_LATITUDE);
            double cityLongitude = cityCoord.getDouble(OWM_LONGITUDE);

            long locationId = addLocation(locationSetting, cityName, cityLatitude, cityLongitude);

            // Insert the new weather information into the database
            Vector<ContentValues> cVVector = new Vector<ContentValues>(weatherArray.length());

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

            for(int i = 0; i < weatherArray.length(); i++) {

                publishProgress(new String[] {"Parsing day " + (i + 1) + " of " + weatherArray.length()});

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // These are the values that will be collected.
                long dateTime;
                double pressure;
                int humidity;
                double windSpeed;
                double windDirection;

                double high;
                double low;

                String description;
                int weatherId;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay+i);

                pressure = dayForecast.getDouble(OWM_PRESSURE);
                humidity = dayForecast.getInt(OWM_HUMIDITY);
                windSpeed = dayForecast.getDouble(OWM_WINDSPEED);
                windDirection = dayForecast.getDouble(OWM_WIND_DIRECTION);

                // Description is in a child array called "weather", which is 1 element long.
                // That element also contains a weather code.
                JSONObject weatherObject =
                        dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);
                weatherId = weatherObject.getInt(OWM_WEATHER_ID);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                high = temperatureObject.getDouble(OWM_MAX);
                low = temperatureObject.getDouble(OWM_MIN);

                ContentValues weatherValues = new ContentValues();

                weatherValues.put(WeatherEntry.COLUMN_LOC_KEY, locationId);
                weatherValues.put(WeatherEntry.COLUMN_DATE, dateTime);
                weatherValues.put(WeatherEntry.COLUMN_HUMIDITY, humidity);
                weatherValues.put(WeatherEntry.COLUMN_PRESSURE, pressure);
                weatherValues.put(WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
                weatherValues.put(WeatherEntry.COLUMN_DEGREES, windDirection);
                weatherValues.put(WeatherEntry.COLUMN_MAX_TEMP, high);
                weatherValues.put(WeatherEntry.COLUMN_MIN_TEMP, low);
                weatherValues.put(WeatherEntry.COLUMN_SHORT_DESC, description);
                weatherValues.put(WeatherEntry.COLUMN_WEATHER_ID, weatherId);

                cVVector.add(weatherValues);
            }

            publishProgress(new String[] {"Saving the weather info to the device's DB..."});

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            int inserted = 0;

            // add to database
            if (cVVector.size() > 0) {
                // Student: call bulkInsert to add the weatherEntries to the database here
                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);
                inserted = this.getContext().getContentResolver().bulkInsert(WeatherEntry.CONTENT_URI, cvArray);
            }

            // Sort order:  Ascending, by date.
            String sortOrder = WeatherEntry.COLUMN_DATE + " ASC";
            Uri weatherForLocationUri = WeatherEntry.buildWeatherLocationWithStartDate(
                    locationSetting, System.currentTimeMillis());

            // Students: Uncomment the next lines to display what what you stored in the bulkInsert

//            // Brings the forecast list from the DB after the insertion
//            // TODO : Buggy code, this returns 12 objects instead of the wanted amount
//            Cursor cur = this.getContext().getContentResolver().query(weatherForLocationUri,
//                    null, null, null, sortOrder);
//
//            cVVector = new Vector<ContentValues>(Math.min(cur.getCount(), forecastJsonStr.split("day").length - 1));
//
//            if (cur.moveToFirst()) {
//                do {
//                    ContentValues cv = new ContentValues();
//                    DatabaseUtils.cursorRowToContentValues(cur, cv);
//                    cVVector.add(cv);
//                } while (cur.moveToNext()); //&& cVVector.capacity() <= Json);
//            }

            Log.d(LOG_TAG, "FetchWeatherTask Complete. " + cVVector.size() + " Inserted");

            String[] resultStrs = convertContentValuesToUXFormat(cVVector);
            return resultStrs;

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        }

        return null;
    }

    //endregion

    /**
     * Function that does the update, I took this out to this func to do a nice try catch system on all of this
     */
    /*private void performUpdateInAnotherTask() {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());

        final String location = prefs.getString(
                this.getContext().getResources().getString(R.string.pref_location_key, ""),
                this.getContext().getResources().getString(R.string.pref_location_default, ""));

        this.execute(location);
    }*/

    protected String[] doInBackground(String... params) {

        String[] weatherForecastArray = null;
        String weatherForecast = null;
        String locationQuery = params[0];

        try {
            // Should throw NetworkErrorException if there is no internet access
            publishProgress(new String[] {"Retriving data from the server"});
            Thread.sleep(300);
            if (!isCancelled())
                weatherForecast = getWeatherForecastDataFromServer(params[0], Integer.parseInt(params[1]));

            if (weatherForecast == null)
                this.getCallback().onFetchingCancelled("Oops.. Couldn't get the weather forecast from the server.", null);
            else {
                publishProgress(new String[]{"Parsing weather data"});
                //Thread.sleep(300);

                if (!isCancelled())
                    weatherForecastArray = getWeatherDataFromJson(weatherForecast, locationQuery);
            }
        } catch (NetworkErrorException e) {
            this.getCallback().onFetchingCancelled(e.getMessage(), e.getCause());
            // TODO : has to throw this exception to the fragment so he will know that there is no internet
        } catch (JSONException e) {
            this.getCallback().onFetchingCancelled(e.getMessage(), e.getCause());
            e.printStackTrace();
        } catch (InterruptedException e) {
            this.getCallback().onFetchingCancelled(e.getMessage(), e.getCause());
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

    @Override
    protected void onProgressUpdate(String[]... values) {
        this.getCallback().startingToCheck(values[0][0]);
    }
}