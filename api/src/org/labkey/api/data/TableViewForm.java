/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.HasBindParameters;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewForm;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.beans.Introspector;
import java.io.File;
import java.lang.reflect.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.action.SpringActionController.FIELD_MARKER;
import static org.labkey.api.data.MultiChoice.ARRAY_MARKER;

/**
 * Basic form for handling posts into views.
 * Supports insert, update, delete functionality with a minimum of fuss
 * <p/>
 */
public class TableViewForm extends ViewForm implements HasBindParameters
{
    private static final Logger _log = LogHelper.getLogger(TableViewForm.class, "Table operation warnings");

    // This is called "stringValues" as this is expected to come from a form POST (but it was never just a string value)
    // However, it can also be String[] and other types
    protected Map<String, Object> _stringValues = new CaseInsensitiveHashMap<>();
    protected Map<String, Object> _values = null;
    protected Object _oldValues;
    protected TableInfo _tinfo = null;
    protected String[] _selectedRows = null;
    protected boolean _isDataLoaded;
    protected boolean _isBulkUpdate;
    public boolean _isDataSubmit = false;
    private boolean _validateRequired = true;

    public static final String DATA_SUBMIT_NAME = ".dataSubmit";
    public static final String BULK_UPDATE_NAME = ".bulkUpdate";


    protected TableViewForm()
    {
        super();
    }


    /**
     * Creates a view form that wraps a table.
     */
    public TableViewForm(@NotNull TableInfo tinfo)
    {
        setTable(tinfo);
    }

    protected void setTable(@NotNull TableInfo tinfo)
    {
        _tinfo = tinfo;
    }

    public TableInfo getTable()
    {
        return _tinfo;
    }

    public boolean hasPermission(Class<? extends Permission> perm)
    {
        return _c.hasPermission(_user, perm);
    }

    public String[] getSelectedRows()
    {
        return _selectedRows;
    }

    /**
     * Inserts data from the current form into the database.
     * Autoinc primary keys are reselected by insert and
     * pushed back into the form. When insert reselects
     * all columns this will reselect.
     */
    public void doInsert() throws SQLException
    {
        assert null != _tinfo;

        if (!isValid())
            throw new SQLException("Form is not valid.");
        if (!hasPermission(InsertPermission.class))
        {
            throw new UnauthorizedException();
        }
        if (null != _tinfo.getColumn("container"))
            setValueToBind("container", _c.getId());

        Map<String, Object> newMap = Table.insert(_user, _tinfo, _getTypedValues());
        setTypedValues(newMap, false);
    }

    /**
     * Updates data from the current form into the database.
     * When update reselects all columns this will drive the changes
     * back into the form
     */
    public void doUpdate() throws SQLException
    {
        assert null != _tinfo : "No table";
        assert null != getPkVals() : "No PK values";

        if (!isValid())
            throw new SQLException("Form is not valid.");
        if (!hasPermission(UpdatePermission.class))
        {
            throw new UnauthorizedException();
        }

        if (null != _tinfo.getColumn("container"))
            setValueToBind("container", _c.getId());

        Object[] pkVal = getPkVals();
        Map<String, Object> newMap = Table.update(_user, _tinfo, _getTypedValues(), pkVal);
        setTypedValues(newMap, true);
    }

    /**
     * If rows are selected in the grid, delete them.
     * Otherwise, if a pk is provided delete the row indicated by the PK.
     * <p/>
     * NOTE: Cascading deletes are NOT supported.
     */
    public void doDelete()
    {
        assert null != _tinfo : "No table";
        assert null != getPkVals() : "No PK values";

        if (!hasPermission(DeletePermission.class))
        {
            throw new UnauthorizedException();
        }

        if (null != _selectedRows && _selectedRows.length > 0)
        {
            for (String selectedRow : _selectedRows)
                Table.delete(_tinfo, selectedRow);
        }
        else
        {
            Object[] pkVal = getPkVals();
            if (null != pkVal && null != pkVal[0])
                Table.delete(_tinfo, pkVal);
            else //Hmm, throw an exception here????
                _log.warn("Nothing to delete for table " + _tinfo.getName() + " on request " + _request.getRequestURI());
        }
    }

