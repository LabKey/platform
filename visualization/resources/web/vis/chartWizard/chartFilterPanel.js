/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.vis.ChartFilterPanel', {
    extend: 'Ext.panel.Panel',

    cls: 'chart-filter-panel',
    layout: 'accordion',
    fill: false,
    width: 220,
    minWidth: 220,
    border: false,
    split: true,
    collapsible: true,
    title: 'Filters',
    titleCollapse: true,

    subject: null,
    subjectSelection: null,

    initComponent : function ()
    {
        var items = [];
        if (this.hasValidStudy())
        {
            items.push(this.getParticipantSelector());
            items.push(this.getGroupSelector());
        }
        this.items = items;

        this.callParent();

        // after render set the initial state of the two selector panels
        this.on('afterRender', function() {
            this.setOptionsForGroupLayout(this.subjectSelection == 'groups');
        }, this);
    },

    hasValidStudy : function()
    {
        var studyCtx = LABKEY.getModuleContext('study');
        return Ext4.isDefined(studyCtx) && Ext4.isString(studyCtx.timepointType);
    },

    getParticipantSelector : function()
    {
        if (!this.participantSelector)
        {
            this.participantSelector = Ext4.create('LABKEY.vis.ChartFilterParticipantSelector', {
                subject: this.subjectSelection != 'groups' && this.subject != null ? this.subject : {},
                collapsed: this.subjectSelection != "subjects",
                bubbleEvents: [
                    'chartDefinitionChanged',
                    'measureMetadataRequestPending',
                    'measureMetadataRequestComplete',
                    'switchToGroupLayout'
                ]
            });
        }

        return this.participantSelector;
    },

    getGroupSelector : function()
    {
        if (!this.groupsSelector)
        {
            this.groupsSelector = Ext4.create('LABKEY.vis.ChartFilterGroupSelector', {
                subject: this.subjectSelection == 'groups' && this.subject != null ? this.subject : {},
                collapsed: this.subjectSelection != 'groups',
                bubbleEvents: [
                    'chartDefinitionChanged',
                    'measureMetadataRequestPending',
                    'measureMetadataRequestComplete'
                ]
            });
        }

        return this.groupsSelector;
    },

    setOptionsForGroupLayout : function(groupLayoutSelected)
    {
        // if the filters panel is collapsed, first open it up
        if (this.collapsed)
            this.expand();

        if (this.hasValidStudy())
        {
            if (groupLayoutSelected)
            {
                this.getGroupSelector().show();
                this.getGroupSelector().expand();
                this.getParticipantSelector().hide();
            }
            else
            {
                this.getParticipantSelector().show();
                this.getParticipantSelector().expand();
                this.getGroupSelector().hide();
            }
        }
    },

    expandDefaultPanel : function()
    {
        if (this.getParticipantSelector().isVisible())
            this.getParticipantSelector().expand();
        else
            this.getGroupSelector().expand();
    },

    getSubject : function(asGroups, displayIndividual)
    {
        if (!this.hasValidStudy())
            return null;
        else if (asGroups)
            return this.getGroupSelector().getSubject(displayIndividual);
        else
            return this.getParticipantSelector().getSubject();
    },

    getSubjectValues : function()
    {
        return !this.hasValidStudy() ? null : this.getParticipantSelector().getSubjectValues();
    },

    getGroupValues : function()
    {
        return !this.hasValidStudy() ? null : this.getGroupSelector().getGroupValues();
    }
});

