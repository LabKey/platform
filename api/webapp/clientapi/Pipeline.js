/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 10.1
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
         * @param {Object} config A configuration object with the following properites.
         * @param {Function} config.successCallback The function to call with the resulting information.
         * This function will be passed a single parameter of type object, which will have the following
         * properites:
         * <ul>
         *  <li>containerPath: the container path in which the pipeline is defined. If no pipeline has
         * been defined in this container hierarhcy, the value of this property will be null.</li>
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
        }
    };
};