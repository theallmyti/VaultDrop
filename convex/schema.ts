import { defineSchema, defineTable } from "convex/server";
import { v } from "convex/values";

export default defineSchema({
  users: defineTable({
    email: v.string(),
    hashedPassword: v.optional(v.string()), // store hash if using passwords
    verified: v.boolean(),
  }).index("by_email", ["email"]),
  
  otps: defineTable({
    email: v.string(),
    otp: v.string(),
    expiresAt: v.number(),
    type: v.union(v.literal("verify"), v.literal("reset")),
  }).index("by_email", ["email"])
    .index("by_email_and_type_and_otp", ["email", "type", "otp"]),
  
  sessions: defineTable({
    userId: v.id("users"),
    token: v.string(),
    expiresAt: v.number(),
  }).index("by_token", ["token"])
    .index("by_user", ["userId"]),
  
  bookmarks: defineTable({
    userId: v.id("users"),
    bookmarkId: v.string(), // ID from Android local storage
    url: v.string(),
    username: v.string(),
    comment: v.string(),
    platform: v.string(),
    thumbnailUrl: v.optional(v.string()),
    createdAt: v.number(),
    tags: v.array(v.string()),
  }).index("by_user", ["userId"])
    .index("by_user_bookmarkId", ["userId", "bookmarkId"]),

  rateLimits: defineTable({
    key: v.string(),
    windowStart: v.number(),
    count: v.number(),
  }).index("by_key", ["key"]),
});
