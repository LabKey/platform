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
package org.labkey.query.controllers;

import org.labkey.api.action.Action;
import org.labkey.api.action.ActionType;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryForm;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/*
* User: dave
* Date: Sep 24, 2009
* Time: 10:39:52 AM
*/

@RequiresPermission(ReadPermission.class)
@Action(ActionType.Configure.class)
public class ViewQuerySourceAction extends SimpleViewAction<QueryForm>
{
    private QueryForm _form = null;
    
    public ModelAndView getView(QueryForm form, BindException errors) throws Exception
    {
        _form = form;
        QueryDefinition qdef = form.getQueryDef();
        if (null == qdef)
                throw new NotFoundException("Could not find a custom query named '" + form.getQueryName() + "' in schema '" + form.getSchemaName() + "'!");

        StringBuilder html = new StringBuilder("<div class='labkey-query-source'><pre>");
        html.append(qdef.getSql());
        html.append("</pre></div>");
        if (null != qdef.getMetadataXml())
        {
            html.append("<div class='labkey-query-metadata'>Metadata:<pre>");
            html.append(PageFlowUtil.filter(qdef.getMetadataXml()));
            html.append("</pre></div>");
        }

        return new HtmlView(html.toString());
    }

    public NavTree appendNavTrail(NavTree root)
    {
        ActionURL urlQ = new ActionURL(QueryController.BeginAction.class, _form.getViewContext().getContainer());
        urlQ.addParameter("schemaName", _form.getSchemaName());
        urlQ.addParameter("queryName", _form.getQueryName());
        return root.addChild("Query '" + _form.getSchemaName() + "." + _form.getQueryName() + "'", urlQ).addChild("Query Source");
    }

}
