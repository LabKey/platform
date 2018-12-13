
SELECT core.fn_dropifexists('ServerSession', 'mothership', 'COLUMN', 'buildTime');

ALTER TABLE mothership.ServerSession
  ADD COLUMN buildTime timestamp;