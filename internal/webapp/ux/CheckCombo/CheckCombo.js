/**
 * Adapted from:
 * http://www.sencha.com/forum/showthread.php?198862-Ext.ux.CheckCombo
 */

(function(Ext){

Ext.define('Ext.ux.CheckCombo',
{
    extend: 'Ext.form.field.ComboBox',
    alias: 'widget.checkcombo',
    multiSelect: true,
    allSelector: false,
    addAllSelector: false,
    allText: 'All',
    delim: ';',
    createPicker: function() {
        var me = this,
            picker,
            menuCls = Ext.baseCSSPrefix + 'menu',
            opts = Ext.apply({
                pickerField: me,
                selModel: {
                    mode: me.multiSelect ? 'SIMPLE' : 'SINGLE'
                },
                floating: true,
                hidden: true,
                ownerCt: me.ownerCt,
                cls: me.el.up('.' + menuCls) ? menuCls : '',
                store: me.store,
                displayField: me.displayField,
                focusOnToFront: false,
                pageSize: me.pageSize,
                tpl:
            [
                '<ul><tpl for=".">',
                    '<li role="option" class="' + Ext.baseCSSPrefix + 'boundlist-item"><span class="x4-combo-checker">&nbsp;</span> {' + me.displayField + '}</li>',
                '</tpl></ul>'
            ]
            }, me.listConfig, me.defaultListConfig);

        picker = me.picker = Ext.create('Ext.view.BoundList', opts);
        if (me.pageSize) {
            picker.pagingToolbar.on('beforechange', me.onPageChange, me);
        }

        me.mon(picker, {
            itemclick: me.onItemClick,
            refresh: me.onListRefresh,
            scope: me
        });

        me.mon(picker.getSelectionModel(), {
            'beforeselect': me.onBeforeSelect,
            'beforedeselect': me.onBeforeDeselect,
            'selectionchange': me.onListSelectionChange,
            scope: me
        });

        return picker;
    },
    getValue: function()
    {
        //modified by bbimber
        return this.value.join(this.delim);
    },
    getValueAsArray: function(){
        return this.value;
    },
    getSubmitValue: function()
    {
        return this.getValue();
    },
    expand: function()
    {
        var me = this,
            bodyEl, picker, collapseIf;

        if (me.rendered && !me.isExpanded && !me.isDestroyed) {
            bodyEl = me.bodyEl;
            picker = me.getPicker();
            collapseIf = me.collapseIf;

            // show the picker and set isExpanded flag
            picker.show();
            me.isExpanded = true;
            me.alignPicker();
            bodyEl.addCls(me.openCls);

            if(me.addAllSelector == true && me.allSelector == false)
            {
               me.allSelector = picker.getEl().insertHtml('afterBegin', '<div class="x4-boundlist-item" role="option"><span class="x4-combo-checker">&nbsp;</span> '+me.allText+'</div>', true);
                me.allSelector.on('click', function(e)
                {
                    if(me.allSelector.hasCls('x4-boundlist-selected'))
                    {
                        me.allSelector.removeCls('x4-boundlist-selected');
                        me.setValue('');
                        me.fireEvent('select', me, []);
                    }
                    else
                    {
                        var records = [];
                        me.store.each(function(record)
                        {
                            records.push(record);
                        });
                        me.allSelector.addCls('x4-boundlist-selected');
                        me.select(records);
                        me.fireEvent('select', me, records);
                    }
                });
            }
            // monitor clicking and mousewheel
            me.mon(Ext.getDoc(), {
                mousewheel: collapseIf,
                mousedown: collapseIf,
                scope: me
            });
            Ext.EventManager.onWindowResize(me.alignPicker, me);
            me.fireEvent('expand', me);
            me.onExpand();
        }
    },
    onListSelectionChange: function(list, selectedRecords)
    {
        var me = this,
            isMulti = me.multiSelect,
            hasRecords = selectedRecords.length > 0;
        // Only react to selection if it is not called from setValue, and if our list is
        // expanded (ignores changes to the selection model triggered elsewhere)
        if (me.isExpanded) {
            if (!isMulti) {
                Ext.defer(me.collapse, 1, me);
            }
            /*
             * Only set the value here if we're in multi selection mode or we have
             * a selection. Otherwise setValue will be called with an empty value
             * which will cause the change event to fire twice.
             */

            if (isMulti || hasRecords) {
                me.setValue(selectedRecords, false);
            }
            if (hasRecords) {
                me.fireEvent('select', me, selectedRecords);
            }
            me.inputEl.focus();
        }

        if(me.addAllSelector == true && me.allSelector != false)
        {
            if(selectedRecords.length == me.store.getTotalCount()) me.allSelector.addCls('x4-boundlist-selected');
            else me.allSelector.removeCls('x4-boundlist-selected');
        }
    }
});

})(Ext4);