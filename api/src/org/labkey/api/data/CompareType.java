/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.SimpleFilter.ColumnNameFormatter;
import org.labkey.api.data.SimpleFilter.FilterClause;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.Group;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.data.xml.queryCustomView.OperatorType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Comparison operators that can be used to create filters over columns.
 *
 * WARNING: Keep in sync and in order with all other client apis and docs:
 * - server: CompareType.java
 * - java: Filter.java
 * - js: Filter.js
 * - R: makeFilter.R, makeFilter.Rd
 * - SAS: labkeymakefilter.sas, labkey.org SAS docs
 * - Python & Perl don't have an filter operator enum
 * User: brittp
 * Date: Oct 10, 2006
 */
public abstract class CompareType
{
    public static final String ME_FILTER_PARAM_VALUE = "~me~";
    public static final String JSON_MARKER_START = "{json:";
    public static final String JSON_MARKER_END = "}";

    //
    // These operators require a data value
    //

    public static final CompareType EQUAL = new CompareType("Equals", "eq", "EQUAL", true, " = ?", OperatorType.EQ)
    {
        @Override
        public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new EqualsCompareClause(fieldKey, this, value);
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] filterValues)
        {
            if (value == null)
            {
                return filterValues[0] == null;
            }
            // First try with no type conversion
            if (value.equals(filterValues[0]))
            {
                return true;
            }
            return value.equals(convertParamValue(col, filterValues[0]));
        }
    };

    public static final CompareType DATE_EQUAL = new CompareType("(Date) Equals", "dateeq", "DATE_EQUAL", true, null, OperatorType.DATEEQ)
    {
        @Override
        public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            //Use the IsBlank
            if (value == null || "".equals(value))
                return ISBLANK.createFilterClause(fieldKey, value);

            return new DateEqCompareClause(fieldKey, value, toDatePart(asDate(value)));
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            FilterClause clause = createFilterClause(FieldKey.fromParts("unused"), paramVals.length > 0 ? paramVals[0] : null);
            return clause.meetsCriteria(col, value);
        }
    };

    public static final CompareType NEQ = new CompareType("Does Not Equal", "neq", "NOT_EQUAL", true, " <> ?", OperatorType.NEQ)
    {
        @Override
        public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new NotEqualsCompareClause(fieldKey, this, value);
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] filterValues)
        {
            return !CompareType.EQUAL.meetsCriteria(col, value, filterValues);
        }
    };

    public static final CompareType DATE_NOT_EQUAL = new CompareType("(Date) Does Not Equal", "dateneq", "DATE_NOT_EQUAL", true, null, OperatorType.DATENEQ)
    {
        @Override
        public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            //Use the non-blank
            if (value == null || "".equals(value))
                return NONBLANK.createFilterClause(fieldKey, value);

            return new DateNeqCompareClause(fieldKey, value, toDatePart(asDate(value)));
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            FilterClause clause = createFilterClause(FieldKey.fromParts("unused"), paramVals.length > 0 ? paramVals[0] : null);
            return clause.meetsCriteria(col, value);
        }
    };

    public static final CompareType NEQ_OR_NULL = new CompareType("Does Not Equal", "neqornull", "NOT_EQUAL_OR_MISSING", true, " <> ?", OperatorType.NEQORNULL)
    {
        @Override
        public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new NotEqualOrNullClause(fieldKey, value);
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] filterValues)
        {
            return value == null || !CompareType.EQUAL.meetsCriteria(col, value, filterValues);
        }
    };

    public static final CompareType GT = new CompareType("Is Greater Than", "gt", "GREATER_THAN", true, " > ?", OperatorType.GT)
    {
        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] filterValues)
        {
            if (!(value instanceof Comparable))
            {
                return false;
            }
            Object filterValue = CompareType.convertParamValue(col, filterValues[0]);
            if (!(filterValue instanceof Comparable))
            {
                return false;
            }
            return compareTo((Comparable)value, (Comparable)filterValue) > 0;
        }
    };

    public static final CompareType DATE_GT = new CompareType("(Date) Is Greater Than", "dategt", "DATE_GREATER_THAN", true, " >= ?", OperatorType.GTE) // GT --> >= roundup(date)
    {
        @Override
        public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new DateGtCompareClause(fieldKey, value, toDatePart(asDate(value)));
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            CompareClause clause = createFilterClause(FieldKey.fromParts("unused"), paramVals.length > 0 ? paramVals[0] : null);
            return clause.meetsCriteria(col, value);
        }
    };

    public static final CompareType LT = new CompareType("Is Less Than", "lt", "LESS_THAN", true, " < ?", OperatorType.LT)
    {
        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] filterValues)
        {
            if (!(value instanceof Comparable))
            {
                return false;
            }
            Object filterValue = CompareType.convertParamValue(col, filterValues[0]);
            if (!(filterValue instanceof Comparable))
            {
                return false;
            }

            return compareTo((Comparable)value, (Comparable)filterValue) < 0;
        }
    };

    public static final CompareType DATE_LT = new CompareType("(Date) Is Less Than", "datelt", "DATE_LESS_THAN", true, " < ?", OperatorType.LT)
    {
        @Override
        public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new DateLtCompareClause(fieldKey, value, toDatePart(asDate(value)));
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            CompareClause clause = createFilterClause(FieldKey.fromParts("unused"), paramVals.length > 0 ? paramVals[0] : null);
            return clause.meetsCriteria(col, value);
        }
    };

    public static final CompareType GTE = new CompareType("Is Greater Than or Equal To", "gte", "GREATER_THAN_OR_EQUAL", true, " >= ?", OperatorType.GTE)
    {
        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] filterValues)
        {
            if (!(value instanceof Comparable))
            {
                return false;
            }
            Object filterValue = CompareType.convertParamValue(col, filterValues[0]);
            if (!(filterValue instanceof Comparable))
            {
                return false;
            }
            return compareTo((Comparable)value, (Comparable)filterValue) >= 0;
        }
    };

    public static final CompareType DATE_GTE = new CompareType("(Date) Is Greater Than or Equal To", "dategte", "DATE_GREATER_THAN_OR_EQUAL", true, " >= ?", OperatorType.GTE)
    {
        @Override
        public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new DateGteCompareClause(fieldKey, value, toDatePart(asDate(value)));
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            CompareClause clause = createFilterClause(FieldKey.fromParts("unused"), paramVals.length > 0 ? paramVals[0] : null);
            return clause.meetsCriteria(col, value);
        }
    };

    public static final CompareType LTE = new CompareType("Is Less Than or Equal To", "lte", "LESS_THAN_OR_EQUAL", true, " <= ?", OperatorType.LTE)
    {
        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] filterValues)
        {
            if (!(value instanceof Comparable))
            {
                return false;
            }
            Object filterValue = CompareType.convertParamValue(col, filterValues[0]);
            if (!(filterValue instanceof Comparable))
            {
                return false;
            }
            return compareTo((Comparable)value, (Comparable)filterValue) <= 0;
        }
    };

    public static final CompareType DATE_LTE = new CompareType("(Date) Is Less Than or Equal To", "datelte", "DATE_LESS_THAN_OR_EQUAL", true, " < ?", OperatorType.LT)  // LTE --> < roundup(date)
    {
        @Override
        public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new DateLteCompareClause(fieldKey, value, toDatePart(asDate(value)));
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            CompareClause clause = createFilterClause(FieldKey.fromParts("unused"), paramVals.length > 0 ? paramVals[0] : null);
            return clause.meetsCriteria(col, value);
        }
    };

    public static final CompareType STARTS_WITH = new CompareType("Starts With", "startswith", "STARTS_WITH", true, null, OperatorType.STARTSWITH)
    {
        @Override
        public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new StartsWithClause(fieldKey, value);
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] filterValues)
        {
            return value != null && value.toString().startsWith((String)filterValues[0]);
        }
    };

    public static final CompareType DOES_NOT_START_WITH = new CompareType("Does Not Start With", "doesnotstartwith", "DOES_NOT_START_WITH", true, null, OperatorType.DOESNOTSTARTWITH)
    {
        @Override
        public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new DoesNotStartWithClause(fieldKey, value);
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] filterValues)
        {
            return value == null || !value.toString().startsWith((String)filterValues[0]);
        }
    };

    public static final CompareType CONTAINS = new CompareType("Contains", "contains", "CONTAINS", true, null, OperatorType.CONTAINS)
    {
        @Override
        public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new ContainsClause(fieldKey, value);
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] filterValues)
        {
            return value != null && value.toString().contains((String) filterValues[0]);
        }
    };

    public static final CompareType DOES_NOT_CONTAIN = new CompareType("Does Not Contain", "doesnotcontain", "DOES_NOT_CONTAIN", true, null, OperatorType.DOESNOTCONTAIN)
    {
        @Override
        public CompareClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new DoesNotContainClause(fieldKey, value);
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] filterValues)
        {
            return value == null || !value.toString().contains((String) filterValues[0]);
        }
    };

    public static final CompareType CONTAINS_ONE_OF = new CompareType("Contains One Of (example usage: a;b;c)", "containsoneof", "CONTAINS_ONE_OF", true, null, OperatorType.CONTAINSONEOF)
    {
        // Each compare type uses CompareClause by default
        @Override
        public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            if (value instanceof Collection)
            {
                return new SimpleFilter.ContainsOneOfClause(fieldKey, (Collection)value, false);
            }
            else
            {
                List<String> values = new ArrayList<>();
                if (value != null && !value.toString().trim().equals(""))
                {
                    values.addAll(parseParams(value, getValueSeparator()));
                }
                return new SimpleFilter.ContainsOneOfClause(fieldKey, values, true, false);
            }
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            FilterClause clause = createFilterClause(FieldKey.fromParts("unused"), Arrays.asList(paramVals));
            return clause.meetsCriteria(col, value);
        }

        @Override
        public String getValueSeparator()
        {
            return SimpleFilter.InClause.SEPARATOR;
        }
    };

    public static final CompareType CONTAINS_NONE_OF = new CompareType("Does Not Contain Any Of (example usage: a;b;c)", "containsnoneof", "CONTAINS_NONE_OF", true, null, OperatorType.CONTAINSNONEOF)
    {
        // Each compare type uses CompareClause by default
        @Override
        public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            if (value instanceof Collection)
            {
                return new SimpleFilter.ContainsOneOfClause(fieldKey, (Collection)value, false, true);
            }
            else
            {
                Set<String> values = parseParams(value, getValueSeparator());

                return new SimpleFilter.ContainsOneOfClause(fieldKey, values, false, true);
            }
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            FilterClause clause = createFilterClause(FieldKey.fromParts("unused"), Arrays.asList(paramVals));
            return !clause.meetsCriteria(col, value);
        }

        @Override
        public String getValueSeparator()
        {
            return SimpleFilter.InClause.SEPARATOR;
        }
    };

    public static final CompareType IN = new CompareType("Equals One Of (example usage: a;b;c)", "in", "IN", true, null, OperatorType.IN)
    {
        // Each compare type uses CompareClause by default
        @Override
        public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            if (value instanceof Collection)
            {
                return new SimpleFilter.InClause(fieldKey, (Collection<?>)value, false);
            }
            else
            {
                List<String> values = new ArrayList<>();
                if (value != null)
                {
                    if (value.toString().trim().equals(""))
                    {
                        values.add(null);
                    }
                    else
                    {
                        values.addAll(parseParams(value, getValueSeparator()));
                    }
                }
                return new SimpleFilter.InClause(fieldKey, values, true);
            }
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            FilterClause clause = new SimpleFilter.InClause(FieldKey.fromParts("unused"), Arrays.asList(paramVals));
            return clause.meetsCriteria(col, value);
        }

        @Override
        public String getValueSeparator()
        {
            return SimpleFilter.InClause.SEPARATOR;
        }
    };

    public static final CompareType NOT_IN = new CompareType("Does Not Equal Any Of (example usage: a;b;c)", "notin", "NOT_IN", true, null, OperatorType.NOTIN)
    {
        // Each compare type uses CompareClause by default
        @Override
        public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            if (value instanceof Collection)
            {
                return new SimpleFilter.InClause(fieldKey, (Collection)value, false, true);
            }
            else
            {
                List<String> values = new ArrayList<>();
                if (value != null)
                {
                    if (value.toString().trim().equals(""))
                    {
                        values.add(null);
                    }
                    else
                    {
                        values.addAll(parseParams(value, getValueSeparator()));
                    }
                }
                return new SimpleFilter.InClause(fieldKey, values, true, true);
            }
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            FilterClause clause = new SimpleFilter.InClause(FieldKey.fromParts("unused"), Arrays.asList(paramVals));
            return !clause.meetsCriteria(col, value);
        }

        @Override
        public String getValueSeparator()
        {
            return SimpleFilter.InClause.SEPARATOR;
        }
    };

    public static final CompareType IN_NS = new CompareType("Equals One Of A Member Of A Named Set", "inns", "IN", true, null, OperatorType.IN)
    {
        // Each compare type uses CompareClause by default
        @Override
        public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            String namedSet = null;
            if (value != null && StringUtils.isNotBlank(value.toString()))
                namedSet = value.toString();
            return new SimpleFilter.InClause(fieldKey, namedSet, true);
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            throw new UnsupportedOperationException("Should be handled inside of " + SimpleFilter.InClause.class);
        }
    };

    public static final CompareType NOT_IN_NS = new CompareType("Does Not Equal Any Members Of A Named Set", "notinns", "NOT IN", true, null, OperatorType.NOTIN)
    {
        // Each compare type uses CompareClause by default
        @Override
        public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            String namedSet = null;
            if (value != null && StringUtils.isNotBlank(value.toString()))
                namedSet = value.toString();
            return new SimpleFilter.InClause(fieldKey, namedSet, true);
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            throw new UnsupportedOperationException("Should be handled inside of " + SimpleFilter.InClause.class);
        }
    };

    public static final CompareType BETWEEN = new CompareType("Between", "between", "BETWEEN", true, " BETWEEN ? AND ?", OperatorType.BETWEEN)
    {
        @Override
        public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            if (value instanceof Collection)
            {
                Object[] values = ((Collection)value).toArray();
                if (values.length != 2)
                    return new BetweenClause(fieldKey, values[0], values[0], false);

                return new BetweenClause(fieldKey, values[0], values[1], false);
            }
            else
            {
                String s = Objects.toString(value, "");
                String[] values = s.split(getValueSeparator());
                if (values.length != 2)
                    return new BetweenClause(fieldKey, values[0], values[0], false);

                return new BetweenClause(fieldKey, values[0], values[1], false);
            }
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            FieldKey fieldKey = FieldKey.fromParts("unused");
            FilterClause clause;
            if (col.isDateTimeType())
            {
                clause = new SimpleFilter.AndClause(
                        DATE_GTE.createFilterClause(fieldKey, asDate(paramVals[0])),
                        DATE_LTE.createFilterClause(fieldKey, asDate(paramVals[1])));
            }
            else
            {
                clause = new SimpleFilter.AndClause(
                        GTE.createFilterClause(fieldKey, paramVals[0]),
                        LTE.createFilterClause(fieldKey, paramVals[1]));
            }
            return clause.meetsCriteria(col, value);
        }

        @Override
        public String getValueSeparator()
        {
            return BetweenClause.SEPARATOR;
        }
    };

    public static final CompareType NOT_BETWEEN = new CompareType("Not Between", "notbetween", "NOT_BETWEEN", true, " NOT BETWEEN ? AND ?", OperatorType.NOTBETWEEN)
    {
        @Override
        public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            if (value instanceof Collection)
            {
                Object[] values = ((Collection)value).toArray();
                if (values.length != 2)
                    return new BetweenClause(fieldKey, values[0], values[0], true);

                return new BetweenClause(fieldKey, values[0], values[1], true);
            }
            else
            {
                String s = Objects.toString(value, "");
                String[] values = s.split(getValueSeparator());
                if (values.length != 2)
                    return new BetweenClause(fieldKey, values[0], values[0], true);

                return new BetweenClause(fieldKey, values[0], values[1], true);
            }
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            FieldKey fieldKey = FieldKey.fromParts("unused");
            FilterClause clause;
            if (col.isDateTimeType())
            {
                clause = new SimpleFilter.OrClause(
                        DATE_LT.createFilterClause(fieldKey, asDate(paramVals[0])),
                        DATE_GT.createFilterClause(fieldKey, asDate(paramVals[1])));
            }
            else
            {
                clause = new SimpleFilter.OrClause(
                        LT.createFilterClause(fieldKey, paramVals[0]),
                        GT.createFilterClause(fieldKey, paramVals[1]));
            }
            return clause.meetsCriteria(col, value);
        }

        @Override
        public String getValueSeparator()
        {
            return BetweenClause.SEPARATOR;
        }
    };

    public static final CompareType MEMBER_OF = new CompareType("Is Member Of", "memberof", "MEMBER_OF", true, " is member of", OperatorType.MEMBEROF)
    {
        @Override
        public MemberOfClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new MemberOfClause(fieldKey, value);
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            throw new UnsupportedOperationException("Conditional formatting not yet supported for MEMBER_OF");
        }
    };

    //
    // These are the "no data value" operators
    //

    public static final CompareType ISBLANK = new CompareType("Is Blank", "isblank", "MISSING", false, " IS NULL", OperatorType.ISBLANK)
    {
        @Override
        public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return super.createFilterClause(fieldKey, null);
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] filterValues)
        {
            return value == null;
        }

        // For display purposes, we want this to say blank not null
        @Override
        public String getFilterValueText()
        {
            return " " + getDisplayValue().toLowerCase();
        }
    };

    public static final CompareType NONBLANK = new CompareType("Is Not Blank", "isnonblank", "NOT_MISSING", false, " IS NOT NULL", OperatorType.ISNONBLANK)
    {
        @Override
        public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return super.createFilterClause(fieldKey, null);
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] filterValues)
        {
            return value != null;
        }

        // For display purposes, we want this to say blank not null
        @Override
        public String getFilterValueText()
        {
            return " " + getDisplayValue().toLowerCase();
        }
    };

    public static final CompareType MV_INDICATOR = new CompareType("Has An MV Indicator", new String[] { "hasmvvalue", "hasqcvalue" }, false, " has a missing value indicator", "MV_INDICATOR", OperatorType.HASMVVALUE)
    {
        @Override
        public MvClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new MvClause(fieldKey, false);
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            throw new UnsupportedOperationException("Conditional formatting not yet supported for MV indicators");
        }
    };

    public static final CompareType NO_MV_INDICATOR = new CompareType("Does Not Have An MV Indicator", new String[] { "nomvvalue", "noqcvalue" }, false, " does not have a missing value indicator", "NO_MV_INDICATOR", OperatorType.NOMVVALUE)
    {
        @Override
        public MvClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new MvClause(fieldKey, true);
        }

        @Override
        public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
        {
            throw new UnsupportedOperationException("Conditional formatting not yet supported for MV indicators");
        }
    };


    //
    // Table/Query-wise operators
    //

    public static final CompareType Q = new CompareType("Search", "q", "Q", true /* dataValueRequired */, "sql", OperatorType.Q)
    {
        @Override
        public QClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
        {
            return new QClause((String) value);
        }
    };

    private static class QClause extends CompareType.CompareClause
    {
        private List<ColumnInfo> _queryColumns = null;

        QClause(String value)
        {
            super(new FieldKey(null, "*"), Q, value);
            _displayFilterText = true;
        }

        @Override
        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            sb.append("Search for \"");
            sb.append(getParamVals()[0]);
            sb.append("\"");
        }

        @Override
        public List<FieldKey> getFieldKeys()
        {
            return this.getQueryColumns(null).stream().map(ColumnInfo::getFieldKey).collect(Collectors.toList());
        }

        private List<ColumnInfo> getQueryColumns(@Nullable Integer paramNum)
        {
            if (_selectColumns == null)
                return Collections.emptyList();

            if (_queryColumns == null)
            {
                _queryColumns = new ArrayList<>();
                for (ColumnInfo column : _selectColumns)
                {
                    if (column == null)
                        continue;

                    // If the search term parsed as a number, include a test for the primary key column
                    if (paramNum != null && column.isKeyField() && column.getJdbcType().isNumeric())
                    {
                        _queryColumns.add(column);
                    }

                    // skip uninteresting things like 'LSID' and 'SourceProtocolApplication'
                    if (column.isHidden())
                        continue;

                    // skip uninteresting things like 'Folder' and 'CreatedBy'
                    ForeignKey fk = column.getFk();
                    if (fk instanceof UserIdQueryForeignKey || fk instanceof UserIdForeignKey || fk instanceof ContainerForeignKey)
                        continue;

                    ColumnInfo targetColumn = column.getDisplayField();
                    if (targetColumn == null)
                        targetColumn = column;

                    // skip more uninteresting columns
                    if (!targetColumn.isStringType() ||
                            targetColumn.getName().equalsIgnoreCase("lsid") ||
                            targetColumn.getSqlTypeName().equalsIgnoreCase("lsidtype") ||
                            targetColumn.getSqlTypeName().equalsIgnoreCase("entityid"))
                        continue;

                    _queryColumns.add(targetColumn);
                }
            }
            return _queryColumns;
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            final String param = Objects.toString(getParamVals()[0], null) ;
            if (_selectColumns == null || _selectColumns.isEmpty() || null == param)
                return new SQLFragment("1=1");

            Integer paramNum = null;
            try
            {
                paramNum = Integer.parseInt(param);
            }
            catch (NumberFormatException ex)
            {
                // ok
            }

            boolean hasResult = false;
            SQLFragment sql = new SQLFragment();
            String sep = "";

            for (ColumnInfo column : this.getQueryColumns(paramNum))
            {
                if (column == null)
                    continue;

                // If the search term parsed as a number, include a test for the primary key column
                if (paramNum != null && column.isKeyField() && column.getJdbcType().isNumeric())
                {
                    hasResult = true;
                    sql.append(sep);
                    sep = " OR ";
                    sql.append(dialect.getColumnSelectName(column.getAlias()));
                    sql.append(" = ");
                    sql.append(paramNum);
                    continue;
                }

                ColumnInfo mappedColumn = columnMap.get(column.getFieldKey());
                if (mappedColumn == null)
                    continue;

                hasResult = true;
                sql.append(sep);
                sep = " OR ";

                sql.append(dialect.getColumnSelectName(mappedColumn.getAlias()));
                sql.append(" ").append(dialect.getCaseInsensitiveLikeOperator()).append(" ");
                sql.append(dialect.concatenate(" '%'", "?", "'%' ")).add(LikeClause.escapeLikePattern(param));
                sql.append(LikeClause.sqlEscape());
            }

            return hasResult ? sql : new SQLFragment("1=1");
        }
    }


    private final String _preferredURLKey;
    private final OperatorType.Enum _xmlType;
    private final Set<String> _urlKeys = new CaseInsensitiveHashSet();
    private final String _displayValue;
    private final boolean _dataValueRequired;
    private final String _sql;
    private final String _scriptName;

    private String _valueSeparator;

    protected CompareType(String displayValue, String[] urlKeys, boolean dataValueRequired, String sql, String scriptName, OperatorType.Enum xmlType)
    {
        this(displayValue, urlKeys[0], scriptName, dataValueRequired, sql, xmlType);
        _urlKeys.addAll(Arrays.asList(urlKeys));
    }

    protected CompareType(String displayValue, String urlKey, String scriptName, boolean dataValueRequired, String sql, OperatorType.Enum xmlType)
    {
        _displayValue = displayValue;
        _preferredURLKey = urlKey;
        _scriptName = scriptName;
        _dataValueRequired = dataValueRequired;

        _xmlType = xmlType;
        _urlKeys.add(urlKey);
        _sql = sql;
    }

    public static Collection<CompareType> values()
    {
        return QueryService.get().getCompareTypes();
    }

    protected static Set<String> parseParams(Object value_, String separator)
    {
        if (value_ == null)
            return Collections.emptySet();
        String value = value_.toString();
        if (StringUtils.isBlank(value))
            return Collections.emptySet();
        // check for JSON marker
        Set<String> values = new LinkedHashSet<>();
        if (value.startsWith(JSON_MARKER_START) && value.endsWith(JSON_MARKER_END))
        {
            value = value.substring(JSON_MARKER_START.length(), value.length()-JSON_MARKER_END.length());
            if (value.startsWith("[") && value.endsWith("]"))
            {
                try
                {
                    // TODO what do we do with malformed parameters???
                    JSONArray array = new JSONArray(value);
                    for (int i = 0; i < array.length(); i++)
                    {
                        values.add(Objects.toString(array.get(i), null));
                    }
                }
                catch (JSONException ex)
                {
                    // pass
                }
            }
            else
            {
                // Unsupported
                // pass
            }
        }
        else
        {
            String[] st = value.split("\\s*" + separator + "\\s*", -1);
            Collections.addAll(values, st);
        }
        return values;
    }

    @Nullable
    public static CompareType getByURLKey(String urlKey)
    {
        for (CompareType type : values())
        {
            if (type.getUrlKeys().contains(urlKey))
                return type;
        }
        return null;
    }

    // NOTE: By checking both the script name and the preferred URL key, we are backwards compatible with the original enum name.
    public static CompareType valueOf(String name)
    {
        Collection<CompareType> types = values();
        for (CompareType ct : types)
        {
            if (ct.name().equalsIgnoreCase(name))
                return ct;
        }

        // For backwards compatibility with Enum.valueOf(), check the url key
        String smooshed = name.replaceAll("_", "").toLowerCase();
        for (CompareType ct : types)
        {
            if (smooshed.equals(ct.getPreferredUrlKey()))
                return ct;
        }

        return null;
    }

    @Override
    public String toString()
    {
        return _scriptName; // not exactly the same as original enum name, but close enough
    }

    public String name()
    {
        return _scriptName; // not exactly the same as original enum name, but close enough
    }

    public String getDisplayValue()
    {
        return _displayValue;
    }

    public OperatorType.Enum getXmlType()
    {
        return _xmlType;
    }

    public String getPreferredUrlKey()
    {
        return _preferredURLKey;
    }

    public Set<String> getUrlKeys()
    {
        return _urlKeys;
    }

    public boolean isDataValueRequired()
    {
        return _dataValueRequired;
    }

    public String getSql()
    {
        return _sql;
    }

    public String getFilterValueText()
    {
        return _sql;
    }

    public String getScriptName()
    {
        return _scriptName;
    }

    public String getValueSeparator()
    {
        return _valueSeparator;
    }

    public boolean meetsCriteria(ColumnRenderProperties col, Object value, Object[] paramVals)
    {
        // if not implemented, but be implemented by the FilterClause
        throw new UnsupportedOperationException();
    }

    // Each compare type uses CompareClause by default
    public FilterClause createFilterClause(@NotNull FieldKey fieldKey, Object value)
    {
        return new CompareClause(fieldKey, this, value);
    }

    public abstract static class AbstractCompareClause extends FilterClause
    {
        @NotNull
        final FieldKey _fieldKey;

        public AbstractCompareClause(@NotNull FieldKey fieldKey)
        {
            _fieldKey = fieldKey;
        }

        @NotNull
        public FieldKey getFieldKey()
        {
            return _fieldKey;
        }

        @Override
        public List<FieldKey> getFieldKeys()
        {
            return Arrays.asList(getFieldKey());
        }

        public abstract CompareType getCompareType();

        /** @return null if there is no value for this filter (such as an IS BLANK clause), or the non-URL-encoded value
         *  that should be added to the URL that's built up
         */
        @Nullable
        protected abstract String toURLParamValue();

        @Override
        public Map.Entry<String, String> toURLParam(String dataRegionPrefix)
        {
            String key = dataRegionPrefix + _fieldKey.toString() + SimpleFilter.SEPARATOR_CHAR + getCompareType().getPreferredUrlKey();
            return new Pair<>(key, toURLParamValue());
        }
    }

    public static class CompareClause extends AbstractCompareClause
    {
        final CompareType _comparison;

        public CompareClause(@NotNull FieldKey fieldKey, CompareType comparison, Object value)
        {
            super(fieldKey);

            _comparison = comparison;

            if (null == value)
                _paramVals = null;
            else
                _paramVals = new Object[]{value};
        }

        String toWhereClause(SqlDialect dialect, String alias)
        {
            return dialect.getColumnSelectName(alias) + _comparison.getSql();
        }

        protected String substituteLabKeySqlParams(String sql, Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            JdbcType type = getColumnType(columnMap);
            if (type == null)
                throw new IllegalArgumentException("Column " + _fieldKey.toDisplayString() + " not found in column map.");
            Object[] params = getParamVals();
            if (params == null || params.length == 0)
                return sql;
            String[] parts = sql.split("\\?");
            StringBuilder substituted = new StringBuilder(parts[0]);
            int i;
            for (i = 0; i < params.length; i++)
            {
                substituted.append(escapeLabKeySqlValue(params[i], type));
                if (parts.length > i + 1)
                    substituted.append(parts[i + 1]);
            }
            return substituted.toString();
        }

        protected JdbcType getColumnType(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            return getColumnType(columnMap, null);
        }

        protected JdbcType getColumnType(Map<FieldKey, ? extends ColumnInfo> columnMap, JdbcType defaultType)
        {
            ColumnInfo col = null==columnMap ? null : columnMap.get(_fieldKey);
            return col != null ? col.getJdbcType() : defaultType;
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            String comparisonSql = _comparison.getSql();
            if (comparisonSql == null)
                throw new IllegalStateException("This compare type must override getLabKeySQLWhereClause.");
            String sql = getLabKeySQLColName(_fieldKey) + _comparison.getSql();
            return substituteLabKeySqlParams(sql, columnMap);
        }

        @Override
        protected int appendFilterValueText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            int result = sb.length();
            sb.append(_comparison.getFilterValueText());
            return result;
        }

        protected boolean isNull(Object value)
        {
            if (value == null)
            {
                return true;
            }
            if (value instanceof Parameter.TypedValue)
            {
                value = ((Parameter.TypedValue)value).getJdbcParameterValue();
            }
            return value == null || (value instanceof String && ((String)value).length() == 0);
        }

        protected void appendColumnName(StringBuilder sb, ColumnNameFormatter formatter)
        {
            sb.append(formatter.format(_fieldKey));
        }

        @Override
        public CompareType getCompareType()
        {
            return _comparison;
        }

        @Override
        protected String toURLParamValue()
        {
            if (getParamVals() != null && getParamVals()[0] != null)
            {
                return getParamVals()[0].toString();
            }
            return null;
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            ColumnInfo colInfo = columnMap != null ? columnMap.get(_fieldKey) : null;
            String alias = colInfo != null ? colInfo.getAlias() : _fieldKey.getName();

            SQLFragment fragment = new SQLFragment(toWhereClause(dialect, alias));
            if (colInfo == null || !needsTypeConversion() || getParamVals() == null)
            {
                fragment.addAll(getParamVals());
            }
            else
            {
                for (Object paramVal : getParamVals())
                {
                    fragment.add(convertParamValue(colInfo, paramVal));
                }
            }
            return fragment;
        }

        @Override
        protected boolean meetsCriteria(ColumnRenderProperties col, Object value)
        {
            return getCompareType().meetsCriteria(col, value, getParamVals());
        }
    }

    // Return the non-URL-encoded filter value for filter types that support multiple parameter values
    @NotNull
    static String toCollectionURLParamValue(final Collection<?> paramVals, final String multiValueSep, final boolean includeNull)
    {
        boolean containsSeparator = paramVals.stream().filter(Objects::nonNull).map(Objects::toString).anyMatch(s -> s.contains(multiValueSep));
        if (containsSeparator)
        {
            JSONArray json = new JSONArray(paramVals);
            if (includeNull)
                json.put((Object)null);

            return JSON_MARKER_START + json + JSON_MARKER_END;
        }
        else
        {
            StringBuilder sb = new StringBuilder();
            String separator = "";
            for (Object value : paramVals)
            {
                sb.append(separator);
                separator = multiValueSep;
                sb.append(value == null ? "" : value.toString());
            }
            if (includeNull)
            {
                sb.append(separator);
            }
            return sb.toString();
        }
    }

    // Issue 39395: ClassCastException when rendering conditional formats in issue reports
    // Widen numeric types before calling compareTo to avoid ClassCastException comparing Long to Integer
    private static int compareTo(@NotNull Comparable a, @NotNull Comparable b)
    {
        if (a.getClass() == b.getClass())
            return a.compareTo(b);

        if (a instanceof Number && b instanceof Number)
        {
            // widen and compare both numbers as doubles
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }

        return a.compareTo(b);
    }

    // Converts parameter value to the proper type based on the SQL type of the ColumnInfo
    public static Object convertParamValue(ColumnRenderProperties colInfo, Object paramVal)
    {
        if (colInfo == null)
        {
            // No way to know what to convert it into
            return paramVal;
        }
        if (!(paramVal instanceof String))
            return paramVal;

        String stringValue = (String)paramVal;

        // Expand the magic 'me' value if the column is a userid or a user display name
        if (ME_FILTER_PARAM_VALUE.equals(stringValue))
        {
            // get the current user from the query env
            User user = (User) QueryService.get().getEnvironment(QueryService.Environment.USER);

            if (isUserIdColumn(colInfo))
            {
                if (user != null)
                    return user.getUserId();

                return User.guest.getUserId();
            }

            if (isUserDisplayColumn(colInfo))
            {
                if (user != null)
                    return user.getDisplayName(user);

                return null;
            }
        }

        return getParamValue(colInfo, stringValue);
    }

    // Returns true if the column is an integer user id column or is a lookup to core.Users
    private static boolean isUserIdColumn(ColumnRenderProperties col)
    {
        if (col.getJdbcType() != JdbcType.INTEGER)
            return false;

        ForeignKey fk = null;
        String lookupSchemaName = null;
        String lookupQueryName = null;
        if (col instanceof ColumnInfo)
        {
            ColumnInfo colInfo = (ColumnInfo)col;
            String sqlTypeName = colInfo.getSqlTypeName();
            if ("userid".equalsIgnoreCase(sqlTypeName))
                return true;

            fk = colInfo.getFk();
            if (fk instanceof UserIdForeignKey || fk instanceof UserIdQueryForeignKey)
                return true;

            if (fk != null)
            {
                lookupSchemaName = fk.getLookupSchemaName();
                lookupQueryName = fk.getLookupTableName();
            }
        }
        else if (col instanceof PropertyDescriptor)
        {
            lookupSchemaName = ((PropertyDescriptor)col).getLookupSchema();
            lookupQueryName = ((PropertyDescriptor)col).getLookupQuery();
        }

        return "core".equalsIgnoreCase(lookupSchemaName) &&
                ("users".equalsIgnoreCase(lookupQueryName) || "usersdata".equalsIgnoreCase(lookupQueryName));
    }

    // TODO: How can I tell if this column is the core.Users DisplayName display column?
    // Returns true if the column is a varchar user display value column
    private static boolean isUserDisplayColumn(ColumnRenderProperties col)
    {
        if (col.getJdbcType() != JdbcType.VARCHAR)
            return false;

        if (col instanceof LookupColumn || (col instanceof AliasedColumn && ((AliasedColumn)col).getColumn() instanceof LookupColumn))
        {
            String propertyURI = col.getPropertyURI();
            return propertyURI != null && (propertyURI.endsWith("core#UsersData.DisplayName") || propertyURI.endsWith("core#Users.DisplayName"));
        }

        return false;
    }

    private static Object getParamValue(ColumnRenderProperties colInfo, String stringValue)
    {
        JdbcType type = colInfo.getJdbcType();
        switch (type)
        {
            case INTEGER, TINYINT, SMALLINT -> {
                // Treat the empty string as null
                stringValue = StringUtils.trimToNull(stringValue);
                if (stringValue == null)
                {
                    return new Parameter.TypedValue(null, JdbcType.INTEGER);
                }
                try
                {
                    return Integer.valueOf(stringValue);
                }
                catch (NumberFormatException e)
                {
                    throwConversionException(stringValue, colInfo, Integer.class);
                }
            }
            case BIGINT -> {
                try
                {
                    return Long.valueOf(stringValue);
                }
                catch (NumberFormatException e)
                {
                    throwConversionException(stringValue, colInfo, Long.class);
                }
            }
            case BOOLEAN -> {
                try
                {
                    // Treat the empty string as null
                    stringValue = StringUtils.trimToNull(stringValue);
                    if (stringValue == null)
                    {
                        return new Parameter.TypedValue(null, JdbcType.BOOLEAN);
                    }
                    return ConvertUtils.convert(stringValue, Boolean.class);
                }
                catch (Exception e)
                {
                    throwConversionException(stringValue, colInfo, Boolean.class);
                }
            }
            case TIMESTAMP, DATE, TIME -> {
                try
                {
                    return ConvertUtils.convert(stringValue, Date.class);
                }
                catch (ConversionException e)
                {
                    throwConversionException(stringValue, colInfo, Date.class);
                }
            }
            //FALL THROUGH! (Decimal is better than nothing)
            case DECIMAL, REAL, DOUBLE -> {
                try
                {
                    // Treat the empty string as null
                    stringValue = StringUtils.trimToNull(stringValue);
                    if (stringValue == null)
                    {
                        return new Parameter.TypedValue(null, type == JdbcType.REAL ? JdbcType.REAL : JdbcType.DOUBLE);
                    }
                    return type == JdbcType.REAL ? Float.valueOf(stringValue) : Double.valueOf(stringValue);
                }
                catch (NumberFormatException e)
                {
                    throwConversionException(stringValue, colInfo, Number.class);
                }
            }
        }
        return stringValue;
    }

    private static void throwConversionException(String value, ColumnRenderProperties column, Class<?> expectedClass)
    {
        throw new RuntimeSQLException(new SQLGenerationException(ConvertHelper.getStandardConversionErrorMessage(value, column.getName(), expectedClass)));
    }

    public static Date asDate(Object v)
    {
        try
        {
            if (v instanceof Date d)
                return d;
            if (v instanceof Calendar cal)
                return cal.getTime();
            String s = v.toString();

            // Support Long notation for dates
            if (s.endsWith("L") || s.endsWith("l"))
            {
                String couldBeLong = s.substring(0, s.length() - 1);

                // Only if rest of the value is numeric is it considered a valid Long
                if (NumberUtils.isDigits(couldBeLong))
                    return new Date(Long.parseLong(couldBeLong));
            }

            if (!s.startsWith("-") && !s.startsWith("+"))
                return new Date(DateUtil.parseDateTime(s));

            boolean add = s.startsWith("+");
            s = s.substring(1);

            // Default to number representing number of "days"
            if (NumberUtils.isDigits(s))
                s = s + "d";

            if (add)
                return new Date(DateUtil.addDuration(System.currentTimeMillis(), s));
            else
                return new Date(DateUtil.subtractDuration(System.currentTimeMillis(), s));
        }
        catch (NumberFormatException nfe)
        {
            throw new ConversionException(nfe);
        }
    }


    public static Calendar toDatePart(Date d)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(d);
        cal.clear(Calendar.SECOND);
        cal.clear(Calendar.MILLISECOND);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.clear(Calendar.MINUTE);
        return cal;
    }


    public static Calendar addOneDay(Calendar d)
    {
        assert d.get(Calendar.MILLISECOND) == 0;
        assert d.get(Calendar.SECOND) == 0;
        assert d.get(Calendar.MINUTE) == 0;
        assert d.get(Calendar.HOUR_OF_DAY) == 0;

        Calendar cal = (Calendar)d.clone();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        return cal;
    }


    /**
     * Compare clause for date operators
     *
     * Note that for most date operators the logical operation does not match the
     * actual sql operation   EQ --> BETWEEN, LTE --> LT, etc.
     */
    private abstract static class DateCompareClause extends CompareClause
    {
        private final String _filterTextDate;
        private final String _filterTextOperator;

        DateCompareClause(FieldKey fieldKey, CompareType t, String op, Object rawFilterValue, Calendar param0)
        {
            super(fieldKey, t, param0.getTime());
            if (null == rawFilterValue)
                rawFilterValue = "";
            if (rawFilterValue instanceof Calendar)
                rawFilterValue = ((Calendar)rawFilterValue).getTime();
            _filterTextDate = rawFilterValue instanceof Date ? ConvertUtils.convert(rawFilterValue) : String.valueOf(rawFilterValue);
            _filterTextOperator = op;
        }

        DateCompareClause(FieldKey fieldKey, CompareType t, String op, Object rawFilterValue, Calendar param0, Calendar param1)
        {
            super(fieldKey, t, null);
            if (null == rawFilterValue)
                rawFilterValue = "";
            if (rawFilterValue instanceof Calendar)
                rawFilterValue = ((Calendar)rawFilterValue).getTime();
            _filterTextDate = rawFilterValue instanceof Date ? ConvertUtils.convert(rawFilterValue) : String.valueOf(rawFilterValue);
            _filterTextOperator = op;
            _paramVals = new Object[]{param0.getTime(), param1.getTime()};
        }

        @Override
        String toWhereClause(SqlDialect dialect, String alias)
        {
            return super.toWhereClause(dialect, alias);
        }

        @Override
        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            sb.append("DATE(");
            appendColumnName(sb, formatter);
            sb.append(")");
            sb.append(_filterTextOperator);
            sb.append(_filterTextDate);
        }

        @Override
        protected String toURLParamValue()
        {
            return _filterTextDate;
        }
    }


    static class DateEqCompareClause extends DateCompareClause
    {
        DateEqCompareClause(FieldKey fieldKey, Calendar startValue)
        {
            this(fieldKey, startValue, startValue);
        }

        DateEqCompareClause(FieldKey fieldKey, Object rawFilterValue, Calendar startValue)
        {
            super(fieldKey, DATE_EQUAL, " = ", rawFilterValue, startValue, addOneDay(startValue));
        }

        @Override
        String toWhereClause(SqlDialect dialect, String alias)
        {
            String selectName = dialect.getColumnSelectName(alias);
            return selectName + " >= ? AND " + selectName + " < ?";
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            String selectName = getLabKeySQLColName(_fieldKey);
            String sql = selectName + " >= ? AND " + selectName + " < ?";
            return substituteLabKeySqlParams(sql, columnMap);
        }

        @Override
        protected boolean meetsCriteria(ColumnRenderProperties col, Object value)
        {
            if (value == null)
                return false;
            Date dateValue = asDate(value);
            Date begin = asDate(getParamVals()[0]);
            Date end = asDate(getParamVals()[1]);
            return dateValue.compareTo(begin) >= 0 && dateValue.compareTo(end) < 0;
        }
    }


    static class DateNeqCompareClause extends DateCompareClause
    {
        DateNeqCompareClause(FieldKey fieldKey, Calendar startValue)
        {
            this(fieldKey, startValue, startValue);
        }

        DateNeqCompareClause(FieldKey fieldKey, Object rawFilterValue, Calendar startValue)
        {
            super(fieldKey, DATE_NOT_EQUAL, " <> ", rawFilterValue, startValue, addOneDay(startValue));
        }

        @Override
        String toWhereClause(SqlDialect dialect, String alias)
        {
            String selectName = dialect.getColumnSelectName(alias);
            return selectName + " < ? OR " + selectName + " >= ?";
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            String selectName = getLabKeySQLColName(_fieldKey);
            String sql = selectName + " < ? OR " + selectName + " >= ?";
            return substituteLabKeySqlParams(sql, columnMap);
        }

        @Override
        protected boolean meetsCriteria(ColumnRenderProperties col, Object value)
        {
            if (value == null)
                return true;
            Date dateValue = asDate(value);
            Date begin = asDate(getParamVals()[0]);
            Date end = asDate(getParamVals()[1]);
            return !(dateValue.compareTo(begin) >= 0 && dateValue.compareTo(end) < 0);
        }
    }


    static class DateGtCompareClause extends DateCompareClause
    {
        DateGtCompareClause(FieldKey fieldKey, Calendar startValue)
        {
            this(fieldKey, startValue, startValue);
        }

        DateGtCompareClause(FieldKey fieldKey, Object rawFilterValue, Calendar startValue)
        {
            super(fieldKey, DATE_GT, " > ", rawFilterValue, addOneDay(startValue));
        }

        @Override
        protected boolean meetsCriteria(ColumnRenderProperties col, Object value)
        {
            if (value == null)
                return false;
            Date dateValue = asDate(value);
            Date param = asDate(getParamVals()[0]);
            return dateValue.compareTo(param) >= 0;
        }
    }


    static class DateGteCompareClause extends DateCompareClause
    {
        DateGteCompareClause(FieldKey fieldKey, Calendar startValue)
        {
            this(fieldKey, startValue, startValue);
        }

        DateGteCompareClause(FieldKey fieldKey, Object rawFilterValue, Calendar startValue)
        {
            super(fieldKey, DATE_GTE, " >= ", rawFilterValue, startValue);
        }

        @Override
        protected boolean meetsCriteria(ColumnRenderProperties col, Object value)
        {
            if (value == null)
                return false;
            Date dateValue = asDate(value);
            Date param = asDate(getParamVals()[0]);
            return dateValue.compareTo(param) >= 0;
        }
    }


    static class DateLtCompareClause extends DateCompareClause
    {
        DateLtCompareClause(FieldKey fieldKey, Calendar startValue)
        {
            this(fieldKey, startValue, startValue);
        }

        DateLtCompareClause(FieldKey fieldKey, Object rawFilterValue, Calendar startValue)
        {
            super(fieldKey, DATE_LT, " < ", rawFilterValue, startValue);
        }

        @Override
        protected boolean meetsCriteria(ColumnRenderProperties col, Object value)
        {
            if (value == null)
                return false;
            Date dateValue = asDate(value);
            Date param = asDate(getParamVals()[0]);
            return dateValue.compareTo(param) < 0;
        }
    }


    static class DateLteCompareClause extends DateCompareClause
    {
        DateLteCompareClause(FieldKey fieldKey, Calendar startValue)
        {
            this(fieldKey, startValue, startValue);
        }

        DateLteCompareClause(FieldKey fieldKey, Object rawFilterValue, Calendar startValue)
        {
            super(fieldKey, DATE_LTE, " <= ", rawFilterValue, addOneDay(startValue));
        }

        @Override
        protected boolean meetsCriteria(ColumnRenderProperties col, Object value)
        {
            if (value == null)
                return false;
            Date dateValue = asDate(value);
            Date param = asDate(getParamVals()[0]);
            return dateValue.compareTo(param) < 0;
        }
    }

    public static class BetweenClause extends CompareClause
    {
        public static final String SEPARATOR = ",";

        public BetweenClause(@NotNull FieldKey fieldKey, Object beginValue, Object endValue, boolean negated)
        {
            super(fieldKey, negated ? NOT_BETWEEN : BETWEEN, null);

            beginValue = validateValue(beginValue);
            endValue = validateValue(endValue);

            _paramVals = new Object[] { beginValue, endValue };
            _negated = negated;
        }

        private Object validateValue(Object value)
        {
            if (value == null)
                throw new IllegalArgumentException(getCompareType()._displayValue + " filter on '" + _fieldKey + "' column requires exactly two non-null and non-empty parameter values separated by comma");

            if (value instanceof String && ((String)value).length() == 0)
                throw new IllegalArgumentException(getCompareType()._displayValue + " filter on '" + _fieldKey + "' column requires exactly two non-null and non-empty parameter values separated by comma");

            return value;
        }

        @Override
        protected String toURLParamValue()
        {
            Object[] values = getParamVals();
            if (values != null && values.length == 2 && values[0] != null && values[1] != null)
            {
                return CompareType.toCollectionURLParamValue(Arrays.asList(getParamVals()), SEPARATOR, false);
            }
            return null;
        }
    }


    static final private char[] charsToBeEscaped = new char[] { '%', '_', '[' };
    /** Note that we've intentionally chosen something other than the default of backslash */
    static final private char defaultEscapeChar = '!';

    public static String escapeLikePattern(String value, char escapeChar)
    {
        String strEscape = new String(new char[] { escapeChar } );
        value = StringUtils.replace(value, strEscape, strEscape + strEscape);
        for (char ch : charsToBeEscaped)
        {
            if (ch == escapeChar)
                continue;
            String strCh = new String(new char[] { ch});
            value = StringUtils.replace(value, strCh, strEscape + strCh);
        }
        return value;
    }

    abstract private static class LikeClause extends CompareClause
    {
        private final String _unescapedValue;

        protected LikeClause(FieldKey fieldKey, CompareType compareType, Object value)
        {
            super(fieldKey, compareType, escapeLikePattern(Objects.toString(value, "")));
            _unescapedValue = Objects.toString(value, "");
        }

        /** Takes a string and replaces all special LIKE characters (such as %) with their escaped equivalents */
        public static String escapeLikePattern(String value)
        {
            return CompareType.escapeLikePattern(value, defaultEscapeChar);
        }

        public static String sqlEscape()
        {
            return " ESCAPE '" + defaultEscapeChar + "'";
        }

        @Override
        protected int appendFilterValueText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            int result = sb.length();
            sb.append(" LIKE ?");
            return result;
        }

        @Override
        // Value has been escaped for LIKE SQL; use stashed unescaped value for display text instead
        protected void replaceParamValues(StringBuilder sb, int fromIndex)
        {
            int i = sb.indexOf("?", fromIndex);
            sb.replace(i, i + 1, _unescapedValue);
        }

        // Issue 37524: QueryWebPart with CONTAINS filter and value that includes an underscore will generate incorrect filter on the "select all" url
        @Override
        protected String toURLParamValue()
        {
            return _unescapedValue;
        }

        @Override
        abstract String toWhereClause(SqlDialect dialect, String alias);
    }

    private static class StartsWithClause extends LikeClause
    {
        public StartsWithClause(FieldKey fieldKey, Object value)
        {
            super(fieldKey, CompareType.STARTS_WITH, value);
        }

        @Override
        String toWhereClause(SqlDialect dialect, String alias)
        {
            return dialect.getColumnSelectName(alias) + " " + dialect.getCaseInsensitiveLikeOperator() + " " + dialect.concatenate("?", "'%'") + sqlEscape();
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            Object value = getParamVals()[0];
            return "STARTSWITH(" + getLabKeySQLColName(_fieldKey) + ", '" + escapeLabKeySqlValue(value, getColumnType(columnMap, JdbcType.VARCHAR), true) + "')";
        }

        @Override
        protected int appendFilterValueText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            int result = sb.length();
            sb.append(" STARTS WITH ?");
            return result;
        }
    }

    private static class DoesNotStartWithClause extends LikeClause
    {
        public DoesNotStartWithClause(FieldKey fieldKey, Object value)
        {
            super(fieldKey, CompareType.DOES_NOT_START_WITH, value);
        }

        @Override
        String toWhereClause(SqlDialect dialect, String alias)
        {
            return "(" + dialect.getColumnSelectName(alias) + " IS NULL OR " + dialect.getColumnSelectName(alias) + " NOT " + dialect.getCaseInsensitiveLikeOperator() + " " + dialect.concatenate("?", "'%'") + sqlEscape() + ")";
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            Object value = getParamVals()[0];
            return "(" + getLabKeySQLColName(_fieldKey) + " IS NULL OR " + getLabKeySQLColName(_fieldKey) + " NOT LIKE '" + escapeLabKeySqlValue(value, getColumnType(columnMap, JdbcType.VARCHAR), true) + "%'" + sqlEscape() + ")";
        }

        @Override
        protected int appendFilterValueText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            int result = sb.length();
            sb.append(" DOES NOT START WITH ?");
            return result;
        }
    }

    public static class EqualsCompareClause extends CompareClause
    {
        public EqualsCompareClause(@NotNull FieldKey fieldKey, CompareType comparison, Object value)
        {
            super(fieldKey, comparison, value);
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            ColumnInfo colInfo = columnMap.get(_fieldKey);
            assert getParamVals().length == 1;
            if (needsTypeConversion())
            {
                Object value = convertParamValue(colInfo, getParamVals()[0]);
                if (isNull(value))
                {
                    // Flip to treat this as an IS NULL comparison request
                    return ISBLANK.createFilterClause(_fieldKey, null).toSQLFragment(columnMap, dialect);
                }
            }

            return super.toSQLFragment(columnMap, dialect);
        }
    }

    public static class NotEqualsCompareClause extends CompareClause
    {
        public NotEqualsCompareClause(FieldKey fieldKey, CompareType comparison, Object value)
        {
            super(fieldKey, comparison, value);
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            ColumnInfo colInfo = columnMap.get(_fieldKey);
            assert getParamVals().length == 1;
            Object convertedValue = convertParamValue(colInfo, getParamVals()[0]);
            if (needsTypeConversion() && isNull(convertedValue))
            {
                // Flip to treat this as an IS NOT NULL comparison request
                return NONBLANK.createFilterClause(_fieldKey, null).toSQLFragment(columnMap, dialect);
            }

            return super.toSQLFragment(columnMap, dialect);
        }
    }

    public static class ContainsClause extends LikeClause
    {
        public ContainsClause(FieldKey fieldKey, Object value)
        {
            super(fieldKey, CompareType.CONTAINS, value);
        }

        @Override
        String toWhereClause(SqlDialect dialect, String alias)
        {
            return dialect.getColumnSelectName(alias) + " " + dialect.getCaseInsensitiveLikeOperator() + " " + dialect.concatenate("'%'", "?", "'%'") + sqlEscape();
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            String colName = getLabKeySQLColName(_fieldKey);
            return "LOWER(" + colName + ") LIKE LOWER('%" + escapeLabKeySqlValue(getParamVals()[0], getColumnType(columnMap, JdbcType.VARCHAR), true) + "%') " + sqlEscape();
        }

        @Override
        protected int appendFilterValueText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            int result = sb.length();
            sb.append(" CONTAINS ?");
            return result;
        }
    }

    public static class DoesNotContainClause extends LikeClause
    {
        public DoesNotContainClause(FieldKey fieldKey, Object value)
        {
            super(fieldKey, CompareType.DOES_NOT_CONTAIN, value);
        }

        @Override
        String toWhereClause(SqlDialect dialect, String alias)
        {
            return "(" + dialect.getColumnSelectName(alias) + " IS NULL OR " + dialect.getColumnSelectName(alias) + " NOT " + dialect.getCaseInsensitiveLikeOperator() + " " + dialect.concatenate("'%'", "?", "'%'") + sqlEscape() + ")";
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            String colName = getLabKeySQLColName(_fieldKey);
            return "(" + colName + " IS NULL OR LOWER(" + colName + ") NOT LIKE LOWER('%" + escapeLabKeySqlValue(getParamVals()[0], getColumnType(columnMap, JdbcType.VARCHAR), true) + "%')" + sqlEscape() + ")";
        }

        @Override
        protected int appendFilterValueText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            appendColumnName(sb, formatter);
            int result = sb.length();
            sb.append(" DOES NOT CONTAIN ?");
            return result;
        }
    }

    private static class NotEqualOrNullClause extends CompareClause
    {
        NotEqualOrNullClause(FieldKey fieldKey, Object value)
        {
            super(fieldKey, CompareType.NEQ_OR_NULL, value);
        }

        @Override
        String toWhereClause(SqlDialect dialect, String alias)
        {
            String neq = CompareType.NEQ.getSql();
            String isNull = CompareType.ISBLANK.getSql();
            return "(" + dialect.getColumnSelectName(alias) + neq + " OR " + dialect.getColumnSelectName(alias) + isNull + ")";
        }
    }

    private static class MvClause extends CompareClause
    {
        private final boolean isNull;

        MvClause(FieldKey fieldKey, boolean isNull)
        {
            super(fieldKey, isNull ? CompareType.NO_MV_INDICATOR : CompareType.MV_INDICATOR, null);
            this.isNull = isNull;
        }

        @Override
        public List<FieldKey> getFieldKeys()
        {
            List<FieldKey> names = new ArrayList<>();
            names.add(_fieldKey);
            names.add(new FieldKey(_fieldKey.getParent(), _fieldKey.getName() + MvColumn.MV_INDICATOR_SUFFIX));
            return names;
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            FieldKey mvFieldKey = new FieldKey(_fieldKey.getParent(), _fieldKey.getName() + MvColumn.MV_INDICATOR_SUFFIX);
            ColumnInfo mvColumn = columnMap.get(mvFieldKey);
            SQLFragment sql = new SQLFragment(mvColumn.getAlias() + " IS " + (isNull ? "" : "NOT ") + "NULL");
            return sql;
        }
    }

    private static class MemberOfClause extends CompareClause
    {
        MemberOfClause(@NotNull FieldKey fieldKey, Object value)
        {
            super(fieldKey, CompareType.MEMBER_OF, value);
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            ColumnInfo colInfo = columnMap != null ? columnMap.get(_fieldKey) : null;
            String alias = colInfo != null ? colInfo.getAlias() : _fieldKey.getName();

            Object id = getId();

            if (id == null)
            {
                return new SQLFragment("(1 = 2)");
            }

            return getMemberOfSQL(dialect, new SQLFragment(alias), new SQLFragment("?", convertParamValue(colInfo, id)));
        }

        @Nullable
        private Object getId()
        {
            Object id = getParamVals().length == 0 ? null : getParamVals()[0];

            // If we don't have a value to use, try using the current user
            if (id == null || "".equals(id) || (id instanceof Parameter.TypedValue && ((Parameter.TypedValue)id).getJdbcParameterValue() == null))
            {
                User user = (User)QueryService.get().getEnvironment(QueryService.Environment.USER);
                if (user != null)
                {
                    id = user.getUserId();
                }
            }
            return id;
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            return "ISMEMBEROF(" + getParamVals()[0] + ", " + getLabKeySQLColName(_fieldKey) + ")";
        }

        @Override
        protected void appendFilterText(StringBuilder sb, ColumnNameFormatter formatter)
        {
            // Try to resolve the parameter value to a Group or User object

            Object id = getId();

            if (id != null)
            {
                try
                {
                    Integer groupId = (Integer)ConvertUtils.convert(String.valueOf(id), Integer.class);
                    if (groupId != null)
                    {
                        Group group = org.labkey.api.security.SecurityManager.getGroup(groupId.intValue());
                        if (group != null)
                        {
                            sb.append("Is a member of the ").append(group.isProjectGroup() ? "project" : "site").append(" group '").append(group.getName()).append("'");
                            return;
                        }
                        User user = UserManager.getUser(groupId.intValue());
                        if (user != null)
                        {
                            sb.append("Is the user '").append(user.getDisplayName(null)).append("'");
                            return;
                        }
                    }
                }
                catch (ConversionException ignored) {}
            }
            // Couldn't resolve the group for whatever reason
            sb.append("Invalid 'member of' filter");
        }
    }

    /** Generates SQL that checks if the given user id is a member of the group id */
    public static SQLFragment getMemberOfSQL(SqlDialect dialect, SQLFragment userIdSQL, SQLFragment groupIdSQL)
    {
        SQLFragment ret = new SQLFragment();
        ret.append("(").append(groupIdSQL).append(") IN (");

        if (dialect.isPostgreSQL())
        {
            ret.append(
                "WITH RECURSIVE allmembers(userid, groupid) AS (\n" +
                "   SELECT userid, groupid FROM core.members WHERE userid = ").append(userIdSQL).append(
                "\nUNION\n" +
                "   SELECT a.groupid as userid, m.groupid as groupid FROM allmembers a, core.members m WHERE a.groupid=m.userid\n" +
                ")\n" +
                "SELECT groupid FROM allmembers"
            );
        }
        else
        {
            // nested WITH doesn't seem to work on SQL Server
            // ONLY WORKS 3 LEVELS DEEP!
            SQLFragment onelevel = new SQLFragment(), twolevel = new SQLFragment(), threelevel = new SQLFragment();
            onelevel.append("SELECT groupid FROM core.members _M1_ where _M1_.userid=(").append(userIdSQL).append(")");
            twolevel.append("SELECT groupid FROM core.members _M2_ WHERE _M2_.userid IN (").append(onelevel).append(")");
            threelevel.append("SELECT groupid FROM core.members _M3_ WHERE _M3_.userid IN (").append(twolevel).append(")");
            ret.append(onelevel).append(" UNION ").append(twolevel).append(" UNION ").append(threelevel);
        }

        ret.append(" UNION SELECT (").append(userIdSQL).append(")");
        ret.append(" UNION SELECT ").append(Group.groupGuests);
        ret.append(" UNION SELECT ").append(Group.groupUsers).append(" WHERE 0 < (").append(userIdSQL).append(")");
        ret.append(")");
        return ret;
    }

    public static class TestCase
    {
        @Test
        public void testAsDate()
        {
            // Date value
            Date dateNow = new Date();
            assertEquals(dateNow.getTime(), asDate(dateNow).getTime());

            // Calendar value
            Calendar calendarNow = Calendar.getInstance();
            assertEquals(calendarNow.getTime().getTime(), asDate(calendarNow).getTime());

            // Days ago
            String daysInFuture = "+5";
            Date dateInFuture = new Date(DateUtil.addDuration(System.currentTimeMillis(), "5d"));

            String daysInPast = "-17";
            Date dateInPast = new Date(DateUtil.subtractDuration(System.currentTimeMillis(), "17d"));

            // These assertions are checking that the day offsets are respected. Since asDate() calls
            // System.currentTimeMillis() itself they are set up to check that the dates are equivalent
            // to their respective "days offset date" +/- one second due to the delta in time between calls.
            assertTrue(Math.abs(dateInFuture.getTime() - asDate(daysInFuture).getTime()) < 1000);
            assertTrue(Math.abs(dateInPast.getTime() - asDate(daysInPast).getTime()) < 1000);

            // Formatted date string
            String formattedDateStr = DateUtil.formatDate(ContainerManager.getRoot(), dateNow);

            // Formatting causes rounding of date (e.g. loss of context like minutes, seconds, etc)
            // Rehydrate a time from the Date parsed from the format.
            long dateFromFormat = DateUtil.parseDateTime(ContainerManager.getRoot(), formattedDateStr);

            assertEquals(dateFromFormat, asDate(formattedDateStr).getTime());

            // Long value
            String longNow = dateNow.getTime() + "L";
            assertEquals(dateNow.getTime(), asDate(longNow).getTime());

            // Number throws exception
            Integer invalidNumber = 123;
            boolean threwExpectedException = false;
            try
            {
                asDate(invalidNumber);
            }
            catch (ConversionException e)
            {
                threwExpectedException = true;
            }

            assertTrue("Expected numeric value to throw ConversionException", threwExpectedException);
        }

        @Test
        public void testMeFilterValue()
        {
            User user = TestContext.get().getUser();
            Container container = ContainerManager.getSharedContainer();
            UserSchema core = QueryService.get().getUserSchema(user, container, "core");

            // lookup to core.SiteUsers
            TableInfo usersTable = core.getTable("Users");
            assertTrue(isUserIdColumn(usersTable.getColumn("UserId")));
            assertTrue(isUserIdColumn(usersTable.getColumn("CreatedBy")));
            assertTrue(isUserIdColumn(usersTable.getColumn("Owner")));

            // lookup to core.Users
            TableInfo usersAndGroupsTable = core.getTable("UsersAndGroups");
            assertTrue(isUserIdColumn(usersTable.getColumn("UserId")));

            TableInfo containerTable = core.getTable("Containers");
            assertTrue(isUserIdColumn(((FilteredTable)containerTable).getRealTable().getColumn("CreatedBy")));

            ColumnInfo createdByCol = containerTable.getColumn("CreatedBy");
            assertTrue(isUserIdColumn(createdByCol));
            assertFalse(isUserDisplayColumn(createdByCol));

            // verify the user lookup display column
            ColumnInfo createdByDisplayCol = createdByCol.getFk().createLookupColumn(createdByCol, "DisplayName");
            assertFalse(isUserIdColumn(createdByDisplayCol));
            assertTrue(isUserDisplayColumn(createdByDisplayCol));

            // expand the '~me~' filter parameter value
            assertEquals(user.getUserId(), CompareType.convertParamValue(createdByCol, ME_FILTER_PARAM_VALUE));
            assertEquals(user.getDisplayName(user), CompareType.convertParamValue(createdByDisplayCol, ME_FILTER_PARAM_VALUE));

            // Issue 40361: query-selectDistinct.api doesn't work when a ~me~ filter exists
            // The logic checking isUserDisplayColumn() for QAliasedColumn was incorrect
            var createdByDisplayNameFieldKey = FieldKey.fromParts("CreatedBy", "DisplayName");
            Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(containerTable, List.of(createdByDisplayNameFieldKey));
            ColumnInfo qAliasedCreatedByDisplayCol = cols.get(createdByDisplayNameFieldKey);
            assertNotNull(qAliasedCreatedByDisplayCol);
            assertFalse(isUserIdColumn(qAliasedCreatedByDisplayCol));
            assertTrue(isUserDisplayColumn(qAliasedCreatedByDisplayCol));

            // create PropertyDescriptor with lookup to core.Users table
            PropertyDescriptor userPd = new PropertyDescriptor();
            userPd.setPropertyType(PropertyType.INTEGER);
            userPd.setName("Owner");
            userPd.setLookupSchema("core");
            userPd.setLookupQuery("Users");
            assertTrue(isUserIdColumn(userPd));
            assertEquals(user.getUserId(), CompareType.convertParamValue(userPd, ME_FILTER_PARAM_VALUE));

            // verify conditional formatting matches
            SimpleFilter.FilterClause clause = CompareType.EQUAL.createFilterClause(createdByCol.getFieldKey(), ME_FILTER_PARAM_VALUE);
            assertFalse(clause.meetsCriteria(createdByCol, User.guest.getUserId()));
            assertTrue(clause.meetsCriteria(createdByCol, user.getUserId()));
            assertTrue(clause.meetsCriteria(userPd, user.getUserId()));
            assertFalse(clause.meetsCriteria(createdByDisplayCol, user.getUserId()));
            assertTrue(clause.meetsCriteria(createdByDisplayCol, user.getDisplayName(user)));

            // Issue 39395: ClassCastException when rendering conditional formats in issue reports
            // call meetsCriteria with a Long value against an Integer column type
            SimpleFilter.FilterClause gtClause = CompareType.GT.createFilterClause(createdByCol.getFieldKey(), "1000");
            assertTrue(gtClause.meetsCriteria(createdByCol, 1001L));
        }

        @Test
        public void testUrlParamValue()
        {
            CompareClause ct = (CompareClause)CompareType.DATE_GT.createFilterClause(new FieldKey(null,"datecol"), "-1");
            // make sure this isn't converted into a date
            assertEquals("-1",ct.toURLParamValue());
        }
    }
}
