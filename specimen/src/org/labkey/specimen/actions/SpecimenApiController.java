/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.specimen.actions;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiVersion;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.specimen.RequestedSpecimens;
import org.labkey.api.specimen.SpecimenManagerNew;
import org.labkey.api.specimen.SpecimenRequestException;
import org.labkey.api.specimen.SpecimenRequestStatus;
import org.labkey.api.specimen.Vial;
import org.labkey.api.specimen.importer.RequestabilityManager;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.specimen.model.PrimaryType;
import org.labkey.api.specimen.model.SpecimenTypeSummary;
import org.labkey.api.specimen.requirements.SpecimenRequest;
import org.labkey.api.specimen.security.permissions.ManageRequestsPermission;
import org.labkey.api.specimen.security.permissions.RequestSpecimensPermission;
import org.labkey.api.specimen.settings.RepositorySettings;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.study.StudyUtils;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.specimen.SpecimenManager;
import org.labkey.specimen.SpecimenRequestManager;
import org.labkey.specimen.model.AdditiveType;
import org.labkey.specimen.model.DerivativeType;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/*
 * User: brittp
 * Date: Dec 18, 2008
 * Time: 11:57:24 AM
 */
@SuppressWarnings("UnusedDeclaration")
public class SpecimenApiController extends SpringActionController
{
    private static final DefaultActionResolver _resolver = new DefaultActionResolver(SpecimenApiController.class);

    public SpecimenApiController()
    {
        setActionResolver(_resolver);
    }

    public static class SpecimenApiForm implements HasViewContext
    {
        private ViewContext _viewContext;

        @Override
        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        @Override
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

    private List<Map<String, Object>> getSpecimenListResponse(List<Vial> vials)
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
                PrimaryType primaryType = SpecimenManagerNew.get().getPrimaryType(vial.getContainer(), vial.getPrimaryTypeId());
                if (primaryType != null)
                    vialProperties.put("primaryType", primaryType.getPrimaryType());
            }
            vialProperties.put("derivativeTypeId", vial.getDerivativeTypeId());
            if (vial.getDerivativeTypeId() != null)
            {
                DerivativeType derivativeType = SpecimenManager.get().getDerivativeType(vial.getContainer(), vial.getDerivativeTypeId());
                if (derivativeType != null)
                    vialProperties.put("derivativeType", derivativeType.getDerivative());
            }
            vialProperties.put("additiveTypeId", vial.getAdditiveTypeId());
            if (vial.getAdditiveTypeId() != null)
            {
                AdditiveType additiveType = SpecimenManager.get().getAdditiveType(vial.getContainer(), vial.getAdditiveTypeId());
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

    private Map<String, Object> getRequestResponse(ViewContext context, SpecimenRequest request)
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
        SpecimenRequestStatus status = SpecimenRequestManager.get().getRequestStatus(request.getContainer(), request.getStatusId());
        if (status != null)
            map.put("status", status.getLabel());
        List<Vial> vials = request.getVials();
        map.put("vials", getSpecimenListResponse(vials));
        return map;
    }

