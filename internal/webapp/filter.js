/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var _tableName = "";
var _fieldName = "";
var _fieldCaption = "";
var _fieldType = "text";
var _filterDiv = null;
var _filterWin = null;
var _filterQueryString = "";

function setFilterQueryString(s)
{
    _filterQueryString = s;
}


function getFilterDiv()
{
    if (!_filterDiv)
    {
        LABKEY.addMarkup('<div id="filterDiv" style="display:none;">' +
        '  <table onkeypress="handleKey(event);">' +
        '    <tr>' +
        '      <td colspan=2 style="padding: 5px" nowrap>' +
        '        <select id="compare_1" name="compare_1" onchange="doChange(this)">' +
        '            <option value="">&lt;has any value></option>' +
        '        </select><br>' +
        '        <input disabled id="value_1" style="visibility:hidden" type=text name=value_1><br>' +
        '        <span id="compareSpan_2" style="visibility:hidden">and<br>' +
        '        <select id="compare_2" name="compare_2" onchange="doChange(this)">' +
        '            <option value="">&lt;no other filter></option>' +
        '        </select><br>' +
        '        <input disabled style="visibility:hidden" id="value_2" type="text" name="value_2"><br><br>' +
        '        </span>' +
        '        <a class="labkey-button" href="#" onclick="doFilter();return false;"><span>OK</span> ' +
        '        <a class="labkey-button" href="#" onclick="hideFilterDiv();return false;"><span>Cancel</span> ' +
        '        <a class="labkey-button" href="#" onclick="clearFilter();return false;"><span>Clear Filter</span> ' +
        '        <a class="labkey-button" href="#" onclick="clearAllFilters();return false;"><span>Clear All Filters</span> ' +
        '      </td>' +
        '    </tr>' +
        '  </table>' +
        '</div>');
        _filterDiv = document.getElementById("filterDiv");
    }
    return _filterDiv;
}

function doChange(obj)
{
    var name = obj.name;
    var index = name.split("_")[1];
    var valueInput = document.getElementById("value_" + index);
    var compare = obj.options[obj.selectedIndex].value;
    if (compare == "" || compare == "isblank" || compare == "isnonblank" || compare == "nomvvalue" || compare == "hasmvvalue")
    {
        if (index == "1")
            document.getElementById("compareSpan_2").style.visibility = "hidden";
        valueInput.style.visibility = "hidden";
        valueInput.disabled = true;
    }
    else
    {
        if (index == "1")
            document.getElementById("compareSpan_2").style.visibility = "visible";
        valueInput.style.visibility = "visible";
        valueInput.disabled = false;
        valueInput.focus();
        valueInput.select();
    }
}

if (navigator.userAgent.toLowerCase().indexOf("httpunit") < 0)
{
    LABKEY.requiresYahoo('yahoo');
    LABKEY.requiresYahoo('dom');
    LABKEY.requiresYahoo('event');
    LABKEY.requiresYahoo('dragdrop');
}

function showFilterPanel(elem, tableName, colName, caption, dataType, mvEnabled)
{
    _fieldName = colName;
    _fieldCaption = caption;
    _tableName = tableName;
    fillOptions(dataType, mvEnabled);

    var paramValPairs = getParamValPairs(null);
    //Fill in existing filters...
    var filterIndex = 1;
    for (var i = 0; i < paramValPairs.length; i++)
    {
        var textbox = document.getElementById("value_" + filterIndex);
        textbox.value = "";
        var pair = paramValPairs[i];
        if (pair[0].indexOf(_tableName + "." + _fieldName + "~") == 0)
        {
            var comparison = (pair[0].split("~"))[1];
            var select = document.getElementById("compare_" + filterIndex);
            for (var opt = 0; opt < select.options.length; opt++)
            {
                if (select.options[opt].value == comparison)
                {
                    select.selectedIndex = opt;
                    break;
                }
            }

            if (pair.length > 1)
            {
                textbox = document.getElementById("value_" + filterIndex);
                textbox.value = pair[1];
            }

            filterIndex++;
            if (filterIndex > 2)
                break;
        }
    }
    var div = getFilterDiv();
    div.style.display = "block";
    div.style.visibility = "visible";

    if (!_filterWin)
    {
        _filterWin = new Ext.Window({
            contentEl: div,
            width: 350,
            autoHeight: true,
            modal: true,
            resizable: false,
            closeAction: 'hide'
        });

        // 5975: Override focus behavior. Keeps Ext.Window from stealing focus after showing.
        _filterWin.focus = function () {
            doChange(document.getElementById("compare_1"));
            doChange(document.getElementById("compare_2"));
        };
    }
    else
    {
        _filterWin.center();
    }

    if (filterIndex == 2)
        document.getElementById("compare_2").selectedIndex = 0;

    _filterWin.setTitle("Show Rows Where " + caption);
    _filterWin.show();
}

