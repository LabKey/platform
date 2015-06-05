/*
 * Copyright (c) 2014-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.define('LABKEY.app.controller.HttpInterceptor', {

    extend : 'Ext.app.Controller',

    isService: true,

    init : function() {

        var me = this;

        Ext.Ajax.defaultHeaders['X-ONUNAUTHORIZED'] = 'UNAUTHORIZED';
        LABKEY.Ajax.DEFAULT_HEADERS['X-ONUNAUTHORIZED'] = 'UNAUTHORIZED';

        Ext.override(Ext.Ajax, {
            request : function(options) {

                Ext.apply(options, {
                    _fail: options.failure,
                    _failScope: options.scope,
                    failure: function(response, options) {
                        me._onAjaxFailure.call(me, response, options);
                    }
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
                    failure: function(response, options) {
                        me._onAjaxFailure.call(me, response, options);
                    }
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

        if (status === 0 || status === -1) {
            /* The request never happened. E.g. when the server is not responding / shutdown OR request aborted*/
            if (this.application.fireEvent('httpaborted', response.status, response.statusText) === false) {
                return;
            }
        }
        else if (response.status === 401) {
            /* UNAUTHORIZED */
            // first check if the caller would like to handle this type of response itself.  Return false
            // from callback to prevent other handlers from being invoked.
            if (Ext.isFunction(options.unauthorized)) {
                if (options.unauthorized.call(options._failScope, response, options) === false) {
                    return;
                }
            }

            if (this.application.fireEvent('httpunauthorized', response.status, response.statusText) === false) {
                return;
            }
        }
        else if (status === 403) {
            /* FORBIDDEN */
            // first check if the caller would like to handle this type of response itself.  Return false
            // from callback to prevent other handlers from being invoked.
            if (Ext.isFunction(options.forbidden)) {
                if (options.forbidden.call(options._failScope, response, options) === false) {
                    return;
                }
            }

            if (this.application.fireEvent('httpforbidden', response.status, response.statusText) === false) {
                return;
            }
        }
//        else {
//            console.log('status was', status);
//        }

        if (options) {
            if (Ext.isFunction(options._fail)) {
                options._fail.call(options._failScope, response, options);
            }
        }
    }
});