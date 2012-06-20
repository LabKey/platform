/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.study.DataViewPropertiesPanel', {

    extend : 'Ext.form.Panel',

    constructor : function(config) {

        Ext4.applyIf(config, {
            border  : false,
            frame   : false,
            autoScroll : true,
            cls     : 'iScroll', // webkit custom scroll bars
            buttonAlign : 'left',
            dateFormat  : 'Y-m-d',
            fieldDefaults  : {
                labelWidth : 120,
                width      : 375,
                style      : 'margin: 0px 0px 10px 0px',
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
                displayField   : 'DisplayName',
                valueField     : 'UserId',
                emptyText      : 'None'
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
                format      : this.dateFormat,
                editable    : false
            });
        }

        if (this.visibleFields['datacutdate']) {

            formItems.push({
                xtype       : 'datefield',
                fieldLabel  : 'Data Cut Date',
                name        : 'refreshDate',
                value       : this.data.refreshDate != null  ? this.data.refreshDate : '',
                blankText   : 'Date of last refresh',
                format      : this.dateFormat,
                editable    : true,
                altFormats  : LABKEY.Utils.getDateAltFormats()
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
                inputValue  : this.data.shared,
                checked     : this.data.shared,
                boxLabel    : 'Share this report with all users?',
                name        : "shared",
                fieldLabel  : "Shared",
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
                value      : this.data.dataType,
                readOnly   : true
            });
        }

        if (this.visibleFields['visible']) {

            formItems.push({
                xtype      : 'radiogroup',
                fieldLabel : 'Visibility',
                items      : [
                    {boxLabel : 'Visible',  name : 'visible', checked : this.data.visible, inputValue : true},
                    {boxLabel : 'Hidden',   name : 'visible', checked : !this.data.visible,  inputValue : false}
                ]
            });
        }
                                                   
        if (this.visibleFields['customThumbnail']) {
            formItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Thumbnail',
                value      : '<div class="thumbnail"><img src="' + this.data.thumbnail + '"/></div>',
                readOnly   : true
            });

            // TODO: see todo from ReportViewProvider.updateProperties, this should be possible for any report type
            if (this.record.get('type') == "Time Chart" || this.record.get('type') == "R View")
                formItems.push({
                    xtype      : 'filefield',
                    id         : 'customThumbnail',
                    name       : 'customThumbnail',
                    fieldLabel : 'Replace Thumbnail',
                    msgTarget  : 'side',
                    validator  : function(value) {
                        value = value.toLowerCase();
                        if (value != null && value.length > 0 && !(/\.png$/.test(value) || /\.jpg$/.test(value) || /\.jpeg$/.test(value) || /\.gif$/.test(value)))
                            return "Please choose a PNG, JPG, or GIF image.";
                        else
                            return true;
                    },
                    listeners  : {
                        afterrender : function(cmp) {
                            Ext4.tip.QuickTipManager.register({
                                target: cmp.getId(),
                                text: 'Choose a PNG, JPG, or GIF image. Images will be scaled to 250 pixels high.'
                            });
                        }
                    }
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

        Ext4.each(this.extraItems, function(item) {
            this.items.push(item);
        }, this);

        this.callParent([arguments]);
    },

    initializeUserStore : function() {

        var config = {
            model   : 'LABKEY.data.User',
            autoLoad: true,
            pageSize: 10000,
            proxy   : {
                type   : 'ajax',
                url : LABKEY.ActionURL.buildURL('query', 'selectRows.api'),
                extraParams : {
                    schemaName  : 'core',
                    queryName   : 'UsersMsgPrefs'
                },
                reader : {
                    type : 'json',
                    root : 'rows'
                }
            },
            listeners : {
                load : function(s, recs, success, operation, ops) {
                    s.sort('DisplayName', 'ASC');
                    s.insert(0, {UserId : -1, DisplayName : 'None '});
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
