/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
package org.labkey.api.attachments;

import org.apache.batik.transcoder.TranscoderException;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * User: adam
 * Date: 10/12/11
 * Time: 4:24 PM
 */
// Expose some interesting conversion methods via this service.
public interface DocumentConversionService
{
    // Returns null if requested page doesn't exist
    @Nullable BufferedImage pdfToImage(InputStream pdfStream, int page);
    // Returns null if requested page doesn't exist
    @Nullable BufferedImage pdfToImage(InputStream pdfStream, int page, int bufferedImageType, int resolution);

    public void svgToPng(String svg, OutputStream os) throws TranscoderException;
    public void svgToPng(String svg, OutputStream os, @Nullable Float height) throws TranscoderException;
}