    /**
     * Pulls in the data from the current row of the database.
     */
    public void refreshFromDb()
    {
        assert null != _tinfo : "No table";
        assert null != getPkVals() : "No PK values";

        Object[] pkVals = getPkVals();
        boolean foundNotNullValue = false;
        for (Object val : pkVals)
        {
            if (val != null)
            {
                foundNotNullValue = true;
                break;
            }
        }
        if (!foundNotNullValue)
        {
            throw new NotFoundException("Invalid PK value - cannot be null");
        }
        Map<String, Object>[] maps = new TableSelector(_tinfo, new PkFilter(_tinfo, pkVals), null).getMapArray();

        if (maps.length > 0)
        {
            setTypedValues(maps[0], false);
            setOldValues(new CaseInsensitiveHashMap<>(_getTypedValues()));
        }
    }

    public boolean isDataSubmit()
    {
        return _isDataSubmit;
    }

    public boolean isBulkUpdate()
    {
        return _isBulkUpdate;
    }

    public void setBulkUpdate(boolean isBulkUpdate)
    {
        _isBulkUpdate = isBulkUpdate;
    }

    public boolean isDataLoaded()
    {
        return _isDataLoaded;
    }

    public void setDataLoaded(boolean isDataLoaded)
    {
        _isDataLoaded = isDataLoaded;
    }

    /**
     * Convenience method for common case where there is only one pk column
     */
    public String getPkName()
    {
        assertSinglePK();

        return _tinfo.getPkColumnNames().get(0);
    }

    public List<String> getPkNamesList()
    {
        return _tinfo.getPkColumnNames();
    }

    public void setPkVal(String str)
    {
        assertSinglePK();
        setValueToBind(getPkName(), str);
    }

    public void setPkVal(Object o)
    {
        assertSinglePK();
        setTypedValue(getPkName(), o);
    }

    public void setPkVals(Object[] o)
    {
        List<String> pkNames = getPkNamesList();
        for (int i = 0; i < pkNames.size() && i < o.length; i++)
            setTypedValue(pkNames.get(i), o[i]);
    }

    public void setPkVals(String s)
    {
        //Issue 42042: Lists with text primary key don't handle commas in key value when viewing row details
        if (getPkNamesList().size() == 1)
        {
            setValueToBind(getPkNamesList().get(0), s);
        }
        else
        {
            // CONSIDER We should support PK column names with commas.  We should replace with better parser.
            // something like: setPkVals(PageFlowUtil.splitStringToValuesForImport(s));
            setPkVals(s.split(","));
        }
    }

    public void setPkVals(String[] s)
    {
        List<String> pkNames = getPkNamesList();
        for (int i = 0; i < pkNames.size() && i < s.length; i++)
            setValueToBind(pkNames.get(i), s[i]);
    }

    /**
     * Convenience method for case of single column pk
     */
    public Object getPkVal()
    {
        assertSinglePK();

        return getPkVals()[0];
    }

    private void assertSinglePK()
    {
        assert _tinfo.getPkColumns().size() == 1 : "Only tables with a single PK column are supported. " + _tinfo + " has " + getPkNamesList().size() + ": " + getPkNamesList();
    }

    public Object[] getPkVals()
    {
        List<String> pkNames = getPkNamesList();
        Object[] pkVals = new Object[pkNames.size()];

        for (int i = 0; i < pkNames.size(); i++)
        {
            String pkName = pkNames.get(i);
            Object pkVal;
            pkVal = _getTypedValues().get(pkName);
            if (null == pkVal)
            {
                Object oldValues = getOldValues();
                if (oldValues instanceof Map m)
                {
                    pkVal = m.get(pkName);
                }
                else
                {
                    try
                    {
                        pkVal = PropertyUtils.getProperty(oldValues, pkName);
                    }
                    catch (Exception ignored) {}
                }
            }
            pkVals[i] = pkVal;
        }

        return pkVals;
    }


