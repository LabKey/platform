/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
        }
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

        this.items = [{
            xtype: 'box',
            itemId: 'loader',
            autoEl: {
                tag: 'p',
                cls: 'lk-qd-loading',
                html: 'Loading...'
            }
        }];

        this.callParent();

        var loader = function(queryDetails) {
            if (queryDetails) {
                this.queryDetails = queryDetails;
            }
            if (this.rendered && this.queryDetails) {
                this.setQueryDetails(this.queryDetails);
            }
        };

        this.on('afterrender', function() { loader.call(this); }, this, {single: true});
        // Callback function scope will be the cache, so stash for future use
        this.cache.loadQueryDetails(this.schemaName, this.queryName, this.fk, function(queryDetails) {
            loader.call(this, queryDetails);
        }, function(errorInfo) {
            this.getEl().update(this.displayError('Error in query: ' + errorInfo.exception));
        }, this);
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
                if (attr.negate ? !col[attrName] : col[attrName]) {
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

        if (queryDetails.isUserDefined && queryDetails.moduleName) {
            var _tip = '' +
                    '<div>' +
                        '<div><span>Module Defined Query</span></div>' +
                        '<div>This query is defined in an external module. Externally defined queries are not editable.</div>' +
                    '</div>';
            children.push({
                tag: 'span',
                'data-qtip': _tip,
                style: 'cursor: default;',
                html: 'Defined in ' + Ext4.htmlEncode(queryDetails.moduleName) + ' module'
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

        if (!queryDetails.exception) {
            children.push(this.formatQueryLink('executeQuery', params, 'view data', undefined, queryDetails.viewDataUrl));
        }

        if (queryDetails.isUserDefined) {
            if (queryDetails.canEdit && !queryDetails.isInherited) {
                if (LABKEY.Security.currentUser.isAdmin) {
                    children.push(this.formatQueryLink("sourceQuery", params, "edit source"));
                    children.push(this.formatQueryLink("propertiesQuery", params, "edit properties"));
                    children.push(this.formatQueryLink("deleteQuery", params, "delete query"));
                }
                children.push(this.formatQueryLink("metadataQuery", params, "edit metadata"));
            }
            else {
                children.push(this.formatQueryLink('viewQuerySource', params, 'view source'));
            }
        }
        else {
            if (LABKEY.Security.currentUser.isAdmin) {
                if (queryDetails.createDefinitionUrl) {
                    children.push(this.formatQueryLink(null, null, 'create definition', undefined, queryDetails.createDefinitionUrl));
                }
                else if (queryDetails.editDefinitionUrl) {
                    children.push(this.formatQueryLink(null, null, 'edit definition', undefined, queryDetails.editDefinitionUrl));
                }

                if (queryDetails.isMetadataOverrideable) {
                    children.push(this.formatQueryLink('metadataQuery', params, 'edit metadata'));
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
