import Sortable from "https://cdn.jsdelivr.net/npm/sortablejs/+esm"

if (sortableList) {
  new Sortable(sortableList, {
      animation: 150,
      ghostClass: "sortable-ghost",
      onEnd: (evt) => {
        sortableList.dispatchEvent(
          new CustomEvent(
            "reordered",
            {
              "detail": [...evt.from.querySelectorAll("li")].map(el => parseInt(el.dataset.index))
            }
          )
        )
      }
  })
}
