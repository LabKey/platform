-- DROP current views.
SELECT core.fn_dropifexists('SpecimenSummary', 'study', 'VIEW', NULL);
SELECT core.fn_dropifexists('SpecimenDetail', 'study', 'VIEW', NULL);
SELECT core.fn_dropifexists('LockedSpecimens', 'study', 'VIEW', NULL);
