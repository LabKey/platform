/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
package org.labkey.experiment;

import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.experiment.api.DataClass;
import org.labkey.experiment.api.DataClassDomainKind;
import org.labkey.experiment.api.ExpDataClassImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.experiment.api.ProtocolApplication;
import org.labkey.experiment.api.SampleTypeServiceImpl;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.labkey.api.files.FileContentService.UPLOADED_FILE_NAMESPACE_PREFIX;

/**
 * User: kevink
 * Date: 12/27/16
 */
public class ExperimentUpgradeCode implements UpgradeCode
{
    private static final Logger LOG = LogManager.getLogger(ExperimentUpgradeCode.class);

    @SuppressWarnings("unused")  // Called from exp-21.006-21.007.sql
    public static void upgradeMaterialSource(ModuleContext context)
    {
        if (context != null && context.isNewInstall())
            return;

        TableInfo msTable = ExperimentServiceImpl.get().getTinfoSampleType();
        TableInfo objTable = OntologyManager.getTinfoObject();
        SQLFragment sql = new SQLFragment("SELECT ms.*, o.objectid FROM ")
                .append(msTable.getFromSQL("ms"))
                .append(" LEFT OUTER JOIN " ).append(objTable.getFromSQL("o")).append(" ON ms.lsid=o.objecturi")
                .append(" WHERE o.objectid IS NULL");
        var collection = new SqlSelector(msTable.getSchema().getScope(), sql).getCollection(MaterialSource.class);
        if (collection.isEmpty())
            return;

        collection.forEach(ms -> {
            int oid = OntologyManager.ensureObject(ms.getContainer(), ms.getLSID());
            ms.setObjectId(oid);
            LOG.info("Created object " + oid + " for " + ms.getName());

            SQLFragment update = new SQLFragment("UPDATE exp.object SET ownerobjectid=?" +
                    " WHERE objecturi IN (SELECT lsid from exp.material WHERE cpastype=?)",  + ms.getObjectId(), ms.getLSID());
            int rowCount = new SqlExecutor(objTable.getSchema().getScope()).execute(update);
            LOG.info("Updated ownerObjectId for " + rowCount + " materials");
        });
        SampleTypeServiceImpl.get().clearMaterialSourceCache(null);
    }

    /**
     * Called from exp-20.001-20.002.sql
     */
    @SuppressWarnings("unused")
    public static void addProvisionedDataClassNameClassId(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
        {
            // get all DataClass across all containers
            TableInfo source = ExperimentServiceImpl.get().getTinfoDataClass();
            new TableSelector(source, null, null).stream(DataClass.class)
                .map(ExpDataClassImpl::new)
                .forEach(ExperimentUpgradeCode::setDataClassNameClassId);

            tx.commit();
        }
    }

