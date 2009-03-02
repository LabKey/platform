/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 9.1
 * @license Copyright (c) 2008-2009 LabKey Corporation
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
 * @namespace Experiment static class to allow creating hidden run groups and other experiment-related functionality.
 */
LABKEY.Experiment = new function()
{
    function getCallbackWrapper(createExpFn, fn, scope)
    {
        return function(response, options)
        {
            //ensure response is JSON before trying to decode
            var json = null;
            var experiment = null;
            if(response && response.getResponseHeader && response.getResponseHeader['Content-Type']
                    && response.getResponseHeader['Content-Type'].indexOf('application/json') >= 0)
            {
                json = Ext.util.JSON.decode(response.responseText);
                experiment = createExpFn(json);
            }

            if(fn)
                fn.call(scope || this, experiment, response);
        };
    }

    /** @scope LABKEY.Experiment.prototype */
    return {

        /**
         * Create or recycle an existing run group. Run groups are the basis for some operations, like comparing
         * MS2 runs to one another.
         * @param config A configuration object with the following properties:
         * @param {function} config.successCallback A reference to a function to call with the API results. This
         * function will be passed the following parameters:
         * <ul>
         * <li><b>runGroup:</b> an @{link LABKEY.Exp.RunGroup} object containing properties about the run group</li>
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
         */
        createHiddenRunGroup : function (config)
        {
            if(!config.successCallback)
                Ext.Msg.alert("Programming Error", "You must specify a value for the config.successCallback when calling LABKEY.Exp.createHiddenRunGroup()!");

            function createExp(json)
            {
                return new LABKEY.Exp.RunGroup(json);
            }

            Ext.Ajax.request(
            {
                url : LABKEY.ActionURL.buildURL("experiment", "createHiddenRunGroup", config.containerPath),
                method : 'POST',
                jsonData : { runIds : config.runIds },
                success: getCallbackWrapper(createExp, config.successCallback, config.scope),
                failure: getCallbackWrapper(createExp, config.failureCallback, config.scope),
                headers :
                {
                    'Content-Type' : 'application/json'
                }
            });
        },

        /**
         * Loads a batch from the server.
         * @param config An object that contains the following configuration parameters
         * @param {Nubmber} config.assayId The assay protocol id.
         * @param {Number} config.batchId The batch id.
         * @param {function} config.successCallback The function to call when the function finishes successfully.
         * This function will be called with a the parameters:
         * <ul>
         * <li><b>batch</b> A new LABKEY.Exp.RunGroup object.
         * <li><b>response</b> The original response
         * </ul>
         * @param {function} [config.errorCallback] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>response</b> The original response
         * </ul>
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
                success: getCallbackWrapper(createExp, config.successCallback, config.scope),
                failure: config.failureCallback,
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
         * @param {Nubmber} config.assayId The assay protocol id.
         * @param {LABKEY.Exp.RunGroup} config.batch The modified batch object.
         * @param {function} config.successCallback The function to call when the function finishes successfully.
         * This function will be called with a the parameters:
         * <ul>
         * <li><b>batch</b> A new LABKEY.Exp.RunGroup object.  Some values will be filled in by the server.
         * <li><b>response</b> The original response
         * </ul>
         * @param {function} [config.errorCallback] The function to call if this function encounters an error.
         * This function will be called with the following parameters:
         * <ul>
         * <li><b>response</b> The original response
         * </ul>
         */
        saveBatch : function (config)
        {
            if (!config.successCallback) {
                Ext.Msg.alert("Programming Error", "You must specify a value for the config.successCallback when calling LABKEY.Exp.saveBatch()!");
                return
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
                success: getCallbackWrapper(createExp, config.successCallback, config.scope),
                failure: config.failureCallback,
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
 * @class Experiment object base class.
 * @extends Ext.util.Observable
 * @memberOf LABKEY.Exp
 *
 * @property {String} lsid The LSID of the object.
 * @property {String} name The name of the object.
 * @property {number} id The id of this object.
 * @property {number} rowId The id of this object.
 * @property {String} comment
 * @property {Date} created When the ExpObject was created.
 * @property {String} createdBy Who created the ExpObject.
 * @property {Date} modified When the ExpObject was last modified.
 * @property {String} modifiedBy Who last modified the ExpObject.
 * @property {Object} properties Map of property descriptor names to values.
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
 * @class Experiment Run.
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 *
 * @property {Array} experiments List of {@link LABKEY.Exp.Experiment}s in the run.
 * @property {String} protocol The {@link LABKEY.Exp.Protocol} for this run.
 * @property {String} filePathRoot The file path where the run is rooted.  File paths in {@link LABKEY.Exp.Data} are relative from this root.
 * @property {Array[LABKEY.Exp.Data]} dataInputs Array of {@link LABKEY.Exp.Data} input files.
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
//    this.protocolApplications = config.protocolApplications || [];
//    this.inputProtocolApplication = config.inputProtocolApplication;
//    this.outputProtocolApplication = config.outputProtocolApplication;
    this.objectProperties = config.objectProperties || {};
};
Ext.extend(LABKEY.Exp.Run, LABKEY.Exp.ExpObject);

/**
 * @class Experiment protocol.
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 */
LABKEY.Exp.Protocol = function (config) {
    LABKEY.Exp.Protocol.superclass.constructor.call(this, config);
    config = config || {};

    //this.protocolParameters = config.protocolParameters || [];
    this.instrument = config.instrument;
    this.software = config.software;
    this.contact = config.contact;
    this.childProtocols = config.childProtocols || [];
    this.steps = config.steps || [];
    this.applicationType = config.applicationType;
    this.description = config.description;
    //this.maxInputDataPerInstance = config.maxInputDataPerInstance;
    //this.maxInputMaterialPerInstance = config.maxInputMaterialPerInstance;
    //this.protocolDescription = config.protocolDescription;
    //this.outputMaterialPerInstance = config.outputMaterialPerInstance;
    //this.outputDataPerInstance = config.outputDataPerInstance;
    //this.outputMaterialType = config.outputMaterialType;
    //this.outputDataType = config.outputDataType;
    //this.parentProtocols = config.parentProtocols || [];
    this.runs = [];
    if (config.runs) {
        for (var i = 0; i < config.runs.length; i++) {
            this.runs.push(new LABKEY.Exp.Run(config.runs[i]));
        }
    }
};
Ext.extend(LABKEY.Exp.Protocol, LABKEY.Exp.ExpObject);

/**
 * @class Experiment Run Group.
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 *
 * @parameter {Array[LABKEY.Exp.Run]} runs List of runs
 * @parameter {Boolean} hidden
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

LABKEY.Exp.ProtocolApplication = function (config) {
    LABKEY.Exp.ProtocolApplication.superclass.constructor.call(this, config);
    config = config || {};

    //this.dataInputs
    //this.inputDatas
    //this.outputDatas
    //this.materialInputs
    //this.inputMaterials
    //this.outputMaterials
    //this.protocol
    //this.run
    //this.actionSequence
    //this.applicationType
    //this.activityDate
    //this.comments
};
Ext.extend(LABKEY.Exp.ProtocolApplication, LABKEY.Exp.ExpObject);

/**
 * @class Experiment Sample Set
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 */
LABKEY.Exp.SampleSet = function (config) {
    LABKEY.Exp.SampleSet.superclass.constructor.call(this, config);
    config = config || {};

    this.materialLSIDPrefix = config.materialLSIDPrefix;
    this.propertiesForType = config.propertiesForType;
    this.samples = config.samples;
    this.type = config.type;
    this.description = config.description;
    this.canImportMoreSamples = config.canImportMoreSamples;
    this.hasIdColumns = config.hasIdColumns;
};
Ext.extend(LABKEY.Exp.SampleSet, LABKEY.Exp.ExpObject);

/**
 * @class Experiment Child
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
 */
LABKEY.Exp.ChildObject = function (config) {
    LABKEY.Exp.ChildObject.superclass.constructor.call(this, config);
    config = config || {};
    // property holder
};
Ext.extend(LABKEY.Exp.ChildObject, LABKEY.Exp.ExpObject);

/**
 * @class Experiment Protocol Output
 * @extends LABKEY.Exp.ExpObject
 * @memberOf LABKEY.Exp
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
 * @class Experiment Material
 * @extends LABKEY.Exp.ProtocolOutput
 * @memberOf LABKEY.Exp
 */
LABKEY.Exp.Material = function (config) {
    LABKEY.Exp.Material.superclass.constructor.call(this, config);
    config = config || {};

    this.sampleSet = config.sampleSet;
    this.propertyValues = config.propertyValues || {};
};
Ext.extend(LABKEY.Exp.Material, LABKEY.Exp.ProtocolOutput);

/**
 * @class Experiment Data.  A data input or output of a run.
 * @extends LABKEY.Exp.ProtocolOutput
 * @memberOf LABKEY.Exp
 */
LABKEY.Exp.Data = function (config) {
    LABKEY.Exp.Data.superclass.constructor.call(this, config);
    config = config || {};

    this.dataType = config.dataType;
    this.dataFileURI = config.dataFileURI;

    var jsonCallbackWrapper = function(fn, format, scope)
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
    };

    
    /**
     * Retrieves the contents of the data object from the server.
     * @param config An object that contains the following configuration parameters
     * @param {function} config.successCallback The function to call when the function finishes successfully.
     * This function will be called with a the parameters:
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
     * @param {string} [config.format] How to format the content. Defaults to plaintext, supported for text/* MIME types,
     * including .html, .xml, .tsv, .txt, and .csv.. Use 'jsonTSV' to get a JSON version of the .xls, .tsv, .or .csv
     * files. The resulting JSON object will have a sheets property, which is an array. Each element will have a
     * 'name' property, and a 'data' property that is a two-dimensional array of values. The sheet names match the
     * sheet names in Excel files, while .tsv and .csv files will have a single entry in the map called 'flat'. 
     */
    this.getContent = function(config)
    {
        if(!config.successCallback)
            Ext.Msg.alert("Programming Error", "You must specify a value for the config.successCallback when calling LABKEY.Exp.Data.getContent()!");

        Ext.Ajax.request(
        {
            url : LABKEY.ActionURL.buildURL("experiment", "showFile"),
            method : 'GET',
            params : { rowId : this.id, format: config.format },
            success: jsonCallbackWrapper(config.successCallback, config.format, config.scope),
            failure: jsonCallbackWrapper(config.failureCallback, config.format, config.scope)
        });

    };

    //this.isInlineImage
    //this.isFileOnDisk
};
Ext.extend(LABKEY.Exp.Data, LABKEY.Exp.ProtocolOutput);
