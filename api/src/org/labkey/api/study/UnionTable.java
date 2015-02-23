package org.labkey.api.study;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.UserSchema;

import java.util.Collection;

/**
 * Created by labkeyuser on 2/22/15.
 */
public class UnionTable extends AbstractTableInfo
{
    final UserSchema _studyQuerySchema;     // really a StudyQuerySchema
    final SQLFragment _sqlInner;
    final TableInfo _componentTable;       // one of the unioned tables

    public UnionTable(UserSchema studyQuerySchema, String tableName, Collection<ColumnInfo> cols, SQLFragment sqlf,
                      TableInfo componentTable, String titleColumn)
    {
        this(studyQuerySchema, tableName, cols, sqlf, componentTable);
        _titleColumn = titleColumn;
    }

    public UnionTable(UserSchema studyQuerySchema, String tableName, Collection<ColumnInfo> cols, SQLFragment sqlf, TableInfo componentTable)
    {
        super(studyQuerySchema.getDbSchema(), tableName);
        _studyQuerySchema = studyQuerySchema;
        _componentTable = componentTable;
        for (ColumnInfo col : cols)
        {
            col.setParentTable(this);
            addColumn(col);
        }
        _sqlInner = sqlf;
    }

    @Override
    public boolean isPublic()
    {
        return false;
    }

    @Override
    protected SQLFragment getFromSQL()
    {
        return _sqlInner;
    }

    @Nullable
    @Override
    public UserSchema getUserSchema()
    {
        return _studyQuerySchema;
    }

    @Override
    public boolean needsContainerClauseAdded()
    {
        return false;
    }

    public TableInfo getComponentTable()
    {
        return _componentTable;
    }
}

