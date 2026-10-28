// Progressive enhancement only: every page reads fine without this file.
document.documentElement.classList.add("js");
const here = location.pathname.split("/").pop() || "index.html";

// Entrances: parts of a tile rise once, staggered, as it scrolls into view.
const rise = document.querySelectorAll(".t h1, .t h2, .t h3, .t p, .t ul, .t pre, .helmets, .fact, .facts li, .go, .item, .mosaic a");
rise.forEach((el, n) => { el.classList.add("rise"); el.style.setProperty("--i", String(n % 3)); });
const io = new IntersectionObserver(es => es.forEach(e => {
  if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); }
}), { threshold: 0.15 });
rise.forEach(el => io.observe(el));

// Gallery: click a picture to see it full size.
const viewer = document.getElementById("viewer");
if (viewer) {
  document.querySelectorAll(".mosaic a").forEach(a => a.addEventListener("click", e => {
    e.preventDefault();
    const img = viewer.querySelector("img");
    img.src = a.href; img.alt = a.querySelector("img").alt;
    viewer.showModal();
  }));
  viewer.addEventListener("click", () => viewer.close());
}

// Tools: the loadout. Pick a tool or engine from the rack and the stage shows it.
const stage = document.getElementById("stage");
if (stage) {
  const items = [...document.querySelectorAll(".item")];
  const show = item => {
    items.forEach(i => i.setAttribute("aria-pressed", String(i === item)));
    const name = item.querySelector("h3").textContent;
    const text = item.querySelector("p").textContent;
    const pic = item.dataset.photo;
    const icon = item.querySelector("img").getAttribute("src");
    stage.querySelector(".pic").innerHTML = pic
      ? `<img src="img/${pic}" alt="${item.dataset.alt}">`
      : `<img src="${icon}" alt="">`;
    stage.querySelector(".pic").classList.toggle("big", !pic);
    stage.querySelector(".pic").classList.toggle("ph", !!pic);
    stage.querySelector("h2").textContent = name;
    stage.querySelector("p").textContent = text;
  };
  items.forEach(i => {
    i.setAttribute("role", "button"); i.tabIndex = 0;
    i.addEventListener("click", () => show(i));
    i.addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); show(i); } });
  });
  show(items[0]);
}
