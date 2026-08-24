import { createHmac, timingSafeEqual } from "node:crypto";
import type { NextFunction, Request, Response } from "express";

export type AuthUser = {
  sub: string;
  email?: string;
  role?: string;
  app_metadata?: { role?: string };
  user_metadata?: { role?: string };
};

declare global {
  namespace Express {
    interface Request {
      user?: AuthUser;
    }
  }
}

function decodePart<T>(part: string): T | null {
  try {
    return JSON.parse(Buffer.from(part, "base64url").toString("utf8")) as T;
  } catch {
    return null;
  }
}

function verifyToken(token: string, secret: string): AuthUser | null {
  const [encodedHeader, encodedPayload, encodedSignature] = token.split(".");
  if (!encodedHeader || !encodedPayload || !encodedSignature) return null;

  const header = decodePart<{ alg?: string; typ?: string }>(encodedHeader);
  const payload = decodePart<AuthUser & { exp?: number }>(encodedPayload);
  if (header?.alg !== "HS256" || !payload?.sub) return null;
  if (payload.exp !== undefined && payload.exp <= Math.floor(Date.now() / 1000)) {
    return null;
  }

  const expected = createHmac("sha256", secret)
    .update(`${encodedHeader}.${encodedPayload}`)
    .digest();
  const provided = Buffer.from(encodedSignature, "base64url");
  return provided.length === expected.length &&
    timingSafeEqual(provided, expected)
    ? payload
    : null;
}

export function requireAuth(req: Request, res: Response, next: NextFunction) {
  const secret = process.env.SUPABASE_JWT_SECRET;
  if (!secret) {
    res.status(503).json({ error: "Authentication is not configured" });
    return;
  }

  const authorization = req.header("authorization");
  const token = authorization?.startsWith("Bearer ")
    ? authorization.slice("Bearer ".length).trim()
    : "";
  const user = token ? verifyToken(token, secret) : null;
  if (!user) {
    res.status(401).json({ error: "A valid bearer token is required" });
    return;
  }

  req.user = user;
  next();
}

export function requireAdmin(req: Request, res: Response, next: NextFunction) {
  requireAuth(req, res, () => {
    const role =
      req.user?.role ??
      req.user?.app_metadata?.role ??
      req.user?.user_metadata?.role;
    if (role !== "admin") {
      res.status(403).json({ error: "Administrator access required" });
      return;
    }
    next();
  });
}