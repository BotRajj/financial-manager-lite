// Auto-dismiss flash alerts after 4s (skips alerts marked as persistent)
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.alert-dismissible:not(.alert-persistent)').forEach(el => {
        setTimeout(() => {
            const bsAlert = bootstrap.Alert.getOrCreateInstance(el);
            if (bsAlert) bsAlert.close();
        }, 4000);
    });
});
