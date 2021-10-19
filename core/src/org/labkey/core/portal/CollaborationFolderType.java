/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.core.portal;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.MultiPortalFolderType;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.view.Portal;
import org.labkey.api.view.Portal.WebPart;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;

import java.util.Arrays;
import java.util.List;

/**
 * User: Mark Igra
 * Date: Aug 4, 2006
 * Time: 3:41:26 PM
 */
public class CollaborationFolderType extends MultiPortalFolderType
{
    public static final String TYPE_NAME = "Collaboration";

    public CollaborationFolderType()
    {
        this(Arrays.asList(
            createWebPart("Subfolders"),
            createWebPart("Wiki"),
            createWebPart("Wiki Table of Contents"),
            createWebPart("Messages")
        ));
    }

    private static @Nullable WebPart createWebPart(String name)
    {
        WebPartFactory factory = Portal.getPortalPart(name);
        return null != factory ? factory.createWebPart() : null;
    }

    public CollaborationFolderType(@Nullable List<WebPart> preferredParts)
    {
        super(TYPE_NAME,
            "Build a web site for publishing and exchanging information. " +
                    "Your tools include Message Boards, Issue Trackers and Wikis. Share information within your own " +
                    "group, across groups or with the public by configuring user permissions.",
            null,
            preferredParts,
            getDefaultModuleSet(),
            null
        );
    }

    @Override
    public String getStartPageLabel(ViewContext ctx)
    {
       Container c = ctx.getContainer();
        if (c.equals(ContainerManager.getHomeContainer()))
            return LookAndFeelProperties.getInstance(c).getShortName();
        else
            return "Start Page";
    }

    @Override
    public String getHelpTopic()
    {
        return "default";
    }
}
