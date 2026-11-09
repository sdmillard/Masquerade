package app.masquerade;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
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
    private LinearLayout library;
    private String searchTerm = "";
    private JSONObject bookmarks;
    private LinearLayout discovery;
    private LinearLayout results;
    private TextView status;
    private String lastSnapshot = "";
    private int searchGeneration;
    private boolean polling;
    private boolean searching;
    private int requestedCount = -1;
    private final java.util.Set<String> displayedCards = new java.util.HashSet<>();
    private ScrollView scroll;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            readResults();
            handler.postDelayed(this, requestedCount == displayedCards.size() ? 350 : 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
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

        discovery = column();
        discovery.setBackgroundColor(0xff16131c);
        discovery.setPadding(24, 32, 24, 24);
        frame.addView(discovery, new FrameLayout.LayoutParams(-1, -1));
        TextView heading = new TextView(this);
        heading.setText("Masquerade");
        heading.setTextSize(26);
        discovery.addView(heading);
        EditText query = new EditText(this);
        query.setSingleLine();
        query.setHint("Search scenarios");
        discovery.addView(query);
        addButton(discovery, "Bookmarks", this::showBookmarks);
        addButton(discovery, "Search", () -> search(query.getText().toString()));
        addButton(discovery, "Open AI Dungeon / sign in", this::showBrowser);
        status = new TextView(this);
        status.setText("Enter a search to find scenarios.");
        discovery.addView(status);

        scroll = new ScrollView(this);
        results = column();
        scroll.addView(results);
        discovery.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            if (y > oldY) {
                prefetchIfNeeded();
            }
        });
        addButton(discovery, "Load more", () -> loadMore(true));
        setContentView(frame);
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
        scroll.scrollTo(0, 0);
        results.removeAllViews();
        status.setText("Waiting for AI Dungeon results…");
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
                int previousCount = displayedCards.size();
                lastSnapshot = snapshot;
                for (int i = 0; i < cards.length(); i++) {
                    JSONObject card = cards.getJSONObject(i);
                    String label = card.getString("label");
                    String title = label.replaceFirst("^scenario\\s+", "")
                            .replaceFirst(", [^,]+ actions?, [^,]+ bookmarks?$", "");
                    if (!displayedCards.add(title)) continue;
                    String originQuery = searchTerm;
                    LinearLayout row = column();
                    addButton(row, label.replaceFirst("^scenario\\s+", ""), () -> openCard(label, originQuery));
                    Button save = new Button(this);
                    save.setText(isBookmarked(label) ? "Remove bookmark" : "Bookmark");
                    save.setOnClickListener(view -> {
                        toggleBookmark(label, originQuery);
                        save.setText(isBookmarked(label) ? "Remove bookmark" : "Bookmark");
                    });
                    row.addView(save);
                    results.addView(row);
                    String text = card.optString("text");
                    if (!text.trim().isEmpty()) {
                        TextView description = new TextView(this);
                        description.setText(text);
                        row.addView(description);
                    }
                }
                status.setText(displayedCards.size() + " loaded scenarios");
                if (displayedCards.size() > previousCount) {
                    scroll.post(this::prefetchIfNeeded);
                }
            } catch (JSONException ignored) {
                status.setText("Couldn't read the current search results.");
            }
        });
    }

    private void prefetchIfNeeded() {
        int viewport = scroll.getHeight();
        int remaining = results.getHeight() - viewport - scroll.getScrollY();
        if (viewport > 0 && remaining <= 4 * viewport) loadMore(false);
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

    private void showBookmarks() {
        library = column();
        addButton(library, "Back to search", this::showDiscovery);
        ScrollView list = new ScrollView(this);
        LinearLayout entries = column();
        list.addView(entries);
        library.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        if (bookmarks.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No bookmarks yet. Bookmark a scenario from search.");
            entries.addView(empty);
        }
        java.util.Iterator<String> keys = bookmarks.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String query = bookmarks.optString(key);
            addButton(entries, key.replaceFirst("^scenario\\s+", ""), () -> openCard(key, query));
            addButton(entries, "Remove bookmark", () -> {
                toggleBookmark(key, query);
                showDiscovery();
                showBookmarks();
            });
        }
        library.setBackgroundColor(0xff16131c);
        FrameLayout frame = (FrameLayout) discovery.getParent();
        frame.addView(library, new FrameLayout.LayoutParams(-1, -1));
        discovery.setVisibility(View.GONE);
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
        if (library != null) {
            ((FrameLayout) library.getParent()).removeView(library);
            library = null;
        }
        playBar.setVisibility(View.GONE);
        discovery.setVisibility(View.VISIBLE);
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
        super.onDestroy();
    }
}





