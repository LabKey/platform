/*
 * Copyright (c) 2011 LabKey Corporation
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
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.Archive;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudyFolderType;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.DatasetImporter;
import org.labkey.study.importer.ImportContext;
import org.labkey.study.importer.MissingValueImporter;
import org.labkey.study.importer.ParticipantGroupImporter;
import org.labkey.study.importer.QcStatesImporter;
import org.labkey.study.importer.VisitImporter;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.EmphasisStudyDefinition;
import org.labkey.study.model.ParticipantCategory;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DataSetQueryView;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.writer.CohortWriter;
import org.labkey.study.writer.DatasetWriter;
import org.labkey.study.writer.ParticipantGroupWriter;
import org.labkey.study.writer.QcStateWriter;
import org.labkey.study.writer.StudyExportContext;
import org.labkey.study.writer.StudyWriter;
import org.labkey.study.writer.VisitMapWriter;
import org.labkey.study.xml.StudyDocument;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private StudyImpl _parentStudy;
    private List<DataSetDefinition> _datasets = new ArrayList<DataSetDefinition>();
    private List<ParticipantCategory> _participantCategories = new ArrayList<ParticipantCategory>();

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
        try
        {
            scope.ensureTransaction();

            List<AttachmentFile> files = getAttachmentFileList();

            StudyImpl newStudy = createNewStudy(form);
            if (newStudy != null)
            {
                newStudy.attachProtocolDocument(files, getViewContext().getUser());
                // get the list of datasets to export
                for (int datasetId : form.getDatasets())
                {
                    DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getParentStudy(), datasetId);

                    if (def != null)
                        _datasets.add(def);
                }

                for (int rowId : form.getCategories())
                {
                    ParticipantCategory pc = ParticipantGroupManager.getInstance().getParticipantCategory(_parentStudy.getContainer(), getViewContext().getUser(), rowId);
                    if (pc != null)
                        _participantCategories.add(pc);
                }

                MemoryVirtualFile vf = new MemoryVirtualFile();

                // export objects from the parent study, then import then into the new study
                exportFromParentStudy(form, errors, vf);
                importToDestinationStudy(form, errors, newStudy, vf);

                // snapshot datasets
                createSnapshotDatasets(form, errors, newStudy);
                
                // copy participants
                copyParticipants(form, errors, newStudy, vf);
                String redirect = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(_dstContainer).getLocalURIString();

                resp.put("redirect", redirect);
                resp.put("success", true);
            }
            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }

        return resp;
    }

    private StudyImpl getParentStudy()
    {
        return _parentStudy;
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

        StudyWriter writer = new StudyWriter();
        StudyExportContext ctx = new StudyExportContext(_parentStudy, user, _parentStudy.getContainer(), false, dataTypes, Logger.getLogger(StudyWriter.class));

        ctx.getDatasets().clear();
        ctx.getDatasets().addAll(_datasets);

        writer.write(getParentStudy(), ctx, vf);
    }

    private void importToDestinationStudy(EmphasisStudyDefinition form, BindException errors, StudyImpl newStudy, VirtualFile vf) throws Exception
    {
        User user = getViewContext().getUser();
        XmlObject studyXml = vf.getXmlBean("study.xml");

        if (studyXml instanceof StudyDocument)
        {
            StudyDocument studyDoc = (StudyDocument)studyXml;
            ImportContext importContext = new ImportContext(user, newStudy.getContainer(), studyDoc, Logger.getLogger(StudyWriter.class));

            // missing values and qc states
            new MissingValueImporter().process(newStudy, importContext, vf, errors);
            new QcStatesImporter().process(newStudy, importContext, vf, errors);

            // dataset definitions
            DatasetImporter datasetImporter = new DatasetImporter();
            datasetImporter.process(newStudy, importContext, vf, errors);

            // import visits
            VisitImporter visitImporter = new VisitImporter();

            // don't create dataset definitions for datasets we don't import
            visitImporter.setEnsureDataSets(false);
            visitImporter.process(newStudy, importContext, vf, errors);

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
        for (ParticipantCategory category : _participantCategories)
        {
            participantGroups.add(category.getRowId());
        }

        for (DataSetDefinition def : _datasets)
        {
            StudyQuerySchema schema = new StudyQuerySchema(getParentStudy(), user, true);
            TableInfo table = def.getTableInfo(user);
            QueryDefinition queryDef = QueryService.get().createQueryDefForTable(schema, table.getName());

            if (queryDef != null)
            {
                queryDef.setContainer(getParentStudy().getContainer());

                QuerySnapshotDefinition qsDef = QueryService.get().createQuerySnapshotDef(newStudy.getContainer(), queryDef, def.getLabel());
                qsDef.setUpdateDelay(form.getUpdateDelay());
                qsDef.setParticipantGroups(participantGroups);

                qsDef.save(user);

                if (svc != null)
                {
                    svc.createSnapshot(getViewContext(), qsDef, errors);
                    if (errors.hasErrors())
                        throw new RuntimeException("Error copying the dataset data : " + errors.getMessage());
                }
            }
        }
    }

    private void copyParticipants(EmphasisStudyDefinition form, BindException errors, StudyImpl newStudy, VirtualFile vf) throws Exception
    {
        User user = getViewContext().getUser();

        List<ParticipantCategory> categoriesToCopy = new ArrayList<ParticipantCategory>();
        if (form.isCopyParticipantGroups())
            categoriesToCopy.addAll(_participantCategories);

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
                ParticipantCategory pc = ParticipantGroupManager.getInstance().getParticipantCategory(_parentStudy.getContainer(), user, category.getRowId());
                if (pc != null)
                    categoriesToCopy.add(pc);
            }
        }
*/

        if (!categoriesToCopy.isEmpty())
        {
            Set<String> dataTypes = new HashSet<String>();
            dataTypes.add(ParticipantGroupWriter.DATA_TYPE);
            StudyExportContext ctx = new StudyExportContext(_parentStudy, user, _parentStudy.getContainer(), false, dataTypes, Logger.getLogger(ParticipantGroupWriter.class));

            ParticipantGroupWriter groupWriter = new ParticipantGroupWriter();

            groupWriter.setCategoriesToCopy(categoriesToCopy);
            groupWriter.write(_parentStudy, ctx, vf);

            XmlObject studyXml = vf.getXmlBean("study.xml");

            if (studyXml instanceof StudyDocument)
            {
                StudyDocument studyDoc = (StudyDocument)studyXml;
                ImportContext importContext = new ImportContext(user, newStudy.getContainer(), studyDoc, Logger.getLogger(StudyWriter.class));

                ParticipantGroupImporter groupImporter = new ParticipantGroupImporter();
                groupImporter.process(newStudy, importContext, vf, errors);
            }
        }

        if (errors.hasErrors())
            throw new RuntimeException("Error copying participant groups : " + errors.getMessage());
    }

    @Override
    public void validateForm(EmphasisStudyDefinition form, Errors errors)
    {
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
        _parentStudy = StudyManager.getInstance().getStudy(sourceContainer);

        if (_parentStudy == null)
            errors.reject(SpringActionController.ERROR_MSG, "Unable to locate the parent study from location : " + form.getSrcPath());
    }

    private StudyImpl createNewStudy(EmphasisStudyDefinition form) throws SQLException
    {
        StudyImpl study = new StudyImpl(_dstContainer, form.getName());

        // new studies should default to read only
        SecurityType securityType = _parentStudy.getSecurityType();
        switch (_parentStudy.getSecurityType())
        {
            case BASIC_WRITE:
                securityType = SecurityType.BASIC_READ;
                break;
            case ADVANCED_WRITE:
                securityType = SecurityType.ADVANCED_READ;
                break;
        }
        study.setTimepointType(_parentStudy.getTimepointType());
        study.setStartDate(_parentStudy.getStartDate());
        study.setSecurityType(securityType);
        study.setSubjectNounSingular(_parentStudy.getSubjectNounSingular());
        study.setSubjectNounPlural(_parentStudy.getSubjectNounPlural());
        study.setSubjectColumnName(_parentStudy.getSubjectColumnName());
        study.setDescription(form.getDescription());

        StudyManager.getInstance().createStudy(getViewContext().getUser(), study);

        FolderType folderType = ModuleLoader.getInstance().getFolderType(StudyService.STUDY_REDESIGN_FOLDER_TYPE_NAME_CHAVI);
        // We may not have the study redesign module installed:
        if (folderType == null)
            folderType = ModuleLoader.getInstance().getFolderType(StudyFolderType.NAME);
        _dstContainer.setFolderType(folderType);

        return study;
    }

    private static class MemoryVirtualFile implements VirtualFile
    {
        private String _root;
        private Map<String, XmlObject> _docMap = new HashMap<String, XmlObject>();
        private Map<String, StringWriter> _textDocMap = new HashMap<String, StringWriter>();
        private Map<String, MemoryVirtualFile> _folders = new HashMap<String, MemoryVirtualFile>();

        public MemoryVirtualFile()
        {
            this("");
        }

        public MemoryVirtualFile(String root)
        {
            _root = root;
        }

        @Override
        public PrintWriter getPrintWriter(String path) throws IOException
        {
            StringWriter writer = new StringWriter();
            _textDocMap.put(path, writer);

            return new PrintWriter(writer);
        }

        @Override
        public OutputStream getOutputStream(String filename) throws IOException
        {
            throw new UnsupportedOperationException("getOutputStream not supported by memory virtual files");
        }

        @Override
        public InputStream getInputStream(String filename) throws IOException
        {
            XmlObject doc = _docMap.get(filename);
            if (doc != null)
                return doc.newInputStream(XmlBeansUtil.getDefaultSaveOptions());

            StringWriter writer = _textDocMap.get(filename);
            if (writer != null)
            {
                String contents = writer.getBuffer().toString();
                return new BufferedInputStream(new ByteArrayInputStream(contents.getBytes()));
            }
            
            return null;
        }

        @Override
        public void saveXmlBean(String filename, XmlObject doc) throws IOException
        {
            _docMap.put(filename, doc);
        }

        @Override
        public Archive createZipArchive(String name) throws IOException
        {
            throw new UnsupportedOperationException("Creating zip archives is not supported by memory virtual files");
        }

        @Override
        public VirtualFile getDir(String path)
        {
            if (!_folders.containsKey(path))
            {
                _folders.put(path, new MemoryVirtualFile(path));
            }
            return _folders.get(path);
        }

        @Override
        public String makeLegalName(String name)
        {
            return FileUtil.makeLegalName(name);
        }

        @Override
        public String getLocation()
        {
            return "memoryVirtualFile";
        }

        public XmlObject getDoc(String filename)
        {
            return _docMap.get(filename);
        }

        @Override
        public XmlObject getXmlBean(String filename) throws IOException
        {
            return _docMap.get(filename);
        }

        @Override
        public String getRelativePath(String filename)
        {
            return _root + File.separator + filename;
        }
    }
}
