/*
 * Copyright (c) 2015 LabKey Corporation
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Convenience class to create a Reader that guarantees UTF-8 encoding of all characters
 */
public class UTF8Reader extends InputStreamReader
{
    public UTF8Reader(InputStream in)
    {
        super(in, StandardCharsets.UTF_8);
    }

    public UTF8Reader(File file) throws FileNotFoundException
    {
        super(new FileInputStream(file), StandardCharsets.UTF_8);
    }
}
