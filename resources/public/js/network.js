// Progressive disclosure of the network of a :network question, drawn by
// views/network.clj. The viewport and the label placement mirror those that
// network.clj's layout-report measures, so keep the two in step.

const PADDING = 24;
const NODE_RADIUS = 8;
// 44 px across, the usual minimum for touch.
const TARGET_RADIUS = 22;
const LABEL_OFFSET = 12;
const LABEL_HEIGHT = 19;
const CHAR_WIDTH = 8.9;
const MAX_ZOOM = 1.6;
// How far pinching and the wheel may zoom in, and how far a pointer may move
// before a tap becomes a drag.
const GESTURE_MAX_ZOOM = 5;
const DRAG_THRESHOLD = 6;
// Distance of pinned parents from the edge of the screen.
const PIN_INSET = 12;
const SVG = "http://www.w3.org/2000/svg";

const overlapArea = (a, b) =>
  Math.max(0, Math.min(a.r, b.r) - Math.max(a.l, b.l)) *
  Math.max(0, Math.min(a.b, b.b) - Math.max(a.t, b.t));

const longest = (lines) => Math.max(...lines.map((line) => line.length));

// `label` wrapped to `n` lines between its words, so that the longest line is
// as short as can be.
function wrap(label, n) {
  const words = label.split(" ");
  let best = null;
  const split = (start, lines) => {
    if (lines.length === n - 1) {
      const all = [...lines, words.slice(start).join(" ")];
      if (!best || longest(all) < longest(best)) best = all;
      return;
    }
    for (let end = start + 1; end <= words.length - (n - 1 - lines.length); end++) {
      split(end, [...lines, words.slice(start, end).join(" ")]);
    }
  };
  split(0, []);
  return best;
}

