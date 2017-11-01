/*
 * Copyright (c) 2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.QuickTips.init();

Ext4.define('LABKEY.ext4.AssayStatusPanel', {

    extend: 'Ext.panel.Panel',

    border: false,

    header: false,

    alias: 'widget.labkey-assay-status',

    padding: 10,

    initComponent: function() {
        Ext4.applyIf(this, {
            visitLabels: [],
            visitLabelMap: {},
            visits: []
        });

        Ext4.each(this.visits, function(visit, i) {
            this.visitLabelMap[visit] = this.visitLabels[i];
        }, this);

        this.items = [{
            xtype: 'panel',
            border: false,
            frame: false,
            tpl: this.getTemplate(),
            data: {
                name: this.name,
                visits: this.visits,
                participants: this.participants
            }
        }];

        this.callParent(arguments);
    },

    getTemplate : function() {

        return new Ext4.XTemplate(
            '<div><h3>{name:htmlEncode}</div>',
            '<div class="table-responsive"><table class="table progress-report" id="ProgressReport"><tr>',
            '<th>Participant</th>',
            '<tpl for="visits">',
                '{[this.getVisitLabel(this, values)]}',
            '</tpl>',
            '</tr>',
            '<tpl for="participants">',
                '<tr class="{[xindex % 2 === 1 ? "labkey-alternate-row" : "labkey-row"]}">',
                '<td>{.:htmlEncode}</td>',
                '<tpl for="parent.visits">',
                    '{[this.renderCellValue(this, parent, values)]}',
                '</tpl>',
                '</tr>',
            '</tpl>',
            '</table></div>',
            {
                getVisitLabel : function(cmp, visit) {
                    var me = cmp.initialConfig.me;
                    return '<th>' + Ext4.htmlEncode(me.visitLabelMap[visit]) + '</th>';
                },
                renderCellValue : function(cmp, ptid, visit) {
                    var me = cmp.initialConfig.me;
                    var className = 'fa fa-circle-o';
                    var key = ptid + '|' + visit;

                    var mapCell = me.heatMap[key];
                    var tooltip = '';
                    if (mapCell) {
                        className = mapCell['iconcls'];
                        tooltip = mapCell['tooltip'];
                    }
                    return '<td><span height="16px" data-qtip="' + tooltip + '" class="' + className + '"></span></td>';
                },
                me : this
            }
        );
    }
});

Ext4.define('LABKEY.ext4.AssayProgressReport', {

    extend: 'Ext.panel.Panel',

    border: false,

    header : false,

    alias: 'widget.labkey-progress-report',

    padding: 10,

    initComponent : function() {
        Ext4.apply(this, {
            reports: []
        });

        var storeData = [{name : 'All', componentId : 'all'}];

        Ext4.each(this.assays, function(assay) {
            var id = assay.id;
            this.reports.push(id);
            storeData.push({
                name: Ext4.htmlEncode(assay.name),
                componentId : id
            })
        }, this);

        this.items = [{
            xtype   : 'button',
            text    : 'Export to Excel',
            itemId  : 'export-btn',
            disabled : true,
            handler : this.exportAssays,
            margin  : '0 0, 10, 0',
            scope   : this
        },{
            // legend and assay selector
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
                store: Ext4.create('Ext.data.Store', {
                    fields: ['name', 'componentId'],
                    data: storeData
                }),
                listeners : {
                    scope   : this,
                    change  : function(cmp, newValue) {
                        var exportBtn = this.getComponent('export-btn');
                        if (exportBtn)
                            exportBtn.setDisabled(newValue === 'all');
                        this.selectedAssay = newValue;
                        Ext4.each(this.reports, function(report) {

                            var panel = this.getComponent(report);
                            if (panel){
                                panel.setVisible(newValue === 'all' || newValue === report);
                            }
                        }, this);
                    }
                }
            }]
        },{
            xtype: 'panel',
            border: false,
            frame: false,
            tpl: new Ext4.XTemplate(
                '<div><h4>Legend</h4></div>',
                '<table class="legend">',
                '<tpl for=".">',
                '<tpl if="xindex % 2 === 1"><tr></tpl>',
                '<td><span style="height:16px;" class="{icon-class}"></span></td><td>&nbsp;{label:htmlEncode}</td>',
                '<tpl if="xindex % 2 != 1"></tr></tpl>',
                '</tpl>',
                '</table>'
            ),
            data: this.legend
        }];

        this.callParent(arguments);

        // add the assays
        this.on('render', this.createAssayReports, this, {single: true});
    },

    createAssayReports : function() {

        this.getEl().mask('Generating Assay Reports...');
        LABKEY.Ajax.request({
            url    : LABKEY.ActionURL.buildURL('study-reports', 'getAssayReportData.api'),
            method  : 'POST',
            jsonData  : {
                reportId : this.reportId
            },
            success : function(response){
                var o = Ext4.decode(response.responseText);
                this.getEl().unmask();

                if (o.success) {
                    var items = [];
                    this.assayData = o.assayData;
                    Ext4.each(this.assays, function(assay) {

                        var data = this.assayData[assay.id];

                        if (data) {
                            this.reports.push(assay.id);
                            items.push({
                                xtype   : 'labkey-assay-status',
                                itemId  : assay.id,
                                name    : assay.name,
                                visits  : data.visits,
                                visitLabels : data.visitLabels,
                                participants : data.participants,
                                heatMap : data.heatMap
                            });
                        }
                    }, this);

                    if (items.length > 0) {
                        this.add(items);
                    }
                }
            },
            failure : function(response) {
                this.getEl().unmask();
                Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
            },
            scope : this
        });
    },

    exportAssays : function() {
        window.location = LABKEY.ActionURL.buildURL('study-reports', 'exportAssayProgressReport.view', null, {
            reportId: this.reportId,
            assayId : this.selectedAssay
        });
    }
});

