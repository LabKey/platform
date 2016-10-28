/*
 * Copyright (c) 2009-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.study.controllers.specimen;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.security.*;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.action.*;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.Container;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.SpecimenManager;
import org.labkey.study.importer.RequestabilityManager;
import org.labkey.study.specimen.settings.RepositorySettings;
import org.labkey.study.security.permissions.RequestSpecimensPermission;
import org.labkey.study.security.permissions.ManageRequestsPermission;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.model.*;
import org.springframework.validation.BindException;

import java.util.*;
import java.sql.SQLException;
/*
 * User: brittp
 * Date: Dec 18, 2008
 * Time: 11:57:24 AM
 */

@SuppressWarnings("UnusedDeclaration")
public class SpecimenApiController extends BaseStudyController
{
    private static final DefaultActionResolver _resolver = new DefaultActionResolver(SpecimenApiController.class);

    public SpecimenApiController()
    {
        super();
        setActionResolver(_resolver);
    }

    public static class SpecimenApiForm implements HasViewContext
    {
        private ViewContext _viewContext;

        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        public void setViewContext(ViewContext viewContext)
        {
            _viewContext = viewContext;
        }
    }

    public static class GetRequestsForm extends SpecimenApiForm
    {
        Boolean _allUsers;

        public Boolean isAllUsers()
        {
            return _allUsers;
        }

        public void setAllUsers(Boolean allUsers)
        {
            _allUsers = allUsers;
        }
    }

    public static class RequestIdForm extends SpecimenApiForm
    {
        private int _requestId;

        public int getRequestId()
        {
            return _requestId;
        }

        public void setRequestId(int requestId)
        {
            _requestId = requestId;
        }
    }

    private List<Map<String, Object>> getSpecimenListResponse(List<Vial> vials) throws SQLException
    {
        List<Map<String, Object>> response = new ArrayList<>();
        for (Vial vial : vials)
        {
            Map<String, Object> vialProperties = new HashMap<>();
            response.add(vialProperties);
            vialProperties.put("rowId", vial.getRowId());
            vialProperties.put("globalUniqueId", vial.getGlobalUniqueId());
            vialProperties.put("ptid", vial.getPtid());
            vialProperties.put("visitValue", vial.getVisitValue());
            vialProperties.put("primaryTypeId", vial.getPrimaryTypeId());
            if (vial.getPrimaryTypeId() != null)
            {
                PrimaryType primaryType = SpecimenManager.getInstance().getPrimaryType(vial.getContainer(), vial.getPrimaryTypeId());
                if (primaryType != null)
                    vialProperties.put("primaryType", primaryType.getPrimaryType());
            }
            vialProperties.put("derivativeTypeId", vial.getDerivativeTypeId());
            if (vial.getDerivativeTypeId() != null)
            {
                DerivativeType derivativeType = SpecimenManager.getInstance().getDerivativeType(vial.getContainer(), vial.getDerivativeTypeId());
                if (derivativeType != null)
                    vialProperties.put("derivativeType", derivativeType.getDerivative());
            }
            vialProperties.put("additiveTypeId", vial.getAdditiveTypeId());
            if (vial.getAdditiveTypeId() != null)
            {
                AdditiveType additiveType = SpecimenManager.getInstance().getAdditiveType(vial.getContainer(), vial.getAdditiveTypeId());
                if (additiveType != null)
                    vialProperties.put("additiveType", additiveType.getAdditive());
            }
            vialProperties.put("drawTimestamp", vial.getDrawTimestamp());
            vialProperties.put("currentLocation", vial.getCurrentLocation() != null ?
                    getLocation(getContainer(), vial.getCurrentLocation()) : null);
            if (vial.getOriginatingLocationId() != null)
                vialProperties.put("originatingLocation", getLocation(getContainer(), vial.getOriginatingLocationId()));
            vialProperties.put("subAdditiveDerivative", vial.getSubAdditiveDerivative());
            vialProperties.put("volume", vial.getVolume());
            vialProperties.put("specimenHash", vial.getSpecimenHash());
            vialProperties.put("volumeUnits", vial.getVolumeUnits());
        }
        return response;
    }

