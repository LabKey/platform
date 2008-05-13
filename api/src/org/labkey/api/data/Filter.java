/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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

import java.util.Set;
import java.util.Map;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: arauch
 * Date: Jan 11, 2005
 * Time: 8:01:17 AM
 */
public interface Filter
{
    public SQLFragment getSQLFragment(TableInfo tableInfo, List<ColumnInfo> colInfos);
    public SQLFragment getSQLFragment(SqlDialect dialect, Map<String, ? extends ColumnInfo> columnMap);
    public Set<String> getWhereParamNames();

    /**
     * @return the SQL fragment with the parameter values substituted in.  Suitable for using as a key in a cache,
     * or pasting into an external SQL tool.
     */
    public String toSQLString(SqlDialect dialect);

    /**
     * toString() on a Filter doesn't do the correct thing.  Should use "substituteParameters" instead.
     * @return
     */
    @Deprecated
    public String toString();
}