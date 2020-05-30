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

import org.labkey.api.util.FileStream;

import java.io.InputStream;
import java.io.IOException;

/**
 * User: adam
 * Date: Sep 10, 2007
 * Time: 3:39:23 PM
 */
public interface AttachmentFile extends FileStream
{
    @Override
    long getSize() throws IOException;
    String getError();
    String getFilename();
    String getContentType();
    @Override
    InputStream openInputStream() throws IOException;
    @Override
    void closeInputStream() throws IOException;
}
