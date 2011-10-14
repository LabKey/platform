package org.labkey.api.attachments;

import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * User: adam
 * Date: 10/12/11
 * Time: 4:24 PM
 */
// Tika is stuck in the search module, but some of the document parsers are useful for more than searching. Expose
// some interesting methods via this service.
public interface DocumentConversionService
{
    // Returns null if requested page doesn't exist
    @Nullable BufferedImage pdfToImage(InputStream pdfStream, int page);
    // Returns null if requested page doesn't exist
    @Nullable BufferedImage pdfToImage(InputStream pdfStream, int page, int bufferedImageType, int resolution);
}
