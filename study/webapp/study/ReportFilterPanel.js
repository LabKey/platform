/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4Sandbox(true);

Ext4.define('LABKEY.ext4.FilterPanel', {

    extend : 'Ext.panel.Panel',

    layout : 'fit',
    border : false,
    frame  : false,
    bubbleEvents : ['select', 'selectionchange'],

    initComponent : function() {

        this.items = [];

        if (this.description) {
            this.items.push({
                xtype : 'box',
                autoEl: {
                    tag : 'div',
                    html: this.description
                }
            });
        }
        if (this.store) {
            this.items.push(this.initGrid());
        }

        this.callParent([arguments]);
    },

//    initTree : function() {
//        if (this.target)
//            return this.target;
//
//        if (this.fn)
//            this.store.filterBy(this.fn);
//
//        function toTreeStore(dataStore) {
//            var treeStore = Ext4.create('Ext.data.TreeStore', {
//                model : dataStore.model.modelName,
//                proxy : dataStore.proxy,
//                listeners : {
//                    beforeappend : function(node, appnode, eOpts) {
//                        console.log('beforeappend');
//                        if (!appnode.data.root) {
//                            appnode.data.leaf    = true;
//                            appnode.data.checked = true;
//                        }
//                        console.log([node, appnode, eOpts]);
//                    }
//                }
//            });
//            console.log('tree store ready');
//            return treeStore;
//        }
//
//        this.target = Ext4.create('Ext.tree.Panel', {
//            layout       : 'fit',
//            border       : false, frame : false,
//            hideHeaders  : true,
//
//            // tree panel configs
//            rootVisible  : false,
//            multiSelect  : true,
//            displayField : 'label',
//            forceFit     : true,
//            store        : toTreeStore(this.store),
//            columns      : this.initTargetColumns(),
//            bubbleEvents : ['select', 'selectionchange'],
//
//            listeners   : {
//                viewready : function(tree) {
//
//                    this.target.suspendEvents();
//
//                    if (!this.selection) {
//                        console.log('selecting all');
//                        this.target.getSelectionModel().selectAll();
//                    }
//                    else {
//                        for (var s=0; s < this.selection.length; s++) {
//                            var rec = this.target.getStore().findRecord('id', this.selection[s].id);
//                            if (rec)
//                                this.target.getSelectionModel().select(rec);
//                            else
//                                console.warn('Unable to find ' + this.selection[s].id);
//                        }
//                    }
//
//                    this.target.resumeEvents();
//                },
//                scope     : this
//            },
//
//            scope        : this
//        });
//
//        return this.target;
//    },

    initGrid : function() {
        if (this.target)
            return this.target;

        if (this.fn)
            this.store.filterBy(this.fn);

        this.target = Ext4.create('Ext.grid.Panel', {
            store       : this.store,
            border      : false, frame : false,
            bodyStyle   : 'border: none;',
            layout      : 'fit',
            hideHeaders : true,
            multiSelect : true,
            columns     : this.initTargetColumns(),
            viewConfig : {
                stripeRows : false,
                emptyText  : 'No Cohorts Available'
            },
            selType     : 'checkboxmodel',
            bubbleEvents: ['select', 'selectionchange'],
            listeners   : {
                viewready : function(grid) {

                    this.target.suspendEvents();

                    if (!this.selection) {
                        console.log('selecting all');
                        this.target.getSelectionModel().selectAll();
                    }
                    else {
                        for (var s=0; s < this.selection.length; s++) {
                            var rec = this.target.getStore().findRecord('id', this.selection[s].id);
                            if (rec)
                                this.target.getSelectionModel().select(rec);
                            else
                                console.warn('Unable to find ' + this.selection[s].id);
                        }
                    }

                    this.target.resumeEvents();
                },
                scope     : this
            },
            scope       : this
        });

        return this.target;
    },

    initTargetColumns : function() {
        if (this.columns)
            return this.columns;

        this.columns = [];

        this.columns.push({
            flex      : 1,
            dataIndex : 'label',
            scope     : this
        },{
            dataIndex : 'type',
            hidden    : true,
            scope     : this
        });

        return this.columns;
    },

    getSelection : function() {
        if (this.target)
            return this.target.getSelectionModel().getSelection();
        // return undefined -- same as getSelection()
    },

    getDescription : function() {
        return this.description;
    }
});

Ext4.define('LABKEY.ext4.ReportFilterPanel', {

    extend : 'Ext.panel.Panel',

    bubbleEvents : ['select', 'selectionchange'],

    constructor : function(config) {
        Ext4.applyIf(config, {
            border : false, frame : false
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.items = [this.initSelectionPanel()];
        this.callParent([arguments]);
    },

    initSelectionPanel : function() {

        if (this.selectionPanel)
            return this.selectionPanel;

        this.filterPanels = [];
        for (var f=0; f < this.filters.length; f++) {
            this.filterPanels.push(Ext4.create('LABKEY.ext4.FilterPanel',Ext4.apply(this.filters[f],{
                border : false, frame : false
            })));
        }

        this.selectionPanel = Ext4.create('Ext.panel.Panel', {
            layout : 'fit',
            border : false, frame : false,
            items  : this.filterPanels
        });

        return this.selectionPanel;
    },

    /**
     * Returns an array of arrays matching the configuration stores indexes. See the 'collapsed' param for alternative
     * return value.
     * @param collapsed When true the function will return a single array of all selected records. Defaults to false.
     */
    getSelection : function(collapsed) {
        var selections = [], select;
        if (this.filterPanels) {
            for (var i=0; i < this.filterPanels.length; i++) {
                select = this.filterPanels[i].getSelection();

                if (!collapsed) {
                    if (select && select.length > 0)
                        selections.push(select);
                    else {
                        selections.push([]);
                    }
                }
                else {
                    if (select) {
                        for (var j=0; j < select.length; j++) {
                            selections.push(select[j]);
                        }
                    }
                }
            }
        }
        return selections;
    },

    allSelected : function() {

        if (this.filterPanels) {
             for (var i=0; i < this.filterPanels.length; i++) {
                 if (this.filterPanels[i].target.getSelectionModel().getCount() != this.filterPanels[i].target.getStore().getCount())
                    return false;
             }
        }

        return true;
    }
});

Ext4.define('LABKEY.ext4.ReportFilterWindow', {
    extend : 'Ext.window.Window',

    constructor : function(config) {

        Ext4.applyIf(config, {
            height        : 500,
            width         : 250,
            collapsible   : true,
            collapsed     : true,
            expandOnShow  : true,
            titleCollapse : true,
            draggable     : false,
            closable      : false,
            cls           : 'report-filter-window',
            title         : 'Filter Report'
        });

        this.callParent([config]);
    },

    initComponent : function() {

        if (!this.items && this.filters) {
            this.filterPanel = Ext4.create('LABKEY.ext4.ReportFilterPanel', {
                layout  : 'fit',
                filters : this.filters,
                border  : false, frame : false
            });

            this.items = [this.filterPanel];
        }

        if (this.relative)
            this.relative.on('resize', this.calculatePosition, this);
        this.on('show', this.calculatePosition, this);

        this.callParent([arguments]);
    },

    calculatePosition : function() {
        if (!this.relative) {
            console.warn('unable to show ReportFilter due to relative component not being provided.');
            this.hide();
            return;
        }

        // elements topleft to targets topright
        this.alignTo(this.relative, 'tl-tr', [-300,27]);
    }
});