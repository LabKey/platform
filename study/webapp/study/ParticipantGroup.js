/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext.namespace("LABKEY.study");

Ext.QuickTips.init();
Ext.GuidedTips.init();

LABKEY.study.ParticipantGroupDialog = Ext.extend(Ext.Window, {

    constructor : function(config){
        this.panelConfig = config;
        this.addEvents('aftersave');
        var h = config.hideDataRegion ? 500 : Ext.getBody().getViewSize().height * .75;

        if(h < 500)
            h = 500;

        LABKEY.study.ParticipantGroupDialog.superclass.constructor.call(this, {
            cls: 'extContainer',
            title: 'Define ' + Ext.util.Format.htmlEncode(config.subject.nounSingular) + ' Group',
            layout:'fit',
            width: Ext.getBody().getViewSize().width < 850 ? Ext.getBody().getViewSize().width * .9 : 800,
            height: h,
            modal: true,
            closeAction:'close'
        });
    },

    initComponent : function() {
        var groupPanel = new LABKEY.study.ParticipantGroupPanel(this.panelConfig);

        groupPanel.on('closeWindow', this.close, this);
        groupPanel.on('aftersave', function(){this.fireEvent('aftersave'); this.close();}, this);
        this.items = [groupPanel];
        
        LABKEY.study.ParticipantGroupDialog.superclass.initComponent.call(this);
    }
});

/**
 * Panel to take user input for the label and list of participant ids for a classfication to be created or edited
 * @cfg {Integer} categoryRowId the rowId of the category to be edited (null if create new)
 * @cfg {Integer} groupRowId the rowId of the group to be edited (null if create new)
 * @cfg {String} groupLabel the current label of the group to be edited (null if create new)
 * @cfg {String} categoryLabel the current label of the category to be edited (null if "list" type)
 * @cfg {Array} categoryParticipantIds the string representation of the ptid ids array of the category to be edited (null if create new)
 */
