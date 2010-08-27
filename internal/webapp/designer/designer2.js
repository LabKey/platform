/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

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

        // add field metadata for view's selected columns, sorts, filters
        if (this.customView)
        {
            // The store uses a reader that expectes the field metadata to be under a 'columns' property.
            this.fieldMetaStore.loadData({columns: this.customView.fields}, true);
        }


        this.columnsTab = new LABKEY.DataRegion.ColumnsTab({
            designer: this,
            schemaName: this.schemaName,
            queryName: this.queryName,
            viewName: this.viewName,
            fieldMetaStore: this.fieldMetaStore,
            customView: this.customView
        });

        this.propertiesTab = new LABKEY.DataRegion.PropertiesTab({
            designer: this,
            customView: this.customView
        });

        config = Ext.applyIf(config, {
            activeTab: 0,
            frame: false,
            shadow: true,
            width: 300,
            height: 560,
            items: [
                this.columnsTab,
                {html: "tab2", title: "Filter"},
                {html: "tab3", title: "Sort"},
                this.propertiesTab
            ],
            buttonAlign: "left",
            fbar: [{
                text: "Delete",
                tooltip: "Delete this view",
                handler: this.onDeleteClick,
                scope: this
            },"->",{
                text: "Cancel",
                tooltip: "Revert any unsaved changes",
                handler: this.onCancelClick,
                scope: this
            },{
                text: "Save",
                tooltip: "Save changes",
                handler: this.onSaveClick,
                scope: this
            }]
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

    isModified : function () {
        return this.editedView != undefined;
    },

    createEditedView : function () {
        this.editedView = Ext.applyIf({}, this.customView);
        return this.editedView;
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
        btn.on('click', this.onCancelClick, this);
        this.closeButton = btn;
    },

    onDeleteClick : function (btn, e) {
        // XXX: prompt to confirm
    },

    onCancelClick : function (btn, e) {
        // XXX: prompt for unsaved changes
        this.setVisible(false);
    },

    onSaveClick : function (btn, e) {
        this.save();
    },

    validate : function () {
        for (var i = 0; i < this.items.length; i++)
        {
            var tab = this.items.get(i);
            if (tab instanceof LABKEY.DataRegion.Tab)
            {
                if (tab.validate() === false)
                    return false;
            }
        }

        return true;
    },

    save : function () {
        if (this.fireEvent("beforeviewsave", this) !== false)
        {
            if (!this.validate())
                return false;

            var edited = { };
            for (var i = 0; i < this.items.length; i++)
            {
                var tab = this.items.get(i);
                if (tab instanceof LABKEY.DataRegion.Tab)
                {
                    Ext.applyIf(edited, tab.save());
                }
            }
            console.log("viewsave");
            console.log(edited);

            // XXX: If no view name set, prompt

            this.fireEvent("viewsave", this, edited);
        }
    }

});

