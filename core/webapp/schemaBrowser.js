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

    getQueryDetails : function(schemaName, queryName) {
        return this.queryDetailsMap[this.getCacheKey(schemaName, queryName)];
    },

    loadQueryDetails : function(schemaName, queryName, callback, scope) {
        if (this.queryDetailsMap[this.getCacheKey(schemaName, queryName)])
        {
            if (callback)
                callback.call(scope || this, this.queryDetailsMap[this.getCacheKey(schemaName, queryName)]);
            return;
        }

        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL('query', 'getQueryDetails.api'),
            method : 'GET',
            success: function(response){
                var qdetails = Ext.util.JSON.decode(response.responseText);
                this.queryDetailsMap[this.getCacheKey(qdetails.schemaName, qdetails.name)] = qdetails;
                this.fireEvent("newdetails", qdetails);
                if (callback)
                    callback.call(scope || this, qdetails);
            },
            scope: this,
            params: {
                schemaName: schemaName,
                queryName: queryName
            }
        });
    },

    clear : function(schemaName, queryName) {
        this.queryDetailsMap[this.getCacheKey(schemaName, queryName)] = undefined;
    },

    clearAll : function() {
        this.queryDetailsMap = {};
    },

    getCacheKey : function(schemaName, queryName) {
        return schemaName + "." + queryName;
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
                expand: {
                    fn: function(){this.fireEvent("schemasloaded");},
                    scope: this
                }
            }
        });

        LABKEY.ext.QueryTreePanel.superclass.initComponent.apply(this, arguments);
    }
});

Ext.reg('labkey-query-tree-panel', LABKEY.ext.QueryTreePanel);

LABKEY.ext.QueryDetailsPanel = Ext.extend(Ext.Panel, {
    initComponent : function() {
        this.addEvents("lookupclick");
        this.bodyStyle = "padding: 5px";
        this.html = "<p class='lk-qd-loading'>Loading...</p>";
        LABKEY.ext.QueryDetailsPanel.superclass.initComponent.apply(this, arguments);
        this.cache.loadQueryDetails(this.schemaName, this.queryName, this.setQueryDetails, this);
    },
    
    setQueryDetails : function(queryDetails) {
        this.queryDetails = queryDetails;
        //if the details are already cached, we might not be rendered yet
        //in that case, set a delayed task so we have a chance to render first
        if (this.rendered)
            this.body.update(this.buildHtml(this.queryDetails));
        else
            new Ext.util.DelayedTask(function(){this.setQueryDetails(queryDetails)}, this).delay(10);
    },

    buildHtml : function(queryDetails) {
        var html = "";

        html += this.buildLinks(queryDetails);

        var viewDataHref = LABKEY.ActionURL.buildURL("query", "executeQuery", undefined, {schemaName:queryDetails.schemaName,"query.queryName":queryDetails.name});
        html += "<div class='lk-qd-name'><a href='" + viewDataHref + "' target='viewData'>" + queryDetails.schemaName + "." + queryDetails.name + "</a></div>";
        if (queryDetails.description)
            html += "<div class='lk-qd-description'>" + queryDetails.description + "</div>";

        //columns table
        html += "<table class='lk-qd-coltable'>";
        //header row
        html += "<tr>";
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
            qtip = "When writing LabKey SQL, these columns are available from this query.";
            html += "<tr><td colspan='6' class='lk-qd-collist-title' ext:qtip='" + qtip + "'>All Columns in this Query</td></tr>";
            html += this.buildColumnTableRows(queryDetails.columns);
        }

        if (queryDetails.defaultView && queryDetails.defaultView.columns)
        {
            qtip = "When using the LABKEY.Query.selectRows() API, these columns will be returned by default.";
            html += "<tr><td colspan='6'>&nbsp;</td></tr>";
            html += "<tr><td colspan='6' class='lk-qd-collist-title' ext:qtip='" + qtip + "'>Columns in Your Default View of this Query</td></tr>";
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
            html += "<td>" + col.name + "</td>";
            html += "<td>" + col.caption + "</td>";
            html += "<td>" + col.type + "</td>";
            html += "<td>" + this.getLookupLink(col) + "</td>";
            html += "<td>" + this.getColAttrs(col) + "</td>";
            html += "<td>" + (col.description ? col.description : "") + "</td>";
            html += "</tr>";
        }
        return html;
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


        var onclickScript = "Ext.ComponentMgr.get(\"" + this.id + "\").fireEvent(\"lookupclick\", \"" + col.lookup.schemaName + "\", \"" + col.lookup.queryName + "\");";

        var html = "<span ext:qtip=\"" + tipText + "\"";
        if (col.lookup.isPublic)
            html += " class='labkey-link' onclick='" + onclickScript + "'";
        
        html += ">" + caption + "</span>";
        return html;
    },

    getColAttrs : function(col) {
        var html = "";

        if (col.isAutoIncrement)
            html = this.appendColAttr("Auto-Increment", html);

        if (col.isKeyField)
            html = this.appendColAttr("Primary Key", html);

        if (col.isMvEnabled)
            html = this.appendColAttr("MV-Enabled", html);

        if (false === col.isNullable)
            html = this.appendColAttr("Required", html);

        if (col.isReadOnly || col.isUserEditable)
            html = this.appendColAttr("Read-Only", html);

        if (col.isVersionField)
            html = this.appendColAttr("Version", html);

        return html;
    },

    appendColAttr : function(attr, html) {
        if (html.length > 0)
            return html + ", " + attr;
        else
            return attr;
    }

});

Ext.reg('labkey-query-details-panel', LABKEY.ext.QueryDetailsPanel);

LABKEY.requiresCss("_images/icons.css");

LABKEY.ext.SchemaBrowser = Ext.extend(Ext.Panel, {

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
                    listeners: {
                        click: {
                            fn: this.onTreeClick,
                            scope: this
                        },
                        schemasloaded: {
                            fn: function(){this.fireEvent("schemasloaded", this);},
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
                            title: 'Home',
                            html: '<p style="padding: 10px">Choose a query on the left to see information about it.</p>'
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
        var id = schemaName + "." + queryName;
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
                title: id,
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

    onLookupClick : function(schemaName, queryName) {
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
                Ext.Msg.alert("Missing Query", "The query " + schemaName + "." + queryName + " was not found! It may not be publicly accessible.");
                return;
            }

            tree.selectPath(queryNode.getPath());
            thisScope.showQueryDetails(schemaName, queryName);
        });
    }
});