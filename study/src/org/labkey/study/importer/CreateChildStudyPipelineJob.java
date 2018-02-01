/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.study.importer;

import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporterImpl;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.PipelineJobLoggerGetter;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.StudySnapshotType;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.MemoryVirtualFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.StudySchema;
import org.labkey.study.model.ChildStudyDefinition;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.ParticipantCategoryImpl;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.ParticipantMapper;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.StudySnapshot;
import org.labkey.study.model.Vial;
import org.labkey.study.pipeline.StudyImportDatasetTask;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.writer.ParticipantGroupWriter;
import org.labkey.study.writer.StudyExportContext;
import org.labkey.study.writer.StudyWriterFactory;
import org.labkey.study.writer.StudyArchiveDataTypes;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
* User: adam
* Date: 9/27/12
* Time: 9:52 PM
*/
public class CreateChildStudyPipelineJob extends AbstractStudyPipelineJob
{
    private final ChildStudyDefinition _form;
    private final boolean _destFolderCreated;

    public CreateChildStudyPipelineJob(ViewContext context, PipeRoot root, ChildStudyDefinition form, boolean destFolderCreated)
    {
        super(context.getContainer(), ContainerManager.getForPath(form.getDstPath()), context.getUser(), context.getActionURL(), root);

        _form = form;
        _destFolderCreated = destFolderCreated;
    }

    @Override
    public String getDescription()
    {
        if (_form.getMode() != null)
            return _form.getMode().getJobDescription();
        else
            return "Create Study";
    }

    @Override
    protected String getLogName()
    {
        return "publishStudy";
    }

