/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.api.gwt.client.assay;

/**
 * User: jeckels
 * Date: Apr 13, 2009
 */
public class SampleChooserUtils
{
    public static final String SAMPLE_COUNT_ELEMENT_NAME = "__sampleCount";

    public static final String PROP_NAME_MAX_SAMPLE_COUNT = "maxSampleCount";
    public static final String PROP_NAME_MIN_SAMPLE_COUNT = "minSampleCount";
    public static final String PROP_NAME_DEFAULT_SAMPLE_COUNT = "defaultSampleCount";

    public static final String PROP_NAME_DEFAULT_SAMPLE_SET_LSID = "defaultSampleSetLSID";
    public static final String PROP_NAME_DEFAULT_SAMPLE_SET_NAME = "defaultSampleSetName";
    public static final String PROP_NAME_DEFAULT_SAMPLE_SET_ROW_ID = "defaultSampleRowId";
    /** Prefix for sample LSIDs that match the barcode */
    public static final String PROP_PREFIX_SELECTED_SAMPLE_LSID = "selectedSampleLSID";
    /** Prefix for sample set LSIDs for each material that matches the barcode */
    public static final String PROP_PREFIX_SELECTED_SAMPLE_SET_LSID = "selectedSampleSetLSID";
    /** Prefix to indicate if the selection should be editable or not (true/false) */
    public static final String PROP_PREFIX_SELECTED_SAMPLE_LOCKED = "selectedSampleLocked";

    public static final String DUMMY_LSID = "--DUMMY-LSID--";
    
    public static String getLsidFormElementID(int index)
    {
        return "__sample" + index + "LSID";
    }

    public static String getNameFormElementID(int index)
    {
        return "__sample" + index + "Name";
    }
}
