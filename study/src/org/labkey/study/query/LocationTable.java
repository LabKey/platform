/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.GUID;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.StudySchema;
import org.labkey.study.model.LocationImpl;
import org.labkey.study.model.StudyManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class LocationTable extends BaseStudyTable
{
    static public ForeignKey fkFor(StudyQuerySchema schema)
    {
        return new QueryForeignKey(schema, null, "Location", "RowId", "Label");
    }

    public LocationTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSite(schema.getContainer()));
        // FK on Container
        ColumnInfo containerColumn = ContainerForeignKey.initColumn(addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Container"))), schema);
        containerColumn.setHidden(true);
        containerColumn.setRequired(true);
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("RowId"))).setHidden(true);
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("EntityId"))).setHidden(true);

        setName("Location");
        setPublicSchemaName("study");
        ColumnInfo inUse = new LocationInUseExpressionColumn(this);
        addColumn(inUse);
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("ExternalId")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("LdmsLabCode")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("LabwareLabCode")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("LabUploadCode")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Label")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Description")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Sal")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Repository")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Endpoint")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Clinic")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("StreetAddress")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("City")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("GoverningDistrict")));
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("PostalArea")));

        List<FieldKey> defaultColumns = new ArrayList<>(Arrays.asList(
                inUse.getFieldKey(),
                FieldKey.fromParts("ExternalId"),
                FieldKey.fromParts("Label"),
                FieldKey.fromParts("Description"),
                FieldKey.fromParts("Sal"),
                FieldKey.fromParts("Repository"),
                FieldKey.fromParts("Endpoint"),
                FieldKey.fromParts("Clinic")
        ));
        setDefaultVisibleColumns(defaultColumns);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return _userSchema.getContainer().hasPermission(this.getClass().getName() + " " + getName(), user, AdminPermission.class);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo dbTable = StudySchema.getInstance().getTableInfoSite(getContainer());
        return new LocationQueryUpdateService(this, dbTable);
    }

    @Override
    public String getTitleColumn()
    {
        return "Label";
    }

    private class LocationQueryUpdateService extends DefaultQueryUpdateService
    {
        public LocationQueryUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        public List<Map<String, Object>> updateRows(User user, Container c, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {
            if (!c.hasPermission(user, AdminPermission.class))
                throw new UnauthorizedException();

            Map<String, Object> map = rows.get(0);

            Integer locId = (Integer)map.get("RowId");
            if (null == locId)
                throw new InvalidKeyException("Invalid location ID");

            LocationImpl loc = StudyManager.getInstance().getLocation(c, locId);
            if (null == loc)
                throw new InvalidKeyException("Location not found");
            loc.createMutable();    // Test mutability

            return super.updateRows(user, c, rows, oldKeys, configParameters, extraScriptContext);
        }

        @Override
        public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {
            StudyManager mgr = StudyManager.getInstance();

            for (Map<String, Object> map : keys)
            {
                Integer locId = (Integer)map.get("RowId");
                if (null == locId)
                    throw new InvalidKeyException("Invalid location ID");

                String cid = (String)map.get("Container");
                if (null == cid)
                    throw new InvalidKeyException("Invalid container ID");

                Container c = ContainerManager.getForId(cid);
                if (null == c)
                    throw new InvalidKeyException("Container not found");

                if (!c.hasPermission(user, AdminPermission.class))
                    throw new UnauthorizedException();

                LocationImpl loc = mgr.getLocation(c, locId);
                if (null == loc)
                    throw new InvalidKeyException("Location not found");

                if (mgr.isLocationInUse(loc))
                    throw new InvalidKeyException("Locations currently in use cannot be deleted");
            }

            return super.deleteRows(user, container, keys, configParameters, extraScriptContext);
        }
    }

    private class LocationInUseExpressionColumn extends ExprColumn
    {
        public LocationInUseExpressionColumn(TableInfo parent)
        {
            super(parent, "In Use", null, JdbcType.BOOLEAN);
        }

        @Override
        public SQLFragment getValueSql(String tableAlias)
        {
            return getLocationInUseExpression(tableAlias);
        }
    }

    public SQLFragment getLocationInUseExpression(String tableAlias)
    {
        final String EXISTS = "    EXISTS(SELECT 1 FROM ";
        final StudySchema schema = StudySchema.getInstance();

        // These are all site-wide tables, so just check for current RowId in use anywhere (regardless of current container filter)
        SQLFragment existsSQL = new SQLFragment(EXISTS).append(schema.getTableInfoSampleRequest(), "sr").append(" WHERE ").append(tableAlias).append(".RowId = sr.DestinationSiteId) OR\n")
                .append(EXISTS).append(schema.getTableInfoSampleRequestRequirement(), "srr").append(" WHERE ").append(tableAlias).append(".RowId = srr.SiteId) OR\n")
                .append(EXISTS).append(schema.getTableInfoParticipant(), "p").append(" WHERE ").append(tableAlias).append(".RowId = p.EnrollmentSiteId OR ").append(tableAlias).append(".RowId = p.CurrentSiteId) OR\n")
                .append(EXISTS).append(schema.getTableInfoAssaySpecimen(), "a").append(" WHERE ").append(tableAlias).append(".RowId = a.LocationId)");

        // Specimen tables are provisioned per container, so include all specimen tables in all study folders referenced by the current container filter
        for (Container c : getStudyContainers(getContainer(), getContainerFilter()))
        {
            TableInfo eventTableInfo = schema.getTableInfoSpecimenEventIfExists(c);
            if (null != eventTableInfo)
                existsSQL.append(" OR\n" + EXISTS).append(eventTableInfo, "se").append(" WHERE ").append(tableAlias).append(".RowId = se.LabId OR ").append(tableAlias).append(".RowId = se.OriginatingLocationId)");

            TableInfo vialTableInfo = schema.getTableInfoVialIfExists(c);
            if (null != vialTableInfo)
                existsSQL.append(" OR\n" + EXISTS).append(vialTableInfo, "v").append(" WHERE ").append(tableAlias).append(".RowId = v.CurrentLocation OR ").append(tableAlias).append(".RowId = v.ProcessingLocation)");

            TableInfo specimentTableInfo = schema.getTableInfoSpecimenIfExists(c);
            if (null != specimentTableInfo)
                existsSQL.append(" OR\n" + EXISTS).append(specimentTableInfo, "s").append(" WHERE ").append(tableAlias).append(".RowId = s.OriginatingLocationId OR ").append(tableAlias).append(".RowId = s.ProcessingLocation)");
        }

        // Wrap the EXISTS expression as needed for this dialect
        return schema.getSqlDialect().wrapExistsExpression(existsSQL);
    }

    public static Collection<Container> getStudyContainers(Container root, ContainerFilter cFilter)
    {
        Collection<GUID> ids = cFilter.getIds(root);

        if (null == ids)
        {
            return Collections.singleton(root);
        }
        else
        {
            List<Container> studyContainers = new LinkedList<>();

            for (GUID id : ids)
            {
                Container c = ContainerManager.getForId(id);

                if (null != StudyManager.getInstance().getStudy(c))
                    studyContainers.add(c);
            }

            return studyContainers;
        }
    }

    @Override
    public ContainerContext getContainerContext()
    {
        return new ContainerContext.FieldKeyContext(new FieldKey(null,"Container"));
    }

    @Override
    public boolean hasUnionTable()
    {
        return true;
    }
}
