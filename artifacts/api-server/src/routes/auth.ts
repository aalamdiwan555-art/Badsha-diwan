import { Router, type Response } from "express";

const router = Router();

function supabaseConfig() {
  const baseUrl = process.env.SUPABASE_URL;
  const publishableKey = process.env.SUPABASE_PUBLISHABLE_KEY;
  if (!baseUrl || !publishableKey) {
    throw new Error("Supabase authentication is not configured");
  }
  return { baseUrl: baseUrl.replace(/\/$/, ""), publishableKey };
}

async function forwardAuth(
  path: string,
  body: unknown,
  res: Response,
) {
  try {
    const { baseUrl, publishableKey } = supabaseConfig();
    const response = await fetch(`${baseUrl}/auth/v1/${path}`, {
      method: "POST",
      headers: {
        apikey: publishableKey,
        Authorization: `Bearer ${publishableKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    });
    const text = await response.text();
    res.status(response.status).type("application/json").send(text);
  } catch (error) {
    res.status(503).json({
      error: error instanceof Error ? error.message : "Authentication unavailable",
    });
  }
}

router.post("/auth/signup", (req, res) =>
  forwardAuth("signup", req.body, res),
);
router.post("/auth/signin", (req, res) =>
  forwardAuth("token?grant_type=password", req.body, res),
);

export default router;