/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.ReadPermission;

import java.lang.reflect.InvocationTargetException;

/**
 * User: matthewb
 * Date: Nov 8, 2006
 * Time: 10:59:13 AM
 */
public class DefaultWebPartFactory extends BaseWebPartFactory
{
    Class<? extends WebPartView> cls;

    public DefaultWebPartFactory(String name, Class<? extends WebPartView> cls)
    {
        super(name);
        this.cls = cls;
    }
    
    public DefaultWebPartFactory(String name, Class<? extends WebPartView> cls, @NotNull String location, String... additionalLocations)
    {
        super(name, location, additionalLocations);
        this.cls = cls;
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException, InstantiationException
    {
        if (!portalCtx.hasPermission(ReadPermission.class))
            return new HtmlView("Not Authorized", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

        return cls.newInstance();
    }
}
