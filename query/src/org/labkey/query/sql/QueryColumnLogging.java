package org.labkey.query.sql;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnLogging;
import org.labkey.api.data.PHI;
import org.labkey.api.data.SelectQueryAuditProvider;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class is used to track columnLogging objects within a LabKey SQL query
 * <p>
 * This collects all the columnLogging object for all the columns involved in an expression when we are
 * trying to create a QueryTableInfo (for instance QuerySelect.getTableInfo()).
 * <p>
 * It gets turned in to a "real" ColumnLogging when remapFieldKeys() is called when we can fix up all the logging columns.
 * <p>
 * NOTE: This class inherts ColumnLogging primarily just so that the ColumnInfo can hang onto it.  remapFieldKey() is used to get
 * needs to be called so this is not used directly by outside callers.
 */
public class QueryColumnLogging extends ColumnLogging
{
    final Collection<AbstractQueryRelation.RelationColumn> columnsUsed;

    QueryColumnLogging(TableInfo parentTable, FieldKey column, Collection<AbstractQueryRelation.RelationColumn> columnsUsed,
                       boolean shouldLogName, String loggingComment, SelectQueryAuditProvider sqap)
    {
        super(parentTable.getSchema().getName(), parentTable.getName(), column,
//            makeUniqueKey(parentTable, column), null,
                shouldLogName, Set.of(), loggingComment, sqap);
        this.columnsUsed = columnsUsed;
    }

    static QueryColumnLogging create(TableInfo parentTable, FieldKey column, Collection<AbstractQueryRelation.RelationColumn> cols)
    {
        boolean shouldLogName = false;
        String loggingComment = null;
        SelectQueryAuditProvider sqap = null;
        for (var col : cols)
        {
            ColumnLogging logging = col.getColumnLogging();
            if (null != logging)
            {
                shouldLogName |= logging.shouldLogName();
                if (null == loggingComment)
                    loggingComment = logging.getLoggingComment();
                if (null == sqap)
                    sqap = logging.getSelectQueryAuditProvider();
            }
        }
        return new QueryColumnLogging(parentTable, column, cols, shouldLogName, loggingComment, sqap);
    }


    @Override
    public ColumnLogging remapFieldKeys(FieldKey baseFieldKey, Map<FieldKey, FieldKey> remap, Set<String> remapWarnings)
    {
        // see QueryTableInfo.remapFieldKeys().
        throw new UnsupportedOperationException();
    }


    @Override
    public Set<FieldKey> getDataLoggingColumns()
    {
        throw new UnsupportedOperationException();
    }


    public PHI getPHI()
    {
        PHI phi = PHI.NotPHI;
        for (var column : columnsUsed)
        {
            if (column.getPHI() != null)
                phi = PHI.max(phi, column.getPHI());
        }
        return phi;
    }


    ColumnLogging remapQueryFieldKeys(QueryTableInfo table, FieldKey column, Map<String,FieldKey> outerMap)
    {
//        Query query = table._relation._query;
//        assert null != query._mapQueryUniqueNamesToSelectAlias;
//        assert null != table.mapFieldKeyToSiblings;

        if (columnsUsed.isEmpty())
            return ColumnLogging.defaultLogging(table, column);

        Set<FieldKey> dataLoggingColumns = new HashSet<>();
        for (var used : columnsUsed)
        {
            ColumnLogging columnLogging = used.getColumnLogging();
            if (null == columnLogging)
                continue;
            if (null != columnLogging.getException())
                return columnLogging;

            QueryRelation r = used.getTable();
            assert r instanceof QueryRelation.ColumnResolvingRelation;

            Map<FieldKey, FieldKey> remap = ((QueryRelation.ColumnResolvingRelation)r).getRemapMap(outerMap);
            for (FieldKey fk : columnLogging.getDataLoggingColumns())
            {
                var mapped = FieldKey.remap(fk, null, remap);
                if (null == mapped)
                    return ColumnLogging.error(_shouldLogName, _selectQueryAuditProvider, "Unable to find required logging column " + fk.getName() + (null==_originalTableName ? "" : (" for table " + _originalTableName)));
                dataLoggingColumns.add(mapped);
            }
        }

//        String unique = makeUniqueKey(table, column.getFieldKey());
        return new ColumnLogging(
            table.getUserSchema().getSchemaName(), table.getName(), column,
//            unique, null,
            _shouldLogName, dataLoggingColumns, _loggingComment, _selectQueryAuditProvider);
    }
}
