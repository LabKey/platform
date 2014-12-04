Ext.define('LABKEY.app.controller.HttpInterceptor', {

    extend : 'Ext.app.Controller',

    isService: true,

    init : function() {

        var me = this;

        Ext.Ajax.defaultHeaders['X-ONUNAUTHORIZED'] = 'AUTHORIZED';
        LABKEY.Ajax.DEFAULT_HEADERS['X-ONUNAUTHORIZED'] = 'AUTHORIZED';

        Ext.override(Ext.Ajax, {
            request : function(options) {

                Ext.apply(options, {
                    _fail: options.failure,
                    _failScope: options.scope,
                    failure: me._onAjaxFailure
                });

                this.callParent(arguments);
            }
        });

        Ext.apply(LABKEY.Ajax, {
            _realRequest: LABKEY.Ajax.request,
            request: function(options) {

                Ext.apply(options, {
                    _fail: options.failure,
                    _failScope: options.scope,
                    failure: me._onAjaxFailure
                });

                LABKEY.Ajax._realRequest(options);
            }
        });

        this.callParent();
    },

    /**
     * @private
     * This is called each time a 'failure' occurs due to the Ajax APIs. It wraps any other failure callbacks giving
     * other services a chance to listen to http events (e.g. 401, 403, etc)
     * @param response
     * @param options
     */
    _onAjaxFailure : function(response, options) {
//        console.log('Response sayz status:', response.status, ', statusText: \'' + response.statusText + '\'');

        var status = response.status;

        if (status === 0) {
            /* The request never happened. E.g. when the server is not responding / shutdown */
        }
        else if (response.status === 401) {
            /* NOT AUTHORIZED */
//            AUTH_STOP = true;
//            Ext.Ajax.abortAll();
//            LABKEY.user.isSignedIn = false;
//            window.location.reload();
        }
        else if (status === 403) {
            /* FORBIDDEN */
        }
        else if (options) {
            if (Ext.isFunction(options._fail)) {
                options._fail.call(options._failScope, response, options);
            }
        }
    }
});