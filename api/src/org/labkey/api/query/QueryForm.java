/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.HasBindParameters;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.MemTracker;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;


/**
 * QueryForm is basically a wrapper for QuerySettings and related helper for the query subsystem.
 *
 * Since this is being bound from request variables all parameters may be overridden.  For more control,
 * use QuerySettings directly.
 *
 * Note, that the QuerySettings require a schemaName and dataRegionName before being constructed.
 */
public class QueryForm extends ReturnUrlForm implements HasViewContext, HasBindParameters
{
    public static final String PARAMVAL_NOFILTER = "NONE";
    private ViewContext _context;

    private SchemaKey _schemaName = null;
    private UserSchema _schema;

    private String _queryName;
    private QueryDefinition _queryDef;

    private String _viewName;
    private CustomView _customView;
    private QuerySettings _querySettings;
    private String _queryViewActionURL;
    private String _dataRegionName = QueryView.DATAREGIONNAME_DEFAULT;

    // Allow URL parameters to bind schemaName, queryName, viewName, and dataRegionName.
    // If a derived class explicitly sets a schemaName and/or queryName (e.g. ListQueryForm or ChooseRunsToAnalyzeForm)
    // it won't be overridden by a URL parameter.
    // NOTE: Ideally we'd just use null to indicate the property has been set yet or not.  Unfortunately,
    // binding doesn't always happen and when it does, our getters have the side-effect of creating QuerySettings
    // with a copy the current queryName/viewName into querySettings.  Eventually, we should split out the
    // form-binding parts from the createSchema() and createQuerySettings() parts into two classes.
    private boolean _bindQueryName = true;
    private boolean _bindSchemaName = true;
    private boolean _bindDataRegionName = true;
    private boolean _bindViewName = true;
    private BindState _bindState = BindState.UNBOUND;

    private enum BindState { UNBOUND, BINDING, BOUND }

    protected PropertyValues _initParameters = null;

    public QueryForm()
    {
        assert MemTracker.getInstance().put(this);
    }

    protected QueryForm(String schemaName, String queryName)
    {
        _schemaName = new SchemaKey(null, schemaName);
        _queryName = queryName;

        _bindSchemaName = false;
        _bindQueryName = false;

        assert MemTracker.getInstance().put(this);
    }

    protected QueryForm(String schemaName, String queryName, String viewName)
    {
        _schemaName = new SchemaKey(null, schemaName);
        _queryName = queryName;
        _viewName = viewName;

        _bindSchemaName = false;
        _bindQueryName = false;
        _bindViewName = false;

        assert MemTracker.getInstance().put(this);
    }

    public void setViewContext(ViewContext context)
    {
        _context = context;
    }

    public ViewContext getViewContext()
    {
        return _context;
    }

    protected User getUser()
    {
        return getViewContext().getUser();
    }


    protected Container getContainer()
    {
        return getViewContext().getContainer();
    }


    public BindException bindParameters(PropertyValues params)
    {
        return doBindParameters(params);
    }


    protected BindException doBindParameters(PropertyValues params)
    {
        assert _bindState == BindState.UNBOUND;
        _initParameters = params;
        String commandName = getDataRegionName() == null ? "form" : getDataRegionName();

        // Delete parameters we don't want to bind or that we want QuerySettings.init() to handle
        MutablePropertyValues bindParams = new MutablePropertyValues(params);
        if (!_bindDataRegionName)
            bindParams.removePropertyValue(QueryParam.dataRegionName.name());
        if (!_bindQueryName)
            bindParams.removePropertyValue(QueryParam.queryName.name());
        if (!_bindViewName)
            bindParams.removePropertyValue(QueryParam.viewName.name());

        // don't override preset schemaName
        SchemaKey schemaName = null;
        if (!_bindSchemaName)
            schemaName = _schemaName;

        _bindState = BindState.BINDING;
        BindException errors = BaseViewAction.springBindParameters(this, commandName, bindParams);
        _bindState = BindState.BOUND;

        if (schemaName != null && schemaName.getName() != null && !schemaName.getName().isEmpty())
            _schemaName = schemaName;

        return errors;
    }

    protected String getValue(Enum key, PropertyValues... pvss)
    {
        return getValue(key.name(), pvss);
    }

    protected String getValue(String key, PropertyValues... pvss)
    {
        for (PropertyValues pvs : pvss)
        {
            if (pvs == null) continue;
            PropertyValue pv = pvs.getPropertyValue(key);
            if (pv == null) continue;
            Object value = pv.getValue();
            if (value == null) continue;
            return value instanceof  String ? (String)value : ((String[])value)[0];
        }
        return null;
    }

    protected String[] getValues(String key, PropertyValues... pvss)
    {
        for (PropertyValues pvs : pvss)
        {
            if (pvs == null) continue;
            PropertyValue pv = pvs.getPropertyValue(key);
            if (pv == null) continue;
            Object value = pv.getValue();
            if (value == null) continue;
            return value instanceof String ? new String[] {(String)value} : ((String[])value);
        }
        return null;
    }

