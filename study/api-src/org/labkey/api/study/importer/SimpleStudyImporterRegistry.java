/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.study.importer;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class SimpleStudyImporterRegistry
{
    private static final Collection<Supplier<Collection<SimpleStudyImporter>>> IMPORTER_SUPPLIERS = new CopyOnWriteArrayList<>();

    public static void registerSimpleStudyImporterProvider(Supplier<Collection<SimpleStudyImporter>> provider)
    {
        IMPORTER_SUPPLIERS.add(provider);
    }

    public static Collection<SimpleStudyImporter> getSimpleStudyImporters()
    {
        // New up the writers every time since these classes can be stateful
        return IMPORTER_SUPPLIERS.stream()
            .map(Supplier::get)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }
}
