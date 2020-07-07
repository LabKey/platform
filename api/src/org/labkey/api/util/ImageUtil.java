/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
package org.labkey.api.util;

import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailOutputStream;
import org.labkey.api.thumbnail.ThumbnailService.ImageType;
import org.labkey.api.view.ViewContext;
import org.w3c.dom.Document;
import org.xhtmlrenderer.resource.ImageResource;
import org.xhtmlrenderer.resource.XMLResource;
import org.xhtmlrenderer.swing.Java2DRenderer;
import org.xhtmlrenderer.swing.NaiveUserAgent;
import org.xhtmlrenderer.util.XRLog;
import org.xhtmlrenderer.util.XRLogger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Basic image manipulation, such as rescaling and conversion between image file formats.
 * User: jeckels
 * Date: Nov 7, 2008
 */
public class ImageUtil
{
    private static Logger LOG = LogManager.getLogger(ImageUtil.class);

    static
    {
        XRLog.setLoggerImpl(new XRLogger()
        {
            org.apache.logging.log4j.Level toPriority(Level level)
            {
                int l = level.intValue();
                if (l == Level.SEVERE.intValue())
                    return org.apache.logging.log4j.Level.ERROR;
                if (l == Level.WARNING.intValue())
                    return org.apache.logging.log4j.Level.WARN;
                if (l == Level.INFO.intValue())
                    return org.apache.logging.log4j.Level.INFO;
                if (l == Level.CONFIG.intValue())
                    return org.apache.logging.log4j.Level.INFO;
                if (l == Level.FINE.intValue())
                    return org.apache.logging.log4j.Level.DEBUG;
                if (l == Level.FINER.intValue())
                    return org.apache.logging.log4j.Level.DEBUG;
                if (l == Level.FINEST.intValue())
                    return org.apache.logging.log4j.Level.TRACE;
                if (l == Level.ALL.intValue())
                    return org.apache.logging.log4j.Level.ALL;
                if (l == Level.OFF.intValue())
                    return org.apache.logging.log4j.Level.OFF;
                return org.apache.logging.log4j.Level.DEBUG;
            }

            @Override
            public void log(String where, Level level, String msg)
            {
                LOG.log(toPriority(level), msg);

            }

            @Override
            public void log(String where, Level level, String msg, Throwable t)
            {
                LOG.log(toPriority(level), msg, t);
            }

            @Override
            public void setLevel(String s, Level level)
            {
            }
        });
    }

    /** Rewrite the output files so that they look nice and antialiased, and writes a PNG to the outputStream */
    public static double resizeImage(BufferedImage originalImage, OutputStream outputStream, double incrementalScale, int iterations) throws IOException
    {
        int imageType = (originalImage.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_RGB : originalImage.getType());
        return resizeImage(originalImage, outputStream, incrementalScale, iterations, imageType);
    }

    /** Rewrite the output files so that they look nice and antialiased, and writes a PNG to the outputStream */
    public static double resizeImage(BufferedImage originalImage, OutputStream outputStream, double incrementalScale, int iterations, int imageType) throws IOException
    {
        double finalScale = Math.pow(incrementalScale, iterations);
        Thumbnails.of(originalImage)
                .scale(finalScale)
                .imageType(imageType)
                .outputFormat("png")
                .toOutputStream(outputStream);
        return finalScale;
    }

    public static @Nullable Thumbnail renderThumbnail(BufferedImage image, ImageType imageType) throws IOException
    {
        return renderThumbnail(image, imageType, false);
    }

    public static @Nullable Thumbnail renderThumbnail(BufferedImage image, ImageType imageType, boolean makeSquare) throws IOException
    {
        return renderThumbnail(image, imageType.getHeight(), makeSquare);
    }

    public static @Nullable Thumbnail renderThumbnail(BufferedImage image) throws IOException
    {
        return renderThumbnail(image, ImageType.Large);
    }

