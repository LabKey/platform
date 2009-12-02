/*
 * Copyright (c) 2009-2009 LabKey Corporation
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
package org.labkey.api.search;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.Resource;
import org.labkey.api.data.*;
import org.apache.log4j.Category;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 12, 2009
 * Time: 12:54:01 PM
 */
public interface SearchService
{
    static Category _log = Category.getInstance(SearchService.class);
    
    enum PRIORITY
    {
        crawl,      // lowest
        background, // crawler item

        bulk,       // all wikis
        group,      // one container
        item        // one page/attachment
    }


    enum PROPERTY
    {
        title("title"),
        category("searchCategory"),
        documentID("uniqueDocumentId");

        final String _propName;
        PROPERTY(String name)
        {
            _propName = name;
        }

        @Override
        public String toString()
        {
            return _propName;
        }
    }



    // UNDONE: convert to interface
    
    public abstract static class IndexTask
    {
        final private String _description;
        protected long _start;
        protected long _complete = 0;
        protected boolean _isReady = false;
        final private AtomicInteger _estimate = new AtomicInteger();
        final private AtomicInteger _indexed = new AtomicInteger();
        final private AtomicInteger _failed = new AtomicInteger();
        final protected Map<Object,Object> _subtasks = Collections.synchronizedMap(new IdentityHashMap<Object,Object>());
        final StringWriter _sw = new StringWriter();
        final PrintWriter _out = new PrintWriter(_sw);


        public IndexTask(String description)
        {
            _description = description;
            _start = System.currentTimeMillis();
        }


        public String getDescription()
        {
            return _description;
        }
        

        public int getDocumentCountEstimate()
        {
            return _estimate.get();
        }


        public int getIndexedCount()
        {
            return _indexed.get();
        }


        public int getFailedCount()
        {
            return _failed.get();
        }
        

        public long getStartTime()
        {
            return _start;
        }


        public long getCompleteTime()
        {
            return _complete;
        }


        protected void addItem(Object item)
        {
            _subtasks.put(item,item);
        }


        public void log(String message)
        {
            synchronized (_sw)
            {
                _out.println(message);
            }
        }


        public Reader getLog()
        {
            synchronized (_sw)
            {
                return new StringReader(_sw.getBuffer().toString());
            }
        }


        public void addToEstimate(int i)
        {
            _estimate.addAndGet(i);
        }


        // indicates that caller is done adding Resources to this task
        public void setReady()
        {
            _isReady = true;
            checkDone();
        }
        

        protected void completeItem(Object item, boolean success)
        {
            if (success)
                _indexed.incrementAndGet();
            else
                _failed.incrementAndGet();
            Object remove =  _subtasks.remove(item);
            assert null != remove;
            assert remove == item;
            checkDone();
        }


        //
        // add items to index
        //

        public abstract void addRunnable(@NotNull Runnable r, @NotNull PRIORITY pri);
        public abstract void addResource(@NotNull SearchCategory category, ActionURL url, PRIORITY pri);
        public abstract void addResource(@NotNull String identifier, PRIORITY pri);
        public abstract void addResource(@NotNull Resource r, PRIORITY pri);
        protected abstract void checkDone();
    }
    

    public interface ResourceResolver
    {
        Resource resolve(@NotNull String resourceIdentifier);
    }


    public static class SearchCategory
    {
        private final String _name;
        private final String _description;

        public SearchCategory(@NotNull String name, @NotNull String description)
        {
            _name = name;
            _description = description;
        }

        public String getName()
        {
            return _name;
        }
        
        public String getDescription()
        {
            return _description;
        }
    }

    //
    // search
    //


    public String search(String queryString);
    public List<SearchCategory> getSearchCategories();

    //
    // index
    //

    public IndexTask defaultTask();
    public IndexTask createTask(String description);

    void deleteResource(String identifier, PRIORITY pri);

    List<IndexTask> getTasks();

    //
    // configuration
    //
    
    public void addSearchCategory(SearchCategory category);
    public void addResourceResolver(@NotNull String prefix, @NotNull ResourceResolver resolver);
    public void clearIndex();


    /**
     * filter for documents modified since the provided date
     *
     * modifiedSince == null, means full search
     * otherwise incremental search which may mean either
     *      modified > lastIndexed
     * or
     *      modified > modifiedSince
     *
     * depending on whether lastIndexed is tracked

     * see Module.enumerateDocuments
     */

    public static class LastIndexedClause extends SimpleFilter.FilterClause
    {
        SQLFragment _sqlf = new SQLFragment();
        private List<String> _colNames = new ArrayList<String>();


        public LastIndexedClause(TableInfo info, java.util.Date modifiedSince, String tableAlias)
        {
            boolean incremental = modifiedSince != null;
            // no filter
            if (!incremental)
                return;

            ColumnInfo modified = info.getColumn("modified");
            ColumnInfo lastIndexed = info.getColumn("lastIndexed");
            String prefix = null == tableAlias ? " " : tableAlias + ".";

            if (null != modified && null != lastIndexed)
            {
                _sqlf.append("NOT (");
                _sqlf.append(prefix).append(modified.getSelectName()).append("<").append(prefix).append(lastIndexed.getSelectName());
                _sqlf.append(")");
                _colNames.add(modified.getName());
                _colNames.add(lastIndexed.getName());
            }
            else if (null != modified)
            {
                _sqlf.append(prefix).append(modified.getSelectName()).append("> ?");
                _sqlf.add(modifiedSince);
                _colNames.add(modified.getName());
            }
            else if (null != lastIndexed)
            {
                _sqlf.append(prefix).append(lastIndexed.getSelectName()).append("< ?");
                _sqlf.add(lastIndexed);
                _colNames.add(lastIndexed.getName());
            }
        }

        public SQLFragment toSQLFragment(Map<String, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            return _sqlf;
        }

        public List<String> getColumnNames()
        {
            return _colNames;
        }
    }
}