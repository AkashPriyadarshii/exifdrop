// Phone-flow demo: chat → share sheet → stripping → clean, looping.
// Reduced-motion users get the clean frame, no loop.
(function () {
  const screens = Array.prototype.slice.call(document.querySelectorAll('.demo-flow-screen'));
  if (!screens.length) return;

  const idx = { chat: 0, sheet: 1, strip: 2, clean: 3 };
  let step = 0;

  function show(i) {
    screens.forEach(function (el, n) {
      el.classList.toggle('is-active', n === i);
    });
  }

  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    show(idx.clean); // static "clean copy" frame
    return;
  }

  show(0);
  setInterval(function () {
    step = (step + 1) % screens.length;
    show(step);
  }, 2600);
})();