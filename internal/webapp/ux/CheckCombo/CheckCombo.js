/**
 * Adapted from:
 * http://www.sencha.com/forum/showthread.php?198862-Ext.ux.CheckCombo
 *
 * however, it has been substantially changed
 */

Ext4.define('LABKEY.layout.component.BoundList', {
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

Ext4.define('Ext.ux.CheckCombo', {
    extend: 'Ext.form.field.ComboBox',
    alias: 'widget.checkcombo',
    multiSelect: true,
    addAllSelector: false,
    allText: 'All',
    delim: ';',

    initComponent: function() {
//        this.plugins = this.plugins || [];
//        this.plugins.push('combo-autowidth');
        this.listConfig = this.listConfig || {};
        Ext4.apply(this.listConfig, {
            tpl: new Ext4.XTemplate(
                '<ul><tpl for=".">',
                    '<li role="option" class="' + Ext4.baseCSSPrefix + 'boundlist-item"><span class="' + Ext4.baseCSSPrefix + 'combo-checker"></span>',
                    '&nbsp;{[this.getDisplayText(values, "' + this.displayField + '", "' + (Ext4.isDefined(this.nullCaption) ? this.nullCaption : "[none]") + '")]}',
                '</li></tpl></ul>',
                {
                    getDisplayText : function (values, displayField, nullCaption) {
                        var text;
                        if (typeof values === "string") {
                            text = values;
                        } else {
                            if (Ext4.isDefined(values[displayField]) && values[displayField] != null)
                                text = values[displayField];
                            else
                                text = nullCaption;
                        }
                        return Ext4.util.Format.htmlEncode(text);
                    }
                }
            ),
            childEls: [
                'listEl',
                'outerEl',
                'checkAllEl'
            ],
            renderTpl: [
                '<div id="{id}-outerEl" style="overflow:auto" width=auto;>',
                (this.addAllSelector ? '<div id="{id}-checkAllEl" class="' + Ext4.baseCSSPrefix + 'boundlist-item" role="option"><span class="' + Ext4.baseCSSPrefix + 'combo-checker">&nbsp;</span> '+this.allText+'</div>' : ''),
                '<div id="{id}-listEl" class="{baseCls}-list-ct"></div>',
                '{%',
                    'var me=values.$comp, pagingToolbar=me.pagingToolbar;',
                    'if (pagingToolbar) {',
                        'pagingToolbar.ownerLayout = me.componentLayout;',
                        'Ext4.DomHelper.generateMarkup(pagingToolbar.getRenderTree(), out);',
                    '}',
                '%}',
                '</div>',
                {
                    disableFormats: true
                }
            ],
            componentLayout: 'boundlist-checkbox',
            onDestroy: function() {
                Ext4.destroyMembers(this, 'pagingToolbar', 'outerEl', 'listEl');
                this.callParent();
            }
        });

        this.callParent();
    },

    createPicker: function()
    {
        var picker = this.callParent(arguments);
        picker.on('render', function(picker){
            if (picker.checkAllEl)
            {
                picker.checkAllEl.addClsOnOver(Ext4.baseCSSPrefix + 'boundlist-item-over');

                picker.checkAllEl.on('click', function(e)
                {
                    if(picker.checkAllEl.hasCls(Ext4.baseCSSPrefix + 'boundlist-selected'))
                    {
                        picker.checkAllEl.removeCls(Ext4.baseCSSPrefix + 'boundlist-selected');
                        this.setValue('');
                        this.fireEvent('select', this, []);
                    }
                    else
                    {
                        var records = [];
                        this.store.clearFilter();  //if the user has typed text, this can filter the store, causing it to select all visible, rather than selecting all
                        this.store.each(function(record)
                        {
                            records.push(record);
                        });
                        this.select(records);
                        picker.checkAllEl.addCls(Ext4.baseCSSPrefix + 'boundlist-selected');
                        this.fireEvent('select', this, records);
                    }
                }, this);
            }
        }, this, {single: true});
        return picker;
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
                checker.addCls(Ext4.baseCSSPrefix + 'boundlist-selected');
            else
                checker.removeCls(Ext4.baseCSSPrefix + 'boundlist-selected');
        }
    }
});