/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 10.1
 * @license Copyright (c) 2008-2010 LabKey Corporation
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
            if (response && response.getResponseHeader && response.getResponseHeader['Content-Type']
                    && response.getResponseHeader['Content-Type'].indexOf('application/json') >= 0)
            {
                json = Ext.util.JSON.decode(response.responseText);
                experiment = createExpFn(json);
            }

            if(fn)
                fn.call(scope || this, experiment, response);
        };
    }

    function getErrorCallbackWrapper(fn, scope)
    {
        if (!fn)
            return Ext.emptyFn;
        return function (response, options)
        {
            var errorInfo = null;
            if (response && response.getResponseHeader && response.getResponseHeader['Content-Type']
                    && response.getResponseHeader['Content-Type'].indexOf('application/json') >= 0)
                errorInfo = Ext.util.JSON.decode(response.responseText);
            else
                errorInfo = {exception: (response && response.statusText ? response.statusText : "Communication failure.")};

            fn.call(scope || this, errorInfo, options, response);
        };
    }

    /** @scope LABKEY.Experiment */
    return {

        /**
         * Create or recycle an existing run group. Run groups are the basis for some operations, like comparing
         * MS2 runs to one another.
         * @param config A configuration object with the following properties:
         * @param {function} config.successCallback A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>runGroup:</b> a {@link LABKEY.Exp.RunGroup} object containing properties about the run group</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {Integer[]} config.runIds An array of integer ids for the runs to be members of the group.
         * @param {function} [config.errorCallback] A reference to a function to call when an error occurs. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
         * <li><b>response:</b> The XMLHttpResponse object</li>
         * </ul>
         * @param {string} [config.containerPath] An alternate container path to get permissions from. If not specified,
         * the current container path will be used.
         * @param {object} [config.scope] An optional scoping object for the success and error callback functions (default to this).
         * @static
         */
        createHiddenRunGroup : function (config)
        {
            if(!config.successCallback)
            {
                Ext.Msg.alert("Programming Error", "You must specify a value for the config.successCallback when calling LABKEY.Exp.createHiddenRunGroup()!");
                return;
            }

            function createExp(json)
            {
                return new LABKEY.Exp.RunGroup(json);
            }

            Ext.Ajax.request(
            {
                url : LABKEY.ActionURL.buildURL("experiment", "createHiddenRunGroup", config.containerPath),
                method : 'POST',
                jsonData : { runIds : config.runIds },
                success: getSuccessCallbackWrapper(createExp, config.successCallback, config.scope),
                failure: getErrorCallbackWrapper(config.failureCallback, config.scope),
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
         * @param {function} config.successCallback The function to call when the function finishes successfully.
         * This function will be called with a the parameters:
         * <ul>
         * <li><b>batch</b> A new {@link LABKEY.Exp.RunGroup} object.
         * <li><b>response</b> The original response
         * </ul>
         * @param {function} [config.errorCallback] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>response</b> The original response
         * </ul>
         * @see The <a href='https://www.labkey.org/wiki/home/Documentation/page.view?name=moduleassay'>Module Assay</a> documentation for more information.
         * @static
         */
        loadBatch : function (config)
        {
            if (!config.successCallback) {
                Ext.Msg.alert("Programming Error", "You must specify a value for the config.successCallback when calling LABKEY.Exp.loadBatch()!");
                return;
            }

            function createExp(json)
            {
                return new LABKEY.Exp.RunGroup(json.batch);
            }

            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL("assay", "getAssayBatch", LABKEY.ActionURL.getContainer()),
                method: 'POST',
                success: getSuccessCallbackWrapper(createExp, config.successCallback, config.scope),
                failure: getErrorCallbackWrapper(config.failureCallback, config.scope),
                scope: config.scope,
                jsonData : {
                    assayId: config.assayId,
                    batchId: config.batchId
                },
                headers : {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Saves a modified batch.
         * @param config An object that contains the following configuration parameters
         * @param {Number} config.assayId The assay protocol id.
         * @param {LABKEY.Exp.RunGroup} config.batch The modified batch object.
         * @param {function} config.successCallback The function to call when the function finishes successfully.
         * This function will be called with a the parameters:
         * <ul>
         * <li><b>batch</b> A new {@link LABKEY.Exp.RunGroup} object.  Some values will be filled in by the server.
         * <li><b>response</b> The original response
         * </ul>
         * @param {function} [config.errorCallback] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>response</b> The original response
         * </ul>
         * @see The <a href='https://www.labkey.org/wiki/home/Documentation/page.view?name=moduleassay'>Module Assay</a> documentation for more information.
         * @static
         */
        saveBatch : function (config)
        {
            if (!config.successCallback) {
                Ext.Msg.alert("Programming Error", "You must specify a value for the config.successCallback when calling LABKEY.Exp.saveBatch()!");
                return;
            }

            function createExp(json)
            {
                return new LABKEY.Exp.RunGroup(json.batch);
            }

            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL("assay", "saveAssayBatch", LABKEY.ActionURL.getContainer()),
                method: 'POST',
                jsonData: {
                    assayId: config.assayId,
                    batch: config.batch
                },
                success: getSuccessCallbackWrapper(createExp, config.successCallback, config.scope),
                failure: getErrorCallbackWrapper(config.failureCallback, config.scope),
                scope: config.scope,
                headers: {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Saves materials.
         *
         * @param config An object that contains the following configuration parameters
         * @param config.name name of the sample set
         * @param config.materials An array of LABKEY.Exp.Material objects to be saved.
         * @param {function} config.successCallback The function to call when the function finishes successfully.
         * This function will be called with a the parameters:
         * <ul>
         * <li><b>batch</b> A new {@link LABKEY.Exp.RunGroup} object.  Some values will be filled in by the server.
         * <li><b>response</b> The original response
         * </ul>
         * @param {function} [config.errorCallback] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>response</b> The original response
         * </ul>
         * @static
         */
        saveMaterials : function (config)
        {
            if (!config.successCallback) {
                Ext.Msg.alert("Programming Error", "You must specify a value for the config.successCallback when calling LABKEY.Exp.saveBatch()!");
                return;
            }

            function createReturnedObject(json)
            {
                return null;
            }

            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL("experiment", "saveMaterials", LABKEY.ActionURL.getContainer()),
                method: 'POST',
                jsonData: {
                    name: config.name,
                    materials: config.materials
                },
                success: getSuccessCallbackWrapper(createReturnedObject, config.successCallback, config.scope),
                failure: getErrorCallbackWrapper(config.failureCallback, config.scope),
                scope: config.scope,
                headers: {
                    'Content-Type' : 'application/json'
                }
            });
        }
    };

};

Ext.namespace('LABKEY', 'LABKEY.Exp');

/**
 * This constructor isn't called directly, but is used by derived classes.
 * @class The experiment object base class describes basic
 * characteristics of a protocol or an experimental run.  Many experiment classes (such as {@link LABKEY.Exp.Run},
 * {@link LABKEY.Exp.Data} and {@link LABKEY.Exp.Material}) are subclasses
 * of ExpObject, so they provide the fields defined by this object (e.g., name, lsid, etc).
 * In java, ExpObject is an abstract class.
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
 * @param {Object} config.properties Map of property descriptor names to values.
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
 * @param {Object[]} config.dataInputs Array of {@link LABKEY.Exp.Data} config objects.
 * @param {Object[]} config.dataOutputs Array of {@link LABKEY.Exp.Data} config objects.
 * @param {Object[]} config.dataRows Array of Objects where each Object corresponds to a row in the results domain.
 * @param {Object[]} config.materialInputs Array of {@link LABKEY.Exp.Material} config objects.
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
 *   successCallback: function (content, format) {
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
 *   failureCallback: function (error, format) {
 *     alert("error: " + error);
 *   }
 * });
 */
LABKEY.Exp.Run = function (config) {
    LABKEY.Exp.Run.superclass.constructor.call(this, config);
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

    /**
     * Deletes the run from the database.
     * @param config An object that contains the following configuration parameters
     * @param {Function} config.successCallback A reference to a function to call with the API results. This
     * function will be passed the following parameters:
     * <ul>
     * <li><b>data:</b> a simple object with one property called 'success' which will be set to true.</li>
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     * @param {Function} [config.errorCallback] A reference to a function to call when an error occurs. This
     * function will be passed the following parameters:
     * <ul>
     * <li><b>errorInfo:</b> an object containing detailed error information (may be null)</li>
     * <li><b>response:</b> The XMLHttpResponse object</li>
     * </ul>
     */
    this.deleteRun = function(config)
    {
        if(!config.successCallback)
        {
            Ext.Msg.alert("Programming Error", "You must specify a value for the config.successCallback when calling LABKEY.Exp.Run.deleteRun()!");
            return;
        }

        Ext.Ajax.request(
        {
            url : LABKEY.ActionURL.buildURL("experiment", "deleteRun"),
            method : 'POST',
            params : { runId : this.id },
            success: LABKEY.Utils.getCallbackWrapper(config.successCallback, this, false),
            failure: LABKEY.Utils.getCallbackWrapper(config.errorCallback, this, true)
        });
    };
};
Ext.extend(LABKEY.Exp.Run, LABKEY.Exp.ExpObject);

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
    LABKEY.Exp.Protocol.superclass.constructor.call(this, config);
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
Ext.extend(LABKEY.Exp.Protocol, LABKEY.Exp.ExpObject);

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
    LABKEY.Exp.RunGroup.superclass.constructor.call(this, config);
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
Ext.extend(LABKEY.Exp.RunGroup, LABKEY.Exp.ExpObject);

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
    LABKEY.Exp.ProtocolApplication.superclass.constructor.call(this, config);
    config = config || {};

};
Ext.extend(LABKEY.Exp.ProtocolApplication, LABKEY.Exp.ExpObject);

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
    LABKEY.Exp.SampleSet.superclass.constructor.call(this, config);
    config = config || {};
    this.samples = config.samples;
    this.description = config.description;
};
Ext.extend(LABKEY.Exp.SampleSet, LABKEY.Exp.ExpObject);

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
    LABKEY.Exp.ChildObject.superclass.constructor.call(this, config);
    config = config || {};
    // property holder
};
Ext.extend(LABKEY.Exp.ChildObject, LABKEY.Exp.ExpObject);

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
    LABKEY.Exp.ProtocolOutput.superclass.constructor.call(this, config);
    config = config || {};

    this.sourceProtocol = config.sourceProtocol;
    this.run = config.run;
    this.targetApplications = config.targetApplications;
    this.sourceApplications = config.sourceApplications;
    this.sucessorRuns = config.sucessorRuns;
    this.cpasType = config.cpasType;
};
Ext.extend(LABKEY.Exp.ProtocolOutput, LABKEY.Exp.ExpObject);

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
 */
