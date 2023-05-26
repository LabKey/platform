package org.labkey.assay;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
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
    public void enumerateDocuments(SearchService.IndexTask task, @NotNull Container c, @Nullable Date modifiedSince)
    {
        Runnable runEnumerate = () -> AssayManager.get().indexAssayBatches(task, c, modifiedSince);
        task.addRunnable(runEnumerate, SearchService.PRIORITY.group);
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
            batch.getContainer().getId(),
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

                int batchId;
                try
                {
                    batchId = Integer.parseInt(resourceIdentifier);
                }
                catch (NumberFormatException e)
                {
                    return null;
                }

                return batchId;
            }

            private @Nullable ExpExperiment getBatch(@NotNull String resourceIdentifier)
            {
                Integer batchRowId = fromDocumentId(resourceIdentifier);
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
                Set<Integer> batchRowIds = new HashSet<>();
                Map<Integer, String> rowIdIdentifierMap = new HashMap<>();
                for (String resourceIdentifier : resourceIdentifiers)
                {
                    Integer batchRowId = fromDocumentId(resourceIdentifier);
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
