/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.ext4.DataViewPropertiesPanel', {

    extend  : 'Ext.form.Panel',
    alias   : 'widget.dvproperties',
    alternateClassName : ['LABKEY.study.DataViewPropertiesPanel'],
    border: false,
    frame: false,
    autoScroll: true,
    cls: 'iScroll', // webkit custom scroll bars
    buttonAlign: 'left',
    dateFormat: 'Y-m-d',
    fieldDefaults  : {
        labelWidth : 120,
        style      : 'margin: 0px 0px 10px 0px',
        labelSeparator : ''
    },

    record: {},

    data: {},

    visibleFields: {},

    extraItems: [],

    disableShared: false,

    constructor : function(config) {

        // the optional data model record for exiting data views
        if (config.record && config.record.data) {
            this.data = config.record.data;
        }

        if (Ext4.isArray(config.items))
            config.items.push({ xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF });

        this.callParent([config]);
    },

    initComponent : function() {

        var propertiesItems = [{
            xtype      : 'textfield',
            allowBlank : false,
            name       : 'viewName',
            readOnly   : !this.visibleFields['viewName'],
            labelWidth : 120,
            width      : 400,
            fieldLabel : 'Name',
            value      : this.data.name
        }];
        var imagesItems = [];
        var thumbnailItems = [];
        var iconItems = [];

        if (this.visibleFields['author']) {

            var authorStore = LABKEY.study.DataViewUtil.getUsersStore();
            var storeLoaded = false;

            // since forceSelection is true  we must set the initial value
            // after the store is loaded
            authorStore.on('load', function() {
                var af = this.down('#authorfield');
                if (af) {
                    af.setValue(af.initialConfig.value);
                }
                storeLoaded = true;
                this.isValid();
            }, this, {single: true});

            propertiesItems.push({
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
                width      : 400,
                validator  : function(){
                    return storeLoaded;
                }
            });
        }

        if (this.visibleFields['status']) {

            propertiesItems.push({
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

        if (this.visibleFields['datacutdate']) {

            propertiesItems.push({
                xtype       : 'datefield',
                fieldLabel  : 'Data Cut Date',
                name        : 'refreshDate',
                value       : this.data.refreshDate != null  ? this.data.refreshDate : '',
                blankText   : 'Date of last refresh',
                format      : LABKEY.extDateInputFormat,
                editable    : true,
                labelWidth : 120,
                width      : 400,
                altFormats  : LABKEY.Utils.getDateAltFormats()
            });
        }

        if (this.visibleFields['category']) {

            var categoryStoreLoaded = false;
            propertiesItems.push({
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
                            categoryStoreLoaded = true;
                            this.isValid();
                            LABKEY.Utils.signalWebDriverTest('category-loaded');
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
                ),
                validator : function() {
                    return categoryStoreLoaded;
                }
            });
        }

        if (this.visibleFields['description']) {

            propertiesItems.push({
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
            var isShared = Ext4.isString(this.data.access) ? this.data.access == 'public' : this.data.shared;

            if (this.disableShared) {
                // be sure to roundtrip the original shared value
                // since we are disabling the checkbox
                propertiesItems.push({
                    xtype : 'hidden',
                    name  : "shared",
                    value : isShared,
                    labelWidth : 120,
                    width      : 400
                });

                // rename the disabled checkbox
                sharedName = "hiddenShared";
            }

            propertiesItems.push({
                xtype   : 'checkbox',
                inputValue  : isShared,
                checked     : isShared,
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

            propertiesItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Type',
                value      : this.data.dataType,
                readOnly   : true,
                labelWidth : 120,
                width      : 400
            });
        }

        if (this.visibleFields['visible']) {

            propertiesItems.push({
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
            var formattedVal = Ext4.util.Format.date(this.data.created, LABKEY.extDefaultDateTimeFormat);
            propertiesItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Created',
                value      : LABKEY.Utils.encodeHtml(formattedVal),
                readOnly   : true,
                labelWidth : 120,
                width      : 400
            });
        }

        if (this.visibleFields['modified'] && this.data.modified) {
            var formattedVal = Ext4.util.Format.date(this.data.modified, LABKEY.extDefaultDateTimeFormat);
            propertiesItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Modified',
                value      : LABKEY.Utils.encodeHtml(formattedVal),
                readOnly   : true,
                labelWidth : 120,
                width      : 400
            });
        }

        if (this.visibleFields['contentModified'] && this.data.contentModified) {
            var formattedVal = Ext4.util.Format.date(this.data.contentModified, LABKEY.extDefaultDateTimeFormat);
            propertiesItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Content Modified',
                value      : LABKEY.Utils.encodeHtml(formattedVal),
                readOnly   : true,
                labelWidth : 120,
                width      : 400
            });
        }

        Ext4.each(this.extraItems, function(item) {
            propertiesItems.push(item);
        }, this);
                                                   
        if (this.visibleFields['customThumbnail']) {
            thumbnailItems.push({
                xtype      : 'displayfield',
                fieldLabel : 'Thumbnail',
                value      : '<div class="thumbnail"></div>',
                readOnly   : true,
                labelWidth : 125
            });

            if (this.data.allowCustomThumbnail)
            {
                var thumbnailFieldContainerItems = [{
                    xtype      : 'box',
                    width      : 350
                },{
                    xtype: 'box',
                    id: 'thumbnail-remove',
                    cls: 'thumbnail-remove',
                    hidden : !this.data.thumbnailType || this.data.thumbnailType == "NONE",
                    html: '<div class="fa fa-trash-o"></div>',
                    listeners: {
                        scope: this,
                        afterrender : function(cmp) {
                            Ext4.tip.QuickTipManager.register({
                                target: cmp.getId(),
                                text: 'Delete Custom Thumbnail'
                            });
                        },
                        render: function (box)
                        {
                            box.getEl().on('click', function ()
                            {
                                this.down('#deleteCustomThumbnail').setValue(true);
                                var thumbNailImageDiv = Ext4.DomQuery.selectNode('.thumbnail');
                                if (thumbNailImageDiv)
                                {
                                    thumbNailImageDiv.children[0].src = this.data.defaultThumbnailUrl;
                                }
                                Ext4.getCmp('customThumbnail').reset();
                                Ext4.getCmp('thumbnail-remove').hide();
                            }, this);
                        }
                    }
                }];

                thumbnailItems.push({
                    xtype: 'fieldcontainer',
                    layout: {type: 'hbox'},
                    items: thumbnailFieldContainerItems
                });

               thumbnailItems.push({
                    xtype      : 'filefield',
                    id         : 'customThumbnail',
                    name       : 'customThumbnail',
                    fieldLabel : 'Change Thumbnail',
                    msgTarget  : 'side',
                    labelWidth : 125,
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
                            Ext4.getCmp('thumbnail-remove').show();
                            this.down('#deleteCustomThumbnail').setValue(false);
                        },
                        scope: this
                    }
                });

                // hidden form field for the custom thumbnail file name
                imagesItems.push({
                            xtype: 'hidden',
                            id: 'customThumbnailFileName',
                            name: 'customThumbnailFileName',
                            value: null
                        },
                        {
                            xtype: 'hidden',
                            id: 'deleteCustomThumbnail',
                            name: 'deleteCustomThumbnail',
                            value: false
                        });
                imagesItems.push({
                    xtype   :'fieldset',
                    border: 1,
                    style:{borderColor:'b4b4b4', borderStyle:'solid'},
                    items   :thumbnailItems
                });
            }

            // For the time being any view that supports custom thumbnails also supports custom icons. So a separate check
            // is not necessary.


            if (this.data.allowCustomThumbnail) {
                var iconFieldContainerItems = [{
                    xtype      : 'displayfield',
                    fieldLabel : 'Icon',
                    value      : this.data.iconCls !== null
                                    ? '<span class="' + this.data.iconCls + '"></span>'
                                    : '<div class="icon"><img height="16" src="' + this.data.icon + '"/></div>',
                    readOnly   : true,
                    labelWidth : 125,
                    width      : 350
                },
                    {
                        xtype: 'box',
                        id: 'icon-remove',
                        cls: 'icon-remove',
                        hidden : !this.data.iconType || this.data.iconType == "NONE",
                        html: '<div class="fa fa-trash-o"></div>',
                        listeners: {
                            scope: this,
                            afterrender : function(cmp) {
                                Ext4.tip.QuickTipManager.register({
                                    target: cmp.getId(),
                                    text: 'Delete Custom Icon'
                                });
                            },
                            render: function (box)
                            {
                                box.getEl().on('click', function ()
                                {
                                    this.down('#deleteCustomIcon').setValue(true);
                                    var iconImageDiv = Ext4.DomQuery.selectNode('.icon');
                                    if (iconImageDiv)
                                    {
                                        if(this.data.defaultIconCls===undefined || this.data.defaultIconCls==='')
                                        {
                                            iconImageDiv.children[0].src = this.data.defaultThumbnailUrl;

                                        }else
                                        {
                                            iconImageDiv.parentNode.innerHTML = '<span class="' + this.data.defaultIconCls + '"></span>';
                                        }
                                    }
                                    Ext4.getCmp('customIcon').reset();
                                    Ext4.getCmp('icon-remove').hide();
                                }, this);
                            }
                        }
                    }];

                iconItems.push({
                    xtype: 'fieldcontainer',
                    layout: {type: 'hbox'},
                    items: iconFieldContainerItems
                });

                iconItems.push({
                    xtype      : 'filefield',
                    id         : 'customIcon',
                    name       : 'customIcon',
                    fieldLabel : 'Change Icon',
                    msgTarget  : 'side',
                    labelWidth : 125,
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
                                text: 'Choose a PNG, JPG, GIF, or SVG image. Images will be scaled to 18 × 18 pixels.'
                            });
                        },
                        change : function(cmp, value) {
                            this.down('#customIconFileName').setValue(value);
                            Ext4.getCmp('icon-remove').show();
                            this.down('#deleteCustomIcon').setValue(false);
                        },
                        scope: this
                    }
                });

                imagesItems.push(
                        {
                            xtype: 'hidden',
                            id: 'customIconFileName',
                            name: 'customIconFileName',
                            value: null
                        },
                        {
                            xtype: 'hidden',
                            id: 'deleteCustomIcon',
                            name: 'deleteCustomIcon',
                            value: false
                        }
                );

                imagesItems.push({
                    xtype: 'fieldset',
                    padding: 3,
                    border: 1,
                    style: {borderColor: '#b4b4b4', borderStyle: 'solid'},
                    items: iconItems
                });
            }
        }


        if (imagesItems.length > 0) {
            this.thumbnail = null;

            this.items = [{
                xtype: 'tabpanel',
                border: false,
                frame: false,
                items: [{
                    xtype: 'form',
                    title: 'Properties',
                    margin: '5 0 0 0',
                    border: false,
                    frame: false,
                    items: propertiesItems
                }, {
                    xtype: 'form',
                    title: 'Images',
                    cls: 'dataview-props-images',
                    margin: '5 0 0 0',
                    border: false,
                    frame: false,
                    items: imagesItems,
                    listeners  : {
                        scope   : this,
                        show    : function(panel){
                            // Have to load the image on show and force layout after image loads because Ext assumes 0
                            // width/height since the image has not been loaded when it calculates the layout.
                            if (!this.thumbnail) {
                                this.thumbnail = new Image();
                                this.thumbnail.style.width = 'auto';
                                this.thumbnail.style.height = 'auto';
                                var t = this.thumbnail; // needed for callback closure.
                                this.thumbnail.onload = function(){
                                    var thumbnailDiv = panel.getEl().dom.querySelector('.thumbnail');
                                    thumbnailDiv.appendChild(t);
                                    panel.doLayout();
                                };
                                this.thumbnail.src = this.data.thumbnail;
                            }
                        }
                    }
                }]
            }];
        } else {
            this.items = propertiesItems;
        }

        this.callParent();
    }
});
