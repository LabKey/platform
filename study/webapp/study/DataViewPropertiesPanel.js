/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.study.DataViewPropertiesPanel', {

    extend : 'Ext.form.Panel',

    constructor : function(config) {

        Ext4.applyIf(config, {
            border  : false,
            frame   : false,
            buttonAlign : 'left',
            fieldDefaults  : {
                labelWidth : 100,
                width      : 375,
                style      : 'padding: 4px 0',
                labelSeparator : ''
            }
        });

        // the optional data model record for exiting data views
        this.record = config.record || {};
        this.data = this.record.data || {};
        this.visibleFields = config.visibleFields || {};
        this.extraItems = config.extraItems || [];

        // define data models
        Ext4.define('Dataset.Browser.Category', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'created',      type : 'string'},
                {name : 'createdBy'                  },
                {name : 'displayOrder', type : 'int' },
                {name : 'label'                      },
                {name : 'modfied',      type : 'string'},
                {name : 'modifiedBy'                 },
                {name : 'rowid',        type : 'int' }
            ]
        });

        Ext4.define('LABKEY.data.User', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'userId',       type : 'int'},
                {name : 'displayName'               }
            ]
        });

        this.callParent([config]);
    },

    initComponent : function() {

        var formItems = [];

        formItems.push({
            xtype      : (this.data.name ? 'displayfield' : 'textfield'),
            allowBlank : false,
            name       : 'viewName',
            fieldLabel : 'Name',
            value      : this.data.name
        });

        if (this.visibleFields['author']) {
            formItems.push({
                xtype       : 'combo',
                fieldLabel  : 'Author',
                name        : 'author',
                store       : this.initializeUserStore(),
                editable    : false,
                value       : this.data.authorUserId,
                queryMode      : 'local',
                displayField   : 'displayName',
                valueField     : 'userId',
                emptyText      : 'Unknown'
            });
        }

        if (this.visibleFields['status']) {

            var statusStore = Ext4.create('Ext.data.Store', {
                fields: ['value', 'label'],
                data : [
                    {value: 'None', label: 'None'},
                    {value: 'Draft', label: 'Draft'},
                    {value: 'Final', label: 'Final'},
                    {value: 'Locked', label: 'Locked'},
                    {value: 'Unlocked', label: 'Unlocked'}
                ]
            });

            formItems.push({
                xtype       : 'combo',
                fieldLabel  : 'Status',
                name        : 'status',
                store       : statusStore,
                editable    : false,
                value       : this.data.status,
                queryMode      : 'local',
                displayField   : 'label',
                valueField     : 'value',
                emptyText      : 'Status'
            });
        }

        if (this.visibleFields['modifieddate']) {

            formItems.push({
                xtype       : 'datefield',
                fieldLabel  : "Date",
                name        : "modifiedDate",
                value       : this.data.modifiedDate != null && this.data.modifiedDate != '' ? new Date(this.data.modifiedDate) : '',
                blankText   : 'Modified Date',
                format      : 'Y-m-d',
                editable    : false
            });
        }

        if (this.visibleFields['datacutdate']) {

            formItems.push({
                xtype       : 'datefield',
                fieldLabel  : 'Data Cut Date',
                name        : 'refreshDate',
                value       : this.data.refreshDate != null && this.data.refreshDate != '' ? new Date(this.data.refreshDate) : '',
                blankText   : 'Date of last refresh',
                format      : 'Y-m-d',
                editable    : false
            });
        }

        if (this.visibleFields['category']) {

            formItems.push({
                xtype       : 'combo',
                fieldLabel  : 'Category',
                name        : 'category',
                store       : this.initializeCategoriesStore(),
                typeAhead   : true,
                hideTrigger : true,
                typeAheadDelay : 75,
                minChars       : 1,
                autoSelect     : false,
                queryMode      : 'remote',
                displayField   : 'label',
                valueField     : 'label',
                emptyText      : 'Uncategorized',
                listeners      : {
                    render : {fn : function(combo){combo.setRawValue(this.data.category);}, scope : this}
                }
            });
        }

        if (this.visibleFields['description']) {

            formItems.push({
                xtype      : 'textarea',
                fieldLabel : 'Description',
                name       : 'description',
                value      : this.data.description
            });
        }

        if (this.visibleFields['shared']) {

            formItems.push({
                xtype   : 'checkbox',
                value   : false,
                inputValue  : false,
                boxLabel    : 'Share this report with all users?',
                name        : "shared",
                fieldLabel  : "Shared",
                checked     : this.data.shared,
                listeners: {
                    change: function(cmp, newVal, oldVal){
                        cmp.inputValue = newVal;
                    }
                }
            },{
                xtype: 'hidden',
                name: "@shared"
            });
        }

        if (this.visibleFields['type']) {

            formItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Type',
                value      : this.data.type,
                readOnly   : true
            });
        }

        if (this.visibleFields['visibility']) {

            formItems.push({
                xtype      : 'radiogroup',
                fieldLabel : 'Visibility',
                items      : [{boxLabel : 'Visible',  name : 'hidden', checked : !this.data.hidden, inputValue : false},
                    {boxLabel : 'Hidden',   name : 'hidden', checked : this.data.hidden,  inputValue : true}]
            });
        }

        if (this.visibleFields['created'] && this.data.created) {

            formItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Created On',
                value      : this.data.created,
                readOnly   : true
            });
        }

        if (this.visibleFields['modified'] && this.data.modified) {

            formItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Last Modified',
                value      : this.data.modified,
                readOnly   : true
            });
        }

        this.items = formItems;
        this.items.push(this.extraItems);

        this.callParent([arguments]);
    },

    initializeUserStore : function() {

        var config = {
            model   : 'LABKEY.data.User',
            autoLoad: true,
            proxy   : {
                type   : 'ajax',
                url : LABKEY.ActionURL.buildURL('user', 'getUsers.api'),
                reader : {
                    type : 'json',
                    root : 'users'
                }
            }
        };

        return Ext4.create('Ext.data.Store', config);
    },

    initializeCategoriesStore : function(useGrouping) {

        var config = {
            pageSize: 100,
            model   : 'Dataset.Browser.Category',
            autoLoad: true,
            autoSync: false,
            proxy   : {
                type   : 'ajax',
                api    : {
                    create  : LABKEY.ActionURL.buildURL('study', 'saveCategories.api'),
                    read    : LABKEY.ActionURL.buildURL('study', 'getCategories.api'),
                    update  : LABKEY.ActionURL.buildURL('study', 'saveCategories.api'),
                    destroy : LABKEY.ActionURL.buildURL('study', 'deleteCategories.api')
                },
                extraParams : {
                    // These parameters are required for specific webpart filtering
                    pageId : this.pageId,
                    index  : this.index
                },
                reader : {
                    type : 'json',
                    root : 'categories'
                },
                writer: {
                    type : 'json',
                    root : 'categories',
                    allowSingle : false
                },
                listeners : {
                    exception : function(p, response, operations, eOpts)
                    {
                    }
                }
            },
            listeners : {
                load : function(s, recs, success, operation, ops) {
                    s.sort('displayOrder', 'ASC');
                }
            }
        };

        if (useGrouping)
            config["groupField"] = 'category';

        return Ext4.create('Ext.data.Store', config);
    }
});
