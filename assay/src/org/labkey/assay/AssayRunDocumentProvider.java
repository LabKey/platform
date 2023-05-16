package org.labkey.assay;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpRun;
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

public class AssayRunDocumentProvider implements SearchService.DocumentProvider
{
    @Override
    public void enumerateDocuments(SearchService.IndexTask task, @NotNull Container c, @Nullable Date modifiedSince)
    {
        Runnable runEnumerate = () -> AssayManager.get().indexAssayRuns(task, c, modifiedSince);
        task.addRunnable(runEnumerate, SearchService.PRIORITY.group);
    }

    private static SearchService.SearchCategory getSearchCategory()
    {
        return AssayManager.get().ASSAY_RUN_CATEGORY;
    }

    private static String getDocumentIdPrefix()
    {
        return getSearchCategory().getName() + ":";
    }

    public static String getDocumentId(@NotNull ExpRun expRun)
    {
        return getDocumentIdPrefix() + expRun.getRowId();
    }

    public static WebdavResource createDocument(@NotNull ExpRun expRun)
    {
        Map<String, Object> props = new HashMap<>();
        Set<String> identifiersHi = new HashSet<>();
        Set<String> identifiersMed = new HashSet<>();
        final String documentId = getDocumentId(expRun);

        identifiersHi.add(expRun.getName());
        identifiersMed.add(expRun.getLSID());

        props.put(SearchService.PROPERTY.identifiersHi.toString(), StringUtils.join(identifiersHi, " "));
        props.put(SearchService.PROPERTY.identifiersMed.toString(), StringUtils.join(identifiersMed, " "));
        props.put(SearchService.PROPERTY.categories.toString(), getSearchCategory().getName());
        props.put(SearchService.PROPERTY.title.toString(), "Assay Run - " + expRun.getName());

        StringBuilder body = new StringBuilder();
        append(body, expRun.getComments());
        append(body, expRun.getProtocol());

        ActionURL url = expRun.detailsURL();
        if (url != null)
            url.setExtraPath(expRun.getContainer().getId());

        return new SimpleDocumentResource(
            new Path(documentId),
            documentId,
            expRun.getContainer().getId(),
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
            private Integer fromDocumentId(@NotNull String resourceIdentifier)
            {
                final String prefix = getDocumentIdPrefix();

                if (resourceIdentifier.startsWith(prefix))
                    resourceIdentifier = resourceIdentifier.substring(prefix.length());

                int expRunId;
                try
                {
                    expRunId = Integer.parseInt(resourceIdentifier);
                }
                catch (NumberFormatException e)
                {
                    return null;
                }

                return expRunId;
            }

            private @Nullable ExpRun getExpRun(@NotNull String resourceIdentifier)
            {
                Integer expRunRowId = fromDocumentId(resourceIdentifier);
                if (expRunRowId == null)
                    return null;

                return ExperimentService.get().getExpRun(expRunRowId);
            }

            @Override
            public WebdavResource resolve(@NotNull String resourceIdentifier)
            {
                ExpRun expRun = getExpRun(resourceIdentifier);
                if (expRun == null)
                    return null;

                return createDocument(expRun);
            }

            @Override
            public Map<String, Object> getCustomSearchJson(User user, @NotNull String resourceIdentifier)
            {
                ExpRun expRun = getExpRun(resourceIdentifier);
                if (expRun == null)
                    return null;

                return serialize(expRun, user);
            }

            @Override
            public Map<String, Map<String, Object>> getCustomSearchJsonMap(User user, @NotNull Collection<String> resourceIdentifiers)
            {
                Set<Integer> expRunRowIds = new HashSet<>();
                Map<Integer, String> rowIdIdentifierMap = new HashMap<>();
                for (String resourceIdentifier : resourceIdentifiers)
                {
                    Integer expRunRowId = fromDocumentId(resourceIdentifier);
                    if (expRunRowId != null)
                    {
                        expRunRowIds.add(expRunRowId);
                        rowIdIdentifierMap.put(expRunRowId, resourceIdentifier);
                    }
                }

                Map<String, Map<String, Object>> results = new HashMap<>();
                for (ExpRun expRun : ExperimentService.get().getExpRuns(expRunRowIds))
                {
                    results.put(rowIdIdentifierMap.get(expRun.getRowId()), serialize(expRun, user));
                }

                return results;
            }

            private Map<String, Object> serialize(@NotNull ExpRun expRun, User user)
            {
                return ExperimentJSONConverter.serialize(expRun, user, ExperimentJSONConverter.DEFAULT_SETTINGS).toMap();
            }
        };
    }
}
