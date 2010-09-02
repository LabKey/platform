/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresScript('ComponentDataView.js', true);

LABKEY.DataRegion.ViewDesigner = Ext.extend(Ext.TabPanel, {

    constructor : function (config) {

        this.cls = 'extContainer';
        this.dataRegion = config.dataRegion;

        this.schemaName = config.schemaName;
        this.queryName = config.queryName;
        this.viewName = config.viewName || "";
        this.query = config.query;
        this.customView = null;
        for (var i = 0; i < this.query.views.length; i++)
        {
            if (this.query.views[i].name == this.viewName)
            {
                this.customView = this.query.views[i];
                break;
            }
        }

        this.fieldMetaStore = new LABKEY.ext.FieldMetaStore({
            schemaName: this.schemaName,
            queryName: this.queryName,
            data: this.query
        });
        this.fieldMetaStore.loadData(this.query);

        // Add any additional field metadata for view's selected columns, sorts, filters.
        // The view may be filtered upon columns not present in the query's selected column metadata.
        if (this.customView)
        {
            // The FieldMetaStore uses a reader that expectes the field metadata to be under a 'columns' property instead of 'fields'
            this.fieldMetaStore.loadData({columns: this.customView.fields}, true);
        }

        this.showHiddenFields = config.showHiddenFields || false;

        this.columnsTab = new LABKEY.DataRegion.ColumnsTab({
            designer: this,
            schemaName: this.schemaName,
            queryName: this.queryName,
            viewName: this.viewName,
            fieldMetaStore: this.fieldMetaStore,
            customView: this.customView
        });

        this.filterTab = new LABKEY.DataRegion.FilterTab({
            designer: this,
            fieldMetaStore: this.fieldMetaStore,
            customView: this.customView
        });

        this.sortTab = new LABKEY.DataRegion.SortTab({
            designer: this,
            fieldMetaStore: this.fieldMetaStore,
            customView: this.customView
        });

        this.propertiesTab = new LABKEY.DataRegion.PropertiesTab({
            designer: this,
            customView: this.customView,
            readOnly: true
        });

        var deleteToolTip =
                (this.customView.name ? "Delete " : "Reset ") +
                (this.customView.shared ? "shared " : "your ") + "view";
        
        config = Ext.applyIf(config, {
            activeTab: 0,
            frame: false,
            shadow: true,
            width: 300,
            height: 560,
            items: [
                this.columnsTab,
                this.filterTab,
                this.sortTab,
                this.propertiesTab
            ],
            buttonAlign: "left",
            bbar: {
                xtype: 'container',
                // would like to use 'labkey-status-info' class instead of inline style, but it centers and stuff
                //cls: "labkey-status-info",
                style: { 'background-color': "#FFDF8C", padding: "2px" },
                html: "button bar",
                hidden: true
            },
            fbar: {
                xtype: 'container',
                layout: {
                    type: 'vbox',
                    pack: 'start',
                    align: 'stretch'
                },
                defaults: { flex: 1 },
                height: 27 * 2,
                items: [{
                    xtype: "toolbar",
                    toolbarCls: "x-tab-panel-footer",
                    defaults: {minWidth: 75},
                    items: [{
                        xtype: "checkbox",
                        boxLabel: "Show Hidden Fields",
                        checked: this.showHiddenFields,
                        handler: function (checkbox, checked) {
                            this.setShowHiddenFields(checked);
                        },
                        scope: this
                    }]
                },{
                    xtype: "toolbar",
                    toolbarCls: "x-tab-panel-footer",
                    defaults: {minWidth: 75},
                    items: [{
                        text: (this.customView.name ? "Delete" : "Reset"),
                        tooltip: deleteToolTip,
                        disabled: !this.canEdit(),
                        handler: this.onDeleteClick,
                        scope: this
                    },"->",{
                        text: "Apply",
                        tooltip: "Apply changes to the view",
                        handler: this.onApplyClick,
                        scope: this
                    },{
                        text: "Save",
                        tooltip: "Save changes",
                        handler: this.onSaveClick,
                        scope: this
                    }]
                }]
            }
        });

        this.addEvents({
            beforeviewsave: true,
            viewsave: true
        });

        LABKEY.DataRegion.ViewDesigner.superclass.constructor.call(this, config);
    },

    initComponent : function () {
        LABKEY.DataRegion.ViewDesigner.superclass.initComponent.call(this);
    },

    onRender : function (ct, position) {
        LABKEY.DataRegion.ViewDesigner.superclass.onRender.call(this, ct, position);

        var h = this.stripWrap.dom.offsetHeight;

        // close button on the right
        var btn = this.stripWrap.insertFirst({
            tag: "img",
            src: LABKEY.contextPath + "/_images/partdelete.gif",
            title: "Close customize view designer",
            style: {
                opacity: "0.6",
                float: "right",
                "margin-top": "8px",
                "margin-right": "8px",
                cursor: "hand"
            }
        });
        btn.on('click', this.onCloseClick, this);
        this.closeButton = btn;

        if (!this.canEdit())
        {
            var msg = "This view is not editable, but you may<br>" +
                      "save a new view with a different name.";
            // XXX: show this.editableErrors in a '?' help tooltip
            this.showMessage(msg);
        }
        else if (this.customView.session)
        {
            this.showMessage("Editing an unsaved view.");
        }
    },

    canEdit : function ()
    {
        return this.getEditableErrors().length == 0;
    },

    getEditableErrors : function ()
    {
        if (!this.editableErrors)
        {
            this.editableErrors = [];
            if (this.customView)
            {
                if (this.customView.inherit)
                {
                    if (this.customView.containerPath != LABKEY.ActionURL.getContainer())
                        this.editableErrors.push("Inherited views can only be edited from the defining folder.");
                }

                if (!this.customView.editable)
                    this.editableErrors.push("The view is read-only and cannot be edited.");
            }
        }
        return this.editableErrors;
    },

    showMessage : function (msg)
    {
        // XXX: support multiple messages and [X] close box
        var tb = this.getBottomToolbar();
        tb.getEl().update(msg);
        tb.setVisible(true);
        tb.getEl().slideIn();
        tb.getEl().on('click', function () { this.hideMessage(); }, this, {single: true});
    },

    hideMessage : function ()
    {
        var tb = this.getBottomToolbar();
        tb.getEl().update('');
        tb.setVisible(false);
        tb.getEl().slideOut();
    },

    setShowHiddenFields : function (showHidden)
    {
        this.showHiddenFields = showHidden;
        for (var i = 0; i < this.items.length; i++)
        {
            var tab = this.items.get(i);
            if (tab instanceof LABKEY.DataRegion.Tab)
                tab.setShowHiddenFields(showHidden);
        }
    },

    onCloseClick : function (btn, e)
    {
        this.setVisible(false);
    },

    onDeleteClick : function (btn, e) {
        this.dataRegion.deleteCustomView();
    },

    onApplyClick : function (btn, e) {
        // Save a session view. Session views can't be inherited or shared.
        var props = {
            name: this.customView.name,
            hidden: this.customView.hidden,
            shared: false,
            inherit: false,
            session: true
        };
        this.save(props, function () {
            this.setVisible(false);
        }, this);
    },

    onSaveClick : function (btn, e) {
        var disableSharedAndInherit = this.customView.hidden || this.customView.session || (this.customView.containerPath != LABKEY.ActionURL.getContainer());
        var canEdit = this.canEdit();
        var win = new Ext.Window({
            title: "Save Custom View" + (this.customView.name ? ": " + escape(this.customView.name) : ""),
            layout: "form",
            cls: "extContainer",
            padding: "8 8 8 4",
            modal: true,
            defaults: {
                tooltipType: "title"
            },
            items: [{
                ref: "nameField",
                fieldLabel: "Name",
                xtype: "textfield",
                tooltip: "Name of the custom view (leave blank to save as the default grid view)",
                value: canEdit ? this.customView.name : (this.customView.name ? this.customView.name + " Copy" : "Customized View"),
                disabled: this.customView.hidden
            },{
                // XXX: this looks terrible
                xtype: "box",
                html: "You must save this view with an alternate name",
                hidden: canEdit
            },{
                ref: "sharedField",
                fieldLabel: "Shared",
                xtype: "checkbox",
                tooltip: "Make this grid view available to all users",
                checked: this.customView.shared,
                disabled: disableSharedAndInherit
            },{
                ref: "inheritField",
                fieldLabel: "Inherit",
                xtype: "checkbox",
                tooltip: "Make this grid view available in child folders",
                checked: this.customView.inherit,
                disabled: disableSharedAndInherit
            },{
                ref: "sessionField",
                fieldLabel: "Temporary",
                xtype: "checkbox",
                tooltip: "Save this view temporarily.  Any changes will only persist for the duration of your session.",
                checked: this.customView.session,
                disabled: this.customView.hidden,
                handler: function (checkbox, checked) {
                    if (checked) {
                        win.sharedField.setValue(false);
                        win.sharedField.setDisabled(true);
                        win.inheritField.setValue(false);
                        win.inheritField.setDisabled(true);
                    }
                    else if (disableSharedAndInherit)
                    {
                        win.sharedField.reset();
                        win.sharedField.setDisabled(false);
                        win.inheritField.reset();
                        win.inheritField.setDisabled(false);
                    }
                },
                scope: this
            }],
            buttons: [{
                text: "Cancel",
                handler: function () { win.close(); }
            },{
                text: "Save",
                handler: function () {
                    if (!canEdit && this.customView.name == win.nameField.getValue())
                    {
                        Ext.Msg.error("You must save this view with an alternate name.");
                        return;
                    }

                    var o = {};
                    if (this.customView.hidden)
                    {
                        o = {
                            name: this.customView.name,
                            shared: this.customView.shared,
                            hidden: this.customView.hidden,
                            session: this.customView.session
                        };
                    }
                    else
                    {
                        o.name = win.nameField.getValue();
                        o.session = win.sessionField.getValue();
                        if (!o.session && this.query.canEditSharedViews)
                        {
                            o.shared = win.sharedField.getValue();
                            o.inherit = win.inheritField.getValue();
                        }
                    }
                    this.save(o);
                    win.close();
                },
                scope: this
            }]
        });
        win.show();
    },

    revert : function () {
        for (var i = 0; i < this.items.length; i++)
        {
            var tab = this.items.get(i);
            if (tab instanceof LABKEY.DataRegion.Tab)
                tab.revert();
        }
    },

    validate : function () {
        for (var i = 0; i < this.items.length; i++)
        {
            var tab = this.items.get(i);
            if (tab instanceof LABKEY.DataRegion.Tab)
            {
                if (tab.validate() === false)
                {
                    this.setActiveTab(tab);
                    return false;
                }
            }
        }

        return true;
    },

    save : function (properties, callback, scope) {
        if (this.fireEvent("beforeviewsave", this) !== false)
        {
            if (!this.validate())
                return false;

            var edited = { };
            for (var i = 0; i < this.items.length; i++)
            {
                var tab = this.items.get(i);
                if (tab instanceof LABKEY.DataRegion.Tab)
                    Ext.applyIf(edited, tab.save());
            }
            Ext.apply(edited, properties);

            this.doSave(edited, callback);
        }
    },

    // private
    doSave : function (edited, callback, scope)
    {
        LABKEY.Query.saveQueryViews({
            schemaName: this.schemaName,
            queryName: this.queryName,
            views: [ edited ],
            successCallback: function (savedViewsInfo) {
                if (callback)
                    callback.call(scope || this, savedViewsInfo);
                this.fireEvent("viewsave", this, savedViewsInfo);
            },
            scope: this
        });
    }

});

