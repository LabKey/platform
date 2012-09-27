package org.labkey.api.laboratory.assay;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/15/12
 * Time: 7:29 AM
 */
public class DefaultAssayImportMethod implements AssayImportMethod
{
    public static final String NAME = "Default Excel";

    public DefaultAssayImportMethod()
    {
    }

    public AssayParser getFileParser(Container c, User u, int assayId, JSONObject formData)
    {
        return new DefaultAssayParser(this, c, u, assayId, formData);
    }

    public String getName()
    {
        return NAME;
    }

    public String getLabel()
    {
        return "Default Excel Upload";
    }

    public List<String> getSkippedBatchFields()
    {
        return Collections.emptyList();
    }

    public List<String> getSkippedRunFields()
    {
        return Collections.emptyList();
    }

    public List<String> getSkippedResultFields()
    {
        return Collections.emptyList();
    }

    public List<String> getAdditionalFields()
    {
        return Collections.emptyList();
    }

    public List<String> getPromotedResultFields()
    {
        return Collections.emptyList();
    }

    public boolean hideTemplateDownload()
    {
        return false;
    }

    public String getTooltip()
    {
        return "Choose this option to upload data using the basic, non-instrument specific excel template";
    }

    public boolean doEnterResultsInGrid()
    {
        return false;
    }

    public String getExampleDataUrl(ViewContext ctx)
    {
        return null;
    }

    public String getTemplateInstructions()
    {
        return null;
    }

    public JSONObject getMetadata(ViewContext ctx)
    {
        return null;
    }

    public String getPreviewPanelClass()
    {
        return "Laboratory.ext.AssayPreviewPanel";
    }

    public JSONObject toJson(ViewContext ctx)
    {
        JSONObject json = new JSONObject();
        json.put("name", getName());
        json.put("label", getLabel());
        json.put("skippedBatchFields", getSkippedBatchFields());
        json.put("skippedRunFields", getSkippedRunFields());
        json.put("skippedResultFields", getSkippedResultFields());
        json.put("additionalFields", getAdditionalFields());
        json.put("promotedResultFields", getPromotedResultFields());
        json.put("hideTemplateDownload", hideTemplateDownload());
        json.put("tooltip", getTooltip());
        json.put("enterResultsInGrid", doEnterResultsInGrid());

        json.put("exampleDataUrl", getExampleDataUrl(ctx));
        json.put("templateInstructions", getTemplateInstructions());
        json.put("previewPanelClass", getPreviewPanelClass());
        json.put("metadata", getMetadata(ctx));

        return json;
    }
}