    protected @Nullable UserSchema createSchema()
    {
        // Don't side-effect until all URL parameters have been bound.
        if (_bindState == BindState.BINDING)
            return null;

        String schemaName = getSchemaName();
        UserSchema baseSchema = QueryService.get().getUserSchema(getUser(), getContainer(), schemaName);

        if (baseSchema == null)
        {
            return null;
        }

        QuerySettings settings = createQuerySettings(baseSchema);
        QueryView view = baseSchema.createView(getViewContext(), settings);
        // In cases of backwards compatibility for legacy names, the schema may have resolved the QueryView based
        // on some other schema or query name. Therefore, remember the correct names so that we're using them
        // consistently within this request
        _schemaName = view.getSchema().getSchemaPath();
        // Will be null in the case of executing LabKey SQL directly without a saved custom query
        if (view.getQueryDef() != null)
        {
            _queryName = view.getQueryDef().getName();
        }
        return view.getSchema();
    }


    final public QuerySettings getQuerySettings()
    {
        // Don't side-effect until all URL parameters have been bound.
        if (_bindState == BindState.BINDING)
            return null;
        if (_querySettings == null)
        {
            UserSchema schema = getSchema();
            if (schema != null)
                _querySettings = createQuerySettings(schema);
        }
        return _querySettings;
    }


    protected QuerySettings createQuerySettings(UserSchema schema)
    {
        QuerySettings settings = schema.getSettings(_initParameters, getDataRegionName());
        if (null != _queryName)
            settings.setQueryName(_queryName);
        if (null != _viewName)
            settings.setViewName(_viewName);
        return settings;
    }


    public void setDataRegionName(String name)
    {
        if (_querySettings != null)
            throw new IllegalStateException();
        _dataRegionName = name;
    }


    public String getDataRegionName()
    {
        return _dataRegionName;
    }


    public void setSchemaName(String name)
    {
        setSchemaName(SchemaKey.fromString(name));
    }

    public void setSchemaName(SchemaKey name)
    {
        if (_querySettings != null)
            throw new IllegalStateException();
        _schemaName = name;
    }

    // UNDONE: Use SchemaKey instead of String
    @NotNull
    public String getSchemaName()
    {
        return _schemaName == null ? "" : _schemaName.toString();
    }

    public @Nullable UserSchema getSchema()
    {
        if (_schema == null)
        {
            _schema = createSchema();
        }
        return _schema;
    }

    public void setQueryName(String name)
    {
        if (_queryDef != null)
            throw new IllegalStateException();
        _queryName = name;
    }

    public String getQueryName()
    {
        // Don't side-effect until all URL parameters have been bound.
        if (_bindState == BindState.BINDING)
            return null;
        return getQuerySettings() != null ? getQuerySettings().getQueryName() : _queryName;
    }

    @Nullable
    public QueryDefinition getQueryDef()
    {
        if (getQueryName() == null)
            return null;
        if (_queryDef == null)
        {
            UserSchema schema = getSchema();
            if (null != schema)
                _queryDef = schema.getQueryDef(getQueryName());
            if (null == _queryDef && null != schema && null != schema.getTable(getQueryName()))
                _queryDef = schema.getQueryDefForTable(getQueryName());
        }
        return _queryDef;
    }

    public ActionURL urlFor(QueryAction action)
    {
        ActionURL ret = getSchema().urlFor(action, getQueryDef());
        if (ret != null && _customView != null && _customView.getName() != null)
        {
            ret.replaceParameter(QueryParam.viewName.toString(), _customView.getName());
        }
        return ret;
    }

    public void setViewName(String name)
    {
        if (null != _customView || null != _querySettings)
            throw new IllegalStateException();
        _viewName = name;
    }

    public String getViewName()
    {
        // Don't side-effect until all URL parameters have been bound.
        if (_bindState == BindState.BINDING)
            return null;
        return getQuerySettings() != null ? getQuerySettings().getViewName() : _viewName;
    }
    
    public CustomView getCustomView()
    {
        // Don't side-effect until all URL parameters have been bound.
        if (_bindState == BindState.BINDING)
            return null;
        if (_customView != null)
            return _customView;
        if (getQuerySettings() == null)
            return null;
        String columnListName = getViewName();
        QueryDefinition querydef = getQueryDef();
        if (null == querydef)
        {
            throw new NotFoundException();
        }
        _customView = querydef.getCustomView(getUser(), getViewContext().getRequest(), columnListName);
        return _customView;
    }

    /**
     * Reset instanced cached information.
     * After deleting a custom view, we want to re-get the custom view to find any shadowed custom views.
     */
    public void reset()
    {
        _customView = null;
        _queryDef = null;
        _schema = null;
    }

    public boolean canEditSql()
    {
        QueryDefinition q = getQueryDef();
        return null != q && q.canEdit(getUser()) && getQueryDef().isSqlEditable();
    }

    public boolean canEditMetaData()
    {
        QueryDefinition q = getQueryDef();
        return null != q && q.canEdit(getUser()) && getQueryDef().isMetadataEditable();
    }

    public boolean canEdit()
    {
        return null != getQueryDef() && getQueryDef().canEdit(getUser());
    }

    public String getQueryViewActionURL()
    {
        return _queryViewActionURL;
    }

    public void setQueryViewActionURL(String queryViewActionURL)
    {
        _queryViewActionURL = queryViewActionURL;
    }
}