Ext4.define('LABKEY.vis.ChartFilterParticipantSelector', {
    extend : 'Ext.panel.Panel',

    border: false,
    autoScroll: true,
    maxInitSelection: 5,

    constructor : function(config)
    {
        config.title = this.getStudyModuleSubjectContext().nounPlural;

        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged',
            'switchToGroupLayout',
            'measureMetadataRequestPending',
            'measureMetadataRequestComplete'
        );

        // fix the issue with the ptid list hidden for saved chart with a measure dimension panel
        this.on('expand', function(){
            if (!this.hidden)
                this.show();
        });
    },

    getStudyModuleSubjectContext: function()
    {
        return LABKEY.vis.TimeChartHelper.getStudySubjectInfo();
    },

    getSubjectValues: function()
    {
        if (this.ptidFilterPanel)
            return;

        // issue 22254: if there are too many ptids in a study, suggest that the user switch to plot by group
        this.switchToGroupPanel = Ext4.create('Ext.panel.Panel', {
            padding: 5,
            border: false,
            hidden: true,
            items: [{
                xtype: 'label',
                style: 'font-size:90%;',
                text: 'There are a large number of ' + this.getStudyModuleSubjectContext().nounPlural.toLowerCase()
                + ' in this study. Would you like to switch to plot by '
                + this.getStudyModuleSubjectContext().nounSingular.toLowerCase() + ' groups?'
            }],
            buttonAlign: 'left',
            buttons: [{
                text: 'Yes', id: 'switchToGroupsYes', height: 20, scope: this,
                handler: function(){
                    this.fireEvent('switchToGroupLayout');
                    this.switchToGroupPanel.hide();
                }
            },{
                text: 'No', id: 'switchToGroupsNo', height: 20, scope: this,
                handler: function(){ this.switchToGroupPanel.hide(); }
            }]
        });
        this.add(this.switchToGroupPanel);

        // add a hidden display field to show what is selected by default and if paging is enabled
        this.defaultDisplayField = Ext4.create('Ext.form.field.Display', {
            hideLabel: true,
            hidden: true,
            padding: 3,
            value: null
        });
        this.add(this.defaultDisplayField);

        this.fireChangeTask = new Ext4.util.DelayedTask(function(){
            this.fireEvent('chartDefinitionChanged', true);
        }, this);

        if (this.subject && this.subject.values)
        {
            this.selection = [];
            Ext4.each(this.subject.values, function(val){
                this.selection.push({type:'participant', id:val});
            }, this);
        }

        this.ptidFilterPanel = Ext4.create('LABKEY.study.ParticipantFilterPanel', {
            displayMode: 'PARTICIPANT',
            filterType: 'participant',
            subjectNoun: {
                singular : this.getStudyModuleSubjectContext().nounSingular,
                plural : this.getStudyModuleSubjectContext().nounPlural,
                columnName: this.getStudyModuleSubjectContext().columnName
            },
            maxInitSelection: this.maxInitSelection,
            selection: this.selection,
            listeners : {
                beforerender : function(){
                    this.fireEvent('measureMetadataRequestPending');
                },
                initSelectionComplete : function(numSelected, panel){
                    this.hideDefaultDisplayField = new Ext4.util.DelayedTask(function(){
                        this.defaultDisplayField.hide();
                    }, this);

                    // if this is a new time chart, show the text indicating that we are selecting the first 5 by default
                    var displayHtml = '';
                    if (!this.subject.values && numSelected == this.maxInitSelection) {
                        displayHtml += '<div style="font-size:75%;color:red;">Selecting 5 values by default.</div>';
                    }

                    // issue 22254: add message about paging toolbar if > 1000 ptids
                    if (!this.ptidFilterPanel.down('pagingtoolbar').hidden) {
                        displayHtml += '<div style="font-size:75%;color:red;">Paging enabled (see bottom of list).</div>';
                    }

                    // issue 22254: if this is a new chart and we have a lot of ptids, show switchToGroupPanel
                    var totalCount = panel.getFilterPanels()[0].getGrid().getStore().getTotalCount();
                    if (!this.subject.values && totalCount >= 1000) {
                        this.switchToGroupPanel.show();
                    }

                    // show the display for 5 seconds before hiding it again
                    if (displayHtml.length > 0)
                    {
                        this.defaultDisplayField.setValue(displayHtml);
                        this.defaultDisplayField.show();
                        this.hideDefaultDisplayField.delay(5000);
                    }

                    this.fireEvent('measureMetadataRequestComplete');
                },
                scope : this
            }
        });
        this.add(this.ptidFilterPanel);

        this.ptidFilterPanel.getFilterPanelGrid().getStore().on('load', function(store){
            if (store.getCount() == 0)
                this.fireEvent('measureMetadataRequestComplete');
        }, this);

        this.ptidFilterPanel.on('selectionchange', function(){
            this.fireChangeTask.delay(100);
        }, this, {buffer: 1000});
    },

    getSubject: function() {
        var participants = [];

        if (this.ptidFilterPanel) {
            var selected = this.ptidFilterPanel.getSelection(true, false);
            for (var i = 0; i < selected.length; i++)
            {
                if (selected[i].get('type') == 'participant')
                    participants.push(selected[i].get('id'));
            }
        }

        return {values: participants};
    }
});

