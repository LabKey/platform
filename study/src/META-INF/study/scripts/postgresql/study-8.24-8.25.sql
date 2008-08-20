/*
 * Copyright (c) 2008 LabKey Corporation
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
CREATE TABLE study.QCState
    (
    RowId SERIAL,
    Label VARCHAR(64) NULL,
    Description VARCHAR(500) NULL,
    Container ENTITYID NOT NULL,
    PublicData BOOLEAN NOT NULL,
    CONSTRAINT PK_QCState PRIMARY KEY (RowId),
    CONSTRAINT UQ_QCState_Label UNIQUE(Label, Container)
    );

ALTER TABLE study.StudyData
    ADD COLUMN QCState INT NULL,
    ADD CONSTRAINT FK_StudyData_QCState FOREIGN KEY (QCState) REFERENCES study.QCState (RowId);

CREATE INDEX IX_StudyData_QCState ON study.StudyData(QCState);

ALTER TABLE study.Study
    ADD DefaultPipelineQCState INT,
    ADD DefaultAssayQCState INT,
    ADD DefaultDirectEntryQCState INT,
    ADD ShowPrivateDataByDefault BOOLEAN NOT NULL DEFAULT False,
    ADD CONSTRAINT FK_Study_DefaultPipelineQCState FOREIGN KEY (DefaultPipelineQCState) REFERENCES study.QCState (RowId),
    ADD CONSTRAINT FK_Study_DefaultAssayQCState FOREIGN KEY (DefaultAssayQCState) REFERENCES study.QCState (RowId),
    ADD CONSTRAINT FK_Study_DefaultDirectEntryQCState FOREIGN KEY (DefaultDirectEntryQCState) REFERENCES study.QCState (RowId);