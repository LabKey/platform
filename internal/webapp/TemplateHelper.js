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


/**
 * This method takes a query result set and preprocesses it
 *
 * ) creates array version of each row (covient for template iteration)
 *      {FirstName:{value:'Matt'}, LastName:{value:'Bellew'}} becomes [{value:'Matt'},{value:'Bellew'}]
 * ) create new parent row object containing object version of row data and array version of row data
 *      {asArray:[], asObject:{}, breakLevel:-1}
 * ) adds reference from each data value object to corresponding field description
 * ) add reference from each data value to parent row object
 * ) optionally computes break level for each row
 *
 * ) consider: attach getHtml() function to each value object, and maybe even row object
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
        nameMap[field.name] = field;
    }

    // compute break levels
    var breakFields = [];
    var breakValues = [];
    var breakLevel = undefined; 

    if (config && config.breakInfo)
    {
        for (i=0 ; i < config.breakInfo.length ; i++)
        {
            var name = config.breakInfo[i];
            field = nameMap[name];
            if (!field)
                continue;
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
            arrayRow.push(value);
        }

        breakLevel = -1;
        for (var b=breakFields.length-1; b >=0 ; b--)
        {
            var obj = arrayRow[breakFields[b].index];
            var v = (obj && Ext.isDefined(obj.value)) ? obj.value : "" ;
            var prev = breakValues[b];
            if (v != prev)
                breakLevel = b;
            breakValues[b] = v;
        }

        parentRow.asArray = arrayRow;
        parentRow.asObject = objectRow;
        parentRow.breakLevel = breakLevel;
        arrayrows.push(parentRow);
    }

    return {fields: fields, breakFields : breakFields, rows:arrayrows};
};
