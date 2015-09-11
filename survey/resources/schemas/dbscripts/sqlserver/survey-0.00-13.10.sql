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

-- Create schema, tables, indexes, and constraints used for Survey module here
-- All SQL VIEW definitions should be created in survey-create.sql and dropped in survey-drop.sql
CREATE SCHEMA survey;
GO

CREATE TABLE survey.SurveyDesigns
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Container ENTITYID NOT NULL,
	Label NVARCHAR(200) NOT NULL,

    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

   	-- the schema for the results
    QueryName NVARCHAR(200),
    SchemaName NVARCHAR(50),

    -- the survey questions
    Metadata TEXT,

    CONSTRAINT pk_surveyDesigns PRIMARY KEY (RowId)
);

CREATE TABLE survey.Surveys
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Container ENTITYID NOT NULL,
    EntityId ENTITYID NOT NULL,
	Label NVARCHAR(200) NOT NULL,

    CreatedBy USERID,
    Created DATETIME,
    SubmittedBy USERID,
    Submitted DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    Status NVARCHAR(50),
    SurveyDesignId INT NOT NULL,

    -- the rowPk which holds the responses
	ResponsesPk NVARCHAR(200),

    CONSTRAINT pk_surveys PRIMARY KEY (RowId),
    CONSTRAINT fk_surveys_surveyDesignId FOREIGN KEY (SurveyDesignId) REFERENCES survey.SurveyDesigns (RowId)
);

/* survey-12.30-13.10.sql */

ALTER TABLE survey.SurveyDesigns ADD Description NTEXT;