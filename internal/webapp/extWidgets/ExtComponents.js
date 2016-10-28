/*
 * Copyright (c) 2011-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

//This will contain custom ext components
Ext4.namespace('LABKEY.ext4');


/**
 * This is a combobox containing operators as might be used in a search form.
 * @cfg {String} jsonType The jsonType for the field.  This determines the set of available filters
 * @cfg {Boolean} mvEnabled Specifies whether this field is missing-value enabled.  Defaults to false.
 * @cfg {String} initialValue The initial value for this field.  Must match a LABKEY filter URL suffix (ie. eq, in, startswith, etc.)
 * @cfg {Boolean} includeHasAnyValue
 * @cfg {Boolean} useLongDisplayText True to use the longDisplayText for the combo display value. Defaults to false.
 */
Ext4.define('LABKEY.ext.OperatorCombo', {
    extend: 'Ext.form.field.ComboBox',
    alias: 'widget.labkey-operatorcombo',
    config: {
        mvEnabled: false,
        jsonType: 'string',
        includeHasAnyValue: false,
        useLongDisplayText: false
    },
    initComponent: function(config){
        this.initConfig();

        if(this.value === undefined)
            this.value = LABKEY.Filter.getDefaultFilterForType(this.jsonType).getURLSuffix();

        Ext4.apply(this, {
            xtype: 'combo'
            ,valueField:'value'
            ,displayField:'text'
            ,typeAhead: false
            ,queryMode: 'local'
            ,editable: false
            ,store: this.createStore()
            ,plugins: ['combo-autowidth']
            ,expandToFitContent: true
        });

        this.callParent(arguments);
    },

    createStore: function () {
        var options = this.getStoreOptions();
        return Ext4.create('Ext.data.ArrayStore', {fields: ['value', 'text'], data: options });
    },

    getStoreOptions: function () {
        var found = false;
        var options = [];

        Ext4.each(LABKEY.Filter.getFilterTypesForType(this.jsonType, this.mvEnabled), function (filterType) {
            if (this.value && this.value == filterType.getURLSuffix())
                found = true;

            if (filterType.getURLSuffix())
            {
                var displayText = this.useLongDisplayText ? filterType.getLongDisplayText() : filterType.getDisplayText();
                options.push([filterType.getURLSuffix(), displayText]);
            }
        }, this);

        if (!found) {
            for (var key in LABKEY.Filter.Types) {
                var filterType = LABKEY.Filter.Types[key];
                if (filterType.getURLSuffix() == this.value) {
                    var displayText = this.useLongDisplayText ? filterType.getLongDisplayText() : filterType.getDisplayText();
                    options.unshift([filterType.getURLSuffix(), displayText]);
                    break;
                }
            }
        }

        if(this.includeHasAnyValue){
            options.unshift([null, this.emptyText || 'Has Any Value']);
        }

        return options;
    },

    changeJsonType: function(newJsonType) {
        this.jsonType = newJsonType;
        this.bindStore(this.createStore());
    }
});

/**
 * A combo extension that can be used instead of a checkbox.  It has the values true/false, and will use yes/no as the display values.
 */
Ext4.define('LABKEY.ext4.BooleanCombo', {
    extend: 'Ext.form.field.ComboBox',
    alias: 'widget.labkey-booleancombo',
    initComponent: function(){
        Ext4.apply(this, {
            displayField: 'displayText'
            ,valueField: 'value'
            ,triggerAction: 'all'
            ,listWidth: 200
            ,forceSelection: true
            ,queryMode: 'local'
            ,store: Ext4.create('Ext.data.ArrayStore', {
                fields: [
                    'value',
                    'displayText'
                ],
                idIndex: 0,
                data: [
                    [false, 'No'],
                    [true, 'Yes']
                ]
            })
        });

        this.callParent(arguments);

        if(this.includeNullRecord){
            this.store.add([[null, ' ']])
        }
    }
});

/**
 * A store extension that will load the views for a given query
 * @cfg containerPath
 * @cfg schemaName
 * @cfg queryName
 */
Ext4.define('LABKEY.ext4.ViewStore', {
    extend: 'Ext.data.ArrayStore',
    alias: 'widget.labkey-viewstore',
    constructor: function(){
        Ext4.apply(this, {
            loading: true,
            fields: [
                'value',
                'displayText'
            ],
            idIndex: 0,
            data: []
        });

        this.callParent(arguments);

        LABKEY.Query.getQueryViews({
            containerPath: this.containerPath
            ,queryName: this.queryName
            ,schemaName: this.schemaName
            ,successCallback: this.onViewLoad
            ,failure: LABKEY.Utils.onError
            ,scope: this
        });
    },
    onViewLoad: function(data){
        this.removeAll();

        var records = [];
        if(data && data.views && data.views.length){
            Ext4.each(data.views, function(s){
                if(s.hidden)
                    return;

                records.push([s.name, (s.name || 'Default')]);
            }, this);
        }

        if(!records.length){
            records.push([null, 'Default']);
        }

        this.loading = false;
        this.add(records);
        this.sort('value');
    }
});

/**
 * A combo extension that will display a list of views for a query.
 * @cfg containerPath
 * @cfg schemaName
 * @cfg queryName
 */
Ext4.define('LABKEY.ext4.ViewCombo', {
    extend: 'Ext.form.field.ComboBox',
    alias: 'widget.labkey-viewcombo',
    plugins: ['combo-autowidth'],
    constructor: function(config){
        Ext4.apply(this, {
            displayField: 'displayText'
            ,valueField: 'value'
            ,queryMode: 'local'
            ,editable: false
            ,store: Ext4.create('LABKEY.ext4.ViewStore', {
                containerPath: config.containerPath,
                schemaName: config.schemaName,
                queryName: config.queryName,
                listeners: {
                    scope: this,
                    add: function(){
                        if(this.value)
                            this.setValue(this.value);
                    }
                }
            })
            ,expandToFitContent: true
        });

        this.callParent(arguments);
    }
});

/**
 * A combo extension that will display LABKEY container filters.  Used in SearchPanel.
 */
Ext4.define('LABKEY.ext.ContainerFilterCombo', {
    extend: 'Ext.form.field.ComboBox',
    alias: 'widget.labkey-containerfiltercombo',
    initComponent: function(){
        Ext4.apply(this, {
            displayField: 'displayText'
            ,valueField: 'value'
            ,triggerAction: 'all'
            ,listConfig: {
                minWidth: 250
            }
            ,forceSelection: true
            ,mode: 'local'
            ,store: Ext4.create('Ext.data.ArrayStore', {
                fields: [
                    'value',
                    'displayText'
                ],
                idIndex: 0,
                data: [
                    ['', 'Current Folder Only'],
                    ['CurrentAndSubfolders', 'Current Folder and Subfolders']
                ]
            })
        });

        this.callParent(arguments);
    }
});