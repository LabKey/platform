/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace('LABKEY.ext4');

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
    initComponent: function(){
        Ext4.apply(this, {
            title: this.title,
            bodyStyle: 'padding: 5px;',
            layout: {
                type: 'table',
                columns: 3
            },
            fieldDefaults: {
                labelWidth: 150
            },
            buttons: [
                {text: 'Submit', scope: this, handler: this.onSubmit}
            ],
            defaults: {
                border: false,
                bodyBorder: false
            },
            items: [{html: 'Loading...'}],
            border: true,
            bodyBorder: false,
            width: 492,
            autoHeight: true,
            keys: [{
                key: Ext4.EventObject.ENTER,
                handler: this.onSubmit,
                scope: this
            }]
        });

        this.callParent();

        this.store = this.store || Ext4.create('LABKEY.ext4.Store', {
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

        if(LABKEY.ext.Ext4Helper.hasStoreLoaded(this.store))
            this.onLoad(this.store, true);
        else
            this.mon(this.store, 'load', this.onLoad, this, {single: true});

        Ext4.Ajax.timeout = 30000; //in milliseconds
    },

    onLoad: function(store, success){
        this.removeAll();

        if (!success || !store || !LABKEY.ext.Ext4Helper.hasStoreLoaded(store)){
            this.add({tag: 'div', html: 'Error loading data'});
            return;
        }

        var toAdd = [];

        store.getFields().each(function(f){
            toAdd.push(this.addRow(f));
        }, this);

        if (this.showContainerFilter){
            toAdd.push({
                html: 'Container Filter:', width: 125
            },{
                xtype: 'labkey-containerfiltercombo'
                ,width: 165
                ,value: this.defaultContainerFilter || ''
                ,fieldType: 'containerFilterName'
                ,itemId: 'containerFilterName'
            });
            toAdd.push({html: ''});
        }

        if (this.allowSelectView!==false){
            toAdd.push({
                html: 'View:', width: 125
            },{
                xtype: 'labkey-viewcombo'
                ,containerPath: this.containerPath
                ,queryName: this.queryName
                ,schemaName: this.schemaName
                ,width: 165
                ,value: this.defaultViewName || ''
                ,fieldType: 'viewName'
                ,itemId: 'viewNameField'
            });
            toAdd.push({html: ''});
        }

        this.add(toAdd);
    },

   addRow: function(meta){
        if(meta.inputType == 'textarea'){
            meta.inputType = 'textbox';
        }

       var rows = [];
        if (!meta.hidden && meta.selectable !== false){
            var replicates = 1;
            if(meta.duplicate)
                replicates = 2;

            for(var i=0;i<replicates;i++)
                rows.push(this.createRow(meta));

        }

       return rows;
   },

   createRow: function(meta){
       var row = [];
        if (meta.lookup && meta.lookups!==false){
            meta.includeNullRecord = false;
            meta.editorConfig = meta.editorConfig || {};
            meta.editorConfig.multiSelect = true;
            meta.editorConfig.delimiter = ';';
        }

        if(meta.jsonType == 'boolean'){
            meta.editorConfig = meta.editorConfig || {};
            meta.editorConfig.includeNullRecord = true;
            meta.xtype = 'labkey-booleancombo';
        }

        //create the field
        var theField = LABKEY.ext.Ext4Helper.getFormEditorConfig(meta);
       theField.fieldLabel = null;

        theField.disabled = false;

        //the label
        row.push({html: meta.caption + ':', width: 150});

        Ext4.apply(theField, {
            nullable: true,
            allowBlank: true,
            width: 150,
            isSearchField: true,
            style: 'padding-left: 5px;'
        });

       //NOTE: if the field is a lookup, the dataRegion will display/filter this field on the value
       //therefore on submit, we actually filter on display value, not raw value
       //Issue: 13723
       if(meta.lookup){
            theField.dataIndex = meta.name + '/' + meta.lookup.displayColumn;
            theField.valueField = theField.displayField;
       }

        //the operator
        if(meta.jsonType=='boolean')
            row.push({});
        else if (theField.xtype == 'labkey-combo'){
            var id = Ext4.id();
            theField.opField = id;
            row.push({
                xtype: 'displayfield',
                value: 'in',
                itemId: id,
                hidden: true
            });
        }
        else {
            var id = Ext4.id();
            theField.opField = id;
            row.push({
                xtype: 'labkey-operatorcombo',
                meta: meta,
                itemId: id,
                width: 165
            });
        }

        //the field itself
        row.push(theField);
       return row;
    },
    onSubmit: function(){
        var params = {
            schemaName: this.schemaName,
            'query.queryName': this.queryName
        };

        var cf = this.down('#containerFilterName');
        if (cf && cf.getValue()){
            params['query.containerFilterName'] = cf.getValue();
        }
        else if(this.defaultContainerFilter){
            params['query.containerFilterName'] = this.defaultContainerFilter;
        }

        var vf = this.down('#viewNameField');
        if (vf && vf.getValue()){
            params['query.viewName'] = vf.getValue();
        }

        this.items.each(function(item){
            if(!item.isSearchField)
                return;

            var op;
            if (item.opField){
                var opField = this.down('#'+item.opField);
                if(opField.getValue())
                    op = opField.getValue();
            }
            else {
                op = 'eq';
            }
            var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(op);
            var val = item.getValue();
            if(Ext4.isArray(val))
                val = val.join(';');

            if (!Ext4.isEmpty(val) || !filterType.isDataValueRequired()){
                params[('query.' + item.dataIndex + '~' + op)] = val;
            }
        }, this);

        window.location = LABKEY.ActionURL.buildURL(
            'query',
            'executeQuery.view',
            (this.containerPath || LABKEY.ActionURL.getContainer()),
            params
        );
    }
});