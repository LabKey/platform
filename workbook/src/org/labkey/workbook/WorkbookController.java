/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.workbook;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.*;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.workbook.view.CreateWorkbookView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

public class WorkbookController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(WorkbookController.class);

    public WorkbookController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends RedirectAction
    {
        public ActionURL getSuccessURL(Object o)
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(getContainer());
        }

        public boolean doAction(Object o, BindException errors) throws Exception
        {
            return true;
        }

        public void validateCommand(Object target, Errors errors)
        {
            return;
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class CreateWorkbookAction extends FormViewAction<CreateWorkbookForm>
    {
        Container _newContainer;

        public void validateCommand(CreateWorkbookForm target, Errors errors)
        {
            StringBuilder nameError = new StringBuilder();
            if(!Container.isLegalName(StringUtils.trimToEmpty(target.getName()), nameError))
                errors.reject(nameError.toString());
        }

        public ModelAndView getView(CreateWorkbookForm createWorkbookForm, boolean reshow, BindException errors) throws Exception
        {
            return new CreateWorkbookView(createWorkbookForm, errors);
        }

        public boolean handlePost(CreateWorkbookForm createWorkbookForm, BindException errors) throws Exception
        {
            Container proj = getContainer().getProject();
            Container expContainer = ContainerManager.ensureContainer(proj.getPath() + "/Experiments");
            _newContainer = ContainerManager.createContainer(expContainer, createWorkbookForm.getName());
            WikiService svc = ServiceRegistry.get().getService(WikiService.class);
            svc.insertWiki(getUser(), _newContainer, WorkbookModule.EXPERIMENT_DESCRIPTION_WIKI_NAME,
                    PageFlowUtil.filter(createWorkbookForm.getDescription(), true, true), WikiRendererType.HTML, createWorkbookForm.getName());
            _newContainer.setFolderType(ModuleLoader.getInstance().getFolderType("Workbook"));
            return true;
        }

        public ActionURL getSuccessURL(CreateWorkbookForm createWorkbookForm)
        {
            return _newContainer.getFolderType().getStartURL(_newContainer, getUser());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Create Workbook", new ActionURL(this.getClass(), getContainer()));
            return root;
        }
    }

    public static class CreateWorkbookForm
    {
        private String name;
        private String description;


        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getDescription()
        {
            return description;
        }

        public void setDescription(String description)
        {
            this.description = description;
        }
    }
}