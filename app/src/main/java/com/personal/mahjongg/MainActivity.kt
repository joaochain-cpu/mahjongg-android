package com.personal.mahjongg

import android.graphics.BitmapFactory
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        setContent { Game() }
    }
}
private data class Spot(val col:Int, val row:Int, val layer:Int=0)
private data class Tile(val id:Int, val face:Int, val spot:Spot)
private fun boardLayout():List<Spot> {
    // A compact, stepped "dragon" layout: broad base, side wings and four
    // progressively smaller raised levels. Coordinates are shared by layers
    // so the cover/free rules work on the same grid as the drawing.
    val out=mutableListOf<Spot>()
    for (r in 0..7) for (c in 1..12) out.add(Spot(c,r,0)) // 96
    for (r in 2..5) { out.add(Spot(0,r,0)); out.add(Spot(13,r,0)) } // +8 = 104
    for (r in 2..5) for (c in 4..9) out.add(Spot(c,r,1)) // +24 = 128
    for (r in 3..4) for (c in 5..8) out.add(Spot(c,r,2)) // +8 = 136
    for (r in 3..4) for (c in 6..7) out.add(Spot(c,r,3)) // +4 = 140
    for (r in 3..4) for (c in 6..7) out.add(Spot(c,r,4)) // +4 = 144
    return out
}
private fun makeDeal(spots:List<Spot>):List<Tile> {
    // 34 ordinary faces x 4, plus 8 single flowers/seasons = 144 tiles.
    val faces=(0 until 34).flatMap { listOf(it,it,it,it) } + (34 until 42).toList()
    return spots.mapIndexed { i,s -> Tile(i,faces.shuffled()[i],s) }
}
@Composable private fun Game() {
    val context=LocalContext.current
    val art=remember(context) { (0 until 44).associateWith { n ->
        val id=context.resources.getIdentifier("medieval_%02d".format(n),"drawable",context.packageName)
        if(id!=0) BitmapFactory.decodeResource(context.resources,id) else null
    } }
    val spots=remember { boardLayout() }
    var tiles by remember { mutableStateOf(makeDeal(spots)) }
    var removed by remember { mutableStateOf(setOf<Int>()) }
    var selected by remember { mutableIntStateOf(-1) }
    var seconds by remember { mutableIntStateOf(0) }
    var sound by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("Tap a free tile, then its match") }
    LaunchedEffect(removed.size) { while(removed.size<tiles.size) { delay(1000); seconds++ } }
    fun beep() { if(sound) try { ToneGenerator(AudioManager.STREAM_MUSIC,35).startTone(ToneGenerator.TONE_PROP_BEEP,45) } catch (_:Exception) {} }
    fun isFree(t:Tile):Boolean {
        if(t.id in removed) return false
        val covered=tiles.any { o -> o.id !in removed && o.spot.layer>t.spot.layer && o.spot.col==t.spot.col && o.spot.row==t.spot.row }
        if(covered) return false
        val peers=tiles.filter { o -> o.id !in removed && o.id!=t.id && o.spot.layer==t.spot.layer && o.spot.row==t.spot.row }
        val left=peers.any { it.spot.col==t.spot.col-1 }
        val right=peers.any { it.spot.col==t.spot.col+1 }
        return !left || !right
    }
    fun choose(id:Int) {
        val t=tiles.firstOrNull { it.id==id } ?: return
        if(!isFree(t)) { message="Blocked: clear a tile beside it or above it"; beep(); return }
        if(selected==id) { selected=-1; message="Selection cleared"; return }
        if(selected<0 || selected !in tiles.indices || selected in removed) {
            selected=id; message="Now tap a matching free tile"; beep(); return
        }
        val previous=tiles[selected]
        if(previous.face==t.face) {
            removed=removed+previous.id+t.id
            selected=-1
            message=if(removed.size==tiles.size) "You win!" else "Matched!"
        } else {
            selected=id
            message="Different tile — find the same picture"
        }
        beep()
    }
    fun restart() { tiles=makeDeal(spots); removed=emptySet(); selected=-1; seconds=0; message="New game — tap a free tile" }
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFF164C36)).padding(horizontal=6.dp, vertical=5.dp)) {
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
                Text("MAH JONGG",color=Color(0xFFFFE4A3),fontSize=17.sp)
                Text((tiles.size-removed.size).toString()+" left  •  "+"%d:%02d".format(seconds/60,seconds%60),color=Color.White,fontSize=12.sp)
            }
            Text(message,color=Color(0xFFFFE4A3),fontSize=12.sp,modifier=Modifier.padding(bottom=3.dp))
            Canvas(Modifier.weight(1f).fillMaxWidth().pointerInput(tiles,removed,selected) {
                detectTapGestures { p ->
                    val w=size.width; val h=size.height
                    val tileW=min(w/14.2f,h/11.2f)
                    val tileH=tileW*1.25f
                    val stepX=tileW*0.91f; val stepY=tileH*0.80f
                    val boardW=13*stepX+tileW; val left=(w-boardW)/2f
                    val boardH=7*stepY+tileH; val top=(h-boardH)/2f
                    val hit=tiles.sortedWith(compareBy<Tile>{it.spot.layer}.thenBy{it.spot.row}.thenBy{it.spot.col}).asReversed().firstOrNull { t ->
                        if(t.id in removed) false else {
                            val x=left+t.spot.col*stepX-t.spot.layer*tileW*.035f
                            val y=top+t.spot.row*stepY-t.spot.layer*tileH*.045f
                            p.x>=x && p.x<=x+tileW && p.y>=y && p.y<=y+tileH
                        }
                    }
                    if(hit!=null) choose(hit.id)
                }
            }) {
                val c=drawContext.canvas.nativeCanvas
                val p=Paint(Paint.ANTI_ALIAS_FLAG)
                val tileW=min(size.width/14.2f,size.height/11.2f)
                val tileH=tileW*1.25f; val stepX=tileW*.91f; val stepY=tileH*.80f
                val boardW=13*stepX+tileW; val left=(size.width-boardW)/2f
                val boardH=7*stepY+tileH; val top=(size.height-boardH)/2f
                tiles.sortedWith(compareBy<Tile>{it.spot.layer}.thenBy{it.spot.row}.thenBy{it.spot.col}).forEach { t ->
                    if(t.id in removed) return@forEach
                    val x=left+t.spot.col*stepX-t.spot.layer*tileW*.035f
                    val y=top+t.spot.row*stepY-t.spot.layer*tileH*.045f
                    p.style=Paint.Style.FILL
                    p.color=android.graphics.Color.rgb(16,27,22)
                    c.drawRoundRect(RectF(x+tileW*.09f,y+tileH*.11f,x+tileW*1.08f,y+tileH*1.09f),tileW*.08f,tileW*.08f,p)
                    p.color=when(t.spot.layer) { 0 -> android.graphics.Color.rgb(112,78,43); 1 -> android.graphics.Color.rgb(213,171,73); else -> android.graphics.Color.rgb(220,225,231) }
                    c.drawRoundRect(RectF(x,y,x+tileW,y+tileH),tileW*.06f,tileW*.06f,p)
                    p.color=when(t.spot.layer) { 0 -> android.graphics.Color.rgb(241,225,183); 1 -> android.graphics.Color.rgb(255,244,202); else -> android.graphics.Color.rgb(248,250,244) }
                    c.drawRoundRect(RectF(x+tileW*.055f,y+tileH*.035f,x+tileW*.945f,y+tileH*.94f),tileW*.045f,tileW*.045f,p)
                    val bmp=art[t.face]
                    if(bmp!=null) c.drawBitmap(bmp,null,RectF(x+tileW*.10f,y+tileH*.08f,x+tileW*.90f,y+tileH*.88f),p)
                    else { p.color=android.graphics.Color.DKGRAY; p.textAlign=Paint.Align.CENTER; p.textSize=tileW*.42f; c.drawText("✦",x+tileW/2,y+tileH*.62f,p) }
                    if(t.id==selected) {
                        p.style=Paint.Style.STROKE; p.strokeWidth=tileW*.10f; p.color=android.graphics.Color.rgb(255,65,40)
                        c.drawRoundRect(RectF(x+2,y+2,x+tileW-2,y+tileH-2),tileW*.06f,tileW*.06f,p); p.style=Paint.Style.FILL
                    } else if(!isFree(t)) {
                        p.color=android.graphics.Color.argb(85,30,35,32)
                        c.drawRoundRect(RectF(x+tileW*.055f,y+tileH*.035f,x+tileW*.945f,y+tileH*.94f),tileW*.045f,tileW*.045f,p)
                    }
                    if(t.spot.layer>0) {
                        p.color=android.graphics.Color.rgb(35,55,43); c.drawCircle(x+tileW*.17f,y+tileH*.84f,tileW*.105f,p)
                        p.color=android.graphics.Color.WHITE; p.textAlign=Paint.Align.CENTER; p.textSize=tileW*.15f
                        c.drawText((t.spot.layer+1).toString(),x+tileW*.17f,y+tileH*.89f,p)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(44.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={sound=!sound}) { Text(if(sound) "Sound on" else "Sound off",color=Color.White,fontSize=12.sp) }
                TextButton(onClick={restart()}) { Text("New game",color=Color(0xFFFFE4A3),fontSize=12.sp) }
                TextButton(onClick={
                    val first=tiles.firstOrNull { it.id !in removed && isFree(it) }
                    if(first!=null) { selected=first.id; message="Hint: find another tile with this picture" }
                }) { Text("Hint",color=Color.White,fontSize=12.sp) }
            }
        }
    }
}
