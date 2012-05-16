/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace('LABKEY.ext4');

/**
 * Constructs a new LabKey FormPanel using the supplied configuration.
 * @class LabKey extension to the <a href="http://docs.sencha.com/ext-js/4-0/#!/api/Ext.form.Panel">Ext.data.FormPanel</a> class,
 * which constructs a form panel, configured based on the query's metadata.
 *
 * <p>If you use any of the LabKey APIs that extend Ext APIs, you must either make your code open source or
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=extDevelopment">purchase an Ext license</a>.</p>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeyExt">Tips for Using Ext to Build LabKey Views</a></li>
 *              </ul>
 *           </p>
 * @constructor
 * @augments Ext.form.Panel
 * @param config Configuration properties.
 * @param {String} [config.store] A LABKEY.ext4.Store. If not supplied, so long as a schemaName/queryName
 * @param {String} [config.schemaName] The LabKey schema to query.
 * @param {String} [config.queryName] The query name within the schema to fetch.
 * @param {String} [config.viewName] A saved custom view of the specified query to use if desired.
 * @param {String} [config.containerPath] The container path from which to get the data. If not specified, the current container is used.
 * @param {Object} [config.metadata] A metadata object that will be applied to the default metadata returned by the server.  See example below for usage.
 * @param {Object} [config.metadataDefaults] A metadata object that will be applied to every field of the default metadata returned by the server.  Will be superceeded by the metadata object in case of conflicts. See example below for usage.
 * @param {Object} [config.storeConfig] A config object that will be used to create the store.
 * @param (boolean) [config.noAlertOnError] If true, no dialog will appear on if the store fires a syncerror event
 * @example &lt;script type="text/javascript"&gt;
    var _grid, _store;
    Ext.onReady(function(){

        //create a Store bound to the 'Users' list in the 'core' schema
        _store = new LABKEY.ext4.Store({
            schemaName: 'core',
            queryName: 'users'
        });

        //create a grid using that store as the data source
        _grid = new LABKEY.ext4.GridPanel({
            store: _store,
            renderTo: 'grid',
            width: 800,
            autoHeight: true,
            title: 'Example',
            editable: true
        });
    });


&lt;/script&gt;
&lt;div id='grid'/&gt;
 */


