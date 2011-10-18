/*
 * Copyright (c) 2011 LabKey Corporation
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