LABKEY.DataRegion.Tab = Ext.extend(Ext.Panel, {
    constructor : function (config) {
        this.designer = config.designer;
        this.unstyled = true;

        LABKEY.DataRegion.Tab.superclass.constructor.call(this, config);
    },

    initComponent : function () {
        LABKEY.DataRegion.Tab.superclass.initComponent.call(this);
        this.getList().on('selectionchange', this.onListSelectionChange, this);
        this.getList().on('render', function () {
            this.addEvents("beforetooltipshow");
            this.tooltip = new Ext.ToolTip({
                renderTo: Ext.getBody(),
                target: this.getEl(),
                delegate: this.itemSelector,
                trackMouse: true,
                listeners: {
                    beforeshow: function (qt) {
                        var node = this.getNode(qt.triggerElement);
                        var record = this.getRecord(node);
                        return this.fireEvent("beforetooltipshow", this, qt, record, node);
                    },
                    scope: this
                }
            });
        }, this.getList(), {single: true});
        this.getList().on('beforetooltipshow', this.onListBeforeToolTipShow, this);
        this.getList().on('beforeclick', this.onListBeforeClick, this);
    },

    setShowHiddenFields : Ext.emptyFn,

    isDirty : function () { return false; },

    revert : Ext.emptyFn,

    validate : Ext.emptyFn,

    save : function () {
        var store = this.getList().store;

        // HACK: I'm most likely abusing the JsonWriter APIs which could break in future versions of Ext.
        var writer = new Ext.data.JsonWriter({
            encode: false,
            writeAllFields: true,
            listful: true,
            meta: store.reader.meta,
            recordType: store.recordType
        });

        var records = store.getRange();
        var params = {};
        writer.apply(params, null, "create", records);
        return params.jsonData;
    },

    hasField : Ext.emptyFn,

    /** Get the listview for the tab. */
    getList : Ext.emptyFn,

    onListBeforeClick : function (list, index, item, e)
    {
        var node = list.getNode(index);
        if (node)
        {
            // handle node's close button click
            var closeEl = Ext.fly(node).child(".item-close", true);
            if (closeEl == e.getTarget())
            {
                this.removeRecord(index);
                return false;
            }
        }
        return true;
    },
    
    onListBeforeToolTipShow : function (list, qt, record, node)
    {
        if (record)
        {
            var fieldKey = record.data.fieldKey || record.data.name;
            var fieldMetaRecord = this.fieldMetaStore.getById(fieldKey);
            if (fieldMetaRecord)
                var html = LABKEY.ext.FieldMetaRecord.getToolTipHtml(fieldMetaRecord);
            else
                var html = "<strong>Field not found:</strong> " + fieldKey;
            qt.body.update(html);
        }
    },

    onListSelectionChange : function (list, selections) {
        var disabled = selections.length == 0;
        this.moveDownButton.setDisabled(disabled);
        this.moveUpButton.setDisabled(disabled);
        this.deleteButton.setDisabled(disabled);
        if (this.editPropsButton)
            this.editPropsButton.setDisabled(selections.length != 1);
    },

    onMoveUpClick : function (btn, e) {
        var list = this.getList();
        var record = null;
        var selectionsArray = list.getSelectedIndexes();
        selectionsArray.sort();
        var newSelectionsArray = [];
        if (selectionsArray.length > 0) {
            for (var i=0; i<selectionsArray.length; i++) {
                record = list.store.getAt(selectionsArray[i]);
                if ((selectionsArray[i] - 1) >= 0) {
                    list.store.remove(record);
                    list.store.insert(selectionsArray[i] - 1, record);
                    newSelectionsArray.push(selectionsArray[i] - 1);
                }
            }
            list.select(newSelectionsArray);
        }
    },

    onMoveDownClick : function (btn, e) {
        var list = this.getList();
        var record = null;
        var selectionsArray = list.getSelectedIndexes();
        selectionsArray.sort();
        selectionsArray.reverse();
        var newSelectionsArray = [];
        if (selectionsArray.length > 0) {
            for (var i=0; i<selectionsArray.length; i++) {
                record = list.store.getAt(selectionsArray[i]);
                if ((selectionsArray[i] + 1) < list.store.getCount()) {
                    list.store.remove(record);
                    list.store.insert(selectionsArray[i] + 1, record);
                    newSelectionsArray.push(selectionsArray[i] + 1);
                }
            }
            list.select(newSelectionsArray);
        }
    },

    onDeleteClick : function (btn, e) {
        var list = this.getList();
        var listRecords = list.getSelectedRecords();
        if (listRecords == null || listRecords.length == 0)
            return [];

        list.store.remove(listRecords);
        return listRecords;
    },

    // subclasses may override this to provide a better default
    getDefaultRecordData : function (fieldKey) { return {fieldKey: fieldKey}; },

    addRecord : function (fieldKey) {
        var list = this.getList();
        var defaultData = this.getDefaultRecordData(fieldKey);
        var columnRecord = new list.store.recordType(defaultData);
        var selected = list.getSelectedIndexes();
        if (selected && selected.length > 0)
        {
            var index = Math.max(selected);
            list.store.insert(index+1, columnRecord);
        }
        else
        {
            list.store.add([columnRecord]);
        }
    },

    removeRecord : function (fieldKeyOrIndex) {
        var list = this.getList();
        var index = -1;
        if (Ext.isNumber(fieldKeyOrIndex))
            index = fieldKeyOrIndex;
        else
            index = list.store.findExact("fieldKey", fieldKeyOrIndex);
        if (index > -1)
            list.store.removeAt(index);
    },

    onAddClick : function (btn, e) {
        var fieldKey;
        var list = this.getList();
        var selected = list.getSelectedIndexes();
        if (selected && selected.length > 0)
        {
            var record = list.store.getAt(selected[0]);
            fieldKey = record.data.fieldKey;
        }

        this.addRecord(fieldKey);
    }
});

