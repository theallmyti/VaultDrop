"use node";

import { Resend } from "resend";
import nodemailer from "nodemailer";
import { v } from "convex/values";
import { internalAction } from "./_generated/server";

export const sendOtpEmail = internalAction({
  args: {
    email: v.string(),
    otp: v.string(),
    purpose: v.string(),
  },
  handler: async (_ctx, args) => {
    const smtpUser = process.env.SMTP_USER;
    const smtpPass = process.env.SMTP_PASS;
    const smtpHost = process.env.SMTP_HOST ?? "smtp.gmail.com";
    const smtpPort = Number(process.env.SMTP_PORT ?? "465");
    const smtpSecure = (process.env.SMTP_SECURE ?? "true").toLowerCase() !== "false";
    const smtpFrom = process.env.SMTP_FROM ?? smtpUser;

    if (smtpUser && !smtpPass) {
      throw new Error("SMTP_PASS is missing. Generate a Gmail App Password and set SMTP_PASS in Convex.");
    }

    if (smtpUser && smtpPass && smtpFrom) {
      try {
        const transporter = nodemailer.createTransport({
          host: smtpHost,
          port: smtpPort,
          secure: smtpSecure,
          auth: {
            user: smtpUser,
            pass: smtpPass,
          },
        });

        await transporter.sendMail({
          from: smtpFrom,
          to: args.email,
          subject: `VaultDrop ${args.purpose} Code`,
          html: `
            <div style="font-family: Arial, sans-serif; line-height: 1.5;">
              <h2>VaultDrop ${args.purpose}</h2>
              <p>Your one-time code is:</p>
              <p style="font-size: 24px; font-weight: bold; letter-spacing: 4px;">${args.otp}</p>
              <p>This code will expire in 15 minutes.</p>
              <p>If you did not request this, you can ignore this email.</p>
            </div>
          `,
          text: `VaultDrop ${args.purpose} code: ${args.otp}. This code expires in 15 minutes.`,
        });
      } catch (err) {
        const message = err instanceof Error ? err.message : "SMTP send failed";
        if (message.includes("535") || message.toLowerCase().includes("username and password not accepted")) {
          throw new Error(
            "Gmail SMTP login failed. Use a Google App Password for noreplyvaultdrop@gmail.com and set it as SMTP_PASS.",
          );
        }
        throw new Error(`SMTP send failed: ${message}`);
      }

      return { success: true, providerId: null };
    }

    const resendKey = process.env.RESEND_API_KEY;
    if (!resendKey) {
      throw new Error(
        "Email service not configured. Set SMTP_USER/SMTP_PASS/SMTP_FROM (recommended) or RESEND_API_KEY.",
      );
    }

    const fromAddress = process.env.RESEND_FROM ?? "VaultDrop <onboarding@resend.dev>";
    const resend = new Resend(resendKey);

    const result = await resend.emails.send({
      from: fromAddress,
      to: [args.email],
      subject: `VaultDrop ${args.purpose} Code`,
      html: `
        <div style="font-family: Arial, sans-serif; line-height: 1.5;">
          <h2>VaultDrop ${args.purpose}</h2>
          <p>Your one-time code is:</p>
          <p style="font-size: 24px; font-weight: bold; letter-spacing: 4px;">${args.otp}</p>
          <p>This code will expire in 15 minutes.</p>
          <p>If you did not request this, you can ignore this email.</p>
        </div>
      `,
      text: `VaultDrop ${args.purpose} code: ${args.otp}. This code expires in 15 minutes.`,
    });

    if (result.error) {
      throw new Error(
        `Failed to send OTP email: ${result.error.message ?? "Email provider rejected the request"}`,
      );
    }

    return { success: true, providerId: result.data?.id ?? null };
  },
});
