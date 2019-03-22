/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * EXPERIMENTAL
 *
 * This panel contains a single file input.  On submit(), it will save this file to the server, to the folder specified in config.containerPath.
 * It will then query exp.data and return the corresponding row for this file.  The purpose is to separate file upload from form submission
 * such that forms can upload files and then store pointers (the rowId from exp.data) to these files in other tables.
 *
 * @param {object} config The configuration properties
 * @param {string} [config.containerPath] The container in which to save the file.
 * @param {boolean} [config.overwrite] If true, uploads will overwrite previously uploaded files of the same name.  Defaults to true.
 *
 * @example &lt;script type="text/javascript"&gt;
    Ext4.onReady(function(){
       Ext4.create('LABKEY.ext.FileUploadPanel', {
         containerPath: LABKEY.ActionURL.getContainer(),
         showSubmitBtn: true,
         //config that will be applied to the file upload field
         defaults: {
            width: 300
         },
         buttons: [{
            text: 'Submit',
            handler: function(btn){
                btn.up('panel').submit({
                    success: function(row){
                        console.log('You uploaded file: ' + row.RowId)
                    }
                });
            }
         }]
       }).render('testDiv');
     });
&lt;/script&gt;
&lt;div id='testDiv'/&gt;
 */

Ext4.define('LABKEY.ext.FileUploadPanel', {
    extend: 'Ext.panel.Panel',
    overwrite: true,
    initComponent: function(){
        this.url = this.getURL();
        Ext4.apply(this, {
            border: false,
            defaults: {
                width: 300
            },
            items: [{
                xtype: 'fileuploadfield',
                itemId: 'fileUpload'
            }]
        });

        this.callParent();
    },

    /**
     *
     * @param config The configuration properties
     * @param [config.success] A success callback.  Will be passed the row object from exp.data for this file
     * @param [config.failure] A failure callback.  Will be passed an error object.
     * @param [config.scope] The scope to be used for both callback functions.
     */
    submit: function(config){
        config = config || {};

        var fileInput = this.down('#fileUpload');
        if(!fileInput.getValue()){
            alert('Must supply a file');
            return;
        }
        var fileName = this.getFilenameFromPath(fileInput.getValue());

        var form = Ext4.create('Ext.form.Basic', this, {
            url: this.url,
            errorReader: this.getErrorReader(),
            method: 'PUT'
        });

        form.submit({
            isUpload: true,
            errorReader: this.getErrorReader(),
            scope: this,
            failure: function(error){
                if(config.failure)
                    config.failure.apply(config.scope || this, arguments);
            },
            success: function(form, action){
                LABKEY.Query.selectRows({
                    schemaName: 'exp',
                    queryName: 'data',
                    filterArray: [LABKEY.Filter.create('name', fileName, LABKEY.Filter.Types.EQUAL)],
                    scope: this,
                    failure: function(error){
                        console.log('error');
                        console.log(error)
                    },
                    success: function(result){
                        if(result && result.rows.length){
                            if(config.success)
                                config.success.apply(config.scope || this, [result.rows[0]])
                        }
                        else {
                            if(config.failure)
                                config.failure.apply(config.scope || this, [{exception: "No row found in exp.data for file: " + fileName}]);
                        }
                    }
                })
            }
        });
    },

    //private
    getURL: function(){
        //strip trailing/leading slashes:
        var container = this.containerPath.replace(/^\/|\/$/, '');
        return LABKEY.ActionURL.getBaseURL() + '_webdav/' + container + '/@files' + '?overwrite=' + (this.overwrite ? 't' : 'f');
    },

    //private
    //the file input contains the whole file path.  there didnt seem to be a good way to parse filename automatically, so we just split on slashes
    getFilenameFromPath: function(fileName){
        return /([^\\|\/]+)$/.exec(fileName)[1];
    },

    //private
    getErrorReader: function(){
        Ext4.define('FileModel', {
            extend: 'Ext.data.Model',
            fields: []
        });

        return new Ext4.data.reader.Xml({
            record : "response",
            id : "",
            model: 'FileModel'
        });
    }
});