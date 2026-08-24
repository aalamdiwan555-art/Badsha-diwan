import {
  boolean,
  integer,
  jsonb,
  numeric,
  pgTable,
  text,
  timestamp,
  uuid,
} from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";

export const usersTable = pgTable("users", {
  id: uuid("id").primaryKey().defaultRandom(),
  email: text("email").notNull().unique(),
  role: text("role").notNull().default("user"),
  subscriptionStatus: text("subscription_status").notNull().default("inactive"),
  subscriptionExpiresAt: timestamp("subscription_expires_at"),
  isAdFree: boolean("is_ad_free").notNull().default(false),
  adsWatchedToday: integer("ads_watched_today").notNull().default(0),
  totalAdsWatched: integer("total_ads_watched").notNull().default(0),
  lastAdWatchedAt: timestamp("last_ad_watched_at"),
  selectedScenarioId: uuid("selected_scenario_id"),
  selectedScenarioName: text("selected_scenario_name"),
  deviceInfo: jsonb("device_info"),
  createdAt: timestamp("created_at").notNull().defaultNow(),
  updatedAt: timestamp("updated_at").notNull().defaultNow(),
});

export const insertUserSchema = createInsertSchema(usersTable).omit({
  id: true, createdAt: true, updatedAt: true,
});
export type InsertUser = z.infer<typeof insertUserSchema>;
export type User = typeof usersTable.$inferSelect;

export const scenariosTable = pgTable("scenarios", {
  id: uuid("id").primaryKey().defaultRandom(),
  adminId: uuid("admin_id").notNull().references(() => usersTable.id),
  name: text("name").notNull(),
  description: text("description"),
  scenarioData: jsonb("scenario_data").notNull(),
  isActive: boolean("is_active").notNull().default(true),
  isGlobal: boolean("is_global").notNull().default(true),
  targetUsers: uuid("target_users").array(),
  version: integer("version").notNull().default(1),
  createdAt: timestamp("created_at").notNull().defaultNow(),
  updatedAt: timestamp("updated_at").notNull().defaultNow(),
});
export const insertScenarioSchema = createInsertSchema(scenariosTable).omit({
  id: true, createdAt: true, updatedAt: true,
});
export type InsertScenario = z.infer<typeof insertScenarioSchema>;
export type Scenario = typeof scenariosTable.$inferSelect;

export const profilesTable = pgTable("profiles", {
  id: uuid("id").primaryKey().defaultRandom(),
  email: text("email").notNull().unique(),
  role: text("role").notNull().default("user"),
  subscriptionStatus: text("subscription_status").notNull().default("inactive"),
  subscriptionExpiresAt: timestamp("subscription_expires_at"),
  isAdFree: boolean("is_ad_free").notNull().default(false),
  adsWatchedToday: integer("ads_watched_today").notNull().default(0),
  totalAdsWatched: integer("total_ads_watched").notNull().default(0),
  lastAdWatchedAt: timestamp("last_ad_watched_at"),
  selectedScenarioId: uuid("selected_scenario_id"),
  selectedScenarioName: text("selected_scenario_name"),
  isAdmin: boolean("is_admin").notNull().default(false),
  isBanned: boolean("is_banned").notNull().default(false),
  banReason: text("ban_reason"),
  freeAccessUntil: timestamp("free_access_until"),
  deviceInfo: jsonb("device_info"),
  createdAt: timestamp("created_at").notNull().defaultNow(),
  updatedAt: timestamp("updated_at").notNull().defaultNow(),
});
export const insertProfileSchema = createInsertSchema(profilesTable).omit({
  createdAt: true, updatedAt: true,
});
export type InsertProfile = z.infer<typeof insertProfileSchema>;
export type Profile = typeof profilesTable.$inferSelect;

export const bansTable = pgTable("bans", {
  id: uuid("id").primaryKey().defaultRandom(),
  userId: uuid("user_id").notNull().references(() => usersTable.id),
  reason: text("reason"),
  bannedAt: timestamp("banned_at").notNull().defaultNow(),
  expiresAt: timestamp("expires_at"),
});
export const insertBanSchema = createInsertSchema(bansTable).omit({ id: true, bannedAt: true });
export type InsertBan = z.infer<typeof insertBanSchema>;
export type Ban = typeof bansTable.$inferSelect;

