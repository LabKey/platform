
Ext4.define('LABKEY.VaccineDesign.BaseDataView', {

    extend : 'Ext.panel.Panel',

    cls : 'study-vaccine-design',

    border : false,

    mainTitle : null,

    cellEditField : null,

    studyDesignQueryNames : null,

    // for a DataSpace project, some scenarios don't make sense to allow insert/update
    disableEdit : false,

    DELETE_ICON_CLS : 'fa fa-trash',
    ADD_ICON_CLS : 'fa fa-plus-circle',

    constructor : function(config)
    {
        this.callParent([config]);
        this.addEvents('dirtychange', 'loadcomplete', 'celledited', 'beforerowdeleted');
    },

    initComponent : function()
    {
        this.items = [
            this.getMainTitle()
            // Note: this.getDataView() will be added after the store loads in this.loadDataViewStore()
        ];

        // Pre-load the study design lookup queries that will be used in dropdowns for this page.
        // Note: these stores are also used for getting display values in the data view XTempalte so don't
        //      bind the data view store until they are all loaded.
        if (Ext4.isArray(this.studyDesignQueryNames) && this.studyDesignQueryNames.length > 0)
        {
            var loadCounter = 0;
            Ext4.each(this.studyDesignQueryNames, function(queryName)
            {
                var studyDesignStore = LABKEY.VaccineDesign.Utils.getStudyDesignStore(queryName);
                studyDesignStore.on('load', function()
                {
                    loadCounter++;

                    if (loadCounter == this.studyDesignQueryNames.length)
                        this.loadDataViewStore();
                }, this);
            }, this);
        }
        else
        {
            this.loadDataViewStore();
        }

        this.callParent();
    },

    getMainTitle : function()
    {
        if (!this.mainTitleCmp && this.mainTitle != null)
        {
            this.mainTitleCmp = Ext4.create('Ext.Component', {
                html: '<div class="main-title">' + Ext4.util.Format.htmlEncode(this.mainTitle) + '</div>'
            });
        }

        return this.mainTitleCmp;
    },

    getDataView : function()
    {
        if (!this.dataView)
        {
            this.dataView = Ext4.create('Ext.view.View', {
                tpl: this.getDataViewTpl(),
                store: this.getStore(),
                itemSelector: 'tr.row'
            });

            this.dataView.on('itemclick', this.onDataViewItemClick, this);
            this.dataView.on('refresh', this.attachAddRowListeners, this, {buffer: 250});
        }

        return this.dataView;
    },

    getDataViewTpl : function()
    {
        var tplArr = [],
            tdCls = this.disableEdit ? 'cell-display' : 'cell-value',
            columns = this.getColumnConfigs();

        tplArr.push('<table class="outer">');
        tplArr = tplArr.concat(this.getTableHeaderRowTpl(columns));

        // data rows
        tplArr.push('<tpl for=".">');
        tplArr.push('<tr class="row {[xindex % 2 === 0 ? "alternate-row" : ""]}">');
        if (!this.disableEdit)
            tplArr.push('<td class="cell-display action"><i class="' + this.DELETE_ICON_CLS + '" outer-index="{[xindex-1]}"/></td>');
        Ext4.each(columns, function(column)
        {
            if (Ext4.isString(column.dataIndex))
            {
                var checkMissingReqTpl = '';
                if (column.required)
                    checkMissingReqTpl = ' {[this.checkMissingRequired(values, "' + column.dataIndex + '")]}';

                if (Ext4.isObject(column.subgridConfig) && Ext4.isArray(column.subgridConfig.columns))
                {
                    tplArr = tplArr.concat(this.getSubGridTpl(column.dataIndex, column.subgridConfig.columns));
                }
                else if (Ext4.isString(column.queryName))
                {
                    tplArr.push('<td class="' + tdCls + checkMissingReqTpl + '" data-index="' + column.dataIndex + '">'
                            + '{[this.getLabelFromStore(values["' + column.dataIndex + '"], "' + column.queryName + '")]}'
                            + '</td>');
                }
                else if (Ext4.isString(column.lookupStoreId) && Ext4.isDefined(column.dataIndexArrFilterValue))
                {
                    tplArr.push('<td class="' + tdCls + checkMissingReqTpl + '" data-index="' + column.dataIndex
                            + '" data-filter-value="' + column.dataIndexArrFilterValue + '">'
                            + '{[this.getDisplayValue(values["' + column.dataIndex + '"], '
                            + '"' + column.dataIndexArrFilterProp + '", "' + column.dataIndexArrFilterValue + '", '
                            + '"' + column.dataIndexArrValue + '", "' + column.lookupStoreId + '")]}'
                            + '</td>');
                }
                else
                {
                    tplArr.push('<td class="' + tdCls + checkMissingReqTpl + '" data-index="' + column.dataIndex + '">'
                            + '{[this.getDisplayValue(values["' + column.dataIndex + '"])]}'
                            + '</td>');
                }
            }
        }, this);
        tplArr.push('</tr>');
        tplArr.push('</tpl>');

        tplArr = tplArr.concat(this.getEmptyTableTpl(columns));
        tplArr = tplArr.concat(this.getAddNewRowTpl(columns));
        tplArr.push('</table>');

        tplArr.push({
            getDisplayValue : function(val, arrPropFilterName, arrPropFilterVal, arrPropDisplayField, lookupStoreId)
            {
                // allow showing a certain filtered row from an array
                if (Ext4.isDefined(arrPropDisplayField))
                {
                    var matchingIndex = LABKEY.VaccineDesign.Utils.getMatchingRowIndexFromArray(val, arrPropFilterName, arrPropFilterVal);
                    if (matchingIndex > -1 && Ext4.isObject(val[matchingIndex]))
                        val = val[matchingIndex][arrPropDisplayField];
                    else
                        val = '';
                }

                // if we have a specific lookupStoreId, get the label value from the matching RowId record in that store
                if (Ext4.isDefined(lookupStoreId) && val != null && val != '')
                {
                    var store = Ext4.getStore(lookupStoreId);
                    if (store != null)
                    {
                        var record = store.findRecord('RowId', val);
                        if (record != null)
                            val = record.get('Label');
                    }
                }

                // need to htmlEncode and then handle newlines in multiline text fields (i.e. Treatment/Description)
                val = Ext4.util.Format.htmlEncode(val);
                val = val.replace(/\n/g, '<br/>');

                return val;
            },

            getLabelFromStore : function(val, queryName)
            {
                if (val != null && val != '')
                    val = LABKEY.VaccineDesign.Utils.getLabelFromStore(queryName, val);

                return Ext4.util.Format.htmlEncode(val);
            },

            checkMissingRequired : function(values, dataIndex)
            {
                if (values[dataIndex] == null || values[dataIndex] == '')
                    return ' missing-required';

                return '';
            }
        });

        return new Ext4.XTemplate(tplArr);
    },

    getSubGridTpl : function(dataIndex, columns)
    {
        var tplArr = [],
            tdCls = this.disableEdit ? 'cell-display' : 'cell-value';

        tplArr.push('<td class="cell-display">');

        // only show the subgrid if we are allowing edits of if it has at least one row
        tplArr.push('<tpl if="' + dataIndex + '.length &gt; 0 || ' + !this.disableEdit + '">');

        tplArr.push('<table class="subgrid subgrid-' + dataIndex + '" width="100%">');
        tplArr = tplArr.concat(this.getTableHeaderRowTpl(columns));

        // data rows
        tplArr.push('<tpl for="' + dataIndex + '">');
        tplArr.push('<tr class="subrow">');
        if (!this.disableEdit)
        {
            tplArr.push('<td class="cell-display action">');
            tplArr.push('<i class="' + this.DELETE_ICON_CLS + '" subgrid-data-index="' + dataIndex + '" subgrid-index="{[xindex-1]}"/>');
            tplArr.push('</td>');
        }
        Ext4.each(columns, function(column)
        {
            if (Ext4.isString(column.dataIndex))
            {
                if (Ext4.isString(column.queryName))
                {
                    var checkMissingReqTpl = '';
                    if (column.required)
                        checkMissingReqTpl = ' {[this.checkMissingRequired(values, "' + column.dataIndex + '")]}';

                    tplArr.push('<td class="' + tdCls + checkMissingReqTpl + '" outer-data-index="' + dataIndex + '" '
                            + 'data-index="' + column.dataIndex + '" subgrid-index="{[xindex-1]}">'
                            + '{[this.getLabelFromStore(values["' + column.dataIndex + '"], "' + column.queryName + '")]}'
                            + '</td>');
                }
                else
                {
                    tplArr.push('<td class="' + tdCls + '" outer-data-index="' + dataIndex + '" data-index="' + column.dataIndex + '" '
                            + 'subgrid-index="{[xindex-1]}">{' + column.dataIndex + ':htmlEncode}</td>');
                }
            }
        }, this);
        tplArr.push('</tr>');
        tplArr.push('</tpl>');

        tplArr = tplArr.concat(this.getAddNewRowTpl(columns, dataIndex));
        tplArr.push('</table>');
        tplArr.push('</tpl>');
        tplArr.push('</td>');

        return tplArr;
    },

    getTableHeaderRowTpl : function(columns)
    {
        var tplArr = [];

        tplArr.push('<tr class="header-row">');
        if (!this.disableEdit)
            tplArr.push('<td class="cell-display">&nbsp;</td>');
        Ext4.each(columns, function(column)
        {
            tplArr.push('<td class="cell-display" width="' + (column.width || 100) +'px">' + Ext4.util.Format.htmlEncode(column.label) + '</td>');
        }, this);
        tplArr.push('</tr>');

        return tplArr;
    },

    getEmptyTableTpl : function(columns)
    {
        var tplArr = [];

        if (this.disableEdit)
        {
            tplArr.push('<tpl if="length == 0">');
            tplArr.push('<tr>');
            tplArr.push('<td class="cell-display empty" colspan="' + columns.length + '">No data to show.</td>');
            tplArr.push('</tr>');
            tplArr.push('</tpl>');
        }

        return tplArr;
    },

    getAddNewRowTpl : function(columns, dataIndex)
    {
        var tplArr = [];

        if (!this.disableEdit)
        {
            tplArr.push('<tr>');
            tplArr.push('<td class="cell-display">&nbsp;</td>');
            tplArr.push('<td class="cell-display action" colspan="' + columns.length + '">');
            if (Ext4.isString(dataIndex))
                tplArr.push('<i class="' + this.ADD_ICON_CLS + '" data-index="' + dataIndex + '" outer-index="{[xindex-1]}"> Add new row</i>');
            else
                tplArr.push('<i class="' + this.ADD_ICON_CLS + '"> Add new row</i>');
            tplArr.push('</td>');
            tplArr.push('</tr>');
        }

        return tplArr;
    },

    onDataViewItemClick : function(view, record, item, index, event)
    {
        if (!this.disableEdit)
        {
            // handle click on a cell that is editable
            if (event.target.getAttribute('class').indexOf('cell-value') > -1 && event.target.hasAttribute('data-index'))
            {
                if (this.cellEditField != null)
                    this.clearPreviousCellEditField();
                else
                    this.createNewCellEditField(event.target, record, index);
            }
            // handle click on trashcan icon to delete row
            else if (event.target.getAttribute('class') == this.DELETE_ICON_CLS)
            {
                if (event.target.hasAttribute('outer-index'))
                {
                    this.removeOuterRecord(this.mainTitle, record);
                }
                // handle click on trashcan icon for outer grid
                else if (event.target.hasAttribute('subgrid-data-index') && event.target.hasAttribute('subgrid-index'))
                {
                    this.removeSubgridRecord(event.target, record);
                }
            }
        }
    },

    clearPreviousCellEditField : function()
    {
        if (this.cellEditField != null)
            this.updateStoreValueForCellEdit();
    },

    createNewCellEditField : function(target, record, index)
    {
        var dataIndex = target.getAttribute('data-index'),
            dataFilterValue = target.getAttribute('data-filter-value'),
            outerDataIndex = target.getAttribute('outer-data-index'),
            subgridIndex = Number(target.getAttribute('subgrid-index')),
            column = this.getColumnConfig(dataIndex, dataFilterValue, outerDataIndex),
            editor = this.getColumnEditorConfig(column);

        if (editor != null)
        {
            // clear the existing HTML in the td cell and remove the missing-required cls while in edit mode
            var targetEl = Ext4.get(target);
            targetEl.update('');
            targetEl.removeCls('missing-required');

            // create a new form field to place in the td cell
            this.cellEditField = Ext4.create(editor.type, Ext4.apply(editor.config, {
                renderTo: target,
                value: this.getCurrentCellValue(column, record, dataIndex, outerDataIndex, subgridIndex),
                storeIndex: index,
                dataFilterValue: dataFilterValue,
                outerDataIndex: outerDataIndex,
                subgridIndex: subgridIndex
            }));

            // add listeners for when to apply the updated value and clear the input field
            this.cellEditField.on('blur', this.updateStoreValueForCellEdit, this);

            // give the new field focus after a brief delay, and select text on focus if not a combo
            var xtype = this.cellEditField.getXType();
            var isCombo = xtype == 'labkey-combo' || xtype == 'combobox' || xtype == 'combo';
            this.cellEditField.focus(!isCombo, true);
        }
    },

    getCurrentCellValue : function(column, record, dataIndex, outerDataIndex, subgridIndex)
    {
        return Ext4.isString(outerDataIndex) ? record.get(outerDataIndex)[subgridIndex][dataIndex] : record.get(dataIndex);
    },

    updateStoreValueForCellEdit : function()
    {
        if (this.cellEditField != null)
        {
            var fieldName = this.cellEditField.getName(),
                newValue = this.cellEditField.getValue(),
                index = this.cellEditField.storeIndex,
                record = this.getStore().getAt(index),
                dataFilterValue = this.cellEditField.dataFilterValue,
                outerDataIndex = this.cellEditField.outerDataIndex,
                subgridIndex = Number(this.cellEditField.subgridIndex);

            if (Ext4.isString(outerDataIndex))
            {
                if (!isNaN(subgridIndex) && Ext4.isArray(record.get(outerDataIndex)))
                    record.get(outerDataIndex)[subgridIndex][fieldName] = newValue;
            }
            else
            {
                var column = this.getColumnConfig(fieldName, dataFilterValue, outerDataIndex);
                this.updateStoreRecordValue(record, column, newValue);
            }

            this.cellEditField = null;
            this.refresh(true);
        }
    },

    updateStoreRecordValue : function(record, column, newValue)
    {
        record.set(column.dataIndex, newValue);
        this.fireEvent('celledited', this);
    },

    removeOuterRecord : function(title, record)
    {
        var msg = this.getDeleteConfirmationMsg() != null ? this.getDeleteConfirmationMsg() : 'Are you sure you want to delete the selected row?';

        Ext4.Msg.confirm('Confirm Delete: ' + title, msg, function(btn)
        {
            if (btn == 'yes')
            {
                this.fireEvent('beforerowdeleted', this, record);

                // suspend events on remove so that we don't re-render the dataview twice
                this.getStore().suspendEvents();
                this.getStore().remove(record);
                this.getStore().resumeEvents();

                this.refresh(true);
            }
        }, this);
    },

    removeSubgridRecord : function(target, record)
    {
        var subgridDataIndex = target.getAttribute('subgrid-data-index'),
            subgridArr = record.get(subgridDataIndex);

        if (Ext4.isArray(subgridArr))
        {
            Ext4.Msg.confirm('Confirm Delete: ' + subgridDataIndex, 'Are you sure you want to delete the selected row?', function(btn)
            {
                if (btn == 'yes')
                {
                    subgridArr.splice(target.getAttribute('subgrid-index'), 1);
                    this.refresh(true);
                }
            }, this);
        }
    },

    attachAddRowListeners : function(view)
    {
        var addIconEls = Ext4.DomQuery.select('i.' + this.ADD_ICON_CLS.replace(/ /g, '.'), view.getEl().dom);

        Ext4.each(addIconEls, function(addIconEl)
        {
            if (addIconEl.hasAttribute('data-index'))
                Ext4.get(addIconEl).on('click', this.addNewSubgridRow, this);
            else
                Ext4.get(addIconEl).on('click', this.addNewOuterRow, this);
        }, this);
    },

    addNewOuterRow : function()
    {
        // suspend events on insert so that we don't re-render the dataview twice
        this.getStore().suspendEvents();
        this.getStore().insert(this.getStore().getCount(), this.getNewModelInstance());
        this.getStore().resumeEvents();

        // on refresh, call to give focus to the first column of the new row
        this.getDataView().on('refresh', function(){
            var index = this.getStore().getCount() - 1,
                selector = 'table.outer tr.row:last td.cell-value:first';
            this.giveLastRowFocus(index, selector);
        }, this, {single: true});

        this.refresh(true);
    },

    addNewSubgridRow : function(event, target)
    {
        var dataIndex = target.getAttribute('data-index'),
            rowIndex = Number(target.getAttribute('outer-index'));

        if (Ext4.isString(dataIndex) && Ext4.isNumber(rowIndex))
        {
            var record = this.getStore().getAt(rowIndex),
                dataIndexArr = record.get(dataIndex);

            if (Ext4.isArray(dataIndexArr))
                record.set(dataIndex, dataIndexArr.concat([{}]));

            // on refresh, call to give focus to the first column of the new row
            this.getDataView().on('refresh', function(){
                var index = rowIndex,
                    selector = 'table.subgrid-' + dataIndex + ':nth(' + (rowIndex+1) + ') tr.subrow:last td.cell-value:first';
                this.giveLastRowFocus(index, selector);
            }, this, {single: true});

            this.refresh(true);
        }
    },

    giveLastRowFocus : function(index, selector)
    {
        var lastRowFirstCell = Ext4.DomQuery.select(selector, this.getDataView().getEl().dom),
            record = this.getStore().getAt(index);

        if (Ext4.isArray(lastRowFirstCell) && lastRowFirstCell.length == 1)
            this.createNewCellEditField(Ext4.get(lastRowFirstCell[0]), record, index);
    },

    refresh : function(hasChanges)
    {
        this.getDataView().refresh();

        if (hasChanges)
            this.fireEvent('dirtychange', this);
    },

    getColumnConfig : function(dataIndex, dataFilterValue, parentDataIndex)
    {
        var columns = this.getColumnConfigs(), matchingColumn = null;

        // if the parentDataIndex is defined, then we are looking for the subgrid column editor config
        if (Ext4.isString(parentDataIndex))
        {
            var colIndex = Ext4.pluck(columns, 'dataIndex').indexOf(parentDataIndex);
            if (colIndex > -1 && columns[colIndex].hasOwnProperty('subgridConfig') && Ext4.isArray(columns[colIndex].subgridConfig.columns))
                columns = columns[colIndex].subgridConfig.columns;
            else
                return null;
        }

        Ext4.each(columns, function(column)
        {
            if (column.dataIndex == dataIndex && (!Ext4.isDefined(dataFilterValue) || column.dataIndexArrFilterValue == dataFilterValue))
            {
                matchingColumn = column;
                return false; // break;
            }
        }, this);

        return matchingColumn;
    },

    getColumnEditorConfig : function(column)
    {
        if (column != null && column.hasOwnProperty('editorType') && column.hasOwnProperty('editorConfig'))
        {
            return {
                type: column.editorType,
                config: Ext4.isFunction(column.editorConfig) ? column.editorConfig.call(this) : column.editorConfig
            };
        }

        return null;
    },

    loadDataViewStore : function()
    {
        // since some tables might need information from the store, wait to add the data view until the store loads
        this.getStore().on('load', function() {
            this.add(this.getDataView());
            this.fireEvent('loadcomplete', this);
        }, this, {single: true});
    },

    getStore : function()
    {
        throw "getStore must be overridden in subclass";
    },

    getNewModelInstance : function()
    {
        throw "getNewModelInstance must be overridden in subclass";
    },

    getColumnConfigs : function()
    {
        throw "getColumnConfigs must be overridden in subclass";
    },

    getDeleteConfirmationMsg : function()
    {
        return null;
    }
});