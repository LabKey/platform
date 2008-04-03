/*
 * Copyright (c) 2007 LabKey Software Foundation
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
package org.labkey.api.pipeline;

import org.labkey.api.util.FileType;

import java.io.File;
import java.io.IOException;

/**
 * <code>WorkDirectory</code>
 *
 * @author brendanx
 */
public interface WorkDirectory
{
    enum Function { input, output }

    static final String DATA_REL_PATH = "../..";

    File getDir();

    File newFile(String name);

    File newFile(Function f, String name);

    File newFile(FileType type);

    File newFile(Function f, FileType type);

    File inputFile(File fileInput) throws IOException;

    File inputFile(File fileInput, boolean forceCopy) throws IOException;

    String getRelativePath(File fileWork) throws IOException;

    void outputFile(File fileWork) throws IOException;

    void outputFile(File fileWork, String nameDest) throws IOException;

    void discardFile(File fileWork) throws IOException;

    void remove() throws IOException;
}
