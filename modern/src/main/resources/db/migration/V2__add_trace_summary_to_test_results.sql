-- Latency-tracing summary flags on the test result.

ALTER TABLE test_results ADD COLUMN IF NOT EXISTS has_trace   BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE test_results ADD COLUMN IF NOT EXISTS trace_count INTEGER DEFAULT 0     NOT NULL;
