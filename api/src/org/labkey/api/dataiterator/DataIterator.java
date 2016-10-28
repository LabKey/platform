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
import org.labkey.api.query.BatchValidationException;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * User: matthewb
 * Date: May 16, 2011
 *
 *  Sticking with the jdbc style 1-based indexing
 *
 *  Column 0 is the row number, used for error reporting
 */
public interface DataIterator extends Closeable
{
    String getDebugName();

    /** count of columns, columns are indexed 1-_columnCount */
    int getColumnCount();

    /** description of column i */
    ColumnInfo getColumnInfo(int i);

    /** to enable optimizations, could consider adding to ColumnInfo  */
    boolean isConstant(int i);
    Object getConstantValue(int i);

    /**
     * Iterators should usually just add errors to a shared ValidationException,
     * however, they may throw to force processing to stop.
     * @return True if there are more items.
     */
    boolean next() throws BatchValidationException;

    /**
     * get the value for column i, the returned object may be one of
     *
     * a) null
     * b) real value (e.g. 5.0, or "name")
     * c) MvFieldWrapper
     *
     * MSInspectFeatursDataHandler uses error values as well, but that's what ValidationException is for
     */
    Object get(int i);

    default Supplier<Object> getSupplier(final int i)
    {
        return () -> get(i);
    }

    @Override
    void close() throws IOException;

    default long estimateSize()
    {
        return Long.MAX_VALUE;
    }

    default Stream<Map<String,Object>> stream()
    {
        return DataIteratorUtil.stream(this,false);
    }

    default void debugLogInfo(StringBuilder sb)
    {
        sb.append(this.getClass().getName()+"\n");
    }
}
