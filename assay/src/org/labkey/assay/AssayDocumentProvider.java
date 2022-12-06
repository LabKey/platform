package org.labkey.assay;

import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.assay.AssayService;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchService.DocumentProvider;
import org.labkey.api.search.SearchService.IndexTask;
import org.labkey.api.security.User;

import java.util.Date;
import java.util.Map;

public class AssayDocumentProvider implements DocumentProvider
{
    @Override
    public void enumerateDocuments(IndexTask task, @NotNull Container c, @Nullable Date modifiedSince)
    {
        Runnable runEnumerate = () -> AssayService.get().indexAssays(task, c);
        task.addRunnable(runEnumerate, SearchService.PRIORITY.group);
    }

    @Override
    public void indexDeleted()
    {
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

                return jsonObject;
            }
        };
    }


}
