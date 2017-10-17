/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.DynaClass;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.HasBindParameters;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewForm;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.beans.Introspector;
import java.io.File;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Basic form for handling posts into views.
 * Supports insert, update, delete functionality with a minimum of fuss
 * <p/>
 */
public class TableViewForm extends ViewForm implements DynaBean, HasBindParameters
{
    private static final Logger _log = Logger.getLogger(TableViewForm.class);

    protected Map<String, String> _stringValues = new CaseInsensitiveHashMap<>();
    protected Map<String, Object> _values = null;
    protected StringWrapperDynaClass _dynaClass;
    protected Object _oldValues;
    protected TableInfo _tinfo = null;
    protected String[] _selectedRows = null;
    protected boolean _isDataLoaded;
    protected boolean _isBulkUpdate;
    public boolean _isDataSubmit = false;
    private boolean _validateRequired = true;

    public static final String DATA_SUBMIT_NAME = ".dataSubmit";
    public static final String BULK_UPDATE_NAME = ".bulkUpdate";

    /**
     * Creates a TableViewForm with no underlying dynaclass.
     */
    protected TableViewForm()
    {
        super();
    }

    public TableViewForm(StringWrapperDynaClass dynaClass)
    {
        super();
        _dynaClass = dynaClass;
    }

    /**
     * Creates a view form that wraps a table.
     */
    public TableViewForm(@NotNull TableInfo tinfo)
    {
        setTable(tinfo);
    }

    /**
     * Creates a view form that uses the supplied dynaClass for the property
     * list, but stashes the tableInfo for insert/update purposes and
     * to perform additional validation.
     */
    public TableViewForm(StringWrapperDynaClass dynaClass, TableInfo tinfo)
    {
        _dynaClass = dynaClass;
        _tinfo = tinfo;
    }

    protected void setDynaClass(StringWrapperDynaClass dynaClass)
    {
        _dynaClass = dynaClass;
    }

    /**
     * Sets the table. NOTE This will also overwrite any previously
     * set dynaClass with one derived from the table.
     */
    protected void setTable(@NotNull TableInfo tinfo)
    {
        _tinfo = tinfo;
        _dynaClass = TableWrapperDynaClass.getDynaClassInstance(tinfo);
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
    public void doInsert() throws SQLException, ServletException
    {
        assert null != _tinfo;

        if (!isValid())
            throw new SQLException("Form is not valid.");
        if (!hasPermission(InsertPermission.class))
        {
            throw new UnauthorizedException();
        }
        if (null != _tinfo.getColumn("container"))
            set("container", _c.getId());

        Map<String, Object> newMap = Table.insert(_user, _tinfo, getTypedValues());
        setTypedValues(newMap, false);
    }

    /**
     * Updates data from the current form into the database.
     * When update reselects all columns this will drive the changes
     * back into the form
     */
    public void doUpdate() throws SQLException, ServletException
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
            set("container", _c.getId());

        Object[] pkVal = getPkVals();
        Map newMap = Table.update(_user, _tinfo, getTypedValues(), pkVal);
        setTypedValues(newMap, true);
    }

    /**
     * If rows are selected in the grid, delete them.
     * Otherwise, if a pk is provided delete the row indicated by the PK.
     * <p/>
     * NOTE: Cascading deletes are NOT supported.
     */
    public void doDelete() throws SQLException, ServletException
    {
        assert null != _tinfo : "No table";
        assert null != getPkVals() : "No PK values";

        if (!hasPermission(DeletePermission.class))
        {
            throw new UnauthorizedException();
        }

        if (null != _selectedRows && _selectedRows.length > 0)
        {
            for (String _selectedRow : _selectedRows)
                Table.delete(_tinfo, _selectedRow);
        }
        else
        {
            Object[] pkVal = getPkVals();
            if (null != pkVal && null != pkVal[0])
                Table.delete(_tinfo, pkVal);
            else //Hmm, thow an exception here????
                _log.warn("Nothing to delete for table " + _tinfo.getName() + " on request " + _request.getRequestURI());
        }
    }