LABKEY.study.ParticipantGroupPanel = Ext.extend(Ext.Panel, {
    constructor : function(config){
        Ext.apply(config, {
            layout: 'form',
            bodyStyle:'padding:20px;',
            border: false,
            autoScroll: true
        });

        this.addEvents('closeWindow');
        this.addEvents('aftersave');

        this.isAdmin = config.isAdmin || false;
        this.subject = config.subject;
        this.hasButtons = config.hasButtons || true;
        this.canShare = config.canShare || true;
        this.dataRegionName = config.dataRegionName || 'demoDataRegion';

        LABKEY.study.ParticipantGroupPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        var buttons = [];

        if (this.hasButtons)
        {
            buttons.push({text:'Save', handler: this.saveCategory, disabled : !this.canEdit, scope: this});
            buttons.push({text:'Cancel', handler: function(){this.fireEvent('closeWindow');}, scope: this});
        }
        this.categoryStore = new Ext.data.JsonStore({
            proxy: new Ext.data.HttpProxy({
                url : LABKEY.ActionURL.buildURL("participant-group", "getParticipantCategories"),
                method : 'POST'
            }),
            root: 'categories',
            idProperty: 'rowId',
            baseParams: {
                categoryType: 'manual'
            },
            fields: [
                {name: 'rowId', type: 'integer'},
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
                    if(this.category && this.categoryStore.find('rowId', this.category.rowId) > -1){
                        this.categoryCombo.setValue(this.category.rowId);
                    }
                },
                scope: this
            },
            autoLoad: true
        });

        this.categoryCombo = new Ext.form.ComboBox({
            id: 'participantCategory',
            xtype: 'combo',
            editable: true,
            mode: 'local',
            anchor: '100%',
            fieldLabel: this.subject.nounSingular + " Category",
            emptyText: this.subject.nounSingular + " Category",
            store: this.categoryStore,
            valueField: 'rowId',
            displayField: 'label',
            triggerAction: 'all',
            listeners: {
                scope:this,
                specialkey: function(f, e){
                    if(e.getKey() == e.ENTER){
                        this.saveCategory();
                    }
                },
                change: function(combo, newValue, oldValue){
                    var shared = false;
                    var index = this.categoryStore.find('rowId', newValue);
                    if(index > -1){
                       shared = this.categoryStore.getAt(index).data.shared;
                    }
                    this.sharedField.setValue(shared);
                }
            }
        });


        this.categoryPanel = new Ext.form.FormPanel({
            border: false,
            defaults : {disabled : !this.canEdit},
            labelAlign: 'top',
            items: [{
                id: 'groupLabel',
                xtype: 'textfield',
                value: this.groupLabel,
                fieldLabel: this.subject.nounSingular + ' Group Label',
                emptyText: this.subject.nounSingular + ' Group Label',
                allowBlank: false,
                selectOnFocus: true,
                preventMark: true,
                anchor: '100%',
                listeners: {
                    scope:this,
                    specialkey: function(f, e){
                        if(e.getKey() == e.ENTER){
                            this.saveCategory();
                        }
                    }
                }
            },{
                id: 'categoryIdentifiers',
                xtype: 'textarea',
                value: this.categoryParticipantIds,
                fieldLabel: this.subject.nounSingular + ' Identifiers',
                emptyText: 'Enter ' + this.subject.nounSingular + ' Identifiers Separated by Commas',
                allowBlank: false,
                preventMark: true,
                height: 100,
                anchor: '100%'
            },
            this.categoryCombo],
            buttons: buttons
        });

        if (!this.hideDataRegion)
        {
            var demoStore = new LABKEY.ext.Store({
                schemaName: 'study',
                queryName: 'DataSets',
                filterArray: [LABKEY.Filter.create('DemographicData', true)],
                columns: 'Label',
                sort: 'Label',
                listeners: {
                    scope: this,
                    'load': function(store, records, options)
                    {
                        // on load, select the first item in the combobox
                        if (records.length > 0)
                        {
                            this.demoCombobox.setValue(records[0].get("Label"));
                            this.demoCombobox.fireEvent('select', this.demoCombobox, records[0], 0);
                        }
                    }
                },
                autoLoad: true
            });

            this.demoCombobox = new Ext.form.ComboBox({
                triggerAction: 'all',
                mode: 'local',
                store: demoStore,
                valueField: 'Label',
                displayField: 'Label',
                fieldLabel: 'Select ' + Ext.util.Format.htmlEncode(this.subject.nounPlural) + ' from',
                labelStyle: 'width: 150px;',
                labelSeparator : '',
                minListWidth : 300,
                disabled : !this.canEdit,
                listeners: {
                    scope: this,
                    'select': function(cmp, record, index){
                        this.getDemoQueryWebPart(record.get("Label"));
                    }
                }
            });
        }

        var sharedTip = '' +
            '<div>' +
                '<div class=\'g-tip-subheader\'>' +
                    'Share this ' + Ext.util.Format.htmlEncode(this.subject.nounSingular).toLowerCase() + ' category with all users' +
                '</div>' +
            '</div>';

        this.sharedField = new Ext.form.Checkbox({
            fieldLabel     : 'Share Category?',
            name           : 'Shared',
            labelSeparator : '',
            hidden         : !this.canShare,
            disabled       : !this.isAdmin || !this.canEdit,
            gtip           : sharedTip
        });

        Ext.QuickTips.register({
            target : this.sharedField,
            text   : 'Share this ' + Ext.util.Format.htmlEncode(this.subject.nounSingular) + ' group with all users'
        });

        if (!this.categoryShared || this.categoryShared == "false")
            this.sharedField.setValue(false);
        else
            this.sharedField.setValue(true);

        var categoryItems = [this.sharedField];
        if (this.demoCombobox)
            categoryItems.push(this.demoCombobox);

        this.items = [
            this.categoryPanel,
            {
                border : false, frame: false,
                style  : this.hasButtons ? 'margin-top: -22px;' : '',
                width  : 450,
                items  : [{
                    layout : 'column',
                    border : false, frame : false,
                    defeaults : { border: false, frame: false },
                    items  : [{
                        layout      : 'form',
                        columnWidth : 1,
                        border : false, frame : false,
                        items : categoryItems
                    }]
                }]
            }
        ];

        if (!this.hideDataRegion)
            this.items.push({xtype: 'panel', id: 'demoQueryWebPartPanel', border: false});

        LABKEY.study.ParticipantGroupPanel.superclass.initComponent.call(this);
    },

    validate: function() {

        // get the label and ids from the form
        var fieldValues = this.categoryPanel.getForm().getFieldValues();
        var label = fieldValues["groupLabel"];
        var idStr = fieldValues["categoryIdentifiers"];

        // make sure the label and idStr are not null
        if (!label)
        {
            Ext.Msg.alert("Error", this.subject.nounSingular + " Category Label required.");
            return false;
        }
        if (!idStr)
        {
            Ext.Msg.alert("Error", "One or more " + this.subject.nounSingular + " Identifiers required.");
            return false;
        }
        return true;
    },

    /**
     * Saves the selected categories. 
     */
    saveCategory: function()
    {
         if (!this.validate())
            return;
        var groupData = this.getGroupData();

        Ext.Ajax.request(
        {
            url : (LABKEY.ActionURL.buildURL("participant-group", "saveParticipantGroup")),
            method : 'POST',
            success: function(){
                this.getEl().unmask();
                this.fireEvent('aftersave');
                _grid.getStore().reload();
            },
            failure: function(response, options){
                this.getEl().unmask();
                LABKEY.Utils.displayAjaxErrorResponse(response, options, false, 'An error occurred:<br>');
            },
            jsonData : groupData,
            headers : {'Content-Type' : 'application/json'},
            scope: this
        });
    },

    getGroupData: function(){
        var fieldValues = this.categoryPanel.getForm().getFieldValues();
        var ptids = fieldValues["categoryIdentifiers"].split(',');
        var categoryLabel;
        var categoryId;
        var categoryType;

        if(typeof this.categoryCombo.getValue() == 'number'){
            categoryId = this.categoryCombo.getValue();
            categoryLabel = this.categoryStore.getAt(this.categoryStore.find("rowId", categoryId)).data.label;
            categoryType = 'manual';
        } else {
            categoryLabel = this.categoryCombo.getRawValue(); //Have to get the raw value so new categories will be seen.
            categoryType = categoryLabel == '' ? 'list' : 'manual';

            if(categoryType == 'list' && (this.category && this.category.type == 'list')){
                categoryId = this.category.rowId;
            }
        }

        for(var i = 0; i < ptids.length; i++)
        {
            ptids[i] = ptids[i].trim();
        }

        var groupData = {
            label: fieldValues["groupLabel"],
            participantIds: ptids,
            categoryLabel: categoryLabel,
            categoryType: categoryType,
            categoryShared : this.sharedField.getValue()
        };

        if(categoryId !== null && categoryId != undefined){
            groupData.categoryId = categoryId;
        }

        if(this.groupRowId !== null && this.groupRowId != undefined){
            groupData.rowId = this.groupRowId;
        }

        return groupData;

    },

    getDemoQueryWebPart: function(queryName)
    {
        var ptidCategoryPanel = this;
        var initialLoad = true;

        var demoQueryWebPart =  new LABKEY.QueryWebPart({
            renderTo: 'demoQueryWebPartPanel',
            dataRegionName: this.dataRegionName,
            schemaName: 'study',
            queryName: queryName,
            frame: 'none',
            border: false,
            showRecordSelectors: true,
            showUpdateColumn: false,
            maskEl: ptidCategoryPanel.getEl(),
            buttonBar: {
                position: (this.canEdit ? 'top' : 'none'),
                includeStandardButtons: false,
                items:[{
                    text: 'Add Selected',
                    requiresSelection: true,
                    handler: function(){
                        ptidCategoryPanel.getSelectedDemoParticipants(queryName, "selected");
                    }
                },{
                    text: 'Add All',
                    handler: function(){
                        // todo: fix this to only add the visible records (i.e. if filter applied to dataregion)
                        ptidCategoryPanel.getSelectedDemoParticipants(queryName, "all");
                    }
                }]
            },
            success: function(){
                // clear any cached selections from the last time this dataRegion was used
                if (initialLoad)
                {
                    Ext.ComponentMgr.get(this.dataRegionName).clearSelected();
                    initialLoad = false;
                }
            }
        });
    },

    getSelectedDemoParticipants: function(queryName, showStr) {
        var ptidCategoryPanel = this;

        // convert user filters from data region to expected filterArray
        var filters = Ext.ComponentMgr.get(this.dataRegionName).getUserFilterArray();

        // get the selected rows from the QWP data region
        LABKEY.Query.selectRows({
            schemaName: 'study',
            queryName: queryName,
            selectionKey: Ext.ComponentMgr.get(this.dataRegionName).selectionKey,
            showRows: showStr,
            filterArray: filters,
            columns: this.subject.columnName,
            scope: this,
            success: function(data){
                // get the current list of participant ids from the form
                var classPanelValues = ptidCategoryPanel.categoryPanel.getForm().getFieldValues();
                var tempIds = classPanelValues["categoryIdentifiers"];

                // also store the ids as an array to check if the ids to add already exist
                var tempIdsArray = tempIds.split(",");
                for (var i = 0; i < tempIdsArray.length; i++)
                {
                    tempIdsArray[i] = tempIdsArray[i].trim();
                }

                // append the selected ids to the current list
                if (data.rows.length > 0)
                {
                   for (var i = 0; i < data.rows.length; i++)
                   {
                       if (tempIdsArray.indexOf(data.rows[i][this.subject.nounColumnName]) == -1)
                       {
                           tempIds += (tempIds.length > 0 ? ", " : "") + data.rows[i][this.subject.nounColumnName];
                           tempIdsArray.push(data.rows[i][this.subject.nounColumnName]);
                       }
                   }
                }

                // put the new list of ids into the form
                ptidCategoryPanel.categoryPanel.getForm().setValues({
                   categoryIdentifiers: tempIds
                });
            }
        });
    }
/*

    getFilterTypeByURLSuffix: function(op)
    {
        // loop through the labkey filter types to match the URL suffix
        for (var type in LABKEY.Filter.Types)
        {
            if (LABKEY.Filter.Types[type].getURLSuffix() == op)
            {
                return LABKEY.Filter.Types[type];
            }
        }
        return null;
    }
*/
});