function hideFilterDiv()
{
    if (_filterWin)
        _filterWin.hide();
}

var _typeMap = {
    "BIGINT":"INT",
    "BIGSERIAL":"INT",
    "BIT":"BOOL",
    "BOOL":"BOOL",
    "BOOLEAN":"BOOL",
    "CHAR":"TEXT",
    "CLOB":"LONGTEXT",
    "DATE":"DATE",
    "DECIMAL":"DECIMAL",
    "DOUBLE":"DECIMAL",
    "DOUBLE PRECISION":"DECIMAL",
    "FLOAT":"DECIMAL",
    "INTEGER":"INT",
    "LONGVARCHAR":"LONGTEXT",
    "NTEXT":"LONGTEXT",
    "NUMERIC":"DECIMAL",
    "REAL":"DECIMAL",
    "SMALLINT":"INT",
    "TIME":"TEXT",
    "TIMESTAMP":"DATE",
    "TINYINT":"INT",
    "VARCHAR":"TEXT",
    "INT":"INT",
    "INT IDENTITY":"INT",
    "DATETIME":"DATE",
    "TEXT":"TEXT",
    "NVARCHAR":"TEXT",
    "INT2":"INT",
    "INT4":"INT",
    "INT8":"INT",
    "FLOAT4":"DECIMAL",
    "FLOAT8":"DECIMAL",
    "SERIAL":"INT"
};
var _mappedType = "TEXT";

function fillOptions(dataType, mvEnabled)
{
    getFilterDiv();
    var mappedType = _typeMap[dataType.toUpperCase()];
    if (mappedType == undefined)
        mappedType = dataType.toUpperCase();

    for (var i = 1; i <= 2; i++)
    {
        var select = document.getElementById("compare_" + i);
        var opt;
        select.options.length = 1;

        if (mappedType != "LONGTEXT")
        {
            opt = document.createElement("OPTION");
            if (mappedType == "DATE")
                opt.value = "dateeq";
            else
                opt.value = "eq";
            opt.text = "Equals";
            appendOption(select, opt);
            if (mappedType != "BOOL")
            {
                opt = document.createElement("OPTION");
                opt.value = "in";
                opt.text = "Equals One Of (e.g. 'a;b;c')";
                appendOption(select, opt);
            }
            opt = document.createElement("OPTION");
            if (mappedType == "DATE")
                opt.value = "dateneq";
            else
                opt.value = "neqornull";
            opt.text = "Does not Equal";
            appendOption(select, opt);
        }

        opt = document.createElement("OPTION");
        opt.value = "isblank";
        opt.text = "Is Blank";
        appendOption(select, opt);

        opt = document.createElement("OPTION");
        opt.value = "isnonblank";
        opt.text = "Is Not Blank";
        appendOption(select, opt);

        if (mappedType != "LONGTEXT" && mappedType != "BOOL")
        {
            opt = document.createElement("OPTION");
            opt.value = "gt";
            opt.text = "Is Greater Than";
            appendOption(select, opt);
            opt = document.createElement("OPTION");
            opt.value = "lt";
            opt.text = "Is Less Than";
            appendOption(select, opt);
            opt = document.createElement("OPTION");
            opt.value = "gte";
            opt.text = "Is Greater Than or Equal To";
            appendOption(select, opt);
            opt = document.createElement("OPTION");
            opt.value = "lte";
            opt.text = "Is Less Than or Equal To";
            appendOption(select, opt);
        }

        if (mappedType == "TEXT" || mappedType == "LONGTEXT")
        {
            opt = document.createElement("OPTION");
            opt.value = "startswith";
            opt.text = "Starts With";
            appendOption(select, opt);
            opt = document.createElement("OPTION");
            opt.value = "doesnotstartwith";
            opt.text = "Does Not Start With";
            appendOption(select, opt);
            opt = document.createElement("OPTION");
            opt.value = "contains";
            opt.text = "Contains";
            appendOption(select, opt);
            opt = document.createElement("OPTION");
            opt.value = "doesnotcontain";
            opt.text = "Does Not Contain";
            appendOption(select, opt);
        }

        if (mvEnabled)
        {
            opt = document.createElement("OPTION");
            opt.value = "hasmvvalue";
            opt.text = "Has a missing value indicator";
            appendOption(select, opt);

            opt = document.createElement("OPTION");
            opt.value = "nomvvalue";
            opt.text = "Does not have a missing value indicator";
            appendOption(select, opt);
        }

        if (i == 1)
            selectDefault(select, mappedType);
    }

    _mappedType = mappedType;
}

