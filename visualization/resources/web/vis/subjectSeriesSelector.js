/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();

LABKEY.vis.SubjectSeriesSelector = Ext.extend(Ext.Panel, {

    constructor : function(config){
        Ext.applyIf(config, {
            title: config.subjectNounPlural,
            autoScroll: true
        });

        this.addEvents(
            'chartDefinitionChanged',
            'measureMetadataRequestPending',
            'measureMetadataRequestComplete'
        );

        this.on('activate', function(){
           this.doLayout();
        }, this);

        LABKEY.vis.SubjectSeriesSelector.superclass.constructor.call(this, config);
    },

    getSubjectValues: function(schema, query) {
        // if there was a previous gridPanel showing (i.e. user is now changing measure),
        // remove it and delete the subject values array
        if(this.items && this.getComponent('subject-list-view')){
            this.removeAll();
            delete this.subject.values;
        }

        // store the subject info for use in the getDimensionValues call
        var subjectInfo = {
            name: this.subjectColumn,
            schemaName: schema,
            queryName: query
        };
        Ext.apply(this.subject, subjectInfo);

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

        this.defaultDisplayField = new Ext.form.DisplayField({
            hideLabel: true,
            hidden: true,
            value: 'Selecting 5 values by default',
            style: 'font-size:75%;color:red;'
        });
        this.add(this.defaultDisplayField);

        // selection model for subject series selector
        var sm = new  Ext.grid.CheckboxSelectionModel({});
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

        // initialize the subject gridPanel with the subjectValues from the getDimensionValues call
        var subjectGridPanel = new Ext.grid.GridPanel({
            id: 'subject-list-view',
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
                {header: this.subjectNounPlural, dataIndex:'value'}
            ],
            listeners: {
                scope: this,
                'viewready': function(grid){
                    // if this is not a saved chart with pre-selected values, initially select the first 5 values
                    var selectDefault = false;
                    if(!this.subject.values){
                        selectDefault = true;
                        this.subject.values = new Array();
                        for(var i = 0; i < (grid.getStore().getCount() < 5 ? grid.getStore().getCount() : 5); i++) {
                            this.subject.values.push(grid.getStore().getAt(i).get("value"));
                        }
                    }

                    // show the selecting default text if necessary
                    if(grid.getStore().getCount() > 5 && selectDefault){
                        // show the display for 3 seconds before hiding it again
                        var refThis = this;
                        refThis.defaultDisplayField.show();
                        refThis.doLayout();
                        setTimeout(function(){
                            refThis.defaultDisplayField.hide();
                            refThis.doLayout();
                        },5000);
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