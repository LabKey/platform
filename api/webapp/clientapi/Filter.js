/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 8.1
 * @license Copyright (c) 2008 LabKey Corporation
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
 * @description Filter static class to describe and create filters.
 * @namespace
 * @property {Object} Types Types static class to describe different types of filters.
 * @property {LABKEY.Filter.FilterDefinition} Types.EQUAL Filter with displayText = "Equals", urlSuffix = "eq" and isDataValueRequired = true.
 * @property {LABKEY.Filter.FilterDefinition} Types.DATE_EQUAL Filter with displayText = "Equals", urlSuffix = "dateeq" and isDataValueRequired = true.
 * @property {LABKEY.Filter.FilterDefinition} Types.DATE_NOT_EQUAL Filter with displayText = "Does Not Equal", urlSuffix = "dateneq" and isDataValueRequired = true.
 * @property {LABKEY.Filter.FilterDefinition} Types.NEQ_OR_NULL Filter with displayText = "Does Not Equal", urlSuffix = "neqornull" and isDataValueRequired = true.
 * @property {LABKEY.Filter.FilterDefinition} Types.NEQ Filter with displayText = "Does Not Equal", urlSuffix = "neq"  and isDataValueRequired = true.
 * @property {LABKEY.Filter.FilterDefinition} Types.ISBLANK Filter with displayText = "Is Blank", urlSuffix = "isblank", false),
 * @property {LABKEY.Filter.FilterDefinition} Types.NONBLANK Filter with displayText = "Is Not Blank", urlSuffix = "isnonblank", false),
 * @property {LABKEY.Filter.FilterDefinition} Types.GT Filter with displayText = "Is Greater Than", urlSuffix = "gt" and isDataValueRequired = true.
 * @property {LABKEY.Filter.FilterDefinition} Types.LT Filter with displayText = "Is Less Than", urlSuffix = "lt" and isDataValueRequired = true.
 * @property {LABKEY.Filter.FilterDefinition} Types.GTE Filter with displayText = "Is Greater Than or Equal To", urlSuffix = "gte" and isDataValueRequired = true.
 * @property {LABKEY.Filter.FilterDefinition} Types.LTE Filter with displayText = "Is Less Than or Equal To", urlSuffix = "lte" and isDataValueRequired = true.
 * @property {LABKEY.Filter.FilterDefinition} Types.CONTAINS Filter with displayText = "Contains", urlSuffix = "contains" and isDataValueRequired = true.
 * @property {LABKEY.Filter.FilterDefinition} Types.DOES_NOT_CONTAIN Filter with displayText = "Does Not Contain", urlSuffix = "doesnotcontain" and isDataValueRequired = true.
 * @property {LABKEY.Filter.FilterDefinition} Types.DOES_NOT_START_WITH Filter with displayText = "Does Not Start With", urlSuffix = "doesnotstartwith" and isDataValueRequired = true.
 * @property {LABKEY.Filter.FilterDefinition} Types.STARTS_WITH Filter with displayText = "Starts With", urlSuffix = "startswith" and isDataValueRequired = true.
 * @property {LABKEY.Filter.FilterDefinition} Types.IN Filter with displayText = "Equals One Of", urlSuffix = "in" and isDataValueRequired = true.
 *
 */
LABKEY.Filter = new function()
{
    function getFilterType(displayText, urlSuffix, dataValueRequired)
    {
        return {
            getDisplayText : function() { return displayText },
            getURLSuffix : function() { return urlSuffix },
            isDataValueRequired : function() { return dataValueRequired }
        };
    }

    function getFilter(columnName, value, filterType)
    {
        return {
            getColumnName: function() {return columnName;},
            getValue: function() {return value},
            getFilterType: function() {return filterType},
            getURLParameterName : function() { return "query." + columnName + "~" + filterType.getURLSuffix() },
            getURLParameterValue : function() { return filterType.isDataValueRequired() ? value : undefined }
        };
    }

    /** @scope LABKEY.Filter.prototype */
    return {

		Types : {

			EQUAL : getFilterType("Equals", "eq", true),
            DATE_EQUAL : getFilterType("Equals", "dateeq", true),
            DATE_NOT_EQUAL : getFilterType("Does Not Equal", "dateneq", true),
            NEQ_OR_NULL : getFilterType("Does Not Equal", "neqornull", true),
            NEQ : getFilterType("Does Not Equal", "neq", true),
            ISBLANK : getFilterType("Is Blank", "isblank", false),
            NONBLANK : getFilterType("Is Not Blank", "isnonblank", false),
            GT : getFilterType("Is Greater Than", "gt", true),
            LT : getFilterType("Is Less Than", "lt", true),
            GTE : getFilterType("Is Greater Than or Equal To", "gte", true),
            LTE : getFilterType("Is Less Than or Equal To", "lte", true),
            CONTAINS : getFilterType("Contains", "contains", true),
            DOES_NOT_CONTAIN : getFilterType("Does Not Contain", "doesnotcontain", true),
            DOES_NOT_START_WITH : getFilterType("Does Not Start With", "doesnotstartwith", true),
            STARTS_WITH : getFilterType("Starts With", "startswith", true),
            IN : getFilterType("Equals One Of", "in", true)
        },

        /**
        * Creates a filter
        * @param {String} columnName String name of the column to filter
        * @param value Value used as the filter criterion
        * @param {LABKEY.Filter#Types} [filterType] Type of filter to apply to the 'column' using the 'value'
		* @example Example: <pre name="code" class="xml">
&lt;script type="text/javascript"&gt;
	LABKEY.requiresClientAPI();
&lt;/script&gt;
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
		successCallback: onSuccess,
		failureCallback: onFailure,
		filterArray: [
			LABKEY.Filter.create('FirstName', 'Johnny'),
			LABKEY.Filter.create('Age', 15, LABKEY.Filter.Types.LTE)
		]
    });
&lt;/script&gt; </pre>
        */

        create : function(columnName, value, filterType)
        {
            if (!filterType)
                filterType = this.Types.EQUAL;
            return getFilter(columnName, value, filterType);
        }
    };
};

/**
* @namespace FilterDefinition static class to define the functions that describe how a particular
            type of filter is identified and operates.  See {@link LABKEY.Filter}.
*/ LABKEY.Filter.FilterDefinition = new function() {};

/**
* Get the Boolean that indicates whether a data value is required.
* @name getDisplayText
* @methodOf LABKEY.Filter.FilterDefinition
* @type String
*/

/**
* Get the string displayed for this filter.
* @name getURLSuffix
* @methodOf LABKEY.Filter.FilterDefinition
* @type String
*/

/**
* Get the ULR suffix used to identify this filter.
* @name isDataValueRequired
* @methodOf LABKEY.Filter.FilterDefinition
* @type Boolean
*/

