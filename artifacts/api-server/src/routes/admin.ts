import { Router } from "express";
import { eq } from "drizzle-orm";
import {
  appSettingsTable,
  db,
  profilesTable,
  scenariosTable,
  subscriptionsTable,
  usersTable,
} from "@workspace/db";
import { requireAdmin } from "../middlewares/auth";

const router = Router();
router.use("/admin", requireAdmin);

router.get("/admin/users", async (_req, res) => {
  res.json(await db.select().from(profilesTable));
});

router.get("/admin/scenarios", async (_req, res) => {
  res.json(await db.select().from(scenariosTable));
});

router.post("/admin/scenarios", async (req, res) => {
  const { adminId, name, description, scenarioData, isGlobal = true } = req.body;
  if (typeof adminId !== "string" || typeof name !== "string" || !scenarioData || typeof scenarioData !== "object" || Array.isArray(scenarioData)) {
    res.status(400).json({ error: "adminId, name, and scenarioData object are required" });
    return;
  }
  const created = await db.insert(scenariosTable).values({
    adminId,
    name: name.trim(),
    description: description ?? null,
    scenarioData: JSON.stringify(scenarioData),
    isGlobal: Boolean(isGlobal),
  }).returning();
  res.status(201).json(created[0]);
});

router.patch("/admin/scenarios/:id", async (req, res) => {
  const updated = await db.update(scenariosTable).set({
    ...(typeof req.body.name === "string" ? { name: req.body.name.trim() } : {}),
    ...(req.body.description !== undefined ? { description: req.body.description } : {}),
    ...(req.body.scenarioData !== undefined ? { scenarioData: JSON.stringify(req.body.scenarioData) } : {}),
    ...(req.body.isActive !== undefined ? { isActive: Boolean(req.body.isActive) } : {}),
    updatedAt: new Date(),
  }).where(eq(scenariosTable.id, req.params.id)).returning();
  if (!updated[0]) {
    res.status(404).json({ error: "Scenario not found" });
    return;
  }
  res.json(updated[0]);
});

router.delete("/admin/scenarios/:id", async (req, res) => {
  const deleted = await db.delete(scenariosTable)
    .where(eq(scenariosTable.id, req.params.id))
    .returning({ id: scenariosTable.id });
  if (!deleted[0]) {
    res.status(404).json({ error: "Scenario not found" });
    return;
  }
  res.status(204).end();
});

router.get("/admin/settings", async (_req, res) => {
  const settings = await db.select().from(appSettingsTable).limit(1);
  res.json(settings[0] ?? {
    id: 1,
    interstitialIntervalMinutes: 2,
    rewardAdsForOneDay: 20,
    trialDays: 3,
  });
});

router.patch("/admin/settings", async (req, res) => {
  const { interstitialIntervalMinutes, rewardAdsForOneDay, trialDays } = req.body;
  if (![interstitialIntervalMinutes, rewardAdsForOneDay, trialDays]
    .every((value) => Number.isInteger(value))) {
    res.status(400).json({ error: "All settings must be integers" });
    return;
  }
  const updated = await db.insert(appSettingsTable).values({
    id: 1,
    interstitialIntervalMinutes,
    rewardAdsForOneDay,
    trialDays,
  }).onConflictDoUpdate({
    target: appSettingsTable.id,
    set: { interstitialIntervalMinutes, rewardAdsForOneDay, trialDays, updatedAt: new Date() },
  }).returning();
  res.json(updated[0]);
});

router.post("/admin/users/:userId/subscription", async (req, res) => {
  const { durationDays, note } = req.body;
  if (!Number.isInteger(durationDays) || durationDays < 1 || durationDays > 3650) {
    res.status(400).json({ error: "durationDays must be between 1 and 3650" });
    return;
  }
  const expiresAt = new Date(Date.now() + durationDays * 86400000);
  const updated = await db.update(usersTable).set({
    subscriptionStatus: "active",
    subscriptionExpiresAt: expiresAt,
    updatedAt: new Date(),
  }).where(eq(usersTable.id, req.params.userId)).returning();
  if (!updated[0]) {
    res.status(404).json({ error: "User not found" });
    return;
  }
  const subscription = await db.insert(subscriptionsTable).values({
    userId: req.params.userId,
    grantedBy: req.user!.sub,
    durationDays,
    expiresAt,
    note: typeof note === "string" ? note : null,
  }).returning();
  res.status(201).json(subscription[0]);
});

router.post("/admin/users/:userId/ban", async (req, res) => {
  const { banned = true, reason } = req.body;
  if (req.params.userId === req.user!.sub) {
    res.status(400).json({ error: "Administrators cannot ban themselves" });
    return;
  }
  const updated = await db.update(profilesTable).set({
    isBanned: Boolean(banned),
    banReason: banned && typeof reason === "string" ? reason.trim() || null : null,
    updatedAt: new Date(),
  }).where(eq(profilesTable.id, req.params.userId)).returning();
  if (!updated[0]) {
    res.status(404).json({ error: "Profile not found" });
    return;
  }
  res.json(updated[0]);
});

router.post("/admin/users/:userId/ad-free", async (req, res) => {
  const updated = await db.update(profilesTable).set({
    isAdFree: Boolean(req.body.adFree),
    updatedAt: new Date(),
  }).where(eq(profilesTable.id, req.params.userId)).returning();
  if (!updated[0]) {
    res.status(404).json({ error: "Profile not found" });
    return;
  }
  res.json(updated[0]);
});

export default router;