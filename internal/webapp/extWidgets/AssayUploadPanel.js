/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace('LABKEY.ext');
/**
 * This class is designed to override the default assay upload page.  The primary purpose was to support multiple 'import methods', which are alternate configurations
 * of fields or parsers.  This can be used to support outputs from multiple instruments imported through one pathway.  It also provides the ability to directly enter results into the browser through an Ext4 grid.
 * @name LABKEY.ext.AssayUploadPanel
 * @param {string} [config.assayName]
 * @param {array} [config.importMethods]
 * @param {function} [config.rowTransform]
 * @param {object} [config.metadata]
 * @param {object} [config.metadataDefaults]
 * @param {string} [config.defaultImportMethod]
 *
 *
 */
Ext4.define('LABKEY.ext.AssayUploadPanel', {
    extend: 'Ext.form.Panel',
    initComponent: function(){
        Ext4.QuickTips.init();

        if(!LABKEY.Security.currentUser.canInsert){
            alert('You do not have permissions to upload data');
            window.location = LABKEY.ActionURL.buildURL('project', 'start');
            return;
        }

        //we use query metadata instead of assay metadata b/c query metadata incorporates the .query.xml file
        this.domains = {};

        var multi = new LABKEY.MultiRequest();
        multi.add(LABKEY.Query.getQueryDetails, {
            schemaName: 'assay'
            ,queryName: this.assayName+' Batches'
            ,successCallback: function(results){
                this.domains.Batch = results;
            }
            ,failure: LABKEY.Utils.onError
            ,scope: this
        });
        multi.add(LABKEY.Query.getQueryDetails, {
            schemaName: 'assay'
            ,queryName: this.assayName+' Runs'
            ,successCallback: function(results){
                this.domains.Run = results;
            }
            ,failure: LABKEY.Utils.onError
            ,scope: this
        });
        multi.add(LABKEY.Query.getQueryDetails, {
            schemaName: 'assay'
            ,queryName: this.assayName+' Data'
            ,successCallback: function(results){
                this.domains.Results = results;
            }
            ,failure: LABKEY.Utils.onError
            ,scope: this
        });
        multi.add(LABKEY.Assay.getByName, {
            name: this.assayName
            ,success: function(results){
                this.assayDesign = results[0];
            }
            ,failure: LABKEY.Utils.onError
            ,scope: this
        });

        multi.send(this.onMetaLoad, this);

        Ext4.apply(this, {
            autoHeight: true
//            ,autoWidth: true
            ,bodyBorder: false
            ,border: false
            ,frame: false
            ,style: 'background-color: transparent;'
            ,bodyStyle: 'background-color: transparent;'
            ,defaults: {
                style:'padding:5px',
                bodyStyle:'padding:5px'
            },
            buttonAlign: 'left',
            monitorValid: true,
            buttons: [{
                text: 'Upload'
                ,width: 50
                ,hidden: !LABKEY.Security.currentUser.canInsert
                ,handler: this.formSubmit
                ,scope: this
                ,formBind: true
            },{
                text: 'Cancel'
                ,width: 50
                ,scope: this
                ,href: LABKEY.ActionURL.getParameter('srcURL') || LABKEY.ActionURL.buildURL('project', 'begin')
                ,target: '_self'
                ,formBind: true
            }],
            listeners: {
                scope: this,
                actioncomplete : this.handleDataUpload,
                actionfailed : this.handleDataUpload
            }
        });

        this.callParent(arguments);

        this.form.url = LABKEY.ActionURL.buildURL("assay", "assayFileUpload");

    },
    onMetaLoad: function(){
        console.log(this.assayDesign)
        this.handleImportMethods();
        this.defaultImportMethod = this.defaultImportMethod || 'defaultExcel';

        //create the panel:
        var radios = [];
        for (var i=0;i<this.importMethods.length;i++){
            radios.push({
                xtype: 'radio',
                name:'importMethod',
                id:'importMethod'+i,
                boxLabel: this.importMethods[i].label,
                inputValue: i,
                checked: (this.defaultImportMethod == this.importMethods[i].name),
                //value: i,
                scope: this,
                listeners: {
                    scope: this,
                    change: {fn: function(radio, val){
                        if(val){
                            this.selectedMethod = this.importMethods[radio.inputValue];
                            this.toggleMethod();
                        }
                    }, buffer: 10, scope: this}
                }
            });
        }

        this.add({
            xtype: 'form',
            title: 'Assay Properties',
            itemId: 'assayProperties',
            defaults: {
                width: 300
            },
            items: [
                {xtype: 'displayfield', fieldLabel: 'Assay Name', value: this.assayName, isFormField: false},
                {xtype: 'displayfield', fieldLabel: 'Assay Description', value: this.assayDesign.description, isFormField: false}
            ]},{
                xtype: 'form',
                title: 'Import Properties',
                itemId: 'runProperties',
                items: [{
                    xtype: 'radiogroup',
                    itemId: 'importMethodRadio',
                    fieldLabel: 'Import Method',
                    columns: 1,
                    isFormField: false,
                    scope: this,
                    defaults: {
                        width: 500
                    },
                    items: radios
                }]
            }
        );

        this.add({
            xtype: 'form',
            title: 'Run Fields',
            itemId: 'runFields'
        });

        this.renderFileArea();
        this.toggleMethod();

    },


    handleImportMethods: function(){
        this.importMethods = this.importMethods || new Array();

        this.importMethods.unshift(Ext4.create('LABKEY.ext.AssayImportMethod', {
            name: 'manualEntry',
            label: 'Manual Entry',
            webFormOnly: true,
            showResultGrid: true,
            noTemplateDownload: true
        }));

        this.importMethods.unshift(Ext4.create('LABKEY.ext.AssayImportMethod', {
            name: 'defaultExcel',
            label: 'Default Excel Upload'
        }));

        for (var i=0;i<this.importMethods.length;i++){
            if (this.importMethods[i].init){
                this.importMethods[i].init(this);
            }

            if(this.importMethods[i].isDefault)
                this.selectedMethod = this.importMethods[i];
        }

        this.selectedMethod = this.selectedMethod || this.importMethods[0]; 
    },


    toggleMethod: function(){
        this.down('#runFields').removeAll();

        //removes records of all fields except run name and description.
        //this.formFields.index = new Array();

        //we add new global fields
        this.addDomain('Batch');
        this.addDomain('Run');
        this.addDomain('Other');

        this.down('#runFields').doLayout();

        //we toggle visibility on the file import area
        this.down('#assayResults').setVisible(!this.selectedMethod.webFormOnly);

        if(this.selectedMethod.showResultGrid)
            this.renderResultGrid();
        else if (this.down('#resultGrid'))
            this.remove(this.down('#resultGrid'));

        this.down('#sampleDataArea').removeAll();

        if (!this.selectedMethod.noTemplateDownload){
            this.down('#sampleDataArea').add({
                xtype: 'button'
                ,text: 'Download Excel Template'
                ,border: false
                ,style: 'margin-bottom: 10px;'
                ,listeners: {
                    scope: this,
                    click: this.templateURL
                }
                ,scope: this
                ,handler: this.templateURL
            });
        }

        if (this.selectedMethod.exampleData){
            this.down('#sampleDataArea').add({
                xtype: 'button'
                ,border: false
                ,style: 'margin-bottom: 10px'
                ,text: 'Download Example Data'
                ,href: this.selectedMethod.exampleData
            });
        }

        this.doLayout();
    },

    addDomain: function(domain){

        var domainFields = new Array();
        switch (domain){
            case 'Other':
                domainFields = domainFields.concat(this.selectedMethod.newGlobalFields);
                break;
            case 'Run':
                domainFields = domainFields.concat(this.domains[domain].columns);

                for (var i=0;i<this.domains.Results.columns.length;i++){
                    var tmp = this.domains.Results.columns[i];
                    if (Ext4.Array.contains(this.selectedMethod.promotedResultFields, tmp.name)){
                        tmp.domain = 'Results';
                        domainFields.push(tmp);
                    }
                }
                break;
            case 'Results':
                domainFields = domainFields.concat(this.domains[domain].columns);
                break;
            default:
                domainFields = domainFields.concat(this.domains[domain].columns);
        }

        var skippedFields = this.selectedMethod['skipped'+domain+'Fields'] || [];
        if (domain == 'Results'){
            skippedFields.merge(this.selectedMethod.promotedResultFields)
        }
        skippedFields.sort();

        for (var i=0; i < domainFields.length; i++){
            if (domain == 'Other' && !domainFields[i].domain){
                alert('Error: Must supply the domain for field: '+domainFields[i].name);
                return
            }

            domainFields[i].domain = domainFields[i].domain || domain;

            if (!Ext4.Array.contains(skippedFields, domainFields[i].name) && LABKEY.ext.MetaHelper.shouldShowInInsertView(domainFields[i])){
                var fieldObj = domainFields[i];
                if(!fieldObj.jsonType)
                    fieldObj.jsonType = LABKEY.ext.MetaHelper.findJsonType(fieldObj);

                if(!fieldObj.id){
                    fieldObj.id = fieldObj.name
                }

                fieldObj = Ext4.Object.merge({
                    editorConfig: {
                        editable: false,
                        width: 350,
                        lazyInit: false,
                        domain: domainFields[i].domain,
                        labelWidth: 150
                    }
                }, fieldObj);

                this.metadataDefaults = this.metadataDefaults || {};
                if (this.metadataDefaults[fieldObj.domain]){
                    Ext4.Object.merge(fieldObj, this.metadataDefaults[fieldObj.domain]);
                }

                this.metadata = this.metadata || {};
                if (this.metadata[fieldObj.domain] && this.metadata[fieldObj.domain][fieldObj.name]){
                    Ext4.Object.merge(fieldObj, this.metadata[fieldObj.domain][fieldObj.name]);
                }

                if (this.selectedMethod.metadataDefaults && this.selectedMethod.metadataDefaults[fieldObj.domain]){
                    Ext4.Object.merge(fieldObj, this.selectedMethod.metadataDefaults[fieldObj.domain]);
                }

                if (this.selectedMethod.metadata && this.selectedMethod.metadata[fieldObj.domain] && this.selectedMethod.metadata[fieldObj.domain][fieldObj.name]){
                    Ext4.Object.merge(fieldObj, this.selectedMethod.metadata[fieldObj.domain][fieldObj.name]);
                }

                fieldObj.input = LABKEY.ext.MetaHelper.getFormEditor(fieldObj);
                this.down('#runFields').add(fieldObj.input);
            }
        }
    },

    templateURL: function(){
        if (!this.selectedMethod.createTemplate)
            this.makeExcel();
        else
            this.selectedMethod.createTemplate.call(this);
    },

    makeExcel: function(){
        var header = [];

        var rf = this.domains.Results.columns;
        var sf = this.selectedMethod.skippedResultFields || [];
        sf.sort();

        for (var i=0; i < rf.length; i++){
            if (!Ext4.Array.contains(sf, rf[i].name) && !rf[i].isHidden && rf[i].shownInInsertView!=false){
                header.push(rf[i].name);
            }
        }

        //we add new global fields
        if (this.selectedMethod.newResultFields){
            for (i=0;i<this.selectedMethod.newResultFields.length;i++)
                header.push(this.selectedMethod.newResultFields[i].name);
        }

        //TODO: Add formatting or validation in the excel sheet?
        var config = {
            fileName : this.assayName + '_' + (new Date().format('Y-m-d H_i_s')) + '.xls',
            sheets : [{
                    name: 'data',
                    data:
                    [
                        header
                    ]
                }]
        };

        LABKEY.Utils.convertToExcel(config);
    },

    renderResultGrid: function(){
        this.add({
            xtype: 'labkey-gridpanel',
            itemId: 'resultGrid',
            title: 'Assay Results',
            autoScroll: true,
            bodyStyle:'',
            minHeight: 400,
            tbar: [
                LABKEY.ext4.GRIDBUTTONS['ADDRECORD'](),
                LABKEY.ext4.GRIDBUTTONS['DELETERECORD']()
            ],
            forceFit: true,
            editable: true,
            hideNonEditableColumns: true,
            store: Ext4.create('LABKEY.ext4.Store', {
                schemaName: 'assay',
                queryName: this.assayName + ' Data',
                columns: '*',
                autoLoad: true,
                maxRows: 0,
                metadataDefaults: Ext4.Object.merge({}, this.metadataDefaults.Results, {
                    ignoreColWidths: true
                })
            })
        })
    },

    renderFileArea: function(){
        this.add({
            title: 'Assay Results',
            xtype: 'panel',
            itemId: 'assayResults',
            autoHeight: true,
            items: [{
                xtype: 'form',
                itemId: 'sampleDataArea',
                border: false
            },{
                xtype: 'radiogroup',
                name: 'uploadType',
                isFormField: false,
                itemId: 'inputType',
                width: 350,
                defaults: {
                    width: 200
                },
                items: [{
                    boxLabel: 'Copy/Paste Data',
                    xtype: 'radio',
                    name: 'uploadType',
                    isFormField: false,
                    inputValue: 'text',
                    checked: true,
                    scope: this,
                    handler: function(fb, y){
                        if (!y){return};

                        this.down('#fileArea').removeAll();
                        this.down('#fileArea').add({
                            itemId:"fileContent",
                            name: 'fileContent',
                            xtype: 'textarea',
                            height:350,
                            width: 700
                        })
                        this.down('#assayResults').doLayout();

                        this.uploadType = 'text';
                    }
                },{
                    boxLabel: 'File Upload',
                    xtype: 'radio',
                    name: 'uploadType',
                    inputValue: 'file',
                    handler: function(fb, y){
                        if (!y){return};

                        this.down('#fileArea').removeAll();
                        this.down('#fileArea').add({
                            xtype: 'filefield',
                            name: 'upload-run-field',
                            itemId: 'upload-run-field',
                            width: 400,
                            buttonText: "Select a file"
                        });
                        this.down('#assayResults').doLayout();

                        this.uploadType = 'file';
                    },
                    scope: this
                }]
            },{
                xtype: 'panel',
                itemId: 'fileArea',
                border: false,
                items: [{
                    itemId:"fileContent",
                    xtype: 'textarea',
                    name: 'fileContent',
                    height:350,
                    width: 700
                }]
            }]
        });
        this.uploadType = 'text';
    },

    formSubmit: function(){
        Ext4.Msg.wait("Uploading...");

        //LABKEY.page.batch.runs = [];
        this.batch = new LABKEY.Exp.ExpObject({
            batchProtocolId: this.assayDesign.id,
            properties: {},
            runs: []
        });
//        this.batch.batchProtocolId = this.assayDesign.id;

        var fields = this.form.getFieldValues();
        var uploadType = this.down('#inputType').getValue();
        
        if (this.selectedMethod.webFormOnly){
            var store = this.down('#resultGrid').store;

            var header = [];
            store.getFields().each(function(f){
                header.push(f.name);
            }, this);
            var data = [header];
            var newRow;
            store.each(function(rec){
                newRow = [];
                Ext4.each(header, function(f){
                    newRow.push({
                        value: rec.get(f)
                    })
                });
                data.push(newRow);
            }, this);

            var run = new LABKEY.Exp.Run();
            this.processData(data, run);
        }
        else {
            if (this.uploadType == 'text'){
                var text = this.down('#fileContent').getValue() || '';
                if(text.replace(/\s/g, '') == ''){
                    alert('You must enter either cut/paste from a spreadsheet or choose a file to import');
                    Ext4.Msg.hide();
                    return;
                }
                this.form.baseParams = {fileName: fields.Name+'.tsv'};
                this.form.fileUpload = false;
            }
            else {
                this.form.fileUpload = true;
                if(!this.down('#upload-run-field').getValue()){
                    alert('You must enter either a file or cut/paste from a spreadsheet');
                    Ext4.Msg.hide();
                    return;
                }
            }

            this.form.submit();
        }
    },

    handleDataUpload: function(f, action){
        if (!action)
        {
            console.log(action);
            Ext4.Msg.alert("Upload Failed", "Something went horribly wrong when uploading.");
            return;
        }
        if(!action.result){
            switch(action.failureType){
                case 'client':
                    Ext4.Msg.alert("One or more fields has a missing or improper value");
                    break;
                default:
                    console.log(action);
                    Ext4.Msg.alert("Upload Failed", "Something went wrong when uploading.");
                    break;
            }
            return;
        }
        if (!action.result.id)
        {
            Ext4.Msg.alert("Upload Failed", "Failed to upload the data file: " + action.result);
            return;
        }

        var data;
        if (this.uploadType == 'file')
        {
            data = new LABKEY.Exp.Data(action.result);
        }
        else
        {
            data = new LABKEY.Exp.Data(Ext4.JSON.decode(action.response.responseText));
        }

        var run = new LABKEY.Exp.Run();
        run.dataInputs = [ data ];

        if (!data.content)
        {
            // fetch the contents of the uploaded file.
            // Using 'jsonTSVExtended' will ensure date formats
            // found in the excel document are applied.
            data.getContent({
                format: 'jsonTSVExtended',
                scope: this,
                successCallback: function (content, format)
                {
                    data.content = content;
                    this.handleFileContent(run, content);
                },
                failureCallback: function (error, format)
                {
                    Ext4.Msg.hide();
                    Ext4.Msg.alert("Upload Failed", "An error occurred while fetching the contents of the data file: " + error.exception);
                    LABKEY.Utils.onError(error);
                }
            })
        }
        else {
            this.handleFileContent(run, data.content);
        }
    },

    handleFileContent: function(run, content){
        if (!content)
        {
            Ext4.Msg.hide();
            Ext4.Msg.alert("Upload Failed", "The data file has no content");
            return;
        }
        if (!content.sheets || content.sheets.length == 0)
        {
            // expected the data file to be parsed as jsonTSV
            Ext4.Msg.hide();
            Ext4.Msg.alert("Upload Failed", "The data file has no sheets of data");
            return;
        }

        // User 1st sheet unless there's a sheet named "Data"
        var sheet = content.sheets[0];
        for (var index = 0; index < content.sheets.length; index++)
        {
            if (content.sheets[index].name == "Data")
                sheet = content.sheets[index];
        }

        var data = sheet.data;
        if (!data.length)
        {
            Ext4.Msg.alert("Upload Failed", "The data file contains no rows");
            return;
        }

        this.processData(data, run)
    },

    processData: function(data, run){
        run.name = this.getForm().findField('Name').getValue() || "[New]";

        //Allows a custom parse/transform method to operate on all data
        if (this.selectedMethod.clientParsing.contentPre){
            var data = this.selectedMethod.clientParsing.contentPre.call(this, data);
            if (!data) return false;

            //TODO: figure out some way to save this modified data as an input?
            //run.dataInputs.push(data);
        }

        this.processing = {};

        this._parseHeader(data[0]);
        this._addGlobalFields(run);

        // convert the result data into an array of map objects
        run.dataRows = [];
        for (var i = 1; i < data.length; i++) {
            var row = data[i].concat(this.processing.extraResults);

            //Allows a custom parse/transform method to be defined per row
            //runs prior to default processing
            if (this.selectedMethod.clientParsing.rowPre)
                row = this.selectedMethod.clientParsing.rowPre.cell(this, row);

            var rowContent = {};
            if (!this.selectedMethod.clientParsing.overrideRow)
                rowContent = this._rowParse(row);

            //Allows a custom parse/transform method to be defined per row
            //runs after default processing
            if (this.selectedMethod.clientParsing.rowPost)
                this.selectedMethod.clientParsing.rowPost.call(this, rowContent);

            run.dataRows.push(rowContent);
        }

        //Allows a custom parse/transform method to be defined per upload
        //runs after default processing code
        if (this.selectedMethod.clientParsing.contentPost)
            this.selectedMethod.clientParsing.contentPost.call(this, run.dataRows);

        //LABKEY.page.batch.runs = LABKEY.page.batch.runs || new Array();
        //LABKEY.page.batch.runs.push(run);
        this.batch.runs = this.batch.runs || new Array();
        this.batch.runs.push(run);

        LABKEY.setDirty(true);
        this.saveBatch();
    },
    _addGlobalFields: function(run){
        //run name and description:
        //run.properties[this.form.items.map.RunName.name] = this.form.items.map.RunName.getValue();
        //run.properties[this.form.items.map.RunDescription.name] = this.form.items.map.RunDescription.getValue();

        //this adds the batch, run and global fields
        //any validation of these values should be done on form submission so we dont need to repeat here
        this.processing.extraResults = [];
        this.processing.otherFields = [];

        this.getForm().getFields().each(function(field){
            var value = field.getValue();
            
            if(field.isFormField === false)
                return;

            switch (field.domain)
            {
            //TODO: verify batch works
            case 'Batch':
                //LABKEY.page.batch.properties[field.name] = value;
                this.batch.properties[field.name] = value;
                break;
            case 'Run':
                run.properties[field.name] = value;
                break;
            case 'Results':
                this.processing.extraResults.push({value: value});
                this.processing.headers.push({name: (field.name || field.id)});
                break;
            case 'Other':
                this.processing.otherFields.push({label: field.name, value: value});
                break;
            }
        }, this);
    },
    _parseHeader: function(row){
        //TODO: figure out how to find row aliases and hook into metadata.          
        //because column titles can be variable, we try to translate them
        this.processing.headers = [];
        for (var i=0;i<row.length;i++){
            //TODO: I think the data object has different format for headers depending on whether it was text or a file
            //text has headers as an array of objects, files have a simple array of scalars
            var rawName = row[i].value || row[i];

            var name = LABKEY.ext.MetaHelper.resolveFieldNameFromLabel(rawName, this.domains.Results.columns);

            //NOTE: we let the process continue b/c it's possible we want to pass values to the validation script
            if (!name){
                console.log('Header Name Not Found: '+rawName);
                name = rawName;
            }

            this.processing.headers.push({raw: rawName, name: name});
        }
    },
    _rowParse: function(row){
        var rowContent = {};
        for (var j=0;j<this.processing.headers.length;j++){
            var field = row[j];
            var header = this.processing.headers[j];
//            var meta = this.metadata;
//            console.log(this.domains)

            if(!field){
                field = {};
            }

            //Allows a custom parse/transform method to be defined per field
            //runs prior to default processing
            if (this.selectedMethod.clientParsing.fieldsPre[header.name])
                this.selectedMethod.clientParsing.fieldsPre[header.name](field);

            var value = '';
            if (!this.selectedMethod.clientParsing[header.name] || !this.selectedMethod.clientParsing[header.name].overrideField)
                value = this._fieldParse(field);

            //Allows a custom parse/transform method to be defined per field
            //runs after default processing
            if (this.selectedMethod.clientParsing.fieldsPost[header.name])
                value = this.selectedMethod.clientParsing.fieldsPost[header.name](value);


            rowContent[header.name] = value;
        }

        if(this.rowTransform){
            rowContent = this.rowTransform.call(this, rowContent);
        }

        return rowContent;
    },

    _fieldParse: function (field){
        var val = field.formattedValue || field.value;
        return Ext.isEmpty(val) ? null : val;
    },

    saveBatch: function()
    {
        if (!LABKEY.dirty) return;

        LABKEY.Experiment.saveBatch({
            assayId : this.assayDesign.id,
            //batch : LABKEY.page.batch,
            batch : this.batch,
            scope: this,
            successCallback : function (batch, response)
            {
                LABKEY.setDirty(false);

                this.getForm().getFields().each(function(f){
                    if(f.isFormField===false)
                        return;

                    f.reset();
                });
                
                Ext4.Msg.hide();
                Ext4.Msg.alert("Success", "Data Uploaded Successfully", function(){
                    function doLoad(){
                        //NOTE: always return to the project root, ignoring srcURL
                        window.location = LABKEY.ActionURL.buildURL('project', 'begin');
                        //LABKEY.ActionURL.getParameter('srcURL') ||
                    }

                    doLoad.defer(400, this)
                });

            },
            failureCallback : function (error, format)
            {
                Ext4.Msg.hide();
                Ext4.Msg.alert("Error", "Failure when communicating with the server: " + error.exception);
                LABKEY.Utils.onError(error);
            }
        });
    }

});

