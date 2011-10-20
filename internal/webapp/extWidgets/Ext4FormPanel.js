/*
 * Copyright (c) 2010-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresExt4ClientAPI();

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
 * @param {Object} [config.fieldDefaults] A metadata object that will be applied to every field of the default metadata returned by the server.  Will be superceeded by the metadata object in case of conflicts. See example below for usage.
 * @param {Object} [config.storeConfig] A config object that will be used to create the store.
 *
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
    config: {
        defaultFieldWidth: 500
    },
    initComponent: function(){
        this.store = this.store || Ext4.create('LABKEY.ext4.Store', {
            containerPath: this.containerPath,
            schemaName: this.schemaName,
            queryName: this.queryName,
            sql: this.sql,
            viewName: this.viewName,
            columns: this.columns,
            storeId: LABKEY.ext.MetaHelper.getLookupStoreId(this),
            filterArray: this.filterArray || [],
            metadata: this.metadata,
            fieldDefaults: this.fieldDefaults,
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

        this.store.on('datachanged', this.onDataChanged, this);

        Ext4.apply(this, {
            trackResetOnLoad: true
            ,autoHeight: true
            ,bubbleEvents: ['added']
            ,buttonAlign: 'left'
            ,monitorValid: false
            ,buttons: [
                LABKEY.ext4.FORMBUTTONS['SUBMIT'].call(this)
            ]
            ,fieldDefaults: {
                labelWidth: 150
            }

        });

        this.plugins = this.plugins || [];
        this.plugins.push(Ext4.create('LABKEY.ext4.DatabindPlugin'));

        LABKEY.Utils.rApplyIf(this, {
            items: {xtype: 'displayfield', value: 'Loading...'}
            ,bodyBorder: false
            ,bodyStyle: 'padding:5px'
            ,style: 'margin-bottom: 15px'
        });

        if(!this.store.hasLoaded())
            this.mon(this.store, 'load', this.loadQuery, this, {single: true});
        else
            this.loadQuery(this.store);

        if(this.errorEl && typeof this.errorEl=='String')
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

        //create a placeholder for error messages
        if(!this.errorEl){
            toAdd.push({
                tag: 'div',
                itemId: 'errorEl',
                border: false,
                width: 350,
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

                if(!c.width){
                    theField.width = this.defaultFieldWidth;
                }

                if (c.inputType == 'textarea' && !c.height){
                    Ext4.apply(theField, {height: 100});
                }

                if(theField.xtype == 'combo'){
                    theField.lazyInit = false;
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
                            //msgTarget: c.msgTarget || 'qtip',
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
                for (var j=0;j<toResize.length;j++){
                    toResize[j].width = newWidth;
                }
            }
        }

        this.fireEvent('formconfiguration', toAdd);

        return toAdd;
    },

    onDataChanged: function(){
        console.log('data changed');
    },

    markInvalid : function()
    {
        var formMessages = [];
        var toMarkInvalid = {};

        if(!this.boundRecord)
            return;

        this.store.errors.each(function(error){
            var meta = error.record.fields.get(error.field);

            if(meta && meta.hidden)
                return;

            if(error.record===this.boundRecord){
                if ("field" in error){
                    //these are generic form-wide errors
                    if ("_form" == error.field){
                        formMessages.push(error.message);
                    }
                }
                else {
                    formMessages.push(error.message);
                }
            }
            else {
                formMessages.push('There are errors in one or more records.  Problem records should be highlighted in red.');
            }
        }, this);

        if (this.errorEl){
            formMessages = Ext.Array.unique(formMessages);
            formMessages = Ext4.util.Format.htmlEncode(formMessages.join('\n'));
            this.errorEl.update(formMessages);
        }

        this.getForm().items.each(function(f){
            f.validate();
        }, this);

    }

//    onRecordChange: function(theForm){
//        if(!this.boundRecord)
//            this.getBottomToolbar().setStatus({text: 'No Records'});
//    },

//    onStoreValidate: function(store, records){
//        if(store.errors.getCount())
//            this.getBottomToolbar().setStatus({text: 'ERRORS', iconCls: 'x-status-error'});
//        else
//            this.getBottomToolbar().setStatus({text: 'Section OK', iconCls: 'x-status-valid'});
//
//        this.markInvalid();
//    },

});


Ext4.define('LABKEY.ext4.FormPanelWin', {
    extend: 'Ext.Window',
    alias: 'widget.labkey-formpanelwin',
    initComponent: function(){
        Ext4.apply(this, {
            closeAction:'hide',
            title: 'Upload Data',
            width: 730,
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

        this.addEvents('uploadexception', 'uploadcomplete');
    }
});

LABKEY.ext4.FORMBUTTONS = {
    SUBMIT: function(){
        return {
            text: 'Submit',
            scope: this,
            handler: function(btn, key){
                btn.up('form').store.sync();
            }
        }
    } ,
    TESTSUBMIT: function(){
        return {
            text: 'Change Values',
            scope: this,
            handler: function(btn, key){
                btn.up('form').store.each(function(r, idx){
                    r.set('field2', Math.random(10));
                    r.set('field1', 'field2341');
                }, this);
                this.store.add({
                    field1: 'new record'
                });
//                this.store.sync();
            }
        }
    },
    SHOWSTORE: function(){
        return {
            text: 'Show Store',
            scope: this,
            handler: function(btn, key){
                btn.up('form').store.each(function(r){
                    console.log(r);
                }, this);
            }
        }
    } ,
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
            scope: this,
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
            autoCreateRecordOnChange: false,
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

        f.hasDatabindListener = true;
    },

    //this is separated so that multiple fields in a single form are filtered into one event per panel
    onFieldChange: function(field){
        this.panel.fireEvent('fieldvaluechange', field);
    }

});