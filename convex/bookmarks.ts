import { mutation, query } from "./_generated/server";
import { v } from "convex/values";
import { authenticateToken } from "./auth";

function normalizeUrl(url: string): string {
  const trimmed = url.trim();
  if (!/^https?:\/\//i.test(trimmed)) {
    throw new Error("Bookmark URL must start with http:// or https://");
  }
  return trimmed;
}

function normalizeText(value: string, fieldName: string): string {
  const trimmed = value.trim();
  if (!trimmed) {
    throw new Error(`${fieldName} cannot be empty`);
  }
  return trimmed;
}

function normalizeOptionalText(value: string): string {
  return value.trim();
}

function normalizeTags(tags: string[]): string[] {
  return Array.from(
    new Set(
      tags
        .map((tag) => tag.trim().toLowerCase().replace(/^#/, "").replace(/\s+/g, "_"))
        .filter((tag) => tag.length > 0),
    ),
  ).slice(0, 20);
}

export const syncBookmarks = mutation({
  args: { 
    token: v.string(),
    bookmarks: v.array(
        v.object({
            bookmarkId: v.string(),
            url: v.string(),
            username: v.string(),
            comment: v.string(),
            platform: v.string(),
            thumbnailUrl: v.optional(v.string()),
            createdAt: v.number(),
            tags: v.array(v.string()),
        })
    )
  },
  handler: async (ctx, args) => {
    const userId = await authenticateToken(ctx, args.token);
    if (!userId) throw new Error("Unauthorized");

    let syncedCount = 0;
    
    // Simple sync: upsert based on bookmarkId
    for (const item of args.bookmarks) {
      const url = normalizeUrl(item.url);
      const username = normalizeOptionalText(item.username);
      const comment = normalizeOptionalText(item.comment);
      const platform = normalizeText(item.platform, "Platform");
      const tags = normalizeTags(item.tags);

        const existing = await ctx.db.query("bookmarks")
            .withIndex("by_user_bookmarkId", (q) => q.eq("userId", userId).eq("bookmarkId", item.bookmarkId))
            .first();

        if (existing) {
            await ctx.db.patch(existing._id, {
          url,
          username,
          comment,
          platform,
                thumbnailUrl: item.thumbnailUrl,
          tags,
            });
        } else {
            await ctx.db.insert("bookmarks", {
                userId,
          bookmarkId: item.bookmarkId,
          url,
          username,
          comment,
          platform,
          thumbnailUrl: item.thumbnailUrl,
          createdAt: item.createdAt,
          tags,
            });
        }
        syncedCount++;
    }

    return { success: true, syncedCount };
  }
});

export const getBookmarks = query({
  args: { token: v.string() },
  handler: async (ctx, args) => {
      const userId = await authenticateToken(ctx, args.token);
      if (!userId) throw new Error("Unauthorized");
      const bookmarks = await ctx.db.query("bookmarks")
        .withIndex("by_user", (q) => q.eq("userId", userId))
        .take(1000);

      return {
        bookmarks: bookmarks.map((bookmark) => ({
          bookmarkId: bookmark.bookmarkId,
          url: bookmark.url,
          username: bookmark.username,
          comment: bookmark.comment,
          platform: bookmark.platform,
          thumbnailUrl: bookmark.thumbnailUrl,
          createdAt: bookmark.createdAt,
          tags: bookmark.tags,
        })),
      };
  }
});

export const deleteBookmark = mutation({
  args: { token: v.string(), bookmarkId: v.string(), url: v.optional(v.string()) },
  handler: async (ctx, args) => {
      const userId = await authenticateToken(ctx, args.token);
      if (!userId) throw new Error("Unauthorized");

      let deletedCount = 0;
      const byBookmarkId = await ctx.db
        .query("bookmarks")
        .withIndex("by_user_bookmarkId", (q) => q.eq("userId", userId).eq("bookmarkId", args.bookmarkId))
        .take(100);

      for (const row of byBookmarkId) {
        await ctx.db.delete(row._id);
        deletedCount++;
      }

      if (args.url && deletedCount === 0) {
        const byUser = await ctx.db
          .query("bookmarks")
          .withIndex("by_user", (q) => q.eq("userId", userId))
          .take(2000);
        for (const row of byUser) {
          if (row.url === args.url) {
            await ctx.db.delete(row._id);
            deletedCount++;
          }
        }
      }

      return { success: true, deletedCount };
  }
});

export const deleteAllBookmarks = mutation({
  args: { token: v.string() },
  handler: async (ctx, args) => {
    const userId = await authenticateToken(ctx, args.token);
    if (!userId) throw new Error("Unauthorized");

    const rows = await ctx.db
      .query("bookmarks")
      .withIndex("by_user", (q) => q.eq("userId", userId))
      .take(5000);

    let deletedCount = 0;
    for (const row of rows) {
      await ctx.db.delete(row._id);
      deletedCount++;
    }

    return { success: true, deletedCount };
  },
});
