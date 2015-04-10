/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.internal.ViewDesigner.tab.FilterTab', {

    extend: 'LABKEY.internal.ViewDesigner.tab.BaseTab',

    constructor : function (config) {

        this.designer = config.designer;
        this.customView = config.customView;
        var fieldMetaStore = this.fieldMetaStore = config.fieldMetaStore;

        // HACK: I'd like to use the GroupingStore with a JsonReader, but DataView doesn't support grouping.
        // HACK: So we will create a manually grouped store.
        var fieldKeyGroups = {};
        var filters = [];
        for (var i = 0; i < this.customView.filter.length; i++)
        {
            var filter = this.customView.filter[i];
            var g = fieldKeyGroups[filter.fieldKey];
            if (!g)
            {
                g = {fieldKey: filter.fieldKey, items: []};
                fieldKeyGroups[filter.fieldKey] = g;
                filters.push(g);
            }
            g.items.push(filter);

            // Issue 14318: Migrate non-date filter ops to their date equivalents
            // CONSIDER: Perhaps we should do this on the server as the CustomView is constructed
            var fieldMetaRecord = fieldMetaStore.getById(filter.fieldKey.toUpperCase());
            if (fieldMetaRecord)
            {
                var jsonType = fieldMetaRecord.data.jsonType;
                if (jsonType == "date" &&
                        (filter.op == LABKEY.Filter.Types.EQUAL.getURLSuffix() ||
                        filter.op == LABKEY.Filter.Types.NOT_EQUAL.getURLSuffix() ||
                        filter.op == LABKEY.Filter.Types.GREATER_THAN.getURLSuffix() ||
                        filter.op == LABKEY.Filter.Types.GREATER_THAN_OR_EQUAL.getURLSuffix() ||
                        filter.op == LABKEY.Filter.Types.LESS_THAN.getURLSuffix() ||
                        filter.op == LABKEY.Filter.Types.LESS_THAN_OR_EQUAL.getURLSuffix()))
                {
                    filter.op = "date" + filter.op;
                }
            }
        }
        this.filterStore = Ext4.create('Ext.data.Store', {
            fields: ['fieldKey', 'items', 'urlParameter'],
            data: { filter: filters },
            remoteSort: true,
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json',
                    root: 'filter',
                    idProperty: function (json) {
                        return json.fieldKey.toUpperCase()
                    }
                }
            }
        });

        this.filterStore.on({
            load: this.onStoreLoad,
            add: this.onStoreAdd,
            remove: this.onStoreRemove,
            scope: this
        });

        var hideContainerFilterToolbar = !this.designer.allowableContainerFilters || this.designer.allowableContainerFilters.length == 0;

        config = Ext4.applyIf({
            cls: "test-filter-tab",
            layout: "fit",
            items: [{
                xtype: "panel",
                cls: 'themed-panel2',
                title: "Selected Filters",
                itemId: "filterPanel",
                border: false,
                style: {"border-left-width": "1px"},
                layout: {
                    type: "vbox",
                    align: "stretch"
                },
                items: [{
                    xtype: "compdataview",
                    itemId: "filterList",
                    cls: "labkey-customview-list",
                    flex: 1,
                    store: this.filterStore,
                    emptyText: "No filters added",
                    deferEmptyText: false,
                    multiSelect: true,
                    height: hideContainerFilterToolbar ? 250 : 220,
                    autoScroll: true,
                    overItemCls: "x4-view-over",
                    itemSelector: '.labkey-customview-item',
                    tpl: new Ext4.XTemplate(
                            '<tpl for=".">',
                            '<table width="100%" cellpadding=0 cellspacing=0 class="labkey-customview-item labkey-customview-filter-item" fieldKey="{fieldKey:htmlEncode}">',
                            '  <tr>',
                            '    <td rowspan="{[values.items.length+2]}" class="labkey-grab" width="8px">&nbsp;</td>',
                            '    <td colspan="3"><div class="item-caption">{[this.getFieldCaption(values)]}</div></td>',
                            '  </tr>',
                            '  <tpl for="items">',
                            '  <tr clauseIndex="{[xindex-1]}">',
                            '    <td>',
                            '      <div class="item-op"></div>',
                            '      <div class="item-value"></div>',
                            '    </td>',
                            '    <td width="21px" valign="top"><div class="item-paperclip"></div></td>',
                            '    <td width="15px" valign="top"><div class="labkey-tool labkey-tool-close" title="Remove filter clause"></div></td>',
                            '  </tr>',
                            '  </tpl>',
                            '  <tr>',
                            '    <td colspan="3">',
                            '      <span style="float:right;">',
                            // NOTE: The click event for the 'Add' text link is handled in onListBeforeClick.
                            LABKEY.Utils.textLink({text: "Add", onClick: "return false;", add: true}),
                            '      </span>',
                            '    </td>',
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
                            this.addDataViewDragDrop(view, 'filtersTabView');
                        }
                    },
                    items: [{
                        xtype: 'labkey-filterOpCombo',
                        cls: 'test-item-op',
                        width: 175,
                        renderTarget: 'div.item-op',
                        indexedProperty: true,
                        fieldMetaStore: this.fieldMetaStore,
                        mode: 'local',
                        triggerAction: 'all',
                        forceSelection: true,
                        allowBlank: false,
                        editable: false,
                        emptyText: "Select filter operator",
                        listeners: {
                            select: this.updateValueTextFieldVisibility,
                            scope: this
                        }
                    },{
                        xtype: 'labkey-filterValue',
                        cls: 'test-item-value',
                        renderTarget: 'div.item-value',
                        indexedProperty: true,
                        fieldMetaStore: this.fieldMetaStore,
                        selectOnFocus: true,
                        emptyText: "Enter filter value"
                    },{
                        xtype: 'paperclip-button',
                        renderTarget: 'div.item-paperclip',
                        indexedProperty: true,
                        tooltipType: "title",
                        itemType: "filter"
                    }]
                }],
                dockedItems: [{
                    xtype: "toolbar",
                    dock: 'bottom',
                    hidden: hideContainerFilterToolbar,
                    height: 30,
                    items: [{
                        xtype: "label",
                        text: "Folder Filter:",
                        style: "font-size: 11px; padding-left: 5px;"
                    }," ", {
                        // HACK: Need to wrap the combo in an panel so the combo doesn't overlap items after it.
                        xtype: "panel",
                        width: 200,
                        plain: true,
                        border: false,
                        layout: "fit",
                        items: [{
                            xtype: "combo",
                            cls: "labkey-folder-filter-combo",
                            value: this.customView.containerFilter,
                            store: [[null, "Default"]].concat(this.designer.allowableContainerFilters),
                            mode: 'local',
                            triggerAction: 'all',
                            allowBlank: true,
                            editable: false,
                            emptyText: "Default",
                            listeners: {
                                change: this.onFolderFilterChange,
                                scope: this
                            }
                        }]
                    },"->",{
                        xtype: "paperclip-button",
                        cls: "labkey-folder-filter-paperclip",
                        pressed: !this.designer.userContainerFilter,
                        tooltipType: "title",
                        disabled: !this.customView.containerFilter,
                        itemType: "container filter"
                    }]
                }]
            }]
        }, config);

        this.callParent([config]);

        var bbar = this.down("#filterPanel").down("toolbar");
        this.containerFilterCombo = bbar.items.get(2).items.get(0);
        this.containerFilterPaperclip = bbar.items.get(4);
    },

    initComponent : function () {
        this.callParent();
        this.updateTitle();
    },

    onFolderFilterChange : function (combo, newValue, oldValue)
    {
        if (newValue) {
            this.containerFilterPaperclip.enable();
        }
        else {
            this.containerFilterPaperclip.disable();
        }
    },

    onListBeforeClick : function (list, record, item, index, e, eOpts)
    {
        if (this.callParent([list, record, item, index, e, eOpts]) === false) {
            return false;
        }

        var target = Ext4.fly(e.getTarget());
        if (target.is("a.labkey-text-link[add='true']")) {
            this.addClause(index);
        }
    },

    addClause : function (index) {
        var record = this.getRecord(index);
        if (record)
        {
            var items = record.get("items");
            // NOTE: need to clone the array otherwise record.set won't fire the change event
            items = items.slice();
            items.push({
                fieldKey: record.get("fieldKey"),
                op: "eq",
                value: ""
            });
            record.set("items", items);
        }
    },

    updateTitle : function ()
    {
        // XXX: only counts the grouped filters
        var count = this.filterStore.getCount();
        var title = "Filter" + (count > 0 ? " (" + count + ")" : "");
        var tabStore = this.designer.getTabsStore();
        this.designer.updateTabText(tabStore, "FilterTab", title);
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

    // Get the record, clause, clause index, and <tr> for a dom node
    getClauseFromNode : function (recordIndex, node)
    {
        var tr = Ext4.fly(node).parent("tr[clauseIndex]");
        if (!tr) {
            return;
        }

        var record = this.getRecord(recordIndex);
        var items = record.get("items");
        var clauseIndex = -1;
        if (tr.dom.getAttribute("clauseIndex") !== undefined) {
            clauseIndex = +tr.dom.getAttribute("clauseIndex");
        }
        if (clauseIndex < 0 || clauseIndex >= items.length) {
            return;
        }

        return {
            record: record,
            index: recordIndex,
            clause: items[clauseIndex],
            clauseIndex: clauseIndex,
            row: tr
        };
    },

    onToolClose : function (index, item, e) {
        var o = this.getClauseFromNode(index, e.getTarget());
        if (!o) {
            return;
        }

        // remove it from the clause list
        var items = o.record.get("items");
        items.splice(o.clauseIndex, 1);

        if (items.length == 0)
        {
            // last clause was removed, remove the entire record
            this.removeRecord(index);
        }
        else
        {
            // remove the dom node and adjust all other clauseIndices by one
            var table = o.row.parent("table.labkey-customview-item");
            Ext4.each(table.query("tr[clauseIndex]"), function (row, i, all)
            {
                var clauseIndex = +row.getAttribute("clauseIndex");
                if (clauseIndex == o.clauseIndex) {
                    Ext4.fly(row).remove();
                }
                else if (clauseIndex > o.clauseIndex) {
                    row.setAttribute("clauseIndex", clauseIndex - 1);
                }
            }, this);

            // adjust clauseIndex down for all components for the filter
            var cs = this.getList().getComponents(index);
            Ext4.each(cs, function (c, i, all)
            {
                if (c.clauseIndex == o.clauseIndex) {
                    Ext4.destroy(c);
                }
                else if (c.clauseIndex > o.clauseIndex) {
                    c.clauseIndex--;
                }
            });
        }
        return false;
    },

    updateValueTextFieldVisibility : function (combo) {
        var record = combo.record;
        var clauseIndex = combo.clauseIndex;

        var filterType = combo.getFilterType();
        // HACK: need to find the text field associated with this filter item
        var cs = this.getList().getComponents(combo);
        for (var i = 0; i < cs.length; i++)
        {
            var c = cs[i];
            if (c.clauseIndex == clauseIndex && c instanceof LABKEY.internal.ViewDesigner.field.FilterTextValue)
            {
                c.setVisible(filterType != null && filterType.isDataValueRequired());
                break;
            }
        }
    },

    createDefaultRecordData : function (fieldKey) {
        // Issue 12334: initialize with default filter based on the field's type.
        var defaultFilter = LABKEY.Filter.Types.EQUAL;

        var fieldMetaRecord = this.fieldMetaStore.getById(fieldKey.toUpperCase());
        if (fieldMetaRecord)
            defaultFilter = LABKEY.Filter.getDefaultFilterForType(fieldMetaRecord.data.jsonType);

        return {
            fieldKey: fieldKey,
            items: [{
                fieldKey: fieldKey,
                op: defaultFilter.getURLSuffix(),
                value: ""
            }]
        };
    },

    getList : function () {
        return this.down('#filterList');
    },

    hasField : function (fieldKey) {
        // filterStore may have more than one filter for a fieldKey
        // Find fieldKey using case-insensitive comparison
        return this.filterStore.find("fieldKey", fieldKey, 0, false, false) != -1;
    },

    revert : function () {

    },

    validate : function () {
        OUTER_LOOP: for (var i = 0; i < this.filterStore.getCount(); i++)
        {
            var filterRecord = this.filterStore.getAt(i);

            var fieldKey = filterRecord.data.fieldKey;
            if (!fieldKey) {
                if (confirm("A fieldKey is required for each filter.\nContinue to save custom view with invalid filter?")) {
                    continue;
                }
                return false;
            }

            var fieldMetaRecord = this.fieldMetaStore.getById(fieldKey.toUpperCase());
            if (!fieldMetaRecord) {
                if (confirm("Field not found for fieldKey '" + fieldKey + "'.\nContinue to save custom view with invalid filter?")) {
                    continue;
                }
                return false;
            }

            var jsonType = fieldMetaRecord.data.jsonType;
            var items = filterRecord.data.items;
            for (var j = 0; j < items.length; j++)
            {
                var filterOp = items[j].op;
                if (filterOp)
                {
                    var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(filterOp);
                    if (!filterType) {
                        if (confirm("Filter operator '" + filterOp + "' isn't recognized.\nContinue to save custom view with invalid filter?")) {
                            continue OUTER_LOOP;
                        }
                        return false;
                    }

                    var value = filterType.validate(items[j].value, jsonType, fieldKey);
                    if (value == undefined) {
                        if (confirm("Continue to save custom view with invalid filter?")) {
                            continue OUTER_LOOP;
                        }
                        return false;
                    }
                }
            }
        }
        return true;
    },

    save : function (edited, urlParameters) {
        var records = this.filterStore.getRange();

        // flatten the filters
        var saveData = [], urlData = [];
        for (var i = 0; i < records.length; i++)
        {
            var filterRecord = records[i];
            var fieldKey = filterRecord.data.fieldKey;
            if (!fieldKey) {
                continue;
            }

            var fieldMetaRecord = this.fieldMetaStore.getById(fieldKey.toUpperCase());
            if (!fieldMetaRecord) {
                continue;
            }

            var jsonType = fieldMetaRecord.data.jsonType;

            var items = filterRecord.get("items");
            for (var j = 0; j < items.length; j++)
            {
                var o = {
                    fieldKey: items[j].fieldKey || fieldKey,
                    op: items[j].op
                };

                var value = undefined;
                var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(items[j].op);
                if (filterType) {
                    // filterType.validate() converts the value to a string
                    value = filterType.validate(items[j].value, jsonType, o.fieldKey);
                }
                else {
                    value = items[j].value;
                }

                if (filterType.isDataValueRequired()) {
                    o.value = value;
                }

                if (items[j].urlParameter) {
                    urlData.push(o);
                }
                else {
                    saveData.push(o);
                }
            }
        }

        var containerFilter = this.containerFilterCombo.getValue();
        if (containerFilter)
        {
            if (this.containerFilterPaperclip.pressed) {
                edited.containerFilter = containerFilter;
            }
            else {
                urlParameters.containerFilter = containerFilter;
            }
        }

        var root = this.filterStore.getProxy().getReader().root;

        edited[root] = saveData;
        urlParameters[root] = urlData;
    }
});
