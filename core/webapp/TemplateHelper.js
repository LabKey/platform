/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
(function() {
    /** @private */
    var $h = Ext4.util.Format.htmlEncode;

    /** @constructor
     *
     * @param {Object} field as defined by SelectRowsResult
     * @param {Object} column as defined by SelectRowsResult
     */
    var FieldDefinition = function(field, column)
    {
        Ext4.apply(this, field||{}, column||{});
        if (this.fieldKeyPath)
            this.fieldKey = LABKEY.FieldKey.fromString(this.fieldKeyPath);
        if (this.extFormatFn)
        {
            try
            {
                this.formatFn = eval(this.extFormatFn);
            }
            catch (ex)
            {
            }
        }
    };

    FieldDefinition.prototype.getCaptionHtml = function()
    {
        if (this.captionHtml)
            return captionHtml;
        if (this.shortCaption)
            return $h(this.shortCaption);
        return this.name;
    };

    FieldDefinition.prototype.getFormattedDisplayValue = function(valueObj)
    {
        // UNDONE: need formatting information (or formatted values) from server
        if ('displayValue' in valueObj)
            return valueObj.displayValue;

        var v = valueObj.value;
        if (undefined == v || null == v)
            return "";

        var formattedValue;
        do  // fake loop so I can use 'break'
        {
            if (this.formatFn)
            {
                try
                {
                    formattedValue = this.formatFn(v);
                    break;
                }
                catch (ex)
                {
                }
            }
            if (this.type == "date")
            {
                try
                {
                    formattedValue = Ext4.Date.format(new Date(v), this.dateFormat||'Y-m-d');
                    break;
                }
                catch (ex)
                {
                }
            }
            formattedValue = v.toString();
        } while(false);

        if ('lookup' in this)
            formattedValue = "<" + formattedValue + ">";
        return formattedValue;
    };

    FieldDefinition.prototype.getDisplayValueHtml = function(value, withUrls)
    {
        withUrls = withUrls !== false;
        if (value.html)
            return html;
        var displayValue = this.getFormattedDisplayValue(value)
        var html = Ext4.isEmpty(displayValue) ? '&nbsp;' : $h(displayValue);
        if (value.url && withUrls)
            html = "<a href=\"" + $h(value.url) + "\">" + html + "</a>";
        return html;
    };


    /**
     * The most common templates render values in <TD>, this helper generates a <TD> and
     * handles these fields to customize the layout
     * ) align
     * ) rowspan (becomes colspan if grid is transposed)
     * ) className
     * ) style
     * ) width (should this me merged into style?)
     *
     * should cell values replace or supplement field settings?
     * what about style objects {color:'red'}?
     *
     * UNDONE: missing values, out-of-range values
     */
    FieldDefinition.prototype.getGridCellHtml = function(value, asHeader, withUrls, transposed)
    {
        if (value.rowspan == -1)
            return "";
        var innerHtml = this.getDisplayValueHtml(value, withUrls);
        var td = !asHeader ? "<td valign=top" : "<th style=\"border: solid 1px #DDDDDD; padding: 4px;\"";

        // align (for transposed, align all values to the right)
        if (!asHeader && !transposed && this.align)
            td += " align=\"" + this.align + "\"";
        else if (!asHeader)
            td += " align=\"right\"";

        // width
        if (this.width)
            td += " width=\"" + this.width + "\"";

        // rowspan
        if (!transposed && value.rowspan > 1)
            td += " rowspan=\"" + value.rowspan + "\"";
        // colspan
        if (transposed && value.rowspan > 1)
            td += " colspan=\"" + value.rowspan + "\"";

        // className
        var className = value.className || this.className;
        if (className)
            td += " class=\"" + className + "\"";

        // style
        var style = value.style || this.style;
        if (style)
            td += " style=\"" + style + "\"";

        return td + ">" + innerHtml + (!asHeader ? "</td>" : "</th>");
    };


    var defaultField = new FieldDefinition({});


/**
 * TemplateReport  organizes the data tranformation steps and html generation steps
 *
 *      STEP 1 -- call transformSelectRowsResult() then
 *          1a) NYI call config.afterTransform(result)
 *          1b) NYI add calculated field using config.calculatedFields
 *
 *      STEP 2 -- paging and grouping transformForReportLayout()
 *          2a) NYI config.afterReportLayout()
 *
 *      STEP 3 -- apply template
 *
 *  A config for TemplateReport will work with a SelectRowsResult of an _expected_ shape.
 *  You can't generally apply an arbitrary result to a report because of binding by field names, etc.
 *
 *  config
 *      config.gridFields, if not specified defaults to all fields in SelectRowsResult
 *          gridFields : ['id', 'title', 'column3']

 *      config.pageFields, default template may use to auto-generate form layout, template could refer to fields by name
 *          pageFields : ['name', 'created']
 *
 *      config.pageBreakInfo configures when to start a new page
 *          pageBreakInfo : [{name:'ParticipantId'}]
 *
 *      config.gridBreakInfo configures grouping in the grid (template may use different markup to indicate grouping)
 *          gridBreakInfo : [{name:'VisitMonth', rowspan:true}]
 *
 *      config.reportTemplate template to create an XTemplate, this object will be used as the last parameter of the XTemplate constructor
 *           using the template property as the first parameter, and registering any "on" properties
 *           config.template = {
                    template:'template string goes here',
                    userdefined:'Yadda yadda',
                    on : {dataload:function(report,data){}}
            }
 *
 * NYI
 *      data processing configuration, such as computed columns, etc
 *
 * UNDONE: are field names the best identifiers or should we be using field keys?
 * UNDONE: support for attaching to an ext store
 *
 * example
 *
 *    var config =
 *    {
 *        rowBreakInfo:[{name:'Status', rowspans:false}],
 *        pageBreakInfo:[{name:'AssignedTo', rowspans:true}],
 *        pageFields : ['AssignedTo', 'AssignedTo/UserId'],
 *        template : LABKEY.TemplateReport.templates.PAGING_WITH_ROWSPANS
 *    };
 *    var helper = new LABKEY.TemplateReport(config);
 *
 *    LABKEY.Query.selectRows({
 *       requiredVersion: 12.1,
 *       schemaName: 'issues',
 *       queryName: 'Issues',
 *       columns: 'AssignedTo,Status,XY,IssueId,Created,Priority,Title,Type,AssignedTo/DisplayName,AssignedTo/UserId,CreatedBy,Area,Milestone,Triage',
 *       sort: 'AssignedTo/DisplayName,Status,-IssueId',
 *       success: function(qr)
 *		{
 *            helper.render(el, qr);
 *		}
 *   });
 */


Ext4.define('LABKEY.TemplateReport',
{
    extend: 'Ext.Component',


    initComponent: function()
    {
            this.addEvents
            (

                /**
                 * @event onload
                 * Fires when data is loaded.
                 * @param {LABKEY.TemplateReport}
                 * @param {LABKEY.ExtendedSelectRowsResult} or {LABKEY.SelectRowsResult}
                 */
                'dataload',

                /**
                 * @event onpostprocess
                 * Fires before data is rendered
                 * @param {LABKEY.TemplateReport}
                 * @param transformed data see transformForReportLayout
                 */
                'afterdatatransform',

                /**
                 * @event onpostprocess
                 * Fires after data is rendered
                 * @param {LABKEY.TemplateReport}
                 */
                'afterrender'
            );


            if (Ext4.isString(this.reportTemplate))
                this.reportTemplate = {template:tpl};
            if (Ext4.isArray(this.reportTemplate.template))
                this.reportTemplate.template = this.reportTemplate.template.join("");
            for (var p in this.reportTemplate.on)
            {
                if (this.reportTemplate.on.hasOwnProperty(p))
                    this.on(p, this.reportTemplate.on[p]);
            }
    },


    /**
     * This method takes a query result set and preprocesses it
     *
     * ) creates array version of each row (covient for template iteration)
     *      {FirstName:{value:'Matt'}, LastName:{value:'Bellew'}} becomes [{value:'Matt'},{value:'Bellew'}]
     * ) create new parent row object containing object version of row data and array version of row data
     *      {asArray:[], asObject:{}, breakLevel:-1}
     *
     * not sure we need these back pointers, but
     * ) adds reference from each data value object to corresponding field description
     * ) add reference from each data value to parent row object
     *
     * NOTE: the structure returned by transformSelectRowsResult() is not compatible with
     * SelectRowsResults (or ExtendedSelectRowsResults).
     *
     * moved break-level computation to transformForGroupedReport
     *
     * @param qr
     */

    transformSelectRowsResult : function(qr)
    {
        var fields = qr.metaData.fields;
        var columns = qr.columnModel;
        var rows = qr.rows;
        var arrayrows = [];
        var i, field;

        // turn field descriptions into FieldDefinition
        //  preprocess fields
        //  add index
        var nameMap = {};
        for (i=0 ; i < fields.length ; i++)
        {
            fields[i].index = i;
            field = fields[i] = new FieldDefinition(fields[i], columns||i<columns.length?columns[i]:{});
            nameMap[field.name] = field;
        }

        var gridFields = null;
        if (Ext4.isArray(this.gridFields))
        {
            gridFields = [];
            for (i=0 ; i<this.gridFields.length ; i++)
            {
                field = nameMap[this.gridFields[i]];
                if (field)
                    gridFields.push(field);
            }
        }

        var pageFields = null;
        if (Ext4.isArray(this.pageFields))
        {
            pageFields = [];
            for (i=0 ; i<this.pageFields.length ; i++)
            {
                var pf = Ext4.isString(this.pageFields[i]) ? {name:this.pageFields[i]} : this.pageFields[i];
                field = nameMap[pf.name];
                if (field)
                    // create a copy of the field so page fields can have different formatting
                    pageFields.push(Ext4.apply({}, pf, field));
            }
        }
        else if (Ext4.isArray(this.pageBreakInfo))
        {
            pageFields = [];
            for (i=0 ; i<this.pageBreakInfo.length ; i++)
            {
                field = nameMap[this.pageBreakInfo[i].name];
                if (field)
                    pageFields.push(field);
            }
        }

        var rowBreakMap = {};
        if (Ext4.isArray(this.rowBreakInfo))
        {
            for (i=0; i < this.rowBreakInfo.length; i++)
            {
                var rbi = this.rowBreakInfo[i];
                if (rbi.rowspans)
                    rowBreakMap[rbi.name] = rbi;
            }
        }

        for (var r=0 ; r < rows.length ; r++)
        {
            var objectRow = rows[r];
            var arrayRow = [];
            var parentRow = {};

            for (var f=0 ; f < fields.length ; f++)
            {
                field = fields[f];
                var value = objectRow[field.name] || {};
                if (!Ext4.isObject(value))
                    value = {value: value};
                value.field = field;
                value.parentRow = parentRow;
                arrayRow.push(value);
            }

            parentRow.isEmpty = this.isEmptyRow(objectRow, gridFields ? gridFields : fields, rowBreakMap);
            parentRow.asArray = arrayRow;
            parentRow.asObject = objectRow;
            arrayrows.push(parentRow);
        }

        var transformedResults =
        {
            fields: fields,
            gridFields:(gridFields?gridFields:fields),
            pageFields:pageFields?pageFields:[],
            rows:arrayrows
        };
        return transformedResults;
    },

    isEmptyRow : function(objectRow, gridFields, rowBreakMap)
    {
        for (var i=0; i < gridFields.length; i++)
        {
            var field = objectRow[gridFields[i].name];
            if (field && !rowBreakMap[gridFields[i].name] && !Ext4.isEmpty(field.value))
                return false;
        }
        return true;
    },

    /**
     * takes a transformed result (see transformSelectRowsResult) and tranforms the data
     * into a report ready data-structure
     *
     *  ) computes breaklevels and rowspans
     *  ) generates gridFields array (subset of fields)
     *  ) generates pageFields array (subset of fields)
     *
     * row array is broken into array of arrays by page grouping
     *  pages[[rows[0..a-1]],[rows[a..b-1],[rows[b..c-1],...]
     *
     * config
     *   {
     *       pageBreakInfo :  [{name:'AssignedTo'}]
     *       rowBreakInfo : [{name:'Status', rowspan:true}]
     *   }
     */

    transformForReportLayout : function(tr)
    {
        /** @type {[FieldDefinition]} */
        var fields = tr.fields;
        var rows = tr.rows;
        var i, field;

        var nameMap = {};
        for (i=0 ; i < fields.length ; i++)
            nameMap[fields[i].name] = fields[i];


        // compute break levels
        var breakFields = [];
        var breakLevel = undefined;
        var rowAtLastBreak = [];
        var breakValues = [];

        var breakInfos = [];
        var breakInfo;
        var pageBreakLevel = -1;
        if (Ext4.isArray(this.pageBreakInfo))
        {
            breakInfos = breakInfos.concat(this.pageBreakInfo);
            pageBreakLevel = this.pageBreakInfo.length;
        }
        if (Ext4.isArray(this.rowBreakInfo))
        {
            breakInfos = breakInfos.concat(this.rowBreakInfo);
        }

        if (breakInfos.length == 0)
        {
            //nothing to do
            tr.pages = [rows];
            return tr;
        }


        for (i=0 ; i < breakInfos.length ; i++)
        {
            breakInfo = breakInfos[i];
            var name = breakInfo.name;
            field = nameMap[name];
            if (!field)
                throw "Field not found: " + name;
            breakFields.push(field);
            breakValues.push(undefined);
        }


        var pages = [];
        var currentPage = [];
        var rowSpanStart = null;
        var prevBreakLevel = undefined;

        // need to make a pass through to coalesce empty rowspan rows
        for (var r=0 ; r < rows.length ; r++)
        {
            var parentRow = rows[r];
            var arrayRow = parentRow.asArray;

            // compute breakLevel for this row
            breakLevel = breakFields.length;
            for (var b=breakFields.length-1; b >=0 ; b--)
            {
                var obj = arrayRow[breakFields[b].index];
                var v = (obj && Ext4.isDefined(obj.value)) ? obj.value : "" ;
                var prev = breakValues[b];
                if (v != prev)
                    breakLevel = b;
                breakValues[b] = v;
            }
            //console.log(r, breakLevel, breakValues);

            if (breakLevel == breakFields.length && prevBreakLevel != breakFields.length)
                rowSpanStart = r-1;
            else if (prevBreakLevel == breakFields.length && breakLevel != prevBreakLevel)
            {
                var isEmpty = true;
                for (var idx = rowSpanStart; idx < r; idx++)
                {
                    var row = rows[idx];

                    if (row && row.isEmpty && (!isEmpty || (idx+1 < r)))
                        row.isHidden = true;
                    else
                        isEmpty = false;
                }
            }
            prevBreakLevel = breakLevel;
        }
        breakValues = [];

        for (r=0 ; r < rows.length ; r++)
        {
            parentRow = rows[r];
            arrayRow = parentRow.asArray;

            if (parentRow.isHidden)
                continue;

            // compute breakLevel for this row
            breakLevel = breakFields.length;
            for (b=breakFields.length-1; b >=0 ; b--)
            {
                obj = arrayRow[breakFields[b].index];
                v = (obj && Ext4.isDefined(obj.value)) ? obj.value : "" ;
                prev = breakValues[b];
                if (v != prev)
                    breakLevel = b;
                breakValues[b] = v;
            }

            // update rowspans for previous breaks and rowspan for this row
            var rowspans = new Array(breakFields.length);
            for (b=breakFields.length-1 ; b>=0 ; b--)
            {
                breakInfo = breakInfos[b];
                if (!breakInfo.rowspans)
                    continue;
                if (b < breakLevel)
                {
                    if (rowAtLastBreak[b])
                    {
                        rowAtLastBreak[b].rowspans[b]++;
                        rowAtLastBreak[b].asArray[breakFields[b].index].rowspan++;
                    }
                    rowspans[b] = -1;
                    arrayRow[breakFields[b].index].rowspan = -1;
                }
                else
                {
                    rowAtLastBreak[b] = parentRow;
                    rowspans[b] = 1;
                    arrayRow[breakFields[b].index].rowspan = 1;
                }
            }

            if (breakLevel < pageBreakLevel)
            {
                if (currentPage.length)
                    pages.push(currentPage);
                currentPage = [];
            }
            currentPage.push(parentRow);

            parentRow.breakLevel = breakLevel == breakFields.length ? -1 : breakLevel;
            parentRow.rowspans = rowspans;
        }
        if (currentPage.length)
            pages.push(currentPage);

        for (var p=0 ; p<pages.length ; p++)
        {
            var pageRows = pages[p];
            var first = pageRows.length>0 ? pageRows[0] : null;
            var last = pageRows.length>0 ? pageRows[pageRows.length-1] : null;
            pages[p] = {first:first, last:last, rows:pageRows};
        }

        tr.breakFields = breakFields;
        tr.pages = pages;
        return tr;
    },

    onRender : function(ct, position)
    {
        if (!this.el)
            this.callParent(arguments);

        if (this.reportData)
            this.renderReport();
        else if (this.queryConfig)
        {
            if (!this.queryConfig.success)
            {
                this.queryConfig.scope = this;
                this.queryConfig.success = this.loadData;
            }
            LABKEY.Query.selectRows(this.queryConfig);
        }
    },

    renderReport:function()
    {
        if (!Ext4.isObject(this.reportTemplate))
            throw "LABKEY.TemplateReport: No template provided";
        if (!Ext4.isObject(this.reportData))
            throw "LABKEY.TemplateReport: Data not provided";

        var tplConfig =
        {
                getCaptionHtml : function(field)
                {
                    if (field.getCaptionHtml)
                        return field.getCaptionHtml();
                    return field.caption || field.name;
                },
                getGridCellHtml : function(d, asHeader)
                {
                    var field = d.field || defaultField;
                    return field.getGridCellHtml(d, asHeader, !this.isPrint, this.transposed);
                },
                getHtml : function(d,values)    // values is just for debugging
                {
                    var field = d.field || defaultField;
                    return field.getDisplayValueHtml(d, !this.isPrint);
                },
                resetGrid : function()
                {
                    this.gridRow = 0; return "";
                },
                getGridRowClass : function()
                {
                    // assumes we call this once per row
                    this.gridRow++;
                    return this.gridRow%2 ? "labkey-alternate-row" : "labkey-row";
                },
                setPageIndex : function(pagesXindex)
                {
                    // workaround for http://www.sencha.com/forum/showthread.php?180125-4.1B2-Ext.XTemplate-no-longer-handles-nested-parent-references
                    // keep track of the array index into the pages array for nested looping in the XTemplate
                    this.data.pageIndex = pagesXindex -1; // pageIndex is 1-based
                    return "";
                },
                gridRow : 0,
                isPrint : -1 != window.location.href.indexOf("_print=true") || -1 != window.location.href.indexOf("_print=1"),
                transposed : this.transposed,
                start : (new Date()).valueOf()
        };
        Ext4.apply(tplConfig, this.reportTemplate);
        this.template = new Ext4.XTemplate(tplConfig.template, tplConfig);
        this.template.data = this.reportData;
        this.template.overwrite(this.el, this.reportData);
        this.fireEvent('afterrender', this);
    },

    // returns data applied to the template in markup form without rendering it to the dom
    getMarkup : function() {

        if (this.template && this.template.data)
            return this.template.apply(this.template.data);
    },

    loadData : function(data)
    {
        this.fireEvent('dataload', this, data);
        var transformData = this.transformSelectRowsResult(data);
        var reportData = this.transformForReportLayout(transformData);
        this.fireEvent('afterdatatransform', this, reportData);

        this.reportData = reportData;
        // if render has been called already, we should refresh
        if (this.el)
            this.renderReport();
    }

});



})(); // file scope function
