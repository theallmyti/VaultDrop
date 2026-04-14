import { mutation, query, MutationCtx, QueryCtx, internalMutation } from "./_generated/server";
import { Id } from "./_generated/dataModel";
import { v } from "convex/values";
import { internal } from "./_generated/api";

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const OTP_REGEX = /^\d{6}$/;
const OTP_WINDOW_MS = 15 * 60 * 1000;
const OTP_REQUEST_LIMIT = 3;
const OTP_RESEND_COOLDOWN_MS = 60 * 1000;
const AUTH_LOGIN_LIMIT = 10;
const AUTH_WINDOW_MS = 15 * 60 * 1000;

// Generate a random 6-digit OTP
function generateOTP() {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

function generateToken() {
  return crypto.randomUUID();
}

function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function hexToBytes(hex: string): Uint8Array {
  const clean = hex.trim().toLowerCase();
  if (clean.length % 2 !== 0) {
    throw new Error("Invalid hex value");
  }
  const out = new Uint8Array(clean.length / 2);
  for (let i = 0; i < clean.length; i += 2) {
    out[i / 2] = parseInt(clean.slice(i, i + 2), 16);
  }
  return out;
}

async function sha256Hex(input: string): Promise<string> {
  const encoded = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", encoded);
  return bytesToHex(new Uint8Array(digest));
}

function normalizeEmail(email: string): string {
  const normalized = email.trim().toLowerCase();
  if (!EMAIL_REGEX.test(normalized)) {
    throw new Error("Enter a valid email address");
  }
  return normalized;
}

function validateOtp(otp: string): string {
  const trimmed = otp.trim();
  if (!OTP_REGEX.test(trimmed)) {
    throw new Error("OTP must be exactly 6 digits");
  }
  return trimmed;
}

function validatePassword(password: string): string {
  const trimmed = password.trim();
  if (trimmed.length < 8) {
    throw new Error("Password must be at least 8 characters");
  }
  return trimmed;
}

async function enforceRateLimit(
  ctx: MutationCtx,
  key: string,
  limit: number,
  windowMs: number,
  errorMessage?: string,
) {
  const now = Date.now();
  const existing = await ctx.db.query("rateLimits").withIndex("by_key", (q) => q.eq("key", key)).first();

  if (!existing) {
    await ctx.db.insert("rateLimits", { key, windowStart: now, count: 1 });
    return;
  }

  if (existing.windowStart + windowMs < now) {
    await ctx.db.patch(existing._id, { windowStart: now, count: 1 });
    return;
  }

  if (existing.count >= limit) {
    throw new Error(errorMessage ?? "Too many requests. Please wait a few minutes and try again.");
  }

  await ctx.db.patch(existing._id, { count: existing.count + 1 });
}

function generateSaltHex(byteLength: number = 16): string {
  const random = new Uint8Array(byteLength);
  crypto.getRandomValues(random);
  return bytesToHex(random);
}

async function makePasswordHash(password: string): Promise<string> {
  const salt = generateSaltHex();
  const digest = await sha256Hex(`${salt}:${password}`);
  return `${salt}:${digest}`;
}

async function verifyPassword(password: string, storedHash: string): Promise<boolean> {
  const [salt] = storedHash.split(":");
  if (!salt) return false;
  hexToBytes(salt);
  const expected = await sha256Hex(`${salt}:${password}`);
  return storedHash === `${salt}:${expected}`;
}

async function createSessionForUser(ctx: MutationCtx, userId: Id<"users">) {
  const token = generateToken();
  const expiresAt = Date.now() + 30 * 24 * 60 * 60 * 1000;
  await ctx.db.insert("sessions", {
    userId,
    token,
    expiresAt,
  });
  return token;
}

async function consumeOtp(
  ctx: MutationCtx,
  email: string,
  otp: string,
  type: "verify" | "reset",
) {
  const normalizedEmail = normalizeEmail(email);
  const otpRecord = await ctx.db
    .query("otps")
    .withIndex("by_email_and_type_and_otp", (q) =>
      q.eq("email", normalizedEmail).eq("type", type).eq("otp", otp),
    )
    .first();

  if (!otpRecord) throw new Error("Invalid or expired OTP");
  if (otpRecord.expiresAt < Date.now()) throw new Error("OTP has expired");

  await ctx.db.delete(otpRecord._id);
}

async function createOtpForEmail(
  ctx: MutationCtx,
  rawEmail: string,
  type: "verify" | "reset",
) {
  const email = normalizeEmail(rawEmail);
  await enforceRateLimit(
    ctx,
    `otpCooldown:${type}:${email}`,
    1,
    OTP_RESEND_COOLDOWN_MS,
    "Please wait 60 seconds before requesting another OTP.",
  );
  await enforceRateLimit(ctx, `otp:${type}:${email}`, OTP_REQUEST_LIMIT, OTP_WINDOW_MS);
  const existingUser = await ctx.db
    .query("users")
    .withIndex("by_email", (q) => q.eq("email", email))
    .first();

  if (!existingUser) {
    throw new Error("No account found on this email");
  }

  const otp = generateOTP();
  const expiresAt = Date.now() + 15 * 60 * 1000;

  await ctx.db.insert("otps", {
    email,
    otp,
    expiresAt,
    type,
  });

  const purpose = type === "reset" ? "Password Reset" : "Email Verification";
  return { email, otp, purpose };
}

export const prepareOtpEmail = internalMutation({
  args: { email: v.string(), type: v.union(v.literal("verify"), v.literal("reset")) },
  handler: async (ctx, args) => {
    return await createOtpForEmail(ctx, args.email, args.type);
  },
});

export const requestOTP = mutation({
  args: { email: v.string(), type: v.union(v.literal("verify"), v.literal("reset")) },
  handler: async (ctx, args) => {
    const { email, otp, purpose } = await createOtpForEmail(ctx, args.email, args.type);
    await ctx.scheduler.runAfter(0, internal.email.sendOtpEmail, {
      email,
      otp,
      purpose,
    });

    return {
      success: true,
      message: "OTP sent to your email",
      email,
    };
  },
});

export const registerWithPassword = mutation({
  args: { email: v.string(), password: v.string() },
  handler: async (ctx, args) => {
    const email = normalizeEmail(args.email);
    const password = validatePassword(args.password);

    const existingUser = await ctx.db
      .query("users")
      .withIndex("by_email", (q) => q.eq("email", email))
      .first();

    const hashedPassword = await makePasswordHash(password);

    if (existingUser) {
      await ctx.db.patch(existingUser._id, {
        hashedPassword,
      });
    } else {
      await ctx.db.insert("users", {
        email,
        hashedPassword,
        verified: false,
      });
    }

    return {
      success: true,
      message: "Password set. Verify email with OTP to complete sign up.",
    };
  },
});

export const loginWithPassword = mutation({
  args: { email: v.string(), password: v.string() },
  handler: async (ctx, args) => {
    const email = normalizeEmail(args.email);
    await enforceRateLimit(ctx, `login:${email}`, AUTH_LOGIN_LIMIT, AUTH_WINDOW_MS);

    const user = await ctx.db
      .query("users")
      .withIndex("by_email", (q) => q.eq("email", email))
      .first();

    if (!user) {
      throw new Error("No account found on this email");
    }

    if (!user.hashedPassword) {
      throw new Error("Invalid email or password");
    }

    const ok = await verifyPassword(args.password, user.hashedPassword);
    if (!ok) {
      throw new Error("Invalid email or password");
    }

    if (!user.verified) {
      throw new Error("Please verify your email with OTP first");
    }

    const token = await createSessionForUser(ctx, user._id);
    return { success: true, token, userId: user._id };
  },
});

export const resetPasswordWithOTP = mutation({
  args: { email: v.string(), otp: v.string(), newPassword: v.string() },
  handler: async (ctx, args) => {
    const email = normalizeEmail(args.email);
    await enforceRateLimit(ctx, `reset:${email}`, AUTH_LOGIN_LIMIT, AUTH_WINDOW_MS);
    const newPassword = validatePassword(args.newPassword);

    await consumeOtp(ctx, email, validateOtp(args.otp), "reset");

    const user = await ctx.db
      .query("users")
      .withIndex("by_email", (q) => q.eq("email", email))
      .first();

    if (!user) {
      throw new Error("User not found");
    }

    const hashedPassword = await makePasswordHash(newPassword);
    await ctx.db.patch(user._id, { hashedPassword, verified: true });

    const token = await createSessionForUser(ctx, user._id);
    return { success: true, token, userId: user._id };
  },
});

export const verifyOTP = mutation({
  args: { email: v.string(), otp: v.string(), type: v.union(v.literal("verify"), v.literal("reset")) },
  handler: async (ctx, args) => {
    const email = normalizeEmail(args.email);
    await enforceRateLimit(ctx, `verify:${email}`, AUTH_LOGIN_LIMIT, AUTH_WINDOW_MS);
    await consumeOtp(ctx, email, validateOtp(args.otp), args.type);

    const user = await ctx.db
      .query("users")
      .withIndex("by_email", (q) => q.eq("email", email))
      .first();

    if (!user) throw new Error("User not found");

    if (args.type === "verify") {
      await ctx.db.patch(user._id, { verified: true });
    }

    const token = await createSessionForUser(ctx, user._id);

    return { success: true, token, userId: user._id };
  },
});

// Helper for verifying token across mutations
export async function authenticateToken(ctx: MutationCtx | QueryCtx, token: string) {
    if (!token) return null;
  const session = await ctx.db.query("sessions").withIndex("by_token", (q) => q.eq("token", token)).first();
    if (!session || session.expiresAt < Date.now()) return null;
    return session.userId;
}

export const getSession = query({
    args: { token: v.string() },
    handler: async (ctx, args) => {
        const userId = await authenticateToken(ctx, args.token);
        if (!userId) return null;
        return await ctx.db.get(userId);
    }
});

export const logout = mutation({
    args: { token: v.string() },
    handler: async (ctx, args) => {
        const session = await ctx.db.query("sessions").withIndex("by_token", (q) => q.eq("token", args.token)).first();
        if (session) {
            await ctx.db.delete(session._id);
        }
        return { success: true };
    }
});