    public BindException populateValues(BindException errors)
    {
        if (errors == null)
        {
            String name="form";
            if (null != getTable())
                name = getTable().getName();
            errors = new NullSafeBindException(this, name);
        }
        _populateValues(errors);
        return errors;
    }


    public void setValidateRequired(boolean validateRequired)
    {
        _validateRequired = validateRequired;
    }


    public Object getValueToBind(String propName)
    {
        Object value =  _stringValues.get(propName);
        if (null == value)
            return null;
        if (value instanceof String str)
           return StringUtils.trimToNull(str);
        return value;
    }


    protected void _populateValues(BindException errors)
    {
        /*
          Note that nulls in the hashmap are NOT the same as missing values
          A null in the hashmap indicates an empty string was posted.
          A missing value may indicate that the field was not even included in the form
          ISSUE: Maybe keep empty strings around? But what about dates?
         */
        Map<String, Object> values = new CaseInsensitiveHashMap<>();
        Set<String> keys = _stringValues.keySet();
        RemapCache cache = new RemapCache();

        for (String propName : keys)
        {
            // NOTE later code relies on false==contains(propName) when there is a conversion error
            Object bindValue = getValueToBind(propName);
            ColumnInfo col = getColumnByFormFieldName(propName);
            String caption = getPropertyCaption(propName);
            Class<?> propType = null;

            try
            {
                if (col != null)
                    propType = col.getJavaClass();
                Object val = getSimpleConvert(propName).convert(bindValue);

                boolean requiredError = false;
                if (_validateRequired && null != _tinfo && null != col && col.isRequired() && !col.isAutoIncrement())
                {
                    requiredError = col.getJdbcType().isEmpty(val);

                    // if the column is mv-enabled and a mv indicator has been specified, don't flag the required error
                    if (requiredError && col.isMvEnabled() && col.isNullable())
                    {
                        ColumnInfo mvCol = _tinfo.getColumn(col.getMvColumnName());
                        if (mvCol != null)
                        {
                            String ff_mvName = getFormFieldName(mvCol);
                            requiredError = null == getValueToBind(ff_mvName);
                        }
                    }
                }
                if (requiredError)
                    errors.addError(new FieldError(errors.getObjectName(), propName, this, true, new String[] {SpringActionController.ERROR_REQUIRED}, new String[] {caption}, caption + " must not be empty."));
                else
                    values.put(propName, val);
            }
            catch (ConversionException e)
            {
                boolean skipError = false;

                // Attempt to resolve lookups by display value
                String defaultMessage = null;
                if (col != null && col.getFk() != null && col.getFk().allowImportByAlternateKey())
                {
                    ForeignKey fk = col.getFk();
                    Container container = fk.getLookupContainer() != null ? fk.getLookupContainer() : getContainer();
                    try
                    {
                        String str = null==bindValue ? null : bindValue instanceof String[] ? ((String[])bindValue)[0] : (String)bindValue;
                        Object remappedValue = cache.remap(fk.getLookupSchemaKey(), fk.getLookupTableName(), getUser(), container, ContainerFilter.Type.CurrentPlusProjectAndShared, str);
                        if (remappedValue != null)
                        {
                            values.put(propName, remappedValue);
                            skipError = true;
                        }
                    }
                    catch (ConversionException e2)
                    {
                        defaultMessage = e2.getMessage();
                    }
                }

                if (!skipError)
                {
                    String error = SpringActionController.ERROR_CONVERSION;
                    if (null != propType)
                        error += "." + propType.getSimpleName();
                    String str = bindValue instanceof String[] strs ? PageFlowUtil.joinValuesToString(Arrays.asList(strs),',') : String.valueOf(bindValue);
                    errors.addError(new FieldError(errors.getObjectName(), propName, this, true, new String[] {error}, new String[] {str, caption}, Objects.toString(defaultMessage, "Could not convert value: " + str)));
                }
            }
        }

        _values = values;
    }

