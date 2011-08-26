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
            },{
                xtype: 'textfield',
                fieldLabel: 'New Study Location',
                allowBlank: false,
                name: 'studyFolder',
                value: this.info.dstPath,
                listeners: {change:function(cmp, newValue, oldValue) {this.info.dstPath = newValue;}, scope:this}
            }
        ];

        var formPanel = new Ext.form.FormPanel({
            border: false,
            defaults: {
                width: '85%'
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

        var txt = Ext.DomHelper.markup({tag:'div', html:'How will participants be added to this study.<br>'});
        //items.push({xtype:'displayfield', html: txt});

        var formItems = [
            {xtype: 'displayfield', html: txt},
            {
                xtype: 'radiogroup',
                columns: 2,
                fieldLabel: '',
                labelWidth: 5,
                items: [
                    {boxLabel:'Create a new participant group', name: 'renderType', scope: this, handler: this.showNewParticipantGroupPanel},
                    {boxLabel:'Select from existing participant groups', name: 'renderType', checked: true, scope:this,
                        handler: this.showExistingParticipantGroupPanel}
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
            }
            this.newGroupPanel.show();
            this.participantPanel.add(this.newGroupPanel);
        }
        else if (this.participantPanel && this.newGroupPanel)
        {
            this.newGroupPanel.hide();
            this.participantPanel.remove(this.newGroupPanel);
        }
        this.participantPanel.doLayout();
    },

    showExistingParticipantGroupPanel : function(cmp, show)
    {
        if (show && this.participantPanel)
        {
            Ext.Ajax.request(
            {
                url : LABKEY.ActionURL.buildURL("participant-group", "getParticipantCategories"),
                method : 'get',
                success: this.createExistingParticipantGroupPanel,
                failure: function(response, options){LABKEY.Utils.displayAjaxErrorResponse(response, options);},
                scope: this
            });
        }
        else if (this.participantPanel && this.existingGroupPanel)
        {
            this.participantPanel.remove(this.existingGroupPanel);
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

                for (var i=0; i < o.categories.length; i++)
                {
                    var group = o.categories[i];

                    items.push({boxLabel: group.label, name:'participantGroups', value: group.rowId});
                }

                var formItems = [{
                    xtype: 'radiogroup',
                    columns: 1,
                    fieldLabel: 'Existing ' + this.subject.nounSingular + ' groups',
                    items: items
                }];

                this.existingGroupPanel = new Ext.Panel({
                    padding: '20px',
                    border: false,
                    labelWidth: 250,
                    flex: 1,
                    layout: 'form',
                    items: formItems
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

        for (var i=0; i < this.info.datasets.length; i++)
        {
            var ds = this.info.datasets[i];

            params.datasets.push(ds.data.DataSetId);
        }

        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL('study', 'createEmphasisStudy'),
            method : 'POST',
            scope: this,
            success: function(){
                this.fireEvent('success');
                this.win.close();
            },
            failure: function(response, opts){
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
