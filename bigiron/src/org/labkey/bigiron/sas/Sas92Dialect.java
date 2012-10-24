/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

package org.labkey.bigiron.sas;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.ColumnMetaDataReader;
import org.labkey.api.data.dialect.PkMetaDataReader;

import java.sql.ResultSet;
import java.sql.SQLException;

/*
* User: adam
* Date: Oct 2, 2009
* Time: 6:31:43 PM
*/

// Supports the SAS 9.2 SAS/SHARE JDBC driver
public class Sas92Dialect extends SasDialect
{
    @Override
    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, TableInfo table)
    {
        return new Sas92ColumnMetaDataReader(rsCols);
    }

    private class Sas92ColumnMetaDataReader extends ColumnMetaDataReader
    {
        private Sas92ColumnMetaDataReader(ResultSet rsCols)
        {
            super(rsCols);

            _nameKey = "COLUMN_NAME";
            _sqlTypeKey = "DATA_TYPE";
            _scaleKey = "COLUMN_SIZE";
            _nullableKey = "NULLABLE";
            _postionKey = "ORDINAL_POSITION";
        }

        @Override
        public String getSqlTypeName() throws SQLException
        {
            return sqlTypeNameFromSqlType(getSqlType());
        }

        @Override
        public boolean isAutoIncrement() throws SQLException
        {
            return false;
        }

        @Override
        public String getLabel() throws SQLException
        {
            // With SAS 9.2 driver, variable labels show up in "remarks" -- treat as label instead of description
            return StringUtils.trimToNull(_rsCols.getString("REMARKS"));
        }

        @Override
        public String getDescription() throws SQLException
        {
            return null;
        }
    }

    public PkMetaDataReader getPkMetaDataReader(ResultSet rs)
    {
        return new PkMetaDataReader(rs, "COLUMN_NAME", "KEY_SEQ") {
            @Override
            public String getName() throws SQLException
            {
                return super.getName().trim();
            }
        };
    }
}