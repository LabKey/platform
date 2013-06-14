/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
import org.labkey.api.admin.PipelineJobLoggerGetter;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.MemoryVirtualFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.ChildStudyDefinition;
import org.labkey.study.model.ParticipantCategoryImpl;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.ParticipantMapper;
import org.labkey.study.model.Specimen;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.StudySnapshot;
import org.labkey.study.pipeline.StudyImportDatasetTask;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.writer.DatasetWriter;
import org.labkey.study.writer.ParticipantGroupWriter;
import org.labkey.study.writer.QcStateWriter;
import org.labkey.study.writer.StudyExportContext;
import org.labkey.study.writer.StudyWriterFactory;
import org.labkey.study.writer.ViewCategoryWriter;
import org.labkey.study.writer.VisitMapWriter;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

import java.sql.SQLException;
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
public class CreateChildStudyPipelineJob extends AbstractStudyPiplineJob
{
    private ChildStudyDefinition _form;
    private boolean _destFolderCreated;

    private transient Set<DataSetDefinition> _datasets = new HashSet<DataSetDefinition>();
    private transient List<ParticipantGroup> _participantGroups = new ArrayList<ParticipantGroup>();

    private static final String REPORT_WRITER_TYPE = "Reports";
    private static final String LIST_WRITER_TYPE = "Lists";
    private static final String CUSTOM_VIEWS_TYPE = "Custom Views";
    private static final String SPECIMEN_WRITER_TYPE = "Specimens"; // TODO: use SpecimenWriterArchive.SELECTION_TEXT

    public CreateChildStudyPipelineJob(ViewContext context, PipeRoot root, ChildStudyDefinition form, boolean destFolderCreated)
    {
        super(context.getContainer(), context.getUser(), context.getActionURL(), root);

        _form = form;
        _destFolderCreated = destFolderCreated;
        _dstContainer = ContainerManager.getForPath(_form.getDstPath());
    }

    @Override
    public String getDescription()
    {
        if (_form.isPublish())
            return "Publish Study";
        else
            return "Create Ancillary Study";
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
            _sourceStudy = StudyManager.getInstance().getStudy(sourceContainer);

            StudyImpl destStudy = StudyManager.getInstance().getStudy(_dstContainer);
            setStatus("RUNNING");

            if (destStudy != null)
            {
                // get the list of datasets to export
                for (int datasetId : _form.getDatasets())
                {
                    DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getSourceStudy(), datasetId);

                    if (def != null)
                        _datasets.add(def);
                }

                for (int rowId : _form.getGroups())
                {
                    ParticipantGroup pg = ParticipantGroupManager.getInstance().getParticipantGroup(_sourceStudy.getContainer(), getUser(), rowId);
                    if(pg !=null)
                        _participantGroups.add(pg);
                }


                HashSet<Integer> selectedVisits = null;
                if (null != _form.getVisits())
                {
                    selectedVisits = new HashSet<Integer>(Arrays.asList(_form.getVisits()));
                }

                // Force snapshots for datasets referenced by the parent study's visit map.  This ensures that
                // we'll be able to calculate visit dates for all data:
                for (Visit visit : getSourceStudy().getVisits(Visit.Order.SEQUENCE_NUM))
                {
                    // Limit datasets taken to those in the selected set of visits.
                    if (visit.getVisitDateDatasetId() != null && (null == selectedVisits || selectedVisits.contains(visit.getId())))
                    {
                        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getSourceStudy(), visit.getVisitDateDatasetId());
                        if (def != null)
                            _datasets.add(def);
                    }
                }

                // issue 15942: for date based studies any demographics datasets that have a StartDate column need to be included so that
                // visits are correctly calculated\
                if (_sourceStudy.getTimepointType() == TimepointType.DATE)
                {
                    for (DataSetDefinition dataset : _sourceStudy.getDataSets())
                    {
                        if (dataset.isDemographicData())
                        {
                            TableInfo tInfo = dataset.getStorageTableInfo();
                            if (tInfo == null) continue;
                            ColumnInfo col = tInfo.getColumn("StartDate");
                            if (null != col)
                            {
                                _datasets.add(dataset);

                                // also add the visits included in this dataset
                                if (selectedVisits != null)
                                {
                                    for (Visit visit : StudyManager.getInstance().getVisitsForDataset(_sourceStudy.getContainer(), dataset.getDataSetId()))
                                        selectedVisits.add(visit.getId());
                                }
                            }
                        }
                    }
                }

                MemoryVirtualFile vf = new MemoryVirtualFile();
                User user = getUser();
                if(_form.getFolderProps() != null)
                    _form.setFolderProps(_form.getFolderProps()[0].split(","));
                if(_form.getStudyProps() != null)
                    _form.setStudyProps(_form.getStudyProps()[0].split(","));
                Set<String> dataTypes = getDataTypesToExport(_form);