    private static void setDataClassNameClassId(ExpDataClassImpl ds)
    {
        Domain domain = ds.getDomain();
        DataClassDomainKind kind = null;
        try
        {
            kind = (DataClassDomainKind) domain.getDomainKind();
        }
        catch (IllegalArgumentException e)
        {
            // pass
        }
        if (null == kind || null == kind.getStorageSchemaName())
            return;

        DbSchema schema = kind.getSchema();
        DbScope scope = kind.getSchema().getScope();

        StorageProvisioner.get().ensureStorageTable(domain, kind, scope);
        domain = PropertyService.get().getDomain(domain.getTypeId());
        assert (null != domain && null != domain.getStorageTableName());

        SchemaTableInfo provisionedTable = schema.getTable(domain.getStorageTableName());
        if (provisionedTable == null)
        {
            LOG.error("DataSet '" + ds.getName() + "' (" + ds.getRowId() + ") has no provisioned table");
            return;
        }

        ColumnInfo nameCol = provisionedTable.getColumn("name");
        if (nameCol == null)
        {
            PropertyStorageSpec nameProp = kind.getBaseProperties(domain).stream().filter(p -> "name".equalsIgnoreCase(p.getName())).findFirst().orElseThrow();
            StorageProvisioner.get().addStorageProperties(domain, Arrays.asList(nameProp), true);
            LOG.info("DataSet '" + ds.getName() + "' (" + ds.getRowId() + ") added 'name' column");
        }

        ColumnInfo classIdCol = provisionedTable.getColumn("classId");
        if (classIdCol == null)
        {
            PropertyStorageSpec classIdProp = kind.getBaseProperties(domain).stream().filter(p -> "classId".equalsIgnoreCase(p.getName())).findFirst().orElseThrow();
            StorageProvisioner.get().addStorageProperties(domain, Arrays.asList(classIdProp), true);
            LOG.info("DataSet '" + ds.getName() + "' (" + ds.getRowId() + ") added 'classId' column");
        }

        fillNameClassId(ds, domain, scope);

        //addIndex
        Set<PropertyStorageSpec.Index> newIndices =  Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(new PropertyStorageSpec.Index(true, "name", "classid"))));
        StorageProvisioner.get().addOrDropTableIndices(domain, newIndices, true, TableChange.IndexSizeMode.Normal);
        LOG.info("DataClass '" + ds.getName() + "' (" + ds.getRowId() + ") added unique constraint on 'name' and 'classId'");
    }

    // populate name and classId value on provisioned table
    private static void fillNameClassId(ExpDataClassImpl ds, Domain domain, DbScope scope)
    {
        String tableName = domain.getStorageTableName();
        SQLFragment update = new SQLFragment()
                .append("UPDATE expdataclass.").append(tableName).append("\n")
                .append("SET name = i.name, classid = i.classid\n")
                .append("FROM (\n")
                .append("  SELECT d.lsid, d.name, d.classid\n")
                .append("  FROM exp.data d\n")
                .append("  WHERE d.cpasType = ?\n").add(domain.getTypeURI())
                .append(") AS i\n")
                .append("WHERE i.lsid = ").append(tableName).append(".lsid");

        int count = new SqlExecutor(scope).execute(update);
        LOG.info("DataClass '" + ds.getName() + "' (" + ds.getRowId() + ") updated 'name' and 'classId' column, count=" + count);
    }

    // Changes from an autoIncrement column as the RowId to a DBSequence so the rowId can be more readily available
    // during creation of materials (particularly during file import).
    //
    // This needs to be run after startup because we are altering the primary key column for exp.Materials, and for SQL Server
    // this means we need to remove some foreign key constraints in other schemas.
    @DeferredUpgrade
    @SuppressWarnings("unused") // called from exp-20.003-20.004
    public static void addDbSequenceForMaterialsRowId(ModuleContext context)
    {
        _addDbSequenceForMaterialRowId();
    }

    // The previous method originally mistakenly did not update RowId column for new installs,
    // leaving databases bootstrapped after the previous upgrade script was implemented in a strange state.
    // This method will fix up the databases where that removal of autoIncrement was missed.
    @DeferredUpgrade
    @SuppressWarnings("unused") // called from exp-20.004-20.005
    public static void addDbSequenceForMaterialsRowIdIfMissed(ModuleContext context)
    {
        if (ExperimentService.get().getTinfoMaterial().getColumn("RowId").isAutoIncrement())
            _addDbSequenceForMaterialRowId();
    }

    private static void _addDbSequenceForMaterialRowId()
    {
        SQLFragment frag = new SQLFragment("SELECT MAX(rowId) FROM exp.material");
        Integer maxId = new SqlSelector(ExperimentService.get().getSchema(), frag).getObject(Integer.class);

        DbSequence sequence = DbSequenceManager.get(ContainerManager.getRoot(), ExperimentService.get().getTinfoMaterial().getDbSequenceName("RowId"));
        if (maxId != null)
            sequence.ensureMinimum(maxId);

        DbScope scope = DbScope.getLabKeyScope();
        try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
        {
            SQLFragment sql;
            if (scope.getSqlDialect().isPostgreSQL())
            {
                sql = new SQLFragment("ALTER SEQUENCE exp.material_rowid_seq owned by NONE;\n");
                sql.append("ALTER TABLE exp.material ALTER COLUMN rowId DROP DEFAULT;\n");
                sql.append("DROP SEQUENCE exp.material_rowid_seq;");
                new SqlExecutor(scope).execute(sql);
            }
            else
            {
                // For SQLServer We can't do this modification in place for the RowId column, so we make a copy of the column, drop the original
                // column then rename the copy and add back the constraints.
                sql = new SQLFragment();

                sql.append("ALTER TABLE exp.material ADD RowId_copy INT NULL;\n");
                // Drop foreign keys before dropping the original column.  First materialInput
                sql.append("ALTER TABLE exp.materialInput DROP CONSTRAINT fk_materialinput_material;\n");
                // now ms2
                sql.append("EXEC core.fn_dropifexists 'ExpressionData', 'ms2', 'constraint', 'FK_ExpressionData_SampleId';\n");
                // and labbook
                sql.append("EXEC core.fn_dropifexists 'LabBookExperimentMaterial', 'labbook', 'constraint', 'FK_LabBookExperimentMaterial_MaterialId';\n");
                // and microarray
                sql.append("EXEC core.fn_dropifexists 'FeatureData', 'microarray', 'constraint', 'FK_FeatureData_SampleId';\n");
                // and idri
                sql.append("EXEC core.fn_dropifexists 'concentrations', 'idri', 'constraint', 'FK_Compounds';\n");
                sql.append("EXEC core.fn_dropifexists 'concentrations', 'idri', 'constraint', 'FK_Materials';\n");
                sql.append("EXEC core.fn_dropifexists 'concentrations', 'idri', 'constraint', 'FK_Lot';\n");
                // Remove primary key constraint
                sql.append("ALTER TABLE exp.Material DROP CONSTRAINT PK_Material;\n");

                new SqlExecutor(scope).execute(sql);

                sql = new SQLFragment();
                // Copy RowId to the new column
                sql.append("UPDATE exp.material SET RowId_copy = RowId;\n");

                // Now drop the original column
                sql.append("ALTER TABLE exp.material DROP COLUMN RowId;\n");

                new SqlExecutor(scope).execute(sql);

                sql = new SQLFragment();
                // Rename the copy to the original name and restore it as a Non-Null PK
                sql.append("EXEC sp_rename 'exp.material.RowId_copy', 'RowId', 'COLUMN';\n");
                sql.append("ALTER TABLE exp.Material ALTER COLUMN RowId INT NOT NULL;\n");
                sql.append("ALTER TABLE exp.Material ADD CONSTRAINT PK_Material PRIMARY KEY (RowId);\n");
                // Add the foreign key constraints back again
                sql.append("ALTER TABLE exp.materialInput ADD CONSTRAINT FK_MaterialInput_Material FOREIGN KEY (MaterialId) REFERENCES exp.Material (RowId);\n");
                sql.append("IF EXISTS (SELECT 1 FROM sys.schemas WHERE Name = 'ms2') \n" +
                        "       ALTER TABLE ms2.ExpressionData ADD CONSTRAINT FK_ExpressionData_SampleId FOREIGN KEY (SampleId) REFERENCES exp.material (RowId);\n"
                );
                sql.append("IF EXISTS (SELECT 1 FROM sys.schemas WHERE NAME = 'microarray')\n" +
                        "   ALTER TABLE microarray.FeatureData ADD CONSTRAINT FK_FeatureData_SampleId FOREIGN KEY (SampleId) REFERENCES exp.material (RowId);\n");
                sql.append("IF EXISTS (SELECT 1 FROM sys.schemas WHERE NAME = 'idri')\n" +
                        " BEGIN" +
                        "   ALTER TABLE idri.concentrations ADD CONSTRAINT FK_Compounds FOREIGN KEY (Compound) REFERENCES exp.Material(RowId);\n" +
                        "   ALTER TABLE idri.concentrations ADD CONSTRAINT FK_Materials FOREIGN KEY (Material) REFERENCES exp.Material(RowId);\n" +
                        "   ALTER TABLE idri.concentrations ADD CONSTRAINT FK_Lot FOREIGN KEY (Lot) REFERENCES exp.Material(RowId);\n" +
                        " END; "
                );
                new SqlExecutor(scope).execute(sql);
            }
            tx.commit();
        }
    }

    // Issue 40443: For SQL Server, if modifying a table that is used in a view, the views need to get recreated after that
    // modification happens. So we need to do that after the previous deferred upgrade scripts happen since
    // the createViews scripts run at the end of the regular upgrade scripts and thus before the deferred ones.
    @DeferredUpgrade
    @SuppressWarnings("unused") // called from exp-20.005-20.006
    public static void recreateViewsAfterMaterialRowIdDbSequence(ModuleContext context)
    {
        ModuleLoader.getInstance().recreateViews(ModuleLoader.getInstance().getModule(context.getName()));
    }

    // Issue 43246: Lineage query NPE while processing an UploadedFile
    // Some exp.object for UploadedFile exp.data were orphaned when imported into an assay prior to Issue 41675 being fixed.
    // This upgrade cleans the orphaned exp.object and rebuilds the exp.edges for the runs.
    @SuppressWarnings("unused") // called from exp-21.004-21.005.sql
    public static void deleteOrphanedUploadedFileObjects(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        try
        {
            ViewBackgroundInfo info = new ViewBackgroundInfo(ContainerManager.getRoot(), null, null);
            PipeRoot root = PipelineService.get().findPipelineRoot(ContainerManager.getRoot());

            // Create a new pipeline job and add it to the JobStore. Since this code runs before the module
            // startBackgroundThreads(), the PipelineModule.startBackgroundThreads JobRestarter will pick up this
            // new job and queue/run it.
            DeleteOrphanedUploadedFileObjectsJob job = new DeleteOrphanedUploadedFileObjectsJob(info, root);
            PipelineJobService.get().getStatusWriter().setStatus(job, PipelineJob.TaskStatus.waiting.toString(), null, true);
            PipelineJobService.get().getJobStore().storeJob(job);
            LOG.info("Queued DeleteOrphanedUploadedFileObjectsJob to run on startup");
        }
        catch (Exception e)
        {
            LOG.error("Unexpected error during DeleteOrphanedUploadedFileObjectsJob", e);
        }
    }

    public static class DeleteOrphanedUploadedFileObjectsJob extends PipelineJob
    {
        /** For JSON serialization/deserialzation round-tripping
         * @noinspection unused*/
        protected DeleteOrphanedUploadedFileObjectsJob() {}

        public DeleteOrphanedUploadedFileObjectsJob(ViewBackgroundInfo info, @NotNull PipeRoot root)
        {
            super(null, info, root);
            setLogFile(new File(root.getRootPath(), FileUtil.makeFileNameWithTimestamp("deleteOrphanedUploadedFileObjects", "log")));
        }

        @Override
        public String getDescription()
        {
            return "Delete orphaned UploadedFile exp.object rows";
        }

        @Override
        public URLHelper getStatusHref()
        {
            return null;
        }

        @Override
        public void run()
        {
            setStatus(TaskStatus.running);

            // Find runs for exp.objects that have been orphaned
            SQLFragment sql = new SQLFragment()
                    .append("SELECT e.runId\n")
                    .append("FROM ").append(ExperimentService.get().getTinfoEdge(), "e").append("\n")
                    .append("WHERE e.toObjectId IN (").append("\n")
                    .append("  SELECT objectId FROM ").append(OntologyManager.getTinfoObject(), "o").append("\n")
                    .append("  WHERE o.objectUri LIKE 'urn:lsid:%:").append(UPLOADED_FILE_NAMESPACE_PREFIX).append("%'").append("\n")
                    .append("  AND NOT EXISTS (").append("\n")
                    .append("    SELECT d.RowId FROM ").append(ExperimentService.get().getTinfoData(), "d").append("\n")
                    .append("    WHERE d.LSID = o.objectUri").append("\n")
                    .append("  )").append("\n")
                    .append(")");

            List<Integer> runs = new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(Integer.class);
            if (!runs.isEmpty())
            {
                getLogger().info("Syncing " + runs.size() + " runs...");
                int runCount = 0;
                for (Integer runId : runs)
                {
                    ExperimentServiceImpl.get().syncRunEdges(runId);
                    if (++runCount % 1000 == 0)
                    {
                        getLogger().info("  fixed " + runCount + " runs...");
                    }
                }
                getLogger().info("Synced " + runs.size() + " runs");
            }
            else
            {
                getLogger().info("No runs need to be synced");
            }

            getLogger().info("Deleting orphaned exp.objects...");
            SQLFragment orphanedSql = new SQLFragment()
                    .append("SELECT objectUri FROM ").append(OntologyManager.getTinfoObject(), "o").append("\n")
                    .append("  WHERE o.objectUri LIKE 'urn:lsid:%:").append(UPLOADED_FILE_NAMESPACE_PREFIX).append("%'").append("\n")
                    .append("  AND NOT EXISTS (").append("\n")
                    .append("    SELECT d.RowId FROM ").append(ExperimentService.get().getTinfoData(), "d").append("\n")
                    .append("    WHERE d.LSID = o.objectUri").append("\n")
                    .append(")");
            int count = OntologyManager.deleteOntologyObjects(OntologyManager.getExpSchema(), orphanedSql, null, false);
            getLogger().info("Deleted " + count + " orphaned exp.objects");

            setStatus(TaskStatus.complete);
        }
    }

    // For a long time we've been creating unused property validators for text length. This script cleans those up. This
    // is deferred because we are using the text length property validators in SND and need to convert them to
    // the new type URI first.
    @SuppressWarnings({"UnusedDeclaration"}) // Called from exp-21.008-21.009.sql
    @DeferredUpgrade
    public void cleanupLengthTypePropertyValidators(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            SQLFragment sql = new SQLFragment("DELETE FROM exp.PropertyValidator WHERE TypeURI = \'urn:lsid:labkey.com:PropertyValidator:length\'");
            try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
            {
                new SqlExecutor(ExperimentService.get().getSchema()).execute(new SQLFragment(sql));
                transaction.commit();
            }
        }
    }

    /**
     * Called from exp-21.013-21.014.sql
     * Generates EntityIds for ExpProtocolApplications that do not have a value
     */
    public static void generateExpProtocolApplicationEntityIds(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
        {
            var protocolApplicationTable = ExperimentService.get().getTinfoProtocolApplication();
            List<ProtocolApplication> applications = new TableSelector(protocolApplicationTable).getArrayList(ProtocolApplication.class);

            for (var application: applications)
            {
                if (application.getEntityId() == null)
                {
                    application.setEntityId(new GUID());
                    Table.update(ctx.getUpgradeUser(), protocolApplicationTable, application, application.getRowId());
                }
            }

            tx.commit();
        }
    }
}
