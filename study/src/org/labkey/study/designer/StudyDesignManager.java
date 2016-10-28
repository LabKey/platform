/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.study.designer;

import gwt.client.org.labkey.study.designer.client.model.GWTAssayDefinition;
import gwt.client.org.labkey.study.designer.client.model.GWTAssayNote;
import gwt.client.org.labkey.study.designer.client.model.GWTAssaySchedule;
import gwt.client.org.labkey.study.designer.client.model.GWTCohort;
import gwt.client.org.labkey.study.designer.client.model.GWTSampleMeasure;
import gwt.client.org.labkey.study.designer.client.model.GWTStudyDefinition;
import gwt.client.org.labkey.study.designer.client.model.GWTTimepoint;
import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.study.StudyFolderType;
import org.labkey.study.StudyModule;
import org.labkey.study.StudySchema;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.controllers.designer.DesignerController;
import org.labkey.study.importer.SimpleSpecimenImporter;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: Mark Igra
 * Date: Feb 12, 2007
 * Time: 5:06:04 PM

 */
public class StudyDesignManager
{
    private static final String STUDY_DESIGN_TABLE_NAME = "StudyDesign";
    private static final String STUDY_VERSION_TABLE_NAME = "StudyDesignVersion";
    private static final StudyDesignManager _instance = new StudyDesignManager();

    public static StudyDesignManager get()
    {
        return _instance;
    }

    public DbSchema getSchema()
    {
        return StudySchema.getInstance().getSchema();
    }

    public TableInfo getStudyDesignTable()
    {
        return getSchema().getTable(STUDY_DESIGN_TABLE_NAME);
    }

    public TableInfo getStudyVersionTable()
    {
        return getSchema().getTable(STUDY_VERSION_TABLE_NAME);
    }

    public StudyDesignInfo getStudyDesign(Container c, int studyId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause("(Container = ? OR SourceContainer = ?) AND StudyId= ?", new Object[] {c.getId(), c.getId(), studyId} , FieldKey.fromParts("Container"), FieldKey.fromParts("SourceContainer"), FieldKey.fromParts("StudyId"));

        return new TableSelector(getStudyDesignTable(), filter, null).getObject(StudyDesignInfo.class);
    }

    public StudyDesignInfo[] getStudyDesigns(Container c)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause("(Container = ? OR SourceContainer = ?)", new Object[] {c, c} , FieldKey.fromParts("Container"), FieldKey.fromParts("SourceContainer"));

