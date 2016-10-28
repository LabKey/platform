/*
 * Copyright (c) 2011-2016 LabKey Corporation
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

package org.labkey.api.dataiterator;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.BatchValidationException;

import java.io.IOException;
import java.util.List;

/**
* User: matthewb
* Date: 2011-05-20
* Time: 2:16 PM
*/
class StringTestIterator extends AbstractDataIterator implements ScrollableDataIterator
{
    final List<String> columns;
    final List<String[]> data;
    boolean isScrollable = false;
    int row = -1;

    StringTestIterator(List<String> columns, List<String[]> data)
    {
        super(null);
        this.columns = columns;
        this.data = data;
    }

    @Override
    public int getColumnCount()
    {
        return columns.size();
    }

    @Override
    public ColumnInfo getColumnInfo(int i)
    {
        if (i==0)
            return new ColumnInfo("_rownumber", JdbcType.INTEGER);
        return new ColumnInfo(columns.get(i-1),JdbcType.VARCHAR);
    }

    @Override
    public boolean next() throws BatchValidationException
    {
        return ++row < data.size();
    }

    @Override
    public Object get(int i)
    {
        if (0==i)
            return row+1;
        return data.get(row)[i-1];
    }

    @Override
    public void close() throws IOException
    {
    }

    @Override
    public boolean isScrollable()
    {
        return isScrollable;
    }

    public void setScrollable(boolean s)
    {
        isScrollable = s;
    }

    @Override
    public void beforeFirst()
    {
        if (!isScrollable())
            throw new IllegalStateException();
        row = -1;
    }
}
