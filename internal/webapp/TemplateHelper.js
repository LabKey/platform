Ext.ns("LABKEY", "LABKEY.TemplateHelper");


/* TODO Consolidate LABKEY.FieldKey code here with FieldKey in queryDesigner.js */

/** @constructor */
LABKEY.FieldKey = function()
{
    /** @private @type {[string]} */
    this._parts = [];
};



/** @param {string} field key using old $ encoding */
LABKEY.FieldKey.prototype.parseOldEncoding = function(fk)
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
LABKEY.FieldKey.prototype.getName = function()
{
    return _parts.length > 0 ? _parts[_parts.length-1] : null;
};



/** @returns {string} */
LABKEY.FieldKey.prototype.toDottedString = function()
{
    // TODO escape names with "
    return "\"" + _parts.join("\".\"") + "\"";
};


(function() {

	var $h = Ext.util.Format.htmlEncode;

    /** @private */
    function getCaptionHtml()
    {
        if (this.captionHtml)
            return captionHtml;
        if (this.shortCaption)
            return $h(this.shortCaption);
        return this.name;
    }


    /** @private @returns {string} */
    function getFormattedDisplayValue(valueObj)
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
                    formattedValue = Ext.Date.format(new Date(v), this.dateFormat||'Y-m-d');
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
    }


    /** @private */
    function getDisplayValueHtml(value, withUrls)
    {
        withUrls = withUrls !== false;
        if (value.html)
            return html;
        var html = $h(this.getFormattedDisplayValue(value));
        if (value.url && withUrls)
            html = "<a href=\"" + $h(value.url) + "\">" + html + "</a>";
        return html;
    }


    function getGridCellHtml(value, withUrls)
    {
        if (value.rowspan == -1)
            return "";
        var innerHtml = this.getDisplayValueHtml(value, withUrls);
        var td = "<td";
        if (value.rowspan > 1)
            td += " rowspan=\"" + value.rowspan + "\"";
        var className = value.className || this.className;
        if (className)
            td += " class=\"" + className + "\"";
        return td + ">" + innerHtml + "</td>";
    }



/**
 * This method takes a query result set and preprocesses it
 *
 * ) creates array version of each row (covient for template iteration)
 *      {FirstName:{value:'Matt'}, LastName:{value:'Bellew'}} becomes [{value:'Matt'},{value:'Bellew'}]
 * ) create new parent row object containing object version of row data and array version of row data
 *      {asArray:[], asObject:{}, breakLevel:-1}
 * ) adds reference from each data value object to corresponding field description
 * ) add reference from each data value to parent row object
 * ) optionally computes break level for each row (and rowspan)
 *
 * ) consider: attach renderCellHtml() function to each value object, and maybe even renderRowHtml() to each row object
 * ) consider: getDomHelper() instead of Html()
 *
 * NOTE: the structure returned by transformSelectRowsResult() is not compatible with
 * SelectRowsResults (or ExtendedSelectRowsResults).
 *
 * @param qr
 */
LABKEY.TemplateHelper.transformSelectRowsResult = function(qr, config)
{
    var fields = qr.metaData.fields;
    var rows = qr.rows;
    var arrayrows = [];
    var i, field;

    // preprocess fields
    //  add index
    //  compute nameMap
    var nameMap = {};
    for (i=0 ; i < fields.length ; i++)
    {
        field = fields[i];
        field.index = i;
        field.fieldKey = (new LABKEY.FieldKey()).parseOldEncoding(field.fieldKeyPath);
        field.getCaptionHtml = getCaptionHtml;
        field.getFormattedDisplayValue = getFormattedDisplayValue;
        field.getDisplayValueHtml = getDisplayValueHtml;
        field.getGridCellHtml = getGridCellHtml;
        if (field.extFormatFn)
        {
            try
            {
                field.formatFn = eval(field.extFormatFn);
            }
            catch (ex)
            {
            }
        }
        nameMap[field.name] = field;
    }

    // compute break levels
    var breakFields = [];
    var breakLevel = undefined;
    var rowAtLastBreak = [];
    var breakValues = [];

    if (config && config.breakInfo)
    {
        for (i=0 ; i < config.breakInfo.length ; i++)
        {
            var breakInfo = config.breakInfo[i];
            var name = breakInfo.name;
            field = nameMap[name];
            if (!field)
                continue;
            field.className = "break-" + i;
            if (breakInfo.className)
                field.className += " " + breakInfo.className;
            breakFields.push(field);
            breakValues.push(undefined);
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
            if (!Ext.isObject(value))
                value = {value: value};
            value.field = field;
            value.parentRow = parentRow;
            value.rowspan=undefined;
            arrayRow.push(value);
        }

        if (breakFields.length == 0)
            continue;

        // compute breakLevel for this row
        breakLevel = breakFields.length;
        for (var b=breakFields.length-1; b >=0 ; b--)
        {
            var obj = arrayRow[breakFields[b].index];
            var v = (obj && Ext.isDefined(obj.value)) ? obj.value : "" ;
            var prev = breakValues[b];
            if (v != prev)
                breakLevel = b;
            breakValues[b] = v;
        }

        // update rowspans for previous breaks and rowspan for this row
        rowspans = new Array(breakFields.length);
        for (var b=breakFields.length-1 ; b>=0 ; b--)
        {
            var breakInfo = config.breakInfo[b];
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

        parentRow.asArray = arrayRow;
        parentRow.asObject = objectRow;
        parentRow.breakLevel = breakLevel == breakFields.length ? -1 : breakLevel;
        parentRow.rowspans = rowspans;
        arrayrows.push(parentRow);
    }

    return {fields: fields, breakFields : breakFields, rows:arrayrows};
};


})();


















