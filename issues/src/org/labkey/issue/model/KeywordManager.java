/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.issue.model;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.issue.ColumnType;
import org.labkey.issue.CustomColumnConfiguration;

import java.util.Collection;
import java.util.Collections;

/**
 * User: adam
 * Date: 6/25/12
 * Time: 7:49 PM
 */
@Deprecated // This class can be deleted in 19.1
public class KeywordManager
{
    public static final Object KEYWORD_LOCK = new Object();
    public static final Cache<String, Collection<Keyword>> KEYWORD_CACHE = CacheManager.getCache(1000, CacheManager.HOUR, "Issue Keywords");

    private static String getCacheKey(Container c, ColumnType type)
    {
        return c.getId() + "/" + type.getOrdinal();
    }

    public static Collection<Keyword> getKeywords(final Container c, final ColumnType type)
    {
        final Container inheritFrom = IssueManager.getInheritFromContainer(c);

        if (inheritFrom != null)
        {
            String colName = type.getColumnName();
            CustomColumnConfiguration ccc = IssueManager.getCustomColumnConfiguration(c);
            String caption = ccc.getCaption(colName);
            if(StringUtils.isNotEmpty(caption) && ((type.isCustom() && !isCurrentColumnInherited(c, type)))) //if custom type is not inherited
                return getKeywordsFromCache(c, type);
            else
                return getKeywordsFromCache(inheritFrom, type);
        }
        else
        {
            return getKeywordsFromCache(c, type);
        }
    }

    private static Collection<Keyword> getKeywordsFromCache(final Container c, final ColumnType type)
    {
        return KEYWORD_CACHE.get(getCacheKey(c, type), c, (key, argument) -> {
            assert type.getOrdinal() > 0;   // Ordinal 0 ==> no pick list (e.g., custom integer columns)

            SimpleFilter filter = SimpleFilter.createContainerFilter(c).addCondition(FieldKey.fromParts("Type"), type.getOrdinal());
            Sort sort = new Sort("Keyword");

            Selector selector = new TableSelector(IssuesSchema.getInstance().getTableInfoIssueKeywords(), PageFlowUtil.set("Keyword", "Default", "Container", "Type"), filter, sort);
            Collection<Keyword> keywords = selector.getCollection(Keyword.class);

            if (keywords.isEmpty())
            {
                String[] initialValues = type.getInitialValues();

                if (initialValues.length > 0)
                {
                    // First reference in this container... save away initial values & default
                    addKeyword(c, type, initialValues);
                    setKeywordDefault(c, type, type.getInitialDefaultValue());
                }

                keywords = selector.getCollection(Keyword.class);
            }

            return Collections.unmodifiableCollection(keywords);
        });
    }


    public static void addKeyword(Container c, ColumnType type, String... keywords)
    {
        //if inheriting settings from a different container, do not allow adding new keywords.
        if(IssueManager.getInheritFromContainer(c) != null && (type.isStandard() || isCurrentColumnInherited(c, type)))
            return;

        synchronized (KEYWORD_LOCK)
        {
            SqlExecutor executor = new SqlExecutor(IssuesSchema.getInstance().getSchema());

            for (String keyword : keywords)
            {
                executor.execute("INSERT INTO " + IssuesSchema.getInstance().getTableInfoIssueKeywords() + " (Container, Type, Keyword) VALUES (?, ?, ?)",
                        c.getId(), type.getOrdinal(), keyword);
            }

            KEYWORD_CACHE.remove(getCacheKey(c, type));
        }
    }


    // Clear old default value and set new one
    public static void setKeywordDefault(Container c, ColumnType type, String keyword)
    {
        //if inheriting settings from a different container, do not allow adding new keywords.
        if(IssueManager.getInheritFromContainer(c) != null && (type.isStandard() || isCurrentColumnInherited(c, type)))
            return;

        clearKeywordDefault(c, type);

        String selectName = IssuesSchema.getInstance().getTableInfoIssueKeywords().getColumn("Default").getSelectName();

        new SqlExecutor(IssuesSchema.getInstance().getSchema()).execute(
                "UPDATE " + IssuesSchema.getInstance().getTableInfoIssueKeywords() + " SET " + selectName + "=? WHERE Container = ? AND Type = ? AND Keyword = ?",
                Boolean.TRUE, c, type.getOrdinal(), keyword);

        KEYWORD_CACHE.remove(getCacheKey(c, type));
    }


    // Clear existing default value
    public static void clearKeywordDefault(Container c, ColumnType type)
    {
        //if inheriting settings from a different container, do not allow clearing
        if(IssueManager.getInheritFromContainer(c) != null && (type.isStandard() || isCurrentColumnInherited(c, type)))
            return;

        String selectName = IssuesSchema.getInstance().getTableInfoIssueKeywords().getColumn("Default").getSelectName();

        new SqlExecutor(IssuesSchema.getInstance().getSchema()).execute(
                "UPDATE " + IssuesSchema.getInstance().getTableInfoIssueKeywords() + " SET " + selectName + " = ? WHERE Container = ? AND Type = ?",
                Boolean.FALSE, c, type.getOrdinal());

        KEYWORD_CACHE.remove(getCacheKey(c, type));
    }


    private static boolean isCurrentColumnInherited(Container c, ColumnType type)
    {
        String colName = type.getColumnName();
        CustomColumnConfiguration ccc = IssueManager.getCustomColumnConfiguration(c);
        CustomColumn customColumn = ccc.getCustomColumn(colName);
        return customColumn.isInherited();
    }


    public static class Keyword
    {
        private String _keyword;
        private boolean _default = false;

        public boolean isDefault()
        {
            return _default;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setDefault(boolean def)
        {
            _default = def;
        }

        public String getKeyword()
        {
            return _keyword;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setKeyword(String keyword)
        {
            _keyword = keyword;
        }

        @Override
        public int hashCode()
        {
            int result;

            result = (_keyword != null ? _keyword.hashCode() : 0);
            result = 31 * result + (_default ? 1 : 0);

            return result;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Keyword that = (Keyword) o;

            if (_keyword != null ? !_keyword.equals(that._keyword) : that._keyword != null) return false;
            if (_default != that._default) return false;

            return true;
        }
    }
}
