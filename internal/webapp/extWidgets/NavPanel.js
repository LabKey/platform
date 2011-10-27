/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4ClientAPI();
LABKEY.requiresScript('/extWidgets/ImportWizard.js');

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
 *          html: '<a href="'+item.url+'">'+item.name+'</a>',
 *          style: 'padding-left:5px;'
 *      }
 *  }


@example

    new LABKEY.ext.NavMenu({
        renderTo: 'vlDiv',
        width: 350,
        renderer: function(item){
            return {
                html: '<div style="float:left;width:250px;">'+item.name+':</div> [<a href="'+LABKEY.ActionURL.buildURL('laboratory', 'AssaySearchPanel', null, {schemaName: item.schemaName, queryName: item.queryName})+'">Search</a>] [<a href="'+LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: item.schemaName, 'query.queryName': item.queryName})+'">Browse Records</a>]',
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
        Ext4.each(this.sections, function(sectionCfg){
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
                    title: sectionCfg.header + ':',
                    cls: 'labkey-wp-header',
                    style: 'margin-bottom:5px;font-weight:bold;' //border-bottom:solid;
                }]
            });

            for (var j=0;j<sectionCfg.items.length;j++)
            {
                var item = {};
                var renderer;
                if(sectionCfg.items[j].renderer)
                    renderer = sectionCfg.items[j].renderer;
                else if (sectionCfg.renderer)
                    renderer = sectionCfg.renderer;
                else if(this.renderer)
                    renderer = this.renderer;

                if(!renderer)
                    renderer = this.renderers.defaultRenderer;

                if(Ext4.isString(renderer))
                    renderer = this.renderers[renderer];

                item = renderer(sectionCfg.items[j]);

                section.add(item);
            }

            section.add({tag: 'p'});
        }, this);
    },
    renderers: {
        assayRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: {
                    style: 'padding-left:5px',
                    border: false,
                    target: '_self',
                    linkPrefix: '[',
                    linkSuffix: ']'
                },
                items: [{
                    tag: 'div',
                    html: item.name+':',
                    width: 250
                },{
                    xtype: 'labkey-linkbutton',
                    href: LABKEY.ActionURL.buildURL('query', 'SearchPanel', null, {schemaName: item.schemaName, 'queryName': item.queryName}),
                    text: 'Search'
                },{
                    xtype: 'labkey-linkbutton',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: item.schemaName, 'query.queryName': item.queryName}),
                    text: 'Browse Records'
                },{
                    xtype: 'labkey-linkbutton',
                    text: 'Import Data',
                    hidden: item.showImport===false,
                    assayId: item.id,
                    useSimpleImport: item.simpleImport,
                    handler: function(btn){
                        if(btn.useSimpleImport)
                            window.location = LABKEY.ActionURL.buildURL('assay', 'moduleAssayUpload', null, {rowId: btn.assayId});
                        else
                            Ext4.create('LABKEY.ext.ImportWizardWin', {
                                controller: 'assay',
                                action: 'moduleAssayUpload',
                                urlParams: {
                                    rowId: btn.assayId
                                }
                            }).show();
                    }
                }],
                style: 'padding-bottom:8px'
            };
        },
        defaultRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: {
                    style: 'padding-left:5px',
                    border: false,
                    target: '_self'
                },
                items: [{
                    xtype: 'labkey-linkbutton',
                    text: item.name,
                    href: item.url,
                    showBrackets: false
                }],
                style: 'padding-bottom:8px'
             }
        },
        queryRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: {
                    style: 'padding-left:5px',
                    border: false,
                    target: '_self',
                    linkPrefix: '[',
                    linkSuffix: ']'
                },
                items: [{
                    tag: 'div',
                    html: item.name+':',
                    width: 250
                },{
                    xtype: 'labkey-linkbutton',
                    href: LABKEY.ActionURL.buildURL('query', 'SearchPanel', null, {schemaName: item.schemaName, 'queryName': item.queryName}),
                    text: 'Search'
                },{
                    xtype: 'labkey-linkbutton',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: item.schemaName, 'query.queryName': item.queryName}),
                    text: 'Browse Records'
                },{
                    xtype: 'labkey-linkbutton',
                    text: 'Import Data',
                    hidden: item.showImport===false,
                    useSimpleImport: item.simpleImport,
                    assayId: item.id,
                    params: {schemaName: item.schemaName, queryName: item.queryName},
                    handler: function(btn){
                        if(btn.useSimpleImport)
                            window.location = LABKEY.ActionURL.buildURL('query', 'importData', null, btn.params);
                        else
                            Ext4.create('LABKEY.ext.ImportWizardWin', {
                                controller: 'query',
                                action: 'importData',
                                urlParams: btn.params
                            }).show();
                    }
                }],
                style: 'padding-bottom:8px'
            };
        },
        fileRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: {
                    style: 'padding-left:5px',
                    border: false,
                    target: '_self',
                    linkPrefix: '[',
                    linkSuffix: ']'
                },
                items: [{
                    tag: 'div',
                    html: item.name+':',
                    width: 250
                },{
                    xtype: 'labkey-linkbutton',
                    href: LABKEY.ActionURL.buildURL('query', 'SearchPanel', null, {schemaName: item.schemaName, 'queryName': item.queryName}),
                    text: 'Search'
                },{
                    xtype: 'labkey-linkbutton',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: item.schemaName, 'query.queryName': item.queryName}),
                    text: 'Browse Files'
                },{
                    xtype: 'labkey-linkbutton',
                    text: 'Import Data',
                    hidden: item.showImport===false,
                    useSimpleImport: item.simpleImport,
                    handler: function(btn){
                        if(btn.useSimpleImport)
                            window.location = LABKEY.ActionURL.buildURL('query', 'importData', null, btn.params);
                        else
                            Ext4.create('LABKEY.ext.ImportWizardWin', {
                                controller: 'filecontent',
                                action: 'begin'
                            }).show();
                    }
                }],
                style: 'padding-bottom:8px'
            };
        }
}
});
