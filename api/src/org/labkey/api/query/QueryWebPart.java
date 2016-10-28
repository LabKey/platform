/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertingWrapDynaBean;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.ButtonBarConfig;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.TableType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class QueryWebPart extends VBox
{
    private ViewContext _context;
    private Map<String, String> _properties;
    private Map<String, Object> _extendedProperties;
    private UserSchema _schema;
    private QuerySettings _settings;
    private String _schemaName;

    // if set to 'html', any parse errors in the query are returned to the client as a JSON object instead of
    // rendered in the webpart
    private String _errorType;
    private String _metadata;
    private boolean _hasSql;


    public QueryWebPart(ViewContext context, Portal.WebPart part)
    {
        _context = context;
        setFrame(FrameType.PORTAL);
        _properties = part.getPropertyMap();
        _extendedProperties = part.getExtendedProperties();
        String title = _properties.get("title");
        _errorType = StringUtils.defaultString(_properties.get("errorType"), "html");

        ActionURL url = QueryService.get().urlQueryDesigner(getUser(), getContainer(), null);
        _schemaName = _properties.get(QueryParam.schemaName.toString());
        if (_schemaName != null)
        {
            _schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), _schemaName);
            // normalize the name (18641)
            if (null != _schema)
                _schemaName = _schema.getSchemaPath().toString();
        }

        // check for any metadata overrides
        if (_extendedProperties != null && _extendedProperties.get("metadata") instanceof JSONObject)
        {
            JSONObject metadata = (JSONObject)_extendedProperties.get("metadata");

            if (metadata.has("type") && metadata.has("value"))
            {
                if ("xml".equalsIgnoreCase((String)metadata.get("type")))
                {
                    _metadata = (String)metadata.get("value");
                }
            }
        }

        if (_schema != null)
        {
            _settings = _schema.getSettings(part, context);
            String queryName = _settings.getQueryName();

            if (queryName == null)
            {
                Object sql = null==_extendedProperties ? null : _extendedProperties.get("sql");
                if (null == sql)
                    sql = _properties.get("sql");

                // execute arbitrary sql
                if (sql != null)
                {
                    String _sql = sql.toString();
                    _hasSql = true;
                    QueryDefinition def = QueryService.get().saveSessionQuery(context, context.getContainer(), _schemaName, _sql, _metadata);

                    _settings.setQueryName(def.getName());
                    queryName = _settings.getQueryName();
                }
            }

            TableInfo td = null;
            try
            {
                ArrayList<QueryException> errors = new ArrayList<>();
                QueryDefinition qd = _settings.getQueryDef(_schema);
                if (null != qd)
                {
                    td = qd.getTable(_schema, errors, true);
                    if (_metadata != null && !_hasSql && td != null)
                    {
                        TableType type = QueryService.get().parseMetadata(_metadata, errors);
                        td.overlayMetadata(Collections.singleton(type), _schema, errors);
                    }
                }
                if (!errors.isEmpty())
                    td = null;
            }
            catch (Exception x) { }

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
                if (td != null)
                {
                    title = td.getTitle();
                }
                else if (_settings.getQueryName() != null)
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

        addViews();
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
    public void render(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        if (_schema == null || _settings == null)
        {
            throw new NotFoundException("Invalid Schema provided: " + (_schemaName != null ? _schemaName : "<empty>"));
        }

        QueryDefinition queryDef = _settings.getQueryDef(_schema);

        if (_metadata != null)
            queryDef.setMetadataXml(_metadata);

        // need to return any parse errors before we start sending anything back through the response so
        // we can return JSON content
        if ("json".equalsIgnoreCase(_errorType) && queryDef != null)
        {
            List<QueryParseException> parseErrors = queryDef.getParseErrors(_schema);
            if (!parseErrors.isEmpty())
            {
                createErrorResponse(response, queryDef, parseErrors);
                return;
            }

            // additionally, check for any render time errors not caught at parse time
            List<QueryException> queryErrors = new ArrayList<>();
            queryDef.getTable(_schema, queryErrors, true);
            if (!queryErrors.isEmpty())
            {
                createErrorResponse(response, queryDef, queryErrors);
                return;
            }
        }
        super.render(request, response);
    }

    private void createErrorResponse(HttpServletResponse response, QueryDefinition queryDef, List<? extends QueryException> errors) throws IOException
    {
        ApiSimpleResponse errorResponse = new ApiSimpleResponse();
        JSONArray errorArray = new JSONArray();

        for (QueryException e : errors)
        {
            if (e instanceof MetadataParseException)
                errorArray.put(e.toJSON(queryDef.getMetadataXml()));
            else
                errorArray.put(e.toJSON(queryDef.getSql()));
        }
        errorResponse.put("parseErrors", errorArray);
        errorResponse.put("success", false);

        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        ApiJsonWriter jsonOut = new ApiJsonWriter(response);

        jsonOut.writeResponse(errorResponse);
    }

    protected void addViews()
    {
        HttpView view = null;

        if (_schema == null)
        {
            if (_schemaName == null)
            {
                view = new HtmlView("Schema name is not set.");
            }
            else
            {
                view = new HtmlView("Schema '" + PageFlowUtil.filter(_schemaName) + "' does not exist.");
            }
        }

        if (_schema != null && _settings != null)
        {
            QueryDefinition queryDef = _settings.getQueryDef(_schema);

            if (queryDef != null)
            {
                try
                {
                    QueryView queryView = createQueryView();
                    view = queryView;
                }
                catch (RuntimeException x)
                {
                    view = new HtmlView("<span class=error>" + PageFlowUtil.filter(x.getMessage()) + "</span>");
                }
            }
        }

        if (view != null)
        {
            _views.add(view);
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

            _views.add(view);
        }
    }

    private QueryView createQueryView()
    {
        NullSafeBindException errors = new NullSafeBindException(new Object(), "form");
        QueryView queryView = _schema.createView(getViewContext(), _settings, errors);
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
                _settings.addSortFilters(filters);
            }
        }


        if (null != _properties.get("showRecordSelectors"))
            queryView.setShowRecordSelectors(Boolean.parseBoolean(_properties.get("showRecordSelectors")));
        return queryView;
    }
}
