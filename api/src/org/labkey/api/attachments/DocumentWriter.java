/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

import java.io.OutputStream;
import java.io.IOException;

/**
 * User: adam
 * Date: Jan 4, 2007
 * Time: 4:20:50 PM
 */
public interface DocumentWriter
{
    public void setContentType(String contentType);

    public void setContentDisposition(String value);

    public void setContentLength(int size);

    public OutputStream getOutputStream() throws IOException;
}
