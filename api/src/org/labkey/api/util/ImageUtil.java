/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailOutputStream;
import org.w3c.dom.Document;
import org.xhtmlrenderer.swing.Java2DRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.*;
import java.io.OutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

/**
 * User: jeckels
 * Date: Nov 7, 2008
 */
public class ImageUtil
{
    /** Rewrite the output files so that they look nice and antialiased */
    public static double resizeImage(BufferedImage originalImage, OutputStream outputStream, double incrementalScale, int iterations) throws IOException
    {
        int imageType = (originalImage.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_RGB : originalImage.getType());
        return resizeImage(originalImage, outputStream, incrementalScale, iterations, imageType);
    }

    public static double resizeImage(BufferedImage originalImage, OutputStream outputStream, double incrementalScale, int iterations, int imageType) throws IOException
    {
        double finalScale = 1;

        BufferedImage bufferedResizedImage = null;

        // Unfortunately these images don't anti-alias well in a single resize using Java's default
        // algorithm, but they look fine if you do it in incremental steps
        for (int i = 0; i < iterations; i++)
        {
            finalScale *= incrementalScale;
            int width = (int) (originalImage.getWidth() * incrementalScale);
            int height = (int) (originalImage.getHeight() * incrementalScale);

            // Create a new empty image buffer to render into
            bufferedResizedImage = new BufferedImage(width, height, imageType);
            Graphics2D g2d = bufferedResizedImage.createGraphics();

            // Set up the hints to make it look decent
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Draw the resized image
            g2d.drawImage(originalImage, 0, 0, width, height, null);
            g2d.dispose();
            originalImage = bufferedResizedImage;
        }

        ImageIO.write(bufferedResizedImage, "png", outputStream);
        return finalScale;
    }


    // Standard thumbnail height in pixels.
    private static final double THUMBNAIL_HEIGHT = 256.0;

    public static Thumbnail renderThumbnail(BufferedImage image) throws IOException
    {
        // TODO: Check size -- don't scale up if smaller than THUMBNAIL_HEIGHT

        if (null != image)
        {
            ThumbnailOutputStream os = new ThumbnailOutputStream();
            ImageUtil.resizeImage(image, os, THUMBNAIL_HEIGHT/image.getHeight(), 1);

            return os.getThumbnail("image/png");
        }

        return null;
    }

    // Default size for generating the web image.
    private static final int WEB_IMAGE_WIDTH = 1024;
    private static final int WEB_IMAGE_HEIGHT = 768;

    public static BufferedImage webImage(URL url) throws IOException
    {
        return webImage(url, WEB_IMAGE_WIDTH, WEB_IMAGE_HEIGHT);
    }

    public static BufferedImage webImage(URL url, int width, int height) throws IOException
    {
        ArrayList<String> errors = new ArrayList<String>();
        Document doc = TidyUtil.convertHtmlToDocument(url, true, errors);
        if (!errors.isEmpty())
            throw new RuntimeException("Error converting to XHTML document: " + errors.get(0));
        return webImage(doc, width, height);
    }

    private static BufferedImage webImage(Document doc, int width, int height)
    {
        Java2DRenderer renderer = new Java2DRenderer(doc, width, height);

        renderer.getSharedContext().getTextRenderer().setSmoothingThreshold(8);
        return renderer.getImage();
    }

    public static Thumbnail webThumbnail(URL url) throws IOException
    {
        return renderThumbnail(webImage(url));
    }

}
