/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

//This will contain custom ext components
Ext.namespace('LABKEY.ext4');


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
                '{[values["' + this.valueField + '"] ? values["' + this.displayField + '"] : "'+ (this.lookupNullCaption ? this.lookupNullCaption : '[none]') +'"]}' +
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
lookupNullCaption
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
lookupNullCaption
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
 * @cfg {LABKEY.Query.FieldMetaData} meta The metadata object for the field to display
 * @cfg {String} initialValue The initial value for this field
 */
Ext4.define('LABKEY.ext.OperatorCombo', {
    extend: 'Ext.form.field.ComboBox',
    alias: 'widget.labkey-operatorcombo',
    initComponent: function(config){
        this.meta = this.meta || {};
        this.meta.jsonType = this.meta.jsonType || 'string';

        if(!this.initialValue){
            this.initialValue = LABKEY.Filter.getDefaultFilterForType(this.meta.jsonType).getURLSuffix();
        }

        Ext4.apply(this, {
            xtype: 'combo'
            ,valueField:'value'
            ,displayField:'text'
            ,listConfig: {
                minWidth: 250
            }
            ,typeAhead: false
            ,queryMode: 'local'
            ,editable: false
            ,value: this.initialValue
            ,store: this.setStore(this.meta, this.initialValue)
        });

        this.callParent(arguments);
    },
    setStore: function (meta, value) {
        var found = false;
        var options = [];
        if (meta.jsonType)
            Ext4.each(LABKEY.Filter.getFilterTypesForType(meta.jsonType, meta.mvEnabled), function (filterType) {
                if (value && value == filterType.getURLSuffix())
                    found = true;
                if (filterType.getURLSuffix())
                    options.push([filterType.getURLSuffix(), filterType.getDisplayText()]);
            });

        if (!found) {
            for (var key in LABKEY.Filter.Types) {
                var filterType = LABKEY.Filter.Types[key];
                if (filterType.getURLSuffix() == value) {
                    options.unshift([filterType.getURLSuffix(), filterType.getDisplayText()]);
                    break;
                }
            }
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
    constructor: function(config){
        Ext4.apply(this, {
            displayField: 'displayText'
            ,valueField: 'value'
            ,queryMode: 'local'
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
        Ext.apply(this, {
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




/**
 * An extension to the Ext4 combobox.  The primary features this provides are more control over
 * how the display values are rendered and auto-resizing of the pick list based on the size of
 * the items.
 * @cfg {Boolean} showValueInList If true, the underlying value will also be shown in the pick menu, in addition to the display value.
 * @cfg {String} lookupNullCaption A string that will be used at the display text if the displayField is blank.  Defaults to '[none]'
 * @cfg {Boolean} lazyCreateStore If the combo is created with a store config object, by default the store will not be instantiated until the combo list is viewed.  If this is set to false, the store will be created immediately.  this can be useful in order to ensure data is loaded.
 */
Ext4.define('LABKEY.ext4.ComboBox', {
    extend: 'Ext.form.field.ComboBox',
    alias: 'widget.labkey-combo',
    lazyCreateStore: true,
    initComponent: function(){
        this.listConfig = this.listConfig || {};
        this.listConfig.innerTpl =
            //allow a custom null caption, defaults to '[none]'
            '{[(typeof values === "string" ? values : (values["' + this.displayField + '"] ? values["' + this.displayField + '"] : '+(Ext4.isDefined(this.lookupNullCaption) ? '"' + '1'+this.lookupNullCaption + '"' : '"[none]"')+'))]}' +
            //allow a flag to display both display and value fields
            (this.showValueInList ? '{[values["' + this.valueField + '"] ? " ("+values["' + this.valueField + '"]+")" : ""]}' : '') +
            (this.multiSelect ? '<tpl if="xindex < xcount">' + '{[(values["' + this.displayField + '"] ? "'+this.delimiter+'" : "")]}' + '</tpl>' : '') +
            //space added so empty strings render with full height
            '&nbsp;'
        this.listConfig.getInnerTpl = function(){
            return this.innerTpl;
        }

        //create store if config object is supplied
        if(!this.store.events && this.lazyCreateStore === false){
            this.store = Ext4.create('LABKEY.ext4.Store', this.store);
        }

        //auto list width
        this.listConfig.listeners = this.listConfig.listeners || {};
        Ext4.apply(this.listConfig.listeners, {
            scope: this
            //TODO: broken in 4.1
            //viewready: this.resizeToFitContent
        });

        //note: will be addressed in resizeToFitContent()
        this.matchFieldWidth = true;

        this.callParent();
    },

    onLoad: function(){
        this.callParent(arguments);

        //will only run if picker has been created
        this.resizeToFitContent();
    },

    //this is designed to autosize the width of the BoundList.  the hope is that by measuring the list as a whole, we avoid measuring
    //each list item, which can be extremely expensive.
    resizeToFitContent: function(){
        //NOTE: prematurely creating the picker can have bad consequences, so we wait until it has been loaded to resize
        if(this.picker && this.picker.el){
            var picker = this.getPicker();
            var el = this.getPicker().el;
            if(!this.metrics)
                this.metrics = new Ext4.util.TextMetrics(this.inputEl);

            var v = el.dom.innerHTML;
            var w = this.metrics.getWidth(v) + 10 + this.getTriggerWidth(); /* add extra padding, plus width of scroll bar */
            var fieldWidth = this.inputEl.getWidth() + this.getTriggerWidth();
            w = Math.max(w, fieldWidth);

            //if width is less than fieldwidth, expand.  otherwise turn off or Ext will coerce the list to match the field
            this.matchFieldWidth = fieldWidth >= w;
            picker.setWidth(w);
        }
    },

    //allows value to be set if store has not yet loaded
    setValue: function(val){
        if(!this.store || !this.store.model || !this.store.model.prototype.fields.getCount()){
            this.value = val;
            this.mon(this.store, 'load', function(){this.setValue(val);this.validate();}, this, {single: true});
        }
        else
            this.callParent(arguments);
    }
});

/*
    This plugin is designed to allow users to add options to combos.  It adds an item on the list called 'Other'.  If they pick this, a prompt comes
    up, allowing the user to enter a value.  this value is added to the store.  it only works well with stores that have 1 column, when the displayField
    equals the valueField (otherwise we need to somehow resolve value based on display)
 */
Ext4.define('LABKEY.ext.UserEditableComboPlugin', {
    extend: 'Ext.AbstractPlugin',
    pluginId: 'labkey-usereditablecombo',
    alias: 'plugin.labkey-usereditablecombo',
    init: function(combo) {
        this.combo = combo;
        if((combo instanceof Ext4.form.ComboBox && !combo.multiSelect )){   //&& combo.displayField==combo.valueField
            Ext4.apply(combo, {
                onListSelectionChange: function(list, records){
                    if(records.length!=1)
                        return;

                    var val = records[0].get(this.displayField);

                    if(val == 'Other'){
                        Ext4.MessageBox.prompt('Enter Value', 'Enter value:', this.addNewValue, this);
                    }
                    else {
                        this.callParent(arguments);
                    }
                },
                setValue: function(v){
                    if(Ext4.isArray(v)){
                        if(v.length == 1)
                            var r = this.findRecord(this.displayField, v[0]);
                            if(!r){
                                console.log('adding: '+v);
                                console.log(v[0])
                                this.addRecord(v[0]);
                            }
                            else
                                this.callParent(arguments);
                    }
                    else
                        this.callParent(arguments);
                },
                addNewValue: function(btn, val){
                    this.addRecord(val);
                    console.log('add record')
                    this.setValue(val);
                    this.fireEvent('change', this, val, 'Other');
                },
                addRecord: function(value){
                    if(!value)
                        return;

                    var data = {};
                    data[this.displayField] = value;
                    if(this.displayField!=this.valueField){
                        data[this.displayField] = value;
                    }

                    if(!this.store || !this.store.model || !this.store.model.prototype.fields.getCount()){
                        this.mon(this.store, 'load', function(store){
                            console.log('load')
                            this.addRecord(value);
                        }, this, {single: true});
                        console.log('unable to add record: '+this.store.storeId+'/'+value);
                        console.log(this.store.proxy.model.prototype.fields.getCount());
                        return;
                    }
                    this.store.add((this.store.createModel(data)));

                    if(this.view){
                        this.view.setStore(this.store);
                        this.view.refresh()
                    }
                }
            });

            if(combo.store.model || !combo.store.model.prototype.fields.length)
                combo.addRecord('Other');
            else {
                if (!combo.store.on)
                    combo.store = Ext4.create(combo.store);

                combo.mon(combo.store, 'load', function(){
                    combo.addRecord('Other');
                }, combo);
            }
        }
    }
});
