document.addEventListener('DOMContentLoaded', () => {
    const viewer = document.getElementById('fileViewer');
    if (!viewer) return;
    const backdrop = document.getElementById('fileViewerBackdrop');
    const body = viewer.querySelector('.file-viewer-body');
    const title = viewer.querySelector('.file-viewer-title');
    const download = viewer.querySelector('[data-viewer-download]');
    const maximize = viewer.querySelector('[data-viewer-maximize]');
    const close = () => {
        viewer.className = 'file-viewer';
        backdrop.className = 'file-viewer-backdrop';
        body.replaceChildren();
    };
    const media = (url, contentType, name) => {
        const extension = (name.split('.').pop() || '').toLowerCase();
        const typeByExtension = {pdf:'application/pdf', png:'image/png', jpg:'image/jpeg', jpeg:'image/jpeg', gif:'image/gif', webp:'image/webp', svg:'image/svg+xml', mp4:'video/mp4', webm:'video/webm', mp3:'audio/mpeg', wav:'audio/wav', txt:'text/plain'};
        contentType = typeByExtension[extension] || contentType || '';
        let element;
        if (contentType.startsWith('image/')) {
            element = document.createElement('img');
            element.className = 'file-viewer-image';
            element.alt = name;
        } else if (contentType.startsWith('video/')) {
            element = document.createElement('video');
            element.className = 'file-viewer-media';
            element.controls = true;
        } else if (contentType.startsWith('audio/')) {
            element = document.createElement('audio');
            element.className = 'file-viewer-media';
            element.controls = true;
        } else if (contentType === 'application/pdf' || contentType.startsWith('text/') || ['doc','docx','csv','xls','xlsx'].includes(extension)) {
            element = document.createElement('iframe');
            element.className = 'file-viewer-frame';
            element.title = name;
        } else {
            element = document.createElement('div');
            element.className = 'file-viewer-message';
            element.innerHTML = '<div><h5>Preview is not supported by this browser</h5><p class="text-muted">You can still download and open this file.</p></div>';
            return element;
        }
        element.src = url;
        return element;
    };
    document.addEventListener('click', event => {
        const trigger = event.target.closest('[data-file-viewer]');
        if (!trigger) return;
        event.preventDefault();
        title.textContent = trigger.dataset.fileName;
        download.href = trigger.dataset.downloadUrl;
        download.setAttribute('download', trigger.dataset.fileName);
        const extension = (trigger.dataset.fileName.split('.').pop() || '').toLowerCase();
        const previewUrl = ['doc','docx','csv','xls','xlsx'].includes(extension) ? trigger.dataset.previewUrl : trigger.href;
        body.replaceChildren(media(previewUrl, trigger.dataset.contentType, trigger.dataset.fileName));
        viewer.className = 'file-viewer is-open';
        backdrop.className = 'file-viewer-backdrop is-open';
        maximize.textContent = 'Maximize';
    });
    viewer.querySelector('[data-viewer-close]').addEventListener('click', close);
    backdrop.addEventListener('click', close);
    viewer.querySelector('[data-viewer-minimize]').addEventListener('click', () => {
        viewer.classList.toggle('is-minimized');
        backdrop.classList.toggle('is-minimized');
    });
    maximize.addEventListener('click', () => {
        viewer.classList.remove('is-minimized');
        backdrop.classList.remove('is-minimized');
        maximize.textContent = viewer.classList.toggle('is-maximized') ? 'Restore' : 'Maximize';
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && viewer.classList.contains('is-open')) close();
    });
});