LABKEY.DataRegion.ColumnsTab = Ext.extend(LABKEY.DataRegion.Tab, {
    constructor : function (config) {
        console.log("ColumnsTab");

        this.customView = config.customView;
        this.fieldMetaStore = config.fieldMetaStore;

        this.columnsStore = new Ext.data.JsonStore({
            fields: ['name', 'fieldKey', 'title'],
            root: 'columns',
            idProperty: 'fieldKey',
            data: this.customView,
            remoteSort: true
        });

        config = Ext.applyIf({
            title: "Columns",
            layout: "border",
            items: [{
                ref: "fieldsTree",
                title: "Available Fields",
                region: "center",
                xtype: "treepanel",
                autoScroll: true,
                border: false,
                style: {"border-bottom-width": "1px"},
                root: new Ext.tree.AsyncTreeNode({
                    id: "<root>",
                    expanded: true,
                    expandable: false,
                    draggable: false
                }),
                rootVisible: false,
                loader: new LABKEY.ext.FieldTreeLoader({
                    store: config.fieldMetaStore,
                    designer: config.designer,
                    schemaName: config.schemaName,
                    queryName: config.queryName,
                    createNodeConfigFn: { fn: this.createNodeAttrs, scope: this }
                })
            },{
                title: "Selected",
                region: "south",
                split: "true",
                xtype: "panel",
                border: false,
                style: {"border-top-width": "1px"},
                layout: {
                    type: "hbox",
                    align: "stretch"
                },
                height: 200,
                items: [{
                    ref: "../columnsList",
                    xtype: "listview",
                    flex: 1,
                    store: this.columnsStore,
                    emptyText: "No fields selected",
                    disableHeaders: true,
                    hideHeaders: true,
                    multiSelect: true,
                    columns: [{
                        header: "Name", dataIndex: "name", tpl: "{[values.title ? values.title : values.name]}"
                    }]
                },{
                    ref: "../buttonBox",
                    unstyled: true,
                    layout: {
                        type: "vbox",
                        defaultMargins: "6 0 0 0"
                    },
                    width: 28,
                    height: 100,
                    defaults: {
                        xtype: "button",
                        scale: "small",
                        tooltipType: "title",
                        disabled: "true",
                        scope: this
                    },
                    items: [{
                        ref: "../../moveDownButton",
//                        icon: LABKEY.contextPath + "/query/moveup.gif",
                        icon: LABKEY.contextPath + "/_images/uparrow.gif",
                        tooltip: "Move Down",
                        handler: this.onMoveUpClick
                    },{
                        ref: "../../moveUpButton",
//                        icon: LABKEY.contextPath + "/query/movedown.gif",
                        icon: LABKEY.contextPath + "/_images/downarrow.gif",
                        tooltip: "Move Up",
                        handler: this.onMoveDownClick
                    },{
                        ref: "../../deleteButton",
                        icon: LABKEY.contextPath + "/_images/delete.gif",
                        tooltip: "Delete",
                        handler: this.onDeleteClick
                    },{
                        ref: "../../editPropsButton",
                        icon: LABKEY.contextPath + "/_images/editprops.png",
                        tooltip: "Set Field Caption",
                        handler: this.onEditPropsClick
                    }]
                }]
            }]
        }, config);

        LABKEY.DataRegion.ColumnsTab.superclass.constructor.call(this, config);

        this.fieldsTree.on('checkchange', this.onCheckChange, this);
    },

    initComponent : function () {
        LABKEY.DataRegion.ColumnsTab.superclass.initComponent.call(this);
    },

    getList : function () { return this.columnsList; },

    onCheckChange : function (node, checked) {
        if (checked)
            this.addRecord(node.id);
        else
            this.removeRecord(node.id);
    },

    onDeleteClick : function (btn, e) {
        var columnRecords = LABKEY.DataRegion.ColumnsTab.superclass.onDeleteClick.call(this, btn, e);

        for (var i = 0; i < columnRecords.length; i++)
        {
            var fieldKey = columnRecords[i].data.fieldKey;
            var treeNode = this.fieldsTree.getNodeById(fieldKey);
            if (treeNode)
                treeNode.getUI().toggleCheck(false);
        }
    },

    onEditPropsClick : function (btn, e) {
        var columnRecords = this.columnsList.getSelectedRecords();
        if (columnRecords && columnRecords.length == 1)
        {
            var columnRecord = columnRecords[0];
            var fieldKey = columnRecord.data.fieldKey;
            Ext.Msg.prompt('Set column caption',
                    "Set caption for column '" + fieldKey + "'",
                    function (btnId, text) {
                        text = text ? text.trim() : "";
                        if (btnId == "ok" && text.length > 0)
                            columnRecord.set("title", text);
                    }, this, false, columnRecord.data.title);
        }
    },

    getDefaultRecordData : function (fieldKey) {
        if (fieldKey)
        {
            var o = {fieldKey: fieldKey};
            var fk = FieldKey.fromString(fieldKey);
            var index = this.fieldMetaStore.findExact("name", fieldKey);
            if (index > -1)
                o.name = fk.name;
            else
                o.name = fk.name + " (not found)";
            return o;
        }

        return { };
    },

    // Called from FieldTreeLoader. Returns a TreeNode config for a FieldMetaRecord
    createNodeAttrs : function (fieldMetaRecord) {
        var fieldMeta = fieldMetaRecord.data;
        var attrs = {
            id: fieldMeta.name,
            text: fieldMeta.caption,
            leaf: !fieldMeta.lookup,
            checked: this.hasField(fieldMeta.name),
            hidden: fieldMeta.hidden && !this.showHiddenFields,
            disabled: !fieldMeta.selectable, // XXX: check unselectable nodes are still expandable
            qtip: fieldMeta.description,
            icon: fieldMeta.keyField ? LABKEY.contextPath + "/_images/key.png" : ""
        };

        return attrs;
    },

    setShowHiddenFields : function (showHidden) {
        this.fieldsTree.getRootNode().cascade(function (node) {
            if (showHidden)
            {
                if (node.hidden)
                    node.ui.show();
            }
            else
            {
                var fieldKey = node.id;
                var index = this.fieldMetaStore.findExact("name", fieldKey);
                if (index > -1)
                {
                    var fieldMetaRecord = this.fieldMetaStore.getAt(index);
                    if (fieldMetaRecord.data.hidden)
                        node.ui.hide();
                }
            }
        }, this);
    },

    hasField : function (fieldKey) {
        return this.columnsStore.findExact("fieldKey", fieldKey) != -1;
    },

    revert : function () {
        // XXX:
    },

    validate : function () {
        if (this.columnsStore.getCount() == 0)
        {
            alert("You must select at least one field to display in the grid.");
            return false;

            // XXX: check each fieldKey is selected only once
        }
        return true;
    }

});


