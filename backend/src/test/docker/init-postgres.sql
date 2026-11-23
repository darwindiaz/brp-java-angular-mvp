\getenv test_password BRP_TEST_DB_PASSWORD

SELECT format(
    'CREATE ROLE brp_test_catalog LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L',
    :'test_password'
) \gexec

REVOKE ALL ON DATABASE postgres, template0, template1 FROM PUBLIC;
CREATE DATABASE brp_test_catalog OWNER brp_test_catalog;
REVOKE ALL ON DATABASE brp_test_catalog FROM PUBLIC;
