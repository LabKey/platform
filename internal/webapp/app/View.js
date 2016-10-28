/*
 * Copyright (c) 2014-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * This is the primary view controller (a.k.a. Application View Controller). It is responsible for registering views
 * that are used in the application and managing what views are shown based on the active view.
 * When a controller creates a view it should register that view instance with this Controller
 */
Ext.define('LABKEY.app.controller.View', {

    extend : 'Ext.app.Controller',

    /**
     * This map keys of known 'xtype's of views that will be managed by the application. The map values are
     * the associated functions for either showing or hiding that view 'type'. If these are not provided then a
     * default show/hide method is provided.
     */
    actions: {
        hide: {},
        show: {}
    },

    /**
     * This is a map of all the views for the Application.
     * It's purpose is to be able to register views and then use them throughout the application lifetime
     * NOTE: This is different from Ext.app.Controller.getView() because it returns an instance of a view
     */
    viewMap: {},

    controllerMap: {},

    controllers: {},

    appName: '',

    init : function() {

        if (LABKEY.devMode) {
            VM = this;
        }

        if (LABKEY.ActionURL) {
            this.urlParams = LABKEY.ActionURL.getParameters();
        }

        if (LABKEY.ActionURL) {
            var params = LABKEY.ActionURL.getParameters();
            //
            // Manage transitions
            //
            this.allowAnimations = false; //!Ext.isDefined(params['transition']);
        }

        this.activeContext = {};

        // by default, just return the title as it is set
        var el = Ext.DomQuery.select('title');
        if (Ext.isArray(el) && el.length > 0) {
            this.defaultTitle = Ext.get(el[0]).getHTML();
        }

        this.application.on('route', this.routeView, this);
    },

    /**
     * This is used to register a view xtype to a controller instance. When an action needs to be performed on a view
     * the associated contoller will be called. Normally, this is called by a Controller registering a view type.
     * @param viewtype
     * @param controllerInstance
     */
    registerView : function(viewtype, controllerInstance) {
        this.controllerMap[viewtype] = controllerInstance;
    },

    registerController : function(name, controllerInstance) {
        this.controllers[name.toLowerCase()] = controllerInstance;
    },

    registerShowAction : function(xtype, showAction, scope) {
        if (this.allowAnimations) {
            this.actions.show[xtype] = {
                fn: showAction,
                scope: scope
            };
        }
    },

    registerHideAction : function(xtype, hideAction, scope) {
        if (this.allowAnimations) {
            this.actions.hide[xtype] = {
                fn: hideAction,
                scope: scope
            };
        }
    },

    /**
     * This function registers a view instance. This should be called by each controller once a view is created.
     * Only one view instance allowed per xtype.
     * @param viewInstance
     */
    register : function(viewInstance) {
        this.viewMap[viewInstance.getXType()] = viewInstance;
    },

    /**
     * Returns true if the provided xtype has been registered with this Controller.
     * @param xtype
     */
    isRegistered : function(xtype) {
        return this.controllerMap[xtype];
    },

    /**
     * Allows a view instance to be unregistered from the controller. This can be used during clean-up of a view.
     * @param viewInstance
     */
    unregister : function(viewInstance) {
        this.viewMap[viewInstance.getXType()] = undefined;
    },

    /**
     * Creates a view instance from the provided xtype. This will call the provided registered Controller for the xtype
     * to get the view instance. See Connector.controller.AbstractViewController.createView.
     * @param xtype
     * @param context
     */
    createView : function(xtype, context) {
        if (this.controllerMap[xtype]) {
            var instance = this.controllerMap[xtype].createView(xtype, context);

            if (!Ext.isDefined(instance) || instance === false) {
                return this.showNotFound();
            }

            if (Ext.isArray(instance)) {
                if (!instance[1])
                    return instance[0];
            }
            this.CREATE_VIEW = true;
            this.register(instance);
            return instance;
        }
        console.warn('Failed to create view of type \'' + xtype + '\' because it has not been registered.');
    },

    /**
     * Will return the instance of a view if that view has already been instantiated.
     * @param xtype The xtype of the view instance
     */
    getViewInstance : function(xtype) {
        if (this.viewMap[xtype])
            return this.viewMap[xtype];
        return null;
    },

    /**
     * The default method for showing a view in the center region.
     * @param xtype
     */
    showView : function(xtype) {

        if (!this.viewMap[xtype]) {
            this.viewMap[xtype] = this.createView(xtype);
        }

        var actions = this.resolveViewTransitions(null, xtype);

        if (actions.show) {
            actions.show.fn.call(actions.show.scope, xtype);
        }
        else {
            console.error('failed to resolve show method.');
        }
    },

    /**
     * The default method for hiding a view in the center region.
     * @param view
     */
    hideView : function(view) {
        var actions = this.resolveViewTransitions(view);

        if (actions.hide)
            actions.hide.fn.call(actions.hide.scope, view, function(){});
        else
            console.error('failed to resolve hide method.');

    },

    /**
     * Default method for fading in a registered view instance.
     * @param xtype
     */
    fadeInView : function(xtype) {
        this.viewMap[xtype].show();
    },

    /**
     * Default method for fading out a registered view instance.
     * @param xtype
     * @param callback
     */
    fadeOutView : function(xtype, callback) {
        callback.call(this);
    },

    showNotFound : function(controller, view, viewContext, title) { },

    lastSignedIn : null,

    routeView : function(newHash, oldHash) {
        var controller = newHash.controller, view = newHash.view, viewContext = newHash.viewContext, params = newHash.params;
        if (this.lastSignedIn != LABKEY.user.isSignedIn) {
            this.lastSignedIn = LABKEY.user.isSignedIn;
            this.application.fireEvent('userChanged');
        }

        if (this.application.defaultLoginController && !LABKEY.user.isSignedIn) {
            if (!controller || controller.toLowerCase() != this.application.defaultLoginController.toLowerCase()) {
                controller = this.application.defaultLoginController;
                view = null;
            }
        } else {
            if (!controller || (Ext.isString(controller) && controller.length === 0)) {
                var resolved = this.application.resolveDefaultController();
                if (Ext.isDefined(resolved)) {
                    if (Ext.isString(resolved)) {
                        controller = resolved;
                    }
                    else {
                        console.log('Application.resolveDefaultController is expected to return a string.');
                    }
                }
                else {
                    console.log("Consider specifying a 'defaultController' on your Application.");
                }
            }
        }

        var control = this.controllers[controller.toLowerCase()], localContext;

        var _context = [];

        if (viewContext) {
            if (Ext.isString(viewContext)) {
                viewContext = [viewContext];
            }
            viewContext = Ext.Array.filter(viewContext, function(ctx) {
                if (ctx) {
                    return true;
                }
            });
            _context = Ext.Array.clone(viewContext);
        }

        if (control) {
            //
            // See if the controller provides a default view
            //
            if (!view) {
                view = control.getDefaultView();
            }

            //
            // Ask the controller to parse the view context
            //
            localContext = control.parseContext(_context, params, newHash, oldHash);
        }
        else {
            //
            // Unable to resolve the controller
            //
            this.showNotFound(controller, view, viewContext);
            this._notFound = false;

            return;
        }

        var actions = this.resolveViewTransitions(this.activeView, view);

        if (actions.hide) {
            actions.show.fn.call(actions.show.scope, view, localContext);
        }
        else if (actions.show) {
            actions.show.fn.call(actions.show.scope, view, localContext);
        }
        else {
            this.requestNotFound();
        }

        if (this.notFound()) {
            this.showNotFound(controller, view, viewContext, title);
            this._notFound = false;

            return;
        }

        if (!this.CREATE_VIEW) {
            this.controllerMap[view].updateView(view, localContext);
        }
        this.CREATE_VIEW = false;

        this.activeView = view;

        this.activeContext = {
            controller: controller,
            view: view
            // Add viewContext later???
        };

        var title = this.defaultTitle;
        var controlTitle = this.controllerMap[view].getViewTitle(view, localContext);
        if (Ext.isString(controlTitle) && controlTitle.length > 0) {
            title = controlTitle + " - " + title;
        }
        document.title = title;

        this.fireEvent('afterchangeview', controller, view, viewContext);
    },

    /**
     * Call when a view needs to be shown. This will resolve all transitions and call the set show/hide methods
     * for that view type.
     * @param {String} controller The name/target of the destination controller
     * @param {String} view the xtype of the destination view
     * @param {Array} viewContext url-based context (optional)
     * @param {Object} params Parameter name and value pairs(optional)
     */
    changeView : function(controller, view, viewContext, params) {
        var hashDelimiter = '/', parameterDelimiter = '?';
        var hash = encodeURIComponent(controller);

        this.fireEvent('beforechangeview', controller, view, this.activeContext, viewContext);

        if (view) {
            hash += hashDelimiter + encodeURIComponent(view);

            // can only provide context if a view is provided
            if (viewContext) {
                Ext.each(viewContext, function(token) {
                    hash += hashDelimiter + encodeURIComponent(token.toString());
                });
            }
        }

        if (params) {
            hash += parameterDelimiter;
            Ext.iterate(params, function(name, value) {
                if (Ext.isString(value) && value.length) {
                    hash += encodeURIComponent(name);
                    hash += '=';
                    hash += encodeURIComponent(value);
                }
            })
        }

        // route
        window.location.hash = hash;
    },

    setAppActionName : function(appName) {
        this.appName = appName;
    },

    getAppActionName : function() {
        return this.appName;
    },

    /**
     * Returns an encoded set of the current URL parameters.
     * @returns {string}
     */
    getAppURLParams : function() {
        var params = '';

        if (this.urlParams)
        {
            Ext.iterate(this.urlParams, function(key, value) {
                params += encodeURIComponent(key) + '=' + encodeURIComponent(value);
            });
        }

        return params;
    },

    /**
     * Resovles the transition functions that will be called to show the newViewXtype and hide the oldViewXtype.
     * @param {String} oldViewXtype
     * @param {String} newViewXtype
     */
    resolveViewTransitions : function(oldViewXtype, newViewXtype) {

        var actions = {
            show : undefined,
            hide : undefined
        };

        if (this.actions && oldViewXtype) {
            actions.hide = this.actions.hide[oldViewXtype];
        }

        if (newViewXtype) {

            if (this.actions) {
                actions.show = this.actions.show[newViewXtype];
            }

            if (!actions.show) {
                actions.show = {fn: this._showView, scope: this};
            }
        }

        return actions;
    },

    requestNotFound : function() {
        this._notFound = true;
    },

    notFound : function() {
        return this._notFound === true;
    },

    /**
     * @private
     * Default show view method used to set the active view for the center region.
     * @param xtype
     * @param context
     */
    _showView : function(xtype, context) {

        var center = this.getCenter();

        if (center) {
            if (!this.viewMap[xtype]) {
                var instance = this.createView(xtype, context);
                if (instance) {
                    this.viewMap[xtype] = instance;
                    center.add(this.viewMap[xtype]);
                }
                else {
                    this.requestNotFound();
                    return;
                }
            }

            var pre = center.getActiveTab();
            var post = this.viewMap[xtype];

            if (this.allowAnimations && pre) {
                var me = this;
                pre.getEl().fadeOut({callback: function() {
                    center.setActiveTab(post);
                    me.fadeInView(xtype);

                    //
                    // Prepare the first view to be shown again
                    //
                    Ext.defer(function() { pre.getEl().fadeIn(); }, 200, pre);
                }});
            }
            else {
                center.setActiveTab(post);
                this.fadeInView(xtype);
            }
        }
    }
});
