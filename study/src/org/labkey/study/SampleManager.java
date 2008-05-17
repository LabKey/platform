/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.study;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.*;
import org.labkey.common.util.Pair;
import org.labkey.study.model.*;
import org.labkey.study.requirements.RequirementProvider;
import org.labkey.study.requirements.SpecimenRequestRequirementProvider;
import org.labkey.study.requirements.SpecimenRequestRequirementType;
import org.labkey.study.samples.report.SpecimenCountSummary;

import javax.mail.Address;
import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

public class SampleManager
{
    static
    {
        ObjectFactory.Registry.register(Site.class, new SiteFactory());
    }

    private static SampleManager _instance;

    private final TableInfo _tableInfoRequestSpecimen;
    private final QueryHelper<SampleRequestEvent> _requestEventHelper;
    private final QueryHelper<Specimen> _specimenHelper;
    private final QueryHelper<SpecimenEvent> _specimenEventHelper;
    private final QueryHelper<SampleRequest> _requestHelper;
    private final QueryHelper<SampleRequestStatus> _requestStatusHelper;
    private final RequirementProvider<SampleRequestRequirement, SampleRequestActor> _requirementProvider =
            new SpecimenRequestRequirementProvider();

    private SampleManager()
    {
        // prevent external construction with a private default constructor
        _tableInfoRequestSpecimen = StudySchema.getInstance().getTableInfoSampleRequestSpecimen();

        _requestEventHelper = new QueryHelper<SampleRequestEvent>(StudySchema.getInstance().getTableInfoSampleRequestEvent(), SampleRequestEvent.class);
        _specimenHelper = new QueryHelper<Specimen>(StudySchema.getInstance().getTableInfoSpecimen(), Specimen.class);
        _specimenEventHelper = new QueryHelper<SpecimenEvent>(StudySchema.getInstance().getTableInfoSpecimenEvent(), SpecimenEvent.class);
        _requestHelper = new QueryHelper<SampleRequest>(StudySchema.getInstance().getTableInfoSampleRequest(), SampleRequest.class);
        _requestStatusHelper = new QueryHelper<SampleRequestStatus>(StudySchema.getInstance().getTableInfoSampleRequestStatus(),
                SampleRequestStatus.class);
    }

    public static synchronized SampleManager getInstance()
    {
        if (_instance == null)
            _instance = new SampleManager();
        return _instance;
    }

