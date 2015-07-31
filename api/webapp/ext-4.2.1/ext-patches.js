/* Apply patches found for Ext 4.2.1 here */

// Set USE_NATIVE_JSON so Ext4.decode and Ext4.encode use JSON.parse and JSON.stringify instead of eval
Ext4.USE_NATIVE_JSON = true;

// set the default ajax timeout from 30's to 5 minutes
Ext4.Ajax.timeout = 5 * 60 * 1000;

// set csrf value for all requests
if (!Ext4.Ajax.defaultHeaders) {
    Ext4.Ajax.defaultHeaders = {}
}
Ext4.Ajax.defaultHeaders['X-LABKEY-CSRF'] = LABKEY.CSRF;

/**
 * @Override
 * Issue 22272: Fix IE 11 detection in ExtJS 4.2.1
 * http://stackoverflow.com/questions/21881671/ext-isie-return-false-in-ie-11
 */
(function() {
    var check = function(regex){return regex.test(Ext4.userAgent);},docMode = document.documentMode,isIE = !Ext4.isOpera && (check(/msie/) || check(/trident/));
    Ext4.apply(Ext4, {
        isIE: isIE, // any users before this point (e.g. the framework itself) will not recognize this change to isIE
        isIE11: isIE && ((check(/trident\/7\.0/) && docMode != 7 && docMode != 8 && docMode != 9 && docMode != 10) || docMode == 11)
    });
})();

/**
 * @Override
 * This is an override of the Ext4.2.1 field template, which was performed in order to support a help popup next to the field label (ie. '?')
 * Set the field's helpPopup property to use this.  This should be the default Ext4.2.1 template with 2 lines added.  This is a heavy handed way to
 * inject this.  It would have been nicer to hook into afterLabelTextTpl; however, this template's data is populated through a slightly different
 * path.  This is probably prone to breaking during Ext version upgrades; however, it is generally not difficult to fix.
 */
Ext4.override(Ext4.form.field.Base, {
    labelableRenderTpl: [
        '<tr role="presentation" id="{id}-inputRow" <tpl if="inFormLayout">id="{id}"</tpl> class="{inputRowCls}">',

        '<tpl if="labelOnLeft">',
        '<td role="presentation" id="{id}-labelCell" style="{labelCellStyle}" {labelCellAttrs}>',
        '{beforeLabelTpl}',
        '<label id="{id}-labelEl" {labelAttrTpl}<tpl if="inputId"> for="{inputId}"</tpl> class="{labelCls}"',
        '<tpl if="labelStyle"> style="{labelStyle}"</tpl>',

        ' unselectable="on"',
        '>',
        '{beforeLabelTextTpl}',
        '<tpl if="fieldLabel">{fieldLabel}{labelSeparator}</tpl>',
        //NOTE: this line added.  this is sub-optimal, but afterLabelTpl uses a different path to populate tpl values
        '<tpl if="helpPopup"><a tabindex="-1" href="javascript:void(0);" data-qtip="{helpPopup}"><span class="labkey-help-pop-up">?</span></a></tpl>',
        '{afterLabelTextTpl}',
        '</label>',
        '{afterLabelTpl}',
        '</td>',
        '</tpl>',

        '<td role="presentation" class="{baseBodyCls} {fieldBodyCls} {extraFieldBodyCls}" id="{id}-bodyEl" colspan="{bodyColspan}" role="presentation">',
        '{beforeBodyEl}',

        '<tpl if="labelAlign==\'top\'">',
        '{beforeLabelTpl}',
        '<div role="presentation" id="{id}-labelCell" style="{labelCellStyle}">',
        '<label id="{id}-labelEl" {labelAttrTpl}<tpl if="inputId"> for="{inputId}"</tpl> class="{labelCls}"',
        '<tpl if="labelStyle"> style="{labelStyle}"</tpl>',

        ' unselectable="on"',
        '>',
        '{beforeLabelTextTpl}',
        '<tpl if="fieldLabel">{fieldLabel}{labelSeparator}</tpl>',
        '{afterLabelTextTpl}',
        '</label>',
        '</div>',
        '{afterLabelTpl}',
        '</tpl>',

        '{beforeSubTpl}',
        '{[values.$comp.getSubTplMarkup(values)]}',
        '{afterSubTpl}',

        '<tpl if="msgTarget===\'side\'">',
        '{afterBodyEl}',
        '</td>',
        '<td role="presentation" id="{id}-sideErrorCell" vAlign="{[values.labelAlign===\'top\' && !values.hideLabel ? \'bottom\' : \'middle\']}" style="{[values.autoFitErrors ? \'display:none\' : \'\']}" width="{errorIconWidth}">',
        '<div role="presentation" id="{id}-errorEl" class="{errorMsgCls}" style="display:none"></div>',
        '</td>',
        '<tpl elseif="msgTarget==\'under\'">',
        '<div role="presentation" id="{id}-errorEl" class="{errorMsgClass}" colspan="2" style="display:none"></div>',
        '{afterBodyEl}',
        '</td>',
        '</tpl>',
        '</tr>',
        {
            disableFormats: true
        }
    ],
    getLabelableRenderData: function() {
        var data = this.callOverridden();

        //support a tooltip
        data.helpPopup = this.helpPopup;

        return data;
    }
    //this would be a more surgical approach, except renderData doesnt seem to be applied right
    //afterLabelTextTpl: ['{%this.helpPopup%}<tpl if="helpPopup"><a href="#" data-qtip="{helpPopup}"><span class="labkey-help-pop-up">?</span></a></tpl>']
});