function appendOption(select, opt)
{
    select.options[select.options.length] = opt;
}

function selectDefault(select, mappedType)
{
    if (mappedType == "LONGTEXT")
        selectByValue(select, "contains");
    else if (mappedType == "DECIMAL")
        selectByValue(select, "gte");
    else if (mappedType == "TEXT")
        selectByValue(select, "startswith");
    else if (select.options.length > 1)
        select.selectedIndex = 1;
}

function selectByValue(select, value)
{
    for (var i = 0; i < select.options.length; i++)
        if (select.options[i].value == value)
        {
            select.selectedIndex = i;
            return;
        }
}


var savedSearchString = null;
var filterListeners = [];

function registerFilterListener(fn)
{
    filterListeners.push(fn);
}

function getSearchString()
{
    if (null == savedSearchString)
        savedSearchString = document.location.search.substring(1) || "";
    return savedSearchString;
}

function setSearchString(tableName, search)
{
    hideFilterDiv();
    savedSearchString = search || "";
    for (var i=0; i < filterListeners.length; i++)
    {
        if (filterListeners[i](tableName, search))
        {
            hideFilterDiv();
            return;
        }
    }
    window.location.search = "?" + savedSearchString;
}


function getParamValPairs(skipPrefixes)
{
    var queryString = getSearchString();
    var iNew = 0;
    //alert("getparamValPairs: " + queryString);
    var newParamValPairs = new Array(0);
    if (queryString != null && queryString.length > 0)
    {
        var paramValPairs = queryString.split("&");
        PARAM_LOOP: for (var i = 0; i < paramValPairs.length; i++)
        {
            var paramPair = paramValPairs[i].split("=");
            paramPair[0] = unescape(paramPair[0]);

            if (paramPair[0] == ".lastFilter")
                continue;

            if (skipPrefixes)
            {
                for (var j = 0; j < skipPrefixes.length; j++)
                {
                    var skipPrefix = skipPrefixes[j];
                    if (skipPrefix && paramPair[0].indexOf(skipPrefix) == 0)
                    {
                        // only skip filter params and sort.
                        if (paramPair[0] == skipPrefix)
                            continue PARAM_LOOP;
                        if (paramPair[0].indexOf("~") > 0)
                            continue PARAM_LOOP;
                        if (paramPair[0] == skipPrefix + "sort")
                            continue PARAM_LOOP;
                    }
                }
            }
            if (paramPair.length > 1)
            {
                // unescape doesn't handle '+' correctly, so swap them with ' ' first
                paramPair[1] = unescape(paramPair[1].replace(/\+/g, " "));
            }
            newParamValPairs[iNew] = paramPair;
            iNew++;
        }
    }
    return newParamValPairs;
}

function getParameter(paramName)
{
    var paramValPairs = getParamValPairs(null);
    for (var i = 0; i < paramValPairs.length; i++)
        if (paramValPairs[i][0] == paramName)
            if (paramValPairs[i].length > 1)
                return paramValPairs[i][1];
            else
                return "";

    return null;
}

function buildQueryString(pairs)
{
    if (pairs == null || pairs.length == 0)
        return "";

    //alert("enter buildQueryString");
    var paramArray = new Array(pairs.length);
    for (var i = 0; i < pairs.length; i++)
    {
        // alert("pair" + pairs[i]);
        if (pairs[i].length > 1)
            paramArray[i] = pairs[i][0] + "=" + escape(pairs[i][1]);
        else
            paramArray[i] = pairs[i][0];
    }

    // Escape doesn't encode '+' properly
    var queryString = paramArray.join("&").replace(/\+/g, "%2B");
    // alert("exit buildQueryString: " + queryString);
    return queryString;
}

