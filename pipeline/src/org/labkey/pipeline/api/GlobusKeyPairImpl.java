/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.pipeline.api;

import org.labkey.api.pipeline.GlobusKeyPair;
import org.apache.activemq.util.ByteArrayInputStream;
import org.globus.gsi.CertUtil;
import org.globus.gsi.bc.BouncyCastleOpenSSLKey;

import java.security.cert.X509Certificate;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/*
* User: jeckels
* Date: Jun 25, 2008
*/
public class GlobusKeyPairImpl implements GlobusKeyPair
{
    private byte[] _keyBytes;
    private String _password;
    private byte[] _certBytes;

    public GlobusKeyPairImpl(byte[] keyBytes, String password, byte[] certBytes)
    {
        _keyBytes = keyBytes;
        _password = password;
        _certBytes = certBytes;
    }

    public byte[] getKeyBytes()
    {
        return _keyBytes;
    }

    public String getPassword()
    {
        return _password;
    }

    public byte[] getCertBytes()
    {
        return _certBytes;
    }

    public void validateMatch() throws GeneralSecurityException
    {
        X509Certificate[] certs = getCertificates();
        RSAPrivateKey rsaPrivateKey = getPrivateKey();
        if (certs == null || certs.length == 0)
        {
            throw new InvalidKeyException("No X509 certificate found");
        }
        if (rsaPrivateKey == null)
        {
            throw new InvalidKeyException("No private key found");
        }

        PublicKey publicKey = certs[0].getPublicKey();
        if (!(publicKey instanceof RSAPublicKey))
        {
            throw new InvalidKeyException("Expected to get an RSA key in the X509 cert, but got a " + publicKey.getFormat());
        }
        RSAPublicKey rsaPublicKey = (RSAPublicKey)publicKey;
        if (!rsaPrivateKey.getModulus().equals(rsaPublicKey.getModulus()))
        {
            throw new InvalidKeyException("The private key does not match the public key in the X509 certificate.");
        }
    }

    public RSAPrivateKey getPrivateKey() throws GeneralSecurityException
    {
        if (_keyBytes == null)
        {
            return null;
        }
        try
        {
            BouncyCastleOpenSSLKey key = new BouncyCastleOpenSSLKey(new ByteArrayInputStream(_keyBytes));
            if (key.isEncrypted())
            {
                key.decrypt(_password == null ? "" : _password);
            }
            PrivateKey result = key.getPrivateKey();
            if (!(result instanceof RSAPrivateKey))
            {
                throw new InvalidKeyException("Expected to get an RSA key, but got a " + result.getFormat());
            }
            return (RSAPrivateKey)result;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Shouldn't happen with ByteArrayInputStream", e);
        }
    }

    public X509Certificate[] getCertificates() throws GeneralSecurityException
    {
        if (_certBytes == null)
        {
            return new X509Certificate[0];
        }
        List<X509Certificate> certs = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(_certBytes)));
        X509Certificate cert;
        try
        {
            while ( (cert = CertUtil.readCertificate(reader)) != null )
            {
                certs.add(cert);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Shouldn't happen with ByteArrayInputStream", e);
        }
        return certs.toArray(new X509Certificate[certs.size()]);
    }
}