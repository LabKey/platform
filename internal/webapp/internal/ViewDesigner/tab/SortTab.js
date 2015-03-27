
Ext4.define('LABKEY.internal.ViewDesigner.tab.SortTab', {

    extend: 'LABKEY.internal.ViewDesigner.tab.BaseTab',

    constructor : function (config) {
        this.designer = config.designer;
        this.customView = config.customView;
        var fieldMetaStore = this.fieldMetaStore = config.fieldMetaStore;

        this.sortStore = Ext4.create('Ext.data.Store', {
            fields: ['fieldKey', 'dir', {name: 'urlParameter', type: 'boolean', defaultValue: false}],
            data: this.customView,
            remoteSort: true,
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json',
                    root: 'sort',
                    idProperty: function (json) {
                        return json.fieldKey.toUpperCase()
                    }
                }
            }
        });

        this.sortStore.on({
            load: this.onStoreLoad,
            add: this.onStoreAdd,
            remove: this.onStoreRemove,
            scope: this
        });

        config = Ext4.applyIf({
            cls: "test-sort-tab",
            layout: "fit",
            items: [{
                xtype: "panel",
                cls: 'themed-panel2',
                title: "Selected Sorts",
                border: false,
                style: {"border-left-width": "1px"},
                layout: {
                    type: "hbox",
                    align: "stretch"
                },
                items: [{
                    xtype: "compdataview",
                    itemId: "sortList",
                    cls: "labkey-customview-list",
                    flex: 1,
                    store: this.sortStore,
                    emptyText: "No sorts added",
                    deferEmptyText: false,
                    multiSelect: true,
                    height: 250,
                    autoScroll: true,
                    overItemCls: "x4-view-over",
                    itemSelector: '.labkey-customview-item',
                    tpl: new Ext4.XTemplate(
                            '<tpl for=".">',
                            '<table width="100%" cellpadding=0 cellspacing=0 class="labkey-customview-item labkey-customview-sort-item" fieldKey="{fieldKey:htmlEncode}">',
                            '  <tr>',
                            '    <td rowspan="2" class="labkey-grab"></td>',
                            '    <td colspan="3"><div class="item-caption">{[this.getFieldCaption(values)]}</div></td>',
                            '  </tr>',
                            '  <tr>',
                            '    <td><div class="item-dir"></div></td>',
                            '    <td width="21px" valign="top"><div class="item-paperclip"></div></td>',
                            '    <td width="15px" valign="top"><span class="labkey-tool labkey-tool-close" title="Remove sort"></span></td>',
                            '  </tr>',
                            '</table>',
                            '</tpl>',
                            {
                                getFieldCaption : function (values) {
                                    var fieldKey = values.fieldKey;
                                    var fieldMeta = fieldMetaStore.getById(fieldKey.toUpperCase());
                                    if (fieldMeta)
                                    {
                                        // caption is already htmlEncoded
                                        if (fieldMeta.data.caption && fieldMeta.data.caption != "&nbsp;") {
                                            return fieldMeta.data.caption;
                                        }
                                        return Ext4.util.Format.htmlEncode(fieldMeta.data.name);
                                    }
                                    return Ext4.util.Format.htmlEncode(values.fieldKey) + " <span class='labkey-error'>(not found)</span>";
                                }
                            }
                    ),
                    listeners: {
                        scope: this,
                        render: function(view) {
                            this.addDataViewDragDop(view, 'sortsTabView');
                        }
                    },
                    items: [{
                        xtype: 'combo',
                        cls: 'test-item-op',
                        renderTarget: 'div.item-dir',
                        applyValue: 'dir',
                        store: [["+", "Ascending"], ["-", "Descending"]],
                        mode: 'local',
                        triggerAction: 'all',
                        forceSelection: true,
                        allowBlank: false,
                        editable: false
                    },{
                        xtype: 'paperclip-button',
                        renderTarget: 'div.item-paperclip',
                        applyValue: 'urlParameter',
                        tooltipType: "title",
                        itemType: "sort"
                    }]
                }]
            }]
        }, config);

        this.callParent([config]);
    },

    initComponent : function () {
        this.callParent();
        this.updateTitle();
    },

    updateTitle : function ()
    {
        var count = this.sortStore.getCount();
        var title = "Sort" + (count > 0 ? " (" + count + ")" : "");
        var tabStore = this.designer.getTabsStore();
        this.designer.updateTabText(tabStore, "SortTab", title);
    },

    onStoreLoad : function (store, filterRecords, options) {
        this.updateTitle();
    },

    onStoreAdd : function (store, records, index) {
        this.updateTitle();
    },

    onStoreRemove : function (store, record, index) {
        this.updateTitle();
    },

    createDefaultRecordData : function (fieldKey) {
        return {
            fieldKey: fieldKey,
            dir: "+",
            urlParameter: false
        };
    },

    setShowHiddenFields : function (showHidden) {
    },

    getList : function () {
        return this.down('#sortList');
    },

    hasField : function (fieldKey) {
        // Find fieldKey using case-insensitive comparison
        return this.sortStore.find("fieldKey", fieldKey, 0, false, false) != -1;
    },

    revert : function () {

    },

    validate : function () {
        return true;
    }

});
