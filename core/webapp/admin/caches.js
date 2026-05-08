LABKEY.Admin = LABKEY.Admin || {};

LABKEY.Admin.Caches = new function() {
    var API_URL = LABKEY.ActionURL.buildURL('admin', 'clearCaches');

    function doPost(params) {
        LABKEY.Ajax.request({
            url: API_URL,
            method: 'POST',
            params: params,
            success: reloadPage,
            failure: LABKEY.Utils.getCallbackWrapper(null, null, true)
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
        bindIfPresent('clearCachesGc', { clearCaches: true, gc: true });
        bindIfPresent('gcOnly', { gc: true });
    });
};
