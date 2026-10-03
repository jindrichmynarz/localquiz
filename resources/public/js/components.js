import { rocket } from "datastar"
import Sortable from "https://cdn.jsdelivr.net/npm/sortablejs/+esm"

rocket("copy-button", {
  mode: "light",
  props: ({ string }) => ({
    text: string,
    copyLabel: string,
    copiedLabel: string,
  }),
  setup: ({ $$, cleanup, host, props }) => {
    $$.copied = false
    let timer = 0

    async function onClick() {
      await navigator.clipboard.writeText(props.text)
      $$.copied = true
      clearTimeout(timer)
      timer = setTimeout(() => $$.copied = false, 2000)
    }

    host.addEventListener("click", onClick)
    cleanup(() => {
      clearTimeout(timer)
      host.removeEventListener("click", onClick)
    })
  },
  render: ({ html, props: { copyLabel, copiedLabel } }) => html`
    <button class="btn" type="button">${copyLabel}</button>
    <span class="copy-popover" data-show="$$copied">${copiedLabel}</span>
  `,
})

// A list of items to put in order by dragging. Items are {value, label}. After each drag,
// it dispatches a bubbling "reordered" event with the items' values in their new order.
rocket("sortable-list", {
  mode: "light",
  props: ({ json }) => ({
    items: json.default([]),
  }),
  onFirstRender: ({ cleanup, host }) => {
    const sortable = new Sortable(host.querySelector("ul"), {
      animation: 150,
      ghostClass: "sortable-ghost",
      onEnd: (evt) => host.dispatchEvent(
        new CustomEvent("reordered", {
          detail: [...evt.from.children].map(li => JSON.parse(li.dataset.value)),
          bubbles: true,
        })
      ),
    })
    cleanup(() => sortable.destroy())
  },
  render: ({ html, props: { items } }) => html`
    <ul>
      ${items.map(({ value, label }) => html`
        <li data-value=${JSON.stringify(value)}>
          <span>${label}</span>
          <i class="material-icons">
            <svg xmlns="http://www.w3.org/2000/svg" height="24px" viewBox="0 0 24 24" width="24px" fill="currentColor"><path d="M11 18c0 1.1-.9 2-2 2s-2-.9-2-2 .9-2 2-2 2 .9 2 2zm-2-8c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0-6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm6 4c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/></svg>
          </i>
        </li>
      `)}
    </ul>
  `,
})

