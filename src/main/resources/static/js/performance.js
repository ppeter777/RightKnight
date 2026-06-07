document.addEventListener("DOMContentLoaded", () => {
    const syncMessage = document.getElementById("sync-message");

    if (!syncMessage) {
        return;
    }

    setTimeout(() => {
        syncMessage.classList.add("fade");

        setTimeout(() => {
            syncMessage.remove();
        }, 500);
    }, 5000);
});