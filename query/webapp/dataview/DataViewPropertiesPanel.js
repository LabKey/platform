/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.ext4.DataViewPropertiesPanel', {

    extend  : 'Ext.form.Panel',
    alias   : 'widget.dvproperties',
    alternateClassName : ['LABKEY.study.DataViewPropertiesPanel'],

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
//                minWidth   : 500,
//                maxWidth   : 450,
//                width      : 400,
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

        this.callParent([config]);
    },

    initComponent : function() {

        var formItems = [];

        formItems.push({
            xtype      : 'textfield',
            allowBlank : false,
            name       : 'viewName',
            readOnly   : !this.visibleFields['viewName'],
            labelWidth : 120,
            width      : 400,
            fieldLabel : 'Name',
            value      : this.data.name
        });

        if (this.visibleFields['author']) {

            var authorStore = LABKEY.study.DataViewUtil.getUsersStore();

            // since forceSelection is true  we must set the initial value
            // after the store is loaded
            authorStore.on('load', function() {
                var af = this.getComponent('authorfield');
                if (af) {
                    af.setValue(af.initialConfig.value);
                }
            }, this, {single: true});

            formItems.push({
                xtype: 'combo',
                itemId: 'authorfield',
                fieldLabel: 'Author',
                name: 'author',
                typeAhead: true,
                typeAheadDelay : 75,
                editable: true, // required for typeAhead
                forceSelection : true, // user must pick from list
                store       : authorStore,
                value       : this.data.authorUserId ? this.data.authorUserId : -1,
                queryMode : 'local',
                displayField : 'DisplayName',
                valueField : 'UserId',
                emptyText: 'None',
                labelWidth : 120,
                width      : 400
            });
        }

        if (this.visibleFields['status']) {

            formItems.push({
                xtype       : 'combo',
                fieldLabel  : 'Status',
                name        : 'status',
                store       : {
                    xtype: 'store',
                    fields: ['value', 'label'],
                    data: [
                        {value: 'None', label: 'None'},
                        {value: 'Draft', label: 'Draft'},
                        {value: 'Final', label: 'Final'},
                        {value: 'Locked', label: 'Locked'},
                        {value: 'Unlocked', label: 'Unlocked'}
                    ]
                },
                editable    : true,
                forceSelection : true,
                typeAhead   : true,
                typeAheadDelay : 75,
                value       : this.data.status != null && this.data.status != '' ? this.data.status : 'None',
                queryMode      : 'local',
                displayField   : 'label',
                valueField     : 'value',
                emptyText      : 'Status',
                labelWidth : 120,
                width      : 400
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
                labelWidth : 120,
                width      : 400,
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
                labelWidth : 120,
                width      : 400,
                altFormats  : LABKEY.Utils.getDateAltFormats()
            });
        }

        if (this.visibleFields['category']) {

            formItems.push({
                xtype       : 'combo',
                fieldLabel  : 'Category',
                name        : 'category',
                store       : LABKEY.study.DataViewUtil.getViewCategoriesStore({index : this.index, pageId : this.pageId}),
                typeAhead   : true,
                typeAheadDelay : 75,
                minChars       : 1,
                autoSelect     : false,
                queryMode      : 'remote',
                displayField   : 'label',
                valueField     : 'rowid',
                emptyText      : 'Uncategorized',
                forceSelection : true,
                labelWidth : 120,
                width      : 400,
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
                            combo.addClass('category-loaded-marker');
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
                value      : this.data.description,
                labelWidth : 120,
                width      : 400
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
                    value : this.data.shared,
                    labelWidth : 120,
                    width      : 400
                });

                // rename the disabled checkbox
                sharedName = "hiddenShared";
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
                labelWidth : 120,
                width      : 400,
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
                readOnly   : true,
                labelWidth : 120,
                width      : 400
            });
        }

        if (this.visibleFields['visible']) {

            formItems.push({
                xtype      : 'radiogroup',
                fieldLabel : 'Visibility',
                columns    : 2,
                items      : [
                    {boxLabel : 'Visible',  name : 'visible', checked : this.data.visible, inputValue : true},
                    {boxLabel : 'Hidden',   name : 'visible', checked : !this.data.visible,  inputValue : false}
                ],
                labelWidth : 120,
                width      : 400
            });
        }

        if (this.visibleFields['created'] && this.data.created) {

            formItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Created',
                value      : Ext4.util.Format.date(this.data.created, 'Y-m-d H:i'),
                readOnly   : true,
                labelWidth : 120,
                width      : 400
            });
        }

        if (this.visibleFields['modified'] && this.data.modified) {

            formItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Modified',
                value      : Ext4.util.Format.date(this.data.modified, 'Y-m-d H:i'),
                readOnly   : true,
                labelWidth : 120,
                width      : 400
            });
        }
                                                   
        if (this.visibleFields['customThumbnail']) {
            formItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Thumbnail',
                value      : '<div class="thumbnail"><img src="' + this.data.thumbnail + '"/></div>',
                readOnly   : true,
                labelWidth : 120,
                width      : 400
            });

            if (this.data.allowCustomThumbnail)
            {
                formItems.push({
                    xtype      : 'filefield',
                    id         : 'customThumbnail',
                    name       : 'customThumbnail',
                    fieldLabel : 'Change Thumbnail',
                    msgTarget  : 'side',
                    labelWidth : 120,
                    width      : 400,
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

        this.callParent();
    }
});
