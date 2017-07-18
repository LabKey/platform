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
 * @namespace Query static class to programmatically retrieve, insert, update and
 *		delete data from LabKey public queries. <p/>
 *		{@link LABKEY.Query.selectRows} works for all LabKey public queries.  However,
 *		{@link LABKEY.Query.updateRows}, {@link LABKEY.Query.insertRows} and
 *		{@link LABKEY.Query.deleteRows} are not available for all tables and queries.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeySql">
 *                      LabKey SQL Reference</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
 *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
 *              </ul>
 *           </p>
 */
LABKEY.Query = new function()
{
    function sendJsonQueryRequest(config)
    {
        var dataObject = {
            schemaName : config.schemaName,
            queryName : config.queryName,
            rows : config.rows || config.rowDataArray,
            transacted : config.transacted,
            extraContext : config.extraContext
        };

        var requestConfig = {
            url : LABKEY.ActionURL.buildURL("query", config.action, config.containerPath),
            method : 'POST',
            success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
            failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
            jsonData : dataObject,
            headers : {
                'Content-Type' : 'application/json'
            }
        };

        if (LABKEY.Utils.isDefined(config.timeout))
            requestConfig.timeout = config.timeout;

        return LABKEY.Ajax.request(requestConfig);
    }

    function getSuccessCallbackWrapper(callbackFn, stripHiddenCols, scope, requiredVersion)
    {
        if (requiredVersion && (requiredVersion === 13.2 || requiredVersion === "13.2" || requiredVersion === 16.2 || requiredVersion === 17.1)) {
            return LABKEY.Utils.getCallbackWrapper(function(data, response, options){
                if (data && callbackFn)
                    callbackFn.call(scope || this, new LABKEY.Query.Response(data), response, options);
            }, this);
        }

        return LABKEY.Utils.getCallbackWrapper(function(data, response, options){
            if (data && data.rows && stripHiddenCols)
                stripHiddenColData(data);
            if (callbackFn)
                callbackFn.call(scope || this, data, options, response);
        }, this);
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
            if (colModel[idx].hidden)
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
            for(var idxHidden = 0; idxHidden < hiddenCols.length; ++idxHidden)
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
            rows: args[2],
            successCallback: args[3],
            errorCallback: args[4]
        };
    }

    function getMethod(value)
    {
        if (value && (value.toUpperCase() === 'GET' || value.toUpperCase() === 'POST'))
            return value.toUpperCase();
        return 'GET';
    }

    // public methods:
    /** @scope LABKEY.Query */
    return {

        /**
         * An enumeration of the various container filters available. Note that not all
         * data types and queries can contain that spans multiple containers. In those cases,
         * all values will behave the same as current and show only data in the current container. 
         * The options are as follows:
         * <ul>
         * <li><b>current:</b> Include the current folder only</li>
         * <li><b>currentAndFirstChildren:</b> Include the current folder and all first children, excluding workbooks</li>
         * <li><b>currentAndSubfolders:</b> Include the current folder and all subfolders</li>
         * <li><b>currentPlusProject:</b> Include the current folder and the project that contains it</li>
         * <li><b>currentAndParents:</b> Include the current folder and its parent folders</li>
         * <li><b>currentPlusProjectAndShared:</b> Include the current folder plus its project plus any shared folders</li>
         * <li><b>allFolders:</b> Include all folders for which the user has read permission</li>
         * </ul>
         */
        containerFilter : {
            current: "Current",
            currentAndFirstChildren: "CurrentAndFirstChildren",
            currentAndSubfolders: "CurrentAndSubfolders",
            currentPlusProject: "CurrentPlusProject",
            currentAndParents: "CurrentAndParents",
            currentPlusProjectAndShared: "CurrentPlusProjectAndShared",
            allFolders: "AllFolders"
        },


        /**
         * Execute arbitrary LabKey SQL. For more information, see the
         * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeySql">
         * LabKey SQL Reference</a>.
         * @param config An object which contains the following configuration properties.
         * @param {String} config.schemaName name of the schema to query.
         * @param {String} config.sql The LabKey SQL to execute.
         * @param {String} [config.containerPath] The path to the container in which the schema and query are defined,
         *       if different than the current container. If not supplied, the current container's path will be used.
         * @param {String} [config.containerFilter] One of the values of {@link LABKEY.Query.containerFilter} that sets
         *       the scope of this query. Defaults to containerFilter.current, and is interpreted relative to
         *       config.containerPath.
         * @param {Function} config.success
                 Function called when the "selectRows" function executes successfully.
                 This function will be called with the following arguments:
                 <ul>
                     <li>
                         <b>data:</b> If config.requiredVersion is not set, or set to "8.3", the success handler will be
                         passed a {@link LABKEY.Query.SelectRowsResults} object. If set to "9.1" the success handler will
                         be passed a {@link LABKEY.Query.ExtendedSelectRowsResults} object. If requiredVersion is greater than 13.2 the success
                         handler will be passed a {@link LABKEY.Query.Response} object.
                     </li>
                     <li><b>responseObj:</b> The XMLHttpResponseObject instance used to make the AJAX request</li>
                     <li><b>options:</b> The options used for the AJAX request</li>
                 </ul>
         * @param {Function} [config.failure] Function called when execution of the "executeSql" function fails.
         *                   See {@link LABKEY.Query.selectRows} for more information on the parameters passed to this function.
         * @param {Integer} [config.maxRows] The maximum number of rows to return from the server (defaults to returning all rows).
         * @param {Integer} [config.offset] The index of the first row to return from the server (defaults to 0).
         *        Use this along with the maxRows config property to request pages of data.
         * @param {Boolean} [config.includeTotalCount] Include the total number of rows available (defaults to true).
         *       If false totalCount will equal number of rows returned (equal to maxRows unless maxRows == 0).
         * @param {String} [config.sort] A sort specification to apply over the rows returned by the SQL. In general,
         * you should either include an ORDER BY clause in your SQL, or specific a sort specification in this config property,
         * but not both. The value of this property should be a comma-delimited list of column names you want to sort by. Use
         * a - prefix to sort a column in descending order (e.g., 'LastName,-Age' to sort first by LastName, then by Age descending).
         * @param {Boolean} [config.saveInSession] Whether or not the definition of this query should be stored for reuse during the current session.
         * If true, all information required to recreate the query will be stored on the server and a unique query name will be passed to the
         * success callback.  This temporary query name can be used by all other API methods, including Query Web Part creation, for as long
         * as the current user's session remains active.
         * @param {Boolean} [config.includeDetailsColumn] Include the Details link column in the set of columns (defaults to false).
         *       If included, the column will have the name "~~Details~~". The underlying table/query must support details links
         *       or the column will be omitted in the response.
         * @param {Object} [config.parameters] Map of name (string)/value pairs for the values of parameters if the SQL
         *        references underlying queries that are parameterized. For example, the following passes two parameters to the query: {'Gender': 'M', 'CD4': '400'}.
         *        The parameters are written to the request URL as follows: query.param.Gender=M&query.param.CD4=400.  For details on parameterized SQL queries, see
         *        <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=paramsql">Parameterized SQL Queries</a>.
         * @param {Double} [config.requiredVersion] If not set, or set to "8.3", the success handler will be passed a {@link LABKEY.Query.SelectRowsResults}
         *       object. If set to "9.1" the success handler will be passed a {@link LABKEY.Query.ExtendedSelectRowsResults}
         *       object. If greater than 13.2 the success handler will be passed a {@link LABKEY.Query.Response} object.
         *       The main difference between SelectRowsResults and ExtendedSelectRowsResults is that each column in each row
         *       will be another object (not just a scalar value) with a "value" property as well as other related properties
         *       (url, mvValue, mvIndicator, etc.). In the LABKEY.Query.Response format each row will be an instance of
         *       {@link LABKEY.Query.Row}.
         *       In the "16.2" format, multi-value columns will be returned as an array of values, each of which may have a value, displayValue, and url.
         *       In the "17.1" format, "formattedValue" may be included in the response as the display column's value formatted with the display column's format or folder format settings.
         * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
         *       generating a timeout error (defaults to 30000).
         * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         * @example Example, from the Reagent Request Confirmation <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=reagentRequestConfirmation">Tutorial</a> and <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=Confirmation">Demo</a>: <pre name="code" class="xml">
         // This snippet extracts a table of UserID, TotalRequests and
         // TotalQuantity from the "Reagent Requests" list.
         // Upon success, the writeTotals function (not included here) uses the
         // returned data object to display total requests and total quantities.

             LABKEY.Query.executeSql({
                     containerPath: 'home/Study/demo/guestaccess',
                     schemaName: 'lists',
                     sql: 'SELECT "Reagent Requests".UserID AS UserID, \
                         Count("Reagent Requests".UserID) AS TotalRequests, \
                         Sum("Reagent Requests".Quantity) AS TotalQuantity \
                         FROM "Reagent Requests" Group BY "Reagent Requests".UserID',
                     success: writeTotals
             });  </pre>

         * @see LABKEY.Query.SelectRowsOptions
         * @see LABKEY.Query.SelectRowsResults
         * @see LABKEY.Query.ExtendedSelectRowsResults
         * @see LABKEY.Query.Response
         */
        executeSql : function(config)
        {
            var dataObject = {
                schemaName: config.schemaName,
                sql: config.sql
            };

            // Work with Ext4.Ajax.request
            if (config.saveInSession !== undefined && config.saveInSession !== null)
                dataObject.saveInSession = config.saveInSession;

            //set optional parameters
            if (config.maxRows !== undefined && config.maxRows >= 0)
                dataObject.maxRows = config.maxRows;
            if (config.offset && config.offset > 0)
                dataObject.offset = config.offset;
            if (config.includeTotalCount != undefined)
                dataObject.includeTotalCount = config.includeTotalCount;

            if (config.containerFilter)
                dataObject.containerFilter = config.containerFilter;

            if (config.requiredVersion)
                dataObject.apiVersion = config.requiredVersion;

            if (config.includeStyle)
                dataObject.includeStyle = config.includeStyle;

            var qsParams = {};
            if (config.sort)
                qsParams["query.sort"] = config.sort;

            if (config.parameters)
            {
                for (var n in config.parameters)
                {
                    if (config.parameters.hasOwnProperty(n))
                    {
                        qsParams["query.param." + n] = config.parameters[n];
                    }
                }
            }

            var requestConfig = {
                url : LABKEY.ActionURL.buildURL("query", "executeSql.api", config.containerPath, qsParams),
                method : 'POST',
                success: getSuccessCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.stripHiddenColumns, config.scope, config.requiredVersion),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData : dataObject,
                headers : {
                    'Content-Type' : 'application/json'
                }
            };

            if (LABKEY.Utils.isDefined(config.timeout))
                requestConfig.timeout = config.timeout;

            return LABKEY.Ajax.request(requestConfig);
        },

        /**
         * Bulk import data rows into a table.
         * One of 'text', 'path', 'moduleResource', or 'file' is required and cannot be combined.
         *
         * @param {Object} config An object which contains the following configuration properties.
         * @param {String} config.schemaName Name of a schema defined within the current container.
         * @param {String} config.queryName Name of a query table associated with the chosen schema.
         * @param {File} [config.file] A <a href='https://developer.mozilla.org/en-US/docs/DOM/File'><code>File</code></a> object or a file input element to upload to the server.
         * @param {String} [config.text] Text to import.
         * @param {String} [config.path] Path to resource under webdav tree. E.g. "/_webdav/MyProject/@files/data.tsv"
         * @param {String} [config.module] Module name to use when resolving a module resource.
         * @param {String} [config.moduleResource] A file resource within the module to import.
         * @param {String} [config.importIdentity] When true, auto-increment key columns may be imported from the data.
         * @param {String} [config.importLookupByAlternateKey] When true, lookup columns can be imported by their alternate keys instead of the primary key.
         *          For example, if a column is a lookup to a SampleSet, the imported value can be the Sample's name since names must be unique within a SampleSet.
         * @param {Function} [config.success] Function called when the "importData" function executes successfully.
                        Will be called with the following arguments:
                        An object containing success and rowCount properties.
         * @param {Function} [config.failure]  Function called importing data fails.
         * @param {String} [config.containerPath] The container path in which the schema and query name are defined.
         * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
         *       generating a timeout error (defaults to 30000).
         * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         * @example Example, importing tsv data from a module: <pre name="code" class="javascript">
         LABKEY.Query.importData({
                 schemaName: 'lists',
                 queryName: 'People',
                 // reference to &lt;input type='file' id='file'&gt;
                 file: document.getElementById('file')
             },
         });</pre>
         * @example Example, importing tsv data from a module: <pre name="code" class="javascript">
         LABKEY.Query.importData({
                 schemaName: 'lists',
                 queryName: 'People',
                 module: 'mymodule',
                 moduleResource: '/data/lists/People.tsv'
             },
         });</pre>
        */
        importData : function (config)
        {
            if (!window.FormData)
                throw new Error("modern browser required");

            var form = new FormData();

            form.append("schemaName", config.schemaName);
            form.append("queryName", config.queryName);
            if (config.text)
                form.append("text", config.text);
            if (config.path)
                form.append("path", config.path);
            if (config.format)
                form.append("format", config.format);
            if (config.module)
                form.append("module", config.module);
            if (config.moduleResource)
                form.append("moduleResource", config.moduleResource);
            if (config.importIdentity)
                form.append("importIdentity", config.importIdentity);
            if (config.importLookupByAlternateKey)
                form.append("importLookupByAlternateKey", config.importLookupByAlternateKey);

            if (config.file) {
                if (config.file instanceof File)
                    form.append("file", config.file);
                else if (config.file.tagName == "INPUT" && config.file.files.length > 0)
                    form.append("file", config.file.files[0]);
            }

            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("query", "import.api", config.containerPath),
                method: 'POST',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope, false),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                form: form,
                timeout: config.timeout
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
        * @param {Function} config.success
				Function called when the "selectRows" function executes successfully.
				This function will be called with the following arguments:
				<ul>
				    <li>
                        <b>data:</b> If config.requiredVersion is not set, or set to "8.3", the success handler will be
                        passed a {@link LABKEY.Query.SelectRowsResults} object. If set to "9.1" the success handler will
                        be passed a {@link LABKEY.Query.ExtendedSelectRowsResults} object. If greater than "13.2" the success
                        handler will be passed a {@link LABKEY.Query.Response} object.
                    </li>
                    <li><b>responseObj:</b> The XMLHttpResponseObject instance used to make the AJAX request</li>
				    <li><b>options:</b> The options used for the AJAX request</li>
				</ul>
        * @param {Function} [config.failure] Function called when execution of the "selectRows" function fails.
        *       This function will be called with the following arguments:
				<ul>
				    <li><b>errorInfo:</b> an object describing the error with the following fields:
                        <ul>
                            <li><b>exception:</b> the exception message</li>
                            <li><b>exceptionClass:</b> the Java class of the exception thrown on the server</li>
                            <li><b>stackTrace:</b> the Java stack trace at the point when the exception occurred</li>
                        </ul>
                    </li>
                    <li><b>responseObj:</b> the XMLHttpResponseObject instance used to make the AJAX request</li>
				    <li><b>options:</b> the options used for the AJAX request</li>
				</ul>
        * @param {Array} [config.filterArray] Array of objects created by {@link LABKEY.Filter.create}.
        * @param {Object} [config.parameters] Map of name (string)/value pairs for the values of parameters if the SQL
        *        references underlying queries that are parameterized. For example, the following passes two parameters to the query: {'Gender': 'M', 'CD4': '400'}.
        *        The parameters are written to the request URL as follows: query.param.Gender=M&query.param.CD4=400.  For details on parameterized SQL queries, see
        *        <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=paramsql">Parameterized SQL Queries</a>.
        * @param {String} [config.sort]  String description of the sort.  It includes the column names
        *       listed in the URL of a sorted data region (with an optional minus prefix to indicate
        *       descending order). In the case of a multi-column sort, up to three column names can be
        *       included, separated by commas.
        * @param {String} [config.viewName] The name of a custom view saved on the server for the specified query.
        * @param {String} [config.columns] An Array of columns or a comma-delimited list of column names you wish to select from the specified
        *       query. By default, selectRows will return the set of columns defined in the default value for this query,
        *       as defined via the Customize View user interface on the server. You can override this by specifying a list
        *       of column names in this parameter, separated by commas. The names can also include references to related
        *       tables (e.g., 'RelatedPeptide/Peptide' where 'RelatedPeptide is the name of a foreign key column in the
        *       base query, and 'Peptide' is the name of a column in the related table).
        * @param {String} [config.containerPath] The path to the container in which the schema and query are defined,
        *       if different than the current container. If not supplied, the current container's path will be used.
         * @param {String} [config.containerFilter] One of the values of {@link LABKEY.Query.containerFilter} that sets
         *       the scope of this query. Defaults to containerFilter.current, and is interpreted relative to
         *       config.containerPath.
        * @param {String} [config.showRows] Either 'paginated' (the default) 'selected', 'unselected', 'all', or 'none'.
        *        When 'paginated', the maxRows and offset parameters can be used to page through the query's result set rows.
        *        When 'selected' or 'unselected' the set of rows selected or unselected by the user in the grid view will be returned.
        *        You can programatically get and set the selection using the {@link LABKEY.DataRegion.setSelected} APIs.
        *        Setting <code>config.maxRows</code> to -1 is the same as 'all'
        *        and setting <code>config.maxRows</code> to 0 is the same as 'none'.
        * @param {Integer} [config.maxRows] The maximum number of rows to return from the server (defaults to 100000).
        *        If you want to return all possible rows, set this config property to -1.
        * @param {Integer} [config.offset] The index of the first row to return from the server (defaults to 0).
        *        Use this along with the maxRows config property to request pages of data.
        * @param {Boolean} [config.includeTotalCount] Include the total number of rows available (defaults to true).
        *       If false totalCount will equal number of rows returned (equal to maxRows unless maxRows == 0).
        * @param {Boolean} [config.includeDetailsColumn] Include the Details link column in the set of columns (defaults to false).
        *       If included, the column will have the name "~~Details~~". The underlying table/query must support details links
        *       or the column will be omitted in the response.
        * @param {Boolean} [config.includeUpdateColumn] Include the Update (or edit) link column in the set of columns (defaults to false).
        *       If included, the column will have the name "~~Update~~". The underlying table/query must support update links
        *       or the column will be omitted in the response.
        * @param {String} [config.selectionKey] Unique string used by selection APIs as a key when storing or retrieving the selected items for a grid.
        *         Not used unless <code>config.showRows</code> is 'selected' or 'unselected'.
        * @param {Boolean} [config.ignoreFilter] If true, the command will ignore any filter that may be part of the chosen view.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        * @param {Double} [config.requiredVersion] If not set, or set to "8.3", the success handler will be passed a {@link LABKEY.Query.SelectRowsResults}
        *        object. If set to "9.1" the success handler will be passed a {@link LABKEY.Query.ExtendedSelectRowsResults}
        *        object. If greater than "13.2" the success handler will be passed a {@link LABKEY.Query.Response} object.
        *        The main difference between SelectRowsResults and ExtendedSelectRowsResults is that each column in each row
        *        will be another object (not just a scalar value) with a "value" property as well as other related properties
        *        (url, mvValue, mvIndicator, etc.). In the LABKEY.Query.Response format each row will an instance of
        *        {@link LABKEY.Query.Row}.
         *       In the "16.2" format, multi-value columns will be returned as an array of values, each of which may have a value, displayValue, and url.
         *       In the "17.1" format, "formattedValue" may be included in the response as the display column's value formatted with the display column's format or folder format settings.
        * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
        * @example Example: <pre name="code" class="xml">
