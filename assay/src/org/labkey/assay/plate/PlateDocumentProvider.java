package org.labkey.assay.plate;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.data.Container;
import org.labkey.api.exp.Lsid;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.util.JsonUtil;
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

public class PlateDocumentProvider implements SearchService.DocumentProvider
{
    @Override
    public void enumerateDocuments(SearchService.IndexTask task, @NotNull Container c, @Nullable Date modifiedSince)
    {
        Runnable runEnumerate = () -> PlateManager.get().indexPlates(task, c, modifiedSince);
        task.addRunnable(runEnumerate, SearchService.PRIORITY.group);
    }

    private static SearchService.SearchCategory getSearchCategory()
    {
        return PlateManager.get().PLATE_CATEGORY;
    }

    private static String getDocumentIdPrefix()
    {
        return getSearchCategory().getName() + ":";
    }

    public static String getDocumentId(@NotNull Lsid plateLsid)
    {
        return getDocumentIdPrefix() + plateLsid;
    }

    public static String getDocumentId(@NotNull Plate plate)
    {
        return getDocumentId(new Lsid(plate.getLSID()));
    }

    public static WebdavResource createDocument(@NotNull Plate plate)
    {
        Map<String, Object> props = new HashMap<>();
        Set<String> identifiersHi = new HashSet<>();
        Set<String> identifiersMed = new HashSet<>();
        final String documentId = getDocumentId(plate);

        identifiersHi.add(plate.getName());
        identifiersMed.add(plate.getLSID());

        props.put(SearchService.PROPERTY.identifiersHi.toString(), StringUtils.join(identifiersHi, " "));
        props.put(SearchService.PROPERTY.identifiersMed.toString(), StringUtils.join(identifiersMed, " "));
        props.put(SearchService.PROPERTY.keywordsLo.toString(), "Plate");
        props.put(SearchService.PROPERTY.categories.toString(), getSearchCategory().getName());
        props.put(SearchService.PROPERTY.title.toString(), "Plate - " + plate.getName());

        StringBuilder body = new StringBuilder();

        PlateType plateType = plate.getPlateType();
        if (plateType != null)
            append(body, plateType.getDescription());

        ActionURL url = plate.detailsURL();
        if (url != null)
            url.setExtraPath(plate.getContainer().getId());

        return new SimpleDocumentResource(
            new Path(documentId),
            documentId,
            plate.getContainer().getId(),
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

            private @Nullable Plate getPlate(@NotNull String resourceIdentifier)
            {
                Lsid lsid = fromDocumentId(resourceIdentifier);
                return PlateManager.get().getPlate(lsid);
            }

            @Override
            public WebdavResource resolve(@NotNull String resourceIdentifier)
            {
                Plate plate = getPlate(resourceIdentifier);
                if (plate == null)
                    return null;

                return createDocument(plate);
            }

            @Override
            public Map<String, Object> getCustomSearchJson(User user, @NotNull String resourceIdentifier)
            {
                Plate plate = getPlate(resourceIdentifier);

                try
                {
                    if (plate != null)
                        return serialize(plate);
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
                    Plate plate = getPlate(resourceIdentifier);
                    if (plate != null)
                    {
                        try
                        {
                            results.put(resourceIdentifier, serialize(plate));
                        }
                        catch (JsonProcessingException e)
                        {
                            /* skip it */
                        }
                    }
                }

                return results;
            }

            private Map<String, Object> serialize(@NotNull Plate plate) throws JsonProcessingException
            {
                return new JSONObject(JsonUtil.DEFAULT_MAPPER.writeValueAsString(plate)).toMap();
            }
        };
    }
}
