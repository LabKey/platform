/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.timeline;

import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.security.User;
import org.labkey.api.view.*;
import org.labkey.timeline.view.TimelineView;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;

import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class TimelineModule extends DefaultModule implements ContainerManager.ContainerListener
{
    public static final String NAME = "Timeline";

    public TimelineModule()
    {
        super(NAME, 0.01, null, false, new WebPartFactory(NAME, null, true, true){

            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
            {
                TimelineSettings settings = new TimelineSettings();
                BeanUtils.populate(settings, webPart.getPropertyMap());
                settings.setDivId("TimelineWebPart." + webPart.getIndex());
                return new TimelineView(settings);
            }

            public HttpView getEditView(Portal.WebPart webPart)
            {
                return new JspView<Portal.WebPart>(TimelineView.class, "customizeTimeline.jsp", webPart);
            }
        });
        addController("timeline", TimelineController.class);
    }

    public void containerCreated(Container c)
    {
    }

    public void containerDeleted(Container c, User user)
    {
    }

    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }

    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(this);
    }

    public Set<String> getSchemaNames()
    {
        return Collections.singleton("timeline");
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(TimelineSchema.getInstance().getSchema());
    }
}