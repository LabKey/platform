/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * Main panel for the NAb QC interface.
 */

Ext4.define('LABKEY.ext4.AssayStatusPanel', {

    extend: 'Ext.panel.Panel',

    border: false,

    header: false,

    alias: 'widget.labkey-assay-status',

    padding: 10,

    constructor: function (config) {
        this.callParent([config]);
    },

    initComponent: function () {
        this.items = [];
        this.items.push(this.getStatusPanel());

        this.callParent(arguments);
    },

    getStatusPanel : function(){

        if (!this.statusPanel){

            this.statusPanel = Ext4.create('Ext.panel.Panel', {
                border  : false,
                frame   : false,
                flex    : 1.2,
                tpl     : this.getTemplate(),
                data    : {
                    name    : this.name,
                    visits  : this.visits,
                    participants : this.participants
                }
            });
        }
        return this.statusPanel;
    },

    getTemplate : function () {

        var tpl = new Ext4.XTemplate(
                '<div><h3>{name}</h3></div>',
                '<div class="table-responsive"><table class="table progress-report" id="ProgressReport"><tr>',
                '<th>Participant</th>',
                '<tpl for="visits">',
                    '<th>{.}</th>',
                '</tpl>',
                '</tr>',
                '<tpl for="participants">',
                    '<tr class="{[xindex % 2 === 1 ? "labkey-alternate-row" : "labkey-row"]}">',
                    '<td>{.}</td>',
                    '<tpl for="parent.visits">',
                        '{[this.renderCellValue(parent, values)]}',
                    '</tpl>',
                    '</tr>',
                '</tpl>',
                '</table></div>',
                {
                    renderCellValue : function(ptid, visit)
                    {
                        var msg = null;
                        var color = null;
                        var className = null;

/*
                        // most visits will numeric, but some are strings (i.e. SR1, SR2, PT1)
                        var cell = this.data.heatMap[ptid + '|' + visit.value];
                        if (!cell)
                            cell = this.data.heatMap[ptid + '|' + visit.label]

                        if (cell)
                        {
                            className = cell.className;
                            color = cell.color;
                            if (cell.queryMsg)
                                msg = cell.queryMsg;
                            if (cell.flagMsg)
                            {
                                className = 'invalid';
                                msg = (msg != null ? msg + ' ' : '') + cell.flagMsg;
                            }
                        }
*/
                        return '<td><span height="16px" class="fa fa-ban"></span></td>';
                    }
                }
        );
        return tpl;
    }
});

Ext4.define('LABKEY.ext4.AssayProgressReport', {

    extend: 'Ext.panel.Panel',

    border: false,

    header : false,

    alias: 'widget.labkey-progress-report',

    padding: 10,

    constructor: function (config)
    {
        this.reports = [];
        this.callParent([config]);
    },

    initComponent: function ()
    {
        this.items = [];
        var assaysReports = [];
        var storeData = [{name : 'All', componentId : 'all'}];

        Ext4.each(this.assays, function(assay){

            var data = this.assayData[assay];

            if (data){
                var id = assay + '-report';
                this.reports.push(id);
                storeData.push({name : assay, componentId : id})
                assaysReports.push({
                    xtype   : 'labkey-assay-status',
                    itemId  : id,
                    name    : assay,
                    visits  : data['visits'],
                    participants : data['participants']
                });
            }
        }, this);

        var assayStore = Ext4.create('Ext.data.Store', {
            fields  : ['name', 'componentId'],
            data : storeData
        });

        // legend and assay selector
        this.items.push({
            xtype   : 'panel',
            border  : false,
            frame   : false,
            items   : [{
                xtype   : 'combo',
                labelWidth   : 200,
                fieldLabel  : 'Assay Progress Reports',
                queryMode   : 'local',
                editable    : false,
                value       : 'all',
                displayField    : 'name',
                valueField      : 'componentId',
                forceSelction   : false,
                store   : assayStore,
                listeners : {
                    scope   : this,
                    change  : function(cmp, newValue) {

                        Ext4.each(this.reports, function(report){

                            var panel = this.getComponent(report);
                            if (panel){
                                panel.setVisible(newValue == 'all' || newValue == report);
                            }
                        }, this);
                    }
                }
            }]
        });

        // add the assays
        this.items = this.items.concat(assaysReports);
        this.callParent(arguments);
    }
});

