/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.study.writer;

import java.io.OutputStream;
import java.io.IOException;

/*
* User: adam
* Date: Sep 5, 2009
* Time: 6:30:12 PM
*/

// Allows overriding arbitrary OutputStreams.  A little strange, since OutputStream is an abstract class not an interface...
public class OutputStreamWrapper extends OutputStream
{
    private final OutputStream _out;

    public OutputStreamWrapper(OutputStream out)
    {
        _out = out;
    }

    public void write(int b) throws IOException
    {
        _out.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException
    {
        _out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException
    {
        _out.write(b, off, len);
    }

    @Override
    public void flush() throws IOException
    {
        _out.flush();
    }

    @Override
    public void close() throws IOException
    {
        _out.close();
    }

    @Override
    public int hashCode()
    {
        return _out.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        return _out.equals(obj);
    }

    @Override
    protected Object clone() throws CloneNotSupportedException
    {
        throw new CloneNotSupportedException();
    }

    @Override
    public String toString()
    {
        return _out.toString();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    protected void finalize() throws Throwable
    {
    }
}