Ext4.define('LABKEY.ext4.FormPanel', {
    extend: 'Ext.form.Panel',
    alias: 'widget.labkey-formpanel',
    autoHeight: true,
    defaultFieldWidth: 350,
    defaultFieldLabelWidth: 150,
    initComponent: function(){
        Ext4.QuickTips.init();
        Ext4.FocusManager.enable();

        this.store = this.store || this.createStore();

        Ext4.apply(this, {
            trackResetOnLoad: true
            ,autoHeight: true
            ,bubbleEvents: ['added']
            ,buttonAlign: 'left'
            ,monitorValid: false
        });

        this.plugins = this.plugins || [];
        this.plugins.push(Ext4.create('LABKEY.ext4.DatabindPlugin'));

        LABKEY.Utils.mergeIf(this, {
            items: [{xtype: 'displayfield', value: 'Loading...'}]
            ,bodyBorder: false
            ,bodyStyle: 'padding:5px'
            ,style: 'margin-bottom: 15px'
            ,buttons: [
                LABKEY.ext4.FORMBUTTONS['SUBMIT']()
            ]
        });

        this.mon(this.store, 'exception', this.onCommitException, this);

        if(!this.store.hasLoaded())
            this.mon(this.store, 'load', this.loadQuery, this, {single: true});
        else
            this.loadQuery(this.store);

        if(Ext4.isString(this.errorEl))
            this.errorEl = Ext4.get(this.errorEl);

        this.callParent();

        /**
         * @memberOf LABKEY.ext4.FormPanel#
         * @name fieldconfiguration
         * @event
         * @description Fired after the query metadata has been processed to produce an array of fields.  Provides an opportunity to edit the fields or change the layout.
         * @param {Array} fields The array of fields that will be added to the form panel.
         */
        /**
         * @memberOf LABKEY.ext4.FormPanel#
         * @name fieldvaluechange
         * @event
         * @description Fired when the value of any field in the panel changes
         */
        this.addEvents('fieldconfiguration', 'fieldvaluechange');

        this.on('recordchange', this.markInvalid, this, {buffer: 100});
    },

    createStore: function(){
        return Ext4.create('LABKEY.ext4.Store', {
            containerPath: this.containerPath,
            schemaName: this.schemaName,
            queryName: this.queryName,
            sql: this.sql,
            viewName: this.viewName,
            columns: this.columns,
            storeId: LABKEY.ext.MetaHelper.getLookupStoreId(this),
            filterArray: this.filterArray || [],
            metadata: this.metadata,
            metadataDefaults: this.metadataDefaults,
            autoLoad: true,
            //NOTE: we do this to prevent loading the whole table
            maxRows: 0,
            listeners: {
                scope: this,
                load: function(store){
                    delete store.maxRows;
                }
            }
        });
    },

    onCommitException: function(response, operation){
        var msg;
        if(response.errors && response.errors.exception)
            msg = response.errors.exception;
        else
            msg = 'There was an error with the submission';

        //NOTE: in the case of trigger script errors, this will display the first error, even if many errors were generated
        if(!this.noAlertOnError)
            Ext4.Msg.alert('Error', msg);

        this.getForm().isValid();
    },

    loadQuery: function(store, records, success)
    {
        this.removeAll();

        if(success===false){
            this.add({html: 'The store did not load properly', border: false});
            this.doLayout();
            return;
        }

        var fields = this.store.getFields();
        if(!fields){
            console.log('There are no fields in the store');
            return;
        }

        var toAdd = this.configureForm(store);

        this.fireEvent('formconfiguration', toAdd);

        //create a placeholder for error messages
        if(!this.errorEl){
            toAdd.push({
                tag: 'div',
                itemId: 'errorEl',
                border: false,
                style: 'padding:5px;text-align:center;'
            });
        }

        this.fireEvent('fieldconfiguration', this, toAdd);
        this.add(toAdd);

        if(this.rendered)
            this.doLayout();

    },
    //NOTE: can be overridden for custom layouts
    configureForm: function(store){
        var toAdd = [];
        var compositeFields = {};
        store.getFields().each(function(c){
            var config = {
                queryName: store.queryName,
                schemaName: store.schemaName
            };

            if (LABKEY.ext.MetaHelper.shouldShowInUpdateView(c)){
                var theField = this.store.getFormEditorConfig(c.name, config);

                if(!c.width)
                    theField.width = this.defaultFieldWidth;
                if(!c.width)
                    theField.labelWidth = this.defaultFieldLabelWidth;

                if (c.inputType == 'textarea' && !c.height){
                    Ext4.apply(theField, {height: 100});
                }

                if(theField.xtype == 'combo'){
                    theField.store.autoLoad = true;
                }

                if(c.isUserEditable===false || c.isAutoIncrement || c.isReadOnly){
                    theField.xtype = 'displayfield';
                }

                if(!c.compositeField)
                    toAdd.push(theField);
                else {
                    theField.fieldLabel = undefined;
                    if(!compositeFields[c.compositeField]){
                        compositeFields[c.compositeField] = {
                            xtype: 'panel',
                            autoHeight: true,
                            layout: 'hbox',
                            border: false,
                            fieldLabel: c.compositeField,
                            defaults: {
                                border: false,
                                margins: '0px 4px 0px 0px '
                            },
                            width: this.defaultFieldWidth,
                            items: [theField]
                        };
                        toAdd.push(compositeFields[c.compositeField]);

                        if(compositeFields[c.compositeField].msgTarget == 'below'){
                            //create a div to hold error messages
                            compositeFields[c.compositeField].msgTargetId = Ext4.id();
                            toAdd.push({
                                tag: 'div',
                                fieldLabel: null,
                                border: false,
                                id: compositeFields[c.compositeField].msgTargetId
                            });
                        }
                        else {
                            theField.msgTarget = 'qtip';
                        }
                    }
                    else {
                        compositeFields[c.compositeField].items.push(theField);
                    }
                }
            }
        }, this);

        //distribute width for compositeFields
        for (var i in compositeFields){
            var compositeField = compositeFields[i];
            var toResize = [];
            //this leaves a 2px buffer between each field
            var availableWidth = this.defaultFieldWidth - 4*(compositeFields[i].items.length-1);
            for (var j=0;j<compositeFields[i].items.length;j++){
                var field = compositeFields[i].items[j];
                //if the field isnt using the default width, we assume it was deliberately customized
                if(field.width && field.width!=this.defaultFieldWidth){
                    availableWidth = availableWidth - field.width;
                }
                else {
                    toResize.push(field)
                }
            }

            if(toResize.length){
                var newWidth = availableWidth/toResize.length;
                for (j=0;j<toResize.length;j++){
                    toResize[j].width = newWidth;
                }
            }
        }

        return toAdd;
    }

});


