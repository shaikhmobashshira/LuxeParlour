package com.parlour.management;
import static com.parlour.management.Db.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import org.springframework.web.bind.annotation.*;
@RestController
public class Availability {
 final Db db;final Catalog catalog;Availability(Db db,Catalog catalog){this.db=db;this.catalog=catalog;}
 record Selection(List<Map<String,Object>> services,int minutes,BigDecimal total){}
 Selection selection(long pid,List<Long> ids){if(ids==null||ids.isEmpty()||ids.size()>20||new HashSet<>(ids).size()!=ids.size())throw error(400,"Select 1\u201320 distinct services");var selected=new ArrayList<Map<String,Object>>();int duration=0;BigDecimal total=BigDecimal.ZERO;for(long id:ids){var s=db.one("SELECT * FROM services WHERE service_id=? AND parlour_id=? AND status='ACTIVE'",id,pid);duration+=((Number)s.get("duration_min")).intValue();total=total.add(money(s.get("price")));selected.add(s);}if(duration>720)throw error(400,"Appointment exceeds 12 hours");return new Selection(selected,duration,total);}
 // Every row needed to decide slots for one parlour on one day, fetched once per
 // request. The previous version re-ran choose() per 15-minute step and stylist,
 // and choose() issued ~11 fresh Firestore queries per call (~480 per request,
 // ~45 s). This context needs ~15 queries total, so a request answers in ~1-2 s.
 record Ctx(Map<String,Object> parlour,List<Map<String,Object>> hours,boolean holiday,List<Map<String,Object>> staff,Map<Long,List<Map<String,Object>>> servicesByStaff,Map<Long,List<Map<String,Object>>> schedulesByStaff,List<Map<String,Object>> blocked,List<Map<String,Object>> bookings){}
 Ctx ctx(long pid,LocalDate date,Instant blockedBefore){
  var p=db.one("SELECT * FROM parlours WHERE parlour_id=?",pid);
  int dow=date.getDayOfWeek().getValue();
  var hours=db.list("SELECT * FROM business_hours WHERE parlour_id=? AND day_of_week=?",pid,dow);
  boolean holiday=db.count("SELECT COUNT(*) FROM holidays WHERE parlour_id=? AND holiday_date=?",pid,java.sql.Date.valueOf(date))>0;
  var staff=db.list("SELECT * FROM staff WHERE parlour_id=? AND status='ACTIVE' ORDER BY staff_id",pid);
  var svc=new HashMap<Long,List<Map<String,Object>>>();var sch=new HashMap<Long,List<Map<String,Object>>>();
  for(var st:staff){long sid=id(st,"staff_id");svc.put(sid,db.list("SELECT * FROM staff_services WHERE staff_id=?",sid));sch.put(sid,db.list("SELECT * FROM staff_schedules WHERE staff_id=? AND day_of_week=? AND status='ACTIVE'",sid,dow));}
  var blocked=db.list("SELECT * FROM blocked_slots WHERE parlour_id=? AND start_at<?",pid,Timestamp.from(blockedBefore));
  var bookings=db.list("SELECT * FROM bookings WHERE parlour_id=?",pid);
  return new Ctx(p,hours,holiday,staff,svc,sch,blocked,bookings);
 }
 Long chooseFrom(Ctx c,List<Long> services,Long preferred,Instant start,int minutes,long exclude){
  ZoneId zone=ZoneId.of(c.parlour().get("timezone").toString());var at=start.atZone(zone);Instant end=start.plusSeconds(minutes*60L);
  if(!start.isAfter(Instant.now())||at.toLocalDate().isAfter(LocalDate.now(zone).plusDays(90))||!end.atZone(zone).toLocalDate().equals(at.toLocalDate()))return null;
  if(c.holiday())return null;
  if(!contains(c.hours(),at.toLocalTime(),end.atZone(zone).toLocalTime()))return null;
  for(var st:c.staff()){long sid=id(st,"staff_id");if(preferred!=null&&preferred!=sid)continue;boolean qualified=true;for(long service:services)if(c.servicesByStaff().getOrDefault(sid,List.of()).stream().filter(r->id(r,"service_id")==service).count()!=1)qualified=false;if(!qualified)continue;
   if(!contains(c.schedulesByStaff().getOrDefault(sid,List.of()),at.toLocalTime(),end.atZone(zone).toLocalTime()))continue;
   boolean blocked=c.blocked().stream().anyMatch(bl->(bl.get("staff_id")==null||id(bl,"staff_id")==sid)&&Bookings.instant(bl.get("start_at")).isBefore(end)&&Bookings.instant(bl.get("end_at")).isAfter(start));
   if(blocked)continue;
   boolean clash=c.bookings().stream().filter(r->id(r,"staff_id")==sid&&Bookings.instant(r.get("end_at")).isAfter(start)).anyMatch(r->id(r,"booking_id")!=exclude&&Bookings.instant(r.get("start_at")).isBefore(end)&&(Set.of("CONFIRMED","PENDING_OWNER","CANCEL_PENDING","COMPLETED").contains(r.get("status"))||("PENDING_PAYMENT".equals(r.get("status"))&&r.get("hold_until")!=null&&Bookings.instant(r.get("hold_until")).isAfter(Instant.now()))));
   if(!clash)return sid;
  }return null;
 }
 @GetMapping("/api/public/parlours/{pid}/availability") Map<String,Object> slots(@PathVariable long pid,@RequestParam List<Long> services,@RequestParam String date,@RequestParam(required=false)Long staff){var p=catalog.verified(pid);var s=selection(pid,services);ZoneId zone=ZoneId.of(p.get("timezone").toString());LocalDate day=LocalDate.parse(date);if(day.isBefore(LocalDate.now(zone))||day.isAfter(LocalDate.now(zone).plusDays(90)))throw error(400,"Choose a date within the next 90 days");Instant dayFrom=day.atStartOfDay(zone).toInstant(),dayTo=day.plusDays(1).atStartOfDay(zone).toInstant();var c=ctx(pid,day,dayTo);var dayBookings=c.bookings().stream().filter(r->!Bookings.instant(r.get("start_at")).isBefore(dayFrom)&&Bookings.instant(r.get("start_at")).isBefore(dayTo)).toList();var result=new ArrayList<Map<String,Object>>();var booked=new ArrayList<Map<String,Object>>();for(var h:c.hours()){if(h.get("start_time")==null||h.get("end_time")==null)continue;LocalTime open=time(h.get("start_time")),close=time(h.get("end_time"));for(LocalDateTime at=day.atTime(open);!at.plusMinutes(s.minutes()).isAfter(day.atTime(close));at=at.plusMinutes(15)){Instant start=at.atZone(zone).toInstant();Instant end=start.plusSeconds(s.minutes()*60L);Long chosen=chooseFrom(c,services,staff,start,s.minutes(),0);if(chosen!=null)result.add(map("start_at",start,"end_at",end,"staff_id",chosen));else if(dayBookings.stream().anyMatch(r->(staff==null||id(r,"staff_id")==staff)&&Bookings.instant(r.get("start_at")).isBefore(end)&&Bookings.instant(r.get("end_at")).isAfter(start)&&(Set.of("CONFIRMED","PENDING_OWNER","CANCEL_PENDING").contains(r.get("status"))||("PENDING_PAYMENT".equals(r.get("status"))&&r.get("hold_until")!=null&&Bookings.instant(r.get("hold_until")).isAfter(Instant.now())))))booked.add(map("start_at",start,"end_at",end));}}return map("slots",result,"booked_slots",booked,"total_amount",s.total(),"duration_min",s.minutes(),"timezone",zone.getId());}
 Long choose(long pid,List<Long> services,Long preferred,Instant start,int minutes,long exclude){var p=db.one("SELECT * FROM parlours WHERE parlour_id=?",pid);ZoneId zone=ZoneId.of(p.get("timezone").toString());LocalDate date=start.atZone(zone).toLocalDate();Instant end=start.plusSeconds(minutes*60L);return chooseFrom(ctx(pid,date,end),services,preferred,start,minutes,exclude);}
 static LocalTime time(Object v){return v instanceof java.sql.Time t?t.toLocalTime():LocalTime.parse(v.toString());}
 static boolean contains(List<Map<String,Object>> periods,LocalTime start,LocalTime end){return periods.stream().anyMatch(p->p.get("start_time")!=null&&p.get("end_time")!=null&&!start.isBefore(time(p.get("start_time")))&&!end.isAfter(time(p.get("end_time"))));}
}
