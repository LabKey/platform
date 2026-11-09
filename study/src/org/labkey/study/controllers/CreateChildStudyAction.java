/*
 * Copyright (c) 2011-2026 LabKey Corporation
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

import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractContainerScopingTest;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.specimen.importer.ImportTemplate;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudySnapshotType;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudyFolderType;
import org.labkey.study.importer.CreateChildStudyPipelineJob;
import org.labkey.study.model.ChildStudyDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

import java.util.List;

// Used to create published studies
@RequiresPermission(AdminPermission.class)
public class CreateChildStudyAction extends MutatingApiAction<ChildStudyDefinition>
{
    private Container _dstContainer;
    private StudyImpl _sourceStudy;
    private boolean _destFolderCreated;

    public CreateChildStudyAction()
    {
        super();
        setContentTypeOverride("text/html");
    }

    @Override
    public ApiResponse execute(ChildStudyDefinition form, BindException errors) throws Exception
    {
        ApiSimpleResponse resp = new ApiSimpleResponse();

        SpecimenTablesTemplate previousTablesTemplate = null;
        try
        {
            // Need to set optional fields to null, or user-added metadata on those fields won't be copied over properly
            previousTablesTemplate = SpecimenSchema.get().setSpecimenTablesTemplates(new ImportTemplate());
            StudyImpl newStudy = createNewStudy(form, errors);

            List<AttachmentFile> files = getAttachmentFileList();
            newStudy.attachProtocolDocument(files, getUser());

            // run the remainder of the study creation as a pipeline job
            PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
            CreateChildStudyPipelineJob job = new CreateChildStudyPipelineJob(getViewContext(), root, form, _destFolderCreated);
            PipelineService.get().queueJob(job);

            String redirect = PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer()).getLocalURIString();

            resp.put("redirect", redirect);
            resp.put("success", true);
        }
        finally
        {
            if (previousTablesTemplate != null)
                SpecimenSchema.get().setSpecimenTablesTemplates(previousTablesTemplate);
        }

        return resp;
    }

    @Override
    public void validateForm(ChildStudyDefinition form, Errors errors)
    {
        // Verify the user can read the source study and can administer the destination (or its parent, if the
        // folder must be created) before we create any folder or queue the copy job.
        Container sourceContainer = form.getSrcPath() != null ? ContainerManager.getForPath(form.getSrcPath()) : null;
        if (sourceContainer == null || !sourceContainer.hasPermission(getUser(), ReadPermission.class))
        {
            errors.reject(SpringActionController.ERROR_MSG, "Unable to locate the parent study from location : " + form.getSrcPath());
        }
        else
        {
            _sourceStudy = StudyManager.getInstance().getStudy(sourceContainer);
            if (_sourceStudy == null)
                errors.reject(SpringActionController.ERROR_MSG, "Unable to locate the parent study from location : " + form.getSrcPath());
        }

        Container existingDst = form.getDstPath() != null ? ContainerManager.getForPath(form.getDstPath()) : null;
        _destFolderCreated = existingDst == null;

        boolean dstAuthorized;
        if (existingDst != null)
        {
            // Existing destination folder: require Admin on it.
            dstAuthorized = existingDst.hasPermission(getUser(), AdminPermission.class);
        }
        else if (form.getDstPath() != null)
        {
            // Folder will be created: require Admin on the parent so an arbitrary folder can't be grafted in.
            Container dstParent = ContainerManager.getForPath(Path.parse(form.getDstPath()).getParent());
            dstAuthorized = dstParent != null && dstParent.hasPermission(getUser(), AdminPermission.class);
        }
        else
        {
            dstAuthorized = false;
        }

        if (!dstAuthorized)
        {
            errors.reject(SpringActionController.ERROR_MSG, "Invalid destination folder.");
        }
        // Only create the destination folder once the rest of the form (including the source-read check above) has
        // validated, so a rejected publish doesn't leave a stray empty folder behind.
        else if (!errors.hasErrors())
        {
            // make sure the folder, if already existing doesn't already contain a study
            _dstContainer = ContainerManager.ensureContainer(Path.parse(form.getDstPath()), getUser());
            Study study = StudyManager.getInstance().getStudy(_dstContainer);
            if (study != null)
                errors.reject(SpringActionController.ERROR_MSG, "A study already exists in the destination folder.");
        }

        if (form.getMode() == null)
            errors.reject(SpringActionController.ERROR_MSG, "Unable to locate a study snapshot type from specified mode");

        // work around for IE bug (13242), in ext 3.4 posting using a basic form will not call the failure handler if the status code is 400
        if (errors.hasErrors())
        {
            StringBuilder sb = new StringBuilder();
            String delim = "";
            for (ObjectError error : errors.getAllErrors())
            {
                sb.append(delim);
                sb.append(error.getDefaultMessage());

                delim = "\n";
            }
            throw new IllegalArgumentException(sb.toString());
        }
    }

    @NotNull
    private StudyImpl createNewStudy(ChildStudyDefinition form, BindException errors) throws ValidationException
    {
        // Minimum set of properties needed to create a study (due to NOT NULL constraints). All other study properties are
        // round-tripped from the source study by StudyXmlWriter and TopLevelStudyPropertiesImporter to ensure consistency
        // with export/import, create from template, etc. #35422
        StudyImpl study = new StudyImpl(_dstContainer, null);
        TimepointType timepointType = _sourceStudy.getTimepointType();
        if (form.getTimepointType() != null)
        {
            try
            {
                timepointType = TimepointType.valueOf(form.getTimepointType());
            }
            catch (IllegalArgumentException ignored) {}
        }
        _sourceStudy.getTimepointType().validateTransition(timepointType);
        study.setTimepointType(timepointType);
        study.setSubjectNounSingular(_sourceStudy.getSubjectNounSingular());
        study.setSubjectNounPlural(_sourceStudy.getSubjectNounPlural());
        study.setSubjectColumnName(_sourceStudy.getSubjectColumnName());

        // This setting is specific to publish study
        if (form.isUpdate())
        {
            study.setSourceStudyContainerId(_sourceStudy.getContainer().getId());
        }

        StudyManager.getInstance().createStudy(getUser(), study);

        // Set a default folder type. Will be overridden if user has chosen to copy from source.
        FolderType folderType = FolderTypeManager.get().getFolderType(StudyFolderType.NAME);
        _dstContainer.setFolderType(folderType, User.getSearchUser(), errors);

        return study;
    }

    public static class ContainerScopingTestCase extends AbstractContainerScopingTest
    {
        private Container _source;

        @Before
        public void createSourceStudy()
        {
            // A real study in the source folder, owned by the site admin
            _source = createContainer("Source");
            StudyImpl study = new StudyImpl(_source, "Source Study");
            study.setTimepointType(TimepointType.VISIT);
            StudyManager.getInstance().createTestStudy(getAdmin(), study);
        }

        @Test
        public void testPublishRequiresSourceRead() throws Exception
        {
            // A user who is a folder admin in the destination only (no rights in the source)
            Container dest = createContainer("Dest");
            User destAdminOnly = createUserInRole(dest, FolderAdminRole.class);

            MockHttpServletResponse resp = publish(dest, dest.getPath(), destAdminOnly);

            // The publish must not succeed because the user can't read the source study
            assertNotEquals("Publish from an unreadable source study must not succeed", HttpServletResponse.SC_OK, resp.getStatus());
            assertNull("No study should have been created in the destination", StudyManager.getInstance().getStudy(dest));
            assertSourceUntouched();

            // Positive scenario covered by StudyPublishTest
        }

        @Test
        public void testPublishToNewFolderRequiresParentAdmin() throws Exception
        {
            // A user who is a folder admin in the source only
            Container parent = createContainer("Parent");
            User sourceAdminOnly = createUserInRole(_source, FolderAdminRole.class);

            // The destination folder does not exist yet, so authorization falls to the dstParent branch
            String newDstPath = parent.getPath() + "/PublishedChild";
            MockHttpServletResponse resp = publish(_source, newDstPath, sourceAdminOnly);

            // The publish must not succeed because the user can't administer the destination's parent
            assertNotEquals("Publishing into a new folder under an unadministered parent must not succeed", HttpServletResponse.SC_OK, resp.getStatus());
            // The guard runs before ensureContainer(), so no destination folder must have been grafted in
            assertNull("No destination folder should have been created under the parent", ContainerManager.getForPath(Path.parse(newDstPath)));
            assertSourceUntouched();

            // Positive scenario (a parent admin creating the child folder) covered by StudyPublishTest
        }

        // Posts a publish of _source into dstPath, dispatched through requestContainer (which is the container the
        // action's @RequiresPermission is evaluated against).
        private MockHttpServletResponse publish(Container requestContainer, String dstPath, User user) throws Exception
        {
            ActionURL url = new ActionURL(CreateChildStudyAction.class, requestContainer)
                    .addParameter("srcPath", _source.getPath())
                    .addParameter("dstPath", dstPath)
                    .addParameter("mode", StudySnapshotType.publish.name());
            return post(url, user);
        }

        private void assertSourceUntouched()
        {
            assertNotNull("The source study must be untouched", StudyManager.getInstance().getStudy(_source));
        }
    }
}
