package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableTracker;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.NotFoundException;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.labkey.api.exp.api.ExpProtocol.ApplicationType.ExperimentRunOutput;


public class ClosureQueryHelper
{
    private final static Logger logger = LogHelper.getLogger(ClosureQueryHelper.class, "Sample and data class object ancestor data computation.");
    final static String CONCEPT_URI = "http://www.labkey.org/types#ancestorLookup";

    // N.B., This should be twice the number of generations we expect as a maximum number of ancestors due to the run nodes.
    static final int MAX_ANCESTOR_LOOKUP_DEPTH = 40;

    static String pgAncestorClosureCTE = String.format("""
            WITH RECURSIVE CTE_ AS (

                SELECT
                    RowId,
                    ObjectId as End_,
                    '/' || CAST(ObjectId AS VARCHAR) || '/' as Path_,
                    0 as Depth_
                /*FROM*/

                    UNION ALL

                SELECT CTE_.RowId, Edge.FromObjectId as End_, CTE_.Path_ || CAST(Edge.FromObjectId AS VARCHAR) || '/' as Path_, Depth_ + 1 as Depth_
                FROM CTE_ INNER JOIN exp.Edge ON CTE_.End_ = Edge.ToObjectId
                WHERE Depth_ < %d AND 0 = POSITION('/' || CAST(Edge.FromObjectId AS VARCHAR) || '/' IN Path_)
            )
            """, MAX_ANCESTOR_LOOKUP_DEPTH);

    static String pgAncestorClosureSql = """
            SELECT RowId, CAST(CASE WHEN COUNT(*) = 1 THEN MIN(ancestorRowId) ELSE -1 * COUNT(*) END AS INT) AS ancestorRowId, ancestorTypeId
            /*INTO*/
            FROM (
                SELECT DISTINCT CTE_.RowId,
                    COALESCE(material.rowid, data.rowid) as ancestorRowId,
                    COALESCE('m' || CAST(materialsource.rowid AS VARCHAR), 'd' || CAST(dataclass.rowid AS VARCHAR)) as ancestorTypeId
                FROM CTE_
                    LEFT OUTER JOIN exp.material ON End_ = material.objectId  LEFT OUTER JOIN exp.materialsource ON material.cpasType = materialsource.lsid
                    LEFT OUTER JOIN exp.data on End_ = data.objectId LEFT OUTER JOIN exp.dataclass ON data.cpasType = dataclass.lsid
                WHERE Depth_ > 0 AND materialsource.rowid IS NOT NULL OR dataclass.rowid IS NOT NULL) _inner_
            GROUP BY ancestorTypeId, RowId
            """;

    static String pgDescendantClosureCTE = String.format("""
            WITH RECURSIVE CTE_ AS (

                SELECT
                    RowId,
                    ObjectId as End_,
                    '/' || CAST(ObjectId AS VARCHAR) || '/' as Path_,
                    0 as Depth_
                /*FROM*/

                    UNION ALL

                SELECT CTE_.RowId, Edge.ToObjectId as End_, CTE_.Path_ || CAST(Edge.ToObjectId AS VARCHAR) || '/' as Path_, Depth_ + 1 as Depth_
                FROM CTE_ INNER JOIN exp.Edge ON CTE_.End_ = Edge.FromObjectId
                WHERE Depth_ < %d AND 0 = POSITION('/' || CAST(Edge.ToObjectId AS VARCHAR) || '/' IN Path_)
            )
            """, MAX_ANCESTOR_LOOKUP_DEPTH);

    static String pgDescendantClosureSql = """
                (SELECT DISTINCT COALESCE(material.objectId, data.objectId) as descendantObjectId
                FROM CTE_
                    LEFT OUTER JOIN exp.material ON End_ = material.objectId  LEFT OUTER JOIN exp.materialsource ON material.cpasType = materialsource.lsid
                    LEFT OUTER JOIN exp.data on End_ = data.objectId LEFT OUTER JOIN exp.dataclass ON data.cpasType = dataclass.lsid
                WHERE Depth_ > 0 AND materialsource.rowid IS NOT NULL OR dataclass.rowid IS NOT NULL)
            """;


