import { rocket } from "datastar"

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
