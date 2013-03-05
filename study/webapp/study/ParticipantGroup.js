/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
        if(config.category) {
            config.type = config.category.type;
            config.shared = config.category.shared;
        }

        Ext4.applyIf(config, {
            hideDataRegion : false,
            isAdmin : false,
            hasButtons : true,
            canShare : true,
            dataRegionName : 'demoDataRegion',
            width : 950,
            height : config.hideDataRegion ? 325 : 500,
            type : config.type || 'manual',
            shared : config.shared || false
        });

        Ext4.apply(config, {
            title : 'Define ' + Ext4.util.Format.htmlEncode(config.subject.nounSingular) + ' Group',
            layout : 'fit',
            autoScroll : true,
            modal : true,
            resizable : false,
            listeners: {
                show: function(win){
                    new Ext4.util.KeyNav(win.getEl(), {
                        "enter" : function(e){
                            win.saveCategory();
                        },
                        scope : this
                    });
                }
            }
        });
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
            disabled : !this.canEdit || this.hideDataRegion,
            hidden : this.hideDataRegion,
            listeners: {
                select: this.onDemographicSelect,
                scope: this
            }
        });

        demoStore.load();

        Ext4.define('categoryModel', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'Label', type : 'string'},
                {name : 'Type', type : 'string'},
                {name : 'RowId', type : 'int'}
            ]
        });

        var categoryStore = Ext4.create('Ext.data.Store', {
            model : 'categoryModel',
            sorters : {property : 'Label', direction : 'ASC'},
            listeners:{
                load: function(me){
                    if(this.category && me.findExact('RowId', this.category.rowId) > -1){
                        categoryCombo.setValue(this.category.rowId);
                    }
                },
                scope: this
            }
        });

        LABKEY.Query.selectRows({
            schemaName : 'study',
            queryName : this.panelConfig.subject.nounSingular.replace(/\s/g, '') + 'Category',
            success : function(details){
                var manualCategories = [];
                for(var i = 0; i < details.rows.length; i++){
                    if(details.rows[i].Type === "manual"){
                        manualCategories.push(details.rows[i]);
                    }
                }
                categoryStore.loadData(manualCategories);
                categoryStore.fireEvent('load', categoryStore);
            },
            scope : this

        });

        var defaultWidth = 880;
        var categoryCombo = Ext4.create('Ext.form.ComboBox', {
            id : 'participantCategory',
            name : 'participantCategory',
            queryMode : 'local',
            xtype : 'combo',
            store : categoryStore,
            editable : true,
            mode : 'local',
            anchor : '100%',
            minChars : 1,
            autoSelect : false,
            emptyText : Ext4.util.Format.htmlEncode(this.panelConfig.subject.nounSingular) + ' Category',
            fieldLabel: Ext4.util.Format.htmlEncode(this.panelConfig.subject.nounSingular) + ' Category',
            labelAlign : 'top',
            grow : true,
            height : 50,
            maxWidth: defaultWidth,
            valueField : 'RowId',
            displayField : 'Label',
            triggerAction : 'all',
            listeners : {
                scope:this,
                change : function(combo, newValue, oldValue){
                    var shared = false;
                    var index = categoryStore.findExact('RowId', newValue);
                    if (index > -1) {
                        shared = categoryStore.getAt(index).data.shared;
                    }
                }
            }
        });

        this.selectionGrid = Ext4.create('Ext.Component', {
            width  :  defaultWidth,
            border : false
        });

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
                    checked : this.shared,
                    disabled : !this.canEdit,
                    listeners : {
                        scope : this,
                        afterrender : function(checkboxField){
                            Ext4.create('Ext.tip.ToolTip', {
                                trackMouse : true,
                                target : checkboxField.getEl(),
                                html : 'Share this ' + Ext4.util.Format.htmlEncode(this.panelConfig.subject.nounSingular) +' category with all users'
                            });
                           this.addClass('share-group-rendered'); // Test marker class
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
                    scope : this,
                    listeners : {
                        afterrender : function(){
                            this.fireEvent("donewithbuttons");
                        },
                        scope : this
                    }
                }, infoCombo, this.selectionGrid
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
        if(this.hideDataRegion){
            this.on('donewithbuttons', function(){this.height = simplePanel.el.dom.scrollHeight;});
        }
    },

    onDemographicSelect : function(combo, records, idx) {
        if (Ext4.isArray(records) && records.length > 0) {
            this.displayQueryWebPart(records[0].get("Label"));
        }
    },

    validate : function() {
        var fieldValues = this.down('panel').getValues(),
            label = fieldValues['groupLabel'],
            idStr = fieldValues['participantIdentifiers'],
            categoryCombo = this.queryById('participantCategory'),
            categoryStore = categoryCombo.getStore();

        if(categoryStore.findExact('Label', categoryCombo.getRawValue()) > -1){
            categoryCombo.select(categoryStore.findRecord('Label', categoryCombo.getRawValue()));
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
        if (this.xtype === 'button')
            var me = this.up('panel').up('window');
        else
            var me = this;
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

    getGroupData : function() {
        var fieldValues = this.queryById('simplePanel').getValues(),
            ptidsPre = fieldValues['participantIdentifiers'].split(','),
            categoryCombo = this.queryById('participantCategory'),
            categoryStore = categoryCombo.getStore(),
            categoryValue = categoryCombo.getValue(),
            categoryLabel,
            categoryId,
            categoryType,
            ptids = [], i, q, count;

        if (Ext4.isNumber(categoryValue)) {
            categoryId = categoryValue;
            categoryLabel = categoryStore.getAt(categoryStore.findExact("RowId", categoryId)).data.label;
            categoryType = 'manual';
        }
        else {
            categoryLabel = categoryCombo.getRawValue();
            categoryType = categoryLabel == '' ? 'list' : 'manual';
            if (categoryType == 'list' && (this.category && this.category.type == 'list')) {
                categoryId = this.category.rowId;
            }
        }

        for(i=0; i < ptidsPre.length; i++) {
            ptidsPre[i] = ptidsPre[i].split('\n');
        }

        for (i=0, count = 0; i < ptidsPre.length; i++) {
            for (q=0; q < ptidsPre[i].length; q++, count++) {
                if (ptidsPre[i][q].trim() != "") {
                    ptids[count] = ptidsPre[i][q].trim();
                }
                else { count--; }
            }
        }

        var groupData = {
            label : fieldValues["groupLabel"],
            participantIds : ptids,
            categoryLabel : categoryLabel,
            categoryType : categoryType,
            categoryShared : Ext4.getCmp('sharedBox').getValue()
        };

        if (categoryId !== null && categoryId != undefined) {
            groupData.categoryId = categoryId;
        }
        if (this.groupRowId !== null && this.groupRowId != undefined) {
            groupData.rowId = this.groupRowId;
        }
        return groupData;
    },

    displayQueryWebPart : function(queryName) {

        if (!this.hideDataRegion) {

            //QueryWebPart is an Ext 3 based component, so we need to include Ext 3 here.
            if (Ext) {

                Ext.onReady(function() {

                    var me = this;
                    var wp = new LABKEY.QueryWebPart({
                        renderTo: this.selectionGrid.id,
                        autoScroll : true,
                        dataRegionName : this.dataRegionName,
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
                                handler : function() { me._getSelectedDemoParticipants(queryName, 'selected'); }
                            },{
                                text : 'Add All',
                                handler : function() { me._getSelectedDemoParticipants(queryName, 'all'); }
                            }]
                        },
                        scope : this
                    });

                }, this);

            }
        }
    },

    /**
     * Assumes the presence of a rendered QueryWebPart.
     * Ext 3.4 is required and should be checked before calling this method.
     */
    _getSelectedDemoParticipants : function(queryName, showStr) {

        var ptidCategoryPanel = this.getComponent('simplePanel');
        var myFilters = Ext.ComponentMgr.get(this.dataRegionName).getUserFilterArray();
        var mySelectionKey = Ext.ComponentMgr.get(this.dataRegionName).selectionKey;

        LABKEY.Query.selectRows({
            schemaName : 'study',
            queryName : queryName,
            selectionKey : mySelectionKey,
            showRows : showStr,
            filterArray : myFilters,
            columns : this.subject.columnName,
            success : function(data) {

                var classPanelValues = ptidCategoryPanel.getValues();
                var tempIds = classPanelValues['participantIdentifiers'];

                var tempIdsArray = tempIds.split(",");
                for (var i = 0; i < tempIdsArray.length; i++){
                    tempIdsArray[i] = tempIdsArray[i].trim();
                }

                //append the selected ids to the current list
                for(i = 0; i < data.rows.length; i++){
                    if(tempIdsArray.indexOf(data.rows[i][this.subject.nounColumnName]) == -1){
                        tempIds += (tempIds.length > 0 ? ", " : "") + data.rows[i][this.subject.nounColumnName];
                        tempIdsArray.push(data.rows[i][this.subject.nounColumnName]);
                    }
                }

                ptidCategoryPanel.getForm().setValues({
                    participantIdentifiers : tempIds
                });
            },
            scope : this
        });
    }
});