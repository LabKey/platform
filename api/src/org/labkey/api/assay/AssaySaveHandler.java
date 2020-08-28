/*
 * Copyright (c) 2013-2019 LabKey Corporation
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

package org.labkey.api.assay;

import org.labkey.api.exp.api.ExperimentSaveHandler;

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
public interface AssaySaveHandler extends ExperimentSaveHandler
{
    /**
     * convenience functions to access the AssayProvider for this file-based assay.
     */
    void setProvider(AssayProvider provider);
    AssayProvider getProvider();
}