&lt;script type="text/javascript"&gt;
	function onFailure(errorInfo, options, responseObj)
	{
	    if (errorInfo && errorInfo.exception)
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
            columns: ['Name', 'Age'],
            success: onSuccess,
            failure: onFailure
        });
&lt;/script&gt; </pre>
		* @see LABKEY.Query.SelectRowsOptions
		* @see LABKEY.Query.SelectRowsResults
        * @see LABKEY.Query.ExtendedSelectRowsResults
        * @see LABKEY.Query.Response
		*/
        selectRows : function(config)
        {
            //check for old-style separate arguments
            if (arguments.length > 1)
            {
                config = {
                    schemaName: arguments[0],
                    queryName: arguments[1],
                    success: arguments[2],
                    errorCallback: arguments[3],
                    filterArray: arguments[4],
                    sort: arguments[5],
                    viewName: arguments[6]
                };
            }

            if (!config.schemaName)
                throw "You must specify a schemaName!";
            if (!config.queryName)
                throw "You must specify a queryName!";

            config.dataRegionName = config.dataRegionName || "query";

            var dataObject = LABKEY.Query.buildQueryParams(
                    config.schemaName,
                    config.queryName,
                    config.filterArray,
                    config.sort,
                    config.dataRegionName
            );

            if (!config.showRows || config.showRows == 'paginated')
            {
                if (config.offset)
                    dataObject[config.dataRegionName + '.offset'] = config.offset;

                if (config.maxRows != undefined)
                {
                    if (config.maxRows < 0)
                        dataObject[config.dataRegionName + '.showRows'] = "all";
                    else
                        dataObject[config.dataRegionName + '.maxRows'] = config.maxRows;
                }
            }
            else if (config.showRows in {'all':true, 'selected':true, 'unselected':true, 'none':true})
            {
                dataObject[config.dataRegionName + '.showRows'] = config.showRows;
            }


            if (config.viewName)
                dataObject[config.dataRegionName + '.viewName'] = config.viewName;

            if (config.columns)
                dataObject[config.dataRegionName + '.columns'] = LABKEY.Utils.isArray(config.columns) ? config.columns.join(",") : config.columns;

            if (config.selectionKey)
                dataObject[config.dataRegionName + '.selectionKey'] = config.selectionKey;

            if (config.ignoreFilter)
                dataObject[config.dataRegionName + '.ignoreFilter'] = 1;

            if (config.parameters)
            {
                for (var propName in config.parameters)
                {
                    if (config.parameters.hasOwnProperty(propName))
                        dataObject[config.dataRegionName + '.param.' + propName] = config.parameters[propName];
                }
            }

            if (config.requiredVersion)
                dataObject.apiVersion = config.requiredVersion;

            if (config.containerFilter)
                dataObject.containerFilter = config.containerFilter;

            if (config.includeTotalCount)
                dataObject.includeTotalCount = config.includeTotalCount;

            if (config.includeDetailsColumn)
                dataObject.includeDetailsColumn = config.includeDetailsColumn;

            if (config.includeUpdateColumn)
                dataObject.includeUpdateColumn = config.includeUpdateColumn;

            if (config.includeStyle)
                dataObject.includeStyle = config.includeStyle;

            var requestConfig = {
                url : LABKEY.ActionURL.buildURL('query', 'getQuery.api', config.containerPath),
                method : getMethod(config.method),
                success: getSuccessCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.stripHiddenColumns, config.scope, config.requiredVersion),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params : dataObject
            };

            if (LABKEY.Utils.isDefined(config.timeout))
                requestConfig.timeout = config.timeout;

            return LABKEY.Ajax.request(requestConfig);
        },

        /**
         * Select Distinct Rows
         * @param {Object} config An object which contains the following configuration properties.
         * @param {String} config.schemaName Name of a schema defined within the current container. See also: <a class="link"
                        href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
                        How To Find schemaName, queryName &amp; viewName</a>.
         * @param {String} config.queryName Name of a query table associated with the chosen schema.  See also: <a class="link"
                        href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
                        How To Find schemaName, queryName &amp; viewName</a>.
         * @param {String} config.column A single column for which the distinct results will be requested. This column
         *              must exist within the specified query.
         * @param {String} [config.containerFilter] One of the values of {@link LABKEY.Query.containerFilter} that sets
         *       the scope of this query. Defaults to containerFilter.current, and is interpreted relative to
         *       config.containerPath.
         * @param {Object} [config.parameters] Map of name (string)/value pairs for the values of parameters if the SQL
         *        references underlying queries that are parameterized. For example, the following passes two parameters to the query: {'Gender': 'M', 'CD4': '400'}.
         *        The parameters are written to the request URL as follows: query.param.Gender=M&query.param.CD4=400.  For details on parameterized SQL queries, see
         *        <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=paramsql">Parameterized SQL Queries</a>.
         * @param {Array} [config.filterArray] Array of objects created by {@link LABKEY.Filter.create}.
         * @param {String} [config.viewName] Name of a view to use.  This is potentially important if this view contains filters on the data.
         * @param {Function} config.success
         * @param {Function} config.failure
         * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         */
        selectDistinctRows : function(config)
        {
            if (!config.schemaName)
                throw "You must specify a schemaName!";
            if (!config.queryName)
                throw "You must specify a queryName!";
            if (!config.column)
                throw "You must specify a column!";

            config.dataRegionName = config.dataRegionName || "query";

            var dataObject = LABKEY.Query.buildQueryParams(
                    config.schemaName,
                    config.queryName,
                    config.filterArray,
                    config.sort,
                    config.dataRegionName
            );

            dataObject[config.dataRegionName + '.columns'] = config.column;

            if (config.viewName)
                dataObject[config.dataRegionName + '.viewName'] = config.viewName;

            if (config.maxRows && config.maxRows >= 0)
                dataObject.maxRows = config.maxRows;

            if (config.containerFilter)
                dataObject.containerFilter = config.containerFilter;

            if (config.parameters)
            {
                for (var propName in config.parameters)
                {
                    if (config.parameters.hasOwnProperty(propName))
                        dataObject[config.dataRegionName + '.param.' + propName] = config.parameters[propName];
                }
            }

            if (config.ignoreFilter)
            {
                dataObject[config.dataRegionName + '.ignoreFilter'] = true;
            }

            return LABKEY.Ajax.request({
                url : LABKEY.ActionURL.buildURL('query', 'selectDistinct.api', config.containerPath),
                method : getMethod(config.method),
                success: getSuccessCallbackWrapper(LABKEY.Utils.getOnSuccess(config), false, config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params : dataObject
            });
        },


        /**
         * Returns a list of reports, views and/or datasets in a container
         * @param config
         * @param {String} [config.containerPath] A container path in which to execute this command.  If not provided, the current container will be used
         * @param {Array} [config.dataTypes] An array of data types to return, which can be any of: 'reports', 'datasets' or 'queries'.  If null, all will be returned
         * @param {Function} [config.success] A function called on success.  It will be passed a single argument with the following properties:
         * <ul>
         * <li>data: An array with one element per dataview.  Each view is a map with the following properties:
         * <ul>
         * <li>access:
         * <li>allowCustomThumbnail: A flag indicating whether the thumbnail can be customized
         * <li>category: The category to which this item has been assigned
         * <li>categoryDisplayOrder: The display order within that category
         * <li>container: The container where this dataView is defined
         * <li>created: The displayName of the user who created the item
         * <li>createdByUserId: The user Id of the user who created the item
         * <li>dataType: The dataType of this item, either queries, reports or datasets
         * <li>detailsUrl: The url that will display additional details about this item
         * <li>icon: The url of the icon for this report
         * <li>id: The unique Id of this item
         * <li>reportId: The unique report Id if this item is a report. Value is null if this item is not a report.
         * <li>modified: The date this item was last modified
         * <li>name: The display name of this item
         * <li>runUrl: The url that can be used to execute this report
         * <li>shared: A flag indicating whether this item is shared
         * <li>thumbnail: The url of this item's thumbnail image
         * <li>type: The display string for the Data Type.
         * <li>visible: A flag indicating whether this report is visible or hidden
         * </ul>
         * <li>types: a map of each dataType, and a boolean indicating whether it was included in the results (this is based on the dataTypes param in the config)
         * </ul>
         * @param {Function} [config.failure] A function called when execution of "getDataViews" fails.
         * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         */
        getDataViews : function(config)
        {
            var dataObject = {
                includeData: true,
                includeMetadata: false
            };
            if(config.dataTypes)
                dataObject.dataTypes = config.dataTypes;

            var callbackFn = LABKEY.Utils.getOnSuccess(config);
            var success = LABKEY.Utils.getCallbackWrapper(function(data, response, options){
                                            if (callbackFn)
                                                callbackFn.call(config.scope || this, data.data, options, response);
                                        }, this);

            return LABKEY.Ajax.request({
                url : LABKEY.ActionURL.buildURL('reports', 'browseData.api', config.containerPath),
                method : 'POST',
                success: success,
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData : dataObject,
                headers : {
                    'Content-Type' : 'application/json'
                }
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
        * @param {Array} config.rows Array of record objects in which each object has a property for each field.
        *               The row array must include the primary key column values and values for
        *               other columns you wish to update.
        * @param {Object} [config.extraContext] <b>Experimental:</b> Optional extra context object passed into the transformation/validation script environment.
        * @param {Function} config.success Function called when the "updateRows" function executes successfully.
        	    Will be called with arguments:
                the parsed response data ({@link LABKEY.Query.ModifyRowsResults}), the XMLHttpRequest object and
                (optionally) the "options" object ({@link LABKEY.Query.ModifyRowsOptions}).
        * @param {Function} [config.failure] Function called when execution of the "updateRows" function fails.
        *                   See {@link LABKEY.Query.selectRows} for more information on the parameters passed to this function.
        * @param {String} [config.containerPath] The container path in which the schema and query name are defined.
        *              If not supplied, the current container path will be used.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        * @param {boolean} [config.transacted] Whether all of the updates should be done in a single transaction, so they all succeed or all fail. Defaults to true
        * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
		* @see LABKEY.Query.ModifyRowsResults
		* @see LABKEY.Query.ModifyRowsOptions
        */
        updateRows : function(config)
        {
            if (arguments.length > 1)
                config = configFromArgs(arguments);
            config.action = "updateRows.api";
            return sendJsonQueryRequest(config);
        },

        /**
        * Save inserts, updates, and/or deletes to potentially multiple tables with a single request.
        * @param {Object} config An object which contains the following configuration properties.
        * @param {Array} config.commands An array of all of the update/insert/delete operations to be performed.
        * Each command has the following structure:
        * @param {String} config.commands[].schemaName Name of a schema defined within the current container. See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} config.commands[].queryName Name of a query table associated with the chosen schema.  See also: <a class="link"
					href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
					How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} config.commands[].command Name of the command to be performed. Must be one of "insert", "update", or "delete".
        * @param {Array} config.commands[].rows An array of data for each row to be changed. See {@link LABKEY.Query.insertRows},
        * {@link LABKEY.Query.updateRows}, or {@link LABKEY.Query.deleteRows} for requirements of what data must be included for each row.
        * @param {Object} [config.commands[].extraContext] <b>Experimental:</b> Optional extra context object passed into the transformation/validation script environment.
        * @param {Object} [config.extraContext] <b>Experimental:</b> Optional extra context object passed into the transformation/validation script environment.
        * The extraContext at the command-level will be merged with the extraContext at the top-level of the config.
        * @param {Function} config.success Function called when the "saveRows" function executes successfully.
        	    Called with arguments:
                <ul>
                    <li>an object with the following properties:
                    <ul>
                        <li><strong>result</strong>: an array of parsed response data ({@link LABKEY.Query.ModifyRowsResults}) (one for each command in the request)
                        <li><strong>errorCount</strong>: an integer, with the total number of errors encountered during the operation
                        <li><strong>committed</strong>: a boolean, indicating if the changes were actually committed to the database
                    </ul>
                    <li>the XMLHttpRequest object</li>
                    <li>(optionally) the "options" object ({@link LABKEY.Query.ModifyRowsOptions})</li>
                </ul>
        * @param {Function} [config.failure] Function called if execution of the "saveRows" function fails.
        *                   See {@link LABKEY.Query.selectRows} for more information on the parameters passed to this function.
        * @param {String} [config.containerPath] The container path in which the changes are to be performed.
        *              If not supplied, the current container path will be used.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        * @param {Double} [config.apiVersion] Version of the API. If this is 13.2 or higher, a request that fails
        * validation will be returned as a successful response. Use the 'errorCount' and 'committed' properties in the
        * response to tell if it committed or not. If this is 13.1 or lower (or unspecified), the failure callback
        * will be invoked instead in the event of a validation failure.
        * @param {boolean} [config.transacted] Whether all of the row changes for all of the tables
        * should be done in a single transaction, so they all succeed or all fail. Defaults to true
        * @param {boolean} [config.validateOnly] Whether or not the server should attempt proceed through all of the
        * commands, but not actually commit them to the database. Useful for scenarios like giving incremental
        * validation feedback as a user fills out a UI form, but not actually save anything until they explicitly request
        * a save.
        * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
		* @see LABKEY.Query.ModifyRowsResults
		* @see LABKEY.Query.ModifyRowsOptions
        */
        saveRows : function(config)
        {
            if (arguments.length > 1)
                config = configFromArgs(arguments);

            var dataObject = {
                commands: config.commands,
                containerPath: config.containerPath,
                validateOnly : config.validateOnly,
                transacted : config.transacted,
                extraContext : config.extraContext,
                apiVersion : config.apiVersion
            };

            var requestConfig = {
                url : LABKEY.ActionURL.buildURL("query", "saveRows.api", config.containerPath),
                method : 'POST',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData : dataObject,
                headers : {
                    'Content-Type' : 'application/json'
                }
            };

            if (LABKEY.Utils.isDefined(config.timeout))
                requestConfig.timeout = config.timeout;

            return LABKEY.Ajax.request(requestConfig);

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
        * @param {Array} config.rows Array of record objects in which each object has a property for each field.
        *                  The row data array must include all column values except for the primary key column.
        *                  However, you will need to include the primary key column values if you defined
        *                  them yourself instead of relying on auto-number.
        * @param {Object} [config.extraContext] <b>Experimental:</b> Optional extra context object passed into the transformation/validation script environment.
        * @param {Function} config.success Function called when the "insertRows" function executes successfully.
						Will be called with the following arguments:
                        the parsed response data ({@link LABKEY.Query.ModifyRowsResults}), the XMLHttpRequest object and
                        (optionally) the "options" object ({@link LABKEY.Query.ModifyRowsOptions}).
		* @param {Function} [config.failure]  Function called when execution of the "insertRows" function fails.
        *                   See {@link LABKEY.Query.selectRows} for more information on the parameters passed to this function.
        * @param {String} [config.containerPath] The container path in which the schema and query name are defined.
        *              If not supplied, the current container path will be used.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        * @param {boolean} [config.transacted] Whether all of the inserts should be done in a single transaction, so they all succeed or all fail. Defaults to true
        * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
        * @example Example, from the Reagent Request <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=reagentRequestForm">Tutorial</a> and <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a>: <pre name="code" class="xml">
         // This snippet inserts data from the ReagentReqForm into a list.
         // Upon success, it moves the user to the confirmation page and
         // passes the current user's ID to that page.
         LABKEY.Query.insertRows({
             containerPath: '/home/Study/demo/guestaccess',
             schemaName: 'lists',
             queryName: 'Reagent Requests',
             rows: [{
                "Name":  ReagentReqForm.DisplayName.value,
                "Email": ReagentReqForm.Email.value,
                "UserID": ReagentReqForm.UserID.value,
                "Reagent": ReagentReqForm.Reagent.value,
                "Quantity": parseInt(ReagentReqForm.Quantity.value),
                "Date": new Date(),
                "Comments": ReagentReqForm.Comments.value,
                "Fulfilled": 'false'
             }],
             successCallback: function(data){
                 window.location =
                    '/wiki/home/Study/demo/page.view?name=confirmation&userid='
                    + LABKEY.Security.currentUser.id;
             },
         });  </pre>
		* @see LABKEY.Query.ModifyRowsResults
		* @see LABKEY.Query.ModifyRowsOptions
        */
        insertRows : function(config)
        {
            if (arguments.length > 1)
                config = configFromArgs(arguments);
            config.action = "insertRows.api";
            return sendJsonQueryRequest(config);
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
        * @param {Object} [config.extraContext] <b>Experimental:</b> Optional extra context object passed into the transformation/validation script environment.
        * @param {Function} config.success Function called when the "deleteRows" function executes successfully.
                     Will be called with the following arguments:
                     the parsed response data ({@link LABKEY.Query.ModifyRowsResults}), the XMLHttpRequest object and
                     (optionally) the "options" object ({@link LABKEY.Query.ModifyRowsOptions}).
		* @param {Function} [config.failure] Function called when execution of the "deleteRows" function fails.
         *                   See {@link LABKEY.Query.selectRows} for more information on the parameters passed to this function.
        * @param {Array} config.rows Array of record objects in which each object has a property for each field.
        *                  The row data array needs to include only the primary key column value, not all columns.
        * @param {String} [config.containerPath] The container path in which the schema and query name are defined.
        *              If not supplied, the current container path will be used.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        * @param {boolean} [config.transacted] Whether all of the deletes should be done in a single transaction, so they all succeed or all fail. Defaults to true
        * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
		* @see LABKEY.Query.ModifyRowsResults
		* @see LABKEY.Query.ModifyRowsOptions
        */
        deleteRows : function(config)
        {
            if (arguments.length > 1)
            {
                config = configFromArgs(arguments);
            }
            config.action = "deleteRows.api";
            return sendJsonQueryRequest(config);
        },

        /**
         * Delete a query view.
         * @param config An object that contains the following configuration parameters
         * @param {String} config.schemaName The name of the schema.
         * @param {String} config.queryName the name of the query.
         * @param {String} [config.viewName] the name of the view. If a viewName is not specified, the default view will be deleted/reverted.
         * @param {boolean} [config.revert] Optionally, the view can be reverted instead of deleted. Defaults to false.
         */
        deleteQueryView : function(config) {
            if (!config) {
                throw 'You must specify a configuration!'
            }
            if (!config.schemaName) {
                throw 'You must specify a schemaName!'
            }
            if (!config.queryName) {
                throw 'You must specify a queryName!'
            }

            var params = {
                schemaName: config.schemaName,
                queryName: config.queryName
            };

            if (config.viewName) {
                params.viewName = config.viewName;
            }

            if (config.revert !== undefined) {
                params.complete = config.revert !== true;
            }

            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'deleteView.api', config.containerPath),
                method: 'POST',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData: params
            });
        },

        /**
         * Build and return an object suitable for passing to the
         * <a href = "http://www.extjs.com/deploy/dev/docs/?class=Ext.Ajax">Ext.Ajax</a> 'params' configuration property.
         * @param {string} schemaName Name of a schema defined within the current container.  See also: <a class="link"
         href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
         How To Find schemaName, queryName &amp; viewName</a>.
         * @param {string} queryName Name of a query table associated with the chosen schema.   See also: <a class="link"
         href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
         How To Find schemaName, queryName &amp; viewName</a>.
         * @param {LABKEY.Filter[]} [filterArray] Array of objects created by {@link LABKEY.Filter.create}.
         * @param {string} [sort]  String description of the sort.  It includes the column names
         *       listed in the URL of a sorted data region (with an optional minus prefix to indicate
         *       descending order). In the case of a multi-column sort, up to three column names can be
         *       included, separated by commas.
         * @param {string} [dataRegionName=query]
         * @returns {Object} Object suitable for passing to the
         * <a href = "http://extjs.com/deploy/ext-2.2.1/docs/?class=Ext.Ajax">Ext.Ajax</a> 'params' configuration property.
         */
        buildQueryParams: function(schemaName, queryName, filterArray, sort, dataRegionName)
        {
            dataRegionName = dataRegionName || "query";
            var params = {};
            params.dataRegionName = dataRegionName;
            params[dataRegionName + '.queryName'] = queryName;
            params.schemaName = schemaName;
            if (sort)
            {
                params[dataRegionName + '.sort'] = sort;
            }

            LABKEY.Filter.appendFilterParams(params, filterArray, dataRegionName);

            return params;
        },

        /**
         * Returns the set of schemas available in the specified container.
         * @param config An object that contains the following configuration parameters
         * @param {String} config.schemaName Get schemas under the given schemaName.
         * @param {String} config.apiVersion Version of the API. Changed the structure of the server's response.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>schemasInfo:</b> An object with a property called "schemas". If no apiVersion is specified, or it is
         * less than 9.3, it will be an array of schema names. If apiVersion is 9.3 or greater, it will be a map where
         * the keys are schemaNames and the values are objects with the following properties:
         *     <ul>
         *         <li><b>schemaName</b>: the short name of the schema</li>
         *         <li><b>fullyQualifiedName</b>: the fully qualified name of the schema, encoded as a string with
         *         "." separators as described in {@link LABKEY.SchemaKey}</li>
         *         <li><b>description</b>: a short description of the schema</li>
         *         <li><b>schemas</b>: a map of child schemas, with values in the same structure as this object</li>
         *     </ul>
         * </li>
         * </ul>
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message.</li>
         * </ul>
         * @param {String} [config.containerPath] A container path in which to execute this command. If not supplied,
         * the current container will be used.
         * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        getSchemas : function(config)
        {
            var params = {};
            if (config.apiVersion)
                params.apiVersion = config.apiVersion;
            if (config.schemaName)
                params.schemaName = config.schemaName;

            return LABKEY.Ajax.request({
                url : LABKEY.ActionURL.buildURL('query', 'getSchemas.api', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params: params
            });
        },

        /**
         * Returns the set of queries available in a given schema.
         * @param config An object that contains the following configuration parameters
         * @param {String} config.schemaName The name of the schema.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>queriesInfo:</b> An object with the following properties
         *  <ul>
         *      <li><b>schemaName:</b> the name of the requested schema</li>
         *      <li><b>queries:</b> an array of objects, each of which has the following properties
         *          <ul>
         *              <li><b>name:</b> the name of the query</li>
         *              <li><b>title:</b> this is the label used when displaying this table. If the table has no title, this will be the same as the name.</li>
         *              <li><b>hidden:</b> true if this is a hidden query or table.
         *              <li><b>inherit:</b> true if this query is marked as inheritable in sub-folders.
         *              <li><b>isUserDefined:</b> true if this is a user-defined query</li>
         *              <li><b>canEdit:</b> true if the current user can edit this query</li>
         *              <li><b>isMetadataOverrideable:</b> true if the current user may override the query's metadata</li>
         *              <li><b>moduleName:</b> the module that defines this query</li>
         *              <li><b>isInherited:</b> true if this query is defined in a different container.</li>
         *              <li><b>containerPath:</b> if <code>isInherited</code>, the container path where this query is defined.</li>
         *              <li><b>description:</b> A description for this query (if provided)</li>
         *              <li><b>viewDataUrl:</b> the server-relative URL where this query's data can be viewed.
         *                  Available in LabKey Server version 10.2 and later.</li>
         *              <li><b>columns:</b> if config.includeColumns is not false, this will contain an array of
         *                 objects with the following properties
         *                  <ul>
         *                      <li><b>name:</b> the name of the column</li>
         *                      <li><b>caption:</b> the caption of the column (may be undefined)</li>
         *                      <li><b>description:</b> the description of the column (may be undefined)</li>
         *                  </ul>
         *              </li>
         *          </ul>
         *      </li>
         *  </ul>
         * </li>
         * </ul>
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message.</li>
         * </ul>
         * @param {Boolean} [config.includeUserQueries] If set to false, user-defined queries will not be included in
         * the results. Default is true.
         * @param {Boolean} [config.includeSystemQueries] If set to false, system-defined queries will not be included in
         * the results. Default is true.
         * @param {Boolean} [config.includeColumns] If set to false, information about the available columns in this
         * query will not be included in the results. Default is true.
         * @param {String} [config.containerPath] A container path in which to execute this command. If not supplied,
         * the current container will be used.
         * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        getQueries : function(config)
        {
            var params = {};
            // Only pass the parameters that the server supports, and exclude ones like successCallback
            LABKEY.Utils.applyTranslated(params, config,
            {
                schemaName: 'schemaName',
                includeColumns: 'includeColumns',
                includeUserQueries: 'includeUserQueries',
                includeSystemQueries: 'includeSystemQueries'
            }, false, false);

            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'getQueries.api', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params: params
            });
        },

        /**
         * Returns the set of views available for a given query in a given schema.
         * @param config An object that contains the following configuration parameters
         * @param {String} config.schemaName The name of the schema.
         * @param {String} config.queryName the name of the query.
         * @param {String} [config.viewName] A view name (empty string for the default view), otherwise return all views for the query.
         * @param {Boolean} [config.metadata] Include view column field metadata.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>viewsInfo:</b> An object with the following properties
         *  <ul>
         *      <li><b>schemaName:</b> the name of the requested schema</li>
         *      <li><b>queryName:</b> the name of the requested query</li>
         *      <li><b>views:</b> an array of objects, each of which has the following properties
         *          <ul>
         *              <li><b>name:</b> the name of the view (default view's name is empty string)</li>
         *              <li><b>label:</b> the label of the view</li>
         *              <li><b>default:</b> true if this is the default view info</li>
         *              <li><b>viewDataUrl:</b> the server-relative URL where this view's data can be viewed.
         *                  Available in LabKey Server version 10.2 and later.</li>
         *              <li><b>columns:</b> this will contain an array of objects with the following properties
         *                  <ul>
         *                      <li><b>name:</b> the name of the column</li>
         *                      <li><b>fieldKey:</b> the field key for the column (may include join column names, e.g. 'State/Population')</li>
         *                  </ul>
         *              </li>
         *              <li><b>filter:</b> TBD
         *                  Available in LabKey Server version 10.3 and later.</li>
         *              <li><b>sort:</b> TBD
         *                  Available in LabKey Server version 10.3 and later.</li>
         *              <li><b>fields:</b> TBD if metadata
         *                  Available in LabKey Server version 10.3 and later.</li>
         *          </ul>
         *      </li>
         *  </ul>
         * </li>
         * </ul>
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message.</li>
         * </ul>
         * @param {String} [config.containerPath] A container path in which to execute this command. If not supplied,
         * the current container will be used.
         * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        getQueryViews : function(config)
        {
            var params = {};
            if (config.schemaName)
                params.schemaName = config.schemaName;
            if (config.queryName)
                params.queryName = config.queryName;
            if (config.viewName != undefined)
                params.viewName = config.viewName;
            if (config.metadata)
                params.metadata = config.metadata;

            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'getQueryViews.api', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params: params
            });
        },

        /**
         * Creates or updates a custom view or views for a given query in a given schema.  The config
         * object matches the viewInfos parameter of the getQueryViews.successCallback.
         * @param config An object that contains the following configuration parameters
         * @param {String} config.schemaName The name of the schema.
         * @param {String} config.queryName The name of the query.
         * @param {String} config.views The updated view definitions.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with the same parameters as getQueryViews.successCallback.
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message.</li>
         * </ul>
         * @param {String} [config.containerPath] A container path in which to execute this command. If not supplied,
         * the current container will be used.
         * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        saveQueryViews : function (config)
        {
            var params = {};
            if (config.schemaName)
                params.schemaName = config.schemaName;
            if (config.queryName)
                params.queryName = config.queryName;
            if (config.views)
                params.views = config.views;

            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'saveQueryViews.api', config.containerPath),
                method: 'POST',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                jsonData : params,
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Returns details about a given query including detailed information about result columns
         * @param {Object} config An object that contains the following configuration parameters
         * @param {String} config.schemaName The name of the schema.
         * @param {String} config.queryName The name of the query.
         * @param {String} [config.viewName] A view name or Array of view names to include custom view details. Use '*' to include all views for the query.
         * @param {String} [config.fields] A field key or Array of field keys to include in the metadata.
         * @param {Boolean} [config.initializeMissingView] Initialize the view based on the default view iff the view doesn't yet exist.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>queryInfo:</b> An object with the following properties
         *  <ul>
         *      <li><b>schemaName:</b> the name of the requested schema</li>
         *      <li><b>name:</b> the name of the requested query</li>
         *      <li><b>isUserDefined:</b> true if this is a user-defined query</li>
         *      <li><b>canEdit:</b> true if the current user can edit this query</li>
         *      <li><b>isMetadataOverrideable:</b> true if the current user may override the query's metadata</li>
         *      <li><b>moduleName:</b> the module that defines this query</li>
         *      <li><b>isInherited:</b> true if this query is defined in a different container.</li>
         *      <li><b>containerPath:</b> if <code>isInherited</code>, the container path where this query is defined.</li>
         *      <li><b>viewDataUrl:</b> The URL to navigate to for viewing the data returned from this query</li>
         *      <li><b>title:</b> If a value has been set, this is the label used when displaying this table</li>
         *      <li><b>description:</b> A description for this query (if provided)</li>
         *      <li><b>columns:</b> Information about all columns in this query. This is an array of LABKEY.Query.FieldMetaData objects.</li>
         *      <li><b>defaultView:</b> An array of column information for the columns in the current user's default view of this query.
         *      The shape of each column info is the same as in the columns array.</li>
         *      <li><b>views:</b> An array of view info (XXX: same as views.getQueryViews()
         *  </ul>
         * </li>
         * </ul>
         * @see LABKEY.Query.FieldMetaData
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message.</li>
         * </ul>
         * @param {String} [config.containerPath] A container path in which to execute this command. If not supplied,
         * the current container will be used.
         * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        getQueryDetails : function(config)
        {
            var params = {};
            if (config.schemaName)
                params.schemaName = config.schemaName;

            if (config.queryName)
                params.queryName = config.queryName;

            if (config.viewName != undefined)
                params.viewName = config.viewName;

            if (config.fields)
                params.fields = config.fields;

            if (config.fk)
                params.fk = config.fk;

            if (config.initializeMissingView)
                params.initializeMissingView = config.initializeMissingView;

            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'getQueryDetails.api', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params: params
            });
        },

        /**
         * Validates the specified query by ensuring that it parses and executes without an exception.
         * @param config An object that contains the following configuration parameters
         * @param {String} config.schemaName The name of the schema.
         * @param {String} config.queryName the name of the query.
         * @param {Boolean} config.includeAllColumns If set to false, only the columns in the user's default view
         * of the specific query will be tested (defaults to true).
         * @param {Boolean} config.validateQueryMetadata If true, the query metadata and custom views will also be validated.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with a simple object with one property named "valid" set to true.
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message. If validateQueryMetadata was used, this will also hae a property called 'errors', which is an array of objects describing each error.</li>
         * </ul>
         * @param {String} [config.containerPath] A container path in which to execute this command. If not supplied,
         * the current container will be used.
         * @param {Object} [config.scope] A scope for the callback functions. Defaults to "this"
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        validateQuery : function(config)
        {
            var params = {};

            LABKEY.Utils.applyTranslated(params, config, {
                successCallback: false,
                errorCallback: false,
                scope: false
            });

            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', (config.validateQueryMetadata ? 'validateQueryMetadata.api' : 'validateQuery.api'), config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params: params
            });
        },

        /**
         * Returns the current date/time on the LabKey server.
         * @param config An object that contains the following configuration parameters
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with a single parameter of type Date.
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message.</li>
         * </ul>
         * @returns {Mixed} In client-side scripts, this method will return a transaction id
         * for the async request that can be used to cancel the request
         * (see <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.data.Connection&member=abort" target="_blank">Ext.data.Connection.abort</a>).
         * In server-side scripts, this method will return the JSON response object (first parameter of the success or failure callbacks.)
         */
        getServerDate : function(config)
        {
            return LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('query', 'getServerDate.api'),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope),
                success: LABKEY.Utils.getCallbackWrapper(function(json){
                    var d;
                    var onSuccess = LABKEY.Utils.getOnSuccess(config);
                    if (json && json.date && onSuccess)
                    {
                        d = new Date(json.date);
                        onSuccess(d);
                    }
                }, this)
            });
        },


        /**
         * Converts a javascript date into a format suitable for using in a LabKey SQL query, includes time but not milliseconds.
         * @param {Date} date JavaScript date
         * @param {Boolean} withMS include milliseconds
         * @returns {String} a date and time literal formatted to be used in a LabKey query
         */
        sqlDateTimeLiteral : function(date, withMS)
        {
            if (date === undefined || date === null || !date)
                return "NULL";
            if (typeof date == "string")
            {
                try { date = new Date(date); } catch (x) { }
            }
            if (typeof date == "object" && typeof date.toISOString == "function")
            {
                var fmt2 = function(a) {return (a>=10 ?  ""+a : "0"+a);};
                var fmt3 = function(a) {return (a>=100 ? ""+a : "0"+fmt2(a));};
                return "{ts '" +
                        date.getFullYear() + "-" + fmt2(date.getMonth()+1) + "-" +fmt2(date.getDate()) + " " + fmt2(date.getHours()) + ":" + fmt2(date.getMinutes()) + ":" + fmt2(date.getSeconds()) +
                        (withMS ? "." + fmt3(date.getMilliseconds()) : "")
                        + "'}";
            }
            return "{ts '" + this.sqlStringLiteral(date) + "'}";
        },


        /**
         * Converts a JavaScript date into a format suitable for using in a LabKey SQL query, does not include time.
         * @param {Date} date JavaScript date
         * @returns {String} a date literal formatted to be used in a LabKey query
         */
        sqlDateLiteral : function(date)
        {
            if (date === undefined || date === null || !date)
                return "NULL";
            if (typeof date == "string")
            {
                try { date = new Date(date); } catch (x) { }
            }
            if (typeof date == "object" && typeof date.toISOString == "function")
            {
                var fmt2 = function(a) {return (a>=10 ? a : "0"+a);};
                var fmt3 = function(a) {return (a>=999 ? a : "0"+fmt2(a));};
                return "{d '" +
                        date.getFullYear() + "-" + fmt2(date.getMonth()+1) + "-" +fmt2(date.getDate())
                        + "'}";
            }
            return "{d '" + this.sqlStringLiteral(date) + "'}";
        },


        /**
         * Converts a JavaScript string into a format suitable for using in a LabKey SQL query.
         * @param {string} str String to use in query
         * @returns {string} value formatted for use in a LabKey query.  Will properly escape single quote characters.
         */
        sqlStringLiteral : function(str)
        {
            if (str === undefined || str === null || str == '')
                return "NULL";
            str = str.toString();
            return "'" + str.replace("'","''") + "'";
        },

        URL_COLUMN_PREFIX: "_labkeyurl_"
    };
};

