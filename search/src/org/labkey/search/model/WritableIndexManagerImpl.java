/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
package org.labkey.search.model;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.quartz.DateBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * User: adam
 * Date: Apr 18, 2010
 * Time: 9:08:19 AM
 */

// Adds synchronization for writing, in addition to searching synchronization provided by IndexManager
class WritableIndexManagerImpl extends IndexManager implements WritableIndexManager
{
    private static final Logger _log = Logger.getLogger(WritableIndexManagerImpl.class);
    private static final AtomicInteger _maybeRefreshRequests = new AtomicInteger();

    private final Object _writerLock = new Object();
    private final IndexWriter _iw;

    private boolean _closed = false;

    static
    {
        // Never ever cache queries, #26416
        IndexSearcher.setDefaultQueryCache(null);
    }

    static WritableIndexManager get(Path indexPath, Analyzer analyzer) throws IOException
    {
        SearcherFactory factory = new SearcherFactory()
        {
            @Override
            public IndexSearcher newSearcher(IndexReader reader, IndexReader previousReader) throws IOException
            {
                // TODO: Warm the new searcher before returning
                return super.newSearcher(reader, previousReader);
            }
        };

        Directory directory = openDirectory(indexPath);
        IndexWriter iw = null;

        try
        {
            // Consider: wrap analyzer with LimitTokenCountAnalyzer to limit indexed content?
            iw = new IndexWriter(directory, new IndexWriterConfig(analyzer));
        }
        finally
        {
            if (null == iw)
                directory.close();
        }

        WritableIndexManagerImpl impl = new WritableIndexManagerImpl(iw, factory, directory);
        MaybeRefreshJob.initializeTimer(impl);

        return impl;
    }

    /**
     * Open a Lucene Directory of the appropriate type. By default, we use the standard Lucene factory method, FSDirectory.open(),
     * but an administrator can override this default and select a specific Directory type (MMapDirectory, SimpleFSDirectory,
     * NIOFSDirectory) instead.
     * @param path The path in the file system to store the index
     * @return The opened Directory implementation
     * @throws IOException
     */
    static Directory openDirectory(Path path) throws IOException
    {
        return LuceneSearchServiceImpl.getDirectoryType().open(path);
    }

    private WritableIndexManagerImpl(IndexWriter iw, SearcherFactory factory, Directory directory) throws IOException
    {
        super(new SearcherManager(iw, factory), directory);
        _iw = iw;
    }

    IndexWriter getIndexWriter()
    {
        // This can happen in a race between the indexing thread and an admin changing the search configuration. We
        // use our own exception here (instead of the standard IndexWriter AlreadyClosedException) to distinguish an
        // explicit IndexManager close by our code from an unexpected IndexWriter.close().
        if (_closed)
            throw new IndexManagerClosedException();

        return _iw;
    }


    @Override
    public void index(String id, Document doc) throws IOException
    {
        synchronized (_writerLock)
        {
            deleteDocument(id);
            getIndexWriter().addDocument(doc);
            maybeRefresh(); // Make this document immediately available for searching (i.e., near-real-time searching), see #39330
        }
    }


    @Override
    public void deleteDocument(String id)
    {
        deleteDocuments(List.of(id));
    }

