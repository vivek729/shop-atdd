import type { Config } from 'jest';

// Narrow-integration suite: real Postgres via Testcontainers (requires Docker).
// Scoped to *.integration.spec.ts so it never runs alongside the unit suite.
// No env-mutating globalSetup — the container is started and POSTGRES_DB_* env
// vars are set inside the spec's beforeAll, because env set in a Jest
// globalSetup does not reliably reach the test-worker process.
const config: Config = {
  moduleFileExtensions: ['js', 'json', 'ts'],
  rootDir: 'src',
  testRegex: '.*\\.integration\\.spec\\.ts$',
  transform: {
    '^.+\\.(t|j)s$': 'ts-jest',
  },
  testEnvironment: 'node',
};

export default config;