    static String mssqlAncestorClosureCTE = String.format("""
            WITH CTE_ AS (

                SELECT
                    RowId,
                    ObjectId as End_,
                    '/' + CAST(ObjectId AS VARCHAR(MAX)) + '/' as Path_,
                    0 as Depth_
                /*FROM*/

                    UNION ALL

                SELECT CTE_.RowId, Edge.FromObjectId as End_, CTE_.Path_ + CAST(Edge.FromObjectId AS VARCHAR) + '/' as Path_, Depth_ + 1 as Depth_
                FROM CTE_ INNER JOIN exp.Edge ON CTE_.End_ = Edge.ToObjectId
                WHERE Depth_ < %d AND 0 = CHARINDEX('/' + CAST(Edge.FromObjectId AS VARCHAR) + '/', Path_)
            )
            """, (MAX_ANCESTOR_LOOKUP_DEPTH));

    static String mssqlAncestorClosureSql = """
            SELECT RowId, CAST(CASE WHEN COUNT(*) = 1 THEN MIN(ancestorRowId) ELSE -1 * COUNT(*) END AS INT) AS ancestorRowId, ancestorTypeId
            /*INTO*/
            FROM (
                SELECT DISTINCT COALESCE(material.rowid, data.rowid) as descendantRowId
                FROM CTE_
                    LEFT OUTER JOIN exp.material ON End_ = material.objectId  LEFT OUTER JOIN exp.materialsource ON material.cpasType = materialsource.lsid
                    LEFT OUTER JOIN exp.data on End_ = data.objectId LEFT OUTER JOIN exp.dataclass ON data.cpasType = dataclass.lsid
                WHERE Depth_ > 0 AND materialsource.rowid IS NOT NULL OR dataclass.rowid IS NOT NULL) _inner_
            """;

    static String mssqlDescendantClosureCTE = String.format("""
            WITH CTE_ AS (

                SELECT
                    RowId,
                    ObjectId as End_,
                    '/' + CAST(ObjectId AS VARCHAR(MAX)) + '/' as Path_,
                    0 as Depth_
                /*FROM*/

                    UNION ALL

                SELECT CTE_.RowId, Edge.ToObjectId as End_, CTE_.Path_ + CAST(Edge.ToObjectId AS VARCHAR) + '/' as Path_, Depth_ + 1 as Depth_
                FROM CTE_ INNER JOIN exp.Edge ON CTE_.End_ = Edge.FromObjectId
                WHERE Depth_ < %d AND 0 = CHARINDEX('/' + CAST(Edge.ToObjectId AS VARCHAR) + '/', Path_)
            )
            """, (MAX_ANCESTOR_LOOKUP_DEPTH));

    static String mssqlDescendantClosureSql = """
                (SELECT DISTINCT COALESCE(material.objectId, data.objectId) as descendantObjectId,
                FROM CTE_
                    LEFT OUTER JOIN exp.material ON End_ = material.objectId  LEFT OUTER JOIN exp.materialsource ON material.cpasType = materialsource.lsid
                    LEFT OUTER JOIN exp.data on End_ = data.objectId LEFT OUTER JOIN exp.dataclass ON data.cpasType = dataclass.lsid
                WHERE Depth_ > 0 AND materialsource.rowid IS NOT NULL OR dataclass.rowid IS NOT NULL)
            """;


    public static SQLFragment selectAndInsertSql(SqlDialect d, SQLFragment from, @Nullable String into, @Nullable String insert)
    {
        String cte = d.isPostgreSQL() ? pgAncestorClosureCTE : mssqlAncestorClosureCTE;
        String select = d.isPostgreSQL() ? pgAncestorClosureSql : mssqlAncestorClosureSql;

        String[] cteParts = StringUtils.splitByWholeSeparator(cte,"/*FROM*/");
        assert cteParts.length == 2;

        String[] selectIntoParts = StringUtils.splitByWholeSeparator(select,"/*INTO*/");
        assert selectIntoParts.length == 2;

        SQLFragment sql = new SQLFragment()
                .append(cteParts[0]).append(" ").append(from).append(" ").append(cteParts[1]);
        if (insert != null)
            sql.append(insert);
        sql.append(selectIntoParts[0]);
        if (into != null)
            sql.append(into);
        sql.append(selectIntoParts[1]);
        return sql;
    }


