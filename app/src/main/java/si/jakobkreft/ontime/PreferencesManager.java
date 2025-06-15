package si.jakobkreft.ontime;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PreferencesManager {
    private static final String PREFS = "MultiTimerPrefs";
    private static final String KEY  = "timers";

    public static List<TimerModel> loadTimers(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = p.getString(KEY, null);
        if (json == null) {
            List<TimerModel> def = new ArrayList<>();
            def.add(new TimerModel());
            return def;
        }
        Type listType = new TypeToken<List<TimerModel>>(){}.getType();
        return new Gson().fromJson(json, listType);
    }

    public static void saveTimers(Context ctx, List<TimerModel> timers) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = new Gson().toJson(timers);
        p.edit().putString(KEY, json).apply();
    }
}