    private Map<String, Object> getLocation(Container container, int locationId)
    {
        LocationImpl location = LocationManager.get().getLocation(container, locationId);
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

    private List<Map<String, Object>> getRequestListResponse(ViewContext context, List<SpecimenRequest> requests)
    {
        List<Map<String, Object>> response = new ArrayList<>();
        for (SpecimenRequest request : requests)
            response.add(getRequestResponse(context, request));
        return response;
    }

    @RequiresPermission(ReadPermission.class)
    @ApiVersion(9.1)
    public class GetRepositoriesAction extends ReadOnlyApiAction<SpecimenApiForm>
    {
        @Override
        public ApiResponse execute(SpecimenApiForm form, BindException errors)
        {
            final List<Map<String, Object>> repositories = new ArrayList<>();
            for (LocationImpl location : LocationManager.get().getLocations(getContainer()))
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
    public class GetOpenRequestsAction extends ReadOnlyApiAction<GetRequestsForm>
    {
        @Override
        public ApiResponse execute(GetRequestsForm requestsForm, BindException errors)
        {
            Container container = requestsForm.getViewContext().getContainer();
            User user = requestsForm.getViewContext().getUser();
            SpecimenRequestStatus shoppingCartStatus = SpecimenRequestManager.get().getRequestShoppingCartStatus(container, user);
            final Map<String, Object> response = new HashMap<>();
            if (user != null && shoppingCartStatus != null)
            {
                boolean allUsers = getContainer().hasPermission(getUser(), ManageRequestsPermission.class);
                if (requestsForm.isAllUsers() != null)
                    allUsers = requestsForm.isAllUsers();
                List<SpecimenRequest> allUserRequests = SpecimenRequestManager.get().getRequests(container, allUsers ? null : user);
                List<SpecimenRequest> nonFinalRequests = new ArrayList<>();
                for (SpecimenRequest request : allUserRequests)
                {
                    if (SpecimenRequestManager.get().hasEditRequestPermissions(getUser(), request) && !SpecimenRequestManager.get().isInFinalState(request))
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

    @RequiresPermission(ReadPermission.class)
    @ApiVersion(9.1)
    public class GetRequestAction extends ReadOnlyApiAction<RequestIdForm>
    {
        @Override
        public ApiResponse execute(RequestIdForm requestIdForm, BindException errors)
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
    public class GetProvidingLocations extends ReadOnlyApiAction<GetProvidingLocationsForm>
    {
        @Override
        public ApiResponse execute(GetProvidingLocationsForm form, BindException errors)
        {
            Map<String, List<Vial>> vialsByHash = SpecimenManagerNew.get().getVialsForSpecimenHashes(getContainer(), getUser(),
                    PageFlowUtil.set(form.getSpecimenHashes()), true);
            Collection<Integer> preferredLocations = StudyUtils.getPreferredProvidingLocations(vialsByHash.values());
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
    public class GetVialsByRowIdAction extends ReadOnlyApiAction<GetVialsByRowIdForm>
    {
        @Override
        public ApiResponse execute(GetVialsByRowIdForm form, BindException errors)
        {
            Container container = form.getViewContext().getContainer();
            final Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> vialList;
            if (form.getRowIds() != null && form.getRowIds().length > 0)
            {
                try
                {
                    List<Vial> vials = SpecimenManager.get().getVials(container, form.getViewContext().getUser(), form.getRowIds());
                    vialList = getSpecimenListResponse(vials);
                }
                catch (SpecimenRequestException e)
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

    public static class AddSpecimensToRequestForm extends RequestIdForm
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

    private SpecimenRequest getRequest(User user, Container container, int rowId, boolean checkOwnership, boolean checkEditability)
    {
        SpecimenRequest request = SpecimenRequestManager.get().getRequest(container, rowId);
        boolean admin = container.hasPermission(user, RequestSpecimensPermission.class);
        boolean adminOrOwner = request != null && (admin || request.getCreatedBy() == user.getUserId());
        if (request == null || (checkOwnership && !adminOrOwner))
            throw new RuntimeException("Request " + rowId + " was not found or the current user does not have permissions to access it.");
        if (checkEditability)
        {
            if (admin)
            {
                if (SpecimenRequestManager.get().isInFinalState(request))
                    throw new RuntimeException("Request " + rowId + " is in a final state and cannot be modified.");
            }
            else
            {
                SpecimenRequestStatus cartStatus = SpecimenRequestManager.get().getRequestShoppingCartStatus(container, user);
                if (cartStatus == null || request.getStatusId() != cartStatus.getRowId())
                    throw new RuntimeException("Request " + rowId + " has been submitted and can only be modified by an administrator.");
            }
        }
        return request;
    }

    public static class VialRequestForm extends RequestIdForm
    {
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

    @RequiresPermission(RequestSpecimensPermission.class)
    @ApiVersion(9.1)
    public class AddVialsToRequestAction extends MutatingApiAction<VialRequestForm>
    {
        @Override
        public ApiResponse execute(VialRequestForm vialRequestForm, BindException errors) throws Exception
        {
            SpecimenRequest request = getRequest(getUser(), getContainer(), vialRequestForm.getRequestId(), true, true);
            for (String vialId : vialRequestForm.getVialIds())
            {
                Vial vial = getVial(vialId, vialRequestForm.getIdType());
                try
                {
                    SpecimenRequestManager.get().createRequestSpecimenMapping(getUser(), request, Collections.singletonList(vial), true, true);
                }
                catch (RequestabilityManager.InvalidRuleException e)
                {
                    errors.reject(ERROR_MSG, "The specimens could not be added because a requestability rule is configured incorrectly. " +
                                "Please report this problem to an administrator. Error details: "  + e.getMessage());
                    return null;
                }
                catch (SpecimenRequestException e)
                {
                    errors.reject(ERROR_MSG, RequestabilityManager.makeSpecimenUnavailableMessage(vial, null));
                    return null;
                }

            }
            final Map<String, Object> response = getRequestResponse(getViewContext(), request);
            return new ApiSimpleResponse(response);
        }
    }

    private Vial getVial(String vialId, String idType)
    {
        Vial vial;
        if (IdTypes.GlobalUniqueId.name().equals(idType))
            vial = SpecimenManagerNew.get().getVial(getContainer(), getUser(), vialId);
        else if (IdTypes.RowId.name().equals(idType))
        {
            try
            {
                int id = Integer.parseInt(vialId);
                vial = SpecimenManagerNew.get().getVial(getContainer(), getUser(), id);
            }
            catch (NumberFormatException e)
            {
                throw new RuntimeException(vialId + " could not be converted into a valid integer RowId.");
            }
        }
        else
        {
            throw new RuntimeException("Invalid ID type \"" + idType + "\": only \"" +
                    IdTypes.GlobalUniqueId.name() + "\" and \"" + IdTypes.RowId.name() +
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
    public class RemoveVialsFromRequestAction extends MutatingApiAction<VialRequestForm>
    {
        @Override
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
                    SpecimenRequestManager.get().deleteRequestSpecimenMappings(getUser(), request, rowIds, true);
                }
                catch (RequestabilityManager.InvalidRuleException e)
                {
                    errors.reject(ERROR_MSG, "The specimens could not be removed because a requestability rule is configured incorrectly. " +
                                "Please report this problem to an administrator. Error details: "  + e.getMessage());
                    return null;
                }
            }
            final Map<String, Object> response = getRequestResponse(getViewContext(), request);
            return new ApiSimpleResponse(response);
        }
    }

    @RequiresPermission(RequestSpecimensPermission.class)
    @ApiVersion(9.1)
    // labkey-js-api now calls addSpecimensToRequest.api, but older versions still call addSamplesToRequest.api
    @ActionNames("addSpecimensToRequest,addSamplesToRequest")
    public class AddSpecimensToRequestAction extends MutatingApiAction<AddSpecimensToRequestForm>
    {
        @Override
        public ApiResponse execute(AddSpecimensToRequestForm addSpecimensToRequestForm, BindException errors) throws Exception
        {
            final SpecimenRequest request = getRequest(getUser(), getContainer(), addSpecimensToRequestForm.getRequestId(), true, true);
            Set<String> hashes = new HashSet<>();
            Collections.addAll(hashes, addSpecimensToRequestForm.getSpecimenHashes());
            RequestedSpecimens requested = SpecimenRequestManager.get().getRequestableBySpecimenHash(getContainer(), getUser(), hashes, addSpecimensToRequestForm.getPreferredLocation());
            if (requested.getVials().size() > 0)
            {
                List<Vial> vials = new ArrayList<>(requested.getVials());
                try
                {
                    SpecimenRequestManager.get().createRequestSpecimenMapping(getUser(), request, vials, true, true);
                }
                catch (RequestabilityManager.InvalidRuleException e)
                {
                    errors.reject(ERROR_MSG, "The specimens could not be added because a requestability rule is configured incorrectly. " +
                                "Please report this problem to an administrator. Error details: "  + e.getMessage());
                    return null;
                }
                catch (SpecimenRequestException e)
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
    public class CancelRequestAction extends MutatingApiAction<RequestIdForm>
    {
        @Override
        public ApiResponse execute(RequestIdForm deleteRequestForm, BindException errors) throws Exception
        {
            SpecimenRequest request = getRequest(getUser(), getContainer(), deleteRequestForm.getRequestId(), true, true);
            try
            {
                SpecimenRequestManager.get().deleteRequest(getUser(), request);
            }
            catch (RequestabilityManager.InvalidRuleException e)
            {
                errors.reject(ERROR_MSG, "The request could not be deleted because a requestability rule is configured incorrectly. " +
                            "Please report this problem to an administrator. Error details: "  + e.getMessage());
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
    public class GetVialTypeSummaryAction extends ReadOnlyApiAction<SpecimenApiForm>
    {
        @Override
        public ApiResponse execute(SpecimenApiForm form, BindException errors)
        {
            Container container = form.getViewContext().getContainer();
            SpecimenTypeSummary summary = SpecimenManagerNew.get().getSpecimenTypeSummary(container, getUser());
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
    public class GetSpecimenWebPartGroupsAction extends ReadOnlyApiAction<SpecimenApiForm>
    {
        @Override
        public ApiResponse execute(SpecimenApiForm form, BindException errors)
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
            RepositorySettings settings = SettingsManager.get().getRepositorySettings(container);
            ArrayList<String[]> groupings = settings.getSpecimenWebPartGroupings();

            final Map<String, Object> response = new HashMap<>();

            List<Map<String, Object>> groupingsJSON = new ArrayList<>();

            Map<String, Map<String, Object>> groupingMap = SpecimenRequestManager.get().getGroupedValuesForColumn(getContainer(), getUser(), groupings);
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