    public static SQLFragment selectDescendantIdsSql(SqlDialect d, SQLFragment from)
    {
        String cte = d.isPostgreSQL() ? pgDescendantClosureCTE : mssqlDescendantClosureCTE;
        String select = d.isPostgreSQL() ? pgDescendantClosureSql : mssqlDescendantClosureSql;

        String[] cteParts = StringUtils.splitByWholeSeparator(cte,"/*FROM*/");
        assert cteParts.length == 2;

        SQLFragment sql = new SQLFragment()
                .append(cteParts[0]).append(" ").append(from).append(" ").append(cteParts[1]);
        sql.append(select);
        return sql;
    }

    public static SQLFragment selectIntoTempTableSql(SqlDialect d, SQLFragment from, @Nullable String tempTable)
    {
        String into = " INTO temp.${NAME} ";
        if (null != tempTable)
            into = " INTO temp." + tempTable + " ";
        return selectAndInsertSql(d, from, into, null);
    }

    /*
     * This can be used to add a column directly to an exp table, or to create a column
     * in an intermediate fake lookup table
     */
    static MutableColumnInfo createAncestorDataLookupColumn(final ColumnInfo fkRowId, boolean isSampleSource, ExpObject target, ExpObject sourceType)
    {
        if (sourceType != null && !(sourceType instanceof ExpSampleType) && !(sourceType instanceof ExpDataClass))
            throw new IllegalStateException();
        if (!(target instanceof ExpSampleType) && !(target instanceof ExpDataClass))
            throw new IllegalStateException();

        final TableType targetType = TableType.fromExpObject(target);

        TableInfo parentTable = fkRowId.getParentTable();

        var ret = new ExprColumn(parentTable, target.getName(), new SQLFragment("#ERROR#"), JdbcType.INTEGER)
        {
            @Override
            public SQLFragment getValueSql(String tableAlias)
            {
                SQLFragment objectId = fkRowId.getValueSql(tableAlias);
                return ClosureQueryHelper.getValueSql(isSampleSource, sourceType, objectId, target);
            }
        };
        ret.setDisplayColumnFactory(AncestorLookupDisplayColumn::new);
        ret.setLabel(target.getName());
        UserSchema schema = Objects.requireNonNull(parentTable.getUserSchema());

        // Determine the container scope of the lookup
        ContainerFilter cf = QueryService.get().getProductContainerFilterForLookups(schema.getContainer(), schema.getUser(), null);
        if (cf == null)
            cf = parentTable.getContainerFilter();

        var builder = new QueryForeignKey.Builder(schema, cf).table(target.getName()).key("rowid");
        builder.schema(targetType.schemaKey);
        var qfk = new QueryForeignKey(builder) {
            @Override
            public ColumnInfo createLookupColumn(ColumnInfo foreignKey, String displayField)
            {
                var ret = (MutableColumnInfo) super.createLookupColumn(foreignKey, displayField);
                if (ret != null)
                {
                    if (ret.getConceptURI() == null)
                        ret.setConceptURI(CONCEPT_URI);
                    DisplayColumnFactory originalDisplayColumnFactory = ret.getDisplayColumnFactory();
                    ret.setDisplayColumnFactory(colInfo -> new AncestorLookupDisplayColumn(foreignKey, colInfo, originalDisplayColumnFactory));
                }
                return ret;
            }
        };
        ret.setFk(qfk);

        // Don't override an existing conceptUri
        if(ret.getConceptURI() == null)
            ret.setConceptURI(CONCEPT_URI);
        return ret;
    }

