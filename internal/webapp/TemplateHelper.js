(function()
{
    /** @private */
    var X = Ext4 || Ext;
    /** @private */
    var $h = X.util.Format.htmlEncode;

    /* TODO Consolidate FieldKey code here with FieldKey in queryDesigner.js */

    /** @constructor @private */
    var FieldKey = function()
    {
        /** @private @type {[string]} */
        this._parts = [];
    };

    /** @param {string} fk field key using old $ encoding */
    FieldKey.prototype.parseOldEncoding = function(fk)
    {
        function decodePart(str)
        {
                str = str.replace(/\$C/, ",");
                str = str.replace(/\$T/, "~");
                str = str.replace(/\$B/, "}");
                str = str.replace(/\$A/, "&");
                str = str.replace(/\$S/, "/");
                str = str.replace(/\$D/, "$");
            return str;
        }
        var a = fk.split('/');
        for (var i=0; i<a.length ; i++)
            a[i] = decodePart(a[i]);
        this._parts = a;
    };

    /** @returns {string} */
    FieldKey.prototype.getName = function()
    {
        return this._parts.length > 0 ? this._parts[this._parts.length-1] : null;
    };

    /** @returns {string} */
    FieldKey.prototype.toDottedString = function()
    {
        // TODO escape names with "
        return "\"" + this._parts.join("\".\"") + "\"";
    };


    /** @constructor
     *
     * @param {Object} field as defined by SelectRowsResult
     * @param {Object} column as defined by SelectRowsResult
     */
    var FieldDefinition = function(field, column)
    {
        X.apply(this, field||{}, column||{});
        if (this.fieldKeyPath)
            this.fieldKey = (new FieldKey()).parseOldEncoding(this.fieldKeyPath);
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
                    formattedValue = X.Date.format(new Date(v), this.dateFormat||'Y-m-d');
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
        var html = $h(this.getFormattedDisplayValue(value));
        if (value.url && withUrls)
            html = "<a href=\"" + $h(value.url) + "\">" + html + "</a>";
        return html;
    };


    /**
     * The most common templates render values in <TD>, this helper generates a <TD> and
     * handles these fields to customize the layout
     * ) align
     * ) rowspan
     * ) className
     * ) style
     * ) width (should this me merged into style?)
     *
     * should cell values replace or supplement field settings?
     * what about style objects {color:'red'}?
     *
     * UNDONE: missing values, out-of-range values
     */
    FieldDefinition.prototype.getGridCellHtml = function(value, withUrls)
    {
        if (value.rowspan == -1)
            return "";
        var innerHtml = this.getDisplayValueHtml(value, withUrls);
        var td = "<td";
        // align
        if (this.align)
            td += " align=\"" + this.align + "\"";
        if (this.width)
            td += " width=\"" + this.width + "\"";
        // rowspan
        if (value.rowspan > 1)
            td += " rowspan=\"" + value.rowspan + "\"";
        // className
        var className = value.className || this.className;
        if (className)
            td += " class=\"" + className + "\"";
        // style
        var style = value.style || this.style;
        if (style)
            td += " style=\"" + style + "\"";

        return td + ">" + innerHtml + "</td>";
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
 *      config.template (text only for now) template to render (maybe we have some predefined)
 *          config.template = PAGING_WITH_ROWSPANS
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


X.define('LABKEY.TemplateReport',
{
    extend: 'Ext.Component',


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

        // turn field descriptions into FieldDefinitionpreprocess fields
        //  add index
        for (i=0 ; i < fields.length ; i++)
        {
            fields[i].index = i;
            field = fields[i] = new FieldDefinition(fields[i], columns||i<columns.length?columns[i]:{});
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
                if (!X.isObject(value))
                    value = {value: value};
                value.field = field;
                value.parentRow = parentRow;
                arrayRow.push(value);
            }

            parentRow.asArray = arrayRow;
            parentRow.asObject = objectRow;
            arrayrows.push(parentRow);
        }

        return {fields: fields, rows:arrayrows};
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
        if (X.isArray(this.pageBreakInfo))
        {
            breakInfos = breakInfos.concat(this.pageBreakInfo);
            pageBreakLevel = this.pageBreakInfo.length;
        }
        if (X.isArray(this.rowBreakInfo))
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

        for (var r=0 ; r < rows.length ; r++)
        {
            var parentRow = rows[r];
            var arrayRow = parentRow.asArray;

            // compute breakLevel for this row
            breakLevel = breakFields.length;
            for (var b=breakFields.length-1; b >=0 ; b--)
            {
                var obj = arrayRow[breakFields[b].index];
                var v = (obj && X.isDefined(obj.value)) ? obj.value : "" ;
                var prev = breakValues[b];
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


    onRender: function(ct, position)
    {
        if (!X.isString(this.template))
            throw "LABKEY.TemplateReport: String template expected";
        if (!X.isObject(this.data))
            throw "LABKEY.TemplateReport: Data not provided";

        if (!this.el)
            this.callParent(arguments);

        var transformData = this.transformSelectRowsResult(this.data);
        var reportData = this.transformForReportLayout(transformData);

        var tpl = new Ext4.XTemplate
        (
            this.template,
            {
                getCaptionHtml : function(field)
                {
                    if (field.getCaptionHtml)
                        return field.getCaptionHtml();
                    return field.shortCaption || field.name;
                },
                getGridCellHtml : function(d)
                {
                    var field = d.field || defaultField;
                    return field.getGridCellHtml(d, !this.isPrint);
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
                gridRow : 0,
                isPrint : -1 != window.location.href.indexOf("_print=true") || -1 != window.location.href.indexOf("_print=1"),
                start : (new Date()).valueOf()
            }
        );

        tpl.overwrite(this.el, reportData);
    }

});



})(); // file scope function


















/***************************************************************************/



var issueTmpl =
[
		'<table class="report" cellspacing=0><thead>',
		'<tpl for="rows">',
			'<tpl if="values.breakLevel==0">',
				'<tr><td class="break-spacer">&nbsp;</td></tr>',
				'<tr><td class="break-0" colspan=3>{[this.getHtml(values.asObject.AssignedTo)]}</tr>',
				'<tr><td>&nbsp;</td>',
				'<tpl for="parent.fields">',
					'<th class="labkey-column-header">{[this.getCaptionHtml(values)]}</th>',
				'</tpl>',
				'</tr>',
				'{[this.resetGrid()]}',
			'</tpl>',
			'<tr class="{[this.getGridRowClass()]}">',
			'<tpl if="values.breakLevel&gt;=0">',
				'<td class="break-1">{[this.getHtml(values.asObject.Status)]}</td>',
			'</tpl>',
			'<tpl if="values.breakLevel&lt;0">',
				'<td class="break-1">&nbsp;</td>',
			'</tpl>',
				'<tpl for="asArray">',
					'<td>{[this.getHtml(values)]}</td>',
				'</tpl>',
			'</tr>',
		'</tpl>',
		'</table>'
].join("");

var rowspanTmpl3 =
[
		'<table class="report">',
		'<thead>',
				'<tpl for="fields">',
					'<th class="labkey-column-header">{[this.getCaptionHtml(values)]}</th>',
				'</tpl>',
		'</thead>',
		'<tpl for="rows">',
		        '<tr class="{[this.getGridRowClass()]}">',
				'<tpl for="asArray">{[this.getGridCellHtml(values)]}</tpl>',
    			'</tr>',
		'</tpl>',
		'</table>',
		'{[((new Date()).valueOf() - this.start)/1000.0]}'
].join("");


var pageTmpl1 =
[
		'<table class="report"  cellspacing=0>',
        '<tpl for="pages">',
            '{[this.resetGrid()]}',
// PAGE TEMPLATE
            '<tr><td class="break-spacer">&nbsp;</td></tr>',
            '<tr><td colspan="{[parent.fields.length]}" style="background-color:#eeeeee; padding:10px;">',
                '<tpl for="first">',
                    '<table>',
                    '<tr><td colspan=2><b>{[this.getHtml(values.asObject.AssignedTo,values)]}</b></td></tr>',
                    '<tr><td>userid</td><td>{[this.getHtml(values.asObject["AssignedTo/UserId"],values)]}</td></tr>',
                    '</table>',
                '</tpl>',
            '</td></tr>',
// GRID TEMPLATE
            '<tr>',
                '<tpl for="parent.fields">',
                    '<th class="labkey-column-header">{[this.getCaptionHtml(values)]}</th>',
                '</tpl>',
            '</tr>',
            '<tpl for="rows">',
                '<tr class="{[this.getGridRowClass()]}">',
                '<tpl for="asArray">',
                    '{[this.getGridCellHtml(values)]}',
         '</tpl>',
    			'</tr>',
            '</tpl>',
		'</tpl>',
		'</table>',
		'{[((new Date()).valueOf() - this.start)/1000.0]}'
].join("");


function testIssues(el)
{
    var helper = new LABKEY.TemplateReport(
    {
        rowBreakInfo:[{name:'Status', rowspans:false}],
        pageBreakInfo:[{name:'AssignedTo', rowspans:true}],
        template : pageTmpl1
    });

    LABKEY.Query.selectRows({
        requiredVersion: 12.1,
        schemaName: 'issues',
        queryName: 'Issues',
        columns: 'AssignedTo,Status,XY,IssueId,Created,Priority,Title,Type,AssignedTo/DisplayName,AssignedTo/UserId,CreatedBy,Area,Milestone,Triage',
        sort: 'AssignedTo/DisplayName,Status,-IssueId',
        includeStyle : true,
        success: function(qr)
		{
            helper.data = qr;
            helper.render(el);
		}
    });
}




