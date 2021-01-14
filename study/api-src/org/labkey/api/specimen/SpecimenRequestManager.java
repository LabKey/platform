package org.labkey.api.specimen;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.annotations.Migrate;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.specimen.importer.RequestabilityManager;
import org.labkey.api.specimen.importer.RollupHelper;
import org.labkey.api.specimen.importer.RollupInstance;
import org.labkey.api.specimen.importer.VialSpecimenRollup;
import org.labkey.api.specimen.model.SpecimenRequestEvent;
import org.labkey.api.specimen.requirements.SpecimenRequest;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirement;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirementProvider;
import org.labkey.api.specimen.security.permissions.ManageRequestsPermission;
import org.labkey.api.specimen.security.permissions.RequestSpecimensPermission;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.study.QueryHelper;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.view.NotFoundException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public class SpecimenRequestManager
{
    private static final SpecimenRequestManager INSTANCE = new SpecimenRequestManager();

    private final QueryHelper<SpecimenRequestEvent> _requestEventHelper;
    private final QueryHelper<SpecimenRequestStatus> _requestStatusHelper;
    private final QueryHelper<SpecimenRequest> _requestHelper;

    public static SpecimenRequestManager get()
    {
        return INSTANCE;
    }

    private SpecimenRequestManager()
    {
        _requestEventHelper = new QueryHelper<>(() -> SpecimenSchema.get().getTableInfoSampleRequestEvent(), SpecimenRequestEvent.class);
        _requestStatusHelper = new QueryHelper<>(() -> SpecimenSchema.get().getTableInfoSampleRequestStatus(), SpecimenRequestStatus.class);
        _requestHelper = new QueryHelper<>(() -> SpecimenSchema.get().getTableInfoSampleRequest(), SpecimenRequest.class);
    }

    public List<SpecimenRequestStatus> getRequestStatuses(Container c, User user)
    {
        List<SpecimenRequestStatus> statuses = _requestStatusHelper.get(c, "SortOrder");
        // if the 'not-yet-submitted' status doesn't exist, create it here, with sort order -1,
        // so it's always first.
        if (statuses == null || statuses.isEmpty() || statuses.get(0).getSortOrder() != -1)
        {
            SpecimenRequestStatus notYetSubmittedStatus = new SpecimenRequestStatus();
            notYetSubmittedStatus.setContainer(c);
            notYetSubmittedStatus.setFinalState(false);
            notYetSubmittedStatus.setSpecimensLocked(true);
            notYetSubmittedStatus.setLabel("Not Yet Submitted");
            notYetSubmittedStatus.setSortOrder(-1);
            try (var ignore = SpringActionController.ignoreSqlUpdates())
            {
                Table.insert(user, _requestStatusHelper.getTableInfo(), notYetSubmittedStatus);
            }
            statuses = _requestStatusHelper.get(c, "SortOrder");
        }
        return statuses;
    }

    public SpecimenRequestStatus getRequestShoppingCartStatus(Container c, User user)
    {
        List<SpecimenRequestStatus> statuses = getRequestStatuses(c, user);
        if (statuses.get(0).getSortOrder() != -1)
            throw new IllegalStateException("Shopping cart status should be created automatically.");
        return statuses.get(0);
    }

    public SpecimenRequestStatus getInitialRequestStatus(Container c, User user, boolean nonCart)
    {
        List<SpecimenRequestStatus> statuses = getRequestStatuses(c, user);
        if (!nonCart && SettingsManager.get().isSpecimenShoppingCartEnabled(c))
            return statuses.get(0);
        else
            return statuses.get(1);
    }

    public boolean hasEditRequestPermissions(User user, SpecimenRequest request)
    {
        if (request == null)
            return false;
        Container container = request.getContainer();
        if (!container.hasPermission(user, RequestSpecimensPermission.class))
            return false;
        if (container.hasPermission(user, ManageRequestsPermission.class))
            return true;

        if (SettingsManager.get().isSpecimenShoppingCartEnabled(container))
        {
            SpecimenRequestStatus cartStatus = getRequestShoppingCartStatus(container, user);
            if (cartStatus.getRowId() == request.getStatusId() && request.getCreatedBy() == user.getUserId())
                return true;
        }
        return false;
    }

    public Set<Integer> getRequestStatusIdsInUse(Container c)
    {
        List<SpecimenRequest> requests = _requestHelper.get(c);
        Set<Integer> uniqueStatuses = new HashSet<>();
        for (SpecimenRequest request : requests)
            uniqueStatuses.add(request.getStatusId());
        return uniqueStatuses;
    }

    public List<SpecimenRequestEvent> getRequestEvents(Container c)
    {
        return _requestEventHelper.get(c);
    }

    public SpecimenRequestEvent getRequestEvent(Container c, int rowId)
    {
        return _requestEventHelper.get(c, rowId);
    }

    public static class SpecimenRequestInput
    {
        private final String _title;
        private final String _helpText;
        private final int _displayOrder;

        private boolean _required;
        private boolean _rememberSiteValue;
        private boolean _multiLine;
        private Map<Integer,String> _locationToDefaultValue;

        public SpecimenRequestInput(String title, String helpText, int displayOrder, boolean multiLine, boolean required, boolean rememberSiteValue)
        {
            _title = title;
            _required = required;
            _rememberSiteValue = rememberSiteValue;
            _helpText = helpText;
            _displayOrder = displayOrder;
            _multiLine = multiLine;
        }

        public SpecimenRequestInput(String title, String helpText, int displayOrder)
        {
            this(title, helpText, displayOrder, false, false, false);
        }

        public String getHelpText()
        {
            return _helpText;
        }

        public boolean isRememberSiteValue()
        {
            return _rememberSiteValue;
        }

        public boolean isRequired()
        {
            return _required;
        }

        public String getTitle()
        {
            return _title;
        }

        public int getDisplayOrder()
        {
            return _displayOrder;
        }

        public boolean isMultiLine()
        {
            return _multiLine;
        }

        public void setMultiLine(boolean multiLine)
        {
            _multiLine = multiLine;
        }

        public void setRememberSiteValue(boolean rememberSiteValue)
        {
            _rememberSiteValue = rememberSiteValue;
        }

        public void setRequired(boolean required)
        {
            _required = required;
        }

        public Map<Integer,String> getDefaultSiteValues(Container container) throws ValidationException
        {
            if (!isRememberSiteValue())
                throw new UnsupportedOperationException("Only those inputs set to remember site values can be queried for a site default.");

            if (_locationToDefaultValue != null)
                return _locationToDefaultValue;
            String defaultObjectLsid = getRequestInputDefaultObjectLsid(container);
            String setItemLsid = ensureOntologyManagerSetItem(container, defaultObjectLsid, getTitle());
            Map<Integer, String> locationToValue = new HashMap<>();

            Map<String, ObjectProperty> defaultValueProperties = OntologyManager.getPropertyObjects(container, setItemLsid);
            if (defaultValueProperties != null)
            {
                for (Map.Entry<String, ObjectProperty> defaultValue : defaultValueProperties.entrySet())
                {
                    String locationIdString = defaultValue.getKey().substring(defaultValue.getKey().lastIndexOf(".") + 1);
                    int locationId = Integer.parseInt(locationIdString);
                    locationToValue.put(locationId, defaultValue.getValue().getStringValue());
                }
            }
            _locationToDefaultValue = locationToValue;
            return _locationToDefaultValue;
        }

        public void setDefaultSiteValue(Container container, int locationId, String value) throws SQLException
        {
            try
            {
                assert locationId > 0 : "Invalid site id: " + locationId;
                if (!isRememberSiteValue())
                    throw new UnsupportedOperationException("Only those inputs configured to remember site values can set a site default.");
                _locationToDefaultValue = null;
                String parentObjectLsid = getRequestInputDefaultObjectLsid(container);

                String setItemLsid = ensureOntologyManagerSetItem(container, parentObjectLsid, getTitle());
                String propertyId = parentObjectLsid + "." + locationId;
                ObjectProperty defaultValueProperty = new ObjectProperty(setItemLsid, container, propertyId, value);
                OntologyManager.deleteProperty(setItemLsid, propertyId, container, container);
                OntologyManager.insertProperties(container, setItemLsid, defaultValueProperty);
            }
            catch (ValidationException e)
            {
                throw new SQLException(e.getMessage());
            }
        }
    }

    public SpecimenRequestInput[] getNewSpecimenRequestInputs(Container container) throws SQLException
    {
        return getNewSpecimenRequestInputs(container, true);
    }

    public SpecimenRequestInput[] getNewSpecimenRequestInputs(Container container, boolean createIfMissing) throws SQLException
    {
        String parentObjectLsid = getRequestInputObjectLsid(container);
        Map<String,ObjectProperty> resourceProperties = OntologyManager.getPropertyObjects(container, parentObjectLsid);
        SpecimenRequestInput[] inputs = new SpecimenRequestInput[0];
        if (resourceProperties == null || resourceProperties.size() == 0)
        {
            if (createIfMissing)
            {
                try (var ignore = SpringActionController.ignoreSqlUpdates())
                {
                    inputs = new SpecimenRequestInput[] {
                            new SpecimenRequestInput("Assay Plan", "Please enter a description of or reference to the assay plan(s) that will be used for the requested specimens.", 0, true, true, false),
                            new SpecimenRequestInput("Shipping Information", "Please enter your shipping address along with any special instructions.", 1, true, true, true),
                            new SpecimenRequestInput("Comments", "Please enter any additional information regarding your request.", 2, true, false, false)
                    };
                    saveNewSpecimenRequestInputs(container, inputs);
                }
            }
            return inputs;
        }
        else
        {
            inputs = new SpecimenRequestInput[resourceProperties.size()];
            for (Map.Entry<String, ObjectProperty> parentPropertyEntry : resourceProperties.entrySet())
            {
                String resourcePropertyLsid = parentPropertyEntry.getKey();
                int displayOrder = Integer.parseInt(resourcePropertyLsid.substring(resourcePropertyLsid.lastIndexOf('.') + 1));

                Map<String, ObjectProperty> inputProperties = parentPropertyEntry.getValue().retrieveChildProperties();
                String title = inputProperties.get(parentObjectLsid + ".Title").getStringValue();
                String helpText = null;
                if (inputProperties.get(parentObjectLsid + ".HelpText") != null)
                    helpText = inputProperties.get(parentObjectLsid + ".HelpText").getStringValue();
                boolean rememberSiteValue = inputProperties.get(parentObjectLsid + ".RememberSiteValue").getFloatValue() == 1;
                boolean required = inputProperties.get(parentObjectLsid + ".Required").getFloatValue() == 1;
                boolean multiLine = inputProperties.get(parentObjectLsid + ".MultiLine").getFloatValue() == 1;
                inputs[displayOrder] = new SpecimenRequestInput(title, helpText, displayOrder, multiLine, required, rememberSiteValue);
            }
        }
        return inputs;
    }

    private static String getRequestInputObjectLsid(Container container)
    {
        return new Lsid(StudyService.SPECIMEN_NAMESPACE_PREFIX, "Folder-" + container.getRowId(), "RequestInput").toString();
    }

    private static String getRequestInputDefaultObjectLsid(Container container)
    {
        return new Lsid(StudyService.SPECIMEN_NAMESPACE_PREFIX, "Folder-" + container.getRowId(), "RequestInputDefault").toString();
    }

    private static String ensureOntologyManagerSetItem(Container container, String lsidBase, String uniqueItemId) throws ValidationException
    {
        try (var ignore = SpringActionController.ignoreSqlUpdates())
        {
            Integer listParentObjectId = OntologyManager.ensureObject(container, lsidBase);
            String listItemReferenceLsidPrefix = lsidBase + "#objectResource.";
            String listItemObjectLsid = lsidBase + "#" + uniqueItemId;
            String listItemPropertyReferenceLsid = listItemReferenceLsidPrefix + uniqueItemId;

            // ensure the object that corresponds to a single list item:
            OntologyManager.ensureObject(container, listItemObjectLsid, listParentObjectId);

            // check to make sure that the list item is wired up to the top-level list object via a property:
            Map<String, ObjectProperty> properties = OntologyManager.getPropertyObjects(container, lsidBase);
            if (!properties.containsKey(listItemPropertyReferenceLsid))
            {
                // create the resource property that links the parent object to the list item object:
                ObjectProperty resourceProperty = new ObjectProperty(lsidBase, container,
                        listItemPropertyReferenceLsid, listItemObjectLsid, PropertyType.RESOURCE);
                OntologyManager.insertProperties(container, lsidBase, resourceProperty);
            }
            return listItemObjectLsid;
        }
    }

    public void saveNewSpecimenRequestInputs(Container container, SpecimenRequestInput[] inputs) throws SQLException
    {
        if (!requestInputsChanged(container, inputs))
            return;

        try {
            String parentObjectLsid = getRequestInputObjectLsid(container);
            String defaultValuesObjectLsid = getRequestInputDefaultObjectLsid(container);
            OntologyManager.deleteOntologyObject(parentObjectLsid, container, true);
            OntologyManager.deleteOntologyObject(defaultValuesObjectLsid, container, true);
            for (int i = 0; i < inputs.length; i++)
            {
                SpecimenRequestInput input = inputs[i];
                String setItemLsid = ensureOntologyManagerSetItem(container, parentObjectLsid, "" + i);
                ObjectProperty[] props = new ObjectProperty[5];
                props[0] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".HelpText", input.getHelpText());
                props[1] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".Required", input.isRequired() ? 1 : 0);
                props[2] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".RememberSiteValue", input.isRememberSiteValue() ? 1 : 0);
                props[3] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".Title", input.getTitle());
                props[4] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".MultiLine", input.isMultiLine() ? 1 : 0);
                OntologyManager.insertProperties(container, setItemLsid, props);
            }
        }
        catch (ValidationException ve)
        {
            throw new SQLException(ve.getMessage());
        }
    }

    private boolean requestInputsChanged(Container container, SpecimenRequestInput[] newInputs) throws SQLException
    {
        SpecimenRequestInput[] oldInputs = getNewSpecimenRequestInputs(container, false);
        if (oldInputs.length == 0)
            return true;
        else if (oldInputs.length != newInputs.length)
            return true;
        else
        {
            for (int i = 0; i < oldInputs.length; i++)
            {
                if (oldInputs[i].isMultiLine() != newInputs[i].isMultiLine() ||
                        oldInputs[i].isRememberSiteValue() != newInputs[i].isRememberSiteValue() ||
                        oldInputs[i].isRequired() != newInputs[i].isRequired() ||
                        !oldInputs[i].getTitle().equals(newInputs[i].getTitle()) ||
                        !getSafeString(oldInputs[i].getHelpText()).equals(getSafeString(newInputs[i].getHelpText())))
                    return true;
            }
        }
        return false;
    }

    private String getSafeString(String str)
    {
        if (str == null)
            return "";
        else
            return str;
    }

    public void createRequestStatus(User user, SpecimenRequestStatus status)
    {
        _requestStatusHelper.create(user, status);
    }

    public void updateRequestStatus(User user, SpecimenRequestStatus status)
    {
        _requestStatusHelper.update(user, status);
    }

    public void deleteRequestStatus(User user, SpecimenRequestStatus status)
    {
        _requestStatusHelper.delete(status);
    }

    public SpecimenRequestRequirement[] getRequestRequirements(SpecimenRequest request)
    {
        if (request == null)
            return new SpecimenRequestRequirement[0];
        return request.getRequirements();
    }

    public void deleteRequestRequirement(User user, SpecimenRequestRequirement requirement) throws AttachmentService.DuplicateFilenameException
    {
        deleteRequestRequirement(user, requirement, true);
    }

    public void deleteRequestRequirement(User user, SpecimenRequestRequirement requirement, boolean createEvent) throws AttachmentService.DuplicateFilenameException
    {
        if (createEvent)
            createRequestEvent(user, requirement, RequestEventType.REQUIREMENT_REMOVED, requirement.getRequirementSummary(), null);
        requirement.delete();
    }

    public void createRequestRequirement(User user, SpecimenRequestRequirement requirement, boolean createEvent) throws AttachmentService.DuplicateFilenameException
    {
        createRequestRequirement(user, requirement, createEvent, false);
    }

    public void createRequestRequirement(User user, SpecimenRequestRequirement requirement, boolean createEvent, boolean force) throws AttachmentService.DuplicateFilenameException
    {
        SpecimenRequest request = getRequest(requirement.getContainer(), requirement.getRequestId());
        SpecimenRequestRequirement newRequirement = SpecimenRequestRequirementProvider.get().createRequirement(user, request, requirement, force);
        if (newRequirement != null && createEvent)
            createRequestEvent(user, requirement, RequestEventType.REQUIREMENT_ADDED, requirement.getRequirementSummary(), null);
    }

    public void updateRequestRequirement(User user, SpecimenRequestRequirement requirement)
    {
        requirement.update(user);
    }

    public boolean isInFinalState(SpecimenRequest request)
    {
        return getRequestStatus(request.getContainer(), request.getStatusId()).isFinalState();
    }

    public SpecimenRequestStatus getRequestStatus(Container c, int rowId)
    {
        return _requestStatusHelper.get(c, rowId);
    }

    private SpecimenRequestEvent createRequestEvent(User user, SpecimenRequestEvent event)
    {
        return _requestEventHelper.create(user, event);
    }

    public SpecimenRequestEvent createRequestEvent(User user, SpecimenRequestRequirement requirement, RequestEventType type, String comments, List<AttachmentFile> attachments) throws AttachmentService.DuplicateFilenameException
    {
        return createRequestEvent(user, requirement.getContainer(), requirement.getRequestId(), requirement.getRowId(), type, comments, attachments);
    }

    public SpecimenRequestEvent createRequestEvent(User user, SpecimenRequest request, RequestEventType type, String comments, List<AttachmentFile> attachments) throws AttachmentService.DuplicateFilenameException
    {
        return createRequestEvent(user, request.getContainer(), request.getRowId(), -1, type, comments, attachments);
    }

    private SpecimenRequestEvent createRequestEvent(User user, Container container, int requestId, int requirementId, RequestEventType type, String comments, List<AttachmentFile> attachments) throws AttachmentService.DuplicateFilenameException
    {
        SpecimenRequestEvent event = new SpecimenRequestEvent();
        event.setEntryType(type.getDisplayText());
        event.setComments(comments);
        event.setRequestId(requestId);
        event.setCreated(new Date(System.currentTimeMillis()));
        if (requirementId >= 0)
            event.setRequirementId(requirementId);
        event.setContainer(container);
        event.setEntityId(GUID.makeGUID());
        event = createRequestEvent(user, event);
        try
        {
            AttachmentService.get().addAttachments(event, attachments, user);
        }
        catch (AttachmentService.DuplicateFilenameException e)
        {
            // UI should (minimally) catch and display these errors nicely
            throw e;
        }
        catch (IOException e)
        {
            // this is unexpected, and indicative of a larger system problem; we'll convert to a runtime
            // exception, rather than requiring all event loggers to handle this unlikely scenario:
            throw new RuntimeException(e);
        }
        return event;
    }

    public List<SpecimenRequest> getRequests(Container c, User user)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Hidden"), Boolean.FALSE);
        if (user != null)
            filter.addCondition(FieldKey.fromParts("CreatedBy"), user.getUserId());
        return _requestHelper.get(c, filter, "-Created");
    }

    public SpecimenRequest getRequest(Container c, int rowId)
    {
        return _requestHelper.get(c, rowId);
    }

    public SpecimenRequest createRequest(User user, SpecimenRequest request, boolean createEvent) throws AttachmentService.DuplicateFilenameException
    {
        request = _requestHelper.create(user, request);
        if (createEvent)
            createRequestEvent(user, request, RequestEventType.REQUEST_CREATED, request.getRequestDescription(), null);
        return request;
    }

    public void updateRequest(User user, SpecimenRequest request) throws RequestabilityManager.InvalidRuleException
    {
        DbScope scope = SpecimenSchema.get().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            _requestHelper.update(user, request);

            // update specimen states
            List<Vial> vials = request.getVials();
            if (vials.size() > 0)
            {
                SpecimenRequestStatus status = getRequestStatus(request.getContainer(), request.getStatusId());
                updateSpecimenStatus(vials, user, status.isSpecimensLocked());
            }
            transaction.commit();
        }
    }

    private static final ReentrantLock REQUEST_ADDITION_LOCK = new ReentrantLock();

    public void createRequestSpecimenMapping(User user, SpecimenRequest request, List<Vial> vials, boolean createEvents, boolean createRequirements)
            throws RequestabilityManager.InvalidRuleException, AttachmentService.DuplicateFilenameException, SpecimenRequestException
    {
        if (vials == null || vials.size() == 0)
            return;

        DbScope scope = SpecimenSchema.get().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction(REQUEST_ADDITION_LOCK))
        {
            for (Vial vial : vials)
            {
                if (!request.getContainer().getId().equals(vial.getContainer().getId()))
                    throw new IllegalStateException("Mismatched containers.");

                if (!vial.isAvailable())
                    throw new SpecimenRequestException("Vial unavailable.");
            }

            for (Vial vial : vials)
            {
                Map<String, Object> fields = new HashMap<>();
                fields.put("Container", request.getContainer().getId());
                fields.put("SampleRequestId", request.getRowId());
                fields.put("SpecimenGlobalUniqueId", vial.getGlobalUniqueId());
                Table.insert(user, SpecimenSchema.get().getTableInfoSampleRequestSpecimen(), fields);
                if (createEvents)
                    SpecimenRequestManager.get().createRequestEvent(user, request, RequestEventType.SPECIMEN_ADDED, vial.getSpecimenDescription(), null);
            }

            if (createRequirements)
                SpecimenRequestRequirementProvider.get().generateDefaultRequirements(user, request);

            SpecimenRequestStatus status = getRequestStatus(request.getContainer(), request.getStatusId());
            updateSpecimenStatus(vials, user, status.isSpecimensLocked());

            transaction.commit();
        }
    }

    /**
     * Update the lockedInRequest and available field states for the set of specimens.
     */
    private void updateSpecimenStatus(List<Vial> vials, User user, boolean lockedInRequest) throws RequestabilityManager.InvalidRuleException
    {
        for (Vial vial : vials)
        {
            vial.setLockedInRequest(lockedInRequest);
            Table.update(user, SpecimenSchema.get().getTableInfoVial(vial.getContainer()), vial.getRowMap(), vial.getRowId());
        }
        updateRequestabilityAndCounts(vials, user);
        if (vials.size() > 0)
            clearCaches(getContainer(vials));
    }

    private void updateRequestabilityAndCounts(List<Vial> vials, User user) throws RequestabilityManager.InvalidRuleException
    {
        if (vials.size() == 0)
            return;
        Container container = getContainer(vials);

        // update requestable flags before updating counts, since available count could change:
        for (int start = 0; start < vials.size(); start += 1000)
        {
            List<Vial> subset = vials.subList(start, start + Math.min(1000, vials.size() - start));
            RequestabilityManager.getInstance().updateRequestability(container, user, subset);
        }

        for (int start = 0; start < vials.size(); start += 1000)
        {
            List<Vial> subset = vials.subList(start, start + Math.min(1000, vials.size() - start));
            updateVialCounts(container, user, subset);
        }
    }

    private Container getContainer(List<Vial> vials)
    {
        Container container = vials.get(0).getContainer();
        if (AppProps.getInstance().isDevMode())
        {
            for (int i = 1; i < vials.size(); i++)
            {
                if (!container.equals(vials.get(i).getContainer()))
                    throw new IllegalStateException("All specimens must be from the same container");
            }
        }
        return container;
    }

    private static final String UPDATE_SPECIMEN_SETS =
        " SET\n" +
        "    TotalVolume = VialCounts.TotalVolume,\n" +
        "    AvailableVolume = VialCounts.AvailableVolume,\n" +
        "    VialCount = VialCounts.VialCount,\n" +
        "    LockedInRequestCount = VialCounts.LockedInRequestCount,\n" +
        "    AtRepositoryCount = VialCounts.AtRepositoryCount,\n" +
        "    AvailableCount = VialCounts.AvailableCount,\n" +
        "    ExpectedAvailableCount = VialCounts.ExpectedAvailableCount";

    private static final String UPDATE_SPECIMEN_SELECTS =
        "\nFROM (\n" +
            "\tSELECT SpecimenId,\n" +
            "\t\tSUM(Volume) AS TotalVolume,\n" +
            "\t\tSUM(CASE Available WHEN ? THEN Volume ELSE 0 END) AS AvailableVolume,\n" +
            "\t\tCOUNT(GlobalUniqueId) AS VialCount,\n" +
            "\t\tSUM(CASE LockedInRequest WHEN ? THEN 1 ELSE 0 END) AS LockedInRequestCount,\n" +
            "\t\tSUM(CASE AtRepository WHEN ? THEN 1 ELSE 0 END) AS AtRepositoryCount,\n" +
            "\t\tSUM(CASE Available WHEN ? THEN 1 ELSE 0 END) AS AvailableCount,\n" +
            "\t\t(COUNT(GlobalUniqueId) - SUM(\n" +
            "\t\tCASE\n" +
            "\t\t\t(CASE LockedInRequest WHEN ? THEN 1 ELSE 0 END) -- Null is considered false for LockedInRequest\n" +
            "\t\t\t| (CASE Requestable WHEN ? THEN 1 ELSE 0 END)-- Null is considered true for Requestable\n" +
            "\t\t\tWHEN 1 THEN 1 ELSE 0 END)\n" +
            "\t\t) AS ExpectedAvailableCount";

    //                "\tFROM ";

    private void updateVialCounts(Container container, User user, List<Vial> vials)
    {
        TableInfo tableInfoSpecimen = SpecimenSchema.get().getTableInfoSpecimen(container);
        TableInfo tableInfoVial = SpecimenSchema.get().getTableInfoVial(container);

        String tableInfoSpecimenSelectName = tableInfoSpecimen.getSelectName();
        String tableInfoVialSelectName = tableInfoVial.getSelectName();
        RollupHelper.RollupMap<VialSpecimenRollup> matchedRollups = RollupHelper.getVialToSpecimenRollups(container, user);

        SQLFragment updateSql = new SQLFragment();
        updateSql.append("UPDATE ").append(tableInfoSpecimenSelectName).append(UPDATE_SPECIMEN_SETS);
        for (List<RollupInstance<VialSpecimenRollup>> rollupList : matchedRollups.values())
            for (Pair<String, VialSpecimenRollup> rollupItem : rollupList)
            {
                ColumnInfo column = tableInfoSpecimen.getColumn(rollupItem.first);
                if (null == column)
                    throw new IllegalStateException("Expected Specimen table column to exist.");
                String colSelectName = column.getSelectName();
                updateSql.append(",\n    ").append(colSelectName).append(" = VialCounts.").append(colSelectName);
            }

        updateSql.append(UPDATE_SPECIMEN_SELECTS);
        updateSql.add(Boolean.TRUE); // AvailableVolume
        updateSql.add(Boolean.TRUE); // LockedInRequestCount
        updateSql.add(Boolean.TRUE); // AtRepositoryCount
        updateSql.add(Boolean.TRUE); // AvailableCount
        updateSql.add(Boolean.TRUE); // LockedInRequest case of ExpectedAvailableCount
        updateSql.add(Boolean.FALSE); // Requestable case of ExpectedAvailableCount

        for (Map.Entry<String, List<RollupInstance<VialSpecimenRollup>>> entry : matchedRollups.entrySet())
        {
            ColumnInfo vialColumn = tableInfoVial.getColumn(entry.getKey());
            if (null == vialColumn)
                throw new IllegalStateException("Expected Vial table column to exist.");
            String fromName = vialColumn.getSelectName();
            for (RollupInstance<VialSpecimenRollup> rollupItem : entry.getValue())
            {
                VialSpecimenRollup rollup = rollupItem.second;
                ColumnInfo column = tableInfoSpecimen.getColumn(rollupItem.first);
                if (null == column)
                    throw new IllegalStateException("Expected Specimen table column to exist.");
                String toName = column.getSelectName();

                updateSql.append(",\n\t\t").append(rollup.getRollupSql(fromName, toName));
            }
        }

        updateSql.append("\tFROM ").append(tableInfoVialSelectName).append("\n");

        if (vials != null && vials.size() > 0)
        {
            Set<Long> specimenIds = new HashSet<>();
            for (Vial vial : vials)
                specimenIds.add(vial.getSpecimenId());

            updateSql.append("WHERE ")
                    .append(tableInfoVial.getColumn("SpecimenId").getValueSql(tableInfoVialSelectName))
                    .append(" IN (");
            String sep = "";
            for (Long id : specimenIds)
            {
                updateSql.append(sep).append("?");
                updateSql.add(id);
                sep = ", ";
            }
            updateSql.append(")\n");
        }

        updateSql.append("\tGROUP BY SpecimenId\n) VialCounts\nWHERE ")
                .append(tableInfoSpecimen.getColumn("RowId").getValueSql(tableInfoSpecimenSelectName))
                .append("= VialCounts.SpecimenId");
        new SqlExecutor(SpecimenSchema.get().getSchema()).execute(updateSql);
    }

    public void updateVialCounts(Container container, User user)
    {
        updateVialCounts(container, user, null);
    }

    @Migrate // Enable ancillary study cache clearing
    public void clearCaches(Container c)
    {
        _requestEventHelper.clearCache(c);
        _requestHelper.clearCache(c);
        _requestStatusHelper.clearCache(c);
//        for (StudyImpl study : StudyManager.getInstance().getAncillaryStudies(c))
//            clearCaches(study.getContainer());

        clearGroupedValuesForColumn(c);
    }

    private DatabaseCache<String, Map<String, Map<String, Object>>> _groupedValuesCache = null;

    private static class GroupedResults
    {
        public String viewName;
        public String urlFilterName;
        public String labelValue;
        public long count;
        public Map<String, GroupedResults> childGroupedResultsMap;
    }

    private static String getGroupedValuesCacheKey(Container container)
    {
        return container.getId();
    }

    public void clearGroupedValuesForColumn(Container container)
    {
        if (null == _groupedValuesCache)
            return;

        String cacheKey = getGroupedValuesCacheKey(container);
        _groupedValuesCache.remove(cacheKey);
    }

    public void deleteRequest(User user, SpecimenRequest request) throws RequestabilityManager.InvalidRuleException, AttachmentService.DuplicateFilenameException
    {
        DbScope scope = SpecimenSchema.get().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            List<Vial> vials = request.getVials();
            List<Long> specimenIds = new ArrayList<>(vials.size());
            for (Vial vial : vials)
                specimenIds.add(vial.getRowId());

            deleteRequestSpecimenMappings(user, request, specimenIds, false);

            deleteMissingSpecimens(request);

            SpecimenRequestRequirementProvider.get().deleteRequirements(request);

            deleteRequestEvents(user, request);
            _requestHelper.delete(request);

            transaction.commit();
        }
    }

    public void deleteRequestSpecimenMappings(User user, SpecimenRequest request, List<Long> vialIds, boolean createEvents)
            throws RequestabilityManager.InvalidRuleException, AttachmentService.DuplicateFilenameException
    {
        if (vialIds == null || vialIds.size() == 0)
            return;

        Set<Long> vialRowIds = new HashSet<>(vialIds);
        List<Vial> vials = getVials(request.getContainer(), user, vialRowIds);
        List<String> globalUniqueIds = new ArrayList<>(vials.size());
        List<String> descriptions = new ArrayList<>();
        for (Vial vial : vials)
        {
            globalUniqueIds.add(vial.getGlobalUniqueId());
            descriptions.add(vial.getSpecimenDescription());
        }

        DbScope scope = SpecimenSchema.get().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(request.getContainer());
            filter.addCondition(FieldKey.fromParts("SampleRequestId"), request.getRowId());
            filter.addInClause(FieldKey.fromParts("SpecimenGlobalUniqueId"), globalUniqueIds);
            Table.delete(SpecimenSchema.get().getTableInfoSampleRequestSpecimen(), filter);
            if (createEvents)
            {
                for (String description : descriptions)
                    SpecimenRequestManager.get().createRequestEvent(user, request, RequestEventType.SPECIMEN_REMOVED, description, null);
            }

            updateSpecimenStatus(vials, user, false);

            transaction.commit();
        }
    }

    public void deleteMissingSpecimens(SpecimenRequest specimenRequest)
    {
        List<String> missingSpecimens = getMissingSpecimens(specimenRequest);
        if (missingSpecimens.isEmpty())
            return;
        SimpleFilter filter = SimpleFilter.createContainerFilter(specimenRequest.getContainer());
        filter.addCondition(FieldKey.fromParts("SampleRequestId"), specimenRequest.getRowId());
        filter.addInClause(FieldKey.fromParts("SpecimenGlobalUniqueId"), missingSpecimens);
        Table.delete(SpecimenSchema.get().getTableInfoSampleRequestSpecimen(), filter);
    }

    public List<String> getMissingSpecimens(SpecimenRequest specimenRequest)
    {
        Container container = specimenRequest.getContainer();
        TableInfo tableInfoVial = SpecimenSchema.get().getTableInfoVial(container);
        SQLFragment sql = new SQLFragment("SELECT SpecimenGlobalUniqueId FROM study.SampleRequestSpecimen WHERE SampleRequestId = ? and Container = ? and \n" +
                "SpecimenGlobalUniqueId NOT IN (SELECT ");
        sql.add(specimenRequest.getRowId());
        sql.add(container);
        sql.append(tableInfoVial.getColumn("GlobalUniqueId").getValueSql("Vial"))
                .append(" FROM ").append(tableInfoVial.getFromSQL("Vial")).append(")");

        return new SqlSelector(SpecimenSchema.get().getSchema(), sql).getArrayList(String.class);
    }

    public @NotNull List<Integer> getRequestIdsForSpecimen(Vial vial)
    {
        return getRequestIdsForSpecimen(vial, false);
    }

    public @NotNull List<Integer> getRequestIdsForSpecimen(Vial vial, boolean lockingRequestsOnly)
    {
        if (vial == null)
            return Collections.emptyList();

        SQLFragment sql = new SQLFragment("SELECT SampleRequestId FROM " + SpecimenSchema.get().getTableInfoSampleRequestSpecimen() +
                " Map, " + SpecimenSchema.get().getTableInfoSampleRequest() + " Request, " +
                SpecimenSchema.get().getTableInfoSampleRequestStatus() + " Status WHERE SpecimenGlobalUniqueId = ? " +
                "AND Request.Container = ? AND Map.Container = Request.Container AND Status.Container = Request.Container " +
                "AND Map.SampleRequestId = Request.RowId AND Request.StatusId = Status.RowId");
        sql.add(vial.getGlobalUniqueId());
        sql.add(vial.getContainer().getId());

        if (lockingRequestsOnly)
        {
            sql.append(" AND Status.SpecimensLocked = ?");
            sql.add(Boolean.TRUE);
        }

        return new SqlSelector(SpecimenSchema.get().getSchema(), sql).getArrayList(Integer.class);
    }

    private void deleteRequestEvents(User user, SpecimenRequest request)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RequestId"), request.getRowId());
        List<SpecimenRequestEvent> events = _requestEventHelper.get(request.getContainer(), filter);
        for (SpecimenRequestEvent event : events)
        {
            AttachmentService.get().deleteAttachments(event);
            _requestEventHelper.delete(event);
        }
    }

    @Migrate // Move back to SpecimenManager?
    public List<Vial> getVials(Container container, User user, Set<Long> vialRowIds)
    {
        // Take a set to eliminate dups - issue 26940

        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addInClause(FieldKey.fromParts("RowId"), vialRowIds);
        List<Vial> vials = getVials(container, user, filter);
        if (vials.size() != vialRowIds.size())
        {
            List<Long> unmatchedRowIds = new ArrayList<>(vialRowIds);
            for (Vial vial : vials)
            {
                unmatchedRowIds.remove(vial.getRowId());
            }
            throw new SpecimenRequestException("One or more specimen RowIds had no matching specimen: " + unmatchedRowIds);
        }
        return vials;
    }

    @Migrate // Move back to SpecimenManager?
    public List<Vial> getVials(final Container container, final User user, SimpleFilter filter)
    {
        // TODO: LinkedList?
        final List<Vial> vials = new ArrayList<>();

        getSpecimensSelector(container, user, filter)
            .forEachMap(map -> vials.add(new Vial(container, map)));

        return vials;
    }

    @Migrate // Move back to SpecimenManager?
    public TableSelector getSpecimensSelector(final Container container, final User user, SimpleFilter filter)
    {
        Study study = StudyService.get().getStudy(container);
        if (study == null)
        {
            throw new NotFoundException("No study in container " + container.getPath());
        }
        UserSchema schema = SpecimenQuerySchema.get(study, user);
        TableInfo specimenTable = schema.getTable(SpecimenQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
        return new TableSelector(specimenTable, filter, null);
    }
}

