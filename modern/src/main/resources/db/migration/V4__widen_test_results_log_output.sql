-- Widen test_results.log_output from VARCHAR(4096) to an unbounded CLOB (the H2 equivalent of
-- PostgreSQL TEXT). The 4096 cap truncated real run logs; callers now guard against pathological
-- sizes in code (TestResultEntity.MAX_LOG_OUTPUT_LENGTH) rather than at the column. CLOB matches
-- Hibernate's @Lob mapping so spring.jpa.hibernate.ddl-auto=validate passes.

ALTER TABLE test_results ALTER COLUMN log_output CLOB;
