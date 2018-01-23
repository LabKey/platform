/*
 * Copyright (c) 2010-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.namespace('LABKEY.ext');

/**
 * Constructs a new LabKey ExcelUploadPanel using the supplied configuration.
 * @class LabKey extension to the <a href="http://docs.sencha.com/ext-js/4-0/#!/api/Ext.form.Panel">Ext.form.Panel</a> class,
 * which creates a the UI to upload an excel file with information to the specified table.
 *
 * <p>If you use any of the LabKey APIs that extend Ext APIs, you must either make your code open source or
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=extDevelopment">purchase an Ext license</a>.</p>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeyExt">Tips for Using Ext to Build LabKey Views</a></li>
 *              </ul>
 *           </p>
 * @class LABKEY.ext4.ExcelUploadPanel
 * @constructor
 * @param config Configuration properties.
 * @param {String} config.schemaName The LabKey schema to query.
 * @param {String} config.queryName The query name within the schema to fetch.
 * @param {String} [config.containerPath] The containerPath to use when fetching the query
 * @param {boolean} config.showAlertOnSuccess Defaults to true
 * @param {boolean} config.showAlertOnFailure Defaults to true
 * @param {Integer} config.timeout The timeout in seconds.  Defaults to 60
 * @example &lt;script type="text/javascript"&gt;
    Ext4.onReady(function(){

         Ext4.create('LABKEY.ext4.ExcelUploadPanel', {
             renderTo: 'excelUploadPanel',
             schemaName: schemaName,
             queryName: queryName
         });

    });
&lt;/script&gt;
&lt;div id='excelUploadPanel'/&gt;
 */

