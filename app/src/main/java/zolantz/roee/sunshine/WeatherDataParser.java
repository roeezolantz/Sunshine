package zolantz.roee.sunshine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by DINA on 30/03/2015.
 */
public class WeatherDataParser {

    public static double getMaxTemperatureForDay(String weatherJsonStr, int dayIndex)
        throws JSONException {
        JSONObject weather = new JSONObject(weatherJsonStr);
        JSONArray days = weather.getJSONArray("list");
        JSONObject dayInfo = days.getJSONObject(dayIndex);
        JSONObject temperatureInfo = dayInfo.getJSONObject("temp");

        return (temperatureInfo.getDouble("max"));
    }
}
