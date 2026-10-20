-- Creates the application role and databases (idempotent).
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'qualityinspect') THEN
    CREATE ROLE qualityinspect LOGIN PASSWORD 'qualityinspect';
  END IF;
END
$$;

SELECT format('CREATE DATABASE %I OWNER qualityinspect', 'qualityinspect')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'qualityinspect')
\gexec

SELECT format('CREATE DATABASE %I OWNER qualityinspect', 'qualityinspect_test')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'qualityinspect_test')
\gexec
