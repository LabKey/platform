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
SELECT
    CAST(json_op(JsonMetrics, '#>>', '{folderTypeCounts,Study}') AS INTEGER) AS Study,
    CAST(json_op(JsonMetrics, '#>>', '{folderTypeCounts,Assay}') AS INTEGER) AS Assay,
    CAST(json_op(JsonMetrics, '#>>', '{folderTypeCounts,Collaboration}') AS INTEGER) AS Collaboration,
    CAST(json_op(JsonMetrics, '#>>', '{folderTypeCounts,MicroArray}') AS INTEGER) AS MicroArray,
    CAST(json_op(JsonMetrics, '#>>', '{folderTypeCounts,"Targeted MS"}') AS INTEGER) AS TargetedMS,
    CAST(json_op(JsonMetrics, '#>>', '{folderTypeCounts,NAb}') AS INTEGER) AS NAb,
    CAST(json_op(JsonMetrics, '#>>', '{folderTypeCounts,Flow}') AS INTEGER) AS Flow,
    ServerSessionId
FROM ServerSessions
WHERE JsonMetrics IS NOT NULL

