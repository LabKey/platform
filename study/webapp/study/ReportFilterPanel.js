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

//        this.addEvents('select');

        this.callParent([arguments]);
    },

    initGrid : function() {
        if (this.grid)
            return this.grid;

        if (this.fn)
            this.store.filterBy(this.fn);

        this.grid = Ext4.create('Ext.grid.Panel', {
            store       : this.store,
            border      : false, frame : false,
            layout      : 'fit',
            hideHeaders : true,
            multiSelect : true,
            columns     : this.initGridColumns(),
            viewConfig : {
                stripRows : false,
                emptyText : 'No Cohorts Available'
            },
            selType     : 'checkboxmodel',
            bubbleEvents: ['select', 'selectionchange'],
            scope       : this
        });

        return this.grid;
    },

    initGridColumns : function() {
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
        if (this.grid)
            return this.grid.getSelectionModel().getSelection();
        // return undefined -- same as getSelection()
    },

    getDescription : function() {
        return this.description;
    }
});

Ext4.define('LABKEY.ext4.ReportFilterPanel', {

    extend : 'Ext.panel.Panel',

    initComponent : function() {
        this.items = [this.initSelectionPanel()];
        this.callParent([arguments]);
    },

    initSelectionPanel : function() {

        if (this.selectionPanel)
            return this.selectionPanel;

        this.filterPanels = [];
        for (var f=0; f < this.filters.length; f++) {
            this.filterPanels.push(Ext4.create('LABKEY.ext4.FilterPanel',this.filters[f]));
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