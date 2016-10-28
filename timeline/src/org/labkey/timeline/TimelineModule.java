/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.apache.commons.beanutils.BeanUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.CodeOnlyModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.timeline.view.TimelineView;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class TimelineModule extends CodeOnlyModule
{
    public static final String NAME = "Timeline";

    public String getName()
    {
        return "Timeline";
    }

    protected void init()
    {
        addController("timeline", TimelineController.class);
    }

    public void doStartup(ModuleContext moduleContext)
    {
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<>(Arrays.asList(new BaseWebPartFactory(NAME, true, true, WebPartFactory.LOCATION_BODY)
        {
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                TimelineSettings settings = new TimelineSettings();
                try
                {
                    BeanUtils.populate(settings, webPart.getPropertyMap());
                }
                catch (InvocationTargetException | IllegalAccessException e)
                {
                    throw new UnexpectedException(e);
                }
                settings.setDivId("TimelineWebPart." + webPart.getIndex());
                return new TimelineView(settings);
            }

            public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
            {
                return new JspView<>(TimelineView.class, "customizeTimeline.jsp", webPart);
            }
        }));
    }
}