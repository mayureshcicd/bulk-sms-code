document.addEventListener('DOMContentLoaded', () => {
    const createForm = document.getElementById('createAccountForm');
    const createButton = document.getElementById('createAccountButton');
    if (createForm && createButton) {
        createForm.addEventListener('submit', () => {
            createButton.disabled = true;
            createButton.innerHTML = '<span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>Creating...';
        });
    }

    const checkboxes = Array.from(document.querySelectorAll('.user-delete-checkbox:not(:disabled)'));
    const selectAll = document.getElementById('selectAllUsers');
    const deleteButton = document.getElementById('deleteSelectedUsers');
    const count = document.getElementById('selectedUserCount');
    if (!selectAll || !deleteButton || !count) return;

    function updateSelection() {
        const selected = checkboxes.filter(checkbox => checkbox.checked);
        count.textContent = selected.length;
        deleteButton.classList.toggle('d-none', selected.length === 0);
        selectAll.checked = checkboxes.length > 0 && selected.length === checkboxes.length;
        selectAll.indeterminate = selected.length > 0 && selected.length < checkboxes.length;
        selectAll.disabled = checkboxes.length === 0;
    }

    selectAll.addEventListener('change', () => {
        checkboxes.forEach(checkbox => {
            checkbox.checked = selectAll.checked;
        });
        updateSelection();
    });
    checkboxes.forEach(checkbox => checkbox.addEventListener('change', updateSelection));
    updateSelection();
});
