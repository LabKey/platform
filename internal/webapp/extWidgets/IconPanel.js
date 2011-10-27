/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/*
    config:
    @param (string) [iconSize] The initial icon size, either 'small', 'medium' or 'large' (defaults to large)
    @param (string) [labelPosition] The initial labPosition, either 'bottom' or 'side' (defaults to bottom)
    @param (object) [store] The Ext4 store holding the data
    @param (string) [iconField] The field name in the store holding the URL to the icon
    @param (string [labelField] The field holding the value to be used as the label.
    @param (string) [urlField] The field name holding the URL for each icon
    @param (boolean) [showMenu] Controls whether the menu is shown, which allows the user to toggle icon sizes. Hidden in webparts

 */
LABKEY.requiresCss("/extWidgets/IconPanel.css");

Ext4.define('LABKEY.ext.IconPanel', {
    extend: 'Ext.panel.Panel',
    initComponent: function(){
        Ext4.QuickTips.init();

        Ext4.applyIf(this, {
            width: 535,
            bodyStyle: 'padding:5px',
            cls: 'labkey-iconpanel',
            iconSizeBtnHandler: function(btn){
                btn.ownerCt.items.each(function(i){
                    i.setChecked(false);
                });
                btn.setChecked(true);
                btn.ownerCt.hide();

                this.resizeIcons(btn);
            },
            resizeIcons: function(config){
                var view = this.down('#dataView');
                view.renderData.iconSize = config.iconSize;
                view.renderData.labelPosition = config.labelPosition;
                view.refresh();
            },
            items: [{
                xtype: 'dataview',
                itemId: 'dataView',
                store: this.store,
                setTemplate: function(template) {
                    this.tpl = template;
                    this.refresh();
                },
                tpl: [
                    '<tpl for=".">',
                        '<tpl if="labelPosition==&quot;bottom&quot;">',
                            '<div class="thumb-wrap thumb-wrap-{labelPosition} thumb-{iconSize}">',
                            '<tpl if="url"><a href="{url}"></tpl>',
                            '<div class="thumb-img-{labelPosition}"><img src="{iconurl}" title="{label}" class="thumb-{iconSize}"></div>',
                            '<span class="thumb-label-{labelPosition}">{label}</span>',
                            '<tpl if="url"></a></tpl>',
                            '</div>',
                        '</tpl>',
                        '<tpl if="labelPosition==&quot;side&quot;">',
                            '<div class="thumb-wrap thumb-wrap-{labelPosition} thumb-{iconSize}">',
                            '<tpl if="url"><a href="{url}"></tpl>',
                            '<div class="thumb-img-{labelPosition}"><img src="{iconurl}" title="{label}" class="thumb-{iconSize}"></div>',
                            '<span class="thumb-label-{labelPosition}">{label}</span>',
                            '<tpl if="url"></a></tpl>',
                            '</div>',
                        '</tpl>',
                    '</tpl>',
                    '<div class="x-clear"></div>'
                ],
                prepareData: function(d){
                    var panel = this.up('panel');
                    var item = Ext4.apply({}, this.renderData);

                    if(panel.iconField)
                        item.iconurl = d[panel.iconField];
                    if(panel.labelField)
                        item.label = d[panel.labelField];
                    if(panel.urlField)
                        item.url = d[panel.urlField];

                    return item;
                },
                renderData: {
                    iconSize: this.iconSize || 'medium',
                    labelPosition: this.labelPosition || 'bottom'
                },
                trackOver: true,
                overItemCls: 'x4-item-over',
                itemSelector: 'div.thumb-wrap',
                emptyText: 'No projects to display'
                }]
        });

        if(this.showMenu){
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
    }
});