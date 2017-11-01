/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

package org.labkey.api.data.dialect;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Handles standard reading of primary key meta data from a JDBC result set, pulling the values from the configured columns */
public class PkMetaDataReader
{
    private final ResultSet _rsCols;
    private final String _nameKey, _seqKey;

    public PkMetaDataReader(ResultSet rsCols, String nameKey, String seqKey)
    {
        _rsCols = rsCols;
        _nameKey = nameKey;
        _seqKey = seqKey;
    }

    public String getName() throws SQLException
    {
        return _rsCols.getString(_nameKey);
    }

    public int getKeySeq() throws SQLException
    {
        return _rsCols.getInt(_seqKey);
    }
}