                FolderExportContext ctx = new FolderExportContext(user, _sourceStudy.getContainer(), dataTypes, "new", false,
                        _form.isRemoveProtectedColumns(), _form.isShiftDates(), _form.isUseAlternateParticipantIds(),
                        _form.isMaskClinic(), new PipelineJobLoggerGetter(this));

                if (_form.getLists() != null)
                    ctx.setListIds(_form.getLists());

                if (_form.getViews() != null)
                    ctx.setViewIds(_form.getViews());

                if (_form.getReports() != null)
                    ctx.setReportIds(_form.getReports());

                StudyExportContext studyCtx = new StudyExportContext(_sourceStudy, user, _sourceStudy.getContainer(),
                        false, dataTypes, _form.isRemoveProtectedColumns(),
                        new ParticipantMapper(_sourceStudy, _form.isShiftDates(), _form.isUseAlternateParticipantIds()),
                        _form.isMaskClinic(), _datasets, new PipelineJobLoggerGetter(this)
                );

                if (selectedVisits != null)
                    studyCtx.setVisitIds(selectedVisits);

                // TODO: Need handlers for each "create study" type (ancillary, publish, specimen)
                if (!_participantGroups.isEmpty())
                    studyCtx.setParticipants(getGroupParticipants(_form, studyCtx));
                else if (null != _form.getSpecimens())
                {
                    studyCtx.setParticipants(getSpecimenParticipants(_form));
                    studyCtx.setSpecimens(_form.getSpecimens());
                }

                ctx.addContext(StudyExportContext.class, studyCtx);

                // Save these snapshot settings to support specimen refresh and provide history
                StudySnapshot snapshot = new StudySnapshot(studyCtx, _dstContainer, _form.isSpecimenRefresh());
                Table.insert(getUser(), StudySchema.getInstance().getTableInfoStudySnapshot(), snapshot);

                // export objects from the parent study, then import them into the new study
                getLogger().info("Exporting data from parent study.");
                exportFromParentStudy(ctx, vf);
                importToDestinationStudy(_errors, destStudy, vf);

                // copy participants
                exportParticipantGroups(_form, ctx, vf);

                // import dataset data or create snapshot datasets
                importDatasetData(context, _form, destStudy, snapshot, vf, _errors);

                // import the specimen data
                importSpecimenData(destStudy, vf);

                // import folder items (reports, lists, etc)
                importFolderItems(destStudy, vf);