    public Specimen[] getSpecimens(Container container, String participantId, Double visit) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addClause(new SimpleFilter.SQLClause("LOWER(ptid) = LOWER(?)", new Object[] {participantId}, "ptid"));
        filter.addCondition("VisitValue", visit);
        filter.addCondition("Container", container.getId());
        return _specimenHelper.get(container, filter);
    }

    // Custom BeanObjectFactory for Site objects.  Needed because of a bad decision to
    // prefix boolean columns with 'Is'.  This breaks the normal reflection-based
    // means of populating the object.
    private static class SiteFactory extends BeanObjectFactory<Site>
    {
        public SiteFactory()
        {
            super(Site.class);
        }

        @Override
        public Site fromMap(Map<String, ? extends Object> map)
        {
            return new Site(map);
        }

        @Override
        public Site[] handleArray(ResultSet rs) throws SQLException
        {
            List<Site> list = new ArrayList<Site>();
            Map<String, Object> m = null;
            while (rs.next())
            {
                m = ResultSetUtil.mapRow(rs, m);
                list.add(fromMap(m));
            }

            return list.toArray(new Site[list.size()]);
        }
    }

    public RequirementProvider<SampleRequestRequirement, SampleRequestActor> getRequirementsProvider()
    {
        return _requirementProvider;
    }

    public Specimen getSpecimen(Container container, int rowId) throws SQLException
    {
        return _specimenHelper.get(container, rowId);
    }

    /** Looks for any specimens that have the given id as a globalUniqueId or a specimenNumber */
    public Specimen[] getSpecimens(Container container, String id) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addClause(new SimpleFilter.SQLClause("LOWER(GlobalUniqueId) = LOWER(?) OR LOWER(SpecimenNumber) = LOWER(?)", new Object[] { id, id }));
        filter.addCondition("Container", container.getId());
        return _specimenHelper.get(container, filter);
    }

    public Specimen[] getSpecimens(Container container, String participantId, Date date) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addClause(new SimpleFilter.SQLClause("LOWER(ptid) = LOWER(?)", new Object[] {participantId}, "ptid"));
        Calendar endCal = DateUtil.newCalendar(date.getTime());
        endCal.add(Calendar.DATE, 1);
        filter.addClause(new SimpleFilter.SQLClause("DrawTimestamp >= ? AND DrawTimestamp < ?", new Object[] {date, endCal.getTime()}));
        filter.addCondition("Container", container.getId());
        return _specimenHelper.get(container, filter);
    }
    
    public SpecimenEvent[] getSpecimenEvents(Specimen sample) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("SpecimenId", sample.getRowId());
        return _specimenEventHelper.get(sample.getContainer(), filter);
    }

    private static class SpecimenEventDateComparator implements Comparator<SpecimenEvent>
    {
        private Date convertToDate(String dateString)
        {
            if (dateString == null)
                return null;
            try
            {
                return DateUtil.parseDateTime(dateString, "yyyy-MM-dd");
            }
            catch (ParseException e)
            {
                return null;
            }
        }

        private Date getAnyDate(SpecimenEvent event)
        {
            if (event.getLabReceiptDate() != null)
                return event.getLabReceiptDate();
            else
            {
                Date storageDate = convertToDate(event.getStorageDate());
                if (storageDate != null)
                    return storageDate;
                else
                    return event.getShipDate();
            }
        }

        private int getTieBreakValue(SpecimenEvent event)
        {
            // our events have the same dates; in this case, we have to consider
            // the date type; a shipping date always comes after a storage date,
            // and a storage date always comes after a receipt date.
            if (event.getLabReceiptDate() != null)
                return 1;
            else if (convertToDate(event.getStorageDate()) != null)
                return 2;
            else if (event.getShipDate() != null)
                return 3;
            throw new IllegalStateException("Can only tiebreak events with at least one date present.");
        }

        public int compare(SpecimenEvent event1, SpecimenEvent event2)
        {
            // we use any date in the event, since we assume that no two events can have
            // overlapping date ranges:
            Date date1 = getAnyDate(event1);
            Date date2 = getAnyDate(event2);
            if (date1 == null && date2 == null)
                return 0;
            if (date1 == null)
                return -1;
            if (date2 == null)
                return 1;
            Long ms1 = date1.getTime();
            Long ms2 = date2.getTime();
            int comp = ms1.compareTo(ms2);
            if (comp == 0)
                return getTieBreakValue(event2) - getTieBreakValue(event1);
            else
                return comp;
        }
    }

    private List<SpecimenEvent> getDateOrderedEventList(Specimen specimen) throws SQLException
    {
        List<SpecimenEvent> eventList = new ArrayList<SpecimenEvent>();
        SpecimenEvent[] events = getSpecimenEvents(specimen);
        if (events == null || events.length == 0)
            return eventList;
        for (SpecimenEvent event : events)
            eventList.add(event);
        Collections.sort(eventList, new SpecimenEventDateComparator());
        return eventList;
    }

    public Site getCurrentSite(Specimen specimen) throws SQLException
    {
        Integer siteId = getCurrentSiteId(specimen);
        if (siteId != null)
            return StudyManager.getInstance().getSite(specimen.getContainer(), siteId.intValue());
        return null;
    }

    public Integer getCurrentSiteId(Specimen specimen) throws SQLException
    {
        List<SpecimenEvent> events = getDateOrderedEventList(specimen);
        if (!events.isEmpty())
        {
            SpecimenEvent lastEvent = events.get(events.size() - 1);

            if (lastEvent.getShipDate() == null &&
                    (lastEvent.getShipBatchNumber() == null || lastEvent.getShipBatchNumber().intValue() == 0) &&
                    (lastEvent.getShipFlag() == null || lastEvent.getShipFlag().intValue() == 0))
            {
                return lastEvent.getLabId();
            }
        }
        return null;
    }

    public Site getOriginatingSite(Specimen specimen) throws SQLException
    {
        if (specimen.getOriginatingLocationId() != null)
        {
            Site site = StudyManager.getInstance().getSite(specimen.getContainer(), specimen.getOriginatingLocationId());
            if (site != null)
                return site;
        }

        List<SpecimenEvent> events = getDateOrderedEventList(specimen);
        if (!events.isEmpty())
            return StudyManager.getInstance().getSite(specimen.getContainer(), events.get(0).getLabId());
        return null;
    }

    public SampleRequest[] getRequests(Container c) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("Hidden", Boolean.FALSE);
        return _requestHelper.get(c, filter);
    }

    public SampleRequest getRequest(Container c, int rowId) throws SQLException
    {
        return _requestHelper.get(c, rowId);
    }

    public SampleRequest createRequest(User user, SampleRequest request, boolean createEvent) throws SQLException
    {
        request = _requestHelper.create(user, request);
        if (createEvent)
            createRequestEvent(user, request, RequestEventType.REQUEST_CREATED, request.getRequestDescription(), null);
        return request;
    }

    public void updateRequest(User user, SampleRequest request) throws SQLException
    {
        _requestHelper.update(user, request);
    }

    public SampleRequestRequirement[] getRequestRequirements(SampleRequest request)
    {
        if (request == null)
            return new SampleRequestRequirement[0];
        return request.getRequirements();
    }

    public void deleteRequestRequirement(User user, SampleRequestRequirement requirement) throws SQLException
    {
        deleteRequestRequirement(user, requirement, true);
    }

    public void deleteRequestRequirement(User user, SampleRequestRequirement requirement, boolean createEvent) throws SQLException
    {
        if (createEvent)
            createRequestEvent(user, requirement, RequestEventType.REQUIREMENT_REMOVED, requirement.getRequirementSummary(), null);
        requirement.delete();
    }

    public void createRequestRequirement(User user, SampleRequestRequirement requirement, boolean createEvent) throws SQLException
    {
        createRequestRequirement(user, requirement, createEvent, false);
    }

    public void createRequestRequirement(User user, SampleRequestRequirement requirement, boolean createEvent, boolean force) throws SQLException
    {
        SampleRequest request = getRequest(requirement.getContainer(), requirement.getRequestId());
        SampleRequestRequirement newRequirement = _requirementProvider.createRequirement(user, request, requirement, force);
        if (newRequirement != null && createEvent)
            createRequestEvent(user, requirement, RequestEventType.REQUIREMENT_ADDED, requirement.getRequirementSummary(), null);
    }

    public void updateRequestRequirement(User user, SampleRequestRequirement requirement)
    {
        requirement.update(user);
    }

    public boolean isInFinalState(SampleRequest request) throws SQLException
    {
        return getRequestStatus(request.getContainer(), request.getStatusId()).isFinalState();
    }

    public SampleRequestStatus getRequestStatus(Container c, int rowId) throws SQLException
    {
        return _requestStatusHelper.get(c, rowId);
    }

    public SampleRequestStatus[] getRequestStatuses(Container c, User user) throws SQLException
    {
        SampleRequestStatus[] statuses = _requestStatusHelper.get(c, "SortOrder");
        // if the 'not-yet-submitted' status doesn't exist, create it here, with sort order -1,
        // so it's always first.
        if (statuses == null || statuses.length == 0 || statuses[0].getSortOrder() != -1)
        {
            SampleRequestStatus notYetSubmittedStatus = new SampleRequestStatus();
            notYetSubmittedStatus.setContainer(c);
            notYetSubmittedStatus.setFinalState(false);
            notYetSubmittedStatus.setSpecimensLocked(true);
            notYetSubmittedStatus.setLabel("Not Yet Submitted");
            notYetSubmittedStatus.setSortOrder(-1);
            Table.insert(user, _requestStatusHelper.getTableInfo(), notYetSubmittedStatus);
            statuses = _requestStatusHelper.get(c, "SortOrder");
        }
        return statuses;
    }

    public SampleRequestStatus getRequestShoppingCartStatus(Container c, User user) throws SQLException
    {
        SampleRequestStatus[] statuses = getRequestStatuses(c, user);
        if (statuses[0].getSortOrder() != -1)
            throw new IllegalStateException("Shopping cart status should be created automatically.");
        return statuses[0];
    }

    public SampleRequestStatus getInitialRequestStatus(Container c, User user, boolean nonCart) throws SQLException
    {
        SampleRequestStatus[] statuses = getRequestStatuses(c, user);
        if (!nonCart && isSpecimenShoppingCartEnabled(c))
            return statuses[0];
        else
            return statuses[1];
    }

    public boolean hasEditRequestPermissions(User user, SampleRequest request) throws SQLException, ServletException
    {
        if (request == null)
            return false;
        Container container = request.getContainer();
        if (!container.hasPermission(user, ACL.PERM_INSERT))
            return false;
        if (user.isAdministrator() || container.hasPermission(user, ACL.PERM_ADMIN))
            return true;

        if (SampleManager.getInstance().isSpecimenShoppingCartEnabled(container))
        {
            SampleRequestStatus cartStatus = SampleManager.getInstance().getRequestShoppingCartStatus(container, user);
            if (cartStatus.getRowId() == request.getStatusId() && request.getCreatedBy() == user.getUserId())
                return true;
        }
        return false;
    }

    public Set<Integer> getRequestStatusIdsInUse(Container c) throws SQLException
    {
        SampleRequest[] requests = _requestHelper.get(c);
        Set<Integer> uniqueStatuses = new HashSet<Integer>();
        for (SampleRequest request : requests)
            uniqueStatuses.add(request.getStatusId());
        return uniqueStatuses;
    }

    public void createRequestStatus(User user, SampleRequestStatus status) throws SQLException
    {
        _requestStatusHelper.create(user, status);
    }

    public void updateRequestStatus(User user, SampleRequestStatus status) throws SQLException
    {
        _requestStatusHelper.update(user, status);
    }

    public void deleteRequestStatus(User user, SampleRequestStatus status) throws SQLException
    {
        _requestStatusHelper.delete(status);
    }

    public SampleRequestEvent[] getRequestEvents(Container c) throws SQLException
    {
        return _requestEventHelper.get(c);
    }

    public SampleRequestEvent getRequestEvent(Container c, int rowId) throws SQLException
    {
        return _requestEventHelper.get(c, rowId);
    }

    public enum RequestEventType
    {
        REQUEST_CREATED("Request Created"),
        REQUEST_STATUS_CHANGED("Request Status Changed"),
        REQUIREMENT_ADDED("Requirement Created"),
        REQUIREMENT_REMOVED("Requirement Removed"),
        REQUIREMENT_UPDATED("Requirement Updated"),
        REQUEST_UPDATED("Request Updated"),
        SPECIMEN_ADDED("Specimen Added"),
        SPECIMEN_REMOVED("Specimen Removed"),
        SPECIMEN_LIST_GENERATED("Specimen List Generated"),
        COMMENT_ADDED("Comment/Attachment(s) Added"),
        NOTIFICATION_SENT("Notification Sent");

        private String _displayText;

        RequestEventType(String displayText)
        {
            _displayText = displayText;
        }

        public String getDisplayText()
        {
            return _displayText;
        }
    }

    public SampleRequestEvent createRequestEvent(User user, SampleRequestRequirement requirement, RequestEventType type, String comments, List<AttachmentFile> attachments) throws SQLException
    {
        return createRequestEvent(user, requirement.getContainer(), requirement.getRequestId(), requirement.getRowId(), type, comments, attachments);
    }

    public SampleRequestEvent createRequestEvent(User user, SampleRequest request, RequestEventType type, String comments, List<AttachmentFile> attachments) throws SQLException
    {
        return createRequestEvent(user, request.getContainer(), request.getRowId(), -1, type, comments, attachments);
    }

    private SampleRequestEvent createRequestEvent(User user, Container container, int requestId, int requirementId, RequestEventType type, String comments, List<AttachmentFile> attachments) throws SQLException
    {
        SampleRequestEvent event = new SampleRequestEvent();
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
            AttachmentService.get().addAttachments(user, event, attachments);
        }
        catch (IOException e)
        {
            // this is unexpected, and indicative of a larger system problem; we'll convert to a runtime
            // exception, rather than requiring all event loggers to handle this unlikely scenario:
            throw new RuntimeException(e);
        }
        catch (AttachmentService.DuplicateFilenameException e)
        {
            // UI should (minimally) catch and display these errors nicely or (better) validate to prevent them in the first place
            // But for now, just display the exception
            throw new RuntimeException(e);
        }
        return event;
    }

    private void deleteRequestEvents(User user, SampleRequest request) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("RequestId", request.getRowId());
        SampleRequestEvent[] events = _requestEventHelper.get(request.getContainer(), filter);
        for (SampleRequestEvent event : events)
        {
            AttachmentService.get().deleteAttachments(event);
            _requestEventHelper.delete(event);
        }
    }

    private SampleRequestEvent createRequestEvent(User user, SampleRequestEvent event) throws SQLException
    {
        return _requestEventHelper.create(user, event);
    }

    private static final String REQUEST_SPECIMEN_JOIN = "SELECT specimen.* FROM study.SampleRequest AS request, " +
            "study.SampleRequestSpecimen AS map, study.Specimen AS specimen\n" +
            "WHERE request.RowId = map.SampleRequestId AND specimen.GlobalUniqueId = map.SpecimenGlobalUniqueId\n" +
            "AND request.Container = map.Container AND map.Container = specimen.Container AND " +
            "request.RowId = ? AND request.Container = ?;";

    public Specimen[] getRequestSpecimens(SampleRequest request) throws SQLException
    {
        return Table.executeQuery(StudySchema.getInstance().getSchema(), REQUEST_SPECIMEN_JOIN,
                new Object[]{request.getRowId(), request.getContainer().getId()}, Specimen.class);
    }

    public static class RepositorySettings
    {
        private static final String KEY_SIMPLE = "Simple";
        private static final String KEY_ENABLE_REQUESTS = "EnableRequests";
        private boolean _simple;
        private boolean _enableRequests;

        public RepositorySettings()
        {
            //no-arg constructor for struts reflection
        }

        public RepositorySettings(Map<String,String> map)
        {
            this();
            String simple = map.get(KEY_SIMPLE);
            _simple = null == simple ? false : Boolean.parseBoolean(simple);
            String enableRequests = map.get(KEY_ENABLE_REQUESTS);
            _enableRequests = null == enableRequests ? !_simple : Boolean.parseBoolean(enableRequests);
        }

        public void populateMap(Map<String,String> map)
        {
            map.put(KEY_SIMPLE, Boolean.toString(_simple));
            map.put(KEY_ENABLE_REQUESTS, Boolean.toString(_enableRequests));
        }

        public boolean isSimple()
        {
            return _simple;
        }

        public void setSimple(boolean simple)
        {
            _simple = simple;
        }

        public boolean isEnableRequests()
        {
            return _enableRequests;
        }

        public void setEnableRequests(boolean enableRequests)
        {
            _enableRequests = enableRequests;
        }

        public static RepositorySettings getDefaultSettings(Container container)
        {
            RepositorySettings settings = new RepositorySettings();
            if (null != StudyManager.getInstance().getStudy(container))
            {
                settings.setSimple(false);
                settings.setEnableRequests(true);
            }
            else
            {
                settings.setSimple(true);
                settings.setEnableRequests(false);
            }
            return settings;
        }
    }

    public RepositorySettings getRepositorySettings(Container container) throws SQLException
    {
        Map<String,String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser().getUserId(),
                container.getId(), "SpecimenRepositorySettings", false);
        if (settingsMap == null)
        {
            RepositorySettings defaults = RepositorySettings.getDefaultSettings(container);
            saveRepositorySettings(container, defaults);
            return defaults;
        }
        else
            return new RepositorySettings(settingsMap);
    }

    public void saveRepositorySettings(Container container, RepositorySettings settings) throws SQLException
    {
        Map<String, String> settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser().getUserId(),
                container.getId(), "SpecimenRepositorySettings", true);
        settings.populateMap(settingsMap);
        PropertyManager.saveProperties(settingsMap);
    }



    public static class RequestNotificationSettings
    {
        private static final String KEY_REPLYTO = "ReplyTo";
        private static final String KEY_CC = "CC";
        private static final String KEY_SUBJECTSUFFIX = "SubjectSuffix";
        private static final String KEY_NEWREQUESTNOTIFY = "NewRequestNotify";
        private String _replyTo;
        private String _cc;
        private String _subjectSuffix;
        private String _newRequestNotify;
        private boolean _ccCheckbox;
        private boolean _newRequestNotifyCheckbox;

        public RequestNotificationSettings()
        {
            // no-arg constructor for struts reflection
        }

        public RequestNotificationSettings(Map<String, String> map)
        {
            _replyTo = (String) map.get(KEY_REPLYTO);
            _cc = (String) map.get(KEY_CC);
            _subjectSuffix = (String) map.get(KEY_SUBJECTSUFFIX);
            _newRequestNotify = (String) map.get(KEY_NEWREQUESTNOTIFY);
        }

        public String getReplyTo()
        {
            return _replyTo;
        }

        public void setReplyTo(String replyTo)
        {
            _replyTo = replyTo;
        }

        public String getCc()
        {
            return _cc;
        }

        public void setCc(String cc)
        {
            _cc = cc;
        }

        public String getSubjectSuffix()
        {
            return _subjectSuffix;
        }

        public void setSubjectSuffix(String subjectSuffix)
        {
            _subjectSuffix = subjectSuffix;
        }

        public String getNewRequestNotify()
        {
            return _newRequestNotify;
        }

        public void setNewRequestNotify(String newRequestNotify)
        {
            _newRequestNotify = newRequestNotify;
        }
        
        public boolean isCcCheckbox()
        {
            return _ccCheckbox;
        }

        public void setCcCheckbox(boolean ccCheckbox)
        {
            _ccCheckbox = ccCheckbox;
        }

        public boolean isNewRequestNotifyCheckbox()
        {
            return _newRequestNotifyCheckbox;
        }

        public void setNewRequestNotifyCheckbox(boolean newRequestNotifyCheckbox)
        {
            _newRequestNotifyCheckbox = newRequestNotifyCheckbox;
        }

        public void populateMap(Map<String, String> map)
        {
            map.put(KEY_REPLYTO, _replyTo);
            map.put(KEY_CC, _cc);
            map.put(KEY_SUBJECTSUFFIX, _subjectSuffix);
            map.put(KEY_NEWREQUESTNOTIFY, _newRequestNotify);
        }

        public static RequestNotificationSettings getDefaultSettings()
        {
            RequestNotificationSettings defaults = new RequestNotificationSettings();
            defaults.setReplyTo(AppProps.getInstance().getSystemEmailAddress());
            defaults.setCc("");
            defaults.setNewRequestNotify("");
            defaults.setSubjectSuffix("Specimen Request Notification");
            return defaults;
        }

        public Address[] getCCAddresses() throws ValidEmail.InvalidEmailException
        {
            if (_cc == null || _cc.length() == 0)
                return null;
            StringTokenizer splitter = new StringTokenizer(_cc, ",;: \t\n\r");
            List<Address> addresses = new ArrayList<Address>();
            while (splitter.hasMoreTokens())
            {
                String token = splitter.nextToken();
                ValidEmail tester = new ValidEmail(token);
                addresses.add(tester.getAddress());
            }
            return addresses.toArray(new Address[addresses.size()]);
        }

        public Address[] getNewRequestNotifyAddresses() throws ValidEmail.InvalidEmailException
        {
            if (_newRequestNotify == null || _newRequestNotify.length() == 0)
                return null;
            StringTokenizer splitter = new StringTokenizer(_newRequestNotify, ",;: \t\n\r");
            List<Address> addresses = new ArrayList<Address>();
            while (splitter.hasMoreTokens())
            {
                String token = splitter.nextToken();
                ValidEmail tester = new ValidEmail(token);
                addresses.add(tester.getAddress());
            }
            return addresses.toArray(new Address[addresses.size()]);
        }
    }

    public RequestNotificationSettings getRequestNotificationSettings(Container container) throws SQLException
    {
        Map<String,String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser().getUserId(),
                container.getId(), "SpecimenRequestNotifications", false);
        if (settingsMap == null || settingsMap.get("ReplyTo") == null)
        {
            RequestNotificationSettings defaults = RequestNotificationSettings.getDefaultSettings();
            saveRequestNotificationSettings(container, defaults);
            return defaults;
        }
        else
            return new RequestNotificationSettings(settingsMap);
    }

    public void saveRequestNotificationSettings(Container container, RequestNotificationSettings settings) throws SQLException
    {
        Map<String, String> settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser().getUserId(),
                container.getId(), "SpecimenRequestNotifications", true);
        settings.populateMap(settingsMap);
        PropertyManager.saveProperties(settingsMap);
    }


    public static class DisplaySettings
    {
        private static final String KEY_FLAG_ONE_AVAIL_VIAL = "OneAvailableVial";
        private static final String KEY_FLAG_ZERO_AVAIL_VIALS = "ZeroAvailableVials";
        private DisplayOption _lastVial;
        private DisplayOption _zeroVials;
        public static enum DisplayOption
        {
            NONE,
            ALL_USERS,
            ADMINS_ONLY
        }

        public DisplaySettings()
        {
            // no-arg constructor for struts reflection
        }

        public DisplaySettings(Map<String, String> map)
        {
            _lastVial = DisplayOption.valueOf(map.get(KEY_FLAG_ONE_AVAIL_VIAL));
            _zeroVials = DisplayOption.valueOf(map.get(KEY_FLAG_ZERO_AVAIL_VIALS));
        }

        public void populateMap(Map<String, String> map)
        {
            map.put(KEY_FLAG_ONE_AVAIL_VIAL, _lastVial.name());
            map.put(KEY_FLAG_ZERO_AVAIL_VIALS, _zeroVials.name());
        }

        public static DisplaySettings getDefaultSettings()
        {
            DisplaySettings defaults = new DisplaySettings();
            defaults.setLastVial(DisplayOption.ALL_USERS.name());
            defaults.setZeroVials(DisplayOption.ALL_USERS.name());
            return defaults;
        }

        public String getLastVial()
        {
            return _lastVial.name();
        }

        public void setLastVial(String lastVial)
        {
            _lastVial = DisplayOption.valueOf(lastVial);
        }

        public String getZeroVials()
        {
            return _zeroVials.name();
        }

        public void setZeroVials(String zeroVials)
        {
            _zeroVials = DisplayOption.valueOf(zeroVials);
        }

        public DisplayOption getLastVialEnum()
        {
            return _lastVial;
        }

        public DisplayOption getZeroVialsEnum()
        {
            return _zeroVials;
        }
    }

    public DisplaySettings getDisplaySettings(Container container) throws SQLException
    {
        Map<String, String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser().getUserId(),
                container.getId(), "SpecimenRequestDisplay", false);
        if (settingsMap == null || settingsMap.get("OneAvailableVial") == null)
        {
            DisplaySettings defaults = DisplaySettings.getDefaultSettings();
            saveDisplaySettings(container, defaults);
            return defaults;
        }
        else
            return new DisplaySettings(settingsMap);
    }

    public void saveDisplaySettings(Container container, DisplaySettings settings) throws SQLException
    {
        Map<String, String> settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser().getUserId(),
                container.getId(), "SpecimenRequestDisplay", true);
        settings.populateMap(settingsMap);
        PropertyManager.saveProperties(settingsMap);
    }

    public static class StatusSettings
    {
        public static final String KEY_USE_SHOPPING_CART = "UseShoppingCart";
        private boolean _useShoppingCart;

        public StatusSettings()
        {
            // no-arg constructor for struts reflection
        }

        public StatusSettings(Map<String, String> map)
        {
            String boolString = map.get(KEY_USE_SHOPPING_CART);
            _useShoppingCart = Boolean.parseBoolean(boolString);
        }

        public void populateMap(Map<String, String> map)
        {
            map.put(KEY_USE_SHOPPING_CART, String.valueOf(_useShoppingCart));
        }

        public static StatusSettings getDefaultSettings()
        {
            StatusSettings defaults = new StatusSettings();
            defaults.setUseShoppingCart(true);
            return defaults;
        }

        public boolean isUseShoppingCart()
        {
            return _useShoppingCart;
        }

        public void setUseShoppingCart(boolean useShoppingCart)
        {
            _useShoppingCart = useShoppingCart;
        }
    }

    public StatusSettings getStatusSettings(Container container) throws SQLException
    {
        Map<String, String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser().getUserId(),
                container.getId(), "SpecimenRequestStatus", false);
        if (settingsMap == null || settingsMap.get(StatusSettings.KEY_USE_SHOPPING_CART) == null)
        {
            StatusSettings defaults = StatusSettings.getDefaultSettings();
            saveStatusSettings(container, defaults);
            return defaults;
        }
        else
            return new StatusSettings(settingsMap);
    }

    public void saveStatusSettings(Container container, StatusSettings settings) throws SQLException
    {
        Map<String, String> settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser().getUserId(),
                container.getId(), "SpecimenRequestStatus", true);
        settings.populateMap(settingsMap);
        PropertyManager.saveProperties(settingsMap);
    }

    public boolean isSpecimenShoppingCartEnabled(Container container) throws SQLException
    {
        return getStatusSettings(container).isUseShoppingCart();
    }
    
    public static class SpecimenRequestInput
    {
        private String _title;
        private String _helpText;
        private boolean _required;
        private boolean _rememberSiteValue;
        private boolean _multiLine;
        private int _displayOrder;
        private Map<Integer,String> _siteToDefaultValue;

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

        public Map<Integer,String> getDefaultSiteValues(Container container) throws SQLException
        {
            if (!isRememberSiteValue())
                throw new UnsupportedOperationException("Only those inputs set to remember site values can be queried for a site default.");

            if (_siteToDefaultValue != null)
                return _siteToDefaultValue;
            String defaultObjectLsid = getRequestInputDefaultObjectLsid(container);
            String setItemLsid = ensureOntologyManagerSetItem(container, defaultObjectLsid, getTitle());
            Map<Integer, String> siteToValue = new HashMap<Integer, String>();

            Map<String, ObjectProperty> defaultValueProperties = OntologyManager.getPropertyObjects(container.getId(), setItemLsid);
            if (defaultValueProperties != null)
            {
                for (Map.Entry<String, ObjectProperty> defaultValue : defaultValueProperties.entrySet())
                {
                    String siteIdString = defaultValue.getKey().substring(defaultValue.getKey().lastIndexOf(".") + 1);
                    int siteId = Integer.parseInt(siteIdString);
                    siteToValue.put(siteId, defaultValue.getValue().getEitherStringValue());
                }
            }
            _siteToDefaultValue = siteToValue;
            return _siteToDefaultValue;
        }

        public void setDefaultSiteValue(Container container, int siteId, String value) throws SQLException
        {
            assert siteId > 0 : "Invalid site id: " + siteId;
            if (!isRememberSiteValue())
                throw new UnsupportedOperationException("Only those inputs configured to remember site values can set a site default.");
            _siteToDefaultValue = null;
            String parentObjectLsid = getRequestInputDefaultObjectLsid(container);

            String setItemLsid = ensureOntologyManagerSetItem(container, parentObjectLsid, getTitle());
            String propertyId = parentObjectLsid + "." + siteId;
            ObjectProperty defaultValueProperty = new ObjectProperty(setItemLsid, container.getId(), propertyId, value);
            OntologyManager.deleteProperty(container.getId(), setItemLsid, propertyId);
            OntologyManager.insertProperty(container.getId(), defaultValueProperty, setItemLsid);
        }
    }

    public SpecimenRequestInput[] getNewSpecimenRequestInputs(Container container) throws SQLException
    {
        return getNewSpecimenRequestInputs(container, true);
    }

    private SpecimenRequestInput[] getNewSpecimenRequestInputs(Container container, boolean createIfMissing) throws SQLException
    {
        String parentObjectLsid = getRequestInputObjectLsid(container);
        Map<String,ObjectProperty> resourceProperties = OntologyManager.getPropertyObjects(container.getId(), parentObjectLsid);
        SpecimenRequestInput[] inputs = null;
        if (resourceProperties == null || resourceProperties.size() == 0)
        {
            if (createIfMissing)
            {
                inputs = new SpecimenRequestInput[] {
                        new SpecimenRequestInput("Assay Plan", "Please enter a description of or reference to the assay plan(s) that will be used for the requested specimens.", 0, true, true, false),
                        new SpecimenRequestInput("Shipping Information", "Please enter your shipping address along with any special instructions.", 1, true, true, true),
                        new SpecimenRequestInput("Comments", "Please enter any additional information regarding your request.", 2, true, false, false)
                };
                saveNewSpecimenRequestInputs(container, inputs);
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
                String title = inputProperties.get(parentObjectLsid + ".Title").getEitherStringValue();
                String helpText = null;
                if (inputProperties.get(parentObjectLsid + ".HelpText") != null)
                    helpText = inputProperties.get(parentObjectLsid + ".HelpText").getEitherStringValue();
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
        return new Lsid("StudySpecimen", "Folder-" + container.getRowId(), "RequestInput").toString();
    }

    private static String getRequestInputDefaultObjectLsid(Container container)
    {
        return new Lsid("StudySpecimen", "Folder-" + container.getRowId(), "RequestInputDefault").toString();
    }

    private static String ensureOntologyManagerSetItem(Container container, String lsidBase, String uniqueItemId) throws SQLException
    {
        Integer listParentObjectId = OntologyManager.ensureObject(container.getId(), lsidBase);
        String listItemReferenceLsidPrefix = lsidBase + "#objectResource.";
        String listItemObjectLsid = lsidBase + "#" + uniqueItemId;
        String listItemPropertyReferenceLsid = listItemReferenceLsidPrefix + uniqueItemId;

        // ensure the object that corresponds to a single list item:
        OntologyManager.ensureObject(container.getId(), listItemObjectLsid, listParentObjectId);

        // check to make sure that the list item is wired up to the top-level list object via a property:
        Map<String, ObjectProperty> properties = OntologyManager.getPropertyObjects(container.getId(), lsidBase);
        if (!properties.containsKey(listItemPropertyReferenceLsid))
        {
            // create the resource property that links the parent object to the list item object:
            ObjectProperty resourceProperty = new ObjectProperty(lsidBase, container.getId(),
                    listItemPropertyReferenceLsid, listItemObjectLsid, PropertyType.RESOURCE);
            OntologyManager.insertProperty(container.getId(), resourceProperty, lsidBase);
        }
        return listItemObjectLsid;
    }

    public void saveNewSpecimenRequestInputs(Container container, SpecimenRequestInput[] inputs) throws SQLException
    {
        if (!requestInputsChanged(container, inputs))
            return;

        String parentObjectLsid = getRequestInputObjectLsid(container);
        String defaultValuesObjectLsid = getRequestInputDefaultObjectLsid(container);
        OntologyManager.deleteOntologyObject(parentObjectLsid, container, true);
        OntologyManager.deleteOntologyObject(defaultValuesObjectLsid, container, true);
        for (int i = 0; i < inputs.length; i++)
        {
            SpecimenRequestInput input = inputs[i];
            String setItemLsid = ensureOntologyManagerSetItem(container, parentObjectLsid, "" + i);
            ObjectProperty[] props = new ObjectProperty[5];
            props[0] = new ObjectProperty(setItemLsid, container.getId(), parentObjectLsid + ".HelpText", input.getHelpText());
            props[1] = new ObjectProperty(setItemLsid, container.getId(), parentObjectLsid + ".Required", input.isRequired() ? 1 : 0);
            props[2] = new ObjectProperty(setItemLsid, container.getId(), parentObjectLsid + ".RememberSiteValue", input.isRememberSiteValue() ? 1 : 0);
            props[3] = new ObjectProperty(setItemLsid, container.getId(), parentObjectLsid + ".Title", input.getTitle());
            props[4] = new ObjectProperty(setItemLsid, container.getId(), parentObjectLsid + ".MultiLine", input.isMultiLine() ? 1 : 0);
            OntologyManager.insertProperties(container.getId(), props, setItemLsid);
        }
    }

    private boolean requestInputsChanged(Container container, SpecimenRequestInput[] newInputs) throws SQLException
    {
        SpecimenRequestInput[] oldInputs = getNewSpecimenRequestInputs(container, false);
        if (oldInputs == null)
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

    public void createRequestSampleMapping(User user, SampleRequest request, List<Specimen> specimens, boolean createEvents) throws SQLException
    {
        if (specimens == null || specimens.size() == 0)
            return;

        for (Specimen specimen : specimens)
        {
            if (!request.getContainer().getId().equals(specimen.getContainer().getId()))
                throw new IllegalStateException("Mismatched containers.");
        }

        for (Specimen specimen : specimens)
        {
            Map<String, Object> fields = new HashMap<String, Object>();
            fields.put("Container", request.getContainer().getId());
            fields.put("SampleRequestId", request.getRowId());
            fields.put("SpecimenGlobalUniqueId", specimen.getGlobalUniqueId());
            Table.insert(user, _tableInfoRequestSpecimen, fields);
            if (createEvents)
                createRequestEvent(user, request, RequestEventType.SPECIMEN_ADDED, specimen.getSampleDescription(), null);
        }
    }

    public Specimen[] getSpecimens(Container container, int[] sampleRowIds) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("Container", container.getId());
        Set<Integer> uniqueRowIds = new HashSet<Integer>(sampleRowIds.length);
        for (int sampleRowId : sampleRowIds)
            uniqueRowIds.add(sampleRowId);
        List<Integer> rowIds = new ArrayList<Integer>(uniqueRowIds);
        filter.addInClause("RowId", rowIds);
        Specimen[] specimens = _specimenHelper.get(container, filter);
        if (specimens.length != rowIds.size())
            throw new IllegalStateException("One or more specimen RowIds had no matching specimen.");
        return specimens;
    }

    public void deleteRequest(User user, SampleRequest request) throws SQLException
    {
        DbScope scope = _requestHelper.getTableInfo().getSchema().getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        try
        {
            if (transactionOwner)
                scope.beginTransaction();

            Specimen[] specimens = request.getSpecimens();
            int[] specimenIds = new int[specimens.length];
            for (int i = 0; i < specimens.length; i++)
                specimenIds[i] = specimens[i].getRowId();

            deleteRequestSampleMappings(user, request, specimenIds, false);

            _requirementProvider.deleteRequirements(request);

            deleteRequestEvents(user, request);
            _requestHelper.delete(request);

            if (transactionOwner)
                scope.commitTransaction();
        }
        finally
        {
            if (transactionOwner)
                scope.closeConnection();
        }
    }

    public void deleteRequestSampleMappings(User user, SampleRequest request, int[] sampleIds, boolean createEvents) throws SQLException
    {
        if (sampleIds == null || sampleIds.length == 0)
            return;
        Specimen[] specimens = getSpecimens(request.getContainer(), sampleIds);
        List<String> globalUniqueIds = new ArrayList<String>(specimens.length);
        List<String> descriptions = new ArrayList<String>();
        for (Specimen specimen : specimens)
        {
            globalUniqueIds.add(specimen.getGlobalUniqueId());
            descriptions.add(specimen.getSampleDescription());
        }

        SimpleFilter filter = new SimpleFilter("SampleRequestId", request.getRowId());
        filter.addCondition("Container", request.getContainer().getId());
        filter.addInClause("SpecimenGlobalUniqueId", globalUniqueIds);
        Table.delete(_tableInfoRequestSpecimen, filter);
        if (createEvents)
        {
            for (String description : descriptions)
                createRequestEvent(user, request, RequestEventType.SPECIMEN_REMOVED, description, null);
        }
    }

    public Integer[] getRequestIdsForSpecimen(Specimen specimen) throws SQLException
    {
        if (specimen == null)
            return new Integer[0];
        SimpleFilter filter = new SimpleFilter("SpecimenGlobalUniqueId", specimen.getGlobalUniqueId());
        List<ColumnInfo> cols = Arrays.asList(_tableInfoRequestSpecimen.getColumn("SampleRequestId"));
        Table.TableResultSet rs = Table.select(_tableInfoRequestSpecimen, cols, filter, null);
        List<Integer> rowIdList = new ArrayList<Integer>();
        while (rs.next())
            rowIdList.add(rs.getInt(1));
        rs.close();
        return rowIdList.toArray(new Integer[rowIdList.size()]);
    }

    private static final String SPECIMEN_TYPE_SUMMARY_SQL = "SELECT study.SpecimenPrimaryType.PrimaryType,\n" +
            "study.SpecimenPrimaryType.ScharpId AS PrimaryTypeId,\n" +
            "study.SpecimenDerivative.Derivative AS Derivative,\n" +
            "study.SpecimenDerivative.ScharpId AS DerivativeId,\n" +
            "study.SpecimenAdditive.Additive AS Additive,\n" +
            "study.SpecimenAdditive.ScharpId AS AdditiveId,\n" +
            "Count(*) AS VialCount\n" +
            "FROM study.SpecimenDetail\n" +
            "LEFT OUTER JOIN study.SpecimenPrimaryType ON\n" +
            "\tstudy.SpecimenPrimaryType.ScharpId = study.SpecimenDetail.PrimaryTypeId AND\n" +
            "\tstudy.SpecimenPrimaryType.Container = study.SpecimenDetail.Container\n" +
            "LEFT OUTER JOIN study.SpecimenDerivative ON\n" +
            "\tstudy.SpecimenDerivative.ScharpId = study.SpecimenDetail.DerivativeTypeId AND\n" +
            "\tstudy.SpecimenDerivative.Container = study.SpecimenDetail.Container\n" +
            "LEFT OUTER JOIN study.SpecimenAdditive ON\n" +
            "\tstudy.SpecimenAdditive.ScharpId = study.SpecimenDetail.AdditiveTypeId AND\n" +
            "\tstudy.SpecimenAdditive.Container = study.SpecimenDetail.Container\n" +
            "WHERE study.SpecimenDetail.Container = ?\n" +
            "GROUP BY PrimaryType, study.SpecimenPrimaryType.ScharpId, \n" +
            "\tDerivative, study.SpecimenDerivative.ScharpId, \n" +
            "\tAdditive, study.SpecimenAdditive.ScharpId\n" +
            "ORDER BY PrimaryType, study.SpecimenPrimaryType.ScharpId, \n" +
            "\tDerivative, study.SpecimenDerivative.ScharpId, \n" +
            "\tAdditive, study.SpecimenAdditive.ScharpId";

    public SpecimenTypeSummary getSpecimenTypeSummary(Container container)
    {
        String cacheKey = container.getId() + "/SpecimenTypeSummary";
        SpecimenTypeSummary summary = (SpecimenTypeSummary) DbCache.get(StudySchema.getInstance().getTableInfoSpecimen(), cacheKey);

        if (summary != null)
            return summary;

        try
        {
            SpecimenTypeSummaryRow[] rows = Table.executeQuery(StudySchema.getInstance().getSchema(), SPECIMEN_TYPE_SUMMARY_SQL,
                    new Object[] { container.getId() }, SpecimenTypeSummaryRow.class);

            summary = new SpecimenTypeSummary(rows);
            DbCache.put(StudySchema.getInstance().getTableInfoSpecimen(), cacheKey, summary, Cache.HOUR);
            return summary;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private class DistinctValueList extends ArrayList<String> implements StudyCachable
    {
        private Container _container;
        private String _cacheKey;
        public DistinctValueList(Container container, String cacheKey)
        {
            super();
            _container = container;
            _cacheKey = cacheKey;
        }

        public StudyCachable createMutable()
        {
            throw new UnsupportedOperationException("DistinctValueList objects are never mutable.");
        }

        public Container getContainer()
        {
            return _container;
        }

        public Object getPrimaryKey()
        {
            return _cacheKey;
        }

        public void lock()
        {
        }
    }

    public List<String> getDistinctColumnValues(Container container, ColumnInfo col, boolean forceDistinctQuery) throws SQLException
    {
        String cacheKey = "DistinctColumnValues." + col.getColumnName();
        boolean isLookup = col.getFk() != null && !forceDistinctQuery; 
        TableInfo tinfo = isLookup ? col.getFk().getLookupTableInfo() : col.getParentTable();
        DistinctValueList distinctValues = (DistinctValueList) StudyCache.getCached(tinfo, container.getId(), cacheKey);
        if (distinctValues == null)
        {
            distinctValues = new DistinctValueList(container, cacheKey);
            if (col.isBooleanType())
            {
                distinctValues.add("True");
                distinctValues.add("False");
            }
            else
            {
                ResultSet rs = null;
                try
                {
                    if (isLookup)
                        rs = Table.select(tinfo, Arrays.asList(tinfo.getColumn(tinfo.getTitleColumn())), null,
                                new Sort(tinfo.getTitleColumn()));
                    else
                    {
                        String sql = "SELECT DISTINCT " + col.getSelectSql() + " FROM " +
                                tinfo.getFromSQL();
                        rs = Table.executeQuery(tinfo.getSchema(), sql, null);
                    }
                    while (rs.next())
                    {
                        Object value = rs.getObject(1);
                        if (value != null && value.toString().length() > 0)
                            distinctValues.add(value.toString());
                    }
                }
                finally
                {
                    if (rs != null) try { rs.close(); } catch (SQLException e) {}
                }
            }
            StudyCache.cache(tinfo, container.getId(), cacheKey, distinctValues);
        }
        return distinctValues;
    }

    public void deleteMissingSpecimens(SampleRequest sampleRequest) throws SQLException
    {
        List<String> missingSpecimens = getMissingSpecimens(sampleRequest);
        if (missingSpecimens.isEmpty())
            return;
        SimpleFilter filter = new SimpleFilter("Container", sampleRequest.getContainer().getId());
        filter.addCondition("SampleRequestId", sampleRequest.getRowId());
        filter.addInClause("SpecimenGlobalUniqueId", missingSpecimens);
        Table.delete(StudySchema.getInstance().getTableInfoSampleRequestSpecimen(), filter);
    }


    public List<String> getMissingSpecimens(SampleRequest sampleRequest) throws SQLException
    {
        String sql = "SELECT SpecimenGlobalUniqueId FROM study.SampleRequestSpecimen WHERE SampleRequestId = ? and Container = ? and \n" +
                "SpecimenGlobalUniqueId NOT IN (SELECT GlobalUniqueId FROM study.specimen WHERE Container = ?);";
        ResultSet rs = null;
        List<String> missingSpecimens = new ArrayList<String>();
        try
        {
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql, new Object[] {
                sampleRequest.getRowId(), sampleRequest.getContainer().getId(), sampleRequest.getContainer().getId() });
            while (rs.next())
                missingSpecimens.add(rs.getString("SpecimenGlobalUniqueId"));
        }
        finally
        {
            if (rs != null) try { rs.close(); } catch (SQLException e) {}
        }
        return missingSpecimens;
    }

    public Specimen[] getSpecimensByAvailableVialCount(Container container, int count) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("Container", container.getId());
        filter.addCondition("AvailableCount", count);
        return Table.select(StudySchema.getInstance().getTableInfoSpecimenSummary(), Table.ALL_COLUMNS, filter, null, Specimen.class);
    }

    public Map<SpecimenSummaryKey,List<Specimen>> getVialsForSamples(Container container, SpecimenSummaryKey[] specimenNumbers) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("Container", container.getId());
        filter.addClause(getSpecimenFilter(specimenNumbers));
        filter.addCondition("Available", true);
        SQLFragment sql = new SQLFragment("SELECT * FROM " + StudySchema.getInstance().getTableInfoSpecimenDetail().getAliasName() + " ");
        sql.append(filter.getSQLFragment(StudySchema.getInstance().getSqlDialect()));

        Map<SpecimenSummaryKey,List<Specimen>> map = new HashMap<SpecimenSummaryKey, List<Specimen>>();
        Specimen[] specimens = Table.executeQuery(StudySchema.getInstance().getSchema(), sql.getSQL(), sql.getParamsArray(), Specimen.class);
        for (Specimen specimen : specimens)
        {
            SpecimenSummaryKey key = new SpecimenSummaryKey(specimen);
            List<Specimen> keySpecimens = map.get(key);
            if (null == keySpecimens)
            {
                keySpecimens = new ArrayList<Specimen>();
                map.put(key, keySpecimens);
            }
            keySpecimens.add(specimen);
        }
        return map;
    }

    public static class SpecimenSummaryKey
    {
        private String _specimenNumber;
        private Integer _derivativeTypeId;
        private Integer _additiveTypeId;

        public SpecimenSummaryKey(String commaSeparatedKey)
        {
            String[] parts = StringUtils.split(commaSeparatedKey, ',');
            _specimenNumber = parts[0];
            _derivativeTypeId = Integer.parseInt(parts[1]);
            _additiveTypeId = Integer.parseInt(parts[2]);
        }

        public SpecimenSummaryKey(Specimen specimen)
        {
            _specimenNumber = specimen.getSpecimenNumber();
            _derivativeTypeId = specimen.getDerivativeTypeId();
            _additiveTypeId = specimen.getAdditiveTypeId();

        }
        public SpecimenSummaryKey(String specimenNumber, int derivativeTypeId, int additiveTypeId)
        {
            _specimenNumber = specimenNumber;
            _derivativeTypeId = derivativeTypeId;
            _additiveTypeId = additiveTypeId;
        }

        public String getSpecimenNumber()
        {
            return _specimenNumber;
        }

        public void setSpecimenNumber(String specimenNumber)
        {
            _specimenNumber = specimenNumber;
        }

        public Integer getDerivativeTypeId()
        {
            return _derivativeTypeId;
        }

        public void setDerivativeTypeId(int derivativeTypeId)
        {
            _derivativeTypeId = derivativeTypeId;
        }

        public Integer getAdditiveTypeId()
        {
            return _additiveTypeId;
        }

        public void setAdditiveTypeId(int additiveTypeId)
        {
            _additiveTypeId = additiveTypeId;
        }

        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SpecimenSummaryKey that = (SpecimenSummaryKey) o;

            if (_additiveTypeId != null ? !_additiveTypeId.equals(that._additiveTypeId) : that._additiveTypeId != null)
                return false;
            if (_derivativeTypeId != null ? !_derivativeTypeId.equals(that._derivativeTypeId) : that._derivativeTypeId != null)
                return false;
            if (_specimenNumber != null ? !_specimenNumber.equals(that._specimenNumber) : that._specimenNumber != null)
                return false;

            return true;
        }

        public int hashCode()
        {
            int result;
            result = (_specimenNumber != null ? _specimenNumber.hashCode() : 0);
            result = 31 * result + (_derivativeTypeId != null ? _derivativeTypeId.hashCode() : 0);
            result = 31 * result + (_additiveTypeId != null ? _additiveTypeId.hashCode() : 0);
            return result;
        }
    }

    private SimpleFilter.SQLClause getSpecimenFilter(SpecimenSummaryKey[] specimens)
    {
        String sep = "";
        StringBuilder filterFragment = new StringBuilder();
        List<Object> allParams = new ArrayList<Object>(specimens.length * 3);
        for (SpecimenSummaryKey ssi : specimens)
        {
            filterFragment.append(sep);
            filterFragment.append("(SpecimenNumber = ? AND AdditiveTypeId = ? AND DerivativeTypeId = ?)");
            allParams.addAll(Arrays.asList(ssi.getSpecimenNumber(), ssi.getAdditiveTypeId(), ssi.getDerivativeTypeId()));
            sep = " OR ";
        }
        return new SimpleFilter.SQLClause(filterFragment.toString(), allParams.toArray(), "SpecimenNumber", "AdditiveTypeId", "DerivativeTypeId");
    }


    public Map<SpecimenSummary,Integer> getSampleCounts(Container container, Set<String> specimenNumbers) throws SQLException
    {

        Table.TableResultSet rs = null;
        Map<SpecimenSummary, Integer> map = new HashMap<SpecimenSummary, Integer>();
        try
        {
            List<Object> params = new ArrayList<Object>();
            params.add(container.getId());
            StringBuilder extraClause = new StringBuilder();
            if (specimenNumbers != null)
            {
                extraClause.append(" AND SpecimenNumber IN(");
                String separator = "";
                for (String specimenNumber : specimenNumbers)
                {
                    extraClause.append(separator);
                    separator = ", ";
                    extraClause.append("?");
                    params.add(specimenNumber);
                }
                extraClause.append(")");
            }

            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), "SELECT " +
                    "SpecimenNumber, " +
                    "PrimaryTypeId AS PrimaryType, " +
                    "DerivativeTypeId AS DerivativeType, " +
                    "ptid AS ParticipantId, " +
                    "VisitDescription, " +
                    "VisitValue AS Visit, " +
                    "VolumeUnits, " +
                    "AdditiveTypeId AS AdditiveType, " +
                    "DrawTimestamp, " +
                    "SalReceiptDate, " +
                    "ClassId, " +
                    "ProtocolNumber, " +
                    "SubAdditiveDerivative, " +
                    "CAST(AvailableCount AS Integer) AS AvailableCount" +
                    " FROM " + StudySchema.getInstance().getTableInfoSpecimenSummary().getAliasName() + " WHERE Container = ?" + extraClause, params.toArray(new Object[params.size()]));
            while(rs.next())
            {
                SpecimenSummary summary = new SpecimenSummary(container, rs.getRowMap());
                map.put(summary, rs.getInt("AvailableCount"));
            }
        }
        finally
        {
            if (null != rs)
                rs.close();
        }
        return map;
    }


    public void upgradeRequirementsTables() throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("EntityId", null, CompareType.ISBLANK);

        SampleRequest[] requests = Table.select(StudySchema.getInstance().getTableInfoSampleRequest(),
                Table.ALL_COLUMNS, filter, null, SampleRequest.class);

        if (requests != null && requests.length > 0)
        {
            // first, we'll build up a set of the type and id of all the "default" requirement request objects.
            // we'll do this by searching each container that contains any requests for default requirements.
            Map<Integer, Pair<Container, SpecimenRequestRequirementType>> defaultRequirementIdToType =
                    new HashMap<Integer, Pair<Container, SpecimenRequestRequirementType>>();
            Set<String> seenContainerIds = new HashSet<String>();
            for (SampleRequest request : requests)
            {
                if (!seenContainerIds.contains(request.getContainer().getId()))
                {
                    seenContainerIds.add(request.getContainer().getId());
                    for (SpecimenRequestRequirementType type : SpecimenRequestRequirementType.values())
                    {
                        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(0, request.getContainer().getId(),
                                "SampleRequestRequirement", true);
                        String ownerRowId = (String) props.get(type.name());
                        if (ownerRowId != null)
                        {
                            try
                            {
                                defaultRequirementIdToType.put(Integer.parseInt(ownerRowId),
                                        new Pair<Container, SpecimenRequestRequirementType>(request.getContainer(), type));
                            }
                            catch (NumberFormatException e)
                            {
                                // fall through; we'll just skip this default requirement.
                            }
                        }
                    }
                }
            }


            DbSchema schema = StudySchema.getInstance().getSchema();
            DbScope scope = schema.getScope();
            boolean transactionOwner = !scope.isTransactionActive();
            try
            {
                if (transactionOwner)
                    scope.beginTransaction();

                for (SampleRequest request : requests)
                {
                    String entityId = GUID.makeGUID();
                    request = request.createMutable();
                    request.setEntityId(entityId);
                    updateRequest(null, request);
                    SimpleFilter requirementFilter = new SimpleFilter("Container", request.getContainer());
                    requirementFilter.addCondition("RequestId", request.getRowId());
                    requirementFilter.addCondition("OwnerEntityId", null, CompareType.ISBLANK);

                    SampleRequestRequirement[] requirements =
                            Table.select(StudySchema.getInstance().getTableInfoSampleRequestRequirement(),
                                    Table.ALL_COLUMNS, requirementFilter, null, SampleRequestRequirement.class);
                    for (SampleRequestRequirement requirement : requirements)
                    {
                        Pair<Container, SpecimenRequestRequirementType> pair = defaultRequirementIdToType.get(request.getRowId());
                        if (pair != null)
                        {
                            requirement = requirement.createMutable();
                            requirement.setRequestId(-1);
                            _requirementProvider.createDefaultRequirement(null, requirement, pair.getValue());
                        }
                        else
                        {
                            requirement = requirement.createMutable();
                            requirement.setOwnerEntityId(entityId);
                            updateRequestRequirement(null, requirement);
                        }
                    }
                }

                if (transactionOwner)
                    scope.commitTransaction();
            }
            finally
            {
                if (transactionOwner)
                    scope.closeConnection();
            }
        }
    }

    public void deleteAllSampleData(Container c, Set<TableInfo> set) throws SQLException
    {
        // UNDONE: use transaction?
        SimpleFilter containerFilter = new SimpleFilter("Container", c.getId());

        Table.delete(_tableInfoRequestSpecimen, containerFilter);
        assert set.add(_tableInfoRequestSpecimen);
        Table.delete(_requestEventHelper.getTableInfo(), containerFilter);
        assert set.add(_requestEventHelper.getTableInfo());
        Table.delete(_requestHelper.getTableInfo(), containerFilter);
        assert set.add(_requestHelper.getTableInfo());
        Table.delete(_requestStatusHelper.getTableInfo(), containerFilter);
        assert set.add(_requestStatusHelper.getTableInfo());
        Table.delete(_specimenEventHelper.getTableInfo(), containerFilter);
        assert set.add(_specimenEventHelper.getTableInfo());
        Table.delete(_specimenHelper.getTableInfo(), containerFilter);
        assert set.add(_specimenHelper.getTableInfo());
        Table.delete(StudySchema.getInstance().getTableInfoSpecimenAdditive(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoSpecimenAdditive());
        Table.delete(StudySchema.getInstance().getTableInfoSpecimenDerivative(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoSpecimenDerivative());
        Table.delete(StudySchema.getInstance().getTableInfoSpecimenPrimaryType(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoSpecimenPrimaryType());

        _requirementProvider.purgeContainer(c);
        assert set.add(StudySchema.getInstance().getTableInfoSampleRequestRequirement());
        assert set.add(StudySchema.getInstance().getTableInfoSampleRequestActor());

        DbSchema expSchema = ExperimentService.get().getSchema();
        TableInfo tinfoMaterial = expSchema.getTable("Material");
        containerFilter.addCondition("CpasType", "StudySpecimen");
        Table.delete(tinfoMaterial, containerFilter);

        // Views
        assert set.add(StudySchema.getInstance().getSchema().getTable("LockedSpecimens"));
        assert set.add(StudySchema.getInstance().getSchema().getTable("SpecimenDetail"));
        assert set.add(StudySchema.getInstance().getSchema().getTable("SpecimenSummary"));
    }


    public void clearCaches(Container c)
    {
        _requestEventHelper.clearCache(c);
        _specimenHelper.clearCache(c);
        _specimenEventHelper.clearCache(c);
        _requestHelper.clearCache(c);
        _requestStatusHelper.clearCache(c);
        DbCache.clear(StudySchema.getInstance().getTableInfoSpecimen());
        StudyCache.clearCache(StudySchema.getInstance().getTableInfoSpecimenSummary(), c.getId());
        StudyCache.clearCache(StudySchema.getInstance().getTableInfoSpecimenDetail(), c.getId());
        StudyCache.clearCache(StudySchema.getInstance().getTableInfoSpecimenAdditive(), c.getId());
        StudyCache.clearCache(StudySchema.getInstance().getTableInfoSpecimenDerivative(), c.getId());
        StudyCache.clearCache(StudySchema.getInstance().getTableInfoSpecimenPrimaryType(), c.getId());
    }

    public Visit[] getVisitsWithSpecimens(Container container)
    {
        return getVisitsWithSpecimens(container, null);
    }

    public Visit[] getVisitsWithSpecimens(Container container, Cohort cohort)
    {
        try
        {
            SQLFragment visitIdSQL = new SQLFragment("SELECT VisitValue from study.Specimen " +
                    "WHERE Container = ? GROUP BY VisitValue", container.getId());
            List<Double> visitIds = new ArrayList<Double>();
            ResultSet rs = null;
            try
            {
                rs = Table.executeQuery(StudySchema.getInstance().getSchema(), visitIdSQL);
                while (rs.next())
                    visitIds.add(rs.getDouble(1));
            }
            finally
            {
                if (rs != null) try { rs.close(); } catch (SQLException e) { /* fall through */ }
            }

            SimpleFilter filter = new SimpleFilter("Container", container.getId());
            filter.addInClause("SequenceNumMin", visitIds);
            if (cohort != null)
                filter.addWhereClause("CohortId IS NULL OR CohortId = ?", new Object[] { cohort.getRowId() });
            return Table.select(StudySchema.getInstance().getTableInfoVisit(), Table.ALL_COLUMNS, filter, new Sort("DisplayOrder,SequenceNumMin"), Visit.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static class SummaryByVisitType extends SpecimenCountSummary
    {
        private String _primaryType;
        private Integer _primaryTypeId;
        private String _derivative;
        private Integer _derivativeTypeId;
        private String _additive;
        private Integer _additiveTypeId;
        private Long _participantCount;
        private Set<String> _participantIds;

        public String getPrimaryType()
        {
            return _primaryType;
        }

        public void setPrimaryType(String primaryType)
        {
            _primaryType = primaryType;
        }

        public String getDerivative()
        {
            return _derivative;
        }

        public void setDerivative(String derivative)
        {
            _derivative = derivative;
        }

        public Long getParticipantCount()
        {
            return _participantCount;
        }

        public void setParticipantCount(Long participantCount)
        {
            _participantCount = participantCount;
        }

        public Integer getPrimaryTypeId()
        {
            return _primaryTypeId;
        }

        public void setPrimaryTypeId(Integer primaryTypeId)
        {
            _primaryTypeId = primaryTypeId;
        }

        public Integer getDerivativeTypeId()
        {
            return _derivativeTypeId;
        }

        public void setDerivativeTypeId(Integer derivativeTypeId)
        {
            _derivativeTypeId = derivativeTypeId;
        }

        public Set<String> getParticipantIds()
        {
            return _participantIds;
        }

        public void setParticipantIds(Set<String> participantIds)
        {
            _participantIds = participantIds;
        }

        public String getAdditive()
        {
            return _additive;
        }

        public void setAdditive(String additive)
        {
            _additive = additive;
        }

        public Integer getAdditiveTypeId()
        {
            return _additiveTypeId;
        }

        public void setAdditiveTypeId(Integer additiveTypeId)
        {
            _additiveTypeId = additiveTypeId;
        }
    }

    public static class RequestSummaryByVisitType extends SummaryByVisitType
    {
        private Integer _destinationSiteId;
        private String _siteLabel;

        public Integer getDestinationSiteId()
        {
            return _destinationSiteId;
        }

        public void setDestinationSiteId(Integer destinationSiteId)
        {
            _destinationSiteId = destinationSiteId;
        }

        public String getSiteLabel()
        {
            return _siteLabel;
        }

        public void setSiteLabel(String siteLabel)
        {
            _siteLabel = siteLabel;
        }
    }

    public SummaryByVisitType[] getSpecimenSummaryByVisitType(Container container, boolean includeParticipantLists, SpecimenTypeLevel level) throws SQLException
    {
        return getSpecimenSummaryByVisitType(container, null, includeParticipantLists, level);
    }

    public enum SpecimenTypeLevel
    {
        PrimaryType()
        {
            public String getJoinClauses(String detailsTable)
            {
                return "LEFT JOIN study.SpecimenPrimaryType on \n" +
                    "\tstudy.SpecimenPrimaryType.scharpid = " + detailsTable + ".PrimaryTypeid AND \n" +
                    "\tstudy.SpecimenPrimaryType.container = " + detailsTable + ".container\n";
            }

            public String getDisplaySelectColumns()
            {
                return getSpecimenDetailColumns() + ", PrimaryType";
            }

            public String getSpecimenDetailColumns()
            {
                return "PrimaryTypeId";
            }

            public String[] getTitleHirarchy(SummaryByVisitType summary)
            {
                return new String[] { summary.getPrimaryType() };
            }
            public String getLabel()
            {
                return "Primary Type";
            }},
        Derivative()
        {
            public String getJoinClauses(String detailsTable)
            {
                return PrimaryType.getJoinClauses(detailsTable) +
                    "LEFT JOIN study.SpecimenDerivative on \n" +
                    "\tstudy.SpecimenDerivative.scharpid = " + detailsTable + ".DerivativeTypeid AND \n" +
                    "\tstudy.SpecimenDerivative.container = " + detailsTable + ".container\n";
            }

            public String getDisplaySelectColumns()
            {
                return PrimaryType.getDisplaySelectColumns() + ", DerivativeTypeId, Derivative";
            }

            public String getSpecimenDetailColumns()
            {
                return PrimaryType.getSpecimenDetailColumns() + ", DerivativeTypeId";
            }

            public String[] getTitleHirarchy(SummaryByVisitType summary)
            {
                return new String[] { summary.getPrimaryType(), summary.getDerivative() };
            }
            public String getLabel()
            {
                return "Derivative";
            }},
        Additive()
        {
            public String getJoinClauses(String detailsTable)
            {
                return Derivative.getJoinClauses(detailsTable) +
                    "LEFT JOIN study.SpecimenAdditive on \n" +
                    "\tstudy.SpecimenAdditive.scharpid = " + detailsTable + ".AdditiveTypeid AND \n" +
                    "\tstudy.SpecimenAdditive.container = " + detailsTable + ".container\n";
            }

            public String getDisplaySelectColumns()
            {
                return Derivative.getDisplaySelectColumns() + ", AdditiveTypeId, Additive";
            }

            public String getSpecimenDetailColumns()
            {
                return Derivative.getSpecimenDetailColumns() + ", AdditiveTypeId";
            }

            public String[] getTitleHirarchy(SummaryByVisitType summary)
            {
                return new String[] { summary.getPrimaryType(), summary.getDerivative(), summary.getAdditive() };
            }
            public String getLabel()
            {
                return "Additive";
            }};

        public abstract String[] getTitleHirarchy(SummaryByVisitType summary);
        public abstract String getSpecimenDetailColumns();
        public abstract String getDisplaySelectColumns();
        public abstract String getJoinClauses(String detailsTable);
        public abstract String getLabel();
    }

    public SummaryByVisitType[] getSpecimenSummaryByVisitType(Container container, SimpleFilter specimenDetailFilter, boolean includeParticipantLists, SpecimenTypeLevel level) throws SQLException
    {
        if (specimenDetailFilter == null)
            specimenDetailFilter = new SimpleFilter();
        else
        {
            SimpleFilter clone = new SimpleFilter();
            clone.addAllClauses(specimenDetailFilter);
            specimenDetailFilter = clone;
        }
        specimenDetailFilter.addCondition("Container", container.getId());
        SQLFragment filterSql = specimenDetailFilter.getSQLFragment(StudySchema.getInstance().getSqlDialect());

        String perPtidSpecimenSQL = "\t-- Inner SELECT gets the number of vials per participant/visit/type:\n" +
            "\tSELECT Container, VisitValue, " + level.getSpecimenDetailColumns() + ", ptid, COUNT(*) AS VialCount, \n" +
            "\tSUM(Volume) AS PtidVolume FROM study.SpecimenDetail\n" + filterSql.toString() + "\n" +
            "\tGROUP BY Container, ptid, VisitValue, " + level.getSpecimenDetailColumns() + "\n";

        StringBuilder sql = new StringBuilder("-- Outer grouping allows us to count participants AND sum vial counts:\n" +
            "SELECT VisitValue AS SequenceNum, " + level.getDisplaySelectColumns() + ", COUNT(*) as ParticipantCount, \n" +
            "SUM(VialCount) AS VialCount, SUM(VialData.PtidVolume) AS TotalVolume FROM \n" +
            "(\n" + perPtidSpecimenSQL + ") AS VialData \n" + level.getJoinClauses("VialData") +
            "GROUP BY VisitValue, " + level.getDisplaySelectColumns() + "\n" +
            "ORDER BY " + level.getDisplaySelectColumns() + ", VisitValue");

        SummaryByVisitType[] summaries = Table.executeQuery(StudySchema.getInstance().getSchema(), sql.toString(), filterSql.getParamsArray(), SummaryByVisitType.class);

        if (includeParticipantLists)
            setSummaryParticpantLists(perPtidSpecimenSQL, filterSql.getParamsArray(), level, summaries, "ptid", "VisitValue");
        return summaries;
    }

    private String getPtidListKey(Double visitValue, Integer primaryTypeId, Integer derivativeTypeId, Integer additiveTypeId)
    {
        return visitValue + "/" + primaryTypeId + "/" +
            (derivativeTypeId != null ? derivativeTypeId : "all") +
            (additiveTypeId != null ? additiveTypeId : "all");
    }

    public Site[] getSitesWithRequests(Container container) throws SQLException
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM study.site WHERE rowid IN\n" +
                "(SELECT destinationsiteid FROM study.samplerequest WHERE container = ?)\n" +
                "AND container = ? ORDER BY label", container.getId(), container.getId());
        return Table.executeQuery(StudySchema.getInstance().getSchema(), sql.getSQL(), sql.getParamsArray(), Site.class);
    }

    public Set<Site> getEnrollmentSitesWithRequests(Container container)
    {
        SQLFragment sql = new SQLFragment("SELECT Participant.EnrollmentSiteId FROM study.Specimen AS Specimen, " +
                "study.SampleRequestSpecimen AS RequestSpecimen, \n" +
                "study.SampleRequest AS Request, study.SampleRequestStatus AS Status,\n" +
                "study.Participant AS Participant\n" +
                "WHERE Request.Container = Status.Container AND\n" +
                "\tRequest.StatusId = Status.RowId AND\n" +
                "\tRequestSpecimen.SampleRequestId = Request.RowId AND\n" +
                "\tRequestSpecimen.Container = Request.Container AND\n" +
                "\tSpecimen.Container = RequestSpecimen.Container AND\n" +
                "\tSpecimen.GlobalUniqueId = RequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                "\tParticipant.Container = Specimen.Container AND\n" +
                "\tParticipant.ParticipantId = Specimen.Ptid AND\n" +
                "\tStatus.SpecimensLocked = ? AND\n" +
                "\tRequest.Container = ?", Boolean.TRUE, container.getId());

        return getSitesWithIdSql(container, "EnrollmentSiteId", sql);
    }

    public Set<Site> getEnrollmentSitesWithSpecimens(Container container)
    {
        SQLFragment sql = new SQLFragment("SELECT EnrollmentSiteId FROM study.Specimen AS Specimen, study.Participant AS Participant\n" +
                "WHERE Specimen.Ptid = Participant.ParticipantId AND\n" +
                "\tSpecimen.Container = Participant.Container AND\n" +
                "\tSpecimen.Container = ?\n" +
                "GROUP BY EnrollmentSiteId", container.getId());

        return getSitesWithIdSql(container, "EnrollmentSiteId", sql);
    }

    private Set<Site> getSitesWithIdSql(Container container, String idColumnName, SQLFragment sql)
    {
        ResultSet rs = null;
        try
        {
            Set<Site> sites = new TreeSet<Site>(new Comparator<Site>()
            {
                public int compare(Site s1, Site s2)
                {
                    if (s1 == null && s2 == null)
                        return 0;
                    if (s1 == null)
                        return -1;
                    if (s2 == null)
                        return 1;
                    return s1.getLabel().compareTo(s2.getLabel());
                }
            });

            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql);
            while (rs.next())
            {
                Integer siteId = rs.getInt(idColumnName);
                if (siteId == null)
                    sites.add(null);
                else
                    sites.add(StudyManager.getInstance().getSite(container, siteId));
            }
            return sites;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (rs != null) try { rs.close(); } catch (SQLException e) { /* fall through */ }
        }
    }


    public static class SummaryByVisitParticipant extends SpecimenCountSummary
    {
        private String _participantId;
        private String _cohort;

        public String getParticipantId()
        {
            return _participantId;
        }

        public void setParticipantId(String participantId)
        {
            _participantId = participantId;
        }

        public String getCohort()
        {
            return _cohort;
        }

        public void setCohort(String cohort)
        {
            _cohort = cohort;
        }
    }

    public SummaryByVisitParticipant[] getParticipantSummaryByVisitType(Container container, SimpleFilter specimenDetailFilter) throws SQLException
    {
        if (specimenDetailFilter == null)
            specimenDetailFilter = new SimpleFilter();
        else
        {
            SimpleFilter clone = new SimpleFilter();
            clone.addAllClauses(specimenDetailFilter);
            specimenDetailFilter = clone;
        }
        specimenDetailFilter.addCondition("study.SpecimenDetail.Container", container.getId());
        SQLFragment filterSql = specimenDetailFilter.getSQLFragment(StudySchema.getInstance().getSqlDialect());

        String ptidSpecimenSQL = "SELECT VisitValue AS SequenceNum, ptid AS ParticipantId,\n" +
                "COUNT(*) AS VialCount, study.Cohort.Label AS Cohort, SUM(Volume) AS TotalVolume\n" +
                "FROM study.SpecimenDetail\n" +
                "LEFT OUTER JOIN study.Participant ON\n" +
                "\tstudy.SpecimenDetail.ptid = study.Participant.ParticipantId AND\n" +
                "\tstudy.SpecimenDetail.Container = study.Participant.Container\n" +
                "LEFT OUTER JOIN study.Cohort ON \n" +
                "\tstudy.Participant.CohortId = study.Cohort.RowId AND\n" +
                "\tStudy.Participant.Container = study.Cohort.Container" +
                "\t" + filterSql.getSQL() + "\n" +
                "GROUP BY study.SpecimenDetail.Container, study.Cohort.Label, VisitValue, ptid\n" +
                "ORDER BY study.Cohort.Label, ptid, VisitValue";

        SummaryByVisitParticipant[] summaries = Table.executeQuery(StudySchema.getInstance().getSchema(),
                ptidSpecimenSQL, filterSql.getParamsArray(), SummaryByVisitParticipant.class);

        return summaries;
    }

    public RequestSummaryByVisitType[] getRequestSummaryBySite(Container container, SimpleFilter specimenDetailFilter, boolean includeParticipantLists, SpecimenTypeLevel level) throws SQLException
    {
        if (specimenDetailFilter == null)
            specimenDetailFilter = new SimpleFilter();
        else
        {
            SimpleFilter clone = new SimpleFilter();
            clone.addAllClauses(specimenDetailFilter);
            specimenDetailFilter = clone;
        }
        specimenDetailFilter.addCondition("Request.Container", container.getId());
        SQLFragment filterSql = specimenDetailFilter.getSQLFragment(StudySchema.getInstance().getSqlDialect());

        String sql = "SELECT Specimen.Container, Specimen.Ptid AS ParticipantId, Request.DestinationSiteId,\n" +
                "Site.Label AS SiteLabel, VisitValue AS SequenceNum, \n" +
                 level.getDisplaySelectColumns() + ", COUNT(*) AS VialCount, SUM(Volume) AS TotalVolume\n" +
                "FROM study.Specimen AS Specimen\n" +
                "JOIN study.SampleRequestSpecimen AS RequestSpecimen ON \n" +
                "\tSpecimen.GlobalUniqueId = RequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                "\tSpecimen.Container = RequestSpecimen.Container\n" +
                "JOIN study.SampleRequest AS Request ON\n" +
                "\tRequestSpecimen.SampleRequestId = Request.RowId AND\n" +
                "\tRequestSpecimen.Container = Request.Container\n" +
                "JOIN study.Site AS Site ON\n" +
                "\tSite.Container = Request.Container AND\n" +
                "\tSite.RowId = Request.DestinationSiteId\n" +
                "JOIN study.SampleRequestStatus AS Status ON\n" +
                "\tStatus.Container = Request.Container AND\n" +
                "\tStatus.RowId = Request.StatusId and Status.SpecimensLocked = ?\n" +
                level.getJoinClauses("Specimen") + "\n" +
                filterSql.toString() +
                "GROUP BY Specimen.Container, Specimen.Ptid, Site.Label, DestinationSiteId, " + level.getDisplaySelectColumns() + ", VisitValue\n" +
                "ORDER BY Specimen.Container, Specimen.Ptid, Site.Label, DestinationSiteId, " + level.getDisplaySelectColumns() + ", VisitValue";

        Object[] params = new Object[filterSql.getParamsArray().length + 1];
        params[0] = Boolean.TRUE;
        System.arraycopy(filterSql.getParamsArray(), 0, params, 1, filterSql.getParamsArray().length);
        RequestSummaryByVisitType[] summaries = Table.executeQuery(StudySchema.getInstance().getSchema(), sql, params, RequestSummaryByVisitType.class);
        if (includeParticipantLists)
            setSummaryParticpantLists(sql, params, level, summaries, "ParticipantId", "SequenceNum");
        return summaries;
    }

    private void setSummaryParticpantLists(String sql, Object[] paramArray, SpecimenTypeLevel level,
                                           SummaryByVisitType[] summaries, String ptidColumnName, String visitValueColumnName) throws SQLException
    {
        Table.TableResultSet rs = null;
        try
        {
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql, paramArray);
            Map<String, Set<String>> cellToPtidSet = new HashMap<String, Set<String>>();
            while (rs.next())
            {
                String ptid = rs.getString(ptidColumnName);
                Double visitValue = rs.getDouble(visitValueColumnName);
                Integer primaryTypeId = rs.getInt("PrimaryTypeId");
                Integer derivativeTypeId = null;
                if (level == SpecimenTypeLevel.Derivative || level == SpecimenTypeLevel.Additive)
                    derivativeTypeId = rs.getInt("DerivativeTypeId");
                Integer additiveTypeId = null;
                if (level == SpecimenTypeLevel.Additive)
                    additiveTypeId = rs.getInt("AdditiveTypeId");

                String key = getPtidListKey(visitValue, primaryTypeId, derivativeTypeId, additiveTypeId);

                Set<String> ptids = cellToPtidSet.get(key);
                if (ptids == null)
                {
                    ptids = new TreeSet<String>();
                    cellToPtidSet.put(key, ptids);
                }
                ptids.add(ptid);
            }

            for (SummaryByVisitType summary : summaries)
            {
                Double visitValue = summary.getSequenceNum();
                Integer primaryTypeId = summary.getPrimaryTypeId();
                Integer derivativeTypeId = summary.getDerivativeTypeId();
                Integer additiveTypeId = summary.getAdditiveTypeId();
                String key = getPtidListKey(visitValue, primaryTypeId, derivativeTypeId, additiveTypeId);
                Set<String> ptids = cellToPtidSet.get(key);
                summary.setParticipantIds(ptids);
            }
        }
        finally
        {
            if (rs != null) try { rs.close(); } catch (SQLException e) { /* fall through */ }
        }

    }
}
