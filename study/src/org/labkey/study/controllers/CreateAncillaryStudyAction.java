/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
package org.labkey.study.controllers;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.Visit;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.MemoryVirtualFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudyFolderType;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.DatasetImporter;
import org.labkey.study.importer.MissingValueImporterFactory;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.importer.ParticipantGroupImporter;
import org.labkey.study.importer.QcStatesImporter;
import org.labkey.study.importer.VisitImporter;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.EmphasisStudyDefinition;
import org.labkey.study.model.ParticipantCategoryImpl;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.writer.CohortWriter;
import org.labkey.study.writer.DatasetWriter;
import org.labkey.study.writer.ParticipantGroupWriter;
import org.labkey.study.writer.QcStateWriter;
import org.labkey.study.writer.StudyExportContext;
import org.labkey.study.writer.StudyWriter;
import org.labkey.study.writer.ViewCategoryWriter;
import org.labkey.study.writer.VisitMapWriter;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Sep 1, 2011
 * Time: 9:39:49 AM
 */
@RequiresPermissionClass(AdminPermission.class)
public class CreateAncillaryStudyAction extends MutatingApiAction<EmphasisStudyDefinition>
{
    private Container _dstContainer;
    private StudyImpl _sourceStudy;
    private Set<DataSetDefinition> _datasets = new HashSet<DataSetDefinition>();
    private List<ParticipantGroup> _participantGroups = new ArrayList<ParticipantGroup>();
    private boolean _destFolderCreated;

    public CreateAncillaryStudyAction()
    {
        super();
        setContentTypeOverride("text/html");
    }

    @Override
    public ApiResponse execute(EmphasisStudyDefinition form, BindException errors) throws Exception
    {
        ApiSimpleResponse resp = new ApiSimpleResponse();

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        boolean success = false;
        try
        {
            // issue: 13706, study needs to be created outside of the transaction to avoid this race
            // condition
            StudyImpl newStudy = createNewStudy(form);

            scope.ensureTransaction();

            List<AttachmentFile> files = getAttachmentFileList();

            if (newStudy != null)
            {
                newStudy.attachProtocolDocument(files, getViewContext().getUser());
                // get the list of datasets to export
                for (int datasetId : form.getDatasets())
                {
                    DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getSourceStudy(), datasetId);

                    if (def != null)
                        _datasets.add(def);
                }

                for (int rowId : form.getGroups())
                {
                    ParticipantGroup pg = ParticipantGroupManager.getInstance().getParticipantGroup(_sourceStudy.getContainer(), getViewContext().getUser(), rowId);
                    if(pg !=null)
                        _participantGroups.add(pg);
                }

                // Force snapshots for datasets referenced by the parent study's visit map.  This ensures that
                // we'll be able to calculate visit dates for all data:
                for (Visit visit : getSourceStudy().getVisits(Visit.Order.SEQUENCE_NUM))
                {
                    if (visit.getVisitDateDatasetId() != null)
                    {
                        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getSourceStudy(), visit.getVisitDateDatasetId());
                        if (def != null)
                            _datasets.add(def);
                    }
                }

                MemoryVirtualFile vf = new MemoryVirtualFile();

                // export objects from the parent study, then import them into the new study
                exportFromParentStudy(form, errors, vf);
                importToDestinationStudy(form, errors, newStudy, vf);

                // copy participants
                copyParticipants(form, errors, newStudy, vf);

                // snapshot datasets
                createSnapshotDatasets(form, errors, newStudy);

                String redirect = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(_dstContainer).getLocalURIString();

                resp.put("redirect", redirect);
            }

