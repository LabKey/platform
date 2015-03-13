
// This TreeLoader returns TreeNodes for field metadata and is backed by a FieldMetaStore.
LABKEY.ext.FieldTreeLoader = Ext.extend(Ext.tree.TreeLoader, {

    constructor : function (config)
    {
        if (!config.createNodeConfigFn) {
            throw new Error("need a FieldMetaRecord->TreeNode fn");
        }
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
        if (this.fireEvent("beforeload", this, node, callback) !== false)
        {
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

        } else
        {
            // if the load is cancelled, make sure we notify
            // the node that we are done
            this.runCallback(callback, scope || node, []);
        }
    },

    // create a new TreeNode from the record.
    createNode : function (fieldMetaRecord) {
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

            for (var groupedByMemberKey in groupedByMember)
            {
                if (groupedByMemberKey === null) {
                    continue;
                }

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
        if (crosstabColumnMember && columns.length > 1)
        {
            var n = this.createCrosstabMemberNode(crosstabColumnMember);
            node.appendChild(n);
            node = n;
        }

        for (var i = 0, len = columns.length; i < len; i++)
        {
            var n = this.createNode(columns[i]);
            if(n) {
                node.appendChild(n);
            }
        }
    },

    // Group the columns by member
    columnsByMember: function (fieldMetaRecords) {
        var groupedByMember = {};

        for (var i = 0; i < fieldMetaRecords.length; i++)
        {
            var fieldMetaRecord = fieldMetaRecords[i];
            var groupedByMemberKey = null;
            var crosstabColumnMember = fieldMetaRecord.get('crosstabColumnMember');
            if (crosstabColumnMember)
            {
                groupedByMemberKey = crosstabColumnMember.dimensionFieldKey + "~" + crosstabColumnMember.value + "~" + crosstabColumnMember.caption;
            }

            var groupedByMemberColumns = groupedByMember[groupedByMemberKey];
            if (groupedByMemberColumns === undefined) {
                groupedByMember[groupedByMemberKey] = groupedByMemberColumns = [];
            }
            groupedByMemberColumns.push(fieldMetaRecord);
        }

        return groupedByMember;
    }
});