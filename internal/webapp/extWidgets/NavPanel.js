/*
 * Copyright (c) 2010-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4ClientAPI();

Ext4.namespace('LABKEY.ext');

/**
 * This class extends Ext.panel.Panel.  The intent is to provide a flexible helper to create data-driven navigation.
 * @class LABKEY.ext.NavPanel
 *
 * @param (array) sections
 * @param (string) secions.header
 * @param (array)secions.items
 * @param (function) renderer Optional. This function will be called on each item in the section.  It should return on Ext config object that wil be added to the panel.  If not provided, a default render will create the row.
 * function(item){
 *      return {
 *          html: '<a href="'+tmp.items[j].url+'">'+tmp.items[j].name+'</a>',
 *          style: 'padding-left:5px;'
 *      }
 *  }


@example

    new LABKEY.ext.NavMenu({
        renderTo: 'vlDiv',
        width: 350,
        renderer: function(item){
            return {
                html: '<div style="float:left;width:250px;">'+item.name+':</div> [<a href="'+LABKEY.ActionURL.buildURL('laboratory', 'AssaySearchPanel', null, {schemaName: item.schemaName, queryName: item.queryName})+'">Search</a>] [<a href="'+LABKEY.ActionURL.buildURL('query', 'executeQuery', container, {schemaName: item.schemaName, 'query.queryName': item.queryName})+'">View All Records</a>]',
                style: 'padding-left:5px;padding-bottom:8px'
            }
        },
        sections: [{
            header: 'Viral Loads',
            items: [
                {name: 'Samples', schemaName: 'laboratory', 'queryName':'Inventory'},
                {name: 'VL Assay Types', schemaName: 'Viral_Load_Assay', 'queryName':'Assays'}
            ]}
        ]
    });

 **/




Ext4.define('LABKEY.ext.NavPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widgets.NavPanel',
    initComponent: function(){
        //calculate size
        var maxHeight = this.maxHeight || 15;

        var size = 0;
        Ext4.each(this.sections, function(i){
            //for the header
            size++;
            size += i.items.length;
        }, this);

        var columns = Math.ceil(size / maxHeight);

        Ext4.apply(this, {
            border: false,
            frame: false,
            frameHeader: false,
            width: this.width || '80%',
            defaults: {
                border: false,
                style: 'background-color: transparent;',
                bodyStyle: 'background-color: transparent;'
            }
        });

        this.callParent(arguments);

        var section;
        Ext4.each(this.sections, function(tmp){
            section = this.add({
                xtype: 'panel',
                cls: 'labkey-navmenu',
                frame: false,
                //frameHeader: false,
                width: this.colWidth,
                style: 'padding-bottom:15px;padding-right:10px;background-color: transparent;',
                bodyStyle: 'background-color: transparent;',
                defaults: {
                    border: false
                },
                items: [{
                    dock: 'top',
                    xtype: 'header',
                    title: tmp.header + ':',
                    cls: 'labkey-wp-header',
                    style: 'margin-bottom:5px;font-weight:bold;' //border-bottom:solid;
                }]
            });

            for (var j=0;j<tmp.items.length;j++){
                var item;
                if(this.renderer){
                    item = this.renderer(tmp.items[j])
                }
                else {
                   item = {
                        html: '<a href="'+tmp.items[j].url+'">'+tmp.items[j].name+'</a>',
                        style: 'padding-left:5px;'
                    }
                }
                section.add(item)
            }

            section.add({tag: 'p'});
        }, this);
    }
});
