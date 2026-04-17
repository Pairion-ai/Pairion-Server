/**
 * @pairion/actions — Computer-use action queue and approval flow.
 *
 * @remarks
 * M0 scaffolding only. No runtime logic. Receives proposed actions from
 * the agent, issues ActionPending messages to the right Client's HUD,
 * handles approval/rejection/expiration, and executes approved actions.
 *
 * @packageDocumentation
 */

/** Computer-use action kinds. */
export type ActionKind = 'click' | 'type' | 'keystroke' | 'script' | 'file-edit' | 'file-create' | 'file-delete' | 'shell';

/** Action approval status. */
export type ActionStatus = 'pending' | 'approved' | 'rejected' | 'expired' | 'executed';
