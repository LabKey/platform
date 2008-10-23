/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.common.tools;

import org.apache.log4j.Logger;

import javax.xml.stream.XMLStreamException;
import java.io.*;

public abstract class MS2Loader
{
    protected Logger _log;
    protected SimpleXMLStreamReader _parser;
    protected long _fileLength;
    private InputStream _fIn;
    private static final int STREAM_BUFFER_SIZE = 128 * 1024;

    protected void init(File f, Logger log) throws FileNotFoundException, XMLStreamException
    {
        if (f.exists())
        {
            _fileLength = f.length();
            _fIn = new BufferedInputStream(new FileInputStream(f), STREAM_BUFFER_SIZE);
            _parser = new SimpleXMLStreamReader(_fIn);
        }
        else
            throw new FileNotFoundException(f.getAbsolutePath());

        _log = log;
    }


    public long getFileLength()
    {
        return _fileLength;
    }


    public int getCurrentOffset()
    {
        return _parser.getLocation().getCharacterOffset();
    }


    public void close()
    {
        try
        {
            if (null != _parser)
                _parser.close();
        }
        catch (XMLStreamException e)
        {
            _log.error(e);
        }
        try
        {
            if (null != _fIn)
                _fIn.close();
        }
        catch (IOException e)
        {
            _log.error(e);
        }
    }

    // Returns the index of the nth occurrence of search string within s, starting from the end
    public static int nthLastIndexOf(String s, String search, int n)
    {
        int index = s.length() + 1;

        for (int i = 0; i < n; i++)
        {
            index = s.lastIndexOf(search, index - 1);
            if (-1 == index) break;
        }

        return index;
    }
}
