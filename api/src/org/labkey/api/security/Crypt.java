/*
 * Copyright (c) 2003-2008 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.security;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * This is just a wrapper over JCE to give functionality similar to crypt().
 * The generated values, however, are not compatible.
 */

public class Crypt
{
    private static final MessageDigest md;

    static
    {
        MessageDigest instance;

        try
        {
            instance = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException x)
        {
            instance = null;
        }

        md = instance;
    }

    public static String digest(String credentials)
    {
        if (null == credentials)
            credentials = "";

        synchronized (md)
        {
            md.reset();
            md.update(credentials.getBytes());
            return (encodeHex(md.digest()));
        }
    }

    private static char convertDigit(int value)
    {
        value &= 0x0f;
        if (value >= 10)
            return ((char) (value - 10 + 'a'));
        else
            return ((char) (value + '0'));
    }

    public static String encodeHex(byte bytes[])
    {
        StringBuffer sb = new StringBuffer(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++)
        {
            sb.append(convertDigit((int) (bytes[i] >> 4)));
            sb.append(convertDigit((int) (bytes[i] & 0x0f)));
        }
        return sb.toString();
    }


    private static final String _algorithm = "DESede";
    private static final int _keybits = 168;
    private static final int _keylen = 24;
    private static final String _text = "I am a string of no consequence.";

    public static String crypt(String pwd, String salt)
    {
        pwd = null == pwd ? "" : pwd.trim();

        String crypt = "";
        if (null == salt) salt = "";
        if (2 > salt.length()) ;
        salt += _randChar();
        if (2 > salt.length()) ;
        salt += _randChar();
        salt = salt.substring(0, 2);

        byte[] keyBytes = new byte[_keylen];
        for (int i = 0; i < pwd.length(); i++)
            keyBytes[i % _keylen] ^= pwd.charAt(i);

        byte[] textBytes = new byte[_keylen];
        for (int i = 0; i < _keylen; i++)
            textBytes[i] = (byte) salt.charAt(i % 2);
        for (int i = 0; i < _text.length(); i++)
            textBytes[i % _keylen] ^= _text.charAt(i);

        try
        {
            SecretKeySpec skey = new SecretKeySpec(keyBytes, _algorithm);
            Cipher cipher = Cipher.getInstance(_algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, skey);
            byte[] cipherBytes = cipher.doFinal(textBytes);
            crypt = new String(Base64.encodeBase64(cipherBytes, false));
        }
        catch (Exception x)
        {
            x.printStackTrace(System.err);
            return null;
        }

        return salt + crypt.substring(0, 32);
    }


    private static char _randChar()
    {
        int i = (int) (Math.random() * 62);
        if (i > 52)
            return (char) ('0' + i - 52);
        return (char) (0 == (i & 0x0001)
                ? 'A' + (i >> 1)
                : 'a' + (i >> 1));
    }
}
