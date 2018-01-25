/* Apply patches found for Ext 4.2.1 here */

// Disable the dynamic class loader (docs mention this should be the default)
Ext4.Loader.setConfig({ enabled: false });

// Set USE_NATIVE_JSON so Ext4.decode and Ext4.encode use JSON.parse and JSON.stringify instead of eval
Ext4.USE_NATIVE_JSON = true;

// set the default ajax timeout from 30's to 5 minutes
Ext4.Ajax.timeout = 5 * 60 * 1000;

// set csrf value for all requests
if (!Ext4.Ajax.defaultHeaders) {
    Ext4.Ajax.defaultHeaders = {};
}
Ext4.apply(Ext4.Ajax.defaultHeaders, LABKEY.defaultHeaders);

/**
 * Override basic submit function to add CSRF (and other LabKey default headers) to Ext4 standardsubmit submits.
 * This is needed because the AJAX default headers are ignored in these submits.
 * Issue 32481: Insert/update of attachment report doesn't work with "All POST requests" CSRF setting
 */
Ext4.override(Ext4.form.Basic, {
    submit: function(options) {
        options = options || {};
        var me = this,
            action;

        if (options.standardSubmit || me.standardSubmit) {
            action = 'standardsubmit';
            // Begin patch
            var params = {};
            for (var headerName in LABKEY.defaultHeaders) {
                if (LABKEY.defaultHeaders.hasOwnProperty(headerName)) {
                    params[headerName] = LABKEY.defaultHeaders[headerName];
                }
            }
            if(options.params)
                Ext4.merge(options.params, params);
            else
                options.params = params;
            // End patch
        } else {
            action = me.api ? 'directsubmit' : 'submit';
        }

        return me.doAction(action, options);
    }
});

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

+function() {
    Ext4.ns('LABKEY.ext4.Util');
    LABKEY.ext4.Util.resizeToContainer = function(ct, options) {
        if (!ct || !ct.rendered) {
            return;
        }

        // default options
        var config = {
            offsetY: 60,
            overrideMinWidth: false,
            paddingHeight: 0,
            paddingWidth: 1, // allow 1 px padding to avoid occasional border cutoff
            skipHeight: true,
            skipWidth: false
        };

        // container-specified options
        if (ct && Ext4.isObject(ct.autoResize)) {
            config = Ext4.apply(config, ct.autoResize);
        }

        // explicit options
        if (Ext4.isObject(options)) {
            config = Ext4.apply(config, options);
        }
        // else ignore parameters

        if (config.skipWidth && config.skipHeight) {
            return;
        }

        var height = 0;
        var width = 0;

        if (!config.skipWidth) {
            width = ct.el.parent().getBox().width;
        }
        if (!config.skipHeight) {
            height = window.innerHeight - ct.el.getXY()[1];
        }

        var padding = [config.paddingWidth, config.paddingHeight];

        var size = {
            width: Math.max(100, width - padding[0]),
            height: Math.max(100, height - padding[1] - config.offsetY)
        };

        if (config.skipWidth) {
            ct.setHeight(size.height);
            if (config.overrideMinWidth) {
                ct.minWidth = size.width;
            }
        }
        else if (config.skipHeight) {
            ct.setWidth(size.width);
        }
        else {
            ct.setSize(size);
        }
        ct.doLayout();
    };

    Ext4.override(Ext4.AbstractComponent, {
        initContainer: function(ct) {
            // examine the specified render target (e.g. via renderTo property or comp.render('someTarget'))
            this._validAutoResizeRenderTarget = Ext4.isString(ct);
            return this.callParent(arguments);
        },
        afterRender: function() {
            var me = this;
            me.callParent(arguments);
            if (!me.ownerCt && me._validAutoResizeRenderTarget === true && (me.autoResize === true || Ext4.isObject(me.autoResize)) && Ext4.isFunction(me.doLayout)) {
                me.on('afterrender', function() {
                    var resize = function() { LABKEY.ext4.Util.resizeToContainer(this, { AUTO_RESIZE_FLAG: true }) };
                    Ext4.EventManager.onWindowResize(resize, this, {delay: 75});
                    Ext4.defer(function() { resize.call(this) }, 100, this);
                }, me, {single: true});
            }
        }
    });
}();

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
     * Sencha Issue: https://www.sencha.com/forum/showthread.php?296844-HTML-injection-attack-against-the-grid-row-s-TR-id-and-dataRecordId-attributes
     * Version: 4.2.1
     */
    rowTpl: [
        '{%',
            'var dataRowCls = values.recordIndex === -1 ? "" : " ' + Ext4.baseCSSPrefix + 'grid-data-row";',
        '%}',
        '<tr role="row" {[values.rowId ? ("id=\\"" + this.encodeId(values.rowId) + "\\"") : ""]} ',
            'data-boundView="{view.id}" ',
            'data-recordId="{record.internalId:htmlEncode}" ',
            'data-recordIndex="{recordIndex}" ',
            'class="{[values.itemClasses.join(" ")]} {[values.rowClasses.join(" ")]}{[dataRowCls]}" ',
            '{rowAttr:attributes} tabIndex="-1">',
            '<tpl for="columns">' +
                '{%',
                    'parent.view.renderCell(values, parent.record, parent.recordIndex, xindex - 1, out, parent)',
                '%}',
            '</tpl>',
        '</tr>',
        {
            encodeId: function(recordId) {
                return Ext4.htmlEncode(recordId);
            },
            priority: 0
        }
    ],

    // in addition to updating rowTpl, update direct setting of the attribute in the DOM
    onIdChanged : function(store, rec, oldId, newId, oldInternalId) {
        var me = this,
            rowDom;

        if (me.viewReady) {
            rowDom = me.getNodeById(oldInternalId);
            if (rowDom) {
                rowDom.setAttribute('data-recordId', Ext4.htmlEncode(rec.internalId));
                rowDom.id = me.getRowId(rec);
            }
        }
    },
    

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