    @Override
    public boolean run(ViewContext context)
    {
        boolean success = false;

        try
        {
            Container sourceContainer = ContainerManager.getForPath(_form.getSrcPath());
            StudyImpl sourceStudy = StudyManager.getInstance().getStudy(sourceContainer);

            if (null == sourceStudy)
                throw new NotFoundException("Source study no longer exists");

            StudyImpl destStudy = StudyManager.getInstance().getStudy(getDstContainer());
            setStatus(TaskStatus.running);

            if (destStudy != null)
            {
                Set<DatasetDefinition> datasets = new HashSet<>();

                // get the list of datasets to export
                for (int datasetId : _form.getDatasets())
                {
                    DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(sourceStudy, datasetId);

                    if (def != null)
                        datasets.add(def);
                }

                List<ParticipantGroup> participantGroups = new ArrayList<>();

                for (int rowId : _form.getGroups())
                {
                    ParticipantGroup pg = ParticipantGroupManager.getInstance().getParticipantGroup(sourceStudy.getContainer(), getUser(), rowId);
                    if (pg !=null)
                        participantGroups.add(pg);
                }

                HashSet<Integer> selectedVisits = null;
                if (null != _form.getVisits())
                {
                    selectedVisits = new HashSet<>(Arrays.asList(_form.getVisits()));
                }

                // Force snapshots for datasets referenced by the parent study's visit map. This ensures that
                // we'll be able to calculate visit dates for all data:
                for (Visit visit : sourceStudy.getVisits(Visit.Order.SEQUENCE_NUM))
                {
                    // Limit datasets taken to those in the selected set of visits.
                    if (visit.getVisitDateDatasetId() != null && (null == selectedVisits || selectedVisits.contains(visit.getId())))
                    {
                        DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(sourceStudy, visit.getVisitDateDatasetId());
                        if (def != null)
                            datasets.add(def);
                    }
                }

                // issue 15942: for date based studies any demographics datasets that have a StartDate column need to be included so that
                // visits are correctly calculated\
                if (sourceStudy.getTimepointType() == TimepointType.DATE)
                {
                    for (DatasetDefinition dataset : sourceStudy.getDatasets())
                    {
                        if (dataset.isDemographicData())
                        {
                            TableInfo tInfo = dataset.getStorageTableInfo();
                            if (tInfo == null) continue;
                            ColumnInfo col = tInfo.getColumn("StartDate");
                            if (null != col)
                            {
                                datasets.add(dataset);

                                // also add the visits included in this dataset
                                if (selectedVisits != null)
                                {
                                    for (Visit visit : StudyManager.getInstance().getVisitsForDataset(sourceStudy.getContainer(), dataset.getDatasetId()))
                                        selectedVisits.add(visit.getId());
                                }
                            }
                        }
                    }
                }

                // log selected settings to the pipeline job log file
                _form.logSelections(getLogger());

                MemoryVirtualFile vf = new MemoryVirtualFile();
                User user = getUser();
                if(_form.getFolderProps() != null)
                    _form.setFolderProps(_form.getFolderProps()[0].split(","));
                if(_form.getStudyProps() != null)
                    _form.setStudyProps(_form.getStudyProps()[0].split(","));
                Set<String> dataTypes = getDataTypesToExport(_form);

                FolderExportContext folderExportContext = new FolderExportContext(user, sourceStudy.getContainer(), dataTypes, "new", false, _form.getExportPhiLevel(), _form.isShiftDates(), _form.isUseAlternateParticipantIds(),
                        _form.isMaskClinic(), new PipelineJobLoggerGetter(this));

                if (_form.getLists() != null)
                    folderExportContext.setListIds(_form.getLists());

                if (_form.getViews() != null)
                    folderExportContext.setViewIds(_form.getViews());

                if (_form.getReports() != null)
                    folderExportContext.setReportIds(_form.getReports());

                StudyExportContext studyExportContext = new StudyExportContext(sourceStudy, user, sourceStudy.getContainer(),
                        dataTypes, _form.getExportPhiLevel(),
                        new ParticipantMapper(sourceStudy, user, _form.isShiftDates(), _form.isUseAlternateParticipantIds()),
                        _form.isMaskClinic(), datasets, new PipelineJobLoggerGetter(this)
                );

                if (selectedVisits != null)
                    studyExportContext.setVisitIds(selectedVisits);

                // TODO: Need handlers for each "create study" type (ancillary, publish, specimen)
                if (!participantGroups.isEmpty())
                {
                    studyExportContext.setParticipants(getGroupParticipants(_form, participantGroups, studyExportContext));
                }
                else if (null != _form.getVials())
                {
                    studyExportContext.setParticipants(getSpecimenParticipants(_form));
                    studyExportContext.setVials(_form.getVials());
                }

                folderExportContext.addContext(StudyExportContext.class, studyExportContext);

                // Save these snapshot settings to support specimen refresh and provide history
                StudySnapshot snapshot = new StudySnapshot(studyExportContext, getDstContainer(), _form.isSpecimenRefresh(), _form);
                Table.insert(getUser(), StudySchema.getInstance().getTableInfoStudySnapshot(), snapshot);

                // Save the snapshot RowId to the destination study
                StudyImpl mutableStudy = StudyManager.getInstance().getStudy(getDstContainer()).createMutable();
                mutableStudy.setStudySnapshot(snapshot.getRowId());
                StudyManager.getInstance().updateStudy(user, mutableStudy);

                // export objects from the parent study, then import them into the new study
                getLogger().info("Exporting data from parent study.");
                // Issue 33016: Publish study fails when dataset has a PHI column
                // ComplianceQueryLoggingProfilerListener.queryInvoked() (in compliance module) will get null user/container without the next two lines
                // CONSIDER: removing these if ComplianceQueryLoggingProfilerListener can ignore passed in user/container and get them somewhere else (like QueryLogging)
                QueryService.get().setEnvironment(QueryService.Environment.USER, user);
                QueryService.get().setEnvironment(QueryService.Environment.CONTAINER, sourceStudy.getContainer());
                exportFromParentStudy(folderExportContext, vf);

                // import folder items (reports, lists, etc)
                importFolderItems(destStudy, vf);

                StudyImportContext studyImportContext = importToDestinationStudy(_errors, destStudy, vf);

                // copy participants
                exportParticipantGroups(_form, sourceStudy, participantGroups, folderExportContext, vf);

                // assay schedule and treatment data (study design)
                importStudyDesignData(_errors, vf, studyImportContext);

                // import dataset data or create snapshot datasets
                importDatasetData(context, _form, sourceStudy, destStudy, snapshot, datasets, participantGroups, vf, _errors, studyImportContext);

                // import the specimen data and settings
                importSpecimenMetadata(_errors, vf, studyImportContext);
                importSpecimenSettings(_errors, vf, studyImportContext);
                importSpecimenData(destStudy, vf);

                // import the cohort settings, needs to happen after the dataset data and specimen data is imported so the full ptid list is available
                importCohortSettings(_errors, vf, studyImportContext);

                // import TreatmentVisitMap, needs to happen after cohort info is loaded (issue 19947)
                importTreatmentVisitMapData(_errors, vf, studyImportContext);
            }

            if (_errors.hasErrors())
            {
                StringBuilder sb = new StringBuilder();
                for (ObjectError error : _errors.getAllErrors())
                {
                    sb.append(error.getDefaultMessage()).append('\n');
                }
                throw new RuntimeException(sb.toString());
            }
            else
            {
                success = true;
            }
        }
        catch (Exception e)
        {
            error(getDescription() + " failed", e);
        }
        finally
        {
            if (!success && _destFolderCreated)
                ContainerManager.delete(getDstContainer(), getUser());
        }

        return success;
    }

