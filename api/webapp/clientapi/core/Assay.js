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
 * @namespace Assay static class to retrieve read-only assay definitions.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=createDatasetViaAssay">LabKey Assays</a></li>
 *              </ul>
 *           </p>
 * @see LABKEY.Experiment
 */
LABKEY.Assay = new function()
{
    function getAssays(config)
    {
        //check for old-style separate arguments
        if(arguments.length > 1) {
            config = {
                success: arguments[0],
                failure: arguments[1],
                parameters: arguments[2],
                containerPath: arguments[3]
            };
        }

        moveParameter(config, "id");
        moveParameter(config, "type");
        moveParameter(config, "name");

        LABKEY.Ajax.request({
            url : LABKEY.ActionURL.buildURL("assay", "assayList", config.containerPath),
            method : 'POST',
            success: LABKEY.Utils.getOnSuccess(config),
            failure: LABKEY.Utils.getOnFailure(config),
            scope: config.scope || this,
            jsonData : config.parameters,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    function getSuccessCallbackWrapper(successCallback, scope)
    {
        return LABKEY.Utils.getCallbackWrapper(function(data, response){
            if(successCallback)
                successCallback.call(this, data.definitions, response);
        }, (scope || this));
    }

    function moveParameter(config, param)
    {
        if (!config.parameters) config.parameters = {};
        if (config[param])
        {
            config.parameters[param] = config[param];
            delete config[param];
        }
    }

    /** @scope LABKEY.Assay */
    return {

	/**
	* Gets all assays.
	* @param {Function} config.success Required. Function called when the
			"getAll" function executes successfully.  Will be called with the argument: 
			{@link LABKEY.Assay.AssayDesign[]}.
    * @param {Object} config An object which contains the following configuration properties.
	* @param {Object} [config.scope] The scope to be used for the success and failure callbacks
    * @param {Function} [config.failure] Function called when execution of the "getAll" function fails.
	* @param {String} [config.containerPath] The container path in which the requested Assays are defined.
	*       If not supplied, the current container path will be used.
	* @example Example:
<pre name="code" class="xml">
&lt;script type="text/javascript"&gt;
	function successHandler(assayArray) 
	{ 
		var html = ''; 
		for (var defIndex = 0; defIndex < assayArray.length; defIndex ++) 
		{ 
			var definition = assayArray[defIndex ]; 
			html += '&lt;b&gt;' + definition.type + '&lt;/b&gt;: ' 
				+ definition.name + '&lt;br&gt;'; 
			for (var domain in definition.domains) 
			{ 
				html += '&nbsp;&nbsp;&nbsp;' + domain + '&lt;br&gt;'; 
				var properties = definition.domains[domain]; 
				for (var propertyIndex = 0; propertyIndex 
					< properties.length; propertyIndex++) 
				{ 
					var property = properties[propertyIndex]; 
					html += '&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;' + property.name + 
						' - ' + property.typeName + '&lt;br&gt;'; 
				} 
			} 
		} 
		document.getElementById('testDiv').innerHTML = html; 
	} 

	function errorHandler(error) 
	{ 
		alert('An error occurred retrieving data.'); 
	}
	
	LABKEY.Assay.getAll({success: successHandler, failure: errorHandler});
&lt;/script&gt;
&lt;div id='testDiv'&gt;Loading...&lt;/div&gt;
</pre>
	  * @see LABKEY.Assay.AssayDesign
	  */
        getAll : function(config)
        {
            if(arguments.length > 1) {
                config = {
                    success: arguments[0],
                    failure: arguments[1],
                    parameters: {},
                    containerPath: arguments[2]
                };
            }

            config.success = getSuccessCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope);
            getAssays(config);
        },
	  /**
	  * Gets an assay by name.
	  * @param {Function(LABKEY.Assay.AssayDesign[])} config.success Function called when the "getByName" function executes successfully.
      * @param {Object} config An object which contains the following configuration properties.
	  * @param {Function} [config.failure] Function called when execution of the "getByName" function fails.
	  * @param {Object} [config.scope] The scope to be used for the success and failure callbacks
      * @param {String} [config.name] String name of the assay.
	  * @param {String} [config.containerPath] The container path in which the requested Assay is defined.
	  *       If not supplied, the current container path will be used.
	  * @see LABKEY.Assay.AssayDesign
	  */
        getByName : function(config)
        {
            if(arguments.length > 1) {
                config = {
                    success: arguments[0],
                    failure: arguments[1],
                    parameters: { name: arguments[2] },
                    containerPath: arguments[3]
                };
            }

            moveParameter(config, "name");
            config.success = getSuccessCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope);
            getAssays(config);
        },

	  /**
	  * Gets an assay by type.
	  * @param {Function(LABKEY.Assay.AssayDesign[])} config.success Function called
				when the "getByType" function executes successfully.
      * @param {Object} config An object which contains the following configuration properties.
	  * @param {Function} [config.failure] Function called when execution of the "getByType" function fails.
	  * @param {Object} [config.scope] The scope to be used for the success and failure callbacks
      * @param {String} config.type String name of the assay type.  "ELISpot", for example.
	  * @param {String} [config.containerPath] The container path in which the requested Assays are defined.
	  *       If not supplied, the current container path will be used.
 	  * @see LABKEY.Assay.AssayDesign
	  */
        getByType : function(config)
        {
            if(arguments.length > 1) {
                config = {
                    success: arguments[0],
                    failure: arguments[1],
                    parameters: { type: arguments[2] },
                    containerPath: arguments[3]
                };
            }

            moveParameter(config, "type");
            config.success = getSuccessCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope);
            getAssays(config);
        },

	 /**
	 * Gets an assay by its ID.
	 * @param {Function(LABKEY.Assay.AssayDesign[])} config.success Function called
				when the "getById" function executes successfully.
     * @param {Object} config An object which contains the following configuration properties.
	 * @param {Function} [config.failure] Function called when execution of the "getById" function fails.
	 * @param {Object} [config.scope] The scope to be used for the success and failure callbacks
     * @param {Integer} config.id Unique integer ID for the assay.
	 * @param {String} [config.containerPath] The container path in which the requested Assay is defined.
	 *       If not supplied, the current container path will be used.
	 * @see LABKEY.Assay.AssayDesign
	 */
        getById : function(config)
        {
            if(arguments.length > 1) {
                config = {
                    success: arguments[0],
                    failure: arguments[1],
                    parameters: { id: arguments[2] },
                    containerPath: arguments[3]
                };
            }

            moveParameter(config, "id");
            config.success = getSuccessCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope);
            getAssays(config);
        },


        /**
        * Select NAb assay data from an assay folder.
        * @param {Object} config An object which contains the following configuration properties.
         * @param {String} config.assayName  The name of the NAb assay design for which runs are to be retrieved.
         * @param {Boolean} [config.includeStats]  Whether or not statistics (standard deviation, max, min, etc.) should
         * be returned with calculations and well data.
         * @param {Boolean} [config.includeWells]  Whether well-level data should be included in the response.
         * @param {Boolean} [config.calculateNeut]  Whether neutralization should be calculated on the server.
         * @param {Boolean} [config.includeFitParameters]  Whether the parameters used in the neutralization curve fitting calculation
         * should be included in the response.
        * @param {Function} config.success
                Function called when the "getNAbRuns" function executes successfully.
                This function will be called with the following arguments:
                <ul>
                    <li>runs: an array of NAb run objects</li>
                    <li>options: the options used for the AJAX request</li>
                    <li>responseObj: the XMLHttpResponseObject instance used to make the AJAX request</li>
                </ul>
        * @param {Function} [config.failure] Function called when execution of the "getNAbRuns" function fails.
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
        *
        * @param {Array} [config.filterArray] Array of objects created by {@link LABKEY.Filter.create}.
        * @param {String} [config.sort]  String description of the sort.  It includes the column names
        *       listed in the URL of a sorted data region (with an optional minus prefix to indicate
        *       descending order). In the case of a multi-column sort, up to three column names can be
        *       included, separated by commas.
        * @param {String} [config.containerPath] The path to the container in which the schema and query are defined,
        *       if different than the current container. If not supplied, the current container's path will be used.
        * @param {Integer} [config.maxRows] The maximum number of runs to return from the server (defaults to 100).
        *        If you want to return all possible rows, set this config property to -1.
        * @param {Integer} [config.offset] The index of the first row to return from the server (defaults to 0).
        *        Use this along with the maxRows config property to request pages of data.
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        */
        getNAbRuns : function(config)
        {
            var dataObject = {};

            LABKEY.Utils.merge(dataObject, config);
            if (config.sort)
                dataObject['query.sort'] = config.sort;
            if(config.offset)
                dataObject['query.offset'] = config.offset;
            if(config.maxRows)
            {
                if(config.maxRows < 0)
                    dataObject['query.showRows'] = "all";
                else
                    dataObject['query.maxRows'] = config.maxRows;
            }

            LABKEY.Filter.appendFilterParams(dataObject, config.filterArray);

            var successCallback = LABKEY.Utils.getOnSuccess(config);

            var requestConfig = {
                url : LABKEY.ActionURL.buildURL('nabassay', 'getNabRuns', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(function(data, response){
                    if(successCallback)
                        successCallback.call(config.scope, data.runs);
                }, this),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                params : dataObject
            };

            if (LABKEY.Utils.isDefined(config.timeout))
                requestConfig.timeout = config.timeout;

            LABKEY.Ajax.request(requestConfig);
        },


        /**
        * Select detailed NAb information for runs with summary data that has been copied to a study folder.  Note that this
         * method must be executed against the study folder containing the copied NAb summary data.
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Array} config.objectIds The object Ids for the NAb data rows that have been copied to the study.
         * @param {Boolean} [config.includeStats]  Whether or not statistics (standard deviation, max, min, etc.) should
         * be returned with calculations and well data.
         * @param {Boolean} [config.includeWells]  Whether well-level data should be included in the response.
         * @param {Boolean} [config.calculateNeut]  Whether neutralization should be calculated on the server.
         * @param {Boolean} [config.includeFitParameters]  Whether the parameters used in the neutralization curve fitting calculation
         * should be included in the response.
         * @param {String} [config.containerPath] The path to the study container containing the NAb summary,
         *       if different than the current container. If not supplied, the current container's path will be used.
         * @param {Function} config.success
                Function called when the "getStudyNabRuns" function executes successfully.
                This function will be called with the following arguments:
                <ul>
                    <li>runs: an array of NAb run objects</li>
                </ul>
        * @param {Function} [config.failure] Function called when execution of the "getStudyNabRuns" function fails.
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
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        */
        getStudyNabRuns : function(config)
        {
            var dataObject = {};

            LABKEY.Utils.merge(dataObject, config);

            var successCallback = LABKEY.Utils.getOnSuccess(config);

            var requestConfig = {
                url : LABKEY.ActionURL.buildURL('nabassay', 'getStudyNabRuns', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(function(data, response){
                    if(successCallback)
                        successCallback.call(config.scope, data.runs);
                }, this),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config) || LABKEY.Utils.displayAjaxErrorResponse, config.scope, true),
                params : dataObject
            };

            if (LABKEY.Utils.isDefined(config.timeout))
                requestConfig.timeout = config.timeout;

            LABKEY.Ajax.request(requestConfig);
        },

        /**
        * Retrieve the URL of an image that contains a graph of dilution curves for NAb results that have been copied to a study.
         * Note that this method must be executed against the study folder containing the copied NAb summary data.
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Array} config.objectIds The object Ids for the NAb data rows that have been copied to the study.
         * This method will ignore requests to graph any object IDs that the current user does not have permission to view.
         * @param {String} config.captionColumn The data column that should be used for per-specimen captions.  If the column
         * doesn't exist, or if it has a null value, a sensible default will be chosen, generally specimen ID or participant/visit.
         * @param {String} [config.chartTitle] The desired title for the chart. Defaults to no title.
         * @param {String} [config.fitType] Allowable values are FIVE_PARAMETER, FOUR_PARAMETER, and POLYNOMIAL.
         * Defaults to FIVE_PARAMETER.
         * @param {String} [config.height] Desired height of the graph image in pixels. Defaults to 300.
         * @param {String} [config.width] Desired width of the graph image in pixels. Defaults to 425.
         * @param {String} [config.containerPath] The path to the study container containing the NAb summary data,
         *       if different than the current container. If not supplied, the current container's path will be used.
         * @param {Function} config.success
                Function called when the "getStudyNabGraphURL" function executes successfully.
                This function will be called with the following arguments:
                <ul>
                    <li>result: an object with the following properties:
                        <ul>
                         <li>url: a string URL of the dilution curve graph.</li>
                         <li>objectIds: an array containing the IDs of the samples that were successfully graphed.</li>
                        </ul>
                    </li>
                    <li>responseObj: the XMLHttpResponseObject instance used to make the AJAX request</li>
                    <li>options: the options used for the AJAX request</li>
                </ul>
        * @param {Function} [config.failure] Function called when execution of the "getStudyNabGraphURL" function fails.
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
        * @param {Integer} [config.timeout] The maximum number of milliseconds to allow for this operation before
        *       generating a timeout error (defaults to 30000).
        * @example Example:
<pre name="code" class="xml">
&lt;script type="text/javascript"&gt;
    function showGraph(data)
    {
        var el = document.getElementById("graphDiv");
        if (data.objectIds && data.objectIds.length &gt; 0)
            el.innerHTML = '&lt;img src=\"' + data.url + '\"&gt;';
        else
            el.innerHTML = 'No graph available.  Insufficient permissions, ' +
                           'or no matching results were found.';
    }

    function initiateGraph(ids)
    {
        LABKEY.Assay.getStudyNabGraphURL({
            objectIds: ids,
            success: showGraph,
            captionColumn: 'VirusName',
            chartTitle: 'My NAb Chart',
            height: 500,
            width: 700,
            fitType: 'FOUR_PARAMETER'
        });
    }

    Ext.onReady(initiateGraph([185, 165]));
&lt;/script&gt;
&lt;div id="graphDiv"&gt;
</pre>

        */
        getStudyNabGraphURL : function(config)
        {
            var parameters = {};

            LABKEY.Utils.applyTranslated(parameters, config, {objectIds: 'id'}, true, false);

            var requestConfig = {
                url : LABKEY.ActionURL.buildURL('nabassay', 'getStudyNabGraphURL', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope, false),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config) || LABKEY.Utils.displayAjaxErrorResponse, config.scope, true),
                params : parameters
            };

            if(LABKEY.Utils.isDefined(config.timeout))
                requestConfig.timeout = config.timeout;

            LABKEY.Ajax.request(requestConfig);
        },

        /**
         * Create an assay run and import results.
         *
         * @param {Number} config.assayId The assay protocol id.
         * @param {String} [config.containerPath] The path to the container in which the assay run will be imported,
         *       if different than the current container. If not supplied, the current container's path will be used.
         * @param {String} [config.name] The name of a run to create. If not provided, the run will be given the same name as the uploaded file or "[Untitled]".
         * @param {String} [config.comment] Run comments.
         * @param {Object} [config.properties] JSON formatted run properties.
         * @param {Number} [config.batchId] The id of an existing {LABKEY.Exp.RunGroup} to add this run into.
         * @param {Object} [config.batchProperties] JSON formatted batch properties.
         * Only used if batchId is not provided when creating a new batch.
         * @param {String} [config.runFilePath] Absolute or relative path to assay data file to be imported.
         * The file must exist under the file or pipeline root of the container.  Only one of 'files', 'runFilePath', or 'dataRows' can be provided.
         * @param {Array} [config.files] Array of <a href='https://developer.mozilla.org/en-US/docs/DOM/File'><code>File</code></a> objects
         * or form file input elements to import.  Only one of 'files', 'runFilePath', or 'dataRows' can be provided.
         * @param {Array} [config.dataRows] Array of assay results to import.  Only one of 'files', 'runFilePath', or 'dataRows' can be provided.
         * @param {Function} config.success The success callback function will be called with the following arguments:
         * <ul>
         *     <li><b>json</b>: The success response object contains two properties:
         *         <ul>
         *             <li><b>success</b>: true</li>
         *             <li><b>successurl</b>: The url to browse the newly imported assay run.</li>
         *             <li><b>assayId</b>: The assay id.</li>
         *             <li><b>batchId</b>: The previously existing or newly created batch id.</li>
         *             <li><b>runId</b>: The newly created run id.</li>
         *         </ul>
         *     </li>
         *     <li><b>response</b>: The XMLHttpResponseObject used to submit the request.</li>
         * </ul>
         * @param {Function} config.failure The error callback function will be called with the following arguments:
         * <ul>
         *     <li><b>errorInfo:</b> an object describing the error with the following fields:
         *         <ul>
         *             <li><b>exception:</b> the exception message</li>
         *             <li><b>exceptionClass:</b> the Java class of the exception thrown on the server</li>
         *             <li><b>stackTrace:</b> the Java stack trace at the point when the exception occurred</li>
         *         </ul>
         *     </li>
         * <li><b>response:</b> the XMLHttpResponseObject used to submit the request.</li>
         *
         * @example Import a file that has been previously uploaded to the server:
         *         LABKEY.Assay.importRun({
         *             assayId: 3,
         *             name: "new run",
         *             runFilePath: "assaydata/2017-05-10/datafile.tsv",
         *             success: function (json, response) {
         *                 window.location = json.successurl;
         *             },
         *             failure: error (json, response) {
         *             }
         *         });
         *
         * @example Import JSON array of data rows:
         *         LABKEY.Assay.importRun({
         *             assayId: 3,
         *             name: "new run",
         *             dataRows: [{
         *                  sampleId: "S-1",
         *                  dataField: 100
         *             },{
         *                  sampleId: "S-2",
         *                  dataField: 200
         *             }]
         *             success: function (json, response) {
         *                 window.location = json.successurl;
         *             },
         *             failure: error (json, response) {
         *             }
         *         });
         *
         * @example Here is an example of retrieving one or more File objects from a form <code>&lt;input&gt;</code>
         * element and submitting them together to create a new run.
         * &lt;input id='myfiles' type='file' multiple>
         * &lt;a href='#' onclick='doSubmit()'>Submit&lt;/a>
         * &lt;script>
         *     function doSubmit() {
         *         LABKEY.Assay.importRun({
         *             assayId: 3,
         *             name: "new run",
         *             properties: {
         *                 "Run Field": "value"
         *             },
         *             batchProperties: {
         *                 "Batch Field": "value"
         *             },
         *             files: [ document.getElementById('myfiles') ],
         *             success: function (json, response) {
         *                 window.location = json.successurl;
         *             },
         *             failure: error (json, response) {
         *             }
         *         });
         *     }
         * &lt;/script>
         *
         * @example Alternatively, you may use an HTML form to submit the multipart/form-data without using the JavaScript API.
         * &lt;form action='./assay.importRun.api' method='POST' enctype='multipart/form-data'>
         *     &lt;input name='assayId' type='text' />
         *     &lt;input name='name' type='text' />
         *     &lt;input name='file' type='file' />
         *     &lt;input name='submit' type='submit' />
         * &lt;/form>
         */
        importRun : function (config)
        {
            if (!window.FormData)
                throw new Error("modern browser required");

            if (!config.assayId)
                throw new Error("assayId required");

            var files = [];
            if (config.files) {
                for (var i = 0; i < config.files.length; i++) {
                    var f = config.files[i];
                    if (f instanceof window.File) {
                        files.push(f);
                    }
                    else if (f.tagName == "INPUT") {
                        for (var j = 0; j < f.files.length; j++) {
                            files.push(f.files[j]);
                        }
                    }
                }
            }

            if (files.length == 0 && !config.runFilePath && !config.dataRows)
                throw new Error("At least one of 'file', 'runFilePath', or 'dataRows' is required");

            if ((files.length > 0 ? 1 : 0) + (config.runFilePath ? 1 : 0) + (config.dataRows ? 1 : 0) > 1)
                throw new Error("Only one of 'file', 'runFilePath', or 'dataRows' is allowed");

            var formData = new FormData();
            formData.append("assayId", config.assayId);
            if (config.name)
                formData.append("name", config.name);
            if (config.comment)
                formData.append("comment", config.comment);
            if (config.batchId)
                formData.append("batchId", config.batchId);

            if (config.properties) {
                for (var key in config.properties) {
                    if (LABKEY.Utils.isObject(config.properties[key]))
                        formData.append("properties['" + key + "']", JSON.stringify(config.properties[key]));
                    else
                        formData.append("properties['" + key + "']", config.properties[key]);
                }
            }

            if (config.batchProperties) {
                for (var key in config.batchProperties) {
                    if (LABKEY.Utils.isObject(config.batchProperties[key]))
                        formData.append("batchProperties['" + key + "']", JSON.stringify(config.batchProperties[key]));
                    else
                        formData.append("batchProperties['" + key + "']", config.batchProperties[key]);
                }
            }

            if (config.dataRows)
                formData.append("dataRows", JSON.stringify(config.dataRows));

            if (config.runFilePath)
                formData.append("runFilePath", config.runFilePath);

            if (files && files.length > 0) {
                formData.append("file", files[0]);
                for (var i = 1; i < files.length; i++) {
                    formData.append("file" + i, files[i]);
                }
            }

            LABKEY.Ajax.request({
                method: 'POST',
                url: LABKEY.ActionURL.buildURL("assay", "importRun.api", config.containerPath),
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope, false),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                form: formData
            });
        }
    };
};


