/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.api.module;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.springframework.web.servlet.mvc.Controller;

import java.util.Collection;
import java.util.Collections;

/*
* User: Dave
* Date: Jan 23, 2009
* Time: 10:16:37 AM
*/

/**
 * Provides a simple Spring controller implementation that resolves action
 * names to html files in the module's views/ directory.
 */
public class SimpleController extends SpringActionController implements SpringActionController.ActionResolver
{
    public static final String BEGIN_VIEW_NAME = "begin";

    public SimpleController()
    {
//        setActionResolver();
    }

    public SimpleController(String controllerName)
    {
        setActionResolver(new HTMLFileActionResolver(controllerName));
    }

    public Controller resolveActionName(Controller actionController, String actionName)
    {
        String controllerName = getViewContext().getActionURL().getController();
        Module module = ModuleLoader.getInstance().getModuleForController(controllerName);

        Path path = ModuleHtmlView.getStandardPath(actionName);
        if (ModuleHtmlView.exists(module, path))
            return new SimpleAction(module, path);

        return null;
    }

    public void addTime(Controller action, long elapsedTime)
    {
    }

    public Collection<ActionDescriptor> getActionDescriptors()
    {
        return Collections.emptyList();
    }

    public static ActionURL getBeginViewUrl(Module module, Container container)
    {
        Path path = ModuleHtmlView.getStandardPath(BEGIN_VIEW_NAME);
        if (ModuleHtmlView.exists(module, path))
            return new ActionURL(module.getName(), BEGIN_VIEW_NAME, container);

        return null;
    }
}
