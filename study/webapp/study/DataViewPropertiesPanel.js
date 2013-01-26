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
                width      : 400,
                style      : 'margin: 0px 0px 10px 0px',
                labelSeparator : ''
            }
        });

        // the optional data model record for exiting data views
        this.record = config.record || {};
        this.data = this.record.data || {};
        this.visibleFields = config.visibleFields || {};
        this.extraItems = config.extraItems || [];
        this.disableShared = config.disableShared;

        // define data models
        if (!Ext4.ModelManager.isRegistered('Dataset.Browser.Category')) {
            Ext4.define('Dataset.Browser.Category', {
                extend : 'Ext.data.Model',
                fields : [
                    {name : 'created',      type : 'string'},
                    {name : 'createdBy'                  },
                    {name : 'displayOrder', type : 'int' },
                    {name : 'label'                      },
                    {name : 'modfied',      type : 'string'},
                    {name : 'modifiedBy'                 },
                    {name : 'rowid',        type : 'int' },
                    {name : 'subCategories' },
                    {name : 'parent',       type : 'int' }
                ]
            });
        }

        if (!Ext4.ModelManager.isRegistered('LABKEY.data.User')) {
            Ext4.define('LABKEY.data.User', {
                extend : 'Ext.data.Model',
                fields : [
                    {name : 'userId',       type : 'int'},
                    {name : 'displayName'               }
                ]
            });
        }

        this.callParent([config]);
    },

    initComponent : function() {

        var formItems = [];

        formItems.push({
            xtype      : 'textfield',
            allowBlank : false,
            name       : 'viewName',
            fieldLabel : 'Name',
            value      : this.data.name
        });

        if (this.visibleFields['author']) {
            var authorField = Ext4.create('Ext.form.field.ComboBox', {
                fieldLabel: 'Author',
                name: 'author',
                typeAhead: true,
                typeAheadDelay : 75,
                editable: true, // required for typeAhead
                forceSelection : true, // user must pick from list
                store       : this.initializeUserStore(),
                value       : this.data.authorUserId ? this.data.authorUserId : null,
                queryMode : 'local',
                displayField : 'DisplayName',
                valueField : 'UserId',
                emptyText: 'None'
            });

            // since forceSelection is true  we must set the initial value
            // after the store is loaded
            authorField.store.on('load', function() {
                authorField.setValue(authorField.initialConfig.value);
            });

            formItems.push(authorField);
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
                editable    : true,
                forceSelection : true,
                typeAhead   : true,
                typeAheadDelay : 75,
                value       : this.data.status != null && this.data.status != '' ? this.data.status : undefined,
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

            console.log(this.data);
            formItems.push({
                xtype       : 'combo',
                fieldLabel  : 'Category',
                name        : 'category',
                store       : this.initializeCategoriesStore(),
                typeAhead   : true,
                typeAheadDelay : 75,
                minChars       : 1,
                autoSelect     : false,
                queryMode      : 'remote',
                displayField   : 'label',
                valueField     : 'rowid',
                emptyText      : 'Uncategorized',
                listeners      : {
                    render : function(combo){

                        // The record must be set from the store in order to save correctly
                        combo.getStore().on('load', function(s) {
                            if (this.data && this.data.category) {
                                var rec = s.findExact('rowid', this.data.category.rowid);
                                if (rec >= 0) {
                                    combo.setValue(s.getAt(rec));
                                }
                            }
                        }, this, {single: true});

                    },
                    scope : this
                },
                tpl : new Ext4.XTemplate(
                    '<ul><tpl for=".">',
                        '<li role="option" class="x4-boundlist-item">',
                            '<tpl if="parent &gt; -1">',
                                '<span style="padding-left: 20px;">{label:htmlEncode}</span>',
                            '</tpl>',
                            '<tpl if="parent &lt; 0">',
                                '<span>{label:htmlEncode}</span>',
                            '</tpl>',
                        '</li>',
                    '</tpl></ul>'
                )
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
            var sharedName = "shared";

            if (this.disableShared) {
                // be sure to roundtrip the original shared value
                // since we are disabling the checkbox
                formItems.push({
                    xtype : 'hidden',
                    name  : "shared",
                    value : this.data.shared
                });

                // rename the disabled checkbox
                sharedName = "hiddenShared"
            }

            formItems.push({
                xtype   : 'checkbox',
                inputValue  : this.data.shared,
                checked     : this.data.shared,
                boxLabel    : 'Share this report with all users?',
                name        : sharedName,
                fieldLabel  : "Shared",
                disabled    : this.disableShared,
                uncheckedValue : false,
                listeners: {
                    change: function(cmp, newVal, oldVal){
                        cmp.inputValue = newVal;
                    }
                }
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

        if (this.visibleFields['created'] && this.data.created) {

            formItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Created',
                value      : Ext4.util.Format.date(this.data.created, 'Y-m-d H:i'),
                readOnly   : true
            });
        }

        if (this.visibleFields['modified'] && this.data.modified) {

            formItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Modified',
                value      : Ext4.util.Format.date(this.data.modified, 'Y-m-d H:i'),
                readOnly   : true
            });
        }
                                                   
        if (this.visibleFields['customThumbnail']) {
            formItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Thumbnail',
                value      : '<div class="thumbnail"><img src="' + this.data.thumbnail + '"/></div>',
                readOnly   : true
            });

            if (this.data.allowCustomThumbnail)
            {
                formItems.push({
                    xtype      : 'filefield',
                    id         : 'customThumbnail',
                    name       : 'customThumbnail',
                    fieldLabel : 'Change Thumbnail',
                    msgTarget  : 'side',
                    validator  : function(value) {
                        value = value.toLowerCase();
                        if (value != null && value.length > 0 && !(/\.png$/.test(value) || /\.jpg$/.test(value) || /\.jpeg$/.test(value) || /\.gif$/.test(value) || /\.svg$/.test(value)))
                            return "Please choose a PNG, JPG, GIF, or SVG image.";
                        else
                            return true;
                    },
                    listeners  : {
                        afterrender : function(cmp) {
                            Ext4.tip.QuickTipManager.register({
                                target: cmp.getId(),
                                text: 'Choose a PNG, JPG, GIF, or SVG image. Images will be scaled to 250 pixels high.'
                            });
                        },
                        change : function(cmp, value) {
                            this.down('#customThumbnailFileName').setValue(value);
                        },
                        scope: this
                    }
                });

                // hidden form field for the custom thumbnail file name
                formItems.push({
                    xtype : 'hidden',
                    id    : 'customThumbnailFileName',
                    name  : 'customThumbnailFileName',
                    value : null
                });
            }
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
                    s.insert(0, {UserId : -1, DisplayName : 'None'});
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
                    index  : this.index || 0
                },
                reader : {
                    type : 'json',
                    root : 'categories'
                },
                writer: {
                    type : 'json',
                    root : 'categories',
                    allowSingle : false
                }
            },
            inRawLoad : false,
            listeners : {
                load : function(s, recs) {

                    if (!s.inRawLoad) {
                        s.inRawLoad = true;
                        var parents = {}, keys = [],
                            newRecs = [], labels = [], r, d, p, u;

                        for (r=0; r < recs.length; r++) {
                            if (recs[r].get('parent') == -1) {
                                parents[recs[r].get('rowid')] = {record : recs[r], subs : []};
                                keys.push(recs[r].get('rowid'));
                            }
                        }

                        for (r=0; r < recs.length; r++) {
                            if (recs[r].get('parent') >= 0) {
                                parents[recs[r].get('parent')].subs.push(recs[r]);
                            }
                        }

                        for (p=0; p < keys.length; p++) {
                            d = parents[keys[p]].record.data;
                            newRecs.push(d);
                            labels.push(parents[keys[p]].record.get('label'));
                            for (u=0; u < parents[keys[p]].subs.length; u++) {
                                d = parents[keys[p]].subs[u].data;
                                newRecs.push(d);
                                labels.push(parents[keys[p]].subs[u].get('label'));
                            }
                        }

                        s.loadData(newRecs);
                    }

                    s.inRawLoad = false;
                }
            }
        };

        if (useGrouping) {
            config["groupField"] = 'category';
        }

        return Ext4.create('Ext.data.Store', config);
    }
});
