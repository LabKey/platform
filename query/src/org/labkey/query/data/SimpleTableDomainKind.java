package org.labkey.query.data;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.module.SimpleModule;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: Sep 22, 2010
 *
 * A domain kind for {@link SimpleUserSchema.SimpleTable}s.
 */
public class SimpleTableDomainKind extends AbstractDomainKind
{
    private static String XAR_SUBSTITUTION_SCHEMA_NAME = "SchemaName";
    private static String XAR_SUBSTITUTION_TABLE_NAME = "TableName";
    private static String XAR_SUBSTITUTION_GUID = "GUID";

    public SimpleTableDomainKind()
    {
    }

    // uck. To get from a Domain to the SimpleTable we need to parse the Domain's type uri.
    public SimpleUserSchema.SimpleTable getTable(Domain domain)
    {
        String domainURI = domain.getTypeURI();
        if (domainURI == null)
            return null;

        Lsid lsid = new Lsid(domainURI);
        String prefix = lsid.getNamespacePrefix();
        if (prefix.startsWith(SimpleModule.NAMESPACE_PREFIX))
        {
            // XXX: need to unescape '-' (and '.' ?) in schema and table names
            String schemaName = prefix.substring(SimpleModule.NAMESPACE_PREFIX.length() + 1);
            String queryName = lsid.getObjectId();

            // HACK: have to reach out to get the user
            User user = HttpView.currentContext().getUser();
            UserSchema schema = QueryService.get().getUserSchema(user, domain.getContainer(), schemaName);
            if (schema != null)
            {
                TableInfo table = schema.getTable(queryName, true);
                if (table instanceof SimpleUserSchema.SimpleTable)
                    return (SimpleUserSchema.SimpleTable)table;
            }
        }

        return null;
    }

    @Override
    public String getKindName()
    {
        return SimpleModule.NAMESPACE_PREFIX;
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return "Extensible Table '" + domain.getName() + "'";
    }

    @Override
    public boolean isDomainType(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return lsid.getNamespacePrefix().startsWith(SimpleModule.NAMESPACE_PREFIX);
    }

    public String generateDomainURI(String schemaName, String tableName, Container c, User u)
    {
        return getDomainURI(schemaName, tableName, c, u);
    }

    public static String getDomainURI(String schemaName, String tableName, Container c, User u)
    {
        try
        {
            XarContext xc = getXarContext(schemaName, tableName, c, u);
            String lsid = LsidUtils.resolveLsidFromTemplate(SimpleModule.DOMAIN_LSID_TEMPLATE, xc, SimpleModule.DOMAIN_NAMESPACE_PREFIX_TEMPLATE);
            return lsid;
        }
        catch (XarFormatException xfe)
        {
            return null;
        }
    }

    public static String getPropertyURI(String schemaName, String tableName, Container c, User u)
    {
        try
        {
            XarContext xc = getXarContext(schemaName, tableName, c, u);
            xc.addSubstitution(XAR_SUBSTITUTION_GUID, GUID.makeGUID());
            return LsidUtils.resolveLsidFromTemplate(SimpleModule.PROPERTY_LSID_TEMPLATE, xc, SimpleModule.PROPERTY_NAMESPACE_PREFIX_TEMPLATE);
        }
        catch (XarFormatException xfe)
        {
            return null;
        }
    }

    private static XarContext getXarContext(String schemaName, String tableName, Container c, User u)
    {
        // XXX: need to escape '-' (and '.' ?) in the schema and table names
        XarContext xc = new XarContext("Domains", c, u);
        xc.addSubstitution(XAR_SUBSTITUTION_SCHEMA_NAME, schemaName);
        xc.addSubstitution(XAR_SUBSTITUTION_TABLE_NAME, tableName);
        return xc;
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
                sql.append("SELECT o.ObjectId FROM " + schemaTable + " me, exp.objet o WHERE me." + objectUriColumn.getSelectName() + " = o.ObjectURI" /* + " AND o.Container = me.Container" */);
                return sql;
            }
        }
        return new SQLFragment("NULL");
    }

    @Override
    public Pair<TableInfo, ColumnInfo> getTableInfo(User user, Domain domain, Container[] containers)
    {
        return null;
    }

    @Override
    public ActionURL urlShowData(Domain domain)
    {
        SimpleUserSchema.SimpleTable table = getTable(domain);
        if (table == null)
            return null;

        // nasty
        QueryDefinition qdef = table._userSchema.getQueryDefForTable(table.getName());
        if (qdef != null)
            return table._userSchema.urlFor(QueryAction.executeQuery, qdef);

        return null;
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain)
    {
        return domain.urlEditDefinition(true, true, true);
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission(user, AdminPermission.class);
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, AdminPermission.class);
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        SimpleUserSchema.SimpleTable table = getTable(domain);
        if (table != null)
        {
            // return the set of built-in column names.
            return Sets.newHashSet(Iterables.transform(table.getBuiltInColumns(),
                new Function<ColumnInfo, String>() {
                    public String apply(ColumnInfo col)
                    {
                        return col.getName();
                    }
                }
            ));
        }
        return Collections.emptySet();
    }

    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user)
    {
        String schemaName = (String)arguments.get("schemaName");
        String tableName = (String)arguments.get("tableName");
        if (schemaName == null || tableName == null)
            throw new IllegalArgumentException("schemaName and tableName are required");

        String domainURI = generateDomainURI(schemaName, tableName, container, user);
        return PropertyService.get().createDomain(container, domainURI, domain.getName());
    }

}

