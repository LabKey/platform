package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
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
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.labkey.api.exp.api.ExpProtocol.ApplicationType.ExperimentRunOutput;


public class ClosureQueryHelper
{
    /* TODO/CONSIDER every SampleType and Dataclass should have a unique ObjectId so it can be stored as an in lineage tables (e.g. edge/closure tables) */

    static final Map<String, MaterializedQueryHelper> queryHelpers = Collections.synchronizedMap(new HashMap<>());
    static final Map<String, AtomicInteger> invalidators =  Collections.synchronizedMap(new HashMap<>());

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
            SELECT Start_, CASE WHEN COUNT(*) = 1 THEN MIN(rowId) ELSE -1 * COUNT(*) END AS rowId, targetId
            /*INTO*/
            FROM (
                SELECT Start_, End_,
                    COALESCE(material.rowid, dataclass.rowid) as rowId,
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
            SELECT Start_, CASE WHEN COUNT(*) = 1 THEN MIN(rowId) ELSE -1 * COUNT(*) END AS rowId, targetId
            /*INTO*/
            FROM (
                SELECT Start_, End_,
                    COALESCE(material.rowid, dataclass.rowid) as rowId,
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
     * This can be used to add a column directly to a exp table, or to create a column
     * in an intermediate fake lookup table
     */
    static MutableColumnInfo createLineageLookupColumn(final ColumnInfo fkRowId, ExpObject source, ExpObject target)
    {
        if (!(source instanceof ExpSampleType) && !(source instanceof ExpDataClass))
            throw new IllegalStateException();
        if (!(target instanceof ExpSampleType) && !(target instanceof ExpDataClass))
            throw new IllegalStateException();

        TableInfo parentTable = fkRowId.getParentTable();
        var ret = new ExprColumn(parentTable, target.getName(), new SQLFragment("#ERROR#"), JdbcType.INTEGER)
        {
            @Override
            public SQLFragment getValueSql(String tableAlias)
            {
                SQLFragment objectId = fkRowId.getValueSql(tableAlias);
                String sourceLsid = source instanceof ExpSampleType ss ? ss.getLSID() : source instanceof ExpDataClass dc ? dc.getLSID() : null;
                if (sourceLsid == null)
                    return new SQLFragment(" NULL ");
                return ClosureQueryHelper.getValueSql(sourceLsid, objectId, target);
            }
        };
        ret.setLabel(target.getName());
        UserSchema schema = Objects.requireNonNull(parentTable.getUserSchema());
        ret.setFk(new QueryForeignKey.Builder(schema, parentTable.getContainerFilter()).table(target.getName()));
        return ret;
    }


    public static SQLFragment getValueSql(String sourceLSID, SQLFragment objectId, ExpObject target)
    {
        if (target instanceof ExpSampleType st)
            return getValueSql(sourceLSID, objectId, "m" + st.getRowId());
        if (target instanceof ExpDataClass dc)
            return getValueSql(sourceLSID, objectId, "d" + dc.getRowId());
        throw new IllegalStateException();
    }


    private static SQLFragment getValueSql(String sourceLSID, SQLFragment objectId, String targetId)
    {
        MaterializedQueryHelper helper = getClosureHelper(sourceLSID, true);

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
        MaterializedQueryHelper helper = getClosureHelper(sourceLSID, false);
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
            getInvalidationCounter(sourceLSID).incrementAndGet();
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
            for (var h : queryHelpers.values())
                h.uncache(null);
        }
    }


//    public static void invalidateCpasType(String sourceTypeLsid)
//    {
//        var counter = invalidators.get(sourceTypeLsid);
//        if (null != counter)
//            counter.incrementAndGet();
//    }


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


    private static MaterializedQueryHelper getClosureHelper(String sourceLSID, boolean computeIfAbsent)
    {
        if (!computeIfAbsent)
            return queryHelpers.get(sourceLSID);

        return queryHelpers.computeIfAbsent(sourceLSID, cpasType ->
                {
                    SQLFragment from = new SQLFragment(" FROM exp.Material WHERE Material.cpasType = ? ").add(cpasType);
                    SQLFragment selectInto = selectIntoSql(getScope().getSqlDialect(), from, null);
                    return new MaterializedQueryHelper.Builder("closure", DbSchema.getTemp().getScope(), selectInto)
                        .setIsSelectInto(true)
                        .addIndex("CREATE UNIQUE INDEX uq_${NAME} ON temp.${NAME} (targetId,Start_)")
                        .maxTimeToCache(TimeUnit.MINUTES.toMillis(5))
                        .addInvalidCheck(() -> String.valueOf(getInvalidationCounter(sourceLSID)))
                        .build();
                });
    }


    private static AtomicInteger getInvalidationCounter(String sourceLSID)
    {
        return invalidators.computeIfAbsent(sourceLSID, (key) -> new AtomicInteger());
    }


    private static DbScope getScope()
    {
        return CoreSchema.getInstance().getScope();
    }
}
