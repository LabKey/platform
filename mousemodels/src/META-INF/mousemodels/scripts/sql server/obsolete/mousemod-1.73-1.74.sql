/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
DROP VIEW mousemod.MouseView
go

CREATE VIEW mousemod.MouseView AS
    Select mouseId, mousemod.Mouse.modelId modelId, mousemod.Mouse.Container Container, EntityId, MouseNo, mousemod.Cage.CageName As CageName, mousemod.Mouse.Sex Sex,
    mousemod.Mouse.Control Control, BirthDate, StartDate, DeathDate,MouseComments,NecropsyAppearance,NecropsyGrossFindings,toeNo,
    int1, int2, int3, date1, date2, string1, string2, string3,
    CASE WHEN DeathDate IS NULL THEN
        CASE WHEN StartDate IS NULL THEN
            DATEDIFF(week, BirthDate, GETDATE())
         ELSE
            DATEDIFF(week, StartDate, GETDATE())
         END
    ELSE
        CASE WHEN StartDate IS NULL THEN
            DATEDIFF(week, BirthDate, DeathDate)
         ELSE
            DATEDIFF(week, StartDate, DeathDate)
         END
    END
         Weeks From mousemod.Mouse JOIN mousemod.Cage on mousemod.Mouse.CageId = mousemod.Cage.CageId
GO

