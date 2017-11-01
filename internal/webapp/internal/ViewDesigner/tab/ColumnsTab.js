/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.internal.ViewDesigner.model.Column', {

    extend: 'LABKEY.internal.ViewDesigner.model.FieldKey',

    fields: [
        {name: 'name'},
        {name: 'title'},
        {name: 'aggregate'}
    ],

    proxy: {
        type: 'memory',
        reader: {
            type: 'json',
            root: 'columns'
        }
    }
});

Ext4.define('LABKEY.internal.ViewDesigner.tab.ColumnsTab', {

    extend: 'LABKEY.internal.ViewDesigner.tab.BaseTab',

    cls: 'test-columns-tab',

    baseTitle: 'Selected Fields',

    initComponent : function() {

        // Load aggregates from the customView.analyticsProviders Array.
        // We use the columnStore to track aggregates since aggregates and columns are 1-to-1 at this time.
        // By adding the aggregate to the columnStore the columnsList can render it.
        if (!Ext4.isEmpty(this.customView.analyticsProviders)) {

            var columnStore = this.getColumnStore();
            for (var i = 0; i < this.customView.analyticsProviders.length; i++) {
                var agg = this.customView.analyticsProviders[i];
                if (!agg.fieldKey || !agg.isSummaryStatistic) {
                    continue;
                }
                var columnRecord = columnStore.getById(agg.fieldKey);
                if (!columnRecord) {
                    continue;
                }

                this.getAggregateStore().add(agg);
            }
        }

        this.callParent();
    },

    getAggregateStore : function() {
        if (!this.aggregateStore) {
            this.aggregateStore = this.createAggregateStore();
        }

        return this.aggregateStore;
    },

    getColumnStore : function() {
        if (!this.columnStore) {
            this.columnStore = Ext4.create('LABKEY.internal.ViewDesigner.store.FieldKey', {
                model: 'LABKEY.internal.ViewDesigner.model.Column',
                data: this.customView
            });
        }

        return this.columnStore;
    },
    
    getList : function() {
        if (!this.listItem) {

            var me = this;

            this.listItem = Ext4.create('Ext.view.View', {
                cls: 'labkey-customview-list',
                store: this.getColumnStore(),
                deferEmptyText: false,
                multiSelect: false,
                height: 196,
                autoScroll: true,
                overItemCls: 'x4-view-over',
                itemSelector: '.labkey-customview-item',
                tpl: new Ext4.XTemplate(
                    '<tpl if="length == 0">',
                    '  <div class="labkey-customview-empty">No fields selected.</div>',
                    '</tpl>',
                    '<tpl for=".">',
                    '<table width="100%" cellspacing="0" cellpadding="0" class="labkey-customview-item labkey-customview-columns-item" fieldKey="{fieldKey:htmlEncode}">',
                    '  <tr>',
                    '    <td class="labkey-grab"></td>',
                    '    <td><div class="item-caption">{[this.getFieldCaption(values)]}</div></td>',
                    '    <td valign="top"><div class="item-aggregate">{[this.getAggregateCaption(values)]}</div></td>',

                    /* Clicking this will fire the onToolGear() function */
                    '    <td width="15px" valign="top"><div class="labkey-tool-gear fa fa-cog" title="Edit title"></div></td>',

                    /* Clicking this will fire the onToolClose() function */
                    '    <td width="15px" valign="top"><span class="labkey-tool-close fa fa-times" title="Remove column"></span></td>',

                    /* Spacer on the end to prevent tools from appearing under scrollbar */
                    '    <td width="5px"><span>&nbsp;</span></td>',
                    '  </tr>',
                    '</table>',
                    '</tpl>',
                    {
                        getFieldCaption : function(values) {
                            if (values.title) {
                                return Ext4.htmlEncode(values.title);
                            }

                            var fieldMeta = me.fieldMetaStore.getById(values.fieldKey);
                            if (fieldMeta) {
                                var caption = fieldMeta.get('caption');
                                if (caption && caption != '&nbsp;') {
                                    return caption; // caption is already htmlEncoded
                                }
                                return Ext4.htmlEncode(fieldMeta.get('name'));
                            }
                            return Ext4.htmlEncode(values.name) + " <span class='labkey-error'>(not found)</span>";
                        },

                        getAggregateCaption : function(values) {
                            var fieldKey = values.fieldKey,
                                labels = [],
                                caption = '';

                            me.getAggregateStore().each(function(rec) {
                                if (rec.get('fieldKey') === fieldKey) {
                                    labels.push(LABKEY.analyticProviders[rec.get('name')] || rec.get('name'));
                                }
                            });

                            labels = Ext4.Array.unique(labels);

                            if (labels.length) {
                                caption = Ext4.htmlEncode(labels.join(', '));
                            }

                            return caption;
                        }
                    }
                ),
                listeners: {
                    render: function(view) {
                        this.addDataViewDragDrop(view, 'columnsTabView');
                    },
                    scope: this
                }
            });
        }

        return this.listItem;
    },

    createAggregateStore: function() {

        var MODEL_CLASS = 'Aggregate';

        if (!Ext4.ModelManager.isRegistered(MODEL_CLASS)) {
            Ext4.define(MODEL_CLASS, {
                extend: 'Ext.data.Model',
                fields: [{name: 'fieldKey'}, {name: 'name'}],
                idProperty: {name: 'id', convert: function(v, rec){
                    return rec.get('fieldKey').toUpperCase() + rec.get('name');
                }}
            });
        }

        return Ext4.create('Ext.data.Store', {
            model: MODEL_CLASS,
            remoteSort: true
        });
    },

    onToolGear : function(index) {
        var columnRecord = this.getColumnStore().getAt(index);
        var metadataRecord = this.fieldMetaStore.getById(columnRecord.get('id'));
        var columnsList = this.getList();

        var win = Ext4.create('Ext.window.Window', {
            title: "Edit Title",
            resizable: false,
            constrain: true,
            constrainHeader: true,
            modal: true,
            border: false,
            closable: true,
            closeAction: 'hide',
            items: {
                xtype: 'form',
                border: false,
                padding: 10,
                items: [{
                    xtype: "textfield",
                    itemId: "titleField",
                    fieldLabel: 'Title',
                    labelWidth: 40,
                    name: "title",
                    allowBlank: true,
                    width: 330
                }]
            },
            buttons: [{
                text: "OK",
                handler: function() {
                    var title = win.down('#titleField').getValue();
                    title = title ? title.trim() : "";
                    win.columnRecord.set("title", !Ext4.isEmpty(title) ? title : undefined);

                    columnsList.refresh();
                    win.hide();
                }
            },{
                text: "Cancel",
                handler: function() { win.hide(); }
            }],

            initEditForm : function(columnRecord, metadataRecord) {
                this.columnRecord = columnRecord;
                this.metadataRecord = metadataRecord;

                this.setTitle("Edit title for '" + Ext4.htmlEncode(this.columnRecord.get('fieldKey')) + "'");
                this.down('#titleField').setValue(this.columnRecord.get("title"));

                //columnsList
                this.columnRecord.store.fireEvent('datachanged', this.columnRecord.store)

            }
        });

        win.render(document.body);
        win.initEditForm(columnRecord, metadataRecord);
        win.show();
    },

    createDefaultRecordData : function(fieldKey) {
        var o = {};

        if (fieldKey) {
            o.fieldKey = fieldKey;
            var fk = LABKEY.FieldKey.fromString(fieldKey);
            var record = this.fieldMetaStore.getById(fieldKey);
            if (record) {
                o.name = record.caption || fk.name;
            }
            else {
                o.name = fk.name + " (not found)";
            }
        }

        return o;
    },

    hasField : function(fieldKey) {
        // Find fieldKey using case-insensitive comparison
        return this.getColumnStore().find("fieldKey", fieldKey, 0, false, false) != -1;
    },

    validate : function() {
        if (this.getColumnStore().getCount() == 0) {
            LABKEY.Utils.alert('Selection required', 'You must select at least one field to display in the grid.');
            return false;

            // XXX: check each fieldKey is selected only once
        }
        return true;
    }
});
