/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.internal.ViewDesigner.tab.ColumnsTab', {

    extend: 'LABKEY.internal.ViewDesigner.tab.BaseTab',

    constructor : function (config) {

        this.customView = config.customView;
        var fieldMetaStore = this.fieldMetaStore = config.fieldMetaStore;

        this.columnStore = Ext4.create('Ext.data.Store', {
            fields: ['name', 'fieldKey', 'title', 'aggregate'],
            data: this.customView,
            remoteSort: true,
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json',
                    root: 'columns',
                    idProperty: function (json) {
                        return json.fieldKey.toUpperCase()
                    }
                }
            }
        });

        this.aggregateStore = this.createAggregateStore();

        // Load aggregates from the customView.aggregates Array.
        // We use the columnStore to track aggregates since aggregates and columns are 1-to-1 at this time.
        // By adding the aggregate to the columnStore the columnsList can render it.
        if (this.customView.aggregates)
        {
            for (var i = 0; i < this.customView.aggregates.length; i++)
            {
                var agg = this.customView.aggregates[i];
                if (!agg.fieldKey && !agg.type) {
                    continue;
                }
                var columnRecord = this.columnStore.getById(agg.fieldKey.toUpperCase());
                if (!columnRecord) {
                    continue;
                }

                this.aggregateStore.add(agg);
            }
        }

        var aggregateStore = this.aggregateStore;

        config = Ext4.applyIf({
            cls: "test-columns-tab",
            layout: "fit",
            items: [{
                xtype: "panel",
                cls: 'themed-panel2',
                title: "Selected Fields",
                border: false,
                width: 200,
                style: 'border-left-width: 1px',
                layout: {
                    type: "hbox",
                    align: "stretch"
                },
                items: [{
                    xtype: "dataview",
                    itemId: "columnsList",
                    cls: "labkey-customview-list",
                    flex: 1,
                    store: this.columnStore,
                    emptyText: "No fields selected",
                    deferEmptyText: false,
                    multiSelect: false,
                    height: 250,
                    autoScroll: true,
                    overItemCls: "x4-view-over",
                    itemSelector: ".labkey-customview-item",
                    tpl: new Ext4.XTemplate(
                            '<tpl for=".">',
                            '<table width="100%" cellspacing="0" cellpadding="0" class="labkey-customview-item labkey-customview-columns-item" fieldKey="{fieldKey:htmlEncode}">',
                            '  <tr>',
                            '    <td class="labkey-grab"></td>',
                            '    <td><div class="item-caption">{[this.getFieldCaption(values)]}</div></td>',
                            '    <td><div class="item-aggregate">{[this.getAggegateCaption(values)]}</div></td>',

                            /* Clicking this will fire the onToolGear() function */
                            '    <td width="15px" valign="top"><div class="labkey-tool labkey-tool-gear" title="Edit"></div></td>',

                            /* Clicking this will fire the onToolClose() function */
                            '    <td width="15px" valign="top"><span class="labkey-tool labkey-tool-close" title="Remove column"></span></td>',
                            '  </tr>',
                            '</table>',
                            '</tpl>',
                            {
                                getFieldCaption : function (values) {
                                    if (values.title) {
                                        return Ext4.htmlEncode(values.title);
                                    }

                                    var fieldKey = values.fieldKey;
                                    var fieldMeta = fieldMetaStore.getById(fieldKey.toUpperCase());
                                    if (fieldMeta) {
                                        // caption is already htmlEncoded
                                        if (fieldMeta.data.caption && fieldMeta.data.caption != "&nbsp;") {
                                            return fieldMeta.data.caption;
                                        }
                                        return Ext4.htmlEncode(fieldMeta.data.name);
                                    }
                                    return Ext4.htmlEncode(values.name) + " <span class='labkey-error'>(not found)</span>";
                                },

                                getAggegateCaption : function (values) {
                                    var fieldKey = values.fieldKey,
                                        labels = [],
                                        caption = '';

                                    aggregateStore.each(function(rec) {
                                        if (rec.get('fieldKey') === fieldKey) {
                                            labels.push(rec.get('type'));
                                        }
                                    });

                                    labels = Ext4.Array.unique(labels);

                                    if (labels.length) {
                                        caption = Ext4.htmlEncode(labels.join(','));
                                    }

                                    return caption;
                                }
                            }
                    ),
                    listeners: {
                        scope: this,
                        render: function(view) {
                            this.addDataViewDragDrop(view, 'columnsTabView');
                        }
                    }
                }]
            }]
        }, config);

        this.callParent([config]);
    },

    getList : function () {
        return this.down('#columnsList');
    },

    createAggregateStore: function(){
        var model = Ext4.define('Aggregate', {
            extend: 'Ext.data.Model',
            fields: [{name: 'fieldKey'}, {name: 'type'}, {name: 'label'} ],
            idProperty: {name: 'id', convert: function(v, rec){
                return rec.get('fieldKey').toUpperCase();
            }}
        });

        return Ext4.create('Ext.data.Store', {
            model: model,
            remoteSort: true
        });
    },

    onToolGear : function (index, item, e) {
        var columnRecord = this.columnStore.getAt(index);
        var fieldKey = columnRecord.data.fieldKey;
        var metadataRecord = this.fieldMetaStore.getById(fieldKey.toUpperCase());

        if (!this._editPropsWin) {
            var aggregateStoreCopy = this.createAggregateStore(); //NOTE: we deliberately create a separate store to use with this window.
            var aggregateStore = this.aggregateStore;
            var columnsList = this.getList();

            var aggregateOptions = [];
            aggregateOptions.push({value: "", name: "[None]"});
            Ext4.each(LABKEY.Query.getAggregatesForType(metadataRecord.get('jsonType')), function(key){
                aggregateOptions.push({value: key.toUpperCase(), name: key.toUpperCase()});
            }, this);

            var win = Ext4.create('Ext.window.Window', {
                title: "Edit column properties",
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
                    padding: 5,
                    defaults: { padding: 5 },
                    items: [{
                        xtype: "label",
                        text: "Title:"
                    },{
                        xtype: "textfield",
                        itemId: "titleField",
                        name: "title",
                        allowBlank: true,
                        width: 330
                    },{
                        xtype: "label",
                        text: "Aggregates:"
                    },{
                        xtype: 'grid',
                        width: 340,
                        store: aggregateStoreCopy,
                        selType: 'rowmodel',
                        plugins: [
                            Ext4.create('Ext.grid.plugin.CellEditing', {
                                clicksToEdit: 1
                            })
                        ],
                        columns: [{ text: 'Type', dataIndex: 'type', width: 75, menuDisabled: true,
                            editor: {
                                xtype: "combo",
                                name: "aggregate",
                                displayField: 'name',
                                valueField: 'value',
                                store: Ext4.create('Ext.data.Store', {
                                    fields: [{name: 'name'}, {name: 'value'}],
                                    data: aggregateOptions
                                }),
                                mode: 'local',
                                editable: false
                            }
                        },
                            { text: 'Label', dataIndex: 'label', flex: 1, menuDisabled: true, editor: 'textfield' },
                            {
                                xtype: 'actioncolumn',
                                width: 30,
                                menuDisabled: true,
                                sortable: false,
                                items: [{
                                    icon: LABKEY.contextPath + '/_images/delete.png',
                                    tooltip: 'Remove',
                                    handler: function(grid, rowIndex, colIndex) {
                                        var record = grid.getStore().getAt(rowIndex);
                                        grid.getStore().remove(record);
                                    }
                                }]
                            }],
                        buttons: [{
                            text: 'Add Aggregate',
                            margin: 10,
                            handler: function(btn){
                                var store = btn.up('grid').getStore();
                                store.add({fieldKey: win.columnRecord.get('fieldKey')});
                            }
                        }]
                    }]
                },
                buttonAlign: "center",
                buttons: [{
                    text: "OK",
                    handler: function () {
                        var title = win.down('#titleField').getValue();
                        title = title ? title.trim() : "";
                        win.columnRecord.set("title", !Ext4.isEmpty(title) ? title : undefined);

                        var error;
                        var fieldKey = win.columnRecord.get('fieldKey');
                        var aggregateStoreCopy = win.down('grid').getStore();

                        //validate the records
                        aggregateStoreCopy.each(function (rec) {
                            if (!rec.get('type') && !rec.get('label')) {
                                aggregateStoreCopy.remove(rec);
                            }
                            else if (!rec.get('type'))
                            {
                                error = true;
                                alert('Aggregate is missing a type');
                                return false;
                            }
                        }, this);

                        if (error) {
                            return;
                        }

                        //remove existing records matching this field
                        aggregateStore.removeAll();

                        //then add to store
                        aggregateStoreCopy.each(function(rec){
                            aggregateStore.add({
                                fieldKey: rec.get('fieldKey'),
                                type: rec.get('type'),
                                label: rec.get('label')
                            });
                        }, this);

                        columnsList.refresh();
                        win.hide();
                    }
                },{
                    text: "Cancel",
                    handler: function () { win.hide(); }
                }],

                initEditForm : function (columnRecord, metadataRecord)
                {
                    this.columnRecord = columnRecord;
                    this.metadataRecord = metadataRecord;

                    this.setTitle("Edit column properties for '" + Ext4.util.Format.htmlEncode(this.columnRecord.get("fieldKey")) + "'");
                    this.down('#titleField').setValue(this.columnRecord.get("title"));

                    //NOTE: we make a copy of the data so we can avoid commiting updates until the user clicks OK
                    aggregateStoreCopy.removeAll();
                    aggregateStore.each(function(rec){
                        if (rec.get('fieldKey') == this.columnRecord.get('fieldKey'))
                        {
                            aggregateStoreCopy.add({
                                fieldKey: rec.get('fieldKey'),
                                label: rec.get('label'),
                                type: rec.get('type')
                            });
                        }
                    }, this);

                    //columnsList
                    this.columnRecord.store.fireEvent('datachanged', this.columnRecord.store)

                }
            });

            win.render(document.body);
            this._editPropsWin = win;
        }

        this._editPropsWin.initEditForm(columnRecord, metadataRecord);
        this._editPropsWin.show();
    },

    createDefaultRecordData : function (fieldKey) {
        var o = {};

        if (fieldKey) {
            o.fieldKey = fieldKey;
            var fk = LABKEY.FieldKey.fromString(fieldKey);
            var record = this.fieldMetaStore.getById(fieldKey.toUpperCase());
            if (record) {
                o.name = record.caption || fk.name;
            }
            else {
                o.name = fk.name + " (not found)";
            }
        }

        return o;
    },

    setShowHiddenFields : function (showHidden) {
    },

    hasField : function (fieldKey) {
        // Find fieldKey using case-insensitive comparison
        return this.columnStore.find("fieldKey", fieldKey, 0, false, false) != -1;
    },

    revert : function () {
        // XXX:
    },

    validate : function () {
        if (this.columnStore.getCount() == 0)
        {
            alert("You must select at least one field to display in the grid.");
            return false;

            // XXX: check each fieldKey is selected only once
        }
        return true;
    },

    save : function (edited, urlParameters) {
        this.callParent([edited, urlParameters]);

        // move the aggregates out of the 'columns' list and into a separate 'aggregates' list
        edited.aggregates = [];
        this.aggregateStore.each(function(rec){
            edited.aggregates.push({fieldKey: rec.get('fieldKey'), type: rec.get('type'), label: rec.get('label')});
        }, this);

        for (var i = 0; i < edited.columns.length; i++) {
            delete edited.columns[i].aggregate;
        }
    }

});
