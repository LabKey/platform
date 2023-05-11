package org.labkey.assay;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayService;
import org.labkey.api.data.Container;
import org.labkey.api.exp.Identifiable;
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

public class AssayRunDocumentProvider implements SearchService.DocumentProvider
{
    @Override
    public void enumerateDocuments(SearchService.IndexTask task, @NotNull Container c, @Nullable Date modifiedSince)
    {
        Runnable runEnumerate = () -> AssayService.get().indexAssayRuns(task, c);
        task.addRunnable(runEnumerate, SearchService.PRIORITY.group);
    }

    public static WebdavResource createDocument(@NotNull ExpRun expRun)
    {
        Map<String, Object> props = new HashMap<>();
        Set<String> identifiersHi = new HashSet<>();
        Set<String> identifiersMed = new HashSet<>();
        final String documentId = AssayService.ASSAY_RUN_CATEGORY.getName() + ":" + expRun.getRowId();

        identifiersHi.add(expRun.getName());
        identifiersMed.add(expRun.getLSID());

        props.put(SearchService.PROPERTY.identifiersHi.toString(), StringUtils.join(identifiersHi, " "));
        props.put(SearchService.PROPERTY.identifiersMed.toString(), StringUtils.join(identifiersMed, " "));
        props.put(SearchService.PROPERTY.categories.toString(), AssayService.ASSAY_RUN_CATEGORY.getName());
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

    private static void append(StringBuilder sb, @Nullable Identifiable identifiable)
    {
        if (null != identifiable)
            append(sb, identifiable.getName());
    }

    private static void append(StringBuilder sb, @Nullable String value)
    {
        if (null != value)
        {
            if (sb.length() > 0)
                sb.append(" ");

            sb.append(value);
        }
    }

    public static SearchService.ResourceResolver getResourceResolver()
    {
        return new SearchService.ResourceResolver()
        {
            private Integer fromDocumentId(@NotNull String resourceIdentifier)
            {
                final String prefix = AssayService.ASSAY_RUN_CATEGORY.getName() + ":";

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

                return ExperimentJSONConverter.serializeRun(expRun, null, user, ExperimentJSONConverter.DEFAULT_SETTINGS).toMap();
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

                ExperimentService.get().getExpRuns(expRunRowIds);
                Map<String, Map<String, Object>> results = new HashMap<>();
                for (ExpRun expRun : ExperimentService.get().getExpRuns(expRunRowIds))
                {
                    results.put(
                        rowIdIdentifierMap.get(expRun.getRowId()),
                        ExperimentJSONConverter.serializeRun(expRun, null, user, ExperimentJSONConverter.DEFAULT_SETTINGS).toMap()
                    );
                }

                return results;
            }
        };
    }
}
