/*
 * Copyright (c) 2015-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.query.browser.view.QueryDetails', {
    extend: 'LABKEY.query.browser.view.Base',

    domProps : {
        schemaName: 'lkqdSchemaName',
        queryName: 'lkqdQueryName',
        containerPath: 'lkqdContainerPath',
        fieldKey: 'lkqdFieldKey'
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
        },
        calculated: {
            abbreviation: 'Calc',
            label: 'Calculated',
            description: 'This column contains a calculated expression'
        },
        phi: {
            enumeration: {
                PHI: {
                    abbreviation: 'PHI',
                    label: 'Full PHI',
                    description: 'This column contains PHI',
                },
                Limited: {
                    abbreviation: 'LimPHI',
                    label: 'Limited PHI',
                    description: 'This column contains PHI',
                },
                Restricted: {
                    abbreviation: 'ResPHI',
                    label: 'Restricted PHI',
                    description: 'This column contains PHI',
                }
            },
        },
    },

    tableCols : [{
        renderer: function(col) { return this.formatExpando(col); }
    },{
        caption: 'Name',
        tip: 'This is the programmatic name used in the API and LabKey SQL.',
        renderer: function(col) { return Ext4.htmlEncode(col.name); }
    },{
        caption: 'Caption',
        tip: 'This is the caption the user sees in views.',
        renderer: function(col) { return col.caption; } // caption is already HTML-encoded on the server
    },{
        caption: 'Type',
        tip: 'The data type of the column. This will be blank if the column is not selectable',
        renderer: function(col) { return col.isSelectable ? col.type : ''; }
    },{
        caption: 'Lookup',
        tip: 'If this column is a foreign key (lookup), the query it joins to.',
        renderer: function(col) { return this.formatLookup(col); }
    },{
        caption: 'Attributes',
        tip: 'Miscellaneous info about the column.',
        renderer: function(col) { return this.formatAttributes(col); }
    },{
        caption: 'Description',
        tip: 'Description of the column.',
        renderer: function(col) { return Ext4.htmlEncode((col.description || '')); }
    }],

    constructor : function(config) {
        this.callParent([config]);
        this.addEvents('lookupclick');
    },

    initComponent : function() {
        this.cache = LABKEY.query.browser.cache.QueryDetails;
        this.queriesCache = LABKEY.query.browser.cache.QueryDependencies;
        this.items = [{
            xtype: 'box',
            itemId: 'loader',
            id: 'loaderTag',
            autoEl: {
                tag: 'p',
                cls: 'lk-qd-loading',
                html: 'Loading...'
            }
        }];
        this.callParent();
        this.on('afterrender', this.loadQueryDetails, this, {single: true});
        this.parent.on('dependencychanged', this.refreshQueryDependencies, this);
    },

    loadQueryDetails : function(){
        this.cache.loadQueryDetails(this.schemaName, this.queryName, this.fk,
                function(queryDetails) {
                    this.queryDetails = queryDetails;
                    this.setQueryDetails(this.queryDetails);
                },
                this.onLoadError,
                this);

    },

    onLoadError : function(errorInfo){
        if (Ext4.getCmp('loaderTag')) {
            Ext4.getCmp('loaderTag').update('<div class="lk-qd-error">Error in query: ' + Ext4.htmlEncode(errorInfo.exception) + '</div>');
        }
    },

    displayError : function(msg) {
        return '<div class="lk-qd-error">' + Ext4.htmlEncode(msg) + '</div>';
    },

    formatAttribute : function(attr) {
        return {
            tag: 'span',
            "data-qtip": attr.label + ': ' + attr.description,
            html: attr.abbreviation
        };
    },

    formatAttributes : function(col) {
        var attrs = {}, attrName, attr;
        for (attrName in this.attrMap) {
            if (this.attrMap.hasOwnProperty(attrName)) {
                attr = this.attrMap[attrName];
                let value = col[attrName];
                if (attr.enumeration && attr.enumeration[value]) {
                    attrs[value] = attr.enumeration[value];
                }
                else if (attr.negate ? !value : value) {
                    if (attr.trump) {
                        return this.formatAttribute(attr);
                    }
                    attrs[attrName] = attr;
                }
            }
        }

        var container = {tag: 'span', children: []}, sep;
        for (attrName in attrs) {
            if (attrs.hasOwnProperty(attrName)) {
                if (sep) {
                    container.children.push(sep);
                }
                else {
                    sep = {tag: 'span', html: ', '};
                }
                container.children.push(this.formatAttribute(attrs[attrName]));
            }
        }

        return container;
    },

    formatExpando : function(col) {
        if (col.lookup) {
            var img = {
                tag: 'img',
                cls: 'lk-qd-expando',
                src: LABKEY.ActionURL.getContextPath() + '/_images/plus.gif'
            };
            img[this.domProps.fieldKey] = col.name;
            return img;
        }
        return '';
    },

    formatJumpToDefinitionLink : function(queryDetails) {
        var url = LABKEY.ActionURL.buildURL('query', 'begin', queryDetails.containerPath, {
            schemaName: queryDetails.schemaName.toString(),
            queryName: queryDetails.name
        });
        return LABKEY.Utils.textLink({
            href: url,
            text: 'Inherited: Jump to Definition'
        });
    },

    formatLookup : function(col) {
        if (!col.lookup || null == col.lookup.queryName) {
            return '';
        }

        var schemaNameEncoded = Ext4.htmlEncode(col.lookup.schemaName),
            queryNameEncoded = Ext4.htmlEncode(col.lookup.queryName),
            keyColumnEncoded = Ext4.htmlEncode(col.lookup.keyColumn),
            displayColumnEncoded = Ext4.htmlEncode(col.lookup.displayColumn),
            containerPathEncoded = Ext4.htmlEncode(col.lookup.containerPath),
            caption = schemaNameEncoded + '.' + queryNameEncoded,
            tipText = 'This column is a lookup to ' + caption;

        if (col.lookup.keyColumn) {
            caption += '.' + keyColumnEncoded;
            tipText += ' joining to the column ' + keyColumnEncoded;
        }

        if (col.lookup.displayColumn) {
            caption += " (" + displayColumnEncoded + ")";
            tipText += " (the value from column " + displayColumnEncoded + " is usually displayed in grids)";
        }

        tipText += ". To reference columns in the lookup table, use the syntax '" +
                    Ext4.htmlEncode(Ext4.htmlEncode(col.name)) + // strangely we need to double-encode this
                    "/col-in-lookup-table'.";

        if (!col.lookup.isPublic) {
            tipText += ' Note that the lookup table is not publicly-available via the APIs.';
        }

        if (col.lookup.containerPath) {
            tipText += " Note that the lookup table is defined in the folder '" + containerPathEncoded + "'.";
        }

        var span = {
            tag: 'span',
            html: caption,
            "data-qtip": tipText
        };

        if (col.lookup.isPublic) {
            span.cls = 'labkey-link';
        }

        //add extra dom props for the event handler
        span[this.domProps.schemaName] = schemaNameEncoded;
        span[this.domProps.queryName] = queryNameEncoded;

        if (col.lookup.containerPath) {
            span[this.domProps.containerPath] = containerPathEncoded;
        }

        return span;
    },

    formatQueryColumns : function(queryDetails) {
        var rows = [
            this.formatQueryColumnGroup(queryDetails.columns, 'All columns in this table', 'When writing LabKey SQL, these columns are available from this query.')
        ];

        if (queryDetails.defaultView) {
            rows.push(this.formatQueryColumnGroup(queryDetails.defaultView.columns, 'Columns in your default view of this query',
                    'When using the LABKEY.Query.selectRows() API, these columns will be returned by default.'));
        }

        return {
            tag: 'table',
            cls: 'lk-qd-coltable',
            children: [{
                tag: 'tbody',
                children: rows
            }]
        };
    },

    formatQueryColumnGroup : function(columns, caption, tip) {
        var rows = [],
            col,
            content,
            row,
            td,
            headerRow = {tag: 'tr', children: []};

        if (caption) {
            rows.push({
                tag: 'tr',
                children: [{
                    tag: 'td',
                    cls: 'lk-qd-collist-title',
                    html: caption,
                    "data-qtip": tip,
                    colspan: this.tableCols.length
                }]
            });
        }

        for (var idxTable = 0; idxTable < this.tableCols.length; ++idxTable) {
            headerRow.children.push({
                tag: 'td',
                cls: 'lk-qd-colheader',
                html: this.tableCols[idxTable].caption,
                "data-qtip": this.tableCols[idxTable].tip
            });
        }
        rows.push(headerRow);

        if (columns) {
            for (var idxCol = 0; idxCol < columns.length; ++idxCol) {
                row = {tag: 'tr', children: []};
                col = columns[idxCol];
                for (idxTable = 0; idxTable < this.tableCols.length; ++idxTable) {
                    td = {tag: 'td'};
                    content = this.tableCols[idxTable].renderer.call(this, col);
                    if (Ext4.isArray(content)) {
                        td.children = content;
                    }
                    else if (Ext4.isObject(content)) {
                        td.children = [content];
                    }
                    else {
                        td.html = content;
                    }

                    row.children.push(td);
                }
                rows.push(row);
            }
        }

        return rows;
    },

    formatIndices: function (queryDetails) {
        // If no indices are present, don't render anything
        if (!queryDetails.indices || !this.hasProperties(queryDetails.indices))
            return;

        var tpl = new Ext4.XTemplate(
            '<table class="lk-qd-coltable" style="margin-top: 1em;">',
                '<thead>',
                    '<tr><td colspan="3" class="lk-qd-collist-title">Indices</td></tr>',
                    '<tr>',
                        '<td class="lk-qd-colheader">Name</td>',
                        '<td class="lk-qd-colheader">Type</td>',
                        '<td class="lk-qd-colheader">Columns</td>',
                    '</tr>',
                '</thead>',
                '<tbody>',
                '<tpl foreach="indices">',
                    '<tr>',
                        '<td>{$:htmlEncode}</td>',
                        '<td>{[Ext4.htmlEncode(values.type)]}</td>',
                        '<td>{[Ext4.htmlEncode(values.columns.join(", "))]}</td>',
                    '</tr>',
                '</tpl>',
                '</tbody>',
            '</table>'
        );

        return {
            tag: 'div',
            cls: 'lk-qd-indices',
            html: tpl.apply(queryDetails)
        };
    },

    formatDependencies : function () {

        const dependencies = this.queriesCache.getDependencies(LABKEY.container.id, this.schemaName, this.queryName);

        // issue : 40993 sort dependencies by type, schemaName and name
        let sortFn = function(a, b){
            // group by type
            let type = a.type.localeCompare(b.type);
            if (type !== 0)
                return type;

            // schema name
            let schemaName = a.schemaDisplayName.toLowerCase().localeCompare(b.schemaDisplayName.toLowerCase());
            if (schemaName !== 0)
                return schemaName;

            // query name
            return a.name.toLowerCase().localeCompare(b.name.toLowerCase());
        };

        if (dependencies){
            dependencies.dependees.sort(sortFn);
            dependencies.dependents.sort(sortFn);

            let tpl = new Ext4.XTemplate(
                '<h3 style="padding-top: 1.0em">Dependency Report</h3>',
                '<span>The queries or tables that this query or table depends on and the queries or tables that depend on it.</span>',
                '<table class="lk-qd-coltable" style="margin-top: 1em;">',
                    '<tr>',
                        '<td>',
                            '<table class="lk-qd-coltable">',
                                '<thead>',
                                '<tr><td colspan="4" class="lk-qd-collist-title" data-qtip="{[this.getDependeesTip(this)]}">Dependees</td></tr>',
                                '<tr>',
                                    '<td class="lk-qd-colheader">Folder</td>',
                                    '<td class="lk-qd-colheader">Type</td>',
                                    '<td class="lk-qd-colheader">Schema</td>',
                                    '<td class="lk-qd-colheader">Query</td>',
                                '</tr>',
                                '</thead>',
                                '<tpl for="dependees">',
                                    '<tr>',
                                        '<td>{[this.renderFolder(this, values)]}</td>',
                                        '<td>{[this.renderType(this, values)]}</td>',
                                        '<td>{schemaDisplayName:htmlEncode}</td>',
                                        '<td>{[this.renderName(this, values)]}</td>',
                                    '</tr>',
                                '</tpl>',
                            '</table>',
                        '</td>',
                        '<td>',
                            '<table class="lk-qd-coltable">',
                                '<thead>',
                                    '<tr><td colspan="4" class="lk-qd-collist-title" data-qtip="{[this.getDependentsTip(this)]}">Dependents</td></tr>',
                                    '<tr>',
                                        '<td class="lk-qd-colheader">Folder</td>',
                                        '<td class="lk-qd-colheader">Type</td>',
                                        '<td class="lk-qd-colheader">Schema</td>',
                                        '<td class="lk-qd-colheader">Query</td>',
                                    '</tr>',
                                '</thead>',
                                '<tpl for="dependents">',
                                    '<tr>',
                                        '<td>{[this.renderFolder(this, values)]}</td>',
                                        '<td>{[this.renderType(this, values)]}</td>',
                                        '<td>{schemaDisplayName:htmlEncode}</td>',
                                        '<td>{[this.renderName(this, values)]}</td>',
                                    '</tr>',
                                '</tpl>',
                            '</table>',
                        '</td>',
                    '</tr>',
                '</table>',
                {
                    renderName : function(cmp, row) {
                        // for reports we just want to link out to URL provided, for tables and
                        // queries we will just navigate within the schema browser
                        if (row.type === 'Report') {
                            let html = "<a class='labkey-link'";

                            html += " href='" + row.url + "'>";
                            html += Ext4.htmlEncode(row.name) + "</a>";
                            return html;
                        }
                        else {
                            let html = "<span class='labkey-link'";

                            html += " " + cmp.me.domProps.schemaName + "='" + Ext4.htmlEncode(row.schemaName) + "'";
                            html += " " + cmp.me.domProps.containerPath + "='" + Ext4.htmlEncode(row.containerPath) + "'";
                            html += " " + cmp.me.domProps.queryName + "='" + Ext4.htmlEncode(row.name) + "'>";
                            html += Ext4.htmlEncode(row.name);
                            html += "</span>";
                            return html;
                        }
                    },
                    renderFolder : function(cmp, row) {
                        // don't render is this is current folder
                        if (LABKEY.container.path === row.containerPath)
                            return "";

                        let folder = row.containerPath;
                        if (folder.startsWith('/'))
                            folder = folder.substr(1);

                        return Ext4.htmlEncode(folder);
                    },
                    renderType : function(cmp, row) {
                        let cls = 'fa fa-database';
                        let tip = 'Type : table';

                        if (row.type === 'Report') {
                            cls = 'fa fa-area-chart';
                            tip = 'Type : report';
                        }
                        else if (row.type === 'Query') {
                            cls = 'fa fa-table';
                            tip = 'Type : query';
                        }
                        return '<span class="' + cls + '" data-qtip="' + tip + '"></span>';
                    },
                    getDependeesTip : function(cmp) {
                        if (cmp.me.queryDetails.isUserDefined)
                            return 'The queries or tables that this query depends on';
                        else
                            return 'The queries or tables that this table depends on';
                    },
                    getDependentsTip : function(cmp) {
                        if (cmp.me.queryDetails.isUserDefined)
                            return 'The queries or tables that depend on this query';
                        else
                            return 'The queries or tables that depend on this table';
                    },
                    me : this
                }
            );

            return {
                tag: 'div',
                cls: 'lk-qd-dependencies',
                html: tpl.apply(dependencies)
            };
        }
    },

    hasProperties: function (o) {
        for (var name in o) {
            if (o.hasOwnProperty(name))
                return true;
        }
        return false;
    },

    formatQueryDetails : function(queryDetails) {

        var children = [
            this.formatQueryLinks(queryDetails),
            this.formatQueryInfo(queryDetails)
        ];

        if (queryDetails.exception) {
            children.push(this.displayError('There was an error while parsing this query: ' + queryDetails.exception));
        }
        else {
            children.push(this.formatQueryColumns(queryDetails));

            var indices = this.formatIndices(queryDetails);
            if (indices)
                children.push(indices);
        }

        return Ext4.create('Ext.Component', {
            autoEl: {
                tag: 'div',
                children: children
            }
        });
    },

    formatQueryInfo : function(queryDetails) {
        var params = {
                    schemaName: queryDetails.schemaName,
                    'query.queryName': queryDetails.name
                },
            viewDataUrl = LABKEY.ActionURL.buildURL('query', (queryDetails.exception ? 'sourceQuery': 'executeQuery'), null, params),
            schemaKey = LABKEY.SchemaKey.fromString(queryDetails.schemaName),
            displayText = queryDetails.name;

        if (queryDetails.name.toLowerCase() != (queryDetails.title || '').toLowerCase()) {
            displayText += ' (' + queryDetails.title + ')';
        }

        var children = [{
            tag: 'h2',
            children: [{
                tag: 'a',
                href: viewDataUrl,
                html: Ext4.htmlEncode(schemaKey.toDisplayString()) + '.' + Ext4.htmlEncode(displayText)
            }]
        }];

        if (queryDetails.isUserDefined) {
            children.push({
                tag: 'span',
                style: 'cursor: default;',
                html: 'LabKey SQL query' + (queryDetails.moduleName ? ' defined in ' + Ext4.htmlEncode(queryDetails.moduleName) + ' module' : '')
            });
        }
        else {
            children.push({
                tag: 'span',
                style: 'cursor: default;',
                html: 'Built-in table'
            });
        }

        return {
            tag: 'div',
            children: [{
                tag: 'div',
                cls: 'lk-qd-name g-tip-label',
                children: children
            },{
                tag: 'div',
                cls: 'lk-qd-description',
                html: Ext4.htmlEncode(queryDetails.description)
            },{
                tag: 'div',
                cls: 'lk-vq-warn-message',
                html: Ext4.htmlEncode(queryDetails.exception || queryDetails.warning)
            }]
        };
    },

    formatQueryLink : function(action, params, caption, target, url) {
        return LABKEY.Utils.textLink({
            href: url || LABKEY.ActionURL.buildURL('query', action, undefined, params),
            text: caption,
            target: (target === undefined ? '' : target)
        });
    },

    formatQueryLinks : function(queryDetails) {
        var container = {tag: 'div', cls: 'lk-qd-links'},
            children = [];

        if (queryDetails.isInherited) {
            children.push(this.formatJumpToDefinitionLink(queryDetails));
        }

        var params = {
            schemaName: queryDetails.schemaName,
            'query.queryName': queryDetails.name
        };

        var metadataParams = {
            schemaName: queryDetails.schemaName,
            'queryName': queryDetails.name
        };

        if (!queryDetails.exception) {
            children.push(this.formatQueryLink('executeQuery', params, 'view data', undefined, queryDetails.viewDataUrl));
        }

        if (queryDetails.isUserDefined) {
            if (!queryDetails.isInherited) {
                if (queryDetails.canEdit) {
                    children.push(this.formatQueryLink("sourceQuery", params, "edit source"));
                    children.push(this.formatQueryLink("propertiesQuery", params, "edit properties"));
                }
                else {
                    children.push(this.formatQueryLink('viewQuerySource', params, 'view source'));
                }
                if (queryDetails.canDelete) {
                    children.push(this.formatQueryLink("deleteQuery", params, "delete query"));
                }
                if (queryDetails.isMetadataOverrideable) {
                    children.push(this.formatQueryLink("metadataQuery", metadataParams, "edit metadata"));
                }
            }
        }
        else {
            if (LABKEY.Security.currentUser.isAdmin) {  // These links go to list designer, etc. Leave as admin-only.
                if (queryDetails.createDefinitionUrl) {
                    children.push(this.formatQueryLink(null, null, 'create definition', undefined, queryDetails.createDefinitionUrl));
                }
                else if (queryDetails.editDefinitionUrl) {
                    children.push(this.formatQueryLink(null, null, 'edit definition', undefined, queryDetails.editDefinitionUrl));
                }

                if (queryDetails.isMetadataOverrideable) {
                    children.push(this.formatQueryLink('metadataQuery', metadataParams, 'edit metadata'));
                }
            }

            if (LABKEY.devMode || LABKEY.Security.currentUser.isDeveloper) {
                children.push(this.formatQueryLink('rawTableMetaData', params, 'view raw table metadata'));
            }
        }

        if (queryDetails.auditHistoryUrl) {
            children.push(this.formatQueryLink('auditHistory', params, 'view history', undefined, queryDetails.auditHistoryUrl));
        }

        container.children = children;
        return container;
    },

    getExpandoClickFn : function(expando) {
        return function() {
            this.toggleLookupRow(expando);
        };
    },

    getLookupLinkClickFn : function(lookupLink) {
        return function() {
            this.fireEvent('lookupclick',
                    Ext4.htmlDecode(lookupLink.getAttributeNS('', this.domProps.schemaName)),
                    Ext4.htmlDecode(lookupLink.getAttributeNS('', this.domProps.queryName)),
                    Ext4.htmlDecode(lookupLink.getAttributeNS('', this.domProps.containerPath))
            );
        };
    },

    registerEventHandlers : function(containerEl) {
        // register for events on lookup links and expandos
        var lookupLinks = containerEl.query("table tr td span[class='labkey-link']"),
            expandos = containerEl.query("table tr td img[class='lk-qd-expando']"),
            link, expando, i;

        for (i = 0; i < lookupLinks.length; i++) {
            link = Ext4.get(lookupLinks[i]);
            link.on('click', this.getLookupLinkClickFn(link), this);
        }

        for (i = 0; i < expandos.length; i++) {
            expando = Ext4.get(expandos[i]);
            expando.on('click', this.getExpandoClickFn(expando), this);
        }
    },

    renderQueryDetails : function() {
        this.removeAll();
        var component = this.formatQueryDetails(this.queryDetails);

        component.on('afterrender', function(c) {
            this.registerEventHandlers(c.getEl());
        }, this, {single: true});

        this.add(component);

        // add a temporary placeholder for the query dependencies but don't block the entire page
        this.add({
            xtype : 'box',
            height : 100,
            itemId : 'lk-dependency-report',
            listeners : {
                render : {
                    scope : this,
                    fn : function(cmp){
                        cmp.getEl().mask('loading dependencies');
                        this.queriesCache.load(null, function(){this.refreshQueryDependencies()}, this.onLoadError, this);
                    }
                }
            }
        });
    },

    /**
     * Swap in query dependency report from the placeholder component.
     */
    refreshQueryDependencies : function(){
        let dep = this.getComponent('lk-dependency-report');
        if (dep) {
            this.remove(dep);
        }

        let dependencies = this.formatDependencies();
        if (dependencies)
            this.add({
                xtype : 'box',
                itemId : 'lk-dependency-report',
                html : dependencies,
                listeners : {
                    afterrender : {
                        scope : this,
                        fn : function(cmp){
                            this.registerEventHandlers(cmp.getEl());
                        }
                    }
                }
            });
    },

    setQueryDetails : function(queryDetails) {
        this.queryDetails = queryDetails;
        this.renderQueryDetails();
    },

    toggleLookupRow : function(expando) {
        // get the field key from the expando
        var fieldKey = expando.getAttributeNS('', this.domProps.fieldKey),
            // get the row containing the expando
            trExpando = expando.findParentNode("tr", undefined, true),
            // if the next row is not the expanded fk col info, create it
            trNext = trExpando.next('tr');

        if (!trNext || !trNext.hasCls("lk-fk-" + fieldKey)) {
            var trNew = {
                tag: 'tr',
                cls: 'lk-fk-' + fieldKey,
                children: [{
                    tag: 'td',
                    cls: 'lk-qd-nested-container',
                    colspan: this.tableCols.length,
                    children: [{
                        tag: 'span',
                        html: 'loading...',
                        cls: 'lk-qd-loading'
                    }]
                }]
            };

            trNext = trExpando.insertSibling(trNew, 'after', false);

            var tdNew = trNext.down('td');
            this.cache.loadQueryDetails(this.schemaName, this.queryName, fieldKey, function(queryDetails) {
                tdNew.update('');
                tdNew.createChild(this.formatQueryColumns(queryDetails));
                this.registerEventHandlers(tdNew);
            }, function(errorInfo) {
                tdNew.update(this.displayError(errorInfo.exception()));
            }, this);
        }
        else {
            trNext.setDisplayed(!trNext.isDisplayed());
        }

        // update the image
        if (trNext.isDisplayed()) {
            trExpando.addCls("lk-qd-colrow-expanded");
            expando.set({ src: LABKEY.ActionURL.getContextPath() + '/_images/minus.gif' });
        }
        else {
            trExpando.removeCls("lk-qd-colrow-expanded");
            expando.set({ src: LABKEY.ActionURL.getContextPath() + '/_images/plus.gif' });
        }
    }
});