/**
 * @class This class is used to construct filters when using APIs such as {@link LABKEY.Query.GetData.getRawData},
 *      {@link LABKEY.Query.selectRows}, or {@link LABKEY.Query.executeSql}. This is the base filter class, which requires
 *      the user specify a filter type from {@link LABKEY.Filter#Types}. Users can avoid the need for specifying a filter
 *      type by using a subclass of Filter such as {@link LABKEY.Query.Filter.Equals} or {@link LABKEY.Query.Filter.GreaterThan}, which
 *      will automatically set the type for the user.
 * @param {String} columnName Required. The name of the column the filter will be applied  Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @param {LABKEY.Filter#Types} filterType Type of filter to apply to the 'column' using the 'value'
 * @constructor
 */
LABKEY.Query.Filter = function (columnName, value, filterType)
{
    if (columnName) {
        if (columnName instanceof LABKEY.FieldKey) {
            columnName = columnName.toString();
        }
        else if (columnName instanceof Array) {
            columnName = columnName.join('/');
        }
    }

    if (!filterType)
    {
        filterType = LABKEY.Filter.Types.EQUAL;
    }

    /**
     * @private
     */
    this.columnName = columnName;

    /**
     * @private
     */
    this.value = value;

    /**
     * @private
     */
    this.filterType = filterType;
};

