/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.study.Dataset;
import org.labkey.data.xml.reportProps.PropertyList;
import org.labkey.study.model.DatasetDefinition;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: May 26, 2009
 * Time: 10:14:08 AM
 */

// This is an ugly but relatively quick way to add support for the new dataset_metadata.xml format while still
// maintaining compatibility with schema.tsv.
//
public interface SchemaReader
{
    OntologyManager.ImportPropertyDescriptorsList getImportPropertyDescriptors(DomainURIFactory factory, Collection<String> errors, Container defaultContainer);

    Map<Integer, DatasetImportInfo> getDatasetInfo();
    String getTypeNameColumn();

    class DatasetImportInfo
    {
        public DatasetImportInfo(String name)
        {
            this.name = name;
        }
        public String name;
        public String label;
        public String description;
        public String visitDatePropertyName;
        public String startDatePropertyName;
        public boolean isHidden;
        public String keyPropertyName;
        public DatasetDefinition.KeyManagementType keyManagementType = Dataset.KeyManagementType.None;
        public String category;
        public boolean demographicData;
        public String type = Dataset.TYPE_STANDARD;
        public PropertyList tags;
        public String tag;
        public Set<PropertyStorageSpec.Index> indices = new HashSet<>();
        public boolean useTimeKeyField;
    }
}
