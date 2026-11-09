/*
 * Copyright (c) 2023-2026 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.LongHashMap;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResource;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.StringUtilsLabKey.append;

public class AssayBatchDocumentProvider implements SearchService.DocumentProvider
{
    @Override
    public void enumerateDocuments(SearchService.TaskIndexingQueue queue, @Nullable Date modifiedSince)
    {
        queue.addRunnable((q) -> AssayManager.get().indexAssayBatches(q, modifiedSince));
    }

    private static SearchService.SearchCategory getSearchCategory()
    {
        return AssayManager.get().ASSAY_BATCH_CATEGORY;
    }

    private static String getDocumentIdPrefix()
    {
        return getSearchCategory().getName() + ":";
    }

    public static String getDocumentId(@NotNull ExpExperiment batch)
    {
        return getDocumentIdPrefix() + batch.getRowId();
    }

    public static WebdavResource createDocument(@NotNull ExpExperiment batch)
    {
        Map<String, Object> props = new HashMap<>();
        Set<String> identifiersHi = new HashSet<>();
        Set<String> identifiersMed = new HashSet<>();
        final String documentId = getDocumentId(batch);

        identifiersHi.add(batch.getName());
        identifiersMed.add(batch.getLSID());

        props.put(SearchService.PROPERTY.identifiersHi.toString(), StringUtils.join(identifiersHi, " "));
        props.put(SearchService.PROPERTY.identifiersMed.toString(), StringUtils.join(identifiersMed, " "));
        props.put(SearchService.PROPERTY.keywordsLo.toString(), "Batch");
        props.put(SearchService.PROPERTY.categories.toString(), getSearchCategory().getName());
        props.put(SearchService.PROPERTY.title.toString(), "Assay Batch - " + batch.getName());

        StringBuilder body = new StringBuilder();
        append(body, batch.getComments());
        append(body, batch.getBatchProtocol());

        ActionURL url = batch.detailsURL();
        if (url != null)
            url.setExtraPath(batch.getContainer().getId());

        return new SimpleDocumentResource(
            new Path(documentId),
            documentId,
            batch.getContainer().getEntityId(),
            "text/plain",
            body.toString(),
            url,
            props
        );
    }

    public static SearchService.ResourceResolver getResourceResolver()
    {
        return new SearchService.ResourceResolver()
        {
            private @Nullable Long fromDocumentId(@NotNull String resourceIdentifier)
            {
                final String prefix = getDocumentIdPrefix();

                if (resourceIdentifier.startsWith(prefix))
                    resourceIdentifier = resourceIdentifier.substring(prefix.length());

                long batchId;
                try
                {
                    batchId = Long.parseLong(resourceIdentifier);
                }
                catch (NumberFormatException e)
                {
                    return null;
                }

                return batchId;
            }

            private @Nullable ExpExperiment getBatch(@NotNull String resourceIdentifier)
            {
                Long batchRowId = fromDocumentId(resourceIdentifier);
                if (batchRowId == null)
                    return null;

                return ExperimentService.get().getExpExperiment(batchRowId);
            }

            @Override
            public WebdavResource resolve(@NotNull String resourceIdentifier)
            {
                ExpExperiment batch = getBatch(resourceIdentifier);
                if (batch == null)
                    return null;

                return createDocument(batch);
            }

            @Override
            public Map<String, Object> getCustomSearchJson(User user, @NotNull String resourceIdentifier)
            {
                ExpExperiment batch = getBatch(resourceIdentifier);
                if (batch == null)
                    return null;

                return serialize(batch, user);
            }

            @Override
            public Map<String, Map<String, Object>> getCustomSearchJsonMap(User user, @NotNull Collection<String> resourceIdentifiers)
            {
                Set<Long> batchRowIds = new HashSet<>();
                Map<Long, String> rowIdIdentifierMap = new LongHashMap<>();
                for (String resourceIdentifier : resourceIdentifiers)
                {
                    Long batchRowId = fromDocumentId(resourceIdentifier);
                    if (batchRowId != null)
                    {
                        batchRowIds.add(batchRowId);
                        rowIdIdentifierMap.put(batchRowId, resourceIdentifier);
                    }
                }

                Map<String, Map<String, Object>> results = new HashMap<>();
                for (ExpExperiment batch : ExperimentService.get().getExpExperiments(batchRowIds))
                {
                    results.put(rowIdIdentifierMap.get(batch.getRowId()), serialize(batch, user));
                }

                return results;
            }

            private Map<String, Object> serialize(@NotNull ExpExperiment batch, User user)
            {
                return ExperimentJSONConverter.serialize(batch, user, ExperimentJSONConverter.DEFAULT_SETTINGS).toMap();
            }
        };
    }
}
