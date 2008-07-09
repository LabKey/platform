-- DROP current views.
EXEC core.fn_dropifexists 'SpecimenSummary', 'study', 'VIEW', NULL
EXEC core.fn_dropifexists 'SpecimenDetail', 'study', 'VIEW', NULL
EXEC core.fn_dropifexists 'LockedSpecimens', 'study', 'VIEW', NULL
GO