        return new TableSelector(getStudyDesignTable(), filter, null).getArray(StudyDesignInfo.class);
    }

    public StudyDesignInfo[] getStudyDesignsForAllFolders(User u, Container root)
    {
        SimpleFilter filter = new SimpleFilter();
        ContainerFilter cf = new ContainerFilter.CurrentAndSubfolders(u);
        filter.addClause(cf.createFilterClause(getSchema(), FieldKey.fromParts("Container"), root));

        return new TableSelector(getStudyDesignTable(), filter, null).getArray(StudyDesignInfo.class);
    }


    public StudyDesignInfo moveStudyDesign(User user, StudyDesignInfo design, Container newContainer)
    {
        Container oldContainer = design.getContainer();
        design.setContainer(newContainer);
        Table.update(user, getStudyDesignTable(), design, design.getStudyId());
        String sql = "UPDATE " + getStudyVersionTable() + " SET Container = ? WHERE Container = ? AND StudyId = ?";
        new SqlExecutor(getSchema()).execute(sql, newContainer, oldContainer, design.getStudyId());
        
        return design;
    }

    /**
     * Copies a study design. The new design will have a revisionid of 1 and will only copy the latest revision
     * @param user
     * @param source StudyDesign to copy
     * @param newContainer May be same as old container if name is different
     * @param newName Label for this study design
     * @return
     */
    public StudyDesignInfo copyStudyDesign(User user, StudyDesignInfo source, Container newContainer, String newName) throws SaveException
    {
        StudyDesignInfo dest = new StudyDesignInfo();
        dest.setLabel(newName);
        dest.setContainer(newContainer);
        dest.setStudyId(0);
        dest.setDraftRevision(0);
        dest.setPublicRevision(1);
        dest = insertStudyDesign(user, dest);

        StudyDesignVersion version = getStudyDesignVersion(source.getContainer(), source.getStudyId());
        version.setStudyId(dest.getStudyId());
        version.setLabel(newName);
        version.setRevision(0);
        version.setContainer(newContainer);
        saveStudyDesign(user, newContainer, version);

        return dest;
    }

    public StudyDesignInfo getStudyDesign(Container c, String name)
    {
        return getStudyDesign(c, name, false);
    }

    public StudyDesignInfo getStudyDesign(Container c, String name, boolean checkSourceContainer)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("Label"), name);
        StudyDesignInfo[] designs = new TableSelector(getStudyDesignTable(), filter, null).getArray(StudyDesignInfo.class);
        if (designs.length > 0)
            return designs[0];

        // Issue 18393: if the study design has been used to create a study folder, also check the sourcecontainer field
        if (checkSourceContainer)
        {
            filter = new SimpleFilter(FieldKey.fromParts("SourceContainer"), c);
            filter.addCondition(FieldKey.fromParts("Label"), name);
            designs = new TableSelector(getStudyDesignTable(), filter, null).getArray(StudyDesignInfo.class);
            if (designs.length > 0)
                return designs[0];
        }

        return null;
    }

    public GWTStudyDefinition getGWTStudyDefinition(User user, Container c, StudyDesignInfo info)
    {
        StudyDesignVersion version = getStudyDesignVersion(info.getContainer(), info.getStudyId());

        GWTStudyDefinition def = XMLSerializer.fromXML(version.getXML(), user, info.getContainer());
        mergeStudyProperties(def, info);
        def.setCavdStudyId(info.getStudyId());

        return def;
    }

    public GWTStudyDefinition mergeStudyProperties(GWTStudyDefinition def, StudyDesignInfo info)
    {
        if (!info.isActive())
            return def;

        if (info.isActive())
        {
            Study study = StudyManager.getInstance().getStudy(info.getContainer());
            if (null != study.getDescription())
                def.setDescription(study.getDescription());
            if (null != study.getInvestigator())
                def.setInvestigator(study.getInvestigator());
            if (null != study.getGrant())
                def.setGrant(study.getGrant());
        }

        return def;
    }

    public StudyDesignInfo insertStudyDesign(User user, StudyDesignInfo info)
    {
        return Table.insert(user, getStudyDesignTable(), info);
    }

    public StudyDesignVersion[] getStudyDesignVersions(Container c, int studyId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("studyId"), studyId);

        return new TableSelector(getStudyVersionTable(), filter, new Sort("Revision")).getArray(StudyDesignVersion.class);
    }
    
    public StudyDesignVersion getStudyDesignVersion(Container c, int studyId, int versionId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("studyId"), studyId);
        filter.addCondition(FieldKey.fromParts("revision"), versionId);

        return new TableSelector(getStudyVersionTable(), filter, null).getObject(StudyDesignVersion.class);
    }

    /**
     * Return the latest version of the given study.
     */
    public StudyDesignVersion getStudyDesignVersion(Container c, int studyId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("studyId"), studyId);
        filter.addWhereClause("revision = (SELECT MAX(revision) FROM " + getStudyVersionTable().toString() + " WHERE studyid=?)", new Object[] {studyId}, FieldKey.fromParts("revision"), FieldKey.fromParts("studyid"));

        return new TableSelector(getStudyVersionTable(), filter, null).getObject(StudyDesignVersion.class);
    }

    /**
     * returns null if no revision can be found for the particular study and container
     */
    public Integer getLatestRevisionNumber(Container c, int studyId)
    {
        String sql = "SELECT MAX(Revision) FROM " + getStudyVersionTable().toString() + " WHERE Container = ? AND StudyId = ?";
        return new SqlSelector(getSchema(), sql, c, studyId).getObject(Integer.class);
    }

    public StudyDesignVersion saveStudyDesign(User user, Container container, StudyDesignVersion version) throws SaveException
    {
        int studyDesignId = version.getStudyId();
        StudyDesignInfo designInfo;

        // Check if there is a name conflict
        StudyDesignInfo existingStudyDesign = getStudyDesign(container, version.getLabel(), true);
        if (existingStudyDesign != null && (0 == studyDesignId || existingStudyDesign.getStudyId() != studyDesignId))
        {
            throw new SaveException("The name '" + version.getLabel() + "' is already in use");
        }

        try (DbScope.Transaction transaction = getSchema().getScope().ensureTransaction())
        {
            if (0 == studyDesignId)
            {
                designInfo = new StudyDesignInfo();
                designInfo.setLabel(version.getLabel());
                designInfo.setContainer(container);
                designInfo = insertStudyDesign(user, designInfo);
                version.setStudyId(designInfo.getStudyId());
            }
            else
                designInfo = getStudyDesign(container, studyDesignId);

            int revision = designInfo.getPublicRevision() + 1;
            version.setRevision(revision);
            version.setContainer(designInfo.getContainer());

            version = Table.insert(user, getStudyVersionTable(), version);
            designInfo.setPublicRevision(version.getRevision());
            designInfo.setLabel(version.getLabel());
            Table.update(user, getStudyDesignTable(), designInfo, designInfo.getStudyId());
            transaction.commit();
        }

        return version;
    }

    public void deleteStudyDesigns(Container c, Set<TableInfo> deletedTables)
    {
        inactivateStudyDesign(c);
        Filter filter = SimpleFilter.createContainerFilter(c);
        Table.delete(getStudyVersionTable(), filter);
        deletedTables.add(getStudyVersionTable());
        Table.delete(getStudyDesignTable(), filter);
        deletedTables.add(getStudyDesignTable());
        //Legacy design sourceContainer "remembered" where the study design was created. If deleting sourceContainer make sure don't have orphan rows
        SQLFragment updateContainers = new SQLFragment("UPDATE " + getStudyDesignTable() + " SET sourceContainer=container WHERE sourceContainer=?", c);
        new SqlExecutor(getSchema()).execute(updateContainers);
    }

    public void deleteStudyDesignLookupValues(Container c, Set<TableInfo> deletedTables)
    {
        Filter filter = SimpleFilter.createContainerFilter(c);
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignImmunogenTypes(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignImmunogenTypes());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignChallengeTypes(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignChallengeTypes());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignGenes(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignGenes());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignRoutes(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignRoutes());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignSubTypes(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignSubTypes());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignSampleTypes(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignSampleTypes());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignUnits(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignUnits());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignAssays(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignAssays());
        Table.delete(StudySchema.getInstance().getTableInfoStudyDesignLabs(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoStudyDesignLabs());
        Table.delete(StudySchema.getInstance().getTableInfoDoseAndRoute(), filter);
        deletedTables.add(StudySchema.getInstance().getTableInfoDoseAndRoute());
    }

    public void inactivateStudyDesign(Container c)
    {
        //If designs were sourced from another folder. Just move ownership back so new studies can be created
        Study study = StudyManager.getInstance().getStudy(c);
        if (null != study)
        {
            StudyDesignInfo studyDesign = getDesignForStudy(StudyManager.getInstance().getStudy(c));
            if (null != studyDesign)
            {
                //First mark as inactive
                studyDesign.setActive(false);
                Table.update(HttpView.currentContext().getUser(), getStudyDesignTable(), studyDesign, studyDesign.getStudyId());
                if (!c.equals(studyDesign.getSourceContainer()))
                    moveStudyDesign(HttpView.currentContext().getUser(), studyDesign, studyDesign.getSourceContainer());
            }
        }
    }

    public void deleteStudyDesign(Container container, int studyId)
    {
        SimpleFilter deleteVersionsFilter = SimpleFilter.createContainerFilter(container);
        deleteVersionsFilter.addCondition(FieldKey.fromParts("studyId"), studyId);
        Table.delete(getStudyVersionTable(), deleteVersionsFilter);

        SimpleFilter deleteDesignInfoFilter = SimpleFilter.createContainerFilter(container);
        deleteDesignInfoFilter.addCondition(FieldKey.fromParts("studyId"), studyId);
        Table.delete(getStudyDesignTable(), deleteDesignInfoFilter);
    }

    public Study generateStudyFromDesign(User user, Container parent, String folderName, Date startDate,
                                         String subjectNounSingular, String subjectNounPlural, String subjectColumnName, StudyDesignInfo info,
                                         List<Map<String,Object>> participantDataset, List<Map<String,Object>> specimens) throws XmlException, IOException, ServletException, ValidationException
    {
        Container studyFolder = parent.getChild(folderName);
        if (null == studyFolder)
            studyFolder = ContainerManager.createContainer(parent, folderName);
        if (null != StudyManager.getInstance().getStudy(studyFolder))
            throw new IllegalStateException("Study already exists in folder");
        
        SecurityManager.setInheritPermissions(studyFolder);
        studyFolder.setFolderType(FolderTypeManager.get().getFolderType(StudyFolderType.NAME), user);

        //Grab study info from XML and use it here
        StudyDesignVersion version = StudyDesignManager.get().getStudyDesignVersion(info.getContainer(), info.getStudyId());
        GWTStudyDefinition def = XMLSerializer.fromXML(version.getXML(), user, studyFolder);

        StudyImpl study = new StudyImpl(studyFolder, folderName + " Study");
        study.setTimepointType(TimepointType.DATE);
        study.setStartDate(startDate);
        study.setSubjectNounSingular(subjectNounSingular);
        study.setSubjectNounPlural(subjectNounPlural);
        study.setSubjectColumnName(subjectColumnName);
        study.setDescription(def.getDescription());
        study.setGrant(def.getGrant());
        study.setInvestigator(def.getInvestigator());
        study = StudyManager.getInstance().createStudy(user, study);

        List<GWTTimepoint> timepoints = def.getAssaySchedule().getTimepoints();
        Collections.sort(timepoints);
        if (timepoints.get(0).getDays() > 0)
            timepoints.add(0, new GWTTimepoint("Study Start", 0, GWTTimepoint.DAYS));

        //We try to create timepoints that make sense. A week is day-3 to day +3 unless that would overlap
        double previousDay = timepoints.get(0).getDays() - 1.0;
        for (int timepointIndex = 0; timepointIndex < timepoints.size(); timepointIndex++)
        {
            GWTTimepoint timepoint = timepoints.get(timepointIndex);
            double startDay = timepoints.get(timepointIndex).getDays();
            double endDay = startDay;
            double nextDay = timepointIndex + 1 == timepoints.size() ? Double.MAX_VALUE : timepoints.get(timepointIndex + 1).getDays();
            if (timepoint.getUnit() == GWTTimepoint.WEEKS)
            {
                startDay = Math.max(previousDay + 1, startDay - 3);
                endDay = Math.min(nextDay - 1, endDay + 3);
            }
            else if (timepoint.getUnit() == GWTTimepoint.MONTHS)
            {
                startDay = Math.max(previousDay + 1, startDay - 15);
                endDay = Math.min(nextDay - 1, endDay + 15);
            }
            VisitImpl visit = new VisitImpl(studyFolder, startDay, endDay, timepoint.toString(), Visit.Type.REQUIRED_BY_TERMINATION);
            StudyManager.getInstance().createVisit(study, user, visit);
            previousDay = endDay;
        }

        Map<String, PropertyType> nameMap = new HashMap<>();
        //TODO: Not quite right. Really should use types culled from tabloader
        for (String propertyId : participantDataset.get(0).keySet())
        {
            Object val = participantDataset.get(0).get(propertyId);
            nameMap.put(propertyId, null == val ? PropertyType.STRING : PropertyType.getFromClass(val.getClass()));
        }
        List<String> errors = new ArrayList<>();

        Dataset subjectDataset = AssayPublishManager.getInstance().createAssayDataset(user, study, "Subjects", null, null, true, null);
        study = study.createMutable();
        study.setParticipantCohortDatasetId(subjectDataset.getDatasetId());
        study.setParticipantCohortProperty("Cohort");
        StudyManager.getInstance().updateStudy(user, study);
        
        AssayPublishService.get().publishAssayData(user, parent, studyFolder, "Subjects", null, participantDataset, nameMap, errors);
        if (errors.size() > 0) //We were supposed to check these coming in
            throw new RuntimeException(StringUtils.join(errors, '\n'));

        //Need to make the dataset at least optional for some visit
//        DatasetDefinition[] dsds = StudyManager.getInstance().getDatasetDefinitions(study);
//        for (DatasetDefinition dsd : dsds)
//            StudyManager.getInstance().updateVisitDatasetMapping(user, study.getContainer(), 1, dsd.getDatasetId(), VisitDatasetType.OPTIONAL);

        SimpleSpecimenImporter importer = new SimpleSpecimenImporter(study.getContainer(), user);
        importer.process(specimens, false);

        //Move study design into study folder...
        moveStudyDesign(user, info, study.getContainer());
        info.setActive(true);
        //and attach to this study
        Table.update(user, getStudyDesignTable(), info, info.getStudyId());

        Portal.addPart(study.getContainer(), StudyModule.studyDesignSummaryWebPartFactory, null, 0);
        
        return study;
    }

    public List<Map<String, Object>> generateParticipantDataset(User user, GWTStudyDefinition def)
    {
        List<GWTCohort> cohorts = def.getGroups();
        int count = 0;
        for (GWTCohort cohort : cohorts)
            count += cohort.getCount();

        List<Map<String,Object>> participantInfo = new ArrayList<>(count);
        for (int cohortNum = 0; cohortNum < cohorts.size(); cohortNum++)
        {
            GWTCohort cohort = cohorts.get(cohortNum);
            String participantId;

            for (int participantNum = 0; participantNum < cohort.getCount(); participantNum++)
            {
                participantId = sprintf("%03d%02d%02d", def.getCavdStudyId(), cohortNum + 1, participantNum + 1);
                Map<String,Object> m = new HashMap<>();
                m.put("ParticipantId", participantId);
                m.put("Cohort", cohort.getName());
                m.put("Index", participantNum + 1);
                m.put("SequenceNum", 1.0);
                participantInfo.add(m);
            }
        }
        return participantInfo;
    }

    /**
     * Generate a list of samples that can be uploaded. Rule is
     * For each timepoint
     *   For each cohort
     *    For each individual
     *            Produce 1 vial
     * Numbering: Sample is StudyId+TimePointIndex+CohortIndex+IndividualIndex+S|P|C
     * Vial: Sample+N where n is index for that timepoint
     * We generate ids for each assay at each timepoint even if assay is not required at that timepoint
     * @param studyDefinition
     * @param participantInfo
     * @return
     */
    public List<Map<String,Object>> generateSampleList(GWTStudyDefinition studyDefinition, List<Map<String, Object>> participantInfo, Date studyStartDate)
    {
        GWTAssaySchedule assaySchedule = studyDefinition.getAssaySchedule();
        List<GWTTimepoint> timepoints = assaySchedule.getTimepoints();
        Collections.sort(timepoints);
        Map<GWTTimepoint, Map<String,Integer>> vialsPerSampleType = new HashMap<>();
        for (GWTAssayDefinition def : assaySchedule.getAssays())
        {
            for (GWTTimepoint tp : timepoints)
            {
                Map<String, Integer> timepointSamples = vialsPerSampleType.get(tp);
                if (null == timepointSamples)
                {
                    timepointSamples = new HashMap<>();
                    vialsPerSampleType.put(tp, timepointSamples);
                }
                GWTAssayNote note = assaySchedule.getAssayPerformed(def, tp);
                GWTSampleMeasure measure;
                if (null != note)
                {
                    measure = note.getSampleMeasure();
                    timepointSamples.put(measure.getType(),  1);
                }
            }
        }

        //CONSIDER: Use something like ArrayListMap to share hash table space
        List<Map<String,Object>> rows = new ArrayList<>();
        int timepointIndex = 1; //Use one based
        for (GWTTimepoint tp : timepoints)
        {
            List<GWTCohort> groups = studyDefinition.getGroups();
            Map<String, Integer> timepointSamples = vialsPerSampleType.get(tp);
            String cohort = null;
            int cohortIndex = 0;
            int participantIndex = 0;
            for (Map participant : participantInfo)
            {
                Date startDate = (Date) participant.get("StartDate");
                if (startDate == null)
                    startDate = studyStartDate;

                String ptid = participant.get("ParticipantId").toString();
                for (String st : timepointSamples.keySet())
                {
                    String sampleId = ptid + "-" + tp.getDays();
                    Map<String,Object> m = new HashMap<>();
                    m.put(SimpleSpecimenImporter.VISIT, timepointIndex);
                    m.put(SimpleSpecimenImporter.PARTICIPANT_ID, ptid);
                    m.put(SimpleSpecimenImporter.SAMPLE_ID, sampleId);
                    m.put(SimpleSpecimenImporter.VIAL_ID, sampleId + (timepointSamples.size() == 1 ? "" : st));
                    m.put(SimpleSpecimenImporter.DERIVIATIVE_TYPE, st);
                    m.put(SimpleSpecimenImporter.DRAW_TIMESTAMP, getDay(startDate, tp.getDays()));
                    rows.add(m);
                }
            }
            timepointIndex++;
        }
        return rows;
    }

    private Date getDay(Date startDate, int days)
    {
        Calendar c = Calendar.getInstance();
        c.setTime(startDate);
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTime();
    }

    private static String sprintf(String pat, Object... args)
    {
        StringWriter s = new StringWriter();
        PrintWriter pw = new PrintWriter(s);
        //TODO: Checksum
        pw.printf(pat, args);
        return s.toString();
    }

    public StudyDesignInfo getDesignForStudy(Study study)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(study.getContainer());
        filter.addCondition(FieldKey.fromParts("Active"), Boolean.TRUE);
        StudyDesignInfo info = new TableSelector(getStudyDesignTable(), filter, null).getObject(StudyDesignInfo.class);
        return info;
    }


    public StudyDesignInfo getDesignForStudy(User user, Study study, boolean createIfNull) throws Exception
    {
        StudyDesignInfo info = getDesignForStudy(study);
        if (null == info && createIfNull)
        {
            GWTStudyDefinition def = DesignerController.getTemplate(user, study.getContainer());
            def.setStudyName(study.getLabel());
            StudyDesignVersion version = new StudyDesignVersion();
            version.setContainer(study.getContainer());
            version.setDescription(study.getDescription());
            version.setLabel(study.getLabel());
            version.setXML(XMLSerializer.toXML(def).toString());
            version = saveStudyDesign(user, study.getContainer(), version);
            info = getStudyDesign(study.getContainer(), version.getStudyId());
            info.setLabel(study.getLabel());
            info.setActive(true);
            Table.update(user, getStudyDesignTable(), info, info.getStudyId());
        }

        return info;
    }

    public List<String> getStudyDesignLookupValues(User user, Container c, TableInfo studyDesignTable)
    {
        return getStudyDesignLookupValues(user, c, studyDesignTable, true, true);
    }

    public List<String> getStudyDesignLookupValues(User user, Container c, TableInfo studyDesignTable, boolean includeProject, boolean excludeInactive)
    {
        if (c != null && studyDesignTable != null)
        {
            SimpleFilter filter = new SimpleFilter();
            if (includeProject)
            {
                ContainerFilter containerFilter = new ContainerFilter.CurrentPlusProject(user);
                filter.addCondition(containerFilter.createFilterClause(getSchema(), FieldKey.fromParts("Container"), c));
            }
            else
                filter = SimpleFilter.createContainerFilter(c);

            if (excludeInactive)
                filter.addCondition(FieldKey.fromParts("Inactive"), false);

            return new TableSelector(studyDesignTable, Collections.singleton("Name"), filter, new Sort("Name")).getArrayList(String.class);
        }
        else
            return Collections.emptyList();
    }

    public List<String> getStudyCohorts(User user, Container c)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(c);
        if (study != null)
        {
            List<String> cohortNames = new ArrayList<>();
            for (CohortImpl cohort : study.getCohorts(user))
                cohortNames.add(cohort.getLabel());

            return cohortNames;
        }
        else
        {
            return Collections.emptyList();
        }
    }
}
