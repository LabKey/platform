/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.ext.SplitGroupTabPanel = Ext.extend(Ext.ux.GroupTabPanel, {
    constructor : function (config) {
        this.cls = 'vertical-tabs extContainer';
        this.splitItem = this.lookupComponent(config.splitItem);
        delete config.splitItem;
        LABKEY.ext.SplitGroupTabPanel.superclass.constructor.call(this, config);

        this.layout.layoutOnCardChange = true;
    },

    initComponent : function () {
        LABKEY.ext.SplitGroupTabPanel.superclass.initComponent.call(this);
    },

    render : function (ct, position) {
        LABKEY.ext.SplitGroupTabPanel.superclass.render.call(this, ct, position);
    },

    afterRender : function () {
        var splitItem = this.splitItem;
        splitItem.render(this.bwrap, 0);
        splitItem.getEl().applyStyles({float: "left"});
        this.splitResizer = new Ext.Resizable(splitItem.getEl(), {
            handles: 'e',
            //pinned: true, // always show the splitter
            constrainTo: this.bwrap,
            minWidth: 100,
            resizeElement : function () {
                var box = this.proxy.getBox();
                splitItem.setWidth(box.width);
                if (splitItem.layout) {
                    splitItem.doLayout();
                }
                return box;
            }
        });
        this.splitResizer.on('resize', this.adjustCenterSize, this);

        LABKEY.ext.SplitGroupTabPanel.superclass.afterRender.apply(this, arguments);
    },

    onResize : function (adjWidth, adjHeight, rawWidth, rawHeight) {
        LABKEY.ext.SplitGroupTabPanel.superclass.onResize.apply(this, arguments);

        this.splitItem.setWidth(Math.floor((this.el.getWidth() - this.header.getWidth())/2));
        this.body.setWidth(this.el.getWidth() - this.splitItem.getWidth() - this.header.getWidth());
        this.splitItem.setHeight(this.body.getHeight());
    },

    adjustCenterSize : function (resizer, w, h, e) {
        this.body.setWidth(this.el.getWidth() - this.splitItem.getWidth() - this.header.getWidth());

        // update the item sizes based on the centerItem
        this.doLayout();
    },

    beforeDestroy : function () {
        LABKEY.ext.SplitGroupTabPanel.superclass.beforeDestroy.call(this);
        if (this.splitItem)
            this.splitItem.destroy();
        if (this.splitResizer)
            this.splitResizer.destroy();
    }
});
Ext.reg('splitgrouptab', LABKEY.ext.SplitGroupTabPanel);

