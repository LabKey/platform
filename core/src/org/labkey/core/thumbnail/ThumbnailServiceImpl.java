/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
package org.labkey.core.thumbnail;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.data.CacheableWriter;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.thumbnail.DynamicThumbnailProvider;
import org.labkey.api.thumbnail.StaticThumbnailProvider;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.view.ViewContext;

import javax.servlet.ServletContextEvent;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * User: adam
 * Date: 10/8/11
 * Time: 9:22 AM
 */
public class ThumbnailServiceImpl implements ThumbnailService
{
    private static final Logger LOG = Logger.getLogger(ThumbnailServiceImpl.class);
    private static final BlockingQueue<Pair<DynamicThumbnailProvider, ImageType>> QUEUE = new LinkedBlockingQueue<>(1000);
    private static final ThumbnailGeneratingThread THREAD = new ThumbnailGeneratingThread();

    static
    {
        THREAD.start();
    }

    public ThumbnailServiceImpl()
    {
    }

    @Override
    public CacheableWriter getThumbnailWriter(StaticThumbnailProvider provider, ImageType type)
    {
        if (provider instanceof DynamicThumbnailProvider)
            return ThumbnailCache.getThumbnailWriter((DynamicThumbnailProvider)provider, type);
        else
            return ThumbnailCache.getThumbnailWriter(provider, type);
    }

    @Override
    public void queueThumbnailRendering(DynamicThumbnailProvider provider, ImageType type)
    {
        QUEUE.offer(new Pair<>(provider, type));
    }

    @Override
    public void deleteThumbnail(DynamicThumbnailProvider provider, ImageType type)
    {
        AttachmentService.Service svc = AttachmentService.get();
        svc.deleteAttachment(provider, type.getFilename(), null);
        ThumbnailCache.remove(provider, type);
    }

    @Override
    // Deletes existing thumbnail and then saves
    public void replaceThumbnail(DynamicThumbnailProvider provider, ImageType type, @Nullable ViewContext context) throws IOException
    {
        // TODO: Shouldn't need this check... but file-based reports don't have entityid??
        if (null == provider.getEntityId())
            return;

        deleteThumbnail(provider, type);

        Thumbnail thumbnail = provider.generateDynamicThumbnail(context);

        if (null != thumbnail)
        {
            AttachmentService.Service svc = AttachmentService.get();
            AttachmentFile thumbnailFile = new InputStreamAttachmentFile(thumbnail.getInputStream(), type.getFilename(), thumbnail.getContentType());

            try
            {
                svc.addAttachments(provider, Collections.singletonList(thumbnailFile), User.guest);
            }
            finally
            {
                // Delete already cleared the cache, but another request could have already cached a miss
                ThumbnailCache.remove(provider, type);
            }
        }
    }

    private static class ThumbnailGeneratingThread extends Thread implements ShutdownListener
    {
        // Not a daemon thread, because it performs I/O and needs to shut down gracefully
        private ThumbnailGeneratingThread()
        {
            setName(ThumbnailGeneratingThread.class.getSimpleName());
            ContextListener.addShutdownListener(this);
        }

        @Override
        public void run()
        {
            ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);

            if (null == svc)
            {
                LOG.warn(getClass().getSimpleName() + " is terminating because ThumbnailService is null");
                return;
            }

            try
            {
                //noinspection InfiniteLoopStatement
                while (!interrupted())
                {
                    Pair<DynamicThumbnailProvider, ImageType> pair = QUEUE.take();
                    DynamicThumbnailProvider provider = pair.getKey();
                    ImageType type = pair.getValue();

                    try
                    {
                        // TODO: Real ViewContext
                        svc.replaceThumbnail(provider, type, null);
                    }
                    catch (Throwable e)  // Make sure throwables don't kill the background thread
                    {
                        ExceptionUtil.logExceptionToMothership(null, e);
                    }
                    finally
                    {
                        // No matter what, clear this entry from the cache.
                        ThumbnailCache.remove(provider, type);
                    }
                }
            }
            catch (InterruptedException e)
            {
                LOG.debug(getClass().getSimpleName() + " is terminating due to interruption");
            }
        }

        @Override
        public void shutdownPre(ServletContextEvent servletContextEvent)
        {
            interrupt();
        }

        @Override
        public void shutdownStarted(ServletContextEvent servletContextEvent)
        {
        }
    }
}