/**
 * @Override
 * This was added for the same purpose as the override of Ext4.form.field.Base directly above.  It allows the field to supply a helpPopup
 * config property, which creates a tooltip next to the field label.  The override of FieldContainer is required for nested fields
 * (like CheckboxGroup or RadioGroup) to support the same option as regular fields.
 */
Ext4.override(Ext4.form.FieldContainer, {
    labelableRenderTpl: Ext4.form.field.Base.prototype.labelableRenderTpl,
    getLabelableRenderData: Ext4.form.field.Base.prototype.getLabelableRenderData
});

/**
 * @Override
 * Sencha Issue: http://www.sencha.com/forum/showthread.php?269116-Ext-4.2.1.883-Sandbox-getRowStyleTableEl
 * Version: 4.2.1
 */
Ext4.override(Ext4.view.Table, {

    /**
     * @Override
     * Sencha Issue: http://www.sencha.com/forum/showthread.php?269116-Ext-4.2.1.883-Sandbox-getRowStyleTableEl
     * Version: 4.2.1
     */
    getRowStyleTableEl : function(item) {
        return this.el.down('table.' + Ext4.baseCSSPrefix  + 'grid-table');
    },

    /**
     * @Override
     * The call to isRowSelected in 4.2.1 could cause an exception when using a buffered store. This exception
     * is swallowed and the row would not prooperly render. This is most easily repro'd by having a 'pageSize' set
     * to the same as the number of records in the store.
     * Issue 22231 - Problems with long lists in file management tool
     * Sencha Issue: Not exactly the one (couldn't find it) but close: http://www.sencha.com/forum/showthread.php?266708
     * Version: 4.2.1
     */
    renderRow: function(record, rowIdx, out) {
        var me = this,
                isMetadataRecord = rowIdx === -1,
                selModel = me.selModel,
                rowValues = me.rowValues,
                itemClasses = rowValues.itemClasses,
                rowClasses = rowValues.rowClasses,
                cls,
                rowTpl = me.rowTpl;

        rowValues.record = record;
        rowValues.recordId = record.internalId;
        rowValues.recordIndex = rowIdx;
        rowValues.rowId = me.getRowId(record);
        rowValues.itemCls = rowValues.rowCls = '';
        if (!rowValues.columns) {
            rowValues.columns = me.ownerCt.columnManager.getColumns();
        }

        itemClasses.length = rowClasses.length = 0;

        if (!isMetadataRecord) {
            itemClasses[0] = Ext4.baseCSSPrefix + "grid-row";
            if (!me.ownerCt.disableSelection && selModel.isRowSelected) {
                // Selection class goes on the outermost row, so it goes into itemClasses
                if (selModel.isRowSelected(record)) {
                    itemClasses.push(me.selectedItemCls);
                }

                // Ensure selection class is added to selected records, and to the record *before* any selected record
                // When looking ahead to see if the next record is selected, ensure we do not look past the end!
                if (me.rowValues.recordIndex < me.store.getTotalCount() - 1 && selModel.isRowSelected(me.rowValues.recordIndex + 1) && !me.isRowStyleFirst(rowIdx + 1)) {
                    rowClasses.push(me.beforeSelectedItemCls);
                }
            }

            if (me.stripeRows && rowIdx % 2 !== 0) {
                rowClasses.push(me.altRowCls);
            }

            if (me.getRowClass) {
                cls = me.getRowClass(record, rowIdx, null, me.dataSource);
                if (cls) {
                    rowClasses.push(cls);
                }
            }
        }

        if (out) {
            rowTpl.applyOut(rowValues, out);
        } else {
            return rowTpl.apply(rowValues);
        }
    }
});

