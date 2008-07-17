/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
ALTER TABLE exp.PropertyDomain ADD Required BIT NOT NULL,
CONSTRAINT DF_Required DEFAULT 0 FOR Required
GO
CREATE TABLE exp.RunList (
	ExperimentId int NOT NULL,
	ExperimentRunId int NOT NULL,
	CONSTRAINT PK_RunList PRIMARY KEY (ExperimentId, ExperimentRunId),
	CONSTRAINT FK_RunList_ExperimentId FOREIGN KEY (ExperimentId)
			REFERENCES exp.Experiment(RowId),
	CONSTRAINT FK_RunList_ExperimentRunId FOREIGN KEY (ExperimentRunId)
			REFERENCES exp.ExperimentRun(RowId) )
GO
INSERT INTO exp.RunList (ExperimentId, ExperimentRunId)
SELECT E.RowId, ER.RowId
   FROM exp.Experiment E INNER JOIN exp.ExperimentRun ER
	ON (E.LSID = ER.ExperimentLSID)
GO
ALTER TABLE exp.ExperimentRun DROP CONSTRAINT FK_ExperimentRun_Experiment
GO
DROP INDEX exp.ExperimentRun.IX_CL_ExperimentRun_ExperimentLSID
GO
ALTER TABLE exp.ExperimentRun DROP COLUMN ExperimentLSID
GO


