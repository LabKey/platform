/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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
