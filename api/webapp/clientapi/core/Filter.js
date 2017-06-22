/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2017 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */

 /**
  * @class LABKEY.Filter
  * @namespace  Filter static class to describe and create filters.
  *            <p>Additional Documentation:
  *              <ul>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=filteringData">Filter via the LabKey UI</a></li>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=tutorialActionURL">Tutorial: Basics: Building URLs and Filters</a></li>
  *              </ul>
  *           </p>
 * @property {Object} Types Types static class to describe different types of filters.
 * @property {LABKEY.Filter.FilterDefinition} Types.EQUAL Finds rows where the column value matches the given filter value. Case-sensitivity depends upon how your underlying relational database was configured.
 * @property {LABKEY.Filter.FilterDefinition} Types.DATE_EQUAL Finds rows where the date portion of a datetime column matches the filter value (ignoring the time portion).
 * @property {LABKEY.Filter.FilterDefinition} Types.DATE_NOT_EQUAL Finds rows where the date portion of a datetime column does not match the filter value (ignoring the time portion).
 * @property {LABKEY.Filter.FilterDefinition} Types.NOT_EQUAL_OR_MISSING Finds rows where the column value does not equal the filter value, or is missing (null).
 * @property {LABKEY.Filter.FilterDefinition} Types.NOT_EQUAL Finds rows where the column value does not equal the filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.MISSING Finds rows where the column value is missing (null). Note that no filter value is required with this operator.
 * @property {LABKEY.Filter.FilterDefinition} Types.NOT_MISSING Finds rows where the column value is not missing (is not null). Note that no filter value is required with this operator.
 * @property {LABKEY.Filter.FilterDefinition} Types.GREATER_THAN Finds rows where the column value is greater than the filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.LESS_THAN Finds rows where the column value is less than the filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.GREATER_THAN_OR_EQUAL Finds rows where the column value is greater than or equal to the filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.LESS_THAN_OR_EQUAL Finds rows where the column value is less than or equal to the filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.CONTAINS Finds rows where the column value contains the filter value. Note that this may result in a slow query as this cannot use indexes.
 * @property {LABKEY.Filter.FilterDefinition} Types.DOES_NOT_CONTAIN Finds rows where the column value does not contain the filter value. Note that this may result in a slow query as this cannot use indexes.
 * @property {LABKEY.Filter.FilterDefinition} Types.DOES_NOT_START_WITH Finds rows where the column value does not start with the filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.STARTS_WITH Finds rows where the column value starts with the filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.IN Finds rows where the column value equals one of the supplied filter values. The values should be supplied as a semi-colon-delimited list (example usage: a;b;c).
 * @property {LABKEY.Filter.FilterDefinition} Types.NOT_IN Finds rows where the column value is not in any of the supplied filter values. The values should be supplied as a semi-colon-delimited list (example usage: a;b;c).
 * @property {LABKEY.Filter.FilterDefinition} Types.MEMBER_OF Finds rows where the column value contains a user id that is a member of the group id of the supplied filter value.
 * @property {LABKEY.Filter.FilterDefinition} Types.CONTAINS_ONE_OF Finds rows where the column value contains any of the supplied filter values. The values should be supplied as a semi-colon-delimited list (example usage: a;b;c).
 * @property {LABKEY.Filter.FilterDefinition} Types.CONTAINS_NONE_OF Finds rows where the column value does not contain any of the supplied filter values. The values should be supplied as a semi-colon-delimited list (example usage: a;b;c).
 * @property {LABKEY.Filter.FilterDefinition} Types.BETWEEN Finds rows where the column value is between the two filter values, inclusive. The values should be supplied as a comma-delimited list (example usage: -4,4).
 * @property {LABKEY.Filter.FilterDefinition} Types.NOT_BETWEEN Finds rows where the column value is not between the two filter values, exclusive. The values should be supplied as a comma-delimited list (example usage: -4,4).
 *
 */
