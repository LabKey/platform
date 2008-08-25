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
 * @namespace Query static class to programmatically retrieve, insert, update and 
 *		delete data from LabKey public queries. <p/>
 *		{@link LABKEY.Query#selectRows} works for all LabKey public queries.  However,
 *		{@link LABKEY.Query#updateRows}, {@link LABKEY.Query#insertRows} and
 *		{@link LABKEY.Query#deleteRows} are only available for lists, study datasets, or
 *		tables in a user-defined schema.
 *		These three methods may not be used to operate on rows returned by queries to other LabKey 
 *		module schemas (e.g., ms1, ms2, flow, etc). To update, insert or delete data returned by 
 * 		queries to these types of schemas, use the methods for their respective classes, 
 *		such as the methods defined by {@link LABKEY.Assay} for assays.
 *
 */
LABKEY.Query = new function()
{
    function sendJsonQueryRequest(config)
    {
        if(config.timeout)
            Ext.Ajax.timeout = config.timeout;
        
        var dataObject = {
            schemaName : config.schemaName,
            queryName : config.queryName,
            rows : config.rowDataArray
        };

        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("query", config.action, config.containerPath),
            method : 'POST',
            success: getCallbackWrapper(config.successCallback),
            failure: getCallbackWrapper(config.errorCallback),
            jsonData : dataObject,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    function getCallbackWrapper(callbackFn, stripHiddenCols)
    {
        return function(response, options)
        {
            var data = null;

            //The response object is not actually the XMLHttpRequest object
            //it's a 'synthesized' object provided by Ext to help on IE 6
            //http://extjs.com/forum/showthread.php?t=27190&highlight=getResponseHeader
            var contentType = response.getResponseHeader['Content-Type'];

            if(contentType && contentType.indexOf('application/json') >= 0)
                data = Ext.util.JSON.decode(response.responseText)

            if(data && data.rows && stripHiddenCols)
                stripHiddenColData(data);

            callbackFn(data, options, response);
        }
    }

    function stripHiddenColData(data)
    {
        //gather the set of hidden columns
        var hiddenCols = [];
        var newColModel = [];
        var newMetaFields = [];
        var colModel = data.columnModel;
        for(var idx = 0; idx < colModel.length; ++idx)
        {
            if(colModel[idx].hidden)
                hiddenCols.push(colModel[idx].dataIndex);
            else
            {
                newColModel.push(colModel[idx]);
                newMetaFields.push(data.metaData.fields[idx]);
            }
        }

        //reset the columnModel and metaData.fields to include only the non-hidden items
        data.columnModel = newColModel;
        data.metaData.fields = newMetaFields;

        //delete column values for any columns in the hiddenCols array
        var row;
        for(idx = 0; idx < data.rows.length; ++idx)
        {
            row = data.rows[idx];
            for(idxHidden = 0; idxHidden < hiddenCols.length; ++idxHidden)
            {
                delete row[hiddenCols[idxHidden]];
                delete row[LABKEY.Query.URL_COLUMN_PREFIX + hiddenCols[idxHidden]];
            }
        }
    }

    function configFromArgs(args)
    {
        return {
            schemaName: args[0],
            queryName: args[1],
            rowDataArray: args[2],
            successCallback: args[3],
            errorCallback: args[4]
        }
    }

    // public methods:
    /** @scope LABKEY.Query.prototype */
    return {

        /**
         * Execute arbitrary LabKey SQL. For more information on LabKey SQL, see
         * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeySql">
         * https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeySql</a>.
         * @param config An object which contains the following configuration properties.
         * @param {String} config.schemaName name of the schema to query.
         * @param {String} config.sql The LabKey SQL to execute.
         * @param {String} [config.containerPath] The path to the container in which the schema and query are defined,
         *       if different than the current container. If not supplied, the current container's path will be used.
         * @param {Function} config.successCallback
				Function called when the "selectRows" function executes successfully. Will be called with arguments:
				{@link LABKEY.Query.SelectRowsResults} and (optionally) {@link LABKEY.Query.SelectRowsOptions}
         * @param {Function} [config.errorCallback] Function called when execution of the "selectRows" function fails.
         * @param {Integer} [config.maxRows] The maximum number of rows to return from the server (defaults to 100).
         *        If you want to return all possible rows, set this config property to -1.
         * @param {Integer} [config.offset] The index of the first row to return from the server (defaults to 0).
         *        Use this along with the maxRows config property to request pages of data.
         * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
         *       generating a timeout error (defaults to 30000).
         */
        executeSql : function(config)
        {
            if(config.timeout)
                Ext.Ajax.timeout = config.timeout;
            
            var dataObject = {
                schemaName: config.schemaName,
                sql: config.sql
            }

            //set optional parameters
            if(config.maxRows && config.maxRows >= 0)
                dataObject.maxRows = config.maxRows;
            if(config.offset && config.offset > 0)
                dataObject.offset = config.offset;

            Ext.Ajax.request({
                url : LABKEY.ActionURL.buildURL("query", "executeSql", config.containerPath),
                method : 'POST',
                success: getCallbackWrapper(config.successCallback, config.stripHiddenColumns),
                failure: getCallbackWrapper(config.errorCallback),
                jsonData : dataObject,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
        * Select rows.
        * @param {Object} config An object which contains the following configuration properties.
        * @param {String} config.schemaName Name of a schema defined within the current container. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} config.queryName Name of a query table associated with the chosen schema. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {Function} config.successCallback
				Function called when the "selectRows" function executes successfully. Will be called with arguments:
				{@link LABKEY.Query.SelectRowsResults} and (optionally) {@link LABKEY.Query.SelectRowsOptions}
        * @param {Function} [config.errorCallback] Function called when execution of the "selectRows" function fails.
        * @param {Array} [config.filterArray] Array of objects created by {@link LABKEY.Filter#create}.
        * @param {String} [config.sort]  String description of the sort.  It includes the column names
        *       listed in the URL of a sorted data region (with an optional minus prefix to indicate
        *       descending order). In the case of a multi-column sort, up to three column names can be
        *       included, separated by commas.
        * @param {String} [config.viewName] The name of a custom view saved on the server for the specified query.
        * @param {String} [config.columns] A comma-delimited list of column names you wish to select from the specified
        *       query. By default, selectRows will return the set of columns defined in the default value for this query,
        *       as defined via the Customize View user interface on the server. You can override this by specifying a list
        *       of column names in this parameter, separated by commas. The names can also include references to related
        *       tables (e.g., 'RelatedPeptide/Peptide' where 'RelatedPeptide is the name of a foreign key column in the
        *       base query, and 'Peptide' is the name of a column in the related table).
        * @param {String} [config.containerPath] The path to the container in which the schema and query are defined,
        *       if different than the current container. If not supplied, the current container's path will be used.
        * @param {Integer} [config.maxRows] The maximum number of rows to return from the server (defaults to 100).
        *        If you want to return all possible rows, set this config property to -1.
        * @param {Integer} [config.offset] The index of the first row to return from the server (defaults to 0).
        *        Use this along with the maxRows config property to request pages of data.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        * @example Example: <pre name="code" class="xml">
&lt;script type="text/javascript"&gt;
	LABKEY.requiresClientAPI();
&lt;/script&gt;
&lt;script type="text/javascript"&gt;
	function failureHandler(responseObj) 
	{ 
	    alert("Failure: " + responseObj.exception); 
	} 

	function successHandler(responseObj) 
	{ 
	    alert("Success! " + responseObj.rowCount + " rows returned."); 
	} 

	LABKEY.Query.selectRows({schemaName: 'lists', queryName: 'People',
	         successCallback: successHandler, errorCallback: failureHandler,
			filterArray: [
			    LABKEY.Filter.create('FirstName', 'Johny')
			    ] });
&lt;/script&gt; </pre>
		* @see LABKEY.Query.SelectRowsOptions
		* @see LABKEY.Query.SelectRowsResults
		*/
        selectRows : function(config)
        {
            //check for old-style separate arguments
            if(arguments.length > 1)
            {
                config = {
                    schemaName: arguments[0],
                    queryName: arguments[1],
                    successCallback: arguments[2],
                    errorCallback: arguments[3],
                    filterArray: arguments[4],
                    sort: arguments[5],
                    viewName: arguments[6]
                };
            }

            if(!config.schemaName)
                throw "You must specify a schemaName!";
            if(!config.queryName)
                throw "You must specify a queryName!";

            var dataObject = {};
            dataObject['query.queryName'] = config.queryName;
            dataObject['schemaName'] = config.schemaName;
            if (config.sort)
                dataObject['query.sort'] = config.sort;
            if(config.offset)
                dataObject['query.offset'] = config.offset;
            if(config.maxRows)
            {
                if(config.maxRows < 0)
                    dataObject['query.showAllRows'] = true;
                else
                    dataObject['query.maxRows'] = config.maxRows;
            }

            if (config.filterArray)
            {
                for (var i = 0; i < config.filterArray.length; i++)
                {
                    var filter = config.filterArray[i];
                    dataObject[filter.getURLParameterName()] = filter.getURLParameterValue();
                }
            }

            if(config.viewName)
                dataObject['query.viewName'] = config.viewName;

            if(config.columns)
                dataObject['query.columns'] = config.columns;

            if(config.timeout)
                Ext.Ajax.timeout = config.timeout;

            Ext.Ajax.request({
                url : LABKEY.ActionURL.buildURL('query', 'getQuery', config.containerPath),
                method : 'GET',
                success: getCallbackWrapper(config.successCallback, config.stripHiddenColumns),
                failure: getCallbackWrapper(config.errorCallback),
                params : dataObject
            });
        },

        /**
        * Update rows.
        * @param {Object} config An object which contains the following configuration properties.
        * @param {String} config.schemaName Name of a schema defined within the current container. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} config.queryName Name of a query table associated with the chosen schema.  See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {Function} config.successCallback Function called when the "updateRows" function executes successfully.
						Will be called with arguments: {@link LABKEY.Query.ModifyRowsResults} and (optionally)
						{@link LABKEY.Query.ModifyRowsOptions}.
        * @param {Function} [config.errorCallback] Function called when execution of the "updateRows" function fails.
        * @param {Array} config.rowDataArray Array of record objects in which each object has a property for each field.
        *               The row array must include the primary key column values and values for
        *               other columns you wish to update.
        * @param {String} [config.containerPath] The container path in which the schema and query name are defined.
         *              If not supplied, the current container path will be used.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
		* @see LABKEY.Query.ModifyRowsResults
		* @see LABKEY.Query.ModifyRowsOptions
        */
        updateRows : function(config)
        {
            if(arguments.length > 1)
                config = configFromArgs(arguments);
            config.action = "updateRows";
            sendJsonQueryRequest(config);
        },

        /**
        * Insert rows.
        * @param {Object} config An object which contains the following configuration properties.
        * @param {String} config.schemaName Name of a schema defined within the current container.  See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} config.queryName Name of a query table associated with the chosen schema. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {Function} config.successCallback Function called when the "insertRows" function executes successfully.
						Will be called with arguments: {@link LABKEY.Query.ModifyRowsResults} and (optionally)
						{@link LABKEY.Query.ModifyRowsOptions}.
		* @param {Function} [config.errorCallback]  Function called when execution of the "insertRows" function fails.
        * @param {Array} config.rowDataArray Array of record objects in which each object has a property for each field.
        *                  The row data array must include all column values except for the primary key column.
        *                  However, you will need to include the primary key column values if you defined
        *                  them yourself instead of relying on auto-number.
        * @param {String} [config.containerPath] The container path in which the schema and query name are defined.
         *              If not supplied, the current container path will be used.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
		* @see LABKEY.Query.ModifyRowsResults
		* @see LABKEY.Query.ModifyRowsOptions
        */
        insertRows : function(config)
        {
            if(arguments.length > 1)
                config = configFromArgs(arguments);
            config.action = "insertRows";
            sendJsonQueryRequest(config);
        },

        /**
        * Delete rows.
        * @param {Object} config An object which contains the following configuration properties.
        * @param {String} config.schemaName Name of a schema defined within the current container. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} config.queryName Name of a query table associated with the chosen schema. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {Function} config.successCallback Function called when the "deleteRows" function executes successfully.
        			Will be called with arguments: {@link LABKEY.Query.ModifyRowsResults} and (optionally)
				    {@link LABKEY.Query.ModifyRowsOptions}.
		* @param {Function} [config.errorCallback] Function called when execution of the "deleteRows" function fails.
        * @param {Array} config.rowDataArray Array of record objects in which each object has a property for each field.
        *                  The row data array needs to include only the primary key column value, not all columns.
        * @param {String} [config.containerPath] The container path in which the schema and query name are defined.
         *              If not supplied, the current container path will be used.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
		* @see LABKEY.Query.ModifyRowsResults
		* @see LABKEY.Query.ModifyRowsOptions
        */
        deleteRows : function(config)
        {
            if(arguments.length > 1)
                config = configFromArgs(arguments);
            config.action = "deleteRows";
            sendJsonQueryRequest(config);
        },

        /**
        * Build and return an object suitable for passing to the
        * <a href = "http://extjs.com/deploy/dev/docs/?class=Ext.Ajax">Ext.Ajax</a> 'params' configuration property.
        * @param {String} schemaName Name of a schema defined within the current container.  See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} queryName Name of a query table associated with the chosen schema.   See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {Array} [filterArray] Array of objects created by {@link LABKEY.Filter#create}.
        * @param {String} [sort]  String description of the sort.  It includes the column names
        *       listed in the URL of a sorted data region (with an optional minus prefix to indicate
        *       descending order). In the case of a multi-column sort, up to three column names can be
        *       included, separated by commas.
        * @returns {Object} Object suitable for passing to the
        * <a href = "http://extjs.com/deploy/dev/docs/?class=Ext.Ajax">Ext.Ajax</a> 'params' configuration property.
        */

        buildQueryParams: function(schemaName, queryName, filterArray, sort)
        {
            var params = {};
            params['query.queryName'] = queryName;
            params['schemaName'] = schemaName;
            if (sort)
                params['query.sort'] = sort;

            if (filterArray)
            {
                for (var i = 0; i < filterArray.length; i++)
                {
                    var filter = filterArray[i];
                    params[filter.getURLParameterName()] = filter.getURLParameterValue();
                }
            }

            return params;
        },

        URL_COLUMN_PREFIX: "_labkeyurl_"
    }
};

/**
* @namespace
* @description SelectRowsResults static class to describe the first
            object passed to the successCallback function by
            {@link LABKEY.Query#selectRows}. This object's properties are useful for
            matching requests to responses, as HTTP requests are typically
            processed asynchronously.
* @property {Object} metaData Contains type and lookup information about the
            columns in the resultset.
 * @property {String} metaData.root	Name of the property containing rows
            ("rows"). This is mainly for the Ext grid component.
* @property {String} metaData.totalProperty	Name of the top-level property
            containing the row count ("rowCount") in our case. This is mainly
            for the Ext grid component.
* @property {Object} metaData.sortInfo	Sort specification in Ext grid terms.
            This contains two sub-properties, field and direction, which indicate
            the sort field and direction ("ASC" or "DESC") respectively.
* @property {String} metaData.id	Name of the primary key column.
* @property {Object[]} metaData.fields	Array of field information.
* @property {Object[]} metaData.fields	Array of field information.
            Each field has the following properties:
            <ul><li>name -- The name of the field</li>
            <li>type -- JavaScript type name of the field</li>
            <li>lookup -- If the field is a lookup, there will
                be three sub-properties listed under this property:
                schema, table, and column, which describe the schema, table, and
                display column of the lookup table (query).</li></ul>
* @property {String} columnModel Contains information about how one may interact
            with the columns within a user interface. This format is generated
            to match the requirements of the Ext grid component. See
            <a href="http://extjs.com/deploy/dev/docs/?class=Ext.grid.ColumnModel">
            Ext.grid.ColumnModel</a> for further information.
* @property {Object[]} rows An array of rows, each of which is a
            sub-element/object containing a property per column.
* @property {Integer} rowCount Indicates the number of total rows that could be
            returned by the query, which may be more than the number of objects
            in the rows array if the client supplied a value for the query.maxRows
            or query.offset parameters. This value is useful for clients that wish
            to display paging UI, such as the Ext grid.
* @see LABKEY.Query#selectRows
*/ LABKEY.Query.SelectRowsResults = new function() {};

/**
* @namespace
* @description SelectRowsOptions static class to describe
            the second object passed to the successCallback function by
            {@link LABKEY.Query#selectRows}.  This object's properties are useful for
            matching requests to responses, as HTTP requests are typically
            processed asynchronously.
* @property {String} schemaName Contains the same schemaName the client passed to the
            calling function. See <a class="link"
            href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
            How To Find schemaName, queryName &amp; viewName</a>.
* @property {String} query.queryName Contains the same queryName the client passed
            to the calling function. See
            <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
            How To Find schemaName, queryName &amp; viewName</a>.
* @property {String} query.viewName	Name of a valid custom view for the chosen queryName. See
            <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
            How To Find schemaName, queryName &amp; viewName</a>.
* @property {Integer} query.offset Row number at which results should begin.
            Use this with maxRows to get pages of results.
* @property {String} query.sort	Sort specification. This can be a comma-delimited list of
            column names, where each column may have an optional dash (-) before the name
            to indicate a descending sort.
* @property {Integer} maxRows Maximum number of rows to return
* @property {String} filters &lt;column-name&gt;~&lt;oper&gt;=&lt;value&gt;
            Filter specifications, one for each filter you supplied. All filters are combined
            using AND logic. The list of valid operators:
            <ul><li>eq = equals</li>
            <li>neq = not equals</li>
            <li>gt = greater-than</li>
            <li>gte = greater-than or equal-to</li>
            <li>lt = less-than</li>
            <li>lte = less-than or equal-to</li>
            <li>dateeq = date equal</li>
            <li>dateneq = date not equal</li>
            <li>neqornull = not equal or null</li>
            <li>isblank = is null</li>
            <li>isnonblank = is not null</li>
            <li>contains = contains</li>
            <li>doesnotcontain = does not contain</li>
            <li>startswith = starts with</li>
            <li>doesnotstartwith = does not start with</li></ul>
* @property {Bool} [lookups="true"]	If 'true' (as by default), the {@link LABKEY.Query.SelectRowsResult}
            for {@link LABKEY.Query#selectRows} will contain the
            foreign key value for lookup columns and include lookup information
            (schema and table) for this column in its metaData property. If 'false,'
            the display value will be for lookup columns instead of the
            foreign key value, and no lookup information will be supplied to the SelectRowsResult.
* @see LABKEY.Query#selectRows
*/ LABKEY.Query.SelectRowsOptions = new function() {};

/**
* @namespace
* @description ModifyRowsResults static class to describe
            the first object passed to the successCallback function
            by {@link LABKEY.Query#updateRows}, {@link LABKEY.Query#insertRows} or
            {@link LABKEY.Query#deleteRows}. This object's properties are useful for
            matching requests to responses, as HTTP requests are typically
            processed asynchronously.
* @property {String} schemaName Contains the same schemaName the client passed to the calling function.
* @property {String} queryName Contains the same queryName the client passed to the calling function.
* @property {String} command Will be "update", "insert", or "delete" depending on the API called.
* @property {Integer} rowsAffected Indicates the number of rows affected by the API action.
            This will typically be the same number of rows passed in to the calling function.
* @property {Object[]} rows Array of rows with field values for the rows updated, inserted,
            or deleted, in the same order as the rows supplied in the request. For insert, the
            new key value for an auto-increment key will be in the returned row's field values.
            For insert or update, the other field values may also be different than those supplied
            as a result of database default expressions, triggers, or LabKey's automatic tracking
            feature, which automatically adjusts columns of certain names (e.g., Created, CreatedBy,
            Modified, ModifiedBy, etc.).
* @example For example:
<pre name="code" class="xml">
{  "schemaName": "lists",
   "queryName": "API Test List"
   "rowsAffected": 1,
   "command": "insert",
   "keys": [3],
} </pre></code>
* @see LABKEY.Query#updateRows
* @see LABKEY.Query#insertRows
* @see LABKEY.Query#deleteRows
*/ LABKEY.Query.ModifyRowsResults = new function() {};

/**
* @namespace
* @description ModifyRowsOptions static class to describe
            the second object passed to the successCallback function
            by {@link LABKEY.Query#updateRows}, {@link LABKEY.Query#insertRows} or
            {@link LABKEY.Query#deleteRows}. This object's properties are useful for
            matching requests to responses, as HTTP requests are typically
            processed asynchronously.
* @property {String} schemaName Contains the same schemaName the client passed to the calling function.
* @property {String} queryName Contains the same queryName the client passed to the calling function.
* @property {Object[]} rows Array of row objects that map the names of the row fields to their values.
            The fields required for inclusion for each row depend on the which LABKEY.Query method you are
            using (updateRows, insertRows or deleteRows).
            <p/>
            <b>For {@link LABKEY.Query#updateRows}:</b> <p/>
            For the 'updateRows' method, each row in the rows array must include its primary key value
            as one of its fields.
            <p/>
            An example of a ModifyRowsOptions object for the 'updateRows' successCallback:
<pre name="code" class="xml">
{"schemaName": "lists",
 "queryName": "API Test List",
 "rows": [
    {"Key": 1,
    "FirstName": "Z",
    "Age": "100"}]
} </pre></code>

            <p/>
            <b>For {@link LABKEY.Query#insertRows}:</b> <p/>
            For the 'insertRows' method, the fields of the rows should look the same as
            they do for the 'updateRows' method, except that primary key values for new rows
            need not be supplied if the primary key columns are auto-increment.
            <p/>
            An example of a ModifyRowsOptions object for the 'insertRows' successCallback:
<pre name="code" class="xml">
{"schemaName": "lists",
 "queryName": "API Test List",
 "rows": [
     {"FirstName": "C",
    "Age": "30"}]
} </pre></code>

            <p/>
            <b>For {@link LABKEY.Query#deleteRows}:</b> <p/>
            For the 'deleteRows' method, the fields of the rows should look the
            same as they do for the 'updateRows' method, except that the 'deleteRows'
            method needs to supply only the primary key values for the rows. All
            other row data will be ignored.
            <p/>
            An example of a ModifyRowsOptions object for the 'deleteRows' successCallback:
<pre name="code" class="xml">
{"schemaName": "lists",
 "queryName": "API Test List",
 "rows": [
       {"Key": 3}]
} </pre></code>
* @see LABKEY.Query#updateRows
* @see LABKEY.Query#insertRows
* @see LABKEY.Query#deleteRows
*/ LABKEY.Query.ModifyRowsOptions = new function() {};