    public static SQLFragment getValueSql(boolean isSampleType, @Nullable ExpObject sourceType, SQLFragment sourceRowId, ExpObject target)
    {
        if (target instanceof ExpSampleType st)
            return getValueSql(isSampleType, sourceType, sourceRowId, "m" + st.getRowId());
        if (target instanceof ExpDataClass dc)
            return getValueSql(isSampleType, sourceType, sourceRowId, "d" + dc.getRowId());
        throw new IllegalStateException();
    }


    private static SQLFragment getValueSql(boolean isSample, @Nullable ExpObject sourceType, SQLFragment sourceRowId, String targetTypeId)
    {
        TableInfo info = isSample ? ExperimentServiceImpl.get().getTinfoMaterialAncestors() : ExperimentServiceImpl.get().getTinfoDataAncestors();
        SQLFragment sql = new SQLFragment()
                .append("(SELECT ancestorRowId FROM ")
                .append(info)
                .append(" WHERE ancestorTypeId=").appendValue(targetTypeId);
        // TODO would this be useful or not?
//        if (sourceType != null)
//            sql.append(" AND typeId=").appendValue(sourceType.getRowId());
        sql.append(" AND RowId=").append(sourceRowId)
            .append(")");
        return sql;
    }

    static final AtomicInteger temptableNumber = new AtomicInteger();

    private static void incrementalRecomputeForDescendants(SQLFragment from)
    {
        SQLFragment fromDescendants = new SQLFragment("FROM (SELECT p.RowId, p.ObjectId FROM ").append(ExperimentServiceImpl.get().getTinfoMaterial(), "p")
                .append(" WHERE p.objectId IN (").append(selectDescendantIdsSql(getScope().getSqlDialect(), from))
                .append(")) x");
        incrementalAncestorRecompute(fromDescendants, true);

        fromDescendants = new SQLFragment("FROM (SELECT p.RowId, p.ObjectId FROM ").append(ExperimentServiceImpl.get().getTinfoData(), "p")
                .append(" WHERE p.objectId IN (").append(selectDescendantIdsSql(getScope().getSqlDialect(), from))
                .append(")) x");
        incrementalAncestorRecompute(fromDescendants, false);

    }

    private static void incrementalAncestorRecompute(SQLFragment from, boolean isSampleType)
    {
        TempTableTracker ttt = null;
        try
        {
            Object ref = new Object();
            String tempTableName = "closinc_"+temptableNumber.incrementAndGet();
            ttt = TempTableTracker.track(tempTableName, ref);
            SQLFragment selectInto = selectIntoTempTableSql(getScope().getSqlDialect(), from, tempTableName);
            new SqlExecutor(getScope()).execute(selectInto);

            SQLFragment upsert;
            TableInfo tInfo = isSampleType ? ExperimentServiceImpl.get().getTinfoMaterialAncestors() : ExperimentServiceImpl.get().getTinfoDataAncestors();
            DbScope scope = tInfo.getSchema().getScope();
            SqlDialect dialect = scope.getSqlDialect();

            if (dialect.isPostgreSQL())
            {
                upsert = new SQLFragment()
                        .append("INSERT INTO ").append(tInfo)
                        .append(" (RowId, AncestorRowId, AncestorTypeId)\n")
                        .append("SELECT RowId, ancestorRowId, ancestorTypeId FROM temp.").append(tempTableName).append(" TMP\n")
                        .append("ON CONFLICT(RowId,ancestorTypeId) DO UPDATE SET ancestorRowId = EXCLUDED.ancestorRowId").appendEOS();
            }
            else
            {
                upsert = new SQLFragment()
                        .append("MERGE ").append(tInfo, "Target")
                        .append(" USING (SELECT RowId, AncestorRowId, AncestorTypeId FROM temp.").append(tempTableName)
                        .append(") AS Source ON Target.RowId=Source.RowId AND Target.AncestorTypeId=Source.ancestorTypeId\n")
                        .append("WHEN MATCHED THEN UPDATE SET Target.AncestorTypeId = Source.ancestorTypeId\n")
                        .append("WHEN NOT MATCHED THEN INSERT (RowId, AncestorRowId, AncestorTypeId) VALUES (Source.RowId, Source.ancestorRowId, Source.ancestorTypeId)").appendEOS();
            }

            new SqlExecutor(scope).execute(upsert);

            /*
            SELECT rowId, ancestorTypeId FROM exp.materialancestors WHERE RowId IN (3878144, 3878146, 3878151)
                EXCEPT
            SELECT rowId, ancestorTypeId FROM temp.closinc_1 WHERE RowId IN (3878144, 3878146, 3878151)
             */
            // now delete the rows for ancestor types no longer in the mix.
            List<Integer> rowIds = new SqlSelector(scope, "SELECT DISTINCT RowId FROM temp." + tempTableName).getArrayList(Integer.class);
            SQLFragment toDelete = new SQLFragment("SELECT rowId, ancestorTypeId FROM ").append(tInfo).append(" WHERE RowId ");
            dialect.appendInClauseSql(toDelete, rowIds);
            toDelete.append(" EXCEPT\n")
                    .append("SELECT rowId, ancestorTypeId FROM temp.").append(tempTableName).append(" WHERE RowId ");
            dialect.appendInClauseSql(toDelete, rowIds);

            new SqlSelector(scope, toDelete).mapStream().forEach(map -> {
                SQLFragment delete = new SQLFragment("DELETE FROM ").append(tInfo)
                        .append(" WHERE RowId = ?").add(map.get("rowId"))
                        .append(" AND AncestorTypeId = ?").add(map.get("AncestorTypeId"));

                new SqlExecutor(scope).execute(delete);
            });
        }
        finally
        {
            if (null != ttt)
                ttt.delete();
        }
    }