LABKEY.DataRegion.ViewDesigner = Ext.extend(LABKEY.ext.SplitGroupTabPanel, {

    constructor : function (config) {

        this.tabWidth = 80;
        this.activeGroup = 0;

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

        if (this.customView)
        {
            // Add any additional field metadata for view's selected columns, sorts, filters.
            // The view may be filtered or sorted upon columns not present in the query's selected column metadata.
            // The FieldMetaStore uses a reader that expects the field metadata to be under a 'columns' property instead of 'fields'
            this.fieldMetaStore.loadData({columns: this.customView.fields}, true);

            // Add user filters
            this.userFilter = this.dataRegion.getUserFilter();
            for (var i = 0; i < this.userFilter.length; i++)
            {
                // copy the filter so the original userFilter isn't modified by the designer
                var userFilter = Ext.apply({urlParameter: true}, this.userFilter[i]);
                this.customView.filter.unshift(userFilter);
            }

            // Add user sort
            this.userSort = this.dataRegion.getUserSort();
            var newSortArray = [];
            for (var i = 0; i < this.userSort.length; i++)
            {
                // copy the sort so the original userSort isn't modified by the designer
                var userSort = Ext.apply({urlParameter: true}, this.userSort[i]);
                newSortArray.push(userSort);
            }

            for (var i = 0; i < this.customView.sort.length; i++)
            {
                var sort = this.customView.sort[i];
                var found = false;
                for (var j = 0; j < newSortArray.length; j++)
                {
                    if (sort.fieldKey == newSortArray[j].fieldKey)
                    {
                        found = true;
                        break;
                    }
                }
                if (!found)
                    newSortArray.push(sort);
            }
            this.customView.sort = newSortArray;

            // Add user containerFilter
            this.userContainerFilter = this.dataRegion.getUserContainerFilter();
            if (this.userContainerFilter && this.customView.containerFilter != this.userContainerFilter)
                this.customView.containerFilter = this.userContainerFilter;
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

//        this.propertiesTab = new LABKEY.DataRegion.PropertiesTab({
//            designer: this,
//            customView: this.customView,
//            readOnly: true
//        });

        this.fieldsTree = new Ext.tree.TreePanel({
            autoScroll: true,
            border: false,
            //width: 230,
            root: new Ext.tree.AsyncTreeNode({
                id: "<root>",
                expanded: true,
                expandable: false,
                draggable: false
            }),
            rootVisible: false,
            loader: new LABKEY.ext.FieldTreeLoader({
                store: this.fieldMetaStore,
                designer: this,
                schemaName: this.schemaName,
                queryName: this.queryName,
                createNodeConfigFn: {fn: this.createNodeAttrs, scope: this }
            }),
            fbar: [{
                xtype: "checkbox",
                boxLabel: "Show Hidden Fields",
                checked: this.showHiddenFields,
                handler: function (checkbox, checked) {
                    this.setShowHiddenFields(checked);
                },
                scope: this
            }]
        });

        var deleteToolTip =
                (this.customView.name ? "Delete " : "Reset ") +
                (this.customView.shared ? "shared " : "your ") + "view";

        config = Ext.applyIf(config, {
            activeTab: 0,
            frame: false,
            shadow: true,
            height: 280,
            splitItem: {
                title: "Available Fields",
                xtype: 'panel',
                layout: 'fit',
                autoScroll: true,
                border: false,
                style: { "border-left-width": "1px", "border-top-width": "1px", "border-bottom-width": "1px" },
                items: [ this.fieldsTree ],
            },
            items: [{
                xtype: 'grouptab',
                items: [ this.columnsTab ]
            },{
                xtype: 'grouptab',
                layoutOnTabChange: true,
                items: [ this.filterTab ]
            },{
                xtype: 'grouptab',
                layoutOnTabChange: true,
                items: [ this.sortTab ]
            }],
            buttonAlign: "left",
            bbar: {
                xtype: 'container',
                // would like to use 'labkey-status-info' class instead of inline style, but it centers and stuff
                //cls: "labkey-status-info",
                style: { 'background-color': "#FFDF8C", padding: "2px" },
                html: "bottom bar",
                hidden: true
            },
            fbar: [{
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
        });

        this.addEvents({
            beforeviewsave: true,
            viewsave: true
        });

        LABKEY.DataRegion.ViewDesigner.superclass.constructor.call(this, config);

        this.fieldsTree.on('checkchange', this.onCheckChange, this);
        this.on('tabchange', this.onTabChange, this);
    },

    initComponent : function () {
        LABKEY.DataRegion.ViewDesigner.superclass.initComponent.call(this);
    },

    onRender : function (ct, position) {
        LABKEY.DataRegion.ViewDesigner.superclass.onRender.call(this, ct, position);
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

    beforeDestroy : function () {
        LABKEY.DataRegion.ViewDesigner.superclass.beforeDestroy.call(this);
        if (this.columnsTab)
            this.columnsTab.destroy();
        if (this.filterTab)
            this.filterTab.destroy();
        if (this.sortTab)
            this.sortTab.destroy();
        if (this.fieldMetaStore)
            this.fieldMetaStore.destroy();
        if (this.dataRegion)
            delete this.dataRegion;
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
        // UNDONE: bottom bar isn't rendering in the GroupTabPanel
        var tb = this.getBottomToolbar();
        if (tb.getEl())
        {
            tb.getEl().update(msg);
            tb.setVisible(true);
            tb.getEl().slideIn();
            tb.getEl().on('click', function () { this.hideMessage(); }, this, {single: true});
        }
        else
        {
            tb.on('afterrender', function () { this.showMessage(msg); }, this, {single: true});
        }
    },

    hideMessage : function ()
    {
        var tb = this.getBottomToolbar();
        tb.getEl().update('');
        tb.setVisible(false);
        tb.getEl().slideOut();
    },

    getDesignerTabs : function ()
    {
        return [this.columnsTab, this.filterTab, this.sortTab];
    },

    getActiveDesignerTab : function ()
    {
        if (this.activeGroup !== undefined)
        {
            var group = (typeof this.activeGroup == 'object') ? this.activeGroup : this.items.get(this.activeGroup);
            if (group)
            {
                var tab = group.activeTab || group.items.get(0);
                if (tab instanceof LABKEY.DataRegion.Tab)
                    return tab;
            }
        }

        return undefined;
    },

    setShowHiddenFields : function (showHidden)
    {
        this.showHiddenFields = showHidden;

        // show hidden fields in fieldsTree
        this.fieldsTree.getRootNode().cascade(function (node) {
            if (showHidden)
            {
                if (node.hidden)
                    node.ui.show();
            }
            else
            {
                if (node.attributes.hidden)
                    node.ui.hide();
            }
        }, this);

        var tabs = this.getDesignerTabs();
        for (var i = 0; i < tabs.length; i++)
        {
            var tab = tabs[i];
            if (tab instanceof LABKEY.DataRegion.Tab)
                tab.setShowHiddenFields(showHidden);
        }
    },

    // Called from FieldTreeLoader. Returns a TreeNode config for a FieldMetaRecord.
    // This method is necessary since we need to determine checked state of the tree
    // using the columnStore.
    createNodeAttrs : function (fieldMetaRecord) {
        var fieldMeta = fieldMetaRecord.data;
        var text = fieldMeta.name;
        if (fieldMeta.caption && fieldMeta.caption != "&nbsp;")
            text = fieldMeta.caption;
        var attrs = {
            id: fieldMeta.fieldKey,
            fieldKey: fieldMeta.fieldKey,
            text: text,
            leaf: !fieldMeta.lookup,
            //checked: fieldMeta.selectable ? this.hasField(fieldMeta.fieldKey) : undefined,
            checked: this.hasField(fieldMeta.fieldKey),
            disabled: !fieldMeta.selectable,
            hidden: fieldMeta.hidden && !this.showHiddenFields,
            qtip: fieldMeta.description,
            icon: fieldMeta.keyField ? LABKEY.contextPath + "/_images/key.png" : ""
        };

        return attrs;
    },

    hasField : function (fieldKey) {
        var tab = this.getActiveDesignerTab();
        if (tab)
            return tab.hasField(fieldKey);
    },

    addRecord : function (fieldKey) {
        var tab = this.getActiveDesignerTab();
        if (tab)
            return tab.addRecord(fieldKey);
    },

    removeRecord : function (fieldKey)
    {
        var tab = this.getActiveDesignerTab();
        if (tab)
            return tab.removeRecord(fieldKey);
    },

    onCheckChange : function (node, checked) {
        if (checked)
            this.addRecord(node.id);
        else
            this.removeRecord(node.id);
    },

    onTabChange : function () {
        var tab = this.getActiveDesignerTab();
        if (tab instanceof LABKEY.DataRegion.Tab)
        {
            // get the checked fields from the new tab's store
            var storeRecords = tab.getList().getStore().getRange();
            var checkedFieldKeys = { };
            for (var i = 0; i < storeRecords.length; i++)
                checkedFieldKeys[storeRecords[i].get("fieldKey")] = true;

            // suspend check events so checked items aren't re-added to the tab's store
            this.fieldsTree.suspendEvents();
            this.fieldsTree.root.cascade(function () {
                var fieldKey = this.id;
                this.getUI().toggleCheck(fieldKey in checkedFieldKeys);
            });
            this.fieldsTree.resumeEvents();
        }
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
        var disableSharedAndInherit = LABKEY.user.isGuest || this.customView.hidden || this.customView.session || (this.customView.containerPath != LABKEY.ActionURL.getContainer());
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
                checked: LABKEY.user.isGuest || this.customView.session,
                disabled: LABKEY.user.isGuest || this.customView.hidden,
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
        var tabs = this.getDesignerTabs();
        for (var i = 0; i < tabs.length; i++)
        {
            var tab = tabs[i];
            if (tab instanceof LABKEY.DataRegion.Tab)
                tab.revert();
        }
    },

    validate : function () {
        var tabs = this.getDesignerTabs();
        for (var i = 0; i < tabs.length; i++)
        {
            var tab = tabs[i];
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
            var urlParameters = { };
            var tabs = this.getDesignerTabs();
            for (var i = 0; i < tabs.length; i++)
            {
                var tab = tabs[i];
                if (tab instanceof LABKEY.DataRegion.Tab)
                    tab.save(edited, urlParameters);
            }
            Ext.apply(edited, properties);

            this.doSave(edited, urlParameters, callback);
        }
    },

    // private
    doSave : function (edited, urlParameters, callback, scope)
    {
        LABKEY.Query.saveQueryViews({
            schemaName: this.schemaName,
            queryName: this.queryName,
            views: [ edited ],
            successCallback: function (savedViewsInfo) {
                if (callback)
                    callback.call(scope || this, savedViewsInfo, urlParameters);
                this.fireEvent("viewsave", this, savedViewsInfo, urlParameters);
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
        this.getList().on('render', function (list) {
            /*
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
            */
        }, this.getList(), {single: true});
        this.getList().on('beforetooltipshow', this.onListBeforeToolTipShow, this);
        this.getList().on('beforeclick', this.onListBeforeClick, this);
    },

    setShowHiddenFields : Ext.emptyFn,

    isDirty : function () { return false; },

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
                        if (this[fnName])
                            return this[fnName].call(this, index, item, e);
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

    onToolPin : function (index, item, e)
    {
        this.pinRecord(index, true);
        Ext.fly(e.getTarget()).replaceClass("labkey-tool-pin", "labkey-tool-unpin");
        return false;
    },

    onToolUnpin : function (index, item, e)
    {
        this.pinRecord(index, false);
        Ext.fly(e.getTarget()).replaceClass("labkey-tool-unpin", "labkey-tool-pin");
        return false;
    },

    getFieldMetaRecord : function (fieldKey)
    {
        return this.fieldMetaStore.getById(fieldKey);
    },

    onListBeforeToolTipShow : function (list, qt, record, node)
    {
        if (record)
        {
            var fieldKey = record.data.fieldKey;
            var fieldMetaRecord = this.getFieldMetaRecord(fieldKey);
            if (fieldMetaRecord)
                var html = LABKEY.ext.FieldMetaRecord.getToolTipHtml(fieldMetaRecord);
            else
                var html = "<strong>Field not found:</strong> " + fieldKey;
            qt.body.update(html);
        }
    },

    onListSelectionChange : function (list, selections) {
    },

    // subclasses may override this to provide a better default
    createDefaultRecordData : function (fieldKey) { return {fieldKey: fieldKey}; },

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
        else
        {
            list.store.add([record]);
        }
    },

    getRecordIndex : function (fieldKeyOrIndex) {
        var list = this.getList();
        var index = -1;
        if (Ext.isNumber(fieldKeyOrIndex))
            index = fieldKeyOrIndex;
        else
            index = list.store.findExact("fieldKey", fieldKeyOrIndex);
        return index;
    },

    getRecord : function (fieldKeyOrIndex) {
        var index = this.getRecordIndex(fieldKeyOrIndex);
        if (index > -1)
            return this.getList().store.getAt(index);
        return null;
    },

    removeRecord : function (fieldKeyOrIndex) {
        var index = this.getRecordIndex(fieldKeyOrIndex);
        var record = this.getList().store.getAt(index);
        if (record)
        {
            // remove from the store and select sibling
            this.getList().store.removeAt(index);
            var i = index < this.getList().store.getCount() ? index : index-1;
            if (i > -1)
                this.getList().select(i);

            // uncheck the field tree
            var fieldKey = record.data.fieldKey;
            var treeNode = this.designer.fieldsTree.getNodeById(fieldKey);
            if (treeNode)
                treeNode.getUI().toggleCheck(false);
        }
    },

    pinRecord : function (fieldKeyOrIndex, pin) {
        var index = this.getRecordIndex(fieldKeyOrIndex);
        if (index > -1)
            this.getList().store.getAt(index).set("urlParameter", pin);
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
        //console.log("ColumnsTab");

        this.customView = config.customView;
        this.fieldMetaStore = config.fieldMetaStore;

        this.columnStore = new Ext.data.JsonStore({
            fields: ['name', 'fieldKey', 'title'],
            root: 'columns',
            idProperty: 'fieldKey',
            data: this.customView,
            remoteSort: true
        });

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
                    plugins: [ new Ext.ux.dd.GridDragDropRowOrder() ],
                    flex: 1,
                    store: this.columnStore,
                    emptyText: "No fields selected",
                    multiSelect: true,
                    autoScroll: true,
                    overClass: "x-view-over",
                    itemSelector: ".labkey-customview-item",
                    tpl: new Ext.XTemplate(
                            '<tpl for=".">',
                            '<table width="100%" cellspacing="0" cellpadding="0" class="labkey-customview-item labkey-customview-columns-item" fieldKey="{fieldKey}">',
                            '  <tr>',
                            '    <td class="labkey-grab"></td>',
                            '    <td><span class="item-caption">{[values.title || values.name]}</span></td>',
                            '    <td width="15px"><div class="labkey-tool labkey-tool-gear" title="Edit Title"></div></td>',
                            '    <td width="15px"><span class="labkey-tool labkey-tool-close" title="Remove column"></span></td>',
                            '  </tr>',
                            '</table>',
                            '</tpl>'
                    )
                }]
            }]
        }, config);

        LABKEY.DataRegion.ColumnsTab.superclass.constructor.call(this, config);
    },

    initComponent : function () {
        LABKEY.DataRegion.ColumnsTab.superclass.initComponent.call(this);
    },

    getList : function () { return this.columnsList; },

    onToolGear : function (index, item, e) {
        var columnRecord = this.columnStore.getAt(index);
        var fieldKey = columnRecord.data.fieldKey;
        Ext.Msg.prompt('Set column caption',
                "Set caption for column '" + fieldKey + "'",
                function (btnId, text) {
                    text = text ? text.trim() : "";
                    if (btnId == "ok" && text.length > 0)
                        columnRecord.set("title", text);
                }, this, false, columnRecord.data.title);
    },

    createDefaultRecordData : function (fieldKey) {
        if (fieldKey)
        {
            var o = {fieldKey: fieldKey};
            var fk = FieldKey.fromString(fieldKey);
            var record = this.fieldMetaStore.getById(fieldKey);
            if (record)
                o.name = record.caption || fk.name;
            else
                o.name = fk.name + " (not found)";
            return o;
        }

        return { };
    },

    setShowHiddenFields : function (showHidden) {
    },

    hasField : function (fieldKey) {
        return this.columnStore.findExact("fieldKey", fieldKey) != -1;
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
    }

});