    public static @Nullable Thumbnail renderThumbnail(BufferedImage image, float desiredHeight, boolean makeSquare) throws IOException
    {
        if (null == image)
            return null;

        ThumbnailOutputStream os = new ThumbnailOutputStream();
        int height = image.getHeight();

        if (makeSquare)
            image = cropImageToSquare(image);

        // Scale the image down if height is greater than THUMBNAIL_HEIGHT, otherwise leave it alone
        if (height > desiredHeight)
            ImageUtil.resizeImage(image, os, desiredHeight / height, 1);
        else
            ImageIO.write(image, "png", os);

        return os.getThumbnail("image/png");
    }

    private static @NotNull BufferedImage cropImageToSquare(@NotNull BufferedImage image)
    {
        int height = image.getHeight();
        int width = image.getWidth();

        if (width > height)
        {
            int sizeDiff = width - height;
            return image.getSubimage(sizeDiff / 2, 0, width - sizeDiff, height);
        }
        else if (height > width)
        {
            int sizeDiff = height - width;
            return image.getSubimage(0, sizeDiff / 2, width, height - sizeDiff);
        }

        return image;
    }

    public static Thumbnail webThumbnail(URL url) throws IOException
    {
        return renderThumbnail(webImage(url, WEB_IMAGE_WIDTH, WEB_IMAGE_HEIGHT), ImageType.Large);
    }

    public static Thumbnail webThumbnail(ViewContext context, String html, URI baseURI) throws IOException
    {
        List<String> errors = new ArrayList<>();
        Document document = TidyUtil.convertHtmlToDocument(html, true, errors);
        if (!errors.isEmpty())
            throw new RuntimeException(errors.get(0));
        return renderThumbnail(webImage(context, document, baseURI, WEB_IMAGE_WIDTH, WEB_IMAGE_HEIGHT));
    }

    // Default size for generating the web image -- using 1366x768 or 1440x900 might look nicer, but Monocle throws a BufferOverflowException while rendering
    private static final int WEB_IMAGE_WIDTH = 1024;
    private static final int WEB_IMAGE_HEIGHT = 768;

    public static BufferedImage webImage(URL url) throws IOException
    {
        return webImage(url, WEB_IMAGE_WIDTH, WEB_IMAGE_HEIGHT);
    }

    private static BufferedImage _webImage(URL url, int width, int height) throws IOException
    {
        URI uri;
        try
        {
            uri = url.toURI();
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }

        Pair<String, URI> content = HttpUtil.getHTML(uri);
        List<String> errors = new ArrayList<>();
        String xhtml = TidyUtil.tidyHTML(content.first, true, errors);
        if (!errors.isEmpty())
            throw new RuntimeException(errors.get(0));

        return _webImage(xhtml, content.second, width, height);
    }

    private static BufferedImage _webImage(String xhtml, URI baseURI, int width, int height)
    {
        String uri = FileUtil.uriToString(baseURI);
        Java2DRenderer renderer = new Java2DRenderer(uri, width, height);
        renderer.getSharedContext().setUserAgentCallback(new TidyUserAgent(xhtml, uri));
        renderer.getSharedContext().getTextRenderer().setSmoothingThreshold(8);
        return renderer.getImage();
    }

    // Tidying the HTML content as a string doesn't work very well -- the Xerces parser will balk.
    // I'd like to just subclass XMLResource and hand back a Tidy DOM Document, but the XMLResource
    // constructors are private.
    private static class TidyUserAgent extends NaiveUserAgent
    {
        String _xhtml;
        String _baseURI;

        private TidyUserAgent(String xhtml, String baseURI)
        {
            _xhtml = xhtml;
            _baseURI = baseURI;
        }