    public boolean isValid()
    {
        BindException bind = populateValues(null);
        return bind.getErrorCount() == 0 && bind.getFieldErrorCount() == 0;
    }

    public Object getTypedValue(String propName)
    {
        return _getTypedValues().get(propName);
    }

    public Object getTypedValue(ColumnInfo column)
    {
        return _getTypedValues().get(getFormFieldName(column));
    }

    public boolean hasTypedValue(String propName)
    {
        return _getTypedValues().containsKey(propName);
    }

    public boolean hasTypedValue(ColumnInfo column)
    {
        return _getTypedValues().containsKey(getFormFieldName(column));
    }

    public void setTypedValue(String propName, Object val)
    {
        // call _populate() if necessary
        _getTypedValues();
        _values.put(propName, val);
        // We don't use setValueToBind() here because we want to avoid its side effect of clearing _values
        // To convert or not to convert???
        _stringValues.put(propName, val);
    }

    /**
     * gets the typed values matching the strings. Values that caused conversion errors
     * will not be returned. (Use isValid if you want to make sure they are all correct).
     * <p/>
     * Note: If you change a value in the returned map, the corresponding string value
     * may not be updated. Use setTypedValue instead, or to reset the whole map use
     * setTypedValues
     */
    public Map<String, Object> getTypedValues()
    {
        return Collections.unmodifiableMap(_getTypedValues());
    }

    private Map<String, Object> _getTypedValues()
    {
        if (null == _values)
        {
            // TODO we don't usually enter this code path, but we should still throw if there is a conversion error
            // or maybe enforce that populateValues() has been called and throw IllegalStateException here?
            populateValues(null);
        }
        return _values;
    }

    /**
     * Returns a case-insensitive map of typed values for each of the columns and mvColumns in the table if available.
     * @param includeUntyped The result map will include the String value that wasn't converted.
     * @return CaseInsensitiveHashMap of typed values.
     */
    public CaseInsensitiveHashMap<Object> getTypedColumns(boolean includeUntyped)
    {
        CaseInsensitiveHashMap<Object> values = new CaseInsensitiveHashMap<>();

        for (ColumnInfo column : getTable().getColumns())
        {
            if (hasTypedValue(column))
            {
                values.put(column.getName(), getTypedValue(column));
            }
            else if (includeUntyped && _stringValues.containsKey(getFormFieldName(column)))
            {
                values.put(column.getName(), _stringValues.get(getFormFieldName(column)));
            }
            else if (getRequest() instanceof MultipartHttpServletRequest request)
            {
                String fieldName = getMultiPartFormFieldName(column);
                Object typedValue = _getTypedValues().get(fieldName);

                if (typedValue != null)
                    values.put(column.getName(), typedValue);
                else if (File.class.equals(column.getJavaClass()))
                {
                    MultipartFile file = request.getFile(fieldName);
                    if (file != null)
                    {
                        // Check if the file was removed
                        if (file.getOriginalFilename() == null || file.getOriginalFilename().isEmpty())
                            values.put(column.getName(), null);
                        else
                            values.put(column.getName(), file);
                    }
                }
            }

            if (column.isMvEnabled())
            {
                ColumnInfo mvColumn = getTable().getColumn(column.getMvColumnName());
                if (null != mvColumn)
                {
                    if (hasTypedValue(mvColumn))
                        values.put(mvColumn.getName(), getTypedValue(mvColumn));
                    else if (includeUntyped && _stringValues.containsKey(getFormFieldName(mvColumn)))
                        values.put(mvColumn.getName(), _stringValues.get(getFormFieldName(mvColumn)));
                }
            }
        }

        return values;
    }

