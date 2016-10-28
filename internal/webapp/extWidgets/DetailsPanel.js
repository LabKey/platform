/*
 * Copyright (c) 2010-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresCss('/extWidgets/Ext4DetailsPanel.css');
Ext4.namespace('LABKEY.ext');

/**
 * Creates a panel designed to present a details view of 1 record.  It can be created by either providing a store or a schema/query, in which
 * case the store will be automatically created.  If the store has more than 1 record, the first record in the store will automatically be loaded,
 * unless a record object or record index is provided using boundRecord.
 * @class LABKEY.ext.DetailsPanel
 * @cfg {object} store
 * @cfg {LABKEY.ext4.data.Store} store Can be supplied instead of schemaName / queryName
 * @cfg {string} titleField
 * @cfg {string} titlePrefix Defaults to 'Details'
 * @cfg {boolean} showBackBtn If false, the default 'back' button will not appear below the panel
 * @cfg {Ext4.data.Record / Integer} boundRecord Either a record object
 */
Ext4.define('LABKEY.ext.DetailsPanel', {
    extend: 'Ext.form.Panel',
    alias: 'widget.labkey-detailspanel',
    DEFAULT_FIELD_WIDTH: 600,

    initComponent: function(){
        Ext4.apply(this, {
            bodyStyle: this.bodyStyle || 'padding:5px;',
            border: Ext4.isDefined(this.border) ? this.border : true,
            frame: false,
            defaults: {
                border: false
            },
            buttonAlign: 'left',
            fieldDefaults: {
                labelWidth: 175
            },
            items: [{
                html: 'Loading...',
                border: false
            }],
            dockedItems: [{
                xtype: 'toolbar',
                ui: 'footer',
                dock: 'bottom',
                style: 'padding-top: 10px;'
            }]
        });

        if(this.showTitle !== false)
            this.title = Ext4.isDefined(this.titlePrefix) ?  this.titlePrefix : 'Details';

        this.callParent(arguments);

        this.store = this.getStore();
        this.mon(this.store, 'load', this.onStoreLoad, this);
        if (!this.store.getCount() && !this.store.isLoading()){
            this.store.load();
        }
        else
            this.onStoreLoad(this.store);
    },

    getStore: function(){
        if(!this.store.events){
            this.store.autoLoad = false;
            this.store = Ext4.create('LABKEY.ext4.data.Store', this.store);
        }

        return this.store;
    },

    onStoreLoad: function(store){
        this.removeAll();

        if (!this.store.getCount()){
            this.add({html: 'No records found'});
            return;
        }

        if (this.boundRecord)
            this.bindRecord(this.boundRecord);
        else
            this.bindRecord(store.getAt(0));
    },

    /**
     *
     * @param rec
     */
    bindRecord: function(rec){
        this.boundRecord = rec;

        this.addFieldsForRecord(rec);

        //configure buttons
        var bbar = this.down('toolbar[dock="bottom"]');
        if(bbar)
            bbar.removeAll();

        var url = LABKEY.ActionURL.getParameter('srcURL') || LABKEY.ActionURL.getParameter('returnURL');
        if(url && this.showBackBtn !== false){
            bbar.add({
                xtype: 'button',
                text: 'Back',
                hrefTarget: '_self',
                url: url
            });
        }

        //TODO: if TableInfo ever supports an auditURL, we should have option to show audit btn
//        if(LABKEY.ActionURL.getParameter('schemaName').match(/^study$/i) && LABKEY.ActionURL.getParameter('keyField').match(/^lsid$/i)){
//            panelCfg.items.push({
//                xtype: 'button',
//                handler: function(b){
//                    window.location = LABKEY.ActionURL.buildURL("query", "executeQuery", null, {
//                        schemaName: 'auditLog',
//                        'query.queryName': 'DatasetAuditEvent',
//                        'query.key1~eq': LABKEY.ActionURL.getParameter('key')
//                    });
//                },
//                text: 'Audit History'
//            });
//        }

    },

    addFieldsForRecord: function(rec){
        var toAdd = this.getDetailItems(rec);
        this.removeAll();
        this.add(toAdd);
    },

    getDetailItems: function(rec){
        var fields = this.store.getFields();
        var toAdd = [];
        fields.each(function(field){
            if (LABKEY.ext4.Util.shouldShowInDetailsView(field)){
                var value;

                if(rec.raw && rec.raw[field.name]){
                    value = rec.raw[field.name].displayValue !== undefined ? rec.raw[field.name].displayValue : rec.get(field.name);
                    if(value && field.jsonType == 'date'){
                        var format = field.extFormat || 'Y-m-d h:i A'; //NOTE: java date formats do not necessarily match Ext
                        value = Ext4.Date.format(value, format);
                    }

                    if(rec.raw[field.name].url)
                        value = '<a href="'+rec.raw[field.name].url+'" target="new">'+value+'</a>';
                }
                else
                    value = rec.get(field.name);

                toAdd.push({
                    fieldLabel: field.label || field.caption || field.name,
                    xtype: 'displayfield',
                    fieldCls: 'labkey-display-field',  //enforce line wrapping
                    width: this.DEFAULT_FIELD_WIDTH,
                    value: value
                });

                //NOTE: because this panel will render multiple rows as multiple forms, we provide a mechanism to append an identifier field
                if (this.titleField == field.name){
                    this.setTitle(this.title + ': '+value);
                }
            }
        }, this);

        return toAdd;
    },

    unbindRecord: function(){
        this.removeAll();

        var bbar = this.down('toolbar[dock="bottom"]');
        if(bbar)
            bbar.removeAll();

        delete this.boundRecord;
    }
});