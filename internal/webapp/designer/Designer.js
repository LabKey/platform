/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.ext4.designer.ViewDesigner', {

    extend: 'Ext.panel.Panel',

    cls: 'customize-view-designer',

    constructor : function (config)
    {
        // For tooltips on the fieldsTree TreePanel
        Ext4.tip.QuickTipManager.init();

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
        this.fieldMetaStore = Ext4.create('LABKEY.ext4.designer.FieldMetaStore', {
            containerPath: this.containerPath,
            schemaName: this.schemaName,
            queryName: this.queryName,
            data: this.query
        });

        {
            // Add any additional field metadata for view's selected columns, sorts, filters.
            // The view may be filtered or sorted upon columns not present in the query's selected column metadata.
            // The FieldMetaStore uses a reader that expects the field metadata to be under a 'columns' property instead of 'fields'
            this.fieldMetaStore.loadRawData({columns: this.customView.fields}, true);

            // Add user filters
            this.userFilter = config.userFilter || [];
            for (var i = 0; i < this.userFilter.length; i++)
            {
                // copy the filter so the original userFilter isn't modified by the designer
                var userFilter = Ext4.apply({urlParameter: true}, this.userFilter[i]);
                this.customView.filter.unshift(userFilter);
            }

            // Add user sort
            var newSortArray = [];
            this.userSort = config.userSort || [];
            for (var i = 0; i < this.userSort.length; i++)
            {
                // copy the sort so the original userSort isn't modified by the designer
                var userSort = Ext4.apply({urlParameter: true}, this.userSort[i]);
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

        // Issue 11188: Don't use friendly id for tabs (eg., "ColumnsTab") -- breaks showing two customize views on the same page.
        // Provide mapping from friendly tab names to tab index.
        this.tabInfoArr = [
            {name: 'ColumnsTab', text: 'Columns', index: 0, active: false},
            {name: 'FilterTab', text: 'Filter', index: 1, active: false},
            {name: 'SortTab', text: 'Sort', index: 2, active: false}
        ];

        config.activeTab = this.translateTabName(config.activeTab);

        Ext4.each(this.tabInfoArr, function(tab){
            tab.active = tab.index == config.activeTab;
        });

        this.columnsTab = Ext4.create('LABKEY.ext4.designer.ColumnsTab', {
            name: "ColumnsTab",
            designer: this,
            schemaName: this.schemaName,
            queryName: this.queryName,
            viewName: this.viewName,
            fieldMetaStore: this.fieldMetaStore,
            customView: this.customView
        });

        this.filterTab = Ext4.create('LABKEY.ext4.designer.FilterTab', {
            name: "FilterTab",
            designer: this,
            fieldMetaStore: this.fieldMetaStore,
            customView: this.customView
        });

        this.sortTab = Ext4.create('LABKEY.ext4.designer.SortTab', {
            name: "SortTab",
            designer: this,
            fieldMetaStore: this.fieldMetaStore,
            customView: this.customView
        });

        this.fieldsTree = Ext4.create('Ext.tree.TreePanel', {
            autoScroll: true,
            border: false,
            cls: "labkey-fieldmeta-tree",
            // TODO reenable after conversion to Ext4
            //root: new Ext.tree.AsyncTreeNode({
            //    id: "<ROOT>",
            //    expanded: true,
            //    expandable: false,
            //    draggable: false
            //}),
            //rootVisible: false,
            //loader: new LABKEY.ext.FieldTreeLoader({
            //    store: this.fieldMetaStore,
            //    designer: this,
            //    containerPath: this.containerPath,
            //    schemaName: this.schemaName,
            //    queryName: this.queryName,
            //    createNodeConfigFn: {fn: this.createNodeAttrs, scope: this}
            //}),
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

        config = Ext4.applyIf(config, {
            height: 280,
            tabWidth: 80,
            activeTab: 0,
            border: false,
            bodyStyle: 'background-color: transparent;'
        });

        config = Ext4.apply(config, {
            layout: 'border',
            items: [
                this.getTabsDataView(true, config.tabWidth),
                this.getTabsMainPanel(config.activeTab, footerBar)
            ]
        });

        this.addEvents({
            beforeviewsave: true,
            viewsave: true
        });

        this.callParent([config]);

        this.fieldsTree.on('checkchange', this.onCheckChange, this);
        this.getInnerTabPanel().on('tabchange', this.onTabChange, this);

        // Show 'does not exist' message only for non-default views.
        if (this.customView.doesNotExist && this.viewName) {
            this.showMessage("Custom View '" + this.viewName + "' not found.");
        }
    },

    getTabsMainPanel : function(activeTab, footerBar) {
        if (!this.tabsMainPanel) {
            this.tabsMainPanel = Ext4.create('Ext.panel.Panel', {
                region: 'center',
                layout: 'border',
                border: false,
                items: [
                    this.getAvailableFieldsPanel(),
                    this.getInnerTabPanel(activeTab)
                ],
                buttonAlign: "left",
                dockedItems: [{
                    xtype: 'toolbar',
                    dock: 'bottom',
                    height: 20,
                    hidden: true,
                    layout: 'fit',
                    border: false,
                    items: [{
                        xtype: 'container',
                        // would like to use 'labkey-status-info' class instead of inline style, but it centers and stuff
                        cls: 'labkey-customview-message'
                    }]
                }],
                fbar: footerBar
            })
        }

        return this.tabsMainPanel;
    },

    getTabsStore : function() {
        if (!this.tabsDataViewStore) {
            this.tabsDataViewStore = Ext4.create('Ext.data.Store', {
                fields: ['name', 'text', 'index', 'active'],
                data: this.tabInfoArr
            });
        }

        return this.tabsDataViewStore;
    },

    getTabsDataView : function(create, tabWidth) {
        if (!this.tabsDataView && create) {
            this.tabsDataView = Ext4.create('Ext.view.View', {
                region: 'west',
                width: tabWidth,
                store: this.getTabsStore(),
                tpl: new Ext4.XTemplate(
                    '<ul><tpl for=".">',
                    '<li class="labkey-customview-tab {active:this.getAdditionalCls}">{text}</li>',
                    '</tpl></ul>',
                    {
                        getAdditionalCls : function(active) {
                            return active ? "labkey-customview-activetab" : "";
                        }
                    }
                ),
                itemSelector: 'li.labkey-customview-tab',
                listeners: {
                    scope: this,
                    itemclick: this.onTabsItemClick
                }
            });
        }

        return this.tabsDataView;
    },

    updateTabText : function(store, name, text) {
        var record = store.findRecord('name', name);
        if (record) {
            record.set('text', text);
        }
    },

    onTabsItemClick : function(view, record, item, index, e) {
        if (!record.get('active'))
        {
            var currentlRec = view.getStore().findRecord('active', true);
            if (currentlRec) {
                currentlRec.set('active', false);
            }

            record.set('active', true);
            this.setActiveDesignerTab(record.get('index'));
        }
    },

    setActiveDesignerTab : function(tab) {
        this.getInnerTabPanel().setActiveTab(this.translateTabName(tab));
    },

    getAvailableFieldsPanel : function() {
        if (!this.availableFieldsPanel) {
            this.availableFieldsPanel = Ext4.create('Ext.panel.Panel', {
                region: 'west',
                title: "Available Fields",
                flex: 1,
                border: false,
                split: true,
                minWidth: 220,
                maxWidth: 700,
                layout: 'fit',
                items: [this.fieldsTree]
            });
        }

        return this.availableFieldsPanel;
    },

    getInnerTabPanel : function(activeTab) {
        if (!this.tabsTabPanel) {
            this.tabsTabPanel = Ext4.create('Ext.tab.Panel', {
                region: 'center',
                flex: 1,
                border: false,
                activeTab: activeTab,
                tabBar: {hidden: true},
                defaults: {
                    border: false
                    //layoutOnTabChange: true
                },
                items: [{
                    items: [this.columnsTab]
                }, {
                    items: [this.filterTab]
                }, {
                    items: [this.sortTab]
                }]
            });
        }

        return this.tabsTabPanel;
    },

    onRender : function (ct, position) {
        this.callParent([ct, position]);

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
        this.callParent();

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

    // tab may be true, tab index, tab name, or the tab instance.
    translateTabName : function (tab) {
        if (tab === null || tab === undefined || Ext.isBoolean(tab)) {
            return 0;
        }
        else if (Ext4.isNumber(tab)) {
            return tab;
        }
        else if (Ext4.isString(tab)) {
            for (var i = 0; i < this.tabInfoArr.length; i++) {
                if (this.tabInfoArr[i].name == tab) {
                    return this.tabInfoArr[i].index;
                }
            }
        }
        return tab;
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

    getMessageToolBar : function() {
        var dockedItems = this.getTabsMainPanel().getDockedItems('toolbar[dock="bottom"][ui="default"]');
        if (dockedItems.length == 1) {
            return dockedItems[0];
        }
        return undefined;
    },

    showMessage : function (msg) {
        // XXX: support multiple messages and [X] close box
        var tb = this.getMessageToolBar();
        if (tb && tb.getEl())
        {
            var el = tb.down('container');
            el.update("<span class='labkey-tool labkey-tool-close' style='float:right;vertical-align:top;'></span><span>"
                + Ext4.util.Format.htmlEncode(msg) + "</span>");
            tb.show();
            tb.getEl().slideIn();
            tb.getEl().on('click', function () { this.hideMessage(); }, this, {single: true});
        }
        else {
            this.on('afterrender', function () { this.showMessage(msg); }, this, {single: true});
        }
    },

    hideMessage : function () {
        var tb = this.getMessageToolBar();
        if (tb)
        {
            tb.down('container').update('');
            tb.hide();
        }
    },

    getDesignerTabs : function () {
        return [this.columnsTab, this.filterTab, this.sortTab];
    },

    getActiveDesignerTab : function () {
        var tab = this.getInnerTabPanel().getActiveTab().down('panel');
        if (tab instanceof LABKEY.ext4.designer.BaseTab) {
            return tab;
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
            if (tab instanceof LABKEY.ext4.designer.BaseTab) {
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
            qtip: fieldMetaRecord.getToolTipHtml(),
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

    onTabChange : function () {

        var tab = this.getActiveDesignerTab();
        if (tab instanceof LABKEY.ext4.designer.BaseTab)
        {
            // get the checked fields from the new tab's store
            var storeRecords = tab.getList().getStore().getRange();
            var checkedFieldKeys = {};
            for (var i = 0; i < storeRecords.length; i++) {
                checkedFieldKeys[storeRecords[i].get("fieldKey").toUpperCase()] = true;
            }

            // suspend check events so checked items aren't re-added to the tab's store
            // TODO reenable after conversion to Ext4
            //this.fieldsTree.suspendEvents();
            //this.fieldsTree.root.cascade(function () {
            //    var fieldKey = this.attributes.fieldKey;
            //    if (fieldKey) {
            //        this.getUI().toggleCheck(fieldKey.toUpperCase() in checkedFieldKeys);
            //    }
            //});
            //this.fieldsTree.resumeEvents();
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
        Ext4.Ajax.request({
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
        var config = Ext4.applyIf({
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
            if (tab instanceof LABKEY.ext4.designer.BaseTab) {
                tab.revert();
            }
        }
    },

    validate : function () {
        var tabs = this.getDesignerTabs();
        for (var i = 0; i < tabs.length; i++)
        {
            var tab = tabs[i];
            if (tab instanceof LABKEY.ext4.designer.BaseTab)
            {
                if (tab.validate() === false)
                {
                    this.setActiveDesignerTab(tab);
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
                if (tab instanceof LABKEY.ext4.designer.BaseTab) {
                    tab.save(edited, urlParameters);
                }
            }
            Ext4.apply(edited, properties);

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
                Ext4.Msg.alert("Error saving view", errorInfo.exception);
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
        this.callParent();

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
