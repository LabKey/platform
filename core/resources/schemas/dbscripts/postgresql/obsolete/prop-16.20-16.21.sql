/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

-- Add support for formatting Date-only columns, as opposed to DateTime columns, #26844. First, rename existing default format
-- property from defaultDateFormatString to defaultDateTimeFormatString
UPDATE prop.Properties SET Name = 'defaultDateTimeFormatString' WHERE Set IN (SELECT Set FROM prop.PropertySets WHERE Category = 'LookAndFeel') AND Name = 'defaultDateFormatString';

-- Now add values for defaultDateFormatString where defaultDateTimeFormatString has been defined. Extract the date-only
-- portion of the format string using a simple algorithm: assume that the first space separates date and time portion of
-- the format string, so truncate at the first space if present, otherwise use the whole string.
INSERT INTO prop.Properties
(
    SELECT Set, 'defaultDateFormatString' AS Name, CASE WHEN POSITION(' ' IN Value) > 0 THEN SUBSTRING(Value FOR POSITION(' ' IN Value) - 1) ELSE Value END AS Value
    FROM prop.Properties WHERE Set IN (SELECT Set FROM prop.PropertySets WHERE Category = 'LookAndFeel') AND Name = 'defaultDateTimeFormatString'
);
