/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import java.io.*;
import java.util.zip.GZIPInputStream;


/**
 * examine a file and access as a gzip file if appropriate
 *
 * note this would be tidier as "public class PossiblyGZIPpedFileInputStream extends InputStream" but
 * I worry about performance since the read() call gets hit a lot and would rather not insert
 * another deeply loop nested function call PossiblyGZIPpedFileInputStream.read->_iStream.read()
 *
 * bpratt, Insilicos
 *
 */
abstract public class PossiblyGZIPpedFileInputStreamFactory
{
    private static final int STREAM_BUFFER_SIZE = 128 * 1024;

    static public InputStream getStream(File f) throws FileNotFoundException
    {
        FileInputStream fis = new FileInputStream(f);
        try
        {
            return new GZIPInputStream(fis, STREAM_BUFFER_SIZE);
        }
        catch (java.io.IOException e)
        {
            // not a gzip file - reopen since we ate a couple of bytes
            try
            {
                fis.close();
            }
            catch (java.io.IOException ee)
            {
                // seems unlikely at this point               
            }
            return new FileInputStream(f);
        }
    }
}
