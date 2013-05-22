/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.study.SpecimenImportStrategy;
import org.labkey.study.importer.SpecimenImporter.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/*
* User: adam
* Date: Feb 16, 2013
* Time: 7:35:23 AM
*/
public class IteratorSpecimenImportFile implements SpecimenImportFile
{
    private final SpecimenImportStrategy _strategy;
    private final List<Map<String, Object>> _rows;
    private final SpecimenTableType _tableType;

    public IteratorSpecimenImportFile(List<Map<String, Object>> rows, SpecimenImportStrategy strategy, SpecimenTableType tableType)
    {
        _strategy = strategy;
        _rows = rows;
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
    public DataLoader getDataLoader() throws IOException
    {
        return new MapLoader(_rows);
    }
}
