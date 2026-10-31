package com.parlour.management;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.activity.result.*;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.textfield.*;
import com.google.firebase.messaging.FirebaseMessaging;
import com.parlour.management.databinding.ActivityMainBinding;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;
import org.json.*;

public class MainActivity extends AppCompatActivity implements ApiRepository.AuthListener {
 // LuxeParlour palette (AI Studio reference)
 static final String IVORY="#FDFBF7",CORAL="#D64A5B",CORAL_D="#B53444",CORAL_L="#FDE8EB",PLUM="#6C3483",PLUM_L="#F4ECF7",BEIGE="#F7F1E5",BORDER="#EBE7DF",INK="#1E2022",MUTED="#555B62",GREEN="#2E7D32",GREEN_L="#E8F5E9",GREEN_B="#00C853",AMBER="#F57F17",AMBER_L="#FFF8E1",BLUE="#1976D2",BLUE_L="#E3F2FD",RED="#D32F2F",RED_L="#FFEBEE",GOLD="#FFB300",LAV="#E8EAF6",NAVY="#1E3A8A",GLASS_BG="#251F35",GLASS_SUB="#D8D2E8",GLASS_MUT="#A79FC0",ROSE="#FF8598",MINT="#69F0AE",SLOTY="#FFD54F";
 static final String[] CATS={"ALL","WOMEN","MEN","UNISEX","SPA","HAIR","SKIN","NAILS"};
 static final String[] CAT_LABEL={"All","Women","Men","Unisex","Spa","Hair","Skin","Nails"};
 static final String[][] AREAS={{"Gulshan-1, Dhaka","23.7806","90.4197"},{"Banani, Dhaka","23.7937","90.4043"},{"Dhanmondi, Dhaka","23.7465","90.3760"},{"Uttara, Dhaka","23.8759","90.3795"},{"Bashundhara R/A, Dhaka","23.8191","90.4523"},{"Mirpur-10, Dhaka","23.8069","90.3687"},{"Mohakhali DOHS, Dhaka","23.7837","90.3965"},{"Lalmatia, Dhaka","23.7560","90.3720"}};
 AppViewModel vm;ActivityMainBinding ui;LinearLayout content;Handler handler=new Handler(Looper.getMainLooper());
 String uiRole="CUSTOMER";boolean wantLogin=false;
 ZoneId zone=ZoneId.of("Asia/Dhaka");
 // location state
 String areaName="Gulshan-1, Dhaka";double lat=23.7806,lng=90.4197;boolean gpsSet=false;
 // home state
 String homeQuery="",homeCategory="ALL";int homeRadius=10;LinearLayout homeResults;TextView homeCount;
 // explore state
 String exQuery="",exCategory="ALL",exSort="distance";int exRadius=10;double exMinRating=0;boolean exOpenNow=false,exAvailToday=false;LinearLayout exResults;TextView exCount;
 // detail + wizard state
 JSONObject selected;final Map<Long,JSONObject> detailCache=new HashMap<>();final Map<Long,String[]> parlourInfo=new HashMap<>();
 long wizPid;int wizStep=1;JSONObject wizDetail;final Set<Long> wizServices=new LinkedHashSet<>();Long wizStaff=null;LocalDate wizDate=LocalDate.now(ZoneId.of("Asia/Dhaka")).plusDays(1);JSONObject wizSlot;String wizMethod="AT_PARLOUR";String wizKey;LinearLayout wizBody,wizSlotsBox;TextView wizTotalLabel,wizCountLabel;Button wizAction;
 // compare + favourites + read receipts (local only)
 final LinkedHashSet<Long> compareIds=new LinkedHashSet<>();final Set<Long> favIds=new HashSet<>();final Set<Long> readIds=new HashSet<>();
 JSONArray lastBookings;String bookingFilter="ALL";
 long ownerPid;String ownerTab="Dashboard",adminTab="Dashboard";
 Runnable slotRefresh;ActivityResultLauncher<String[]> photoPicker;long photoParlourId;
 final String[] SPLASH_MSGS={"Loading verified salons in Dhaka...","Syncing real-time stylist schedules...","Configuring instant advance booking...","Welcome to Luxe Parlour!"};
 int splashIdx=0;boolean splashDone=false;Runnable splashTick;

