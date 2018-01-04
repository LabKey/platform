/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.study;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Constraint;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.DatasetDomainKind;
import org.labkey.study.model.DoseAndRoute;
import org.labkey.study.model.LocationDomainKind;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.TreatmentManager;
import org.labkey.study.model.TreatmentProductImpl;
import org.labkey.study.query.LocationTable;
import org.labkey.study.query.SpecimenTablesProvider;
import org.labkey.study.query.StudyQuerySchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: Nov 25, 2008
 * Time: 4:54:36 PM
 */
public class StudyUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(StudyUpgradeCode.class);

    // Invoked by study-16.10-16.20.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void upgradeLocationTables(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            LocationDomainKind domainKind = new LocationDomainKind();
            for (Container container : ContainerManager.getAllChildren(ContainerManager.getRoot()))
            {
                if (null != container)
                {
                    Study study = StudyManager.getInstance().getStudy(container);
                    if (null != study)
                    {
                        try
                        {
                            SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, context.getUpgradeUser(), null);
                            Domain domain = specimenTablesProvider.getDomain(SpecimenTablesProvider.LOCATION_TABLENAME, true);
                            if (null != domain)
                            {
                                specimenTablesProvider.ensureAddedProperties(context.getUpgradeUser(), domain, domainKind, domainKind.getAddedProperties());
                                TableInfo table = specimenTablesProvider.getTableInfoIfExists(SpecimenTablesProvider.LOCATION_TABLENAME);
                                if (null != table)
                                    LocationTable.updateLocationTableInUse(table, container);
                            }
                        }
                        catch (Exception e)
                        {
                            _log.error("Error upgrading location table in folder '" + container.getName() + "'", e);
                        }
                    }
                }
            }
        }
    }

    // Invoked by study-16.20-16.30.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void updatePKAndContainer(final ModuleContext context) throws Exception
    {
        if (!context.isNewInstall())
        {
            try (DbScope.Transaction transaction = StudySchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                List<String> updated = new ArrayList<>();
                Set<Container> allContainers = ContainerManager.getAllChildren(ContainerManager.getRoot());
                for (Container c : allContainers)
                {
                    Study study = StudyManager.getInstance().getStudy(c);
                    if (null != study)
                    {
                        List<? extends Dataset> datasets = study.getDatasets();
                        for (Dataset dataset : datasets)
                        {
                            Domain domain = dataset.getDomain();
                            if (domain != null)
                            {
                                if(updated.contains(domain.getStorageTableName()))
                                    continue;

                                // These have changed in DatasetDomainKind so need to identify old indices to delete
                                PropertyStorageSpec.Index[] indices = {
                                        new PropertyStorageSpec.Index(false, DatasetDomainKind.PARTICIPANTID, DatasetDomainKind.DATE),
                                        new PropertyStorageSpec.Index(false, DatasetDomainKind.CONTAINER, DatasetDomainKind.QCSTATE),
                                        new PropertyStorageSpec.Index(false, DatasetDomainKind.CONTAINER, DatasetDomainKind.PARTICIPANTSEQUENCENUM),
                                        new PropertyStorageSpec.Index(true, DatasetDomainKind.CONTAINER, DatasetDomainKind.PARTICIPANTID, DatasetDomainKind.SEQUENCENUM, DatasetDomainKind._KEY)
                                };
                                Set<PropertyStorageSpec.Index> dropIndices = new HashSet<>(Arrays.asList(indices));

                                // Container column to be dropped
                                Collection<PropertyStorageSpec> propsDropped = new ArrayList<>();
                                propsDropped.add(new PropertyStorageSpec(DatasetDomainKind.CONTAINER, JdbcType.GUID));

                                // Lsid primary key to be dropped
                                Constraint dropPk = new Constraint(domain.getStorageTableName(), Constraint.CONSTRAINT_TYPES.PRIMARYKEY, false, null);
                                List<Constraint> constDrops = new ArrayList<>();
                                constDrops.add(dropPk);

                                // New primary key
                                PropertyStorageSpec ps = new PropertyStorageSpec(DatasetDomainKind.DSROWID, JdbcType.BIGINT, 0,
                                        PropertyStorageSpec.Special.PrimaryKeyNonClustered, false, true, null);
                                Collection<PropertyStorageSpec> propsAdded = new ArrayList<>();
                                propsAdded.add(ps);

                                try
                                {
                                    // Drop indices, drop container, drop pk, add rowid pk, rebuild indices
                                    StorageProvisioner.addOrDropTableIndices(domain, dropIndices, false, TableChange.IndexSizeMode.Normal);
                                    if (!StudyManager.getInstance().getStudy(domain.getContainer()).isDataspaceStudy())
                                    {
                                        StorageProvisioner.dropStorageProperties(domain, propsDropped);
                                    }

                                    StorageProvisioner.addOrDropConstraints(domain, constDrops, false);
                                    StorageProvisioner.addStorageProperties(domain, propsAdded, true);
                                    StorageProvisioner.addOrDropTableIndices(domain, null, true, null);
                                    updated.add(domain.getStorageTableName());
                                }
                                catch (Exception e)
                                {
                                    _log.error("Error upgrading study dataset schemas: ", e);
                                    throw (e);
                                }
                            }
                        }
                    }
                }
                transaction.commit();
            }
        }
    }

    /**
     * Populate the new DoseAndRoute table with existing entries from the ProductTreatmentMap provisioned table, so they can
     * be used in the update study design tool.
     *
     * Invoked by study-16.20-16.30.sql
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void populateDoseAndRoute(final ModuleContext context) throws Exception
    {
        if (!context.isNewInstall())
        {
            try (DbScope.Transaction transaction = StudySchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                Set<Container> allContainers = ContainerManager.getAllChildren(ContainerManager.getRoot());
                for (Container c : allContainers)
                {
                    UserSchema schema = QueryService.get().getUserSchema(context.getUpgradeUser(), c, StudyQuerySchema.SCHEMA_NAME);
                    if (schema != null)
                    {
                        TableInfo ti = schema.getTable(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME);
                        if (ti != null)
                        {
                            for (TreatmentProductImpl product : new TableSelector(ti).getArrayList(TreatmentProductImpl.class))
                            {
                                if (!StringUtils.isBlank(product.getDose()) || !StringUtils.isBlank(product.getRoute()) && product.getProductId() != 0)
                                {
                                    if (TreatmentManager.getInstance().getDoseAndRoute(product.getContainer(), product.getDose(), product.getRoute(), product.getProductId()) == null)
                                    {
                                        DoseAndRoute doseAndRoute = new DoseAndRoute(product.getDose(), product.getRoute(), product.getProductId(), c);
                                        TreatmentManager.getInstance().saveStudyProductDoseAndRoute(c, context.getUpgradeUser(), doseAndRoute);
                                    }
                                }
                            }
                        }
                    }
                }
                transaction.commit();
            }
        }
    }

    /*
     * Update dataspace primary key to be consistent with non-dataspace primary key.  Add date index to speed up queries.
     */

    // Invoked by study-16.20-16.30.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void updateDateIndex(final ModuleContext context) throws Exception
    {
        if (!context.isNewInstall())
        {
            try (DbScope.Transaction transaction = StudySchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                List<String> updated = new ArrayList<>();
                Set<Container> allContainers = ContainerManager.getAllChildren(ContainerManager.getRoot());
                for (Container c : allContainers)
                {
                    Study study = StudyManager.getInstance().getStudy(c);
                    if (null != study)
                    {
                        List<? extends Dataset> datasets = study.getDatasets();
                        for (Dataset dataset : datasets)
                        {
                            Domain domain = dataset.getDomain();
                            if (domain != null)
                            {
                                if(updated.contains(domain.getStorageTableName()))
                                    continue;

                                // New primary key
                                PropertyStorageSpec ps = new PropertyStorageSpec(DatasetDomainKind.DSROWID, JdbcType.BIGINT, 0,
                                        PropertyStorageSpec.Special.PrimaryKeyNonClustered, false, true, null);

                                // Primary key constraint
                                Constraint dropPk = new Constraint(domain.getStorageTableName(), Constraint.CONSTRAINT_TYPES.PRIMARYKEY, false, null);

                                try
                                {
                                    // Update pk on dataspace datasets
                                    if (StudyManager.getInstance().getStudy(domain.getContainer()).isDataspaceStudy())
                                    {
                                        String sql = "";
                                        if(((DatasetDefinition) dataset).getStorageTableInfo().getSqlDialect().isSqlServer())
                                        {
                                            sql = "IF NOT EXISTS(\n" +
                                                    "    SELECT *\n" +
                                                    "    FROM sys.columns \n" +
                                                    "    WHERE Name = N'" + DatasetDomainKind.DSROWID + "'\n" +
                                                    "      AND Object_ID = Object_ID(N'studydataset." + domain.getStorageTableName() + "'))\n" +
                                                    "BEGIN\n" +
                                                    "    ALTER TABLE studydataset." + domain.getStorageTableName() + " ADD " + DatasetDomainKind.DSROWID +
                                                    " BIGINT IDENTITY (1, 1) NOT NULL;\n" +
                                                    "    ALTER TABLE studydataset." + domain.getStorageTableName() + " DROP CONSTRAINT " + dropPk.getName() + ";\n" +
                                                    "    ALTER TABLE studydataset." + domain.getStorageTableName() + " ADD CONSTRAINT " + dropPk.getName() +
                                                    " " + dropPk.getType() + " NONCLUSTERED " + "(" + DatasetDomainKind.DSROWID + ");" +
                                                    "END";
                                        }
                                        else if(((DatasetDefinition) dataset).getStorageTableInfo().getSqlDialect().isPostgreSQL())
                                        {
                                            sql = "DO $$\n" +
                                                    "BEGIN\n" +
                                                    "IF NOT EXISTS(SELECT column_name \n" +
                                                    "FROM information_schema.columns \n" +
                                                    "WHERE table_name='" + domain.getStorageTableName() + "' and column_name='" + DatasetDomainKind.DSROWID + "')\n" +
                                                    "THEN\n" +
                                                    "   ALTER TABLE studydataset." + domain.getStorageTableName() + " ADD COLUMN " + DatasetDomainKind.DSROWID + " BIGSERIAL;\n" +
                                                    "   ALTER TABLE studydataset." + domain.getStorageTableName() + " DROP CONSTRAINT " + dropPk.getName() + ";\n" +
                                                    "   ALTER TABLE studydataset." + domain.getStorageTableName() + " ADD CONSTRAINT " + dropPk.getName() +
                                                    " " + dropPk.getType() + " (" + DatasetDomainKind.DSROWID + ");\n" +
                                                    "END IF;\n" +
                                                    "END$$;";
                                        }

                                        if(!sql.isEmpty())
                                        {
                                            SQLFragment f = new SQLFragment(sql);
                                            new SqlExecutor(StudySchema.getInstance().getScope()).execute(f);
                                        }
                                    }

                                    // This will add any indexes not already in the table. In this case the date index.
                                    StorageProvisioner.addOrDropTableIndices(domain, null, true, null);
                                    updated.add(domain.getStorageTableName());
                                }
                                catch (Exception e)
                                {
                                    _log.error("Error upgrading study dataset schemas: ", e);
                                    throw (e);
                                }
                            }
                        }
                    }
                }
                transaction.commit();
            }
        }
    }

    /**
     * Move QC state to core to allow it to be used by modules outside the study module.  Moves existing data over
     * as well and handles inserting into identity/serial columns for SQL Server and Postgres.  Drops study.qcstate
     * but references to study.qcstate in the study user schema have been updated to core.qcstate.
     */

    /*
        Note: When this upgrade code is removed (in v20.1), we'll need to move the three ADD CONSTRAINT statements into an upgrade script
     */

    // Invoked by study-17.21-17.22.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void moveQCStateToCore(final ModuleContext context) throws Exception
    {
        SQLFragment sqlFrag;
        String sql;
        try (DbScope.Transaction transaction = StudySchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            SqlExecutor sel = new SqlExecutor(StudySchema.getInstance().getScope());

            // SQL Server insert into identity column
            if (StudySchema.getInstance().getSqlDialect().isSqlServer())
            {
                sql = "SET IDENTITY_INSERT core.QCState ON";
                sqlFrag = new SQLFragment(sql);
                sel.execute(sqlFrag);
            }

            sql = "INSERT INTO core.QCState (RowId, Label, Description, Container, PublicData) " +
                    "SELECT RowId, Label, Description, Container, PublicData FROM study.QCState;";
            sqlFrag = new SQLFragment(sql);
            sel.execute(sqlFrag);

            if (StudySchema.getInstance().getSqlDialect().isSqlServer())
            {
                sql = "SET IDENTITY_INSERT core.QCState OFF";
                sqlFrag = new SQLFragment(sql);
                sel.execute(sqlFrag);
            }
            else
            {   // PG after inserting into serial column
                sql = "SELECT setval(pg_get_serial_sequence('core.QCState', 'rowid'), \n" +
                        "(SELECT MAX(RowId) FROM core.QCState));";
                sqlFrag = new SQLFragment(sql);
                sel.execute(sqlFrag);
            }

            sql = "ALTER TABLE study.Study DROP CONSTRAINT FK_Study_DefaultAssayQCState";
            sqlFrag = new SQLFragment(sql);
            sel.execute(sqlFrag);

            sql = "ALTER TABLE study.Study DROP CONSTRAINT FK_Study_DefaultDirectEntryQCState";
            sqlFrag = new SQLFragment(sql);
            sel.execute(sqlFrag);

            sql = "ALTER TABLE study.Study DROP CONSTRAINT FK_Study_DefaultPipelineQCState";
            sqlFrag = new SQLFragment(sql);
            sel.execute(sqlFrag);

            sql = "ALTER TABLE study.Study ADD CONSTRAINT FK_Study_DefaultPipelineQCState FOREIGN KEY (DefaultPipelineQCState) REFERENCES core.QCState (RowId)";
            sqlFrag = new SQLFragment(sql);
            sel.execute(sqlFrag);

            sql = "ALTER TABLE study.Study ADD CONSTRAINT FK_Study_DefaultDirectEntryQCState FOREIGN KEY (DefaultDirectEntryQCState) REFERENCES core.QCState (RowId)";
            sqlFrag = new SQLFragment(sql);
            sel.execute(sqlFrag);

            sql = "ALTER TABLE study.Study ADD CONSTRAINT FK_Study_DefaultAssayQCState FOREIGN KEY (DefaultAssayQCState) REFERENCES core.QCState (RowId)";
            sqlFrag = new SQLFragment(sql);
            sel.execute(sqlFrag);

            transaction.commit();
        }

        // Don't hold transaction open for this
        sql = "DROP TABLE study.QCState";
        sqlFrag = new SQLFragment(sql);
        new SqlExecutor(StudySchema.getInstance().getScope()).execute(sqlFrag);
    }

    // Invoked by study-17.30-17.31.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void updateSpecimenHash(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            try (DbScope.Transaction transaction = StudySchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                List<String> updated = new ArrayList<>();
                Set<Container> allContainers = ContainerManager.getAllChildren(ContainerManager.getRoot());
                for (Container c : allContainers)
                {
                    Study study = StudyManager.getInstance().getStudy(c);
                    if (null != study)
                    {
                        _log.info("Updating Specimen.SpecimenHash in container: " + c.getName());
                        TableInfo specimenTable = StudySchema.getInstance().getTableInfoSpecimen(c);
                        SQLFragment sql = new SQLFragment("UPDATE ");
                        sql.append(specimenTable.getSelectName()).append(" SET SpecimenHash = (SELECT \n");
                        SpecimenImporter.makeUpdateSpecimenHashSql(StudySchema.getInstance().getSchema(), c, Collections.emptyList(), "", sql);
                        sql.append(")");
                        new SqlExecutor(StudySchema.getInstance().getSchema().getScope()).execute(sql);
                    }
                }
                transaction.commit();
            }
            catch (Exception e)
            {
                _log.error("Error updating SpecimenHash: " + e.getMessage());
            }
        }
    }
}