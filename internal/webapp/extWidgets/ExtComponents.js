/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
            this.store = LABKEY.ext.MetaHelper.simpleLookupStore(this);

        if(!this.store){
            console.log('RemoteGroup requires a store');
            return;
        }

        if(this.store && !this.store.events)
            this.store = Ext4.create(this.store, 'labkey-store');

        if(!this.store.hasLoaded())
            this.store.on('load', this.onStoreLoad, this, {single: true});
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


//The purpose of this field is to allow a display value to hold one value, but display another
//experimental
Ext4.define('LABKEY.ext4.DisplayField', {
    extend: 'Ext.form.field.Display',
    alias: 'widget.labkey-displayfield',
    initComponent: function()
    {
        if(!this.fieldMetadata){
            console.log('must provide the field metadata');
            return;
        }
        this.callParent(arguments);
    },
    getDisplayValue: function(v){
        if(this.lookup && this.lookups !== false){
            return v;
        }
        else if(Ext4.isDate(v)){
            return this.format ? v.format(this.format) : v.format('Y-m-d H:i');
        }
        else
            return v;
    },

    setValue: function(v){
        this.rawValue = v;
        this.displayValue = this.getDisplayValue(v);
        this.callParent([this.displayValue]);
    },

    getValue: function(){
        return this.rawValue ? this.rawValue : this.callParent();
    }
});


//this is a combobox containing operators as might be used in a search form
Ext4.define('LABKEY.ext.OperatorCombo', {
    extend: 'Ext.form.field.ComboBox',
    alias: 'widget.labkey-operatorcombo',
    initComponent: function(config){
        this.meta = this.meta || {};
        this.meta.jsonType = this.meta.jsonType || 'string';

        if(!this.initialValue){
            switch(this.meta.jsonType){
                case 'int':
                case 'float':
                    this.initialValue = 'eq';
                    break;
                case 'date':
                    this.initialValue = 'dateeq';
                    break;
                case 'boolean':
                    this.initialValue = 'startswith';
                    break;
                default:
                    this.initialValue = 'startswith';
                    break;
            }
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

Ext4.define('LABKEY.ext4.ViewCombo', {
    extend: 'Ext.form.field.ComboBox',
    alias: 'widget.labkey-viewcombo',
    constructor: function(config){
        Ext4.apply(this, {
            displayField: 'displayText'
            ,valueField: 'value'
            ,queryMode: 'local'
            ,store: Ext4.create('LABKEY.ext4.ViewStore', {
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




/*
  config:
  showValueInList
  lookupNullCaption

*/
//TODO: explore how to add the list width auto-size plugin
Ext4.define('LABKEY.ext4.ComboBox', {
    extend: 'Ext.form.field.ComboBox',
    alias: 'widget.labkey-combo',
    initComponent: function(){
        this.listConfig = this.listConfig || {};
        this.listConfig.itemTpl = Ext4.create('Ext.XTemplate',
            '<tpl for=".">' +
                '{[(typeof values === "string" ? values : (values["' + this.displayField + '"] ? values["' + this.displayField + '"] : '+(Ext4.isDefined(this.lookupNullCaption) ? '"' + '1'+this.lookupNullCaption + '"' : '"[none]"')+'))]}' +
                //allow a flag to display both display and value fields
                (this.showValueInList ? '{[values["' + this.valueField + '"] ? " ("+values["' + this.valueField + '"]+")" : ""]}' : '') +
                (this.multiSelect ? '<tpl if="xindex < xcount">' + '{[(values["' + this.displayField + '"] ? "'+this.delimiter+'" : "")]}' + '</tpl>' : '') +

                '&nbsp;' + //space added so empty strings render with full height
            '</tpl>'
        ).compile();

        this.callParent();

    },
    setValue: function(v){
        //save the value if it is set prior to loading
        if(this.store && !this.store.hasLoaded()){
            this.initialValue = v;
        }

        this.callParent(arguments);
    }
});


//Ext4.define('LABKEY.ext.UserEditableComboPlugin', {
//    extend: 'Ext.util.Observable',
//    init: function(combo) {
//        if((combo instanceof Ext.form.ComboBox)){
//            Ext.apply(combo, {
//                onSelect: function(cmp, idx){
//                    var val;
//                    if(idx)
//                        val = this.store.getAt(idx).get(this.valueField);
//
//                    if(val == 'Other'){
//                        Ext.MessageBox.prompt('Enter Value', 'Enter value:', this.addNewValue, this);
//                    }
//                    LABKEY.ext.ComboBox.superclass.onSelect.apply(this, arguments);
//                },
//                setValue:     function(v){
//                    var r = this.findRecord(this.valueField, v);
//                    if(!r){
//                        this.addRecord(v, v);
//                    }
//                    LABKEY.ext.ComboBox.superclass.setValue.apply(this, arguments);
//                },
//                addNewValue: function(btn, val){
//                    this.addRecord(val);
//                    this.setValue(val);
//                    this.fireEvent('change', this, val, 'Other');
//                },
//                addRecord: function(value){
//                    if(!value)
//                        return;
//
//                    var data = {};
//                    data[this.valueField] = value;
//                    if(this.displayField!=this.valueField){
//                        data[this.displayField] = value;
//                    }
//
//                    if(!this.store || !this.store.fields){
//                        this.store.on('load', function(store){
//                            this.addRecord(value);
//                        }, this, {single: true});
//                        console.log('unable to add record: '+this.store.storeId+'/'+value);
//                        return;
//                    }
//                    this.store.add((new this.store.recordType(data)));
//
//                    if(this.view){
//                        this.view.setStore(this.store);
//                        this.view.refresh()
//                    }
//                }
//            });
//
//            if(combo.store.fields)
//                combo.addRecord('Other');
//            else {
//                if (!combo.store.on)
//                    combo.store = Ext.ComponentMgr.create(combo.store);
//
//                combo.store.on('load', function(){
//                    combo.addRecord('Other');
//                }, this);
//            }
//        }
//    }
//});
