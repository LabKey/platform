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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.Action;
import org.labkey.api.action.ActionType;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JsonWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.query.CustomViewUtil;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * User: dave
 * Date: Sep 3, 2009
 * Time: 3:36:07 PM
 */
@RequiresPermission(ReadPermission.class)
@Action(ActionType.SelectMetaData.class)
public class GetQueryDetailsAction extends ApiAction<GetQueryDetailsAction.Form>
{
    private static final Logger LOG = Logger.getLogger(GetQueryDetailsAction.class);

    public ApiResponse execute(Form form, BindException errors) throws Exception
    {
        ApiSimpleResponse resp = new ApiSimpleResponse();

        Container container = getContainer();
        User user = getUser();

        if (StringUtils.isEmpty(form.getSchemaName()))
            throw new NotFoundException("SchemaName not specified");

        QuerySchema querySchema = DefaultSchema.get(user, container, form.getSchemaName());
        if (!(querySchema instanceof UserSchema))
            throw new NotFoundException("Could not find the schema '" + form.getSchemaName() + "' in the folder '" + container.getPath() + "'!");
        UserSchema schema = (UserSchema)querySchema;

        QuerySettings settings = schema.getSettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, form.getQueryName());
        QueryDefinition queryDef = settings.getQueryDef(schema);
        if (null == queryDef)
            throw new NotFoundException("Could not find the query '" + form.getQueryName() + "' in the schema '" + form.getSchemaName() + "'!");

        boolean isUserDefined = !queryDef.isTableQueryDefinition();

        //a few basic props about the query
        //this needs to be populated before attempting to get the table info
        //so that the client knows if this query is user defined or not
        //so it can display edit source, edit design links
        resp.put("name", form.getQueryName());
        resp.put("schemaName", form.getSchemaName());
        resp.put("isUserDefined", isUserDefined);
        boolean canEdit = queryDef.canEdit(user);
        resp.put("canEdit", canEdit);
        resp.put("canEditSharedViews", container.hasPermission(user, EditSharedViewPermission.class));
        resp.put("isMetadataOverrideable", canEdit); //for now, this is the same as canEdit(), but in the future we can support this for non-editable queries

        if (isUserDefined)
            resp.put("moduleName", queryDef.getModuleName());
        boolean isInherited = (queryDef.canInherit() && !container.equals(queryDef.getDefinitionContainer()));
        resp.put("isInherited", isInherited);
        if (isInherited)
            resp.put("containerPath", queryDef.getDefinitionContainer().getPath());

        resp.put("isTemporary", queryDef.isTemporary());

        TableInfo tinfo;
        try
        {
            List<QueryException> qerrors = new ArrayList<>();
            tinfo = queryDef.getTable(qerrors, true);
            if (!qerrors.isEmpty())
            {
                resp.put("exception", qerrors.get(0).getMessage());
                return resp;
            }
        }
        catch(Exception e)
        {
            resp.put("exception", e.getMessage());
            return resp;
        }

        if (null == tinfo)
            throw new NotFoundException("Could not find the query '" + form.getQueryName() + "' in the schema '" + form.getSchemaName() + "'!");

        if (!isUserDefined && tinfo.isMetadataOverrideable())
            resp.put("isMetadataOverrideable", true);

        ActionURL auditHistoryUrl = QueryService.get().getAuditHistoryURL(user, container, tinfo);
        if (auditHistoryUrl != null)
            resp.put("auditHistoryUrl", auditHistoryUrl);

        resp.put("title", tinfo.getTitle());
        resp.put("titleColumn", tinfo.getTitleColumn());


        //8649: let the table provide the view data url
        ActionURL viewDataUrl = schema.urlFor(QueryAction.executeQuery, queryDef);
        if (null != viewDataUrl)
            resp.put("viewDataUrl", viewDataUrl);

        ActionURL importDataUrl =  schema.urlFor(QueryAction.importData, queryDef);
        if (null != importDataUrl)
        {
            if (AbstractTableInfo.LINK_DISABLER_ACTION_URL.equals(importDataUrl))
                resp.put("importUrlDisabled", true);
            else
                resp.put("importUrl", importDataUrl);
        }

        ActionURL insertDataUrl = schema.urlFor(QueryAction.insertQueryRow, queryDef);
        if (null != insertDataUrl) {
            if (AbstractTableInfo.LINK_DISABLER_ACTION_URL.equals(insertDataUrl))
                resp.put("insertUrlDisabled", true);
            else
                resp.put("insertUrl", insertDataUrl);
        }