/***************************************************************************/





function testIssues(renderTo)
{
    LABKEY.Query.selectRows({
/*	containerPath : '/home/Developer/issues', */
        requiredVersion: 12.1,
        schemaName: 'issues',
        queryName: 'Issues',
        columns: 'AssignedTo,Status,XY,IssueId,Created,Priority,Title,Type,AssignedTo/DisplayName,CreatedBy,Area,Milestone,Triage',
/*        filterArray: [LABKEY.Filter.create('Milestone', '12.', LABKEY.Filter.Types.STARTS_WITH),LABKEY.Filter.create('Status', 'closed', LABKEY.Filter.Types.NOT_EQUAL_OR_MISSING)], */
        sort: 'AssignedTo/DisplayName,Status,-IssueId',
        success: function(result)
		{
			// result.metaData.sortInfo is TOFU
			// result.metaData.sortInfo = [{field:"AssignedTo",direction:"ASC"},{field:"Status", direction:"ASC"}];
		   	var data = LABKEY.TemplateHelper.transformSelectRowsResult(
				result,
				{breakInfo:[{name:'AssignedTo', rowspans:false}, {name:'Status', rowspans:true}]}
			);
			EXT4_renderTo(renderTo, data);
		}
    });
}




var issueTmpl =
[
		'<table class="report"><thead>',
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
		'</table>',
].join("");


var rowspanTmpl1 =
[
		'<table class="report">',
		'<thead>',
				'<tpl for="fields">',
					'<th class="labkey-column-header">{[this.getCaptionHtml(values)]}</th>',
				'</tpl>',
		'</thead>',
		'<tpl for="rows">',
				'<tpl for="asArray">',
				    '<tpl if="xindex &lt;= parent.rowspans.length">',
    				    '<tpl if="parent.rowspans[xindex-1]&gt;0">',
        					'<td style="border:solid 1px black;" rowspan={[parent.rowspans[xindex-1]]}>{[this.getHtml(values)]}</td>',
	    			    '</tpl>',
				    '</tpl>',
				    '<tpl if="xindex &gt; parent.rowspans.length">',
    					'<td>{[this.getHtml(values)]}</td>',
				    '</tpl>',
				'</tpl>',
			'</tr>',
		'</tpl>',
		'</table>',
		'{[((new Date()).valueOf() - this.start)/1000.0]}'
].join("");


var rowspanTmpl2 =
[
		'<table class="report">',
		'<thead>',
				'<tpl for="fields">',
					'<th class="labkey-column-header">{[this.getCaptionHtml(values)]}</th>',
				'</tpl>',
		'</thead>',
		'<tpl for="rows">',
		        '<tr>',
				'<tpl for="asArray">',
    				'<tpl if="!rowspan">',
                        '<td>{[this.getHtml(values)]}</td>',
	    			'</tpl>',
				    '<tpl if="rowspan&lt;0"></tpl>',
    				'<tpl if="rowspan&gt;0">',
                        '<td style="border:solid 1px black;" rowspan={rowspan}>{[this.getHtml(values)]}</td>',
	    			'</tpl>',
				'</tpl>',
			'</tr>',
		'</tpl>',
		'</table>',
		'{[((new Date()).valueOf() - this.start)/1000.0]}'
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
		        '<tr>',
				'<tpl for="asArray">',
				    '{[this.getGridCellHtml(values)]}',
				'</tpl>',
			'</tr>',
		'</tpl>',
		'</table>',
		'{[((new Date()).valueOf() - this.start)/1000.0]}'
].join("");


function EXT4_renderTo(el, data)
{
	var $h = Ext.util.Format.htmlEncode;

	el = Ext.get(el);
	var tplOneBreak = new Ext.XTemplate
	(
	    rowspanTmpl3,
		{
		    getCaptionHtml : function(field)
		    {
                if (field.getCaptionHtml)
                    return field.getCaptionHtml();
                return field.shortCaption || field.name;
		    },
		    getGridCellHtml : function(d)
		    {
                if (d.field && d.field.getGridCellHtml)
                    return d.field.getGridCellHtml(d, !this.isPrint);
                return "<td>" + getHtml() + "</td>";
		    },
			getHtml : function(d)
			{
			    if (d.field && d.field.getDisplayValueHtml)
			        return d.field.getDisplayValueHtml(d, !this.isPrint);
				if (d.html)
					return d.html;
				var html = $h(this.getFormattedValue(d));
				if (d.url && !this.isPrint)
					html = "<a href=\"" + $h(d.url) + "\">" + html + "</a>";
				return html;
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
			getFormattedValue : function (d)
			{
			    if (d.field && d.field.getFormattedDisplayValue)
			        return d.field.getFormattedDisplayValue(d);
                var v = d.displayValue || d.value;
                return Ext.isDefined(v) ? v.toString() : "";
			},
			dateFormat:null,
			gridRow : 0,
			isPrint : -1 != window.location.href.indexOf("_print=true") || -1 != window.location.href.indexOf("_print=1"),
			start : (new Date()).valueOf()
		}
	);
	tplOneBreak.overwrite(el, data);
}

