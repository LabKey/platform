/*
 * Copyright (c) 2009-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext.namespace('LABKEY', 'LABKEY.ext');

Ext.Ajax.timeout = 60000;

LABKEY.ext.QueryCache = Ext.extend(Ext.util.Observable, {
    constructor : function(config) {
        LABKEY.ext.QueryCache.superclass.constructor.apply(this, arguments);
    },

    getSchemas : function(callback, scope) {
        if (this.schemasMap)
        {
            if (callback)
                callback.call(scope || this, this.schemasMap);
            return;
        }

        LABKEY.Query.getSchemas({
            apiVersion: 9.3,
            successCallback: function(schemasMap) {
                this.schemasMap = schemasMap;
                this.getSchemas(callback, scope);
            },
            scope: this
        });
    },

    getSchema : function(schemaName, callback, scope) {
        if (!callback)
            return this.schemasMap ? this.schemasMap[schemaName] : null;

        this.getSchemas(function(schemasMap){
            callback.call(scope || this, schemasMap[schemaName]);
        }, this);
    },

    getQueries : function(schemaName, callback, scope) {
        if (!this.schemasMap)
        {
            this.getSchemas(function(){
                this.getQueries(schemaName, callback, scope);
            }, this);
            return;
        }

        var schema = this.schemasMap[schemaName];
        if (!schema)
            throw "schema name '" + schemaName + "' does not exist!";

        if (schema.queriesMap)
        {
            callback.call(scope || this, schema.queriesMap);
            return;
        }

        LABKEY.Query.getQueries({
            schemaName: schemaName,
            includeColumns: false,
            includeUserQueries: true,
            successCallback: function(data){
                var schema = this.schemasMap[schemaName];
                schema.queriesMap = {};
                var query;
                for (var idx = 0; idx < data.queries.length; ++idx)
                {
                    query = data.queries[idx];
                    schema.queriesMap[query.name] = query;
                }
                this.getQueries(schemaName, callback, scope);
            },
            scope: this
        });
    },

    clearAll : function()
    {
        delete this.schemasMap;
    }
});

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

        this.rootVisible = false;
        Ext.EventManager.on(window, "beforeunload", function(evt){
            this._unloading = true
        }, this);

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
                        try {
                            this.fireEvent("schemasloaded", node.childNodes);
                        } catch(ignore) {}
                    },
                    scope: this
                }
            }
        });

        LABKEY.ext.QueryTreePanel.superclass.initComponent.apply(this, arguments);
        this.addEvents("schemasloaded");
        this.getLoader().on("loadexception", function(loader, node, response){
            if (!this._unloading)
                LABKEY.Utils.displayAjaxErrorResponse(response);
        }, this);
    }
});

Ext.reg('labkey-query-tree-panel', LABKEY.ext.QueryTreePanel);

LABKEY.ext.QueryDetailsPanel = Ext.extend(Ext.Panel, {

    domProps : {
        schemName: 'lkqdSchemaName',
        queryName: 'lkqdQueryName',
        containerPath: 'lkqdContainerPath',
        fieldKey: 'lkqdFieldKey'
    },

    initComponent : function() {
        this.bodyStyle = "padding: 5px";

        LABKEY.ext.QueryDetailsPanel.superclass.initComponent.apply(this, arguments);

        this.cache.loadQueryDetails(this.schemaName, this.queryName, this.fk, this.setQueryDetails, function(errorInfo){
            var html = "<p class='lk-qd-error'>Error in query: " + errorInfo.exception + "</p>";
            this.getEl().update(html);
        }, this);
        this.addEvents("lookupclick");
    },

    onRender : function() {
        LABKEY.ext.QueryDetailsPanel.superclass.onRender.apply(this, arguments);
        this.body.createChild({
            tag: 'p',
            cls: 'lk-qd-loading',
            html: 'Loading...'
        });
    },

    setQueryDetails : function(queryDetails) {
        this.queryDetails = queryDetails;
        if (this.rendered)
            this.renderQueryDetails();
        else
        {
            new Ext.util.DelayedTask(function(){
                this.renderQueryDetails();
            }, this).delay(100);
        }
    },

    renderQueryDetails : function() {
        var elemDef = this.formatQueryDetails(this.queryDetails);
        this.body.update("");
        var container = this.body.createChild(elemDef);
        this.registerEventHandlers(container);
    },

    registerEventHandlers : function(containerEl) {
        //register for events on lookup links and expandos
        var lookupLinks = containerEl.query("table tr td span[class='labkey-link']");
        for (var idx = 0; idx < lookupLinks.length; ++idx)
        {
            var link = Ext.get(lookupLinks[idx]);
            link.on("click", this.getLookupLinkClickFn(link), this);
        }

        var expandos = containerEl.query("table tr td img[class='lk-qd-expando']");
        for (idx = 0; idx < expandos.length; ++idx)
        {
            var expando = Ext.get(expandos[idx]);
            expando.on("click", this.getExpandoClickFn(expando), this);
        }

    },

    getExpandoClickFn : function(expando) {
        return function() {
            this.toggleLookupRow(expando);
        };
    },

    getLookupLinkClickFn : function(lookupLink) {
        return function() {
            this.fireEvent("lookupclick",
                    Ext.util.Format.htmlDecode(lookupLink.getAttributeNS('', this.domProps.schemaName)),
                    Ext.util.Format.htmlDecode(lookupLink.getAttributeNS('', this.domProps.queryName)),
                    Ext.util.Format.htmlDecode(lookupLink.getAttributeNS('', this.domProps.containerPath)));
        };
    },

    formatQueryDetails : function(queryDetails) {
        var root = {tag: 'div', children: []};
        root.children.push(this.formatQueryLinks(queryDetails));
        root.children.push(this.formatQueryInfo(queryDetails));
        if (queryDetails.exception)
            root.children.push(this.formatQueryException(queryDetails));
        else
            root.children.push(this.formatQueryColumns(queryDetails));
        return root;
    },

    formatQueryLinks : function(queryDetails) {
        var container = {tag: 'div', cls: 'lk-qd-links', children:[]};

        if (queryDetails.isInherited)
            container.children.push(this.formatJumpToDefinitionLink(queryDetails));

        var params = {schemaName: queryDetails.schemaName};
        params["query.queryName"] = queryDetails.name;

        container.children.push(this.formatQueryLink("executeQuery", params, "view data", undefined, queryDetails.viewDataUrl));

        if (queryDetails.isUserDefined)
        {
            if (queryDetails.canEdit && !queryDetails.isInherited)
            {
                if (LABKEY.Security.currentUser.isAdmin)
                {
                    container.children.push(this.formatQueryLink("designQuery", params, "edit design"));
                    container.children.push(this.formatQueryLink("sourceQuery", params, "edit source"));
                    container.children.push(this.formatQueryLink("propertiesQuery", params, "edit properties"));
                    container.children.push(this.formatQueryLink("deleteQuery", params, "delete query"));
                }
                container.children.push(this.formatQueryLink("metadataQuery", params, "edit metadata"));
            }
            else
                container.children.push(this.formatQueryLink("viewQuerySource", params, "view source"));
        }
        else if(queryDetails.isMetadataOverrideable && LABKEY.Security.currentUser.isAdmin)
            container.children.push(this.formatQueryLink("metadataQuery", params, "edit metadata"));
        
        return container;
    },

    formatJumpToDefinitionLink : function(queryDetails) {
        var url = LABKEY.ActionURL.buildURL("query", "begin", queryDetails.containerPath, {
            schemaName: queryDetails.schemaName,
            queryName: queryDetails.name
        });
        return {
            tag: 'span',
            children: [
                {
                    tag: 'span',
                    html: '['
                },
                {
                    tag: 'a',
                    href: url,
                    html: 'Inherited: Jump to Definition'
                },
                {
                    tag: 'span',
                    html: ']'
                }
            ]
        };
    },

    formatQueryLink : function(action, params, caption, target, url) {
        url = url || LABKEY.ActionURL.buildURL("query", action, undefined, params);
        var link = {
            tag: 'a',
            href: url,
            html: caption
        };
        if (target)
            link.target = target;
        return {
            tag: 'span',
            cls: 'lk-qd-query-link',
            children: [
                {
                    tag: 'span',
                    html: '['
                },
                link,
                {
                    tag: 'span',
                    html: ']'
                }
            ]
        };
    },

    formatQueryInfo : function(queryDetails) {
        var viewDataUrl = LABKEY.ActionURL.buildURL("query", "executeQuery", undefined, {schemaName:queryDetails.schemaName,"query.queryName":queryDetails.name});
        return {
            tag: 'div',
            children: [
                {
                    tag:'div',
                    cls: 'lk-qd-name',
                    children: [
                        {
                            tag: 'a',
                            href: viewDataUrl,
                            html: Ext.util.Format.htmlEncode(queryDetails.schemaName) + "." + Ext.util.Format.htmlEncode(queryDetails.name)
                        }
                    ]
                },
                {
                    tag: 'div',
                    cls: 'lk-qd-description',
                    html: Ext.util.Format.htmlEncode(queryDetails.description)
                }
            ]
        };
    },

    tableCols : [
        {
            renderer: function(col){return this.formatExpando(col);}
        },
        {
            caption: 'Name',
            tip: 'This is the programmatic name used in the API and LabKey SQL.',
            renderer: function(col){return Ext.util.Format.htmlEncode(col.name);}
        },
        {
            caption: 'Caption',
            tip: 'This is the caption the user sees in views.',
            renderer: function(col){return col.caption;} //caption is already HTML-encoded on the server
        },
        {
            caption: 'Type',
            tip: 'The data type of the column. This will be blank if the column is not selectable',
            renderer: function(col){return col.isSelectable ? col.type : "";}
        },
        {
            caption: 'Lookup',
            tip: 'If this column is a foreign key (lookup), the query it joins to.',
            renderer: function(col){return this.formatLookup(col);}
        },
        {
            caption: 'Attributes',
            tip: 'Miscellaneous info about the column.',
            renderer: function(col){return this.formatAttributes(col);}
        },
        {
            caption: 'Description',
            tip: 'Description of the column.',
            renderer: function(col){return Ext.util.Format.htmlEncode((col.description || ""));}
        }

    ],

    formatQueryColumns : function(queryDetails) {
        var rows = [];
        rows.push(this.formatQueryColumnGroup(queryDetails.columns, "All columns in this table",
                "When writing LabKey SQL, these columns are available from this query."));

        if (queryDetails.defaultView)
        {
            rows.push(this.formatQueryColumnGroup(queryDetails.defaultView.columns, "Columns in your default view of this query",
                    "When using the LABKEY.Query.selectRows() API, these columns will be returned by default."));
        }

        return {
            tag:'table',
            cls: 'lk-qd-coltable',
            children: [
                {
                    tag: 'tbody',
                    children: rows
                }
            ]
        };
    },

    formatQueryColumnGroup : function(columns, caption, tip) {
        var rows = [];
        var col;
        var content;
        var row;
        var td;

        if (caption)
        {
            rows.push({
                tag: 'tr',
                children: [
                    {
                        tag: 'td',
                        cls: 'lk-qd-collist-title',
                        html: caption,
                        "ext:qtip": tip,
                        colspan: this.tableCols.length
                    }
                ]
            });
        }

        var headerRow = {tag: 'tr', children: []};
        for (var idxTable = 0; idxTable < this.tableCols.length; ++idxTable)
        {
            headerRow.children.push({
                tag: 'td',
                cls: 'lk-qd-colheader',
                html: this.tableCols[idxTable].caption,
                "ext:qtip": this.tableCols[idxTable].tip
            });
        }
        rows.push(headerRow);

        if (columns)
        {
            for (var idxCol = 0; idxCol < columns.length; ++idxCol)
            {
                row = {tag: 'tr', children: []};
                col = columns[idxCol];
                for (idxTable = 0; idxTable < this.tableCols.length; ++idxTable)
                {
                    td = {tag: 'td'};
                    content = this.tableCols[idxTable].renderer.call(this, col);
                    if (Ext.type(content) == "array")
                        td.children = content;
                    else if (Ext.type(content) == "object")
                        td.children = [content];
                    else
                        td.html = content;

                    row.children.push(td);
                }
                rows.push(row);
            }
        }
        
        return rows;
    },

    formatLookup : function(col) {
        if (!col.lookup || null == col.lookup.queryName)
            return "";

        var schemaNameEncoded = Ext.util.Format.htmlEncode(col.lookup.schemaName);
        var queryNameEncoded = Ext.util.Format.htmlEncode(col.lookup.queryName);
        var keyColumnEncoded = Ext.util.Format.htmlEncode(col.lookup.keyColumn);
        var displayColumnEncoded = Ext.util.Format.htmlEncode(col.lookup.displayColumn);
        var containerPathEncoded = Ext.util.Format.htmlEncode(col.lookup.containerPath);

        var caption = schemaNameEncoded + "." + queryNameEncoded;
        var tipText = "This column is a lookup to " + caption;
        if (col.lookup.keyColumn)
        {
            caption += "." + keyColumnEncoded;
            tipText += " joining to the column " + keyColumnEncoded;
        }
        if (col.lookup.displayColumn)
        {
            caption += " (" + displayColumnEncoded + ")";
            tipText += " (the value from column " + displayColumnEncoded + " is usually displayed in grids)";
        }
        tipText += ". To reference columns in the lookup table, use the syntax '"
                + Ext.util.Format.htmlEncode(Ext.util.Format.htmlEncode(col.name)) //strangely-we need to double-encode this 
                + "/col-in-lookup-table'.";

        if (!col.lookup.isPublic)
            tipText += " Note that the lookup table is not publicly-available via the APIs.";

        if (col.lookup.containerPath)
            tipText += " Note that the lookup table is defined in the folder '" + containerPathEncoded + "'.";

        var span = {
            tag: 'span',
            html: caption,
            "ext:qtip": tipText
        };

        if (col.lookup.isPublic)
            span.cls = 'labkey-link';

        //add extra dom props for the event handler
        span[this.domProps.schemaName] = schemaNameEncoded;
        span[this.domProps.queryName] = queryNameEncoded;
        if (col.lookup.containerPath)
            span[this.domProps.containerPath] = containerPathEncoded;

        return span;
    },

    formatExpando : function(col) {
        if (col.lookup)
        {
            var img = {
                tag: 'img',
                cls: 'lk-qd-expando',
                src: LABKEY.ActionURL.getContextPath() + "/_images/plus.gif"
            };
            img[this.domProps.fieldKey] = col.name;
            return img;
        }
        else
            return "";
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

    formatAttributes : function(col) {
        var attrs = {};
        for (var attrName in this.attrMap)
        {
            var attr = this.attrMap[attrName];
            if (attr.negate ? !col[attrName] : col[attrName])
            {
                if (attr.trump)
                    return this.formatAttribute(attr);
                attrs[attrName] = attr;
            }
        }

        var container = {tag: 'span', children: []};
        var sep;
        for (attrName in attrs)
        {
            if (sep)
                container.children.push(sep);
            else
                sep = {tag: 'span', html: ', '};
            container.children.push(this.formatAttribute(attrs[attrName]));
        }

        return container;
    },

    formatAttribute : function(attr) {
        return {
            tag: 'span',
            "ext:qtip": attr.label + ": " + attr.description,
            html: attr.abbreviation
        };
    },

    formatQueryException : function(queryDetails) {
        return {
            tag: 'div',
            cls: 'lk-qd-error',
            html: "There was an error while parsing this query: " + queryDetails.exception
        };
    },

    toggleLookupRow : function(expando) {
        //get the field key from the expando
        var fieldKey = expando.getAttributeNS('', this.domProps.fieldKey);

        //get the row containing the expando
        var trExpando = expando.findParentNode("tr", undefined, true);

        //if the next row is not the expanded fk col info, create it
        var trNext = trExpando.next("tr");

        if (!trNext || !trNext.hasClass("lk-fk-" + fieldKey))
        {
            var trNew = {
                tag: 'tr',
                cls: 'lk-fk-' + fieldKey,
                children: [
                    {
                        tag: 'td',
                        cls: 'lk-qd-nested-container',
                        colspan: this.tableCols.length,
                        children: [
                            {
                                tag: 'span',
                                html: 'loading...',
                                cls: 'lk-qd-loading'
                            }
                        ]
                    }
                ]
            };

            trNext = trExpando.insertSibling(trNew, 'after', false);

            var tdNew = trNext.down("td");
            this.cache.loadQueryDetails(this.schemaName, this.queryName, fieldKey, function(queryDetails){
                tdNew.update("");
                tdNew.createChild(this.formatQueryColumns(queryDetails));
                this.registerEventHandlers(tdNew);
            }, function(errorInfo){
                tdNew.update("<p class='lk-qd-error'>" + errorInfo.exception + "</p>");
            }, this);
        }
        else
            trNext.setDisplayed(!trNext.isDisplayed());

        //update the image
        if (trNext.isDisplayed())
        {
            trExpando.addClass("lk-qd-colrow-expanded");
            expando.set({src: LABKEY.ActionURL.getContextPath() + "/_images/minus.gif"});
        }
        else
        {
            trExpando.removeClass("lk-qd-colrow-expanded");
            expando.set({src: LABKEY.ActionURL.getContextPath() + "/_images/plus.gif"});
        }
    }
});

Ext.reg('labkey-query-details-panel', LABKEY.ext.QueryDetailsPanel);

LABKEY.ext.ValidateQueriesPanel = Ext.extend(Ext.Panel, {

    initComponent : function() {
        this.addEvents("queryclick");
        this.bodyStyle = "padding: 5px";
        this.stop = false;
        this.schemaNames = [];
        this.queries = [];
        this.curSchemaIdx = 0;
        this.curQueryIdx = 0;
        LABKEY.ext.ValidateQueriesPanel.superclass.initComponent.apply(this, arguments);
    },

    initEvents : function() {
        LABKEY.ext.ValidateQueriesPanel.superclass.initEvents.apply(this, arguments);
        this.ownerCt.on("beforeremove", function(){
            if (this.validating)
            {
                Ext.Msg.alert("Validation in Process", "Please stop the validation process before closing this tab.");
                return false;
            }
        }, this);
    },

    onRender : function() {
        LABKEY.ext.ValidateQueriesPanel.superclass.onRender.apply(this, arguments);
        this.body.createChild({
            tag: 'p',
            html: 'This will validate that all queries in all schemas parse and execute without errors. This will not examine the data returned from the query.',
            cls: 'lk-sb-instructions'
        });
        this.body.createChild({
            tag: 'div',
            cls: 'lk-sb-instructions',
            children: [
                {
                    id: 'lk-vq-start',
                    tag: 'button',
                    cls: 'lk-sb-button',
                    html: 'Start Validation'
                },
                {
                    id: 'lk-vq-stop',
                    tag: 'button',
                    cls: 'lk-sb-button',
                    html: 'Stop Validation',
                    disabled: true
                }
            ]
        });

        Ext.get("lk-vq-start").on("click", this.startValidation, this);
        Ext.get("lk-vq-stop").on("click", this.stopValidation, this);
    },

    setStatus : function(msg, cls, resetCls) {
        var frame = Ext.get("lk-vq-status-frame");
        if (!frame)
        {
            frame = this.body.createChild({
                id: 'lk-vq-status-frame',
                tag: 'div',
                cls: 'lk-vq-status-frame',
                children: [
                    {
                        id: 'lk-vq-status',
                        tag: 'div',
                        cls: 'lk-vq-status'
                    }
                ]
            });
        }
        var elem = Ext.get("lk-vq-status");
        elem.update(msg);

        if (true === resetCls)
            frame.dom.className = "lk-vq-status-frame";
        
        if (cls)
        {
            if (this.curStatusClass)
                frame.removeClass(this.curStatusClass);
            frame.addClass(cls);
            this.curStatusClass = cls;
        }

        return elem;
    },

    setStatusIcon : function(iconCls) {
        var elem = Ext.get("lk-vq-status");
        if (!elem)
            elem = this.setStatus("");
        if (this.curIconClass)
            elem.removeClass(this.curIconClass);
        elem.addClass(iconCls);
        this.curIconClass = iconCls;
    },

    startValidation : function() {
        this.numErrors = 0;
        this.numValid = 0;
        this.stop = false;
        this.clearValidationErrors();
        
        LABKEY.Query.getSchemas({
            successCallback: this.onSchemas,
            scope: this
        });
        Ext.get("lk-vq-start").dom.disabled = true;
        Ext.get("lk-vq-stop").dom.disabled = false;
        Ext.get("lk-vq-stop").focus();
        this.setStatus("Starting validation...", null, true);
        this.setStatusIcon("iconAjaxLoadingGreen");
        this.validating = true;
    },

    stopValidation : function() {
        this.stop = true;
        Ext.get("lk-vq-stop").set({
            disabled: true
        });
        Ext.get("lk-vq-stop").dom.disabled = true;
    },

    onSchemas : function(schemasInfo) {
        this.schemaNames = schemasInfo.schemas;
        this.curSchemaIdx = 0;
        this.validateSchema();
    },

    validateSchema : function() {
        var schemaName = this.schemaNames[this.curSchemaIdx];
        this.setStatus("Validating queries in schema '" + Ext.util.Format.htmlEncode(schemaName) + "'...");
        LABKEY.Query.getQueries({
            schemaName: schemaName,
            successCallback: this.onQueries,
            scope: this,
            includeColumns: false,
            includeUserQueries: true
        });
    },

    onQueries : function(queriesInfo) {
        this.queries = queriesInfo.queries;
        this.curQueryIdx = 0;
        if (this.queries && this.queries.length > 0)
            this.validateQuery();
        else
            this.advance();
    },

    validateQuery : function() {
        this.setStatus("Validating '" + this.getCurrentQueryLabel() + "'...");
        LABKEY.Query.validateQuery({
            schemaName: this.schemaNames[this.curSchemaIdx],
            queryName: this.queries[this.curQueryIdx].name,
            successCallback: this.onValidQuery,
            errorCallback: this.onValidationFailure,
            scope: this,
            includeAllColumns: true
        });
    },

    onValidQuery : function() {
        ++this.numValid;
        this.setStatus("Validating '" + this.getCurrentQueryLabel() + "'...OK");
        this.advance();
    },

    onValidationFailure : function(errorInfo) {
        ++this.numErrors;
        //add to errors list
        var queryLabel = this.getCurrentQueryLabel();
        this.setStatus("Validating '" + queryLabel + "'...FAILED: " + errorInfo.exception);
        this.setStatusIcon("iconAjaxLoadingRed");
        this.addValidationError(this.schemaNames[this.curSchemaIdx], this.queries[this.curQueryIdx].name, errorInfo);
        this.advance();
    },

    advance : function() {
        if (this.stop)
        {
            this.onFinish();
            return;
        }
        ++this.curQueryIdx;
        if (this.curQueryIdx >= this.queries.length)
        {
            //move to next schema
            this.curQueryIdx = 0;
            ++this.curSchemaIdx;

            if (this.curSchemaIdx >= this.schemaNames.length) //all done
                this.onFinish();
            else
                this.validateSchema();
        }
        else
            this.validateQuery();
    },

    onFinish : function() {
        Ext.get("lk-vq-start").dom.disabled = false;
        Ext.get("lk-vq-stop").dom.disabled = true;
        Ext.get("lk-vq-start").focus();
        var msg = (this.stop ? "Validation stopped by user." : "Finished Validation.");
        msg += " " + this.numValid + (1 == this.numValid ? " query was valid." : " queries were valid.");
        msg += " " + this.numErrors + (1 == this.numErrors ? " query" : " queries") + " failed validation.";
        this.setStatus(msg, (this.numErrors > 0 ? "lk-vq-status-error" : "lk-vq-status-all-ok"));
        this.setStatusIcon(this.numErrors > 0 ? "iconWarning" : "iconCheck");
        this.validating = false;
    },

    getCurrentQueryLabel : function() {
        return Ext.util.Format.htmlEncode(this.schemaNames[this.curSchemaIdx]) + "." + Ext.util.Format.htmlEncode(this.queries[this.curQueryIdx].name);
    },

    clearValidationErrors : function() {
        var errors = Ext.get("lk-vq-errors");
        if (errors)
            errors.remove();
    },

    addValidationError : function(schemaName, queryName, errorInfo) {
        var errors = Ext.get("lk-vq-errors");
        if (!errors)
        {
            errors = this.body.createChild({
                id: 'lk-vq-errors',
                tag: 'div',
                cls: 'lk-vq-errors-frame'
            });
        }

        var error = errors.createChild({
            tag: 'div',
            cls: 'lk-vq-error',
            children: [
                {
                    tag: 'div',
                    cls: 'labkey-vq-error-name',
                    children: [
                        {
                            tag: 'span',
                            cls: 'labkey-link lk-vq-error-name',
                            html: Ext.util.Format.htmlEncode(schemaName) + "." + Ext.util.Format.htmlEncode(queryName)
                        }
                    ]
                },
                {
                    tag: 'div',
                    cls: 'lk-vq-error-message',
                    html: Ext.util.Format.htmlEncode(errorInfo.exception)
                }
            ]
        });

        error.down("div span.labkey-link").on("click", function(){
            this.fireEvent("queryclick", schemaName, queryName);
        }, this);
    }
});

LABKEY.ext.SchemaBrowserHomePanel = Ext.extend(Ext.Panel, {
    initComponent : function() {
        this.bodyStyle = "padding: 5px";
        this.addEvents("schemaclick");
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
        this.queryCache.getSchemas(this.setSchemas, this);
    },

    setSchemas : function(schemasMap) {
        //erase loading message
        this.body.update("");

        this.body.createChild({
            tag: 'div',
            cls: 'lk-sb-instructions',
            html: 'Use the tree on the left to select a query, or select a schema below to expand that schema in the tree.'
        });

        //create a sorted list of schema names
        var sortedNames = [];
        for (var schemaName in schemasMap)
        {
            sortedNames.push(schemaName);
        }
        sortedNames.sort(function(a,b){return a.localeCompare(b);});

        //IE won't let you create the table rows incrementally
        //so build the rows as a data structure first and then
        //do one createChild() for the whole table
        var rows = [];
        for (var idx = 0; idx < sortedNames.length; ++idx)
        {
            rows.push({
                tag: 'tr',
                children: [
                    {
                        tag: 'td',
                        children: [
                            {
                                tag: 'span',
                                cls: 'labkey-link',
                                html: Ext.util.Format.htmlEncode(sortedNames[idx])
                            }
                        ]
                    },
                    {
                        tag: 'td',
                        html: Ext.util.Format.htmlEncode(schemasMap[sortedNames[idx]].description)
                    }
                ]
            });
        }

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
                },
                {
                    tag: 'tbody',
                    children: rows
                }
            ]
        });

        var nameLinks = table.query("tbody tr td span");
        for (idx = 0; idx < nameLinks.length; ++idx)
        {
            Ext.get(nameLinks[idx]).on("click", function(evt,t){
                this.fireEvent("schemaclick", t.innerHTML);
            }, this);
        }
    }
});

Ext.reg('labkey-schema-browser-home-panel', LABKEY.ext.SchemaBrowserHomePanel);

LABKEY.ext.SchemaSummaryPanel = Ext.extend(Ext.Panel, {
    initComponent : function() {
        this.bodyStyle = "padding: 5px";
        this.addEvents("queryclick");
        LABKEY.ext.SchemaSummaryPanel.superclass.initComponent.apply(this, arguments);
    },

    onRender : function() {
        //call superclass to create basic elements
        LABKEY.ext.SchemaSummaryPanel.superclass.onRender.apply(this, arguments);
        this.body.createChild({
            tag: 'div',
            cls: 'lk-qd-loading',
            html: 'loading...'
        });
        this.cache.getQueries(this.schemaName, this.onQueries, this);
    },

    onQueries : function(queriesMap) {
        this.queries = queriesMap;
        this.body.update("");
        this.body.createChild({
            tag: 'div',
            cls: 'lk-qd-name',
            html: this.schemaName + " Schema"
        });
        this.body.createChild({
            tag: 'div',
            cls: 'lk-qd-description',
            html: this.cache.getSchema(this.schemaName).description
        });

        //create one list for user-defined and one for built-in
        var userDefined = [];
        var builtIn = [];
        for (var queryName in queriesMap)
        {
            var query = queriesMap[queryName];
            query.name = queryName;
            if (query.isUserDefined)
                userDefined.push(query);
            else
                builtIn.push(query);
        }

        if (userDefined.length > 0)
            userDefined.sort(function(a,b){return a.name.localeCompare(b.name);});
        if (builtIn.length > 0)
            builtIn.sort(function(a,b){return a.name.localeCompare(b.name);});

        var rows = [];
        if (userDefined.length > 0)
            rows.push(this.formatQueryList(userDefined, "User-Defined Queries"));
        if (builtIn.length > 0)
            rows.push(this.formatQueryList(builtIn, "Built-In Queries and Tables"));

        var table = this.body.createChild({
            tag: 'table',
            cls: 'lk-qd-coltable',
            children: [{tag: 'tbody', children: rows}]
        });

        var nameLinks = table.query("tbody tr td span");
        for (var idx = 0; idx < nameLinks.length; ++idx)
        {
            Ext.get(nameLinks[idx]).on("click", function(evt,t){
                this.fireEvent("queryclick", this.schemaName, Ext.util.Format.htmlDecode(t.innerHTML));
            }, this);
        }

    },

    formatQueryList : function(queries, title) {
        var rows = [{
                tag: 'tr',
                children: [{
                    tag: 'td',
                    colspan: 2,
                    cls: 'lk-qd-collist-title',
                    html: title
                }]
            },{
                tag: 'tr',
                children: [{
                    tag: 'td',
                    cls: 'lk-qd-colheader',
                    html: 'Name'
                },{
                    tag: 'td',
                    cls: 'lk-qd-colheader',
                    html: 'Description'
                }]
            }];

        var query;
        for (var idx = 0; idx < queries.length; ++idx)
        {
            query = queries[idx];
            rows.push({
                tag: 'tr',
                children: [
                    {
                        tag: 'td',
                        children: [
                            {
                                tag: 'span',
                                cls: 'labkey-link',
                                html: Ext.util.Format.htmlEncode(query.name)
                            }
                        ]
                    },
                    {
                        tag: 'td',
                        html: Ext.util.Format.htmlEncode(query.description)
                    }
                ]
            });
        }

        return rows;
    }
});

Ext.reg('labkey-schema-summary-panel', LABKEY.ext.SchemaSummaryPanel);


LABKEY.requiresCss("_images/icons.css");

LABKEY.ext.SchemaBrowser = Ext.extend(Ext.Panel, {

    qdpPrefix: 'qdp-',
    sspPrefix: 'ssp-',
    historyPrefix: 'sbh-',

    _qcache: new LABKEY.ext.QueryCache(),

    initComponent : function(){
        var tbar = [
            {
                text: 'Refresh',
                handler: this.onRefresh,
                scope: this,
                iconCls:'iconReload',
                tooltip: 'Refreshes the tree of schemas and queries, or a particular schema if one is selected.'
            },
            {
                text: 'Validate Queries',
                handler: function(){this.showPanel("lk-vq-panel");},
                scope: this,
                iconCls: 'iconCheck',
                tooltip: 'Opens the validate queries tab where you can validate all the queries defined in this folder.'
            }
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
        }

        Ext.apply(this,{
            _qdcache: new LABKEY.ext.QueryDetailsCache(),
            layout: 'border',
            items : [
                {
                    id: 'lk-sb-tree',
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
                                this.fireEvent("schemasloaded", this);
                            },
                            scope: this
                        }
                    }
                },
                {
                    id: 'lk-sb-details',
                    xtype: 'tabpanel',
                    region: 'center',
                    activeTab: 0,
                    items: [
                        {
                            xtype: 'labkey-schema-browser-home-panel',
                            title: 'Home',
                            id: 'lk-sb-panel-home',
                            queryCache: this._qcache,
                            listeners: {
                                schemaclick: {
                                    fn: function(schemaName){
                                        this.selectSchema(schemaName);
                                        this.showPanel(this.sspPrefix + schemaName);
                                    },
                                    scope: this
                                }
                            }
                        }
                    ],
                    enableTabScroll:true,
                    defaults: {autoScroll:true},
                    listeners: {
                        tabchange: {
                            fn: this.onTabChange,
                            scope: this
                        }
                    },
                    plugins: new Ext.ux.TabCloseMenu()
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
        Ext.History.init();
        Ext.History.on('change', this.onHistoryChange, this);
    },

    showPanel : function(id) {
        var tabs = this.getComponent("lk-sb-details");
        if (tabs.getComponent(id))
            tabs.activate(id);
        else
        {
            var panel;
            if (id == "lk-vq-panel")
            {
                panel = new LABKEY.ext.ValidateQueriesPanel({
                    id: "lk-vq-panel",
                    closable: true,
                    title: "Validate Queries",
                    listeners: {
                        queryclick: {
                            fn: function(schemaName, queryName){
                                this.selectQuery(schemaName, queryName); this.showQueryDetails(schemaName, queryName);
                            },
                            scope: this
                        }
                    }
                });
            }
            else if (this.qdpPrefix == id.substring(0, this.qdpPrefix.length))
            {
                var idMap = this.parseQueryPanelId(id);
                panel = new LABKEY.ext.QueryDetailsPanel({
                    cache: this._qdcache,
                    schemaName: idMap.schemaName,
                    queryName: idMap.queryName,
                    id: id,
                    title: Ext.util.Format.htmlEncode(idMap.schemaName) + "." + Ext.util.Format.htmlEncode(idMap.queryName),
                    autoScroll: true,
                    listeners: {
                        lookupclick: {
                            fn: this.onLookupClick,
                            scope: this
                        }
                    },
                    closable: true
                });
            }
            else if (this.sspPrefix == id.substring(0, this.sspPrefix.length))
            {
                var schemaName = id.substring(this.sspPrefix.length);
                panel = new LABKEY.ext.SchemaSummaryPanel({
                    cache: this._qcache,
                    schemaName: schemaName,
                    id: id,
                    title: Ext.util.Format.htmlEncode(schemaName),
                    autoScroll: true,
                    listeners: {
                        queryclick: {
                            fn: function(schemaName, queryName){this.showQueryDetails(schemaName, queryName);},
                            scope: this
                        }
                    },
                    closable: true
                });
            }


            if (panel)
            {
                tabs.add(panel);
                tabs.activate(panel.id);
            }
        }
    },

    parseQueryPanelId : function(id) {
        var parts = id.substring(this.qdpPrefix.length).split('&');
        if (parts.length >= 2)
            return {schemaName: decodeURIComponent(parts[0]), queryName: decodeURIComponent(parts[1])};
        else
            return {};
    },

    buildQueryPanelId : function(schemaName, queryName) {
        return this.qdpPrefix + encodeURIComponent(schemaName) + "&" + encodeURIComponent(queryName);
    },

    onHistoryChange : function(token) {
        if (!token)
            token = "lk-sb-panel-home"; //back to home panel
        else
            token = token.substring(this.historyPrefix.length);

        this.showPanel(token);
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
        var tree = this.getComponent("lk-sb-tree");
        var node = tree.getSelectionModel().getSelectedNode();
        if (node && node.attributes.schemaName)
            window.location = this.getCreateQueryUrl(node.attributes.schemaName, node.attributes.queryName), "createQuery";
        else
            Ext.Msg.alert("Which Schema?", "Please select the schema in which you want to create the new query.");

    },

    getCreateQueryUrl : function(schemaName, queryName) {
        var params = {schemaName: schemaName};
        if (queryName)
            params.ff_baseTableName = queryName;
        return LABKEY.ActionURL.buildURL("query", "newQuery", undefined, params);
    },

    onTabChange : function(tabpanel, tab) {
        if (tab.schemaName && tab.queryName)
            this.selectQuery(tab.schemaName, tab.queryName);
        else if (tab.schemaName)
            this.selectSchema(tab.schemaName);

        //don't add any history the first time this is called.
        //the creation of the home tab causes this event to fire
        //but we don't want to add something to the history stack
        //at that time.
        if (this._addHistory)
        {
            if (tab.id == 'lk-sb-panel-home')
                Ext.History.add("#");
            else
                Ext.History.add(this.historyPrefix + tab.id);
        }
        this._addHistory = true;
    },

    onTreeClick : function(node, evt) {
        if (node.attributes.schemaName && !node.attributes.queryName)
            this.showPanel(this.sspPrefix + node.attributes.schemaName);
        else if (node.leaf)
            this.showQueryDetails(node.attributes.schemaName, node.attributes.queryName);
    },

    showQueryDetails : function(schemaName, queryName) {
        this.showPanel(this.buildQueryPanelId(schemaName, queryName));
    },

    onSchemaAdminClick : function() {
        window.location = LABKEY.ActionURL.buildURL("query", "admin");
    },

    onRefresh : function() {
        //clear the query details cache
        this._qcache.clearAll();
        this._qdcache.clearAll();

        //remove all tabs except for the first one (home)
        var tabs = this.getComponent("lk-sb-details");
        while (tabs.items.length > 1)
        {
            tabs.remove(tabs.items.length - 1, true);
        }

        //reload schema tree
        this.getComponent("lk-sb-tree").getRootNode().reload();
    },

    onLookupClick : function(schemaName, queryName, containerPath) {
        if (containerPath && containerPath != LABKEY.ActionURL.getContainer())
            window.open(LABKEY.ActionURL.buildURL("query", "begin", containerPath, {schemaName: schemaName, queryName: queryName}));
        else
        {
            this.selectQuery(schemaName, queryName, function(){
                this.showQueryDetails(schemaName, queryName);
            }, this);
        }
    },

    selectSchema : function(schemaName) {
        var tree = this.getComponent("lk-sb-tree");
        var schemaNode = getSchemaNode(tree, schemaName);

        if (schemaNode)
        {
            tree.selectPath(schemaNode.getPath());
            schemaNode.expand(false, false);
        }
        else
            Ext.Msg.alert("Missing Schema", "The schema '" + Ext.util.Format.htmlEncode(schemaName) + "' was not found. The data source for the schema may be unreachable, or the schema may have been deleted.");

    },

    selectQuery : function(schemaName, queryName, callback, scope) {
        var tree = this.getComponent("lk-sb-tree");
        var schemaNode = getSchemaNode(tree, schemaName);
        if (!schemaNode)
        {
            Ext.Msg.alert("Missing Schema", "The schema '" +  Ext.util.Format.htmlEncode(schemaName) + "' was not found. The data source for the schema may be unreachable, or the schema may have been deleted.");
            return;
        }

        schemaNode.expand(false, false, function(schemaNode){
            //look for the query node under both built-in and user-defined
            var queryNode;
            if (schemaNode.childNodes.length > 0)
                queryNode = schemaNode.childNodes[0].findChildBy(function(node){return node.attributes.queryName.toLowerCase() == queryName.toLowerCase();});
            if (!queryNode && schemaNode.childNodes.length > 1)
                queryNode = schemaNode.childNodes[1].findChildBy(function(node){return node.attributes.queryName.toLowerCase() == queryName.toLowerCase();});

            if (!queryNode)
            {
                Ext.Msg.alert("Missing Query", "The query " +  Ext.util.Format.htmlEncode(schemaName + "." + queryName) + " was not found! It may not be publicly accessible.");
                return;
            }

            tree.selectPath(queryNode.getPath());
            if (callback)
                callback.call((scope || this));
        });
    }
});

function getSchemaNode(tree, schemaName)
{
    var root = tree.getRootNode();

    var dataSourceNodes = root.childNodes;
    var schemaToFind = schemaName.toLowerCase();
    var schemaNode;

    for (var i = 0; !schemaNode && i < dataSourceNodes.length; i++)
    {
        schemaNode = dataSourceNodes[i].findChildBy(function(node){
            return node.attributes.schemaName && node.attributes.schemaName.toLowerCase() == schemaToFind;
        });
    }

    return schemaNode;
}

//adapted from http://www.extjs.com/deploy/dev/examples/tabs/tabs-adv.js
Ext.ux.TabCloseMenu = function(){
    var tabs, menu, ctxItem;
    this.init = function(tp){
        tabs = tp;
        tabs.on('contextmenu', onContextMenu);
    };

    function onContextMenu(ts, item, e){
        if(!menu){ // create context menu on first right click
            menu = new Ext.menu.Menu({
            cls: 'extContainer',
            items: [{
                id: tabs.id + '-close',
                text: 'Close',
                handler : function(){
                    tabs.remove(ctxItem);
                }
            },{
                id: tabs.id + '-close-all',
                text: 'Close All',
                handler : function(){
                    tabs.items.each(function(item){
                        if(item.closable){
                            tabs.remove(item);
                        }
                    });
                }
            },{
                id: tabs.id + '-close-others',
                text: 'Close Others',
                handler : function(){
                    tabs.items.each(function(item){
                        if(item.closable && item != ctxItem){
                            tabs.remove(item);
                        }
                    });
                }
            }]});
        }
        ctxItem = item;
        var items = menu.items;
        items.get(tabs.id + '-close').setDisabled(!item.closable);
        var disableOthers = true;
        var disableAll = true;
        tabs.items.each(function(){
            if(this != item && this.closable){
                disableOthers = false;
                return false;
            }
        });
        tabs.items.each(function(){
            if(this.closable){
                disableAll = false;
                return false;
            }
        });
        items.get(tabs.id + '-close-others').setDisabled(disableOthers);
        items.get(tabs.id + '-close-all').setDisabled(disableAll);
	    e.stopEvent();
        menu.showAt(e.getPoint());
    }
};
