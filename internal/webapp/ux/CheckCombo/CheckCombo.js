/**
 * Adapted from:
 * http://www.sencha.com/forum/showthread.php?198862-Ext.ux.CheckCombo
 *
 * however, it has been substantially changed
 */

(function(Ext){

Ext.define('LABKEY.layout.component.BoundList', {
    extend: 'Ext.layout.component.BoundList',
    alias: 'layout.boundlist-checkbox',
    beginLayout: function(ownerContext){
        this.callParent(arguments);
        ownerContext.listContext = ownerContext.getEl('outerEl');
    },
    measureContentHeight: function(ownerContext) {
        return this.owner.outerEl.getHeight();
    }
});

Ext.define('Ext.ux.CheckCombo',
{
    extend: 'Ext.form.field.ComboBox',
    alias: 'widget.checkcombo',
    multiSelect: true,
    addAllSelector: false,
    allText: 'All',
    delim: ';',

    initComponent: function()
    {
        this.plugins = this.plugins || [];
        this.plugins.push('combo-autowidth');
        this.listConfig = this.listConfig || {};
        Ext.apply(this.listConfig, {
            tpl:[
                '<ul><tpl for=".">',
                    '<li role="option" class="' + Ext.baseCSSPrefix + 'boundlist-item"><span class="' + Ext.baseCSSPrefix + 'combo-checker"></span>',
                    '&nbsp;{[(typeof values === "string" ? values : (values["' + this.displayField + '"] ? values["' + this.displayField + '"] : '+(Ext4.isDefined(this.nullCaption) ? '"' + this.nullCaption + '"' : '"[none]"')+'))]}',
                '</li></tpl></ul>'
            ],
            childEls: [
                'listEl',
                'outerEl',
                'checkAllEl'
            ],
            renderTpl: [
                '<div id="{id}-outerEl" style="overflow:auto" width=auto;>',
                (this.addAllSelector ? '<div id="{id}-checkAllEl" class="' + Ext.baseCSSPrefix + 'boundlist-item" role="option"><span class="' + Ext.baseCSSPrefix + 'combo-checker">&nbsp;</span> '+this.allText+'</div>' : ''),
                '<div id="{id}-listEl" class="{baseCls}-list-ct"></div>',
                '{%',
                    'var me=values.$comp, pagingToolbar=me.pagingToolbar;',
                    'if (pagingToolbar) {',
                        'pagingToolbar.ownerLayout = me.componentLayout;',
                        'Ext.DomHelper.generateMarkup(pagingToolbar.getRenderTree(), out);',
                    '}',
                '%}',
                '</div>',
                {
                    disableFormats: true
                }
            ],
            componentLayout: 'boundlist-checkbox',
            onDestroy: function() {
                Ext.destroyMembers(this, 'pagingToolbar', 'outerEl', 'listEl');
                this.callParent();
            }
        });

        this.callParent(arguments);
    },

    createPicker: function()
    {
        var picker = this.callParent(arguments);
        picker.on('render', function(picker){
            if (picker.checkAllEl)
            {
                picker.checkAllEl.addClsOnOver(Ext.baseCSSPrefix + 'boundlist-item-over');

                picker.checkAllEl.on('click', function(e)
                {
                    if(picker.checkAllEl.hasCls(Ext.baseCSSPrefix + 'boundlist-selected'))
                    {
                        picker.checkAllEl.removeCls(Ext.baseCSSPrefix + 'boundlist-selected');
                        this.setValue('');
                        this.fireEvent('select', this, []);
                    }
                    else
                    {
                        var records = [];
                        this.store.each(function(record)
                        {
                            records.push(record);
                        });
                        picker.checkAllEl.addCls(Ext.baseCSSPrefix + 'boundlist-selected');
                        this.select(records);
                        this.fireEvent('select', this, records);
                    }
                }, this);
            }
        }, this, {single: true});
        return picker;
    },

    getValue: function()
    {
        return this.value;
    },

    getSubmitValue: function()
    {
        return this.getValue();
    },

    onListSelectionChange: function(list, selectedRecords)
    {
        this.callParent(arguments);

        var checker = this.getPicker().checkAllEl;
        if(checker)
        {
            if(selectedRecords.length == this.store.getTotalCount())
                checker.addCls(Ext.baseCSSPrefix + 'boundlist-selected');
            else
                checker.removeCls(Ext.baseCSSPrefix + 'boundlist-selected');
        }
    }
});

})(Ext4);