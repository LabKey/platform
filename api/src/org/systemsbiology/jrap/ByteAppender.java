/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
package org.systemsbiology.jrap;

/**
 * User: migra
 * Date: Jul 27, 2004
 * Time: 3:42:06 PM
 *
 *
 * Non-thread-safe buffer meant to behave like a StringBuffer.
 */
public class ByteAppender
{
    byte[] value;
    int count = 0;

    public ByteAppender()
    {
        value = new byte[1024];
    }

    public ByteAppender(int len)
    {
        value = new byte[len];
    }

    public byte[] getBuffer()
    {
        return value;
    }

    public int getCount()
    {
        return count;
    }

    public void reset()
    {
        count = 0;
    }

    public void ensureCapacity(int minimumCapacity)
    {
        if (minimumCapacity > value.length)
        {
            expandCapacity(minimumCapacity);
        }
    }

    private void expandCapacity(int minimumCapacity)
    {
        int newCapacity = (value.length + 1) * 2;
        if (newCapacity < 0)
        {
            newCapacity = Integer.MAX_VALUE;
        }
        else if (minimumCapacity > newCapacity)
        {
            newCapacity = minimumCapacity;
        }

        byte newValue[] = new byte[newCapacity];
        System.arraycopy(value, 0, newValue, 0, count);
        value = newValue;
    }

    public ByteAppender append(byte bytes[], int offset, int len) {
        int newcount = count + len;
        if (newcount > value.length)
            expandCapacity(newcount);
        System.arraycopy(bytes, offset, value, count, len);
        count = newcount;
        return this;
    }

    public ByteAppender appendCharsAsBytes(char chars[], int offset, int len) {
        int newcount = count + len;
        if (newcount > value.length)
            expandCapacity(newcount);

        int end = offset + len;
        int dst = count;
        for (int i = offset; i < end; i++)
            value[dst++] = (byte) chars[i];

        count = newcount;
        return this;
    }

}
