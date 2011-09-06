/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext.namespace("LABKEY.study");

LABKEY.requiresScript("study/ParticipantGroup.js");

Ext.QuickTips.init();

LABKEY.study.CreateStudyWizard = Ext.extend(Ext.util.Observable, {

    constructor : function(config)
    {
        Ext.apply(this, config);
        Ext.util.Observable.prototype.constructor.call(this, config);
    },

    show : function() {
        this.steps = [];
        this.currentStep = 0;
        this.info = {};

        this.steps.push(this.getNamePanel());
        this.steps.push(this.getParticipantsPanel());
        this.steps.push(this.getDatasetsPanel());

        this.prevBtn = new Ext.Button({text: 'Previous', disabled: true, scope: this, handler: function(){
            this.currentStep--;
            this.updateStep();
        }});

        if (this.steps.length == 1)
        {
            this.nextBtn = new Ext.Button({text: 'Finish', scope: this, handler: this.onFinish});
            this.prevBtn.hide();
        }
        else
        {
            this.nextBtn = new Ext.Button({text: 'Next', scope: this, handler: function(){
                this.currentStep++;
                this.updateStep();
            }});
        }

        this.wizard = new Ext.Panel({
            border: false,
            layout: 'card',
            layoutConfig: {deferredRender:true},
            activeItem: 0,
            bodyStyle : 'padding: 25px;',
            items: this.steps,
            bbar: ['->', this.prevBtn, this.nextBtn]
        });

        this.win = new Ext.Window({
            title: 'Create New Study',
            width: 875,
            height: 600,
            cls: 'extContainer',
            autoScroll: true,
            closeAction:'close',
            border: false,
            modal: true,
            layout: 'fit',
            items: [this.wizard]
        });
        this.win.show();
    },

    updateStep : function() {

        this.prevBtn.setDisabled(this.currentStep == 0);
        if (this.currentStep == this.steps.length-1)
        {
            this.nextBtn.setText('Finish');
            this.nextBtn.setHandler(function(){this.onFinish();}, this);
        }
        else
        {
            this.nextBtn.setText('Next');
            this.nextBtn.setHandler(function(){
                this.currentStep++;
                this.updateStep();
            }, this);
        }
        this.wizard.getLayout().setActiveItem(this.currentStep);
    },

    getNamePanel : function() {

        var items = [];

        this.info.name = 'New Study';
        this.info.dstPath = LABKEY.ActionURL.getContainer();

        var studyLocation = new Ext.form.TextField({
            fieldLabel: 'New Study Location',
            allowBlank: true,
            name: 'studyFolder',
            value: this.info.dstPath,
            validator: addSlash,
            listeners: {change:function(cmp, newValue, oldValue) {this.info.dstPath = newValue;}, scope:this},
            scope: this
        });

        function addSlash(input){
            if(input == ""){
                studyLocation.setValue('/');
            }
            return true;
        }

        var formItems = [
            {
                xtype: 'textfield',
                fieldLabel: 'Study Name',
                allowBlank: false,
                name: 'studyName',
                value: this.info.name,
                listeners: {change:function(cmp, newValue, oldValue) {this.info.name = newValue;}, scope:this}
            },{
                xtype: 'textarea',
                name: 'studyDescription',
                height: '200',
                emptyText: 'Type Description here',
                listeners: {change:function(cmp, newValue, oldValue) {this.info.description = newValue;}, scope:this}
            },
            studyLocation
        ];

        var folderTree = new Ext.tree.TreePanel({
            loader : new Ext.tree.TreeLoader({
                dataUrl : LABKEY.ActionURL.buildURL('core', 'getExtContainerAdminTree.api'),
                baseParams : {requiredPermission : 'org.labkey.api.security.permissions.AdminPermission'}
            }),
            root : {
                id : '1',
                nodeType : 'async',
                expanded : true,
                editable : true,
                expandable : true,
                draggable : false,
                text : 'LabKey Server Projects',
                cls : 'x-tree-node-current'
            },
            listeners: {
                dblclick: onDblClick,
                scope:this
            },
            fieldLabel: 'Choose A Folder',
            cls : 'folder-management-tree', // used by selenium helper
            rootVisible: true,
            enableDD: false,
            animate : true,
            useArrows : true,
            autoScroll: true,
            height: 200,
            border: true
        });

        function onDblClick(e){
            studyLocation.setValue(e.attributes.containerPath);
            this.info.dstPath = e.attributes.containerPath;
        }

        formItems.push(folderTree);

        var formPanel = new Ext.form.FormPanel({
            border: false,
            defaults: {
                width: 710
            },
            flex: 1,
            layout: 'form',
            items: formItems
        });
        items.push(formPanel);

        var txt = Ext.DomHelper.markup({tag:'div', html:'This Study will be created as a subfolder from the current folder. Click on the Change Folder button to select a different location.<br>'});
//        items.push({xtype:'displayfield', html: txt});

        var panel = new Ext.Panel({
            border: false,
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
//            buttons: [{text: 'Change Folder', scope: this, handler: function(){Ext.MessageBox.alert('Coming Soon', 'This feature has not been implemented yet.');}}]
        });

        panel.on('beforehide', function(cmp){
            if (formPanel && !formPanel.getForm().isValid())
            {
                Ext.MessageBox.alert('Error', 'Please enter the required information on the page.');
                this.currentStep--;
                this.updateStep();
                return false;
            }
            return true;
        }, this);

        return panel;
    },

    getParticipantsPanel : function() {

        var items = [];

        var txt = Ext.DomHelper.markup({tag:'div', html:'How will participants be added to this study?<br>'});

        this.newGroupRadio = new Ext.form.Radio({boxLabel:'Create a new participant group', name: 'renderType', scope: this,
            handler: this.showNewParticipantGroupPanel});
        this.existingGroupRadio = new Ext.form.Radio({boxLabel:'Select from existing participant groups', name: 'renderType',
            checked: true, scope:this, handler: this.showExistingParticipantGroupPanel});

        var formItems = [
            {xtype: 'displayfield', html: txt},
            {
                xtype: 'radiogroup',
                columns: 2,
                fieldLabel: '',
                labelWidth: 5,
                items: [
                    this.newGroupRadio,
                    this.existingGroupRadio
                ]
            }
        ];

        var formPanel = new Ext.Panel({
            padding: '5px',
            border: false,
            layout: 'form',
            //flex: 2,
            height: 75,
            labelWidth: 5,
            items: formItems
        });
        items.push(formPanel);

        this.participantPanel = new Ext.Panel({
            border: false,
            layout: 'vbox',
            items: items,
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            }
        });

        this.participantPanel.on('beforehide', function(cmp){
            if (this.newGroupPanel && this.newGroupPanel.isVisible())
            {
                if (!this.newGroupPanel.validate())
                {
                    this.currentStep--;
                    this.updateStep();
                    return false;
                }
            } else {
                if(!this.info.existingRowId){
                    Ext.Msg.alert("Error", "You must select an existing group or create a new one.");
                    this.currentStep--;
                    this.updateStep();
                    return false;
                }
            }
            return true;
        }, this);

        this.showExistingParticipantGroupPanel(null, true);

        return this.participantPanel;
    },

    /**
     * Show or hide the new participant group panel
     */
    showNewParticipantGroupPanel : function(cmp, show)
    {
        if (show && this.participantPanel)
        {
            if (!this.newGroupPanel)
            {
                this.newGroupPanel = new LABKEY.study.ParticipantGroupPanel({
                    flex: 2,
                    canEdit: true,
                    canShare: false,
                    hasButtons: false,
                    subject: this.subject
                });

                this.participantPanel.add(this.newGroupPanel);
                this.participantPanel.doLayout();
            } else {
                this.newGroupPanel.setVisible(true);
            }
        }
        else if (this.participantPanel && this.newGroupPanel)
        {
            this.newGroupPanel.setVisible(false);
            this.participantPanel.doLayout();
        }
    },

    showExistingParticipantGroupPanel : function(cmp, show)
    {
        if (show && this.participantPanel)
        {
            if(!this.existingGroupPanel){
                Ext.Ajax.request(
                        {
                            url : LABKEY.ActionURL.buildURL("participant-group", "getParticipantCategories"),
                            method : 'get',
                            success: this.createExistingParticipantGroupPanel,
                            failure: function(response, options){LABKEY.Utils.displayAjaxErrorResponse(response, options);},
                            scope: this
                        });

                this.participantPanel.doLayout();
            } else {
                this.existingGroupPanel.setVisible(true);
            }
        }
        else if (this.participantPanel && this.existingGroupPanel)
        {
            this.existingGroupPanel.setVisible(false);
            this.participantPanel.doLayout();
        }
    },

    createExistingParticipantGroupPanel : function(resp, options)
    {
        var o = eval('var $=' + resp.responseText + ';$;');

        if (o.success && o.categories)
        {
            if (o.categories.length > 0)
            {
                var items = [];
                var participantStores = [];
                var participantGroupsStore =new Ext.data.ArrayStore({
                    fields: ['participantGroup', 'storeIndex']
                });
                var participantGroupsStoreRecord = Ext.data.Record.create([
                    {name:'participantGroup'},
                    {name: 'rowId'},
                    {name: 'storeIndex'}
                ]);
                var participantsStoreRecord = Ext.data.Record.create([
                    {name:'participantId'}
                ]);

                for (var i=0; i < o.categories.length; i++)
                {
                    var group = o.categories[i];
                    var tempStore = new Ext.data.ArrayStore({
                        fields: ['participantId']
                    });
                    for(var j = 0; j < group.participantIds.length; j++){
                        tempStore.add(new participantsStoreRecord({participantId: group.participantIds[j]}));
                    }
                    participantGroupsStore.add(new participantGroupsStoreRecord({participantGroup: group.label, rowId: group.rowId, storeIndex: i}));
                    participantStores.push(tempStore);
                }

                this.participantListView = new Ext.list.ListView({
                    store: new Ext.data.ArrayStore({
                        fields: ['participantId']
                    }),
                    emptyText: "No participants to display.",
                    width: 250,
                    columns:[
                        {
                            header:'Participants',
                            dataIndex: 'participantId'
                        }
                    ]
                });

                function groupSelected(cmp, selection){
                    var selectedStoreIndex = cmp.getSelectedRecords()[0].get('storeIndex');
                    this.info.existingRowId = cmp.getSelectedRecords()[0].get('rowId');
                    this.participantListView.bindStore(participantStores[selectedStoreIndex]);
                }

                this.participantGroupListView = new Ext.list.ListView({
                    store: participantGroupsStore,
                    emptyText: "No groups to display.",
                    width: 250,
                    singleSelect: true,
                    columnSort: false,
                    columns:[
                        {
                            header:'Participant Groups',
                            dataIndex: 'participantGroup'
                        }
                    ],
                    listeners:{
                        'selectionchange': groupSelected,
                        scope: this
                    }
                });

                var formItems = [
                    this.participantGroupListView,
                    this.participantListView
                ];

                this.existingGroupPanel = new Ext.Panel({
                    padding: '20px',
                    border: false,
                    labelWidth: 250,
                    flex: 1,
                    layout: 'hbox',
                    items: [formItems]
                });
            }
            else
            {
                this.existingGroupPanel = new Ext.Panel({
                    padding: '20px',
                    border: false,
                    flex: 1,
                    layout: 'fit',
                    html: 'There are no ' + this.subject.nounSingular + ' groups defined for this Study'
                });
            }

            this.participantPanel.add(this.existingGroupPanel);
            this.participantPanel.doLayout();
        }
    },

    getDatasetsPanel : function() {

        var items = [];

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-wp-header', html:'Dataset Selection'}) +
                Ext.DomHelper.markup({tag:'div', html:'Select existing datasets to include in this Study.<br>'});

        items.push({xtype:'displayfield', html: txt});

        var grid = new LABKEY.ext.EditorGridPanel({
            store: new LABKEY.ext.Store({
                schemaName: 'study',
                queryName: 'Datasets',
                columns: 'dataSetId, label, category, description',
                sort: 'label'
            }),
            viewConfig: {forceFit: true, scrollOffset: 0},
            loadMask:{msg:"Loading, please wait..."},
            enableFilters: true,
            editable: false,
            stripeRows: true,
            //border: false,
            cls: 'extContainer',
            flex: 1,
            tbar: []
        });
        items.push(grid);
        grid.on('columnmodelcustomize', this.customizeColumnModel, this);
        grid.selModel.on('selectionchange', function(cmp){this.info.datasets = cmp.getSelections();}, this);

        var panel = new Ext.Panel({
            border: false,
            layout: 'vbox',
            items: items,
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            }
        });

        return panel;
    },

    customizeColumnModel : function(colModel, index, c)
    {
        index.Label.header = 'Dataset';
        index.Label.width = 250;
        index.DataSetId.hidden = true;
    },

    onFinish : function() {

        if (!this.info.datasets)
        {
            Ext.MessageBox.alert('Error', 'You must select at least one dataset to create the new study from.');
            return;
        }

        // prepare the params to post to the api action
        var params = {};

        params.name = this.info.name;
        params.description = this.info.description;
        params.srcPath = LABKEY.ActionURL.getContainer();
        params.dstPath = this.info.dstPath;
        params.datasets = [];

        if(this.existingGroupRadio.checked){
            //If we chose an existing group then we just pass the rowid of the group.
            params.categories = [{rowId: this.info.existingRowId}];
        } else {
            //If it's a new group we pass the categoryData from the newGroupPanel.
            params.categories = [this.newGroupPanel.getCategoryData()];
        }

        for (var i=0; i < this.info.datasets.length; i++)
        {
            var ds = this.info.datasets[i];

            params.datasets.push(ds.data.DataSetId);
        }

        this.win.getEl().mask("creating study...", "x-mask-loading");

        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL('study', 'createAncillaryStudy'),
            method : 'POST',
            scope: this,
            success: function(response, opts){
                var o = eval('var $=' + response.responseText + ';$;');

                this.fireEvent('success');
                this.win.close();

                if (o.redirect)
                    window.location = o.redirect;
            },
            failure: function(response, opts){
                this.win.getEl().unmask();
                LABKEY.Utils.displayAjaxErrorResponse(response, opts);
                this.fireEvent('failure');
            },
            jsonData : params,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }
});
