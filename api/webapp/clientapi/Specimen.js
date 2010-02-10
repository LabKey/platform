/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 10.1
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
 * @namespace Specimen static class to retrieve and update specimen and specimen request information.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=manageSpecimens">Specimen Management for Administrators</a></li>
 *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=specimens">Specimen User Guide</a></li>
 *              </ul>
 *           </p>
*/
LABKEY.Specimen = new function()
{
    function getSuccessCallbackWrapper(successCallback, rootProperty)
    {
        return LABKEY.Utils.getCallbackWrapper(function(data){
            successCallback(rootProperty ? data[rootProperty] : data);
        }, this);
    }

    /** @scope LABKEY.Specimen */
    return {
        /**
         * Retrieves an array of locations that are identified as specimen repositories.
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
                  failure: LABKEY.Utils.getCallbackWrapper(failureCallback, this, true),
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          },

        // GetVialsByRowIdAction
        /**
         * Retrieves an array of vials that correspond to an array of unique vial row ids.
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
                  failure: LABKEY.Utils.getCallbackWrapper(failureCallback, this, true),
                  jsonData : { rowIds : vialRowIdArray },
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          },

        // GetOpenRequestsAction
        /**
         * Retrieves an array of open (non-final) specimen requests, including all requests that are in "shopping cart"
         * status as well as those that have been submitted for processing but are not yet complete.
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
                  failure: LABKEY.Utils.getCallbackWrapper(failureCallback, this, true),
                  jsonData : { allUsers: allUsers },
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          },

        // GetProvidingLocationsAction
        /**
         * Retrieves an array of locations that could provide vials from all identified primary specimens.
         * @param {Function} successCallback Required. Function called when the
                 "getProvidingLocations" function executes successfully.  Will be called with the argument:
                 {@link LABKEY.Specimen.Location[]}..
         * @param specimenHashArray {String[]} An array of hash codes identifying the primary specimens to be provided.
         * @param {Function} [failureCallback] Function called when execution of the "getProvidingLocations" function fails.
         * @param {String} [containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
        */
          getProvidingLocations : function(successCallback, specimenHashArray, failureCallback, containerPath)
          {
              if (!failureCallback)
                 failureCallback = LABKEY.Utils.displayAjaxErrorResponse;
              Ext.Ajax.request({
                  url : LABKEY.ActionURL.buildURL("study-samples-api", "getProvidingLocations", containerPath),
                  method : 'POST',
                  success: getSuccessCallbackWrapper(successCallback, 'locations'),
                  failure: LABKEY.Utils.getCallbackWrapper(failureCallback, this, true),
                  jsonData : { specimenHashes : specimenHashArray },
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          },

        // GetRequestAction
        /**
         * Retrieves a specimen request for a given specimen request ID.
         * @param {Function} successCallback Required. Function called when the
                 "getRequest" function executes successfully.  Will be called with the argument:
                 {@link LABKEY.Specimen.Request}.
         * @param {int} requestId The integer ID of the desired specimen request
         * @param {Function} [failureCallback] Function called when execution of the "getOpenRequests" function fails.
         * @param {String} [containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
        */
          getRequest : function(successCallback, requestId, failureCallback, containerPath)
          {
              if (!failureCallback)
                 failureCallback = LABKEY.Utils.displayAjaxErrorResponse;
              Ext.Ajax.request({
                  url : LABKEY.ActionURL.buildURL("study-samples-api", "getRequest", containerPath),
                  method : 'POST',
                  success: getSuccessCallbackWrapper(successCallback, 'request'),
                  failure: LABKEY.Utils.getCallbackWrapper(failureCallback, this, true),
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
                  failure: LABKEY.Utils.getCallbackWrapper(failureCallback, this, true),
                  jsonData : {requestId : requestId },
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          },

        // AddVialToRequestAction
        /**
         * Adds multiple vials to a request based on an array of unique unique vial IDs.  If called by a non-administrator, the target request must be owned by the
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
                  failure: LABKEY.Utils.getCallbackWrapper(failureCallback, this, true),
                  jsonData : {requestId : requestId, vialIds : vialIdArray, idType: idType},
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          },

        // AddSampleToRequestAction
        /**
         * Adds multiple vials to a request based on an array of hash codes uniquely identifying the primary specimens.  The vials will
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
                  failure: LABKEY.Utils.getCallbackWrapper(failureCallback, this, true),
                  jsonData : {requestId : requestId, specimenHashes : specimenHashArray, preferredLocation : preferredLocation},
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          },


        // RemoveVialFromRequestAction
        /**
         * Removes multiple vials from a request based on an array of vial row IDs.  If called by a non-administrator, the target request must be owned by the
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
                  failure: LABKEY.Utils.getCallbackWrapper(failureCallback, this, true),
                  headers : {
                      'Content-Type' : 'application/json'
                  }
              });
          }
    };
};

 /**
* @name LABKEY.Specimen.Location
* @class   Location class to describe the shape and fields of a specimen location.
  *            <p>Additional Documentation:
  *              <ul>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=manageSpecimens">Specimen Management for Administrators</a></li>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=specimens">Specimen User Guide</a></li>
  *              </ul>
  *           </p>
*/

/**#@+
* @memberOf LABKEY.Specimen.Location#
* @field
*/

/**
* @name    endpoint
* @description   Whether this location is an endpoint lab.  May be null if unspecified.
* @type       Boolean
*/

/**
* @name        entityId
* @description    An entity Id uniquely identifying this location.
* @type        String
*/

/**
* @name        label
* @description   The display name for this location.
* @type        String
*/

/**
* @name          labUploadCode
* @description    The location upload code. May be null if unspecified.
* @type      String
*/

/**
* @name    labwareLabCode
* @description    The labware location code. May be null if unspecified.
* @type       String
*/

/**
* @name       ldmsLabCode
* @description    The LDMS location code. May be null if unspecified.
* @type         String
*/

/**
* @name        repository
* @description  Whether this location is a repository. May be null if unspecified.
* @type       Boolean
*/

/**
* @name      LABKEY.Specimen.Location#rowId
* @description   An integer uniquely identifying this location.
* @type   Integer
*/

/**
* @name   SAL
* @description  Whether this location is a Site Affiliated Laboratory.  May be null if unspecified.
* @type     Boolean
*/

/**
* @name   clinic
* @description  Whether this location is a clinic.  May be null if unspecified.
* @type    Boolean
*/

/**
* @name     externalId
* @description  The unique identifier for locations imported from an external database of record.  May be null.
* @type    Integer
*/

/**#@-*/

 /**
* @name LABKEY.Specimen.Request
* @class   Request class to describe the shape and fields of a specimen request.
  *            <p>Additional Documentation:
  *              <ul>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=manageSpecimens">Specimen Management for Administrators</a></li>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=specimens">Specimen User Guide</a></li>
  *              </ul>
  *           </p>
* @see LABKEY.Specimen.Location
* @see LABKEY.Specimen.Vial
*/

/**#@+
* @memberOf LABKEY.Specimen.Request#
* @field
*/

/**
* @name      requestId
* @description   The unique ID for this request.
* @type            Integer
*/

/**
* @name      comments
* @description    All comments associated with this request.
* @type       String
*/

/**
* @name            created
* @description   The date and time that this request was created.
* @type        Date
*/

/**
* @name  createdBy
* @description  An object describing the user that created this request.  This object has two properties:
*          <ul>
*              <li><b>userId:</b> The user's ID</li>
*              <li><b>displayName:</b> The user's display name</li>
*          </ul>
* @type      Object
*/

/**
* @name         destination
* @description   Indicates which location that will receive the requested vials.
* @type         LABKEY.Specimen.Location
*/

/**
* @name       statusId
* @description   The unique identifier of the request's current status.
* @type        Integer
*/

/**
* @name          status
* @description   The string label of the request's current status.
* @type      String
*/

/**
* @name  vials
* @description  An array of objects of type {@link LABKEY.Specimen.Vial}, indicating which vials are part of this request.
* @type     Object
*/

/**#@-*/



 /**
* @name LABKEY.Specimen.Vial
* @class  Vial class to describe the shape and fields of a specimen vial.
  *            <p>Additional Documentation:
  *              <ul>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=manageSpecimens">Specimen Management for Administrators</a></li>
  *                  <li><a href="https://www.labkey.org/wiki/home/Documentation/page.view?name=specimens">Specimen User Guide</a></li>
  *              </ul>
  *           </p>
* @see LABKEY.Specimen.Location
*/

/**#@+
* @memberOf LABKEY.Specimen.Vial#
* @field
*/

/**
* @name  globalUniqueId
* @description  The global unique ID of this vial
* @type  String
*/

/**
* @name rowId
* @description  The unique database Row ID of this vial
* @type Integer
*/

/**
* @name ptid
* @description  The ID of the participant providing this vial.
* @type String
*/

/**
* @name visitValue
* @description  The visit value at which this specimen was collected.
* @type Double
*/

/**
* @name primaryTypeId
* @description  The unique identifier of this vial's primary specimen type.
* @type Integer
*/

/**
* @name primaryType
* @description   The label of this vial's primary specimen type.
* @type  String
*/

/**
* @name        derivativeTypeId
* @description  The unique identifier of this vial's derivative type.
* @type      Integer
*/

/**
* @name  derivativeType
* @description   The label of this vial's derivative type.
* @type     String
*/

/**
* @name        additiveTypeId
* @description     The unique identifier of this vial's additive type.
* @type       Integer
*/

/**
* @name   additiveType
* @description   The label of this vial's additive type.
* @type      String
*/

/**
* @name currentLocation
* @description  A {@link LABKEY.Specimen.Location} object indicating this vial's current location.
* @type    LABKEY.Specimen.Location
*/

/**
* @name     originatingLocation
* @description   A {@link LABKEY.Specimen.Location} object indicating this vial's originating location.
* @type         LABKEY.Specimen.Location
*/

/**
* @name        subAdditiveDerivative
* @description    A label of this vial's sub-additive/derivative type.
* @type         String
*/

/**
* @name       specimenHash
* @description    A  hash code uniquely identifying this vial's ptid, visit, and type information.
*                 This value can be used to group vials into primary specimen draws.
* @type         String
*/

/**
* @name         volume
* @description    The volume of this vial.
* @type       Double
*/

/**
* @name      volumeUnits
* @description   The volume units for this vial.
* @type      String
*/

/**#@-*/