LABKEY.DataRegion.FilterItemPanel = Ext.extend(Ext.Container, {
    constructor : function (config) {
        this.fieldMetaStore = config.fieldMetaStore;

        this.items = [{
            ref: "closeButton",
            cls: 'item-close',
            xtype: 'component'
        },{
            ref: "fieldKeyButtonMenu",
            xtype: 'lk.fieldMetaButtonMenu',
            cls: 'item-fieldKey',
            applyValue: 'fieldKey',
            fieldMetaStore: this.fieldMetaStore,
            listeners: {
                select: this.updateOpComboOptions,
                blur: this.onFieldBlur,
                scope: this
            }
        },{
            ref: 'opCombo',
            xtype: 'lk.filterOpCombo',
            cls: 'item-op',
            applyValue: 'op',
            fieldMetaStore: this.fieldMetaStore,
            listeners: {
                select: this.updateValueTextFieldVisibility,
                blur: this.onFieldBlur,
                scope: this
            }
        },{
            ref: 'valueTextField',
            xtype: 'textfield',
            cls: 'item-value',
            applyValue: 'value',
            listeners: {
                blur: this.onFieldBlur,
                scope: this
            }
        }];

        LABKEY.DataRegion.FilterItemPanel.superclass.constructor.call(this, config);
    },

    setRecord : function (filterRecord) {
        this.record = filterRecord;
        this.setFieldValue(filterRecord, this.fieldKeyButtonMenu);
        this.updateOpComboOptions();
        this.setFieldValue(filterRecord, this.opCombo);
        this.updateValueTextFieldVisibility();
        this.setFieldValue(filterRecord, this.valueTextField);
    },

    setFieldValue : function (record, field) {
        if (field.applyValue)
            field.setValue(record.get(field.applyValue));
    },

    onFieldBlur : function (field) {
        // bypass record update events by setting the value directly.
        if (field.applyValue)
            this.record.data[field.applyValue] = field.getValue();
    },

    updateOpComboOptions : function () {
        var filterRecord = this.record;
        var fieldKey = filterRecord.data.fieldKey;
        if (fieldKey)
        {
            var fieldMetaRecord = this.fieldMetaStore.getById(fieldKey);
            if (fieldMetaRecord)
                this.opCombo.setOptions(fieldMetaRecord.data.jsonType);
        }
    },

    updateValueTextFieldVisibility : function (combo) {
        var filterRecord = this.record;

        var filterType = this.opCombo.getFilterType();
        this.valueTextField.setVisible(filterType != null && filterType.isDataValueRequired());
    },

    setShowHiddenFields : function (showHidden) {
        this.fieldMetaButtonMenu.setShowHiddenFields(showHidden);
    }

});
Ext.reg('lk.filteritem', LABKEY.DataRegion.FilterItemPanel);