    private static void incrementalRecompute(SQLFragment from, boolean isSampleType)
    {
        incrementalAncestorRecompute(from, isSampleType);
        incrementalRecomputeForDescendants(from);
    }

    public static void clearAncestorsForMaterial(int rowId)
    {
        var tx = getScope().getCurrentTransaction();
        if (null != tx)
        {
            tx.addCommitTask(() -> clearAncestorsForMaterial(rowId), DbScope.CommitTaskOption.POSTCOMMIT);
            return;
        }
        SQLFragment sql = new SQLFragment("DELETE FROM ").append(ExperimentService.get().getTinfoMaterialAncestors())
                .append(" WHERE RowId=").appendValue(rowId);
        new SqlExecutor(getScope()).execute(sql);
        incrementalRecomputeForDescendants(new SQLFragment("FROM exp.material WHERE rowId=?").add(rowId));
    }

    public static void clearAncestorsForDataObject(int rowId)
    {
        var tx = getScope().getCurrentTransaction();
        if (null != tx)
        {
            tx.addCommitTask(() -> clearAncestorsForDataObject(rowId), DbScope.CommitTaskOption.POSTCOMMIT);
            return;
        }
        SQLFragment sql = new SQLFragment("DELETE FROM ").append(ExperimentService.get().getTinfoDataAncestors())
                .append(" WHERE RowId=").appendValue(rowId);
        new SqlExecutor(getScope()).execute(sql);
        incrementalRecomputeForDescendants(new SQLFragment("FROM exp.data WHERE rowId=?").add(rowId));
    }

    public static void populateMaterialAncestors(Logger logger)
    {
        DbSchema schema = ExperimentService.get().getSchema();

        ContainerManager.getAllChildren(ContainerManager.getRoot()).forEach(
                container -> {
                    logger.info("Adding rows to exp.materialAncestors from samples in container " + container.getPath());
                    SampleTypeService.get().getSampleTypes(container, null, false).forEach(
                            sampleType -> {
                                logger.debug("   Adding rows from samples in sampleType " + sampleType.getName() + " in container " + container.getPath());
                                SQLFragment from = new SQLFragment(" FROM exp.material WHERE container = ?").add(container.getEntityId())
                                        .append(" AND materialSourceId = ?").add(sampleType.getRowId());
                                SQLFragment sql = ClosureQueryHelper.selectAndInsertSql(schema.getSqlDialect(), from, null, "INSERT INTO exp.materialAncestors (RowId, AncestorRowId, AncestorTypeId) ");
                                new SqlExecutor(schema.getScope()).execute(sql);
                            }
                    );
                }
        );
        logger.info("Finished populating exp.materialAncestors");
    }

