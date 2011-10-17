package org.labkey.core.thumbnail;

import org.apache.log4j.Logger;
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
import org.labkey.api.util.ShutdownListener;

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
    private static final BlockingQueue<DynamicThumbnailProvider> QUEUE = new LinkedBlockingQueue<DynamicThumbnailProvider>(1000);
    private static final ThumbnailGeneratingThread THREAD = new ThumbnailGeneratingThread();

    static
    {
        THREAD.start();
    }

    public ThumbnailServiceImpl()
    {
    }

    @Override
    public CacheableWriter getThumbnailWriter(StaticThumbnailProvider provider)
    {
        if (provider instanceof DynamicThumbnailProvider)
            return ThumbnailCache.getThumbnailWriter((DynamicThumbnailProvider)provider);
        else
            return ThumbnailCache.getThumbnailWriter(provider);
    }

    @Override
    public void queueThumbnailRendering(DynamicThumbnailProvider provider)
    {
        QUEUE.offer(provider);
    }

    @Override
    public void deleteThumbnail(DynamicThumbnailProvider provider)
    {
        AttachmentService.Service svc = AttachmentService.get();
        svc.deleteAttachment(provider, THUMBNAIL_FILENAME, null);
        ThumbnailCache.remove(provider);
    }

    @Override
    // Deletes existing thumbnail before saving
    public void replaceThumbnail(DynamicThumbnailProvider provider, AttachmentFile thumbnailFile) throws IOException
    {
        deleteThumbnail(provider);
        AttachmentService.Service svc = AttachmentService.get();
        svc.addAttachments(provider, Collections.singletonList(thumbnailFile), User.guest);
        ThumbnailCache.remove(provider);   // Just in case (delete already cleared the old thumbnail from the cache)
    }

    private static class ThumbnailGeneratingThread extends Thread implements ShutdownListener
    {
        private ThumbnailGeneratingThread()
        {
            setDaemon(true);
            setName(ThumbnailGeneratingThread.class.getSimpleName());
            ContextListener.addShutdownListener(this);
        }

        @Override
        public void run()
        {
            try
            {
                //noinspection InfiniteLoopStatement
                while (!interrupted())
                {
                    DynamicThumbnailProvider provider = QUEUE.take();

                    try
                    {
                        // TODO: Real ViewContext
                        Thumbnail thumbnail = provider.generateDynamicThumbnail(null);

                        if (null != thumbnail)
                        {
                            ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);
                            AttachmentFile file = new InputStreamAttachmentFile(thumbnail.getInputStream(), THUMBNAIL_FILENAME, thumbnail.getContentType());
                            svc.replaceThumbnail(provider, file);
                        }
                    }
                    catch (Exception e)  // Make sure exceptions don't kill the background thread
                    {
                        ExceptionUtil.logExceptionToMothership(null, e);
                    }
                    finally
                    {
                        // No matter what, clear this entry from the cache.
                        ThumbnailCache.remove(provider);
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
