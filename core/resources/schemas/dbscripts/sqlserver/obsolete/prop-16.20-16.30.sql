/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

/* prop-16.20-16.21.sql */

-- Add support for formatting Date-only columns, as opposed to DateTime columns, #26844. First, rename existing default format
-- property from defaultDateFormatString to defaultDateTimeFormatString
UPDATE prop.Properties SET Name = 'defaultDateTimeFormatString' WHERE [Set] IN (SELECT [Set] FROM prop.PropertySets WHERE Category = 'LookAndFeel') AND Name = 'defaultDateFormatString';

-- Now add values for defaultDateFormatString where defaultDateTimeFormatString has been defined. Extract the date-only
-- portion of the format string using a simple algorithm: assume that the first space separates date and time portion of
-- the format string, so truncate at the first space if present, otherwise use the whole string.
INSERT INTO prop.Properties
    SELECT [Set], 'defaultDateFormatString' AS Name, CASE WHEN CHARINDEX(' ', Value) > 0 THEN LEFT(Value, CHARINDEX(' ', Value) - 1) ELSE Value END AS Value
    FROM prop.Properties WHERE [Set] IN (SELECT [Set] FROM prop.PropertySets WHERE Category = 'LookAndFeel') AND Name = 'defaultDateTimeFormatString';

/* prop-16.21-16.22.sql */

-- Re-split the old date-time formats into date-only formats using a slightly better algorithm: truncate at the first
-- space that follows the first "y" or "Y" in the format.

-- Delete the previous date-only formats
DELETE FROM prop.Properties WHERE [Set] IN (SELECT [Set] FROM prop.PropertySets WHERE Category = 'LookAndFeel') AND Name = 'defaultDateFormatString';

-- Now insert values for defaultDateFormatString where defaultDateTimeFormatString is defined, using the new approach
INSERT INTO prop.Properties
    SELECT [Set], 'defaultDateFormatString' AS Name,
        CASE WHEN CHARINDEX('y', LOWER(Value)) > 0 THEN
            CASE WHEN CHARINDEX(' ', Value, CHARINDEX('y', LOWER(Value))) > 0 THEN
                SUBSTRING(Value, 0, CHARINDEX(' ', Value, CHARINDEX('y', LOWER(Value))))
            ELSE
                Value
            END
        ELSE
            Value
        END AS Value
    FROM prop.Properties WHERE [Set] IN (SELECT [Set] FROM prop.PropertySets WHERE Category = 'LookAndFeel') AND Name = 'defaultDateTimeFormatString';