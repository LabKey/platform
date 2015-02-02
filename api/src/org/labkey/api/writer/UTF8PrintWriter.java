/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.writer;

import org.labkey.api.util.StringUtilsLabKey;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * User: adam
 * Date: 1/10/2015
 * Time: 4:07 PM
 */

/**
 * PrintWriter that guarantees standard encoding of all characters, which the standard PrintWriter doesn't make very easy.
 */
public class UTF8PrintWriter extends PrintWriter
{
    public UTF8PrintWriter(OutputStream out)
    {
        super(new OutputStreamWriter(out, StringUtilsLabKey.DEFAULT_CHARSET));
    }

    public UTF8PrintWriter(File file) throws FileNotFoundException
    {
        this(file, false);
    }

    public UTF8PrintWriter(File file, boolean append) throws FileNotFoundException
    {
        super(new OutputStreamWriter(new FileOutputStream(file, append), StringUtilsLabKey.DEFAULT_CHARSET));
    }
}
