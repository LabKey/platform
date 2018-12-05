/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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
 
/* prop-0.00-10.30.sql */

-- Create generic properties tables, stored procedures, etc.

CREATE SCHEMA prop;

--
-- NOTE: Bug in PropertyManager means NULL can't be passed for ObjectId, Category, etc. right now
--
-- ObjectId: EntityId or ContainerId this setting applies to
--     Generally only global User settings would use NULL ObjectId
-- Category: Category can be used to group related properties,
--     Category can be NULL if the Key field is reasonably unique/descriptive
-- UserId: Modules may use NULL UserId for general configuration
--
CREATE TABLE prop.PropertySets
(
    Set SERIAL,
    ObjectId UNIQUEIDENTIFIER NULL,  -- e.g. EntityId or ContainerID
    Category VARCHAR(255) NULL,      -- e.g. "org.labkey.api.MailingList", may be NULL
    UserId USERID,

    CONSTRAINT PK_PropertySets PRIMARY KEY (Set),
    CONSTRAINT UQ_PropertySets UNIQUE (ObjectId, UserId, Category)
);


CREATE TABLE prop.Properties
(
    Set INT NOT NULL,
    Name VARCHAR(255) NOT NULL,
    Value VARCHAR(2000) NOT NULL,

    CONSTRAINT PK_Properties PRIMARY KEY (Set, Name)
);


CREATE FUNCTION prop.Property_setValue(INT, TEXT, TEXT) RETURNS void AS $$
    DECLARE
        propertySet ALIAS FOR $1;
        propertyName ALIAS FOR $2;
        propertyValue ALIAS FOR $3;

        rowCount INT;
    BEGIN
        IF (propertyValue IS NULL) THEN
            DELETE FROM prop.Properties WHERE Set = propertySet AND Name = propertyName;
        ELSE
            UPDATE prop.Properties SET Value = propertyValue WHERE Set = propertySet AND Name = propertyName;

            IF NOT FOUND THEN
                INSERT INTO prop.Properties VALUES (propertySet, propertyName, propertyValue);
            END IF;
        END IF;

        RETURN;
    END;
    $$ LANGUAGE plpgsql;

/* prop-12.10-12.20.sql */

-- Clean out any Properties that belong to a PropertySet that doesn't have a Container (ObjectId) anymore
DELETE FROM prop.properties WHERE "set" IN
  (SELECT "set" FROM prop.propertysets WHERE ObjectId NOT IN
    (SELECT EntityId FROM core.Containers));

-- Clean out any Properties that belong to a PropertySet that doesn't exist anymore
DELETE FROM prop.properties WHERE "set" NOT IN
  (SELECT "set" FROM prop.propertysets);

-- Clean out PropertySets that don't have a Container (ObjectId) anymore
DELETE FROM prop.propertysets WHERE ObjectId NOT IN
  (SELECT EntityId FROM core.Containers);

-- Create real FKs to prevent orphaning in the future
ALTER TABLE prop.properties
    ADD CONSTRAINT FK_Properties_Set FOREIGN KEY ("set") REFERENCES prop.PropertySets ("set");

ALTER TABLE prop.propertysets
    ADD CONSTRAINT FK_PropertySets_ObjectId FOREIGN KEY (ObjectId) REFERENCES core.Containers (EntityId);

/* prop-13.20-13.30.sql */

-- Add a column that specifies algorithm used to encrypt all values in this property set
ALTER TABLE prop.PropertySets
    ADD Encryption VARCHAR(100) NOT NULL DEFAULT 'None';

/* prop-13.30-14.10.sql */

-- Remove limit on value length
ALTER TABLE prop.Properties ALTER COLUMN Value TYPE TEXT;

/* prop-16.20-16.30.sql */

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

-- Re-split the old date-time formats into date-only formats using a slightly better algorithm: truncate at the first
-- space that follows the first "y" or "Y" in the format.

-- Delete the previous date-only formats
DELETE FROM prop.Properties WHERE Set IN (SELECT Set FROM prop.PropertySets WHERE Category = 'LookAndFeel') AND Name = 'defaultDateFormatString';

-- Now insert values for defaultDateFormatString where defaultDateTimeFormatString is defined, using the new approach
INSERT INTO prop.Properties
(
    SELECT Set, 'defaultDateFormatString' AS Name,
        CASE WHEN POSITION('y' IN LOWER(Value)) > 0 THEN  -- Is there a "Y"?
            CASE WHEN POSITION(' ' IN SUBSTRING(Value, POSITION('y' IN LOWER(Value)))) > 0 THEN  -- Is there a space after the "Y"?
                SUBSTRING(Value, 0, POSITION('y' IN LOWER(Value)) + POSITION(' ' IN SUBSTRING(Value, POSITION('y' IN LOWER(Value)))) - 1)
            ELSE
                Value
            END
        ELSE
            Value
        END AS Value
    FROM prop.Properties WHERE Set IN (SELECT Set FROM prop.PropertySets WHERE Category = 'LookAndFeel') AND Name = 'defaultDateTimeFormatString'
);