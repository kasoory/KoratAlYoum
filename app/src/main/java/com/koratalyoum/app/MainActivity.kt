package com.koratalyoum.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

private val Ink = Color(0xFF080E10)
private val Surface = Color(0xFF14211F)
private val Gold = Color(0xFFD8B979)
private val Muted = Color(0xFFA7B5B0)
private val White = Color(0xFFF5F6F2)

data class Fixture(val id:Int,val league:String,val home:String,val away:String,val homeGoals:String,val awayGoals:String,val status:String,val leagueId:Int,val homeLogo:String,val awayLogo:String,val country:String)

class MainActivity: ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val prefs=getSharedPreferences("kora_private",MODE_PRIVATE)
  setContent {
   var key by remember { mutableStateOf(prefs.getString("api_key","") ?: "") }
   var draft by remember { mutableStateOf(key) }
   var section by remember { mutableStateOf("المباريات") }
   var fixtures by remember { mutableStateOf(listOf<Fixture>()) }
   var loading by remember { mutableStateOf(false) }
   var error by remember { mutableStateOf("") }
   var selected by remember { mutableStateOf<Fixture?>(null) }
   var detail by remember { mutableStateOf<JSONObject?>(null) }
   var detailLoading by remember { mutableStateOf(false) }
   var dateOffset by remember { mutableIntStateOf(0) }
   fun load(){
    if(key.isBlank())return
    loading=true;error=""
    val date=SimpleDateFormat("yyyy-MM-dd",Locale.US).apply {timeZone=TimeZone.getTimeZone("Asia/Riyadh")}.format(Calendar.getInstance(TimeZone.getTimeZone("Asia/Riyadh")).apply {add(Calendar.DATE,dateOffset)}.time)
    thread {
     try {
      val conn=URL("https://v3.football.api-sports.io/fixtures?date=$date&timezone=Asia%2FRiyadh").openConnection() as HttpURLConnection
      conn.setRequestProperty("x-apisports-key",key);conn.connectTimeout=12000;conn.readTimeout=15000
      if(conn.responseCode!=200)error="تعذر الاتصال: HTTP ${conn.responseCode}"
      else {
       val json=JSONObject(conn.inputStream.bufferedReader().use{it.readText()})
       val response=json.optJSONArray("response")
       val result=mutableListOf<Fixture>()
       if(response!=null)for(i in 0 until response.length()){
        val o=response.getJSONObject(i);val league=o.getJSONObject("league");val teams=o.getJSONObject("teams");val goals=o.getJSONObject("goals");val fixture=o.getJSONObject("fixture")
        val status=fixture.getJSONObject("status").optString("short")
        result.add(Fixture(fixture.optInt("id"),league.optString("name"),teams.getJSONObject("home").optString("name"),teams.getJSONObject("away").optString("name"),goals.optString("home","–").replace("null","–"),goals.optString("away","–").replace("null","–"),status,league.optInt("id"),teams.getJSONObject("home").optString("logo"),teams.getJSONObject("away").optString("logo"),league.optString("country")))
       }
       runOnUiThread{fixtures=result}
      }
     }catch(e:Exception){runOnUiThread{error="تعذر جلب المباريات: ${e.localizedMessage ?: "خطأ في الاتصال"}"}}
     runOnUiThread{loading=false}
    }
   }
   fun openMatch(f:Fixture){
    selected=f;detail=null;detailLoading=true
    thread {
     var response:JSONObject?=null
     try {
      val conn=URL("https://v3.football.api-sports.io/fixtures?id=${f.id}").openConnection() as HttpURLConnection
      conn.setRequestProperty("x-apisports-key",key);conn.connectTimeout=12000;conn.readTimeout=15000
      if(conn.responseCode==200)response=JSONObject(conn.inputStream.bufferedReader().use{it.readText()}).optJSONArray("response")?.optJSONObject(0)
     }catch(_:Exception){}
     runOnUiThread{detail=response;detailLoading=false}
    }
   }
   LaunchedEffect(key,dateOffset){if(key.isNotBlank())load()}
   Column(Modifier.fillMaxSize().background(Ink).padding(18.dp)){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column {Text("KORA  ✦  LUXURY",fontSize=26.sp,fontWeight=FontWeight.Black,color=Gold);Text("THE BEAUTIFUL GAME",fontSize=10.sp,color=Muted,letterSpacing=2.sp)};Text("⚽",fontSize=28.sp)}
    Text("كورة اليوم  •  تغطية المباريات حول العالم",color=Muted,fontSize=12.sp)
    Spacer(Modifier.height(15.dp))
    if(selected!=null){
     TextButton(onClick={selected=null}){Text("‹  العودة للمباريات",color=Gold)}
     Text("${selected!!.home}  ${selected!!.homeGoals} : ${selected!!.awayGoals}  ${selected!!.away}",color=White,fontSize=20.sp,fontWeight=FontWeight.Bold)
     Spacer(Modifier.height(14.dp))
     if(detailLoading)CircularProgressIndicator(color=Gold)
     else if(detail==null)Text("تفاصيل المباراة غير متاحة من المصدر.",color=Muted)
     else {
      val data=detail!!
      val tabs=listOf("التشكيلات","الأحداث","الإحصائيات")
      var detailTab by remember(selected){mutableStateOf("التشكيلات")}
      Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){tabs.forEach{tab->TextButton(onClick={detailTab=tab}){Text(tab,color=if(detailTab==tab)Gold else Muted,fontSize=12.sp)}}}
      LazyColumn {
       val array=when(detailTab){"التشكيلات"->data.optJSONArray("lineups");"الأحداث"->data.optJSONArray("events");else->data.optJSONArray("statistics")}
       if(array==null||array.length()==0)item{Text("هذه البيانات غير متاحة للمباراة.",color=Muted)}
       else if(detailTab=="التشكيلات")for(i in 0 until array.length()){
        val lineup=array.getJSONObject(i)
        item{Text(lineup.optJSONObject("team")?.optString("name").orEmpty()+"  •  "+lineup.optString("formation"),color=Gold,fontSize=19.sp)}
        for(group in listOf("startXI","substitutes")){
         item{Text(if(group=="startXI")"الأساسيون" else "البدلاء",color=Gold)}
         val players=lineup.optJSONArray(group)
         if(players!=null)for(j in 0 until players.length()){
          val player=players.getJSONObject(j).optJSONObject("player")
          item{Text("${player?.optString("number")}  ${player?.optString("name")}  •  ${player?.optString("pos")}",color=White,modifier=Modifier.padding(vertical=7.dp))}
         }
        }
       }else for(i in 0 until array.length()){
        val obj=array.getJSONObject(i)
        item{
         val label=if(detailTab=="الأحداث")"${obj.optJSONObject("time")?.optString("elapsed")}'  ${obj.optJSONObject("player")?.optString("name")}  •  ${obj.optString("detail")}" else obj.optJSONObject("team")?.optString("name").orEmpty()
         Text(label,color=White,modifier=Modifier.padding(vertical=8.dp))
         if(detailTab=="الإحصائيات"){val stats=obj.optJSONArray("statistics");if(stats!=null)for(j in 0 until stats.length()){val stat=stats.getJSONObject(j);Text("${stat.optString("type")}: ${stat.optString("value")}",color=Muted,fontSize=12.sp)}}
        }
       }
      }
     }
    }else if(key.isBlank()||section=="الإعدادات"){
     Text("مفتاح API-Football",color=White,fontSize=20.sp)
     Spacer(Modifier.height(12.dp))
     OutlinedTextField(value=draft,onValueChange={draft=it},label={Text("API key")},singleLine=true,colors=OutlinedTextFieldDefaults.colors(focusedTextColor=White,unfocusedTextColor=White,focusedBorderColor=Gold))
     Spacer(Modifier.height(12.dp))
     Button(onClick={key=draft.trim();prefs.edit().putString("api_key",key).apply();section="المباريات"},colors=ButtonDefaults.buttonColors(containerColor=Gold)){Text("حفظ على الجهاز",color=Ink)}
     Text("يُحفظ المفتاح محليًا داخل بيانات التطبيق ولا يُرفع إلى GitHub.",color=Muted,fontSize=12.sp)
    }else {
     Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("المباريات","مباشر","الأهلي","الإعدادات").forEach{item->
      TextButton(onClick={section=item}){Text(item,color=if(section==item)Gold else Muted,fontSize=12.sp)}
     }}
     if(section=="المباريات")Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      listOf(-1 to "أمس",0 to "اليوم",1 to "غدًا").forEach{(offset,label)->TextButton(onClick={dateOffset=offset}){Text(label,color=if(dateOffset==offset)Gold else Muted)}}
     }
     Text(if(section=="الأهلي")"قلعة الكؤوس  ♛" else if(section=="مباشر")"مباريات مباشرة" else "مركز المباريات",fontSize=24.sp,color=White,fontWeight=FontWeight.Bold)
     Spacer(Modifier.height(12.dp))
     if(loading)CircularProgressIndicator(color=Gold)
     if(error.isNotBlank())Text(error,color=Color(0xFFFF8A80))
     val important=setOf(307,39,140,135,78,61,2,3,848,17,4,203,262,94,88,45,48,143,137,15,253,71,128)
     val visible=fixtures.filter { f->when(section){"مباشر"->f.status in listOf("1H","2H","HT","ET","P","BT","INT");"الأهلي"->f.home.contains("Ahli",true)||f.away.contains("Ahli",true)||f.home.contains("الأهلي")||f.away.contains("الأهلي");else->f.leagueId in important} }.sortedWith(compareBy<Fixture> { if(it.leagueId==307)0 else if(it.leagueId==2)1 else if(it.leagueId==39)2 else if(it.leagueId==140)3 else if(it.leagueId==135)4 else 5 }.thenBy{it.league})
     LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){
      items(visible){f->
       Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Surface).border(1.dp,Color(0xFF34443B),RoundedCornerShape(22.dp)).clickable{openMatch(f)}.padding(16.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
         Text("✦  "+f.league,color=Gold,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
         Text(if(f.status in listOf("1H","2H","HT","ET"))"● LIVE" else if(f.status=="FT")"انتهت" else "قادمة",color=if(f.status in listOf("1H","2H","HT","ET"))Color(0xFFFF6C6C) else Muted,fontSize=11.sp)
        }
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
         Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){
          AsyncImage(model=f.homeLogo,contentDescription=f.home,modifier=Modifier.size(48.dp),contentScale=ContentScale.Fit)
          Spacer(Modifier.height(6.dp))
          Text(f.home,color=White,fontSize=12.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,maxLines=2)
         }
         Column(horizontalAlignment=Alignment.CenterHorizontally){
          Text("${f.homeGoals}   :   ${f.awayGoals}",color=White,fontSize=28.sp,fontWeight=FontWeight.Black)
          Text(if(f.status=="NS")"VS" else f.status,color=Gold,fontSize=11.sp)
         }
         Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){
          AsyncImage(model=f.awayLogo,contentDescription=f.away,modifier=Modifier.size(48.dp),contentScale=ContentScale.Fit)
          Spacer(Modifier.height(6.dp))
          Text(f.away,color=White,fontSize=12.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,maxLines=2)
         }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color=Color(0xFF34443B))
        Spacer(Modifier.height(9.dp))
        Text("التشكيلة   •   الأحداث   •   الإحصائيات      ←",color=Muted,fontSize=11.sp)
       }
      }
      if(!loading&&visible.isEmpty())item{Text("لا توجد مباريات من البطولات المختارة اليوم. جرّب تاريخًا آخر.",color=Muted)}
     }
    }
   }
  }
 }
}
