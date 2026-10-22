package com.personal.mahjongg

import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        setContent { Game() }
    }
}
private data class Spot(val x: Int, val y: Int, val z: Int = 0)
private data class Tile(val id: Int, val pair: Int, val art: Int, val spot: Spot)
private fun layout(): List<Spot> {
    val a = mutableListOf<Spot>()
    val rows = listOf(10,12,14,14,14,14,14,14,12,10)
    rows.forEachIndexed { y,n -> val start=(14-n)/2; repeat(n) { x -> a.add(Spot(start+x,y)) } }
    for (y in 3..6) for (x in 5..8) a.add(Spot(x,y,1))
    return a.take(144)
}
@Composable private fun Game() {
    val spots = remember { layout() }
    var tiles by remember { mutableStateOf(deal(spots)) }
    var gone by remember { mutableStateOf(setOf<Int>()) }
    var selected by remember { mutableIntStateOf(-1) }
    var secs by remember { mutableIntStateOf(0) }
    var sound by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(gone.size) { while (gone.size < 144) { delay(1000); secs++ } }
    fun beep() { if(sound) try { ToneGenerator(AudioManager.STREAM_MUSIC,35).startTone(ToneGenerator.TONE_PROP_BEEP,50) } catch (_:Exception) {} }
    fun free(t: Tile): Boolean {
        if (t.id in gone) return false
        val covered = tiles.any { o -> o.id !in gone && o.spot.z>t.spot.z && abs(o.spot.x-t.spot.x)<1 && abs(o.spot.y-t.spot.y)<1 }
        if(covered) return false
        val same=tiles.filter { it.id !in gone && it.id!=t.id && it.spot.y==t.spot.y && it.spot.z==t.spot.z }
        val left=same.any { it.spot.x==t.spot.x-1 }; val right=same.any { it.spot.x==t.spot.x+1 }
        return !left || !right
    }
    fun tap(id:Int) {
        val t=tiles[id]
        if(!free(t)){ message="BLOCKED"; beep(); return }
        if(selected<0){selected=id;message="";beep();return}
        if(selected==id){selected=-1;return}
        if(tiles[selected].pair==t.pair){gone=gone+selected+id;selected=-1;message=if(gone.size==144)"YOU WIN!" else "";beep()}
        else {selected=id;beep()}
    }
    fun reset(){tiles=deal(spots);gone=emptySet();selected=-1;secs=0;message=""}
    MaterialTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF000080))) {
            val w=constraints.maxWidth.toFloat(); val h=constraints.maxHeight.toFloat()
            val tw=minOf(w/15.5f,h/8.2f); val th=tw*1.28f
            val left=(w-tw*14)/2f
            Canvas(Modifier.fillMaxSize().pointerInput(tiles,gone,selected,tw,th){detectTapGestures { p ->
                tiles.sortedBy { it.spot.z }.asReversed().firstOrNull { t ->
                    t.id !in gone && p.x>=left+t.spot.x*tw+t.spot.z*tw*.12f && p.x<=left+(t.spot.x+1)*tw+t.spot.z*tw*.12f &&
                    p.y>=t.spot.y*th*.75f-t.spot.z*th*.12f && p.y<=(t.spot.y*.75f*th+th-t.spot.z*th*.12f)
                }?.let { tap(it.id) }
            }}) {
                val c=drawContext.canvas.nativeCanvas
                val p=Paint(Paint.ANTI_ALIAS_FLAG)
                tiles.sortedWith(compareBy<Tile>{it.spot.z}.thenBy{it.spot.y}.thenBy{it.spot.x}).forEach { t ->
                    if(t.id in gone)return@forEach
                    val x=left+t.spot.x*tw+t.spot.z*tw*.12f; val y=t.spot.y*th*.75f-t.spot.z*th*.12f
                    p.color=android.graphics.Color.rgb(35,40,52); c.drawRoundRect(RectF(x+tw*.09f,y+th*.07f,x+tw*1.08f,y+th*1.08f),3f,3f,p)
                    p.color=if(t.id==selected) android.graphics.Color.YELLOW else android.graphics.Color.rgb(176,184,188)
                    c.drawRect(x-1,y-1,x+tw+1,y+th+1,p)
                    p.color=android.graphics.Color.rgb(210,211,205);c.drawRect(x,y,x+tw,y+th,p)
                    p.color=android.graphics.Color.rgb(245,241,220);c.drawRect(x+tw*.09f,y+th*.05f,x+tw*.91f,y+th*.95f,p)
                    p.color=android.graphics.Color.rgb(62+(t.art*19)%100,42+(t.art*13)%80,30+(t.art*7)%70)
                    p.style=Paint.Style.STROKE;p.strokeWidth=tw*.055f;c.drawRect(x+tw*.16f,y+th*.12f,x+tw*.84f,y+th*.88f,p);p.style=Paint.Style.FILL
                    p.textAlign=Paint.Align.CENTER;p.typeface=android.graphics.Typeface.create("serif",android.graphics.Typeface.BOLD);p.textSize=tw*.44f
                    val symbols=listOf("♜","♞","♖","♘","⚔","♛","♚","♝","✠","⚜","♙")
                    c.drawText(symbols[t.art%symbols.size],x+tw/2,y+th*.60f,p)
                    p.textSize=tw*.13f;c.drawText("%02d".format(t.art+1),x+tw/2,y+th*.82f,p)
                    if(!free(t)){p.color=android.graphics.Color.argb(28,0,0,0);c.drawRect(x,y,x+tw,y+th,p)}
                }
            }
            Column(Modifier.align(Alignment.CenterStart).padding(start=8.dp)){Text("Tiles Left:",color=Color.White,fontSize=12.sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold);Text("${144-gone.size}",color=Color.White,fontSize=12.sp,fontFamily=FontFamily.Serif)}
            Column(Modifier.align(Alignment.CenterEnd).padding(end=8.dp,top=28.dp)){Text("Time:",color=Color.White,fontSize=12.sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold);Text("%d:%02d".format(secs/60,secs%60),color=Color.White,fontSize=12.sp,fontFamily=FontFamily.Serif)}
            Row(Modifier.align(Alignment.TopCenter),verticalAlignment=Alignment.CenterVertically){Text("MAH JONGG",color=Color(0xFFB8C6FF),fontSize=12.sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold);TextButton(onClick={sound=!sound}){Text(if(sound)"Sound On" else "Sound Off",color=Color.White,fontSize=9.sp)};TextButton(onClick={reset()}){Text("New Game",color=Color.White,fontSize=9.sp)}}
            if(message.isNotEmpty())Text(message,Modifier.align(Alignment.BottomCenter),color=Color.White,fontSize=12.sp)
        }
    }
}
private fun deal(spots:List<Spot>):List<Tile>{
    val pairs=(0 until spots.size/2).flatMap{listOf(it,it)}.shuffled()
    return spots.mapIndexed{i,s->Tile(i,pairs[i],pairs[i]%44,s)}
}