LABKEY.DataRegion.Tab = Ext.extend(Ext.Panel, {
    constructor : function (config) {
        console.log("Tab");
        this.designer = config.designer;

        LABKEY.DataRegion.Tab.superclass.constructor.call(this, config);
    },

    initComponent : function () {
        LABKEY.DataRegion.Tab.superclass.initComponent.call(this);
    },

    isDirty : function () { return false; },

    validate : Ext.emptyFn,

    save : function () { return { }; },

    hasField : Ext.emptyFn
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
                    createNodeFn: { fn: this.createNodeAttrs, scope: this }
                })
            },{
                title: "Selected",
                region: "south",
                split: "true",
                xtype: "panel",
                layout: "hbox",
                pack: "end",
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
                    }],
                    onRender: function () {
                        Ext.list.ListView.prototype.onRender.apply(this, arguments);
                        this.addEvents("beforetooltipshow");
                        this.tooltip = new Ext.ToolTip({
                            renderTo: Ext.getBody(),
                            target: this.getEl(),
                            delegate: "dl",
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
                    },
                    listeners: {
                        selectionchange: this.onListSelectionChange,
                        beforetooltipshow: function (list, qt, columnRecord, node) {
                            var fieldRecord = this.fieldMetaStore.getById(columnRecord.data.fieldKey);
                            if (fieldRecord)
                                var html = LABKEY.ext.FieldMetaRecord.getToolTipHtml(fieldRecord);
                            else
                                var html = "<strong>Field not found:</strong> " + columnRecord.data.fieldKey;
                            qt.body.update(html);
                        },
                        scope: this
                    }
                },{
                    ref: "../buttonBox",
                    unstyled: true,
                    layout: "vbox",
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

    onCheckChange : function (node, checked) {
        console.log("onCheckChange");
        console.log(node);
        if (checked)
            this.addColumn(node.id);
        else
            this.removeColumn(node.id);
    },

    onListSelectionChange : function (list, selections) {
        var disabled = selections.length == 0;
        this.moveDownButton.setDisabled(disabled);
        this.moveUpButton.setDisabled(disabled);
        this.deleteButton.setDisabled(disabled);
        this.editPropsButton.setDisabled(selections.length != 1);
    },

    _moveSelectedRecords : function (moveDown) {
        var records = this.columnsList.getSelectedRecords();
        if (records == null || records.length == 0)
            return;

        // UNDONE: multi-select move is broken.  Look at http://dev.sencha.com/deploy/dev/examples/ux/ItemSelector.js
        var store = this.columnsList.getStore();
        var index = store.indexOf(records[0]);
        if (index <= 0)
            return;

        store.suspendEvents(true);
        store.remove(records);
        store.insert(index + (moveDown ? 1 : -1), records);
        store.resumeEvents();

        this.columnsList.select(records, false);
    },

    onMoveUpClick : function (btn, e) {
        this._moveSelectedRecords(false);
    },

    onMoveDownClick : function (btn, e) {
        this._moveSelectedRecords(true);
    },

    onDeleteClick : function (btn, e) {
        var records = this.columnsList.getSelectedRecords();
        if (records == null || records.length == 0)
            return;

        this.columnsStore.remove(records);
        for (var i = 0; i < records.length; i++)
        {
            var fieldKey = records[i].data.fieldKey;
            var treeNode = this.fieldsTree.getNodeById(fieldKey);
            if (treeNode)
                treeNode.getUI().toggleCheck(false);
        }
    },

    onEditPropsClick : function (btn, e) {
        alert("not yet implemented");
    },

    addColumn : function (fieldKey) {
        var column = {fieldKey: fieldKey};
        var fk = FieldKey.fromString(fieldKey);
        var index = this.fieldMetaStore.findExact("name", fieldKey);
        if (index > -1)
        {
            var record = this.fieldMetaStore.getAt(index);
            column.name = fk.name;
        }
        else
            column.name = fk.name + " (not found)";

        var record = new this.columnsStore.recordType(column);
        var selected = this.columnsList.getSelectedIndexes();
        if (selected && selected.length > 0)
        {
            var index = Math.max(selected);
            this.columnStore.insert(index+1, record);
        }
        else
        {
            this.columnsStore.add([record]);
        }
    },

    removeColumn : function (fieldKey) {
        var index = this.columnsStore.findExact("fieldKey", fieldKey);
        if (index > -1)
            this.columnsStore.removeAt(index);
    },

    // Called from FieldTreeLoader. Returns a TreeNode config for a FieldMetaRecord
    createNodeAttrs : function (record) {
        var fieldMeta = record.data;
        var attrs = {
            id: fieldMeta.name,
            text: fieldMeta.caption,
            leaf: !fieldMeta.lookup,
            checked: this.hasField(fieldMeta.name),
            hidden: fieldMeta.hidden,
            disabled: !fieldMeta.selectable,
            qtip: fieldMeta.description,
            icon: fieldMeta.keyField ? LABKEY.contextPath + "/_images/key.png" : ""
        };

        return attrs;
    },

    hasField : function (fieldKey) {
        return this.columnsStore.findExact("fieldKey", fieldKey) != -1;
    },

    validate : function () {
        // UNDONE
        return true;
    },

    save : function () {
        // HACK: I'm most likely abusing the JsonWriter APIs
        var writer = new Ext.data.JsonWriter({
            encode: false,
            writeAllFields: true,
            listful: true,
            meta: this.columnsStore.reader.meta,
            recordType: this.columnsStore.recordType
        });

        var records = this.columnsStore.getRange();
        var params = {};
        writer.apply(params, null, "create", records);
        return params.jsonData;
    }
});


