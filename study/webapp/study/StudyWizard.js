/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext.namespace("LABKEY.study");

LABKEY.requiresScript("study/ParticipantGroup.js");
LABKEY.requiresScript("FileUploadField.js");

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
        this.info.dstPath = LABKEY.ActionURL.getContainer() + '/' + this.info.name;

        var studyLocation = new Ext.form.DisplayField({
            fieldLabel: 'New Study Location',
            name: 'studyFolder',
            width: 650,
            readOnly: true,
            value: this.info.dstPath,
            scope: this
        });

        var browseBtn = new Ext.Button({
            name:"browseBtn",
            text: "Change",
            cls: "labkey-button",
            handler: browseFolders
        });

        function browseFolders(){
            treeWin.show();
        }

        var protocolDocField = new Ext.form.FileUploadField({
            emptyText: 'Select a protocol document',
            fieldLabel: 'Protocol Document',
            name: 'protocolDoc',
            buttonText: 'Browse',
            buttonCfg: { cls: "labkey-button" },
            listeners: {
                "fileselected": function (fb, v) {
                    var i = Math.max(v.lastIndexOf('/'), v.lastIndexOf('\\'));
                    var name = v.substring(i+1); //this code was taken from fileBrowser.js line 3570.
                    this.info.protocolDoc = v;
                },
                scope: this
            },
            scope: this
        });

        var formItems = [
            {
                xtype: 'textfield',
                fieldLabel: 'Study Name',
                allowBlank: false,
                name: 'studyName',
                value: this.info.name,
                enableKeyEvents: true,
                listeners: {change: blurChange, keyup:nameChange, scope:this}
            },{
                xtype: 'textarea',
                name: 'studyDescription',
                height: '200',
                emptyText: 'Type Description here',
                listeners: {change:function(cmp, newValue, oldValue) {this.info.description = newValue;}, scope:this}
            },
            protocolDocField,
            {
                xtype:'compositefield',
                items:[
                    studyLocation,
                    browseBtn
                ]
            }
        ];

        function blurChange(txtField){
            //Changes the study location when you click away from the field. This is needed if you are typing and click
            // away from the textfield very fast.
            var newValue = txtField.getValue();
            var path;
            if(folderTree.getSelectionModel().getSelectedNode()){
                path = folderTree.getSelectionModel().getSelectedNode().attributes.containerPath;
            } else {
                path = LABKEY.ActionURL.getContainer();
            }
            this.info.name = newValue;
            this.info.dstPath = path + '/' + this.info.name;
            studyLocation.setValue(this.info.dstPath);
        }

        function nameChange(txtField){
            var newValue = txtField.getValue();
            var path;
            if(folderTree.getSelectionModel().getSelectedNode()){
                path = folderTree.getSelectionModel().getSelectedNode().attributes.containerPath;
            } else {
                path = LABKEY.ActionURL.getContainer();
            }
            this.info.name = newValue;
            this.info.dstPath = path + '/' + this.info.name;
            studyLocation.setValue(path + '/' + this.info.name);
        }

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
            cls : 'folder-management-tree', // used by selenium helper
            rootVisible: true,
            enableDD: false,
            animate : true,
            useArrows : true,
            autoScroll: true,
            height: 325,
            border: true
        });

        function onDblClick(e){
            if(e.attributes.containerPath){
                studyLocation.setValue(e.attributes.containerPath + '/' + this.info.name);
                this.info.dstPath = e.attributes.containerPath + '/' + this.info.name
            } else {
                studyLocation.setValue("/" + this.info.name);
                this.info.dstPath = "/" + this.info.name;
            }
            treeWin.hide();
        }

        var selectFolderBtn = new Ext.Button({
            name:"selectFolder",
            text: "Select",
            cls: "labkey-button",
            handler: selectFolder,
            scope: this
        });

        var cancelBtn = new Ext.Button({
            name: "cancelBtn",
            text: "cancel",
            cls: "labkey-button",
            handler: function(){
                treeWin.hide();
            },
            scope: this
        });

        function selectFolder(){
            var path = folderTree.getSelectionModel().getSelectedNode().attributes.containerPath;
            if(path){
                studyLocation.setValue(path + '/' + this.info.name);
                this.info.dstPath = path + '/' + this.info.name;
            } else {
                studyLocation.setValue("/" + this.info.name);
                this.info.dstPath = "/" + this.info.name;
            }
            treeWin.hide();
        }

        var treeWin = new Ext.Window ({
            title: 'Choose Study Location',
            width: 600,
            height: 400,
            cls: 'extContainer',
            autoScroll: true,
            closeAction:'hide',
            border: false,
            modal: true,
            layout: 'fit',
            items: [folderTree],
            bbar: ['->',cancelBtn, selectFolderBtn]
        });

        this.nameFormPanel = new Ext.form.FormPanel({
            border: false,
            defaults: {
                labelSeparator: '',
                width: 710
            },
            flex: 1,
            layout: 'form',
            items: formItems
        });
        items.push(this.nameFormPanel);

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
            if (this.nameFormPanel && !this.nameFormPanel.getForm().isValid())
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

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-strong', html: this.subject.nounSingular + ' Selection'}) +
                Ext.DomHelper.markup({tag:'div', cls: 'labkey-emphasis', html:'How will ' + this.subject.nounPlural + ' be added to this study?<br>'});

        items.push({xtype:'displayfield', html: txt});

        var noGroupRadio = new Ext.form.Radio({boxLabel:'Use all ' + this.subject.nounPlural + ' from the source Study', name: 'renderType', inputValue: 'all'});
        this.newGroupRadio = new Ext.form.Radio({
            boxLabel:'Create a new ' + this.subject.nounSingular + ' group',
            name: 'renderType',
            inputValue: 'new',
            scope: this,
            handler: this.showNewParticipantGroupPanel
        });

        this.existingGroupRadio = new Ext.form.Radio({
            boxLabel:'Select from existing ' + this.subject.nounPlural + ' groups',
            name: 'renderType',
            inputValue: 'existing',
            checked: true,
            scope:this,
            handler: this.showExistingParticipantGroupPanel
        });

        this.copyParticipantGroups = new Ext.form.Checkbox({
            boxLabel: 'Copy selected ' + this.subject.nounSingular + ' groups to the new study',
            checked:true
        });

        var formItems = [
            {
                xtype: 'radiogroup',
                columns: 2,
                fieldLabel: '',
                labelWidth: 5,
                items: [
                    this.existingGroupRadio,
                    noGroupRadio
                ]
            }
            //this.copyParticipantGroups
        ];

        var formPanel = new Ext.Panel({
            padding: '5px',
            border: false,
            layout: 'form',
            flex: 1,
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
            }
            else if (this.existingGroupRadio.checked)
            {
                if(!this.info.existingCategories){
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
        this.copyParticipantGroups.setVisible(show);
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
        var o = Ext.util.JSON.decode(resp.responseText);

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
                    flex: 1,
                    autoScroll: true,
                    columns:[
                        {
                            header: this.subject.nounPlural,
                            dataIndex: 'participantId'
                        }
                    ]
                });

                function groupSelected(cmp, selection){
                    this.info.existingCategories = cmp.getSelectedRecords();

                    var selectedStoreIndex = cmp.getSelectedRecords()[0].get('storeIndex');
                    this.participantListView.bindStore(participantStores[selectedStoreIndex]);
                }

                this.participantGroupListView = new Ext.list.ListView({
                    store: participantGroupsStore,
                    emptyText: "No groups to display.",
                    width: 250,
                    autoScroll: true,
                    multiSelect: true,
                    simpleSelect: true,
                    columnSort: false,
                    columns:[
                        {
                            header: this.subject.nounSingular + ' Groups',
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
                    labelWidth: 250,
                    flex: 2,
                    layout: 'hbox',
                    layoutConfig: {
                        align: 'stretch',
                        pack: 'start'
                    },
                    items: [formItems],
                    cls: 'testParticipantGroups'
                });
            }
            else
            {
                this.existingGroupPanel = new Ext.Panel({
                    padding: '20px',
                    border: false,
                    flex: 2,
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

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-strong', html:'Dataset Selection'}) +
                Ext.DomHelper.markup({tag:'div', cls: 'labkey-emphasis', html:'Select existing datasets to include in this Study<br>'});

        items.push({xtype:'displayfield', html: txt});

        var grid = new LABKEY.ext.EditorGridPanel({
            store: new LABKEY.ext.Store({
                schemaName: 'study',
                queryName: 'Datasets',
                filterArray: [ LABKEY.Filter.create('ShowByDefault', true) ],
                columns: 'dataSetId, label, category, description',
                sort: 'label'
            }),
            viewConfig: {forceFit: true, scrollOffset: 0},
            loadMask:{msg:"Loading, please wait..."},
            enableFilters: true,
            editable: false,
            stripeRows: true,
            pageSize: 300000,
            cls: 'extContainer studyWizardDatasetList',
            flex: 1,
            tbar: []
        });
        items.push(grid);
        grid.on('columnmodelcustomize', this.customizeColumnModel, this);
        grid.selModel.on('selectionchange', function(cmp){this.info.datasets = cmp.getSelections();}, this);

        var syncTxt = Ext.DomHelper.markup({tag:'div', cls:'labkey-strong', html:'<br><br>Data Linking Options'}) +
                Ext.DomHelper.markup({tag:'div', cls: 'labkey-emphasis', html:'Linked datasets can be configured to be manually updated or to automatically ' +
                    'update within a fixed amount of time after the data from the original study has changed<br>'});

        items.push({xtype:'displayfield', html: syncTxt});

        this.snapshotOptions = new Ext.form.FormPanel({
            defaults: {labelSeparator: ''},
            items: [
                {xtype: 'hidden', name: 'updateDelay', value: 30},
                {xtype: 'radiogroup', fieldLabel: '', columns: 2, items: [
                    {name: 'autoRefresh', boxLabel: 'Automatic Refresh', inputValue: true, checked: true},
                    {name: 'autoRefresh', boxLabel: 'Manual Refresh', inputValue: false}]
                }
            ],
            padding: '10px 0px',
            border: false,
            height: 100,
            labelWidth: 100
        });
        items.push(this.snapshotOptions);

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

        var hiddenFields = [];
        if(this.existingGroupRadio.checked)
        {
            // If we chose an existing group then we just pass the rowid of the group, because of a bug in ie
            // we add categories and datasets as hidden form fields to the form so the arrays get
            // serialized correctly
            for (var i=0; i < this.info.existingCategories.length; i++)
            {
                var category = this.info.existingCategories[i];
                var id = Ext.id();
                hiddenFields.push(id);
                this.nameFormPanel.add({xtype:'hidden', id: id, name: 'categories', value: category.get('rowId')});
            }
        }
        params.copyParticipantGroups = true;//this.copyParticipantGroups.checked;

        for (var i=0; i < this.info.datasets.length; i++)
        {
            var ds = this.info.datasets[i];
            var id = Ext.id();
            hiddenFields.push(id);
            this.nameFormPanel.add({xtype:'hidden', id: id, name: 'datasets', value: ds.data.DataSetId});
        }
        this.nameFormPanel.doLayout();

        var form = this.snapshotOptions.getForm();
        var refreshOptions = form.getValues();
        if (refreshOptions.autoRefresh === 'true')
            params.updateDelay = refreshOptions.updateDelay;

        this.win.getEl().mask("creating study...", "x-mask-loading");

        var createForm = new Ext.form.BasicForm(this.nameFormPanel.getForm().getEl(), {
            method : 'POST',
            url    : LABKEY.ActionURL.buildURL('study', 'createAncillaryStudy'),
            fileUpload : true
        });

        createForm.submit({
            scope: this,
            success: function(response, opts){
                var o = Ext.util.JSON.decode(opts.response.responseText);

                this.fireEvent('success');
                this.win.close();

                if (o.redirect)
                    window.location = o.redirect;
            },
            failure: function(response, opts){
                this.win.getEl().unmask();

                // need to clean up the added hidden form fields
                for (var i=0; i < hiddenFields.length; i++)
                    this.nameFormPanel.remove(hiddenFields[i]);
                this.nameFormPanel.doLayout();

                var jsonResponse = Ext.util.JSON.decode(opts.response.responseText);
                if (jsonResponse && jsonResponse.exception)
                {
                    var error = "An error occurred trying to create the study:\n" + jsonResponse.exception;
                    Ext.Msg.alert("Create Study Error", error);
                }
                this.fireEvent('failure');
            },
            params: params
        });
    }
});
