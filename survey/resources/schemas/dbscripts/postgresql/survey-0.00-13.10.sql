/*
 * Copyright (c) 2012-2015 LabKey Corporation
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

/* survey-0.00-0.01.sql */

CREATE SCHEMA survey;

CREATE TABLE survey.SurveyDesigns
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
	Label VARCHAR(200) NOT NULL,

    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

   	-- the schema for the results
    QueryName VARCHAR(200),
    SchemaName VARCHAR(50),

    -- the survey questions
    Metadata TEXT,

    CONSTRAINT pk_surveyDesigns PRIMARY KEY (RowId)
);

CREATE TABLE survey.Surveys
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    EntityId ENTITYID NOT NULL,
	Label VARCHAR(200) NOT NULL,

    CreatedBy USERID,
    Created TIMESTAMP,
    SubmittedBy USERID,
    Submitted TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    Status VARCHAR(50),
    SurveyDesignId Integer NOT NULL,

    -- the rowPk which holds the responses
	ResponsesPk VARCHAR(200),

    CONSTRAINT pk_surveys PRIMARY KEY (RowId),
    CONSTRAINT fk_surveys_surveyDesignId FOREIGN KEY (SurveyDesignId) REFERENCES survey.SurveyDesigns (RowId)
);

/* survey-12.30-12.31.sql */

ALTER TABLE survey.SurveyDesigns ADD COLUMN Description TEXT;