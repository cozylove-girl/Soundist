package com.soundist.core.network

data class SupabaseConfig(val url:String,val anonKey:String){
 val enabled:Boolean get()=url.startsWith("https://")&&anonKey.isNotBlank()
 companion object { val Disabled=SupabaseConfig("","") }
}