/**
 * Gets the column name used in the filter.
 * @returns {String}
 */
LABKEY.Query.Filter.prototype.getColumnName = function ()
{
    return this.columnName
};

/**
 * Gets the filter type used to construct the filter.
 * @returns {LABKEY.Filter#Types}
 */
LABKEY.Query.Filter.prototype.getFilterType = function ()
{
    return this.filterType
};

/**
 * Returns the value of the filter.
 * @returns {*}
 */
LABKEY.Query.Filter.prototype.getValue = function ()
{
    return this.value
};

/**
 * Returns the value that will be put on URL.
 * @returns {String}
 */
LABKEY.Query.Filter.prototype.getURLParameterValue = function ()
{
    return this.filterType.isDataValueRequired() ? this.value : ''
};

/**
 * Returns the URL parameter name used for the filter.
 * @param dataRegionName The dataRegionName the filter is associated with.
 * @returns {String}
 */
LABKEY.Query.Filter.prototype.getURLParameterName = function (dataRegionName)
{
    return (dataRegionName || "query") + "." + this.columnName + "~" + this.filterType.getURLSuffix();
};

LABKEY.Query.Filter.HasAnyValue = function (columnName)
{
    LABKEY.Query.Filter.call(this, columnName, null, LABKEY.Filter.Types.HAS_ANY_VALUE);
};
LABKEY.Query.Filter.HasAnyValue.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.Equal subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value matches the given filter value. Case-sensitivity depends upon how your
 *      underlying relational database was configured.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.Equal = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.EQUAL);
};
LABKEY.Query.Filter.Equal.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.DateEqual subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the date portion of a datetime column matches the filter value (ignoring the time portion).
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.DateEqual = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.DATE_EQUAL);
};
LABKEY.Query.Filter.DateEqual.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.DateNotEqual subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the date portion of a datetime column does not match the filter value (ignoring the time portion).
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.DateNotEqual = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.DATE_NOT_EQUAL);
};
LABKEY.Query.Filter.DateNotEqual.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.NotEqualOrNull subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value does not equal the filter value, or is missing (null).
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.NotEqualOrNull = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.NEQ_OR_NULL);
};
LABKEY.Query.Filter.NotEqualOrNull.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.NotEqualOrMissing subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value does not equal the filter value, or is missing (null).
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.NotEqualOrMissing = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.NOT_EQUAL_OR_MISSING);
};
LABKEY.Query.Filter.NotEqualOrMissing.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.NotEqual subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value does not equal the filter value.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.NotEqual = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.NOT_EQUAL);
};
LABKEY.Query.Filter.NotEqual.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.Neq subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value does not equal the filter value.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.Neq = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.NEQ);
};
LABKEY.Query.Filter.Neq.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.Neq subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value is blank.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @constructor
 */
