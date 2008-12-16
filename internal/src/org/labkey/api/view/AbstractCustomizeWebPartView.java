/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

package org.labkey.api.view;

import org.labkey.api.data.Container;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.portal.ProjectUrls;

import javax.servlet.ServletException;

/**
 * User: jeckels
 * Date: Nov 22, 2005
 */
public abstract class AbstractCustomizeWebPartView<ModelBean> extends GroovyView<ModelBean>
{
    public AbstractCustomizeWebPartView(String templateName)
    {
        super(templateName);
    }

    @Override
    public void prepareWebPart(ModelBean model) throws ServletException
    {
        super.prepareWebPart(model);
        Container c = getViewContext().getContainer(ACL.PERM_UPDATE);
        addObject("postURL", PageFlowUtil.urlProvider(ProjectUrls.class).getCustomizeWebPartURL(c).toString());
        Portal.WebPart webPart = (Portal.WebPart) getViewContext().get("webPart");
        if (null != webPart)
            setTitle("Customize " + webPart.getName() + " Web Part");
    }
}
