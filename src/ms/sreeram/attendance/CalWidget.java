package ms.sreeram.attendance;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class CalWidget extends AppWidgetProvider {
    static final String PREV = "ms.sreeram.attendance.W_PREV", NEXT = "ms.sreeram.attendance.W_NEXT", TODAY = "ms.sreeram.attendance.W_TODAY";

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { for (int id : ids) draw(c, m, id); Alarm.schedule(c); }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) { draw(c, m, id); }

    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (PREV.equals(a) || NEXT.equals(a) || TODAY.equals(a)) {
            int off = Store.p(c).getInt("woff", 0);
            off = TODAY.equals(a) ? 0 : off + (PREV.equals(a) ? -1 : 1);
            Store.p(c).edit().putInt("woff", off).apply();
            refreshAll(c);
            return;
        }
        super.onReceive(c, i);
    }

    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, CalWidget.class));
        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) == 0) Store.p(c).edit().putInt("woff", 0).apply();
        for (int id : ids) draw(c, m, id);
    }

    static PendingIntent self(Context c, String action, int id) {
        Intent i = new Intent(c, CalWidget.class).setAction(action).setData(Uri.parse("att://w/" + id + "/" + action));
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void draw(Context c, AppWidgetManager m, int id) {
        JSONObject days = Store.days(c), set = Store.set(c);
        int off = Store.p(c).getInt("woff", 0);
        Calendar today = Calendar.getInstance();
        Calendar mo = (Calendar) today.clone();
        mo.set(Calendar.DAY_OF_MONTH, 1); mo.add(Calendar.MONTH, off);

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setTextViewText(R.id.title, new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(mo.getTime()) + (off != 0 ? "  ⟲" : ""));
        int n = mo.getActualMaximum(Calendar.DAY_OF_MONTH);
        v.setTextViewText(R.id.stats, Store.summary(Store.sum(days, mo, n), "Nothing logged this month"));

        Bundle o = m.getAppWidgetOptions(id);
        float dens = c.getResources().getDisplayMetrics().density;
        int wdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
        int hdp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 250);
        if (wdp <= 0) wdp = 250; if (hdp <= 0) hdp = 250;
        boolean showBtns = hdp >= 200;
        v.setViewVisibility(R.id.btns, showBtns ? android.view.View.VISIBLE : android.view.View.GONE);
        int gw = Math.max(120, wdp - 20), gh = Math.max(90, hdp - 20 - 44 - (showBtns ? 38 : 0));
        float sc = Math.min(dens, 2.5f);
        v.setImageViewBitmap(R.id.grid, grid(c, days, set, mo, today, (int) (gw * sc), (int) (gh * sc), sc));

        v.setOnClickPendingIntent(R.id.prev, self(c, PREV, id));
        v.setOnClickPendingIntent(R.id.next, self(c, NEXT, id));
        v.setOnClickPendingIntent(R.id.head, off != 0 ? self(c, TODAY, id) : Alarm.open(c, null, 300 + id));
        v.setOnClickPendingIntent(R.id.grid, Alarm.open(c, null, 400 + id));
        v.setOnClickPendingIntent(R.id.bLeave, Alarm.open(c, "leave", 500 + id * 3));
        v.setOnClickPendingIntent(R.id.bLate, Alarm.open(c, "late", 501 + id * 3));
        v.setOnClickPendingIntent(R.id.bOt, Alarm.open(c, "ot", 502 + id * 3));
        m.updateAppWidget(id, v);
    }

    static Bitmap grid(Context c, JSONObject days, JSONObject set, Calendar mo, Calendar today, int W, int H, float d) {
        boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int ink = night ? 0xFFE8EEF3 : 0xFF12202B, muted = night ? 0xFF93A5B4 : 0xFF56677A, bg = night ? 0xFF16212A : 0xFFFFFFFF;
        int lateC = night ? 0xFFF2A93F : 0xFFA55D00;
        int[] tint = night ? new int[]{0xFF12363B, 0xFF3A2A0F, 0xFF272249} : new int[]{0xFFBFE6EA, 0xFFF9D9A2, 0xFFD3CDF7};
        int[] edge = night ? new int[]{0xFF52C6CF, 0xFFF2A93F, 0xFFA99CFF} : new int[]{0xFF0D7A84, 0xFFA55D00, 0xFF5848C2};
        String[] types = {"leave", "late", "ot"};

        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextAlign(Paint.Align.CENTER);

        int first = mo.get(Calendar.DAY_OF_WEEK) - 1, n = mo.getActualMaximum(Calendar.DAY_OF_MONTH);
        int rows = (first + n + 6) / 7;
        float dowH = 16 * d, cw = W / 7f, ch = (H - dowH) / rows;
        String[] dw = {"S", "M", "T", "W", "T", "F", "S"};
        p.setTextSize(10.5f * d); p.setFakeBoldText(true);
        for (int i = 0; i < 7; i++) { p.setColor(i == 0 ? lateC : muted); cv.drawText(dw[i], cw * i + cw / 2, 11.5f * d, p); }
        p.setFakeBoldText(false);

        float gap = 2 * d, rad = Math.min(8 * d, Math.min(cw, ch) / 3);
        float fs = Math.min(13.5f * d, ch * 0.42f);
        Calendar k = (Calendar) mo.clone();
        for (int day = 1; day <= n; day++) {
            int idx = first + day - 1, col = idx % 7, row = idx / 7;
            RectF r = new RectF(col * cw + gap, dowH + row * ch + gap, (col + 1) * cw - gap, dowH + (row + 1) * ch - gap);
            JSONObject dd = days.optJSONObject(Store.key(k));
            int cnt = 0; for (String t : types) if (Store.has(dd, t)) cnt++;
            if (cnt > 0) {
                cv.save();
                Path clip = new Path(); clip.addRoundRect(r, rad, rad, Path.Direction.CW); cv.clipPath(clip);
                int j = 0; float seg = r.width() / cnt;
                for (int t = 0; t < 3; t++) if (Store.has(dd, types[t])) {
                    p.setColor(tint[t]); cv.drawRect(r.left + seg * j, r.top, r.left + seg * (j + 1), r.bottom, p);
                    p.setColor(edge[t]); cv.drawRect(r.left + seg * j, r.bottom - 2.5f * d, r.left + seg * (j + 1), r.bottom, p);
                    j++;
                }
                cv.restore();
            }
            boolean isToday = k.get(Calendar.YEAR) == today.get(Calendar.YEAR) && k.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
            boolean off = Store.isOff(set, k), hol = Store.isHoliday(set, k);
            float cx = r.centerX(), cy = r.centerY();
            if (isToday) { p.setColor(ink); cv.drawCircle(cx, cy, Math.min(fs * 1.05f, Math.min(r.width(), r.height()) / 2 - d), p); }
            p.setTextSize(fs);
            p.setFakeBoldText(!off);
            p.setColor(isToday ? bg : off ? muted : ink);
            cv.drawText(String.valueOf(day), cx, cy + fs * 0.35f, p);
            if (hol) { p.setColor(isToday ? bg : lateC); cv.drawCircle(cx, cy + fs * 0.75f, 1.6f * d, p); }
            k.add(Calendar.DAY_OF_MONTH, 1);
        }
        return b;
    }
}
