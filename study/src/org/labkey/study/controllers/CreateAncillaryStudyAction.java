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

import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Study;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.StudyFolderType;
import org.labkey.study.importer.PublishStudyPipelineJob;
import org.labkey.study.model.EmphasisStudyDefinition;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

import java.sql.SQLException;
import java.util.List;

/**
 * User: klum
 * Date: Sep 1, 2011
 * Time: 9:39:49 AM
 */
@RequiresPermissionClass(AdminPermission.class)
public class CreateAncillaryStudyAction extends MutatingApiAction<EmphasisStudyDefinition>
{
    private Container _dstContainer;
    private StudyImpl _sourceStudy;
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
        StudyImpl newStudy = createNewStudy(form);

        if (newStudy != null)
        {
            List<AttachmentFile> files = getAttachmentFileList();
            newStudy.attachProtocolDocument(files, getViewContext().getUser());

            // run the remainder of the study creation as a pipeline job
            PipeRoot root = PipelineService.get().findPipelineRoot(getViewContext().getContainer());
            PublishStudyPipelineJob job = new PublishStudyPipelineJob(getViewContext(), root, form, _destFolderCreated);
            PipelineService.get().getPipelineQueue().addJob(job);

            String redirect = PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getViewContext().getContainer()).getLocalURIString();

            resp.put("redirect", redirect);
            resp.put("success", true);
        }
        else
            errors.reject(SpringActionController.ERROR_MSG, "Failed to create the destination study.");

        return resp;
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
        if (!form.isPublish())
        {
            study.setSourceStudyContainerId(sourceContainer.getId());
        }
        study.setSubjectNounSingular(_sourceStudy.getSubjectNounSingular());
        study.setSubjectNounPlural(_sourceStudy.getSubjectNounPlural());
        study.setSubjectColumnName(_sourceStudy.getSubjectColumnName());
        study.setAlternateIdPrefix(_sourceStudy.getAlternateIdPrefix());
        study.setAlternateIdDigits(_sourceStudy.getAlternateIdDigits());
        study.setDescription(form.getDescription());

        StudyManager.getInstance().createStudy(getViewContext().getUser(), study);

        FolderType folderType = ModuleLoader.getInstance().getFolderType(StudyFolderType.NAME);
        _dstContainer.setFolderType(folderType, ModuleLoader.getInstance().getUpgradeUser());

        return study;
    }
}