LABKEY.DataRegion.PropertiesTab = Ext.extend(LABKEY.DataRegion.Tab, {
    constructor : function (config) {
        console.log("PropertiesTab");

        this.customView = config.customView;

        var disableSharedAndInherit = this.customView.hidden || this.customView.session || !this.customView.canSaveForAllUsers;

        config = Ext.applyIf({
            title: "Properties",
            layout: "form",
            defaults: {
                tooltipType: "title"
            },
            items: [{
                ref: "nameField",
                fieldLabel: "Name",
//                name: "name",
                xtype: "textfield",
                tooltip: "Name of the custom view (leave blank to save as the default grid view)",
                value: this.customView.name,
                disabled: this.customView.hidden
            },{
                ref: "sharedField",
                fieldLabel: "Shared",
//                name: "shared",
                xtype: "checkbox",
                tooltip: "Make this grid view available to all users",
                value: this.customView.shared,
                disabled: disableSharedAndInherit
            },{
                ref: "inheritField",
                fieldLabel: "Inherit",
//                name: "inherit",
                xtype: "checkbox",
                tooltip: "Make this grid view available in child folders",
                value: this.customView.inherit,
                disabled: disableSharedAndInherit
            },{
                ref: "sessionField",
                fieldLabel: "Temporary",
                xtype: "checkbox",
                tooltip: "Save this view temporarily.  Any changes will only persist for the duration of your session.",
                value: this.customView.session,
                disabled: this.customView.hidden,
                handler: function (checkbox, checked) {
                    if (checked) {
                        this.sharedField.setValue(false);
                        this.sharedField.setDisabled(true);
                        this.inheritField.setValue(false);
                        this.inheritField.setDisabled(true);
                    }
                    else {
                        if (disabledSharedInherit)
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
                Ext.MsgBox.alert("You must save this view aith an alternate name.");
                return false;
            }
        }
        if (!this.customView.canSaveForAllUsers)
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
LABKEY.ext.FieldMetaRecord.getToolTipHtml = function (record) {
    var field = record.data;
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
//        this.loaded = false;
        this.remoteSort = true;
        this.reader = new Ext.data.JsonReader({
            idProperty: 'fieldKey',
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
//        this.loaded = true;
        if (options.fk)
            this.lookupLoaded = true;
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
            // XXX: check we handle fieldKeys with '/' in them
            var prefixMatch = fieldKey == "<root>" ? "" : fieldKey;
            var collection = this.queryBy(function (r, id) {
                var idx = id.indexOf(prefixMatch);
                if (idx == 0 && id.substring(prefixMatch.length).indexOf("/") == -1)
                    return true;
                return false;
            });
            var r = collection.getRange();
            if (options.callback)
                options.callback.call(options.scope || this, r, options, true);
        }
        else
        {
            this.load({
                params: { fk: fieldKey },
                callback: options.callback.createSequence(function () { this.lookupLoaded[fieldKey] = true; }, this),
                scope: options.scope,
                add: true
            });
        }
    }

});


// This TreeLoader returns TreeNodes for field metadata and is backed by a FieldMetaStore.
LABKEY.ext.FieldTreeLoader = Ext.extend(Ext.tree.TreeLoader, {
    constructor : function (config) {
        if (!config.createNodeFn)
            throw new Error("need a FieldMetaRecord->TreeNode fn");
        this.createNodeFn = config.createNodeFn;

        this.store = config.store || new LABKEY.ext.FieldMetaStore({
            schemaName: config.schemaName,
            queryName: config.queryName
        });

//        this.rootLoader = config.rootLoader;

        // we will attach a lookup loader as needed
//        this.applyLoader = false;

        // Set url to true so TreeLoader.load will call requestData().
        this.url = true;
        LABKEY.ext.FieldTreeLoader.superclass.constructor.call(this, config);
    },

    requestData : function (node, callback, scope) {
        console.log("requestData");
        console.log(node);
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

    // attach a lookup store and the original record to the TreeNode
    createNode : function (record) {
        console.log("createNode");
        console.log(record);
        var attr = this.createNodeFn.fn.call(this.createNodeFn.scope || this, record);
        var node = LABKEY.ext.FieldTreeLoader.superclass.createNode.call(this, attr);
        //node.record = record;
//        if (record.data.lookup)
//        {
//            // Since fieldKeys are lookups are all relative to the root query table,
//            // create a new lookup store from the root node's store.
//            var loader = this.rootLoader || this;
//            var store = loader.store.getLookupStore(record);
//            attr.loader = new LABKEY.ext.FieldTreeLoader({store: store, rootLoader: loader});
//        }
        return node;
    },

    processResponse: function(response, node, callback, scope) {
        console.log("processResponse");
        var records = response.records;
        try {
            node.beginUpdate();
            for (var i = 0, len = records.length; i < len; i++) {
                var n = this.createNode(records[i]);
                if(n) {
                    node.appendChild(n);
                }
            }
            node.endUpdate();
            this.runCallback(callback, scope || node, [node]);
        } catch(e) {
            console.log("FieldTreeLoader.processResponse: " + e);
            throw e;
            this.handleFailure(response);
        }
    }

});

