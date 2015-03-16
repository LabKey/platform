/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.DataRegion.Tab = Ext.extend(Ext.Panel, {

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

        LABKEY.DataRegion.Tab.superclass.constructor.call(this, config);
    },

    initComponent : function () {
        LABKEY.DataRegion.Tab.superclass.initComponent.apply(this, arguments);
        this.getList().on('selectionchange', this.onListSelectionChange, this);
        this.getList().on('render', function (list) {
            this.addEvents("beforetooltipshow");
            this.tooltip = new Ext.ToolTip({
                renderTo: Ext.getBody(),
                target: this.getEl(),
                delegate: ".item-caption",
                trackMouse: true,
                listeners: {
                    beforeshow: function (qt) {
                        var el = Ext.fly(qt.triggerElement).up(this.itemSelector);
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
        this.getList().on('beforeclick', this.onListBeforeClick, this);
    },

    setShowHiddenFields : Ext.emptyFn,

    isDirty : function () {
        return false;
    },

    revert : Ext.emptyFn,

    validate : Ext.emptyFn,

    save : function (edited, urlParameters) {
        var store = this.getList().getStore();

        // HACK: I'm most likely abusing the JsonWriter APIs which could break in future versions of Ext.
        var writer = new Ext.data.JsonWriter({
            encode: false,
            writeAllFields: true,
            listful: true,
            meta: store.reader.meta,
            recordType: store.recordType
        });

        var saveRecords = [], urlRecords = [];
        store.each(function (r) {
            if (r.data.urlParameter)
                urlRecords.push(r);
            else
                saveRecords.push(r);
        });

        var o = {};
        writer.apply(o, null, "create", saveRecords);
        Ext.applyIf(edited, o.jsonData);

        o = {};
        writer.apply(o, null, "create", urlRecords);
        Ext.applyIf(urlParameters, o.jsonData);
    },

    hasField : Ext.emptyFn,

    /** Get the listview for the tab. */
    getList : Ext.emptyFn,

    onListBeforeClick : function (list, index, item, e)
    {
        var node = list.getNode(index);
        if (node)
        {
            var target = Ext.fly(e.getTarget());
            if (target.hasClass("labkey-tool"))
            {
                var classes = ("" + target.dom.className).split(" ");
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
            var html = LABKEY.ext.FieldMetaRecord.getToolTipHtml(fieldMetaRecord, fieldKey);
            qt.body.update(html);
        }
        else {
            qt.body.update("<strong>No field found</strong>");
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
            var index = Ext.max(selected);
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
        if (Ext.isNumber(fieldKeyOrIndex)) {
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
            var upperFieldKey = record.data.fieldKey.toUpperCase();
            var treeNode = this.designer.fieldsTree.getRootNode().findChildBy(function (node) {
                return node.attributes.fieldKey.toUpperCase() == upperFieldKey;
            }, null, true);
            if (treeNode) {
                treeNode.getUI().toggleCheck(false);
            }
        }
    }

});

LABKEY.DataRegion.ColumnsTab = Ext.extend(LABKEY.DataRegion.Tab, {

    constructor : function (config) {

        this.customView = config.customView;
        var fieldMetaStore = this.fieldMetaStore = config.fieldMetaStore;

        this.columnStore = new Ext.data.JsonStore({
            fields: ['name', 'fieldKey', 'title', 'aggregate'],
            root: 'columns',
            idProperty: function (json) { return json.fieldKey.toUpperCase() },
            data: this.customView,
            remoteSort: true
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

                this.aggregateStore.add(new this.aggregateStore.recordType(agg));
            }
        }

        var aggregateStore = this.aggregateStore;
        config = Ext.applyIf({
            title: "Columns",
            cls: "test-columns-tab",
            layout: "fit",
            items: [{
                title: "Selected Fields",
                xtype: "panel",
                border: false,
                width: 200,
                style: {"border-left-width": "1px"},
                layout: {
                    type: "hbox",
                    align: "stretch"
                },
                items: [{
                    ref: "../columnsList",
                    xtype: "dataview",
                    cls: "labkey-customview-list",
                    plugins: [ new Ext.ux.dd.GridDragDropRowOrder() ],
                    flex: 1,
                    store: this.columnStore,
                    emptyText: "No fields selected",
                    deferEmptyText: false,
                    multiSelect: true,
                    autoScroll: true,
                    overClass: "x-view-over",
                    itemSelector: ".labkey-customview-item",
                    tpl: new Ext.XTemplate(
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
                                        return Ext.util.Format.htmlEncode(values.title);
                                    }

                                    var fieldKey = values.fieldKey;
                                    var fieldMeta = fieldMetaStore.getById(fieldKey.toUpperCase());
                                    if (fieldMeta)
                                    {
                                        // caption is already htmlEncoded
                                        if (fieldMeta.data.caption && fieldMeta.data.caption != "&nbsp;") {
                                            return fieldMeta.data.caption;
                                        }
                                        return Ext.util.Format.htmlEncode(fieldMeta.data.name);
                                    }
                                    return Ext.util.Format.htmlEncode(values.name) + " <span class='labkey-error'>(not found)</span>";
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
                                    labels = Ext.unique(labels);

                                    if (labels.length) {
                                        return Ext.util.Format.htmlEncode(labels.join(','));
                                    }

                                    return "";
                                }
                            }
                    )
                }]
            }]
        }, config);

        LABKEY.DataRegion.ColumnsTab.superclass.constructor.call(this, config);
    },

    getList : function () { return this.columnsList; },

    createAggregateStore: function(){
        return new Ext.data.ArrayStore({
            fields: ['fieldKey', 'type', 'label'],
            remoteSort: true,
            idProperty: function (json) { return json.fieldKey.toUpperCase() }
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
            var columnsList = this.columnsList;
            var itemDeleter = new Ext.grid.RowSelectionModel({
                width: 30,

                sortable: false,
                dataIndex: 0, // this is needed, otherwise there will be an error

                menuDisabled: true,
                fixed: true,
                id: 'deleter',

                initEvents: function(){
                    Ext.grid.RowSelectionModel.prototype.initEvents.call(this);
                    this.grid.on('cellclick', function(grid, rowIndex, columnIndex, e){
                        if (columnIndex==grid.getColumnModel().getIndexById('deleter'))
                        {
                            var record = grid.getStore().getAt(rowIndex);
                            grid.getStore().remove(record);
                        }
                    });
                },

                renderer: function(v, p, record, rowIndex){
                    return '<div class="labkey-tool labkey-tool-close" style="width: 15px; height: 16px;"></div>';
                }
            });

            var aggregateOptionStore = new Ext.data.ArrayStore({
                fields: ['name', 'value'],
                data: []
            });

            var win = new Ext.Window({
                title: "Edit column properties",
                resizable: false,
                constrain: true,
                constrainHeader: true,
                minimizable: false,
                maximizable: false,
                modal: true,
                stateful: false,
                shim: true,
                buttonAlign: "center",
                width: 350,
                autoHeight: true,
                minHeight: 100,
                footer: true,
                closable: true,
                closeAction: 'hide',
                items: {
                    xtype: 'form',
                    border: false,
                    labelAlign: 'top',
                    bodyStyle: 'padding: 5px;',
                    defaults: { width: 330 },
                    items: [{
                        xtype: "textfield",
                        fieldLabel: "Title",
                        name: "title",
                        ref: "../titleField",
                        allowBlank: true
                    },{
                        xtype: 'editorgrid',
                        fieldLabel: 'Aggregates',
                        editable: true,
                        autoHeight: true,
                        store: aggregateStoreCopy,
                        viewConfig: {
                            scrollOffset: 1,
                            rowOverCls: 'x-view-selected'
                        },
                        autoExpandColumn: 'label',
                        selModel: itemDeleter,
                        colModel: new Ext.grid.ColumnModel({
                            columns: [{
                                header: 'Type',
                                dataIndex: 'type',
                                width: 60,
                                editor: {
                                    xtype: "combo",
                                    fieldLabel: "Aggregate",
                                    name: "aggregate",
                                    ref: "aggregateField",
                                    //width: 'auto',
                                    displayField: 'name',
                                    valueField: 'value',
                                    store: aggregateOptionStore,
                                    mode: 'local',
                                    triggerAction: 'all',
                                    typeAhead: false,
                                    disableKeyFilter: true
                                }
                            },{
                                header: 'Label',
                                dataIndex: 'label',
                                id: 'label',
                                //width: 200,
                                editor: {
                                    fieldLabel: "Label",
                                    name: "label",
                                    ref: "aggregateLabelField"
                                }
                            }, itemDeleter
                            ]
                        }),
                        buttons: [{
                            text: 'Add Aggregate',
                            handler: function(btn){
                                var store = btn.findParentByType('grid').store;
                                store.add(new store.recordType({fieldKey: win.columnRecord.get('fieldKey')}))
                            }
                        }]
                    }]
                },
                buttons: [{
                    text: "OK",
                    handler: function () {
                        var title = win.titleField.getValue();
                        title = title ? title.trim() : "";
                        win.columnRecord.set("title", !Ext.isEmpty(title) ? title : undefined);

                        var error;
                        var fieldKey = win.columnRecord.get('fieldKey');
                        var aggregateStoreCopy = win.findByType('grid')[0].store;

                        //validate the records
                        aggregateStoreCopy.each(function (rec) {
                            if (!rec.get('type') && !rec.get('label'))
                            {
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
                            aggregateStore.add(new aggregateStore.recordType({
                                fieldKey: rec.get('fieldKey'),
                                type: rec.get('type'),
                                label: rec.get('label')
                            }));
                        }, this);

                        columnsList.refresh();
                        win.hide();
                    }
                },{
                    text: "Cancel",
                    handler: function () { win.hide(); }
                }]
            });
            win.initEditForm = function (columnRecord, metadataRecord)
            {
                this.columnRecord = columnRecord;
                this.metadataRecord = metadataRecord;

                this.setTitle("Edit column properties for '" + Ext.util.Format.htmlEncode(this.columnRecord.get("fieldKey")) + "'");
                this.titleField.setValue(this.columnRecord.get("title"));

                //NOTE: we make a copy of the data so we can avoid commiting updates until the user clicks OK
                aggregateStoreCopy.removeAll();
                aggregateStore.each(function(rec){
                    if (rec.get('fieldKey') == this.columnRecord.get('fieldKey'))
                    {
                        aggregateStoreCopy.add(new aggregateStoreCopy.recordType({
                            fieldKey: rec.get('fieldKey'),
                            label: rec.get('label'),
                            type: rec.get('type')
                        }));
                    }
                }, this);


                aggregateOptionStore.removeAll();
                aggregateOptionStore.add(new aggregateOptionStore.recordType({value: "", name: "[None]"}));
                Ext.each(LABKEY.Query.getAggregatesForType(metadataRecord.get('jsonType')), function(key){
                    aggregateOptionStore.add(new aggregateOptionStore.recordType({value: key.toUpperCase(), name: key.toUpperCase()}));
                }, this);

                //columnsList
                this.columnRecord.store.fireEvent('datachanged', this.columnRecord.store)

            };
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
        LABKEY.DataRegion.ColumnsTab.superclass.save.call(this, edited, urlParameters);

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

LABKEY.DataRegion.FilterTab = Ext.extend(LABKEY.DataRegion.Tab, {

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
        this.filterStore = new Ext.data.JsonStore({
            fields: ['fieldKey', 'items', 'urlParameter'],
            root: 'filter',
            idProperty: function (json) { return json.fieldKey.toUpperCase() },
            data: { filter: filters },
            remoteSort: true
        });

        this.filterStore.on({
            load: this.onStoreLoad,
            add: this.onStoreAdd,
            remove: this.onStoreRemove,
            scope: this
        });

        var thisTab = this;
        config = Ext.applyIf({
            title: "Filter",
            cls: "test-filter-tab",
            layout: "fit",
            items: [{
                ref: "filterPanel",
                title: "Selected Filters",
                xtype: "panel",
                border: false,
                style: {"border-left-width": "1px"},
                layout: {
                    type: "vbox",
                    align: "stretch"
                },
                items: [{
                    ref: "../filterList",
                    xtype: "compdataview",
                    cls: "labkey-customview-list",
                    flex: 1,
                    plugins: [ new Ext.ux.dd.GridDragDropRowOrder() ],
                    store: this.filterStore,
                    emptyText: "No filters added",
                    deferEmptyText: false,
                    multiSelect: true,
                    autoScroll: true,
                    overClass: "x-view-over",
                    itemSelector: '.labkey-customview-item',
                    tpl: new Ext.XTemplate(
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
                                        return Ext.util.Format.htmlEncode(fieldMeta.data.name);
                                    }
                                    return Ext.util.Format.htmlEncode(values.fieldKey) + " <span class='labkey-error'>(not found)</span>";
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
                                        thisTab.filterList.refresh();
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
                    }," ",{
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
                    }," ",{
                        xtype: "paperclip-button",
                        cls: "labkey-folder-filter-paperclip",
                        pressed: !this.designer.userContainerFilter,
                        tooltipType: "title",
                        disabled: !this.customView.containerFilter,
                        itemType: "container filter"
                    }]
                }
            }]
        }, config);

        LABKEY.DataRegion.FilterTab.superclass.constructor.call(this, config);

        var bbar = this.filterPanel.getBottomToolbar();
        this.containerFilterCombo = bbar.items.get(2).items.get(0);
        this.containerFilterPaperclip = bbar.items.get(4);
    },

    initComponent : function () {
        LABKEY.DataRegion.FilterTab.superclass.initComponent.apply(this, arguments);
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
        if (LABKEY.DataRegion.FilterTab.superclass.onListBeforeClick.call(this, list, index, item, e) === false) {
            return false;
        }

        var target = Ext.fly(e.getTarget());
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
        this.setTitle(title);
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

    /** Get the record, clause, clause index, and &lt;tr> for a dom node. */
    getClauseFromNode : function (recordIndex, node)
    {
        var tr = Ext.fly(node).parent("tr[clauseIndex]");
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
            Ext.each(table.query("tr[clauseIndex]"), function (row, i, all)
            {
                var clauseIndex = +row.getAttribute("clauseIndex");
                if (clauseIndex == o.clauseIndex) {
                    Ext.fly(row).remove();
                }
                else if (clauseIndex > o.clauseIndex) {
                    row.setAttribute("clauseIndex", clauseIndex - 1);
                }
            }, this);

            // adjust clauseIndex down for all components for the filter
            var cs = this.filterList.getComponents(index);
            Ext.each(cs, function (c, i, all)
            {
                if (c.clauseIndex == o.clauseIndex) {
                    Ext.destroy(c);
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
        var cs = this.filterList.getComponents(combo);
        for (var i = 0; i < cs.length; i++)
        {
            var c = cs[i];
            if (c.clauseIndex == clauseIndex && c instanceof LABKEY.ext.FilterTextValue)
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
        return this.filterList;
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


LABKEY.DataRegion.SortTab = Ext.extend(LABKEY.DataRegion.Tab, {

    constructor : function (config) {
        this.designer = config.designer;
        this.customView = config.customView;
        var fieldMetaStore = this.fieldMetaStore = config.fieldMetaStore;

        this.sortStore = new Ext.data.JsonStore({
            fields: ['fieldKey', 'dir', {name: 'urlParameter', type: 'boolean', defaultValue: false}],
            root: 'sort',
            idProperty: function (json) { return json.fieldKey.toUpperCase() },
            data: this.customView,
            remoteSort: true
        });

        this.sortStore.on({
            load: this.onStoreLoad,
            add: this.onStoreAdd,
            remove: this.onStoreRemove,
            scope: this
        });

        var thisTab = this;
        config = Ext.applyIf({
            title: "Sort",
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
                    ref: "../sortList",
                    cls: "labkey-customview-list",
                    xtype: "compdataview",
                    flex: 1,
                    store: this.sortStore,
                    plugins: [ new Ext.ux.dd.GridDragDropRowOrder() ],
                    emptyText: "No sorts added",
                    deferEmptyText: false,
                    multiSelect: true,
                    autoScroll: true,
                    overClass: "x-view-over",
                    itemSelector: '.labkey-customview-item',
                    tpl: new Ext.XTemplate(
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
                                        return Ext.util.Format.htmlEncode(fieldMeta.data.name);
                                    }
                                    return Ext.util.Format.htmlEncode(values.fieldKey) + " <span class='labkey-error'>(not found)</span>";
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
                                        thisTab.sortList.refresh();
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

        LABKEY.DataRegion.SortTab.superclass.constructor.call(this, config);
    },

    initComponent : function () {
        LABKEY.DataRegion.SortTab.superclass.initComponent.apply(this, arguments);
        this.updateTitle();
    },

    updateTitle : function ()
    {
        var count = this.sortStore.getCount();
        var title = "Sort" + (count > 0 ? " (" + count + ")" : "");
        this.setTitle(title);
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
        return this.sortList;
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

LABKEY.ext.FilterOpCombo = Ext.extend(Ext.form.ComboBox, {

    constructor : function (config) {
        this.fieldMetaStore = config.fieldMetaStore;
        this.mode = 'local';
        this.triggerAction = 'all';
        this.forceSelection = true;
        this.valueField = 'value';
        this.displayField = 'text';
        this.allowBlank = false;
        LABKEY.ext.FilterOpCombo.superclass.constructor.call(this, config);
        this.addEvents('optionsupdated');
    },

    initComponent : function () {
        LABKEY.ext.FilterOpCombo.superclass.initComponent.apply(this, arguments);
        this.setOptions();
    },

    onMouseDown : function (e) {
        // XXX: work around annoying focus bug for Fields in DataView.
        this.focus();
        LABKEY.ext.FilterOpCombo.superclass.onMouseDown.call(this, e);
    },

    /** Called once during initialization. */
    setRecord : function (filterRecord, clauseIndex) {
        this.record = filterRecord;
        this.clauseIndex = clauseIndex;
        var jsonType = undefined;
        var mvEnabled = false;
        if (this.record)
        {
            var fieldKey = this.record.data.fieldKey;
            var fieldMetaRecord = this.fieldMetaStore.getById(fieldKey.toUpperCase());
            if (fieldMetaRecord)
            {
                jsonType = fieldMetaRecord.data.jsonType;
                mvEnabled = fieldMetaRecord.data.mvEnabled;
            }
        }
        var value = this.getRecordValue();
        this.setOptions(jsonType, mvEnabled, value);

        this.setValue(value);
        this.on('blur', function (f) {
            var v = f.getValue();
            this.setRecordValue(v);
        }, this);
    },

    getRecordValue : function () {
        return this.record.get("items")[this.clauseIndex].op;
    },

    setRecordValue : function (value) {
        this.record.get("items")[this.clauseIndex].op = value;
    },

    setOptions : function (type, mvEnabled, value) {
        var found = false;
        var options = [];
        if (type)
            Ext.each(LABKEY.Filter.getFilterTypesForType(type, mvEnabled), function (filterType) {
                if (value && value == filterType.getURLSuffix())
                    found = true;
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

        var store = new Ext.data.SimpleStore({fields: ['value', 'text'], data: options });

        // Ext.form.ComboBox private method
        this.bindStore(store);
        this.fireEvent('optionsupdated', this);
    },

    getFilterType : function () {
        return LABKEY.Filter.getFilterTypeForURLSuffix(this.getValue());
    }
});
Ext.reg("labkey-filterOpCombo", LABKEY.ext.FilterOpCombo);

LABKEY.ext.FilterTextValue = Ext.extend(Ext.form.TextField, {
    onMouseDown : function (e) {
        // XXX: work around annoying focus bug for Fields in DataView.
        this.focus();
        LABKEY.ext.FilterTextValue.superclass.onMouseDown.call(this, e);
    },

    setRecord : function (filterRecord, clauseIndex) {
        this.record = filterRecord;
        this.clauseIndex = clauseIndex;

        // UGH: get the op value to set visibility on init
        var op = this.record.get("items")[this.clauseIndex].op;
        var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(op);
        this.setVisible(filterType != null && filterType.isDataValueRequired());

        var value = this.getRecordValue();
        this.setValue(value);
        this.on('blur', function (f) {
            var v = f.getValue();
            this.setRecordValue(v);
        }, this);
    },

    getRecordValue : function () {
        return this.record.get("items")[this.clauseIndex].value;
    },

    setRecordValue : function (value) {
        this.record.get("items")[this.clauseIndex].value = value;
    }

});
Ext.reg("labkey-filterValue", LABKEY.ext.FilterTextValue);

LABKEY.DataRegion.PaperclipButton = Ext.extend(Ext.Button, {

    iconCls: 'labkey-paperclip',
    iconAlign: 'top',
    enableToggle: true,

    initComponent : function () {
        this.addEvents('blur');
        LABKEY.DataRegion.PaperclipButton.superclass.initComponent.apply(this, arguments);
    },

    afterRender : function () {
        LABKEY.DataRegion.PaperclipButton.superclass.afterRender.call(this);
        this.updateToolTip();
    },

    // Called by ComponentDataView.renderItem when indexedProperty is false
    // When the record.urlParameter is true, the button is not pressed.
    setValue : function (value) {
        this.toggle(!value, true);
        this.updateToolTip();
    },

    // Called by ComponentDataView.renderItem when indexedProperty is false
    // We need to invert the value so the record.urlParameter is true when the button is not pressed.
    getValue : function () {
        return !this.pressed;
    },

    // 'blur' event needed by ComponentDataView to set the value after changing
    toggleHandler : function (btn, state) {
        this.fireEvent('blur', this);
        this.updateToolTip();
    },

    // Called by ComponentDataView.renderItem when indexedProperty is true
    setRecord : function (filterRecord, clauseIndex) {
        if (clauseIndex !== undefined)
        {
            this.record = filterRecord;
            this.clauseIndex = clauseIndex;

            var value = this.getRecordValue();
            this.setValue(value);
            this.on('toggle', function (f, pressed) {
                this.setRecordValue(!pressed);
            }, this);
        }
    },

    getRecordValue : function () {
        return this.record.get("items")[this.clauseIndex].urlParameter;
    },

    setRecordValue : function (value) {
        this.record.get("items")[this.clauseIndex].urlParameter = value;
    },

    getToolTipText : function ()
    {
        if (this.pressed) {
            return "This " + this.itemType + " will be saved with the view";
        }
        else {
            return "This " + this.itemType + " will NOT be saved as part of the view";
        }
    },

    updateToolTip : function () {
        var el = this.btnEl;
        var msg = this.getToolTipText();
        el.set({title: msg});
    }
});
Ext.reg('paperclip-button', LABKEY.DataRegion.PaperclipButton);
