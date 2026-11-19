package br.com.droidboaoferta;

import android.content.SharedPreferences;
import org.junit.Test;
import static org.junit.Assert.*;

public class FontControllerTest {
    @Test public void existingInstallKeepsDefaultFontUntilUserChooses() {
        assertEquals(FontController.MODE_DEFAULT, FontController.readMode(TestPreferences.create()));
    }

    @Test public void condensedIsRestoredAndUnknownFamilyFallsBackToDefault() {
        SharedPreferences preferences = TestPreferences.create();
        preferences.edit().putString(FontController.KEY, "condensed").apply();
        assertEquals(FontController.MODE_CONDENSED, FontController.readMode(preferences));
        assertEquals(R.font.app_condensed, FontController.fontResource(FontController.readMode(preferences)));
        assertEquals(0, FontController.fontResource(FontController.MODE_DEFAULT));
        preferences.edit().putString(FontController.KEY, "unknown-font").apply();
        assertEquals(FontController.MODE_DEFAULT, FontController.readMode(preferences));
    }
}
