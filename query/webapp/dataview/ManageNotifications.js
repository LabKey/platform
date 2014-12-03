/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext4.ManageReportNotifications', {

    getManageReportPanel: function(config, categories, returnUrl, notifyOption) {

        if (!Ext4.ModelManager.isRegistered('CategoryNotifications')) {
            Ext4.define('CategoryNotifications', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'label', type: 'string'},
                    {name: 'rowid', type: 'int'},
                    {name: 'subscribed', type: 'boolean'}
                ]
            });
        }
        var categoryStore = Ext4.create('Ext.data.Store', {
            model : 'CategoryNotifications',
            data  : categories
        });

        if (null == returnUrl)
            returnUrl = LABKEY.ActionURL.buildURL('project', 'begin', null, {'pageId' : 'study.DATA_ANALYSIS'});

        var gridConfig = {
            margin   : '10',
            disabled : 'select' != notifyOption,
            store    : categoryStore,
            maxWidth : 706,
            pageId: -1,
            index: -1,
            border   : false, frame: false,
            scroll   : 'vertical',
            columns  : [{
                text     : 'Category',
                flex     : 1,
                sortable : false,
                hideable : false,
                dataIndex: 'label',
                tpl      : '{label:htmlEncode}'
            },{
                text     : 'Subscribe',
                xtype    : 'checkcolumn',
                width    : 100,
                align    : 'center',
                sortable : false,
                hideable : false,
                flex     : 0,
                dataIndex: 'subscribed'
            }],
            cls       : 'iScroll', // webkit custom scroll bars
            selType   : 'rowmodel',
            scope     : this
        };

        var gridPanel = Ext4.create('Ext.grid.Panel', gridConfig);

        var panelConfig = {
            maxWidth : 726,
            title : 'Choose Notification Option',
            frame: false,
            items : [
                {
                    xtype  : 'radiogroup',
                    id     : 'notifyOption',
                    columns: 1,
                    margin : '4, 4, 4, 6',
                    items  : [
                        {
                            boxLabel  : 'None.',
                            name      : 'rb',
                            inputValue: 'none',
                            checked   : 'none' == notifyOption
                        },
                        {
                            boxLabel  : 'All. Your daily digest will list changes and additions to all reports and datasets.',
                            name      : 'rb',
                            inputValue: 'all',
                            checked   : 'all' == notifyOption
                        },
                        {
                            boxLabel  : 'By category. Your daily digest will list changes and additions to reports and datasets in the subscribed categories.',
                            name      : 'rb',
                            inputValue: 'select',
                            checked   : 'select' == notifyOption
                        }
                    ],
                    listeners : {
                        change : function(button, newValue) {
                            if ('select' != newValue.rb)
                                gridPanel.disable();
                            else
                                gridPanel.enable();
                        },
                        scope : this
                    }
                },
                gridPanel
            ],
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent;',
                margin: '2 0 0 0',
                items: [{
                    type: 'button',
                    text: 'Save',
                    handler: function ()
                    {
                        var categories = this.getStoreCategories(categoryStore);
                        var notifyOption = panel.getComponent('notifyOption').getValue().rb;
                        Ext4.Ajax.request({
                            url     : LABKEY.ActionURL.buildURL('reports', 'saveCategoryNotifications.api'),
                            method  : 'POST',
                            jsonData: {
                                'categories'   : categories,
                                'notifyOption' : notifyOption
                            },
                            headers : {'Content-Type' : 'application/json'},
                            success : function(response) {
                                window.location = returnUrl;
                            },
                            failure : function(resp) {
                                var o;
                                try {
                                    o = Ext4.decode(resp.responseText);
                                }
                                catch (error) {

                                }
                            },
                            scope   : this
                        });

                    },
                    scope: this
                },
                    {
                        type: 'button',
                        text: 'Cancel',
                        handler: function ()
                        {
                            window.location = returnUrl;
                        },
                        scope: this
                    }],
                scope: this
            }],
            scope: this
        };

        Ext4.applyIf(panelConfig, config);
        var panel = Ext4.create('Ext.panel.Panel', panelConfig);
        return panel;
    },

    getStoreCategories: function(store) {
        var categories = [];
        Ext4.Array.each(store.getRange(), function(category, index) {
            categories.push({'rowid' : category.getData().rowid, 'subscribed' : category.getData().subscribed});
        }, this);
        return categories;
    }

});