/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext4.ReportNotificationPanel', {

    extend: 'Ext.panel.Panel',

    frame: false,

    initComponent: function() {

        this.items = [{
            xtype  : 'radiogroup',
            id     : 'notifyOption',
            columns: 1,
            margin : '4, 4, 4, 6',
            items  : [{
                    boxLabel  : 'None.',
                    name      : 'rb',
                    inputValue: 'none',
                    checked   : 'none' == this.notifyOption
                },{
                    boxLabel  : 'All. Your daily digest will list changes and additions to all reports and datasets.',
                    name      : 'rb',
                    inputValue: 'all',
                    checked   : 'all' == this.notifyOption
                },{
                    boxLabel  : 'By category. Your daily digest will list changes and additions to reports and datasets in the subscribed categories.',
                    name      : 'rb',
                    inputValue: 'category',
                    checked   : 'category' == this.notifyOption
                },{
                    boxLabel  : 'By dataset. Your daily digest will list changes and additions to subscribed datasets.',
                    name      : 'rb',
                    inputValue: 'dataset',
                    checked   : 'dataset' == this.notifyOption
                }
            ],
            listeners : {
                change : function(button, newValue) {this.updateGrid(newValue.rb);},
                scope : this
            }
        }];

        this.items.push({
            xtype    : 'gridpanel',
            margin   : '10',
            store    : this.getSubscriptionStore(),
            maxWidth : 706,
            itemId   : 'selection-grid',
            border   : false,
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
        });

        this.dockedItems = [{
            xtype: 'toolbar',
            dock: 'bottom',
            ui: 'footer',
            style: 'background-color: transparent;',
            margin: '2 0 0 0',
            items: [{
                type: 'button',
                text: 'Save',
                handler: function () {
                    var selections = this.getSelections(this.getSubscriptionStore());
                    var notifyOption = this.getComponent('notifyOption').getValue().rb;
                    Ext4.Ajax.request({
                        url     : LABKEY.ActionURL.buildURL('reports', 'saveNotificationSettings.api'),
                        method  : 'POST',
                        jsonData: {
                            'selections'   : selections,
                            'notifyOption' : notifyOption
                        },
                        headers : {'Content-Type' : 'application/json'},
                        success : function(response) {
                            window.location = this.returnUrl;
                        },
                        failure : LABKEY.Utils.displayAjaxErrorResponse,
                        scope   : this
                    });
                },
                scope: this
            }, {
                type: 'button',
                text: 'Cancel',
                handler: function () {
                    window.location = this.returnUrl;
                },
                scope: this
            }],
            scope: this
        }];

        this.on('render', function(cmp){this.updateGrid(this.notifyOption);}, this);
        this.callParent(arguments);
    },

    getSubscriptionStore : function() {
        if (!Ext4.ModelManager.isRegistered('LABKEY.NotificationConfigModel')) {
            Ext4.define('LABKEY.NotificationConfigModel', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'label', type: 'string'},
                    {name: 'rowid', type: 'int'},
                    {name: 'subscribed', type: 'boolean'}
                ]
            });
        }

        if (!this.subscriptionStore) {
            this.subscriptionStore = Ext4.create('Ext.data.Store', {
                model : 'LABKEY.NotificationConfigModel'
            });
        }
        return this.subscriptionStore;
    },

    updateGrid : function(value) {
        var cmp = this.getComponent('selection-grid');
        if (cmp) {
            var enabled = value === 'category' || value === 'dataset';
            cmp.setDisabled(!enabled);

            if (value === 'category') {
                this.getSubscriptionStore().loadData(this.categories);
                label = 'Category';
            }
            else if (value === 'dataset') {
                this.getSubscriptionStore().loadData(this.datasets);
                label = 'Dataset'
            }

            // update the header label
            if (enabled)
                cmp.columns[0].setText(label);
        }
        else
            console.warn('The grid component could not be found.');
    },

    getSelections : function(store) {
        var selections = [];
        var cmp = this.getComponent('selection-grid');

        // no need for the selections if the grid is disabled
        if (cmp && !cmp.isDisabled()) {
            var recs = store.query('subscribed', true);
            recs.each(function(item, idx){
                selections.push(item.get('rowid'));
            });
        }
        return selections;
    }
});