LABKEY.Query.Filter.IsBlank = function (columnName)
{
    LABKEY.Query.Filter.call(this, columnName, null, LABKEY.Filter.Types.ISBLANK);
};
LABKEY.Query.Filter.IsBlank.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.Missing subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value is missing (null). Note that no filter value is required with this operator.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @constructor
 */
LABKEY.Query.Filter.Missing = function (columnName)
{
    LABKEY.Query.Filter.call(this, columnName, null, LABKEY.Filter.Types.MISSING);
};
LABKEY.Query.Filter.Missing.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.NonBlank subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value is not blank.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @constructor
 */
LABKEY.Query.Filter.NonBlank = function (columnName)
{
    LABKEY.Query.Filter.call(this, columnName, null, LABKEY.Filter.Types.NONBLANK);
};
LABKEY.Query.Filter.NonBlank.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.NotMissing subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value is not missing.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @constructor
 */
LABKEY.Query.Filter.NotMissing = function (columnName)
{
    LABKEY.Query.Filter.call(this, columnName, null, LABKEY.Filter.Types.NOT_MISSING);
};
LABKEY.Query.Filter.NotMissing.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.Gt subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value is greater than the filter value.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.Gt = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.GT);
};
LABKEY.Query.Filter.Gt.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.GreaterThan subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value is greater than the filter value.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.GreaterThan = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.GREATER_THAN);
};
LABKEY.Query.Filter.GreaterThan.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.Lt subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value is less than the filter value.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.Lt = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.LT);
};
LABKEY.Query.Filter.Lt.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.LessThan subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value is less than the filter value.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.LessThan = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.LESS_THAN);
};
LABKEY.Query.Filter.LessThan.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.DateLessThan subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the date portion of a datetime column is less than the filter value (ignoring the time portion).
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.DateLessThan = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.DATE_LESS_THAN);
};
LABKEY.Query.Filter.DateLessThan.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.Gte subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value is greater than or equal to the filter value.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.Gte = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.GTE);
};
LABKEY.Query.Filter.Gte.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.GreaterThanOrEqual subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value is greater than or equal to the filter value.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.GreaterThanOrEqual = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.GREATER_THAN_OR_EQUAL);
};
LABKEY.Query.Filter.GreaterThanOrEqual.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.GreaterThanOrEqual subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the date portion of a datetime column is greater than or equal to the filter value
 *      (ignoring the time portion).
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.DateGreaterThanOrEqual = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.DATE_GREATER_THAN_OR_EQUAL);
};
LABKEY.Query.Filter.DateGreaterThanOrEqual.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.Lte subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value is less than or equal to the filter value.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.Lte = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.LTE);
};
LABKEY.Query.Filter.Lte.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.LessThanOrEqual subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value is less than or equal to the filter value.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.LessThanOrEqual = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.LESS_THAN_OR_EQUAL);
};
LABKEY.Query.Filter.LessThanOrEqual.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.DateLessThanOrEqual subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the date portion of a datetime column is less than or equal to the filter value
 *      (ignoring the time portion).
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.DateLessThanOrEqual = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.DATE_LESS_THAN_OR_EQUAL);
};
LABKEY.Query.Filter.DateLessThanOrEqual.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.Contains subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value contains the filter value. Note that this may result in a slow query as this
 *      cannot use indexes.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.Contains = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.CONTAINS);
};
LABKEY.Query.Filter.Contains.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.DoesNotContain subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value does not contain the filter value. Note that this may result in a slow query
 *      as this cannot use indexes.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.DoesNotContain = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.DOES_NOT_CONTAIN);
};
LABKEY.Query.Filter.DoesNotContain.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.StartsWith subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value starts with the filter value.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.StartsWith = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.STARTS_WITH);
};
LABKEY.Query.Filter.StartsWith.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.In subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value equals one of the supplied filter values. The values should be supplied as a
 *      semi-colon-delimited list (example usage: a;b;c).
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.In = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.IN);
};
LABKEY.Query.Filter.In.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.EqualsOneOf subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value equals one of the supplied filter values. The values should be supplied as a
 *      semi-colon-delimited list (example usage: a;b;c).
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.EqualsOneOf = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.EQUALS_ONE_OF);
};
LABKEY.Query.Filter.EqualsOneOf.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.EqualsNoneOf subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value does not equal one of the supplied filter values. The values should be supplied as a
 *      semi-colon-delimited list (example usage: a;b;c).
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.EqualsNoneOf = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.EQUALS_NONE_OF);
};
LABKEY.Query.Filter.EqualsNoneOf.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.NotIn subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value is not in any of the supplied filter values. The values should be supplied as
 *      a semi-colon-delimited list (example usage: a;b;c).
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.NotIn = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.NOT_IN);
};
LABKEY.Query.Filter.NotIn.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.ContainsOneOf subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value contains any of the supplied filter values. The values should be supplied as a
 *      semi-colon-delimited list (example usage: a;b;c).
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.ContainsOneOf = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.CONTAINS_ONE_OF);
};
LABKEY.Query.Filter.ContainsOneOf.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.MemberOf subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value corresponds to a user that is a member of a group with the id of the supplied filter value.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion.
 * @constructor
 */
