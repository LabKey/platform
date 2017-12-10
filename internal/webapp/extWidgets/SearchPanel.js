/*
 * Copyright (c) 2010-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace('LABKEY.ext4');

LABKEY.requiresScript('ux/CheckCombo/CheckCombo.js');
LABKEY.requiresCss('ux/CheckCombo/CheckCombo.css');

/**
 * Constructs a new LabKey Search Panel using the supplied configuration.
 * @class LabKey extension to the <a href="http://docs.sencha.com/ext-js/4-0/#!/api/Ext.form.Panel">Ext.form.Panel</a> class,
 * which creates a search panel for the specified query.
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
 * @param config Configuration properties.
 * @param {String} [config.schemaName] The LabKey schema to query.
 * @param {String} [config.queryName] The query name within the schema to fetch.
 * @param {String} [config.viewName] A saved custom view of the specified query to use if desired.
 * @param {String} [config.containerPath] The containerPath to use when fetching the query
 * @param {String} [config.columns] A comma-delimited list of column names to fetch from the specified query.
 * @param {Object} [config.metadata] A metadata object that will be applied to the default metadata returned by the server.  See example below for usage.
 * @param {Object} [config.metadataDefaults] A metadata object that will be applied to every field of the default metadata returned by the server.  Will be superceeded by the metadata object in case of conflicts. See example below for usage.
 * @param (boolean) showContainerFilter Dictates whether a combobox appears to let the user pick a container filter when searching
 * @param (string) defaultContainerFilter The default container filter in the combo.  If provided, but showContainerFilter is false, this container filter will be silently applied to the search
 * @param (boolean) allowSelectView Dictates whether a combobox appears to let the user pick a view
 * @param (string) defaultViewName If provided, this view will be initially selected in the views combo
 *
 * @example &lt;script type="text/javascript"&gt;

    Ext4.create('LABKEY.ext4.SearchPanel', {
         schemaName: 'core'
        ,queryName: 'users'
        ,columns: '*'
        ,title: 'Search Panel'
        ,showContainerFilter: true
        ,defaultContainerFilter: 'CurrentAndSubfolders'
        //override default metadata
        ,metadata: {
            VialId: {lookups: false},
            Id: {lookups: false}
        }
    }).render('searchPanel');

 &lt;/script&gt;
 &lt;div id='searchPanel'/&gt;
 */