Ext4.define('LABKEY.vis.ChartFilterGroupSelector', {
    extend : 'Ext.panel.Panel',

    title: 'Groups',
    border: false,
    cls: 'rpf',
    autoScroll: true,
    maxInitSelection: 5,

    constructor : function(config)
    {
        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged',
            'measureMetadataRequestPending',
            'measureMetadataRequestComplete'
        );

        // fix the issue with the group list hidden for saved chart with a measure dimension panel
        this.on('expand', function(){
            if (!this.hidden)
                this.show();
        });
    },

    getGroupValues : function(){
        // add a text link to the manage participant groups page
        this.manageGroupsLink = Ext4.create('Ext.form.field.Display', {
            hideLabel: true,
            width: 175,
            value: LABKEY.Utils.textLink({href: LABKEY.ActionURL.buildURL("study", "manageParticipantCategories"), text: 'Manage Groups'})
        });

        // add a hidden display field to show what is selected by default
        this.defaultDisplayField = Ext4.create('Ext.form.field.Display', {
            hideLabel: true,
            hidden: true,
            padding: 3,
            value: '<span style="font-size:75%;color:red;">Selecting 5 values by default.</span>'
        });

        // add a hidden display field for warning the user if a saved chart has a group that is no longer available
        this.groupsRemovedDisplayField = Ext4.create('Ext.form.field.Display', {
            hideLabel: true,
            hidden: true,
            padding: 3,
            width: 210,
            value: '<span style="font-size:90%;font-style:italic;">One or more of the participant groups originally saved with this chart are not currently visible. ' +
            'The group(s) may have been deleted or you may not have permission to view them.</span><br> <br>'
        });

        if (this.subject && this.subject.groups)
        {
            this.selection = [];
            Ext4.each(this.subject.groups, function(group){
                this.selection.push({type:group.type || 'participantGroup', label:group.label});
            }, this);
        }

        this.fireChangeTask = new Ext4.util.DelayedTask(function(){
            this.fireEvent('chartDefinitionChanged', true);
        }, this);

        this.groupFilterList = Ext4.create('LABKEY.study.ParticipantFilterPanel', {
            itemId   : 'filterPanel',
            flex     : 1,
            allowAll : true,
            normalWrap : true,
            includeParticipantIds : true,
            includeUnassigned : false,
            maxInitSelection: this.maxInitSelection,
            selection : this.selection,
            listeners : {
                selectionchange : function(){
                    this.fireChangeTask.delay(1000);
                },
                beforerender : function(){
                    this.fireEvent('measureMetadataRequestPending');
                },
                initSelectionComplete : function(numSelected){
                    // if there were saved groups that are no longer availabe, display a message
                    if (this.selection && this.selection.length > 0 && this.selection.length != numSelected)
                        this.groupsRemovedDisplayField.setVisible(true);

                    // if this is a new time chart, show the text indicating that we are selecting the first 5 by default
                    if (!this.subject.groups && numSelected == this.maxInitSelection)
                    {
                        this.hideDefaultDisplayField = new Ext4.util.DelayedTask(function(){
                            this.defaultDisplayField.hide();
                        }, this);

                        // show the display for 5 seconds before hiding it again
                        this.defaultDisplayField.show();
                        this.hideDefaultDisplayField.delay(5000);
                    }

                    this.fireEvent('measureMetadataRequestComplete');
                },
                scope : this
            }
        });

        this.add(this.groupsRemovedDisplayField);
        this.add(this.manageGroupsLink);
        this.add(this.defaultDisplayField);
        this.add(this.groupFilterList);
    },

    getUniqueGroupSubjectValues: function(groups){
        var map = {};
        var values = [];
        for (var i = 0; i < groups.length; i++)
        {
            // issue 22254: using map to check for uniqueness is faster than concat+unique
            for (var j = 0; j < groups[i].participantIds.length; j++)
            {
                var ptid = groups[i].participantIds[j];
                if (!map[ptid])
                {
                    map[ptid] = true;
                    values.push(ptid);
                }
            }
        }
        return values.sort();
    },

    getSubject: function(includeIndividual){
        var groups = [];
        var selected = this.groupFilterList.getSelection(true);
        for (var i = 0; i < selected.length; i++)
        {
            groups.push({
                id : selected[i].get("id"),
                categoryId : selected[i].get("categoryId"),
                label: selected[i].get("label"),
                type : selected[i].get("type"),
                participantIds: selected[i].get("participantIds")
            });
        }

        // sort the selected groups array to match the selection list order (cohort before ptid groups)
        function compareGroups(a, b) {
            if (a.type == 'cohort' && b.type == 'participantGroup') { return -1; }
            if (a.type == 'participantGroup' && b.type == 'cohort') { return 1; }
            if (a.type == 'cohort' && a.label < b.label) { return -1; }  // issue 20992
            if (a.type == 'cohort' && a.label > b.label) { return 1; }
            if (a.type == 'participantGroup' && a.id < b.id) { return -1; }
            if (a.type == 'participantGroup' && a.id > b.id) { return 1; }
            return 0;
        }
        groups.sort(compareGroups);

        var results = {groups: groups};
        // issue 22254: we only need to include the unique ptid list when displaying individual lines
        if (includeIndividual) {
            results.values = this.getUniqueGroupSubjectValues(groups);
        }

        return results;
    }
});

Ext4.define('GroupSelector.ParticipantCategory', {
    extend: 'Ext.data.Model',
    fields : [
        {name : 'id'},
        {name : 'categoryId'},
        {name : 'label'},
        {name : 'description'},
        {name : 'participantIds'},
        {name : 'type'}
    ]
});