    private void importParticipantGroups(VirtualFile vf, BindException errors, StudyImportContext importContext) throws Exception
    {
        VirtualFile studyDir = vf.getDir("study");

        if (importContext != null)
        {
            ParticipantGroupImporter groupImporter = new ParticipantGroupImporter();
            groupImporter.process(importContext, studyDir, errors);
        }
    }

    private Set<String> getDataTypesToExport(ChildStudyDefinition form)
    {
        Set<String> dataTypes = new HashSet<>();
        String[] folderProps = form.getFolderProps();
        String[] studyProps = form.getStudyProps();

        dataTypes.add(StudyWriterFactory.DATA_TYPE);
        dataTypes.add(StudyArchiveDataTypes.QC_STATE_SETTINGS);
        dataTypes.add(StudyArchiveDataTypes.VISIT_MAP);
        dataTypes.add(StudyArchiveDataTypes.CRF_DATASETS);
        dataTypes.add(StudyArchiveDataTypes.DATASET_DATA);
        dataTypes.add(StudyArchiveDataTypes.VIEW_CATEGORIES);
        dataTypes.add(StudyArchiveDataTypes.PARTICIPANT_GROUPS);

        if (StudySnapshotType.ancillary.equals(form.getMode()))
        {
            dataTypes.add(StudyArchiveDataTypes.COHORT_SETTINGS);
            dataTypes.add(StudyArchiveDataTypes.ASSAY_SCHEDULE);
            dataTypes.add(StudyArchiveDataTypes.TREATMENT_DATA);
        }

        if (folderProps != null)
        {
            Collections.addAll(dataTypes, folderProps);
        }
        if (studyProps != null)
        {
            Collections.addAll(dataTypes, studyProps);
        }

        if (form.getReports() != null)
        {
            dataTypes.add(FolderArchiveDataTypes.REPORTS_AND_CHARTS);
        }

        if (form.getViews() != null)
        {
            dataTypes.add(FolderArchiveDataTypes.GRID_VIEWS);
        }

        if (form.getLists() != null)
        {
            dataTypes.add(FolderArchiveDataTypes.LISTS);
        }

        if (form.isIncludeSpecimens())
        {
            dataTypes.add(StudyArchiveDataTypes.SPECIMENS);
        }

        return dataTypes;
    }

