/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresScript("study/ReportFilterPanel.js"); // needed for xtype:'labkey-filterselectpanel'

/**
 * @cfg displayMode Determines what type of filtering is supported, either 'PARTICIPANT', 'GROUP' or 'BOTH'.
 * If BOTH is used, a radiogroup will allow toggling between participants and groups.  Defaults to GROUP.
 */
Ext4.define('LABKEY.study.ParticipantFilterPanel', {

    extend : 'Ext.panel.Panel',

    bubbleEvents : ['select', 'selectionchange', 'itemmouseenter', 'itemmouseleave'],

    constructor : function(config) {

        Ext4.applyIf(config, {
            allowAll  : true,
            normalWrap : false,
            cls       : 'participant-filter-panel',
            border    : false, frame : false,
            bodyStyle : 'padding: 5px;',
            includeParticipantIds : false,
            includeUnassigned : true,
            subjectNoun : {singular : 'Participant', plural : 'Participants', columnName: 'Participant'}
        });

        // models Participant Groups and Cohorts mixed
        Ext4.define('LABKEY.study.GroupCohort', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'id'},
                {name : 'categoryId'},
                {name : 'enrolled'},
                {name : 'label'},
                {name : 'description'},
                {name : 'participantIds'},
                {name : 'type'}
            ]
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.items = this.getItems();

        this.callParent();
    },

    //the purpose of this method is to support config to filter on either participants or groups only, or provide a toggle between them
    getItems: function(){
        if(this.displayMode == 'BOTH'){
            this.filterType = this.filterType || 'group';
            return [{
                xtype: 'radiogroup',
                itemId: 'filterType',
                columns: 1,
                vertical: true,
                width: 180,
                style: 'padding: 5px;margin-bottom: 10px;',
                listeners: {
                    scope: this,
                    buffer: 20,
                    change: this.onFilterTypeChange
                },
                items: [{
                    xtype: 'radio',
                    boxLabel: 'By Participant',
                    name: 'filterType',
                    inputValue: 'participant',
                    checked: this.filterType == 'participant'
                },{
                    xtype: 'radio',
                    boxLabel: 'By Group',
                    name: 'filterType',
                    inputValue: 'group',
                    checked: this.filterType != 'participant'
                }]
            },{
                xtype   : 'panel',
                border  : false,
                layout  : {
                    type : 'card',
                    deferredRender : true
                },
                itemId  : 'filterArea',
                activeItem : this.filterType == 'participant' ? 0 : 1,
                items: [this.getParticipantPanelCfg(), this.getGroupPanelCfg()]
            }];
        }
        else if (this.displayMode == 'PARTICIPANT'){
            this.filterType = 'participant';
            return [this.getParticipantPanelCfg()];
        }
        //for legacy reasons, default to group only
        else {
            this.filterType = 'group';
            return [this.getGroupPanelCfg()];
        }
    },

    onFilterTypeChange: function(radioGroup, val, oldVal, event){
        var filterArea = this.down('#filterArea');
        var activeItem;
        this.filterType = val.filterType;

        if(val.filterType == 'participant'){
            activeItem = 0;
        }
        else {
            activeItem = 1;
        }

        if(oldVal && oldVal.filterType != val.filterType)
        {
            var selections = this.getSelection(true, false);
            filterArea.getLayout().setActiveItem(activeItem);

            if(val.filterType == 'participant')
            {
                var groups = [];
                for (var i=0;i<selections.length;i++){
                    groups.push(selections[i].data);
                }
                if (groups.length > 0)
                {
                    Ext4.Ajax.request({
                        url      : LABKEY.ActionURL.buildURL('participant-group', 'getSubjectsFromGroups.api'),
                        method   : 'POST',
                        jsonData : Ext4.encode({
                            groups : groups
                        }),
                        success  : this.onResolveSubjects,
                        failure  : LABKEY.Utils.displayAjaxErrorResponse,
                        scope    : this
                    });
                }
                else
                {
                    this.getFilterPanel().deselectAll(true);
                    this.setParticipantSelectionDirty(false);
                }
            }
            else
            {
                if (this.participantSelectionChanged)
                {
                    Ext4.Msg.confirm('Filter Change', 'By changing filter types, your selections will be lost.  Do you want to continue?', function(btn){
                        if(btn == 'yes'){
                            this.getFilterPanel().deselectAll(true);
                            this.fireEvent('selectionchange');
                        }
                        else {
                            var rg = this.down('radiogroup');
                            rg.suspendEvents();
                            rg.setValue({filterType: 'participant'});
                            rg.resumeEvents();
                            filterArea.getLayout().setActiveItem(0);
                        }
                    }, this);
                }
            }
        }
    },

    onResolveSubjects: function(response){
        var panel = this.getFilterPanel().getFilterPanels()[0];
        if(!panel.store.getCount()){
            this.onResolveSubjects.defer(20, this, [response]);
            return;
        }

        var json = Ext4.decode(response.responseText);
        var subjects = json.subjects ? json.subjects : [];

        if(!subjects.length){
            panel.selectAll.defer(20, panel);
        }
        else {
            var recs = [];
            for (var i=0;i<subjects.length;i++){
                var recIdx = panel.store.find('id', subjects[i]);
                if(recIdx > -1)
                    recs.push(panel.store.getAt(recIdx));
            }

            var sm = panel.getGrid().getSelectionModel();
            sm.select.defer(10, sm, [recs]);
        }
        this.setParticipantSelectionDirty.defer(50, this, [false]);
    },

    setParticipantSelectionDirty : function(dirty) {
        this.participantSelectionChanged = dirty;
    },

    getGroupPanelCfg: function(){

        var groupPanel = Ext4.create('Ext.panel.Panel', {
            layout      : 'fit',
            border      : false,
            frame       : false
        });

        // need to get the categories for the panel sections
        Ext4.Ajax.request({
            url    : LABKEY.ActionURL.buildURL('participant-group', 'browseParticipantGroups.api'),
            success : function(response){

                var o = Ext4.decode(response.responseText);
                var groups = o.groups || [];

                // parse out the group types
                var categories = {};
                var categoryName = {};
                for (var i=0; i < groups.length; i++)
                {
                    var row = groups[i];
                    var key;

                    if (row.type == 'cohort') {
                        key = row.type;
                        categoryName[key] = 'Cohorts';
                    } else {
                        key = row.categoryId;
                        if (row.category)
                            categoryName[key] = row.category.label;
                    }

                    var groupList = categories[key] || [];

                    groupList.push({
                        id          : row.id,
                        categoryId  : row.categoryId,
                        enrolled    : row.enrolled,
                        label       : row.label,
                        description : row.description,
                        participantIds: row.participantIds,
                        type        : row.type});

                    categories[key] = groupList;
                }

                var storeConfig = {
                    pageSize : 100,
                    model    : 'LABKEY.study.GroupCohort'
                };

                var groupSectionCfg = [];
                var maxSelection = undefined;
                if (this.maxInitSelection !== undefined)
                    maxSelection = this.maxInitSelection;

                if (categories['cohort'])
                {
                    var cohortConfig = Ext4.clone(storeConfig);
                    cohortConfig.data = categories['cohort'];

                    groupSectionCfg.push({
                        normalWrap  : this.normalWrap,
                        store       : Ext4.create('Ext.data.Store', cohortConfig),
                        selection   : this.getInitialSelection('cohort'),
                        maxInitSelection : maxSelection,
                        description : 'Cohorts'
                    });

                    maxSelection = maxSelection ? Math.max(0, maxSelection - cohortConfig.data.length) : maxSelection;
                }

                for (var type in categories) {
                    if (categories.hasOwnProperty(type))
                    {
                        if (type == 'cohort')
                            continue;

                        var groupConfig = Ext4.clone(storeConfig);
                        groupConfig.data = categories[type];

                        groupSectionCfg.push({

                            normalWrap  : this.normalWrap,
                            store       : Ext4.create('Ext.data.Store', groupConfig),
                            selection   : this.getInitialSelection('participantGroup'),
                            maxInitSelection : maxSelection,
                            description : categoryName[type]
                        });

                        maxSelection = maxSelection ? Math.max(0, maxSelection - groupConfig.data.length) : maxSelection;
                    }
                }

                groupPanel.add({
                    xtype : 'labkey-filterselectpanel',
                    itemId   : 'filterPanel',
                    border   : false, frame : false,
                    allowGlobalAll  : this.allowAll,
                    allowAll : this.allowAll,
                    sections : groupSectionCfg
                })
            },
            params : {
                includeParticipantIds   : this.includeParticipantIds,
                includeUnassigned       : this.includeUnassigned,
                type                    : ['participantGroup', 'cohort']
            },
            failure : this.onFailure,
            scope   : this
        });

        return groupPanel;
    },

    getParticipantPanelCfg: function(){
        if(!this.participantSectionCfg){
            var store = Ext4.create('LABKEY.ext4.Store', {
                schemaName: 'study',
                sql: 'select "' + this.subjectNoun.columnName + '" as id, "' + this.subjectNoun.columnName + '" as label, \'participant\' as type FROM study."' + this.subjectNoun.singular + '"',
                queryName: this.subjectNoun.singular,
                autoLoad: true
            });

            var selections = this.getInitialSelection('participant');
            this.setParticipantSelectionDirty(selections && selections.length > 0);

            this.participantSectionCfg = [{
                store       : store,
                selection   : selections,
                sectionName : 'participant',
                maxInitSelection : this.maxInitSelection,
                description : this.subjectNoun.plural
            }]
        }

        return {
            xtype    : 'labkey-filterselectpanel',
            itemId   : 'filterPanel',
            panelName: 'participant',
            border   : false, frame : false,
            allowAll : true,
            hidden   : this.displayMode == 'BOTH',
            sections : this.participantSectionCfg,
            schemaName: 'study',
            //TODO: this should be able to become a more generalized boundColumn-based filter panel
            queryName: this.subjectNoun.singular,
            boundColumn: {
                displayField: this.subjectNoun.columnName,
                displayFieldSqlType: 'string',
                facetingBehaviorType: 'AUTOMATIC',
                lookup: {

                },
                dimension: true
            },
            listeners : {
                selectionchange : function(){
                    this.setParticipantSelectionDirty(true);},
                scope : this
            }
        }
    },

    initSelection: function(){
        var panel = this.getFilterPanel();
        if(!panel){
            this.initSelection.defer(20, this);
            return;
        }
        panel.initSelection();
    },

    getSelection: function(collapsed, skipIfAllSelected){
        var panel = this.getFilterPanel();

        return panel ? panel.getSelection(collapsed, skipIfAllSelected) : [];
    },

    getInitialSelection: function(type){
        if(this.selection){
            var selections = [];
            Ext4.each(this.selection, function(rec){
                if(rec.type == type)
                    selections.push(rec)
            }, this);
            return selections;
        }
    },

    getFilterPanel : function() {
        return this.down('#filterPanel[hidden=false]');
    },

    getFilterArray: function(){
        //TODO: we need server-side changes in order to support this properly
    },

    getFilterType: function(){
        return this.filterType;
    }
});