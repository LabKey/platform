package org.labkey.assay.plate;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.data.Container;
import org.labkey.api.exp.Lsid;
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

public class PlateSetDocumentProvider  implements SearchService.DocumentProvider
{
    @Override
    public void enumerateDocuments(SearchService.IndexTask task, @NotNull Container c, @Nullable Date modifiedSince)
    {
        Runnable runEnumerate = () -> PlateManager.get().indexPlateSets(task, c, modifiedSince);
        task.addRunnable(runEnumerate, SearchService.PRIORITY.group);
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

    public static String getDocumentId(@NotNull Lsid plateSetLsid)
    {
        return getDocumentIdPrefix() + plateSetLsid;
    }

    public static String getDocumentId(@NotNull PlateSet plateSet)
    {
        return getDocumentId(new Lsid(plateSet.getLSID()));
    }

    public static WebdavResource createDocument(@NotNull PlateSet plateSet)
    {
        Map<String, Object> props = new HashMap<>();
        Set<String> identifiersHi = new HashSet<>();
        Set<String> identifiersMed = new HashSet<>();
        final String documentId = getDocumentId(plateSet);

        identifiersHi.add(plateSet.getName());
        identifiersHi.add(plateSet.getPlateSetId());
        identifiersMed.add(plateSet.getLSID());

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
            append(body, plateSet.getDescription()); // PR Flag: what should the summary display?

        return new SimpleDocumentResource(
                new Path(documentId),
                documentId,
                plateSet.getContainer().getId(),
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
            private Lsid fromDocumentId(@NotNull String resourceIdentifier)
            {
                final String prefix = getDocumentIdPrefix();

                if (resourceIdentifier.startsWith(prefix))
                    resourceIdentifier = resourceIdentifier.substring(prefix.length());

                return Lsid.parse(resourceIdentifier);
            }

            private @Nullable PlateSet getPlateSet(@NotNull String resourceIdentifier)
            {
                Lsid id = fromDocumentId(resourceIdentifier);
                return PlateManager.get().getPlateSet(id.toString()); // todo flag, it's not an LSID
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
                return new JSONObject(JsonUtil.DEFAULT_MAPPER.writeValueAsString(plateSet)).toMap();
            }
        };
    }
}
