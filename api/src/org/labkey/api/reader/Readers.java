/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.api.reader;

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.io.input.XmlStreamReader;
import org.labkey.api.util.StringUtilsLabKey;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 *  Factory methods to create Readers, ensuring correct character sets and buffering by default.
 *
 *  Created by adam on 5/30/2015.
 */
public class Readers
{
    public static Reader getUnbufferedReader(InputStream in)
    {
        return new InputStreamReader(in, StringUtilsLabKey.DEFAULT_CHARSET);
    }

    public static Reader getUnbufferedReader(File file) throws FileNotFoundException
    {
        return getUnbufferedReader(new FileInputStream(file));
    }

    public static BufferedReader getReader(InputStream in)
    {
        return new BufferedReader(getUnbufferedReader(in));
    }

    public static BufferedReader getReader(File file) throws FileNotFoundException
    {
        return getReader(new FileInputStream(file));
    }

    // Detects XML file character encoding based on BOM, XML prolog, or content type... falling back on UTF-8
    public static BufferedReader getXmlReader(InputStream in) throws IOException
    {
        return new BufferedReader(new XmlStreamReader(in));
    }

    // Detects XML file character encoding based on BOM, XML prolog, or content type... falling back on UTF-8
    public static BufferedReader getXmlReader(File file) throws IOException
    {
        return new BufferedReader(new XmlStreamReader(file));
    }

    /**
     * Detects text file character encoding based on BOM... falling back on UTF-8 if no BOM present
     */
    public static BufferedReader getBOMDetectingReader(InputStream in) throws IOException
    {
        BOMInputStream bos = new BOMInputStream(in, ByteOrderMark.UTF_8, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_32LE);
        Charset charset = bos.hasBOM() ? Charset.forName(bos.getBOM().getCharsetName()) : StringUtilsLabKey.DEFAULT_CHARSET;
        return new BufferedReader(new InputStreamReader(bos, charset));
    }

    /**
     * Detects text file character encoding based on BOM... falling back on UTF-8 if no BOM present
     */
    public static BufferedReader getBOMDetectingReader(File file) throws IOException
    {
        return getBOMDetectingReader(new FileInputStream(file));
    }
}
