/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;

import java.util.List;

/**
 * User: bimber
 * Date: 9/15/12
 * Time: 2:18 PM
 */
public interface AssayImportMethod
{
    abstract public String getName();

    /**
     * The display label
     * @return
     */
    abstract public String getLabel();

    /**
     * If true, no link to download an excel template will appear on the result upload page.  Defaults to false
     * @return
     */
    abstract public boolean hideTemplateDownload();

    /**
     * @return A tooltip used for the radio button
     */
    abstract public String getTooltip();

    /**
     * controls whether file content area shows.  otherwise this would be a web form only.  defulats to false
     * @return
     */
    abstract public boolean doEnterResultsInGrid();

    /**
     * URL to a to file with example data
     * @return
     */
    abstract public String getExampleDataUrl(ViewContext ctx);

    /**
     * instructions that will be displayed above the results area
     * @return
     */
    abstract public String getTemplateInstructions();

    /**
     * The name of an Ext class that will render the results preview.  Defaults to 'Laboratory.ext.AssayPreviewPanel'
     * @return
     */
    abstract public String getPreviewPanelClass();

    /**
     * A metadata config object that will be applied to the fields on the import page
     * @return
     */
    abstract public JSONObject getMetadata(ViewContext ctx, ExpProtocol protocol);

    /**
     * Serialized this import method to JSON, which is consumed by the client
     * @return
     */
    abstract public JSONObject toJson(ViewContext ctx, ExpProtocol protocol);

    /**
     * Returns the AssayParser used to process data imported through this pathway.
     * @return
     */
    abstract public AssayParser getFileParser(Container c, User u, int assayId);

    /**
     * Returns true if this import method supports assay run templates, which allow the user to proactively
     * upload sample metadata and create an export to be read directly into an instrument
     * @return
     */
    abstract public boolean supportsRunTemplates();

    abstract public String getProviderName();

    abstract public void generateTemplate(ViewContext ctx, ExpProtocol protocol, @Nullable Integer templateId, String title, JSONObject json) throws BatchValidationException;

    abstract public void validateTemplate(User u, Container c, ExpProtocol protocol, @Nullable Integer templateId, String title, JSONObject json, BatchValidationException errors) throws BatchValidationException;

    abstract public List<String> getImportColumns(ViewContext ctx, ExpProtocol protocol);

    abstract public JSONObject getSupplementalTemplateMetadata();
}
