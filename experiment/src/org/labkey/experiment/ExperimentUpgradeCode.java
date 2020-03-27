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
import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleContext;
import org.labkey.experiment.api.DataClass;
import org.labkey.experiment.api.DataClassDomainKind;
import org.labkey.experiment.api.ExpDataClassImpl;
import org.labkey.experiment.api.ExpSampleSetImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.experiment.api.SampleSetDomainKind;
import org.labkey.experiment.api.SampleSetServiceImpl;
import org.labkey.experiment.api.property.DomainImpl;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * User: kevink
 * Date: 12/27/16
 */
public class ExperimentUpgradeCode implements UpgradeCode
{
    private static final Logger LOG = Logger.getLogger(ExperimentUpgradeCode.class);

    /**
     * Called from multiple experiment upgrade scripts,
     * uses @DeferredUpgrade and local flag to make sure we don't run this multiple times, when a server is upgraded
     * multiple versions in at one go.
     */
    static private boolean rebuildEdgesHasRun = false;

    @DeferredUpgrade
    public static void rebuildAllEdges(ModuleContext context)
    {
        if (context.isNewInstall() || rebuildEdgesHasRun)
            return;

        rebuildEdgesHasRun = true;
        ExperimentServiceImpl.get().rebuildAllEdges();
    }

