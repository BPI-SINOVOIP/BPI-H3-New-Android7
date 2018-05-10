package com.android.server.pm.util;

import java.io.Serializable;
import android.util.DisplayMetrics;

public class DisplayMetricsGhost implements Serializable {

    public int widthPixels;
    public int heightPixels;
    public float density;
    public int densityDpi;
    public float scaledDensity;
    public float xdpi;
    public float ydpi;
    public int noncompatWidthPixels;
    public int noncompatHeightPixels;
    public float noncompatDensity;
    public int noncompatDensityDpi;
    public float noncompatScaledDensity;
    public float noncompatXdpi;
    public float noncompatYdpi;

    public DisplayMetricsGhost(DisplayMetrics o) {
        widthPixels = o.widthPixels;
        heightPixels = o.heightPixels;
        density = o.density;
        densityDpi = o.densityDpi;
        scaledDensity = o.scaledDensity;
        xdpi = o.xdpi;
        ydpi = o.ydpi;
        noncompatWidthPixels = o.noncompatWidthPixels;
        noncompatHeightPixels = o.noncompatHeightPixels;
        noncompatDensity = o.noncompatDensity;
        noncompatDensityDpi = o.noncompatDensityDpi;
        noncompatScaledDensity = o.noncompatScaledDensity;
        noncompatXdpi = o.noncompatXdpi;
        noncompatYdpi = o.noncompatYdpi;
    }

    public DisplayMetrics dumpFromGhost() {
        DisplayMetrics dm = new DisplayMetrics();
        dm.widthPixels = widthPixels;
        dm.heightPixels = heightPixels;
        dm.density = density;
        dm.densityDpi = densityDpi;
        dm.scaledDensity = scaledDensity;
        dm.xdpi = xdpi;
        dm.ydpi = ydpi;
        dm.noncompatWidthPixels = noncompatWidthPixels;
        dm.noncompatHeightPixels = noncompatHeightPixels;
        dm.noncompatDensity = noncompatDensity;
        dm.noncompatDensityDpi = noncompatDensityDpi;
        dm.noncompatScaledDensity = noncompatScaledDensity;
        dm.noncompatXdpi = noncompatXdpi;
        dm.noncompatYdpi = noncompatYdpi;
        return dm;
    }
}