        Map<FieldKey, Map<String, Object>> columnMetadata;

        //if the caller asked us to chase a foreign key, do that.  Note that any call to get a lookup table can throw a
        // QueryParseException, so we wrap all FK accesses in a try/catch.
        try
        {
            FieldKey fk = null;
            if (null != form.getFk())
            {
                fk = FieldKey.fromString(form.getFk());
                Map<FieldKey,ColumnInfo> colMap = QueryService.get().getColumns(tinfo, Collections.singletonList(fk));
                ColumnInfo cinfo = colMap.get(fk);
                if (null == cinfo)
                    throw new IllegalArgumentException("Could not find the column '" + form.getFk() + "' starting from the query " + form.getSchemaName() + "." + form.getQueryName() + "!");
                if (null == cinfo.getFk() || null == cinfo.getFkTableInfo())
                    throw new IllegalArgumentException("The column '" + form.getFk() + "' is not a foreign key!");
                tinfo = cinfo.getFkTableInfo();
            }

            if (null != tinfo.getDescription())
                resp.put("description", tinfo.getDescription());
            if (null != tinfo.getImportMessage())
                resp.put("importMessage", tinfo.getImportMessage());

            JSONArray templates = new JSONArray();
            List<Pair<String, String>> it = tinfo.getImportTemplates(getViewContext());
            if (null != it && it.size() > 0)
            {
                for (Pair<String, String> pair : it)
                {
                    JSONObject o = new JSONObject();
                    o.put("label", pair.getKey());
                    o.put("url", pair.second);
                    templates.put(o);
                }
            }
            resp.put("importTemplates", templates);

            Collection<FieldKey> fields = Collections.emptyList();
            if (null != form.getAdditionalFields() && form.getAdditionalFields().length > 0)
            {
                String[] additionalFields = form.getAdditionalFields();
                fields = new ArrayList<>(additionalFields.length);
                for (String additionalField : additionalFields)
                    fields.add(FieldKey.fromString(additionalField));
            }

            //now the native columns plus any additional fields requested
            columnMetadata = JsonWriter.getNativeColProps(tinfo, fields, fk, false);
            resp.put("columns", columnMetadata.values());

            // table indices
            JSONObject jsonIndices = new JSONObject();
            for (Map.Entry<String, Pair<TableInfo.IndexType, List<ColumnInfo>>> entry : tinfo.getUniqueIndices().entrySet())
            {
                JSONObject jsonIndex = new JSONObject();
                jsonIndex.put("type", entry.getValue().getKey());
                jsonIndex.put("columns", entry.getValue().getValue().stream().map(ColumnInfo::getFieldKey).toArray());
                jsonIndices.put(entry.getKey(), jsonIndex);
            }
            resp.put("indices", jsonIndices);
        }
        catch (QueryParseException e)
        {
            String queryName = ExceptionUtil.getExceptionDecoration(e, ExceptionUtil.ExceptionInfo.QueryName);
            String message = e.getMessage();
            String fullMessage = "";
            if (queryName != null)
            {
                fullMessage = "Error parsing query: '" + queryName + "'.";
                if (message != null && !message.isEmpty())
                {
                    fullMessage += " Message: ";
                }
            }
            fullMessage += message;
            resp.put("exception", fullMessage);

            LOG.error("QueryParseException. Container: " + getContainer().getPath() + " Query: " + queryName, e);

            return resp;
        }

