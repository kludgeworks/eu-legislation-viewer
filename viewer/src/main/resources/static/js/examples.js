// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file Turns the examples of the API's documentation (catalogue/api.jte) into cards.
 *
 * An example is a `figure.api-example`: a caption with the request, then a `pre[data-tab]` for
 * each of the response's body, its headers and the request as curl. Without this module, the card
 * shows all three, one under the other. With it:
 *
 * - the three are tabs, and the first one shows;
 * - the caption has a button that copies the curl command;
 * - the body's repetitive parts are folded, each behind a button that says what it holds: the
 *   links every answer has (`self`, `up`, `curies`), the items of a list after the first, and
 *   what is nested deeper than {@link DEPTH};
 * - highlight.js, which the page loads before this module with its grammar of HTTP, colours the
 *   headers and the command, and this module colours the body with highlight.js's class names.
 *   site.css gives them the theme's colours.
 */

/** @type {{ highlightElement(element: HTMLElement): void } | undefined} */
const hljs = /** @type {any} */ (globalThis).hljs;

/** The links that every answer has. A body shows them folded. */
const ROUTINE_LINKS = new Set(["self", "up", "curies"]);

/** How deep a body shows its objects and arrays. Deeper, they are folded. */
const DEPTH = 6;

/** The number of cards so far, to give each tab an id of its own. */
let cards = 0;

for (const example of document.querySelectorAll("figure.api-example")) {
  card(/** @type {HTMLElement} */ (example));
}

/** @param {HTMLElement} example */
function card(example) {
  cards += 1;
  const json = example.querySelector("code.language-json");
  if (json) {
    body(/** @type {HTMLElement} */ (json));
  }
  for (const code of example.querySelectorAll("code.language-http, code.language-bash")) {
    hljs?.highlightElement(/** @type {HTMLElement} */ (code));
  }
  tabs(example);
  copy(example);
}

/**
 * Makes a tab of each `pre[data-tab]`, and shows the first.
 *
 * @param {HTMLElement} example
 */
function tabs(example) {
  const panels = /** @type {HTMLElement[]} */ ([...example.querySelectorAll(":scope > pre[data-tab]")]);
  const list = document.createElement("div");
  list.className = "api-tabs";
  list.setAttribute("role", "tablist");
  const buttons = panels.map((panel, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.id = `example-${cards}-tab-${index}`;
    button.textContent = panel.dataset.tab ?? "";
    button.setAttribute("role", "tab");
    button.setAttribute("aria-controls", (panel.id = `example-${cards}-${index}`));
    panel.setAttribute("role", "tabpanel");
    panel.setAttribute("aria-labelledby", button.id);
    panel.tabIndex = 0;
    button.addEventListener("click", () => select(index));
    button.addEventListener("keydown", (event) => {
      const step = { ArrowRight: 1, ArrowLeft: -1 }[event.key];
      if (step) {
        const next = (index + step + panels.length) % panels.length;
        select(next);
        buttons[next]?.focus();
      }
    });
    return button;
  });
  /** @param {number} selected */
  function select(selected) {
    panels.forEach((panel, index) => {
      panel.hidden = index !== selected;
      buttons[index]?.setAttribute("aria-selected", String(index === selected));
      buttons[index]?.setAttribute("tabindex", index === selected ? "0" : "-1");
    });
  }
  list.append(...buttons);
  panels[0]?.before(list);
  select(0);
}

/**
 * Adds a button to the caption that copies the curl command.
 *
 * @param {HTMLElement} example
 */
function copy(example) {
  const command = example.querySelector("code.language-bash");
  if (!command || !navigator.clipboard) {
    return;
  }
  const button = document.createElement("button");
  button.type = "button";
  button.className = "api-copy";
  button.textContent = "Copy curl";
  button.addEventListener("click", async () => {
    await navigator.clipboard.writeText(command.textContent ?? "");
    button.textContent = "Copied";
    setTimeout(() => (button.textContent = "Copy curl"), 2000);
  });
  example.querySelector("figcaption")?.append(button);
}

/**
 * Shows a JSON body indented, coloured, with its repetitive parts folded. A body that is not JSON
 * stays as it is.
 *
 * @param {HTMLElement} code
 */
function body(code) {
  const text = code.textContent ?? "";
  const parsed = parse(text);
  if (parsed !== undefined) {
    code.replaceChildren(value(parsed, "", 0, undefined));
  } else if (text.startsWith("event:")) {
    code.replaceChildren(events(text));
  }
}

/**
 * Shows a stream of server-sent events, each field on its line, and the JSON of a data field
 * indented.
 *
 * @param {string} text
 */
