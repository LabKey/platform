/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.security.Encryption;

/**
 * First reference to PropertyManager constructs the stores which causes initialization of the PropertyEncryption enum.
 * The AES128 enum requires the property manager (we store the standard salt in properties), so we use a holder pattern
 * (instead of normal static initialization) to implement thread-safe lazy initialization, breaking the loop.

 * User: adam
 * Date: 10/25/13
 */
class AES
{
    private static class AESHolder
    {
        static final AES INSTANCE = new AES();
    }

    private final Encryption.Algorithm _aes;

    AES()
    {
        _aes = Encryption.getAES128();
    }

    Encryption.Algorithm getAES()
    {
        return _aes;
    }

    static Encryption.Algorithm get()
    {
        return AESHolder.INSTANCE.getAES();
    }
}
