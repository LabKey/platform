/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresScript("study/ReportFilterPanel.js");
LABKEY.requiresScript("extWidgets/FilterPanel.js");

/**
 * @cfg displayMode Determines what type of filtering is supported, either 'PARTICIPANT', 'GROUP' or 'BOTH'.  If BOTH is used, a radiogroup will allow toggling between
 * participants and groups.  Defaults to GROUP.
 */
Ext4.define('LABKEY.study.ParticipantFilterPanel', {

    extend : 'Ext.panel.Panel',

    bubbleEvents : ['select', 'selectionchange', 'itemmouseenter', 'itemmouseleave'],

    constructor : function(config) {

        Ext4.applyIf(config, {
            layout    : 'fit',
            allowAll  : true,
            cls       : 'participant-filter-panel',
            border    : false, frame : false,
            bodyStyle : 'padding: 5px;',
            subjectNoun : {singular : 'Participant', plural : 'Participants', columnName: 'Participant'}
        });

        // models Participant Groups and Cohorts mixed
        Ext4.define('LABKEY.study.GroupCohort', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'id'},
                {name : 'label'},
                {name : 'description'},
                {name : 'type'}
            ]
        });

        this.callParent([config]);
    },

    initComponent : function() {
        this.items = this.getItems();

        this.callParent([arguments]);
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
                style: 'padding: 5px;padding-bottom: 15px;',
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
                xtype: 'container',
                itemId: 'filterArea',
                items: [this.filterType == 'participant' ? this.getParticipantPanelCfg() : this.getGroupPanelCfg()]
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
        var cfg;
        this.filterType = val.filterType;

        if(val.filterType == 'participant'){
            cfg = this.getParticipantPanelCfg();
        }
        else {
            cfg = this.getGroupPanelCfg();
        }

        if(oldVal && oldVal.filterType != val.filterType){
            if(val.filterType == 'participant'){
                var selections = this.getSelection(true, true);
                filterArea.removeAll(true);
                filterArea.add(cfg);

                var groups = [];
                for (var i=0;i<selections.length;i++){
                    groups.push(selections[i].data);
                }
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
            else {
                Ext4.Msg.confirm('Filter Change', 'By changing filter types, your selections will be lost.  Do you want to continue?', function(btn){
                    if(btn == 'yes'){
                        filterArea.removeAll(true);
                        filterArea.add(cfg);
                        this.initSelection();
                        this.fireEvent('selectionchange')
                    }
                    else {
                        var rg = this.down('radiogroup');
                        rg.suspendEvents();
                        rg.setValue({filterType: 'participant'});
                        rg.resumeEvents();
                    }
                }, this);
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
    },

    getGroupPanelCfg: function(){
        if (!this.groupSectionCfg) {
            var storeConfig = {
                pageSize : 100,
                model    : 'LABKEY.study.GroupCohort',
                autoLoad : true,
                proxy    : {
                    type   : 'ajax',
                    url    : LABKEY.ActionURL.buildURL('participant-group', 'browseParticipantGroups.api'),
                    reader : {
                        type : 'json',
                        root : 'groups'
                    }
                }
            };

            var cohortConfig = Ext4.clone(storeConfig);
            Ext4.apply(cohortConfig.proxy, {
                extraParams : { type : 'cohort'}
            });
            var groupConfig = Ext4.clone(storeConfig);
            Ext4.apply(groupConfig.proxy, {
                extraParams : { type : 'participantGroup'}
            });

            this.groupSectionCfg = [{
                store       : Ext4.create('Ext.data.Store', cohortConfig),
                selection   : this.getInitialSelection('cohort'),
                description : '<b class="filter-description">Cohorts</b>'
            },{
                store       : Ext4.create('Ext.data.Store', groupConfig),
                selection   : this.getInitialSelection('participantGroup'),
                description : '<b class="filter-description">Groups</b>'
            }];
        }

        return {
            xtype    : 'labkey-filterselectpanel',
            itemId   : 'filterPanel',
            flex     : 1,
            border   : false, frame : false,
            allowAll : true,
            sections : this.groupSectionCfg
        }
    },

    getParticipantPanelCfg: function(){
        if(!this.participantSectionCfg){
            var store = Ext4.create('LABKEY.ext4.Store', {
                schemaName: 'study',
                sql: 'select "' + this.subjectNoun.columnName + '" as id, "' + this.subjectNoun.columnName + '" as label, \'participant\' as type FROM study."' + this.subjectNoun.singular + '"',
                queryName: this.subjectNoun.singular,
                autoLoad: true
            });

            this.participantSectionCfg = [{
                store       : store,
                selection   : this.getInitialSelection('participant'),
                description : '<b class="filter-description">' + this.subjectNoun.plural + '</b>'
            }]
        }

        return {
            xtype    : 'labkey-filterselectpanel',
            itemId   : 'filterPanel',
            flex     : 1,
            border   : false, frame : false,
            allowAll : true,
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
            }
        }
    },

    initSelection: function(){
        this.getFilterPanel().initSelection();
    },

    getSelection: function(collapsed, skipIfAllSelected){
        return this.getFilterPanel().getSelection(collapsed, skipIfAllSelected);
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
        return this.down('#filterPanel');
    },

    getFilterArray: function(){
        //TODO: we need server-side changes in order to support this properly
    },

    getFilterType: function(){
        return this.filterType
    }
});