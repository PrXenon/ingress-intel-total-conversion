package org.exarhteam.iitc_mobile;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import org.exarhteam.iitc_mobile.async.CheckHttpResponse;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@SuppressLint("SetJavaScriptEnabled")
public class IITC_WebView extends WebView {

    // fullscreen modes
    public static final int FS_ENABLED = (1 << 0);
    public static final int FS_SYSBAR = (1 << 1);
    public static final int FS_ACTIONBAR = (1 << 2);
    public static final int FS_STATUSBAR = (1 << 3);
    public static final int FS_NAVBAR = (1 << 4);
    private final String mDesktopUserAgent = "Mozilla/5.0 (X11; Linux x86_64; rv:17.0)" +
            " Gecko/20130810 Firefox/17.0 Iceweasel/17.0.8";
    private WebSettings mSettings;
    private IITC_WebViewClient mIitcWebViewClient;
    private IITC_WebChromeClient mIitcWebChromeClient;
    private IITC_JSInterface mJsInterface;
    private IITC_Mobile mIitc;
    private SharedPreferences mSharedPrefs;
    private int mFullscreenStatus = 0;
    private Runnable mNavHider;
    private boolean mDisableJs = false;
    private String mMobileUserAgent;


    // constructors -------------------------------------------------
    public IITC_WebView(final Context context) {
        super(context);

        iitc_init(context);
    }

    public IITC_WebView(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        iitc_init(context);
    }

