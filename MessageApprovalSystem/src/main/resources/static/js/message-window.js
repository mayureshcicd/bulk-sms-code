document.addEventListener('DOMContentLoaded', () => {
    const messageWindow = document.getElementById('messageWindow');
    if (!messageWindow) return;
    const backdrop = document.getElementById('messageWindowBackdrop');
    const frame = document.getElementById('messageWindowFrame');
    const title = messageWindow.querySelector('.file-viewer-title');
    const maximize = messageWindow.querySelector('[data-message-maximize]');
    let open = false;

    const closeWindow = () => {
        open = false;
        messageWindow.className = 'file-viewer account-window message-window';
        backdrop.className = 'file-viewer-backdrop';
        frame.removeAttribute('src');
    };

    document.addEventListener('click', event => {
        const trigger = event.target.closest('[data-message-window]');
        if (!trigger) return;
        event.preventDefault();
        title.textContent = trigger.dataset.windowTitle || 'Message';
        frame.src = trigger.href;
        open = true;
        messageWindow.className = 'file-viewer account-window message-window is-open';
        backdrop.className = 'file-viewer-backdrop is-open';
        maximize.textContent = 'Maximize';
    });

    frame.addEventListener('load', () => {
        if (!open) return;
        try {
            if (frame.contentWindow.location.pathname === '/dashboard') {
                closeWindow();
                window.location.reload();
            }
        } catch (ignored) { }
    });

    messageWindow.querySelector('[data-message-close]').addEventListener('click', closeWindow);
    backdrop.addEventListener('click', closeWindow);
    messageWindow.querySelector('[data-message-minimize]').addEventListener('click', () => {
        messageWindow.classList.toggle('is-minimized');
        backdrop.classList.toggle('is-minimized');
    });
    maximize.addEventListener('click', () => {
        messageWindow.classList.remove('is-minimized');
        backdrop.classList.remove('is-minimized');
        maximize.textContent = messageWindow.classList.toggle('is-maximized') ? 'Restore' : 'Maximize';
    });
});