    private void importCohortSettings(BindException errors, VirtualFile vf, StudyImportContext importContext) throws Exception
    {
        VirtualFile studyDir = vf.getDir("study");

        if (importContext != null)
        {
            new CohortImporter().process(importContext, studyDir, errors);
        }
    }

    private void importSpecimenMetadata(BindException errors, VirtualFile vf, StudyImportContext importContext) throws Exception
    {
        VirtualFile specimenDir = SpecimenSchemaImporter.getSpecimenFolder(importContext);

        if (importContext != null)
        {
            new SpecimenSchemaImporter().process(importContext, specimenDir, errors);
        }
    }

    private void importSpecimenSettings(BindException errors, VirtualFile vf, StudyImportContext importContext) throws Exception
    {
        VirtualFile studyDir = vf.getDir("study");

        if (importContext != null)
        {
            new SpecimenSettingsImporter().process(importContext, studyDir, errors);
        }
    }

    private StudyImportContext importToDestinationStudy(BindException errors, StudyImpl newStudy, VirtualFile vf) throws Exception
    {
        VirtualFile studyDir = vf.getDir("study");
        StudyDocument studyDoc = getStudyDocument(studyDir);
        StudyImportContext importContext = null;

        getLogger().info("Importing data to destination study");
        if (studyDoc != null)
        {
            importContext = new StudyImportContext(getUser(), newStudy.getContainer(), studyDoc, null, new PipelineJobLoggerGetter(this), studyDir);

            // missing values and qc states
            new MissingValueImporterFactory().create().process(null, importContext, studyDir);
            new QcStatesImporter().process(importContext, studyDir, errors);

            // dataset definitions
            DatasetDefinitionImporter datasetDefinitionImporter = new DatasetDefinitionImporter();
            datasetDefinitionImporter.process(importContext, studyDir, errors);

            // import visits
            VisitImporter visitImporter = new VisitImporter();

            // don't create dataset definitions for datasets we don't import
            visitImporter.setEnsureDatasets(false);
            visitImporter.process(importContext, studyDir, errors);

            ProtocolDocumentImporter proImporter = new ProtocolDocumentImporter();
            proImporter.process(importContext, studyDir, errors);

            // custom participant view
            StudyViewsImporter viewsImporter = new StudyViewsImporter();
            viewsImporter.process(importContext, studyDir, errors);

            if (errors.hasErrors())
                throw new RuntimeException("Error importing study objects : " + errors.getMessage());
        }

        return importContext;
    }

    /**
     * Study design data includes treatment data, assay schedule and study design tables.
     */
    private void importStudyDesignData(BindException errors, VirtualFile vf, StudyImportContext importContext) throws Exception
    {
        VirtualFile studyDir = vf.getDir("study");

        if (importContext != null)
        {
            // assay schedule and treatment data (study design)
            new TreatmentDataImporter().process(importContext, studyDir, errors);
            new AssayScheduleImporter().process(importContext, studyDir, errors);

            if (errors.hasErrors())
                throw new RuntimeException("Error importing study design tables : " + errors.getMessage());
        }
    }

    private void importTreatmentVisitMapData(BindException errors, VirtualFile vf, StudyImportContext importContext) throws Exception
    {
        VirtualFile studyDir = vf.getDir("study");

        if (importContext != null)
        {
            new TreatmentVisitMapImporter().process(importContext, studyDir, errors);

            if (errors.hasErrors())
                throw new RuntimeException("Error importing treatment visit map table : " + errors.getMessage());
        }
    }

    private void importFolderItems(StudyImpl newStudy, VirtualFile vf) throws Exception
    {
        User user = getUser();
        FolderImporterImpl importer = new FolderImporterImpl();
        FolderDocument folderDoc = (FolderDocument)vf.getXmlBean("folder.xml");
        FolderImportContext folderImportContext = new FolderImportContext(user, newStudy.getContainer(), folderDoc, null, new PipelineJobLoggerGetter(this), vf);

        // remove the study folder importer since we are handling dataset, specimen, etc. importing separately
        importer.removeImporterByDescription("study");

        importer.process(null, folderImportContext, vf);
    }

