/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.transcoder.wmf.tosvg.WMFTranscoder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.DocumentConversionService;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;

/**
 * User: adam
 * Date: 10/12/11
 * Time: 5:33 PM
 */
public class DocumentConversionServiceImpl implements DocumentConversionService
{
    private static final int DEFAULT_USER_SPACE_UNIT_DPI = 72;     // From PDFBox PDPage

    @Override
    public void svgToPng(String svg, OutputStream os) throws TranscoderException
    {
        svgToPng(svg, os, null);
    }

    // If height is provided, auto-size keeping the aspect ratio; if null, use the dimensions in the SVG
    @Override
    public void svgToPng(String svg, OutputStream os, @Nullable Float height) throws TranscoderException
    {
        svgToPng(new StringReader(svg), os, height);
    }

    // If height is provided, auto-size keeping the aspect ratio; if null, use the dimensions in the SVG
    @Override
    public void svgToPng(Reader reader, OutputStream os, @Nullable Float height) throws TranscoderException
    {
        TranscoderInput xIn = new TranscoderInput(reader);

        PNGTranscoder transcoder = new PNGTranscoder();
        transcoder.addTranscodingHint(PNGTranscoder.KEY_BACKGROUND_COLOR, java.awt.Color.WHITE);

        if (null != height)
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, height);

        transcoder.transcode(xIn, new TranscoderOutput(os));
    }

    // Not tested yet... but should work  TODO: wmfToPng, chaining this with svgToPng
    public void wmfToSvg(InputStream is, OutputStream os, @Nullable Float height) throws TranscoderException
    {
        TranscoderInput xIn = new TranscoderInput(is);
        WMFTranscoder transcoder = new WMFTranscoder();
        transcoder.transcode(xIn, new TranscoderOutput(os));
    }

    @Override
    public BufferedImage pdfToImage(InputStream pdfStream, int page)
    {
        // This matches the PDFBox PDPage.convertToImage defaults... these probably aren't ideal for all PDFs and target image formats
        return pdfToImage(pdfStream, page, BufferedImage.TYPE_USHORT_565_RGB, 2 * DEFAULT_USER_SPACE_UNIT_DPI);
    }

    @Override
    public BufferedImage pdfToImage(InputStream pdfStream, int page, int bufferedImageType, int resolution)
    {
        try (InputStream is = pdfStream; PDDocument document = PDDocument.load(is))
        {
            // PDFBox extracts secure PDFs as blank images; detect and use static thumbnail instead
            if (document.isEncrypted())
                return null;

            if (document.getNumberOfPages() > page)
            {
                PDFRenderer pdfRenderer = new PDFRenderer(document);

                return pdfRenderer.renderImageWithDPI(page, resolution, ImageType.RGB);
            }
        }
        catch (IOException e)
        {
            // Fall through
        }

        return null;
    }
}
