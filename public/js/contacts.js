(() => {
    'use strict';
    const selected = new Set();
    let contacts = [], letter = '', editingId = null, loading = false;
    const endpoint = (window.location.pathname.includes('/bulk-sms') ? '/bulk-sms' : '') + '/api/contacts';
    const $id = id => document.getElementById(id);
    const modalElement = $id('contactsModal');
    const modal = bootstrap.Modal.getOrCreateInstance(modalElement);

    window.contactSelection = {
        hasSelection: () => selected.size > 0,
        appendRecipients(formData, csv) {
            if (selected.size) selected.forEach(id => formData.append('contactIds', id));
            else if (csv) formData.append('csv', csv);
        }
    };
    function notice(message = '') {
        $id('contactsNotice').textContent = message;
        $id('contactsNotice').classList.toggle('d-none', !message);
    }
    async function request(path = '', options = {}) {
        const response = await fetch(endpoint + path, { ...options, headers: { 'Content-Type': 'application/json' } });
        if (response.redirected) throw new Error('Your session has ended. Please sign in again.');
        if (!response.ok) {
            let message = response.status === 402 ? 'Trial period has expired.' : 'Unable to save or load contacts. Please try again.';
            if (response.status === 404) message = 'This contact no longer exists. Close and reopen the dialog to refresh.';
            if (response.status === 400) message = 'Enter a name and a phone number with 10–15 digits.';
            throw new Error(message);
        }
        return response.status === 204 ? null : response.json();
    }
    function syncSelection() {
        for (const id of ['bulkCsvFile', 'csvFile']) {
            const input = $id(id);
            input.disabled = selected.size > 0;
            if (selected.size) input.value = '';
        }
        document.querySelectorAll('.contact-selection-summary').forEach(el => {
            el.textContent = selected.size ? `${selected.size} contact(s) selected · CSV/TXT upload disabled` : 'No contacts selected';
        });
    }
    function filtered() {
        const search = $id('contactSearch').value.trim().toLocaleLowerCase();
        return contacts.filter(c => (!letter || c.name.toLocaleUpperCase().startsWith(letter))
            && c.name.toLocaleLowerCase().includes(search));
    }
    function render() {
        const rows = $id('contactRows');
        rows.replaceChildren();
        const visible = filtered();
        $id('contactCount').textContent = `${visible.length} displayed · ${selected.size} selected`;
        $id('selectAllContacts').disabled = loading || !visible.length;
        for (const contact of visible) {
            const row = document.createElement('tr');
            const checkCell = row.insertCell();
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox'; checkbox.className = 'form-check-input';
            checkbox.checked = selected.has(contact.id);
            checkbox.setAttribute('aria-label', `Select ${contact.name}`);
            checkbox.addEventListener('change', () => {
                if (checkbox.checked) selected.add(contact.id); else selected.delete(contact.id);
                syncSelection();
                $id('contactCount').textContent = `${visible.length} displayed · ${selected.size} selected`;
            });
            checkCell.append(checkbox);
            row.insertCell().textContent = contact.name;
            row.insertCell().textContent = contact.phoneNumber;
            row.insertCell().textContent = contact.email || '—';
            const actions = row.insertCell();
            const edit = document.createElement('button');
            edit.type = 'button'; edit.className = 'btn btn-primary btn-sm me-2'; edit.textContent = 'Edit';
            edit.addEventListener('click', () => {
                editingId = contact.id;
                $id('contactName').value = contact.name;
                $id('contactPhone').value = contact.phoneNumber;
                if ($id('contactEmail')) $id('contactEmail').value = contact.email || '';
                $id('contactFormTitle').textContent = 'Update Contact'; $id('saveContact').textContent = 'Update';
                $id('cancelContactEdit').classList.remove('d-none'); $id('contactName').focus();
            });
            const remove = document.createElement('button');
            remove.type = 'button'; remove.className = 'btn btn-danger btn-sm'; remove.textContent = 'Delete';
            remove.addEventListener('click', async () => {
                if (!confirm(`Delete contact ${contact.name}?`)) return;
                remove.disabled = true;
                try {
                    await request('/' + contact.id, { method: 'DELETE' });
                    contacts = contacts.filter(c => c.id !== contact.id); selected.delete(contact.id);
                    if (editingId === contact.id) resetForm();
                    notice(); syncSelection(); render();
                } catch (error) { notice(error.message); remove.disabled = false; }
            });
            actions.append(edit, remove); rows.append(row);
        }
        if (!visible.length) {
            const cell = document.createElement('tr').insertCell();
            cell.colSpan = 5; cell.className = 'text-center text-muted py-4';
            cell.textContent = loading ? 'Loading contacts…' : 'No contacts found.';
            rows.append(cell.parentElement);
        }
    }
    function resetForm() {
        editingId = null; $id('contactForm').reset();
        if ($id('contactEmail')) $id('contactEmail').value = '';
        $id('contactFormTitle').textContent = 'Create Contact'; $id('saveContact').textContent = 'Create';
        $id('cancelContactEdit').classList.add('d-none');
    }
    for (const value of ['', ...'ABCDEFGHIJKLMNOPQRSTUVWXYZ']) {
        const button = document.createElement('button');
        button.type = 'button'; button.className = 'btn btn-outline-primary btn-sm';
        button.textContent = value || 'All'; button.setAttribute('aria-pressed', String(!value));
        button.classList.toggle('active', !value);
        button.addEventListener('click', () => {
            letter = value;
            $id('contactAlphabet').querySelectorAll('button').forEach(b => {
                b.classList.toggle('active', b === button); b.setAttribute('aria-pressed', String(b === button));
            }); render();
        });
        $id('contactAlphabet').append(button);
    }
    document.querySelectorAll('.provide-contacts').forEach(button => button.addEventListener('click', async () => {
        modal.show(); notice(); loading = true; contacts = []; render();
        $id('saveContact').disabled = true;
        try {
            contacts = await request();
            const available = new Set(contacts.map(c => c.id));
            for (const id of selected) if (!available.has(id)) selected.delete(id);
            syncSelection();
        } catch (error) { notice(error.message); }
        finally { loading = false; $id('saveContact').disabled = false; render(); }
    }));
    $id('contactForm').addEventListener('submit', async event => {
        event.preventDefault();
        const button = $id('saveContact'); button.disabled = true; notice();
        const emailVal = $id('contactEmail') ? $id('contactEmail').value.trim() : null;
        try {
            const saved = await request(editingId === null ? '' : '/' + editingId, {
                method: editingId === null ? 'POST' : 'PUT',
                body: JSON.stringify({
                    name: $id('contactName').value.trim(),
                    phoneNumber: $id('contactPhone').value.trim(),
                    email: emailVal || null
                })
            });
            contacts = contacts.filter(c => c.id !== saved.id); contacts.push(saved);
            contacts.sort((a, b) => a.name.localeCompare(b.name));
            resetForm(); render();
        } catch (error) { notice(error.message); }
        finally { button.disabled = false; }
    });
    $id('cancelContactEdit').addEventListener('click', resetForm);

    const importBtn = $id('importContactCsvBtn');
    if (importBtn) {
        importBtn.addEventListener('click', async () => {
            const fileInput = $id('contactCsvFile');
            const file = fileInput && fileInput.files ? fileInput.files[0] : null;
            if (!file) {
                notice('Please select a CSV or TXT file to upload.');
                return;
            }
            importBtn.disabled = true;
            importBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Importing...';
            notice();
            try {
                const formData = new FormData();
                formData.append('csvFile', file);
                const res = await fetch(endpoint + '/import-csv', {
                    method: 'POST',
                    body: formData
                });
                const data = await res.json();
                if (!res.ok) throw new Error(data.message || data.error || 'Failed to import CSV');
                notice(data.message || 'Contacts imported successfully!');
                fileInput.value = '';
                contacts = await request();
                render();
            } catch (err) {
                notice(err.message);
            } finally {
                importBtn.disabled = false;
                importBtn.innerHTML = '<i class="fas fa-file-import"></i> Import CSV';
            }
        });
    }

    $id('contactSearch').addEventListener('input', render);
    $id('selectAllContacts').addEventListener('click', () => { filtered().forEach(c => selected.add(c.id)); syncSelection(); render(); });
    $id('clearAllContacts').addEventListener('click', () => { selected.clear(); syncSelection(); render(); });
    syncSelection();
})();
