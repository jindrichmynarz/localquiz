function typeText(el, speed = 20) {
  const nodes = [];
  const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
  let node;
  while (node = walker.nextNode()) {
    if (node.textContent.trim()) {
      nodes.push({node, text: node.textContent});
      node.textContent = "";
    }
  }
  if (!nodes.length) return;
  let nodeIdx = 0, charIdx = 0;
  const id = setInterval(() => {
    if (nodeIdx >= nodes.length) { clearInterval(id); return; }
    const {node, text} = nodes[nodeIdx];
    node.textContent += text[charIdx++];
    if (charIdx >= text.length) { nodeIdx++; charIdx = 0; }
  }, speed);
}

window.typeText = typeText;