    private Map<String, Object> getRequestResponse(ViewContext context, SpecimenRequest request) throws SQLException
    {
        Map<String, Object> map = new HashMap<>();
        map.put("requestId", request.getRowId());
        map.put("comments", request.getComments());
        map.put("created", request.getCreated());
        map.put("createdBy", request.getCreatedBy());
        User user = UserManager.getUser(request.getCreatedBy());
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("userId", request.getCreatedBy());
        userMap.put("displayName", user.getDisplayName(context.getUser()));
        map.put("createdBy", userMap);
        map.put("destination", request.getDestinationSiteId() != null ? getLocation(getContainer(), request.getDestinationSiteId().intValue()) : null);
        map.put("statusId", request.getStatusId());
        SpecimenRequestStatus status = SpecimenManager.getInstance().getRequestStatus(request.getContainer(), request.getStatusId());
        if (status != null)
            map.put("status", status.getLabel());
        List<Vial> vials = SpecimenManager.getInstance().getRequestVials(request);
        map.put("vials", getSpecimenListResponse(vials));
        return map;
    }

    private Map<String, Object> getLocation(Container container, int locationId)
    {
        LocationImpl location = StudyManager.getInstance().getLocation(container, locationId);
        if (location == null)
            return null;
        return getLocation(location);
    }

    private Map<String, Object> getLocation(LocationImpl location1)
    {
        Map<String, Object> locationMap = new HashMap<>();
        locationMap.put("endpoint", location1.isEndpoint());
        locationMap.put("entityId", location1.getEntityId());
        locationMap.put("label", location1.getLabel());
        locationMap.put("labUploadCode", location1.getLabUploadCode());
        locationMap.put("labwareLabCode", location1.getLabwareLabCode());
        locationMap.put("ldmsLabCode", location1.getLdmsLabCode());
        locationMap.put("repository", location1.isRepository());
        locationMap.put("rowId", location1.getRowId());
        locationMap.put("SAL", location1.isSal());
        locationMap.put("clinic", location1.isClinic());
        locationMap.put("externalId", location1.getExternalId());
        return locationMap;
    }

    private List<Map<String, Object>> getRequestListResponse(ViewContext context, List<SpecimenRequest> requests) throws SQLException
    {
        List<Map<String, Object>> response = new ArrayList<>();
        for (SpecimenRequest request : requests)
            response.add(getRequestResponse(context, request));
        return response;
    }

    @RequiresPermission(ReadPermission.class)
    @ApiVersion(9.1)
    public class GetRepositoriesAction extends ApiAction<SpecimenApiForm>
    {
        public ApiResponse execute(SpecimenApiForm form, BindException errors) throws Exception
        {
            final List<Map<String, Object>> repositories = new ArrayList<>();
            for (LocationImpl location : StudyManager.getInstance().getSites(getContainer()))
            {
                if (location.isRepository())
                    repositories.add(getLocation(location));
            }
            Map<String, Object> result = new HashMap<>();
            result.put("repositories", repositories);
            return new ApiSimpleResponse(result);
        }
    }

    @RequiresPermission(ReadPermission.class)
    @ApiVersion(9.1)
    public class GetOpenRequestsAction extends ApiAction<GetRequestsForm>
    {
        public ApiResponse execute(GetRequestsForm requestsForm, BindException errors) throws Exception
        {
            Container container = requestsForm.getViewContext().getContainer();
            User user = requestsForm.getViewContext().getUser();
            SpecimenRequestStatus shoppingCartStatus = SpecimenManager.getInstance().getRequestShoppingCartStatus(container, user);
            final Map<String, Object> response = new HashMap<>();
            if (user != null && shoppingCartStatus != null)
            {
                boolean allUsers = getContainer().hasPermission(getUser(), ManageRequestsPermission.class);
                if (requestsForm.isAllUsers() != null)
                    allUsers = requestsForm.isAllUsers();
                List<SpecimenRequest> allUserRequests = SpecimenManager.getInstance().getRequests(container, allUsers ? null : user);
                List<SpecimenRequest> nonFinalRequests = new ArrayList<>();
                for (SpecimenRequest request : allUserRequests)
                {
                    if (SpecimenManager.getInstance().hasEditRequestPermissions(getUser(), request) && !SpecimenManager.getInstance().isInFinalState(request))
                    {
                        nonFinalRequests.add(request);
                    }
                }
                response.put("requests", getRequestListResponse(getViewContext(), nonFinalRequests));
            }
            else
                response.put("requests", Collections.emptyList());

            return new ApiSimpleResponse(response);
        }
    }

