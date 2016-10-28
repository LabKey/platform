/*
 * Copyright (c) 2014-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.define('LABKEY.app.controller.Route', {

    extend : 'Ext.app.Controller',

    delimiter : '#',

    routes: [],

    init : function() {

        var me = this;
        var popState = false, previousUrlHash = location.hash;
        var oldUrl = previousUrlHash;

        var pathChangeTask = new Ext.util.DelayedTask(function() {
            me.route(me.getHashValue(location.hash), popState, me.getHashValue(oldUrl));
            popState = false;
        });

        Ext.EventManager.on(window, 'hashchange', function(e) {
            oldUrl = previousUrlHash;
            pathChangeTask.delay(50);
            previousUrlHash = location.hash;
        });

        if (Ext.supports.History) {
            Ext.EventManager.on(window, 'popstate', function() {
                popState = true;
                pathChangeTask.delay(50);
            });
        }
    },

    onAppReady : function() {
        this.route(this.getHashValue(location.hash), false);
    },

    route : function(fragments, popState, oldFragments) {
        var newFragments = this.getHashFragments(fragments);
        if (newFragments.controller !== undefined) {
            this.application.fireEvent('route', newFragments, this.getHashFragments(oldFragments, true));
        }
        else {
            alert('Router failed to find resolve view context from route.');
        }
    },

    getHashFragments: function(hash, noWarning) {
        if (!Ext.isString(hash))
        {
            if (!noWarning) {
                console.warn('invalid route fragment supplied.');
            }
            hash = '';
        }

        var fragmentSplits = hash.split('?');
        var splitFragments = fragmentSplits[0].split('/');
        var urlContext = [], parameters, parsedFragment = {
            controller  : undefined,
            view : undefined,
            viewContext: undefined,
            params: undefined
        };

        Ext.each(splitFragments, function(frag) {
            urlContext.push(decodeURIComponent(frag));
        });

        if (urlContext.length > 0)
        {
            parsedFragment.controller = urlContext[0];
            var viewContext = null, view;
            if (urlContext.length > 1)
            {
                urlContext.shift(); // drop the controller
                parsedFragment.view = urlContext[0];
                if (urlContext.length > 1)
                {
                    urlContext.shift(); // drop the view
                    parsedFragment.viewContext = urlContext;
                }
            }
            if (fragmentSplits.length > 1)
            {
                parsedFragment.params = this.getHashParameters(fragmentSplits[1]);
            }
        }
        return parsedFragment;
    },

    getHashParameters: function(fragment) {
        var params = {};
        var query = fragment;
        var vars = query.split("&");
        for (var i=0;i<vars.length;i++) {
            var pair = vars[i].split("=");
            // If first entry with this name
            if (typeof params[pair[0]] === "undefined") {
                params[pair[0]] = decodeURIComponent(pair[1]);
                // If second entry with this name
            } else if (typeof params[pair[0]] === "string") {
                var arr = [ params[pair[0]],decodeURIComponent(pair[1]) ];
                params[pair[0]] = arr;
                // If third or later entry with this name
            } else {
                params[pair[0]].push(decodeURIComponent(pair[1]));
            }
        }
        return params;
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
