/**
 * Error classes with machine-readable codes matching the OpenAPI `Error` schema
 * and AsyncAPI `ErrorPayload`.
 *
 * @remarks
 * Every error carries a dotted `code` string for programmatic handling and a
 * human-readable `message`. The optional `details` bag holds context-specific
 * data.
 */

/**
 * Base error for all Pairion application errors.
 *
 * @remarks
 * Carries a machine-readable `code`, HTTP `statusCode`, and optional `details`.
 */
export class PairionError extends Error {
  /** Machine-readable dotted error code (e.g. `auth.invalid_token`). */
  public readonly code: string;
  /** Suggested HTTP status code for REST responses. */
  public readonly statusCode: number;
  /** Optional structured context for debugging. */
  public readonly details?: Record<string, unknown> | undefined;

  constructor(
    code: string,
    message: string,
    statusCode: number = 500,
    details?: Record<string, unknown>,
  ) {
    super(message);
    this.name = 'PairionError';
    this.code = code;
    this.statusCode = statusCode;
    this.details = details;
  }

  /** Serializes to the OpenAPI Error schema shape. */
  toJSON(): { code: string; message: string; details?: Record<string, unknown> } {
    return {
      code: this.code,
      message: this.message,
      ...(this.details ? { details: this.details } : {}),
    };
  }
}

/** The requested feature or endpoint is not yet implemented. */
export class NotImplementedError extends PairionError {
  constructor(feature?: string) {
    super(
      'server.not_implemented',
      feature ? `Not implemented: ${feature}` : 'Not implemented',
      501,
    );
    this.name = 'NotImplementedError';
  }
}

/** The bearer token is missing or invalid. */
export class UnauthorizedError extends PairionError {
  constructor(message: string = 'Missing or invalid device token') {
    super('auth.unauthorized', message, 401);
    this.name = 'UnauthorizedError';
  }
}

/** The token is valid but lacks permission for this operation. */
export class ForbiddenError extends PairionError {
  constructor(message: string = 'Insufficient permissions') {
    super('auth.forbidden', message, 403);
    this.name = 'ForbiddenError';
  }
}

/** The requested resource was not found. */
export class NotFoundError extends PairionError {
  constructor(resource: string = 'Resource') {
    super('resource.not_found', `${resource} not found`, 404);
    this.name = 'NotFoundError';
  }
}

/** The request body or parameters are invalid. */
export class BadRequestError extends PairionError {
  constructor(message: string, details?: Record<string, unknown>) {
    super('request.bad_request', message, 400, details);
    this.name = 'BadRequestError';
  }
}

/** A conflict with the current resource state. */
export class ConflictError extends PairionError {
  constructor(message: string, details?: Record<string, unknown>) {
    super('resource.conflict', message, 409, details);
    this.name = 'ConflictError';
  }
}
