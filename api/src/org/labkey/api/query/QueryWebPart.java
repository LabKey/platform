/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.api.query;

import org.labkey.api.data.Aggregate;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QueryWebPart extends WebPartView
{
    private ViewContext _context;
    private UserSchema _schema;
    private QuerySettings _settings;
    private String _schemaName;
    private DataRegion.ButtonBarPosition _buttonBarPosition = null;

    public QueryWebPart(ViewContext context, Portal.WebPart part)
    {
        _context = context;
        setFrame(FrameType.PORTAL);
        Map<String, String> properties = part.getPropertyMap();
        String title = properties.get("title");

        String buttonBarPositionProp = properties.get("buttonBarPosition");
        if(null != buttonBarPositionProp)
            _buttonBarPosition = DataRegion.ButtonBarPosition.valueOf(buttonBarPositionProp.toUpperCase());

        ActionURL url = QueryService.get().urlQueryDesigner(getContainer(), null);
        _schemaName = properties.get(QueryParam.schemaName.toString());
        _schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), _schemaName);

        if (_schema != null)
        {
            _settings = _schema.getSettings(part, context);
            if (_settings.getQueryName() == null)
            {
                url = _schema.urlSchemaDesigner();
            }
            else
            {
                url = context.cloneActionURL();
                url.deleteParameters();
            }

            setTitleHref(url);
            if (title == null)
            {
                if (_settings.getQueryName() != null)
                {
                    title = _settings.getQueryName();
                }
                else
                {
                    title = _schema.getSchemaName() + " Queries";
                    title = title.substring(0,1).toUpperCase() + title.substring(1);
                }
            }
        }
        else
        {
            title = "Query";
            setTitleHref(QueryService.get().urlQueryDesigner(getContainer(), null));
        }
        if (url != null)
        {
            setTitleHref(url);
        }
        setTitle(title);
    }

    public User getUser()
    {
        return _context.getUser();
    }

    public Container getContainer()
    {
        return _context.getContainer();
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        HttpView view = null;
        if (_schema == null)
        {
            if (_schemaName == null)
            {
                out.write("Schema name is not set.");
            }
            else
            {
                out.write("Schema '" + PageFlowUtil.filter(_schemaName) + "' does not exist.");
            }
        }
        if (_schema != null && _settings != null)
        {
            QueryDefinition queryDef = _settings.getQueryDef(_schema);
            if (queryDef != null)
            {
                QueryView queryView = _schema.createView(getViewContext(), _settings);
                queryView.setShadeAlternatingRows(true);
                queryView.setShowBorders(true);
                if(null != _buttonBarPosition)
                {
                    queryView.setButtonBarPosition(_buttonBarPosition);
                    if (_buttonBarPosition == DataRegion.ButtonBarPosition.NONE)
                        queryView.setShowRecordSelectors(false);
                }
                view = queryView;
            }
        }


        if (view != null)
        {
            include(view);
            return;
        }
        if (_schema != null && _settings != null)
        {
            if (_settings.getAllowChooseQuery())
            {
                view = new ChooseQueryView(_schema, getViewContext().getActionURL(), _settings.getDataRegionName());
            }
            else
            {
                view = new ChooseQueryView(_schema, null, null);
            }
            include(view, out);
        }
    }
}
