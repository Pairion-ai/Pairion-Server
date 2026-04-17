import { describe, it, expect } from 'vitest';
import type { SkillSourceKind, SkillSource } from '../src/index.js';

describe('@pairion/skills', () => {
  it('exports SkillSourceKind type', () => {
    const kind: SkillSourceKind = 'bundled';
    expect(kind).toBe('bundled');
  });

  it('SkillSource is structurally valid', () => {
    const source: SkillSource = { kind: 'mcp-url', url: 'http://example.com' };
    expect(source.kind).toBe('mcp-url');
  });
});
