/*
 * Copyright (c) 2012-2019 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.SpecimenTablesTemplate;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.study.SpecimenManager;
import org.labkey.study.StudyFolderType;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.CreateChildStudyPipelineJob;
import org.labkey.study.importer.SpecimenSchemaImporter;
import org.labkey.study.model.ChildStudyDefinition;
import org.labkey.study.model.SpecimenRequest;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Vial;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

import java.util.ArrayList;
import java.util.List;

/**
 * User: klum
 * Date: Sep 1, 2011
 * Time: 9:39:49 AM
 */

// Used to create ancillary studies, study snapshots, and specimen-based studies
@RequiresPermission(AdminPermission.class)
public class CreateChildStudyAction extends MutatingApiAction<ChildStudyDefinition>
{
    private static final Logger LOG = LogManager.getLogger(CreateChildStudyAction.class);
    public static final String CREATE_SPECIMEN_STUDY = "CreateSpecimenStudy";

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
            previousTablesTemplate = StudySchema.getInstance().setSpecimenTablesTemplates(new SpecimenSchemaImporter.ImportTemplate());
            StudyImpl newStudy = createNewStudy(form);

            if (newStudy != null)
            {
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
            else
                errors.reject(SpringActionController.ERROR_MSG, "Failed to create the destination study.");
        }
        finally
        {
            if (previousTablesTemplate != null)
                StudySchema.getInstance().setSpecimenTablesTemplates(previousTablesTemplate);
        }

        return resp;
    }

    @Override
    public void validateForm(ChildStudyDefinition form, Errors errors)
    {
        Container c = ContainerManager.getForPath(form.getDstPath());
        _destFolderCreated = c == null;

        // make sure the folder, if already existing doesn't already contain a study
        _dstContainer = ContainerManager.ensureContainer(Path.parse(form.getDstPath()), getUser());
        if (_dstContainer != null)
        {
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

        if (form.getMode() == null)
            errors.reject(SpringActionController.ERROR_MSG, "Unable to locate a study snapshot type from specified mode");

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

        if (null != form.getRequestId() || null != form.getSpecimenIds())
        {
            form.setIncludeSpecimens(true);
            SpecimenManager sm = SpecimenManager.getInstance();

            if (null != form.getRequestId())
            {
                SpecimenRequest request = sm.getRequest(sourceContainer, form.getRequestId());

                if (null == request)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Unable to locate specimen request with id " + form.getRequestId());
                }
                else
                {
                    List<Vial> vials = request.getVials();

                    if (0 == vials.size())
                        errors.reject(SpringActionController.ERROR_MSG, "Specimen request is empty");
                    else
                        form.setVials(vials);
                }
            }
            else
            {
                String[] specimenIds = form.getSpecimenIds();
                List<Vial> list = new ArrayList<>(specimenIds.length);
                User user = getUser();
                for (String specimenId : specimenIds)
                {
                    Vial vial = sm.getVial(sourceContainer, user, specimenId);

                    if (null == vial)
                        LOG.error("Specimen ID " + specimenId + " not found!!");
                    else
                        list.add(vial);
                }

                form.setVials(list);
            }
        }
    }

    private StudyImpl createNewStudy(ChildStudyDefinition form) throws ValidationException
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

        // This setting is specific to ancillary / publish study
        if (form.isUpdate())
        {
            study.setSourceStudyContainerId(_sourceStudy.getContainer().getId());
        }

        StudyManager.getInstance().createStudy(getUser(), study);

        // Set a default folder type. Will be overridden if user has chosen to copy from source.
        FolderType folderType = FolderTypeManager.get().getFolderType(StudyFolderType.NAME);
        _dstContainer.setFolderType(folderType, ModuleLoader.getInstance().getUpgradeUser());

        return study;
    }
}
