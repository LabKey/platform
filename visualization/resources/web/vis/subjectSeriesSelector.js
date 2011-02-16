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

        LABKEY.vis.SubjectSeriesSelector.superclass.constructor.call(this, config);
    },

    initComponent : function() {

        // selection model for subject series selector
        var sm = new  Ext.grid.CheckboxSelectionModel({
            listeners: {
                scope: this,
                'selectionChange': function(selModel){
                    // add the selected subjects to the subject object
                    this.subject.selected = [];
                    var selectedRecords = selModel.getSelections();
                    for(var i = 0; i < selectedRecords.length; i++) {
                        this.subject.selected.push(selectedRecords[i].get('value'));
                    }

                    this.fireEvent('chartDefinitionChanged', false);
                }
            }
        });

        this.items = [new Ext.grid.GridPanel({
            id: 'subject-list-view',
            autoHeight: true,
            hidden: true, // initally hidden until subject values are loaded into the store
            viewConfig: {forceFit: true},
            border: false,
            frame: false,
            selModel: sm,
            header: false,
            store: new Ext.data.JsonStore({
                root: 'values',
                fields: ['value']
            }),
            columns: [
                sm,
                {header: this.subjectNounPlural, dataIndex:'value'}
            ]
         })];

        LABKEY.vis.SubjectSeriesSelector.superclass.initComponent.call(this);
    },

    getSubjectValues: function(schema, query) {
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
        var reader = new Ext.data.JsonReader({root:'values'}, [{name:'value'}]);
        var o = reader.read(response);

        var subjectGridStore = Ext.getCmp('subject-list-view').getStore();
        var subjectSelModel = Ext.getCmp('subject-list-view').getSelectionModel();

        // add all of the values to the subject list view store
        if(subjectGridStore.getCount() > 0){
            subjectGridStore.removeAll();
            delete this.subject.selected;
        }
        subjectGridStore.add(o.records);

        // if not saved chart, initially select first 5 subject values from the list view (but suspend events during selection)
        if(!this.subject.selected) {
            this.subject.selected = [];
            // select the first 5 subjects by default (select all if length < 5)
            for(var i = 0; i < (o.records.length < 5 ? o.records.length : 5); i++) {
                this.subject.selected.push(o.records[i].data.value != undefined ? o.records[i].data.value : o.records[i].data);
            }
        }
        subjectSelModel.suspendEvents(false);
        for(var i = 0; i < this.subject.selected.length; i++){
            subjectSelModel.selectRow(subjectGridStore.find('value', this.subject.selected[i]), true);
        }
        subjectSelModel.resumeEvents();

        // now that the subject values are loaded, show the list view
        Ext.getCmp('subject-list-view').show();

        // this is one of the requests being tracked, see if the rest are done
        this.fireEvent('measureMetadataRequestComplete');
    },

    getSubject: function(){
        return this.subject;
    }
});