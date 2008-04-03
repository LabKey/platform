DROP VIEW mousemod.MouseView;

CREATE VIEW mousemod.MouseView AS
    Select mouseId, mousemod.Mouse.modelId AS modelId, mousemod.Mouse.Container AS Container, EntityId, MouseNo, mousemod.Cage.CageName AS CageName, mousemod.Mouse.Sex AS Sex,
    mousemod.Mouse.Control AS Control, BirthDate, StartDate, DeathDate,MouseComments,NecropsyAppearance,NecropsyGrossFindings,toeNo,
    int1, int2, int3, date1, date2, string1, string2, string3,
    CASE WHEN DeathDate IS NULL THEN
        CASE WHEN StartDate IS NULL THEN
            (CURRENT_DATE - Date(BirthDate)) / 7
         ELSE
            (CURRENT_DATE - Date(StartDate)) / 7
         END
    ELSE
        CASE WHEN StartDate IS NULL THEN
            (Date(DeathDate) - Date(BirthDate)) / 7
         ELSE
            (Date(DeathDate) - Date(StartDate)) / 7
         END
    END
         AS Weeks FROM mousemod.Mouse JOIN mousemod.Cage on mousemod.Mouse.CageId = mousemod.Cage.CageId
;
