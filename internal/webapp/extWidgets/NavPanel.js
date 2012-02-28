/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
                html: '<div style="float:left;width:250px;">'+item.name+':</div> [<a href="'+LABKEY.ActionURL.buildURL('laboratory', 'AssaySearchPanel', null, {schemaName: item.schemaName, queryName: item.queryName})+'">Search</a>] [<a href="'+LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: item.schemaName, 'query.queryName': item.queryName})+'">Browse All</a>]',
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
                frame: false,
                width: this.colWidth,
                style: 'padding-right:10px;background-color: transparent;',
                bodyStyle: 'background-color: transparent;',
                defaults: {
                    border: false,
                    cls: 'labkey-wiki'
                },
                items: [{
                    dock: 'top',
                    xtype: 'header',
                    title: sectionCfg.header + ':',
                    style: 'margin-bottom:5px;font-weight:bold;'
                }]
            });

            for (var j=0;j<sectionCfg.items.length;j++)
            {
                var item = {};
                var renderer = null;
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

            section.add({tag: 'p', style: 'padding-bottom: 15px;'});
        }, this);
    },
    renderers: {
        assayRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: {
                    style: 'padding-left:5px;padding-bottom:5px;',
                    border: false,
                    target: '_self',
                    linkCls: 'labkey-text-link'
                },
                items: [{
                    tag: 'div',
                    html: item.name+':',
                    width: 250
                },{
                    xtype: 'labkey-linkbutton',
                    href: LABKEY.ActionURL.buildURL('query', 'SearchPanel', null, {schemaName: item.schemaName, 'queryName': item.queryName, defaultContainerFilter: 'CurrentAndSubfolders'}),
                    text: 'Search'
                },{
                    xtype: 'labkey-linkbutton',
                    href: function(item){
                        var params = {rowId: item.id}; //schemaName: item.schemaName, 'query.queryName': item.queryName,
                        params[item.name+'.containerFilterName'] = 'CurrentAndSubfolders';
                        return LABKEY.ActionURL.buildURL('assay', 'assayResults', null, params)
                    }(item),
                    text: 'Browse All'
                },{
                    xtype: 'labkey-linkbutton',
                    text: 'Import Data',
                    hidden: item.showImport===false  || !LABKEY.Security.currentUser.canInsert,
                    assayId: item.id,
                    useSimpleImport: item.simpleImport,
                    urlParams: {rowId: item.id, srcURL: LABKEY.ActionURL.buildURL('project', 'begin')},
                    importAction: item.importAction || 'moduleAssayUpload',
                    importController: item.importController || 'assay',
                    handler: function(btn){
                        if(btn.useSimpleImport)
                            window.location = LABKEY.ActionURL.buildURL(btn.importController, btn.importAction, null, btn.urlParams);
                        else
                            Ext4.create('LABKEY.ext.ImportWizardWin', {
                                controller: btn.importController,
                                action: btn.importAction,
                                urlParams: btn.urlParams,
                                workbookFolderType: 'Expt Workbook'
                            }).show();
                    }
                }]
            };
        },
        defaultRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: {
                    style: 'padding-left:5px;padding-bottom:5px;',
                    border: false,
                    target: '_self',
                    linkCls: 'labkey-text-link'
                },
                items: [{
                    xtype: 'labkey-linkbutton',
                    text: item.name,
                    href: item.url || LABKEY.ActionURL.buildURL(item.controller, item.action, null, item.urlParams),
                    showBrackets: false
                }]
             }
        },
        queryRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: {
                    style: 'padding-left:5px;padding-bottom:5px;',
                    border: false,
                    target: '_self',
                    linkCls: 'labkey-text-link'
                },
                items: [{
                    tag: 'div',
                    html: item.name+':',
                    width: 250
                },{
                    xtype: 'labkey-linkbutton',
                    href: LABKEY.ActionURL.buildURL('query', 'SearchPanel', null, {schemaName: item.schemaName, 'queryName': item.queryName, 'defaultContainerFilter': 'CurrentAndSubfolders'}),
                    text: 'Search'
                },{
                    xtype: 'labkey-linkbutton',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: item.schemaName, 'query.queryName': item.queryName, 'query.containerFilterName': 'CurrentAndSubfolders'}),
                    text: 'Browse All'
                },{
                    xtype: 'labkey-linkbutton',
                    text: 'Import Data',
                    hidden: item.showImport===false || !LABKEY.Security.currentUser.canInsert,
                    useSimpleImport: item.simpleImport,
                    assayId: item.id,
                    urlParams: {schemaName: item.schemaName, queryName: item.queryName, srcURL: LABKEY.ActionURL.buildURL('project', 'begin')},
                    handler: function(btn){
                        if(btn.useSimpleImport)
                            window.location = LABKEY.ActionURL.buildURL('query', 'importData', null, btn.urlParams);
                        else
                            Ext4.create('LABKEY.ext.ImportWizardWin', {
                                controller: 'query',
                                action: 'importData',
                                urlParams: btn.urlParams,
                                workbookFolderType: 'Expt Workbook'
                            }).show();
                    }
                }]
            };
        },
        sampleSetRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: {
                    style: 'padding-left:5px;padding-bottom:5px;',
                    border: false,
                    target: '_self',
                    linkCls: 'labkey-text-link'
                },
                items: [{
                    tag: 'div',
                    html: item.name+':',
                    width: 250
                },{
                    xtype: 'labkey-linkbutton',
                    href: LABKEY.ActionURL.buildURL('query', 'SearchPanel', null, {schemaName: item.schemaName, 'queryName': item.queryName, 'defaultContainerFilter': 'CurrentAndSubfolders'}),
                    text: 'Search'
                },{
                    xtype: 'labkey-linkbutton',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: item.schemaName, 'query.queryName': item.queryName, 'query.containerFilterName': 'CurrentAndSubfolders'}),
                    text: 'Browse All'
                },{
                    xtype: 'labkey-linkbutton',
                    text: 'Import Data',
                    hidden: item.showImport===false || !LABKEY.Security.currentUser.canInsert,
                    assayId: item.id,
                    urlParams: {schemaName: item.schemaName, queryName: item.queryName, name: item.queryName, importMoreSamples: true},
                    handler: function(btn){
                        window.location = LABKEY.ActionURL.buildURL('experiment', 'showUploadMaterials', null, btn.urlParams);
                    }
                }]
            };
        },
        fileRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: {
                    style: 'padding-left:5px;padding-bottom:5px;',
                    border: false,
                    target: '_self'
                },
                items: [{
                    tag: 'div',
                    html: item.name+':',
                    width: 250
                },{
                    xtype: 'labkey-linkbutton',
                    href: LABKEY.ActionURL.buildURL('query', 'SearchPanel', null, {schemaName: item.schemaName, 'queryName': item.queryName, 'defaultContainerFilter': 'CurrentAndSubfolders'}),
                    text: 'Search'
                },{
                    xtype: 'labkey-linkbutton',
                    href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: item.schemaName, 'query.queryName': item.queryName, 'query.containerFilterName': 'CurrentAndSubfolders'}),
                    text: 'Browse All'
                },{
                    xtype: 'labkey-linkbutton',
                    text: 'Import Data',
                    hidden: item.showImport===false || !LABKEY.Security.currentUser.canInsert,
                    useSimpleImport: item.simpleImport,
                    handler: function(btn){
                        if(btn.useSimpleImport)
                            window.location = LABKEY.ActionURL.buildURL('query', 'importData', null, btn.urlParams);
                        else
                            Ext4.create('LABKEY.ext.ImportWizardWin', {
                                controller: 'project',
                                action: 'begin',
                                urlParams: btn.urlParams,
                                workbookFolderType: 'Expt Workbook'
                            }).show();
                    }
                }]
            };
        }
}
});