/**
 * Override Ext4.data.Model, Ext4.data.NodeInterface and Ext4.data.TreeStore to add support for Ext4.2.1 tree store filter
 * Issue 26404: filters and sorts on columns from look up tables result in duplicate nodes.
 * Copied from https://fiddle.sencha.com/#fiddle/ra
 * ExtJS forum thread: https://www.sencha.com/forum/showthread.php?184010-TreeStore-filtering.
 * Update: modified patch to pass internalId on copy only if it's a deep copy.
 */
Ext4.override(Ext4.data.Model, {
    // fixes the issue where null newId doesn't use me.internalId to copy the record using its existing ID
    // Modified original patch to only use me.internalId when deep copy, which is used for tree nodes
    copy: function(newId, deep) {
        var me = this;
        return new me.self(me.raw, newId || (deep ? me.internalId : undefined), null, Ext4.apply({}, me[me.persistenceProperty]));
    }
});
Ext4.override(Ext4.data.NodeInterface, {
    statics: {
        getPrototypeBody: function() {
            var result = this.callParent();

            result.filter = function(property, value, anyMatch, caseSensitive, matchParentNodes) {
                var filters = [];

                //support for the simple case of filtering by property/value
                if (Ext4.isString(property)) {
                    filters.push(new Ext4.util.Filter({
                        property: property,
                        value: value,
                        anyMatch: anyMatch,
                        caseSensitive: caseSensitive
                    }));
                } else if (Ext4.isArray(property) || property instanceof Ext4.util.Filter) {
                    filters = filters.concat(property);
                }

                // At this point we have an array of zero or more Ext4.util.Filter objects to filter with,
                // so here we construct a function that combines these filters by ANDing them together
                // and filter by that.
                return this.filterBy(Ext4.util.Filter.createFilterFn(filters), null, matchParentNodes);
            };

            result.filterBy = function(fn, scope, matchParentNodes) {
                var me = this,
                        newNode = me.copy(null, true),
                        matchedNodes = [],
                        allNodes = [],
                        markMatch, i;

                markMatch = function(node) {
                    node.filterMatch = true;
                    if (node.parentNode) {
                        markMatch(node.parentNode);
                    }
                };

                newNode.cascadeBy(function(node) {
                    allNodes.push(node);
                    if (fn.call(scope || me, node)) {
                        if (node.isLeaf() || matchParentNodes === true) {
                            matchedNodes.push(node);
                        }
                    }
                });

                for (i = 0; i < matchedNodes.length; i++) {
                    markMatch(matchedNodes[i])
                }

                for (i = 0; i < allNodes.length; i++) {
                    if (allNodes[i].filterMatch !== true) {
                        allNodes[i].remove();
                    }
                }

                return newNode;
            };

            return result;
        }
    }
});
Ext4.define('Ext4.override.data.TreeStore', {
    override: 'Ext4.data.TreeStore',
    hasFilterListeners: false,
    filterListeners: {
        move: function(node, oldParent, newParent, index) {
            var me = this,
                    snapshotNode = me.snapshot.findChild('id', node.get('id'), true),
                    snapshotNewParent = me.snapshot.findChild('id', newParent.get('id'), true) || me.snapshot;
            snapshotNewParent.insertChild(index, snapshotNode);
        },
        append: function(parentNode, appendedNode, index) {
            var me = this,
                    snapshotParentNode = me.snapshot.findChild('id', parentNode.get('id'), true) || me.shapshot,
                    foundNode = me.snapshot.findChild('id', appendedNode.get('id'), true);
            snapshotParentNode.insertChild(index, foundNode || appendedNode.copy(null, true));
        },
        insert: function(parentNode, insertedNode, refNode) {
            var me = this,
                    snapshotParentNode = me.snapshot.findChild('id', parentNode.get('id'), true) || me.snapshot,
                    foundNode = me.snapshot.findChild('id', insertedNode.get('id'), true);

            snapshotParentNode.insertBefore(foundNode || insertedNode.copy(null, true), refNode);
        },
        remove: function(parentNode, removedNode, isMove) {
            var me = this;
            if (!isMove) {
                me.snapshot.findChild('id', removedNode.get('id'), true).remove(true);
            }
        }
    },
    filter: function(filters, value, anyMatch, caseSensitive, matchParentNodes) {
        if (Ext4.isString(filters)) {
            filters = {
                property: filters,
                value: value,
                root: 'data',
                anyMatch: anyMatch,
                caseSensitive: caseSensitive
            };
        }

        var me = this,
                decoded = me.decodeFilters(filters),
                i,
                doLocalSort = me.sorters.length && me.sortOnFilter && !me.remoteSort,
                length = decoded.length,
                filtered;

        for (i = 0; i < length; i++) {
            me.filters.replace(decoded[i]);
        }

        if (me.remoteFilter) {
            // So that prefetchPage does not consider the store to be fully loaded if the local count is equal to the total count
            delete me.totalCount;

            // Reset to the first page, the filter is likely to produce a smaller data set
            me.currentPage = 1;
            //the load function will pick up the new filters and request the filtered data from the proxy
            me.load();
        } else {
            if (me.filters.getCount()) {
                me.snapshot = me.snapshot || me.getRootNode().copy(null, true);

                // Filter the unfiltered dataset using the filter set
                filtered = me.setRootNode(me.snapshot.filter(me.filters.items, null, null, null, matchParentNodes));
                filtered.getOwnerTree().expandAll();

                me.addFilterListeners();

                if (doLocalSort) {
                    me.sort();
                } else {
                    // fire datachanged event if it hasn't already been fired by doSort
                    me.fireEvent('datachanged', me);
                    me.fireEvent('refresh', me);
                }
            }
        }
        me.fireEvent('filterchange', me, me.filters.items);
    },
    addFilterListeners: function() {
        var me = this;

        if (!me.hasFilterListeners) {
            me.on(me.filterListeners);
            me.hasFilterListeners = true;
        }
    },
    filterBy: function(fn, scope, matchParentNodes) {
        var me = this;

        me.snapshot = me.snapshot || me.getRootNode().copy(null, true);
        me.setRootNode(me.queryBy(fn, scope || me, matchParentNodes));

        me.addFilterListeners();

        me.fireEvent('datachanged', me);
        me.fireEvent('refresh', me);
    },
    queryBy: function(fn, scope, matchParentNodes) {
        var me = this;
        return (me.snapshot || me.getRootNode()).filterBy(fn, scope || me, matchParentNodes);
    },
    clearFilter: function(suppressEvent) {
        var me = this;

        me.filters.clear();

        if (me.hasFilterListeners) {
            me.un(me.filterListeners);
            me.hasFilterListeners = false;
        }

        if (me.remoteFilter) {

            // In a buffered Store, the meaning of suppressEvent is to simply clear the filters collection
            if (suppressEvent) {
                return;
            }

            // So that prefetchPage does not consider the store to be fully loaded if the local count is equal to the total count
            delete me.totalCount;

            // For a buffered Store, we have to clear the prefetch cache because the dataset will change upon filtering.
            // Then we must prefetch the new page 1, and when that arrives, reload the visible part of the Store
            // via the guaranteedrange event
            if (me.buffered) {
                me.data.clear();
                me.loadPage(1);
            } else {
                // Reset to the first page, clearing a filter will destroy the context of the current dataset
                me.currentPage = 1;
                me.load();
            }
        } else if (me.isFiltered()) {
            me.setRootNode(me.snapshot);
            delete me.snapshot;

            if (suppressEvent !== true) {
                me.fireEvent('datachanged', me);
                me.fireEvent('refresh', me);
            }
        }

        if (me.sorters && me.sorters.items.length > 0) {
            me.sort();
        }

        me.fireEvent('filterchange', me, me.filters.items);
    },
    isFiltered: function() {
        var snapshot = this.snapshot;
        return !!(snapshot && snapshot !== this.getRootNode());
    },
    addFilter: function(filters, applyFilters) {
        var me = this,
                decoded,
                i,
                length;

        // Decode passed filters and replace/add into the filter set
        decoded = me.decodeFilters(filters);
        length = decoded.length;
        for (i = 0; i < length; i++) {
            me.filters.replace(decoded[i]);
        }

        if (applyFilters !== false) {
            me.filter();
        }
        me.fireEvent('filterchange', me, me.filters.items);
    },

    removeFilter: function(toRemove, applyFilters) {
        var me = this;

        if (!me.remoteFilter && me.isFiltered()) {
            if (toRemove instanceof Ext4.util.Filter) {
                me.filters.remove(toRemove);
            } else {
                me.filters.removeAtKey(toRemove);
            }

            if (applyFilters !== false) {

                // Not gone down to zero filters - re-filter Store
                if (me.filters.getCount()) {
                    me.filter();
                }

                // No filters left - let clearFilter do its thing.
                else {
                    me.clearFilter();
                }
            }
            me.fireEvent('filterchange', me, me.filters.items);
        }
    },
    sortOnFilter: true,
    remoteFilter: false
});