    @Override
    public void deleteDocuments(Collection<String> ids)
    {
        String currentId = null;
        try
        {
            synchronized (_writerLock)
            {
                IndexWriter iw = getIndexWriter();
                for (var id : ids)
                {
                    currentId = id;
                    iw.deleteDocuments(new Term(LuceneSearchServiceImpl.FIELD_NAME.uniqueId.toString(), id));
                }
                maybeRefresh();
            }
        }
        catch (IndexManagerClosedException x)
        {
            // Happens when an admin switches the index configuration, e.g., setting a new path to the index files.
            // We've swapped in the new IndexManager, but the indexing thread still holds an old (closed) IndexManager.
            // The document is not marked as indexed so it'll get reindexed... plus we're switching index directories
            // anyway, so everything's getting reindexed anyway.
        }
        catch (AlreadyClosedException e)
        {
            if (e.getCause() instanceof IOException && e.getCause().getMessage().equalsIgnoreCase("No space left on device"))
            {
                throw new ConfigurationException("Unable to write to search index, Disk is full", e);
            }
            else
                throw e;
        }
        catch (Throwable e)
        {
            _log.error("Indexing error deleting " + StringUtils.trimToEmpty(currentId), e);
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }

    @Override
    public void deleteQuery(Query query) throws IOException
    {
        try
        {
            synchronized (_writerLock)
            {
                IndexWriter w = getIndexWriter();
                w.deleteDocuments(query);
                maybeRefresh();
            }
        }
        catch (AlreadyClosedException e)
        {
            if (e.getCause() instanceof IOException && e.getCause().getMessage().equalsIgnoreCase("No space left on device"))
            {
                throw new ConfigurationException("Unable to write to search index, Disk is full", e);
            }
            else
                throw e;
        }
        catch (IndexManagerClosedException e)
        {
            // Configuration was changed
        }
    }

    @Override
    public void close() throws IOException
    {
        synchronized (_writerLock)
        {
            _closed = true;
            _iw.close();
            _manager.close();
            _directory.close();
        }
    }

    // If this throws then re-initialize the index manager
    @Override
    public void commit()
    {
        synchronized (_writerLock)
        {
            IndexWriter iw = getIndexWriter();

            try
            {
                iw.commit();
                _manager.maybeRefreshBlocking();
            }
            catch (AccessDeniedException e)
            {
                // Index is unwritable wrap in configuration exception to notify Admin
                throw new ConfigurationException("Unable to write to Full-Text search index: " + e.getMessage(), e);
            }
            catch (IOException e)
            {
                // Close IndexWriter here as well?
                ExceptionUtil.logExceptionToMothership(null, e);
            }
            catch (OutOfMemoryError e)
            {
                // JavaDoc strongly recommends closing the IndexWriter on OOM
                try
                {
                    try
                    {
                        iw.close();
                    }
                    catch (IOException e1)
                    {
                        // Log it and try again (per Lucene JavaDoc)
                        ExceptionUtil.logExceptionToMothership(null, e1);

                        try
                        {
                            iw.close();
                        }
                        catch (IOException e2)
                        {
                            ExceptionUtil.logExceptionToMothership(null, e2);
                        }
                    }
                }
                finally
                {
                    try
                    {
                        _directory.close();
                    }
                    catch (IOException e1)
                    {
                        ExceptionUtil.logExceptionToMothership(null, e1);
                    }
                    finally
                    {
                        throw e;
                    }
                }
            }
        }
    }


    @Override
    public Map<String, String> getIndexFormatProperties()
    {
        try
        {
            SegmentInfos segmentInfos = SegmentInfos.readLatestCommit(_directory);
            return ImmutableMap.of(
                "Index created major version", Integer.toString(segmentInfos.getIndexCreatedVersionMajor()),
                "Minimum segment version", segmentInfos.getMinSegmentLuceneVersion().toString(),
                "Latest commit version", segmentInfos.getCommitLuceneVersion().toString()
            );
        }
        catch (Exception e)
        {
            return Collections.singletonMap("Format", "Unknown");
        }
    }

    @Override
    public Directory getCurrentDirectory()
    {
        return getIndexWriter().getDirectory();
    }

    @Override
    public boolean isReal()
    {
        return true;
    }

    private void maybeRefresh()
    {
        _maybeRefreshRequests.incrementAndGet();
    }

    public static class MaybeRefreshJob implements Job
    {
        private static final int MAYBE_REFRESH_SECONDS = 5;
        private static final TriggerKey TRIGGER_KEY = new TriggerKey(MaybeRefreshJob.class.getName());

        private static void initializeTimer(WritableIndexManagerImpl impl)
        {
            try
            {
                Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

                // Clear previous job, if present
                if (scheduler.checkExists(TRIGGER_KEY))
                    scheduler.unscheduleJob(TRIGGER_KEY);

                // Configure quartz Trigger
                Trigger trigger = TriggerBuilder.newTrigger()
                    .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(MAYBE_REFRESH_SECONDS))
                    .startAt(DateBuilder.futureDate(MAYBE_REFRESH_SECONDS, DateBuilder.IntervalUnit.SECOND))
                    .build();

                // Quartz Job that executes maybe refresh
                JobDetail job = JobBuilder.newJob(MaybeRefreshJob.class).build();
                job.getJobDataMap().put(WritableIndexManagerImpl.class.getName(), impl);

                // Schedule trigger to execute the maybe refresh job on the configured schedule
                scheduler.scheduleJob(job, trigger);
            }
            catch (SchedulerException e)
            {
                throw new RuntimeException("Failed to schedule maybeRefresh job", e);
            }
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException
        {
            try
            {
                int requests = _maybeRefreshRequests.getAndSet(0);
                if (requests > 0)
                {
                    WritableIndexManagerImpl impl = (WritableIndexManagerImpl)context.getJobDetail().getJobDataMap().get(WritableIndexManagerImpl.class.getName());
                    impl.refreshNow();
                }
            }
            catch (Exception e)
            {
                throw new JobExecutionException(e);
            }
        }
    }

    public void refreshNow() throws IOException
    {
        _manager.maybeRefresh();
    }
}
