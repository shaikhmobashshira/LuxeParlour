package com.parlour.management;
import static com.parlour.management.Db.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

/** Password reset for every account role (customer, owner, admin).
    "Forgot password" issues a single-use token valid for 30 minutes; only the
    SHA-256 hash of the token is stored (collection password_resets). The raw
    token travels only inside the emailed link: {public-url}/reset-password?token=...
    Mail is sent over SMTP when SMTP_HOST is configured in .env; otherwise the
    link is written to the backend log (local development mode). Responses never
    reveal whether an email address belongs to an account. */
@RestController
public class PasswordReset {
 final Db db; final BCryptPasswordEncoder encoder; final String publicUrl;
 final String smtpHost, smtpUser, mailFrom; final ObjectProvider<JavaMailSender> mailer;
 PasswordReset(Db db, BCryptPasswordEncoder encoder, @Value("${app.public-url}") String publicUrl,
  @Value("${spring.mail.host:}") String smtpHost, @Value("${spring.mail.username:}") String smtpUser,
  @Value("${app.mail-from:}") String mailFrom, ObjectProvider<JavaMailSender> mailer) {
  this.db = db; this.encoder = encoder; this.publicUrl = publicUrl.replaceAll("/$", "");
  this.smtpHost = smtpHost; this.smtpUser = smtpUser; this.mailFrom = mailFrom; this.mailer = mailer;
 }
 static String sha256(String s) {
  try { var d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
   var sb = new StringBuilder(); for (byte x : d) sb.append(String.format("%02x", x)); return sb.toString();
  } catch (Exception e) { throw new IllegalStateException(e); }
 }
 static String newToken() { byte[] b = new byte[32]; new SecureRandom().nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }

 @PostMapping("/api/auth/forgot-password") Map<String,Object> forgot(@RequestBody Map<String,Object> b) {
  String email = text(b, "email", 254).toLowerCase(Locale.ROOT);
  var rows = db.list("SELECT * FROM users WHERE email=?", email);
  if (!rows.isEmpty()) {
   var u = rows.get(0);
   if (u.get("password_hash") != null && "ACTIVE".equals(u.get("status"))) {
    long uid = id(u, "user_id"); String token = newToken();
    db.update("DELETE FROM password_resets WHERE user_id=?", uid);
    db.insert("password_resets", map("user_id", uid, "token_hash", sha256(token), "expires_at", System.currentTimeMillis() + 30L*60*1000, "used", false));
    send(email, publicUrl + "/reset-password?token=" + token);
   }
  }
  return map("ok", true, "message", "If an account exists for that email, a password reset link has been sent.");
 }

 @PostMapping("/api/auth/reset-password") Map<String,Object> reset(@RequestBody Map<String,Object> b) {
  String token = text(b, "token", 200), password = text(b, "password", 72); Auth.validatePassword(password);
  var rows = db.list("SELECT * FROM password_resets WHERE token_hash=?", sha256(token));
  if (rows.isEmpty()) throw error(400, "This reset link is invalid or has expired");
  var r = rows.get(0);
  long exp = r.get("expires_at") instanceof Number n ? n.longValue() : 0L;
  if (Boolean.TRUE.equals(r.get("used")) || exp < System.currentTimeMillis()) throw error(400, "This reset link is invalid or has expired");
  db.update("UPDATE users SET password_hash=? WHERE user_id=?", encoder.encode(password), id(r, "user_id"));
  db.update("UPDATE password_resets SET used=? WHERE token_hash=?", true, sha256(token));
  return map("ok", true, "message", "Your password has been reset. You can now sign in with the new password.");
 }

 void send(String to, String link) {
  var sender = mailer.getIfAvailable();
  String from = !mailFrom.isBlank() ? mailFrom : smtpUser;
  if (sender == null || smtpHost.isBlank() || from.isBlank()) {
   System.out.println("[PasswordReset] Mail server not configured (dev mode). Reset link for " + to + ": " + link); return;
  }
  try {
   var m = new SimpleMailMessage(); m.setFrom(from); m.setTo(to);
   m.setSubject("Reset your Parlour Booking password");
   m.setText("Hello,\n\nWe received a request to reset the password for your Parlour Booking account. Open this link within 30 minutes to choose a new password:\n\n" + link + "\n\nIf you did not request this, you can safely ignore this email.");
   sender.send(m); System.out.println("[PasswordReset] Reset email sent to " + to);
  } catch (Exception e) {
   System.out.println("[PasswordReset] Mail send failed for " + to + " (" + e.getMessage() + "). Reset link: " + link);
  }
 }

 @GetMapping(value = "/forgot-password", produces = MediaType.TEXT_HTML_VALUE) String forgotPage() {
  return page("Forgot password", "Enter the email address you used to create your account. We will send you a link to reset your password.",
   "<label>Email<input id=\"email\" type=\"email\" autocomplete=\"username\" required></label>"
   + "<button onclick=\"go()\">Send reset link</button>"
   + "<script>async function go(){let r=await fetch('/api/auth/forgot-password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:document.getElementById('email').value})});let j=await r.json().catch(()=>({}));document.getElementById('msg').textContent=j.message||'Request failed. Try again.';}</script>");
 }

 @GetMapping(value = "/reset-password", produces = MediaType.TEXT_HTML_VALUE) String resetPage(@RequestParam(required = false) String token) {
  if (token == null || token.isBlank()) return page("Reset password", "This reset link is incomplete. Open the full link from your email, or request a new one.", "<p><a href=\"/forgot-password\">Request a new reset link</a></p>");
  String t = token.replace("'", "").replace("\"", "").replace("<", "").replace("&", "");
  return page("Reset password", "Choose a new password (at least 12 characters).",
   "<label>New password<input id=\"pw\" type=\"password\" autocomplete=\"new-password\" required></label>"
   + "<label>Confirm new password<input id=\"pw2\" type=\"password\" autocomplete=\"new-password\" required></label>"
   + "<button onclick=\"go()\">Reset password</button>"
   + "<script>const token='" + t + "';async function go(){let p=document.getElementById('pw').value;if(p!==document.getElementById('pw2').value){document.getElementById('msg').textContent='The two passwords do not match.';return;}let r=await fetch('/api/auth/reset-password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token:token,password:p})});let j=await r.json().catch(()=>({}));document.getElementById('msg').textContent=j.message||'Request failed. The link may have expired.';}</script>");
 }

 static String page(String title, String intro, String body) {
  return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + title + " · LuxeParlour</title>"
   + "<style>body{font-family:sans-serif;background:#FDFBF7;color:#1E2022;margin:0;padding:24px}main{max-width:420px;margin:40px auto;background:#fff;border:1px solid #EBE7DF;border-radius:14px;padding:24px}h1{font-size:22px;margin:0 0 8px}p{color:#555B62}label{display:block;margin:14px 0 6px}input{width:100%;padding:10px;border:1px solid #EBE7DF;border-radius:8px;box-sizing:border-box}button{margin-top:18px;width:100%;padding:12px;background:#D64A5B;color:#fff;border:0;border-radius:10px;font-weight:bold;cursor:pointer}#msg{margin-top:14px;color:#2E7D32}</style></head>"
   + "<body><main><h1>" + title + "</h1><p>" + intro + "</p>" + body + "<p id=\"msg\" role=\"status\"></p></main></body></html>";
 }
}
