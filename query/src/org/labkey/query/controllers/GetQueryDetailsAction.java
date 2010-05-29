/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiQueryResponse;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Sep 3, 2009
 * Time: 3:36:07 PM
 */
@RequiresPermissionClass(ReadPermission.class)
public class GetQueryDetailsAction extends ApiAction<GetQueryDetailsAction.Form>
{
    public ApiResponse execute(Form form, BindException errors) throws Exception
    {
        ApiSimpleResponse resp = new ApiSimpleResponse();

        Container container = getViewContext().getContainer();
        User user = getViewContext().getUser();
        QuerySchema schema = DefaultSchema.get(user, container).getSchema(form.getSchemaName());
        if (null == schema)
            throw new IllegalArgumentException("Could not find the schema '" + form.getSchemaName() + "' in the folder '" + container.getPath() + "'!");

        //a few basic props about the query
        //this needs to be populated before attempting to get the table info
        //so that the client knows if this query is user defined or not
        //so it can display edit source, edit design links
        resp.put("name", form.getQueryName());
        resp.put("schemaName", form.getSchemaName());
        Map<String,QueryDefinition> queryDefs = QueryService.get().getQueryDefs(container, form.getSchemaName());
        boolean isUserDefined = (null != queryDefs && queryDefs.containsKey(form.getQueryName()));
        resp.put("isUserDefined", isUserDefined);
        boolean canEdit = (null != queryDefs && queryDefs.containsKey(form.getQueryName()) && queryDefs.get(form.getQueryName()).canEdit(user));
        resp.put("canEdit", canEdit);
        resp.put("isMetadataOverrideable", canEdit); //for now, this is the same as canEdit(), but in the future we can support this for non-editable queries

        QueryDefinition querydef = (null == queryDefs ? null : queryDefs.get(form.getQueryName()));
        boolean isInherited = (null != querydef && querydef.canInherit() && !container.equals(querydef.getContainer()));
        resp.put("isInherited", isInherited);
        if (isInherited)
            resp.put("containerPath", querydef.getContainer().getPath());

        TableInfo tinfo;
        try
        {
            tinfo = schema.getTable(form.getQueryName());
        }
        catch(Exception e)
        {
            resp.put("exception", e.getMessage());
            return resp;
        }

        if (null == tinfo)
            throw new IllegalArgumentException("Could not find the query '" + form.getQueryName() + "' in the schema '" + form.getSchemaName() + "'!");

        if (!isUserDefined && tinfo.isMetadataOverrideable())
            resp.put("isMetadataOverrideable", true);
        
        //8649: let the table provide the view data url
        if (schema instanceof UserSchema)
        {
            UserSchema uschema = (UserSchema)schema;
            QueryDefinition qdef = QueryService.get().createQueryDefForTable(uschema, form.getQueryName());
            ActionURL viewDataUrl = qdef == null ? null : uschema.urlFor(QueryAction.executeQuery, qdef);
            if (null != viewDataUrl)
                resp.put("viewDataUrl", viewDataUrl);
        }

        //if the caller asked us to chase a foreign key, do that
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

        //now the native columns
        resp.put("columns", getNativeColProps(tinfo, fk));

        //now the columns in the user's default view for this query
        if (schema instanceof UserSchema && null == form.getFk())
        {
            resp.put("defaultView", getDefaultViewProps((UserSchema)schema, form.getQueryName()));
        }

        return resp;
    }

    protected List<Map<String,Object>> getNativeColProps(TableInfo tinfo, FieldKey fieldKeyPrefix)
    {
        List<Map<String,Object>> colProps = new ArrayList<Map<String,Object>>();
        for (ColumnInfo cinfo : tinfo.getColumns())
        {
            colProps.add(getColProps(cinfo, fieldKeyPrefix));
        }

        return colProps;
    }

    protected Map<String,Object> getDefaultViewProps(UserSchema schema, String queryName)
    {
        //build a query view
        QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, queryName);
        QueryView view = new QueryView(schema, settings, null);

        Map<String,Object> defViewProps = new HashMap<String,Object>();
        defViewProps.put("columns", getDefViewColProps(view));
        return defViewProps;
    }

    protected List<Map<String,Object>> getDefViewColProps(QueryView view)
    {
        List<Map<String,Object>> colProps = new ArrayList<Map<String,Object>>();
        for (DisplayColumn dc : view.getDisplayColumns())
        {
            if (dc.isQueryColumn() && null != dc.getColumnInfo())
                colProps.add(getColProps(dc.getColumnInfo(), null));
        }
        return colProps;
    }

    protected Map<String,Object> getColProps(ColumnInfo cinfo, FieldKey fieldKeyPrefix)
    {
        Map<String,Object> props = new HashMap<String,Object>();
        props.put("name", (null != fieldKeyPrefix ? FieldKey.fromString(fieldKeyPrefix, cinfo.getName()) : cinfo.getName()));
        if (null != cinfo.getDescription())
            props.put("description", cinfo.getDescription());

        props.put("type", cinfo.getFriendlyTypeName());

        if (null != cinfo.getFieldKey())
            props.put("fieldKey", cinfo.getFieldKey().toString());

        props.put("isAutoIncrement", cinfo.isAutoIncrement());
        props.put("isHidden", cinfo.isHidden());
        props.put("isKeyField", cinfo.isKeyField());
        props.put("isMvEnabled", cinfo.isMvEnabled());
        props.put("isNullable", cinfo.isNullable());
        props.put("isReadOnly", cinfo.isReadOnly());
        props.put("isUserEditable", cinfo.isUserEditable());
        props.put("isVersionField", cinfo.isVersionColumn());
        props.put("isShownInInsertView", cinfo.isShownInInsertView());
        props.put("isShownInUpdateView", cinfo.isShownInUpdateView());
        props.put("isShownInDetailsView", cinfo.isShownInDetailsView());
        if (!cinfo.getImportAliasesSet().isEmpty())
        {
            props.put("importAliases", new ArrayList<String>(cinfo.getImportAliasesSet()));
        }
        props.put("isSelectable", !cinfo.isUnselectable()); //avoid double-negative boolean name

        DisplayColumn dc = cinfo.getRenderer();
        if (null != dc)
        {
            props.put("caption", dc.getCaption());
        }

        Map<String, Object> lookupJSON = ApiQueryResponse.getLookupJSON(cinfo);
        if (lookupJSON != null)
        {
            props.put("lookup", lookupJSON);
        }

        return props;
    }

    public static class Form
    {
        private String _queryName;
        private String _schemaName;
        private String _fk;

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

        public String getFk()
        {
            return _fk;
        }

        public void setFk(String fk)
        {
            _fk = fk;
        }
    }
}
