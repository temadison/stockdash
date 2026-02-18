const backButtons = document.querySelectorAll("[data-nav-back]");

for (const btn of backButtons) {
  btn.addEventListener("click", () => {
    const startUrl = window.location.href;
    let navigated = false;
    const markNavigated = () => {
      navigated = true;
    };

    window.addEventListener("pagehide", markNavigated, { once: true });
    window.history.back();

    window.setTimeout(() => {
      if (!navigated && window.location.href === startUrl) {
        window.location.assign("/");
      }
    }, 180);
  });
}
