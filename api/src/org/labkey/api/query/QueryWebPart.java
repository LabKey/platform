/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.json.JSONObject;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.apache.commons.beanutils.ConvertingWrapDynaBean;

import java.io.PrintWriter;
import java.util.Map;
import java.util.ArrayList;

public class QueryWebPart extends WebPartView
{
    private ViewContext _context;
    private Map<String, String> _properties;
    private Map<String, Object> _extendedProperties;
    private UserSchema _schema;
    private QuerySettings _settings;
    private String _schemaName;

    public QueryWebPart(ViewContext context, Portal.WebPart part)
    {
        _context = context;
        setFrame(FrameType.PORTAL);
        _properties = part.getPropertyMap();
        _extendedProperties = part.getExtendedProperties();
        String title = _properties.get("title");

        ActionURL url = QueryService.get().urlQueryDesigner(getUser(), getContainer(), null);
        _schemaName = _properties.get(QueryParam.schemaName.toString());
        if (_schemaName != null)
        {
            _schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), _schemaName);
        }

        if (_schema != null)
        {
            _settings = _schema.getSettings(part, context);
            String queryName = _settings.getQueryName();

            TableInfo td = null;
            try {
                ArrayList<QueryException> errors = new ArrayList<QueryException>();
                QueryDefinition qd = _settings.getQueryDef(_schema);
                if (null != qd)
                    td = qd.getTable(_schema, errors, false);
                if (!errors.isEmpty())
                    td = null;
            }catch(Exception x){}
            if (null == td)
            {
                url = _schema.urlSchemaDesigner();
            }
            else
            {
                url = QueryService.get().urlFor(context.getUser(), context.getContainer(), QueryAction.executeQuery, _schemaName, queryName);
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
            setTitleHref(QueryService.get().urlQueryDesigner(getUser(), getContainer(), null));
        }

        if (url != null)
            setTitleHref(url);

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
                String linkTarget = _properties.get("linkTarget");
                if (linkTarget != null)
                {
                    queryView.setLinkTarget(linkTarget);
                }
                queryView.setShadeAlternatingRows(true);
                queryView.setShowBorders(true);

                ConvertingWrapDynaBean dynaBean = new ConvertingWrapDynaBean(queryView);
                for (String key : _properties.keySet())
                {
                    if ("buttonBarPosition".equals(key))
                        continue;
                    String value = _properties.get(key);
                    if (value != null)
                    {
                        try
                        {
                            dynaBean.set(key, value);
                        }
                        catch (IllegalArgumentException e)
                        {
                            // just ignore non-queryview properties
                        }
                    }
                }

                String buttonBarPositionProp = _properties.get("buttonBarPosition");
                if (null != buttonBarPositionProp)
                {
                    try
                    {
                        queryView.setButtonBarPosition(DataRegion.ButtonBarPosition.valueOf(buttonBarPositionProp.toUpperCase()));
                    }
                    catch(IllegalArgumentException ignore) {}
                    if (queryView._buttonBarPosition == DataRegion.ButtonBarPosition.NONE)
                        queryView.setShowRecordSelectors(false);
                }

                if (null != _extendedProperties)
                {
                    if (_extendedProperties.get("buttonBar") instanceof JSONObject)
                    {
                        ButtonBarConfig bbarConfig = new ButtonBarConfig((JSONObject)_extendedProperties.get("buttonBar"));
                        queryView.setButtonBarConfig(bbarConfig);
                    }

                    // 10505 : add QueryWebPart filters to QuerySettings base sort/filter so they won't be changeable in the DataRegion UI.
                    if (_extendedProperties.get("filters") instanceof JSONObject)
                    {
                        JSONObject filters = (JSONObject)_extendedProperties.get("filters");
                        if (filters.size() > 0)
                        {
                            // UNDONE: there should be an easier way to convert into a Filter than having to serialize them onto an ActionUrl and back out.
                            ActionURL url = new ActionURL();
                            url.addParameters(filters);

                            SimpleFilter filter = _settings.getBaseFilter();
                            filter.addUrlFilters(url, queryView.getDataRegionName());

                            Sort sort = _settings.getBaseSort();
                            sort.addURLSort(url, queryView.getDataRegionName());
                        }
                    }
                }


                if (null != _properties.get("showRecordSelectors"))
                    queryView.setShowRecordSelectors(Boolean.parseBoolean(_properties.get("showRecordSelectors")));

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