LABKEY.Query.Filter.MemberOf = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.MEMBER_OF);
};
LABKEY.Query.Filter.ContainsOneOf.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.ContainsNoneOf subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows where the column value does not contain any of the supplied filter values. The values should be supplied
 *      as a semi-colon-delimited list (example usage: a;b;c).
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @param value Value used as the filter criterion or an Array of values.
 * @constructor
 */
LABKEY.Query.Filter.ContainsNoneOf = function (columnName, value)
{
    LABKEY.Query.Filter.call(this, columnName, value, LABKEY.Filter.Types.CONTAINS_NONE_OF);
};
LABKEY.Query.Filter.ContainsNoneOf.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.HasMissingValue subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows that have a missing value indicator.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @constructor
 */
LABKEY.Query.Filter.HasMissingValue = function (columnName)
{
    LABKEY.Query.Filter.call(this, columnName, null, LABKEY.Filter.Types.HAS_MISSING_VALUE);
};
LABKEY.Query.Filter.HasMissingValue.prototype = new LABKEY.Query.Filter;

/**
 * @class LABKEY.Query.Filter.DoesNotHaveMissingValue subclass of {@link LABKEY.Query.Filter}.
 *      Finds rows that do not have a missing value indicator.
 * @augments LABKEY.Query.Filter
 * @param columnName Required. The name of the column the filter will be applied to. Can be a string, array of strings,
 * or a {@link LABKEY.FieldKey}
 * @constructor
 */
LABKEY.Query.Filter.DoesNotHaveMissingValue = function (columnName)
{
    LABKEY.Query.Filter.call(this, columnName, null, LABKEY.Filter.Types.DOES_NOT_HAVE_MISSING_VALUE);
};
LABKEY.Query.Filter.DoesNotHaveMissingValue.prototype = new LABKEY.Query.Filter;

(function(){
    /**
     * @class LABKEY.Query.Row The class used to wrap each row object returned from the server during a GetData, executeSql,
     * or selectRows request. Most users will not instantiate these themselves. Instead they will interact with them during
     * the success handler of the API they are using.
     * @see LABKEY.Query.Response
     * @param row The raw row from a GetData or executeSQL, selectRows (version 13.2 and above) request.
     * @constructor
     */
    LABKEY.Query.Row = function(row){
        this.links = null;

        if(row.links){
            this.links = row.links;
        }

        for (var attr in row.data) {
            if (row.data.hasOwnProperty(attr)) {
                this[attr] = row.data[attr];
            }
        }
    };

    /**
     * Gets the requested column from the row. Includes extended values such as display value, URL, etc.
     * When requested version is >16.2, multi-value columns will return an array of objects containing "value" and other properties.
     * In the "17.1" format, "formattedValue" may be included in the response as the column display value formatted with the display column's format or folder format settings.
     * @param {String} columnName The column name requested. Used to do a case-insensitive match to find the column.
     * @returns {Object} For the given columnName, returns an object in the common case or an array of objects for multi-value columns.
     * The object will always contain a property named "value" that is the column's value, but it may also contain other properties about that column's value. For
     * example, if the column was setup to track missing value information, it will also contain a property named mvValue
     * (which is the raw value that is considered suspect), and a property named mvIndicator, which will be the string MV
     * indicator (e.g., "Q").
     */
    LABKEY.Query.Row.prototype.get = function(columnName){
        columnName = columnName.toLowerCase();

        for (var attr in this) {
            if (attr.toLowerCase() === columnName && this.hasOwnProperty(attr) && !(this[attr] instanceof Function)) {
                return this[attr];
            }
        }

        return null;
    };

    /**
     * Gets the simple value for the requested column. Equivalent of doing Row.get(columnName).value.
     * For multi-value columns, the result is an array of values.
     * @param {String} columnName The column name requested. Used to do a case-insensitive match to find the column.
     * @returns {*} Returns the simple value for the given column.
     */
    LABKEY.Query.Row.prototype.getValue = function(columnName){
        columnName = columnName.toLowerCase();

        for (var attr in this) {
            if (attr.toLowerCase() === columnName && this.hasOwnProperty(attr) && !(this[attr] instanceof Function)) {
                if (LABKEY.Utils.isArray(this[attr])) {
                    return this[attr].map(function (i) { return i.value; });
                }
                if (this[attr].hasOwnProperty('value')) {
                    return this[attr].value;
                }
            }
        }

        return null;
    };

    /**
     * Gets all of the links for a row (details, update, etc.).
     * @returns {Object} Returns an object with all of the links types (details, update, etc.) for a row.
     */
    LABKEY.Query.Row.prototype.getLinks = function(){
        return this.links;
    };

    /**
     * Gets a specific link type for a row (details, update, etc.).
     * @param linkType Required. The name of the link type to be returned.
     * @returns {Object} Returns an object with the display text and link value.
     */
    LABKEY.Query.Row.prototype.getLink = function(linkType){
        if (this.links[linkType]) {
            return this.links[linkType];
        }

        return null;
    };

    /**
     * @private
     */
    var generateColumnModel = function(fields) {
        var i, columns = [];

        for (i = 0; i < fields.length; i++) {
            columns.push({
                scale: fields[i].scale,
                hidden: fields[i].hidden,
                sortable: fields[i].sortable,
                align: fields[i].align,
                width: fields[i].width,
                dataIndex: fields[i].fieldKey.toString(),
                required: fields[i].nullable, // Not sure if this is correct.
                editable: fields[i].userEditable,
                header: fields[i].shortCaption
            })
        }

        return columns;
    };

    /**
     * @private
     */
    var generateGetDisplayField = function(fieldKeyToFind, fields) {
        return function() {
            var fieldString = fieldKeyToFind.toString();
            for (var i = 0; i < fields.length; i++) {
                if (fieldString == fields[i].fieldKey.toString()) {
                    return fields[i];
                }
            }
            return null;
        };
    };

    /**
     * @class The class used to wrap the response object from {@link LABKEY.Query.GetData.getRawData},
     *      {@link LABKEY.Query.selectRows}, and {@link LABKEY.Query.executeSql}.
     * @param response The raw JSON response object returned from the server when executing {@link LABKEY.Query.GetData.getRawData},
     *      {@link LABKEY.Query.selectRows}, or {@link LABKEY.Query.executeSql} when requiredVersion is greater than 13.2.
     * @see LABKEY.Query.GetData.getRawData
     * @see LABKEY.Query.selectRows
     * @see LABKEY.Query.executeSql
     * @constructor
     */
    LABKEY.Query.Response = function(response) {
        // response = response;
        var i, attr;

        // Shallow copy the response.
        for (attr in response) {
            if (response.hasOwnProperty(attr)) {
                this[attr] = response[attr];
            }
        }

        // Wrap the Schema, Lookup, and Field Keys.
        this.schemaKey = LABKEY.SchemaKey.fromParts(response.schemaName);

        for (i = 0; i < response.metaData.fields.length; i++) {
            var field = response.metaData.fields[i],
                lookup = field.lookup;

            field.fieldKey = LABKEY.FieldKey.fromParts(field.fieldKey);

            if (lookup && lookup.schemaName) {
                lookup.schemaName = LABKEY.SchemaKey.fromParts(lookup.schemaName);
            }

            if (field.displayField) {
                field.displayField = LABKEY.FieldKey.fromParts(field.displayField);
                field.getDisplayField = generateGetDisplayField(field.displayField, response.metaData.fields);
            }

            // Only parse the 'extFormatFn' if ExtJS is present
            // checking to see if the fn ExtJS version and the window ExtJS version match
            if (field.extFormatFn) {
                var ext4Index = field.extFormatFn.indexOf('Ext4.'),
                    isExt4Fn = ext4Index == 0 || ext4Index == 1,
                    canEvalExt3 = !isExt4Fn && window && window.Ext !== undefined,
                    canEvalExt4 = isExt4Fn && window && window.Ext4 !== undefined;

                if (canEvalExt3 || canEvalExt4) {
                    field.extFormatFn = eval(field.extFormatFn);
                }
            }
        }

        // Generate Column Model
        this.columnModel = generateColumnModel(this.metaData.fields);

        // Wrap the rows -- may not be in the response (e.g. maxRows: 0)
        if (this.rows !== undefined) {
            for (i = 0; i < this.rows.length; i++) {
                this.rows[i] = new LABKEY.Query.Row(this.rows[i]);
            }
        }
        else {
            this.rows = [];
        }

        return this;
    };

    /**
     * Gets the metaData object from the response.
     * @returns {Object} Returns an object with the following properties:
     * <ul>
     *     <li><strong>fields</strong>: {Object[]}
     *     Each field has the following properties:
     *          <ul>
     *              <li><strong>name</strong>: {String} The name of the field</li>
     *              <li><strong>type</strong>: {String} JavaScript type name of the field</li>
     *              <li><strong>shownInInsertView</strong>: {Boolean} whether this field is intended to be shown in insert views</li>
     *              <li><strong>shownInUpdateView</strong>: {Boolean} whether this field is intended to be shown in update views</li>
     *              <li><strong>shownInDetailsView</strong>: {Boolean} whether this field is intended to be shown in details views</li>
     *              <li>
     *                  <strong>measure</strong>: {Boolean} whether this field is a measure.  Measures are fields that contain data
     *                  subject to charting and other analysis.
     *              </li>
     *              <li>
     *                  <strong>dimension</strong>: {Boolean} whether this field is a dimension.  Data dimensions define logical groupings
     *                  of measures.
     *              </li>
     *              <li><strong>hidden</strong>: {Boolean} whether this field is hidden and not normally shown in grid views</li>
     *              <li><strong>lookup</strong>: {Object} If the field is a lookup, there will
     *                  be four sub-properties listed under this property:
     *                  schema, table, displayColumn, and keyColumn, which describe the schema, table, and
     *                  display column, and key column of the lookup table (query).
     *              </li>
     *              <li>
     *                  <strong>displayField</strong>: {{@link LABKEY.FieldKey}} If the field has a display field this is
     *                  the field key for that field.
     *              </li>
     *              <li>
     *                  <strong>getDisplayField</strong>: {Function} If the field has a display field this function will
     *                  return the metadata field object for that field.
     *              </li>
     *          </ul>
     *     </li>
     *
     *     <li><strong>id</strong>: Name of the primary key column.</li>
     *     <li>
     *         <strong>root</strong>: Name of the property containing rows ("rows"). This is mainly for the Ext
     *         grid component.
     *     </li>
     *     <li><strong>title</strong>:</li>
     *     <li>
     *         <strong>totalProperty</strong>: Name of the top-level property containing the row count ("rowCount") in our case.
     *         This is mainly for the Ext grid component.
     *     </li>
     * </ul>
     */
    LABKEY.Query.Response.prototype.getMetaData = function() {
        return this.metaData;
    };

    /**
     * Returns the schema name from the Response.
     * @param {Boolean} asString
     * @returns {*} If asString is true it returns a string, otherwise it returns a {@link LABKEY.FieldKey} object.
     */
    LABKEY.Query.Response.prototype.getSchemaName = function(asString) {
        return asString ? this.schemaKey.toString() : this.schemaName;
    };

    /**
     * Returns the query name from the Response.
     * @returns {String}
     */
    LABKEY.Query.Response.prototype.getQueryName = function() {
        return this.queryName;
    };

    /**
     * Returns an array of objects that can be used to assist in creating grids using ExtJs.
     * @returns {Array} Returns an array of Objects that can be used to assist in creating Ext Grids to
     *      render the data.
     */
    LABKEY.Query.Response.prototype.getColumnModel = function() {
        return this.columnModel;
    };

    /**
     * Returns the array of row objects.
     * @returns {Array} Returns an array of {@link LABKEY.Query.Row} objects.
     */
    LABKEY.Query.Response.prototype.getRows = function() {
        return this.rows;
    };

    /**
     * Get a specific row from the row array.
     * @param {Integer} idx The index of the row you need.
     * @returns {LABKEY.Query.Row}
     */
    LABKEY.Query.Response.prototype.getRow = function(idx) {
        if (this.rows[idx] !== undefined) {
            return this.rows[idx];
        }

        throw new Error('No row found for index ' + idx);
    };

    /**
     * Gets the row count from the response, which is the total number of rows in the query, not necessarily the number
     * of rows returned. For example, if setting maxRows to 100 on a query that has 5,000 rows, getRowCount will return
     * 5,000, not 100.
     * @returns {Integer}
     */
    LABKEY.Query.Response.prototype.getRowCount = function() {
        return this.rowCount;
    };
})();