        @Override
        public XMLResource getXMLResource(String uri)
        {
            if (uri.equals(_baseURI))
                return XMLResource.load(new StringReader(_xhtml));

            final Pair<String, URI> content;

            try
            {
                content = HttpUtil.getHTML(new URI(uri));
            }
            catch (IOException | URISyntaxException e)
            {
                throw new RuntimeException(e);
            }

            ArrayList<String> errors = new ArrayList<>();
            String xhtml = TidyUtil.tidyHTML(content.first, true, errors);
            if (!errors.isEmpty())
                throw new RuntimeException("Error converting to XHTML document: " + errors.get(0));

            return XMLResource.load(new StringReader(xhtml));
        }
    }

    public static BufferedImage webImage(final URL url, int width, int height) throws IOException
    {
        URI uri;
        try
        {
            uri = url.toURI();
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }

        Pair<Document, URI> content = HttpUtil.getXHTML(uri);
        if (content != null)
            return webImage(content.first, content.second, width, height);

        return null;
    }

    private static BufferedImage webImage(ViewContext context, Document document, URI baseURI, int width, int height)
    {
        String uri = FileUtil.uriToString(baseURI);
        Java2DRenderer renderer = new Java2DRenderer(document, width, height);

        if (null == context)
            renderer.getSharedContext().setUserAgentCallback(new DumbUserAgent(uri));
        else
            renderer.getSharedContext().setUserAgentCallback(new LabKeyUserAgent(context, uri));

        renderer.getSharedContext().getTextRenderer().setSmoothingThreshold(8);
        return renderer.getImage();
    }

    private static BufferedImage webImage(Document document, URI baseURI, int width, int height)
    {
        return webImage(null, document, baseURI, width, height);
    }

    // DumbUserAgent is intended to be used by a Java2DRenderer that has been constructed with a Document.
    // Java2DRenderer.getImage() tries to set the baseURL to null which breaks finding relative resources.
    // No tidying of HTML content is needed.
    private static class DumbUserAgent extends NaiveUserAgent
    {
        protected DumbUserAgent(String baseURL)
        {
            super.setBaseURL(baseURL);
        }

        @Override
        public void setBaseURL(String url)
        {
            // no-op.  Java2DRenderer.getImage() tries to set the baseURL to null when using the overloaded Java2DRenderer(Document, ...) constructor.
        }
    }

    // LabKey user agent is used to resolve image resources (or others if required in the future) using the
    // same session as the incoming request.  Right now this occurs when we are generating a thumbnail for a Knitr
    // R report
    private static class LabKeyUserAgent extends DumbUserAgent
    {
        private final ViewContext _context;

        private LabKeyUserAgent(ViewContext context, String baseURL)
        {
            super(baseURL);
            _context = context;
        }

        @Override
        public org.xhtmlrenderer.resource.ImageResource getImageResource(java.lang.String uri)
        {
            ImageResource ir;
            String uriResolved = resolveURI(uri);
            ir = (ImageResource) _imageCache.get(uriResolved);

            if (ir == null &&
                _context != null &&
                _context.getRequest() != null &&
                _context.getRequest().getSession() != null)
            {
                try
                {
                    URLHelper helper = new URLHelper(uriResolved);
                    String sessionKey = helper.getParameter("sessionKey");
                    String deleteFile = helper.getParameter("deleteFile");
                    File file = (File) _context.getRequest().getSession().getAttribute(sessionKey);
                    if (file != null && file.exists())
                    {
                        try
                        {
                            BufferedImage img = ImageIO.read(file);
                            ir = createImageResource(uri, img);
                            _imageCache.put(uri, ir);

                            if (BooleanUtils.toBoolean(deleteFile))
                                file.delete();
                        }
                        catch(IOException e)
                        {
                        }
                    }
                }
                catch(URISyntaxException e)
                {
                }
            }

            if (ir != null)
                return ir;

            return super.getImageResource(uri);
        }
    }


    public static void main(String[] args) throws Exception
    {
        // enable logging
        System.setProperty("xr.util-logging.loggingEnabled", "true");

        URL url = new URL(args[0]);
        BufferedImage img = webImage(url);
        ImageIO.write(img, "png", new File("out.png"));
    }
}
