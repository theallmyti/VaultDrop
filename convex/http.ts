import { httpRouter } from "convex/server";
import { httpAction } from "./_generated/server";
import { api, internal } from "./_generated/api";

const http = httpRouter();

async function readJsonBody(request: Request): Promise<unknown> {
  try {
    return await request.json();
  } catch {
    throw new Error("Request body must be valid JSON");
  }
}

function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function cleanErrorMessage(error: unknown): string {
  const raw = error instanceof Error ? error.message : "Request failed";
  const firstLine = raw.split("\n")[0] ?? "Request failed";
  return firstLine.replace(/^Uncaught Error:\s*/i, "").trim();
}

function getStringField(body: unknown, field: string): string {
  if (!body || typeof body !== "object" || !(field in body)) {
    throw new Error(`Missing field: ${field}`);
  }

  const value = (body as Record<string, unknown>)[field];
  if (typeof value !== "string") {
    throw new Error(`Field ${field} must be a string`);
  }

  return value;
}

function getBookmarksField(body: unknown): Array<Record<string, unknown>> {
  if (!body || typeof body !== "object" || !("bookmarks" in body)) {
    throw new Error("Missing field: bookmarks");
  }

  const value = (body as Record<string, unknown>).bookmarks;
  if (!Array.isArray(value)) {
    throw new Error("Field bookmarks must be an array");
  }

  return value.map((bookmark, index) => {
    if (!bookmark || typeof bookmark !== "object") {
      throw new Error(`Bookmark at index ${index} must be an object`);
    }
    return bookmark as Record<string, unknown>;
  });
}

http.route({
  path: "/auth/requestOTP",
  method: "POST",
  handler: httpAction(async (ctx, request) => {
    try {
      const body = await readJsonBody(request);
      const email = getStringField(body, "email");
      const type = getStringField(body, "type");
      if (type !== "verify" && type !== "reset") {
        throw new Error("Field type must be either 'verify' or 'reset'");
      }

      const otpPayload: { email: string; otp: string; purpose: string } = await ctx.runMutation(
        internal.auth.prepareOtpEmail,
        { email, type },
      );

      const mailResult: { success: boolean; providerId: string | null } = await ctx.runAction(
        internal.email.sendOtpEmail,
        otpPayload,
      );
      return jsonResponse({
        success: true,
        message: "OTP sent to your email",
        email: otpPayload.email,
        providerId: mailResult.providerId,
      });
    } catch (e: unknown) {
      const message = cleanErrorMessage(e);
      return jsonResponse({ error: message }, 400);
    }
  }),
});

http.route({
  path: "/auth/verifyOTP",
  method: "POST",
  handler: httpAction(async (ctx, request) => {
    try {
      const body = await readJsonBody(request);
      const email = getStringField(body, "email");
      const otp = getStringField(body, "otp");
      const type = getStringField(body, "type");
      return jsonResponse(await ctx.runMutation(api.auth.verifyOTP, { email, otp, type }));
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : "Request failed";
      return jsonResponse({ error: message }, 400);
    }
  }),
});

http.route({
  path: "/auth/registerPassword",
  method: "POST",
  handler: httpAction(async (ctx, request) => {
    try {
      const body = await readJsonBody(request);
      const email = getStringField(body, "email");
      const password = getStringField(body, "password");
      return jsonResponse(await ctx.runMutation(api.auth.registerWithPassword, { email, password }));
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : "Request failed";
      return jsonResponse({ error: message }, 400);
    }
  }),
});

http.route({
  path: "/auth/loginPassword",
  method: "POST",
  handler: httpAction(async (ctx, request) => {
    try {
      const body = await readJsonBody(request);
      const email = getStringField(body, "email");
      const password = getStringField(body, "password");
      return jsonResponse(await ctx.runMutation(api.auth.loginWithPassword, { email, password }));
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : "Request failed";
      return jsonResponse({ error: message }, 401);
    }
  }),
});

http.route({
  path: "/auth/resetPassword",
  method: "POST",
  handler: httpAction(async (ctx, request) => {
    try {
      const body = await readJsonBody(request);
      const email = getStringField(body, "email");
      const otp = getStringField(body, "otp");
      const newPassword = getStringField(body, "newPassword");
      return jsonResponse(await ctx.runMutation(api.auth.resetPasswordWithOTP, { email, otp, newPassword }));
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : "Request failed";
      return jsonResponse({ error: message }, 400);
    }
  }),
});

http.route({
  path: "/bookmarks/sync",
  method: "POST",
  handler: httpAction(async (ctx, request) => {
    try {
      const body = await readJsonBody(request) as Record<string, unknown>;
      const token = getStringField(body, "token");
      const bookmarks = getBookmarksField(body);
      return jsonResponse(await ctx.runMutation(api.bookmarks.syncBookmarks, { token, bookmarks: bookmarks as never }));
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : "Request failed";
      return jsonResponse({ error: message }, 401);
    }
  }),
});

http.route({
  path: "/bookmarks/get",
  method: "POST",
  handler: httpAction(async (ctx, request) => {
    try {
      const body = await readJsonBody(request);
      const token = getStringField(body, "token");
      return jsonResponse(await ctx.runQuery(api.bookmarks.getBookmarks, { token }));
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : "Request failed";
      return jsonResponse({ error: message }, 401);
    }
  }),
});

http.route({
  path: "/bookmarks/delete",
  method: "POST",
  handler: httpAction(async (ctx, request) => {
    try {
      const body = (await readJsonBody(request)) as Record<string, unknown>;
      const token = getStringField(body, "token");
      const bookmarkId = getStringField(body, "bookmarkId");
      const url = typeof body.url === "string" ? body.url : undefined;
      return jsonResponse(await ctx.runMutation(api.bookmarks.deleteBookmark, { token, bookmarkId, url }));
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : "Request failed";
      return jsonResponse({ error: message }, 401);
    }
  }),
});

http.route({
  path: "/bookmarks/deleteAll",
  method: "POST",
  handler: httpAction(async (ctx, request) => {
    try {
      const body = await readJsonBody(request);
      const token = getStringField(body, "token");
      return jsonResponse(await ctx.runMutation(api.bookmarks.deleteAllBookmarks, { token }));
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : "Request failed";
      return jsonResponse({ error: message }, 401);
    }
  }),
});

export default http;
