package org.labkey.search.model;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.labkey.api.attachments.DocumentConversionService;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * User: adam
 * Date: 10/12/11
 * Time: 5:33 PM
 */
public class DocumentConversionServiceImpl implements DocumentConversionService
{
    private static final int DEFAULT_USER_SPACE_UNIT_DPI = 72;     // From PDFBox PDPage

    @Override
    public BufferedImage pdfToImage(InputStream pdfStream, int page)
    {
        // This matches the PDFBox PDPage.convertToImage defaults... these probably aren't ideal for all PDFs and target image formats
        return pdfToImage(pdfStream, page, BufferedImage.TYPE_USHORT_565_RGB, 2 * DEFAULT_USER_SPACE_UNIT_DPI);
    }

    @Override
    public BufferedImage pdfToImage(InputStream pdfStream, int page, int bufferedImageType, int resolution)
    {
        try
        {
            PDDocument document = PDDocument.load(pdfStream);
            List<PDPage> pages = document.getDocumentCatalog().getAllPages();

            if (pages.size() >= page)
            {
                PDPage pdPage = pages.get(page);
                return pdPage.convertToImage(bufferedImageType, resolution);
            }
        }
        catch (IOException e)
        {
            // Fall through
        }

        return null;
    }
}
