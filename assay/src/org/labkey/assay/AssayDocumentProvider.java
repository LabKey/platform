/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
package org.labkey.assay;

import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.assay.AssayService;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchService.DocumentProvider;
import org.labkey.api.security.User;

import java.util.Date;
import java.util.Map;

public class AssayDocumentProvider implements DocumentProvider
{
    @Override
    public void enumerateDocuments(SearchService.TaskIndexingQueue queue, @Nullable Date modifiedSince)
    {
        queue.addRunnable((q) -> AssayService.get().indexAssays(q));
    }

    public static SearchService.ResourceResolver getSearchResolver()
    {
        return new SearchService.ResourceResolver()
        {
            @Override
            public Map<String, Object> getCustomSearchJson(User user, @NotNull String resourceIdentifier)
            {
                int rowId = NumberUtils.toInt(resourceIdentifier);
                if (rowId == 0)
                    return null;

                ExpProtocol assayProtocol = ExperimentService.get().getExpProtocol(rowId);
                if (assayProtocol == null)
                    return null;

                JSONObject jsonObject = new JSONObject();

                jsonObject.put("createdBy", assayProtocol.getCreatedBy().getEmail());
                jsonObject.put("created", assayProtocol.getCreated());
                jsonObject.put("modifiedBy", assayProtocol.getModifiedBy().getEmail());
                jsonObject.put("modified", assayProtocol.getModified());

                return jsonObject.toMap();
            }
        };
    }
}