function events(text) {
  return fragment(
    ...text.split("\n").flatMap((line, index) => {
      const colon = line.indexOf(":");
      const name = line.slice(0, colon);
      const data = name === "data" ? parse(line.slice(colon + 1)) : undefined;
      return [
        index === 0 ? "" : "\n",
        colon < 0 ? line : fragment(token("hljs-attr", name), token("hljs-punctuation", ":")),
        colon < 0 ? "" : data === undefined ? line.slice(colon + 1) : value(data, "", 0, undefined),
      ];
    }),
  );
}

/**
 * Returns the JSON in `text`, or undefined if it is not JSON.
 *
 * @param {string} text
 * @returns {unknown}
 */
function parse(text) {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

/**
 * @param {unknown} json
 * @param {string} indent the indent of the line the value starts on
 * @param {number} depth
 * @param {string | undefined} key the member's name the value is the value of
 * @returns {Node}
 */
function value(json, indent, depth, key) {
  if (json === null || typeof json === "boolean") {
    return token("hljs-literal", String(json));
  }
  if (typeof json === "number") {
    return token("hljs-number", String(json));
  }
  if (typeof json === "string") {
    return token("hljs-string", JSON.stringify(json));
  }
  const array = Array.isArray(json);
  const entries = array
    ? json.map((item) => /** @type {[string | undefined, unknown]} */ ([undefined, item]))
    : Object.entries(/** @type {object} */ (json));
  const [open, close] = array ? ["[", "]"] : ["{", "}"];
  if (entries.length === 0) {
    return token("hljs-punctuation", `${open} ${close}`);
  }
  if (depth >= DEPTH) {
    return fold(`${open}…${entries.length}${close}`, "", () => structure(entries, open, close, indent, 0, key));
  }
  return structure(entries, open, close, indent, depth, key);
}

/**
 * Returns an object or an array, its members or items one to a line, and its repetitive ones in a
 * fold on the last line.
 *
 * @param {[string | undefined, unknown][]} entries
 * @param {string} open
 * @param {string} close
 * @param {string} indent
 * @param {number} depth
 * @param {string | undefined} key
 */
function structure(entries, open, close, indent, depth, key) {
  let shown = entries;
  /** @type {[string | undefined, unknown][]} */
  let folded = [];
  let label = "";
  if (key === "_links") {
    shown = entries.filter(([name]) => !ROUTINE_LINKS.has(name ?? ""));
    folded = entries.filter(([name]) => ROUTINE_LINKS.has(name ?? ""));
    label = folded.map(([name]) => name).join(", ");
  } else if (open === "[" && entries.length > 2 && entries.every(([, item]) => isObject(item))) {
    shown = entries.slice(0, 1);
    folded = entries.slice(1);
    label = `${folded.length} more`;
  }
  const inner = `${indent}  `;
  /** @param {[string | undefined, unknown]} entry */
  const line = ([name, item]) =>
    fragment(
      inner,
      ...(name === undefined ? [] : [token("hljs-attr", JSON.stringify(name)), token("hljs-punctuation", " : ")]),
      value(item, inner, depth + 1, name),
    );
  /** @type {Node[]} */
  const lines = shown.map(line);
  if (folded.length > 0) {
    lines.push(fold(label, inner, () => joined(folded.map(line))));
  }
  return fragment(token("hljs-punctuation", open), "\n", joined(lines), "\n", indent, token("hljs-punctuation", close));
}

/**
 * Returns a button that says what a fold holds, after `indent`. Pressed, the fold opens: the
 * button and its indent give way to what it holds.
 *
 * @param {string} label
 * @param {string} indent
 * @param {() => Node} content
 */
function fold(label, indent, content) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "api-fold";
  button.setAttribute("aria-label", `Show ${label}`);
  const arrow = document.createElement("span");
  arrow.setAttribute("aria-hidden", "true");
  arrow.textContent = "▸ ";
  button.append(arrow, label);
  const wrapper = document.createElement("span");
  wrapper.append(indent, button);
  button.addEventListener("click", () => wrapper.replaceWith(content()));
  return wrapper;
}

/** @param {unknown} json */
function isObject(json) {
  return typeof json === "object" && json !== null && !Array.isArray(json);
}

/**
 * @param {string} kind
 * @param {string} text
 */
function token(kind, text) {
  const span = document.createElement("span");
  span.className = kind;
  span.textContent = text;
  return span;
}

/** @param {...(Node | string)} parts */
function fragment(...parts) {
  const result = new DocumentFragment();
  result.append(...parts);
  return result;
}

/** @param {Node[]} lines the lines, joined by commas */
function joined(lines) {
  return fragment(...lines.flatMap((line, index) => (index === 0 ? [line] : [",\n", line])));
}
