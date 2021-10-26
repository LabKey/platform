/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.specimen.location.LocationCache;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.GUID;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.StudySchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class LocationTable extends BaseStudyTable
{
    static public ForeignKey fkFor(StudyQuerySchema schema, ContainerFilter cf)
    {
        return QueryForeignKey.from(schema, cf).to("Location", "RowId", "Label").build();
    }

    public LocationTable(StudyQuerySchema schema, ContainerFilter cf)
    {
        super(schema, SpecimenSchema.get().getTableInfoLocation(schema.getContainer()), cf);
        // FK on Container
        var containerColumn = addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Container")));
        containerColumn.setHidden(true);
        containerColumn.setRequired(true);
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("RowId"))).setHidden(true);
        addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("EntityId"))).setHidden(true);

        setName("Location");
        setPublicSchemaName("study");
        var inUse = new LocationInUseExpressionColumn(this);
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

        List<FieldKey> defaultColumns = List.of(
            inUse.getFieldKey(),
            FieldKey.fromParts("ExternalId"),
            FieldKey.fromParts("Label"),
            FieldKey.fromParts("Description"),
            FieldKey.fromParts("Sal"),
            FieldKey.fromParts("Repository"),
            FieldKey.fromParts("Endpoint"),
            FieldKey.fromParts("Clinic")
        );
        setDefaultVisibleColumns(defaultColumns);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return canReadOrIsAdminPermission(user, perm);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo dbTable = SpecimenSchema.get().getTableInfoLocation(getContainer());
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
        protected Map<String, Object> insertRow(User user, Container c, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            if (!c.hasPermission(user, AdminPermission.class))
                throw new UnauthorizedException();

            Map<String, Object> insertedRow;
            try (DbScope.Transaction transaction = StudySchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                insertedRow = super.insertRow(user, c, row);
                transaction.addCommitTask(() -> LocationCache.clear(c), DbScope.CommitTaskOption.IMMEDIATE, DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();
            }
            return insertedRow;
        }

        @Override
        public List<Map<String, Object>> updateRows(User user, Container c, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {
            if (!c.hasPermission(user, AdminPermission.class))
                throw new UnauthorizedException();

            // this should not get called with > 1 row
            if (1 != rows.size())
                throw new IllegalStateException("Bulk update not allowed on locations.");

            Map<String, Object> map = rows.get(0);

            Integer locId = (Integer)map.get("RowId");
            if (null == locId)
                throw new InvalidKeyException("Invalid location ID");

            LocationImpl loc = LocationManager.get().getLocation(c, locId);
            if (null == loc)
                throw new BatchValidationException(Collections.singletonList(new ValidationException("Location not found: " + locId)), extraScriptContext);
            loc.createMutable();    // Test mutability

            try
            {
                return super.updateRows(user, c, rows, oldKeys, configParameters, extraScriptContext);
            }
            finally
            {
                LocationCache.clear(c);
            }
        }

        @Override
        public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException
        {
            assert null == configParameters && null == extraScriptContext;      // We're not expecting these for this table
            LocationManager mgr = LocationManager.get();

            List<ValidationException> validationExceptions = new ArrayList<>();
            for (Map<String, Object> map : keys)
            {
                Integer locId = (Integer)map.get("RowId");
                if (null == locId)
                    throw new InvalidKeyException("Invalid location ID");

                String cid = (String)map.get("Container");
                if (null == cid)
                    throw new IllegalStateException("Invalid container ID");

                Container c = ContainerManager.getForId(cid);
                if (null == c)
                    throw new IllegalStateException("Container not found");

                if (!c.hasPermission(user, AdminPermission.class))
                    throw new UnauthorizedException();

                LocationImpl loc = mgr.getLocation(c, locId);
                if (null == loc)
                {
                    validationExceptions.add(new ValidationException("Location not found: " + locId));
                    continue;
                }

                if (LocationManager.get().isLocationInUse(loc))
                {
                    validationExceptions.add(new ValidationException("Location is currently in use and cannot be deleted: " + loc.getDisplayName()));
                    continue;
                }

                try
                {
                    // Note: deleteLocation() clears the location cache
                    LocationManager.get().deleteLocation(loc);
                }
                catch (ValidationException e)
                {
                    validationExceptions.add(e);
                }
            }

            if (!validationExceptions.isEmpty())
                throw new BatchValidationException(validationExceptions, extraScriptContext);

            return Collections.emptyList();
        }
    }

    private class LocationInUseExpressionColumn extends ExprColumn
    {
        public LocationInUseExpressionColumn(TableInfo locationTable)
        {
            super(locationTable, "In Use", null, JdbcType.BOOLEAN);
        }

        @Override
        public SQLFragment getValueSql(String locationTableAlias)
        {
            return getLocationInUseExpression(locationTableAlias);
        }
    }

    private static final String EXISTS = "    EXISTS(SELECT 1 FROM ";

    private SQLFragment getLocationInUseExpression(String locationTableAlias)
    {
        final SpecimenSchema schema = SpecimenSchema.get();

        // Because Location is now provisioned, each row's container must match container in the target table to be an InUse match
        var inUseColumn = getRealTable().getColumn("InUse");
        SQLFragment existsSQL = new SQLFragment();
        existsSQL.append(inUseColumn.getValueSql(locationTableAlias));
        if (schema.getSqlDialect().isSqlServer())
            existsSQL.append(" = 1");

        existsSQL
            .append(" OR\n")
            .append(EXISTS)
            .append(schema.getTableInfoSampleRequest(), "sr")
            .append(" WHERE ")
            .append(locationTableAlias)
            .append(".RowId = sr.DestinationSiteId AND ")
            .append(locationTableAlias)
            .append(".Container = sr.Container) OR\n")
            .append(EXISTS)
            .append(schema.getTableInfoSampleRequestRequirement(), "srr")
            .append(" WHERE ")
            .append(locationTableAlias)
            .append(".RowId = srr.SiteId AND ")
            .append(locationTableAlias)
            .append(".Container = srr.Container) OR\n");

        StudyService.get().appendLocationInUseClauses(existsSQL, locationTableAlias, EXISTS);

        // Wrap the EXISTS expression as needed for this dialect
        return schema.getSqlDialect().wrapExistsExpression(existsSQL);
    }

    public static Collection<Container> getStudyContainers(Container root, ContainerFilter cFilter)
    {
        Collection<GUID> ids = cFilter.getIds();

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

                if (null != StudyService.get().getStudy(c))
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
