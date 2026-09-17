(function () {
    fetch('/api/public/config', { credentials: 'same-origin' })
        .then(response => response.ok ? response.json() : Promise.reject())
        .then(config => {
            if (!config.companyName) return;
            document.querySelectorAll('[data-company-name]').forEach(element => {
                element.textContent = config.companyName;
            });
        })
        .catch(() => { /* Keep the built-in fallback name when configuration is unavailable. */ });
})();
