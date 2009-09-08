/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

    loadQueryDetails : function(schemaName, queryName) {
        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL('query', 'getQueryDetails.api'),
            method : 'GET',
            success: LABKEY.Utils.getCallbackWrapper(this.onQueryDetails, this),
            params: {
                schemaName: schemaName,
                queryName: queryName
            }
        });
    },

    clear : function(schemaName, queryName) {
        this.queryDetailsMap[getCacheKey(schemaName, queryName)] = undefined;
    },

    clearAll : function() {
        this.queryDetailsMap = {};
    },

    onQueryDetails : function(qdetails) {
        this.queryDetailsMap[this.getCacheKey(qdetails.schemaName, qdetails.name)] = qdetails;
        this.fireEvent("newdetails", qdetails);
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
        LABKEY.ext.QueryDetailsPanel.superclass.initComponent.apply(this, arguments);
    },
    
    setQueryDetails : function(queryDetails) {
        this.queryDetails = queryDetails;

        this.body.update(this.buildHtml(this.queryDetails));
    },

    buildHtml : function(queryDetails) {
        var html = "";

        html += this.buildLinks(queryDetails);

        html += "<div class='lk-qd-name'>" + queryDetails.schemaName + "." + queryDetails.name + "</div>";
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
        html += this.buildLink("query", "executeQuery", params, "view data", "_blank") + "&nbsp;";

        if (queryDetails.isUserDefined)
        {
            html += this.buildLink("query", "designQuery", params, "edit design") + "&nbsp;";
            html += this.buildLink("query", "sourceQuery", params, "edit source") + "&nbsp;";
            html += this.buildLink("query", "deleteQuery", params, "delete query") + "&nbsp;";
            html += this.buildLink("query", "propertiesQuery", params, "edit properties");
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

        var onclickScript = "Ext.ComponentMgr.get(\"" + this.id + "\").fireEvent(\"lookupclick\", \"" + col.lookup.schemaName + "\", \"" + col.lookup.queryName + "\");";
        return "<span class='labkey-link' onclick='" + onclickScript + "'>" + col.lookup.schemaName + "." + col.lookup.queryName + "</span>";
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

LABKEY.ext.SchemaBrowser = Ext.extend(Ext.Panel, {

    initComponent : function(){
        Ext.apply(this,{
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
                    xtype: 'labkey-query-details-panel',
                    region: 'center',
                    header: true,
                    autoScroll: true,
                    listeners: {
                        lookupclick: {
                            fn: this.onLookupClick,
                            scope: this
                        }
                    }
                }
            ],
           tbar: [
               {
                   text: 'Refresh',
                   handler: this.onRefresh,
                   scope: this
               },
               {
                   text: 'Schema Administration', //TODO: check for admin perms before adding this!
                   handler: this.onSchemaAdminClick,
                   scope: this
               }
           ]
        });

        Ext.applyIf(this, {
            autoResize: true
        });

        this.addEvents("schemasloaded");

        this._qdcache = new LABKEY.ext.QueryDetailsCache();
        this._qdcache.on("newdetails", this.onQueryDetails, this);

        LABKEY.ext.SchemaBrowser.superclass.initComponent.apply(this, arguments);
    },

    onTreeClick : function(node, evt) {
        if (!node.leaf)
            return;

        this.showQueryDetails(node.attributes.schemaName, node.text);
    },

    showQueryDetails : function(schemaName, queryName) {
        //get the query details and refresh the details view...
        var qdetails = this._qdcache.getQueryDetails(schemaName, queryName);
        if (qdetails)
            this.onQueryDetails(qdetails);
        else
            this._qdcache.loadQueryDetails(schemaName, queryName);

    },

    onQueryDetails : function(qdetails) {
        this.getComponent('details').setQueryDetails(qdetails);
    },

    onSchemaAdminClick : function() {
        window.location = LABKEY.ActionURL.buildURL("query", "admin");
    },

    onRefresh : function() {
        this._qdcache.clearAll();
        this.getComponent('tree').getRootNode().reload();
    },

    onLookupClick : function(schemaName, queryName) {
        this.selectQuery(schemaName, queryName);
    },

    selectSchema : function(schemaName) {
        var tree = this.getComponent("tree");
        var root = tree.getRootNode();
        var schemaNode = root.findChild("text", schemaName);
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
        var schemaNode = root.findChild("text", schemaName);
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
                queryNode = schemaNode.childNodes[0].findChild("text", queryName);
            if (!queryNode && schemaNode.childNodes.length > 1)
                queryNode = schemaNode.childNodes[1].findChild("text", queryName);

            if (!queryNode)
            {
                Ext.Msg.alert("Missing Query", "The query " + schemaName + "." + queryName + " was not found in the browser!");
                return;
            }

            tree.selectPath(queryNode.getPath());
            thisScope.showQueryDetails(schemaName, queryName);
        });
    }
});