/**
 * @Override
 * Sencha Issue: http://www.sencha.com/forum/showthread.php?258397-4.2.0-RC-Selecting-a-grid-s-row-with-a-buffered-store-causes-a-JavaScript-error
 * Version: 4.2.1
 */
Ext4.override(Ext4.selection.Model, {
    /**
     * this override fixes multiple bugs when store is a buffered type
     * and fixed a bug that causes the initial hasId to not work because
     * the store.gerById() method expects an ID and not the record
     * also forcing the double verification when the record is not found because
     * it places both checks in a single if statement
     * @param record
     * @returns {boolean}
     */
    storeHasSelected: function(record) {
        var store = this.store,
                records,
                len, id, i, m;


        if (record.hasId()) {
            return store.getById(record.getId());
        } else {
            if (store.buffered) {//on buffered stores the map holds the data items
                records = [];
                for (m in store.data.map) {
                    records = records.concat(store.data.map[m].value);
                }
            } else {
                records = store.data.items;
            }
            len = records.length;
            id = record.internalId;


            for (i = 0; i < len; ++i) {
                if (id === records[i].internalId) {
                    return true;
                }
            }
        }
        return false;
    }
});

/**
 * @Override
 * Cannot find issue related to this override, however node.removeContext is being accessed when it is not available
 * Ext 4.2.1
 */
Ext4.override(Ext4.data.NodeStore, {
    onNodeRemove: function(parent, node, isMove) {
        var me = this;
        if (me.indexOf(node) != -1) {
            if (!node.isLeaf() && node.isExpanded() && node.removeContext) {
                node.parentNode = node.removeContext.parentNode;
                node.nextSibling = node.removeContext.nextSibling;
                me.onNodeCollapse(node, node.childNodes, true);
                node.parentNode = node.nextSibling = null;
            }
            me.remove(node);
        }
    }
});

Ext4.override(Ext4.data.Store, {
    loadRawData : function(data, append) {
        var me = this;

        // the load method stores the last set of options (action, filters, sorters) which are not provided in loadRawData
        me.lastOptions = {};

        me.callParent(arguments);
        me.fireEvent('load', me, me.data.getRange(), true);
    }
});

/**
 * RowExpander plugin is not properly configured to handle sandboxed ExtJS.
 */
Ext4.override(Ext4.grid.plugin.RowExpander, {
    rowBodyTrSelector: '.' + Ext4.baseCSSPrefix + 'grid-rowbody-tr',
    rowBodyHiddenCls: Ext4.baseCSSPrefix + 'grid-row-body-hidden',
    rowCollapsedCls: Ext4.baseCSSPrefix + 'grid-row-collapsed',
    getHeaderConfig: function() {
        var me = this;

        return {
            width: 24,
            lockable: false,
            sortable: false,
            resizable: false,
            draggable: false,
            hideable: false,
            menuDisabled: true,
            tdCls: Ext4.baseCSSPrefix + 'grid-cell-special',
            innerCls: Ext4.baseCSSPrefix + 'grid-cell-inner-row-expander',
            renderer: function(value, metadata) {

                if (!me.grid.ownerLockable) {
                    metadata.tdAttr += ' rowspan="2"';
                }
                return '<div class="' + Ext4.baseCSSPrefix + 'grid-row-expander"></div>';
            },
            processEvent: function(type, view, cell, rowIndex, cellIndex, e, record) {
                if (type == "mousedown" && e.getTarget('.' + Ext4.baseCSSPrefix + 'grid-row-expander')) {
                    me.toggleRow(rowIndex, record);
                    return me.selectRowOnExpand;
                }
            }
        };
    }
});

/**
 *  Issue 20644: Individual explorer - IE11 - choosing an item in long list snaps the list back to top
 *  Sencha issue: http://www.sencha.com/forum/showthread.php?279797-DataView-scrolls-to-the-top-in-IE-when-selecting-an-item
 */
