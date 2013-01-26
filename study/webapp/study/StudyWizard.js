/*
 * Copyright (c) 2011-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext.namespace("LABKEY.study");

Ext.QuickTips.init();
Ext.GuidedTips.init();

LABKEY.study.CreateStudyWizard = Ext.extend(Ext.util.Observable, {

    constructor : function(config)
    {
        Ext.apply(this, config);
        this.pageOptions = this.initPages();
        this.requestId = config.requestId;

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

    initPages : function(){
        var pages = [];
        pages[0] = {
            panelType : 'name',
            active :  (this.namePanel == false) ? false : this.mode == 'publish' || this.mode == 'ancillary' || this.namePanel
        };
        pages[1] = {
            panelType : 'participants',
            active : (this.participantsPanel == false) ? false : this.mode == 'publish' || this.mode == 'ancillary' || this.participantsPanel
        };
        pages[2] = {
            panelType : 'datasets',
            active : (this.datasetsPanel == false) ? false : this.mode == 'publish' || this.mode == 'ancillary' || this.datasetsPanel
        };
        pages[3] = {
            panelType : 'visits',
            active : (this.visitsPanel == false) ? false : this.mode == 'publish' || this.vistsPanel
        };
        pages[4] = {
            panelType : 'lists',
            active : (this.listsPanel == false) ? false : this.mode == 'publish' || this.listsPanel
        };
        pages[5] = {
            panelType : 'views',
            active : (this.viewsPanel == false) ? false : this.mode == 'publish' || this.viewsPanel
        };
        pages[6] = {
            panelType : 'reports',
            active : (this.reportsPanel == false) ? false : this.mode == 'publish' || this.reportsPanel
        };
        pages[7] = {
            panelType : 'specimens',
            active : (this.specimensPanel == false) ? false : this.mode == 'publish' || this.specimensPanel,
        };
        pages[8] = {
            panelType : 'publishOptions',
            active : (this.publishOptionsPanel == false) ? false : this.mode == 'publish' || this.publishOptionsPanel
        };
        return pages;
    },

    show : function() {
        this.steps = [];
        this.info = {};
        this.currentStep = 0;
        this.lastStep = 0;

        //TODO:  This format is not pleasing.
        if(this.pageOptions[0].active == true) this.steps.push(this.getNamePanel());
        if(this.pageOptions[1].active == true) this.steps.push(this.getParticipantsPanel());
        if(this.pageOptions[2].active == true) this.steps.push(this.getDatasetsPanel());

        if(this.pageOptions[3].active == true) this.steps.push(this.getVisitsPanel());
        if(this.pageOptions[4].active == true) this.steps.push(this.getListsPanel());
        if(this.pageOptions[5].active == true) this.steps.push(this.getViewsPanel());
        if(this.pageOptions[6].active == true) this.steps.push(this.getReportsPanel());
        if(this.pageOptions[7].active == true) this.steps.push(this.getSpecimensPanel());
        if(this.pageOptions[8].active == true) this.steps.push(this.getPublishOptionsPanel());

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

        var setup = [
            {value: 'General Setup', currentStep: true}
         ];
        if(this.pageOptions[1].active) {
            setup.push({value: this.subject.nounPlural, currentStep: false});
        }
        else {
            setup.push({value : 'Participants', currentStep : false});
        }
        setup.push({value: 'Datasets', currentStep: false});

        if(this.studyType){
            setup.push({value: this.studyType == "VISIT" ? 'Visits' : 'Timepoints', currentStep: false});
        }

        setup.push(
        {value: 'Lists', currentStep: false},
        {value: 'Views', currentStep: false},
        {value: 'Reports', currentStep: false},
        {value: 'Specimens', currentStep: false},
        {value: 'Publish Options', currentStep: false}
        );

        var steps = [];
        for(var i = 0; i < this.pageOptions.length; i++){
            if(this.pageOptions[i].active == true){
                console.log(this.pageOptions[i].panelType);
                steps.push(setup[i]);
                console.log(setup[i].value);
            }
        }


        this.sideBar = new Ext.Panel({
            //This is going to be where the sidebar content goes.
            name: 'sidebar',
            width: 175,
            border: false,
            cls: 'extContainer',
            tpl: this.sideBarTemplate,
            data: {
                steps: steps
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
        var title;
        if(this.title) {
            title = this.title;
        }
        else if (this.mode == "ancillary") title ='Create Ancillary Study';
        else title = 'Publish Study';

        this.win = new Ext.Window({
            title: title,
            width: 875,
            height: 600,
            autoScroll: false,
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
                baseParams : {requiredPermission : 'org.labkey.api.security.permissions.AdminPermission', showContainerTabs: false}
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
                autoCreate: {tag: 'input', type: 'text', size: '20', autocomplete: 'off', maxlength: '255'},
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

        var panel = new Ext.Panel({
            cls : 'extContainer',
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
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});

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
            baseParams: { type : 'participantGroup', includeParticipantIds: true, includeUnassigned: false },
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
        this.pageOptions[1].value = this.selectedParticipantGroups;
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
            height: 50,
            width : 300
        });

        if (this.mode != 'publish')
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

    getViewsPanel: function(){
        this.selectedViews = [];
        var items = [];
        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Views'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'}) +
                Ext.DomHelper.markup({tag:'div', html:'Choose the views you would like to publish:'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});

        items.push({xtype:'displayfield', html: txt});

        var selectionModel = new Ext.grid.CheckboxSelectionModel({
            moveEditorOnEnter: false,
            listeners: {
                selectionChange: function(selModel){
                    this.selectedViews = selModel.getSelections();
                },
                scope: this
            }
        });

        var viewsStore = new Ext.data.Store({
            proxy: new Ext.data.HttpProxy({
                url: LABKEY.ActionURL.buildURL('study', 'browseData.api')
            }),
            reader: new Ext.data.JsonReader({
                root: 'data',
                fields: [
                    {name : 'id'},
                    {name : 'name'},
                    {name : 'category'},
                    {name : 'createdBy'},
                    {name : 'createdByUserId',      type : 'int'},
                    {name : 'type'},
                    {name : 'description'},
                    {name : 'schemaName'},
                    {name : 'queryName'},
                    {name : 'shared'}
                ]
            }),
            listeners: {
                load: function(store){
                    store.filterBy(function(record){
                        return record.get('type') === 'Query' &&
                                record.get('shared') == true;
                    });
                }
            },
            autoLoad: true
        });

        var grid = new Ext.grid.EditorGridPanel({
            store: viewsStore,
            selModel: selectionModel,
            columns: [
                selectionModel,
                {header: 'Name', width: 270, sortable: true, dataIndex: 'name'},
                {header: 'Category', width: 120, sortable: true, dataIndex:'category'},
                {header: 'Description', width: 215, sortable: true, dataIndex: 'description'}
            ],
            loadMask:{msg:"Loading, please wait..."},
            editable: false,
            stripeRows: true,
            pageSize: 300000,
            cls: 'studyWizardViewList',
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

        var panel = new Ext.Panel({
            border: false,
            name: "Views",
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });

        panel.on('activate', function(){
            // Filter out the views that will not be exported.
            var listQueries = {},
                studyQueries = {},
                i;
            
            for(i = 0; i < this.selectedLists.length; i++){
                listQueries[this.selectedLists[i].data.name] = this.selectedLists[i].data.name;
            }

            for(i = 0; i < this.info.datasets.length; i++){
                studyQueries[this.info.datasets[i].data.Label] = this.info.datasets[i].data.Label;
            }

            viewsStore.filterBy(function(record){
                var schemaName = record.get('schemaName'),
                    queryName = record.get('queryName');

                if(record.get('type') === 'Query' && record.get('shared')){
                    if(schemaName == 'lists'){
                        return listQueries[record.get('queryName')] != undefined;
                    } else if(schemaName == 'study'){
                        return studyQueries[record.get('queryName')] != undefined;
                    }
                }

                return false;
            }, this);
        }, this);
        this.pageOptions[5].value = this.selectedViews;
        return panel;
    },

    getReportsPanel: function(){
        this.selectedReports = [];
        
        var items = [];

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Reports'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'}) +
                Ext.DomHelper.markup({tag:'div', html:'Choose the reports you would like to publish:'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});

        items.push({xtype:'displayfield', html: txt});

        var selectionModel = new Ext.grid.CheckboxSelectionModel({
            moveEditorOnEnter: false,
            listeners: {
                selectionChange: function(selModel){
                    this.selectedReports = selModel.getSelections();
                },
                scope: this
            }
        });

        var grid = new Ext.grid.EditorGridPanel({
            store: new Ext.data.Store({
                proxy: new Ext.data.HttpProxy({
                    url: LABKEY.ActionURL.buildURL('study', 'browseData.api')
                }),
                reader: new Ext.data.JsonReader({
                    root: 'data',
                    fields: [
                        {name : 'id'},
                        {name : 'name'},
                        {name : 'category'},
                        {name : 'createdBy'},
                        {name : 'createdByUserId',      type : 'int'},
                        {name : 'type'},
                        {name : 'description'},
                        {name : 'schemaName'},
                        {name : 'queryName'},
                        {name : 'shared'}
                    ]
                }),
                listeners: {
                    load: function(store){
                        store.filterBy(function(record){
                            return record.get('type') != 'Dataset' &&
                                    record.get('type') != 'Query' &&
                                    record.get('shared') == true;
                        });
                    }
                },
                autoLoad: true
            }),
            selModel: selectionModel,
            columns: [
                selectionModel,
                {header: 'Name', width: 200, sortable: true, dataIndex: 'name'},
                {header: 'Category', width: 120, sortable: true, dataIndex:'category'},
                {header: 'Type', width: 140, sortable: true, dataIndex: 'type'},
                {header: 'Description', width: 145, sortable: true, dataIndex: 'description'}
            ],
            loadMask:{msg:"Loading, please wait..."},
            editable: false,
            stripeRows: true,
            pageSize: 300000,
            cls: 'studyWizardReportList',
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

        this.pageOptions[6].value = this.selectedReports;
        return new Ext.Panel({
            border: false,
            name: "Reports",
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });

    },

    getSpecimensPanel: function(){
        var items = [];

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Specimens'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});

        items.push({xtype:'displayfield', html: txt});

        this.includeSpecimensCheckBox = new Ext.form.Checkbox({
            xtype: 'checkbox',
            name: 'includeSpecimens',
            fieldLabel: 'Include Specimens?',
            checked: true,
            value: true,
            listeners: {
                check: function(cb, checked) {
                    this.specimenRefreshRadioGroup.setDisabled(!checked);
                    if (!checked)
                        this.specimenRefreshRadioGroup.reset();
                },
                scope: this
            }
        });

        this.specimenRefreshRadioGroup = new Ext.form.RadioGroup({
            xtype: 'radiogroup',
            fieldLabel: 'Refresh rate:',
            columns: 1,
            items: [
                {boxLabel: 'One-time snapshot', name: 'specimenRefresh', inputValue: false, checked: true},
                {boxLabel: 'Nightly refresh', name: 'specimenRefresh', inputValue: true}
            ]
        });

        var optionsPanel = new Ext.form.FormPanel({
            defaults: {labelSeparator: ''},
            padding: '10px 0px',
            border: false,
            labelWidth: 120,
            height: 200,
            width : 300,
            items: [
                this.includeSpecimensCheckBox,
                this.specimenRefreshRadioGroup
            ]
        });
        items.push(optionsPanel);

        return new Ext.Panel({
            border: false,
            name: "Specimens",
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });
    },

    getVisitsPanel: function(){
        var items = [];

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: this.studyType == "VISIT" ? 'Visits' : 'Timepoints'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'}) +
                Ext.DomHelper.markup({tag:'div', html:'Choose the ' + (this.studyType == "VISIT" ? 'visits' : 'timepoints') + ' you would like to publish:'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});

        items.push({xtype:'displayfield', html: txt});

        var grid = new LABKEY.ext.EditorGridPanel({
            store: new LABKEY.ext.Store({
                schemaName: 'study',
                queryName: 'visit',
                columns: 'RowId, Label, SequenceNumMin, SequenceNumMax, DisplayOrder',
                sort: 'DisplayOrder, SequenceNumMin'
            }),
            loadMask:{msg:"Loading, please wait..."},
            enableFilters: true,
            editable: false,
            stripeRows: true,
            pageSize: 300000,
            cls: 'studyWizardVisitList',
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
        grid.on('columnmodelcustomize', this.customizeVisitColumnModel, this);
        grid.selModel.on('selectionchange', function(cmp){this.selectedVisits = cmp.getSelections();}, this);

        var panel = new Ext.Panel({
            border: false,
            name: this.studyType == "VISIT" ? 'Visits' : 'Timepoints',
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });

        var validate = function(cmp){
            // if the prev button was pressed, we don't care about validation
            if (this.lastStep > this.currentStep)
                return;

            if(!this.selectedVisits || this.selectedVisits == 0){
                Ext.MessageBox.alert('Error', 'You must select at least one ' + (this.studyType == "VISIT" ? 'visit' : 'timepoint') + '.');
                this.currentStep--;
                this.updateStep();
                return false;
            }
            return true;
        };

        panel.on('beforehide', validate, this);
        this.pageOptions[3].value = this.selectedVisits;
        return panel;
    },

    getListsPanel: function(){
        this.selectedLists = [];

        var items = [];

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Lists'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'}) +
                Ext.DomHelper.markup({tag:'div', html:'Choose the lists you would like to publish:'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});

        items.push({xtype:'displayfield', html: txt});

        var selectionModel = new Ext.grid.CheckboxSelectionModel({
            moveEditorOnEnter: false,
            listeners: {
                selectionChange: function(selModel){
                    this.selectedLists = selModel.getSelections();
                },
                scope: this
            }
        });

        var grid = new Ext.grid.EditorGridPanel({
            store: new Ext.data.Store({
                proxy: new Ext.data.HttpProxy({
                    url: LABKEY.ActionURL.buildURL('list', 'browseLists.api')
                }),
                reader: new Ext.data.JsonReader({
                    root: 'lists',
                    fields: [
                        {name : 'id'},
                        {name : 'name'},
                        {name : 'description'}
                    ]
                }),
                autoLoad: true
            }),
            selModel: selectionModel,
            columns: [
                selectionModel,
                {header: 'Name', width: 300, sortable: true, dataIndex: 'name'},
                {header: 'Description', width: 300, sortable: true, dataIndex: 'description'}
            ],
            loadMask:{msg:"Loading, please wait..."},
            editable: false,
            stripeRows: true,
            pageSize: 300000,
            cls: 'studyWizardListList',
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

        var panel = new Ext.Panel({
            border: false,
            name: "Lists",
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });
        this.pageOptions[4].value = this.selectedLists;
        return panel;
    },

    getPublishOptionsPanel: function(){
        var items = [];

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Publish Options'});

        items.push({xtype:'displayfield', html: txt});

        //Export Alternate Participant IDs

        this.alternateIdsCheckBox = new Ext.form.Checkbox({
            name: 'alternateids',
            fieldLabel: "Export Alternate " + this.subject.nounSingular + " IDs?",
            checked: true,
            value: true
        });

        this.shiftDatesCheckBox = new Ext.form.Checkbox({
            xtype: 'checkbox',
            name: 'shiftDates',
            fieldLabel: 'Shift Participant Dates?',
            checked: true,
            value: true
        });

        this.protectedColumnsCheckBox = new Ext.form.Checkbox({
            xtype: 'checkbox',
            name: 'removeProtected',
            fieldLabel: 'Remove All Columns Tagged as Protected?',
            checked: true,
            value: true
        });

        var optionsPanel = new Ext.form.FormPanel({
            defaults: {labelSeparator: ''},
            padding: '10px 0px',
            border: false,
            labelWidth: 300,
            height: 300,
            width : 400,
            items: [
                this.protectedColumnsCheckBox,
                this.shiftDatesCheckBox,
                this.alternateIdsCheckBox
            ]
        });

        items.push(optionsPanel);

        var panel = new Ext.Panel({
            border: false,
            name: "Publish Options",
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });

        return panel;
    },

    customizeColumnModel : function(colModel, index, c){
        index.Label.header = 'Dataset';
        index.Label.width = 250;
        index.DataSetId.hidden = true;
    },

    customizeVisitColumnModel: function(colModel, index, c){
        index.RowId.hidden = true;
        index.Folder.hidden = true;
        index.DisplayOrder.hidden = true;

        index.SequenceNumMin.header = 'Sequence Min';
        index.SequenceNumMin.align = 'left';
        index.SequenceNumMax.header = 'Sequence Max';
        index.SequenceNumMax.align = 'left';

        index.SequenceNumMin.width = 70;
        index.SequenceNumMax.width = 70;
    },

    onFinish : function() {
        // prepare the params to post to the api action
        var params = {};

        params.name = this.info.name;
        params.description = this.info.description;
        params.srcPath = LABKEY.ActionURL.getContainer();
        params.dstPath = this.info.dstPath;

        if(this.mode == 'publish'){
            params.useAlternateParticipantIds = this.alternateIdsCheckBox.getValue();
            params.shiftDates = this.shiftDatesCheckBox.getValue();
            params.removeProtectedColumns = this.protectedColumnsCheckBox.getValue();
            params.includeSpecimens = this.includeSpecimensCheckBox.getValue();
            params.specimenRefresh = this.specimenRefreshRadioGroup.getValue().inputValue;
        }

        var hiddenFields = [];

        if(this.pageOptions[1].active){
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
        }
        if(this.info.datasets){
            for (var i=0; i < this.info.datasets.length; i++)
            {
                var ds = this.info.datasets[i];
                var id = Ext.id();
                hiddenFields.push(id);
                this.nameFormPanel.add({xtype:'hidden', id: id, name: 'datasets', value: ds.data.DataSetId});
            }
        }

        params.copyParticipantGroups = true;//this.copyParticipantGroups.checked;

        this.pageOptions[3].value = this.selectedVisits;
        this.pageOptions[4].value = this.selectedLists;
        this.pageOptions[5].value = this.selectedViews;
        this.pageOptions[6].value = this.selectedReports;

        if(this.pageOptions[8].active){
            id = Ext.id();
            hiddenFields.push(id);
            this.nameFormPanel.add({xtype: 'hidden', id: id, name: 'publish', value: true});
        }
        if(this.requestId){
            id = Ext.id();
            hiddenFields.push(id);
            this.nameFormPanel.add({xtype: 'hidden', id : id, name : 'requestId', value : this.requestId});
        }


        //TODO:  Get rid of mode here, or at least make it work in the context.
        if(this.mode == 'publish'){

            if(this.pageOptions[3].active){
                for(i = 0; i < this.selectedVisits.length; i++){
                    var visit = this.selectedVisits[i];
                    id = Ext.id();
                    hiddenFields.push(id);
                    this.nameFormPanel.add({xtype: 'hidden', id: id, name: 'visits', value: visit.data.RowId});
                }
            }
            if(this.pageOptions[4].active){
                for(i = 0; i < this.selectedLists.length; i++){
                    var list = this.selectedLists[i];
                    id = Ext.id();
                    hiddenFields.push(id);
                    this.nameFormPanel.add({xtype: 'hidden', id: id, name: 'lists', value: list.data.id});
                }
            }
            if(this.pageOptions[5].active){
                for(i = 0; i < this.selectedViews.length; i++){
                    var view = this.selectedViews[i];
                    id = Ext.id();
                    hiddenFields.push(id);
                    this.nameFormPanel.add({xtype: 'hidden', id: id, name: 'views', value: view.data.id});
                }
            }
            if(this.pageOptions[6].active){
                for(i = 0; i < this.selectedReports.length; i++){
                    var report = this.selectedReports[i];
                    id = Ext.id();
                    hiddenFields.push(id);
                    this.nameFormPanel.add({xtype: 'hidden', id: id, name: 'reports', value: report.data.id});
                }
            }


        }


        this.nameFormPanel.doLayout();

        //TODO:  Get rid of mode here, or at least make it work in the context.
        if(this.mode != 'publish'){
            var form = this.snapshotOptions.getForm();
            var refreshOptions = form.getValues();
            if (refreshOptions.autoRefresh === 'true')
                params.updateDelay = refreshOptions.updateDelay;
        }

        this.win.getEl().mask("creating study...");

        var createForm = new Ext.form.BasicForm(this.nameFormPanel.getForm().getEl(), {
            method : 'POST',
            url    : LABKEY.ActionURL.buildURL('study', 'createChildStudy'),
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
