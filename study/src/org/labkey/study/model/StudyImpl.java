/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

package org.labkey.study.model;

import gwt.client.org.labkey.study.designer.client.model.GWTStudyDefinition;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.FileAttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.Results;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Transient;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.QueryService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.study.Location;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudySnapshotType;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HTMLContentExtractor;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiRenderingService;
import org.labkey.study.StudyModule;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.designer.StudyDesignInfo;
import org.labkey.study.designer.StudyDesignManager;
import org.labkey.study.query.StudyQuerySchema;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:28:32 AM
 */
public class StudyImpl extends ExtensibleStudyEntity<StudyImpl> implements Study
{
    private static final Logger LOG = LogManager.getLogger(StudyImpl.class);
    private static final String DOMAIN_URI_PREFIX = "Study";

    public static final DomainInfo DOMAIN_INFO = new StudyDomainInfo(DOMAIN_URI_PREFIX, true);

    private String _label;
    private TimepointType _timepointType;
    private Date _startDate;
    private Date _endDate;
    private SecurityType _securityType = SecurityType.BASIC_READ; // Default value. Not allowed to be null
    private String _participantCohortProperty;
    private Integer _participantCohortDatasetId;
    private boolean _manualCohortAssignment;
    private String _lsid;
    private Integer _defaultPipelineQCState;
    private Integer _defaultPublishDataQCState;
    private Integer _defaultDirectEntryQCState;
    private boolean _showPrivateDataByDefault = true;
    private boolean _blankQCStatePublic = false;
    private boolean _advancedCohorts;
    private Integer _participantCommentDatasetId;
    private String _participantCommentProperty;
    private Integer _participantVisitCommentDatasetId;
    private String _participantVisitCommentProperty;
    private String _subjectNounSingular;
    private String _subjectNounPlural;
    private String _subjectColumnName;
    private String _assayPlan;
    private String _description;
    private String _descriptionRendererType = WikiRendererType.TEXT_WITH_LINKS.name();
    private String _protocolDocumentEntityId;
    private String _sourceStudyContainerId;
    private String _investigator;
    private String _grant;
    private String _species;
    private int _defaultTimepointDuration = 1;
    private String _alternateIdPrefix;
    private int _alternateIdDigits;
    private Integer _studySnapshot = null;  // RowId of the study snapshot configuration that created this study (or null)
    private StudySnapshotType _studySnapshotType = null;
    private Date _lastSpecimenLoad = null;
    private boolean _allowReqLocRepository = true;
    private boolean _allowReqLocClinic = true;
    private boolean _allowReqLocSal = true;
    private boolean _allowReqLocEndpoint = true;

    private Integer _participantAliasDatasetId;
    private String _participantAliasSourceProperty;
    private String _participantAliasProperty;
    private Integer _lastSpecimenRequest = null;

    // experimental
    private boolean _shareDatasetDefinitions = false;
    private boolean _shareVisitDefinitions = false;


    public StudyImpl()
    {
    }

    public StudyImpl(Container container, String label)
    {
        super(container);
        _label = label;
        _entityId = GUID.makeGUID();
    }

    @Override
    public String toString()
    {
        return getDisplayString();
    }

    @Override
    @Transient
    public SecurableResource getParentResource()
    {
        //overridden to return the container
        //all other study entities return the study,
        //but the study's parent is the container
        return getContainer();
    }

    @Override
    @NotNull
    public List<SecurableResource> getChildResources(User user)
    {
        List<DatasetDefinition> datasets = getDatasets();
        ArrayList<SecurableResource> readableDatasets = new ArrayList<>(datasets.size());
        for (DatasetDefinition ds : datasets)
            if (ds.canRead(user))
                readableDatasets.add(ds);

        return readableDatasets;
    }

    @NotNull
    @Override
    @Transient
    public String getResourceDescription()
    {
        return "The study " + _label;
    }

    @Override
    public String getLabel()
    {
        return _label;
    }


    @Override
    public void setLabel(String label)
    {
        verifyMutability();
        _label = label;
    }


    // TODO: Make short name admin editable, persist, and display. For now, just use the first "word" of the label.
    @Override
    @Transient
    public String getShortName()
    {
        String label = _label == null ? "Study" : _label;
        String shortName = label.split("\\s", 2)[0];
        return shortName.isEmpty() ? label : shortName;
    }


    @Override
    public List<VisitImpl> getVisits(Visit.Order order)
    {
        return StudyManager.getInstance().getVisits(this, order);
    }