    /**
     * Returns a case-insensitive map of typed values for each of the columns and mvColumns in the table if available.
     * @return CaseInsensitiveHashMap of typed values.
     */
    public CaseInsensitiveHashMap<Object> getTypedColumns()
    {
        return getTypedColumns(false);
    }

    /**
     * Set a map of real values. This will reset the matching strings stored in the object.
     */
    public void setTypedValues(Map<String, Object> values, boolean merge)
    {
        values = Collections.unmodifiableMap(values);

        //We assume this means data is loaded.
        _isDataLoaded = true;
        if (!merge)
            _values = new CaseInsensitiveHashMap<>();
        _stringValues.clear();

        for (Map.Entry<String,Object> e : values.entrySet())
        {
            //fix up propNames as we go. These come out of the
            //database messed up sometimes.
            String propName = e.getKey();
            if (Character.isUpperCase(propName.charAt(0)))
                propName = Introspector.decapitalize(propName);
            setTypedValue(propName, e.getValue());
            _stringValues.put(propName, e.getValue());
        }
    }

    public void setValuesToBind(Map<String, Object> strings)
    {
        _stringValues.clear();
        _values = null;
        for (Map.Entry<String, Object> e : strings.entrySet())
            setValueToBind(e.getKey(), e.getValue());
    }

    public Map<String, Object> getValuesToBind()
    {
        return Collections.unmodifiableMap(_stringValues);
    }

    public boolean contains(DisplayColumn col, RenderContext ctx)
    {
        return _stringValues.containsKey(col.getFormFieldName(ctx));
    }

    public @Nullable String getAsString(@NotNull String propName)
    {
        Object value = _stringValues.get(propName);
        if (value == null || value instanceof String)
            return (String)value;
        if (value instanceof String[] arr)
        {
            if (arr.length == 0)
                return null;
        }
        return ConvertUtils.convert(value);
    }

    public String getAsString(ColumnInfo col)
    {
        return getAsString(getFormFieldName(col));
    }

    public void setValueToBind(String propName, Object value)
    {
        if (null == value || value instanceof String || value instanceof String[])
            _stringValues.put(propName, value);
        else if (value instanceof Collection<?> col && col.stream().allMatch(e -> null==e || e instanceof String))
            _stringValues.put(propName, col.toArray(new String[0]));
        else
            _stringValues.put(propName, ConvertUtils.convert(value));
        _values = null;
    }


    public void validateBind(BindException errors)
    {
        populateValues(errors);
    }

    public Object getOldValues()
    {
        return _oldValues;
    }


    public void setOldValues(Object oldValues)
    {
        _oldValues = oldValues;
    }


    public void forceReselect()
    {
        Object[] pk = getPkVals();
        setValuesToBind(new HashMap<>());
        setOldValues(null);
        setPkVals(pk);
        setDataLoaded(false);
    }

    protected SimpleConvert getSimpleConvert(String propName)
    {
        ColumnInfo column = getColumnByFormFieldName(propName);
        if (null == column)
            return (value) -> value;
        boolean multiValued = column.getFk() instanceof MultiValuedForeignKey && ((MultiValuedForeignKey)column.getFk()).isMultiSelectInput();
        if (multiValued)
        {
            // TODO This should be reconciled with SimpleTranslator.MultiValueConvertColumn.convert()
            // TODO shouldn't this be getFk().createLookupColumn(getLookupDisplayName()).getJavaClass() or something like that?
            // I think String is a better guess than column.getJavaClass()
            return ConvertHelper.getSimpleConvert(String[].class);
        }
        return column.getConvertFn();
    }

    private static <K> Class<?> arrayClass(Class<K> k)
    {
        Object o = Array.newInstance(k, 0);
        return o.getClass();
    }

    private String getPropertyCaption(String propName)
    {
        ColumnInfo column = getColumnByFormFieldName(propName);
        return null==column ? propName : column.getLabel();
    }

    public String getFormFieldName(@NotNull ColumnInfo column)
    {
        return column.getName();
    }

