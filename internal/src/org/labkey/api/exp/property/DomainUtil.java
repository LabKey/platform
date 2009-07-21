/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.api.exp.property;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.DateUtil;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;

import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.*;
import java.sql.SQLException;
import java.sql.ResultSet;

/**
 * User: jgarms
 * Date: Aug 12, 2008
 * Time: 3:44:30 PM
 */
public class DomainUtil
{
    private DomainUtil()
    {
    }

    public static String getFormattedDefaultValue(User user, DomainProperty property, Object defaultValue)
    {
        if (defaultValue == null)
            return "[none]";
        if (defaultValue instanceof Date)
        {
            Date defaultDate = (Date) defaultValue;
            if (property.getFormatString() != null)
                return DateUtil.formatDateTime(defaultDate, property.getFormatString());
            else
                return DateUtil.formatDate(defaultDate);
        }
        else if (property.getLookup() != null)
        {
            Container lookupContainer = property.getLookup().getContainer();
            if (lookupContainer == null)
                lookupContainer = property.getContainer();
            UserSchema schema = QueryService.get().getUserSchema(user, lookupContainer, property.getLookup().getSchemaName());
            if (schema != null)
            {
                TableInfo table = schema.getTable(property.getLookup().getQueryName());
                if (table != null)
                {
                    List<String> pks = table.getPkColumnNames();
                    String pkCol = pks.get(0);
                    if ((pkCol.equalsIgnoreCase("container") || pkCol.equalsIgnoreCase("containerid")) && pks.size() == 2)
                        pkCol = pks.get(1);
                    if (pkCol != null)
                    {
                        ColumnInfo pkColumnInfo = table.getColumn(pkCol);
                        if (!pkColumnInfo.getClass().equals(defaultValue.getClass()))
                            defaultValue = ConvertUtils.convert(defaultValue.toString(), pkColumnInfo.getJavaClass());
                        SimpleFilter filter = new SimpleFilter(pkCol, defaultValue);
                        ResultSet rs = null;
                        try
                        {
                            rs = Table.select(table, Table.ALL_COLUMNS, filter, null);
                            if (rs.next())
                            {
                                Object value = rs.getObject(table.getTitleColumn());
                                if (value != null)
                                    return value.toString();
                            }
                        }
                        catch (SQLException e)
                        {
                            throw new RuntimeSQLException(e);
                        }
                        finally
                        {
                            if (rs != null)
                                try { rs.close(); } catch (SQLException e) { }
                        }
                    }
                }
            }
        }
        return defaultValue.toString();
    }

    @Nullable
    public static GWTDomain getDomainDescriptor(User user, String typeURI, Container domainContainer)
    {
        try
        {
            DomainDescriptor dd = OntologyManager.getDomainDescriptor(typeURI, domainContainer);
            if (null == dd)
                return null;
            Domain domain = PropertyService.get().getDomain(dd.getDomainId());
            GWTDomain d = new GWTDomain();
            PropertyUtils.copyProperties(d, dd);

            ArrayList<GWTPropertyDescriptor> list = new ArrayList<GWTPropertyDescriptor>();

            DomainProperty[] properties = domain.getProperties();
            Map<DomainProperty, Object> defaultValues = DefaultValueService.get().getDefaultValues(domainContainer, domain);

            for (DomainProperty prop : properties)
            {
                GWTPropertyDescriptor p = getPropertyDescriptor(prop);
                Object defaultValue = defaultValues.get(prop);
                String formattedDefaultValue = getFormattedDefaultValue(user, prop, defaultValue);
                p.setDefaultDisplayValue(formattedDefaultValue);
                p.setDefaultValue(ConvertUtils.convert(defaultValue));
                list.add(p);
            }

            d.setFields(list);

            // Handle reserved property names
            DomainKind domainKind = domain.getDomainKind();
            Set<String> reservedProperties = domainKind.getReservedPropertyNames(domain);
            d.setReservedFieldNames(new HashSet<String>(reservedProperties));

            return d;
        }
        catch (IllegalAccessException e)
        {
            Logger.getLogger(DomainEditorServiceBase.class).error("unexpected error", e);
            throw new RuntimeException(e);
        }
        catch (InvocationTargetException e)
        {
            Logger.getLogger(DomainEditorServiceBase.class).error("unexpected error", e);
            throw new RuntimeException(e);
        }
        catch (NoSuchMethodException e)
        {
            Logger.getLogger(DomainEditorServiceBase.class).error("unexpected error", e);
            throw new RuntimeException(e);
        }
    }