    private static void materializeSampleSet(ExpSampleSetImpl ss)
    {
        Logger log = Logger.getLogger(ExperimentUpgradeCode.class);
        Domain domain = ss.getDomain();
        DomainKind kind = null;
        try
        {
            kind =  domain.getDomainKind();
        }
        catch (IllegalArgumentException iae)
        {
            // pass
        }
        if (null == kind || null == kind.getStorageSchemaName())
        {
            return;
        }

        // skip the 'Unspecified' SampleSet
        if (ExperimentServiceImpl.get().getDefaultSampleSetLsid().equals(ss.getLSID()))
            return;

        DbScope scope = ExperimentServiceImpl.get().getSchema().getScope();
        SqlDialect d = scope.getSqlDialect();

        for (DomainProperty property : domain.getProperties())
        {
            // Make sure that all properties have a storagecolumnname value
            PropertyDescriptor propertyDescriptor = property.getPropertyDescriptor();
            boolean updated = false;
            if (propertyDescriptor.getStorageColumnName() == null)
            {
                ((DomainImpl)domain).generateStorageColumnName(propertyDescriptor);
                updated = true;
            }
            // migrate REAL->DOUBLE to correctly handle upgrade of special values, see ResultSetUtil.mapJavaDoubleToDatabaseDouble()
            if (propertyDescriptor.getJdbcType() == JdbcType.REAL)
            {
                propertyDescriptor.setJdbcType(JdbcType.DOUBLE, 0);
                propertyDescriptor.setPropertyType(PropertyType.DOUBLE);
                updated = true;
            }

            // Issue 36817 - deal with string values longer than the property descriptor's declared scale
            if (propertyDescriptor.getJdbcType().isText())
            {
                SQLFragment longestSQL =  new SQLFragment("SELECT MAX(").append(d.getVarcharLengthFunction()).append("(StringValue)) FROM ").
                        append(OntologyManager.getTinfoObjectProperty(), "op").
                        append(" WHERE PropertyId = ?").
                        add(propertyDescriptor.getPropertyId());
                Integer longest = new SqlSelector(OntologyManager.getExpSchema(), longestSQL).getObject(Integer.class);
                if (longest != null && longest.intValue() > propertyDescriptor.getScale())
                {
                    propertyDescriptor.setScale(4000);
                    updated = true;
                }
            }
            if (updated)
            {
                OntologyManager.updatePropertyDescriptor(propertyDescriptor);
                LOG.debug("SampleSet '" + ss.getName() + "' (" + ss.getRowId() + "), property='" + property.getName() + "' updated");
            }
        }

        StorageProvisioner.ensureStorageTable(domain, kind, scope);
        // refetch the domain which we just updated
        domain = PropertyService.get().getDomain(domain.getTypeId());
        assert(null != domain.getStorageTableName());
        LOG.debug("SampleSet '" + ss.getName() + "' (" + ss.getRowId() + ") provisioned");


        // generate SQL to select from exp.material and exp.objectproperty
        SQLFragment select = new SQLFragment("SELECT m.lsid AS lsid");
        SQLFragment insert = new SQLFragment("INSERT INTO expsampleset.");
        insert.append(domain.getStorageTableName());
        insert.append(" (lsid");

        String comma = ", ";
        for (DomainProperty dp : domain.getProperties())
        {
            select.append(comma);
            // TODO need casts
            String dbtype = d.getSqlTypeName(dp.getJdbcType());
            String columnSelectName = d.getColumnSelectName(dp.getPropertyDescriptor().getStorageColumnName().toLowerCase());
            if (dp.getPropertyType() == PropertyType.BOOLEAN)
                select.append("\n  (SELECT CAST(CASE WHEN floatvalue IS NULL THEN NULL WHEN floatvalue=1.0 THEN 1 ELSE 0 END AS " + dbtype + ") FROM exp.objectproperty OP WHERE OP.objectid=O.objectid AND OP.propertyid=" + dp.getPropertyId() + ") AS " + columnSelectName);
            else if (dp.getJdbcType().isText())
                select.append("\n  (SELECT stringvalue FROM exp.objectproperty OP WHERE OP.objectid=O.objectid AND OP.propertyid=" + dp.getPropertyId() + ") AS " + columnSelectName);
            else if (dp.getJdbcType().isDateOrTime())
                select.append("\n  (SELECT CAST(datetimevalue AS " + dbtype + ") FROM exp.objectproperty OP WHERE OP.objectid=O.objectid AND OP.propertyid=" + dp.getPropertyId() + ") AS " + columnSelectName);
            else
                select.append("\n  (SELECT CAST(floatvalue AS " + dbtype + ") FROM exp.objectproperty OP WHERE OP.objectid=O.objectid AND OP.propertyid=" + dp.getPropertyId() + ") AS " + columnSelectName);

            insert.append(comma);
            insert.append(columnSelectName);
            if (null != dp.getPropertyDescriptor().getMvIndicatorStorageColumnName())
            {
                String mvcolumnSelectName = d.getColumnSelectName(dp.getPropertyDescriptor().getMvIndicatorStorageColumnName()).toLowerCase();
                select.append(comma);
                select.append("(SELECT mvindicator FROM exp.objectproperty OP WHERE OP.objectid=O.objectid AND OP.propertyid=" + dp.getPropertyId() + ") AS " + mvcolumnSelectName);
                insert.append(comma);
                insert.append(mvcolumnSelectName);
            }
        }
        select.append("\nFROM exp.material m\n");
        select.append("\nLEFT OUTER JOIN exp.object O ON m.lsid = O.objecturi");
        select.append("\nWHERE m.CpasType = ?").add(domain.getTypeURI());
        insert.append(")\n");
        insert.append(select);

        int count = new SqlExecutor(scope).execute(insert);
        LOG.info("SampleSet '" + ss.getName() + "' (" + ss.getRowId() + ") inserted provisioned rows, count=" + count);


        // handle migration of Description column from property to exp.Material.Description column
        DomainProperty desc = domain.getPropertyByName("Description");
        if (null != desc)
        {
            String columnSelectName = d.getColumnSelectName(desc.getPropertyDescriptor().getStorageColumnName().toLowerCase());
            SQLFragment update;
            if (scope.getSqlDialect().isSqlServer())
            {
                update = new SQLFragment(
                        "UPDATE exp.material\n"+
                                "SET Description = (SELECT " + columnSelectName + " FROM expsampleset." + domain.getStorageTableName() +" ss WHERE ss.lsid = m.lsid)\n"+
                                "FROM exp.material m\n" +
                                "WHERE m.CpasType = ?",
                        domain.getTypeURI());
            }
            else
            {
                update = new SQLFragment(
                        "UPDATE exp.material m\n"+
                                "SET Description = (SELECT " + columnSelectName + " FROM expsampleset." + domain.getStorageTableName() +" ss WHERE ss.lsid = m.lsid)\n"+
                                "WHERE m.CpasType = ?",
                        domain.getTypeURI());
            }
            new SqlExecutor(scope).execute(update);

            // delete the property
            try
            {
                desc.delete();
                domain.save(null);
            }
            catch (ChangePropertyDescriptorException x)
            {
                log.warn("unexpected error during upgrade", x);
            }
        }

        // delete objectproperty rows for samples in the SampleSet, but only for properties of the SampleSet domain
        SQLFragment deleteObjectProperties = new SQLFragment("DELETE FROM exp.objectproperty\n");
        deleteObjectProperties.append("WHERE objectid IN (SELECT objectid FROM exp.object WHERE objecturi IN (SELECT lsid FROM exp.material WHERE CpasType = ?))");
        deleteObjectProperties.add(ss.getDataObject().getLSID());
        deleteObjectProperties.append(" AND propertyId IN (");
        comma = "";
        for (DomainProperty dp : domain.getProperties())
        {
            deleteObjectProperties.append(comma).append(dp.getPropertyId());
            comma = ",";
        }
        deleteObjectProperties.append(")");
        if (!domain.getProperties().isEmpty())
        {
            new SqlExecutor(scope).execute(deleteObjectProperties);
            LOG.info("SampleSet '" + ss.getName() + "' (" + ss.getRowId() + ") deleted ontology properties");
        }
    }


