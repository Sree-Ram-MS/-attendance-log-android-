package ms.sreeram.attendance;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.Locale;

final class Store {
    static SharedPreferences p(Context c) { return c.getSharedPreferences("att", 0); }
    static JSONObject days(Context c) { try { return new JSONObject(p(c).getString("days", "{}")); } catch (Exception e) { return new JSONObject(); } }
    static JSONObject set(Context c) { try { return new JSONObject(p(c).getString("set", "{}")); } catch (Exception e) { return new JSONObject(); } }

    static String key(Calendar k) {
        return String.format(Locale.US, "%04d-%02d-%02d", k.get(Calendar.YEAR), k.get(Calendar.MONTH) + 1, k.get(Calendar.DAY_OF_MONTH));
    }
    static boolean isOff(JSONObject set, Calendar k) {
        JSONArray w = set.optJSONArray("weekoff");
        int dow = k.get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday
        if (w == null) { if (dow == 0) return true; }
        else for (int i = 0; i < w.length(); i++) if (w.optInt(i, -1) == dow) return true;
        JSONObject h = set.optJSONObject("holidays");
        return h != null && h.has(key(k));
    }
    static boolean isHoliday(JSONObject set, Calendar k) {
        JSONObject h = set.optJSONObject("holidays"); return h != null && h.has(key(k));
    }
    static boolean has(JSONObject day, String t) {
        if (day == null) return false;
        JSONArray a = day.optJSONArray("t");
        if (a != null) for (int i = 0; i < a.length(); i++) if (t.equals(a.optString(i))) return true;
        return false;
    }
    static String dur(int m) {
        int h = m / 60, r = m % 60;
        return h > 0 ? (r > 0 ? h + "h " + r + "m" : h + "h") : r + "m";
    }
    static String num(double d) { return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d); }

    /** Summary over [from, from+n) days: leave days, late count, late mins, ot mins */
    static double[] sum(JSONObject days, Calendar from, int n) {
        double lv = 0, lc = 0, lm = 0, om = 0;
        Calendar k = (Calendar) from.clone();
        for (int i = 0; i < n; i++) {
            JSONObject d = days.optJSONObject(key(k));
            if (d != null) {
                lv += d.optDouble("lv", 0);
                if (has(d, "late")) { lc++; lm += d.optInt("lm", 0); }
                om += d.optInt("om", 0);
            }
            k.add(Calendar.DAY_OF_MONTH, 1);
        }
        return new double[]{lv, lc, lm, om};
    }
    static String summary(double[] s, String empty) {
        StringBuilder b = new StringBuilder();
        if (s[0] > 0) b.append(num(s[0])).append(s[0] == 1 ? " leave day" : " leave days");
        if (s[1] > 0) { if (b.length() > 0) b.append(" · "); b.append((int) s[1]).append(" late (").append(dur((int) s[2])).append(")"); }
        if (s[3] > 0) { if (b.length() > 0) b.append(" · "); b.append(dur((int) s[3])).append(" OT"); }
        return b.length() > 0 ? b.toString() : empty;
    }
}