    @RequiresPermission(ReadPermission.class)
    @ApiVersion(9.1)
    public class GetRequestAction extends ApiAction<RequestIdForm>
    {
        public ApiResponse execute(RequestIdForm requestIdForm, BindException errors) throws Exception
        {
            SpecimenRequest request = getRequest(getUser(), getContainer(), requestIdForm.getRequestId(), false, false);
            final Map<String, Object> response = new HashMap<>();
            response.put("request", request != null ? getRequestResponse(getViewContext(), request) : null);
            return new ApiSimpleResponse(response);
        }
    }

    public static class GetVialsByRowIdForm extends SpecimenApiForm
    {
        int[] _rowIds;

        public int[] getRowIds()
        {
            return _rowIds;
        }

        public void setRowIds(int[] rowIds)
        {
            _rowIds = rowIds;
        }
    }

    public static class GetProvidingLocationsForm extends RequestIdForm
    {
        private String[] specimenHashes;

        public String[] getSpecimenHashes()
        {
            return specimenHashes;
        }

        public void setSpecimenHashes(String[] specimenHashes)
        {
            this.specimenHashes = specimenHashes;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @ApiVersion(9.1)
    public class GetProvidingLocations extends ApiAction<GetProvidingLocationsForm>
    {
        public ApiResponse execute(GetProvidingLocationsForm form, BindException errors) throws Exception
        {
            Map<String, List<Vial>> vialsByHash = SpecimenManager.getInstance().getVialsForSampleHashes(getContainer(), getUser(),
                    PageFlowUtil.set(form.getSpecimenHashes()), true);
            Collection<Integer> preferredLocations = SpecimenUtils.getPreferredProvidingLocations(vialsByHash.values());
            final Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> locations = new ArrayList<>();
            for (Integer locationId : preferredLocations)
                locations.add(getLocation(getContainer(), locationId));
            response.put("locations", locations);
            return new ApiSimpleResponse(response);
        }
    }

    @RequiresPermission(ReadPermission.class)
    @ApiVersion(9.1)
    public class GetVialsByRowIdAction extends ApiAction<GetVialsByRowIdForm>
    {
        public ApiResponse execute(GetVialsByRowIdForm form, BindException errors) throws Exception
        {
            Container container = form.getViewContext().getContainer();
            final Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> vialList;
            if (form.getRowIds() != null && form.getRowIds().length > 0)
            {
                try
                {
                    List<Vial> vials = SpecimenManager.getInstance().getVials(container, form.getViewContext().getUser(), form.getRowIds());
                    vialList = getSpecimenListResponse(vials);
                }
                catch (SpecimenManager.SpecimenRequestException e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                    vialList = Collections.emptyList();
                }
            }
            else
                vialList = Collections.emptyList();
            response.put("vials", vialList);
            return new ApiSimpleResponse(response);
        }
    }

    public static class VialRequestForm extends RequestIdForm
    {
        public enum IdTypes
        {
            GlobalUniqueId,
            SpecimenHash,
            RowId
        }

        private String _idType;
        private String[] _vialIds;

        public String[] getVialIds()
        {
            return _vialIds;
        }

        public void setVialIds(String[] vialIds)
        {
            _vialIds = vialIds;
        }

        public String getIdType()
        {
            return _idType;
        }

        public void setIdType(String idType)
        {
            _idType = idType;
        }
    }

    public static class AddSpecimenToRequestForm extends RequestIdForm
    {
        private String[] specimenHashes;
        private Integer _preferredLocation;

        public String[] getSpecimenHashes()
        {
            return specimenHashes;
        }

        public void setSpecimenHashes(String[] specimenHashes)
        {
            this.specimenHashes = specimenHashes;
        }

        public Integer getPreferredLocation()
        {
            return _preferredLocation;
        }

        public void setPreferredLocation(Integer preferredLocation)
        {
            _preferredLocation = preferredLocation;
        }
    }

    private SpecimenRequest getRequest(User user, Container container, int rowId, boolean checkOwnership, boolean checkEditability) throws SQLException
    {
        SpecimenRequest request = SpecimenManager.getInstance().getRequest(container, rowId);
        boolean admin = container.hasPermission(user, RequestSpecimensPermission.class);
        boolean adminOrOwner = request != null && (admin || request.getCreatedBy() == user.getUserId());
        if (request == null || (checkOwnership && !adminOrOwner))
            throw new RuntimeException("Request " + rowId + " was not found or the current user does not have permissions to access it.");
        if (checkEditability)
        {
            if (admin)
            {
                if (SpecimenManager.getInstance().isInFinalState(request))
                    throw new RuntimeException("Request " + rowId + " is in a final state and cannot be modified.");
            }
            else
            {
                SpecimenRequestStatus cartStatus = SpecimenManager.getInstance().getRequestShoppingCartStatus(container, user);
                if (cartStatus == null || request.getStatusId() != cartStatus.getRowId())
                    throw new RuntimeException("Request " + rowId + " has been submitted and can only be modified by an administrator.");
            }
        }
        return request;
    }

    @RequiresPermission(RequestSpecimensPermission.class)
    @ApiVersion(9.1)
    public class AddVialsToRequestAction extends ApiAction<VialRequestForm>
    {
        public ApiResponse execute(VialRequestForm vialRequestForm, BindException errors) throws Exception
        {
            SpecimenRequest request = getRequest(getUser(), getContainer(), vialRequestForm.getRequestId(), true, true);
            for (String vialId : vialRequestForm.getVialIds())
            {
                Vial vial = getVial(vialId, vialRequestForm.getIdType());
                try
                {
                    SpecimenManager.getInstance().createRequestSampleMapping(getUser(), request, Collections.singletonList(vial), true, true);
                }
                catch (RequestabilityManager.InvalidRuleException e)
                {
                    errors.reject(ERROR_MSG, "The samples could not be added because a requestability rule is configured incorrectly. " +
                                "Please report this problem to an administrator.  Error details: "  + e.getMessage());
                    return null;
                }
                catch (SpecimenManager.SpecimenRequestException e)
                {
                    errors.reject(ERROR_MSG, RequestabilityManager.makeSpecimenUnavailableMessage(vial, null));
                    return null;
                }

            }
            final Map<String, Object> response = getRequestResponse(getViewContext(), request);
            return new ApiSimpleResponse(response);
        }
    }

    private Vial getVial(String vialId, String idType) throws SQLException
    {
        Vial vial;
        if (VialRequestForm.IdTypes.GlobalUniqueId.name().equals(idType))
            vial = SpecimenManager.getInstance().getVial(getContainer(), getUser(), vialId);
        else if (VialRequestForm.IdTypes.RowId.name().equals(idType))
        {
            try
            {
                int id = Integer.parseInt(vialId);
                vial = SpecimenManager.getInstance().getVial(getContainer(), getUser(), id);
            }
            catch (NumberFormatException e)
            {
                throw new RuntimeException(vialId + " could not be converted into a valid integer RowId.");
            }
        }
        else
        {
            throw new RuntimeException("Invalid ID type \"" + idType + "\": only \"" +
                    VialRequestForm.IdTypes.GlobalUniqueId.name() + "\" and \"" + VialRequestForm.IdTypes.RowId.name() +
                    "\" are valid parameter values.");
        }
        if (vial == null)
        {
            throw new NotFoundException("No vial was found with " +  idType + " " + vialId + ".");
        }
        return vial;
    }

    @RequiresPermission(RequestSpecimensPermission.class)
    @ApiVersion(9.1)
    public class RemoveVialsFromRequestAction extends ApiAction<VialRequestForm>
    {
        public ApiResponse execute(VialRequestForm vialRequestForm, BindException errors) throws Exception
        {
            SpecimenRequest request = getRequest(getUser(), getContainer(), vialRequestForm.getRequestId(), true, true);
            List<Long> rowIds = new ArrayList<>();
            List<Vial> currentVials = request.getVials();
            for (String vialId : vialRequestForm.getVialIds())
            {
                Vial vial = getVial(vialId, vialRequestForm.getIdType());
                Vial toRemove = null;
                for (int i = 0; i < currentVials.size() && toRemove == null; i++)
                {
                    Vial possible = currentVials.get(i);
                    if (possible.getRowId() == vial.getRowId())
                        toRemove = possible;
                }
                if (toRemove != null)
                    rowIds.add(toRemove.getRowId());
            }
            if (!rowIds.isEmpty())
            {
                try
                {
                    SpecimenManager.getInstance().deleteRequestSampleMappings(getUser(), request, rowIds, true);
                }
                catch (RequestabilityManager.InvalidRuleException e)
                {
                    errors.reject(ERROR_MSG, "The samples could not be removed because a requestability rule is configured incorrectly. " +
                                "Please report this problem to an administrator.  Error details: "  + e.getMessage());
                    return null;
                }
            }
            final Map<String, Object> response = getRequestResponse(getViewContext(), request);
            return new ApiSimpleResponse(response);
        }
    }

    @RequiresPermission(RequestSpecimensPermission.class)
    @ApiVersion(9.1)
    public class AddSamplesToRequestAction extends ApiAction<AddSpecimenToRequestForm>
    {
        public ApiResponse execute(AddSpecimenToRequestForm addSampleToRequestForm, BindException errors) throws Exception
        {
            final SpecimenRequest request = getRequest(getUser(), getContainer(), addSampleToRequestForm.getRequestId(), true, true);
            Set<String> hashes = new HashSet<>();
            Collections.addAll(hashes, addSampleToRequestForm.getSpecimenHashes());
            SpecimenUtils.RequestedSpecimens requested = getUtils().getRequestableBySampleHash(hashes, addSampleToRequestForm.getPreferredLocation());
            if (requested.getVials().size() > 0)
            {
                List<Vial> vials = new ArrayList<>(requested.getVials());
                try
                {
                    SpecimenManager.getInstance().createRequestSampleMapping(getUser(), request, vials, true, true);
                }
                catch (RequestabilityManager.InvalidRuleException e)
                {
                    errors.reject(ERROR_MSG, "The samples could not be added because a requestability rule is configured incorrectly. " +
                                "Please report this problem to an administrator.  Error details: "  + e.getMessage());
                    return null;
                }
                catch (SpecimenManager.SpecimenRequestException e)
                {
                    errors.reject(ERROR_MSG, "A vial that was available for request has become unavailable.");
                    return null;
                }
            }
            final Map<String, Object> response = getRequestResponse(getViewContext(), request);
            return new ApiSimpleResponse(response);
        }
    }

    @RequiresPermission(RequestSpecimensPermission.class)
    @ApiVersion(9.1)
    public class CancelRequestAction extends ApiAction<RequestIdForm>
    {
        public ApiResponse execute(RequestIdForm deleteRequestForm, BindException errors) throws Exception
        {
            SpecimenRequest request = getRequest(getUser(), getContainer(), deleteRequestForm.getRequestId(), true, true);
            try
            {
                SpecimenManager.getInstance().deleteRequest(getUser(), request);
            }
            catch (RequestabilityManager.InvalidRuleException e)
            {
                errors.reject(ERROR_MSG, "The request could not be deleted because a requestability rule is configured incorrectly. " +
                            "Please report this problem to an administrator.  Error details: "  + e.getMessage());
                return null;
            }

            final Map<String, Object> response = getRequestResponse(getViewContext(), request);
            return new ApiSimpleResponse(response);
        }
    }

    private void buildTypeSummary(List<Map<String, Object>> summary, List<? extends SpecimenTypeSummary.TypeCount> types)
    {
        // Recursively decend through the vial type hierarchy, adding a count property and a list of children for each type.
        for (SpecimenTypeSummary.TypeCount count : types)
        {
            Map<String, Object> countProperties = new TreeMap<>();
            summary.add(countProperties);
            countProperties.put("label", count.getLabel() != null ? count.getLabel() : "[unknown]");
            countProperties.put("count", count.getVialCount());
            countProperties.put("url", count.getURL());
            List<? extends SpecimenTypeSummary.TypeCount> childCounts = count.getChildren();
            if (childCounts != null && !childCounts.isEmpty())
            {
                List<Map<String, Object>> childList = new ArrayList<>();
                buildTypeSummary(childList, childCounts);
                if (!childList.isEmpty())
                    countProperties.put("children", childList);
            }
        }
    }

    @RequiresPermission(ReadPermission.class)
    @ApiVersion(11.2)
    public class GetVialTypeSummaryAction extends ApiAction<SpecimenApiForm>
    {
        public ApiResponse execute(SpecimenApiForm form, BindException errors) throws Exception
        {
            Container container = form.getViewContext().getContainer();
            SpecimenTypeSummary summary = SpecimenManager.getInstance().getSpecimenTypeSummary(container, getUser());
            final Map<String, Object> response = new HashMap<>();

            List<Map<String, Object>> primaryTypes = new ArrayList<>();
            buildTypeSummary(primaryTypes, summary.getPrimaryTypes());
            response.put("primaryTypes", primaryTypes);

            List<Map<String, Object>> derivativeTypes = new ArrayList<>();
            buildTypeSummary(derivativeTypes, summary.getDerivatives());
            response.put("derivativeTypes", derivativeTypes);

            List<Map<String, Object>> additiveTypes = new ArrayList<>();
            buildTypeSummary(additiveTypes, summary.getAdditives());
            response.put("additiveTypes", additiveTypes);

            return new ApiSimpleResponse(response);
        }
    }

    @RequiresPermission(ReadPermission.class)
    @ApiVersion(13.1)
    public class GetSpecimenWebPartGroupsAction extends ApiAction<SpecimenApiForm>
    {
        public ApiResponse execute(SpecimenApiForm form, BindException errors) throws Exception
        {
            // Build a JSON response with up to 2 groupings, each with up to 3 levels
            // VALUE -->
            // { "count" : int,
            //   "label" : string,
            //   "url" : string,
            //   "group" : GROUP        [optional]
            // }
            // GROUP -->
            // { "name" : "<column name>",
            //   "dummy" : bool,    (true means dummy so we have at least 1)
            //   "values" : [VALUE, ...]
            // }
            // GROUPINGS -- >
            // { "groupings" : [GROUP, ...]       [could be empty]
            //
            Container container = form.getViewContext().getContainer();
            RepositorySettings settings = SpecimenManager.getInstance().getRepositorySettings(container);
            ArrayList<String[]> groupings = settings.getSpecimenWebPartGroupings();

            final Map<String, Object> response = new HashMap<>();

            SpecimenManager specimenManager = SpecimenManager.getInstance();
            List<Map<String, Object>> groupingsJSON = new ArrayList<>();

            Map<String, Map<String, Object>> groupingMap = specimenManager.getGroupedValuesForColumn(getContainer(), getUser(), groupings);
            for (String[] grouping: groupings)
            {
                if (null != StringUtils.trimToNull(grouping[0]))        // Do nothing if no columns were specified
                {
                    Map<String, Object> groupingJSON = groupingMap.get(grouping[0]);
                    groupingsJSON.add(groupingJSON);
                }
            }
            if (groupingsJSON.isEmpty())
            {
                // no groupings; create default grouping
                Map<String, Object> groupingJSON = new JSONObject();
                groupingJSON.put("dummy", true);
                groupingsJSON.add(groupingJSON);
            }
            response.put("groupings", groupingsJSON);

            return new ApiSimpleResponse(response);
        }
    }
}
