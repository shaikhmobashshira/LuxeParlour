package com.parlour.management;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Geocoder;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;

final class OwnerScreens {
 static final String[] TABS={"Dashboard","Bookings","Services","Staff","Schedules","Policies","Alerts"};
 static void tabs(MainActivity a,String active){a.chrome("");a.ownerTab=active;a.showTabs(TABS,active,t->{switch(t){case "Dashboard"->dashboard(a);case "Bookings"->bookings(a);case "Services"->services(a);case "Staff"->staff(a);case "Schedules"->schedules(a);case "Policies"->policies(a);case "Alerts"->alerts(a);}});}
 static JSONObject obj(Object v){return MainActivity.obj(v);}
 static JSONArray arr(Object v){return MainActivity.arr(v);}
 // ---------- login / register ----------
 static void login(MainActivity a,boolean register){if(!register){a.unifiedLogin();return;}a.uiRole="OWNER";a.chrome("");a.hideTabs();a.content.removeAllViews();
  var center=new LinearLayout(a);center.setOrientation(LinearLayout.VERTICAL);center.setGravity(Gravity.CENTER_HORIZONTAL);center.setPadding(0,a.dp(18),0,0);a.content.addView(center);
  var circ=new ImageView(a);circ.setImageResource(R.drawable.ic_logo_light);circ.setPadding(a.dp(13),a.dp(13),a.dp(13),a.dp(13));circ.setBackground(a.roundBg(MainActivity.CORAL,34));circ.setLayoutParams(new LinearLayout.LayoutParams(a.dp(68),a.dp(68)));center.addView(circ);
  var title=a.tv(center,register?"Create Owner Account":"Salon Owner Portal",22,true,MainActivity.INK);title.setGravity(Gravity.CENTER);
  var sub=a.tv(center,"Manage your salon bookings, staff, prices, and policies",13,false,MainActivity.MUTED);sub.setGravity(Gravity.CENTER);
  a.spacer(center,10);
  EditText name=null,phone=null;
  if(register){name=a.field(center,"Owner Full Name *","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PERSON_NAME);phone=a.field(center,"Owner Phone *","",InputType.TYPE_CLASS_PHONE);}
  var email=a.field(center,"Owner Email","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
  var pass=a.field(center,"Password","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
  final EditText nameF=name,phoneF=phone;
  a.btn(center,register?"Create Owner Account":"Sign In to Salon Portal",MainActivity.CORAL,v->{
   try{if(register){var body=new JSONObject().put("name",a.value(nameF)).put("email",a.value(email)).put("phone",a.value(phoneF)).put("password",a.value(pass)).put("role","OWNER");
     a.post("/api/auth/register",body,x->{a.saveSession(obj(x));a.toast("Welcome! Now create your parlour profile.");parlourForm(a,null);});}
    else a.post("/api/auth/login",new JSONObject().put("email",a.value(email)).put("password",a.value(pass)),x->{var r=obj(x);if(!"OWNER".equals(r.optJSONObject("user")==null?"":r.optJSONObject("user").optString("role"))){a.message("This account is not a parlour owner account.");return;}a.saveSession(r);dashboard(a);});
   }catch(Exception e){a.message(e.getMessage());}});
  var or=a.hrow(center);var l1=new View(a);l1.setBackgroundColor(Color.parseColor(MainActivity.BORDER));l1.setLayoutParams(new LinearLayout.LayoutParams(0,a.dp(1),1));or.addView(l1);var ot=a.tv(or," OR ",11,false,MainActivity.MUTED);var l2=new View(a);l2.setBackgroundColor(Color.parseColor(MainActivity.BORDER));l2.setLayoutParams(new LinearLayout.LayoutParams(0,a.dp(1),1));or.addView(l2);
  a.outline(center,register?"Sign In to Existing Salon":"New Owner? Register Salon Profile",MainActivity.CORAL,v->login(a,!register));
  var fp=a.tv(center,"Forgot password",13,false,MainActivity.MUTED);fp.setGravity(Gravity.CENTER);fp.setPadding(0,a.dp(10),0,0);fp.setOnClickListener(v->a.forgotPassword());}
 // ---------- parlour form ----------
 static void parlourForm(MainActivity a,JSONObject existing){a.chrome("");a.hideTabs();a.content.removeAllViews();
  long pid=existing==null?0:existing.optLong("parlour_id");
  a.screenBar(existing==null?"Register Salon Profile":"Business profile",()->{if("OWNER".equals(a.vm.repository.role()))dashboard(a);else login(a,false);},null,null);
  if(existing==null)a.label(a.content,"Create your salon profile to receive online client bookings");
  var card=a.card();var hr=a.hrow(card);var circ=new TextView(a);circ.setText("🏬");circ.setTextSize(15);circ.setGravity(Gravity.CENTER);circ.setBackground(a.roundBg(MainActivity.CORAL,16));circ.setLayoutParams(new LinearLayout.LayoutParams(a.dp(32),a.dp(32)));hr.addView(circ);var ht=a.tv(hr,"  1. Salon Business Profile",15,true,MainActivity.INK);
  var name=a.field(card,"Salon / Parlour Name *",existing==null?"":existing.optString("name"),InputType.TYPE_CLASS_TEXT);
  a.tv(card,"Salon Category *",12,true,MainActivity.INK);
  final String[] cat={existing==null?"WOMEN":existing.optString("category","WOMEN")};
  var crow=a.hrow(card);String[][] cats={{"WOMEN","Women Only"},{"MEN","Men Grooming"},{"UNISEX","Unisex Salon"},{"SPA","Spa & Wellness"},{"HAIR","Hair"},{"SKIN","Skin"},{"NAILS","Nails"}};
  var chipViews=new ArrayList<TextView>();
  Runnable paintChips=()->{for(int i=0;i<chipViews.size();i++){var t=chipViews.get(i);boolean sel=catsFinal(cats,i).equals(cat[0]);if(sel){t.setBackground(a.roundBg(MainActivity.CORAL,16));t.setTextColor(Color.WHITE);t.setTypeface(Typeface.DEFAULT_BOLD);}else{t.setBackground(a.strokeBg("#FFFFFF",MainActivity.BORDER,16));t.setTextColor(Color.parseColor(MainActivity.INK));t.setTypeface(Typeface.DEFAULT);}}};
  for(int i=0;i<cats.length;i++){final int idx=i;var t=a.chip(crow,cats[i][1],cats[i][0].equals(cat[0]),MainActivity.CORAL,v->{cat[0]=cats[idx][0];paintChips.run();});chipViews.add(t);}
  var address=a.field(card,"Complete Address *",existing==null?"":existing.optString("address"),InputType.TYPE_CLASS_TEXT);
  a.label(card,"Only your address is needed — we find the location automatically.");
  var phone=a.field(card,"Salon Official Phone *",existing==null?"":existing.optString("phone"),InputType.TYPE_CLASS_PHONE);
  var desc=a.field(card,"Short Description & Highlights",existing==null?"":existing.optString("description"),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
  a.btn(a.content,existing==null?"Create Parlour Profile & Launch Dashboard":"Save changes",MainActivity.CORAL,v->{
   String nm=a.value(name),ad=a.value(address);if(nm.isEmpty()||ad.isEmpty()){a.toast("Please fill in Salon Name and Complete Address.");return;}
   a.vm.loading.setValue(true);
   new Thread(()->{Double la=null,ln=null;try{var results=new Geocoder(a).getFromLocationName(ad,1);if(results!=null&&!results.isEmpty()){la=results.get(0).getLatitude();ln=results.get(0).getLongitude();}}catch(Exception ignored){}
    final Double laF=la,lnF=ln;
    a.runOnUiThread(()->{a.vm.loading.setValue(false);try{var body=new JSONObject().put("name",nm).put("category",cat[0]).put("address",ad).put("phone",a.value(phone)).put("description",a.value(desc)).put("timezone","Asia/Dhaka");if(laF!=null){body.put("lat",laF);body.put("lng",lnF);}
     if(existing==null)a.post("/api/owner/parlours",body,x->{a.ownerPid=obj(x).optLong("parlour_id");a.toast("Parlour created. It will appear publicly after admin verification.");dashboard(a);});
     else a.put("/api/owner/parlours/"+pid,body,x->{a.toast("Profile saved. Status returns to pending verification.");dashboard(a);});
    }catch(Exception e){a.message(e.getMessage());}});}).start();});
  if(existing==null){var foot=a.tv(a.content,"Already registered? Sign In to Existing Salon",13,true,MainActivity.CORAL);foot.setGravity(Gravity.CENTER);foot.setPadding(0,a.dp(10),0,0);foot.setOnClickListener(v->login(a,false));}}
 static String catsFinal(String[][] cats,int i){return cats[i][0];}
 // ---------- dashboard ----------
 static void withParlour(MainActivity a,Consumer<Long> go){if(a.ownerPid>0){go.accept(a.ownerPid);return;}
  a.get("/api/owner/parlours",v->{var ps=arr(v);if(ps.length()==0){a.content.removeAllViews();tabs(a,"Dashboard");a.emptyState(a.content,"🏬","No parlour yet","Register your parlour to receive bookings.","Register a parlour",()->parlourForm(a,null));return;}a.ownerPid=ps.optJSONObject(0).optLong("parlour_id");go.accept(a.ownerPid);});}
 static void status(MainActivity a,long bid,String st){try{a.post("/api/owner/bookings/"+bid+"/status",new JSONObject().put("status",st),v->{var r=obj(v);if("COMPLETED".equals(st)&&r.optBoolean("removed"))a.toast("Service completed - booking record removed.");else a.toast("Booking "+st.toLowerCase()+".");dashboard(a);});}catch(Exception e){a.message(e.getMessage());}}
 static void rejectWithReason(MainActivity a,long bid,Runnable after){var body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
  a.glassText(body,"The customer will see this reason in their notifications.",13,false,MainActivity.GLASS_SUB);
  var reason=a.glassField(body,"Reason for rejection (e.g. stylist on leave)","");
  a.glass("Reject this booking?",body,"Reject Booking",MainActivity.RED,()->{try{a.post("/api/owner/bookings/"+bid+"/status",new JSONObject().put("status","REJECTED").put("reason",a.value(reason)),x->{a.toast("Booking rejected.");after.run();});}catch(Exception e){a.message(e.getMessage());}});}
 static void dashboard(MainActivity a){a.uiRole="OWNER";a.content.removeAllViews();tabs(a,"Dashboard");a.tv(a.content,"Loading your salon...",13,false,MainActivity.MUTED);
  a.get("/api/owner/parlours",v->{var ps=arr(v);a.content.removeAllViews();tabs(a,"Dashboard");
   if(ps.length()==0){a.emptyState(a.content,"🏬","No parlour yet","Register your parlour to receive bookings.","Register a parlour",()->parlourForm(a,null));return;}
   if(a.ownerPid==0)a.ownerPid=ps.optJSONObject(0).optLong("parlour_id");
   JSONObject mine=null;for(int i=0;i<ps.length();i++){var p=ps.optJSONObject(i);if(p!=null&&p.optLong("parlour_id")==a.ownerPid)mine=p;}
   if(mine==null){mine=ps.optJSONObject(0);a.ownerPid=mine.optLong("parlour_id");}
   final JSONObject parlour=mine;
   if(ps.length()>1){var srow=a.hrow(a.content);for(int i=0;i<ps.length();i++){var p=ps.optJSONObject(i);if(p==null)continue;final long id=p.optLong("parlour_id");a.chip(srow,p.optString("name"),id==a.ownerPid,MainActivity.CORAL,x->{a.ownerPid=id;a.detailCache.remove(id);dashboard(a);});}}
   a.fetchDetail(a.ownerPid,d->{var policy=a.firstPolicy(d);var staffArr=d.optJSONArray("staff");
    a.get("/api/owner/bookings",bv->{var bookings=arr(bv);
     a.get("/api/notifications",nv->{var notifs=arr(nv);int unread=0;for(int i=0;i<notifs.length();i++){var n=notifs.optJSONObject(i);if(n!=null&&!a.readIds.contains(n.optLong("notification_id")))unread++;}
      a.setBell(unread);
      var hc=a.card();var hr=a.hrow(hc);var nm=a.tv(hr,parlour.optString("name"),16,true,MainActivity.INK);nm.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));a.parlourBadge(hr,parlour.optString("status"));
      a.tv(hc,parlour.optString("address")+" • Category: "+parlour.optString("category"),12,false,MainActivity.MUTED);
      var th=a.todayHours(d);a.tv(hc,"Hours: "+(th==null?"not set":th[0]+" - "+th[1])+" • Advance Req: "+(policy==null?0:policy.optInt("min_advance_pct"))+"%",11,false,MainActivity.CORAL);
      var polChip=a.tv(hc,"Booking policies →",12,true,MainActivity.CORAL);polChip.setOnClickListener(x->policies(a));
      if(unread>0){var ac=a.card();ac.setBackground(a.strokeBg(MainActivity.CORAL_L,MainActivity.CORAL,12));var ar=a.hrow(ac);var al=a.tv(ar,"🔔 You have "+unread+" unread salon alert(s)",12,true,MainActivity.INK);al.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var va=a.tv(ar,"   View Alerts →",11,true,MainActivity.CORAL);va.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));va.setOnClickListener(x->alerts(a));ac.setOnClickListener(x->alerts(a));}
      // KPIs
      int total=0,today=0;double revenue=0;var mineBookings=new ArrayList<JSONObject>();
      for(int i=0;i<bookings.length();i++){var b=bookings.optJSONObject(i);if(b==null||b.optLong("parlour_id")!=a.ownerPid)continue;mineBookings.add(b);total++;if(!"CANCELLED".equals(b.optString("status")))revenue+=b.optDouble("total_amount");
       try{if(java.time.Instant.parse(b.optString("start_at")).atZone(a.zone).toLocalDate().equals(LocalDate.now(a.zone)))today++;}catch(Exception ignored){}}
      int activeStaff=0;if(staffArr!=null)for(int i=0;i<staffArr.length();i++){var s=staffArr.optJSONObject(i);if(s!=null&&"ACTIVE".equals(s.optString("status")))activeStaff++;}
      var k1=a.hrow(a.content);metric(a,k1,"Total Bookings",String.valueOf(total),MainActivity.CORAL);metric(a,k1,"Today's Agenda",String.valueOf(today),MainActivity.BLUE);
      var k2=a.hrow(a.content);metric(a,k2,"Gross Revenue",a.money(revenue),MainActivity.GREEN);metric(a,k2,"Active Stylists",String.valueOf(activeStaff),MainActivity.PLUM);
      a.tv(a.content,"Quick Management Actions",14,true,MainActivity.INK);
      var qa=a.hrow(a.content);a.btnW(qa,"Bookings",MainActivity.CORAL,1,x->bookings(a));a.btnW(qa,"Block Slots",MainActivity.PLUM,1,x->schedules(a));a.btnW(qa,"Services",MainActivity.INK,1,x->services(a));
      var qh=a.hrow(a.content);var qt=a.tv(qh,"Upcoming Bookings Queue",15,true,MainActivity.INK);qt.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var vall=a.tv(qh,"View All →",12,true,MainActivity.CORAL);vall.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));vall.setOnClickListener(x->bookings(a));
      mineBookings.sort(Comparator.comparing(b->b.optString("start_at")));int shown=0;
      for(var b:mineBookings){String st=b.optString("status");if(!("PENDING_OWNER".equals(st)||"CONFIRMED".equals(st)))continue;if(shown++>=4)break;long bid=b.optLong("booking_id");
       var qc=a.card();var qr=a.hrow(qc);var cn=a.tv(qr,b.optString("customer_name")+" ("+b.optString("customer_phone")+")",13,true,MainActivity.INK);cn.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));a.badge(qr,st);
       a.tv(qc,"📅 "+a.fmtDate(b.optString("start_at"))+" at "+a.fmtTime(b.optString("start_at"))+" • Stylist: "+a.staffName(d,b.optLong("staff_id")),12,false,MainActivity.MUTED);
       var items=b.optJSONArray("items");if(items!=null){var names=new StringBuilder();for(int j=0;j<items.length();j++){var it=items.optJSONObject(j);if(it!=null){if(names.length()>0)names.append(", ");names.append(it.optString("name"));}}a.tv(qc,"✂ Services: "+names,12,false,MainActivity.INK);}
       var pr=a.hrow(qc);var pt=a.tv(pr,a.money(b.optDouble("total_amount"))+" (Due: "+a.money(b.optDouble("due_amount"))+")",12,true,MainActivity.CORAL);pt.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
       if("PENDING_OWNER".equals(st)){a.btnSW(pr,"Confirm",MainActivity.GREEN,0.8f,x->status(a,bid,"CONFIRMED"));a.outlineW(pr,"Reject",MainActivity.RED,0.7f,x->rejectWithReason(a,bid,()->dashboard(a)));}
       if("CONFIRMED".equals(st)){a.btnSW(pr,"Complete",MainActivity.BLUE,0.8f,x->status(a,bid,"COMPLETED"));a.outlineW(pr,"Move",MainActivity.INK,0.7f,x->ownerReschedule(a,b));}
       qc.setOnClickListener(x->a.booking(bid));}
      if(shown==0)a.label(a.content,"No upcoming bookings right now.");
      a.tv(a.content,"More",14,true,MainActivity.INK);
      a.outline(a.content,"Edit business profile",MainActivity.INK,x->parlourForm(a,parlour));
      a.outline(a.content,"Upload parlour photo",MainActivity.INK,x->{a.photoParlourId=a.ownerPid;a.photoPicker.launch(new String[]{"image/*"});});
      a.outline(a.content,"Assign staff to services",MainActivity.INK,x->assign(a,a.ownerPid));
      a.outline(a.content,"Past bookings (archive)",MainActivity.INK,x->archive(a));
      a.outline(a.content,"Send offer to past customers",MainActivity.INK,x->offer(a));
      a.outline(a.content,"Payment accounts (bKash / Nagad / Bank)",MainActivity.INK,x->paymentSettings(a));
      a.outline(a.content,"Register another parlour",MainActivity.CORAL,x->parlourForm(a,null));
     });});});});}
 static void archive(MainActivity a){a.uiRole="OWNER";a.content.removeAllViews();tabs(a,"Dashboard");a.screenBar("Past Bookings (Archive)",()->dashboard(a),null,null);
  a.tv(a.content,"Loading completed visits...",13,false,MainActivity.MUTED);
  a.get("/api/owner/archive",v->{a.content.removeAllViews();tabs(a,"Dashboard");a.screenBar("Past Bookings (Archive)",()->dashboard(a),null,null);
   var rows=arr(v);if(rows.length()==0){a.emptyState(a.content,"🗂","No past bookings yet","Completed visits will appear here with full details, even after the booking record is removed.",null,null);return;}
   a.tv(a.content,rows.length()+" completed visit(s)",12,false,MainActivity.MUTED);
   for(int i=0;i<rows.length();i++){var r=rows.optJSONObject(i);if(r==null)continue;var c=a.card();
    a.tv(c,r.optString("customer_name")+" • "+r.optString("customer_phone"),13,true,MainActivity.INK);
    a.tv(c,"🏬 "+r.optString("parlour_name"),12,false,MainActivity.MUTED);
    a.tv(c,"✂ "+r.optString("services_text"),12,false,MainActivity.INK);
    if(r.optString("staff_name").length()>0)a.tv(c,"💇 "+r.optString("staff_name"),12,false,MainActivity.MUTED);
    var hr=a.hrow(c);var cd=a.tv(hr,"Completed: "+a.fmtDate(r.optString("completed_at")),11,false,MainActivity.MUTED);cd.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));a.tv(hr,"   "+a.money(r.optDouble("total_amount")),13,true,MainActivity.CORAL);
    var arow=a.hrow(c);final long ccid=r.optLong("customer_id");final long bbid=r.optLong("booking_id");a.outlineW(arow,"👤 Profile",MainActivity.PLUM,1,x->customerProfile(a,ccid,bbid));a.outlineW(arow,"⭐ Rate customer",MainActivity.CORAL,1,x->rateCustomer(a,ccid,bbid));}});}
 static void offer(MainActivity a){a.uiRole="OWNER";a.content.removeAllViews();tabs(a,"Dashboard");a.screenBar("Send Offer",()->dashboard(a),null,null);
  withParlour(a,pid->{a.content.removeAllViews();tabs(a,"Dashboard");a.screenBar("Send Offer",()->dashboard(a),null,null);
   a.tv(a.content,"Send an offer to every customer who has completed a visit at this parlour. It arrives in their notifications.",12,false,MainActivity.MUTED);
   var title=a.field(a.content,"Offer title (e.g. 20% off this week)","",android.text.InputType.TYPE_CLASS_TEXT);
   var body=a.field(a.content,"Offer details","",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
   a.btn(a.content,"Send offer",MainActivity.CORAL,v->{String t=a.value(title),b=a.value(body);if(t.isEmpty()||b.isEmpty()){a.toast("Write a title and details first.");return;}try{a.post("/api/owner/offers",new JSONObject().put("parlour_id",pid).put("title",t).put("body",b),x->{int sent=obj(x).optInt("sent");a.toast(sent>0?"Offer sent to "+sent+" past customer(s).":"No past customers yet for this parlour.");dashboard(a);});}catch(Exception e){a.message(e.getMessage());}});});}
 static void paymentSettings(MainActivity a){a.uiRole="OWNER";a.content.removeAllViews();tabs(a,"Dashboard");a.screenBar("Payment Accounts",()->dashboard(a),null,null);
  withParlour(a,pid->{a.get("/api/owner/parlours/"+pid+"/payment_methods",v->{a.content.removeAllViews();tabs(a,"Dashboard");a.screenBar("Payment Accounts",()->dashboard(a),null,null);
   a.tv(a.content,"Customers see these accounts at checkout and pay you directly. The platform charge set by the admin is recorded automatically on every payment.",12,false,MainActivity.MUTED);
   var rows=arr(v);for(int i=0;i<rows.length();i++){var r=rows.optJSONObject(i);if(r==null)continue;final long mid=r.optLong("payment_method_id");var c=a.card();
    a.tv(c,r.optString("method")+" • "+r.optString("account_name"),13,true,MainActivity.INK);
    a.tv(c,r.optString("account_number")+(r.optString("bank_name").length()>0?" • "+r.optString("bank_name")+" "+r.optString("branch"):""),12,false,MainActivity.MUTED);
    a.outline(c,"Remove",MainActivity.RED,x->a.delete("/api/owner/parlours/"+pid+"/payment_methods/"+mid,y->paymentSettings(a)));}
   var form=a.card();a.tv(form,"Add an account",14,true,MainActivity.INK);
   final String[] method={"BKASH"};var mrow=a.hrow(form);for(String m:new String[]{"BKASH","NAGAD","BANK"})a.chip(mrow,m,m.equals(method[0]),MainActivity.CORAL,x->{method[0]=m;chipRefresh(a,mrow,method);});
   var nameF=a.field(form,"Account holder name","",android.text.InputType.TYPE_CLASS_TEXT);
   var numF=a.field(form,"Account / wallet number","",android.text.InputType.TYPE_CLASS_PHONE);
   var bankF=a.field(form,"Bank name (for BANK only)","",android.text.InputType.TYPE_CLASS_TEXT);
   var branchF=a.field(form,"Branch (for BANK only)","",android.text.InputType.TYPE_CLASS_TEXT);
   a.btn(form,"Save account",MainActivity.CORAL,x->{try{var body=new JSONObject().put("method",method[0]).put("account_name",a.value(nameF)).put("account_number",a.value(numF)).put("bank_name",a.value(bankF)).put("branch",a.value(branchF)).put("status","ACTIVE");a.post("/api/owner/parlours/"+pid+"/payment_methods",body,y->{a.toast("Payment account saved.");paymentSettings(a);});}catch(Exception e){a.message(e.getMessage());}});});});}
 static void chipRefresh(MainActivity a,LinearLayout row,String[] method){for(int i=0;i<row.getChildCount();i++){var t=(android.widget.TextView)row.getChildAt(i);boolean sel=t.getText().toString().equals(method[0]);t.setBackground(sel?a.roundBg(MainActivity.CORAL,16):a.strokeBg("#FFFFFF",MainActivity.BORDER,16));t.setTextColor(android.graphics.Color.parseColor(sel?"#FFFFFF":MainActivity.INK));}}
 static void metric(MainActivity a,LinearLayout row,String title,String value,String accent){var c=a.cardIn(row);var lp=(LinearLayout.LayoutParams)c.getLayoutParams();lp.width=0;lp.weight=1;c.setLayoutParams(lp);
  var hr=a.hrow(c);var dot=new TextView(a);dot.setBackground(a.roundBg(accent,14));dot.setLayoutParams(new LinearLayout.LayoutParams(a.dp(28),a.dp(28)));hr.addView(dot);var t=a.tv(hr,"  "+title,11,false,MainActivity.MUTED);
  a.tv(c,value,18,true,MainActivity.INK);}
 // ---------- bookings tab ----------
 static String bFilter="All";
 // ---------- customer profile & owner feedback ----------
 static void customerProfile(MainActivity a,long cid,long rateBid){a.uiRole="OWNER";a.content.removeAllViews();tabs(a,"Bookings");a.screenBar("Customer Profile",()->bookings(a),null,null);
  a.tv(a.content,"Loading customer profile...",13,false,MainActivity.MUTED);
  a.get("/api/owner/customers/"+cid,v->{a.content.removeAllViews();tabs(a,"Bookings");a.screenBar("Customer Profile",()->bookings(a),null,null);
   var o=obj(v);var cu=o.optJSONObject("customer");if(cu==null){a.message("Customer not found.");return;}
   var c=a.card();a.head(c,"👤  "+cu.optString("name"));
   a.kv(c,"Phone",cu.optString("phone"));if(cu.optString("email").length()>0)a.kv(c,"Email",cu.optString("email"));
   a.kv(c,"Owner rating",o.optInt("reviews_count")==0?"No ratings yet":String.format(Locale.US,"★ %.1f",o.optDouble("avg_rating"))+" ("+o.optInt("reviews_count")+" review(s))");
   a.kv(c,"Completed visits",String.valueOf(o.optLong("completed_count")));a.kv(c,"Cancelled / rejected",String.valueOf(o.optLong("cancelled_count")));
   if(rateBid>0)a.btn(c,"⭐ Rate this customer",MainActivity.CORAL,x->rateCustomer(a,cid,rateBid));
   var rc=a.card();a.head(rc,"⭐ What other owners say");
   var revs=o.optJSONArray("reviews");if(revs==null||revs.length()==0)a.tv(rc,"No owner has reviewed this customer yet.",12,false,MainActivity.MUTED);
   else for(int i=0;i<revs.length();i++){var r=revs.optJSONObject(i);if(r==null)continue;int sc=r.optInt("score");a.tv(rc,"★".repeat(sc)+"☆".repeat(5-sc)+"  — "+r.optString("parlour_name"),13,true,MainActivity.INK);if(r.optString("review").length()>0)a.tv(rc,"“"+r.optString("review")+"”",12,false,MainActivity.MUTED);a.tv(rc,a.fmtDate(r.optString("created_at")),11,false,MainActivity.MUTED);}
   var hc=a.card();a.head(hc,"🧾 Booking history (all parlours)");
   var hist=o.optJSONArray("history");if(hist==null||hist.length()==0)a.tv(hc,"No bookings found for this customer.",12,false,MainActivity.MUTED);
   else for(int i=0;i<hist.length();i++){var h=hist.optJSONObject(i);if(h==null)continue;String when=h.optString("start_at");if(when.isEmpty())when=h.optString("completed_at");
    a.tv(hc,"🏬 "+h.optString("parlour_name")+" • "+a.fmtDate(when),12,true,MainActivity.INK);
    String svc=h.optString("services_text");a.tv(hc,(svc.isEmpty()?"Booking":"✂ "+svc)+" • "+("COMPLETED".equals(h.optString("kind"))?"Completed":h.optString("status"))+" • "+a.money(h.optDouble("total_amount")),12,false,MainActivity.MUTED);}});}
 static void rateCustomer(MainActivity a,long cid,long bid){var body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
  a.glassText(body,"How was this customer? Your review helps other parlour owners.",13,false,MainActivity.GLASS_SUB);
  final int[] score={5};var row=new LinearLayout(a);row.setOrientation(LinearLayout.HORIZONTAL);body.addView(row);
  TextView[] starViews=new TextView[5];
  for(int i=0;i<5;i++){final int n=i+1;var s=new TextView(a);s.setText("★");s.setTextSize(30);s.setPadding(a.dp(6),a.dp(4),a.dp(6),a.dp(4));s.setTextColor(Color.parseColor("#FFB300"));row.addView(s);starViews[i]=s;s.setOnClickListener(x->{score[0]=n;for(int j=0;j<5;j++)starViews[j].setTextColor(Color.parseColor(j<n?"#FFB300":"#8E8EA3"));});}
  var rev=a.glassField(body,"Write a short review (optional)","");
  a.glass("Rate Customer",body,"Submit Review",MainActivity.CORAL,()->{try{a.post("/api/owner/customers/"+cid+"/rate",new JSONObject().put("booking_id",bid).put("score",score[0]).put("review",a.value(rev)),x->{a.toast("Thank you — your review is saved.");customerProfile(a,cid,0);});}catch(Exception e){a.message(e.getMessage());}});}
 static void bookings(MainActivity a){a.uiRole="OWNER";a.content.removeAllViews();tabs(a,"Bookings");a.tv(a.content,"Loading bookings...",13,false,MainActivity.MUTED);
  withParlour(a,pid->a.fetchDetail(pid,d->a.get("/api/owner/bookings",v->{a.content.removeAllViews();tabs(a,"Bookings");
   a.tv(a.content,"Booking Queue & Operations",16,true,MainActivity.INK);
   var all=arr(v);var mine=new ArrayList<JSONObject>();for(int i=0;i<all.length();i++){var b=all.optJSONObject(i);if(b!=null&&b.optLong("parlour_id")==pid)mine.add(b);}
   a.tv(a.content,mine.size()+" Bookings",12,false,MainActivity.MUTED);
   var acts=a.hrow(a.content);a.btnW(acts,"➕ Book for a customer",MainActivity.CORAL,1,x->bookForCustomer(a,pid,d));a.outlineW(acts,"📅 Booked slots",MainActivity.PLUM,1,x->slotDay(a,pid,d));
   var frow=a.hrow(a.content);String[] fs={"All","Pending","Confirmed","Done","Cancelled"};for(String f:fs)a.pill(frow,f,bFilter.equals(f),x->{bFilter=f;bookings(a);});
   var box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);a.content.addView(box);
   for(var b:mine){String st=b.optString("status");boolean show=switch(bFilter){case "Pending"->Set.of("PENDING_OWNER","PENDING_PAYMENT","CANCEL_PENDING").contains(st);case "Confirmed"->Set.of("CONFIRMED","RESCHEDULED").contains(st);case "Done"->"COMPLETED".equals(st);case "Cancelled"->Set.of("CANCELLED","REJECTED").contains(st);default->true;};
    if(!show)continue;long bid=b.optLong("booking_id");var c=a.cardIn(box);
    a.tv(c,b.optString("customer_name"),14,true,MainActivity.INK);
    a.tv(c,"📞 "+b.optString("customer_phone")+" • Ref: "+bid,11,false,MainActivity.MUTED);
    var br=a.hrow(c);a.badge(br,st);a.payBadge(br,b.optString("payment_method"));
    a.tv(c,"📅 Date: "+a.fmtDate(b.optString("start_at"))+" at "+a.fmtTime(b.optString("start_at")),12,false,MainActivity.INK);
    a.tv(c,"👤 Stylist: "+a.staffName(d,b.optLong("staff_id")),12,false,MainActivity.MUTED);
    var items=b.optJSONArray("items");if(items!=null){var names=new StringBuilder();for(int j=0;j<items.length();j++){var it=items.optJSONObject(j);if(it!=null){if(names.length()>0)names.append(", ");names.append(it.optString("name"));}}a.tv(c,"✂ Services: "+names,12,false,MainActivity.INK);}
    if(b.optString("status_reason").length()>0)a.tv(c,"💬 Reason: "+b.optString("status_reason"),12,false,MainActivity.RED);
    var crow=a.hrow(c);a.outlineW(crow,"👤 Customer profile",MainActivity.PLUM,1,x->customerProfile(a,b.optLong("customer_id"),0));
    a.divider(c);var pr=a.hrow(c);var pt=a.tv(pr,a.money(b.optDouble("total_amount"))+" (Due: "+a.money(b.optDouble("due_amount"))+")",13,true,MainActivity.CORAL);pt.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
    if("PENDING_OWNER".equals(st)){a.btnSW(pr,"Confirm",MainActivity.GREEN,0.9f,x->status(a,bid,"CONFIRMED"));a.outlineW(pr,"Reject",MainActivity.RED,0.8f,x->rejectWithReason(a,bid,()->bookings(a)));}
    if("CONFIRMED".equals(st)){a.btnSW(pr,"Complete",MainActivity.BLUE,0.9f,x->status(a,bid,"COMPLETED"));a.outlineW(pr,"Cash",MainActivity.GREEN,0.7f,x->a.post("/api/owner/bookings/"+bid+"/cash-payment",new JSONObject(),y->bookings(a)));}
    if("PENDING_OWNER".equals(st)||"CONFIRMED".equals(st)){var ar=a.hrow(c);a.outlineW(ar,"Reschedule",MainActivity.INK,1,x->ownerReschedule(a,b));if("CONFIRMED".equals(st))a.outlineW(ar,"Reject",MainActivity.RED,1,x->rejectWithReason(a,bid,()->bookings(a)));}
    if("CANCEL_PENDING".equals(st))a.btnSW(pr,"Approve cancellation",MainActivity.RED,1.2f,x->status(a,bid,"CANCELLED"));
    c.setOnClickListener(x->a.booking(bid));}
  })));}
 // ---------- owner booking tools: reschedule, booked-slots day view, book on behalf ----------
 static LocalDate rsDate,sdDate;
 static void ownerReschedule(MainActivity a,JSONObject b){a.uiRole="OWNER";a.content.removeAllViews();tabs(a,"Bookings");a.screenBar("Reschedule Booking",()->bookings(a),null,null);
  long bid=b.optLong("booking_id");long pid=b.optLong("parlour_id");long staffId=b.optLong("staff_id");
  a.tv(a.content,b.optString("customer_name")+" • currently "+a.fmtDate(b.optString("start_at"))+" at "+a.fmtTime(b.optString("start_at")),13,true,MainActivity.INK);
  a.tv(a.content,"Pick a new date and time. Times already booked show as Booked and cannot be chosen.",12,false,MainActivity.MUTED);
  a.get("/api/bookings/"+bid,v->{var det=obj(v);var items=det.optJSONArray("items");var ids=new ArrayList<Long>();if(items!=null)for(int i=0;i<items.length();i++){var it=items.optJSONObject(i);if(it!=null)ids.add(it.optLong("service_id"));}
   if(rsDate==null||rsDate.isBefore(LocalDate.now(a.zone)))rsDate=LocalDate.now(a.zone).plusDays(1);
   var dscroll=new android.widget.HorizontalScrollView(a);dscroll.setHorizontalScrollBarEnabled(false);a.content.addView(dscroll);var drow=new LinearLayout(a);drow.setOrientation(LinearLayout.HORIZONTAL);dscroll.addView(drow);
   var today=LocalDate.now(a.zone);for(int i=0;i<7;i++){final LocalDate day=today.plusDays(i);boolean sel=day.equals(rsDate);
    var cell=new TextView(a);cell.setText(day.format(DateTimeFormatter.ofPattern("EEE d MMM",Locale.ENGLISH)));cell.setTextSize(11);if(sel)cell.setTypeface(Typeface.DEFAULT_BOLD);cell.setTextColor(Color.parseColor(sel?"#FFFFFF":MainActivity.INK));cell.setPadding(a.dp(10),a.dp(7),a.dp(10),a.dp(7));cell.setBackground(sel?a.roundBg(MainActivity.CORAL,8):a.strokeBg("#FFFFFF",MainActivity.BORDER,8));var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(a.dp(2),a.dp(2),a.dp(2),a.dp(2));cell.setLayoutParams(lp);cell.setOnClickListener(x->{rsDate=day;ownerReschedule(a,b);});drow.addView(cell);}
   var box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);a.content.addView(box);
   a.tv(box,"Loading slots... this can take a minute.",13,false,MainActivity.MUTED);
   var csv=new StringBuilder();for(int i=0;i<ids.size();i++){if(i>0)csv.append(',');csv.append(ids.get(i));}
   a.fetchSlotsFull(pid,csv.toString(),staffId>0?staffId:null,rsDate,(slots,bk)->a.slotGridBooked(box,slots,bk,null,s->a.confirm("Move this booking to "+a.fmtDT(s.optString("start_at"))+"?",()->{try{a.post("/api/owner/bookings/"+bid+"/reschedule",new JSONObject().put("start_at",s.optString("start_at")).put("staff_id",staffId),x->{a.toast("Booking rescheduled.");bookings(a);});}catch(Exception e){a.message(e.getMessage());}})));});}
 static void slotDay(MainActivity a,long pid,JSONObject d){a.uiRole="OWNER";a.content.removeAllViews();tabs(a,"Bookings");a.tv(a.content,"Loading booked slots...",13,false,MainActivity.MUTED);
  if(sdDate==null)sdDate=LocalDate.now(a.zone);
  a.get("/api/owner/bookings",v->{a.content.removeAllViews();tabs(a,"Bookings");a.screenBar("Booked Slots",()->bookings(a),null,null);
   var dscroll=new android.widget.HorizontalScrollView(a);dscroll.setHorizontalScrollBarEnabled(false);a.content.addView(dscroll);var drow=new LinearLayout(a);drow.setOrientation(LinearLayout.HORIZONTAL);dscroll.addView(drow);
   var today=LocalDate.now(a.zone);for(int i=0;i<7;i++){final LocalDate day=today.plusDays(i);boolean sel=day.equals(sdDate);
    var cell=new TextView(a);cell.setText(day.format(DateTimeFormatter.ofPattern("EEE d MMM",Locale.ENGLISH)));cell.setTextSize(11);if(sel)cell.setTypeface(Typeface.DEFAULT_BOLD);cell.setTextColor(Color.parseColor(sel?"#FFFFFF":MainActivity.INK));cell.setPadding(a.dp(10),a.dp(7),a.dp(10),a.dp(7));cell.setBackground(sel?a.roundBg(MainActivity.CORAL,8):a.strokeBg("#FFFFFF",MainActivity.BORDER,8));var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(a.dp(2),a.dp(2),a.dp(2),a.dp(2));cell.setLayoutParams(lp);cell.setOnClickListener(x->{sdDate=day;slotDay(a,pid,d);});drow.addView(cell);}
   var all=arr(v);var dayRows=new ArrayList<JSONObject>();for(int i=0;i<all.length();i++){var b=all.optJSONObject(i);if(b==null||b.optLong("parlour_id")!=pid)continue;String st=b.optString("status");if("CANCELLED".equals(st)||"REJECTED".equals(st))continue;try{if(sdDate.equals(java.time.ZonedDateTime.parse(b.optString("start_at")).withZoneSameInstant(a.zone).toLocalDate()))dayRows.add(b);}catch(Exception ignored){}}
   dayRows.sort(Comparator.comparing(b->b.optString("start_at")));
   a.tv(a.content,dayRows.size()+" booked appointment(s) on "+sdDate.format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.ENGLISH))+" — these times are unavailable to new customers.",12,false,MainActivity.MUTED);
   if(dayRows.isEmpty())a.label(a.content,"No bookings on this date — every slot is free.");
   for(var b:dayRows){var c=a.card();var hr=a.hrow(c);var tm=a.tv(hr,"🕐 "+a.fmtTime(b.optString("start_at"))+" – "+a.fmtTime(b.optString("end_at")),13,true,MainActivity.INK);tm.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));a.badge(hr,b.optString("status"));
    a.tv(c,b.optString("customer_name")+" • 📞 "+b.optString("customer_phone"),12,false,MainActivity.INK);
    a.tv(c,"👤 Stylist: "+a.staffName(d,b.optLong("staff_id"))+" • "+a.money(b.optDouble("total_amount")),12,false,MainActivity.MUTED);}});}
 static void refreshGrid(MainActivity a,LinearLayout box,JSONArray[] lastSlots,JSONArray[] lastBooked,JSONObject[] slotH){a.slotGridBooked(box,lastSlots[0],lastBooked[0],slotH[0],s->{slotH[0]=s;refreshGrid(a,box,lastSlots,lastBooked,slotH);});}
 static void bookForCustomer(MainActivity a,long pid,JSONObject d){a.uiRole="OWNER";a.content.removeAllViews();tabs(a,"Bookings");a.screenBar("Book for a Customer",()->bookings(a),null,null);
  var services=d.optJSONArray("services");var staffArr=d.optJSONArray("staff");
  a.tv(a.content,"For phone and walk-in customers. The booking is confirmed immediately, and that time becomes unavailable to everyone else.",12,false,MainActivity.MUTED);
  a.tv(a.content,"1. Choose services",14,true,MainActivity.INK);
  final var sel=new LinkedHashSet<Long>();final var svcPrice=new HashMap<Long,Double>();final TextView[] totalRef=new TextView[1];
  final Long[] staffH={null};final LocalDate[] dateH={LocalDate.now(a.zone).plusDays(1)};final JSONObject[] slotH={null};final JSONArray[] lastSlots={null},lastBooked={null};final Runnable[] refresh={null},drawStaff={null},drawDates={null},drawPay={null};
  final var slotsBox=new LinearLayout(a);slotsBox.setOrientation(LinearLayout.VERTICAL);
  refresh[0]=()->{slotH[0]=null;slotsBox.removeAllViews();if(sel.isEmpty()){a.tv(slotsBox,"Select at least one service to see open times.",13,false,MainActivity.MUTED);return;}
   a.tv(slotsBox,"Loading slots... this can take a minute.",13,false,MainActivity.MUTED);
   var csv=new StringBuilder();boolean first=true;for(long id2:sel){if(!first)csv.append(',');csv.append(id2);first=false;}
   a.fetchSlotsFull(pid,csv.toString(),staffH[0],dateH[0],(slots,bk)->{lastSlots[0]=slots;lastBooked[0]=bk;refreshGrid(a,slotsBox,lastSlots,lastBooked,slotH);});};
  if(services!=null)for(int i=0;i<services.length();i++){var s=services.optJSONObject(i);if(s==null)continue;long sid2=s.optLong("service_id");svcPrice.put(sid2,s.optDouble("price"));
   var row=new LinearLayout(a);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(a.dp(12),a.dp(10),a.dp(12),a.dp(10));row.setBackground(a.strokeBg("#FFFFFF",MainActivity.BORDER,10));var rlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);rlp.setMargins(0,a.dp(3),0,a.dp(3));row.setLayoutParams(rlp);a.content.addView(row);
   var nm=a.tv(row,s.optString("name"),13,true,MainActivity.INK);nm.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
   var pr=a.tv(row,a.money(s.optDouble("price"))+" • "+s.optInt("duration_min")+"m",12,false,MainActivity.MUTED);pr.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
   final long fsid=sid2;row.setOnClickListener(x->{if(sel.contains(fsid)){sel.remove(fsid);row.setBackground(a.strokeBg("#FFFFFF",MainActivity.BORDER,10));}else{sel.add(fsid);row.setBackground(a.strokeBg(MainActivity.CORAL_L,MainActivity.CORAL,10));}double tot=0;for(long id2:sel)tot+=svcPrice.getOrDefault(id2,0.0);if(totalRef[0]!=null)totalRef[0].setText("Total: "+a.money(tot));refresh[0].run();});}
  a.tv(a.content,"2. Choose stylist",14,true,MainActivity.INK);
  final var staffRow=a.hrow(a.content);
  drawStaff[0]=()->{staffRow.removeAllViews();a.pill(staffRow,"Any available",staffH[0]==null,x->{staffH[0]=null;drawStaff[0].run();refresh[0].run();});if(staffArr!=null)for(int i=0;i<staffArr.length();i++){var st2=staffArr.optJSONObject(i);if(st2==null)continue;final long fsid=st2.optLong("staff_id");a.pill(staffRow,st2.optString("name"),staffH[0]!=null&&staffH[0]==fsid,x->{staffH[0]=fsid;drawStaff[0].run();refresh[0].run();});}};
  drawStaff[0].run();
  a.tv(a.content,"3. Choose date & time",14,true,MainActivity.INK);
  var dscroll=new android.widget.HorizontalScrollView(a);dscroll.setHorizontalScrollBarEnabled(false);a.content.addView(dscroll);final var dateRow=new LinearLayout(a);dateRow.setOrientation(LinearLayout.HORIZONTAL);dscroll.addView(dateRow);
  drawDates[0]=()->{dateRow.removeAllViews();var today=LocalDate.now(a.zone);for(int i=0;i<7;i++){final LocalDate day=today.plusDays(i);boolean selD=day.equals(dateH[0]);
   var cell=new TextView(a);cell.setText(day.format(DateTimeFormatter.ofPattern("EEE d MMM",Locale.ENGLISH)));cell.setTextSize(11);if(selD)cell.setTypeface(Typeface.DEFAULT_BOLD);cell.setTextColor(Color.parseColor(selD?"#FFFFFF":MainActivity.INK));cell.setPadding(a.dp(10),a.dp(7),a.dp(10),a.dp(7));cell.setBackground(selD?a.roundBg(MainActivity.CORAL,8):a.strokeBg("#FFFFFF",MainActivity.BORDER,8));var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(a.dp(2),a.dp(2),a.dp(2),a.dp(2));cell.setLayoutParams(lp);cell.setOnClickListener(x->{dateH[0]=day;drawDates[0].run();refresh[0].run();});dateRow.addView(cell);}};
  drawDates[0].run();
  a.content.addView(slotsBox);
  a.tv(a.content,"4. Customer details",14,true,MainActivity.INK);
  var nameF=a.field(a.content,"Customer name *","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
  var phoneF=a.field(a.content,"Customer phone *","",InputType.TYPE_CLASS_PHONE);
  a.tv(a.content,"5. Payment",14,true,MainActivity.INK);
  final String[] payH={"AT_PARLOUR"};final var payRow=a.hrow(a.content);
  drawPay[0]=()->{payRow.removeAllViews();String[][] opts={{"AT_PARLOUR","Pay at parlour"},{"BKASH","bKash"},{"NAGAD","Nagad"},{"BANK","Bank"}};for(var o:opts){final String val=o[0];a.pill(payRow,o[1],payH[0].equals(val),x->{payH[0]=val;drawPay[0].run();});}};
  drawPay[0].run();
  totalRef[0]=a.tv(a.content,"Total: ৳0",14,true,MainActivity.CORAL);
  a.btn(a.content,"Create Booking",MainActivity.CORAL,x->{if(sel.isEmpty()){a.message("Choose at least one service.");return;}if(slotH[0]==null){a.message("Choose an open time slot.");return;}if(a.value(nameF).isBlank()||a.value(phoneF).isBlank()){a.message("Enter the customer's name and phone.");return;}
   try{var body=new JSONObject().put("parlour_id",pid).put("customer_name",a.value(nameF)).put("customer_phone",a.value(phoneF)).put("payment_method",payH[0]).put("start_at",slotH[0].optString("start_at"));var arr2=new JSONArray();for(long id2:sel)arr2.put(id2);body.put("services",arr2);if(staffH[0]!=null)body.put("staff_id",staffH[0]);
   a.post("/api/owner/bookings",body,v->{a.toast("Booking created for "+a.value(nameF)+" — confirmed.");bookings(a);});}catch(Exception e){a.message(e.getMessage());}});
  refresh[0].run();}
 // ---------- services tab ----------
 static void services(MainActivity a){a.uiRole="OWNER";a.content.removeAllViews();tabs(a,"Services");a.tv(a.content,"Loading services...",13,false,MainActivity.MUTED);
  withParlour(a,pid->a.get("/api/owner/parlours/"+pid+"/services",v->{a.content.removeAllViews();tabs(a,"Services");
   a.tv(a.content,"Service Catalog & Pricing",16,true,MainActivity.INK);var rows=arr(v);int active=0;for(int i=0;i<rows.length();i++){var s=rows.optJSONObject(i);if(s!=null&&"ACTIVE".equals(s.optString("status")))active++;}
   a.tv(a.content,active+" Services Active",12,false,MainActivity.MUTED);
   a.btn(a.content,"+ Add Service",MainActivity.CORAL,x->serviceForm(a,pid,null));
   for(int i=0;i<rows.length();i++){var s=rows.optJSONObject(i);if(s==null)continue;final JSONObject row=s;long sid=s.optLong("service_id");var c=a.card();
    var hr=a.hrow(c);var nm=a.tv(hr,s.optString("name"),14,true,MainActivity.INK);nm.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));a.badge(hr,s.optString("status"));
    a.tv(c,"⏱ "+s.optInt("duration_min")+" mins • Price: "+a.money(s.optDouble("price")),12,false,MainActivity.INK);
    var dd=a.tv(c,s.optString("description"),11,false,MainActivity.MUTED);dd.setMaxLines(1);dd.setEllipsize(android.text.TextUtils.TruncateAt.END);
    var ar=a.hrow(c);var sw=new Switch(a);sw.setText("Active");sw.setTextSize(12);sw.setChecked("ACTIVE".equals(s.optString("status")));ar.addView(sw);
    sw.setOnCheckedChangeListener((btn,on)->{try{var body=new JSONObject(row.toString());body.put("status",on?"ACTIVE":"INACTIVE");a.put("/api/owner/parlours/"+pid+"/services/"+sid,body,x->a.toast("Service "+(on?"activated":"deactivated")+"."));}catch(Exception e){a.message(e.getMessage());}});
    a.outlineW(ar,"Edit",MainActivity.MUTED,0.8f,x->serviceForm(a,pid,row));
    a.outlineW(ar,"Delete",MainActivity.RED,0.8f,x->a.confirm("Delete this service?",()->a.delete("/api/owner/parlours/"+pid+"/services/"+sid,y->services(a))));}
  }));}
 static void serviceForm(MainActivity a,long pid,JSONObject row){a.chrome("");a.hideTabs();a.content.removeAllViews();a.screenBar(row==null?"Add New Service":"Edit Service",()->services(a),null,null);
  var name=a.field(a.content,"Service Title (e.g. Keratin Hair Spa)",row==null?"":row.optString("name"),InputType.TYPE_CLASS_TEXT);
  var price=a.field(a.content,"Price (BDT)",row==null?"":String.valueOf(row.optDouble("price")),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
  var duration=a.field(a.content,"Duration (minutes)",row==null?"":String.valueOf(row.optInt("duration_min")),InputType.TYPE_CLASS_NUMBER);
  var desc=a.field(a.content,"Short Description",row==null?"":row.optString("description"),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
  a.btn(a.content,"Save Service",MainActivity.CORAL,v->{try{var body=new JSONObject().put("name",a.value(name)).put("price",Double.parseDouble(a.value(price))).put("duration_min",Integer.parseInt(a.value(duration))).put("description",a.value(desc)).put("status",row==null?"ACTIVE":row.optString("status","ACTIVE"));
   if(row==null)a.post("/api/owner/parlours/"+pid+"/services",body,x->services(a));else a.put("/api/owner/parlours/"+pid+"/services/"+row.optLong("service_id"),body,x->services(a));}catch(Exception e){a.toast("Check the price and duration numbers.");}});}
 // ---------- staff tab ----------
 static void staff(MainActivity a){a.uiRole="OWNER";a.content.removeAllViews();tabs(a,"Staff");a.tv(a.content,"Loading stylists...",13,false,MainActivity.MUTED);
  withParlour(a,pid->a.get("/api/owner/parlours/"+pid+"/staff",v->{a.content.removeAllViews();tabs(a,"Staff");
   a.tv(a.content,"Staff & Stylists Management",16,true,MainActivity.INK);var rows=arr(v);a.tv(a.content,rows.length()+" Stylists",12,false,MainActivity.MUTED);
   a.btn(a.content,"+ Add Stylist",MainActivity.CORAL,x->staffForm(a,pid,null));
   for(int i=0;i<rows.length();i++){var s=rows.optJSONObject(i);if(s==null)continue;final JSONObject row=s;long sid=s.optLong("staff_id");var c=a.card();var r=a.hrow(c);
    var av=new TextView(a);av.setText(s.optString("name","?").substring(0,1));av.setTextSize(18);av.setTypeface(Typeface.DEFAULT_BOLD);av.setTextColor(Color.parseColor(MainActivity.CORAL));av.setGravity(Gravity.CENTER);av.setBackground(a.roundBg(MainActivity.CORAL_L,24));av.setLayoutParams(new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));r.addView(av);
    var info=a.col(r,1);info.setPadding(a.dp(10),0,0,0);var nr=a.hrow(info);var nm=a.tv(nr,s.optString("name"),14,true,MainActivity.INK);nm.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
    boolean active="ACTIVE".equals(s.optString("status"));var bd=new TextView(a);bd.setText(active?"Active":"Off Duty");bd.setTextSize(10);bd.setTypeface(Typeface.DEFAULT_BOLD);bd.setTextColor(Color.parseColor(active?MainActivity.GREEN:MainActivity.RED));bd.setBackground(a.roundBg(active?MainActivity.GREEN_L:MainActivity.RED_L,4));bd.setPadding(a.dp(6),a.dp(2),a.dp(6),a.dp(2));nr.addView(bd);
    a.tv(info,s.optString("designation")+" • "+s.optInt("experience")+"y exp",12,false,MainActivity.MUTED);
    a.tv(info,a.stars(s.optDouble("rating"))+" "+String.format(Locale.US,"%.1f",s.optDouble("rating")),11,false,MainActivity.MUTED);
    if(!s.optString("specialization").isEmpty())a.tv(info,"Specialities: "+s.optString("specialization"),11,false,MainActivity.INK);
    var ar=a.hrow(c);a.outlineW(ar,"Edit",MainActivity.MUTED,1,x->staffForm(a,pid,row));a.outlineW(ar,"Delete",MainActivity.RED,1,x->a.confirm("Remove this stylist?",()->a.delete("/api/owner/parlours/"+pid+"/staff/"+sid,y->staff(a))));
    var tg=a.outlineW(ar,active?"Set off duty":"Set active",MainActivity.PLUM,1.2f,x->{try{var body=new JSONObject(row.toString());body.put("status",active?"INACTIVE":"ACTIVE");a.put("/api/owner/parlours/"+pid+"/staff/"+sid,body,y->staff(a));}catch(Exception e){a.message(e.getMessage());}});}
   a.outline(a.content,"Assign staff to services",MainActivity.PLUM,x->assign(a,pid));
  }));}
 static void staffForm(MainActivity a,long pid,JSONObject row){a.chrome("");a.hideTabs();a.content.removeAllViews();a.screenBar(row==null?"Add New Stylist":"Edit Stylist Profile",()->staff(a),null,null);
  var name=a.field(a.content,"Stylist Name",row==null?"":row.optString("name"),InputType.TYPE_CLASS_TEXT);
  var designation=a.field(a.content,"Designation (e.g. Master Colorist)",row==null?"Senior Stylist":row.optString("designation"),InputType.TYPE_CLASS_TEXT);
  var exp=a.field(a.content,"Experience (Years)",row==null?"5":String.valueOf(row.optInt("experience",5)),InputType.TYPE_CLASS_NUMBER);
  var spec=a.field(a.content,"Specializations (comma separated)",row==null?"":row.optString("specialization"),InputType.TYPE_CLASS_TEXT);
  a.btn(a.content,"Save Stylist",MainActivity.CORAL,v->{try{var body=new JSONObject().put("name",a.value(name)).put("designation",a.value(designation)).put("experience",Integer.parseInt(a.value(exp))).put("specialization",a.value(spec)).put("status",row==null?"ACTIVE":row.optString("status","ACTIVE"));
   if(row==null)a.post("/api/owner/parlours/"+pid+"/staff",body,x->staff(a));else a.put("/api/owner/parlours/"+pid+"/staff/"+row.optLong("staff_id"),body,x->staff(a));}catch(Exception e){a.toast("Check the experience number.");}});}
 static void assign(MainActivity a,long pid){a.chrome("");a.hideTabs();a.content.removeAllViews();a.screenBar("Assign staff to services",()->staff(a),null,null);
  a.get("/api/owner/parlours/"+pid+"/staff",v->{var staffRows=arr(v);if(staffRows.length()==0){a.label(a.content,"Add a staff member first.");return;}
   var names=new ArrayList<String>();for(int i=0;i<staffRows.length();i++)names.add(staffRows.optJSONObject(i).optString("name"));
   var spinner=a.spinner(a.content,names);var box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);a.content.addView(box);
   a.btn(a.content,"Load services for stylist",MainActivity.PLUM,x->{long sid=staffRows.optJSONObject(spinner.getSelectedItemPosition()).optLong("staff_id");
    a.get("/api/owner/parlours/"+pid+"/staff/"+sid+"/services",sv->{var assigned=new HashSet<Long>();var sa=arr(sv);for(int i=0;i<sa.length();i++)assigned.add(sa.optLong(i));
     a.get("/api/owner/parlours/"+pid+"/services",all->{box.removeAllViews();var services=arr(all);var checks=new ArrayList<CheckBox>();
      for(int i=0;i<services.length();i++){var s=services.optJSONObject(i);if(s==null)continue;var cb=new CheckBox(a);cb.setText(s.optString("name"));cb.setTag(s.optLong("service_id"));cb.setChecked(assigned.contains(s.optLong("service_id")));box.addView(cb);checks.add(cb);}
      a.btn(box,"Save assignments",MainActivity.CORAL,y->{var ids=new JSONArray();for(var cb:checks)if(cb.isChecked())ids.put((long)cb.getTag());a.put("/api/owner/parlours/"+pid+"/staff/"+sid+"/services",ids,z->a.toast("Assignments saved."));});});});});
  });}
 // ---------- schedules tab ----------
 static long pkOf(JSONObject row,String... preferred){for(String k:preferred)if(row.has(k))return row.optLong(k);var keys=row.keys();while(keys.hasNext()){String k=keys.next();if(k.endsWith("_id")&&!k.equals("parlour_id")&&!k.equals("staff_id"))return row.optLong(k);}return 0;}
 static void schedules(MainActivity a){a.uiRole="OWNER";a.content.removeAllViews();tabs(a,"Schedules");a.tv(a.content,"Loading schedules...",13,false,MainActivity.MUTED);
  withParlour(a,pid->{a.content.removeAllViews();tabs(a,"Schedules");
   a.tv(a.content,"Schedules, Blocked Slots & Holidays",16,true,MainActivity.INK);
   a.label(a.content,"Block specific time slots during salon rush hours or maintenance, and declare closed holidays.");
   // block slots
   var c1=a.card();a.tv(c1,"Block Salon Time Slots",14,true,MainActivity.CORAL);
   var dateF=a.field(c1,"Date (YYYY-MM-DD)",LocalDate.now(a.zone).plusDays(1).toString(),InputType.TYPE_CLASS_TEXT);
   var slotF=a.field(c1,"Slot (e.g. 02:00 PM)","02:00 PM",InputType.TYPE_CLASS_TEXT);
   a.btn(c1,"Block Slot",MainActivity.CORAL,x->{try{LocalTime t=null;for(String pat:new String[]{"h:mm a","H:mm"}){try{t=LocalTime.parse(a.value(slotF).toUpperCase(),DateTimeFormatter.ofPattern(pat,Locale.ENGLISH));break;}catch(Exception ignored){}}
     if(t==null){a.toast("Use a time like 02:00 PM.");return;}
     var day=LocalDate.parse(a.value(dateF));var start=ZonedDateTime.of(day,t,a.zone);var body=new JSONObject().put("start_at",start.toInstant().toString()).put("end_at",start.plusHours(1).toInstant().toString()).put("reason","Blocked from app");
     a.post("/api/owner/parlours/"+pid+"/blocked_slots",body,y->schedules(a));}catch(Exception e){a.toast("Check the date (YYYY-MM-DD).");}});
   a.tv(c1,"Currently Blocked Slots:",12,true,MainActivity.INK);
   a.get("/api/owner/parlours/"+pid+"/blocked_slots",v->{var rows=arr(v);if(rows.length()==0)a.tv(c1,"No slots blocked. All standard slots are open.",11,false,MainActivity.MUTED);
    for(int i=0;i<rows.length();i++){var r=rows.optJSONObject(i);if(r==null)continue;long id=pkOf(r,"blocked_slot_id");var row=a.hrow(c1);var t=a.tv(row,a.fmtDT(r.optString("start_at")),12,false,MainActivity.INK);t.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var ub=a.tv(row,"Unblock",11,true,MainActivity.BLUE);ub.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));ub.setOnClickListener(x->a.delete("/api/owner/parlours/"+pid+"/blocked_slots/"+id,y->schedules(a)));}});
   // holidays
   var c2=a.card();a.tv(c2,"Salon Holiday Dates",14,true,MainActivity.PLUM);
   a.label(c2,"Mark days when the parlour is completely closed for staff training, renovations, or festivals.");
   var hrowF=a.hrow(c2);var hc=a.col(hrowF,1);var holidayF=a.field(hc,"Holiday Date (YYYY-MM-DD)","",InputType.TYPE_CLASS_TEXT);a.btnW(hrowF,"Add Holiday",MainActivity.PLUM,0.8f,x->{try{a.post("/api/owner/parlours/"+pid+"/holidays",new JSONObject().put("holiday_date",a.value(holidayF)).put("reason","Closed"),y->schedules(a));}catch(Exception e){a.message(e.getMessage());}});
   a.tv(c2,"Marked Holidays:",12,true,MainActivity.INK);
   a.get("/api/owner/parlours/"+pid+"/holidays",v->{var rows=arr(v);if(rows.length()==0)a.tv(c2,"No holidays scheduled.",11,false,MainActivity.MUTED);
    for(int i=0;i<rows.length();i++){var r=rows.optJSONObject(i);if(r==null)continue;long id=pkOf(r,"holiday_id");var row=a.hrow(c2);var t=a.tv(row,"📅 "+r.optString("holiday_date")+" (Parlour Closed)",12,false,MainActivity.INK);t.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var rm=a.tv(row,"Remove",11,true,MainActivity.RED);rm.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));rm.setOnClickListener(x->a.delete("/api/owner/parlours/"+pid+"/holidays/"+id,y->schedules(a)));}});
   // working hours
   var c3=a.card();a.tv(c3,"Working Hours",14,true,MainActivity.CORAL);
   a.get("/api/owner/parlours/"+pid+"/business_hours",v->{var rows=arr(v);String[] days={"","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"};
    for(int day=1;day<=7;day++){JSONObject found=null;for(int i=0;i<rows.length();i++){var r=rows.optJSONObject(i);if(r!=null&&r.optInt("day_of_week")==day)found=r;}
     final JSONObject rowF=found;final int dayF2=day;var row=a.hrow(c3);var t=a.tv(row,days[day]+": "+(found==null?"Not set":found.optString("start_time")+" - "+found.optString("end_time")),12,false,MainActivity.INK);t.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var ed=a.tv(row,"Edit",11,true,MainActivity.CORAL);ed.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));ed.setOnClickListener(x->hoursForm(a,pid,dayF2,rowF));}});
   // staff schedules
   var c4=a.card();a.tv(c4,"Staff Schedules",14,true,MainActivity.PLUM);
   a.get("/api/owner/parlours/"+pid+"/staff",sv->{var staffRows=arr(sv);var staffNames=new HashMap<Long,String>();for(int i=0;i<staffRows.length();i++){var s=staffRows.optJSONObject(i);if(s!=null)staffNames.put(s.optLong("staff_id"),s.optString("name"));}
    a.get("/api/owner/parlours/"+pid+"/staff_schedules",v->{var rows=arr(v);String[] days={"","Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
     if(rows.length()==0)a.tv(c4,"No staff schedules yet. Add one so slots can be booked.",11,false,MainActivity.MUTED);
     for(int i=0;i<rows.length();i++){var r=rows.optJSONObject(i);if(r==null)continue;long id=pkOf(r,"schedule_id");var row=a.hrow(c4);var t=a.tv(row,staffNames.getOrDefault(r.optLong("staff_id"),"Staff")+" • "+days[Math.max(1,Math.min(7,r.optInt("day_of_week")))]+" "+r.optString("start_time")+"-"+r.optString("end_time")+" ("+r.optString("status")+")",12,false,MainActivity.INK);t.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var del=a.tv(row,"Delete",11,true,MainActivity.RED);del.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));del.setOnClickListener(x->a.delete("/api/owner/parlours/"+pid+"/staff_schedules/"+id,y->schedules(a)));}
     a.btn(c4,"+ Add Staff Schedule",MainActivity.PLUM,x->scheduleForm(a,pid,staffRows));});});
  });}
 static void hoursForm(MainActivity a,long pid,int day,JSONObject row){a.chrome("");a.hideTabs();a.content.removeAllViews();String[] days={"","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"};
  a.screenBar("Working hours - "+days[day],()->schedules(a),null,null);
  var start=a.field(a.content,"Start time (HH:MM)",row==null?"09:00":row.optString("start_time"),InputType.TYPE_CLASS_TEXT);
  var end=a.field(a.content,"End time (HH:MM)",row==null?"21:00":row.optString("end_time"),InputType.TYPE_CLASS_TEXT);
  a.btn(a.content,"Save hours",MainActivity.CORAL,v->{try{var body=new JSONObject().put("day_of_week",day).put("start_time",a.value(start)).put("end_time",a.value(end));
   if(row==null)a.post("/api/owner/parlours/"+pid+"/business_hours",body,x->schedules(a));else a.put("/api/owner/parlours/"+pid+"/business_hours/"+pkOf(row,"hour_id"),body,x->schedules(a));}catch(Exception e){a.message(e.getMessage());}});}
 static void scheduleForm(MainActivity a,long pid,JSONArray staffRows){a.chrome("");a.hideTabs();a.content.removeAllViews();a.screenBar("Add staff schedule",()->schedules(a),null,null);
  if(staffRows.length()==0){a.label(a.content,"Add a staff member first.");return;}
  var names=new ArrayList<String>();for(int i=0;i<staffRows.length();i++)names.add(staffRows.optJSONObject(i).optString("name"));
  var staffSp=a.spinner(a.content,names);
  var daySp=a.spinner(a.content,List.of("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"));
  var start=a.field(a.content,"Start time (HH:MM)","09:00",InputType.TYPE_CLASS_TEXT);
  var end=a.field(a.content,"End time (HH:MM)","21:00",InputType.TYPE_CLASS_TEXT);
  a.btn(a.content,"Save schedule",MainActivity.PLUM,v->{try{long sid=staffRows.optJSONObject(staffSp.getSelectedItemPosition()).optLong("staff_id");var body=new JSONObject().put("staff_id",sid).put("day_of_week",daySp.getSelectedItemPosition()+1).put("start_time",a.value(start)).put("end_time",a.value(end)).put("status","ACTIVE");
   a.post("/api/owner/parlours/"+pid+"/staff_schedules",body,x->schedules(a));}catch(Exception e){a.message(e.getMessage());}});}
 // ---------- policies tab ----------
 static void policies(MainActivity a){a.uiRole="OWNER";a.content.removeAllViews();tabs(a,"Policies");a.tv(a.content,"Loading policies...",13,false,MainActivity.MUTED);
  withParlour(a,pid->a.get("/api/owner/parlours/"+pid+"/cancellation_policies",v->{a.content.removeAllViews();tabs(a,"Policies");
   var rows=arr(v);JSONObject row=rows.length()==0?null:rows.optJSONObject(0);
   a.tv(a.content,"Booking, Cancellation & Payment Policies",16,true,MainActivity.INK);
   a.label(a.content,"Adjust business rules for online booking deposits, client cancellation deadlines, and refund calculations.");
   var c1=a.card();a.tv(c1,"Advance Deposit & Payment Rules",14,true,MainActivity.CORAL);
   final boolean[] adv={row!=null&&row.optBoolean("advance_required")};final int[] minAdv={row==null?30:Math.max(10,Math.min(100,row.optInt("min_advance_pct",30)))};
   var advRow=a.hrow(c1);var acol=a.col(advRow,1);a.tv(acol,"Require Advance Payment",13,true,MainActivity.INK);a.tv(acol,"Customers must pay advance deposit online to confirm.",11,false,MainActivity.MUTED);
   var advSw=new Switch(a);advSw.setChecked(adv[0]);advRow.addView(advSw);
   var advLabel=a.tv(c1,"Minimum Advance Deposit: "+minAdv[0]+"%",12,false,MainActivity.INK);
   var advSeek=new SeekBar(a);advSeek.setMax(90);advSeek.setProgress(minAdv[0]-10);c1.addView(advSeek);
   advSeek.setOnSeekBarChangeListener(simpleSeek(p->{minAdv[0]=p+10;advLabel.setText("Minimum Advance Deposit: "+minAdv[0]+"%");}));
   advSw.setOnCheckedChangeListener((b,on)->{adv[0]=on;advSeek.setVisibility(on?View.VISIBLE:View.GONE);advLabel.setVisibility(on?View.VISIBLE:View.GONE);});
   advSeek.setVisibility(adv[0]?View.VISIBLE:View.GONE);advLabel.setVisibility(adv[0]?View.VISIBLE:View.GONE);
   var c2=a.card();a.tv(c2,"Cancellation & Refund Rules",14,true,MainActivity.PLUM);
   final int[] notice={row==null?24:Math.max(2,Math.min(48,row.optInt("cancel_window_hr",24)))};final int[] fee={row==null?10:Math.max(0,Math.min(40,row.optInt("fee_pct",10)))};final int[] refund={row==null?90:Math.max(50,Math.min(100,row.optInt("refund_pct",90)))};
   var nLabel=a.tv(c2,"Cancellation Notice Window: "+notice[0]+" Hours Prior",12,false,MainActivity.INK);var nSeek=new SeekBar(a);nSeek.setMax(46);nSeek.setProgress(notice[0]-2);c2.addView(nSeek);nSeek.setOnSeekBarChangeListener(simpleSeek(p->{notice[0]=p+2;nLabel.setText("Cancellation Notice Window: "+notice[0]+" Hours Prior");}));
   var fLabel=a.tv(c2,"Cancellation Fee: "+fee[0]+"%",12,false,MainActivity.INK);var fSeek=new SeekBar(a);fSeek.setMax(40);fSeek.setProgress(fee[0]);c2.addView(fSeek);fSeek.setOnSeekBarChangeListener(simpleSeek(p->{fee[0]=p;fLabel.setText("Cancellation Fee: "+fee[0]+"%");}));
   var rLabel=a.tv(c2,"Refund Percentage: "+refund[0]+"% of Advance",12,false,MainActivity.INK);var rSeek=new SeekBar(a);rSeek.setMax(50);rSeek.setProgress(refund[0]-50);c2.addView(rSeek);rSeek.setOnSeekBarChangeListener(simpleSeek(p->{refund[0]=p+50;rLabel.setText("Refund Percentage: "+refund[0]+"% of Advance");}));
   final boolean[] resched={row==null||"ALLOWED".equals(row.optString("reschedule_rule","ALLOWED"))};final boolean[] ownerAppr={row!=null&&row.optBoolean("owner_approval")};final boolean[] manualAppr={row!=null&&row.optBoolean("manual_approval")};
   var r1=a.hrow(c2);var rc=a.col(r1,1);a.tv(rc,"Allow Rescheduling",13,true,MainActivity.INK);var rSw=new Switch(a);rSw.setChecked(resched[0]);r1.addView(rSw);rSw.setOnCheckedChangeListener((b,on)->resched[0]=on);
   var r2=a.hrow(c2);var oc=a.col(r2,1);a.tv(oc,"Owner Approval Required",13,true,MainActivity.INK);a.tv(oc,"New bookings wait for your confirmation.",11,false,MainActivity.MUTED);var oSw=new Switch(a);oSw.setChecked(ownerAppr[0]);r2.addView(oSw);oSw.setOnCheckedChangeListener((b,on)->ownerAppr[0]=on);
   var r3=a.hrow(c2);var mc=a.col(r3,1);a.tv(mc,"Manual Cancellation Approval",13,true,MainActivity.INK);a.tv(mc,"Cancellations wait for your approval.",11,false,MainActivity.MUTED);var mSw=new Switch(a);mSw.setChecked(manualAppr[0]);r3.addView(mSw);mSw.setOnCheckedChangeListener((b,on)->manualAppr[0]=on);
   a.btn(a.content,"Save & Apply Salon Policy",MainActivity.CORAL,x->{try{var body=new JSONObject().put("advance_required",adv[0]).put("min_advance_pct",minAdv[0]).put("cancel_window_hr",notice[0]).put("fee_pct",fee[0]).put("refund_pct",refund[0]).put("reschedule_rule",resched[0]?"ALLOWED":"DISABLED").put("manual_approval",manualAppr[0]).put("owner_approval",ownerAppr[0]);
    if(row==null)a.post("/api/owner/parlours/"+pid+"/cancellation_policies",body,y->{a.toast("Policy saved.");policies(a);});
    else a.put("/api/owner/parlours/"+pid+"/cancellation_policies/"+row.optLong("policy_id"),body,y->{a.toast("Policy saved.");policies(a);});}catch(Exception e){a.message(e.getMessage());}});
  }));}
 static SeekBar.OnSeekBarChangeListener simpleSeek(java.util.function.IntConsumer on){return new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){if(u)on.accept(p);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};}
 // ---------- alerts tab ----------
 static String aFilter="ALL";
 static void alerts(MainActivity a){a.uiRole="OWNER";a.content.removeAllViews();tabs(a,"Alerts");
  var hr=a.hrow(a.content);var colT=a.col(hr,1);a.tv(colT,"Salon Owner Alerts",16,true,MainActivity.INK);var sub=a.tv(colT,"Loading...",11,false,MainActivity.MUTED);
  var mark=new TextView(a);mark.setText("Mark all read");mark.setTextSize(12);mark.setTypeface(Typeface.DEFAULT_BOLD);mark.setTextColor(Color.parseColor(MainActivity.CORAL));hr.addView(mark);
  var box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);a.content.addView(box);
  a.get("/api/notifications",v->{var all=arr(v);box.removeAllViews();int unread=0;for(int i=0;i<all.length();i++){var n=all.optJSONObject(i);if(n!=null&&!a.readIds.contains(n.optLong("notification_id")))unread++;}
   sub.setText(unread>0?unread+" unread notifications":"All caught up");sub.setTextColor(Color.parseColor(unread>0?MainActivity.CORAL:MainActivity.MUTED));a.setBell(unread);
   mark.setOnClickListener(x->{for(int i=0;i<all.length();i++){var n=all.optJSONObject(i);if(n!=null)a.readIds.add(n.optLong("notification_id"));}a.persistUi();alerts(a);});
   var chips=a.hrow(box);String[][] fs={{"ALL","All Alerts ("+all.length()+")"},{"BOOKING","Bookings"},{"PAYMENT","Payments"},{"SYSTEM","System & Updates"}};for(String[] f:fs)a.chip(chips,f[1],aFilter.equals(f[0]),MainActivity.CORAL,x->{aFilter=f[0];alerts(a);});
   int shown=0;
   for(int i=0;i<all.length();i++){var n=all.optJSONObject(i);if(n==null)continue;String type=n.optString("type");String bucket=type.contains("BOOKING")?"BOOKING":type.contains("PAYMENT")?"PAYMENT":"SYSTEM";
    if(!"ALL".equals(aFilter)&&!aFilter.equals(bucket))continue;shown++;long nid=n.optLong("notification_id");boolean unr=!a.readIds.contains(nid);
    var c=a.cardIn(box);c.setBackground(unr?a.strokeBg(MainActivity.CORAL_L,MainActivity.BORDER,10):a.strokeBg("#FFFFFF",MainActivity.BORDER,10));var r=a.hrow(c);
    String glyph=type.contains("BOOKING")?"📅":type.contains("PAYMENT")?"💳":type.contains("REMINDER")?"⏰":"🏬";
    var circ=new TextView(a);circ.setText(glyph);circ.setTextSize(17);circ.setGravity(Gravity.CENTER);circ.setBackground(a.roundBg(unr?MainActivity.CORAL:MainActivity.BEIGE,19));circ.setLayoutParams(new LinearLayout.LayoutParams(a.dp(38),a.dp(38)));r.addView(circ);
    var info=a.col(r,1);info.setPadding(a.dp(10),0,0,0);a.tv(info,n.optString("title","Update"),14,unr,MainActivity.INK);a.tv(info,n.optString("message"),12,false,MainActivity.INK);a.tv(info,n.optString("created_at"),10,false,MainActivity.MUTED);
    if(type.contains("BOOKING")){var mg=a.tv(info,"Manage Bookings →",11,true,MainActivity.CORAL);mg.setOnClickListener(x->{a.readIds.add(nid);a.persistUi();bookings(a);});}
    final long nId=nid;c.setOnClickListener(x->{a.readIds.add(nId);a.persistUi();alerts(a);});}
   if(shown==0)a.emptyState(box,"🔔","No alerts found","You will receive real-time notifications here about new customer bookings, advance payments, and staff schedules.",null,null);
  });}
}
