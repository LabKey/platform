/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.DeveloperOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    constructor : function(config){

        Ext4.applyIf(config, {
            defaultPointClickFn: null,
            pointClickFnHelp: null
        });

        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged',
            'closeOptionsWindow'
        );
    },

    initComponent : function(){
        this.id = 'developerPanel-' + Ext4.id();

        // track if the panel has changed
        this.hasChanges = false;

        this.fnErrorDiv = 'error-' + Ext4.id();
        this.pointClickFnDesc = Ext4.create('Ext.container.Container', {
            width: 675,
            autoEl: {
                tag: 'span',
                html: 'A developer can provide a JavaScript function that will be called when a data point in the chart is clicked. '
                    + 'See the "Help" tab for more information on the parameters available to the function.'
                    + '<br/><div id="' + this.fnErrorDiv + '">&nbsp;</div>'
            }
        });

        this.pointClickFnBtn = Ext4.create('Ext.Button', {
            text: this.pointClickFn ? 'Disable' : 'Enable',
            handler: this.togglePointClickFn,
            scope: this
        });

        this.pointClickTextAreaId = 'textarea-' + Ext4.id();
        this.pointClickTextAreaHtml = Ext4.create('Ext.Panel', {
            border: false,
            disabled: this.pointClickFn == null,                    // name is for selenium testing
            html: '<textarea id="' + this.pointClickTextAreaId + '" name="point-click-fn-textarea" onchange="Ext4.ComponentManager.get(\'' + this.getId() + '\').hasChanges = true;"'
                    + 'wrap="on" rows="23" cols="120" style="width: 100%;"></textarea>',
            listeners: {
                afterrender: function(cmp) {
                    var code = Ext4.get(this.pointClickTextAreaId);
                    var size = cmp.getSize();

                    if (code) {

                        var me = this;
                        this.codeMirror = CodeMirror.fromTextArea(code.dom, {
                            mode            : 'text/javascript',
                            lineNumbers     : true,
                            lineWrapping    : true,
                            indentUnit      : 3,
                            onChange : function(cmp){
                                Ext4.ComponentManager.get(me.getId()).hasChanges = true;
                            }
                        });

                        this.codeMirror.setSize(null, size.height + 'px');
                        this.codeMirror.setValue(this.pointClickFn ? this.pointClickFn : '');
                    }

                },
                scope: this
            }
        });

        this.pointClickFnTabPanel = Ext4.create('Ext.tab.Panel', {
            height: 400,
            items: [
                Ext4.create('Ext.Panel', {
                    title: 'Source',
                    width: 600,
                    layout: 'fit',
                    items: this.pointClickTextAreaHtml
                }),
                Ext4.create('Ext.Panel', {
                    title: 'Help',
                    width: 600,
                    padding: 5,
                    html: this.getPointClickFnHelp()
                })
            ]
        });

        if (this.isDeveloper)
        {
            this.items = [
                {
                    xtype: 'fieldcontainer',
                    layout: 'hbox',
                    anchor: '100%',
                    hideLabel: true,
                    items: [
                        this.pointClickFnDesc,
                        this.pointClickFnBtn
                    ]
                },
                this.pointClickFnTabPanel
            ];
        }

        this.buttons = [{
            text: 'OK',
            handler: this.applyChangesButtonClicked,
            scope: this
        },{
            text: 'Cancel',
            handler: this.cancelChangesButtonClicked,
            scope: this
        }];

        this.callParent();
    },

    togglePointClickFn: function() {
        if (this.pointClickTextAreaHtml.isDisabled())
        {
            this.setEditorEnabled(this.getDefaultPointClickFn());
        }
        else
        {
            Ext4.Msg.show({
                title:'Confirmation...',
                msg: 'Disabling this feature will delete any code that you have provided. Would you like to proceed?',
                buttons: Ext4.Msg.YESNO,
                fn: function(btnId, text, opt){
                    if(btnId == 'yes'){
                        this.setEditorDisabled();
                    }
                },
                icon: Ext4.MessageBox.QUESTION,
                scope: this
            });
        }
        this.hasChanges = true;
    },

    setEditorEnabled: function(editorValue) {
        this.pointClickFn = editorValue;
        this.pointClickTextAreaHtml.enable();
        this.codeMirror.setValue(editorValue);
        this.pointClickFnBtn.setText('Disable');
    },

    setEditorDisabled: function() {
        if (Ext4.getDom(this.fnErrorDiv) != null)
            Ext4.getDom(this.fnErrorDiv).innerHTML = '&nbsp;';

        this.pointClickFn = null;
        this.codeMirror.setValue('');
        this.pointClickTextAreaHtml.disable();
        this.pointClickFnBtn.setText('Enable');
    },

    setDefaultPointClickFn: function(defaultPointClickFn) {
        this.defaultPointClickFn = defaultPointClickFn;
    },

    getDefaultPointClickFn: function() {
        return this.defaultPointClickFn != null ? this.defaultPointClickFn : "function () {\n\n}";
    },

    removeLeadingComments: function(fnText) {
        // issue 15679: allow comments before function definition
        fnText = fnText.trim();
        while (fnText.indexOf("//") == 0 || fnText.indexOf("/*") == 0)
        {
            var start = 0;
            if (fnText.indexOf("//") == 0)
                start = fnText.indexOf("\n") + 1;
            else
                start = fnText.indexOf("*/") + 2;
            fnText = fnText.substring(start).trim();
        }
        return fnText;
    },

    setPointClickFnHelp: function(pointClickFnHelp) {
        this.pointClickFnHelp = pointClickFnHelp;
    },

    getPointClickFnHelp: function() {
        return this.pointClickFnHelp != null ? this.pointClickFnHelp : "";
    },

    applyChangesButtonClicked: function() {
        // verify the pointClickFn for JS errors
        if (!this.pointClickTextAreaHtml.isDisabled())
        {
            var fnText = this.codeMirror.getValue();
            fnText = this.removeLeadingComments(fnText);

            if (fnText == null || fnText.length == 0 || fnText.indexOf("function") != 0)
            {
                Ext4.getDom(this.fnErrorDiv).innerHTML = '<span class="labkey-error">Error: the value provided does not begin with a function declaration.</span>';
                return;
            }

            try
            {
                var verifyFn = new Function("", "return " + fnText);
            }
            catch(err)
            {
                console.error(err.message);
                Ext4.getDom(this.fnErrorDiv).innerHTML = '<span class="labkey-error">Error parsing the function: ' + err.message + '</span>';
                return;
            }
            Ext4.getDom(this.fnErrorDiv).innerHTML = '&nbsp;';
            this.pointClickFn = this.codeMirror.getValue();
        }

        this.fireEvent('closeOptionsWindow', false);
        this.checkForChangesAndFireEvents();
    },

    cancelChangesButtonClicked: function(){
        this.fireEvent('closeOptionsWindow', true);
    },

    getPanelOptionValues : function() {
        return {pointClickFn: !this.pointClickTextAreaHtml.isDisabled() ? this.pointClickFn : null};
    },

    restoreValues : function(initValues) {
        if (initValues.hasOwnProperty("pointClickFn"))
        {
            if (initValues.pointClickFn != null)
                this.setEditorEnabled(initValues.pointClickFn);
            else
                this.setEditorDisabled();
        }

        this.hasChanges = false;
    },

    setPanelOptionValues: function(config){
        this.suppressEvents = true;
        this.restoreValues(config);
        this.suppressEvents = false;
    },

    checkForChangesAndFireEvents : function() {
        if (this.hasChanges)
            this.fireEvent('chartDefinitionChanged', false);

        // reset the changes flags
        this.hasChanges = false;
    }
});