            if (errors.hasErrors())
            {
                StringBuilder sb = new StringBuilder();
                for (ObjectError error : (List<ObjectError>)errors.getAllErrors())
                {
                    sb.append(error.getDefaultMessage()).append('\n');
                }
                throw new RuntimeException(sb.toString());
            }
            else
            {
                resp.put("success", true);
                scope.commitTransaction();
                success = true;
            }
        }
        finally
        {
            if (!success && _destFolderCreated)
                ContainerManager.delete(_dstContainer, getViewContext().getUser());
            
            scope.closeConnection();
        }

        return resp;
    }

    private StudyImpl getSourceStudy()
    {
        return _sourceStudy;
    }

    private void exportFromParentStudy(EmphasisStudyDefinition form, BindException errors, VirtualFile vf) throws Exception
    {
        User user = getViewContext().getUser();
        Set<String> dataTypes = new HashSet<String>();

        // export content from the parent study into a memory virtual file to avoid needing to serialize to disk
        // need a better way to add export types...

        dataTypes.add(CohortWriter.DATA_TYPE);
        dataTypes.add(QcStateWriter.DATA_TYPE);
        dataTypes.add(VisitMapWriter.DATA_TYPE);
        dataTypes.add(DatasetWriter.SELECTION_TEXT);
        dataTypes.add(ViewCategoryWriter.DATA_TYPE);

        StudyWriter writer = new StudyWriter();
        StudyExportContext ctx = new StudyExportContext(_sourceStudy, user, _sourceStudy.getContainer(), false, dataTypes, Logger.getLogger(StudyWriter.class));

        ctx.getDatasets().clear();
        ctx.getDatasets().addAll(_datasets);

        writer.write(getSourceStudy(), ctx, vf);
    }

    private void importToDestinationStudy(EmphasisStudyDefinition form, BindException errors, StudyImpl newStudy, VirtualFile vf) throws Exception
    {
        User user = getViewContext().getUser();
        XmlObject studyXml = vf.getXmlBean("study.xml");

        if (studyXml instanceof StudyDocument)
        {
            StudyDocument studyDoc = (StudyDocument)studyXml;
            StudyImportContext importContext = new StudyImportContext(user, newStudy.getContainer(), studyDoc, Logger.getLogger(StudyWriter.class), vf);

            // missing values and qc states
            new MissingValueImporterFactory().create().process(null, importContext, vf);
            new QcStatesImporter().process(importContext, vf, errors);

            // dataset definitions
            DatasetImporter datasetImporter = new DatasetImporter();
            datasetImporter.process(importContext, vf, errors);

            // import visits
            VisitImporter visitImporter = new VisitImporter();

            // don't create dataset definitions for datasets we don't import
            visitImporter.setEnsureDataSets(false);
            visitImporter.process(importContext, vf, errors);

            if (errors.hasErrors())
                throw new RuntimeException("Error importing study objects : " + errors.getMessage());
        }
    }

    private void createSnapshotDatasets(EmphasisStudyDefinition form, BindException errors, StudyImpl newStudy) throws Exception
    {
        User user = getViewContext().getUser();
        Container container = getViewContext().getContainer();

        QuerySnapshotService.I svc = QuerySnapshotService.get(StudyManager.getSchemaName());

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

                QuerySnapshotDefinition qsDef = QueryService.get().createQuerySnapshotDef(newStudy.getContainer(), queryDef, def.getLabel());
                qsDef.setUpdateDelay(form.getUpdateDelay());
                qsDef.setParticipantGroups(participantGroups);

                qsDef.save(user);

                if (svc != null)
                {
                    svc.createSnapshot(getViewContext(), qsDef, datasetErrors);
                    if (datasetErrors.hasErrors())
                    {
                        StringBuilder sb = new StringBuilder();
                        for (ObjectError error : (List<ObjectError>)datasetErrors.getAllErrors())
                            sb.append(error.getDefaultMessage()).append('\n');

                        errors.reject(SpringActionController.ERROR_MSG,
                                String.format("Unable to create dataset '%s' : %s", def.getName(), sb.toString()));

                        return;
                    }
                }
            }
        }
    }

    private void copyParticipants(EmphasisStudyDefinition form, BindException errors, StudyImpl newStudy, VirtualFile vf) throws Exception
    {
        User user = getViewContext().getUser();

        List<ParticipantGroup> groupsToCopy = new ArrayList<ParticipantGroup>();
        if (form.isCopyParticipantGroups())
            groupsToCopy.addAll(_participantGroups);

/*
        for (ParticipantGroupController.ParticipantCategorySpecification category : form.getCategories())
        {
            if (category.isNew())
            {
                category.setContainer(_dstContainer.getId());
                ParticipantGroupManager.getInstance().setParticipantCategory(_dstContainer, user, category, category.getParticipantIds());
            }
            else
            {
                // existing groups from the parent study, use export to copy them over
                ParticipantCategory pc = ParticipantGroupManager.getInstance().getParticipantCategory(_sourceStudy.getContainer(), user, category.getRowId());
                if (pc != null)
                    categoriesToCopy.add(pc);
            }
        }
*/

        if (!groupsToCopy.isEmpty())
        {
            ensureGroupParticipants();
            
            Set<String> dataTypes = new HashSet<String>();
            dataTypes.add(ParticipantGroupWriter.DATA_TYPE);
            StudyExportContext ctx = new StudyExportContext(_sourceStudy, user, _sourceStudy.getContainer(), false, dataTypes, Logger.getLogger(ParticipantGroupWriter.class));

            ParticipantGroupWriter groupWriter = new ParticipantGroupWriter();

            groupWriter.setGroupsToCopy(groupsToCopy);
            groupWriter.write(_sourceStudy, ctx, vf);

            XmlObject studyXml = vf.getXmlBean("study.xml");

            if (studyXml instanceof StudyDocument)
            {
                StudyDocument studyDoc = (StudyDocument)studyXml;
                StudyImportContext importContext = new StudyImportContext(user, newStudy.getContainer(), studyDoc, Logger.getLogger(StudyWriter.class), vf);

                ParticipantGroupImporter groupImporter = new ParticipantGroupImporter();
                groupImporter.process(importContext, vf, errors);
            }
        }

        if (errors.hasErrors())
            throw new RuntimeException("Error copying participant groups : " + errors.getMessage());
    }

    /**
     * Participant groups cannot be created unless the subject id's exist in the participant table, participants are usually
     * added via dataset data insertion but we need to have them sooner because we need to filter the datasets on participant
     * group participants.
     */
    private void ensureGroupParticipants()
    {
        try {
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

    @Override
    public void validateForm(EmphasisStudyDefinition form, Errors errors)
    {
        Container c = ContainerManager.getForPath(form.getDstPath());
        _destFolderCreated = c == null;

        // make sure the folder, if already existing doesn't already contain a study

        _dstContainer = ContainerManager.ensureContainer(form.getDstPath());
        if (_dstContainer != null)
        {
            //Container
            Study study = StudyManager.getInstance().getStudy(_dstContainer);
            if (study != null)
            {
                errors.reject(SpringActionController.ERROR_MSG, "A study already exists in the destination folder.");
            }
        }
        else
            errors.reject(SpringActionController.ERROR_MSG, "Invalid destination folder.");

        Container sourceContainer = ContainerManager.getForPath(form.getSrcPath());
        _sourceStudy = StudyManager.getInstance().getStudy(sourceContainer);

        if (_sourceStudy == null)
            errors.reject(SpringActionController.ERROR_MSG, "Unable to locate the parent study from location : " + form.getSrcPath());

        // work around for IE bug (13242), in ext 3.4 posting using a basic form will not call the failure handler if the status code is 400
        if (errors.hasErrors())
        {
            StringBuilder sb = new StringBuilder();
            String delim = "";
            for (Object error : errors.getAllErrors())
            {
                sb.append(delim);
                sb.append(((ObjectError)error).getDefaultMessage());

                delim = "\n";
            }
            throw new IllegalArgumentException(sb.toString());
        }
    }

    private StudyImpl createNewStudy(EmphasisStudyDefinition form) throws SQLException
    {
        StudyImpl study = new StudyImpl(_dstContainer, form.getName());

        // new studies should default to read only
        SecurityType securityType = _sourceStudy.getSecurityType();
        switch (_sourceStudy.getSecurityType())
        {
            case BASIC_WRITE:
                securityType = SecurityType.BASIC_READ;
                break;
            case ADVANCED_WRITE:
                securityType = SecurityType.ADVANCED_READ;
                break;
        }
        study.setTimepointType(_sourceStudy.getTimepointType());
        study.setStartDate(_sourceStudy.getStartDate());
        study.setSecurityType(securityType);
        Container sourceContainer = ContainerManager.getForPath(form.getSrcPath());
        study.setSourceStudyContainerId(sourceContainer.getId());
        study.setSubjectNounSingular(_sourceStudy.getSubjectNounSingular());
        study.setSubjectNounPlural(_sourceStudy.getSubjectNounPlural());
        study.setSubjectColumnName(_sourceStudy.getSubjectColumnName());
        study.setDescription(form.getDescription());

        StudyManager.getInstance().createStudy(getViewContext().getUser(), study);

        FolderType folderType = ModuleLoader.getInstance().getFolderType(StudyFolderType.NAME);
        _dstContainer.setFolderType(folderType, ModuleLoader.getInstance().getUpgradeUser());

        

        return study;
    }
}
