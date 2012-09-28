/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.laboratory.assay;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
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
        JSONObject meta = new JSONObject();

        JSONObject batchMeta = new JSONObject();
        JSONObject importMethod = new JSONObject();
        importMethod.put("getInitialValue", "function(panel){if(panel.selectedMethod) {return panel.selectedMethod.name;}}");
        batchMeta.put("importMethod", importMethod);
        meta.put("Batch", batchMeta);

        JSONObject runMeta = new JSONObject();

        SimpleDateFormat format = new SimpleDateFormat("yyyy-mm-dd");
        runMeta.put("Name", new JSONObject().put("value", ctx.getUser().getDisplayName(ctx.getUser()) + "_" + format.format(new Date())));

        JSONObject runDate = new JSONObject();
        runDate.put("getInitialValue", "function(){return new Date().format('Y-m-d');}");
        runDate.put("extFormat", "Y-m-d");
        runMeta.put("performedBy", new JSONObject().put("value", ctx.getUser().getDisplayName(ctx.getUser())));
        runMeta.put("runDate", runDate);
        runMeta.put("comments", new JSONObject().put("height", 100));
        meta.put("Run", runMeta);

        JSONObject resultsMeta = new JSONObject();
        resultsMeta.put("sourceMaterial", new JSONObject().put("value", "Plasma"));
        resultsMeta.put("sampleType", new JSONObject().put("value", "vRNA"));
        resultsMeta.put("sampleId", new JSONObject().put("lookups", false));
        resultsMeta.put("subjectId", new JSONObject().put("lookups", false));
        meta.put("Results", resultsMeta);

        return meta;
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