    public static GWTPropertyDescriptor getPropertyDescriptor(DomainProperty prop)
    {
        GWTPropertyDescriptor gwtProp = new GWTPropertyDescriptor();

        gwtProp.setPropertyId(prop.getPropertyId());
        gwtProp.setDescription(prop.getDescription());
        gwtProp.setFormat(prop.getFormatString());
        gwtProp.setLabel(prop.getLabel());
        gwtProp.setName(prop.getName());
        gwtProp.setPropertyURI(prop.getPropertyURI());
        gwtProp.setRangeURI(prop.getType().getTypeURI());
        gwtProp.setRequired(prop.isRequired());
        gwtProp.setHidden(prop.isHidden());
        gwtProp.setMvEnabled(prop.isMvEnabled());
        gwtProp.setDefaultValueType(prop.getDefaultValueTypeEnum());

        List<GWTPropertyValidator> validators = new ArrayList<GWTPropertyValidator>();
        for (IPropertyValidator pv : prop.getValidators())
        {
            GWTPropertyValidator gpv = new GWTPropertyValidator();
            Lsid lsid = new Lsid(pv.getTypeURI());

            gpv.setName(pv.getName());
            gpv.setDescription(pv.getDescription());
            gpv.setExpression(pv.getExpressionValue());
            gpv.setRowId(pv.getRowId());
            gpv.setType(lsid.getObjectId());
            gpv.setErrorMessage(pv.getErrorMessage());
            gpv.setProperties(new HashMap<String,String>(pv.getProperties()));

            validators.add(gpv);
        }
        gwtProp.setPropertyValidators(validators);

        if (prop.getLookup() != null)
        {
            gwtProp.setLookupContainer(prop.getLookup().getContainer() == null ? null : prop.getLookup().getContainer().getPath());
            gwtProp.setLookupQuery(prop.getLookup().getQueryName());
            gwtProp.setLookupSchema(prop.getLookup().getSchemaName());
        }                                                   
        return gwtProp;
    }

    @SuppressWarnings("unchecked")
    public static List<String> updateDomainDescriptor(GWTDomain orig, GWTDomain update, Container container, User user) throws ChangePropertyDescriptorException
    {
        assert orig.getDomainURI().equals(update.getDomainURI());
        List<String> errors = new ArrayList<String>();

        Domain d = PropertyService.get().getDomain(container, update.getDomainURI());
        if (null == d)
        {
            errors.add("Domain not found: " + update.getDomainURI());
            return errors;
        }
        Map<DomainProperty, Object> defaultValues = new HashMap<DomainProperty, Object>();

        if (!d.getDomainKind().canEditDefinition(user, d))
        {
            errors.add("Unauthorized");
            return errors;
        }

        // validate names
        // look for swapped names

        // first delete properties
        Set<Integer> s = new HashSet<Integer>();
        for (GWTPropertyDescriptor pd : (List<GWTPropertyDescriptor>) orig.getFields())
            s.add(pd.getPropertyId());
        for (GWTPropertyDescriptor pd : (List<GWTPropertyDescriptor>) update.getFields())
        {
            String format = pd.getFormat();
            String type = "";
            try {
                if (!StringUtils.isEmpty(format))
                {
                    String ptype = pd.getRangeURI();
                    if (ptype.equalsIgnoreCase(PropertyType.DATE_TIME.getTypeUri()))
                    {
                        type = " for type " + PropertyType.DATE_TIME.getXarName();
                        FastDateFormat.getInstance(format);
                    }
                    else if (ptype.equalsIgnoreCase(PropertyType.DOUBLE.getTypeUri()))
                    {
                        type = " for type " + PropertyType.DOUBLE.getXarName();
                        new DecimalFormat(format);
                    }
                    else if (ptype.equalsIgnoreCase(PropertyType.INTEGER.getTypeUri()))
                    {
                        type = " for type " + PropertyType.INTEGER.getXarName();
                        new DecimalFormat(format);
                    }
                }
            }
            catch (IllegalArgumentException e)
            {
                errors.add(format + " is an illegal format" + type);
            }

            s.remove(pd.getPropertyId());
        }
        for (int id : s)
        {
            if (id <= 0)
                continue;
            DomainProperty p = d.getProperty(id);
            if (null == p)
                continue;
            p.delete();
        }

        // and now update properties
        for (GWTPropertyDescriptor pd : (List<GWTPropertyDescriptor>) update.getFields())
        {
            if (pd.getPropertyId() <= 0)
                continue;
            GWTPropertyDescriptor old = null;
            for (GWTPropertyDescriptor t : (List<GWTPropertyDescriptor>) orig.getFields())
            {
                if (t.getPropertyId() == pd.getPropertyId())
                {
                    old = t;
                    break;
                }
            }
            // UNDONE: DomainProperty does not support all PropertyDescriptor fields
            DomainProperty p = d.getProperty(pd.getPropertyId());
            defaultValues.put(p, pd.getDefaultValue());

            if (old == null)
                continue;
            updatePropertyValidators(p, old, pd);
            if (old.equals(pd))
                continue;

            try
            {
                _copyProperties(p, pd, errors);
            }
            catch (IllegalAccessException e)
            {
                throw new RuntimeException(e);
            }
            catch (InvocationTargetException e)
            {
                throw new RuntimeException(e);
            }
        }

        // Need to ensure that any new properties are given a unique PropertyURI.  See #8329
        Set<String> propertyUrisInUse = new HashSet<String>(update.getFields().size());

        for (GWTPropertyDescriptor pd : (List<GWTPropertyDescriptor>) update.getFields())
            if (!StringUtils.isEmpty(pd.getPropertyURI()))
                propertyUrisInUse.add(pd.getPropertyURI());

        // now add properties
        for (GWTPropertyDescriptor pd : (List<GWTPropertyDescriptor>) update.getFields())
        {
            if (pd.getPropertyId() > 0)
                continue;

            if (StringUtils.isEmpty(pd.getPropertyURI()))
            {
                String newPropertyURI = createUniquePropertyURI(update.getDomainURI() + "#" + pd.getName(), propertyUrisInUse);
                assert !propertyUrisInUse.contains(newPropertyURI) : "Attempting to assign an existing PropertyURI to a new property";
                pd.setPropertyURI(newPropertyURI);
                propertyUrisInUse.add(newPropertyURI);
            }

            // UNDONE: DomainProperty does not support all PropertyDescriptor fields
            DomainProperty p = d.addProperty();
            defaultValues.put(p, pd.getDefaultValue());
            try
            {
                _copyProperties(p, pd, errors);
                updatePropertyValidators(p, null, pd);
            }
            catch (IllegalAccessException e)
            {
                throw new RuntimeException(e);
            }
            catch (InvocationTargetException e)
            {
                throw new RuntimeException(e);
            }
        }

        try
        {
            if (errors.size() == 0)
            {
                d.save(user);
                // Rebucket the hash map with the real property ids
                defaultValues = new HashMap<DomainProperty, Object>(defaultValues);
                try
                {
                    DefaultValueService.get().setDefaultValues(d.getContainer(), defaultValues);
                }
                catch (ExperimentException e)
                {
                    errors.add(e.getMessage());
                }
            }
        }
        catch (IllegalStateException x)
        {
            errors.add(x.getMessage());
        }

        return errors.size() > 0 ? errors : null;
    }

