/*
 * Copyright (c) 2015-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.internal.ViewDesigner.model.FieldKey', {

    extend: 'Ext.data.Model',

    fields: [
        {
            name: 'id',
            mapping: 'fieldKey',
            convert : function(fieldKey, rec) {
                if (Ext4.isString(fieldKey)) {
                    return fieldKey.toUpperCase();
                }

                if (rec && rec.raw && Ext4.isString(rec.raw.fieldKey)) {
                    return rec.raw.fieldKey.toUpperCase();
                }

                throw new Error('LABKEY.internal.ViewDesigner.model.FieldKey: unable to generate id due to missing fieldKey.');
            }
        },
        {name: 'fieldKey'}
    ],

    statics: {
        getById: function(id) {
            var _id;
            if (Ext4.isString(id)) {
                _id = id.toUpperCase();
            }
            else {
                _id = id;
            }

            return this.callParent([_id]);
        }
    }
});

Ext4.define('LABKEY.internal.ViewDesigner.store.FieldKey', {
    extend: 'Ext.data.Store',

    model: 'LABKEY.internal.ViewDesigner.model.FieldKey',

    remoteSort: true,

    getById: function(id) {
        var _id;
        if (Ext4.isString(id)) {
            _id = id.toUpperCase();
        }
        else {
            _id = id;
        }

        return this.callParent([_id]);
    }
});

Ext4.define('LABKEY.internal.ViewDesigner.tab.BaseTab', {

    extend: 'Ext.panel.Panel',

    layout: 'fit',

    baseTitle: '',
    
    border: false,

    customView: undefined,

    designer: undefined,

    fieldMetaStore: undefined,

    constructor : function(config) {

        Ext4.apply(this, {
            designer: config.designer,
            customView: config.customView,
            fieldMetaStore: config.fieldMetaStore
        });

        this.callParent([config]);

        this.addEvents('beforetooltipshow', 'recordremoved');
    },

    initComponent : function() {

        this.items = this.getBaseItems();

        this.callParent();
        
        var list = this.getList();

        list.on('beforetooltipshow', this.onListBeforeToolTipShow, this);
        list.on('beforeitemclick', this.onListBeforeClick, this);
        list.on('render', function() {
            this.tooltip = Ext4.create('Ext.tip.ToolTip',{
                renderTo: Ext4.getBody(),
                target: this.getEl(),
                delegate: '.item-caption',
                trackMouse: true,
                listeners: {
                    beforeshow: function(qt) {
                        var el = Ext4.fly(qt.triggerElement).up(this.itemSelector);
                        if (!el)
                            return false;
                        var record = this.getRecord(el.dom);
                        return this.fireEvent('beforetooltipshow', this, qt, record, el);
                    },
                    scope: this
                }
            });
        }, this.getList(), {single: true});
    },

    setShowHiddenFields : Ext4.emptyFn,

    isDirty : function() {
        return false;
    },

    revert : Ext4.emptyFn,

    validate : Ext4.emptyFn,

    save : function(edited, urlParameters, properties) {
        var store = this.getList().getStore();

        var writer = Ext4.create('Ext.data.writer.Json', {
            encode: false,
            writeAllFields: true
        });

        var root = store.getProxy().getReader().root,
            isSessionView = Ext4.isObject(properties) ? properties.session : false;

        edited[root] = [];
        urlParameters[root] = [];

        store.each(function(r) {
            // keep the records added via the url, regardless of session view or saved view
            if (r.get('urlParameter')) {
                urlParameters[root].push(writer.getRecordData(r, {action: 'create'}));
            }

            // only convert url records to the save object if this is not a session based view save
            if (!isSessionView || !r.get('urlParameter')) {
                edited[root].push(writer.getRecordData(r, {action: 'create'}));
            }
        });
    },

    hasField : Ext4.emptyFn,
    
    getBaseItems : function() {
        var items = [];
        if (Ext4.isString(this.baseTitle)) {
            items.push(this.getTitlePanel());
        }
        if (Ext4.isString(this.baseTitleDescription)) {
            items.push(this.getTitleDescriptionPanel());
        }
        items.push(this.getList());

        return [{
            xtype: 'panel',
            border: false,
            cls: 'labkey-customview-panel',
            layout: {
                type: 'vbox',
                align: 'stretch'
            },
            items: items,
            dockedItems: this.getSubDockedItems()
        }];
    },
    
    getSubDockedItems : function() {
        return undefined;
    },

    getTitlePanel : function() {
        if (!this.titlePanel) {
            this.titlePanel = Ext4.create('Ext.Component', {
                cls: 'labkey-customview-title',
                html: Ext4.String.htmlEncode(this.baseTitle)
            });
        }

        return this.titlePanel;
    },

    getTitleDescriptionPanel : function() {
        if (!this.titleDescriptionPanel) {
            this.titleDescriptionPanel = Ext4.create('Ext.Component', {
                cls: 'labkey-customview-description',
                html: Ext4.String.htmlEncode(this.baseTitleDescription)
            });
        }

        return this.titleDescriptionPanel;
    },

    /** Get the listview for the tab. */
    getList : function() {
        throw new Error(this.$className + ' must extend getList() and provide the ListView for this tab');
    },

    onListBeforeClick : function(list, record, item, index, e) {
        var node = list.getNode(index);
        if (node) {
            var target = e.getTarget();
            if (target.className.indexOf("labkey-tool") > -1) {
                var classes = ("" + target.className).split(" ");
                for (var j = 0; j < classes.length; j++) {
                    var cls = classes[j].trim();
                    if (cls.indexOf("labkey-tool-") == 0) {
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

    onToolClose : function(index) {
        this.removeRecord(index);
        return false;
    },


    getFieldMetaRecord : function(fieldKey) {
        return this.fieldMetaStore.getById(fieldKey);
    },

    onListBeforeToolTipShow : function(list, qt, record) {
        if (record) {
            var fieldKey = record.get('fieldKey');
            var fieldMetaRecord = this.getFieldMetaRecord(fieldKey);
            var html;
            if (fieldMetaRecord) {
                html = fieldMetaRecord.getToolTipHtml();
            }
            else {
                html = "<table><tr><td><strong>Field not found:</strong></td></tr><tr><td>" + Ext4.htmlEncode(fieldKey) + "</td></tr></table>";
            }
            qt.update(html);
        }
        else {
            qt.update("<strong>No field found</strong>");
        }
    },

    // subclasses may override this to provide a better default
    createDefaultRecordData : function(fieldKey) {
        return {fieldKey: fieldKey};
    },

    addRecord : function(fieldKey) {
        var list = this.getList();
        var defaultData = this.createDefaultRecordData(fieldKey);
        var record = new list.store.model(defaultData);
        var listSelection = list.getSelectionModel().getSelection();

        if (Ext4.isEmpty(listSelection)) {
            list.store.add([record]);
        }
        else {
            var index = list.getStore().indexOf(listSelection[listSelection.length-1]);
            list.store.insert(index+1, record);
        }

        return record;
    },

    getRecordIndex : function(fieldKeyOrIndex) {
        var list = this.getList();
        var index = -1;
        if (Ext4.isNumber(fieldKeyOrIndex)) {
            index = fieldKeyOrIndex;
        }
        else {
            index = list.store.find('fieldKey', fieldKeyOrIndex, 0, false, false);
        }
        return index;
    },

    getRecord : function(fieldKeyOrIndex) {
        var index = this.getRecordIndex(fieldKeyOrIndex);
        if (index > -1) {
            return this.getList().getStore().getAt(index);
        }
        return null;
    },

    removeRecord : function(fieldKeyOrIndex) {
        var index = this.getRecordIndex(fieldKeyOrIndex),
            store = this.getList().getStore(),
            record = store.getAt(index);

        if (record) {
            // remove from the store and select sibling
            store.removeAt(index);
            var i = index < store.getCount() ? index : index - 1;
            if (i > -1) {
                this.getList().select(i);
            }

            this.fireEvent('recordremoved', record.get('id'));
        }
    },

    addDataViewDragDrop : function(view, groupName) {
        var viewTop, viewBottom;

        new Ext4.dd.DragZone(view.getEl(), {
            ddGroup: groupName,
            repairHighlightColor: 'ffffff',

            getDragData: function(e) {
                var sourceEl = e.getTarget(view.itemSelector);
                if (sourceEl) {
                    var d = Ext4.get(sourceEl).select('.item-caption').elements[0].cloneNode(true);
                    d.id = Ext4.id();
                    d.style = 'font-weight: bold; width: 300px;';
                    return {
                        view: view,
                        ddel: d,
                        sourceEl: sourceEl,
                        repairXY: Ext4.fly(sourceEl).getXY(),
                        records: [view.getRecord(sourceEl)]
                    }
                }
            },

            getRepairXY: function() {
                return this.dragData.repairXY;
            },

            /* Handle scroll on drag above or below */
            beforeDragOut: function(target, e, id) {
                if (!viewTop) {
                    viewTop = view.getEl().getY();
                }
                if (!viewBottom) {
                    viewBottom = viewTop + view.getEl().getHeight();
                }

                var y = e.getY();
                if (y <= viewTop) {
                    view.getEl().scrollBy(0, -50, true);
                }
                else if (y >= viewBottom) {
                    view.getEl().scrollBy(0, 50, true);
                }
            }
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
