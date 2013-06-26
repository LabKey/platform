/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.ldk;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SimpleController;
import org.labkey.api.module.SimpleModule;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;

/**
 * User: bimber
 * Date: 2/6/13
 * Time: 6:26 PM
 */
public class ExtendedSimpleModule extends SimpleModule
{

    @Override
    public Controller getController(@Nullable HttpServletRequest request, Class controllerClass)
    {
        try
        {
            if (SimpleController.class.equals(controllerClass))
                return new SimpleController(getName().toLowerCase());

            // try spring configuration first
            Controller con = (Controller)getBean(controllerClass);
            if (null == con)
            {
                con = (Controller)controllerClass.newInstance();
                if (con instanceof ApplicationContextAware)
                    ((ApplicationContextAware)con).setApplicationContext(getApplicationContext());
            }
            return con;
        }
        catch (IllegalAccessException x)
        {
            throw new RuntimeException(x);
        }
        catch (InstantiationException x)
        {
            throw new RuntimeException(x);
        }
    }


    @Override
    public String getResourcePath()
    {
        return "/" + getClass().getPackage().getName().replaceAll("\\.", "/");
    }

    public boolean hasScripts()
    {
        return true;
    }

    @Override
    final public void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        super.startupAfterSpringConfig(moduleContext);
        doStartupAfterSpringConfig(moduleContext);
    }

    protected void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {

    }
}
