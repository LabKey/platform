/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.DbScope;

import java.sql.ResultSet;
import java.sql.SQLException;

/*
* User: adam
* Date: Oct 2, 2009
* Time: 6:31:43 PM
*/

// Supports the SAS 9.1 SAS/SHARE JDBC driver
public class SqlDialectSas91 extends SqlDialectSas
{
    // SAS/SHARE driver throws when invoking DatabaseMetaData database version methods, so use the jdbcDriverVersion to determine dialect version
    protected boolean claimsProductNameAndVersion(String dataBaseProductName, int databaseMajorVersion, int databaseMinorVersion, String jdbcDriverVersion, boolean logWarnings)
    {
        return dataBaseProductName.equals(getProductName()) && jdbcDriverVersion.startsWith("9.1");
    }

    public ColumnMetaDataReader getColumnMetaDataReader(ResultSet rsCols, DbScope scope)
    {
        return new Sas91ColumnMetaDataReader(rsCols);
    }

    private class Sas91ColumnMetaDataReader extends ColumnMetaDataReader
    {
        private Sas91ColumnMetaDataReader(ResultSet rsCols)
        {
            super(rsCols);

            _nameKey = "NAME";
            _sqlTypeKey = "SQLTYPE";
            _scaleKey = "SIZE";
            _nullableKey = "NULLABLE";
            _postionKey = "POSITION";
            _descriptionKey = "COMMENT";
        }

        @Override
        public String getSqlTypeName() throws SQLException
        {
            return sqlTypeNameFromSqlTypeInt(getSqlType());
        }

        public boolean isAutoIncrement() throws SQLException
        {
            return false;
        }

        @Override
        public String getDatabaseFormat() throws SQLException
        {
            return StringUtils.trimToNull(_rsCols.getString("type"));
        }
    }

    public PkMetaDataReader getPkMetaDataReader(ResultSet rs)
    {
        return new PkMetaDataReader(rs, "NAME", "SEQ") {
            @Override
            public String getName() throws SQLException
            {
                return super.getName().trim();
            }
        };
    }    
}
