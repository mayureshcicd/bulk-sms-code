document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.dashboard-grid table').forEach(table => {
        Array.from(table.querySelectorAll('thead th')).forEach(header => {
            header.style.width = `${Math.max(90, Math.round(header.getBoundingClientRect().width))}px`;
            const resizer = document.createElement('span');
            resizer.className = 'column-resizer';
            resizer.setAttribute('aria-hidden', 'true');
            header.appendChild(resizer);
            resizer.addEventListener('pointerdown', event => {
                event.preventDefault();
                const startX = event.clientX;
                const startWidth = header.getBoundingClientRect().width;
                document.body.classList.add('is-resizing');
                resizer.setPointerCapture(event.pointerId);
                const resize = moveEvent => {
                    header.style.width = `${Math.max(90, startWidth + moveEvent.clientX - startX)}px`;
                };
                const stop = () => {
                    document.body.classList.remove('is-resizing');
                    resizer.removeEventListener('pointermove', resize);
                    resizer.removeEventListener('pointerup', stop);
                    resizer.removeEventListener('pointercancel', stop);
                };
                resizer.addEventListener('pointermove', resize);
                resizer.addEventListener('pointerup', stop);
                resizer.addEventListener('pointercancel', stop);
            });
        });
    });

    const accountWindow = document.getElementById('accountWindow');
    if (!accountWindow) return;
    const backdrop = document.getElementById('accountWindowBackdrop');
    const frame = accountWindow.querySelector('.account-window-frame');
    const title = accountWindow.querySelector('.file-viewer-title');
    const maximize = accountWindow.querySelector('[data-account-maximize]');
    const closeWindow = () => {
        accountWindow.className = 'file-viewer account-window';
        backdrop.className = 'file-viewer-backdrop';
        frame.removeAttribute('src');
    };
    document.addEventListener('click', event => {
        const trigger = event.target.closest('[data-account-window]');
        if (!trigger) return;
        event.preventDefault();
        title.textContent = trigger.dataset.windowTitle || 'Account';
        frame.src = trigger.href;
        accountWindow.className = 'file-viewer account-window is-open';
        backdrop.className = 'file-viewer-backdrop is-open';
        maximize.textContent = 'Maximize';
    });
    accountWindow.querySelector('[data-account-close]').addEventListener('click', closeWindow);
    backdrop.addEventListener('click', closeWindow);
    accountWindow.querySelector('[data-account-minimize]').addEventListener('click', () => {
        accountWindow.classList.toggle('is-minimized');
        backdrop.classList.toggle('is-minimized');
    });
    maximize.addEventListener('click', () => {
        accountWindow.classList.remove('is-minimized');
        backdrop.classList.remove('is-minimized');
        const maximized = accountWindow.classList.toggle('is-maximized');
        maximize.textContent = maximized ? 'Restore' : 'Maximize';
    });
});
