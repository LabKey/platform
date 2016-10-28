/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.issue.actions;

import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.issue.IssuesModule;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.IssuesUpgradeCode;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Created by klum on 6/6/2016.
 */
@RequiresSiteAdmin
public class UpgradeIssuesAction extends SimpleViewAction<Object>
{
    @Override
    public ModelAndView getView(Object o, BindException errors) throws Exception
    {
        Module module = ModuleLoader.getInstance().getModule(IssuesModule.NAME);
        IssuesUpgradeCode upgradeCode = new IssuesUpgradeCode();
        upgradeCode.upgradeIssuesTables(ModuleLoader.getInstance().getModuleContext(module));

        return new HtmlView("issue upgrade complete");
    }

    @Override
    public NavTree appendNavTrail(NavTree root)
    {
        return null;
    }
}