export const rewardsTable = pgTable("rewards", {
  id: uuid("id").primaryKey().defaultRandom(),
  userId: uuid("user_id").notNull().references(() => usersTable.id),
  adType: text("ad_type").notNull(),
  rewardedAt: timestamp("rewarded_at").notNull().defaultNow(),
  expiresAt: timestamp("expires_at").notNull(),
});
export const insertRewardSchema = createInsertSchema(rewardsTable).omit({ id: true, rewardedAt: true });
export type InsertReward = z.infer<typeof insertRewardSchema>;
export type Reward = typeof rewardsTable.$inferSelect;

export const subscriptionsTable = pgTable("subscriptions", {
  id: uuid("id").primaryKey().defaultRandom(),
  userId: uuid("user_id").notNull().references(() => usersTable.id),
  grantedBy: uuid("granted_by").references(() => usersTable.id),
  planType: text("plan_type").notNull().default("reward_ad"),
  durationDays: integer("duration_days"),
  startedAt: timestamp("started_at").notNull().defaultNow(),
  expiresAt: timestamp("expires_at"),
  isActive: boolean("is_active").notNull().default(true),
  note: text("note"),
  createdAt: timestamp("created_at").notNull().defaultNow(),
});
export const insertSubscriptionSchema = createInsertSchema(subscriptionsTable).omit({
  id: true, createdAt: true,
});
export type InsertSubscription = z.infer<typeof insertSubscriptionSchema>;
export type Subscription = typeof subscriptionsTable.$inferSelect;

export const adEventsTable = pgTable("ad_events", {
  id: uuid("id").primaryKey().defaultRandom(),
  userId: uuid("user_id").references(() => usersTable.id),
  adType: text("ad_type").notNull(),
  eventType: text("event_type").notNull(),
  revenueEstimate: numeric("revenue_estimate", { precision: 10, scale: 6 }),
  createdAt: timestamp("created_at").notNull().defaultNow(),
});
export const insertAdEventSchema = createInsertSchema(adEventsTable).omit({ id: true, createdAt: true });
export type InsertAdEvent = z.infer<typeof insertAdEventSchema>;
export type AdEvent = typeof adEventsTable.$inferSelect;

export const clickSessionsTable = pgTable("click_sessions", {
  id: uuid("id").primaryKey().defaultRandom(),
  userId: uuid("user_id").references(() => usersTable.id),
  scenarioId: uuid("scenario_id").references(() => scenariosTable.id),
  startedAt: timestamp("started_at").notNull().defaultNow(),
  endedAt: timestamp("ended_at"),
  totalClicks: integer("total_clicks").notNull().default(0),
  durationSeconds: integer("duration_seconds"),
  deviceInfo: jsonb("device_info"),
});
export const insertClickSessionSchema = createInsertSchema(clickSessionsTable).omit({ id: true, startedAt: true });
export type InsertClickSession = z.infer<typeof insertClickSessionSchema>;
export type ClickSession = typeof clickSessionsTable.$inferSelect;

export const appSettingsTable = pgTable("app_settings", {
  id: integer("id").primaryKey().default(1),
  interstitialIntervalMinutes: integer("interstitial_interval_minutes").notNull().default(2),
  rewardAdsForOneDay: integer("reward_ads_for_one_day").notNull().default(20),
  trialDays: integer("trial_days").notNull().default(3),
  updatedAt: timestamp("updated_at").notNull().defaultNow(),
});
export const insertAppSettingsSchema = createInsertSchema(appSettingsTable).omit({ id: true, updatedAt: true });
export type InsertAppSettings = z.infer<typeof insertAppSettingsSchema>;
export type AppSettings = typeof appSettingsTable.$inferSelect;

export const adminLogsTable = pgTable("admin_logs", {
  id: uuid("id").primaryKey().defaultRandom(),
  adminId: uuid("admin_id").references(() => usersTable.id),
  action: text("action").notNull(),
  targetUserId: uuid("target_user_id").references(() => usersTable.id),
  details: jsonb("details"),
  createdAt: timestamp("created_at").notNull().defaultNow(),
});
export const insertAdminLogSchema = createInsertSchema(adminLogsTable).omit({ id: true, createdAt: true });
export type InsertAdminLog = z.infer<typeof insertAdminLogSchema>;
export type AdminLog = typeof adminLogsTable.$inferSelect;