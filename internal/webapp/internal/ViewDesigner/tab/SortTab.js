/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.internal.ViewDesigner.model.Sort', {

    extend: 'LABKEY.internal.ViewDesigner.model.FieldKey',

    fields: [
        {name: 'dir'},
        {name: 'urlParameter', type: 'boolean', defaultValue: false}
    ],

    proxy: {
        type: 'memory',
        reader: {
            type: 'json',
            root: 'sort'
        }
    }
});

Ext4.define('LABKEY.internal.ViewDesigner.tab.SortTab', {

    extend: 'LABKEY.internal.ViewDesigner.tab.BaseTab',

    cls: 'test-sort-tab',
    baseTitle: 'Selected Sorts',
    baseTitleDescription: 'Sorts will be applied in the order set below.',
    
    getSortStore : function() {
        if (!this.sortStore) {
            this.sortStore = Ext4.create('LABKEY.internal.ViewDesigner.store.FieldKey', {
                model: 'LABKEY.internal.ViewDesigner.model.Sort',
                data: this.customView,
                listeners: {
                    load: this.bindTitle,
                    add: this.bindTitle,
                    remove: this.bindTitle,
                    scope: this
                }
            });

            this.bindTitle(this.sortStore);
        }
        
        return this.sortStore;
    },

    bindTitle : function(store) {
        var count = store ? store.getCount() : 0;
        var title = 'Sort' + (count > 0 ? ' (' + count + ')' : '');
        this.designer.updateTabText(this.name, title);
    },

    createDefaultRecordData : function(fieldKey) {
        return {
            fieldKey: fieldKey,
            dir: '+',
            urlParameter: false
        };
    },

    getList : function() {
        if (!this.listItem) {

            var me = this;

            this.listItem = Ext4.create('Ext.ux.ComponentDataView', {
                cls: 'labkey-customview-list',
                store: this.getSortStore(),
                deferEmptyText: false,
                multiSelect: true,
                height: 166,
                autoScroll: true,
                overItemCls: 'x4-view-over',
                itemSelector: '.labkey-customview-item',
                tpl: new Ext4.XTemplate(
                    '<tpl if="length == 0">',
                    '  <div class="labkey-customview-empty">No sorts added.</div>',
                    '</tpl>',
                    '<tpl for=".">',
                    '<table width="100%" cellpadding=0 cellspacing=0 class="labkey-customview-item labkey-customview-sort-item" fieldKey="{fieldKey:htmlEncode}">',
                    '  <tr>',
                    '    <td rowspan="2" class="labkey-grab"></td>',
                    '    <td colspan="2"><div class="item-caption">{[this.getFieldCaption(values)]}</div></td>',
                    '  </tr>',
                    '  <tr>',
                    '    <td><div class="item-dir"></div></td>',
                    '    <td width="15px" valign="top"><span class="labkey-tool-close fa fa-times" title="Remove sort"></span></td>',
                    '  </tr>',
                    '</table>',
                    '</tpl>',
                    {
                        getFieldCaption : function(values) {
                            var fieldKey = values.fieldKey;
                            var fieldMeta = me.fieldMetaStore.getById(fieldKey.toUpperCase());
                            if (fieldMeta)
                            {
                                // caption is already htmlEncoded
                                if (fieldMeta.data.caption && fieldMeta.data.caption != "&nbsp;") {
                                    return fieldMeta.data.caption;
                                }
                                return Ext4.htmlEncode(fieldMeta.data.name);
                            }
                            return Ext4.htmlEncode(values.fieldKey) + " <span class='labkey-error'>(not found)</span>";
                        }
                    }
                ),
                listeners: {
                    render: function(view) {
                        this.addDataViewDragDrop(view, 'sortsTabView');
                    },
                    scope: this
                },
                items: [{
                    xtype: 'combo',
                    cls: 'test-item-op',
                    width: 175,
                    renderTarget: 'div.item-dir',
                    applyValue: 'dir',
                    store: [['+', 'Ascending'], ['-', 'Descending']],
                    mode: 'local',
                    triggerAction: 'all',
                    forceSelection: true,
                    allowBlank: false,
                    editable: false
                }]
            });
        }

        return this.listItem;
    },

    hasField : function(fieldKey) {
        // Find fieldKey using case-insensitive comparison
        return this.getSortStore().find("fieldKey", fieldKey, 0, false, false) != -1;
    },

    validate : function() {
        return true;
    }
});