    public IITC_WebView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        iitc_init(context);
    }

    // init web view
    private void iitc_init(final Context c) {
        if (isInEditMode()) return;
        mIitc = (IITC_Mobile) c;
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(mIitc);

        mSettings = getSettings();
        mSettings.setJavaScriptEnabled(true);
        mSettings.setDomStorageEnabled(true);
        mSettings.setAllowFileAccess(true);
        mSettings.setGeolocationEnabled(true);
        //   mSettings.setAppCacheEnabled(true);  ! is deprecated (new: .setCacheMode())
        mSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // mSettings.setAppCachePath(getContext().getCacheDir().getAbsolutePath()); ! removed in SDK 33 (https://developer.android.com/sdk/api_diff/33/changes)
        int zoom = Integer.parseInt(mSharedPrefs.getString("pref_webview_zoom", "-1"));
        if (zoom != -1) {
            mSettings.setTextZoom(zoom);
        }

        // enable mixed content (http on https...needed for some map tiles) mode
        setWebContentsDebuggingEnabled(true);
        mSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        setWebContentsDebuggingEnabled(true);
        mJsInterface = new IITC_JSInterfaceKitkat(mIitc);

        addJavascriptInterface(mJsInterface, "app");

        // https://developer.chrome.com/multidevice/user-agent#webview_user_agent
        final String original_ua = mSettings.getUserAgentString();
        if (original_ua.contains("; wv")) {
            // remove ";wv " marker as Google blocks WebViews from using OAuth
            mMobileUserAgent = original_ua.replace("; wv", "");
        } else { // KitKat and older
            mMobileUserAgent = original_ua.replaceFirst("Version\\/\\d\\.\\d+ ", "");
        }
        setUserAgent();

        mNavHider = new Runnable() {
            @Override
            public void run() {
                if (isInFullscreen() && (getFullscreenStatus() & (FS_NAVBAR)) != 0) {
                    int systemUiVisibility = SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                    // in immersive mode the user can interact with the app while the navbar is hidden
                    // this mode is available since KitKat
                    // you can leave this mode by swiping down from the top of the screen. this does only work
                    // when the app is in total-fullscreen mode
                    if ((mFullscreenStatus & FS_SYSBAR) != 0) {
                        systemUiVisibility |= SYSTEM_UI_FLAG_IMMERSIVE;
                    }
                    setSystemUiVisibility(systemUiVisibility);
                }
            }
        };

        mIitcWebChromeClient = new IITC_WebChromeClient(mIitc);
        setWebChromeClient(mIitcWebChromeClient);
        mIitcWebViewClient = new IITC_WebViewClient(mIitc);
        setWebViewClient(mIitcWebViewClient);
    }

    // ----------------------------------------------------------------

    @Override
    public void loadUrl(String url) {
        if (url.startsWith("javascript:")) {
            // do nothing if script is enabled;
            if (mDisableJs) {
                Log.d("javascript injection disabled...return");
                return;
            }
            loadJS(url.substring("javascript:".length()));
        } else {
            // Niantic no longer allows connections without https
            url = url.replace("http://", "https://");

            // disable splash screen if a http error code is responded
            new CheckHttpResponse(mIitc).execute(url);
            Log.d("loading url: " + url);
            super.loadUrl(url);
        }
    }

    @TargetApi(19)
    public void loadJS(final String js) {
        // if in edit text mode, don't load javascript otherwise the keyboard closes.
        final HitTestResult testResult = getHitTestResult();
        if (testResult != null && testResult.getType() == HitTestResult.EDIT_TEXT_TYPE) {
            // let window.show(...) interrupt input
            // window.show(...) is called if one of the action bar buttons
            // is clicked
            if (!js.startsWith("window.show(")) {
                Log.d("in insert mode. do not load script.");
                return;
            }
        }
        super.loadUrl("javascript:" + js);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        getHandler().removeCallbacks(mNavHider);
        getHandler().postDelayed(mNavHider, 3000);
        return super.onTouchEvent(event);
    }

    @Override
    public void setSystemUiVisibility(final int visibility) {
        if ((visibility & SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
            getHandler().postDelayed(mNavHider, 3000);
        }
        super.setSystemUiVisibility(visibility);
    }

    @Override
    public void onWindowFocusChanged(final boolean hasWindowFocus) {
        if (hasWindowFocus) {
            getHandler().postDelayed(mNavHider, 3000);
            // if the webView has focus, JS should always be enabled
            mDisableJs = false;
        } else {
            getHandler().removeCallbacks(mNavHider);
        }
        super.onWindowFocusChanged(hasWindowFocus);
    }

    public void toggleFullscreen() {
        mFullscreenStatus ^= FS_ENABLED;

        final WindowManager.LayoutParams attrs = mIitc.getWindow().getAttributes();
        // toggle notification bar
        if (isInFullscreen()) {
            // show a toast with instructions to exit the fullscreen mode again
            Toast.makeText(mIitc, "Press back button to exit fullscreen", Toast.LENGTH_SHORT).show();
            if ((mFullscreenStatus & FS_ACTIONBAR) != 0) {
                mIitc.getNavigationHelper().hideActionBar();
            }
            if ((mFullscreenStatus & FS_SYSBAR) != 0) {
                attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
            }
            if ((mFullscreenStatus & FS_NAVBAR) != 0) {
                getHandler().post(mNavHider);
            }
            if ((mFullscreenStatus & FS_STATUSBAR) != 0) {
                loadUrl("javascript: $('#updatestatus').hide();");
            }
        } else {
            attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
            mIitc.getNavigationHelper().showActionBar();
            loadUrl("javascript: $('#updatestatus').show();");
        }
        mIitc.getWindow().setAttributes(attrs);
        mIitc.invalidateOptionsMenu();
    }

    void updateFullscreenStatus() {
        final String[] fullscreenDefaults = getResources().getStringArray(R.array.pref_hide_fullscreen_defaults);
        final Set<String> entries = mSharedPrefs.getStringSet("pref_fullscreen",
                new HashSet<String>(Arrays.asList(fullscreenDefaults)));
        mFullscreenStatus &= FS_ENABLED;

        for (final String entry : entries) {
            mFullscreenStatus += Integer.parseInt(entry);
        }
    }

    int getFullscreenStatus() {
        return mFullscreenStatus;
    }

    public boolean isInFullscreen() {
        return (mFullscreenStatus & FS_ENABLED) != 0;
    }

    public IITC_WebViewClient getWebViewClient() {
        return mIitcWebViewClient;
    }

    public IITC_JSInterface getJSInterface() {
        return mJsInterface;
    }

    public boolean isConnectedToWifi() {
        final ConnectivityManager conMan = (ConnectivityManager) mIitc.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo wifi = conMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        // since jelly bean you can mark wifi networks as mobile hotspots
        // settings -> data usage -> menu -> mobile hotspots
        // ConnectivityManager.isActiveNetworkMeter returns if the currently used wifi-network
        // is ticked as mobile hotspot or not.
        // --> IITC_WebView.isConnectedToWifi should return 'false' if connected to mobile hotspot
        if (conMan.isActiveNetworkMetered()) return false;

        return (wifi.getState() == NetworkInfo.State.CONNECTED);
    }

    public void disableJS(final boolean val) {
        mDisableJs = val;
    }

    public void setUserAgent() {
        final String ua = mSharedPrefs.getBoolean("pref_fake_user_agent", false) ?
                mDesktopUserAgent : mMobileUserAgent;
        Log.d("setting user agent to: " + ua);
        mSettings.setUserAgentString(ua);
    }
}
