// Copyright (c) 2009, Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package net.tawacentral.roger.secrets;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

/**
 * This class wraps OS-specific APIs behind Java's reflection API so that the
 * application can still run on OSs that don't support the given API.  This
 * class is used as a backward compatibility layer so that I don't need to
 * build different versions of the application for different OSs.
 *
 * The root of the problem is the dalvik class verifier, which rejects classes
 * that depend on system classes or interfaces that are not present on the
 * device, causing the application to force exit.  Instead of statically
 * depending on OS-specific classes, this code uses reflection to dynamically
 * discover if a class is available or not, in order to get around the verifier.
 * This is the "correct" way to support different OS versions.
 *
 * @author rogerta
 *
 */
public class OS {
  /** Tag for logging purposes. */
  public static final String LOG_TAG = "Secrets";

  private static int sdkVersion;
  static {
    try {
      sdkVersion = Integer.parseInt(android.os.Build.VERSION.SDK);
    } catch (Exception ex) {
    }
  }

  /** Does the device support the froyo (Android 2.2) APIs? */
  public static boolean isAndroid22() {
    return sdkVersion >= 8;
  }

  /** Does the device support the Gingerbread (Android 2.3) APIs? */
  public static boolean isAndroid23() {
    return sdkVersion >= 9;
  }

  /** Does the device support the Honeycomb (Android 3.0) APIs? */
  public static boolean isAndroid30() {
    return sdkVersion >= 11;
  }

  /** Hide the soft keyboard if visible. */
  public static void hideSoftKeyboard(Context ctx, View view) {
    InputMethodManager manager = (InputMethodManager)
        ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
    if (null != manager)
      manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
  }

  /**
   * Creates a backup manager for handling app backup in Android 2.2 and later.
   * Returns an Object so that that this function can be called from any
   * version of Android.
   *
   * @param context Application context for the backup manager.
   * @return A backup manager on Android 2.2 and later, null otherwise.
   */
  public static Object createBackupManager(Context context) {
    Object bm = null;

    if (isAndroid22()) {
      try {
        Class<?> clazz = Class.forName("android.app.backup.BackupManager");
        Constructor<?> c = clazz.getConstructor(Context.class);
        bm = c.newInstance(context);
        Log.d(LOG_TAG, "createBackupManager");
      } catch (Exception ex) {
        Log.e(LOG_TAG, "Should have found backup manager", ex);
      }
    }

    return bm;
  }

  /**
   * Calls dataChanged() on the backup manager on Android 2.2 and later, or
   * does nothing on previous versions of Android.
   *
   * @param bm Backup manager.  Can be null.
   */
  public static void backupManagerDataChanged(Object bm) {
    if (isAndroid22() && bm != null) {
      try {
        Class<?> clazz = Class.forName("android.app.backup.BackupManager");
        Method m = clazz.getMethod("dataChanged");
        m.invoke(bm);
        Log.d(LOG_TAG, "backupManagerDataChanged");
      } catch (Exception ex) {
        Log.e(LOG_TAG, "backupManagerDataChanged", ex);
      }
    }
  }

  /**
   * Invalidates the option menu so that it is recreated.  This is needed
   * for Honeycomb so that the action bar can be updated when switching to
   * and from editing mode.
   *
   * @param activity The activity containing the option menu.
   */
  public static void invalidateOptionsMenu(Activity activity) {
    if (!isAndroid30())
      return;

    try {
      Method m = activity.getClass().getMethod("invalidateOptionsMenu");
      m.invoke(activity);
    } catch (Exception ex) {
      Log.e(LOG_TAG, "invalidateOptionMenu", ex);
    }
  }

  /**
   * Configures a SearchView if this is Android 3.0 or later.
   *
   * @param activity The activity containing the search view.
   */
  public static void configureSearchView(Activity activity, Menu menu) {
    if (!isAndroid30())
      return;

    try {
      SearchManager sm = (SearchManager) activity.getSystemService(
          Context.SEARCH_SERVICE);
      if (null == sm)
        return;

      Method m = sm.getClass().getMethod("getSearchableInfo",
          android.content.ComponentName.class);
      Object si = m.invoke(sm, activity.getComponentName());

      MenuItem item = menu.findItem(R.id.list_search);
      m = item.getClass().getMethod("getActionView");
      View widget = (View) m.invoke(item);

      m = widget.getClass().getMethod("setSearchableInfo", si.getClass());
      m.invoke(widget, si);

      m = widget.getClass().getMethod("setIconifiedByDefault", boolean.class);
      m.invoke(widget, false);

      m = widget.getClass().getMethod("setSubmitButtonEnabled", boolean.class);
      m.invoke(widget, true);
    } catch (Exception ex) {
      Log.e(LOG_TAG, "configureSearchView", ex);
    }
  }

  /** Does the device support a scroll wheel or trackball? */
  public static boolean supportsScrollWheel() {
    // This API is only support in Android 2.3 and later.  If this is an
    // earlier version of Android, then assume we have a scroll wheel.
    if (!isAndroid23())
      return true;

    try {
      Class<?> clazz = Class.forName("android.view.InputDevice");
      Method m = clazz.getMethod("getDeviceIds");
      Field f = clazz.getField("SOURCE_TRACKBALL");
      final int trackballId = f.getInt(null);
      f = clazz.getField("SOURCE_DPAD");
      final int dpadId = f.getInt(null);

      Method mGetDevice = clazz.getMethod("getDevice", int.class);
      Method mGetSources = clazz.getMethod("getSources");
      int[] ids = (int[]) m.invoke(null);
      for (int id : ids) {
        Object device = mGetDevice.invoke(null, id);
        Integer sources = (Integer) mGetSources.invoke(device);
        if (0 != (sources.intValue() & (trackballId | dpadId)))
          return true;
      }
    } catch (Exception ex) {
      Log.e(LOG_TAG, "supportsScrollWheel", ex);
      return true;
    }

    return false;
  }

  public static SharedPreferences getSharedPreferences(Object ba,
                                                       String name,
                                                       int mode) {
    if (!isAndroid22())
      return null;

    try {
      Class<?> clazz = Class.forName("android.app.backup.BackupAgentHelper");
      Method m = clazz.getMethod("getSharedPreferences", String.class,
                                 int.class);
      SharedPreferences prefs = (SharedPreferences) m.invoke(ba, name, mode);
      return prefs;
    } catch (Exception ex) {
      Log.e(LOG_TAG, "getSharedPreferences", ex);
      return null;
    }
  }
}
