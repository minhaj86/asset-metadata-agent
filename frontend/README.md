# AI Metadata DAM - web UI

React + TypeScript UI for the DAM Metadata Agent: asset metadata with provenance, AI enrichment with live agentic
workflow progress, human approval, quality, taxonomy, semantic search and audit. It talks only to the Spring Boot
backend; all LLM calls happen server-side.

```bash
npm install
npm run dev          # http://localhost:5173, proxies /api etc. to DAM_BACKEND_URL (default http://localhost:8080)
npm test             # Vitest + React Testing Library (MSW-mocked API)
npm run lint
npm run typecheck
npm run build        # production build into dist/
```

Requires Node.js 22.22 or newer and a running backend (`./mvnw spring-boot:run` in the repository root).
`./mvnw -Pfrontend package` bundles this UI into the Spring Boot jar.

Architecture, API integration, workflow visualisation, approval and search flows are described in the
[repository README](../README.md#web-ui); design decisions in [docs/architecture.md](../docs/architecture.md).