    @Override
    @Transient
    public Map<String, BigDecimal> getVisitAliases()
    {
        return StudyManager.getInstance().getCustomVisitImportMapping(this)
            .stream()
            .collect(Collectors.toMap(StudyManager.VisitAlias::getName, StudyManager.VisitAlias::getSequenceNum));
    }

    @Override
    public DatasetDefinition getDataset(int id)
    {
        return StudyManager.getInstance().getDatasetDefinition(this, id);
    }

    @Override
    public DatasetDefinition getDatasetByName(String name)
    {
        return StudyManager.getInstance().getDatasetDefinitionByName(this, name);
    }

    @Override
    public DatasetDefinition getDatasetByLabel(String label)
    {
        return StudyManager.getInstance().getDatasetDefinitionByLabel(this, label);
    }

    @Override
    @Transient
    public List<DatasetDefinition> getDatasets()
    {
        return StudyManager.getInstance().getDatasetDefinitions(this);
    }

    @Override
    public List<DatasetDefinition> getDatasetsByType(String... types)
    {
        return StudyManager.getInstance().getDatasetDefinitions(this, null, types);
    }

    @Transient
    public Set<PropertyDescriptor> getSharedProperties()
    {
        return StudyManager.getInstance().getSharedProperties(this);
    }

    @Override
    @Transient
    public List<LocationImpl> getLocations()
    {
        return LocationManager.get().getLocations(getContainer());
    }

    @Override
    public List<CohortImpl> getCohorts(User user)
    {
        return StudyManager.getInstance().getCohorts(getContainer(), user);
    }

    @Override
    public boolean hasGWTStudyDesign(Container c, User user)
    {
        StudyDesignManager manager = StudyDesignManager.get();
        StudyDesignInfo info = manager.getDesignForStudy(this);
        if (info != null)
        {
            // consider the XML study design non-empty if we have an immunogen, adjuvant, immunization timepoint, etc.
            GWTStudyDefinition def = manager.getGWTStudyDefinition(user, c, info);
            return def != null && (
                    def.getImmunogens().size() > 0 || def.getAdjuvants().size() > 0 ||
                    def.getImmunizationSchedule().getTimepoints().size() > 0 ||
                    def.getAssaySchedule().getAssays().size() > 0 || def.getAssaySchedule().getTimepoints().size() > 0
                );
        }

        return false;
    }

    @Override
    public List<AssaySpecimenConfigImpl> getAssaySpecimenConfigs(String sortCol)
    {
        return StudyManager.getInstance().getAssaySpecimenConfigs(getContainer(), sortCol);
    }

    @Override
    @Transient
    public List<VisitImpl> getVisitsForAssaySchedule()
    {
        return StudyManager.getInstance().getVisitsForAssaySchedule(getContainer());
    }

    @Override
    public List<ProductImpl> getStudyProducts(User user, String role)
    {
        return TreatmentManager.getInstance().getStudyProducts(getContainer(), user, role, null);
    }

    @Override
    public List<TreatmentImpl> getStudyTreatments(User user)
    {
        return TreatmentManager.getInstance().getStudyTreatments(getContainer(), user);
    }

    @Override
    public List<TreatmentVisitMapImpl> getStudyTreatmentVisitMap(Container container, @Nullable Integer cohortId)
    {
        return TreatmentManager.getInstance().getStudyTreatmentVisitMap(container, cohortId);
    }

    @Override
    @Transient
    public List<VisitImpl> getVisitsForTreatmentSchedule()
    {
        return TreatmentManager.getInstance().getVisitsForTreatmentSchedule(getContainer());
    }

