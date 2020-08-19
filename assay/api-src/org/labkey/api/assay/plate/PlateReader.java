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

package org.labkey.api.assay.plate;

import org.labkey.api.exp.ExperimentException;
import org.labkey.api.query.ValidationException;

import java.io.File;
import java.util.Map;

/**
 * User: klum
 * Date: 10/9/12
 */
public interface PlateReader
{
    /**
     * Well rejection codes
     */
    int WELL_NOT_COUNTED = -1;          // TNTC : too numerous to count
    int WELL_OFF_SCALE = -2;
    int WELL_QC_REJECTED = -3;          // rejected during the QC process
    int WELL_NOT_PERFORMED = -4;        // measurement was not performed

    String getType();

    /**
     * Parse the specified datafile and populate an array of well values
     * @param template
     * @param dataFile
     * @return
     * @throws ExperimentException
     */
    double[][] loadFile(PlateTemplate template, File dataFile) throws ExperimentException;

    /**
     * Parse the specified datafile and populate a map of array of well values. This is designed to process files
     * that have multiple grids of data embedded and the caller is interested in all of the data. The parser will
     * attempt to annotate the grids with metadata that it may discover during parsing.
     * @param template
     * @param dataFile
     * @return
     * @throws ExperimentException
     */
    Map<String, double[][]> loadMultiGridFile(PlateTemplate template, File dataFile) throws ExperimentException;

    /**
     * Determines whether the specified well value should be used in any analytical calculations
     * @param value
     * @return
     */
    boolean isWellValueValid(double value);

    /**
     * Return the display value for a specified well value. Used for cases where the underlying value may
     * be a rejection code (above), and an alternate display value should be rendered.
     * @param value
     * @return
     */
    String getWellDisplayValue(Object value);

    /**
     * Converts the string token value to a numeric well value.
     * @param token
     * @return
     * @throws ValidationException - if the value cannot be converted, will halt parsing of the plate
     */
    double convertWellValue(String token) throws ValidationException;
}
