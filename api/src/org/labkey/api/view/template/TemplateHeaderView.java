/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.view.template;

import org.labkey.api.view.JspView;

import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Nov 29, 2007
 * Time: 9:51:54 AM
 */
public class TemplateHeaderView extends JspView<TemplateHeaderView.TemplateHeaderBean>
{
    public TemplateHeaderView(List<String> containerLinks, String upgradeMessage, Map<String, Throwable> moduleErrors, PageConfig page)
    {
        super("/org/labkey/api/view/template/header.jsp", new TemplateHeaderBean(containerLinks, upgradeMessage, moduleErrors, page));
    }

    public TemplateHeaderView(PageConfig page)
    {
        this(null, null, null, page);
    }

    public static class TemplateHeaderBean
    {
        public List<String> containerLinks;
        public String upgradeMessage;
        public Map<String, Throwable> moduleFailures;
        public PageConfig pageConfig;

        private TemplateHeaderBean(List<String> containerLinks, String upgradeMessage, Map<String, Throwable> moduleFailures, PageConfig page)
        {
            this.containerLinks = containerLinks;
            this.upgradeMessage = upgradeMessage;
            this.moduleFailures = moduleFailures;
            this.pageConfig = page;
        }
    }
}