//this is a prototype for the structure of a spreadsheet import method
//the idea is to let the use define custom methods, which contain might contain unique transform/validation
//it allows multiple pathways to import into the same table/assay
LABKEY.ext.AssayImportMethod = function(config){

    //check the config
    var required = ['name', 'label'];
    for (var i=0;i<required.length;i++){
        if (!config[required[i]]){
            alert('Must supply config.'+required[i]);
            return false;
        }
    }

    //this is the set of default options.
    // All possible params have been included even if not used
    //commented params are there for documentation only
    var defaults = {
        //name: 'Name',
        //,label: 'Other CSV Upload'

        //provide arrays with the names of any existing fields to skip
        skippedBatchFields: []
        ,skippedRunFields: []
        ,skippedResultFields: []

        //controls whether link to excel template appears
        ,noTemplateDownload: false

        //controls whether file content area shows.  otherwise this would be a web form only
        ,webFormOnly: false

        //path to file with example data
        ,exampleData: null

        //you can provide an array of new fields to add
        //object should match the labkey row object
        ,newGlobalFields: new Array()
        //,newGlobalFields: [{name: 'New', label: 'New', domain: 'Run'}]

        //names of result domain fields to be displayed globally.  will not appear in excel file
        ,promotedResultFields: new Array()

        //extra metadata applied to fields
        ,metadata: null

        //will be appended to excel template. note: result fields can also be added globally above
        //,newResultFields: [{name: 'New', label: 'New', domain: 'Run'}]

        //init function will run on page load.  useful if you need to load queries for validation
        //,init: function(){}

        //javascript methods to process data. not mutually exclusive with server-side scripts
        //methods should accept the listed argument and return the modified result

        //currently allows scripts to be run per cell, row or per upload.
        //allows scripts to be run either before or after the default processing
        //perhaps both are not needed, although in the past I have had a need for both
        //perhaps any pre-processing is best shifted to single transform script, rather than trying to do it per row/cell
        ,clientParsing: {
            contentPre: null, //function(data){}
            contentPost: null, //function(run.dataRows){}

            rowPre: null, //function(row){}
            rowPost: null, //function(rowContent){}

            fieldsPre: {
                //fieldName: function(field){}  //supplements processing on the named field
            },
            fieldsPost: {
                //fieldName: function(value){}
            }

        }

        //name or other ID to identify the server-side processing scripts
        //not currently supported,
        //ideally, would permit the same granularity as client-side scripts
        ,serverValidationScript: ''
        ,serverTransformScript: ''
        };

    return Ext4.Object.merge(defaults, config);
    
};