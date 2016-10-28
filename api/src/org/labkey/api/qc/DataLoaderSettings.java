/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.api.qc;

/**
 * User: klum
 * Date: Oct 9, 2011
 * Time: 2:07:48 PM
 */
public class DataLoaderSettings
{
    private boolean _bestEffortConversion;      // if conversion fails, the original field value is returned
    private boolean _allowEmptyData;
    private boolean _throwOnErrors;
    private boolean _allowUnexpectedColumns;    // don't load columns not in target domain
    private boolean _allowLookupByAlternateKey; // import lookup column by unique index on target column or by title display column (if unique)

    public boolean isBestEffortConversion()
    {
        return _bestEffortConversion;
    }

    public void setBestEffortConversion(boolean bestEffortConversion)
    {
        _bestEffortConversion = bestEffortConversion;
    }

    public boolean isAllowEmptyData()
    {
        return _allowEmptyData;
    }

    public void setAllowEmptyData(boolean allowEmptyData)
    {
        _allowEmptyData = allowEmptyData;
    }
    
    public boolean isThrowOnErrors()
    {
        return _throwOnErrors;
    }

    public void setThrowOnErrors(boolean throwOnErrors)
    {
        _throwOnErrors = throwOnErrors;
    }

    public boolean isAllowUnexpectedColumns()
    {
        return _allowUnexpectedColumns;
    }

    public void setAllowUnexpectedColumns(boolean allowUnexpectedColumns)
    {
        _allowUnexpectedColumns = allowUnexpectedColumns;
    }

    public boolean isAllowLookupByAlternateKey()
    {
        return _allowLookupByAlternateKey;
    }

    public void setAllowLookupByAlternateKey(boolean allowLookupByAlternateKey)
    {
        _allowLookupByAlternateKey = allowLookupByAlternateKey;
    }
}
