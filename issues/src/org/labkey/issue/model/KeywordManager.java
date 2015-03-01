/*
 * Copyright (c) 2012-2015 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
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

import java.util.Collection;
import java.util.Collections;

/**
 * User: adam
 * Date: 6/25/12
 * Time: 7:49 PM
 */
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
        final Container currentOrInheritFrom = IssueManager.getInheritFromOrCurrentContainer(c);

        return KEYWORD_CACHE.get(getCacheKey(currentOrInheritFrom, type), currentOrInheritFrom, new CacheLoader<String, Collection<Keyword>>() {
            @Override
            public Collection<Keyword> load(String key, @Nullable Object argument)
            {
                assert type.getOrdinal() > 0;   // Ordinal 0 ==> no pick list (e.g., custom integer columns)

                SimpleFilter filter = SimpleFilter.createContainerFilter(currentOrInheritFrom).addCondition(FieldKey.fromParts("Type"), type.getOrdinal());
                Sort sort = new Sort("Keyword");

                Selector selector = new TableSelector(IssuesSchema.getInstance().getTableInfoIssueKeywords(), PageFlowUtil.set("Keyword", "Default", "Container", "Type"), filter, sort);
                Collection<Keyword> keywords = selector.getCollection(Keyword.class);

                if (keywords.isEmpty())
                {
                    String[] initialValues = type.getInitialValues();

                    if (initialValues.length > 0)
                    {
                        // First reference in this container... save away initial values & default
                        addKeyword(currentOrInheritFrom, type, initialValues);
                        setKeywordDefault(currentOrInheritFrom, type, type.getInitialDefaultValue());
                    }

                    keywords = selector.getCollection(Keyword.class);
                }

                return Collections.unmodifiableCollection(keywords);
            }
        });
    }


    public static void addKeyword(Container c, ColumnType type, String... keywords)
    {
        //if inheriting settings from a different container, do not allow adding new keywords.
        if(IssueManager.getInheritFromContainer(c) != null)
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
        if(IssueManager.getInheritFromContainer(c) != null)
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
        if(IssueManager.getInheritFromContainer(c) != null)
            return;

        String selectName = IssuesSchema.getInstance().getTableInfoIssueKeywords().getColumn("Default").getSelectName();

        new SqlExecutor(IssuesSchema.getInstance().getSchema()).execute(
                "UPDATE " + IssuesSchema.getInstance().getTableInfoIssueKeywords() + " SET " + selectName + " = ? WHERE Container = ? AND Type = ?",
                Boolean.FALSE, c, type.getOrdinal());

        KEYWORD_CACHE.remove(getCacheKey(c, type));
    }


    public static void deleteKeyword(Container c, ColumnType type, String keyword)
    {
        //if inheriting settings from a different container, do not allow deleting
        if(IssueManager.getInheritFromContainer(c) != null)
            return;

        Collection<Keyword> keywords;

        synchronized (KEYWORD_LOCK)
        {
            new SqlExecutor(IssuesSchema.getInstance().getSchema()).execute(
                    "DELETE FROM " + IssuesSchema.getInstance().getTableInfoIssueKeywords() + " WHERE Container = ? AND Type = ? AND Keyword = ?",
                    c, type.getOrdinal(), keyword);
            KEYWORD_CACHE.remove(getCacheKey(c, type));
            keywords = getKeywords(c, type);
        }

        //Check to see if the last keyword of a required field was deleted, if so no longer make the field required.
        if (keywords == null || keywords.isEmpty())
        {
            String columnName = type.getColumnName();
            String requiredFields = IssueManager.getRequiredIssueFields(c);

            if (null != columnName && requiredFields.contains(columnName))
            {
                //Here we want to remove the type from the required fields.
                requiredFields = requiredFields.replace(columnName, "");
                if (requiredFields.length() > 0)
                {
                    if (requiredFields.charAt(0) == ';')
                    {
                       requiredFields = requiredFields.substring(1);
                    }
                    else if (requiredFields.charAt(requiredFields.length()-1) == ';')
                    {
                       requiredFields = requiredFields.substring(0, requiredFields.length()-1);
                    }
                    else
                    {
                       requiredFields = requiredFields.replace(";;", ";");
                    }
                }

                IssueManager.setRequiredIssueFields(c, requiredFields);
            }
        }
    }


    public static String getKeywordOptions(final Container c, final ColumnType type)
    {
        Collection<Keyword> keywords = getKeywords(c, type);
        StringBuilder sb = new StringBuilder(keywords.size() * 30);

        if (type.allowBlank())
            sb.append("<option></option>\n");

        for (Keyword keyword : keywords)
        {
            sb.append("<option>");
            sb.append(PageFlowUtil.filter(keyword.getKeyword()));
            sb.append("</option>\n");
        }

        return sb.toString();
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
    }
}
