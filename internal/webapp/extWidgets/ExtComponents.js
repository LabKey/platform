/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

//This will contain custom ext components
Ext4.namespace('LABKEY.ext4');


Ext4.define('LABKEY.ext4.RemoteGroup', {
    initGroup: function(field){
        Ext4.apply(this, {
            name: this.name || Ext4.id(),
            //layout: 'checkboxgroup',
            //autoHeight: true,
            //storeLoaded: false,
            items: [{
                name: 'placeholder',
                fieldLabel: 'Loading...'
            }],
           fieldRendererTpl : new Ext4.XTemplate('<tpl for=".">' +
                '{[values["' + this.valueField + '"] ? values["' + this.displayField + '"] : "'+ (this.nullCaption ? this.nullCaption : '[none]') +'"]}' +
                //allow a flag to display both display and value fields
                '<tpl if="'+this.showValueInList+'">{[(values["' + this.valueField + '"] ? " ("+values["' + this.valueField + '"]+")" : "")]}</tpl>'+
                '</tpl>'
            ).compile()
        });

        //we need to test whether the store has been created
        if(!this.store && this.queryName)
            this.store = LABKEY.ext.Ext4Helper.simpleLookupStore(this);

        if(!this.store){
            console.log('RemoteGroup requires a store');
            return;
        }

        if(this.store && !this.store.events)
            this.store = Ext4.create(this.store, 'labkey-store');

        if(!this.store || !this.store.model || !this.store.model.prototype.fields.getCount())
            this.mon(this.store, 'load', this.onStoreLoad, this, {single: true});
        else
            this.onStoreLoad();
    }

    ,onStoreLoad : function(store, records, success) {
        this.removeAll();
        if(!success){
            this.add('Error Loading Store');
        }
        else {
            var toAdd = [];
            var config;
            this.store.each(function(record, idx){
                config = {
                    boxLabel: (this.tpl ? this.fieldRendererTpl.apply(record.data) : record.get(this.displayField)),
                    inputValue: record.get(this.valueField),
                    disabled: this.disabled,
                    readOnly: this.readOnly || false
                };

                if(this instanceof Ext4.form.RadioGroup)
                    config.name = this.name+'_radio';

                toAdd.push(config);
            }, this);
            this.add(toAdd);
        }
    }
});


/* options:
valueField: the inputValue of the checkbox
displayField: the label of the checkbox
store
nullCaption
showValueInList

 */
Ext4.define('LABKEY.ext4.RemoteCheckboxGroup', {
    extend: 'Ext.form.CheckboxGroup',
    alias: 'widget.labkey-remotecheckboxgroup',
    initComponent: function(){
        this.initGroup(this);
        this.callParent(arguments);
    },
    mixins: {
        remotegroup: 'LABKEY.ext4.RemoteGroup'
    }
});


/* options:
valueField: the inputValue of the checkbox
displayField: the label of the checkbox
store
nullCaption
showValueInList

 */
Ext4.define('LABKEY.ext4.RemoteRadioGroup', {
    extend: 'Ext.form.RadioGroup',
    alias: 'widget.labkey-remoteradiogroup',
    initComponent: function(){
        this.initGroup(this);
        this.callParent(arguments);
    },
    mixins: {
        remotegroup: 'LABKEY.ext4.RemoteGroup'
    }
});



/**
 * This is a combobox containing operators as might be used in a search form.
 * @cfg {String} jsonType The jsonType for the field.  This determines the set of available filters
 * @cfg {Boolean} mvEnabled Specifies whether this field is missing-value enabled.  Defaults to false.
 * @cfg {String} initialValue The initial value for this field.  Must match a LABKEY filter URL suffix (ie. eq, in, startswith, etc.)
 * @cfg {Boolean} includeHasAnyValue
 */
Ext4.define('LABKEY.ext.OperatorCombo', {
    extend: 'Ext.form.field.ComboBox',
    alias: 'widget.labkey-operatorcombo',
    config: {
        mvEnabled: false,
        jsonType: 'string',
        includeHasAnyValue: false
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
            ,store: this.getStore()
            ,plugins: ['combo-autowidth']
            ,expandToFitContent: true
        });

        this.callParent(arguments);
    },

    getStore: function () {
        var found = false;
        var options = [];
        Ext4.each(LABKEY.Filter.getFilterTypesForType(this.jsonType, this.mvEnabled), function (filterType) {
            if (this.value && this.value == filterType.getURLSuffix())
                found = true;
            if (filterType.getURLSuffix())
                options.push([filterType.getURLSuffix(), filterType.getDisplayText()]);
        });

        if (!found) {
            for (var key in LABKEY.Filter.Types) {
                var filterType = LABKEY.Filter.Types[key];
                if (filterType.getURLSuffix() == this.value) {
                    options.unshift([filterType.getURLSuffix(), filterType.getDisplayText()]);
                    break;
                }
            }
        }

        if(this.includeHasAnyValue){
            options.unshift([null, this.emptyText || 'Has Any Value']);
        }

        return Ext4.create('Ext.data.ArrayStore', {fields: ['value', 'text'], data: options });
    }
});


/**
 * A simple extension of an ext button that will render to appear like simple link.
 * The advantage of extending button is that it supports handlers, custom menus, etc.
 * @cfg linkPrefix
 * @cfg linkSuffix
 * @cfg linkCls
 * @cfg linkTarget
 * @cfg tooltip
 */
Ext4.define('LABKEY.ext.LinkButton', {
    extend: 'Ext.button.Button',
    alias: 'widget.labkey-linkbutton',
    initComponent: function(){
        this.callParent(arguments);

        this.renderData = this.renderData || {};
        Ext4.apply(this.renderData, {
            linkPrefix: this.linkPrefix,
            linkSuffix: this.linkSuffix,
            linkCls: this.linkCls,
            linkTarget: this.linkTarget,
            tooltip: this.tooltip
        });

        //prevent double clicks
        this.on('click', function(btn){
            btn.setDisabled(true);
            btn.setDisabled.defer(100, this, [false]);
        })
    },
    showBrackets: true,
    renderSelectors: {
        btnEl: 'a'
    },
    baseCls: 'linkbutton',
    renderTpl:
        '<em id="{id}-btnWrap" class="{splitCls}">' +
            '{linkPrefix}' +
            '<a id="{id}-btnEl" role="link" ' +
                '<tpl if="linkCls">class="{linkCls}"</tpl>' +
                '<tpl if="href">href="{href}" </tpl>' +
                '<tpl if="linkTarget">target="{linkTarget}" </tpl>' +
                '<tpl if="tooltip">data-qtip="{tooltip}"</tpl>' +
                '<tpl if="tabIndex"> tabIndex="{tabIndex}"</tpl>' +
            '>' +
            '<span id="{id}-btnInnerEl" class="{baseCls}-inner">{text}</span>' +
            '<span id="{id}-btnIconEl" class="{baseCls}-icon"></span>' +
            '</a>' +
            '{linkSuffix}' +
        '</em>'
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