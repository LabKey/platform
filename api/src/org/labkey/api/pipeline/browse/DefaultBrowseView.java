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

package org.labkey.api.pipeline.browse;

import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.PageFlowUtil;

import java.io.PrintWriter;

public class DefaultBrowseView <FORM extends BrowseForm> extends HttpView
{
    FORM form;
    BrowseView browseView;

    public DefaultBrowseView(FORM form)
    {
        super(form.getViewContext());
        this.form = form;
        this.browseView = PipelineService.get().getBrowseView(form);
    }


    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        out.print(PageFlowUtil.getStrutsError(getViewContext().getRequest(), null));
        ActionURL formAction = getViewContext().getActionURL();
        out.write("<form method=\"POST\" action=\"" + PageFlowUtil.filter(formAction) + "\">");
        include(browseView, out);
        out.write("</form>");
    }
}
