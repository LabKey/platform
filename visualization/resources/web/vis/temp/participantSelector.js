/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.vis.ParticipantSelector', {

    extend : 'Ext.panel.Panel',

    constructor : function(config){
        Ext4.applyIf(config, {
            title: config.subjectNounPlural,
            border: false,
            autoScroll: true
        });

        Ext4.define('SimpleValue', {
            extend: 'Ext.data.Model',
            fields: [{name: 'value', type: 'string'}]
        });

        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged',
            'measureMetadataRequestPending',
            'measureMetadataRequestComplete'
        );
    },

    getSubjectValues: function() {
        // if the subjects gridpanel is already available, then return
        if(this.subjectGridPanel){
            return;
        }

        // store the subject info for use in the getDimensionValues call
        var subjectInfo = {
            name: this.subjectColumn,
            schemaName: 'study',
            queryName: this.subjectNounSingular
        };

        // this is the query/column we need to get the subject IDs for the selected measure
        this.fireEvent('measureMetadataRequestPending');
        Ext4.Ajax.request({
            url : LABKEY.ActionURL.buildURL("visualization", "getDimensionValues", null, subjectInfo),
            method:'GET',
            disableCaching:false,
            success : function(response, e){
                this.renderSubjects(response, e);
            },
            failure: function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
            scope: this
        });
    },

    renderSubjects: function(response, e) {
        // decode the JSON responseText
        var subjectValues = Ext4.decode(response.responseText);

        // if this is not a saved chart with pre-selected values, initially select the first 5 values (after sorting)
        this.selectDefault = false;
        if(!this.subject.values){
            this.selectDefault = true;

            // sort the subject values
            function compareValue(a, b) {
                if (a.value < b.value) {return -1}
                if (a.value > b.value) {return 1}
                return 0;
            }
            subjectValues.values.sort(compareValue);

            // select the first 5 values, or all if the length is less than 5
            this.subject.values = [];
            for (var i = 0; i < (subjectValues.values.length < 5 ? subjectValues.values.length : 5); i++)
            {
                this.subject.values.push(subjectValues.values[i].value);
            }
        }

        this.defaultDisplayField = Ext4.create('Ext.form.field.Display', {
            hideLabel: true,
            hidden: true,
            width: 210,
            html: '<span style="font-size:75%;color:red;">Selecting 5 values by default</span>'
        });
        this.add(this.defaultDisplayField);

        // selection model for subject series selector
        var sm = Ext4.create('Ext.selection.CheckboxModel', {checkOnly: true});
        sm.on('selectionchange', function(selModel){
            // add the selected subjects to the subject object
            this.subject.values = [];
            var selectedRecords = selModel.getSelection();
            for(var i = 0; i < selectedRecords.length; i++) {
                this.subject.values.push(selectedRecords[i].get('value'));
            }

            // sort the selected subject array
            this.subject.values.sort();

            this.fireEvent('chartDefinitionChanged', true);
        }, this, {buffer: 1000}); // buffer allows single event to fire if bulk changes are made within the given time (in ms)

        var ttRenderer = function(value, p, record) {
            var msg = Ext4.util.Format.htmlEncode(value);
            p.tdAttr = 'data-qtip="' + msg + '"';
            return msg;
        };

        // initialize the subject gridPanel with the subjectValues from the getDimensionValues call
        this.subjectGridPanel = Ext4.create('Ext.grid.Panel', {
            autoHeight: true,
            viewConfig: {forceFit: true},
            border: false,
            frame: false,
            selModel: sm,
            header: false,
            enableHdMenu: false,
            store: Ext4.create('Ext.data.Store', {
                model: 'SimpleValue',
                proxy: {
                    type: 'memory',
                    reader: {
                        type: 'json',
                        root:'values',
                        idProperty:'value'
                    }
                },
                data: subjectValues,
                sorters: {property: 'value', direction: 'ASC'}
            }),
            columns: [{header: this.subjectNounPlural, dataIndex:'value', renderer: ttRenderer, flex: 1}],
            listeners: {
                scope: this,
                'viewready': function(grid){
                    // show the selecting default text if necessary
                    if(grid.getStore().getCount() > 5 && this.selectDefault){
                        // show the display for 5 seconds before hiding it again
                        var refThis = this;
                        refThis.defaultDisplayField.show();
                        setTimeout(function(){
                            refThis.defaultDisplayField.hide();
                        },5000);
                        this.selectDefault = false;
                    }

                    // check selected subject values in grid panel (but suspend events during selection)
                    sm.suspendEvents(false);
                    for(var i = 0; i < this.subject.values.length; i++){
                        var index = grid.getStore().find('value', this.subject.values[i]);
                        sm.select(index, true);
                    }
                    sm.resumeEvents();
                }
            }
         });
         this.add(this.subjectGridPanel);

        // this is one of the requests being tracked, see if the rest are done
        this.fireEvent('measureMetadataRequestComplete');
    },

    getSubject: function(){
        return this.subject;
    }
});
