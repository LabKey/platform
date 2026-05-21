LABKEY.Admin = LABKEY.Admin || {};

LABKEY.Admin.Caches = new function() {
    var API_URL = LABKEY.ActionURL.buildURL('admin', 'clearCaches');

    function showSpinner() {
        var el = document.getElementById('cacheSpinner');
        if (el) el.style.display = 'inline';
    }

    function hideSpinner() {
        var el = document.getElementById('cacheSpinner');
        if (el) el.style.display = 'none';
    }

    function showError(msg) {
        hideSpinner();
        var el = document.getElementById('cacheError');
        if (el) {
            el.textContent = msg || 'An error occurred. Please try again.';
            el.style.display = 'block';
        }
    }

    function hideError() {
        var el = document.getElementById('cacheError');
        if (el) el.style.display = 'none';
    }

    function doPost(params) {
        hideError();
        showSpinner();
        LABKEY.Ajax.request({
            url: API_URL,
            method: 'POST',
            params: params,
            success: reloadPage,
            failure: function(response) {
                var msg = 'Request failed.';
                try {
                    var json = JSON.parse(response.responseText);
                    if (json && json.exception) msg = json.exception;
                } catch (e) { /* ignore */ }
                showError(msg);
            }
        });
    }

    function reloadPage() { window.location.reload(); }

    // clearSingle is called from inline onclick handlers (addHandler), so must be defined immediately.
    this.clearSingle = function(debugName) {
        doPost({ debugName: debugName });
    };

    // Bind button handlers once the DOM is ready — elements don't exist yet when this script runs.
    window.addEventListener('DOMContentLoaded', function() {
        function bindIfPresent(id, params) {
            var el = document.getElementById(id);
            if (el) {
                el.addEventListener('click', function(e) {
                    e.preventDefault();
                    el.disabled = true;
                    doPost(params);
                });
            }
        }

        bindIfPresent('clearAllCaches', { clearCaches: true });
        bindIfPresent('clearCachesGc', { clearCaches: true, gc: 1 });
        bindIfPresent('gcOnly', { gc: true });

        const refreshEl = document.getElementById('refreshPage');
        if (refreshEl) {
            refreshEl.addEventListener('click', function(e) {
                e.preventDefault();
                reloadPage();
            });
        }
    });
};
