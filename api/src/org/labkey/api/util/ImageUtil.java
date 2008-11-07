/*
 * Copyright (c) 2008 LabKey Corporation
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.*;
import java.io.OutputStream;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Nov 7, 2008
 */
public class ImageUtil
{
    /** Rewrite the output files so that they look nice and antialiased */
    public static double resizeImage(BufferedImage originalImage, OutputStream outputStream, double incrementalScale, int iterations) throws IOException
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
            bufferedResizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
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
}