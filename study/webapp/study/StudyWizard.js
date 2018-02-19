/*
 * Copyright (c) 2011-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// TODO: I am pretty sure this is stashing the input and output into different variables.
// TODO: factor out EditorGridPanel for regular ext GridPanel (via Nick) -- No reason for editor here?
// We should look to reuse those objects as we are not using the initial values for anything are we?

Ext.namespace("LABKEY.study");

Ext.QuickTips.init();
Ext.GuidedTips.init();

//NOTE: The margin-left 2px which are placed sporadically here fix some rendering on Mac but appears not to be an issue with any other browser...

LABKEY.study.openRepublishStudyWizard = function(snapshotId, availableContainerName, maxAllowedPhi) {
    // get the rest of the study snapshot details/settings from the server

    var sql = "SELECT c.DisplayName as PreviousStudy, u.DisplayName as CreatedBy, ss.Created, ss.Settings, ss.Type " +
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
                mode: row.Type,
                studyName: availableContainerName,
                settings: settings,
                previousStudy: row.PreviousStudy,
                createdBy: row.CreatedBy,
                created: row.Created,
                maxAllowedPhi: maxAllowedPhi
            };

            //issue 22070: specimen study republish needs to include requestId
            if (row.Type == 'specimen')
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
            if (row.Type == undefined && settings.visits == null) {
                config.mode = 'ancillary';
            }

            new LABKEY.study.CreateStudyWizard(config).show();
        },
        failure: function(response) {
            Ext.Msg.alert("Error", response.exception);
        }
    });

};

// NOTE: consider wrapping in Ext.onReady
LABKEY.study.openCreateStudyWizard = function(mode, studyName, maxAllowedPhi) {
    LABKEY.Query.selectRows({
        schemaName: 'study',
        queryName: 'StudySnapshot',
        columns: 'Created,CreatedBy/DisplayName,Destination,Settings',
        filterArray: [LABKEY.Filter.create('Type', mode)],
        success: function(data)
        {
            var config = {
                mode: mode,
                studyName: studyName,
                maxAllowedPhi: maxAllowedPhi
            };

            if (data.rowCount > 0)
            {
                // Issue 25834: check if the user has permission to the destination folder and show the name if possible
                var containerIds = [];
                Ext.each(data.rows, function(row)
                {
                    if (row.Destination != null)
                        containerIds.push(row.Destination);
                });

                LABKEY.Security.getContainers({
                    container: containerIds,
                    includeEffectivePermissions: true,
                    success: function(info)
                    {
                        // only keep track of the id->name mapping for containers the user has permissions to
                        var containerNameMap = {};
                        Ext.each(info.containers, function(container)
                        {
                            if (container.userPermissions > 0)
                                containerNameMap[container.id] = container.name;
                        });

                        // populate the previous studies, mapping the container name where applicable
                        var previousStudies = [];
                        Ext.each(data.rows, function(row)
                        {
                            row.Destination = containerNameMap[row.Destination] || null;
                            if (row.Destination) {
                                previousStudies.push(row);
                            }
                        });

                        if (previousStudies.length > 0) {
                            config.previousStudies = previousStudies;
                        }

                        var wizard = new LABKEY.study.CreateStudyWizard(config);
                        wizard.show();
                    }
                });
            }
            else
            {
                var wizard = new LABKEY.study.CreateStudyWizard(config);
                wizard.show();
            }
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
        this.pageOptions = this.initPageOptions();
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
        if (arr) {
            for (var i = 0; i < arr.length; i++) {
                setObj[arr[i]] = true;
            }
        }
        return setObj;
    },

    initPageOptions : function(){
        var pages = {
            previousSettings: {
                panelType: 'previousSettings',
                active: this.previousStudies ? true : false
            },
            name: {
                panelType: 'name',
                active: this.mode == 'publish' || this.mode == 'ancillary' || this.mode == 'specimen'
            },
            participants: {
                panelType: 'participants',
                active: this.mode == 'publish' || this.mode == 'ancillary'
            },
            datasets: {
                panelType: 'datasets',
                active: this.mode == 'publish' || this.mode == 'ancillary' || this.mode == 'specimen'
            },
            visits: {
                panelType: 'visits',
                active: this.mode == 'publish' && this.studyType != 'CONTINUOUS'
            },
            specimens: {
                panelType: 'specimens',
                active: this.mode == 'publish'
            },
            studyProps: {
                panelType: 'studyProps',
                active: this.mode == 'publish'
            },
            lists: {
                panelType: 'lists',
                active: this.mode == 'publish'
            },
            views: {
                panelType: 'views',
                active: this.mode == 'publish'
            },
            reports: {
                panelType: 'reports',
                active: this.mode == 'publish'
            },
            folderProps: {
                panelType: 'folderProps',
                active: this.mode == 'publish'
            },
            publishOptions: {
                panelType: 'publishOptions',
                active: this.mode == 'publish'
            }
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

                    var folderWritersToExclude = ['Grid Views', 'Lists', 'Notification Settings', 'Queries', 'Reports and Charts', 'Study', 'Experiments and runs'];
                    var studyWritersToExclude = ['Assay Datasets', 'Categories', 'CRF Datasets', 'Participant Groups', 'QC State Settings', 'Specimens', 'Visit Map'];
                    this.studyWriters = [];
                    this.folderWriters = [];

                    // Issue 30656: hide/disable certain publish options for Dataspace folder type
                    if (LABKEY.container.folderType == "Dataspace") {
                        folderWritersToExclude.push('Folder type and active modules');
                    }

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
        if(this.pageOptions.previousSettings.active) this.steps.push(this.getPreviousSettingsPanel());

        if(this.pageOptions.name.active) this.steps.push(this.getNamePanel());
        if(this.pageOptions.participants.active) this.steps.push(this.getParticipantsPanel());
        if(this.pageOptions.datasets.active) this.steps.push(this.getDatasetsPanel());

        if(this.pageOptions.visits.active) this.steps.push(this.getVisitsPanel());
        if(this.pageOptions.specimens.active) this.steps.push(this.getSpecimensPanel());
        if(this.pageOptions.studyProps.active) this.steps.push(this.getStudyPropsPanel());
        if(this.pageOptions.lists.active) this.steps.push(this.getListsPanel());
        if(this.pageOptions.views.active) this.steps.push(this.getViewsPanel());
        if(this.pageOptions.reports.active) this.steps.push(this.getReportsPanel());
        if(this.pageOptions.folderProps.active) this.steps.push(this.getFolderPropsPanel());
        if(this.pageOptions.publishOptions.active) this.steps.push(this.getPublishOptionsPanel());

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
            bodyStyle: 'padding: 25px;',
            flex: 1,
            items: this.steps,
            bbar: ['->', this.prevBtn, this.nextBtn]
        });

        // TODO: look at merging this with pageOptions (not sure why these aren't the same object)
        var setup = {
            previousSettings: {value: 'Previous Settings', currentStep: this.previousStudies ? true : false},
            name: {value: 'General Setup', currentStep: this.previousStudies ? false : true},
            participants:  {value: this.pageOptions.participants.active ? this.subject.nounPlural : 'Participants', currentStep: false},
            datasets: {value: 'Datasets', currentStep: false},
            visits: {value: this.studyType == "VISIT" ? 'Visits' : 'Timepoints', currentStep: false},
            specimens: {value: 'Specimens', currentStep: false},
            studyProps: {value: 'Study Objects', currentStep: false},
            lists: {value: 'Lists', currentStep: false},
            views: {value: 'Grid Views', currentStep: false},
            reports: {value: 'Reports and Charts', currentStep: false},
            folderProps: {value: 'Folder Objects', currentStep: false},
            publishOptions: {value: 'Publish Options', currentStep: false}
        };

        var steps = [];
        Ext.iterate(this.pageOptions, function(name, pageOption){
            if(pageOption.active) steps.push(setup[name]);
        });

        this.sideBar = new Ext.Panel({
            name: 'sidebar',
            width: 210,
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
            height: 630,
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
        else if(this.steps[this.currentStep].name == 'General Setup')
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

    getPreviousSettingsPanel : function() {
        var message;
        if (this.mode == 'ancillary')
            message = "This study was previously used to create an ancillary study. You can republish (populating the wizard with the previous settings) or create a new ancillary study with no default settings.";
        else
            message = "This study was published previously. You can republish (populating the wizard with the previous settings) or publish the new study with no default settings.";

        (this.mode == 'ancillary' ? 'ancillary' : 'published')

        var headerTxt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Previous Settings'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'}) +
                Ext.DomHelper.markup({tag:'div', html:message}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});

        var grid = new Ext.grid.GridPanel({
            columns: [
                {dataIndex: 'Created', header: '&nbsp;Created', width: 100, sortable: true,
                 renderer: Ext.util.Format.dateRenderer(LABKEY.extDefaultDateFormat) },
                {dataIndex: 'CreatedBy/DisplayName', header: '&nbsp;Created By', width: 100, sortable: true},
                {dataIndex: 'Destination', header: '&nbsp;Destination', width: 200, sortable: true},
                {dataIndex: 'RowId', hidden: true},
                {dataIndex: 'Settings', hidden: true}
            ],
            selModel: new Ext.grid.RowSelectionModel({singleSelect:true}),
            store: new Ext.data.JsonStore({
                fields: ['Created', 'CreatedBy/DisplayName', 'Destination', 'RowId', 'Settings'],
                data: this.previousStudies,
                sortInfo: {
                    field: 'Created',
                    direction: 'DESC'
                }
            }),
            viewConfig: {forceFit: true},
            loadMask: {msg: "Loading, please wait..."},
            enableFilters: true,
            stripeRows: true,
            pageSize: 300000,
            flex: 1,
            style: {
                paddingLeft: '22px',
                paddingBottom: '10px'
            }
        });
        grid.initialValue = null; // used for dirty logic

        var republishRadio = new Ext.form.Radio({
            height: 30,
            style: 'margin-left: 2px',
            boxLabel: (this.mode == 'publish' ? 'Republish' : 'Recreate anciliary study') + ' starting with the settings from a previous publication',
            name: 'publishType',
            checked: true,
            inputValue: 'republish',
            listeners: {
                check: function(radio, checked){
                    (!checked) ? grid.disable() : grid.enable();
                    grid.getSelectionModel().clearSelections();
                }
            }
        });

        var publishRadio = new Ext.form.Radio({
            height: 30,
            style: 'margin-left: 2px',
            boxLabel: (this.mode == 'publish' ? 'Publish new study' : 'Create anciliary study') +  ' from scratch',
            name: 'publishType',
            inputValue: 'publish'
        });

        var panel = new Ext.Panel({
            border: false,
            name:  'Previous Settings',
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: [
                {xtype:'displayfield', html: headerTxt},
                republishRadio,
                grid,
                publishRadio
            ]
        });

        panel.on('beforehide', function(cmp){
            if (republishRadio.getValue())
            {
                if (!grid.getSelectionModel().getSelected())
                {
                    Ext.MessageBox.alert('Error', 'If republishing a study, please select a study to republish.');
                    this.currentStep--;
                    this.updateStep();
                    return false;
                }

                var data = grid.getSelectionModel().getSelected().data;
                var settings = Ext.decode(data.Settings);
                this.settings = settings;
            }
            else
                this.settings = null;

            if (republishRadio.isDirty() || grid.initialValue != grid.getSelectionModel().getSelected()) {
                grid.initialValue = grid.getSelectionModel().getSelected();
                republishRadio.originalValue = republishRadio.getValue();
                this.fireEvent('settingsChange');
            }

            return true;

        }, this);

        return panel;
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
            listeners: {
                "fileselected": function (fb, v) {
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
            cls : 'extContainer folder-management-tree', // used by selenium helper
            header:false,
            style: "margin-left: 130px;", //Line up the folder tree with the rest of the form elements.
            rootVisible: true,
            enableDD: false,
            animate : true,
            useArrows : true,
            height: this.settings ? 130 : 240,
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
                fieldLabel: 'Previous Child Study',
                value: this.previousStudy
            },{
                xtype: 'displayfield',
                fieldLabel: 'Created By',
                value: this.createdBy
            },
            {
                xtype: 'displayfield',
                fieldLabel: 'Created',
                value: new Date(this.created).format(LABKEY.extDefaultDateFormat)
            });

            formItems.unshift({
                xtype: 'box',
                width: 600,
                html: 'Since you are republishing a study, settings in this wizard are preset to the values selected when the previous '
                      + (this.mode == 'publish' ? 'published' : this.mode) + ' study was created.',
                style: 'margin-bottom: 15px;'
            });
        }

        formItems.push({
            xtype: 'textfield',
            fieldLabel: 'Name',
            autoCreate: {tag: 'input', type: 'text', size: '20', autocomplete: 'off', maxlength: '255'},
            allowBlank: false,
            name: 'studyName',
            value: this.info.name,
            enableKeyEvents: true,
            listeners: {change: nameChange, keyup:nameChange, scope:this}
        });

        var descriptionTextArea = new Ext.form.TextArea({
            fieldLabel: 'Description',
            name: 'studyDescription',
            height: '100',
            emptyText: 'Type Description here',
            listeners: {
                change:function(cmp, newValue, oldValue) {this.info.description = newValue;},
                scope:this
            }
        });

        var afterRenderFunc = function() {
            if(this.settings)
            {
                descriptionTextArea.setValue(this.settings.description);
                this.info.description = this.settings.description;
            }
        };

        if (!this.previousStudies) // only needed when not in republish extended wizard
            descriptionTextArea.on('afterrender', afterRenderFunc, this);

        formItems.push(descriptionTextArea);

        formItems.push([
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
            border: false,
            name: "General Setup",
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });

        this.on('settingsChange', afterRenderFunc, this);

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

        var radioGroup = new Ext.form.RadioGroup({
            columns: 2,
            fieldLabel: '',
            style: 'padding-bottom: 10px;',
            labelWidth: 5,
            items: [
                this.existingGroupRadio,
                this.noGroupRadio
            ]
        });

        var afterRenderFunc = function() {
            if (this.settings) {
                if (this.settings.participants == null)
                    radioGroup.setValue('all');
                else
                    radioGroup.setValue('existing');
            }
        };

        radioGroup.on('afterrender', afterRenderFunc, this);

        items.push(radioGroup);

        var participantGroupStore = new Ext.data.JsonStore({
            proxy: new Ext.data.HttpProxy({
                url : LABKEY.ActionURL.buildURL("participant-group", "browseParticipantGroups", null, {
                    // the ImmPort module "Data Finder" creates private groups, so include them if that module is enabled
                    includePrivateGroups : LABKEY.container.activeModules.indexOf('ImmPort') > -1
                }),
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
                load: function(store, records){
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
                {header:'&nbsp;&nbsp;' + this.subject.nounSingular + ' Group', dataIndex:'label', renderer: this.participantGroupRenderer, scope: this}
            ]
        });

        var viewReadyFunc = function(){
            grid.getSelectionModel().clearSelections();
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
        };

        grid.on('viewready', viewReadyFunc, this);

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

        this.participantPanel.on('afterrender', function() {
            // 22656: Going back to "Previous Settings" and selecting a different snapshot doesn't reflect in republish study wizard
            // NOTE: this is wired up on the afterrender such that it doesn't fire the first time the component shows. The first showing is handled by viewready on the grid.
            this.on('settingsChange', viewReadyFunc, this);
            this.on('settingsChange', afterRenderFunc, this);
        }, this);


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
        this.pageOptions.participants.value = this.selectedParticipantGroups;
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
                columns: 'datasetId, name, label, category, description',
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
                columns: 'datasetId, name, label, category, description',
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
            style: 'padding-top: 10px;',
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

        grid.on('columnmodelcustomize', this.customizeColumnModel, this);
        grid.selModel.on('selectionchange', function(cmp){this.info.datasets = cmp.getSelections();}, this);
        hiddenGrid.on('columnmodelcustomize', this.customizeColumnModel, this);
        hiddenGrid.selModel.on('selectionchange', function(cmp){this.info.hiddenDatasets = cmp.getSelections();}, this);

        var viewReadyFunc = function(grid){
            return function()
            {
                grid.getSelectionModel().clearSelections();
                if (this.settings)
                {
                    if (!this.settings.datasets)
                    {
                        this.gridSelectAll(grid);
                    }
                    else
                    {
                        var datasets = this.setify(this.settings.datasets);
                        grid.getSelectionModel().selectRecords(grid.store.queryBy(function (rec)
                        {
                            return (rec.get('DataSetId') in datasets);
                        }).getRange(), true);
                    }
                }
            }
        };

        grid.on('viewready', viewReadyFunc(grid), this);
        hiddenGrid.on('viewready', viewReadyFunc(hiddenGrid), this);

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

        var radioGroup = new Ext.form.RadioGroup({
            fieldLabel: 'Data Refresh',
            gtip : syncTip,
            columns: 1,
            width: 300,
            defaults: {style:"margin: 0 0 2px 2px"},
            items: [
                {name: 'refreshType', boxLabel: 'None', inputValue: 'None', checked: this.mode != 'ancillary', hidden: this.mode == 'ancillary'},
                {name: 'refreshType', boxLabel: 'Automatic', inputValue: 'Automatic', checked: this.mode == 'ancillary'},
                {name: 'refreshType', boxLabel: 'Manual', inputValue: 'Manual'}
            ]
        });

        var afterRenderFunc = function(cmp) {
            if (this.settings && Ext.isDefined(this.settings.datasetRefresh)) {
                if (!this.settings.datasetRefresh && this.mode != 'ancillary') radioGroup.setValue('None');
                else if (this.settings.datasetRefreshDelay == null) radioGroup.setValue('Manual');
                else radioGroup.setValue('Automatic');
            }
            else
                radioGroup.setValue(this.mode == 'ancillary' ? 'Automatic' : 'None');
        };

        radioGroup.on('afterrender', afterRenderFunc, this);

        if (this.allowRefresh)
        {
            this.snapshotOptions = new Ext.form.FormPanel({
                items: [radioGroup],
                padding: 0,
                border: false,
                height: 85,
                width : 300
            });

            items.push(new Ext.BoxComponent({height: 10})); // spacer
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

        panel.on('afterrender', function() {
            // 22656: Going back to "Previous Settings" and selecting a different snapshot doesn't reflect in republish study wizard
            // NOTE: this is wired up on the afterrender such that it doesn't fire the first time the component shows. The first showing is handled by viewready on the grid.
            this.on('settingsChange', viewReadyFunc(grid), this);
            this.on('settingsChange', viewReadyFunc(hiddenGrid), this);
            if(this.allowRefresh)
                this.on('settingsChange', afterRenderFunc, this);
        }, this);
        
        return panel;
    },

    getViewsPanel: function(){
        this.selectedViews = [];
        var items = [];
        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Grid Views'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'}) +
                Ext.DomHelper.markup({tag:'div', html:'Choose the grid views you would like to publish:'}) +
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
            viewConfig: {forceFit: true},
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
            bbarCfg: [{hidden:true}],
            tbarCfg: [{hidden:true}]
        });

        items.push(grid);

        var viewReadyFunc = function() {
            grid.getSelectionModel().clearSelections();
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
        };

        grid.on('viewready', viewReadyFunc, this);

        var panel = new Ext.Panel({
            border: false,
            name: "Grid Views",
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });

        panel.on('afterrender', function(cmp) {
            // 22656: Going back to "Previous Settings" and selecting a different snapshot doesn't reflect in republish study wizard
            // NOTE: this is wired up on the afterrender such that it doesn't fire the first time the component shows. The first showing is handled by viewready on the grid.
            this.on('settingsChange', viewReadyFunc, this);
        }, this);

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
        this.pageOptions.views.value = this.selectedViews;
        return panel;
    },

    getStudyPropsPanel : function(){
        var items = [];

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Study Objects'})+
            Ext.DomHelper.markup({tag:'div', html: '&nbsp'})+
            Ext.DomHelper.markup({tag:'div', html: 'Choose additional study objects to publish:'})+
            Ext.DomHelper.markup({tag:'div', html: '&nbsp'});

        items.push({xtype:'displayfield', html: txt});

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

        var grid = new Ext.grid.EditorGridPanel({
            cls : 'studyObjects',
            store: studyStore,
            selModel: selectionModel,
            viewConfig: {forceFit: true},
            columns: [
                selectionModel,
                {header: 'Name', sortable: true, dataIndex: 'name'}
            ],
            loadMask:{msg:"Loading, please wait..."},
            editable: false,
            stripeRows: true,
            pageSize: 300000,
            flex: 1,
            bbarCfg: [{hidden:true}],
            tbarCfg: [{hidden:true}]
        });

        items.push(grid);

        var viewReadyFunc = function(){
            grid.getSelectionModel().clearSelections();
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
        };

        grid.on('viewready', viewReadyFunc, this);

        var panel = new Ext.Panel({
            border: false,
            name : 'Study Objects',
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });

        panel.on('afterrender', function(cmp) {
            // 22656: Going back to "Previous Settings" and selecting a different snapshot doesn't reflect in republish study wizard
            // NOTE: this is wired up on the afterrender such that it doesn't fire the first time the component shows. The first showing is handled by viewready on the grid.
            this.on('settingsChange', viewReadyFunc, this);
        }, this);

        return panel;
    },

    getReportsPanel: function(){
        this.selectedReports = [];
        
        var items = [];

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Reports and Charts'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'}) +
                Ext.DomHelper.markup({tag:'div', html:'Choose the reports and charts you would like to publish:'}) +
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
                    {name : 'iconCls'},
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
            viewConfig : {forceFit : true},
            store: reportsStore,
            selModel: selectionModel,
            columns: [
                selectionModel,
                {header: 'Name', width: 200, sortable: true, dataIndex: 'name', renderer: Ext.util.Format.htmlEncode},
                {header: 'Category', width: 120, sortable: true, dataIndex:'category', renderer: Ext.util.Format.htmlEncode},
                {header: 'Type', width: 60, sortable: true, dataIndex: 'type', renderer : function(v, meta, rec) {
                    var imgText;
                    if (rec.data.iconCls) {
                        imgText = '<div class="' + rec.data.iconCls + '"/>';
                    }
                    else {
                        imgText = '<img title="' + rec.data.type + '" src="' + rec.data.icon + '" alt="' + rec.data.type +'" height="16px" width="16px">';
                    }
                    return '<div style="margin-left: auto; margin-right: auto; width: 30px; vertical-align: middle;">' +
                                imgText +'</div>';
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
            bbarCfg: [{hidden:true}],
            tbarCfg: [{hidden:true}]
        });

        items.push(grid);

        var viewReadyFunc = function(){
            grid.getSelectionModel().clearSelections();
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
        };

        grid.on('viewready', viewReadyFunc, this);

        this.pageOptions.reports.value = this.selectedReports;

        var panel = new Ext.Panel({
            border: false,
            name: "Reports and Charts",
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });

        panel.on('afterrender', function(cmp) {
            // 22656: Going back to "Previous Settings" and selecting a different snapshot doesn't reflect in republish study wizard
            // NOTE: this is wired up on the afterrender such that it doesn't fire the first time the component shows. The first showing is handled by viewready on the grid.
            this.on('settingsChange', viewReadyFunc, this);
        }, this);

        return panel;
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

        var afterRenderFuncCb = function(cb) {
            if (this.settings)
                this.includeSpecimensCheckBox.setValue(this.settings.includeSpecimens);
            else
                this.includeSpecimensCheckBox.setValue(true);
        }

        this.includeSpecimensCheckBox.on('afterrender', afterRenderFuncCb, this);

        this.specimenRefreshRadioGroup = new Ext.form.RadioGroup({
            xtype: 'radiogroup',
            fieldLabel: 'Refresh rate:',
            columns: 1,
            items: [
                {boxLabel: 'One-time snapshot', name: 'specimenRefresh', inputValue: 'false', checked: true, style:"margin-left: 2px"},
                {boxLabel: 'Nightly refresh', name: 'specimenRefresh', inputValue: 'true', style:"margin-left: 2px"}
            ]
        });

        var afterRenderFunc = function() {
            if (this.settings)
                this.specimenRefreshRadioGroup.setValue(this.settings.specimenRefresh.toString());
            else
                this.specimenRefreshRadioGroup.setValue(false);
        };

        this.specimenRefreshRadioGroup.on('afterrender', afterRenderFunc, this);

        var optionsPanel = new Ext.form.FormPanel({
            defaults: {labelSeparator: ''},
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

        var panel = new Ext.Panel({
            border: false,
            name: "Specimens",
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });

        panel.on('afterrender', function(cmp) {
            // 22656: Going back to "Previous Settings" and selecting a different snapshot doesn't reflect in republish study wizard
            // NOTE: this is wired up on the afterrender such that it doesn't fire the first time the component shows. The first showing is handled by viewready on the grid.
            this.on('settingsChange', afterRenderFuncCb, this);
            this.on('settingsChange', afterRenderFunc, this);
        }, this);

        return panel;
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
                columns: 'RowId, Label, SequenceNumMin, SequenceNumMax, DisplayOrder, Folder',
                sort: 'DisplayOrder, SequenceNumMin'
            }),
            loadMask:{msg:"Loading, please wait..."},
            enableFilters: true,
            editable: false,
            stripeRows: true,
            pageSize: 300000,
            cls: 'studyWizardVisitList',
            flex: 1,
            bbarCfg: [{hidden:true}],
            tbarCfg: [{hidden:true}]
        });

        items.push(grid);

        grid.on('columnmodelcustomize', this.customizeVisitColumnModel, this);
        grid.selModel.on('selectionchange', function(cmp){this.selectedVisits = cmp.getSelections();}, this);

        var viewReadyFunc = function(){
            grid.getSelectionModel().clearSelections();
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
        };

        grid.on('viewready', viewReadyFunc, this);

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

        panel.on('afterrender', function(cmp) {
            // 22656: Going back to "Previous Settings" and selecting a different snapshot doesn't reflect in republish study wizard
            // NOTE: this is wired up on the afterrender such that it doesn't fire the first time the component shows. The first showing is handled by viewready on the grid.
            this.on('settingsChange', viewReadyFunc, this);
        }, this);

        this.pageOptions.visits.value = this.selectedVisits;
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
            bbarCfg: [{hidden:true}],
            tbarCfg: [{hidden:true}]
        });

        items.push(grid);

        var viewReadyFunc = function(){
            grid.getSelectionModel().clearSelections();
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
        };

        grid.on('viewready', viewReadyFunc, this);

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

        panel.on('afterrender', function(cmp) {
            // 22656: Going back to "Previous Settings" and selecting a different snapshot doesn't reflect in republish study wizard
            // NOTE: this is wired up on the afterrender such that it doesn't fire the first time the component shows. The first showing is handled by viewready on the grid.
            this.on('settingsChange', viewReadyFunc, this);
        }, this);

        this.pageOptions.lists.value = this.selectedLists;
        return panel;
    },

    getFolderPropsPanel : function(){
        var items = [];

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Folder Objects'})+
                Ext.DomHelper.markup({tag:'div', html: '&nbsp'})+
                Ext.DomHelper.markup({tag:'div', html: 'Choose additional folder objects to publish:'})+
                Ext.DomHelper.markup({tag:'div', html: '&nbsp'});

        items.push({xtype:'displayfield', html: txt});

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

        var grid = new Ext.grid.EditorGridPanel({
            cls: 'folderObjects',
            store: folderStore,
            selModel: selectionModel,
            viewConfig: {forceFit: true},
            columns: [
                selectionModel,
                {header: 'Name', sortable: true, dataIndex: 'name', name: 'name'}
            ],
            loadMask:{msg:"Loading, please wait..."},
            editable: false,
            stripeRows: true,
            pageSize: 300000,
            flex: 1,
            bbarCfg: [{hidden:true}],
            tbarCfg: [{hidden:true}]
        });

        items.push(grid);

        var viewReadyFunc = function(){
            grid.getSelectionModel().clearSelections();
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
        };

        grid.on('viewready', viewReadyFunc, this);

        var panel = new Ext.Panel({
            border: false,
            name : 'Folder Objects',
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });

        panel.on('afterrender', function(cmp) {
            // 22656: Going back to "Previous Settings" and selecting a different snapshot doesn't reflect in republish study wizard
            // NOTE: this is wired up on the afterrender such that it doesn't fire the first time the component shows. The first showing is handled by viewready on the grid.
            this.on('settingsChange', viewReadyFunc, this);
        }, this);

        return panel;
    },

    getPublishOptionsPanel: function(){
        var items = [];

        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: 'Publish Options'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'}) +
                Ext.DomHelper.markup({tag:'div', html:'Choose publish options:'}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});

        items.push({xtype:'displayfield', html: txt});

        var selectionModel = new Ext.grid.CheckboxSelectionModel({
            moveEditorOnEnter: false,
            listeners: {
                selectionChange: function(selModel) {
                    this.selectedPublishOptions = selModel.getSelections();
                },
                scope: this
            }
        });

        var availablePublishOptions = [];
        // Issue 30656: hide/disable certain publish options for Dataspace folder type
        if (LABKEY.container.folderType != "Dataspace") {
            availablePublishOptions.push(['Use Alternate ' + this.subject.nounSingular + ' IDs',
                'Replace each ' + this.subject.nounSingular.toLowerCase() + ' id with a randomly generated alternate id',
                'useAlternateParticipantIds']);
            availablePublishOptions.push(['Shift ' + this.subject.nounSingular + ' Dates',
                'Shift date values associated with a ' + this.subject.nounSingular.toLowerCase() + ' by a random, ' + this.subject.nounSingular.toLowerCase() + ' specific, offset (from 1 to 365 days)',
                'shiftDates']);
        }
        availablePublishOptions.push(['Mask Clinic Names',
            'Replace clinic labels with a generic label ("Clinic")',
            'maskClinic']);

        var publishOptionsStore = new Ext.data.ArrayStore({
            fields : [
                {name : 'name', type : 'string'},
                {name : 'description', type : 'string'},
                {name : 'option', type : 'string'}
            ],
            data : availablePublishOptions
        });

        // http://stackoverflow.com/questions/2106104/word-wrap-grid-cells-in-ext-js
        var columnWrap = function(val){
            return '<div style="white-space:normal !important;">'+ val +'</div>';
        };

        var grid = new Ext.grid.EditorGridPanel({
            cls: 'studyWizardPublishOptionsList',
            store: publishOptionsStore,
            selModel: selectionModel,
            viewConfig: {forceFit: true},
            columns: [
                selectionModel,
                {header: 'Name', width: 220, sortable: true, dataIndex: 'name', name: 'name', renderer: columnWrap},
                {header: 'Description', width: 380, sortable: true, dataIndex: 'description', name: 'description', renderer: columnWrap}
            ],
            loadMask: {msg:"Loading, please wait..."},
            editable: false,
            stripeRows: true,
            pageSize: 300000,
            flex: 1,
            bbarCfg: [{hidden:true}],
            tbarCfg: [{hidden:true}]
        });
        items.push(grid);

        if ('NotPHI' !== this.maxAllowedPhi) {
            var phiItems = [];
            if (this.maxAllowedPhi === 'Restricted')
                phiItems.push({boxLabel: 'Restricted, Full and Limited PHI', inputValue: 'Restricted', name: 'exportPhiLevel', checked: true});
            if (this.maxAllowedPhi >= 'PHI')
                phiItems.push({boxLabel: 'Full and Limited PHI', inputValue: 'PHI', name: 'exportPhiLevel', checked: this.maxAllowedPhi === 'PHI'});
            if (this.maxAllowedPhi >= 'Limited')
                phiItems.push({boxLabel: 'Limited PHI', inputValue: 'Limited', name: 'exportPhiLevel', checked: this.maxAllowedPhi === 'Limited'});
            phiItems.push({boxLabel: 'Not PHI', inputValue: 'NotPHI', name: 'exportPhiLevel'});

            this.publishPhiRadioGroup = new Ext.form.RadioGroup({
                columns: 1,
                cls: 'publish-radio-option',
                items: phiItems
            });

            var phiPanel = new Ext.form.FormPanel({
                border: false,
                width: 100,
                height: 150,
                padding: '10px 0 0 0',
                name: 'PHI Options',
                layout: 'auto',
                layoutConfig: {
                    align: 'left'
                },
                items: [
                    new Ext.form.Label({html: 'Include PHI Columns:'}),
                    this.publishPhiRadioGroup
                ]
            });

            items.push(phiPanel);
        }

        var viewReadyFunc = function(){
            grid.getSelectionModel().clearSelections();
            if (this.settings)
            {
                var publishOptions = {};
                publishOptions['maskClinic'] = this.settings.maskClinic;
                publishOptions['shiftDates'] = this.settings.shiftDates;
                publishOptions['useAlternateParticipantIds'] = this.settings.useAlternateParticipantIds;
                if(this.settings.removeProtectedColumns && this.publishPhiRadioGroup)  // may be true for legacy studies, so convert to PHI
                {
                    this.publishPhiRadioGroup.setValue('exportPhiLevel', 'Limited');  // protected bit being set is equal to Limited PHI
                }
                if(this.settings.phiLevel && this.publishPhiRadioGroup)
                    this.publishPhiRadioGroup.setValue('exportPhiLevel', this.settings.phiLevel);
                if (this.settings.maskClinic && this.settings.shiftDates && this.settings.useAlternateParticipantIds)
                    this.gridSelectAll(grid);
                else
                    grid.getSelectionModel().selectRecords(grid.store.queryBy(function(rec) { return publishOptions[rec.get('option')]; }).getRange(), true);
            }
        };

        grid.on('viewready', viewReadyFunc, this);

        var panel = new Ext.Panel({
            border: false,
            name : 'Publish Options',
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });

        panel.on('afterrender', function(cmp) {
            // 22656: Going back to "Previous Settings" and selecting a different snapshot doesn't reflect in republish study wizard
            // NOTE: this is wired up on the afterrender such that it doesn't fire the first time the component shows. The first showing is handled by viewready on the grid.
            this.on('settingsChange', viewReadyFunc, this);
        }, this);

        return panel;
    },

    customizeColumnModel : function(colModel, index, c){
        index.Name.renderer = Ext.util.Format.htmlEncode;
        index.Label.renderer = Ext.util.Format.htmlEncode;
        index.Description.renderer = Ext.util.Format.htmlEncode;

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
            params.exportPhiLevel = this.publishPhiRadioGroup ? this.publishPhiRadioGroup.getValue().inputValue : 'NotPHI';
        }

        var hiddenFields = [];

        if(this.pageOptions.participants.active){
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

        this.pageOptions.visits.value = this.selectedVisits;
        this.pageOptions.lists.value = this.selectedLists;
        this.pageOptions.views.value = this.selectedViews;
        this.pageOptions.reports.value = this.selectedReports;

        if(this.requestId){
            id = Ext.id();
            hiddenFields.push(id);
            this.nameFormPanel.add({xtype: 'hidden', id : id, name : 'requestId', value : this.requestId});
        }

        if (this.pageOptions.studyProps.active && this.selectedStudyObjects)
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
        if (this.pageOptions.folderProps.active && this.selectedFolderObjects)
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

            if(this.pageOptions.visits.active){
                for(i = 0; i < this.selectedVisits.length; i++){
                    var visit = this.selectedVisits[i];
                    id = Ext.id();
                    hiddenFields.push(id);
                    this.nameFormPanel.add({xtype: 'hidden', id: id, name: 'visits', value: visit.data.RowId});
                }
            }
            if(this.pageOptions.lists.active){
                for(i = 0; i < this.selectedLists.length; i++){
                    var list = this.selectedLists[i];
                    id = Ext.id();
                    hiddenFields.push(id);
                    this.nameFormPanel.add({xtype: 'hidden', id: id, name: 'lists', value: list.data.id});
                }
            }
            if(this.pageOptions.views.active){
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
            if(this.pageOptions.reports.active){
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
