/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext.namespace("LABKEY.study");

LABKEY.requiresScript("study/ParticipantGroup.js");
LABKEY.requiresScript("FileUploadField.js");

Ext.QuickTips.init();
Ext.GuidedTips.init();

LABKEY.study.CreateStudyWizard = Ext.extend(Ext.util.Observable, {

    constructor : function(config)
    {
        Ext.apply(this, config);
        Ext.util.Observable.prototype.constructor.call(this, config);
        this.sideBarTemplate = new Ext.XTemplate(
                '<div class="labkey-ancillary-wizard-background">',
                '<ol class="labkey-ancillary-wizard-steps">',
                '<tpl for="steps">',

                    '<tpl if="values.currentStep == true">',
                    '<li class="labkey-ancillary-wizard-active-step">{value}</li>',
                    '</tpl>',

                    '<tpl if="values.currentStep == false">',
                    '<li>{value}</li>',
                    '</tpl>',

                '</tpl>',
                '</ol>',
                '</div>'

        );
        this.sideBarTemplate.compile();
    },

    show : function() {
        this.steps = [];
        this.currentStep = 0;
        this.lastStep = 0;
        this.info = {};

        this.steps.push(this.getNamePanel());
        this.steps.push(this.getParticipantsPanel());
        this.steps.push(this.getDatasetsPanel());

        this.prevBtn = new Ext.Button({text: 'Previous', disabled: true, scope: this, handler: function(){
            this.lastStep = this.currentStep;
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
                this.lastStep = this.currentStep;
                this.currentStep++;
                this.updateStep();
            }});
        }

        this.pages = new Ext.Panel({
            border: false,
            layout: 'card',
            layoutConfig: {deferredRender:true},
            activeItem: 0,
            bodyStyle : 'padding: 25px;',
            flex: 1,
            items: this.steps,
            bbar: ['->', this.prevBtn, this.nextBtn]
        });

        this.sideBar = new Ext.Panel({
            //This is going to be where the sidebar content goes.
            name: 'sidebar',
            width: 175,
            border: false,
            cls: 'extContainer',
            tpl: this.sideBarTemplate,
            data: {
                steps: [{value: 'General Setup', currentStep: true}, {value: this.subject.nounPlural, currentStep: false}, {value: 'Datasets', currentStep: false}]
            }
        });

        this.wizard = new Ext.Panel({
            layout: 'hbox',
            layoutConfig: {
                pack: 'start',
                align: 'stretch'
            },
            defaults: {
                border: false
            },
            items: [this.sideBar, this.pages]
        });

        //To update the sideBar call this.sideBar.update({steps: [], currentStepIndex: int});

        this.win = new Ext.Window({
            title: 'Create Ancillary Study',
            width: 875,
            height: 600,
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

        var sideBarData = [];
        for(var i = 0; i < this.steps.length; i++){
            var value = this.steps[i].name;
            var isCurrentStep = i == this.currentStep;
            sideBarData.push({
                value: value,
                currentStep: isCurrentStep
            });
        }
        this.sideBar.update({steps: sideBarData});

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
        this.pages.getLayout().setActiveItem(this.currentStep);
    },

    getNamePanel : function() {

        var items = [];

        this.info.name = this.studyName;
        this.info.dstPath = LABKEY.ActionURL.getContainer() + '/' + this.info.name;

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'General Setup'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});

        items.push({xtype:'displayfield', html: txt});

        var studyLocation = new Ext.form.TextField({
            fieldLabel: 'Location',
            name: 'studyFolder',
            width: 446,
            readOnly: true,
            fieldClass: 'x-form-empty-field',
            value: this.info.dstPath,
            scope: this
        });

        var changeFolderBtn = new Ext.Button({
            name:"changeFolderBtn",
            text: "Change",
            width: 57,
            cls: "labkey-button",
            handler: browseFolders
        });

        var locationField = new Ext.form.CompositeField({
            width: 510,
            items:[
                studyLocation,
                changeFolderBtn
            ]
        });

        function browseFolders(){
            folderTree.toggleCollapse();
        }

        var protocolTip = '' +
            '<div>' +
                '<div class=\'g-tip-header\'>' +
                    '<span>Protocol Document</span>' +
                '</div>' +
                '<div class=\'g-tip-subheader\'>' +
                    'A document that describes the objective, design, and orgainzation of your study.' +
                    ' Often, this document contains a study plan, the types of participants, as well as scheduling.' +
                '</div>' +
            '</div>';

        var protocolDocField = new Ext.form.FileUploadField({
            emptyText: 'Select a protocol document',
            fieldLabel: 'Protocol',
            name: 'protocolDoc',
            gtip : protocolTip,
            height: 24,
            buttonText: 'Browse',
            buttonCfg: { cls: "labkey-button"},
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
            header:false,
            style: "margin-left: 103px;", //Line up the folder tree with the rest of the form elements.
            rootVisible: true,
            enableDD: false,
            animate : true,
            useArrows : true,
            height: 240,
            collapsible : true,
            collapsed: true,
            autoScroll: true,
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
            folderTree.collapse();
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

        var formItems = [
            {
                xtype: 'textfield',
                fieldLabel: 'Name',
                allowBlank: false,
                name: 'studyName',
                value: this.info.name,
                enableKeyEvents: true,
                listeners: {change: blurChange, keyup:nameChange, scope:this}
            },{
                xtype: 'textarea',
                fieldLabel: 'Description',
                name: 'studyDescription',
                height: '100',
                emptyText: 'Type Description here',
                listeners: {change:function(cmp, newValue, oldValue) {this.info.description = newValue;}, scope:this}
            },
            protocolDocField,
            locationField,
            folderTree
        ];

        this.nameFormPanel = new Ext.form.FormPanel({
            border: false,
            defaults: {
                labelSeparator: '',
                width: 510
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
            name: "General Setup",
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
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

        panel.on('show', function(cmp){
            locationField.setVisible(true);
        }, this);

        return panel;
    },

    getParticipantsPanel : function() {

        var items = [];

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: this.subject.nounPlural}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});// +
//                Ext.DomHelper.markup({tag:'div', html:'Choose the ' + this.subject.nounSingular.toLowerCase() + ' groups you would like to use from the parent study:'}) +
//                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});

        items.push({xtype:'displayfield', html: txt});

        this.noGroupRadio = new Ext.form.Radio({
            height: 20,
            boxLabel:'Use all ' + this.subject.nounPlural.toLowerCase() + ' from the source study',
            name: 'renderType',
            inputValue: 'all'
        });

        this.existingGroupRadio = new Ext.form.Radio({
            height: 20,
            boxLabel:'Select from existing ' + this.subject.nounSingular.toLowerCase() + ' groups',
            name: 'renderType',
            inputValue: 'existing',
            checked: true,
            scope:this,
            handler: existingGroupHandler
        });

        function existingGroupHandler(cmp, checked){
            if(checked == true){
                grid.setDisabled(false);
            } else {
                grid.setDisabled(true);
            }
        }

        items.push(
                {
                    xtype: 'radiogroup',
                    columns: 2,
                    fieldLabel: '',
                    style:"padding-bottom: 10px;",
                    labelWidth: 5,
                    items: [
                        this.existingGroupRadio,
                        this.noGroupRadio
                    ]
                }
        );

        this.store = new Ext.data.JsonStore({
            proxy: new Ext.data.HttpProxy({
                url : LABKEY.ActionURL.buildURL("participant-group", "browseParticipantGroups", null, {includePrivateGroups : false}),
                method : 'POST'
            }),
            baseParams: { type : 'participantGroup', includeParticipantIds: true},
            root: 'groups',
            fields: [
                {name: 'id', type: 'integer'},
                {name: 'label', type: 'string'},
                {name: 'type', type: 'string'},
                {name: 'createdBy', type: 'string', convert: function(v, record){return (v.displayValue ? v.displayValue : v.value)}},
                {name: 'modifiedBy', type: 'string', convert: function(v, record){return (v.displayValue ? v.displayValue : v.value)}},
                {name: 'shared', type: 'boolean', mapping: 'category.shared'},
                {name: 'participantIds', type: 'array'}
            ],
            sortInfo: {
                field: 'label',
                direction: 'ASC'
            },
            listeners: {
                load: function(store, records, options){
                    if(records.length > 0){
                        this.existingGroupRadio.setValue(true);
                    } else {
                        this.noGroupRadio.setValue(true);
                        this.existingGroupRadio.setDisabled(true);
                    }
                },
                scope: this
            },
            autoLoad: true
        });

        var selModel = new Ext.grid.CheckboxSelectionModel({checkOnly:true});
        selModel.on('selectionchange', function(cmp){this.selectedParticipantGroups = cmp.getSelections();}, this);

        var expander = new LABKEY.grid.RowExpander({
            tpl : new Ext.XTemplate(
                '<tpl for="participantIds">',
                    '<span class="testParticipantGroups" style="margin-left:30px;">{.}</span>' +
                    '<tpl if="this.isLineEnd(xindex, xcount)">' +
                        '<br>' +
                    '</tpl>' +
                '</tpl>' +
                '<div>&nbsp;</div>',
                {
                    compiled: true,
                    isLineEnd: function(idx, total){
                        var maxCols = Math.min(total / 5, 5);
                        return idx % maxCols < 1;
                    }
                })
            });

        var grid = new Ext.grid.GridPanel({
            store: this.store,
            flex: 1,
            selModel: selModel,
            plugins: expander,
            //hideHeaders: true,
            viewConfig: {forceFit: true, scrollOffset: 0},
            cls: 'studyWizardParticipantList',
            listeners: {
                cellclick: function(cmp, rowIndex, colIndex, event){
                    // ignore the checkbox selection
                    if (colIndex > 0)
                    {
                        event.stopEvent();
                        expander.toggleRow(rowIndex);
                    }
                },
                scope:this
            },

            columns: [
                selModel,
                {header:'&nbsp;&nbsp;All ' + this.subject.nounSingular + ' Groups', dataIndex:'label', renderer: this.participantGroupRenderer, scope: this}
            ]
        });

        this.participantGroupTemplate = new Ext.DomHelper.createTemplate(
                '<span style="margin-left:10px;" class="labkey-link">{0}</span>&nbsp;<span class="labkey-disabled">' +
                '<i>({1} ' + this.subject.nounPlural.toLowerCase() + ')</i></span>').compile();

        items.push(grid);

        this.participantPanel = new Ext.Panel({
            border: false,
            name: this.subject.nounPlural,
            layout: 'vbox',
            items: items,
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            }
        });

        this.participantPanel.on('beforehide', function(cmp){
            // if the prev button was pressed, we don't care about validation
            if (this.lastStep > this.currentStep)
                return;

            if (this.existingGroupRadio.getValue() == true && (!this.selectedParticipantGroups || this.selectedParticipantGroups.length == 0))
            {
                Ext.Msg.alert("Error", "You must select at least one " + this.subject.nounSingular + " group.");
                this.currentStep--;
                this.updateStep();
                return false;
            }
            return true;
        }, this);

        return this.participantPanel;
    },

    participantGroupRenderer : function(data, meta, record, rowIndex, colIndex, store)
    {
        var args = [];

        meta.css = 'labkey-link';
        meta.attr = 'ext:qtip="Click to hide or show the list of ' + this.subject.nounPlural + '"';
        args.push(Ext.util.Format.htmlEncode(data));
        args.push(record.data.participantIds.length);

        return this.participantGroupTemplate.apply(args);
    },

    getDatasetsPanel : function() {

        var items = [];

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Datasets'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'}) +
                Ext.DomHelper.markup({tag:'div', html:'Choose the datasets you would like to use from the source study:'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});

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
            cls: 'studyWizardDatasetList',
            flex: 1,
            bbar: [{hidden:true}],
            tbar: [{hidden:true}]
        });
        items.push(grid);
        grid.on('render', function(cmp){
            //This is to hide the background color of the bbar/tbar.
            cmp.getTopToolbar().getEl().dom.style.background = 'transparent';
            cmp.getBottomToolbar().getEl().dom.style.background = 'transparent';
        });
        grid.on('columnmodelcustomize', this.customizeColumnModel, this);
        grid.selModel.on('selectionchange', function(cmp){this.info.datasets = cmp.getSelections();}, this);

        var syncTip = '' +
            '<div>' +
                '<div class=\'g-tip-header\'>' +
                    '<span>Data Refresh</span>' +
                '</div>' +
                '<div class=\'g-tip-subheader\'>' +
                    '<span>Automatic:</span>' +
                    ' When data refreshes or changes in the source study, the data will ' +
                    'automatically refresh in the ancillary study as well.' +
                '</div>' +
                '<div class=\'g-tip-subheader\'>' +
                    '<span>Manual:</span> When data refreshes or changes in the source study, ' +
                    'the ancillary data will <b>not</b> refresh until you specifically choose to refresh.' +
                '</div>' +
            '</div>';

        this.snapshotOptions = new Ext.form.FormPanel({
            defaults: {labelSeparator: ''},
            items: [
                {xtype: 'hidden', name: 'updateDelay', value: 30},
                {xtype: 'radiogroup', fieldLabel: 'Data Refresh', gtip : syncTip, columns: 1, items: [
                    {name: 'autoRefresh', boxLabel: 'Automatic', inputValue: true, checked: true},
                    {name: 'autoRefresh', boxLabel: 'Manual', inputValue: false}]
                }
            ],
            padding: '10px 0px',
            border: false,
            height: 100,
//            labelWidth: 100,
            width : 300
        });
        items.push(this.snapshotOptions);

        var panel = new Ext.Panel({
            border: false,
            name: "Datasets",
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

        if (!this.info.datasets || (this.info.datasets.length == 0))
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
            for (var i=0; i < this.selectedParticipantGroups.length; i++)
            {
                var category = this.selectedParticipantGroups[i];
                var id = Ext.id();
                hiddenFields.push(id);
                this.nameFormPanel.add({xtype:'hidden', id: id, name: 'groups', value: category.id});
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

        this.win.getEl().mask("creating study...");

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