        if (null == form.getFk())
        {
            //now the columns in the user's default view for this query
            QueryView qview = new QueryView(schema, settings, null);
            resp.put("defaultView", getDefaultViewProps(qview));

            List<Map<String, Object>> viewInfos = new ArrayList<>();
            Map<String, CustomView> allViews = queryDef.getCustomViews(getUser(), getViewContext().getRequest(), true, false);
            Set<String> viewNames = new CaseInsensitiveHashSet("");
            if (form.getViewName() != null)
                viewNames.addAll(Arrays.stream(form.getViewName()).map(String::trim).collect(Collectors.toList()));

            if (viewNames.contains("*"))
            {
                boolean hasDefault = false;
                for (CustomView cv: allViews.values())
                {
                    if (cv.getName() == null)
                        hasDefault = true;
                    viewInfos.add(CustomViewUtil.toMap(cv, getUser(), true, columnMetadata));
                }

                if (!hasDefault)
                    viewInfos.add(CustomViewUtil.toMap(queryDef.createCustomView(getUser(), null), getUser(), true, columnMetadata));
            }
            else
            {
                allViews = new CaseInsensitiveHashMap<>(allViews);
                for (String viewName : viewNames)
                {
                    // NOTE viewName==null in the allViews map
                    viewName = trimToNull(viewName);
                    CustomView cv = allViews.get(viewName);
                    if (null == cv && (viewName == null || form.isInitializeMissingView()))
                        cv = queryDef.createCustomView(getUser(), viewName);
                    if (null != cv)
                        viewInfos.add(CustomViewUtil.toMap(cv, getUser(), true, columnMetadata));
                }
            }

            // Include information about where these views might be saved and if the user has permission
            // to share views in that container
            JSONArray targetContainers = new JSONArray();
            JSONObject targetContainer = new JSONObject(container.toJSON(getUser()));
            targetContainer.put("canEditSharedViews", container.hasPermission(user, EditSharedViewPermission.class));
            targetContainers.put(targetContainer);

            if (tinfo.supportsContainerFilter())
            {
                Container c = container.getParent();
                while (c != null && !c.equals(ContainerManager.getRoot()))
                {
                    targetContainer = new JSONObject(c.toJSON(getUser()));
                    targetContainer.put("canEditSharedViews", c.hasPermission(user, EditSharedViewPermission.class));
                    targetContainers.put(targetContainer);
                    c = c.getParent();
                }

                c = ContainerManager.getSharedContainer();
                targetContainer = new JSONObject(c.toJSON(getUser()));
                targetContainer.put("canEditSharedViews", c.hasPermission(user, EditSharedViewPermission.class));
                targetContainers.put(targetContainer);
            }

            resp.put("targetContainers", targetContainers);

            resp.put("views", viewInfos);

            // add a create or edit url for the associated domain.
            DomainKind kind = tinfo.getDomainKind();
            if (kind == null)
            {
                String domainURI = null;
                try
                {
                    domainURI = schema.getDomainURI(tinfo.getName());
                }
                catch (NotFoundException nfe) { }

                if (domainURI != null)
                    kind = PropertyService.get().getDomainKind(domainURI);
            }
            
            if (kind != null)
            {
                Domain domain = tinfo.getDomain();
                if (domain != null)
                {
                    if (kind.canEditDefinition(user, domain))
                        resp.put("editDefinitionUrl", kind.urlEditDefinition(domain, getViewContext()));
                }
                else
                {
                    // Yes, some tables exist before their Domain does
                    if (kind.canCreateDefinition(user, container))
                        resp.put("createDefinitionUrl", kind.urlCreateDefinition(schema.getName(), tinfo.getName(), container, user));
                }
            }
        }

        return resp;
    }

    protected Map<String,Object> getDefaultViewProps(QueryView view)
    {
        Map<String,Object> defViewProps = new HashMap<>();
        defViewProps.put("columns", getDefViewColProps(view));
        return defViewProps;
    }

    protected List<Map<String,Object>> getDefViewColProps(QueryView view)
    {
        List<Map<String,Object>> colProps = new ArrayList<>();
        for (DisplayColumn dc : view.getDisplayColumns())
        {
            if (dc.isQueryColumn() && null != dc.getColumnInfo())
                colProps.add(JsonWriter.getMetaData(dc, null, true, true, false));
        }
        return colProps;
    }

    public static class Form
    {
        private String _queryName;
        private String _schemaName;
        private String[] _viewName;
        private String _fk;
        private String[] _additionalFields;
        private boolean _initializeMissingView;

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        public String[] getViewName()
        {
            return _viewName;
        }

        public void setViewName(String[] viewName)
        {
            _viewName = viewName;
        }

        public String getFk()
        {
            return _fk;
        }

        public void setFk(String fk)
        {
            _fk = fk;
        }

        public String[] getAdditionalFields()
        {
            return _additionalFields;
        }

        public void setFields(String[] fields)
        {
            _additionalFields = fields;
        }

        public boolean isInitializeMissingView()
        {
            return _initializeMissingView;
        }

        public void setInitializeMissingView(boolean initializeMissingView)
        {
            _initializeMissingView = initializeMissingView;
        }
    }
}
