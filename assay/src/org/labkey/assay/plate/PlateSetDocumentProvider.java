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
package org.labkey.assay.plate;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResource;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.StringUtilsLabKey.append;

public class PlateSetDocumentProvider implements SearchService.DocumentProvider
{
    @Override
    public void enumerateDocuments(SearchService.TaskIndexingQueue queue, @Nullable Date modifiedSince)
    {
        queue.addRunnable((q) -> PlateManager.get().indexPlateSets(q, modifiedSince));
    }

    @Override
    public void indexDeleted() throws SQLException
    {
        SearchService.DocumentProvider.super.indexDeleted();
    }

    private static SearchService.SearchCategory getSearchCategory()
    {
        return PlateManager.get().PLATE_SET_CATEGORY;
    }

    private static String getDocumentIdPrefix()
    {
        return getSearchCategory().getName() + ":";
    }

    public static String getDocumentId(@NotNull PlateSet plateSet)
    {
        return getDocumentId(plateSet.getContainer(), plateSet.getRowId());
    }

    public static String getDocumentId(@NotNull Container container, long plateSetRowId)
    {
        return getDocumentIdPrefix() + container.getId() + ":" + plateSetRowId;
    }

    public static WebdavResource createDocument(@NotNull PlateSet plateSet)
    {
        Map<String, Object> props = new HashMap<>();
        Set<String> identifiersHi = new HashSet<>();
        Set<String> identifiersMed = new HashSet<>();
        final String documentId = getDocumentId(plateSet);

        identifiersHi.add(plateSet.getName());
        identifiersHi.add(plateSet.getPlateSetId());

        props.put(SearchService.PROPERTY.identifiersHi.toString(), StringUtils.join(identifiersHi, " "));
        props.put(SearchService.PROPERTY.identifiersMed.toString(), StringUtils.join(identifiersMed, " "));
        props.put(SearchService.PROPERTY.keywordsLo.toString(), "PlateSet");
        props.put(SearchService.PROPERTY.categories.toString(), getSearchCategory().getName());
        props.put(SearchService.PROPERTY.title.toString(), "Plate Set - " + plateSet.getName());

        ActionURL url = plateSet.detailsURL();
        if (url != null)
            url.setExtraPath(plateSet.getContainer().getId());

        StringBuilder body = new StringBuilder();

        if (plateSet.getDescription() != null)
            append(body, plateSet.getDescription());

        return new SimpleDocumentResource(
            new Path(documentId),
            documentId,
            plateSet.getContainer().getEntityId(),
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
            private @Nullable PlateSet getPlateSet(@NotNull String resourceIdentifier)
            {
                final String prefix = getDocumentIdPrefix();

                if (resourceIdentifier.startsWith(prefix))
                    resourceIdentifier = resourceIdentifier.substring(prefix.length());

                String[] parts = resourceIdentifier.split(":");
                if (parts.length != 2)
                    return null;

                int rowId;
                try
                {
                    rowId = Integer.parseInt(parts[1]);
                }
                catch (NumberFormatException e)
                {
                    // skip it
                    return null;
                }

                Container container = ContainerManager.getForId(parts[0]);
                if (container == null)
                    return null;

                return PlateManager.get().getPlateSet(container, rowId);
            }

            @Override
            public WebdavResource resolve(@NotNull String resourceIdentifier)
            {
                PlateSet plateSet = getPlateSet(resourceIdentifier);
                if (plateSet == null)
                    return null;

                return createDocument(plateSet);
            }

            @Override
            public Map<String, Object> getCustomSearchJson(User user, @NotNull String resourceIdentifier)
            {
                PlateSet plateSet = getPlateSet(resourceIdentifier);

                try
                {
                    if (plateSet != null)
                        return serialize(plateSet);
                }
                catch (JsonProcessingException e)
                {
                    /* skip it */
                }

                return null;
            }

            @Override
            public Map<String, Map<String, Object>> getCustomSearchJsonMap(User user, @NotNull Collection<String> resourceIdentifiers)
            {
                Map<String, Map<String, Object>> results = new HashMap<>();
                for (String resourceIdentifier : resourceIdentifiers)
                {
                    PlateSet plateSet = getPlateSet(resourceIdentifier);
                    if (plateSet != null)
                    {
                        try
                        {
                            results.put(resourceIdentifier, serialize(plateSet));
                        }
                        catch (JsonProcessingException e)
                        {
                            /* skip it */
                        }
                    }
                }

                return results;
            }

            private Map<String, Object> serialize(@NotNull PlateSet plateSet) throws JsonProcessingException
            {
                JSONObject json = new JSONObject(JsonUtil.DEFAULT_MAPPER.writeValueAsString(plateSet));
                // Skip serializing plates into search results
                json.remove("plates");

                return json.toMap();
            }
        };
    }
}
