/*
 * Copyright (c) 2012-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.ParticipantSelector', {

    extend : 'Ext.panel.Panel',

    constructor : function(config){
        Ext4.applyIf(config, {
            title: LABKEY.moduleContext.study.subject.nounPlural,
            border: false,
            autoScroll: true,
            maxInitSelection: 5
        });

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

    getSubjectValues: function() {
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
                text: 'There is a large number of ' + LABKEY.moduleContext.study.subject.nounPlural.toLowerCase()
                    + ' in this study. Would you like to switch to plot by '
                    + LABKEY.moduleContext.study.subject.nounSingular.toLowerCase() + ' groups?'
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
                singular : LABKEY.moduleContext.study.subject.nounSingular,
                plural : LABKEY.moduleContext.study.subject.nounPlural,
                columnName: LABKEY.moduleContext.study.subject.columnName
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