/**
* @name LABKEY.Assay.AssayDesign
* @class  Static class to describe the shape and fields of an assay.  Each of the {@link LABKEY.Assay}
            'get' methods passes its success callback function an array of AssayDesigns.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=createDatasetViaAssay">LabKey Assays</a></li>
 *              </ul>
 *           </p>
*/

/**#@+
* @memberOf LABKEY.Assay.AssayDesign#
* @field
*/

/**
* @name LABKEY.Assay.AssayDesign#name
* @description   The name of the assay.
* @type String
*/

/**
* @name id
* @description The unique ID of the assay.
* @type Integer
*/

/**
* @name importController
* @description The name of the controller used for data import
* @type String
*/

/**
* @name importAction
* @description The name of the action used for data import
* @type String
*/

/**
* @name containerPath
* @description The path to the container in which this assay design is saved
* @type String
*/

/**
* @name type
* @description The name of the assay type.  Example:  "ELISpot"
* @type String
*/

/**
* @name projectLevel
* @description  Indicates whether this is a project-level assay.
* @type Boolean
*/

/**
* @name LABKEY.Assay.AssayDesign#description
* @description  Contains the assay description.
* @type String
*/

/**
* @name plateTemplate
* @description  Contains the plate template name if the assay is plate-based.  Undefined otherwise.
* @type String
*/

