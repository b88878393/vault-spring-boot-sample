-- ---------------------------------------------------------------------------------------------------------------
-- PostgreSQL bootstrap for the sample (runs once, on an empty data directory, via /docker-entrypoint-initdb.d).
--
--   vault_admin  - LOGIN role Vault's database secrets engine connects with. It can create/drop the short-lived
--                  application users and grant them membership in app_owner - nothing else.
--   app_owner    - NOLOGIN group role that owns the schema objects. Every Vault-generated user is a member and
--                  switches to it with SET ROLE, so tables outlive individual (rotated/revoked) users.
--
-- DEMO PASSWORD: 'vault-admin-demo-password' is a placeholder for local use only. In a real deployment, generate
-- a strong password and let Vault rotate it right away:  vault write -f database/rotate-root/postgres
-- ---------------------------------------------------------------------------------------------------------------
CREATE ROLE app_owner NOLOGIN;
ALTER SCHEMA public OWNER TO app_owner;

CREATE ROLE vault_admin WITH LOGIN CREATEROLE PASSWORD 'vault-admin-demo-password';
GRANT app_owner TO vault_admin WITH ADMIN OPTION;
GRANT CONNECT ON DATABASE appdb TO vault_admin;
