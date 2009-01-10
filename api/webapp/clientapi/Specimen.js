/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 8.1
 * @license Copyright (c) 2008 LabKey Corporation
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
 * @namespace Specimen static class to retrieve and update specimen and specimen request information.
*/
LABKEY.Specimen = new function()
{
    function getSuccessCallbackWrapper(successCallback, rootProperty)
    {
        return function(response, options)
        {
            var data = Ext.util.JSON.decode(response.responseText);
            successCallback(rootProperty ? data[rootProperty] : data);
        };
    }

    /** @scope LABKEY.Specimen.prototype */
    return {
        /**
         * @param {Function} successCallback Required. Function called when the
                 "getAll" function executes successfully.  Will be called with the argument:
                 {@link LABKEY.Specimen.Location[]}.
         * @param {Function} [failureCallback] Function called when execution of the "getRespositories" function fails.
         * @param {String} [containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
        * Retrieves an array of all locations identified as repositories within the specified study.
        */
          getRepositories : function(successCallback, failureCallback, containerPath)
          {
              if (!failureCallback)
                 failureCallback = LABKEY.Utils.displayAjaxErrorResponse;
              Ext.Ajax.request({
                  url : LABKEY.ActionURL.buildURL("study-samples-api", "getRespositories", containerPath),
                  method : 'POST',
                  success: getSuccessCallbackWrapper(successCallback, 'repositories'),
                  failure: failureCallback,
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          },

        // GetVialsByRowIdAction
        /**
         * @param {Function} successCallback Required. Function called when the
                 "getVialsByRowId" function executes successfully.  Will be called with the argument:
                 {@link LABKEY.Specimen.Vial[]}.
         * @param {Function} vialRowIdArray An array of integer vial row IDs.
         * @param {Function} [failureCallback] Function called when execution of the "getVialsByRowId" function fails.
         * @param {String} [containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
        * Retrieves an array of all locations identified as repositories within the specified study.
        */
          getVialsByRowId : function(successCallback, vialRowIdArray, failureCallback, containerPath)
          {
              if (!failureCallback)
                 failureCallback = LABKEY.Utils.displayAjaxErrorResponse;
              Ext.Ajax.request({
                  url : LABKEY.ActionURL.buildURL("study-samples-api", "getVialsByRowId", containerPath),
                  method : 'POST',
                  success: getSuccessCallbackWrapper(successCallback, 'vials'),
                  failure: failureCallback,
                  jsonData : { rowIds : vialRowIdArray },
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          },

        // GetOpenRequestsAction
        /**
         * @param {Function} successCallback Required. Function called when the
                 "getOpenRequests" function executes successfully.  Will be called with the argument:
                 {@link LABKEY.Specimen.Request[]}.
         * @param {Boolean} [allUsers] Indicates whether to retrive open requests for all users, rather than just those created
         * by the current user.  If not supplied, requests will be returned based on the user's permission.  Administrators will
         * see all open requests, while non-admin users will only see those requests that they have created.
         * @param {Function} [failureCallback] Function called when execution of the "getOpenRequests" function fails.
         * @param {String} [containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
        * Retrieves an array of open requests within the specified study.
        */
          getOpenRequests : function(successCallback, allUsers, failureCallback, containerPath)
          {
              if (!failureCallback)
                 failureCallback = LABKEY.Utils.displayAjaxErrorResponse;
              Ext.Ajax.request({
                  url : LABKEY.ActionURL.buildURL("study-samples-api", "getOpenRequests", containerPath),
                  method : 'POST',
                  success: getSuccessCallbackWrapper(successCallback, 'requests'),
                  failure: failureCallback,
                  jsonData : { allUsers: allUsers },
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          },

        // GetRequestAction
        /**
         * @param {Function} successCallback Required. Function called when the
                 "getRequest" function executes successfully.  Will be called with the argument:
                 {@link LABKEY.Specimen.Request}.
         * @param {int} requestId The integer ID of
         * @param {Function} [failureCallback] Function called when execution of the "getOpenRequests" function fails.
         * @param {String} [containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
        * Retrieves an array of open requests within the specified study.
        */
          getRequest : function(successCallback, requestId, failureCallback, containerPath)
          {
              if (!failureCallback)
                 failureCallback = LABKEY.Utils.displayAjaxErrorResponse;
              Ext.Ajax.request({
                  url : LABKEY.ActionURL.buildURL("study-samples-api", "getRequest", containerPath),
                  method : 'POST',
                  success: getSuccessCallbackWrapper(successCallback, 'request'),
                  failure: failureCallback,
                  jsonData : { requestId: requestId },
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          },

        // DeleteRequestAction
        /**
         * Completely and permanently cancels a request.  THIS ACTION CANNOT BE UNDONE.
         * If called by a non-administrator, the target request must be owned by the
         * calling user, and the request must be in an open (not yet submitted) state.  Administrators may delete
         * requests at any time.
         * @param {Function} successCallback Required. Function called when the
                 "addVialToRequest" function executes successfully.  No arguments are provided.
         * @param requestId {int} The unique integer identifier of the target request.
         * @param {Function} [failureCallback] Function called when execution of the "addVialToRequest" function fails.
         * @param {String} [containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
        */
          cancelRequest : function(successCallback, requestId, failureCallback, containerPath)
          {
              if (!failureCallback)
                 failureCallback = LABKEY.Utils.displayAjaxErrorResponse;
              Ext.Ajax.request({
                  url : LABKEY.ActionURL.buildURL("study-samples-api", "cancelRequest", containerPath),
                  method : 'POST',
                  success: getSuccessCallbackWrapper(successCallback),
                  failure: failureCallback,
                  jsonData : {requestId : requestId },
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          },

        // AddVialToRequestAction
        /**
         * Adds a single vial to a request.  If called by a non-administrator, the target request must be owned by the
         * calling user, and the request must be in an open (not yet submitted) state.  Administrators may add vials
         * to any request at any time.
         * @param {Function} successCallback Required. Function called when the
                 "addVialToRequest" function executes successfully.  Will be called with a single argument
                 {@link LABKEY.Specimen.Request}, containing the newly modified request.
         * @param requestId {int} The unique integer identifier of the target request.
         * @param vialIdArray {String[]} An array of global unique vial IDs to add to the target request.
         * @param {String} [idType] A string constant indicating how vials are identified.  This must be either
         * "GlobalUniqueId" or "RowId".  If undefined, "GlobalUniqueId" is assumed.
         * @param {Function} [failureCallback] Function called when execution of the "addVialToRequest" function fails.
         * @param {String} [containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
        */
          addVialsToRequest : function(successCallback, requestId, vialIdArray, idType, failureCallback, containerPath)
          {
              if (!failureCallback)
                 failureCallback = LABKEY.Utils.displayAjaxErrorResponse;
              if (!idType)
                idType = "GlobalUniqueId";
              Ext.Ajax.request({
                  url : LABKEY.ActionURL.buildURL("study-samples-api", "addVialsToRequest", containerPath),
                  method : 'POST',
                  success: getSuccessCallbackWrapper(successCallback),
                  failure: failureCallback,
                  jsonData : {requestId : requestId, vialIds : vialIdArray, idType: idType},
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          },

        // AddSampleToRequestAction
        /**
         * Adds a single vial to a request based on a hash code uniquely identifying the primary specimen.  The vial will
         * be selected based on availability and current location.  If called by a non-administrator, the target request must be owned by the
         * calling user, and the request must be in an open (not yet submitted) state.  Administrators may add vials
         * to any request at any time.
         * @param {Function} successCallback Required. Function called when the
                 "addSampleToRequest" function executes successfully.  Will be called with a single argument
                 {@link LABKEY.Specimen.Request}, containing the newly modified request.
         * @param requestId {int} The unique integer identifier of the target request.
         * @param specimenHashArray {String[]} An array of hash codes identifying the primary specimens to add to the target request.
         * @param preferredLocation {int} The unique ID of the preferred providing location.  If more than on providing location is possible,
         * and if the request does not already contain vials uniquely identifying one of these providing locations, this
         * parameter must be provided.
         * @param {Function} [failureCallback] Function called when execution of the "addSampleToRequest" function fails.
         * @param {String} [containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
        */
          addSamplesToRequest : function(successCallback, requestId, specimenHashArray, preferredLocation, failureCallback, containerPath)
          {
              if (!failureCallback)
                 failureCallback = LABKEY.Utils.displayAjaxErrorResponse;
              Ext.Ajax.request({
                  url : LABKEY.ActionURL.buildURL("study-samples-api", "addSamplesToRequest", containerPath),
                  method : 'POST',
                  success: getSuccessCallbackWrapper(successCallback),
                  failure: failureCallback,
                  jsonData : {requestId : requestId, specimenHashes : specimenHashArray, preferredLocation : preferredLocation},
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          },


        // RemoveVialFromRequestAction
        /**
         * Removes a single vial from a request.  If called by a non-administrator, the target request must be owned by the
         * calling user, and the request must be in an open (not yet submitted) state.  Administrators may remove vials
         * from any request at any time.
         * @param {Function} successCallback Required. Function called when the
                 "removeVialFromRequest" function executes successfully.  Will be called with a single argument
                 {@link LABKEY.Specimen.Request}, containing the newly modified request.
         * @param requestId {int} The unique integer identifier of the target request.
         * @param vialIdArray {String[]} An array of global unique vial IDs to remove from the target request.
         * @param {String} [idType] A string constant indicating how vials are identified.  This must be either
         * "GlobalUniqueId" or "RowId".  If undefined, "GlobalUniqueId" is assumed.
         * @param {Function} [failureCallback] Function called when execution of the "removeVialFromRequest" function fails.
         * @param {String} [containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
        */
          removeVialsFromRequest : function(successCallback, requestId, vialIdArray, idType, failureCallback, containerPath)
          {
              if (!failureCallback)
                 failureCallback = LABKEY.Utils.displayAjaxErrorResponse;
              if (!idType)
                idType = "GlobalUniqueId";
              Ext.Ajax.request({
                  url : LABKEY.ActionURL.buildURL("study-samples-api", "removeVialsFromRequest", containerPath),
                  method : 'POST',
                  success: getSuccessCallbackWrapper(successCallback),
                  jsonData : {requestId : requestId, vialIds : vialIdArray, idType : idType},
                  failure: failureCallback,
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          }
    };
};

/**
* @namespace
* @description Location static class to describe the shape and fields of a specimen location.
*
* @property {Boolean} endpoint Whether this location is an endpoint lab.  May be null if unspecified.
* @property {String} entityId An entity Id uniquely identifying this location.
* @property {String} label The display name for this location.
* @property {String} labUploadCode The location upload code. May be null if unspecified.
* @property {String} labwareLabCode The labware location code. May be null if unspecified.
* @property {String} ldmsLabCode  The LDMS location code. May be null if unspecified.
* @property {Boolean} repository Whether this location is a repository. May be null if unspecified.
* @property {int} rowId An integer uniquely identifying this location.
* @property {Boolean} SAL Whether this location is a Site Affiliated Laboratory.  May be null if unspecified.
* @property {Boolean} clinic Whether this location is a clinic.  May be null if unspecified.
* @property {int} externalId The unique identifier for locations imported from an external database of record.  May be null.
*/
LABKEY.Specimen.Location = new function() {};

/**
* @namespace
* @description Request static class to describe the shape and fields of a specimen request.
* @see LABKEY.Specimen.Location
* @see LABKEY.Specimen.Vial
*
* @property {int} requestId The unique ID for this request.
* @property {String} comments All comments associated with this request.
* @property {Date} created The date and time that this request was created.
* @property {int} createdBy The user Id of the user that created this request.
* @property {Object} createdBy An object describing the user that created this request.  This object has two properties:
*          <ul>
*              <li>userId: the user's id</li>
*              <li>displayName: the user's display name</li>
*          </ul>
* @property {Object} destination An object of type Location indicating which location that will receive the requested vials.
* @property {int} statusId The unique identifier of the request's current status.
* @property {String} status  The string label of the request's current status.
* @property {Object} vials An array of objects of type Vial, indicating which vials are part of this request.
*/
LABKEY.Specimen.Request = new function() {};

/**
* @namespace
* @description Vial static class to describe the shape and fields of a specimen vial.
* @see LABKEY.Specimen.Location
*
* @property {String} globalUniqueId The global unique ID of this vial
 * @property {int} rowId The unique database Row ID of this vial
* @property {String} ptid The ID of the participant providing this vial.
* @property {Double} visitValue The visit value at which this specimen was collected.
* @property {int} primaryTypeId The unique integer identifier of this vial's primary specimen type.
* @property {String} primaryType The string label of this vial's primary specimen type.
* @property {int} derivativeTypeId The unique integer identifier of this vial's derivative type.
* @property {String} derivativeType The string label of this vial's derivative type.
* @property {int} additiveTypeId The unique integer identifier of this vial's additive type.
* @property {String} additiveType The string label of this vial's additive type.
* @property {Object} currentLocation A Location object indicating this vial's current location.
* @property {Object} originatingLocation A Location object indicating this vial's originating location.
 * @property {String} subAdditiveDerivative A string label of this vial's sub-additive/derivative type.
 * @property {String} specimenHash A string hash code uniquely identifying this vial's ptid, visit, and type information.
 * This value can be used to group vials into primary specimen draws.
* @property {Double} volume The volume of this vial.
* @property {String} volumeUnits The volume units for this vial.
 *
 */
LABKEY.Specimen.Vial = new function() {};