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