LABKEY.DataRegion.FilterTab = Ext.extend(LABKEY.DataRegion.Tab, {
    constructor : function (config) {
        console.log("FilterTab");

        this.designer = config.designer;
        this.customView = config.customView;
        this.fieldMetaStore = config.fieldMetaStore;

        this.filterStore = new Ext.data.JsonStore({
            fields: ['fieldKey', 'op', 'value'],
            root: 'filter',
            // Note: use auto-gen id instead of idProperty: there may be more than one filter with the same fieldKey.
            //idProperty: 'fieldKey',
            data: this.customView,
            remoteSort: true
        });
        this.filterStore.on({
            load: this.onStoreLoad,
            add: this.onStoreAdd,
            remove: this.onStoreRemove,
            scope: this
        });

        config = Ext.applyIf({
            title: "Filter",
            layout: {
                type: "hbox",
                align: "stretch"
            },
            items: [{
                ref: "filterList",
                xtype: "compdataview",
                flex: 1,
                store: this.filterStore,
                emptyText: "No filters added",
                deferEmptyText: false,
                multiSelect: true,
                itemSelector: 'dt.labkey-customview-item',
                overClass: "x-view-over",
                tpl: new Ext.XTemplate(
                        '<dl>',
                        '<tpl for=".">',
                        '<dt class="labkey-customview-item"></dt>',
                        '</tpl>',
                        '</dl>'
                ),
                items: [{
                    xtype: 'lk.filteritem',
                    applyValue: 'fieldKey',
                    fieldMetaStore: this.fieldMetaStore
                }]
            },{
                ref: "buttonBox",
                unstyled: true,
                layout: {
                    type: "vbox",
                    defaultMargins: "6 0 0 0"
                },
                width: 28,
                height: 100,
                defaults: {
                    xtype: "button",
                    scale: "small",
                    tooltipType: "title",
                    disabled: true,
                    scope: this
                },
                items: [{
                    ref: "../moveDownButton",
//                        icon: LABKEY.contextPath + "/query/moveup.gif",
                    icon: LABKEY.contextPath + "/_images/uparrow.gif",
                    tooltip: "Move Down",
                    handler: this.onMoveUpClick
                },{
                    ref: "../moveUpButton",
//                        icon: LABKEY.contextPath + "/query/movedown.gif",
                    icon: LABKEY.contextPath + "/_images/downarrow.gif",
                    tooltip: "Move Up",
                    handler: this.onMoveDownClick
                },{
                    ref: "../deleteButton",
                    icon: LABKEY.contextPath + "/_images/delete.gif",
                    tooltip: "Delete",
                    handler: this.onDeleteClick
                },{
                    ref: "../addButton",
                    icon: LABKEY.contextPath + "/_images/partadded.gif",
                    tooltip: "Add",
                    handler: this.onAddClick,
                    disabled: false
                }]
            }]
        }, config);

        LABKEY.DataRegion.FilterTab.superclass.constructor.call(this, config);
    },

    initComponent : function () {
        LABKEY.DataRegion.FilterTab.superclass.initComponent.call(this);
        this.updateTitle();
    },

    updateTitle : function ()
    {
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

    setShowHiddenFields : function (showHidden) {
        // Note: this.components is a private member of ComponentDataView
        for (var i = 0; i < this.filterList.components.length; i++)
        {
            var cs = this.filterList.components[i];
            var filteritem = cs[0];
            filteritem.setShowHiddenFields(showHidden);
        }
    },

    getList : function () { return this.filterList; },

    hasField : function (fieldKey) {
        return this.filterStore.findExact("fieldKey", fieldKey) != -1;
    },

    revert : function () {

    },

    validate : function () {
        for (var i = 0; i < this.filterStore.getCount(); i++)
        {
            var filterRecord = this.filterStore.getAt(i);

            var fieldKey = filterRecord.data.fieldKey;
            if (!fieldKey) {
                alert("fieldKey required for filter");
                return false;
            }

            var fieldMetaRecord = this.fieldMetaStore.getById(fieldKey);
            if (!fieldMetaRecord) {
                alert("field not found for fieldKey '" + fieldKey + "'");
                return false;
            }

            var jsonType = fieldMetaRecord.data.jsonType;
            var filterOp = filterRecord.data.op;
            if (filterOp)
            {
                var filterType = LABKEY.Filter.filterTypeForURLSuffix(filterOp);
                if (!filterType) {
                    alert("filter type '" + filterOp + "' isn't recognized");
                    return false;
                }

                var value = filterType.validate(filterRecord.data.value, jsonType, fieldKey);
                if (value == undefined)
                    return false;
            }
        }
        return true;
    }

});

LABKEY.DataRegion.SortTab = Ext.extend(LABKEY.DataRegion.Tab, {
    constructor : function (config) {
        this.designer = config.designer;
        this.customView = config.customView;
        this.fieldMetaStore = config.fieldMetaStore;

        this.sortStore = new Ext.data.JsonStore({
            fields: ['fieldKey', 'dir'],
            root: 'sort',
            //idProperty: 'fieldKey',
            data: this.customView,
            remoteSort: true
        });
        this.sortStore.on({
            load: this.onStoreLoad,
            add: this.onStoreAdd,
            remove: this.onStoreRemove,
            scope: this
        });

        config = Ext.applyIf({
            title: "Sort",
            layout: {
                type: "hbox",
                align: "stretch"
            },
            items: [{
                ref: "sortList",
                xtype: "compdataview",
                flex: 1,
                store: this.sortStore,
                emptyText: "No sorts added",
                deferEmptyText: false,
                multiSelect: true,
                itemSelector: 'dt.labkey-customview-item',
                overClass: "x-view-over",
                tpl: new Ext.XTemplate(
                        '<dl>',
                        '<tpl for=".">',
                        '<dt class="labkey-customview-item">',
                        '<div class="item-close" title="Delete"></div>',
                        '<div class="item-fieldKey"></div>',
                        '<div class="item-dir"></div>',
                        '</dt>',
                        '</tpl>',
                        '</dl>'
                ),
                items: [{
                    xtype: 'lk.fieldMetaButtonMenu',
                    renderTarget: 'div.item-fieldKey',
                    applyValue: 'fieldKey',
                    fieldMetaStore: this.fieldMetaStore
                },{
                    xtype: 'combo',
                    renderTarget: 'div.item-dir',
                    applyValue: 'dir',
                    store: [["+", "Ascending"], ["-", "Descending"]],
                    mode: 'local',
                    triggerAction: 'all',
                    forceSelection: true,
                    allowBlank: false
                }]
            },{
                ref: "buttonBox",
                unstyled: true,
                layout: {
                    type: "vbox",
                    defaultMargins: "6 0 0 0"
                },
                width: 28,
                height: 100,
                defaults: {
                    xtype: "button",
                    scale: "small",
                    tooltipType: "title",
                    disabled: true,
                    scope: this
                },
                items: [{
                    ref: "../moveDownButton",
                    icon: LABKEY.contextPath + "/_images/uparrow.gif",
                    tooltip: "Move Down",
                    handler: this.onMoveUpClick
                },{
                    ref: "../moveUpButton",
                    icon: LABKEY.contextPath + "/_images/downarrow.gif",
                    tooltip: "Move Up",
                    handler: this.onMoveDownClick
                },{
                    ref: "../deleteButton",
                    icon: LABKEY.contextPath + "/_images/delete.gif",
                    tooltip: "Delete",
                    handler: this.onDeleteClick
                },{
                    ref: "../addButton",
                    icon: LABKEY.contextPath + "/_images/partadded.gif",
                    tooltip: "Add",
                    handler: this.onAddClick,
                    disabled: false
                }]
            }]
        }, config);

        LABKEY.DataRegion.SortTab.superclass.constructor.call(this, config);
    },

    initComponent : function () {
        LABKEY.DataRegion.SortTab.superclass.initComponent.call(this);
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

    getDefaultRecordData : function (fieldKey) {
        return {
            fieldKey: fieldKey,
            dir: "+"
        };
    },

    setShowHiddenFields : function (showHidden) {
        // Note: this.components is a private member of ComponentDataView
        for (var i = 0; i < this.sortList.components.length; i++)
        {
            var cs = this.sortList.components[i];
            var filteritem = cs[0];
            filteritem.setShowHiddenFields(showHidden);
        }
    },

    getList : function () { return this.sortList; },

    hasField : function (fieldKey) {
        return this.sortStore.findExact("fieldKey", fieldKey) != -1;
    },

    revert : function () {

    },

    validate : function () {
        return true;
    }

});

LABKEY.DataRegion.PropertiesTab = Ext.extend(Ext.Panel, {
    constructor : function (config) {
        console.log("PropertiesTab");

        this.designer = config.designer;
        this.customView = config.customView;
        this.readOnly = config.readOnly;

        var disableSharedAndInherit = this.customView.hidden || this.customView.session || !this.designer.query.canEditSharedViews;

        config = Ext.applyIf({
            title: "?", // "Properties" is too long to fit in the tab strip
            layout: "form",
            defaults: {
                tooltipType: "title"
            },
            items: [{
                ref: "nameField",
                fieldLabel: "Name",
                xtype: "textfield",
                tooltip: "Name of the custom view (leave blank to save as the default grid view)",
                value: this.customView.name,
                disabled: this.readOnly || this.customView.hidden
            },{
                ref: "sharedField",
                fieldLabel: "Shared",
                xtype: "checkbox",
                tooltip: "Make this grid view available to all users",
                checked: this.customView.shared,
                disabled: this.readOnly || disableSharedAndInherit
            },{
                ref: "inheritField",
                fieldLabel: "Inherit",
                xtype: "checkbox",
                tooltip: "Make this grid view available in child folders",
                checked: this.customView.inherit,
                disabled: this.readOnly || disableSharedAndInherit
            },{
                ref: "sessionField",
                fieldLabel: "Temporary",
                xtype: "checkbox",
                tooltip: "Save this view temporarily.  Any changes will only persist for the duration of your session.",
                checked: this.customView.session,
                disabled: this.readOnly || this.customView.hidden,
                handler: function (checkbox, checked) {
                    if (this.readOnly)
                        return;
                    if (checked) {
                        this.sharedField.setValue(false);
                        this.sharedField.setDisabled(true);
                        this.inheritField.setValue(false);
                        this.inheritField.setDisabled(true);
                    }
                    else {
                        if (disableSharedAndInherit)
                        {
                            this.sharedField.reset();
                            this.sharedField.setDisabled(false);
                            this.inheritField.reset();
                            this.inheritField.setDisabled(false);
                        }
                    }
                },
                scope: this
            }]
        }, config);

        LABKEY.DataRegion.PropertiesTab.superclass.constructor.call(this, config);
    },

    isDirty : function () {
        for (var i = 0; i < this.items.length; i++)
        {
            var field = this.items.get(i);
            if (field instanceof Ext.form.Field)
                if (field.isDirty())
                    return true;
        }
        return false;
    },

    validate : function () {
        if (!this.customView.editable)
        {
            if (!this.nameField.isDirty())
            {
                Ext.MsgBox.alert("You must save this view with an alternate name.");
                return false;
            }
        }
        if (!this.designer.query.canEditSharedViews)
        {
            // UNDONE: check shared/inherit
            // Ext.Msg.alert(...)
        }

        return true;
    },

    save : function () {
        var o = {};

        if (this.customView.hidden)
        {
            o = {
                name: this.customView.name,
                shared: this.customView.shared,
                hidden: this.customView.hidden,
                session: this.customView.session
            };
        }
        else
        {
            o.name = this.nameField.getValue();
            o.session = this.sessionField.getValue();
            if (!o.session && this.customView.canSaveForAllUsers)
            {
                o.shared = this.sharedField.getValue();
                o.inherit = this.inheritField.getValue();
            }
        }

        return o;
    }

});

Ext.namespace('LABKEY', 'LABKEY.ext');

/** An Ext.data.Record constructor for LABKEY.Query.FieldMetaData json objects. */
LABKEY.ext.FieldMetaRecord = Ext.data.Record.create([
    'name',
    'fieldKey',
    'description',
    'friendlyType',
    'type',
    'jsonType',
    'autoIncrement',
    'hidden',
    'keyField',
    'mvEnabled',
    'nullable',
    'readOnly',
    'userEditable',
    'versionField',
    'selectable',
    'showInInsertView',
    'showInUpdateView',
    'showInDetailsView',
    'importAliases',
    'tsvFormat',
    'format',
    'excelFormat',
    'inputType',
    'caption',
    'lookup'
]);
LABKEY.ext.FieldMetaRecord.getToolTipHtml = function (fieldMetaRecord) {
    var field = fieldMetaRecord.data;
    var body = "<table>";
    if (field.description)
    {
        body += "<tr><td valign='top'><strong>Description:</strong></td><td>" + field.description + "</td></tr>";
    }
    body += "<tr><td valign='top'><strong>Field&nbsp;key:</strong></td><td>" + field.fieldKey + "</td></tr>";
    if (field.friendlyType)
    {
        body += "<tr><td valign='top'><strong>Data&nbsp;type:</strong></td><td>" + field.friendlyType + "</td></tr>";
    }
    if (field.hidden)
    {
        body += "<tr><td valign='top'><strong>Hidden:</strong></td><td>" + field.hidden + "</td></tr>";
    }
    body += "</table>";
    return body;
};

/**
 * An Ext.data.Store for LABKEY.Query.FieldMetaData json objects.
 */
LABKEY.ext.FieldMetaStore = Ext.extend(Ext.data.Store, {
    constructor : function (config) {
        console.log("FieldMetaStore");

        if (config.schemaName && config.queryName) {
            var params = { schemaName: config.schemaName, queryName: config.queryName };
            if (config.fk)
                params.fk = config.fk;
            this.url = LABKEY.ActionURL.buildURL("query", "getQueryDetails",
                config.containerPath, params);
        }

        this.isLoading = false;
        this.remoteSort = true;
        this.reader = new Ext.data.JsonReader({
            idProperty: "name", // name is actually the fieldKey
            root: 'columns',
            fields: LABKEY.ext.FieldMetaRecord
        });

        LABKEY.ext.FieldMetaStore.superclass.constructor.call(this, config);
        this.on('beforeload', this.onBeforeLoad, this);
        this.on('load', this.onLoad, this);
        this.on('loadexception', this.onLoadException, this);
    },

    onBeforeLoad : function() {
        console.log("onBeforeLoad");
        this.isLoading = true;
    },

    onLoad : function(store, records, options) {
        console.log("onLoad");
        this.isLoading = false;
    },

    onLoadException : function(proxy, options, response, error)
    {
        console.log("onLoadException");

        this.isLoading = false;
        var loadError = {message: error};

        if(response && response.getResponseHeader
                && response.getResponseHeader("Content-Type").indexOf("application/json") >= 0)
        {
            var errorJson = Ext.util.JSON.decode(response.responseText);
            if(errorJson && errorJson.exception)
                loadError.message = errorJson.exception;
        }

        this.loadError = loadError;
    },

    /**
     * Loads records for the given lookup fieldKey.  The fieldKey is the relative to the base query.
     * The special fieldKey '<root>' returns the records in the base query.
     * 
     * @param options
     * @param {String} [options.fieldKey] Either fieldKey or record is required.
     * @param {FieldMetaRecord} [options.record] Either fieldKey or FieldMetaRecord is required.
     * @param {Function} [options.callback] A function called when the records have been loaded.  The function accepts the following parameters:
     * <ul>
     *   <li><b>records:</b> The Array of records loaded.
     *   <li><b>options:</b> The options object passed into the this function.
     *   <li><b>success:</b> A boolean indicating success.
     * </ul>
     * @param {Object} [options.scope] The scope the callback will be called in.
     */
    loadLookup : function (options) {
        console.log("loadLookup");
        console.log(options);

        // The record's name is the fieldKey relative to the root query table.
        var fieldKey = options.fieldKey || (options.record && options.record.data.name);
        if (!fieldKey)
            throw new Error("fieldKey or record is required");

        if (!this.lookupLoaded)
            this.lookupLoaded = {};

        if (fieldKey == "<root>" || this.lookupLoaded[fieldKey])
        {
            var r = this.queryLookup(fieldKey);
            if (options.callback)
                options.callback.call(options.scope || this, r, options, true);
        }
        else
        {
            var o = Ext.applyIf({
                params: { fk: fieldKey },
                callback: options.callback.createSequence(function () { this.lookupLoaded[fieldKey] = true; }, this),
                add: true
            }, options);

            this.load(o);
        }
    },
    
    queryLookup : function (fieldKey)
    {
        // XXX: check we handle fieldKeys with '/' in them
        var prefixMatch = fieldKey == "<root>" ? "" : (fieldKey + "/");
        var collection = this.queryBy(function (r, id) {
            var fieldKey = id;
            var idx = fieldKey.indexOf(prefixMatch);
            if (idx == 0 && fieldKey.substring(prefixMatch.length).indexOf("/") == -1)
                return true;
            return false;
        });
        return collection.getRange();
    }

});


/**
 * Creates an Ext.menu.Menu bound to a LABKEY.ext.FieldMetaStore.
 * Implements enough of Field for ComponentDataView and FilterItemPanel to call getValue/setValue.
 */
LABKEY.ext.FieldMetaButtonMenu = Ext.extend(Ext.Button, {
    constructor: function (config) {
        console.log("FieldMetaButtonMenu");
        this.fieldMetaStore = config.fieldMetaStore;
        this.originalValue = config.fieldKey;
        this.menu = this.createLookupMenu("<root>");
        this.showHiddenFields = config.showHiddenFields || false;
        this.showHiddenFieldsChanged = false;
        LABKEY.ext.FieldMetaButtonMenu.superclass.constructor.call(this, config);

        this.addEvents({
            beforeselect : true,
            select: true,
            blur: true
        });
    },

    initComponent : function () {
        LABKEY.ext.FieldMetaButtonMenu.superclass.initComponent.call(this);
        // UNDONE: load subtrees on path of selected fieldKey ?

        this.setValue(this.originalValue);
    },

    setShowHiddenFields : function (showHidden) {
        this.showHiddenFieldsChanged = showHidden !== this.showHiddenFields;
        this.showHiddenFields = showHidden;
    },

    setRecord : function (filterRecord) {
        this.record = filterRecord;
    },

    // Pretend to be Field.isDirty()
    isDirty : function () {
        if (this.disabled || !this.rendered)
            return false;
        return String(this.getValue()) !== String(this.originalValue);
    },

    // Pretend to be Field.setValue().
    // ComponentDataView will call this with the value of the field named in applyValue.
    setValue : function (fieldKey) {
        this.fieldKey = fieldKey;
        var text = fieldKey || "<i>Select a field</i>";
        this.setText(text);
    },

    // Pretend to be Field.getValue().
    getValue : function () {
        return this.fieldKey;
    },

    // Pretend to be Field.onBlur().
    // ComponentDataView will listen for 'blur' events to get the new value.
    onBlur : function (e) {
        LABKEY.ext.FieldMetaButtonMenu.superclass.onBlur.call(this, e);
        this.fireEvent('blur', this); // notify the ComponentDataView the value changed
    },

    /** Get the FieldMetaRecord for the selected fieldKey. */
    getSelected : function () {
        return this.fieldMetaStore.getById(this.fieldKey);
    },

    _onMenuShow : function (menu) {
        if (!menu.loadedLookup)
        {
            menu.loadedLookup = true;
            var fieldKey = menu.fieldKey;
            this.fieldMetaStore.loadLookup({fieldKey: fieldKey, menu: menu, callback: this.onLoadItems, scope: this});
        }
    },

    _onMenuBeforeShow : function (menu) {
        if (this.showHiddenFieldsChanged)
        {
            menu.items.each(function (item, index, len) {
                if (this.showHiddenFields)
                    item.show();
                else
                {
                    var fieldKey = item.fieldKey;
                    var index = this.fieldMetaStore.findExact("name", fieldKey);
                    if (index > -1)
                    {
                        var fieldMetaRecord = this.fieldMetaStore.getAt(index);
                        if (fieldMetaRecord.data.hidden)
                            item.hide();
                    }
                }
            }, this);
            this.showHiddenFieldsChanged = false;
        }
    },

    _onMenuItemClick : function (item, e) {
        var fieldKey = item.fieldKey;
        var fieldMetaRecord = this.fieldMetaStore.getById(fieldKey);
        if (this.fireEvent('beforeselect', this, fieldMetaRecord) !== false)
        {
            this.setValue(fieldKey);
            this.fireEvent('blur', this); // notify the ComponentDataView the value changed
            this.fireEvent('select', this, fieldMetaRecord);
        }
    },

    onLoadItems : function (fieldMetaRecords, options, success) {
        var menu = options.menu;
        for (var i = 0; i < fieldMetaRecords.length; i++)
        {
            var fieldMetaRecord = fieldMetaRecords[i];
            var config = this.createMenuConfig(fieldMetaRecord);
            menu.add(config);
        }
    },

    createMenuConfig : function (fieldMetaRecord) {
        var fieldMeta = fieldMetaRecord.data;
        var attrs = {
            fieldKey: fieldMeta.name,
            text: fieldMeta.caption || fieldMeta.fieldKey,
            hidden: fieldMeta.hidden && !this.showHiddenFields,
            disabled: !fieldMeta.selectable,
            icon: fieldMeta.keyField ? LABKEY.contextPath + "/_images/key.png" : ""
        };
        if (fieldMeta.lookup)
        {
            attrs.menu = this.createLookupMenu(fieldMeta.name);
        }
        return attrs;
    },

    createLookupMenu : function (fieldKey) {
        return {
            cls: "extContainer",
            fieldKey: fieldKey,
            listeners: {
                beforeshow: {
                    fn: this._onMenuBeforeShow,
                    scope: this
                },
                show: {
                    fn: this._onMenuShow,
                    scope: this
                },
                itemclick: {
                    fn: this._onMenuItemClick,
                    scope: this
                }
            }
        };
    }
});
Ext.reg('lk.fieldMetaButtonMenu', LABKEY.ext.FieldMetaButtonMenu);


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
        LABKEY.ext.FilterOpCombo.superclass.initComponent.call(this);
        this.setOptions();
    },

    setRecord : function (filterRecord) {
        this.record = filterRecord;
        var jsonType = undefined;
        if (this.record)
        {
            var fieldMetaRecord = this.fieldMetaStore.getById(this.record.data.fieldKey);
            if (fieldMetaRecord)
                jsonType = fieldMetaRecord.data.jsonType;
        }
        this.setOptions(jsonType);
    },

    setOptions : function (type) {
        var options = [];
        if (type)
            Ext.each(LABKEY.Filter.getFlterTypesForType(type), function (filterType) {
                options.push([filterType.getURLSuffix(), filterType.getDisplayText()]);
            });

        var store = new Ext.data.SimpleStore({fields: ['value', 'text'], data: options });

        // Ext.form.ComboBox private method
        this.bindStore(store);
        this.fireEvent('optionsupdated', this);
    },

    getFilterType : function () {
        return LABKEY.Filter.getFilterTypeForURLSuffix(this.getValue());
    }
});
Ext.reg("lk.filterOpCombo", LABKEY.ext.FilterOpCombo);

