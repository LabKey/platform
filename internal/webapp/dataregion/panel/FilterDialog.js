/*
 * Copyright (c) 2014-2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
Ext4.define('LABKEY.dataregion.panel.FilterDialog', {

    extend: 'Ext.panel.Panel',

    requires: ['Ext.form.field.ComboBox'],

    border: false,
    padding: 25,
    flex: 1,
    filters: [],
    defaultFiltId: 'labkey-filterdialog-default',
    facetedFiltId: 'labkey-filterdialog-faceted',
    maxGroupSize: 20,

    initComponent: function () {

        Ext4.apply(this, {
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

        this.items = this.getHeaderItems();
        this.items.push(this.getContainer());

        this.dockedItems = this.getButtons();

        this.callParent(arguments);


    },

    getButtons: function () {
        var buttons = [{
            xtype: 'toolbar',
            dock: 'bottom',
            ui: 'footer',
            items: [
                {xtype: 'button', text: 'OK', handler: this.onApply, scope: this},
                {xtype: 'button', text: 'CANCEL', handler: this.onClose, scope: this},
                {xtype: 'button', text: 'CLEAR FILTER', handler: this.clearFilter, scope: this},
                {xtype: 'button', text: 'CLEAR ALL FILTERS', handler: this.clearAllFilters, scope: this}
            ]
        }];

        return buttons;
    },

    onClose: function () {
        this.close;
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
                autoEl: {tag: 'div', cls: 'x-body', html: Ext4.htmlEncode(this.boundColumn.description)},
                maxHeight: 125,
                autoScroll: true
            });
        }

        return items;
    },

    getContainer: function () {

        if (!this.viewcontainer) {

            var views = this.getViews();
            var type = 'TabPanel';

            if (views.length == 1) {
                views[0].title = false;
                type = 'Panel';
            }

            var config = {
                defaults: this.defaults,
                deferredRender: false,
                monitorValid: true,

                // sizing and styling
                autoHeight: true,
                bodyStyle: 'padding: 5px;',
                border: false,
                width: this.width - 45,
                items: views
            };

            if (type == 'TabPanel') {
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

            this.viewcontainer = new Ext4[type](config);

            if (!Ext4.isFunction(this.viewcontainer.getActiveTab)) {
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
        return this.getContainer().getComponent('labkey-filterdialog-default');
    },

    getFacetedPanel: function () {
        return this.getContainer().getComponent('labkey-filterdialog-faceted');
    },

    getColumnFilters: function (filters) {
        var colFilters = [];
        Ext4.each(filters, function (filter) {
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
            itemId: 'labkey-filterdialog-default',
            boundColumn: this.boundColumn,
            filterArray: this.store.filterArray,
            style: 'padding: 20px 35px;',
            schemaName: this.schemaName,
            queryName: this.queryName
        });

        // facet view
        if (this.allowFaceting()) {
            views.push({
                title: 'Choose Values',
                xtype: 'labkey-faceted-filterpanel',
                itemId: 'labkey-filterdialog-faceted',
                border: false,
                overflowY: 'auto',
                useGrouping: true,
                maxGroup: this.maxGroupSize,
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