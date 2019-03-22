/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.internal.ViewDesigner.Designer', {

    extend: 'Ext.panel.Panel',

    cls: 'labkey-customize-grid-panel',
    layout: 'border',
    height: 310,
    width: 800,
    activeTab: 0,
    border: false,
    bodyStyle: 'background-color: transparent;',

    statics: {
        RESERVED_VIEW_NAMES : ['default', '~~DETAILS~~', '~~INSERT~~', '~~UPDATE~~'],
        saveCustomizeViewPrompt: function(config) {
            var success = config.success,
                    scope = config.scope,
                    viewName = config.name,
                    hidden = config.hidden,
                    session = config.session,
                    inherit = config.inherit,
                    shared = config.shared,
                    containerPath = config.containerPath;

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

            var containerStore = Ext4.create('Ext.data.ArrayStore', {
                fields: ['path'],
                data: containerData
            });

            var disableSharedAndInherit = LABKEY.user.isGuest || hidden;
            var newViewName = viewName || "New Grid View";
            if (!canEdit && viewName) {
                newViewName = viewName + " Copy";
            }

            var warnedAboutMoving = false;

            var win = Ext4.create('Ext.window.Window', {
                title: "Save Custom Grid View",
                cls: "labkey-customview-save",
                border: false,
                autoShow: true,
                bodyStyle: "padding: 6px",
                modal: true,
                width: 490,
                height: 260,
                layout: "form",
                defaults: { tooltipType: "title" },
                items: [{
                    xtype: "radio",
                    itemId: "defaultNameField",
                    fieldLabel: "Grid View Name",
                    boxLabel: "Default grid view for this page",
                    inputValue: "default",
                    name: "saveCustomView_namedView",
                    checked: canEdit && !viewName,
                    disabled: hidden || !canEdit
                },{
                    xtype: "fieldcontainer",
                    itemId: "nameFieldContainer",
                    layout: {
                        type: 'hbox'
                    },
                    // Let the saveCustomView_name field display the error message otherwise it will render as "saveCustomView_name: error message"
                    combineErrors: false,
                    items: [{
                        xtype: "radio",
                        width: 175,
                        fieldLabel: " ",
                        labelSeparator: "",
                        boxLabel: "Named",
                        inputValue: "named",
                        name: "saveCustomView_namedView",
                        checked: !canEdit || viewName,
                        handler: function(radio, value) {
                            // nameFieldContainer.items will be populated after initComponent
                            var nameFieldContainer = win.down('#nameFieldContainer');
                            if (nameFieldContainer.items.get) {
                                var nameField = nameFieldContainer.items.get(1);
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
                        xtype: "textfield",
                        itemId: "nameTextField",
                        fieldLabel: "",
                        name: "saveCustomView_name",
                        tooltip: "Name of the custom grid view",
                        tooltipType: "title",
                        msgTarget: "side",
                        allowBlank: false,
                        emptyText: "Name is required",
                        width: 280,
                        autoCreate: {tag: 'input', type: 'text', size: '50'},
                        validator: function(value) {
                            var trimmedValue = value.trim();
                            if (trimmedValue.length == 0)
                                return "Blank grid view name is not allowed";
                            else if (trimmedValue.length > 50)
                                return "The grid view name must be less than 50 characters long";
                            else if (LABKEY.internal.ViewDesigner.Designer.RESERVED_VIEW_NAMES.indexOf(trimmedValue) > -1) {
                                return "The grid view name '" + value + "' is not allowed";
                            }
                            return true;
                        },
                        selectOnFocus: true,
                        value: newViewName,
                        disabled: hidden || (canEdit && !viewName)
                    }]
                },{
                    xtype: "box",
                    style: "padding-left: 122px; padding-bottom: 8px; font-style: italic;",
                    html: "The " + (!config.canEdit ? "current" : "shadowed") + " grid view is not editable.<br>Please enter an alternate name.",
                    hidden: canEdit
                },{
                    // spacer
                    xtype: "box",
                    height: 8
                },{
                    xtype: "checkbox",
                    itemId: "sharedField",
                    name: "saveCustomView_shared",
                    fieldLabel: "Shared",
                    boxLabel: "Make this grid view available to all users",
                    checked: shared,
                    disabled: disableSharedAndInherit || !canEditSharedViews
                },{
                    xtype: "checkbox",
                    itemId: "inheritField",
                    name: "saveCustomView_inherit",
                    fieldLabel: "Inherit",
                    boxLabel: "Make this grid view available in child folders",
                    checked: containerFilterable && inherit,
                    disabled: disableSharedAndInherit || !containerFilterable,
                    hidden: !containerFilterable,
                    listeners: {
                        check: function(checkbox, checked) {
                            Ext4.ComponentMgr.get("saveCustomView_targetContainer").setDisabled(!checked);
                        }
                    }
                },{
                    xtype: "combo",
                    itemId: "targetContainer",
                    name: "saveCustomView_targetContainer",
                    id: "saveCustomView_targetContainer",
                    fieldLabel: "Save in Folder",
                    store: containerStore,
                    value: containerPath,
                    displayField: 'path',
                    valueField: 'path',
                    width: 300,
                    triggerAction: 'all',
                    mode: 'local',
                    editable: false,
                    hidden: !containerFilterable,
                    disabled: disableSharedAndInherit || !containerFilterable || !inherit,
                    listeners: {
                        select: function(combobox) {
                            if (!warnedAboutMoving && combobox.getValue() != containerPath)
                            {
                                warnedAboutMoving = true;
                                Ext4.Msg.alert("Moving a Saved Grid View", "If you save, this grid view will be moved from '" + containerPath + "' to " + combobox.getValue());
                            }
                        }
                    }
                }],
                buttons: [{
                    text: "Save",
                    handler: function() {
                        var nameField = win.down('#nameFieldContainer').items.get(1);
                        if (!nameField.isValid())
                        {
                            Ext4.Msg.alert("Invalid grid view name", nameField.getErrors());
                            return;
                        }
                        if (!canEdit && viewName == nameField.getValue())
                        {
                            Ext4.Msg.alert("Error saving", "This grid view is not editable.  You must save this grid view with an alternate name.");
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
                            if (!win.down('#defaultNameField').getValue()) {
                                o.name = nameField.getValue();
                            }
                            o.session = false;
                            if (!o.session && canEditSharedViews)
                            {
                                o.shared = win.down('#sharedField').getValue();
                                // Issue 13594: disallow setting inherit bit if query view has no available container filters
                                o.inherit = containerFilterable && win.down('#inheritField').getValue();
                            }
                        }

                        if (o.inherit) {
                            o.containerPath = win.down('#targetContainer').getValue();
                        }

                        // Callback is responsible for closing the save dialog window on success.
                        success.call(scope, win, o);
                    },
                    scope: this
                },{
                    text: "Cancel",
                    handler: function() {
                        win.close();
                    }
                }]
            });
        }
    },

    constructor : function(config) {

        // For tooltips on the fieldsTree TreePanel
        Ext4.tip.QuickTipManager.init();

        this.cache = LABKEY.internal.ViewDesigner.QueryDetailsCache;
        this.cache.on('beforecacheresponse', this.onBeforeCache, this);

        this.dataRegion = config.dataRegion;

        this.containerPath = config.containerPath;
        this.schemaName = config.schemaName;
        this.queryName = config.queryName;
        this.viewName = config.viewName || "";
        this.query = config.query;

        this.cache.add({
            schema: this.schemaName,
            query: this.queryName,
            view: this.viewName
        }, this.query);

        // Find the custom view in the LABKEY.Query.getQueryDetails() response.
        this.customView = null;

        Ext4.each(this.query.views, function(view) {
            if (view.name == this.viewName) {
                this.customView = view;
                return false;
            }
        }, this);

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

        this.getFieldMetaStore().loadRawData(this.query, true /* append the records */);

        this.getColumnTree();

        // Add any additional field metadata for view's selected columns, sorts, filters.
        // The view may be filtered or sorted upon columns not present in the query's selected column metadata.
        // The FieldMetaStore uses a reader that expects the field metadata to be under a 'columns' property instead of 'fields'
        if (Ext4.isDefined(this.customView)) {
            this.fieldMetaStore.loadRawData({
                columns: this.customView.fields
            }, true /* append the records */);
        }

        // Add user filters
        this.userFilter = config.userFilter || [];
        Ext4.each(this.userFilter, function(filter) {
            // copy the filter so the original userFilter isn't modified by the designer
            var userFilter = Ext4.apply({urlParameter: true}, filter);
            this.customView.filter.unshift(userFilter);
        }, this);

        // Add user sort
        var newSortArray = [];
        this.userSort = config.userSort || [];
        Ext4.each(this.userSort, function(sort) {
            // copy the sort so the original userSort isn't modified by the designer
            newSortArray.push(Ext4.apply({ urlParameter: true }, sort));
        });

        // Merge userSort and existing customView sort.
        for (var i = 0; i < this.customView.sort.length; i++) {
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
        if (this.userContainerFilter && this.customView.containerFilter != this.userContainerFilter) {
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

        Ext4.each(this.tabInfoArr, function(tab) {
            tab.active = tab.index == config.activeTab;
        });

        this.columnsTab = Ext4.create('LABKEY.internal.ViewDesigner.tab.ColumnsTab', {
            name: "ColumnsTab",
            designer: this,
            schemaName: this.schemaName,
            queryName: this.queryName,
            viewName: this.viewName,
            fieldMetaStore: this.fieldMetaStore,
            customView: this.customView,
            listeners: {
                recordremoved: this.onRemoveColumn,
                scope: this
            }
        });

        this.filterTab = Ext4.create('LABKEY.internal.ViewDesigner.tab.FilterTab', {
            name: "FilterTab",
            designer: this,
            fieldMetaStore: this.fieldMetaStore,
            customView: this.customView,
            listeners: {
                recordremoved: this.onRemoveColumn,
                scope: this
            }
        });

        this.sortTab = Ext4.create('LABKEY.internal.ViewDesigner.tab.SortTab', {
            name: "SortTab",
            designer: this,
            fieldMetaStore: this.fieldMetaStore,
            customView: this.customView,
            listeners: {
                recordremoved: this.onRemoveColumn,
                scope: this
            }
        });

        this.callParent([config]);

        this.addEvents('beforesaveview', 'viewsave', 'beforedeleteview', 'deleteview');
    },

    initComponent : function() {

        this.items = [
            this.getCustomViewClose(),
            this.getTabsDataView(true),
            this.getTopMessagePanel(),
            this.getTabsMainPanel(),
            this.getBottomToolbarPanel()
        ];

        this.callParent();

        this.fieldsTree.on('checkchange', this.onCheckChange, this);
        this.getInnerTabPanel().on('tabchange', this.onTabChange, this);
    },

    onBeforeCache : function(response) {
        var groupedByMember = this.columnsByMember(response.columns),
            allColumns = [];

        // clear out response columns
        response.columns = groupedByMember[null];

        Ext4.iterate(groupedByMember, function(name, columns) {
            if (name !== "null" && columns.length > 0) {
                var crossTabMetadata = columns[0]['crosstabColumnMember'],
                    rootCol = {
                        fieldKeyPath: crossTabMetadata.value,
                        caption: crossTabMetadata.caption,
                        dimensionFieldKey: crossTabMetadata.dimensionFieldKey,
                        leaf: false,
                        lookup: {},
                        selectable: false,
                        crosstabMember: true,
                        expanded: true,
                        columns: columns
                    };

                response.columns.push(rootCol);

                if (columns.length > 0) {
                    allColumns = allColumns.concat(columns);
                }
            }
        }, this);

        this.getFieldMetaStore().loadRawData({ columns: allColumns }, true);
    },

    columnsByMember : function(responseColumns) {
        var groupedByMember = {},
            groupedByMemberKey = null,
            groupedByMemberColumns,
            crosstabColumnMember,
            col, i=0;

        for (; i < responseColumns.length; i++) {
            col = responseColumns[i];
            groupedByMemberKey = null;
            crosstabColumnMember = col['crosstabColumnMember'];

            if (crosstabColumnMember) {
                groupedByMemberKey = [
                    crosstabColumnMember.dimensionFieldKey,
                    crosstabColumnMember.value,
                    crosstabColumnMember.caption
                ].join('~');
            }

            groupedByMemberColumns = groupedByMember[groupedByMemberKey];
            if (groupedByMemberColumns === undefined) {
                groupedByMember[groupedByMemberKey] = groupedByMemberColumns = [];
            }
            groupedByMemberColumns.push(col);
        }

        return groupedByMember;
    },

    getColumnTree : function() {

        if (!this.fieldsTree) {
            var loaded = false,
                rendered = false,
                isExpand = false,
                me = this;

            // each expand/collapse needs to configure the nodes again for hidden/checked
            var expandCollapse = function() {
                isExpand = true;
                firstCheck();
            };

            var firstCheck = function() {
                if (loaded && rendered) {
                    if (isExpand) {
                        isExpand = false;
                        me.configureHidden();
                        me.configureChecked();
                    }
                    else {
                        // let things render
                        Ext4.defer(function() {
                            me.configureHidden();
                            me.configureChecked();
                        }, 300);
                    }
                }
            };

            // Create the tree store
            var treeStore = Ext4.create('LABKEY.internal.ViewDesigner.FieldMetaTreeStore', {
                containerPath: this.containerPath,
                schemaName: this.schemaName,
                queryName: this.queryName,
                viewName: this.viewName,
                listeners: {
                    load: function(store) {
                        loaded = true;
                        firstCheck();

                        this.fieldMetaStore.loadRawData(store.getProxy().getReader().rawData, true);
                    },
                    expand: expandCollapse,
                    collapse: expandCollapse,
                    scope: this
                }
            });

            this.fieldsTree = Ext4.create('Ext.tree.TreePanel', {
                autoScroll: true,
                border: false,
                cls: 'labkey-fieldmeta-tree',
                height: 200,
                rootVisible: false,
                store: treeStore,
                dockedItems: [{
                    xtype: 'toolbar',
                    dock: 'bottom',
                    height: 34,
                    style: 'padding-left: 5px;',
                    items: [{
                        xtype: 'checkbox',
                        boxLabel: 'Show Hidden Fields',
                        checked: this.showHiddenFields,
                        handler: function(checkbox, checked) {
                            this.setShowHiddenFields(checked);
                        },
                        scope: this
                    }]
                }],
                listeners: {
                    afterrender: function() {
                        rendered = true;
                        treeStore.filterBy(function(record) {
                            // Issue 26404: filters and sorts on columns from look up tables result in duplicate nodes.
                            // skip node on CV render if it's a child node of a lookup table that's collpased
                            // All nodes except ROOT are collapsed on render
                            if (record && record.get('fieldKey').indexOf('/') == -1) {
                                return record;
                            }
                            return false;
                        }, null /* scope */, true /* matchParentNodes */);

                        firstCheck();
                    },
                    beforeselect: function(tree, record) {
                        if (record.get('disabled')) {
                            return false; // do not allow selection of disabled nodes
                        }
                    },
                    scope: this
                }
            });
        }

        return this.fieldsTree;
    },

    getTabsMainPanel : function() {
        if (!this.tabsMainPanel) {
            this.tabsMainPanel = Ext4.create('Ext.panel.Panel', {
                region: 'center',
                layout: 'border',
                border: false,
                items: [
                    {
                        xtype: 'panel',
                        region: 'west',
                        cls: 'labkey-customview-panel',
                        flex: 1,
                        border: false,
                        margin: '0 5px 0 0',
                        items: [
                            {
                                xtype: 'box',
                                cls: 'labkey-customview-title',
                                html: 'Available Fields'
                            },
                            this.fieldsTree
                        ]
                    },
                    this.getInnerTabPanel(this.activeTab)
                ]
            })
        }

        return this.tabsMainPanel;
    },

    getFooterItems : function() {

        var canEdit = this.canEdit();

        // enabled for named editable views that exist; additionally overridable module based view is not deletable.
        var deleteEnabled = canEdit && this.customView.deletable && this.customView.name && !this.customView.doesNotExist;

        // enabled for saved (non-session) editable views or customized default or overridable module based view (not new) views.
        var revertEnabled = canEdit && (this.customView.revertable || this.customView.session || (!this.customView.name && !this.customView.doesNotExist));

        var items = [{
            text: 'Delete',
            tooltip: 'Delete ' + (this.customView.shared ? 'shared' : 'your') + ' saved grid view',
            tooltipType: 'title',
            disabled: !deleteEnabled,
            handler: this.onDeleteClick,
            scope: this
        }];

        // Only add Revert if we're being rendered attached to a grid
        if (this.dataRegion) {
            items.push({
                text: "Revert",
                tooltip: "Revert " + (this.customView.shared ? "shared" : "your") + " edited grid view",
                tooltipType: "title",
                // disabled for hidden, saved (non-session), customized (not new) default view, or uneditable views
                disabled: !revertEnabled,
                handler: this.onRevertClick,
                scope: this
            });
        }

        items.push('->');

        // Only add View Grid if we're being rendered attached to a grid
        if (this.dataRegion) {
            items.push({
                text: "View Grid",
                tooltip: "Apply changes to the grid view and reshow grid",
                tooltipType: "title",
                handler: this.onApplyClick,
                scope: this
            });
        }

        if (!this.query.isTemporary) {
            items.push({
                text: "Save",
                tooltip: "Save changes",
                tooltipType: "title",
                handler: this.onSaveClick,
                scope: this
            });
        }

        return items;
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

    getCustomViewClose : function() {
        if (!this.customViewClose) {
            this.customViewClose = Ext4.create('Ext.Component', {
                region: 'north',
                html: '<i class="fa fa-times fa-lg labkey-customview-close"></i>'
            });

            this.customViewClose.on('render', function(cmp) {
                this.attachClickHandler(cmp, 'labkey-customview-close', function() { this.close();});
            }, this);
        }

        return this.customViewClose;
    },

    getTabsDataView : function(create) {
        if (!this.tabsDataView && create) {
            this.tabsDataView = Ext4.create('Ext.view.View', {
                region: 'north',
                padding: '0 0 5px 0',
                store: this.getTabsStore(),
                tpl: new Ext4.XTemplate(
                    '<ul class="nav nav-tabs">',
                    '<tpl for=".">',
                        '<li role="presentation" class="labkey-customview-tab {active:this.getAdditionalCls}"><a>{text}</a></li>',
                    '</tpl>',
                    '</ul>',
                    {
                        getAdditionalCls : function(active) {
                            return active ? "active" : "";
                        }
                    }
                ),
                itemSelector: 'li',
                listeners: {
                    scope: this,
                    itemclick: this.onTabsItemClick
                }
            });
        }

        return this.tabsDataView;
    },

    updateTabText : function(name, text) {
        var record = this.getTabsStore().findRecord('name', name);
        if (record) {
            record.set('text', text);
        }
    },

    onTabsItemClick : function(view, record) {
        if (!record.get('active')) {
            // suspend events so that we can just use the view.refresh to update at the end
            view.getStore().suspendEvents(false);
            var currentlRec = view.getStore().findRecord('active', true);
            if (currentlRec) {
                currentlRec.set('active', false);
            }
            record.set('active', true);
            view.getStore().resumeEvents();
            view.refresh();

            this.setActiveDesignerTab(record.get('index'));
        }
    },

    setActiveDesignerTab : function(tab) {
        this.getInnerTabPanel().setActiveTab(this.translateTabName(tab));
    },

    getInnerTabPanel : function(activeTab) {
        if (!this.tabsTabPanel) {
            this.tabsTabPanel = Ext4.create('Ext.tab.Panel', {
                region: 'center',
                flex: 1,
                border: false,
                activeTab: activeTab,
                tabBar: {hidden: true},
                items: this.getDesignerTabs()
            });
        }

        return this.tabsTabPanel;
    },

    getBottomToolbarPanel : function() {
        if (!this.bottomToolbarPanel) {
            this.bottomToolbarPanel = Ext4.create('Ext.panel.Panel', {
                region: 'south',
                layout: 'fit',
                border: false,
                items: [{
                    xtype: 'toolbar',
                    dock: 'bottom',
                    ui: 'footer',
                    cls: 'labkey-customview-button-footer',
                    items: this.getFooterItems()
                }]
            });
        }

        return this.bottomToolbarPanel;
    },

    getTopMessagePanel : function() {
        if (!this.topMessagePanel) {
            var items = [];
            if (this.customView.doesNotExist && this.viewName) {
                items.push(this.getMessageCmp("Custom Grid View '" + this.viewName + "' not found.", false));
            }
            if (!this.canEdit()) {
                items.push(this.getMessageCmp("This grid view is not editable, but you may save a new grid view with a different name.", false));
            }
            if (this.customView.session) {
                items.push(this.getMessageCmp("Editing an unsaved grid view.", false));
            }

            this.topMessagePanel = Ext4.create('Ext.panel.Panel', {
                region: 'north',
                border: false,
                items: items
            });

            // update the main panel height to account for the messages
            this.height = this.height + (items.length * 25);
        }

        return this.topMessagePanel;
    },

    getMessageCmp : function(msg, closable) {
        var msgId = Ext4.id(),
            msgBox = Ext4.create('Ext.Component', {
                xtype: 'box',
                id: msgId,
                cls: 'labkey-customview-message alert-warning',
                html: (closable ? '<span class="fa fa-times labkey-tool-close" title="Close message"></span>' : '')
                        + '<div>' + Ext4.htmlEncode(msg) + '</div>'
            });

        if (closable) {
            msgBox.on('render', function(cmp) {
                this.attachClickHandler(cmp, 'labkey-tool-close', function() { this.hideMessage(msgId);});
            }, this);
        }

        return msgBox;
    },

    hideMessage : function(msgId) {
        var msgBox = this.getTopMessagePanel().down('#' + msgId);
        this.getTopMessagePanel().remove(msgBox);
    },

    beforeDestroy : function() {
        this.callParent();

        if (this.columnsTab) {
            this.columnsTab.destroy();
            delete this.columnsTab;
        }
        if (this.filterTab) {
            this.filterTab.destroy();
            delete this.filterTab;
        }
        if (this.sortTab) {
            this.sortTab.destroy();
            delete this.sortTab;
        }
        if (this.fieldMetaStore) {
            this.fieldMetaStore.destroy();
        }
        if (this.dataRegion)
            delete this.dataRegion;
    },

    // tab may be true, tab index, tab name, or the tab instance.
    translateTabName : function(tab) {
        if (tab === null || tab === undefined || Ext4.isBoolean(tab)) {
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

    canEdit : function() {
        return this.getEditableErrors().length == 0;
    },

    getEditableErrors : function() {
        if (!this.editableErrors) {
            this.editableErrors = LABKEY.DataRegion.getCustomViewEditableErrors(this.customView);
        }
        return this.editableErrors;
    },

    getFieldMetaStore : function() {
        if (!this.fieldMetaStore) {
            this.fieldMetaStore = Ext4.create('LABKEY.internal.ViewDesigner.FieldMetaStore', {
                containerPath: this.containerPath,
                schemaName: this.schemaName,
                queryName: this.queryName
            });
        }

        return this.fieldMetaStore;
    },

    attachClickHandler : function(cmp, subElCls, handler) {
        var subEl = Ext4.dom.Query.selectNode('.' + subElCls, cmp.getEl().dom);
        if (subEl) {
            Ext4.get(subEl).on('click', handler, this);
        }
    },

    getDesignerTabs : function() {
        return [this.columnsTab, this.filterTab, this.sortTab];
    },

    getActiveDesignerTab : function() {
        var tab = this.getInnerTabPanel().getActiveTab();
        if (tab instanceof LABKEY.internal.ViewDesigner.tab.BaseTab) {
            return tab;
        }
        // return undefined;
    },

    showHideNodes : function() {

        var showHidden = this.showHiddenFields,
            view = this.fieldsTree.getView();

        // collapse hidden nodes before hide them so that none-hidden children nodes of a hidden node won't stay visible
        this.fieldsTree.getRootNode().cascadeBy(function(node) {
            if (!showHidden && node.get('hidden')) {
                    node.collapse();
            }
        });
        // show hidden fields in fieldsTree
        this.fieldsTree.getRootNode().cascadeBy(function(node) {
            if (node.isRoot()) {
                return;
            }

            var hiddenField = node.get('hidden'),
                elem = Ext4.fly(view.getNode(node));

            if (elem) {
                if (showHidden) {
                    if (hiddenField) {
                        elem.setDisplayed('block');
                    }
                }
                else if (hiddenField) {
                    elem.setDisplayed('none');
                }
            }
            //else {
            //    console.warn('Unable to find the element for:', "'" + node.get('fieldKey') + "'");
            //}
        });

    },

    setShowHiddenFields : function(showHidden) {
        this.showHiddenFields = showHidden;

        this.showHideNodes();

        var tabs = this.getDesignerTabs();
        for (var i = 0; i < tabs.length; i++)
        {
            var tab = tabs[i];
            if (tab instanceof LABKEY.internal.ViewDesigner.tab.BaseTab) {
                tab.setShowHiddenFields(showHidden);
            }
        }
    },

    hasField : function(fieldKey) {
        var tab = this.getActiveDesignerTab();
        if (tab) {
            return tab.hasField(fieldKey);
        }
    },

    addRecord : function(fieldKey) {
        var tab = this.getActiveDesignerTab();
        if (tab) {
            return tab.addRecord(fieldKey);
        }
    },

    removeRecord : function(fieldKey) {
        var tab = this.getActiveDesignerTab();
        if (tab) {
            return tab.removeRecord(fieldKey);
        }
    },

    onCheckChange : function(node, checked) {
        if (checked) {
            //console.log('add:', '\'' + node.get('fieldKey') + '\'');
            this.addRecord(node.get('fieldKey'));
        }
        else {
            //console.log('remove:', '\'' + node.get('fieldKey') + '\'');
            this.removeRecord(node.get('fieldKey'));
        }
    },

    onTabChange : function() {
        this.configureChecked();
    },

    configureHidden : function() {
        this.showHideNodes();
    },

    configureChecked : function() {
        var tab = this.getActiveDesignerTab();
        if (tab instanceof LABKEY.internal.ViewDesigner.tab.BaseTab) {
            // get the checked fields from the new tab's store
            var columns = tab.getList().getStore().getRange(),
                checked = {},
                treeView = this.getColumnTree().getView(),
                nodeEl;

            for (var i = 0; i < columns.length; i++) {
                checked[columns[i].get('id')] = true;
            }

            this.getColumnTree().getRootNode().cascadeBy(function(node) {
                node.set('checked', node.internalId in checked);

                // yup, we have to manually do the disabled state ourselves! hooray!
                if (node.get('disabled') === true) {
                    nodeEl = treeView.getNode(node);
                    if (nodeEl) {
                        nodeEl = Ext4.get(nodeEl);

                        // disable the checkbox
                        Ext4.get(Ext4.DomQuery.select('input.x4-tree-checkbox', nodeEl.id)).set({disabled: ''});
                        Ext4.get(Ext4.DomQuery.select('span.x4-tree-node-text', nodeEl.id)).setStyle('color', 'gray');
                    }
                }
            }, this);
        }
    },

    onDeleteClick : function() {
        if (this.dataRegion) {
            var view = this.customView;

            // build title
            var title = 'Delete';
            if (view) {
                title += (view.shared ? ' shared' : ' your');
                title += (view.session ? ' unsaved' : '');
            }
            title += ' view';

            // build msg
            var msg = 'Are you sure you want to delete the';
            if (view) {
                if (view['default'] === true) {
                    msg += ' default';
                }
                else {
                    msg += " '<em>" + Ext4.htmlEncode(view.label) + "</em>'";
                }
            }
            msg += ' saved view';

            if (view && view.containerPath && view.containerPath != LABKEY.ActionURL.getContainer()) {
                msg += " from '" + view.containerPath + "'";
            }
            msg += '?';

            Ext4.Msg.confirm(title, msg, function(btn) {
                if (btn === 'yes') {
                    this._deleteCustomView(true);
                }
            }, this);
        }
        else {
            this._deleteCustomView(true, true);
        }
    },

    onRemoveColumn : function(id) {
        var node = this.getColumnTree().getStore().getNodeById(id);
        if (node) {
            node.set('checked', false);
        }
    },

    onRevertClick : function() {
        this._deleteCustomView(false, true);
    },

    _deleteCustomView : function(isDelete, reload) {
        this.fireEvent('beforedeleteview', this, !isDelete);

        LABKEY.Query.deleteQueryView({
            schemaName: this.schemaName,
            queryName: this.queryName,
            viewName: this.viewName,
            revert: !isDelete,
            success: function(json) {
                this.fireEvent('deleteview', true /* success */, json);
                if (reload === true) {
                    window.location.reload();
                }
            },
            failure: function(json) {
                this.fireEvent('deleteview', false /* success */, json);
            },
            scope: this
        });
    },

    onApplyClick : function() {
        // Save a session view. Session views can't be inherited or shared.
        this.save({
            name: this.customView.name,
            hidden: this.customView.hidden,
            shared: false,
            inherit: false,
            session: true
        });
    },

    onSaveClick : function() {
        var config = Ext4.applyIf({
            canEditSharedViews: this.query.canEditSharedViews,
            allowableContainerFilters: this.allowableContainerFilters,
            targetContainers: this.query.targetContainers,
            canEdit: this.getEditableErrors().length == 0,
            success: function(win, o) {
                this.save(o, function() {
                    win.close();
                    this.setVisible(false);
                }, this);
            },
            scope: this
        }, this.customView);

        LABKEY.internal.ViewDesigner.Designer.saveCustomizeViewPrompt(config);
    },

    revert : function() {
        var tabs = this.getDesignerTabs();
        for (var i = 0; i < tabs.length; i++)
        {
            var tab = tabs[i];
            if (tab instanceof LABKEY.internal.ViewDesigner.tab.BaseTab) {
                tab.revert();
            }
        }
    },

    validate : function() {
        var tabs = this.getDesignerTabs();
        for (var i = 0; i < tabs.length; i++)
        {
            var tab = tabs[i];
            if (tab instanceof LABKEY.internal.ViewDesigner.tab.BaseTab)
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

    save : function(properties, callback, scope) {
        if (this.fireEvent('beforeviewsave', this) !== false) {
            if (!this.validate()) {
                return false;
            }

            var edited = {},
                urlParameters = {},
                tabs = this.getDesignerTabs(),
                tab, i;

            for (i = 0; i < tabs.length; i++) {
                tab = tabs[i];
                if (tab instanceof LABKEY.internal.ViewDesigner.tab.BaseTab) {
                    tab.save(edited, urlParameters, properties);
                }
            }

            // include the custom view analyticsProviders (note: we don't currently have a way to edit them from the ViewDesigner
            edited.analyticsProviders = this.customView.analyticsProviders;

            Ext4.apply(edited, properties);

            LABKEY.Query.saveQueryViews({
                containerPath: this.containerPath,
                schemaName: this.schemaName,
                queryName: this.queryName,
                views: [ edited ],
                success: function(savedViewsInfo) {
                    if (Ext4.isFunction(callback)) {
                        callback.call(scope || this, savedViewsInfo, urlParameters);
                    }
                    this.fireEvent('viewsave', this, savedViewsInfo, urlParameters);
                },
                failure: function(errorInfo) {
                    Ext4.Msg.alert('Error saving grid view', errorInfo.exception);
                },
                scope: this
            });
        }
    },

    close : function() {
        if (this.dataRegion) {
            this.dataRegion.hideCustomizeView();
        }
        else {
            // If we're not attached to a grid, just remove from the DOM
            this.getEl().remove();
        }
    }
});