/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
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
 * @example
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
        Ext4.QuickTips.init({
            constrainPosition: true
        });

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

                item = renderer.call(this, sectionCfg.items[j]);

                section.add(item);
            }

            section.add({tag: 'span', style: 'padding-bottom: 15px;'});
        }, this);
    },
    renderers: {
        assayRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: this.ITEM_DEFAULTS,
                items: [
                    this.getLabelItemCfg(item)
                ,
                    this.getSearchItemCfg(item)
                ,
                    this.getBrowseItemCfg(item, {
                        href: LABKEY.ActionURL.buildURL('assay', 'assayResults', null, {rowId: item.id})
                    })
                ,
                    this.getImportItemCfg(item, {
                        assayId: item.id,
                        urlParams: {rowId: item.id, srcURL: LABKEY.ActionURL.buildURL('project', 'begin')},
                        importAction: item.importAction || 'moduleAssayUpload',
                        importController: item.importController || 'assay',
                        tooltip: item.importTooltip || 'Click to import data into this assay'
                    })
                ]
            };
        },
        defaultRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: this.ITEM_DEFAULTS,
                items: [{
                    xtype: 'labkey-linkbutton',
                    style: this.ITEM_STYLE_DEFAULT,
                    text: item.name,
                    href: item.url || LABKEY.ActionURL.buildURL(item.controller, item.action, null, item.urlParams),
                    tooltip: item.tooltip,
                    showBrackets: false
                }]
             }
        },
        queryRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: this.ITEM_DEFAULTS,
                items: [
                    this.getLabelItemCfg(item)
                ,
                    this.getSearchItemCfg(item)
                ,
                    this.getBrowseItemCfg(item)
                ,
                    this.getImportItemCfg(item, {
                        urlParams: {schemaName: item.schemaName, queryName: item.queryName, srcURL: LABKEY.ActionURL.buildURL('project', 'begin')},
                        importAction: 'importData',
                        importController: 'query'
                    })
                ]
            };
        },
        sampleSetRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: this.ITEM_DEFAULTS,
                items: [
                    this.getLabelItemCfg(item)
                ,
                    this.getSearchItemCfg(item)
                ,
                    this.getBrowseItemCfg(item)
                ,
                    this.getImportItemCfg(item, {
                        urlParams: {schemaName: item.schemaName, queryName: item.queryName, name: item.queryName, importMoreSamples: true},
                        importAction: 'showUploadMaterials',
                        importController: 'experiment'
                    })
                ]
            };
        },
        fileRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: this.ITEM_DEFAULTS,
                items: [
                    this.getLabelItemCfg(item)
                ,
                    this.getSearchItemCfg(item)
                ,
                    this.getBrowseItemCfg(item)
                ,
                    this.getImportItemCfg(item)
                ]
            };
        },

        workbookRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: this.ITEM_DEFAULTS,
                items: [
                    this.getLabelItemCfg(item)
                ,
                    this.getSearchItemCfg(item)
                ,
                    this.getBrowseItemCfg(item, {
                        tooltip: item.browseTooltip || 'Click to display a table of all workbooks'
                    })
                ,
                    this.getImportItemCfg(item, {
                        xtype: 'labkey-linkbutton',
                        text: 'Create New Workbook',
                        hidden: !LABKEY.Security.currentUser.canInsert,
                        tooltip: 'Click to create a new workbook',
                        importWizardConfig: {
                            canAddToExistingExperiment: false,
                            title: 'Create Workbook'
                        },
                        target: '_self',
                        importAction: 'begin',
                        importController: 'project'
                    })
                ]
             }
        },

        summaryCountRenderer: function(item){
            return {
                layout: 'hbox',
                defaults: this.ITEM_DEFAULTS,
                items: [
                    this.getLabelItemCfg(item)
                ,{
                    xtype: 'labkey-linkbutton',
                    linkCls: 'labkey-text-link-noarrow',
                    tooltip: 'Click to view these records',
                    href: item.queryName ? LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: item.schemaName, queryName: item.queryName}): null,
                    text: item.total
                }]
            }
        }
    },
    getSearchItemCfg: function(item, config){
        config = config || {};
        return Ext4.apply({
            xtype: 'labkey-linkbutton',
            tooltip: item.searchTooltip || 'Click to display a search panel',
            href: LABKEY.ActionURL.buildURL('query', 'SearchPanel', null, {schemaName: item.schemaName, 'queryName': item.queryName, defaultContainerFilter: 'CurrentAndSubfolders'}),
            text: 'Search'
        }, config);
    },
    getBrowseItemCfg: function(item, config){
        config = config || {};
        return Ext4.apply({
            xtype: 'labkey-linkbutton',
            text: item.browseTooltip || 'Browse All',
            tooltip: 'Click to display a table of all records',
            href: LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: item.schemaName, 'query.queryName': item.queryName}) //, 'query.containerFilterName': 'CurrentAndSubfolders'
        }, config);
    },
    getImportItemCfg: function(item, config){
        config = config || {};
        return Ext4.apply({
            xtype: 'labkey-linkbutton',
            text: 'Import Data',
            tooltip: item.importTooltip || 'Click to import new data',
            hidden: item.showImport===false || !LABKEY.Security.currentUser.canInsert,
            useSimpleImport: item.simpleImport,
            handler: function(btn){
                if(btn.useSimpleImport)
                    window.location = LABKEY.ActionURL.buildURL(btn.importController, btn.importAction, null, btn.urlParams);
                else {
                    var wizardCfg = Ext4.apply({
                        controller: btn.importController,
                        action: btn.importAction,
                        urlParams: btn.urlParams,
                        workbookFolderType: 'Expt Workbook',
                        title: btn.importTitle || 'Import Data'
                    }, config.importWizardConfig);
                    Ext4.create('LABKEY.ext.ImportWizardWin', wizardCfg).show();

                }
            }
        }, config);
    },
    getLabelItemCfg: function(item, config){
        config = config || {};
        return Ext4.apply({
            tag: 'div',
            style: this.ITEM_STYLE_DEFAULT,
            html: '<span' + (item.description ? ' data-qtip="'+Ext4.htmlEncode(item.description)+'"' : '') + '>' + (item.name || item.queryName) + ':' + '</span>',
            width: 250
        }, config);

    }
});

Ext4.apply(LABKEY.ext.NavPanel.prototype, {
    ITEM_STYLE_DEFAULT: 'padding: 2px;'
});

Ext4.apply(LABKEY.ext.NavPanel.prototype, {
    ITEM_DEFAULTS: {
        bodyStyle: LABKEY.ext.NavPanel.prototype.ITEM_STYLE_DEFAULT,
        style: 'padding-right: 8px;',
        border: false,
        target: '_self',
        linkCls: 'labkey-text-link'
    }
});

/**
 * A plugin which allows menu items to have tooltips.
 */
Ext4.define('LABKEY.ext.MenuQuickTips', {
    alias: 'plugin.menuqtips',
    init: function (menu) {
        menu.items.each(function (item) {
            if (typeof (item.qtip) != 'undefined')
                item.on('afterrender', function (menuItem) {
                    var qtip = typeof (menuItem.qtip) == 'string'
                                ? {text: menuItem.qtip}
                                : menuItem.qtip;
                    qtip = Ext4.apply(qtip, {target: menuItem.getEl().getAttribute('id')});
                    Ext4.QuickTips.register(qtip);
                });
        });
    }
});
