package com.parlour.management;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;

/** Publishes this backend's public URL to the Realtime Database pointer node
 *  (config/backend_url) on startup, so installed apps can always find the
 *  current address even when the tunnel URL changes. Apps only read the node;
 *  writes happen here through the Admin SDK and bypass RTDB rules. */
@Component
public class Pointer {
 @Value("${app.public-url:}") String publicUrl;
 @EventListener(ApplicationReadyEvent.class)
 void publish(){
  try{
   if(publicUrl==null||!publicUrl.startsWith("https://")){System.out.println("POINTER_SKIPPED no https public url");return;}
   String url=publicUrl.replaceAll("/+$","");
   var db=FirebaseDatabase.getInstance(FirebaseApp.getInstance(),"https://parlour-booking-management-default-rtdb.asia-southeast1.firebasedatabase.app");
   db.getReference("config/backend_url").setValueAsync(Map.of("url",url,"updated_at",Instant.now().toString()));
   System.out.println("POINTER_PUBLISHED "+url);
  }catch(Exception e){System.out.println("POINTER_FAILED "+e.getMessage());}
 }
}
