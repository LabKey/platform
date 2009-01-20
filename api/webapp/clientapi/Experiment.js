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
    function getCallbackWrapper(fn, scope)
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
                experiment = new LABKEY.Exp.RunGroup(json);
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
                Ext.Msg.alert("Programming Error", "You must specify a value for the config.successCallback when calling LABKEY.Security.getUserPermissions()!");

            Ext.Ajax.request(
            {
                url : LABKEY.ActionURL.buildURL("experiment", "createHiddenRunGroup", config.containerPath),
                method : 'POST',
                jsonData : { runIds : config.runIds },
                success: getCallbackWrapper(config.successCallback, config.scope),
                failure: getCallbackWrapper(config.failureCallback, config.scope),
                headers :
                {
                    'Content-Type' : 'application/json'
                }
            });
        }
    };

};

Ext.namespace('LABKEY', 'LABKEY.Exp');

LABKEY.Exp.ExpObject = function (config) {
    config = config || {};
    this.lsid = config.lsid;
    this.name = config.name;
    this.id = config.id || config.rowId;
    this.rowId = this.id;
    this.comment = config.comment;

    /**
     * When the ExpObject was created.
     * @type {Date}
     */
    this.created = config.created;

    /**
     * Who created the ExpObject.
     * @type {String}
     */
    this.createdBy = config.createdBy;

    this.modified = config.modified;
    this.modifiedBy = config.modifiedBy;

    /**
     * Map of property descriptor names to values.
     * @type {Object}
     */
    this.properties = config.properties || {};
};

LABKEY.Exp.Run = function (config) {
    LABKEY.Exp.Run.superclass.constructor.call(this, config);
    config = config || {};

    this.experiments = config.experiments || [];
    this.protocol = config.protocol;
    this.filePathRoot = config.filePathRoot;
    this.dataInputs = config.dataInputs || [];
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

LABKEY.Exp.Protocol = function (config) {
    LABKEY.Exp.Protocol.superclass.constructor.call(this, config);
    config = config || {};

    this.protocolParameters = config.protocolParameters || [];
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

LABKEY.Exp.RunGroup = function (config) {
    LABKEY.Exp.RunGroup.superclass.constructor.call(this, config);
    config = config || {};

    this.runs = [];
    if (config.runs) {
        for (var i = 0; i < config.runs.length; i++) {
            this.runs.push(new LABKEY.Exp.Run(config.runs[i]));
        }
    }
    this.protocols = config.protocols || [];
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

LABKEY.Exp.ChildObject = function (config) {
    LABKEY.Exp.ChildObject.superclass.constructor.call(this, config);
    config = config || {};
    // property holder
};
Ext.extend(LABKEY.Exp.ChildObject, LABKEY.Exp.ExpObject);

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

LABKEY.Exp.Material = function (config) {
    LABKEY.Exp.Material.superclass.constructor.call(this, config);
    config = config || {};

    this.sampleSet = config.sampleSet;
    this.propertyValues = config.propertyValues || {};
};
Ext.extend(LABKEY.Exp.Material, LABKEY.Exp.ProtocolOutput);

LABKEY.Exp.Data = function (config) {
    LABKEY.Exp.Data.superclass.constructor.call(this, config);
    config = config || {};

    this.dataType = config.dataType;
    this.dataFileURI = config.dataFileURI;
    //this.isInlineImage
    //this.isFileOnDisk
};
Ext.extend(LABKEY.Exp.Data, LABKEY.Exp.ProtocolOutput);


