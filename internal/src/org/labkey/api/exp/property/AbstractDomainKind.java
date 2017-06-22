/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: kevink
 * Date: Jun 4, 2010
 * Time: 3:29:46 PM
 */
public abstract class AbstractDomainKind extends DomainKind
{
    @Override
    public String generateDomainURI(String schemaName, String queryName, Container container, User user)
    {
        return null;
    }


    public boolean canCreateDefinition(User user, Container container)
    {
        return false;
    }

    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, AdminPermission.class);
    }

    @Override
    public boolean canDeleteDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, AdminPermission.class);
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        String domainURI = generateDomainURI(schemaName, queryName, container, user);
        if (domainURI == null)
            return null;

        ActionURL ret = PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(container, domainURI, false, false, false);
        ret.addParameter("createOrEdit", true);
        return ret;
    }

    // Override to customize the nav trail on shared pages like edit domain
    public void appendNavTrail(NavTree root, Container c, User user)
    {
    }

    // Do any special handling before a PropertyDescriptor is deleted -- do nothing by default
    public void deletePropertyDescriptor(Domain domain, User user, PropertyDescriptor pd)
    {
    }

    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user, @Nullable TemplateInfo templateInfo)
    {
        return null;
    }

    /** @return Errors encountered during the save attempt */
    @NotNull
    public List<String> updateDomain(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update, Container container, User user)
    {
        return DomainUtil.updateDomainDescriptor(original, update, container, user);
    }

    @Override
    public void deleteDomain(User user, Domain domain)
    {
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        return Collections.emptySet();
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        return Collections.emptySet();
    }

    @Override
    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container)
    {
        return Collections.emptySet();
    }

    @Override
    public DbScope getScope()
    {
        return null;
    }

    @Override
    public String getStorageSchemaName()
    {
        return null;
    }


    @Override
    public boolean hasNullValues(Domain domain, DomainProperty prop)
    {
        SQLFragment allRowsSQL;
        SQLFragment nonBlankRowsSQL;
        if (getStorageSchemaName() == null)
        {
            SQLFragment sqlObjectIds = sqlObjectIdsInDomain(domain);
            allRowsSQL = new SQLFragment("SELECT exp.object.objectId FROM exp.object WHERE exp.object.objectid IN (");
            allRowsSQL.append(sqlObjectIds);
            allRowsSQL.append(")");

            nonBlankRowsSQL = new SQLFragment("SELECT op.objectId FROM exp.objectproperty op WHERE ");
            nonBlankRowsSQL.append("(op.StringValue IS NOT NULL OR op.FloatValue IS NOT NULL OR ");
            nonBlankRowsSQL.append("op.DateTimeValue IS NOT NULL OR op.MVIndicator IS NULL) AND op.objectid IN (");
            nonBlankRowsSQL.append(sqlObjectIds);
            nonBlankRowsSQL.append(") AND op.PropertyId = ?");
            nonBlankRowsSQL.add(prop.getPropertyId());
        }
        else if (domain.getStorageTableName() != null)
        {
            String table = domain.getStorageTableName();
            allRowsSQL = new SQLFragment("SELECT * FROM " + getStorageSchemaName() + "." + table);
            nonBlankRowsSQL = new SQLFragment("SELECT * FROM " + getStorageSchemaName() + "." + table + " x WHERE ");
            SqlDialect dialect = CoreSchema.getInstance().getSqlDialect();
            // Issue 17183 - Postgres uses lower case column names when quoting is required
            nonBlankRowsSQL.append("x.");
            // Issue 29047
            nonBlankRowsSQL.append(dialect.makeLegalIdentifier(prop.getPropertyDescriptor().getStorageColumnName().toLowerCase()));
            nonBlankRowsSQL.append(" IS NOT NULL");
            if (prop.isMvEnabled())
            {
                TableInfo storageTable = DbSchema.get(getStorageSchemaName(), DbSchemaType.Provisioned).getTable(table);
                ColumnInfo mvColumn = StorageProvisioner.getMvIndicatorColumn(storageTable, prop.getPropertyDescriptor(), "No MV column found for" + prop.getName());
                nonBlankRowsSQL.append(" OR x.");
                nonBlankRowsSQL.append(mvColumn.getName().toLowerCase());
                nonBlankRowsSQL.append(" IS NOT NULL");
            }
        }
        else
        {
            return false;
        }

        long totalRows = new SqlSelector(ExperimentService.get().getSchema(), allRowsSQL).getRowCount();
        long nonBlankRows = new SqlSelector(ExperimentService.get().getSchema(), nonBlankRowsSQL).getRowCount();
        return totalRows != nonBlankRows;
    }


    @Override
    public Set<String> getMandatoryPropertyNames(Domain domain)
    {
        TreeSet<String> ret = new TreeSet<>();
        for (PropertyStorageSpec spec : getBaseProperties(domain))
            ret.add(spec.getName());

        DomainTemplate template = getDomainTemplate(domain);
        if (template != null)
            ret.addAll(template.getMandatoryPropertyNames());

        return ret;
    }

    @Nullable
    protected DomainTemplate getDomainTemplate(Domain domain)
    {
        return DomainTemplate.findTemplate(domain.getTemplateInfo(), getKindName());
    }


    @Override
    public Set<String> getNonProvisionedTableNames()
    {
        return Collections.emptySet();
    }

    @Override
    public PropertyStorageSpec getPropertySpec(PropertyDescriptor pd, Domain domain)
    {
        return new PropertyStorageSpec(pd);
    }

    @Nullable
    @Override
    public String getMetaDataSchemaName()
    {
        return null;
    }

    @Nullable
    @Override
    public String getMetaDataTableName()
    {
        return null;
    }

    @Override
    public boolean hasPropertiesIncludeBaseProperties()
    {
        return true;
    }

    /**
     * Check if existing string data fits in property scale
     * @param domain to execute within
     * @param prop property to check
     * @return true if the DomainProperty is a string and a value exists that is greater than the DomainProperty's max length
     */
    @Override
    public boolean exceedsMaxLength(Domain domain, DomainProperty prop)
    {
        if (prop.getPropertyDescriptor().isStringType())
            return false;

        String schema = getStorageSchemaName();
        if (schema == null || domain.getStorageTableName() == null)
            return false;

        SqlDialect dialect = CoreSchema.getInstance().getSqlDialect();
        SQLFragment sql = new SQLFragment(String.format("SELECT coalesce(MAX(%s(%s)),0) FROM %s.%s",
                dialect.getVarcharLengthFunction(),
                //Lowercase names for postgres (MSSQL is case insensitive in this case)
                dialect.makeLegalIdentifier(prop.getName().toLowerCase()),
                dialect.makeLegalIdentifier(schema.toLowerCase()),
                dialect.makeLegalIdentifier(domain.getStorageTableName().toLowerCase())
        ));

        int maxSize = new SqlSelector(ExperimentService.get().getSchema(), sql).getObject(Integer.class);
        return prop.getScale() < maxSize;
    }
}
