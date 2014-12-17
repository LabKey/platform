/*
 * Copyright (c) 2011-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// TODO: I am pretty sure this is stashing the input and output into different variables.
// We should look to reuse those objects as we are not using the initial values for anything are we?

Ext.namespace("LABKEY.study");

Ext.QuickTips.init();
Ext.GuidedTips.init();

LABKEY.study.openCreateStudyWizard = function(snapshotId, availableContainerName) {
    // get the rest of the study snapshot details/settings from the server

    var sql = "SELECT c.DisplayName as PreviousStudy, u.DisplayName as CreatedBy, ss.Created, ss.Settings " +
              "FROM study.StudySnapshot AS ss LEFT JOIN core.Users AS u ON ss.CreatedBy = u.UserId " +
              "LEFT JOIN core.Containers AS c ON c.EntityId = ss.Destination WHERE ss.RowId = " + parseInt(snapshotId);

    LABKEY.Query.executeSql({
        schemaName: 'study',
        sql: sql,
        containerFilter: LABKEY.Query.containerFilter.allFolders,
        success: function(data) {
            var row = data.rows[0];
            var settings = Ext.decode(row.Settings);
            var config = {
                mode: (settings && settings.type ? settings.type : 'publish'),
                studyName: availableContainerName,
                settings: settings,
                previousStudy: row.PreviousStudy,
                createdBy: row.CreatedBy,
                created: row.Created
            };

            //issue 22070: specimen study republish needs to include requestId
            if (settings.type == 'specimen')
            {
                if (!settings.specimenRequestId) {
                    config.mode = 'publish';
                }
                else {
                    config.requestId = settings.specimenRequestId;
                    config.allowRefresh = false;
                }
            }

            // issue 22076: snapshot settings prior to 15.1 didn't have type specific so we guess between publish and ancillary
            if (settings.type == undefined && settings.visits == null) {
                config.mode = 'ancillary';
            }

            new LABKEY.study.CreateStudyWizard(config).show();
        },
        failure: function(response) {
            Ext.Msg.alert("Error", response.exception);
        }
    });

};

LABKEY.study.CreateStudyWizard = Ext.extend(Ext.util.Observable, {

    constructor : function(config)
    {
        Ext.applyIf(config, {
            allowRefresh : true,
            studyWriters : [],
            folderWriters : [],
            studyType : LABKEY.getModuleContext("study").timepointType,
            subject : LABKEY.getModuleContext("study").subject
        });
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

    // helper method to create set-like object for incoming arrays
    setify : function(arr) {
        var setObj = {};
        for (var i = 0; i < arr.length; i++) setObj[arr[i]] = true;
        return setObj;
    },

    initPages : function(){
        var pages = [];
        pages[0] = {
            panelType : 'name',
            active :  (this.namePanel == false) ? false : this.mode == 'publish' || this.mode == 'ancillary' || this.mode == 'specimen' || this.namePanel
        };
        pages[1] = {
            panelType : 'participants',
            active : (this.participantsPanel == false) ? false : this.mode == 'publish' || this.mode == 'ancillary' || this.participantsPanel
        };
        pages[2] = {
            panelType : 'datasets',
            active : (this.datasetsPanel == false) ? false : this.mode == 'publish' || this.mode == 'ancillary' || this.mode == 'specimen' || this.datasetsPanel
        };
        pages[3] = {
            panelType : 'visits',
            active : (this.visitsPanel == false) ? false : this.mode == 'publish' || this.visitsPanel
        };
        pages[4] = {
            panelType : 'specimens',
            active : (this.specimensPanel == false) ? false : this.mode == 'publish' || this.specimensPanel
        };
        pages[5] = {
            panelType : 'studyProps',
            active : (this.studyPropertiesPanel == false) ? false : this.mode == 'publish' || this.studyPropertiesPanel
        };
        pages[6] = {
            panelType : 'lists',
            active : (this.listsPanel == false) ? false : this.mode == 'publish' || this.listsPanel
        };
        pages[7] = {
            panelType : 'views',
            active : (this.viewsPanel == false) ? false : this.mode == 'publish' || this.viewsPanel
        };
        pages[8] = {
            panelType : 'reports',
            active : (this.reportsPanel == false) ? false : this.mode == 'publish' || this.reportsPanel
        };
        pages[9] = {
            panelType : 'FolderProps',
            active : (this.folderPropertiesPanel == false) ? false : this.mode == 'publish' || this.folderPropertiesPanel
        };
        pages[10] = {
            panelType : 'publishOptions',
            active : (this.publishOptionsPanel == false) ? false : this.mode == 'publish' || this.publishOptionsPanel
        };
        return pages;
    },

    loadWriters : function(store, isStudy) {

        if (this.studyWriters.length == 0 || this.folderWriters.length == 0)
        {
            LABKEY.Ajax.request({
                url : LABKEY.ActionURL.buildURL("core", "getRegisteredFolderWriters"),
                method : 'POST',
                scope : this,
                success : function(response) {
                    var allWriters = Ext.decode(response.responseText).writers;

                    var folderWritersToExclude = ['Custom Views', 'Lists', 'Notification Settings', 'Queries', 'Reports', 'Study'];
                    var studyWritersToExclude = ['Assay Datasets', 'Categories', 'CRF Datasets', 'Participant Groups', 'QC State Settings', 'Specimens', 'Visit Map'];
                    this.studyWriters = [];
                    this.folderWriters = [];

                    Ext.each(allWriters, function(writer){
                        if (folderWritersToExclude.indexOf(writer.name) == -1) {
                            this.folderWriters.push([writer.name]);
                        }

                        if (writer.name == 'Study' && Ext.isDefined(writer.children)) {
                            Ext.each(writer.children, function(child){
                                if (studyWritersToExclude.indexOf(child) == -1) {
                                    this.studyWriters.push([child]);
                                }
                            }, this);
                        }
                    }, this);

                    store.loadData(isStudy ? this.studyWriters : this.folderWriters);
                }
            });
        }
        else
        {
            store.loadData(isStudy ? this.studyWriters : this.folderWriters);
        }
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
        if(this.pageOptions[4].active == true) this.steps.push(this.getSpecimensPanel());
        if(this.pageOptions[5].active == true) this.steps.push(this.getStudyPropsPanel());
        if(this.pageOptions[6].active == true) this.steps.push(this.getListsPanel());
        if(this.pageOptions[7].active == true) this.steps.push(this.getViewsPanel());
        if(this.pageOptions[8].active == true) this.steps.push(this.getReportsPanel());
        if(this.pageOptions[9].active == true) this.steps.push(this.getFolderPropsPanel());
        if(this.pageOptions[10].active == true) this.steps.push(this.getPublishOptionsPanel());

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
                this.validateName();
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
                {value: 'Specimens', currentStep: false},
                {value: 'Study Objects', currentStep: false},
                {value: 'Lists', currentStep: false},
                {value: 'Views', currentStep: false},
                {value: 'Reports', currentStep: false},
                {value: 'Folder Objects', currentStep: false},
                {value: 'Publish Options', currentStep: false}
        );

        var steps = [];
        for(var i = 0; i < this.pageOptions.length; i++){
            if(this.pageOptions[i].active == true){
                steps.push(setup[i]);
            }
        }

        this.sideBar = new Ext.Panel({
            //This is going to be where the sidebar content goes.
            name: 'sidebar',
            width: 180,
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
        else if (this.mode == "ancillary") {
            title ='Create Ancillary Study';
        }
        else if (this.mode == "specimen") {
            title = (this.settings ? 'Republish' : 'Publish') + ' Specimen Study';
        }
        else {
            title = (this.settings ? 'Republish' : 'Publish') + ' Study';
        }

        this.win = new Ext.Window({
            title: title,
            resizable: false,
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
        var me = this;
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
        else if(this.currentStep == 0)
        {
            this.nextBtn.setText('Next');
            this.nextBtn.setHandler(function(){
                me.validateName();
            });
        }
        else
        {
            this.nextBtn.setText('Next');
            this.nextBtn.setHandler(function(){
                    me.currentStep++;
                    me.updateStep();
            }, this);
        }
        this.pages.getLayout().setActiveItem(this.currentStep);
    },

    validateName : function()
    {
        LABKEY.Security.getContainers({
            path : LABKEY.ActionURL.getContainer(),
            success : function(resp)
            {
                var present =  false;
                for(var i = 0; i < resp.children.length; i++)
                {
                    if(resp.children[i].path ==  this.info.dstPath)
                    {
                        present = true;
                        break;
                    }
                }
                this.checkIfStudyPresent(present);
            },
            scope : this

        });


    },

    checkIfStudyPresent : function(present)
    {
        if(!present)
        {
            this.currentStep++;
            this.updateStep();
        }
        else
        {
            LABKEY.Query.selectRows({
                containerPath : this.info.dstPath,
                schemaName : 'study',
                queryName : 'studyProperties',
                success : function(resp)
                {
                    if(resp.rows.length > 0)
                    {
                        Ext.Msg.alert('Study Already Present', 'There is already a study in the target folder');
                    }
                    else
                    {
                        this.currentStep++;
                        this.updateStep();
                    }
                },
                scope : this
            });
        }
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
            flex: 1,
            readOnly: true,
            fieldClass: 'x-form-empty-field',
            value: this.info.dstPath,
            scope: this
        });

        var changeFolderBtn = new Ext.Button({
            name:"changeFolderBtn",
            text: "Change",
            cls: "labkey-button",
            handler: browseFolders
        });

        var locationField = new Ext.form.CompositeField({
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

        function nameChange(txtField){
            var newValue = txtField.getValue();
            var path;
            if(folderTree.getSelectionModel().getSelectedNode()){
                var attributes = folderTree.getSelectionModel().getSelectedNode().attributes;
                path = (attributes.hasOwnProperty('containerPath')) ? attributes.containerPath : "";
            } else {
                path = LABKEY.ActionURL.getContainer();
            }
            this.info.name = newValue;
            this.info.dstPath = path + '/' + this.info.name;
            studyLocation.setValue(this.info.dstPath);
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
                click: onClick,
                dblclick: onDblClick,
                scope:this
            },
            cls : 'folder-management-tree', // used by selenium helper
            header:false,
            style: "margin-left: 130px;", //Line up the folder tree with the rest of the form elements.
            rootVisible: true,
            enableDD: false,
            animate : true,
            useArrows : true,
            height: 130,
            collapsible : true,
            collapsed: true,
            autoScroll: true,
            border: true
        });

        function onClick(e){
            if(e.attributes.containerPath){
                studyLocation.setValue(e.attributes.containerPath + '/' + this.info.name);
                this.info.dstPath = e.attributes.containerPath + '/' + this.info.name
            } else {
                studyLocation.setValue("/" + this.info.name);
                this.info.dstPath = "/" + this.info.name;
            }
        }

        function onDblClick(e){
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

        var formItems = [{ xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF }];

        if (this.settings) // show extra details if this is a republish
        {
            formItems.push({
                xtype: 'displayfield',
                fieldLabel: 'Previous Child Study', // uh, better name?
                style: 'font: normal 12px tahoma, arial, helvetica, sans-serif;',
                value: this.previousStudy
            },{
                xtype: 'displayfield',
                fieldLabel: 'Created By',
                style: 'font: normal 12px tahoma, arial, helvetica, sans-serif;',
                value: this.createdBy
            },
            {
                xtype: 'displayfield',
                fieldLabel: 'Created',
                style: 'font: normal 12px tahoma, arial, helvetica, sans-serif;',
                value: new Date(this.created).format(LABKEY.extDefaultDateFormat)
            });

            formItems.unshift({
                xtype: 'box',
                width: 550,
                html: '**This study is being republished and has preset values based on the previous publish studies values.',
                style: 'font: normal 12px tahoma, arial, helvetica, sans-serif; margin-bottom: 15px;'
            });
        }

        formItems.push([
            {
                xtype: 'textfield',
                fieldLabel: 'Name',
                autoCreate: {tag: 'input', type: 'text', size: '20', autocomplete: 'off', maxlength: '255'},
                allowBlank: false,
                name: 'studyName',
                value: this.info.name,
                enableKeyEvents: true,
                listeners: {change: nameChange, keyup:nameChange, scope:this}
            },
            {
                xtype: 'textarea',
                fieldLabel: 'Description',
                name: 'studyDescription',
                height: '100',
                emptyText: 'Type Description here',
                listeners: {
                    change:function(cmp, newValue, oldValue) {this.info.description = newValue;},
                    afterrender: function(cmp) { if(this.settings){ cmp.setValue(this.settings.description); this.info.description = this.settings.description;}},
                    scope:this
                }
            },
            protocolDocField,
            locationField,
            folderTree
        ]);

        this.nameFormPanel = new Ext.form.FormPanel({
            border: false,
            defaults: {
                labelSeparator: '',
                width: 475
            },
            flex: 1,
            labelWidth: 125,
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
            style:"margin-left: 2px",
            boxLabel:'Use all ' + this.subject.nounPlural.toLowerCase() + ' from the source study',
            name: 'renderType',
            inputValue: 'all'
        });

        this.existingGroupRadio = new Ext.form.Radio({
            height: 20,
            style:"margin-left: 2px",
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
                    ],
                    listeners: {
                        scope: this,
                        afterrender: function(cmp) {
                            if (this.settings && this.settings.participants == null) cmp.setValue('all');
                        }
                    }
                }
        );

        var participantGroupStore = new Ext.data.JsonStore({
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
        selModel.on('selectionchange', function(cmp){
            this.selectedParticipantGroups = cmp.getSelections();
            this.selectedParticipantGroupsAll = selModel.getCount() == participantGroupStore.getCount();
        }, this);

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
            store: participantGroupStore,
            flex: 1,
            selModel: selModel,
            plugins: expander,
            viewConfig: {forceFit: true},
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
                {header:'&nbsp;&nbsp;' + this.subject.nounSingular + ' Groups', dataIndex:'label', renderer: this.participantGroupRenderer, scope: this}
            ]
        });

        grid.on('viewready', function(grid){
            if (this.settings && this.settings.participants != null)
            {
                if (Ext.isDefined(this.settings.participantGroups) && this.settings.participantGroups == null)
                {
                    this.gridSelectAll(grid);
                }
                else
                {
                    var participantGroups = this.setify(this.settings.participantGroups);
                    grid.getSelectionModel().selectRecords(grid.store.queryBy(function(rec) { return (rec.get('id') in participantGroups); }).getRange(), true);
                }
            }
        }, this);

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
                columns: 'dataSetId, name, label, category, description',
                sort: 'label'
            }),
            viewConfig: {forceFit: true},
            loadMask:{msg:"Loading, please wait..."},
            enableFilters: true,
            editable: false,
            stripeRows: true,
            pageSize: 300000,
            height : 175,
            cls: 'studyWizardDatasetList',
            bbarCfg: [{hidden:true}],
            tbarCfg: [{hidden:true}]
        });

        var hiddenGrid = new LABKEY.ext.EditorGridPanel({
            store: new LABKEY.ext.Store({
                schemaName: 'study',
                queryName: 'Datasets',
                filterArray: [ LABKEY.Filter.create('ShowByDefault', false) ],
                columns: 'dataSetId, name, label, category, description',
                sort: 'label',
                listeners :   {
                    scope : this,
                    load : function(store)
                    {
                        if(store.data.items.length == 0)
                        {
                            grid.setHeight(this.mode == 'ancillary' ? 350 : 325);
                            hiddenGrid.hide();
                        }
                    }
                }
            }),
            title : 'Hidden Datasets',
            viewConfig: {forceFit: true},
            loadMask:{msg:"Loading, please wait..."},
            enableFilters: true,
            editable: false,
            stripeRows: true,
            pageSize: 300000,
            height : this.mode == 'ancillary' ? 175 : 150,
            cls: 'studyWizardHiddenDatasetList',
            bbarCfg: [{hidden:true}],
            tbarCfg: [{hidden:true}]
        });

        items.push(grid);
        items.push(hiddenGrid);

        grid.on('render', function(cmp){
            //This is to hide the background color of the bbar/tbar.
            cmp.getTopToolbar().getEl().dom.style.background = 'transparent';
            cmp.getBottomToolbar().getEl().dom.style.background = 'transparent';
        });
        hiddenGrid.on('render', function(cmp){
            cmp.getTopToolbar().getEl().dom.style.background = 'transparent';
            cmp.getBottomToolbar().getEl().dom.style.background = 'transparent';
        });
        grid.on('columnmodelcustomize', this.customizeColumnModel, this);
        grid.selModel.on('selectionchange', function(cmp){this.info.datasets = cmp.getSelections();}, this);
        hiddenGrid.on('columnmodelcustomize', this.customizeColumnModel, this);
        hiddenGrid.selModel.on('selectionchange', function(cmp){this.info.hiddenDatasets = cmp.getSelections();}, this);

        var viewReadyFunc = function(grid){
            if (this.settings)
            {
                if (!this.settings.datasets)
                {
                    this.gridSelectAll(grid);
                }
                else
                {
                    var datasets = this.setify(this.settings.datasets);
                    grid.getSelectionModel().selectRecords(grid.store.queryBy(function(rec) { return (rec.get('DataSetId') in datasets); }).getRange(), true);
                }
            }
        };

        grid.on('viewready', viewReadyFunc, this);
        hiddenGrid.on('viewready', viewReadyFunc, this);

        var syncTip = '' +
            '<div>' +
                '<div class=\'g-tip-header\'>' +
                    '<span>Data Refresh</span>' +
                '</div>';
        if (this.mode == 'publish')
        {
            syncTip += '<div class=\'g-tip-subheader\'>' +
                 '<span>None:</span>' +
                     ' The data in this published study will not refresh.' +
                '</div>';
        }
        syncTip += '<div class=\'g-tip-subheader\'>' +
                 '<span>Automatic:</span>' +
                    ' When data refreshes or changes in the source study, the data will ' +
                    'automatically refresh in the ' +
                    (this.mode == 'ancillary' ? 'ancillary' : 'published') +
                    ' study as well.' +
                '</div>' +
                '<div class=\'g-tip-subheader\'>' +
                    '<span>Manual:</span> When data refreshes or changes in the source study, the ' +
                    (this.mode == 'ancillary' ? 'ancillary' : 'published') +
                    ' data will <b>not</b> refresh until you specifically choose to refresh.' +
                '</div>' +
            '</div>';

        if(this.allowRefresh)
        {
            this.snapshotOptions = new Ext.form.FormPanel({
                items: [
                    {
                        xtype: 'radiogroup', fieldLabel: 'Data Refresh', gtip : syncTip, columns: 1, width: 300, height: 75, defaults: {style:"margin: 0 0 2px 2px"},
                        items: [
                            {name: 'refreshType', boxLabel: 'None', inputValue: 'None', checked: this.mode != 'ancillary', hidden: this.mode == 'ancillary'},
                            {name: 'refreshType', boxLabel: 'Automatic', inputValue: 'Automatic', checked: this.mode == 'ancillary'},
                            {name: 'refreshType', boxLabel: 'Manual', inputValue: 'Manual'}
                        ],
                        listeners: {
                            scope: this,
                            afterrender: function(cmp) {
                                if (this.settings) {
                                    if (!this.settings.datasetRefresh) cmp.setValue('None');
                                    else if (this.settings.datasetRefreshDelay == null) cmp.setValue('Manual');
                                    else cmp.setValue('Automatic');
                                }
                            }
                        }
                    }
                ],
                padding: '10px 0 0 0',
                border: false,
                height: 85,
                width : 300
            });

            items.push(this.snapshotOptions);
        }

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
                    this.selectedViewsAll = selModel.getCount() == viewsStore.getCount();
                },
                scope: this
            }
        });

        var viewsStore = new Ext.data.Store({
            proxy: new Ext.data.HttpProxy({
                url: LABKEY.ActionURL.buildURL('reports', 'browseData.api')
            }),
            reader: new Ext.data.JsonReader({
                root: 'data',
                fields: [
                    {name : 'id'},
                    {name : 'name'},
                    {name : 'category', mapping : 'category.label', convert : function(v) { if (v == 'Uncategorized') return ''; return v; }},
                    {name : 'createdBy'},
                    {name : 'createdByUserId',      type : 'int'},
                    {name : 'type'},
                    {name : 'icon'},
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
                {header: 'Name', width: 270, sortable: true, dataIndex: 'name', renderer: Ext.util.Format.htmlEncode},
                {header: 'Category', width: 120, sortable: true, dataIndex:'category', renderer: Ext.util.Format.htmlEncode},
                {header: 'Description', width: 215, sortable: true, dataIndex: 'description', renderer: Ext.util.Format.htmlEncode}
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
        grid.on('viewready', function(grid){
            if (this.settings)
            {
                if (!this.settings.views)
                {
                    this.gridSelectAll(grid);
                }
                else
                {
                    var views = this.setify(this.settings.views);
                    grid.getSelectionModel().selectRecords(grid.store.queryBy(function(rec) { return (rec.get('name') in views); }).getRange(), true);
                }
            }
        }, this);

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

            if (this.info.datasets)
            {
                for(i = 0; i < this.info.datasets.length; i++){
                    studyQueries[this.info.datasets[i].data.Name] = this.info.datasets[i].data.Name;
                }
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
        this.pageOptions[7].value = this.selectedViews;
        return panel;
    },

    getStudyPropsPanel : function(){
        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Study Objects'})+
            Ext.DomHelper.markup({tag:'div', html: '&nbsp'})+
            Ext.DomHelper.markup({tag:'div', html: 'Choose additional study objects to publish:'})+
            Ext.DomHelper.markup({tag:'div', html: '&nbsp'});

        var selectionModel = new Ext.grid.CheckboxSelectionModel({
            moveEditorOnEnter: false,
            listeners: {
                selectionChange: function(selModel){
                    this.selectedStudyObjects = selModel.getSelections();
                    this.selectedStudyObjectsAll = selModel.getCount() == studyStore.getCount();
                },
                scope: this
            }
        });

        var studyStore = new Ext.data.ArrayStore({
            fields : [{name : 'name', type : 'string'}],
            sortInfo : {field: 'name', direction: 'ASC'},
            data : []
        });

        this.loadWriters(studyStore, true);

        var selectionGrid = new Ext.grid.EditorGridPanel({
            cls : 'studyObjects',
            store: studyStore,
            selModel: selectionModel,
            viewConfig: {forceFit: true, scrollOffset: 0},
            columns: [
                selectionModel,
                {header: 'Name', sortable: true, dataIndex: 'name'}
            ],
            loadMask:{msg:"Loading, please wait..."},
            editable: false,
            stripeRows: true,
            pageSize: 300000,
            flex: 1,
            bbar: [{hidden:true}],
            tbar: [{hidden:true}]
        });

        selectionGrid.on('render', function(cmp){
            //This is to hide the background color of the bbar/tbar.
            cmp.getTopToolbar().getEl().dom.style.background = 'transparent';
            cmp.getBottomToolbar().getEl().dom.style.background = 'transparent';
        });
        selectionGrid.on('viewready', function(grid){
            if (this.settings)
            {
                if (!this.settings.studyObjects)
                {
                    this.gridSelectAll(grid);
                }
                else
                {
                    var studyObjects = this.setify(this.settings.studyObjects);
                    grid.getSelectionModel().selectRecords(grid.store.queryBy(function(rec) { return (rec.get('name') in studyObjects); }).getRange(), true);
                }
            }
        }, this);

        this.studyPropsPanel = new Ext.FormPanel({
            name : 'Study Objects',
            html : txt,
            border : false,
            layout : 'vbox',
            items : selectionGrid
        });
        return this.studyPropsPanel;
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
                    this.selectedReportsAll = selModel.getCount() == reportsStore.getCount();
                },
                scope: this
            }
        });

        var reportsStore = new Ext.data.Store({
            proxy: new Ext.data.HttpProxy({
                url: LABKEY.ActionURL.buildURL('reports', 'browseData.api')
            }),
            reader: new Ext.data.JsonReader({
                root: 'data',
                fields: [
                    {name : 'id'},
                    {name : 'name'},
                    {name : 'category', mapping : 'category.label', convert : function(v) { if (v == 'Uncategorized') return ''; return v; }},
                    {name : 'createdBy'},
                    {name : 'createdByUserId',      type : 'int'},
                    {name : 'type'},
                    {name : 'icon'},
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
            autoLoad: true,
            sortInfo: {field: 'name', direction: 'ASC'}
        });

        var grid = new Ext.grid.EditorGridPanel({
            viewConfig : {
                forceFit : true
            },
            store: reportsStore,
            selModel: selectionModel,
            columns: [
                selectionModel,
                {header: 'Name', width: 200, sortable: true, dataIndex: 'name', renderer: Ext.util.Format.htmlEncode},
                {header: 'Category', width: 120, sortable: true, dataIndex:'category', renderer: Ext.util.Format.htmlEncode},
                {header: 'Type', width: 60, sortable: true, dataIndex: 'type', renderer : function(v, meta, rec) {
                    return '<div style="margin-left: auto; margin-right: auto; width: 30px; vertical-align: middle;">' +
                                '<img title="' + rec.data.type + '" src="' + rec.data.icon + '" alt="' + rec.data.type +'" height="16px" width="16px">' +
                           '</div>';
                }
                },
                {header: 'Description', sortable: true, dataIndex: 'description', renderer: Ext.util.Format.htmlEncode}
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
        grid.on('viewready', function(grid){
            if (this.settings)
            {
                if (!this.settings.reports)
                {
                    this.gridSelectAll(grid);
                }
                else
                {
                    var reports = this.setify(this.settings.reports);
                    grid.getSelectionModel().selectRecords(grid.store.queryBy(function(rec) { return (rec.data['name'] in reports); }).getRange(), true);
                }
            }
        }, this);

        this.pageOptions[8].value = this.selectedReports;
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
                afterrender: function(cb) {
                    if (this.settings) cb.setValue(this.settings.includeSpecimens);
                },
                scope: this
            }
        });

        this.specimenRefreshRadioGroup = new Ext.form.RadioGroup({
            xtype: 'radiogroup',
            fieldLabel: 'Refresh rate:',
            columns: 1,
            items: [
                {boxLabel: 'One-time snapshot', name: 'specimenRefresh', inputValue: 'false', checked: true, style:"margin-left: 2px"},
                {boxLabel: 'Nightly refresh', name: 'specimenRefresh', inputValue: 'true', style:"margin-left: 2px"}
            ],
            listeners: {
                scope: this,
                afterrender: function(cmp) {
                    if (this.settings) cmp.setValue(this.settings.specimenRefresh.toString());
                }
            }
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
        grid.on('viewready', function(grid){
            if (this.settings)
            {
                if (!this.settings.visits)
                {
                    this.gridSelectAll(grid);
                }
                else
                {
                    var visits = this.setify(this.settings.visits);
                    var records = grid.store.queryBy(function(rec) { return (rec.get('RowId') in visits); }).getRange();
                    if ( grid.store.getCount() == records.length )
                        this.gridSelectAll(grid);
                    else
                        grid.getSelectionModel().selectRecords(records, true);
                }
            }
        }, this);

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
            viewConfig : { forceFit : true },
            columns: [
                selectionModel,
                {header: 'Name', sortable: true, dataIndex: 'name', renderer: Ext.util.Format.htmlEncode}
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
        grid.on('viewready', function(grid){
            if (this.settings)
            {
                if(!this.settings.lists)
                {
                    this.gridSelectAll(grid);
                }
                else
                {
                    var lists = this.setify(this.settings.lists);
                    grid.getSelectionModel().selectRecords(grid.store.queryBy(function(rec) { return (rec.get('name') in lists); }).getRange(), true);
                }
            }
        }, this);

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
        this.pageOptions[6].value = this.selectedLists;
        return panel;
    },

    getFolderPropsPanel : function(){
        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Folder Objects'})+
                Ext.DomHelper.markup({tag:'div', html: '&nbsp'})+
                Ext.DomHelper.markup({tag:'div', html: 'Choose additional folder objects to publish:'})+
                Ext.DomHelper.markup({tag:'div', html: '&nbsp'});

        var selectionModel = new Ext.grid.CheckboxSelectionModel({
            moveEditorOnEnter: false,
            listeners: {
                selectionChange: function(selModel){
                    this.selectedFolderObjects = selModel.getSelections();
                    this.selectedFolderObjectsAll = selModel.getCount() == folderStore.getCount();
                },
                scope: this
            }
        });

        var folderStore = new Ext.data.ArrayStore({
            fields : [{name : 'name', type : 'string'}],
            sortInfo : {field: 'name', direction: 'ASC'},
            data : []
        });

        this.loadWriters(folderStore, false);

        var selectionGrid = new Ext.grid.EditorGridPanel({
            cls: 'folderObjects',
            store: folderStore,
            selModel: selectionModel,
            viewConfig: {forceFit: true, scrollOffset: 0},
            columns: [
                selectionModel,
                {header: 'Name', sortable: true, dataIndex: 'name', name: 'name'}
            ],
            loadMask:{msg:"Loading, please wait..."},
            editable: false,
            stripeRows: true,
            pageSize: 300000,
            flex: 1,
            bbar: [{hidden:true}],
            tbar: [{hidden:true}]
        });

        selectionGrid.on('render', function(cmp){
            //This is to hide the background color of the bbar/tbar.
            cmp.getTopToolbar().getEl().dom.style.background = 'transparent';
            cmp.getBottomToolbar().getEl().dom.style.background = 'transparent';
        });
        selectionGrid.on('viewready', function(grid){
            if (this.settings)
            {
                if (!this.settings.folderObjects)
                {
                    this.gridSelectAll(grid);
                }
                else
                {
                    var folderObjects = this.setify(this.settings.folderObjects);
                    var records = grid.store.queryBy(function(rec) { return (rec.get('name') in folderObjects); }).getRange();
                    if (records.length == grid.store.getCount())
                    {
                        this.gridSelectAll(grid);
                    }
                    else
                    {
                        grid.getSelectionModel().selectRecords(records, true);
                    }
                }
            }
        }, this);

        this.folderPropsPanel = new Ext.FormPanel({
            name : 'Folder Objects',
            html : txt,
            border : false,
            layout : 'vbox',
            items : selectionGrid
        });
        return this.folderPropsPanel;
    },

    getPublishOptionsPanel: function(){
        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Publish Options'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'}) +
                Ext.DomHelper.markup({tag:'div', html:'Choose publish options:'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});

        var selectionModel = new Ext.grid.CheckboxSelectionModel({
            moveEditorOnEnter: false,
            listeners: {
                selectionChange: function(selModel) {
                    this.selectedPublishOptions = selModel.getSelections();
                },
                scope: this
            }
        });

        var publishOptionsStore = new Ext.data.ArrayStore({
            fields : [
                {name : 'name', type : 'string'},
                {name : 'description', type : 'string'},
                {name : 'option', type : 'string'}
            ],
            data : [
                ['Use Alternate ' + this.subject.nounSingular + ' IDs',
                 'This will replace each ' + this.subject.nounSingular.toLowerCase() + ' id by an alternate randomly generated id.',
                 'useAlternateParticipantIds'],
                ['Shift ' + this.subject.nounSingular + ' Dates',
                 'This will shift selected date values associated with a ' + this.subject.nounSingular.toLowerCase() + ' by a random, ' + this.subject.nounSingular.toLowerCase() + ' specific, offset (from 1 to 365 days).',
                 'shiftDates'],
                ['Remove Protected Columns',
                 'Selecting this option will exclude all dataset, list, and specimen columns that have been tagged as protected columns.',
                 'removeProtectedColumns'],
                ['Mask Clinic Names',
                 'Selecting this option will change the labels for clinics in the published list of locations to a generic label (i.e. Clinic).',
                 'maskClinic']
            ]
        });

        // http://stackoverflow.com/questions/2106104/word-wrap-grid-cells-in-ext-js
        var columnWrap = function(val){
            return '<div style="white-space:normal !important;">'+ val +'</div>';
        };

        var selectionGrid = new Ext.grid.EditorGridPanel({
            cls: 'studyWizardPublishOptionsList',
            store: publishOptionsStore,
            selModel: selectionModel,
            viewConfig: {forceFit: true, scrollOffset: 0},
            columns: [
                selectionModel,
                {header: 'Name', width: 220, sortable: true, dataIndex: 'name', name: 'name'},
                {header: 'Description', width: 380, sortable: true, dataIndex: 'description', name: 'description', renderer: columnWrap}
            ],
            loadMask: {msg:"Loading, please wait..."},
            editable: false,
            stripeRows: true,
            pageSize: 300000,
            flex: 1,
            bbar: [{hidden:true}],
            tbar: [{hidden:true}]
        });

        selectionGrid.on('render', function(cmp){
            //This is to hide the background color of the bbar/tbar.
            cmp.getTopToolbar().getEl().dom.style.background = 'transparent';
            cmp.getBottomToolbar().getEl().dom.style.background = 'transparent';
        });
        selectionGrid.on('viewready', function(grid){
            if (this.settings)
            {
                var publishOptions = {};
                publishOptions['maskClinic'] = this.settings.maskClinic;
                publishOptions['removeProtectedColumns'] = this.settings.removeProtectedColumns;
                publishOptions['shiftDates'] = this.settings.shiftDates;
                publishOptions['useAlternateParticipantIds'] = this.settings.useAlternateParticipantIds;
                if (this.settings.maskClinic && this.settings.removeProtectedColumns && this.settings.shiftDates && this.settings.useAlternateParticipantIds)
                    this.gridSelectAll(grid);
                else
                    grid.getSelectionModel().selectRecords(grid.store.queryBy(function(rec) { return publishOptions[rec.get('option')]; }).getRange(), true);
            }
        }, this);

        this.publishOptionsPanel = new Ext.Panel({
            border: false,
            name: "Publish Options",
            layout: 'vbox',
            html: txt,
            items: selectionGrid
        });

        return this.publishOptionsPanel;
    },

    customizeColumnModel : function(colModel, index, c){
        index.Name.renderer = Ext.util.Format.htmlEncode;
        index.Label.renderer = Ext.util.Format.htmlEncode;
        index.description.renderer = Ext.util.Format.htmlEncode;

        index.Label.header = 'Label';
        index.Label.width = 250;
        index.DataSetId.hidden = true;
        index.Name.hidden = true;
    },

    customizeVisitColumnModel: function(colModel, index, c){
        index.RowId.hidden = true;
        index.Folder.hidden = true;
        index.DisplayOrder.hidden = true;

        index.Label.header = 'Label';
        index.Label.renderer = Ext.util.Format.htmlEncode;

        index.SequenceNumMin.header = 'Sequence Min';
        index.SequenceNumMin.align = 'left';
        index.SequenceNumMax.header = 'Sequence Max';
        index.SequenceNumMax.align = 'left';

        index.SequenceNumMin.width = 70;
        index.SequenceNumMax.width = 70;
    },

    gridSelectAll: function(grid) {
        var el = grid.el.dom.getElementsByClassName('x-grid3-hd-checker')[0];
        Ext.fly(el).addClass('x-grid3-hd-checker-on');
        grid.getSelectionModel().selectAll();
    },

    onFinish : function() {
        // prepare the params to post to the api action
        var params = {};

        params.name = this.info.name;
        params.mode = this.mode;
        params.description = this.info.description;
        params.srcPath = LABKEY.ActionURL.getContainer();
        params.dstPath = this.info.dstPath;

        if(this.mode == 'publish'){
            if (this.selectedPublishOptions)
                for (var i = 0; i < this.selectedPublishOptions.length; i++)
                    params[this.selectedPublishOptions[i].get('option')] = true;
            params.includeSpecimens = this.includeSpecimensCheckBox.getValue();
            params.specimenRefresh = eval(this.specimenRefreshRadioGroup.getValue().inputValue);
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

                if (this.selectedParticipantGroupsAll) {
                    params.participantGroupsAll = true;
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
        if(this.info.hiddenDatasets)
        {
            for (var i=0; i < this.info.hiddenDatasets.length; i++)
            {
                var ds = this.info.hiddenDatasets[i];
                var id = Ext.id();
                hiddenFields.push(id);
                this.nameFormPanel.add({xtype:'hidden', id: id, name: 'datasets', value: ds.data.DataSetId});
            }
        }

        params.copyParticipantGroups = true;

        this.pageOptions[3].value = this.selectedVisits;
        this.pageOptions[6].value = this.selectedLists;
        this.pageOptions[7].value = this.selectedViews;
        this.pageOptions[8].value = this.selectedReports;

        if(this.requestId){
            id = Ext.id();
            hiddenFields.push(id);
            this.nameFormPanel.add({xtype: 'hidden', id : id, name : 'requestId', value : this.requestId});
        }

        if (this.pageOptions[5].active && this.selectedStudyObjects)
        {
            var studyProps = [];
            id = Ext.id();
            for(var i = 0; i < this.selectedStudyObjects.length; i++)
            {
                studyProps.push(this.selectedStudyObjects[i].json[0]);
            }
            this.nameFormPanel.add({xtype : 'hidden', id : id, name : 'studyProps', value : studyProps});

            if (this.selectedStudyObjectsAll) {
                params.studyPropsAll = true;
            }
        }
        if (this.pageOptions[8].active && this.selectedFolderObjects)
        {
            var folderProps = [];
            id = Ext.id();
            for(var i = 0; i < this.selectedFolderObjects.length; i++)
            {
                folderProps.push(this.selectedFolderObjects[i].json[0]);

            }
            this.nameFormPanel.add({xtype : 'hidden', id : id, name : 'folderProps', value : folderProps});

            if (this.selectedFolderObjectsAll) {
                params.folderPropsAll = true;
            }
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
            if(this.pageOptions[6].active){
                for(i = 0; i < this.selectedLists.length; i++){
                    var list = this.selectedLists[i];
                    id = Ext.id();
                    hiddenFields.push(id);
                    this.nameFormPanel.add({xtype: 'hidden', id: id, name: 'lists', value: list.data.id});
                }
            }
            if(this.pageOptions[7].active){
                for(i = 0; i < this.selectedViews.length; i++){
                    var view = this.selectedViews[i];
                    id = Ext.id();
                    hiddenFields.push(id);
                    this.nameFormPanel.add({xtype: 'hidden', id: id, name: 'views', value: view.data.id});
                }

                if (this.selectedViewsAll) {
                    params.viewsAll = true;
                }
            }
            if(this.pageOptions[8].active){
                for(i = 0; i < this.selectedReports.length; i++){
                    var report = this.selectedReports[i];
                    id = Ext.id();
                    hiddenFields.push(id);
                    this.nameFormPanel.add({xtype: 'hidden', id: id, name: 'reports', value: report.data.id});
                }

                if (this.selectedReportsAll) {
                    params.reportsAll = true;
                }
            }


        }


        this.nameFormPanel.doLayout();

        if(!this.allowRefresh)
        {
            params.update = false;
        }
        else{
            var form = this.snapshotOptions.getForm();
            var refreshOptions = form.getValues();

            if (refreshOptions.refreshType == 'None') {
                params.update = false;
            }
            else if (refreshOptions.refreshType == 'Automatic') {
                params.updateDelay = 30;
                params.update = true;
            }
            else if (refreshOptions.refreshType == 'Manual') {
                params.update = true;
            }
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
