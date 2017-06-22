/*
 * Copyright (c) 2011-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('Study.window.ParticipantGroup', {
    extend : 'Ext.window.Window',

    statics: {
        /**
         * Used via ParticipantGroupManager to initiate a window from a data region.
         * Assumes usage only in the context of a study.
         */
        fromDataRegion : function(dataRegionName, fromSelection, isAdmin) {
            var region = LABKEY.DataRegions[dataRegionName],
                nounSingular = LABKEY.moduleContext.study.subject.nounSingular,
                nounPlural = LABKEY.moduleContext.study.subject.nounPlural,
                _isAdmin = isAdmin === true,
                checked = [];

            if (region) {

                if (fromSelection) {
                    checked = region.getChecked();
                    if (checked.length < 1) {
                        Ext4.Msg.alert('Selection Error', 'At least one ' + nounSingular + ' must be selected from the checkboxes in order to use this feature.');
                        return;
                    }
                }

                var params = LABKEY.ActionURL.getParameters(region.requestURL),
                    jsonData = {
                        schemaName: region.schemaName,
                        queryName: region.queryName,
                        viewName: region.viewName,
                        dataRegionName: region.name,
                        requestURL: region.requestURL
                    };

                if (fromSelection) {
                    jsonData.selections = checked;
                }
                else {
                    jsonData.selectAll = true;
                }

                Ext4.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('participant-group', 'getParticipantsFromSelection.api', null, params),
                    method: 'POST',
                    jsonData: jsonData,
                    success: function(response) {

                        var json = Ext4.decode(response.responseText);

                        // create a window
                        Ext4.create('Study.window.ParticipantGroup', {
                            subject: {
                                nounSingular: nounSingular,
                                nounPlural: nounPlural
                            },
                            categoryParticipantIds: json.ptids.join(', '),
                            canEdit: true,
                            hideDataRegion: true,
                            isAdmin: _isAdmin,
                            autoShow: true,
                            listeners: {
                                aftersave: function() {
                                    region.clearSelected();
                                    region.refresh();
                                }
                            }
                        });
                    },
                    failure: LABKEY.Utils.displayAjaxErrorResponse
                });
            }
        }
    },

    constructor : function(config){
        Ext4.QuickTips.init();
        this.panelConfig = config;

        if (config.category) {
            config.type = config.category.type;
            config.shared = config.category.shared;
        }

        // issue 18500: alter the Define Ptid Group dialog for users that can't edit shared categories/groups
        if (!config.canEdit)
            config.hideDataRegion = true;

        Ext4.applyIf(config, {
            hideDataRegion : false,
            isAdmin : false,
            hasButtons : true,
            canShare : true,
            dataRegionName : 'demoDataRegion',
            width : window.innerWidth < 950 ? window.innerWidth : 950,
            height : config.hideDataRegion ? 325 : 500,
            type : config.type || 'manual',
            shared : config.shared || false,
            resizable : false,
            isUpdate : false
        });

        Ext4.apply(config, {
            title : (config.canEdit ? 'Define ' : 'View ') + Ext4.htmlEncode(config.subject.nounSingular) + ' Group',
            layout : 'fit',
            autoScroll : true,
            modal : true
        });

        this.callParent([config]);

        this.addEvents('aftersave', 'closewindow');
    },

    initComponent : function() {

        var demoStore = Ext4.create('LABKEY.ext4.data.Store', {
            name : 'demoStore',
            schemaName : 'study',
            queryName : 'DataSets',
            filterArray : [LABKEY.Filter.create('DemographicData', true)],
            columns : ['Label', 'Name'],
            sort : 'Label',
            listeners : {
                load :function(store, records, options){
                    if (records.length > 0){
                        infoCombo.setValue(records[0].get("Label"));
                        this.onDemographicSelect(infoCombo, records, 0);
                    }
                    else
                    {
                        LABKEY.Utils.signalWebDriverTest("dataRegionUpdate"); // Tests expect a data region
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
            fieldLabel: 'Select ' + Ext4.htmlEncode(this.panelConfig.subject.nounPlural) + ' from',
            labelAlign: 'top',
            padding: '15px 0 0 0',
            grow: true,
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
                {name : 'label', type : 'string'},
                {name : 'type',  type : 'string'},
                {name : 'rowId', type : 'int'},
                {name : 'shared',type : 'boolean'}
            ]
        });

        var categoryStore = Ext4.create('Ext.data.Store', {
            model : 'categoryModel',
            sorters : {property : 'label', direction : 'ASC'},
            listeners:{
                load: function(me){
                    if (this.category && me.findExact('rowId', this.category.rowId) > -1){
                        categoryCombo.setValue(this.category.rowId);
                    }
                },
                scope: this
            }
        });

        Ext4.Ajax.request({
            url : (LABKEY.ActionURL.buildURL("participant-group", "getParticipantCategories.api")),
            method : 'GET',
            success : function(resp){
                var o = Ext4.decode(resp.responseText);

                if (o.success && o.categories.length)
                {
                    var manualCategories = [];
                    for (var i=0; i < o.categories.length; i++)
                    {
                        if (o.categories[i].type === "manual")
                            manualCategories.push(o.categories[i]);
                    }
                    categoryStore.loadData(manualCategories);
                    categoryStore.fireEvent('load', categoryStore);
                }
            },
            failure : function(response, options){
                LABKEY.Utils.displayAjaxErrorResponse(response, options, false);
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
            emptyText : Ext4.htmlEncode(this.panelConfig.subject.nounSingular) + ' Category',
            fieldLabel: Ext4.htmlEncode(this.panelConfig.subject.nounSingular) + ' Category',
            labelAlign : 'top',
            grow : true,
            readOnly : !this.canEdit,
            maxWidth: defaultWidth,
            valueField : 'rowId',
            displayField : 'label',
            listConfig: { itemTpl: "{label:htmlEncode}" },
            triggerAction : 'all',
            listeners : {
                scope:this,
                change : function(combo, newValue, oldValue){
                    var shared = false;
                    var index = categoryStore.findExact('rowId', newValue);
                    if (index > -1) {
                        shared = categoryStore.getAt(index).data.shared;
                        Ext4.getCmp('sharedBox').setValue(shared);
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
                    readOnly : !this.canEdit,
                    emptyText : Ext4.htmlEncode(this.panelConfig.subject.nounSingular) + ' Group Label',
                    fieldLabel: Ext4.htmlEncode(this.panelConfig.subject.nounSingular) + ' Group Label',
                    labelAlign : 'top',
                    width: defaultWidth
                },
                {
                    xtype : 'textareafield',
                    name : 'participantIdentifiers',
                    id : 'participantIdentifiers',
                    readOnly : !this.canEdit,
                    emptyText : 'Enter ' + Ext4.htmlEncode(this.panelConfig.subject.nounSingular) + ' Identifiers Separated by Commas',
                    fieldLabel: Ext4.htmlEncode(this.panelConfig.subject.nounSingular) + ' Identifiers',
                    labelAlign : 'top',
                    width: defaultWidth
                },
                {
                    xtype: 'hiddenfield',
                    name : 'filters',
                    value: JSON.stringify(this.filters)
                },
                categoryCombo,
                {
                    xtype : 'checkboxfield',
                    name : 'sharedBox',
                    id : 'sharedBox',
                    boxLabel : 'Share Category?',
                    checked : this.shared,
                    readOnly : !this.canEdit,
                    listeners : {
                        scope : this,
                        afterrender : function(checkboxField){
                            Ext4.create('Ext.tip.ToolTip', {
                                trackMouse : true,
                                target : checkboxField.getEl(),
                                html : 'Share this ' + Ext4.htmlEncode(this.panelConfig.subject.nounSingular) +' category with all users'
                            });
                           this.addClass('share-group-rendered'); // Test marker class
                        }

                    }
                },
                {
                    xtype : 'button',
                    text : "Save",
                    margin: '3 3 3 0',
                    hidden : !this.canEdit,
                    handler : this.saveCategory
                },
                {
                    xtype : 'button',
                    text : this.canEdit ? "Cancel" : "Close",
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
        this.on('aftersave', this.close, this);
        this.on('show', function(win) {
            new Ext4.util.KeyNav(win.getEl(), {
                'enter': function() {
                    win.saveCategory();
                },
                scope: this
            });
        }, this, {single: true});
        
        this.items = [simplePanel];
        if (this.categoryParticipantIds) {
            simplePanel.queryById('participantIdentifiers').setValue(this.categoryParticipantIds);
        }
        if (this.groupLabel){
            simplePanel.queryById('groupLabel').setValue(this.groupLabel);
        }
        simplePanel.on('closewindow', this.close, this);
        this.callParent(arguments);
        if (this.hideDataRegion){
            this.on('donewithbuttons', function(){this.height = simplePanel.el.dom.scrollHeight + 25;});
        }
    },

    onDemographicSelect : function(combo, records, idx) {
        if (Ext4.isArray(records) && records.length > 0) {
            this.displayQueryWebPart(records[0].get("Name"));       // Use Name for getting query
        }
    },

    validate : function() {
        var fieldValues = this.down('panel').getValues(),
            label = fieldValues['groupLabel'],
            idStr = fieldValues['participantIdentifiers'],
            categoryCombo = this.queryById('participantCategory'),
            categoryStore = categoryCombo.getStore();

        if (categoryStore.findExact('label', categoryCombo.getRawValue()) > -1){
            categoryCombo.select(categoryStore.findRecord('label', categoryCombo.getRawValue()));
        }
        if (!label){
            Ext4.Msg.alert("Error", this.subject.nounSingular + " Group Label Required");
            return false;
        }
        if (!idStr){
            Ext4.Msg.alert("Error", "One or more " + this.subject.nounSingular + " Identifiers required");
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

        if (groupData.participantIds.length == 0)
        {
            Ext4.Msg.alert("Error", "One or more " + me.subject.nounSingular + " Identifiers required");
            return false;
        }

        var apiMethod = "saveParticipantGroup.api";
        if (me.isUpdate) {
            apiMethod = "updateParticipantGroup.api";
        }

        Ext4.Ajax.request({
            url : (LABKEY.ActionURL.buildURL("participant-group", apiMethod)),
            method : 'POST',
            success : LABKEY.Utils.getCallbackWrapper(function(data) {
                this.getEl().unmask();
                me.fireEvent('aftersave', data);
                if (me.grid){
                    me.grid.getStore().load();
                }
            }),
            failure : function(response, options){
                this.getEl().unmask();
                LABKEY.Utils.displayAjaxErrorResponse(response, options, false, "An error occurred trying to save:  ");
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
            categoryLabel = categoryStore.getAt(categoryStore.findExact("rowId", categoryId)).data.label;
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
            categoryType : categoryType
        };

        if (fieldValues["filters"] != undefined)
            groupData["filters"] = fieldValues["filters"];

        if (!Ext4.getCmp('sharedBox').getValue())
            groupData['categoryOwnerId'] = LABKEY.user.id;

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
                disableAnalytics: true,
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
        }
    },

    /**
     * Assumes the presence of a rendered QueryWebPart.
     */
    _getSelectedDemoParticipants : function(queryName, showRows) {

        var filterArray = LABKEY.DataRegions[this.dataRegionName].getUserFilterArray();
        var selectionKey = LABKEY.DataRegions[this.dataRegionName].selectionKey;

        LABKEY.Query.selectRows({
            schemaName: 'study',
            queryName: queryName,
            selectionKey: selectionKey,
            showRows: showRows,
            filterArray: filterArray,
            columns: this.subject.columnName,
            success: function(data) {

                var ptidCategoryPanel = this.getComponent('simplePanel');
                var tempIds = ptidCategoryPanel.getValues()['participantIdentifiers'];
                var tempIdsArray = tempIds.split(',');
                var col = this.subject.nounColumnName;
                var value;

                for (var i = 0; i < tempIdsArray.length; i++) {
                    tempIdsArray[i] = tempIdsArray[i].trim();
                }

                //append the selected ids to the current list
                for (i = 0; i < data.rows.length; i++) {
                    value = data.rows[i][col];
                    if (tempIdsArray.indexOf(value) == -1) {
                        tempIds += (tempIds.length ? ', ' : '') + value;
                        tempIdsArray.push(value);
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
