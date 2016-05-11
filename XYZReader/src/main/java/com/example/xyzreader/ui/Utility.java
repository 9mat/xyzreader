package com.example.xyzreader.ui;

import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.TypedValue;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by 9mat on 9/5/2016.
 * Helper functions
 */
public final class Utility {

    static final boolean POST_LOLLIPOP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    static Context sGlobalContext;

    private static Map<Long, Integer> sArticleColorMap = new HashMap<>();


    public static String makeDateString(long date) {
        return DateUtils.getRelativeTimeSpanString(date,
                System.currentTimeMillis(),
                DateUtils.HOUR_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL).toString();
    }

    public static void updateArticleColorMap(long id, int color) {
        if(sArticleColorMap.containsKey(id)) {
            Log.i(Utility.class.getSimpleName(), "update color already exist");
            if(sArticleColorMap.get(id) != color) {
                Log.i(Utility.class.getSimpleName(), "new color " + Integer.toHexString(color) + " for id " + String.valueOf(id) + " old color " + Integer.toHexString(getArticleColor(id)));
            }
        }
        sArticleColorMap.put(id,color);
    }

    public static boolean hasArticleColor(long id) {
        return sArticleColorMap.containsKey(id);
    }

    public static int getArticleColor(long id){
        if(sArticleColorMap.containsKey(id)) return sArticleColorMap.get(id);
        return getPrimaryDarkColor(getGlobalContext());
    }

    public static int getPrimaryDarkColor(Context context) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
        return typedValue.data;
    }

    public static Context getGlobalContext() {
        return sGlobalContext;
    }

    public static class ArticleInfoSimple {
        public String title, author, photoUrl, thumbUrl;
        public long id, date;
        public float aspect;

        public ArticleInfoSimple(Cursor cursor) {
            if(cursor != null) {
                title = cursor.getString(ArticleLoader.Query.TITLE);
                author = cursor.getString(ArticleLoader.Query.AUTHOR);
                photoUrl = cursor.getString(ArticleLoader.Query.PHOTO_URL);
                thumbUrl = cursor.getString(ArticleLoader.Query.THUMB_URL);
                id = cursor.getLong(ArticleLoader.Query._ID);
                date = cursor.getLong(ArticleLoader.Query.PUBLISHED_DATE);
                aspect = cursor.getFloat(ArticleLoader.Query.ASPECT_RATIO);
            }
        }
    }

    public static class ArticleInfo extends ArticleInfoSimple {
        public String body;

        public ArticleInfo(Cursor cursor) {
            super(cursor);
            body = cursor.getString(ArticleLoader.Query.BODY);
        }
    }

    public static String makeTransitionName(long id, String suffix) {
        return "transition_" + String.valueOf(id) + "_" + suffix;
    }

    public static String makeTransitionName(long id){
        return "transition_" + String.valueOf(id);
    }

    public static String makeTransitionName(String str, String suffix){
        return str + "_" + suffix;
    }
}