    /** Called from exp-18.31-18.32.sql */
    public static void materializeSampleSets(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        // get all MaterialSource across all containers
        TableInfo source = ExperimentServiceImpl.get().getTinfoMaterialSource();
        new TableSelector(source, null, null).stream(MaterialSource.class)
                .map(ExpSampleSetImpl::new)
                .forEach(ExperimentUpgradeCode::materializeSampleSet);
    }

    private static void addSampleSetGenId(ExpSampleSetImpl ss)
    {
        Domain domain = ss.getDomain();
        SampleSetDomainKind kind = null;
        try
        {
            kind = (SampleSetDomainKind)domain.getDomainKind();
        }
        catch (IllegalArgumentException e)
        {
            // pass
        }
        if (null == kind || null == kind.getStorageSchemaName())
            return;

        // skip the 'Unspecified' SampleSet
        if (ExperimentServiceImpl.get().getDefaultSampleSetLsid().equals(ss.getLSID()))
            return;

        DbSchema schema = kind.getSchema();
        DbScope scope = schema.getScope();

        StorageProvisioner.ensureStorageTable(domain, kind, scope);
        domain = PropertyService.get().getDomain(domain.getTypeId());
        assert(null != domain.getStorageTableName());

        SchemaTableInfo provisionedTable = schema.getTable(domain.getStorageTableName());
        if (provisionedTable == null)
        {
            LOG.error("SampleSet '" + ss.getName() + "' (" + ss.getRowId() + ") has no provisioned table");
            return;
        }

        ColumnInfo genIdCol = provisionedTable.getColumn("genId");
        if (genIdCol == null)
        {
            PropertyStorageSpec genIdProp = kind.getBaseProperties(domain).stream().filter(p -> "genId".equalsIgnoreCase(p.getName())).findFirst().orElseThrow();
            StorageProvisioner.addStorageProperties(domain, Arrays.asList(genIdProp), true);
            LOG.info("SampleSet '" + ss.getName() + "' (" + ss.getRowId() + ") added 'genId' column");
        }

        addMissingSampleRows(ss, domain, scope);
        fillGenId(ss, domain, scope);
        setGenIdCounter(ss, domain, scope);
    }

    // A previous version of the 'materializeSampleSet' upgrade didn't insert rows into the provisioned table for each exp.material in the sample set.
    // Insert any missing provisioned rows that exist in exp.material but didn't have an exp.object row
    private static void addMissingSampleRows(ExpSampleSetImpl ss, Domain domain, DbScope scope)
    {
        SQLFragment insert = new SQLFragment("INSERT INTO expsampleset.")
                .append(domain.getStorageTableName())
                .append(" (lsid)\n")
                .append("  SELECT m.lsid FROM exp.material m\n")
                .append("  WHERE m.lsid NOT IN (\n")
                .append("    SELECT lsid from expsampleset.").append(domain.getStorageTableName())
                .append("  )\n")
                .append("  AND m.cpasType = ?").add(domain.getTypeURI());

        int count = new SqlExecutor(scope).execute(insert);
        if (count > 0)
            LOG.info("SampleSet '" + ss.getName() + "' (" + ss.getRowId() + ") inserting missing rows into provisioned table, count=" + count);
    }

