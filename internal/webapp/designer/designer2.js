/*
 * Copyright (c) 2010-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.ns('LABKEY.ext', 'LABKEY.DataRegion');

LABKEY.ext.SplitGroupTabPanel = Ext.extend(Ext.ux.GroupTabPanel, {
    constructor : function (config) {
        this.cls = 'vertical-tabs extContainer customizeViewPanel';
        this.splitItem = this.lookupComponent(config.splitItem);
        delete config.splitItem;
        LABKEY.ext.SplitGroupTabPanel.superclass.constructor.call(this, config);

        this.layout.layoutOnCardChange = true;
    },

    afterRender : function () {
        var splitItem = this.splitItem;
        splitItem.render(this.bwrap, 0);
        // Add 6px for the resizer on the right
        splitItem.getEl().applyStyles({float: "left", "padding-right": "6px"});
        this.splitResizer = new Ext.Resizable(splitItem.getEl(), {
            handles: 'e',
            pinned: true, // always show the splitter
            constrainTo: this.bwrap,
            minWidth: 100,
            maxWidth: 700,
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
        // For tooltips on the fieldsTree TreePanel
        Ext.QuickTips.init();
        
        this.dataRegion = config.dataRegion;

        this.containerPath = config.containerPath;
        this.schemaName = config.schemaName;
        this.queryName = config.queryName;
        this.viewName = config.viewName || "";
        this.query = config.query;

        // Find the custom view in the LABKEY.Query.getQueryDetails() response.
        this.customView = null;
        for (var i = 0; i < this.query.views.length; i++)
        {
            if (this.query.views[i].name == this.viewName)
            {
                this.customView = this.query.views[i];
                break;
            }
        }

        if (!this.customView)
        {
            this.customView = {
                name: this.viewName,
                inherit: false,
                shared: false,
                session: false,
                hidden: false,
                editable: true,
                fields: [],
                columns: [],
                sort: [],
                filter: [],
                doesNotExist: true
            };
        }

        // Create the FieldKey metadata store
        this.fieldMetaStore = new LABKEY.ext.FieldMetaStore({
            containerPath: this.containerPath,
            schemaName: this.schemaName,
            queryName: this.queryName,
            data: this.query
        });
        this.fieldMetaStore.loadData(this.query);


        {
            // Add any additional field metadata for view's selected columns, sorts, filters.
            // The view may be filtered or sorted upon columns not present in the query's selected column metadata.
            // The FieldMetaStore uses a reader that expects the field metadata to be under a 'columns' property instead of 'fields'
            this.fieldMetaStore.loadData({columns: this.customView.fields}, true);

            // Add user filters
            this.userFilter = config.userFilter || [];
            for (var i = 0; i < this.userFilter.length; i++)
            {
                // copy the filter so the original userFilter isn't modified by the designer
                var userFilter = Ext.apply({urlParameter: true}, this.userFilter[i]);
                this.customView.filter.unshift(userFilter);
            }

            // Add user sort
            var newSortArray = [];
            this.userSort = config.userSort || [];
            for (var i = 0; i < this.userSort.length; i++)
            {
                // copy the sort so the original userSort isn't modified by the designer
                var userSort = Ext.apply({urlParameter: true}, this.userSort[i]);
                newSortArray.push(userSort);
            }

            // Merge userSort and existing customView sort.
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

            this.userColumns = config.userColumns;
            if (this.userColumns)
            {
                this.customView.columns = [];
                if (this.userColumns == '*')
                {
                    // Pull in all columns from the target query - issue 17425
                    for (var i = 0; i < this.query.columns.length; i++)
                    {
                        this.customView.columns.push({
                            fieldKey : this.query.columns[i].name,
                            key : this.query.columns[i].name
                        });
                    }
                }
                else
                {
                    var columnNames = this.userColumns.split(",");
                    for (var i = 0; i < columnNames.length; i++)
                    {
                        this.customView.columns.push({
                            fieldKey : columnNames[i],
                            key : columnNames[i]
                        });
                    }
                }
            }

            // Add user containerFilter
            this.userContainerFilter = config.userContainerFilter;
            if (this.userContainerFilter && this.customView.containerFilter != this.userContainerFilter)
                this.customView.containerFilter = this.userContainerFilter;
        }

        this.showHiddenFields = config.showHiddenFields || false;
        this.allowableContainerFilters = config.allowableContainerFilters || [];

        this.columnsTab = new LABKEY.DataRegion.ColumnsTab({
            name: "ColumnsTab",
            designer: this,
            schemaName: this.schemaName,
            queryName: this.queryName,
            viewName: this.viewName,
            fieldMetaStore: this.fieldMetaStore,
            customView: this.customView
        });

        this.filterTab = new LABKEY.DataRegion.FilterTab({
            name: "FilterTab",
            designer: this,
            fieldMetaStore: this.fieldMetaStore,
            customView: this.customView
        });

        this.sortTab = new LABKEY.DataRegion.SortTab({
            name: "SortTab",
            designer: this,
            fieldMetaStore: this.fieldMetaStore,
            customView: this.customView
        });

        this.fieldsTree = new Ext.tree.TreePanel({
            autoScroll: true,
            border: false,
            //width: 230,
            cls: "labkey-fieldmeta-tree",
            root: new Ext.tree.AsyncTreeNode({
                id: "<ROOT>",
                expanded: true,
                expandable: false,
                draggable: false
            }),
            rootVisible: false,
            loader: new LABKEY.ext.FieldTreeLoader({
                store: this.fieldMetaStore,
                designer: this,
                containerPath: this.containerPath,
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

        var canEdit = this.canEdit();

        // enabled for named editable views that exist.
        var deleteEnabled = canEdit && this.customView.name && !this.customView.doesNotExist;

        // enabled for saved (non-session) editable views or customized default view (not new) views.
        var revertEnabled = canEdit && (this.customView.session || (!this.customView.name && !this.customView.doesNotExist));

        // Issue 11188: Don't use friendly id for grouptabs (eg., "ColumnsTab") -- breaks showing two customize views on the same page.
        // Provide mapping from friendly tab names to tab index.
        this.groupNames = {
            ColumnsTab: 0,
            FilterTab: 1,
            SortTab: 2
        };

        config.activeGroup = this.translateGroupName(config.activeGroup);

        var footerBar = [{
                text: "Delete",
                tooltip: "Delete " + (this.customView.shared ? "shared" : "your") + " saved view",
                tooltipType: "title",
                disabled: !deleteEnabled,
                handler: this.onDeleteClick,
                scope: this
            }];

        // Only add Revert if we're being rendered attached to a grid
        if (config.dataRegion)
        {
            footerBar[footerBar.length] = {
                text: "Revert",
                tooltip: "Revert " + (this.customView.shared ? "shared" : "your") + " edited view",
                tooltipType: "title",
                // disabled for hidden, saved (non-session), customized (not new) default view, or uneditable views
                disabled: !revertEnabled,
                handler: this.onRevertClick,
                scope: this
            };
        }

        footerBar[footerBar.length] = "->";

        // Only add View Grid if we're being rendered attached to a grid
        if (config.dataRegion)
        {
            footerBar[footerBar.length] = {
                text: "View Grid",
                tooltip: "Apply changes to the view and reshow grid",
                tooltipType: "title",
                handler: this.onApplyClick,
                scope: this
            };
        }

        if (!this.query.isTemporary)
        {
            footerBar[footerBar.length] = {
                text: "Save",
                tooltip: "Save changes",
                tooltipType: "title",
                handler: this.onSaveClick,
                scope: this
            };
        }

        config = Ext.applyIf(config, {
            tabWidth: 80,
            activeGroup: 0,
            activeTab: 0,
            frame: false,
            shadow: true,
            height: 280,
            splitItem: {
                title: "Available Fields",
                xtype: 'panel',
                layout: 'fit',
                autoScroll: false,
                border: false,
                style: { "border-left-width": "1px", "border-top-width": "1px", "border-bottom-width": "1px" },
                items: [ this.fieldsTree ]
            },
            defaults : {
                xtype             : 'grouptab',
                layoutOnTabChange : true
            },
            items: [{
                items: [ this.columnsTab ]
            },{
                items: [ this.filterTab ]
            },{
                items: [ this.sortTab ]
            }],
            buttonAlign: "left",
            bbar: {
                xtype: 'container',
                // would like to use 'labkey-status-info' class instead of inline style, but it centers and stuff
                //cls: "labkey-status-info",
                style: { 'background-color': "#FFDF8C", padding: "2px" },
                html: "<span class='labkey-tool labkey-tool-close' style='float:right;vertical-align:top;'></span><span>message</span>",
                hidden: true
            },
            fbar: footerBar
        });

        this.addEvents({
            beforeviewsave: true,
            viewsave: true
        });

        LABKEY.DataRegion.ViewDesigner.superclass.constructor.call(this, config);

        this.fieldsTree.on('checkchange', this.onCheckChange, this);
        this.on('tabchange', this.onTabChange, this);
        this.on('groupchange', this.onGroupChange, this);

        // Show 'does not exist' message only for non-default views.
        if (this.customView.doesNotExist && this.viewName)
            this.showMessage("Custom View '" + Ext.util.Format.htmlEncode(this.viewName) + "' not found.");
    },

    onRender : function (ct, position) {
        LABKEY.DataRegion.ViewDesigner.superclass.onRender.call(this, ct, position);
        if (!this.canEdit())
        {
            var msg = "This view is not editable, but you may save a new view with a different name.";
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

    // group may be true, group index, group name, or the group tab instance.
    translateGroupName : function (group) {
        // translate group tab name into index.
        if (group === null || group === undefined || Ext.isBoolean(group))
            return 0;
        if (Ext.isNumber(group))
            return group;
        if (Ext.isString(group))
            return this.groupNames[group];
        return group;
    },

    // Issue 11188: Translate friendly group tab name into item index.
    setActiveGroup : function (group) {
        group = this.translateGroupName(group);
        return LABKEY.DataRegion.ViewDesigner.superclass.setActiveGroup.call(this, group);
    },

    canEdit : function ()
    {
        return this.getEditableErrors().length == 0;
    },

    getEditableErrors : function ()
    {
        if (!this.editableErrors)
        {
            this.editableErrors = LABKEY.DataRegion._getCustomViewEditableErrors(this.customView);
        }
        return this.editableErrors;
    },

    showMessage : function (msg)
    {
        // XXX: support multiple messages and [X] close box
        // UNDONE: bottom bar isn't rendering in the GroupTabPanel
        var tb = this.getBottomToolbar();
        if (tb && tb.getEl())
        {
            var el = tb.getEl().last();
            el.update(msg);
            tb.setVisible(true);
            tb.getEl().slideIn();
            tb.getEl().on('click', function () { this.hideMessage(); }, this, {single: true});
        }
        else
        {
            this.on('afterrender', function () { this.showMessage(msg); }, this, {single: true});
        }
    },

    hideMessage : function ()
    {
        var tb = this.getBottomToolbar();
        tb.getEl().last().update('');
        tb.setVisible(false);
        tb.getEl().slideOut();
    },

    getDesignerTabs : function ()
    {
        return [this.columnsTab, this.filterTab, this.sortTab];
    },

    getActiveGroup : function ()
    {
        var group = (typeof this.activeGroup == 'object') ? this.activeGroup : this.items.get(this.activeGroup);
        return group;
    },

    getActiveDesignerTab : function ()
    {
        if (this.activeGroup !== undefined)
        {
            var group = this.getActiveGroup();
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
            // NOTE: Don't use the fieldKey as id since it will be rendered into the dom without being html escaped.
            // NOTE: Escaping the value here breaks the TreePanel.nodeHash collection.
            // Instead we use the LABKEY.ext.FieldTreeNodeUI to add an htmlEscaped fieldKey attribute.
            //id: fieldMeta.fieldKey,
            fieldKey: fieldMeta.fieldKey,
            text: text,
            leaf: !fieldMeta.lookup,
            //checked: fieldMeta.selectable ? this.hasField(fieldMeta.fieldKey) : undefined,
            checked: this.hasField(fieldMeta.fieldKey),
            disabled: !fieldMeta.selectable,
            hidden: fieldMeta.hidden && !this.showHiddenFields,
            qtip: LABKEY.ext.FieldMetaRecord.getToolTipHtml(fieldMetaRecord),
            iconCls: "x-hide-display",
            uiProvider: LABKEY.ext.FieldTreeNodeUI
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
            this.addRecord(node.attributes.fieldKey);
        else
            this.removeRecord(node.attributes.fieldKey);
    },

    onGroupChange : function () {
        this.onTabChange();
    },

    onTabChange : function () {

        var tab = this.getActiveDesignerTab();
        if (tab instanceof LABKEY.DataRegion.Tab)
        {
            // get the checked fields from the new tab's store
            var storeRecords = tab.getList().getStore().getRange();
            var checkedFieldKeys = { };
            for (var i = 0; i < storeRecords.length; i++)
                checkedFieldKeys[storeRecords[i].get("fieldKey").toUpperCase()] = true;

            // suspend check events so checked items aren't re-added to the tab's store
            this.fieldsTree.suspendEvents();
            this.fieldsTree.root.cascade(function () {
                var fieldKey = this.attributes.fieldKey;
                if (fieldKey)
                    this.getUI().toggleCheck(fieldKey.toUpperCase() in checkedFieldKeys);
            });
            this.fieldsTree.resumeEvents();
        }
    },

    onDeleteClick : function (btn, e) {
        if (this.dataRegion)
            this.dataRegion.deleteCustomView();
        else
            this._deleteCustomView(true);
    },

    onRevertClick : function (btn, e) {
        if (this.dataRegion)
            this.dataRegion.revertCustomView();
        else
            this._deleteCustomView(false);
    },

    // If designer isn't attached to a DataRegion, delete the view and reload the page.  Only call when no grid is present.
    _deleteCustomView : function (complete)
    {
        Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("query", "deleteView", this.containerPath),
            jsonData: {schemaName: this.schemaName, queryName: this.queryName, viewName: this.viewName, complete: complete},
            method: "POST",
            scope: this,
            success: function() { window.location.reload() }
        });
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
        this.save(props);
    },

    onSaveClick : function (btn, e) {
        var config = Ext.applyIf({
            canEditSharedViews: this.query.canEditSharedViews,
            allowableContainerFilters: this.allowableContainerFilters,
            targetContainers: this.query.targetContainers,
            canEdit: this.getEditableErrors().length == 0,
            success: function (win, o) {
                this.save(o, function () {
                    win.close();
                    this.setVisible(false);
                }, this);
            },
            scope: this
        }, this.customView);

        LABKEY.DataRegion.saveCustomizeViewPrompt(config);
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
            containerPath: this.containerPath,
            schemaName: this.schemaName,
            queryName: this.queryName,
            views: [ edited ],
            success: function (savedViewsInfo) {
                if (callback)
                    callback.call(scope || this, savedViewsInfo, urlParameters);
                this.fireEvent("viewsave", this, savedViewsInfo, urlParameters);
            },
            failure: function (errorInfo) {
                Ext.Msg.alert("Error saving view", errorInfo.exception);
            },
            scope: this
        });
    },

    close : function ()
    {
        if (this.dataRegion)
        {
            this.dataRegion.hideCustomizeView(true);
        }
        else
        {
            // If we're not attached to a grid, just remove from the DOM
            this.getEl().remove();
        }
    }

});
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
            if (fieldMetaRecord)
                var html = LABKEY.ext.FieldMetaRecord.getToolTipHtml(fieldMetaRecord);
            else
                var html = "<strong>Field not found:</strong> " + Ext.util.Format.htmlEncode(fieldKey);
            qt.body.update(html);
        }
        else
        {
            qt.body.update("<strong>No field found</strong>");
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
        return record;
    },

    getRecordIndex : function (fieldKeyOrIndex) {
        var list = this.getList();
        var index = -1;
        if (Ext.isNumber(fieldKeyOrIndex))
            index = fieldKeyOrIndex;
        else
            index = list.store.find("fieldKey", fieldKeyOrIndex, 0, false, false);
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
            var upperFieldKey = record.data.fieldKey.toUpperCase();
            var treeNode = this.designer.fieldsTree.getRootNode().findChildBy(function (node) { return node.attributes.fieldKey.toUpperCase() == upperFieldKey; }, null, true);
            if (treeNode)
                treeNode.getUI().toggleCheck(false);
        }
    }

});

LABKEY.DataRegion.ColumnsTab = Ext.extend(LABKEY.DataRegion.Tab, {
    constructor : function (config) {
        //console.log("ColumnsTab");

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
                if (!agg.fieldKey && !agg.type)
                    continue;
                var columnRecord = this.columnStore.getById(agg.fieldKey.toUpperCase());
                if (!columnRecord)
                    continue;

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
                                if (values.title)
                                    return Ext.util.Format.htmlEncode(values.title);

                                var fieldKey = values.fieldKey;
                                var fieldMeta = fieldMetaStore.getById(fieldKey.toUpperCase());
                                if (fieldMeta)
                                {
                                    // caption is already htmlEncoded
                                    if (fieldMeta.data.caption && fieldMeta.data.caption != "&nbsp;")
                                        return fieldMeta.data.caption;
                                    return Ext.util.Format.htmlEncode(fieldMeta.data.name);
                                }
                                return Ext.util.Format.htmlEncode(values.name) + " <span class='labkey-error'>(not found)</span>";
                            },

                            getAggegateCaption : function (values) {
                                var fieldKey = values.fieldKey;
                                var fieldMeta = fieldMetaStore.getById(fieldKey.toUpperCase());
                                var labels = [];
                                aggregateStore.each(function(rec){
                                    if(rec.get('fieldKey') == fieldKey){
                                        labels.push(rec.get('type'));
                                    }
                                }, this);
                                labels = Ext.unique(labels);

                                if(labels.length){
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
                        if(columnIndex==grid.getColumnModel().getIndexById('deleter')) {
                            var record = grid.getStore().getAt(rowIndex);
                            grid.getStore().remove(record);
                            //grid.getView().refresh();
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
                //autoCreate: true,
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
                //height: 200,
                autoHeight: true,
                minHeight: 100,
                //plain: true,
                footer: true,
                closable: true,
                closeAction: 'hide',
                //layout: 'fit',
                items: {
                    xtype: 'form',
                    border: false,
                    labelAlign: 'top',
                    bodyStyle: 'padding: 5px;',
                    defaults: {
                        width: 330
                    },
                    items: [{
                        xtype: "textfield",
                        fieldLabel: "Title",
                        name: "title",
                        ref: "../titleField",
                        allowBlank: true
                        //width: 'auto'
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
                        aggregateStoreCopy.each(function(rec){
                            if(!rec.get('type') && !rec.get('label')){
                                aggregateStoreCopy.remove(rec);
                            }
                            else if (!rec.get('type')){
                                error = true;
                                alert('Aggregate is missing a type');
                                return false;
                            }
                        }, this);

                        if(error)
                            return;

                        //remove existing records matching this field
                        aggregateStore.each(function(rec){
                            if(rec.get('fieldKey') == fieldKey)
                                aggregateStore.remove(rec);
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
                    if(rec.get('fieldKey') == this.columnRecord.get('fieldKey')){
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

        for (var i = 0; i < edited.columns.length; i++)
        {
            delete edited.columns[i].aggregate;
        }
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
                                    if (fieldMeta.data.caption && fieldMeta.data.caption != "&nbsp;")
                                        return fieldMeta.data.caption;
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
                                if (this.getWidth() == 0) {
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

    onFolderFilterChange : function (combo, newValue, oldValue) {
        if (newValue)
            this.containerFilterPaperclip.enable();
        else
            this.containerFilterPaperclip.disable();
    },

    onListBeforeClick : function (list, index, item, e) {
        if (LABKEY.DataRegion.FilterTab.superclass.onListBeforeClick.call(this, list, index, item, e) === false)
            return false;

        var target = Ext.fly(e.getTarget());
        if (target.is("a.labkey-text-link[add='true']"))
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

    getList : function () { return this.filterList; },

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
            if (!fieldKey)
                continue;

            var fieldMetaRecord = this.fieldMetaStore.getById(fieldKey.toUpperCase());
            if (!fieldMetaRecord)
                continue;

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
                } else {
                    value = items[j].value;
                }
                if (filterType.isDataValueRequired())
                    o.value = value;
                if (items[j].urlParameter)
                    urlData.push(o);
                else
                    saveData.push(o);
            }
        }

        var containerFilter = this.containerFilterCombo.getValue();
        if (containerFilter)
        {
            if (this.containerFilterPaperclip.pressed)
                edited.containerFilter = containerFilter;
            else
                urlParameters.containerFilter = containerFilter;
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
                //padding: "4px",
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
                                    if (fieldMeta.data.caption && fieldMeta.data.caption != "&nbsp;")
                                        return fieldMeta.data.caption;
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
                                if (this.getWidth() == 0) {
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

    getList : function () { return this.sortList; },

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
        // UNDONE: if view name is different, we should check that the target view is editable
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

    getToolTipText : function () {
        if (this.pressed)
            return "This " + this.itemType + " will be saved with the view";
        else
            return "This " + this.itemType + " will NOT be saved as part of the view";
    },

    updateToolTip : function () {
        var el = this.btnEl;
        var msg = this.getToolTipText();
        el.set({title: msg});
    }
});
Ext.reg('paperclip-button', LABKEY.DataRegion.PaperclipButton);

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
    'lookup',
    'crosstabColumnDimension',
    'crosstabColumnMember'
]);
LABKEY.ext.FieldMetaRecord.getToolTipHtml = function (fieldMetaRecord) {
    var field = fieldMetaRecord.data;
    var body = "<table>";
    if (field.description)
    {
        body += "<tr><td valign='top'><strong>Description:</strong></td><td>" + Ext.util.Format.htmlEncode(field.description) + "</td></tr>";
    }
    body += "<tr><td valign='top'><strong>Field&nbsp;key:</strong></td><td>" + Ext.util.Format.htmlEncode(LABKEY.FieldKey.fromString(field.fieldKey).toDisplayString()) + "</td></tr>";
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
            idProperty: function (json) { return json.fieldKeyPath.toUpperCase(); },
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

        LABKEY.Utils.alert('Error loading query information', loadError.message);

        this.loadError = loadError;
    },

    /**
     * Loads records for the given lookup fieldKey.  The fieldKey is the full path relative to the base query.
     * The special fieldKey '<ROOT>' returns the records in the base query.
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

        var upperFieldKey = fieldKey.toUpperCase();
        if (upperFieldKey == "<ROOT>" || this.lookupLoaded[upperFieldKey])
        {
            var r = this.queryLookup(upperFieldKey);
            if (options.callback)
                options.callback.call(options.scope || this, r, options, true);
        }
        else
        {
            var o = Ext.applyIf({
                params: { fk: fieldKey },
                callback: options.callback.createSequence(function () { this.lookupLoaded[upperFieldKey] = true; }, this),
                add: true
            }, options);

            this.load(o);
        }
    },

    queryLookup : function (fieldKey)
    {
        var prefixMatch = fieldKey == "<ROOT>" ? "" : (fieldKey + "/");
        var collection = this.queryBy(function (record, id) {
            var recordFieldKey = record.get("fieldKey");
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


// Adds a 'fieldKey' attribute to the available fields tree used by the test framework
LABKEY.ext.FieldTreeNodeUI = Ext.extend(Ext.tree.TreeNodeUI, {
    renderElements : function () {
        LABKEY.ext.FieldTreeNodeUI.superclass.renderElements.apply(this, arguments);
        var node = this.node;
        var fieldKey = node.attributes.fieldKey;
        this.elNode.setAttribute("fieldKey", fieldKey);
    }
});

// This TreeLoader returns TreeNodes for field metadata and is backed by a FieldMetaStore.
LABKEY.ext.FieldTreeLoader = Ext.extend(Ext.tree.TreeLoader, {
    constructor : function (config) {
        if (!config.createNodeConfigFn)
            throw new Error("need a FieldMetaRecord->TreeNode fn");
        this.createNodeConfigFn = config.createNodeConfigFn;

        this.store = config.store || new LABKEY.ext.FieldMetaStore({
            containerPath: config.containerPath,
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
                fieldKey: node.attributes.fieldKey || "<ROOT>",
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

    createCrosstabMemberNode : function (crosstabColumnMember) {
        var attr = {
            value: crosstabColumnMember.value,
            text: crosstabColumnMember.caption,
            dimensionFieldKey: crosstabColumnMember.dimensionFieldKey,
            leaf: false,
            disabled: true,
            expanded: true,
            crosstabMember: true,
            iconCls: "x-hide-display"
        };
        var node = LABKEY.ext.FieldTreeLoader.superclass.createNode.call(this, attr);
        // Mark the crosstab member value node as loaded so the TreeLoader won't try to ajax request children
        node.loaded = true;
        return node;
    },

    processResponse: function(response, node, callback, scope) {
        var fieldMetaRecords = response.records;
        try {
            node.beginUpdate();
            // UNDONE: Don't bother trying to group by column members if query is not a crosstab table
            var groupedByMember = this.columnsByMember(fieldMetaRecords);
            var rowDimCols = groupedByMember[null];
            this.processMemberColumns(rowDimCols);

            for (var groupedByMemberKey in groupedByMember) {
                if (groupedByMemberKey === null)
                    continue;

                this.processMemberColumns(node, groupedByMember[groupedByMemberKey]);
            }

            node.endUpdate();
            this.runCallback(callback, scope || node, [node]);
        } catch(e) {
            console.log("Error in FieldTreeLoader.processResponse: " + e);
            throw e;
            this.handleFailure(response);
        }
    },

    processMemberColumns: function (node, columns) {
        if (!columns || columns.length == 0)
            return;

        var crosstabColumnMember = columns[0].get('crosstabColumnMember');
        if (crosstabColumnMember && columns.length > 1) {
            var n = this.createCrosstabMemberNode(crosstabColumnMember);
            node.appendChild(n);
            node = n;
        }

        for (var i = 0, len = columns.length; i < len; i++) {
            var n = this.createNode(columns[i]);
            if(n) {
                node.appendChild(n);
            }
        }
    },

    // Group the columns by member
    columnsByMember: function (fieldMetaRecords) {
        var groupedByMember = {};

        for (var i = 0; i < fieldMetaRecords.length; i++) {
            var fieldMetaRecord = fieldMetaRecords[i];
            var groupedByMemberKey = null;
            var crosstabColumnMember = fieldMetaRecord.get('crosstabColumnMember');
            if (crosstabColumnMember) {
                groupedByMemberKey = crosstabColumnMember.dimensionFieldKey + "~" + crosstabColumnMember.value + "~" + crosstabColumnMember.caption;
            }

            var groupedByMemberColumns = groupedByMember[groupedByMemberKey];
            if (groupedByMemberColumns === undefined)
                groupedByMember[groupedByMemberKey] = groupedByMemberColumns = [];
            groupedByMemberColumns.push(fieldMetaRecord);
        }

        return groupedByMember;
    }
});

// private
LABKEY.DataRegion.saveCustomizeViewPrompt = function(config) {
    var success = config.success;
    var scope = config.scope;

    var viewName = config.name;
    var hidden = config.hidden;
    var session = config.session;
    var inherit = config.inherit;
    var shared = config.shared;
    var containerPath = config.containerPath;
    // User can save this view if it is editable and the shadowed view is editable if present.
    var shadowedViewEditable = config.session && (!config.shadowed || config.shadowed.editable);
    var canEdit = config.canEdit && (!config.session || shadowedViewEditable);
    var canEditSharedViews = config.canEditSharedViews;

    var targetContainers = config.targetContainers;
    var allowableContainerFilters = config.allowableContainerFilters;
    var containerFilterable = (allowableContainerFilters && allowableContainerFilters.length > 1);

    var containerData = [];
    if (targetContainers)
    {
        for (var i = 0; i < targetContainers.length; i++)
        {
            var targetContainer = targetContainers[i];
            containerData[i] = [targetContainers[i].path];
        }
    }
    else
    {
        // Assume view should be saved to current container
        containerData[0] = LABKEY.ActionURL.getContainer();
    }

    var containerStore = new Ext.data.ArrayStore({
        fields: [ 'path' ],
        data: containerData
    });

    var disableSharedAndInherit = LABKEY.user.isGuest || hidden;
    var newViewName = viewName || "New View";
    if (!canEdit && viewName)
        newViewName = viewName + " Copy";

    var warnedAboutMoving = false;

    var win = new Ext.Window({
        title: "Save Custom View" + (viewName ? ": " + Ext.util.Format.htmlEncode(viewName) : ""),
        cls: "extContainer",
        bodyStyle: "padding: 6px",
        modal: true,
        width: 490,
        height: 260,
        layout: "form",
        defaults: {
            tooltipType: "title"
        },
        items: [
            {
                ref: "defaultNameField",
                xtype: "radio",
                fieldLabel: "View Name",
                boxLabel: "Default view for this page",
                inputValue: "default",
                name: "saveCustomView_namedView",
                checked: canEdit && !viewName,
                disabled: hidden || !canEdit
            },
            {
                xtype: "compositefield",
                ref: "nameCompositeField",
                // Let the saveCustomView_name field display the error message otherwise it will render as "saveCustomView_name: error message"
                combineErrors: false,
                items: [
                    {
                        xtype: "radio",
                        fieldLabel: "",
                        boxLabel: "Named",
                        inputValue: "named",
                        name: "saveCustomView_namedView",
                        checked: !canEdit || viewName,
                        handler: function (radio, value)
                        {
                            // nameCompositeField.items will be populated after initComponent
                            if (win.nameCompositeField.items.get)
                            {
                                var nameField = win.nameCompositeField.items.get(1);
                                if (value)
                                    nameField.enable();
                                else
                                    nameField.disable();
                            }
                        },
                        scope: this
                    },
                    {
                        fieldLabel: "",
                        xtype: "textfield",
                        name: "saveCustomView_name",
                        tooltip: "Name of the custom view",
                        tooltipType: "title",
                        msgTarget: "side",
                        allowBlank: false,
                        emptyText: "Name is required",
                        maxLength: 50,
                        width: 280,
                        autoCreate: {tag: 'input', type: 'text', size: '50'},
                        validator: function (value)
                        {
                            if ("default" === value.trim())
                                return "The view name 'default' is not allowed";
                            return true;
                        },
                        selectOnFocus: true,
                        value: newViewName,
                        disabled: hidden || (canEdit && !viewName)
                    }
                ]
            },
            {
                xtype: "box",
                style: "padding-left: 122px; padding-bottom: 8px",
                html: "<em>The " + (!config.canEdit ? "current" : "shadowed") + " view is not editable.<br>Please enter an alternate view name.</em>",
                hidden: canEdit
            },
            {
                xtype: "spacer",
                height: "8"
            },
            {
                ref: "sharedField",
                xtype: "checkbox",
                name: "saveCustomView_shared",
                fieldLabel: "Shared",
                boxLabel: "Make this grid view available to all users",
                checked: shared,
                disabled: disableSharedAndInherit || !canEditSharedViews
            },
            {
                ref: "inheritField",
                xtype: "checkbox",
                name: "saveCustomView_inherit",
                fieldLabel: "Inherit",
                boxLabel: "Make this grid view available in child folders",
                checked: containerFilterable && inherit,
                disabled: disableSharedAndInherit || !containerFilterable,
                hidden: !containerFilterable,
                listeners: {
                    check: function (checkbox, checked)
                    {
                        Ext.ComponentMgr.get("saveCustomView_targetContainer").setDisabled(!checked);
                    }
                }
            },
            {
                ref: "targetContainer",
                xtype: "combo",
                name: "saveCustomView_targetContainer",
                id: "saveCustomView_targetContainer",
                fieldLabel: "Save in Folder",
                store: containerStore,
                value: config.containerPath,
                displayField: 'path',
                valueField: 'path',
                width: 300,
                triggerAction: 'all',
                mode: 'local',
                editable: false,
                hidden: !containerFilterable,
                disabled: disableSharedAndInherit || !containerFilterable || !inherit,
                listeners: {
                    select: function (combobox)
                    {
                        if (!warnedAboutMoving && combobox.getValue() != config.containerPath)
                        {
                            warnedAboutMoving = true;
                            Ext.Msg.alert("Moving a Saved View", "If you save, this view will be moved from '" + config.containerPath + "' to " + combobox.getValue());
                        }
                    }
                }
            }
        ],
        buttons: [
            {
                text: "Save",
                handler: function ()
                {
                    if (!win.nameCompositeField.isValid())
                    {
                        Ext.Msg.alert("Invalid view name", "The view name must be less than 50 characters long and not 'default'.");
                        return;
                    }

                    var nameField = win.nameCompositeField.items.get(1);
                    if (!canEdit && viewName == nameField.getValue())
                    {
                        Ext.Msg.alert("Error saving", "This view is not editable.  You must save this view with an alternate name.");
                        return;
                    }

                    var o = {};
                    if (hidden)
                    {
                        o = {
                            name: viewName,
                            shared: shared,
                            hidden: true,
                            session: session // set session=false for hidden views?
                        };
                    }
                    else
                    {
                        o.name = "";
                        if (!win.defaultNameField.getValue())
                            o.name = nameField.getValue();
                        o.session = false;
                        if (!o.session && canEditSharedViews)
                        {
                            o.shared = win.sharedField.getValue();
                            // Issue 13594: disallow setting inherit bit if query view has no available container filters
                            o.inherit = containerFilterable && win.inheritField.getValue();
                        }
                    }

                    if (o.inherit)
                    {
                        o.containerPath = win.targetContainer.getValue();
                    }

                    // Callback is responsible for closing the save dialog window on success.
                    success.call(scope, win, o);
                },
                scope: this
            },
            {
                text: "Cancel",
                handler: function ()
                {
                    win.close();
                }
            }
        ]
    });
    win.show();
};