LABKEY.DataRegion.FilterTab = Ext.extend(LABKEY.DataRegion.Tab, {
    constructor : function (config) {
        //console.log("FilterTab");

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
        }
        this.filterStore = new Ext.data.JsonStore({
            fields: ['fieldKey', 'items', 'urlParameter'],
            root: 'filter',
            data: { filter: filters },
            remoteSort: true
        });

        this.filterStore.on({
            load: this.onStoreLoad,
            add: this.onStoreAdd,
            remove: this.onStoreRemove,
            scope: this
        });

        function getFieldMetaRecord(fieldKey)
        {
            return fieldMetaStore.getById(fieldKey);
        }

        config = Ext.applyIf({
            title: "Filter",
            cls: "test-filter-tab",
            layout: "fit",
            items: [{
                title: "Selected Filters",
                xtype: "panel",
                border: false,
                style: {"border-left-width": "1px"},
                layout: {
                    type: "hbox",
                    align: "stretch"
                },
                items: [{
                    ref: "../filterList",
                    xtype: "compdataview",
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
                            '<table width="100%" cellpadding=0 cellspacing=0 class="labkey-customview-item labkey-customview-filter-item" fieldKey="{fieldKey}">',
                            '  <tr>',
                            '    <td rowspan="{[values.items.length+2]}" class="labkey-grab" width="8px">&nbsp;</td>',
                            '    <td colspan="3"><span class="item-caption">{[this.getFieldCaption(values.fieldKey)]}</span></td>',
                            '  </tr>',
                            '  <tpl for="items">',
                            '  <tr clauseIndex="{[xindex-1]}">',
                            '    <td>',
                            '      <div class="item-op"></div>',
                            '      <div class="item-value"></div>',
                            '    </td>',
                            '    <td width="15px"><div class="labkey-tool {[values.urlParameter ? "labkey-tool-unpin" : "labkey-tool-pin"]}"></div></td>',
                            '    <td width="15px"><div class="labkey-tool labkey-tool-close" title="Remove filter clause"></div></td>',
                            '  </tr>',
                            '  </tpl>',
                            '  <tr>',
                            '    <td colspan="3">',
                            '      <span style="float:right;">',
                            LABKEY.Utils.textLink({text: "Add", onClick: "return false;", add: true}),
                            '      </span>',
                            '    </td>',
                            '  </tr>',
                            '</table>',
                            '</tpl>',
                        {
                            getFieldMetaRecord : getFieldMetaRecord,
                            getFieldCaption : function (fieldKey) {
                                var fieldMeta = this.getFieldMetaRecord(fieldKey);
                                return fieldMeta.data.caption || fieldMeta.data.name;
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
                    }]
                }]
            }]
        }, config);

        LABKEY.DataRegion.FilterTab.superclass.constructor.call(this, config);
    },

    initComponent : function () {
        LABKEY.DataRegion.FilterTab.superclass.initComponent.call(this);
        this.updateTitle();
    },

    onListBeforeClick : function (list, index, item, e) {
        if (LABKEY.DataRegion.FilterTab.superclass.onListBeforeClick.call(this, list, index, item, e) === false)
            return false;

        var target = Ext.fly(e.getTarget());
        if (target.is("a.labkey-text-link") && target.dom.innerHTML == "Add")
        {
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
    getClauseFromNode : function (recordIndex, node) {
        var tr = Ext.fly(node).parent("tr[clauseIndex]");
        if (!tr)
            return;

        var record = this.getRecord(recordIndex);
        var items = record.get("items");
        var clauseIndex = -1;
        if (tr.dom.getAttribute("clauseIndex") !== undefined)
            clauseIndex = +tr.dom.getAttribute("clauseIndex");
        if (clauseIndex < 0 || clauseIndex >= items.length)
            return;

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
        if (!o)
            return;

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
            Ext.each(table.query("tr[clauseIndex]"), function (row, i, all) {
                var clauseIndex = +row.getAttribute("clauseIndex");
                if (clauseIndex == o.clauseIndex)
                    Ext.fly(row).remove();
                else if (clauseIndex > o.clauseIndex)
                    row.setAttribute("clauseIndex", clauseIndex-1);
            }, this);

            // adjust clauseIndex down for all components for the filter
            var cs = this.filterList.getComponents(index);
            Ext.each(cs, function (c, i, all) {
                if (c.clauseIndex == o.clauseIndex)
                    Ext.destroy(c);
                else if (c.clauseIndex > o.clauseIndex)
                    c.clauseIndex--;
            });
        }
        return false;
    },

    onToolPin : function (index, item, e) {
        var o = this.getClauseFromNode(index, e.getTarget());
        if (!o)
            return;

        this.pinClause(o.record, o.clauseIndex, true);
        Ext.fly(e.getTarget()).replaceClass("labkey-tool-pin", "labkey-tool-unpin");
        return false;
    },

    onToolUnpin : function (index, item, e) {
        var o = this.getClauseFromNode(index, e.getTarget());
        if (!o)
            return;

        this.pinClause(o.record, o.clauseIndex, false);
        Ext.fly(e.getTarget()).replaceClass("labkey-tool-unpin", "labkey-tool-pin");
        return false;
    },

    pinClause : function (record, clauseIndex, pin) {
        record.get("items")[clauseIndex].urlParameter = pin;
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
        return {
            fieldKey: fieldKey,
            items: [{
                fieldKey: fieldKey,
                op: "eq",
                value: ""
            }]
        };
    },

    getList : function () { return this.filterList; },

    hasField : function (fieldKey) {
        // filterStore may have more than one filter for a fieldKey
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
            var items = filterRecord.data.items;
            for (var i = 0; i < items.length; i++)
            {
                var filterOp = items[i].op;
                if (filterOp)
                {
                    var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(filterOp);
                    if (!filterType) {
                        alert("filter type '" + filterOp + "' isn't recognized");
                        return false;
                    }

                    var value = filterType.validate(items[i].value, jsonType, fieldKey);
                    if (value == undefined)
                        return false;
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
            var items = filterRecord.get("items");
            for (var j = 0; j < items.length; j++)
            {
                var o = {
                    fieldKey: items[j].fieldKey || filterRecord.data.fieldKey,
                    op: items[j].op,
                    value: items[j].value
                };
                if (items[j].urlParameter)
                    urlData.push(o);
                else
                    saveData.push(o);
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
            fields: ['fieldKey', 'dir', 'urlParameter'],
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

        function getFieldMetaRecord(fieldKey)
        {
            return fieldMetaStore.getById(fieldKey);
        }

        config = Ext.applyIf({
            title: "Sort",
            cls: "test-sort-tab",
            layout: "fit",
            items: [{
                title: "Selected Sort",
                xtype: "panel",
                border: false,
                style: {"border-left-width": "1px"},
                //padding: "4px",
                layout: {
                    type: "hbox",
                    align: "stretch"
                },
                items: [{
                    title: "Selected Sort",
                    ref: "../sortList",
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
                            '<table width="100%" cellpadding=0 cellspacing=0 class="labkey-customview-item labkey-customview-sort-item" fieldKey="{fieldKey}">',
                            '  <tr>',
                            '    <td rowspan="2" class="labkey-grab"></td>',
                            '    <td colspan="3"><span class="item-caption">{[this.getFieldCaption(values.fieldKey)]}</span></td>',
                            '  </tr>',
                            '  <tr>',
                            '    <td><div class="item-dir"></div></td>',
                            '    <td width="15px"><div class="labkey-tool {[values.urlParameter ? "labkey-tool-unpin" : "labkey-tool-pin"]}"></div></td>',
                            '    <td width="15px"><span class="labkey-tool labkey-tool-close" title="Remove sort"></span></td>',
                            '  </tr>',
                            '</table>',
                            '</tpl>',
                        {
                            getFieldMetaRecord : getFieldMetaRecord,
                            getFieldCaption : function (fieldKey) {
                                var fieldMeta = this.getFieldMetaRecord(fieldKey);
                                return fieldMeta.data.caption || fieldMeta.data.name;
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
                            }
                        }
                    }]
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

    createDefaultRecordData : function (fieldKey) {
        return {
            fieldKey: fieldKey,
            dir: "+"
        };
    },

    setShowHiddenFields : function (showHidden) {
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
        //console.log("PropertiesTab");

        this.designer = config.designer;
        this.customView = config.customView;
        this.readOnly = config.readOnly;

        var disableSharedAndInherit = this.customView.hidden || this.customView.session || !this.designer.query.canEditSharedViews;

        config = Ext.applyIf({
            title: "Properties",
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
        for (var i = 0; i < this.grouptab.items.length; i++)
        {
            var field = this.grouptab.items.get(i);
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

    save : function (edited, urlParameters) {
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

        Ext.applyIf(edited, o);
    }

});

Ext.namespace('LABKEY', 'LABKEY.ext');

/** An Ext.data.Record constructor for LABKEY.Query.FieldMetaData json objects. */
LABKEY.ext.FieldMetaRecord = Ext.data.Record.create([
    'name',
    {name: 'fieldKey', mapping: 'fieldKeyPath' },
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
    body += "<tr><td valign='top'><strong>Field&nbsp;key:</strong></td><td>" + FieldKey.fromString(field.fieldKey).toDisplayString() + "</td></tr>";
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
 * An Ext.data.Store for LABKEY.ext.FieldMetaRecord json objects.
 */
LABKEY.ext.FieldMetaStore = Ext.extend(Ext.data.Store, {
    constructor : function (config) {
        //console.log("FieldMetaStore");

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
            idProperty: "fieldKeyPath",
            root: 'columns',
            fields: LABKEY.ext.FieldMetaRecord
        });

        LABKEY.ext.FieldMetaStore.superclass.constructor.call(this, config);
        this.on('beforeload', this.onBeforeLoad, this);
        this.on('load', this.onLoad, this);
        this.on('loadexception', this.onLoadException, this);
    },

    onBeforeLoad : function() {
        this.isLoading = true;
    },

    onLoad : function(store, records, options) {
        this.isLoading = false;
    },

    onLoadException : function(proxy, options, response, error)
    {
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
     * Loads records for the given lookup fieldKey.  The fieldKey is the full path relative to the base query.
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
        //console.log("loadLookup");
        //console.log(options);

        // The record's name is the fieldKey relative to the root query table.
        var fieldKey = options.fieldKey || (options.record && options.record.data.fieldKey);
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

            this.      load(o);
        }
    },

    queryLookup : function (fieldKey)
    {
        // XXX: check we handle fieldKeys with '/' in them
        var prefixMatch = fieldKey == "<root>" ? "" : (fieldKey + "/");
        var collection = this.queryBy(function (r, id) {
            var recordFieldKey = id;//r.data.fieldKey;
            var idx = recordFieldKey.indexOf(prefixMatch);
            if (idx == 0 && recordFieldKey.substring(prefixMatch.length).indexOf("/") == -1)
                return true;
            return false;
        });
        return collection.getRange();
    }

});


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
            var fieldMetaRecord = this.fieldMetaStore.getById(this.record.data.fieldKey);
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

