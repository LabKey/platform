
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
            minWidth: 220,
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
        if (this.splitItem) {
            this.splitItem.destroy();
        }
        if (this.splitResizer) {
            this.splitResizer.destroy();
        }
    }
});
Ext.reg('splitgrouptab', LABKEY.ext.SplitGroupTabPanel);

LABKEY.DataRegion.ViewDesigner = Ext.extend(LABKEY.ext.SplitGroupTabPanel, {

    constructor : function (config)
    {
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

        if (!this.customView) {
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
                if (!found) {
                    newSortArray.push(sort);
                }
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
                            fieldKey: this.query.columns[i].name,
                            key: this.query.columns[i].name
                        });
                    }
                }
                else
                {
                    var columnNames = this.userColumns.split(",");
                    for (var i = 0; i < columnNames.length; i++)
                    {
                        this.customView.columns.push({
                            fieldKey: columnNames[i],
                            key: columnNames[i]
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
                createNodeConfigFn: {fn: this.createNodeAttrs, scope: this}
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
                border: true,
                items: [this.fieldsTree]
            },
            defaults: {
                xtype: 'grouptab',
                layoutOnTabChange: true
            },
            items: [{
                items: [this.columnsTab]
            }, {
                items: [this.filterTab]
            }, {
                items: [this.sortTab]
            }],
            buttonAlign: "left",
            bbar: {
                xtype: 'container',
                // would like to use 'labkey-status-info' class instead of inline style, but it centers and stuff
                //cls: "labkey-status-info",
                style: {'background-color': "#FFDF8C", padding: "2px"},
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
        if (this.customView.doesNotExist && this.viewName) {
            this.showMessage("Custom View '" + Ext.util.Format.htmlEncode(this.viewName) + "' not found.");
        }
    },

    onRender : function (ct, position) {
        LABKEY.DataRegion.ViewDesigner.superclass.onRender.call(this, ct, position);
        if (!this.canEdit())
        {
            var msg = "This view is not editable, but you may save a new view with a different name.";
            // XXX: show this.editableErrors in a '?' help tooltip
            this.showMessage(msg);
        }
        else if (this.customView.session) {
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

    canEdit : function () {
        return this.getEditableErrors().length == 0;
    },

    getEditableErrors : function () {
        if (!this.editableErrors) {
            this.editableErrors = LABKEY.DataRegion._getCustomViewEditableErrors(this.customView);
        }
        return this.editableErrors;
    },

    showMessage : function (msg) {
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
        else {
            this.on('afterrender', function () { this.showMessage(msg); }, this, {single: true});
        }
    },

    hideMessage : function () {
        var tb = this.getBottomToolbar();
        tb.getEl().last().update('');
        tb.setVisible(false);
        tb.getEl().slideOut();
    },

    getDesignerTabs : function () {
        return [this.columnsTab, this.filterTab, this.sortTab];
    },

    getActiveGroup : function () {
        var group = (typeof this.activeGroup == 'object') ? this.activeGroup : this.items.get(this.activeGroup);
        return group;
    },

    getActiveDesignerTab : function () {
        if (this.activeGroup !== undefined)
        {
            var group = this.getActiveGroup();
            if (group)
            {
                var tab = group.activeTab || group.items.get(0);
                if (tab instanceof LABKEY.DataRegion.Tab) {
                    return tab;
                }
            }
        }

        return undefined;
    },

    setShowHiddenFields : function (showHidden) {
        this.showHiddenFields = showHidden;

        // show hidden fields in fieldsTree
        this.fieldsTree.getRootNode().cascade(function (node) {
            if (showHidden) {
                if (node.hidden) {
                    node.ui.show();
                }
            }
            else {
                if (node.attributes.hidden) {
                    node.ui.hide();
                }
            }
        }, this);

        var tabs = this.getDesignerTabs();
        for (var i = 0; i < tabs.length; i++)
        {
            var tab = tabs[i];
            if (tab instanceof LABKEY.DataRegion.Tab) {
                tab.setShowHiddenFields(showHidden);
            }
        }
    },

    // Called from FieldTreeLoader. Returns a TreeNode config for a FieldMetaRecord.
    // This method is necessary since we need to determine checked state of the tree
    // using the columnStore.
    createNodeAttrs : function (fieldMetaRecord) {
        var fieldMeta = fieldMetaRecord.data;
        var text = fieldMeta.name;
        if (fieldMeta.caption && fieldMeta.caption != "&nbsp;") {
            text = fieldMeta.caption;
        }

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
        if (tab) {
            return tab.hasField(fieldKey);
        }
    },

    addRecord : function (fieldKey) {
        var tab = this.getActiveDesignerTab();
        if (tab) {
            return tab.addRecord(fieldKey);
        }
    },

    removeRecord : function (fieldKey) {
        var tab = this.getActiveDesignerTab();
        if (tab) {
            return tab.removeRecord(fieldKey);
        }
    },

    onCheckChange : function (node, checked) {
        if (checked) {
            this.addRecord(node.attributes.fieldKey);
        }
        else {
            this.removeRecord(node.attributes.fieldKey);
        }
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
            var checkedFieldKeys = {};
            for (var i = 0; i < storeRecords.length; i++) {
                checkedFieldKeys[storeRecords[i].get("fieldKey").toUpperCase()] = true;
            }

            // suspend check events so checked items aren't re-added to the tab's store
            this.fieldsTree.suspendEvents();
            this.fieldsTree.root.cascade(function () {
                var fieldKey = this.attributes.fieldKey;
                if (fieldKey) {
                    this.getUI().toggleCheck(fieldKey.toUpperCase() in checkedFieldKeys);
                }
            });
            this.fieldsTree.resumeEvents();
        }
    },

    onDeleteClick : function (btn, e) {
        if (this.dataRegion) {
            this.dataRegion.deleteCustomView();
        }
        else {
            this._deleteCustomView(true);
        }
    },

    onRevertClick : function (btn, e) {
        if (this.dataRegion) {
            this.dataRegion.revertCustomView();
        }
        else {
            this._deleteCustomView(false);
        }
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
            if (tab instanceof LABKEY.DataRegion.Tab) {
                tab.revert();
            }
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
            if (!this.validate()) {
                return false;
            }

            var edited = { };
            var urlParameters = { };
            var tabs = this.getDesignerTabs();
            for (var i = 0; i < tabs.length; i++)
            {
                var tab = tabs[i];
                if (tab instanceof LABKEY.DataRegion.Tab) {
                    tab.save(edited, urlParameters);
                }
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
        if (this.dataRegion) {
            this.dataRegion.hideCustomizeView(true);
        }
        else {
            // If we're not attached to a grid, just remove from the DOM
            this.getEl().remove();
        }
    }

});

// Adds a 'fieldKey' attribute to the available fields tree used by the test framework
LABKEY.ext.FieldTreeNodeUI = Ext.extend(Ext.tree.TreeNodeUI, {
    renderElements : function () {
        LABKEY.ext.FieldTreeNodeUI.superclass.renderElements.apply(this, arguments);
        var node = this.node;
        var fieldKey = node.attributes.fieldKey;
        this.elNode.setAttribute("fieldKey", fieldKey);
    }
});

// private
LABKEY.DataRegion.saveCustomizeViewPrompt = function(config)
{
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
        fields: ['path'],
        data: containerData
    });

    var disableSharedAndInherit = LABKEY.user.isGuest || hidden;
    var newViewName = viewName || "New View";
    if (!canEdit && viewName) {
        newViewName = viewName + " Copy";
    }

    var warnedAboutMoving = false;

    var win = new Ext.Window({
        title: "Save Custom View" + (viewName ? ": " + Ext.util.Format.htmlEncode(viewName) : ""),
        cls: "extContainer",
        bodyStyle: "padding: 6px",
        modal: true,
        width: 490,
        height: 260,
        layout: "form",
        defaults: { tooltipType: "title" },
        items: [{
            ref: "defaultNameField",
            xtype: "radio",
            fieldLabel: "View Name",
            boxLabel: "Default view for this page",
            inputValue: "default",
            name: "saveCustomView_namedView",
            checked: canEdit && !viewName,
            disabled: hidden || !canEdit
        },{
            xtype: "compositefield",
            ref: "nameCompositeField",
            // Let the saveCustomView_name field display the error message otherwise it will render as "saveCustomView_name: error message"
            combineErrors: false,
            items: [{
                xtype: "radio",
                fieldLabel: "",
                boxLabel: "Named",
                inputValue: "named",
                name: "saveCustomView_namedView",
                checked: !canEdit || viewName,
                handler: function (radio, value) {
                    // nameCompositeField.items will be populated after initComponent
                    if (win.nameCompositeField.items.get)
                    {
                        var nameField = win.nameCompositeField.items.get(1);
                        if (value) {
                            nameField.enable();
                        }
                        else {
                            nameField.disable();
                        }
                    }
                },
                scope: this
            },{
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
                validator: function (value) {
                    if ("default" === value.trim()) {
                        return "The view name 'default' is not allowed";
                    }
                    return true;
                },
                selectOnFocus: true,
                value: newViewName,
                disabled: hidden || (canEdit && !viewName)
            }]
        },{
            xtype: "box",
            style: "padding-left: 122px; padding-bottom: 8px",
            html: "<em>The " + (!config.canEdit ? "current" : "shadowed") + " view is not editable.<br>Please enter an alternate view name.</em>",
            hidden: canEdit
        },{
            xtype: "spacer",
            height: "8"
        },{
            ref: "sharedField",
            xtype: "checkbox",
            name: "saveCustomView_shared",
            fieldLabel: "Shared",
            boxLabel: "Make this grid view available to all users",
            checked: shared,
            disabled: disableSharedAndInherit || !canEditSharedViews
        },{
            ref: "inheritField",
            xtype: "checkbox",
            name: "saveCustomView_inherit",
            fieldLabel: "Inherit",
            boxLabel: "Make this grid view available in child folders",
            checked: containerFilterable && inherit,
            disabled: disableSharedAndInherit || !containerFilterable,
            hidden: !containerFilterable,
            listeners: {
                check: function (checkbox, checked) {
                    Ext.ComponentMgr.get("saveCustomView_targetContainer").setDisabled(!checked);
                }
            }
        },{
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
                select: function (combobox) {
                    if (!warnedAboutMoving && combobox.getValue() != config.containerPath)
                    {
                        warnedAboutMoving = true;
                        Ext.Msg.alert("Moving a Saved View", "If you save, this view will be moved from '" + config.containerPath + "' to " + combobox.getValue());
                    }
                }
            }
        }],
        buttons: [{
            text: "Save",
            handler: function () {
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
                    if (!win.defaultNameField.getValue()) {
                        o.name = nameField.getValue();
                    }
                    o.session = false;
                    if (!o.session && canEditSharedViews)
                    {
                        o.shared = win.sharedField.getValue();
                        // Issue 13594: disallow setting inherit bit if query view has no available container filters
                        o.inherit = containerFilterable && win.inheritField.getValue();
                    }
                }

                if (o.inherit) {
                    o.containerPath = win.targetContainer.getValue();
                }

                // Callback is responsible for closing the save dialog window on success.
                success.call(scope, win, o);
            },
            scope: this
        },{
            text: "Cancel",
            handler: function () {
                win.close();
            }
        }]
    });
    win.show();
};