    public static void populateDataAncestors(Logger logger)
    {
        DbSchema schema = ExperimentService.get().getSchema();

        ContainerManager.getAllChildren(ContainerManager.getRoot()).forEach(
                container -> {
                    logger.info("Adding rows to exp.dataAncestors from data objects in container " + container.getPath());
                    ExperimentService.get().getDataClasses(container, null, false).forEach(
                            dataClass -> {
                                logger.debug("    Adding rows to exp.dataAncestors from data class " + dataClass.getName() + " in container " + container.getPath());
                                SQLFragment from = new SQLFragment(" FROM exp.data WHERE container = ?").add(container.getEntityId())
                                        .append(" AND classId = ?").add(dataClass.getRowId());
                                SQLFragment sql = ClosureQueryHelper.selectAndInsertSql(schema.getSqlDialect(), from, null,
                                        "INSERT INTO exp.dataAncestors (RowId, AncestorRowId, AncestorTypeId) ");
                                new SqlExecutor(schema.getScope()).execute(sql);
                            }
                    );
                }
        );
        logger.info("Finished populating exp.dataAncestors");
    }

    public static void truncateAndRecreate()
    {
        try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction())
        {
            TableInfo tInfo = ExperimentServiceImpl.get().getTinfoMaterialAncestors();
            new SqlExecutor(tInfo.getSchema()).execute("TRUNCATE TABLE " + tInfo);
            ClosureQueryHelper.populateMaterialAncestors(logger);

            tInfo = ExperimentServiceImpl.get().getTinfoDataAncestors();
            new SqlExecutor(tInfo.getSchema()).execute("TRUNCATE TABLE " + tInfo);
            ClosureQueryHelper.populateDataAncestors(logger);

            transaction.commit();
        }
    }

    public static void invalidateMaterialsForRun(String sourceTypeLsid, int runId)
    {
        var tx = getScope().getCurrentTransaction();
        if (null != tx)
        {
            tx.addCommitTask(() -> invalidateMaterialsForRun(sourceTypeLsid, runId), DbScope.CommitTaskOption.POSTCOMMIT);
            return;
        }

        SQLFragment seedFrom = new SQLFragment()
                .append("FROM (SELECT m.RowId, m.ObjectId FROM exp.material m\n")
                .append("INNER JOIN exp.MaterialInput mi ON m.rowId = mi.materialId\n")
                .append("INNER JOIN exp.ProtocolApplication pa ON mi.TargetApplicationId = pa.RowId\n")
                .append("WHERE pa.RunId = ").appendValue(runId)
                .append(" AND m.cpasType = ? ").add(sourceTypeLsid)
                .append(" AND pa.CpasType = ").appendValue(ExperimentRunOutput).append(") _seed_ ");
        incrementalRecompute(seedFrom, true);
    }

    public static void invalidateDataObjectsForRun(String sourceTypeLsid, int runId)
    {
        var tx = getScope().getCurrentTransaction();
        if (null != tx)
        {
            tx.addCommitTask(() -> invalidateDataObjectsForRun(sourceTypeLsid, runId), DbScope.CommitTaskOption.POSTCOMMIT);
            return;
        }

        SQLFragment seedFrom = new SQLFragment()
                .append("FROM (SELECT d.RowId, d.ObjectId FROM exp.data d\n")
                .append("INNER JOIN exp.DataInput di ON d.rowId = di.dataId\n")
                .append("INNER JOIN exp.ProtocolApplication pa ON di.TargetApplicationId = pa.RowId\n")
                .append("WHERE pa.RunId = ").appendValue(runId)
                .append(" AND d.cpasType = ? ").add(sourceTypeLsid)
                .append(" AND pa.CpasType = ").appendValue(ExperimentRunOutput).append(") _seed_ ");
        incrementalRecompute(seedFrom, false);
    }

    private static DbScope getScope()
    {
        return CoreSchema.getInstance().getScope();
    }

    /*
     * Code to create the lineage ancestor lookup column and intermediate lookups that use ClosureQueryHelper
     */
    public static MutableColumnInfo createAncestorLookupColumnInfo(String columnName, FilteredTable<UserSchema> parent, ColumnInfo rowId, @Nullable ExpObject source, boolean isSample)
    {
        MutableColumnInfo wrappedRowId = parent.wrapColumn(columnName, rowId);
        wrappedRowId.setIsUnselectable(true);
        wrappedRowId.setReadOnly(true);
        wrappedRowId.setCalculated(true);
        wrappedRowId.setRequired(false);
        wrappedRowId.setUserEditable(false);
        wrappedRowId.setFk(new AbstractForeignKey(parent.getUserSchema(),parent.getContainerFilter())
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return new LineageLookupTypesTableInfo(parent.getUserSchema(), source, isSample);
            }

            @Override
            public @Nullable ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
            {
                ColumnInfo lk = getLookupTableInfo().getColumn(displayField);
                if (null == lk)
                    return null;
                var ret = new ExprColumn(parent.getParentTable(), new FieldKey(parent.getFieldKey(),lk.getName()), null, JdbcType.INTEGER)
                {
                    @Override
                    public SQLFragment getValueSql(String tableAlias)
                    {
                        return parent.getValueSql(tableAlias);
                    }
                };
                ret.setFk(lk.getFk());
                return ret;
            }

            @Override
            public StringExpression getURL(ColumnInfo parent)
            {
                return null;
            }
        });
        wrappedRowId.setConceptURI(CONCEPT_URI);
        wrappedRowId.setShownInDetailsView(false);
        wrappedRowId.setShownInInsertView(false);
        wrappedRowId.setShownInUpdateView(false);
        return wrappedRowId;
    }

    public enum  TableType
    {
        SampleType("Samples", "materials")
                {
                    @Override
                    Collection<? extends ExpObject> getInstances(Container c, User u)
                    {
                        return SampleTypeServiceImpl.get()
                                .getSampleTypes(c, u,true)
                                .stream()
                                .filter(this::isInstance)
                                .collect(Collectors.toList());
                    }
                    @Override
                    ExpObject getInstance(Container c, User u, String name)
                    {
                        return SampleTypeServiceImpl.get().getSampleType(c, u, name);
                    }
                    @Override
                    boolean isInstance(ExpObject expObject)
                    {
                        return expObject instanceof ExpSampleType && !((ExpSampleType) expObject).isMedia();
                    }
                },
        RegistryOrSourceType("RegistryAndSources", "data")
                {
                    @Override
                    Collection<? extends ExpObject> getInstances(Container c, User u)
                    {
                        return ExperimentServiceImpl.get().getDataClasses(c, u,true)
                                .stream()
                                .filter(this::isInstance)
                                .collect(Collectors.toList());
                    }
                    @Override
                    ExpObject getInstance(Container c, User u, String name)
                    {
                        return ExperimentServiceImpl.get().getDataClass(c, u, name);
                    }
                    @Override
                    boolean isInstance(ExpObject expObject)
                    {
                        return expObject instanceof ExpDataClass && (((ExpDataClass) expObject).isSource() || ((ExpDataClass) expObject).isRegistry());
                    }
                },
        MediaData("MediaData", "data")
                {
                    @Override
                    Collection<? extends ExpObject> getInstances(Container c, User u)
                    {
                        return ExperimentServiceImpl.get()
                                .getDataClasses(c, u,true)
                                .stream()
                                .filter(this::isInstance)
                                .collect(Collectors.toList());
                    }
                    @Override
                    ExpObject getInstance(Container c, User u, String name)
                    {
                        return ExperimentServiceImpl.get().getDataClass(c, u, name);
                    }
                    @Override
                    boolean isInstance(ExpObject expObject)
                    {
                        return expObject instanceof ExpDataClass && ((ExpDataClass) expObject).isMedia();
                    }
                },
        MediaSamples("MediaSamples", "materials")
                {
                    @Override
                    Collection<? extends ExpObject> getInstances(Container c, User u)
                    {
                        return SampleTypeServiceImpl.get()
                                .getSampleTypes(c, u,true)
                                .stream()
                                .filter(this::isInstance)
                                .collect(Collectors.toList());
                    }
                    @Override
                    ExpObject getInstance(Container c, User u, String name)
                    {
                        return SampleTypeServiceImpl.get().getSampleType(c, u, name);
                    }
                    @Override
                    boolean isInstance(ExpObject expObject)
                    {
                        return expObject instanceof ExpSampleType && ((ExpSampleType) expObject).isMedia();
                    }
                },
        DataClass("OtherData", "data")
                {
                    @Override
                    Collection<? extends ExpObject> getInstances(Container c, User u)
                    {
                        return ExperimentServiceImpl.get()
                                .getDataClasses(c, u,true)
                                .stream()
                                .filter(this::isInstance)
                                .collect(Collectors.toList());
                    }
                    @Override
                    ExpObject getInstance(Container c, User u, String name)
                    {
                        return ExperimentServiceImpl.get().getDataClass(c, u, name);
                    }
                    @Override
                    boolean isInstance(ExpObject expObject)
                    {
                        return expObject instanceof ExpDataClass && ((ExpDataClass) expObject).getCategory() == null;
                    }
                },
        ;

        final String lookupName;
        final String userSchemaTableName;
        final SchemaKey schemaKey;

        TableType(String lookupName, String userSchemaTableName)
        {
            this.lookupName = lookupName;
            this.userSchemaTableName = userSchemaTableName;
            this.schemaKey = SchemaKey.fromParts("exp", userSchemaTableName);
        }

        abstract Collection<? extends ExpObject> getInstances(Container c, User u);
        abstract ExpObject getInstance(Container c, User u, String name);
        abstract boolean isInstance(ExpObject object);

        String getDbTableName()
        {
            return userSchemaTableName.equalsIgnoreCase("materials") ? "Material" : userSchemaTableName;
        }

        static TableType fromExpObject(ExpObject object)
        {
            for (TableType type:  TableType.values())
                if (type.isInstance(object))
                    return type;
            throw new NotFoundException("No table type found for object " + object.getName() + " with class " + object.getClass());
        }
    }

    private static class LineageLookupTypesTableInfo extends VirtualTable<UserSchema>
    {
        LineageLookupTypesTableInfo(UserSchema userSchema, @Nullable ExpObject source, boolean isSampleSource)
        {
            super(userSchema.getDbSchema(), "LineageLookupTypes", userSchema);

            for (var lk : TableType.values())
            {
                var col = new BaseColumnInfo(lk.lookupName, this, JdbcType.INTEGER);
                col.setIsUnselectable(true);
                col.setFk(new AbstractForeignKey(getUserSchema(),null)
                {
                    @Override
                    public @Nullable ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
                    {
                        if (null == displayField)
                            return null;
                        if (null == _userSchema)
                            return null;
                        var target = lk.getInstance(_userSchema.getContainer(), _userSchema.getUser(), displayField);
                        if (null == target)
                            return null;
                        return ClosureQueryHelper.createAncestorDataLookupColumn(parent, isSampleSource, target, source);
                    }

                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        return new AncestorLookupTableInfo(userSchema, isSampleSource, lk, source);
                    }

                    @Override
                    public StringExpression getURL(ColumnInfo parent)
                    {
                        return null;
                    }
                });
                addColumn(col);
            }
        }
    }

    private static class AncestorLookupTableInfo extends VirtualTable<UserSchema>
    {
        AncestorLookupTableInfo(UserSchema userSchema, boolean isSampleSource, TableType type, @Nullable ExpObject source)
        {
            super(userSchema.getDbSchema(), "Ancestor Lookup", userSchema);
            ColumnInfo wrap = new BaseColumnInfo("rowid", this, JdbcType.INTEGER);
            for (var target : type.getInstances(_userSchema.getContainer(), _userSchema.getUser()))
                addColumn(ClosureQueryHelper.createAncestorDataLookupColumn(wrap, isSampleSource, target, source));
        }
    }

}
