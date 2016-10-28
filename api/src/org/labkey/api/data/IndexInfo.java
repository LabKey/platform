/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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

import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.List;

/**
 * Represents an index that's part of a {@link TableInfo}.
 */
public class IndexInfo
{

    private static final Logger _log = Logger.getLogger(IndexInfo.class);


    private TableInfo.IndexType _type;
    private List<String> _columns;

    public IndexInfo(TableInfo.IndexType type, List<String> columns)
    {
        _type = type;
        _columns = Collections.unmodifiableList(columns);
    }


    public TableInfo.IndexType getType()
    {
        return _type;
    }

    public List<String> getColumns()
    {
        return _columns;
    }
}