                // Get a fresh copy of the study... import methods may have changed it
                StudyImpl mutableStudy = StudyManager.getInstance().getStudy(_dstContainer).createMutable();
                mutableStudy.setStudySnapshot(snapshot.getRowId());
                StudyManager.getInstance().updateStudy(user, mutableStudy);
            }

            if (_errors.hasErrors())
            {
                StringBuilder sb = new StringBuilder();
                for (ObjectError error : (List<ObjectError>)_errors.getAllErrors())
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
            error("Study creation failed", e);
        }
        finally
        {
            if (!success && _destFolderCreated)
                ContainerManager.delete(_dstContainer, getUser());

            return success;
        }
    }

    private void importParticipantGroups(Study newStudy, VirtualFile vf, BindException errors) throws Exception
    {
        VirtualFile studyDir = vf.getDir("study");
        StudyDocument studyDoc = getStudyDocument(studyDir);

        if (studyDoc != null)
        {
            StudyImportContext importContext = new StudyImportContext(getUser(), newStudy.getContainer(), studyDoc, new PipelineJobLoggerGetter(this), studyDir);

            ParticipantGroupImporter groupImporter = new ParticipantGroupImporter();
            groupImporter.process(importContext, studyDir, errors);
        }
    }

    private StudyImpl getSourceStudy()
    {
        return _sourceStudy;
    }

    private Set<String> getDataTypesToExport(ChildStudyDefinition form)
    {
        Set<String> dataTypes = new HashSet<String>();
        String[] folderProps = form.getFolderProps();
        String[] studyProps = form.getStudyProps();

        dataTypes.add(StudyWriterFactory.DATA_TYPE);
        dataTypes.add(QcStateWriter.DATA_TYPE);
        dataTypes.add(VisitMapWriter.DATA_TYPE);
        dataTypes.add(DatasetWriter.SELECTION_TEXT);
        dataTypes.add(ViewCategoryWriter.DATA_TYPE);
        dataTypes.add(ParticipantGroupWriter.DATA_TYPE);


        if(folderProps != null)
        {
            for(int i = 0; i < folderProps.length; i++)
            {
                dataTypes.add(folderProps[i]);
            }
        }
        if(studyProps != null)
        {
            for(int i = 0; i < studyProps.length; i++)
            {
                dataTypes.add(studyProps[i]);
            }
        }

        if (form.getReports() != null)
        {
            dataTypes.add(REPORT_WRITER_TYPE);
        }

        if (form.getViews() != null)
        {
            dataTypes.add(CUSTOM_VIEWS_TYPE);
        }

        if (form.getLists() != null)
        {
            dataTypes.add(LIST_WRITER_TYPE);
        }

        if (form.isIncludeSpecimens())
        {
            dataTypes.add(SPECIMEN_WRITER_TYPE);
        }

        return dataTypes;
    }

    private void importToDestinationStudy(BindException errors, StudyImpl newStudy, VirtualFile vf) throws Exception
    {
        User user = getUser();
        VirtualFile studyDir = vf.getDir("study");
        StudyDocument studyDoc = getStudyDocument(studyDir);

        getLogger().info("Importing data to destination study");
        if (studyDoc != null)
        {
            StudyImportContext importContext = new StudyImportContext(user, newStudy.getContainer(), studyDoc, new PipelineJobLoggerGetter(this), studyDir);

            // missing values and qc states
            new MissingValueImporterFactory().create().process(null, importContext, studyDir);
            new QcStatesImporter().process(importContext, studyDir, errors);
            new SpecimenSettingsImporter().process(importContext, studyDir, errors);

            // dataset definitions
            DatasetDefinitionImporter datasetDefinitionImporter = new DatasetDefinitionImporter();
            datasetDefinitionImporter.process(importContext, studyDir, errors);

            // import visits
            VisitImporter visitImporter = new VisitImporter();

            // don't create dataset definitions for datasets we don't import
            visitImporter.setEnsureDataSets(false);
            visitImporter.process(importContext, studyDir, errors);

            ProtocolDocumentImporter proImporter = new ProtocolDocumentImporter();
            proImporter.process(importContext, studyDir, errors);

            CohortImporter cohortImporter = new CohortImporter();
            cohortImporter.process(importContext, studyDir, errors);

            if (errors.hasErrors())
                throw new RuntimeException("Error importing study objects : " + errors.getMessage());
        }

    }

    private void importFolderItems(StudyImpl newStudy, VirtualFile vf) throws Exception
    {
        User user = getUser();
        FolderImporterImpl importer = new FolderImporterImpl();
        FolderDocument folderDoc = (FolderDocument)vf.getXmlBean("folder.xml");
        FolderImportContext folderImportContext = new FolderImportContext(user, newStudy.getContainer(), folderDoc, new PipelineJobLoggerGetter(this), vf);

        // remove the study folder importer since we are handling dataset, specimen, etc. importing separately
        importer.removeImporterByDescription("study");

        importer.process(null, folderImportContext, vf);
    }

    private void importDatasetData(ViewContext context, ChildStudyDefinition form, StudyImpl destStudy, StudySnapshot snapshot, VirtualFile vf, BindException errors) throws Exception
    {
        User user = getUser();

        if (!form.isUpdate())
        {
            VirtualFile studyDir = vf.getDir("study");
            StudyDocument studyDoc = getStudyDocument(studyDir);

            if (studyDoc != null)
            {
                StudyImportContext importContext = new StudyImportContext(user, destStudy.getContainer(), studyDoc, new PipelineJobLoggerGetter(this), studyDir);

                // the dataset import task handles importing the dataset data and updating the participant and participantVisit tables
                VirtualFile datasetsDirectory = StudyImportDatasetTask.getDatasetsDirectory(importContext, studyDir);
                String datasetsFileName = StudyImportDatasetTask.getDatasetsFileName(importContext);

                StudyImportDatasetTask.doImport(datasetsDirectory, datasetsFileName, this, importContext, destStudy);
            }
            importParticipantGroups(destStudy, vf, errors);
        }
        else
        {
            info("Creating query snapshot datasets.");

            importParticipantGroups(destStudy, vf, errors);

            QuerySnapshotService.I svc = QuerySnapshotService.get(StudySchema.getInstance().getSchemaName());

            List<Integer> participantGroups = new ArrayList<Integer>();
            if (!_participantGroups.isEmpty())
            {
                // get the participant categories that were copied to the ancillary study
                for (ParticipantCategoryImpl category : ParticipantGroupManager.getInstance().getParticipantCategories(_dstContainer, user))
                {
                    for(ParticipantGroup group : category.getGroups())
                    {
                        participantGroups.add(group.getRowId());
                    }
                }
            }

            for (DataSetDefinition def : _datasets)
            {
                BindException datasetErrors = new NullSafeBindException(def, "dataset");
                StudyQuerySchema schema = new StudyQuerySchema(getSourceStudy(), user, true);
                TableInfo table = def.getTableInfo(user);
                QueryDefinition queryDef = QueryService.get().createQueryDefForTable(schema, table.getName());

                if (queryDef != null && def.getType().equals(DataSet.TYPE_STANDARD))
                {
                    queryDef.setContainer(getSourceStudy().getContainer());

                    QuerySnapshotDefinition qsDef = QueryService.get().createQuerySnapshotDef(destStudy.getContainer(), queryDef, def.getName());
                    qsDef.setUpdateDelay(form.getUpdateDelay());
                    qsDef.setParticipantGroups(participantGroups);
                    qsDef.setOptionsId(snapshot.getRowId());

                    qsDef.save(user);

                    if (svc != null)
                    {
                        svc.createSnapshot(context, qsDef, datasetErrors);
                        if (datasetErrors.hasErrors())
                        {
                            StringBuilder sb = new StringBuilder();
                            for (ObjectError error : (List<ObjectError>)datasetErrors.getAllErrors())
                                sb.append(error.getDefaultMessage()).append('\n');

                            error(String.format("Unable to create dataset '%s' : %s", def.getName(), sb.toString()));
                            return;
                        }
                    }
                }
            }
        }
    }

    private void exportParticipantGroups(ChildStudyDefinition form, FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        List<ParticipantGroup> groupsToCopy = new ArrayList<ParticipantGroup>();
        if (form.isCopyParticipantGroups())
            groupsToCopy.addAll(_participantGroups);

        if (!groupsToCopy.isEmpty())
        {
            // query snapshots need to have participants created ahead of the snapshot
            if (!form.isPublish())
                ensureGroupParticipants();

            VirtualFile studyDir = vf.getDir("study");

            ParticipantGroupWriter groupWriter = new ParticipantGroupWriter();
            groupWriter.setGroupsToCopy(groupsToCopy);
            groupWriter.write(_sourceStudy, ctx.getContext(StudyExportContext.class), studyDir);
        }
    }

    private List<String> getGroupParticipants(ChildStudyDefinition form, StudyExportContext ctx)
    {
        if (!_participantGroups.isEmpty())
        {
            StudySchema schema = StudySchema.getInstance();

            StringBuilder groupInClause = new StringBuilder();
            String delim = "";

            groupInClause.append("(");
            for (ParticipantGroup group : _participantGroups)
            {
                groupInClause.append(delim);
                groupInClause.append(group.getRowId());

                delim = ",";
            }
            groupInClause.append(")");

            SQLFragment sql = new SQLFragment();

            sql.append(" SELECT DISTINCT(ParticipantId), ? FROM ").append(ParticipantGroupManager.getInstance().getTableInfoParticipantGroupMap(), "");
            sql.append(" WHERE GroupId IN ").append(groupInClause).append(" AND Container = ?");

            sql.add(_dstContainer.getId());
            sql.add(_sourceStudy.getContainer().getId());
            SqlSelector selector = new SqlSelector(schema.getSchema(), sql);

            if (form.isUseAlternateParticipantIds())
            {
                List<String> alternateIds = new ArrayList<String>();
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
        Set<String> ptids = new HashSet<String>();

        for (Specimen specimen : form.getSpecimens())
        {
            String ptid = specimen.getPtid();

            // PTID can be null
            if (null != ptid)
                ptids.add(specimen.getPtid());
        }

        return new LinkedList<String>(ptids);
    }

    /**
     * Participant groups cannot be created unless the subject id's exist in the participant table, participants are usually
     * added via dataset data insertion but we need to have them sooner because we need to filter the datasets on participant
     * group participants.
     */
    private void ensureGroupParticipants()
    {
        try
        {
            if (!_participantGroups.isEmpty())
            {
                StudySchema schema = StudySchema.getInstance();

                StringBuilder groupInClause = new StringBuilder();
                String delim = "";

                groupInClause.append("(");
                for (ParticipantGroup group : _participantGroups)
                {
                    groupInClause.append(delim);
                    groupInClause.append(group.getRowId());

                    delim = ",";
                }
                groupInClause.append(")");

                SQLFragment sql = new SQLFragment();

                sql.append("INSERT INTO ").append(schema.getTableInfoParticipant()).append(" (ParticipantId, Container)");
                sql.append(" SELECT DISTINCT(ParticipantId), ? FROM ").append(ParticipantGroupManager.getInstance().getTableInfoParticipantGroupMap(), "");
                sql.append(" WHERE GroupId IN ").append(groupInClause).append(" AND Container = ?");

                Table.execute(schema.getSchema(), sql.getSQL(), _dstContainer.getId(), _sourceStudy.getContainer().getId());
            }
        }
        catch (SQLException se)
        {
            throw new RuntimeException(se);
        }
    }
}
