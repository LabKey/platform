/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4ClientAPI(true);

Ext4.define('Study.window.ParticipantGroup', {
    extend : 'Ext.window.Window',

    constructor : function(config){
        Ext4.QuickTips.init();
        this.panelConfig = config;
        this.addEvents('aftersave', 'closewindow');

        //var h = config.hideDataRegion ? 500 : Ext4.getBody().getViewSize().height * 75;
        var h = 500;

        Ext4.apply(config, {
            title : 'Define ' + Ext4.util.Format.htmlEncode(config.subject.nounSingular) + ' Group',
            layout : 'fit',
            autoScroll : true,
            modal : true,
            resizable : false,
            height : h,
            width : 950
        });

        this.hideDataRegion = config.hideDataRegion || false;
        this.groupLabel = config.groupLabel;
        this.categoryParticipantIds = config.categoryParticipantIds;
        this.isAdmin = config.isAdmin || false;
        this.grid = config.grid;
        this.category = config.category;
        this.subject = config.subject;
        this.hasButtons = config.hasButtons || true;
        this.canShare = config.canShare || true;
        this.dataRegionName = config.dataRegionName || 'demoDataRegion';
        this.callParent([config]);
    },

    initComponent : function(){

        var demoStore = Ext4.create('LABKEY.ext4.Store', {
            name : 'demoStore',
            schemaName : 'study',
            queryName : 'DataSets',
            filterArray : [LABKEY.Filter.create('DemographicData', true)],
            columns : 'Label',
            sort : 'Label',
            listeners : {
                load :function(store, records, options){
                    if (records.length > 0){
                        infoCombo.setValue(records[0].get("Label"));
                        this.onDemographicSelect(infoCombo, records, 0);
                    }
                },
                scope : this
            }
        });

        var infoCombo = Ext4.create('Ext.form.ComboBox', {
            name : 'infoCombo',
            triggerAction: 'all',
            scope : this,
            mode: 'local',
            store: demoStore,
            valueField: 'Label',
            displayField: 'Label',
            fieldLabel: 'Select ' + Ext4.util.Format.htmlEncode(this.panelConfig.subject.nounSingular) + ' from',
            labelStyle: 'width: 150px;',
            labelSeparator : '',
            disabled : !this.canEdit,
            listeners: {
                select: this.onDemographicSelect,
                scope: this
            }
        });

        demoStore.load();

        var categoryStore = Ext4.create('Ext.data.JsonStore', {

            proxy: {
                type : 'ajax',
                url : LABKEY.ActionURL.buildURL("participant-group", "getParticipantCategories"),
                method : 'POST',
                reader: {
                    type: 'json',
                    root: 'categories'
                }
            },
            xtype :     'jsonstore',
            name :      'categoryStore',
            id :        'categoryStore',
            root:       'categories',
            idProperty: 'rowId',
            baseParams: {
                categoryType: 'manual'
            },
            fields: [
                {name: 'rowId', type: 'int'},
                {name: 'label', type: 'string'},
                {name: 'type', type: 'string'},
                {name: 'createdBy', type: 'string', convert: function(v, record){return (v.displayValue ? v.displayValue : v.value)}},
                {name: 'modifiedBy', type: 'string', convert: function(v, record){return (v.displayValue ? v.displayValue : v.value)}},
                {name: 'shared', type: 'string'},
                {name: 'participantIds', type: 'string', convert: function(v, record){return v.toString().replace(/,/g,", ");}},
                {name: 'canEdit', type: 'boolean'},
                {name: 'canDelete', type: 'boolean'}
            ],
            listeners:{
                load: function(){
                    var thisStore = this.queryById('participantCategory').getStore();
                    if(this.category && thisStore.find('rowId', this.category.rowId) > -1){
                        categoryCombo.setValue(this.category.rowId);
                    }
                },
                scope: this

            }
        });

        var defaultWidth = 880;
        var categoryCombo = Ext4.create('Ext.form.ComboBox', {
            id : 'participantCategory',
            name : 'participantCategory',
            xtype : 'combo',
            store : categoryStore,
            editable : true,
            mode : 'local',
            anchor : '100%',
            typeAhead : true,
            typeAheadDelay : 75,
            minChars : 1,
            autoSelect : false,
            emptyText : Ext4.util.Format.htmlEncode(this.panelConfig.subject.nounSingular) + ' Category',
            fieldLabel: Ext4.util.Format.htmlEncode(this.panelConfig.subject.nounSingular) + ' Category',
            labelAlign : 'top',
            grow : true,
            height : 50,
            maxWidth: defaultWidth,
            valueField : 'rowId',
            displayField : 'label',
            triggerAction : 'all',
            listeners : {
                scope:this,
                change : function(combo, newValue, oldValue){
                    var shared = false;
                    var index = categoryStore.find('rowId', newValue);
                    if(index > -1){
                        shared = categoryStore.getAt(index).data.shared;
                    }
                    simplePanel.queryById('sharedBox').setValue(shared);

                }
            }
        });

        categoryStore.load();

        var simplePanel = Ext4.create('Ext.form.FormPanel', {
            id : 'simplePanel',
            name : 'simplePanel',
            bodyStyle : 'padding: 15px 0 0 15px',
            autoScroll : true,
            items : [
                {
                    xtype : 'textfield',
                    name : 'groupLabel',
                    id : 'groupLabel',
                    emptyText : Ext4.util.Format.htmlEncode(this.panelConfig.subject.nounSingular) + ' Group Label',
                    fieldLabel: Ext4.util.Format.htmlEncode(this.panelConfig.subject.nounSingular) + ' Group Label',
                    labelAlign : 'top',
                    width: defaultWidth
                },
                {
                    xtype : 'textareafield',
                    name : 'participantIdentifiers',
                    id : 'participantIdentifiers',
                    emptyText : 'Enter ' + Ext4.util.Format.htmlEncode(this.panelConfig.subject.nounSingular) + ' Identifiers Separated by Commas',
                    fieldLabel: Ext4.util.Format.htmlEncode(this.panelConfig.subject.nounSingular) + ' Identifiers',
                    labelAlign : 'top',
                    width: defaultWidth
                },
                categoryCombo,
                {
                    xtype : 'checkboxfield',
                    name : 'sharedBox',
                    id : 'sharedBox',
                    boxLabel : 'Share Category?',
                    listeners : {
                        scope : this,
                        afterrender : function(checkboxField){
                            var shareTip = Ext4.create('Ext.tip.ToolTip', {
                                trackMouse : true,
                                target : checkboxField.getEl(),
                                html : 'Share this ' + Ext4.util.Format.htmlEncode(this.panelConfig.subject.nounSingular) +' category with all users'
                            });
                        }

                    }
                },
                {
                    xtype : 'button',
                    text : "Save",
                    margin: '3 3 3 0',
                    handler : this.saveCategory
                },
                {
                    xtype : 'button',
                    text : "Cancel",
                    margin: 3,
                    handler : function(){this.fireEvent('closewindow');},
                    scope : this
                }, infoCombo,
                {
                    xtype: 'panel',
                    id : 'webPartPanel',
                    height : 400,
                    width :  defaultWidth,
                    grow : true,
                    border : false
                }
            ]
        });

        this.on('closewindow', this.close, this);
        this.on('afterSave', this.close, this);
        this.items = [simplePanel];
        if(this.categoryParticipantIds) {
            simplePanel.queryById('participantIdentifiers').setValue(this.categoryParticipantIds);
        }
        if(this.groupLabel){
            simplePanel.queryById('groupLabel').setValue(this.groupLabel);
        }
        simplePanel.on('closewindow', this.close, this);
        this.callParent(arguments);
        //This class exists for testing purposes (e.g. ReportTest)
        this.cls = "doneLoadingTestMarker";
    },

    onDemographicSelect : function(combo, records, idx) {
        if (Ext4.isArray(records) && records.length > 0) {
            this.getQueryWebPart(records[0].get("Label"));
        }
    },

    validate : function() {
        var fieldValues = this.down('panel').getValues(),
            label = fieldValues['groupLabel'],
            idStr = fieldValues['participantIdentifiers'],
            categoryCombo = this.queryById('participantCategory'),
            categoryStore = categoryCombo.getStore();

        if(categoryStore.find('label', categoryCombo.getRawValue()) > -1){
            categoryCombo.select(categoryStore.findRecord('label', categoryCombo.getRawValue()));
        }
        if(!label){
            Ext4.Msg.alert("Error", this.subject.nounSingular + " Group Label Required");
            return false;
        }
        if(!idStr){
            Ext4.Msg.alert("Error", "One or more " + this.subject.nounSingular + "Identifiers required");
            return false;
        }
        return true;
    },

    saveCategory : function(){
        var me = this.up('panel').up('window');
        if (!me.validate()) return;

        var groupData = me.getGroupData();

        Ext4.Ajax.request({
            url : (LABKEY.ActionURL.buildURL("participant-group", "saveParticipantGroup")),
            method : 'POST',
            success : function(){
                this.getEl().unmask();
                me.fireEvent('aftersave');
                if(me.grid){
                    me.grid.getStore().load();
                }
            },
            failure : function(response, options){
                this.getEl().unmask();
                LABKEY.Utils.displayAjaxErrorResponse(response, options, false);
            },
            jsonData : groupData,
            scope : this
        });
    },

    getGroupData : function(){
        var fieldValues = this.queryById('simplePanel').getValues(),
            ptids = fieldValues['participantIdentifiers'].split(','),
            categoryLabel,
            categoryId,
            categoryType,
            categoryCombo = Ext4.ComponentQuery.query('combo[id=participantCategory]')[0],
            categoryStore = categoryCombo.getStore();

        if(typeof categoryCombo.getValue() == 'number'){
            categoryId = categoryCombo.getValue();
            categoryLabel = categoryStore.getAt(categoryStore.find("rowId", categoryId)).data.label;
            categoryType = 'manual';
        } else {
            categoryLabel = categoryCombo.getRawValue();
            categoryType = categoryLabel == '' ? 'list' : 'manual';
            if(categoryType == 'list' && (this.category && this.category.type == 'list')){
                categoryId = this.category.rowId;
            }
        }

        for(var i = 0; i < ptids.length; i++){
            ptids[i] = ptids[i].trim();
        }

        var groupData = {
            label : fieldValues["groupLabel"],
            participantIds : ptids,
            categoryLabel : categoryLabel,
            categoryType : categoryType,
            categoryShared : this.down('panel').getForm().getFieldValues()['sharedBox']
        }

        if(categoryId !== null && categoryId != undefined){
            groupData.categoryId = categoryId;
        }
        if(this.groupRowId !== null && this.groupRowId != undefined) {
            groupData.rowId = this.groupRowId;
        }

        return groupData;
    },

    getQueryWebPart : function(queryName){
        var me = this;
        var initialLoad = true;
        if(!this.hideDataRegion){
            var webPart = new LABKEY.QueryWebPart({
                renderTo: 'webPartPanel',
                autoScroll : true,
                dataRegionName : this.dataRegionName,
                scope : this,
                schemaName: 'study',
                queryName: queryName,
                frame : 'none',
                border : false,
                showRecordSelectors : true,
                showUpdateColumn : false,
                buttonBar : {
                    position: (this.canEdit ? 'top' : 'none'),
                    includeStandardButtons : false,
                    items : [{
                        text : 'Add Selected',
                        requiresSelection : true,
                        handler : function() {
                            me.getSelectedDemoParticipants(queryName, "selected");
                        }
                    },{
                        text : 'Add All',
                        handler : function(){
                            me.getSelectedDemoParticipants(queryName, "all");
                        }

                    }

                    ]
                },
                success : function(){
                    if(initialLoad){
                        //QueryWebPart is an Ext 3 based component, so we need to include Ext 3 here.
                        if(Ext){
                            Ext.onReady(function(){
                                Ext.ComponentMgr.get(this.dataRegionName).clearSelected();
                                initialLoad = false;
                            }, this);
                        }

                    }
                }
            });
        }

    },

    getSelectedDemoParticipants : function(queryName, showStr) {
        var ptidCategoryPanel = this.down('panel');
        var me = this;
        if(Ext){
            Ext.onReady(function(){
                var myFilters = Ext.ComponentMgr.get(me.dataRegionName).getUserFilterArray();
                var mySelectionKey = Ext.ComponentMgr.get(me.dataRegionName).selectionKey;
                LABKEY.Query.selectRows({
                    schemaName : 'study',
                    queryName : queryName,
                    selectionKey : mySelectionKey,
                    showRows : showStr,
                    filterArray : myFilters,
                    columns : this.subject.columnName,
                    scope : this,
                    success : function(data){

                        var classPanelValues = ptidCategoryPanel.getValues();
                        var tempIds = classPanelValues['participantIdentifiers'];

                        var tempIdsArray = tempIds.split(",");
                        for (var i = 0; i < tempIdsArray.length; i++){
                            tempIdsArray[i] = tempIdsArray[i].trim();
                        }

                        //append the selected ids to the current list
                        for(i = 0; i < data.rows.length; i++){
                            if(tempIdsArray.indexOf(data.rows[i][me.subject.nounColumnName]) == -1){
                                tempIds += (tempIds.length > 0 ? ", " : "") + data.rows[i][me.subject.nounColumnName];
                                tempIdsArray.push(data.rows[i][me.subject.nounColumnName]);
                            }
                        }

                        ptidCategoryPanel.getForm().setValues({
                            participantIdentifiers : tempIds
                        });
                    }
                });
            }, this)
        }
    }

});