    private void importDatasetData(ViewContext context, ChildStudyDefinition form, StudyImpl sourceStudy, StudyImpl destStudy, StudySnapshot snapshot, Set<DatasetDefinition> datasets,
                                   List<ParticipantGroup> participantGroups, VirtualFile vf, BindException errors, StudyImportContext importContext) throws Exception
    {
        User user = getUser();

        if (!form.isUpdate())
        {
            VirtualFile studyDir = vf.getDir("study");

            if (importContext != null)
            {
                // the dataset import task handles importing the dataset data and updating the participant and participantVisit tables
                VirtualFile datasetsDirectory = StudyImportDatasetTask.getDatasetsDirectory(importContext, studyDir);
                String datasetsFileName = StudyImportDatasetTask.getDatasetsFileName(importContext);

                StudyImportDatasetTask.doImport(datasetsDirectory, datasetsFileName, this, importContext, destStudy, true);
            }
            importParticipantGroups(vf, errors, importContext);
        }
        else
        {
            info("Creating query snapshot datasets.");

            importParticipantGroups(vf, errors, importContext);

            QuerySnapshotService.Provider svc = QuerySnapshotService.get(StudySchema.getInstance().getSchemaName());

            List<Integer> participantGroupIds = new ArrayList<>();
            if (!participantGroups.isEmpty())
            {
                // get the participant categories that were copied to the ancillary study
                for (ParticipantCategoryImpl category : ParticipantGroupManager.getInstance().getParticipantCategories(getDstContainer(), user))
                {
                    for (ParticipantGroup group : category.getGroups())
                    {
                        participantGroupIds.add(group.getRowId());
                    }
                }
            }

            for (DatasetDefinition def : datasets)
            {
                BindException datasetErrors = new NullSafeBindException(def, "dataset");
                StudyQuerySchema schema = StudyQuerySchema.createSchema(sourceStudy, user, true);
                TableInfo table = def.getTableInfo(user);
                QueryDefinition queryDef = QueryService.get().createQueryDefForTable(schema, table.getName());

                if (queryDef != null && def.getType().equals(Dataset.TYPE_STANDARD))
                {
                    queryDef.setDefinitionContainer(sourceStudy.getContainer());

                    QuerySnapshotDefinition qsDef = QueryService.get().createQuerySnapshotDef(destStudy.getContainer(), queryDef, def.getName());
                    qsDef.setUpdateDelay(form.getUpdateDelay());
                    qsDef.setParticipantGroups(participantGroupIds);
                    qsDef.setOptionsId(snapshot.getRowId());

                    qsDef.save(user);

                    if (svc != null)
                    {
                        svc.createSnapshot(context, qsDef, datasetErrors);
                        if (datasetErrors.hasErrors())
                        {
                            StringBuilder sb = new StringBuilder();
                            for (ObjectError error : datasetErrors.getAllErrors())
                                sb.append(error.getDefaultMessage()).append('\n');

                            error(String.format("Unable to create dataset '%s' : %s", def.getName(), sb.toString()));
                            return;
                        }
                    }
                }
            }
        }
    }

    private void exportParticipantGroups(ChildStudyDefinition form, StudyImpl sourceStudy, List<ParticipantGroup> participantGroups, FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        List<ParticipantGroup> groupsToCopy = new ArrayList<>();
        if (form.isCopyParticipantGroups())
            groupsToCopy.addAll(participantGroups);

        if (!groupsToCopy.isEmpty())
        {
            // query snapshots need to have participants created ahead of the snapshot
            if (form.isUpdate())
                ensureGroupParticipants(participantGroups, sourceStudy, _form.isUseAlternateParticipantIds());

            VirtualFile studyDir = vf.getDir("study");

            ParticipantGroupWriter groupWriter = new ParticipantGroupWriter();
            groupWriter.setGroupsToCopy(groupsToCopy);
            groupWriter.write(sourceStudy, ctx.getContext(StudyExportContext.class), studyDir);
        }
    }

