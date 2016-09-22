/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.vis.DeveloperOptionsPanel', {

    extend : 'LABKEY.vis.GenericOptionsPanel',

    isDeveloper: false,
    defaultPointClickFn : null,
    pointClickFnHelp : null,
    showButtons: false,

    constructor : function(config)
    {
        this.callParent([config]);

        this.addEvents(
            'chartDefinitionChanged',
            'closeOptionsWindow'
        );
    },

    initComponent : function(){
        this.id = 'developerPanel-' + Ext4.id();

        this.fnErrorDiv = 'error-' + Ext4.id();
        this.pointClickFnDesc = Ext4.create('Ext.container.Container', {
            width: 670,
            html: 'A developer can provide a JavaScript function that will be called when a data point in the chart is clicked. '
                    + 'See the "Help" tab for more information on the parameters available to the function.'
                    + '<br/><div id="' + this.fnErrorDiv + '">&nbsp;</div>'
        });

        this.pointClickFnBtn = Ext4.create('Ext.Button', {
            text: this.pointClickFn ? 'Disable' : 'Enable',
            handler: this.togglePointClickFn,
            scope: this
        });

        this.pointClickTextAreaId = 'textarea-' + Ext4.id();
        this.pointClickTextAreaHtml = Ext4.create('Ext.Panel', {
            height: 280,
            border: false,
            disabled: this.pointClickFn == null,                    // name is for selenium testing
            html: '<textarea id="' + this.pointClickTextAreaId + '" name="point-click-fn-textarea" '
                    + 'wrap="on" rows="21" cols="120" style="width: 100%;"></textarea>',
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
                            indentUnit      : 3
                        });

                        this.codeMirror.setSize(null, size.height + 'px');
                        this.codeMirror.setValue(this.pointClickFn ? this.pointClickFn : '');
                        LABKEY.codemirror.RegisterEditorInstance('point-click-fn-textarea', this.codeMirror);
                    }
                },
                scope: this
            }
        });

        this.pointClickFnTabPanel = Ext4.create('Ext.tab.Panel', {
            items: [
                Ext4.create('Ext.Panel', {
                    title: 'Source',
                    layout: 'fit',
                    items: this.pointClickTextAreaHtml
                }),
                Ext4.create('Ext.Panel', {
                    title: 'Help',
                    height: 280,
                    autoScroll: true,
                    items: [{
                        xtype: 'panel',
                        border: false,
                        padding: 10,
                        html: this.getPointClickFnHelp()
                    }]
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

        if (this.showButtons)
        {
            this.buttons = [{
                text: 'OK',
                handler: this.applyChangesButtonClicked,
                scope: this
            }, {
                text: 'Cancel',
                handler: this.cancelChangesButtonClicked,
                scope: this
            }];
        }

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
    },

    setEditorEnabled: function(editorValue) {
        this.pointClickFn = editorValue;
        this.pointClickTextAreaHtml.enable();
        if (this.codeMirror)
            this.codeMirror.setValue(editorValue);
        this.pointClickFnBtn.setText('Disable');
    },

    setEditorDisabled: function() {
        if (Ext4.getDom(this.fnErrorDiv) != null)
            Ext4.getDom(this.fnErrorDiv).innerHTML = '&nbsp;';

        this.pointClickFn = null;
        if (this.codeMirror)
            this.codeMirror.setValue('');
        this.pointClickTextAreaHtml.disable();
        this.pointClickFnBtn.setText('Enable');
    },

    getDefaultPointClickFn: function() {
        return this.defaultPointClickFn != null ? this.defaultPointClickFn : "function () {\n\n}";
    },

    removeLeadingComments: function(fnText) {
        // issue 15679: allow comments before function definition
        fnText = fnText.trim();
        while (fnText.indexOf("//") == 0 || fnText.indexOf("/*") == 0)
        {
            if (fnText.indexOf("//") == 0)
            {
                var endLineIndex = fnText.indexOf("\n");
                fnText = endLineIndex > -1 ? fnText.substring(endLineIndex + 1).trim() : '';
            }
            else if (fnText.indexOf("*/") > -1)
            {
                fnText = fnText.substring(fnText.indexOf("*/") + 2).trim()
            }
            else
            {
                break;
            }
        }

        return fnText;
    },

    getPointClickFnHelp: function() {
        return this.pointClickFnHelp != null ? this.pointClickFnHelp : "";
    },

    applyChangesButtonClicked: function() {

        if (!this.pointClickTextAreaHtml.isDisabled())
        {
            // true param to verify the pointClickFn for JS errors
            this.pointClickFn = this.getPointClickFnValue(true);
            if (this.pointClickFn == null)
                return; // don't close window as there was a validation error
        }

        this.fireEvent('closeOptionsWindow', false);
        this.checkForChangesAndFireEvents();
    },

    cancelChangesButtonClicked: function(){
        this.fireEvent('closeOptionsWindow', true);
    },

    getPointClickFnValue : function(validate)
    {
        if (validate)
        {
            var fnText = this.codeMirror.getValue();
            fnText = this.removeLeadingComments(fnText);

            if (fnText == null || fnText.length == 0 || fnText.indexOf("function") != 0)
            {
                Ext4.getDom(this.fnErrorDiv).innerHTML = '<span class="labkey-error">Error: the value provided does not begin with a function declaration.</span>';
                return null;
            }

            try
            {
                var verifyFn = new Function("", "return " + fnText);
            }
            catch(err)
            {
                Ext4.getDom(this.fnErrorDiv).innerHTML = '<span class="labkey-error">Error parsing the function: ' + err.message + '</span>';
                return null;
            }

            Ext4.getDom(this.fnErrorDiv).innerHTML = '&nbsp;';
        }

        if (this.codeMirror)
            return this.codeMirror.getValue();
        else
            return this.pointClickFn;
    },

    getPanelOptionValues : function() {
        return {
            pointClickFn: !this.pointClickTextAreaHtml.isDisabled()
                    // TODO the removeLeadingComments should only be applied when the function is being used, not here
                    ? this.removeLeadingComments(this.getPointClickFnValue(false))
                    : null
        };
    },

    validateChanges : function()
    {
        return this.pointClickTextAreaHtml.isDisabled() || this.getPointClickFnValue(true) != null;
    },

    restoreValues : function(initValues) {
        if (initValues && initValues.hasOwnProperty("pointClickFn"))
        {
            if (initValues.pointClickFn != null)
                this.setEditorEnabled(initValues.pointClickFn);
            else
                this.setEditorDisabled();
        }
    },

    setPanelOptionValues: function(config){
        this.restoreValues(config);
    },

    checkForChangesAndFireEvents : function() {
        this.fireEvent('chartDefinitionChanged', false);
    }
});
