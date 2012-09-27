package org.labkey.core.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.query.SimpleTableDomainKind;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 9/18/12
 */
public class UsersDomainKind extends SimpleTableDomainKind
{
    public static final String NAME = "CoreUsersTable";
    private static final Set<String> _reservedNames = new HashSet<String>();

    static {
        _reservedNames.add("Email");
        _reservedNames.add("_ts");
        _reservedNames.add("EntityId");
        _reservedNames.add("CreatedBy");
        _reservedNames.add("Created");
        _reservedNames.add("ModifiedBy");
        _reservedNames.add("Modified");
        _reservedNames.add("Owner");
        _reservedNames.add("UserId");
        _reservedNames.add("DisplayName");
        _reservedNames.add("LastLogin");
        _reservedNames.add("Active");
    }

    @Override
    public String getKindName()
    {
        return NAME;
    }

    public static Container getDomainContainer()
    {
        return ContainerManager.getSharedContainer();
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        return super.urlCreateDefinition(schemaName, queryName, getDomainContainer(), user);
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(containerUser.getContainer(), domain.getTypeURI(), true, true, true);
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
/*
        ActionURL url = super.urlShowData(domain, containerUser);
        return url.setContainer(containerUser.getContainer());
*/
        return PageFlowUtil.urlProvider(UserUrls.class).getSiteUsersURL();
    }

    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user)
    {
        return super.createDomain(domain, arguments, getDomainContainer(), user);
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
/*
        SimpleUserSchema.SimpleTable table = getTable(domain);
        if (table != null)
        {
            // return the set of built-in column names.
            return Sets.newHashSet(Iterables.transform(table.getBuiltInColumns(),
                    new Function<ColumnInfo, String>()
                    {
                        public String apply(ColumnInfo col)
                        {
                            return col.getName();
                        }
                    }
            ));
        }
        return Collections.emptySet();
*/
        return _reservedNames;
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        SimpleUserSchema.SimpleTable table = getTable(domain);
        if (table != null)
        {
            ColumnInfo objectUriColumn = table.getObjectUriColumn();
            if (objectUriColumn != null)
            {
                TableInfo schemaTable = table.getRealTable();

                SQLFragment sql = new SQLFragment();
                sql.append("SELECT o.ObjectId FROM " + schemaTable + " me, exp.object o WHERE me." + objectUriColumn.getSelectName() + " = o.ObjectURI");
                return sql;
            }
        }
        return new SQLFragment("NULL");
    }

    /**
     * Returns the set of built in columns that are managed by property descriptors
     * @return
     */
    public Set<String> getWrappedColumns()
    {
        Set<String> columns = new HashSet<String>();

        columns.add("FirstName");
        columns.add("LastName");
        columns.add("Phone");
        columns.add("Mobile");
        columns.add("Pager");
        columns.add("IM");
        columns.add("Description");

        return columns;
    }

    public static void ensureDomainProperties(Domain domain, User user, boolean isNewInstallation)
    {
        DbScope scope = CoreSchema.getInstance().getSchema().getScope();
        try {
            scope.ensureTransaction();
            Map<String, DomainProperty> existingProps = new HashMap<String, DomainProperty>();
            Map<String, Boolean> requiredMap = new HashMap<String, Boolean>();
            boolean dirty = false;

            if (user == null)
                user = UserManager.getGuestUser();

            for (DomainProperty dp : domain.getProperties())
            {
                existingProps.put(dp.getName(), dp);
            }

            // map in the existing required user fields
            Map<String, String> map = UserManager.getUserPreferences(true);
            if (map.containsKey("UserInfoRequiredFields"))
            {
                String required = map.get("UserInfoRequiredFields");
                for (String field : required.split(";"))
                    requiredMap.put(field, true);
            }
            map.remove("UserInfoRequiredFields");
            PropertyManager.saveProperties(map);

            dirty = (createPropertyDescriptor(domain, user, existingProps, requiredMap, "FirstName", PropertyType.STRING, false)|| dirty);
            dirty = (createPropertyDescriptor(domain, user, existingProps, requiredMap, "LastName", PropertyType.STRING, false) || dirty);
            dirty = (createPropertyDescriptor(domain, user, existingProps, requiredMap, "Phone", PropertyType.STRING, false)    || dirty);
            dirty = (createPropertyDescriptor(domain, user, existingProps, requiredMap, "Mobile", PropertyType.STRING, false)   || dirty);
            dirty = (createPropertyDescriptor(domain, user, existingProps, requiredMap, "Pager", PropertyType.STRING, isNewInstallation)    || dirty);
            dirty = (createPropertyDescriptor(domain, user, existingProps, requiredMap, "IM", PropertyType.STRING, isNewInstallation)       || dirty);
            dirty = (createPropertyDescriptor(domain, user, existingProps, requiredMap, "Description", PropertyType.STRING, isNewInstallation) || dirty);

            if (dirty)
                domain.save(user);

            scope.commitTransaction();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            scope.closeConnection();
        }
    }

    private static boolean createPropertyDescriptor(Domain domain, User user, Map<String, DomainProperty> existing,
                                             Map<String, Boolean> required, String name, PropertyType type, boolean hidden)
    {
        if (!existing.containsKey(name))
        {
            String propertyURI = UsersDomainKind.createPropertyURI("core", CoreQuerySchema.USERS_TABLE_NAME, getDomainContainer(), user);

            DomainProperty prop = domain.addProperty();
            prop.setName(name);
            prop.setType(PropertyService.get().getType(domain.getContainer(), type.getXmlName()));
            prop.setPropertyURI(propertyURI);
            prop.setRequired(required.containsKey(name));

            if (hidden)
            {
                prop.setShownInDetailsView(false);
                prop.setShownInInsertView(false);
                prop.setShownInUpdateView(false);
                prop.setHidden(true);
            }
            return true;
        }
        return false;
    }

    /**
     * Called to determine if a property descriptor can be marked as required. In the user table case we always want to allow
     * this so a user can be redirected at login to furnish newly required fields.
     */
    @Override
    public boolean hasNullValues(Domain domain, DomainProperty prop)
    {
        return false;
    }

    @Override
    public Set<String> getMandatoryPropertyNames(Domain domain)
    {
        return getWrappedColumns();
    }
}
