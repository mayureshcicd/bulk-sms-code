document.addEventListener('DOMContentLoaded', () => {
    const checkboxes = Array.from(document.querySelectorAll('.message-review-checkbox'));
    const selectAll = document.getElementById('selectAllMessages');
    const actionButton = document.getElementById('reviewSelectedButton');
    const count = document.getElementById('selectedMessageCount');
    const modalCount = document.getElementById('modalSelectedMessageCount');
    const inputs = document.getElementById('selectedMessageInputs');
    if (!selectAll || !actionButton) return;

    function updateSelection() {
        const selected = checkboxes.filter(checkbox => checkbox.checked);
        count.textContent = selected.length;
        modalCount.textContent = selected.length;
        actionButton.classList.toggle('d-none', selected.length === 0);
        selectAll.checked = checkboxes.length > 0 && selected.length === checkboxes.length;
        selectAll.indeterminate = selected.length > 0 && selected.length < checkboxes.length;
        inputs.replaceChildren(...selected.map(checkbox => {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'messageIds';
            input.value = checkbox.value;
            return input;
        }));
    }

    selectAll.addEventListener('change', () => {
        checkboxes.forEach(checkbox => checkbox.checked = selectAll.checked);
        updateSelection();
    });
    checkboxes.forEach(checkbox => checkbox.addEventListener('change', updateSelection));
    updateSelection();
});
