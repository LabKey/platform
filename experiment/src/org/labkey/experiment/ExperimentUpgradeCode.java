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
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
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
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.experiment.api.ExpSampleTypeImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.experiment.api.ProtocolApplication;
import org.labkey.experiment.api.SampleTypeDomainKind;
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
            Parameter rowId = new Parameter("rowid", JdbcType.INTEGER);
            Parameter entityId = new Parameter("entityid", JdbcType.GUID);
            ParameterMapStatement pm = new ParameterMapStatement(protocolApplicationTable.getSchema().getScope(), tx.getConnection(),
                    new SQLFragment("UPDATE " + protocolApplicationTable.getSelectName() + " SET EntityId = ? WHERE RowId = ?", entityId, rowId), null);
            int count = 0;

            for (var application: applications)
            {
                if (application.getEntityId() == null)
                {
                    rowId.setValue(application.getRowId());
                    entityId.setValue(new GUID());
                    pm.addBatch();
                    count = count + 1;

                    if (count == 1000)
                    {
                        count = 0;
                        pm.executeBatch();
                    }
                }
            }

            if (count > 0) pm.executeBatch();

            tx.commit();
        }
    }

    /**
     * Called from exp-22.003-20.004.sql
     */
    public static void addProvisionedSampleName(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
        {
            // get all SampleTypes across all containers
            TableInfo sampleTypeTable = ExperimentServiceImpl.get().getTinfoSampleType();
            new TableSelector(sampleTypeTable, null, null).stream(MaterialSource.class)
                    .map(ExpSampleTypeImpl::new)
                    .forEach(ExperimentUpgradeCode::setSampleName);

            tx.commit();
        }
    }

    private static void setSampleName(ExpSampleTypeImpl st)
    {
        Domain domain = st.getDomain();
        SampleTypeDomainKind kind = null;
        try
        {
            kind = (SampleTypeDomainKind) domain.getDomainKind();
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
            LOG.error("Sample type '" + st.getName() + "' (" + st.getRowId() + ") has no provisioned table.");
            return;
        }

        ColumnInfo nameCol = provisionedTable.getColumn("name");
        if (nameCol == null)
        {
            PropertyStorageSpec nameProp = kind.getBaseProperties(domain).stream().filter(p -> "name".equalsIgnoreCase(p.getName())).findFirst().orElseThrow();
            StorageProvisioner.get().addStorageProperties(domain, Arrays.asList(nameProp), true);
            LOG.info("Added 'name' column to sample type '" + st.getName() + "' (" + st.getRowId() + ") provisioned table.");
        }

        fillSampleName(st, domain, scope);

        //addIndex
        Set<PropertyStorageSpec.Index> newIndices =  Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(new PropertyStorageSpec.Index(true, "name"))));
        StorageProvisioner.get().addOrDropTableIndices(domain, newIndices, true, TableChange.IndexSizeMode.Normal);
        LOG.info("Sample type '" + st.getName() + "' (" + st.getRowId() + ") added unique constraint on 'name'");
    }

    // populate name on provisioned sample tables
    private static void fillSampleName(ExpSampleTypeImpl st, Domain domain, DbScope scope)
    {
        String tableName = domain.getStorageTableName();
        SQLFragment update = new SQLFragment()
                .append("UPDATE expsampleset.").append(tableName).append("\n")
                .append("SET name = i.name\n")
                .append("FROM (\n")
                .append("  SELECT m.lsid, m.name\n")
                .append("  FROM exp.material m\n")
                .append("  WHERE m.cpasType = ?\n").add(domain.getTypeURI())
                .append(") AS i\n")
                .append("WHERE i.lsid = ").append(tableName).append(".lsid");

        int count = new SqlExecutor(scope).execute(update);
        LOG.info("Sample type '" + st.getName() + "' (" + st.getRowId() + ") updated 'name' column, count=" + count);
    }

}
