/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * User: tholzman
 * Date: Jun 10, 2005
 * Time: 12:14:52 PM
 */
public class HashHelpers
{
    private static final int BYTE_ARRAY_SIZE = 4096;

    public static String hashFileContents(File file)  throws IOException
    {
        InputStream is = null;
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA");
            byte[] byteArray = new byte[BYTE_ARRAY_SIZE];
            is = new BufferedInputStream(new FileInputStream(file));
            int len;
            while ((len = is.read(byteArray)) > 0)
               md.update(byteArray, 0, len);
            return toHex(md.digest());
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            if (is != null)
                try { is.close(); } catch (IOException e) {}
        }
    }

    public static String hash(String data)
    {
        return hash(data.getBytes());
    }

    public static String hash(byte[] data)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA");
            md.update(data);
            return toHex(md.digest());
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static String toHex(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes)
        {
            char c1 = (char) ((aByte >>> 4) & 0xf);
            char c2 = (char) (aByte & 0xf);
            c1 = (char) ((c1 > 9) ? 'a' + (c1 - 10) : '0' + c1);
            c2 = (char) ((c2 > 9) ? 'a' + (c2 - 10) : '0' + c2);
            sb.append(c1);
            sb.append(c2);
        }
        return sb.toString().trim();
    }
}

