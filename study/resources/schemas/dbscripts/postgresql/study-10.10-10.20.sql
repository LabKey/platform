/*
 * Copyright (c) 2010 LabKey Corporation
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

/* study-10.10-10.11.sql */

/* noop */

/* study-10.11-10.12.sql */

/* Handle the possibility that branch release10.1 study-10.10-10.11.sql script has already run. */

/* Create studydata participant index if it doesn't exist. */
CREATE OR REPLACE FUNCTION core.fn_create_studydata_participant_index () RETURNS integer AS $$
BEGIN
    IF NOT EXISTS (SELECT * FROM pg_indexes
                   WHERE schemaname = 'study'
                   AND tablename = LOWER('StudyData')
                   AND indexname = LOWER('IX_StudyData_Participant'))
    THEN
        CREATE INDEX IX_StudyData_Participant
            ON study.StudyData
            USING btree
            (container, participantid);
        RETURN 1;
    ELSE
        RETURN 0;
    END IF;
END;
$$ LANGUAGE plpgsql;

SELECT core.fn_create_studydata_participant_index();
DROP FUNCTION core.fn_create_studydata_participant_index();


/* Create study.vial.AvailabilityReason column if it doesn't exist. */
CREATE OR REPLACE FUNCTION core.fn_create_vial_availabilityreason () RETURNS integer AS $$
BEGIN
    IF NOT EXISTS ( SELECT * FROM pg_attribute, pg_class
        WHERE attname = LOWER('AvailabilityReason')
        AND pg_attribute.attrelid = pg_class.oid
        AND pg_class.relname = LOWER('Vial') )
    THEN
        ALTER TABLE study.Vial ADD AvailabilityReason VARCHAR(256);
        RETURN 1;
    ELSE
        RETURN 0;
    END IF;
END;
$$ LANGUAGE plpgsql;

SELECT core.fn_create_vial_availabilityreason();
DROP FUNCTION core.fn_create_vial_availabilityreason();


/* Create study.SampleAvailabilityRule table if it doesn't exist */
CREATE OR REPLACE FUNCTION core.fn_create_study_sampleavailabilityrule () RETURNS integer AS $$
BEGIN
    IF NOT EXISTS ( SELECT * FROM pg_tables WHERE schemaname = 'study' AND tablename = LOWER('SampleAvailabilityRule'))
    THEN
        CREATE TABLE study.SampleAvailabilityRule
        (
            RowId SERIAL NOT NULL,
            Container EntityId NOT NULL,
            SortOrder INTEGER NOT NULL,
            RuleType VARCHAR(50),
            RuleData VARCHAR(250),
            MarkType VARCHAR(30),
            CONSTRAINT PL_SampleAvailabilityRule PRIMARY KEY (RowId)
        );
        RETURN 1;
    ELSE
        RETURN 0;
    END IF;
END;
$$ LANGUAGE plpgsql;

SELECT core.fn_create_study_sampleavailabilityrule();
DROP FUNCTION core.fn_create_study_sampleavailabilityrule();

/* study-10.12-10.13.sql */

-- Migrate from boolean key management type to None, RowId, and GUID
ALTER TABLE study.dataset ADD KeyManagementType VARCHAR(10);

UPDATE study.dataset SET KeyManagementType='RowId' WHERE KeyPropertyManaged = true;
UPDATE study.dataset SET KeyManagementType='None' WHERE KeyPropertyManaged = false;

ALTER TABLE study.dataset DROP COLUMN KeyPropertyManaged;
ALTER TABLE study.dataset ALTER KeyManagementType SET NOT NULL;

/* study-10.13-10.14.sql */

CREATE SCHEMA studyDataset;