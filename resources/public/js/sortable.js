import Sortable from "https://cdn.jsdelivr.net/npm/sortablejs/+esm"

function createSortableList(element) {
  if (element) {
    new Sortable(element, {
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
}

window.createSortableList = createSortableList;
