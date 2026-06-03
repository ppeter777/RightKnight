document.addEventListener("DOMContentLoaded", () => {
    const input = document.getElementById("lichessUsername");

    if (!input) {
        return;
    }

    input.addEventListener("input", () => {
        input.value = input.value
            .trim()
            .toLowerCase();
    });
});
