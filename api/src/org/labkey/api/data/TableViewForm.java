/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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

import org.apache.beehive.netui.pageflow.Forward;
import org.apache.commons.beanutils.*;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.labkey.api.security.ACL;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.HasBindParameters;
import org.labkey.api.action.BaseViewAction;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.PropertyValue;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.beans.Introspector;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;

/**
 * Basic form for handling posts into views.
 * Supports insert, update, delete functionality with a minimum of fuss
 * <p/>
 */
public class TableViewForm extends ViewFormData implements DynaBean, HasBindParameters
{
    protected Map<String, String> _stringValues = new CaseInsensitiveHashMap<String>();
    protected Map<String, Object> _values = null;
    protected StringWrapperDynaClass _dynaClass;
    protected Object _oldValues;
    protected TableInfo _tinfo = null;
    protected String[] _selectedRows = null;
    protected boolean _isDataLoaded;
    private static Logger _log = Logger.getLogger(TableViewForm.class);
    private boolean _validateRequired = true;

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
    public TableViewForm(TableInfo tinfo)
    {
        setTable(tinfo);
    }

    /**
     * Creates a view form that uses the suppllied dynaClass for the property
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
    protected void setTable(TableInfo tinfo)
    {
        _tinfo = tinfo;
        _dynaClass = TableWrapperDynaClass.getDynaClassInstance(tinfo);
    }

    public TableInfo getTable()
    {
        return _tinfo;
    }

    public boolean hasPermission(int perm)
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
        if (!hasPermission(ACL.PERM_INSERT))
            HttpView.throwUnauthorized();
        if (null != _tinfo.getColumn("container"))
            set("container", _c.getId());

        Map newMap = Table.insert(_user, _tinfo, getTypedValues());
        setTypedValues(newMap, false);
    }

    /**
     * Updates data from the current form into the database.
     * When update reselects all columns this will drive the changes
     * back into the form
     */
    public void doUpdate() throws SQLException, ServletException
    {
        assert null != _tinfo;
        assert null != getPkVals();

        if (!isValid())
            throw new SQLException("Form is not valid.");
        if (!hasPermission(ACL.PERM_UPDATE))
            HttpView.throwUnauthorized();

        if (null != _tinfo.getColumn("container"))
            set("container", _c.getId());

        Object[] pkVal = getPkVals();
        Map newMap = Table.update(_user, _tinfo, getTypedValues(), pkVal, null);
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
        assert null != _tinfo;
        assert null != getPkVals();

        if (!hasPermission(ACL.PERM_DELETE))
            HttpView.throwUnauthorized();

        if (null != _selectedRows && _selectedRows.length > 0)
        {
            for (String _selectedRow : _selectedRows)
                Table.delete(_tinfo, _selectedRow, null);
        }
        else
        {
            Object[] pkVal = getPkVals();
            if (null != pkVal && null != pkVal[0])
                Table.delete(_tinfo, pkVal, null);
            else //Hmm, thow an exception here????
                _log.warn("Nothing to delete for table " + _tinfo.getName() + " on request " + _request.getRequestURI());
        }
    }

    /**
     * Returns a new forward based on the pk.
     * Forward will be to same PageFlow. All existing parameters will be
     * deleted and a new parameter pkName=pkVal will be added where
     * pkName is the name of the primary key and pkVal is the value of the
     * primary key.
     *
     * @param action The action to forward to within this pageflow
     */
    public Forward getPkForward(String action) throws URISyntaxException
    {
        assert null != getPkVals();

        ActionURL urlhelp = getViewContext().cloneActionURL();
        urlhelp.setAction(action);
        urlhelp.deleteParameters();
        List<ColumnInfo> pkCols = _tinfo.getPkColumns();
        Object[] pkVals = getPkVals();
        for (int i = 0; i < pkCols.size(); i++)
            urlhelp.replaceParameter(pkCols.get(i).getPropertyName(), pkVals[i].toString());
        return new ViewForward(urlhelp);
    }

    /**
     * Pulls in the data from the current row of the database.
     */
    public void refreshFromDb() throws SQLException
    {
        assert null != _tinfo;
        assert null != getPkVals();

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
            HttpView.throwNotFound("Invalid PK value - cannot be null");
        }
        Map[] maps = Table.select(_tinfo, _tinfo.getColumnNameSet(), new PkFilter(_tinfo, pkVals), null, Map.class);

        if (maps.length > 0)
        {
            setTypedValues(maps[0], false);
            setOldValues(maps[0]);
        }
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
        assert _tinfo.getPkColumns().size() == 1;

