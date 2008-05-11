package org.labkey.api.exp.property;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.*;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.util.GUID;

import java.util.*;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 4, 2007
 * Time: 10:55:27 AM
 * <p/>
 * Base class for building GWT editors that edit domains
 *
 * @see org.labkey.api.gwt.client.ui.PropertiesEditor in InternalGWT
 */
public class DomainEditorServiceBase extends BaseRemoteService
{
    public DomainEditorServiceBase(ViewContext context)
    {
        super(context);
    }


    // path -> containerid
    public List getContainers()
    {
        try
        {
            Set<Container> set = ContainerManager.getAllChildren(ContainerManager.getRoot(), getUser(), ACL.PERM_READ);
            List list = new ArrayList();
            for (Container c : set)
            {
                if (c.isRoot())
                    continue;
                list.add(c.getPath());
            }
            Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
            return list;
        }
        catch (RuntimeException x)
        {
            Logger.getLogger(DomainEditorServiceBase.class).error("unexpected error", x);
            throw x;
        }
    }


    public List getSchemas(String containerId)
    {
        try
        {
            DefaultSchema defSchema = getSchemaForContainer(containerId);
            List list = new ArrayList();
            if (null != defSchema.getUserSchemaNames())
                for (String schemaName : defSchema.getUserSchemaNames())
                    list.add(schemaName);
            return list;
        }
        catch (RuntimeException x)
        {
            Logger.getLogger(DomainEditorServiceBase.class).error("unexpected error", x);
            throw x;
        }
    }


    public Map getTablesForLookup(String containerId, String schemaName)
    {
        try
        {
            DefaultSchema defSchema = getSchemaForContainer(containerId);
            QuerySchema qSchema = defSchema.getSchema(schemaName);
            if (qSchema == null || !(qSchema instanceof UserSchema))
                return null;

            UserSchema schema = (UserSchema) qSchema;
            Map<String, String> availableQueries = new HashMap<String, String>();  //  GWT: TreeMap does not work
            for (String name : schema.getTableAndQueryNames(false))
            {
                TableInfo table = schema.getTable(name, null);
                if (table == null)
                    continue;
                List<ColumnInfo> pkColumns = table.getPkColumns();
                if (pkColumns.size() != 1)
                    continue;
                availableQueries.put(name, pkColumns.get(0).getName());
            }
            return availableQueries;
        }
        catch (RuntimeException x)
        {
            Logger.getLogger(DomainEditorServiceBase.class).error("unexpected error", x);
            throw x;
        }
    }


    private DefaultSchema getSchemaForContainer(String containerId)
    {
        Container container = null;
        if (containerId == null || containerId.length() == 0)
            container = getContainer();
        else
        {
            if (GUID.isGUID(containerId))
                container = ContainerManager.getForId(containerId);
            if (null == container)
                container = ContainerManager.getForPath(containerId);
        }

        if (container == null)
        {
            throw new IllegalArgumentException(containerId);
        }
        else if (!container.hasPermission(getUser(), ACL.PERM_READ))
        {
            throw new IllegalStateException("You do not have permissions to see this folder.");
        }

        DefaultSchema defSchema = DefaultSchema.get(getUser(), container);
        return defSchema;
    }

    public GWTDomain getDomainDescriptor(String typeURI) throws Exception
    {
        return getDomainDescriptor(typeURI, getContainer());
    }

    public GWTDomain getDomainDescriptor(String typeURI, String domainContainerId) throws Exception
    {
        Container domainContainer = ContainerManager.getForId(domainContainerId);
        return getDomainDescriptor(typeURI, domainContainer);
    }
    
    protected GWTDomain getDomainDescriptor(String typeURI, Container domainContainer)
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

            PropertyDescriptor[] pds = OntologyManager.getPropertiesForType(typeURI, domainContainer);
            for (PropertyDescriptor pd : pds)
            {
                GWTPropertyDescriptor p = new GWTPropertyDescriptor();
                PropertyUtils.copyProperties(p, pd);
                // We translate the lookupContainer entityid into a path, because that's a value the user will
                // understand.
                if (pd.getLookupContainer() != null)
                {
                    Container c = ContainerManager.getForId(pd.getLookupContainer());
                    if (c != null)
                    {
                        p.setLookupContainer(c.getPath());
                    }
                    else
                    {
                        // If the container doesn't exist, we just blank out the value, since they won't be able to set it anyway.
                        p.setLookupContainer("");
                    }
                }
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

    public boolean canUpdate(User user, Domain domain)
    {
        return getContainer().hasPermission(getUser(), ACL.PERM_ADMIN);
    }

    public List updateDomainDescriptor(GWTDomain orig, GWTDomain update) throws ChangePropertyDescriptorException
    {
        assert orig.getDomainURI().equals(update.getDomainURI());
        List<String> errors = new ArrayList<String>();

        Domain d = PropertyService.get().getDomain(getContainer(), update.getDomainURI());
        if (null == d)
        {
            errors.add("Domain not found: " + update.getDomainURI());
            return errors;
        }

        if (!canUpdate(getUser(), d))
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
            if (old == null)
                continue;
            if (old.equals(pd))
                continue;

            // UNDONE: DomainProperty does not support all PropertyDescriptor fields
            DomainProperty p = d.getProperty(pd.getPropertyId());
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
                d.save(getUser());
            }
        }
        catch (IllegalStateException x)
        {
            errors.add(x.getMessage());
        }

        return errors.size() > 0 ? errors : null;
    }

    private void _copyProperties(DomainProperty p, GWTPropertyDescriptor pd, List<String> errors)
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
}
