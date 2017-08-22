/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.VaccineDesign.BaseDataView', {

    extend : 'Ext.panel.Panel',

    cls : 'study-vaccine-design',

    border : false,

    mainTitle : null,

    studyDesignQueryNames : null,

    // for a DataSpace project, some scenarios don't make sense to allow insert/update
    disableEdit : false,

    DELETE_ICON_CLS : 'fa fa-trash',
    ADD_ICON_CLS : 'fa fa-plus-circle',

    constructor : function(config)
    {
        this.callParent([config]);
        this.addEvents('dirtychange', 'loadcomplete', 'celledited', 'beforerowdeleted', 'renderviewcomplete');
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

        this.fireRenderCompleteTask = new Ext4.util.DelayedTask(function() {
            this.fireEvent('renderviewcomplete', this);
        }, this);

        // add a single event listener to focus the first input field on the initial render
        this.on('renderviewcomplete', function() {
            this.giveCellInputFocus('table.outer tr.data-row:first td.cell-value:first input', true);
            LABKEY.Utils.signalWebDriverTest("VaccineDesign_renderviewcomplete");
        }, this, {single: true});

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
                cls: 'table-responsive',
                store: this.getStore(),
                itemSelector: 'tr.data-row',
                disableSelection: true,
                setTemplate: function(newTpl)
                {
                    this.tpl = newTpl;
                    this.refresh();
                }
            });

            this.dataView.on('itemclick', this.onDataViewItemClick, this);
            this.dataView.on('refresh', this.onDataViewRefresh, this, {buffer: 250});
        }

        return this.dataView;
    },

    getDataViewTpl : function()
    {
        var showEdit = !this.disableEdit,
            tdCls = !showEdit ? 'cell-display' : 'cell-value',
            tplArr = [],
            columns = this.getColumnConfigs();

        tplArr.push('<table class="table outer">');
        tplArr = tplArr.concat(this.getTableHeaderRowTpl(columns));

        // data rows
        tplArr.push('<tpl for=".">');
        tplArr.push('<tr class="data-row row-outer {[xindex % 2 === 0 ? "alternate-row" : ""]}">');
        if (showEdit)
            tplArr.push('<td class="cell-display action"><i class="' + this.DELETE_ICON_CLS + '" outer-index="{[xindex-1]}"/></td>');
        Ext4.each(columns, function(column)
        {
            if (Ext4.isString(column.dataIndex) && !column.hidden)
            {
                var checkMissingReqTpl = column.required ? ' {[this.checkMissingRequired(values, "' + column.dataIndex + '")]}' : '',
                    tdTpl = '<td class="' + tdCls + checkMissingReqTpl + '" data-index="' + column.dataIndex + '" outer-index="{[xindex-1]}">',
                    tdCloseTpl = '</td>';

                if (Ext4.isDefined(column.dataIndexArrFilterValue))
                    tdTpl = tdTpl.substring(0, tdTpl.length -1) + ' data-filter-value="' + column.dataIndexArrFilterValue + '">';

                // decide which of the td tpls to use based on the column definition
                if (Ext4.isObject(column.subgridConfig) && Ext4.isArray(column.subgridConfig.columns))
                {
                    tplArr = tplArr.concat(this.getSubGridTpl(column.dataIndex, column.subgridConfig.columns));
                }
                else if (Ext4.isString(column.queryName))
                {
                    tplArr.push(tdTpl + (!showEdit ? '{[this.getLabelFromStore(values["' + column.dataIndex + '"], "' + column.queryName + '")]}' : '') + tdCloseTpl);
                }
                else if (Ext4.isString(column.lookupStoreId) && Ext4.isDefined(column.dataIndexArrFilterValue))
                {
                    var valTpl = '';
                    if (!showEdit)
                    {
                        valTpl = '{[this.getDisplayValue(values["' + column.dataIndex + '"], '
                            + '"' + column.dataIndexArrFilterProp + '", "' + column.dataIndexArrFilterValue + '", '
                            + '"' + column.dataIndexArrValue + '", "' + column.lookupStoreId + '")]}';
                    }

                    tplArr.push(tdTpl + valTpl + tdCloseTpl);
                }
                else if (column.editorType == 'Ext.form.field.Checkbox')
                {
                    var valTpl = '';
                    if (!showEdit)
                    {
                        valTpl = '{[this.getDisplayValue(values["' + column.dataIndex + '"], '
                                + '"' + column.dataIndexArrFilterProp + '", '
                                + '"' + column.dataIndexArrFilterValue + '", '
                                + '"checkbox")]}';
                    }

                    tdTpl = tdTpl.substring(0, tdTpl.length -1) + ' style="text-align: center;">';
                    tplArr.push(tdTpl + valTpl + tdCloseTpl);
                }
                else
                {
                    tplArr.push(tdTpl + (!showEdit ? '{[this.getDisplayValue(values["' + column.dataIndex + '"])]}' : '') + tdCloseTpl);
                }
            }
            else if (Ext4.isString(column.displayValue))
            {
                tplArr.push('<td class="cell-display" outer-index="{[xindex-1]}">' + column.displayValue + '</td>');
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
                    {
                        if (arrPropDisplayField == 'checkbox')
                            return '&#x2713;';
                        else
                            val = val[matchingIndex][arrPropDisplayField];
                    }
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

                if (Ext4.isNumber(val))
                {
                    return val == 0 ? '' : val;
                }
                else
                {
                    // need to htmlEncode and then handle newlines in multiline text fields (i.e. Treatment/Description)
                    val = Ext4.util.Format.htmlEncode(val);
                    val = val.replace(/\n/g, '<br/>');
                }

                return val;
            },

            getLabelFromStore : function(val, queryName)
            {
                if (val != null && val != '')
                    val = LABKEY.VaccineDesign.Utils.getLabelFromStore(queryName, val);

                if (Ext4.isNumber(val))
                    return val == 0 ? '' : val;
                else
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
        var showEdit = !this.disableEdit,
            tdCls = showEdit ? 'cell-value' : 'cell-display',
            tplArr = [];

        tplArr.push('<td class="cell-display" outer-index="{[xindex-1]}">');

        // only show the subgrid if we are allowing edits of if it has at least one row
        tplArr.push('<tpl if="' + dataIndex + '.length &gt; 0 || ' + showEdit + '">');

        tplArr.push('<table class="subgrid subgrid-' + dataIndex + '" width="100%">');
        tplArr = tplArr.concat(this.getTableHeaderRowTpl(columns));

        // data rows
        tplArr.push('<tpl for="' + dataIndex + '">');
        tplArr.push('<tr class="subrow">');
        if (showEdit)
        {
            tplArr.push('<td class="cell-display action">');
            tplArr.push('<i class="' + this.DELETE_ICON_CLS + '" subgrid-data-index="' + dataIndex + '" subgrid-index="{[xindex-1]}"/>');
            tplArr.push('</td>');
        }
        Ext4.each(columns, function(column)
        {
            if (Ext4.isString(column.dataIndex))
            {
                var checkMissingReqTpl = column.required ? ' {[this.checkMissingRequired(values, "' + column.dataIndex + '")]}' : '',
                    tdTpl = '<td class="' + tdCls + checkMissingReqTpl + '" outer-data-index="' + dataIndex + '" data-index="' + column.dataIndex + '" subgrid-index="{[xindex-1]}">',
                    tdCloseTpl = '</td>';

                if (Ext4.isString(column.queryName))
                    tplArr.push(tdTpl + (!showEdit ? '{[this.getLabelFromStore(values["' + column.dataIndex + '"], "' + column.queryName + '")]}' : '') + tdCloseTpl);
                else
                    tplArr.push(tdTpl + (!showEdit ? '{' + column.dataIndex + ':htmlEncode}' : '') + tdCloseTpl);
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
            tplArr.push('<td class="cell-display" width="22px">&nbsp;</td>');
        Ext4.each(columns, function(column)
        {
            if (!column.hidden)
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
                tplArr.push('<i class="' + this.ADD_ICON_CLS + ' add-new-row" data-index="' + dataIndex + '" outer-index="{[xindex-1]}"> Add new row</i>');
            else
                tplArr.push('<i class="' + this.ADD_ICON_CLS + ' add-new-row outer-add-new-row"> Add new row</i>');
            tplArr.push('</td>');
            tplArr.push('</tr>');
        }

        return tplArr;
    },

    onDataViewItemClick : function(view, record, item, index, event)
    {
        if (!this.disableEdit)
        {
            // handle click on trashcan icon to delete row
            if (event.target.getAttribute('class') == this.DELETE_ICON_CLS)
            {
                if (event.target.hasAttribute('outer-index'))
                {
                    this.removeOuterRecord(this.mainTitle, record);
                }
                // handle click on trashcan icon for outer grid
                else if (event.target.hasAttribute('subgrid-data-index') && event.target.hasAttribute('subgrid-index'))
                {
                    this.confirmRemoveSubgridRecord(event.target, record);
                }
            }
        }
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
            var config = {
                renderTo: target,
                required: column.required,
                storeIndex: index,
                dataFilterValue: dataFilterValue,
                outerDataIndex: outerDataIndex,
                subgridIndex: subgridIndex
            };

            var currentValue = this.getCurrentCellValue(column, record, dataIndex, outerDataIndex, subgridIndex);
            if (editor.type == 'Ext.form.field.Checkbox')
                config.checked = currentValue;
            else
                config.value = currentValue;

            if (column.isTreatmentLookup) {
                var treatmentLabel = this.getTreatmentCellDisplayValue(currentValue, column.lookupStoreId);
                config.value = treatmentLabel;
                config.treatmentId = currentValue;
            }

            // create a new form field to place in the td cell
            var field = Ext4.create(editor.type, Ext4.apply(editor.config, config));

            // add listeners for when to apply the updated value and clear the input field
            field.on('change', this.updateStoreValueForCellEdit, this, {buffer: 500});
        }
    },

    getCurrentCellValue : function(column, record, dataIndex, outerDataIndex, subgridIndex)
    {
        return Ext4.isString(outerDataIndex) ? record.get(outerDataIndex)[subgridIndex][dataIndex] : record.get(dataIndex);
    },

    updateStoreValueForCellEdit : function(field)
    {
        var fieldName = field.getName(),
            newValue = field.getValue(),
            index = field.storeIndex,
            record = this.getStore().getAt(index),
            dataFilterValue = field.dataFilterValue,
            outerDataIndex = field.outerDataIndex,
            subgridIndex = Number(field.subgridIndex);

        // suspend events on cell update so that we don't re-render the dataview
        this.getStore().suspendEvents();

        if (Ext4.isString(outerDataIndex))
        {
            if (!isNaN(subgridIndex) && Ext4.isArray(record.get(outerDataIndex)))
                this.updateSubgridRecordValue(record, outerDataIndex, subgridIndex, fieldName, newValue);
        }
        else
        {
            var column = this.getColumnConfig(fieldName, dataFilterValue, outerDataIndex);
            this.updateStoreRecordValue(record, column, newValue, field);
        }

        // update the missing-required cls based on the new field value
        if (field.required)
        {
            if (newValue == null || newValue == '')
                Ext4.get(field.renderTo).addCls('missing-required');
            else
                Ext4.get(field.renderTo).removeCls('missing-required');
        }

        // resume store events so that adding and deleting will re-render the dataview
        this.getStore().resumeEvents();
    },

    updateSubgridRecordValue : function(record, outerDataIndex, subgridIndex, fieldName, newValue)
    {
        if (Ext4.isString(newValue))
            newValue.trim();

        record.get(outerDataIndex)[subgridIndex][fieldName] = newValue;
        this.fireEvent('celledited', this, fieldName, newValue);
    },

    updateStoreRecordValue : function(record, column, newValue, field)
    {
        if (Ext4.isString(newValue))
            newValue.trim();

        record.set(column.dataIndex, newValue);
        this.fireEvent('celledited', this, column.dataIndex, newValue);
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

    confirmRemoveSubgridRecord : function(target, record)
    {
        var subgridDataIndex = target.getAttribute('subgrid-data-index'),
            subgridArr = record.get(subgridDataIndex);

        if (Ext4.isArray(subgridArr))
        {
            Ext4.Msg.confirm('Confirm Delete: ' + subgridDataIndex, 'Are you sure you want to delete the selected row?', function(btn)
            {
                if (btn == 'yes')
                {
                    this.removeSubgridRecord(target, record);
                    this.refresh(true);
                }
            }, this);
        }
    },

    removeSubgridRecord : function(target, record)
    {
        var subgridDataIndex = target.getAttribute('subgrid-data-index'),
            subgridArr = record.get(subgridDataIndex);

        subgridArr.splice(target.getAttribute('subgrid-index'), 1);
    },

    onDataViewRefresh : function(view)
    {
        this.attachCellEditors(view);
        this.attachAddRowListeners(view);
        this.doLayout();
        this.fireRenderCompleteTask.delay(250);
    },

    attachCellEditors : function(view)
    {
        // attach cell editors for each of the store records (i.e. tr.row elements in the table)
        var index = 0;
        Ext4.each(this.getStore().getRange(), function(record)
        {
            var targetCellEls = Ext4.DomQuery.select('tr.data-row:nth(' + (index+1) + ') td.cell-value', view.getEl().dom);
            Ext4.each(targetCellEls, function(targetCell)
            {
                this.createNewCellEditField(targetCell, record, index);
            }, this);

            index++;
        }, this);
    },

    attachAddRowListeners : function(view)
    {
        var addIconEls = Ext4.DomQuery.select('i.add-new-row', view.getEl().dom);

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
        this.on('renderviewcomplete', function(){
            this.giveCellInputFocus('table.outer tr.data-row:last td.cell-value:first input');
        }, this, {single: true});

        this.refresh();
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
            this.on('renderviewcomplete', function(){
                var selector = 'table.subgrid-' + dataIndex + ':nth(' + (rowIndex+1) + ') tr.subrow:last td.cell-value:first input';
                this.giveCellInputFocus(selector);
            }, this, {single: true});

            this.refresh();
        }
    },

    giveCellInputFocus : function(selector, queryFullPage)
    {
        var cellInputField = Ext4.DomQuery.selectNode(selector, queryFullPage ? undefined : this.getDataView().getEl().dom);
        if (cellInputField)
            cellInputField.focus();
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