Ext4.define('LABKEY.ext4.SearchPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.labkey-searchpanel',
    LABEL_WIDTH: 150,
    FIELD_WIDTH: 250,
    OP_FIELD_WIDTH: 185,

    initComponent: function(){
        Ext4.apply(this, {
            title: this.title,
            fieldDefaults: {
                labelWidth: this.LABEL_WIDTH
            },
            defaults: {
                border: false,
                bodyBorder: false
            },
            items: [{
                html: 'Loading...',
                bodyStyle: 'background-color: transparent;'
            }],
            keys: this.keys || [{
                key: Ext4.EventObject.ENTER,
                handler: this.onSubmit,
                scope: this
            }]
        });

        //use dockedItems directly, in order to make background transparent
        var buttons = this.buttons || [{
            text: 'Submit', scope: this, handler: this.onSubmit
        }];
        buttons.unshift({xtype: 'tbfill'});
        this.buttons = null;
        if(buttons){
            this.dockedItems = {
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent;',
                items: buttons
            }
        }

        Ext4.applyIf(this, {
            bodyStyle: 'background-color: transparent;padding: 5px;',
            border: true,
            bodyBorder: false,
            width: 610
        });

        this.callParent();

        this.store = this.store || Ext4.create('LABKEY.ext4.data.Store', {
            containerPath: this.containerPath
            ,queryName: this.queryName
            ,schemaName: this.schemaName
            ,viewName: this.viewName
            ,metadata: this.metadata
            ,metadataDefaults: this.metadataDefaults
            ,columns: this.columns
            ,maxRows: 0
            ,timeout: 30000
            ,scope: this
            ,autoLoad: true
            ,listeners: {
                scope: this,
                exception: LABKEY.Utils.onError
            }
        });

        if (LABKEY.ext4.Util.hasStoreLoaded(this.store))
            this.onLoad(this.store, true);
        else
            this.mon(this.store, 'load', this.onLoad, this, {single: true});

        Ext4.Ajax.timeout = 30000; //in milliseconds
    },

    onLoad: function(store, success){
        this.removeAll();

        if (!success || !store || !LABKEY.ext4.Util.hasStoreLoaded(store)){
            this.add({tag: 'div', html: 'Error loading data'});
            LABKEY.Utils.signalWebDriverTest('extSearchPanelLoaded', 'error');
            return;
        }

        var toAdd = [];

        // loop through metadata to find input-only parameters on search panel

        for (var metadataField in store.metadata) {
            if(!store.metadata.hasOwnProperty(metadataField))
                continue;

            var metadataObj = store.metadata[metadataField];

            if(metadataObj.searchOnly && (metadataObj.searchOnly === true)) {
                var dummyMeta = {};
                dummyMeta.hidden = false;
                dummyMeta.selectable = true;
                dummyMeta.caption = metadataObj.caption;
                dummyMeta.defaultValue = metadataObj.defaultValue;
                if(metadataObj.type) {
                    if (metadataObj.type === 'date') // only supporting date types for now
                        dummyMeta.jsonType = 'date';
                }
                if(metadataObj.queryParameter && (metadataObj.queryParameter === true)) {
                    dummyMeta.queryParameter = true;
                    dummyMeta.parameterName = metadataObj.parameterName;
                }

                toAdd = toAdd.concat(this.addRow(dummyMeta));
            }
        }

        store.getFields().each(function(f){
            toAdd = toAdd.concat(this.addRow(f));
        }, this);

        if (this.showContainerFilter){
            toAdd.push(Ext4.apply(this.getRowCfg(), {
                bodyStyle: 'background-color: transparent;',
                items: [{
                    cls: 'search-panel-row-label',
                    html: 'Container Filter:',
                    bodyStyle: 'background-color: transparent;',
                    width: this.LABEL_WIDTH
                },{
                    xtype: 'labkey-containerfiltercombo'
                    ,width: this.OP_FIELD_WIDTH
                    ,value: this.defaultContainerFilter || ''
                    ,fieldType: 'containerFilterName'
                    ,itemId: 'containerFilterName'
                }]
            }));
        }

        if (this.allowSelectView !== false){
            toAdd.push(Ext4.apply(this.getRowCfg(), {
                bodyStyle: 'background-color: transparent;',
                items: [{
                    cls: 'search-panel-row-label',
                    html: 'View:',
                    bodyStyle: 'background-color: transparent;',
                    width: this.LABEL_WIDTH
                },{
                    xtype: 'labkey-viewcombo',
                    containerPath: this.containerPath,
                    queryName: this.store.queryName,
                    schemaName: this.store.schemaName,
                    width: this.OP_FIELD_WIDTH,
                    value: this.defaultViewName || '',
                    fieldType: 'viewName',
                    itemId: 'viewNameField'
                }]
            }));
        }

        this.add(toAdd);
        LABKEY.Utils.signalWebDriverTest('extSearchPanelLoaded', 'success');
    },

    getRowCfg: function(){
        return {
            xtype: 'container',
            layout: 'hbox',
            bodyStyle: 'padding: 5px;',
            border: false,
            cls: 'search-panel-row',
            defaults: {
                style: 'margin: 2px;',
                border: false
            }
        }
    },

    addRow: function(meta){
        if (meta.inputType == 'textarea'){
            meta.inputType = 'textbox';
        }

        if (this.metadataDefaults){
            LABKEY.Utils.merge(meta, this.metadataDefaults);
        }

        if (this.metadata && this.metadata[meta.name]){
            LABKEY.Utils.merge(meta, this.metadata[meta.name]);
        }

        var rows = [];
        //NOTE: if the query lacks a PK, Ext automatically inserts a field called 'id'
        if (!meta.hidden && meta.selectable !== false && meta.caption !== undefined){
            var replicates = 1;
            if (meta.duplicate)
                replicates = 2;

            for (var i = 0; i < replicates; i++){
                rows.push(Ext4.apply(this.getRowCfg(), {
                    items: this.getRowItems(meta)
                }));
            }
        }

        return rows;
    },

    getRowItems: function(meta){
        var row = [];
        if (meta.lookup && meta.lookups !== false){
            meta.includeNullRecord = false;
            meta.editorConfig = meta.editorConfig || {};
            meta.editorConfig.multiSelect = true;
            meta.editorConfig.delimiter = ';';
        }

        if (meta.jsonType == 'boolean'){
            meta.editorConfig = meta.editorConfig || {};
            meta.editorConfig.includeNullRecord = true;
            meta.xtype = 'labkey-booleancombo';
        }

        // Integer field must be able to use commas for between option
        if (meta.jsonType == 'int'){
            meta.xtype = 'textfield';
            meta.editorConfig = meta.editorConfig || {};
            meta.editorConfig.regex = /^[0-9,]+$/;
            meta.editorConfig.regexText = 'Must be an integer or two comma separated integers if using a Between operator.';
        }

        if (meta.jsonType == 'float'){
            meta.xtype = 'textfield';
            meta.editorConfig = meta.editorConfig || {};
            meta.editorConfig.regex = /^[0-9.,]+$/;
            meta.editorConfig.regexText = 'Must be a decimal or two comma separated decimals if using a Between operator.';
        }

        if (meta.jsonType == 'date')
            meta.name = meta.caption;

        meta.editable = true; //force read only fields to give an input

        //create the field
        var theField = LABKEY.ext4.Util.getFormEditorConfig(meta);
        theField.fieldLabel = null;
        theField.defaultValue = null;
        theField.value = null;
        theField.disabled = false;
        theField.hidden = false;
        theField.cls = 'search-panel-row-value';

        //the label
        row.push({
            cls: 'search-panel-row-label',
            html: meta.caption + ':', width: this.LABEL_WIDTH,
            bodyStyle: 'background-color: transparent;'
        });
        Ext4.apply(theField, {
            nullable: true,
            allowBlank: true,
            width: this.FIELD_WIDTH,
            isSearchField: true
        });

        //NOTE: if the field is a lookup, the dataRegion will display/filter this field on the value
        //therefore on submit, we actually filter on display value, not raw value
        //Issue: 13723
        //see also issue 16791
        if (meta.lookup && meta.displayField){
            theField.dataIndex = meta.displayField;
            theField.valueField = theField.displayField;
        }

        //the operator
        var id = Ext4.id();
        if (meta.jsonType == 'boolean'){
            row.push({width: this.OP_FIELD_WIDTH});
        }
        else if (theField.xtype == 'labkey-combo'){
            Ext4.apply(theField, {
                xtype: 'checkcombo',
                expandToFitContent: true,
                addAllSelector: true,
                nullCaption: '[Blank]'
            });

            theField.opField = id;
            row.push({
                xtype: 'hidden',
                value: 'in',
                itemId: id
            });

            //NOTE: in Ext4.2.1 hidden fields no longer preserve width, so add a spacer
            row.push({
                border: false,
                width: this.OP_FIELD_WIDTH
            });
        }
        else {
            theField.opField = id;
            if(!meta.queryParameter) {
                row.push({
                    xtype: 'labkey-operatorcombo',
                    cls: 'search-panel-row-operator',
                    jsonType: meta.jsonType,
                    mvEnabled: meta.mvEnabled,
                    itemId: id,
                    width: this.OP_FIELD_WIDTH
                });
            }
        }

        //the field itself
        row.push(theField);
        return row;
    },

    onSubmit: function(){
        var params = {
            schemaName: this.store.schemaName,
            'query.queryName': this.store.queryName
        };

        var cf = this.down('#containerFilterName');
        if (cf && cf.getValue()){
            params['query.containerFilterName'] = cf.getValue();
        }
        else if (this.defaultContainerFilter){
            params['query.containerFilterName'] = this.defaultContainerFilter;
        }

        var vf = this.down('#viewNameField');
        if (vf && vf.getValue()){
            params['query.viewName'] = vf.getValue();
        }

        Ext4.apply(params, this.getParams());

        window.location = LABKEY.ActionURL.buildURL(
            'query',
            'executeQuery.view',
            (this.containerPath || LABKEY.ActionURL.getContainer()),
            params
        );
    },

    getParams: function(dataRegionName){
        dataRegionName = dataRegionName || 'query';
        var params = {};

        this.cascade(function(item){
            if (!item.isSearchField)
                return;
            if (item.originalConfig.queryParameter && (item.originalConfig.queryParameter === true)) {
                if(item.rawValue)
                    params[('query.param.' + item.originalConfig.parameterName)] = item.rawValue;
                else if (item.originalConfig.defaultValue)
                    params[('query.param.' + item.originalConfig.parameterName)] = item.originalConfig.defaultValue;
                return;
            }

            var op;
            if (item.opField){
                var opField = this.down('#' + item.opField);
                if (opField.getValue())
                    op = opField.getValue();
            }
            else {
                op = 'eq';
            }
            var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(op);
            if(!filterType){
                //TODO: report?
                alert('ERROR: Unknown filter type: ' + op);
                return;
            }

            var val = item.getValue();

            if(item.originalConfig.formatter && typeof item.originalConfig.formatter === "function" ) {
                val = item.originalConfig.formatter(val);
            }

            if(Ext4.isArray(val)){
                if(val.length > 1)
                    op = 'in';

                var optimized = this.optimizeFilter(op, val, item);
                if(optimized){
                    op = optimized[0];
                    val = optimized[1];
                }

                val = val.join(';');
            }
            else if (val instanceof Date){
                var format = item.format || 'Y-m-d';
                val = val.format(format);
            }

            if (!Ext4.isEmpty(val) || !filterType.isDataValueRequired()){
                params[(dataRegionName + '.' + item.dataIndex + '~' + op)] = val;
            }
        }, this);
        return params;
    },

    optimizeFilter: function(op, values, field){
        if(field && field.store){
            if(values.length == field.store.getTotalCount()){
                op = null;
                values = [];
            }
            else if(values.length > (field.store.getTotalCount() / 2)){
                op = LABKEY.Filter.getFilterTypeForURLSuffix(op).getOpposite().getURLSuffix();
                var filters = Ext4.clone(field.store.filters.items);
                field.store.clearFilter();
                var newValues = [];
                field.store.each(function(rec){
                    var v = rec.get(field.displayField);
                    if(values.indexOf(v) == -1){
                        newValues.push(v);
                    }
                }, this);
                values = newValues;
                field.store.addFilter(filters, true);
            }
        }
        values = Ext4.unique(values);
        return [op, values];
    }
});
