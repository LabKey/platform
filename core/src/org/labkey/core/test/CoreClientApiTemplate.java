/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.core.test;

import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PrintTemplate;
import org.labkey.api.view.template.TemplateHeaderView;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/**
 * Created by klum on 4/8/2014.
 */
public class CoreClientApiTemplate extends PrintTemplate
{
    public CoreClientApiTemplate(ModelAndView body, PageConfig page)
    {
        super("/org/labkey/core/test/coreClientApiTemplate.jsp", page);

        setFrame(FrameType.NONE);
        page.setShowHeader(true);
        setView("header", getHeaderView(page));
        setBody(body);
    }

    public CoreClientApiTemplate(ModelAndView body)
    {
        this(body, new PageConfig());
    }

    protected HttpView getHeaderView(PageConfig page)
    {
        String upgradeMessage = UsageReportingLevel.getUpgradeMessage();
        Map<String, Throwable> moduleFailures = ModuleLoader.getInstance().getModuleFailures();
        return new TemplateHeaderView(upgradeMessage, moduleFailures, page);
    }
}
