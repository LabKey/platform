package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MaterializedQueryHelper;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableTracker;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MemTrackerListener;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.NotFoundException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.labkey.api.exp.api.ExpProtocol.ApplicationType.ExperimentRunOutput;


public class ClosureQueryHelper
{
    final static String CONCEPT_URI = "http://www.labkey.org/types#ancestorLookup";

    final static long CACHE_INVALIDATION_INTERVAL = TimeUnit.MINUTES.toMillis(5);
    final static long CACHE_LRU_AGE_OUT_INTERVAL = TimeUnit.MINUTES.toMillis(30);

    /* TODO/CONSIDER every SampleType and Dataclass should have a unique ObjectId so it can be stored as an in lineage tables (e.g. edge/closure tables) */

    record ClosureTable(MaterializedQueryHelper helper, AtomicInteger counter, TableType type, String lsid) {};

    static final Map<String, ClosureTable> queryHelpers = Collections.synchronizedMap(new HashMap<>());
    // use this as a separate LRU implementation, because I only want to track calls to getValueSql() not other calls to queryHelpers.get()
    static final Map<String, Long> lruQueryHelpers = new LinkedHashMap<>(100,0.75f,true);

    static
    {
        MemTracker.getInstance().register(new MemTrackerListener()
        {
            @Override
            public void beforeReport(Set<Object> set)
            {
                synchronized (queryHelpers)
                {
                    queryHelpers.values().forEach(ch -> set.add(ch.helper));
                }
            }
        });
    }


    static final int MAX_LINEAGE_LOOKUP_DEPTH = 10;

    static String pgMaterialClosureCTE = String.format("""
            WITH RECURSIVE CTE_ AS (

                SELECT
                    RowId AS Start_,
                    ObjectId as End_,
                    '/' || CAST(ObjectId AS VARCHAR) || '/' as Path_,
                    0 as Depth_
                /*FROM*/

                    UNION ALL

                SELECT CTE_.Start_, Edge.FromObjectId as End_, CTE_.Path_ || CAST(Edge.FromObjectId AS VARCHAR) || '/' as Path_, Depth_ + 1 as Depth_
                FROM CTE_ INNER JOIN exp.Edge ON CTE_.End_ = Edge.ToObjectId
                WHERE Depth_ < %d AND 0 = POSITION('/' || CAST(Edge.FromObjectId AS VARCHAR) || '/' IN Path_)
                
            )
            """, MAX_LINEAGE_LOOKUP_DEPTH);

    static String pgMaterialClosureSql = """
            SELECT Start_, CAST(CASE WHEN COUNT(*) = 1 THEN MIN(rowId) ELSE -1 * COUNT(*) END AS INT) AS rowId, targetId
            /*INTO*/
            FROM (
                SELECT Start_, End_,
                    COALESCE(material.rowid, data.rowid) as rowId,
                    COALESCE('m' || CAST(materialsource.rowid AS VARCHAR), 'd' || CAST(dataclass.rowid AS VARCHAR)) as targetId
                FROM CTE_
                    LEFT OUTER JOIN exp.material ON End_ = material.objectId  LEFT OUTER JOIN exp.materialsource ON material.cpasType = materialsource.lsid
                    LEFT OUTER JOIN exp.data on End_ = data.objectId LEFT OUTER JOIN exp.dataclass ON data.cpasType = dataclass.lsid
                WHERE Depth_ > 0 AND materialsource.rowid IS NOT NULL OR dataclass.rowid IS NOT NULL) _inner_
            GROUP BY targetId, Start_
            """;


    static String mssqlMaterialClosureCTE = String.format("""
            WITH CTE_ AS (

                SELECT
                    RowId AS Start_,
                    ObjectId as End_,
                    '/' + CAST(ObjectId AS VARCHAR(MAX)) + '/' as Path_,
                    0 as Depth_
                /*FROM*/

                    UNION ALL

                SELECT CTE_.Start_, Edge.FromObjectId as End_, CTE_.Path_ + CAST(Edge.FromObjectId AS VARCHAR) + '/' as Path_, Depth_ + 1 as Depth_
                FROM CTE_ INNER JOIN exp.Edge ON CTE_.End_ = Edge.ToObjectId
                WHERE Depth_ < %d AND 0 = CHARINDEX('/' + CAST(Edge.FromObjectId AS VARCHAR) + '/', Path_)
            )
            """, (MAX_LINEAGE_LOOKUP_DEPTH));

