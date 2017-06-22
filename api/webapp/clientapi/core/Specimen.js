/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2009-2017 LabKey Corporation
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

    /** @scope LABKEY.Specimen */
    return {
        /**
         * Retrieves an array of locations that are identified as specimen repositories.
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Function} config.success Required. Function called when the
         "getAll" function executes successfully.  Will be called with the argument:
         {@link LABKEY.Specimen.Location[]}.
         * @param {Function} [config.failure] Function called when execution of the "getRespositories" function fails.
         * @param {String} [config.containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
         * Retrieves an array of all locations identified as repositories within the specified study.
         */
        getRepositories : function(config)
        {
            if (config && (LABKEY.Utils.isFunction(config) || arguments.length > 1)){
                config = {
                    success : arguments[0],
                    failure : arguments[1],
                    containerPath : arguments[2]
                };
            }

            sendJsonQueryRequest({
                url : LABKEY.ActionURL.buildURL("study-samples-api", "getRespositories", config.containerPath),
                success : getSuccessCallbackWrapper(config.success, 'repositories'),
                failure : LABKEY.Utils.getCallbackWrapper(config.failure || LABKEY.Utils.displayAjaxErrorResponse, this, true)
            });
        },

        // GetVialsByRowIdAction
        /**
         * Retrieves an array of vials that correspond to an array of unique vial row ids.
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Function} config.success Required. Function called when the
         "getVialsByRowId" function executes successfully.  Will be called with the argument:
         {@link LABKEY.Specimen.Vial[]}.
         * @param {Function} config.vialRowIdArray An array of integer vial row IDs.
         * @param {Function} [config.failure] Function called when execution of the "getVialsByRowId" function fails.
         * @param {String} [config.containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
         * Retrieves an array of all locations identified as repositories within the specified study.
         */
        getVialsByRowId : function(config)
        {
            if (config && (LABKEY.Utils.isFunction(config) || arguments.length > 1)){
                config = {
                    success : arguments[0],
                    vialRowIdArray : arguments[1],
                    failure : arguments[2],
                    containerPath : arguments[3]
                };
            }

            sendJsonQueryRequest({
                url : LABKEY.ActionURL.buildURL("study-samples-api", "getVialsByRowId", config.containerPath),
                success : getSuccessCallbackWrapper(config.success, 'vials'),
                failure: LABKEY.Utils.getCallbackWrapper(config.failure || LABKEY.Utils.displayAjaxErrorResponse, this, true),
                jsonData : { rowIds : config.vialRowIdArray }
            });
        },

        // GetOpenRequestsAction
        /**
         * Retrieves an array of open (non-final) specimen requests, including all requests that are in "shopping cart"
         * status as well as those that have been submitted for processing but are not yet complete.
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Function} config.success Required. Function called when the
         "getOpenRequests" function executes successfully.  Will be called with the argument:
         {@link LABKEY.Specimen.Request[]}.
         * @param {Boolean} [config.allUsers] Indicates whether to retrieve open requests for all users, rather than just those created
         * by the current user.  If not supplied, requests will be returned based on the user's permission.  Administrators will
         * see all open requests, while non-admin users will only see those requests that they have created.
         * @param {Function} [config.failure] Function called when execution of the "getOpenRequests" function fails.
         * @param {String} [config.containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
         * Retrieves an array of open requests within the specified study.
         */
        getOpenRequests : function(config)
        {
            if (config && (LABKEY.Utils.isFunction(config) || arguments.length > 1)){
                config = {
                    success : arguments[0],
                    allUsers: arguments[1],
                    failure : arguments[2],
                    containerPath : arguments[3]
                };
            }

            // Unfortunately, we need to reverse our parameter order here- LABKEY.Utils uses inconsistent ordering for its
            // default callback and callback wrapper functions:
            if (!config.failure)
                config.failure = function(error, response) { return LABKEY.Utils.displayAjaxErrorResponse(response, error); };

            sendJsonQueryRequest({
                url : LABKEY.ActionURL.buildURL("study-samples-api", "getOpenRequests", config.containerPath),
                success : getSuccessCallbackWrapper(config.success, 'requests'),
                failure : LABKEY.Utils.getCallbackWrapper(config.failure, this, true),
                jsonData : { allUsers: config.allUsers }
            });
        },

        // GetProvidingLocationsAction
        /**
         * Retrieves an array of locations that could provide vials from all identified primary specimens.
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Function} config.success Required. Function called when the
         "getProvidingLocations" function executes successfully.  Will be called with the argument:
         {@link LABKEY.Specimen.Location[]}..
         * @param {String[]} config.specimenHashArray An array of hash codes identifying the primary specimens to be provided.
         * @param {Function} [config.failure] Function called when execution of the "getProvidingLocations" function fails.
         * @param {String} [config.containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
         */
        getProvidingLocations : function(config)
        {
            if (config && (LABKEY.Utils.isFunction(config) || arguments.length > 1)){
                config = {
                    success : arguments[0],
                    specimenHashArray : arguments[1],
                    failure : arguments[2],
                    containerPath : arguments[3]
                };
            }

            // Unfortunately, we need to reverse our parameter order here- LABKEY.Utils uses inconsistent ordering for its
            // default callback and callback wrapper functions:
            if (!config.failure)
                config.failure = function(error, response) { return LABKEY.Utils.displayAjaxErrorResponse(response, error); };

            sendJsonQueryRequest({
                url : LABKEY.ActionURL.buildURL("study-samples-api", "getProvidingLocations", config.containerPath),
                success : getSuccessCallbackWrapper(config.success, 'locations'),
                failure : LABKEY.Utils.getCallbackWrapper(config.failure, this, true),
                jsonData: { specimenHashes : config.specimenHashArray }
            });
        },

        // GetVialTypeSummaryAction
        /**
         * Retrieves a specimen request for a given specimen request ID.
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Function} config.success Required. Function called when the
         *        "getVialTypeSummary" function executes successfully.  Will be called with a single argument with the following top-level properties:
         * <ul>
         *  <li>primaryTypes</li>
         *  <li>derivativeTypes</li>
         *  <li>additiveTypes</li>
         * </ul>
         *
         * The value of each of these properties is an array of objects with four properties:
         * <ul>
         *  <li>label: the text label of the specimen type.</li>
         *  <li>count: the number of vials of this type.  This count will reflect any parent types as well (see 'children' below).</li>
         *  <li>url: the URL that can be used to access the list of these vials.</li>
         *  <li>children: an array of sub-types.  May be undefined if no child types are available.  If present, each child array element has the same properties described in this list.</li>
         * </ul>
         * @param {Function} [config.failure] Function called when execution of the "getOpenRequests" function fails.
         * @param {String} [config.containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
         */
        getVialTypeSummary : function(config)
        {
            // Unfortunately, we need to reverse our parameter order here- LABKEY.Utils uses inconsistent ordering for its
            // default callback and callback wrapper functions:
            if (!config.failure)
                config.failure = function(error, response) { return LABKEY.Utils.displayAjaxErrorResponse(response, error); };

            sendJsonQueryRequest({
                url : LABKEY.ActionURL.buildURL("study-samples-api", "getVialTypeSummary", config.containerPath),
                success: getSuccessCallbackWrapper(config.success),
                failure: LABKEY.Utils.getCallbackWrapper(config.failure, this, true)
            });
        },

        // GetSpecimenWebPartGroupsAction
        /**
         * Retrieves a specimen request for a given specimen request ID.
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Function} config.success Required. Function called when the
         *        "getSpecimenWebPartGroups" function executes successfully.  Will be called with a single argument with the following top-level properties:
         * <ul>
         *  <li>groupings: [group...]</li>
         * </ul>
         * The value of this property is an array of group objects, each with two properties:
         *  <li>name: the text label of the column.</li>
         *  <li>values: the value of this property is an array ofobjects with four properties:
         * <ul>
         *  <li>label: the text label of the specimen type.</li>
         *  <li>count: the number of vials of this type.  This count will reflect any parent types as well (see 'children' below).</li>
         *  <li>url: the URL that can be used to access the list of these vials.</li>
         *  <li>group: an optional sub-group.</li>
         * </ul>
         * @param {Function} [config.failure] Function called when execution of the "getOpenRequests" function fails.
         * @param {String} [config.containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
         */
        getSpecimenWebPartGroups : function(config)
        {
            // Unfortunately, we need to reverse our parameter order here- LABKEY.Utils uses inconsistent ordering for its
            // default callback and callback wrapper functions:
            if (!config.failure)
                config.failure = function(error, response) { return LABKEY.Utils.displayAjaxErrorResponse(response, error); };

            sendJsonQueryRequest({
                url : LABKEY.ActionURL.buildURL("study-samples-api", "getSpecimenWebPartGroups", config.containerPath),
                success: getSuccessCallbackWrapper(config.success),
                failure: LABKEY.Utils.getCallbackWrapper(config.failure, this, true)
            });
        },

        // GetRequestAction
        /**
         * Retrieves a specimen request for a given specimen request ID.
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Function} config.success Required. Function called when the
         "getRequest" function executes successfully.  Will be called with the argument:
         {@link LABKEY.Specimen.Request}.
         * @param {int} config.requestId The integer ID of the desired specimen request
         * @param {Function} [config.failure] Function called when execution of the "getOpenRequests" function fails.
         * @param {String} [config.containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
         */
        getRequest : function(config)
        {
            if (config && LABKEY.Utils.isFunction(config) || arguments.length > 1){
                config = {
                    success : arguments[0],
                    requestId : arguments[1],
                    failure : arguments[2],
                    containerPath : arguments[3]
                };
            }

            // Unfortunately, we need to reverse our parameter order here- LABKEY.Utils uses inconsistent ordering for its
            // default callback and callback wrapper functions:
            if (!config.failure)
                config.failure = function(error, response) { return LABKEY.Utils.displayAjaxErrorResponse(response, error); };

            sendJsonQueryRequest({
                url : LABKEY.ActionURL.buildURL("study-samples-api", "getRequest", config.containerPath),
                success: getSuccessCallbackWrapper(config.success, 'request'),
                failure: LABKEY.Utils.getCallbackWrapper(config.failure, this, true),
                jsonData : { requestId: config.requestId }
            });
        },

        // DeleteRequestAction
        /**
         * Completely and permanently cancels a request.  THIS ACTION CANNOT BE UNDONE.
         * If called by a non-administrator, the target request must be owned by the
         * calling user, and the request must be in an open (not yet submitted) state.  Administrators may delete
         * requests at any time.
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Function} config.success Required. Function called when the
         "addVialToRequest" function executes successfully.  No arguments are provided.
         * @param {int} config.requestId The unique integer identifier of the target request.
         * @param {Function} [config.failure] Function called when execution of the "addVialToRequest" function fails.
         * @param {String} [config.containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
         */
        cancelRequest : function(config)
        {
            if (config && (LABKEY.Utils.isFunction(config) || arguments.length > 1)){
                config = {
                    success : arguments[0],
                    requestId : arguments[1],
                    failure : arguments[2],
                    containerPath : arguments[3]
                }
            }

            // Unfortunately, we need to reverse our parameter order here- LABKEY.Utils uses inconsistent ordering for its
            // default callback and callback wrapper functions:
            if (!config.failure)
                config.failure = function(error, response) { return LABKEY.Utils.displayAjaxErrorResponse(response, error); };

            sendJsonQueryRequest({
                url : LABKEY.ActionURL.buildURL("study-samples-api", "cancelRequest", config.containerPath),
                success: getSuccessCallbackWrapper(config.success),
                failure: LABKEY.Utils.getCallbackWrapper(config.failure, this, true),
                jsonData : {requestId : config.requestId }
            });
        },

        // AddVialToRequestAction
        /**
         * Adds multiple vials to a request based on an array of unique unique vial IDs.  If called by a non-administrator,
         * the target request must be owned by the calling user, and the request must be in an open (not yet submitted)
         * state.  Administrators may add vials to any request at any time.
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Function} config.success Required. Function called when the
         "addVialToRequest" function executes successfully.  Will be called with a single argument
         {@link LABKEY.Specimen.Request}, containing the newly modified request.
         * @param {int} config.requestId The unique integer identifier of the target request.
         * @param {Array} config.vialIdArray An array of global unique vial IDs to add to the target request.
         * @param {String} [config.idType] A string constant indicating how vials are identified.  This must be either
         * "GlobalUniqueId" or "RowId".  If undefined, "GlobalUniqueId" is assumed.
         * @param {Function} [config.failure] Function called when execution of the "addVialToRequest" function fails.
         * @param {String} [config.containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
         */
        addVialsToRequest : function(config)
        {
            if (config && (LABKEY.Utils.isFunction(config) || arguments.length > 1)){
                config = {
                    success : arguments[0],
                    requestId : arguments[1],
                    vialIdArray: arguments[2],
                    idType : arguments[3],
                    failure: arguments[4],
                    containerPath: arguments[5]
                };
            }

            // Unfortunately, we need to reverse our parameter order here- LABKEY.Utils uses inconsistent ordering for its
            // default callback and callback wrapper functions:
            if (!config.failure)
                config.failure = function(error, response) { return LABKEY.Utils.displayAjaxErrorResponse(response, error); };
            if (!config.idType)
                config.idType = "GlobalUniqueId";

            sendJsonQueryRequest({
                url : LABKEY.ActionURL.buildURL("study-samples-api", "addVialsToRequest", config.containerPath),
                success: getSuccessCallbackWrapper(config.success),
                failure: LABKEY.Utils.getCallbackWrapper(config.failure, this, true),
                jsonData : {requestId : config.requestId, vialIds : config.vialIdArray, idType: config.idType}
            });
        },

        // AddSampleToRequestAction
        /**
         * Adds multiple vials to a request based on an array of hash codes uniquely identifying the primary specimens.  The vials will
         * be selected based on availability and current location.  If called by a non-administrator, the target request must be owned by the
         * calling user, and the request must be in an open (not yet submitted) state.  Administrators may add vials
         * to any request at any time.
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Function} config.success Required. Function called when the
         "addSampleToRequest" function executes successfully.  Will be called with a single argument
         {@link LABKEY.Specimen.Request}, containing the newly modified request.
         * @param {int} config.requestId The unique integer identifier of the target request.
         * @param {String[]} config.specimenHashArray An array of hash codes identifying the primary specimens to add to the target request.
         * @param {int} config.preferredLocation The unique ID of the preferred providing location.  If more than on providing location is possible,
         * and if the request does not already contain vials uniquely identifying one of these providing locations, this
         * parameter must be provided.
         * @param {Function} [config.failure] Function called when execution of the "addSampleToRequest" function fails.
         * @param {String} [config.containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
         */
        addSamplesToRequest : function(config)
        {
            if (config && (LABKEY.Utils.isFunction(config) || arguments.length > 1)){
                config = {
                    success : arguments[0],
                    requestId : arguments[1],
                    specimenHashArray : arguments[2],
                    preferredLocation : arguments[3],
                    failure : arguments[4],
                    containerPath : arguments[5]
                };
            }

            var failure = LABKEY.Utils.getOnFailure(config);
            var success = LABKEY.Utils.getOnSuccess(config);
            
            // Unfortunately, we need to reverse our parameter order here- LABKEY.Utils uses inconsistent ordering for its
            // default callback and callback wrapper functions:
            if (!failure)
                failure = function(error, response) { return LABKEY.Utils.displayAjaxErrorResponse(response, error); };

            sendJsonQueryRequest({
                url : LABKEY.ActionURL.buildURL("study-samples-api", "addSamplesToRequest", config.containerPath),
                success: getSuccessCallbackWrapper(success),
                failure: LABKEY.Utils.getCallbackWrapper(failure, this, true),
                jsonData : {
                    requestId : config.requestId,
                    specimenHashes : config.specimenHashArray,
                    preferredLocation : config.preferredLocation
                }
            });
        },


        // RemoveVialFromRequestAction
        /**
         * Removes multiple vials from a request based on an array of vial row IDs.  If called by a non-administrator,
         * the target request must be owned by the calling user, and the request must be in an open (not yet submitted)
         * state.  Administrators may remove vials from any request at any time.
         * @param {Object} config An object which contains the following configuration properties.
         * @param {Function} config.success Required. Function called when the
         "removeVialFromRequest" function executes successfully.  Will be called with a single argument
         {@link LABKEY.Specimen.Request}, containing the newly modified request.
         * @param {int} config.requestId The unique integer identifier of the target request.
         * @param {String[]} config.vialIdArray An array of global unique vial IDs to remove from the target request.
         * @param {String} [config.idType] A string constant indicating how vials are identified.  This must be either
         * "GlobalUniqueId" or "RowId".  If undefined, "GlobalUniqueId" is assumed.
         * @param {Function} [config.failure] Function called when execution of the "removeVialFromRequest" function fails.
         * @param {String} [config.containerPath] The container path in which the relevant study is defined.
         *       If not supplied, the current container path will be used.
         */
        removeVialsFromRequest : function(config)
        {
            if (config && (LABKEY.Utils.isFunction(config) || arguments.length > 1)){
                config = {
                    success : arguments[0],
                    requestId : arguments[1],
                    vialIdArray : arguments[2],
                    idType : arguments[3],
                    failure : arguments[4],
                    containerPath : arguments[5]
                };
            }

            // Unfortunately, we need to reverse our parameter order here- LABKEY.Utils uses inconsistent ordering for its
            // default callback and callback wrapper functions:
            if (!config.failure)
                config.failure = function(error, response) { return LABKEY.Utils.displayAjaxErrorResponse(response, error); };
            if (!config.idType)
                config.idType = "GlobalUniqueId";

            sendJsonQueryRequest({
                url : LABKEY.ActionURL.buildURL("study-samples-api", "removeVialsFromRequest", config.containerPath),
                success: getSuccessCallbackWrapper(config.success),
                failure: LABKEY.Utils.getCallbackWrapper(config.failure, this, true),
                jsonData : {requestId : config.requestId, vialIds : config.vialIdArray, idType : config.idType}
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