Ext4.define('LABKEY.ext4.FormPanelWin', {
    extend: 'Ext.Window',
    alias: 'widget.labkey-formpanelwin',
    initComponent: function(){
        Ext4.apply(this, {
            closeAction:'hide',
            title: 'Upload Data',
            width: 730,
            modal: true,
            items: [{
                xtype: 'labkey-formpanel',
                bubbleEvents: ['uploadexception', 'uploadcomplete'],
                itemId: 'theForm',
                title: null,
                buttons: null,
                schemaName: this.schemaName,
                queryName: this.queryName,
                viewName: this.viewName
            }],
            buttons: [{
                text: 'Upload'
                ,width: 50
                ,handler: function(){
                    var form = this.down('form');
                    form.formSubmit.call(form);
                }
                ,scope: this
                ,formBind: true
            },{
                text: 'Close'
                ,width: 50
                ,scope: this
                ,handler: function(btn){
                    this.hide();
                }
            }]
        });

        this.callParent();

        this.on('validitychange', function(){
            console.log('validity chne')
        })
        this.addEvents('uploadexception', 'uploadcomplete');
    }
});

LABKEY.ext4.FORMBUTTONS = {
    SUBMIT: function(config){
        return Ext4.Object.merge({
            text: 'Submit',
            formBind: true,
            successURL: LABKEY.ActionURL.getParameter('srcURL'),
            handler: function(btn, key){
                var panel = btn.up('form');

                if(!panel.store.getNewRecords().length && !panel.store.getUpdatedRecords().length && !panel.store.getRemovedRecords().length){
                    Ext4.Msg.alert('No changes', 'There are no changes, nothing to do');
                    window.location = btn.successURL || LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: this.store.schemaName, 'query.queryName': this.store.queryName})
                    return;
                }

                function onSuccess(store){
                    Ext4.Msg.alert("Success", "Your upload was successful!", function(){
                        window.location = btn.successURL || LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: this.store.schemaName, 'query.queryName': this.store.queryName})
                    }, panel);
                }

                panel.store.on('write', onSuccess, this, {single: true});
                panel.store.on('exception', function(error){panel.store.un(onSuccess)}, this, {single: true});
                panel.store.sync();
            }
        }, config);
    },
    NEXTRECORD: function(){
        return {
            text: 'Next Record',
            scope: this,
            handler: function(btn, key){
                var panel = btn.up('form');
                var rec = panel.getForm().getRecord();
                if(rec){
                    var idx = panel.store.indexOf(rec);
                    idx = (idx+1) % panel.store.getCount();
                    panel.bindRecord(panel.store.getAt(idx));
                }
            }
        }
    },
    PREVIOUSRECORD: function(){
        return {
            text: 'Previous Record',
            handler: function(btn, key){
                var panel = btn.up('form');
                var rec = panel.getForm().getRecord();
                if(rec){
                    var idx = panel.store.indexOf(rec);
                    if(idx==0)
                        idx = panel.store.getCount();

                    idx = (idx-1) % panel.store.getCount();
                    panel.bindRecord(panel.store.getAt(idx));
                }
            }
        }
    },
    CANCEL: function(config){
        return Ext4.Object.merge({
            text: 'Cancel',
            handler: function(btn, key){
                window.location = btn.returnURL || LABKEY.ActionURL.getParameter('srcURL') || LABKEY.ActionURL.buildURL('project', 'begin')
            }
        }, config)
    }
}