    static String mssqlMaterialClosureSql = """
            SELECT Start_, CAST(CASE WHEN COUNT(*) = 1 THEN MIN(rowId) ELSE -1 * COUNT(*) END AS INT) AS rowId, targetId
            /*INTO*/
            FROM (
                SELECT Start_, End_,
                    COALESCE(material.rowid, data.rowid) as rowId,
                    COALESCE('m' + CAST(materialsource.rowid AS VARCHAR), 'd' + CAST(dataclass.rowid AS VARCHAR)) as targetId
                FROM CTE_
                    LEFT OUTER JOIN exp.material ON End_ = material.objectId  LEFT OUTER JOIN exp.materialsource ON material.cpasType = materialsource.lsid
                    LEFT OUTER JOIN exp.data on End_ = data.objectId LEFT OUTER JOIN exp.dataclass ON data.cpasType = dataclass.lsid
                WHERE Depth_ > 0 AND materialsource.rowid IS NOT NULL OR dataclass.rowid IS NOT NULL) _inner_
            GROUP BY targetId, Start_
            """;




    static SQLFragment selectIntoSql(SqlDialect d, SQLFragment from, @Nullable String tempTable)
    {
        String cte = d.isPostgreSQL() ? pgMaterialClosureCTE : mssqlMaterialClosureCTE;
        String select = d.isPostgreSQL() ? pgMaterialClosureSql : mssqlMaterialClosureSql;

        String[] cteParts = StringUtils.splitByWholeSeparator(cte,"/*FROM*/");
        assert cteParts.length == 2;

        String into = " INTO temp.${NAME} ";
        if (null != tempTable)
            into = " INTO temp." + tempTable + " ";
        String[] selectIntoParts = StringUtils.splitByWholeSeparator(select,"/*INTO*/");
        assert selectIntoParts.length == 2;

        return new SQLFragment()
                .append(cteParts[0]).append(" ").append(from).append(" ").append(cteParts[1])
                .append(selectIntoParts[0]).append(into).append(selectIntoParts[1]);
    }


    static SQLFragment selectSql(SqlDialect d, SQLFragment from)
    {
        String cte = d.isPostgreSQL() ? pgMaterialClosureCTE : mssqlMaterialClosureCTE;
        String select = d.isPostgreSQL() ? pgMaterialClosureSql : mssqlMaterialClosureSql;

        String[] cteParts = StringUtils.splitByWholeSeparator(cte,"/*FROM*/");
        assert cteParts.length == 2;

        return new SQLFragment(cteParts[0]).append(from).append(cteParts[1]).append(select);
    }


    /*
     * This can be used to add a column directly to an exp table, or to create a column
     * in an intermediate fake lookup table
     */
    static MutableColumnInfo createLineageDataLookupColumn(final ColumnInfo fkRowId, ExpObject source, ExpObject target)
    {
        if (!(source instanceof ExpSampleType) && !(source instanceof ExpDataClass))
            throw new IllegalStateException();
        if (!(target instanceof ExpSampleType) && !(target instanceof ExpDataClass))
            throw new IllegalStateException();

        final TableType sourceType = TableType.fromExpObject(source);
        final TableType targetType = TableType.fromExpObject(target);

        TableInfo parentTable = fkRowId.getParentTable();
        var ret = new ExprColumn(parentTable, target.getName(), new SQLFragment("#ERROR#"), JdbcType.INTEGER)
        {
            @Override
            public SQLFragment getValueSql(String tableAlias)
            {
                SQLFragment objectId = fkRowId.getValueSql(tableAlias);
                String sourceLsid = source.getLSID();
                if (sourceLsid == null)
                    return new SQLFragment(" NULL ");
                return ClosureQueryHelper.getValueSql(sourceType, sourceLsid, objectId, target);
            }
        };
        ret.setDisplayColumnFactory(AncestorLookupDisplayColumn::new);
        ret.setLabel(target.getName());
        UserSchema schema = Objects.requireNonNull(parentTable.getUserSchema());
        var builder = new QueryForeignKey.Builder(schema, parentTable.getContainerFilter()).table(target.getName()).key("rowid");
        if (sourceType != targetType)
            builder.schema(targetType.schemaKey);
        var qfk = new QueryForeignKey(builder) {
            @Override
            public ColumnInfo createLookupColumn(ColumnInfo foreignKey, String displayField)
            {
                var ret = (MutableColumnInfo) super.createLookupColumn(foreignKey, displayField);
                if (ret != null)
                {
                    ret.setDisplayColumnFactory(colInfo -> new AncestorLookupDisplayColumn(foreignKey, colInfo));
                    ret.setConceptURI(CONCEPT_URI);
                }
                return ret;
            }
        };
        ret.setFk(qfk);
        ret.setConceptURI(CONCEPT_URI);
        return ret;
    }


