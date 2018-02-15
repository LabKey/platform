/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2016 LabKey Corporation
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
 * @namespace The Experiment static class allows you to create hidden run groups and other experiment-related functionality.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href='https://www.labkey.org/wiki/home/Documentation/page.view?name=moduleassay'>LabKey File-Based Assays</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=experiment">LabKey Experiment</a></li>
 *              </ul>
 *           </p>
 */
LABKEY.Experiment = new function()
{
    function getSuccessCallbackWrapper(createExpFn, fn, scope)
    {
        return function(response, options)
        {
            //ensure response is JSON before trying to decode
            var json = null;
            var experiment = null;
            if (response && response.getResponseHeader && response.getResponseHeader('Content-Type')
                    && response.getResponseHeader('Content-Type').indexOf('application/json') >= 0)
            {
                json = LABKEY.Utils.decode(response.responseText);
                experiment = createExpFn(json);
            }

            if(fn)
                fn.call(scope || this, experiment, response);
        };
    }

    function _saveBatches(config, createExps)
    {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL("assay", "saveAssayBatch", LABKEY.ActionURL.getContainer()),
            method: 'POST',
            jsonData: {
                assayId: config.assayId,
                assayName: config.assayName,
                providerName: config.providerName,
                batches: config.batches
            },
            success: getSuccessCallbackWrapper(createExps, config.success, config.scope),
            failure: LABKEY.Utils.getCallbackWrapper(config.failure, config.scope, true),
            scope: config.scope,
            headers: {
                'Content-Type' : 'application/json'
            }
        });
    }

    // normalize the different config object passed in for saveBatch and saveBatches into one config
    // appropriate for _saveBatches call above
    function getSaveBatchesConfig(config)
    {
        var wrapConfig = {};

        if (config.batches)
        {
            wrapConfig.batches = config.batches;
        }
        else
        {
            wrapConfig.batches=[];
            wrapConfig.batches.push(config.batch);
        }
        wrapConfig.assayId = config.assayId;
        wrapConfig.assayName = config.assayName;
        wrapConfig.providerName = config.providerName;
        wrapConfig.scope = config.scope;
        wrapConfig.success = LABKEY.Utils.getOnSuccess(config);
        wrapConfig.failure = LABKEY.Utils.getOnFailure(config);
        return wrapConfig;
    }

    /** @scope LABKEY.Experiment */
    return {

        /**
         * Create or recycle an existing run group. Run groups are the basis for some operations, like comparing
         * MS2 runs to one another.
         * @param config A configuration object with the following properties:
         * @param {function} config.success A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>runGroup:</b> a {@link LABKEY.Exp.RunGroup} object containing properties about the run group</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Integer[]} [config.runIds] An array of integer ids for the runs to be members of the group. Either
         * runIds or selectionKey must be specified.
         * @param {string} [config.selectionKey] The DataRegion's selectionKey to be used to resolve the runs to be
         * members of the group. Either runIds or selectionKey must be specified.
         * @param {function} [config.failure] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @static
         */
        createHiddenRunGroup : function (config)
        {
            function createExp(json)
            {
                return new LABKEY.Exp.RunGroup(json);
            }

            var jsonData = {};
            if (config.runIds && config.selectionKey)
            {
                throw "Only one of runIds or selectionKey config parameter is allowed for a single call.";
            }
            else if (config.runIds)
            {
                jsonData.runIds = config.runIds;
            }
            else if (config.selectionKey)
            {
                jsonData.selectionKey = config.selectionKey;
            }
            else
            {
                throw "Either the runIds or the selectionKey config parameter is required.";
            }
            LABKEY.Ajax.request(
            {
                url : LABKEY.ActionURL.buildURL("experiment", "createHiddenRunGroup", config.containerPath),
                method : 'POST',
                jsonData : jsonData,
                success: getSuccessCallbackWrapper(createExp, LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                headers :
                {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Loads a batch from the server.
         * @param config An object that contains the following configuration parameters
         * @param {Number} config.assayId The assay protocol id.
         * @param {Number} config.batchId The batch id.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with a the parameters:
         * <ul>
         * <li><b>batch</b> A new {@link LABKEY.Exp.RunGroup} object.
         * <li><b>response</b> The original response
         * </ul>
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>response</b> The original response
         * </ul>
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @see The <a href='https://www.labkey.org/wiki/home/Documentation/page.view?name=moduleassay'>Module Assay</a> documentation for more information.
         * @static
         */
        loadBatch : function (config)
        {
            function createExp(json)
            {
                return new LABKEY.Exp.RunGroup(json.batch);
            }

            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("assay", "getAssayBatch", LABKEY.ActionURL.getContainer()),
                method: 'POST',
                success: getSuccessCallbackWrapper(createExp, LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                scope: config.scope,
                jsonData : {
                    assayId: config.assayId,
                    assayName: config.assayName,
                    providerName: config.providerName,
                    batchId: config.batchId
                },
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Loads batches from the server.
         * @param config An object that contains the following configuration parameters
         * @param {Number} config.assayId The assay protocol id.
         * @param {Number} config.batchIds The list of batch ids.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with a the parameters:
         * <ul>
         * <li><b>batches</b> The list of {@link LABKEY.Exp.RunGroup} objects.
         * <li><b>response</b> The original response
         * </ul>
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>response</b> The original response
         * </ul>
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @see The <a href='https://www.labkey.org/wiki/home/Documentation/page.view?name=moduleassay'>Module Assay</a> documentation for more information.
         * @static
         */
        loadBatches : function (config)
        {
            function createExp(json)
            {
                var batches = [];
                if (json.batches) {
                    for (var i = 0; i < json.batches.length; i++) {
                        batches.push(new LABKEY.Exp.RunGroup(json.batches[i]));
                    }
                }
                return batches;
            }

            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("assay", "getAssayBatches", LABKEY.ActionURL.getContainer()),
                method: 'POST',
                success: getSuccessCallbackWrapper(createExp, LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                scope: config.scope,
                jsonData : {
                    assayId: config.assayId,
                    assayName: config.assayName,
                    providerName: config.providerName,
                    batchIds: config.batchIds
                },
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Saves a modified batch.
         * Runs within the batch may refer to existing data and material objects, either inputs or outputs, by ID or LSID.
         * Runs may also define new data and materials objects by not specifying an ID or LSID in their properties.
         * @param config An object that contains the following configuration parameters
         * @param {Number} config.assayId The assay protocol id.
         * @param {LABKEY.Exp.RunGroup} config.batch The modified batch object.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>batch</b> A new {@link LABKEY.Exp.RunGroup} object.  Some values (such as IDs and LSIDs) will be filled in by the server.
         * <li><b>response</b> The original response
         * </ul>
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>response</b> The original response
         * </ul>
         * @see The <a href='https://www.labkey.org/wiki/home/Documentation/page.view?name=moduleassay'>Module Assay</a> documentation for more information.
         * @static
         */
        saveBatch : function (config)
        {
            _saveBatches(getSaveBatchesConfig(config), function(json) {
                if (json.batches) {
                    return new LABKEY.Exp.RunGroup(json.batches[0])
                }
             });
        },

        /**
         * Saves an array of modified batches.
         * Runs within the batches may refer to existing data and material objects, either inputs or outputs, by ID or LSID.
         * Runs may also define new data and materials objects by not specifying an ID or LSID in their properties.
         * @param config An object that contains the following configuration parameters
         * @param {Number} config.assayId The assay protocol id.
         * @param {LABKEY.Exp.RunGroup[]} config.batches The modified batch objects.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>batches</b> An array of new {@link LABKEY.Exp.RunGroup} objects.  Some values (such as IDs and LSIDs) will be filled in by the server.
         * <li><b>response</b> The original response
         * </ul>
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>response</b> The original response
         * </ul>
         * @see The <a href='https://www.labkey.org/wiki/home/Documentation/page.view?name=moduleassay'>Module Assay</a> documentation for more information.
         * @static
         */
        saveBatches : function (config)
        {
            _saveBatches(getSaveBatchesConfig(config), function(json){
                var batches = [];
                if (json.batches) {
                    for (var i = 0; i < json.batches.length; i++) {
                        batches.push(new LABKEY.Exp.RunGroup(json.batches[i]));
                    }
                }
                return batches;
            });
        },

        /**
         * Saves materials.
         * @deprecated Use LABKEY.Query.insertRows({schemaName: 'Samples', queryName: '&lt;sample set name>', ...});
         *
         * @param config An object that contains the following configuration parameters
         * @param config.name name of the sample set
         * @param config.materials An array of LABKEY.Exp.Material objects to be saved.
         * @param {function} config.success The function to call when the function finishes successfully.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>batch</b> A new {@link LABKEY.Exp.RunGroup} object.  Some values will be filled in by the server.
         * <li><b>response</b> The original response
         * </ul>
         * @param {function} [config.failure] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>response</b> The original response
         * </ul>
         * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
         * @static
         */
        saveMaterials : function (config)
        {
            LABKEY.Query.insertRows({
                schemaName: 'Samples',
                queryName: config.name,
                rows: config.materials,
                success: LABKEY.Utils.getOnSuccess(config),
                failure: LABKEY.Utils.getOnFailure(config),
                scope: config.scope
            });
        },

        /**
         * Get parent/child relationships of an ExpData or ExpMaterial.
         * @param config
         * @param config.rowId The row id of the seed ExpData or ExpMaterial.  Either rowId or lsid is required.
         * @param config.lsid The LSID of the seed ExpData or ExpMaterial.  Either rowId or lsid is required.
         * @param {Number} [config.depth] An optional depth argument.  Defaults to include all.
         * @param {Boolean} [config.parents] Include parents in the lineage response.  Defaults to true.
         * @param {Boolean} [config.children] Include children in the lineage response.  Defaults to true.
         * @param {String} [config.expType] Optional experiment type to filter response -- either "Data", "Material", or "ExperimentRun".  Defaults to include all.
         * @param {String} [config.cpasType] Optional LSID of a SampleSet or DataClass to filter the response.  Defaults to include all.
         * @static
         */
        lineage : function (config)
        {
            var params = {};
            if (config.veryNewHotness !== undefined)
                params.veryNewHotness = config.veryNewHotness;
            if (config.rowId)
                params.rowId = config.rowId;
            else if (config.lsid)
                params.lsid = config.lsid;

            if (config.hasOwnProperty('parents'))
                params.parents = config.parents;
            if (config.hasOwnProperty('children'))
                params.children = config.children;
            if (config.hasOwnProperty('depth'))
                params.depth = config.depth;

            if (config.expType)
                params.expType = config.expType;
            if (config.cpasType)
                params.cpasType = config.cpasType;

            LABKEY.Ajax.request({
                method: 'GET',
                url: LABKEY.ActionURL.buildURL("experiment", "lineage.api"),
                params: params,
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                scope: config.scope
            });
        }
    };

};

if (typeof LABKEY.Exp == "undefined")
    LABKEY.Exp = {};

/**
 * This constructor isn't called directly, but is used by derived classes.
 * @class The experiment object base class describes basic
 * characteristics of a protocol or an experimental run.  Many experiment classes (such as {@link LABKEY.Exp.Run},
 * {@link LABKEY.Exp.Data} and {@link LABKEY.Exp.Material}) are subclasses
 * of ExpObject, so they provide the fields defined by this object (e.g., name, lsid, etc).
 * In a Java representation of these same classes, ExpObject is an abstract class.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=experiment">LabKey Experiment</a></li>
 *              </ul>
 *           </p>
 * @memberOf LABKEY.Exp
 *
 * @param {Object} [config] Configuration object.
 *
 * @param {String} config.lsid The LSID of the ExpObject.
 * @param {String} config.name The name of the ExpObject.
 * @param {number} config.id The id of the ExpObject.
 * @param {number} config.rowId The id of the ExpObject (alias of id property)
 * @param {String} config.comment User editable comment.
 * @param {Date} config.created When the ExpObject was created.
 * @param {String} config.createdBy The person who created the ExpObject.
 * @param {Date} config.modified When the ExpObject was last modified.
 * @param {String} config.modifiedBy The person who last modified the ExpObject.
 * @param {Object} config.properties Map of property descriptor names to values. Most types, such as strings and
 * numbers, are just stored as simple properties. Properties of type FileLink will be returned by the server in the
 * same format as {@link LABKEY.Exp.Data} objects (missing many properties such as id and createdBy if they exist on disk but
 * have no row with metadata in the database). FileLink values are accepted from the client in the same way, or a simple value of the
 * following three types:  the data's RowId, the data's LSID, or the full path on the server's file system.
 */
LABKEY.Exp.ExpObject = function (config) {
    config = config || {};
    this.lsid = config.lsid;
    this.name = config.name;
    this.id = config.id || config.rowId;
    this.rowId = this.id;
    this.comment = config.comment;
    this.created = config.created;
    this.createdBy = config.createdBy;
    this.modified = config.modified;
    this.modifiedBy = config.modifiedBy;
    this.properties = config.properties || {};
};

/**
 * Constructs a new experiment run object.
 * @class The Exp.Run class describes an experiment run.  An experiment run is an application of an experimental
 * protocol to concrete inputs, producing concrete outputs. In object-oriented terminology, a protocol would be a class
 * while a run would be an instance.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=experiment">LabKey Experiment</a></li>
 *              </ul>
 *           </p>
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 *
 * @param {Object} [config] The configuration object.  Inherits the config properties of {@link LABKEY.Exp.ExpObject}.
 * @param {Object[]} config.dataInputs Array of {@link LABKEY.Exp.Data} objects that are the inputs to this run. Datas typically represent a file on the server's file system.
 * @param {Object[]} config.dataOutputs Array of {@link LABKEY.Exp.Data} objects that are the outputs from this run. Datas typically represent a file on the server's file system.
 * @param {Object[]} config.dataRows Array of Objects where each Object corresponds to a row in the results domain.
 * @param {Object[]} config.materialInputs Array of {@link LABKEY.Exp.Material} objects that are material/sample inputs to the run.
 * @param {Object[]} config.materialOutputs Array of {@link LABKEY.Exp.Material} objects that are material/sample outputs from the run.
 *
 * @see LABKEY.Exp.Data#getContent
 *
 * @example
 * var result = // ... result of uploading a new assay results file
 * var data = new LABKEY.Exp.Data(result);
 *
 * var run = new LABKEY.Exp.Run();
 * run.name = data.name;
 * run.properties = { "MyRunProperty" : 3 };
 * run.dataInputs = [ data ];
 *
 * data.getContent({
 *   format: 'jsonTSV',
 *   success: function (content, format) {
 *     data.content = content;
 *     var sheet = content.sheets[0];
 *     var filedata = sheet.data;
 *
 *     // transform the file content into the dataRows array used by the run
 *     run.dataRows = [];
 *     for (var i = 1; i < filedata.length; i++) {
 *       var row = filedata[i];
 *       run.dataRows.push({
 *         "SampleId": row[0],
 *         "DataValue": row[1],
 *         // ... other columns
 *       });
 *     }
 *
 *     var batch = // ... the LABKEY.Exp.RunGroup object
 *     batch.runs.push(run);
 *   },
 *   failure: function (error, format) {
 *     alert("error: " + error);
 *   }
 * });
 */
LABKEY.Exp.Run = function (config) {
    LABKEY.Exp.ExpObject.call(this, config);
    config = config || {};

    this.experiments = config.experiments || [];
    this.protocol = config.protocol;
    this.filePathRoot = config.filePathRoot;

    this.dataInputs = [];
    if (config.dataInputs) {
        for (var i = 0; i < config.dataInputs.length; i++) {
            this.dataInputs.push(new LABKEY.Exp.Data(config.dataInputs[i]));
        }
    }

    this.dataOutputs = config.dataOutputs || [];
    this.dataRows = config.dataRows || [];
    this.materialInputs = config.materialInputs || [];
    this.materialOutputs = config.materialOutputs || [];
    this.objectProperties = config.objectProperties || {};
};
LABKEY.Exp.Run.prototype = new LABKEY.Exp.ExpObject;
LABKEY.Exp.Run.prototype.constructor = LABKEY.Exp.Run;

/**
 * Deletes the run from the database.
 * @param config An object that contains the following configuration parameters
 * @param {Function} config.success A reference to a function to call with the API results. This
 * function will be passed the following parameters:
 * <ul>
 * <li><b>data:</b> a simple object with one property called 'success' which will be set to true.</li>
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 * @param {Function} [config.failure] A reference to a function to call when an error occurs. This
 * function will be passed the following parameters:
 * <ul>
 * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
 * <li><b>response:</b> The XMLHttpResponse object</li>
 * </ul>
 */
LABKEY.Exp.Run.prototype.deleteRun = function(config)
{
    LABKEY.Ajax.request(
    {
        url : LABKEY.ActionURL.buildURL("experiment", "deleteRun"),
        method : 'POST',
        params : { runId : this.id },
        success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), this, false),
        failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), this, true)
    });
};



/**
 * The Protocol constructor is private.
 * @class Experiment protocol.
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 *
 * @param {Object} [config] private constructor argument.  Inherits config properties of {@link LABKEY.Exp.ExpObject}.
 *
 * @ignore hide from JsDoc for now
 */
LABKEY.Exp.Protocol = function (config) {
    LABKEY.Exp.ExpObject.call(this, config);
    config = config || {};

    this.instrument = config.instrument;
    this.software = config.software;
    this.contact = config.contact;
    this.childProtocols = config.childProtocols || [];
    this.steps = config.steps || [];
    this.applicationType = config.applicationType;
    this.description = config.description;
    this.runs = [];
    if (config.runs) {
        for (var i = 0; i < config.runs.length; i++) {
            this.runs.push(new LABKEY.Exp.Run(config.runs[i]));
        }
    }
};
LABKEY.Exp.Protocol.prototype = new LABKEY.Exp.ExpObject;
LABKEY.Exp.Protocol.prototype.constructor = LABKEY.Exp.Protocol;

/**
 * The RunGroup constructor is private.  To retrieve a batch RunGroup
 * from the server, see {@link LABKEY.Experiment.loadBatch}.
 * @class An experiment run group contains an array of
 * {@link LABKEY.Exp.Run}s.  If all runs have the same assay protocol, the run group
 * is considered a batch.  To add runs to a batch, insert new {@link LABKEY.Exp.Run}
 * instances into to the 'runs' Array and save the batch.
 * <p>
 * Use {@link LABKEY.Experiment.loadBatch} and {@link LABKEY.Experiment.saveBatch} to
 * load and save a RunGroup.
 * </p>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href='https://www.labkey.org/wiki/home/Documentation/page.view?name=moduleassay'>LabKey File-Based Assays</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=experiment">LabKey Experiment</a></li>
 *              </ul>
 *           </p>
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 *
 * @param {Object} [config] Private configuration object. Inherits config properties of {@link LABKEY.Exp.ExpObject}.
 * @param {LABKEY.Exp.Run[]} config.runs Array of {@link LABKEY.Exp.Run}s in this run group.
 * @param {Boolean} config.hidden Determines whether the RunGroup is hidden.
 */
LABKEY.Exp.RunGroup = function (config) {
    LABKEY.Exp.ExpObject.call(this, config);
    config = config || {};

    this.batchProtocolId = config.batchProtocolId || 0;
    this.runs = [];
    if (config.runs) {
        for (var i = 0; i < config.runs.length; i++) {
            this.runs.push(new LABKEY.Exp.Run(config.runs[i]));
        }
    }
    //this.protocols = config.protocols || [];
    //this.batchProtocol = config.batchProtocol;
    this.hidden = config.hidden;
};
LABKEY.Exp.RunGroup.prototype = new LABKEY.Exp.ExpObject;
LABKEY.Exp.RunGroup.prototype.constructor = LABKEY.Exp.RunGroup;

/**
 * The ProtocolApplication constructor is private.
 * @class Experiment ProtocolApplication.
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 *
 * @param {Object} [config] Private configuration object. Inherits config properties of {@link LABKEY.Exp.ExpObject}.
 *
 * @ignore hide from JsDoc for now
 */
LABKEY.Exp.ProtocolApplication = function (config) {
    LABKEY.Exp.ExpObject.call(this, config);
    config = config || {};

};
LABKEY.Exp.ProtocolApplication.prototype = new LABKEY.Exp.ExpObject;
LABKEY.Exp.ProtocolApplication.prototype.constructor = LABKEY.Exp.ProtocolApplication;

/**
 * @class The SampleSet class describes a collection of experimental samples, which are
 * also known as materials (see {@link LABKEY.Exp.Material}). This class defines the set of fields that
 * you you wish to attach to all samples in the group. These fields supply characteristics of the sample
 * (e.g., its volume, number of cells, color, etc.).
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=experiment">LabKey Experiment</a></li>
 *              </ul>
 *           </p>
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 *
 * @param {Object} [config] Describes the SampleSet's properties.  Inherits the config properties of {@link LABKEY.Exp.ExpObject}.
 * @param {Object[]} config.samples Array of {@link LABKEY.Exp.Material} config objects.
 * @param {String} config.description Description of the SampleSet
 */

LABKEY.Exp.SampleSet = function (config) {
    LABKEY.Exp.ExpObject.call(this, config);
    config = config || {};
    this.samples = config.samples;
    this.description = config.description;
};
LABKEY.Exp.SampleSet.prototype = new LABKEY.Exp.ExpObject;
LABKEY.Exp.SampleSet.prototype.constructor = LABKEY.Exp.SampleSet;

/**
 * Get a domain design for the SampleSet.
 *
 * @param {Function} config.success Required. Function called if the
 *	"getDomain" function executes successfully. Will be called with the argument {@link LABKEY.Domain.DomainDesign},
 *    which describes the fields of a domain.
 * @param {Function} [config.failure] Function called if execution of the "getDomain" function fails.
 * @param {String} [config.containerPath] The container path in which the requested Domain is defined.
 *       If not supplied, the current container path will be used.
 *
 * @ignore hide from JsDoc for now
 *
 * @example
 * &lt;script type="text/javascript">
 *   Ext.onReady(function() {
 *     var ss = new LABKEY.Exp.SampleSet({name: 'MySampleSet'});
 *     ss.getDomain({
 *       success : function (domain) {
 *         console.log(domain);
 *       }
 *     });
 *   }
 * &lt;/script>
 */
LABKEY.Exp.SampleSet.prototype.getDomain = function (config)
{
    LABKEY.Domain.get(LABKEY.Utils.getOnSuccess(config), LABKEY.Utils.getOnFailure(config), "Samples", this.name, config.containerPath);
};

/**
 * Create a new Sample Set definition.
 * @param {Function} config.success Required callback function.
 * @param {Function} [config.failure] Failure callback function.
 * @param {LABKEY.Domain.DomainDesign} config.domainDesign The domain design to save.
 * @param {Object} [config.options] Set of extra options used when creating the SampleSet:
 * <ul>
 *   <li>idCols: Optional. Array of indexes into the domain design fields.  If the domain design contains a 'Name' field, no idCols are allowed.  Either a 'Name' field must be present or at least one idCol must be supplied..
 *   <li>parentCol: Optional. Index of the parent id column.
 * </ul>
 * @param {String} [config.containerPath] The container path in which to create the domain.
 * @static
 *
 * @ignore hide from JsDoc for now
 *
 * @example
 * var domainDesign = {
 *   name: "BoyHowdy",
 *   description: "A client api created sample set",
 *   fields: [{
 *     name: 'TestName',
 *     label: 'The First Field',
 *     rangeURI: 'http://www.w3.org/2001/XMLSchema#string'
 *   },{
 *     name: 'Num',
 *     rangeURI: 'http://www.w3.org/2001/XMLSchema#int'
 *   },{
 *     name: 'Parent',
 *     rangeURI: 'http://www.w3.org/2001/XMLSchema#string'
 *   }]
 * };
 *
 * LABKEY.Exp.SampleSet.create({
 *   success: function () { alert("success!"); },
 *   failure: function () { alert("failure!"); },
 *   domainDesign: domainDesign,
 *   options: { idCols: [0, 1], parentCol: 2 }
 * });
 */
LABKEY.Exp.SampleSet.create = function (config)
{
    LABKEY.Domain.create(LABKEY.Utils.getOnSuccess(config), LABKEY.Utils.getOnFailure(config), "SampleSet", config.domainDesign, config.options, config.containerPath);
};

/**
 * DataClass represents a set of ExpData objects that share a set of properties.
 *
 * @class DataClass describes a collection of Data objects.
 * This class defines the set of fields that you you wish to attach to all datas in the group.
 * Within the DataClass, each Data has a unique name.
 *
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 *
 * @param config
 * @param {String} config.description Description of the DataClass.
 * @param {String} [config.nameExpression] Optional name expression used to generate unique names for ExpData inserted into the DataClass.
 * @param {Object} [config.sampleSet] The optional SampleSet the DataClass is associated with.  With the following properties:
 * @param {Integer} [config.sampleSet.id] The row id of the SampleSet.
 * @param {String} [config.sampleSet.name] The name of the SampleSet.
 * @constructor
 */
LABKEY.Exp.DataClass = function (config)
{
    "use strict";

    LABKEY.Exp.ExpObject.call(this, config);
    config = config || {};
    this.data = config.data;
    this.description = config.description;
    this.sampleSet = config.sampleSet;
};
LABKEY.Exp.DataClass.prototype = new LABKEY.Exp.ExpObject;
LABKEY.Exp.DataClass.prototype.constructor = LABKEY.Exp.DataClass;

LABKEY.Exp.DataClass.prototype.getDomain = function (config)
{
    "use strict";
    LABKEY.Domain.get({
        success: LABKEY.Utils.getOnSuccess(config),
        failure: LABKEY.Utils.getOnFailure(config),
        schemaName: "exp.data",
        queryName: this.name,
        containerPath: config.containerPath
    });
};

LABKEY.Exp.DataClass.create = function (config)
{
    "use strict";
    LABKEY.Domain.create({
        success: LABKEY.Utils.getOnSuccess(config),
        failure: LABKEY.Utils.getOnFailure(config),
        type: "dataclass",
        domainDesign: config.domainDesign,
        options: config.options,
        containerPath: config.containerPath
    });
};

/**
 * The ChildObject constructor is private.
 * @class Experiment Child
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 *
 * @param {Object} [config] Private configuration object. Inherits the config properties of {@link LABKEY.Exp.ExpObject}.
 *
 * @ignore hide from JsDoc for now
 */
LABKEY.Exp.ChildObject = function (config) {
    LABKEY.Exp.ExpObject.call(this, config);
    config = config || {};
    // property holder
};
LABKEY.Exp.ChildObject.prototype = new LABKEY.Exp.ExpObject;
LABKEY.Exp.ChildObject.prototype.constructor = LABKEY.Exp.ChildObject;

/**
 * The ProtocolOutput constructor is private.
 * @class Experiment Protocol Output.  Base class for {@link LABKEY.Exp.Data}
 * and {@link LABKEY.Exp.Material}.
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 * @param {Object} [config] Private configuration object. Inherits config properties of {@link LABKEY.Exp.ExpObject}.
 * @ignore hide from JsDoc for now
 */
LABKEY.Exp.ProtocolOutput = function (config) {
    LABKEY.Exp.ExpObject.call(this, config);
    config = config || {};

    this.sourceProtocol = config.sourceProtocol;
    this.run = config.run;
    this.targetApplications = config.targetApplications;
    this.sourceApplications = config.sourceApplications;
    this.sucessorRuns = config.sucessorRuns;
    this.cpasType = config.cpasType;
};
LABKEY.Exp.ProtocolOutput.prototype = new LABKEY.Exp.ExpObject;
LABKEY.Exp.ProtocolOutput.prototype.constructor = LABKEY.Exp.ProtocolOutput;

/**
 * Constructs a new experiment material object.
 * @class The Exp.Material class describes an experiment material.  "Material" is a synonym for both
 * "sample" and "specimen."  Thus, for example, the input to an assay could be called a material.
 * The fields of this class are inherited from the {@link LABKEY.Exp.ExpObject} object and
 * the private LABKEY.Exp.ProtocolOutput object.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=experiment">LabKey Experiment</a></li>
 *              </ul>
 *           </p>
 * @extends LABKEY.Exp.ProtocolOutput
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 *
 * @param {Object} [config] Configuration object.  Inherits the config properties of {@link LABKEY.Exp.ExpObject}.
 * @param {Object} [config.sampleSet] The SampleSet the material belongs to.  With the following properties:
 * @param {Integer} [config.sampleSet.id] The row id of the SampleSet.
 * @param {String} [config.sampleSet.name] The name of the SampleSet.
 */
LABKEY.Exp.Material = function (config) {
    LABKEY.Exp.ProtocolOutput.call(this, config);
    config = config || {};

    this.sampleSet = config.sampleSet;
};
LABKEY.Exp.Material.prototype = new LABKEY.Exp.ProtocolOutput;
LABKEY.Exp.Material.prototype.constructor = LABKEY.Exp.Material;

/**
 * The Data constructor is private.
 * To create a LABKEY.Exp.Data object, upload a file using to the "assayFileUpload" action of
 * the "assay" controller.
 *
 * @class The Experiment Data class describes the data input or output of a {@link LABKEY.Exp.Run}.  This typically
 * corresponds to an assay results file uploaded to the LabKey server.
 * <p>
 * To create a LABKEY.Exp.Data object, upload a file using to the "assayFileUpload" action of
 * the "assay" controller.
 * </p>
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href='https://www.labkey.org/wiki/home/Documentation/page.view?name=moduleassay'>LabKey File-Based Assays</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=experiment">LabKey Experiment</a></li>
 *              </ul>
 *           </p>
 *
 * @extends LABKEY.Exp.ProtocolOutput
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 *
 * @param {Object} [config] Private configuration object.  Inherits the config properties of {@link LABKEY.Exp.ExpObject}.
 * @param {String} config.dataFileURL The local file url of the uploaded file.
 * @param {Object} [config.dataClass] The DataClass the data belongs to.  With the following properties:
 * @param {Integer} [config.dataClass.id] The row id of the DataClass.
 * @param {String} [config.dataClass.name] The name of the DataClass.
 *
 * @example
 * // To perform a file upload over HTTP:
 * &lt;form id="upload-run-form" enctype="multipart/form-data" method="POST">
 *   &lt;div id="upload-run-button">&lt;/div>
 * &lt;/form>
 * &lt;script type="text/javascript">
 *    LABKEY.Utils.requiresScript("FileUploadField.js");
 *    // Optional - specify a protocolId so that the Exp.Data object is assigned the related LSID namespace.
 *    var url = LABKEY.ActionURL.buildURL("assay", "assayFileUpload", LABKEY.ActionURL.getContainer(), { protocolId: 50 });
 *    Ext.onReady(function() {
 *       var form = new Ext.form.BasicForm(
 *       Ext.get("upload-run-form"), {
 *          fileUpload: true,
 *          frame: false,
 *          url: url,
 *          listeners: {
 *             actioncomplete : function (form, action) {
 *                alert('Upload successful!');
 *                var data = new LABKEY.Exp.Data(action.result);
 *
 *                // now add the data as a dataInput to a LABKEY.Exp.Run
 *                var run = new LABKEY.Exp.Run();
 *                run.name = data.name;
 *                run.dataInputs = [ data ];
 *
 *                // add the new run to a LABKEY.Exp.Batch object and
 *                // fetch the parsed file contents from the data object
 *                // using the LABKEY.Exp.Data#getContent() method.
 *             },
 *             actionfailed: function (form, action) {
 *                alert('Upload failed!');
 *             }
 *          }
 *       });
 *
 *       var uploadField = new Ext.form.FileUploadField({
 *          id: "upload-run-field",
 *          renderTo: "upload-run-button",
 *          buttonText: "Upload Data...",
 *          buttonOnly: true,
 *          buttonCfg: { cls: "labkey-button" },
 *          listeners: {
 *             "fileselected": function (fb, v) {
 *                form.submit();
 *             }
 *          }
 *       });
 *    });
 * &lt;/script>
 *
 * // Or, to upload the contents of a JavaScript string as a file:
 * &lt;script type="text/javascript">
 * Ext.onReady(function() {
 *    LABKEY.Ajax.request({
 *      url: LABKEY.ActionURL.buildURL("assay", "assayFileUpload"),
 *      params: { fileName: 'test.txt', fileContent: 'Some text!' },
 *      success: function(response, options) {
 *         var data = new LABKEY.Exp.Data(Ext.util.JSON.decode(response.responseText));
 *
 *         // now add the data as a dataInput to a LABKEY.Exp.Run
 *         var run = new LABKEY.Exp.Run();
 *         run.name = data.name;
 *         run.dataInputs = [ data ];
 *
 *         // add the new run to a LABKEY.Exp.Batch object here
 *      }
 *    });
 *  });
 *
 * &lt;/script>
 */
LABKEY.Exp.Data = function (config) {
    LABKEY.Exp.ProtocolOutput.call(this, config);
    config = config || {};

    this.dataType = config.dataType;
    this.dataFileURL = config.dataFileURL;
    this.dataClass = config.dataClass;
    if (config.pipelinePath)
        this.pipelinePath = config.pipelinePath;
    if (config.role)
        this.role = config.role;
};
LABKEY.Exp.Data.prototype = new LABKEY.Exp.ProtocolOutput;
LABKEY.Exp.Data.prototype.constructor = LABKEY.Exp.Data;

/**
 * Retrieves the contents of the data object from the server.
 * @param config An object that contains the following configuration parameters
 * @param {object} [config.scope] A scoping object for the success and error callback functions (default to this).
 * @param {function} config.success The function to call when the function finishes successfully.
 * This function will be called with the parameters:
 * <ul>
 * <li><b>content</b> The type of the content varies based on the format requested.
 * <li><b>format</b> The format used in the request
 * <li><b>response</b> The original response
 * </ul>
 * @param {function} [config.failure] The function to call if this function encounters an error.
 * This function will be called with the following parameters:
 * <ul>
 * <li><b>errorInfo:</b> An object with a property called "exception," which contains the error message.</li>
 * <li><b>format</b> The format used in the request
 * <li><b>response</b> The original response
 * </ul>
 * @param {String} [config.format] How to format the content. Defaults to plaintext, supported for text/* MIME types,
 * including .html, .xml, .tsv, .txt, and .csv. Use 'jsonTSV' to get a JSON version of the .xls, .tsv, .or .csv
 * files, the structure of which matches the argument to convertToExcel in {@link LABKEY.Utils}.
 * <ul>
 * <li><b>fileName:</b> the name of the file</li>
 * <li><b>sheets:</b> an array of the sheets in the file. Text file types will have a single sheet named 'flat'.
 * <ul><li><b>name:</b> the name of the sheet</li>
 *     <li><b>values:</b> two-dimensional array of all the cells in the worksheet. First array index is row, second is column</li>
 * </ul>
 * </ul>
 * <br/>Use 'jsonTSVExtended' to get include metadata in the 2D array of cells.
 * Text file types will not supply additional metadata but populate the 'value' attribute in the map.
 * Excel files will include:
 * <ul>
 * <li><b>value:</b> the string, boolean, date, or number in the cell</li>
 * <li><b>timeOnly:</b> whether the date part should be ignored for dates</li>
 * <li><b>formatString:</b> the Java format string to be used to render the value for dates and numbers</li>
 * <li><b>formattedValue:</b> the formatted string for that value for all value types</li>
 * <li><b>error:</b> true if this cell has an error</li>
 * <li><b>formula:</b> if the cell's value is specified by a formula, the text of the formula</li>
 * </ul>
 * <br/>Use 'jsonTSVIgnoreTypes' to always return string values for all cells, regardless of type.
 * <br/>
 * An example of the results for a request for 'jsonTsv' format:
 * <pre>
 * {
"sheets": [
    {
        "name": "Sheet1",
        "data": [
            [
                "StringColumn",
                "DateColumn"
            ],
            [
                "Hello",
                "16 May 2009 17:00:00"
            ],
            [
                "world",
                "12/21/2008 08:45AM"
            ]
        ]
    },
    {
        "name": "Sheet2",
        "data": [
            ["NumberColumn"],
            [55.44],
            [100.34],
            [-1]
        ]
    },
    {
        "name": "Sheet3",
        "data": []
    }
],
"fileName": "SimpleExcelFile.xls"
}</pre>
 <br/>
 An example of the same file in the 'jsonTSVExtended' format:
 <pre>
 * {
"sheets": [
    {
        "name": "Sheet1",
        "data": [
            [
                {
                    "value": "StringColumn",
                    "formattedValue": "StringColumn"
                },
                {
                    "value": "DateColumn",
                    "formattedValue": "DateColumn"
                }
            ],
            [
                {
                    "value": "Hello",
                    "formattedValue": "Hello"
                },
                {
                    "formatString": "MMMM d, yyyy",
                    "value": "16 May 2009 17:00:00",
                    "timeOnly": false,
                    "formattedValue": "May 17, 2009"
                }
            ],
            [
                {
                    "value": "world",
                    "formattedValue": "world"
                },
                 {
                     "formatString": "M/d/yy h:mm a",
                     "value": "21 Dec 2008 19:31:00",
                     "timeOnly": false,
                     "formattedValue": "12/21/08 7:31 PM"
                 }
            ]
        ]
    },
    {
        "name": "Sheet2",
        "data": [
            [{
                "value": "NumberColumn",
                "formattedValue": "NumberColumn"
            }],
            [{
                "formatString": "$#,##0.00",
                "value": 55.44,
                "formattedValue": "$55.44"
            }],
            [{
                "value": 100.34,
                "formattedValue": "100.34"
            }],
            [{
                "value": -1,
                "formattedValue": "-1"
            }]
        ]
    },
    {
        "name": "Sheet3",
        "data": []
    }
],
"fileName": "SimpleExcelFile.xls"
}
 </pre>
 *
 */
LABKEY.Exp.Data.prototype.getContent = function(config)
{
    if(!LABKEY.Utils.getOnSuccess(config))
    {
        alert("You must specify a callback function in config.success when calling LABKEY.Exp.Data.getContent()!");
        return;
    }

    function getSuccessCallbackWrapper(fn, format, scope)
    {
        return function(response)
        {
            //ensure response is JSON before trying to decode
            var content = null;
            if(response && response.getResponseHeader && response.getResponseHeader('Content-Type')
                    && response.getResponseHeader('Content-Type').indexOf('application/json') >= 0)
            {
                content = LABKEY.Utils.decode(response.responseText);
            }
            else
            {
                content = response.responseText;
            }

            if(fn)
                fn.call(scope || this, content, format, response);
        };
    }

    LABKEY.Ajax.request(
    {
        url : LABKEY.ActionURL.buildURL("experiment", "showFile"),
        method : 'GET',
        params : { rowId : this.id, format: config.format },
        success: getSuccessCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.format, config.scope),
        failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
    });

};


