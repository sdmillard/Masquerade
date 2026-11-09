// ==UserScript==
// @name         Masquerade Discovery
// @namespace    masquerade
// @description  Compact filtering for AI Dungeon scenario results.
// @version      0.2.0
// @match        https://play.aidungeon.com/*
// @grant        none
// ==/UserScript==

(() => {
  "use strict";

  const panel = document.createElement("section");
  panel.id = "masquerade-discovery";
  const root = panel.attachShadow({ mode: "open" });
  root.innerHTML = `
    <style>
      :host{display:block;margin:1rem auto;max-width:76rem;padding:0 1rem;color:#f1edf7}
      details{background:#1d1926;border:1px solid #51465f;border-radius:.5rem;padding:1rem}
      summary{cursor:pointer;font-weight:700}
      :host([workspace]){position:fixed;inset:0;z-index:2147483000;max-width:none;margin:0;padding:0;background:#16131c}
      :host([workspace]) > details{height:100%;box-sizing:border-box;overflow:auto;border:0;border-radius:0;overscroll-behavior:contain}
      button{cursor:pointer}
      #more{margin-top:1rem}
      .controls{display:flex;flex-wrap:wrap;gap:.75rem;align-items:center;margin:.8rem 0}
      input,select{font:inherit;color:inherit;background:#282132;border:1px solid #756582;border-radius:.3rem;padding:.45rem}
      input[type=search]{width:min(100%,16rem)}
      article{padding:.65rem 0;border-top:1px solid #51465f}
      a{color:#dabaff;font-weight:650}
      p{margin:.25rem 0;color:#c8bfd4}
    </style>
    <details open>
      <summary>Masquerade · Discovery</summary>
      <div class="controls">
        <button type="button" id="workspace">Open Masquerade</button>
        <input type="search" aria-label="Refine loaded results" placeholder="Refine loaded results">
        <input type="search" aria-label="Exclude words" placeholder="Exclude words">
        <label><input type="checkbox"> Title only</label>
        <select aria-label="Sort loaded results">
          <option value="site">Site order</option>
          <option value="title">Title</option>
        </select>
      </div>
      <p role="status"></p>
      <div id="rows"></div>
      <button type="button" id="more" hidden>Load more from AI Dungeon</button>
    </details>`;

  const [query, exclude, titleOnly] = root.querySelectorAll("input");
  const sort = root.querySelector("select");
  const status = root.querySelector("[role=status]");
  const rows = root.querySelector("#rows");
  const workspace = root.querySelector("#workspace");
  const more = root.querySelector("#more");
  const scroller = root.querySelector("details");
  let lastCard;
  let requestedCard;
  let lastRequest = 0;
  let lastScrollTop = 0;
  let signature;
  const targets = new Map();

  function collectResults() {
    const results = new Map();
    targets.clear();
    lastCard = undefined;
    for (const link of document.querySelectorAll('a[href], [role=link], [aria-label^="scenario " i]')) {
      const label = link.getAttribute("aria-label")
        || link.closest('[aria-label^="scenario " i]')?.getAttribute("aria-label")
        || "";
      const match = label.match(/^scenario\s+(.+?),\s*([^,]+ actions?),\s*([^,]+ bookmarks?)$/i);
      if (!match) continue;
      const href = link.getAttribute("href") || link.querySelector("a[href]")?.getAttribute("href");
      const target = link.matches("a, [role=link]") ? link : link.querySelector("a, [role=link]");
      if (!href && !target) continue;
      const url = href ? new URL(href, location.href) : null;
      if (url && url.origin !== location.origin) continue;
      const description = Array.from(link.querySelectorAll("p, span, div"))
        .map(node => node.textContent.trim())
        .filter(value => value.length > match[1].length && !value.includes(match[0]))
        .sort((a, b) => b.length - a.length)[0] || "";
      if (results.has(label)) continue;
      targets.set(label, target);
      lastCard = link;
      results.set(label, {
        label,
        url: url?.href,
        title: match[1],
        stats: `${match[2]} · ${match[3]}`,
        description,
      });
    }
    return [...results.values()];
  }

  function refresh() {
    if (location.pathname !== "/search") {
      panel.remove();
      panel.removeAttribute("workspace");
      workspace.textContent = "Open Masquerade";
      requestedCard = undefined;
      signature = undefined;
      return;
    }

    const results = collectResults();
    more.hidden = !lastCard;
    if (!panel.isConnected) {
      const firstCard = document.querySelector('[aria-label^="scenario " i]');
      if (!firstCard) return;
      let grid = firstCard.parentElement;
      while (grid.parentElement && grid.querySelectorAll('[role=group][aria-label^="scenario " i]').length < 2) {
        const parent = grid.parentElement;
        if (parent === document.body) break;
        grid = parent;
      }
      grid.before(panel);
    }

    const phrase = query.value.trim().toLowerCase();
    const words = exclude.value.toLowerCase().trim().split(/\s+/).filter(Boolean);
    const matching = results.filter(result => {
      const title = result.title.toLowerCase();
      const fullText = `${title} ${result.description.toLowerCase()}`;
      return (titleOnly.checked ? title : fullText).includes(phrase)
        && words.every(word => !fullText.includes(word));
    });
    if (sort.value === "title") matching.sort((a, b) => a.title.localeCompare(b.title));

    const nextSignature = JSON.stringify([results.length, matching]);
    if (nextSignature === signature) return;
    signature = nextSignature;
    status.textContent = results.length
      ? `${matching.length} matching · ${results.length} loaded scenarios`
      : "No recognizable scenario cards loaded. Use the AI Dungeon results below.";
    rows.replaceChildren(...matching.map(result => {
      const row = document.createElement("article");
      const link = document.createElement(result.url ? "a" : "button");
      if (result.url) link.href = result.url;
      else link.addEventListener("click", () => targets.get(result.label)?.click());
      link.textContent = result.title;
      const stats = document.createElement("p");
      stats.textContent = result.stats;
      row.append(link, stats);
      if (result.description) {
        const details = document.createElement("details");
        const summary = document.createElement("summary");
        summary.textContent = "Description";
        const description = document.createElement("p");
        description.textContent = result.description;
        details.append(summary, description);
        row.append(details);
      }
      return row;
    }));
  }

  let pending;
  function scheduleRefresh() {
    if (pending !== undefined) return;
    pending = setTimeout(() => {
      pending = undefined;
      refresh();
    }, 150);
  }

  function requestMore(manual = false) {
    if (!lastCard?.isConnected) return;
    if (!manual && (requestedCard === lastCard || Date.now() - lastRequest < 750)) return;
    requestedCard = lastCard;
    lastRequest = Date.now();
    // Move the official results to their loading edge while the overlay keeps its own scroll position.
    lastCard.scrollIntoView({ block: "end", inline: "nearest" });
  }

  workspace.addEventListener("click", () => {
    const open = panel.toggleAttribute("workspace");
    workspace.textContent = open ? "Back to AI Dungeon" : "Open Masquerade";
    lastScrollTop = scroller.scrollTop;
  });
  more.addEventListener("click", () => requestMore(true));
  scroller.addEventListener("scroll", () => {
    const scrollingDown = scroller.scrollTop > lastScrollTop;
    lastScrollTop = scroller.scrollTop;
    if (panel.hasAttribute("workspace") && scrollingDown &&
        scroller.scrollHeight - scroller.clientHeight - scroller.scrollTop < 320) {
      requestMore();
    }
  });
  root.addEventListener("input", refresh);
  new MutationObserver(scheduleRefresh).observe(document.body, {
    childList: true,
    subtree: true,
    characterData: true,
    attributes: true,
    attributeFilter: ["aria-label", "href"],
  });
  window.addEventListener("popstate", scheduleRefresh);
  refresh();
})();