    // populate the genId value on an existing provisioned table
    private static void fillGenId(ExpSampleSetImpl ss, Domain domain, DbScope scope)
    {
        String tableName = domain.getStorageTableName();
        SQLFragment update = new SQLFragment()
                .append("UPDATE expsampleset.").append(tableName).append("\n")
                .append("SET genId = i.genId\n")
                .append("FROM (\n")
                .append("  SELECT\n")
                .append("    m.lsid,\n")
                .append("    row_number() over (order by m.rowId) AS genId\n")
                .append("  FROM exp.material m\n")
                .append("  WHERE m.cpasType = ?\n").add(domain.getTypeURI())
                .append(") AS i\n")
                .append("WHERE i.lsid = ").append(tableName).append(".lsid");

        int count = new SqlExecutor(scope).execute(update);
        LOG.info("SampleSet '" + ss.getName() + "' (" + ss.getRowId() + ") updated 'genId' column, count=" + count);
    }

    // create a genId sequence counter for the SampleSet
    private static void setGenIdCounter(ExpSampleSetImpl ss, Domain domain, DbScope scope)
    {
        SQLFragment frag = new SQLFragment("SELECT COUNT(*) FROM exp.material WHERE cpasType=?").add(domain.getTypeURI());
        int count = new SqlSelector(scope, frag).getObject(Integer.class);

        DbSequence sequence = DbSequenceManager.get(ss.getContainer(), ExpSampleSetImpl.SEQUENCE_PREFIX, ss.getRowId());
        sequence.ensureMinimum(count);
        LOG.debug("SampleSet '" + ss.getName() + "' (" + ss.getRowId() + ") set counter for 'genId' column to " + count);
    }


    /** Called from exp-18.32-18.33.sql */
    public static void addSampleSetGenId(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
        {
            // get all MaterialSource across all containers
            TableInfo source = ExperimentServiceImpl.get().getTinfoMaterialSource();
            new TableSelector(source, null, null).stream(MaterialSource.class)
                    .map(ExpSampleSetImpl::new)
                    .forEach(ExperimentUpgradeCode::addSampleSetGenId);

            tx.commit();
        }
    }


    /** NOT yet called from upgrade script, needs to be added to a script in develop (e.g. 20.7) */
    public static void upgradeMaterialSource(ModuleContext context)
    {
        if (context != null && context.isNewInstall())
            return;

        TableInfo msTable = ExperimentServiceImpl.get().getTinfoMaterialSource();
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
        SampleSetServiceImpl.get().clearMaterialSourceCache(null);
    }

    /**
     * Called from exp-20.001-20.002.sql
     */
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

        StorageProvisioner.ensureStorageTable(domain, kind, scope);
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
            StorageProvisioner.addStorageProperties(domain, Arrays.asList(nameProp), true);
            LOG.info("DataSet '" + ds.getName() + "' (" + ds.getRowId() + ") added 'name' column");
        }

        ColumnInfo classIdCol = provisionedTable.getColumn("classId");
        if (classIdCol == null)
        {
            PropertyStorageSpec classIdProp = kind.getBaseProperties(domain).stream().filter(p -> "classId".equalsIgnoreCase(p.getName())).findFirst().orElseThrow();
            StorageProvisioner.addStorageProperties(domain, Arrays.asList(classIdProp), true);
            LOG.info("DataSet '" + ds.getName() + "' (" + ds.getRowId() + ") added 'classId' column");
        }

        fillNameClassId(ds, domain, scope);

        //addIndex
        Set<PropertyStorageSpec.Index> newIndices =  Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(new PropertyStorageSpec.Index(true, "name", "classid"))));
        StorageProvisioner.addOrDropTableIndices(domain, newIndices, true, TableChange.IndexSizeMode.Normal);
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

}
