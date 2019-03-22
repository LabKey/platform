/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
// http://www.sencha.com/forum/showthread.php?79210-ComponentDataView-Ext-components-inside-a-dataview-or-listview

Ext4.define('Ext.ux.ComponentDataView', {

    extend : 'Ext.view.View',

    alias : 'widget.compdataview',

    defaultType: 'textfield',

    initComponent : function(){
        this.callParent();
        this.components = [];
    },
    refresh : function(){
        Ext4.destroy(this.components);
        this.components = [];
        this.callParent();
        this.renderItems(0, this.store.getCount() - 1);
    },
    onUpdate : function(ds, record){
        var index = ds.indexOf(record);
        if(index > -1){
            this.destroyItems(index);
        }
        this.callParent([ds, record]);
        if(index > -1){
            this.renderItems(index, index);
        }
    },
    onAdd : function(ds, records, index){
        var count = this.all.getCount();
        this.callParent([ds, records, index]);
        if(count !== 0){
            this.renderItems(index, index + records.length - 1);
        }
    },
    onRemove : function(ds, record, index){
        this.destroyItems(index);
        this.callParent([ds, record, index]);
    },
    onDestroy : function(){
        this.callParent();
        Ext4.destroy(this.components);
        this.components = [];
    },

    renderItem : function (rootNode, item, node, record, index)
    {
        var c = item.render ? item.cloneConfig() : Ext4.ComponentManager.create(item, this.defaultType);

        if (c.renderTarget) {
            node = Ext4.DomQuery.is(node, c.renderTarget) ? node : Ext4.DomQuery.selectNode(c.renderTarget, node);
            c.render(node);
        }
        else if (c.applyTarget) {
            node = Ext4.DomQuery.is(node, c.applyTarget) ? node : Ext4.DomQuery.selectNode(c.applyTarget, node);
            c.applyToMarkup(node);
        }
        else {
            c.render(node);
        }

        if (Ext4.isFunction(c.setRecord)) {
            c.setRecord(record, index);
        }

        if (!Ext4.isDefined(index)) {

            if (Ext4.isFunction(c.setValue) && c.applyValue) {
                c.setValue(record.get(c.applyValue));
                c.on('blur', function(f){
                    var record = this.dataView.getRecord(this.node);
                    record.data[this.dataIndex] = f.getValue();
                }, {node: rootNode, dataView: this, dataIndex: c.applyValue});
            }
        }
        else
        {
            // XXX: generalize setting index values on blur. See LABKEY.ext.FilterOpCombo and LABKEY.ext.FilterTextValue
        }

        return c;
    },

    renderItems : function(startIndex, endIndex){
        var ns = this.all.elements;
        var args = [startIndex, 0];
        for(var i = startIndex; i <= endIndex; i++){
            var r = args[args.length] = [];
            for(var items = this.items, j = 0, len = items.length, c; j < len; j++){

                var item = items[j];
                var record = this.store.getAt(i);

                var query = item.renderTarget || item.applyTarget;
                if (query)
                {
                    if (item.indexedProperty)
                    {
                        Ext4.each(Ext4.query(query, ns[i]), function (node, index) {
                            r.push(this.renderItem(ns[i], item, node, record, index));
                        }, this);
                    }
                    else
                        r.push(this.renderItem(ns[i], item, ns[i], record));
                }
                else
                {
                    r.push(this.renderItem(ns[i], item, ns[i], record));
                }
            }
        }
        this.components.splice.apply(this.components, args);
    },
    destroyItems : function(index){
        Ext4.destroy(this.components[index]);
        this.components.splice(index, 1);
    },
    /**
     * Finds the index of the passed node.
     * @param nodeInfo {HTMLElement/String/Number/Record} nodeInfo An HTMLElement template node, index of a template node, the id of a template node, a component member of a template node
     * or a record associated with a node.
     * @return {HTMLElement} The node or null if it wasn't found
     */
    // Extends DataView.getNode to allow one of the Components in the item's view as the 'nodeInfo' parameter.
    getNode : function (nodeInfo) {
        if (nodeInfo instanceof Ext4.Component) {
            var n = nodeInfo.getEl();
            return n.findParentNode(this.itemSelector, 20);
        }
        return this.callParent([nodeInfo]);
    },
    /**
     * Get array of component members for the passed node.
     * @param node {HTMLElement/String/Number/Record} nodeInfo An HTMLElement template node, index of a template node, the id of a template node, a component member of a template node.
     * or a record associated with a node.
     */
    getComponents : function (node) {
        for (var i = 0; i < this.components.length; i++) {
            if (this.components[i].indexOf(node) > -1) {
                return this.components[i];
            }
        }

        return undefined;
    }
});
