Ext4.define('LABKEY.internal.ViewDesigner.tab.BaseTab', {

    extend: 'Ext.panel.Panel',

    constructor : function (config) {
        this.designer = config.designer;
        this.unstyled = true;

        var mainPanel = config.items[0];
        mainPanel.tools = [{
            handler: function () {
                this.designer.close();
            },
            scope: this
        }];

        this.callParent([config]);
    },

    initComponent : function () {
        this.callParent();

        this.getList().on('selectionchange', this.onListSelectionChange, this);
        this.getList().on('render', function (list) {
            this.addEvents("beforetooltipshow");
            this.tooltip = Ext4.create('Ext.tip.ToolTip',{
                renderTo: Ext4.getBody(),
                target: this.getEl(),
                delegate: ".item-caption",
                trackMouse: true,
                listeners: {
                    beforeshow: function (qt) {
                        var el = Ext4.fly(qt.triggerElement).up(this.itemSelector);
                        if (!el)
                            return false;
                        var record = this.getRecord(el.dom);
                        return this.fireEvent("beforetooltipshow", this, qt, record, el);
                    },
                    scope: this
                }
            });
        }, this.getList(), {single: true});
        this.getList().on('beforetooltipshow', this.onListBeforeToolTipShow, this);
        this.getList().on('beforeitemclick', this.onListBeforeClick, this);
    },

    setShowHiddenFields : Ext4.emptyFn,

    isDirty : function () {
        return false;
    },

    revert : Ext4.emptyFn,

    validate : Ext4.emptyFn,

    save : function (edited, urlParameters) {
        var store = this.getList().getStore();

        var writer = Ext4.create('Ext.data.writer.Json', {
            encode: false,
            writeAllFields: true
        });

        var root = store.getProxy().getReader().root;
        edited[root] = [];
        urlParameters[root] = [];

        store.each(function (r) {
            if (r.data.urlParameter) {
                urlParameters[root].push(writer.getRecordData(r, {action: 'create'}));
            }
            else {
                edited[root].push(writer.getRecordData(r, {action: 'create'}));
            }
        });
    },

    hasField : Ext4.emptyFn,

    /** Get the listview for the tab. */
    getList : Ext4.emptyFn,

    onListBeforeClick : function (list, record, item, index, e, eOpts) {
        var node = list.getNode(index);
        if (node)
        {
            var target = e.getTarget();
            if (target.className.indexOf("labkey-tool") > -1)
            {
                var classes = ("" + target.className).split(" ");
                for (var j = 0; j < classes.length; j++)
                {
                    var cls = classes[j].trim();
                    if (cls.indexOf("labkey-tool-") == 0)
                    {
                        var toolName = cls.substring("labkey-tool-".length);
                        var fnName = "onTool" + toolName.charAt(0).toUpperCase() + toolName.substring(1);
                        if (this[fnName]) {
                            return this[fnName].call(this, index, item, e);
                        }
                    }
                }
            }
        }
        return true;
    },

    onToolClose : function (index) {
        this.removeRecord(index);
        return false;
    },


    getFieldMetaRecord : function (fieldKey) {
        return this.fieldMetaStore.getById(fieldKey.toUpperCase());
    },

    onListBeforeToolTipShow : function (list, qt, record)
    {
        if (record)
        {
            var fieldKey = record.data.fieldKey;
            var fieldMetaRecord = this.getFieldMetaRecord(fieldKey);
            var html;
            if (fieldMetaRecord) {
                html = fieldMetaRecord.getToolTipHtml();
            }
            else {
                html = "<table><tr><td><strong>Field not found:</strong></td></tr><tr><td>" + Ext4.util.Format.htmlEncode(fieldKey) + "</td></tr></table>";
            }
            qt.update(html);
        }
        else {
            qt.update("<strong>No field found</strong>");
        }
    },

    onListSelectionChange : function (list, selections) {
    },

    // subclasses may override this to provide a better default
    createDefaultRecordData : function (fieldKey) {
        return {fieldKey: fieldKey};
    },

    addRecord : function (fieldKey) {
        var list = this.getList();
        var defaultData = this.createDefaultRecordData(fieldKey);
        //var record = new list.store.recordType(defaultData);
        var record = new list.store.model(defaultData);
        var selected = list.getSelectedNodes();
        //var selected = list.getSelectedIndexes();
        if (Ext4.isEmpty(selected)) {
            list.store.add([record]);
        }
        else {
            //var index = Ext4.Array.max(selected);
            //list.store.insert(index+1, record);
            list.store.insert(0, record);
        }
        return record;
    },

    getRecordIndex : function (fieldKeyOrIndex) {
        var list = this.getList();
        var index = -1;
        if (Ext4.isNumber(fieldKeyOrIndex)) {
            index = fieldKeyOrIndex;
        }
        else {
            index = list.store.find("fieldKey", fieldKeyOrIndex, 0, false, false);
        }
        return index;
    },

    getRecord : function (fieldKeyOrIndex) {
        var index = this.getRecordIndex(fieldKeyOrIndex);
        if (index > -1) {
            return this.getList().store.getAt(index);
        }
        return null;
    },

    removeRecord : function (fieldKeyOrIndex) {
        var index = this.getRecordIndex(fieldKeyOrIndex);
        var record = this.getList().store.getAt(index);
        if (record)
        {
            // remove from the store and select sibling
            this.getList().store.removeAt(index);
            var i = index < this.getList().store.getCount() ? index : index - 1;
            if (i > -1) {
                this.getList().select(i);
            }

            // uncheck the field tree
            // TODO reenable after conversion to Ext4
            //var upperFieldKey = record.data.fieldKey.toUpperCase();
            //var treeNode = this.designer.fieldsTree.getRootNode().findChildBy(function (node) {
            //    return node.attributes.fieldKey.toUpperCase() == upperFieldKey;
            //}, null, true);
            //if (treeNode) {
            //    treeNode.getUI().toggleCheck(false);
            //}
        }
    },

    addDataViewDragDop : function(view, groupName) {
        new Ext4.view.DragZone({
            view: view,
            ddGroup: groupName,
            dragText: 'Reorder selected'
        });

        new Ext4.view.DropZone({
            view: view,
            ddGroup: groupName,
            handleNodeDrop : function(data, record, position) {
                var store = data.view.getStore();
                if (data.copy)
                {
                    var records = data.records;
                    data.records = [];
                    for (var i = 0; i < records.length; i++) {
                        data.records.push(records[i].copy(records[i].getId()));
                    }
                }
                else {
                    data.view.store.remove(data.records, data.view === view);
                }

                var index = store.indexOf(record);
                if (position !== 'before') {
                    index++;
                }
                store.insert(index, data.records);
                view.getSelectionModel().select(data.records);
            }
        });
    }

});