function clearFilter()
{
    var newParamValPairs = getParamValPairs([_tableName + "." + _fieldName + "~", _tableName + ".offset"]);
    setSearchString(_tableName, buildQueryString(newParamValPairs));
}

function clearAllFilters()
{
    var newParamValPairs = getParamValPairs([_tableName + ".", _tableName + ".offset"]);
    setSearchString(_tableName, buildQueryString(newParamValPairs));
}

function doFilter()
{
    var newParamValPairs = getParamValPairs([_tableName + "." + _fieldName + "~", _tableName + ".offset"]);
    var iNew = newParamValPairs.length;

    var comparisons = getValidCompares();
    if (null == comparisons)
        return;

    for (var i = 0; i < comparisons.length; i++)
    {
        newParamValPairs[iNew] = comparisons[i];
        iNew ++;
    }

    //alert("new: " +buildQueryString(newParamValPairs));
    setSearchString(_tableName, buildQueryString(newParamValPairs));
}

function getValidComparesFromForm(formIndex, newParamValPairs)
{
    var obj = document.getElementById("compare_" + formIndex);
    var comparison = obj.options[obj.selectedIndex].value;
    var compareTo = document.getElementById("value_" + formIndex).value;
    //alert("comparison: " + comparison + ", compareTo: " + compareTo);
    if (comparison != "")
    {
        var pair;
        if (comparison == "isblank" || comparison == "isnonblank" || comparison == "nomvvalue" || comparison == "hasmvvalue")
        {
            pair = [_tableName + "." + _fieldName + "~" + comparison];
        }
        else
        {
            var validCompareTo;
            if (comparison == 'in')
            {
                validCompareTo = validateMultiple(compareTo);
            }
            else
            {
                validCompareTo = validate(compareTo);
            }

            if (validCompareTo == undefined)
                return false;
            pair = [_tableName + "." + _fieldName + "~" + comparison, validCompareTo];
        }
        newParamValPairs[newParamValPairs.length] = pair;
    }
    return true;
}

function getValidCompares()
{
    var newParamValPairs = new Array(0);

    var success = getValidComparesFromForm(1, newParamValPairs);
    if (!success)
    {
        return null;
    }
    success = getValidComparesFromForm(2, newParamValPairs);
    if (!success)
    {
        return null;
    }
    return newParamValPairs;
}

function validateMultiple(allValues, mappedType, fieldName)
{
    if (!mappedType) mappedType = _mappedType;
    if (!fieldName) fieldName = _fieldCaption || _fieldName;

    if (!allValues)
    {
        alert("filter value for field '" + fieldName + "' cannot be empty.");
        return undefined;
    }
    var values = allValues.split(";");
    var result = '';
    var separator = '';
    for (var i = 0; i < values.length; i++)
    {
        var value = validate(values[i].trim(), mappedType, fieldName);
        if (value == undefined)
            return undefined;

        result = result + separator + value;
        separator = ";";
    }
    return result;
}