Ext4.define('LABKEY.ext4.DatabindPlugin', {
    extend: 'Ext.AbstractPlugin',
    pluginId: 'labkey-databind',
    mixins: {
        observable: 'Ext.util.Observable'
    },
    init: function(panel){
        this.panel = panel;

        Ext4.apply(panel, {
            bindRecord: function(rec){
                var plugin = this.getPlugin('labkey-databind');
                plugin.bindRecord.call(plugin, rec);
            },
            unbindRecord: function(){
                var plugin = this.getPlugin('labkey-databind');
                plugin.unbindRecord.call(plugin);
            }
        });

        //set defaults
        panel.bindConfig = panel.bindConfig || {};
        panel.bindConfig = Ext4.Object.merge({
            disableUnlessBound: false,
            autoCreateRecordOnChange: true,
            autoBindFirstRecord: false,
            createRecordOnLoad: false
        }, panel.bindConfig);

        panel.addEvents('recordchange', 'fieldvaluechange');

        this.configureStore(panel.store);
        this.addFieldListeners();

        //we queue changes from all fields into a single event using buffer
        //this way batch updates of the form only trigger one record update/validation
        this.mon(panel, 'fieldvaluechange', this.updateRecord, this, {buffer: 50, delay: 10});
        this.mon(panel, 'add', function(o, c, idx){
            var findMatchingField = function(f) {
                if (f.isFormField) {
                    if (f.dataIndex) {
                        this.addFieldListener(c);
                    } else if (f.isComposite) {
                        f.items.each(findMatchingField, this);
                    }
                }
            };
            findMatchingField.call(this, c);
        }, this);

        this.callParent(arguments);
    },

    configureStore: function(store){
        store = Ext4.StoreMgr.lookup(store);

        if(!store)
            return;

        this.mon(store, 'load', function(store, records, success, operation, options){
            // Can only contain one row of data.
            if (records.length == 0){
                if(this.panel.bindConfig.createRecordOnLoad){
                    var values = this.getForm.getFieldValues();
                    var record = this.store.model.create(values);
                    this.store.add(record);
                    this.bindRecord(record);
                }
            }
            else {
                if(this.panel.bindConfig.autoBindFirstRecord){
                    this.bindRecord(records[0]);
                }
            }
        }, this);

        this.mon(store, 'remove', this.onRecordRemove, this);
        this.mon(this.panel.store, 'update', this.onRecordUpdate, this);
    },

    onRecordRemove: function(store, rec, idx){
        var boundRecord = this.panel.getForm().getRecord();
        if(boundRecord && rec == boundRecord){
            this.unbindRecord();
        }
    },

    onRecordUpdate: function(store, record, operation){
        var form = this.panel.getForm();
        if(form.getRecord() && record == form.getRecord()){
            form.suspendEvents();
            form.loadRecord(record);
            form.resumeEvents();
        }
    },

    bindRecord: function(record){
        var form = this.panel.getForm();

        if(form.getRecord())
            this.unbindRecord();

        form.suspendEvents();
        form.loadRecord(record);
        form.resumeEvents();
        form.isValid();
    },

    unbindRecord: function(){
        var form = this.panel.getForm();

        if(form.getRecord()){
            form.updateRecord(form.getRecord());
        }

        form._record = null;
        form.reset();
    },

    updateRecord: function(){
        var form = this.panel.getForm();
        if(form.getRecord()){
            form.updateRecord(form.getRecord());
        }
        else if (this.panel.bindConfig.autoCreateRecordOnChange){
            var values = form.getFieldValues();
            var record = this.panel.store.model.create(values);
            this.panel.store.add(record);
            this.bindRecord(record);
        }
    },

    addFieldListeners: function(){
        this.panel.getForm().getFields().each(this.addFieldListener, this);
    },

    addFieldListener: function(f){
        if(f.hasDatabindListener){
            console.log('field already has listener');
            return;
        }

        this.mon(f, 'check', this.onFieldChange, this);
        this.mon(f, 'change', this.onFieldChange, this);

        var form = f.up('form');
        if(form.getRecord() && this.panel.bindConfig.disableUnlessBound && !this.panel.bindConfig.autoCreateRecordOnChange)
            f.setDisabled(true);

        f.oldGetErrors = f.getErrors;
        f.getErrors = function(value){
            var errors = this.oldGetErrors(value);
            var record = this.up('form').getForm().getRecord();

            if(record){
                record.validate().each(function(e){
                    if(e.field == this.name)
                        errors.push(e.message);
                }, this);

                if(record.serverErrors && record.serverErrors[f.name]){
                    errors.push(record.serverErrors[f.name].join("<br>"));
                    delete record.serverErrors[f.name]; //only use it once
                }
            }



            errors = Ext4.Array.unique(errors);

//            if(errors.length)
//                console.log(errors);
            return errors;
        };
        f.hasDatabindListener = true;
    },

    //this is separated so that multiple fields in a single form are filtered into one event per panel
    onFieldChange: function(field){
        this.panel.fireEvent('fieldvaluechange', field);
    }

});