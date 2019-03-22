/*
 * Copyright (c) 2011-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/*
    config:
    @param (string) [iconSize] The initial icon size, either 'small', 'medium' or 'large' (defaults to large)
    @param (string) [labelPosition] The initial labPosition, either 'bottom' or 'side' (defaults to bottom)
    @param (object) [store] The Ext4 store holding the data
    @param (string) [iconField] The field name in the store holding the URL to the icon
    @param (string) [iconCls] The font-awesome class used for all icons, if provided, will ignore iconField
    @param (string [labelField] The field holding the value to be used as the label.
    @param {string} [tooltipField] The field holding the value to be used as a tooltip
    @param (string) [urlField] The field name holding the URL for each icon
    @param (boolean) [showMenu] Controls whether the menu is shown, which allows the user to toggle icon sizes. Hidden in webparts

 */
LABKEY.requiresCss("/extWidgets/IconPanel.css");

Ext4.define('LABKEY.ext.IconPanel', {

    extend: 'Ext.panel.Panel',

    sizeContainer : false,

    initComponent: function() {

        Ext4.QuickTips.init();

        Ext4.applyIf(this, {
            bodyStyle: 'padding:5px',
            cls: 'labkey-iconpanel',
            iconSizeBtnHandler: function(btn) {
                btn.ownerCt.items.each(function(i){
                    i.setChecked(false);
                });
                btn.setChecked(true);
                btn.ownerCt.hide();

                this.resizeIcons(btn);
            },
            resizeIcons: function(config) {
                var view = this.down('#dataView');
                this.iconSize = config.iconSize;
                view.renderData.iconSize = config.iconSize;
                this.labelPosition = config.labelPosition;
                view.renderData.labelPosition = config.labelPosition;
                view.refresh();
            },
            items: [this.getDataViewCfg()]
        });

        if (this.showMenu) {
            this.tools = this.tools || [];
            this.tools.push([{
                xtype: 'button',
                tooltip: 'Choose layout',
                menu: {
                    xtype: 'menu',
                    plain: false,
                    defaults: {
                        xtype: 'menucheckitem',
                        scope: this,
                        handler: function(btn){
                            this.iconSizeBtnHandler(btn);
                        }
                    },
                    items: [{
                        text: 'List',
                        iconSize: 'small',
                        labelPosition: 'side',
                        checked: this.iconSize=='small'
                    },{
                        text: 'Medium Icons',
                        iconSize: 'medium',
                        labelPosition: 'bottom',
                        checked: this.iconSize=='medium'
                    },{
                        text: 'Large Icons',
                        iconSize: 'large',
                        labelPosition: 'bottom',
                        checked: this.iconSize=='large'
                    }]
                }
            }]);
        }

        this.callParent(arguments);

        //resize panel on window resize
        this.on('afterrender', this.onAfterRender, this);

        //poor solution to firefox Ext4 layout issue that occurs when adding items from store after panel has rendered
        //TODO: should revisit with future Ext4 versions
        if(!this.store.getCount()){
            this.mon(this.store, 'load', this.doLayout, this, {single: true});
        }
    },

    onAfterRender : function(panel, opts) {
        this._previousWidth = this.container.getWidth();
        Ext4.EventManager.onWindowResize(this.onWindowResize, this);

        // configure initial sizing
        if (this.sizeContainer) {
            this.down('dataview').on('viewready', function() {
                Ext4.isGecko ? Ext4.defer(this.onSizeContainer, 100, this) : this.onSizeContainer();
            }, this, {single: true});
        }
    },

    onWindowResize : function(h, w, opts) {
        if (this._previousWidth && this._previousWidth == this.container.getWidth()) {
            return;
        }

        this._previousWidth = this.container.getWidth();
        this.setWidth(this._previousWidth);  //get the width of the DIV containing it
        this.down('dataview').refresh();
        this.onSizeContainer();
    },

    onSizeContainer : function() {
        if (this.sizeContainer) {
            this.up('container').setHeight(this.down('dataview').getHeight() + 65);
        }
    },

    getDataViewCfg : function() {
        return {
            xtype: 'dataview',
            itemId: 'dataView',
            store: this.store,
            setTemplate: function(template) {
                this.tpl = template;
                this.refresh();
            },
            tpl: [
                '<table><tr>',
                '<tpl for=".">',
                    '<td class="thumb-wrap">',
                    '<div ' +
                        '<tpl if="tooltip">data-qtip="{tooltip}"</tpl>',
                        'style="width: {thumbWidth};" class="tool-icon thumb-wrap thumb-wrap-{labelPosition}">',
                        '<a ' + '<tpl if="url">href="{url}"</tpl>' + '>',
                        '<tpl if="iconCls">',
                            '<div class="thumb-img-{labelPosition}"><span class="fa {iconCls} {imageSize}"></span></div>',
                        '<tpl else>',
                            '<div class="thumb-img-{labelPosition}"><img src="{iconurl}" style="width: {imageSize}px;height: {imageSize}px;" class="thumb-{iconSize}"></div>',
                        '</tpl>',
                        '<span class="thumb-label-{labelPosition}">{label:htmlEncode}</span>',
                        '</a>',
                    '</div>',
                    '</td>',
                    '<tpl if="xindex % columns === 0"></tr><tr></tpl>',
                '</tpl>',
                '</td></tr></table>',
                '<tpl if="values.length === 0"><div>' + (this.emptyText || 'No matching data to show') + '</div></tpl>',
                '<div class="x-clear"></div>'
            ],
            imageSizeMap: {
                large: 60,
                medium: 40,
                small: 20
            },
            faImageSizeMap: {
                large: "fa-5x",
                medium: "fa-3x",
                small: "fa-lg"
            },
            prepareData: function(d){
                var panel = this.up('panel');
                var item = Ext4.apply({}, this.renderData);

                if (panel.iconCls) {
                    item.iconCls = panel.iconCls;
                }
                else if(panel.iconField){
                    item.iconurl = d[panel.iconField];
                }
                if(panel.labelField)
                    item.label = d[panel.labelField];
                if(panel.urlField)
                    item.url = d[panel.urlField];
                if(panel.tooltipField)
                    item.tooltip = d[panel.tooltipField];

                var multiplier = 1.66;
                var imageSizePx = this.imageSizeMap[this.renderData.iconSize];
                item.imageSize = item.iconurl == null ? this.faImageSizeMap[this.renderData.iconSize] : imageSizePx;
                item.thumbWidth = item.labelPosition=='bottom' ? imageSizePx * multiplier + 'px':  '100%';
                item.columns = item.labelPosition=='bottom' ? this.calculateColumnNumber(Math.ceil(imageSizePx * multiplier)) : 1;
                return item;
            },
            calculateColumnNumber: function(thumbWidth){
                var totalWidth = this.ownerCt.container.getWidth() - 10; // padding
                return parseInt(totalWidth / (thumbWidth + 10)); //padding
            },
            renderData: {
                iconSize: this.iconSize || 'medium',
                labelPosition: this.labelPosition || 'bottom'
            },
            //trackOver: true,
            //overItemCls: 'x4-item-over',
            itemSelector: 'div.thumb-wrap'
        };
    }
});