function validate(value, mappedType, fieldName)
{
    if (!mappedType) mappedType = _mappedType;
    if (!fieldName) fieldName = _fieldCaption || _fieldName;

    if (!value)
    {
        alert("filter value for field '" + fieldName + "' cannot be empty.");
        return undefined
    }

    if (mappedType == "INT")
    {
        var intVal = parseInt(value);
        if (isNaN(intVal))
        {
            alert(value + " is not a valid integer for field '" + fieldName + "'.");
            return undefined;
        }
        else
            return "" + intVal;
    }
    else if (mappedType == "DECIMAL")
    {
        var decVal = parseFloat(value);
        if (isNaN(decVal))
        {
            alert(value + " is not a valid decimal number for field '" + fieldName + "'.");
            return undefined;
        }
        else
            return "" + decVal;
    }
    else if (mappedType == "DATE")
    {
        var year, month, day, hour, minute;
        hour = 0;
        minute = 0;

        //Javascript does not parse ISO dates, but if date matches we're done
        if (value.match(/^\s*(\d\d\d\d)-(\d\d)-(\d\d)\s*$/) ||
            value.match(/^\s*(\d\d\d\d)-(\d\d)-(\d\d)\s*(\d\d):(\d\d)\s*$/))
        {
            return value;
        }
        else
        {
            var dateVal = new Date(value);
            if (isNaN(dateVal))
            {
                alert(value + " is not a valid date for field '" + fieldName + "'.");
                return undefined;
            }
            //Try to do something decent with 2 digit years!
            //if we have mm/dd/yy (but not mm/dd/yyyy) in the date
            //fix the broken date parsing
            if (value.match(/\d+\/\d+\/\d{2}(\D|$)/))
            {
                if (dateVal.getFullYear() < new Date().getFullYear() - 80)
                    dateVal.setFullYear(dateVal.getFullYear() + 100);
            }
            year = dateVal.getFullYear();
            month = dateVal.getMonth() + 1;
            day = dateVal.getDate();
            hour = dateVal.getHours();
            minute = dateVal.getMinutes();
        }
        var str = "" + year + "-" + twoDigit(month) + "-" + twoDigit(day);
        if (hour != 0 || minute != 0)
            str += " " + twoDigit(hour) + ":" + twoDigit(minute);

        return str;
    }
    else if (mappedType == "BOOL")
    {
        var upperVal = value.toUpperCase();
        if (upperVal == "TRUE" || value == "1" || upperVal == "Y" || upperVal == "YES" || upperVal == "ON" || upperVal == "T")
            return "1";
        if (upperVal == "FALSE" || value == "0" || upperVal == "N" || upperVal == "NO" || upperVal == "OFF" || upperVal == "F")
            return "0";
        else
        {
            alert(value + " is not a valid boolean for field '" + fieldName + "'. Try true,false; yes,no; on,off; or 1,0.");
            return undefined
        }
    }
    else
        return value;
}

function twoDigit(num)
{
    if (num < 10)
        return "0" + num;
    else
        return "" + num;
}

function doSort(tableName, columnName, sortDirection)
{
    var newSortArray = new Array(1);
    //sort forward
    var sortString = getParameter(tableName + ".sort");
    var currentSort;

    if (sortString != null)
    {
        var sortArray = sortString.split(",");
        for (var j = 0; j < sortArray.length; j++)
        {
            if (sortArray[j] == columnName || sortArray[j] == "+" + columnName)
                currentSort = "+";
            else if (sortArray[j] == "-" + columnName)
                currentSort = "-";
            else if (newSortArray.length <= 2)
                newSortArray[newSortArray.length] = sortArray[j];
        }
    }

    if (sortDirection == "+") //Easier to read without the encoded + on the URL...
        sortDirection = "";
    newSortArray[0] = sortDirection + columnName;

    var paramValPairs = getParamValPairs([tableName + ".sort", tableName + ".offset"]);
    paramValPairs[paramValPairs.length] = [tableName + ".sort", newSortArray.join(",")];

    setSearchString(tableName, buildQueryString(paramValPairs));
}

function clearSort(tableName, columnName)
{
    if(!tableName || !columnName)
        return;

    var sortString =  getParameter(tableName + ".sort");
    if(!sortString)
        return;

    var sortArray = sortString.split(",");
    var newSortArray = [];

    for(var idx = 0; idx < sortArray.length; ++idx)
    {
        if(sortArray[idx] != columnName && sortArray[idx] != "-" + columnName)
            newSortArray.push(sortArray[idx]);
    }

    var paramValPairs = getParamValPairs([tableName + ".sort", tableName + ".offset"]);
    if(newSortArray.length > 0)
        paramValPairs.push([tableName + ".sort", newSortArray.join(",")]);

    setSearchString(tableName, buildQueryString(paramValPairs));
}

// If at least one checkbox on the form is selected then GET/POST url.  Otherwise, display an error.
function verifySelected(form, url, method, pluralNoun, confirmText)
{
    var checked = false;
    var elems = form.elements;
    var l = elems.length;
    for (var i = 0; i < l; i++)
    {
        var e = elems[i];
        if (e.type == 'checkbox' && e.checked == true && e.name != '.toggle')
        {
            checked = true;
            break;
        }
    }
    if (checked)
    {
        if ((window.parent == window) && (null != confirmText))
        {
            if (!window.confirm(confirmText))
                return false;
        }
        form.action = url;
        form.method = method;
        return true;
    }
    else
    {
        window.alert('Please select one or more ' + pluralNoun + '.');
        return false;
    }
}

function handleKey(event)
{
    switch (event.keyCode)
    {
        case 13: // enter
            doFilter();
            break;

        case 27: // esc
            hideFilterDiv();
            break;
    }
}
