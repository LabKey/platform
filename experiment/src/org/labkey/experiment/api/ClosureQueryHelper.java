package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MaterializedQueryHelper;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryForeignKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class ClosureQueryHelper
{
    /* TODO/CONSIDER every SampleType and Dataclass should have a unique ObjectId so it can be stored as an in lineage tables (e.g. edge/closure tables) */

    static final Map<String, MaterializedQueryHelper> queryHelpers = Collections.synchronizedMap(new HashMap<>());
    static final Map<String, AtomicInteger> invalidators =  Collections.synchronizedMap(new HashMap<>());

    static final int MAX_LINEAGE_LOOKUP_DEPTH = 10;

    static String pgMaterialClosureSql = String.format("""
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

            SELECT Start_, CASE WHEN COUNT(*) = 1 THEN MIN(rowId) ELSE -1 * COUNT(*) END AS rowId, targetId
            /* INTO */
            FROM (
                SELECT Start_, End_,
                    COALESCE(material.rowid, dataclass.rowid) as rowId,
                    COALESCE('m' || CAST(materialsource.rowid AS VARCHAR), 'd' || CAST(dataclass.rowid AS VARCHAR)) as targetId
                FROM CTE_
                    LEFT OUTER JOIN exp.material ON End_ = material.objectId  LEFT OUTER JOIN exp.materialsource ON material.cpasType = materialsource.lsid
                    LEFT OUTER JOIN exp.data on End_ = data.objectId LEFT OUTER JOIN exp.dataclass ON data.cpasType = dataclass.lsid
                WHERE Depth_ > 0 AND materialsource.rowid IS NOT NULL OR dataclass.rowid IS NOT NULL) _inner_
            GROUP BY targetId, Start_
            """, MAX_LINEAGE_LOOKUP_DEPTH);


    /*
     * This can be used to add a column directly to a exp table, or to create a column
     * in an intermediate fake lookup table
     */
    static MutableColumnInfo createLineageLookupColumn(final ColumnInfo fkRowId, ExpSampleType source, ExpSampleType target)
    {
        TableInfo parentTable = fkRowId.getParentTable();
        var ret = new ExprColumn(parentTable, target.getLSID(), new SQLFragment("#ERROR#"), JdbcType.INTEGER)
        {
            @Override
            public SQLFragment getValueSql(String tableAlias)
            {
                SQLFragment objectId = fkRowId.getValueSql(tableAlias);
                return ClosureQueryHelper.getValueSql(source.getLSID(), objectId, target);
            }
        };
        ret.setLabel(target.getName());
        ret.setFk(new QueryForeignKey.Builder(parentTable.getUserSchema(), parentTable.getContainerFilter()).table(target.getName()));
        return ret;
    }


    public static SQLFragment getValueSql(String sourceLSID, SQLFragment objectId, ExpSampleType target)
    {
        return getValueSql(sourceLSID, objectId, "m" + target.getRowId());
    }


    public static SQLFragment getValueSql(String sourceLSID, SQLFragment objectId, ExpDataClass target)
    {
        return getValueSql(sourceLSID, objectId, "d" + target.getRowId());
    }


    private static SQLFragment getValueSql(String sourceLSID, SQLFragment objectId, String targetId)
    {
        MaterializedQueryHelper helper = getClosureHelper(sourceLSID);

        return new SQLFragment()
                .append("(SELECT rowId FROM ")
                .append(helper.getFromSql("CLOS", null))
                .append(" WHERE targetId='").append(targetId).append("'")
                .append(" AND Start_=").append(objectId)
                .append(")");
    }


/*
    public static void recomputeLineage(String sourceLSID, Collection<Pair<Integer,Integer>> rowid_objectids)
    {
        if (rowid_objectids.isEmpty())
            return;

        if (getScope().isTransactionActive())
        {
            // TODO/CONSIDER handle the tx case?
            getInvalidationCounter(sourceLSID).incrementAndGet();
            return;
        }

        // if there's nothing cached, we don't need to do incremental
        MaterializedQueryHelper helper = getClosureHelper(sourceLSID);
        if (!helper.isCached(null))
            return;

        TempTableTracker ttt = null;

        try
        {
            // COMPUTE closure for given rows, save into temp table
            StringBuilder from = new StringBuilder(" FROM (VALUES ");
            String comma = "";
            for (var p : rowid_objectids)
            {
                from.append(comma);
                from.append("(").append(p.first).append(",").append(p.second).append(")");
                comma = ",\n";
            }
            from.append(") AS _mat_ (RowId,ObjectId) ");
            String sql = pgMaterialClosureSql.replace("/*FROM*\/", from);
            Object ref = new Object();
            String tempTableName = "closure"+temptableNumber.incrementAndGet();
            ttt = TempTableTracker.track("closure"+temptableNumber.incrementAndGet(), ref);
            SQLFragment selectInto = new SQLFragment("SELECT * INTO temp.\"" + tempTableName + "\"\nFROM (\n")
                    .append(new SQLFragment(sql))
                    .append("\n) _sql_");
            new SqlExecutor(getScope()).execute(selectInto);

            SQLFragment upsert = new SQLFragment()
                    .append("INSERT INTO temp.${NAME} (Start_, rowId, targetid)\n").append("SELECT Start, RowId, targetId FROM temp.").append(tempTableName).append(" TMP\n")
                    .append("ON CONFLICT(Start_,targetId) UPDATE SET rowId = EXCLUDED.rowId");

            helper.upsert(upsert);
            return;
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
*/

    public static void invalidateAll()
    {
        synchronized (queryHelpers)
        {
            for (var h : queryHelpers.values())
                h.uncache(null);
        }
    }


    public static void invalidateCpasType(String lsid)
    {
        var counter = invalidators.get(lsid);
        if (null != counter)
            counter.incrementAndGet();
    }


    private static MaterializedQueryHelper getClosureHelper(String sourceLSID)
    {
        return queryHelpers.computeIfAbsent(sourceLSID, cpasType ->
                {
                String sql = pgMaterialClosureSql.replace("/*FROM*/", "FROM exp.Material WHERE Material.cpasType = ?");
                return new MaterializedQueryHelper.Builder("closure", DbSchema.getTemp().getScope(), new SQLFragment(sql, cpasType))
                        .addIndex("CREATE UNIQUE INDEX uq_${NAME} ON temp.${NAME} (targetId,Start_)")
                        .maxTimeToCache(TimeUnit.MINUTES.toMillis(5))
                        .addInvalidCheck(() -> String.valueOf(getInvalidationCounter(sourceLSID)))
                        .build();
                });
    }


    static final AtomicInteger temptableNumber = new AtomicInteger();

    private static AtomicInteger getInvalidationCounter(String sourceLSID)
    {
        return invalidators.computeIfAbsent(sourceLSID, (key) -> new AtomicInteger());
    }


    private static DbScope getScope()
    {
        return CoreSchema.getInstance().getScope();
    }
}