LABKEY.Filter = new function()
{
    function validateMultiple(type, value, colName, sep, minOccurs, maxOccurs)
    {
        var values = value.split(sep);
        var result = '';
        var separator = '';
        for (var i = 0; i < values.length; i++)
        {
            var value = validate(type, values[i].trim(), colName);
            if (value == undefined)
                return undefined;

            result = result + separator + value;
            separator = sep;
        }

        if (minOccurs !== undefined && minOccurs > 0)
        {
            if (values.length < minOccurs)
            {
                alert("At least " + minOccurs + " '" + sep + "' separated values are required");
                return undefined;
            }
        }

        if (maxOccurs !== undefined && maxOccurs > 0)
        {
            if (values.length > maxOccurs)
            {
                alert("At most " + maxOccurs + " '" + sep + "' separated values are allowed");
                return undefined;
            }
        }

        return result;
    }

    /**
     * Note: this is an experimental API that may change unexpectedly in future releases.
     * Validate a form value against the json type.  Error alerts will be displayed.
     * @param type The json type ("int", "float", "date", or "boolean")
     * @param value The value to test.
     * @param colName The column name to use in error messages.
     * @return undefined if not valid otherwise a normalized string value for the type.
     */
    function validate(type, value, colName)
    {
        if (type == "int")
        {
            var intVal = parseInt(value);
            if (isNaN(intVal))
            {
                alert(value + " is not a valid integer for field '" + colName + "'.");
                return undefined;
            }
            else
                return "" + intVal;
        }
        else if (type == "float")
        {
            var decVal = parseFloat(value);
            if (isNaN(decVal))
            {
                alert(value + " is not a valid decimal number for field '" + colName + "'.");
                return undefined;
            }
            else
                return "" + decVal;
        }
        else if (type == "date")
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
                    //filters can use relative dates, in the format +1d, -5H, etc.  we try to identfy those here
                    //this is fairly permissive and does not attempt to parse this value into a date.  See CompareType.asDate()
                    //for server-side parsing
                    if (value.match(/^(-|\+)/i))
                    {
                        return value;
                    }

                    alert(value + " is not a valid date for field '" + colName + "'.");
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
        else if (type == "boolean")
        {
            var upperVal = value.toUpperCase();
            if (upperVal == "TRUE" || value == "1" || upperVal == "YES" || upperVal == "Y" || upperVal == "ON" || upperVal == "T")
                return "1";
            if (upperVal == "FALSE" || value == "0" || upperVal == "NO" || upperVal == "N" || upperVal == "OFF" || upperVal == "F")
                return "0";
            else
            {
                alert(value + " is not a valid boolean for field '" + colName + "'. Try true,false; yes,no; y,n; on,off; or 1,0.");
                return undefined;
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

    var urlMap = {};
    var oppositeMap = {
        //HAS_ANY_VALUE: null,
        eq: 'neqornull',
        dateeq : 'dateneq',
        dateneq : 'dateeq',
        neqornull : 'eq',
        neq : 'eq',
        isblank : 'isnonblank',
        isnonblank : 'isblank',
        gt : 'lte',
        dategt : 'datelte',
        lt : 'gte',
        datelt : 'dategte',
        gte : 'lt',
        dategte : 'datelt',
        lte : 'gt',
        datelte : 'dategt',
        contains : 'doesnotcontain',
        doesnotcontain : 'contains',
        doesnotstartwith : 'startswith',
        startswith : 'doesnotstartwith',
        'in' : 'notin',
        notin : 'in',
        memberof : 'memberof',
        containsoneof : 'containsnoneof',
        containsnoneof : 'containsoneof',
        hasmvvalue : 'nomvvalue',
        nomvvalue : 'hasmvvalue',
        between : 'notbetween',
        notbetween : 'between'
    };

    //NOTE: these maps contains the unambiguous pairings of single- and multi-valued filters
    //due to NULLs, one cannot easily convert neq to notin
    var multiValueToSingleMap = {
        'in' : 'eq',
        containsoneof : 'contains',
        containsnoneof : 'doesnotcontain',
        between: 'gte',
        notbetween: 'lt'
    };

    var singleValueToMultiMap = {
        eq : 'in',
        neq : 'notin',
        neqornull: 'notin',
        doesnotcontain : 'containsnoneof',
        contains : 'containsoneof'
    };

    function createNoValueFilterType(displayText, displaySymbol, urlSuffix, longDisplayText)
    {
        return createFilterType(displayText, displaySymbol, urlSuffix, false, false, null, longDisplayText);
    }

    function createSingleValueFilterType(displayText, displaySymbol, urlSuffix, longDisplayText)
    {
        return createFilterType(displayText, displaySymbol, urlSuffix, true, false, null, longDisplayText);
    }

    function createMultiValueFilterType(displayText, displaySymbol, urlSuffix, longDisplayText, multiValueSeparator, minOccurs, maxOccurs)
    {
        return createFilterType(displayText, displaySymbol, urlSuffix, true, false, multiValueSeparator, longDisplayText, minOccurs, maxOccurs);
    }

    function createTableFilterType(displayText, displaySymbol, urlSuffix, longDisplayText)
    {
        return createFilterType(displayText, displaySymbol, urlSuffix, true, true, null, longDisplayText);
    }

    function createFilterType(displayText, displaySymbol, urlSuffix, dataValueRequired, isTableWise, multiValueSeparator, longDisplayText, minOccurs, maxOccurs)
    {
        var result = {
            getDisplaySymbol : function() { return displaySymbol },
            getDisplayText : function() { return displayText },
            getLongDisplayText : function() { return longDisplayText || displayText },
            getURLSuffix : function() { return urlSuffix },
            isDataValueRequired : function() { return dataValueRequired === true },
            isMultiValued : function() { return multiValueSeparator != null; },
            isTableWise : function() { return isTableWise === true },
            getMultiValueSeparator : function() { return multiValueSeparator },
            getMultiValueMinOccurs : function() { return minOccurs },
            getMultiValueMaxOccurs : function() { return maxOccurs; },
            getOpposite : function() {return oppositeMap[urlSuffix] ? urlMap[oppositeMap[urlSuffix]] : null},
            getSingleValueFilter : function() {return this.isMultiValued() ? urlMap[multiValueToSingleMap[urlSuffix]] : this},
            getMultiValueFilter : function() {return this.isMultiValued() ? null : urlMap[singleValueToMultiMap[urlSuffix]]},
            validate : function (value, type, colName) {
                if (!dataValueRequired)
                    return true;

                var f = filterTypes[type];
                var found = false;
                for (var i = 0; !found && i < f.length; i++)
                {
                    if (f[i].getURLSuffix() == urlSuffix)
                        found = true;
                }
                if (!found) {
                    alert("Filter type '" + displayText + "' can't be applied to " + type + " types.");
                    return undefined;
                }

                if (this.isMultiValued())
                    return validateMultiple(type, value, colName, multiValueSeparator, minOccurs, maxOccurs);
                else
                    return validate(type, value, colName);
            }
        };
        urlMap[urlSuffix] = result;
        return result;
    }

    function getFilter(columnName, value, filterType)
    {
        var column = filterType.isTableWise() ? "*" : columnName;

        return {
            getColumnName: function() {return column;},
            getValue: function() {return value},
            getFilterType: function() {return filterType},
            getURLParameterName : function(dataRegionName) { return (dataRegionName || "query") + "." + column + "~" + filterType.getURLSuffix();},
            getURLParameterValue : function() { return filterType.isDataValueRequired() ? value : "" }
        };
    }

    var ret = /** @scope LABKEY.Filter */{

        // WARNING: Keep in sync and in order with all other client apis and docs
        // - server: CompareType.java
        // - java: Filter.java
        // - js: Filter.js
        // - R: makeFilter.R, makeFilter.Rd
        // - SAS: labkeymakefilter.sas, labkey.org SAS docs
        // - Python & Perl don't have an filter operator enum
        // - EXPERIMENTAL: Added an optional displaySymbol() for filters that want to support it
        Types : {

            HAS_ANY_VALUE : createNoValueFilterType("Has Any Value", null, "", null),

            //
            // These operators require a data value
            //

            EQUAL : createSingleValueFilterType("Equals", "=", "eq", null),
            DATE_EQUAL : createSingleValueFilterType("Equals", "=", "dateeq", null),

            NEQ : createSingleValueFilterType("Does Not Equal", "<>", "neq", null),
            NOT_EQUAL : createSingleValueFilterType("Does Not Equal", "<>", "neq", null),
            DATE_NOT_EQUAL : createSingleValueFilterType("Does Not Equal", "<>", "dateneq", null),

            NEQ_OR_NULL : createSingleValueFilterType("Does Not Equal", "<>", "neqornull", null),
            NOT_EQUAL_OR_MISSING : createSingleValueFilterType("Does Not Equal", "<>", "neqornull", null),

            GT : createSingleValueFilterType("Is Greater Than", ">", "gt", null),
            GREATER_THAN : createSingleValueFilterType("Is Greater Than", ">", "gt", null),
            DATE_GREATER_THAN : createSingleValueFilterType("Is Greater Than", ">", "dategt", null),

            LT : createSingleValueFilterType("Is Less Than", "<", "lt", null),
            LESS_THAN : createSingleValueFilterType("Is Less Than", "<", "lt", null),
            DATE_LESS_THAN : createSingleValueFilterType("Is Less Than", "<", "datelt", null),

            GTE : createSingleValueFilterType("Is Greater Than or Equal To", ">=", "gte", null),
            GREATER_THAN_OR_EQUAL : createSingleValueFilterType("Is Greater Than or Equal To", ">=", "gte", null),
            DATE_GREATER_THAN_OR_EQUAL : createSingleValueFilterType("Is Greater Than or Equal To", ">=", "dategte", null),

            LTE : createSingleValueFilterType("Is Less Than or Equal To", "=<", "lte", null),
            LESS_THAN_OR_EQUAL : createSingleValueFilterType("Is Less Than or Equal To", "=<", "lte", null),
            DATE_LESS_THAN_OR_EQUAL : createSingleValueFilterType("Is Less Than or Equal To", "=<", "datelte", null),

            STARTS_WITH : createSingleValueFilterType("Starts With", null, "startswith", null),
            DOES_NOT_START_WITH : createSingleValueFilterType("Does Not Start With", null, "doesnotstartwith", null),

            CONTAINS : createSingleValueFilterType("Contains", null, "contains", null),
            DOES_NOT_CONTAIN : createSingleValueFilterType("Does Not Contain", null, "doesnotcontain", null),

            CONTAINS_ONE_OF : createMultiValueFilterType("Contains One Of", null, "containsoneof", 'Contains One Of (example usage: a;b;c)', ";"),
            CONTAINS_NONE_OF : createMultiValueFilterType("Does Not Contain Any Of", null, "containsnoneof", 'Does Not Contain Any Of (example usage: a;b;c)', ";"),

            IN : createMultiValueFilterType("Equals One Of", null, "in", 'Equals One Of (example usage: a;b;c)', ";"),
            //NOTE: for some reason IN is aliased as EQUALS_ONE_OF.  not sure if this is for legacy purposes or it was determined EQUALS_ONE_OF was a better phrase
            //to follow this pattern I did the same for IN_OR_MISSING
            EQUALS_ONE_OF : createMultiValueFilterType("Equals One Of", null, "in", 'Equals One Of (example usage: a;b;c)', ";"),

            NOT_IN: createMultiValueFilterType("Does Not Equal Any Of", null, "notin", 'Does Not Equal Any Of (example usage: a;b;c)', ";"),
            EQUALS_NONE_OF: createMultiValueFilterType("Does Not Equal Any Of", null, "notin", 'Does Not Equal Any Of (example usage: a;b;c)', ";"),

            BETWEEN : createMultiValueFilterType("Between", null, "between", 'Between, Inclusive (example usage: -4,4)', ",", 2, 2),
            NOT_BETWEEN : createMultiValueFilterType("Not Between", null, "notbetween", 'Not Between, Exclusive (example usage: -4,4)', ",", 2, 2),

            MEMBER_OF : createSingleValueFilterType("Member Of", null, "memberof", 'Member Of'),

            //
            // These are the "no data value" operators
            //

            ISBLANK : createNoValueFilterType("Is Blank", null, "isblank", null),
            MISSING : createNoValueFilterType("Is Blank", null, "isblank", null),
            NONBLANK : createNoValueFilterType("Is Not Blank", null, "isnonblank", null),
            NOT_MISSING : createNoValueFilterType("Is Not Blank", null, "isnonblank", null),

            HAS_MISSING_VALUE : createNoValueFilterType("Has a missing value indicator", null, "hasmvvalue", null),
            DOES_NOT_HAVE_MISSING_VALUE : createNoValueFilterType("Does not have a missing value indicator", null, "nomvvalue", null),

            EXP_CHILD_OF : createSingleValueFilterType("Is Child Of", null, "exp:childof", " is child of" ),

            //
            // Table/Query-wise operators
            //
            Q : createTableFilterType("Search", null, "q", "Search across all columns")
        },

        /** @private create a js object suitable for Query.selectRows, etc */
        appendFilterParams : function (params, filterArray, dataRegionName)
        {
            dataRegionName = dataRegionName || "query";
            params = params || {};
            if (filterArray)
            {
                for (var i = 0; i < filterArray.length; i++)
                {
                    var filter = filterArray[i];
                    // 10.1 compatibility: treat ~eq=null as a NOOP (ref 10482)
                    if (filter.getFilterType().isDataValueRequired() && null == filter.getURLParameterValue())
                        continue;

                    // Create an array of filter values if there is more than one filter for the same column and filter type.
                    var paramName = filter.getURLParameterName(dataRegionName);
                    var paramValue = filter.getURLParameterValue();
                    if (params[paramName] !== undefined)
                    {
                        var values = params[paramName];
                        if (!LABKEY.Utils.isArray(values))
                            values = [ values ];
                        values.push(paramValue);
                        paramValue = values;
                    }
                    params[paramName] = paramValue;
                }
            }
            return params;
        },

        /** @private create a js object suitable for QueryWebPart, etc */
        appendAggregateParams : function (params, aggregateArray, dataRegionName)
        {
            dataRegionName = dataRegionName || "query";
            params = params || {};
            if (aggregateArray)
            {
                for (var idx = 0; idx < aggregateArray.length; ++idx)
                {
                    var aggregate = aggregateArray[idx];
                    var value = "type=" + aggregate.type;
                    if (aggregate.label)
                        value = value + "&label=" + aggregate.label;
                    if (aggregate.type && aggregate.column)
                    {
                        // Create an array of aggregate values if there is more than one aggregate for the same column.
                        var paramName = dataRegionName + '.analytics.' + aggregate.column;
                        var paramValue = encodeURIComponent(value);
                        if (params[paramName] !== undefined)
                        {
                            var values = params[paramName];
                            if (!LABKEY.Utils.isArray(values))
                                values = [ values ];
                            values.push(paramValue);
                            paramValue = values;
                        }
                        params[paramName] = paramValue;
                    }

                }
            }

            return params;
        },


        /**
        * Creates a filter
        * @param {String} columnName String name of the column to filter
        * @param value Value used as the filter criterion or an Array of values.
        * @param {LABKEY.Filter#Types} [filterType] Type of filter to apply to the 'column' using the 'value'
		* @example Example: <pre name="code" class="xml">
&lt;script type="text/javascript"&gt;
	function onFailure(errorInfo, options, responseObj)
	{
	    if(errorInfo && errorInfo.exception)
	        alert("Failure: " + errorInfo.exception);
	    else
	        alert("Failure: " + responseObj.statusText);
	}

	function onSuccess(data)
	{
	    alert("Success! " + data.rowCount + " rows returned.");
	}

	LABKEY.Query.selectRows({
		schemaName: 'lists',
		queryName: 'People',
		success: onSuccess,
		failure: onFailure,
		filterArray: [
			LABKEY.Filter.create('FirstName', 'Johnny'),
			LABKEY.Filter.create('Age', 15, LABKEY.Filter.Types.LESS_THAN_OR_EQUAL)
            LABKEY.Filter.create('LastName', ['A', 'B'], LABKEY.Filter.Types.DOES_NOT_START_WITH)
		]
    });
&lt;/script&gt; </pre>
        */

        create : function(columnName, value, filterType)
        {
            if (!filterType)
                filterType = this.Types.EQUAL;
            return getFilter(columnName, value, filterType);
        },

        /**
         * Not for public use. Can be changed or dropped at any time.
         * @param typeName
         * @param displayText
         * @param urlSuffix
         * @param isMultiType
         * @private
         */
        _define : function(typeName, displayText, urlSuffix, isMultiType) {
            if (!LABKEY.Filter.Types[typeName]) {
                if (isMultiType) {
                    LABKEY.Filter.Types[typeName] = createMultiValueFilterType(displayText, null, urlSuffix, null);
                }
                else {
                    LABKEY.Filter.Types[typeName] = createSingleValueFilterType(displayText, null, urlSuffix, null);
                }
            }
        },

        /**
         * Given an array of filter objects, return a new filterArray with old filters from a column removed and new filters for the column added
         * If new filters are null, simply remove all old filters from baseFilters that refer to this column
         * @param {Array} baseFilters  Array of existing filters created by {@link LABKEY.Filter.create}
         * @param {String} columnName  Column name of filters to replace
         * @param {Array} columnFilters Array of new filters created by {@link LABKEY.Filter.create}. Will replace any filters referring to columnName
         */
        merge : function(baseFilters, columnName, columnFilters)
        {
            var newFilters = [];
            if (null != baseFilters)
                for (var i = 0; i < baseFilters.length; i++)
                {
                    var filt = baseFilters[i];
                    if (filt.getColumnName() != columnName)
                        newFilters.push(filt);
                }

            return null == columnFilters ? newFilters : newFilters.concat(columnFilters);
        },

        /**
        * Convert from URL syntax filters to a human readable description, like "Is Greater Than 10 AND Is Less Than 100"
        * @param {String} url URL containing the filter parameters
        * @param {String} dataRegionName String name of the data region the column is a part of
        * @param {String} columnName String name of the column to filter
        * @return {String} human readable version of the filter
         */
        getFilterDescription : function(url, dataRegionName, columnName)
        {
            var params = LABKEY.ActionURL.getParameters(url);
            var result = "";
            var separator = "";
            for (var paramName in params)
            {
                // Look for parameters that have the right prefix
                if (paramName.indexOf(dataRegionName + "." + columnName + "~") == 0)
                {
                    var filterType = paramName.substring(paramName.indexOf("~") + 1);
                    var values = params[paramName];
                    if (!LABKEY.Utils.isArray(values))
                    {
                        values = [values];
                    }
                    // Get the human readable version, like "Is Less Than"
                    var friendly = urlMap[filterType];
                    var displayText;
                    if (!friendly)
                    {
                        displayText = filterType;
                    }
                    else
                    {
                        displayText = friendly.getDisplayText();
                    }

                    for (var j = 0; j < values.length; j++)
                    {
                        // If the same type of filter is applied twice, it will have multiple values
                        result += separator;
                        separator = " AND ";

                        result += displayText;
                        result += " ";
                        result += values[j];
                    }
                }
            }
            return result;
        },

        // Create an array of LABKEY.Filter objects from the filter parameters on the URL
        getFiltersFromUrl : function(url, dataRegionName)
        {
            dataRegionName = dataRegionName || 'query';
            var params = LABKEY.ActionURL.getParameters(url);
            var filterArray = [];

            for (var paramName in params)
            {
                if (params.hasOwnProperty(paramName)) {
                    // Look for parameters that have the right prefix
                    if (paramName.indexOf(dataRegionName + ".") == 0)
                    {
                        var tilde = paramName.indexOf("~");

                        if (tilde != -1)
                        {
                            var columnName = paramName.substring(dataRegionName.length + 1, tilde);
                            var filterName = paramName.substring(tilde + 1);
                            var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(filterName);
                            var values = params[paramName];
                            if (!LABKEY.Utils.isArray(values))
                            {
                                values = [values];
                            }
                            filterArray.push(LABKEY.Filter.create(columnName, values, filterType));
                        }
                    }
                }
            }
            return filterArray;
        },

        getSortFromUrl : function(url, dataRegionName)
        {
            dataRegionName = dataRegionName || 'query';

            var params = LABKEY.ActionURL.getParameters(url);
            return params[dataRegionName + "." + "sort"];
        },

        getQueryParamsFromUrl : function(url, dataRegionName)
        {
            dataRegionName = dataRegionName || 'query';

            var queryParams = {};
            var params = LABKEY.ActionURL.getParameters(url);
            for (var paramName in params)
            {
                if (params.hasOwnProperty(paramName))
                {
                    if (paramName.indexOf(dataRegionName + "." + "param.") == 0)
                    {
                        var queryParamName = paramName.substring((dataRegionName + "." + "param.").length);
                        queryParams[queryParamName] = params[paramName];
                    }
                }
            }

            return queryParams;
        },

        getFilterTypeForURLSuffix : function (urlSuffix)
        {
            return urlMap[urlSuffix];
        }
    };

    var ft = ret.Types;
    var filterTypes = {
        "int":[ft.HAS_ANY_VALUE, ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK, ft.GT, ft.LT, ft.GTE, ft.LTE, ft.IN, ft.NOT_IN, ft.BETWEEN, ft.NOT_BETWEEN],
        "string":[ft.HAS_ANY_VALUE, ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK, ft.GT, ft.LT, ft.GTE, ft.LTE, ft.CONTAINS, ft.DOES_NOT_CONTAIN, ft.DOES_NOT_START_WITH, ft.STARTS_WITH, ft.IN, ft.NOT_IN, ft.CONTAINS_ONE_OF, ft.CONTAINS_NONE_OF, ft.BETWEEN, ft.NOT_BETWEEN],
        "boolean":[ft.HAS_ANY_VALUE, ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK],
        "float":[ft.HAS_ANY_VALUE, ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK, ft.GT, ft.LT, ft.GTE, ft.LTE, ft.IN, ft.NOT_IN, ft.BETWEEN, ft.NOT_BETWEEN],
        "date":[ft.HAS_ANY_VALUE, ft.DATE_EQUAL, ft.DATE_NOT_EQUAL, ft.ISBLANK, ft.NONBLANK, ft.DATE_GREATER_THAN, ft.DATE_LESS_THAN, ft.DATE_GREATER_THAN_OR_EQUAL, ft.DATE_LESS_THAN_OR_EQUAL]
    };

    var defaultFilter = {
        "int": ft.EQUAL,
        "string": ft.CONTAINS,
        "boolean": ft.EQUAL,
        "float": ft.EQUAL,
        "date": ft.DATE_EQUAL
    };

    /** @private Returns an Array of filter types that can be used with the given json type ("int", "double", "string", "boolean", "date") */
    ret.getFilterTypesForType = function (type, mvEnabled)
    {
        var types = [];
        if (filterTypes[type])
            types = types.concat(filterTypes[type]);

        if (mvEnabled)
        {
            types.push(ft.HAS_MISSING_VALUE);
            types.push(ft.DOES_NOT_HAVE_MISSING_VALUE);
        }

        return types;
    };

    /** @private Return the default LABKEY.Filter.Type for a json type ("int", "double", "string", "boolean", "date"). */
    ret.getDefaultFilterForType = function (type)
    {
        if (defaultFilter[type])
            return defaultFilter[type];

        return ft.EQUAL;
    };

    return ret;
};

/**
* @name LABKEY.Filter.FilterDefinition
* @description Static class that defines the functions that describe how a particular
*            type of filter is identified and operates.  See {@link LABKEY.Filter}.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=filteringData">Filter via the LabKey UI</a></li>
 *              </ul>
 *           </p>
* @class  Static class that defines the functions that describe how a particular
*            type of filter is identified and operates.  See {@link LABKEY.Filter}.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=filteringData">Filter via the LabKey UI</a></li>
 *              </ul>
 *           </p>
*/

/**#@+
 * @methodOf LABKEY.Filter.FilterDefinition#
*/

/**
* Get the string displayed for this filter.
* @name getDisplayText
* @type String
*/

/**
* Get the more descriptive string displayed for this filter.  This is used in filter dialogs.
* @name getLongDisplayText
* @type String
*/

/**
* Get the URL suffix used to identify this filter.
* @name getURLSuffix
* @type String
*/

/**
* Get the Boolean that indicates whether a data value is required.
* @name isDataValueRequired
* @type Boolean
*/

/**
* Get the Boolean that indicates whether the filter supports a string with multiple filter values (ie. contains one of, not in, etc).
* @name isMultiValued
* @type Boolean
*/

/**
* Get the LABKEY.Filter.FilterDefinition the represents the opposite of this filter type.
* @name getOpposite
* @type LABKEY.Filter.FilterDefinition
*/

/**#@-*/