    public static SQLFragment getValueSql(TableType type, String sourceLSID, SQLFragment objectId, ExpObject target)
    {
        if (target instanceof ExpSampleType st)
            return getValueSql(type, sourceLSID, objectId, "m" + st.getRowId());
        if (target instanceof ExpDataClass dc)
            return getValueSql(type, sourceLSID, objectId, "d" + dc.getRowId());
        throw new IllegalStateException();
    }


    private static SQLFragment getValueSql(TableType type, String sourceLSID, SQLFragment objectId, String targetId)
    {
        MaterializedQueryHelper helper = getClosureHelper(type, sourceLSID, true);

        return new SQLFragment()
                .append("(SELECT rowId FROM ")
                .append(helper.getFromSql("CLOS", null))
                .append(" WHERE targetId='").append(targetId).append("'")
                .append(" AND Start_=").append(objectId)
                .append(")");
    }


    static final AtomicInteger temptableNumber = new AtomicInteger();

    private static void incrementalRecompute(String sourceLSID, SQLFragment from)
    {
        // if there's nothing cached, we don't need to do incremental
        MaterializedQueryHelper helper = getClosureHelper(null, sourceLSID, false);
        if (null == helper || !helper.isCached(null))
            return;

        TempTableTracker ttt = null;
        try
        {
            Object ref = new Object();
            String tempTableName = "closinc_"+temptableNumber.incrementAndGet();
            ttt = TempTableTracker.track(tempTableName, ref);
            SQLFragment selectInto = selectIntoSql(getScope().getSqlDialect(), from, tempTableName);
            new SqlExecutor(getScope()).execute(selectInto);

            SQLFragment upsert;
            if (getScope().getSqlDialect().isPostgreSQL())
            {
                upsert = new SQLFragment()
                        .append("INSERT INTO temp.${NAME} (Start_, rowId, targetid)\n")
                        .append("SELECT Start_, RowId, targetId FROM temp.").append(tempTableName).append(" TMP\n")
                        .append("ON CONFLICT(Start_,targetId) DO UPDATE SET rowId = EXCLUDED.rowId;");
            }
            else
            {
                upsert = new SQLFragment()
                        .append("MERGE temp.${NAME} AS Target\n")
                        .append("USING (SELECT Start_, RowId, targetId FROM temp.").append(tempTableName).append(") AS Source ON Target.Start_=Source.Start_ AND Target.targetid=Source.targetId\n")
                        .append("WHEN MATCHED THEN UPDATE SET Target.targetId = Source.targetId\n")
                        .append("WHEN NOT MATCHED THEN INSERT (Start_, rowId, targetid) VALUES (Source.Start_, Source.rowId, Source.targetId);");
            }

            helper.upsert(upsert);
        }
        catch (Exception x)
        {
            invalidate(sourceLSID);
            throw x;
        }
        finally
        {
            if (null != ttt)
                ttt.delete();
        }
    }


