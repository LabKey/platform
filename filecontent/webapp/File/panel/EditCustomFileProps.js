/*
 * Copyright (c) 2013-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.EditCustomFileProps', {
    extend : 'Ext.panel.Panel',
    alias : ['widget.editFileProps'],

    constructor : function(config)
    {
        Ext4.applyIf(config, {
            winId       : 'NoIdSupplied',
            fileRecords : [],
            extraColumns : [],
            fileProps : [],
            formPages : [],
            displayNum : 0,
            border : false,
            padding : '10 0 0 10',
            minHeight : 150
        });

        // Always include the Description field ("Flag/Comment")
        config.extraColumns.unshift({name: "Flag/Comment"});

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
        this.defaultValueTypes = {};
        this.defaultValues = {};
        for (var i=0; i < this.extraColumns.length; i++)
        {
            columns.push(this.extraColumns[i].name);
            this.defaultValueTypes[this.extraColumns[i].name] = this.extraColumns[i].defaultValueType;
            this.defaultValues[this.extraColumns[i].name] = this.extraColumns[i].defaultValue;
        }

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
        this.add({
            xtype : 'label',
            id : 'topPropsLabel',
            cls : 'labkey-mv',
            text : 'File (1 of ' + this.fileRecords.length + ') : ' + this.fileRecords[0].data.name
        });

        var customFields = [];
        for (var i=0; i < data.metaData.fields.length; i++)
        {
            var field = data.metaData.fields[i];

            if (field.name != 'RowId')
            {
                var fieldConfig = LABKEY.ext4.Util.getFormEditorConfig(field);
                fieldConfig.id = fieldConfig.name;

                if (fieldConfig.name == "Flag/Comment") {
                    fieldConfig.label = "Description";
                    fieldConfig.fieldLabel = "Description";
                    fieldConfig.itemId = "descriptionField";
                }

                fieldConfig.width = 330;
                fieldConfig.padding = "8 8 0 0";

                fieldConfig.value = this.getFormFieldValue(fieldConfig.name, this.displayNum);
                fieldConfig.helpPopup = this.defaultValueTypes[fieldConfig.name] == 'FIXED_NON_EDITABLE' ? "This field is read only because it has a fixed default value." : "";
                fieldConfig.readOnly = this.defaultValueTypes[fieldConfig.name] == 'FIXED_NON_EDITABLE';

                // resize text area to fit better in the dialog
                if (fieldConfig.xtype == "textarea")
                    fieldConfig.height = 75;

                // use 'checked' for setting boolean field value
                if (fieldConfig.xtype == 'checkbox')
                {
                    fieldConfig.checked = fieldConfig.value;
                    fieldConfig.uncheckedValue = false;
                }

                customFields.push(fieldConfig);
            }
        }

        this.add({
            xtype: 'form',
            border: false,
            maxHeight: 250,
            autoScroll: true,
            items: customFields
        });

        // set intial focus on the first field in the form (always the description field)
        var descriptionField = this.queryById("descriptionField");
        if (descriptionField)
            descriptionField.focus();

        this.applyCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel: "Apply to all remaining files",
            width: 330,
            style: "margin-top: 10px;",
            cls: "labkey-checkbox-mv",
            hidden: this.fileRecords.length <= 1,
            scope: this,
            handler: function(cb, checked) {
                this.down('#propsPrev').setDisabled(checked || this.displayNum == 0);
                this.down('#propsNext').setDisabled(checked || this.displayNum == (this.fileRecords.length-1));
            }
        });
        this.add(this.applyCheckbox);
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
                        this.down('#propsNext').setDisabled(false);
                        this.changeFormPage();
                        this.applyCheckbox.setValue(false);
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
                        this.down('#propsPrev').setDisabled(false);
                        this.changeFormPage();
                        this.applyCheckbox.setValue(false);
                    },
                    scope : this
                }
            );
        }

        buttons.push(
                {
                    xtype : 'button',
                    text : 'Save',
                    handler : this.doSave,
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

    doSave : function() {
        var files = [];
        this.saveFormPage();
        for (var i = 0; i < this.formPages.length; i++) {
            var formPage = this.formPages[i];
            var rec = this.fileRecords[i];
            var row = {
                // This is required when a row does not exist in Exp.Data table and an insert needs to be performed
                dataFileUrl: rec.data.dataFileUrl,
                Name: rec.data.name,
                // This is required when requesting the Resource from FileContentService
                id: rec.data.href,
                'Flag/Comment': rec.data.description
            };

            var prevValues = this.fileProps[rec.data.id];
            if (prevValues && prevValues.rowId !== undefined) {
                // This is required when a row does exist for this file in Exp.Data table and an update can be performed
                row.RowId = prevValues.rowId;
            }

            for (var r = 0; r < this.extraColumns.length; r++) {
                var extraColName = this.extraColumns[r].name;
                row[extraColName] = formPage[extraColName];
            }

            // Copy the field values back to the fileRecord
            Ext4.apply(rec.data, row);
            rec.data.description = row["Flag/Comment"];
            delete rec.data["Flag/Comment"];

            files.push(row);
        }

        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('filecontent', 'updateFileProps.api', this.containerPath),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            jsonData: { files: files },
            success: LABKEY.Utils.getCallbackWrapper(function(json) {
                if (!json.success) {
                    this.onSaveFailure(json);
                    return;
                }

                var win = Ext4.getCmp(this.winId);
                if (win) {
                    win.fireEvent('successfulsave');
                    win.close();
                }
            }),
            failure: LABKEY.Utils.getCallbackWrapper(this.onSaveFailure, this, true),
            scope: this
        });
    },

    onSaveFailure : function(json) {
        var errorMsg = '';

        if (json) {
            if (json.errors) {
                for (var i=0; i < json.errors.length; i++) {
                    var error = json.errors[i];
                    errorMsg += '<div class="labkey-error">' + (error.message || error.exception) + '</div>';
                }
            }
            else if (json.exception) {
                errorMsg = json.exception;
            }
        }

        if (!errorMsg) {
            errorMsg = 'Failed to update file properties. An unspecified error occurred.';
        }

        var el = Ext4.get('file-props-status');
        if (el) {
            el.update(errorMsg);
        }
        else {
            Ext4.Msg.alert('Error', errorMsg);
        }
    },

    saveFormPage : function()
    {
        var values = this.down('.form').getForm().getValues();

        if (this.applyCheckbox.getValue())
        {
            // apply current form to all files in this set
            for (var i = 0; i < this.fileRecords.length; i++)
            {
                if(!this.formPages[i])
                    this.formPages[i] = {};
                Ext4.apply(this.formPages[i], values);
            }
        }
        else
        {
            if(!this.formPages[this.displayNum])
                this.formPages[this.displayNum] = {};
            Ext4.apply(this.formPages[this.displayNum], values);
        }
    },

    changeFormPage : function()
    {
        var labelText = 'File (' + (this.displayNum+1) + ' of ' + this.fileRecords.length + ') : ' + this.fileRecords[this.displayNum].data.name;
        this.down('#topPropsLabel').setText(labelText);

        for(var i = 0; i < this.extraColumns.length; i++)
        {
            var formField = this.down('.form').getForm().findField(this.extraColumns[i].name);
            if (formField)
                formField.setValue(this.getFormFieldValue(this.extraColumns[i].name, this.displayNum));
        }
    },

    getFormFieldValue : function(name, fileIndex)
    {
        var prevValue = null;
        if (!this.formPages[fileIndex])
            prevValue = this.fileProps[this.fileRecords[fileIndex].data.id][name];
        else
            prevValue = this.formPages[fileIndex][name];

        return prevValue || this.defaultValues[name];
    }
});