/**
* @name LABKEY.Query.ModifyRowsOptions
* @class   ModifyRowsOptions class to describe
            the third object passed to the successCallback function
            by {@link LABKEY.Query.updateRows}, {@link LABKEY.Query.insertRows} or
            {@link LABKEY.Query.deleteRows}. This object's properties are useful for
            matching requests to responses, as HTTP requests are typically
            processed asynchronously.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *              </ul>
 *           </p>
  * @see LABKEY.Query.updateRows
  * @see LABKEY.Query.insertRows
  * @see LABKEY.Query.deleteRows
*/

/**#@+
* @memberOf LABKEY.Query.ModifyRowsOptions#
* @field
*/

/**
* @name headers
* @description  An object containing one property for each HTTP header sent to the server.
* @type Object
*/

/**
* @name method
* @description The HTTP method used for the request (typically 'GET' or 'POST').
* @type String
 */

/**
* @name url
* @description  The URL that was requested.
* @type String
*/

/**
* @name jsonData
* @description  The data object sent to the server. This will contain the following properties:
          <ul>
            <li><b>schemaName</b>: String. The schema name being modified.  This is the same schemaName
                the client passed to the calling function. </li>
            <li><b>queryName</b>: String. The query name being modified. This is the same queryName
                the client passed to the calling function.  </li>
            <li><b>rows</b>: Object[]. Array of row objects that map the names of the row fields to their values.
            The fields required for inclusion for each row depend on the which LABKEY.Query method you are
            using (updateRows, insertRows or deleteRows). </li>
         </ul>
 <p/>
 <b>For {@link LABKEY.Query.updateRows}:</b> <p/>
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
 <b>For {@link LABKEY.Query.insertRows}:</b> <p/>
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
 <b>For {@link LABKEY.Query.deleteRows}:</b> <p/>
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
* @type  Object
*/

/**#@-*/

 /**
* @name LABKEY.Query.ModifyRowsResults
* @class  ModifyRowsResults class to describe
            the first object passed to the successCallback function
            by {@link LABKEY.Query.updateRows}, {@link LABKEY.Query.insertRows} or
            {@link LABKEY.Query.deleteRows}. This object's properties are useful for
            matching requests to responses, as HTTP requests are typically
            processed asynchronously.
  *            <p>Additional Documentation:
  *              <ul>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
  *                      How To Find schemaName, queryName &amp; viewName</a></li>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
  *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
  *              </ul>
  *           </p>
* @example For example:
        <pre name="code" class="xml">
        {  "schemaName": "lists",
           "queryName": "API Test List"
           "rowsAffected": 1,
           "command": "insert",
           "errors": [],
           "rows": [{ Key: 3, StringField: 'NewValue'}]
        } </pre></code>
* @see LABKEY.Query.updateRows
* @see LABKEY.Query.insertRows
* @see LABKEY.Query.deleteRows
* @see LABKEY.Query.saveRows
*/

/**#@+
* @memberOf LABKEY.Query.ModifyRowsResults#
* @field
*/

/**
* @name LABKEY.Query.ModifyRowsResults#schemaName
* @description  Contains the same schemaName the client passed to the calling function.
* @type  String
*/

/**
* @name     LABKEY.Query.ModifyRowsResults#queryName
* @description  Contains the same queryName the client passed to the calling function.
* @type     String
*/

/**
* @name    command
* @description   Will be "update", "insert", or "delete" depending on the API called.
* @type    String
*/

/**
* @name    errors
* @description   Objects will contain the properties 'id' (the field to which the error is related, if any),
*                and 'msg' (the error message itself).
* @type    Array
*/

/**
* @name  rowsAffected
* @description  Indicates the number of rows affected by the API action.
            This will typically be the same number of rows passed in to the calling function.
* @type        Integer
*/

/**
* @name LABKEY.Query.ModifyRowsResults#rows
* @description   Array of rows with field values for the rows updated, inserted,
            or deleted, in the same order as the rows supplied in the request. For insert, the
            new key value for an auto-increment key will be in the returned row's field values.
            For insert or update, the other field values may also be different than those supplied
            as a result of database default expressions, triggers, or LabKey's automatic tracking
            feature, which automatically adjusts columns of certain names (e.g., Created, CreatedBy,
            Modified, ModifiedBy, etc.).
* @type    Object[]
*/

/**#@-*/

/**
* @name LABKEY.Query.SelectRowsOptions
* @class  SelectRowsOptions class to describe
           the third object passed to the successCallback function by
           {@link LABKEY.Query.selectRows}.  This object's properties are useful for
           matching requests to responses, as HTTP requests are typically
           processed asynchronously.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *              </ul>
 *           </p>
* @see LABKEY.Query.selectRows
*/

/**#@+
* @memberOf LABKEY.Query.SelectRowsOptions#
* @field
*/

/**
* @name   query.schemaName
* @description   Contains the same schemaName the client passed to the
            calling function. See <a class="link"
            href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
            How To Find schemaName, queryName &amp; viewName</a>.
* @type   String
*/

/**
* @name   query.queryName
* @description   Contains the same queryName the client passed
            to the calling function. See
            <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
            How To Find schemaName, queryName &amp; viewName</a>.
* @type   String
*/

/**
* @name    query.viewName
* @description 	Name of a valid custom view for the chosen queryName. See
            <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
            How To Find schemaName, queryName &amp; viewName</a>.
* @type    String
*/

/**
* @name    query.offset
* @description  Row number at which results should begin.
            Use this with maxRows to get pages of results.
* @type   Integer
*/

/**
* @name   query.sort
* @description	Sort specification. This can be a comma-delimited list of
            column names, where each column may have an optional dash (-) before the name
            to indicate a descending sort.
* @type  String
*/

/**
* @name   maxRows
* @description   Maximum number of rows to return
* @type     Integer
*/

/**
* @name   filters
* @description   An object whose properties are filter specifications, one for each filter you supplied.
 *          All filters are combined using AND logic. Each one is of type {@link LABKEY.Filter.FilterDefinition}.
 *          The list of valid operators:
            <ul><li><b>eq</b> = equals</li>
            <li><b>neq</b> = not equals</li>
            <li><b>gt</b> = greater-than</li>
            <li><b>gte</b> = greater-than or equal-to</li>
            <li><b>lt</b> = less-than</li>
            <li><b>lte</b> = less-than or equal-to</li>
            <li><b>dateeq</b> = date equal</li>
            <li><b>dateneq</b> = date not equal</li>
            <li><b>neqornull</b> = not equal or null</li>
            <li><b>isblank</b> = is null</li>
            <li><b>isnonblank</b> = is not null</li>
            <li><b>contains</b> = contains</li>
            <li><b>doesnotcontain</b> = does not contain</li>
            <li><b>startswith</b> = starts with</li>
            <li><b>doesnotstartwith</b> = does not start with</li>
            <li><b>in</b> = equals one of</li></ul>
* @type    Object
*/