    private static String createUniquePropertyURI(String base, Set<String> propertyUrisInUse)
    {
        String candidateURI = base;
        int i = 0;

        while (propertyUrisInUse.contains(candidateURI))
        {
            i++;
            candidateURI = base + i;
        }

        return candidateURI;
    }

    private static void _copyProperties(DomainProperty p, GWTPropertyDescriptor pd, List<String> errors)
            throws IllegalAccessException, InvocationTargetException
    {
        BeanUtils.copyProperties(p, pd);
        if (pd.getLookupQuery() != null)
        {
            String container = pd.getLookupContainer();
            Container c = null;
            if (container != null)
            {
                if (GUID.isGUID(container))
                    c = ContainerManager.getForId(container);
                if (null == c)
                    c = ContainerManager.getForPath(container);
                if (c == null)
                    errors.add("Container not found: " + container);
            }
            Lookup lu = new Lookup(c, pd.getLookupSchema(), pd.getLookupQuery());
            p.setLookup(lu);
        }
        else
        {
            p.setLookup(null);
        }
    }

    @SuppressWarnings("unchecked")
    private static void updatePropertyValidators(DomainProperty dp, GWTPropertyDescriptor oldPd, GWTPropertyDescriptor newPd)
    {
        Map<Integer, GWTPropertyValidator> newProps = new HashMap<Integer, GWTPropertyValidator>();
        for (GWTPropertyValidator v : (List<GWTPropertyValidator>)newPd.getPropertyValidators())
        {
            if (v.getRowId() != 0)
                newProps.put(v.getRowId(), v);
            else
            {
                String typeURI = new Lsid(ValidatorKind.NAMESPACE, v.getType()).toString();
                IPropertyValidator pv = PropertyService.get().createValidator(typeURI);

                _copyValidator(pv, v);
                dp.addValidator(pv);
            }
        }

        if (oldPd != null)
        {
            List<GWTPropertyValidator> deleted = new ArrayList<GWTPropertyValidator>();
            for (GWTPropertyValidator v : (List<GWTPropertyValidator>)oldPd.getPropertyValidators())
            {
                GWTPropertyValidator prop = newProps.get(v.getRowId());
                if (v.equals(prop))
                    newProps.remove(v.getRowId());
                else if (prop == null)
                    deleted.add(v);
            }

            // update any new or changed
            for (IPropertyValidator pv : dp.getValidators())
                _copyValidator(pv, newProps.get(pv.getRowId()));

            // deal with removed validators
            for (GWTPropertyValidator gpv : deleted)
                dp.removeValidator(gpv.getRowId());
        }
    }

    @SuppressWarnings("unchecked")
    private static void _copyValidator(IPropertyValidator pv, GWTPropertyValidator gpv)
    {
        if (pv != null && gpv != null)
        {
            pv.setName(gpv.getName());
            pv.setDescription(gpv.getDescription());
            pv.setExpressionValue(gpv.getExpression());
            pv.setErrorMessage(gpv.getErrorMessage());

            for (Map.Entry<String, String> entry : ((Map<String, String>)gpv.getProperties()).entrySet())
            {
                pv.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }
}