Ext4.define('LABKEY.ext4.ExcelUploadPanel', {
    extend: 'Ext.form.Panel',
    alias: 'widget.labkey-exceluploadpanel',
    showAlertOnSuccess: true,
    showAlertOnFailure: true,
    importLookupByAlternateKey: false,
    initComponent: function(){
        Ext4.QuickTips.init();

        Ext4.apply(this, {
            autoHeight: true
            ,url: LABKEY.ActionURL.buildURL('query', 'import', null, {schemaName: this.schemaName, 'query.queryName': this.queryName, importLookupByAlternateKey: !!this.importLookupByAlternateKey})
            ,bodyBorder: false
            ,border: true
            ,bodyStyle:'padding:5px'
            ,frame: false
            ,timeout: this.timeout || 60
            ,defaults: {
                bodyStyle:'padding:5px',
                border: false
            }
            ,buttonAlign: 'left'
            ,monitorValid: true
            ,items: [{
                xtype: 'container',
                itemId: 'templateArea'
            },{
                xtype: 'container',
                itemId: 'errorArea',
                border: false,
                defaults: {
                    border: false,
                    bodyBorder: false
                }
            },{
                xtype: 'radiogroup',
                name: 'uploadType',
                isFormField: false,
                itemId: 'inputType',
                width: 400,
                items: [{
                    boxLabel: 'Copy/Paste Data',
                    width: 200,
                    xtype: 'radio',
                    name: 'uploadType',
                    isFormField: false,
                    inputValue: 'text',
                    checked: true,
                    scope: this,
                    handler: function(fb, y){
                        if (!y){return};

                        var fileArea = this.down('#fileArea');
                        fileArea.removeAll();

                        fileArea.add(this.createTextArea(), this.createFileTypeCombo());

                        this.uploadType = 'text';
                    }
                },{
                    boxLabel: 'Upload From File',
                    width: 200,
                    xtype: 'radio',
                    name: 'uploadType',
                    inputValue: 'file',
                    handler: function(fb, y){
                        if (!y){return};

                        var fileArea = this.down('#fileArea');
                        fileArea.removeAll();

                        fileArea.add({
                            xtype: 'filefield',
                            name: 'file',
                            width: 400,
                            itemId: 'fileContent',
                            buttonText: 'Select File...'

                        });

                        this.uploadType = 'file';
                    },
                    scope: this
                }]
            },{
                xtype: 'panel',
                itemId: 'fileArea',
                width: '100%',
                border: false,
                items: [this.createTextArea(), this.createFileTypeCombo()]
            }]
        });

        Ext4.applyIf(this, {
            title: 'Upload Data'
            ,buttons: [{
                text: 'Upload'
                ,width: 50
                ,handler: this.formSubmit
                ,scope: this
                ,formBind: true
            },{
                text: 'Cancel'
                ,width: 50
                ,scope: this
                ,handler: function(){
                    window.location = LABKEY.ActionURL.getParameter('srcURL') || LABKEY.ActionURL.getParameter('returnURL') || LABKEY.ActionURL.getParameter('returnUrl') || LABKEY.ActionURL.buildURL('project', 'begin.view')
                }
            }]
        })

        if (this.isVisible()){
            this.doCloseExtMsg = true;
            Ext4.Msg.wait('Loading...');
        }

        LABKEY.Query.getQueryDetails({
            containerPath: this.containerPath
            ,schemaName: this.schemaName
            ,queryName: this.queryName
            ,scope: this
            ,success: this.populateTemplates
        });

        this.uploadType = 'text';

        this.callParent();

        this.on('actioncomplete', this.processResponse, this);
        this.on('actionfailed', this.processResponse, this);

        /**
         * @event uploadexception
         */
        /**
         * @event uploadcomplete
         */
        this.addEvents('uploadexception', 'uploadcomplete');
    },

    createFileTypeCombo: function() {
        return {
            xtype: 'combo',
            name: 'format',
            itemId: 'formatField',
            width: 300,
            value: 'tsv',
            displayField: 'displayText',
            valueField: 'value',
            triggerAction: 'all',
            mode: 'local',
            store: Ext4.create('Ext.data.ArrayStore', {
                fields: [
                    'value',
                    'displayText'
                ],
                idIndex: 0,
                data: [
                    ['tsv', 'Tab-separated text (tsv)'],
                    ['csv', 'Comma-separated text (csv)']
                ]
            })
        }
    },

    createTextArea: function() {
        return {
            itemId: 'fileContent',
            name: 'text',
            xtype: 'textarea',
            enableKeyEvents: true,
            height: 350,
            width: 700,
            listeners: {
                keydown: function(textfield, event) {
                    if (event.getKey() == event.TAB) {
                        // Special code to treat tabs as characters instead of navigation, adapted from
                        // http://ext4all.com/post/how-to-allow-tab-key-in-textarea.html
                        event.stopEvent();

                        var el = textfield.inputEl.dom;

                        if (el.setSelectionRange) {
                            var withIns = el.value.substring(0, el.selectionStart) + '\t';
                            var pos = withIns.length;
                            el.value = withIns + el.value.substring(el.selectionEnd, el.value.length);
                            el.setSelectionRange(pos, pos);
                        }
                        else if (document.selection) {
                            document.selection.createRange().text = '\t';
                        }
                    }
                }
            }
        };
    },

    populateTemplates: function(meta){
        //this allows for the possibility that some other component on the page opened a waiting dialog
        if (this.doCloseExtMsg && Ext4.Msg.isVisible()) {
            Ext4.Msg.hide();
        }
        this.doCloseExtMsg = false;

        var toAdd = [];

        if (meta.importMessage){
            toAdd.push({
                html: '<b>' + meta.importMessage + '</b>',
                style: 'padding-bottom: 20px;',
                border: false
            });
        }

        if (meta.importTemplates && meta.importTemplates.length > 1){
            toAdd.push({
                layout: 'hbox',
                style: 'padding-bottom: 20px;',
                border: false,
                items: [{
                    xtype: 'combo',
                    itemId: 'selectedTemplate',
                    fieldLabel: 'Choose Template',
                    width: 400,
                    value: meta.importTemplates[0].url,
                    labelWidth: 120,
                    displayField: 'label',
                    valueField: 'url',
                    store: Ext4.create('Ext.data.Store', {
                        data: meta.importTemplates,
                        fields: ['label', 'url'],
                        proxy: {
                            type: 'memory'
                        }
                    }),
                    queryMode: 'local'
                },{
                    xtype: 'button',
                    text: 'Download',
                    scope: this,
                    handler: function(btn){
                        var field = this.down('#selectedTemplate');
                        if (!field.getValue()){
                            Ext4.Msg.alert('Error', 'Must pick a template');
                            return;
                        }

                        this.generateExcelTemplate({
                            templateUrl: field.getValue()
                        })
                    },
                    style: 'margin-left: 5px;'
                }]
            });
        }
        else {
            toAdd.push({
                xtype: 'button',
                style: 'margin-bottom: 10px;',
                text: meta.importTemplates[0].label,
                border: true,
                handler: this.generateExcelTemplate,
                templateUrl: meta.importTemplates[0].url

            })
        }

        this.down('#templateArea').add(toAdd);
    },

    generateExcelTemplate: function(btn){
        Ext4.create('Ext.form.Panel', {
            url: btn.templateUrl,
            standardSubmit: true
        }).submit();
    },

    formSubmit: function(btn){
        btn.setDisabled(true);

        var value = this.down('#fileContent') ? this.down('#fileContent').getValue() : this.down('fileuploadfield').getValue();
        if (!value){
            Ext4.Msg.alert('Error', 'Must paste text or upload a file');
            btn.setDisabled(false);
            return;
        }

        //hold a reference to re-enable it later; the reason we do this is b/c the Window version of this component makes it slightly tricky to query and find the right btn
        this.btnToEnableOnComplete = btn;

        Ext4.Msg.wait('Uploading...');

        this.down('#errorArea').removeAll();

        this.form.fileUpload = !(this.uploadType == 'text');
        this.form.url = this.url;
        this.form.submit();
    },

    processResponse: function(form, action){
        var errorArea = this.down('#errorArea');
        errorArea.removeAll();

        if (this.btnToEnableOnComplete){
            this.btnToEnableOnComplete.setDisabled(false);
            this.btnToEnableOnComplete = null;
        }

        var response;
        try {
            response = (action.response && action.response.responseText) ? Ext4.JSON.decode(action.response.responseText) : null;
        }
        catch (err){
            console.error(err);
        }

        if (response && response.errors){
            var html = '<div style="color: red;padding-bottom: 10px;">There were errors in the upload: ';
            if (response.errors._form){
                html += response.errors._form;
            }
            html += '<br><br>';

            if (Ext4.isArray(response.errors)){
                var rowErrors;
                var style = 'style="color:red;padding-right: 10px;vertical-align: top;"';

                html += '<table border=0 style="vertical-align: top;">';
                Ext4.each(response.errors, function(error){
                    var rowErrors = [];
                    if (error.errors)
                        Ext4.iterate(error.errors, function(field){
                            rowErrors.push(error.errors[field]); //field + ': ' +
                        }, this);

                    var hasRow = Ext4.isDefined(error.rowNumber);
                    if (hasRow)
                        html += '<tr><td '+style+'>Row '+(error.rowNumber+1) + ':</td>';

                    html += '<td style="color:red;" '+(hasRow ? '' : ' colspan="2"')+'>'+rowErrors.join('<br>')+'</td></tr>';
                }, this);

                html += '</table>';
            }
            html += '</div>';
        }
        else {
            console.log(action)
        }

        errorArea.add({html: html});
        Ext4.Msg.hide();

        if (!response || !response.success){
            var msg = response ? response.exception || response.message : null;

            if (this.showAlertOnFailure)
                Ext4.Msg.alert('Error', msg || 'There was a problem with the upload');

            this.fireEvent('uploadexception', this, response);
        }
        else {
            if (response.rowCount > 0)
                response.successMessage = 'Success! '+response.rowCount+' rows inserted.';
            else
                response.successMessage = 'No rows inserted.';

            if (this.showAlertOnSuccess)
                Ext4.Msg.alert('Error', response.successMessage);

            this.fireEvent('uploadcomplete', this, response);

        }
    }
});



Ext4.define('LABKEY.ext4.ExcelUploadWin', {
    extend: 'Ext.Window',
    alias: 'widget.labkey-exceluploadwin',
    initComponent: function(){
        Ext4.apply(this, {
            closeAction:'hide',
            title: 'Upload Data',
            modal: true,
            width: 730,
            items: [{
                xtype: 'labkey-exceluploadpanel',
                bubbleEvents: ['uploadexception', 'uploadcomplete'],
                itemId: 'theForm',
                title: null,
                buttons: null,
                containerPath: this.containerPath,
                schemaName: this.schemaName,
                queryName: this.queryName
            }],
            buttons: [{
                text: 'Upload'
                ,width: 50
                ,handler: function(btn){
                    var form = this.down('#theForm');
                    form.formSubmit.call(form, btn);
                }
                ,scope: this
                ,formBind: true
            },{
                text: 'Close'
                ,width: 50
                ,scope: this
                ,handler: function(btn){
                    this.close();
                }
            }]
        });

        this.callParent();

        this.addEvents('uploadexception', 'uploadcomplete');
    }
});