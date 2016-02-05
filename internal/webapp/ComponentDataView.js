/*
 * Copyright (c) 2010-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
// http://www.sencha.com/forum/showthread.php?79210-ComponentDataView-Ext-components-inside-a-dataview-or-listview

Ext.ns('Ext.ux');
Ext.ux.ComponentDataView = Ext.extend(Ext.DataView, {
    defaultType: 'textfield',
    initComponent : function(){
        Ext.ux.ComponentDataView.superclass.initComponent.call(this);
        this.components = [];
    },
    refresh : function(){
        Ext.destroy(this.components);
        this.components = [];
        Ext.ux.ComponentDataView.superclass.refresh.call(this);
        this.renderItems(0, this.store.getCount() - 1);
    },
    onUpdate : function(ds, record){
        var index = ds.indexOf(record);
        if(index > -1){
            this.destroyItems(index);
        }
        Ext.ux.ComponentDataView.superclass.onUpdate.apply(this, arguments);
        if(index > -1){
            this.renderItems(index, index);
        }
    },
    onAdd : function(ds, records, index){
        var count = this.all.getCount();
        Ext.ux.ComponentDataView.superclass.onAdd.apply(this, arguments);
        if(count !== 0){
            this.renderItems(index, index + records.length - 1);
        }
    },
    onRemove : function(ds, record, index){
        this.destroyItems(index);
        Ext.ux.ComponentDataView.superclass.onRemove.apply(this, arguments);
    },
    onDestroy : function(){
        Ext.ux.ComponentDataView.superclass.onDestroy.call(this);
        Ext.destroy(this.components);
        this.components = [];
    },
    
    renderItem : function (rootNode, item, node, record, index)
    {
        var c = item.render ?
                item.cloneConfig() :
                Ext.create(item, this.defaultType);

        if(c.renderTarget){
            node = Ext.DomQuery.is(node, c.renderTarget) ? node : Ext.DomQuery.selectNode(c.renderTarget, node);
            c.render(node);
        }else if(c.applyTarget){
            node = Ext.DomQuery.is(node, c.applyTarget) ? node : Ext.DomQuery.selectNode(c.applyTarget, node);
            c.applyToMarkup(node);
        }else{
            c.render(node);
        }

        if (Ext.isFunction(c.setRecord)) {
            c.setRecord(record, index);
        }

        if (index === undefined)
        {
            if(Ext.isFunction(c.setValue) && c.applyValue){
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
                        Ext.each(Ext.query(query, ns[i]), function (node, index) {
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
        Ext.destroy(this.components[index]);
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
        if (nodeInfo instanceof Ext.Component) {
            var n = nodeInfo.getEl();
            return n.findParentNode(this.itemSelector, 20);
        }
        return Ext.ux.ComponentDataView.superclass.getNode.call(this, nodeInfo);
    },
    /**
     * Get array of component members for the passed node.
     * @param node {HTMLElement/String/Number/Record} nodeInfo An HTMLElement template node, index of a template node, the id of a template node, a component member of a template node.
     * or a record associated with a node.
     */
    getComponents : function (node) {
        var index = this.indexOf(node);
        if (index > -1)
            return this.components[index];
        return undefined;
    }
});
Ext.reg('compdataview', Ext.ux.ComponentDataView);