/**#@-*/

/**
* @name LABKEY.Query.FieldMetaDataLookup
* @class  Lookup metadata about a single field.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
 *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
 *              </ul>
 *           </p>
 * @see LABKEY.Query.FieldMetaData
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaDataLookup#
* @field
* @name    containerPath
* @description The path to the container that this lookup points to, if not the current container
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaDataLookup#
* @field
* @name    public
* @description Whether the target of this lookup is exposed as a top-level query
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaDataLookup#
* @field
* @name queryName
* @description The name of the query that this lookup targets
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaDataLookup#
* @field
* @name schemaName
* @description The name of the schema that this lookup targets
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaDataLookup#
* @field
* @name keyColumn
* @description The name of column in the lookup's target that will be joined to the value in the local field
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaDataLookup#
* @field
* @name displayColumn
* @description The name of the column in the lookup's target that will be shown as its value, instead of the raw key value
* @type    String
*/

/**#@-*/

/**
* @name LABKEY.Query.FieldMetaData
* @class  Metadata about a single field.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
 *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
 *              </ul>
 *           </p>
 * @see LABKEY.Query.selectRows
 * @see LABKEY.Query.getQueryDetails
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name    name
* @description The name of the field
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name    friendlyType
* @description A friendlier, more verbose description of the type, like "Text (String)" or "Date and Time"
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name    shownInInsertView
* @description Whether this field is intended to be displayed in insert UIs
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name    shownInDetailView
* @description Whether this field is intended to be displayed in detail UIs
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name    shownInUpdateView
* @description Whether this field is intended to be displayed in update UIs
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name    versionField
* @description Whether this field's value stores version information for the row
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name userEditable
* @description Whether this field is intended to be edited directly by the user, or managed by the system
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name calculated
* @description Whether this field is a calculated value such as something generated by a SQL expression,
* or a "real"/"physical" column in the database
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name readOnly
* @description Whether the field's value can be modified
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name nullable
* @description Whether the field's value is allowed to be null
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name mvEnabled
* @description Whether this field supports missing value indicators instead of or addition to its standard value
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name keyField
* @description Whether this field is part of the row's primary key
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name hidden
* @description Whether this value is intended to be hidden from the user, especially for grid views
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name autoIncrement
* @description Whether this field's value is automatically assigned by the server, like a RowId whose value is determined by a database sequence
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name jsonType
* @description The type of JSON object that will represent this field's value: string, boolean, date, int, or float
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name importAliases
* @description Alternate names for this field that may appear in data when importing, whose values should be mapped to this field
* @type    String[]
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name tsvFormat
* @description The format string to be used for TSV exports
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name format
* @description The format string to be used for generating HTML
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name excelFormat
* @description The format string to be used for Excel exports
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name extFormat
* @description The format string that can be passed to Ext components.  This is currently only supported for dates.
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name extFormatFn
* @description A function that can be used to produce the formatted string for this field.  This is currently supported for dates and numeric values.
* Note: this function is returned as a string, so you will need to evaluate it to convert it to a function.  See example below.
* @example <pre name="code" class="xml">
* var formatFn = eval(meta.extFormatFn);
* var formattedValue = formatFn(data);
* </pre></code>
*
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name caption
* @description The caption to be shown for this field, typically in a column header, which may differ from its name
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name shortCaption
* @description The caption for this field, without any prefix from potential parent lookups. In many cases this will be identical to the caption property.
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name description
* @description The description for this field
* @type    String
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name inputType
* @description The type of form input to be used when editing this field, such as select, text, textarea, checkbox, or file
* @type    boolean
*/

/**#@+
* @memberOf LABKEY.Query.FieldMetaData#
* @field
* @name lookup
* @description Information about this field's lookup configuration
* @type    LABKEY.Query.FieldMetaDataLookup
*/

/**#@-*/

/**
* @name LABKEY.Query.SelectRowsResults
* @class  SelectRowsResults class to describe the first
            object passed to the successCallback function by
            {@link LABKEY.Query.selectRows}. This object's properties are useful for
            matching requests to responses, as HTTP requests are typically
            processed asynchronously.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
 *                      How To Find schemaName, queryName &amp; viewName</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=javascriptTutorial">LabKey JavaScript API Tutorial</a> and
 *                      <a href="https://www.labkey.org/wiki/home/Study/demo/page.view?name=reagentRequest">Demo</a></li>
 *              </ul>
 *           </p>
* @see LABKEY.Query.selectRows
*/

/**#@+
* @memberOf LABKEY.Query.SelectRowsResults#
* @field
*/

/**
* @name    schemaName
* @description the name of the resultset's source schema.
* @type    String
*/

/**
* @name    queryName
* @description the name of the resultset's source query.  In some cases, such as an 'executeSql' call with 'saveInSession' set to true, the
 * query name may refer to temporary query that can be used to re-retrieve data for the duration of the user's session. 
* @type    String
*/

/**
* @name    metaData
* @description Contains type and lookup information about the columns in the resultset.
* @type    Object
*/

/**
* @name    metaData.root
* @description 	Name of the property containing rows ("rows"). This is mainly for the Ext grid component.
* @type     String
*/

/**
* @name    metaData.totalProperty
* @description 	Name of the top-level property
            containing the row count ("rowCount") in our case. This is mainly
            for the Ext grid component.
* @type   String
*/

/**
* @name   metaData.sortInfo
* @description  Sort specification in Ext grid terms.
            This contains two sub-properties, field and direction, which indicate
            the sort field and direction ("ASC" or "DESC") respectively.
* @type   Object
*/

/**
* @name    metaData.id
* @description  Name of the primary key column.
* @type     String
*/

/**
* @name    metaData.fields
* @description	Array of field information.
* @type    LABKEY.Query.FieldMetaData[]
*/

/**
* @name    columnModel
* @description   Contains information about how one may interact
            with the columns within a user interface. This format is generated
            to match the requirements of the Ext grid component. See
            <a href="http://extjs.com/deploy/ext-2.2.1/docs/?class=Ext.grid.ColumnModel">
            Ext.grid.ColumnModel</a> for further information.
* @type  Object[]
*/

/**
* @name   rows
* @description    An array of rows, each of which is a
            sub-element/object containing a property per column.
* @type   Object[]
*/

/**
* @name rowCount
* @description Indicates the number of total rows that could be
            returned by the query, which may be more than the number of objects
            in the rows array if the client supplied a value for the query.maxRows
            or query.offset parameters. This value is useful for clients that wish
            to display paging UI, such as the Ext grid.
* @type   Integer
*/

/**#@-*/

 /**
* @name LABKEY.Query.ExtendedSelectRowsResults
* @class  ExtendedSelectRowsResults class to describe the first
            object passed to the successCallback function by
            {@link LABKEY.Query.selectRows} if config.requiredVersion is set to "9.1".
  *            <p>Additional Documentation:
  *              <ul>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
  *                      How To Find schemaName, queryName &amp; viewName</a></li>
  *              </ul>
  *           </p>
* @see LABKEY.Query.selectRows
 */

/**#@+
* @memberOf LABKEY.Query.ExtendedSelectRowsResults#
* @field
*/

/**
* @name    LABKEY.Query.ExtendedSelectRowsResults#metaData
* @description Contains type and lookup information about the columns in the resultset.
* @type    Object
*/

/**
* @name    LABKEY.Query.ExtendedSelectRowsResults#metaData.root
* @description 	Name of the property containing rows ("rows"). This is mainly for the Ext grid component.
* @type     String
*/

/**
* @name    LABKEY.Query.ExtendedSelectRowsResults#metaData.totalProperty
* @description 	Name of the top-level property
            containing the row count ("rowCount") in our case. This is mainly
            for the Ext grid component.
* @type   String
*/

/**
* @name   LABKEY.Query.ExtendedSelectRowsResults#metaData.sortInfo
* @description  Sort specification in Ext grid terms.
            This contains two sub-properties, field and direction, which indicate
            the sort field and direction ("ASC" or "DESC") respectively.
* @type   Object
*/

/**
* @name    LABKEY.Query.ExtendedSelectRowsResults#metaData.id
* @description  Name of the primary key column.
* @type     String
*/

/**
* @name    LABKEY.Query.ExtendedSelectRowsResults#metaData.fields
* @description	Array of field information.
            Each field has the following properties:
            <ul><li><b>name</b> -- The name of the field</li>
            <li><b>type</b> -- JavaScript type name of the field</li>
            <li><b>shownInInsertView</b> -- whether this field is intended to be shown in insert views</li>
            <li><b>shownInUpdateView</b> -- whether this field is intended to be shown in update views</li>
            <li><b>shownInDetailsView</b> -- whether this field is intended to be shown in details views</li>
            <li><b>measure</b> -- whether this field is a measure.  Measures are fields that contain data subject to charting and other analysis.</li>
            <li><b>dimension</b> -- whether this field is a dimension.  Data dimensions define logical groupings of measures.</li>
            <li><b>hidden</b> -- whether this field is hidden and not normally shown in grid views</li>
            <li><b>lookup</b> -- If the field is a lookup, there will
                be four sub-properties listed under this property:
                schema, table, displayColumn, and keyColumn, which describe the schema, table, and
                display column, and key column of the lookup table (query).</li></ul>
* @type    Object[]
*/

/**
* @name    LABKEY.Query.ExtendedSelectRowsResults#columnModel
* @description   Contains information about how one may interact
            with the columns within a user interface. This format is generated
            to match the requirements of the Ext grid component. See
            <a href="http://extjs.com/deploy/ext-2.2.1/docs/?class=Ext.grid.ColumnModel">
            Ext.grid.ColumnModel</a> for further information.
* @type  String
*/

/**
* @name   LABKEY.Query.ExtendedSelectRowsResults#rows
* @description    An array of rows, each of which is a
            sub-element/object containing an object per column. The
            object will always contain a property named "value" that is the
            column's value, but it may also contain other properties about
            that column's value. For example, if the column was setup to track
            missing value information, it will also contain a property named mvValue (which
            is the raw value that is considered suspect), and a property named
            mvIndicator, which will be the string MV indicator (e.g., "Q").
* @type   Object[]
*/

/**
* @name LABKEY.Query.ExtendedSelectRowsResults#rowCount
* @description Indicates the number of total rows that could be
            returned by the query, which may be more than the number of objects
            in the rows array if the client supplied a value for the query.maxRows
            or query.offset parameters. This value is useful for clients who wish
            to display paging UI, such as the Ext grid.
* @type   Integer
*/

/**#@-*/

/** docs for methods defined in dom/Query.js - primarily here to ensure API docs get generated with combined core/dom versions */

/**
 * Execute arbitrary LabKey SQL and export the results to Excel or TSV. After this method is
 * called, the user will be prompted to accept a file from the server, and most browsers will allow
 * the user to either save it or open it in an apporpriate application.
 * For more information, see the
 * <a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=labkeySql">
 * LabKey SQL Reference</a>.
 *
 * @memberOf LABKEY.Query
 * @function
 * @static
 * @name exportSql
 * @param config An object which contains the following configuration properties.
 * @param {String} config.schemaName name of the schema to query.
 * @param {String} config.sql The LabKey SQL to execute.
 * @param {String} [config.format] The desired export format. May be either 'excel' or 'tsv'. Defaults to 'excel'.
 * @param {String} [config.containerPath] The path to the container in which the schema and query are defined,
 *       if different than the current container. If not supplied, the current container's path will be used.
 * @param {String} [config.containerFilter] One of the values of {@link LABKEY.Query.containerFilter} that sets
 *       the scope of this query. Defaults to containerFilter.current, and is interpreted relative to
 *       config.containerPath.
 */
