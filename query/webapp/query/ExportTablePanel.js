/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('HIPC.tree.ExportTablePanel', {
    
    extend: 'Ext.tree.Panel',

    treeStore: null,

    selectNodeAndAllChildren: function(node){
        node.set('checked', true);
        node.eachChild(this.selectNodeAndAllChildren);
    },

    deselectNodeAndAllChildren: function(node){
        node.set('checked', false);
        node.eachChild(this.deselectNodeAndAllChildren);
    },

    deselectNodeAndParents: function(node){
        // This function is used to deselect all parent nodes that are selected.
        node.set('checked', false);
        if(node.parentNode != null && node.parentNode.get('checked') === true){
            this.deselectNodeAndParents(node.parentNode);
        }
    },

    addParams: function(formPanel){
        var schemas = [];
        this.getRootNode().eachChild(function(node){

            var schemaName = node.get('text');
            var queryNames = [];

            node.eachChild(function(queryNode){
                if(queryNode.get('checked')){
                    queryNames.push(queryNode.get('text'));
                }
            });

            if(queryNames.length > 0){
                formPanel.add({xtype: 'hidden', name: schemaName, value: queryNames.join(';')});
            }
        });
    },

    getQueriesCallback: function(data){
        var node = {
            text: data.schemaName,
            expanded: false,
            checked: false,
            leaf: false,
            cls: 'folder',
            children: []
        };

        for(var i = 0; i < data.queries.length; i++){
            node.children.push({
                text: data.queries[i].name,
                checked: false,
                leaf: true
            });
        }

        this.getRootNode().appendChild(node);
    },

    getSchemasAndFillStore: function(){
        LABKEY.Query.getSchemas({
            apiVersion: 9.3,
            successCallback: function(schemaTree) {
                for(var schema in schemaTree){
                    LABKEY.Query.getQueries({
                        schemaName: schema,
                        includeColumns: false,
                        includeUserQueries: true,
                        successCallback: this.getQueriesCallback,
                        scope: this
                    });
                }
            },
            scope: this
        });
    },

    getTreeStore: function(){
        if(this.treeStore){
            return this.treeStore;
        }

        this.treeStore = Ext4.create('Ext.data.TreeStore', {
            proxy: {type: 'memory'},
            sorters: [{
                property: 'leaf',
                direction: 'ASC'
            }, {
                property: 'text',
                direction: 'ASC'
            }]
        });

        return this.treeStore
    },

    onExportButtonClicked: function(){
        var formPanel = Ext4.create('Ext.form.Panel', {
            standardSubmit: true,
            method: 'POST',
            url: LABKEY.ActionURL.buildURL('query', 'exportTables')
        });

        this.addParams(formPanel);

//        console.log(formPanel.getForm().getFields());
        formPanel.getForm().submit();
    },

    constructor: function(config){
        Ext4.applyIf(config, {
            width: 400,
            height: 500,
            useArrows: true
        });

        Ext4.apply(config, {
            rootVisible: false
        });

        this.callParent([config]);
    },

    initComponent: function(){

        this.rootNode = {
            text: '.'
        };

        this.store = this.getTreeStore();

        this.dockedItems = [{
            xtype: 'toolbar',
            items: {
                text: 'Export Selected',
                handler: this.onExportButtonClicked,
                scope: this
            }
        }];

        this.on('checkchange', function(node, checked){
            if(checked){
                node.eachChild(this.selectNodeAndAllChildren);
            } else {
                node.eachChild(this.deselectNodeAndAllChildren);

                if(node.parentNode != null){
                    this.deselectNodeAndParents(node.parentNode);
                }
            }
        });

        this.getSchemasAndFillStore();

        this.callParent();
    }
});