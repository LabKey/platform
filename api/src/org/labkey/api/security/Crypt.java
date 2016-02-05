/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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
import org.apache.commons.lang3.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public enum Crypt
{
    MD5(new _md5(), "md5"),
    SaltMD5(new _saltmd5(), "salt"),
    BCrypt(new _bcrypt(), "bc");

    final _Crypt _crypt;
    final String _prefix;

    Crypt(_Crypt crypt, String prefix)
    {
        _crypt = crypt;
        _prefix = prefix + ":";
    }

    public boolean matches(String credentials, String digest)
    {
        return _crypt.matches(credentials, digest);
    }
    public String digest(String credentials)
    {
        return _crypt.digest(credentials);
    }
    public String digestWithPrefix(String credentials)
    {
        return _prefix + _crypt.digest(credentials);
    }
    public boolean acceptPrefix(String prefix)
    {
        return prefix.startsWith(_prefix);
    }
    public boolean matchesWithPrefix(String credentials, String digest)
    {
        return _crypt.matches(credentials, digest.substring(_prefix.length()));
    }



    private interface _Crypt
    {
        public abstract boolean matches(String credentials, String digest);
        public abstract String digest(String credentials);
    }


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



    public static class _md5 implements _Crypt
    {
        public boolean matches(String credentials, String digest)
        {
            return digest.equals(digest(credentials));
        }


        public String digest(String credentials)
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
    }


    public static class _saltmd5 implements _Crypt
    {
        public boolean matches(String credentials, String digest)
        {
            return digest.equals(_digest(credentials,digest));
        }

        @Override
        public String digest(String credentials)
        {
            return _digest(credentials, null);
        }

        public String _digest(String credentials, String salt)
        {
            credentials = StringUtils.trimToEmpty(credentials);
            salt = makeSalt(salt);

            synchronized (md)
            {
                md.reset();
                md.update(salt.getBytes());
                md.update(credentials.getBytes());
                return salt + encodeBase64(md.digest());
            }
        }
    }

    public static class _bcrypt implements _Crypt
    {
        @Override
        public boolean matches(String credentials, String digest)
        {
            return org.labkey.api.security.BCrypt.checkpw(credentials, digest);
        }

        @Override
        public String digest(String credentials)
        {
            long n = System.nanoTime();
            String ret = org.labkey.api.security.BCrypt.hashpw(credentials, org.labkey.api.security.BCrypt.gensalt(11));
            double d = (System.nanoTime() - n)/1000000000.0;
            return ret;
        }
    }


/*    public static class _saltaes extends Crypt
    {
        private static final String _algorithm = "AES";
        private static final int _keylen = 24;
        private static final String _text = "eUpeZclKWup36fRxihyIVcKf"; // don't change me!


        public boolean matches(String credentials, String crypt)
        {
            return crypt.equals(_digest(credentials,crypt));
        }

        public String digest(String pwd)
        {
            return _digest(pwd, null);
        }

        private String _digest(String pwd, String salt)
        {
            pwd = StringUtils.trimToEmpty(pwd);
            salt = makeSalt(salt);
            
            String crypt = "";

            int[] keyInts = new int[_keylen];
            for (int i = 0; i < pwd.length() ; i++)
                keyInts[i % _keylen] = keyInts[i%_keylen] * 31 + pwd.charAt(i);
            byte[] keyBytes = new byte[_keylen];
            for (int i = 0; i < _keylen ; i++)
                keyBytes[i] = (byte)keyInts[i];
            byte[] textBytes = new byte[_keylen];
            for (int i = 0; i < _keylen; i++)
                textBytes[i] = (byte)(salt.charAt(i % salt.length()) ^ _text.charAt(i));

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
    }
*/


    private static String makeSalt(String salt)
    {
        if (null == salt) 
            salt = "";
        while (salt.length() < 4)
            salt += _randChar();
        salt = salt.substring(0, 4);
        return salt;
    }

    private static char convertDigit(int value)
    {
        value &= 0x0f;
        if (value >= 10)
            return ((char) (value - 10 + 'a'));
        else
            return ((char) (value + '0'));
    }

    public static String encodeHex(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder(bytes.length * 2);

        for (byte aByte : bytes)
        {
            sb.append(convertDigit(aByte >> 4));
            sb.append(convertDigit(aByte & 0x0f));
        }

        return sb.toString();
    }


    public static String encodeBase64(byte[] bytes)
    {
        return new String(Base64.encodeBase64(bytes));    
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
