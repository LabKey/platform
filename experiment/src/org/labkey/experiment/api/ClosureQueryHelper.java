package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
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

public class ClosureQueryHelper
{
    /* TODO/CONSIDER every SampleType and Dataclass should have a unique ObjectId so it can be stored as an in lineage tables (e.g. edge/closure tables) */

    static Map<String, MaterializedQueryHelper> queryHelpers = Collections.synchronizedMap(new HashMap<>());

    static String pgMaterialClosureSql = """
            WITH RECURSIVE CTE_ AS (

                SELECT
                    RowId AS Start_,
                    ObjectId as End_,
                    '/' || CAST(ObjectId AS VARCHAR) || '/' as Path_,
                    0 as Depth_
                FROM exp.Material
                WHERE Material.cpasType = ?

                    UNION ALL

                SELECT CTE_.Start_, Edge.FromObjectId as End_, CTE_.Path_ || CAST(Edge.FromObjectId AS VARCHAR) || '/' as Path_, Depth_ + 1 as Depth_
                FROM CTE_ INNER JOIN exp.Edge ON CTE_.End_ = Edge.ToObjectId
                WHERE Depth_ < 100 AND 0 = POSITION('/' || CAST(Edge.FromObjectId AS VARCHAR) || '/' IN Path_)

            )

            SELECT Start_, CASE WHEN COUNT(*) = 1 THEN MIN(rowId) ELSE -1 * COUNT(*) END AS rowId, targetId
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

    /*
     * THIS IS A TERRIBLE INVALIDATION QUERY.  a) we don't care about deletes b) we don't even care about inserts/updates.  Only
     * lineage changes.  Probably best to use the in-memory invalidator counter strategy.
     */
    static String pgUpToDateMaterialSql = """
            SELECT CAST(MAX(rowId) AS VARCHAR) || '/' || CAST(MAX(modified) AS VARCHAR) FROM exp.material WHERE cpasType = ?
            """;


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
        /* TODO might need/want an in-memory invalidator flag/autoincrement per container/sampletype.  pgUpToDateSql is good enough for prototyping */
        MaterializedQueryHelper helper = queryHelpers.computeIfAbsent(sourceLSID, cpasType ->
                new MaterializedQueryHelper.Builder("closure", DbSchema.getTemp().getScope(), new SQLFragment(pgMaterialClosureSql, cpasType))
                        .addIndex("CREATE UNIQUE INDEX uq_${NAME} ON temp.${NAME} (targetId,Start_)")
                        .upToDateSql(new SQLFragment(pgUpToDateMaterialSql, cpasType))
                        .build());

        return new SQLFragment()
                .append("(SELECT rowId FROM ")
                .append(helper.getFromSql("CLOS", null))
                .append(" WHERE targetId='").append(targetId).append("'")
                .append(" AND Start_=").append(objectId)
                .append(")");
    }
}