    public String getMultiPartFormFieldName(@NotNull ColumnInfo column)
    {
        return getFormFieldName(column);
    }

    @Nullable
    public ColumnInfo getColumnByFormFieldName(@NotNull String name)
    {
        return null == getTable() ? null : getTable().getColumn(name);
    }

    @Override
    public void setViewContext(@NotNull ViewContext context)
    {
        super.setViewContext(context);

        HttpServletRequest request = getRequest();

        _isBulkUpdate = Boolean.parseBoolean(request.getParameter(BULK_UPDATE_NAME));
        _isDataSubmit = Boolean.parseBoolean(request.getParameter(DATA_SUBMIT_NAME));

        if (_isBulkUpdate)
        {
            Set<String> selected = DataRegionSelection.getSelected(context, null, false);
            _selectedRows = selected.toArray(new String[0]);
        }
        else
        {
            _selectedRows = request.getParameterValues(DataRegion.SELECT_CHECKBOX_NAME);
        }

        String pkString = request.getParameter("pk");
        if (null != StringUtils.trimToNull(pkString) && null != _tinfo)
            setPkVals(pkString);

        String oldValues = request.getParameter(DataRegion.OLD_VALUES_NAME);
        if (null != StringUtils.trimToNull(oldValues))
        {
            try
            {
                // Just the PK and version values
                _oldValues = new JSONObject(oldValues).toMap();
            }
            catch (JSONException e)
            {
                _log.debug("Failed to parse '.oldValues' JSON", e);
            }
        }
    }

    /** Handle @ prefix and [] prefix
     * "@quf_field" indicates that if "field" is missing, it should be treated as "field=0"
     * "[]quf_field indicates that value should be treated as an array even if only one value is present
     *  <br>
     *  client _could_ post both "myfield=" and "[]myfield=", but that's a client bug
     */
    public static PropertyValues preprocessPropertyValues(PropertyValues params)
    {
        // we can usually just return params
        if (params.stream().noneMatch(e -> e.getName().startsWith(ARRAY_MARKER) || e.getName().startsWith(FIELD_MARKER)))
            return params;

        Set<String> names = params.stream().map(PropertyValue::getName).collect(Collectors.toSet());
        var ret = new MutablePropertyValues();
        for (var orig : params)
        {
            var copy = orig;
            if (orig.getName().startsWith(FIELD_MARKER))
            {
                if (names.contains(orig.getName().substring(FIELD_MARKER.length())))
                    continue;
                copy = new PropertyValue(orig.getName().substring(1), "0");
            }
            else if (orig.getName().startsWith(ARRAY_MARKER) && orig.getValue()!=null)
            {
                var value = orig.getValue();
                var convertedValue = value;
                if (List.class.isAssignableFrom(value.getClass()))
                {
                    convertedValue = ((List<?>) value).toArray(new Object[0]);
                }
                if (!value.getClass().isArray())
                {
                    convertedValue = Array.newInstance(value.getClass(), 1);
                    Array.set(convertedValue, 0, value);
                }
                copy = new PropertyValue(orig.getName().substring(ARRAY_MARKER.length()), convertedValue);
            }
            ret.addPropertyValue(copy);
        }
        return ret;
    }

    @Override
    public @NotNull BindException bindParameters(PropertyValues paramsIn)
    {
        var params = preprocessPropertyValues(paramsIn);

        // handle binding of base class ReturnURLForm
        PropertyValue pvReturn = params.getPropertyValue(ActionURL.Param.returnUrl.toString());
        if (null != pvReturn)
        {
            try
            {
                setReturnUrl((String)pvReturn.getValue());
            }
            catch (Exception ignored) {}
        }

        for (PropertyValue pv : params.getPropertyValues())
        {
            setValueToBind(pv.getName(), pv.getValue());
        }

        BindException errors = new NullSafeBindException(this, "form");
        validateBind(errors);
        return errors;
    }
}


