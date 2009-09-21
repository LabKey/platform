/*
 * Copyright (c) 2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext.namespace('LABKEY', 'LABKEY.ext');

LABKEY.ext.QueryDetailsCache = Ext.extend(Ext.util.Observable, {
    constructor : function(config) {
        this.addEvents("newdetails");
        LABKEY.ext.QueryDetailsCache.superclass.constructor.apply(this, arguments);
        this.queryDetailsMap = {};
    },

    getQueryDetails : function(schemaName, queryName, fk) {
        return this.queryDetailsMap[this.getCacheKey(schemaName, queryName, fk)];
    },

    loadQueryDetails : function(schemaName, queryName, fk, callback, errorCallback, scope) {
        if (this.queryDetailsMap[this.getCacheKey(schemaName, queryName, fk)])
        {
            if (callback)
                callback.call(scope || this, this.queryDetailsMap[this.getCacheKey(schemaName, queryName, fk)]);
            return;
        }

        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL('query', 'getQueryDetails.api'),
            method : 'GET',
            success: function(response){
                var qdetails = Ext.util.JSON.decode(response.responseText);
                this.queryDetailsMap[this.getCacheKey(schemaName, queryName, fk)] = qdetails;
                this.fireEvent("newdetails", qdetails);
                if (callback)
                    callback.call(scope || this, qdetails);
            },
            failure: LABKEY.Utils.getCallbackWrapper(errorCallback, (scope || this), true),
            scope: this,
            params: {
                schemaName: schemaName,
                queryName: queryName,
                fk: fk
            }
        });
    },

    clear : function(schemaName, queryName, fk) {
        this.queryDetailsMap[this.getCacheKey(schemaName, queryName, fk)] = undefined;
    },

    clearAll : function() {
        this.queryDetailsMap = {};
    },

    getCacheKey : function(schemaName, queryName, fk) {
        return schemaName + "." + queryName + (fk ? "." + fk : "");
    }

});

LABKEY.ext.QueryTreePanel = Ext.extend(Ext.tree.TreePanel, {
    initComponent : function(){
        this.addEvents("schemasloaded");
        this.dataUrl = LABKEY.ActionURL.buildURL("query", "getSchemaQueryTree.api");
        this.root = new Ext.tree.AsyncTreeNode({
            id: 'root',
            text: 'Schemas',
            expanded: true,
            expandable: false,
            draggable: false,
            listeners: {
                load: {
                    fn: function(node){
                        this.fireEvent("schemasloaded", node.childNodes);
                    },
                    scope: this
                }
            }
        });

        LABKEY.ext.QueryTreePanel.superclass.initComponent.apply(this, arguments);
    }
});

Ext.reg('labkey-query-tree-panel', LABKEY.ext.QueryTreePanel);

LABKEY.ext.QueryDetailsPanel = Ext.extend(Ext.Panel, {
    colspan : 7, //number of columns in the column details table (used for cells that need to span the whole row)

    initComponent : function() {
        this.addEvents("lookupclick");
        this.bodyStyle = "padding: 5px";
        this.html = "<p class='lk-qd-loading'>Loading...</p>";

        LABKEY.ext.QueryDetailsPanel.superclass.initComponent.apply(this, arguments);

        this.cache.loadQueryDetails(this.schemaName, this.queryName, this.fk, this.setQueryDetails, function(errorInfo){
            var html = "<p class='lk-qd-error'>Error in query: " + errorInfo.exception + "</p>";
            this.getEl().update(html);
        }, this);
    },
    
    setQueryDetails : function(queryDetails) {
        this.queryDetails = queryDetails;
        //if the details are already cached, we might not be rendered yet
        //in that case, set a delayed task so we have a chance to render first
        if (this.rendered)
            this.body.update(this.buildHtml(this.queryDetails));
        else
            new Ext.util.DelayedTask(function(){this.setQueryDetails(queryDetails);}, this).delay(10);
    },

    buildHtml : function(queryDetails) {
        var html = "";

        html += this.buildLinks(queryDetails);

        var viewDataHref = LABKEY.ActionURL.buildURL("query", "executeQuery", undefined, {schemaName:queryDetails.schemaName,"query.queryName":queryDetails.name});
        html += "<div class='lk-qd-name'><a href='" + viewDataHref + "' target='viewData'>" + queryDetails.schemaName + "." + queryDetails.name + "</a></div>";
        if (queryDetails.description)
            html += "<div class='lk-qd-description'>" + queryDetails.description + "</div>";

        if (queryDetails.exception)
        {
            html += "<div class='lk-qd-error'>There was an error while parsing this query: " + queryDetails.exception + "</div>";
            return html;
        }
        
        //columns table
        html += this.buildColumnsTable(queryDetails);

        return html;
    },

    buildColumnsTable : function(queryDetails) {
        //columns table
        var html = "<table class='lk-qd-coltable'>";
        //header row
        html += "<tr>";
        html += "<th></th>";
        html += "<th ext:qtip='This is the programmatic name used in the API and LabKey SQL.'>Name</th>";
        html += "<th ext:qtip='This is the caption the user sees in views.'>Caption</th>";
        html += "<th ext:qtip='The data type of the column.'>Type</th>";
        html += "<th ext:qtip='If this column is a foreign key (lookup), the query it joins to.'>Lookup</th>";
        html += "<th ext:qtip='Miscellaneous info about the column.'>Attributes</th>";
        html += "<th>Description</th>";
        html += "</tr>";

        var qtip;
        if (queryDetails.columns)
        {
            //don't show group heading if there is only one column group
            if (queryDetails.defaultView)
            {
                qtip = "When writing LabKey SQL, these columns are available from this query.";
                html += "<tr><td colspan='" + this.colspan + "' class='lk-qd-collist-title' ext:qtip='" + qtip + "'>All Columns in this Query</td></tr>";
            }
            html += this.buildColumnTableRows(queryDetails.columns);
        }

        if (queryDetails.defaultView && queryDetails.defaultView.columns)
        {
            if (queryDetails.columns)
            {
                qtip = "When using the LABKEY.Query.selectRows() API, these columns will be returned by default.";
                html += "<tr><td colspan='" + this.colspan + "'>&nbsp;</td></tr>";
                html += "<tr><td colspan='" + this.colspan + "' class='lk-qd-collist-title' ext:qtip='" + qtip + "'>Columns in Your Default View of this Query</td></tr>";
            }
            html += this.buildColumnTableRows(queryDetails.defaultView.columns);
        }

        //close the columns table
        html += "</table>";
        return html;
    },

    buildLinks : function(queryDetails) {
        var params = {schemaName: queryDetails.schemaName};
        params["query.queryName"] = queryDetails.name;

        var html = "<div class='lk-qd-links'>";
        html += this.buildLink("query", "executeQuery", params, "view data", "viewData") + "&nbsp;";

        if (queryDetails.isUserDefined && LABKEY.Security.currentUser.isAdmin)
        {
            html += this.buildLink("query", "designQuery", params, "edit design") + "&nbsp;";
            html += this.buildLink("query", "sourceQuery", params, "edit source") + "&nbsp;";
            html += this.buildLink("query", "deleteQuery", params, "delete query") + "&nbsp;";
            html += this.buildLink("query", "propertiesQuery", params, "edit properties");
        }
        else
        {
            html += this.buildLink("query", "metadataQuery", params, "customize display");
        }

        html += "</div>";
        return html;
    },

    buildLink : function(controller, action, params, caption, target) {
        var link = Ext.util.Format.htmlEncode(LABKEY.ActionURL.buildURL(controller, action, null, params));
        return "[<a" + (target ? " target=\"" + target + "\"" : "") + " href=\"" + link + "\">" + caption + "</a>]";
    },

    buildColumnTableRows : function(columns) {
        var html = "";
        for (var idx = 0; idx < columns.length; ++idx)
        {
            var col = columns[idx];

            html += "<tr class='lk-qd-coltablerow'>";
            html += "<td>" + this.getColExpander(col) + "</td>";
            html += "<td>" + col.name + "</td>";
            html += "<td>" + col.caption + "</td>";
            html += "<td>" + (col.isSelectable ? col.type : "") + "</td>";
            html += "<td>" + this.getLookupLink(col) + "</td>";
            html += "<td>" + this.getColAttrs(col) + "</td>";
            html += "<td>" + (col.description ? col.description : "") + "</td>";
            html += "</tr>";
        }
        return html;
    },

    getColExpander : function(col) {
        if (!col.lookup)
            return "";

        var expandScript = "Ext.ComponentMgr.get(\"" + this.id + "\").onLookupExpand(\"" + col.name + "\", this);";
        return "<img src='" + LABKEY.ActionURL.getContextPath() + "/_images/plus.gif' onclick='" + expandScript + "'/>&nbsp;";
    },

    getLookupLink : function(col) {
        if (!col.lookup)
            return "";

        var tipText = "This column is a lookup to " + col.lookup.schemaName + "." + col.lookup.queryName;
        var caption = col.lookup.schemaName + "." + col.lookup.queryName;
        if (col.lookup.keyColumn)
        {
            caption += "." + col.lookup.keyColumn;
            tipText += " joining to the column " + col.lookup.keyColumn;
        }
        if (col.lookup.displayColumn)
        {
            caption += " (" + col.lookup.displayColumn + ")";
            tipText += " (the value from column " + col.lookup.displayColumn + " is usually displayed in grids)";
        }
        tipText += ". To reference columns in the lookup table, use the syntax '" + col.name + "/col-in-lookup-table'.";

        if (!col.lookup.isPublic)
            tipText += " Note that the lookup table is not publicly-available via the APIs.";

        if (col.lookup.containerPath)
            tipText += " Note that the lookup table is defined in the folder '" + col.lookup.containerPath + "'.";

        var html = "";

        //script for click on lookup link
        var argList = "\"" + col.lookup.schemaName + "\", \"" + col.lookup.queryName + "\"";
        if (col.lookup.containerPath)
            argList += ", \"" + col.lookup.containerPath + "\"";
        var onclickScript = "Ext.ComponentMgr.get(\"" + this.id + "\").fireEvent(\"lookupclick\", " + argList + ");";

        html += "<span ext:qtip=\"" + tipText + "\"";
        if (col.lookup.isPublic)
            html += " class='labkey-link' onclick='" + onclickScript + "'";
        
        html += ">" + caption + "</span>";
        return html;
    },

    onLookupExpand : function(colName, expandImage) {
        var img = Ext.get(expandImage);
        var tr = img.findParentNode("tr", undefined, true);
        if (!tr)
            throw "Couldn't find table row containing expander image!";

        tr.addClass("lk-qd-colrow-expanded");

        var trNew = tr.insertHtml("afterEnd", "<tr></tr>", true);
        var tdNew = trNew.insertHtml("beforeEnd", "<td colspan='" + this.colspan + "' class='lk-qd-nested-container'><span class='lk-qd-loading'>loading...</span></td>", true);
        img.set({
            src: LABKEY.ActionURL.getContextPath() + "/_images/minus.gif",
            onclick: "Ext.ComponentMgr.get(\"" + this.id + "\").toggleLookupRow('" + trNew.id + "', this);"
        });

        //load query details for this schema + query + colName (fk)
        this.cache.loadQueryDetails(this.schemaName, this.queryName, colName, function(queryDetails){
            //build html
            var html = this.buildColumnsTable(queryDetails);
            tdNew.update(html);
        }, function(errorInfo){
            tdNew.update("<p class='lk-qd-error'>" + errorInfo.exception + "</p>");
        },this);
    },

    toggleLookupRow : function(trId, expandImage) {
        var tr = Ext.get(trId);

        tr.setDisplayed(!tr.isDisplayed());
        if (tr.isDisplayed())
        {
            tr.prev("tr").addClass("lk-qd-colrow-expanded");
            Ext.get(expandImage).set({
                src: LABKEY.ActionURL.getContextPath() + "/_images/minus.gif"
            });
        }
        else
        {
            tr.prev("tr").removeClass("lk-qd-colrow-expanded");
            Ext.get(expandImage).set({
                src: LABKEY.ActionURL.getContextPath() + "/_images/plus.gif"
            });
        }
    },

    attrMap : {
        isSelectable: {
            abbreviation: 'U',
            label: 'Unselectable',
            description: 'This column is not selectable directly, but it may be used to access other columns in the lookup table it points to.',
            negate: true,
            trump: true
        },
        isAutoIncrement: {
            abbreviation: 'AI',
            label: 'Auto-Increment',
            description: 'This value for this column is automatically assigned to an incrememnting integer value by the server.'
        },
        isKeyField: {
            abbreviation: 'PK',
            label: 'Primary Key',
            description: 'This column is the primary key for the table (or part of a compound primary key).'
        },
        isMvEnabled: {
            abbreviation: 'MV',
            label: 'MV-Enabled',
            description: 'This column has a related column that stores missing-value information.'
        },
        isNullable: {
            abbreviation: 'Req',
            label: 'Required',
            description: 'This column is required.',
            negate: true
        },
        isReadOnly: {
            abbreviation: 'RO',
            label: 'Read-Only',
            description: 'This column is read-only.'
        },
        isVersionField: {
            abbreviation: 'V',
            label: 'Version',
            description: 'This column contains a version number for the row.'
        }
    },

    getColAttrs : function(col) {
        var attrs = {};
        for (var attrName in this.attrMap)
        {
            var attr = this.attrMap[attrName];
            if (attr.negate ? !col[attrName] : col[attrName])
            {
                if (attr.trump)
                    return this.formatAttr(attr);
                attrs[attrName] = attr;
            }
        }

        var html = "";
        for (attrName in attrs)
        {
            html += this.formatAttr(attrs[attrName], html);
        }

        return html;
    },

    formatAttr : function(attr, html) {
        var fmtAttr = "<span ext:qtip='" + attr.label + ": " + attr.description + "'>" + attr.abbreviation + "</span>";
        return (html && html.length > 0) ? ", " + fmtAttr : fmtAttr;
    }
});

Ext.reg('labkey-query-details-panel', LABKEY.ext.QueryDetailsPanel);

LABKEY.ext.SchemaBrowserHomePanel = Ext.extend(Ext.Panel, {
    initComponent : function() {
        this.bodyStyle = "padding: 5px";
        if (this.schemaBrowser)
            this.schemaBrowser.on("schemasloaded", function(browser, schemaNodes){
                this.setSchemas(schemaNodes);
            }, this);
        LABKEY.ext.SchemaBrowserHomePanel.superclass.initComponent.apply(this, arguments);
    },

    onRender : function() {
        //call superclass to create basic elements
        LABKEY.ext.SchemaBrowserHomePanel.superclass.onRender.apply(this, arguments);
        this.body.createChild({
            tag: 'div',
            cls: 'lk-qd-loading',
            html: 'loading...'
        });
    },

    setSchemas : function(schemaNodes) {
        this.schemas = schemaNodes;

        this.body.update("");

        this.body.createChild({
            tag: 'div',
            cls: 'lk-sb-instructions',
            html: 'Use the tree on the left to select a query, or select a schema below to expand that schema in the tree.'
        });

        var table = this.body.createChild({
            tag: 'table',
            cls: 'lk-qd-coltable',
            children: [
                {
                    tag: 'thead',
                    children: [
                        {
                            tag: 'tr',
                            children: [
                                {
                                    tag: 'th',
                                    html: 'Name'
                                },
                                {
                                    tag: 'th',
                                    html: 'Description'
                                }

                            ]
                        }
                    ]
                }
            ]
        });
        var tbody = table.createChild({
            tag: 'tbody'
        });

        for (var idx = 0; idx < schemaNodes.length; ++idx)
        {
            var schemaNode = schemaNodes[idx];

            var tr = tbody.createChild({
                tag: 'tr'
            });
            var tdName = tr.createChild({
                tag: 'td'
            });
            var spanName = tdName.createChild({
                tag: 'span',
                cls: 'labkey-link',
                html: schemaNode.attributes.schemaName
            });
            spanName.on("click", function(evt, t){
                if (this.schemaBrowser)
                    this.schemaBrowser.selectSchema(t.innerHTML);
            }, this);

            tr.createChild({
                tag: 'td',
                html: schemaNode.attributes.description
            });
        }
    }
});

Ext.reg('labkey-schema-browser-home-panel', LABKEY.ext.SchemaBrowserHomePanel);

LABKEY.requiresCss("_images/icons.css");

LABKEY.ext.SchemaBrowser = Ext.extend(Ext.Panel, {

    qdpPrefix: 'qdp-',

    initComponent : function(){
        var tbar = [
            {
                text: 'Refresh',
                handler: this.onRefresh,
                scope: this,
                iconCls:'iconReload',
                tooltip: 'Refreshes the tree of schemas and queries, or a particular schema if one is selected.'
            },
        ];

        if (LABKEY.Security.currentUser.isAdmin)
        {
            tbar.push({
                text: 'Define External Schemas',
                handler: this.onSchemaAdminClick,
                scope: this,
                iconCls: 'iconFolderNew',
                tooltip: 'Create or modify external schemas.'
            });
            tbar.push({
                text: 'Create New Query',
                handler: this.onCreateQueryClick,
                scope: this,
                iconCls: 'iconFileNew',
                tooltip: 'Create a new query in the selected schema (requires that you select a particular schema or query within that schema).'
            });
            tbar.push({
                text: 'Validate Queries',
                handler: function(){window.open(this.getValidateQueriesUrl(), "validateQueries");},
                scope: this,
                iconCls: 'iconCheck',
                tooltip: 'Takes you to the validate queries page where you can validate all the queries defined in this folder.'
            });
        }

        Ext.apply(this,{
            _qdcache: new LABKEY.ext.QueryDetailsCache(),
            layout: 'border',
            items : [
                {
                    id: 'tree',
                    xtype: 'labkey-query-tree-panel',
                    region: 'west',
                    split: true,
                    width: 200,
                    autoScroll: true,
                    enableDrag: false,
                    useArrows: true,
                    listeners: {
                        click: {
                            fn: this.onTreeClick,
                            scope: this
                        },
                        schemasloaded: {
                            fn: function(schemaNodes){
                                this.fireEvent("schemasloaded", this, schemaNodes);
                            },
                            scope: this
                        }
                    }
                },
                {
                    id: 'details',
                    xtype: 'tabpanel',
                    region: 'center',
                    activeTab: 0,
                    items: [
                        {
                            xtype: 'labkey-schema-browser-home-panel',
                            title: 'Home',
                            schemaBrowser: this,
                            id: 'lk-sb-panel-home'
                        }
                    ],
                    enableTabScroll:true,
                    defaults: {autoScroll:true},
                    listeners: {
                        tabchange: {
                            fn: this.onTabChange,
                            scope: this
                        }
                    }
                }
            ],
           tbar: tbar
        });

        Ext.applyIf(this, {
            autoResize: true
        });

        if (this.autoResize)
        {
            Ext.EventManager.onWindowResize(function(w,h){this.resizeToViewport(w,h);}, this);
            this.on("render", function(){Ext.EventManager.fireWindowResize();}, this);
        }

        this.addEvents("schemasloaded");

        LABKEY.ext.SchemaBrowser.superclass.initComponent.apply(this, arguments);
        if (this.useHistory)
        {
            Ext.History.init();
            Ext.History.on('change', this.onHistoryChange, this);
        }
    },

    onHistoryChange : function(token) {
        if (!token)
        {
            //back to home panel
            this.getComponent("details").activate("lk-sb-panel-home");
        }
        else
        {
            var tokenMap = this.parseHistoryToken(token);
            if (tokenMap)
                this.selectQuery(tokenMap.schemaName, tokenMap.queryName);
        }
    },

    getHistoryToken : function(schemaName, queryName) {
        return encodeURIComponent(schemaName) + "&" + encodeURIComponent(queryName);
    },

    parseHistoryToken : function(token) {
        var parts = token.split('&');
        if (parts.length == 2)
            return {schemaName: decodeURIComponent(parts[0]), queryName: decodeURIComponent(parts[1])};
        else
            return null;
    },

    resizeToViewport : function(w,h) {
        if (!this.rendered)
            return;

        var padding = [20,20];
        var xy = this.el.getXY();
        var size = {
            width : Math.max(100,w-xy[0]-padding[0]),
            height : Math.max(100,h-xy[1]-padding[1])};
        this.setSize(size);
    },

    onCreateQueryClick : function() {
        //determine which schema is selected in the tree
        var tree = this.getComponent("tree");
        var node = tree.getSelectionModel().getSelectedNode();
        if (node && node.attributes.schemaName)
            window.open(this.getCreateQueryUrl(node.attributes.schemaName), "createQuery");
        else
            Ext.Msg.alert("Which Schema?", "Please select the schema in which you want to create the new query.");

    },

    getCreateQueryUrl : function(schemaName) {
        return LABKEY.ActionURL.buildURL("query", "newQuery", undefined, {schemaName: schemaName});
    },

    getValidateQueriesUrl : function() {
        return LABKEY.ActionURL.buildURL("query", "validateQueries");
    },

    onTabChange : function(tabpanel, tab) {
        if (tab.queryDetails)
            this.selectQuery(tab.queryDetails.schemaName, tab.queryDetails.name);
    },

    onTreeClick : function(node, evt) {
        if (!node.leaf)
            return;

        this.showQueryDetails(node.attributes.schemaName, node.text);
    },

    showQueryDetails : function(schemaName, queryName) {
        var id = this.qdpPrefix + schemaName + "." + queryName;
        var tabs = this.getComponent('details');
        if (tabs.getComponent(id))
            tabs.activate(id);
        else
        {
            var qdetailsPanel = new LABKEY.ext.QueryDetailsPanel({
                cache: this._qdcache,
                schemaName: schemaName,
                queryName: queryName,
                id: id,
                title: schemaName + "." + queryName,
                autoScroll: true,
                listeners: {
                    lookupclick: {
                        fn: this.onLookupClick,
                        scope: this
                    }
                },
                closable: true
            });
            tabs.add(qdetailsPanel).show();
        }
        Ext.History.add(this.getHistoryToken(schemaName, queryName));
    },

    onSchemaAdminClick : function() {
        window.location = LABKEY.ActionURL.buildURL("query", "admin");
    },

    onRefresh : function() {
        //clear the query details cache
        this._qdcache.clearAll();

        //remove all tabs except for the first one (home)
        var tabs = this.getComponent("details");
        while (tabs.items.length > 1)
        {
            tabs.remove(tabs.items.length - 1, true);
        }

        //if tree selection is below a schema, refresh only that schema
        var tree = this.getComponent("tree");
        var nodeToReload = tree.getRootNode();
        var nodeSelected = tree.getSelectionModel().getSelectedNode();

        if (nodeSelected && nodeSelected.attributes.schemaName)
        {
            var schemaToFind = nodeSelected.attributes.schemaName.toLowerCase();
            var foundNode = tree.getRootNode().findChildBy(function(node){
                return node.attributes.schemaName && node.attributes.schemaName.toLowerCase() == schemaToFind;
            });
            if (foundNode)
                nodeToReload = foundNode;
        }

        nodeToReload.reload();
    },

    onLookupClick : function(schemaName, queryName, containerPath) {
        if (containerPath && containerPath != LABKEY.ActionURL.getContainer())
            window.open(LABKEY.ActionURL.buildURL("query", "begin", containerPath, {schemaName: schemaName, queryName: queryName}));
        else
            this.selectQuery(schemaName, queryName);
    },

    selectSchema : function(schemaName) {
        var tree = this.getComponent("tree");
        var root = tree.getRootNode();
        var schemaToFind = schemaName.toLowerCase();
        var schemaNode = root.findChildBy(function(node){
            return node.attributes.schemaName && node.attributes.schemaName.toLowerCase() == schemaToFind;
        });
        if (schemaNode)
        {
            tree.selectPath(schemaNode.getPath());
            schemaNode.expand(false, false);
        }
        else
            Ext.Msg.alert("Missing Schema", "The schema name " + schemaName + " was not found in the browser!");

    },

    selectQuery : function(schemaName, queryName) {
        var tree = this.getComponent("tree");
        var root = tree.getRootNode();
        var schemaNode = root.findChildBy(function(node){return node.attributes.schemaName.toLowerCase() == schemaName.toLowerCase();});
        if (!schemaNode)
        {
            Ext.Msg.alert("Missing Schema", "The schema name " + schemaName + " was not found in the browser!");
            return;
        }

        //Ext 2.2 doesn't have a scope param on the expand() method
        var thisScope = this;
        schemaNode.expand(false, false, function(schemaNode){
            //look for the query node under both built-in and user-defined
            var queryNode;
            if (schemaNode.childNodes.length > 0)
                queryNode = schemaNode.childNodes[0].findChildBy(function(node){return node.text.toLowerCase() == queryName.toLowerCase();});
            if (!queryNode && schemaNode.childNodes.length > 1)
                queryNode = schemaNode.childNodes[1].findChildBy(function(node){return node.text.toLowerCase() == queryName.toLowerCase();});

            if (!queryNode)
            {
                Ext.Msg.alert("Missing Query", "The query " + schemaName + "." + queryName + " was not found! It may not be publicly accessible." +
                        " You can expand the field to see the columns in the related table.");
                return;
            }

            tree.selectPath(queryNode.getPath());
            thisScope.showQueryDetails(schemaName, queryName);
        });
    }
});