    @Override
    public List<ParticipantCategoryImpl> getParticipantCategories(User user)
    {
        return ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), user);
    }

    @Override
    public Object getPrimaryKey()
    {
        return getContainer().getId();
    }

    public int getRowId()
    {
        return -1;
    }

    @Override
    public void savePolicy(MutableSecurityPolicy policy, User user)
    {
        SecurityPolicy existingPolicy = SecurityPolicyManager.getPolicy(this);
        if (!existingPolicy.getAssignments().equals(policy.getAssignments()))
        {
            String comment = getPolicyChangeSummary(policy, existingPolicy, "Group dataset access type changed.", "Previous access", "New access");
            StudyService.get().addStudyAuditEvent(getContainer(), user, comment);
        }

        super.savePolicy(policy, user);
        StudyManager.getInstance().scrubDatasetAcls(this, policy);
    }

    @Override
    protected boolean supportsPolicyUpdate()
    {
        return true;
    }

    @Override
    public TimepointType getTimepointType()
    {
        return _timepointType;
    }

    public void setTimepointType(TimepointType timepointType)
    {
        verifyMutability();
        _timepointType = timepointType;
    }

    public SecurityType getSecurityType()
    {
        return _securityType;
    }

    public void setSecurityType(SecurityType securityType)
    {
        verifyMutability();
        if (securityType == null)
            throw new IllegalArgumentException("securityType cannot be null");
        _securityType = securityType;
    }

    @Override
    public Date getStartDate()
    {
        return _startDate;
    }

    public void setStartDate(Date startDate)
    {
        verifyMutability();
        _startDate = startDate;
    }

    @Override
    @Nullable
    public String getParticipantCohortProperty()
    {
        if (null != _participantCohortProperty)
            return _participantCohortProperty;
        Study shared = StudyManager.getInstance().getSharedStudy(this);
        if (null != shared)
            return shared.getParticipantCohortProperty();
        return null;
    }

    public void setParticipantCohortProperty(@Nullable String participantCohortProperty)
    {
        _participantCohortProperty = participantCohortProperty;
    }

    @Override
    @Nullable
    public Integer getParticipantCohortDatasetId()
    {
        if (null != _participantCohortDatasetId)
            return _participantCohortDatasetId;
        Study shared = StudyManager.getInstance().getSharedStudy(this);
        if (null != shared)
            return shared.getParticipantCohortDatasetId();
        return null;
    }

    public void setParticipantCohortDatasetId(@Nullable Integer participantCohortDatasetId)
    {
        _participantCohortDatasetId = participantCohortDatasetId;
    }

    @Override
    public boolean isManualCohortAssignment()
    {
        return _manualCohortAssignment;
    }

    public void setManualCohortAssignment(boolean manualCohortAssignment)
    {
        _manualCohortAssignment = manualCohortAssignment;
    }

    @Override
    public String getDomainURIPrefix()
    {
        return DOMAIN_URI_PREFIX;
    }

    @Override
    protected boolean getUseSharedProjectDomain()
    {
        return true;
    }

    @Override
    public void initLsid()
    {
        Lsid lsid = new Lsid(getDomainURIPrefix(), "Folder-" + getContainer().getRowId(), String.valueOf(getContainer().getRowId()));
        setLsid(lsid.toString());
    }

    @Override
    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        verifyMutability();
        _lsid = lsid;
    }

    public Integer getDefaultPipelineQCState()
    {
        return _defaultPipelineQCState;
    }

    public void setDefaultPipelineQCState(Integer defaultPipelineQCState)
    {
        _defaultPipelineQCState = defaultPipelineQCState;
    }

    public Integer getDefaultPublishDataQCState()
    {
        return _defaultPublishDataQCState;
    }

    public void setDefaultPublishDataQCState(Integer defaultPublishDataQCState)
    {
        _defaultPublishDataQCState = defaultPublishDataQCState;
    }

    public Integer getDefaultDirectEntryQCState()
    {
        return _defaultDirectEntryQCState;
    }

    public void setDefaultDirectEntryQCState(Integer defaultDirectEntryQCState)
    {
        _defaultDirectEntryQCState = defaultDirectEntryQCState;
    }

    /**
     * Used to determine which QC states should be shown when viewing datasets
     */
    public boolean isShowPrivateDataByDefault()
    {
        return _showPrivateDataByDefault;
    }

    public void setShowPrivateDataByDefault(boolean showPrivateDataByDefault)
    {
        _showPrivateDataByDefault = showPrivateDataByDefault;
    }

    public boolean isBlankQCStatePublic()
    {
        return _blankQCStatePublic;
    }

    public void setBlankQCStatePublic(boolean blankQCStatePublic)
    {
        _blankQCStatePublic = blankQCStatePublic;
    }

    /**
     * Used to determine whether records without an assigned QC state are considered 'public' data
     */

    public int getNumExtendedProperties(User user)
    {
        StudyQuerySchema schema = StudyQuerySchema.createSchema(this, user);
        String domainURI = DOMAIN_INFO.getDomainURI(schema.getContainer());
        Domain domain = PropertyService.get().getDomain(schema.getContainer(), domainURI);

        if (domain == null)
            return 0;

        return domain.getProperties().size();
    }

    @Override
    public boolean isAdvancedCohorts()
    {
        return _advancedCohorts;
    }

    public void setAdvancedCohorts(boolean advancedCohorts)
    {
        _advancedCohorts = advancedCohorts;
    }

    public Integer getParticipantCommentDatasetId()
    {
        return _participantCommentDatasetId;
    }

    public void setParticipantCommentDatasetId(Integer participantCommentDatasetId)
    {
        _participantCommentDatasetId = participantCommentDatasetId;
    }

    @Override
    public String getParticipantCommentProperty()
    {
        return _participantCommentProperty;
    }

    public void setParticipantCommentProperty(String participantCommentProperty)
    {
        _participantCommentProperty = participantCommentProperty;
    }

    public Integer getParticipantVisitCommentDatasetId()
    {
        return _participantVisitCommentDatasetId;
    }

    public void setParticipantVisitCommentDatasetId(Integer participantVisitCommentDatasetId)
    {
        _participantVisitCommentDatasetId = participantVisitCommentDatasetId;
    }

    @Override
    public String getParticipantVisitCommentProperty()
    {
        return _participantVisitCommentProperty;
    }

    public void setParticipantVisitCommentProperty(String participantVisitCommentProperty)
    {
        _participantVisitCommentProperty = participantVisitCommentProperty;
    }

    @Override
    public String getSubjectNounSingular()
    {
        return _subjectNounSingular;
    }

    public void setSubjectNounSingular(String subjectNounSingular)
    {
        _subjectNounSingular = subjectNounSingular;
    }

    @Override
    public String getSubjectNounPlural()
    {
        return _subjectNounPlural;
    }

    public void setSubjectNounPlural(String subjectNounPlural)
    {
        _subjectNounPlural = subjectNounPlural;
    }

    @Override
    public String getSubjectColumnName()
    {
        return _subjectColumnName;
    }

    public void setSubjectColumnName(String subjectColumnName)
    {
        _subjectColumnName = ColumnInfo.legalNameFromName(subjectColumnName);
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    @Override
    public String getDescriptionRendererType()
    {
        return _descriptionRendererType;
    }

    public void setDescriptionRendererType(String descriptionRendererType)
    {
        _descriptionRendererType = descriptionRendererType;
    }

    @Override
    @Transient
    public HtmlString getDescriptionHtml()
    {
        String description = getDescription();

        if (description == null || description.length() == 0)
        {
            long count = StudyManager.getInstance().getParticipantCount(this);
            String subjectNoun = (count == 1 ? this.getSubjectNounSingular() : this.getSubjectNounPlural());
            TimepointType timepointType = getTimepointType();
            // TODO: Use HtmlStringBuilder, DOM, or JSP to assemble this
            String html = PageFlowUtil.filter((getLabel() == null ? "This study" : getLabel()) + " tracks data in ")
                + "<a href=\"" + new ActionURL(StudyController.DatasetsAction.class, getContainer()) + "\">"
                + getDatasets().size() + " dataset" + (getDatasets().size() == 1 ? "" : "s") + "</a>" +
                (null != timepointType ? (timepointType != TimepointType.CONTINUOUS ? (PageFlowUtil.filter(" over " + getVisits(Visit.Order.DISPLAY).size() + " "
                + (timepointType.isVisitBased() ? "visit" : "time point") + (getVisits(Visit.Order.DISPLAY).size() == 1 ? "" : "s"))) : "" ) : "")
                + ". Data is present for " + PageFlowUtil.filter(DecimalFormat.getNumberInstance().format(count)) + " " + PageFlowUtil.filter(subjectNoun) + ".";
            return HtmlString.unsafe(html);
        }
        else
        {
            WikiRenderingService wrs = WikiRenderingService.get();
            return wrs.getFormattedHtml(getDescriptionWikiRendererType(), description);
        }
    }

    @Transient
    public WikiRendererType getDescriptionWikiRendererType()
    {
        try
        {
            return WikiRendererType.valueOf(_descriptionRendererType);
        }
        catch (IllegalArgumentException x)
        {
            return WikiRendererType.TEXT_WITH_LINKS;
        }
    }

    public String getProtocolDocumentEntityId()
    {
        return _protocolDocumentEntityId;
    }

    public void setProtocolDocumentEntityId(String protocolDocumentEntityId)
    {
        _protocolDocumentEntityId = protocolDocumentEntityId;
    }

    @Override
    public void attachProtocolDocument(List<AttachmentFile> files, User user) throws IOException
    {
        AttachmentService.get().addAttachments(getProtocolDocumentAttachmentParent(), files, user);
        SearchService ss = SearchService.get();
        if (null != ss)
            StudyManager._enumerateProtocolDocuments(ss.defaultTask(), this);
    }

    @Override
    public String getInvestigator()
    {
        return _investigator;
    }

    public void setInvestigator(String investigator)
    {
        _investigator = investigator;
    }

    @Override
    public String getGrant()
    {
        return _grant;
    }

    public void setGrant(String grant)
    {
        _grant = grant;
    }

    @Override
    @Transient
    public List<Attachment> getProtocolDocuments()
    {
        return new ArrayList<>(AttachmentService.get().getAttachments(getProtocolDocumentAttachmentParent()));
    }

    public String getSourceStudyContainerId()
    {
        return _sourceStudyContainerId;
    }

    public void setSourceStudyContainerId(String sourceStudyContainerId)
    {
        _sourceStudyContainerId = sourceStudyContainerId;
    }

    @Override
    public boolean isAncillaryStudy()
    {
        // prior to 15.1 we couldn't actually correctly distinquish between publish and ancillary studies,
        // because of this we have to have the second check for if the study has a source study but isn't of type publish
        return StudySnapshotType.ancillary.equals(getStudySnapshotType())
            || (hasSourceStudy() && !StudySnapshotType.publish.equals(getStudySnapshotType()));
    }

    @Override
    public boolean hasSourceStudy()
    {
        return getSourceStudy() != null;
    }

    @Override
    public boolean isSnapshotStudy()
    {
        // StudySnapshot includes both Ancillary and Published Studies (as of 12.3)
        return getStudySnapshot() != null;
    }

    @Override
    @Nullable
    @Transient
    public StudyImpl getSourceStudy()
    {
        if (getSourceStudyContainerId() == null)
            return null;
        Container sourceContainer = ContainerManager.getForId(getSourceStudyContainerId());
        if (sourceContainer == null)
            return null;
        return StudyManager.getInstance().getStudy(sourceContainer);
    }

    @Override
    public StudySnapshotType getStudySnapshotType()
    {
        if (_studySnapshotType == null && getStudySnapshot() != null)
        {
            StudySnapshot snapshot = StudyManager.getInstance().getStudySnapshot(getStudySnapshot());
            if (snapshot != null)
            {
                _studySnapshotType = snapshot.getType();
            }

            // prior to 15.1, we didn't store the snapshot type in the SnapshotSettings, so use the "guess type" method
            // NOTE: this is not reliable because we can't determine if it is a Specimen Study and a Publish Study
            //       can also have a source study if it was configured with data refresh = Manual
            if (_studySnapshotType == null)
            {
                _studySnapshotType = getSourceStudy() != null ? StudySnapshotType.ancillary : StudySnapshotType.publish;
            }
        }

        return _studySnapshotType;
    }

    @Override
    public boolean isEmptyStudy()
    {
        List<DatasetDefinition> datasets = getDatasets();
        List<VisitImpl> visits = getVisits(Visit.Order.DISPLAY);
        return visits.size() < 1 && datasets.size() < 1;
    }

    @Override
    public void removeProtocolDocument(String name, User user)
    {
        AttachmentService.get().deleteAttachment(getProtocolDocumentAttachmentParent(), name, user);
        SearchService ss = SearchService.get();
        if (null != ss)
            ss.deleteResource("attachment:/" + _protocolDocumentEntityId + "/" + PageFlowUtil.encode(name));
    }

    @Override
    public String getAlternateIdPrefix()
    {
        return _alternateIdPrefix;
    }

    @Override
    public int getAlternateIdDigits()
    {
        return _alternateIdDigits;
    }

    public void setAlternateIdPrefix(String alternateIdPrefix)
    {
        verifyMutability();
        _alternateIdPrefix = alternateIdPrefix;
    }

    public void setAlternateIdDigits(int alternateIdDigits)
    {
        verifyMutability();
        _alternateIdDigits = alternateIdDigits;
    }

    @Override
    public boolean isAllowReqLocRepository()
    {
        return _allowReqLocRepository;
    }

    public void setAllowReqLocRepository(boolean allowReqLocRepository)
    {
        verifyMutability();
        _allowReqLocRepository = allowReqLocRepository;
    }

    @Override
    public boolean isAllowReqLocClinic()
    {
        return _allowReqLocClinic;
    }

    public void setAllowReqLocClinic(boolean allowReqLocClinic)
    {
        verifyMutability();
        _allowReqLocClinic = allowReqLocClinic;
    }

    @Override
    public boolean isAllowReqLocSal()
    {
        return _allowReqLocSal;
    }

    public void setAllowReqLocSal(boolean allowReqLocSal)
    {
        verifyMutability();
        _allowReqLocSal = allowReqLocSal;
    }

    @Override
    public boolean isAllowReqLocEndpoint()
    {
        return _allowReqLocEndpoint;
    }

    public void setAllowReqLocEndpoint(boolean allowReqLocEndpoint)
    {
        verifyMutability();
        _allowReqLocEndpoint = allowReqLocEndpoint;
    }

    public Integer getLastSpecimenRequest()
    {
        return _lastSpecimenRequest;
    }

    public void setLastSpecimenRequest(Integer lastSpecimenRequest)
    {
        _lastSpecimenRequest = lastSpecimenRequest;
    }

    public Date getEndDate()
    {
        return _endDate;
    }

    public void setEndDate(Date endDate)
    {
        verifyMutability();
        _endDate = endDate;
    }

    public String getSpecies()
    {
        return _species;
    }

    public void setSpecies(String species)
    {
        _species = species;
    }

    @Override
    public String getAssayPlan()
    {
        return _assayPlan;
    }

    public void setAssayPlan(String assayPlan)
    {
        _assayPlan = assayPlan;
    }


    @Transient
    public AttachmentParent getProtocolDocumentAttachmentParent()
    {
        return new ProtocolDocumentAttachmentParent(this);
    }


    @Override
    @Transient
    public String getSearchDisplayTitle()
    {
        return "Study -- " + getLabel();
    }


    private static final CaseInsensitiveHashSet _skipProperties = new CaseInsensitiveHashSet();

    static
    {
        _skipProperties.addAll("lsid", "timepointtype", "description", "descriptionrenderertype", "SubjectNounPlural", "SubjectColumnName", "container");
    }

    @Override
    @Transient
    public String getSearchKeywords()
    {
        StringBuilder sb = new StringBuilder();

        StudyQuerySchema sqs = StudyQuerySchema.createSchema(this, User.getSearchUser(), RoleManager.getRole(ReaderRole.class));
        TableInfo sp = sqs.getTable("StudyProperties");
        if (null != sp)
        {
            List<ColumnInfo> cols = sp.getColumns();
            try (Results results = QueryService.get().select(sp, cols, null, null))
            {
                if (results.next())
                {
                    for (ColumnInfo col : cols)
                    {
                        if (_skipProperties.contains(col.getName()))
                            continue;
                        if (col.getJdbcType() != JdbcType.VARCHAR)
                            continue;
                        appendKeyword(sb, results.getString(col.getFieldKey()));
                    }
                }
            }
            catch (SQLException e)
            {
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }

        appendKeyword(sb, getLabel());
        appendKeyword(sb, getInvestigator());

        return sb.toString();
    }


    @Override
    @Transient
    public String getSearchBody()
    {
        Container c = getContainer();
        StringBuilder sb = new StringBuilder();

        if (c.isProject())
            appendKeyword(sb, "Study Project " + c.getName());
        else
            appendKeyword(sb, "Study Folder " + c.getName() + " in Project " + c.getProject().getName());

        appendKeyword(sb, getLabel());
        appendKeyword(sb, getInvestigator());

        // Render and parse description
        HtmlString descriptionHtml = getDescriptionHtml();

        try
        {
            appendKeyword(sb, new HTMLContentExtractor.GenericHTMLExtractor(descriptionHtml.toString()).extract());
        }
        catch (IOException e)
        {
            LOG.error("Can't extract text from study description", e);
        }

        for (DatasetDefinition dataset : getDatasets())
        {
            appendKeyword(sb, dataset.getName());
            appendKeyword(sb, dataset.getLabel());
            appendKeyword(sb, dataset.getDescription());
        }

        /*
           Per Sarah, leave cohort labels out for now... to re-enable, uncomment and special case
           the search user in getCohorts()
        for (Cohort cohort : getCohorts(User.getSearchUser()))
            appendKeyword(sb, cohort.getLabel());
        */

        for (Location location : getLocations())
            appendKeyword(sb, location.getLabel());

        return sb.toString();
    }


    private void appendKeyword(StringBuilder sb, String s)
    {
        if (!StringUtils.isBlank(s))
        {
            sb.append(s);
            sb.append(" ");
        }
    }

    @Override
    public int getDefaultTimepointDuration()
    {
        return _defaultTimepointDuration;
    }

    public void setDefaultTimepointDuration(int defaultTimepointDuration)
    {
        _defaultTimepointDuration = defaultTimepointDuration;
    }

    public Integer getStudySnapshot()
    {
        return _studySnapshot;
    }

    public void setStudySnapshot(Integer studySnapshot)
    {
        _studySnapshot = studySnapshot;
    }

    public Date getLastSpecimenLoad()
    {
        return _lastSpecimenLoad;
    }

    public void setLastSpecimenLoad(Date lastSpecimenLoad)
    {
        _lastSpecimenLoad = lastSpecimenLoad;
    }

    public Integer getParticipantAliasDatasetId()
    {
        return _participantAliasDatasetId;
    }

    public void setParticipantAliasDatasetId(Integer participantAliasDatasetId)
    {
        _participantAliasDatasetId = participantAliasDatasetId;
    }

    public String getParticipantAliasSourceProperty()
    {
        return _participantAliasSourceProperty;
    }

    public void setParticipantAliasSourceProperty(String participantAliasSourceProperty)
    {
        _participantAliasSourceProperty = participantAliasSourceProperty;
    }

    public String getParticipantAliasProperty()
    {
        return _participantAliasProperty;
    }

    public void setParticipantAliasProperty(String participantAliasProperty)
    {
        _participantAliasProperty = participantAliasProperty;
    }

    @Override
    @NotNull
    public Boolean getShareDatasetDefinitions()
    {
        return _shareDatasetDefinitions && getContainer().isProject();
    }


    public void setShareDatasetDefinitions(Boolean shareDatasetDefinitions)
    {
        _shareDatasetDefinitions = Boolean.TRUE == shareDatasetDefinitions;
    }


    @Override
    @NotNull
    public Boolean getShareVisitDefinitions()
    {
        return _shareVisitDefinitions && getContainer().isProject();
    }


    public void setShareVisitDefinitions(Boolean shareVisitDefinitions)
    {
        _shareVisitDefinitions = Boolean.TRUE == shareVisitDefinitions;
    }


    @Override
    public boolean isDataspaceStudy()
    {
        return getContainer().isProject() && (_shareDatasetDefinitions || _shareVisitDefinitions);
    }

    @Override
    public boolean allowExport(User user)
    {
        Container c = getContainer();
        boolean userHasPerm = c.hasPermission(user, AdminPermission.class);
        boolean allowForNonDataspace = !c.isDataspace();
        boolean allowForDataspace = c.isProject() && c.isDataspace() && getTimepointType() == TimepointType.VISIT
                                        && getShareDatasetDefinitions() && getShareVisitDefinitions();
        
        return userHasPerm && (allowForNonDataspace || allowForDataspace);
    }

    @Override
    public Visit getVisit(String participantID, Double visitID, Date date, boolean returnPotentialTimepoints)
    {
        Double sequenceNum = null;
        if (visitID != null && getTimepointType().isVisitBased())
        {
            sequenceNum = visitID;
        }
        if (participantID != null && date != null && !getTimepointType().isVisitBased())
        {
            // Translate the date into a sequencenum based on the participant's start date
            Participant participant = StudyManager.getInstance().getParticipant(this, participantID);
            Calendar startCal = new GregorianCalendar();
            if (participant != null && participant.getStartDate() != null)
            {
                startCal.setTime(participant.getStartDate());
            }
            else
            {
                Date studyStartDate = getStartDate();
                if (studyStartDate != null)
                {
                    startCal.setTime(getStartDate());
                }
            }
            Calendar timepointCal = new GregorianCalendar();
            timepointCal.setTime(date);
            sequenceNum = (double) daysBetween(startCal, timepointCal);
        }

        if (sequenceNum != null)
        {
            BigDecimal sequenceNumBD = VisitImpl.getSequenceNum(sequenceNum);
            // Look up the visit if we have a sequencenum
            VisitImpl result = StudyManager.getInstance().getVisitForSequence(this, sequenceNumBD);
            if (result == null && returnPotentialTimepoints)
            {
                result = StudyManager.getInstance().ensureVisit(this, null, sequenceNumBD, null, false);
            }
            return result;
        }

        return null;
    }

    /**
     * Implementation based on posting at http://tripoverit.blogspot.com/2007/07/java-calculate-difference-between-two.html
     */
    public static int daysBetween(Calendar startDate, Calendar endDate)
    {
        boolean flipped = startDate.after(endDate);
        if (flipped)
        {
            Calendar temp = startDate;
            startDate = endDate;
            endDate = temp;
        }
        Calendar date = (Calendar) startDate.clone();

        // Calculate the difference in terms of days
        long diffInMillis = endDate.getTimeInMillis() - startDate.getTimeInMillis();
        int daysBetween = (int)(diffInMillis / TimeUnit.DAYS.toMillis(1));

        if (daysBetween > 0)
        {
            // Round down to ensure consistent results with original implementation, which added one day at a time and
            // was a performance bottleneck when the supplied dates were many years apart
            daysBetween--;
            date.add(Calendar.DAY_OF_MONTH, daysBetween);
        }

        // Now iterate for one or possibly two more days until we go beyond the original date
        while (date.before(endDate))
        {
            date.add(Calendar.DAY_OF_MONTH, 1);
            daysBetween++;
        }
        return daysBetween * (flipped ? -1 : 1);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StudyImpl study = (StudyImpl) o;

        return !(getContainer() != null ? !getContainer().equals(study.getContainer()) : study.getContainer() != null);
    }

    @Override
    public int hashCode()
    {
        return getContainer() != null ? getContainer().hashCode() : 0;
    }

    public static class DateMathTestCase extends Assert
    {
        @Test
        public void testDayInterval()
        {
            // The behavior here is questionable - arguments that differ by one
            // millisecond will return a difference of 1 day (plus or minus), but keeping as-is during the optimization
            // to not increment by one day for the whole interval
            Calendar jan1 = new GregorianCalendar();
            jan1.setTimeInMillis(DateUtil.parseISODateTime("2020-01-01"));
            Calendar c = new GregorianCalendar();
            c.setTimeInMillis(jan1.getTimeInMillis());
            assertEquals(0, daysBetween(jan1, c));

            c.setTimeInMillis(jan1.getTimeInMillis() + 1);
            assertEquals(1, daysBetween(jan1, c));

            c.add(Calendar.DATE, 1);
            assertEquals(2, daysBetween(jan1, c));

            c.setTimeInMillis(jan1.getTimeInMillis() - 1);
            assertEquals(-1, daysBetween(jan1, c));
            c.add(Calendar.DATE, -1);
            assertEquals(-2, daysBetween(jan1, c));
        }
    }


    @TestWhen(TestWhen.When.BVT)
    public static class ProtocolDocumentTestCase extends Assert
    {
        Study _testStudy = null;
        TestContext _context = null;

        //        @BeforeClass
        public void createStudy()
        {
            _context = TestContext.get();
            Container junit = JunitUtil.getTestContainer();

            String name = GUID.makeHash();
            Container c = ContainerManager.createContainer(junit, name);
            StudyImpl s = new StudyImpl(c, "Junit Study");
            s.setTimepointType(TimepointType.DATE);
            s.setStartDate(new Date(DateUtil.parseISODateTime("2001-01-01")));
            s.setSubjectColumnName("SubjectID");
            s.setSubjectNounPlural("Subjects");
            s.setSubjectNounSingular("Subject");
            s.setSecurityType(SecurityType.BASIC_WRITE);
            s.setStartDate(new Date(DateUtil.parseDateTime(c, "1 Jan 2000")));
            _testStudy = StudyManager.getInstance().createStudy(_context.getUser(), s);

            MvUtil.assignMvIndicators(c,
                    new String[]{"X", "Y", "Z"},
                    new String[]{"XXX", "YYY", "ZZZ"});

        }

        //        @AfterClass
        public void tearDown()
        {
            if (null != _testStudy)
            {
                ContainerManager.delete(_testStudy.getContainer(), _context.getUser());
            }
        }

        @Test
        public void test() throws Throwable
        {
            try
            {
                createStudy();
                _testAttachProtocolDoc(_testStudy);
                _testDeleteProtocolDoc(_testStudy);
            }
            finally
            {
                tearDown();
            }
        }

        public void _testAttachProtocolDoc(Study testStudy) throws SQLException, IOException
        {
            List<Attachment> attachedFiles = testStudy.getProtocolDocuments();
            assertEquals("Expected 0 attached documents", 0, attachedFiles.size());

            AttachmentFile file = new FileAttachmentFile(JunitUtil.getSampleData(ModuleLoader.getInstance().getModule(StudyModule.class), "study/Protocol.txt"));
            testStudy.attachProtocolDocument(Collections.singletonList(file), _context.getUser());
            attachedFiles = testStudy.getProtocolDocuments();
            assertEquals("Expected 1 attached document", 1, attachedFiles.size());
            assertTrue("Expected filename to be \"Protocol.txt\", but it was \"" + attachedFiles.get(0).getName() + "\"", attachedFiles.get(0).getName().equals("Protocol.txt"));
        }

        public void _testDeleteProtocolDoc(Study testStudy) throws SQLException, IOException
        {
            List<Attachment> attachedFiles = testStudy.getProtocolDocuments();
            assertEquals("Expected 1 attached document", 1, attachedFiles.size());

            testStudy.removeProtocolDocument("Protocol.txt", _context.getUser());
            attachedFiles = testStudy.getProtocolDocuments();
            assertEquals("Expected 0 attached documents", 0, attachedFiles.size());
        }
    }


    static
    {
        ObjectFactory.Registry.register(StudyImpl.class, new _BeanObjectFactory());
    }

    private static class _BeanObjectFactory extends BeanObjectFactory<StudyImpl>
    {
        _BeanObjectFactory()
        {
            super(StudyImpl.class);
            assert _readableProperties.contains("emptyStudy");
            _readableProperties.remove("emptyStudy");
        }
    }
}
