/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.core.security;

import org.labkey.api.external.tools.ExternalToolsViewProvider;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.view.PopupUserView;
import org.springframework.web.servlet.ModelAndView;

import java.util.LinkedList;
import java.util.List;

public class ApiKeyViewProvider implements ExternalToolsViewProvider
{
    @Override
    public List<ModelAndView> getViews(User user)
    {
        List<ModelAndView> views = new LinkedList<>();
        if (PopupUserView.allowApiKeyPage(user))
        {
            views.add(ModuleHtmlView.get(ModuleLoader.getInstance().getModule("core"), ModuleHtmlView.getGeneratedViewPath("apiKeys")));
        }

        return views;
    }
}
