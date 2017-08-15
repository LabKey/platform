
EXEC core.fn_dropifexists 'Events','codedprocs','CONSTRAINT','FK_CODEDPROCS_EVENTS_QCSTATE'
EXEC core.fn_dropifexists 'Pkgs','codedprocs','CONSTRAINT','FK_CODEDPROCS_PKGS_QCSTATE'

EXEC core.executeJavaUpgradeCode 'moveQCStateToCore';
