package com.exito.chop;

import android.content.Context;
import android.graphics.Typeface;

/** Bundled, offline lettering for the sampler panel. */
final class Typography {
    private static Typeface body, control, display;

    static synchronized Typeface body(Context c) {
        if (body == null) body = load(c, "chop_regular");
        return body;
    }

    static synchronized Typeface control(Context c) {
        if (control == null) control = load(c, "chop_control");
        return control;
    }

    static synchronized Typeface display(Context c) {
        if (display == null) display = load(c, "chop_display");
        return display;
    }

    private static Typeface load(Context c, String name) {
        int id = c.getResources().getIdentifier(name, "font", c.getPackageName());
        return id == 0 ? Typeface.DEFAULT : c.getResources().getFont(id);
    }

    private Typography() {}
}
