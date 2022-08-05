/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.HasBindParameters;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.data.Container;
import org.labkey.api.gwt.client.AuditBehaviorType;
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
    private ViewContext _context;

    private SchemaKey _schemaName = null;
    private UserSchema _schema;

    private String _queryName;
    private QueryView _queryView;

    private String _viewName;
    private CustomView _customView;
    private QuerySettings _querySettings;
    private String _queryViewActionURL;
    private String _dataRegionName = QueryView.DATAREGIONNAME_DEFAULT;

    private AuditBehaviorType _auditBehavior = null;

    // Allow URL parameters to bind schemaName, queryName, viewName, and dataRegionName.
    // If a derived class explicitly sets a schemaName and/or queryName (e.g. ListQueryForm or ChooseRunsToAnalyzeForm)
    // it won't be overridden by a URL parameter.
    // NOTE: Ideally we'd just use null to indicate the property has been set yet or not.  Unfortunately,
    // binding doesn't always happen and when it does, our getters have the side-effect of creating QuerySettings
    // with a copy the current queryName/viewName into querySettings.  Eventually, we should split out the
    // form-binding parts from the createSchema() and createQuerySettings() parts into two classes.
    private boolean _bindQueryName = true;
    private boolean _bindSchemaName = true;
    private boolean _bindViewName = true;
    protected BindState _bindState = BindState.UNBOUND;
    private QueryUpdateService.InsertOption _insertOption = QueryUpdateService.InsertOption.IMPORT;


    /**
     * @throws NotFoundException if the query/table does not exist.
     */
    public final QueryView getQueryView()
    {
        init();

        ensureSchemaExists();

        if (StringUtils.isEmpty(getQueryName()))
        {
            throw new NotFoundException("Query not specified");
        }

        if (_queryView == null)
        {
            throw new IllegalStateException("Expected _queryView to be initialized in call to init()");
        }

        // Don't treat a query with errors as if it doesn't exist at all
        if (_queryView.getTable() == null && _queryView.getParseErrors().isEmpty())
        {
            throw new NotFoundException("The specified query does not exist in schema '" + getSchemaName() + "'");
        }

        return _queryView;
    }

    protected enum BindState { UNBOUND, BINDING, BOUND }

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

    public AuditBehaviorType getAuditBehavior()
    {
        return _auditBehavior;
    }

    public void setAuditBehavior(AuditBehaviorType auditBehavior)
    {
        _auditBehavior = auditBehavior;
    }

    @Override
    public void setViewContext(ViewContext context)
    {
        _context = context;
    }

    @Override
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

    @Override
    public @NotNull BindException bindParameters(PropertyValues params)
    {
        return doBindParameters(params);
    }

    protected @NotNull BindException doBindParameters(PropertyValues params)
    {
        assert _bindState == BindState.UNBOUND;
        _initParameters = params;
        String commandName = getDataRegionName() == null ? "form" : getDataRegionName();

        // Delete parameters we don't want to bind or that we want QuerySettings.init() to handle
        MutablePropertyValues bindParams = new MutablePropertyValues(params);
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

        if (schemaName != null && !schemaName.getName().isEmpty())
            _schemaName = schemaName;

        return errors;
    }

    protected String getValue(Enum key, PropertyValues... pvss)
    {
        return getValue(key.name(), pvss);
    }

    protected String getValue(String key, PropertyValues... pvss)
    {
        String[] values = getValues(key, pvss);
        if (values != null && values.length > 0)
        {
            return values[0];
        }
        return null;
    }

    @Nullable
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

        return baseSchema;
    }


    final public QuerySettings getQuerySettings()
    {
        init();
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

    public final @Nullable UserSchema getSchema()
    {
        init();
        return _schema;
    }

    /** Initializes _schema, _querySettings, and _queryView */
    protected void init()
    {
        // Don't side-effect until all URL parameters have been bound.
        if (_bindState != BindState.BINDING)
        {
            if (_schema == null)
            {
                _schema = createSchema();
            }
            if (_querySettings == null && _schema != null)
            {
                _querySettings = createQuerySettings(_schema);
            }
            if (_queryView == null && _schema != null && _querySettings != null)
            {
                _queryView = _schema.createView(getViewContext(), _querySettings);
                // In cases of backwards compatibility for legacy names, the schema may have resolved the QueryView based
                // on some other schema or query name. Therefore, remember the correct names so that we're using them
                // consistently within this request
                _schemaName = _queryView.getSchema().getSchemaPath();
                // Will be null in the case of executing LabKey SQL directly without a saved custom query
                if (_queryView.getQueryDef() != null)
                {
                    _queryName = _queryView.getQueryDef().getName();
                }
            }
        }
    }

    public void setQueryName(String name)
    {
        if (_queryView != null)
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

    /** @throws NotFoundException if the query can't be resolved */
    @NotNull
    public QueryDefinition getQueryDef()
    {
        return getQueryView().getQueryDef();
    }

    public @Nullable ActionURL urlFor(QueryAction action)
    {
        ActionURL ret = null;
        UserSchema schema = getSchema();
        QueryDefinition def = getQueryDef();

        if (null != schema)
        {
            ret = schema.urlFor(action, def);
            if (ret != null && _customView != null && _customView.getName() != null)
            {
                ret.replaceParameter(QueryParam.viewName.toString(), _customView.getName());
            }
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
        _queryView = null;
        _schema = null;
        _querySettings = null;
    }

    public boolean canEdit()
    {
        return getQueryDef().canEdit(getUser());
    }

    public boolean canEditMetadata()
    {
        return getQueryDef().canEditMetadata(getUser());
    }

    public String getQueryViewActionURL()
    {
        return _queryViewActionURL;
    }

    public void setQueryViewActionURL(String queryViewActionURL)
    {
        _queryViewActionURL = queryViewActionURL;
    }

    public QueryUpdateService.InsertOption getInsertOption()
    {
        return _insertOption;
    }

    public void setInsertOption(QueryUpdateService.InsertOption insertOption)
    {
        _insertOption = insertOption;
    }

    public PropertyValues getInitParameters()
    {
        return _initParameters;
    }

    // Throws NotFoundException if schema doesn't exist, query isn't specified, or query doesn't exist
    // Code paths that call getQueryView() don't need to call this. Other code paths should call this to ensure
    // consistent and properly sanitized error messages.
    public void ensureQueryExists() throws NotFoundException
    {
        getQueryView();
    }

    // Throws NotFoundException if schema doesn't exist. Code paths that call getQueryView() don't need to call this.
    // Other code paths should call this to ensure consistent and properly sanitized error messages.
    public void ensureSchemaExists() throws NotFoundException
    {
        ensureSchemaNotNull(getSchema());
    }

    public static void ensureSchemaNotNull(QuerySchema schema)
    {
        if (schema == null)
            throw new NotFoundException("The specified schema does not exist");
    }
}
