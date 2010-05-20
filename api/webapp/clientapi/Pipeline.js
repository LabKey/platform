/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 10.2
 * @license Copyright (c) 2009-2010 LabKey Corporation
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
 * @namespace Pipeline static class that allows programmatic manipulation of the data pipeline.
 */
LABKEY.Pipeline = new function(){
    // private methods and data

    //public interface
    /** @scope LABKEY.Pipeline */
    return {
        /**
         * Gets the container in which the pipeline for this container is defined. This may be the
         * container in which the request was made, or a parent container if the pipeline was defined
         * there.
         * @param {Object} config A configuration object with the following properties.
         * @param {Function} config.successCallback The function to call with the resulting information.
         * This function will be passed a single parameter of type object, which will have the following
         * properties:
         * <ul>
         *  <li>containerPath: the container path in which the pipeline is defined. If no pipeline has
         * been defined in this container hierarchy, the value of this property will be null.</li>
         *  <li>webDavURL: the WebDavURL for the pipeline root.</li>
         * </ul>
         * @param {Function} [config.errorCallback] A function to call if an error occurs. This function
         * will receive one parameter of type object with the following properites:
         * <ul>
         *  <li>exception: The exception message.</li>
         * </ul>
         * @param {String} [config.containerPath] The container in which to make the request (defaults to current container)
         * @param {Object} [config.scope] The scope to use when calling the callbacks (defaults to this).
         */
        getPipelineContainer : function(config) {
            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL("pipeline", "getPipelineContainer.api", config.containerPath),
                method: 'GET',
                success: LABKEY.Utils.getCallbackWrapper(config.successCallback, config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(config.errorCallback, config.scope, true)
            });
        },

        /**
         * Gets the protocols that have been saved for a particular pipeline.
         * 
         * @param {Object} config A configuration object with the following properties.
         * @param {String} config.taskId Identifier for the pipeline.
         * @param {String} [config.containerPath] The container in which to make the request (defaults to current container)
         * @param {Function} config.successCallback The function to call with the resulting information.
         * This function will be passed a list of protocol objects, which will have the following properties:
         * <ul>
         *  <li>name: name of the saved protocol.</li>
         *  <li>description: description of the saved protocol, if provided.</li>
         *  <li>xmlParameters: bioml representation of the parameters defined by this protocol.</li>
         *  <li>jsonParameters: JSON representation of the parameters defined by this protocol.</li>
         * </ul>
         * @param {Function} [config.errorCallback] A function to call if an error occurs. This function
         * will receive one parameter of type object with the following properites:
         * <ul>
         *  <li>exception: The exception message.</li>
         * </ul>
         * @param {Object} [config.scope] The scope to use when calling the callbacks (defaults to this).
         */
        getProtocols : function(config) {
            var params = new Object();
            params.taskId = config.taskId;
            var containerPath = config.containerPath ? config.containerPath : LABKEY.ActionURL.getContainer();
            var url = LABKEY.ActionURL.buildURL("pipeline-analysis", "getSavedProtocols.api", containerPath);
            Ext.Ajax.request({
                url: url,
                method: 'POST',
                params: params,
                success: LABKEY.Utils.getCallbackWrapper(function(data, response){
                    if(config.successCallback)
                        config.successCallback(data.protocols, data.defaultProtocolName, response);
                }, this),
                failure: LABKEY.Utils.getCallbackWrapper(config.errorCallback, config.scope, true)
            });
        },

        /**
         * Gets the status of analysis using a particular protocol for a particular pipeline.
         *
         * @param {Object} config A configuration object with the following properties.
         * @param {String} config.taskId Identifier for the pipeline.
         * @param {String} config.path relative path from the folder's pipeline root
         * @param {String[]} config.files names of the file within the subdirectory described by the path property
         * @param {String} config.protocolName name of the analysis protocol
         * @param {String} [config.containerPath] The container in which to make the request (defaults to current container)
         * @param {Function} config.successCallback The function to call with the resulting information.
         * This function will be passed two arguments, a list of file status objects (described below) and the
         * name of the action that would be performed on the files if the user initiated processing
         * ('Retry' or 'Analyze', for example).
         * <ul>
         *  <li>name: name of the file, a String.</li>
         *  <li>status: status of the file, a String</li>
         * </ul>
         * @param {Function} [config.errorCallback] A function to call if an error occurs. This function
         * will receive one parameter of type object with the following properites:
         * <ul>
         *  <li>exception: The exception message.</li>
         * </ul>
         * @param {Object} [config.scope] The scope to use when calling the callbacks (defaults to this).
         */
        getFileStatus : function(config)
        {
            var params = new Object();
            params.taskId = config.taskId;
            params.path = config.path;
            params.file = config.files;
            params.protocolName = config.protocolName;
            var containerPath = config.containerPath ? config.containerPath : LABKEY.ActionURL.getContainer();
            var url = LABKEY.ActionURL.buildURL("pipeline-analysis", "getFileStatus.api", containerPath);
            Ext.Ajax.request({
                url: url,
                method: 'POST',
                timeout: 60000000,
                params: params,
                success: LABKEY.Utils.getCallbackWrapper(function(data, response){
                    if(config.successCallback)
                        config.successCallback(data.files, data.submitType, response);
                }, this),
                failure: LABKEY.Utils.getCallbackWrapper(config.errorCallback, config.scope, true)
            });
        },

        /**
         * Starts analysis of a set of files using a particular protocol definition with a particular pipeline.
         *
         * @param {Object} config A configuration object with the following properties.
         * @param {String} config.taskId Identifier for the pipeline.
         * @param {String} config.path relative path from the folder's pipeline root
         * @param {String[]} config.files names of the file within the subdirectory described by the path property
         * @param {String} config.protocolName name of the analysis protocol
         * @param {String} [config.protocolDescription] description of the analysis protocol
         * @param {String|Element} [config.xmlParameters] XML representation of the protocol description. Not allowed
         * if a protocol with the same name has already been saved. If no protocol with the same name exists, either
         * this property or jsonParameters must be specified.
         * @param {String|Object} [config.jsonParameters] JSON representation of the protocol description. Not allowed
         * if a protocol with the same name has already been saved. If no protocol with the same name exists, either
         * this property or xmlParameters must be specified.
         * @param {String} [config.saveProtocol] if no protocol with this name already exists, whether or not to save
         * this protocol definition for future use. Defaults to true.
         *
         * @param {String} [config.containerPath] The container in which to make the request (defaults to current container)
         * @param {Function} config.successCallback A function to call if this operation is successful.
         * @param {Function} [config.errorCallback] A function to call if an error occurs. This function
         * will receive one parameter of type object with the following properites:
         * <ul>
         *  <li>exception: The exception message.</li>
         * </ul>
         * @param {Object} [config.scope] The scope to use when calling the callbacks (defaults to this).
         */
        startAnalysis : function(config) {
            var params = new Object();
            params.taskId = config.taskId;
            params.path = config.path;
            if (!config.protocolName)
            {
                throw "Invalid config, must include protocolName property";
            }
            params.protocolName = config.protocolName;
            params.protocolDescription = config.protocolDescription;
            params.file = config.files;
            params.saveProtocol = config.saveProtocol == undefined || config.saveProtocol;
            if (config.xmlParameters)
            {
                // Convert from an Element to a string if needed
                params.configureXml = Ext.DomHelper.markup(config.xmlParameters);
            }
            else if (config.jsonParameters)
            {
                if (Ext.isString(config.jsonParameters))
                {
                    // We already have a string
                    params.configureJson = config.jsonParameters;
                }
                else
                {
                    // Convert from JavaScript object to a string
                    params.configureJson = Ext.util.JSON.encode(config.jsonParameters);
                }
            }
            var containerPath = config.containerPath ? config.containerPath : LABKEY.ActionURL.getContainer();
            var url = LABKEY.ActionURL.buildURL("pipeline-analysis", "startAnalysis.api", containerPath);
            Ext.Ajax.request({
                url: url,
                method: 'POST',
                params: params,
                timeout: 60000000,
                success: LABKEY.Utils.getCallbackWrapper(config.successCallback, config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(config.errorCallback, config.scope, true)
            });
        }
    };
};