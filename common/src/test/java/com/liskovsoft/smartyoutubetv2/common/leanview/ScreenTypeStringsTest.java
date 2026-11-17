package com.liskovsoft.smartyoutubetv2.common.leanview;

import static org.junit.Assert.assertEquals;

import com.liskovsoft.smartyoutubetv2.common.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

// leanview: the "Screen type" setting is named in English and in Spanish with its diacritics.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ScreenTypeStringsTest {
    private static String s(int id) {
        return RuntimeEnvironment.getApplication().getString(id);
    }

    @Test
    public void english() {
        assertEquals("Screen type", s(R.string.leanview_screen_type));
        assertEquals("Automatic", s(R.string.leanview_screen_type_auto));
        assertEquals("TV", s(R.string.leanview_screen_type_tv));
        assertEquals("Phone", s(R.string.leanview_screen_type_phone));
    }

    @Test
    @Config(sdk = 28, qualifiers = "es")
    public void spanishWithItsDiacritics() {
        assertEquals("Tipo de pantalla", s(R.string.leanview_screen_type));
        assertEquals("Autom\u00e1tico", s(R.string.leanview_screen_type_auto));
        assertEquals("TV", s(R.string.leanview_screen_type_tv));
        assertEquals("Tel\u00e9fono", s(R.string.leanview_screen_type_phone));
    }
}
