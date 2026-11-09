package app.masquerade;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.webkit.JavascriptInterface;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public final class MainActivity extends Activity {
    private static final String SITE = "https://play.aidungeon.com";
    private static final String CARDS = "[role=group][aria-label^=scenario]";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView browser;
    private LinearLayout playBar;
    private String searchTerm = "";
    private JSONObject bookmarks;
    private WebView discovery;
    private JSONArray entries = new JSONArray();
    private String status = "Enter a search to find scenarios.";
    private String lastSnapshot = "";
    private int searchGeneration;
    private boolean polling;
    private boolean searching;
    private int requestedCount = -1;
    private final java.util.Set<String> displayedCards = new java.util.HashSet<>();

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            readResults();
            handler.postDelayed(this, requestedCount == displayedCards.size() ? 350 : 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        android.content.Context app = getApplicationContext();
        UiUpdate ui = UiUpdate.load(app);
        new Thread(() -> UiUpdate.check(app), "masquerade-ui-update").start();
        FrameLayout frame = new FrameLayout(this);
        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                                | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            }
            return insets;
        });
        browser = new WebView(this);
        browser.setWebViewClient(new WebViewClient());
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(false);
        bookmarks = readBookmarks();
        LinearLayout play = column();
        playBar = column();
        addButton(playBar, "Back to Masquerade", this::showDiscovery);
        play.addView(playBar);
        play.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        frame.addView(play, new FrameLayout.LayoutParams(-1, -1));
        playBar.setVisibility(View.GONE);

        discovery = new WebView(this);
        discovery.setBackgroundColor(0xff16131c);
        discovery.getSettings().setJavaScriptEnabled(true);
        discovery.getSettings().setAllowFileAccess(false);
        discovery.getSettings().setAllowContentAccess(false);
        discovery.getSettings().setBlockNetworkLoads(true);
        discovery.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }
        });
        discovery.addJavascriptInterface(new InterfaceHost(), "MasqueradeHost");
        frame.addView(discovery, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
        if (ui.html == null) {
            discovery.loadUrl("file:///android_asset/interface.html");
        } else {
            discovery.loadDataWithBaseURL("https://masquerade.invalid/", ui.html, "text/html", "UTF-8", null);
        }
        browser.loadUrl(SITE);
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private void addButton(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        parent.addView(button);
    }

    private void search(String query) {
        searchTerm = query.trim();
        searchGeneration++;
        searching = true;
        requestedCount = -1;
        lastSnapshot = "";
        displayedCards.clear();
        entries = new JSONArray();
        status = "Waiting for AI Dungeon results…";
        sendState();
        browser.loadUrl(SITE + "/search?searchTerm=" + Uri.encode(searchTerm));
    }

    private void readResults() {
        if (!polling || !searching || discovery.getVisibility() != View.VISIBLE) return;
        int generation = searchGeneration;
        String script = "(() => { if(location.origin !== " + JSONObject.quote(SITE)
                + " || location.pathname !== '/search') return null;"
                + "return JSON.stringify(Array.from(document.querySelectorAll("
                + JSONObject.quote(CARDS) + "), card => ({"
                + "label:card.getAttribute('aria-label'), text:card.querySelector('[role=link]')?.innerText || ''"
                + "}))); })()";
        browser.evaluateJavascript(script, value -> {
            if (!polling || generation != searchGeneration) return;
            try {
                Object decoded = new JSONTokener(value).nextValue();
                if (!(decoded instanceof String)) return;
                String snapshot = (String) decoded;
                if (snapshot.equals(lastSnapshot)) return;
                JSONArray cards = new JSONArray(snapshot);
                lastSnapshot = snapshot;
                for (int i = 0; i < cards.length(); i++) {
                    JSONObject card = cards.getJSONObject(i);
                    String label = card.getString("label");
                    if (!displayedCards.add(bookmarkKey(label))) continue;
                    card.put("query", searchTerm);
                    entries.put(card);
                }
                status = displayedCards.size() + " loaded scenarios";
                sendState();
            } catch (JSONException ignored) {
                status = "Couldn't read the current search results.";
                sendState();
            }
        });
    }

    private void loadMore(boolean manual) {
        int count = displayedCards.size();
        if (!searching || count == 0 || (!manual && requestedCount == count)) return;
        requestedCount = count;
        browser.evaluateJavascript("(() => { if(location.origin !== " + JSONObject.quote(SITE)
                + " || location.pathname !== '/search') return;"
                + "const cards = document.querySelectorAll(" + JSONObject.quote(CARDS)
                + "); const last = cards[cards.length-1]; if (!last) return; last.scrollIntoView({block:'end'}); for(let node=last.parentElement;node;node=node.parentElement){if(/auto|scroll/.test(getComputedStyle(node).overflowY) && node.scrollHeight > node.clientHeight){node.scrollBy(0, node.clientHeight / 2); return;}} window.scrollBy(0, innerHeight / 2); })()", null);
    }

    private JSONObject readBookmarks() {
        try {
            return new JSONObject(getPreferences(MODE_PRIVATE).getString("bookmarks", "{}"));
        } catch (JSONException ignored) {
            return new JSONObject();
        }
    }

    private boolean isBookmarked(String label) {
        return bookmarks.has(bookmarkKey(label));
    }

    private String bookmarkKey(String label) {
        return label.replaceFirst(", [^,]+ actions?, [^,]+ bookmarks?$", "");
    }

    private void toggleBookmark(String label, String query) {
        String key = bookmarkKey(label);
        if (bookmarks.has(key)) {
            bookmarks.remove(key);
        } else {
            try {
                bookmarks.put(key, query);
            } catch (JSONException ignored) {
                return;
            }
        }
        getPreferences(MODE_PRIVATE).edit().putString("bookmarks", bookmarks.toString()).apply();
    }

    private final class InterfaceHost {
        @JavascriptInterface public void dispatch(String message) {
            if (message == null || message.length() > 8192) return;
            handler.post(() -> {
                if (isDestroyed() || discovery.getVisibility() != View.VISIBLE) return;
                try {
                    JSONObject request = new JSONObject(message);
                    String label = request.optString("label");
                    String query = request.optString("query");
                    switch (request.getString("action")) {
                        case "ready": sendState(); break;
                        case "search": search(query); break;
                        case "more": loadMore(true); break;
                        case "prefetch": loadMore(false); break;
                        case "browser": showBrowser(); break;
                        case "open": if (!label.isEmpty()) openCard(label, query); break;
                        case "bookmark":
                            if (!label.isEmpty()) toggleBookmark(label, query);
                            sendState();
                            break;
                    }
                } catch (JSONException ignored) {
                    // Ignore malformed interface messages.
                }
            });
        }
    }

    private void sendState() {
        try {
            JSONArray saved = new JSONArray();
            java.util.Iterator<String> keys = bookmarks.keys();
            while (keys.hasNext()) {
                String label = keys.next();
                saved.put(new JSONObject().put("label", label)
                        .put("query", bookmarks.getString(label)).put("saved", true));
            }
            for (int i = 0; i < entries.length(); i++) {
                JSONObject card = entries.getJSONObject(i);
                card.put("saved", isBookmarked(card.getString("label")));
            }
            JSONObject state = new JSONObject().put("status", status)
                    .put("results", entries).put("bookmarks", saved);
            discovery.evaluateJavascript("window.masqueradeState?.(JSON.parse("
                    + JSONObject.quote(state.toString()) + "))", null);
        } catch (JSONException ignored) {
            // Retain the current interface if a snapshot cannot be serialized.
        }
    }

    private void openCard(String label, String query) {
        showBrowser();
        String key = bookmarkKey(label);
        playBar.removeAllViews();
        addButton(playBar, "Back to Masquerade", this::showDiscovery);
        Button save = new Button(this);
        save.setText(isBookmarked(key) ? "Remove bookmark" : "Bookmark");
        save.setOnClickListener(view -> {
            toggleBookmark(key, query);
            save.setText(isBookmarked(key) ? "Remove bookmark" : "Bookmark");
        });
        playBar.addView(save);
        browser.evaluateJavascript("(() => { if(location.origin !== " + JSONObject.quote(SITE)
                + " || location.pathname !== '/search') return false;"
                + "const card = Array.from(document.querySelectorAll(" + JSONObject.quote(CARDS)
                + ")).find(card => card.getAttribute('aria-label').replace(/, [^,]+ actions?, [^,]+ bookmarks?$/, '') === "
                + JSONObject.quote(key)
                + "); const link = card?.querySelector('[role=link], a');"
                + "if (!link) return false; link.click(); return true; })()", opened -> {
            if ("true".equals(opened)) return;
            browser.loadUrl(SITE + "/search?searchTerm=" + Uri.encode(query));
            android.widget.Toast.makeText(this, "Reopened the original search for this scenario.",
                    android.widget.Toast.LENGTH_LONG).show();
        });
    }

    private void showBrowser() {
        showDiscovery();
        playBar.removeAllViews();
        addButton(playBar, "Back to Masquerade", this::showDiscovery);
        playBar.setVisibility(View.VISIBLE);
        discovery.setVisibility(View.GONE);
        browser.requestFocus();
    }

    private void showDiscovery() {

        playBar.setVisibility(View.GONE);
        discovery.setVisibility(View.VISIBLE);
        sendState();
        readResults();
    }

    @Override public void onBackPressed() {
        if (discovery.getVisibility() == View.GONE) {
            showDiscovery();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        polling = true;
        handler.post(poll);
    }

    @Override protected void onPause() {
        polling = false;
        handler.removeCallbacks(poll);
        super.onPause();
    }

    @Override protected void onDestroy() {
        browser.destroy();
        discovery.destroy();
        super.onDestroy();
    }
}







