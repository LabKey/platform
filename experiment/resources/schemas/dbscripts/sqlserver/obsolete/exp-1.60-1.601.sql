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
CREATE VIEW exp.ExperimentRunMaterialOutputs AS
	SELECT exp.Material.LSID AS MaterialLSID, exp.ExperimentRun.LSID AS RunLSID, exp.ExperimentRun.Container AS Container
	FROM exp.Material
	JOIN exp.ExperimentRun ON exp.Material.RunId=exp.ExperimentRun.RowId
	WHERE SourceApplicationId IS NOT NULL
GO
