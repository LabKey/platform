/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.ext4.designer.BaseTab', {

    extend: 'Ext.panel.Panel',

    constructor : function (config) {
        this.designer = config.designer;
        this.unstyled = true;

        var mainPanel = config.items[0];
        mainPanel.tools = [{
            id: "close",
            handler: function (event, toolEl, panel, config) {
                this.designer.close();
            },
            scope: this
        }];

        this.callParent([config]);
    },

    initComponent : function () {
        this.callParent();

        this.getList().on('selectionchange', this.onListSelectionChange, this);
        this.getList().on('render', function (list) {
            this.addEvents("beforetooltipshow");
            this.tooltip = Ext4.create('Ext.tip.ToolTip',{
                renderTo: Ext4.getBody(),
                target: this.getEl(),
                delegate: ".item-caption",
                trackMouse: true,
                listeners: {
                    beforeshow: function (qt) {
                        var el = Ext4.fly(qt.triggerElement).up(this.itemSelector);
                        if (!el)
                            return false;
                        var record = this.getRecord(el.dom);
                        return this.fireEvent("beforetooltipshow", this, qt, record, el);
                    },
                    scope: this
                }
            });
        }, this.getList(), {single: true});
        this.getList().on('beforetooltipshow', this.onListBeforeToolTipShow, this);
        this.getList().on('beforeitemclick', this.onListBeforeClick, this);
    },

    setShowHiddenFields : Ext4.emptyFn,

    isDirty : function () {
        return false;
    },

    revert : Ext4.emptyFn,

    validate : Ext4.emptyFn,

    save : function (edited, urlParameters) {
        var store = this.getList().getStore();

        // HACK: I'm most likely abusing the JsonWriter APIs which could break in future versions of Ext.
        var writer = Ext4.create('Ext.data.writer.Json', {
            encode: false,
            writeAllFields: true,
            listful: true,
            meta: store.reader.meta,
            recordType: store.recordType
        });

        var saveRecords = [], urlRecords = [];
        store.each(function (r) {
            if (r.data.urlParameter) {
                urlRecords.push(r);
            }
            else {
                saveRecords.push(r);
            }
        });

        var o = {};
        writer.apply(o, null, "create", saveRecords);
        Ext4.applyIf(edited, o.jsonData);

        o = {};
        writer.apply(o, null, "create", urlRecords);
        Ext4.applyIf(urlParameters, o.jsonData);
    },

    hasField : Ext4.emptyFn,

    /** Get the listview for the tab. */
    getList : Ext4.emptyFn,

    onListBeforeClick : function (list, record, item, index, e) {
        var node = list.getNode(index);
        if (node)
        {
            var target = e.getTarget();
            if (target.className.indexOf("labkey-tool") > -1)
            {
                var classes = ("" + target.className).split(" ");
                for (var j = 0; j < classes.length; j++)
                {
                    var cls = classes[j].trim();
                    if (cls.indexOf("labkey-tool-") == 0)
                    {
                        var toolName = cls.substring("labkey-tool-".length);
                        var fnName = "onTool" + toolName.charAt(0).toUpperCase() + toolName.substring(1);
                        if (this[fnName]) {
                            return this[fnName].call(this, index, item, e);
                        }
                    }
                }
            }
        }
        return true;
    },

    onToolClose : function (index, item, e)
    {
        this.removeRecord(index);
        return false;
    },


    getFieldMetaRecord : function (fieldKey)
    {
        return this.fieldMetaStore.getById(fieldKey.toUpperCase());
    },

    onListBeforeToolTipShow : function (list, qt, record, el)
    {
        if (record)
        {
            var fieldKey = record.data.fieldKey;
            var fieldMetaRecord = this.getFieldMetaRecord(fieldKey);
            var html;
            if (fieldMetaRecord) {
                html = fieldMetaRecord.getToolTipHtml();
            }
            else {
                html = "<table><tr><td><strong>Field not found:</strong></td></tr><tr><td>" + Ext4.util.Format.htmlEncode(fieldKey) + "</td></tr></table>";
            }
            qt.update(html);
        }
        else {
            qt.update("<strong>No field found</strong>");
        }
    },

    onListSelectionChange : function (list, selections) {
    },

    // subclasses may override this to provide a better default
    createDefaultRecordData : function (fieldKey) {
        return {fieldKey: fieldKey};
    },

    addRecord : function (fieldKey) {
        var list = this.getList();
        var defaultData = this.createDefaultRecordData(fieldKey);
        var record = new list.store.recordType(defaultData);
        var selected = list.getSelectedIndexes();
        if (selected && selected.length > 0)
        {
            var index = Ext4.Array.max(selected);
            list.store.insert(index+1, record);
        }
        else {
            list.store.add([record]);
        }
        return record;
    },

    getRecordIndex : function (fieldKeyOrIndex) {
        var list = this.getList();
        var index = -1;
        if (Ext4.isNumber(fieldKeyOrIndex)) {
            index = fieldKeyOrIndex;
        }
        else {
            index = list.store.find("fieldKey", fieldKeyOrIndex, 0, false, false);
        }
        return index;
    },

    getRecord : function (fieldKeyOrIndex) {
        var index = this.getRecordIndex(fieldKeyOrIndex);
        if (index > -1) {
            return this.getList().store.getAt(index);
        }
        return null;
    },

    removeRecord : function (fieldKeyOrIndex) {
        var index = this.getRecordIndex(fieldKeyOrIndex);
        var record = this.getList().store.getAt(index);
        if (record)
        {
            // remove from the store and select sibling
            this.getList().store.removeAt(index);
            var i = index < this.getList().store.getCount() ? index : index - 1;
            if (i > -1) {
                this.getList().select(i);
            }

            // uncheck the field tree
            // TODO reenable after conversion to Ext4
            //var upperFieldKey = record.data.fieldKey.toUpperCase();
            //var treeNode = this.designer.fieldsTree.getRootNode().findChildBy(function (node) {
            //    return node.attributes.fieldKey.toUpperCase() == upperFieldKey;
            //}, null, true);
            //if (treeNode) {
            //    treeNode.getUI().toggleCheck(false);
            //}
        }
    }

});

