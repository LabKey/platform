/*
 * Copyright (c) 2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.define('LABKEY.app.panel.FilterDialog', {

    extend: 'Ext.panel.Panel',

    requires: ['Ext.form.field.ComboBox'],

    border: false,
    filters: [],
    defaultFiltId: 'labkey-filterdialog-default',
    facetedFiltId: 'labkey-filterdialog-faceted',
    useFacetGrouping: true,
    maxGroupSize: 20,
    minFilterPanelHeight: undefined,
    maxFilterPanelHeight: undefined,

    initComponent: function () {

        Ext.apply(this, {
            carryfilter: true, // whether filter state should try to be carried between views (e.g. when changing tabs)

            // hook key events
            keys: [{
                key: Ext.EventObject.ENTER,
                handler: this.onApply,
                scope: this
            }, {
                key: Ext.EventObject.ESC,
                handler: this.onClose,
                scope: this
            }]
        });

        if (!this.col) {
            console.error('\'col\' value must be provided to instantiate a', this.$className);
            return;
        }

        this.items = this.getItems();
        this.dockedItems = this.getButtons();

        this.callParent(arguments);
    },

    getItems: function () {
        var items = this.getHeaderItems();
        items.push(this.getContainer());
        return items;
    },

    resetItems: function () {
        this.viewcontainer = undefined;
        this.removeAll();
        this.add(this.getItems());
    },
    
    getButtons: function () {
        var buttons = [{
            xtype: 'toolbar',
            dock: 'bottom',
            ui: 'footer',
            items: [
                {xtype: 'button', text: 'OK', handler: this.onApply, scope: this},
                {xtype: 'button', text: 'Cancel', handler: this.onClose, scope: this},
                {xtype: 'button', text: 'Clear Filter', handler: this.clearFilter, scope: this},
                {xtype: 'button', text: 'Clear All Filters', handler: this.clearAllFilters, scope: this}
            ]
        }];

        return buttons;
    },

    onClose: function () {
        console.error('All uses of ', this.$className, ' should overwrite onClose()');
    },

    onApply: function () {
        console.error('All uses of ', this.$className, ' should overwrite onApply()');
    },

    clearFilter: function () {
        console.error('All uses of ', this.$className, ' should overwrite clearFilter() or getButtons()');
    },

    clearAllFilters: function () {
        console.error('All uses of ', this.$className, ' should overwrite clearAllFilters() or getButtons()');
    },

    getFilters: function () {
        return this.getContainer().getActiveTab().getFilters();
    },

    getHeaderItems: function () {
        var items = [{
            xtype: 'box',
            autoEl: {
                tag: 'div',
                html: this.col.text,
                cls: 'windowheader'
            }
        }];

        if (this.boundColumn.description) {
            items.push({
                xtype: 'box',
                autoEl: {tag: 'div', cls: 'x-body windowdescription', html: Ext.htmlEncode(this.boundColumn.description)},
                maxHeight: 125,
                autoScroll: true
            });
        }

        return items;
    },

    getContainer: function () {

        if (!this.viewcontainer) {

            var views = this.getViews();
            var type = 'Ext.tab.Panel';

            if (views.length == 1) {
                views[0].title = false;
                type = 'Ext.panel.Panel';
            }

            var config = {
                cls: 'filterdialogcontainer',
                border: false,
                defaults: this.defaults,
                deferredRender: false,
                monitorValid: true,
                items: views
            };

            if (type == 'Ext.tab.Panel') {
                config.listeners = {
                    beforetabchange: function (tp, newTab, oldTab) {
                        if (this.carryfilter && newTab && oldTab && oldTab.isChanged()) {
                            newTab.setFilters(oldTab.getFilters());
                        }
                    },
                    tabchange: function () {
                        this.syncShadow();
                        this.viewcontainer.getActiveTab().doLayout(); // required when facets return while on another tab
                    },
                    scope: this
                };
            }

            if (views.length > 1) {
                config.activeTab = (this.allowFaceting() ? 1 : 0);
            }
            else {
                views[0].title = false;
            }

            this.viewcontainer = Ext.create(type, config);

            if (!Ext.isFunction(this.viewcontainer.getActiveTab)) {
                var me = this;
                this.viewcontainer.getActiveTab = function () {
                    return me.viewcontainer.items.items[0];
                };
                // views attempt to hook the 'activate' event but some panel types do not fire
                // force fire on the first view
                this.viewcontainer.items.items[0].on('afterlayout', function (p) {
                    p.fireEvent('activate', p);
                }, this, {single: true});
            }
        }

        return this.viewcontainer;
    },

    allowFaceting: function () {
        console.error('All uses of ', this.$className, ' should overwrite allowFaceting()');
    },

    getDefaultPanel: function () {
        return this.getContainer().getComponent(this.defaultFiltId);
    },

    getFacetedPanel: function () {
        return this.getContainer().getComponent(this.facetedFiltId);
    },

    getColumnFilters: function (filters) {
        var colFilters = [];
        Ext.each(filters, function (filter) {
            if (filter.getColumnName() === this.boundColumn.displayField ? this.boundColumn.displayField : this.boundColumn.fieldKey
                    && !filter.isSelection) {
                colFilters.push(filter);
            }
        }, this);
        return colFilters;
    },

    // Override to return your own filter views
    getViews: function () {

        var views = [];

        // default view
        views.push({
            title: 'Choose Filters',
            xtype: 'labkey-default-filterpanel',
            itemId: this.defaultFiltId,
            boundColumn: this.boundColumn,
            filterArray: this.store.filterArray,
            schemaName: this.schemaName,
            queryName: this.queryName
        });

        // facet view
        if (this.allowFaceting()) {
            views.push({
                title: 'Choose Values',
                xtype: 'labkey-faceted-filterpanel',
                itemId: this.facetedFiltId,
                cls: 'facetfilterpanel',
                border: false,
                overflowY: 'auto',
                useGrouping: this.useFacetGrouping,
                maxGroup: this.maxGroupSize,
                maxRows: this.maxRows,
                onOverValueLimit: this.onOverValueLimit,
                onSuccessfulLoad: this.onSuccessfulLoad,
                minHeight: this.minFilterPanelHeight,
                maxHeight: this.maxFilterPanelHeight,
                useStoreCache: false,
                groupFilters: this.store.filterArray,
                filters: this.store.filterArray,
                model: {
                    column: this.boundColumn,
                    schemaName: this.schemaName,
                    queryName: this.queryName
                },
                listeners: {
                    invalidfilter: function () {
                        this.carryfilter = false;
                        this.getContainer().setActiveTab(0);
                        this.getContainer().getActiveTab().doLayout();
                        this.carryfilter = true;
                    },
                    scope: this
                },
                scope: this
            })
        }

        return views;
    }
});