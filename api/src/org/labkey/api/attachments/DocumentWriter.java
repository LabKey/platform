/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import java.io.IOException;
import java.io.OutputStream;

/**
 * Something that knows how to absorb the content of a file or document, including some basic metadata.
 * User: adam
 * Date: Jan 4, 2007
 */
public interface DocumentWriter
{
    void setContentType(String contentType);

    void setContentDisposition(String value);

    void setContentLength(int size);

    OutputStream getOutputStream() throws IOException;
}