Ext4.define('LABKEY.ext4.designer.ColumnsTab', {

    extend: 'LABKEY.ext4.designer.BaseTab',

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

                // TODO: this won't work, no recordType
                //this.aggregateStore.add(new this.aggregateStore.recordType(agg));
            }
        }

        var aggregateStore = this.aggregateStore;

        config = Ext4.applyIf({
            //title: "Columns",
            cls: "test-columns-tab",
            layout: "fit",
            items: [{
                xtype: "panel",
                title: "Selected Fields",
                border: false,
                width: 200,
                style: {"border-left-width": "1px"},
                layout: {
                    type: "hbox",
                    align: "stretch"
                },
                items: [{
                    xtype: "dataview",
                    itemId: "columnsList",
                    cls: "labkey-customview-list",
                    // TODO plugins: [ new Ext.ux.dd.GridDragDropRowOrder() ],
                    flex: 1,
                    store: this.columnStore,
                    emptyText: "No fields selected",
                    deferEmptyText: false,
                    multiSelect: true,
                    height: 240,
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
                            '    <td width="15px" valign="top"><div class="labkey-tool labkey-tool-gear" title="Edit"></div></td>',
                            '    <td width="15px" valign="top"><span class="labkey-tool labkey-tool-close" title="Remove column"></span></td>',
                            '  </tr>',
                            '</table>',
                            '</tpl>',
                            {
                                getFieldCaption : function (values) {
                                    if (values.title) {
                                        return Ext4.util.Format.htmlEncode(values.title);
                                    }

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
                                    return Ext4.util.Format.htmlEncode(values.name) + " <span class='labkey-error'>(not found)</span>";
                                },

                                getAggegateCaption : function (values) {
                                    var fieldKey = values.fieldKey;
                                    var fieldMeta = fieldMetaStore.getById(fieldKey.toUpperCase());
                                    var labels = [];
                                    aggregateStore.each(function(rec){
                                        if (rec.get('fieldKey') == fieldKey) {
                                            labels.push(rec.get('type'));
                                        }
                                    }, this);
                                    labels = Ext4.Array.unique(labels);

                                    if (labels.length) {
                                        return Ext4.util.Format.htmlEncode(labels.join(','));
                                    }

                                    return "";
                                }
                            }
                    )
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

        if (!this._editPropsWin)
        {
            var fieldMetaStore = this.fieldMetaStore;
            var aggregateStoreCopy = this.createAggregateStore(); //NOTE: we deliberately create a separate store to use with this window.
            var aggregateStore = this.aggregateStore;
            var columnsList = this.getList();

            var aggregateOptionStore = Ext4.create('Ext.data.Store', {
                fields: [{name: 'name'}, {name: 'value'}]
            });

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
                        //viewConfig: {
                        //    scrollOffset: 1,
                        //    rowOverCls: 'x4-item-selected'
                        //},
                        //autoExpandColumn: 'label',
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
                                store: aggregateOptionStore,
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
                        aggregateStore.each(function(rec) {
                            if (rec.get('fieldKey') == fieldKey) {
                                aggregateStore.remove(rec);
                            }
                        }, this);

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

                    aggregateOptionStore.removeAll();
                    aggregateOptionStore.add({value: "", name: "[None]"});
                    Ext4.each(LABKEY.Query.getAggregatesForType(metadataRecord.get('jsonType')), function(key){
                        aggregateOptionStore.add({value: key.toUpperCase(), name: key.toUpperCase()});
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
        if (fieldKey)
        {
            var o = {fieldKey: fieldKey};
            var fk = LABKEY.FieldKey.fromString(fieldKey);
            var record = this.fieldMetaStore.getById(fieldKey.toUpperCase());
            if (record) {
                o.name = record.caption || fk.name;
            }
            else {
                o.name = fk.name + " (not found)";
            }
            return o;
        }

        return { };
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

Ext4.define('LABKEY.ext4.designer.FilterTab', {

    extend: 'LABKEY.ext4.designer.BaseTab',

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

        var thisTab = this;
        config = Ext4.applyIf({
            //title: "Filter",
            cls: "test-filter-tab",
            layout: "fit",
            items: [{
                title: "Selected Filters",
                itemId: "filterPanel",
                xtype: "panel",
                border: false,
                style: {"border-left-width": "1px"},
                layout: {
                    type: "vbox",
                    align: "stretch"
                },
                items: [{
                    xtype: "dataview", // TODO compdataview
                    itemId: "filterList",
                    cls: "labkey-customview-list",
                    flex: 1,
                    // TODO plugins: [ new Ext.ux.dd.GridDragDropRowOrder() ],
                    store: this.filterStore,
                    emptyText: "No filters added",
                    deferEmptyText: false,
                    multiSelect: true,
                    height: 240,
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
                    items: [{
                        xtype: 'labkey-filterOpCombo',
                        cls: 'test-item-op',
                        renderTarget: 'div.item-op',
                        indexedProperty: true,
                        fieldMetaStore: this.fieldMetaStore,
                        listeners: {
                            select: this.updateValueTextFieldVisibility,
                            afterrender: function () {
                                if (this.getWidth() == 0)
                                {
                                    // If the activeGroup tab is specified in the customize view config,
                                    // the initial size of the items in the SortTab/FilterTab will be zero.
                                    // As a bruteforce workaround, refresh the entire list forcing a redraw.
                                    setTimeout(function () {
                                        thisTab.getList().refresh();
                                    }, 200);
                                }
                            },
                            scope: this
                        },
                        mode: 'local',
                        triggerAction: 'all',
                        forceSelection: true,
                        allowBlank: false,
                        selectOnFocus: true,
                        emptyText: "Select filter operator"
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
                bbar: {
                    xtype: "toolbar",
                    hidden: !this.designer.allowableContainerFilters || this.designer.allowableContainerFilters.length == 0,
                    items: [{
                        xtype: "label",
                        text: "Folder Filter:"
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
                            //enabled: this.customView.containerFilter != null,
                            value: this.customView.containerFilter,
                            store: [["&nbsp;", "Default"]].concat(this.designer.allowableContainerFilters),
                            mode: 'local',
                            triggerAction: 'all',
                            allowBlank: true,
                            emptyText: "Default",
                            listeners: {
                                change: this.onFolderFilterChange,
                                scope: this
                            }
                        }]
                    //}," ",{ // TODO
                    //    xtype: "paperclip-button",
                    //    cls: "labkey-folder-filter-paperclip",
                    //    pressed: !this.designer.userContainerFilter,
                    //    tooltipType: "title",
                    //    disabled: !this.customView.containerFilter,
                    //    itemType: "container filter"
                    }]
                }
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

    onListBeforeClick : function (list, index, item, e)
    {
        if (this.callParent([list, index, item, e]) === false) {
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

    onToolClose : function (index, item, e)
    {
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
            if (c.clauseIndex == clauseIndex && c instanceof LABKEY.ext4.designer.FilterTextValue) // TODO check this
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

        edited[this.filterStore.root] = saveData;
        urlParameters[this.filterStore.root] = urlData;
    }
});

Ext4.define('LABKEY.ext4.designer.SortTab', {

    extend: 'LABKEY.ext4.designer.BaseTab',

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

        var thisTab = this;
        config = Ext4.applyIf({
            //title: "Sort",
            cls: "test-sort-tab",
            layout: "fit",
            items: [{
                title: "Selected Sort",
                xtype: "panel",
                border: false,
                style: {"border-left-width": "1px"},
                layout: {
                    type: "hbox",
                    align: "stretch"
                },
                items: [{
                    title: "Selected Sort",
                    itemId: "sortList",
                    cls: "labkey-customview-list",
                    xtype: "dataview", // TODO compdataview
                    flex: 1,
                    store: this.sortStore,
                    // TODO plugins: [ new Ext.ux.dd.GridDragDropRowOrder() ],
                    emptyText: "No sorts added",
                    deferEmptyText: false,
                    multiSelect: true,
                    height: 240,
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
                        listeners: {
                            'afterrender': function () {
                                // XXX: work around annoying focus bug for Fields in DataView.
                                this.mon(this.el, 'mousedown', function () { this.focus(); }, this);
                                if (this.getWidth() == 0)
                                {
                                    // If the activeGroup tab is specified in the customize view config,
                                    // the initial size of the items in the SortTab/FilterTab will be zero.
                                    // As a bruteforce workaround, refresh the entire list forcing a redraw.
                                    setTimeout(function () {
                                        thisTab.getList().refresh();
                                    }, 200);
                                }
                            }
                        }
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

//LABKEY.DataRegion.PropertiesTab = Ext.extend(Ext.Panel, {
//
//    constructor : function (config) {
//
//        this.designer = config.designer;
//        this.customView = config.customView;
//        this.readOnly = config.readOnly;
//
//        var disableSharedAndInherit = this.customView.hidden || this.customView.session || !this.designer.query.canEditSharedViews;
//
//        config = Ext.applyIf({
//            title: "Properties",
//            layout: "form",
//            defaults: {
//                tooltipType: "title"
//            },
//            items: [{
//                ref: "nameField",
//                fieldLabel: "Name",
//                xtype: "textfield",
//                tooltip: "Name of the custom view (leave blank to save as the default grid view)",
//                value: this.customView.name,
//                disabled: this.readOnly || this.customView.hidden
//            },{
//                ref: "sharedField",
//                fieldLabel: "Shared",
//                xtype: "checkbox",
//                tooltip: "Make this grid view available to all users",
//                checked: this.customView.shared,
//                disabled: this.readOnly || disableSharedAndInherit
//            },{
//                ref: "inheritField",
//                fieldLabel: "Inherit",
//                xtype: "checkbox",
//                tooltip: "Make this grid view available in child folders",
//                checked: this.customView.inherit,
//                disabled: this.readOnly || disableSharedAndInherit
//            },{
//                ref: "sessionField",
//                fieldLabel: "Temporary",
//                xtype: "checkbox",
//                tooltip: "Save this view temporarily.  Any changes will only persist for the duration of your session.",
//                checked: this.customView.session,
//                disabled: this.readOnly || this.customView.hidden,
//                handler: function (checkbox, checked) {
//                    if (this.readOnly)
//                        return;
//                    if (checked) {
//                        this.sharedField.setValue(false);
//                        this.sharedField.setDisabled(true);
//                        this.inheritField.setValue(false);
//                        this.inheritField.setDisabled(true);
//                    }
//                    else {
//                        if (disableSharedAndInherit)
//                        {
//                            this.sharedField.reset();
//                            this.sharedField.setDisabled(false);
//                            this.inheritField.reset();
//                            this.inheritField.setDisabled(false);
//                        }
//                    }
//                },
//                scope: this
//            }]
//        }, config);
//
//        LABKEY.DataRegion.PropertiesTab.superclass.constructor.call(this, config);
//    },
//
//    isDirty : function () {
//        for (var i = 0; i < this.grouptab.items.length; i++)
//        {
//            var field = this.grouptab.items.get(i);
//            if (field instanceof Ext.form.Field)
//                if (field.isDirty())
//                    return true;
//        }
//        return false;
//    },
//
//    validate : function () {
//        // UNDONE: if view name is different, we should check that the target view is editable
//        if (!this.customView.editable)
//        {
//            if (!this.nameField.isDirty())
//            {
//                Ext.MsgBox.alert("You must save this view with an alternate name.");
//                return false;
//            }
//        }
//        if (!this.designer.query.canEditSharedViews)
//        {
//            // UNDONE: check shared/inherit
//            // Ext.Msg.alert(...)
//        }
//
//        return true;
//    },
//
//    save : function (edited, urlParameters) {
//        var o = {};
//
//        if (this.customView.hidden)
//        {
//            o = {
//                name: this.customView.name,
//                shared: this.customView.shared,
//                hidden: this.customView.hidden,
//                session: this.customView.session
//            };
//        }
//        else
//        {
//            o.name = this.nameField.getValue();
//            o.session = this.sessionField.getValue();
//            if (!o.session && this.customView.canSaveForAllUsers)
//            {
//                o.shared = this.sharedField.getValue();
//                o.inherit = this.inheritField.getValue();
//            }
//        }
//
//        Ext.applyIf(edited, o);
//    }
//
//});

