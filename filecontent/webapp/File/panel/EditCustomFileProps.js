/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.EditCustomFileProps', {
    extend : 'Ext.form.Panel',
    alias : ['widget.editFileProps'],

    constructor : function(config)
    {
        Ext4.apply(config);
        Ext4.applyIf(config, {
            winId       : 'NoIdSupplied',
            fileRecords : [],
            extraColumns : [],
            fileProps : [],
            formPages : [],
            displayNum : 0,
            border : false,
            padding : '10 0 0 10',
            minHeight : 150,
            maxHeight : 300,
            autoScroll: true
        });

        this.callParent([config]);
    },

    initComponent : function()
    {
        this.items = [];
        this.buttons = this.getButtons();
        this.callParent();

        this.getItems();
    },

    getItems : function()
    {
        var columns = ["RowId"];
        for (var i=0; i < this.extraColumns.length; i++)
            columns.push(this.extraColumns[i]);

        // Query the exp.Data table to get the column metadata info for the extraColumns
        LABKEY.Query.selectRows({
            schemaName:'exp',
            queryName:'Data',
            maxRows: 0,
            scope: this,
            containerPath: this.containerPath,
            requiredVersion: '9.1',
            columns: columns.join(','),
            successCallback: this.createFormPanel
        });
    },

    createFormPanel : function(data)
    {
        // Create a form panel from the returned select rows metadata
        var customFields = [{
            xtype : 'label',
            id : 'topPropsLabel',
            cls : 'labkey-mv',
            text : 'File (1 of ' + this.fileRecords.length + ') : ' + this.fileRecords[0].data.name
        }];

        for (var i=0; i < data.metaData.fields.length; i++)
        {
            var field = data.metaData.fields[i];
            var prevValues = this.fileProps[this.fileRecords[this.displayNum].data.id];

            if (field.name != 'RowId')
            {
                var fieldConfig = LABKEY.ext.Ext4Helper.getFormEditorConfig(field);
                fieldConfig.id = fieldConfig.name;
                fieldConfig.value = prevValues ? prevValues[fieldConfig.name] : null;
                fieldConfig.width = 330;
                fieldConfig.padding = "8 8 0 0";
                fieldConfig.helpPopup = "";

                // resize text area to fit better in the dialog
                if (fieldConfig.xtype == "textarea")
                    fieldConfig.height = 75;

                // use 'checked' for setting boolean field value
                if (fieldConfig.xtype == 'checkbox')
                    fieldConfig.checked = fieldConfig.value;

                customFields.push(fieldConfig);
            }
        }

        this.add(customFields);
    },

    getButtons : function()
    {

        var buttons = [];
        if (this.fileRecords.length > 1)
        {
            buttons.push(
                {
                    xtype : 'button',
                    id : 'propsPrev',
                    text : 'Prev',
                    anchor : 'left',
                    disabled : true,
                    handler : function(button){
                        this.saveFormPage();
                        this.displayNum--;
                        if(this.displayNum == 0)
                            button.setDisabled(true);
                        Ext4.getCmp('propsNext').setDisabled(false);
                        this.changeFormPage(this);
                    },
                    scope : this
                },
                {
                    xtype : 'button',
                    id : 'propsNext',
                    text : 'Next',
                    anchor : 'left',
                    handler : function(button) {
                        this.saveFormPage();
                        this.displayNum++;
                        if(this.displayNum == this.fileRecords.length-1)
                            button.setDisabled(true);
                        Ext4.getCmp('propsPrev').setDisabled(false);
                        this.changeFormPage(this);
                    },
                    scope : this
                }
            );
        }

        buttons.push(
                {
                    xtype : 'button',
                    text : 'Save',
                    handler : function()
                    {
                        var files = [];
                        this.saveFormPage();
                        for(var i = 0; i < this.formPages.length; i++)
                        {
                            var row = {Name: this.fileRecords[i].data.name};

                            var prevValues = this.fileProps[this.fileRecords[i].data.id];
                            if (prevValues)
                                row["RowId"] = prevValues["rowId"];

                            for(var r = 0; r < this.extraColumns.length; r++)
                            {
                                row[this.extraColumns[r]] = this.formPages[i][this.extraColumns[r]];
                            }

                            for(var r = 0; r < this.fileRecords.length; r++)
                            {
                                if(this.fileRecords[r].data.name === row["Name"])
                                {
                                    row.id = this.fileRecords[r].data.href;
                                    files.push(Ext4.apply(this.fileRecords[r].data, row));
                                    break;
                                }

                            }
                        }

                        Ext4.Ajax.request({
                            url: LABKEY.ActionURL.buildURL("filecontent", "updateFileProps"),
                            method : 'POST',
                            scope: this,
                            success: function(){
                                Ext4.getCmp(this.winId).fireEvent('successfulsave');
                                Ext4.getCmp(this.winId).close();
                            },
                            failure: function(response, opt){
                                var errorTxt = 'An error occurred submitting the .';
                                var jsonResponse = Ext.util.JSON.decode(response.responseText);
                                if (jsonResponse && jsonResponse.errors)
                                {
                                    for (var i=0; i < jsonResponse.errors.length; i++)
                                    {
                                        var error = jsonResponse.errors[i];
                                        errorTxt = '<span class="labkey-error">' + error.message + '</span>'
                                    }
                                }
                                var el = Ext4.get('file-props-status');
                                if (el)
                                    el.update(errorTxt);
                                Ext4.getCmp(this.winId).close();
                            },
                            jsonData : {files : files},
                            headers : {
                                'Content-Type' : 'application/json'
                            }
                        });
                    },
                    anchor : 'right',
                    scope : this
                },
                {
                    xtype : 'button',
                    text : 'Cancel',
                    handler : function()
                    {
                        Ext4.getCmp(this.winId).close();
                    },
                    anchor : 'right',
                    scope : this
                }
        );
        return buttons;
    },


    saveFormPage : function()
    {
        if(!this.formPages[this.displayNum])
            this.formPages[this.displayNum] = {};

        var values = this.getForm().getValues();
        for(var i = 0; i < this.extraColumns.length; i++)
        {
            this.formPages[this.displayNum][this.extraColumns[i]] = values[this.extraColumns[i]] || null;
        }
    },

    changeFormPage : function()
    {
        var labelText = 'File (' + (this.displayNum+1) + ' of ' + this.fileRecords.length + ') : ' + this.fileRecords[this.displayNum].data.name;
        Ext4.getCmp('topPropsLabel').setText(labelText);

        for(var i = 0; i < this.extraColumns.length; i++)
        {
            if (!this.formPages[this.displayNum])
                Ext4.getCmp(this.extraColumns[i]).setValue(this.fileProps[this.fileRecords[this.displayNum].data.id][this.extraColumns[i]]);
            else
                Ext4.getCmp(this.extraColumns[i]).setValue(this.formPages[this.displayNum][this.extraColumns[i]]);
        }
    }
});

