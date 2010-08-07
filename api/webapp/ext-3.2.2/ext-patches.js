// Adding 'tooltip' property to menu items
// http://www.sencha.com/forum/showthread.php?77656-How-to-put-a-tooltip-on-a-menuitem&p=374038#post374038
Ext.override(Ext.menu.Item, {
    onRender : function(container, position){
        if (!this.itemTpl) {
            this.itemTpl = Ext.menu.Item.prototype.itemTpl = new Ext.XTemplate(
                '<a id="{id}" class="{cls}" hidefocus="true" unselectable="on" href="{href}"',
                    '<tpl if="hrefTarget">',
                        ' target="{hrefTarget}"',
                    '</tpl>',
                 '>',
                     '<img src="{icon}" class="x-menu-item-icon {iconCls}"/>',
                     '<span class="x-menu-item-text">{text}</span>',
                 '</a>'
             );
        }
        var a = this.getTemplateArgs();
        this.el = position ? this.itemTpl.insertBefore(position, a, true) : this.itemTpl.append(container, a, true);
        this.iconEl = this.el.child('img.x-menu-item-icon');
        this.textEl = this.el.child('.x-menu-item-text');
        if (this.tooltip) {
            this.tooltip = new Ext.ToolTip(Ext.apply({
                target: this.el
            }, Ext.isObject(this.tooltip) ? this.toolTip : { html: this.tooltip }));
        }
        Ext.menu.Item.superclass.onRender.call(this, container, position);
    }
});
