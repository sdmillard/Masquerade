# Masquerade

Masquerade is a self-contained Android interface for discovering and playing AI Dungeon scenarios. AI Dungeon supplies the catalog, account, models, and cloud saves. The first milestone replaces discovery and hands play off to the official page inside the same app, subject to embedded-browser validation.

## Discovery

Keep the query, sort, and active filters in view. Use dense rows with full titles, creators, activity counts, and expandable descriptions. Restore the query, filters, expanded rows, and scroll position after leaving a result. Save named filter presets locally.

AI Dungeon already provides tag inclusion and exclusion, creator selection, date ranges, ratings, feature filters, and advanced sorts. Use those controls for catalog searches. Title-only matching and excluded words can refine loaded results locally. Label those results with matching and loaded counts.

## Integration

Start with a presentation prototype using fictional fixtures. Next, test a page adapter in LibreWolf against the official search UI. Verify that applying a tag inclusion, exclusion, and sort through the adapter yields the same results as applying them through Advanced Filters. Keep extraction and control wiring separate from presentation so site changes can be isolated. Retain the official page as a fallback if the adapter cannot recognize it.

For Android, test an embedded browser session on the Pixel before committing to a shell. Verify sign-in, search, opening a scenario, generating one turn with the selected model, and reopening the saved adventure. Keep authentication and generation inside the official site and store only interface preferences locally. Check Latitude's current terms and integration options before distributing the wrapper.

## Later milestones

Build a reading-focused play screen with a thumb-accessible composer, visible generation state, and quick access to retry, undo, and story cards. Verify parity with the official UI before exposing controls that mutate saves or consume credits.

## Run the prototype

Open index.html in a desktop or mobile browser. Try “space opera,” add “isekai” to Exclude words, and toggle Title only. The prototype uses fictional fixtures and a link to official search. Live filters, account access, and gameplay are integration milestones.

## Live browser spike

Install masquerade.user.js in a userscript manager in LibreWolf or IronFox. Run a scenario search on AI Dungeon. The panel uses rendered scenario links and their accessible labels, with descriptions where exposed inside the links. The official results stay available below it. Use the site's own filters to change the search, then use Masquerade to narrow the loaded results. If the panel reports no recognizable cards while the site shows scenarios, inspect the live card markup and update the adapter.

This spike has no private API integration. It needs a live browser test before treating card extraction or navigation as verified.

## Dedicated discovery view

Open Masquerade expands the live panel into a full-screen view within the AI Dungeon tab. This keeps the official search page and its session underneath the interface. Scrolling near the bottom moves the official grid to its last recognized card to trigger its existing infinite loader. A manual Load more button is available when there are too few matching rows to scroll. If the official site stops loading or changes its pagination, this mechanism requires live revalidation.

## Android discovery spike

The android directory contains a standalone Android project. Build with JDK 17, Gradle 8.9, and Android SDK 35 using gradle -p android assembleDebug. The native discovery view covers a laid-out WebView. The app reads rendered public scenario cards with evaluateJavascript and does not expose a native JavaScript interface to the page. Use Open AI Dungeon / sign in to reveal the browser and Android Back to return to discovery. Opening a scenario also reveals the official page.

Verify on the Pixel that search results populate with the WebView covered, then scroll down and confirm a second batch arrives. Test manual Load more, scenario launch, sign-in and save reopening separately. The Android source has not been compiled or tested on-device in this workspace.
