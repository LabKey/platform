package org.labkey.api.assay.dilution;

import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.study.actions.PlateUploadForm;
import org.labkey.api.study.assay.PlateBasedAssayProvider;
import org.labkey.api.view.ActionURL;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 5/6/13
 */
public interface DilutionAssayProvider extends PlateBasedAssayProvider
{
    public static final String[] CUTOFF_PROPERTIES = { "Cutoff1", "Cutoff2", "Cutoff3" };
    public static final String SAMPLE_METHOD_PROPERTY_NAME = "Method";
    public static final String SAMPLE_METHOD_PROPERTY_CAPTION = "Method";
    public static final String SAMPLE_INITIAL_DILUTION_PROPERTY_NAME = "InitialDilution";
    public static final String SAMPLE_INITIAL_DILUTION_PROPERTY_CAPTION = "Initial Dilution";
    public static final String SAMPLE_DILUTION_FACTOR_PROPERTY_NAME = "Factor";
    public static final String SAMPLE_DILUTION_FACTOR_PROPERTY_CAPTION = "Dilution Factor";
    public static final String SAMPLE_DESCRIPTION_PROPERTY_NAME = "SampleDescription";
    public static final String SAMPLE_DESCRIPTION_PROPERTY_CAPTION = "Sample Description";
    public static final String CURVE_FIT_METHOD_PROPERTY_NAME = "CurveFitMethod";
    public static final String CURVE_FIT_METHOD_PROPERTY_CAPTION = "Curve Fit Method";
    public static final String LOCK_AXES_PROPERTY_NAME = "LockYAxis";
    public static final String LOCK_AXES_PROPERTY_CAPTION = "Lock Graph Y-Axis";

    DilutionDataHandler getDataHandler();
    ActionURL getUploadWizardCompleteURL(PlateUploadForm<DilutionAssayProvider> form, ExpRun run);
}
