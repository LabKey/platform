/*
 * Copyright (c) 2014 LabKey Corporation
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
     * This is a map of all the views for the Connector Application.
     * It's purpose is to be able to register views and then use them throughout the application lifetime
     * NOTE: This is different from Ext.app.Controller.getView() because it returns an instance of a view
     */
    viewMap: {},

    tabMap: {},

    controllerMap: {},

    init : function() {

        if (LABKEY.ActionURL) {
            var params = LABKEY.ActionURL.getParameters();
            //
            // Manage transitions
            //
            this.allowAnimations = false; //!Ext.isDefined(params['transition']);
        }
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

    registerShowAction : function(xtype, showAction, scope) {
        if (this.allowAnimations) {
            this.actions.show[xtype] = {
                fn: showAction,
                scope: scope
            }
        }
    },

    registerHideAction : function(xtype, hideAction, scope) {
        if (this.allowAnimations) {
            this.actions.hide[xtype] = {
                fn: hideAction,
                scope: scope
            }
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
        console.error('Failed to create view of type \'' + xtype + '\' because it has not been registered.');
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
//        if (this.viewMap[xtype].rendered) {
//            this.viewMap[xtype].getEl().fadeIn();
//            this.viewMap[xtype].show();
//        }
//        else {
//            this.viewMap[xtype].show();
//        }
    },

    /**
     * Default method for fading out a registered view instance.
     * @param xtype
     * @param callback
     */
    fadeOutView : function(xtype, callback) {
        callback.call(this);
//        this.viewMap[xtype].getEl().fadeOut({
//            listeners : {
//                afteranimate : function() {
//                    callback.call(this);
//                },
//                scope : this
//            },
//            scope : this
//        });
    },

    showNotFound : function() { },

    /**
     * Call when a view needs to be shown. This will resolve all transitions and call the set show/hide methods
     * for that view type.
     * @param {String} newViewXtype Xtype of the view to be shown
     * @param {Array} newViewContext url-based context (optional)
     * @param {String} viewTitle Title to display for page in browser (optional)
     * @param {Boolean} skipState Control over whether this view change is a recorded state event. Defaults to False.
     * @param {Boolean} skipHide Control over whether the 'activeView' should be hidden. Defaults to False.
     */
    changeView : function(newViewXtype, newViewContext, viewTitle, skipState, skipHide) {
        this.inTransition = true;

        var _context = [];
        if (newViewContext) {
            if (Ext.isString(newViewContext))
                newViewContext = newViewContext.split('/');
            _context = Ext.Array.clone(newViewContext);
            _context.shift(); // drop the active view
        }

        var c = this.controllerMap[newViewXtype], context;

        if (c) {
            context = c.parseContext(_context);
        }
        else {
            this.showNotFound();
            this.inTransition = false;

            return;
        }

        var actions = this.resolveViewTransitions(this.activeView, newViewXtype);

        if (!skipHide && actions.hide) {
            actions.show.fn.call(actions.show.scope, newViewXtype, context);
        }
        else if (actions.show) {
            actions.show.fn.call(actions.show.scope, newViewXtype, context);
        }

        if (!this.CREATE_VIEW) {
            this.controllerMap[newViewXtype].updateView(newViewXtype, context);
        }
        this.CREATE_VIEW = false;

        this.activeView = newViewXtype;

        this.inTransition = false;

        this.fireEvent('afterchangeview', this.activeView, context, viewTitle, skipState);
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

    /**
     * @private
     * Default show view method used to set the active view for the center region.
     * @param xtype
     * @param context
     */
    _showView : function(xtype, context) {

        var center = this.getCenter();

        if (center) {
            if (!this.viewMap[xtype] || !this.tabMap[xtype]) {
                this.viewMap[xtype] = this.createView(xtype, context);
                center.add(this.viewMap[xtype]);
            }

            var pre = center.getActiveTab();
            var post = this.tabMap[xtype];

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
        else {
            console.warn('WATCH OUT: Failed to load', xtype);
        }
    }
});
