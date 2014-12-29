/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

package org.labkey.study.importer;

import org.labkey.api.reader.TabLoader;
import org.labkey.api.study.SpecimenImportStrategy;
import org.labkey.api.util.Filter;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.importer.SpecimenImporter.SpecimenTableType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;

/*
* User: adam
* Date: Feb 10, 2013
* Time: 5:16:12 PM
*/
public class FileSystemSpecimenImportFile implements SpecimenImportFile
{
    private final VirtualFile _dir;
    private final String _fileName;
    private final SpecimenImportStrategy _strategy;
    private final SpecimenTableType _tableType;

    public FileSystemSpecimenImportFile(VirtualFile dir, String fileName, SpecimenImportStrategy strategy, SpecimenTableType tableType)
    {
        _dir = dir;
        _fileName = fileName;
        _strategy = strategy;
        _tableType = tableType;
    }

    @Override
    public SpecimenTableType getTableType()
    {
        return _tableType;
    }

    @Override
    public SpecimenImportStrategy getStrategy()
    {
        return _strategy;
    }

    @Override
    public TabLoader getDataLoader() throws IOException
    {
        Reader reader = new InputStreamReader(getInputStream());
        TabLoader loader = new TabLoader(reader, true, null, true);   // Close on complete
        loader.setInferTypes(false);

        Filter<Map<String, Object>> filter = getStrategy().getImportFilter();

        if (null != filter)
            loader.setMapFilter(filter);

        return loader;
    }

    private InputStream getInputStream() throws IOException
    {
        return _dir.getInputStream(_fileName);
    }
}