    public static void invalidateAll()
    {
        synchronized (queryHelpers)
        {
            for (var c : queryHelpers.values())
            {
                c.counter.incrementAndGet();
                c.helper.uncache(null);
            }
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
                .append("WHERE pa.RunId = ").append(runId)
                .append(" AND m.cpasType = ? ").add(sourceTypeLsid)
                .append(" AND pa.CpasType = '").append(ExperimentRunOutput.name()).append("') _seed_ ");
        incrementalRecompute(sourceTypeLsid, seedFrom);
    }


    private static MaterializedQueryHelper getClosureHelper(TableType type, String sourceLSID, boolean computeIfAbsent)
    {
        ClosureTable closure;

        if (!computeIfAbsent)
        {
            closure = queryHelpers.get(sourceLSID);
            return null==closure ? null : closure.helper;
        }

        if (null == type)
            throw new IllegalStateException();

        closure = queryHelpers.computeIfAbsent(sourceLSID, cpasType ->
                {
                    SQLFragment from = new SQLFragment(" FROM exp.Material WHERE Material.cpasType = ? ").add(cpasType);
                    SQLFragment selectInto = selectIntoSql(getScope().getSqlDialect(), from, null);

                    var helper =  new MaterializedQueryHelper.Builder("closure", DbSchema.getTemp().getScope(), selectInto)
                        .setIsSelectInto(true)
                        .addIndex("CREATE UNIQUE INDEX uq_${NAME} ON temp.${NAME} (targetId,Start_)")
                        .maxTimeToCache(CACHE_INVALIDATION_INTERVAL)
                        .addInvalidCheck(() -> getInvalidationCounterString(sourceLSID))
                        .build();
                    return new ClosureTable(helper, new AtomicInteger(), type, sourceLSID);
                });

        // update LRU
        synchronized (lruQueryHelpers)
        {
            lruQueryHelpers.put(sourceLSID, HeartBeat.currentTimeMillis());
            checkStaleEntries();
        }

        return closure.helper;
    }


    private static void checkStaleEntries()
    {
        synchronized (lruQueryHelpers)
        {
            if (lruQueryHelpers.isEmpty())
                return;
            var oldestEntry = lruQueryHelpers.entrySet().iterator().next();
            if (HeartBeat.currentTimeMillis() - oldestEntry.getValue() < CACHE_LRU_AGE_OUT_INTERVAL)
                return;
            queryHelpers.remove(oldestEntry.getKey());
            lruQueryHelpers.remove(oldestEntry.getKey());
        }
    }


    private static void invalidate(String sourceLSID)
    {
        var closure = queryHelpers.get(sourceLSID);
        if (null != closure)
            closure.counter.incrementAndGet();
    }


    private static String getInvalidationCounterString(String sourceLSID)
    {
        var closure = queryHelpers.get(sourceLSID);
        return null==closure ? "-1" : String.valueOf(closure.counter.get());
    }


    private static DbScope getScope()
    {
        return CoreSchema.getInstance().getScope();
    }







    /*
     * Code to create the lineage ancestor lookup column and intermediate lookups that use ClosureQueryHelper
     */

    public static MutableColumnInfo createLineageLookupColumnInfo(String columnName, FilteredTable<UserSchema> parent, ColumnInfo rowid, ExpObject source)
    {
        MutableColumnInfo wrappedRowId = parent.wrapColumn(columnName, rowid);
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
                return new LineageLookupTypesTableInfo(parent.getUserSchema(), source);
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


    enum TableType
    {
        SampleType("Samples", SchemaKey.fromParts("exp", "materials") )
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
        RegistryOrSourceType("RegistryAndSources", SchemaKey.fromParts("exp", "data") )
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
        MediaData("MediaData", SchemaKey.fromParts("exp", "data") )
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
        MediaSamples("MediaSamples", SchemaKey.fromParts("exp", "materials") )
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
        DataClass("OtherData", SchemaKey.fromParts("exp", "data") )
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
        final SchemaKey schemaKey;

        TableType(String lookupName, SchemaKey schemaKey)
        {
            this.lookupName = lookupName;
            this.schemaKey = schemaKey;
        }

        abstract Collection<? extends ExpObject> getInstances(Container c, User u);
        abstract ExpObject getInstance(Container c, User u, String name);
        abstract boolean isInstance(ExpObject object);

        static TableType fromExpObject(ExpObject object)
        {
            for (TableType type:  TableType.values())
                if (type.isInstance(object))
                    return type;
            throw new NotFoundException("No table type found for object " + object.getName() + " with class " + object.getClass());
        }
    };


    private static class LineageLookupTypesTableInfo extends VirtualTable<UserSchema>
    {
        LineageLookupTypesTableInfo(UserSchema userSchema, ExpObject source)
        {
            super(userSchema.getDbSchema(), "LineageLookupTypes",userSchema);

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
                        var target = lk.getInstance(_userSchema.getContainer(), _userSchema.getUser(), displayField);
                        if (null == target)
                            return null;
                        return ClosureQueryHelper.createLineageDataLookupColumn(parent, source, target);
                    }

                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        return new LineageLookupTableInfo(userSchema, source, lk);
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


    private static class LineageLookupTableInfo extends VirtualTable<UserSchema>
    {
        LineageLookupTableInfo(UserSchema userSchema, ExpObject source, TableType type)
        {
            super(userSchema.getDbSchema(), "Lineage Lookup", userSchema);
            ColumnInfo wrap = new BaseColumnInfo("rowid", this, JdbcType.INTEGER);
            for (var target : type.getInstances(_userSchema.getContainer(), _userSchema.getUser()))
                addColumn(ClosureQueryHelper.createLineageDataLookupColumn(wrap, source, target));
        }

        @Override
        public @NotNull SQLFragment getFromSQL()
        {
            throw new IllegalStateException();
        }
    }

}
