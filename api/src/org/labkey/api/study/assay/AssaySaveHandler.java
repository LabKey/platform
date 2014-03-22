/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.ValidationException;
import org.labkey.api.view.ViewContext;

import java.sql.SQLException;
import java.util.List;

/**
 * User: dax
 * Date: Oct 8, 2013
 *
 * This interface enables file-based assays to extend the functionality of the SaveAssayBatch action with Java code.
 * A file-based assay can provide an implementation of this interface by creating a java based module and then putting
 * the class under the module's src/[module namespace]/assay/[assay name] directory.  This class can then be referenced
 * by name in the <saveHandler/> element in the assay's config file.  For example, a fully-qualified entry might look
 * like <saveHandler>org.labkey.icemr.assay.tracking.TrackingSaveHandler</saveHandler>.  If a non-fully-qualified name
 * is used (for example, TrackingSaveHandler) then LabKey will attempt to find this class under
 * org.labkey.[module name].assay.[assay name].[save handler name].
 *
 * The SaveAssayBatch function creates a new instance of the SaveHandler for each request. SaveAssayBatch will dispatch
 * to the methods of this interface according to the format of the JSON Experiment Batch (or run group) sent to it
 * by the client.  If a client chooses to implement this interface directly then the order of method calls will be:
 *
 * beforeSave
 * handleBatch
 * afterSave
 *
 * A client can also inherit from DefaultAssaySaveHandler class to get a default implementation.  In this case, the
 * default handler does a deep walk through all the runs in a batch, inputs, outputs, materials, and properties.  The
 * sequence of calls for DefaultAssaySaveHandler will be:
 *
 * beforeSave
 * handleBatch
 *      handleProperties (for the batch)
 *      handleRun (for each run)
 *          handleProperties (for the run)
 *          handleProtocolApplications
 *              handleData (for each data output)
 *                  handleProperties (for the data)
 *              handleMaterial (for each input material)
 *                  handleProperties (for the material)
 *              handleMaterial (for each output material)
 *                  handleProperties (for the material)
 * afterSave
 *
 */
public interface AssaySaveHandler
{
    /**
     * convenience functions to access the AssayProvider for this file-based assay.
     */
    void setProvider(AssayProvider provider);
    AssayProvider getProvider();

    /**
     * This function will always be called.  It is invoked by the SaveAssayBatch action.  A custom implementation
     * could choose to handle the entire batch itself without invoking any of the other functions in this interface.
     */
    ExpExperiment handleBatch(ViewContext context, JSONObject batchJson, ExpProtocol protocol) throws Exception;

    /**
     * Handles each run included in a batch.
     * Called from DefaultAssaySaveHandler.handleBatch.
     */
    ExpRun handleRun(ViewContext context, JSONObject runJson, ExpProtocol protocol, ExpExperiment batch)
            throws JSONException, ValidationException, ExperimentException, SQLException;

    /**
     * Handles persistence of each outout data object if included
     * Called from DefaultAssaySaveHandler.handleProtocolApplications
     */
    ExpData handleData(ViewContext context, JSONObject dataJson) throws ValidationException;

    /**
     * Handles persistence of all materials for the run (input or output) if included.
     * Called from DefaultAssaySaveHandler.handleProtocolAplications
     */
    ExpMaterial handleMaterial(ViewContext context, JSONObject materialJson) throws ValidationException;

    /**
     * Enables the implementor to decide how to save the passed in ExpRun.  The default implementation deletes the
     * run and recreates it.  A custom implementation could choose to enforce that only certain aspects of the
     * protocol application change or choose not add its own data for saving.
     * Called from DefaultAssaySaveHandler.handleRun.
     */
    void handleProtocolApplications(ViewContext context, ExpProtocol protocol, ExpExperiment batch, ExpRun run, JSONArray inputDataArray,
            JSONArray dataArray, JSONArray inputMaterialArray, JSONObject runJsonObject, JSONArray outputDataArray,
            JSONArray outputMaterialArray) throws ExperimentException, ValidationException;

    /**
     * Used to handle any properties of an ExpObject.
     * Called from DefaultAssaySaveHandler.handleBatch, handleRun, handleData, and handleMaterial as appropriate.
     */
    void handleProperties(ViewContext context, ExpObject object, List<? extends DomainProperty> dps, JSONObject propertiesJson) throws ValidationException, JSONException;

    /**
     * The JSON object sent to beforeSave is the JSON sent directly from the client.  As long as the client provides a
     * "batch" or "batches" id in the JSON, this object can contain whatever JSON format the client and AssaySaveHandler agree upon
     * Always called by SaveAssayBatchAction.
     */
    void beforeSave(ViewContext context, JSONObject rootJson, ExpProtocol protocol)throws Exception;

    /**
     * The batches will contain all the updates that have been processed including values that are refreshed
     * from the database if needed.  In the case of a single batch, a single element array of batches will
     * be passed.  This method is always called by SaveAssayBatchAction
     */
    void afterSave(ViewContext context, List<? extends ExpExperiment> batches, ExpProtocol protocol) throws Exception;
}
