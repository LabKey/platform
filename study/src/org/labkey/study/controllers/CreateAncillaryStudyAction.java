package org.labkey.study.controllers;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Study;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.Archive;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudyFolderType;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.ImportContext;
import org.labkey.study.importer.ParticipantGroupImporter;
import org.labkey.study.importer.VisitImporter;
import org.labkey.study.model.EmphasisStudyDefinition;
import org.labkey.study.model.ParticipantCategory;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.writer.DatasetWriter;
import org.labkey.study.writer.ParticipantGroupWriter;
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

    @Override
    public ApiResponse execute(EmphasisStudyDefinition form, BindException errors) throws Exception
    {
        ApiSimpleResponse resp = new ApiSimpleResponse();

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try
        {
            scope.ensureTransaction();

            StudyImpl newStudy = createNewStudy(form);
            if (newStudy != null)
            {
                MemoryVirtualFile vf = new MemoryVirtualFile("");

                exportFromParentStudy(form, errors, vf);

                // import content from the parent study into the new study
                importToDestinationStudy(form, errors, newStudy, vf);

                // copy participants
                copyParticipants(form, errors, newStudy, vf);
                String redirect = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(_dstContainer).getLocalURIString();

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

    private void exportFromParentStudy(EmphasisStudyDefinition form, BindException errors, VirtualFile vf) throws Exception
    {
        User user = getViewContext().getUser();
        Set<String> dataTypes = new HashSet<String>();

        // export conten from the parent study into a memory virtual file to avoid needing to serialize to disk
        // need a better way to add types...

        dataTypes.add(VisitMapWriter.DATA_TYPE);
        dataTypes.add(ParticipantGroupWriter.DATA_TYPE);
        dataTypes.add(DatasetWriter.SELECTION_TEXT);

        StudyWriter writer = new StudyWriter();
        StudyExportContext ctx = new StudyExportContext(_parentStudy, user, _parentStudy.getContainer(), false, dataTypes, Logger.getLogger(StudyWriter.class));

        writer.write(_parentStudy, ctx, vf);
    }

    private void importToDestinationStudy(EmphasisStudyDefinition form, BindException errors, StudyImpl newStudy, VirtualFile vf) throws Exception
    {
        User user = getViewContext().getUser();
        XmlObject studyXml = vf.getXmlBean("study.xml");

        if (studyXml instanceof StudyDocument)
        {
            StudyDocument studyDoc = (StudyDocument)studyXml;
            ImportContext importContext = new ImportContext(user, _parentStudy.getContainer(), studyDoc, Logger.getLogger(StudyWriter.class));

            // import visits
            VisitImporter visitImporter = new VisitImporter();
            visitImporter.process(newStudy, importContext, vf, errors);

            // datasets (temporary)
/*
            DatasetImporter datasetImporter = new DatasetImporter();
            datasetImporter.process(newStudy, importContext, vf, errors);
*/
        }
    }

    private void copyParticipants(EmphasisStudyDefinition form, BindException errors, StudyImpl newStudy, VirtualFile vf) throws Exception
    {
        User user = getViewContext().getUser();

        List<ParticipantCategory> categoriesToCopy = new ArrayList<ParticipantCategory>();
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

        if (!categoriesToCopy.isEmpty())
        {
            Set<String> dataTypes = new HashSet<String>();
            dataTypes.add(ParticipantGroupWriter.DATA_TYPE);
            StudyExportContext ctx = new StudyExportContext(_parentStudy, user, _parentStudy.getContainer(), false, dataTypes, Logger.getLogger(ParticipantGroupWriter.class));

            ParticipantGroupWriter groupWriter = new ParticipantGroupWriter();

            groupWriter.setCategoriesToCopy(categoriesToCopy);
            groupWriter.write(_parentStudy, ctx, vf);

            ParticipantGroupImporter groupImporter = new ParticipantGroupImporter();
            XmlObject groupDoc = vf.getXmlBean(ParticipantGroupWriter.FILE_NAME);
            if (groupDoc != null)
            {
                //ImportContext importCtx = new ImportContext(user, _dstContainer, null, Logger.getLogger(CreateAncillaryStudyAction.class));
                //groupImporter.process(newStudy, importContext, groupDoc);
            }
        }
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

        study.setTimepointType(_parentStudy.getTimepointType());
        study.setStartDate(_parentStudy.getStartDate());
        study.setSecurityType(_parentStudy.getSecurityType());
        study.setSubjectNounSingular(_parentStudy.getSubjectNounSingular());
        study.setSubjectNounPlural(_parentStudy.getSubjectNounPlural());
        study.setSubjectColumnName(_parentStudy.getSubjectColumnName());

        StudyManager.getInstance().createStudy(getViewContext().getUser(), study);

        _dstContainer.setFolderType(ModuleLoader.getInstance().getFolderType(StudyFolderType.NAME));

        return study;
    }

    private static class MemoryVirtualFile implements VirtualFile
    {
        private String _root;
        private Map<String, XmlObject> _docMap = new HashMap<String, XmlObject>();
        private Map<String, StringWriter> _textDocMap = new HashMap<String, StringWriter>();
        private Map<String, MemoryVirtualFile> _folders = new HashMap<String, MemoryVirtualFile>();

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