LABKEY.Exp.Material = function (config) {
    LABKEY.Exp.Material.superclass.constructor.call(this, config);
    config = config || {};
};
Ext.extend(LABKEY.Exp.Material, LABKEY.Exp.ProtocolOutput);

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
 *
 * @example
 * // To perform a file upload over HTTP:
 * &lt;form id="upload-run-form" enctype="multipart/form-data" method="POST">
 *   &lt;div id="upload-run-button">&lt;/div>
 * &lt;/form>
 * &lt;script type="text/javascript">
 *    LABKEY.Utils.requiresScript("FileUploadField.js");
 * &lt;/script>
 * &lt;script type="text/javascript">
 * var form = new Ext.form.BasicForm(
 *   Ext.get("upload-run-form"), {
 *     fileUpload: true,
 *     frame: false,
 *     url: LABKEY.ActionURL.buildURL("assay", "assayFileUpload"),
 *     listeners: {
 *       "actioncomplete" : function (f, action) {
 *         var data = new LABKEY.Exp.Data(action.result);
 *
 *         // now add the data as a dataInput to a LABKEY.Exp.Run
 *         var run = new LABKEY.Exp.Run();
 *         run.name = data.name;
 *         run.dataInputs = [ data ];
 *
 *         // add the new run to a LABKEY.Exp.Batch object and
 *         // fetch the parsed file contents from the data object
 *         // using the LABKEY.Exp.Data#getContent() method.
 *       }
 *     }
 *   }
 * );
 *
 * var uploadField = new Ext.form.FileUploadField({
 *   id: "upload-run-field",
 *   renderTo: "upload-run-button",
 *   buttonText: "Upload Data...",
 *   buttonOnly: true,
 *   buttonCfg: {
 *     cls: "labkey-button"
 *   },
 *   listeners: {
 *     "fileselected": function (fb, v) {
 *       form.submit();
 *     }
 *   }
 *  });
 * &lt;/script>
 *
 * // To upload the contents of a JavaScript string as a file:
 * &lt;script type="text/javascript">
 * Ext.Ajax.request({
 *   url: LABKEY.ActionURL.buildURL("assay", "assayFileUpload"),
 *   params: { fileName: 'test.txt', fileContent: 'Some text!' },
 *   success: function(response, options)
 *   {
 *      var data = Ext.util.JSON.decode(response.responseText)
 *
 *      // now add the data as a dataInput to a LABKEY.Exp.Run
 *      var run = new LABKEY.Exp.Run();
 *      run.name = data.name;
 *      run.dataInputs = [ data ];
 *
 *      // add the new run to a LABKEY.Exp.Batch object here
 *   } });
 * &lt;/script>
 */