/**
* @name domains
* @description Map containing name/value pairs.  Typically contains three entries for three domains (batch, run and results).
  * Each domain is associated with an array of objects that each describe a domain field.
 *  Each field object has the following properties:
  *        <ul>
  *           <li><b>name: </b>The name of the domain field. (string)</li>
  *           <li><b>typeName: </b> The name of the type of the domain field. (Human readable.) (string)</li>
  *           <li><b>typeURI: </b> The URI that uniquely identifies the domain field type. (Not human readable.) (string)</li>
  *           <li><b>label: </b> The domain field label. (string)</li>
  *           <li><b>description: </b> The domain field description. (string)</li>
  *           <li><b>formatString: </b> The format string applied to the domain field. (string)</li>
  *           <li><b>required: </b> Indicates whether a value is required for this domain field. (boolean)</li>
  *           <li><b>lookup.container: </b> If this domain field is a lookup, lookup.container holds the
             String path to the lookup container or null if the lookup in the
             same container.  Undefined otherwise.(string)</li>
  *           <li><b>lookup.schema: </b> If this domain field object is a lookup, lookup.schema holds the
            String name of the lookup schema.  Undefined otherwise.(string)</li>
  *           <li><b>lookup.table: </b> If this domain field object is a lookup, lookup.table holds the String
            name of the lookup query.  Undefined otherwise. (string)</li>
  *           <li><b>lookup.keyColumn: </b> The primary key field in target table (string)</li>
  *           <li><b>lookup.displayColumn: </b>The display column in target table (string)</li>
  *       </ul>
* @type Object
*/

/**#@-*/

