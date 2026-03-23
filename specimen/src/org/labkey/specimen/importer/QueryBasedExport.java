package org.labkey.specimen.importer;

import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.specimen.actions.SpecimenController.ConfigureQueryImportAction;
import org.labkey.vfs.FileLike;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

import java.io.IOException;
import java.util.List;

public class QueryBasedExport
{
    private final QueryBasedSpecimenReloadConfig _config;
    private final PipelineJob _job;
    private final FileLike _archive;

    public QueryBasedExport (QueryBasedSpecimenReloadConfig config, PipelineJob job, FileLike archive)
    {
        _config = config;
        _job = job;
        _archive = archive;
    }

    public QueryBasedSpecimenReloadConfig getConfig()
    {
        return _config;
    }

    public void exportRepository() throws PipelineJobException
    {
        _job.info("Starting query-based specimen export");

        User user = _job.getUser();
        Container c = _job.getContainer();
        String schemaName = _config.getSchemaName();
        String queryName = _config.getQueryName();
        String viewName = _config.getViewName();

        // We fake up a ViewContext here as we're in a background job, and and no request-based ViewContext is available
        try (ViewContext.StackResetter reset = ViewContext.pushMockViewContext(user, c, new ActionURL(ConfigureQueryImportAction.class, c)))
        {
            ViewContext viewContext = reset.getContext();

            UserSchema userSchema = QueryService.get().getUserSchema(user, c, schemaName);
            if (null == userSchema)
                throw new PipelineJobException(String.format("Schema %s is either inaccessible or deleted.", schemaName));

            QuerySettings querySettings = new QuerySettings(viewContext, "query", queryName);
            querySettings.setSchemaName(schemaName);
            querySettings.setViewName(viewName);
            querySettings.setShowRows(ShowRows.ALL);

            _job.info(String.format("Requesting data from query '%s' of schema '%s'", queryName, schemaName));
            BindException errors = new NullSafeBindException(new Object(), "form");
            QueryView queryView = userSchema.createView(viewContext, querySettings, errors);

            if (errors.hasErrors())
            {
                List<ObjectError> allErrors = errors.getAllErrors();
                for (ObjectError error : allErrors)
                {
                    _job.error(error.toString());
                }
                return;
            }

            _job.info("creating the exported data .csv file");
            try (TSVGridWriter tsvWriter = queryView.getTsvWriter())
            {
                tsvWriter.setDelimiterCharacter(TSVWriter.DELIM.COMMA);
                tsvWriter.setColumnHeaderType(ColumnHeaderType.DisplayFieldKey);
                tsvWriter.write(_archive.openOutputStream());
                _job.info("finished writing data file: " + _archive.getName());
            }
            catch (IOException e)
            {
                _job.error("Error writing TSV: " + e.getMessage());
            }
        }
        _job.info("Finished writing data file: " + _archive.getName()); // use Format
    }
}
