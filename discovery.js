const scenarios = [
  { title: "Space Opera: The Glass Armada", creator: "OrbitScribe", actions: 18400, bookmarks: 92, description: "Command a splinter fleet negotiating passage through a shattered ringworld." },
  { title: "Space Opera Isekai", creator: "SignalWitch", actions: 2900, bookmarks: 21, description: "Wake up aboard a research vessel inside your favorite space opera." },
  { title: "The Quiet Frontier", creator: "OrbitScribe", actions: 36000, bookmarks: 157, description: "A space opera about the crew of a courier ship and the stations they keep alive." },
  { title: "Clockwork Harbor", creator: "PaperNavigator", actions: 1700, bookmarks: 28, description: "Solve a mystery in a canal city built around an enormous mechanical clock." },
];

function filterScenarios(entries, { query, exclude, titleOnly, sort }) {
  const phrase = query.trim().toLowerCase();
  const excludedWords = exclude.toLowerCase().split(/\s+/).filter(Boolean);
  const matches = entries.filter(({ title, description }) => {
    const text = `${title} ${description}`.toLowerCase();
    const searchText = titleOnly ? title.toLowerCase() : text;
    return searchText.includes(phrase) && !excludedWords.some(word => text.includes(word));
  });

  if (sort === "title") return matches.sort((a, b) => a.title.localeCompare(b.title));
  if (sort === "actions" || sort === "bookmarks") return matches.sort((a, b) => b[sort] - a[sort]);
  return matches;
}

if (typeof module !== "undefined") module.exports = { filterScenarios, scenarios };

if (typeof document !== "undefined") {
  const form = document.getElementById("filters");
  const query = document.getElementById("query");
  const exclude = document.getElementById("exclude");
  const titleOnly = document.getElementById("titleOnly");
  const sort = document.getElementById("sort");
  const results = document.getElementById("results");
  const count = document.getElementById("count");
  const official = document.getElementById("official");
  const numbers = new Intl.NumberFormat();

  const rows = new Map(scenarios.map(scenario => {
    const row = document.createElement("article");
    const heading = document.createElement("h2");
    heading.textContent = scenario.title;
    const meta = document.createElement("p");
    meta.className = "meta";
    meta.textContent = `${scenario.creator} · ${numbers.format(scenario.actions)} actions · ${numbers.format(scenario.bookmarks)} bookmarks`;
    const details = document.createElement("details");
    const summary = document.createElement("summary");
    summary.textContent = "Description";
    const description = document.createElement("p");
    description.textContent = scenario.description;
    details.append(summary, description);
    row.append(heading, meta, details);
    return [scenario, row];
  }));

  function render() {
    const matches = filterScenarios(scenarios, {
      query: query.value,
      exclude: exclude.value,
      titleOnly: titleOnly.checked,
      sort: sort.value,
    });
    results.replaceChildren(...matches.map(scenario => rows.get(scenario)));
    count.textContent = `${matches.length} matching · ${scenarios.length} sample scenarios`;
    const params = new URLSearchParams({ searchTerm: query.value.trim() });
    official.href = `https://play.aidungeon.com/search?${params}`;
  }

  form.addEventListener("input", render);
  form.addEventListener("submit", event => event.preventDefault());
  form.addEventListener("reset", event => {
    event.preventDefault();
    query.value = "";
    exclude.value = "";
    titleOnly.checked = false;
    sort.value = "original";
    render();
  });
  render();
}
