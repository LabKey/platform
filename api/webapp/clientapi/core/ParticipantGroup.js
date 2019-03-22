/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2009-2016 LabKey Corporation
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
 * @namespace Participant group static class to update participant group information.
 *            <p>Additional Documentation:
 *              <ul>
 *              </ul>
 *           </p>
 */
LABKEY.ParticipantGroup = new function()
{
    function getSuccessCallbackWrapper(successCallback, rootProperty)
    {
        return LABKEY.Utils.getCallbackWrapper(function(data){
            successCallback(rootProperty ? data[rootProperty] : data);
        }, this);
    }

    function sendJsonQueryRequest(config) {
        LABKEY.Ajax.request({
            url : config.url,
            method : config.method || 'POST',
            success : config.success,
            failure : config.failure,
            jsonData : config.jsonData,
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    }

    /** @scope LABKEY.ParticipantGroup */
    return {
        // UpdateParticipantGroupAction
        /**
         * Updates an existing participant group, already saved and accessible to the current user on the server.
         * @param {Object} config An object which contains the following configuration properties. The group properties
         * are not included in the config, their values will remain unchanged.
         * @param {Function} config.success Required. Function called when the
         request executes successfully.  Will be called with the arguments:
         <ul>
            <li>group: the new state of the participant group, with properties rowId, participantIds, label and description.
            <li>response: the full response object from the AJAX request.
         </ul>
         * @param {Function} [config.failure] Function called when execution of the request fails.
         * @param {int} config.rowId The integer ID of the desired participant group
         * @param {Array} [config.participantId] Set of IDs to be members of the group
         * @param {Array} [config.ensureParticipantIds] Set of IDs to be added to the group if they are not already members
         * @param {Array} [config.deleteParticipantIds] Set of IDs to be removed from the group if they are already members
         * @param {Array} [config.label] The new value for the label of the group
         * @param {Array} [config.description] The new value for the description of the group
         * @param {String} [config.containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
         */
        updateParticipantGroup : function(config)
        {
            var group = {};
            if (config.rowId) {
                group.rowId = config.rowId;
            }
            if (config.participantIds) {
                group.participantIds = config.participantIds;
            }
            if (config.ensureParticipantIds) {
                group.ensureParticipantIds = config.ensureParticipantIds;
            }
            if (config.deleteParticipantIds) {
                group.deleteParticipantIds = config.deleteParticipantIds;
            }
            if (config.label) {
                group.label = config.label;
            }
            if (config.description) {
                group.description = config.description;
            }
            if (config.filters) {
                group.filters = config.filters;
            }

            sendJsonQueryRequest({
                url : LABKEY.ActionURL.buildURL("participant-group", "updateParticipantGroup.api", config.containerPath),
                success: getSuccessCallbackWrapper(config.success, 'group'),
                failure: LABKEY.Utils.getCallbackWrapper(config.failure, this, true),
                jsonData : group
            });
        }
    };
};

/**#@-*/
