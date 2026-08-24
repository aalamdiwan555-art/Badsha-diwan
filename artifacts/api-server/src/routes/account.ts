import { Router } from "express";
import { eq } from "drizzle-orm";
import { db, profilesTable, scenariosTable } from "@workspace/db";
import { requireAuth } from "../middlewares/auth";

const router = Router();

router.get("/modes", async (_req, res) => {
  const modes = await db
    .select()
    .from(scenariosTable)
    .where(eq(scenariosTable.isActive, true));
  res.json(modes.filter((mode) => mode.isGlobal));
});

router.get("/profile", requireAuth, async (req, res) => {
  const profile = await db
    .select()
    .from(profilesTable)
    .where(eq(profilesTable.userId, req.user!.sub))
    .limit(1);
  if (!profile[0]) {
    res.status(404).json({ error: "Profile not found" });
    return;
  }
  res.json(profile[0]);
});

export default router;
