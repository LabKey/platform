/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2012 LabKey Corporation
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
 * @property {LABKEY.Filter.FilterDefinition} Types.IN Finds rows where the column value equals one of the supplied filter values. The values should be supplied as a semi-colon-delimited list (e.g., 'a;b;c').
 * @property {LABKEY.Filter.FilterDefinition} Types.NOT_IN Finds rows where the column value is not in any of the supplied filter values. The values should be supplied as a semi-colon-delimited list (e.g., 'a;b;c').
 * @property {LABKEY.Filter.FilterDefinition} Types.CONTAINS_ONE_OF Finds rows where the column value contains any of the supplied filter values. The values should be supplied as a semi-colon-delimited list (e.g., 'a;b;c').
 * @property {LABKEY.Filter.FilterDefinition} Types.CONTAINS_NONE_OF Finds rows where the column value does not contain any of the supplied filter values. The values should be supplied as a semi-colon-delimited list (e.g., 'a;b;c').
 *
 */
LABKEY.Filter = new function()
{
    function validateMultiple(type, value, colName)
    {
        var values = value.split(";");
        var result = '';
        var separator = '';
        for (var i = 0; i < values.length; i++)
        {
            var value = LABKEY.ext.FormHelper.validate(type, values[i].trim(), colName);
            if (value == undefined)
                return undefined;

            result = result + separator + value;
            separator = ";";
        }
        return result;
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
        containsoneof : 'containsnoneof',
        containsnoneof : 'containsoneof',
        hasmvvalue : 'nomvvalue',
        nomvvalue : 'hasmvvalue'
    };

    //NOTE: these maps contains the unambiguous pairings of single- and multi-valued filters
    //due to NULLs, one cannot easily convert neq to notin
    var multiValueToSingleMap = {
        'in' : 'eq',
        containsoneof : 'contains',
        containsnoneof : 'doesnotcontain'
    };

    var singleValueToMultiMap = {
        eq : 'in',
        neq : 'notin',
        neqornull: 'notin',
        doesnotcontain : 'containsnoneof',
        contains : 'containsoneof'
    };

    function createFilterType(displayText, urlSuffix, dataValueRequired, isMultiValued, longDisplayText)
    {
        var result = {
            getDisplayText : function() { return displayText },
            getLongDisplayText : function() { return longDisplayText || displayText },
            getURLSuffix : function() { return urlSuffix },
            isDataValueRequired : function() { return dataValueRequired },
            isMultiValued : function() { return isMultiValued },
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
                    return validateMultiple(type, value, colName);
                else
                    return LABKEY.ext.FormHelper.validate(type, value, colName);
            }
        };
        urlMap[urlSuffix] = result;
        return result;
    }

    function getFilter(columnName, value, filterType)
    {
        return {
            getColumnName: function() {return columnName;},
            getValue: function() {return value},
            getFilterType: function() {return filterType},
            getURLParameterName : function(dataRegionName) { return (dataRegionName || "query") + "." + columnName + "~" + filterType.getURLSuffix();},
            getURLParameterValue : function() { return filterType.isDataValueRequired() ? value : "" }
        };
    }

    var ret = /** @scope LABKEY.Filter */{

		Types : {

            HAS_ANY_VALUE : createFilterType("Has Any Value", "", false, null),
			EQUAL : createFilterType("Equals", "eq", true, null),
            DATE_EQUAL : createFilterType("Equals", "dateeq", true, null),
            DATE_NOT_EQUAL : createFilterType("Does Not Equal", "dateneq", true, null),
            NEQ_OR_NULL : createFilterType("Does Not Equal", "neqornull", true, null),
            NOT_EQUAL_OR_MISSING : createFilterType("Does Not Equal", "neqornull", true, null),
            NEQ : createFilterType("Does Not Equal", "neq", true, null),
            NOT_EQUAL : createFilterType("Does Not Equal", "neq", true, null),
            ISBLANK : createFilterType("Is Blank", "isblank", false, null),
            MISSING : createFilterType("Is Blank", "isblank", false, null),
            NONBLANK : createFilterType("Is Not Blank", "isnonblank", false, null),
            NOT_MISSING : createFilterType("Is Not Blank", "isnonblank", false, null),
            GT : createFilterType("Is Greater Than", "gt", true, null),
            GREATER_THAN : createFilterType("Is Greater Than", "gt", true, null),
            DATE_GREATER_THAN : createFilterType("Is Greater Than", "dategt", true, null),
            LT : createFilterType("Is Less Than", "lt", true, null),
            LESS_THAN : createFilterType("Is Less Than", "lt", true, null),
            DATE_LESS_THAN : createFilterType("Is Less Than", "datelt", true, null),
            GTE : createFilterType("Is Greater Than or Equal To", "gte", true, null),
            GREATER_THAN_OR_EQUAL : createFilterType("Is Greater Than or Equal To", "gte", true, null),
            DATE_GREATER_THAN_OR_EQUAL : createFilterType("Is Greater Than or Equal To", "dategte", true, null),
            LTE : createFilterType("Is Less Than or Equal To", "lte", true, null),
            LESS_THAN_OR_EQUAL : createFilterType("Is Less Than or Equal To", "lte", true, null),
            DATE_LESS_THAN_OR_EQUAL : createFilterType("Is Less Than or Equal To", "datelte", true, null),
            CONTAINS : createFilterType("Contains", "contains", true, null),
            DOES_NOT_CONTAIN : createFilterType("Does Not Contain", "doesnotcontain", true, null),
            DOES_NOT_START_WITH : createFilterType("Does Not Start With", "doesnotstartwith", true, null),
            STARTS_WITH : createFilterType("Starts With", "startswith", true, null),
            IN : createFilterType("Equals One Of", "in", true, true, 'Equals One Of (e.g. \"a;b;c\")'),
            //NOTE: for some reason IN is aliased as EQUALS_ONE_OF.  not sure if this is for legacy purposes or it was determined EQUALS_ONE_OF was a better phrase
            //to follow this pattern I did the same for IN_OR_MISSING
            EQUALS_ONE_OF : createFilterType("Equals One Of", "in", true, true, 'Equals One Of (e.g. \"a;b;c\")'),
            EQUALS_NONE_OF: createFilterType("Does Not Equal Any Of", "notin", true, true, 'Does Not Equal Any Of (e.g. \"a;b;c\")'),
            NOT_IN: createFilterType("Does Not Equal Any Of", "notin", true, true, 'Does Not Equal Any Of (e.g. \"a;b;c\")'),
            CONTAINS_ONE_OF : createFilterType("Contains One Of", "containsoneof", true, true, 'Contains One Of (e.g. \"a;b;c\")'),
            CONTAINS_NONE_OF : createFilterType("Does Not Contain Any Of", "containsnoneof", true, true, 'Does Not Contain Any Of (e.g. \"a;b;c\")'),
            HAS_MISSING_VALUE : createFilterType("Has a missing value indicator", "hasmvvalue", false, null),
            DOES_NOT_HAVE_MISSING_VALUE : createFilterType("Does not have a missing value indicator", "nomvvalue", false, null)
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
                    var currentValue = params[paramName];
                    if (currentValue === undefined)
                    {
                        currentValue = paramValue;
                    }
                    else
                    {
                        if (LABKEY.ExtAdapter.isArray(currentValue))
                            currentValue.push(paramValue);
                        else
                            currentValue = [ currentValue, paramValue ];
                    }
                    params[paramName] = currentValue;
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
                        params[dataRegionName + '.agg.' + aggregate.column] = encodeURIComponent(value);
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
                    if (!LABKEY.ExtAdapter.isArray(values))
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
                        if (!LABKEY.ExtAdapter.isArray(values))
                        {
                            values = [values];
                        }
                        filterArray.push(LABKEY.Filter.create(columnName, values, filterType));
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

        getFilterTypeForURLSuffix : function (urlSuffix)
        {
            return urlMap[urlSuffix];
        }
    };

    var ft = ret.Types;
    var filterTypes = {
        "int":[ft.HAS_ANY_VALUE, ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK, ft.GT, ft.LT, ft.GTE, ft.LTE, ft.IN, ft.NOT_IN],
        "string":[ft.HAS_ANY_VALUE, ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK, ft.GT, ft.LT, ft.GTE, ft.LTE, ft.CONTAINS, ft.DOES_NOT_CONTAIN, ft.DOES_NOT_START_WITH, ft.STARTS_WITH, ft.IN, ft.NOT_IN, ft.CONTAINS_ONE_OF, ft.CONTAINS_NONE_OF],
        "boolean":[ft.HAS_ANY_VALUE, ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK],
        "float":[ft.HAS_ANY_VALUE, ft.EQUAL, ft.NEQ_OR_NULL, ft.ISBLANK, ft.NONBLANK, ft.GT, ft.LT, ft.GTE, ft.LTE, ft.IN, ft.NOT_IN],
        "date":[ft.HAS_ANY_VALUE, ft.DATE_EQUAL, ft.DATE_NOT_EQUAL, ft.ISBLANK, ft.NONBLANK, ft.DATE_GREATER_THAN, ft.DATE_LESS_THAN, ft.DATE_GREATER_THAN_OR_EQUAL, ft.DATE_LESS_THAN_OR_EQUAL, ft.IN, ft.NOT_IN]
    };

    var defaultFilter = {
        "int": ft.EQUAL,
        "string": ft.STARTS_WITH,
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
