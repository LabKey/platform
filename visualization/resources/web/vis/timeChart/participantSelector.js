/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.ParticipantSelector', {

    extend : 'Ext.panel.Panel',

    constructor : function(config){
        Ext4.applyIf(config, {
            title: config.subjectNounPlural,
            border: false,
            autoScroll: true,
            maxInitSelection: 5
        });

        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged',
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

        // add a hiden display field to show what is selected by default
        this.defaultDisplayField = Ext4.create('Ext.form.field.Display', {
            hideLabel: true,
            hidden: true,
            width: 210,
            value: '<span style="font-size:75%;color:red;">Selecting 5 values by default</span>'
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
                singular : this.subjectNounSingular,
                plural : this.subjectNounPlural,
                columnName: this.subjectColumn
            },
            maxInitSelection: this.maxInitSelection,
            selection: this.selection,
            listeners : {
                selectionchange : function(){
                    this.fireChangeTask.delay(1000); 
                },
                beforerender : function(){
                    this.fireEvent('measureMetadataRequestPending');
                },
                initSelectionComplete : function(numSelected){
                    // if this is a new time chart, show the text indicating that we are selecting the first 5 by default
                    if (!this.subject.values && numSelected == this.maxInitSelection)
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
        this.add(this.ptidFilterPanel);
    },

    getSubject: function(){
        var participants = [];
        var selected = this.ptidFilterPanel.getSelection(true, false);
        for (var i = 0; i < selected.length; i++)
        {
            if (selected[i].get('type') == 'participant')
                participants.push(selected[i].get('id'));
        }

        return {values: participants};
    }
});
