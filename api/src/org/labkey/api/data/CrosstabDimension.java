/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.ResultSetUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a dimension for a crosstab table info. A dimension maps to an
 * underlying ColumnInfo.
 *
 * User: Dave
 * Date: Jan 28, 2008
 * Time: 9:24:49 AM
 */
public class CrosstabDimension
{
    private FieldKey _fieldKey;
    private ColumnInfo _sourceColumn = null;
    private String _url = null;

    public CrosstabDimension(TableInfo table, FieldKey sourceKey)
    {
        this(QueryService.get().getColumns(table, Arrays.asList(sourceKey)).get(sourceKey), sourceKey);
    }

    public CrosstabDimension(ColumnInfo sourceColumn, FieldKey sourceKey)
    {
        _sourceColumn = sourceColumn;
        _fieldKey = sourceKey;
    }

    public ColumnInfo getSourceColumn()
    {
        return _sourceColumn;
    }

    public FieldKey getSourceFieldKey()
    {
        return _fieldKey;
    }

    public FieldKey getFieldKey()
    {
        return FieldKey.fromParts(_sourceColumn.getAlias());
    }

    public String getName()
    {
        return _sourceColumn.getAlias();
    }

    public void setSourceColumn(ColumnInfo sourceColumn)
    {
        _sourceColumn = sourceColumn;
    }

    // XXX: Change to DetailsURL
    public String getUrl()
    {
        return _url;
    }

    // XXX: Change to DetailsURL
    public void setUrl(String url)
    {
        _url = url;
    }

    public String getMemberUrl(CrosstabMember member)
    {
        return member.replaceTokens(_url);
    }

    public String getMemberUrl(DisplayColumn member)
    {
        return member.getCaption(null);
    }

    /**
     * Use this method to fetch the distinct set of members for this dimension.
     *
     * If you do not already have the set of column members for a CrosstabTableInfo,
     * use this method to fetch them. It uses the source TableInfo to select
     * the distinct set of values for the source ColumnInfo and build corresponding CrosstabMember objects.
     * You may optionally supply a caption ColumnInfo that contains captions
     * for the distinct members (e.g., Run name for RunId).
     * @param captionCol Optional caption column (may be null)
     * @return A list of CrosstabMembers suitable for use with the CrosstabTableInfo
     */
    /*
    public List<CrosstabMember> fetchMembers(ColumnInfo captionCol)
    {
        ArrayList<CrosstabMember> members = new ArrayList<CrosstabMember>();
        Table.TableResultSet rs = null;
        try
        {
            ColumnInfo sourceCol = getSourceColumn();
            if(null == captionCol)
                captionCol = sourceCol;

            SQLFragment sql = new SQLFragment("SELECT DISTINCT ");
            sql.append(sourceCol.getAlias());
            sql.append(" AS Value,");
            sql.append(captionCol.getAlias());
            sql.append(" AS Caption FROM (");
            sql.append(Table.getSelectSQL(sourceCol.getParentTable(), Arrays.asList(sourceCol, captionCol), null, null));

            sql.append(") as x ORDER BY ");
            sql.append(sourceCol.getAlias());

            rs = Table.executeQuery(sourceCol.getParentTable().getSchema(), sql);

            while(rs.next())
                members.add(new CrosstabMember(rs.getObject("Value"), this, rs.getString("Caption")));
        }
        catch(SQLException e)
        {
            throw new RuntimeException("Could not fetch the distinct members for the column dimension: " + e.toString());
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

        return members;
    } //fetchMembers()
    */
}
