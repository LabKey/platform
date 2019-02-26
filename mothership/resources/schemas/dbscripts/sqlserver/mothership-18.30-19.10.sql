ALTER TABLE mothership.ServerSession
  ADD buildTime datetime;
GO

EXEC core.fn_dropifexists 'ServerSession', 'mothership', 'COLUMN', 'buildTIme';
GO

ALTER TABLE mothership.ServerSession
  ADD buildTime datetime;
GO