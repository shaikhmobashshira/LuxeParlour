package com.parlour.management;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;
import org.json.*;

final class AdminScreens {
 static final String[] TABS={"Dashboard","Verifications","Users","Transactions","Complaints","Analytics","Audit Log"};
 static void tabs(MainActivity a,String active){a.chrome("");a.adminTab=active;a.showTabs(TABS,active,t->{switch(t){case "Dashboard"->dashboard(a);case "Verifications"->parlours(a);case "Users"->users(a);case "Transactions"->transactions(a);case "Complaints"->complaints(a);case "Analytics"->analytics(a);case "Audit Log"->audit(a);}});}
 static JSONObject obj(Object v){return MainActivity.obj(v);}
 static JSONArray arr(Object v){return MainActivity.arr(v);}
 static String txPeriod="ALL";
 // ---------- login ----------
 static void login(MainActivity a){a.unifiedLogin();}
 // ---------- dashboard ----------
 static void metric(MainActivity a,LinearLayout row,String title,String value,String accent){var c=a.cardIn(row);var lp=(LinearLayout.LayoutParams)c.getLayoutParams();lp.width=0;lp.weight=1;c.setLayoutParams(lp);
  var hr=a.hrow(c);var dot=new TextView(a);dot.setBackground(a.roundBg(accent,14));dot.setLayoutParams(new LinearLayout.LayoutParams(a.dp(28),a.dp(28)));hr.addView(dot);a.tv(hr,"  "+title,11,false,MainActivity.MUTED);a.tv(c,value,18,true,MainActivity.INK);}
 static void dashboard(MainActivity a){a.uiRole="ADMIN";a.content.removeAllViews();tabs(a,"Dashboard");a.tv(a.content,"Loading platform overview...",13,false,MainActivity.MUTED);
  a.get("/api/admin/dashboard",v->{var dash=obj(v);
   a.get("/api/admin/parlours",pv->{var parlours=arr(pv);
    a.get("/api/admin/payments",payv->{var payments=arr(payv);double gmv=0;for(int i=0;i<payments.length();i++){var p=payments.optJSONObject(i);if(p!=null)gmv+=p.optDouble("amount");}final double gmvF=gmv;
     a.get("/api/admin/complaints",cv->{var complaints=arr(cv);int open=0;for(int i=0;i<complaints.length();i++){var c=complaints.optJSONObject(i);if(c!=null&&!"RESOLVED".equals(c.optString("status")))open++;}
      a.content.removeAllViews();tabs(a,"Dashboard");
      var hc=a.card();a.tv(hc,"Platform Governance Overview",16,true,MainActivity.INK);a.tv(hc,"Dhaka Metro Beauty & Wellness Network Operations",12,false,MainActivity.MUTED);
      var hr=a.hrow(hc);var on=a.tv(hr,"Admin on Duty: Platform Admin (SuperAdmin)",11,true,MainActivity.PLUM);on.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
      var health=new TextView(a);health.setText("Systems Healthy");health.setTextSize(11);health.setTypeface(Typeface.DEFAULT_BOLD);health.setTextColor(Color.parseColor(MainActivity.GREEN));health.setBackground(a.roundBg(MainActivity.GREEN_L,8));health.setPadding(a.dp(8),a.dp(4),a.dp(8),a.dp(4));hr.addView(health);
      var k1=a.hrow(a.content);metric(a,k1,"Pending Verifications",String.valueOf(dash.optInt("pending_verification")),MainActivity.AMBER);metric(a,k1,"Total Parlours",String.valueOf(dash.optInt("parlours")),MainActivity.CORAL);
      var k2=a.hrow(a.content);metric(a,k2,"Platform GMV (BDT)",a.money(gmvF),MainActivity.GREEN);metric(a,k2,"Open Complaints",String.valueOf(open),MainActivity.RED);
      a.tv(a.content,"Administrative Modules",14,true,MainActivity.INK);
      var m1=a.hrow(a.content);a.btnW(m1,"Verifications",MainActivity.PLUM,1,x->parlours(a));a.btnW(m1,"Users",MainActivity.BLUE,1,x->users(a));
      var m2=a.hrow(a.content);a.btnW(m2,"Complaints",MainActivity.CORAL,1,x->complaints(a));a.btnW(m2,"Analytics",MainActivity.INK,1,x->analytics(a));
      var pending=new ArrayList<JSONObject>();for(int i=0;i<parlours.length();i++){var p=parlours.optJSONObject(i);if(p!=null&&"PENDING".equals(p.optString("status")))pending.add(p);}
      var ph=a.hrow(a.content);var pt=a.tv(ph,"Parlours Awaiting Onboarding ("+pending.size()+")",15,true,MainActivity.INK);pt.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var va=a.tv(ph,"View All",12,true,MainActivity.PLUM);va.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));va.setOnClickListener(x->parlours(a));
      if(pending.isEmpty())a.tv(a.content,"All parlour applications have been processed!",12,false,MainActivity.MUTED);
      for(var p:pending){var c=a.card();var nr=a.hrow(c);var nm=a.tv(nr,p.optString("name"),14,true,MainActivity.INK);nm.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));a.parlourBadge(nr,"PENDING");
       a.tv(c,"📍 "+p.optString("address"),12,false,MainActivity.MUTED);a.tv(c,"📞 "+p.optString("phone")+" • Category: "+p.optString("category"),12,false,MainActivity.INK);
       var br=a.hrow(c);a.outlineW(br,"Reject",MainActivity.RED,1,x->verifyDialog(a,p,"REJECTED",()->dashboard(a)));a.btnSW(br,"Approve & Verify",MainActivity.GREEN_B,1.2f,x->verifyDialog(a,p,"VERIFIED",()->dashboard(a)));}
     });});});});}
 static void verifyDialog(MainActivity a,JSONObject p,String target,Runnable after){var body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
  a.glassText(body,"Target Parlour: "+p.optString("name"),14,true,"#FFFFFF");
  var reason=a.glassField(body,"Audit Log Justification / Notes","Passed document checks and physical address audit.");
  long pid=p.optLong("parlour_id");
  a.glass("Update Status to "+target,body,"Confirm Action","VERIFIED".equals(target)?MainActivity.GREEN_B:MainActivity.RED,()->{try{a.post("/api/admin/parlours/"+pid+"/verify",new JSONObject().put("status",target).put("reason",a.value(reason)),x->{a.toast("Parlour "+target.toLowerCase()+".");after.run();});}catch(Exception e){a.message(e.getMessage());}});}
 // ---------- verifications ----------
 static String pFilter="ALL";
 static void parlours(MainActivity a){a.uiRole="ADMIN";a.content.removeAllViews();tabs(a,"Verifications");a.tv(a.content,"Loading parlours...",13,false,MainActivity.MUTED);
  a.get("/api/admin/parlours",v->{a.content.removeAllViews();tabs(a,"Verifications");var rows=arr(v);
   a.tv(a.content,"Parlour Onboarding & Verifications",16,true,MainActivity.INK);
   a.label(a.content,"Review legal documents, trade licenses, and verify or suspend salons across Dhaka.");
   var frow=a.hrow(a.content);String[][] fs={{"ALL","All"},{"PENDING","Pending"},{"VERIFIED","Verified"},{"SUSPENDED","Suspended"},{"REJECTED","Rejected"}};for(String[] f:fs){var t=a.pill(frow,f[1],pFilter.equals(f[0]),x->{pFilter=f[0];parlours(a);});if(pFilter.equals(f[0])){t.setBackground(a.roundBg(MainActivity.PLUM,8));}}
   for(int i=0;i<rows.length();i++){var p=rows.optJSONObject(i);if(p==null)continue;String st=p.optString("status");if(!"ALL".equals(pFilter)&&!pFilter.equals(st))continue;
    var c=a.card();var nr=a.hrow(c);var nm=a.tv(nr,p.optString("name"),14,true,MainActivity.INK);nm.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));a.parlourBadge(nr,st);
    a.tv(c,"📍 Location: "+p.optString("address"),12,false,MainActivity.INK);
    a.tv(c,"📞 Contact: "+p.optString("phone"),12,false,MainActivity.MUTED);
    a.tv(c,"Category: "+p.optString("category")+" • Owner #"+p.optLong("owner_id"),12,false,MainActivity.MUTED);
    a.divider(c);var br=a.hrow(c);
    if(!"VERIFIED".equals(st))a.btnSW(br,"Approve / Verify",MainActivity.GREEN_B,1.1f,x->verifyDialog(a,p,"VERIFIED",()->parlours(a)));
    if(!"SUSPENDED".equals(st))a.outlineW(br,"Suspend",MainActivity.RED,0.9f,x->verifyDialog(a,p,"SUSPENDED",()->parlours(a)));
    if(!"REJECTED".equals(st))a.outlineW(br,"Reject",MainActivity.INK,0.8f,x->verifyDialog(a,p,"REJECTED",()->parlours(a)));}});}
 // ---------- users ----------
 static void users(MainActivity a){a.uiRole="ADMIN";a.content.removeAllViews();tabs(a,"Users");a.tv(a.content,"Loading users...",13,false,MainActivity.MUTED);
  a.get("/api/admin/users",v->{a.content.removeAllViews();tabs(a,"Users");var rows=arr(v);
   a.tv(a.content,"User Account Management",16,true,MainActivity.INK);
   a.label(a.content,"Monitor platform customers, parlour owners, and administrators.");
   for(int i=0;i<rows.length();i++){var u=rows.optJSONObject(i);if(u==null)continue;long uid=u.optLong("user_id");String role=u.optString("role");boolean suspended="SUSPENDED".equals(u.optString("status"));
    var c=a.card();var r=a.hrow(c);String color="ADMIN".equals(role)?MainActivity.PLUM:"OWNER".equals(role)?MainActivity.CORAL:MainActivity.BLUE;
    var av=new TextView(a);av.setText(u.optString("name","?").substring(0,1));av.setTextSize(17);av.setTypeface(Typeface.DEFAULT_BOLD);av.setTextColor(Color.WHITE);av.setGravity(Gravity.CENTER);av.setBackground(a.roundBg(color,22));av.setLayoutParams(new LinearLayout.LayoutParams(a.dp(44),a.dp(44)));r.addView(av);
    var info=a.col(r,1);info.setPadding(a.dp(10),0,0,0);var nr=a.hrow(info);a.tv(nr,u.optString("name"),14,true,MainActivity.INK);var chipT=new TextView(a);chipT.setText(role);chipT.setTextSize(9);chipT.setTypeface(Typeface.DEFAULT_BOLD);chipT.setTextColor(Color.parseColor(MainActivity.INK));chipT.setBackground(a.roundBg(MainActivity.BEIGE,4));chipT.setPadding(a.dp(5),a.dp(2),a.dp(5),a.dp(2));var clp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);clp.setMargins(a.dp(6),0,0,0);chipT.setLayoutParams(clp);nr.addView(chipT);
    a.tv(info,u.optString("email")+" • "+u.optString("phone"),12,false,MainActivity.MUTED);
    a.tv(info,suspended?"Status: SUSPENDED":"Status: Active",11,true,suspended?MainActivity.RED:MainActivity.GREEN);
    if(!"ADMIN".equals(role)){var br=a.hrow(c);a.btnSW(br,suspended?"Reinstate":"Suspend",suspended?MainActivity.GREEN_B:MainActivity.RED,1,x->userDialog(a,u,suspended));}}});}
 static void userDialog(MainActivity a,JSONObject u,boolean suspended){var body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
  a.glassText(body,"User: "+u.optString("name")+" ("+u.optString("email")+")",14,true,"#FFFFFF");
  var reason=a.glassField(body,"Reason for action","Violation of platform booking rules");
  long uid=u.optLong("user_id");String target=suspended?"ACTIVE":"SUSPENDED";
  a.glass(suspended?"Reinstate User":"Suspend User Account",body,"Confirm",suspended?MainActivity.GREEN_B:MainActivity.RED,()->{try{a.post("/api/admin/users/"+uid+"/status",new JSONObject().put("status",target).put("reason",a.value(reason)),x->{a.toast("User "+target.toLowerCase()+".");users(a);});}catch(Exception e){a.message(e.getMessage());}});}
 static java.time.Instant payTime(JSONObject p){String s=p.optString("created_at");try{return java.time.OffsetDateTime.parse(s).toInstant();}catch(Exception e){try{return java.time.Instant.parse(s);}catch(Exception e2){return java.time.Instant.EPOCH;}}}
 static boolean inPeriod(JSONObject p,String period){if("ALL".equals(period))return true;var now=java.time.Instant.now();java.time.Instant cut;switch(period){case "DAY"->cut=java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();case "WEEK"->cut=now.minus(java.time.Duration.ofDays(7));case "MONTH"->cut=now.minus(java.time.Duration.ofDays(30));default->cut=now.minus(java.time.Duration.ofDays(365));}return !payTime(p).isBefore(cut);}
 // ---------- transactions ----------
 static void transactions(MainActivity a){a.uiRole="ADMIN";a.content.removeAllViews();tabs(a,"Transactions");a.tv(a.content,"Loading transactions...",13,false,MainActivity.MUTED);
  a.get("/api/admin/payments",v->{var rows=arr(v);renderTx(a,rows);});}
 static void renderTx(MainActivity a,JSONArray rows){a.content.removeAllViews();tabs(a,"Transactions");
  var hr=a.hrow(a.content);var colT=a.col(hr,1);a.tv(colT,"Payment Transactions Ledger",16,true,MainActivity.INK);a.tv(colT,"All payments across the platform. Pick a period to total them.",12,false,MainActivity.MUTED);
  var chips=a.hrow(a.content);for(String per:new String[]{"DAY","WEEK","MONTH","YEAR","ALL"}){String lbl=per.equals("DAY")?"Today":per.equals("WEEK")?"Week":per.equals("MONTH")?"Month":per.equals("YEAR")?"Year":"All";a.pill(chips,lbl,per.equals(txPeriod),x->{txPeriod=per;renderTx(a,rows);});}
  var setBox=new LinearLayout(a);setBox.setOrientation(LinearLayout.VERTICAL);a.content.addView(setBox);
  a.get("/api/admin/platform-settings",sv->{var s=obj(sv);setBox.removeAllViews();var sc=a.cardIn(setBox);a.tv(sc,"Platform Charge & Receiving Accounts",14,true,MainActivity.PLUM);
   a.tv(sc,"This charge is deducted automatically from every payment, and the earnings are recorded against these admin accounts.",11,false,MainActivity.MUTED);
   var feeF=a.field(sc,"Platform charge %",String.valueOf(s.optDouble("platform_fee_pct",5)),android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
   var bkF=a.field(sc,"Admin bKash number",s.optString("admin_bkash"),android.text.InputType.TYPE_CLASS_PHONE);
   var ngF=a.field(sc,"Admin Nagad number",s.optString("admin_nagad"),android.text.InputType.TYPE_CLASS_PHONE);
   var bankF=a.field(sc,"Admin bank account",s.optString("admin_bank"),android.text.InputType.TYPE_CLASS_TEXT);
   a.btn(sc,"Save platform settings",MainActivity.PLUM,x->{try{double fee=Double.parseDouble(a.value(feeF));a.post("/api/admin/platform-settings",new JSONObject().put("platform_fee_pct",fee).put("admin_bkash",a.value(bkF)).put("admin_nagad",a.value(ngF)).put("admin_bank",a.value(bankF)),y->a.toast("Platform settings saved."));}catch(Exception e){a.toast("Enter a valid charge %.");}});});
  double total=0,fees=0;int n=0;for(int i=0;i<rows.length();i++){var p=rows.optJSONObject(i);if(p==null||!inPeriod(p,txPeriod))continue;n++;total+=p.optDouble("amount");fees+=p.optDouble("platform_charge");}
  var sum=a.card();a.tv(sum,"Total ("+txPeriod+"): "+a.money(total),16,true,MainActivity.GREEN);a.tv(sum,n+" payments in this period",12,false,MainActivity.MUTED);if(fees>0)a.tv(sum,"Platform earnings (charges): "+a.money(fees),13,true,MainActivity.PLUM);
  if(n==0)a.label(a.content,"No payments in this period yet.");
  for(int i=0;i<rows.length();i++){var p=rows.optJSONObject(i);if(p==null||!inPeriod(p,txPeriod))continue;var c=a.card();
   var r1=a.hrow(c);var id=a.tv(r1,p.optString("transaction_id").isEmpty()?"Payment #"+p.optLong("payment_id"):p.optString("transaction_id"),13,true,MainActivity.PLUM);id.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));id.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);a.badge(r1,p.optString("status"));
   var r2=a.hrow(c);var rf=a.tv(r2,"Booking Ref: "+p.optLong("booking_id")+" • Method: "+p.optString("method"),11,false,MainActivity.MUTED);rf.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var amt=a.tv(r2,"  "+a.money(p.optDouble("amount")),14,true,MainActivity.CORAL);amt.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));amt.setGravity(Gravity.END);
   a.tv(c,"Date: "+p.optString("created_at"),11,false,MainActivity.MUTED);
   if(p.has("platform_charge"))a.tv(c,"Platform charge: "+a.money(p.optDouble("platform_charge"))+" • Owner receives: "+a.money(p.optDouble("owner_amount")),11,false,MainActivity.PLUM);}}
 // ---------- complaints ----------
 static void complaints(MainActivity a){a.uiRole="ADMIN";a.content.removeAllViews();tabs(a,"Complaints");a.tv(a.content,"Loading complaints...",13,false,MainActivity.MUTED);
  a.get("/api/admin/complaints",v->{a.content.removeAllViews();tabs(a,"Complaints");var rows=arr(v);
   a.tv(a.content,"Customer Complaints & Disputed Sessions",16,true,MainActivity.INK);
   a.label(a.content,"Investigate hygiene issues, booking cancellations, or refund disputes.");
   if(rows.length()==0)a.label(a.content,"No complaints filed yet.");
   for(int i=0;i<rows.length();i++){var cp=rows.optJSONObject(i);if(cp==null)continue;long cid=cp.optLong("complaint_id");String st=cp.optString("status");
    var c=a.card();var hr=a.hrow(c);var id=a.tv(hr,"CMP-"+cid,13,true,MainActivity.PLUM);id.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));a.badge(hr,st);
    a.tv(c,"Subject: "+(cp.optString("subject").isEmpty()?"Booking #"+cp.optLong("booking_id"):cp.optString("subject")),14,true,MainActivity.INK);
    String body=cp.optString("message").isEmpty()?cp.optString("details"):cp.optString("message");a.tv(c,body,12,false,MainActivity.INK);
    if(!cp.optString("resolution").isEmpty()){var rb=new LinearLayout(a);rb.setOrientation(LinearLayout.VERTICAL);rb.setBackground(a.roundBg(MainActivity.LAV,6));rb.setPadding(a.dp(10),a.dp(8),a.dp(10),a.dp(8));var rlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);rlp.setMargins(0,a.dp(6),0,0);rb.setLayoutParams(rlp);c.addView(rb);a.tv(rb,"Resolution: "+cp.optString("resolution"),11,false,MainActivity.PLUM);}
    if(!"RESOLVED".equals(st))a.btnH(c,"Resolve Issue",MainActivity.CORAL,34,x->resolveDialog(a,cp));}});}
 static void resolveDialog(MainActivity a,JSONObject cp){var body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
  a.glassText(body,"Subject: "+(cp.optString("subject").isEmpty()?"Booking #"+cp.optLong("booking_id"):cp.optString("subject")),14,true,"#FFFFFF");
  var notes=a.glassField(body,"Resolution Notes & Action Taken","Refund approved & parlour issued a hygiene compliance reminder.");
  long cid=cp.optLong("complaint_id");
  a.glass("Resolve Complaint CMP-"+cid,body,"Mark Resolved",MainActivity.GREEN_B,()->{try{a.post("/api/admin/complaints/"+cid+"/resolve",new JSONObject().put("resolution",a.value(notes)),x->{a.toast("Complaint resolved.");complaints(a);});}catch(Exception e){a.message(e.getMessage());}});}
 // ---------- analytics (real data, reference layout) ----------
 static void bar(MainActivity a,LinearLayout parent,String label,double pct,String stat,String color){var r=a.hrow(parent);var l=a.tv(r,label,12,false,MainActivity.INK);l.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var s=a.tv(r,stat,11,true,color);s.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));s.setGravity(Gravity.END);
  var pb=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(100);pb.setProgress((int)Math.round(pct*100));int base=Color.parseColor(color);pb.setProgressTintList(android.content.res.ColorStateList.valueOf(base));pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.argb(51,Color.red(base),Color.green(base),Color.blue(base))));var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,a.dp(8));lp.setMargins(0,a.dp(2),0,a.dp(8));pb.setLayoutParams(lp);parent.addView(pb);}
 static void analytics(MainActivity a){a.uiRole="ADMIN";a.content.removeAllViews();tabs(a,"Analytics");a.tv(a.content,"Loading analytics...",13,false,MainActivity.MUTED);
  a.loadParlourMap(()->a.get("/api/admin/parlours",pv->{var parlours=arr(pv);
   a.get("/api/admin/bookings",bv->{var bookings=arr(bv);
    a.get("/api/admin/payments",payv->{var payments=arr(payv);
     a.content.removeAllViews();tabs(a,"Analytics");
     a.tv(a.content,"Platform Growth & Performance Analytics",16,true,MainActivity.INK);
     a.label(a.content,"Booking volume distributions, category market share, and revenue leaders.");
     var c1=a.card();a.tv(c1,"Demand by Category",14,true,MainActivity.PLUM);
     var counts=new LinkedHashMap<String,Integer>();int totalP=0;for(int i=0;i<parlours.length();i++){var p=parlours.optJSONObject(i);if(p==null)continue;counts.merge(p.optString("category","UNISEX"),1,Integer::sum);totalP++;}
     String[] colors={MainActivity.CORAL,MainActivity.BLUE,MainActivity.GREEN,MainActivity.AMBER,MainActivity.PLUM};int ci=0;
     if(totalP==0)a.tv(c1,"No parlours yet.",12,false,MainActivity.MUTED);
     for(var e:counts.entrySet()){double pct=(double)e.getValue()/Math.max(1,totalP);bar(a,c1,e.getKey(),pct,Math.round(pct*100)+"% ("+e.getValue()+" Parlours)",colors[ci++%colors.length]);}
     var c2=a.card();a.tv(c2,"Top Salons by Monthly Revenue",14,true,MainActivity.CORAL);
     var bookingParlour=new HashMap<Long,Long>();for(int i=0;i<bookings.length();i++){var b=bookings.optJSONObject(i);if(b!=null)bookingParlour.put(b.optLong("booking_id"),b.optLong("parlour_id"));}
     var revenue=new HashMap<Long,Double>();for(int i=0;i<payments.length();i++){var p=payments.optJSONObject(i);if(p==null)continue;long pid=bookingParlour.getOrDefault(p.optLong("booking_id"),0L);if(pid>0)revenue.merge(pid,p.optDouble("amount"),Double::sum);}
     var sorted=new ArrayList<>(revenue.entrySet());sorted.sort((x,y)->Double.compare(y.getValue(),x.getValue()));
     if(sorted.isEmpty())a.tv(c2,"No revenue recorded yet. Revenue appears once online payments are configured.",12,false,MainActivity.MUTED);
     int rank=0;for(var e:sorted){if(rank++>=4)break;var r=a.hrow(c2);var chipT=new TextView(a);chipT.setText(String.valueOf(rank));chipT.setTextSize(10);chipT.setTypeface(Typeface.DEFAULT_BOLD);chipT.setTextColor(Color.parseColor(rank==1?"#FFFFFF":MainActivity.INK));chipT.setBackground(a.roundBg(rank==1?MainActivity.CORAL:MainActivity.BEIGE,4));chipT.setPadding(a.dp(6),a.dp(2),a.dp(6),a.dp(2));r.addView(chipT);
      var info=a.parlourInfo.get(e.getKey());var nm=a.tv(r," "+(info!=null?info[0]:"Parlour #"+e.getKey()),13,true,MainActivity.INK);nm.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var mv=a.tv(r,a.money(e.getValue()),14,true,MainActivity.INK);mv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));}
     var c3=a.card();a.tv(c3,"Platform Operational Reliability",14,true,MainActivity.INK);
     int totalB=bookings.length();long conf=0,canc=0,pend=0;for(int i=0;i<bookings.length();i++){var b=bookings.optJSONObject(i);if(b==null)continue;String st=b.optString("status");if(st.startsWith("CONFIRMED")||"RESCHEDULED".equals(st))conf++;else if("CANCELLED".equals(st)||"REJECTED".equals(st))canc++;else pend++;}
     if(totalB==0)a.tv(c3,"No bookings yet.",12,false,MainActivity.MUTED);
     else{bar(a,c3,"Confirmed Appointments",(double)conf/totalB,Math.round(conf*1000.0/totalB)/10.0+"% Confirmed",MainActivity.GREEN);bar(a,c3,"Customer Cancellations",(double)canc/totalB,Math.round(canc*1000.0/totalB)/10.0+"% Cancelled (Policy applied)",MainActivity.AMBER);bar(a,c3,"Pending / Other",(double)pend/totalB,Math.round(pend*1000.0/totalB)/10.0+"% Pending",MainActivity.RED);}
    });});}));}
 // ---------- audit log ----------
 static void audit(MainActivity a){a.uiRole="ADMIN";a.content.removeAllViews();tabs(a,"Audit Log");a.tv(a.content,"Loading audit trail...",13,false,MainActivity.MUTED);
  a.get("/api/admin/admin_actions",v->{a.content.removeAllViews();tabs(a,"Audit Log");var rows=arr(v);
   a.tv(a.content,"Administrative Audit Trail",16,true,MainActivity.INK);
   a.label(a.content,"Immutable event logging of all parlour onboarding decisions, user account penalties, and complaint resolutions.");
   if(rows.length()==0)a.label(a.content,"No admin actions recorded yet.");
   for(int i=0;i<rows.length();i++){var r=rows.optJSONObject(i);if(r==null)continue;var c=a.card();
    var hr=a.hrow(c);var chipT=new TextView(a);chipT.setText(r.optString("action"));chipT.setTextSize(10);chipT.setTypeface(Typeface.DEFAULT_BOLD);chipT.setTextColor(Color.parseColor(MainActivity.PLUM));chipT.setBackground(a.roundBg(MainActivity.LAV,4));chipT.setPadding(a.dp(6),a.dp(3),a.dp(6),a.dp(3));hr.addView(chipT);
    var ts=a.tv(hr,"  "+r.optString("created_at"),10,false,MainActivity.MUTED);
    a.tv(c,"Target Entity: "+r.optString("target_type")+" #"+r.optLong("target_id"),13,true,MainActivity.INK);
    a.tv(c,"Admin: #"+r.optLong("admin_id")+" • Ref: "+r.optLong("action_id"),11,false,MainActivity.MUTED);
    if(!r.optString("reason").isEmpty())a.tv(c,"Reason: "+r.optString("reason"),12,false,MainActivity.INK);}});}
}
