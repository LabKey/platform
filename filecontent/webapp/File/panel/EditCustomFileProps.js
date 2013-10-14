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
            extraColumns : [],
            fileProps : [],
            formPages : [],
            displayNum : 0,
            height : 200
        });

        this.callParent([config]);
    },

    initComponent : function()
    {
        this.items = this.getItems();
        this.buttons = this.getButtons();
        this.callParent();
    },

    getItems : function()
    {


        var customFields = [{
            xtype : 'label',
            id : 'topPropsLabel',
            text : 'Edit file properties for ' + this.sm[0].get('name')
        }];
        for(var i = 0; i < this.extraColumns.length; i++)
        {
            customFields.push({
                xtype : 'textfield',
                width : 200,
                id : this.extraColumns[i],
                fieldLabel : this.extraColumns[i],
                value : this.fileProps[this.sm[this.displayNum].get('name')][this.extraColumns[i]],
                padding : '10 10 0 0'
            });
        }
        return customFields;
    },

    getButtons : function()
    {

        var buttons = [];
        if(this.sm.length > 1)
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
                        if(this.displayNum == this.sm.length-1)
                            button.setDisabled(true);
                        Ext4.getCmp('propsPrev').setDisabled(false);
                        this.changeFormPage(this);
                    },
                    scope : this
                }
        );

        buttons.push(
                {
                    xtype : 'button',
                    text : 'Save',
                    handler : function()
                    {
                        var files = [];
                        var row;
                        this.saveFormPage();
                        for(var i = 0; i < this.formPages.length; i++)
                        {
                            row = {};
                            for(var r = 0; r < this.extraColumns.length; r++)
                            {
                                row[this.extraColumns[r]] = this.formPages[i][this.extraColumns[r]];
                            }
                            row["RowId"] = this.fileProps[this.sm[i].get('name')]["rowId"];
                            row["Name"] = this.fileProps[this.sm[i].get('name')]["name"];
                            for(var r = 0; r < this.sm.length; r++)
                            {
                                if(this.sm[r].data.name === row["Name"])
                                {
                                    row.id = this.sm[r].data.href;
                                    files.push(Ext4.apply(this.sm[r].data, row));
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
                    anchor : 'right'
                }
        );
        return buttons;
    },


    saveFormPage : function()
    {
        if(!this.formPages[this.displayNum])
            this.formPages[this.displayNum] = {};
        for(var i = 0; i < this.extraColumns.length; i++)
        {
            this.formPages[this.displayNum][this.extraColumns[i]] = Ext4.getCmp(this.extraColumns[i]).getValue();
        }
    },

    changeFormPage : function()
    {
        for(var i = 0; i < this.extraColumns.length; i++)
        {
            Ext4.getCmp('topPropsLabel').setText('Edit file properties for ' + this.sm[this.displayNum].get('name'));
            if(!this.formPages[this.displayNum]){
                Ext4.getCmp(this.extraColumns[i]).setValue(this.fileProps[this.sm[this.displayNum].get('name')][this.extraColumns[i]]);
            }
            else
            {
                Ext4.getCmp(this.extraColumns[i]).setValue(this.formPages[this.displayNum][this.extraColumns[i]]);
            }

        }
    }
});

