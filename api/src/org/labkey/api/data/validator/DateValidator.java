/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.api.data.validator;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.util.DateUtil;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

/**
 * Validate date is within min/max range on PostgreSQL.
 */
public class DateValidator extends AbstractColumnValidator
{
    // This is the SQLServer DATETIME range, Postgres supports a wide range, as do SQLServer DATETIME2 fields
    // https://msdn.microsoft.com/en-us/library/ms187819.aspx
    private static final long MIN_TIMESTAMP_SQLSERVER =  DateUtil.parseISODateTime("1753-01-01");
    private static final long MAX_TIMESTAMP_SQLSERVER = DateUtil.parseISODateTime("9999-12-31") + TimeUnit.DAYS.toMillis(1);

    private static final String ERRMSG_SQLSERVER = "Only dates between January 1, 1753 and December 31, 9999 are accepted.";
    private static final String ERRMSG_POSTGRESQL = "Only dates between January 1, 4713 BCE and January 8, 294247 are accepted.";

    private static long MIN_TIMESTAMP_POSTGRESQL;
    private static long MAX_TIMESTAMP_POSTGRESQL;
    static
    {
        try
        {
            MIN_TIMESTAMP_POSTGRESQL = new SimpleDateFormat("yyyy-MM-dd G").parse("4713-01-01 BC").getTime();  // 4713-01-01 BCE
            MAX_TIMESTAMP_POSTGRESQL = new SimpleDateFormat("yyyyyy-MM-dd").parse("294247-01-09").getTime();   // 294247-01-09
        }
        catch (ParseException e)
        {
            MIN_TIMESTAMP_POSTGRESQL = -210866774400000L;
            MAX_TIMESTAMP_POSTGRESQL = 922337964800000L;
        }
    }

    private final long _minDate;
    private final long _maxDate;
    private final String _errMsg;

    public DateValidator(String columnName)
    {
        this(columnName, MIN_TIMESTAMP_SQLSERVER, MAX_TIMESTAMP_SQLSERVER, ERRMSG_SQLSERVER);
    }

    public DateValidator(String columnName, @Nullable SqlDialect dialect)
    {
        this(columnName,
             null != dialect && dialect.isPostgreSQL() ? MIN_TIMESTAMP_POSTGRESQL : MIN_TIMESTAMP_SQLSERVER,
             null != dialect && dialect.isPostgreSQL() ? MAX_TIMESTAMP_POSTGRESQL : MAX_TIMESTAMP_SQLSERVER,
             null != dialect && dialect.isPostgreSQL() ? ERRMSG_POSTGRESQL : ERRMSG_SQLSERVER);
    }

    private DateValidator(String columnName, long minDate, long maxDate, String errMsg)
    {
        super(columnName);
        _minDate = minDate;
        _maxDate = maxDate;
        _errMsg = errMsg;
    }

    @Override
    public String _validate(int rowNum, Object value)
    {
        if (!(value instanceof java.util.Date))
            return null;
        long t = ((java.util.Date)value).getTime();
        if (t >= _minDate && t < _maxDate)
            return null;
        return _errMsg;
    }
}
