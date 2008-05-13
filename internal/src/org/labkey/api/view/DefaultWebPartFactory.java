/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.security.ACL;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 8, 2006
 * Time: 10:59:13 AM
 */
public class DefaultWebPartFactory extends WebPartFactory
{
    Class<? extends WebPartView> cls;

    public DefaultWebPartFactory(String name, Class<? extends WebPartView> cls)
    {
        super(name);
        this.cls = cls;
    }
    
    public DefaultWebPartFactory(String name, String location, Class<? extends WebPartView> cls)
    {
        super(name, location);
        this.cls = cls;
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException, InstantiationException
    {
        if (!portalCtx.hasPermission(ACL.PERM_READ))
            return new HtmlView("Datasets", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

        return cls.newInstance();
    }
}
