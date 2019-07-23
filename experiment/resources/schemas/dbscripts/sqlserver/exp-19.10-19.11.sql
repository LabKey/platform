/*
 * Copyright (c) 2019 LabKey Corporation
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
DROP TABLE exp.Edge;
GO

CREATE TABLE exp.Edge
(
    FromObjectId INT NOT NULL,
--    FromLsid LSIDtype NOT NULL,
    ToObjectId INT NOT NULL,
--    ToLsid LSIDtype NOT NULL,
    RunId INT NOT NULL,

    CONSTRAINT FK_Edge_From_Object FOREIGN KEY (FromObjectId) REFERENCES exp.object (objectid),
    CONSTRAINT FK_Edge_To_Object FOREIGN KEY (ToObjectId) REFERENCES exp.object (objectid),
    CONSTRAINT FK_Edge_RunId_Run FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId),
-- for query performance
    CONSTRAINT UQ_Edge_FromTo_RunId UNIQUE (FromObjectId, ToObjectId, RunId),
    CONSTRAINT UQ_Edge_ToFrom_RunId UNIQUE (ToObjectId, FromObjectId, RunId)
);
GO

EXEC core.executeJavaUpgradeCode 'rebuildAllEdges';
GO