// This TreeLoader returns TreeNodes for field metadata and is backed by a FieldMetaStore.
LABKEY.ext.FieldTreeLoader = Ext.extend(Ext.tree.TreeLoader, {
    constructor : function (config) {
        if (!config.createNodeConfigFn)
            throw new Error("need a FieldMetaRecord->TreeNode fn");
        this.createNodeConfigFn = config.createNodeConfigFn;

        this.store = config.store || new LABKEY.ext.FieldMetaStore({
            schemaName: config.schemaName,
            queryName: config.queryName
        });

        // Set url to true so TreeLoader.load() will call requestData().
        this.url = true;
        LABKEY.ext.FieldTreeLoader.superclass.constructor.call(this, config);
    },

    requestData : function (node, callback, scope) {
        //console.log("requestData");
        //console.log(node);
        if (this.fireEvent("beforeload", this, node, callback) !== false) {
            this.store.loadLookup({
                fieldKey: node.id || "<root>",
                callback: function (r, options, success) {
                    this.handleResponse({
                        records: r,
                        argument: {node: node, callback: callback, scope: scope}
                    });
                },
                scope: this
            });

        } else {
            // if the load is cancelled, make sure we notify
            // the node that we are done
            this.runCallback(callback, scope || node, []);
        }
    },

//    getNodeFieldKey : function (node) {
//        var path = node.getPath();
//        if (path.indexOf("/<root>") == 0)
//            path = path.substring("/<root>/".length);
//        if (path.length == 0)
//            return null;
//        return path;
//    },

    // create a new TreeNode from the record.
    createNode : function (fieldMetaRecord) {
        //console.log("createNode");
        //console.log(fieldMetaRecord);
        var attr = this.createNodeConfigFn.fn.call(this.createNodeConfigFn.scope || this, fieldMetaRecord);
        var node = LABKEY.ext.FieldTreeLoader.superclass.createNode.call(this, attr);
        return node;
    },

    processResponse: function(response, node, callback, scope) {
        var fieldMetaRecords = response.records;
        try {
            node.beginUpdate();
            for (var i = 0, len = fieldMetaRecords.length; i < len; i++) {
                var n = this.createNode(fieldMetaRecords[i]);
                if(n) {
                    node.appendChild(n);
                }
            }
            node.endUpdate();
            this.runCallback(callback, scope || node, [node]);
        } catch(e) {
            console.log("Error in FieldTreeLoader.processResponse: " + e);
            throw e;
            this.handleFailure(response);
        }
    }

});

