/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
package org.labkey.devtools;

import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.SpringActionController.ActionDescriptor;
import org.labkey.api.data.Container;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;

import java.io.PrintWriter;
import java.util.Comparator;
import java.util.function.Predicate;

public class ActionListView extends HttpView
{
    private final SpringActionController _controller;
    private final Predicate<ActionDescriptor> _filter;

    public ActionListView(SpringActionController controller, Predicate<ActionDescriptor> filter)
    {
        _controller = controller;
        _filter = filter;
    }

    @Override
    protected void renderInternal(Object model, PrintWriter out)
    {
        Container c = _controller.getViewContext().getContainer();

        _controller.getActionResolver().getActionDescriptors().stream()
            .filter(ad->!(FormHandlerAction.class.isAssignableFrom(ad.getActionClass())))
            .filter(_filter)
            .sorted(Comparator.comparing(ActionDescriptor::getPrimaryName))
            .forEach(ad->{
                out.println(LinkBuilder.simpleLink(ad.getPrimaryName(), new ActionURL(ad.getActionClass(), c)));
                out.print("<br>");
            });
    }
}
