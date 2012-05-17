/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.QuickTips.init();

Ext4.define('LABKEY.vis.GroupSelector', {

    extend : 'Ext.panel.Panel',

    constructor : function(config){
        Ext4.apply(config, {
            title: 'Groups',
            border: false,
            autoScroll: true
        });

        Ext4.define('ParticipantCategory', {
            extend: 'Ext.data.Model',
            fields: [
                {name: 'rowId', type: 'integer'},
                {name: 'label', type: 'string'},
                {name: 'created', type: 'date'},
                {name: 'participantIds'}
            ]
        });

        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged'
        );
    },

    initComponent : function(){
        // add a hiden display field to show what is selected by default
        this.defaultDisplayField = Ext4.create('Ext.form.field.Display', {
            hideLabel: true,
            hidden: true,
            width: 210,
            html: '<span style="font-size:75%;color:red;">Selecting 5 values by default</span>'
        });

        // selection model for group selector gridPanel
        var sm = Ext4.create('Ext.selection.CheckboxModel', {checkOnly: true});
        sm.on('selectionchange', function(selModel){
            // add the selected groups/subjects to the subject object
            this.subject.groups = [];
            var selectedRecords = selModel.getSelection();
            for (var i = 0; i < selectedRecords.length; i++)
            {
                this.subject.groups.push({
                    label: selectedRecords[i].get("label"),
                    id: selectedRecords[i].get('rowId'),
                    participantIds: selectedRecords[i].get("participantIds"),
                    created: selectedRecords[i].get("created")
                });
            }
            this.subject.values = this.getUniqueGroupSubjectValues(this.subject.groups);

            // sort the selected group array
            function compareCreated(a, b) {
                if (a.created < b.created) {return -1}
                if (a.created > b.created) {return 1}
                return 0;
            }
            this.subject.groups.sort(compareCreated);

            this.fireEvent('chartDefinitionChanged', true);
        }, this, {buffer: 1000}); // buffer allows single event to fire if bulk changes are made within the given time (in ms)

        var ttRenderer = function(value, p, record) {
            var msg = Ext4.util.Format.htmlEncode(value);
            p.attr = 'ext:qtip="' + msg + '"';
            return msg;
        };

        this.groupGridPanel = Ext4.create('Ext.grid.Panel', {
            autoHeight: true,
            viewConfig: {forceFit: true},
            border: false,
            frame: false,
            selModel: sm,
            header: false,
            enableHdMenu: false,
            columns: [{header: 'Groups', dataIndex:'label', renderer: ttRenderer, flex: 1}],
            store: Ext4.create('Ext.data.Store', {
                model: 'ParticipantCategory',
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL("participant-group", "getParticipantCategories"),
                    reader: {
                        type: 'json',
                        root:'categories',
                        idProperty:'rowId'
                    }
                },
                sorters: {property: 'created', direction: 'ASC'},
                autoLoad: true,
                listeners: {
                    scope: this,
                    'load': function(store, records, options){
                        // if this is not a saved chart with pre-selected groups, initially select to the first 5
                        this.selectDefault = false;
                        if (!this.subject.groups)
                        {
                            this.selectDefault = true;

                            // select the first 5 groups, or all if the length is less than 5
                            this.subject.groups = [];
                            for (var i = 0; i < (store.getCount() < 5 ? store.getCount() : 5); i++)
                            {
                                var record = store.getAt(i);
                                this.subject.groups.push({
                                    label: record.get("label"),
                                    id: record.get('rowId'),
                                    participantIds: record.get("participantIds"),
                                    created: record.get("created")
                                });

                            }
                            this.subject.values = this.getUniqueGroupSubjectValues(this.subject.groups);
                        }
                        // for saved charts w/ pre-selected groups, update the group memberships or remove the groups that don't exist
                        // (because they were deleted or because the given user does not have access to them)
                        else
                        {
                            for (var i = 0; i < this.subject.groups.length; i++)
                            {
                                var index = store.find('label', this.subject.groups[i].label);
                                if (index == -1)
                                {
                                    if(!this.groupsRemovedDisplayField.isVisible())
                                        this.groupsRemovedDisplayField.setVisible(true);
                                    this.subject.groups.splice(i, 1);
                                    i--;
                                }
                                else
                                {
                                    this.subject.groups[i].participantIds = store.getAt(index).get("participantIds").slice(0);
                                }
                            }
                            this.subject.values = this.getUniqueGroupSubjectValues(this.subject.groups);
                        }

                        // if there are no groups in the given study, hide the gridPanel and add some text
                        if (store.getCount() == 0)
                        {
                            this.groupGridPanel.hide();
                            this.noGroupsDisplayField.show();
                        }
                        // else if the grid is already rendered, call the 'viewready' event for it
                        else if (this.isVisible())
                        {
                            this.groupGridPanel.fireEvent('viewready', this.groupGridPanel);
                        }
                    }
                }
            }),
            listeners: {
                scope: this,
                'viewready': function(grid){
                    // show the selecting default text if necessary
                    if (grid.getStore().getCount() > 5 && this.selectDefault)
                    {
                        // show the display for 5 seconds before hiding it again
                        var refThis = this;
                        refThis.defaultDisplayField.show();
                        setTimeout(function(){
                            refThis.defaultDisplayField.hide();
                        },5000);
                        this.selectDefault = false;
                    }

                    // check selected groups in grid panel (but suspend events during selection)
                    sm.suspendEvents(false);
                    if (this.subject.groups)
                    {
                        for (var i = 0; i < this.subject.groups.length; i++)
                        {
                            var index = grid.getStore().find('label', this.subject.groups[i].label);
                            if (index > -1)
                                sm.select(index, true);
                        }
                    }
                    sm.resumeEvents();
                }
            }
        });

        // add a text link to the manage participant groups page
        this.manageGroupsLink = Ext4.create('Ext.form.field.Display', {
            hideLabel: true,
            width: 175,
            html: LABKEY.Utils.textLink({href: LABKEY.ActionURL.buildURL("study", "manageParticipantCategories"), text: 'Manage Groups'})
        });

        // add a hidden display field that will be shown if there are no groups in the gridPanel
        this.noGroupsDisplayField = Ext4.create('Ext.form.field.Display', {
            hideLabel: true,
            hidden: true,
            width: 220,
            html: '<span style="font-size:90%;font-style:italic;">No participant groups have been configured or shared in this study.<BR/><BR/>Click the \'Manage Groups\' link below to begin configuring groups.</span>'
        });

        this.groupsRemovedDisplayField = Ext4.create('Ext.form.field.Display', {
            hideLabel: true,
            hidden: true,
            width: 220,
            html: '<span style="font-size:90%;font-style:italic;">One or more of the participant groups originally saved with this chart are not currently visible. ' +
                    'The group(s) may have been deleted or you may not have permission to view them.</span><br> <br>'
        });

        this.items = [
            this.groupsRemovedDisplayField,
            this.noGroupsDisplayField,
            this.manageGroupsLink,
            this.defaultDisplayField,
            this.groupGridPanel
        ];

        this.callParent();
    },

    getUniqueGroupSubjectValues: function(groups){
        var values = [];
        for (var i = 0; i < groups.length; i++)
        {
            values = Ext4.Array.unique(values.concat(groups[i].participantIds));
        }
        return values.sort();
    },

    getSubject: function(){
        return this.subject;
    }
});