// <network-map>: progressive disclosure of the network of a :network question,
// which views/network.clj passes in. Each view is laid out afresh for the screen,
// around the directions in the network's radial layout that network.clj computes.
// The block scopes its constants and helpers to the component.
{
  // A view's nodes sit on a circle that keeps at least this far from the sides of
  // the screen, leaving room for labels facing outward, and from its top and
  // bottom, whichever of the two leaves it smaller.
  // ponytail: hand-tuned on a portrait phone.
  const SIDE_MARGIN = 60;
  const END_MARGIN = 36;
  // Widest gap between neighbours on the circle, so that a few nodes stay near
  // the directions they lie in rather than spreading around it.
  const MAX_GAP = 120;
  // Below this gap, every other node moves onto an inner circle this much smaller.
  const MIN_GAP = 44;
  const INNER = 0.6;
  // How long nodes take to move to their places in a new view.
  const GLIDE_MS = 400;
  const TAU = 2 * Math.PI;
  const NODE_RADIUS = 8;
  // 44 px across, the usual minimum for touch.
  const TARGET_RADIUS = 22;
  const LABEL_OFFSET = 12;
  const LABEL_HEIGHT = 19;
  const CHAR_WIDTH = 8.9;
  // How far pinching and the wheel may zoom in, and how far a pointer may move
  // before a tap becomes a drag.
  const GESTURE_MAX_ZOOM = 5;
  // How far a view may zoom in to fill the screen, when what it frames leaves much of
  // the circle empty, as a few nodes kept near their directions do.
  const FIT_MAX_ZOOM = 2;
  const DRAG_THRESHOLD = 6;
  // Distance of pinned parents from the edge of the screen, and of the focus, which a pan
  // keeps on screen so that the map cannot be dragged away.
  const PIN_INSET = 12;
  // How long the hint that the map pans stays, unheeded.
  const HINT_MS = 8000;
  const SVG = "http://www.w3.org/2000/svg";

  // Which way the turn a -> b -> c bends: 1, -1, or 0 if it doesn't.
  const ccw = ([ax, ay], [bx, by], [cx, cy]) => Math.sign((by - ay) * (cx - ax) - (bx - ax) * (cy - ay));

  // How many pairs of `edges` cross, drawn straight between the points `at` gives.
  function crossings(edges, at) {
    let n = 0;
    for (let i = 0; i < edges.length; i++) {
      for (let j = i + 1; j < edges.length; j++) {
        const [a, b, c, d] = [edges[i].from, edges[i].to, edges[j].from, edges[j].to];
        if (a === c || a === d || b === c || b === d) continue;
        const [p, q, r, t] = [a, b, c, d].map(at);
        n += ccw(p, q, r) !== ccw(p, q, t) && ccw(r, t, p) !== ccw(r, t, q);
      }
    }
    return n;
  }

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
        // Where the node is in the network's layout.
        gx: parseFloat(el.dataset.x),
        gy: parseFloat(el.dataset.y),
        // Where it goes in the view, and where it is drawn while it moves there.
        x: 0,
        y: 0,
        px: undefined,
        py: undefined,
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
    // Nothing else says that the map pans, there being no cursor on touch, so a hint shows
    // until it is heeded, it times out, or it was heeded on an earlier question.
    const hint = container.querySelector(".nm-hint");
    const hide = () => hint.classList.add("nm-gone");
    const panned = () => {
      sessionStorage.nmPanned = 1;
      hide();
    };
    if (sessionStorage.nmPanned) hide();
    else setTimeout(hide, HINT_MS);
    const degree = (id) => nodes.get(id).children.length + nodes.get(id).parents.length;
    const byDegree = (a, b) => degree(b) - degree(a) || (a < b ? -1 : a > b ? 1 : 0);

    // Where the nodes and pins on screen were last drawn, as {id, x, y}.
    let drawn = [];
    // What is shown, what a tap framed, and the view, a screen point being
    // [dx + zoom * x, dy + zoom * y]. Gestures change only the view.
    let shown = new Set();
    let frame = [];
    let view = null;

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
      const vertical = side === "above" || side === "below";
      const x = vertical ? 0 : side === "left" ? -LABEL_OFFSET : LABEL_OFFSET;
      // Beside the node, the lines are centred on it. Above or below it, they stack
      // from the top of their block, each baseline 0.95em into its 1.2em line.
      const top = { above: -LABEL_OFFSET - LABEL_HEIGHT * lines.length, below: LABEL_OFFSET }[side];
      const first = vertical ? "0.95em" : `${0.35 - 0.6 * (lines.length - 1)}em`;
      text.removeAttribute("display");
      text.setAttribute("x", x);
      text.setAttribute("text-anchor", vertical ? "middle" : side === "left" ? "end" : "start");
      if (vertical) text.setAttribute("y", top);
      else text.removeAttribute("y");
      if (lines.length === 1) {
        text.setAttribute("dy", first);
        text.textContent = lines[0];
      } else {
        // Lines 1.2em apart.
        text.removeAttribute("dy");
        text.replaceChildren(
          ...lines.map((line, i) => {
            const tspan = document.createElementNS(SVG, "tspan");
            tspan.setAttribute("x", x);
            tspan.setAttribute("dy", i ? "1.2em" : first);
            tspan.textContent = line;
            return tspan;
          }),
        );
      }
    }

    // Every label of `items` ({id, x, y, text, left}, in screen coordinates) shows.
    // It goes on one line on its preferred side, else the other side, else above or
    // below, outward first, which is where room is left at the top and the bottom
    // of a crowded circle, its neighbours being beside it. Else wrapped to ever more
    // lines, a word per line at most, on any side, whichever first stays on screen
    // and clear of the circles and of the labels placed before. Failing that, the
    // one that overlaps least.
    function placeLabels(items, width, height) {
      const circles = items.map(({ id, x, y }) => (
        { id, l: x - NODE_RADIUS, r: x + NODE_RADIUS, t: y - NODE_RADIUS, b: y + NODE_RADIUS }
      ));
      const placed = [];
      for (const { id, x, y, text, left } of items) {
        const layouts = id.split(" ").map((_, i) => wrap(id, i + 1));
        const sides = [
          ...(left ? ["left", "right"] : ["right", "left"]),
          ...(y < height / 2 ? ["above", "below"] : ["below", "above"]),
        ];
        const candidates = layouts.flatMap((lines) =>
          sides.map((side) => {
            const w = CHAR_WIDTH * longest(lines);
            const h = LABEL_HEIGHT * lines.length;
            const l = { left: x - LABEL_OFFSET - w, right: x + LABEL_OFFSET }[side] ?? x - w / 2;
            const t = { above: y - LABEL_OFFSET - h, below: y + LABEL_OFFSET }[side] ?? y - h / 2;
            const box = { side, lines, l, r: l + w, t, b: t + h };
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
    // Their labels face the middle. Where pins would pile up, the one nearest the
    // focus along the trail shows.
    function pinOffScreen(at, width, height) {
      const inside = ([x, y]) =>
        x >= PIN_INSET && x <= width - PIN_INSET && y >= PIN_INSET && y <= height - PIN_INSET;
      pins.replaceChildren();
      const away = new Set([...nodes.get(focus).parents, ...[...trail].reverse()]);
      away.delete(focus);
      const placed = [];
      return [...away].flatMap((id) => {
        const [px, py] = at(id);
        if (px >= 0 && px <= width && py >= 0 && py <= height) return [];
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
        if (placed.some(([qx, qy]) => Math.hypot(qx - x, qy - y) < 2 * TARGET_RADIUS)) return [];
        placed.push([x, y]);
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
        // A chevron in the hollow, pointing on along the edge, off screen, so that the
        // pin reads as the way to a node out of view rather than as a node of its own.
        const chevron = document.createElementNS(SVG, "path");
        chevron.setAttribute("class", "nm-chevron");
        chevron.setAttribute("d", "M -1.5 -3 L 1.5 0 L -1.5 3");
        chevron.setAttribute("transform", `rotate(${(Math.atan2(dy, dx) * 180) / Math.PI})`);
        pin.appendChild(chevron);
        const text = document.createElementNS(SVG, "text");
        pin.appendChild(text);
        pins.appendChild(pin);
        return [{ id, x, y, text, left: x > width / 2 }];
      });
    }

    // Places the shown nodes for the view: the focus in the middle, and its
    // parents and, while open, its children around a circle that fills the screen,
    // each in the direction it lies from the focus in the network's layout and
    // spread apart where they crowd. The rest of the trail goes beyond, off
    // screen, where it is pinned.
    function arrange(width, height) {
      const f = nodes.get(focus);
      const [cx, cy] = [width / 2, height / 2];
      const r = Math.max(1, Math.min(cx - SIDE_MARGIN, cy - END_MARGIN));
      Object.assign(f, { x: cx, y: cy });
      const direction = (id) => Math.atan2(nodes.get(id).gy - f.gy, nodes.get(id).gx - f.gx);
      // Places go by arc length, which on a circle is the angle times the radius.
      const perimeter = TAU * r;
      // How far along the circle the ray at `angle` from the middle meets it.
      const along = (angle) => (((angle % TAU) + TAU) % TAU) * r;
      // The point that far along the circle, scaled by `scale` from the middle.
      const point = (s, scale) => [cx + scale * r * Math.cos(s / r), cy + scale * r * Math.sin(s / r)];
      const ring = [...new Set([...f.parents, ...(open ? f.children : [])])];
      const items = ring
        .map((id) => ({ id, s: along(direction(id)) }))
        .sort((p, q) => p.s - q.s || (p.id < q.id ? -1 : 1));
      const n = items.length;
      const gap = Math.min(MAX_GAP, perimeter / Math.max(1, n));
      if (n * MAX_GAP >= perimeter) {
        // As many as fill the circle: evenly spaced, turned to sit nearest their places.
        const turn = Math.atan2(
          ...[Math.sin, Math.cos].map((trig) =>
            items.reduce((sum, { s }, i) => sum + trig(((s - i * gap) / perimeter) * TAU), 0)),
        );
        items.forEach((item, i) => (item.s = (turn / TAU) * perimeter + i * gap));
      } else {
        // Fewer: neighbours pushed apart, in order, until they are `gap` apart.
        for (let round = 0, moved = n > 1; round < 100 && moved; round++) {
          moved = false;
          for (let i = 0; i < n; i++) {
            const [p, q] = [items[i], items[(i + 1) % n]];
            const d = q.s - p.s + (i === n - 1 ? perimeter : 0);
            if (d < gap - 0.5) {
              p.s -= (gap - d) / 2;
              q.s += (gap - d) / 2;
              moved = true;
            }
          }
        }
      }
      const crowded = gap < MIN_GAP;
      const slots = items.map(({ s }, i) => point(s, crowded && i % 2 ? INNER : 1));
      // Nodes move to other places on the circle, the rest shifting along, while
      // that uncrosses the edges among the focus and the nodes around it.
      const local = edges.filter(({ from, to }) =>
        [from, to].every((id) => id === focus || ring.includes(id)));
      const count = (order) => {
        const placed = new Map([[focus, [cx, cy]], ...order.map((id, i) => [id, slots[i]])]);
        return crossings(local, (id) => placed.get(id));
      };
      let order = items.map(({ id }) => id);
      let least = local.length > 1 ? count(order) : 0;
      for (let improved = least > 0; improved; ) {
        improved = false;
        for (let i = 0; i < n && least > 0; i++) {
          for (let k = 0; k < n && least > 0; k++) {
            if (k === i) continue;
            const moved = [...order];
            moved.splice(k, 0, ...moved.splice(i, 1));
            const c = count(moved);
            if (c < least) {
              [order, least, improved] = [moved, c, true];
            }
          }
        }
      }
      order.forEach((id, i) => (items[i].id = id));
      items.forEach(({ id }, i) => {
        const [x, y] = slots[i];
        Object.assign(nodes.get(id), { x, y });
      });
      // The rest of the trail leads on from the parent it came through, outward,
      // so that its edges run off screen rather than across the view.
      const via = items.find(({ id }) => id === trail[trail.length - 2]);
      trail.slice(0, -2).forEach((id, i, rest) => {
        if (via && !ring.includes(id)) {
          const [x, y] = point(via.s, 3 + rest.length - i);
          Object.assign(nodes.get(id), { x, y });
        }
      });
    }

    // Moves the shown nodes and their edges to their places. Those shown before go
    // from where they were, the rest from where the focus was.
    let gliding = 0;
    function glide(was, width, height) {
      const f = nodes.get(focus);
      const origin = was.has(focus) ? [f.px, f.py] : [width / 2, height / 2];
      const start = new Map([...shown].map((id) => {
        const node = nodes.get(id);
        return [id, was.has(id) ? [node.px, node.py] : origin];
      }));
      const t0 = performance.now();
      const instant = matchMedia("(prefers-reduced-motion: reduce)").matches;
      cancelAnimationFrame(gliding);
      const step = (now) => {
        const t = instant ? 1 : Math.min(1, (now - t0) / GLIDE_MS);
        const eased = 1 - (1 - t) ** 3;
        for (const [id, [x0, y0]] of start) {
          const node = nodes.get(id);
          node.px = x0 + (node.x - x0) * eased;
          node.py = y0 + (node.y - y0) * eased;
          node.el.setAttribute("transform", `translate(${node.px} ${node.py})`);
        }
        for (const { el, from, to } of edges) {
          if (start.has(from) && start.has(to)) {
            el.setAttribute("x1", nodes.get(from).px);
            el.setAttribute("y1", nodes.get(from).py);
            el.setAttribute("x2", nodes.get(to).px);
            el.setAttribute("y2", nodes.get(to).py);
          }
        }
        if (t < 1) gliding = requestAnimationFrame(step);
      };
      step(t0);
    }

    // Shows what the focus reveals, laid out for the screen.
    function render() {
      const width = svg.clientWidth;
      const height = svg.clientHeight;
      if (!width || !height) return;
      const was = shown;
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
      arrange(width, height);
      // Gestures may zoom in from the view, and it fills the screen, so not out.
      view = fitted(width, height);
      svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
      glide(was, width, height);
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
      // Pins go first, so that the way back is labelled clearly.
      const pinned = pinOffScreen(at, width, height);
      const rest = [...shown].filter((id) => !frame.includes(id)).sort(byDegree);
      const onScreen = [...frame, ...rest].flatMap((id) => {
        const [x, y] = at(id);
        const node = nodes.get(id);
        return x >= 0 && x <= width && y >= 0 && y <= height
          ? [{ id, x, y, text: node.text, left: node.x < width / 2 }]
          : [];
      });
      // Labels not placed, off screen, would linger where they were.
      for (const id of shown) nodes.get(id).text.setAttribute("display", "none");
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

    // The view that fills the screen with what is framed, keeping the margins its
    // labels need, as far as FIT_MAX_ZOOM. Anything else shown that falls outside is
    // pinned to the edge, as after a pan.
    function fitted(width, height) {
      const xs = frame.map((id) => nodes.get(id).x);
      const ys = frame.map((id) => nodes.get(id).y);
      const [l, r, t, b] = [Math.min(...xs), Math.max(...xs), Math.min(...ys), Math.max(...ys)];
      // A lone node or a line of them has no extent, which leaves the cap to decide.
      const zoom = Math.max(1, Math.min(
        FIT_MAX_ZOOM,
        (width - 2 * SIDE_MARGIN) / (r - l),
        (height - 2 * END_MARGIN) / (b - t),
      ));
      return clamped({ zoom, dx: width / 2 - zoom * (l + r) / 2, dy: height / 2 - zoom * (t + b) / 2 });
    }

    // A view with the focus kept on screen, so that no gesture can take the map away.
    function clamped({ zoom, dx, dy }) {
      const f = nodes.get(focus);
      const on = (d, at, extent) =>
        Math.min(extent - PIN_INSET - zoom * at, Math.max(PIN_INSET - zoom * at, d));
      return {
        zoom,
        dx: on(dx, f.x, svg.clientWidth),
        dy: on(dy, f.y, svg.clientHeight),
      };
    }

    // Zooms by `factor` around the screen point [x, y], which stays put.
    function zoomAt([x, y], factor) {
      const zoom = Math.min(GESTURE_MAX_ZOOM, Math.max(1, view.zoom * factor));
      const scale = zoom / view.zoom;
      view = clamped({ zoom, dx: x - scale * (x - view.dx), dy: y - scale * (y - view.dy) });
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
        panned();
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
        view = clamped({ ...view, dx: view.dx + x - px, dy: view.dy + y - py });
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
      panned();
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
    const observer = new ResizeObserver(render);
    observer.observe(svg);
    return () => {
      observer.disconnect();
      cancelAnimationFrame(gliding);
    };
  }

  // `network` is {root, nodes: [{id, x, y}], edges: [[from, to]], choices: [{label,
  // description}]}, the choices being the nodes that can be picked. Dispatches a bubbling
  // `network-select` event with the label of each choice tapped, which the cell under the
  // map then shows. The hints say that the map pans, by touch and by pointer.
  rocket("network-map", {
    mode: "light",
    props: ({ json, string }) => ({
      network: json.default({ root: "", nodes: [], edges: [], choices: [] }),
      hint: string,
      hintPointer: string,
    }),
    onFirstRender: ({ cleanup, host }) => cleanup(createNetworkMap(host)),
    render: ({ html, svg, props: { network: { root, nodes, edges, choices }, hint, hintPointer } }) => {
      const choice = new Set(choices.map(({ label }) => label));
      return html`
        <svg data-root=${root}>
          <g class="nm-viewport">
            ${edges.map(([from, to]) => svg`<line class="nm-edge" data-from=${from} data-to=${to}></line>`)}
            ${nodes.map(({ id, x, y }) => svg`
              <g class=${["nm-node", id === root && "nm-root", choice.has(id) && "nm-choice"].filter(Boolean).join(" ")}
                 data-id=${id} data-x=${x} data-y=${y} tabindex="0" role="button" aria-label=${id}>
                <g class="nm-glyph">
                  <circle class="nm-target" r=${TARGET_RADIUS}></circle>
                  <circle r=${NODE_RADIUS}></circle>
                  <text>${id}</text>
                </g>
              </g>
            `)}
          </g>
        </svg>
        <div class="nm-hint" aria-hidden="true">
          <span class="nm-hint-touch">${hint}</span>
          <span class="nm-hint-pointer">${hintPointer}</span>
        </div>
        <div class="nm-info" hidden>
          ${choices.map(({ label, description }) => html`
            <div class="nm-chosen" data-for=${label} hidden>
              <strong>${label}</strong>
              ${description ? html`<div class="description">${description}</div>` : ""}
            </div>
          `)}
        </div>
      `;
    },
  });
}
