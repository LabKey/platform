/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

package gwt.client.org.labkey.study.dataset.client;

import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.LookupService;
import gwt.client.org.labkey.study.dataset.client.model.GWTDataset;

import java.util.List;

/**
 * User: matthewb
 * Date: Apr 26, 2007
 * Time: 1:34:56 PM
 */
public interface DatasetService extends LookupService
{
    GWTDataset getDataset(int id);

    /**
     * @param ds  Dataset this domain belongs to
     * @param orig Unchanged domain
     * @param dd New Domain
     * @return List of errors
     */
    List<String> updateDatasetDefinition(GWTDataset ds, GWTDomain orig, GWTDomain dd);
    GWTDomain getDomainDescriptor(String typeURI);
}
