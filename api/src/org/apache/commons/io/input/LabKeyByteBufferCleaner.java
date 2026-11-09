/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.apache.commons.io.input;

import java.nio.ByteBuffer;

// Provides access to org.apache.commons.io.input.ByteBufferCleaner, which is package-private
public class LabKeyByteBufferCleaner
{
    private static final boolean _supported = ByteBufferCleaner.isSupported();

    public static void clean(ByteBuffer buffer)
    {
        if (_supported)
        {
            ByteBufferCleaner.clean(buffer);
        }
    }
}