/**
 * Patch to allow responsive modal interaction for Ext4 Ext.Window components.
 * At small screen width (user configurable), the popup will take full screen width.
 * For modal dialog with 'closable' set to true, clicking outside the popup will close the popup.
 * Configs:
 *      suppressResponsive: true to opt out of this feature, default false
 *      smallScreenWidth: the pixel screen width at which responsive sizing kicks in, default 480
 *      maximizeOnSmallScreen: true to maximize popup to full screen width and height on small screen,
 *                              false to only take full width, default false.
 *      closableOnMaskClick: true to always support closing modal by click on mask regardless of screen size,
 *                            otherwise only closable on mask click for small screens, default false
 *      useExtStyle: true to use ext style, false to use bootstrap-like style, default true
 */
Ext4.override(Ext4.window.Window, {
    constructor: function() {
        var isAutoShow = this.autoShow || (arguments && arguments[0] ? arguments[0].autoShow : false);
        if (isAutoShow) {
            // temporarily disable autoShow so that responsive configs are applied before show
            this.autoShow = false;
            if (arguments && arguments[0] && arguments[0].autoShow)
                arguments[0].autoShow = false;
        }

        this.callParent(arguments);

        if (this.suppressResponsive) {
            if (isAutoShow && !this.isContained) {
                this.autoShow = true;
                this.show();
            }
            return;
        }

        // experimental, change look of windows
        var useBootstrapStyle = this.useExtStyle === undefined ? false : !this.useExtStyle;
        if (useBootstrapStyle) {
            if (!this.bodyCls)
                this.bodyCls = '';
            this.bodyCls += ' modal-body';
            if (!this.cls)
                this.cls = '';
            this.cls += ' modal-content';
            this.shadow = false;
        }

        var useMaxWidth = window.innerWidth < (this.smallScreenWidth ? this.smallScreenWidth : 481);
        useMaxWidth = useMaxWidth || (this.width && (this.width > window.innerWidth)); // if configured width is large than available screen size.
        useMaxWidth = useMaxWidth || (this.minWidth && (this.minWidth > window.innerWidth)); // if configured min-width is large than available screen size.

        if (useMaxWidth) {
            if (this.maximizeOnSmallScreen) {
                this.maximized = true;
            }
            else {
                var getBodyContainerWidth = function() {
                    var containerWidth = window.innerWidth;
                    var parent = Ext4.getBody();
                    var container = parent.query("> div.container");
                    if (container && container[0])
                        containerWidth = container[0].offsetWidth;
                    else {
                        // if template is not body
                        container = parent.query("> div > div.container");
                        if (container && container[0])
                            containerWidth = container[0].offsetWidth;
                    }

                    return containerWidth;
                };

                var windowWidth = window.innerWidth;
                var containerWidth = getBodyContainerWidth();
                if (windowWidth - containerWidth < 30)
                    this.width = containerWidth - 20*2; // reserve extra padding for scrollbar
                else
                    this.width = containerWidth;
            }
        }

        var me = this;
        if (this.modal && this.closable && (useMaxWidth || this.closableOnMaskClick)) {
            var parentCmp = Ext4.getBody();
            this.clickOutHandler = function(){
                me.close(me.closeAction);
            };
            this.clickOutParentCmp = parentCmp;
            this.mon(this.clickOutParentCmp, 'click', this.clickOutHandler, this, { delegate: '.x4-mask' });
        }

        if (isAutoShow && !this.isContained) {
            this.autoShow = true;
            this.show();
        }
    }
});