    private List<String> getGroupParticipants(ChildStudyDefinition form, List<ParticipantGroup> participantGroups, StudyExportContext ctx)
    {
        if (!participantGroups.isEmpty())
        {
            StudySchema schema = StudySchema.getInstance();

            StringBuilder groupInClause = new StringBuilder();
            String delim = "";

            groupInClause.append("(");
            for (ParticipantGroup group : participantGroups)
            {
                groupInClause.append(delim);
                groupInClause.append(group.getRowId());

                delim = ",";
            }
            groupInClause.append(")");

            SQLFragment sql = new SQLFragment();
            sql.append(" SELECT DISTINCT(ParticipantId), ? FROM ").append(ParticipantGroupManager.getInstance().getTableInfoParticipantGroupMap(), "");
            sql.append(" WHERE GroupId IN ").append(groupInClause);
            sql.add(getDstContainer());
            SqlSelector selector = new SqlSelector(schema.getSchema(), sql);

            if (form.isUseAlternateParticipantIds())
            {
                List<String> alternateIds = new ArrayList<>();
                ParticipantMapper mapper = ctx.getParticipantMapper();
                for (String id : selector.getArray(String.class))
                    alternateIds.add(mapper.getMappedParticipantId(id));

                return alternateIds;
            }
            else
                return (List<String>)selector.getCollection(String.class);
        }
        return Collections.emptyList();
    }

    private List<String> getSpecimenParticipants(ChildStudyDefinition form)
    {
        Set<String> ptids = new HashSet<>();

        for (Vial vial : form.getVials())
        {
            String ptid = vial.getPtid();

            // PTID can be null
            if (null != ptid)
                ptids.add(vial.getPtid());
        }

        return new LinkedList<>(ptids);
    }

    /**
     * Participant groups cannot be created unless the subject ids exist in the participant table. Participants are usually
     * added via dataset data insertion but we need to have them sooner because we need to filter the datasets on participant
     * group participants.
     *
     * @param useAlternateParticipantIds Whether to use alternate participant ids
     */
    private void ensureGroupParticipants(List<ParticipantGroup> participantGroups, StudyImpl sourceStudy, boolean useAlternateParticipantIds)
    {
        if (!participantGroups.isEmpty())
        {
            StudySchema schema = StudySchema.getInstance();

            StringBuilder groupInClause = new StringBuilder();
            String delim = "";

            groupInClause.append("(");
            for (ParticipantGroup group : participantGroups)
            {
                groupInClause.append(delim);
                groupInClause.append(group.getRowId());

                delim = ",";
            }
            groupInClause.append(")");

            String columnName = useAlternateParticipantIds ? "AlternateId" : "ParticipantId";

            SQLFragment sql = new SQLFragment();

            sql.append("INSERT INTO ").append(schema.getTableInfoParticipant()).append(" (ParticipantId, Container)");
            sql.append(" SELECT DISTINCT(").append(columnName).append("), ? FROM ").append(ParticipantGroupManager.getInstance().getTableInfoParticipantGroupMap(), "gm");
            sql.add(getDstContainer());

            if (useAlternateParticipantIds)
                sql.append(" INNER JOIN ").append(schema.getTableInfoParticipant(), "p").append(" ON gm.Container = p.Container AND gm.ParticipantId = p.ParticipantId");

            sql.append(" WHERE GroupId IN ").append(groupInClause).append(" AND gm.Container = ?");
            sql.add(sourceStudy.getContainer());

            new SqlExecutor(schema.getSchema()).execute(sql);
        }
    }
}
