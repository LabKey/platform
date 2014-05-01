/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.define('LABKEY.app.controller.Route', {

    extend : 'Ext.app.Controller',

    delimiter : '#',

    routes: [],

    init : function() {

        var me = this;
        var popState = false;

        var pathChangeTask = new Ext.util.DelayedTask(function() {
            me.route(me.getHashValue(location.hash), popState);
            popState = false;
        });

        var pathChange = function() { pathChangeTask.delay(50); };

        Ext.EventManager.on(window, 'hashchange', pathChange);

        if (Ext.supports.History) {
            Ext.EventManager.on(window, 'popstate', function() {
                popState = true;
                pathChange();
            });
        }
    },

    onAppReady : function() {

        var hash = this.getHashValue(location.hash);

        if (hash.length > 0) {
            this.route(hash);
        }
        else {
            this.application.getController('State').loadState(null, null, null, null, true);
        }
    },

    route : function(fragments, popState) {

        if (!Ext.isString(fragments))
        {
            console.warn('invalid route fragment supplied.');
            fragments = '';
        }

        var splitFragments = fragments.split('/');

        var urlContext = [];

        Ext.each(splitFragments, function(frag) {
            urlContext.push(decodeURIComponent(frag));
        });

        if (urlContext.length > 0) {
            var controller = urlContext[0];
            var viewContext = null, view;
            if (urlContext.length > 1) {
                urlContext.shift(); // drop the controller
                view = urlContext[0];
                if (urlContext.length > 1) {
                    urlContext.shift(); // drop the view
                    viewContext = urlContext;
                }
            }
//            console.log('control:', controller);
//            console.log('view:', view);
//            console.log('viewcontext:', viewContext);
            this.application.getController('State').loadState(controller, view, viewContext, null, true);
        }
        else {
            alert('Router failed to find resolve view context from route.');
        }
    },

    /**
     * Returns a string representation of what is found after the last instance of {this.delimiter}
     * @param str
     * @returns {*}
     */
    getHashValue : function(str) {
        var h = str.split(this.delimiter);
        if (h.length == 1) {
            h = [''];
        }
        return h[h.length-1];
    }
});