        return _tinfo.getPkColumnNames().get(0);
    }

    public List<String> getPkNamesList()
    {
        return _tinfo.getPkColumnNames();
    }

    public void setPkVal(String str)
    {
        assert _tinfo.getPkColumns().size() == 1;

        set(getPkName(), str);
    }

    public void setPkVal(Object o)
    {
        assert _tinfo.getPkColumns().size() == 1;

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
        assert _tinfo.getPkColumns().size() == 1;

        return getPkVals()[0];
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
                    catch (Exception e)
                    {
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
            errors = new BindException(this, name);
        }
        _populateValues(errors);
        return errors;
    }

    public void setValidateRequired(boolean validateRequired)
    {
        _validateRequired = validateRequired;
    }

    public void populateValues(ActionErrors errors)
    {
        BindException bind = populateValues((BindException)null);
        errors.add(convertErrorsToStruts(bind, getRequest()));
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
        Map<String, Object> values = new CaseInsensitiveHashMap<Object>();
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
                    Object val = ConvertUtils.convert(str, propType);
                    values.put(propName, val);
                }
                else if (_validateRequired && null != _tinfo)
                {
                    ColumnInfo col = getColumnByFormFieldName(propName);
                    if (null == col || col.isNullable())
                        values.put(propName, null);
                    else
                        errors.addError(new FieldError(errors.getObjectName(), propName, this, true, new String[] {SpringActionController.ERROR_REQUIRED}, new String[] {caption}, caption + " must not be empty."));
                }
                else
                    values.put(propName, null);
            }
            catch (ConversionException e)
            {
                // We may have a qc value instead
                ColumnInfo col = getColumnByFormFieldName(propName);
                if (col != null && col.isQcEnabled() && QcUtil.isQcValue(str, getContainer()))
                {
                    values.put(propName, str);
                }
                else
                {
                    String error = SpringActionController.ERROR_CONVERSION;
                    if (null != propType)
                        error += "." + propType.getSimpleName();
                    errors.addError(new FieldError(errors.getObjectName(), propName, this, true, new String[] {error}, new String[] {str, caption}, "Could not convert value: " + str));
                }
            }
        }
        _values = values;
    }

    public boolean isValid()
    {
        ActionErrors errors = new ActionErrors();
        populateValues(errors);
        return errors.isEmpty();
    }

    public Object getTypedValue(String propName)
    {
        return getTypedValues().get(propName);
    }

    public Object getTypedValue(ColumnInfo column)
    {
        return getTypedValues().get(getFormFieldName(column));
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
            populateValues((BindException)null);

        return _values;
    }

    /**
     * Set a map of real values. This will reset the matching strings stored in the object.
     */
    public void setTypedValues(Map<String, Object> values, boolean merge)
    {
        assert null != _dynaClass;

        //We assume this means data is loaded.
        _isDataLoaded = true;
        if (!merge)
            _values = values;
        _stringValues.clear();

        // Iterate a copy of the keys since we may modify the set
        Set<String> keys = new HashSet<String>(values.keySet());

        for (String propName : keys)
        {
            Object o = values.get(propName);
            //fix up propNames as we go. These come out of the
            //database messed up sometimes.
            if (Character.isUpperCase(propName.charAt(0)))
            {
                values.remove(propName);
                propName = Introspector.decapitalize(propName);
                values.put(propName, o);
            }

            if (merge)
                _values.put(propName, o);
            _stringValues.put(propName, ConvertUtils.convert(o));
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


    public Object get(String arg0)
    {
        return _stringValues.get(arg0);
    }

    public void set(String arg0, Object arg1)
    {
        _stringValues.put(arg0, arg1 == null ? null : arg1.toString());
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

    @Override
    public ActionErrors validate(ActionMapping arg0, HttpServletRequest arg1)
    {
        ActionErrors errors = PageFlowUtil.getActionErrors(arg1, true);
        populateValues(errors);
        return errors;
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

    public String getFormFieldName(ColumnInfo column)
    {
        return column.getPropertyName();
    }

    public ColumnInfo getColumnByFormFieldName(String name)
    {
        return getTable().getColumn(name);
    }


    public void reset(ActionMapping arg0, HttpServletRequest request)
    {
        super.reset(arg0, request);
        /*
         * Checkboxes are weird. If set to FALSE they don't post
         * at all. So impossible to tell difference between values
         * that weren't on the html form at all and ones that were set to false
         * by the user.
         * To fix this each checkbox posts its name in a hidden field
         * We set them all to false and struts will overwrite with true
         * if they are set.
         */
        String[] checkboxes = request.getParameterValues("~checkboxes");
        if (null != checkboxes)
            for (String checkbox : checkboxes)
                set(checkbox, "0");
        // handle Spring style markers as well
        for (Enumeration e = request.getParameterNames(); e.hasMoreElements() ; )
        {
            String name = (String)e.nextElement();
            if (name.startsWith(SpringActionController.FIELD_MARKER))
                set(name.substring(SpringActionController.FIELD_MARKER.length()), "0");
        }

        _selectedRows = request.getParameterValues(DataRegion.SELECT_CHECKBOX_NAME);

        String pkString = request.getParameter("pk");
        if (null != StringUtils.trimToNull(pkString))
            setPkVals(pkString);

        try
        {
            String oldVals = request.getParameter(".oldValues");
            if (null != StringUtils.trimToNull(oldVals))
            {
                _oldValues = PageFlowUtil.decodeObject(oldVals);
                _isDataLoaded = true;
            }
        }
        catch (Exception x)
        {
        }
    }


    public static void copyErrorsToStruts(BindException errors, HttpServletRequest request)
    {
        PageFlowUtil.getActionErrors(request, true).add(convertErrorsToStruts(errors,request));
    }


    public static ActionErrors convertErrorsToStruts(BindException errors, HttpServletRequest request)
    {
        ViewContext context = HttpView.getRootContext();
        ActionErrors struts = new ActionErrors();
        // UNDONE: need to move errors where InsertView expects them
        if (errors.hasGlobalErrors())
            for (ObjectError e : (List<ObjectError>)errors.getGlobalErrors())
                struts.add("main", new ActionMessage("Error", context.getMessage(e)));
        if (errors.hasFieldErrors())
            for (FieldError e : (List<FieldError>)errors.getFieldErrors())
                struts.add(e.getField(), new ActionMessage("Error", context.getMessage(e)));
        return struts;
    }


    public BindException bindParameters(PropertyValues params)
    {
        BindException errors = new BindException(new BaseViewAction.BeanUtilsPropertyBindingResult(this, "form"));
        reset(null, getViewContext().getRequest());
        {
            for (PropertyValue pv : params.getPropertyValues())
            {
                if (pv.getValue() instanceof String)
                    set(pv.getName(), (String)pv.getValue());
            }
        }
        validateBind(errors);
        return errors;
    }
}


