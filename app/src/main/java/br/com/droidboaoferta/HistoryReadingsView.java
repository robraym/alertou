package br.com.droidboaoferta;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Only the readings scroll; the caller keeps its graph, heading and actions outside this view. */
final class HistoryReadingsView {
    interface RowFactory<T> { View bind(T point, View previous); }

    static <T> ListView create(Activity activity, List<T> chronological, RowFactory<T> factory) {
        List<T> newestFirst = new ArrayList<>(chronological);
        Collections.reverse(newestFirst);
        ListView list = new ListView(activity);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(true);
        list.setScrollbarFadingEnabled(false);
        list.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);
        list.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);
        list.setPadding(0, 0, Math.round(8 * activity.getResources().getDisplayMetrics().density), 0);
        list.setAdapter(new ArrayAdapter<T>(activity, 0, newestFirst) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                return factory.bind(getItem(position), convertView);
            }
        });
        return list;
    }
}