LABKEY.Exp.Data = function (config) {
    LABKEY.Exp.Data.superclass.constructor.call(this, config);
    config = config || {};

    this.dataType = config.dataType;
    this.dataFileURL = config.dataFileURL;
    if (config.pipelinePath)
        this.pipelinePath = config.pipelinePath;
    if (config.role)
        this.role = config.role;

    function getSuccessCallbackWrapper(fn, format, scope)
    {
        return function(response)
        {
            //ensure response is JSON before trying to decode
            var content = null;
            if(response && response.getResponseHeader && response.getResponseHeader['Content-Type']
                    && response.getResponseHeader['Content-Type'].indexOf('application/json') >= 0)
            {
                content = Ext.util.JSON.decode(response.responseText);
            }
            else
            {
                content = response.responseText;
            }

            if(fn)
                fn.call(scope || this, content, format, response);
        };
    }

    function getErrorCallbackWrapper(fn, scope)
    {
        if (!fn)
            return Ext.emptyFn;
        return function (response, options)
        {
            var errorInfo = null;
            if (response && response.getResponseHeader && response.getResponseHeader['Content-Type']
                    && response.getResponseHeader['Content-Type'].indexOf('application/json') >= 0)
                errorInfo = Ext.util.JSON.decode(response.responseText);
            else
                errorInfo = {exception: (response && response.statusText ? response.statusText : "Communication failure.")};

            fn.call(scope || this, errorInfo, options, response);
        };
    }

    /**
     * Retrieves the contents of the data object from the server.
     * @param config An object that contains the following configuration parameters
     * @param {function} config.successCallback The function to call when the function finishes successfully.
     * This function will be called with the parameters:
     * <ul>
     * <li><b>content</b> The type of the content varies based on the format requested.
     * <li><b>format</b> The format used in the request
     * <li><b>response</b> The original response
     * </ul>
     * @param {function} [config.errorCallback] The function to call if this function encounters an error.
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
     * </ul>
     */
    this.getContent = function(config)
    {
        if(!config.successCallback)
        {
            Ext.Msg.alert("Programming Error", "You must specify a value for the config.successCallback when calling LABKEY.Exp.Data.getContent()!");
            return;
        }

        Ext.Ajax.request(
        {
            url : LABKEY.ActionURL.buildURL("experiment", "showFile"),
            method : 'GET',
            params : { rowId : this.id, format: config.format },
            success: getSuccessCallbackWrapper(config.successCallback, config.format, config.scope),
            failure: getErrorCallbackWrapper(config.failureCallback, config.scope)
        });

    };
};
Ext.extend(LABKEY.Exp.Data, LABKEY.Exp.ProtocolOutput);
