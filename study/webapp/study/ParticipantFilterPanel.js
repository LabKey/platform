/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresScript("study/ReportFilterPanel.js");

Ext4.define('LABKEY.study.ParticipantFilterPanel', {

    extend : 'Ext.panel.Panel',

    bubbleEvents : ['select', 'selectionchange', 'itemmouseenter', 'itemmouseleave'],

    constructor : function(config) {

        Ext4.applyIf(config, {
            layout    : 'fit',
            allowAll  : true,
            border    : false, frame : false,
            subjectNoun : {singular : 'Participant', plural : 'Participants'}
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

        if (!this.filters) {
            this.filters = [{
                store       : Ext4.create('Ext.data.Store', cohortConfig),
                selection   : this.selection,
                description : 'In these Cohorts:'
            },{
                store       : Ext4.create('Ext.data.Store', groupConfig),
                selection   : this.selection,
                description : 'In these Groups:'
            }];
        }

        this.filterPanel = Ext4.create('LABKEY.ext4.ReportFilterPanel', {
            layout   : 'fit',
            border   : false, frame : false,
            allowAll : this.allowAll,
            filters  : this.filters
        });

        this.items = [this.filterPanel];

        this.callParent([arguments]);
    },

    getFilterPanel : function() {
        return this.filterPanel;
    }
});