/*
 * Copyright (c) 2008 LabKey Corporation
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
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;

import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.*;

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

    @Nullable
    public static GWTDomain getDomainDescriptor(String typeURI, Container domainContainer)
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

            // add system properties
            DomainKind domainKind = domain.getDomainKind();
            DomainProperty[] domainProperties = null == domainKind ? new DomainProperty[0] : domainKind.getDomainProperties(typeURI);
            for (DomainProperty domainProperty : domainProperties)
            {
                GWTPropertyDescriptor p = new GWTPropertyDescriptor();
                p.setName(domainProperty.getName());
                p.setLabel(domainProperty.getLabel());
                p.setRangeURI(domainProperty.getType().getTypeURI());
                p.setRequired(!domainProperty.isRequired());
                p.setDescription(domainProperty.getDescription());
                p.setEditable(false);
                list.add(p);
            }

            for (DomainProperty prop : domain.getProperties())
            {
                GWTPropertyDescriptor p = getPropertyDescriptor(prop);
                list.add(p);
            }

            d.setPropertyDescriptors(list);
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
            gpv.setProperties(new HashMap(pv.getProperties()));

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

        if (!d.getDomainKind().canEditDefinition(user, d))
        {
            errors.add("Unauthorized");
            return errors;
        }

        // validate names
        // look for swapped names

        // first delete properties
        Set<Integer> s = new HashSet<Integer>();
        for (GWTPropertyDescriptor pd : (List<GWTPropertyDescriptor>) orig.getPropertyDescriptors())
            s.add(pd.getPropertyId());
        for (GWTPropertyDescriptor pd : (List<GWTPropertyDescriptor>) update.getPropertyDescriptors())
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
        for (GWTPropertyDescriptor pd : (List<GWTPropertyDescriptor>) update.getPropertyDescriptors())
        {
            if (pd.getPropertyId() <= 0 || !pd.isEditable())
                continue;
            GWTPropertyDescriptor old = null;
            for (GWTPropertyDescriptor t : (List<GWTPropertyDescriptor>) orig.getPropertyDescriptors())
            {
                if (t.getPropertyId() == pd.getPropertyId())
                {
                    old = t;
                    break;
                }
            }
            // UNDONE: DomainProperty does not support all PropertyDescriptor fields
            DomainProperty p = d.getProperty(pd.getPropertyId());

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

        // now add properties
        for (GWTPropertyDescriptor pd : (List<GWTPropertyDescriptor>) update.getPropertyDescriptors())
        {
            if (pd.getPropertyId() > 0 || !pd.isEditable())
                continue;

            if (pd.getPropertyURI() == null)
                pd.setPropertyURI(update.getDomainURI() + "#" + pd.getName());

            // UNDONE: DomainProperty does not support all PropertyDescriptor fields
            DomainProperty p = d.addProperty();
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
            }
        }
        catch (IllegalStateException x)
        {
            errors.add(x.getMessage());
        }

        return errors.size() > 0 ? errors : null;
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