 @Override public void onCreate(Bundle state){super.onCreate(state);if(state!=null){ownerPid=state.getLong("ownerPid");photoParlourId=state.getLong("photoParlourId");}
  vm=new ViewModelProvider(this).get(AppViewModel.class);ui=ActivityMainBinding.inflate(getLayoutInflater());setContentView(ui.getRoot());content=ui.content;
  vm.repository.setAuthListener(this);
  try{var p=getSharedPreferences("ui",MODE_PRIVATE);for(String s:p.getStringSet("fav",new HashSet<>()))favIds.add(Long.parseLong(s));for(String s:p.getStringSet("read",new HashSet<>()))readIds.add(Long.parseLong(s));}catch(Exception ignored){}
  ui.logoCircle.setBackground(roundBg(CORAL,18));ui.bellBadge.setBackground(roundBg(CORAL,9));ui.splashLogo.setBackground(roundBg("#FFFFFF",55));ui.splashMsg.setBackground(strokeBg("#FFFFFF",BORDER,18));
  styleSplashButton();wireChrome();
  photoPicker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{if(uri!=null&&photoParlourId>0)vm.repository.uploadImage(this,"/api/owner/parlours/"+photoParlourId+"/images",uri,(v,e)->runOnUiThread(()->{if(e!=null)message(e);else{toast("Photo uploaded");OwnerScreens.dashboard(this);}}));});
  vm.loading.observe(this,v->ui.loading.setVisibility(Boolean.TRUE.equals(v)?View.VISIBLE:View.GONE));
  vm.results.observe(this,r->{if(r==null||r.consumed)return;var cb=vm.pendingCallbacks.remove(r.key);if(cb==null)return;r.consumed=true;if(r.error!=null)cb.accept(new CallError(r.error));else cb.accept(r.value);});
  showSplash();
 }
 @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putLong("ownerPid",ownerPid);out.putLong("photoParlourId",photoParlourId);}
 @Override protected void onDestroy(){if(slotRefresh!=null)handler.removeCallbacks(slotRefresh);if(splashTick!=null)handler.removeCallbacks(splashTick);super.onDestroy();}
 public static class CallError{public final String message;CallError(String m){message=m;}}
 void call(String method,String path,Object body,String idem,Consumer<Object> done){String key=method+" "+path+"#"+System.nanoTime();vm.pendingCallbacks.put(key,raw->{if(raw instanceof CallError e){vm.loading.setValue(false);message(e.message);}else done.accept(raw);});vm.call(key,method,path,body,idem);}
 void get(String path,Consumer<Object> done){call("GET",path,null,null,done);}
 void post(String path,Object body,Consumer<Object> done){call("POST",path,body,null,done);}
 void post(String path,Object body,String idem,Consumer<Object> done){call("POST",path,body,idem,done);}
 void put(String path,Object body,Consumer<Object> done){call("PUT",path,body,null,done);}
 void delete(String path,Consumer<Object> done){call("DELETE",path,null,null,done);}
 static JSONObject obj(Object v){return v instanceof JSONObject o?o:new JSONObject();}
 static JSONArray arr(Object v){return v instanceof JSONArray a?a:new JSONArray();}
 static String enc(String s){try{return java.net.URLEncoder.encode(s==null?"":s,"UTF-8");}catch(Exception e){return "";}}
 // ---------- splash ----------
 void styleSplashButton(){var b=ui.splashExplore;b.setBackground(roundBg(CORAL,8));b.setTextColor(Color.WHITE);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT_BOLD);}
 void showSplash(){ui.splashView.setVisibility(View.VISIBLE);splashIdx=0;ui.splashMsg.setText(SPLASH_MSGS[0]);
  splashTick=()->{if(splashDone)return;splashIdx++;if(splashIdx<SPLASH_MSGS.length){ui.splashMsg.setText(SPLASH_MSGS[splashIdx]);handler.postDelayed(splashTick,1400);}else dismissSplash();};
  handler.postDelayed(splashTick,1400);
  ui.splashExplore.setOnClickListener(v->dismissSplash());
  ui.splashSignin.setOnClickListener(v->{wantLogin=true;dismissSplash();});
 }
 void dismissSplash(){if(splashDone)return;splashDone=true;if(splashTick!=null)handler.removeCallbacks(splashTick);ui.splashView.setVisibility(View.GONE);ensureSession();}
 void ensureSession(){vm.repository.healFromPointer(()->runOnUiThread(()->{
  if(vm.repository.base().isEmpty()){connection();return;}
  if(!vm.repository.token().isEmpty()){routeStart();return;}
  post("/api/auth/guest",new JSONObject(),v->{try{vm.repository.session(obj(v));}catch(Exception ignored){}routeStart();});
 }));}
 void routeStart(){if(wantLogin){wantLogin=false;unifiedLogin();return;}
  String r=vm.repository.role();
  if("OWNER".equals(r)){uiRole="OWNER";OwnerScreens.dashboard(this);}
  else if("ADMIN".equals(r)){uiRole="ADMIN";AdminScreens.dashboard(this);}
  else{uiRole="CUSTOMER";home();}
 }
 @Override public void onSessionExpired(){runOnUiThread(()->{new AlertDialog.Builder(this).setTitle("Session expired").setMessage("Session expired, please sign in again.").setPositiveButton("OK",(d,w)->{uiRole="CUSTOMER";unifiedLogin();}).show();});}
 // ---------- chrome ----------
 void wireChrome(){
  ui.navHome.setOnClickListener(v->home());ui.navExplore.setOnClickListener(v->explore());ui.navBookings.setOnClickListener(v->myBookings());ui.navCompare.setOnClickListener(v->compare());
  ui.locationChange.setOnClickListener(v->areaDialog());
  ui.bell.setOnClickListener(v->{if("OWNER".equals(vm.repository.role()))OwnerScreens.alerts(this);else inbox();});
  ui.logoutBtn.setOnClickListener(v->doLogout());
  ui.logoCircle.setOnClickListener(v->openDrawer());
 }
 void doLogout(){vm.repository.logout();FirebaseSession.signOut();getSharedPreferences("ui",0).edit().putBoolean("custAuth",false).remove("custEmail").remove("actEmail").apply();toast("You have been signed out.");if("OWNER".equals(uiRole)||"ADMIN".equals(uiRole)){uiRole="CUSTOMER";unifiedLogin();}else{uiRole="CUSTOMER";ensureSession();}}
 // ---------- sidebar drawer ----------
 FrameLayout drawerRoot;
 void closeDrawer(){if(drawerRoot!=null){var p=(ViewGroup)drawerRoot.getParent();if(p!=null)p.removeView(drawerRoot);drawerRoot=null;}}
 void drawerItem(LinearLayout parent,String label,Runnable go){var t=tv(parent,label,15,false,INK);t.setPadding(dp(8),dp(13),dp(8),dp(13));t.setOnClickListener(v->{closeDrawer();go.run();});}
 void openDrawer(){closeDrawer();var root=(FrameLayout)ui.splashView.getParent();
  drawerRoot=new FrameLayout(this);drawerRoot.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
  var scrim=new View(this);scrim.setBackgroundColor(0x73000000);scrim.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));scrim.setOnClickListener(v->closeDrawer());drawerRoot.addView(scrim);
  var scroll=new ScrollView(this);scroll.setBackgroundColor(Color.WHITE);scroll.setLayoutParams(new FrameLayout.LayoutParams(dp(294),ViewGroup.LayoutParams.MATCH_PARENT));drawerRoot.addView(scroll);
  var panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(22),dp(18),dp(18));scroll.addView(panel);
  var li=new ImageView(this);li.setImageResource(R.drawable.ic_logo_light);li.setPadding(dp(8),dp(8),dp(8),dp(8));li.setBackground(roundBg(CORAL,20));li.setLayoutParams(new LinearLayout.LayoutParams(dp(56),dp(56)));panel.addView(li);
  var wn=tv(panel,"LuxeParlour",18,true,INK);wn.setPadding(0,dp(8),0,0);
  String role=vm.repository.role();boolean ca=getSharedPreferences("ui",MODE_PRIVATE).getBoolean("custAuth",false);
  boolean staff="OWNER".equals(role)||"ADMIN".equals(role);boolean signedIn=staff||("CUSTOMER".equals(role)&&ca);
  String em=getSharedPreferences("ui",MODE_PRIVATE).getString("actEmail","");if(em.isEmpty())em=getSharedPreferences("ui",MODE_PRIVATE).getString("custEmail","");
  tv(panel,signedIn?(em.isEmpty()?"Signed in":em):"Browsing as guest",12,false,MUTED);
  if(signedIn)tv(panel,staff?("OWNER".equals(role)?"Parlour Owner account":"Platform Admin account"):"Customer account",11,true,PLUM);
  divider(panel);
  drawerItem(panel,"👤  My Profile",()->profileScreen());
  if("CUSTOMER".equals(uiRole)){drawerItem(panel,"🏠  Home",()->home());drawerItem(panel,"🔍  Explore Parlours",()->explore());drawerItem(panel,"📅  My Bookings",()->myBookings());drawerItem(panel,"🧾  Past Bookings",()->pastBookings());drawerItem(panel,"⇄  Compare Parlours",()->compare());drawerItem(panel,"🔔  Notifications",()->inbox());}
  if("OWNER".equals(uiRole)){drawerItem(panel,"📊  Owner Dashboard",()->OwnerScreens.dashboard(this));drawerItem(panel,"🔔  Alerts",()->OwnerScreens.alerts(this));}
  if("ADMIN".equals(uiRole)){drawerItem(panel,"🛡  Admin Console",()->AdminScreens.dashboard(this));}
  drawerItem(panel,"🔒  Security",()->securityScreen());
  drawerItem(panel,"⚙️  Settings & Connection",()->connection());
  drawerItem(panel,"❓  Help & Support",()->helpScreen());
  drawerItem(panel,"ℹ️  About LuxeParlour",()->aboutScreen());
  divider(panel);
  if(signedIn)drawerItem(panel,"🚪  Log Out",()->doLogout());else drawerItem(panel,"🔑  Sign In",()->unifiedLogin());
  root.addView(drawerRoot);}
 void profileScreen(){chrome("");hideTabs();content.removeAllViews();screenBar("My Profile",()->accountScreen(),null,null);
  String role=vm.repository.role();boolean ca=getSharedPreferences("ui",MODE_PRIVATE).getBoolean("custAuth",false);boolean staff="OWNER".equals(role)||"ADMIN".equals(role);
  String em=getSharedPreferences("ui",MODE_PRIVATE).getString("actEmail","");if(em.isEmpty())em=getSharedPreferences("ui",MODE_PRIVATE).getString("custEmail","");
  var c=card();head(c,"👤  Profile");
  if(!staff&&!ca){tv(c,"You are browsing as a guest. Sign in to save your bookings, reviews and offers in one place.",13,false,MUTED);btn(c,"Sign In",CORAL,v->unifiedLogin());outline(c,"Create a free account",CORAL,v->customerLogin(true));return;}
  kv(c,"Account type",staff?("OWNER".equals(role)?"Parlour Owner":"Platform Admin"):"Customer");
  if(!em.isEmpty())kv(c,"Email",em);
  kv(c,"Status","Signed in");
  tv(c,"Your bookings, past visits, reviews and offers are saved under this account.",12,false,MUTED);
  if("CUSTOMER".equals(uiRole)){btn(c,"My bookings",CORAL,v->myBookings());outline(c,"Past bookings (archive)",PLUM,v->pastBookings());}
  if("OWNER".equals(uiRole))btn(c,"Open Owner Dashboard",CORAL,v->OwnerScreens.dashboard(this));
  if("ADMIN".equals(uiRole))btn(c,"Open Admin Console",CORAL,v->AdminScreens.dashboard(this));
  outline(c,"Security settings",PLUM,v->securityScreen());}
 void securityScreen(){chrome("");hideTabs();content.removeAllViews();screenBar("Security",()->profileScreen(),null,null);
  String role=vm.repository.role();String em=getSharedPreferences("ui",MODE_PRIVATE).getString("actEmail","");if(em.isEmpty())em=getSharedPreferences("ui",MODE_PRIVATE).getString("custEmail","");
  var c=card();head(c,"🔒  Security");
  kv(c,"Signed in as",em.isEmpty()?"Guest session":em);
  kv(c,"Account role",(role==null||role.isEmpty())?"CUSTOMER":role);
  tv(c,"Passwords are stored encrypted (BCrypt) on the server and are never shown in the app. Tap below to get a secure password-reset link for your account email.",12,false,MUTED);
  btn(c,"Change / reset password",CORAL,v->forgotPassword());
  tv(c,"To log out, tap the LuxeParlour logo at the top and choose Log Out in the sidebar.",12,false,MUTED);}
 void helpScreen(){chrome("");hideTabs();content.removeAllViews();screenBar("Help & Support",()->home(),null,null);
  var c=card();head(c,"❓  How booking works");
  tv(c,"1. Pick your location — type any area or use your current GPS location.\n2. Choose a parlour and service, then a stylist.\n3. Pick a date and an open time slot (times marked Booked are already taken).\n4. Pay by bKash, Nagad or bank using the parlour's number shown at checkout, or pay at the parlour.",13,false,INK);
  var c2=card();head(c2,"Payments & refunds");
  tv(c2,"The platform charge set by the admin is deducted automatically; the rest goes to the parlour. Cancellations follow each parlour's own policy, shown before you confirm.",13,false,INK);
  var c3=card();head(c3,"Need help with a booking?");
  tv(c3,"Open the booking from My Bookings to reschedule, cancel, or send a complaint. Parlour owners can also help you from their portal.",13,false,INK);
  btn(c3,"My bookings",CORAL,v->myBookings());}
 void aboutScreen(){chrome("");hideTabs();content.removeAllViews();screenBar("About",()->home(),null,null);
  var c=card();head(c,"🌸  LuxeParlour");
  tv(c,"Premium Salon & Spa Appointments — book verified parlours and salons near you, anywhere in Bangladesh.",13,false,INK);
  kv(c,"App","LuxeParlour for Android");
  kv(c,"Accounts","One sign-in for customers, owners and admins");
  tv(c,"Made for parlour customers, owners and the platform team.",12,false,MUTED);}
 void chrome(String navActive){String sr=vm.repository.role();
  ui.tagline.setText("OWNER".equals(uiRole)?"Owner Portal":"ADMIN".equals(uiRole)?"Admin Console":"Customer Mobile App");
  ui.locationStrip.setVisibility("CUSTOMER".equals(uiRole)?View.VISIBLE:View.GONE);
  ui.locationText.setText("📍 Deliver services near: "+areaName);
  ui.bottomNav.setVisibility("CUSTOMER".equals(uiRole)?View.VISIBLE:View.GONE);
  boolean staff="OWNER".equals(sr)||"ADMIN".equals(sr);
  ui.logoutBtn.setVisibility(View.GONE);
  ui.bellWrap.setVisibility(staff||"CUSTOMER".equals(sr)?View.VISIBLE:View.GONE);
  if(!"OWNER".equals(uiRole)&&!"ADMIN".equals(uiRole))ui.tabScroll.setVisibility(View.GONE);
  styleNav(ui.navHome,"home".equals(navActive));styleNav(ui.navExplore,"explore".equals(navActive));styleNav(ui.navBookings,"bookings".equals(navActive));styleNav(ui.navCompare,"compare".equals(navActive));
  ui.navCompare.setText("⇄\nCompare"+(compareIds.isEmpty()?"":" ("+compareIds.size()+")"));
 }
 void styleRoleTab(TextView t,boolean sel){if(sel){t.setBackground(roundBg(CORAL,6));t.setTextColor(Color.WHITE);}else{t.setBackgroundColor(Color.TRANSPARENT);t.setTextColor(Color.parseColor(INK));}}
 void styleNav(TextView t,boolean sel){if(sel){t.setBackground(roundBg(CORAL_L,8));t.setTextColor(Color.parseColor(CORAL));}else{t.setBackgroundColor(Color.TRANSPARENT);t.setTextColor(Color.parseColor(INK));}}
 void showTabs(String[] labels,String active,Consumer<String> go){ui.tabScroll.setVisibility(View.VISIBLE);ui.tabRow.removeAllViews();
  for(String l:labels){var t=new TextView(this);t.setText(l);t.setTextSize(12);t.setPadding(dp(12),dp(8),dp(12),dp(8));var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(dp(2),0,dp(2),0);t.setLayoutParams(lp);
   if(l.equals(active)){t.setBackground(roundBg(CORAL_L,8));t.setTextColor(Color.parseColor(CORAL));t.setTypeface(Typeface.DEFAULT_BOLD);}else t.setTextColor(Color.parseColor(INK));
   t.setOnClickListener(v->go.accept(l));ui.tabRow.addView(t);}}
 void hideTabs(){ui.tabScroll.setVisibility(View.GONE);}
 void setBell(int unread){if(unread>0){ui.bellBadge.setVisibility(View.VISIBLE);ui.bellBadge.setText(String.valueOf(unread));}else ui.bellBadge.setVisibility(View.GONE);}
 void refreshBell(){if(!"OWNER".equals(vm.repository.role())&&!"CUSTOMER".equals(vm.repository.role())){setBell(0);return;}get("/api/notifications",v->{int n=0;var a=arr(v);for(int i=0;i<a.length();i++)if(!readIds.contains(a.optJSONObject(i)==null?0L:a.optJSONObject(i).optLong("notification_id")))n++;setBell(n);});}
 void persistUi(){var set=new HashSet<String>();for(long id:favIds)set.add(String.valueOf(id));var set2=new HashSet<String>();for(long id:readIds)set2.add(String.valueOf(id));getSharedPreferences("ui",MODE_PRIVATE).edit().putStringSet("fav",set).putStringSet("read",set2).apply();}
 void areaDialog(){var box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(8),dp(4),dp(8),dp(4));
  glassText(box,"Type any area or address — you are not limited to a fixed list:",13,false,GLASS_SUB);
  var q=glassField(box,"e.g. Shaheb Bazar, Rajshahi","");q.setSingleLine(true);
  var search=glassText(box,"🔎  Search this location",14,true,ROSE);search.setPadding(dp(10),dp(12),dp(10),dp(12));
  search.setOnClickListener(v->{String text=value(q).trim();if(text.isEmpty()){toast("Type an area or address first.");return;}
   new Thread(()->{Double la=null,ln=null;try{var rs=new Geocoder(this).getFromLocationName(text+", Bangladesh",1);if(rs!=null&&!rs.isEmpty()){la=rs.get(0).getLatitude();ln=rs.get(0).getLongitude();}}catch(Exception ignored){}
    final Double fla=la,fln=ln;
    runOnUiThread(()->{if(fla==null){toast("Could not find that place. Check the spelling, or use GPS below.");return;}areaName=text;lat=fla;lng=fln;gpsSet=false;ui.locationText.setText("📍 Deliver services near: "+areaName);toast("Location updated to "+areaName);dismissGlass();if(homeResults!=null&&"CUSTOMER".equals(uiRole))loadHomeResults();if(exResults!=null)loadExploreResults();});}).start();});
  var or=glassText(box,"— or —",12,false,GLASS_SUB);or.setGravity(Gravity.CENTER);
  var gps=glassText(box,"🛰  Use my current location (GPS)",14,true,ROSE);gps.setPadding(dp(10),dp(12),dp(10),dp(12));gps.setOnClickListener(v->{dismissGlass();location();});
  glass("Set Your Location",box,null,null,null);}
 // ---------- view helpers ----------
 int dp(double v){return (int)(v*getResources().getDisplayMetrics().density+.5);}
 GradientDrawable roundBg(String hex,int radiusDp){var g=new GradientDrawable();g.setColor(Color.parseColor(hex));g.setCornerRadius(dp(radiusDp));return g;}
 GradientDrawable strokeBg(String fill,String stroke,int radiusDp){var g=new GradientDrawable();g.setColor(Color.parseColor(fill));g.setCornerRadius(dp(radiusDp));g.setStroke(dp(1),Color.parseColor(stroke));return g;}
 TextView tv(String s){return tv(content,s,15,false,INK);}
 TextView tv(LinearLayout parent,String s,int size,boolean bold,String color){var t=new TextView(this);t.setText(s==null?"":s);t.setTextSize(size);t.setTextColor(Color.parseColor(color));if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);t.setLineSpacing(0,1.1f);var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,dp(1),0,dp(1));t.setLayoutParams(lp);parent.addView(t);return t;}
 TextView label(LinearLayout parent,String s){return tv(parent,s,13,false,MUTED);}
 TextView head(LinearLayout parent,String s){return tv(parent,s,16,true,INK);}
 LinearLayout card(){var c=cardIn(content);return c;}
 LinearLayout cardIn(LinearLayout parent){var c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setBackground(strokeBg("#FFFFFF",BORDER,14));c.setPadding(dp(12),dp(8),dp(12),dp(8));var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,dp(4),0,dp(4));c.setLayoutParams(lp);parent.addView(c);return c;}
 LinearLayout hrow(LinearLayout parent){var r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,dp(2),0,dp(2));r.setLayoutParams(lp);parent.addView(r);return r;}
 LinearLayout col(LinearLayout parent,float weight){var c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);var lp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,weight);c.setLayoutParams(lp);parent.addView(c);return c;}
 void spacer(LinearLayout parent,int h){var v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(h)));parent.addView(v);}
 void divider(LinearLayout parent){var v=new View(this);v.setBackgroundColor(Color.parseColor(BORDER));var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(1));lp.setMargins(0,dp(6),0,dp(6));v.setLayoutParams(lp);parent.addView(v);}
 Button btn(LinearLayout parent,String text,String hex,View.OnClickListener l){return btnH(parent,text,hex,44,l);}
 Button btnH(LinearLayout parent,String text,String hex,int h,View.OnClickListener l){var b=new Button(this);b.setText(text);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.WHITE);b.setBackground(roundBg(hex,h/4));b.setAllCaps(false);var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(h));lp.setMargins(0,dp(4),0,dp(4));b.setLayoutParams(lp);b.setOnClickListener(l);parent.addView(b);return b;}
 Button btnW(LinearLayout parent,String text,String hex,float weight,View.OnClickListener l){var b=new Button(this);b.setText(text);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.WHITE);b.setBackground(roundBg(hex,8));b.setAllCaps(false);var lp=new LinearLayout.LayoutParams(0,dp(36),weight);lp.setMargins(dp(2),dp(3),dp(2),dp(3));b.setLayoutParams(lp);b.setOnClickListener(l);parent.addView(b);return b;}
 Button outline(LinearLayout parent,String text,String hex,View.OnClickListener l){var b=new Button(this);b.setText(text);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.parseColor(hex));b.setBackground(strokeBg("#FFFFFF",hex,8));b.setAllCaps(false);var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(40));lp.setMargins(0,dp(4),0,dp(4));b.setLayoutParams(lp);b.setOnClickListener(l);parent.addView(b);return b;}
 Button outlineW(LinearLayout parent,String text,String hex,float weight,View.OnClickListener l){var b=new Button(this);b.setText(text);b.setTextSize(11);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.parseColor(hex));b.setBackground(strokeBg("#FFFFFF",hex,6));b.setAllCaps(false);var lp=new LinearLayout.LayoutParams(0,dp(32),weight);lp.setMargins(dp(2),dp(2),dp(2),dp(2));b.setLayoutParams(lp);b.setOnClickListener(l);parent.addView(b);return b;}
 Button soft(LinearLayout parent,String text,View.OnClickListener l){var b=new Button(this);b.setText(text);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.parseColor(CORAL_D));b.setBackground(roundBg(CORAL_L,8));b.setAllCaps(false);var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(36));lp.setMargins(0,dp(3),0,dp(3));b.setLayoutParams(lp);b.setOnClickListener(l);parent.addView(b);return b;}
 TextView chip(LinearLayout parent,String text,boolean sel,String accent,View.OnClickListener l){var t=new TextView(this);t.setText(text);t.setTextSize(12);t.setPadding(dp(10),dp(6),dp(10),dp(6));var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(dp(2),dp(2),dp(2),dp(2));t.setLayoutParams(lp);
  if(sel){t.setBackground(roundBg(accent,16));t.setTextColor(Color.WHITE);t.setTypeface(Typeface.DEFAULT_BOLD);}else{t.setBackground(strokeBg("#FFFFFF",BORDER,16));t.setTextColor(Color.parseColor(INK));}
  if(l!=null)t.setOnClickListener(l);parent.addView(t);return t;}
 TextView pill(LinearLayout parent,String text,boolean sel,View.OnClickListener l){var t=new TextView(this);t.setText(text);t.setTextSize(11);t.setGravity(Gravity.CENTER);t.setPadding(dp(9),dp(6),dp(9),dp(6));var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(dp(2),dp(2),dp(2),dp(2));t.setLayoutParams(lp);
  if(sel){t.setBackground(roundBg(CORAL,8));t.setTextColor(Color.WHITE);t.setTypeface(Typeface.DEFAULT_BOLD);}else{t.setBackground(roundBg(BEIGE,8));t.setTextColor(Color.parseColor(INK));}
  if(l!=null)t.setOnClickListener(l);parent.addView(t);return t;}
 void badge(LinearLayout parent,String status){if(status==null||status.isEmpty())return;String[] c=badgeColors(status);var t=new TextView(this);t.setText(badgeLabel(status));t.setTextSize(10);t.setTypeface(Typeface.DEFAULT_BOLD);t.setTextColor(Color.parseColor(c[0]));t.setBackground(roundBg(c[1],6));t.setPadding(dp(7),dp(3),dp(7),dp(3));var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(dp(3),0,0,0);t.setLayoutParams(lp);parent.addView(t);}
 String badgeLabel(String s){return switch(s){case "PENDING"->"Pending Confirmation";case "PENDING_OWNER"->"Pending";case "PENDING_PAYMENT"->"Pending Payment";case "CANCEL_PENDING"->"Cancel Requested";case "RESCHEDULED"->"Rescheduled";default->s.charAt(0)+s.substring(1).toLowerCase().replace('_',' ');};}
 String[] badgeColors(String s){return switch(s){case "CONFIRMED","VERIFIED","ACTIVE","PAID","SUCCESS"->new String[]{GREEN,GREEN_L};case "COMPLETED"->new String[]{BLUE,BLUE_L};case "PENDING","PENDING_OWNER","PENDING_PAYMENT","CANCEL_PENDING"->new String[]{AMBER,AMBER_L};case "CANCELLED","REJECTED","SUSPENDED","FAILED","INACTIVE"->new String[]{RED,RED_L};case "RESCHEDULED","REFUNDED"->new String[]{PLUM,PLUM_L};default->new String[]{MUTED,BEIGE};};}
 void parlourBadge(LinearLayout parent,String status){if(status==null)return;String txt=switch(status){case "VERIFIED"->"Verified";case "PENDING"->"Pending Review";case "SUSPENDED"->"Suspended";case "REJECTED"->"Rejected";default->status;};String[] c=badgeColors(status.equals("PENDING")?"PENDING_OWNER":status);var t=new TextView(this);t.setText(txt);t.setTextSize(10);t.setTypeface(Typeface.DEFAULT_BOLD);t.setTextColor(Color.parseColor(status.equals("REJECTED")?"#616161":c[0]));t.setBackground(roundBg(status.equals("REJECTED")?"#EEEEEE":c[1],6));t.setPadding(dp(7),dp(3),dp(7),dp(3));var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(dp(4),0,0,0);t.setLayoutParams(lp);parent.addView(t);}
 void payBadge(LinearLayout parent,String method){if(method==null||method.isEmpty())return;String txt=switch(method){case "ONLINE"->"Paid Online";case "SPLIT_50_50"->"Advance Paid";case "AT_PARLOUR"->"Pay at Counter";default->method;};String[] c=switch(method){case "ONLINE","SPLIT_50_50"->new String[]{GREEN,GREEN_L};case "AT_PARLOUR"->new String[]{BLUE,BLUE_L};default->new String[]{MUTED,BEIGE};};var t=new TextView(this);t.setText(txt);t.setTextSize(10);t.setTypeface(Typeface.DEFAULT_BOLD);t.setTextColor(Color.parseColor(c[0]));t.setBackground(roundBg(c[1],6));t.setPadding(dp(7),dp(3),dp(7),dp(3));var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(dp(3),0,0,0);t.setLayoutParams(lp);parent.addView(t);}
 EditText field(LinearLayout parent,String hint,String value,int type){return fieldC(parent,hint,value,type,CORAL);}
 EditText fieldC(LinearLayout parent,String hint,String value,int type,String stroke){var layout=new TextInputLayout(this);layout.setHint(hint);layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);layout.setBoxStrokeColor(Color.parseColor(stroke));layout.setBoxCornerRadii(dp(10),dp(10),dp(10),dp(10));layout.setPadding(0,dp(2),0,dp(2));parent.addView(layout);var e=new TextInputEditText(this);e.setText(value==null?"":value);e.setInputType(type);e.setTextSize(14);layout.addView(e);if((type&InputType.TYPE_TEXT_VARIATION_PASSWORD)==InputType.TYPE_TEXT_VARIATION_PASSWORD){layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);layout.setEndIconContentDescription("Show or hide password");}return e;}
 String value(EditText e){return e.getText()==null?"":e.getText().toString().trim();}
 Spinner spinner(LinearLayout parent,List<String> values){var s=new Spinner(this);var ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);s.setAdapter(ad);s.setBackground(strokeBg("#FFFFFF",BORDER,10));var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));lp.setMargins(0,dp(5),0,dp(5));s.setLayoutParams(lp);parent.addView(s);return s;}
 void emptyState(LinearLayout parent,String icon,String title,String sub,String action,Runnable go){var box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(dp(16),dp(16),dp(16),dp(16));parent.addView(box);
  var c=new TextView(this);c.setText(icon);c.setTextSize(28);c.setGravity(Gravity.CENTER);c.setBackground(roundBg(BEIGE,30));var clp=new LinearLayout.LayoutParams(dp(60),dp(60));c.setLayoutParams(clp);box.addView(c);
  var t=tv(box,title,17,true,INK);t.setGravity(Gravity.CENTER);var s=tv(box,sub,13,false,MUTED);s.setGravity(Gravity.CENTER);
  if(action!=null){var b=btnH(box,action,CORAL,44,v->go.run());var blp=(LinearLayout.LayoutParams)b.getLayoutParams();blp.width=ViewGroup.LayoutParams.WRAP_CONTENT;b.setPadding(dp(22),0,dp(22),0);}}
 String stars(double rating){int full=(int)Math.round(rating);var sb=new StringBuilder();for(int i=0;i<5;i++)sb.append(i<full?"★":"☆");return sb.toString();}
 String money(double v){if(v==Math.floor(v))return "৳"+(long)v;return String.format(Locale.US,"৳%.2f",v);}
 String fmtDT(String iso){try{var z=ZonedDateTime.parse(iso).withZoneSameInstant(zone);return z.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a",Locale.ENGLISH));}catch(Exception e){return iso;}}
 String fmtDate(String iso){try{var z=ZonedDateTime.parse(iso).withZoneSameInstant(zone);return z.format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.ENGLISH));}catch(Exception e){return iso;}}
 String fmtTime(String iso){try{var z=ZonedDateTime.parse(iso).withZoneSameInstant(zone);return z.format(DateTimeFormatter.ofPattern("h:mm a",Locale.ENGLISH));}catch(Exception e){return iso;}}
 double haversine(double la1,double lo1,double la2,double lo2){double r=6371,dLa=Math.toRadians(la2-la1),dLo=Math.toRadians(lo2-lo1);double a=Math.sin(dLa/2)*Math.sin(dLa/2)+Math.cos(Math.toRadians(la1))*Math.cos(Math.toRadians(la2))*Math.sin(dLo/2)*Math.sin(dLo/2);return 2*r*Math.asin(Math.sqrt(a));}
 void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 void message(String text){new AlertDialog.Builder(this).setTitle("LuxeParlour").setMessage(text).setPositiveButton("OK",null).show();}
 void confirm(String text,Runnable yes){new AlertDialog.Builder(this).setTitle("Please confirm").setMessage(text).setNegativeButton("Cancel",null).setPositiveButton("Confirm",(d,w)->yes.run()).show();}
 // ---------- glass dialogs (dark frosted style of the reference) ----------
 AlertDialog glassDialog;
 TextView glassText(LinearLayout parent,String s,int size,boolean bold,String color){var t=new TextView(this);t.setText(s==null?"":s);t.setTextSize(size);t.setTextColor(Color.parseColor(color));if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);t.setLineSpacing(0,1.1f);var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,dp(1),0,dp(1));t.setLayoutParams(lp);parent.addView(t);return t;}
 EditText glassField(LinearLayout parent,String hint,String value){var e=new EditText(this);e.setHint(hint);e.setText(value==null?"":value);e.setTextSize(14);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.parseColor(GLASS_MUT));e.setBackground(strokeBg("#2BFFFFFF","#4DFFFFFF",8));e.setPadding(dp(12),dp(10),dp(12),dp(10));var lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,dp(6),0,dp(6));e.setLayoutParams(lp);parent.addView(e);return e;}
 void glass(String title,LinearLayout body,String confirmText,String confirmColor,Runnable onConfirm){var root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(roundBg(GLASS_BG,26));root.setPadding(dp(18),dp(16),dp(18),dp(14));
  if(title!=null)glassText(root,title,17,true,"#FFFFFF");
  if(body!=null)root.addView(body);
  var row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);var rlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);rlp.setMargins(0,dp(10),0,0);row.setLayoutParams(rlp);root.addView(row);
  var cancel=new TextView(this);cancel.setText("Cancel");cancel.setTextSize(14);cancel.setTextColor(Color.parseColor("#F0ECF8"));cancel.setPadding(dp(12),dp(8),dp(12),dp(8));cancel.setOnClickListener(v->dismissGlass());row.addView(cancel);
  if(confirmText!=null){var ok=new Button(this);ok.setText(confirmText);ok.setTextSize(14);ok.setTypeface(Typeface.DEFAULT_BOLD);ok.setTextColor(Color.WHITE);ok.setBackground(roundBg(confirmColor==null?CORAL:confirmColor,10));ok.setAllCaps(false);var olp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(40));olp.setMargins(dp(10),0,0,0);ok.setLayoutParams(olp);ok.setOnClickListener(v->{dismissGlass();if(onConfirm!=null)onConfirm.run();});row.addView(ok);}
  var scroll=new ScrollView(this);scroll.addView(root);
  glassDialog=new AlertDialog.Builder(this).setView(scroll).create();if(glassDialog.getWindow()!=null)glassDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));glassDialog.show();}
 void dismissGlass(){if(glassDialog!=null){glassDialog.dismiss();glassDialog=null;}}
 void kv(LinearLayout parent,String k,String v){kv(parent,k,v,MUTED,INK,false);}
 void kv(LinearLayout parent,String k,String v,String kc,String vc,boolean boldV){var r=hrow(parent);var a=tv(r,k,13,false,kc);a.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var b=tv(r,v,13,boldV,vc);b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));b.setGravity(Gravity.END);}
 void glassKv(LinearLayout parent,String k,String v,String vc,boolean boldV){var r=hrow(parent);var a=glassText(r,k,13,false,GLASS_SUB);a.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var b=glassText(r,v,13,boldV,vc);b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));b.setGravity(Gravity.END);}
 // ---------- location ----------
 void applyGps(Location l){lat=l.getLatitude();lng=l.getLongitude();gpsSet=true;areaName="Current location";ui.locationText.setText("📍 Deliver services near: Current location");toast("Location updated to your current position");if(homeResults!=null)loadHomeResults();if(exResults!=null)loadExploreResults();}
 @Override public void onRequestPermissionsResult(int code,String[] perms,int[] grants){super.onRequestPermissionsResult(code,perms,grants);if(code==41&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)location();}
 void location(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},41);toast("Allow location access, then tap Change again.");return;}
  try{var lm=(LocationManager)getSystemService(LOCATION_SERVICE);Location best=null;for(String p:lm.getProviders(true)){var l=lm.getLastKnownLocation(p);if(l!=null&&(best==null||l.getTime()>best.getTime()))best=l;}
   if(best!=null&&(System.currentTimeMillis()-best.getTime())<120000){applyGps(best);return;}
   if(Build.VERSION.SDK_INT>=30){toast("Finding your location...");String prov=lm.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;final Location fb=best;lm.getCurrentLocation(prov,new android.os.CancellationSignal(),getMainExecutor(),l->{if(l!=null)applyGps(l);else if(fb!=null)applyGps(fb);else toast("No location fix yet. Check GPS is on and try again.");});}
   else if(best!=null)applyGps(best);else toast("No location fix yet. Check GPS is on and try again.");
  }catch(SecurityException e){toast("Location permission is not available.");}catch(Exception e){toast("Location is not available on this device.");}}
 void saveSession(JSONObject response){try{vm.repository.session(response);FirebaseSession.sync(this,vm.repository);FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t->{try{var o=new JSONObject().put("token",t).put("platform","ANDROID");vm.repository.request("POST","/api/device-token",o,null,(a,b)->{});}catch(Exception ignored){}});}catch(Exception e){message(e.getMessage());}}
 void clear(String title,String subtitle){content.removeAllViews();if(title!=null&&!title.isEmpty()){var t=tv(title);t.setTextSize(24);}if(subtitle!=null&&!subtitle.isEmpty())label(content,subtitle);}
 void screenBar(String title,Runnable back,String rightText,Runnable right){var r=hrow(content);var b=new TextView(this);b.setText("←");b.setTextSize(20);b.setTextColor(Color.parseColor(INK));b.setPadding(dp(4),dp(4),dp(10),dp(4));b.setOnClickListener(v->back.run());r.addView(b);var t=tv(r,title,16,true,INK);t.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));if(rightText!=null){var x=new TextView(this);x.setText(rightText);x.setTextSize(13);x.setTypeface(Typeface.DEFAULT_BOLD);x.setTextColor(Color.parseColor(CORAL));x.setPadding(dp(8),dp(4),dp(4),dp(4));x.setOnClickListener(v->right.run());r.addView(x);}}
 // ---------- customer: home ----------
 void home(){screen="home";chrome("home");hideTabs();content.removeAllViews();
  var srow=hrow(content);var sc=col(srow,1);var q=field(sc,"Search parlours, facials, haircuts, spas...",homeQuery,InputType.TYPE_CLASS_TEXT);btnW(srow,"Search",CORAL,0.55f,v->{homeQuery=value(q);loadHomeResults();});
  var rrow=hrow(content);var rlab=tv(rrow,"Radius:",12,true,INK);rlab.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));int[] radii={1,3,5,10,20,50};for(int r:radii)pill(rrow,r+" km",homeRadius==r,v->{homeRadius=r;home();});
  var scroll=new android.widget.HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);content.addView(scroll);var prow=new LinearLayout(this);prow.setOrientation(LinearLayout.HORIZONTAL);scroll.addView(prow);
  promoCard(prow,"BRIDAL25","25","Festive Bridal Season","Get up to 25% advance cashback on top-rated Gulshan & Banani salons");
  promoCard(prow,"BLISS200","15","Men's Luxury Grooming","Flat BDT 200 off haircut & beard sculpting combos");
  promoCard(prow,"ZENSPA","20","Spa Rejuvenation Week","Free organic steam wrap with every Swedish body massage");
  tv(content,"Browse by Category",15,true,INK);
  var cscroll=new android.widget.HorizontalScrollView(this);cscroll.setHorizontalScrollBarEnabled(false);content.addView(cscroll);var crow=new LinearLayout(this);crow.setOrientation(LinearLayout.HORIZONTAL);cscroll.addView(crow);
  for(int i=0;i<CATS.length;i++){final String c=CATS[i];chip(crow,CAT_LABEL[i],homeCategory.equals(c),CORAL,v->{homeCategory=c;home();});}
  var hrow2=hrow(content);homeCount=tv(hrow2,"Nearby Parlours",16,true,INK);homeCount.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var va=new TextView(this);va.setText("View All Filtered");va.setTextSize(12);va.setTextColor(Color.parseColor(CORAL));va.setOnClickListener(v->explore());hrow2.addView(va);
  homeResults=new LinearLayout(this);homeResults.setOrientation(LinearLayout.VERTICAL);content.addView(homeResults);
  var foot=tv(content,"Connection & account settings",12,false,MUTED);foot.setGravity(Gravity.CENTER);foot.setPadding(0,dp(14),0,dp(6));foot.setOnClickListener(v->accountScreen());
  loadHomeResults();refreshBell();
 }
 void promoCard(LinearLayout parent,String code,String pct,String title,String sub){var c=new LinearLayout(this);c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);var g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.parseColor(CORAL),Color.parseColor(PLUM)});g.setCornerRadius(dp(12));c.setBackground(g);c.setPadding(dp(14),dp(10),dp(12),dp(10));var lp=new LinearLayout.LayoutParams(dp(290),dp(110));lp.setMargins(dp(2),dp(6),dp(10),dp(6));c.setLayoutParams(lp);parent.addView(c);
  var texts=col(c,1);var chipT=new TextView(this);chipT.setText("CODE: "+code);chipT.setTextSize(9);chipT.setTypeface(Typeface.DEFAULT_BOLD);chipT.setTextColor(Color.WHITE);chipT.setBackground(roundBg("#40FFFFFF",6));chipT.setPadding(dp(6),dp(2),dp(6),dp(2));var clp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);chipT.setLayoutParams(clp);texts.addView(chipT);
  tv(texts,title,14,true,"#FFFFFF");var s=tv(texts,sub,11,false,"#EFFFFFFF");s.setMaxLines(2);
  var circ=new TextView(this);circ.setText(pct+"%");circ.setTextSize(14);circ.setTypeface(Typeface.DEFAULT_BOLD);circ.setTextColor(Color.WHITE);circ.setGravity(Gravity.CENTER);circ.setBackground(roundBg("#33FFFFFF",22));circ.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(44)));c.addView(circ);}
 void loadHomeResults(){if(homeResults==null)return;homeResults.removeAllViews();tv(homeResults,"Loading parlours...",13,false,MUTED);
  StringBuilder path=new StringBuilder("/api/public/parlours?sort="+(Double.isNaN(lat)?"rating":"distance")+"&radius="+homeRadius);
  if(!homeQuery.isEmpty())path.append("&q=").append(enc(homeQuery));
  if(!"ALL".equals(homeCategory))path.append("&category=").append(homeCategory);
  if(!Double.isNaN(lat))path.append("&lat=").append(lat).append("&lng=").append(lng);
  get(path.toString(),v->{var a=arr(v);homeResults.removeAllViews();if(homeCount!=null)homeCount.setText("Nearby Parlours ("+a.length()+")");
   if(a.length()==0){emptyState(homeResults,"🏬","No parlours found near you","No parlour exists within "+homeRadius+" km of "+areaName+". Try a larger radius or pick a different area.","Widen radius to 50 km",()->{homeRadius=50;home();});return;}
   for(int i=0;i<a.length();i++){var p=a.optJSONObject(i);if(p!=null)parlourCard(homeResults,p);}});}
 void parlourCard(LinearLayout parent,JSONObject p){long pid=p.optLong("parlour_id");var c=cardIn(parent);c.setPadding(0,0,0,0);
  String cat=p.optString("category","UNISEX");String bandTop=switch(cat){case "WOMEN"->CORAL_L;case "MEN","HAIR"->BLUE_L;case "SPA"->GREEN_L;default->PLUM_L;};
  var band=new LinearLayout(this);band.setOrientation(LinearLayout.HORIZONTAL);band.setGravity(Gravity.CENTER_VERTICAL);var bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.parseColor(bandTop),Color.WHITE});band.setBackground(bg);band.setPadding(dp(10),dp(8),dp(10),dp(8));band.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(64)));c.addView(band);
  var catchip=new TextView(this);catchip.setText(cat);catchip.setTextSize(10);catchip.setTypeface(Typeface.DEFAULT_BOLD);catchip.setTextColor(Color.WHITE);catchip.setBackground(roundBg(CORAL,6));catchip.setPadding(dp(7),dp(3),dp(7),dp(3));band.addView(catchip);
  if(p.has("distance_km")&&!p.isNull("distance_km")){var d=new TextView(this);d.setText(String.format(Locale.US,"%.1f km",p.optDouble("distance_km")));d.setTextSize(10);d.setTextColor(Color.parseColor(INK));d.setBackground(roundBg(BEIGE,6));d.setPadding(dp(6),dp(2),dp(6),dp(2));var dlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);dlp.setMargins(dp(6),0,0,0);d.setLayoutParams(dlp);band.addView(d);}
  var sp=new View(this);sp.setLayoutParams(new LinearLayout.LayoutParams(0,1,1));band.addView(sp);
  boolean inCompare=compareIds.contains(pid);var comp=new TextView(this);comp.setText(inCompare?"✓ Comp":"+ Comp");comp.setTextSize(11);comp.setTypeface(Typeface.DEFAULT_BOLD);comp.setPadding(dp(8),dp(4),dp(8),dp(4));
  if(inCompare){comp.setBackground(roundBg(PLUM,6));comp.setTextColor(Color.WHITE);}else{comp.setTextColor(Color.parseColor(PLUM));}
  comp.setOnClickListener(v->{toggleCompare(pid);});band.addView(comp);
  boolean fav=favIds.contains(pid);var heart=new TextView(this);heart.setText(fav?"♥":"♡");heart.setTextSize(20);heart.setTextColor(Color.parseColor(fav?CORAL:MUTED));heart.setPadding(dp(8),0,dp(2),0);heart.setOnClickListener(v->{if(favIds.contains(pid))favIds.remove(pid);else favIds.add(pid);persistUi();if(homeResults!=null)loadHomeResults();});band.addView(heart);
  var body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(12),dp(8),dp(12),dp(10));c.addView(body);
  var nrow=hrow(body);var nm=tv(nrow,p.optString("name","Parlour"),15,true,INK);nm.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));if("VERIFIED".equals(p.optString("status")))parlourBadge(nrow,"VERIFIED");
  var rrow=hrow(body);var st=new TextView(this);st.setText(stars(p.optDouble("rating")));st.setTextSize(13);st.setTextColor(Color.parseColor(GOLD));rrow.addView(st);tv(rrow," "+String.format(Locale.US,"%.1f",p.optDouble("rating")),12,true,INK);if(p.optDouble("popularity")>0)tv(rrow," ("+(long)p.optDouble("popularity")+" bookings)",10,false,MUTED);
  tv(body,"📍 "+p.optString("address",""),12,false,MUTED);
  var desc=tv(body,p.optString("description",""),12,false,MUTED);desc.setMaxLines(2);desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
  divider(body);
  var prow=hrow(body);var pc=col(prow,1);tv(pc,"Starts from",10,false,MUTED);tv(pc,money(p.optDouble("starting_price")),15,true,CORAL);
  outlineW(prow,"Details",CORAL,0.9f,v->details(pid));btnW(prow,"Book Slot",CORAL,1.1f,v->wizardStart(pid,0));
  c.setOnClickListener(v->details(pid));
 }
 String screen="";
 void toggleCompare(long pid){if(compareIds.contains(pid))compareIds.remove(pid);else compareIds.add(pid);if("explore".equals(screen))explore();else home();}
 // ---------- customer: explore ----------
 void explore(){screen="explore";chrome("explore");hideTabs();content.removeAllViews();
  screenBar("Explore & Filter Parlours",()->home(),"Filters",this::filtersDialog);
  var srow=hrow(content);var sc=col(srow,1);var q=field(sc,"Search by name, area (Gulshan, Banani, Dhanmondi)...",exQuery,InputType.TYPE_CLASS_TEXT);btnW(srow,"Search",CORAL,0.55f,v->{exQuery=value(q);loadExploreResults();});
  var cscroll=new android.widget.HorizontalScrollView(this);cscroll.setHorizontalScrollBarEnabled(false);content.addView(cscroll);var crow=new LinearLayout(this);crow.setOrientation(LinearLayout.HORIZONTAL);cscroll.addView(crow);
  for(int i=0;i<CATS.length;i++){final String c=CATS[i];chip(crow,CAT_LABEL[i],exCategory.equals(c),CORAL,v->{exCategory=c;explore();});}
  var srt=hrow(content);tv(srt,"Sort:",11,true,MUTED);String[][] sorts={{"distance","Nearest"},{"rating","Top Rated"},{"price","Price: Low"},{"popularity","Popular"}};for(String[] s:sorts)pill(srt,s[1],exSort.equals(s[0]),v->{exSort=s[0];explore();});
  exCount=tv(content,"Found parlours matching criteria",12,false,MUTED);
  exResults=new LinearLayout(this);exResults.setOrientation(LinearLayout.VERTICAL);content.addView(exResults);
  loadExploreResults();
 }
 void loadExploreResults(){if(exResults==null)return;exResults.removeAllViews();tv(exResults,"Loading parlours...",13,false,MUTED);
  StringBuilder path=new StringBuilder("/api/public/parlours?sort="+exSort+"&radius="+exRadius);
  if(!exQuery.isEmpty())path.append("&q=").append(enc(exQuery));
  if(!"ALL".equals(exCategory))path.append("&category=").append(exCategory);
  if(!Double.isNaN(lat))path.append("&lat=").append(lat).append("&lng=").append(lng);
  get(path.toString(),v->{var all=arr(v);var list=new ArrayList<JSONObject>();for(int i=0;i<all.length();i++){var p=all.optJSONObject(i);if(p!=null&&p.optDouble("rating")>=exMinRating)list.add(p);}
   if(!exOpenNow&&!exAvailToday){renderExplore(list);return;}
   // open-now / available-today need each parlour's hours: fetch details, then filter
   var kept=new ArrayList<JSONObject>();var total=list.size();if(total==0){renderExplore(kept);return;}
   var counter=new int[]{0};
   for(var p:list){long pid=p.optLong("parlour_id");fetchDetail(pid,d->{synchronized(counter){if(d!=null){boolean openToday=todayHours(d)!=null;boolean open=openNow(d);if((!exAvailToday||openToday)&&(!exOpenNow||open))kept.add(p);}if(++counter[0]==total)renderExplore(kept);}});}
  });}
 void renderExplore(List<JSONObject> list){if(exResults==null)return;exResults.removeAllViews();if(exCount!=null)exCount.setText("Found ("+list.size()+") parlours matching criteria");
  if(list.isEmpty()){emptyState(exResults,"🔍","No matching parlours found","No parlour exists within "+exRadius+" km of "+areaName+" matching your filters. Try a larger radius or reset the filters.","Reset All Filters",()->{exQuery="";exCategory="ALL";exRadius=20;exMinRating=0;exOpenNow=false;exAvailToday=false;exSort="distance";explore();});return;}
  for(var p:list)parlourCard(exResults,p);}
 void filtersDialog(){var box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(16),dp(20),dp(10));
  var hr=new LinearLayout(this);hr.setOrientation(LinearLayout.HORIZONTAL);hr.setGravity(Gravity.CENTER_VERTICAL);box.addView(hr);var t=tv(hr,"Filter Parlours",18,true,INK);t.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var reset=new TextView(this);reset.setText("Reset All");reset.setTextSize(13);reset.setTypeface(Typeface.DEFAULT_BOLD);reset.setTextColor(Color.parseColor(CORAL));hr.addView(reset);
  final int[] rad={exRadius};final double[] minR={exMinRating};final boolean[] on={exOpenNow},at={exAvailToday};
  var rl=tv(box,"Search Radius: "+rad[0]+" km",13,true,INK);var seek=new SeekBar(this);seek.setMax(24);seek.setProgress(rad[0]-1);box.addView(seek);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){rad[0]=p+1;rl.setText("Search Radius: "+rad[0]+" km");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
  tv(box,"Minimum Rating",13,true,INK);var chips=hrow(box);String[] ml={"Any","4.0+ ★","4.5+ ★","4.8+ ★"};double[] mv={0,4.0,4.5,4.8};for(int i=0;i<ml.length;i++){final double val=mv[i];chip(chips,ml[i],minR[0]==val,CORAL,v->{minR[0]=val;});}
  var sw1=new Switch(this);sw1.setText("Open Now Only");sw1.setTextSize(13);sw1.setChecked(on[0]);box.addView(sw1);
  var sw2=new Switch(this);sw2.setText("Available Today");sw2.setTextSize(13);sw2.setChecked(at[0]);box.addView(sw2);
  var dlg=new AlertDialog.Builder(this).setView(box).create();
  reset.setOnClickListener(v->{rad[0]=10;minR[0]=0;on[0]=false;at[0]=false;seek.setProgress(9);rl.setText("Search Radius: 10 km");sw1.setChecked(false);sw2.setChecked(false);});
  var apply=btnH(box,"Apply Filters",CORAL,48,v->{exRadius=rad[0];exMinRating=minR[0];exOpenNow=sw1.isChecked();exAvailToday=sw2.isChecked();dlg.dismiss();loadExploreResults();});
  dlg.show();}
 // ---------- customer: parlour detail ----------
 void fetchDetail(long pid,Consumer<JSONObject> done){var cached=detailCache.get(pid);if(cached!=null){done.accept(cached);return;}
  String path="/api/public/parlours/"+pid+(!Double.isNaN(lat)?"?lat="+lat+"&lng="+lng:"");
  get(path,v->{var d=obj(v);detailCache.put(pid,d);done.accept(d);});}
 String[] todayHours(JSONObject detail){var hours=detail.optJSONArray("business_hours");if(hours==null)return null;int dow=LocalDate.now(zone).getDayOfWeek().getValue();for(int i=0;i<hours.length();i++){var h=hours.optJSONObject(i);if(h!=null&&h.optInt("day_of_week")==dow){String st=h.optString("start_time"),en=h.optString("end_time");if(st.length()==8&&st.endsWith(":00"))st=st.substring(0,5);if(en.length()==8&&en.endsWith(":00"))en=en.substring(0,5);return new String[]{st,en};}}
  return null;}
 boolean openNow(JSONObject detail){var th=todayHours(detail);if(th==null)return false;try{var now=LocalTime.now(zone);var s=LocalTime.parse(th[0].length()>5?th[0]:th[0]+":00");var e=LocalTime.parse(th[1].length()>5?th[1]:th[1]+":00");return !now.isBefore(s)&&now.isBefore(e);}catch(Exception ex){return false;}}
 String staffName(JSONObject detail,long staffId){var staff=detail==null?null:detail.optJSONArray("staff");if(staff!=null)for(int i=0;i<staff.length();i++){var s=staff.optJSONObject(i);if(s!=null&&s.optLong("staff_id")==staffId)return s.optString("name","");}return staffId>0?"Staff #"+staffId:"Any available";}
 void details(long pid){chrome("");hideTabs();content.removeAllViews();tv(content,"Loading parlour...",13,false,MUTED);
  fetchDetail(pid,d->{selected=d;content.removeAllViews();var p=d.optJSONObject("parlour");if(p==null){tv(content,"Parlour not found.",15,true,INK);return;}
   screenBar(p.optString("name","Parlour"),()->home(),"⇄ Compare",()->{if(!compareIds.contains(pid))toggleCompareSilent(pid);compare();});
   var hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setGravity(Gravity.BOTTOM);var hg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.parseColor(CORAL_L),Color.parseColor(BEIGE)});hg.setCornerRadius(dp(14));hero.setBackground(hg);hero.setPadding(dp(16),dp(16),dp(16),dp(14));hero.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(170)));content.addView(hero);
   var hchips=hrow(hero);var cc=new TextView(this);cc.setText(p.optString("category","UNISEX"));cc.setTextSize(11);cc.setTypeface(Typeface.DEFAULT_BOLD);cc.setTextColor(Color.WHITE);cc.setBackground(roundBg(CORAL,6));cc.setPadding(dp(8),dp(3),dp(8),dp(3));hchips.addView(cc);if("VERIFIED".equals(p.optString("status")))parlourBadge(hchips,"VERIFIED");
   tv(hero,p.optString("name","Parlour"),20,true,INK);tv(hero,p.optString("address",""),13,false,MUTED);
   var images=d.optJSONArray("parlour_images");if(images!=null&&images.length()>0){var ph=new TextView(this);ph.setText("📷 "+images.length()+" Photos");ph.setTextSize(11);ph.setTextColor(Color.WHITE);ph.setBackground(roundBg("#99000000",6));ph.setPadding(dp(8),dp(3),dp(8),dp(3));var plp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);plp.setMargins(0,dp(6),0,0);ph.setLayoutParams(plp);hero.addView(ph);}
   // quick stats
   var stats=card();stats.setOrientation(LinearLayout.HORIZONTAL);
   var policy=firstPolicy(d);
   statCol(stats,"★ "+String.format(Locale.US,"%.1f",p.optDouble("rating")),GOLD,(long)p.optDouble("popularity")+" bookings");
   statCol(stats,p.has("distance_km")&&!p.isNull("distance_km")?String.format(Locale.US,"%.1f km",p.optDouble("distance_km")):"—",INK,"Distance");
   var th=todayHours(d);boolean open=openNow(d);
   statCol(stats,open?"Open":"Closed",open?GREEN:RED,th==null?"Hours n/a":th[0]+" - "+th[1]);
   statCol(stats,(policy==null?0:policy.optInt("min_advance_pct"))+"%",CORAL,"Min Advance");
   // tabs
   var bodyBox=new LinearLayout(this);bodyBox.setOrientation(LinearLayout.VERTICAL);content.addView(bodyBox);
   var staff=d.optJSONArray("staff");int staffCount=staff==null?0:staff.length();
   var tabRow=hrow(content);content.removeView(tabRow);content.addView(tabRow,content.indexOfChild(bodyBox));
   final int[] tab={0};String[] tabNames={"Services","Stylists ("+staffCount+")","Policies & Map"};
   Runnable renderTab=()->{bodyBox.removeAllViews();
    if(tab[0]==0)detailServices(bodyBox,d,p);
    else if(tab[0]==1)detailStaff(bodyBox,d);
    else detailPolicies(bodyBox,d,p);};
   for(int i=0;i<3;i++){final int idx=i;chip(tabRow,tabNames[i],tab[0]==i,CORAL,v->{tab[0]=idx;detailsTabRefresh(tabRow,tabNames,tab);renderTab.run();});}
   renderTab.run();
   var ratings=d.optJSONArray("ratings");if(ratings!=null&&ratings.length()>0){divider(content);tv(content,"Customer Reviews ("+ratings.length()+")",15,true,INK);
    for(int i=0;i<ratings.length();i++){var r=ratings.optJSONObject(i);if(r==null)continue;var rc=cardIn(content);
     var rh=hrow(rc);tv(rh,"STAFF".equals(r.optString("target_type"))?"💇 Stylist review":"🏬 Parlour review",12,true,PLUM);var spc=col(rh,1);
     int sc=r.optInt("score");var stb=new StringBuilder();for(int k=0;k<5;k++)stb.append(k<sc?"★":"☆");tv(rh,stb.toString(),13,true,GOLD);
     if(r.optString("review").length()>0)tv(rc,"\u201C"+r.optString("review")+"\u201D",12,false,INK);
     tv(rc,fmtDate(r.optString("created_at")),10,false,MUTED);}}
   divider(content);var brow=hrow(content);var bc=col(brow,1);tv(bc,"Starting from",11,false,MUTED);tv(bc,money(p.optDouble("starting_price")),15,true,CORAL);
   btnW(brow,"Book Appointment",CORAL,1.3f,v->wizardStart(pid,0));
   if(images!=null&&images.length()>0){tv(content,"Photos",15,true,INK);var grow=hrow(content);for(int i=0;i<images.length();i++){var im=images.optJSONObject(i);if(im==null)continue;final String url=im.optString("image_url");chip(grow,"🖼 Photo "+(i+1),false,PLUM,v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))));}}
  });}
 void detailsTabRefresh(LinearLayout row,String[] names,int[] tab){for(int i=0;i<row.getChildCount()&&i<names.length;i++){var t=(TextView)row.getChildAt(i);boolean sel=tab[0]==i;if(sel){t.setBackground(roundBg(CORAL,16));t.setTextColor(Color.WHITE);t.setTypeface(Typeface.DEFAULT_BOLD);}else{t.setBackground(strokeBg("#FFFFFF",BORDER,16));t.setTextColor(Color.parseColor(INK));t.setTypeface(Typeface.DEFAULT);}}}
 void statCol(LinearLayout parent,String value,String color,String sub){var c=col(parent,1);var v=tv(c,value,14,true,color);v.setGravity(Gravity.CENTER);var s=tv(c,sub,11,false,MUTED);s.setGravity(Gravity.CENTER);}
 JSONObject firstPolicy(JSONObject detail){var pols=detail.optJSONArray("cancellation_policies");return pols==null||pols.length()==0?null:pols.optJSONObject(0);}
 void detailServices(LinearLayout box,JSONObject d,JSONObject p){var services=d.optJSONArray("services");if(services==null||services.length()==0){label(box,"No services listed yet.");return;}
  for(int i=0;i<services.length();i++){var s=services.optJSONObject(i);if(s==null)continue;final long sid=s.optLong("service_id");var c=cardIn(box);
   tv(c,s.optString("name","Service"),15,true,INK);tv(c,"⏱ "+s.optInt("duration_min")+" mins",11,false,MUTED);
   if(!s.optString("description").isEmpty())tv(c,s.optString("description"),12,false,MUTED);
   var pr=hrow(c);var pc=col(pr,1);tv(pc,money(s.optDouble("price")),16,true,CORAL);soft(c,"Select Service & Book",v->wizardStart(p.optLong("parlour_id"),sid));}}
 void detailStaff(LinearLayout box,JSONObject d){var staff=d.optJSONArray("staff");if(staff==null||staff.length()==0){label(box,"No stylists listed yet.");return;}
  for(int i=0;i<staff.length();i++){var s=staff.optJSONObject(i);if(s==null)continue;var c=cardIn(box);var r=hrow(c);
   var av=new TextView(this);av.setText(s.optString("name","?").substring(0,1));av.setTextSize(20);av.setTypeface(Typeface.DEFAULT_BOLD);av.setTextColor(Color.parseColor(CORAL));av.setGravity(Gravity.CENTER);av.setBackground(roundBg(CORAL_L,27));av.setLayoutParams(new LinearLayout.LayoutParams(dp(54),dp(54)));r.addView(av);
   var info=col(r,1);info.setPadding(dp(10),0,0,0);tv(info,s.optString("name",""),15,true,INK);tv(info,s.optString("designation",""),12,false,CORAL);
   tv(info,s.optInt("experience")+" years experience",11,false,MUTED);
   tv(info,stars(s.optDouble("rating"))+" "+String.format(Locale.US,"%.1f",s.optDouble("rating")),11,false,MUTED);
   if(!s.optString("specialization").isEmpty())tv(info,"Expertise: "+s.optString("specialization"),11,false,MUTED);
   boolean active="ACTIVE".equals(s.optString("status"));var bd=new TextView(this);bd.setText(active?"Available":"Busy");bd.setTextSize(10);bd.setTypeface(Typeface.DEFAULT_BOLD);bd.setTextColor(Color.parseColor(active?GREEN:RED));bd.setBackground(roundBg(active?GREEN_L:RED_L,4));bd.setPadding(dp(6),dp(2),dp(6),dp(2));var ilp=(LinearLayout.LayoutParams)info.getLayoutParams();var brow=new LinearLayout(this);brow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));brow.addView(bd);info.addView(brow);}}
 void detailPolicies(LinearLayout box,JSONObject d,JSONObject p){tv(box,"About Salon",15,true,INK);tv(box,p.optString("description",""),13,false,MUTED);
  tv(box,"Contact & Address",15,true,INK);
  var addr=tv(box,"📍 "+p.optString("address",""),13,false,INK);addr.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode(p.optString("address"))))));
  var phone=tv(box,"📞 "+p.optString("phone",""),13,false,CORAL);phone.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+p.optString("phone")))));
  var th=todayHours(d);tv(box,"🕐 Daily "+(th==null?"hours not set":th[0]+" to "+th[1]),13,false,MUTED);
  var policy=firstPolicy(d);var c=cardIn(box);tv(c,"Cancellation & Refund Policy",14,true,PLUM);
  if(policy==null){tv(c,"• This parlour has not published a cancellation policy yet.",12,false,MUTED);}
  else{tv(c,"• Notice Window: Free cancellation up to "+policy.optInt("cancel_window_hr")+" hours before slot time.",12,false,MUTED);
   tv(c,"• Cancellation Fee: "+policy.optInt("fee_pct")+"% processing fee applies.",12,false,MUTED);
   tv(c,"• Refund Amount: "+policy.optInt("refund_pct")+"% refund credited via original gateway.",12,false,MUTED);
   tv(c,"• Rescheduling: "+("ALLOWED".equals(policy.optString("reschedule_rule"))?"Allowed (see policy window).":"Not allowed by this parlour."),12,false,MUTED);}
  divider(c);tv(c,"Payment Policy",14,true,CORAL);
  boolean adv=policy!=null&&policy.optBoolean("advance_required");int pct=policy==null?0:policy.optInt("min_advance_pct");
  tv(c,adv&&pct>0?"• Requires minimum "+pct+"% advance payment via SSLCommerz (bKash/Nagad/Cards).":"• Zero advance required! Pay entire bill at parlour counter.",12,false,MUTED);
  tv(c,"• Pay at parlour is ENABLED for remaining balance or full amount.",12,false,MUTED);}
 void toggleCompareSilent(long pid){if(compareIds.contains(pid))compareIds.remove(pid);else compareIds.add(pid);}
 // ---------- customer: booking wizard ----------
 Button btnSW(LinearLayout parent,String text,String hex,float weight,View.OnClickListener l){var b=new Button(this);b.setText(text);b.setTextSize(11);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.WHITE);b.setBackground(roundBg(hex,6));b.setAllCaps(false);var lp=new LinearLayout.LayoutParams(0,dp(32),weight);lp.setMargins(dp(2),dp(2),dp(2),dp(2));b.setLayoutParams(lp);b.setOnClickListener(l);parent.addView(b);return b;}
 void fetchSlots(long pid,String servicesCsv,Long staffId,LocalDate date,Consumer<JSONArray> done){String path="/api/public/parlours/"+pid+"/availability?services="+servicesCsv+"&date="+date+(staffId!=null?"&staff="+staffId:"");get(path,v->{JSONArray s=v instanceof JSONObject o?o.optJSONArray("slots"):arr(v);done.accept(s==null?new JSONArray():s);});}
 void slotGrid(LinearLayout box,JSONArray slots,JSONObject current,Consumer<JSONObject> onPick){box.removeAllViews();if(slots==null||slots.length()==0){tv(box,"No slots are open on this date. Please choose another date.",13,false,MUTED);return;}
  LinearLayout row=null;for(int i=0;i<slots.length();i++){var s=slots.optJSONObject(i);if(s==null)continue;if(i%3==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);box.addView(row);}
   boolean sel=current!=null&&current.optString("start_at").equals(s.optString("start_at"));
   var cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);cell.setPadding(dp(6),dp(8),dp(6),dp(8));
   cell.setBackground(sel?roundBg(CORAL,8):strokeBg("#FFFFFF",BORDER,8));var clp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);clp.setMargins(dp(3),dp(3),dp(3),dp(3));cell.setLayoutParams(clp);
   var t=new TextView(this);t.setText(fmtTime(s.optString("start_at")));t.setTextSize(12);t.setTypeface(Typeface.DEFAULT_BOLD);t.setTextColor(Color.parseColor(sel?"#FFFFFF":INK));t.setGravity(Gravity.CENTER);cell.addView(t);
   var u=new TextView(this);u.setText("Available");u.setTextSize(9);u.setTextColor(Color.parseColor(sel?"#FFFFFF":GREEN));u.setGravity(Gravity.CENTER);cell.addView(u);
   final JSONObject slot=s;cell.setOnClickListener(v->onPick.accept(slot));row.addView(cell);}
  if(row!=null&&slots.length()%3!=0){for(int k=slots.length()%3;k<3;k++){var pad=new View(this);pad.setLayoutParams(new LinearLayout.LayoutParams(0,1,1));row.addView(pad);}}}
 void fetchSlotsFull(long pid,String servicesCsv,Long staffId,LocalDate date,java.util.function.BiConsumer<JSONArray,JSONArray> done){String path="/api/public/parlours/"+pid+"/availability?services="+servicesCsv+"&date="+date+(staffId!=null?"&staff="+staffId:"");get(path,v->{JSONObject o=v instanceof JSONObject j?j:null;JSONArray s=o!=null?o.optJSONArray("slots"):null;JSONArray bk=o!=null?o.optJSONArray("booked_slots"):null;done.accept(s==null?new JSONArray():s,bk==null?new JSONArray():bk);});}
 void slotGridBooked(LinearLayout box,JSONArray slots,JSONArray booked,JSONObject current,Consumer<JSONObject> onPick){box.removeAllViews();
  var all=new java.util.ArrayList<JSONObject>();if(slots!=null)for(int i=0;i<slots.length();i++){var s=slots.optJSONObject(i);if(s!=null)all.add(s);}
  if(booked!=null)for(int i=0;i<booked.length();i++){var s=booked.optJSONObject(i);if(s!=null){try{var c=new JSONObject(s.toString());c.put("booked",true);all.add(c);}catch(Exception ignored){}}}
  if(all.isEmpty()){tv(box,"No slots are open on this date. Please choose another date.",13,false,MUTED);return;}
  all.sort(java.util.Comparator.comparing(s->s.optString("start_at")));
  LinearLayout row=null;for(int i=0;i<all.size();i++){var s=all.get(i);if(i%3==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);box.addView(row);}
   boolean bk=s.optBoolean("booked");boolean sel=!bk&&current!=null&&current.optString("start_at").equals(s.optString("start_at"));
   var cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);cell.setPadding(dp(6),dp(8),dp(6),dp(8));
   cell.setBackground(sel?roundBg(CORAL,8):bk?roundBg("#ECECEC",8):strokeBg("#FFFFFF",BORDER,8));var clp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);clp.setMargins(dp(3),dp(3),dp(3),dp(3));cell.setLayoutParams(clp);
   var t=new TextView(this);t.setText(fmtTime(s.optString("start_at")));t.setTextSize(12);t.setTypeface(Typeface.DEFAULT_BOLD);t.setTextColor(Color.parseColor(sel?"#FFFFFF":bk?MUTED:INK));t.setGravity(Gravity.CENTER);cell.addView(t);
   var u=new TextView(this);u.setText(bk?"Booked":"Available");u.setTextSize(9);u.setTextColor(Color.parseColor(sel?"#FFFFFF":bk?"#B53444":GREEN));u.setGravity(Gravity.CENTER);cell.addView(u);
   if(!bk){final JSONObject slot=s;cell.setOnClickListener(v->onPick.accept(slot));}row.addView(cell);}
  if(row!=null&&all.size()%3!=0){for(int k=all.size()%3;k<3;k++){var pad=new View(this);pad.setLayoutParams(new LinearLayout.LayoutParams(0,1,1));row.addView(pad);}}}
 String servicesCsv(Set<Long> ids){var list=new ArrayList<>(ids);Collections.sort(list);var sb=new StringBuilder();for(int i=0;i<list.size();i++){if(i>0)sb.append(',');sb.append(list.get(i));}return sb.toString();}
 int wizMinutes(){int m=0;var services=wizDetail==null?null:wizDetail.optJSONArray("services");if(services!=null)for(int i=0;i<services.length();i++){var s=services.optJSONObject(i);if(s!=null&&wizServices.contains(s.optLong("service_id")))m+=s.optInt("duration_min");}return m;}
 double wizTotal(){double t=0;var services=wizDetail==null?null:wizDetail.optJSONArray("services");if(services!=null)for(int i=0;i<services.length();i++){var s=services.optJSONObject(i);if(s!=null&&wizServices.contains(s.optLong("service_id")))t+=s.optDouble("price");}return t;}
 void wizardStart(long pid,long preselect){if("CUSTOMER".equals(vm.repository.role())&&!getSharedPreferences("ui",MODE_PRIVATE).getBoolean("custAuth",false)){var gbox=new LinearLayout(this);gbox.setOrientation(LinearLayout.VERTICAL);glassText(gbox,"You need an account to book a service. Browsing parlours and reading reviews is free — but booking requires signing in, so your appointments, payments and reviews stay saved under your account.",13,false,GLASS_SUB);glass("🔐 Sign in to book",gbox,"Sign In",CORAL,()->unifiedLogin());return;}
 wizPid=pid;wizStep=1;wizServices.clear();wizStaff=null;wizSlot=null;wizMethod="AT_PARLOUR";wizPayChoice="CASH";wizNameText="";wizPhoneText="";wizKey=UUID.randomUUID().toString();wizDate=LocalDate.now(zone).plusDays(1);wizSlotsKey="";
  fetchDetail(pid,d->{wizDetail=d;var services=d.optJSONArray("services");boolean added=false;if(services!=null)for(int i=0;i<services.length();i++){var s=services.optJSONObject(i);if(s!=null&&s.optLong("service_id")==preselect){wizServices.add(preselect);added=true;}}
   if(!added&&services!=null&&services.length()>0)wizServices.add(services.optJSONObject(0).optLong("service_id"));
   renderWizard();});}
 String wizPayChoice="CASH";
 void renderWizard(){chrome("");hideTabs();content.removeAllViews();var p=wizDetail.optJSONObject("parlour");if(p==null)return;
  screenBar("Book Appointment",()->{if(wizStep>1){wizStep--;renderWizard();}else details(wizPid);},null,null);
  tv(content,p.optString("name")+" • Step "+wizStep+" of 4",11,false,MUTED);
  var ind=hrow(content);String[] labels={"Services","Stylist","Date & Slot","Payment"};
  for(int i=1;i<=4;i++){var colI=col(ind,1);var circ=new TextView(this);circ.setGravity(Gravity.CENTER);circ.setTextSize(12);circ.setTypeface(Typeface.DEFAULT_BOLD);circ.setLayoutParams(new LinearLayout.LayoutParams(dp(28),dp(28)));((LinearLayout.LayoutParams)circ.getLayoutParams()).gravity=Gravity.CENTER_HORIZONTAL;
   if(i<wizStep){circ.setText("✓");circ.setBackground(roundBg(GREEN,14));circ.setTextColor(Color.WHITE);}else if(i==wizStep){circ.setText(String.valueOf(i));circ.setBackground(roundBg(CORAL,14));circ.setTextColor(Color.WHITE);}else{circ.setText(String.valueOf(i));circ.setBackground(roundBg(BEIGE,14));circ.setTextColor(Color.parseColor(INK));}
   colI.addView(circ);var lb=tv(colI,labels[i-1],11,false,MUTED);lb.setGravity(Gravity.CENTER);}
  wizBody=new LinearLayout(this);wizBody.setOrientation(LinearLayout.VERTICAL);content.addView(wizBody);
  divider(content);var brow=hrow(content);var bc=col(brow,1);wizCountLabel=tv(bc,"",11,false,MUTED);wizTotalLabel=tv(bc,"",16,true,CORAL);
  wizAction=btnW(brow,wizStep<4?"Continue":("AT_PARLOUR".equals(wizMethod)?"Confirm Booking":"Pay & Confirm"),CORAL,1.1f,v->wizardNext());
  renderWizStep();}
 void wizardNext(){if(wizStep==1){if(wizServices.isEmpty()){toast("Please select at least one service.");return;}wizStep=2;renderWizard();}
  else if(wizStep==2){wizStep=3;renderWizard();}
  else if(wizStep==3){if(wizSlot==null){toast("Please select an available time slot.");return;}wizStep=4;renderWizard();}
  else submitBooking();}
 void updateWizBottom(){if(wizCountLabel!=null)wizCountLabel.setText(wizServices.size()+" services • "+wizMinutes()+"m");if(wizTotalLabel!=null)wizTotalLabel.setText("Total: "+money(wizTotal()));
  if(wizAction!=null&&wizStep==4)wizAction.setText("AT_PARLOUR".equals(wizMethod)?"Confirm Booking":"Pay & Confirm");}
 void renderWizStep(){wizBody.removeAllViews();updateWizBottom();
  if(wizStep==1)wizStepServices();else if(wizStep==2)wizStepStaff();else if(wizStep==3)wizStepSlots();else wizStepPayment();}
 void wizCard(LinearLayout parent,boolean selected){var c=cardIn(parent);c.setBackground(selected?strokeBg(CORAL_L,CORAL,10):strokeBg("#FFFFFF",BORDER,10));}
 void wizStepServices(){head(wizBody,"Select Desired Services");label(wizBody,"You can select multiple services for a single combined appointment.");
  var services=wizDetail.optJSONArray("services");if(services==null)return;
  for(int i=0;i<services.length();i++){var s=services.optJSONObject(i);if(s==null)continue;final long sid=s.optLong("service_id");boolean sel=wizServices.contains(sid);
   var c=cardIn(wizBody);c.setBackground(sel?strokeBg(CORAL_L,CORAL,10):strokeBg("#FFFFFF",BORDER,10));var r=hrow(c);
   var cb=new CheckBox(this);cb.setChecked(sel);cb.setEnabled(false);cb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(CORAL)));r.addView(cb);
   var info=col(r,1);tv(info,s.optString("name"),14,true,INK);tv(info,s.optInt("duration_min")+" mins",11,false,MUTED);var dd=tv(info,s.optString("description"),11,false,MUTED);dd.setMaxLines(1);dd.setEllipsize(android.text.TextUtils.TruncateAt.END);
   var prc=tv(r,money(s.optDouble("price")),14,true,CORAL);prc.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
   c.setOnClickListener(v->{if(wizServices.contains(sid))wizServices.remove(sid);else wizServices.add(sid);wizSlot=null;wizSlotsKey="";renderWizStep();});}}
 void wizStepStaff(){head(wizBody,"Select Specialist / Stylist");label(wizBody,"Choose your preferred expert or let the salon assign any available expert.");
  var anyC=cardIn(wizBody);anyC.setBackground(wizStaff==null?strokeBg(CORAL_L,CORAL,10):strokeBg("#FFFFFF",BORDER,10));var ar=hrow(anyC);var arb=new RadioButton(this);arb.setChecked(wizStaff==null);arb.setEnabled(false);arb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(CORAL)));ar.addView(arb);
  var ainfo=col(ar,1);tv(ainfo,"Any Available Specialist (Fastest)",14,true,INK);tv(ainfo,"Salon will assign the next available senior professional.",12,false,MUTED);
  anyC.setOnClickListener(v->{wizStaff=null;wizSlot=null;wizSlotsKey="";renderWizStep();});
  var staff=wizDetail.optJSONArray("staff");if(staff==null)return;
  for(int i=0;i<staff.length();i++){var s=staff.optJSONObject(i);if(s==null)continue;final long sid=s.optLong("staff_id");boolean sel=wizStaff!=null&&wizStaff==sid;
   var c=cardIn(wizBody);c.setBackground(sel?strokeBg(CORAL_L,CORAL,10):strokeBg("#FFFFFF",BORDER,10));var r=hrow(c);
   var rb=new RadioButton(this);rb.setChecked(sel);rb.setEnabled(false);rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(CORAL)));r.addView(rb);
   var info=col(r,1);tv(info,s.optString("name"),14,true,INK);tv(info,s.optString("designation")+" • "+s.optInt("experience")+"y exp",11,false,MUTED);
   tv(info,stars(s.optDouble("rating"))+" "+String.format(Locale.US,"%.1f",s.optDouble("rating")),10,false,MUTED);
   boolean active="ACTIVE".equals(s.optString("status"));var bd=new TextView(this);bd.setText(active?"Available":"Busy");bd.setTextSize(10);bd.setTypeface(Typeface.DEFAULT_BOLD);bd.setTextColor(Color.parseColor(active?GREEN:RED));bd.setBackground(roundBg(active?GREEN_L:RED_L,4));bd.setPadding(dp(6),dp(2),dp(6),dp(2));var br=new LinearLayout(this);br.addView(bd);info.addView(br);
   c.setOnClickListener(v->{wizStaff=sid;wizSlot=null;wizSlotsKey="";renderWizStep();});}}
 String wizSlotsKey="";JSONArray wizSlotsData,wizBookedData;
 void wizStepSlots(){head(wizBody,"Select Appointment Date & Time");label(wizBody,"Slots account for your total service duration ("+wizMinutes()+" minutes).");
  tv(wizBody,"Available Dates",13,true,INK);
  var dscroll=new android.widget.HorizontalScrollView(this);dscroll.setHorizontalScrollBarEnabled(false);wizBody.addView(dscroll);var drow=new LinearLayout(this);drow.setOrientation(LinearLayout.HORIZONTAL);dscroll.addView(drow);
  var today=LocalDate.now(zone);for(int i=0;i<7;i++){final LocalDate day=today.plusDays(i);boolean sel=day.equals(wizDate);
   var cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);cell.setPadding(dp(8),dp(8),dp(8),dp(8));cell.setBackground(sel?roundBg(CORAL,10):strokeBg("#FFFFFF",BORDER,10));var clp=new LinearLayout.LayoutParams(dp(58),ViewGroup.LayoutParams.WRAP_CONTENT);clp.setMargins(dp(3),dp(3),dp(3),dp(3));cell.setLayoutParams(clp);
   var w=new TextView(this);w.setText(day.format(DateTimeFormatter.ofPattern("EEE",Locale.ENGLISH)));w.setTextSize(11);w.setGravity(Gravity.CENTER);w.setTextColor(Color.parseColor(sel?"#FFFFFF":MUTED));cell.addView(w);
   var dd=new TextView(this);dd.setText(day.format(DateTimeFormatter.ofPattern("d MMM",Locale.ENGLISH)));dd.setTextSize(13);dd.setTypeface(Typeface.DEFAULT_BOLD);dd.setGravity(Gravity.CENTER);dd.setTextColor(Color.parseColor(sel?"#FFFFFF":INK));cell.addView(dd);
   cell.setOnClickListener(v->{wizDate=day;wizSlot=null;renderWizStep();});drow.addView(cell);}
  outline(wizBody,"Pick another date",CORAL,v->{var now=LocalDate.now(zone);new DatePickerDialog(this,(view,y,m,d)->{wizDate=LocalDate.of(y,m+1,d);wizSlot=null;renderWizStep();},wizDate.getYear(),wizDate.getMonthValue()-1,wizDate.getDayOfMonth()).show();});
  tv(wizBody,"Available Time Slots for "+wizDate,13,true,INK);
  wizSlotsBox=new LinearLayout(this);wizSlotsBox.setOrientation(LinearLayout.VERTICAL);wizBody.addView(wizSlotsBox);
  String key=wizDate+"|"+servicesCsv(wizServices)+"|"+wizStaff;
  if(key.equals(wizSlotsKey)&&wizSlotsData!=null){drawWizSlots();return;}
  wizSlotsKey=key;tv(wizSlotsBox,"Loading available slots... this can take a minute.",13,false,MUTED);
  fetchSlotsFull(wizPid,servicesCsv(wizServices),wizStaff,wizDate,(a,bk)->{wizSlotsData=a;wizBookedData=bk;if(wizSlotsBox!=null)drawWizSlots();});}
 void drawWizSlots(){if(wizSlotsBox!=null&&wizSlotsData!=null)slotGridBooked(wizSlotsBox,wizSlotsData,wizBookedData,wizSlot,s->{wizSlot=s;drawWizSlots();});}
 EditText wizName,wizPhone;String wizNameText="",wizPhoneText="";
 void wizStepPayment(){var p=wizDetail.optJSONObject("parlour");var policy=firstPolicy(wizDetail);
  wizName=field(wizBody,"Customer Full Name *",wizNameText,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
  wizPhone=field(wizBody,"Phone Number (+880) *",wizPhoneText,InputType.TYPE_CLASS_PHONE);
  field(wizBody,"Email Address (Optional)","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
  field(wizBody,"Special Requests / Allergy Notes (Optional)","",InputType.TYPE_CLASS_TEXT);
  var sum=cardIn(wizBody);tv(sum,"Booking Summary",14,true,PLUM);
  tv(sum,"📍 Salon: "+p.optString("name"),12,false,INK);
  tv(sum,"📅 Date: "+wizDate+" at "+(wizSlot==null?"":fmtTime(wizSlot.optString("start_at"))),12,false,INK);
  tv(sum,"👤 Stylist: "+(wizStaff==null?"Any Available Expert":staffName(wizDetail,wizStaff)),12,false,INK);
  var names=new StringBuilder();var services=wizDetail.optJSONArray("services");if(services!=null)for(int i=0;i<services.length();i++){var s=services.optJSONObject(i);if(s!=null&&wizServices.contains(s.optLong("service_id"))){if(names.length()>0)names.append(", ");names.append(s.optString("name"));}}
  tv(sum,"✂ Services: "+names,12,false,INK);tv(sum,"⏱ Duration: "+wizMinutes()+" minutes",12,false,INK);
  divider(sum);double total=wizTotal();boolean manualPay="BKASH".equals(wizMethod)||"NAGAD".equals(wizMethod)||"BANK".equals(wizMethod);double advance="SPLIT_50_50".equals(wizMethod)?Math.round(total*50.0)/100.0:"ONLINE".equals(wizMethod)?total:manualPay?(policy!=null&&policy.optBoolean("advance_required")?Math.round(total*policy.optInt("min_advance_pct"))/100.0:total):0;
  kv(sum,"Subtotal:",money(total));kv(sum,"SPLIT_50_50".equals(wizMethod)?"Advance Payable Online (50%):":"ONLINE".equals(wizMethod)?"Payable Online:":manualPay?"Payable via "+wizMethod+":":"Advance Required:",money(advance),MUTED,CORAL,true);
  kv(sum,"Balance Payable at Salon:",money(total-advance),MUTED,GREEN,true);
  tv(wizBody,"Choose Payment Method",14,true,INK);
  boolean cashBlocked=policy!=null&&policy.optBoolean("advance_required")&&policy.optInt("min_advance_pct")>=100;
  payOption("BKASH","bKash","bKash","Send money to the parlour's bKash number, then submit the TrxID","BKASH",false,sum);
  payOption("NAGAD","Nagad","Nagad","Send money to the parlour's Nagad number, then submit the TrxID","NAGAD",false,sum);
  payOption("BANK","Bank","Bank Transfer","Transfer to the parlour's bank account, then submit the reference","BANK",false,sum);
  payOption("CARD","Card","Visa / Mastercard / Amex","Credit or debit cards through SSLCommerz","ONLINE",false,sum);
  payOption("CASH","Cash","Pay at Salon Counter",cashBlocked?"":"No online advance; pay full bill in cash/card on arrival","AT_PARLOUR",cashBlocked,sum);
  updateWizBottom();}
 void payOption(String choice,String shortName,String title,String desc,String apiMethod,boolean blocked,LinearLayout sumCard){boolean sel=choice.equals(wizPayChoice);
  var c=cardIn(wizBody);if(blocked){c.setBackground(strokeBg("#FFF9F9",BORDER,8));}else c.setBackground(sel?strokeBg(CORAL_L,CORAL,8):strokeBg("#FFFFFF",BORDER,8));
  var r=hrow(c);var rb=new RadioButton(this);rb.setChecked(sel);rb.setEnabled(false);rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(CORAL)));r.addView(rb);
  var info=col(r,1);tv(info,title,13,true,blocked?MUTED:INK);tv(info,blocked?"Not allowed by this parlour's policy":desc,11,false,blocked?RED:MUTED);
  if(!blocked)c.setOnClickListener(v->{if(wizName!=null)wizNameText=value(wizName);if(wizPhone!=null)wizPhoneText=value(wizPhone);wizPayChoice=choice;wizMethod=apiMethod;renderWizStep();});}
 void submitBooking(){String name=value(wizName),phone=value(wizPhone);if(name.isEmpty()||phone.isEmpty()){toast("Please enter customer name and phone number.");return;}
  try{var body=new JSONObject().put("parlour_id",wizPid).put("services",new JSONArray(wizServices.stream().mapToLong(Long::longValue).toArray())).put("start_at",wizSlot.optString("start_at")).put("customer_name",name).put("customer_phone",phone).put("payment_method",wizMethod);
   if(wizStaff!=null)body.put("staff_id",wizStaff);
   post("/api/bookings",body,wizKey,v->confirmation(obj(v)));}catch(Exception e){message(e.getMessage());}}
 // ---------- customer: confirmation + receipt ----------
 String bookingParlourName(JSONObject b){long pid=b.optLong("parlour_id");if(wizDetail!=null&&wizDetail.optJSONObject("parlour")!=null&&wizDetail.optJSONObject("parlour").optLong("parlour_id")==pid)return wizDetail.optJSONObject("parlour").optString("name");var info=parlourInfo.get(pid);return info!=null?info[0]:"Parlour #"+pid;}
 void confirmation(JSONObject b){chrome("bookings");hideTabs();content.removeAllViews();
  var center=new LinearLayout(this);center.setOrientation(LinearLayout.VERTICAL);center.setGravity(Gravity.CENTER_HORIZONTAL);content.addView(center);
  var check=new TextView(this);check.setText("✓");check.setTextSize(30);check.setTypeface(Typeface.DEFAULT_BOLD);check.setTextColor(Color.WHITE);check.setGravity(Gravity.CENTER);check.setBackground(roundBg(GREEN,27));check.setLayoutParams(new LinearLayout.LayoutParams(dp(54),dp(54)));center.addView(check);
  var t=tv(center,"Appointment Confirmed!",22,true,INK);t.setGravity(Gravity.CENTER);
  var s=tv(center,"Your booking code has been generated and notified to "+bookingParlourName(b)+".",13,false,MUTED);s.setGravity(Gravity.CENTER);
  var codeBox=new LinearLayout(this);codeBox.setOrientation(LinearLayout.VERTICAL);codeBox.setGravity(Gravity.CENTER_HORIZONTAL);codeBox.setBackground(roundBg(BEIGE,10));codeBox.setPadding(dp(16),dp(12),dp(16),dp(12));var clp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);clp.setMargins(0,dp(12),0,dp(12));codeBox.setLayoutParams(clp);center.addView(codeBox);
  var cl=tv(codeBox,"BOOKING REFERENCE CODE",10,true,MUTED);cl.setGravity(Gravity.CENTER);var cv=tv(codeBox,"#"+b.optLong("booking_id"),22,true,CORAL);cv.setGravity(Gravity.CENTER);cv.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
  var brows=hrow(codeBox);brows.setGravity(Gravity.CENTER);badge(brows,b.optString("status"));payBadge(brows,b.optString("payment_method"));
  var card=card();tv(card,"Appointment Details",14,true,PLUM);
  tv(card,"🏬 "+bookingParlourName(b),13,false,INK);
  tv(card,"📅 "+fmtDate(b.optString("start_at"))+" at "+fmtTime(b.optString("start_at")),13,false,INK);
  tv(card,"👤 Stylist: "+staffName(wizDetail,b.optLong("staff_id")),13,false,INK);
  var names=new StringBuilder();var items=b.optJSONArray("items");if(items!=null)for(int i=0;i<items.length();i++){var it=items.optJSONObject(i);if(it!=null){if(names.length()>0)names.append(", ");names.append(it.optString("name"));}}
  tv(card,"✂ "+names,13,false,INK);divider(card);
  kv(card,"Total Price:",money(b.optDouble("total_amount")),MUTED,INK,true);
  kv(card,"Paid Amount:",money(b.optDouble("paid_amount")),MUTED,GREEN,true);
  kv(card,"Balance Due at Counter:",money(b.optDouble("due_amount")),MUTED,b.optDouble("due_amount")>0?AMBER:GREEN,true);
  if(Set.of("CONFIRMED","PENDING_OWNER","PENDING_PAYMENT").contains(b.optString("status")))payNowSection(content,b);
  outline(content,"View Digital Pass & QR Code",CORAL,v->receiptDialog(b));
  outline(content,"Add to Device Calendar",CORAL,v->toast("Event exported to Google Calendar & reminder synced."));
  btn(content,"View My Bookings",CORAL,v->myBookings());
  var bh=tv(content,"Back to Home",13,false,MUTED);bh.setGravity(Gravity.CENTER);bh.setPadding(0,dp(10),0,dp(4));bh.setOnClickListener(v->home());}
 void payNowSection(LinearLayout parent,JSONObject b){String m=b.optString("payment_method");if(!Set.of("BKASH","NAGAD","BANK").contains(m))return;
  var pays=b.optJSONArray("payments");double reported=0;if(pays!=null)for(int i=0;i<pays.length();i++){var py=pays.optJSONObject(i);if(py!=null&&Set.of("PAID","REPORTED").contains(py.optString("status")))reported+=py.optDouble("amount");}
  double need=b.optDouble("advance_amount");if(need<=0)need=b.optDouble("total_amount");if(reported>=need)return;
  long pid=b.optLong("parlour_id");long bid=b.optLong("booking_id");double amt=need-reported;
  var c=cardIn(parent);tv(c,"💳 Pay with "+m,14,true,PLUM);tv(c,"Loading the parlour's "+m+" account...",12,false,MUTED);
  get("/api/public/parlours/"+pid+"/payment-methods",v->{var a=arr(v);c.removeAllViews();tv(c,"💳 Pay with "+m,14,true,PLUM);
   JSONObject found=null;for(int i=0;i<a.length();i++){var pm=a.optJSONObject(i);if(pm!=null&&m.equals(pm.optString("method")))found=pm;}
   if(found==null){tv(c,"The parlour has not added a "+m+" account yet. Please pay at the parlour or contact them.",12,false,RED);return;}
   tv(c,"Send "+money(amt)+" to this "+m+" account:",12,false,INK);
   tv(c,found.optString("account_name"),13,true,INK);tv(c,found.optString("account_number"),16,true,CORAL);
   if("BANK".equals(m)&&found.optString("bank_name").length()>0)tv(c,found.optString("bank_name")+" • "+found.optString("branch"),12,false,MUTED);
   tv(c,"After sending, enter the Transaction ID (TrxID) from your confirmation SMS:",12,false,MUTED);
   var trx=field(c,"Transaction ID (TrxID)","",InputType.TYPE_CLASS_TEXT);
   btn(c,"Submit Payment ("+money(amt)+")",CORAL,x->{String t=value(trx);if(t.isEmpty()){toast("Enter the transaction ID.");return;}try{post("/api/bookings/"+bid+"/report-payment",new JSONObject().put("method",m).put("transaction_id",t).put("amount",amt),y->{toast("Payment reported. The parlour will confirm receipt.");booking(bid);});}catch(Exception e){message(e.getMessage());}});});}
 void receiptDialog(JSONObject b){var body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
  var qr=new LinearLayout(this);qr.setOrientation(LinearLayout.VERTICAL);qr.setGravity(Gravity.CENTER);qr.setBackground(roundBg("#FFFFFF",12));qr.setPadding(dp(8),dp(8),dp(8),dp(8));var qlp=new LinearLayout.LayoutParams(dp(130),dp(130));qlp.gravity=Gravity.CENTER_HORIZONTAL;qr.setLayoutParams(qlp);body.addView(qr);
  var qi=new TextView(this);qi.setText("▦");qi.setTextSize(64);qi.setTextColor(Color.parseColor(INK));qi.setGravity(Gravity.CENTER);qr.addView(qi);
  var qt=glassText(body,"QR-PARLOUR-"+b.optLong("booking_id"),9,false,GLASS_MUT);qt.setGravity(Gravity.CENTER);qt.setTypeface(Typeface.MONOSPACE);
  spacer(body,8);
  glassKv(body,"Booking ID","#"+b.optLong("booking_id"),"#FFFFFF",true);
  glassKv(body,"Parlour",bookingParlourName(b),"#FFFFFF",false);
  var info=parlourInfo.get(b.optLong("parlour_id"));glassKv(body,"Area",info!=null?info[1]:"","#D8D2E8",false);
  glassKv(body,"Date & Slot",fmtDate(b.optString("start_at"))+", "+fmtTime(b.optString("start_at")),SLOTY,false);
  glassKv(body,"Assigned Expert",staffName(wizDetail,b.optLong("staff_id")),"#FFFFFF",false);
  var names=new StringBuilder();var items=b.optJSONArray("items");if(items!=null)for(int i=0;i<items.length();i++){var it=items.optJSONObject(i);if(it!=null){if(names.length()>0)names.append(", ");names.append(it.optString("name"));}}
  glassKv(body,"Services",names.toString(),"#D8D2E8",false);
  var dv=new View(this);dv.setBackgroundColor(Color.parseColor("#33FFFFFF"));var dlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(1));dlp.setMargins(0,dp(6),0,dp(6));dv.setLayoutParams(dlp);body.addView(dv);
  glassKv(body,"Total Service Price",money(b.optDouble("total_amount")),"#FFFFFF",true);
  glassKv(body,"Advance Paid",money(b.optDouble("paid_amount")),MINT,false);
  glassKv(body,"Balance Due at Salon",money(b.optDouble("due_amount")),b.optDouble("due_amount")>0?SLOTY:MINT,false);
  glassKv(body,"Payment Mode",b.optString("payment_method")+" ("+b.optString("status")+")","#D8D2E8",false);
  glass("Digital Booking Receipt",body,"Close Receipt",CORAL,null);}
 // ---------- customer: my bookings, detail, dialogs ----------
 void loadParlourMap(Runnable done){if(!parlourInfo.isEmpty()){done.run();return;}get("/api/public/parlours?limit=100",v->{var a=arr(v);for(int i=0;i<a.length();i++){var p=a.optJSONObject(i);if(p!=null)parlourInfo.put(p.optLong("parlour_id"),new String[]{p.optString("name"),p.optString("address")});}done.run();});}
 void myBookings(){chrome("bookings");hideTabs();content.removeAllViews();tv(content,"Loading your bookings...",13,false,MUTED);
  String path="OWNER".equals(vm.repository.role())?"/api/owner/bookings":"ADMIN".equals(vm.repository.role())?"/api/admin/bookings":"/api/bookings";
  loadParlourMap(()->get(path,v->{lastBookings=arr(v);renderBookings();}));}
 boolean filterMatch(String status){return switch(bookingFilter){case "UPCOMING"->Set.of("CONFIRMED","PENDING_OWNER","PENDING_PAYMENT","RESCHEDULED","CANCEL_PENDING").contains(status);case "COMPLETED"->"COMPLETED".equals(status);case "CANCELLED"->Set.of("CANCELLED","REJECTED").contains(status);default->true;};}
 void renderBookings(){content.removeAllViews();int n=lastBookings==null?0:lastBookings.length();
  head(content,"My Bookings ("+n+")");
  var frow=hrow(content);String[][] fs={{"ALL","All"},{"UPCOMING","Upcoming"},{"COMPLETED","Completed"},{"CANCELLED","Cancelled"}};for(String[] f:fs)pill(frow,f[1],bookingFilter.equals(f[0]),v->{bookingFilter=f[0];renderBookings();});
  var shown=new ArrayList<JSONObject>();if(lastBookings!=null)for(int i=0;i<lastBookings.length();i++){var b=lastBookings.optJSONObject(i);if(b!=null&&filterMatch(b.optString("status")))shown.add(b);}
  if(shown.isEmpty()){emptyState(content,"📅","No bookings in this category","Find a parlour and book your next beauty or grooming session.","Browse Parlours",()->home());return;}
  for(var b:shown)bookingCard(b);}
 void bookingCard(JSONObject b){long bid=b.optLong("booking_id");long pid=b.optLong("parlour_id");var info=parlourInfo.get(pid);var c=card();
  var hr=hrow(c);var nm=tv(hr,info!=null?info[0]:"Parlour #"+pid,15,true,INK);nm.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));badge(hr,b.optString("status"));payBadge(hr,b.optString("payment_method"));
  tv(c,"Ref: "+bid+(info!=null?" • "+info[1]:""),11,false,MUTED);
  tv(c,"📅 "+fmtDate(b.optString("start_at"))+", "+fmtTime(b.optString("start_at")),12,false,INK);
  var items=b.optJSONArray("items");if(items!=null&&items.length()>0){var names=new StringBuilder();for(int i=0;i<items.length();i++){var it=items.optJSONObject(i);if(it!=null){if(names.length()>0)names.append(", ");names.append(it.optString("name"));}}tv(c,"Services: "+names,12,false,INK);}
  var prow=hrow(c);var totw=tv(prow,"Total: "+money(b.optDouble("total_amount")),12,true,CORAL);totw.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));if(b.optDouble("advance_amount")>0){var pdt=tv(prow,"Advance "+money(b.optDouble("advance_amount")),11,false,MUTED);pdt.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));}
  var arow=hrow(c);String st=b.optString("status");
  outlineW(arow,"Receipt",INK,1,v->get("/api/bookings/"+bid,x->receiptDialog(obj(x))));
  if("CONFIRMED".equals(st)||"RESCHEDULED".equals(st)){outlineW(arow,"Reschedule",INK,1.1f,v->rescheduleScreen(b));outlineW(arow,"Cancel",RED,0.9f,v->cancelDialog(b));}
  if("COMPLETED".equals(st))btnSW(arow,"Rate & Review",CORAL,1.2f,v->review(bid));
  c.setOnClickListener(v->booking(bid));}
 void booking(long bid){chrome("");hideTabs();content.removeAllViews();tv(content,"Loading booking...",13,false,MUTED);
  get("/api/bookings/"+bid,v->{var b=obj(v);content.removeAllViews();screenBar("Booking #"+bid,()->myBookings(),null,null);
   var hr=hrow(content);badge(hr,b.optString("status"));payBadge(hr,b.optString("payment_method"));
   var c=card();long pid=b.optLong("parlour_id");var info=parlourInfo.get(pid);
   kv(c,"Parlour",info!=null?info[0]:"Parlour #"+pid);kv(c,"Customer",b.optString("customer_name"));kv(c,"Phone",b.optString("customer_phone"));
   kv(c,"When",fmtDT(b.optString("start_at")));kv(c,"Stylist",staffName(detailCache.get(pid),b.optLong("staff_id")));
   var items=b.optJSONArray("items");if(items!=null)for(int i=0;i<items.length();i++){var it=items.optJSONObject(i);if(it!=null)kv(c,it.optString("name"),money(it.optDouble("price")));}
   divider(c);kv(c,"Total",money(b.optDouble("total_amount")),MUTED,INK,true);kv(c,"Advance",money(b.optDouble("advance_amount")));kv(c,"Cash on service",money(b.optDouble("cash_on_service_amount")));kv(c,"Paid",money(b.optDouble("paid_amount")));kv(c,"Due",money(b.optDouble("due_amount")),MUTED,CORAL,true);
   String st=b.optString("status");String role=vm.repository.role();
   if("OWNER".equals(role)){
    if("PENDING_OWNER".equals(st)){btn(content,"Accept booking",GREEN,x->ownerStatus(bid,"CONFIRMED"));btn(content,"Reject booking",RED,x->ownerStatus(bid,"REJECTED"));}
    if("CONFIRMED".equals(st)){btn(content,"Mark completed",BLUE,x->ownerStatus(bid,"COMPLETED"));outline(content,"Record cash received",GREEN,x->post("/api/owner/bookings/"+bid+"/cash-payment",new JSONObject(),y->booking(bid)));}
    if("CANCEL_PENDING".equals(st))btn(content,"Approve cancellation",RED,x->ownerStatus(bid,"CANCELLED"));
   } else {
    if("COMPLETED".equals(st))btn(content,"Write a review",CORAL,x->review(bid));
    if(Set.of("CONFIRMED","PENDING_OWNER","PENDING_PAYMENT").contains(st))payNowSection(content,b);
    outline(content,"View Digital Pass & QR Code",CORAL,x->receiptDialog(b));
    outline(content,"Report an issue",PLUM,x->complaint(bid));
    outline(content,"Share receipt",INK,x->{var text="Parlour booking #"+bid+"\n"+(info!=null?info[0]:"")+"\n"+fmtDT(b.optString("start_at"))+"\nTotal "+money(b.optDouble("total_amount"))+" - Paid "+money(b.optDouble("paid_amount"))+" - Due "+money(b.optDouble("due_amount"));var i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(i,"Share receipt"));});
    if("CONFIRMED".equals(st)||"PENDING_OWNER".equals(st)){outline(content,"Reschedule",INK,x->rescheduleScreen(b));btn(content,"Cancel booking",RED,x->cancelDialog(b));}
   }});}
 void ownerStatus(long bid,String status){try{post("/api/owner/bookings/"+bid+"/status",new JSONObject().put("status",status),v->{var r=obj(v);if("COMPLETED".equals(status)&&r.optBoolean("removed")){new AlertDialog.Builder(this).setTitle("Completed").setMessage("Service completed - booking record removed.").setPositiveButton("OK",(d,w)->myBookings()).show();}else booking(bid);});}catch(Exception e){message(e.getMessage());}}
 void cancelDialog(JSONObject b){long bid=b.optLong("booking_id");
  get("/api/bookings/"+bid,v->{var full=obj(v);JSONObject snap=null;try{snap=new JSONObject(full.optString("policy_snapshot","{}"));}catch(Exception ignored){}
   int fee=snap==null?0:snap.optInt("fee_pct");int refund=snap==null?100:snap.optInt("refund_pct");int window=snap==null?24:snap.optInt("cancel_window_hr");double advance=full.optDouble("advance_amount");double eligible=advance*refund/100.0;
   var body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
   glassText(body,"Are you sure you want to cancel booking #"+bid+" at "+bookingParlourName(full)+"?",14,false,GLASS_SUB);
   var box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setBackground(strokeBg("#22FFFFFF","#33FFFFFF",12));box.setPadding(dp(12),dp(10),dp(12),dp(10));var blp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);blp.setMargins(0,dp(12),0,dp(4));box.setLayoutParams(blp);body.addView(box);
   glassText(box,"Salon Cancellation Policy",13,true,ROSE);
   glassText(box,"• Notice window: "+window+"h before scheduled time",12,false,GLASS_MUT);
   glassText(box,"• Cancellation fee: "+fee+"%",12,false,GLASS_MUT);
   glassText(box,"• Eligible Refund: "+refund+"% of advance ("+money(eligible)+")",12,true,MINT);
   glassField(body,"Reason for cancellation","Change of plans");
   glass("Cancel Appointment?",body,"Confirm Cancel",RED,()->post("/api/bookings/"+bid+"/cancel",new JSONObject(),x->{toast("Booking cancelled.");myBookings();}));});}
 LocalDate reDate;JSONArray reSlots;JSONObject reSlot;LinearLayout reSlotsBox;
 void rescheduleScreen(JSONObject b){long bid=b.optLong("booking_id");long pid=b.optLong("parlour_id");
  var serviceIds=new LinkedHashSet<Long>();var items=b.optJSONArray("items");if(items!=null)for(int i=0;i<items.length();i++){var it=items.optJSONObject(i);if(it!=null)serviceIds.add(it.optLong("service_id"));}
  Long staff=b.optLong("staff_id")>0?b.optLong("staff_id"):null;
  chrome("bookings");hideTabs();content.removeAllViews();screenBar("Reschedule Appointment",()->myBookings(),null,null);
  tv(content,"Select a new date and time for booking #"+bid+" at "+bookingParlourName(b)+".",13,false,MUTED);
  reDate=LocalDate.now(zone).plusDays(1);reSlot=null;
  var dscroll=new android.widget.HorizontalScrollView(this);dscroll.setHorizontalScrollBarEnabled(false);content.addView(dscroll);var drow=new LinearLayout(this);drow.setOrientation(LinearLayout.HORIZONTAL);dscroll.addView(drow);
  reSlotsBox=new LinearLayout(this);reSlotsBox.setOrientation(LinearLayout.VERTICAL);content.addView(reSlotsBox);
  Runnable load=()->{tv(reSlotsBox,"Loading available slots... this can take a minute.",13,false,MUTED);fetchSlotsFull(pid,servicesCsv(serviceIds),staff,reDate,(a,bk)->{reSlots=a;slotGridBooked(reSlotsBox,a,bk,reSlot,s->{reSlot=s;confirm("Move this booking to "+fmtDT(s.optString("start_at"))+"?",()->{try{post("/api/bookings/"+bid+"/reschedule",new JSONObject().put("start_at",s.optString("start_at")),x->{toast("Booking rescheduled.");myBookings();});}catch(Exception e){message(e.getMessage());}});});});};
  var today=LocalDate.now(zone);for(int i=0;i<7;i++){final LocalDate day=today.plusDays(i);
   var cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);cell.setPadding(dp(8),dp(8),dp(8),dp(8));cell.setBackground(day.equals(reDate)?roundBg(CORAL,10):strokeBg("#FFFFFF",BORDER,10));var clp=new LinearLayout.LayoutParams(dp(58),ViewGroup.LayoutParams.WRAP_CONTENT);clp.setMargins(dp(3),dp(3),dp(3),dp(3));cell.setLayoutParams(clp);
   var w=new TextView(this);w.setText(day.format(DateTimeFormatter.ofPattern("EEE",Locale.ENGLISH)));w.setTextSize(11);w.setGravity(Gravity.CENTER);w.setTextColor(Color.parseColor(day.equals(reDate)?"#FFFFFF":MUTED));cell.addView(w);
   var dd=new TextView(this);dd.setText(day.format(DateTimeFormatter.ofPattern("d MMM",Locale.ENGLISH)));dd.setTextSize(13);dd.setTypeface(Typeface.DEFAULT_BOLD);dd.setGravity(Gravity.CENTER);dd.setTextColor(Color.parseColor(day.equals(reDate)?"#FFFFFF":INK));cell.addView(dd);
   cell.setOnClickListener(v->{reDate=day;reSlot=null;rescheduleScreen(b);});drow.addView(cell);}
  load.run();}
 void review(long bid){chrome("bookings");hideTabs();content.removeAllViews();screenBar("Rate Your Experience",()->myBookings(),null,null);
  var c=card();final int[] pr={5},sr={5};
  tv(c,"Parlour",14,true,INK);starsRow(c,pr);tv(c,"Stylist",14,true,INK);starsRow(c,sr);
  var feedback=field(c,"Share your feedback about the hygiene, behavior, and styling...","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
  btn(c,"Submit Review",CORAL,v->{try{post("/api/ratings",new JSONObject().put("booking_id",bid).put("target_type","PARLOUR").put("score",pr[0]).put("review",value(feedback)),x->{try{var o=new JSONObject().put("booking_id",bid).put("target_type","STAFF").put("score",sr[0]).put("review",value(feedback));vm.repository.request("POST","/api/ratings",o,null,(a,e)->{});}catch(Exception ignored){}toast("Thanks for your review.");myBookings();});}catch(Exception e){message(e.getMessage());}});}
 void starsRow(LinearLayout parent,int[] state){var row=hrow(parent);TextView[] stars=new TextView[5];
  Runnable paint=()->{for(int i=0;i<5;i++){stars[i].setText(i<state[0]?"★":"☆");stars[i].setTextColor(Color.parseColor(i<state[0]?GOLD:"#CCCCCC"));}};
  for(int i=0;i<5;i++){final int idx=i;var t=new TextView(this);t.setTextSize(30);t.setPadding(dp(2),0,dp(2),0);t.setOnClickListener(v->{state[0]=idx+1;paint.run();});row.addView(t);stars[i]=t;}
  paint.run();}
 void complaint(long bid){chrome("bookings");hideTabs();content.removeAllViews();screenBar("Report an issue",()->booking(bid),null,null);
  var subject=field(content,"Subject","",InputType.TYPE_CLASS_TEXT);var details=field(content,"What happened?","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
  btn(content,"Send complaint",CORAL,v->{try{post("/api/complaints",new JSONObject().put("booking_id",bid).put("subject",value(subject)).put("message",value(details)).put("details",value(details)),x->{toast("Complaint sent. Our team will review it.");booking(bid);});}catch(Exception e){message(e.getMessage());}});}
 // ---------- customer: notifications ----------
 void inbox(){chrome("");hideTabs();content.removeAllViews();
  var hr=hrow(content);var t=tv(hr,"Notifications & Alerts",16,true,INK);t.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
  var mark=new TextView(this);mark.setText("Mark all read");mark.setTextSize(12);mark.setTypeface(Typeface.DEFAULT_BOLD);mark.setTextColor(Color.parseColor(CORAL));hr.addView(mark);
  var box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);content.addView(box);
  get("/api/notifications",v->{var a=arr(v);box.removeAllViews();
   mark.setOnClickListener(x->{for(int i=0;i<a.length();i++){var n=a.optJSONObject(i);if(n!=null)readIds.add(n.optLong("notification_id"));}persistUi();setBell(0);inbox();});
   if(a.length()==0){emptyState(box,"🔔","No notifications yet","You will receive updates here about your bookings, payment receipts, and salon offers.",null,null);return;}
   for(int i=0;i<a.length();i++){var n=a.optJSONObject(i);if(n==null)continue;long nid=n.optLong("notification_id");boolean unread=!readIds.contains(nid);
    var c=cardIn(box);c.setBackground(unread?strokeBg(CORAL_L,BORDER,10):strokeBg("#FFFFFF",BORDER,10));var r=hrow(c);
    String type=n.optString("type");String glyph=type.contains("BOOKING")?"📅":type.contains("PAYMENT")?"🧾":type.contains("PROMO")?"🏷":"🔔";
    var circ=new TextView(this);circ.setText(glyph);circ.setTextSize(16);circ.setGravity(Gravity.CENTER);circ.setBackground(roundBg(unread?CORAL:BEIGE,18));circ.setLayoutParams(new LinearLayout.LayoutParams(dp(36),dp(36)));r.addView(circ);
    var info=col(r,1);info.setPadding(dp(10),0,0,0);var tr=hrow(info);var tt=tv(tr,n.optString("title","Update"),14,unread,INK);tt.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));if(unread){var dot=new TextView(this);dot.setBackground(roundBg(CORAL,4));dot.setLayoutParams(new LinearLayout.LayoutParams(dp(8),dp(8)));tr.addView(dot);}
    tv(info,n.optString("created_at"),10,false,MUTED);tv(info,n.optString("message"),12,false,MUTED);
    final long bookingId=n.optLong("booking_id");final String typeF=type;
    c.setOnClickListener(x->{readIds.add(nid);persistUi();refreshBell();if("COMPLETED".equals(typeF)&&bookingId>0)review(bookingId);else if(bookingId>0)booking(bookingId);else inbox();});}
   outline(box,"Enable push notifications",CORAL,x->{FirebaseSession.sync(this,vm.repository);toast("Push notifications are on for this device.");});});}
 // ---------- customer: compare ----------
 void compare(){chrome("compare");hideTabs();content.removeAllViews();
  screenBar("Compare Parlours ("+compareIds.size()+")",()->home(),compareIds.isEmpty()?null:"Clear All",()->{compareIds.clear();chrome("compare");compare();});
  if(compareIds.isEmpty()){emptyState(content,"⇄","No parlours selected to compare","Tap '+ Compare' on any parlour card on the Home or Explore screen to compare features, prices, and policies side-by-side.","Browse Parlours",()->home());return;}
  tv(content,"Loading comparison...",13,false,MUTED);
  var ids=new ArrayList<>(compareIds);var detailsList=new ArrayList<JSONObject>();var counter=new int[]{0};
  for(long pid:ids)fetchDetail(pid,d->{synchronized(counter){detailsList.add(d);if(++counter[0]==ids.size())renderCompare(detailsList);}});}
 void renderCompare(List<JSONObject> ds){content.removeAllViews();screenBar("Compare Parlours ("+ds.size()+")",()->home(),"Clear All",()->{compareIds.clear();compare();});
  var table=new LinearLayout(this);table.setOrientation(LinearLayout.VERTICAL);table.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));content.addView(table);
  var header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);table.addView(header);
  var ml=new TextView(this);ml.setText("METRICS");ml.setTextSize(11);ml.setTypeface(Typeface.DEFAULT_BOLD);ml.setTextColor(Color.parseColor(MUTED));ml.setLayoutParams(new LinearLayout.LayoutParams(dp(84),ViewGroup.LayoutParams.WRAP_CONTENT));header.addView(ml);
  for(var d:ds){var p=d.optJSONObject("parlour");if(p==null)continue;long pid=p.optLong("parlour_id");var cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setBackground(strokeBg("#FFFFFF",BORDER,10));cell.setPadding(dp(6),dp(6),dp(6),dp(6));var clp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);clp.setMargins(dp(2),dp(2),dp(2),dp(2));cell.setLayoutParams(clp);header.addView(cell);
   var nr=hrow(cell);var nm=tv(nr,p.optString("name"),11,true,INK);nm.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));var x=new TextView(this);x.setText("✕");x.setTextSize(13);x.setTextColor(Color.parseColor(MUTED));x.setPadding(dp(6),dp(2),dp(2),dp(2));x.setOnClickListener(v->{compareIds.remove(pid);compare();});nr.addView(x);
   tv(cell,p.optString("address")+" • "+p.optString("category"),9,false,MUTED);
   btnW(cell,"Book Now",CORAL,1,v->wizardStart(pid,0));}
  String[][] rows=new String[12][];
  var metrics=new String[]{"Rating","Distance","Starting Price","Opening Hours","Open Status","Min Advance","Pay at Parlour","Cancel Window","Cancellation Fee","Refund Policy","Total Stylists","Active Services"};
  var values=new ArrayList<String[]>();
  for(var d:ds){var p=d.optJSONObject("parlour");var policy=firstPolicy(d);var th=todayHours(d);
   String dist="—";if(p!=null&&p.has("lat")&&p.has("lng")&&!Double.isNaN(lat))dist=String.format(Locale.US,"%.1f km",haversine(lat,lng,p.optDouble("lat"),p.optDouble("lng")));
   values.add(new String[]{p==null?"":String.format(Locale.US,"%.1f",p.optDouble("rating"))+" ★ ("+(long)(p==null?0:p.optDouble("popularity"))+" bookings)",dist,p==null?"":money(p.optDouble("starting_price")),th==null?"—":th[0]+" - "+th[1],openNow(d)?"Open Now":"Closed",policy==null?"—":policy.optInt("min_advance_pct")+"% advance","Enabled",policy==null?"—":policy.optInt("cancel_window_hr")+" hours before",policy==null?"—":policy.optInt("fee_pct")+"% fee",policy==null?"—":policy.optInt("refund_pct")+"% refund",(d.optJSONArray("staff")==null?0:d.optJSONArray("staff").length())+" staff",(d.optJSONArray("services")==null?0:d.optJSONArray("services").length())+" services"});}
  for(int r=0;r<metrics.length;r++){var row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,dp(3),0,dp(3));table.addView(row);
   var lab=new TextView(this);lab.setText(metrics[r]);lab.setTextSize(11);lab.setTypeface(Typeface.DEFAULT_BOLD);lab.setTextColor(Color.parseColor(INK));lab.setLayoutParams(new LinearLayout.LayoutParams(dp(84),ViewGroup.LayoutParams.WRAP_CONTENT));row.addView(lab);
   for(int c=0;c<values.size();c++){var cell=new TextView(this);cell.setText(values.get(c)[r]);cell.setTextSize(11);cell.setTextColor(Color.parseColor(INK));cell.setBackground(strokeBg("#FFFFFF",BORDER,6));cell.setPadding(dp(5),dp(6),dp(5),dp(6));var clp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);clp.setMargins(dp(2),0,dp(2),0);cell.setLayoutParams(clp);row.addView(cell);}}}
 // ---------- connection + account hub + password reset ----------
 void connection(){chrome("");hideTabs();content.removeAllViews();head(content,"Connect your app");label(content,"Use the secure address we gave you, or let the app find the computer automatically on your Wi-Fi. On the emulator no setup is needed.");
  var base=field(content,"Backend address (https://...)",vm.repository.base(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
  btn(content,"Connect",CORAL,v->{try{vm.repository.setBase(value(base));toast("Backend address saved.");routeStart();}catch(Exception e){message(e.getMessage());}});
  if(!ApiRepository.isEmulator())outline(content,"Find my computer automatically",PLUM,v->{vm.loading.setValue(true);vm.repository.discover((hit,error)->runOnUiThread(()->{vm.loading.setValue(false);if(error!=null)message(error+". You can also type the address above.");else{toast("Computer found. You are connected.");routeStart();}}));});
  else label(content,"Emulator detected: the app already reaches the computer through the built-in address.");}
 void unifiedLogin(){uiRole="CUSTOMER";chrome("");hideTabs();content.removeAllViews();
  var center=new LinearLayout(this);center.setOrientation(LinearLayout.VERTICAL);center.setGravity(Gravity.CENTER_HORIZONTAL);center.setPadding(0,dp(18),0,0);content.addView(center);
  var circ=new ImageView(this);circ.setImageResource(R.drawable.ic_logo_light);circ.setPadding(dp(13),dp(13),dp(13),dp(13));circ.setBackground(roundBg(CORAL,34));circ.setLayoutParams(new LinearLayout.LayoutParams(dp(68),dp(68)));center.addView(circ);
  var title=tv(center,"Sign In",22,true,INK);title.setGravity(Gravity.CENTER);
  var sub=tv(center,"One sign-in for everyone — customers, parlour owners and admins. Your own portal opens automatically.",13,false,MUTED);sub.setGravity(Gravity.CENTER);
  spacer(center,10);
  var email=field(center,"Email","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
  var pass=field(center,"Password","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
  btn(center,"Sign In",CORAL,v->{try{post("/api/auth/login",new JSONObject().put("email",value(email)).put("password",value(pass)),x->{var r=obj(x);var user=r.optJSONObject("user");String role=user==null?"":user.optString("role");
   if("OWNER".equals(role)){saveSession(r);getSharedPreferences("ui",0).edit().putString("actEmail",value(email)).apply();uiRole="OWNER";OwnerScreens.dashboard(this);}
   else if("ADMIN".equals(role)){saveSession(r);getSharedPreferences("ui",0).edit().putString("actEmail",value(email)).apply();uiRole="ADMIN";AdminScreens.dashboard(this);}
   else if("CUSTOMER".equals(role)){saveSession(r);getSharedPreferences("ui",0).edit().putBoolean("custAuth",true).putString("custEmail",value(email)).putString("actEmail",value(email)).apply();uiRole="CUSTOMER";toast("Signed in. Welcome back!");home();}
   else message("Could not sign in. Please check your email and password.");});}catch(Exception e){message(e.getMessage());}});
  outline(center,"New customer? Create a free account",CORAL,v->customerLogin(true));
  outline(center,"Own a parlour? Register as an owner",CORAL,v->OwnerScreens.login(this,true));
  var fp=tv(center,"Forgot password",13,false,MUTED);fp.setGravity(Gravity.CENTER);fp.setPadding(0,dp(10),0,0);fp.setOnClickListener(v->forgotPassword());
  outline(center,"Continue as guest",MUTED,v->home());}
 void customerLogin(boolean register){if(!register){unifiedLogin();return;}chrome("");hideTabs();content.removeAllViews();
  var center=new LinearLayout(this);center.setOrientation(LinearLayout.VERTICAL);center.setGravity(Gravity.CENTER_HORIZONTAL);center.setPadding(0,dp(18),0,0);content.addView(center);
  var circ=new ImageView(this);circ.setImageResource(R.drawable.ic_logo_light);circ.setPadding(dp(13),dp(13),dp(13),dp(13));circ.setBackground(roundBg(CORAL,34));circ.setLayoutParams(new LinearLayout.LayoutParams(dp(68),dp(68)));center.addView(circ);
  var title=tv(center,register?"Create Customer Account":"Customer Sign In",22,true,INK);title.setGravity(Gravity.CENTER);
  var sub=tv(center,"Your bookings, past visits and reviews stay saved to your account",13,false,MUTED);sub.setGravity(Gravity.CENTER);
  spacer(center,10);
  EditText name=null,phone=null;
  if(register){name=field(center,"Full Name *","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PERSON_NAME);phone=field(center,"Phone *","",InputType.TYPE_CLASS_PHONE);}
  var email=field(center,"Email","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
  var pass=field(center,"Password","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
  final EditText nameF=name,phoneF=phone;
  btn(center,register?"Create Account":"Sign In",CORAL,v->{
   try{if(register){var body=new JSONObject().put("name",value(nameF)).put("email",value(email)).put("phone",value(phoneF)).put("password",value(pass)).put("role","CUSTOMER");
     post("/api/auth/register",body,x->{var r=obj(x);if(!"CUSTOMER".equals(r.optJSONObject("user")==null?"":r.optJSONObject("user").optString("role"))){message("Could not create the account.");return;}saveSession(r);getSharedPreferences("ui",0).edit().putBoolean("custAuth",true).putString("custEmail",value(email)).putString("actEmail",value(email)).apply();toast("Welcome! Your account is ready.");home();});}
    else post("/api/auth/login",new JSONObject().put("email",value(email)).put("password",value(pass)),x->{var r=obj(x);if(!"CUSTOMER".equals(r.optJSONObject("user")==null?"":r.optJSONObject("user").optString("role"))){message("This is not a customer account.");return;}saveSession(r);getSharedPreferences("ui",0).edit().putBoolean("custAuth",true).putString("custEmail",value(email)).apply();toast("Signed in. Welcome back!");home();});
   }catch(Exception e){message(e.getMessage());}});
  outline(center,register?"Already have an account? Sign In":"New here? Create a free account",CORAL,v->customerLogin(!register));
  var fp=tv(center,"Forgot password",13,false,MUTED);fp.setGravity(Gravity.CENTER);fp.setPadding(0,dp(10),0,0);fp.setOnClickListener(v->forgotPassword());
  outline(center,"Continue as guest",MUTED,v->home());}
 void pastBookings(){chrome("");hideTabs();content.removeAllViews();screenBar("Past Bookings (Archive)",()->accountScreen(),null,null);
  tv(content,"Loading your past bookings...",13,false,MUTED);
  get("/api/bookings/archive",v->{content.removeAllViews();screenBar("Past Bookings (Archive)",()->accountScreen(),null,null);var rows=arr(v);
   if(rows.length()==0){emptyState(content,"🧾","No past bookings yet","Completed visits are archived here automatically, so you can rebook and review them any time.","Browse Parlours",()->home());return;}
   tv(content,"Your completed visits, saved permanently:",12,false,MUTED);
   for(int i=0;i<rows.length();i++){var r=rows.optJSONObject(i);if(r==null)continue;var c=card();
    tv(c,r.optString("parlour_name","Parlour"),15,true,INK);
    tv(c,"Services: "+r.optString("services_text",""),12,false,MUTED);
    tv(c,"Completed: "+r.optString("completed_at",""),12,false,MUTED);
    var hr=hrow(c);var tot=tv(hr,"Total: "+money(r.optDouble("total_amount")),13,true,CORAL);tot.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
    final long bid=r.optLong("booking_id");final long pid2=r.optLong("parlour_id");btnW(hr,"Review",CORAL,1,x->review(bid));outlineW(hr,"Book again",PLUM,1,x->wizardStart(pid2,0));}});}
 void accountScreen(){chrome("");hideTabs();content.removeAllViews();head(content,"Account");
  var c=card();String role=vm.repository.role();
  outline(c,"Connection",INK,v->connection());
  if("OWNER".equals(role))btn(c,"Business dashboard",PLUM,v->OwnerScreens.dashboard(this));
  else if("ADMIN".equals(role))btn(c,"Admin panel",PLUM,v->AdminScreens.dashboard(this));
  else{boolean ca=getSharedPreferences("ui",0).getBoolean("custAuth",false);
   if(ca){tv(c,"Customer account: "+getSharedPreferences("ui",0).getString("custEmail",""),12,false,MUTED);btn(c,"My bookings",CORAL,v->myBookings());btn(c,"Past bookings (archive)",PLUM,v->pastBookings());}
   else{btn(c,"Sign in",CORAL,v->unifiedLogin());outline(c,"Create customer account",CORAL,v->customerLogin(true));btn(c,"My bookings",PLUM,v->myBookings());outline(c,"Past bookings (archive)",PLUM,v->pastBookings());}
   outline(c,"Register as an owner",CORAL,v->OwnerScreens.login(this,true));}
  outline(c,"Switch to customer mode",MUTED,v->{vm.repository.logout();FirebaseSession.signOut();uiRole="CUSTOMER";ensureSession();});}
 void forgotPassword(){chrome("");hideTabs();content.removeAllViews();screenBar("Reset your password",()->unifiedLogin(),null,null);
  label(content,"Enter your account email. If an account exists, we will email a reset link (check spam too).");
  var email=field(content,"Email","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
  btn(content,"Email me a reset link",CORAL,v->{try{post("/api/auth/forgot-password",new JSONObject().put("email",value(email)),x->message("If an account exists for that email, a reset link is on its way. Open it on this device or your computer to set a new password."));}catch(Exception e){message(e.getMessage());}});}
}
