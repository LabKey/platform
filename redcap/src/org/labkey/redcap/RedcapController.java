/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.redcap;

import org.apache.xmlbeans.XmlException;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.study.StudyReloadSource;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.study.xml.redcapExport.RedcapConfigDocument;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 1/16/2015.
 */
public class RedcapController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(RedcapController.class);

    public RedcapController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ConfigureAction extends SimpleViewAction<RedcapManager.RedcapSettings>
    {
        @Override
        public ModelAndView getView(RedcapManager.RedcapSettings form, BindException errors) throws Exception
        {
            if (Encryption.isMasterEncryptionPassPhraseSpecified())
            {
                form = RedcapManager.getRedcapSettings(getViewContext());
                return new JspView<>("/org/labkey/redcap/view/configure.jsp", form, errors);
            }
            else
            {
                return new HtmlView("<span class='labkey-error'>Unable to save or retrieve configuration information, MasterEncryptionKey has not been specified in labkey.xml.</span>");
            }
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("redCap");
            return root.addChild("REDCap Configuration");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SaveRedcapConfig extends ApiAction<RedcapManager.RedcapSettings>
    {
        @Override
        public void validateForm(RedcapManager.RedcapSettings form, Errors errors)
        {
            // validate any metadata
            String metadata = form.getMetadata();
            if (metadata != null)
            {
                try
                {
                    RedcapConfigDocument doc = RedcapConfigDocument.Factory.parse(metadata, XmlBeansUtil.getDefaultParseOptions());
                    Set<String> projectsNames = new HashSet<>();

                    for (String project : form.getProjectname())
                        projectsNames.add(project);

                    for (RedcapConfigDocument.RedcapConfig.Projects.Project p : doc.getRedcapConfig().getProjects().getProjectArray())
                    {
                        if (!projectsNames.contains(p.getProjectName()))
                        {
                            errors.reject(ERROR_MSG, "The the project : " + p.getProjectName() + " exists in the configuration info, but there isn't an entry " +
                                    "for that project in the connection section. Please add an entry before saving.");
                        }
                    }
                }
                catch (XmlException e)
                {
                    errors.reject(ERROR_MSG, "The metadata submitted was malformed. The following error was returned : " + e.getMessage());
                }
            }
        }

        @Override
        public ApiResponse execute(RedcapManager.RedcapSettings form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            RedcapManager.saveRedcapSettings(getViewContext(), form);

            response.put("success", true);
            response.put("returnUrl", PageFlowUtil.urlProvider(StudyUrls.class).getManageStudyURL(getContainer()).getLocalURIString());

            return response;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ReloadRedcap extends ApiAction<RedcapManager.RedcapSettings>
    {
        @Override
        public ApiResponse execute(RedcapManager.RedcapSettings form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            try
            {
                StudyReloadSource reloadSource = StudyService.get().getStudyReloadSource(RedcapReloadSource.NAME);

                PipelineJob job = StudyService.get().createReloadSourceJob(getContainer(), getUser(), reloadSource, getViewContext().getActionURL());
                PipelineService.get().queueJob(job);

                response.put("success", true);
                response.put("returnUrl", PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer()));
            }
            catch (PipelineValidationException e)
            {
                throw new IOException(e);
            }
            return response;
        }
    }
}
