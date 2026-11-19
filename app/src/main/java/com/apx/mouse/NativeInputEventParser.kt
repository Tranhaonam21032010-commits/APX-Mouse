package com.apx.mouse

/** Converts Linux input_event tuples [deviceIndex, type, code, value] to normalized events. */
internal class NativeInputEventParser {
    private data class Pending(var dx:Int=0,var dy:Int=0,var wheelX:Int=0,var wheelY:Int=0) {
        fun empty() = dx==0 && dy==0 && wheelX==0 && wheelY==0
    }
    private val pending = mutableMapOf<Int,Pending>()

    fun parseBatch(tuples:IntArray):List<EvdevInputEventParser.Event> {
        val out=mutableListOf<EvdevInputEventParser.Event>()
        var i=0
        while(i+3<tuples.size) {
            val device=tuples[i]; val type=tuples[i+1]; val code=tuples[i+2]; val value=tuples[i+3]
            val name="native:$device"
            when(type) {
                EV_REL -> {
                    val p=pending.getOrPut(device){Pending()}
                    when(code) {
                        REL_X -> p.dx=add(p.dx,value)
                        REL_Y -> p.dy=add(p.dy,value)
                        REL_HWHEEL -> p.wheelX=add(p.wheelX,value)
                        REL_WHEEL -> p.wheelY=add(p.wheelY,value)
                    }
                }
                EV_KEY -> if(value in 0..2) {
                    val button=buttons[code]
                    if(button!=null) out+=EvdevInputEventParser.Event.MouseButton(name,button,value!=0)
                    else keys[code]?.let { out+=EvdevInputEventParser.Event.Key(name,it,value!=0,value==2) }
                }
                EV_SYN -> if(code==SYN_REPORT) {
                    val p=pending.remove(device)
                    if(p!=null && !p.empty()) out+=EvdevInputEventParser.Event.Relative(name,p.dx,p.dy,p.wheelX,p.wheelY)
                }
            }
            i+=4
        }
        return out
    }
    fun reset()=pending.clear()
    private fun add(a:Int,b:Int)=(a.toLong()+b.toLong()).coerceIn(Int.MIN_VALUE.toLong(),Int.MAX_VALUE.toLong()).toInt()

    companion object {
        private const val EV_SYN=0; private const val EV_KEY=1; private const val EV_REL=2
        private const val SYN_REPORT=0; private const val REL_X=0; private const val REL_Y=1
        private const val REL_HWHEEL=6; private const val REL_WHEEL=8
        private val buttons=mapOf(272 to "BTN_LEFT",273 to "BTN_RIGHT",274 to "BTN_MIDDLE",275 to "BTN_SIDE",276 to "BTN_EXTRA")
        private val keys=buildMap {
            put(1,"KEY_ESC")
            for(c in 2..11) put(c,"KEY_"+if(c==11) "0" else (c-1).toString())
            putAll(mapOf(
                12 to "KEY_MINUS",13 to "KEY_EQUAL",14 to "KEY_BACKSPACE",15 to "KEY_TAB",
                16 to "KEY_Q",17 to "KEY_W",18 to "KEY_E",19 to "KEY_R",20 to "KEY_T",
                21 to "KEY_Y",22 to "KEY_U",23 to "KEY_I",24 to "KEY_O",25 to "KEY_P",
                26 to "KEY_LEFTBRACE",27 to "KEY_RIGHTBRACE",28 to "KEY_ENTER",29 to "KEY_LEFTCTRL",
                30 to "KEY_A",31 to "KEY_S",32 to "KEY_D",33 to "KEY_F",34 to "KEY_G",
                35 to "KEY_H",36 to "KEY_J",37 to "KEY_K",38 to "KEY_L",39 to "KEY_SEMICOLON",
                40 to "KEY_APOSTROPHE",41 to "KEY_GRAVE",42 to "KEY_LEFTSHIFT",43 to "KEY_BACKSLASH",
                44 to "KEY_Z",45 to "KEY_X",46 to "KEY_C",47 to "KEY_V",48 to "KEY_B",
                49 to "KEY_N",50 to "KEY_M",51 to "KEY_COMMA",52 to "KEY_DOT",53 to "KEY_SLASH",
                54 to "KEY_RIGHTSHIFT",56 to "KEY_LEFTALT",57 to "KEY_SPACE",58 to "KEY_CAPSLOCK",
                69 to "KEY_NUMLOCK",70 to "KEY_SCROLLLOCK",96 to "KEY_KPENTER",97 to "KEY_RIGHTCTRL",
                98 to "KEY_KPSLASH",99 to "KEY_SYSRQ",100 to "KEY_RIGHTALT",102 to "KEY_HOME",
                103 to "KEY_UP",104 to "KEY_PAGEUP",105 to "KEY_LEFT",106 to "KEY_RIGHT",
                107 to "KEY_END",108 to "KEY_DOWN",109 to "KEY_PAGEDOWN",110 to "KEY_INSERT",
                111 to "KEY_DELETE",113 to "KEY_MUTE",114 to "KEY_VOLUMEDOWN",115 to "KEY_VOLUMEUP",
                116 to "KEY_POWER",119 to "KEY_PAUSE",125 to "KEY_LEFTMETA",126 to "KEY_RIGHTMETA",
                127 to "KEY_MENU"
            ))
            for(c in 59..68) put(c,"KEY_F"+(c-58))
            put(87,"KEY_F11"); put(88,"KEY_F12")
            put(71,"KEY_KP7"); put(72,"KEY_KP8"); put(73,"KEY_KP9"); put(74,"KEY_KPMINUS")
            put(75,"KEY_KP4"); put(76,"KEY_KP5"); put(77,"KEY_KP6"); put(78,"KEY_KPPLUS")
            put(79,"KEY_KP1"); put(80,"KEY_KP2"); put(81,"KEY_KP3"); put(82,"KEY_KP0"); put(83,"KEY_KPDOT")
        }
    }
}
