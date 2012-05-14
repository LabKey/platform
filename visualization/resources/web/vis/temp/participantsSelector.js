/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();

LABKEY.vis.SubjectSeriesSelector = Ext.extend(Ext.Panel, {

    constructor : function(config){
        Ext.applyIf(config, {
            title: config.subjectNounPlural,
            border: false,
            autoScroll: true
        });

        this.addEvents(
            'chartDefinitionChanged',
            'measureMetadataRequestPending',
            'measureMetadataRequestComplete'
        );

        LABKEY.vis.SubjectSeriesSelector.superclass.constructor.call(this, config);
    },

    getSubjectValues: function() {
        // if the subjects gridpanel is already available, then return
        if(this.items && this.getComponent(this.subjectListViewId)){
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
        Ext.Ajax.request({
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
        var subjectValues = Ext.util.JSON.decode(response.responseText);

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

        this.defaultDisplayField = new Ext.form.DisplayField({
            hideLabel: true,
            hidden: true,
            value: 'Selecting 5 values by default',
            style: 'font-size:75%;color:red;'
        });
        this.add(this.defaultDisplayField);

        // selection model for subject series selector
        var sm = new  Ext.grid.CheckboxSelectionModel({checkOnly: true});
        sm.on('selectionchange', function(selModel){
            // add the selected subjects to the subject object
            this.subject.values = [];
            var selectedRecords = selModel.getSelections();
            for(var i = 0; i < selectedRecords.length; i++) {
                this.subject.values.push(selectedRecords[i].get('value'));
            }

            // sort the selected subject array
            this.subject.values.sort();

            this.fireEvent('chartDefinitionChanged', true);
        }, this, {buffer: 1000}); // buffer allows single event to fire if bulk changes are made within the given time (in ms)

        var ttRenderer = function(value, p, record) {
            var msg = Ext.util.Format.htmlEncode(value);
            p.attr = 'ext:qtip="' + msg + '"';
            return msg;
        };

        this.subjectListViewId = Ext.id();
        // initialize the subject gridPanel with the subjectValues from the getDimensionValues call
        var subjectGridPanel = new Ext.grid.GridPanel({
            id: this.subjectListViewId,
            autoHeight: true,
            viewConfig: {forceFit: true},
            border: false,
            frame: false,
            selModel: sm,
            header: false,
            enableHdMenu: false,
            store: new Ext.data.JsonStore({
                data: subjectValues,
                root: 'values',
                fields: ['value'],
                sortInfo: {
                    field: 'value',
                    direction: 'ASC'
                }
            }),
            columns: [
                sm,
                {header: this.subjectNounPlural, dataIndex:'value', renderer: ttRenderer}
            ],
            listeners: {
                scope: this,
                'viewready': function(grid){
                    // show the selecting default text if necessary
                    if(grid.getStore().getCount() > 5 && this.selectDefault){
                        // show the display for 5 seconds before hiding it again
                        var refThis = this;
                        refThis.defaultDisplayField.show();
                        refThis.doLayout();
                        setTimeout(function(){
                            refThis.defaultDisplayField.hide();
                            refThis.doLayout();
                        },5000);
                        this.selectDefault = false;
                    }

                    // check selected subject values in grid panel (but suspend events during selection)
                    sm.suspendEvents(false);
                    for(var i = 0; i < this.subject.values.length; i++){
                        var index = grid.getStore().find('value', this.subject.values[i]);
                        sm.selectRow(index, true);
                    }
                    sm.resumeEvents();
                }
            }
         });
         this.add(subjectGridPanel);
         this.doLayout();

        // this is one of the requests being tracked, see if the rest are done
        this.fireEvent('measureMetadataRequestComplete');
    },

    getSubject: function(){
        return this.subject;
    }
});
