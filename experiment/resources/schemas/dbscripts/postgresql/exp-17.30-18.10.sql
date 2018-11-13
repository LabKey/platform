/*
 * Copyright (c) 2018 LabKey Corporation
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
/* exp-17.30-17.31.sql */

CREATE TABLE exp.Edge
(
    FromLsid LSIDTYPE NOT NULL,
    ToLsid LSIDTYPE NOT NULL,
    RunId INT NOT NULL,

    CONSTRAINT FK_Edge_FromLsid_Object FOREIGN KEY (FromLsid) REFERENCES exp.object (objecturi),
    CONSTRAINT FK_Edge_ToLsid_Object FOREIGN KEY (ToLsid) REFERENCES exp.object (objecturi),
    CONSTRAINT FK_Edge_RunId_Run FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId),
    CONSTRAINT UQ_Edge_FromLsid_ToLsid_RunId UNIQUE (FromLsid, ToLsid, RunId)
);

