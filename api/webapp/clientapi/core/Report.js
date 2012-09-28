/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2012 LabKey Corporation
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
 * @namespace Report static class that allows programmatic manipulation of reports and their underlying engines.
 */
LABKEY.Report = new function(){
    // private methods and data

    //public interface
    /** @scope LABKEY.Report */
    return {
        /**
         * Creates a new report session which can be used across multiple report requests.  For example,
         * this allows an R script to setup an R environment and then use this environment in
         * subsequent R scripts.
         * @param {Object} config A configuration object with the following properties.
         * @param {Function} config.success The function to call with the resulting information.
         * This function will be passed a single parameter of type object, which will have the following
         * properties:
         * <ul>
         *  <li>reportSessionId: A unique identifier that represents the new underlying report session, a String</li>
         * </ul>
         * @param {Function} [config.failure] A function to call if an error occurs. This function
         * will receive one parameter of type object with the following properites:
         * <ul>
         *  <li>exception: The exception message.</li>
         * </ul>
         * @param {String} [config.containerPath] The container in which to make the request (defaults to current container)
         * @param {Object} [config.scope] The scope to use when calling the callbacks (defaults to this).
         */
        createSession : function(config) {
            var containerPath = config && config.containerPath;
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("reports", "createSession", containerPath ),
                method: 'POST',
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        },
        /**
         * Deletes an underlying report session
         *
         * @param {Object} config A configuration object with the following properties.
         * @param {String} config.reportSessionId Identifier for the report session to delete.
         * @param {Function} config.success The function to call if the operation is successful.
         * @param {Function} [config.failure] A function to call if an error occurs. This function
         * will receive one parameter of type object with the following properites:
         * <ul>
         *  <li>exception: The exception message.</li>
         * </ul>
         * @param {String} [config.containerPath] The container in which to make the request (defaults to current container)
         * @param {Object} [config.scope] The scope to use when calling the callbacks (defaults to this).
         */
        deleteSession : function(config) {
            var params = { reportSessionId : config.reportSessionId };
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("reports", "deleteSession", config.containerPath),
                method: 'POST',
                params: params,
                success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true)
            });
        }
  };
};