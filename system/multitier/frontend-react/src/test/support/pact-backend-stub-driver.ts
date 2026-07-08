// Pact-backed Backend Stub Driver — the "latest" test-kit's low-level plumbing.
//
// It absorbs the raw PactV3 ceremony (construction, addInteraction, and the
// executeTest mock-server lifecycle) so the Backend Stub DSL and the specs above
// it never touch Pact directly. The pact is written into the canonical contracts/
// dir, resolved from the package root via process.cwd() — so a spec's folder depth
// (component/latest, integration/legacy, …) is irrelevant; every consumer spec
// merges idempotently into the one frontend-backend.json.
import path from 'node:path';
import { PactV3 } from '@pact-foundation/pact';
import type { V3Interaction } from '@pact-foundation/pact/src/v3/types';

// The seam the Backend Stub DSL depends on: stage an interaction, then run the
// test body against the stubbed backend. A Component Test's in-process stub could
// implement the same surface without Pact — here it's Pact-backed.
export interface BackendStubDriver {
  stub(interaction: V3Interaction): void;
  runContract(testBody: (baseUrl: string) => Promise<void>): Promise<void>;
}

export class PactBackendStubDriver implements BackendStubDriver {
  private readonly provider = new PactV3({
    consumer: 'frontend',
    provider: 'backend',
    dir: path.resolve(process.cwd(), '../../../contracts'),
  });

  stub(interaction: V3Interaction): void {
    this.provider.addInteraction(interaction);
  }

  async runContract(testBody: (baseUrl: string) => Promise<void>): Promise<void> {
    await this.provider.executeTest(async (mockserver) => {
      await testBody(mockserver.url);
    });
  }
}
