/**
 * @pairion/authoring — Conversational skill-authoring flow.
 *
 * @remarks
 * M0 scaffolding only. No runtime logic. State machine for skill authoring:
 * clarifying → drafting → testing → installing → complete.
 *
 * @packageDocumentation
 */

/** Skill authoring session states. */
export type AuthoringState = 'clarifying' | 'drafting' | 'testing' | 'installing' | 'complete' | 'failed' | 'cancelled';

/** Skill authoring agent turn intents. */
export type AuthoringIntent = 'clarifying-question' | 'proposing-draft' | 'presenting-test-result' | 'confirming-install' | 'acknowledging';