Ext4.override(Ext4.view.View, {
    focusNode: function(rec){
        var me          = this,
                node        = me.getNode(rec, true),
                el          = me.el,
                adjustmentY = 0,
                adjustmentX = 0,
                elRegion    = el.getRegion(),
                nodeRegion;

        // Viewable region must not include scrollbars, so use
        // DOM client dimensions
        elRegion.bottom = elRegion.top + el.dom.clientHeight;
        elRegion.right = elRegion.left + el.dom.clientWidth;
        if (node) {
            nodeRegion = Ext4.fly(node).getRegion();
            // node is above
            if (nodeRegion.top < elRegion.top) {
                adjustmentY =nodeRegion.top - elRegion.top;
                // node is below
            } else if (nodeRegion.bottom > elRegion.bottom) {
                adjustmentY = nodeRegion.bottom - elRegion.bottom;
            }

            // node is left
            if (nodeRegion.left < elRegion.left) {
                adjustmentX =nodeRegion.left - elRegion.left;
                // node is right
            } else if (nodeRegion.right > elRegion.right) {
                adjustmentX = nodeRegion.right - elRegion.right;
            }

            if (adjustmentX || adjustmentY) {
                me.scrollBy(adjustmentX, adjustmentY, false);
            }

            // Poke on a tabIndex to make the node focusable.
            Ext4.fly(node).set({
                tabIndex: -1
            });

            node.focus();
        }
    }
});

/**
 * This is a change to the iteration of records when using a buffered store. The NodeCache assumed that recCount
 * would be safe in using to iterate across newNodes, however, it is not. This resulted bad behavior in the grid where
 * many rows might not appear at all. Sencha did not announce a solution so the fix is based on a community suggestion.
 * A better solution may exist.
 * Issue 22231 - Problems with long lists in file management tool
 * Sencha issue: http://www.sencha.com/forum/showthread.php?265323-Problem-when-scrolling-at-the-bottom-of-a-grid-with-buffered-store
 * Version: 4.2.1
 */
Ext4.override(Ext4.view.NodeCache, {
    scroll: function(newRecords, direction, removeCount) {
        var me = this,
                elements = me.elements,
                recCount = newRecords.length,
                i, el, removeEnd,
                newNodes,
                nodeContainer = me.view.getNodeContainer(),
                frag = document.createDocumentFragment();


        if (direction == -1) {
            for (i = (me.endIndex - removeCount) + 1; i <= me.endIndex; i++) {
                el = elements[i];
                delete elements[i];
                el.parentNode.removeChild(el);
            }
            me.endIndex -= removeCount;


            newNodes = me.view.bufferRender(newRecords, me.startIndex -= recCount);
            for (i = 0; i < recCount; i++) {
                elements[me.startIndex + i] = newNodes[i];
                frag.appendChild(newNodes[i]);
            }
            nodeContainer.insertBefore(frag, nodeContainer.firstChild);
        }


        else {
            removeEnd = me.startIndex + removeCount;
            for (i = me.startIndex; i < removeEnd; i++) {
                el = elements[i];
                delete elements[i];
                el.parentNode.removeChild(el);
            }
            me.startIndex = i;


            newNodes = me.view.bufferRender(newRecords, me.endIndex + 1);
            for (i = 0; i < newNodes.length; i++) { // was (i = 0; i < recCount; i++)
                elements[me.endIndex += 1] = newNodes[i];
                frag.appendChild(newNodes[i]);
            }
            nodeContainer.appendChild(frag);
        }

        me.count = me.endIndex - me.startIndex + 1;
    }
});

/**
 * Chrome only issue where a submenu appear when the parent selector is highlighted, but disappear soon
 * after hovering over the submenu making it nearly impossible to use/click on a submenu item.
 * Issue 23352: Chrome 43 breaks submenus
 * Sencha issue: https://www.sencha.com/forum/showthread.php?301116-Submenus-disappear-in-Chrome-43-beta
 * Version: 4.2.1
 */
if (Ext4.isChrome) {
    Ext4.override(Ext4.menu.Menu, {
        onMouseLeave: function(e) {
            var me = this,
                    item;

            for (var i=0; i < me.items.length; i++) {
                item = me.items.get(i);
                if (item.menu && item.menu.isVisible()) {
                    return;
                }
            }

            me.deactivateActiveItem();

            if (me.disabled) {
                return;
            }

            me.fireEvent('mouseleave', me, e);
        }
    });
}

/**
 * @Override
 * Ext4.Ajax.timeout is not respected as a global for the Ext4 library. In order to have this work,
 * several overrides need to be made.
 * Issue 23920: Files web part times out too quickly
 * Sencha issue: https://www.sencha.com/forum/showthread.php?188502&p=759207
 * Version: 4.2.1
 */
Ext4.override(Ext4.data.Connection, { timeout: Ext4.Ajax.timeout });
Ext4.override(Ext4.data.proxy.Server, { timeout: Ext4.Ajax.timeout });
Ext4.override(Ext4.form.Basic, { timeout: Ext4.Ajax.timeout / 1000 });
Ext4.override(Ext4.form.action.Action, { timeout: Ext4.Ajax.timeout / 1000 });