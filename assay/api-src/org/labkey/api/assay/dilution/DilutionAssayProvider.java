/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.assay.dilution;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.assay.plate.PlateBasedAssayProvider;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

/**
 * User: klum
 * Date: 5/6/13
 */
public interface DilutionAssayProvider<FormType extends DilutionRunUploadForm> extends PlateBasedAssayProvider
{
    String[] CUTOFF_PROPERTIES = { "Cutoff1", "Cutoff2", "Cutoff3" };
    String SAMPLE_METHOD_PROPERTY_NAME = "Method";
    String SAMPLE_METHOD_PROPERTY_CAPTION = "Method";
    String SAMPLE_INITIAL_DILUTION_PROPERTY_NAME = "InitialDilution";
    String SAMPLE_INITIAL_DILUTION_PROPERTY_CAPTION = "Initial Dilution";
    String SAMPLE_DILUTION_FACTOR_PROPERTY_NAME = "Factor";
    String SAMPLE_DILUTION_FACTOR_PROPERTY_CAPTION = "Dilution Factor";
    String SAMPLE_DESCRIPTION_PROPERTY_NAME = "SampleDescription";
    String SAMPLE_DESCRIPTION_PROPERTY_CAPTION = "Sample Description";
    String CURVE_FIT_METHOD_PROPERTY_NAME = "CurveFitMethod";
    String CURVE_FIT_METHOD_PROPERTY_CAPTION = "Curve Fit Method";
    String LOCK_AXES_PROPERTY_NAME = "LockYAxis";
    String LOCK_AXES_PROPERTY_CAPTION = "Lock Graph Y-Axis";

    DilutionDataHandler getDataHandler();
    ActionURL getUploadWizardCompleteURL(FormType form, ExpRun run);

    @Nullable
    default ActionURL getAssayQCRunURL(ViewContext context, ExpRun run)
    {
        return null;
    }
}