function createNetworkMap(container) {
  const svg = container.querySelector("svg");
  const viewport = svg.querySelector(".nm-viewport");
  // In screen coordinates, so outside the viewport that zooms.
  const pins = svg.appendChild(document.createElementNS(SVG, "g"));
  pins.setAttribute("class", "nm-pins");
  const root = svg.dataset.root;
  const nodes = new Map();
  for (const el of svg.querySelectorAll(".nm-node")) {
    nodes.set(el.dataset.id, {
      el,
      text: el.querySelector("text"),
      x: parseFloat(el.dataset.x),
      y: parseFloat(el.dataset.y),
      children: [],
      parents: [],
    });
  }
  const edges = [...svg.querySelectorAll(".nm-edge")].map((el) => {
    const edge = { el, from: el.dataset.from, to: el.dataset.to };
    nodes.get(edge.from).children.push(edge.to);
    nodes.get(edge.to).parents.push(edge.from);
    return edge;
  });
  // The cell under the drawing, hidden until a choice is chosen, then showing it.
  const info = container.querySelector(".nm-info");
  const chosen = new Map(
    [...container.querySelectorAll(".nm-chosen")].map((el) => [el.dataset.for, el]),
  );
  const degree = (id) => nodes.get(id).children.length + nodes.get(id).parents.length;
  const byDegree = (a, b) => degree(b) - degree(a) || (a < b ? -1 : a > b ? 1 : 0);

  // Where the nodes and pins on screen were last drawn, as {id, x, y}.
  let drawn = [];
  // What is shown, what a tap framed, and the view, a screen point being
  // [dx + zoom * x, dy + zoom * y]. Gestures change only the view.
  let shown = new Set();
  let frame = [];
  let view = null;
  let minZoom = 0;

  let focus = root;
  // Whether the focus shows its children.
  let open = true;
  let selected = null;
  // The nodes from the root to the focus, as navigated, so the way back shows.
  let trail = [root];

  // The trail shows, with the focus's parents and, while open, its children.
  function visible() {
    const { children, parents } = nodes.get(focus);
    return new Set([...trail, ...parents, ...(open ? children : [])]);
  }

  // A shortest path from the root down to `id`.
  function pathTo(id) {
    const previous = new Map([[root, null]]);
    const queue = [root];
    while (queue.length && !previous.has(id)) {
      const node = queue.shift();
      for (const child of nodes.get(node).children) {
        if (!previous.has(child)) {
          previous.set(child, node);
          queue.push(child);
        }
      }
    }
    const path = [];
    for (let node = id; node !== null; node = previous.get(node)) path.unshift(node);
    return path;
  }

  // The focus with its children if it is open and has some, else its parents.
  function framed() {
    const { children, parents } = nodes.get(focus);
    const around = open && children.length ? children : parents;
    return [focus, ...[...around].sort(byDegree)];
  }

  function setLabel(text, side, lines) {
    const x = side === "left" ? -LABEL_OFFSET : LABEL_OFFSET;
    text.setAttribute("x", x);
    text.setAttribute("text-anchor", side === "left" ? "end" : "start");
    if (lines.length === 1) {
      text.setAttribute("dy", "0.35em");
      text.textContent = lines[0];
    } else {
      // Lines 1.2em apart, centred on the node.
      text.removeAttribute("dy");
      text.replaceChildren(
        ...lines.map((line, i) => {
          const tspan = document.createElementNS(SVG, "tspan");
          tspan.setAttribute("x", x);
          tspan.setAttribute("dy", i ? "1.2em" : `${0.35 - 0.6 * (lines.length - 1)}em`);
          tspan.textContent = line;
          return tspan;
        }),
      );
    }
  }

  // Every label of `items` ({id, x, y, text, left}, in screen coordinates) shows.
  // It goes on one line on its preferred side, else the other side, else wrapped
  // to ever more lines, a word per line at most, on either, whichever first stays
  // on screen and clear of the circles and of the labels placed before. Failing
  // that, the one that overlaps least.
  function placeLabels(items, width, height) {
    const circles = items.map(({ id, x, y }) => (
      { id, l: x - NODE_RADIUS, r: x + NODE_RADIUS, t: y - NODE_RADIUS, b: y + NODE_RADIUS }
    ));
    const placed = [];
    for (const { id, x, y, text, left } of items) {
      const layouts = id.split(" ").map((_, i) => wrap(id, i + 1));
      const sides = left ? ["left", "right"] : ["right", "left"];
      const candidates = layouts.flatMap((lines) =>
        sides.map((side) => {
          const w = CHAR_WIDTH * longest(lines);
          const h = LABEL_HEIGHT * lines.length;
          const l = side === "left" ? x - LABEL_OFFSET - w : x + LABEL_OFFSET;
          const box = { side, lines, l, r: l + w, t: y - h / 2, b: y + h / 2 };
          box.cost =
            w * h - overlapArea(box, { l: 0, r: width, t: 0, b: height }) +
            placed.reduce((sum, other) => sum + overlapArea(box, other), 0) +
            circles.reduce((sum, circle) => sum + (circle.id === id ? 0 : overlapArea(box, circle)), 0);
          return box;
        }),
      );
      // Under a square pixel is rounding error, not overlap.
      const best = candidates.find((box) => box.cost < 1) ??
        candidates.reduce((best, box) => (box.cost < best.cost ? box : best));
      placed.push(best);
      setLabel(text, best.side, best.lines);
    }
  }

  // The trail and the parents of the focus that lie off screen, each pinned where
  // the line towards it leaves the screen, so that the way back stays in reach.
  // Their labels face the middle.
  function pinOffScreen(at, width, height) {
    const inside = ([x, y]) =>
      x >= PIN_INSET && x <= width - PIN_INSET && y >= PIN_INSET && y <= height - PIN_INSET;
    pins.replaceChildren();
    const away = new Set([...trail, ...nodes.get(focus).parents]);
    away.delete(focus);
    return [...away].flatMap((id) => {
      const [px, py] = at(id);
      if (inside([px, py])) return [];
      // Aim from the next node on the trail that is on screen, so that the pin
      // sits on the edge that leads to it, else from the focus, else from the
      // middle of the screen, when panning has taken the focus away.
      const i = trail.indexOf(id);
      const from = (i === -1 ? [focus] : trail.slice(i + 1)).map(at).find(inside) ??
        (inside(at(focus)) ? at(focus) : [width / 2, height / 2]);
      const [fx, fy] = from;
      const [dx, dy] = [px - fx, py - fy];
      // How far along the edge it leaves the inset screen.
      const t = Math.min(
        px < PIN_INSET ? (PIN_INSET - fx) / dx : 1,
        px > width - PIN_INSET ? (width - PIN_INSET - fx) / dx : 1,
        py < PIN_INSET ? (PIN_INSET - fy) / dy : 1,
        py > height - PIN_INSET ? (height - PIN_INSET - fy) / dy : 1,
      );
      const [x, y] = [fx + t * dx, fy + t * dy];
      const pin = document.createElementNS(SVG, "g");
      pin.setAttribute("class", "nm-pin");
      pin.setAttribute("data-id", id);
      pin.setAttribute("transform", `translate(${x} ${y})`);
      pin.setAttribute("tabindex", 0);
      pin.setAttribute("role", "button");
      pin.setAttribute("aria-label", id);
      for (const r of [TARGET_RADIUS, NODE_RADIUS]) {
        const circle = document.createElementNS(SVG, "circle");
        circle.setAttribute("r", r);
        if (r === TARGET_RADIUS) circle.setAttribute("class", "nm-target");
        pin.appendChild(circle);
      }
      const text = document.createElementNS(SVG, "text");
      pin.appendChild(text);
      pins.appendChild(pin);
      return [{ id, x, y, text, left: x > width / 2 }];
    });
  }

  // Fits `ids` on screen, as a view.
  function fit(ids, width, height, maxZoom) {
    const xs = ids.map((id) => nodes.get(id).x);
    const ys = ids.map((id) => nodes.get(id).y);
    const [x0, x1, y0, y1] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)];
    const zoom = Math.min(
      maxZoom,
      (width - 2 * PADDING) / Math.max(1e-9, x1 - x0),
      (height - 2 * PADDING) / Math.max(1e-9, y1 - y0),
    );
    return { zoom, dx: width / 2 - (zoom * (x0 + x1)) / 2, dy: height / 2 - (zoom * (y0 + y1)) / 2 };
  }

  // Shows what the focus reveals, fitted to its neighbourhood.
  function render() {
    const width = svg.clientWidth;
    const height = svg.clientHeight;
    if (!width || !height) return;
    shown = visible();
    for (const [id, node] of nodes) {
      node.el.classList.toggle("nm-hidden", !shown.has(id));
      node.el.classList.toggle("nm-focus", id === focus);
      node.el.classList.toggle("nm-selected", id === selected);
      node.el.classList.toggle("nm-trail", trail.includes(id));
    }
    const onTrail = (edge) => trail.some((id, i) => id === edge.from && trail[i + 1] === edge.to);
    for (const edge of edges) {
      edge.el.classList.toggle("nm-hidden", !(shown.has(edge.from) && shown.has(edge.to)));
      edge.el.classList.toggle("nm-adjacent", edge.from === focus || edge.to === focus);
      edge.el.classList.toggle("nm-trail", onTrail(edge));
    }
    frame = framed();
    view = fit(frame, width, height, MAX_ZOOM);
    // Zooming out stops at the whole network.
    minZoom = Math.min(view.zoom, fit([...nodes.keys()], width, height, MAX_ZOOM).zoom);
    svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
    draw();
    svg.classList.add("nm-ready");
  }

  // Draws the view, with pins and labels placed for it.
  function draw() {
    const width = svg.clientWidth;
    const height = svg.clientHeight;
    const { zoom, dx, dy } = view;
    svg.style.setProperty("--k", zoom);
    viewport.style.transform = `translate(${dx}px, ${dy}px) scale(${zoom})`;

    const at = (id) => [dx + zoom * nodes.get(id).x, dy + zoom * nodes.get(id).y];
    const rest = [...shown].filter((id) => !frame.includes(id)).sort(byDegree);
    const onScreen = [...frame, ...rest].flatMap((id) => {
      const [x, y] = at(id);
      const node = nodes.get(id);
      return x >= 0 && x <= width && y >= 0 && y <= height
        ? [{ id, x, y, text: node.text, left: node.x < 0 }]
        : [];
    });
    // Pins go first, so that the way back is labelled clearly.
    const pinned = pinOffScreen(at, width, height);
    placeLabels([...pinned, ...onScreen], width, height);
    drawn = [...pinned, ...onScreen];
  }

  let drawing = false;
  function redraw() {
    if (drawing) return;
    drawing = true;
    requestAnimationFrame(() => {
      drawing = false;
      draw();
    });
  }

  // Zooms by `factor` around the screen point [x, y], which stays put.
  function zoomAt([x, y], factor) {
    const zoom = Math.min(GESTURE_MAX_ZOOM, Math.max(minZoom, view.zoom * factor));
    const scale = zoom / view.zoom;
    view = { zoom, dx: x - scale * (x - view.dx), dy: y - scale * (y - view.dy) };
  }

  // Tapping a node focuses and opens it, and selects it if it is a choice.
  // Tapping the focus again closes or opens it. Going back along the trail cuts
  // it, going down to a child extends it, and going anywhere else takes the
  // shortest path from the root.
  function activate(id) {
    if (id === focus) {
      open = !open;
    } else {
      const back = trail.indexOf(id);
      if (back !== -1) trail = trail.slice(0, back + 1);
      else if (nodes.get(focus).children.includes(id)) trail = [...trail, id];
      else trail = pathTo(id);
      focus = id;
      open = true;
      if (nodes.get(id).el.classList.contains("nm-choice")) {
        selected = id;
        info.hidden = false;
        for (const [label, el] of chosen) el.hidden = label !== id;
        container.dispatchEvent(new CustomEvent("network-select", { detail: id, bubbles: true }));
      }
    }
    render();
  }

  // Where `evt` happened on screen. The drawing starts inside the border.
  function local(evt) {
    const box = svg.getBoundingClientRect();
    return [evt.clientX - box.left - svg.clientLeft, evt.clientY - box.top - svg.clientTop];
  }

  // The node or pin nearest to a tap, within reach of its target. Targets
  // overlap where nodes crowd, and the one on top need not be the nearest.
  function nearest(evt) {
    const [x, y] = local(evt);
    let best = null;
    for (const item of drawn) {
      const d = Math.hypot(item.x - x, item.y - y);
      if (d <= TARGET_RADIUS && (!best || d < best.d)) best = { ...item, d };
    }
    return best;
  }

  function press(evt) {
    // A click that ends a drag or a pinch is no tap.
    if (evt.type === "click" && dragged) {
      dragged = false;
      return false;
    }
    // A click from the keyboard has no pointer position, so it goes by its target.
    const hit = evt.type === "click" && evt.detail ? nearest(evt) : null;
    if (hit) {
      activate(hit.id);
      return true;
    }
    const el = evt.target.closest(".nm-node, .nm-pin");
    if (!el || el.classList.contains("nm-hidden")) return false;
    activate(el.dataset.id);
    return true;
  }
  // Dragging pans and pinching zooms, both without transitions, which would lag.
  const pointers = new Map();
  let start = null;
  let dragged = false;
  const middle = () => {
    const [a, b] = [...pointers.values()];
    return [[(a[0] + b[0]) / 2, (a[1] + b[1]) / 2], Math.hypot(a[0] - b[0], a[1] - b[1])];
  };
  svg.addEventListener("pointerdown", (evt) => {
    pointers.set(evt.pointerId, local(evt));
    if (pointers.size === 1) {
      start = local(evt);
      dragged = false;
    }
  });
  svg.addEventListener("pointermove", (evt) => {
    if (!pointers.has(evt.pointerId)) return;
    const [x, y] = local(evt);
    if (!dragged && Math.hypot(x - start[0], y - start[1]) < DRAG_THRESHOLD && pointers.size === 1) return;
    if (!dragged) {
      dragged = true;
      svg.classList.add("nm-panning");
      svg.setPointerCapture(evt.pointerId);
    }
    if (pointers.size === 2) {
      const [before, spread] = middle();
      pointers.set(evt.pointerId, [x, y]);
      const [after, spreadAfter] = middle();
      view = { ...view, dx: view.dx + after[0] - before[0], dy: view.dy + after[1] - before[1] };
      zoomAt(after, spreadAfter / Math.max(1, spread));
    } else {
      const [px, py] = pointers.get(evt.pointerId);
      pointers.set(evt.pointerId, [x, y]);
      view = { ...view, dx: view.dx + x - px, dy: view.dy + y - py };
    }
    redraw();
  });
  const release = (evt) => {
    pointers.delete(evt.pointerId);
    if (!pointers.size) svg.classList.remove("nm-panning");
  };
  svg.addEventListener("pointerup", release);
  svg.addEventListener("pointercancel", release);
  svg.addEventListener("wheel", (evt) => {
    evt.preventDefault();
    svg.classList.add("nm-panning");
    zoomAt(local(evt), Math.exp(-evt.deltaY * 0.002));
    redraw();
    clearTimeout(wheeling);
    wheeling = setTimeout(() => svg.classList.remove("nm-panning"), 200);
  }, { passive: false });
  let wheeling = null;
  svg.addEventListener("click", press);
  svg.addEventListener("keydown", (evt) => {
    if ((evt.key === "Enter" || evt.key === " ") && press(evt)) evt.preventDefault();
  });
  // Also renders the first time, once the SVG has a size.
  new ResizeObserver(render).observe(svg);
}

window.createNetworkMap = createNetworkMap;