    /**
     * Pulls in the data from the current row of the database.
     */
    public void refreshFromDb() throws SQLException
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
            setOldValues(new CaseInsensitiveHashMap<>(getTypedValues()));
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

        set(getPkName(), str);
    }

    public void setPkVal(Object o)
    {
        assertSinglePK();

        setTypedValue(getPkName(), o);
    }

    public void setPkVals(Object[] o)
    {
        List<String> pkNames = getPkNamesList();
        for (int i = 0; i < pkNames.size(); i++)
            setTypedValue(pkNames.get(i), o[i]);
    }

    public void setPkVals(String s)
    {
        setPkVals(s.split(","));
    }

    public void setPkVals(String[] s)
    {
        List<String> pkNames = getPkNamesList();
        for (int i = 0; i < pkNames.size(); i++)
            set(pkNames.get(i), s[i]);
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
            pkVal = getTypedValues().get(pkName);
            if (null == pkVal)
            {
                Object oldValues = getOldValues();
                if (oldValues instanceof Map)
                    pkVal = ((Map) oldValues).get(pkName);
                else
                    try
                    {
                        pkVal = PropertyUtils.getProperty(oldValues, pkName);
                    }
                    catch (Exception ignored) {}
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

    protected void _populateValues(BindException errors)
    {
        // Don't do anything special if dynaclass is null
        assert _dynaClass != null;

        /**
         * Note that nulls in the hashmap are NOT the same as missing values
         * A null in the hashmap indicates an empty string was posted.
         * A missing value may indicate that the field was not even included in the form
         * ISSUE: Maybe keep empty strings around? But what about dates?
         *
         */
        Map<String, Object> values = new CaseInsensitiveHashMap<>();
        Set<String> keys = _stringValues.keySet();

        for (String propName : keys)
        {
            String str = _stringValues.get(propName);
            String caption = _dynaClass.getPropertyCaption(propName);

            if (null != str && "".equals(str.trim()))
                str = null;

            Class propType = null;

            try
            {
                if (null != str)
                {
                    propType = _dynaClass.getTruePropType(propName);
                    if (propType != null)
                    {
                        Object val = ConvertUtils.convert(str, propType);
                        values.put(propName, val);
                    }
                    else
                    {
                        values.put(propName, str);
                    }
                }
                else if (_validateRequired && null != _tinfo)
                {
                    ColumnInfo col = getColumnByFormFieldName(propName);

                    if (null == col || !col.isRequired())
                    {
                        values.put(propName, null);
                    }
                    else
                    {
                        boolean isError = true;

                        // if the column is mv-enabled and a mv indicator has been specified, don't flag the required
                        // error
                        if (col.isMvEnabled() && col.isNullable())
                        {
                            ColumnInfo mvCol = _tinfo.getColumn(col.getMvColumnName());
                            if (mvCol != null)
                            {
                                String ff_mvName = getFormFieldName(mvCol);
                                isError = StringUtils.trimToNull(_stringValues.get(ff_mvName)) == null;
                            }
                        }
                        if (isError)
                            errors.addError(new FieldError(errors.getObjectName(), propName, this, true, new String[] {SpringActionController.ERROR_REQUIRED}, new String[] {caption}, caption + " must not be empty."));
                        else
                            values.put(propName, null);
                    }

                }
                else
                {
                    values.put(propName, null);
                }
            }
            catch (ConversionException e)
            {
                String error = SpringActionController.ERROR_CONVERSION;
                if (null != propType)
                    error += "." + propType.getSimpleName();
                errors.addError(new FieldError(errors.getObjectName(), propName, this, true, new String[] {error}, new String[] {str, caption}, "Could not convert value: " + str));
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
        return getTypedValues().get(propName);
    }

    public Object getTypedValue(ColumnInfo column)
    {
        return getTypedValues().get(getFormFieldName(column));
    }

    public boolean hasTypedValue(String propName)
    {
        return getTypedValues().containsKey(propName);
    }

    public boolean hasTypedValue(ColumnInfo column)
    {
        return getTypedValues().containsKey(getFormFieldName(column));
    }

    public void setTypedValue(String propName, Object val)
    {
        getTypedValues().put(propName, val);
        _stringValues.put(propName, ConvertUtils.convert(val));
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
        // Don't have values if dynaclass is null
        if (null == _dynaClass)
            return null;

        if (null == _values)
            populateValues(null);

        return _values;
    }

    /**
     * Get case-insensitive map of typed values for each of the columns and mvColumns in the table if available.
     * @param includeUntyped The result map will include the String value that wasn't converted.
     * @return CaseInsensitiveHashMap of typed values.
     */
    public Map<String,Object> getTypedColumns(boolean includeUntyped)
    {
        Map<String, Object> values = new CaseInsensitiveHashMap<>();
        for (ColumnInfo column : getTable().getColumns())
        {
            if (hasTypedValue(column))
                values.put(column.getName(), getTypedValue(column));
            else if (includeUntyped && contains(column))
                values.put(column.getName(), get(column));

            // Check if there was a file uploaded for the column's value
            if (values.get(column.getName()) == null && File.class.equals(column.getJavaClass()) && getRequest() instanceof MultipartHttpServletRequest)
            {
                MultipartHttpServletRequest request = (MultipartHttpServletRequest) getRequest();
                MultipartFile f = request.getFile(getFormFieldName(column));
                // Only set the parameter value if there was a form element that was posted
                if (f != null)
                {
                    values.put(column.getName(), f.getOriginalFilename() == null || f.getOriginalFilename().isEmpty() ? null : f);
                }
            }

            if (column.isMvEnabled())
            {
                ColumnInfo mvColumn = getTable().getColumn(column.getMvColumnName());
                if (null != mvColumn)
                {
                    if (hasTypedValue(mvColumn))
                        values.put(mvColumn.getName(), getTypedValue(mvColumn));
                    else if (includeUntyped && contains(column))
                        values.put(mvColumn.getName(), get(mvColumn));
                }
            }
        }
        return values;
    }

    /**
     * Get case-insensitive map of typed values for each of the columns and mvColumns in the table if available.
     * @return CaseInsensitiveHashMap of typed values.
     */
    public Map<String,Object> getTypedColumns()
    {
        return getTypedColumns(false);
    }


    /**
     * Set a map of real values. This will reset the matching strings stored in the object.
     */
    public void setTypedValues(Map<String, Object> values, boolean merge)
    {
        assert null != _dynaClass;
        assert null != (values = Collections.unmodifiableMap(values));

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
            _values.put(propName, e.getValue());
            _stringValues.put(propName, ConvertUtils.convert(e.getValue()));
        }
    }

    public void setStrings(Map<String, String> strings)
    {
        assert null != _dynaClass;

        _stringValues = strings;
        _values = null;
    }

    public Map<String, String> getStrings()
    {
        return _stringValues;
    }

    public boolean contains(ColumnInfo col)
    {
        return _stringValues.containsKey(getFormFieldName(col));
    }

    public boolean contains(DisplayColumn col, RenderContext ctx)
    {
        return _stringValues.containsKey(col.getFormFieldName(ctx));
    }

    public Object get(String arg0)
    {
        return _stringValues.get(arg0);
    }

    public Object get(ColumnInfo col)
    {
        return _stringValues.get(getFormFieldName(col));
    }

    public void set(String arg0, Object arg1)
    {
        String v;
        if (arg1 == null)
            v = null;
        else if (arg1 instanceof Object[])
        {
            // HACK: This is annoying, but TableViewForm insists on converting values to Strings before letting populateValues() bind.
            // Doubly annoying is we need to work around StringArrayConverter's poor parsing of single string values as seen in Issue 5340.
            // Convert into stringified array that org.apache.commons.beanutils.converters.StringArrayConverter can parse.
            v = "{" + StringUtils.join((Object[])arg1, ",") + "}";
        }
        else
        {
            // Trim to prevent users from inadvertently letting in leading/trailing spaces, which cause confusion on filtering, sorting, joins, and many other places
            v = arg1.toString().trim();
        }
        _stringValues.put(arg0, v);
        _values = null;
    }

    public boolean contains(String arg0, String arg1)
    {
        throw new UnsupportedOperationException("No mapped properties in a table");
    }

    public Object get(String arg0, String arg1)
    {
        throw new UnsupportedOperationException("No mapped properties in a table");
    }

    public Object get(String arg0, int arg1)
    {
        throw new UnsupportedOperationException("No indexed properties in a table");
    }

    public DynaClass getDynaClass()
    {
        return _dynaClass;
    }

    public void remove(String arg0, String arg1)
    {
        throw new UnsupportedOperationException("No indexed properties in a table");
    }

    public void set(String arg0, String arg1, Object arg2)
    {
        throw new UnsupportedOperationException("No mapped properties in a table");
    }

    public void set(String arg0, int arg1, Object arg2)
    {
        throw new UnsupportedOperationException("No indexed properties in a table");
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
        setStrings(new HashMap<String,String>());
        setOldValues(null);
        setPkVals(pk);
        setDataLoaded(false);
    }

    public String getFormFieldName(@NotNull ColumnInfo column)
    {
        return column.getPropertyName();
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
            Set<String> selected = DataRegionSelection.getSelected(context, null, true, false);
            _selectedRows = selected.toArray(new String[selected.size()]);
        }
        else
        {
            _selectedRows = request.getParameterValues(DataRegion.SELECT_CHECKBOX_NAME);
        }

        String pkString = request.getParameter("pk");
        if (null != StringUtils.trimToNull(pkString) && null != _tinfo)
            setPkVals(pkString);

        try
        {
            String oldVals = request.getParameter(DataRegion.OLD_VALUES_NAME);
            if (null != StringUtils.trimToNull(oldVals))
            {
                _oldValues = PageFlowUtil.decodeObject(oldVals);
                _isDataLoaded = true;
            }
        }
        catch (Exception ignored) {}
    }


    public BindException bindParameters(PropertyValues params)
    {
        /*
         * Checkboxes are weird. If set to FALSE they don't post
         * at all. So impossible to tell difference between values
         * that weren't on the html form at all and ones that were set to false
         * by the user.
         * To fix this each checkbox posts its name in a hidden field
         * We set them all to false and spring will overwrite with true
         * if they are set.
         */
        HttpServletRequest request = getRequest();

        // TODO: Remove all ~checkboxes handling -- I don't think we ever output this
        String[] checkboxes = request.getParameterValues("~checkboxes");

        if (null != checkboxes)
            for (String checkbox : checkboxes)
                set(checkbox, "0");

        // handle Spring style markers as well
        IteratorUtils.asIterator(request.getParameterNames()).forEachRemaining(name -> {
            if (name.startsWith(SpringActionController.FIELD_MARKER))
                set(name.substring(SpringActionController.FIELD_MARKER.length()), "0");
        });

        BindException errors = new NullSafeBindException(new BaseViewAction.BeanUtilsPropertyBindingResult(this, "form"));

        // handle binding of base class ReturnURLForm
        PropertyValue pvReturn = params.getPropertyValue("returnUrl");
        if (null == pvReturn)
            pvReturn = params.getPropertyValue("returnURL");
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
            Object value = pv.getValue();
            if (value instanceof String || value instanceof String[])
                set(pv.getName(), value);
        }

        validateBind(errors);
        return errors;
    }
}


